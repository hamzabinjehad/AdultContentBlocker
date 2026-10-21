package app.hisn.core;

import java.util.Arrays;
import java.util.Random;

public final class Ipv4TcpPacketTest {
    private static final byte[] SERVER = {10, 111, 0, 2};
    private static final byte[] PEER = {(byte) 192, 0, 2, (byte) 128};

    public static void main(String[] args) {
        basicAndOwnership();
        options();
        rejectMalformed();
        responseBounds();
        fuzz();
        System.out.println("Ipv4TcpPacket tests passed (TCP checksums, bounds, options, unsigned sequences and ownership)");
    }

    private static void basicAndOwnership() {
        byte[] data = {0, 12, 1, 2, 3};
        byte[] raw = request(data, new byte[0], Ipv4TcpPacket.ACK | Ipv4TcpPacket.PSH);
        byte[] server = SERVER.clone();
        Ipv4TcpPacket packet = require(raw, server);
        check(packet.flowId == (0xc0000280L << 16 | 43123L), "unsigned flow ID");
        check(packet.sequence == 0xffffffffL && packet.acknowledgement == 0x80000000L,
                "unsigned sequence parsing");
        check(packet.sourcePort == 43123 && packet.flags == 24 && packet.window == 4096
                && packet.mss == 536 && Arrays.equals(packet.payload, data), "extracted TCP fields");
        Arrays.fill(raw, (byte) 0);
        Arrays.fill(server, (byte) 0);
        check(Arrays.equals(packet.payload, data), "parse clones input payload");
        Arrays.fill(packet.payload, (byte) 88);
        byte[] reply = packet.response(0x100000001L, -1L, Ipv4TcpPacket.ACK, 8192, data);
        check(reply.length == 45 && u16(reply, 20) == 53 && u16(reply, 22) == 43123,
                "reversed TCP ports");
        check(Arrays.equals(Arrays.copyOfRange(reply, 12, 16), SERVER)
                && Arrays.equals(Arrays.copyOfRange(reply, 16, 20), PEER), "owned reversed addresses");
        check(u32(reply, 24) == 1 && u32(reply, 28) == 0xffffffffL, "response sequence wrap");
        check((reply[32] & 255) == 0x50 && (reply[33] & 255) == 16
                && u16(reply, 34) == 8192, "plain reply header");
        validChecksums(reply);
        Arrays.fill(data, (byte) 0);
        check(reply[42] == 1 && reply[44] == 3, "response clones caller data");
        byte[] second = packet.response(1, 2, Ipv4TcpPacket.RST, 0, new byte[0]);
        reply[12] = 99;
        check(second[12] == SERVER[0], "response buffers independent");

        byte[] max = request(new byte[1460], new byte[0], Ipv4TcpPacket.ACK);
        check(require(max, SERVER).payload.length == 1460, "MTU sized incoming payload");
        byte[] withIpOptions = new byte[48];
        byte[] syn = request(new byte[0], new byte[0], Ipv4TcpPacket.SYN);
        System.arraycopy(syn, 0, withIpOptions, 0, 20);
        System.arraycopy(syn, 20, withIpOptions, 28, 20);
        withIpOptions[0] = 0x47;
        withIpOptions[20] = 1; withIpOptions[21] = 7; withIpOptions[22] = 3;
        withIpOptions[23] = 4; withIpOptions[24] = 0;
        fixChecksums(withIpOptions);
        require(withIpOptions, SERVER);
        withIpOptions[22] = 12;
        fixChecksums(withIpOptions);
        reject(withIpOptions, "malformed IPv4 option");
    }

    private static void options() {
        byte[] mss = {2, 4, 5, (byte) 180};
        Ipv4TcpPacket syn = require(request(new byte[0], mss, Ipv4TcpPacket.SYN), SERVER);
        check(syn.mss == 1460, "peer MSS");
        byte[] reply = syn.response(123, 0, Ipv4TcpPacket.SYN | Ipv4TcpPacket.ACK,
                2400, new byte[0]);
        check(reply.length == 44 && (reply[32] & 255) == 0x60 && reply[40] == 2
                && reply[41] == 4 && u16(reply, 42) == 1200, "SYN advertises only MSS1200");
        validChecksums(reply);
        check(require(request(new byte[0], new byte[0], Ipv4TcpPacket.SYN), SERVER).mss == 536,
                "default MSS");
        require(request(new byte[0], new byte[] {1, 1, 30, 2}, Ipv4TcpPacket.SYN), SERVER);
        require(request(new byte[0], new byte[] {0, (byte) 255, 0, 0}, Ipv4TcpPacket.SYN), SERVER);
        require(request(new byte[0], new byte[] {1, 1, 1, 1}, Ipv4TcpPacket.SYN), SERVER);
        reject(request(new byte[0], new byte[] {2, 3, 1, 0}, Ipv4TcpPacket.SYN), "short MSS");
        reject(request(new byte[0], new byte[] {2, 4, 0, 0}, Ipv4TcpPacket.SYN), "zero MSS");
        reject(request(new byte[0], mss, Ipv4TcpPacket.ACK), "MSS outside SYN");
        reject(request(new byte[0], new byte[] {2, 4, 5, 0, 2, 4, 5, 0}, Ipv4TcpPacket.SYN),
                "duplicate MSS");
        reject(request(new byte[0], new byte[] {30, 0, 0, 0}, Ipv4TcpPacket.SYN), "zero option length");
        reject(request(new byte[0], new byte[] {30, 1, 0, 0}, Ipv4TcpPacket.SYN), "one byte option length");
        reject(request(new byte[0], new byte[] {30, 5, 0, 0}, Ipv4TcpPacket.SYN), "option past header");
        reject(request(new byte[0], new byte[] {1, 1, 1, 30}, Ipv4TcpPacket.SYN), "missing option length");
    }

    private static void rejectMalformed() {
        byte[] good = request(new byte[] {1}, new byte[0], Ipv4TcpPacket.ACK);
        check(Ipv4TcpPacket.parse(null, SERVER) == null && Ipv4TcpPacket.parse(good, null) == null
                && Ipv4TcpPacket.parse(good, new byte[16]) == null, "null / address shape");
        for (int i = 0; i < good.length; i++) reject(Arrays.copyOf(good, i), "truncated " + i);
        reject(Arrays.copyOf(good, good.length + 1), "trailing packet bytes");
        byte[] bad = good.clone(); bad[8] ^= 1; reject(bad, "IP checksum");
        bad = good.clone(); bad[40] ^= 1; reject(bad, "TCP checksum");
        bad = good.clone(); bad[36] = 0; bad[37] = 0; reject(bad, "omitted TCP checksum");
        bad = good.clone(); bad[0] = 0x65; fixChecksums(bad); reject(bad, "IPv6");
        bad = good.clone(); bad[0] = 0x44; reject(bad, "short IP header");
        bad = good.clone(); bad[9] = 17; fixChecksums(bad); reject(bad, "non TCP");
        bad = good.clone(); bad[19] = 3; fixChecksums(bad); reject(bad, "different server");
        bad = good.clone(); put16(bad, 20, 0); fixChecksums(bad); reject(bad, "source port zero");
        bad = good.clone(); put16(bad, 22, 853); fixChecksums(bad); reject(bad, "DoT port 853");
        bad = good.clone(); put16(bad, 22, 443); fixChecksums(bad); reject(bad, "HTTPS port");
        bad = good.clone(); bad[32] = 0x40; fixChecksums(bad); reject(bad, "short TCP header");
        bad = good.clone(); bad[32] = (byte) 0xf0; fixChecksums(bad); reject(bad, "oversized TCP header");
        for (int bit = 1; bit <= 8; bit <<= 1) {
            bad = good.clone(); bad[32] |= bit; fixChecksums(bad); reject(bad, "TCP reserved bit " + bit);
        }
        for (int bits : new int[] {0x8000, 0x2000, 1, 0x1fff}) {
            bad = good.clone(); put16(bad, 6, bits); fixChecksums(bad); reject(bad, "IP fragment / reserved");
        }
        bad = good.clone(); put16(bad, 6, 0x4000); fixChecksums(bad); require(bad, SERVER);
        reject(request(new byte[1461], new byte[0], Ipv4TcpPacket.ACK), "over MTU");
    }

    private static void responseBounds() {
        Ipv4TcpPacket packet = require(request(new byte[0], new byte[0], Ipv4TcpPacket.SYN), SERVER);
        validChecksums(packet.response(0, 0, Ipv4TcpPacket.ACK, 65535, new byte[1200]));
        invalidResponse(packet, 0, 0, null);
        invalidResponse(packet, 0, 0, new byte[1201]);
        invalidResponse(packet, -1, 0, new byte[0]);
        invalidResponse(packet, 256, 0, new byte[0]);
        invalidResponse(packet, 0, -1, new byte[0]);
        invalidResponse(packet, 0, 65536, new byte[0]);
    }

    private static void fuzz() {
        Random random = new Random(20261020);
        for (int i = 0; i < 30000; i++) {
            byte[] arbitrary = new byte[random.nextInt(1601)];
            random.nextBytes(arbitrary);
            Ipv4TcpPacket.parse(arbitrary, SERVER);
        }
        for (int i = 0; i < 4000; i++) {
            byte[] raw = request(new byte[random.nextInt(1400)], new byte[0], Ipv4TcpPacket.ACK);
            random.nextBytes(raw);
            raw[0] = 0x45; raw[9] = 6; raw[32] = (byte) (random.nextInt(256));
            put16(raw, 6, 0); put16(raw, 20, 1234); put16(raw, 22, 53);
            System.arraycopy(SERVER, 0, raw, 16, 4);
            fixChecksums(raw);
            Ipv4TcpPacket.parse(raw, SERVER);
        }
    }

    private static void invalidResponse(Ipv4TcpPacket packet, int flags, int window, byte[] data) {
        try {
            packet.response(0, 0, flags, window, data);
            throw new AssertionError("invalid response accepted");
        } catch (IllegalArgumentException expected) { }
    }

    private static Ipv4TcpPacket require(byte[] raw, byte[] server) {
        Ipv4TcpPacket result = Ipv4TcpPacket.parse(raw, server);
        if (result == null) throw new AssertionError("Valid TCP packet rejected");
        return result;
    }

    private static void reject(byte[] raw, String why) {
        check(Ipv4TcpPacket.parse(raw, SERVER) == null, "accepted " + why);
    }

    private static byte[] request(byte[] data, byte[] options, int flags) {
        if (options.length % 4 != 0) throw new AssertionError("unaligned test options");
        byte[] out = new byte[40 + options.length + data.length];
        out[0] = 0x45; out[8] = 64; out[9] = 6;
        System.arraycopy(PEER, 0, out, 12, 4);
        System.arraycopy(SERVER, 0, out, 16, 4);
        put16(out, 20, 43123); put16(out, 22, 53);
        put32(out, 24, 0xffffffffL); put32(out, 28, 0x80000000L);
        out[32] = (byte) (((20 + options.length) / 4) << 4);
        out[33] = (byte) flags; put16(out, 34, 4096);
        System.arraycopy(options, 0, out, 40, options.length);
        System.arraycopy(data, 0, out, 40 + options.length, data.length);
        fixChecksums(out);
        return out;
    }

    // Independent checksum implementation, rather than importing production helpers.
    private static long sum(byte[] data, int offset, int length) {
        long sum = 0;
        for (int i = 0; i < length; i++) sum += (long) (data[offset + i] & 255) << (i % 2 == 0 ? 8 : 0);
        while (sum > 65535) sum = (sum & 65535) + (sum >>> 16);
        return sum;
    }

    private static int checksum(byte[] data, int offset, int length, long initial) {
        long value = initial + sum(data, offset, length);
        while (value > 65535) value = (value & 65535) + (value >>> 16);
        return (int) (~value & 65535);
    }

    private static void fixChecksums(byte[] data) {
        int header = (data[0] & 15) * 4;
        put16(data, 2, data.length);
        put16(data, 10, 0);
        put16(data, header + 16, 0);
        put16(data, 10, checksum(data, 0, header, 0));
        put16(data, header + 16, checksum(data, header, data.length - header,
                sum(data, 12, 8) + 6 + data.length - header));
    }

    private static void validChecksums(byte[] data) {
        check(checksum(data, 0, 20, 0) == 0, "response IP checksum");
        check(checksum(data, 20, data.length - 20,
                sum(data, 12, 8) + 6 + data.length - 20) == 0, "response TCP checksum");
        check(u16(data, 2) == data.length, "response total length");
    }

    private static int u16(byte[] data, int offset) {
        return ((data[offset] & 255) << 8) | (data[offset + 1] & 255);
    }

    private static long u32(byte[] data, int offset) {
        return (long) u16(data, offset) << 16 | u16(data, offset + 2);
    }

    private static void put16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 8); data[offset + 1] = (byte) value;
    }

    private static void put32(byte[] data, int offset, long value) {
        data[offset] = (byte) (value >>> 24); data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8); data[offset + 3] = (byte) value;
    }

    private static void check(boolean okay, String message) {
        if (!okay) throw new AssertionError(message);
    }
}
