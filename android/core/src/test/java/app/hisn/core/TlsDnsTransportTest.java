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
        }
        System.out.println("TlsDnsTransport tests passed (protection ordering, TLS identity, stale-only retry, generation invalidation, in-flight cancellation and terminal closure)");
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
}
