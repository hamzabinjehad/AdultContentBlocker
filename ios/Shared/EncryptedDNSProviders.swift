import Foundation

/// Well-known public encrypted-DNS hostnames, answered locally with NXDOMAIN so
/// a client that looks them up through Hisn falls back to the filtered system
/// route.
///
/// This mirrors `android/core/.../EncryptedDnsProviders.java`; the Python suite
/// compares the two lists so a provider added on one platform cannot silently
/// stay unblocked on the other. The DNS proxy cannot stop a client with a
/// hard-coded bootstrap address, an IP-literal endpoint, a cached answer or an
/// unlisted provider; this reduces the hostname path only.
///
/// Hisn's own upstream is a pre-resolved address (the transport rejects a name
/// that needs DNS), so listing `family.cloudflare-dns.com` never affects it.
enum EncryptedDNSProviders {
    /// NXDOMAIN for Mozilla's canary turns off Firefox's default, not user-forced, DoH.
    static let firefoxCanary = "use-application-dns.net"

    /// Each entry also covers its subdomains, e.g. `mozilla.cloudflare-dns.com`.
    private static let names: Set<String> = [
        firefoxCanary,
        // RFC 9462 discovery (_dns.resolver.arpa) would advertise the upstream's own endpoints.
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
        "doh.xfinity.com", "private.canadianshield.cira.ca", "protected.canadianshield.cira.ca",
        "family.canadianshield.cira.ca", "odvr.nic.cz", "doh.dns.sb",
        "dns.alidns.com", "doh.pub", "dot.pub",
    ]

    /// Test-visible proof that the shared list is not accidentally empty.
    static var count: Int { names.count }

    /// True for a listed provider or any name under it. A parent site, a
    /// look-alike label (`notcloudflare-dns.com`) and `dns.google.example` stay
    /// governed by the normal list, TLD and term rules.
    static func blocks(_ host: String) -> Bool {
        guard !host.isEmpty else { return false }
        var current = host.lowercased()
        if current.hasSuffix(".") { current.removeLast() }
        while !current.isEmpty {
            if names.contains(current) { return true }
            guard let dot = current.firstIndex(of: ".") else { return false }
            current = String(current[current.index(after: dot)...])
        }
        return false
    }
}
