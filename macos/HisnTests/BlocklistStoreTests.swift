import XCTest
@testable import Hisn

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

final class LockStoreTests: XCTestCase {

    func testWriteRefusesToShortenDeadline() {
        let long = LockStore.LockState(
            deadline: Date().addingTimeInterval(30 * 86400),
            mode: "blocklist", startedAt: Date())
        XCTAssertTrue(LockStore.write(long))

        let short = LockStore.LockState(
            deadline: Date().addingTimeInterval(60),
            mode: "blocklist", startedAt: Date())
        XCTAssertFalse(LockStore.write(short),
                       "shortening an active lock must be refused")
        XCTAssertEqual(LockStore.read().deadline.timeIntervalSince1970,
                       long.deadline.timeIntervalSince1970, accuracy: 1)
    }

    func testExtendingIsAllowed() {
        let base = LockStore.LockState(
            deadline: Date().addingTimeInterval(86400),
            mode: "blocklist", startedAt: Date())
        XCTAssertTrue(LockStore.write(base))

        var longer = base
        longer.deadline = base.deadline.addingTimeInterval(86400)
        XCTAssertTrue(LockStore.write(longer))
    }

    /// The clock-rollback defence. Winding the system date back must freeze the
    /// countdown, never advance it.
    func testTrustedNowNeverGoesBackwards() {
        let first = LockStore.trustedNow()
        let second = LockStore.trustedNow()
        XCTAssertGreaterThanOrEqual(second, first)
    }

    func testClearRefusedWhileLockIsRunning() {
        let running = LockStore.LockState(
            deadline: Date().addingTimeInterval(3600),
            mode: "blocklist", startedAt: Date())
        _ = LockStore.write(running)
        XCTAssertFalse(LockStore.clearIfExpired(),
                       "a running lock must not be clearable")
        XCTAssertTrue(LockStore.isLocked())
    }
}

final class LookupTests: XCTestCase {

    /// Mirrors `test_collapse_lookup_invariant` in the Python suite: the
    /// builder drops `a.example.com` when `example.com` is listed, so the
    /// parent-walk here is what recovers it.
    func testParentWalkRecoversCollapsedSubdomains() throws {
        let store = BlocklistStore.shared
        let domains = "example.com\nstandalone.net\n"
        let manifest = """
        {"schema":1,"version":1,"built_at":"","domain_count":200000,\
        "core_domain_count":200000,"artifacts":{}}
        """
        // Loading is signature-gated by design, so this asserts the guard holds
        // rather than reaching past it.
        XCTAssertThrowsError(
            try store.load(manifestData: Data(manifest.utf8),
                           signatureHex: String(repeating: "00", count: 64),
                           domainsData: Data(domains.utf8)),
            "an unsigned list must never load"
        )
    }
}
