package app.hisn.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Both UDP and TCP listeners are loopback only. */
public final class DnsTunnelTcpProbeTest {
    private static final InetAddress LOOP;
    static { try { LOOP = InetAddress.getByAddress(new byte[]{127, 0, 0, 1}); }
        catch (IOException impossible) { throw new ExceptionInInitializerError(impossible); } }
    public static void main(String[] args) throws Exception {
        run(false, 0); run(true, 0);
        for (int mode = 1; mode <= 4; mode++) run(false, mode);
        cancellation();
        System.out.println("DnsTunnelTcpProbe tests passed (UDP+TCP, framing/reuse, failure bounds and cancellation)");
    }

    private static void run(boolean both, int mode) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try (ServerSocket tcp = new ServerSocket(0, 1, LOOP);
             DatagramSocket udp = new DatagramSocket(new InetSocketAddress(LOOP, tcp.getLocalPort()))) {
            tcp.setSoTimeout(1000); udp.setSoTimeout(1000);
            Future<Integer> serving = workers.submit(() -> {
                try (Socket client = tcp.accept()) {
                    client.setSoTimeout(700);
                    DataInputStream in = new DataInputStream(client.getInputStream());
                    DataOutputStream out = new DataOutputStream(client.getOutputStream());
                    byte[] local = read(in);
                    byte[] reply = block(local);
                    if (mode == 1) reply[1] ^= 1;
                    if (mode == 2) { out.writeShort(4097); out.flush(); return 1; }
                    if (mode == 3) return 1;
                    writeSplit(out, reply);
                    if (mode == 1) {
                        try { read(in); throw new AssertionError("Public query after bad local block"); }
                        catch (IOException expected) { return 1; }
                    }
                    byte[] allowed = read(in);
                    reply = answer(allowed);
                    if (mode == 4) reply[1] ^= 1;
                    writeSplit(out, reply);
                    return 2;
                }
            });
            Future<?> udpServing = both ? workers.submit(() -> {
                try {
                    for (int i = 0; i < 2; i++) {
                        DatagramPacket request = new DatagramPacket(new byte[4097], 4097);
                        udp.receive(request);
                        byte[] query = Arrays.copyOf(request.getData(), request.getLength());
                        byte[] reply = i == 0 ? block(query) : answer(query);
                        udp.send(new DatagramPacket(reply, reply.length, request.getSocketAddress()));
                    }
                } catch (IOException problem) { throw new RuntimeException(problem); }
            }) : null;
            DnsTunnelProbe probe = new DnsTunnelProbe(new InetSocketAddress(LOOP, tcp.getLocalPort()), 500,
                    both ? DnsTunnelProbe.Protocol.BOTH : DnsTunnelProbe.Protocol.TCP);
            DnsTunnelProbe.Result result = probe.run();
            check(result.passed == (mode == 0) && result.tcpPassed == (mode == 0), "TCP result for mode " + mode);
            check(result.udpPassed == both, "Conflated UDP and TCP results");
            if (mode != 0) check(result.failure == (mode == 4 ? DnsTunnelProbe.Failure.TCP_RESOLVER_CHECK
                    : DnsTunnelProbe.Failure.TCP_BLOCK_CHECK), "Wrong TCP failure stage");
            check(serving.get(2, TimeUnit.SECONDS) == (mode >= 1 && mode <= 3 ? 1 : 2), "Query count");
            if (udpServing != null) udpServing.get(2, TimeUnit.SECONDS);
        } finally { workers.shutdownNow(); }
    }

    private static void cancellation() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch arrived = new CountDownLatch(1);
        try (ServerSocket tcp = new ServerSocket(0, 1, LOOP)) {
            tcp.setSoTimeout(1000);
            Future<?> serving = workers.submit(() -> {
                try (Socket socket = tcp.accept()) {
                    socket.setSoTimeout(1500);
                    read(new DataInputStream(socket.getInputStream())); arrived.countDown();
                    check(socket.getInputStream().read() == -1, "Cancelled TCP socket remained open");
                } catch (IOException problem) { throw new RuntimeException(problem); }
            });
            DnsTunnelProbe probe = new DnsTunnelProbe(new InetSocketAddress(LOOP, tcp.getLocalPort()), 5000,
                    DnsTunnelProbe.Protocol.TCP);
            Future<DnsTunnelProbe.Result> result = workers.submit(probe::run);
            check(arrived.await(1, TimeUnit.SECONDS), "No TCP local check");
            probe.close();
            check(result.get(1, TimeUnit.SECONDS).failure == DnsTunnelProbe.Failure.CANCELLED, "Cancellation failed");
            serving.get(2, TimeUnit.SECONDS);
        } finally { workers.shutdownNow(); }
    }
    private static byte[] read(DataInputStream in) throws IOException {
        int size = in.readUnsignedShort();
        if (size < 12 || size > 4096) throw new IOException("Bad test frame");
        byte[] data = new byte[size]; in.readFully(data); return data;
    }
    private static void writeSplit(DataOutputStream out, byte[] data) throws IOException {
        out.writeByte(data.length >>> 8); out.flush();
        out.writeByte(data.length); out.write(data); out.flush();
    }
    private static byte[] block(byte[] query) { return new DnsPolicy(host -> DomainPolicy.Decision.BLOCK).evaluate(query).response; }
    private static byte[] answer(byte[] query) {
        byte[] response = Arrays.copyOf(query, query.length + 16);
        response[2] = (byte) 0x81; response[3] = (byte) 0x80; response[7] = 1;
        byte[] record = {(byte) 0xc0, 12, 0, 1, 0, 1, 0, 0, 0, 1, 0, 4, (byte) 192, 0, 2, 1};
        System.arraycopy(record, 0, response, query.length, record.length); return response;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
