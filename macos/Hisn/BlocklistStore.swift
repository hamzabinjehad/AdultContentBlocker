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
        "e63cfca8c4fc01412cdaf006c3f654c7be4e8218e81f44f6b4daf47e55a360af"

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
    private var _version: Int = 0
    private var _domainCount: Int = 0

    private let queue = DispatchQueue(label: "app.hisn.blocklist",
                                      attributes: .concurrent)

    /// Read through the same queue that guards the list. The network extension
    /// installs a new list from one thread while the app reads these from
    /// another, so an unsynchronised stored property here is a data race.
    public var version: Int { queue.sync { _version } }
    public var domainCount: Int { queue.sync { _domainCount } }

    public init(publicKeyHex: String = BlocklistStore.productionPublicKeyHex) {
        self.publicKeyHex = publicKeyHex
    }

    // MARK: - Lookup

    /// Stable, seeded 64-bit hash. SipHash via Swift's `Hasher` is not stable
    /// across launches (it is randomly seeded), so we cannot use it here — the
    /// hashes have to mean the same thing in the app and the extension, and
    /// across reboots. FNV-1a is stable, fast, and good enough for set
    /// membership where a collision only ever over-blocks.
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
        var candidate = Substring(host.lowercased())
        while true {
            if let decision = decide(Self.hash(candidate)) { return decision }
            guard let dot = candidate.firstIndex(of: ".") else { return false }
            candidate = candidate[candidate.index(after: dot)...]
            // A bare TLD is never a useful match.
            if !candidate.contains(".") { return false }
        }
    }

    /// True if `host` or any of its parent domains is blocked.
    public func isBlocked(host: String) -> Bool {
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

        guard let keyBytes = Data(hexString: publicKeyHex),
              let key = try? Curve25519.Signing.PublicKey(rawRepresentation: keyBytes)
        else { throw LoadError.malformed("public key") }

        guard let sig = Data(hexString: signatureHex.trimmingCharacters(
            in: .whitespacesAndNewlines))
        else { throw LoadError.malformed("signature hex") }

        guard key.isValidSignature(sig, for: manifestData) else {
            throw LoadError.badSignature
        }

        let manifest = try JSONDecoder().decode(Manifest.self, from: manifestData)

        // Rollback protection. A validly signed *old* manifest is still an
        // attack: it unblocks everything added since.
        let held = version
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
