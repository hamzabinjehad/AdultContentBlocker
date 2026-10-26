package app.hisn.core;

import java.net.IDN;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

/**
 * Well-known public encrypted-DNS hostnames, answered locally with NXDOMAIN so a browser
 * that looks them up through Hisn falls back to the filtered system route. This reduces but
 * cannot eliminate DNS-over-HTTPS bypass: clients with hard-coded bootstrap addresses,
 * IP-literal endpoints, cached answers or unlisted providers never ask Hisn, and the canary
 * does not turn off DoH that the user forced on in Firefox.
 * Hisn's own upstream is a pre-resolved IP address (TlsDnsTransport rejects unresolved
 * addresses) whose hostname is used only for TLS identity, so this list never affects it.
 */
public final class EncryptedDnsProviders {
    /** NXDOMAIN for Mozilla's canary turns off Firefox's default, not user-forced, DoH. */
    public static final String FIREFOX_CANARY = "use-application-dns.net";
    // Each entry also covers its subdomains, e.g. mozilla.cloudflare-dns.com and <id>.dns.nextdns.io.
    private static final DomainPolicy NAMES = new DomainPolicy(Arrays.asList(
            FIREFOX_CANARY,
            // RFC 9462 discovery (_dns.resolver.arpa) would advertise the upstream's own encrypted endpoints.
            "resolver.arpa",
            "dns.google", "dns.google.com", "8888.google",
            "cloudflare-dns.com", "one.one.one.one",
            "dns.quad9.net", "dns9.quad9.net", "dns10.quad9.net", "dns11.quad9.net", "dns12.quad9.net",
            "doh.opendns.com", "doh.familyshield.opendns.com", "doh.cleanbrowsing.org",
            "dns.nextdns.io", "dns1.nextdns.io", "dns2.nextdns.io",
            "dns.adguard-dns.com", "unfiltered.adguard-dns.com", "family.adguard-dns.com", "d.adguard-dns.com",
            "dns.adguard.com", "dns-unfiltered.adguard.com", "dns-family.adguard.com",
            "doh.mullvad.net", "dns.mullvad.net",
            "dns.controld.com", "freedns.controld.com",
            // Other providers built into Firefox or Chrome secure-DNS settings, and large regional ones.
            "doh.xfinity.com", "private.canadianshield.cira.ca", "protected.canadianshield.cira.ca",
            "family.canadianshield.cira.ca", "odvr.nic.cz", "doh.dns.sb",
            "dns.alidns.com", "doh.pub", "dot.pub"), Collections.emptySet());

    private EncryptedDnsProviders() { }

    /**
     * exempt is Android's strict Private DNS hostname, or null. Android can resolve that name
     * through the VPN's DNS server and strict mode fails closed, so answering it NXDOMAIN
     * could break all system DNS.
     */
    public static boolean blocks(String hostname, String exempt) {
        if (NAMES.evaluate(hostname) != DomainPolicy.Decision.BLOCK) return false;
        return exempt == null || !canonical(hostname).equals(canonical(exempt));
    }

    static int size() { return NAMES.blockedRules().size(); }

    private static String canonical(String name) {
        String value = name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
        try {
            value = IDN.toASCII(value);
        } catch (IllegalArgumentException invalid) { /* Compare the raw form. */ }
        return value.toLowerCase(Locale.ROOT);
    }
}
