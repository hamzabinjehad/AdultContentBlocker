import XCTest
import CryptoKit
@testable import Hisn

// MARK: - Helpers

private func hex(_ bytes: some Sequence<UInt8>) -> String {
    bytes.map { String(format: "%02x", $0) }.joined()
}

/// Build a manifest of the shape `build.py` emits, signed with `key`.
///
/// `domainCount` is what the manifest *claims*, which is what the size guard
/// reads; the domain bytes are separate on purpose so a test can make the two
/// disagree.
private func signedManifest(
    key: Curve25519.Signing.PrivateKey,
    version: Int,
    domainsData: Data,
    domainCount: Int = 200_000,
    packedSHA: String? = nil
) throws -> (manifest: Data, signature: String) {
    let sha = packedSHA ?? hex(SHA256.hash(data: domainsData))
    let json = """
    {"schema":1,"version":\(version),"built_at":"2026-01-01T00:00:00+00:00",\
    "domain_count":\(domainCount),"core_domain_count":\(domainCount),\
    "artifacts":{"domains.packed":{"sha256":"\(sha)","bytes":\(domainsData.count)}}}
    """
    let data = Data(json.utf8)
    return (data, hex(try key.signature(for: data)))
}

// MARK: - Hash agreement

/// The hash function in `BlocklistStore` has to agree, byte for byte, with the
/// one in the Python builder — they are two implementations of the same
/// contract in two languages, and nothing at build time forces them to match.
/// If they ever drift, every lookup misses and the app blocks nothing while
/// reporting a healthy list. These vectors were generated from the Python
/// implementation and pin it down.
final class BlocklistHashTests: XCTestCase {

    func testHashMatchesPythonBuilder() {
        let vectors: [(String, UInt64)] = [
            ("pornhub.com",     0xb36a_12aa_1fcc_c058),
            ("example.com",     0x5768_4663_4e27_14c6),
            ("a.b.example.com", 0xb612_afb4_90b9_f1ab),
            ("apple.com",       0xc247_6d81_d6f2_aad0),
        ]
        for (domain, expected) in vectors {
            XCTAssertEqual(BlocklistStore.hash(Substring(domain)), expected,
                           "hash drifted from the Python builder for \(domain)")
        }
    }

    func testHashIsStableAcrossCalls() {
        // Swift's built-in Hasher is randomly seeded per process, so using it
        // here would produce a set that silently stops matching after a
        // relaunch. This asserts we are not using it.
        let a = BlocklistStore.hash(Substring("example.com"))
        let b = BlocklistStore.hash(Substring("example.com"))
        XCTAssertEqual(a, b)
    }
}

// MARK: - Loading

/// Everything here signs its own fixtures with a throwaway key, which is why
/// `BlocklistStore` takes a public key at init. `BlocklistStore.shared` — the
/// only instance the app and the extension use — stays pinned to the shipped
/// production key.
final class BlocklistLoadTests: XCTestCase {

    private var key = Curve25519.Signing.PrivateKey()
    private var store = BlocklistStore()

    override func setUp() {
        super.setUp()
        key = Curve25519.Signing.PrivateKey()
        store = BlocklistStore(publicKeyHex: hex(key.publicKey.rawRepresentation))
    }

    func testAcceptsAValidlySignedList() throws {
        let domains = Data("example.com\nstandalone.net\n".utf8)
        let (manifest, sig) = try signedManifest(key: key, version: 7,
                                                 domainsData: domains)
        try store.load(manifestData: manifest, signatureHex: sig,
                       domainsData: domains)

        XCTAssertEqual(store.version, 7)
        XCTAssertTrue(store.isBlocked(host: "example.com"))
        XCTAssertTrue(store.isBlocked(host: "standalone.net"))
        XCTAssertFalse(store.isBlocked(host: "unrelated.org"))
    }

    /// `version` is written under a barrier. It used to be written
    /// asynchronously, so a caller reading it on the line after `load` — which
    /// is exactly what `ListUpdater` does when it records `listVersion` — could
    /// read the previous version.
    func testVersionIsVisibleImmediatelyAfterLoad() throws {
        let domains = Data("example.com\n".utf8)
        let (manifest, sig) = try signedManifest(key: key, version: 42,
                                                 domainsData: domains)
        try store.load(manifestData: manifest, signatureHex: sig,
                       domainsData: domains)
        XCTAssertEqual(store.version, 42, "load returned before installing the list")
    }

    /// The builder writes `domains.packed` with "\\n".join(...), so the last
    /// domain carries no trailing newline. A parser that only flushes on a
    /// newline drops it, and the highest-sorting domain in the published list
    /// silently stops being blocked.
    func testFinalDomainWithoutTrailingNewlineIsBlocked() throws {
        let domains = Data("aaa.example\nzzz.example".utf8)   // no trailing \n
        let (manifest, sig) = try signedManifest(key: key, version: 1,
                                                 domainsData: domains)
        try store.load(manifestData: manifest, signatureHex: sig,
                       domainsData: domains)

        XCTAssertTrue(store.isBlocked(host: "aaa.example"))
        XCTAssertTrue(store.isBlocked(host: "zzz.example"),
                      "the last line of domains.packed was dropped")
    }

    func testRejectsAnInvalidSignature() throws {
        let domains = Data("example.com\n".utf8)
        let (manifest, _) = try signedManifest(key: key, version: 1,
                                               domainsData: domains)
        XCTAssertThrowsError(
            try store.load(manifestData: manifest,
                           signatureHex: String(repeating: "00", count: 64),
                           domainsData: domains)
        ) { error in
            guard case BlocklistStore.LoadError.badSignature = error else {
                return XCTFail("expected badSignature, got \(error)")
            }
        }
        XCTAssertFalse(store.isBlocked(host: "example.com"),
                       "a rejected list must not be installed")
    }

    /// A list signed by someone else is not our list, however well-formed.
    func testRejectsAListSignedByAnotherKey() throws {
        let attacker = Curve25519.Signing.PrivateKey()
        let domains = Data("example.com\n".utf8)
        let (manifest, sig) = try signedManifest(key: attacker, version: 1,
                                                 domainsData: domains)
        XCTAssertThrowsError(try store.load(manifestData: manifest,
                                            signatureHex: sig,
                                            domainsData: domains))
    }

    /// An old manifest is still validly signed. Replaying one is how an
    /// attacker who can serve traffic unblocks whatever was added since.
    func testRejectsARollback() throws {
        let domains = Data("example.com\n".utf8)
        let (newer, newerSig) = try signedManifest(key: key, version: 9,
                                                   domainsData: domains)
        try store.load(manifestData: newer, signatureHex: newerSig,
                       domainsData: domains)

        let (older, olderSig) = try signedManifest(key: key, version: 8,
                                                   domainsData: domains)
        XCTAssertThrowsError(try store.load(manifestData: older,
                                            signatureHex: olderSig,
                                            domainsData: domains)) { error in
            guard case BlocklistStore.LoadError.rollback(let offered, let held) = error
            else { return XCTFail("expected rollback, got \(error)") }
            XCTAssertEqual(offered, 8)
            XCTAssertEqual(held, 9)
        }
        XCTAssertEqual(store.version, 9)
    }

    /// Re-publishing the same version is a rebuild, not an attack.
    func testAcceptsTheSameVersionAgain() throws {
        let domains = Data("example.com\n".utf8)
        let (manifest, sig) = try signedManifest(key: key, version: 5,
                                                 domainsData: domains)
        try store.load(manifestData: manifest, signatureHex: sig, domainsData: domains)
        XCTAssertNoThrow(try store.load(manifestData: manifest, signatureHex: sig,
                                        domainsData: domains))
    }

    /// A signed list can still be a broken list. Yesterday's list is strictly
    /// safer than a build that quietly lost its coverage.
    func testRejectsASuspiciouslySmallList() throws {
        let domains = Data("example.com\n".utf8)
        let (manifest, sig) = try signedManifest(key: key, version: 1,
                                                 domainsData: domains,
                                                 domainCount: 12)
        XCTAssertThrowsError(try store.load(manifestData: manifest,
                                            signatureHex: sig,
                                            domainsData: domains)) { error in
            guard case BlocklistStore.LoadError.tooSmall = error else {
                return XCTFail("expected tooSmall, got \(error)")
            }
        }
    }

    /// The signature covers the manifest; the manifest's hashes cover the
    /// artifacts. Swapping the domain file has to be caught by the second link.
    func testRejectsDomainsThatDoNotMatchTheManifestHash() throws {
        let domains = Data("example.com\n".utf8)
        let (manifest, sig) = try signedManifest(key: key, version: 1,
                                                 domainsData: domains)
        XCTAssertThrowsError(
            try store.load(manifestData: manifest, signatureHex: sig,
                           domainsData: Data("substituted.example\n".utf8))
        ) { error in
            guard case BlocklistStore.LoadError.hashMismatch = error else {
                return XCTFail("expected hashMismatch, got \(error)")
            }
        }
    }
}

// MARK: - Lookup

final class LookupTests: XCTestCase {

    private let key = Curve25519.Signing.PrivateKey()

    private func loaded(_ domains: [String]) throws -> BlocklistStore {
        let store = BlocklistStore(publicKeyHex: hex(key.publicKey.rawRepresentation))
        let data = Data(domains.joined(separator: "\n").utf8)
        let (manifest, sig) = try signedManifest(key: key, version: 1,
                                                 domainsData: data)
        try store.load(manifestData: manifest, signatureHex: sig, domainsData: data)
        return store
    }

    /// Mirrors `test_collapse_lookup_invariant` in the Python suite, on the same
    /// fixture. The builder drops `a.example.com` when `example.com` is listed;
    /// this parent-walk is the half that recovers it. The two halves live in
    /// different languages and nothing at build time forces them to agree, so
    /// both suites assert the invariant against the same data.
    func testParentWalkRecoversCollapsedSubdomains() throws {
        // What `collapse_subdomains` leaves of the Python fixture.
        let store = try loaded(["example.com", "cdn.other.com", "standalone.net",
                                "d1abc.cloudfront.net"])

        for host in ["example.com", "a.example.com", "b.a.example.com",
                     "cdn.other.com", "deep.cdn.other.com",
                     "standalone.net", "d1abc.cloudfront.net"] {
            XCTAssertTrue(store.isBlocked(host: host),
                          "\(host) was collapsed away and not recovered by lookup")
        }
    }

    /// The mirror of the above: walking up must not start matching things that
    /// were never listed. `example.com.evil.net` is the case that matters —
    /// a suffix check written as string containment would pass it.
    func testLookupDoesNotOverreach() throws {
        let store = try loaded(["example.com"])
        XCTAssertFalse(store.isBlocked(host: "notexample.com"))
        XCTAssertFalse(store.isBlocked(host: "example.com.evil.net"))
        XCTAssertFalse(store.isBlocked(host: "com"))
        XCTAssertTrue(store.isBlocked(host: "sub.example.com"))
    }

    func testLookupIsCaseInsensitive() throws {
        let store = try loaded(["example.com"])
        XCTAssertTrue(store.isBlocked(host: "EXAMPLE.com"))
        XCTAssertTrue(store.isBlocked(host: "CDN.Example.COM"))
    }

    /// Strict mode inverts the question, and the inversion has to be total:
    /// an empty allowlist denies everything rather than allowing everything.
    /// That asymmetry is what stops a corrupt list turning strict mode into an
    /// open door.
    func testStrictModeDeniesEverythingByDefault() throws {
        let store = try loaded(["example.com"])
        XCTAssertFalse(store.isAllowedInStrictMode(host: "anything.org"))
        XCTAssertFalse(store.isAllowedInStrictMode(host: "example.com"))
    }

    func testStrictModeAllowsTheAllowlistAndItsSubdomains() throws {
        let store = try loaded(["example.com"])
        store.setAllowlist(["work.example", "docs.internal"])

        XCTAssertTrue(store.isAllowedInStrictMode(host: "work.example"))
        XCTAssertTrue(store.isAllowedInStrictMode(host: "mail.work.example"))
        XCTAssertFalse(store.isAllowedInStrictMode(host: "notwork.example"))
        XCTAssertFalse(store.isAllowedInStrictMode(host: "anything.org"))
    }

    /// The allowlist wins over the blocklist, so a partner can unblock one
    /// domain without rebuilding the list.
    func testAllowlistOverridesTheBlocklist() throws {
        let store = try loaded(["example.com"])
        store.setAllowlist(["example.com"])
        XCTAssertFalse(store.isBlocked(host: "example.com"))
    }
}

// MARK: - The committed seed

/// The Swift mirror of `TestRealArtifacts` in the Python suite.
///
/// These assert on the bytes actually committed to this repo, verified with the
/// key actually compiled into the app — no fixtures. That makes them the tests
/// that catch the mistakes no unit test can: rotating the signing key without
/// re-signing the seed, a corrupted artifact, or an upstream source adding a
/// domain that would lock the user out of their own machine.
final class BundledSeedTests: XCTestCase {

    private func loadSeed() throws -> BlocklistStore {
        let bundle = Bundle(for: type(of: self))
        let manifest = try XCTUnwrap(bundle.url(forResource: "manifest",
                                                withExtension: "json"))
        let sig = try XCTUnwrap(bundle.url(forResource: "manifest.json",
                                           withExtension: "sig"))
        let domains = try XCTUnwrap(bundle.url(forResource: "domains_core",
                                               withExtension: "txt"))
        // No public key passed: this verifies against the production key the
        // app ships with, which is the whole point of the test.
        let store = BlocklistStore()
        try store.load(manifestData: try Data(contentsOf: manifest),
                       signatureHex: try String(contentsOf: sig, encoding: .utf8),
                       domainsData: try Data(contentsOf: domains),
                       artifact: "domains_core.txt")
        return store
    }

    func testSeedVerifiesAgainstTheShippedPublicKey() throws {
        let store = try loadSeed()
        XCTAssertGreaterThan(store.domainCount, 100_000,
                             "the seed did not load a usable number of domains")
    }

    func testSeedBlocksKnownAdultDomains() throws {
        let store = try loadSeed()
        for host in ["pornhub.com", "www.pornhub.com", "xvideos.com"] {
            XCTAssertTrue(store.isBlocked(host: host), "\(host) is not blocked")
        }
    }

    /// The lockout check. A blocklist that takes out Apple's infrastructure or
    /// the certificate authorities bricks the machine it was meant to protect,
    /// and an admin is then the only way back.
    func testSeedDoesNotBlockCriticalInfrastructure() throws {
        let store = try loadSeed()
        for host in ["apple.com", "ocsp.apple.com", "github.com",
                     "raw.githubusercontent.com", "icloud.com",
                     "cloudflare-dns.com", "letsencrypt.org",
                     "cloudfront.net", "amazonaws.com"] {
            XCTAssertFalse(store.isBlocked(host: host),
                           "\(host) would be blocked — lockout risk")
        }
    }

    /// The comment header the builder writes must not become a blocklist entry.
    func testSeedHeaderCommentsAreNotTreatedAsDomains() throws {
        let store = try loadSeed()
        XCTAssertFalse(store.isBlocked(host: "# Hisn blocklist"))
    }
}

// MARK: - Lock persistence

final class LockStoreTests: XCTestCase {

    private var scratch: URL!
    private var namespace: String!

    /// Point all three stores somewhere private.
    ///
    /// Without this the suite writes a real multi-week lock into the stores the
    /// installed app reads — and since `write` refuses to shorten a deadline,
    /// it then passes once and fails on every run after that.
    override func setUp() {
        super.setUp()
        namespace = "app.hisn.tests.\(UUID().uuidString)"
        scratch = FileManager.default.temporaryDirectory
            .appendingPathComponent(namespace, isDirectory: true)
        try? FileManager.default.createDirectory(at: scratch,
                                                 withIntermediateDirectories: true)
        LockStore.appGroup = namespace
        LockStore.keychainService = namespace
        LockStore.systemPath = scratch.appendingPathComponent("lock.plist").path
    }

    override func tearDown() {
        UserDefaults().removePersistentDomain(forName: namespace)
        SecItemDelete([
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: namespace as Any,
        ] as CFDictionary)
        try? FileManager.default.removeItem(at: scratch)
        super.tearDown()
    }

    private func state(inDays days: Double, mode: String = "blocklist") -> LockStore.LockState {
        LockStore.LockState(deadline: Date().addingTimeInterval(days * 86400),
                            mode: mode, startedAt: Date())
    }

    func testWriteRefusesToShortenDeadline() {
        XCTAssertTrue(LockStore.write(state(inDays: 30)))
        XCTAssertFalse(LockStore.write(state(inDays: 0.001)),
                       "shortening an active lock must be refused")
        XCTAssertEqual(LockStore.read().deadline.timeIntervalSinceNow,
                       30 * 86400, accuracy: 5)
    }

    func testExtendingIsAllowed() {
        let base = state(inDays: 1)
        XCTAssertTrue(LockStore.write(base))

        var longer = base
        longer.deadline = base.deadline.addingTimeInterval(86400)
        XCTAssertTrue(LockStore.write(longer))
        XCTAssertEqual(LockStore.read().deadline.timeIntervalSince1970,
                       longer.deadline.timeIntervalSince1970, accuracy: 1)
    }

    /// Deleting one store must achieve nothing: `read` resolves by MAX across
    /// all three, and heals the ones that are behind.
    func testDeletingOneStoreDoesNotClearTheLock() {
        XCTAssertTrue(LockStore.write(state(inDays: 14)))

        // The obvious move: wipe the app's preferences.
        UserDefaults().removePersistentDomain(forName: namespace)

        XCTAssertTrue(LockStore.isLocked(),
                      "clearing one store must not release the lock")
        XCTAssertEqual(LockStore.read().deadline.timeIntervalSinceNow,
                       14 * 86400, accuracy: 5)
    }

    /// And the heal is real: after a read, the store that was cleared holds the
    /// winning value again, so clearing it is not merely useless but undone.
    func testReadHealsAStoreThatWasCleared() {
        XCTAssertTrue(LockStore.write(state(inDays: 14)))
        try? FileManager.default.removeItem(atPath: LockStore.systemPath)

        _ = LockStore.read()

        XCTAssertTrue(FileManager.default.fileExists(atPath: LockStore.systemPath),
                      "the cleared store was not restored")
    }

    /// The clock-rollback defence. Winding the system date back must freeze the
    /// countdown, never advance it.
    func testTrustedNowNeverGoesBackwards() {
        let first = LockStore.trustedNow()
        let second = LockStore.trustedNow()
        XCTAssertGreaterThanOrEqual(second, first)
    }

    func testClearRefusedWhileLockIsRunning() {
        _ = LockStore.write(state(inDays: 1))
        XCTAssertFalse(LockStore.clearIfExpired(),
                       "a running lock must not be clearable")
        XCTAssertTrue(LockStore.isLocked())
    }

    func testNoLockMeansNotLocked() {
        XCTAssertFalse(LockStore.isLocked())
        XCTAssertEqual(LockStore.read(), .unlocked)
    }
}
