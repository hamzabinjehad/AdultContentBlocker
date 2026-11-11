package app.hisn.core;

public final class Ipv6DnsPacketTest {
    private static final byte[] SERVER = {(byte)0xfd,0,0x01,0x11,0,0,0,0,0,0,0,0,0,0,0,0x02};
    private static final byte[] APP = {(byte)0xfd,0,0x01,0x11,0,0,0,0,0,0,0,0,0,0,0,0x01};

    private static byte[] packet(byte[] dns) {
        byte[] request = new byte[48 + dns.length];
        request[0] = 0x60; request[6] = 17; request[7] = 64;
        Ipv6DnsPacketTest.put16(request, 4, request.length - 40);
        System.arraycopy(APP, 0, request, 8, 16);
        System.arraycopy(SERVER, 0, request, 24, 16);
        Ipv6DnsPacketTest.put16(request, 40, 12345);
        Ipv6DnsPacketTest.put16(request, 42, 53);
        Ipv6DnsPacketTest.put16(request, 44, dns.length + 8);
        System.arraycopy(dns, 0, request, 48, dns.length);
        int pseudo = 17 + dns.length + 8;
        for (int i = 8; i < 40; i += 2) pseudo += Ipv6DnsPacketTest.u16(request, i);
        Ipv6DnsPacketTest.put16(request, 46,
                Ipv6DnsPacketTest.checksum(request, 40, dns.length + 8, pseudo));
        return request;
    }

    public static void main(String[] args) {
        byte[] query = DnsPolicyTest.query("blocked.example", 1);
        Ipv6DnsPacket parsed = Ipv6DnsPacket.parse(packet(query), SERVER);
        if (parsed == null || !java.util.Arrays.equals(parsed.dns, query)) throw new AssertionError("IPv6 UDP payload extraction");
        byte[] reply = parsed.response(query);
        if (reply[0] != 0x60 || reply[6] != 17 || reply[7] != 64 || reply[40] != 0 || reply[41] != 53
                || !java.util.Arrays.equals(java.util.Arrays.copyOfRange(reply, 8, 24), SERVER)
                || !java.util.Arrays.equals(java.util.Arrays.copyOfRange(reply, 24, 40), APP)
                || u16(reply, 4) != reply.length - 40) throw new AssertionError("IPv6 reply header");
        int pseudo = 17 + reply.length - 40;
        for (int i = 8; i < 40; i += 2) pseudo += u16(reply, i);
        if (checksum(reply, 40, reply.length - 40, pseudo) != 0) throw new AssertionError("IPv6 UDP checksum");
        byte[] bad = packet(query); bad[6] = 44; // extension header / not UDP
        if (Ipv6DnsPacket.parse(bad, SERVER) != null) throw new AssertionError("non-UDP IPv6 accepted");
        bad = packet(query); bad[0] = 0x40;
        if (Ipv6DnsPacket.parse(bad, SERVER) != null) throw new AssertionError("IPv4 accepted as IPv6");
        bad = packet(query); bad[46] ^= 1;
        if (Ipv6DnsPacket.parse(bad, SERVER) != null) throw new AssertionError("bad IPv6 UDP checksum accepted");
        bad = packet(query); bad[42] = 54;
        if (Ipv6DnsPacket.parse(bad, SERVER) != null) throw new AssertionError("wrong IPv6 UDP port accepted");
        java.util.Random random = new java.util.Random(2026);
        for (int i = 0; i < 20000; i++) {
            byte[] arbitrary = new byte[random.nextInt(1600)];
            random.nextBytes(arbitrary);
            Ipv6DnsPacket.parse(arbitrary, SERVER);
        }
        System.out.println("Ipv6DnsPacket tests passed (checksums, headers and malformed input)");
    }

    private static int u16(byte[] data, int i) { return ((data[i] & 255) << 8) | (data[i + 1] & 255); }
    private static void put16(byte[] data, int i, int v) { data[i] = (byte) (v >>> 8); data[i + 1] = (byte) v; }
    private static int checksum(byte[] data, int start, int length, int sum) {
        int end = start + length;
        for (int i = start; i < end; i += 2) {
            sum += (data[i] & 255) << 8;
            if (i + 1 < end) sum += data[i + 1] & 255;
        }
        while ((sum >>> 16) != 0) sum = (sum & 65535) + (sum >>> 16);
        return (~sum) & 65535;
    }
}
