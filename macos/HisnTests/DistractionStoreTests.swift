import XCTest
@testable import Hisn

/// The rule book: changes under strictness, waiting changes, a new day,
/// Focus, the emergency pass, time given back, and saving.
@MainActor
final class DistractionStoreTests: XCTestCase {
    private var calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(secondsFromGMT: 0)!
        return c
    }()

    private func monday(_ hour: Int, _ minute: Int = 0) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: 11, day: 23, hour: hour, minute: minute))!
    }

    private func feeds(_ strictness: Strictness = .gentle, minutes: Int = 60) -> DistractionRule {
        DistractionRule(name: "Feeds", targets: [.site("youtube.com"), .site("x.com")],
                        action: .dailyLimit(minutesPerDay: minutes), strictness: strictness)
    }

    func testAddingIsAlwaysAllowedAndInvalidRulesAreRefused() {
        var book = RuleBook()
        XCTAssertEqual(book.propose(feeds(.locked), replacing: nil, lockUntil: monday(20), at: monday(12),
                                    calendar: calendar), .applied, "a new rule only adds, even when locked")
        let broken = DistractionRule(name: "", targets: [], action: .block)
        XCTAssertEqual(book.propose(broken, replacing: nil, lockUntil: nil, at: monday(12), calendar: calendar),
                       .invalid(["name", "targets"]))
        XCTAssertEqual(book.rules.count, 1)
    }

    func testFirmLooseningWaitsAndTheNewestWishWins() {
        var book = RuleBook()
        let rule = feeds(.firm)
        _ = book.propose(rule, replacing: nil, lockUntil: nil, at: monday(12), calendar: calendar)
        var longer = rule; longer.action = .dailyLimit(minutesPerDay: 90)
        XCTAssertEqual(book.propose(longer, replacing: rule.id, lockUntil: nil, at: monday(12), calendar: calendar),
                       .scheduled(monday(12, 10)))
        var muchLonger = rule; muchLonger.action = .dailyLimit(minutesPerDay: 120)
        _ = book.propose(muchLonger, replacing: rule.id, lockUntil: nil, at: monday(12, 5), calendar: calendar)
        XCTAssertEqual(book.pending.count, 1)
        XCTAssertFalse(book.applyDue(at: monday(12, 14)))
        XCTAssertTrue(book.applyDue(at: monday(12, 15)))
        XCTAssertEqual(book.rules.first?.action, .dailyLimit(minutesPerDay: 120))

        // Tightening a firm rule is immediate.
        var shorter = book.rules[0]; shorter.action = .dailyLimit(minutesPerDay: 30)
        XCTAssertEqual(book.propose(shorter, replacing: rule.id, lockUntil: nil, at: monday(13), calendar: calendar),
                       .applied)
    }

    func testCancellingAWaitingChangeKeepsTheRule() {
        var book = RuleBook()
        let rule = feeds(.firm)
        _ = book.propose(rule, replacing: nil, lockUntil: nil, at: monday(12), calendar: calendar)
        _ = book.propose(nil, replacing: rule.id, lockUntil: nil, at: monday(12), calendar: calendar)
        book.cancelPending(book.pending[0].id)
        book.applyDue(at: monday(13))
        XCTAssertEqual(book.rules.count, 1)
    }

    func testLockedAndMacLockRefuse() {
        var book = RuleBook()
        let rule = feeds(.gentle)
        _ = book.propose(rule, replacing: nil, lockUntil: nil, at: monday(12), calendar: calendar)
        XCTAssertEqual(book.propose(nil, replacing: rule.id, lockUntil: monday(20), at: monday(12), calendar: calendar),
                       .refused(until: monday(20)))
        XCTAssertEqual(book.rules.count, 1)
    }

    func testNewDayMovesTotalsToHistoryAndResetsCounts() {
        var book = RuleBook()
        let rule = feeds()
        _ = book.propose(rule, replacing: nil, lockUntil: nil, at: monday(9), calendar: calendar)
        book.roll(to: "2026-11-23")
        book.ledger.record(subject: "site:youtube.com", from: monday(12), to: monday(12, 20), calendar: calendar)
        book.closedToday = 2
        book.refreshUsage()
        XCTAssertEqual(book.day.usedSeconds[rule.id] ?? 0, 20 * 60, accuracy: 0.5)
        book.roll(to: "2026-11-24")
        XCTAssertEqual(book.history.last?.day, "2026-11-23")
        XCTAssertEqual(book.history.last?.feedSeconds ?? 0, 20 * 60, accuracy: 0.5)
        XCTAssertEqual(book.history.last?.closed, 2)
        XCTAssertEqual(book.day, RuleDay())
        XCTAssertEqual(book.ledger.spans.count, 0)
    }

    func testGivenBackGrowsThroughTheDayAndNeverGoesNegative() {
        var book = RuleBook()
        _ = book.propose(feeds(), replacing: nil, lockUntil: nil, at: monday(0), calendar: calendar)
        book.roll(to: "2026-11-23")
        book.beforePerDay = 120 * 60
        XCTAssertEqual(book.givenBackSeconds(at: monday(12), calendar: calendar), 60 * 60, accuracy: 1)
        book.ledger.record(subject: "site:x.com", from: monday(1), to: monday(1, 30), calendar: calendar)
        book.ledger.record(subject: "site:x.com", from: monday(2), to: monday(2, 30), calendar: calendar)
        book.ledger.record(subject: "site:x.com", from: monday(3), to: monday(3, 30), calendar: calendar)
        XCTAssertEqual(book.givenBackSeconds(at: monday(12), calendar: calendar), 0, accuracy: 1,
                       "90 minutes by noon against a 60-minute share")
    }

    func testFocusEndsByStrictness() {
        var book = RuleBook()
        let gentle = FocusSessionState(startedAt: monday(12), until: monday(12, 25), intent: "", targets: [.site("x.com")])
        XCTAssertTrue(book.startFocus(gentle))
        XCTAssertEqual(book.howToEndFocus(at: monday(12, 5)), .now)
        var locked = gentle; locked.strictness = .locked; locked.until = monday(13)
        XCTAssertTrue(book.startFocus(locked))
        XCTAssertEqual(book.howToEndFocus(at: monday(12, 5)), .notUntil(monday(13)))
        XCTAssertFalse(book.startFocus(gentle), "a shorter Focus cannot replace a longer running one")
        var tooLong = gentle; tooLong.until = monday(17)
        XCTAssertFalse(book.startFocus(tooLong))
    }

    func testEmergencyPassLiftsRunningRulesOnceAWeek() {
        var book = RuleBook()
        let rule = DistractionRule(name: "Work", targets: [.app("com.hnc.Discord")], action: .block,
                                   window: TimeWindow(startMinute: 9 * 60, endMinute: 17 * 60), strictness: .locked)
        _ = book.propose(rule, replacing: nil, lockUntil: nil, at: monday(8), calendar: calendar)
        book.focus = FocusSessionState(startedAt: monday(12), until: monday(13), intent: "", targets: [], strictness: .locked)
        XCTAssertTrue(book.useEmergencyPass(at: monday(12), calendar: calendar))
        XCTAssertNil(book.focus)
        XCTAssertEqual(book.day.snoozedUntil[rule.id], monday(17))
        XCTAssertEqual(DistractionEngine.decide(.app("com.hnc.Discord"), at: monday(16), rules: book.rules,
                                                day: book.day, focus: nil, calendar: calendar), .allow)
        XCTAssertFalse(book.useEmergencyPass(at: monday(14), calendar: calendar))
    }

    func testStoreSavesAndReloads() throws {
        let suite = "unrot.tests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = DistractionStore(defaults: defaults)
        store.calendar = calendar
        store.clock = { self.monday(12) }
        store.lockUntil = { nil }
        XCTAssertEqual(store.propose(feeds(), replacing: nil), .applied)
        let again = DistractionStore(defaults: defaults)
        XCTAssertEqual(again.book.rules.map(\.name), ["Feeds"])
    }
}
