package app.hisn.core;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** All traffic in these tests stays on loopback; no public resolver is queried. */
public final class DnsTunnelProbeTest {
    private enum Mode { PASS, WRONG_ID, WRONG_QUESTION, BAD_BLOCK, OVERSIZE, TRUNCATED,
        RESOLVER_FAILURE, NO_ANSWER, MALFORMED_ANSWER, TIMEOUT }
    private static final InetAddress LOOPBACK;
    static {
        try { LOOPBACK = InetAddress.getByAddress(new byte[]{127, 0, 0, 1}); }
        catch (IOException impossible) { throw new ExceptionInInitializerError(impossible); }
    }

    public static void main(String[] args) throws Exception {
        run(Mode.PASS, true, true, DnsTunnelProbe.Failure.NONE);
        for (Mode mode : new Mode[]{Mode.WRONG_ID, Mode.WRONG_QUESTION, Mode.BAD_BLOCK,
                Mode.OVERSIZE, Mode.TRUNCATED, Mode.TIMEOUT}) {
            run(mode, false, false, DnsTunnelProbe.Failure.BLOCK_CHECK);
        }
        for (Mode mode : new Mode[]{Mode.RESOLVER_FAILURE, Mode.NO_ANSWER, Mode.MALFORMED_ANSWER}) {
            run(mode, false, true, DnsTunnelProbe.Failure.RESOLVER_CHECK);
        }
        cancellation();
        bounds();
        System.out.println("DnsTunnelProbe tests passed (local block, public-path response, no fallback, bounds and cancellation)");
    }

    private static void run(Mode mode, boolean passed, boolean local, DnsTunnelProbe.Failure failure) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (DatagramSocket server = new DatagramSocket(new InetSocketAddress(LOOPBACK, 0))) {
            server.setSoTimeout(650);
            Future<Integer> serving = executor.submit(() -> serve(server, mode));
            DnsTunnelProbe probe = new DnsTunnelProbe(new InetSocketAddress(LOOPBACK, server.getLocalPort()), 250);
            DnsTunnelProbe.Result result = probe.run();
            require(result.passed == passed && result.localBlockPassed == local && result.failure == failure,
                    "Unexpected result for " + mode + ": " + result.failure);
            require(result.elapsedMillis >= 0 && result.elapsedMillis < 2000, "Unbounded check duration");
            int clientPort = serving.get(2, TimeUnit.SECONDS);
            // A successfully rebound source port also catches sockets left open on any result path.
            try (DatagramSocket rebound = new DatagramSocket(new InetSocketAddress(LOOPBACK, clientPort))) {
                require(rebound.isBound(), "Probe socket leaked");
            }
            try { probe.run(); throw new AssertionError("Probe could be reused"); }
            catch (IllegalStateException expected) { }
        } finally { executor.shutdownNow(); }
    }

    private static int serve(DatagramSocket server, Mode mode) throws IOException {
        DatagramPacket packet = receive(server);
        int clientPort = packet.getPort();
        byte[] request = request(packet);
        require(DnsTunnelProbe.BLOCKED_HOST.equals(host(request)), "First query is not the reserved local name");
        byte[] local = blockReply(request);
        switch (mode) {
            case WRONG_ID: local[1] ^= 1; break;
            case WRONG_QUESTION: local[13] ^= 1; break;
            case BAD_BLOCK: local[3] = 0; break;
            case OVERSIZE: local = Arrays.copyOf(local, 4097); break;
            case TRUNCATED: local[2] |= 2; break;
            default: break;
        }
        if (mode != Mode.TIMEOUT) reply(server, packet, local);
        boolean needsPublic = mode == Mode.PASS || mode == Mode.RESOLVER_FAILURE
                || mode == Mode.NO_ANSWER || mode == Mode.MALFORMED_ANSWER;
        if (!needsPublic) {
            try {
                receive(server);
                throw new AssertionError("Public query leaked after failed local check: " + mode);
            } catch (SocketTimeoutException expected) { }
            return clientPort;
        }
        DatagramPacket publicPacket = receive(server);
        byte[] publicRequest = request(publicPacket);
        require(DnsTunnelProbe.ALLOWED_HOST.equals(host(publicRequest)), "Unexpected public test name");
        byte[] response = answer(publicRequest);
        if (mode == Mode.RESOLVER_FAILURE) {
            response = new DnsPolicy(hostname -> DomainPolicy.Decision.ALLOW).failureResponse(publicRequest);
        } else if (mode == Mode.NO_ANSWER) {
            response = publicRequest.clone(); response[2] = (byte) 0x81;
        } else if (mode == Mode.MALFORMED_ANSWER) {
            response[publicRequest.length + 11] = 5; // A RDATA must contain exactly four bytes.
        }
        reply(server, publicPacket, response);
        return clientPort;
    }

    private static void cancellation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (DatagramSocket server = new DatagramSocket(new InetSocketAddress(LOOPBACK, 0))) {
            server.setSoTimeout(650);
            CountDownLatch arrived = new CountDownLatch(1);
            Future<Integer> serving = executor.submit(() -> {
                DatagramPacket first = receive(server);
                arrived.countDown();
                try { receive(server); throw new AssertionError("Query sent after cancellation"); }
                catch (SocketTimeoutException expected) { }
                return first.getPort();
            });
            DnsTunnelProbe probe = new DnsTunnelProbe(new InetSocketAddress(LOOPBACK, server.getLocalPort()), 5000);
            Future<DnsTunnelProbe.Result> pending = executor.submit(probe::run);
            require(arrived.await(1, TimeUnit.SECONDS), "Probe did not send local query");
            probe.close();
            DnsTunnelProbe.Result result = pending.get(1, TimeUnit.SECONDS);
            require(!result.passed && result.failure == DnsTunnelProbe.Failure.CANCELLED, "Cancellation not reported");
            int port = serving.get(2, TimeUnit.SECONDS);
            try (DatagramSocket rebound = new DatagramSocket(new InetSocketAddress(LOOPBACK, port))) {
                require(rebound.isBound(), "Cancelled socket leaked");
            }
            DnsTunnelProbe alreadyClosed = new DnsTunnelProbe(new InetSocketAddress(LOOPBACK, server.getLocalPort()), 250);
            alreadyClosed.close();
            require(alreadyClosed.run().failure == DnsTunnelProbe.Failure.CANCELLED, "Closed probe ran");
        } finally { executor.shutdownNow(); }
    }

    private static void bounds() {
        for (int timeout : new int[]{-1, 0, 99, 5001}) {
            try {
                new DnsTunnelProbe(new InetSocketAddress(LOOPBACK, 53), timeout);
                throw new AssertionError("Invalid timeout accepted");
            } catch (IllegalArgumentException expected) { }
        }
        try {
            new DnsTunnelProbe(InetSocketAddress.createUnresolved("resolver.invalid", 53), 250);
            throw new AssertionError("Unresolved endpoint accepted");
        } catch (IllegalArgumentException expected) { }
        try { new DnsTunnelProbe(null, 250); throw new AssertionError("Missing endpoint accepted"); }
        catch (IllegalArgumentException expected) { }
        try {
            new DnsTunnelProbe(new InetSocketAddress(LOOPBACK, 0), 250);
            throw new AssertionError("Missing destination port accepted");
        } catch (IllegalArgumentException expected) { }
    }

    private static DatagramPacket receive(DatagramSocket server) throws IOException {
        DatagramPacket packet = new DatagramPacket(new byte[4097], 4097);
        server.receive(packet);
        return packet;
    }

    private static byte[] request(DatagramPacket packet) { return Arrays.copyOf(packet.getData(), packet.getLength()); }

    private static String host(byte[] query) {
        String[] name = {null};
        DnsPolicy.Result parsed = new DnsPolicy(hostname -> {
            name[0] = hostname; return DomainPolicy.Decision.ALLOW;
        }).evaluate(query);
        require(parsed.action == DnsPolicy.Action.FORWARD, "Invalid probe query");
        return name[0];
    }

    private static byte[] blockReply(byte[] request) {
        return new DnsPolicy(hostname -> DomainPolicy.Decision.BLOCK).evaluate(request).response;
    }

    private static byte[] answer(byte[] query) {
        byte[] reply = Arrays.copyOf(query, query.length + 16);
        reply[2] = (byte) 0x81; reply[3] = (byte) 0x80; reply[7] = 1;
        byte[] record = {(byte) 0xc0, 12, 0, 1, 0, 1, 0, 0, 0, 1, 0, 4, (byte) 192, 0, 2, 1};
        System.arraycopy(record, 0, reply, query.length, record.length);
        return reply;
    }

    private static void reply(DatagramSocket server, DatagramPacket request, byte[] response) throws IOException {
        server.send(new DatagramPacket(response, response.length, request.getSocketAddress()));
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
