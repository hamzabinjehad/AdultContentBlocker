import FamilyControls
import XCTest
@testable import HisnMobile

/// The pause rule is the only mode whose shield can be opened, so its budget is
/// the part that has to be exact: opens are spent once, a damaged record never
/// invents a fresh one, and a session never outlives the day it started.
final class PauseRuleTests: XCTestCase {
    private let utc = TimeZone(secondsFromGMT: 0)!

    private func at(_ day: Int, _ hour: Int = 9, _ minute: Int = 0) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = utc
        return calendar.date(from: DateComponents(year: 2026, month: 11, day: day,
                                                  hour: hour, minute: minute))!
    }

    // MARK: - Budget

    func testNoRecordMeansTheFullDay() {
        XCTAssertEqual(PauseRule.budget(nil, opens: 3, now: at(6), timeZone: utc),
                       .opens(remaining: 3))
        XCTAssertEqual(PauseRule.budget(nil, opens: 3, now: at(6), timeZone: utc).remaining, 3)
        XCTAssertTrue(PauseRule.budget(nil, opens: 1, now: at(6), timeZone: utc).canOpen)
    }

    func testSpendingAnOpenLeavesTheRest() {
        let first = PauseRule.consume(nil, opens: 3, now: at(6), timeZone: utc)
        XCTAssertEqual(first, PauseRule.DayRecord(day: "2026-11-6", used: 1, at: at(6)))
        let second = PauseRule.consume(first, opens: 3, now: at(6, 10), timeZone: utc)
        XCTAssertEqual(second?.used, 2)
        XCTAssertEqual(PauseRule.budget(second, opens: 3, now: at(6, 10), timeZone: utc),
                       .opens(remaining: 1))
        let third = PauseRule.consume(second, opens: 3, now: at(6, 11), timeZone: utc)
        XCTAssertEqual(third?.used, 3)
        XCTAssertEqual(PauseRule.budget(third, opens: 3, now: at(6, 11), timeZone: utc), .exhausted)
        XCTAssertNil(PauseRule.consume(third, opens: 3, now: at(6, 12), timeZone: utc))
    }

    func testYesterdaysRecordNeverClosesToday() {
        let yesterday = PauseRule.DayRecord(day: "2026-11-5", used: 3, at: at(5, 22))
        XCTAssertEqual(PauseRule.budget(yesterday, opens: 3, now: at(6), timeZone: utc),
                       .opens(remaining: 3))
        XCTAssertEqual(PauseRule.consume(yesterday, opens: 3, now: at(6), timeZone: utc),
                       PauseRule.DayRecord(day: "2026-11-6", used: 1, at: at(6)))
        XCTAssertEqual(PauseRule.usedToday(yesterday, now: at(6), timeZone: utc), 0)
    }

    func testTheDayKeyIsGregorianInTheDevicesZone() {
        // A person reading the Buddhist calendar must not get a different budget
        // day: the key comes from the Gregorian calendar in the current zone.
        let zone = TimeZone(identifier: "Asia/Riyadh")!
        let now = at(6, 23, 30)
        XCTAssertEqual(PauseRule.dayKey(now, timeZone: zone), "2026-11-7")
        XCTAssertEqual(PauseRule.dayKey(now, timeZone: TimeZone(identifier: "America/Los_Angeles")!),
                       "2026-11-6")
        XCTAssertTrue(AppUsageConfiguration.isValidDayName("2026-11-6"))
        XCTAssertFalse(AppUsageConfiguration.isValidDayName("2026-11-31"))
        XCTAssertFalse(AppUsageConfiguration.isValidDayName("2026-02-30"))
        XCTAssertFalse(AppUsageConfiguration.isValidDayName(""))
    }

    func testOpensAreClampedToTheEditorsRange() {
        XCTAssertEqual(PauseRule.budget(nil, opens: 0, now: at(6), timeZone: utc), .opens(remaining: 1))
        XCTAssertEqual(PauseRule.budget(nil, opens: 99, now: at(6), timeZone: utc), .opens(remaining: 10))
    }

    // MARK: - Saved records

    private func suite() throws -> UserDefaults {
        let name = "hisn.tests.pause." + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        addTeardownBlock { defaults.removePersistentDomain(forName: name) }
        return defaults
    }

    func testMirroredDaySurvivesOneBadCopy() throws {
        let defaults = try suite()
        let store = PauseRuleStore(defaults: defaults)
        XCTAssertEqual(store.loadDay(), .absent)
        XCTAssertTrue(store.saveDay(PauseRule.DayRecord(day: "2026-11-6", used: 2, at: at(6))))
        XCTAssertEqual(store.dayRecord()?.used, 2)

        // One copy damaged: the record still reads, and is not mistaken for fresh.
        defaults.set(Data("not json".utf8), forKey: PauseRuleStore.dayKey)
        XCTAssertEqual(store.dayRecord()?.used, 2)
        XCTAssertEqual(store.budget(opens: 3, now: at(6), timeZone: utc), .opens(remaining: 1))
    }

    func testBothBadCopiesReadAsUnreadableNotAsAFreshDay() throws {
        let defaults = try suite()
        let store = PauseRuleStore(defaults: defaults)
        for key in [PauseRuleStore.dayKey, PauseRuleStore.dayMirrorKey] {
            defaults.set(Data("not json".utf8), forKey: key)
        }
        XCTAssertEqual(store.loadDay(), .damaged)
        XCTAssertEqual(store.budget(opens: 3, now: at(6), timeZone: utc), .unreadable)
        XCTAssertEqual(store.budget(opens: 3, now: at(6), timeZone: utc).remaining, 0)
        XCTAssertFalse(store.budget(opens: 3, now: at(6), timeZone: utc).canOpen)
    }

    func testCopiesThatDisagreeOnTheDayAreUnreadable() throws {
        let defaults = try suite()
        let store = PauseRuleStore(defaults: defaults)
        let first = try JSONEncoder().encode(PauseRule.DayRecord(day: "2026-11-6", used: 1, at: at(6)))
        let other = try JSONEncoder().encode(PauseRule.DayRecord(day: "2026-11-5", used: 1, at: at(5)))
        defaults.set(first, forKey: PauseRuleStore.dayKey)
        defaults.set(other, forKey: PauseRuleStore.dayMirrorKey)
        XCTAssertEqual(store.loadDay(), .damaged)
    }

    func testARecordBeyondTheCeilingIsRejected() throws {
        let bad = PauseRule.DayRecord(day: "2026-11-6", used: PauseRule.DayRecord.maximumUsed + 1,
                                      at: at(6))
        XCTAssertFalse(bad.isValid)
        let negative = PauseRule.DayRecord(day: "2026-11-6", used: -1, at: at(6))
        XCTAssertFalse(negative.isValid)
        XCTAssertFalse(PauseRule.DayRecord(day: "2026-13-1", used: 1, at: at(6)).isValid)
    }

    func testMissingAppGroupWritesNothingAndPretendsNothing() {
        let store = PauseRuleStore(defaults: nil)
        XCTAssertEqual(store.loadDay(), .absent)
        XCTAssertFalse(store.saveDay(PauseRule.DayRecord(day: "2026-11-6", used: 1, at: at(6))))
        XCTAssertNil(store.loadSession())
        XCTAssertNil(store.consumeRequest())
        XCTAssertEqual(store.practiceDays(), [])
    }

    // MARK: - Session

    func testASessionBelongsToItsDay() {
        let today = PauseRule.SessionRecord(startedAt: at(6, 20), minutes: 5)
        XCTAssertTrue(PauseRule.sessionIsToday(today, now: at(6, 20, 30), timeZone: utc))
        XCTAssertFalse(PauseRule.sessionIsToday(today, now: at(7, 0, 1), timeZone: utc))
        XCTAssertFalse(PauseRule.sessionExpired(today, now: at(6, 20, 50)),
                       "ten minutes after a five-minute session is not stale yet")
        XCTAssertTrue(PauseRule.sessionExpired(today, now: at(8, 20)))
        XCTAssertTrue(PauseRule.sessionExpired(
            PauseRule.SessionRecord(startedAt: at(6), minutes: 99), now: at(6, 1)),
            "an out-of-range session is never live")
    }

    func testSessionRecordRoundTripsAndIgnoresDamage() throws {
        let defaults = try suite()
        let store = PauseRuleStore(defaults: defaults)
        XCTAssertNil(store.loadSession())
        XCTAssertTrue(store.saveSession(.init(startedAt: at(6, 18), minutes: 5)))
        XCTAssertEqual(store.loadSession()?.minutes, 5)
        defaults.set(Data("broken".utf8), forKey: PauseRuleStore.sessionKey)
        XCTAssertNil(store.loadSession(), "a damaged session reads as none, so the shield returns")
    }

    // MARK: - Handoff

    func testOnlyAFreshHandoffIsShown() {
        let now = at(6, 12)
        XCTAssertTrue(PauseRule.requestIsFresh(.init(at: now, appName: "Threads"), now: now))
        XCTAssertTrue(PauseRule.requestIsFresh(
            .init(at: now.addingTimeInterval(-(PauseRule.requestLifetime - 1)), appName: nil), now: now))
        XCTAssertFalse(PauseRule.requestIsFresh(
            .init(at: now.addingTimeInterval(-PauseRule.requestLifetime - 1), appName: nil), now: now))
        XCTAssertFalse(PauseRule.requestIsFresh(
            .init(at: now.addingTimeInterval(120), appName: nil), now: now),
            "a future timestamp is not a fresh request")
        XCTAssertFalse(PauseRule.requestIsFresh(nil, now: now))
    }

    func testConsumingTheHandoffRemovesIt() throws {
        let defaults = try suite()
        let store = PauseRuleStore(defaults: defaults)
        XCTAssertTrue(store.saveRequest(.init(at: at(6, 12), appName: "Threads")))
        XCTAssertEqual(store.consumeRequest()?.appName, "Threads")
        XCTAssertNil(store.loadRequest())
        XCTAssertNil(store.consumeRequest(), "a second foreground never repeats the pause")
    }

    func testTheHandoffCarriesNoMoreThanANameAndATime() throws {
        let request = PauseRule.Request(at: at(6), appName: "Threads")
        let data = try JSONEncoder().encode(request)
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(Set(object.keys), ["at", "appName"])
    }

    // MARK: - Practice streak

    func testStreakCountsBackFromToday() {
        let days: Set<String> = ["2026-11-6", "2026-11-5", "2026-11-4"]
        XCTAssertEqual(PauseRule.streak(days: days, now: at(6, 21), timeZone: utc), 3)
        // A day with no practice yet does not break yesterday's streak.
        XCTAssertEqual(PauseRule.streak(days: ["2026-11-5", "2026-11-4"], now: at(6, 9), timeZone: utc), 2)
        // A gap stops the count.
        XCTAssertEqual(PauseRule.streak(days: ["2026-11-6", "2026-11-4"], now: at(6), timeZone: utc), 1)
        XCTAssertEqual(PauseRule.streak(days: [], now: at(6), timeZone: utc), 0)
        XCTAssertEqual(PauseRule.streak(days: ["2026-11-2"], now: at(6), timeZone: utc), 0)
    }

    func testPracticeDaysAreBoundedUniqueAndValid() throws {
        let defaults = try suite()
        let store = PauseRuleStore(defaults: defaults)
        XCTAssertTrue(store.recordPractice(now: at(6)).contains("2026-11-6"))
        XCTAssertEqual(store.recordPractice(now: at(6, 20)).filter { $0 == "2026-11-6" }.count, 1)
        XCTAssertEqual(store.streak(now: at(6, 20), timeZone: utc), 1)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = utc
        let history = (0..<500).map { offset in
            AppUsageConfiguration.day(calendar.date(byAdding: .day, value: -offset, to: at(6))!, in: utc)
        }
        XCTAssertEqual(PauseRule.recordingPractice(days: history, now: at(6)).count, 400)
        XCTAssertFalse(PauseRule.recordingPractice(days: ["not-a-day"], now: at(6))
            .contains("not-a-day"), "a malformed day is dropped")
    }

    // MARK: - The rule model

    func testPauseRuleValidityCoversItsTwoNumbers() {
        let empty = FamilyActivitySelection()
        func rule(_ minutes: Int, _ opens: Int) -> AppUsageConfiguration {
            AppUsageConfiguration(selection: empty, mode: .pause, minutes: minutes, opens: opens)
        }
        XCTAssertFalse(rule(5, 3).isValid, "a rule with no selection is never valid")
        // The token sets are opaque; the rule's own bounds still have to hold.
        XCTAssertTrue(PauseRule.sessionRange.contains(5))
        XCTAssertTrue(PauseRule.opensRange.contains(3))
        XCTAssertFalse(PauseRule.sessionRange.contains(0))
        XCTAssertFalse(PauseRule.opensRange.contains(11))
        XCTAssertTrue(AppUsageConfiguration.health(storageAvailable: true, hasSavedData: true,
            valid: true, authorized: true, mode: .pause, registered: false) == .pauseConfigured,
            "a pause rule with no session is configured, not stopped")
        XCTAssertFalse(AppUsageConfiguration.Health.pauseConfigured.needsAttention)
    }

    func testAPauseChangeIsFrozenDuringACommitment() {
        let empty = FamilyActivitySelection()
        let first = AppUsageConfiguration(selection: empty, mode: .pause, minutes: 5, opens: 3)
        let moreOpens = AppUsageConfiguration(selection: empty, mode: .pause, minutes: 5, opens: 4)
        let longer = AppUsageConfiguration(selection: empty, mode: .pause, minutes: 10, opens: 3)
        XCTAssertFalse(first.permits(moreOpens, committed: true))
        XCTAssertFalse(first.permits(longer, committed: true))
        XCTAssertTrue(first.permits(first, committed: true))
        XCTAssertTrue(first.permits(longer, committed: false))
    }

    func testRulesSavedBeforePauseDecodeWithTheDefaultOpens() throws {
        let rule = AppUsageConfiguration(selection: FamilyActivitySelection(), mode: .always, minutes: 30)
        let data = try JSONEncoder().encode(rule)
        var object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        object.removeValue(forKey: "opens")
        let decoded = try JSONDecoder().decode(
            AppUsageConfiguration.self, from: JSONSerialization.data(withJSONObject: object))
        XCTAssertEqual(decoded.opens, PauseRule.defaultOpens)
    }

    func testShieldWatchTreatsARunningSessionAsTheRuleWorking() {
        let config = AppUsageConfiguration(selection: FamilyActivitySelection(), mode: .pause,
                                           minutes: 5, opens: 3)
        let running = AppUsageConfiguration.appShieldWatch(
            config: config, guardConfig: nil, quietHoursActive: false, alwaysShieldSet: false,
            windowShieldSet: false, guardShieldSet: false,
            pauseSessionActive: true, pauseShieldSet: false)
        XCTAssertFalse(running.expected, "no shield is expected while the session runs")
        let outside = AppUsageConfiguration.appShieldWatch(
            config: config, guardConfig: nil, quietHoursActive: false, alwaysShieldSet: false,
            windowShieldSet: false, guardShieldSet: false,
            pauseSessionActive: false, pauseShieldSet: false)
        XCTAssertTrue(outside.expected)
        XCTAssertFalse(outside.set, "a missing pause shield outside a session is a lost layer")
        let lost = AppUsageConfiguration.appShieldWatch(
            config: config, guardConfig: nil, quietHoursActive: false, alwaysShieldSet: false,
            windowShieldSet: false, guardShieldSet: false,
            pauseSessionActive: false, pauseShieldSet: true)
        XCTAssertTrue(lost.set)
    }
}
