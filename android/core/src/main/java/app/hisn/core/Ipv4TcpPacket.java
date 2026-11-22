package app.hisn.core;

/** IPv4 packets for the virtual TCP DNS endpoint. Fragmentation is unsupported. */
public final class Ipv4TcpPacket extends TcpDnsPacket {
    private static final int HEADER = 20;
    private static final int ADDRESS_OFFSET = 12;
    private static final int ADDRESS_LENGTH = 4;
    private static final int DEFAULT_MSS = 536;

    public final long flowId;

    private Ipv4TcpPacket(byte[] packet, int length, int ipHeader, int tcpHeader, int peerMss) {
        super(packet, length, ipHeader, tcpHeader, peerMss, ADDRESS_OFFSET, ADDRESS_LENGTH);
        flowId = (u32(packet, ADDRESS_OFFSET) << 16) | sourcePort;
    }

    public static Ipv4TcpPacket parse(byte[] packet, byte[] server) {
        return parse(packet, server, 53);
    }

    public static Ipv4TcpPacket parse(byte[] packet, byte[] server, int port) {
        return packet == null ? null : parse(packet, packet.length, server, port);
    }

    static Ipv4TcpPacket parse(byte[] packet, int length, byte[] server, int port) {
        if (packet == null || server == null || server.length != ADDRESS_LENGTH || port < 1 || port > 65535
                || length < HEADER + TCP_HEADER || length > MAX_PACKET || length > packet.length
                || (packet[0] & 0xf0) != 0x40) return null;
        int ipHeader = (packet[0] & 15) * 4;
        if (ipHeader < HEADER || ipHeader + TCP_HEADER > length
                || u16(packet, 2) != length
                || (u16(packet, 6) & 0xbfff) != 0
                || packet[9] != 6
                || checksum(packet, 0, ipHeader, 0) != 0
                || !validIpOptions(packet, ipHeader)) return null;

        int tcpHeader = validatedHeader(packet, length, server, port, ipHeader, ADDRESS_OFFSET);
        if (tcpHeader < 0) return null;
        int peerMss = readMss(packet, ipHeader, tcpHeader, DEFAULT_MSS);
        return peerMss < 0 ? null : new Ipv4TcpPacket(packet, length, ipHeader, tcpHeader, peerMss);
    }

    @Override int responseHeaderLength() { return HEADER; }
    @Override int addressOffset() { return ADDRESS_OFFSET; }
    @Override int addressLength() { return ADDRESS_LENGTH; }

    @Override void writeResponseHeader(byte[] out) {
        out[0] = 0x45;
        put16(out, 2, out.length);
        out[8] = 64;
        out[9] = 6;
        endpoint.reverseInto(out, ADDRESS_OFFSET);
        put16(out, 10, checksum(out, 0, HEADER, 0));
    }

    private static boolean validIpOptions(byte[] packet, int ipHeader) {
        for (int i = HEADER; i < ipHeader;) {
            int kind = packet[i] & 255;
            if (kind == 0) return true;
            if (kind == 1) { i++; continue; }
            if (i + 1 >= ipHeader) return false;
            int length = packet[i + 1] & 255;
            if (length < 2 || length > ipHeader - i) return false;
            i += length;
        }
        return true;
    }
}
