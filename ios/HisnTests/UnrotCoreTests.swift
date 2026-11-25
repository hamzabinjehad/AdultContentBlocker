import XCTest
@testable import UnrotMobile

/// Unrot's pure rules, matching Android's GlassStateTest and the stage table.
final class UnrotCoreTests: XCTestCase {
    private let day = "2026-11-23"

    // MARK: - Glass

    func testClearWithoutEvents() {
        XCTAssertEqual(GlassState.at(now: 1000, crackedAt: 0, sealedAt: 0, brokenAt: 0, sealedDay: nil, today: day).look, .clear)
    }

    func testCrackLastsTenMinutesWithCountdown() {
        let cracked = GlassState.at(now: 1060, crackedAt: 1000, sealedAt: 0, brokenAt: 0, sealedDay: nil, today: day)
        XCTAssertEqual(cracked.look, .cracked)
        XCTAssertEqual(cracked.sealLeft, 540, accuracy: 0.001)
        let later = GlassState.at(now: 1000 + 600, crackedAt: 1000, sealedAt: 0, brokenAt: 0, sealedDay: nil, today: day)
        XCTAssertEqual(later.look, .clear)
    }

    func testBreathSealsOnlyInsideWindowAndOnce() {
        XCTAssertTrue(GlassState.breathSeals(now: 1300, crackedAt: 1000, sealedAt: 0))
        XCTAssertFalse(GlassState.breathSeals(now: 1000 + 601, crackedAt: 1000, sealedAt: 0))
        XCTAssertFalse(GlassState.breathSeals(now: 1300, crackedAt: 1000, sealedAt: 1200))
        XCTAssertFalse(GlassState.breathSeals(now: 900, crackedAt: 1000, sealedAt: 0))
        XCTAssertFalse(GlassState.breathSeals(now: 1300, crackedAt: 0, sealedAt: 0))
    }

    func testGoldShowsForTheRestOfTheDay() {
        let gold = GlassState.at(now: 5000, crackedAt: 1000, sealedAt: 1200, brokenAt: 0, sealedDay: day, today: day)
        XCTAssertEqual(gold.look, .gold)
        let tomorrow = GlassState.at(now: 90000, crackedAt: 1000, sealedAt: 1200, brokenAt: 0, sealedDay: day, today: "2026-11-24")
        XCTAssertEqual(tomorrow.look, .clear)
    }

    func testBrokenWinsThenNewGlass() {
        let broken = GlassState.at(now: 2000, crackedAt: 1000, sealedAt: 0, brokenAt: 1500, sealedDay: nil, today: day)
        XCTAssertEqual(broken.look, .broken)
        let after = GlassState.at(now: 1500 + 3600, crackedAt: 1000, sealedAt: 0, brokenAt: 1500, sealedDay: nil, today: day)
        XCTAssertEqual(after.look, .clear)
        // A newer crack shows over an older break.
        let newer = GlassState.at(now: 1700, crackedAt: 1600, sealedAt: 0, brokenAt: 1500, sealedDay: nil, today: day)
        XCTAssertEqual(newer.look, .cracked)
    }

    func testClockMovedBackNeverSticks() {
        XCTAssertEqual(GlassState.at(now: 500, crackedAt: 1000, sealedAt: 0, brokenAt: 0, sealedDay: nil, today: day).look, .clear)
        XCTAssertEqual(GlassState.at(now: 500, crackedAt: 0, sealedAt: 0, brokenAt: 1000, sealedDay: nil, today: day).look, .clear)
    }

    // MARK: - Stages

    func testStagesFollowAndroidThresholds() {
        XCTAssertEqual(GlassStage.stage(givenBackMinutes: 0), 0)
        XCTAssertEqual(GlassStage.stage(givenBackMinutes: 4), 0)
        XCTAssertEqual(GlassStage.stage(givenBackMinutes: 5), 1)
        XCTAssertEqual(GlassStage.stage(givenBackMinutes: 59), 2)
        XCTAssertEqual(GlassStage.stage(givenBackMinutes: 60), 3)
        XCTAssertEqual(GlassStage.stage(givenBackMinutes: 8 * 60), 5)
        XCTAssertEqual(GlassStage.stage(givenBackMinutes: 100_000), 6)
        XCTAssertEqual(GlassStage.stage(givenBackMinutes: -3), 0)
    }

    func testNextStageAndPart() {
        XCTAssertEqual(GlassStage.minutesToNext(givenBackMinutes: 59), 1)
        XCTAssertNil(GlassStage.minutesToNext(givenBackMinutes: 24 * 60))
        XCTAssertEqual(GlassStage.part(givenBackMinutes: 10), 0.5, accuracy: 0.0001)
        XCTAssertEqual(GlassStage.part(givenBackMinutes: 24 * 60), 1)
    }

    func testArtFollowsLookThenNightThenStage() {
        XCTAssertEqual(GlassStage.art(stage: 3, look: .cracked, night: true), "glass_cracked")
        XCTAssertEqual(GlassStage.art(stage: 3, look: .clear, night: true), "glass_night")
        XCTAssertEqual(GlassStage.art(stage: 0, look: .clear, night: false), "glass_seed")
        XCTAssertEqual(GlassStage.art(stage: 4, look: .clear, night: false), "glass_sprout")
        XCTAssertEqual(GlassStage.art(stage: 6, look: .clear, night: false), "glass_tree")
    }

    // MARK: - Feed time and time given back

    func testFeedEventsRoundTripAndRejectStrangers() {
        XCTAssertEqual(FeedWatch.steps.count, 40)
        XCTAssertEqual(FeedWatch.minutes(event: FeedWatch.eventName(70)), 70)
        XCTAssertNil(FeedWatch.minutes(event: "unrot.feed.75"))
        XCTAssertNil(FeedWatch.minutes(event: "hisn.selected.budget"))
    }

    func testFeedRecordOnlyRises() {
        var record = FeedWatch.recording(40, day: day, into: [:])
        record = FeedWatch.recording(20, day: day, into: record)
        XCTAssertEqual(record[day], 40)
        record = FeedWatch.recording(90, day: day, into: record)
        XCTAssertEqual(record[day], 90)
    }

    func testGainsCountWholeMeasuredDaysOnly() {
        let measured = ["2026-11-20": 200, "2026-11-21": 100, "2026-11-22": 30, "2026-11-23": 10, "2026-11-19": 0]
        let gains = GivenBack.gains(beforeMinutes: 180, measured: measured, startDay: "2026-11-20", today: day)
        XCTAssertEqual(gains, ["2026-11-20": 0, "2026-11-21": 80, "2026-11-22": 150])
        XCTAssertEqual(GivenBack.total(gains), 230)
        XCTAssertTrue(GivenBack.gains(beforeMinutes: 0, measured: measured, startDay: "2026-11-20", today: day).isEmpty)
    }

    func testPercent() {
        XCTAssertEqual(GivenBack.percent(before: 225, now: 165), -27)
        XCTAssertEqual(GivenBack.percent(before: 0, now: 50), 0)
    }

    // MARK: - Format, today's three, shield chip

    func testFormat() {
        XCTAssertTrue(UnrotFormat.parts(minutes: 59) == (0, 0, 59))
        XCTAssertTrue(UnrotFormat.parts(minutes: 1500) == (1, 1, 0))
        XCTAssertEqual(UnrotFormat.short(minutes: 66), "1 h 06")
        XCTAssertEqual(UnrotFormat.short(minutes: 45), "45 m")
        XCTAssertEqual(UnrotFormat.clock(seconds: 409), "6:49")
    }

    func testDayNumberStartsAtOne() {
        let calendar = Calendar(identifier: .gregorian)
        let start = Date(timeIntervalSince1970: 1_700_000_000)
        XCTAssertEqual(UnrotFormat.dayNumber(start: start, now: start, calendar: calendar), 1)
        XCTAssertEqual(UnrotFormat.dayNumber(start: start, now: start.addingTimeInterval(86400 * 2), calendar: calendar), 3)
    }

    func testTodaysThree() {
        let items = TodaysThree.items(checkedIn: true, pauses: 0, usedToday: 30, beforeMinutes: 120)
        XCTAssertEqual(items.map(\.done), [true, false, true])
        XCTAssertFalse(TodaysThree.items(checkedIn: false, pauses: 1, usedToday: nil, beforeMinutes: 120)[2].done)
    }

    func testShieldChipOrder() {
        XCTAssertEqual(ShieldSummary.chip(openPanes: 2, setupLeft: 1, quietNow: true), .steps(1))
        XCTAssertEqual(ShieldSummary.chip(openPanes: 2, setupLeft: 0, quietNow: true), .open(2))
        XCTAssertEqual(ShieldSummary.chip(openPanes: 0, setupLeft: 0, quietNow: true), .night)
        XCTAssertEqual(ShieldSummary.chip(openPanes: 0, setupLeft: 0, quietNow: false), .on)
    }

    func testEditionNamesAndBrand() {
        XCTAssertTrue(Edition.isUnrot)
        XCTAssertEqual(Edition.appGroup, "group.app.unrot.mobile")
        XCTAssertEqual(MobileProtectionPolicy.dnsProviderBundleIdentifier, "app.unrot.mobile.dnsproxy")
        XCTAssertEqual(Edition.brand("Hisn is protecting this app"), "Unrot is protecting this app")
        XCTAssertEqual(ShieldTextPolicy.copy(for: .always, language: .english).title, "Unrot is protecting this app")
    }

    // MARK: - Keys built at run time

    func testEveryBuiltKeyIsTranslatedInBothLanguages() throws {
        var keys = GlassStage.nameKeys
        keys += ["home", "shield", "progress", "you"].map { "u.tab." + $0 }
        keys += UnrotShieldFacts.Pane.allCases.flatMap { ["u.pane." + $0.rawValue, "u.pane." + $0.rawValue + ".sub"] }
        keys += UnrotShieldView.Tab.allCases.map { "u.shield.tab." + $0.rawValue }
        keys += (1...5).map { "u.ob.before.h\($0)" }
        keys += ["morning", "work", "evening", "bed"].flatMap { ["u.ob.when." + $0, "u.peak." + $0] }
        keys += ["tired", "behind", "low", "empty"].map { "u.ob.feel." + $0 }
        keys += (1...4).flatMap { ["u.ob.trick\($0)", "u.ob.trick\($0).body", "u.ob.trick\($0).answer"] }
        keys += (0..<8).map { "u.thought.\($0)" }
        keys += TodaysThree.items(checkedIn: false, pauses: 0, usedToday: nil, beforeMinutes: 0)
            .flatMap { [$0.key, $0.key + ".sub"] }
        for language in ["en", "ar"] {
            let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"))
            let bundle = try XCTUnwrap(Bundle(path: path))
            for key in keys {
                XCTAssertNotEqual(bundle.localizedString(forKey: key, value: nil, table: nil), key, "\(language): \(key)")
            }
            // The shared screens are renamed in the built app, keys untouched.
            let name = bundle.localizedString(forKey: "app.name", value: nil, table: nil)
            XCTAssertFalse(name.contains("Hisn") || name.contains("حصن"), name)
        }
    }

    // MARK: - Lock Unrot

    func testLockFlowFollowsTheVault() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let until = now.addingTimeInterval(30 * 86400)
        XCTAssertEqual(UnrotLockFlow.step(phase: nil, confirmed: false), .choose)
        XCTAssertEqual(UnrotLockFlow.step(phase: .revealed(passcode: "2580"), confirmed: true), .choose)
        XCTAssertEqual(UnrotLockFlow.step(phase: .setup(passcode: "2580", hidesAt: now), confirmed: false),
                       .enterCode(passcode: "2580", hidesAt: now))
        XCTAssertEqual(UnrotLockFlow.step(phase: .sealed(until: until), confirmed: false), .confirm(until: until))
        XCTAssertEqual(UnrotLockFlow.step(phase: .sealed(until: until), confirmed: true), .locked(until: until))
        XCTAssertEqual(UnrotLockFlow.step(phase: .due(since: until), confirmed: true), .due)
    }

    func testOnlyAShownLevelCountsAsLocked() {
        XCTAssertFalse(UnrotLockFlow.isLocked(.selfOnly))
        XCTAssertTrue(UnrotLockFlow.isLocked(.passcodeVault))
        XCTAssertTrue(UnrotLockFlow.isLocked(.supervised))
    }

    /// The real vault, start to finish: made, shown, sealed, then locked only once the person confirms.
    @MainActor
    func testVaultWalksTheFlowToLocked() {
        var clock = Date(timeIntervalSince1970: 1_800_000_000)
        let vault = PasscodeVaultController(storage: MemoryPasscodeVaultStorage(), clock: { clock })
        XCTAssertEqual(UnrotLockFlow.step(phase: vault.phase, confirmed: false), .choose)
        vault.create(days: 30)
        guard case .enterCode? = Optional(UnrotLockFlow.step(phase: vault.phase, confirmed: false)) else {
            return XCTFail("expected the code on screen")
        }
        vault.seal()
        clock = clock.addingTimeInterval(60)
        vault.tick()
        guard case .confirm? = Optional(UnrotLockFlow.step(phase: vault.phase, confirmed: false)) else {
            return XCTFail("expected confirmation")
        }
        XCTAssertEqual(vault.facts?.holdsPasscode(at: clock), true)
    }

    // MARK: - Notes calendar

    func testMonthGridLinesUpWeekdaysInWholeWeeks() {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        cal.firstWeekday = 2 // Monday
        let nov = cal.date(from: DateComponents(year: 2026, month: 11, day: 23))!
        let grid = NotesCalendar.monthGrid(for: nov, calendar: cal)
        XCTAssertEqual(grid.count % 7, 0)
        XCTAssertEqual(grid.compactMap { $0 }.count, 30)
        // 1 November 2026 is a Sunday: six blanks before it in a Monday-first week.
        XCTAssertEqual(grid.prefix(6).allSatisfy { $0 == nil }, true)
        XCTAssertEqual(cal.component(.day, from: grid[6]!), 1)
        XCTAssertEqual(NotesCalendar.weekdaySymbols(calendar: cal).count, 7)
        XCTAssertEqual(NotesCalendar.weekdaySymbols(calendar: cal).first, cal.veryShortStandaloneWeekdaySymbols[1])
    }

    @MainActor
    func testJournalKeepsDaysNewestFirstAndRefusesTheFuture() {
        let store = UnrotStore(defaults: UserDefaults(suiteName: "unrot.journal.\(UUID().uuidString)")!)
        let now = Date()
        let cal = Calendar.current
        let twoDaysAgo = cal.date(byAdding: .day, value: -2, to: now)!
        store.addNote("today", on: now, now: now)
        store.addNote("earlier", on: twoDaysAgo, now: now)
        store.addNote("tomorrow", on: cal.date(byAdding: .day, value: 1, to: now)!, now: now)
        XCTAssertEqual(store.notes, ["today", "earlier"])
        XCTAssertEqual(store.notes(on: twoDaysAgo).map(\.text), ["earlier"])
        XCTAssertEqual(store.noteDays, [UnrotFormat.dayKey(now), UnrotFormat.dayKey(twoDaysAgo)])
        store.removeNote(id: store.journal[0].id)
        XCTAssertEqual(store.notes, ["earlier"])
    }

    @MainActor
    func testOldNotesMoveIntoTheJournalOnce() {
        let defaults = UserDefaults(suiteName: "unrot.migrate.\(UUID().uuidString)")!
        defaults.set(["one", "two"], forKey: UnrotStore.Key.notes)
        let store = UnrotStore(defaults: defaults)
        XCTAssertEqual(store.notes, ["one", "two"])
        XCTAssertNil(defaults.stringArray(forKey: UnrotStore.Key.notes))
        XCTAssertEqual(UnrotStore(defaults: defaults).notes.count, 2)
    }

    // MARK: - Moments

    @MainActor
    func testStageUpNeverOnFirstLookThenOncePerNewStage() {
        let defaults = UserDefaults(suiteName: "unrot.stage.\(UUID().uuidString)")!
        let store = UnrotStore(defaults: defaults)
        XCTAssertNil(store.stageToCelebrate(), "a first look never celebrates")
        XCTAssertEqual(store.celebratedStage, 0)
        // A day with an hour given back: Young plant (stage 3).
        let cal = Calendar.current
        let start = cal.date(byAdding: .day, value: -3, to: Date())!
        store.finishOnboarding(.init(beforeMinutes: 120), now: start)
        defaults.set([UnrotFormat.dayKey(cal.date(byAdding: .day, value: -1, to: Date())!): 60], forKey: UnrotStore.Key.feed)
        store.reload()
        XCTAssertEqual(store.stageToCelebrate(), 3)
        XCTAssertNil(store.stageToCelebrate(), "each stage is celebrated once")
    }

    func testShareCardCarriesGrowthOnly() {
        let lines = ShareCardContent.lines(stage: 9, givenBack: 710, day: 9)
        XCTAssertEqual(lines.stageKey, "u.stage.tree")
        XCTAssertEqual(lines.givenBack, "11 h 50")
        // The card's whole content is these three lines: no adult counts, notes or check-ins.
        XCTAssertEqual(Mirror(reflecting: lines).children.count, 3)
    }

    func testTourHasFiveTranslatedStops() throws {
        XCTAssertEqual(TourSpot.allCases.count, 5)
        let path = try XCTUnwrap(Bundle.main.path(forResource: "en", ofType: "lproj"))
        let bundle = try XCTUnwrap(Bundle(path: path))
        for spot in TourSpot.allCases {
            for key in ["u.tour.title.\(spot.rawValue)", "u.tour.body.\(spot.rawValue)"] {
                XCTAssertNotEqual(bundle.localizedString(forKey: key, value: nil, table: nil), key)
            }
        }
    }

    // MARK: - Reminders, widget, help

    func testReminderPackShipsWithTheAppAndParses() {
        XCTAssertGreaterThanOrEqual(UnrotReminders.all.count, 80, "the shared pack is bundled")
        XCTAssertTrue(UnrotReminders.all.allSatisfy { !$0.en.isEmpty && !$0.ar.isEmpty })
        XCTAssertTrue(UnrotReminders.parse(Data("{}".utf8)).isEmpty, "a damaged pack is no lines, not a crash")
        let day = Date(timeIntervalSince1970: 1_800_000_000)
        XCTAssertEqual(UnrotReminders.forDay(day), UnrotReminders.forDay(day.addingTimeInterval(3600)))
    }

    func testReminderPlanUsesTheirTimesAndNeverThePast() {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        let now = cal.date(from: DateComponents(year: 2026, month: 11, day: 23, hour: 13))!
        let plan = ReminderPlan.requests(now: now, days: 7, when: ["bed", "morning"], count: 86, calendar: cal)
        // Today: the morning slot has passed, bed has not; then two a day for six days.
        XCTAssertEqual(plan.count, 1 + 6 * 2)
        XCTAssertEqual(plan.first?.date.hour, 22)
        XCTAssertEqual(Set(plan.map(\.id)).count, plan.count)
        XCTAssertEqual(Set(plan.map(\.index)).count, plan.count, "no line repeats in a week")
        XCTAssertTrue(plan.allSatisfy { $0.id.hasPrefix(ReminderPlan.prefix) })
        XCTAssertEqual(ReminderPlan.slots(for: []).map(\.key), ["evening"])
        XCTAssertTrue(ReminderPlan.requests(now: now, when: ["work"], count: 0, calendar: cal).isEmpty)
    }

    func testWidgetSnapshotFollowsTheGlassAndCarriesNothingPrivate() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let snapshot = WidgetSnapshot(givenBack: 90, day: 4, crackedAt: now.timeIntervalSince1970 - 60, sealedAt: 0,
                                      brokenAt: 0, sealedDay: nil, night: false, updated: now)
        XCTAssertEqual(snapshot.glass(at: now).look, .cracked)
        let change = try! XCTUnwrap(snapshot.nextChange(after: now))
        XCTAssertEqual(snapshot.glass(at: change).look, .clear)
        let fields = Set(Mirror(reflecting: snapshot).children.compactMap(\.label))
        XCTAssertEqual(fields, ["givenBack", "day", "crackedAt", "sealedAt", "brokenAt", "sealedDay", "night", "updated"])
    }

    func testEveryHelpAnswerIsTranslated() throws {
        for language in ["en", "ar"] {
            let bundle = try XCTUnwrap(Bundle(path: try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"))))
            for i in 0..<UnrotHelpSheet.count {
                for key in ["u.help.q\(i)", "u.help.a\(i)"] {
                    XCTAssertNotEqual(bundle.localizedString(forKey: key, value: nil, table: nil), key, "\(language) \(key)")
                }
            }
        }
    }

    // MARK: - Links and the mood week

    func testLinksRoundTripAndRejectOthers() {
        for link in UnrotLink.allCases { XCTAssertEqual(UnrotLink(url: link.url), link) }
        XCTAssertNil(UnrotLink(url: URL(string: "unrot://somewhere")!))
        XCTAssertNil(UnrotLink(url: URL(string: "https://breathe")!))
    }

    func testTopPullIgnoresNothingAndBreaksTiesStably() {
        XCTAssertEqual(MoodWeek.topPull(["tired", "bored", "tired", "none", "none", "none"]), "tired")
        XCTAssertNil(MoodWeek.topPull(["none", "none"]))
        XCTAssertEqual(MoodWeek.topPull(["stress", "alone"]), MoodWeek.topPull(["alone", "stress"]))
    }

    // MARK: - Day page

    func testDaySummaryReadsEveryRecordForThatDayOnly() {
        let day = DaySummary.make(key: "2026-11-21", today: "2026-11-23", startDay: "2026-11-15", before: 180,
                                  feed: ["2026-11-21": 120, "2026-11-22": 300], gains: ["2026-11-21": 60],
                                  mood: (1, "tired"), pauses: 2, tries: 1, notes: ["hard evening"])
        XCTAssertEqual(day.feed, 120)
        XCTAssertEqual(day.gain, 60)
        XCTAssertEqual(day.difference, -60)
        XCTAssertEqual(day.pull, "tired")
        XCTAssertEqual(day.notes, ["hard evening"])
        XCTAssertFalse(day.isToday || day.isFuture || day.beforeStart)
    }

    func testDaySummaryMarksTodayFutureAndBeforeStart() {
        func make(_ key: String) -> DaySummary {
            DaySummary.make(key: key, today: "2026-11-23", startDay: "2026-11-15", before: 120, feed: [:], gains: [:],
                            mood: (2, "none"), pauses: 0, tries: 0, notes: [])
        }
        XCTAssertTrue(make("2026-11-23").isToday)
        XCTAssertTrue(make("2026-11-24").isFuture)
        XCTAssertTrue(make("2026-11-10").beforeStart)
        XCTAssertNil(make("2026-11-20").difference, "an unmeasured day has no difference")
        XCTAssertNil(make("2026-11-20").pull, "\"nothing today\" is not a pull")
    }

    @MainActor
    func testStoreSummaryCountsThatDaysNotesAndCheckIn() {
        let store = UnrotStore(defaults: UserDefaults(suiteName: "unrot.day.\(UUID().uuidString)")!)
        let yesterday = Calendar.current.date(byAdding: .day, value: -1, to: Date())!
        store.addNote("yesterday's note", on: yesterday)
        store.addNote("today's note")
        store.checkIn(mood: 4, pull: "bored", now: yesterday)
        let summary = store.summary(for: yesterday)
        XCTAssertEqual(summary.notes, ["yesterday's note"])
        XCTAssertEqual(summary.mood, 4)
        XCTAssertEqual(summary.pull, "bored")
    }

    func testRangeStatsLeaveTodayOutAndFindTheLightestDay() {
        let days = ["2026-11-20", "2026-11-21", "2026-11-22", "2026-11-23"]
        let stats = RangeStats.make(days: days, today: "2026-11-23",
                                    feed: ["2026-11-20": 200, "2026-11-21": 90, "2026-11-23": 5],
                                    gains: ["2026-11-20": 0, "2026-11-21": 90], before: 180)
        XCTAssertEqual(stats.average, 145)
        XCTAssertEqual(stats.measuredDays, 2)
        XCTAssertEqual(stats.underBefore, 1)
        XCTAssertEqual(stats.lightest?.key, "2026-11-21")
        XCTAssertEqual(stats.givenBack, 90)
        XCTAssertNil(RangeStats.make(days: days, today: "2026-11-23", feed: [:], gains: [:], before: 180).average)
    }

    // MARK: - Path and trend

    func testStagePathDatesEachStageByRunningTotal() {
        let gains = ["2026-11-20": 4, "2026-11-21": 20, "2026-11-22": 40, "2026-11-23": 0]
        let steps = StagePath.steps(gains: gains, startDay: "2026-11-19")
        XCTAssertEqual(steps.count, 7)
        XCTAssertEqual(steps[0].reached, "2026-11-19")
        XCTAssertEqual(steps[1].reached, "2026-11-21", "5 m reached on the second day (4 + 20)")
        XCTAssertEqual(steps[2].reached, "2026-11-21", "15 m the same day")
        XCTAssertEqual(steps[3].reached, "2026-11-22", "1 h on the third (64)")
        XCTAssertNil(steps[4].reached)
    }

    func testWeekTrendNeedsTwoWeeksOfReadings() {
        let cal = Calendar(identifier: .gregorian)
        var c = cal; c.timeZone = TimeZone(identifier: "UTC")!
        let today = c.date(from: DateComponents(year: 2026, month: 11, day: 23))!
        let days = UnrotFormat.lastDays(14, now: today, calendar: c).map { UnrotFormat.dayKey($0, calendar: c) }
        var feed: [String: Int] = [:]
        for (i, key) in days.enumerated() { feed[key] = i < 6 ? 200 : 140 }
        let trend = try! XCTUnwrap(WeekTrend.make(days14: days, today: days.last!, feed: feed))
        XCTAssertEqual(trend.lastWeek, 200)
        XCTAssertEqual(trend.thisWeek, 140)
        XCTAssertEqual(trend.percent, -30)
        XCTAssertNil(WeekTrend.make(days14: days, today: days.last!, feed: [days[12]: 50]))
    }

    func testFeedBeforeStartIsIgnored() {
        var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "UTC")!
        let start = cal.date(from: DateComponents(year: 2026, month: 11, day: 23, hour: 9))!
        let days = ["2026-11-21": 200, "2026-11-22": 180, "2026-11-23": 40, "2026-11-24": 30]
        XCTAssertEqual(UnrotFormat.since(start, days, calendar: cal), ["2026-11-23": 40, "2026-11-24": 30])
        XCTAssertEqual(UnrotFormat.since(nil, days, calendar: cal), days)
    }

    func testShowingUpMilestones() {
        XCTAssertEqual(ShowingUp.make(day: 1), .init(day: 1, reached: 1, next: 3, part: 0))
        XCTAssertEqual(ShowingUp.make(day: 15).reached, 14)
        XCTAssertEqual(ShowingUp.make(day: 15).next, 30)
        XCTAssertEqual(ShowingUp.make(day: 15).daysToNext, 15)
        XCTAssertEqual(ShowingUp.make(day: 22).part, 0.5, accuracy: 0.001)
        XCTAssertEqual(ShowingUp.make(day: 400).next, nil)
        XCTAssertEqual(ShowingUp.make(day: 400).part, 1)
        XCTAssertEqual(ShowingUp.make(day: 0).day, 1)
        XCTAssertEqual(ShowingUp.make(day: 15).lineKey, "u.showing.14")
    }

    func testAgainstBefore() {
        XCTAssertNil(AgainstBefore.make(feed: nil, before: 200))
        XCTAssertNil(AgainstBefore.make(feed: 100, before: 0))
        XCTAssertEqual(AgainstBefore.make(feed: 120, before: 240), .init(difference: -120, part: 0.5))
        XCTAssertEqual(AgainstBefore.make(feed: 600, before: 240)?.part, 1)
        XCTAssertEqual(AgainstBefore.make(feed: 240, before: 240)?.difference, 0)
    }

    func testFocusClock() {
        let now = Date(timeIntervalSince1970: 1_000)
        XCTAssertEqual(FocusClock.remaining(until: 1_000 + 1122, now: now), 1122)
        XCTAssertEqual(FocusClock.remaining(until: 900, now: now), 0)
        XCTAssertEqual(FocusClock.text(seconds: 1122), "18:42")
        XCTAssertEqual(FocusClock.text(seconds: 3900), "1:05:00")
        XCTAssertEqual(FocusClock.part(until: 1_000 + 750, lengthMinutes: 25, now: now), 0.5, accuracy: 0.001)
        XCTAssertEqual(FocusClock.part(until: 1_000, lengthMinutes: 0, now: now), 0)
        XCTAssertEqual(FocusClock.step(5, by: -5), 5)
        XCTAssertEqual(FocusClock.step(180, by: 5), 180)
        XCTAssertEqual(FocusClock.step(25, by: 5), 30)
    }

    func testCheckInNext() {
        XCTAssertEqual(CheckInNext.suggest(mood: 3, pull: "stress"), .breathe)
        XCTAssertEqual(CheckInNext.suggest(mood: 2, pull: "bored"), .instead)
        XCTAssertEqual(CheckInNext.suggest(mood: 1, pull: "tired"), .rest)
        XCTAssertEqual(CheckInNext.suggest(mood: 4, pull: "alone"), .reachOut)
        XCTAssertEqual(CheckInNext.suggest(mood: 0, pull: "none"), .gentle)
        XCTAssertEqual(CheckInNext.suggest(mood: 3, pull: "none"), .keep)
        XCTAssertNil(CheckInNext.keep.actionKey)
        XCTAssertEqual(CheckInNext.rest.actionKey, "u.next.rest.action")
    }

    func testWeeklyRecap() {
        var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "UTC")!; cal.firstWeekday = 2
        // Tuesday 24 November 2026: last week is Mon 16 to Sun 22.
        let now = cal.date(from: DateComponents(year: 2026, month: 11, day: 24, hour: 12))!
        let weeks = WeeklyRecap.weeks(now: now, calendar: cal)!
        XCTAssertEqual(weeks.last.first, "2026-11-16")
        XCTAssertEqual(weeks.last.last, "2026-11-22")
        XCTAssertEqual(weeks.before.first, "2026-11-09")
        let feed = ["2026-11-16": 100, "2026-11-17": 100, "2026-11-09": 200, "2026-11-10": 200]
        let recap = WeeklyRecap.make(now: now, feed: feed, gains: ["2026-11-16": 60], before: 180,
                                     breaths: ["2026-11-18": 2, "2026-11-23": 5],
                                     pulls: ["2026-11-17": "tired", "2026-11-19": "tired", "2026-11-20": "none"], calendar: cal)!
        XCTAssertEqual(recap.weekKey, "2026-11-16")
        XCTAssertEqual(recap.stats.average, 100)
        XCTAssertEqual(recap.stats.givenBack, 60)
        XCTAssertEqual(recap.pauses, 2)
        XCTAssertEqual(recap.checkIns, 3)
        XCTAssertEqual(recap.topPull, "tired")
        XCTAssertEqual(recap.change, -50)
        XCTAssertTrue(recap.hasAnything)
        let empty = WeeklyRecap.make(now: now, feed: [:], gains: [:], before: 180, breaths: [:], pulls: [:], calendar: cal)!
        XCTAssertFalse(empty.hasAnything)
        XCTAssertNil(empty.change)
    }

    func testPullHours() {
        var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "UTC")!
        let day = cal.date(from: DateComponents(year: 2026, month: 11, day: 20))!.timeIntervalSince1970
        let times = [21, 21, 22, 22, 9, 23].map { day + Double($0 * 3600 + 60) }
        let b = PullHours.buckets(times, calendar: cal)
        XCTAssertEqual(b[21], 2); XCTAssertEqual(b[22], 2); XCTAssertEqual(b.reduce(0, +), 6)
        XCTAssertEqual(PullHours.peak(b), 21)
        XCTAssertNil(PullHours.peak(PullHours.buckets(Array(times.prefix(4)), calendar: cal)))
        var wrap = Array(repeating: 0, count: 24); wrap[23] = 3; wrap[0] = 3
        XCTAssertEqual(PullHours.peak(wrap), 23)
        XCTAssertEqual(PullHours.slot(beforeHour: 21), "evening")
        XCTAssertEqual(PullHours.slot(beforeHour: 23), "bed")
        XCTAssertEqual(PullHours.slot(beforeHour: 14), "work")
        XCTAssertEqual(PullHours.slot(beforeHour: 2), "bed")
        XCTAssertEqual(PullHours.slot(beforeHour: 8), "morning")
    }

    // MARK: - The colour of the moment (matches Android's GlassMoodTest)

    func testMoodPutsTheMostUrgentStateFirst() {
        XCTAssertEqual(UnrotMood.of(look: .broken, focusRunning: true, quietNow: true), .slip)
        XCTAssertEqual(UnrotMood.of(look: .cracked, focusRunning: true, quietNow: true), .hard)
        XCTAssertEqual(UnrotMood.of(look: .clear, focusRunning: true, quietNow: true), .night)
        XCTAssertEqual(UnrotMood.of(look: .gold, focusRunning: false, quietNow: true), .night)
        XCTAssertEqual(UnrotMood.of(look: .clear, focusRunning: true, quietNow: false), .working)
        XCTAssertEqual(UnrotMood.of(look: .gold, focusRunning: false, quietNow: false), .rest)
        XCTAssertEqual(UnrotMood.of(look: .clear, focusRunning: false, quietNow: false), .growing)
    }

    // MARK: - Unrot Plus (matches Android's PlusGateTest)

    func testPlusGateKeepsThreeRulesAndTwoWoodsFree() {
        XCTAssertTrue(PlusGate.open(.moreRules, plus: false, have: 2))
        XCTAssertFalse(PlusGate.open(.moreRules, plus: false, have: 3))
        XCTAssertTrue(PlusGate.open(.moreRules, plus: true, have: 40))
        XCTAssertTrue(PlusGate.open(.moreWoods, plus: false, have: 1))
        XCTAssertFalse(PlusGate.open(.moreWoods, plus: false, have: 2))
        XCTAssertFalse(PlusGate.open(.treeKinds, plus: false))
        XCTAssertTrue(PlusGate.open(.seasons, plus: true))
    }

    func testPlusIsOnWhileASubscriptionRunsOrForGood() {
        XCTAssertFalse(PlusGate.active(now: 1000, paidUntil: 0, lifetime: false))
        XCTAssertTrue(PlusGate.active(now: 1000, paidUntil: 2000, lifetime: false))
        XCTAssertFalse(PlusGate.active(now: 3000, paidUntil: 2000, lifetime: false))
        XCTAssertTrue(PlusGate.active(now: 3000, paidUntil: 0, lifetime: true))
    }

    // MARK: - Your wood (same layout as Android's placeTrees)

    func testWoodKeepsTreesOffThePath() {
        for count in [1, 4, 7, 18, 24, 45] {
            let spots = WoodLayout.spots(count: count)
            XCTAssertEqual(spots.count, count)
            for spot in spots {
                let x = spot.x * 360
                XCTAssertFalse(x > 160 && x < 222, "a tree stands on the path at \(x) for \(count) trees")
                XCTAssertTrue(spot.y > 0.4 && spot.y < 0.95)
            }
        }
        XCTAssertTrue(WoodLayout.spots(count: 0).isEmpty)
    }

    func testWoodKindsAreEarnedWithDays() {
        XCTAssertEqual(WoodLayout.kind(dayNumber: 1), "oak")
        XCTAssertEqual(WoodLayout.kind(dayNumber: 6), "oak")
        XCTAssertTrue(["oak", "pine"].contains(WoodLayout.kind(dayNumber: 9)))
        XCTAssertFalse((1..<45).map { WoodLayout.kind(dayNumber: $0) }.contains("willow"))
    }
}
