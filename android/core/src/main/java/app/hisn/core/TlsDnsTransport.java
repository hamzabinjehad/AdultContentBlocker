package app.hisn.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Authenticated DNS-over-TLS with per-worker connection reuse and no plaintext fallback. With more
 * than one resolver address, all share one TLS identity; a failure to connect or a timeout moves to
 * the next address (see {@link ResolverFailover}), and nothing else does.
 */
public final class TlsDnsTransport implements DnsExchange.Transport, AutoCloseable {
    public interface SocketProtector { boolean protect(Socket socket) throws IOException; }
    /** Cloudflare for Families (malware and adult content filtered), the only upstream Hisn uses. */
    public static final String FAMILY_NAME = "family.cloudflare-dns.com";
    private static final byte[][] FAMILY_ADDRESSES = {{1, 1, 1, 3}, {1, 0, 0, 3}};
    private final InetSocketAddress[] resolvers;
    private final String serverName;
    private final int timeout;
    private final SocketProtector protector;
    private final SSLSocketFactory factory;
    private final LongSupplier clock;
    private final ResolverFailover failover;
    private final ThreadLocal<Connection> local = new ThreadLocal<>();
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    // Only generation changes, failover records and socket registration use this lock. Network
    // I/O, including socket closure, must never hold it.
    private final Object lifecycle = new Object();
    private volatile boolean closed;
    private volatile long generation;

    /** 1.1.1.3, then 1.0.0.3 if it cannot be reached, both on port 853 as {@link #FAMILY_NAME}. */
    public static TlsDnsTransport cloudflareFamily(int timeout, SocketProtector protector) {
        InetSocketAddress[] family = new InetSocketAddress[FAMILY_ADDRESSES.length];
        try {
            for (int i = 0; i < family.length; i++)
                family[i] = new InetSocketAddress(InetAddress.getByAddress(FAMILY_ADDRESSES[i]), 853);
        } catch (UnknownHostException impossible) { throw new IllegalStateException(impossible); }
        return new TlsDnsTransport(Arrays.asList(family), FAMILY_NAME, timeout, protector);
    }

    public TlsDnsTransport(InetSocketAddress resolver, String serverName, int timeout, SocketProtector protector) {
        this(Collections.singletonList(resolver), serverName, timeout, protector);
    }

    public TlsDnsTransport(List<InetSocketAddress> resolvers, String serverName, int timeout, SocketProtector protector) {
        this(resolvers, serverName, timeout, protector, (SSLSocketFactory) SSLSocketFactory.getDefault(), TlsDnsTransport::now);
    }

    // Package-private so protocol tests can trust only their disposable local certificate.
    TlsDnsTransport(InetSocketAddress resolver, String serverName, int timeout, SocketProtector protector, SSLSocketFactory factory) {
        this(Collections.singletonList(resolver), serverName, timeout, protector, factory, TlsDnsTransport::now);
    }

    TlsDnsTransport(List<InetSocketAddress> resolvers, String serverName, int timeout, SocketProtector protector,
            SSLSocketFactory factory, LongSupplier clock) {
        if (resolvers == null || resolvers.isEmpty() || resolvers.size() > 8 || serverName == null
                || serverName.length() > 253 || !serverName.matches("[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*")
                || timeout < 1 || timeout > 10000 || protector == null || factory == null || clock == null) {
            throw new IllegalArgumentException("Invalid TLS resolver configuration");
        }
        for (InetSocketAddress resolver : resolvers) {
            if (resolver == null || resolver.isUnresolved() || resolver.getPort() == 0)
                throw new IllegalArgumentException("Invalid TLS resolver configuration");
        }
        this.resolvers = resolvers.toArray(new InetSocketAddress[0]);
        this.serverName = serverName;
        this.timeout = timeout;
        this.protector = protector;
        this.factory = factory;
        this.clock = clock;
        failover = new ResolverFailover(this.resolvers.length);
    }

    private static long now() { return System.nanoTime() / 1000000L; }

    public List<InetSocketAddress> resolvers() { return Collections.unmodifiableList(Arrays.asList(resolvers)); }
    public String serverName() { return serverName; }

    /** Index of the resolver new connections use now: 0 is the first, -1 while every one is failing. */
    public int preferredResolver() { return failover.preferred(clock.getAsLong()); }

    @Override public byte[] exchange(byte[] query) throws IOException {
        if (query == null || query.length < 12 || query.length > 4096) throw new IOException("DNS transport unavailable");
        final long exchangeGeneration;
        synchronized (lifecycle) {
            if (closed) throw new IOException("DNS transport unavailable");
            exchangeGeneration = generation;
        }
        Connection connection = local.get();
        int resolver = failover.choose(clock.getAsLong(), connection == null ? -1 : connection.resolver);
        // Drop a connection from an older network, or to a resolver that is no longer the one to use.
        if (connection != null && (connection.generation != exchangeGeneration || connection.resolver != resolver)) {
            connection.close();
            local.remove();
            connection = null;
        }
        boolean reused = connection != null;
        int tried = 1 << resolver;
        while (true) {
            try {
                requireGeneration(exchangeGeneration);
                if (connection == null) {
                    connection = open(resolver, exchangeGeneration);
                    local.set(connection);
                }
                connection.output.writeShort(query.length);
                connection.output.write(query);
                connection.output.flush();
                int length = connection.input.readUnsignedShort();
                if (length < 12 || length > 4096) throw new IOException("Invalid DNS frame size");
                byte[] response = new byte[length];
                connection.input.readFully(response);
                // A complete reply can already be buffered when the network
                // changes. Closing its socket alone cannot invalidate that reply.
                synchronized (lifecycle) {
                    requireGeneration(exchangeGeneration);
                    failover.succeeded(resolver);
                    return response;
                }
            } catch (IOException thrown) {
                if (connection != null) connection.close();
                connection = null;
                local.remove();
                boolean unreachable = thrown instanceof Unreachable;
                IOException failure = unreachable ? (IOException) thrown.getCause() : thrown;
                // An idle upstream can close a pooled connection. Retry only one
                // reused EOF/reset, at the SAME authenticated resolver.
                boolean stale = failure instanceof java.io.EOFException || failure instanceof java.net.SocketException;
                if (closed || generation != exchangeGeneration) throw failure;
                if (reused && stale) { reused = false; continue; }
                reused = false;
                // Only a failure to connect or a timeout moves on, to the next resolver with the same
                // TLS identity. TLS identity failures, bad frames and fresh EOFs fail here.
                if (!unreachable && !(failure instanceof java.net.SocketTimeoutException)) throw failure;
                synchronized (lifecycle) {
                    if (closed || generation != exchangeGeneration) throw failure;
                    failover.failed(resolver, clock.getAsLong());
                    resolver = failover.next(tried, clock.getAsLong());
                }
                if (resolver < 0) throw failure;
                tried |= 1 << resolver;
            }
        }
    }

    private void requireGeneration(long expected) throws IOException {
        if (closed || generation != expected) throw new IOException("DNS connection invalidated");
    }

    private Connection open(int resolver, long expectedGeneration) throws IOException {
        Socket raw = new Socket();
        try {
            synchronized (lifecycle) {
                requireGeneration(expectedGeneration);
                sockets.add(raw);
            }
            // Allocate the native descriptor before Android's protect(Socket) reads it.
            // A fresh lazy Socket may otherwise have no descriptor to protect. Binding
            // locally does not contact the resolver; protect still precedes connect.
            raw.bind(new InetSocketAddress(0));
            requireGeneration(expectedGeneration);
            if (!protector.protect(raw)) throw new IOException("Could not protect resolver socket");
            requireGeneration(expectedGeneration);
            try { raw.connect(resolvers[resolver], timeout); }
            catch (IOException unreachable) { throw new Unreachable(unreachable); }
            raw.setSoTimeout(timeout);
            SSLSocket secure = (SSLSocket) factory.createSocket(raw, serverName, resolvers[resolver].getPort(), true);
            secure.setSoTimeout(timeout);
            SSLParameters parameters = secure.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            secure.setSSLParameters(parameters);
            java.util.List<String> protocols = new java.util.ArrayList<>();
            for (String protocol : secure.getSupportedProtocols()) {
                if (protocol.equals("TLSv1.2") || protocol.equals("TLSv1.3")) protocols.add(protocol);
            }
            if (protocols.isEmpty()) throw new IOException("Modern TLS unavailable");
            secure.setEnabledProtocols(protocols.toArray(new String[0]));
            secure.startHandshake();
            requireGeneration(expectedGeneration);
            return new Connection(raw, secure, resolver, expectedGeneration);
        } catch (IOException | RuntimeException failure) {
            sockets.remove(raw);
            try { raw.close(); } catch (IOException ignored) { }
            throw failure;
        }
    }

    /**
     * Drops connections from the previous underlying network without closing the
     * transport. In-flight exchanges fail; a later exchange uses a fresh socket,
     * starting again from the first resolver. This never changes the authenticated
     * resolver identity or enables a fallback.
     */
    public void invalidateConnections() {
        Socket[] previous;
        synchronized (lifecycle) {
            if (closed) return;
            generation++;
            failover.reset();
            previous = sockets.toArray(new Socket[0]);
            sockets.clear();
        }
        closeSockets(previous);
        local.remove();
    }

    @Override public void close() {
        Socket[] previous;
        synchronized (lifecycle) {
            if (closed) return;
            closed = true;
            generation++;
            previous = sockets.toArray(new Socket[0]);
            sockets.clear();
        }
        closeSockets(previous);
        local.remove();
    }

    private static void closeSockets(Socket[] previous) {
        for (Socket socket : previous) try { socket.close(); } catch (IOException ignored) { }
    }

    /** A failure before the resolver accepted the TCP connection; the cause is what callers see. */
    private static final class Unreachable extends IOException {
        Unreachable(IOException cause) { super(cause); }
    }

    private final class Connection {
        final Socket raw;
        final SSLSocket secure;
        final int resolver;
        final long generation;
        final DataInputStream input;
        final DataOutputStream output;
        Connection(Socket raw, SSLSocket secure, int resolver, long generation) throws IOException {
            this.raw = raw; this.secure = secure;
            this.resolver = resolver; this.generation = generation;
            input = new DataInputStream(secure.getInputStream());
            output = new DataOutputStream(secure.getOutputStream());
        }
        void close() {
            sockets.remove(raw);
            // Cancel reads before asking TLS to close, avoiding a TLS shutdown
            // exchange on an already invalidated or unavailable network.
            try { raw.close(); } catch (IOException ignored) { }
            try { secure.close(); } catch (IOException ignored) { }
        }
    }
}
