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
}
