import XCTest
@testable import Hisn

/// The escape hatch.
///
/// `docs/THREAT_MODEL.md` Part 4 argues that a lock with genuinely no exit is
/// not the safest design — it is the one people refuse to install, uninstall
/// pre-emptively, or route around by buying a second device, and it is
/// dangerous when someone needs the web for something real. What works is an
/// exit that is **slow and social rather than instant and private**.
///
/// Before these tests existed the mechanism was inoperable in two independent
/// ways at once, and each would have been sufficient on its own:
///
///   1. `requestSelfRelease()` wrote a date to a preferences key that NOTHING
///      ever read back. The lock timer only compared against the original
///      deadline, so a matured request did nothing at all.
///   2. No screen called it, so the request could not be created. The UI
///      offered only to *cancel* a request that could never exist.
///
/// And fixing (1) naively would have introduced a third, worse problem: the key
/// lived in plain `UserDefaults`, where one `defaults write` sets it to any
/// value — turning a 48-hour delay into an instant unlock. That is what
/// `testForgedPastDateStillWaits` exists to prevent.
final class SelfReleaseTests: XCTestCase {

    private var scratch: URL!
    private var namespace: String!

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

    private func locked(days: Double = 30, release: Date? = nil) -> LockStore.LockState {
        LockStore.LockState(deadline: Date().addingTimeInterval(days * 86400),
                            mode: "blocklist",
                            startedAt: Date(),
                            selfReleaseAt: release)
    }

    // MARK: - The mechanism works at all

    /// Prevents the original bug: a recorded release that nothing acts on.
    ///
    /// The lock must actually end once the request matures. This is the whole
    /// feature, and it was silently absent.
    func testAMaturedReleaseEndsTheLock() {
        let matured = Date().addingTimeInterval(-60)          // 1 min ago
        XCTAssertTrue(LockStore.write(locked(release: matured)))

        // Backdate the first-seen record so the delay has genuinely elapsed,
        // which is what would happen naturally after 48 hours.
        UserDefaults(suiteName: namespace)?.set(
            Date().addingTimeInterval(-LockStore.selfReleaseDelay - 120),
            forKey: "selfReleaseFirstSeen.\(Int(matured.timeIntervalSince1970))")

        XCTAssertFalse(LockStore.isLocked(),
                       "a matured self-release did not end the lock")
        XCTAssertTrue(LockStore.clearIfExpired(),
                      "a matured self-release must be clearable")
    }

    /// Prevents the request ending the lock immediately.
    ///
    /// The delay IS the mechanism — an exit available in the moment, in
    /// silence, is not an exit this product can offer.
    func testAFreshRequestDoesNotEndTheLockYet() {
        let at = Date().addingTimeInterval(LockStore.selfReleaseDelay)
        XCTAssertTrue(LockStore.write(locked(release: at)))
        XCTAssertTrue(LockStore.isLocked())
        XCTAssertFalse(LockStore.clearIfExpired())
    }

    // MARK: - Tamper resistance

    /// THE load-bearing test.
    ///
    /// Prevents the bypass that a naive fix would have created. The release
    /// date is local state and local state is editable — it previously sat in
    /// plain preferences where `defaults write group.app.hisn selfReleaseAt`
    /// set it to anything. Honouring the date directly would make the 48-hour
    /// wait a one-line unlock.
    ///
    /// The defence mirrors the clock-rollback defence: the wait runs from when
    /// we FIRST SAW the date, not from what the date claims. A date forged into
    /// the distant past therefore still waits the full period.
    func testForgedPastDateStillWaits() {
        let forged = Date(timeIntervalSince1970: 0)           // 1970
        XCTAssertTrue(LockStore.write(locked(release: forged)))

        XCTAssertTrue(LockStore.isLocked(),
                      "a release date forged into the past unlocked immediately")

        let effective = LockStore.effectiveDeadline(LockStore.read())
        XCTAssertGreaterThan(effective.timeIntervalSinceNow,
                             LockStore.selfReleaseDelay - 60,
                             "the waiting period did not start when we first saw it")
    }

    /// Prevents accelerating a pending request.
    ///
    /// "Requestable at any time, cannot be accelerated, can be cancelled" —
    /// the middle clause is the one an impatient person at 2am attacks.
    func testCannotPullAReleaseEarlier() {
        let at = Date().addingTimeInterval(LockStore.selfReleaseDelay)
        XCTAssertTrue(LockStore.write(locked(release: at)))

        let sooner = Date().addingTimeInterval(60)
        XCTAssertFalse(LockStore.write(locked(release: sooner)),
                       "a pending release must not be moveable earlier")
        XCTAssertEqual(LockStore.read().selfReleaseAt?.timeIntervalSince1970 ?? 0,
                       at.timeIntervalSince1970, accuracy: 1)
    }

    /// Cancelling makes the lock longer, so it is always permitted — the same
    /// asymmetry every other guard in this codebase enforces.
    func testCancellingIsAlwaysAllowed() {
        let at = Date().addingTimeInterval(LockStore.selfReleaseDelay)
        XCTAssertTrue(LockStore.write(locked(release: at)))

        var cancelled = LockStore.read()
        cancelled.selfReleaseAt = nil
        XCTAssertTrue(LockStore.write(cancelled))
        XCTAssertNil(LockStore.read().selfReleaseAt)
        XCTAssertTrue(LockStore.isLocked())
    }

    /// Prevents clearing one store to erase a pending release's maturity.
    ///
    /// Resolution takes the LATEST release any store reports, so a store that
    /// reports none cannot cancel one another store still holds. Cancelling has
    /// to go through `write`, which updates all three at once.
    func testDeletingOneStoreDoesNotCancelTheRequest() {
        let at = Date().addingTimeInterval(LockStore.selfReleaseDelay)
        XCTAssertTrue(LockStore.write(locked(release: at)))

        UserDefaults().removePersistentDomain(forName: namespace)

        XCTAssertNotNil(LockStore.read().selfReleaseAt,
                        "wiping one store erased the pending release")
    }

    /// Prevents a release request from EXTENDING a lock.
    ///
    /// A request may only bring the end forward. If the deadline arrives first,
    /// the lock is over regardless of what any pending request says.
    func testReleaseNeverOutlastsTheDeadline() {
        var state = locked(days: 0.5)
        state.selfReleaseAt = Date().addingTimeInterval(90 * 86400)
        XCTAssertTrue(LockStore.write(state))

        XCTAssertEqual(LockStore.effectiveDeadline(LockStore.read()),
                       LockStore.read().deadline,
                       "a pending release pushed the lock past its own deadline")
    }

    /// Prevents a lock recorded by an older build being lost on upgrade.
    ///
    /// `selfReleaseAt` is a new field; a state encoded without it must still
    /// decode, with `nil` — the safe reading of "no request pending".
    func testStateWithoutAReleaseFieldStillDecodes() throws {
        let legacy = Data("""
        {"deadline":760000000,"mode":"strict","startedAt":750000000}
        """.utf8)
        let decoded = try JSONDecoder().decode(LockStore.LockState.self, from: legacy)
        XCTAssertNil(decoded.selfReleaseAt)
        XCTAssertEqual(decoded.mode, "strict")
    }
}
