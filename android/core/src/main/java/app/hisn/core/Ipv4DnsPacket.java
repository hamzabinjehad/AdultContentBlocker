package app.hisn.core;

import java.util.Arrays;

/** IPv4/UDP DNS packet adapter. Fragmented packets and IPv6 are explicitly unsupported. */
public final class Ipv4DnsPacket {
    private final byte[] request;
    private final int headerSize;
    public final byte[] dns;

    private Ipv4DnsPacket(byte[] request, int headerSize) {
        this.request = request;
        this.headerSize = headerSize;
        this.dns = Arrays.copyOfRange(request, headerSize + 8, request.length);
    }

    public static Ipv4DnsPacket parse(byte[] packet, byte[] serverAddress) {
        if (packet == null || serverAddress == null || serverAddress.length != 4
                || packet.length < 40 || packet.length > 1500 || (packet[0] & 0xf0) != 0x40) return null;
        int header = (packet[0] & 15) * 4;
        if (header < 20 || header + 20 > packet.length || u16(packet, 2) != packet.length
                || (u16(packet, 6) & 0xbfff) != 0 || packet[9] != 17
                || checksum(packet, 0, header, 0) != 0) return null;
        for (int i = 0; i < 4; i++) if (packet[16 + i] != serverAddress[i]) return null;
        if (u16(packet, header + 2) != 53 || u16(packet, header + 4) != packet.length - header) return null;
        if (u16(packet, header + 6) != 0 && checksum(packet, header, packet.length - header,
                pseudoSum(packet, packet.length - header)) != 0) return null;
        return new Ipv4DnsPacket(packet.clone(), header);
    }

    public byte[] response(byte[] payload) {
        if (payload == null || payload.length < 12 || payload.length > 1472) {
            throw new IllegalArgumentException("Response exceeds tunnel MTU");
        }
        byte[] out = new byte[28 + payload.length];
        out[0] = 0x45;
        put16(out, 2, out.length);
        out[8] = 64; out[9] = 17;
        System.arraycopy(request, 16, out, 12, 4);
        System.arraycopy(request, 12, out, 16, 4);
        put16(out, 20, 53);
        put16(out, 22, u16(request, headerSize));
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
