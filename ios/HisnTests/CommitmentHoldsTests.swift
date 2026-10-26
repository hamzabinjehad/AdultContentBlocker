import Foundation
import ManagedSettings
import XCTest
@testable import HisnMobile

/// Apple's server time as a test sets it; nil is "offline".
private final class StubServerTime: ServerTimeSource {
    var time: Date?
    private(set) var reads = 0
    init(_ time: Date?) { self.time = time }
    func currentTime() async -> Date? { reads += 1; return time }
}

/// Hisn's Screen Time filter without a device: access and the filter as a test sets them.
private final class FakeScreenTime: ScreenTimeFilterAccess {
    var authorized: Bool
    var filter: WebContentSettings.FilterPolicy?
    init(authorized: Bool, filter: WebContentSettings.FilterPolicy?) {
        self.authorized = authorized; self.filter = filter
    }
}

private final class FakeNotifications: NotificationPermission {
    var granted: Bool
    private(set) var requests = 0
    private var asked = false
    init(granted: Bool) { self.granted = granted }
    func status() async -> NotificationStatus { asked ? (granted ? .allowed : .denied) : .notDetermined }
    func request() async -> Bool { requests += 1; asked = true; return granted }
}

final class CommitmentHoldsTests: XCTestCase {
    private let start = Date(timeIntervalSince1970: 1_800_000_000)
    private var suites: [String] = []

    override func tearDown() {
        for name in suites { UserDefaults().removePersistentDomain(forName: name) }
        suites = []
        super.tearDown()
    }

    /// A private stand-in for the app group shared with the monitor.
    private func appGroup() throws -> UserDefaults {
        let name = "hisn.tests.group." + UUID().uuidString
        suites.append(name)
        return try XCTUnwrap(UserDefaults(suiteName: name))
    }

    private func state(_ group: UserDefaults) -> MonitorSharedState { MonitorSharedState(defaults: group) }

    private var gregorian: Calendar { AppUsageConfiguration.dayCalendar }
    private func local(_ year: Int, _ month: Int, _ day: Int, _ hour: Int = 0, _ minute: Int = 0) -> Date {
        gregorian.date(from: DateComponents(year: year, month: month, day: day, hour: hour, minute: minute))!
    }

    // MARK: - Trusted time

    func testAppleReadingIsExactInOneBootAndNeverOverCountsAfterAReboot() {
        let anchor = TrustedTime.Anchor(server: start, uptime: 1000)
        XCTAssertEqual(anchor.lowerBound(uptime: 1000), start)
        XCTAssertEqual(anchor.lowerBound(uptime: 4600), start.addingTimeInterval(3600), "same boot: exact")
        // Rebooted: uptime restarted below the reading's. Only this boot's uptime is added.
        XCTAssertEqual(anchor.lowerBound(uptime: 30), start.addingTimeInterval(30))
        XCTAssertEqual(anchor.age(uptime: 4600), 3600)
        XCTAssertNil(anchor.age(uptime: 30), "a reading from before a reboot has no age in this boot")
        for broken in [TimeInterval.nan, -1, .infinity] {
            XCTAssertNil(anchor.lowerBound(uptime: broken))
            XCTAssertNil(TrustedTime.Anchor(server: start, uptime: broken).lowerBound(uptime: 10))
        }
    }

    func testADateHasPassedOnlyOnBothClocks() {
        let date = start
        XCTAssertEqual(TrustedTime.verdict(for: date, deviceNow: date.addingTimeInterval(-1), trustedNow: date), .notYet)
        XCTAssertEqual(TrustedTime.verdict(for: date, deviceNow: date, trustedNow: nil), .unverified)
        XCTAssertEqual(TrustedTime.verdict(for: date, deviceNow: date.addingTimeInterval(86400),
                                           trustedNow: date.addingTimeInterval(-1)), .clockAhead)
        XCTAssertEqual(TrustedTime.verdict(for: date, deviceNow: date, trustedNow: date), .passed)
        XCTAssertEqual(TrustedTime.verdict(for: date, deviceNow: date.addingTimeInterval(-1),
                                           trustedNow: date.addingTimeInterval(86400)), .notYet,
                       "a clock set back keeps the lock: the device clock must pass too")
        // A reading lags by whole seconds and network delay; minutes are allowed, not more.
        let skew = TrustedTime.readingSkew
        XCTAssertLessThanOrEqual(skew, 300)
        XCTAssertEqual(TrustedTime.verdict(for: date, deviceNow: date, trustedNow: date.addingTimeInterval(-2),
                                           skew: skew), .passed)
        XCTAssertEqual(TrustedTime.verdict(for: date, deviceNow: date, trustedNow: date.addingTimeInterval(-skew - 1),
                                           skew: skew), .clockAhead)
        XCTAssertEqual(TrustedTime.verdict(for: date, deviceNow: date, trustedNow: date.addingTimeInterval(-1),
                                           skew: -3600), .clockAhead, "a negative allowance is none")
    }

    func testDamagedReadingIsNoReading() throws {
        let storage = EphemeralRuleStorage()
        let store = TrustedTime.AnchorStore(storage: storage)
        XCTAssertNil(store.load())
        try store.save(.init(server: start, uptime: 5))
        XCTAssertEqual(store.load(), .init(server: start, uptime: 5))
        try storage.write(Data("damaged".utf8), forKey: TrustedTime.AnchorStore.key)
        XCTAssertNil(store.load())
    }

    func testOnlyAnHTTPSResponseFromAppleCounts() throws {
        func response(_ url: String, date: String? = "Sat, 24 Oct 2026 21:57:06 GMT") -> HTTPURLResponse {
            HTTPURLResponse(url: URL(string: url)!, statusCode: 200, httpVersion: nil,
                            headerFields: date.map { ["Date": $0] })!
        }
        XCTAssertEqual(AppleServerTime.time(from: response("https://www.apple.com/"))?.timeIntervalSince1970,
                       1_792_879_026)
        XCTAssertNotNil(AppleServerTime.time(from: response("https://apple.com/")))
        XCTAssertNil(AppleServerTime.time(from: response("http://www.apple.com/")))
        XCTAssertNil(AppleServerTime.time(from: response("https://evilapple.com/")))
        XCTAssertNil(AppleServerTime.time(from: response("https://apple.com.example/")))
        XCTAssertNil(AppleServerTime.time(from: response("https://www.apple.com/", date: nil)))
        XCTAssertNil(AppleServerTime.time(from: nil))
        XCTAssertEqual(AppleServerTime.request(timeout: 5).httpMethod, "HEAD")
        XCTAssertFalse(AppleServerTime.request(timeout: 5).httpShouldHandleCookies)
    }

    // MARK: - Commitment end (M2.1)

    @MainActor func testMovedClockAfterRelaunchDoesNotEndTheCommitment() async throws {
        let store = MemoryCommitmentStore()
        var wall = start
        var uptime: TimeInterval = 100
        let server = StubServerTime(start)
        ProtectionController(commitmentStore: store, clockSource: { (wall, uptime) }, serverTime: server)
            .startCommitment(days: 7)
        let session = try XCTUnwrap(store.session)
        // Relaunch after the clock was moved eight days forward; Apple says ten minutes passed.
        wall = start.addingTimeInterval(8 * 86400); uptime = 700
        server.time = start.addingTimeInterval(600)
        let relaunched = ProtectionController(commitmentStore: store, clockSource: { (wall, uptime) },
                                              serverTime: server)
        XCTAssertEqual(relaunched.commitmentTimeCheck, .unverified)
        XCTAssertTrue(relaunched.commitmentActive, "unconfirmed is still committed")
        await relaunched.checkServerTime()
        XCTAssertEqual(relaunched.commitmentTimeCheck, .clockAhead)
        XCTAssertTrue(relaunched.commitmentActive)
        relaunched.startCommitment(days: 1)
        XCTAssertEqual(relaunched.commitment, session, "a moved clock cannot replace the running commitment")
        XCTAssertTrue(relaunched.customSitesLocked)
    }

    @MainActor func testNetworkErrorNeverEndsTheCommitment() async throws {
        let store = MemoryCommitmentStore()
        var wall = start
        let offline = StubServerTime(nil)
        let controller = ProtectionController(commitmentStore: store, clockSource: { (wall, 100) }, serverTime: offline)
        controller.startCommitment(days: 1)
        wall = start.addingTimeInterval(2 * 86400)
        let relaunched = ProtectionController(commitmentStore: store, clockSource: { (wall, 50) }, serverTime: offline)
        await relaunched.checkServerTime()
        XCTAssertGreaterThan(offline.reads, 0)
        XCTAssertTrue(relaunched.serverTimeFailed)
        XCTAssertEqual(relaunched.commitmentTimeCheck, .unverified)
        XCTAssertTrue(relaunched.commitmentActive, "offline, the commitment stays on")
        XCTAssertTrue(relaunched.needsServerTime)
    }

    @MainActor func testCommitmentEndsWhenBothClocksHavePassed() async throws {
        let store = MemoryCommitmentStore()
        var wall = start
        let server = StubServerTime(nil)
        let controller = ProtectionController(commitmentStore: store, clockSource: { (wall, 100) }, serverTime: server)
        controller.startCommitment(days: 1)
        let session = try XCTUnwrap(controller.commitment)
        wall = session.deadline.addingTimeInterval(60)
        server.time = session.deadline.addingTimeInterval(30)
        let relaunched = ProtectionController(commitmentStore: store, clockSource: { (wall, 10) }, serverTime: server)
        await relaunched.checkServerTime()
        XCTAssertEqual(relaunched.commitmentTimeCheck, .passed)
        XCTAssertFalse(relaunched.commitmentActive)
        XCTAssertFalse(relaunched.customSitesLocked)
        XCTAssertFalse(relaunched.needsServerTime, "an ended commitment needs no more server reads")
        relaunched.startCommitment(days: 7)
        XCTAssertNotEqual(relaunched.commitment, session, "a new commitment can start after the real end")
    }

    @MainActor func testSavedAppleReadingSettlesTheEndOfflineEvenAfterAReboot() throws {
        let group = try appGroup()
        let store = MemoryCommitmentStore()
        let deadline = start.addingTimeInterval(86400)
        store.session = .init(startedAt: start, deadline: deadline)
        // The last reading was an hour before the end, at uptime 1000.
        try TrustedTime.AnchorStore(storage: DefaultsRuleStorage(defaults: group))
            .save(.init(server: deadline.addingTimeInterval(-3600), uptime: 1000))
        let wall = deadline.addingTimeInterval(7200)
        var uptime: TimeInterval = 10
        let rebooted = ProtectionController(commitmentStore: store, clockSource: { (wall, uptime) },
                                            serverTime: StubServerTime(nil), shared: state(group))
        XCTAssertEqual(rebooted.commitmentTimeCheck, .clockAhead, "after a reboot only this boot's time counts")
        uptime = 1000 + 3600 + 60
        let later = ProtectionController(commitmentStore: store, clockSource: { (wall, uptime) },
                                         serverTime: StubServerTime(nil), shared: state(group))
        XCTAssertEqual(later.commitmentTimeCheck, .passed, "enough monotonic time has passed since Apple's reading")
        XCTAssertFalse(later.commitmentActive)
    }

    @MainActor func testClockSetBackBeforeStartingDoesNotShortenTheCommitment() async throws {
        let store = MemoryCommitmentStore()
        let server = StubServerTime(start)
        let controller = ProtectionController(commitmentStore: store,
            clockSource: { (self.start.addingTimeInterval(-6 * 86400), 100) }, serverTime: server)
        await controller.checkServerTime()
        controller.startCommitment(days: 7)
        let session = try XCTUnwrap(controller.commitment)
        XCTAssertEqual(session.startedAt, start, "starts from Apple's time when the device clock is behind")
        XCTAssertEqual(session.deadline, start.addingTimeInterval(7 * 86400))
    }

    @MainActor func testPasscodeVaultUsesTheSameBothClocksRule() throws {
        var generator = SystemRandomNumberGenerator()
        let vault = try XCTUnwrap(PasscodeVaultPolicy.make(days: 1, now: start, using: &generator))
        let pairs: [(TrustedTime.Verdict, PasscodeVaultPolicy.RevealRefusal?)] =
            [(.notYet, .notYet), (.unverified, .needsNetwork), (.clockAhead, .clockDisagrees), (.passed, nil)]
        for (deviceNow, serverNow) in [(start, vault.revealAt), (vault.revealAt, nil),
                                       (vault.revealAt, start), (vault.revealAt, vault.revealAt)] {
            let verdict = TrustedTime.verdict(for: vault.revealAt, deviceNow: deviceNow, trustedNow: serverNow)
            let expected = try XCTUnwrap(pairs.first { $0.0 == verdict }).1
            XCTAssertEqual(PasscodeVaultPolicy.revealRefusal(vault, deviceNow: deviceNow, serverNow: serverNow), expected)
        }
    }

    // MARK: - Restoring the Screen Time filter (M1.4)

    @MainActor func testFilterChosenWhenCommittingIsRestoredAfterRelaunch() async throws {
        let store = MemoryCommitmentStore()
        let screenTime = FakeScreenTime(authorized: true, filter: .auto([]))
        let controller = ProtectionController(commitmentStore: store, screenTimeFilter: screenTime)
        controller.startCommitment(days: 7)
        XCTAssertEqual(store.session?.screenTimeFilter, true, "the choice is stored with the commitment")
        // iOS dropped the filter; access is still granted.
        screenTime.filter = nil
        let relaunched = ProtectionController(commitmentStore: store, screenTimeFilter: screenTime)
        await relaunched.refresh()
        XCTAssertTrue(ProtectionController.isHisnFilter(screenTime.filter))
        XCTAssertTrue(relaunched.screenTimeConfigured)
    }

    @MainActor func testFilterTurnedOffBeforeCommittingIsNeverTurnedBackOn() async throws {
        let store = MemoryCommitmentStore()
        let screenTime = FakeScreenTime(authorized: true, filter: .auto([]))
        let controller = ProtectionController(commitmentStore: store, screenTimeFilter: screenTime)
        controller.disablePersonalScreenTime()
        XCTAssertNil(screenTime.filter)
        controller.startCommitment(days: 7)
        XCTAssertEqual(store.session?.screenTimeFilter, false)
        let relaunched = ProtectionController(commitmentStore: store, screenTimeFilter: screenTime)
        await relaunched.refresh()
        XCTAssertNil(screenTime.filter)
        XCTAssertFalse(relaunched.screenTimeConfigured)
    }

    @MainActor func testRestoringNeedsAccessStillGrantedAndNeverAsksForIt() async throws {
        let store = MemoryCommitmentStore()
        store.session = .init(startedAt: Date(), deadline: Date().addingTimeInterval(7 * 86400), screenTimeFilter: true)
        let screenTime = FakeScreenTime(authorized: false, filter: nil)
        let relaunched = ProtectionController(commitmentStore: store, screenTimeFilter: screenTime)
        await relaunched.refresh()
        XCTAssertNil(screenTime.filter, "revoked access is not repaired by Hisn")
        XCTAssertNil(relaunched.errorKey, "no authorization request was made")
        XCTAssertEqual(store.session?.screenTimeFilter, true, "the recorded choice is kept for when access returns")
    }

    @MainActor func testSessionsWithoutARecordedChoiceAreNotRestored() async throws {
        let legacy = try JSONDecoder().decode(CommitmentPolicy.Session.self,
            from: JSONEncoder().encode(LegacySession(startedAt: Date(), deadline: Date().addingTimeInterval(86400))))
        XCTAssertNil(legacy.screenTimeFilter)
        let store = MemoryCommitmentStore()
        store.session = legacy
        let screenTime = FakeScreenTime(authorized: true, filter: nil)
        await ProtectionController(commitmentStore: store, screenTimeFilter: screenTime).refresh()
        XCTAssertNil(screenTime.filter)
    }

    @MainActor func testFilterTurnedOnDuringTheCommitmentIsRecorded() async throws {
        let store = MemoryCommitmentStore()
        let screenTime = FakeScreenTime(authorized: true, filter: nil)
        let controller = ProtectionController(commitmentStore: store, screenTimeFilter: screenTime)
        controller.startCommitment(days: 7)
        XCTAssertEqual(store.session?.screenTimeFilter, false)
        screenTime.filter = .auto([])   // what enablePersonalScreenTime writes, after consent
        await controller.refresh()
        XCTAssertEqual(store.session?.screenTimeFilter, true)
        let started = try XCTUnwrap(store.session)
        controller.extendCommitment(days: 7)
        XCTAssertEqual(store.session?.screenTimeFilter, true, "extending keeps the recorded choice")
        XCTAssertEqual(store.session?.startedAt, started.startedAt)
    }

    func testRestorationRequiresTheRecordedChoiceAndEveryCondition() {
        let chosen = CommitmentPolicy.Session(startedAt: start, deadline: start.addingTimeInterval(86400),
                                              screenTimeFilter: true)
        for bits in 0..<16 {
            XCTAssertEqual(CommitmentPolicy.shouldRestoreScreenTimeFilter(session: chosen,
                active: bits & 1 != 0, commitmentReadable: bits & 2 != 0, authorized: bits & 4 != 0,
                filterPresent: bits & 8 != 0), bits == 7)
        }
        for session in [nil, CommitmentPolicy.Session(startedAt: start, deadline: start.addingTimeInterval(86400)),
                        CommitmentPolicy.Session(startedAt: start, deadline: start.addingTimeInterval(86400),
                                                 screenTimeFilter: false)] {
            XCTAssertFalse(CommitmentPolicy.shouldRestoreScreenTimeFilter(session: session, active: true,
                commitmentReadable: true, authorized: true, filterPresent: false))
        }
    }

    func testMirrorRestorationKeepsTheRecordedChoice() throws {
        let deadline = start.addingTimeInterval(7 * 86400)
        let without = try JSONEncoder().encode(CommitmentPolicy.Session(startedAt: start, deadline: deadline))
        let with = try JSONEncoder().encode(CommitmentPolicy.Session(startedAt: start, deadline: deadline,
                                                                     screenTimeFilter: true))
        XCTAssertEqual(try CommitmentPolicy.restoredSession(from: [without, with])?.screenTimeFilter, true)
        XCTAssertEqual(try CommitmentPolicy.restoredSession(from: [with, without])?.screenTimeFilter, true)
        let later = try JSONEncoder().encode(CommitmentPolicy.Session(startedAt: start,
            deadline: deadline.addingTimeInterval(86400)))
        XCTAssertEqual(try CommitmentPolicy.restoredSession(from: [with, later])?.deadline,
                       deadline.addingTimeInterval(86400), "the later end still wins")
    }

    // MARK: - Budget day boundary (M2.2)

    func testMovedDateDoesNotStartANewBudgetDay() {
        typealias C = AppUsageConfiguration
        let reached = "2026-10-26"
        let nextMorning = local(2026, 10, 27, 9)
        XCTAssertEqual(C.budgetDay(reachedDay: nil, deviceNow: nextMorning, trustedNow: nil), .notReached)
        XCTAssertEqual(C.budgetDay(reachedDay: reached, deviceNow: local(2026, 10, 26, 23, 59),
                                   trustedNow: nil), .reached)
        XCTAssertEqual(C.budgetDay(reachedDay: reached, deviceNow: nextMorning,
                                   trustedNow: local(2026, 10, 26, 15)), .unverified,
                       "the date was moved forward: Apple's time is still the reached day")
        XCTAssertEqual(C.budgetDay(reachedDay: reached, deviceNow: nextMorning, trustedNow: nil), .unverified,
                       "no Apple time inside the extension: keep the shield")
        XCTAssertEqual(C.budgetDay(reachedDay: reached, deviceNow: nextMorning,
                                   trustedNow: local(2026, 10, 27, 0, 1)), .over)
        XCTAssertEqual(C.budgetDay(reachedDay: reached, deviceNow: local(2026, 10, 27),
                                   trustedNow: local(2026, 10, 27).addingTimeInterval(-2)), .over,
                       "an honest midnight with a reading a moment behind is the new day")
        XCTAssertEqual(C.budgetDay(reachedDay: reached, deviceNow: local(2026, 10, 25, 12),
                                   trustedNow: nextMorning), .reached, "a clock set back keeps the shield")
        XCTAssertEqual(C.budgetDay(reachedDay: "damaged", deviceNow: nextMorning, trustedNow: nextMorning),
                       .unverified, "an unreadable day never clears a shield")
    }

    func testAMarkerFromALaterDayStillCountsAsReached() {
        typealias C = AppUsageConfiguration
        XCTAssertTrue(C.budgetReached(mode: .dailyBudget, reachedDay: "2026-10-27", today: "2026-10-26"))
        XCTAssertTrue(C.budgetReached(mode: .dailyBudget, reachedDay: "2026-10-26", today: "2026-10-26"))
        XCTAssertFalse(C.budgetReached(mode: .dailyBudget, reachedDay: "2026-10-25", today: "2026-10-26"))
        XCTAssertFalse(C.budgetReached(mode: .dailyBudget, reachedDay: "2025-12-31", today: "2026-1-1"))
        XCTAssertTrue(C.budgetReached(mode: .dailyBudget, reachedDay: "2026-1-1", today: "2025-12-31"))
        XCTAssertFalse(C.budgetReached(mode: .always, reachedDay: "2026-10-27", today: "2026-10-26"))
    }

    func testBudgetDaysAreGregorianWhateverCalendarThePersonReads() {
        XCTAssertEqual(AppUsageConfiguration.dayCalendar.identifier, .gregorian)
        XCTAssertEqual(AppUsageConfiguration.dayCalendar.timeZone, .current)
        XCTAssertEqual(AppUsageConfiguration.day(local(2026, 10, 26, 12)), "2026-10-26")
    }

    func testTheAppFinishesAHeldDayOnlyWithAppleTime() {
        typealias C = AppUsageConfiguration
        let nextMorning = local(2026, 10, 27, 9)
        func action(held: Bool = true, mode: C.Mode? = .dailyBudget, reached: String? = "2026-10-26",
                    trusted: Date?) -> C.HeldDayAction {
            C.heldDayAction(held: held, mode: mode, reachedDay: reached, deviceNow: nextMorning, trustedNow: trusted)
        }
        XCTAssertEqual(action(held: false, trusted: nextMorning), .none)
        XCTAssertEqual(action(trusted: nil), .none, "still offline: keep the shield")
        XCTAssertEqual(action(trusted: local(2026, 10, 26, 18)), .none, "the clock is still ahead")
        XCTAssertEqual(action(trusted: nextMorning), .clearShield)
        XCTAssertEqual(action(reached: "2026-10-27", trusted: nextMorning), .forget,
                       "the budget was reached again today: the shield is right")
        XCTAssertEqual(action(mode: .always, trusted: nil), .forget)
        XCTAssertEqual(action(mode: nil, trusted: nil), .forget)
    }

    func testUnreadableRulesOrMarkersNeverLetTheAppClearAHeldShield() throws {
        typealias C = AppUsageConfiguration
        let defaults = try appGroup()
        let now = local(2026, 10, 27, 9)
        XCTAssertEqual(C.heldDayAction(defaults, deviceNow: now, trustedNow: now), .none)
        C.markHeld(defaults, day: "2026-10-26")
        XCTAssertEqual(C.heldDay(defaults), "2026-10-26")
        XCTAssertEqual(C.heldDayAction(defaults, deviceNow: now, trustedNow: now), .forget, "no saved rules")
        defaults.set("not-data", forKey: C.key)
        XCTAssertEqual(C.heldDayAction(defaults, deviceNow: now, trustedNow: now), .none)
        defaults.removeObject(forKey: C.key)
        defaults.set(Data("damaged".utf8), forKey: C.reachedKey)
        XCTAssertEqual(C.heldDayAction(defaults, deviceNow: now, trustedNow: now), .none)
        C.clearHeld(defaults)
        XCTAssertNil(C.heldDay(defaults))
    }

    @MainActor func testHeldDayAsksForAppleTimeAndForgetsWhenRulesAreGone() async throws {
        let group = try appGroup()
        AppUsageConfiguration.markHeld(group, day: "2026-10-26")
        let server = StubServerTime(local(2026, 10, 27, 9))
        let controller = ProtectionController(commitmentStore: MemoryCommitmentStore(),
            clockSource: { (self.local(2026, 10, 27, 9), 100) }, serverTime: server, shared: state(group))
        XCTAssertTrue(controller.needsServerTime)
        await controller.checkServerTime()
        XCTAssertEqual(server.reads, 1)
        XCTAssertNil(AppUsageConfiguration.heldDay(group), "no daily budget is saved, so nothing is held")
        XCTAssertEqual(TrustedTime.AnchorStore(storage: DefaultsRuleStorage(defaults: group)).load()?.server,
                       local(2026, 10, 27, 9), "the reading is shared with the monitor")
        XCTAssertFalse(controller.needsServerTime)
    }

    func testWithoutAnAppGroupNothingIsSharedOrCleared() {
        let memory = MonitorSharedState(defaults: nil)
        XCTAssertFalse(memory.alertsWanted)
        memory.alertsWanted = true
        XCTAssertTrue(memory.alertsWanted)
        XCTAssertFalse(memory.hasHeldBudgetDay)
        XCTAssertFalse(memory.dailyBudgetConfigured)
        XCTAssertEqual(memory.heldDayAction(deviceNow: start, trustedNow: start), .none)
    }

    // MARK: - Layer-loss alerts (M2.4)

    private func watch(webFilter: Bool = true, alertsOn: Bool = true, deadline: Date? = nil) -> LayerAlertPolicy.Watch {
        let texts = Dictionary(uniqueKeysWithValues: LayerAlertPolicy.Layer.allCases.map {
            ($0.rawValue, LayerAlertPolicy.Text(title: "t-\($0.rawValue)", body: "b-\($0.rawValue)"))
        })
        return .init(startedAt: start, deadline: deadline ?? start.addingTimeInterval(7 * 86400),
                     webFilter: webFilter, alertsOn: alertsOn, texts: texts)
    }

    private func observed(authorized: Bool = true, filter: Bool = true, shieldsExpected: Bool = false,
                          shields: Bool = false) -> LayerAlertPolicy.Observation {
        .init(authorized: authorized, webFilterSet: filter, appShieldsExpected: shieldsExpected, appShieldsSet: shields)
    }

    func testOnlyAMissingExpectedLayerIsALoss() {
        typealias P = LayerAlertPolicy
        XCTAssertEqual(P.lostLayers(watch(), observed: observed()), [])
        XCTAssertEqual(P.lostLayers(watch(), observed: observed(filter: false)), [.webFilter])
        XCTAssertEqual(P.lostLayers(watch(webFilter: false), observed: observed(filter: false)), [],
                       "a filter the person did not choose is not expected")
        XCTAssertEqual(P.lostLayers(watch(), observed: observed(shieldsExpected: true, shields: false)), [.appShields])
        XCTAssertEqual(P.lostLayers(watch(), observed: observed(filter: false, shieldsExpected: true)),
                       [.webFilter, .appShields])
        XCTAssertEqual(P.lostLayers(watch(), observed: observed(authorized: false, filter: false, shieldsExpected: true)),
                       [.screenTimeAccess], "access turned off removes everything: one alert")
        XCTAssertEqual(P.lostLayers(watch(), observed: observed(authorized: false)), [],
                       "a status read with every setting still in place is not a loss")
    }

    func testAlertsOnlyDuringTheCommitmentWhenTurnedOnAndOncePerLoss() {
        typealias P = LayerAlertPolicy
        let during = start.addingTimeInterval(86400)
        let lost = observed(filter: false)
        XCTAssertEqual(P.decide(watch: nil, deviceNow: during, trustedNow: nil, observed: lost, alreadyPosted: []).post, [])
        XCTAssertEqual(P.decide(watch: watch(alertsOn: false), deviceNow: during, trustedNow: nil,
                                observed: lost, alreadyPosted: []).post, [])
        let first = P.decide(watch: watch(), deviceNow: during, trustedNow: nil, observed: lost, alreadyPosted: [])
        XCTAssertEqual(first.post, [.webFilter])
        XCTAssertEqual(first.remember, [.webFilter])
        let again = P.decide(watch: watch(), deviceNow: during, trustedNow: nil, observed: lost,
                             alreadyPosted: first.remember)
        XCTAssertEqual(again.post, [], "one notification per loss")
        let back = P.decide(watch: watch(), deviceNow: during, trustedNow: nil, observed: observed(),
                            alreadyPosted: again.remember)
        XCTAssertEqual(back.remember, [])
        XCTAssertEqual(P.decide(watch: watch(), deviceNow: during, trustedNow: nil, observed: lost,
                                alreadyPosted: back.remember).post, [.webFilter], "lost again: announced again")
        let end = watch().deadline
        XCTAssertEqual(P.decide(watch: watch(), deviceNow: end, trustedNow: end, observed: lost,
                                alreadyPosted: []).post, [], "after the end on both clocks: no alerts")
        XCTAssertEqual(P.decide(watch: watch(), deviceNow: end, trustedNow: nil, observed: lost,
                                alreadyPosted: []).post, [.webFilter], "an unconfirmed end is still the commitment")
    }

    func testAlertWithoutTextIsNotPostedAndMemoryBelongsToOneCommitment() {
        typealias P = LayerAlertPolicy
        let silent = P.Watch(startedAt: start, deadline: start.addingTimeInterval(86400), webFilter: true,
                             alertsOn: true, texts: [:])
        let decision = P.decide(watch: silent, deviceNow: start, trustedNow: nil, observed: observed(filter: false),
                                alreadyPosted: [])
        XCTAssertEqual(decision.post, [])
        XCTAssertEqual(decision.remember, [], "never a raw key; retried once text exists")
        let memory = P.Memory(startedAt: start, layers: [.webFilter])
        XCTAssertEqual(P.posted(memory, for: watch()), [.webFilter])
        let next = P.Watch(startedAt: start.addingTimeInterval(1), deadline: start.addingTimeInterval(86400),
                           webFilter: true, alertsOn: true, texts: [:])
        XCTAssertEqual(P.posted(memory, for: next), [], "a new commitment starts with none")
        XCTAssertEqual(P.posted(nil, for: watch()), [])
    }

    func testWatchStorageRoundTripsAndRemoves() throws {
        let storage = EphemeralRuleStorage()
        XCTAssertNil(LayerAlertPolicy.loadWatch(storage))
        try LayerAlertPolicy.saveWatch(watch(), to: storage)
        XCTAssertEqual(LayerAlertPolicy.loadWatch(storage), watch())
        try LayerAlertPolicy.saveWatch(nil, to: storage)
        XCTAssertNil(storage.object(forKey: LayerAlertPolicy.watchKey))
        try storage.write(Data("damaged".utf8), forKey: LayerAlertPolicy.watchKey)
        XCTAssertNil(LayerAlertPolicy.loadWatch(storage), "a damaged watch posts nothing")
        try LayerAlertPolicy.saveWatch(nil, to: storage)
        XCTAssertNil(storage.object(forKey: LayerAlertPolicy.watchKey))
        try LayerAlertPolicy.saveMemory(.init(startedAt: start, layers: [.appShields]), to: storage)
        XCTAssertEqual(LayerAlertPolicy.loadMemory(storage), .init(startedAt: start, layers: [.appShields]))
    }

    func testAlertTextShipsInEnglishAndArabic() throws {
        let english = ProtectionController.alertTexts(languageChoice: "en")
        let arabic = ProtectionController.alertTexts(languageChoice: "ar")
        XCTAssertEqual(Set(english.keys), Set(LayerAlertPolicy.Layer.allCases.map(\.rawValue)))
        XCTAssertEqual(Set(arabic.keys), Set(english.keys))
        for (key, text) in arabic {
            XCTAssertNotNil(text.title.range(of: "\\p{Arabic}", options: .regularExpression), key)
            XCTAssertNotNil(text.body.range(of: "\\p{Arabic}", options: .regularExpression), key)
            XCTAssertNotEqual(text, english[key])
        }
        XCTAssertTrue(english["webFilter"]?.body.contains("Only you") == true)
        XCTAssertEqual(ProtectionController.alertTexts(languageChoice: "system").count, 3)
    }

    @MainActor func testPermissionIsAskedOnlyWhenThePersonTurnsAlertsOn() async throws {
        let group = try appGroup()
        let notifications = FakeNotifications(granted: true)
        let controller = ProtectionController(commitmentStore: MemoryCommitmentStore(), shared: state(group),
                                              notifications: notifications)
        await controller.refresh()
        controller.startCommitment(days: 7)
        XCTAssertEqual(notifications.requests, 0, "starting a commitment or refreshing never asks")
        XCTAssertEqual(controller.layerAlerts, .off)
        XCTAssertEqual(LayerAlertPolicy.loadWatch(DefaultsRuleStorage(defaults: group))?.alertsOn, false)
        await controller.enableLayerAlerts()
        XCTAssertEqual(notifications.requests, 1)
        XCTAssertEqual(controller.layerAlerts, .on)
        let watch = try XCTUnwrap(LayerAlertPolicy.loadWatch(DefaultsRuleStorage(defaults: group)))
        XCTAssertTrue(watch.alertsOn)
        XCTAssertEqual(watch.deadline, controller.commitment?.deadline)
        XCTAssertFalse(watch.webFilter, "no filter was chosen in this test")
        XCTAssertEqual(watch.texts.count, 3)
        // The preference survives a relaunch; the monitor's watch is rewritten, not re-asked.
        let relaunched = ProtectionController(commitmentStore: MemoryCommitmentStore(), shared: state(group),
                                              notifications: notifications)
        XCTAssertTrue(relaunched.alertsWanted)
        relaunched.disableLayerAlerts()
        XCTAssertEqual(relaunched.layerAlerts, .off)
        XCTAssertEqual(notifications.requests, 1)
    }

    @MainActor func testDeclinedNotificationsSayAlertsCannotShow() async throws {
        let group = try appGroup()
        let controller = ProtectionController(commitmentStore: MemoryCommitmentStore(), shared: state(group),
                                              notifications: FakeNotifications(granted: false))
        controller.startCommitment(days: 1)
        await controller.enableLayerAlerts()
        XCTAssertEqual(controller.layerAlerts, .notAllowed)
        XCTAssertEqual(LayerAlertPolicy.loadWatch(DefaultsRuleStorage(defaults: group))?.alertsOn, false)
    }

    @MainActor func testWatchIsRemovedOnceTheCommitmentEndsOnBothClocks() async throws {
        let group = try appGroup()
        let store = MemoryCommitmentStore()
        var wall = start
        let server = StubServerTime(nil)
        let controller = ProtectionController(commitmentStore: store, clockSource: { (wall, 100) },
                                              serverTime: server, shared: state(group))
        controller.startCommitment(days: 1)
        XCTAssertNotNil(LayerAlertPolicy.loadWatch(DefaultsRuleStorage(defaults: group)))
        let deadline = try XCTUnwrap(store.session?.deadline)
        wall = deadline.addingTimeInterval(10)
        server.time = deadline.addingTimeInterval(5)
        let relaunched = ProtectionController(commitmentStore: store, clockSource: { (wall, 5) },
                                              serverTime: server, shared: state(group))
        XCTAssertNotNil(LayerAlertPolicy.loadWatch(DefaultsRuleStorage(defaults: group)), "not ended yet: unverified")
        await relaunched.checkServerTime()
        XCTAssertFalse(relaunched.commitmentActive)
        XCTAssertNil(LayerAlertPolicy.loadWatch(DefaultsRuleStorage(defaults: group)))
    }

    func testCommitmentTextShipsInEnglishAndArabic() throws {
        for language in ["en", "ar"] {
            let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"))
            let localized = try XCTUnwrap(Bundle(path: path))
            for key in ["commitment.endUnverified", "commitment.clockAhead", "commitment.checkTime",
                        "commitment.checkingTime", "commitment.trustedTime", "commitment.restoreConsent",
                        "alerts.enable", "alerts.consentTitle", "alerts.consentBody", "alerts.on",
                        "alerts.notAllowed", "alerts.notScheduled", "alerts.limits", "alerts.disable"] {
                let text = localized.localizedString(forKey: key, value: nil, table: nil)
                XCTAssertNotEqual(text, key, "\(language): \(key)")
            }
        }
    }
}

/// A commitment as saved before the Screen Time choice was recorded.
private struct LegacySession: Codable {
    let startedAt: Date
    let deadline: Date
}
