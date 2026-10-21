package app.hisn.core;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Packet-level protocol tests. No sockets, public DNS or Android permissions are used. */
public final class DnsTcpServerTest {
    static final byte[] SERVER = {10, 111, 0, 2};
    static final byte[] EMPTY = new byte[0];
    static final int ACK = Ipv4TcpPacket.ACK, SYN = Ipv4TcpPacket.SYN, FIN = Ipv4TcpPacket.FIN, RST = Ipv4TcpPacket.RST;
    static long add(long value, long count) { return (value + count) & 0xffffffffL; }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    static Ipv4TcpPacket packet(int port, long seq, long ack, int flags, int window, byte[] data, int mss) {
        int tcp = (flags & SYN) != 0 ? 24 : 20;
        byte[] bytes = new byte[20 + tcp + data.length];
        bytes[0] = 0x45; bytes[8] = 64; bytes[9] = 6;
        put16(bytes, 2, bytes.length);
        bytes[12] = 10; bytes[13] = 111; bytes[15] = 1;
        System.arraycopy(SERVER, 0, bytes, 16, 4);
        put16(bytes, 20, port); put16(bytes, 22, 53);
        put32(bytes, 24, seq); put32(bytes, 28, ack);
        bytes[32] = (byte) (tcp / 4 << 4); bytes[33] = (byte) flags;
        put16(bytes, 34, window);
        if (tcp == 24) { bytes[40] = 2; bytes[41] = 4; put16(bytes, 42, mss); }
        System.arraycopy(data, 0, bytes, 20 + tcp, data.length);
        put16(bytes, 10, Ipv4DnsPacket.checksum(bytes, 0, 20, 0));
        int sum = 6 + tcp + data.length;
        for (int i = 12; i < 20; i += 2) sum += u16(bytes, i);
        put16(bytes, 36, Ipv4DnsPacket.checksum(bytes, 20, tcp + data.length, sum));
        Ipv4TcpPacket result = Ipv4TcpPacket.parse(bytes, SERVER);
        check(result != null, "Invalid test packet");
        return result;
    }

    static final class Client {
        final DnsTcpServer server;
        final int port;
        long client = 0xfffffff0L, next;
        long now;
        int window = 8192;
        Client(int mss) { this(new DnsTcpServer(() -> 0xfffffff8L), 41000, mss); }
        Client(DnsTcpServer server, int port, int mss) {
            this.server = server; this.port = port;
            Ipv4TcpPacket syn = packet(port, client, 0, SYN | 0xc0, window, EMPTY, mss);
            DnsTcpServer.Result first = server.receive(syn, now);
            check(first.packets.size() == 1 && (first.packets.get(0)[33] & 255) == (SYN | ACK), "SYN ACK");
            next = add(u32(first.packets.get(0), 24), 1);
            check(u32(first.packets.get(0), 28) == add(client, 1), "SYN acknowledged");
            byte[] repeated = server.receive(syn, now).packets.get(0);
            check(u32(repeated, 24) == add(next, -1), "Duplicate SYN changed ISN");
            client = add(client, 1);
            server.receive(packet(port, client, next, ACK, window, EMPTY, mss), now);
        }
        DnsTcpServer.Result raw(long seq, long ack, int flags, byte[] bytes) {
            return server.receive(packet(port, seq, ack, flags, window, bytes, 1200), ++now);
        }
        DnsTcpServer.Result send(byte[] bytes) {
            DnsTcpServer.Result result = raw(client, next, ACK, bytes);
            client = add(client, bytes.length);
            return result;
        }
        List<DnsTcpServer.Request> drain(DnsTcpServer.Result result, byte[] expected, int mss) {
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            List<DnsTcpServer.Request> requests = new ArrayList<>(result.requests);
            List<byte[]> packets = new ArrayList<>(result.packets);
            int iterations = 0;
            while (!packets.isEmpty()) {
                check(++iterations < 10000, "Unbounded response loop");
                byte[] out = packets.remove(0);
                int header = 20 + ((out[32] & 255) >>> 4) * 4;
                int length = out.length - header;
                check(length <= mss && length <= window, "Exceeded peer MSS/window");
                if (length == 0) continue;
                check(u32(out, 24) == next, "Wrong response sequence");
                received.write(out, header, length);
                next = add(next, length);
                DnsTcpServer.Result acked = raw(client, next, ACK, EMPTY);
                packets.addAll(acked.packets); requests.addAll(acked.requests);
            }
            check(Arrays.equals(received.toByteArray(), frame(expected)), "Framed DNS reply mismatch");
            return requests;
        }
    }

    public static void main(String[] args) {
        framingAndReuse(); overlapAndFuture(); windowsAndPartialAck(); closingAndTokens(); boundsAndTimers();
        System.out.println("DnsTcpServer tests passed (handshake, framing, reuse, pipeline, wrap, windows, retries, FIN/RST and bounds)");
    }

    static void framingAndReuse() {
        Client c = new Client(80);
        byte[] dns = DnsPolicyTest.query("blocked.example", 1), bytes = frame(dns);
        check(c.send(Arrays.copyOfRange(bytes, 0, 1)).requests.isEmpty(), "Partial prefix dispatched");
        check(c.send(Arrays.copyOfRange(bytes, 1, 5)).requests.isEmpty(), "Partial body dispatched");
        DnsTcpServer.Request first = only(c.send(Arrays.copyOfRange(bytes, 5, bytes.length)).requests);
        check(Arrays.equals(first.dns, dns), "Split message changed");
        byte[] second = DnsPolicyTest.query("second.example", 1);
        byte[] third = DnsPolicyTest.query("third.example", 1);
        c.send(concat(frame(second), frame(third)));
        byte[] large = Arrays.copyOf(dns, 900);
        DnsTcpServer.Request next = only(c.drain(c.server.complete(first, large, ++c.now), large, 80));
        check(Arrays.equals(next.dns, second), "Pipeline order");
        DnsTcpServer.Request last = only(c.drain(c.server.complete(next, second, ++c.now), second, 80));
        check(Arrays.equals(last.dns, third), "Third pipeline query lost");
        check(c.drain(c.server.complete(last, third, ++c.now), third, 80).isEmpty(), "Extra resolver work");
        check(c.send(frame(dns)).requests.size() == 1, "Connection not reusable");

        // Data on the third handshake packet is valid and must not be discarded.
        DnsTcpServer s = new DnsTcpServer(() -> 42);
        s.receive(packet(45000, 9, 0, SYN, 8192, EMPTY, 1200), 0);
        check(s.receive(packet(45000, 10, 43, ACK, 8192, bytes, 1200), 1).requests.size() == 1,
                "Final handshake ACK with data lost");
    }

    static void overlapAndFuture() {
        Client c = new Client(1200);
        byte[] dns = DnsPolicyTest.query("one.example", 1), bytes = frame(dns);
        long start = c.client;
        check(c.raw(add(start, 5), c.next, ACK, bytes).requests.isEmpty(), "Future bytes accepted");
        c.send(Arrays.copyOfRange(bytes, 0, 8));
        DnsTcpServer.Result rest = c.raw(add(start, 4), c.next, ACK, Arrays.copyOfRange(bytes, 4, bytes.length));
        c.client = add(start, bytes.length);
        DnsTcpServer.Request work = only(rest.requests);
        check(Arrays.equals(work.dns, dns), "Overlap corrupts DNS");
        check(c.raw(start, c.next, ACK, bytes).requests.isEmpty(), "Duplicate query executed");
        check(c.raw(c.client, add(c.next, 100), ACK, frame(dns)).requests.isEmpty(), "Future ACK accepted data");
        c.drain(c.server.complete(work, dns, ++c.now), dns, 1200);
    }

    static void windowsAndPartialAck() {
        Client c = new Client(70);
        byte[] dns = DnsPolicyTest.query("window.example", 1);
        DnsTcpServer.Request work = only(c.send(frame(dns)).requests);
        c.window = 0; c.raw(c.client, c.next, ACK, EMPTY);
        check(c.server.complete(work, dns, ++c.now).packets.isEmpty(), "Sent into zero window");
        c.window = 9;
        c.drain(c.raw(c.client, c.next, ACK, EMPTY), dns, 70);

        c = new Client(1200);
        work = only(c.send(frame(dns)).requests);
        byte[] out = c.server.complete(work, dns, ++c.now).packets.get(0);
        long sequence = u32(out, 24);
        c.raw(c.client, add(sequence, 5), ACK, EMPTY);
        byte[] retry = c.server.poll(c.now + 1001).packets.get(0);
        check(u32(retry, 24) == add(sequence, 5) && retry.length == out.length - 5, "Partial ACK retry wrong");
        check(c.server.complete(work, dns, ++c.now).packets.isEmpty(), "Completed token reused");
    }

    static void closingAndTokens() {
        Client c = new Client(1200);
        byte[] dns = DnsPolicyTest.query("fin.example", 1);
        DnsTcpServer.Result result = c.raw(c.client, c.next, ACK | FIN, frame(dns));
        c.client = add(c.client, frame(dns).length + 1);
        DnsTcpServer.Request work = only(result.requests);
        result = c.server.complete(work, dns, ++c.now);
        byte[] response = result.packets.get(0);
        c.next = add(c.next, response.length - 40);
        DnsTcpServer.Result fin = c.raw(c.client, c.next, ACK, EMPTY);
        check(fin.packets.size() == 1 && (fin.packets.get(0)[33] & FIN) != 0, "Half-close omitted response/FIN");
        c.next = add(c.next, 1);
        c.raw(c.client, c.next, ACK, EMPTY);
        check(!c.server.isPending(work), "Closed work remained live");

        c = new Client(1200);
        work = only(c.send(frame(dns)).requests);
        c.raw(add(c.client, 1), c.next, RST, EMPTY);
        check(c.server.isPending(work), "Wrong-sequence RST closed flow");
        c.raw(c.client, c.next, RST, EMPTY);
        check(c.server.complete(work, dns, c.now).packets.isEmpty(), "Late reset work published");
        c = new Client(1200); work = only(c.send(frame(dns)).requests);
        c.server.invalidate();
        check(c.server.complete(work, dns, c.now).packets.isEmpty(), "Invalidated work published");
        new Client(c.server, 41000, 1200);
        check(c.server.complete(work, dns, c.now).packets.isEmpty(), "Old flow token matched a new flow");
        c.server.close();
        check(c.server.receive(packet(45000, 1, 0, SYN, 8192, EMPTY, 1200), 1).packets.isEmpty(), "Closed endpoint reopened");
    }

    static void boundsAndTimers() {
        DnsTcpServer s = new DnsTcpServer(() -> 5);
        for (int i = 0; i < 16; i++) s.receive(packet(42000 + i, 1, 0, SYN, 8192, EMPTY, 1200), 0);
        byte[] capped = s.receive(packet(43000, 1, 0, SYN, 8192, EMPTY, 1200), 0).packets.get(0);
        check((capped[33] & RST) != 0, "Flow cap bypass");
        check(s.poll(1000).packets.size() == 16, "SYN retransmissions missing");
        check(s.poll(10000).packets.size() == 16, "Handshake deadline not enforced");
        check((s.receive(packet(43000, 1, 0, SYN, 8192, EMPTY, 1200), 10001).packets.get(0)[33] & SYN) != 0,
                "Expired flow slots not reusable");
        Client c = new Client(1200);
        c.send(new byte[]{0}); c.now = 9000; c.send(new byte[]{30});
        check((c.server.poll(10002).packets.get(0)[33] & RST) != 0, "Partial-frame deadline slid with each fragment");
        c = new Client(1200);
        check((c.send(new byte[]{0, 11}).packets.get(0)[33] & RST) != 0, "Tiny DNS frame accepted");
        c = new Client(1200);
        check((c.send(new byte[]{16, 1}).packets.get(0)[33] & RST) != 0, "Oversize DNS frame accepted");
        c = new Client(1200);
        byte[] dns = DnsPolicyTest.query("cap.example", 1);
        byte[] excess = frame(dns);
        for (int i = 0; i < 6; i++) excess = concat(excess, frame(dns));
        DnsTcpServer.Result rejected = c.send(excess);
        check(rejected.requests.isEmpty() && (rejected.packets.get(0)[33] & RST) != 0, "Pipeline cap left live work");
    }

    static DnsTcpServer.Request only(List<DnsTcpServer.Request> requests) {
        check(requests.size() == 1, "Expected one request, got " + requests.size()); return requests.get(0);
    }
    static byte[] frame(byte[] dns) {
        byte[] result = new byte[dns.length + 2]; put16(result, 0, dns.length);
        System.arraycopy(dns, 0, result, 2, dns.length); return result;
    }
    static byte[] concat(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length); return result;
    }
    static int u16(byte[] b, int o) { return ((b[o] & 255) << 8) | (b[o + 1] & 255); }
    static long u32(byte[] b, int o) { return ((long) u16(b, o) << 16) | u16(b, o + 2); }
    static void put16(byte[] b, int o, int v) { b[o] = (byte) (v >>> 8); b[o + 1] = (byte) v; }
    static void put32(byte[] b, int o, long v) { put16(b, o, (int) (v >>> 16)); put16(b, o + 2, (int) v); }
}
