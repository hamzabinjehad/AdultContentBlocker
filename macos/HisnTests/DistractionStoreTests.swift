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

    func testBridgeTellsTheExtensionWhichHostsToAskAbout() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        var book = RuleBook()
        var watch = DistractionBridge.watch(book: book, now: now)
        XCTAssertEqual(watch["hosts"] as? [String], [])
        XCTAssertEqual(watch["all"] as? Bool, false)
        XCTAssertTrue((watch["usageHosts"] as? [String] ?? []).contains("youtube.com"))

        var off = DistractionRule(name: "Off", targets: [.site("example.org")], action: .block)
        off.enabled = false
        book.rules = [DistractionRule(name: "Feeds", targets: [.site("reddit.com"), .surface(.xForYou), .app("com.game")],
                                      action: .block), off]
        watch = DistractionBridge.watch(book: book, now: now)
        XCTAssertEqual(watch["hosts"] as? [String], ["reddit.com", "twitter.com", "x.com"])

        XCTAssertTrue(book.startFocus(FocusSessionState(startedAt: now, until: now.addingTimeInterval(600), intent: "",
                                                        targets: [.site("docs.google.com")], allowOnly: true)))
        book.ownReason = "My evenings"
        watch = DistractionBridge.watch(book: book, now: now)
        XCTAssertEqual(watch["all"] as? Bool, true, "allow-only Focus needs a decision for every page")
        XCTAssertEqual(watch["reason"] as? String, "My evenings")
        XCTAssertNotNil(watch["focusUntil"])
        // Every site the app calls distracting is one the extension may report time for.
        for category in SiteCategory.allCases {
            XCTAssertTrue(Set(category.sites).isSubset(of: DistractionBridge.knownDistracting), "\(category)")
        }
    }

    func testTheMacsOwnFocusStartsAndEndsOnlyItsOwnSession() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        var book = RuleBook()
        book.systemFocus(on: true, targets: [.site("youtube.com")], at: now)
        XCTAssertTrue(book.focus?.isRunning(at: now) ?? false)
        XCTAssertEqual(book.focus?.strictness, .gentle)
        book.systemFocus(on: false, targets: [], at: now.addingTimeInterval(600))
        XCTAssertNil(book.focus, "the session ends with the system Focus")

        // A session the person started by hand is neither replaced nor ended from here.
        let mine = FocusSessionState(startedAt: now, until: now.addingTimeInterval(1500), intent: "Write",
                                     targets: [.site("x.com")], strictness: .locked)
        XCTAssertTrue(book.startFocus(mine))
        book.systemFocus(on: true, targets: [.site("youtube.com")], at: now.addingTimeInterval(60))
        XCTAssertEqual(book.focus, mine)
        book.systemFocus(on: false, targets: [], at: now.addingTimeInterval(120))
        XCTAssertEqual(book.focus, mine)
    }

    func testHostsFileKnowsItsOwnBlockFromAnotherList() {
        let foreign = Data("# StevenBlack\n0.0.0.0 a.example\n0.0.0.0 b.example\n".utf8)
        XCTAssertEqual(HostsFile.count(in: foreign), 2)
        XCTAssertEqual(HostsFile.ownCount(in: foreign), 0)
        let merged = Data("0.0.0.0 a.example\n\(HostsFile.beginMark)\n0.0.0.0 c.example\n0.0.0.0 d.example\n\(HostsFile.endMark)\n0.0.0.0 e.example\n".utf8)
        XCTAssertEqual(HostsFile.count(in: merged), 4)
        XCTAssertEqual(HostsFile.ownCount(in: merged), 2)
        XCTAssertTrue(ProtectionStatus.hostsLayer(152_401, own: 0).detail.contains("another list"))
        XCTAssertFalse(ProtectionStatus.hostsLayer(152_401, own: 90_000).detail.contains("another list"))
        XCTAssertTrue(ProtectionStatus.hostsLayer(152_401, own: 0).ok)
    }

    func testSuggestionsComeFromMeasuredUse() {
        let distracting: (String) -> Bool = { $0 != "app:com.apple.dt.Xcode" }
        let past = [UsageDay(day: "2026-11-22", seconds: ["site:youtube.com": 5400, "app:com.apple.dt.Xcode": 20_000]),
                    UsageDay(day: "2026-11-23", seconds: ["site:youtube.com": 3600, "site:reddit.com": 600])]
        let today: [String: Double] = ["site:youtube.com": 1800, "site:reddit.com": 2400, "app:com.game": 300]
        let found = UsageSuggestion.suggest(today: today, past: past, rules: [], exceptions: [], isDistracting: distracting)
        XCTAssertEqual(found.map(\.subject), ["site:youtube.com", "site:reddit.com"])
        XCTAssertEqual(found[0].days, 3)
        XCTAssertEqual(found[0].perDay, 3600, accuracy: 0.5)
        XCTAssertEqual(found[0].limitMinutes, 30)
        XCTAssertEqual(found[1].limitMinutes, 15, "40 minutes today asks for a limit even when the average is low")
        // A rule that already covers it, or a never-closed site, is not offered again.
        let rule = DistractionRule(name: "Video", targets: [.site("youtube.com")], action: .block)
        let covered = UsageSuggestion.suggest(today: today, past: past, rules: [rule], exceptions: ["reddit.com"],
                                              isDistracting: distracting)
        XCTAssertTrue(covered.isEmpty)
        XCTAssertEqual(UsageSuggestion.limit(forPerDay: 10 * 3600), 120)
        XCTAssertEqual(UsageSuggestion.limit(forPerDay: 60), 15)

        // A finished day is folded into the two-week history, with or without rules.
        var calendar = Calendar(identifier: .gregorian); calendar.timeZone = TimeZone(identifier: "UTC")!
        var book = RuleBook()
        book.roll(to: "2026-11-23")
        let start = calendar.date(from: DateComponents(year: 2026, month: 11, day: 23, hour: 10))!
        book.ledger.record(subject: "site:youtube.com", from: start, to: start.addingTimeInterval(1500), calendar: calendar)
        book.ledger.record(subject: "site:youtube.com", from: start.addingTimeInterval(3600),
                           to: start.addingTimeInterval(5100), calendar: calendar)
        book.roll(to: "2026-11-24")
        XCTAssertEqual(book.usageDays?.first?.seconds["site:youtube.com"] ?? 0, 3000, accuracy: 0.5)
        XCTAssertEqual(book.suggestions(isDistracting: { _ in true }).first?.subject, "site:youtube.com")
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

    func testNoRulesGiveNothingBack() {
        var book = RuleBook()
        book.roll(to: "2026-11-23")
        XCTAssertEqual(book.givenBackSeconds(at: monday(18), calendar: calendar), 0)
        book.roll(to: "2026-11-24")
        XCTAssertTrue(book.history.isEmpty, "a day without rules is not a day Unrot helped")
    }

    func testExceptionsStayOpenAndCannotBeAddedAroundALockedRule() throws {
        var book = RuleBook()
        let locked = DistractionRule(name: "Video", targets: [.site("youtube.com")], action: .block, strictness: .locked)
        let focus = FocusSessionState(startedAt: monday(12), until: monday(13), intent: "", targets: [.site("youtube.com")])
        XCTAssertEqual(book.setException("https://music.youtube.com/watch", on: true, lockUntil: nil, at: monday(12),
                                         calendar: calendar), .applied)
        XCTAssertEqual(book.exceptions, ["music.youtube.com"])
        _ = book.propose(locked, replacing: nil, lockUntil: nil, at: monday(12), calendar: calendar)
        func decide(_ url: String) -> RuleDecision {
            DistractionEngine.decide(.page(URL(string: url)!), at: monday(12, 5), rules: book.rules, day: book.day,
                                     focus: focus, exceptions: book.exceptions, calendar: calendar)
        }
        XCTAssertEqual(decide("https://music.youtube.com/"), .allow, "even in Focus")
        XCTAssertTrue(decide("https://www.youtube.com/").isBlock)
        XCTAssertTrue(decide("https://notmusic.youtube.com/").isBlock)

        // Once the locked rule runs, a new hole in it is refused; removing one is always fine.
        XCTAssertEqual(book.setException("youtube.com", on: true, lockUntil: nil, at: monday(12), calendar: calendar),
                       .refused(until: nil))
        XCTAssertEqual(book.setException("github.com", on: true, lockUntil: nil, at: monday(12), calendar: calendar), .applied)
        XCTAssertEqual(book.setException("github.com", on: true, lockUntil: monday(20), at: monday(12), calendar: calendar),
                       .applied, "already there")
        XCTAssertEqual(book.setException("gitlab.com", on: true, lockUntil: monday(20), at: monday(12), calendar: calendar),
                       .refused(until: monday(20)))
        XCTAssertEqual(book.setException("music.youtube.com", on: false, lockUntil: monday(20), at: monday(12),
                                         calendar: calendar), .applied)
        XCTAssertEqual(book.setException("nonsense", on: true, lockUntil: nil, at: monday(12), calendar: calendar),
                       .invalid(["site"]))
        // A book saved before exceptions existed still loads.
        let old = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(RuleBook())) as? [String: Any])
        XCTAssertNil(old["exceptionHosts"])
        XCTAssertNoThrow(try JSONDecoder().decode(RuleBook.self, from: JSONSerialization.data(withJSONObject: old)))
    }

    func testAWarningComesOnceBeforeItClosesAgain() {
        let wait = DistractionRule(name: "Chat", targets: [.app("com.hnc.Discord")],
                                   action: .wait(seconds: 10, grows: false, opensPerDay: nil, openMinutes: 10))
        var day = DistractionEngine.recordingOpen(RuleDay(), rule: wait, at: monday(12))
        XCTAssertTrue(RelockNotice.due(day: day, rules: [wait], now: monday(12, 5), announced: [:]).isEmpty)
        let due = RelockNotice.due(day: day, rules: [wait], now: monday(12, 9).addingTimeInterval(20), announced: [:])
        XCTAssertEqual(due, [RelockNotice.Item(rule: wait.id, at: monday(12, 10))])
        XCTAssertTrue(RelockNotice.due(day: day, rules: [wait], now: monday(12, 9).addingTimeInterval(40),
                                       announced: [wait.id: monday(12, 10)]).isEmpty, "said once")
        XCTAssertTrue(RelockNotice.due(day: day, rules: [wait], now: monday(12, 11), announced: [:]).isEmpty, "already over")
        // A new open later is a new end, so it is announced again.
        day = DistractionEngine.recordingOpen(day, rule: wait, at: monday(13))
        XCTAssertEqual(RelockNotice.due(day: day, rules: [wait], now: monday(13, 9).addingTimeInterval(30),
                                        announced: [wait.id: monday(12, 10)]).count, 1)
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

    // MARK: Browser bridge

    func testBridgeDecidesPagesAndHidesOnlyTheFeed() {
        var book = RuleBook()
        book.rules = [RuleTemplate.feedsOnly.make(),
                      DistractionRule(name: "Chat", targets: [.site("discord.com")],
                                      action: .wait(seconds: 10, grows: true, opensPerDay: 2, openMinutes: 10))]
        func ask(_ url: String, inbox: [DistractionBridge.Report] = [], at now: Date? = nil) -> [String: Any] {
            DistractionBridge.decide(url: url, book: book, inbox: inbox, now: now ?? monday(12), calendar: calendar)
        }
        XCTAssertEqual(ask("https://www.youtube.com/")["action"] as? String, "hideFeed")
        XCTAssertEqual(ask("https://www.youtube.com/")["surface"] as? String, "youtubeHome")
        XCTAssertEqual(ask("https://www.youtube.com/shorts/abc")["action"] as? String, "block")
        XCTAssertEqual(ask("https://www.youtube.com/watch?v=1")["action"] as? String, "allow")
        XCTAssertEqual(ask("chrome://settings")["action"] as? String, "allow")

        let wait = ask("https://discord.com/channels/@me")
        XCTAssertEqual(wait["action"] as? String, "wait")
        XCTAssertEqual(wait["seconds"] as? Int, 10)
        XCTAssertEqual(wait["opensLeft"] as? Int, 2)
        // The open is still in the inbox, not yet in the book: the reload must pass.
        let opened = DistractionBridge.Report(kind: .open, rule: book.rules[1].id, host: nil,
                                              from: monday(12), to: monday(12))
        XCTAssertEqual(ask("https://discord.com/", inbox: [opened], at: monday(12, 1))["action"] as? String, "allow")
        XCTAssertEqual(ask("https://discord.com/", inbox: [opened], at: monday(12, 11))["seconds"] as? Int, 15)
    }

    func testBridgeReportsAreCheckedAndDrained() throws {
        let suite = "unrot.tests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = DistractionStore(defaults: defaults)
        store.calendar = calendar
        store.clock = { self.monday(12) }
        store.lockUntil = { nil }
        let locked = DistractionRule(name: "Feeds", targets: [.site("x.com")], action: .block, strictness: .locked)
        _ = store.propose(locked, replacing: nil)

        XCTAssertEqual(DistractionBridge.handle(["op": "usage", "host": "https://www.x.com/home", "seconds": 600],
                                                defaults: defaults, now: monday(12))["ok"] as? Bool, true)
        for bad: [String: Any] in [["op": "usage", "host": "x.com", "seconds": 999_999],
                                   ["op": "usage", "host": "not a host", "seconds": 60],
                                   ["op": "opened", "rule": "nope"],
                                   ["op": "snoozed", "rule": locked.id.uuidString]] {
            XCTAssertEqual(DistractionBridge.handle(bad, defaults: defaults, now: monday(12))["ok"] as? Bool, false)
        }
        store.tick()
        XCTAssertEqual(store.book.ledger.seconds(for: locked), 600, accuracy: 0.5)
        XCTAssertTrue(DistractionBridge.loadInbox(defaults).isEmpty, "drained once")
        XCTAssertEqual(DistractionBridge.handle(["op": "decide", "url": "https://x.com/home"], defaults: defaults,
                                                now: monday(12), calendar: calendar)["action"] as? String, "block")
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
