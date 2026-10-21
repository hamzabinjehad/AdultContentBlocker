package app.hisn.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Authenticated DNS-over-TLS with per-worker connection reuse and no plaintext fallback. */
public final class TlsDnsTransport implements DnsExchange.Transport, AutoCloseable {
    public interface SocketProtector { boolean protect(Socket socket) throws IOException; }
    private final InetSocketAddress resolver;
    private final String serverName;
    private final int timeout;
    private final SocketProtector protector;
    private final SSLSocketFactory factory;
    private final ThreadLocal<Connection> local = new ThreadLocal<>();
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    // Only generation changes and socket registration use this lock. Network I/O,
    // including socket closure, must never hold it.
    private final Object lifecycle = new Object();
    private volatile boolean closed;
    private volatile long generation;

    public TlsDnsTransport(InetSocketAddress resolver, String serverName, int timeout, SocketProtector protector) {
        this(resolver, serverName, timeout, protector, (SSLSocketFactory) SSLSocketFactory.getDefault());
    }

    // Package-private so protocol tests can trust only their disposable local certificate.
    TlsDnsTransport(InetSocketAddress resolver, String serverName, int timeout, SocketProtector protector, SSLSocketFactory factory) {
        if (resolver == null || resolver.isUnresolved() || resolver.getPort() == 0 || serverName == null
                || serverName.length() > 253 || !serverName.matches("[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*")
                || timeout < 1 || timeout > 10000 || protector == null || factory == null) {
            throw new IllegalArgumentException("Invalid TLS resolver configuration");
        }
        this.resolver = resolver;
        this.serverName = serverName;
        this.timeout = timeout;
        this.protector = protector;
        this.factory = factory;
    }

    @Override public byte[] exchange(byte[] query) throws IOException {
        if (query == null || query.length < 12 || query.length > 4096) throw new IOException("DNS transport unavailable");
        final long exchangeGeneration;
        synchronized (lifecycle) {
            if (closed) throw new IOException("DNS transport unavailable");
            exchangeGeneration = generation;
        }
        Connection connection = local.get();
        if (connection != null && connection.generation != exchangeGeneration) {
            connection.close();
            local.remove();
            connection = null;
        }
        boolean reused = connection != null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                requireGeneration(exchangeGeneration);
                if (connection == null) {
                    connection = open(exchangeGeneration);
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
                    return response;
                }
            } catch (IOException failure) {
                if (connection != null) connection.close();
                connection = null;
                local.remove();
                // An idle upstream can close a pooled connection. Retry only one
                // reused EOF/reset, at the SAME authenticated resolver. Fresh
                // failures, timeouts, TLS identity failures and bad frames fail.
                boolean stale = failure instanceof java.io.EOFException
                        || failure instanceof java.net.SocketException;
                if (attempt != 0 || !reused || !stale || closed
                        || generation != exchangeGeneration) throw failure;
            }
        }
        throw new IOException("DNS transport unavailable");
    }

    private void requireGeneration(long expected) throws IOException {
        if (closed || generation != expected) throw new IOException("DNS connection invalidated");
    }

    private Connection open(long expectedGeneration) throws IOException {
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
            raw.connect(resolver, timeout);
            raw.setSoTimeout(timeout);
            SSLSocket secure = (SSLSocket) factory.createSocket(raw, serverName, resolver.getPort(), true);
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
            return new Connection(raw, secure, expectedGeneration);
        } catch (IOException | RuntimeException failure) {
            sockets.remove(raw);
            try { raw.close(); } catch (IOException ignored) { }
            throw failure;
        }
    }

    /**
     * Drops connections from the previous underlying network without closing the
     * transport. In-flight exchanges fail; a later exchange uses a fresh socket.
     * This never changes the authenticated resolver or enables a fallback.
     */
    public void invalidateConnections() {
        Socket[] previous;
        synchronized (lifecycle) {
            if (closed) return;
            generation++;
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

    private final class Connection {
        final Socket raw;
        final SSLSocket secure;
        final long generation;
        final DataInputStream input;
        final DataOutputStream output;
        Connection(Socket raw, SSLSocket secure, long generation) throws IOException {
            this.raw = raw; this.secure = secure;
            this.generation = generation;
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
