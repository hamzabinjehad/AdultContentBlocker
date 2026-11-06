package app.hisn.core;

import java.util.Arrays;

/**
 * Fixed public resolver addresses used by encrypted-DNS clients (DoH/DoT).
 *
 * <p>Hisn's tunnel already answers the well-known provider names and refuses
 * TLS on port 853 to its own virtual server. An app that hard-codes one of
 * these addresses never asks Hisn's DNS server, so that traffic is routed into
 * the tunnel and dropped instead. The list contains dedicated resolver
 * addresses only: shared CDN addresses are deliberately absent because
 * blocking them would break ordinary websites.
 */
public final class EncryptedDnsResolvers {
    private static final String[] IPV4 = {
            "1.1.1.1", "1.0.0.1",                 // Cloudflare
            "8.8.8.8", "8.8.4.4",                 // Google
            "9.9.9.9", "149.112.112.112",         // Quad9
            "208.67.222.222", "208.67.220.220",   // OpenDNS
            "94.140.14.14", "94.140.15.15",       // AdGuard
            "76.76.2.0", "76.76.10.0",            // Control D
            "185.228.168.9", "185.228.169.9",     // CleanBrowsing Family
            "64.6.64.6", "64.6.65.6",             // Verisign
            "156.154.70.1", "156.154.71.1"        // Neustar
    };
    private static final String[] IPV6 = {
            "2606:4700:4700::1111", "2606:4700:4700::1001", // Cloudflare
            "2001:4860:4860::8888", "2001:4860:4860::8844", // Google
            "2620:fe::fe", "2620:fe::9",                    // Quad9
            "2620:119:35::35", "2620:119:53::53",           // OpenDNS
            "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff",       // AdGuard
            "2606:1a40::2", "2606:1a40:1::2"                // Control D
    };

    private static final byte[][] IPV4_BYTES = parseAll(IPV4, 4);
    private static final byte[][] IPV6_BYTES = parseAll(IPV6, 16);

    private EncryptedDnsResolvers() { }

    /** The exact IPv4 addresses to route into the tunnel and drop. */
    public static String[] ipv4() { return IPV4.clone(); }

    /** The exact IPv6 addresses to route into the tunnel and drop. */
    public static String[] ipv6() { return IPV6.clone(); }

    /**
     * True when this raw IP packet's destination is one of the fixed resolver
     * addresses. The caller drops it without forwarding.
     */
    public static boolean blocks(byte[] packet) {
        if (packet == null || packet.length < 20) return false;
        int version = (packet[0] & 0xf0) >>> 4;
        if (version == 4) {
            for (byte[] address : IPV4_BYTES) if (matches(packet, 16, address)) return true;
        } else if (version == 6 && packet.length >= 40) {
            for (byte[] address : IPV6_BYTES) if (matches(packet, 24, address)) return true;
        }
        return false;
    }

    private static boolean matches(byte[] packet, int offset, byte[] address) {
        if (offset + address.length > packet.length) return false;
        for (int i = 0; i < address.length; i++) {
            if (packet[offset + i] != address[i]) return false;
        }
        return true;
    }

    private static byte[][] parseAll(String[] addresses, int length) {
        byte[][] parsed = new byte[addresses.length][];
        for (int i = 0; i < addresses.length; i++) {
            parsed[i] = length == 4 ? parse4(addresses[i]) : parse6(addresses[i]);
            if (parsed[i].length != length) throw new IllegalArgumentException("Invalid resolver address");
            for (int j = 0; j < i; j++) {
                if (Arrays.equals(parsed[i], parsed[j])) {
                    throw new IllegalArgumentException("Duplicate resolver address");
                }
            }
        }
        return parsed;
    }

    private static byte[] parse4(String text) {
        String[] parts = text.split("\\.", -1);
        if (parts.length != 4) throw new IllegalArgumentException("Invalid IPv4 resolver");
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 3) throw new IllegalArgumentException();
            int value = 0;
            for (int j = 0; j < parts[i].length(); j++) {
                char c = parts[i].charAt(j);
                if (c < '0' || c > '9') throw new IllegalArgumentException();
                value = value * 10 + (c - '0');
            }
            if (value > 255) throw new IllegalArgumentException();
            out[i] = (byte) value;
        }
        return out;
    }

    private static byte[] parse6(String text) {
        String[] halves = text.split("::", -1);
        if (halves.length > 2) throw new IllegalArgumentException();
        String[] left = halves[0].isEmpty() ? new String[0] : halves[0].split(":", -1);
        String[] right = halves.length == 2 && !halves[1].isEmpty()
                ? halves[1].split(":", -1) : new String[0];
        int missing = 8 - left.length - right.length;
        if (halves.length == 1 ? missing != 0 : missing < 1) throw new IllegalArgumentException();
        byte[] out = new byte[16];
        int at = 0;
        for (String group : left) at = putGroup(out, at, group);
        at += missing * 2;
        for (String group : right) at = putGroup(out, at, group);
        if (at != 16) throw new IllegalArgumentException();
        return out;
    }

    private static int putGroup(byte[] out, int at, String group) {
        if (group.isEmpty() || group.length() > 4) throw new IllegalArgumentException();
        int value = 0;
        for (int i = 0; i < group.length(); i++) {
            int digit = Character.digit(group.charAt(i), 16);
            if (digit < 0) throw new IllegalArgumentException();
            value = (value << 4) | digit;
        }
        out[at] = (byte) (value >>> 8);
        out[at + 1] = (byte) value;
        return at + 2;
    }
}
