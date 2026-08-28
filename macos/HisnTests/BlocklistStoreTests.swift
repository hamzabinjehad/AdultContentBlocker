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

    /// A hand-added block applies on top of the published list, and covers
    /// subdomains through the same parent walk.
    func testCustomBlocksAreEnforced() throws {
        let store = try loaded(["example.com"])
        store.setCustomBlocks(["reddit.com"])

        XCTAssertTrue(store.isBlocked(host: "reddit.com"))
        XCTAssertTrue(store.isBlocked(host: "old.reddit.com"))
        XCTAssertFalse(store.isBlocked(host: "unrelated.org"))
    }

    /// Naming a domain explicitly is a more specific statement than a broad
    /// allowance, so contradicting yourself resolves to blocking.
    func testCustomBlockBeatsTheAllowlist() throws {
        let store = try loaded([])
        store.setAllowlist(["example.com"])
        store.setCustomBlocks(["example.com"])

        XCTAssertTrue(store.isBlocked(host: "example.com"))
        XCTAssertFalse(store.isAllowedInStrictMode(host: "example.com"),
                       "strict mode must not readmit a domain blocked by hand")
    }
}

// MARK: - Native messaging registration

/// The reason the blocking system did not block anything: nothing ever wrote
/// the file that tells Chrome/Edge this native host exists. Without it,
/// `background.js`'s `pollNative()` fails on every attempt, forever, and the
/// extension never learns a lock is running — see `NativeMessagingInstaller`.
final class NativeMessagingInstallerTests: XCTestCase {

    func testManifestShapeIsWhatChromeExpects() {
        let manifest = NativeMessagingInstaller.hostManifest(bridgePath: "/tmp/HisnBridge")
        XCTAssertEqual(manifest["name"] as? String, "app.hisn.bridge")
        XCTAssertEqual(manifest["type"] as? String, "stdio",
                       "Chrome native messaging only speaks stdio")
        XCTAssertEqual(manifest["path"] as? String, "/tmp/HisnBridge")
        XCTAssertEqual(manifest["allowed_origins"] as? [String],
                       ["chrome-extension://hfhaffbmoeepcdolgejeidkgaoapcjig/"])
    }

    /// THE load-bearing test in this file. The extension's Chrome ID is derived
    /// from `extension/manifest.json`'s `"key"`; this Swift constant is typed
    /// in by hand from that same derivation. If the two ever disagree, nothing
    /// on either side raises an error — Chrome just refuses to deliver the
    /// native message, and `background.js` logs "native host unreachable"
    /// forever. Recomputes the ID from the manifest's actual key rather than
    /// comparing two hand-typed strings, which would only ever catch a typo in
    /// one specific place and miss every other way this can drift.
    func testExtensionIDMatchesTheManifestKey() throws {
        let thisFile = URL(fileURLWithPath: #filePath)
        let repoRoot = thisFile
            .deletingLastPathComponent()   // HisnTests
            .deletingLastPathComponent()   // macos
            .deletingLastPathComponent()   // repo root
        let manifestPath = repoRoot.appendingPathComponent("extension/manifest.json")

        let data = try Data(contentsOf: manifestPath)
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        guard let keyBase64 = json?["key"] as? String,
              let der = Data(base64Encoded: keyBase64) else {
            return XCTFail("extension/manifest.json has no usable \"key\"")
        }

        let digest = SHA256.hash(data: der)
        let mapping = Array("abcdefghijklmnop")
        let id = String(digest.prefix(16).flatMap {
            [mapping[Int($0 >> 4)], mapping[Int($0 & 0xF)]]
        })

        XCTAssertEqual(id, NativeMessagingInstaller.extensionID,
            "extension/manifest.json's key no longer derives the pinned "
            + "extension ID — native messaging will silently stop working")
    }

    /// THE regression test for the actual bug: an earlier `generate_xcodeproj.py`
    /// embedded `HisnBridge` with a copy-files destination that built cleanly
    /// and copied nothing, so this returned nil in production too, silently —
    /// no crash, no error, the browser extension just never heard from the
    /// app. `HisnTests` runs hosted inside the built `Hisn.app` (`TEST_HOST`),
    /// so `Bundle.main` here is the real app bundle and this exercises the
    /// exact path production uses, rather than asserting something true only
    /// of the test runner's own bundle.
    func testFindsTheRealEmbeddedBridge() {
        guard let path = NativeMessagingInstaller.bridgeExecutablePath() else {
            return XCTFail("HisnBridge was not found embedded in this test's "
                + "own app host — the Embed Bridge copy phase is not doing "
                + "what NativeMessagingInstaller expects")
        }
        XCTAssertTrue(path.hasSuffix("/Contents/MacOS/HisnBridge"))
        XCTAssertTrue(FileManager.default.isExecutableFile(atPath: path))
    }

    /// The two installers must never disagree about where the hardened,
    /// admin-only manifest lives — `hasSystemManifest` checks exactly that
    /// path to decide whether to defer, so a drift here silently reintroduces
    /// the bug this file exists to close: a user-writable copy sitting next
    /// to (or in place of) the admin-owned one, undoing the one property
    /// `install_native_host.sh` exists for.
    func testSystemPathsMatchTheShellInstaller() throws {
        let thisFile = URL(fileURLWithPath: #filePath)
        let scriptPath = thisFile
            .deletingLastPathComponent()          // HisnTests
            .deletingLastPathComponent()          // macos
            .appendingPathComponent("install_native_host.sh")
        let script = try String(contentsOf: scriptPath, encoding: .utf8)

        for browser in NativeMessagingInstaller.browsers {
            XCTAssertTrue(script.contains(browser.systemDir),
                "install_native_host.sh no longer mentions \(browser.systemDir) "
                + "— confirm it moved intentionally and update both places")
        }
    }

    /// The load-bearing guard: an admin has already run the hardened
    /// installer, so this must not also write a user-scope copy — Chrome
    /// checks user-scope first, so doing so anyway would silently hand back
    /// exactly the protection `install_native_host.sh` exists to provide.
    func testDefersCompletelyWhenASystemManifestExists() {
        let chrome = NativeMessagingInstaller.browsers[0]
        XCTAssertEqual(chrome.name, "Chrome")

        // This machine's real /Library/Google/Chrome/NativeMessagingHosts is
        // not writable by this test (that is the entire point), so the
        // meaningful assertion is behavioural: whatever hasSystemManifest
        // reports for the real path, installIfNeeded's per-browser branch is
        // provably gated on it (see the `if hasSystemManifest(for:) { continue }`
        // in installIfNeeded) — this test pins that the check itself is
        // reading the same path a `sudo install_native_host.sh` run would
        // have written to, not a path that happens to always be empty.
        XCTAssertEqual(chrome.systemDir,
                       "/Library/Google/Chrome/NativeMessagingHosts")
        let exists = FileManager.default.fileExists(
            atPath: "\(chrome.systemDir)/app.hisn.bridge.json")
        XCTAssertEqual(NativeMessagingInstaller.hasSystemManifest(for: chrome),
                       exists)
    }
}

// MARK: - Hand-written site lists

final class SiteListParsingTests: XCTestCase {

    func testNormalizesWhatPeopleActuallyPaste() {
        XCTAssertEqual(SiteLists.normalize("https://www.Example.com/watch?v=1"),
                       "example.com")
        XCTAssertEqual(SiteLists.normalize("  EXAMPLE.com.  "), "example.com")
        XCTAssertEqual(SiteLists.normalize("http://user@example.com:8443/x"),
                       "example.com")
        XCTAssertEqual(SiteLists.normalize("sub.example.co.uk"), "sub.example.co.uk")
    }

    /// `example.com` already covers `www.example.com` through the parent walk,
    /// so both spellings in the list is redundancy that looks like a bug.
    func testStripsWWW() {
        XCTAssertEqual(SiteLists.normalize("www.example.com"), "example.com")
    }

    func testRejectsThingsThatAreNotDomains() {
        for junk in ["", "   ", "localhost", "no-dot", "-lead.com", "trail-.com",
                     "under_score.com", "1.2.3.4", "..", "a..b.com"] {
            XCTAssertNil(SiteLists.normalize(junk), "accepted \(junk)")
        }
    }

    func testParseDeduplicatesSortsAndCountsWhatItDropped() {
        let result = SiteLists.parse("""
        www.example.com
        example.com
        # a comment

        alpha.org
        not a domain
        """)
        XCTAssertEqual(result.domains, ["alpha.org", "example.com"])
        XCTAssertEqual(result.ignored, 1, "only the junk line counts as ignored")
    }
}

final class SiteListGuardTests: XCTestCase {

    private var namespace: String!

    override func setUp() {
        super.setUp()
        namespace = "app.hisn.tests.\(UUID().uuidString)"
        LockStore.appGroup = namespace
    }

    override func tearDown() {
        UserDefaults().removePersistentDomain(forName: namespace)
        super.tearDown()
    }

    func testUnlockedAnythingGoes() throws {
        try SiteLists.save(customBlocks: ["a.com"], allowlist: ["b.com"], locked: false)
        try SiteLists.save(customBlocks: [], allowlist: ["c.com", "d.com"], locked: false)
        XCTAssertEqual(SiteLists.customBlocks(), [])
        XCTAssertEqual(SiteLists.allowlist(), ["c.com", "d.com"])
    }

    func testLockedAllowsTightening() throws {
        try SiteLists.save(customBlocks: ["a.com"], allowlist: ["x.com", "y.com"],
                           locked: false)
        // Add a block, drop an allowance: both tighten.
        try SiteLists.save(customBlocks: ["a.com", "b.com"], allowlist: ["x.com"],
                           locked: true)
        XCTAssertEqual(SiteLists.customBlocks(), ["a.com", "b.com"])
        XCTAssertEqual(SiteLists.allowlist(), ["x.com"])
    }

    func testLockedRefusesRemovingABlock() throws {
        try SiteLists.save(customBlocks: ["a.com", "b.com"], allowlist: [],
                           locked: false)
        XCTAssertThrowsError(
            try SiteLists.save(customBlocks: ["a.com"], allowlist: [], locked: true))
        XCTAssertEqual(SiteLists.customBlocks(), ["a.com", "b.com"],
                       "a refused save must not partially apply")
    }

    func testLockedRefusesAddingAnAllowance() throws {
        try SiteLists.save(customBlocks: [], allowlist: ["x.com"], locked: false)
        XCTAssertThrowsError(
            try SiteLists.save(customBlocks: [], allowlist: ["x.com", "new.com"],
                               locked: true))
        XCTAssertEqual(SiteLists.allowlist(), ["x.com"])
    }

    /// The case a length comparison waves through. In strict mode the allowlist
    /// is the only thing reachable, so swapping one entry for another is not a
    /// small loosening — it is a complete change of what the lock permits.
    func testLockedRefusesSwappingAnAllowanceOfTheSameLength() throws {
        try SiteLists.save(customBlocks: [], allowlist: ["work.example"],
                           locked: false)
        XCTAssertThrowsError(
            try SiteLists.save(customBlocks: [], allowlist: ["anything.example"],
                               locked: true),
            "same count, entirely different permissions"
        )
        XCTAssertEqual(SiteLists.allowlist(), ["work.example"])
    }
}

// MARK: - Typed lock lengths

/// A typed duration is the one number in this app a person can get wrong in a
/// way they cannot take back, so the range check gets its own tests.
final class LockDurationTests: XCTestCase {

    func testAcceptsALengthInsideTheRange() {
        XCTAssertEqual(LockManager.validated(seconds: 7 * 86400), 7 * 86400)
        XCTAssertEqual(LockManager.validated(seconds: LockManager.minimumLock),
                       LockManager.minimumLock)
        XCTAssertEqual(LockManager.validated(seconds: LockManager.maximumLock),
                       LockManager.maximumLock)
    }

    /// Zero, blank-parsed-as-zero and negatives would all start a lock that has
    /// already expired.
    func testRejectsNothingAndLessThanNothing() {
        XCTAssertNil(LockManager.validated(seconds: 0))
        XCTAssertNil(LockManager.validated(seconds: -86400))
        XCTAssertNil(LockManager.validated(seconds: 30))
    }

    /// The typo case: 3650 typed where 365 was meant. It must be refused, not
    /// clamped — clamping starts a year-long lock nobody chose.
    func testRejectsAndDoesNotClampTooLong() {
        XCTAssertNil(LockManager.validated(seconds: 3650 * 86400))
        XCTAssertNil(LockManager.validated(seconds: LockManager.maximumLock + 1))
    }

    /// `Double("inf")` and `Double("nan")` both parse, so a text field can hand
    /// either one straight to the validator.
    func testRejectsNonFiniteInput() {
        XCTAssertNil(LockManager.validated(seconds: .infinity))
        XCTAssertNil(LockManager.validated(seconds: .nan))
        XCTAssertNil(LockManager.validated(seconds: Double("1e400") ?? 0))
    }

    /// A typed length is charged like the preset it matches, or "Custom: 8
    /// days" is just a way around the paywall.
    func testTypedLengthsAreGatedLikePresets() {
        XCTAssertFalse(LockManager.requiresSubscription(seconds: 7 * 86400))
        XCTAssertFalse(LockManager.requiresSubscription(seconds: 3600))
        XCTAssertTrue(LockManager.requiresSubscription(seconds: 8 * 86400))
    }

    func testEveryPresetIsInsideTheAllowedRange() {
        for preset in LockManager.Duration.allCases {
            guard let seconds = preset.seconds else {
                XCTAssertEqual(preset, .custom, "only .custom may have no length")
                continue
            }
            XCTAssertNotNil(LockManager.validated(seconds: seconds),
                            "the \(preset.rawValue) preset cannot be started")
        }
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
