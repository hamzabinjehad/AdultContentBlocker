package app.hisn.core;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;

/** Explicit optional network diagnostic; never part of the offline test suite. */
public final class ResolverProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].equals("--public-example-only")) {
            throw new IllegalArgumentException("Explicit public test flag required");
        }
        try (TlsDnsTransport transport = new TlsDnsTransport(new InetSocketAddress(
                InetAddress.getByAddress(new byte[]{1, 1, 1, 3}), 853), "family.cloudflare-dns.com", 2000, socket -> true)) {
            DnsExchange exchange = new DnsExchange(new DnsPolicy(new DomainPolicy(Collections.emptySet(), Collections.emptySet())), transport);
            for (int i = 0; i < 2; i++) {
                byte[] answer = exchange.resolve(DnsPolicyTest.query("example.com", 1));
                if ((answer[3] & 15) != 0) throw new java.io.IOException("Resolver did not answer public test successfully");
            }
            System.out.println("Public resolver probe passed: two authenticated TLS DNS requests for example.com");
        }
    }
}
