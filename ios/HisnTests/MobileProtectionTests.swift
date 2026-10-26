import CryptoKit
import SwiftUI
import WebKit
import FamilyControls
import XCTest
@testable import HisnMobile

final class MobileProtectionTests: XCTestCase {
    private func setupAssessment(checked: Bool = true, busy: Bool = false,
                                 listVerified: Bool = true, listCount: Int = 4, listVersion: Int = 1,
                                 safari: [Bool?] = [true, true, true, true],
                                 lastReload: [Bool?] = [nil, nil, nil, nil],
                                 screenTime: Bool = true, dns: MobileProtectionPolicy.DNSState = .enabled,
                                 storageHealthy: Bool = true) -> MobileProtectionPolicy.SetupAssessment {
        MobileProtectionPolicy.setupAssessment(checked: checked, busy: busy,
            listVerified: listVerified, listCount: listCount, listVersion: listVersion,
            safari: safari, lastReload: lastReload, screenTime: screenTime, dns: dns,
            storageHealthy: storageHealthy)
    }

    func testVerifiedSetupRequiresCurrentChecksNotSavedSuccessOrAButton() {
        XCTAssertEqual(setupAssessment(checked: false).state, .unchecked)
        XCTAssertEqual(setupAssessment(busy: true).state, .checking)
        let ready = setupAssessment()
        XCTAssertEqual(ready.state, .configured)
        XCTAssertEqual(ready.state.rawValue, "setup.status.configured")
        XCTAssertTrue(ready.issues.isEmpty)
        XCTAssertEqual(setupAssessment(checked: false, busy: true).state, .checking)
    }

    func testVerifiedSetupRejectsMissingEmptyAndUnverifiedSafariLists() {
        for count in [0, -1, 160_001, Int.max] {
            XCTAssertEqual(setupAssessment(listCount: count).issues, [.list])
        }
        XCTAssertEqual(setupAssessment(listVersion: 0).issues, [.list])
        XCTAssertEqual(setupAssessment(listVerified: false).state, .needsSetup)
        XCTAssertEqual(setupAssessment(listVerified: false).issues, [.list])
    }

    func testVerifiedSetupLosesReadyStateOnSafariOffUnknownOrReloadFailure() {
        for enabled: [Bool?] in [[], [true], [true, true, true, false], [true, true, true, nil]] {
            XCTAssertEqual(setupAssessment(safari: enabled).state, .needsSetup)
            XCTAssertEqual(setupAssessment(safari: enabled).issues, [.safari])
        }
        XCTAssertEqual(setupAssessment(lastReload: [true, false, true, true]).issues, [.safari])
        XCTAssertEqual(setupAssessment(lastReload: []).issues, [.safari])
    }

    func testVerifiedSetupLosesReadyStateOnScreenTimeRevocation() {
        XCTAssertEqual(setupAssessment(screenTime: false).state, .needsSetup)
        XCTAssertEqual(setupAssessment(screenTime: false).issues, [.screenTime])
        XCTAssertEqual(setupAssessment().state, .configured, "Only fresh approved/configured evidence can restore readiness")
    }

    func testVerifiedSetupDistinguishesSavedDisabledAndDifferentDNS() {
        XCTAssertEqual(setupAssessment(dns: .saved).issues, [.dnsSaved])
        XCTAssertEqual(setupAssessment(dns: .differentConfiguration).issues, [.dnsDifferent])
        for dns in [MobileProtectionPolicy.DNSState.absent, .unknown, .unavailable] {
            XCTAssertEqual(setupAssessment(dns: dns).state, .needsSetup)
            XCTAssertEqual(setupAssessment(dns: dns).issues, [.dns])
        }
    }

    func testVerifiedSetupDoesNotHideUnreadableCommitmentOrHistory() {
        XCTAssertEqual(setupAssessment(storageHealthy: false).state, .needsSetup)
        XCTAssertEqual(setupAssessment(storageHealthy: false).issues, [.storage])
        XCTAssertEqual(setupAssessment(listVerified: false, safari: [], screenTime: false,
            dns: .absent, storageHealthy: false).issues, [.list, .safari, .screenTime, .dns, .storage])
    }

    func testSafariListReadinessRequiresFourNonemptyMatchingVerifiedParts() {
        typealias Part = MobileProtectionPolicy.SafariListPart
        let valid = Array(repeating: Part(count: 1, version: 1, integrityMatches: true), count: 4)
        XCTAssertTrue(MobileProtectionPolicy.safariListReady(count: 4, version: 1, parts: valid))
        XCTAssertFalse(MobileProtectionPolicy.safariListReady(count: 0, version: 1, parts: valid))
        XCTAssertFalse(MobileProtectionPolicy.safariListReady(count: 4, version: 0, parts: valid))
        XCTAssertFalse(MobileProtectionPolicy.safariListReady(count: 5, version: 1, parts: valid))
        XCTAssertFalse(MobileProtectionPolicy.safariListReady(count: 4, version: 1, parts: Array(valid.prefix(3))))
        for bad in [Part(count: 0, version: 1, integrityMatches: true),
                    Part(count: 40_001, version: 1, integrityMatches: true),
                    Part(count: 1, version: 2, integrityMatches: true),
                    Part(count: 1, version: 1, integrityMatches: false)] {
            XCTAssertFalse(MobileProtectionPolicy.safariListReady(count: 4, version: 1, parts: [bad] + Array(valid.prefix(3))))
        }
    }

    func testBundledListCheckRejectsMissingDamagedAndEmptyResources() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("hisn-list-test-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        XCTAssertFalse(ProtectionController.checkBundledList(at: directory).verified)
        let summary = try JSONSerialization.data(withJSONObject: ["count": 4, "version": 1, "parts": 4])
        try summary.write(to: directory.appendingPathComponent("rules-metadata.json"))
        XCTAssertFalse(ProtectionController.checkBundledList(at: directory).verified)
        let raw = Data("[{\"trigger\":{\"url-filter\":\"example.com\"},\"action\":{\"type\":\"block\"}}]".utf8)
        let digest = SHA256.hash(data: raw).map { String(format: "%02x", $0) }.joined()
        let metadata = try JSONSerialization.data(withJSONObject: ["count": 1, "version": 1, "sha256": digest])
        for part in 1...4 {
            let partURL = directory.appendingPathComponent("PlugIns/Hisn\(part).appex")
            try FileManager.default.createDirectory(at: partURL, withIntermediateDirectories: true)
            try raw.write(to: partURL.appendingPathComponent("rules.json"))
            try metadata.write(to: partURL.appendingPathComponent("rules-metadata.json"))
        }
        let verified = ProtectionController.checkBundledList(at: directory)
        XCTAssertTrue(verified.verified)
        XCTAssertEqual(verified.count, 4)
        XCTAssertEqual(verified.version, 1)
        try Data("[]".utf8).write(to: directory.appendingPathComponent("PlugIns/Hisn4.appex/rules.json"))
        XCTAssertFalse(ProtectionController.checkBundledList(at: directory).verified)
    }

    func testVerifiedSetupGuidanceShipsInEnglishAndArabic() throws {
        let keys = ["setup.status.title", "setup.status.scope", "setup.status.mixedcontent", "setup.next.check"]
            + [MobileProtectionPolicy.SetupState.unchecked, .checking, .needsSetup, .configured].map(\.rawValue)
            + [MobileProtectionPolicy.SetupIssue.list, .safari, .screenTime, .screenTimeAccess, .dns, .dnsSaved,
               .dnsDifferent, .storage].map(\.rawValue)
        for language in ["en", "ar"] {
            let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"))
            let localized = try XCTUnwrap(Bundle(path: path))
            for key in keys {
                let text = localized.localizedString(forKey: key, value: nil, table: nil)
                XCTAssertFalse(text.isEmpty, "\(language): \(key)")
                XCTAssertNotEqual(text, key, "\(language): \(key)")
            }
        }
    }

    func testBrowserShieldGuidanceShipsInEnglishAndArabic() throws {
        for language in ["en", "ar"] {
            let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"))
            let localized = try XCTUnwrap(Bundle(path: path))
            for key in ["apps.browserGuardGuidance", "apps.browserGuardLimits"] {
                let text = localized.localizedString(forKey: key, value: nil, table: nil)
                XCTAssertFalse(text.isEmpty, "\(language): \(key)")
                XCTAssertNotEqual(text, key, "\(language): \(key) must not fall back to its key")
            }
        }
    }

    func testBrowserFirstGuidanceShipsInEnglishAndArabic() throws {
        for language in ["en", "ar"] {
            let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"))
            let localized = try XCTUnwrap(Bundle(path: path))
            for key in ["browserfirst.title", "browserfirst.body", "browserfirst.unverified",
                        "browserfirst.steps", "browserfirst.appOnly", "browserfirst.limits"] {
                let text = localized.localizedString(forKey: key, value: nil, table: nil)
                XCTAssertFalse(text.isEmpty, "\(language): \(key)")
                XCTAssertNotEqual(text, key, "\(language): \(key)")
            }
        }
    }

    @MainActor func testWallClockJumpDoesNotReplaceOrExpireRunningMobileCommitment() async throws {
        let start = Date(timeIntervalSince1970: 1_800_000_000)
        var wall = start
        var uptime: TimeInterval = 100
        let controller = ProtectionController(commitmentStore: MemoryCommitmentStore(),
            clockSource: { (wall, uptime) }, serverTime: AppleTimeStub(start))
        await controller.startCommitment(days: 90)
        let original = try XCTUnwrap(controller.commitment)
        wall = start.addingTimeInterval(100 * 86400); uptime = 101
        await controller.startCommitment(days: 1)
        XCTAssertTrue(controller.commitmentActive)
        XCTAssertEqual(controller.commitment, original)
        XCTAssertEqual(controller.clockAssessment, .changed)
        XCTAssertEqual(controller.now, start.addingTimeInterval(1))
        wall = start.addingTimeInterval(2); uptime = 102
        await controller.startCommitment(days: 1)
        XCTAssertEqual(controller.clockAssessment, .consistent)
        XCTAssertEqual(controller.commitment, original)
    }
    @MainActor func testConfiguredLayerHistorySurvivesControllerRelaunchWithoutClaimingProtection() {
        let memory = EphemeralRuleStorage()
        func history() -> ReadinessHistory {
            ReadinessHistory(key: "mobile-history", storage: memory,
                allowed: ["dns.title", "safari.title", "screentime.layer"])
        }
        history().observe(["dns.title"])
        let controller = ProtectionController(commitmentStore: MemoryCommitmentStore(), readinessHistory: history())
        XCTAssertEqual(controller.previouslyReadyContentLayers, ["dns.title"])
        XCTAssertEqual(controller.unconfirmedContentLayers, ["dns.title"])
        XCTAssertEqual(controller.configuredContentLayers, 0)
        XCTAssertEqual(controller.setupAssessment.state, .unchecked)
        XCTAssertTrue(controller.historyStorageHealthy)
    }
    @MainActor func testAppRuleStorageFailureIsNotUnconfiguredProtection() throws {
        let name = "hisn.mobile.rules.tests." + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        let controller = AppUsageController(defaults: defaults)
        controller.refresh()
        XCTAssertEqual(controller.health, .notConfigured)
        defaults.set("not-data", forKey: AppUsageConfiguration.key)
        controller.refresh()
        XCTAssertEqual(controller.health, .invalid)
        XCTAssertEqual(controller.errorKey, "apps.failed")
        XCTAssertFalse(controller.registered)
        XCTAssertNil(controller.configuration)
        defaults.removeObject(forKey: AppUsageConfiguration.key)
        defaults.set(Data("damaged-record".utf8), forKey: AppUsageConfiguration.key + ".mirror")
        controller.refresh()
        XCTAssertEqual(controller.health, .invalid)
        XCTAssertNil(AppUsageConfiguration.read(defaults), "Monitor must not treat invalid data as valid rules")
    }
    @MainActor func testUnavailableAppGroupHasExplicitFailureState() {
        let controller = AppUsageController(defaults: nil)
        controller.refresh()
        XCTAssertEqual(controller.health, .unavailable)
        XCTAssertEqual(controller.errorKey, "apps.failed")
        XCTAssertFalse(controller.registered)
    }
    func testReachedBudgetSurvivesSameDayRefreshWithoutBlockingAnotherDay() {
        typealias C = AppUsageConfiguration
        XCTAssertTrue(C.budgetReached(mode: .dailyBudget, reachedDay: "2026-10-4", today: "2026-10-4"))
        XCTAssertFalse(C.budgetReached(mode: .dailyBudget, reachedDay: "2026-10-3", today: "2026-10-4"))
        XCTAssertFalse(C.budgetReached(mode: .dailyBudget, reachedDay: nil, today: "2026-10-4"))
        XCTAssertFalse(C.budgetReached(mode: .dailyBudget, reachedDay: "corrupt", today: "2026-10-4"))
        XCTAssertFalse(C.budgetReached(mode: .always, reachedDay: "2026-10-4", today: "2026-10-4"))
    }
    func testReachedBudgetMirrorSurvivesLossOrDamageOfOneCopy() throws {
        typealias C = AppUsageConfiguration
        let name = "hisn.mobile.budget.tests." + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        try C.markBudgetReached(defaults, today: "2026-10-13")
        XCTAssertNotNil(defaults.object(forKey: C.reachedKey) as? Data)
        XCTAssertEqual(defaults.object(forKey: C.reachedMirrorKey) as? Data, defaults.object(forKey: C.reachedKey) as? Data)
        XCTAssertEqual(try C.reachedDay(defaults), "2026-10-13")
        defaults.removeObject(forKey: C.reachedKey)
        XCTAssertTrue(try C.budgetReached(mode: .dailyBudget, defaults: defaults, today: "2026-10-13"))
        defaults.set(["wrong": "type"], forKey: C.reachedKey)
        XCTAssertTrue(try C.budgetReached(mode: .dailyBudget, defaults: defaults, today: "2026-10-13"))
        try C.markBudgetReached(defaults, today: "2026-10-13")
        defaults.set("damaged", forKey: C.reachedMirrorKey)
        XCTAssertEqual(try C.reachedDay(defaults), "2026-10-13")
    }
    func testReachedBudgetDamagedEvidenceIsNotAnUnrestrictedDay() throws {
        typealias C = AppUsageConfiguration
        let name = "hisn.mobile.budget.tests." + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        XCTAssertFalse(try C.budgetReached(mode: .dailyBudget, defaults: defaults, today: "2026-10-13"))
        defaults.set(Data("not-a-day".utf8), forKey: C.reachedKey)
        XCTAssertThrowsError(try C.reachedDay(defaults))
        XCTAssertThrowsError(try C.budgetReached(mode: .dailyBudget, defaults: defaults, today: "2026-10-13"))
        XCTAssertNil(try? C.budgetReached(mode: .dailyBudget, defaults: defaults, today: "2026-10-13"),
            "Monitor must not take its clear-shield branch on unreadable evidence")
        XCTAssertFalse(try C.budgetReached(mode: .always, defaults: defaults, today: "2026-10-13"),
            "An unused budget marker must not interfere with the stronger always-block layer")
    }
    func testReachedBudgetLegacyMarkerAndPartialNewDayWriteArePreserved() throws {
        typealias C = AppUsageConfiguration
        let name = "hisn.mobile.budget.tests." + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        func record(_ day: String) throws -> Data { try JSONEncoder().encode(XCTUnwrap(C.reachedDay(day))) }
        defaults.set(try record("2026-10-9"), forKey: C.reachedKey)
        XCTAssertTrue(try C.budgetReached(mode: .dailyBudget, defaults: defaults, today: "2026-10-9"))
        defaults.set(try record("2026-10-13"), forKey: C.reachedMirrorKey)
        XCTAssertEqual(try C.reachedDay(defaults), "2026-10-13")
        XCTAssertTrue(try C.budgetReached(mode: .dailyBudget, defaults: defaults, today: "2026-10-13"))
        XCTAssertFalse(try C.budgetReached(mode: .dailyBudget, defaults: defaults, today: "2026-10-14"))
        XCTAssertEqual(defaults.object(forKey: C.reachedKey) as? Data, try record("2026-10-9"),
            "Reading must not write stale data over the monitor's newer marker")
    }
    func testReachedBudgetRejectsMalformedOrImpossibleDays() throws {
        typealias C = AppUsageConfiguration
        let name = "hisn.mobile.budget.tests." + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        for invalid in ["", "2026-2-29", "2026-4-31", "2026-13-1", "2026-0-1", "0-1-1",
                        "2026-01-1", "2026-1-01", "2026-1-1 ", "2026-1-1-extra", "２０２６-1-1"] {
            defaults.set(invalid, forKey: C.reachedKey)
            XCTAssertThrowsError(try C.reachedDay(defaults), invalid)
            XCTAssertThrowsError(try C.markBudgetReached(defaults, today: invalid), invalid)
        }
        try C.markBudgetReached(defaults, today: "2024-2-29")
        XCTAssertEqual(try C.reachedDay(defaults), "2024-2-29")
    }
    func testExplicitRuleRemovalClearsBothBudgetMarkers() throws {
        typealias C = AppUsageConfiguration
        let name = "hisn.mobile.budget.tests." + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        try C.markBudgetReached(defaults, today: "2026-10-13")
        try C.clearBudgetMarker(defaults)
        XCTAssertNil(defaults.object(forKey: C.reachedKey))
        XCTAssertNil(defaults.object(forKey: C.reachedMirrorKey))
        XCTAssertNil(try C.reachedDay(defaults))
    }
    func testAppIntegritySeparatesMissingPermissionFromStoppedMonitoring() {
        typealias C = AppUsageConfiguration
        XCTAssertEqual(C.health(storageAvailable: false, hasSavedData: false, valid: false,
            authorized: true, mode: nil, registered: true), .unavailable)
        XCTAssertEqual(C.health(storageAvailable: true, hasSavedData: false, valid: false,
            authorized: false, mode: nil, registered: false), .notConfigured)
        XCTAssertEqual(C.health(storageAvailable: true, hasSavedData: true, valid: false,
            authorized: true, mode: .always, registered: true), .invalid)
        XCTAssertEqual(C.health(storageAvailable: true, hasSavedData: true, valid: true,
            authorized: false, mode: .dailyBudget, registered: true), .permissionMissing)
        XCTAssertEqual(C.health(storageAvailable: true, hasSavedData: true, valid: true,
            authorized: true, mode: .dailyBudget, registered: false), .budgetStopped)
        XCTAssertEqual(C.health(storageAvailable: true, hasSavedData: true, valid: true,
            authorized: true, mode: .dailyBudget, registered: true), .budgetRegistered)
        XCTAssertEqual(C.health(storageAvailable: true, hasSavedData: true, valid: true,
            authorized: true, mode: .always, registered: false), .alwaysConfigured)
        XCTAssertTrue(C.Health.permissionMissing.needsAttention)
        XCTAssertTrue(C.Health.budgetStopped.needsAttention)
        XCTAssertFalse(C.Health.notConfigured.needsAttention)
        XCTAssertFalse(C.Health.budgetRegistered.needsAttention)
    }
    @MainActor func testExtensionPreservesStartAndSurvivesRelaunch() async throws {
        let store = MemoryCommitmentStore()
        let controller = ProtectionController(commitmentStore: store, serverTime: AppleTimeStub.followingDeviceClock())
        await controller.startCommitment(days: 90)
        let first = try XCTUnwrap(controller.commitment)
        controller.extendCommitment(days: 7)
        let next = try XCTUnwrap(controller.commitment)
        XCTAssertEqual(next.startedAt, first.startedAt)
        XCTAssertEqual(next.deadline, first.deadline.addingTimeInterval(7 * 86400))
        XCTAssertEqual(ProtectionController(commitmentStore: store).commitment, next)
        controller.extendCommitment(days: -7)
        XCTAssertEqual(controller.commitment, next)
        await controller.startCommitment(days: 1)
        XCTAssertEqual(controller.commitment, next)
    }

    func testExtensionRejectsInvalidAndOverlongSessions() {
        let start = Date(timeIntervalSince1970: 1_800_000_000)
        let session = CommitmentPolicy.Session(startedAt: start, deadline: start.addingTimeInterval(360 * 86400))
        XCTAssertNotNil(session.extending(by: 86400))
        XCTAssertNil(session.extending(by: 7 * 86400))
        for seconds: TimeInterval in [-1, 0, 59, .infinity, .nan] {
            XCTAssertNil(session.extending(by: seconds))
        }
    }

    @MainActor func testFailedExtensionKeepsOriginalCommitmentAndDisallowsWeakening() async throws {
        final class FailingStore: CommitmentPersistence {
            let original = CommitmentPolicy.Session(startedAt: Date(), deadline: Date().addingTimeInterval(7 * 86400))
            func load() throws -> CommitmentPolicy.Session? { original }
            func save(_ session: CommitmentPolicy.Session) throws { throw MobileCommitmentStore.Failure.writeFailed }
        }
        let store = FailingStore()
        let controller = ProtectionController(commitmentStore: store)
        controller.extendCommitment(days: 7)
        XCTAssertEqual(controller.commitment, store.original)
        XCTAssertFalse(controller.commitmentStorageHealthy)
        await controller.startCommitment(days: 1)
        XCTAssertEqual(controller.commitment, store.original)
    }
    @MainActor func testUncheckedProtectionDoesNotClaimConfiguredLayers() async {
        let controller = ProtectionController(commitmentStore: MemoryCommitmentStore(),
                                              serverTime: AppleTimeStub.followingDeviceClock())
        XCTAssertEqual(controller.configuredContentLayers, 0)
        XCTAssertFalse(controller.safariConfigured)
        XCTAssertFalse(controller.hasBlockingConfiguration)
        XCTAssertFalse(controller.listIntegrityVerified)
        XCTAssertEqual(controller.setupAssessment.state, .unchecked)
        await controller.startCommitment(days: 7)
        XCTAssertNotNil(controller.commitment)
        XCTAssertEqual(controller.setupAssessment.state, .unchecked,
            "Starting a commitment must not mark unchecked protection as configured")
        XCTAssertTrue(controller.previouslyReadyContentLayers.isEmpty)
        XCTAssertTrue(controller.unconfirmedContentLayers.isEmpty)
    }
    @MainActor func testNinetyDayCommitmentCannotBeReplacedWithOneDay() async throws {
        let controller = ProtectionController(commitmentStore: MemoryCommitmentStore(),
                                              serverTime: AppleTimeStub.followingDeviceClock())
        await controller.startCommitment(days: 90)
        let first = try XCTUnwrap(controller.commitment)
        XCTAssertEqual(first.deadline.timeIntervalSince(first.startedAt), 90 * 86400)
        await controller.startCommitment(days: 1)
        XCTAssertEqual(controller.commitment, first)
    }

    func testAppBudgetCannotBeWeakenedDuringCommitment() {
        let empty = FamilyActivitySelection()
        let always = AppUsageConfiguration(selection: empty, mode: .always, minutes: 30)
        let budget = AppUsageConfiguration(selection: empty, mode: .dailyBudget, minutes: 30)
        let longer = AppUsageConfiguration(selection: empty, mode: .dailyBudget, minutes: 60)
        XCTAssertFalse(always.isValid, "Empty selection must never create an all-device monitoring event")
        XCTAssertFalse(always.permits(budget, committed: true))
        XCTAssertFalse(budget.permits(longer, committed: true))
        XCTAssertTrue(budget.permits(always, committed: true))
        XCTAssertTrue(budget.permits(longer, committed: false))
        XCTAssertTrue(AppUsageConfiguration.preserves(Set(["A"]), Set(["A", "B"])))
        XCTAssertFalse(AppUsageConfiguration.preserves(Set(["A", "B"]), Set(["A"])))
        XCTAssertFalse(AppUsageConfiguration.preserves(Set(["A"]), Set(["B"])))
    }

    func testRouterAppExportRejectsUnsafeInputAndWarnsAboutAllClients() throws {
        XCTAssertEqual(try RouterAppDomains.parse(" api.example.com\nAPI.EXAMPLE.COM\n"), ["api.example.com"])
        for input in ["https://example.com", "1.1.1.1", "*.example.com", "cloudfront.net", "apple.com", "api.icloud.com", "host.example/", "-a.example.com"] {
            XCTAssertThrowsError(try RouterAppDomains.parse(input))
        }
        XCTAssertTrue(RouterAppDomains.adGuardRules(["api.example.com"]).contains("EVERY client"))
        XCTAssertTrue(RouterAppDomains.adGuardRules(["api.example.com"]).contains("||api.example.com^"))
    }

    func testActivityMonitorIsEmbeddedWithCorrectExtensionPoint() throws {
        let path = Bundle.main.bundleURL.appendingPathComponent("PlugIns/HisnActivityMonitor.appex/Info.plist")
        let plist = try XCTUnwrap(NSDictionary(contentsOf: path) as? [String: Any])
        let extensionInfo = try XCTUnwrap(plist["NSExtension"] as? [String: Any])
        XCTAssertEqual(extensionInfo["NSExtensionPointIdentifier"] as? String, "com.apple.deviceactivity.monitor-extension")
    }
    @MainActor func testSimulatorDoesNotClaimPersonalScreenTimeProtection() async {
        #if targetEnvironment(simulator)
        let controller = ProtectionController(commitmentStore: MemoryCommitmentStore(),
                                              serverTime: AppleTimeStub.followingDeviceClock())
        await controller.startCommitment(days: 7)
        let session = controller.commitment
        XCTAssertNotNil(session)
        await controller.enablePersonalScreenTime()
        XCTAssertFalse(controller.screenTimeConfigured)
        XCTAssertEqual(controller.errorKey, "screentime.simulator")
        XCTAssertEqual(controller.commitment, session)
        XCTAssertEqual(controller.removal, .unavailable)
        #endif
    }
    @MainActor func testCommitmentSurvivesRelaunchAndCannotBeShortened() async {
        let store = MemoryCommitmentStore()
        let controller = ProtectionController(commitmentStore: store, serverTime: AppleTimeStub.followingDeviceClock())
        await controller.startCommitment(days: 7)
        let first = controller.commitment
        XCTAssertNotNil(first)
        XCTAssertTrue(controller.commitmentActive)
        await controller.startCommitment(days: 1)
        XCTAssertEqual(controller.commitment, first)
        await controller.startCommitment(days: 30)
        XCTAssertEqual(controller.commitment, first, "New duration requires separate consent, not replacement")
        let relaunched = ProtectionController(commitmentStore: store)
        XCTAssertEqual(relaunched.commitment, first)
        XCTAssertTrue(relaunched.commitmentActive)
        relaunched.disablePersonalScreenTime()
        XCTAssertEqual(relaunched.commitment, first)
    }

    @MainActor func testCorruptCommitmentDoesNotLookUnlockedOrAllowNewPlan() async {
        struct BrokenStore: CommitmentPersistence {
            func load() throws -> CommitmentPolicy.Session? { throw MobileCommitmentStore.Failure.corrupt }
            func save(_ session: CommitmentPolicy.Session) throws { XCTFail("Must not overwrite unknown lock") }
        }
        let controller = ProtectionController(commitmentStore: BrokenStore(),
                                              serverTime: AppleTimeStub.followingDeviceClock())
        XCTAssertFalse(controller.commitmentStorageHealthy)
        await controller.startCommitment(days: 7)
        XCTAssertNil(controller.commitment)
        XCTAssertEqual(controller.errorKey, "commitment.storageFailed")
    }

    func testCommitmentPolicyExpiryDurationAndReleaseFloor() {
        let start = Date(timeIntervalSince1970: 1_800_000_000)
        let end = start.addingTimeInterval(7 * 86400)
        let session = CommitmentPolicy.Session(startedAt: start, deadline: end)
        XCTAssertTrue(session.isActive(at: end.addingTimeInterval(-1)))
        XCTAssertFalse(session.isActive(at: end))
        let invalid: [TimeInterval] = [0.0, -1, .infinity, .nan, 366 * 86400]
        for seconds in invalid {
            XCTAssertFalse(CommitmentPolicy.validDuration(seconds))
        }
        XCTAssertEqual(CommitmentPolicy.releaseDeadline(deadline: end, requested: start,
            matured: start.addingTimeInterval(2 * 86400), fixedUntil: end), end)
    }
    func testRemovalProtectionRequiresObservedChildRequestAndLosesEvidenceOnRevocation() {
        var evidence = MobileProtectionPolicy.RemovalEvidence()
        XCTAssertEqual(evidence.observe(.approved), .approvedScopeUnknown)
        XCTAssertEqual(evidence.observe(.approved, childRequestSucceeded: true), .guardianRequestAccepted)
        XCTAssertEqual(evidence.observe(.approved), .guardianRequestAccepted)
        XCTAssertEqual(evidence.observe(.denied), .denied)
        XCTAssertFalse(evidence.childRequestAccepted)
        XCTAssertEqual(evidence.observe(.approved), .approvedScopeUnknown)
        XCTAssertEqual(evidence.observe(.notDetermined, childRequestSucceeded: true), .notRequested)
        XCTAssertEqual(evidence.observe(.unavailable), .unavailable)
        var relaunched = MobileProtectionPolicy.RemovalEvidence()
        XCTAssertEqual(relaunched.observe(.approved), .approvedScopeUnknown)
    }
    func testFailedReloadCannotLookLikeAReadySafariConfiguration() {
        XCTAssertEqual(MobileProtectionPolicy.safariPartState(enabled: true, lastReload: false), .reloadFailed)
        XCTAssertEqual(MobileProtectionPolicy.safariPartState(enabled: nil, lastReload: true), .unknown)
        XCTAssertEqual(MobileProtectionPolicy.safariPartState(enabled: false, lastReload: true), .disabled)
        XCTAssertEqual(MobileProtectionPolicy.safariPartState(enabled: true, lastReload: nil), .enabled)
        XCTAssertFalse(MobileProtectionPolicy.safariConfigurationReady(enabled: [true, true, true, true],
            lastReload: [true, true, false, true]))
        XCTAssertFalse(MobileProtectionPolicy.safariConfigurationReady(enabled: [true, true, true, true], lastReload: []))
        XCTAssertTrue(MobileProtectionPolicy.safariConfigurationReady(enabled: [true, true, true, true],
            lastReload: [nil, nil, nil, nil]))
        XCTAssertFalse(MobileProtectionPolicy.safariConfigurationReady(enabled: [true, true, nil, true],
            lastReload: [true, true, true, true]))
    }

    func testSavingDNSIsNotTheSameAsEnablingIt() {
        XCTAssertEqual(MobileProtectionPolicy.dnsState(hasConfiguration: false, isEnabled: true,
            serverURL: nil, servers: []), .absent)
        XCTAssertEqual(MobileProtectionPolicy.dnsState(hasConfiguration: true, isEnabled: false,
            serverURL: MobileProtectionPolicy.dnsURL, servers: MobileProtectionPolicy.dnsServers), .saved)
        XCTAssertEqual(MobileProtectionPolicy.dnsState(hasConfiguration: true, isEnabled: true,
            serverURL: MobileProtectionPolicy.dnsURL, servers: MobileProtectionPolicy.dnsServers.reversed()), .enabled)
        XCTAssertEqual(MobileProtectionPolicy.dnsState(hasConfiguration: true, isEnabled: true,
            serverURL: URL(string: "https://cloudflare-dns.com/dns-query"),
            servers: MobileProtectionPolicy.dnsServers), .differentConfiguration)
        XCTAssertEqual(MobileProtectionPolicy.dnsState(hasConfiguration: true, isEnabled: true,
            serverURL: MobileProtectionPolicy.dnsURL, servers: ["1.1.1.1"]), .differentConfiguration)
    }

    func testScopedDNSIsNotDeviceWideProtection() {
        for scope in [["example.com"], ["."], [" "], ["com", "org"]] {
            XCTAssertEqual(MobileProtectionPolicy.dnsState(hasConfiguration: true, isEnabled: true,
                serverURL: MobileProtectionPolicy.dnsURL, servers: MobileProtectionPolicy.dnsServers,
                matchDomains: scope), .differentConfiguration)
        }
        for scope: [String]? in [nil, [], [""], ["example.com", ""]] {
            XCTAssertEqual(MobileProtectionPolicy.dnsState(hasConfiguration: true, isEnabled: true,
                serverURL: MobileProtectionPolicy.dnsURL, servers: MobileProtectionPolicy.dnsServers,
                matchDomains: scope), .enabled)
        }
    }

    func testAllFourSafariPartsAreRequiredIncludingUnknownStates() {
        XCTAssertTrue(MobileProtectionPolicy.allSafariLayersEnabled([true, true, true, true]))
        for values: [Bool?] in [[], [true], [true, true, true], [true, true, true, false],
                               [true, true, true, nil], [true, true, true, true, true]] {
            XCTAssertFalse(MobileProtectionPolicy.allSafariLayersEnabled(values))
        }
    }

    func testEveryBundledPartHasMatchingIntegrityAndCount() throws {
        let bundle = Bundle.main.bundleURL
        XCTAssertTrue(ProtectionController.checkBundledList(at: bundle).verified,
            "The same resource evidence used by live setup must verify the shipped bundle")
        var total = 0
        var patterns = Set<String>()
        for part in 1...4 {
            let directory = bundle.appendingPathComponent("PlugIns/Hisn\(part).appex")
            let data = try Data(contentsOf: directory.appendingPathComponent("rules.json"))
            let metadata = try JSONDecoder().decode(Metadata.self,
                from: Data(contentsOf: directory.appendingPathComponent("rules-metadata.json")))
            let rules = try JSONSerialization.jsonObject(with: data) as! [[String: Any]]
            XCTAssertEqual(rules.count, metadata.count)
            XCTAssertTrue((1...40_000).contains(rules.count))
            XCTAssertEqual(SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined(), metadata.sha256)
            for rule in rules {
                let trigger = rule["trigger"] as! [String: Any]
                XCTAssertTrue(patterns.insert(trigger["url-filter"] as! String).inserted)
            }
            total += rules.count
        }
        let metadata = try JSONSerialization.jsonObject(with:
            Data(contentsOf: bundle.appendingPathComponent("rules-metadata.json"))) as! [String: Int]
        XCTAssertEqual(total, metadata["count"])
    }

    @MainActor
    func testAppleCompilesEveryFullBundledSafariPart() async throws {
        let store = WKContentRuleListStore.default()!
        for part in 1...4 {
            let url = Bundle.main.bundleURL.appendingPathComponent("PlugIns/Hisn\(part).appex/rules.json")
            let json = try String(contentsOf: url, encoding: .utf8)
            let identifier = "hisn-part-test-\(UUID().uuidString)"
            let compiled: WKContentRuleList = try await withCheckedThrowingContinuation { continuation in
                store.compileContentRuleList(forIdentifier: identifier, encodedContentRuleList: json) { rule, error in
                    if let rule { continuation.resume(returning: rule) }
                    else { continuation.resume(throwing: error ?? NSError(domain: "HisnTests", code: 2)) }
                }
            }
            XCTAssertEqual(compiled.identifier, identifier)
            try await store.removeContentRuleList(forIdentifier: identifier)
        }
    }

    @MainActor
    func testPhoneAndTabletLayoutsRenderInBothLanguages() async throws {
        for language in ["en", "ar"] {
            for size in [CGSize(width: 390, height: 844), CGSize(width: 1024, height: 1366)] {
              for tab in [0, 1] {
                let view = MobileRootView(protection: ProtectionController(), language: .constant(language), initialTab: tab)
                    .environment(\.locale, Locale(identifier: language))
                    .environment(\.layoutDirection, language == "ar" ? .rightToLeft : .leftToRight)
                let host = UIHostingController(rootView: view.frame(width: size.width, height: size.height))
                let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
                let window = UIWindow(windowScene: scene)
                window.frame = CGRect(origin: .zero, size: size)
                window.rootViewController = host
                window.makeKeyAndVisible()
                defer { window.isHidden = true }
                host.view.frame = CGRect(origin: .zero, size: size)
                try await Task.sleep(nanoseconds: 300_000_000)
                host.view.layoutIfNeeded()
                let image = UIGraphicsImageRenderer(size: size).image { context in
                    host.view.drawHierarchy(in: CGRect(origin: .zero, size: size), afterScreenUpdates: true)
                }
                XCTAssertEqual(image.size, size)
                let pixels = try XCTUnwrap(image.cgImage?.dataProvider?.data) as Data
                let shades = Set(stride(from: 0, to: pixels.count, by: 97).map { pixels[$0] })
                XCTAssertGreaterThan(shades.count, 8, "A blank image is not evidence of rendered UI")
                let attachment = XCTAttachment(image: image)
                attachment.name = "\(language)-\(Int(size.width))-tab\(tab)"
                attachment.lifetime = .keepAlways
                add(attachment)
              }
            }
        }
    }

    @MainActor
    func testAppleCompilesTheSupportedURLRuleGrammar() async throws {
        let url = Bundle.main.bundleURL.appendingPathComponent("PlugIns/Hisn1.appex/rules.json")
        let rules = try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as! [[String: Any]]
        let json = String(data: try JSONSerialization.data(withJSONObject: [rules[0]]), encoding: .utf8)!
        let store = WKContentRuleListStore.default()!
        let identifier = "hisn-test-\(UUID().uuidString)"
        let compiled: WKContentRuleList = try await withCheckedThrowingContinuation { continuation in
            store.compileContentRuleList(forIdentifier: identifier, encodedContentRuleList: json) { rule, error in
                if let rule { continuation.resume(returning: rule) }
                else { continuation.resume(throwing: error ?? NSError(domain: "HisnTests", code: 1)) }
            }
        }
        XCTAssertEqual(compiled.identifier, identifier)
        try await store.removeContentRuleList(forIdentifier: identifier)
    }

    private struct Metadata: Decodable { let count: Int; let sha256: String }
}

/// Honest, guided setup: the removal-protection level, the vault and commitment dates, and
/// the order Setup asks for things in.
final class SetupHonestyTests: XCTestCase {
    private typealias Policy = MobileProtectionPolicy
    private typealias Attestation = MobileProtectionPolicy.LockAttestation
    private let start = Date(timeIntervalSince1970: 1_800_000_000)
    private let day: TimeInterval = 86400

    private func facts(created: Date? = nil, days: Double = 7, revealed: Bool = false) -> Policy.VaultFacts {
        let created = created ?? start
        return .init(createdAt: created, revealAt: created.addingTimeInterval(days * day), revealed: revealed)
    }

    private func lock(_ removal: Policy.RemovalState = .notRequested, _ attestation: Attestation? = nil,
                      vault: Policy.VaultFacts? = nil, now: Date? = nil) -> Policy.RemovalProtection {
        Policy.removalProtection(removal: removal, attestation: attestation, vault: vault,
                                 now: now ?? start.addingTimeInterval(3600))
    }

    private static let removalStates: [Policy.RemovalState] = [.notRequested, .denied, .approvedScopeUnknown,
                                                               .guardianRequestAccepted, .unavailable]

    // MARK: Removal-protection level

    func testANewInstallIsSelfOnly() {
        XCTAssertNil(Attestation(storage: Data()), "nothing saved is no confirmation")
        for removal: Policy.RemovalState in [.notRequested, .unavailable, .denied, .approvedScopeUnknown] {
            let level = lock(removal, Attestation(storage: Data()))
            XCTAssertEqual(level.level, .selfOnly, "\(removal)")
            XCTAssertNil(level.evidence)
            XCTAssertEqual(level.verifiedLevel, .selfOnly)
            XCTAssertFalse(level.selfTested)
            XCTAssertFalse(level.staleAttestation)
        }
    }

    func testLevelsFollowThePlanLadder() {
        XCTAssertEqual(Policy.LockLevel.allCases, [.selfOnly, .passcodeVault, .familyGuardian, .supervised])
        XCTAssertLessThan(Policy.LockLevel.selfOnly, .passcodeVault)
        XCTAssertLessThan(Policy.LockLevel.passcodeVault, .familyGuardian)
        XCTAssertLessThan(Policy.LockLevel.familyGuardian, .supervised)
        XCTAssertEqual(Attestation.Claim.allCases.map(\.level), [.passcodeVault, .familyGuardian, .supervised])
    }

    func testPasscodeVaultIsOnlyEverAttested() {
        let vault = facts()
        let claim = Attestation(claim: .passcodeVault, selfTested: true, vaultCreatedAt: vault.createdAt, confirmedAt: start)
        for removal: Policy.RemovalState in [.notRequested, .denied, .approvedScopeUnknown, .unavailable] {
            let level = lock(removal, claim, vault: vault)
            XCTAssertEqual(level.level, .passcodeVault, "\(removal)")
            XCTAssertEqual(level.evidence, .attested, "Hisn cannot read the Screen Time passcode or deletion setting")
            XCTAssertEqual(level.verifiedLevel, .selfOnly)
            XCTAssertTrue(level.selfTested)
        }
        let untested = Attestation(claim: .passcodeVault, vaultCreatedAt: vault.createdAt, confirmedAt: start)
        XCTAssertFalse(lock(.notRequested, untested, vault: vault).selfTested, "the self-test is optional")
    }

    func testVaultConfirmationCountsOnlyForThatVaultWhileItKeepsThePasscode() {
        let vault = facts()
        let claim = Attestation(claim: .passcodeVault, vaultCreatedAt: vault.createdAt, confirmedAt: start)
        XCTAssertEqual(lock(.notRequested, claim, vault: vault).level, .passcodeVault)
        let gone: [(Policy.VaultFacts?, Date?)] = [
            (nil, nil),                                                    // forgotten
            (facts(created: start.addingTimeInterval(day)), nil),          // a new vault
            (facts(revealed: true), nil),                                  // shown again
            (vault, vault.revealAt),                                       // its date reached
            (vault, vault.revealAt.addingTimeInterval(day)),
        ]
        for (current, now) in gone {
            let level = lock(.notRequested, claim, vault: current, now: now)
            XCTAssertEqual(level.level, .selfOnly)
            XCTAssertNil(level.evidence)
            XCTAssertTrue(level.staleAttestation, "an old confirmation is reported, never kept as a lock")
        }
    }

    func testOnlyChildAuthorizationSeenThisSessionIsVerified() {
        var evidence = Policy.RemovalEvidence()
        let accepted = evidence.observe(.approved, childRequestSucceeded: true)
        let guardian = lock(accepted)
        XCTAssertEqual(guardian.level, .familyGuardian)
        XCTAssertEqual(guardian.evidence, .verified)
        XCTAssertEqual(lock(evidence.observe(.denied)).level, .selfOnly, "revoked approval drops it")
        var relaunched = Policy.RemovalEvidence()
        XCTAssertNil(lock(relaunched.observe(.approved)).evidence, "a relaunch alone proves nothing")

        let vault = facts()
        let attestations: [Attestation?] = [nil] + Attestation.Claim.allCases.map {
            Attestation(claim: $0, selfTested: true, vaultCreatedAt: vault.createdAt, confirmedAt: start)
        }
        for removal in Self.removalStates {
            for attestation in attestations {
                for current in [nil, vault] {
                    let level = lock(removal, attestation, vault: current)
                    if level.evidence == .verified {
                        XCTAssertEqual(removal, .guardianRequestAccepted, "only an API result is verified")
                    }
                    if level.evidence == .attested { XCTAssertEqual(level.level, attestation?.claim.level) }
                    XCTAssertEqual(level.evidence == nil, level.level == .selfOnly)
                    XCTAssertGreaterThanOrEqual(level.level, level.verifiedLevel)
                }
            }
        }
    }

    func testAHigherConfirmedLevelStaysMarkedAsConfirmed() {
        let supervised = Attestation(claim: .supervised, confirmedAt: start)
        let above = lock(.guardianRequestAccepted, supervised)
        XCTAssertEqual(above.level, .supervised)
        XCTAssertEqual(above.evidence, .attested)
        XCTAssertEqual(above.verifiedLevel, .familyGuardian)

        let guardian = Attestation(claim: .familyGuardian, selfTested: true, confirmedAt: start)
        XCTAssertEqual(lock(.guardianRequestAccepted, guardian).evidence, .verified, "verified outranks the same claim")
        XCTAssertTrue(lock(.guardianRequestAccepted, guardian).selfTested)
        XCTAssertEqual(lock(.approvedScopeUnknown, guardian).evidence, .attested)

        let vault = facts()
        let lower = Attestation(claim: .passcodeVault, selfTested: true, vaultCreatedAt: vault.createdAt, confirmedAt: start)
        let verified = lock(.guardianRequestAccepted, lower, vault: vault)
        XCTAssertEqual(verified.level, .familyGuardian)
        XCTAssertFalse(verified.selfTested, "a self-test belongs to the level it was given for")
    }

    func testDamagedOrMalformedConfirmationNeverRaisesTheLevel() throws {
        XCTAssertNil(Attestation(storage: Data("damaged".utf8)))
        XCTAssertNil(Attestation(storage: Data("{}".utf8)))
        let reference = start.timeIntervalSinceReferenceDate
        func json(_ fields: String) -> Data {
            Data("{\(fields),\"selfTested\":false,\"confirmedAt\":\(reference)}".utf8)
        }
        XCTAssertNil(Attestation(storage: json("\"claim\":\"passcodeVault\"")), "a vault claim must name its vault")
        XCTAssertNil(Attestation(storage: json("\"claim\":\"supervised\",\"vaultCreatedAt\":1")))
        XCTAssertNil(Attestation(storage: json("\"claim\":\"verified\"")))
        XCTAssertNotNil(Attestation(storage: json("\"claim\":\"supervised\"")))
        let saved = Attestation(claim: .familyGuardian, selfTested: true, vaultCreatedAt: start, confirmedAt: start)
        XCTAssertNil(saved.vaultCreatedAt, "only a vault claim names a vault")
        XCTAssertEqual(Attestation(storage: saved.storage), saved)
        let vault = facts()
        let vaultClaim = Attestation(claim: .passcodeVault, vaultCreatedAt: vault.createdAt, confirmedAt: start)
        let restored = try XCTUnwrap(Attestation(storage: vaultClaim.storage))
        XCTAssertTrue(Policy.attestationApplies(restored, vault: vault, now: start), "survives being saved")
    }

    // MARK: Vault and commitment dates

    private func vault(days: Int = 7, revealed: Bool = false) throws -> PasscodeVault {
        var generator = SystemRandomNumberGenerator()
        var made = try XCTUnwrap(PasscodeVaultPolicy.make(days: days, now: start, using: &generator))
        if revealed { made.revealedAt = made.revealAt }
        return made
    }

    func testNoDateOfferWithoutARunningCommitment() throws {
        let sealed = try vault()
        let now = start.addingTimeInterval(3600)
        XCTAssertNil(VaultCommitmentDates.plan(vault: sealed, commitmentEnd: nil, now: now))
        XCTAssertNil(VaultCommitmentDates.plan(vault: nil, commitmentEnd: nil, now: now))
        XCTAssertNil(VaultCommitmentDates.plan(vault: nil, commitmentEnd: now, now: now), "an ended commitment")
        XCTAssertNil(VaultCommitmentDates.plan(vault: nil, commitmentEnd: now.addingTimeInterval(-1), now: now))
    }

    func testDateOfferNeverShortensTheVault() throws {
        let sealed = try vault(days: 30)
        let now = start.addingTimeInterval(3600)
        for end in [sealed.revealAt, sealed.revealAt.addingTimeInterval(-day), start.addingTimeInterval(2 * day)] {
            XCTAssertNil(VaultCommitmentDates.plan(vault: sealed, commitmentEnd: end, now: now),
                         "a vault that already lasts as long is left alone")
        }
        XCTAssertNil(VaultCommitmentDates.plan(vault: try vault(revealed: true),
                                               commitmentEnd: start.addingTimeInterval(60 * day), now: now),
                     "a shown passcode is the person's again")
    }

    func testDateOfferReachesTheCommitmentEndWithTheFewestDays() throws {
        let sealed = try vault(days: 7)
        let now = start.addingTimeInterval(3600)
        let exact = try XCTUnwrap(VaultCommitmentDates.plan(vault: sealed,
            commitmentEnd: sealed.revealAt.addingTimeInterval(7 * day), now: now))
        XCTAssertNil(exact.createDays)
        XCTAssertEqual(exact.extensions, [7])
        XCTAssertEqual(exact.until, sealed.revealAt.addingTimeInterval(7 * day), "lands on the commitment's end")

        for gapDays in [0.01, 1, 6.5, 8, 23, 29.9, 30, 31, 45, 100, 200] {
            let end = sealed.revealAt.addingTimeInterval(gapDays * day)
            let plan = try XCTUnwrap(VaultCommitmentDates.plan(vault: sealed, commitmentEnd: end, now: now), "\(gapDays)")
            XCTAssertNil(plan.createDays)
            XCTAssertTrue(plan.extensions.allSatisfy { [7, 30].contains($0) }, "only the vault's own steps")
            XCTAssertGreaterThanOrEqual(plan.until, end, "never before the commitment ends")
            XCTAssertLessThan(plan.until.timeIntervalSince(end), 7 * day, "\(gapDays): no week longer than needed")
            XCTAssertEqual(plan.until, sealed.revealAt.addingTimeInterval(Double(plan.extensions.reduce(0, +)) * day))
        }
        XCTAssertEqual(VaultCommitmentDates.plan(vault: sealed, commitmentEnd: sealed.revealAt.addingTimeInterval(23 * day),
                                                 now: now)?.extensions, [7, 7, 7, 7], "28 days, not 30")
        XCTAssertEqual(VaultCommitmentDates.plan(vault: sealed, commitmentEnd: sealed.revealAt.addingTimeInterval(30 * day),
                                                 now: now)?.extensions, [30], "same days, fewer steps")
    }

    func testANewPasscodeForACommitmentUsesTheShortestCoveringDuration() throws {
        let now = start
        func plan(_ days: Double) throws -> VaultCommitmentDates.Plan {
            try XCTUnwrap(VaultCommitmentDates.plan(vault: nil, commitmentEnd: now.addingTimeInterval(days * day), now: now))
        }
        XCTAssertEqual(try plan(0.5), .init(createDays: 1, extensions: [], until: now.addingTimeInterval(day)))
        XCTAssertEqual(try plan(5), .init(createDays: 7, extensions: [], until: now.addingTimeInterval(7 * day)))
        XCTAssertEqual(try plan(7), .init(createDays: 7, extensions: [], until: now.addingTimeInterval(7 * day)))
        XCTAssertEqual(try plan(31), .init(createDays: 1, extensions: [30], until: now.addingTimeInterval(31 * day)))
        XCTAssertEqual(try plan(90), .init(createDays: 90, extensions: [], until: now.addingTimeInterval(90 * day)))
        for days in [0.01, 2, 8, 14.5, 37, 60, 91, 120, 250, 365] {
            let result = try plan(days)
            let created = try XCTUnwrap(result.createDays)
            XCTAssertTrue(PasscodeVaultPolicy.durations.contains(created))
            XCTAssertGreaterThanOrEqual(result.until, now.addingTimeInterval(days * day))
            XCTAssertLessThan(result.until.timeIntervalSince(now.addingTimeInterval(days * day)), 7 * day, "\(days)")
            XCTAssertLessThanOrEqual(result.until.timeIntervalSince(now), PasscodeVaultPolicy.maximumSeal)
        }
    }

    func testDateOfferStaysWithinTheLongestSeal() throws {
        var extended = try vault(days: 90)
        for _ in 0..<7 { extended = try XCTUnwrap(PasscodeVaultPolicy.extending(extended, byDays: 30)) }  // 300 days
        let now = start.addingTimeInterval(3600)
        XCTAssertNil(VaultCommitmentDates.plan(vault: extended, commitmentEnd: extended.revealAt.addingTimeInterval(100 * day),
                                               now: now), "past 366 days the vault would refuse, so nothing is offered")
        XCTAssertNotNil(VaultCommitmentDates.plan(vault: extended, commitmentEnd: extended.revealAt.addingTimeInterval(60 * day),
                                                  now: now))
        XCTAssertNil(VaultCommitmentDates.plan(vault: nil, commitmentEnd: now.addingTimeInterval(400 * day), now: now))
        XCTAssertNil(VaultCommitmentDates.extensions(covering: .infinity, room: 366 * day))
        XCTAssertEqual(VaultCommitmentDates.extensions(covering: 0, room: 0), [])
    }

    @MainActor func testApplyingTheOfferThroughTheVaultLandsOnItsDate() throws {
        let clock = start
        let controller = PasscodeVaultController(storage: MemoryPasscodeVaultStorage(), clock: { clock })
        controller.create(days: 7)
        controller.seal()
        let before = try XCTUnwrap(controller.vault)
        let end = before.revealAt.addingTimeInterval(12 * day)
        let plan = try XCTUnwrap(VaultCommitmentDates.plan(vault: before, commitmentEnd: end, now: clock))
        VaultCommitmentDates.apply(plan, to: controller)
        XCTAssertEqual(controller.vault?.revealAt, plan.until)
        XCTAssertEqual(controller.vault?.passcode, before.passcode, "the same passcode, kept longer")
        XCTAssertEqual(controller.phase, .sealed(until: plan.until))

        let fresh = PasscodeVaultController(storage: MemoryPasscodeVaultStorage(), clock: { clock })
        let newPlan = try XCTUnwrap(VaultCommitmentDates.plan(vault: nil,
            commitmentEnd: clock.addingTimeInterval(45 * day), now: clock))
        VaultCommitmentDates.apply(newPlan, to: fresh)
        XCTAssertEqual(fresh.vault?.revealAt, newPlan.until)
        guard case .setup? = fresh.phase else { return XCTFail("a new passcode is shown for its setup window") }

        VaultCommitmentDates.apply(newPlan, to: controller)
        XCTAssertEqual(controller.vault?.revealAt, plan.until, "a plan for a new passcode never touches an existing one")
    }

    // MARK: Guided order

    func testStartingACommitmentWithNoLayerNeedsASecondConfirmation() {
        XCTAssertEqual(CommitmentStartFlow.afterFirstConfirmation(hasBlockingLayer: true), .start)
        XCTAssertEqual(CommitmentStartFlow.afterFirstConfirmation(hasBlockingLayer: false), .confirmNothingLocked)
        XCTAssertEqual(CommitmentStartFlow.confirmationsNeeded(hasBlockingLayer: true), 1)
        XCTAssertEqual(CommitmentStartFlow.confirmationsNeeded(hasBlockingLayer: false), 2)
    }

    @MainActor func testUncheckedProtectionCountsAsNoLayerForTheCommitment() {
        let controller = ProtectionController(commitmentStore: MemoryCommitmentStore())
        XCTAssertFalse(controller.hasBlockingConfiguration)
        XCTAssertEqual(CommitmentStartFlow.confirmationsNeeded(hasBlockingLayer: controller.hasBlockingConfiguration), 2)
    }

    func testSetupGuideAsksForLayersThenCommitmentThenVault() {
        XCTAssertEqual(SetupGuide.Step.allCases, [.layers, .commitment, .vault])
        func statuses(_ layer: Bool, _ commitment: Bool, _ vault: Bool) -> [SetupGuide.Status] {
            SetupGuide.Step.allCases.map {
                SetupGuide.status(of: $0, hasBlockingLayer: layer, commitmentActive: commitment, vaultHoldsPasscode: vault)
            }
        }
        XCTAssertEqual(statuses(false, false, false), [.next, .later, .later])
        XCTAssertEqual(statuses(true, false, false), [.done, .next, .later])
        XCTAssertEqual(statuses(true, true, false), [.done, .done, .next])
        XCTAssertEqual(statuses(true, true, true), [.done, .done, .done])
        XCTAssertEqual(statuses(false, true, false), [.next, .done, .later], "a commitment with no layer sends you back")
        XCTAssertEqual(statuses(false, false, true), [.next, .later, .done])
    }

    func testSetupHonestyTextShipsInEnglishAndArabic() throws {
        let claims = Attestation.Claim.allCases
        let keys = Policy.LockLevel.allCases.map(\.rawValue)
            + [Policy.LockEvidence.verified, .attested].map(\.rawValue)
            + SetupGuide.Step.allCases.map(\.rawValue)
            + claims.map(LockConfirmationRows.confirmKey) + claims.map(LockConfirmationRows.confirmBodyKey)
            + claims.map(LockConfirmationRows.confirmedKey)
            + ["lock.title", "lock.evidence.none", "lock.selfTested", "lock.stale", "lock.ladder", "lock.selfTest",
               "lock.withdraw", "lock.confirm.hint", "dates.commitmentUntil", "dates.vaultUntil", "dates.vaultEarlier",
               "dates.neverEarlier", "vault.match", "vault.matchBody", "vault.matchCreateBody", "vault.moreLocks",
               "vault.writeDown", "vault.appleGuide", "commitment.empty.title", "commitment.empty.body %lld",
               "commitment.shortScopeGuided", "commitment.screenTimeLostSetup", "guide.title"]
        for language in ["en", "ar"] {
            let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"))
            let localized = try XCTUnwrap(Bundle(path: path))
            for key in keys {
                XCTAssertNotEqual(localized.localizedString(forKey: key, value: nil, table: nil), key, "\(language): \(key)")
            }
        }
    }

    func testVaultGuideNamesTheOtherContentAndPrivacyLocksInBothLanguages() throws {
        func text(_ language: String, _ key: String) throws -> String {
            let bundle = try XCTUnwrap(Bundle(path: try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"))))
            return bundle.localizedString(forKey: key, value: nil, table: nil)
        }
        let english = try text("en", "vault.moreLocks")
        for setting in ["Account Changes", "Passcode Changes", "Installing Apps", "Content & Privacy"] {
            XCTAssertTrue(english.contains(setting), setting)
        }
        XCTAssertTrue(try text("en", "vault.writeDown").contains("write it down"))
        let arabic = try text("ar", "vault.moreLocks")
        for setting in ["تغييرات الحساب", "تغييرات رمز الدخول", "تثبيت التطبيقات", "قيود المحتوى والخصوصية"] {
            XCTAssertTrue(arabic.contains(setting), setting)
        }
        XCTAssertTrue(try text("ar", "vault.writeDown").contains("كتابته"))
    }
}
