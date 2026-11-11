import Foundation
import CryptoKit

/// The filter's aggregate count of denied flows for one local day.
///
/// A number and a day key, nothing else: no host, app, path or address is
/// recorded anywhere, and the type has no field that could carry one. The day
/// key is the local "yyyy-MM-dd" the count belongs to. `count(on:)` answers
/// zero for any other day, so a filter that stays up across midnight starts
/// the new day at zero without a timer or a reset write. The count is capped:
/// past the cap the app can only say "many", and an unbounded counter in a
/// long-running extension buys nothing.
public struct BlockedFlowTally: Equatable {
    public static let cap = 9_999

    private var day: String
    private var count: Int

    /// The local midnights that bound `day`. The socket filter notes a denied
    /// flow for every block, and deriving the calendar day each time —
    /// `Calendar.dateComponents` under a lock — cost microseconds on exactly
    /// the flows that are already doing the most work. Two date comparisons
    /// settle the common case; the slow path runs at most once a day.
    private var dayStart: Date
    private var dayEnd: Date

    public init(now: Date = Date(), calendar: Calendar = .current) {
        day = Self.dayKey(for: now, calendar: calendar)
        count = 0
        dayStart = calendar.startOfDay(for: now)
        dayEnd = calendar.date(byAdding: .day, value: 1, to: dayStart) ?? dayStart
    }

    /// Denied flows on the local day `now` belongs to.
    public func count(on now: Date, calendar: Calendar = .current) -> Int {
        Self.dayKey(for: now, calendar: calendar) == day ? count : 0
    }

    /// Count one denied flow, moving to a new day first when needed.
    public mutating func note(at now: Date, calendar: Calendar = .current) {
        if now >= dayStart, now < dayEnd {
            if count < Self.cap { count += 1 }
            return
        }
        // Midnight (or a clock change) crossed: re-derive the day the slow way.
        let key = Self.dayKey(for: now, calendar: calendar)
        if key != day {
            day = key
            count = 0
        }
        dayStart = calendar.startOfDay(for: now)
        dayEnd = calendar.date(byAdding: .day, value: 1, to: dayStart) ?? dayStart
        if count < Self.cap { count += 1 }
    }

    /// The local calendar day as "yyyy-MM-dd" — the key both sides compare.
    public static func dayKey(for date: Date, calendar: Calendar = .current) -> String {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d",
                      parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }
}

/// Loads, verifies and answers questions about the blocklist.
///
/// Two hard requirements shape this type:
///
///  * **Never apply an unverified list.** The signature check is not optional
///    and there is no code path that skips it. A blocklist is a security
///    control; a list you cannot authenticate is an attacker's list.
///
///  * **Lookups must be fast enough to sit in the packet path.** The network
///    extension calls `isBlocked` for every new flow, so it has to be O(1) and
///    allocation-free in the common case. A `Set<String>` of ~1M domains costs
///    roughly 60–80MB resident, which is too much for an extension; we hold
///    64-bit hashes instead, which is ~8MB and has a negligible collision rate
///    at this scale.
public final class BlocklistStore {

    public static let shared = BlocklistStore()

    /// Ed25519 public key for the list-signing key, raw 32 bytes.
    /// Rotating this requires shipping an app update — treat the private half
    /// accordingly.
    public static let productionPublicKeyHex =
        "eb6751a0429413d0cfb24a778f7d6ecdd8436e573af94c325de5fcbd38e59ede"

    /// The key this instance verifies against.
    ///
    /// Held per-instance so a test can sign a fixture with a throwaway key and
    /// exercise the whole accept path. `shared` — the only instance the app and
    /// the extension ever use — is pinned to the production key, so this is a
    /// seam for tests, not a way to configure verification at runtime.
    private let publicKeyHex: String

    public struct Manifest: Codable {
        public let schema: Int
        public let version: Int
        public let built_at: String
        public let domain_count: Int
        public let core_domain_count: Int
        public let artifacts: [String: Artifact]

        public struct Artifact: Codable {
            public let sha256: String
            public let bytes: Int
        }
    }

    public enum LoadError: Error, CustomStringConvertible {
        case badSignature
        case rollback(offered: Int, held: Int)
        case hashMismatch(String)
        case tooSmall(Int)
        case malformed(String)

        public var description: String {
            switch self {
            case .badSignature:            return "list signature is invalid"
            case let .rollback(o, h):      return "rollback rejected: offered v\(o), holding v\(h)"
            case let .hashMismatch(name):  return "artifact hash mismatch: \(name)"
            case let .tooSmall(n):         return "list suspiciously small: \(n) domains"
            case let .malformed(what):     return "malformed: \(what)"
            }
        }
    }

    /// 64-bit hashes of every blocked domain.
    private var hashes: Set<UInt64> = []
    private var allowlist: Set<UInt64> = []
    private var customBlocks: Set<UInt64> = []
    private var networkStrict = false
    private var networkBlockedApps: Set<String> = []

    /// The keyword layer. Kept as strings rather than hashes: the Unicode path
    /// needs `substrings`, which a hash cannot answer, and both sets are small
    /// enough (a few hundred entries) that the memory argument driving the
    /// domain hashing does not apply.
    ///
    /// The hash/byte forms beside each set are what the ASCII fast path reads.
    /// `handleNewFlow` asks this question for every socket the machine opens,
    /// and the overwhelming majority of those hosts are plain ASCII: they must
    /// answer from bytes, without normalising, tokenising or allocating.
    private var hostTokens: Set<String> = []
    private var hostTokenHashes: Set<UInt64> = []
    private var hostSubstrings: [String] = []
    /// Substring terms grouped by first byte: the ASCII path scans the hostname
    /// once and only compares the terms that could start at that position.
    private var substringBuckets: [[[UInt8]]] = Array(repeating: [], count: 256)
    private var neverKeyword: Set<String> = []
    private var neverHashes: Set<UInt64> = []
    /// Sites whose own name collides with a term (keyword_exempt_hosts.txt):
    /// the keyword layer steps aside for them and their subdomains.
    private var keywordExemptHosts: Set<String> = []
    private var exemptHashes: Set<UInt64> = []
    private var _version: Int = 0
    private var _domainCount: Int = 0

    // MARK: - Blocked-flow tally

    /// Denied flows so far this local day, or zero. An aggregate count ONLY:
    /// no host, app or address is ever recorded anywhere in this store. The
    /// tally exists so the app can say the filter is acting without building
    /// the browsing history this product must never keep. It resets when the
    /// local day changes and when the filter process restarts, and it is
    /// evidence of nothing but itself.
    public func blockedFlowsToday(now: Date = Date()) -> (day: String, count: Int) {
        let tally = tallyLock.withLock { self.tally }
        return (BlockedFlowTally.dayKey(for: now), tally.count(on: now))
    }

    private let tallyLock = NSLock()
    private var tally = BlockedFlowTally()

    /// Count one denied flow. The list queue is concurrent and this is a
    /// write, so the tally has its own lock rather than serialising every
    /// lookup in the packet path.
    private func noteDeniedFlow(_ now: Date) {
        tallyLock.withLock { tally.note(at: now) }
    }

    private let queue = DispatchQueue(label: "app.hisn.blocklist",
                                      attributes: .concurrent)

    /// Read through the same queue that guards the list. The network extension
    /// installs a new list from one thread while the app reads these from
    /// another, so an unsynchronised stored property here is a data race.
    public var version: Int { queue.sync { _version } }
    public var domainCount: Int { queue.sync { _domainCount } }
    /// Host terms currently installed — the keyword layer's size, for the
    /// status header. Zero means the layer is off.
    public var hostTermCount: Int { queue.sync { hostTokens.count + hostSubstrings.count } }

    /// The lowest version `load` will accept, whatever this instance has seen.
    ///
    /// Rollback protection compares against `version`, which starts at 0 in
    /// every new process — so a filter restart used to forget every version it
    /// had ever installed, and a validly signed OLD list would be accepted on
    /// the next launch. The filter persists the highest version it installed
    /// and sets this at startup, so the floor survives the process.
    public var versionFloor: Int {
        get { queue.sync { _versionFloor } }
        set { queue.sync(flags: .barrier) { _versionFloor = max(_versionFloor, newValue) } }
    }
    private var _versionFloor: Int = 0

    /// The published domain list is raw DEFLATE (`domains.packed.deflate`,
    /// see build.py): the plain file is over GitHub's 100 MiB limit.
    public static func inflate(_ data: Data) throws -> Data {
        do {
            return try (data as NSData).decompressed(using: .zlib) as Data
        } catch {
            throw LoadError.malformed("domains.packed.deflate does not inflate")
        }
    }

    public static func sha256Hex(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    public init(publicKeyHex: String = BlocklistStore.productionPublicKeyHex) {
        self.publicKeyHex = publicKeyHex
    }

    // MARK: - Lookup

    /// Stable, seeded 64-bit hash. SipHash via Swift's `Hasher` is not stable
    /// across launches (it is randomly seeded), so we cannot use it here — the
    /// hashes have to mean the same thing in the app and the extension, and
    /// across reboots. FNV-1a is stable, fast, and good enough for set
    /// membership where a collision only ever over-blocks.
    /// Characters trimmed from both ends of a hostname before matching:
    /// the FQDN trailing dot, a stray leading dot, and whitespace.
    private static let hostTrimSet = CharacterSet(charactersIn: ". \t\n\r")

    /// "xn--" as bytes: an IDN label that the keyword layer must decode first.
    private static let punycodeLabelMarker: [UInt8] = [0x78, 0x6E, 0x2D, 0x2D]

    /// ".." as bytes: an empty label. The reference splits labels and drops
    /// empty ones, which shifts which suffixes the exemption test sees.
    private static let emptyLabelMarker: [UInt8] = [0x2E, 0x2E]

    /// The byte form of `hostTrimSet`, so the flow path can trim without
    /// going through Foundation's Unicode machinery.
    @inline(__always)
    private static func isTrimByte(_ byte: UInt8) -> Bool {
        byte == 0x2E || byte == 0x20 || byte == 0x09 || byte == 0x0A || byte == 0x0D
    }

    @inline(__always)
    private static func isASCIIAlnum(_ byte: UInt8) -> Bool {
        (byte >= 0x61 && byte <= 0x7A)      // a-z
            || (byte >= 0x41 && byte <= 0x5A)   // A-Z
            || (byte >= 0x30 && byte <= 0x39)   // 0-9
    }

    @inline(__always)
    private static func isASCIIDigit(_ byte: UInt8) -> Bool {
        byte >= 0x30 && byte <= 0x39
    }

    @inline(__always)
    static func hash(_ domain: Substring) -> UInt64 {
        var h: UInt64 = 0xcbf2_9ce4_8422_2325
        for byte in domain.utf8 {
            h ^= UInt64(byte)
            h &*= 0x0000_0100_0000_01B3
        }
        return h
    }

    /// The same FNV-1a over an already-contiguous byte buffer. The list parser
    /// and every flow-path lookup use this one; it is what keeps them from
    /// building a String per line or per parent domain.
    @inline(__always)
    static func hash(bytes: UnsafeBufferPointer<UInt8>) -> UInt64 {
        var h: UInt64 = 0xcbf2_9ce4_8422_2325
        for byte in bytes {
            h ^= UInt64(byte)
            h &*= 0x0000_0100_0000_01B3
        }
        return h
    }

    @inline(__always)
    static func hash(bytes: [UInt8]) -> UInt64 {
        bytes.withUnsafeBufferPointer { hash(bytes: $0) }
    }

    /// Canonicalise a hostname: IDNA dot separators become ".", letters are
    /// lowercased, and the trailing FQDN dot, a stray leading dot and
    /// surrounding whitespace are removed.
    ///
    /// `pornhub.com.` is a valid absolute FQDN, browsers accept it, and it
    /// resolves to exactly the same site as `pornhub.com` — but it hashes
    /// differently, so without this every blocked domain has a one-keystroke
    /// bypass.
    ///
    /// This runs for every new socket. A host that is already lowercase ASCII
    /// with no stray dots returns unchanged, with no allocation at all: the
    /// Foundation version (`lowercased` + three `replacingOccurrences` +
    /// `trimmingCharacters`) cost several microseconds per flow.
    @inline(__always)
    private static func canonicalHost(_ host: String) -> String {
        // `String.withUTF8` is mutating; work on a copy so `original` can be
        // returned untouched when nothing needs rewriting.
        let original = host
        var mutable = host
        let ascii: String? = mutable.withUTF8 { buffer -> String? in
            var start = 0, end = buffer.count
            while start < end, isTrimByte(buffer[start]) { start += 1 }
            while end > start, isTrimByte(buffer[end - 1]) { end -= 1 }
            guard start < end else { return "" }

            // ASCII needs no Unicode folding; lowercase is the only rewrite.
            var needsRewrite = false
            for i in start..<end {
                let byte = buffer[i]
                // Non-ASCII needs ICU lowercase and the IDNA separators.
                if byte >= 0x80 { return nil }
                if byte >= 0x41, byte <= 0x5A { needsRewrite = true }
            }
            if !needsRewrite {
                if start == 0, end == buffer.count { return original }
                return String(decoding: buffer[start..<end], as: UTF8.self)
            }
            var out = [UInt8]()
            out.reserveCapacity(end - start)
            for i in start..<end {
                let byte = buffer[i]
                out.append(byte >= 0x41 && byte <= 0x5A ? byte + 0x20 : byte)
            }
            return String(decoding: out, as: UTF8.self)
        }
        return ascii ?? canonicalUnicodeHost(host)
    }

    /// The non-ASCII canonicalisation path: ICU lowercase and the three IDNA
    /// dot separators. Slow by comparison, and rare on the flow path.
    private static func canonicalUnicodeHost(_ host: String) -> String {
        var scalars = String.UnicodeScalarView()
        scalars.reserveCapacity(host.unicodeScalars.count)
        for scalar in host.unicodeScalars {
            switch scalar.value {
            case 0x3002, 0xFF0E, 0xFF61: scalars.append(".")
            default: scalars.append(scalar)
            }
        }
        return String(scalars).lowercased().trimmingCharacters(in: hostTrimSet)
    }

    /// Walk `host` and each of its parent domains, stopping at the first
    /// decision `decide` returns. Returning nil means "keep walking up".
    ///
    /// Walking is what lets the builder collapse subdomains: a list entry of
    /// `example.com` covers `cdn.videos.example.com` without the child being
    /// listed. Every lookup shares this loop so the block, allow and strict
    /// questions cannot drift apart in how far they walk.
    ///
    /// `host` must already be canonicalised, and callers must hold `queue`.
    /// Labels are hashed in place from the UTF-8 buffer: the previous version
    /// allocated a Substring per label and re-canonicalised the host on every
    /// call.
    @inline(__always)
    private func walk<T>(_ host: String, _ decide: (UInt64) -> T?) -> T? {
        var host = host
        return host.withUTF8 { buffer -> T? in
            guard !buffer.isEmpty else { return nil }
            var start = 0
            while true {
                let suffix = UnsafeBufferPointer(rebasing: buffer[start...])
                if let decision = decide(Self.hash(bytes: suffix)) { return decision }
                // `rebasing:` resets the slice's indices to zero, so the dot
                // is relative to `start`.
                guard let dot = suffix.firstIndex(of: 0x2E) else { return nil }
                let next = start + dot + 1
                // A bare TLD is never a useful match.
                guard buffer[next...].contains(0x2E) else { return nil }
                start = next
            }
        }
    }

    /// True if `host` or any of its parent domains is blocked.
    ///
    /// The published list is consulted first and the keyword layer only when it
    /// misses, so a domain that is explicitly allowed can never be re-blocked
    /// by a keyword that happens to appear in its name.
    public func isBlocked(host: String) -> Bool {
        queue.sync { blocked(Self.canonicalHost(host)) }
    }

    /// True when the published list names `host` or a parent domain.
    ///
    /// Hand lists and the keyword layer are deliberately excluded: the site
    /// checker needs to name which rule decided an answer, and `isBlocked`
    /// deliberately answers the combined question. This is a read-only query;
    /// it changes no policy.
    public func isOnPublishedList(host: String) -> Bool {
        queue.sync {
            walk(Self.canonicalHost(host)) { hashes.contains($0) ? true : nil } ?? false
        }
    }

    /// True when the hostname itself matches the keyword layer. Same reason as
    /// `isOnPublishedList`: the checker explains one layer at a time.
    public func matchesKeyword(host: String) -> Bool {
        queue.sync { hostMatchesTerm(Self.canonicalHost(host)) }
    }

    // The entire verdict uses one policy/list snapshot, including exceptions
    // and keyword rules. `host` must already be canonicalised; callers must
    // hold `queue`.
    private func blocked(_ host: String) -> Bool {
        if WebProtectionPolicy.isBlockedCanonical(host) { return true }
        switch handVerdict(host) {
        case .some(true):  return false          // a named allowance, the escape hatch
        case .some(false): return true           // a named block outranks a broad allowance
        case .none:        break
        }
        // A sponsored adult TLD needs no list entry. It sits after the hand
        // allowance — the escape hatch a false positive keeps — and before the
        // list, so it still works when the list is missing or the terms file
        // failed to load.
        if AdultTldGuard.matches(host: host) { return true }
        if walk(host, { hashes.contains($0) ? true : nil }) ?? false { return true }
        return hostMatchesTerm(host)
    }

    /// The hand-maintained lists in one walk per lookup, or no walk at all
    /// when both are empty — which is the state almost every install is in.
    ///
    /// `nil` means neither list names the host or a parent. `true` means an
    /// allowance was found before any block on the way up; `false` means a
    /// custom block was found first, which is the safer reading of a
    /// contradiction. This replaces two full parent walks (`allowedByHand`
    /// then `listSaysBlocked`) with one.
    private func handVerdict(_ host: String) -> Bool? {
        guard !allowlist.isEmpty || !customBlocks.isEmpty else { return nil }
        return walk(host) { h in
            if customBlocks.contains(h) { return false }
            if allowlist.contains(h) { return true }
            return nil
        }
    }

    // MARK: - Keyword layer

    /// Does the hostname itself read as adult content?
    ///
    /// ── THE `essex` PROBLEM ────────────────────────────────────────────────
    /// This is the highest-impact risk in the whole feature, because a wrong
    /// hostname verdict takes out an ENTIRE SITE rather than one page. Naive
    /// substring matching blocks `essex.gov.uk`, `sussex.ac.uk`,
    /// `analytics.google.com`, `tasks.office.com` and `therapist.com`.
    ///
    /// Three defences, all required:
    ///   1. Ambiguous terms match whole TOKENS only. `essex` is one token and
    ///      never equals `sex`.
    ///   2. `neverKeyword` tokens are REMOVED from the haystack before any
    ///      substring test, rather than exempting the host outright — so
    ///      `essex-porn.com` is still caught while `essex.gov.uk` is not.
    ///   3. Punycode is decoded first, or Arabic-script domains are invisible
    ///      to the entire mechanism.
    ///
    /// Callers must already hold `queue`.
    private func hostMatchesTerm(_ host: String) -> Bool {
        guard !hostTokens.isEmpty || !hostSubstrings.isEmpty else { return false }
        if let verdict = hostMatchesTermASCII(host) { return verdict }
        return hostMatchesTermUnicode(host)
    }

    /// The keyword answer for a plain ASCII hostname, allocation-free.
    ///
    /// Returns nil when the answer genuinely needs the Unicode path: a
    /// non-ASCII name, an `xn--` label that has to be decoded first, or a host
    /// with more than one distinct `never_keyword`, whose haystack rewrite is
    /// a Set's iteration order in the reference path. Those are a minority of
    /// flows. Everything else — `www.google.com`, `cdn.jsdelivr.net` — is
    /// answered by scanning bytes.
    ///
    /// Every rule below is the Unicode path's rule restated for ASCII:
    /// `normalize` is the identity on lowercase ASCII, so the cleaned haystack
    /// is the canonical host itself; tokenisation splits on exactly the
    /// non-alphanumeric ASCII bytes; and the only variants an ASCII token can
    /// have are the trailing-digit trim and letter elongation, because the
    /// Arabic clitic prefixes and suffixes cannot prefix an ASCII token.
    private func hostMatchesTermASCII(_ host: String) -> Bool? {
        var host = host
        return host.withUTF8 { buffer -> Bool? in
            guard !buffer.isEmpty else { return false }
            for byte in buffer where byte >= 0x80 { return nil }
            // An IDN label travels as ASCII and has to be decoded first.
            if Self.contains(buffer, Self.punycodeLabelMarker) { return nil }
            // The reference drops empty labels before the exemption test; a
            // malformed "a..b" would disagree about which suffix is tested.
            if Self.contains(buffer, Self.emptyLabelMarker) { return nil }

            // 1. keyword_exempt_hosts, and every subdomain of one.
            if !exemptHashes.isEmpty, Self.exemptSuffixHit(buffer, exemptHashes) {
                return false
            }

            // 2. A whole-token term.
            var innocent: [[UInt8]] = []
            var i = 0
            while i < buffer.count {
                if !Self.isASCIIAlnum(buffer[i]) { i += 1; continue }
                let start = i
                while i < buffer.count, Self.isASCIIAlnum(buffer[i]) { i += 1 }
                if tokenMatchesTerm(buffer, start: start, end: i, innocent: &innocent) {
                    return true
                }
            }

            // The reference path removes innocent words from the haystack
            // before testing substrings. One distinct innocent word is
            // reproducible here byte for byte; more than one is a Set's
            // iteration order over there, so take that path instead.
            guard innocent.count <= 1 else { return nil }
            if let removal = innocent.first {
                return containsSubstringTerm(removingOccurrences: removal, in: buffer)
            }

            // 3. A substring term anywhere in the name.
            return containsSubstringTerm(buffer)
        }
    }

    /// The reference path: normalisation, punycode, tokenisation and the
    /// never-keyword haystack rewrite. Used for every host the ASCII path
    /// declines, and asserted against that path by the differential test.
    private func hostMatchesTermUnicode(_ host: String) -> Bool {
        // A site named with a term — jins.com, koss.com — and its subdomains.
        let labels = host.lowercased().trimmingCharacters(in: Self.hostTrimSet)
            .split(separator: ".").map(String.init)
        if labels.count >= 2, (0..<(labels.count - 1)).contains(where: {
            keywordExemptHosts.contains(labels[$0...].joined(separator: "."))
        }) {
            return false
        }

        let cleaned = TextNormalizer.normalize(
            Punycode.decodeHost(
                host.trimmingCharacters(in: Self.hostTrimSet)))
        guard !cleaned.isEmpty else { return false }

        // Test each variant directly instead of unioning every token into one
        // set and intersecting twice. Same rule — a variant that is a
        // `never_keyword` never counts as a hit — without two Set allocations
        // per hostname.
        var innocent: Set<String> = []
        for token in TextNormalizer.tokenize(cleaned) {
            for variant in TextNormalizer.variants(of: token) {
                if neverKeyword.contains(variant) {
                    innocent.insert(variant)
                } else if hostTokens.contains(variant) {
                    return true
                }
            }
        }

        var haystack = cleaned
        for word in innocent {
            haystack = haystack.replacingOccurrences(of: word, with: "")
        }
        // The same byte-bucket scan the ASCII path uses. Both sides are
        // NFC-normalised by `TextNormalizer`, so a UTF-8 substring match is
        // the same question as `String.contains`, without the fixed cost of
        // one Foundation containment call per term.
        var mutable = haystack
        return mutable.withUTF8 { containsSubstringTerm($0) }
    }

    /// True when the host, or a parent above its bare TLD, is an exempt site.
    /// `jins.com` and `cdn.jins.com` step aside; the TLD never does.
    @inline(__always)
    private static func exemptSuffixHit(_ buffer: UnsafeBufferPointer<UInt8>,
                                        _ exempt: Set<UInt64>) -> Bool {
        var lastDot = -1
        for i in 0..<buffer.count where buffer[i] == 0x2E { lastDot = i }
        guard lastDot >= 0 else { return false }
        var start = 0
        while start < lastDot {
            if exempt.contains(hash(bytes: UnsafeBufferPointer(rebasing: buffer[start...]))) {
                return true
            }
            // A slice of an UnsafeBufferPointer keeps the parent's indices, so
            // `dot` is already absolute here (unlike the rebased buffer above).
            guard let dot = buffer[start...].firstIndex(of: 0x2E) else { return false }
            start = dot + 1
        }
        return false
    }

    /// One token, and the two forms worth testing beside it. Every form that is
    /// a `never_keyword` is collected in `innocent`: the caller removes those
    /// words from the haystack before testing substrings.
    @inline(__always)
    private func tokenMatchesTerm(_ buffer: UnsafeBufferPointer<UInt8>,
                                  start: Int, end: Int,
                                  innocent: inout [[UInt8]]) -> Bool {
        let token = UnsafeBufferPointer(rebasing: buffer[start..<end])
        if termVerdict(token, innocent: &innocent) == true { return true }

        // A trailing digit run is hostname decoration ("sharmota123"). Only a
        // stripped form of at least three characters is worth testing.
        var shortEnd = end
        while shortEnd > start, Self.isASCIIDigit(buffer[shortEnd - 1]) { shortEnd -= 1 }
        if shortEnd != end, shortEnd - start >= 3,
           termVerdict(UnsafeBufferPointer(rebasing: buffer[start..<shortEnd]),
                       innocent: &innocent) == true {
            return true
        }

        // Letter elongation: "pooorn" is "porn" held down. Ordinary words top
        // out at doubles, so only a run of three or more can collapse.
        guard hasLongRun(buffer, start: start, end: end) else { return false }
        for keep in 1...2 {
            var collapsed = [UInt8]()
            collapsed.reserveCapacity(end - start)
            var i = start
            while i < end {
                let ch = buffer[i]
                var count = 1
                i += 1
                while i < end, buffer[i] == ch { count += 1; i += 1 }
                collapsed.append(contentsOf: repeatElement(ch, count: count >= 3 ? keep : count))
            }
            guard collapsed.count >= 3 else { continue }
            let hit = collapsed.withUnsafeBufferPointer {
                termVerdict($0, innocent: &innocent)
            }
            if hit == true { return true }
        }
        return false
    }

#if DEBUG
    /// Test seam, compiled out of release builds.
    ///
    /// Returns both keyword paths for one raw host: the ASCII fast path's
    /// answer (nil when it declines) and the reference answer. The differential
    /// test asserts they agree wherever the fast path answers, and that the
    /// common shapes are actually answered by it.
    func keywordPathsForTesting(_ raw: String) -> (fast: Bool?, reference: Bool) {
        guard !hostTokens.isEmpty || !hostSubstrings.isEmpty else { return (false, false) }
        let host = Self.canonicalHost(raw)
        return (hostMatchesTermASCII(host), hostMatchesTermUnicode(host))
    }
#endif

    /// A term hit (true) or no decision (nil). A `never_keyword` is not a hit:
    /// its bytes are remembered so the caller can perform the haystack rewrite.
    @inline(__always)
    private func termVerdict(_ bytes: UnsafeBufferPointer<UInt8>,
                             innocent: inout [[UInt8]]) -> Bool? {
        let hash = Self.hash(bytes: bytes)
        if neverHashes.contains(hash) {
            let known = innocent.contains { pattern in
                pattern.count == bytes.count && Self.matches(bytes, at: 0, pattern)
            }
            if !known { innocent.append(Array(bytes)) }
            return nil
        }
        return hostTokenHashes.contains(hash) ? true : nil
    }

    @inline(__always)
    private func hasLongRun(_ buffer: UnsafeBufferPointer<UInt8>,
                            start: Int, end: Int) -> Bool {
        guard end - start >= 3 else { return false }
        var run = 1
        var i = start + 1
        while i < end {
            if buffer[i] == buffer[i - 1] {
                run += 1
                if run >= 3 { return true }
            } else {
                run = 1
            }
            i += 1
        }
        return false
    }

    @inline(__always)
    private func containsSubstringTerm(_ buffer: UnsafeBufferPointer<UInt8>) -> Bool {
        guard !hostSubstrings.isEmpty else { return false }
        var i = 0
        while i < buffer.count {
            for pattern in substringBuckets[Int(buffer[i])] {
                if Self.matches(buffer, at: i, pattern) { return true }
            }
            i += 1
        }
        return false
    }

    /// `replacingOccurrences(of: removal, with: "")` for the one-innocent case,
    /// then the substring layer. Removing non-overlapping occurrences from the
    /// left is what Foundation's literal replacement does.
    @inline(__always)
    private func containsSubstringTerm(removingOccurrences removal: [UInt8],
                                       in buffer: UnsafeBufferPointer<UInt8>) -> Bool {
        guard !removal.isEmpty, removal.count <= buffer.count else {
            return containsSubstringTerm(buffer)
        }
        var reduced = [UInt8]()
        reduced.reserveCapacity(buffer.count)
        var i = 0
        while i < buffer.count {
            if i + removal.count <= buffer.count, Self.matches(buffer, at: i, removal) {
                i += removal.count
            } else {
                reduced.append(buffer[i])
                i += 1
            }
        }
        return reduced.withUnsafeBufferPointer { containsSubstringTerm($0) }
    }

    @inline(__always)
    private static func matches(_ buffer: UnsafeBufferPointer<UInt8>,
                                at index: Int, _ pattern: [UInt8]) -> Bool {
        guard pattern.count <= buffer.count - index else { return false }
        var k = 0
        while k < pattern.count {
            if buffer[index + k] != pattern[k] { return false }
            k += 1
        }
        return true
    }

    @inline(__always)
    private static func contains(_ buffer: UnsafeBufferPointer<UInt8>,
                                 _ needle: [UInt8]) -> Bool {
        guard !needle.isEmpty, needle.count <= buffer.count else { return false }
        var i = 0
        while i + needle.count <= buffer.count {
            if matches(buffer, at: i, needle) { return true }
            i += 1
        }
        return false
    }

    /// Install the keyword layer.
    public func setHostTerms(tokens: [String],
                             substrings: [String],
                             neverKeyword: [String],
                             exemptHosts: [String] = []) {
        let t = Set(tokens.map { TextNormalizer.normalize($0) }.filter { !$0.isEmpty })
        let sub = substrings.map { TextNormalizer.normalize($0) }.filter { !$0.isEmpty }
        let never = Set(neverKeyword.map { TextNormalizer.normalize($0) }
            .filter { !$0.isEmpty })
        let exempt = Set(exemptHosts.map { $0.lowercased() })

        // The forms the ASCII path reads. Built before the barrier so the
        // install itself stays a pointer swap.
        let tokenHashes = Set(t.map { Self.hash(Substring($0)) })
        let neverHashes = Set(never.map { Self.hash(Substring($0)) })
        let exemptHashes = Set(exempt.map { Self.hash(Substring($0)) })
        var buckets: [[[UInt8]]] = Array(repeating: [], count: 256)
        for pattern in sub {
            let bytes = Array(pattern.utf8)
            guard let first = bytes.first else { continue }
            buckets[Int(first)].append(bytes)
        }

        queue.sync(flags: .barrier) {
            self.hostTokens = t
            self.hostTokenHashes = tokenHashes
            self.hostSubstrings = sub
            self.substringBuckets = buckets
            self.neverKeyword = never
            self.neverHashes = neverHashes
            self.keywordExemptHosts = exempt
            self.exemptHashes = exemptHashes
        }
    }

    /// Verify and install `terms.json`.
    ///
    /// The hash is checked against the value recorded in the SIGNED manifest,
    /// so a term list is trusted on exactly the same evidence as a domain list.
    /// Being bundled buys it nothing — the seed goes through this path too.
    public func loadHostTerms(termsData: Data, expectedSHA256: String) throws {
        let actual = Self.sha256Hex(termsData)
        guard actual == expectedSHA256 else {
            throw LoadError.hashMismatch("terms.json")
        }

        struct Payload: Decodable {
            struct HostTerm: Decodable { let t: String; let kind: String }
            let host_terms: [HostTerm]
            let never_keyword: [String]
            let keyword_exempt_hosts: [String]?
        }
        guard let payload = try? JSONDecoder().decode(Payload.self, from: termsData)
        else { throw LoadError.malformed("terms.json") }

        setHostTerms(
            tokens: payload.host_terms.filter { $0.kind == "token" }.map(\.t),
            substrings: payload.host_terms.filter { $0.kind == "substring" }.map(\.t),
            neverKeyword: payload.never_keyword,
            exemptHosts: payload.keyword_exempt_hosts ?? [])

        NSLog("[Hisn] keyword layer installed: %d terms, %d guards",
              payload.host_terms.count, payload.never_keyword.count)
    }

    /// Strict mode inverts the question: everything is blocked unless allowed.
    public func isAllowedInStrictMode(host: String) -> Bool {
        queue.sync { allowedInStrictMode(Self.canonicalHost(host)) }
    }

    /// `host` must already be canonicalised; callers must hold `queue`.
    private func allowedInStrictMode(_ host: String) -> Bool {
        if WebProtectionPolicy.isBlockedCanonical(host) { return false }
        return walk(host) { h in
            if customBlocks.contains(h) { return false }
            if allowlist.contains(h) { return true }
            return nil
        } ?? false
    }

    /// Install all authority-controlled flow rules in one transaction. A flow
    /// sees the old policy or the new one, never a mixture of both.
    public func setNetworkPolicy(strict: Bool, allowlist: [String],
                                 customBlocks: [String], blockedApps: [String]) {
        let allowed = Set(allowlist.map { Self.hash(Substring(Self.canonicalHost($0))) })
        let blocked = Set(customBlocks.map { Self.hash(Substring(Self.canonicalHost($0))) })
        let apps = Set(blockedApps)
        queue.sync(flags: .barrier) {
            self.allowlist = allowed
            self.customBlocks = blocked
            self.networkStrict = strict
            self.networkBlockedApps = apps
        }
    }

    public var networkStrictActive: Bool { queue.sync { networkStrict } }

    /// Identification is lazy because it involves Security framework work.
    /// The callback must not call back into this store.
    ///
    /// A deny verdict also feeds the aggregate tally — the only "what
    /// happened" this store ever records, and a count, never a host. Allowed
    /// flows, the overwhelming majority, take the same path as before.
    public func shouldBlockFlow(host: String?, sourceAppIdentifier: () -> String?) -> Bool {
        queue.sync {
            let verdict: Bool
            if !networkBlockedApps.isEmpty, let app = sourceAppIdentifier(),
               networkBlockedApps.contains(app) {
                verdict = true
            } else if let host, !host.isEmpty {
                // Canonicalise once here; the verdict functions assume it.
                let host = Self.canonicalHost(host)
                verdict = networkStrict ? !allowedInStrictMode(host) : blocked(host)
            } else {
                verdict = networkStrict
            }
            if verdict { noteDeniedFlow(Date()) }
            return verdict
        }
    }

    public func setAllowlist(_ domains: [String]) {
        let set = Set(domains.map { Self.hash(Substring(Self.canonicalHost($0))) })
        queue.sync(flags: .barrier) { self.allowlist = set }
    }

    /// Domains the person added by hand, on top of the published list.
    public func setCustomBlocks(_ domains: [String]) {
        let set = Set(domains.map { Self.hash(Substring(Self.canonicalHost($0))) })
        queue.sync(flags: .barrier) { self.customBlocks = set }
    }

    // MARK: - Loading

    /// Check `manifestData` against this store's key and decode it.
    ///
    /// The one place a manifest earns trust. `load` uses it for the domain
    /// list, and the filter uses it before reading the keyword layer's hash
    /// out of the bundled manifest — which it once did WITHOUT this step,
    /// trusting the hash of a file it had not verified. A code-signed bundle
    /// makes that hard to exploit, but the domain path never relied on that
    /// argument and the keyword path should not either.
    public func verifiedManifest(manifestData: Data,
                                 signatureHex: String) throws -> Manifest {
        guard let keyBytes = Data(hexString: publicKeyHex),
              let key = try? Curve25519.Signing.PublicKey(rawRepresentation: keyBytes)
        else { throw LoadError.malformed("public key") }

        guard let sig = Data(hexString: signatureHex.trimmingCharacters(
            in: .whitespacesAndNewlines))
        else { throw LoadError.malformed("signature hex") }

        guard key.isValidSignature(sig, for: manifestData) else {
            throw LoadError.badSignature
        }
        return try JSONDecoder().decode(Manifest.self, from: manifestData)
    }

    /// Verify and install a downloaded list.
    ///
    /// - Parameters:
    ///   - manifestData: raw bytes of `manifest.json`, exactly as served. Do
    ///     not re-encode: the signature covers these bytes.
    ///   - signatureHex: contents of `manifest.json.sig`.
    ///   - domainsData: raw bytes of the domain-list artifact.
    ///   - artifact: which artifact `domainsData` is, so its hash is checked
    ///     against the right entry. The published list is `domains.packed`; the
    ///     seed bundled with the extension is the core tier, `domains_core.txt`,
    ///     covered by the same signed manifest.
    public func load(manifestData: Data,
                     signatureHex: String,
                     domainsData: Data,
                     artifact: String = "domains.packed") throws {

        let manifest = try verifiedManifest(manifestData: manifestData,
                                            signatureHex: signatureHex)

        // Rollback protection. A validly signed *old* manifest is still an
        // attack: it unblocks everything added since. `versionFloor` carries
        // the memory across restarts — see its comment.
        let held = max(version, versionFloor)
        guard manifest.version >= held else {
            throw LoadError.rollback(offered: manifest.version, held: held)
        }

        // A signed list can still be a broken list. Refuse a build that lost
        // most of its coverage — keeping yesterday's list is strictly safer.
        guard manifest.domain_count > 100_000 else {
            throw LoadError.tooSmall(manifest.domain_count)
        }

        guard let expected = manifest.artifacts[artifact]?.sha256 else {
            throw LoadError.malformed("\(artifact) missing from manifest")
        }
        let actual = SHA256.hash(data: domainsData)
            .map { String(format: "%02x", $0) }.joined()
        guard actual == expected else {
            throw LoadError.hashMismatch(artifact)
        }

        // Parse straight from the buffer — no intermediate [String].
        //
        // The final line matters: the builder writes `domains.packed` with
        // "\n".join(...), so the last domain has no trailing newline. A parser
        // that only flushes on 0x0A drops it, and the highest-sorting domain in
        // the list silently stops being blocked. Flush the tail explicitly
        // rather than assuming a terminator the format does not promise.
        //
        // The capacity hint is the artifact's own count, not the manifest's
        // full-list count: the bundled seed is the ~236k core tier inside a
        // manifest that advertises 5.28M, and `minimumCapacity` sized the set
        // for the full list — tens of megabytes allocated on every first
        // launch for a quarter of the entries. Empty lines insert nothing and
        // a real domain line is longer than one byte, so capping the hint
        // against the artifact size costs nothing and keeps a corrupt count
        // from asking for an enormous allocation.
        let claimed = artifact == "domains_core.txt" && manifest.core_domain_count > 0
            ? manifest.core_domain_count
            : manifest.domain_count
        let capacityHint = min(claimed, domainsData.count / 2 + 1)
        var newHashes = Set<UInt64>(minimumCapacity: capacityHint)
        domainsData.withUnsafeBytes { (raw: UnsafeRawBufferPointer) in
            guard let base = raw.baseAddress?.assumingMemoryBound(to: UInt8.self)
            else { return }
            let count = raw.count

            func insert(_ start: Int, _ end: Int) {
                guard end > start else { return }
                // The `.txt` artifacts carry a two-line generated header;
                // `domains.packed` does not. Skipping comments lets both be
                // parsed by the same loop.
                guard base[start] != 0x23 else { return }   // '#'
                var h: UInt64 = 0xcbf2_9ce4_8422_2325
                var i = start
                while i < end {
                    h ^= UInt64(base[i])
                    h &*= 0x0000_0100_0000_01B3
                    i += 1
                }
                newHashes.insert(h)
            }

            // memchr, not a Swift byte loop: `for i in 0..<raw.count where ...`
            // bounds-checks and iterates every byte of a 135MB artifact, which
            // is most of the filter's start-up time on a full list.
            var start = 0
            while start < count {
                guard let newline = memchr(base + start, 0x0A, count - start) else {
                    insert(start, count)
                    return
                }
                let end = Int(bitPattern: newline) - Int(bitPattern: base)
                insert(start, end)
                start = end + 1
            }
        }

        // Synchronous on purpose. An async barrier lets `load` return before the
        // list is installed, so a caller that reads `version` on the next line —
        // ListUpdater does exactly that when recording `listVersion` — can
        // record the previous version, and a lookup made in between answers
        // against the old list.
        queue.sync(flags: .barrier) {
            self.hashes = newHashes
            self._version = manifest.version
            // What was actually installed, not what the manifest advertises.
            // Loading the core-tier seed against a full-list manifest would
            // otherwise report 982k domains while holding 148k, and this number
            // exists to be reported honestly.
            self._domainCount = newHashes.count
        }

        NSLog("[Hisn] blocklist v%d installed from %@, %d domains",
              manifest.version, artifact, newHashes.count)
    }
}

extension Data {
    init?(hexString: String) {
        let chars = Array(hexString.utf8)
        guard chars.count % 2 == 0 else { return nil }
        var bytes = [UInt8]()
        bytes.reserveCapacity(chars.count / 2)
        for i in stride(from: 0, to: chars.count, by: 2) {
            guard let hi = Data.nibble(chars[i]),
                  let lo = Data.nibble(chars[i + 1]) else { return nil }
            bytes.append(hi << 4 | lo)
        }
        self.init(bytes)
    }

    private static func nibble(_ c: UInt8) -> UInt8? {
        switch c {
        case 0x30...0x39: return c - 0x30            // 0-9
        case 0x61...0x66: return c - 0x61 + 10       // a-f
        case 0x41...0x46: return c - 0x41 + 10       // A-F
        default: return nil
        }
    }
}
