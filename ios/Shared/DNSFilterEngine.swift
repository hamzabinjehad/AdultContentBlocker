import CryptoKit
import Foundation

/// The on-device DNS decision: the signed seed's core tier, the adult-TLD
/// rail, the hostname keyword layer and the person's own blocked sites.
///
/// The proxy extension and the app's route check compile this same type. It
/// has no network state and nothing here stores or reports a queried name.
struct DNSFilterEngine {
    enum Reason: String, Equatable, CaseIterable {
        case list
        case adultTld
        case hostTerm
        case customSite
        /// A public encrypted-DNS provider name; answered locally so it cannot become the route around Hisn.
        case encryptedDns
    }

    enum Decision: Equatable {
        case blocked(Reason)
        case allowed
        /// Hisn's own alias, answered locally so the app can prove the DNS route
        /// reaches the proxy without querying a real name.
        case selfCheck
    }

    let domains: DomainIndex
    let hostTerms: HostTermPolicy?

    /// `customSites` is passed in rather than stored: the app can change the
    /// person's own blocked sites while the proxy runs, and the extension
    /// re-reads them on a short cache instead of restarting the tunnel.
    func decide(_ question: DNSQuestion, customSites: [String] = []) -> Decision {
        decision(host: question.name, customSites: customSites)
    }

    /// True when a resolved alias target is blocked. CNAME/DNAME/SVCB answers
    /// must be checked as well as the question, or a first-party name can point
    /// at a listed domain and carry its content through an allowed query.
    func blocksHost(_ host: String, customSites: [String] = []) -> Bool {
        if case .blocked = decision(host: host, customSites: customSites) { return true }
        return false
    }

    private func decision(host: String, customSites: [String]) -> Decision {
        guard !host.isEmpty else { return .allowed }
        // The route-check alias, and any name under the reserved `.hisn.invalid`
        // domain, is answered on the device so the app can prove the path. A
        // real resolver would never answer these names.
        if host == "hisn.invalid" || host.hasSuffix(".hisn.invalid") {
            return .selfCheck
        }
        // Before the person's own allowlist, as Android applies it: a saved
        // allowance must not turn a public DoH endpoint into the route around
        // the DNS layer because a bypass is not a content preference.
        if EncryptedDNSProviders.blocks(host) { return .blocked(.encryptedDns) }
        if CustomSitePolicy.covers(customSites, host) { return .blocked(.customSite) }
        if domains.blocks(host: host) { return .blocked(.list) }
        if AdultTldGuard.matches(host: host) { return .blocked(.adultTld) }
        if hostTerms?.matches(host) == true { return .blocked(.hostTerm) }
        return .allowed
    }
}

/// Loads the DNS filter's build-time assets, verifying every byte it trusts.
///
/// The seed is signature-verified when `prepare_rules.py --dns` builds this
/// data, and the generated files' SHA-256 digests are pinned in
/// `dns-metadata.json`. Apple code signing protects all three, and the digest
/// check catches a partial or replaced resource at runtime. A failed domain
/// list is fatal: the extension refuses to start rather than run unfiltered.
/// A failed keyword list is not: the domain list still protects, exactly as on
/// Android, and `termsLoaded` says which layers are live.
enum DNSFilterAssets {
    static let metadataName = "dns-metadata.json"
    static let domainsName = "dns-domains.txt"
    static let termsName = "dns-host-terms.json"
    /// Written by the app into the shared App Group after every custom-site
    /// save. Read-only in the extension; damage means no custom sites, never a
    /// reason to disable the seed list.
    static let customSitesKey = "hisn.dns.customSites"
    /// The App Group shared by the app and its extensions.
    static let group = "group.app.hisn.mobile"
    static let schema = 1
    static let maximumTermsBytes = 2 << 20

    struct Metadata: Decodable {
        let schema: Int
        let version: Int
        let count: Int
        let built: String?
        let domainsSHA256: String
        let domainsBytes: Int
        let termsSHA256: String
        let termsBytes: Int
        let termsVersion: Int
        let adultTlds: [String]

        enum CodingKeys: String, CodingKey {
            case schema, version, count, built
            case domainsSHA256 = "domains_sha256"
            case domainsBytes = "domains_bytes"
            case termsSHA256 = "terms_sha256"
            case termsBytes = "terms_bytes"
            case termsVersion = "terms_version"
            case adultTlds = "adult_tlds"
        }
    }

    enum LoadError: Error, Equatable {
        case missingMetadata
        case invalidMetadata
        case missingDomains
        case domainsDigestMismatch
        case domainsCountMismatch
        case domainsBytesMismatch
        case unsupportedSchema
        case adultTldMismatch
    }

    struct Loaded {
        let engine: DNSFilterEngine
        let metadata: Metadata
        let termsLoaded: Bool
    }

    static func load(bundle: Bundle = .main, directory: URL? = nil) throws -> Loaded {
        let root = directory ?? bundle.bundleURL
        let metadata = try readMetadata(directory: root)
        guard metadata.schema == schema else { throw LoadError.unsupportedSchema }
        guard Set(metadata.adultTlds) == AdultTldGuard.tlds else {
            throw LoadError.adultTldMismatch
        }
        guard let domainData = try? Data(contentsOf: root.appendingPathComponent(domainsName),
                                         options: .mappedIfSafe) else {
            throw LoadError.missingDomains
        }
        guard domainData.count == metadata.domainsBytes else { throw LoadError.domainsBytesMismatch }
        guard sha256Hex(domainData) == metadata.domainsSHA256 else {
            throw LoadError.domainsDigestMismatch
        }
        let domains: DomainIndex
        do {
            domains = try DomainIndex(fileData: domainData)
        } catch {
            throw LoadError.domainsCountMismatch
        }
        guard domains.count == metadata.count else { throw LoadError.domainsCountMismatch }

        // The keyword layer is a second rail, not the list. A missing or damaged
        // terms file is reported through `termsLoaded` and leaves the domain
        // list enforcing, exactly as Android's resolver does. Failing the whole
        // tunnel here would be a larger protection loss than the layer it drops.
        var terms: HostTermPolicy?
        if let termsData = try? Data(contentsOf: root.appendingPathComponent(termsName),
                                     options: .mappedIfSafe),
           termsData.count == metadata.termsBytes,
           sha256Hex(termsData) == metadata.termsSHA256,
           let payload = try? JSONDecoder().decode(HostTermPolicy.Payload.self,
                                                   from: termsData),
           payload.version == metadata.termsVersion {
            terms = try? HostTermPolicy(payload: payload)
        }
        let engine = DNSFilterEngine(domains: domains, hostTerms: terms)
        return Loaded(engine: engine, metadata: metadata, termsLoaded: terms != nil)
    }

    static func readMetadata(directory: URL) throws -> Metadata {
        guard let data = try? Data(contentsOf: directory.appendingPathComponent(metadataName)),
              data.count <= 64 << 10 else { throw LoadError.missingMetadata }
        guard let metadata = try? JSONDecoder().decode(Metadata.self, from: data),
              metadata.count > 0, metadata.count <= DomainIndex.maximumCount,
              metadata.domainsBytes > 0, metadata.domainsBytes <= DomainIndex.maximumBytes,
              metadata.termsBytes > 0, metadata.termsBytes <= maximumTermsBytes,
              metadata.domainsSHA256.count == 64, metadata.termsSHA256.count == 64,
              metadata.version >= 1, metadata.termsVersion >= 1 else {
            throw LoadError.invalidMetadata
        }
        return metadata
    }

    /// The person's own blocked sites, as the app last wrote them. Unreadable or
    /// invalid data is no sites; it never turns the keyword or seed layer off.
    static func customSites(defaults: UserDefaults?) -> [String] {
        guard let data = defaults?.data(forKey: customSitesKey),
              let sites = CustomSitePolicy.decode(data) else { return [] }
        return sites
    }

    static func sha256Hex(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }
}
