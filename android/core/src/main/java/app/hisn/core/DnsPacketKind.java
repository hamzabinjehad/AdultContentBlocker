package app.hisn.core;

/** Bounded, privacy-safe packet diagnostics. A diagnostic never makes a packet filterable.
 * No addresses, ports, payloads or packet objects are retained by this classifier. */
public enum DnsPacketKind {
    UDP_DNS,
    /** TCP to port 53 that the bounded TCP adapter rejected: a DNS request was lost. */
    TCP_DNS_UNSUPPORTED,
    /** TCP to the DNS-over-TLS port 853, which Hisn refuses so Android falls back to port 53. */
    TCP_TLS_PORT,
    /** Fragmented messages cannot be served by the virtual DNS endpoints. */
    FRAGMENT,
    OTHER_UNSUPPORTED,
    MALFORMED;

    /** Unrelated traffic and Private DNS probes do not mean a DNS request failed. */
    public boolean benign() { return this == TCP_TLS_PORT || this == OTHER_UNSUPPORTED; }

    /** UDP_DNS is returned only for packets accepted by the existing tunnel adapter. */
    public static DnsPacketKind classify(byte[] packet, byte[] server) {
        return classify(packet, server, null);
    }

    public static DnsPacketKind classify(byte[] packet, byte[] server, byte[] server6) {
        return packet == null ? MALFORMED : classify(packet, packet.length, server, server6);
    }

    public static DnsPacketKind classify(byte[] packet, int length, byte[] server, byte[] server6) {
        if (packet == null || server == null || server.length != 4
                || length < 20 || length > 1500 || length > packet.length) return MALFORMED;
        int version = (packet[0] & 255) >>> 4;
        if (version == 6) return classifyIpv6(packet, length, server6);
        if (version != 4) return MALFORMED;
        int header = (packet[0] & 15) * 4;
        if (header < 20 || header > length || u16(packet, 2) != length
                || Ipv4DnsPacket.checksum(packet, 0, header, 0) != 0
                || (u16(packet, 6) & 0x8000) != 0) return MALFORMED;
        // A fragment is not a complete UDP/TCP message. Do not infer a transport from it.
        if ((u16(packet, 6) & 0x3fff) != 0) return FRAGMENT;
        int protocol = packet[9] & 255;
        int payload = length - header;
        if (protocol == 17) {
            if (payload < 8 || u16(packet, header + 4) != payload
                    || (u16(packet, header + 6) != 0
                    && Ipv4DnsPacket.checksum(packet, header, payload,
                    pseudoSum(packet, protocol, payload)) != 0)) return MALFORMED;
            if (!destinationMatches(packet, server) || u16(packet, header + 2) != 53)
                return OTHER_UNSUPPORTED;
            return Ipv4DnsPacket.parse(packet, length, server) == null ? MALFORMED : UDP_DNS;
        }
        if (protocol == 6) {
            if (payload < 20) return MALFORMED;
            int tcpHeader = ((packet[header + 12] & 255) >>> 4) * 4;
            if (tcpHeader < 20 || tcpHeader > payload) return MALFORMED;
            if (!destinationMatches(packet, server)) return OTHER_UNSUPPORTED;
            return tcpPort(u16(packet, header + 2));
        }
        return OTHER_UNSUPPORTED;
    }

    private static DnsPacketKind classifyIpv6(byte[] packet, int length, byte[] server) {
        int header = 40;
        if (length < header || u16(packet, 4) != length - header) return MALFORMED;
        if (server == null || server.length != 16) return OTHER_UNSUPPORTED;
        for (int i = 0; i < 16; i++) if (packet[24 + i] != server[i]) return OTHER_UNSUPPORTED;
        if (packet[6] == 44) return length >= header + 8 ? FRAGMENT : MALFORMED;
        if (packet[6] == 17) {
            if (length < header + 8) return MALFORMED;
            if (u16(packet, header + 2) != 53) return OTHER_UNSUPPORTED;
            return Ipv6DnsPacket.parse(packet, length, server) == null ? MALFORMED : UDP_DNS;
        }
        if (packet[6] == 6) {
            if (length < header + 20) return MALFORMED;
            int tcpHeader = ((packet[header + 12] & 255) >>> 4) * 4;
            if (tcpHeader < 20 || tcpHeader > length - header) return MALFORMED;
            return tcpPort(u16(packet, header + 2));
        }
        return OTHER_UNSUPPORTED;
    }

    private static DnsPacketKind tcpPort(int port) {
        return port == 53 ? TCP_DNS_UNSUPPORTED : port == 853 ? TCP_TLS_PORT : OTHER_UNSUPPORTED;
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
