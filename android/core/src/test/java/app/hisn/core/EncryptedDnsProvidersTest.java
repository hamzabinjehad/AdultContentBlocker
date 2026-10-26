package app.hisn.core;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;

public final class EncryptedDnsProvidersTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        for (String name : Arrays.asList("use-application-dns.net", "USE-APPLICATION-DNS.NET.",
                "dns.google", "dns.google.com", "dns64.dns.google", "8888.google",
                "cloudflare-dns.com", "mozilla.cloudflare-dns.com", "chrome.cloudflare-dns.com",
                "family.cloudflare-dns.com", "1dot1dot1dot1.cloudflare-dns.com", "one.one.one.one",
                "dns.quad9.net", "dns11.quad9.net", "doh.opendns.com", "doh.familyshield.opendns.com",
                "doh.cleanbrowsing.org", "family.adguard-dns.com", "dns-family.adguard.com",
                "dns.nextdns.io", "abc123.dns.nextdns.io", "firefox.dns.nextdns.io",
                "dns.adguard-dns.com", "unfiltered.adguard-dns.com", "abc.d.adguard-dns.com", "dns.adguard.com",
                "doh.mullvad.net", "base.dns.mullvad.net", "dns.controld.com", "freedns.controld.com",
                "doh.xfinity.com", "private.canadianshield.cira.ca", "family.canadianshield.cira.ca",
                "odvr.nic.cz", "doh.dns.sb", "dns.alidns.com", "doh.pub", "sm2.doh.pub", "dot.pub",
                "resolver.arpa")) {
            check(EncryptedDnsProviders.blocks(name, null), "provider not blocked: " + name);
        }
        // Parent sites, look-alike labels and ordinary names stay governed by the normal rules.
        for (String name : Arrays.asList("google.com", "www.google.com", "google", "cloudflare.com",
                "notcloudflare-dns.com", "cloudflare-dns.com.example", "dns.google.example", "quad9.net",
                "www.quad9.net", "opendns.com", "nextdns.io", "my.nextdns.io", "adguard.com", "adguard-dns.io",
                "mullvad.net", "controld.com", "cleanbrowsing.org", "familyshield.opendns.com",
                "example.com", "application-dns.net", "arpa",
                "xfinity.com", "cira.ca", "canadianshield.cira.ca", "nic.cz", "dns.sb", "alidns.com",
                "pub", "example.pub", "4.3.2.1.in-addr.arpa", "", "1.1.1.1", "_dns.resolver.arpa")) {
            check(!EncryptedDnsProviders.blocks(name, null), "unexpected block: " + name);
        }
        check(!EncryptedDnsProviders.blocks(null, null), "null name");
        check(EncryptedDnsProviders.size() >= 35, "curated list unexpectedly short");

        // Android can resolve its strict Private DNS hostname through the VPN; only that exact name is spared.
        check(!EncryptedDnsProviders.blocks("dns.google", "dns.google"), "strict Private DNS name blocked");
        check(!EncryptedDnsProviders.blocks("DNS.Google.", "dns.google"), "exemption is case/root-dot insensitive");
        check(!EncryptedDnsProviders.blocks("abc.dns.nextdns.io", "ABC.dns.nextdns.io."), "exact exemption");
        check(EncryptedDnsProviders.blocks("dns64.dns.google", "dns.google"), "exemption must not cover subdomains");
        check(EncryptedDnsProviders.blocks("dns.quad9.net", "dns.google"), "other providers stay blocked");
        check(EncryptedDnsProviders.blocks(EncryptedDnsProviders.FIREFOX_CANARY, "dns.google"), "canary still blocked");

        // The Firefox canary and DDR discovery receive NXDOMAIN through the normal DNS message path.
        DomainPolicy seed = new DomainPolicy(Collections.singleton("blocked.example"), Collections.emptySet());
        DnsPolicy policy = new DnsPolicy(name -> EncryptedDnsProviders.blocks(name, null)
                ? DomainPolicy.Decision.BLOCK : seed.evaluate(name));
        for (int type : new int[] {1, 28, 65}) {
            DnsPolicy.Result canary = policy.evaluate(DnsPolicyTest.query("use-application-dns.net", type));
            check(canary.action == DnsPolicy.Action.BLOCK && (canary.response[3] & 15) == 3
                    && (canary.response[2] & 0x80) != 0, "Firefox canary is NXDOMAIN, type " + type);
        }
        check(policy.evaluate(DnsPolicyTest.query("_dns.resolver.arpa", 64)).action == DnsPolicy.Action.BLOCK,
                "DDR SVCB discovery answered locally");
        check(policy.evaluate(DnsPolicyTest.query("mozilla.cloudflare-dns.com", 1)).action == DnsPolicy.Action.BLOCK,
                "Firefox Cloudflare DoH name blocked");
        check(policy.evaluate(DnsPolicyTest.query("example.com", 1)).action == DnsPolicy.Action.FORWARD,
                "ordinary name forwarded");
        DnsExchange exchange = new DnsExchange(policy, query -> { throw new AssertionError("Provider name leaked upstream"); });
        check((exchange.resolve(DnsPolicyTest.query("dns.google", 1))[3] & 15) == 3, "local NXDOMAIN, no upstream");

        // Hisn's own resolver name is on the list; this is safe only because the transport is
        // pinned to a resolved IP and never looks its hostname up through the tunnel.
        check(EncryptedDnsProviders.blocks("family.cloudflare-dns.com", null), "upstream name is listed");
        try {
            new TlsDnsTransport(InetSocketAddress.createUnresolved("family.cloudflare-dns.com", 853),
                    "family.cloudflare-dns.com", 2000, socket -> true).close();
            throw new AssertionError("Upstream transport accepted a name that needs DNS resolution");
        } catch (IllegalArgumentException expected) { }
        // DnsVpnService's configuration: a literal address needs no lookup; the name is TLS identity only.
        new TlsDnsTransport(new InetSocketAddress(InetAddress.getByAddress(new byte[] {1, 1, 1, 3}), 853),
                "family.cloudflare-dns.com", 2000, socket -> true).close();
        System.out.println("EncryptedDnsProviders tests passed (Firefox canary, curated DoH/DoT names, "
                + "strict Private DNS exemption, DDR and IP-pinned upstream)");
    }
}
