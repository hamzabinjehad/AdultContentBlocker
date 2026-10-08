import Foundation
import XCTest
#if os(iOS)
@testable import HisnMobile
#else
@testable import Hisn
#endif

final class CommitmentClockTests: XCTestCase {
    func testAbsentCommitmentCopiesAreTheOnlyEmptyRestoration() throws {
        XCTAssertNil(try CommitmentPolicy.restoredSession(from: [nil, nil]))
        for object: Any in ["not-data", 123, Data("damaged".utf8)] {
            XCTAssertThrowsError(try CommitmentPolicy.restoredSession(from: [object, nil]))
        }
    }
    func testValidCommitmentMirrorRecoversDamagedCopyWithoutShortening() throws {
        let first = CommitmentPolicy.Session(startedAt: start, deadline: start.addingTimeInterval(7 * 86400))
        let extended = try XCTUnwrap(first.extending(by: 7 * 86400))
        let data = try JSONEncoder().encode(first), newer = try JSONEncoder().encode(extended)
        XCTAssertEqual(try CommitmentPolicy.restoredSession(from: ["damaged", newer]), extended)
        XCTAssertEqual(try CommitmentPolicy.restoredSession(from: [newer, data]), extended)
        XCTAssertEqual(try CommitmentPolicy.restoredSession(from: [data, newer]), extended)
    }
    func testInvalidCommitmentPayloadCannotRestoreAnUnlockedSession() throws {
        let invalid = CommitmentPolicy.Session(startedAt: start, deadline: start)
        XCTAssertThrowsError(try CommitmentPolicy.restoredSession(from: [try JSONEncoder().encode(invalid), nil]))
    }
    func testProtectionRestorationRequiresEveryConsentAndHealthCondition() {
        for bits in 0..<32 {
            XCTAssertEqual(CommitmentPolicy.shouldRestoreProtection(
                active: bits & 1 != 0, commitmentReadable: bits & 2 != 0,
                historyReadable: bits & 4 != 0, previouslyEnabled: bits & 8 != 0,
                authorized: bits & 16 != 0), bits == 31)
        }
    }
    private let start = Date(timeIntervalSince1970: 1_800_000_000)
    func testSleepInclusiveElapsedTimeDoesNotTriggerClockWarning() {
        let clock = CommitmentClock(wallAnchor: start, uptimeAnchor: 100)
        let afterSleep = start.addingTimeInterval(8 * 3600)
        XCTAssertEqual(clock.elapsedDate(uptime: 100 + 8 * 3600), afterSleep)
        XCTAssertEqual(clock.assessment(wall: afterSleep, uptime: 100 + 8 * 3600), .consistent)
    }
    func testPlatformContinuousClockIsFiniteAndMonotonic() {
        let first = CommitmentClock.continuousTime
        let second = CommitmentClock.continuousTime
        XCTAssertTrue(first.isFinite)
        XCTAssertGreaterThanOrEqual(first, 0)
        XCTAssertGreaterThanOrEqual(second, first)
    }
    func testElapsedTimeIgnoresForwardAndBackwardWallJumps() {
        let clock = CommitmentClock(wallAnchor: start, uptimeAnchor: 100)
        XCTAssertEqual(clock.elapsedDate(uptime: 160), start.addingTimeInterval(60))
        XCTAssertEqual(clock.assessment(wall: start.addingTimeInterval(86400), uptime: 160), .changed)
        XCTAssertEqual(clock.assessment(wall: start.addingTimeInterval(-86400), uptime: 160), .changed)
    }
    func testSmallCorrectionsAndNormalElapsedTimeRemainConsistent() {
        let clock = CommitmentClock(wallAnchor: start, uptimeAnchor: 100)
        for correction in [-120.0, 0, 120] {
            XCTAssertEqual(clock.assessment(wall: start.addingTimeInterval(60 + correction), uptime: 160), .consistent)
        }
    }
    func testRestoredAgreementClearsSessionWarning() {
        let clock = CommitmentClock(wallAnchor: start, uptimeAnchor: 100)
        XCTAssertEqual(clock.assessment(wall: start.addingTimeInterval(1000), uptime: 110), .changed)
        XCTAssertEqual(clock.assessment(wall: start.addingTimeInterval(20), uptime: 120), .consistent)
    }
    func testInvalidOrRewoundUptimeIsUnknownNotElapsedExpiry() {
        let clock = CommitmentClock(wallAnchor: start, uptimeAnchor: 100)
        for uptime in [99.0, -1, .infinity, .nan] {
            XCTAssertNil(clock.elapsedDate(uptime: uptime))
            XCTAssertEqual(clock.assessment(wall: start, uptime: uptime), .unavailable)
        }
    }
    func testInvalidWallAndAnchorAreNotTrusted() {
        let clock = CommitmentClock(wallAnchor: start, uptimeAnchor: 100)
        XCTAssertEqual(clock.assessment(wall: Date(timeIntervalSince1970: .nan), uptime: 100), .unavailable)
        XCTAssertNil(CommitmentClock(wallAnchor: start, uptimeAnchor: .nan).elapsedDate(uptime: 100))
    }
}

final class ReadinessHistoryTests: XCTestCase {
    private func history(_ memory: MirroredRuleStoreTests.Memory) -> ReadinessHistory {
        ReadinessHistory(key: "history", storage: memory, allowed: ["filter", "extension"])
    }
    func testFreshHistoryNeverAccusesUnconfiguredLayers() {
        let h = history(MirroredRuleStoreTests.Memory())
        XCTAssertTrue(h.known.isEmpty)
        XCTAssertTrue(h.storageHealthy)
    }
    func testHistorySurvivesRelaunchAndMergesSeparateWindows() {
        let memory = MirroredRuleStoreTests.Memory()
        let first = history(memory), second = history(memory)
        first.observe(["filter"])
        second.observe(["extension"])
        XCTAssertEqual(history(memory).known, ["filter", "extension"])
        first.observe([])
        XCTAssertEqual(first.known, ["filter", "extension"])
    }
    func testFailedWriteRetainsSessionEvidenceAndRetries() {
        let memory = MirroredRuleStoreTests.Memory()
        memory.failKey = "history.mirror"
        let h = history(memory)
        h.observe(["filter"])
        XCTAssertFalse(h.storageHealthy)
        XCTAssertEqual(h.known, ["filter"])
        memory.failKey = nil
        h.observe([])
        XCTAssertTrue(h.storageHealthy)
        XCTAssertEqual(history(memory).known, ["filter"])
    }
    func testCorruptHistoryIsUnknownAndNotOverwritten() {
        let memory = MirroredRuleStoreTests.Memory()
        let bad = Data("bad".utf8)
        memory.values["history.mirror"] = bad
        let h = history(memory)
        XCTAssertTrue(h.known.isEmpty)
        XCTAssertFalse(h.storageHealthy)
        h.observe(["filter"])
        XCTAssertEqual(h.known, ["filter"])
        XCTAssertFalse(h.storageHealthy)
        XCTAssertEqual(memory.values["history.mirror"] as? Data, bad)
    }
    func testUnknownIdentifiersCannotEnterBoundedHistory() {
        let memory = MirroredRuleStoreTests.Memory()
        history(memory).observe(["filter", "unexpected"])
        XCTAssertEqual(history(memory).known, ["filter"])
    }
    func testUnchangedChecksDoNotWriteNewRevision() throws {
        let memory = MirroredRuleStoreTests.Memory()
        let h = history(memory)
        h.observe(["filter"])
        let saved = memory.values["history.record"] as? Data
        h.observe(["filter"])
        h.observe([])
        XCTAssertEqual(memory.values["history.record"] as? Data, saved)
    }
}

final class MirroredRuleStoreTests: XCTestCase {
    final class Memory: RuleStorage {
        var values: [String: Any] = [:]
        var failKey: String?
        func object(forKey key: String) -> Any? { values[key] }
        func write(_ data: Data, forKey key: String) throws {
            if key == failKey { throw MirroredRuleStore.Failure.writeFailed }
            values[key] = data
        }
        func remove(_ key: String) throws {
            if key == failKey { throw MirroredRuleStore.Failure.writeFailed }
            values.removeValue(forKey: key)
        }
    }
    private func store(_ memory: Memory) -> MirroredRuleStore {
        MirroredRuleStore(key: "rules", storage: memory) { $0 == Data("old".utf8) || $0 == Data("new".utf8) }
    }
    private let old = Data("old".utf8)
    private let new = Data("new".utf8)

    func testFreshStorageIsNotCorruption() throws {
        XCTAssertNil(try store(Memory()).load())
    }
    func testLegacyRulesLoadAndMigrateOnlyOnExplicitWrite() throws {
        let memory = Memory(); memory.values["rules"] = old
        let rules = store(memory)
        XCTAssertEqual(try rules.load()?.revision, 0)
        XCTAssertNil(memory.values[rules.mirrorKey], "Readers must never repair stale copies")
        XCTAssertEqual(try rules.save(new).revision, 1)
        XCTAssertEqual(try rules.load()?.payload, new)
        XCTAssertEqual(memory.values["rules"] as? Data, new)
    }
    func testEitherMissingCopyRetainsLatestRules() throws {
        for missingMirror in [true, false] {
            let memory = Memory(); let rules = store(memory)
            try rules.save(old); try rules.save(new)
            memory.values.removeValue(forKey: missingMirror ? rules.mirrorKey : rules.primaryKey)
            XCTAssertEqual(try rules.load()?.payload, new)
            XCTAssertEqual(try rules.load()?.revision, 2)
        }
    }
    func testDeletionTombstoneDoesNotResurrectLegacyRules() throws {
        let memory = Memory(); let rules = store(memory)
        try rules.save(old); try rules.save(nil)
        memory.values["rules"] = old
        memory.values.removeValue(forKey: rules.primaryKey)
        XCTAssertNil(try rules.load()?.payload)
        XCTAssertEqual(try rules.load()?.revision, 2)
    }
    func testWrongTypeLegacyDataIsNotAnEmptyConfiguration() {
        let memory = Memory(); memory.values["rules"] = "wrong-type"
        XCTAssertThrowsError(try store(memory).load())
        XCTAssertThrowsError(try store(memory).save(new))
    }
    func testCorruptCopyCannotSilentlyFallBackToOlderPermissiveRules() throws {
        let memory = Memory(); let rules = store(memory)
        try rules.save(old)
        memory.values[rules.mirrorKey] = Data("damaged".utf8)
        XCTAssertThrowsError(try rules.load())
        XCTAssertThrowsError(try rules.save(new))
    }
    func testFailedFirstWritePreservesExistingRevision() throws {
        let memory = Memory(); let rules = store(memory)
        try rules.save(old); memory.failKey = rules.mirrorKey
        XCTAssertThrowsError(try rules.save(new))
        XCTAssertEqual(try rules.load()?.payload, old)
        XCTAssertEqual(try rules.load()?.revision, 1)
    }
    func testInterruptedSecondWriteRetainsNewRevisionAndSupportsExplicitRollback() throws {
        let memory = Memory(); let rules = store(memory)
        try rules.save(old); memory.failKey = rules.primaryKey
        XCTAssertThrowsError(try rules.save(new))
        XCTAssertEqual(try rules.load()?.payload, new)
        XCTAssertEqual(try rules.load()?.revision, 2)
        memory.failKey = nil
        try rules.save(old)
        XCTAssertEqual(try rules.load()?.payload, old)
        XCTAssertEqual(try rules.load()?.revision, 3)
    }
    func testInterruptedLegacyRemovalStillHonorsTombstone() throws {
        let memory = Memory(); let rules = store(memory)
        try rules.save(old); memory.failKey = "rules"
        XCTAssertThrowsError(try rules.save(nil))
        XCTAssertNil(try rules.load()?.payload)
        XCTAssertEqual(memory.values["rules"] as? Data, old)
    }
    func testConflictingEqualRevisionsAreNotHealthy() throws {
        let memory = Memory(); let rules = store(memory)
        try rules.save(old)
        memory.values[rules.mirrorKey] = try JSONEncoder().encode(MirroredRuleStore.Record(revision: 1, payload: new))
        XCTAssertThrowsError(try rules.load()) { XCTAssertEqual($0 as? MirroredRuleStore.Failure, .conflictingRevision) }
    }
    func testUnsupportedSchemaAndInvalidPayloadAreRejected() throws {
        let memory = Memory(); let rules = store(memory)
        for record in [MirroredRuleStore.Record(schemaVersion: 2, revision: 1, payload: old),
                       MirroredRuleStore.Record(revision: 1, payload: Data("invalid".utf8)),
                       MirroredRuleStore.Record(revision: 0, payload: old)] {
            memory.values[rules.primaryKey] = try JSONEncoder().encode(record)
            XCTAssertThrowsError(try rules.load())
        }
    }
    func testOverflowAndInvalidWritesDoNotReplaceRules() throws {
        let memory = Memory(); let rules = store(memory)
        memory.values[rules.primaryKey] = try JSONEncoder().encode(MirroredRuleStore.Record(revision: Int.max, payload: old))
        XCTAssertThrowsError(try rules.save(new))
        XCTAssertThrowsError(try rules.save(Data("invalid".utf8)))
        XCTAssertEqual(try rules.load()?.payload, old)
    }
    func testIsolatedDefaultsWritesAndRemovalsReadBack() throws {
        let name = "hisn.rules.tests." + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        let rules = MirroredRuleStore(key: "test", storage: DefaultsRuleStorage(defaults: defaults)) { $0 == self.old }
        try rules.save(old)
        XCTAssertEqual(try rules.load()?.payload, old)
        try rules.save(nil)
        XCTAssertNil(try rules.load()?.payload)
        XCTAssertNil(defaults.object(forKey: "test"))
    }
}
