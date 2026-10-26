package app.hisn.core;

import java.util.Arrays;
import java.util.Random;

public final class DnsPacketKindTest {
    private static final byte[] SERVER = {10, 111, 0, 2};

    private static void expect(DnsPacketKind expected, byte[] packet) {
        DnsPacketKind actual = DnsPacketKind.classify(packet, SERVER);
        if (actual != expected) throw new AssertionError("Expected " + expected + ", got " + actual);
        if (actual == DnsPacketKind.UDP_DNS && Ipv4DnsPacket.parse(packet, SERVER) == null)
            throw new AssertionError("Diagnostic bypassed packet adapter");
    }

    private static byte[] packet(int protocol, int payload) {
        byte[] packet = new byte[20 + payload];
        packet[0] = 0x45;
        packet[8] = 64;
        packet[9] = (byte) protocol;
        Ipv4DnsPacket.put16(packet, 2, packet.length);
        packet[12] = 10; packet[13] = 111; packet[15] = 1;
        System.arraycopy(SERVER, 0, packet, 16, 4);
        if (payload >= 4) {
            Ipv4DnsPacket.put16(packet, 20, 12345);
            Ipv4DnsPacket.put16(packet, 22, 53);
        }
        if (protocol == 17 && payload >= 8) Ipv4DnsPacket.put16(packet, 24, payload);
        if (protocol == 6 && payload >= 20) packet[32] = 0x50;
        checksum(packet);
        return packet;
    }

    private static void checksum(byte[] packet) {
        Ipv4DnsPacket.put16(packet, 10, 0);
        Ipv4DnsPacket.put16(packet, 10,
                Ipv4DnsPacket.checksum(packet, 0, (packet[0] & 15) * 4, 0));
    }

    public static void main(String[] args) {
        byte[] udp = packet(17, 20);
        expect(DnsPacketKind.UDP_DNS, udp);
        byte[] before = udp.clone();
        DnsPacketKind.classify(udp, SERVER);
        if (!Arrays.equals(before, udp)) throw new AssertionError("Classifier modified packet");

        byte[] bad = udp.clone(); bad[8] ^= 1;
        expect(DnsPacketKind.MALFORMED, bad);
        bad = udp.clone(); Ipv4DnsPacket.put16(bad, 2, bad.length - 1); checksum(bad);
        expect(DnsPacketKind.MALFORMED, bad);
        bad = udp.clone(); bad[0] = 0x4f;
        expect(DnsPacketKind.MALFORMED, bad);
        bad = udp.clone(); Ipv4DnsPacket.put16(bad, 24, 19);
        expect(DnsPacketKind.MALFORMED, bad);
        bad = udp.clone(); bad[26] = 1;
        expect(DnsPacketKind.MALFORMED, bad);
        expect(DnsPacketKind.MALFORMED, packet(17, 7));
        expect(DnsPacketKind.MALFORMED, packet(17, 8));

        bad = udp.clone(); bad[6] = 0x20; checksum(bad);
        expect(DnsPacketKind.FRAGMENT, bad);
        bad = udp.clone(); bad[7] = 1; checksum(bad);
        expect(DnsPacketKind.FRAGMENT, bad);
        bad = udp.clone(); bad[6] = (byte) 0x80; checksum(bad);
        expect(DnsPacketKind.MALFORMED, bad);
        bad = udp.clone(); bad[19] = 3; checksum(bad);
        expect(DnsPacketKind.OTHER_UNSUPPORTED, bad);
        bad = udp.clone(); Ipv4DnsPacket.put16(bad, 22, 123);
        expect(DnsPacketKind.OTHER_UNSUPPORTED, bad);

        byte[] tcp = packet(6, 20);
        expect(DnsPacketKind.TCP_DNS_UNSUPPORTED, tcp);
        Ipv4DnsPacket.put16(tcp, 22, 853);
        expect(DnsPacketKind.TCP_TLS_PORT, tcp);
        Ipv4DnsPacket.put16(tcp, 22, 443);
        expect(DnsPacketKind.OTHER_UNSUPPORTED, tcp);
        Ipv4DnsPacket.put16(tcp, 22, 53); tcp[19] = 3; checksum(tcp);
        expect(DnsPacketKind.OTHER_UNSUPPORTED, tcp);
        tcp = packet(6, 20); tcp[32] = 0x40;
        expect(DnsPacketKind.MALFORMED, tcp);
        tcp[32] = 0x60;
        expect(DnsPacketKind.MALFORMED, tcp);
        expect(DnsPacketKind.MALFORMED, packet(6, 19));
        tcp = packet(6, 20); tcp[6] = 0x20; checksum(tcp);
        expect(DnsPacketKind.FRAGMENT, tcp);
        expect(DnsPacketKind.OTHER_UNSUPPORTED, packet(1, 8));

        byte[] ipv6 = new byte[40]; ipv6[0] = 0x60;
        expect(DnsPacketKind.OTHER_UNSUPPORTED, ipv6);
        ipv6[5] = 1;
        expect(DnsPacketKind.MALFORMED, ipv6);
        expect(DnsPacketKind.MALFORMED, null);
        if (DnsPacketKind.classify(udp, null) != DnsPacketKind.MALFORMED
                || DnsPacketKind.classify(udp, new byte[3]) != DnsPacketKind.MALFORMED)
            throw new AssertionError("Invalid server accepted");

        // Only unrelated traffic is benign; anything that may have been a DNS request is not.
        for (DnsPacketKind kind : DnsPacketKind.values()) {
            boolean benign = kind == DnsPacketKind.TCP_TLS_PORT || kind == DnsPacketKind.OTHER_UNSUPPORTED;
            if (kind.benign() != benign) throw new AssertionError("Wrong benign classification: " + kind);
        }

        Random random = new Random(20261020);
        for (int i = 0; i < 20000; i++) {
            byte[] arbitrary = new byte[random.nextInt(1600)];
            random.nextBytes(arbitrary);
            DnsPacketKind.classify(arbitrary, SERVER);
            byte[] mutation = udp.clone();
            mutation[random.nextInt(mutation.length)] = (byte) random.nextInt(256);
            expectAdapterInvariant(mutation);
        }
        System.out.println("DnsPacketKind tests passed (UDP adapter gate, unsupported TCP, port 853, fragments, benign split, checksums, bounds and fuzz)");
    }

    private static void expectAdapterInvariant(byte[] packet) {
        if (DnsPacketKind.classify(packet, SERVER) == DnsPacketKind.UDP_DNS
                && Ipv4DnsPacket.parse(packet, SERVER) == null)
            throw new AssertionError("Diagnostic accepted a rejected UDP packet");
    }
}
