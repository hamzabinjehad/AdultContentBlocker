package app.hisn.core;

import java.util.Arrays;

/**
 * Bounded IPv4/TCP adapter for the virtual DNS endpoint, not a general TCP/IP stack.
 * Parsing never takes ownership of caller buffers. Fragmentation is unsupported.
 */
public final class Ipv4TcpPacket {
    public static final int FIN = 1;
    public static final int SYN = 2;
    public static final int RST = 4;
    public static final int PSH = 8;
    public static final int ACK = 16;

    private static final int MAX_PACKET = 1500;
    private static final int MAX_RESPONSE_PAYLOAD = 1200;
    private static final int DEFAULT_MSS = 536;

    public final long flowId;
    public final long sequence;
    public final long acknowledgement;
    public final int flags;
    public final int window;
    public final int mss;
    public final int sourcePort;
    public final int destinationPort;
    public final byte[] payload;

    private final byte[] peerAddress;
    private final byte[] serverAddress;

    private Ipv4TcpPacket(byte[] packet, int ipHeader, int tcpHeader, int peerMss) {
        peerAddress = Arrays.copyOfRange(packet, 12, 16);
        serverAddress = Arrays.copyOfRange(packet, 16, 20);
        sourcePort = u16(packet, ipHeader);
        destinationPort = u16(packet, ipHeader + 2);
        flowId = (u32(packet, 12) << 16) | sourcePort;
        sequence = u32(packet, ipHeader + 4);
        acknowledgement = u32(packet, ipHeader + 8);
        flags = packet[ipHeader + 13] & 255;
        window = u16(packet, ipHeader + 14);
        mss = peerMss;
        payload = Arrays.copyOfRange(packet, ipHeader + tcpHeader, packet.length);
    }

    /** Returns null for malformed packets or traffic outside this DNS-only adapter. */
    public static Ipv4TcpPacket parse(byte[] packet, byte[] server) {
        return parse(packet, server, 53);
    }

    /** The same validation for another port of the virtual DNS address, e.g. 853 to refuse it. */
    public static Ipv4TcpPacket parse(byte[] packet, byte[] server, int port) {
        if (packet == null || server == null || server.length != 4 || port < 1 || port > 65535
                || packet.length < 40 || packet.length > MAX_PACKET
                || (packet[0] & 0xf0) != 0x40) return null;
        int ipHeader = (packet[0] & 15) * 4;
        if (ipHeader < 20 || ipHeader + 20 > packet.length
                || u16(packet, 2) != packet.length
                || (u16(packet, 6) & 0xbfff) != 0
                || packet[9] != 6
                || checksum(packet, 0, ipHeader, 0) != 0
                || !validIpOptions(packet, ipHeader)) return null;
        for (int i = 0; i < 4; i++) if (packet[16 + i] != server[i]) return null;
        if (u16(packet, ipHeader) == 0 || u16(packet, ipHeader + 2) != port) return null;

        int tcpHeader = ((packet[ipHeader + 12] & 255) >>> 4) * 4;
        // The low nibble is reserved, including the obsolete NS bit.
        if ((packet[ipHeader + 12] & 15) != 0 || tcpHeader < 20
                || ipHeader + tcpHeader > packet.length
                || checksum(packet, ipHeader, packet.length - ipHeader,
                        pseudoSum(packet, packet.length - ipHeader)) != 0) return null;
        int peerMss = readMss(packet, ipHeader, tcpHeader);
        if (peerMss < 0) return null;
        return new Ipv4TcpPacket(packet, ipHeader, tcpHeader, peerMss);
    }

    /**
     * Builds a fresh reply with reversed endpoints and unsigned, wrapping sequence numbers.
     * SYN replies advertise only MSS 1200; other replies contain no TCP options.
     */
    public byte[] response(long seq, long ack, int responseFlags, int responseWindow, byte[] data) {
        if (data == null || data.length > MAX_RESPONSE_PAYLOAD
                || responseFlags < 0 || responseFlags > 255
                || responseWindow < 0 || responseWindow > 65535) {
            throw new IllegalArgumentException("Invalid bounded TCP response");
        }
        int tcpHeader = (responseFlags & SYN) != 0 ? 24 : 20;
        byte[] out = new byte[20 + tcpHeader + data.length];
        out[0] = 0x45;
        put16(out, 2, out.length);
        out[8] = 64;
        out[9] = 6;
        System.arraycopy(serverAddress, 0, out, 12, 4);
        System.arraycopy(peerAddress, 0, out, 16, 4);
        put16(out, 20, destinationPort);
        put16(out, 22, sourcePort);
        put32(out, 24, seq);
        put32(out, 28, ack);
        out[32] = (byte) ((tcpHeader / 4) << 4);
        out[33] = (byte) responseFlags;
        put16(out, 34, responseWindow);
        if (tcpHeader == 24) {
            out[40] = 2;
            out[41] = 4;
            put16(out, 42, MAX_RESPONSE_PAYLOAD);
        }
        System.arraycopy(data, 0, out, 20 + tcpHeader, data.length);
        put16(out, 10, checksum(out, 0, 20, 0));
        put16(out, 36, checksum(out, 20, out.length - 20,
                pseudoSum(out, out.length - 20)));
        return out;
    }

    private static int readMss(byte[] packet, int ipHeader, int tcpHeader) {
        int result = DEFAULT_MSS;
        boolean seen = false;
        int end = ipHeader + tcpHeader;
        for (int i = ipHeader + 20; i < end;) {
            int kind = packet[i] & 255;
            if (kind == 0) break;
            if (kind == 1) { i++; continue; }
            if (i + 1 >= end) return -1;
            int length = packet[i + 1] & 255;
            if (length < 2 || length > end - i) return -1;
            if (kind == 2) {
                if (length != 4 || seen || (packet[ipHeader + 13] & SYN) == 0) return -1;
                result = u16(packet, i + 2);
                if (result == 0) return -1;
                seen = true;
            }
            i += length;
        }
        return result;
    }

    private static boolean validIpOptions(byte[] packet, int ipHeader) {
        for (int i = 20; i < ipHeader;) {
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

    private static int pseudoSum(byte[] packet, int length) {
        int sum = 6 + length;
        for (int i = 12; i < 20; i += 2) sum += u16(packet, i);
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

    private static int u16(byte[] data, int offset) {
        return ((data[offset] & 255) << 8) | (data[offset + 1] & 255);
    }

    private static long u32(byte[] data, int offset) {
        return ((long) u16(data, offset) << 16) | u16(data, offset + 2);
    }

    private static void put16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 8);
        data[offset + 1] = (byte) value;
    }

    private static void put32(byte[] data, int offset, long value) {
        data[offset] = (byte) (value >>> 24);
        data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8);
        data[offset + 3] = (byte) value;
    }
}
