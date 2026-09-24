import Foundation
import CryptoKit

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

    /// The keyword layer. Kept as strings rather than hashes: `substrings`
    /// needs `contains`, which a hash cannot answer, and both sets are small
    /// enough (a few hundred entries) that the memory argument driving the
    /// domain hashing does not apply.
    private var hostTokens: Set<String> = []
    private var hostSubstrings: [String] = []
    private var neverKeyword: Set<String> = []
    private var _version: Int = 0
    private var _domainCount: Int = 0

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

    @inline(__always)
    static func hash(_ domain: Substring) -> UInt64 {
        var h: UInt64 = 0xcbf2_9ce4_8422_2325
        for byte in domain.utf8 {
            h ^= UInt64(byte)
            h &*= 0x0000_0100_0000_01B3
        }
        return h
    }

    /// Walk `host` and each of its parent domains, stopping at the first
    /// decision `decide` returns. Returning nil means "keep walking up".
    ///
    /// Walking is what lets the builder collapse subdomains: a list entry of
    /// `example.com` covers `cdn.videos.example.com` without the child being
    /// listed. Every lookup shares this loop so the block, allow and strict
    /// questions cannot drift apart in how far they walk.
    ///
    /// Callers must already hold `queue`.
    @inline(__always)
    private func walk(_ host: String, _ decide: (UInt64) -> Bool?) -> Bool {
        // Strip a trailing dot before matching. `pornhub.com.` is a valid
        // absolute FQDN, browsers accept it, and it resolves to exactly the
        // same site as `pornhub.com` — but it hashes differently, so without
        // this every blocked domain has a one-keystroke bypass. Leading dots
        // and surrounding whitespace are trimmed on the same grounds.
        var candidate = Substring(
            host.lowercased().trimmingCharacters(in: Self.hostTrimSet))
        while true {
            if let decision = decide(Self.hash(candidate)) { return decision }
            guard let dot = candidate.firstIndex(of: ".") else { return false }
            candidate = candidate[candidate.index(after: dot)...]
            // A bare TLD is never a useful match.
            if !candidate.contains(".") { return false }
        }
    }

    /// True if `host` or any of its parent domains is blocked.
    ///
    /// The published list is consulted first and the keyword layer only when it
    /// misses, so a domain that is explicitly allowed can never be re-blocked
    /// by a keyword that happens to appear in its name.
    public func isBlocked(host: String) -> Bool {
        if queue.sync(execute: { allowedByHand(host) }) { return false }
        if listSaysBlocked(host: host) { return true }
        return queue.sync { hostMatchesTerm(host) }
    }

    /// A hand-added allowance, checked before anything else.
    private func allowedByHand(_ host: String) -> Bool {
        walk(host) { h in
            if customBlocks.contains(h) { return false }
            if allowlist.contains(h) { return true }
            return nil
        }
    }

    private func listSaysBlocked(host: String) -> Bool {
        queue.sync {
            walk(host) { h in
                // An explicit "always block this" is a statement about one
                // named domain, so it outranks a broad allowance. Contradicting
                // yourself resolves to the safer answer.
                if customBlocks.contains(h) { return true }
                if allowlist.contains(h) { return false }
                if hashes.contains(h) { return true }
                return nil
            }
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

        let cleaned = TextNormalizer.normalize(
            Punycode.decodeHost(
                host.trimmingCharacters(in: Self.hostTrimSet)))
        guard !cleaned.isEmpty else { return false }

        var tokens: Set<String> = []
        for token in TextNormalizer.tokenize(cleaned) {
            tokens.formUnion(TextNormalizer.variants(of: token))
        }

        let innocent = tokens.intersection(neverKeyword)
        if !tokens.subtracting(neverKeyword).intersection(hostTokens).isEmpty {
            return true
        }

        var haystack = cleaned
        for word in innocent {
            haystack = haystack.replacingOccurrences(of: word, with: "")
        }
        return hostSubstrings.contains { haystack.contains($0) }
    }

    /// Install the keyword layer.
    public func setHostTerms(tokens: [String],
                             substrings: [String],
                             neverKeyword: [String]) {
        let t = Set(tokens.map { TextNormalizer.normalize($0) }.filter { !$0.isEmpty })
        let sub = substrings.map { TextNormalizer.normalize($0) }.filter { !$0.isEmpty }
        let never = Set(neverKeyword.map { TextNormalizer.normalize($0) }
            .filter { !$0.isEmpty })
        queue.sync(flags: .barrier) {
            self.hostTokens = t
            self.hostSubstrings = sub
            self.neverKeyword = never
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
        }
        guard let payload = try? JSONDecoder().decode(Payload.self, from: termsData)
        else { throw LoadError.malformed("terms.json") }

        setHostTerms(
            tokens: payload.host_terms.filter { $0.kind == "token" }.map(\.t),
            substrings: payload.host_terms.filter { $0.kind == "substring" }.map(\.t),
            neverKeyword: payload.never_keyword)

        NSLog("[Hisn] keyword layer installed: %d terms, %d guards",
              payload.host_terms.count, payload.never_keyword.count)
    }

    /// Strict mode inverts the question: everything is blocked unless allowed.
    public func isAllowedInStrictMode(host: String) -> Bool {
        queue.sync {
            walk(host) { h in
                if customBlocks.contains(h) { return false }
                if allowlist.contains(h) { return true }
                return nil
            }
        }
    }

    public func setAllowlist(_ domains: [String]) {
        let set = Set(domains.map { Self.hash(Substring($0.lowercased())) })
        queue.sync(flags: .barrier) { self.allowlist = set }
    }

    /// Domains the person added by hand, on top of the published list.
    public func setCustomBlocks(_ domains: [String]) {
        let set = Set(domains.map { Self.hash(Substring($0.lowercased())) })
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
        var newHashes = Set<UInt64>(minimumCapacity: manifest.domain_count)
        domainsData.withUnsafeBytes { (raw: UnsafeRawBufferPointer) in
            func insert(_ range: Range<Int>) {
                guard !range.isEmpty else { return }
                // The `.txt` artifacts carry a two-line generated header;
                // `domains.packed` does not. Skipping comments lets both be
                // parsed by the same loop.
                guard raw[range.lowerBound] != 0x23 else { return }   // '#'
                var h: UInt64 = 0xcbf2_9ce4_8422_2325
                for byte in UnsafeRawBufferPointer(rebasing: raw[range]) {
                    h ^= UInt64(byte)
                    h &*= 0x0000_0100_0000_01B3
                }
                newHashes.insert(h)
            }

            var start = 0
            for i in 0..<raw.count where raw[i] == 0x0A {
                insert(start..<i)
                start = i + 1
            }
            insert(start..<raw.count)
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
