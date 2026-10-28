package app.hisn.core;

import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.FilterInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.HandshakeCompletedListener;

public final class TlsDnsTransportTest {
    private static void failure(TlsDnsTransport transport, byte[] query) throws Exception {
        try { transport.exchange(query); throw new AssertionError("Invalid TLS exchange accepted"); }
        catch (IOException expected) { }
    }

    public static void main(String[] args) throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        byte[] query = DnsPolicyTest.query("safe.example", 1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 1, loopback)) {
            server.setSoTimeout(1500);
            InetSocketAddress address = new InetSocketAddress(loopback, server.getLocalPort());
            try (TlsDnsTransport denied = new TlsDnsTransport(address, "localhost", 200, socket -> {
                if (!socket.isBound() || socket.isConnected()) throw new AssertionError("Protect requires an allocated, unconnected socket");
                return false;
            })) {
                failure(denied, query);
            }
            Future<?> fake = executor.submit(() -> {
                try (Socket client = server.accept()) {
                    // A plaintext server cannot impersonate an authenticated TLS resolver.
                    client.getOutputStream().write(new byte[]{0, 1, 2, 3, 4});
                } catch (IOException unavailable) { throw new RuntimeException(unavailable); }
            });
            TlsDnsTransport transport = new TlsDnsTransport(address, "localhost", 500, socket -> true);
            try { failure(transport, query); }
            finally { transport.close(); }
            fake.get(2, TimeUnit.SECONDS);
            failure(transport, query);
            try (TlsDnsTransport invalid = new TlsDnsTransport(address, "localhost", 200, socket -> true)) {
                failure(invalid, new byte[2]);
            }
        } finally { executor.shutdownNow(); }
        if (args.length == 1) {
            KeyStore store = KeyStore.getInstance("PKCS12");
            char[] password = "hisn-test-only".toCharArray();
            try (FileInputStream input = new FileInputStream(args[0])) { store.load(input, password); }
            KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, password);
            TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(store);
            SSLContext serverContext = SSLContext.getInstance("TLS");
            serverContext.init(keys.getKeyManagers(), null, null);
            SSLContext clientContext = SSLContext.getInstance("TLS");
            clientContext.init(null, trust.getTrustManagers(), null);
            authenticatedExchange(serverContext, clientContext.getSocketFactory(), loopback, query);
            staleConnectionRetry(serverContext, clientContext.getSocketFactory(), loopback, query);
            staleConnectionRetriesOnlyOnce(serverContext, clientContext.getSocketFactory(), loopback, query);
            freshEofIsNotRetried(serverContext, clientContext.getSocketFactory(), loopback, query);
            invalidatedCacheReconnects(serverContext, clientContext.getSocketFactory(), loopback, query, false);
            invalidatedCacheReconnects(serverContext, clientContext.getSocketFactory(), loopback, query, true);
            invalidateInFlight(serverContext, clientContext.getSocketFactory(), loopback, query);
            invalidateBufferedReply(serverContext, clientContext.getSocketFactory(), loopback, query);
            invalidateDuringOpen(loopback, query, false);
            invalidateDuringOpen(loopback, query, true);
            rejectIdentity(serverContext, clientContext.getSocketFactory(), loopback, query, "wrong.example");
            rejectIdentity(serverContext, (SSLSocketFactory) SSLSocketFactory.getDefault(), loopback, query, "localhost");
            Failover.run(serverContext, clientContext.getSocketFactory(), loopback, query);
        }
        cloudflareFamilyOnly();
        System.out.println("TlsDnsTransport tests passed (protection ordering, TLS identity, stale-only retry, generation invalidation, in-flight cancellation, terminal closure and backup-resolver failover)");
    }

    private static void readQuery(SSLSocket socket, byte[] query, boolean reply) throws IOException {
        socket.setSoTimeout(3000);
        DataInputStream input = new DataInputStream(socket.getInputStream());
        int length = input.readUnsignedShort();
        if (length != query.length) throw new IOException("Unexpected test frame size");
        byte[] message = new byte[length]; input.readFully(message);
        if (reply) {
            message[2] = (byte) 0x81;
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeShort(length); output.write(message); output.flush();
        }
    }

    private static void noConnection(SSLServerSocket server) throws IOException {
        server.setSoTimeout(300);
        try (Socket unexpected = server.accept()) {
            throw new AssertionError("Unexpected retry or resurrected transport");
        } catch (java.net.SocketTimeoutException expected) { }
    }

    private static void exchangeFailed(Future<byte[]> pending) throws Exception {
        try {
            pending.get(2, TimeUnit.SECONDS);
            throw new AssertionError("Invalidated exchange returned a reply");
        } catch (ExecutionException rejected) {
            if (!(rejected.getCause() instanceof IOException)) throw rejected;
        }
    }

    private static void invalidatedCacheReconnects(SSLContext context, SSLSocketFactory factory,
            InetAddress loopback, byte[] query, boolean freshFailure) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ExecutorService invalidator = Executors.newSingleThreadExecutor();
        try (SSLServerSocket server = (SSLServerSocket) context.getServerSocketFactory().createServerSocket(0, 3, loopback)) {
            server.setSoTimeout(3000);
            Future<?> serving = executor.submit(() -> {
                try {
                    try (SSLSocket first = (SSLSocket) server.accept()) {
                        readQuery(first, query, true);
                        // Keep the first connection open until invalidation closes
                        // it. A later exchange must not reuse this worker's cache.
                        if (first.getInputStream().read() != -1) throw new AssertionError("Old connection reused");
                    }
                    try (SSLSocket second = (SSLSocket) server.accept()) {
                        readQuery(second, query, !freshFailure);
                    }
                    if (freshFailure) noConnection(server);
                } catch (IOException failure) { throw new RuntimeException(failure); }
            });
            try (TlsDnsTransport client = new TlsDnsTransport(new InetSocketAddress(loopback, server.getLocalPort()),
                    "localhost", 1500, socket -> true, factory)) {
                if (client.exchange(query).length != query.length) throw new AssertionError("First response");
                // A network callback runs on a different thread. Removing only
                // that callback's ThreadLocal would leave this cache behind.
                invalidator.submit(client::invalidateConnections).get(1, TimeUnit.SECONDS);
                if (freshFailure) failure(client, query);
                else if (client.exchange(query).length != query.length) throw new AssertionError("Reconnected response");
            }
            serving.get(5, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); invalidator.shutdownNow(); }
    }

    private static void invalidateInFlight(SSLContext context, SSLSocketFactory factory,
            InetAddress loopback, byte[] query) throws Exception {
        ExecutorService servingExecutor = Executors.newSingleThreadExecutor();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (SSLServerSocket server = (SSLServerSocket) context.getServerSocketFactory().createServerSocket(0, 2, loopback)) {
            server.setSoTimeout(3000);
            Future<?> serving = servingExecutor.submit(() -> {
                try {
                    try (SSLSocket first = (SSLSocket) server.accept()) {
                        readQuery(first, query, false);
                        received.countDown();
                        if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("Test reply not released");
                        // Any late response belongs to the old generation.
                        try {
                            DataOutputStream output = new DataOutputStream(first.getOutputStream());
                            output.writeShort(query.length); output.write(query); output.flush();
                        } catch (IOException cancelled) { }
                    }
                    try (SSLSocket second = (SSLSocket) server.accept()) { readQuery(second, query, true); }
                } catch (IOException failure) { throw new RuntimeException(failure); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new RuntimeException(interrupted); }
            });
            try (TlsDnsTransport client = new TlsDnsTransport(new InetSocketAddress(loopback, server.getLocalPort()),
                    "localhost", 3000, socket -> true, factory)) {
                Future<byte[]> pending = worker.submit(() -> client.exchange(query));
                if (!received.await(3, TimeUnit.SECONDS)) throw new AssertionError("In-flight query not received");
                client.invalidateConnections();
                // Must cancel promptly, not wait for the old read timeout or
                // transparently replay a query onto the new network.
                exchangeFailed(pending);
                release.countDown();
                if (worker.submit(() -> client.exchange(query)).get(3, TimeUnit.SECONDS).length != query.length) {
                    throw new AssertionError("Transport unusable after cancellation");
                }
            }
            serving.get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); servingExecutor.shutdownNow(); worker.shutdownNow(); }
    }

    private static void invalidateDuringOpen(InetAddress loopback, byte[] query, boolean terminal) throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch protecting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ServerSocket server = new ServerSocket(0, 1, loopback)) {
            server.setSoTimeout(300);
            try (TlsDnsTransport client = new TlsDnsTransport(new InetSocketAddress(loopback, server.getLocalPort()),
                    "localhost", 3000, socket -> {
                        protecting.countDown();
                        try {
                            if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("Protect test timed out");
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt(); throw new IOException(interrupted);
                        }
                        return true;
                    })) {
                Future<byte[]> pending = worker.submit(() -> client.exchange(query));
                if (!protecting.await(2, TimeUnit.SECONDS)) throw new AssertionError("Protect not reached");
                if (terminal) client.close();
                client.invalidateConnections();
                release.countDown();
                exchangeFailed(pending);
                if (terminal) failure(client, query);
                try (Socket unexpected = server.accept()) {
                    throw new AssertionError("Invalidated open contacted resolver");
                } catch (java.net.SocketTimeoutException expected) { }
            }
        } finally { release.countDown(); worker.shutdownNow(); }
    }

    private static void invalidateBufferedReply(SSLContext context, SSLSocketFactory factory,
            InetAddress loopback, byte[] query) throws Exception {
        ExecutorService servingExecutor = Executors.newSingleThreadExecutor();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch buffered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SSLSocketFactory gatedFactory = new ReplyGateFactory(factory, query.length + 2, buffered, release);
        try (SSLServerSocket server = (SSLServerSocket) context.getServerSocketFactory().createServerSocket(0, 2, loopback)) {
            server.setSoTimeout(3000);
            Future<?> serving = servingExecutor.submit(() -> {
                try {
                    try (SSLSocket first = (SSLSocket) server.accept()) { readQuery(first, query, true); }
                    noConnection(server);
                } catch (IOException failure) { throw new RuntimeException(failure); }
            });
            try (TlsDnsTransport client = new TlsDnsTransport(new InetSocketAddress(loopback, server.getLocalPort()),
                    "localhost", 3000, socket -> true, gatedFactory)) {
                Future<byte[]> pending = worker.submit(() -> client.exchange(query));
                if (!buffered.await(3, TimeUnit.SECONDS)) throw new AssertionError("Reply was not buffered");
                client.invalidateConnections();
                release.countDown();
                // The wrapper now returns bytes already read from authenticated
                // TLS. Socket closure cannot erase them; generation validation
                // must reject them before exchange returns.
                exchangeFailed(pending);
            }
            serving.get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); servingExecutor.shutdownNow(); worker.shutdownNow(); }
    }

    /** A real authenticated TLS socket with a gate after its final response byte. */
    private static final class ReplyGateFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private final int frameLength;
        private final CountDownLatch buffered;
        private final CountDownLatch release;
        ReplyGateFactory(SSLSocketFactory delegate, int frameLength, CountDownLatch buffered, CountDownLatch release) {
            this.delegate = delegate; this.frameLength = frameLength; this.buffered = buffered; this.release = release;
        }
        @Override public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            return new ReplyGateSocket((SSLSocket) delegate.createSocket(socket, host, port, autoClose),
                    frameLength, buffered, release);
        }
        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        @Override public Socket createSocket(String host, int port) throws IOException { return delegate.createSocket(host, port); }
        @Override public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException {
            return delegate.createSocket(host, port, local, localPort);
        }
        @Override public Socket createSocket(InetAddress host, int port) throws IOException { return delegate.createSocket(host, port); }
        @Override public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException {
            return delegate.createSocket(host, port, local, localPort);
        }
    }

    private static final class ReplyGateSocket extends SSLSocket {
        private final SSLSocket delegate;
        private final InputStream input;
        ReplyGateSocket(SSLSocket delegate, int frameLength, CountDownLatch buffered, CountDownLatch release) throws IOException {
            this.delegate = delegate;
            input = new FilterInputStream(delegate.getInputStream()) {
                int remaining = frameLength;
                private void consumed(int length) throws IOException {
                    if (length > 0 && remaining > 0 && (remaining -= length) <= 0) {
                        buffered.countDown();
                        try {
                            if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("Reply gate timed out");
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt(); throw new IOException(interrupted);
                        }
                    }
                }
                @Override public int read() throws IOException {
                    int value = in.read(); consumed(value < 0 ? 0 : 1); return value;
                }
                @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                    int count = in.read(bytes, offset, length); consumed(count); return count;
                }
            };
        }
        @Override public InputStream getInputStream() { return input; }
        @Override public OutputStream getOutputStream() throws IOException { return delegate.getOutputStream(); }
        @Override public void close() throws IOException { delegate.close(); }
        @Override public void setSoTimeout(int timeout) throws java.net.SocketException { delegate.setSoTimeout(timeout); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        @Override public String[] getEnabledCipherSuites() { return delegate.getEnabledCipherSuites(); }
        @Override public void setEnabledCipherSuites(String[] suites) { delegate.setEnabledCipherSuites(suites); }
        @Override public String[] getSupportedProtocols() { return delegate.getSupportedProtocols(); }
        @Override public String[] getEnabledProtocols() { return delegate.getEnabledProtocols(); }
        @Override public void setEnabledProtocols(String[] protocols) { delegate.setEnabledProtocols(protocols); }
        @Override public SSLSession getSession() { return delegate.getSession(); }
        @Override public void addHandshakeCompletedListener(HandshakeCompletedListener listener) { delegate.addHandshakeCompletedListener(listener); }
        @Override public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) { delegate.removeHandshakeCompletedListener(listener); }
        @Override public void startHandshake() throws IOException { delegate.startHandshake(); }
        @Override public void setUseClientMode(boolean enabled) { delegate.setUseClientMode(enabled); }
        @Override public boolean getUseClientMode() { return delegate.getUseClientMode(); }
        @Override public void setNeedClientAuth(boolean enabled) { delegate.setNeedClientAuth(enabled); }
        @Override public boolean getNeedClientAuth() { return delegate.getNeedClientAuth(); }
        @Override public void setWantClientAuth(boolean enabled) { delegate.setWantClientAuth(enabled); }
        @Override public boolean getWantClientAuth() { return delegate.getWantClientAuth(); }
        @Override public void setEnableSessionCreation(boolean enabled) { delegate.setEnableSessionCreation(enabled); }
        @Override public boolean getEnableSessionCreation() { return delegate.getEnableSessionCreation(); }
        @Override public SSLParameters getSSLParameters() { return delegate.getSSLParameters(); }
        @Override public void setSSLParameters(SSLParameters parameters) { delegate.setSSLParameters(parameters); }
    }

    private static void staleConnectionRetriesOnlyOnce(SSLContext context, SSLSocketFactory factory,
            InetAddress loopback, byte[] query) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SSLServerSocket server = (SSLServerSocket) context.getServerSocketFactory().createServerSocket(0, 3, loopback)) {
            server.setSoTimeout(3000);
            Future<?> serving = executor.submit(() -> {
                try {
                    try (SSLSocket first = (SSLSocket) server.accept()) { readQuery(first, query, true); }
                    try (SSLSocket second = (SSLSocket) server.accept()) { readQuery(second, query, false); }
                    noConnection(server);
                } catch (IOException failure) { throw new RuntimeException(failure); }
            });
            try (TlsDnsTransport client = new TlsDnsTransport(new InetSocketAddress(loopback, server.getLocalPort()),
                    "localhost", 1500, socket -> true, factory)) {
                client.exchange(query);
                failure(client, query);
            }
            serving.get(5, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }

    private static void staleConnectionRetry(SSLContext context, SSLSocketFactory factory,
            InetAddress loopback, byte[] query) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SSLServerSocket server = (SSLServerSocket) context.getServerSocketFactory().createServerSocket(0, 2, loopback)) {
            server.setSoTimeout(3000);
            Future<?> serving = executor.submit(() -> {
                try {
                    for (int i = 0; i < 2; i++) {
                        try (SSLSocket socket = (SSLSocket) server.accept()) {
                            socket.setSoTimeout(3000);
                            DataInputStream input = new DataInputStream(socket.getInputStream());
                            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                            int length = input.readUnsignedShort();
                            if (length < 12 || length > 4096) throw new IOException("Invalid test frame");
                            byte[] message = new byte[length]; input.readFully(message);
                            message[2] = (byte) 0x81;
                            output.writeShort(length); output.write(message); output.flush();
                            // Close after each reply, forcing the reused connection to reach EOF.
                        }
                    }
                } catch (IOException failure) { throw new RuntimeException(failure); }
            });
            try (TlsDnsTransport client = new TlsDnsTransport(new InetSocketAddress(loopback, server.getLocalPort()),
                    "localhost", 1500, socket -> true, factory)) {
                for (int i = 0; i < 2; i++) {
                    if (client.exchange(query).length != query.length) throw new AssertionError("Retry response");
                }
            }
            serving.get(5, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }

    private static void freshEofIsNotRetried(SSLContext context, SSLSocketFactory factory,
            InetAddress loopback, byte[] query) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SSLServerSocket server = (SSLServerSocket) context.getServerSocketFactory().createServerSocket(0, 2, loopback)) {
            server.setSoTimeout(3000);
            Future<?> serving = executor.submit(() -> {
                try {
                    try (SSLSocket socket = (SSLSocket) server.accept()) {
                        socket.setSoTimeout(3000);
                        DataInputStream input = new DataInputStream(socket.getInputStream());
                        int length = input.readUnsignedShort();
                        if (length < 12 || length > 4096) throw new IOException("Invalid test frame");
                        input.readFully(new byte[length]);
                        // A fresh connection fails without a response: do not loop/retry it.
                    }
                    server.setSoTimeout(300);
                    try (Socket unexpected = server.accept()) {
                        throw new AssertionError("Fresh failure was retried");
                    } catch (java.net.SocketTimeoutException expected) { }
                } catch (IOException failure) { throw new RuntimeException(failure); }
            });
            try (TlsDnsTransport client = new TlsDnsTransport(new InetSocketAddress(loopback, server.getLocalPort()),
                    "localhost", 1500, socket -> true, factory)) {
                failure(client, query);
            }
            serving.get(5, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }

    private static void authenticatedExchange(SSLContext serverContext, SSLSocketFactory clientFactory,
            InetAddress loopback, byte[] query) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SSLServerSocket server = (SSLServerSocket) serverContext.getServerSocketFactory().createServerSocket(0, 1, loopback)) {
            server.setSoTimeout(3000);
            Future<?> serving = executor.submit(() -> {
                try (SSLSocket client = (SSLSocket) server.accept()) {
                    client.setSoTimeout(3000);
                    DataInputStream input = new DataInputStream(client.getInputStream());
                    DataOutputStream output = new DataOutputStream(client.getOutputStream());
                    for (int i = 0; i < 2; i++) {
                        int length = input.readUnsignedShort();
                        if (length < 12 || length > 4096) throw new IOException("Invalid test frame");
                        byte[] message = new byte[length]; input.readFully(message);
                        message[2] = (byte) 0x81;
                        output.writeShort(message.length); output.write(message); output.flush();
                    }
                } catch (IOException failure) { throw new RuntimeException(failure); }
            });
            try (TlsDnsTransport client = new TlsDnsTransport(new InetSocketAddress(loopback, server.getLocalPort()),
                    "localhost", 3000, socket -> {
                        if (!socket.isBound() || socket.isConnected()) throw new AssertionError("Protect must precede connect");
                        return true;
                    }, clientFactory)) {
                for (int i = 0; i < 2; i++) {
                    byte[] response = client.exchange(query);
                    if ((response[2] & 0x80) == 0 || response.length != query.length) throw new AssertionError("TLS response");
                }
            }
            serving.get(4, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }

    private static void rejectIdentity(SSLContext serverContext, SSLSocketFactory clientFactory,
            InetAddress loopback, byte[] query, String hostname) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SSLServerSocket server = (SSLServerSocket) serverContext.getServerSocketFactory().createServerSocket(0, 1, loopback)) {
            server.setSoTimeout(3000);
            Future<?> serving = executor.submit(() -> {
                try (SSLSocket client = (SSLSocket) server.accept()) {
                    client.setSoTimeout(3000);
                    client.startHandshake();
                } catch (IOException rejected) { /* Client rejects the certificate or name. */ }
            });
            try (TlsDnsTransport client = new TlsDnsTransport(new InetSocketAddress(loopback, server.getLocalPort()),
                    hostname, 3000, socket -> true, clientFactory)) {
                try { client.exchange(query); throw new AssertionError("Untrusted identity accepted"); }
                catch (SSLHandshakeException expected) { }
            }
            serving.get(4, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }

    /** The only upstream is Cloudflare for Families, main address first, then its second address. */
    private static void cloudflareFamilyOnly() throws Exception {
        try (TlsDnsTransport family = TlsDnsTransport.cloudflareFamily(2000, socket -> {
            throw new AssertionError("Constructing the upstream must not connect");
        })) {
            List<InetSocketAddress> resolvers = family.resolvers();
            if (resolvers.size() != 2 || !resolvers.get(0).equals(new InetSocketAddress(InetAddress.getByName("1.1.1.3"), 853))
                    || !resolvers.get(1).equals(new InetSocketAddress(InetAddress.getByName("1.0.0.3"), 853))
                    || !"family.cloudflare-dns.com".equals(family.serverName()) || family.preferredResolver() != 0)
                throw new AssertionError("Upstream must be the two Cloudflare Families addresses on 853, main first");
        }
        try {
            new TlsDnsTransport(Collections.emptyList(), "localhost", 200, socket -> true);
            throw new AssertionError("Empty resolver list accepted");
        } catch (IllegalArgumentException expected) { }
    }

    /**
     * Two loopback resolvers on different ports stand in for 1.1.1.3 and 1.0.0.3. Each accepted
     * connection is handled in the mode current when it was accepted, and accepts are counted.
     */
    private static final class Failover {
        enum Mode { TLS, STALL, PLAINTEXT, NO_REPLY, SLOW_SECOND }

        static final class Resolver implements AutoCloseable {
            final ServerSocket server;
            final SSLContext context;
            volatile Mode mode;
            final AtomicInteger accepts = new AtomicInteger();
            final List<Socket> open = Collections.synchronizedList(new ArrayList<>());

            Resolver(SSLContext context, InetAddress loopback, Mode mode) throws IOException {
                this.context = context; this.mode = mode;
                server = new ServerSocket(0, 8, loopback);
                Thread acceptor = new Thread(this::serve, "fake-resolver");
                acceptor.setDaemon(true);
                acceptor.start();
            }

            InetSocketAddress address() { return new InetSocketAddress(server.getInetAddress(), server.getLocalPort()); }

            private void serve() {
                while (!server.isClosed()) {
                    Socket client;
                    try { client = server.accept(); } catch (IOException closed) { return; }
                    open.add(client);
                    accepts.incrementAndGet();
                    Mode current = mode;
                    Thread handler = new Thread(() -> handle(client, current), "fake-resolver-client");
                    handler.setDaemon(true);
                    handler.start();
                }
            }

            private void handle(Socket client, Mode current) {
                try {
                    client.setSoTimeout(5000);
                    // Accept TCP, read the TLS hello and never answer, as a black-holed resolver does.
                    if (current == Mode.STALL) { while (client.getInputStream().read(new byte[4096]) >= 0) { } return; }
                    if (current == Mode.PLAINTEXT) { client.getOutputStream().write(new byte[]{0, 1, 2, 3, 4}); return; }
                    SSLSocket secure = (SSLSocket) context.getSocketFactory().createSocket(client, null, client.getPort(), true);
                    secure.setUseClientMode(false);
                    DataInputStream input = new DataInputStream(secure.getInputStream());
                    DataOutputStream output = new DataOutputStream(secure.getOutputStream());
                    for (int received = 1; ; received++) {
                        int length = input.readUnsignedShort();
                        byte[] message = new byte[length]; input.readFully(message);
                        if (current == Mode.NO_REPLY) return;
                        // Answer the first query, then keep the connection open without answering, as a slow lookup does.
                        if (current == Mode.SLOW_SECOND && received > 1) { while (input.read() >= 0) { } return; }
                        message[2] = (byte) 0x81;
                        output.writeShort(length); output.write(message); output.flush();
                    }
                } catch (IOException finished) { /* Client closed or test finished. */ }
                finally { try { client.close(); } catch (IOException ignored) { } }
            }

            @Override public void close() throws IOException {
                server.close();
                synchronized (open) { for (Socket socket : open) try { socket.close(); } catch (IOException ignored) { } }
            }
        }

        static void run(SSLContext context, SSLSocketFactory factory, InetAddress loopback, byte[] query) throws Exception {
            timeoutMovesToBackupAndReturns(context, factory, loopback, query);
            refusedMainUsesBackup(context, factory, loopback, query);
            waitsDoubleWhileMainFails(context, factory, loopback, query);
            handshakeFailureMoves(context, factory, loopback, query);
            identityFailsAtEveryAddress(context, factory, loopback, query);
            freshCloseDoesNotMove(context, factory, loopback, query);
            slowReplyIsNotCounted(context, factory, loopback, query);
            bothFailingIsBounded(context, factory, loopback, query);
            networkChangeReturnsToMain(context, factory, loopback, query);
            invalidatedAttemptDoesNotMove(context, factory, loopback, query);
        }

        private static TlsDnsTransport client(Resolver main, Resolver backup, SSLSocketFactory factory,
                AtomicLong clock, int timeout) {
            return new TlsDnsTransport(Arrays.asList(main.address(), backup.address()), "localhost", timeout,
                    socket -> true, factory, clock::get);
        }

        private static void reply(TlsDnsTransport client, byte[] query, String label) throws IOException {
            byte[] response = client.exchange(query);
            if (response.length != query.length || (response[2] & 0x80) == 0) throw new AssertionError(label);
        }

        private static void counts(Resolver main, int mainAccepts, Resolver backup, int backupAccepts, String label) throws Exception {
            // Accepts are counted on another thread; let it catch up before comparing.
            for (int i = 0; i < 100 && (main.accepts.get() < mainAccepts || backup.accepts.get() < backupAccepts); i++) Thread.sleep(10);
            Thread.sleep(30);
            if (main.accepts.get() != mainAccepts || backup.accepts.get() != backupAccepts)
                throw new AssertionError(label + ": main " + main.accepts.get() + ", backup " + backup.accepts.get());
        }

        private static void timeoutMovesToBackupAndReturns(SSLContext context, SSLSocketFactory factory,
                InetAddress loopback, byte[] query) throws Exception {
            AtomicLong clock = new AtomicLong(1000);
            try (Resolver main = new Resolver(context, loopback, Mode.STALL);
                 Resolver backup = new Resolver(context, loopback, Mode.TLS);
                 TlsDnsTransport client = client(main, backup, factory, clock, 300)) {
                reply(client, query, "Backup answers when the main resolver times out");
                counts(main, 1, backup, 1, "One main attempt, then the backup");
                if (client.preferredResolver() != 1) throw new AssertionError("Backup preferred while main waits");
                reply(client, query, "Backup connection reused");
                clock.set(1000 + ResolverFailover.FIRST_WAIT - 1);
                reply(client, query, "Still the backup before the wait ends");
                counts(main, 1, backup, 1, "No main attempt during its wait");
                main.mode = Mode.TLS;
                clock.set(1000 + ResolverFailover.FIRST_WAIT);
                reply(client, query, "Main resolver answers again after its wait");
                counts(main, 2, backup, 1, "Returned to the main resolver");
                reply(client, query, "Main connection reused");
                counts(main, 2, backup, 1, "Main connection kept");
                if (client.preferredResolver() != 0) throw new AssertionError("Main preferred after success");
            }
        }

        private static void refusedMainUsesBackup(SSLContext context, SSLSocketFactory factory,
                InetAddress loopback, byte[] query) throws Exception {
            InetSocketAddress refused;
            try (ServerSocket closed = new ServerSocket(0, 1, loopback)) {
                refused = new InetSocketAddress(loopback, closed.getLocalPort());
            }
            AtomicLong clock = new AtomicLong();
            try (Resolver backup = new Resolver(context, loopback, Mode.TLS);
                 TlsDnsTransport client = new TlsDnsTransport(Arrays.asList(refused, backup.address()),
                         "localhost", 1500, socket -> true, factory, clock::get)) {
                reply(client, query, "Backup answers when the main resolver refuses");
                if (backup.accepts.get() != 1 || client.preferredResolver() != 1) throw new AssertionError("Backup used once");
            }
        }

        private static void waitsDoubleWhileMainFails(SSLContext context, SSLSocketFactory factory,
                InetAddress loopback, byte[] query) throws Exception {
            AtomicLong clock = new AtomicLong();
            long first = ResolverFailover.FIRST_WAIT;
            try (Resolver main = new Resolver(context, loopback, Mode.STALL);
                 Resolver backup = new Resolver(context, loopback, Mode.TLS);
                 TlsDnsTransport client = client(main, backup, factory, clock, 300)) {
                reply(client, query, "First failover");
                clock.set(first);
                reply(client, query, "Probe fails, backup answers");
                counts(main, 2, backup, 2, "Probe dropped the backup connection, then reconnected to it");
                clock.set(first + 2 * first - 1);
                reply(client, query, "Second wait is doubled");
                counts(main, 2, backup, 2, "No probe during the doubled wait");
                clock.set(first + 2 * first);
                reply(client, query, "Probe after the doubled wait");
                counts(main, 3, backup, 3, "Third main attempt only after the doubled wait");
            }
        }

        /** A server that cannot complete the TLS handshake (here, a plaintext one) moves to the backup. */
        private static void handshakeFailureMoves(SSLContext context, SSLSocketFactory factory,
                InetAddress loopback, byte[] query) throws Exception {
            AtomicLong clock = new AtomicLong();
            try (Resolver main = new Resolver(context, loopback, Mode.PLAINTEXT);
                 Resolver backup = new Resolver(context, loopback, Mode.TLS);
                 TlsDnsTransport client = client(main, backup, factory, clock, 1500)) {
                reply(client, query, "Backup answers when the main handshake fails");
                counts(main, 1, backup, 1, "One main attempt, then the backup");
                if (client.preferredResolver() != 1) throw new AssertionError("Failed handshake marks the main resolver");
            }
        }

        /** The backup must prove the same identity: a name that does not verify fails at both addresses. */
        private static void identityFailsAtEveryAddress(SSLContext context, SSLSocketFactory factory,
                InetAddress loopback, byte[] query) throws Exception {
            AtomicLong clock = new AtomicLong();
            try (Resolver main = new Resolver(context, loopback, Mode.TLS);
                 Resolver backup = new Resolver(context, loopback, Mode.TLS);
                 TlsDnsTransport client = new TlsDnsTransport(Arrays.asList(main.address(), backup.address()),
                         "wrong.example", 1500, socket -> true, factory, clock::get)) {
                try { client.exchange(query); throw new AssertionError("Unverified identity accepted"); }
                catch (SSLHandshakeException expected) { }
                counts(main, 1, backup, 1, "Each address tried once, neither accepted");
            }
        }

        /** A reply-less close after the handshake is not a reachability problem: no backup attempt. */
        private static void freshCloseDoesNotMove(SSLContext context, SSLSocketFactory factory,
                InetAddress loopback, byte[] query) throws Exception {
            AtomicLong clock = new AtomicLong();
            try (Resolver main = new Resolver(context, loopback, Mode.NO_REPLY);
                 Resolver backup = new Resolver(context, loopback, Mode.TLS);
                 TlsDnsTransport client = client(main, backup, factory, clock, 1500)) {
                failure(client, query);
                counts(main, 1, backup, 0, "A closed connection stays a failure");
                if (client.preferredResolver() != 0) throw new AssertionError("A close marked the main resolver failing");
            }
        }

        /** One slow reply on a connection that already answered is retried at the backup but not counted. */
        private static void slowReplyIsNotCounted(SSLContext context, SSLSocketFactory factory,
                InetAddress loopback, byte[] query) throws Exception {
            AtomicLong clock = new AtomicLong();
            try (Resolver main = new Resolver(context, loopback, Mode.SLOW_SECOND);
                 Resolver backup = new Resolver(context, loopback, Mode.TLS);
                 TlsDnsTransport client = client(main, backup, factory, clock, 300)) {
                reply(client, query, "Main answers first");
                reply(client, query, "Backup answers the slow query");
                counts(main, 1, backup, 1, "Slow query retried once at the backup");
                if (client.preferredResolver() != 0) throw new AssertionError("A slow reply put the main resolver in backoff");
                main.mode = Mode.TLS;
                reply(client, query, "Main resolver used again at once");
                counts(main, 2, backup, 1, "Next query returns to the main resolver");
                // A fresh connection that never answers is a failing resolver, as before.
                main.mode = Mode.STALL;
                client.invalidateConnections();
                reply(client, query, "Backup after a fresh timeout");
                if (client.preferredResolver() != 1) throw new AssertionError("A fresh timeout marks the main resolver");
            }
        }

        private static void bothFailingIsBounded(SSLContext context, SSLSocketFactory factory,
                InetAddress loopback, byte[] query) throws Exception {
            AtomicLong clock = new AtomicLong();
            try (Resolver main = new Resolver(context, loopback, Mode.STALL);
                 Resolver backup = new Resolver(context, loopback, Mode.STALL);
                 TlsDnsTransport client = client(main, backup, factory, clock, 250)) {
                try { client.exchange(query); throw new AssertionError("Timeouts answered"); }
                catch (java.net.SocketTimeoutException expected) { }
                counts(main, 1, backup, 1, "One attempt per resolver");
                failure(client, query);
                counts(main, 2, backup, 1, "Only one attempt while both wait");
                if (client.preferredResolver() != -1) throw new AssertionError("Nothing preferred while both fail");
            }
        }

        private static void networkChangeReturnsToMain(SSLContext context, SSLSocketFactory factory,
                InetAddress loopback, byte[] query) throws Exception {
            AtomicLong clock = new AtomicLong();
            try (Resolver main = new Resolver(context, loopback, Mode.STALL);
                 Resolver backup = new Resolver(context, loopback, Mode.TLS);
                 TlsDnsTransport client = client(main, backup, factory, clock, 300)) {
                reply(client, query, "Backup after main timeout");
                main.mode = Mode.TLS;
                client.invalidateConnections();
                reply(client, query, "A new network starts with the main resolver");
                counts(main, 2, backup, 1, "Main resolver retried at once after a network change");
            }
        }

        /** An attempt cut short by a network change fails; it is not replayed on the backup. */
        private static void invalidatedAttemptDoesNotMove(SSLContext context, SSLSocketFactory factory,
                InetAddress loopback, byte[] query) throws Exception {
            AtomicLong clock = new AtomicLong();
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try (Resolver main = new Resolver(context, loopback, Mode.STALL);
                 Resolver backup = new Resolver(context, loopback, Mode.TLS);
                 TlsDnsTransport client = client(main, backup, factory, clock, 3000)) {
                Future<byte[]> pending = worker.submit(() -> client.exchange(query));
                for (int i = 0; i < 200 && main.accepts.get() == 0; i++) Thread.sleep(10);
                Thread.sleep(50);
                client.invalidateConnections();
                exchangeFailed(pending);
                counts(main, 1, backup, 0, "Invalidated attempt not moved to the backup");
                if (client.preferredResolver() != 0) throw new AssertionError("Invalidation counted as a resolver failure");
            } finally { worker.shutdownNow(); }
        }
    }
}
