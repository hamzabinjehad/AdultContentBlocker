package app.hisn.core;

import java.util.Arrays;

/** IPv4/UDP DNS packet adapter. Fragmented packets are rejected; IPv6/UDP has its own adapter. */
public final class Ipv4DnsPacket implements UdpDnsPacket {
    private final byte[] addresses;
    private final int clientPort;
    public final byte[] dns;

    @Override public byte[] getDns() { return dns; }

    private Ipv4DnsPacket(byte[] packet, int length, int header) {
        addresses = Arrays.copyOfRange(packet, 12, 20);
        clientPort = u16(packet, header);
        dns = Arrays.copyOfRange(packet, header + 8, length);
    }

    public static Ipv4DnsPacket parse(byte[] packet, byte[] serverAddress) {
        return packet == null ? null : parse(packet, packet.length, serverAddress);
    }

    /** Parses only the bytes read and owns the fields it needs after the buffer is reused. */
    public static Ipv4DnsPacket parse(byte[] packet, int length, byte[] serverAddress) {
        if (packet == null || serverAddress == null || serverAddress.length != 4
                || length < 40 || length > 1500 || length > packet.length || (packet[0] & 0xf0) != 0x40) return null;
        int header = (packet[0] & 15) * 4;
        if (header < 20 || header + 20 > length || u16(packet, 2) != length
                || (u16(packet, 6) & 0xbfff) != 0 || packet[9] != 17
                || checksum(packet, 0, header, 0) != 0) return null;
        for (int i = 0; i < 4; i++) if (packet[16 + i] != serverAddress[i]) return null;
        int udpLength = length - header;
        if (u16(packet, header + 2) != 53 || u16(packet, header + 4) != udpLength) return null;
        if (u16(packet, header + 6) != 0 && checksum(packet, header, udpLength, pseudoSum(packet, udpLength)) != 0)
            return null;
        return new Ipv4DnsPacket(packet, length, header);
    }

    @Override public byte[] response(byte[] payload) {
        if (payload == null || payload.length < 12 || payload.length > 1472) {
            throw new IllegalArgumentException("Response exceeds tunnel MTU");
        }
        byte[] out = new byte[28 + payload.length];
        out[0] = 0x45;
        put16(out, 2, out.length);
        out[8] = 64; out[9] = 17;
        System.arraycopy(addresses, 4, out, 12, 4);
        System.arraycopy(addresses, 0, out, 16, 4);
        put16(out, 20, 53);
        put16(out, 22, clientPort);
        put16(out, 24, payload.length + 8);
        System.arraycopy(payload, 0, out, 28, payload.length);
        put16(out, 10, checksum(out, 0, 20, 0));
        int udpChecksum = checksum(out, 20, payload.length + 8, pseudoSum(out, payload.length + 8));
        put16(out, 26, udpChecksum == 0 ? 65535 : udpChecksum);
        return out;
    }

    private static int pseudoSum(byte[] data, int udpLength) {
        int sum = 17 + udpLength;
        for (int i = 12; i < 20; i += 2) sum += u16(data, i);
        return sum;
    }

    static int checksum(byte[] data, int start, int length, int sum) {
        int end = start + length;
        for (int i = start; i < end; i += 2) {
            sum += (data[i] & 255) << 8;
            if (i + 1 < end) sum += data[i + 1] & 255;
        }
        while ((sum >>> 16) != 0) sum = (sum & 65535) + (sum >>> 16);
        return (~sum) & 65535;
    }

    private static int u16(byte[] data, int offset) { return ((data[offset] & 255) << 8) | (data[offset + 1] & 255); }
    static void put16(byte[] data, int offset, int value) { data[offset] = (byte) (value >>> 8); data[offset + 1] = (byte) value; }
}
