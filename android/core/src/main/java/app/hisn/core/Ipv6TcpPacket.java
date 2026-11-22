package app.hisn.core;

/** IPv6 packets for the virtual TCP DNS endpoint. Extension headers are unsupported. */
public final class Ipv6TcpPacket extends TcpDnsPacket {
    private static final int HEADER = 40;
    private static final int ADDRESS_OFFSET = 8;
    private static final int ADDRESS_LENGTH = 16;
    private static final int DEFAULT_MSS = 1220;

    private Ipv6TcpPacket(byte[] packet, int length, int tcpHeader, int peerMss) {
        super(packet, length, HEADER, tcpHeader, peerMss, ADDRESS_OFFSET, ADDRESS_LENGTH);
    }

    public static Ipv6TcpPacket parse(byte[] packet, byte[] server) {
        return parse(packet, server, 53);
    }

    public static Ipv6TcpPacket parse(byte[] packet, byte[] server, int port) {
        return packet == null ? null : parse(packet, packet.length, server, port);
    }

    static Ipv6TcpPacket parse(byte[] packet, int length, byte[] server, int port) {
        if (packet == null || server == null || server.length != ADDRESS_LENGTH || port < 1 || port > 65535
                || length < HEADER + TCP_HEADER || length > MAX_PACKET || length > packet.length
                || (packet[0] & 0xf0) != 0x60
                || u16(packet, 4) != length - HEADER || packet[6] != 6) return null;
        int tcpHeader = validatedHeader(packet, length, server, port, HEADER, ADDRESS_OFFSET);
        if (tcpHeader < 0) return null;
        int peerMss = readMss(packet, HEADER, tcpHeader, DEFAULT_MSS);
        return peerMss < 0 ? null : new Ipv6TcpPacket(packet, length, tcpHeader, peerMss);
    }

    @Override int responseHeaderLength() { return HEADER; }
    @Override int addressOffset() { return ADDRESS_OFFSET; }
    @Override int addressLength() { return ADDRESS_LENGTH; }

    @Override void writeResponseHeader(byte[] out) {
        out[0] = 0x60;
        put16(out, 4, out.length - HEADER);
        out[6] = 6;
        out[7] = 64;
        endpoint.reverseInto(out, ADDRESS_OFFSET);
    }
}
