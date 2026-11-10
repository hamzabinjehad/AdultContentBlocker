package app.hisn.core;

import java.util.Arrays;

/** IPv6/UDP DNS packet adapter. Extension headers and IPv6 TCP are explicitly unsupported. */
public final class Ipv6DnsPacket implements UdpDnsPacket {
    private static final int HEADER = 40;
    private final byte[] request;
    public final byte[] dns;

    private Ipv6DnsPacket(byte[] request) {
        this.request = request;
        this.dns = Arrays.copyOfRange(request, HEADER + 8, request.length);
    }

    @Override public byte[] getDns() { return dns; }

    public static Ipv6DnsPacket parse(byte[] packet, byte[] serverAddress) {
        if (packet == null || serverAddress == null || serverAddress.length != 16
                || packet.length < 48 || packet.length > 1500 || (packet[0] & 0xf0) != 0x60) return null;
        int udpLength = packet.length - HEADER;
        if (u16(packet, 4) != udpLength || packet[6] != 17) return null;
        for (int i = 0; i < 16; i++) if (packet[24 + i] != serverAddress[i]) return null;
        if (u16(packet, HEADER + 2) != 53 || u16(packet, HEADER + 4) != udpLength) return null;
        // IPv6 makes the UDP checksum mandatory; a zero checksum is not "absent".
        if (u16(packet, HEADER + 6) == 0
                || checksum(packet, HEADER, udpLength, pseudoSum(packet, udpLength)) != 0) return null;
        return new Ipv6DnsPacket(packet.clone());
    }

    @Override public byte[] response(byte[] payload) {
        if (payload == null || payload.length < 12 || payload.length > 1452) {
            throw new IllegalArgumentException("Response exceeds tunnel MTU");
        }
        byte[] out = new byte[HEADER + 8 + payload.length];
        out[0] = 0x60;
        put16(out, 4, out.length - HEADER);
        out[6] = 17;
        out[7] = 64;
        System.arraycopy(request, 24, out, 8, 16);   // server -> app
        System.arraycopy(request, 8, out, 24, 16);   // app <- server
        put16(out, HEADER, 53);
        put16(out, HEADER + 2, u16(request, HEADER));
        put16(out, HEADER + 4, payload.length + 8);
        System.arraycopy(payload, 0, out, HEADER + 8, payload.length);
        int sum = checksum(out, HEADER, payload.length + 8, pseudoSum(out, payload.length + 8));
        put16(out, HEADER + 6, sum == 0 ? 65535 : sum);
        return out;
    }

    private static int pseudoSum(byte[] data, int udpLength) {
        int sum = 17 + udpLength;
        for (int i = 8; i < 40; i += 2) sum += u16(data, i);
        return sum;
    }

    private static int checksum(byte[] data, int start, int length, int sum) {
        int end = start + length;
        for (int i = start; i < end; i += 2) {
            sum += (data[i] & 255) << 8;
            if (i + 1 < end) sum += data[i + 1] & 255;
        }
        while ((sum >>> 16) != 0) sum = (sum & 65535) + (sum >>> 16);
        return (~sum) & 65535;
    }

    private static int u16(byte[] data, int offset) { return ((data[offset] & 255) << 8) | (data[offset + 1] & 255); }
    private static void put16(byte[] data, int offset, int value) { data[offset] = (byte) (value >>> 8); data[offset + 1] = (byte) value; }
}
