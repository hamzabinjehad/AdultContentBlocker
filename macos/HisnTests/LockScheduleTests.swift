import XCTest
@testable import Hisn

/// The daily lock: which window is open when, what counts as weakening it,
/// and the wait a weaker schedule serves.
final class LockScheduleTests: XCTestCase {

    private var calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Europe/Berlin")!
        return c
    }()

    /// 26 September 2026 at hh:mm, Berlin.
    private func at(_ hour: Int, _ minute: Int = 0, day: Int = 26) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: 9, day: day, hour: hour, minute: minute))!
    }

    private let night = LockSchedule(enabled: true, start: 22 * 60, end: 7 * 60, strict: false)

    // MARK: - The window

    func testANightWindowCrossesMidnight() {
        XCTAssertEqual(night.window(containing: at(23, 30), calendar: calendar),
                       DateInterval(start: at(22), end: at(7, day: 27)))
        XCTAssertEqual(night.window(containing: at(6, 59), calendar: calendar),
                       DateInterval(start: at(22, day: 25), end: at(7)))
        XCTAssertNil(night.window(containing: at(7), calendar: calendar), "it ends at 07:00 exactly")
        XCTAssertNil(night.window(containing: at(12), calendar: calendar))
        XCTAssertNotNil(night.window(containing: at(22), calendar: calendar), "and begins at 22:00 exactly")
    }

    func testADayWindow() {
        let work = LockSchedule(enabled: true, start: 9 * 60, end: 17 * 60, strict: true)
        XCTAssertEqual(work.window(containing: at(10), calendar: calendar),
                       DateInterval(start: at(9), end: at(17)))
        XCTAssertNil(work.window(containing: at(8, 59), calendar: calendar))
    }

    func testAnOffScheduleHasNoWindow() {
        var off = night
        off.enabled = false
        XCTAssertNil(off.window(containing: at(23), calendar: calendar))
    }

    // MARK: - When the tick starts a lock

    func testAnOpenWindowWithNoLockStartsOne() {
        XCTAssertEqual(night.lockNeeded(at: at(22, 1), lockedUntil: nil, calendar: calendar)?.end,
                       at(7, day: 27))
    }

    func testAShorterLockIsLengthenedAndALongerOneLeftAlone() {
        XCTAssertNotNil(night.lockNeeded(at: at(23), lockedUntil: at(23, 30), calendar: calendar))
        XCTAssertNil(night.lockNeeded(at: at(23), lockedUntil: at(7, day: 27), calendar: calendar),
                     "a lock already ending with the window is not restarted every tick")
        XCTAssertNil(night.lockNeeded(at: at(23), lockedUntil: at(12, day: 30), calendar: calendar))
    }

    func testTheLastSecondsOfAWindowStartNothing() {
        XCTAssertNil(night.lockNeeded(at: at(6, 59).addingTimeInterval(30), lockedUntil: nil,
                                      calendar: calendar))
    }

    // MARK: - Weakening

    func testWhatCountsAsWeaker() {
        var off = night; off.enabled = false
        XCTAssertTrue(night.isLoosened(by: off), "switching it off")
        XCTAssertTrue(night.isLoosened(by: LockSchedule(enabled: true, start: 23 * 60, end: 7 * 60, strict: false)),
                      "starting later")
        XCTAssertTrue(night.isLoosened(by: LockSchedule(enabled: true, start: 22 * 60, end: 6 * 60, strict: false)),
                      "ending earlier")
        XCTAssertTrue(night.isLoosened(by: LockSchedule(enabled: true, start: 8 * 60, end: 17 * 60, strict: false)),
                      "moving it to the day")
        var strictNight = night; strictNight.strict = true
        XCTAssertTrue(strictNight.isLoosened(by: night), "dropping strict")
    }

    func testWhatCountsAsStronger() {
        XCTAssertFalse(night.isLoosened(by: LockSchedule(enabled: true, start: 21 * 60, end: 8 * 60, strict: false)),
                       "a wider window")
        var strictNight = night; strictNight.strict = true
        XCTAssertFalse(night.isLoosened(by: strictNight), "adding strict")
        var off = night; off.enabled = false
        XCTAssertFalse(off.isLoosened(by: night), "switching it on")
        XCTAssertFalse(off.isLoosened(by: off), "an off schedule cannot get weaker")
    }

    // MARK: - The store

    private var namespace: String!

    override func setUp() {
        super.setUp()
        namespace = TestNamespace.make()
        LockStore.appGroup = namespace
    }

    override func tearDown() {
        TestNamespace.dispose(namespace)
        super.tearDown()
    }

    func testStrongerIsSavedAtOnce() {
        XCTAssertEqual(ScheduleStore.request(night, now: at(12)), .applied)
        XCTAssertEqual(ScheduleStore.current(), night)
        XCTAssertNil(ScheduleStore.pending())
    }

    func testWeakerWaitsADay() {
        ScheduleStore.request(night, now: at(12))
        var off = night; off.enabled = false
        XCTAssertEqual(ScheduleStore.request(off, now: at(21, 59)), .waiting(until: at(21, 59, day: 27)))
        XCTAssertEqual(ScheduleStore.current(), night, "tonight's lock still starts at 22:00")
        XCTAssertFalse(ScheduleStore.applyDue(now: at(21, 58, day: 27)))
        XCTAssertTrue(ScheduleStore.applyDue(now: at(21, 59, day: 27)))
        XCTAssertEqual(ScheduleStore.current(), off)
        XCTAssertNil(ScheduleStore.pending())
    }

    func testANewWeakerRequestRestartsTheWait() {
        ScheduleStore.request(night, now: at(12))
        let later = LockSchedule(enabled: true, start: 23 * 60, end: 7 * 60, strict: false)
        ScheduleStore.request(later, now: at(12))
        var off = night; off.enabled = false
        XCTAssertEqual(ScheduleStore.request(off, now: at(20)), .waiting(until: at(20, day: 27)),
                       "the bigger change does not inherit the smaller one's head start")
    }

    func testAStrongerRequestDropsAWaitingWeakerOne() {
        ScheduleStore.request(night, now: at(12))
        var off = night; off.enabled = false
        ScheduleStore.request(off, now: at(12))
        var strictNight = night; strictNight.strict = true
        XCTAssertEqual(ScheduleStore.request(strictNight, now: at(13)), .applied)
        XCTAssertNil(ScheduleStore.pending())
        XCTAssertEqual(ScheduleStore.current(), strictNight)
    }

    func testCancellingAWaitingChangeIsAlwaysAllowed() {
        ScheduleStore.request(night, now: at(12))
        var off = night; off.enabled = false
        ScheduleStore.request(off, now: at(12))
        ScheduleStore.cancelPending()
        XCTAssertNil(ScheduleStore.pending())
        XCTAssertEqual(ScheduleStore.current(), night)
    }
}
