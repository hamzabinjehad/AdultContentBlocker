import XCTest
@testable import Hisn

/// The Mac's distraction rules: what each rule decides, which one wins, when a
/// change counts as weaker and what strictness then does, and how time is
/// counted. All pure, at fixed dates in UTC.
final class DistractionRulesTests: XCTestCase {
    private var calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(secondsFromGMT: 0)!
        return c
    }()

    /// Monday 23 November 2026 at `hour:minute` UTC.
    private func monday(_ hour: Int, _ minute: Int = 0) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: 11, day: 23, hour: hour, minute: minute))!
    }

    private let youtube = URL(string: "https://www.youtube.com/watch?v=abc")!
    private let shorts = URL(string: "https://m.youtube.com/shorts/xyz")!
    private let discord = "com.hnc.Discord"

    private func rule(_ action: RuleAction, targets: Set<RuleTarget> = [.site("youtube.com")],
                      window: TimeWindow? = nil, strictness: Strictness = .gentle) -> DistractionRule {
        DistractionRule(name: "Test", targets: targets, action: action, window: window, strictness: strictness)
    }

    // MARK: Targets

    func testSiteNormalisation() {
        XCTAssertEqual(RuleTarget.normalizedSite("https://www.YouTube.com/shorts"), "youtube.com")
        XCTAssertEqual(RuleTarget.normalizedSite("reddit.com."), "reddit.com")
        XCTAssertNil(RuleTarget.normalizedSite("localhost"))
        XCTAssertNil(RuleTarget.normalizedSite("*.tiktok.com"))
        XCTAssertNil(RuleTarget.normalizedSite("a..b.com"))
    }

    func testTargetKeysRoundTripAndRejectJunk() throws {
        for target: RuleTarget in [.app(discord), .site("x.com"), .surface(.youtubeShorts)] {
            XCTAssertEqual(RuleTarget(key: target.key), target)
        }
        XCTAssertNil(RuleTarget(key: "app:nodots"))
        XCTAssertNil(RuleTarget(key: "surface:unknown"))
        XCTAssertNil(RuleTarget(key: "file:/etc/hosts"))
        let data = try JSONEncoder().encode(rule(.block))
        XCTAssertEqual(try JSONDecoder().decode(DistractionRule.self, from: data).targets, [.site("youtube.com")])
    }

    func testSiteRuleCoversSubdomainsButNotLookalikes() {
        let r = rule(.block)
        XCTAssertTrue(r.covers(.page(youtube)))
        XCTAssertTrue(r.covers(.page(shorts)))
        XCTAssertFalse(r.covers(.page(URL(string: "https://notyoutube.com/")!)))
        XCTAssertFalse(r.covers(.app("com.google.Chrome")))
    }

    func testFeedSurfacesLeaveTheRestOfTheSite() {
        XCTAssertTrue(FeedSurface.youtubeShorts.matches(shorts))
        XCTAssertFalse(FeedSurface.youtubeShorts.matches(youtube))
        XCTAssertTrue(FeedSurface.youtubeHome.matches(URL(string: "https://www.youtube.com/")!))
        XCTAssertFalse(FeedSurface.youtubeHome.matches(URL(string: "https://www.youtube.com/results?search_query=swift")!))
        XCTAssertFalse(FeedSurface.youtubeHome.matches(URL(string: "https://www.youtube.com/feed/subscriptions")!))
        XCTAssertTrue(FeedSurface.xForYou.matches(URL(string: "https://twitter.com/home")!))
        XCTAssertFalse(FeedSurface.xForYou.matches(URL(string: "https://x.com/someone/status/1")!))
        XCTAssertTrue(FeedSurface.instagramReels.matches(URL(string: "https://www.instagram.com/reels/abc/")!))
        XCTAssertFalse(FeedSurface.instagramReels.matches(URL(string: "https://www.instagram.com/direct/inbox/")!))
        XCTAssertTrue(FeedSurface.redditPopular.matches(URL(string: "https://www.reddit.com/r/popular/")!))
        XCTAssertFalse(FeedSurface.redditHome.matches(URL(string: "https://www.reddit.com/r/swift/")!))
        XCTAssertTrue(FeedSurface.facebookReels.matches(URL(string: "https://www.facebook.com/watch/reels/1")!))
        XCTAssertFalse(FeedSurface.facebookWatch.matches(URL(string: "https://www.facebook.com/watch/reels/1")!))
        XCTAssertFalse(FeedSurface.tiktok.matches(URL(string: "file:///tiktok.com")!))
    }

    // MARK: Windows

    func testWindowCrossingMidnightBelongsToItsStartDay() {
        // Sunday night only: 22:00–07:00 starting Sunday.
        let w = TimeWindow(startMinute: 22 * 60, endMinute: 7 * 60, days: .sunday)
        XCTAssertNotNil(w.interval(containing: monday(6, 59), calendar: calendar), "Sunday's night runs into Monday")
        XCTAssertNil(w.interval(containing: monday(7), calendar: calendar))
        XCTAssertNil(w.interval(containing: monday(23), calendar: calendar), "Monday night is not chosen")
        XCTAssertEqual(Weekdays.of(monday(12), calendar: calendar), .monday)
    }

    func testNextStart() {
        let work = TimeWindow(startMinute: 9 * 60, endMinute: 17 * 60, days: .workdays)
        XCTAssertEqual(work.nextStart(after: monday(8), calendar: calendar), monday(9))
        XCTAssertFalse(TimeWindow(startMinute: 60, endMinute: 60).isValid)
    }

    // MARK: Decisions

    func testBlockAndScheduledBlock() {
        let always = rule(.block)
        XCTAssertEqual(DistractionEngine.decide(.page(youtube), at: monday(12), rules: [always], day: RuleDay(),
                                                focus: nil, calendar: calendar),
                       .block(rule: always.id, reason: .blocked, until: nil))
        let work = rule(.block, window: TimeWindow(startMinute: 9 * 60, endMinute: 17 * 60, days: .workdays))
        XCTAssertEqual(DistractionEngine.decide(.page(youtube), at: monday(12), rules: [work], day: RuleDay(),
                                                focus: nil, calendar: calendar),
                       .block(rule: work.id, reason: .scheduled, until: monday(17)))
        XCTAssertEqual(DistractionEngine.decide(.page(youtube), at: monday(18), rules: [work], day: RuleDay(),
                                                focus: nil, calendar: calendar), .allow)
    }

    func testDailyLimitIsSharedByTheRuleTargets() {
        let feeds = rule(.dailyLimit(minutesPerDay: 60), targets: [.site("youtube.com"), .app(discord)])
        var day = RuleDay()
        day.usedSeconds[feeds.id] = 59 * 60
        XCTAssertEqual(DistractionEngine.decide(.app(discord), at: monday(12), rules: [feeds], day: day,
                                                focus: nil, calendar: calendar), .allow)
        day.usedSeconds[feeds.id] = 60 * 60
        XCTAssertEqual(DistractionEngine.decide(.page(youtube), at: monday(12), rules: [feeds], day: day,
                                                focus: nil, calendar: calendar),
                       .block(rule: feeds.id, reason: .limitReached, until: calendar.date(byAdding: .day, value: 1, to: monday(0))))
    }

    func testWaitGrowsCountsOpensAndHonoursAnOpenInProgress() {
        let wait = rule(.wait(seconds: 10, grows: true, opensPerDay: 3, openMinutes: 10), targets: [.app(discord)])
        var day = RuleDay()
        XCTAssertEqual(DistractionEngine.decide(.app(discord), at: monday(12), rules: [wait], day: day,
                                                focus: nil, calendar: calendar),
                       .wait(rule: wait.id, seconds: 10, opensLeft: 3, openMinutes: 10))
        day = DistractionEngine.recordingOpen(day, rule: wait, at: monday(12))
        XCTAssertEqual(DistractionEngine.decide(.app(discord), at: monday(12, 9), rules: [wait], day: day,
                                                focus: nil, calendar: calendar), .allow, "inside the 10 minutes")
        XCTAssertEqual(DistractionEngine.decide(.app(discord), at: monday(12, 10), rules: [wait], day: day,
                                                focus: nil, calendar: calendar),
                       .wait(rule: wait.id, seconds: 15, opensLeft: 2, openMinutes: 10), "second open waits longer")
        day = DistractionEngine.recordingOpen(day, rule: wait, at: monday(13))
        day = DistractionEngine.recordingOpen(day, rule: wait, at: monday(14))
        if case .block(_, let reason, _) = DistractionEngine.decide(.app(discord), at: monday(15), rules: [wait],
                                                                    day: day, focus: nil, calendar: calendar) {
            XCTAssertEqual(reason, .noOpensLeft)
        } else {
            XCTFail("no opens left must close the app")
        }
    }

    func testBlockBeatsWaitAndTheLongerBlockWins() {
        let wait = rule(.wait(seconds: 10, grows: false, opensPerDay: nil, openMinutes: 5))
        let evening = rule(.block, window: TimeWindow(startMinute: 11 * 60, endMinute: 13 * 60))
        let always = rule(.block)
        let decision = DistractionEngine.decide(.page(youtube), at: monday(12), rules: [wait, evening, always],
                                                day: RuleDay(), focus: nil, calendar: calendar)
        XCTAssertEqual(decision, .block(rule: always.id, reason: .blocked, until: nil))
        let shorter = DistractionEngine.decide(.page(youtube), at: monday(12), rules: [wait, evening],
                                               day: RuleDay(), focus: nil, calendar: calendar)
        XCTAssertEqual(shorter, .block(rule: evening.id, reason: .scheduled, until: monday(13)))
    }

    func testSnoozeLiftsOnlyThatRule() {
        let r = rule(.block)
        var day = SnoozePolicy.granting(RuleDay(), rule: r.id, at: monday(12))
        XCTAssertEqual(DistractionEngine.decide(.page(youtube), at: monday(12, 4), rules: [r], day: day,
                                                focus: nil, calendar: calendar), .allow)
        XCTAssertTrue(DistractionEngine.decide(.page(youtube), at: monday(12, 5), rules: [r], day: day,
                                               focus: nil, calendar: calendar).isBlock)
        day.snoozesToday[r.id] = 3
        XCTAssertEqual(SnoozePolicy.delayBeforeSnooze(strictness: .gentle, snoozesToday: 3), 0)
        XCTAssertEqual(SnoozePolicy.delayBeforeSnooze(strictness: .firm, snoozesToday: 0), 15)
        XCTAssertEqual(SnoozePolicy.delayBeforeSnooze(strictness: .firm, snoozesToday: 3), 120)
        XCTAssertEqual(SnoozePolicy.delayBeforeSnooze(strictness: .firm, snoozesToday: 9), 300)
        XCTAssertNil(SnoozePolicy.delayBeforeSnooze(strictness: .locked, snoozesToday: 0))
    }

    func testFocusClosesChosenTargetsOrEverythingElse() {
        let session = FocusSessionState(startedAt: monday(12), until: monday(12, 25), intent: "Report",
                                        targets: [.site("youtube.com")])
        XCTAssertEqual(DistractionEngine.decide(.page(youtube), at: monday(12, 10), rules: [], day: RuleDay(),
                                                focus: session, calendar: calendar),
                       .block(rule: DistractionEngine.focusRuleID, reason: .focus, until: monday(12, 25)))
        XCTAssertEqual(DistractionEngine.decide(.page(youtube), at: monday(12, 25), rules: [], day: RuleDay(),
                                                focus: session, calendar: calendar), .allow, "ends on time")

        let onlyXcode = FocusSessionState(startedAt: monday(12), until: monday(13), intent: "Code",
                                          targets: [.app("com.apple.dt.Xcode")], allowOnly: true)
        XCTAssertTrue(DistractionEngine.decide(.app(discord), at: monday(12, 30), rules: [], day: RuleDay(),
                                               focus: onlyXcode, calendar: calendar).isBlock)
        XCTAssertEqual(DistractionEngine.decide(.app("com.apple.dt.Xcode"), at: monday(12, 30), rules: [],
                                                day: RuleDay(), focus: onlyXcode, calendar: calendar), .allow)
        XCTAssertEqual(DistractionEngine.decide(.app("com.apple.systempreferences"), at: monday(12, 30), rules: [],
                                                day: RuleDay(), focus: onlyXcode, calendar: calendar), .allow,
                       "System Settings is never closed")
        XCTAssertEqual(onlyXcode.progress(at: monday(12, 30)), 0.5, accuracy: 0.001)
    }

    func testBrokenRulesDoNothing() {
        let empty = rule(.block, targets: [])
        XCTAssertFalse(empty.problems.isEmpty)
        XCTAssertEqual(DistractionEngine.decide(.page(youtube), at: monday(12), rules: [empty], day: RuleDay(),
                                                focus: nil, calendar: calendar), .allow)
        XCTAssertEqual(rule(.dailyLimit(minutesPerDay: 2)).problems, ["limit"])
        XCTAssertEqual(rule(.wait(seconds: 0, grows: false, opensPerDay: nil, openMinutes: 5)).problems, ["wait"])
    }

    // MARK: Changing rules

    func testWhatCountsAsWeaker() {
        let base = rule(.dailyLimit(minutesPerDay: 60), targets: [.site("youtube.com"), .site("x.com")],
                        window: TimeWindow(startMinute: 9 * 60, endMinute: 17 * 60, days: .workdays),
                        strictness: .firm)
        var stronger = base
        stronger.targets.insert(.app(discord))
        stronger.action = .dailyLimit(minutesPerDay: 30)
        stronger.window = TimeWindow(startMinute: 8 * 60, endMinute: 18 * 60, days: .everyDay)
        stronger.strictness = .locked
        XCTAssertFalse(RuleChange.loosens(from: base, to: stronger))

        var fewer = base; fewer.targets = [.site("youtube.com")]
        var longer = base; longer.action = .dailyLimit(minutesPerDay: 90)
        var later = base; later.window = TimeWindow(startMinute: 10 * 60, endMinute: 17 * 60, days: .workdays)
        var softer = base; softer.strictness = .gentle
        var off = base; off.enabled = false
        var waitInstead = base; waitInstead.action = .wait(seconds: 30, grows: true, opensPerDay: 1, openMinutes: 5)
        for weaker in [fewer, longer, later, softer, off, waitInstead] {
            XCTAssertTrue(RuleChange.loosens(from: base, to: weaker))
        }
        XCTAssertTrue(RuleChange.loosens(from: base, to: nil), "deleting a rule loosens")
        var blockInstead = base; blockInstead.action = .block
        XCTAssertFalse(RuleChange.loosens(from: base, to: blockInstead))
    }

    func testStrictnessDecidesWhenAWeakerChangeApplies() {
        let work = TimeWindow(startMinute: 9 * 60, endMinute: 17 * 60, days: .workdays)
        var r = rule(.block, window: work)
        var weaker = r; weaker.enabled = false

        r.strictness = .gentle
        XCTAssertEqual(RuleChange.verdict(from: r, to: weaker, lockUntil: nil, at: monday(12), calendar: calendar), .now)
        r.strictness = .firm
        XCTAssertEqual(RuleChange.verdict(from: r, to: weaker, lockUntil: nil, at: monday(12), calendar: calendar),
                       .after(monday(12, 10)))
        r.strictness = .locked
        XCTAssertEqual(RuleChange.verdict(from: r, to: weaker, lockUntil: nil, at: monday(12), calendar: calendar),
                       .refused(until: monday(17)), "locked while running")
        XCTAssertEqual(RuleChange.verdict(from: r, to: weaker, lockUntil: nil, at: monday(8, 30), calendar: calendar),
                       .refused(until: monday(17)), "nor in the hour before it starts")
        XCTAssertEqual(RuleChange.verdict(from: r, to: weaker, lockUntil: nil, at: monday(18), calendar: calendar), .now)

        var allDay = rule(.block, strictness: .locked)
        allDay.window = nil
        XCTAssertEqual(RuleChange.verdict(from: allDay, to: nil, lockUntil: nil, at: monday(12), calendar: calendar),
                       .after(monday(12).addingTimeInterval(24 * 3600)))

        r.strictness = .gentle
        XCTAssertEqual(RuleChange.verdict(from: r, to: weaker, lockUntil: monday(20), at: monday(18), calendar: calendar),
                       .refused(until: monday(20)), "the Mac's lock wins over a gentle rule")
        XCTAssertEqual(RuleChange.verdict(from: r, to: r, lockUntil: monday(20), at: monday(18), calendar: calendar),
                       .now, "an unchanged rule is not weaker")
    }

    func testEmergencyPassOnceAWeek() {
        XCTAssertTrue(EmergencyPass.available(lastUsed: nil, at: monday(12)))
        let used = monday(12)
        XCTAssertFalse(EmergencyPass.available(lastUsed: used, at: used.addingTimeInterval(6 * 86_400)))
        XCTAssertTrue(EmergencyPass.available(lastUsed: used, at: used.addingTimeInterval(7 * 86_400)))
        XCTAssertEqual(EmergencyPass.nextAvailable(lastUsed: used, at: used.addingTimeInterval(86_400)),
                       used.addingTimeInterval(7 * 86_400))
    }

    // MARK: Time

    func testLedgerCountsOverlapOnceAndSplitsAtMidnight() {
        var ledger = UsageLedger(day: UsageLedger.dayKey(monday(12), calendar: calendar))
        // Safari in front 12:00–12:20; the extension saw YouTube 12:05–12:15 inside it.
        ledger.record(subject: "app:com.apple.Safari", from: monday(12), to: monday(12, 20), calendar: calendar)
        ledger.record(subject: "site:youtube.com", from: monday(12, 5), to: monday(12, 15), calendar: calendar)
        ledger.record(subject: "app:\(discord)", from: monday(12, 10), to: monday(12, 30), calendar: calendar)
        let feeds = rule(.dailyLimit(minutesPerDay: 60), targets: [.site("youtube.com"), .app(discord)])
        XCTAssertEqual(ledger.seconds(for: feeds), 25 * 60, accuracy: 0.5, "12:05–12:30, overlap counted once")

        // A sleeping Mac is not use.
        ledger.record(subject: "app:\(discord)", from: monday(13), to: monday(16), calendar: calendar)
        XCTAssertEqual(ledger.seconds(for: feeds), 25 * 60 + UsageLedger.maxSpan, accuracy: 0.5)

        // Tomorrow's span is not today's.
        ledger.record(subject: "app:\(discord)", from: monday(24, 10), to: monday(24, 20), calendar: calendar)
        XCTAssertEqual(ledger.seconds(for: feeds), 25 * 60 + UsageLedger.maxSpan, accuracy: 0.5)

        let hours = ledger.secondsByHour(calendar: calendar)
        XCTAssertEqual(hours[12], 30 * 60, accuracy: 0.5)
        XCTAssertEqual(ledger.topSubjects(limit: 1).first?.subject, "app:\(discord)")
    }

    func testAdjacentReportsMerge() {
        var ledger = UsageLedger(day: UsageLedger.dayKey(monday(12), calendar: calendar))
        ledger.record(subject: "app:a.b", from: monday(12), to: monday(12, 1), calendar: calendar)
        ledger.record(subject: "app:a.b", from: monday(12, 1), to: monday(12, 2), calendar: calendar)
        XCTAssertEqual(ledger.spans.count, 1)
    }

    func testTimeGivenBackNeverGoesBackwards() {
        let before = 105.0 * 60
        XCTAssertEqual(TimeGivenBack.seconds(beforePerDay: before, feedSecondsByDay: [45 * 60, 200 * 60, 105 * 60]),
                       60 * 60, "a heavy day adds nothing and takes nothing")
        XCTAssertEqual(TimeGivenBack.stage(forSeconds: 0), 1)
        XCTAssertEqual(TimeGivenBack.stage(forSeconds: 3600), 4)
        XCTAssertEqual(TimeGivenBack.stage(forSeconds: 30 * 3600), 7)
    }
}
