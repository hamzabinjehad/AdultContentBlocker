package app.hisn.core;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

public final class EncryptedDnsResolversTest {
    public static void main(String[] args) throws Exception {
        Set<String> seen = new HashSet<>();
        for (String address : EncryptedDnsResolvers.ipv4()) {
            if (!seen.add(address)) throw new AssertionError("Duplicate IPv4 " + address);
            if (!EncryptedDnsResolvers.blocks(packet(address, 4))) {
                throw new AssertionError("IPv4 resolver not blocked: " + address);
            }
            if (!(InetAddress.getByName(address) instanceof java.net.Inet4Address)) {
                throw new AssertionError("Not an IPv4 literal: " + address);
            }
        }
        for (String address : EncryptedDnsResolvers.ipv6()) {
            if (!seen.add(address)) throw new AssertionError("Duplicate address " + address);
            if (!EncryptedDnsResolvers.blocks(packet(address, 6))) {
                throw new AssertionError("IPv6 resolver not blocked: " + address);
            }
            if (!(InetAddress.getByName(address) instanceof java.net.Inet6Address)) {
                throw new AssertionError("Not an IPv6 literal: " + address);
            }
        }
        // Hisn's own virtual server and its filtered upstream must never be dropped.
        if (EncryptedDnsResolvers.blocks(packet("10.111.0.2", 4))) {
            throw new AssertionError("Virtual DNS server blocked");
        }
        if (EncryptedDnsResolvers.blocks(packet("1.1.1.3", 4))) {
            throw new AssertionError("Filtered upstream blocked");
        }
        if (EncryptedDnsResolvers.blocks(packet("1.0.0.3", 4))) {
            throw new AssertionError("Filtered backup upstream blocked");
        }
        if (EncryptedDnsResolvers.blocks(new byte[12])) {
            throw new AssertionError("Short packet accepted");
        }
        System.out.println("EncryptedDnsResolvers tests passed ("
                + EncryptedDnsResolvers.ipv4().length + " IPv4 and "
                + EncryptedDnsResolvers.ipv6().length + " IPv6 dedicated resolver addresses)");
    }

    private static byte[] packet(String address, int version) throws Exception {
        byte[] raw = new byte[version == 4 ? 20 : 40];
        byte[] bytes = InetAddress.getByName(address).getAddress();
        if (version == 4) {
            raw[0] = 0x45;
            System.arraycopy(bytes, 0, raw, 16, 4);
        } else {
            raw[0] = 0x60;
            System.arraycopy(bytes, 0, raw, 24, 16);
        }
        return raw;
    }
}
