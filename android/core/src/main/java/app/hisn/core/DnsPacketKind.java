package app.hisn.core;

/** Bounded, privacy-safe packet diagnostics. A diagnostic never makes a packet filterable.
 * No addresses, ports, payloads or packet objects are retained by this classifier. */
public enum DnsPacketKind {
    UDP_DNS,
    /** TCP to port 53 that the bounded TCP adapter rejected: a DNS request was lost. */
    TCP_DNS_UNSUPPORTED,
    /** TCP to the DNS-over-TLS port 853, which Hisn refuses so Android falls back to port 53. */
    TCP_TLS_PORT,
    /** An IPv4 fragment is not a complete message; a fragmented DNS query cannot be served. */
    FRAGMENT,
    OTHER_UNSUPPORTED,
    MALFORMED;

    /** Unrelated traffic (another protocol or port, IPv6, port 853) does not mean a DNS request failed. */
    public boolean benign() { return this == TCP_TLS_PORT || this == OTHER_UNSUPPORTED; }

    /** UDP_DNS is returned only for packets accepted by the existing tunnel adapter. */
    public static DnsPacketKind classify(byte[] packet, byte[] server) {
        if (packet == null || server == null || server.length != 4
                || packet.length < 20 || packet.length > 1500) return MALFORMED;
        int version = (packet[0] & 255) >>> 4;
        if (version == 6) {
            // IPv6 has no header checksum. Even a valid IPv6 header is unsupported here.
            return packet.length >= 40 && u16(packet, 4) == packet.length - 40
                    ? OTHER_UNSUPPORTED : MALFORMED;
        }
        if (version != 4) return MALFORMED;
        int header = (packet[0] & 15) * 4;
        if (header < 20 || header > packet.length || u16(packet, 2) != packet.length
                || Ipv4DnsPacket.checksum(packet, 0, header, 0) != 0
                || (u16(packet, 6) & 0x8000) != 0) return MALFORMED;
        // A fragment is not a complete UDP/TCP message. Do not infer a transport from it.
        if ((u16(packet, 6) & 0x3fff) != 0) return FRAGMENT;
        int protocol = packet[9] & 255;
        int payload = packet.length - header;
        if (protocol == 17) {
            if (payload < 8 || u16(packet, header + 4) != payload
                    || (u16(packet, header + 6) != 0
                    && Ipv4DnsPacket.checksum(packet, header, payload,
                    pseudoSum(packet, protocol, payload)) != 0)) return MALFORMED;
            if (!destinationMatches(packet, server) || u16(packet, header + 2) != 53)
                return OTHER_UNSUPPORTED;
            return Ipv4DnsPacket.parse(packet, server) == null ? MALFORMED : UDP_DNS;
        }
        if (protocol == 6) {
            if (payload < 20) return MALFORMED;
            int tcpHeader = ((packet[header + 12] & 255) >>> 4) * 4;
            if (tcpHeader < 20 || tcpHeader > payload) return MALFORMED;
            if (!destinationMatches(packet, server)) return OTHER_UNSUPPORTED;
            int port = u16(packet, header + 2);
            return port == 53 ? TCP_DNS_UNSUPPORTED : port == 853 ? TCP_TLS_PORT : OTHER_UNSUPPORTED;
        }
        return OTHER_UNSUPPORTED;
    }

    private static boolean destinationMatches(byte[] packet, byte[] server) {
        for (int i = 0; i < 4; i++) if (packet[16 + i] != server[i]) return false;
        return true;
    }

    private static int pseudoSum(byte[] packet, int protocol, int length) {
        int sum = protocol + length;
        for (int i = 12; i < 20; i += 2) sum += u16(packet, i);
        return sum;
    }

    private static int u16(byte[] bytes, int offset) {
        return ((bytes[offset] & 255) << 8) | (bytes[offset + 1] & 255);
    }
}
