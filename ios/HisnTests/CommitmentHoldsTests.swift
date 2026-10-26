import Foundation
import ManagedSettings
import XCTest
@testable import HisnMobile

/// Apple's server time as a test sets it; nil is "offline". `followingDeviceClock()` answers
/// with the real clock, for tests that run on the real clock and only need a commitment to
/// start (starting reads Apple's time when this boot has no reading yet).
final class AppleTimeStub: ServerTimeSource {
    var time: Date?
    private var followsDeviceClock = false
    private(set) var reads = 0
    init(_ time: Date?) { self.time = time }
    static func followingDeviceClock() -> AppleTimeStub {
        let stub = AppleTimeStub(nil)
        stub.followsDeviceClock = true
        return stub
    }
    func currentTime() async -> Date? { reads += 1; return followsDeviceClock ? Date() : time }
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
    private func anchors(_ group: UserDefaults) -> TrustedTime.AnchorStore {
        TrustedTime.AnchorStore(storage: DefaultsRuleStorage(defaults: group))
    }

    private var gregorian: Calendar { AppUsageConfiguration.dayCalendar }
    private func local(_ year: Int, _ month: Int, _ day: Int, _ hour: Int = 0, _ minute: Int = 0) -> Date {
        gregorian.date(from: DateComponents(year: year, month: month, day: day, hour: hour, minute: minute))!
    }
    /// A day reached in the device's time zone.
    private func reached(_ day: String) -> AppUsageConfiguration.ReachedDay {
        AppUsageConfiguration.reachedDay(day)!
    }

    // MARK: - Trusted time

    func testAppleReadingIsExactInOneBootAndNeverOverCountsAfterAReboot() {
        let anchor = TrustedTime.Anchor(server: start, uptime: 1000, boot: "A")
        XCTAssertEqual(anchor.lowerBound(uptime: 1000, boot: "A"), start)
        XCTAssertEqual(anchor.lowerBound(uptime: 4600, boot: "A"), start.addingTimeInterval(3600), "same boot: exact")
        // Rebooted: uptime restarted below the reading's. Only this boot's uptime is added.
        XCTAssertEqual(anchor.lowerBound(uptime: 30, boot: "B"), start.addingTimeInterval(30))
        XCTAssertEqual(anchor.age(uptime: 4600, boot: "A"), 3600)
        XCTAssertNil(anchor.age(uptime: 30, boot: "B"), "a reading from before a reboot has no age in this boot")
        XCTAssertNil(anchor.age(uptime: 30, boot: "A"), "the clock went back: another boot whatever the identifier")
        for broken in [TimeInterval.nan, -1, .infinity] {
            XCTAssertNil(anchor.lowerBound(uptime: broken, boot: "A"))
            XCTAssertNil(TrustedTime.Anchor(server: start, uptime: broken, boot: "A").lowerBound(uptime: 10, boot: "A"))
        }
    }

    /// The reading was made at uptime 10 h; the phone restarted and has now run 11 h. That is
    /// another boot: the reading is old, and only this boot's uptime counts.
    func testALaterBootThatRanLongerIsStillAnotherBoot() {
        let anchor = TrustedTime.Anchor(server: start, uptime: 10 * 3600, boot: "A")
        XCTAssertNil(anchor.age(uptime: 11 * 3600, boot: "B"), "was taken as an hour old")
        XCTAssertEqual(anchor.lowerBound(uptime: 11 * 3600, boot: "B"), start.addingTimeInterval(11 * 3600),
                       "only this boot's uptime is added, never the difference of two boots' uptimes")
        XCTAssertFalse(anchor.sameBoot(uptime: 11 * 3600, boot: "B"))
        // Without an identifier (an older saved reading, or none from iOS) it is another boot.
        let unknown = TrustedTime.Anchor(server: start, uptime: 10 * 3600)
        XCTAssertNil(unknown.age(uptime: 11 * 3600, boot: "B"))
        XCTAssertNil(anchor.age(uptime: 11 * 3600, boot: nil))
        XCTAssertNotNil(TrustedTime.bootSession.map { !$0.isEmpty }, "iOS gives this boot's identifier")
    }

    @MainActor func testAReadingFromALongerRunningEarlierBootIsRefreshed() throws {
        let group = try appGroup()
        let store = MemoryCommitmentStore()
        store.session = .init(startedAt: start, deadline: start.addingTimeInterval(7 * 86400))
        try anchors(group).save(.init(server: start, uptime: 10 * 3600, boot: "boot-1"))
        let controller = ProtectionController(commitmentStore: store,
            clockSource: { (self.start.addingTimeInterval(86400), 11 * 3600) },
            serverTime: AppleTimeStub(nil), shared: state(group), bootSession: { "boot-2" })
        XCTAssertTrue(controller.needsServerTime, "a reading from before a reboot is never fresh")
        XCTAssertEqual(controller.trustedNow, start.addingTimeInterval(11 * 3600))
        XCTAssertFalse(controller.hasReadingThisBoot)
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
        try store.save(.init(server: start, uptime: 5, boot: "A", carried: true))
        XCTAssertEqual(store.load(), .init(server: start, uptime: 5, boot: "A", carried: true))
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

    /// Without pinning, a trusted proxy can forge the `Date` header. In a boot that already has
    /// a reading, a new reading may only lower the time, never raise it.
    func testAReadingAheadOfThisBootsReadingIsNotTaken() {
        let saved = TrustedTime.Anchor(server: start, uptime: 1000, boot: "A")
        let hourLater = start.addingTimeInterval(3600)
        func kept(_ reading: Date, uptime: TimeInterval = 4600, boot: String = "A",
                  with anchor: TrustedTime.Anchor? = nil) -> Date? {
            TrustedTime.reconcile(reading: reading, uptime: uptime, boot: boot, with: anchor ?? saved)
                .lowerBound(uptime: uptime, boot: boot)
        }
        XCTAssertEqual(kept(start.addingTimeInterval(3 * 86400)), hourLater, "a forged date days ahead is not taken")
        XCTAssertEqual(kept(hourLater.addingTimeInterval(240)), hourLater, "nor minutes ahead")
        XCTAssertEqual(kept(hourLater.addingTimeInterval(-30)), hourLater.addingTimeInterval(-30),
                       "a lower reading is kept: that only keeps a lock longer")
        XCTAssertEqual(kept(hourLater), hourLater)
        // Another boot, or a reading carried over one: nothing exact to check against.
        XCTAssertEqual(kept(start.addingTimeInterval(3 * 86400), uptime: 50, boot: "B"),
                       start.addingTimeInterval(3 * 86400))
        let carried = TrustedTime.Anchor(server: start, uptime: 1000, boot: "A", carried: true)
        XCTAssertEqual(kept(start.addingTimeInterval(2 * 86400), with: carried), start.addingTimeInterval(2 * 86400))
        XCTAssertNil(TrustedTime.reconcile(reading: hourLater, uptime: 4600, boot: "A", with: saved).carried)
    }

    @MainActor func testAForgedAppleTimeCannotEndTheCommitmentInTheSameBoot() async throws {
        let group = try appGroup()
        let store = MemoryCommitmentStore()
        var wall = start
        var uptime: TimeInterval = 100
        let server = AppleTimeStub(start)
        await ProtectionController(commitmentStore: store, clockSource: { (wall, uptime) }, serverTime: server,
                                   shared: state(group), bootSession: { "boot-1" }).startCommitment(days: 7)
        let deadline = try XCTUnwrap(store.session?.deadline)
        // Two days later, same boot: the clock is moved past the end and a proxy forges Apple's time.
        uptime += 2 * 86400
        wall = deadline.addingTimeInterval(3600)
        server.time = deadline.addingTimeInterval(3600)
        let relaunched = ProtectionController(commitmentStore: store, clockSource: { (wall, uptime) },
                                              serverTime: server, shared: state(group), bootSession: { "boot-1" })
        await relaunched.checkServerTime()
        XCTAssertGreaterThan(server.reads, 1)
        XCTAssertEqual(relaunched.trustedNow, start.addingTimeInterval(2 * 86400))
        XCTAssertEqual(relaunched.commitmentTimeCheck, .clockAhead)
        XCTAssertTrue(relaunched.commitmentActive)
        XCTAssertEqual(anchors(group).load()?.lowerBound(uptime: uptime, boot: "boot-1"),
                       start.addingTimeInterval(2 * 86400), "the forged reading is not shared with the monitor")
    }

    func testARestatedReadingNeverMovesTheSharedTimeBack() throws {
        let store = TrustedTime.AnchorStore(storage: EphemeralRuleStorage())
        let reading = TrustedTime.Anchor(server: start, uptime: 1000, boot: "A")
        try store.save(reading)
        // Restated an hour later in the same boot: the same time now, a later start after a reboot.
        let restated = try XCTUnwrap(reading.rebased(uptime: 4600, boot: "A"))
        XCTAssertEqual(restated.server, start.addingTimeInterval(3600))
        XCTAssertNil(restated.carried, "still a reading made in this boot")
        try store.advance(to: restated, uptime: 4600, boot: "A")
        XCTAssertEqual(store.load(), restated)
        XCTAssertEqual(store.load()?.lowerBound(uptime: 20, boot: "B"), start.addingTimeInterval(3620))
        // An older restatement does not replace it.
        try store.advance(to: reading, uptime: 4600, boot: "A")
        XCTAssertEqual(store.load(), restated)
        // A reading carried over a reboot stays carried, and never replaces a fresh one.
        let carried = try XCTUnwrap(reading.rebased(uptime: 50, boot: "B"))
        XCTAssertEqual(carried.carried, true)
        XCTAssertNil(carried.age(uptime: 60, boot: "B"))
        let fresh = TrustedTime.Anchor(server: start.addingTimeInterval(50), uptime: 50, boot: "B")
        try store.save(fresh)
        try store.advance(to: carried, uptime: 50, boot: "B")
        XCTAssertEqual(store.load(), fresh)
    }

    // MARK: - Commitment end (M2.1)

    @MainActor func testMovedClockAfterRelaunchDoesNotEndTheCommitment() async throws {
        let store = MemoryCommitmentStore()
        var wall = start
        var uptime: TimeInterval = 100
        let server = AppleTimeStub(start)
        await ProtectionController(commitmentStore: store, clockSource: { (wall, uptime) }, serverTime: server)
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
        await relaunched.startCommitment(days: 1)
        XCTAssertEqual(relaunched.commitment, session, "a moved clock cannot replace the running commitment")
        XCTAssertTrue(relaunched.customSitesLocked)
    }

    @MainActor func testNetworkErrorNeverEndsTheCommitment() async throws {
        let store = MemoryCommitmentStore()
        var wall = start
        let server = AppleTimeStub(start)
        let controller = ProtectionController(commitmentStore: store, clockSource: { (wall, 100) }, serverTime: server)
        await controller.startCommitment(days: 1)
        XCTAssertNotNil(store.session)
        wall = start.addingTimeInterval(2 * 86400)
        server.time = nil
        let relaunched = ProtectionController(commitmentStore: store, clockSource: { (wall, 50) }, serverTime: server)
        await relaunched.checkServerTime()
        XCTAssertGreaterThan(server.reads, 1)
        XCTAssertTrue(relaunched.serverTimeFailed)
        XCTAssertEqual(relaunched.commitmentTimeCheck, .unverified)
        XCTAssertTrue(relaunched.commitmentActive, "offline, the commitment stays on")
        XCTAssertTrue(relaunched.needsServerTime)
    }

    @MainActor func testCommitmentEndsWhenBothClocksHavePassed() async throws {
        let store = MemoryCommitmentStore()
        var wall = start
        let server = AppleTimeStub(start)
        let controller = ProtectionController(commitmentStore: store, clockSource: { (wall, 100) }, serverTime: server)
        await controller.startCommitment(days: 1)
        let session = try XCTUnwrap(controller.commitment)
        wall = session.deadline.addingTimeInterval(60)
        server.time = session.deadline.addingTimeInterval(30)
        let relaunched = ProtectionController(commitmentStore: store, clockSource: { (wall, 10) }, serverTime: server)
        await relaunched.checkServerTime()
        XCTAssertEqual(relaunched.commitmentTimeCheck, .passed)
        XCTAssertFalse(relaunched.commitmentActive)
        XCTAssertFalse(relaunched.customSitesLocked)
        XCTAssertEqual(store.session?.endConfirmedAt, session.deadline.addingTimeInterval(30),
                       "the confirmed end is saved with the commitment")
        XCTAssertFalse(relaunched.needsServerTime, "an ended commitment needs no more server reads")
        await relaunched.startCommitment(days: 7)
        XCTAssertNotEqual(relaunched.commitment?.deadline, session.deadline, "a new commitment can start after the real end")
        XCTAssertNil(relaunched.commitment?.endConfirmedAt)
        XCTAssertTrue(relaunched.commitmentActive)
    }

    @MainActor func testSavedAppleReadingSettlesTheEndOfflineEvenAfterAReboot() throws {
        let group = try appGroup()
        let store = MemoryCommitmentStore()
        let deadline = start.addingTimeInterval(86400)
        store.session = .init(startedAt: start, deadline: deadline)
        // The last reading was an hour before the end, at uptime 1000, in the previous boot.
        try anchors(group).save(.init(server: deadline.addingTimeInterval(-3600), uptime: 1000, boot: "boot-1"))
        let wall = deadline.addingTimeInterval(7200)
        var uptime: TimeInterval = 10
        let rebooted = ProtectionController(commitmentStore: store, clockSource: { (wall, uptime) },
                                            serverTime: AppleTimeStub(nil), shared: state(group),
                                            bootSession: { "boot-2" })
        XCTAssertEqual(rebooted.commitmentTimeCheck, .clockAhead, "after a reboot only this boot's time counts")
        uptime = 3600 + 60
        let later = ProtectionController(commitmentStore: store, clockSource: { (wall, uptime) },
                                         serverTime: AppleTimeStub(nil), shared: state(group),
                                         bootSession: { "boot-2" })
        XCTAssertEqual(later.commitmentTimeCheck, .passed, "this boot alone has run past the end")
        XCTAssertFalse(later.commitmentActive)
    }

    /// The end passes on a reading carried forward in the same boot; the person then turns the
    /// filter off. After a restart, offline, the commitment must stay ended and the filter off.
    @MainActor func testAConfirmedEndSurvivesARebootAndTheFilterStaysOff() async throws {
        let group = try appGroup()
        let store = MemoryCommitmentStore()
        let deadline = start.addingTimeInterval(7 * 86400)
        store.session = .init(startedAt: start, deadline: deadline, screenTimeFilter: true)
        try anchors(group).save(.init(server: deadline.addingTimeInterval(-3 * 3600), uptime: 10_000, boot: "boot-1"))
        let screenTime = FakeScreenTime(authorized: true, filter: .auto([]))
        var wall = deadline.addingTimeInterval(60)
        var uptime: TimeInterval = 10_000 + 3 * 3600 + 60
        var boot = "boot-1"
        let offline = AppleTimeStub(nil)
        func controller() -> ProtectionController {
            ProtectionController(commitmentStore: store, clockSource: { (wall, uptime) }, serverTime: offline,
                                 shared: state(group), screenTimeFilter: screenTime, bootSession: { boot })
        }
        let sameBoot = controller()
        XCTAssertEqual(sameBoot.commitmentTimeCheck, .passed)
        XCTAssertEqual(store.session?.endConfirmed, true, "saved with the commitment")
        sameBoot.disablePersonalScreenTime()
        XCTAssertNil(screenTime.filter)
        // Restart the iPhone and open Hisn in airplane mode.
        wall = deadline.addingTimeInterval(600); uptime = 30; boot = "boot-2"
        let rebooted = controller()
        XCTAssertEqual(rebooted.commitmentTimeCheck, .passed)
        XCTAssertFalse(rebooted.commitmentActive)
        await rebooted.refresh()
        XCTAssertNil(screenTime.filter, "a filter turned off after the end is never turned back on")
        XCTAssertFalse(rebooted.customSitesLocked)
        XCTAssertNil(LayerAlertPolicy.loadWatch(DefaultsRuleStorage(defaults: group)))
        // The reading restated at the end also survives the restart.
        XCTAssertGreaterThanOrEqual(try XCTUnwrap(anchors(group).load()?.lowerBound(uptime: 30, boot: "boot-2")),
                                    deadline)
        // Even with no shared reading at all, the saved confirmation keeps it ended.
        group.removeObject(forKey: TrustedTime.AnchorStore.key)
        let noReading = controller()
        XCTAssertNil(noReading.trustedNow)
        XCTAssertFalse(noReading.commitmentActive)
        await noReading.refresh()
        XCTAssertNil(screenTime.filter)
        // A clock set back after the end does not bring it back either.
        wall = deadline.addingTimeInterval(-86400)
        XCTAssertFalse(controller().commitmentActive)
    }

    func testAConfirmedEndCountsOnlyWhenEveryCopyCarriesIt() throws {
        typealias S = CommitmentPolicy.Session
        let deadline = start.addingTimeInterval(7 * 86400)
        let running = S(startedAt: start, deadline: deadline, screenTimeFilter: true)
        XCTAssertNil(running.confirmingEnd(at: deadline.addingTimeInterval(-1)), "never before the end")
        let ended = try XCTUnwrap(running.confirmingEnd(at: deadline.addingTimeInterval(5)))
        XCTAssertTrue(ended.endConfirmed)
        XCTAssertTrue(ended.isValid())
        XCTAssertEqual(ended.screenTimeFilter, true)
        XCTAssertFalse(S(startedAt: start, deadline: deadline, endConfirmedAt: deadline.addingTimeInterval(-1)).isValid())
        func data(_ session: S) throws -> Data { try JSONEncoder().encode(session) }
        XCTAssertEqual(try CommitmentPolicy.restoredSession(from: [data(ended), data(ended)]), ended)
        XCTAssertEqual(try CommitmentPolicy.restoredSession(from: [nil, data(ended)]), ended)
        XCTAssertEqual(try CommitmentPolicy.restoredSession(from: [data(running), data(ended)]), running,
                       "one copy alone cannot end a commitment")
        let forged = S(startedAt: start, deadline: deadline.addingTimeInterval(1),
                       endConfirmedAt: deadline.addingTimeInterval(1))
        XCTAssertEqual(try CommitmentPolicy.restoredSession(from: [data(forged), data(running)])?.endConfirmed, false)
        XCTAssertNil(ended.extending(by: 7 * 86400)?.endConfirmedAt, "a later end is not confirmed")
        XCTAssertEqual(ended.choosingScreenTimeFilter(), ended)
        let legacy = try JSONDecoder().decode(S.self, from: JSONEncoder().encode(LegacySession(startedAt: start,
                                                                                               deadline: deadline)))
        XCTAssertNil(legacy.endConfirmedAt)
        XCTAssertFalse(legacy.endConfirmed)
    }

    /// A start date from a clock set back would end the commitment early in real time.
    @MainActor func testClockSetBackBeforeStartingDoesNotShortenTheCommitment() async throws {
        let store = MemoryCommitmentStore()
        let server = AppleTimeStub(start)
        let controller = ProtectionController(commitmentStore: store,
            clockSource: { (self.start.addingTimeInterval(-6 * 86400), 100) }, serverTime: server)
        XCTAssertFalse(controller.hasReadingThisBoot)
        await controller.startCommitment(days: 7)
        XCTAssertEqual(server.reads, 1, "Apple's time is read before starting")
        let session = try XCTUnwrap(controller.commitment)
        XCTAssertEqual(session.startedAt, start, "starts from Apple's time when the device clock is behind")
        XCTAssertEqual(session.deadline, start.addingTimeInterval(7 * 86400))
        XCTAssertNil(controller.commitmentStartNote)
    }

    @MainActor func testWithoutAppleTimeTheCommitmentDoesNotStartAndSaysWhy() async throws {
        let group = try appGroup()
        let store = MemoryCommitmentStore()
        // A reading from before the last restart is not enough: it may be days behind.
        try anchors(group).save(.init(server: start.addingTimeInterval(-6 * 86400), uptime: 9000, boot: "boot-1"))
        let server = AppleTimeStub(nil)
        let controller = ProtectionController(commitmentStore: store,
            clockSource: { (self.start.addingTimeInterval(-6 * 86400), 100) }, serverTime: server,
            shared: state(group), bootSession: { "boot-2" })
        await controller.startCommitment(days: 7)
        XCTAssertEqual(server.reads, 1)
        XCTAssertNil(controller.commitment)
        XCTAssertNil(store.session)
        XCTAssertEqual(controller.commitmentStartNote, "commitment.startNeedsTime")
        XCTAssertFalse(controller.busy)
        // Online again: it starts from Apple's time.
        server.time = start
        await controller.startCommitment(days: 7)
        XCTAssertEqual(store.session?.startedAt, start)
        XCTAssertNil(controller.commitmentStartNote)
        // A reading made in this boot is enough: no second read.
        let reads = server.reads
        let other = MemoryCommitmentStore()
        await ProtectionController(commitmentStore: other,
            clockSource: { (self.start, 100) }, serverTime: server,
            shared: state(group), bootSession: { "boot-2" }).startCommitment(days: 1)
        XCTAssertNotNil(other.session)
        XCTAssertEqual(server.reads, reads)
    }

    /// With Apple's time 100 s before the end, the commitment has not ended, whatever the device
    /// clock says: the reading allowance is only for the monitor's budget day.
    @MainActor func testTheCommitmentEndHasNoReadingAllowance() async throws {
        let group = try appGroup()
        let store = MemoryCommitmentStore()
        let deadline = start.addingTimeInterval(86400)
        store.session = .init(startedAt: start, deadline: deadline)
        try anchors(group).save(.init(server: deadline.addingTimeInterval(-100), uptime: 500, boot: "boot-1"))
        let controller = ProtectionController(commitmentStore: store,
            clockSource: { (deadline.addingTimeInterval(86400), 500) }, serverTime: AppleTimeStub(nil),
            shared: state(group), bootSession: { "boot-1" })
        XCTAssertEqual(controller.commitmentTimeCheck, .clockAhead)
        XCTAssertTrue(controller.commitmentActive)
        XCTAssertNil(store.session?.endConfirmedAt)
        let lost = observed(filter: false)
        XCTAssertEqual(LayerAlertPolicy.decide(watch: watch(deadline: deadline), deviceNow: deadline.addingTimeInterval(86400),
                                               trustedNow: deadline.addingTimeInterval(-100), observed: lost,
                                               alreadyPosted: []).post, [.webFilter], "alerts run until the real end")
        // The budget day keeps its allowance.
        let day = reached("2026-10-26")
        XCTAssertEqual(AppUsageConfiguration.budgetDay(reached: day, deviceNow: day.end,
                                                       trustedNow: day.end.addingTimeInterval(-100)), .over)
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
        let controller = ProtectionController(commitmentStore: store, serverTime: AppleTimeStub.followingDeviceClock(),
                                              screenTimeFilter: screenTime)
        await controller.startCommitment(days: 7)
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
        let controller = ProtectionController(commitmentStore: store, serverTime: AppleTimeStub.followingDeviceClock(),
                                              screenTimeFilter: screenTime)
        controller.disablePersonalScreenTime()
        XCTAssertNil(screenTime.filter)
        await controller.startCommitment(days: 7)
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
        let controller = ProtectionController(commitmentStore: store, serverTime: AppleTimeStub.followingDeviceClock(),
                                              screenTimeFilter: screenTime)
        await controller.startCommitment(days: 7)
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
        let day = reached("2026-10-26")
        let nextMorning = local(2026, 10, 27, 9)
        XCTAssertEqual(C.budgetDay(reached: nil, deviceNow: nextMorning, trustedNow: nil), .notReached)
        XCTAssertEqual(C.budgetDay(reached: day, deviceNow: local(2026, 10, 26, 23, 59), trustedNow: nil), .reached)
        XCTAssertEqual(C.budgetDay(reached: day, deviceNow: nextMorning, trustedNow: local(2026, 10, 26, 15)),
                       .unverified, "the date was moved forward: Apple's time is still the reached day")
        XCTAssertEqual(C.budgetDay(reached: day, deviceNow: nextMorning, trustedNow: nil), .unverified,
                       "no Apple time inside the extension: keep the shield")
        XCTAssertEqual(C.budgetDay(reached: day, deviceNow: nextMorning, trustedNow: local(2026, 10, 27, 0, 1)), .over)
        XCTAssertEqual(C.budgetDay(reached: day, deviceNow: local(2026, 10, 27),
                                   trustedNow: local(2026, 10, 27).addingTimeInterval(-2)), .over,
                       "an honest midnight with a reading a moment behind is the new day")
        XCTAssertEqual(C.budgetDay(reached: day, deviceNow: local(2026, 10, 25, 12), trustedNow: nextMorning),
                       .reached, "a clock set back keeps the shield")
        XCTAssertEqual(C.budgetDay(reached: .init(day: "damaged", end: nextMorning), deviceNow: nextMorning,
                                   trustedNow: nextMorning), .unverified, "an unreadable day never clears a shield")
        XCTAssertEqual(C.budgetDay(reached: .init(day: "2026-10-26", end: local(2026, 12, 1)), deviceNow: nextMorning,
                                   trustedNow: nextMorning), .unverified, "nor does an end that is not that day's")
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

    /// A marker far ahead (written under a clock moved far forward, or an old calendar's year)
    /// would otherwise keep the shield every day until that date.
    func testAMarkerFarAheadDoesNotKeepTheShieldForever() {
        typealias C = AppUsageConfiguration
        XCTAssertTrue(C.budgetReached(mode: .dailyBudget, reachedDay: "2026-10-28", today: "2026-10-26"))
        XCTAssertFalse(C.budgetReached(mode: .dailyBudget, reachedDay: "2026-10-29", today: "2026-10-26"))
        XCTAssertFalse(C.budgetReached(mode: .dailyBudget, reachedDay: "2569-10-26", today: "2026-10-26"))
        XCTAssertFalse(C.budgetReached(mode: .dailyBudget, reachedDay: "5787-2-15", today: "2026-10-26"))
        let noon = local(2026, 10, 26, 12)
        XCTAssertEqual(C.budgetDay(reached: reached("2026-10-27"), deviceNow: noon, trustedNow: nil), .reached,
                       "a clock set back a day keeps the shield")
        XCTAssertEqual(C.budgetDay(reached: reached("2026-10-28"), deviceNow: noon, trustedNow: nil), .reached)
        XCTAssertEqual(C.budgetDay(reached: reached("2026-11-5"), deviceNow: noon, trustedNow: nil), .notReached)
        XCTAssertEqual(C.budgetDay(reached: reached("2569-10-26"), deviceNow: noon, trustedNow: nil), .notReached)
        XCTAssertEqual(C.budgetDay(reached: reached("2026-11-5"), deviceNow: local(2026, 11, 4, 12),
                                   trustedNow: nil), .reached, "honoured again once it is near")
        XCTAssertEqual(C.heldDayAction(held: true, mode: .dailyBudget, reached: reached("2569-10-26"),
                                       deviceNow: noon, trustedNow: noon), .clearShield)
    }

    func testBudgetDaysAreGregorianWhateverCalendarThePersonReads() {
        XCTAssertEqual(AppUsageConfiguration.dayCalendar.identifier, .gregorian)
        XCTAssertEqual(AppUsageConfiguration.dayCalendar.timeZone, .current)
        XCTAssertEqual(AppUsageConfiguration.day(local(2026, 10, 26, 12)), "2026-10-26")
    }

    /// Markers saved before budget days were Gregorian named the day in the person's calendar:
    /// "2569-10-26" (Buddhist) or "5787-2-15" (Hebrew) read as Gregorian are centuries ahead.
    func testLegacyCalendarMarkersAreMigratedOrExpiredNeverFarFuture() throws {
        typealias C = AppUsageConfiguration
        func personal(_ identifier: Calendar.Identifier) -> Calendar {
            var calendar = Calendar(identifier: identifier)
            calendar.timeZone = .current
            return calendar
        }
        func name(_ date: Date, _ calendar: Calendar) -> String {
            let c = calendar.dateComponents([.year, .month, .day], from: date)
            return "\(c.year!)-\(c.month!)-\(c.day!)"
        }
        let reachedOn = local(2026, 10, 26, 12)
        let later = local(2026, 10, 30, 12)
        for identifier in [Calendar.Identifier.buddhist, .hebrew, .gregorian, .japanese, .islamicUmmAlQura] {
            let calendar = personal(identifier)
            let legacy = name(reachedOn, calendar)
            // Days later: expired, not a far-future day and not damage.
            let defaults = try appGroup()
            defaults.set(legacy, forKey: C.legacyReachedKeys[0])
            defaults.set(legacy, forKey: C.legacyReachedKeys[1])
            XCTAssertNil(try C.reachedRecord(defaults, now: later, personalCalendar: calendar), "\(identifier)")
            XCTAssertNil(defaults.object(forKey: C.legacyReachedKeys[0]), "removed once read")
            XCTAssertFalse(try C.budgetReached(mode: .dailyBudget, defaults: defaults, today: C.day(later)))
            // The same day: it becomes today's marker, with today's end.
            let today = try appGroup()
            today.set(legacy, forKey: C.legacyReachedKeys[0])
            let migrated = try XCTUnwrap(try C.reachedRecord(today, now: reachedOn, personalCalendar: calendar),
                                         "\(identifier)")
            XCTAssertEqual(migrated, reached("2026-10-26"))
            XCTAssertEqual(try C.reachedRecord(today), migrated, "saved in the new format")
            XCTAssertNil(today.object(forKey: C.legacyReachedKeys[0]))
        }
        // The review's literal markers, and a Hebrew thirteenth month or damage, never keep a shield.
        for legacy: Any in ["2569-10-26", "5787-2-15", "5787-13-1", Data("x".utf8)] {
            let defaults = try appGroup()
            defaults.set(legacy, forKey: C.legacyReachedKeys[1])
            XCTAssertNil(try C.reachedRecord(defaults, now: later, personalCalendar: personal(.gregorian)))
            XCTAssertFalse(try C.budgetReached(mode: .dailyBudget, defaults: defaults, today: "2026-10-30"))
        }
        // A new marker wins over leftovers, which are removed.
        let defaults = try appGroup()
        defaults.set("2569-10-26", forKey: C.legacyReachedKeys[0])
        try C.markBudgetReached(defaults, today: "2026-10-30")
        XCTAssertNil(defaults.object(forKey: C.legacyReachedKeys[0]))
        XCTAssertEqual(try C.reachedDay(defaults), "2026-10-30")
        try C.clearBudgetMarker(defaults)
        XCTAssertNil(try C.reachedRecord(defaults))
    }

    /// Reached at 14:55 in a zone west of the device's, then the zone is changed: the day still
    /// ends at that zone's midnight, not at the new zone's earlier one.
    func testTheReachedDayEndsWhereItWasReachedEvenAfterAZoneChange() throws {
        typealias C = AppUsageConfiguration
        let defaults = try appGroup()
        let west = TimeZone(secondsFromGMT: -12 * 3600)!
        try C.markBudgetReached(defaults, today: "2026-10-26", timeZone: west)
        let saved = try XCTUnwrap(C.reachedRecord(defaults))
        let westEnd = try XCTUnwrap(C.dayEnd("2026-10-26", in: west))
        let hereEnd = try XCTUnwrap(C.dayEnd("2026-10-26", in: .current))
        XCTAssertEqual(saved.end, westEnd)
        XCTAssertLessThan(hereEnd, westEnd)
        let between = hereEnd.addingTimeInterval(min(3600, westEnd.timeIntervalSince(hereEnd) / 2))
        XCTAssertEqual(C.budgetDay(reached: saved, deviceNow: between, trustedNow: between), .reached,
                       "both clocks are past this zone's midnight, not the reached day's end")
        C.markHeld(defaults, day: saved.day)
        XCTAssertEqual(C.heldDayAction(held: true, mode: .dailyBudget, reached: saved, deviceNow: between,
                                       trustedNow: between), .forget)
        let after = westEnd.addingTimeInterval(60)
        XCTAssertEqual(C.budgetDay(reached: saved, deviceNow: after, trustedNow: after), .over)
    }

    /// Reached Oct 20; cleared at Oct 21 midnight with Apple's time. After a reboot the saved
    /// reading under-counts; at Oct 22 midnight offline the old day must not shield again.
    func testASettledDayIsNeverShieldedAgain() throws {
        typealias C = AppUsageConfiguration
        let defaults = try appGroup()
        try C.markBudgetReached(defaults, today: "2026-10-20")
        let day = try XCTUnwrap(C.reachedRecord(defaults))
        let midnight = local(2026, 10, 21).addingTimeInterval(30)
        let first = C.budgetDay(reached: day, deviceNow: midnight, trustedNow: midnight)
        XCTAssertEqual(first, .over)
        XCTAssertFalse(C.intervalStarted(defaults, reached: day, decision: first), "cleared")
        XCTAssertNil(try C.reachedRecord(defaults), "the day is settled")
        let next = try C.reachedRecord(defaults)
        let offline = C.budgetDay(reached: next, deviceNow: local(2026, 10, 22), trustedNow: local(2026, 10, 20, 16))
        XCTAssertEqual(offline, .notReached)
        XCTAssertFalse(C.intervalStarted(defaults, reached: next, decision: offline), "never shielded again")
        XCTAssertNil(C.heldDay(defaults))
        // Only that day is settled: a newer one written meanwhile stays.
        try C.markBudgetReached(defaults, today: "2026-10-22")
        XCTAssertFalse(C.settle(defaults, reached: day))
        XCTAssertEqual(try C.reachedDay(defaults), "2026-10-22")
        // The other decisions keep or hold the shield, as before.
        let today = try XCTUnwrap(C.reachedRecord(defaults))
        XCTAssertTrue(C.intervalStarted(defaults, reached: today, decision: .reached))
        XCTAssertTrue(C.intervalStarted(defaults, reached: today, decision: .unverified))
        XCTAssertEqual(C.heldDay(defaults), "2026-10-22")
        XCTAssertNotNil(try C.reachedRecord(defaults), "a held day is not settled")
        // The app settles a held day once it is over on both clocks, and not before.
        let state = MonitorSharedState(defaults: defaults)
        state.settleReachedDay(deviceNow: local(2026, 10, 23, 9), trustedNow: local(2026, 10, 22, 18))
        XCTAssertNotNil(try C.reachedRecord(defaults))
        state.settleReachedDay(deviceNow: local(2026, 10, 23, 9), trustedNow: local(2026, 10, 23, 9))
        XCTAssertNil(try C.reachedRecord(defaults))
    }

    func testTheAppFinishesAHeldDayOnlyWithAppleTime() {
        typealias C = AppUsageConfiguration
        let nextMorning = local(2026, 10, 27, 9)
        func action(held: Bool = true, mode: C.Mode? = .dailyBudget, reached day: String? = "2026-10-26",
                    trusted: Date?) -> C.HeldDayAction {
            C.heldDayAction(held: held, mode: mode, reached: day.map { self.reached($0) }, deviceNow: nextMorning,
                            trustedNow: trusted)
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
        let server = AppleTimeStub(local(2026, 10, 27, 9))
        let controller = ProtectionController(commitmentStore: MemoryCommitmentStore(),
            clockSource: { (self.local(2026, 10, 27, 9), 100) }, serverTime: server, shared: state(group))
        XCTAssertTrue(controller.needsServerTime)
        await controller.checkServerTime()
        XCTAssertEqual(server.reads, 1)
        XCTAssertNil(AppUsageConfiguration.heldDay(group), "no daily budget is saved, so nothing is held")
        XCTAssertEqual(anchors(group).load()?.server, local(2026, 10, 27, 9), "the reading is shared with the monitor")
        XCTAssertEqual(anchors(group).load()?.boot, TrustedTime.bootSession, "with the boot it was read in")
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
        memory.settleReachedDay(deviceNow: start, trustedNow: start)
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
        let controller = ProtectionController(commitmentStore: MemoryCommitmentStore(),
                                              serverTime: AppleTimeStub.followingDeviceClock(), shared: state(group),
                                              notifications: notifications)
        await controller.refresh()
        await controller.startCommitment(days: 7)
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
        let controller = ProtectionController(commitmentStore: MemoryCommitmentStore(),
                                              serverTime: AppleTimeStub.followingDeviceClock(), shared: state(group),
                                              notifications: FakeNotifications(granted: false))
        await controller.startCommitment(days: 1)
        XCTAssertNotNil(controller.commitment)
        await controller.enableLayerAlerts()
        XCTAssertEqual(controller.layerAlerts, .notAllowed)
        XCTAssertEqual(LayerAlertPolicy.loadWatch(DefaultsRuleStorage(defaults: group))?.alertsOn, false)
    }

    @MainActor func testWatchIsRemovedOnceTheCommitmentEndsOnBothClocks() async throws {
        let group = try appGroup()
        let store = MemoryCommitmentStore()
        var wall = start
        let server = AppleTimeStub(start)
        let controller = ProtectionController(commitmentStore: store, clockSource: { (wall, 100) },
                                              serverTime: server, shared: state(group))
        await controller.startCommitment(days: 1)
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
                        "commitment.startNeedsTime",
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
