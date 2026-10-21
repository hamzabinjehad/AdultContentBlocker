package app.hisn.core;

public final class Ipv4DnsPacketTest {
    private static final byte[] SERVER = {10, 111, 0, 2};
    private static byte[] packet(byte[] dns) {
        byte[] request = new byte[28 + dns.length];
        request[0] = 0x45; request[8] = 64; request[9] = 17;
        Ipv4DnsPacket.put16(request, 2, request.length);
        request[12] = 10; request[13] = 111; request[15] = 1;
        System.arraycopy(SERVER, 0, request, 16, 4);
        Ipv4DnsPacket.put16(request, 20, 12345);
        Ipv4DnsPacket.put16(request, 22, 53);
        Ipv4DnsPacket.put16(request, 24, dns.length + 8);
        System.arraycopy(dns, 0, request, 28, dns.length);
        Ipv4DnsPacket.put16(request, 10, Ipv4DnsPacket.checksum(request, 0, 20, 0));
        return request;
    }

    public static void main(String[] args) {
        byte[] query = DnsPolicyTest.query("blocked.example", 1);
        byte[] request = packet(query);
        Ipv4DnsPacket parsed = Ipv4DnsPacket.parse(request, SERVER);
        if (parsed == null || !java.util.Arrays.equals(parsed.dns, query)) throw new AssertionError("UDP payload extraction");
        byte[] response = parsed.response(query);
        if (response[12] != 10 || response[15] != 2 || response[19] != 1
                || Ipv4DnsPacket.checksum(response, 0, 20, 0) != 0) throw new AssertionError("Reply address or IP checksum");
        int pseudo = 17 + response.length - 20;
        for (int i = 12; i < 20; i += 2) pseudo += ((response[i] & 255) << 8) | (response[i + 1] & 255);
        if (Ipv4DnsPacket.checksum(response, 20, response.length - 20, pseudo) != 0) throw new AssertionError("UDP checksum");
        byte[] bad = request.clone(); bad[8] ^= 1;
        if (Ipv4DnsPacket.parse(bad, SERVER) != null) throw new AssertionError("Corrupt IP accepted");
        bad = request.clone(); bad[26] = 1;
        if (Ipv4DnsPacket.parse(bad, SERVER) != null) throw new AssertionError("Corrupt UDP accepted");
        bad = request.clone(); bad[6] = 0x20;
        Ipv4DnsPacket.put16(bad, 10, 0);
        Ipv4DnsPacket.put16(bad, 10, Ipv4DnsPacket.checksum(bad, 0, 20, 0));
        if (Ipv4DnsPacket.parse(bad, SERVER) != null) throw new AssertionError("Fragment accepted");
        bad = request.clone(); bad[6] = (byte) 0x80;
        Ipv4DnsPacket.put16(bad, 10, 0);
        Ipv4DnsPacket.put16(bad, 10, Ipv4DnsPacket.checksum(bad, 0, 20, 0));
        if (Ipv4DnsPacket.parse(bad, SERVER) != null) throw new AssertionError("Reserved IP flag accepted");
        java.util.Random random = new java.util.Random(2018);
        for (int i = 0; i < 20000; i++) {
            byte[] arbitrary = new byte[random.nextInt(1600)];
            random.nextBytes(arbitrary);
            Ipv4DnsPacket.parse(arbitrary, SERVER);
        }
        System.out.println("Ipv4DnsPacket tests passed (checksums, fragments and malformed input)");
    }
}
