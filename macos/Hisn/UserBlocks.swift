import Foundation

/// The two things a person adds by hand that are not domains: words, and apps.
///
/// `SiteLists` already owns hand-maintained *domains*. This owns the other two,
/// and follows the same rules for the same reasons — stored in the shared
/// app-group defaults, read by the filter and handed to the browser extension
/// over the bridge, and guarded so that a running lock can be tightened but
/// never loosened.
///
/// WHY WORDS ARE NOT JUST MORE DOMAINS
/// -----------------------------------
/// A domain is a fact — either the page came from it or it did not. A word is a
/// judgement, and a badly chosen one is far more destructive: `nude` blocks art
/// history, `sex` blocks half of medicine, and a single-character entry would
/// block everything. The published list handles that with weights, negatives and
/// a density floor built from thousands of pages. A hand-typed word gets none of
/// that scrutiny, so this type is deliberately stricter than the term list is:
/// a minimum length, no substring matching, and a cap on how many can be added.
///
/// WHY APPS ARE NETWORK BLOCKS AND NOT LAUNCH BLOCKS
/// -------------------------------------------------
/// Stopping an app from *opening* needs authority macOS does not give a normal
/// application — that is Screen Time and MDM territory. What the content filter
/// can do is refuse its traffic: `NEFilterFlow.sourceAppIdentifier` names the
/// app behind every flow, so a blocked app launches and then reaches nothing.
/// For the apps this exists for — a browser kept around for one purpose, a
/// social client — that is the same outcome. It is not the same for an app that
/// is useful offline, and the UI says so rather than implying otherwise.
public enum UserBlocks {

    public static let termsKey = "customTerms"
    public static let appsKey = "blockedApps"

    /// Short words are where hand-typed lists go wrong. Two and three letter
    /// strings collide with acronyms, initials and half the words in another
    /// language, and the scorer matches whole tokens so there is no way for the
    /// person to see why a page tripped.
    public static let minimumTermLength = 4

    /// A ceiling, not a performance limit — the scorer would happily take
    /// thousands. Past this many hand-typed words nobody remembers what is on
    /// the list, and an unexplained block is indistinguishable from a bug.
    public static let maximumTerms = 200

    public enum BlockError: LocalizedError {
        case wouldLoosenWhileLocked(removedTerms: [String], unblockedApps: [String])
        case termTooShort(String)
        case tooManyTerms(Int)

        public var errorDescription: String? {
            switch self {
            case let .wouldLoosenWhileLocked(terms, apps):
                let refusal: String
                switch (terms.isEmpty, apps.isEmpty) {
                case (false, false):
                    refusal = String(localized: "A lock is running, so you cannot stop blocking \(SiteLists.shortList(terms)) or unblock \(SiteLists.shortList(apps)) until it ends.")
                case (false, true):
                    refusal = String(localized: "A lock is running, so you cannot stop blocking \(SiteLists.shortList(terms)) until it ends.")
                default:
                    refusal = String(localized: "A lock is running, so you cannot unblock \(SiteLists.shortList(apps)) until it ends.")
                }
                return refusal + " " + String(localized: "Adding more still works.")
            case let .termTooShort(word):
                return String(localized: """
                    “\(word)” is too short to block safely. Words need at \
                    least \(minimumTermLength) letters — shorter ones match \
                    parts of ordinary words and block pages you need.
                    """)
            case let .tooManyTerms(count):
                return String(localized: """
                    That is \(count) words; the limit is \(maximumTerms). \
                    Past that nobody remembers what is on the list, and a \
                    surprising block looks like a broken app.
                    """)
            }
        }
    }

    private static var defaults: UserDefaults? {
        UserDefaults(suiteName: LockStore.appGroup)
    }

    // MARK: - Reading

    public static func terms() -> [String] {
        defaults?.stringArray(forKey: termsKey) ?? []
    }

    public static func apps() -> [String] {
        defaults?.stringArray(forKey: appsKey) ?? []
    }

    // MARK: - Words

    /// Normalise a typed word the same way the compiled term list is normalised,
    /// so `إباحية` and `اباحيه` mean the same thing here as they do there.
    /// Returns nil for anything that is not usable as a word.
    public static func normalizeTerm(_ raw: String) -> String? {
        let t = TextNormalizer.normalize(raw)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return nil }
        // A phrase is fine — the scorer matches phrases too — but every part of
        // it has to be a real word.
        let parts = t.split(separator: " ")
        guard !parts.isEmpty else { return nil }
        return parts.joined(separator: " ")
    }

    public struct ParseResult {
        public let terms: [String]
        /// Lines that were not blank and could not be used. Reported rather than
        /// dropped: a silently ignored word looks exactly like a blocked one,
        /// and the person finds out only when the page they expected to be
        /// blocked opens.
        public let ignored: Int
        public let tooShort: [String]
    }

    public static func parseTerms(_ text: String) -> ParseResult {
        var seen = Set<String>()
        var out: [String] = []
        var ignored = 0
        var short: [String] = []

        for rawLine in text.split(whereSeparator: \.isNewline) {
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            if line.isEmpty || line.hasPrefix("#") { continue }
            guard let term = normalizeTerm(line) else {
                ignored += 1
                continue
            }
            guard isLongEnough(term) else {
                short.append(term)
                continue
            }
            if seen.insert(term).inserted { out.append(term) }
        }
        return ParseResult(terms: out.sorted(), ignored: ignored, tooShort: short)
    }

    /// Whether a term is specific enough to be worth blocking on.
    ///
    /// A single token is measured on its own length: `sex` matches an initialism
    /// and a surname and a word in three other languages, and the person has no
    /// way to see why an ordinary page stopped loading.
    ///
    /// A phrase is measured on its letters as a whole, because a phrase only
    /// matches when its words appear *together and in order*. `hot sex` has no
    /// word of four letters and is nonetheless far more specific than either
    /// half — measuring it on its longest word would reject the safest kind of
    /// entry this feature has.
    static func isLongEnough(_ term: String) -> Bool {
        let words = term.split(separator: " ")
        if words.count <= 1 { return term.count >= minimumTermLength }
        return words.joined().count >= minimumTermLength
    }

    // MARK: - Apps

    /// The identifier the content filter sees for an app's traffic.
    ///
    /// `NEFilterFlow.sourceAppIdentifier` reports the *signing* identifier,
    /// which for a normally distributed app is its bundle identifier. Reading it
    /// from the bundle the person picked is what keeps the two in step; asking
    /// them to type `com.example.app` by hand would be a spelling test with a
    /// silent failure as the penalty.
    public static func bundleIdentifier(forAppAt url: URL) -> String? {
        Bundle(url: url)?.bundleIdentifier
    }

    // MARK: - Writing

    /// What a change would loosen, or nil — shared with `PolicyAuthority`.
    public static func loosening(fromTerms: [String], fromApps: [String],
                                 toTerms: [String], toApps: [String]) -> BlockError? {
        let removedTerms = Set(fromTerms).subtracting(toTerms).sorted()
        let unblockedApps = Set(fromApps).subtracting(toApps).sorted()
        guard removedTerms.isEmpty, unblockedApps.isEmpty else {
            return .wouldLoosenWhileLocked(removedTerms: removedTerms,
                                           unblockedApps: unblockedApps)
        }
        return nil
    }

    /// Persist both lists, refusing anything that loosens while a lock runs.
    ///
    /// Same asymmetry as everywhere else in this product: add a word, block an
    /// app, at any time. Removing either waits for the lock to end.
    public static func save(terms newTerms: [String],
                            apps newApps: [String],
                            locked: Bool) throws {
        guard newTerms.count <= maximumTerms else {
            throw BlockError.tooManyTerms(newTerms.count)
        }
        if let short = newTerms.first(where: { !isLongEnough($0) }) {
            throw BlockError.termTooShort(short)
        }

        if locked, let refusal = loosening(fromTerms: terms(), fromApps: apps(),
                                           toTerms: newTerms, toApps: newApps) {
            throw refusal
        }

        defaults?.set(newTerms, forKey: termsKey)
        defaults?.set(newApps, forKey: appsKey)
    }
}
