import XCTest
@testable import Hisn

/// The filter's root-owned authority: the rules it applies, what it keeps on
/// disk, and how its copy and the app's are merged.
///
/// These are the tests that stand between "a standard user can end a lock by
/// deleting three files" and "cannot" — every refusal here is a way out that
/// stays shut.
final class PolicyAuthorityTests: XCTestCase {

    private let t0 = Date(timeIntervalSince1970: 1_800_000_000)
    private let day: TimeInterval = 86_400
    private typealias A = PolicyAuthority

    private func lock(_ days: Double, mode: String = "blocklist",
                      release: Date? = nil) -> LockStore.LockState {
        LockStore.LockState(deadline: t0.addingTimeInterval(days * day), mode: mode,
                            startedAt: t0, selfReleaseAt: release)
    }

    private func applied(_ requests: [PolicyRequest], at wall: Date? = nil,
                         to start: PolicyRecord = PolicyRecord()) -> PolicyRecord {
        requests.reduce(start) { r, q in
            guard case let .success(next) = A.apply(q, to: r, wall: wall ?? t0) else {
                XCTFail("refused: \(q)"); return r
            }
            return next
        }
    }

    private func refused(_ q: PolicyRequest, _ r: PolicyRecord, at wall: Date? = nil) -> Bool {
        if case .failure = A.apply(q, to: r, wall: wall ?? t0) { return true }
        return false
    }

    // MARK: Lock

    func testStartExtendAndTighten() {
        var r = applied([.proposeLock(lock(7))])
        XCTAssertTrue(A.isLocked(r, now: t0))
        XCTAssertFalse(A.strictActive(r, now: t0))
        r = applied([.proposeLock(lock(14)), .proposeLock(lock(14, mode: "strict"))], to: r)
        XCTAssertEqual(r.lock?.deadline, t0.addingTimeInterval(14 * day))
        XCTAssertTrue(A.strictActive(r, now: t0))
    }

    func testCannotShortenOrLeaveStrict() {
        let r = applied([.proposeLock(lock(7, mode: "strict"))])
        XCTAssertTrue(refused(.proposeLock(lock(3, mode: "strict")), r))
        XCTAssertTrue(refused(.proposeLock(lock(30, mode: "blocklist")), r),
                      "a longer lock must not be a way out of strict mode")
    }

    func testReleaseWaitsFromWhenTheAuthoritySawIt() {
        // A release date forged into the past still waits the full delay from
        // the moment the authority first saw it.
        var r = applied([.proposeLock(lock(30))])
        let later = t0.addingTimeInterval(3600)
        r = applied([.proposeLock(lock(30, release: t0.addingTimeInterval(-day)))],
                    at: later, to: r)
        XCTAssertEqual(r.releaseFirstSeen, later)
        let matures = later.addingTimeInterval(LockStore.selfReleaseDelay)
        XCTAssertTrue(A.isLocked(r, now: matures.addingTimeInterval(-1)))
        XCTAssertFalse(A.isLocked(r, now: matures))
    }

    func testReleaseCannotBeBroughtForwardButCanBeCancelled() {
        let asked = t0.addingTimeInterval(2 * day)
        let r = applied([.proposeLock(lock(30)), .proposeLock(lock(30, release: asked))])
        XCTAssertTrue(refused(.proposeLock(lock(30, release: asked.addingTimeInterval(-60))), r))
        let cancelled = applied([.proposeLock(lock(30))], to: r)
        XCTAssertNil(cancelled.lock?.selfReleaseAt)
        XCTAssertNil(cancelled.releaseFirstSeen)
    }

    func testResubmittingTheSameReleaseKeepsItsWait() {
        let asked = t0.addingTimeInterval(2 * day)
        let r = applied([.proposeLock(lock(30)), .proposeLock(lock(30, release: asked))])
        let again = applied([.proposeLock(lock(30, release: asked))],
                            at: t0.addingTimeInterval(day), to: r)
        XCTAssertEqual(again.releaseFirstSeen, t0, "a repeated sync must not restart the wait")
    }

    func testWoundBackClockFreezesTheCountdown() {
        var r = applied([.proposeLock(lock(1))], at: t0)
        r = A.settle(r, wall: t0.addingTimeInterval(12 * 3600))
        // The clock goes back a year: time stands still at the mark.
        r = A.settle(r, wall: t0.addingTimeInterval(-365 * day))
        XCTAssertEqual(r.highWaterMark, t0.addingTimeInterval(12 * 3600))
        XCTAssertTrue(A.isLocked(r, now: r.highWaterMark))
    }

    func testExpiredLockIsDroppedAndAnythingMayFollow() {
        var r = applied([.proposeLock(lock(1, mode: "strict"))])
        r = A.settle(r, wall: t0.addingTimeInterval(2 * day))
        XCTAssertNil(r.lock)
        XCTAssertFalse(refused(.proposeLock(lock(3, mode: "blocklist")), r,
                               at: t0.addingTimeInterval(2 * day)))
    }

    func testALockThatIsAlreadyOverIsRefused() {
        XCTAssertTrue(refused(.proposeLock(lock(-1)), PolicyRecord()))
    }

    // MARK: Lists and settings

    func testListsLoosenOnlyWhenUnlocked() {
        let base = applied([.setLists(customBlocks: ["bad.example"], allowlist: ["ok.example"])])
        // Unlocked: anything goes.
        XCTAssertFalse(refused(.setLists(customBlocks: [], allowlist: ["x.example"]), base))
        let locked = applied([.proposeLock(lock(7))], to: base)
        XCTAssertTrue(refused(.setLists(customBlocks: [], allowlist: ["ok.example"]), locked))
        XCTAssertTrue(refused(.setLists(customBlocks: ["bad.example"],
                                        allowlist: ["other.example"]), locked),
                      "swapping one allowance for another is a loosening")
        XCTAssertFalse(refused(.setLists(customBlocks: ["bad.example", "more.example"],
                                         allowlist: []), locked))
    }

    func testWordsAppsAndChecksLoosenOnlyWhenUnlocked() {
        let locked = applied([
            .setUserBlocks(terms: ["gambling"], apps: ["com.example.app"]),
            .setInspection(Inspection.Settings(text: true, textSensitivity: 60, hostKeywords: true)),
            .proposeLock(lock(7)),
        ])
        XCTAssertTrue(refused(.setUserBlocks(terms: [], apps: ["com.example.app"]), locked))
        XCTAssertTrue(refused(.setUserBlocks(terms: ["gambling"], apps: []), locked))
        XCTAssertTrue(refused(.setInspection(Inspection.Settings(
            text: true, textSensitivity: 40, hostKeywords: true)), locked))
        XCTAssertTrue(refused(.setInspection(Inspection.Settings(
            text: false, textSensitivity: 60, hostKeywords: true)), locked))
        XCTAssertFalse(refused(.setInspection(Inspection.Settings(
            text: true, textSensitivity: 90, hostKeywords: true)), locked))
    }

    func testRefusalLeavesTheRecordUnchanged() {
        let locked = applied([.setLists(customBlocks: ["a.example"], allowlist: []),
                              .proposeLock(lock(7))])
        guard case .failure = A.apply(.setLists(customBlocks: [], allowlist: []),
                                      to: locked, wall: t0) else {
            return XCTFail("expected a refusal")
        }
        XCTAssertEqual(locked.customBlocks, ["a.example"])
    }

    func testEveryAcceptedChangeBumpsTheRevision() {
        let r = applied([.proposeLock(lock(7)), .setLists(customBlocks: ["a.example"], allowlist: [])])
        XCTAssertEqual(r.revision, 2)
    }

    // MARK: Store

    private func scratch() -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("hisn-policy-\(UUID().uuidString)", isDirectory: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: url) }
        return url
    }

    func testStoreRoundTripsAndSurvivesACorruptCopy() throws {
        let dir = scratch()
        let store = PolicyStore(directory: dir)
        var r = applied([.proposeLock(lock(7))])            // revision 1 → slot b
        try store.save(r)
        r = applied([.setLists(customBlocks: ["a.example"], allowlist: [])], to: r) // rev 2 → slot a
        try store.save(r)
        XCTAssertEqual(store.load(), r)

        // The newest copy is torn: the older one still holds the lock.
        try Data("{ not json".utf8).write(to: dir.appendingPathComponent("policy.a.json"))
        let recovered = PolicyStore(directory: dir).load()
        XCTAssertEqual(recovered.revision, 1)
        XCTAssertNotNil(recovered.lock, "one bad write must not end the lock")
    }

    func testEmptyDirectoryIsAnEmptyRecord() {
        XCTAssertEqual(PolicyStore(directory: scratch()).load(), PolicyRecord())
    }

    // MARK: Service

    func testServicePersistsAcceptedChangesAndReportsRefusals() {
        let dir = scratch()
        var now = t0
        var changes = 0
        let service = PolicyService(store: PolicyStore(directory: dir), clock: { now })
        service.onChange = { _ in changes += 1 }

        XCTAssertTrue(service.handle(.proposeLock(lock(7, mode: "strict"))).accepted)
        let refusal = service.handle(.proposeLock(lock(1, mode: "strict")))
        XCTAssertFalse(refusal.accepted)
        XCTAssertNotNil(refusal.refusal)
        XCTAssertEqual(changes, 1)

        // A new process (a filter restart) reads the same lock back.
        let restarted = PolicyService(store: PolicyStore(directory: dir), clock: { now })
        XCTAssertTrue(restarted.status().isLocked)
        XCTAssertTrue(restarted.status().strictActive)

        now = t0.addingTimeInterval(8 * day)
        XCTAssertFalse(restarted.status().isLocked, "a lock ends at its deadline")
        XCTAssertNil(PolicyStore(directory: dir).load().lock,
                     "and its end is written down, not only held in memory")
    }

    func testStopDuringALockIsRecorded() {
        let service = PolicyService(store: PolicyStore(directory: scratch()), clock: { self.t0 })
        service.noteStoppedDuringLock()
        XCTAssertNil(service.current().stoppedDuringLock, "no lock, nothing to record")
        _ = service.handle(.proposeLock(lock(7)))
        service.noteStoppedDuringLock()
        XCTAssertEqual(service.current().stoppedDuringLock, t0)
    }

    func testListFloorOnlyRises() {
        let service = PolicyService(store: PolicyStore(directory: scratch()), clock: { self.t0 })
        service.raiseListFloor(to: 9)
        service.raiseListFloor(to: 4)
        XCTAssertEqual(service.current().listVersionFloor, 9)
    }

    // MARK: Wire format

    func testRequestsAndStatusSurviveJSON() throws {
        let requests: [PolicyRequest] = [
            .proposeLock(lock(7, mode: "strict", release: t0.addingTimeInterval(day))),
            .setLists(customBlocks: ["a.example"], allowlist: ["b.example"]),
            .setUserBlocks(terms: ["gambling"], apps: ["com.example.app"]),
            .setInspection(Inspection.Settings(text: false, textSensitivity: 10, hostKeywords: false)),
        ]
        for q in requests {
            let back = try JSONDecoder().decode(PolicyRequest.self, from: JSONEncoder().encode(q))
            XCTAssertEqual(back, q)
        }
        // Lists first: added after the lock, an allowance would be refused.
        let status = A.status(applied([requests[1], requests[0]]),
                              health: FilterHealth(domainCount: 5))
        let back = try JSONDecoder().decode(PolicyStatus.self, from: JSONEncoder().encode(status))
        XCTAssertEqual(back, status)
    }

    func testServiceNameComesFromTheFilterInfoPlist() {
        XCTAssertEqual(FilterXPC.machServiceName(filterInfo: [
            "NetworkExtension": ["NEMachServiceName": "ABCDE12345.app.hisn.filter"]]),
            "ABCDE12345.app.hisn.filter")
        XCTAssertNil(FilterXPC.machServiceName(filterInfo: [
            "NetworkExtension": ["NEMachServiceName": "$(TeamIdentifierPrefix)app.hisn.filter"]]),
            "an unexpanded build variable is not a service name")
        XCTAssertNil(FilterXPC.machServiceName(filterInfo: [:]))
    }

    func testBuiltFilterDeclaresAServiceName() throws {
        // The test host IS the built app, with the filter embedded — so this
        // reads the Info.plist the system will read.
        let plist = Bundle.main.bundleURL.appendingPathComponent(
            "Contents/Library/SystemExtensions/HisnFilter.systemextension/Contents/Info.plist")
        let info = try XCTUnwrap(NSDictionary(contentsOf: plist) as? [String: Any])
        let ne = try XCTUnwrap(info["NetworkExtension"] as? [String: Any])
        let name = try XCTUnwrap(ne["NEMachServiceName"] as? String)
        XCTAssertTrue(name.hasSuffix("app.hisn.filter"), name)
    }

    func testAnUnsignedBuildTrustsNoPeer() {
        // Tests run unsigned (CODE_SIGNING_ALLOWED=NO): no team, so no
        // requirement, so the link reports itself unconfigured and never
        // connects — the fallback every machine without the entitlements takes.
        if FilterXPC.ownTeamIdentifier() == nil {
            XCTAssertNil(FilterXPC.peerRequirement())
            XCTAssertFalse(FilterLink(serviceName: "x.app.hisn.filter",
                                      requirement: nil).isConfigured)
        }
    }
}

/// Two copies of policy, one answer: the stricter.
final class PolicyMergeTests: XCTestCase {

    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    private func view(lockDays: Double? = nil, mode: String = "blocklist",
                      allows: [String] = [], blocks: [String] = [],
                      terms: [String] = [], sensitivity: Int = 50,
                      text: Bool = true) -> PolicyView {
        let end = lockDays.map { now.addingTimeInterval($0 * 86_400) }
        return PolicyView(
            lock: end.map { LockStore.LockState(deadline: $0, mode: mode, startedAt: now) },
            effectiveDeadline: end, allowlist: allows, customBlocks: blocks,
            customTerms: terms, blockedApps: [],
            inspection: Inspection.Settings(text: text, textSensitivity: sensitivity,
                                            hostKeywords: true))
    }

    func testUnlockedEverywhereTheEditorWins() {
        let editor = view(allows: ["new.example"])
        let merged = PolicyMerge.stricter(editor: editor, other: view(allows: ["old.example"]),
                                          now: now)
        XCTAssertEqual(merged, editor)
    }

    func testDeletedMirrorsCannotEndTheAuthoritysLock() {
        // The user wiped the app's stores; the authority still holds the lock.
        let merged = PolicyMerge.stricter(editor: view(), other: view(lockDays: 5, mode: "strict"),
                                          now: now)
        XCTAssertTrue(merged.isLocked(at: now))
        XCTAssertEqual(merged.lock?.mode, "strict")
    }

    func testLockedMergeTakesTheStricterOfEveryField() {
        let local = view(lockDays: 3, allows: ["a.example", "b.example"], blocks: ["x.example"],
                         terms: ["one1"], sensitivity: 40, text: false)
        let authority = view(lockDays: 5, mode: "strict", allows: ["a.example"],
                             blocks: ["y.example"], terms: ["two2"], sensitivity: 70)
        let m = PolicyMerge.stricter(editor: local, other: authority, now: now)
        XCTAssertEqual(m.effectiveDeadline, now.addingTimeInterval(5 * 86_400))
        XCTAssertEqual(m.lock?.mode, "strict")
        XCTAssertEqual(m.allowlist, ["a.example"])
        XCTAssertEqual(Set(m.customBlocks), ["x.example", "y.example"])
        XCTAssertEqual(Set(m.customTerms), ["one1", "two2"])
        XCTAssertEqual(m.inspection.textSensitivity, 70)
        XCTAssertTrue(m.inspection.text)
    }

    func testForgedMirrorAllowanceIsDroppedWhileLocked() {
        let forged = view(lockDays: 5, allows: ["ok.example", "escape.example"])
        let authority = view(lockDays: 5, allows: ["ok.example"])
        XCTAssertEqual(PolicyMerge.stricter(editor: forged, other: authority, now: now).allowlist,
                       ["ok.example"])
    }

    func testBridgeReplyReportsTheMergedLock() {
        let m = PolicyMerge.stricter(editor: view(), other: view(lockDays: 2, mode: "strict"),
                                     now: now)
        let reply = m.bridgeReply(now: now, listVersion: 3)
        XCTAssertEqual(reply["mode"] as? String, "strict")
        XCTAssertEqual(reply["lockUntil"] as? Double,
                       now.addingTimeInterval(2 * 86_400).timeIntervalSince1970 * 1000)
    }
}
