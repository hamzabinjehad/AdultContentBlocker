package app.hisn.core;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit local-block and allowed-query checks over selected UDP/TCP transports, not all device traffic.
 * The socket is deliberately NOT protected from the VPN. No system resolver or alternate
 * endpoint is used, and the public query is sent only after the local-block check passes. */
public final class DnsTunnelProbe implements Closeable {
    public static final String BLOCKED_HOST = "blocked.hisn-self-check.invalid";
    public static final String ALLOWED_HOST = "example.com";
    public enum Failure { NONE, BLOCK_CHECK, RESOLVER_CHECK, TCP_BLOCK_CHECK, TCP_RESOLVER_CHECK, CANCELLED }
    public enum Protocol { UDP, TCP, BOTH }

    public static final class Result {
        public final boolean passed;
        public final boolean localBlockPassed;
        public final long elapsedMillis;
        public final Failure failure;
        public final boolean udpPassed;
        public final boolean tcpPassed;

        private Result(boolean passed, boolean localBlockPassed, long elapsedMillis, Failure failure,
                boolean udpPassed, boolean tcpPassed) {
            this.passed = passed;
            this.localBlockPassed = localBlockPassed;
            this.elapsedMillis = elapsedMillis;
            this.failure = failure;
            this.udpPassed = udpPassed;
            this.tcpPassed = tcpPassed;
        }
    }

    private final InetSocketAddress destination;
    private final int timeoutMillis;
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean closed;
    private DatagramSocket socket;
    private Socket tcpSocket;
    private final Protocol protocol;
    private boolean useTcp;

    /** The destination must already be resolved, so construction never performs DNS lookup. */
    public DnsTunnelProbe(InetSocketAddress destination, int timeoutMillis) {
        this(destination, timeoutMillis, Protocol.UDP);
    }

    public DnsTunnelProbe(InetSocketAddress destination, int timeoutMillis, Protocol protocol) {
        if (destination == null || destination.isUnresolved() || destination.getPort() < 1
                || timeoutMillis < 100 || timeoutMillis > 5000 || protocol == null) {
            throw new IllegalArgumentException("Invalid tunnel probe endpoint or timeout");
        }
        this.destination = destination;
        this.timeoutMillis = timeoutMillis;
        this.protocol = protocol;
    }

    /** Single-use; all sockets are closed on success, failure or cancellation. */
    public Result run() {
        if (!started.compareAndSet(false, true)) throw new IllegalStateException("Probe already started");
        long began = System.nanoTime();
        boolean localBlockPassed = false;
        boolean udpPassed = false;
        boolean tcpPassed = false;
        try {
            DnsPolicy validation = new DnsPolicy(host -> DomainPolicy.Decision.ALLOW);
            DnsExchange exchange = new DnsExchange(validation, this::exchange);
            Protocol[] checks = protocol == Protocol.BOTH ? new Protocol[]{Protocol.UDP, Protocol.TCP}
                    : new Protocol[]{protocol};
            for (Protocol current : checks) {
                useTcp = current == Protocol.TCP;
                localBlockPassed = false;
                openSocket();
                byte[] local = exchange.resolve(query(BLOCKED_HOST));
                if (rcode(local) != 3 || read16(local, 6) != 0 || read16(local, 8) != 0) {
                    throw new IOException("Local block was not observed");
                }
                localBlockPassed = true;
                byte[] allowed = exchange.resolve(query(ALLOWED_HOST));
                if (rcode(allowed) != 0 || read16(allowed, 6) == 0) {
                    throw new IOException("Public test name did not resolve");
                }
                if (closed || Thread.currentThread().isInterrupted()) throw new IOException("Probe cancelled");
                if (useTcp) tcpPassed = true;
                else udpPassed = true;
            }
            return result(true, true, began, Failure.NONE, udpPassed, tcpPassed);
        } catch (IOException failure) {
            Failure reason = closed || Thread.currentThread().isInterrupted() ? Failure.CANCELLED
                    : useTcp ? (localBlockPassed ? Failure.TCP_RESOLVER_CHECK : Failure.TCP_BLOCK_CHECK)
                    : localBlockPassed ? Failure.RESOLVER_CHECK : Failure.BLOCK_CHECK;
            return result(false, localBlockPassed, began, reason, udpPassed, tcpPassed);
        } finally {
            close();
        }
    }

    private void openSocket() throws IOException {
        Socket connecting = null;
        synchronized (this) {
            if (closed) throw new IOException("Probe closed");
            if (useTcp) {
                if (socket != null) { socket.close(); socket = null; }
                tcpSocket = new Socket();
                connecting = tcpSocket;
            } else {
                socket = new DatagramSocket();
                socket.connect(destination);
                socket.setSoTimeout(timeoutMillis);
            }
        }
        // Never hold the close/cancellation lock while connecting or reading.
        if (connecting != null) {
            connecting.connect(destination, timeoutMillis);
            connecting.setSoTimeout(timeoutMillis);
        }
    }

    private byte[] exchange(byte[] query) throws IOException {
        if (useTcp) {
            Socket current;
            synchronized (this) {
                if (closed || tcpSocket == null || Thread.currentThread().isInterrupted()) throw new IOException("Probe closed");
                current = tcpSocket;
            }
            DataOutputStream output = new DataOutputStream(current.getOutputStream());
            output.writeShort(query.length); output.write(query); output.flush();
            DataInputStream input = new DataInputStream(current.getInputStream());
            int length = input.readUnsignedShort();
            if (length < 12 || length > 4096) throw new IOException("Invalid DNS frame size");
            byte[] response = new byte[length]; input.readFully(response);
            return response;
        }
        DatagramSocket current;
        synchronized (this) {
            if (closed || socket == null || Thread.currentThread().isInterrupted()) throw new IOException("Probe closed");
            current = socket;
        }
        current.send(new DatagramPacket(query, query.length));
        byte[] bytes = new byte[4097];
        DatagramPacket response = new DatagramPacket(bytes, bytes.length);
        current.receive(response);
        // DnsExchange rejects malformed, mismatched, truncated and oversized replies.
        return Arrays.copyOf(bytes, response.getLength());
    }

    @Override public synchronized void close() {
        closed = true;
        if (socket != null) socket.close();
        if (tcpSocket != null) try { tcpSocket.close(); } catch (IOException ignored) { }
    }

    private static Result result(boolean passed, boolean local, long began, Failure failure, boolean udp, boolean tcp) {
        return new Result(passed, local, Math.max(0, (System.nanoTime() - began) / 1000000), failure, udp, tcp);
    }

    private static byte[] query(String hostname) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // DnsExchange randomizes the transaction ID independently for each query.
        byte[] header = {0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0};
        output.write(header, 0, header.length);
        for (String label : hostname.split("\\.")) {
            byte[] ascii = label.getBytes(StandardCharsets.US_ASCII);
            output.write(ascii.length); output.write(ascii, 0, ascii.length);
        }
        output.write(0); output.write(0); output.write(1); output.write(0); output.write(1);
        return output.toByteArray();
    }

    private static int rcode(byte[] reply) { return reply[3] & 15; }
    private static int read16(byte[] bytes, int offset) { return ((bytes[offset] & 255) << 8) | (bytes[offset + 1] & 255); }
}
