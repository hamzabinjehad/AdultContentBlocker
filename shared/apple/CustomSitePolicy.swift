import Foundation

/// The person's own extra sites for Hisn's Screen Time web filter.
///
/// Apple's adult-content filter (`WebContentSettings.FilterPolicy.auto`) accepts
/// a short list of additional domains. This is that list: canonical ASCII
/// hosts, add-only while a commitment runs, the same rules the Mac app and the
/// browser extension use for their custom blocks. A saved or applied list is
/// configuration evidence, not proof that any request was blocked.
enum CustomSitePolicy {
    /// Apple documents up to 50 extra domains for `.auto`; developers report the
    /// filter silently stops blocking once the list crosses 49. Stay at 49.
    static let limit = 49

    enum AddResult: Equatable { case added([String]), invalid, duplicate, full }

    /// Scheme, path, userinfo and port stripped; trailing dots and a leading
    /// `www.` dropped; ASCII labels only. An internationalized name must be
    /// entered in its `xn--` form. A numeric last label is an address, not a site.
    static func canonicalHost(_ raw: String) -> String? {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !s.isEmpty else { return nil }
        if let scheme = s.range(of: "://") { s = String(s[scheme.upperBound...]) }
        if let end = s.firstIndex(where: { $0 == "/" || $0 == "?" || $0 == "#" }) { s = String(s[..<end]) }
        if let at = s.lastIndex(of: "@") { s = String(s[s.index(after: at)...]) }
        if let colon = s.firstIndex(of: ":") { s = String(s[..<colon]) }
        while s.hasSuffix(".") { s.removeLast() }
        if s.hasPrefix("www.") { s = String(s.dropFirst(4)) }
        guard !s.isEmpty, s.count <= 253 else { return nil }
        let labels = s.split(separator: ".", omittingEmptySubsequences: false)
        guard labels.count >= 2 else { return nil }
        for label in labels {
            guard (1...63).contains(label.count), !label.hasPrefix("-"), !label.hasSuffix("-"),
                  label.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "-") }) else { return nil }
        }
        if let last = labels.last, last.allSatisfy({ $0.isASCII && $0.isNumber }) { return nil }
        return s
    }

    /// A saved site covers itself and every subdomain, with label boundaries.
    static func covers(_ sites: [String], _ host: String) -> Bool {
        sites.contains { host == $0 || host.hasSuffix("." + $0) }
    }

    /// Adding a parent replaces the subdomains it now covers, freeing slots.
    static func adding(_ raw: String, to sites: [String]) -> AddResult {
        guard let host = canonicalHost(raw) else { return .invalid }
        guard !covers(sites, host) else { return .duplicate }
        let kept = sites.filter { !$0.hasSuffix("." + host) }
        guard kept.count < limit else { return .full }
        return .added((kept + [host]).sorted())
    }

    /// While committed the list can grow but never stop covering a saved site.
    static func permits(current: [String], proposed: [String], committed: Bool) -> Bool {
        !committed || current.allSatisfy { covers(proposed, $0) }
    }

    static func isValid(_ sites: [String]) -> Bool {
        sites.count <= limit && Set(sites).count == sites.count
            && sites.allSatisfy { canonicalHost($0) == $0 }
    }

    static func decode(_ data: Data) -> [String]? {
        guard let sites = try? JSONDecoder().decode([String].self, from: data), isValid(sites) else { return nil }
        return sites
    }
}

/// Mirrored app-local persistence. Damage is reported, never read as "no sites".
final class CustomSiteStore {
    enum Failure: Error { case corrupt, invalid }
    private let store: MirroredRuleStore
    init(storage: RuleStorage) {
        store = MirroredRuleStore(key: "hisn.mobile.customSites", storage: storage) { CustomSitePolicy.decode($0) != nil }
    }
    func load() throws -> [String] {
        guard let data = try store.load()?.payload else { return [] }
        guard let sites = CustomSitePolicy.decode(data) else { throw Failure.corrupt }
        return sites
    }
    func save(_ sites: [String]) throws {
        guard CustomSitePolicy.isValid(sites) else { throw Failure.invalid }
        try store.save(sites.isEmpty ? nil : JSONEncoder().encode(sites))
    }
    static func deviceLocal() -> CustomSiteStore {
        CustomSiteStore(storage: NSClassFromString("XCTestCase") == nil
            ? DefaultsRuleStorage(defaults: .standard) : EphemeralRuleStorage())
    }
}
