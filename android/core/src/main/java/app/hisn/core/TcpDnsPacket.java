package app.hisn.core;

import java.util.Arrays;

/** The common TCP segment format for Hisn's bounded virtual DNS endpoints. */
public abstract class TcpDnsPacket {
    public static final int FIN = 1;
    public static final int SYN = 2;
    public static final int RST = 4;
    public static final int PSH = 8;
    public static final int ACK = 16;
    static final int MAX_PACKET = 1500;
    static final int MAX_RESPONSE_PAYLOAD = 1200;
    static final int TCP_HEADER = 20;

    public final long sequence;
    public final long acknowledgement;
    public final int flags;
    public final int window;
    public final int mss;
    public final int sourcePort;
    public final int destinationPort;
    public final byte[] payload;

    final Endpoint endpoint;

    TcpDnsPacket(byte[] packet, int length, int ipHeader, int tcpHeader, int peerMss,
            int addressOffset, int addressLength) {
        sourcePort = u16(packet, ipHeader);
        destinationPort = u16(packet, ipHeader + 2);
        endpoint = new Endpoint(packet, addressOffset, addressLength, sourcePort, destinationPort);
        sequence = u32(packet, ipHeader + 4);
        acknowledgement = u32(packet, ipHeader + 8);
        flags = packet[ipHeader + 13] & 255;
        window = u16(packet, ipHeader + 14);
        mss = peerMss;
        payload = Arrays.copyOfRange(packet, ipHeader + tcpHeader, length);
    }

    /** Keeps connections distinct across both address families and local DNS endpoints. */
    static final class Endpoint {
        private final byte[] addresses;
        private final int ports;
        private final int hash;

        Endpoint(byte[] packet, int offset, int length, int sourcePort, int destinationPort) {
            addresses = Arrays.copyOfRange(packet, offset, offset + length * 2);
            ports = (sourcePort << 16) | destinationPort;
            hash = 31 * Arrays.hashCode(addresses) + ports;
        }

        void reverseInto(byte[] out, int offset) {
            int length = addresses.length / 2;
            System.arraycopy(addresses, length, out, offset, length);
            System.arraycopy(addresses, 0, out, offset + length, length);
        }

        @Override public int hashCode() { return hash; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Endpoint)) return false;
            Endpoint endpoint = (Endpoint) other;
            return ports == endpoint.ports && Arrays.equals(addresses, endpoint.addresses);
        }
    }

    /** Returns null for malformed packets or traffic to another endpoint. */
    public static TcpDnsPacket parse(byte[] packet, byte[] server4, byte[] server6, int port) {
        return packet == null ? null : parse(packet, packet.length, server4, server6, port);
    }

    /** Parses a single segment from a reusable receive buffer. */
    public static TcpDnsPacket parse(byte[] packet, int length, byte[] server4, byte[] server6, int port) {
        if (packet == null || length <= 0 || length > packet.length) return null;
        return (packet[0] & 0xf0) == 0x60 ? Ipv6TcpPacket.parse(packet, length, server6, port)
                : Ipv4TcpPacket.parse(packet, length, server4, port);
    }

    /** Builds a fresh reply with reversed endpoints and unsigned, wrapping sequence numbers. */
    public final byte[] response(long seq, long ack, int responseFlags, int responseWindow, byte[] data) {
        if (data == null || data.length > MAX_RESPONSE_PAYLOAD
                || responseFlags < 0 || responseFlags > 255
                || responseWindow < 0 || responseWindow > 65535) {
            throw new IllegalArgumentException("Invalid bounded TCP response");
        }
        int tcpHeader = (responseFlags & SYN) != 0 ? 24 : TCP_HEADER;
        int offset = responseHeaderLength();
        byte[] out = new byte[offset + tcpHeader + data.length];
        writeResponseHeader(out);
        put16(out, offset, destinationPort);
        put16(out, offset + 2, sourcePort);
        put32(out, offset + 4, seq);
        put32(out, offset + 8, ack);
        out[offset + 12] = (byte) ((tcpHeader / 4) << 4);
        out[offset + 13] = (byte) responseFlags;
        put16(out, offset + 14, responseWindow);
        if (tcpHeader == 24) {
            out[offset + 20] = 2;
            out[offset + 21] = 4;
            put16(out, offset + 22, MAX_RESPONSE_PAYLOAD);
        }
        System.arraycopy(data, 0, out, offset + tcpHeader, data.length);
        int segment = out.length - offset;
        put16(out, offset + 16, checksum(out, offset, segment, pseudoSum(out, segment, addressOffset(), addressLength())));
        return out;
    }

    abstract int responseHeaderLength();
    abstract int addressOffset();
    abstract int addressLength();
    abstract void writeResponseHeader(byte[] out);

    /** Caller has already checked that a complete minimum TCP header is present. */
    static int validatedHeader(byte[] packet, int length, byte[] server, int port, int offset, int addressOffset) {
        int destination = addressOffset + server.length;
        for (int i = 0; i < server.length; i++) if (packet[destination + i] != server[i]) return -1;
        if (u16(packet, offset) == 0 || u16(packet, offset + 2) != port) return -1;
        int segment = length - offset;
        int tcpHeader = ((packet[offset + 12] & 255) >>> 4) * 4;
        if ((packet[offset + 12] & 15) != 0 || tcpHeader < TCP_HEADER || tcpHeader > segment
                || checksum(packet, offset, segment, pseudoSum(packet, segment, addressOffset, server.length)) != 0) return -1;
        return tcpHeader;
    }

    static int readMss(byte[] packet, int offset, int tcpHeader, int defaultMss) {
        int result = defaultMss;
        boolean seen = false;
        int end = offset + tcpHeader;
        for (int i = offset + TCP_HEADER; i < end;) {
            int kind = packet[i] & 255;
            if (kind == 0) break;
            if (kind == 1) { i++; continue; }
            if (i + 1 >= end) return -1;
            int length = packet[i + 1] & 255;
            if (length < 2 || length > end - i) return -1;
            if (kind == 2) {
                if (length != 4 || seen || (packet[offset + 13] & SYN) == 0) return -1;
                result = u16(packet, i + 2);
                if (result == 0) return -1;
                seen = true;
            }
            i += length;
        }
        return result;
    }

    private static int pseudoSum(byte[] packet, int length, int offset, int addressLength) {
        // The length is bounded by the MTU, so the high IPv6 length word is zero.
        int sum = 6 + length;
        for (int i = offset; i < offset + addressLength * 2; i += 2) sum += u16(packet, i);
        return sum;
    }

    static int checksum(byte[] packet, int offset, int length, int sum) {
        return Ipv4DnsPacket.checksum(packet, offset, length, sum);
    }

    static int u16(byte[] data, int offset) {
        return ((data[offset] & 255) << 8) | (data[offset + 1] & 255);
    }

    static long u32(byte[] data, int offset) {
        return ((long) u16(data, offset) << 16) | u16(data, offset + 2);
    }

    static void put16(byte[] data, int offset, int value) {
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
