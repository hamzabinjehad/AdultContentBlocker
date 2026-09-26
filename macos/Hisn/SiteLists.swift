import Foundation

/// The two lists the person maintains by hand: sites they always want blocked
/// on top of the published blocklist, and sites that stay reachable in strict
/// mode.
///
/// These live in the shared app-group defaults under the keys everything else
/// already reads — the network extension takes both, and the native-messaging
/// bridge hands both to the browser extension. The app is the only writer that
/// matters: the browser extension's own copy is overwritten from here on every
/// heartbeat, so a list authored in the extension's options page survives at
/// most one minute. Authoring has to happen on this side.
public enum SiteLists {

    public static let allowlistKey = "allowlist"
    public static let customBlocksKey = "customBlocks"

    public enum ListError: LocalizedError {
        case wouldLoosenWhileLocked(unblocked: [String], allowed: [String])

        public var errorDescription: String? {
            switch self {
            case let .wouldLoosenWhileLocked(unblocked, allowed):
                // Whole sentences per case: fragments joined by "or" only
                // read right in English.
                let refusal: String
                switch (unblocked.isEmpty, allowed.isEmpty) {
                case (false, false):
                    refusal = String(localized: "A lock is running, so you cannot stop blocking \(SiteLists.shortList(unblocked)) or allow \(SiteLists.shortList(allowed)) until it ends.")
                case (false, true):
                    refusal = String(localized: "A lock is running, so you cannot stop blocking \(SiteLists.shortList(unblocked)) until it ends.")
                default:
                    refusal = String(localized: "A lock is running, so you cannot allow \(SiteLists.shortList(allowed)) until it ends.")
                }
                return refusal + " " + String(localized: "Adding blocks and removing allowances still work.")
            }
        }
    }

    /// At most three names, then a count: a refusal listing forty domains is
    /// not read.
    public static func shortList(_ items: [String]) -> String {
        guard items.count > 3 else { return items.formatted(.list(type: .and)) }
        let shown = items.prefix(3).formatted(.list(type: .and, width: .narrow))
        return String(localized: "\(shown) and \(items.count - 3) more")
    }

    private static var defaults: UserDefaults? {
        UserDefaults(suiteName: LockStore.appGroup)
    }

    // MARK: - Reading

    public static func customBlocks() -> [String] {
        defaults?.stringArray(forKey: customBlocksKey) ?? []
    }

    public static func allowlist() -> [String] {
        defaults?.stringArray(forKey: allowlistKey) ?? []
    }

    // MARK: - Parsing

    public struct ParseResult {
        public let domains: [String]
        /// Lines that were not blank and were not a domain we could use. Worth
        /// reporting: a silently dropped line looks exactly like a saved one.
        public let ignored: Int
    }

    /// Turn a free-text box into domains.
    public static func parse(_ text: String) -> ParseResult {
        var seen = Set<String>()
        var domains: [String] = []
        var ignored = 0

        for rawLine in text.split(whereSeparator: \.isNewline) {
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            if line.isEmpty || line.hasPrefix("#") { continue }
            guard let domain = normalize(line) else {
                ignored += 1
                continue
            }
            if seen.insert(domain).inserted { domains.append(domain) }
        }
        return ParseResult(domains: domains.sorted(), ignored: ignored)
    }

    /// Reduce whatever was typed to a bare registrable host, or nil.
    ///
    /// People paste URLs rather than domains, so a line like
    /// `https://www.Example.com/watch?v=1` has to end up as `example.com`.
    /// Matches the normalisation the Python builder applies to upstream lists,
    /// so a hand-typed entry and a published one mean the same thing.
    public static func normalize(_ raw: String) -> String? {
        var s = raw.trimmingCharacters(in: .whitespaces).lowercased()
        guard !s.isEmpty else { return nil }

        if let scheme = s.range(of: "://") { s = String(s[scheme.upperBound...]) }
        if let slash = s.firstIndex(of: "/") { s = String(s[..<slash]) }
        if let at = s.lastIndex(of: "@") { s = String(s[s.index(after: at)...]) }
        if let colon = s.firstIndex(of: ":") { s = String(s[..<colon]) }
        while s.hasSuffix(".") { s.removeLast() }

        // A rule for example.com already covers www.example.com. Keeping both
        // spellings makes the list look broken when only one of them hits.
        if s.hasPrefix("www.") { s = String(s.dropFirst(4)) }

        return isValidDomain(s) ? s : nil
    }

    private static func isValidDomain(_ s: String) -> Bool {
        guard !s.isEmpty, s.count <= 253 else { return false }
        let labels = s.split(separator: ".", omittingEmptySubsequences: false)
        guard labels.count >= 2 else { return false }
        for label in labels {
            guard (1...63).contains(label.count),
                  !label.hasPrefix("-"), !label.hasSuffix("-"),
                  label.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "-") })
            else { return false }
        }
        // A numeric final label means this is an address, not a name. The
        // browser's rule format cannot express one, so accepting it here would
        // enforce it on the Mac and nowhere else.
        return !(labels.last?.allSatisfy(\.isNumber) ?? true)
    }

    // MARK: - Writing

    /// What a change from one pair of lists to another would loosen, or nil.
    /// Checked by membership, never by count (see the type comment). Shared
    /// with the filter's `PolicyAuthority`, which applies the same rule to the
    /// root-owned copy, so the two can never disagree about what "looser" is.
    public static func loosening(fromBlocks: [String], fromAllows: [String],
                                 toBlocks: [String], toAllows: [String]) -> ListError? {
        let unblocked = Set(fromBlocks).subtracting(toBlocks).sorted()
        let allowed = Set(toAllows).subtracting(fromAllows).sorted()
        guard unblocked.isEmpty, allowed.isEmpty else {
            return .wouldLoosenWhileLocked(unblocked: unblocked, allowed: allowed)
        }
        return nil
    }

    /// Persist both lists, refusing any change that loosens protection while a
    /// lock is running.
    ///
    /// The direction is the same asymmetry the rest of the product enforces:
    /// you may add a block and you may withdraw an allowance at any time; the
    /// reverse waits for the lock to end.
    ///
    /// Compared as sets, deliberately, not by count. Swapping one allowed
    /// domain for another leaves the count identical while completely changing
    /// what is reachable — and in strict mode, where the allowlist is the only
    /// thing permitted, that swap is not a loosening but a total bypass.
    public static func save(customBlocks newBlocks: [String],
                            allowlist newAllows: [String],
                            locked: Bool) throws {
        if locked, let refusal = loosening(fromBlocks: customBlocks(),
                                           fromAllows: allowlist(),
                                           toBlocks: newBlocks, toAllows: newAllows) {
            throw refusal
        }
        defaults?.set(newBlocks, forKey: customBlocksKey)
        defaults?.set(newAllows, forKey: allowlistKey)
    }
}
