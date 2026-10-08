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

    func testFixedExtensionKeepsItsFullHorizonAndRejectsInvalidIncrements() throws {
        var fixed = lock(7)
        fixed.commitmentUntil = fixed.deadline
        fixed.selfReleaseAt = t0.addingTimeInterval(2 * day)
        let extended = try XCTUnwrap(fixed.extending(by: 7 * day))
        XCTAssertEqual(extended.deadline, t0.addingTimeInterval(14 * day))
        XCTAssertEqual(extended.commitmentUntil, extended.deadline)
        XCTAssertEqual(extended.startedAt, fixed.startedAt)
        XCTAssertEqual(extended.selfReleaseAt, fixed.selfReleaseAt)
        let original = applied([.proposeLock(fixed)])
        var weakExtension = extended
        weakExtension.commitmentUntil = fixed.deadline
        XCTAssertTrue(refused(.proposeLock(weakExtension), original))
        let record = applied([.proposeLock(extended)], to: original)
        XCTAssertTrue(A.isLocked(record, now: t0.addingTimeInterval(10 * day)))
        XCTAssertEqual(A.effectiveDeadline(record), extended.deadline)
        for seconds: TimeInterval in [-1, 0, 59, .infinity, .nan, 366 * day] {
            XCTAssertNil(fixed.extending(by: seconds))
        }
        XCTAssertNil(try XCTUnwrap(lock(7).extending(by: day)).commitmentUntil,
                     "Legacy non-fixed locks retain their existing recovery behavior")
    }

    func testFixedCommitmentCannotBeRemovedOrBypassedBySelfRelease() throws {
        var fixed = lock(7)
        fixed.commitmentUntil = fixed.deadline
        var record = applied([.proposeLock(fixed)])
        var weakened = fixed
        weakened.commitmentUntil = nil
        XCTAssertTrue(refused(.proposeLock(weakened), record))
        weakened.commitmentUntil = t0.addingTimeInterval(day)
        XCTAssertTrue(refused(.proposeLock(weakened), record))
        fixed.selfReleaseAt = t0
        record = applied([.proposeLock(fixed)], to: record)
        XCTAssertEqual(A.effectiveDeadline(record), fixed.deadline)
        XCTAssertTrue(A.isLocked(record, now: t0.addingTimeInterval(3 * day)))
        XCTAssertFalse(A.isLocked(record, now: fixed.deadline))
        let decoded = try JSONDecoder().decode(LockStore.LockState.self, from: JSONEncoder().encode(fixed))
        XCTAssertEqual(decoded.commitmentUntil, fixed.deadline)
        var oldPayload = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(fixed)) as? [String: Any])
        oldPayload.removeValue(forKey: "commitmentUntil")
        let oldLock = try JSONDecoder().decode(LockStore.LockState.self,
            from: JSONSerialization.data(withJSONObject: oldPayload))
        XCTAssertNil(oldLock.commitmentUntil, "Existing locks keep their original release behavior")
    }

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
        XCTAssertTrue(recovered.recoveryRequired,
                      "the lost newer copy could have held stricter rules")
    }

    func testEmptyDirectoryIsAnEmptyRecord() {
        XCTAssertEqual(PolicyStore(directory: scratch()).load(), PolicyRecord())
    }

    func testOneValidCopyIsAUsableInstallation() throws {
        let dir = scratch()
        let store = PolicyStore(directory: dir)
        let r = applied([.proposeLock(lock(7, mode: "strict"))])
        try store.save(r)
        XCTAssertEqual(PolicyStore(directory: dir).load(), r,
                       "a first saved policy has only one copy and is not damage")
    }

    func testBothCorruptCopiesRequireRecoveryAcrossRestarts() throws {
        let dir = scratch()
        _ = PolicyStore(directory: dir)
        let a = dir.appendingPathComponent("policy.a.json")
        let b = dir.appendingPathComponent("policy.b.json")
        let badA = Data("{ damaged".utf8), badB = Data("{}".utf8)
        try badA.write(to: a)
        try badB.write(to: b)
        for _ in 0..<3 {
            let service = PolicyService(store: PolicyStore(directory: dir), clock: { self.t0 })
            let status = service.status()
            XCTAssertTrue(status.record.recoveryRequired)
            XCTAssertTrue(status.isLocked)
            XCTAssertTrue(status.strictActive)
            XCTAssertNotNil(status.persistenceError)
            XCTAssertFalse(service.handle(.setLists(customBlocks: [], allowlist: ["escape.example"])).accepted)
            XCTAssertFalse(service.handle(.proposeLock(lock(1))).accepted)
            XCTAssertTrue(service.handle(.checkIn(browser: "com.google.Chrome")).accepted,
                          "liveness reporting remains available during recovery")
            XCTAssertEqual(try Data(contentsOf: a), badA, "retain the original evidence")
            XCTAssertEqual(try Data(contentsOf: b), badB)
        }
        // Even if somebody moves the bad originals aside, the persistent
        // marker/evidence still distinguishes this from a fresh installation.
        try FileManager.default.removeItem(at: a)
        try FileManager.default.removeItem(at: b)
        XCTAssertTrue(PolicyStore(directory: dir).load().recoveryRequired)
    }

    func testLegacyCorruptEvidenceCannotBecomeAFreshUnlockedInstall() throws {
        let dir = scratch()
        _ = PolicyStore(directory: dir)
        try Data("bad old record".utf8).write(to: dir.appendingPathComponent("policy.a.json.unreadable-old"))
        XCTAssertTrue(PolicyStore(directory: dir).load().recoveryRequired)
    }

    func testOlderPermissiveCopyDoesNotUnlockAfterNewerCopyIsDamaged() throws {
        let dir = scratch()
        let store = PolicyStore(directory: dir)
        try store.save(PolicyRecord()) // slot a: the state before any lock
        let locked = applied([.proposeLock(lock(7, mode: "strict"))])
        try store.save(locked)         // slot b: the newer lock
        try Data("torn lock".utf8).write(to: dir.appendingPathComponent("policy.b.json"))
        let service = PolicyService(store: PolicyStore(directory: dir), clock: { self.t0 })
        XCTAssertNil(service.current().lock, "the surviving copy was permissive")
        XCTAssertTrue(service.status().strictActive, "unknown lost policy requires restriction")
        XCTAssertTrue(service.status().isLocked)
        XCTAssertFalse(service.handle(.setInspection(.default)).accepted)
    }

    func testUnreadableSlotAndUnavailableDirectoryRequireRecovery() throws {
        let dir = scratch()
        _ = PolicyStore(directory: dir)
        try FileManager.default.createDirectory(at: dir.appendingPathComponent("policy.a.json"),
                                                withIntermediateDirectories: true)
        XCTAssertTrue(PolicyStore(directory: dir).load().recoveryRequired)
        let file = scratch()
        try Data("not a directory".utf8).write(to: file)
        XCTAssertTrue(PolicyStore(directory: file).load().recoveryRequired,
                      "a failed directory open must not count as a fresh installation")
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

    /// A directory at the next slot deterministically rejects an atomic file
    /// write, without depending on the test runner's UID or disk exhaustion.
    private func blockWrite(at directory: URL, slot: String) throws -> URL {
        let url = directory.appendingPathComponent(slot)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        try Data("keep directory nonempty".utf8).write(to: url.appendingPathComponent("sentinel"))
        return url
    }

    func testFailedMutationIsRefusedAndKeepsThePreviousAuthority() throws {
        let dir = scratch()
        let service = PolicyService(store: PolicyStore(directory: dir), clock: { self.t0 })
        XCTAssertTrue(service.handle(.proposeLock(lock(7, mode: "strict"))).accepted)
        let previous = service.current()
        var changes = 0
        service.onChange = { _ in changes += 1 }
        let blocked = try blockWrite(at: dir, slot: "policy.a.json")
        let reply = service.handle(.setLists(customBlocks: ["a.example"], allowlist: []))
        XCTAssertFalse(reply.accepted)
        XCTAssertNotNil(reply.refusal)
        XCTAssertNotNil(reply.status.persistenceError)
        XCTAssertEqual(reply.status.record, previous)
        XCTAssertEqual(service.current(), previous)
        XCTAssertEqual(changes, 0, "a refused write must not update enforcement")
        try FileManager.default.removeItem(at: blocked)
        XCTAssertEqual(PolicyStore(directory: dir).load(), previous)
        let retry = service.handle(.setLists(customBlocks: ["a.example"], allowlist: []))
        XCTAssertTrue(retry.accepted)
        XCTAssertNil(retry.status.persistenceError)
        XCTAssertEqual(changes, 1)
        XCTAssertEqual(PolicyStore(directory: dir).load(), retry.status.record,
                       "the acknowledged response describes exactly what was saved")
    }

    func testFirstLockCannotBeAcknowledgedWhenItsSaveFails() throws {
        let dir = scratch()
        let service = PolicyService(store: PolicyStore(directory: dir), clock: { self.t0 })
        _ = try blockWrite(at: dir, slot: "policy.b.json")
        let reply = service.handle(.proposeLock(lock(7)))
        XCTAssertFalse(reply.accepted)
        XCTAssertNil(reply.status.record.lock)
        XCTAssertEqual(reply.status.record.revision, 0)
    }

    func testInternalPersistentMutationsReportFailureWithoutChangingState() throws {
        let dir = scratch()
        let service = PolicyService(store: PolicyStore(directory: dir), clock: { self.t0 })
        XCTAssertTrue(service.handle(.proposeLock(lock(7))).accepted)
        let previous = service.current()
        _ = try blockWrite(at: dir, slot: "policy.a.json")
        XCTAssertFalse(service.raiseListFloor(to: 9))
        XCTAssertEqual(service.current(), previous)
        XCTAssertFalse(service.noteStoppedDuringLock())
        XCTAssertEqual(service.current(), previous)
        XCTAssertFalse(service.adopt(legacy: lock(14)))
        XCTAssertEqual(service.current(), previous)
        XCTAssertNotNil(service.status().persistenceError)
    }

    func testExpiredLockStaysEnforcedUntilItsEndCanBeSaved() throws {
        let dir = scratch()
        var now = t0
        let service = PolicyService(store: PolicyStore(directory: dir), clock: { now })
        XCTAssertTrue(service.handle(.proposeLock(lock(1, mode: "strict"))).accepted)
        let previous = service.current()
        let blocked = try blockWrite(at: dir, slot: "policy.a.json")
        now = t0.addingTimeInterval(2 * day)
        let failed = service.status()
        XCTAssertEqual(failed.record, previous)
        XCTAssertTrue(failed.isLocked)
        XCTAssertTrue(failed.strictActive)
        XCTAssertNotNil(failed.persistenceError)
        let unlockedMirror = PolicyView(lock: nil, effectiveDeadline: nil,
            allowlist: [], customBlocks: [], customTerms: [], blockedApps: [], inspection: .default)
        let heldReply = BridgePolicy.reply(local: unlockedMirror, authority: failed,
                                          requiresAuthority: true, listVersion: 1)
        XCTAssertEqual(heldReply["mode"] as? String, "strict")
        XCTAssertGreaterThan(try XCTUnwrap(heldReply["lockUntil"] as? Double),
                             now.timeIntervalSince1970 * 1000,
                             "browser restrictions must not expire while authority holds the old policy")
        XCTAssertEqual(failed.record.lock, previous.lock, "the real deadline is never extended")
        try FileManager.default.removeItem(at: blocked)
        let recovered = service.status()
        XCTAssertFalse(recovered.isLocked)
        XCTAssertNil(recovered.record.lock)
        XCTAssertNil(recovered.persistenceError)
        XCTAssertNil(PolicyStore(directory: dir).load().lock)
        let unlockedReply = BridgePolicy.reply(local: unlockedMirror, authority: recovered,
                                              requiresAuthority: true, listVersion: 1)
        XCTAssertEqual(unlockedReply["lockUntil"] as? Double, 0)
    }

    func testFailedClockCheckpointIsRetriedAndDoesNotLoseObservedTime() throws {
        let dir = scratch()
        var now = t0
        let service = PolicyService(store: PolicyStore(directory: dir), clock: { now })
        XCTAssertTrue(service.handle(.proposeLock(lock(7))).accepted)
        let slot = dir.appendingPathComponent("policy.b.json")
        let original = try Data(contentsOf: slot)
        try FileManager.default.removeItem(at: slot)
        let blocked = try blockWrite(at: dir, slot: "policy.b.json")
        let later = t0.addingTimeInterval(600)
        now = later
        XCTAssertNotNil(service.status().persistenceError)
        now = t0.addingTimeInterval(-day)
        try FileManager.default.removeItem(at: blocked)
        try original.write(to: slot)
        XCTAssertEqual(service.current().highWaterMark, later,
                       "a failed checkpoint never lets the in-process clock move back")
        XCTAssertEqual(PolicyStore(directory: dir).load().highWaterMark, later,
                       "the failed checkpoint was not marked as already persisted")
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
        let merged = PolicyMerge.stricter(editor: editor, other: view(allows: ["old.example"]))
        XCTAssertEqual(merged, editor)
    }

    func testDeletedMirrorsCannotEndTheAuthoritysLock() {
        // The user wiped the app's stores; the authority still holds the lock.
        let merged = PolicyMerge.stricter(editor: view(), other: view(lockDays: 5, mode: "strict"))
        XCTAssertTrue(merged.isLocked(at: now))
        XCTAssertEqual(merged.lock?.mode, "strict")
    }

    func testLockedMergeTakesTheStricterOfEveryField() {
        let local = view(lockDays: 3, allows: ["a.example", "b.example"], blocks: ["x.example"],
                         terms: ["one1"], sensitivity: 40, text: false)
        let authority = view(lockDays: 5, mode: "strict", allows: ["a.example"],
                             blocks: ["y.example"], terms: ["two2"], sensitivity: 70)
        let m = PolicyMerge.stricter(editor: local, other: authority)
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
        XCTAssertEqual(PolicyMerge.stricter(editor: forged, other: authority).allowlist,
                       ["ok.example"])
    }

    func testBridgeReplyReportsTheMergedLock() {
        let m = PolicyMerge.stricter(editor: view(), other: view(lockDays: 2, mode: "strict"))
        let reply = m.bridgeReply(listVersion: 3)
        XCTAssertEqual(reply["mode"] as? String, "strict")
        XCTAssertEqual(reply["lockUntil"] as? Double,
                       now.addingTimeInterval(2 * 86_400).timeIntervalSince1970 * 1000)
    }

    func testRecoveryRestrictsTheBrowserWithoutInventingAMirroredLock() {
        var damaged = PolicyRecord()
        damaged.recoveryRequired = true
        damaged.allowlist = ["escape.example"]
        let authority = PolicyView(status: PolicyAuthority.status(damaged, health: FilterHealth()))
        let merged = PolicyMerge.stricter(editor: view(lockDays: 2, allows: ["escape.example"]),
                                         other: authority)
        XCTAssertTrue(merged.recoveryRequired)
        XCTAssertTrue(merged.isLocked(at: .distantFuture), "recovery does not expire with a clock")
        XCTAssertNil(merged.lock, "sync must not adopt an artificial permanent lock into user mirrors")
        XCTAssertTrue(merged.allowlist.isEmpty)
        let reply = merged.bridgeReply(listVersion: 1)
        XCTAssertEqual(reply["mode"] as? String, "strict")
        XCTAssertEqual(reply["allowlist"] as? [String], [])
        XCTAssertGreaterThan(reply["lockUntil"] as? Double ?? 0, now.timeIntervalSince1970 * 1000)
    }
}
