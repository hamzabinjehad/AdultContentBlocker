import AppKit
import SwiftUI
import XCTest
@testable import Hisn

/// The wait / closed panel: what it says for each decision and strictness,
/// where it is placed, and a rendered capture of each state for review
/// (written to /tmp/hisn-mac-ui/panel-*.png).
@MainActor
final class DistractionPanelTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_795_000_000)

    private func book(_ rule: DistractionRule) -> RuleBook {
        var book = RuleBook()
        book.rules = [rule]
        return book
    }

    func testWaitWordsShowOpensLeft() {
        let rule = DistractionRule(name: "Games and chat", targets: [.app("com.hnc.Discord")],
                                   action: .wait(seconds: 10, grows: true, opensPerDay: 5, openMinutes: 10))
        let model = DistractionPanelModel(decision: .wait(rule: rule.id, seconds: 15, opensLeft: 3, openMinutes: 10),
                                          appName: "Discord", appIcon: nil, book: book(rule), now: now)
        XCTAssertEqual(model.kind, .wait(seconds: 15))
        XCTAssertEqual(model.eyebrow, "Games and chat")
        XCTAssertEqual(model.title, "What did you open Discord for?")
        XCTAssertEqual(model.opensLeft, 3)
        XCTAssertEqual(model.opensPerDay, 5)
        XCTAssertEqual(model.openTitle, "Open for 10 minutes")
    }

    func testClosedOffersWhatTheStrictnessAllows() {
        for (strictness, delay, pass) in [(Strictness.gentle, Optional(0), false),
                                         (.firm, Optional(15), false),
                                         (.locked, nil, true)] {
            let rule = DistractionRule(name: "Work hours", targets: [.app("com.hnc.Discord")], action: .block,
                                       window: TimeWindow(startMinute: 9 * 60, endMinute: 17 * 60),
                                       strictness: strictness)
            let model = DistractionPanelModel(decision: .block(rule: rule.id, reason: .scheduled, until: now + 3600),
                                              appName: "Discord", appIcon: nil, book: book(rule), now: now)
            XCTAssertEqual(model.kind, .closed)
            XCTAssertEqual(model.title, "Discord is closed for now")
            XCTAssertEqual(model.snoozeDelay, delay, "\(strictness)")
            XCTAssertEqual(model.passAvailable, pass, "\(strictness)")
        }
    }

    func testFocusNeverOffersExtraMinutes() {
        var book = RuleBook()
        book.focus = FocusSessionState(startedAt: now, until: now + 1500, intent: "Write the report", targets: [])
        let model = DistractionPanelModel(decision: .block(rule: DistractionEngine.focusRuleID, reason: .focus,
                                                           until: now + 1500),
                                          appName: "Discord", appIcon: nil, book: book, now: now)
        XCTAssertEqual(model.title, "Discord is closed during Focus")
        XCTAssertEqual(model.detail, "You started this to: Write the report")
        XCTAssertNil(model.snoozeDelay)
    }

    func testPanelCoversARealWindowNotAPalette() {
        let tooltip = CGRect(x: 10, y: 10, width: 200, height: 40)
        let window = CGRect(x: 100, y: 80, width: 1200, height: 800)
        XCTAssertEqual(FrontWindowFrame.pick([tooltip, window]), window)
        XCTAssertNil(FrontWindowFrame.pick([tooltip]))
    }

    func testRuleWordsSayWhatIsLeft() {
        let limit = DistractionRule(name: "Feeds", targets: [.site("youtube.com"), .site("x.com")],
                                    action: .dailyLimit(minutesPerDay: 60))
        var day = RuleDay()
        day.usedSeconds[limit.id] = 38 * 60
        XCTAssertEqual(RuleSummary.detail(limit, day: day), "22 m left of 1 h")
        day.usedSeconds[limit.id] = 61 * 60
        XCTAssertEqual(RuleSummary.detail(limit, day: day), "Used for today")
        let wait = DistractionRule(name: "Chat", targets: [.site("discord.com")],
                                   action: .wait(seconds: 10, grows: true, opensPerDay: 5, openMinutes: 10),
                                   window: TimeWindow(startMinute: 540, endMinute: 1020, days: .workdays))
        day.opens[wait.id] = 3
        XCTAssertTrue(RuleSummary.detail(wait, day: day).hasPrefix("10 s pause · 2 of 5 opens left · "))
        XCTAssertTrue(RuleSummary.detail(wait, day: day).hasSuffix("Mon–Fri"))
        XCTAssertEqual(RuleSummary.targets(limit), "x.com, youtube.com")
        XCTAssertEqual(RuleSummary.duration(minutes: 95), "1 h 35 m")
        XCTAssertTrue(RuleSummary.outcome(.refused(until: nil)).isError)
        XCTAssertFalse(RuleSummary.outcome(.applied).isError)
    }

    func testEveryTemplateMakesAUsableRuleOnceItHasTargets() {
        for template in RuleTemplate.allCases {
            var rule = template.make()
            if rule.targets.isEmpty { rule.targets = [.app("com.hnc.Discord")] }
            XCTAssertEqual(rule.problems, [], template.rawValue)
        }
    }

    func testRulesPageRendersForVisualReview() async throws {
        let directory = URL(fileURLWithPath: "/tmp/hisn-mac-ui", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let suite = "unrot.tests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = DistractionStore(defaults: defaults)
        store.lockUntil = { nil }
        var feeds = RuleTemplate.dailyLimit.make(); feeds.strictness = .firm
        var wait = RuleTemplate.wait.make(); wait.name = "Games and chat"
        wait.targets = [.app("com.apple.Music"), .site("discord.com"), .site("twitch.tv")]
        let work = RuleTemplate.schedule.make()
        var night = RuleTemplate.quietHours.make(); night.strictness = .locked
        let feedsOnly = RuleTemplate.feedsOnly.make()
        for rule in [feeds, wait, work, night, feedsOnly] { XCTAssertEqual(store.propose(rule, replacing: nil), .applied) }
        let now = store.clock()
        store.record(subject: "site:youtube.com", from: now.addingTimeInterval(-1500), to: now.addingTimeInterval(-60))
        store.tick()

        for (name, dark, editing) in [("rules-light", false, Optional(wait)), ("rules-dark", true, Optional(feeds)),
                                      ("rules-none-light", false, nil)] {
            let size = NSSize(width: 1040, height: 900)
            let host = NSHostingView(rootView: DistractionRulesPage(store: store, editing: editing)
                .padding(28).frame(width: size.width, height: size.height, alignment: .top)
                .background(HnPalette.canvas).environment(\.locale, Locale(identifier: "en")))
            let window = NSWindow(contentRect: NSRect(origin: .zero, size: size), styleMask: [.borderless],
                                  backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.appearance = NSAppearance(named: dark ? .darkAqua : .aqua)
            window.contentView = host
            window.orderFront(nil)
            try await Task.sleep(nanoseconds: 400_000_000)
            host.layoutSubtreeIfNeeded()
            let rep = try XCTUnwrap(host.bitmapImageRepForCachingDisplay(in: host.bounds))
            host.cacheDisplay(in: host.bounds, to: rep)
            try XCTUnwrap(rep.representation(using: .png, properties: [:]))
                .write(to: directory.appendingPathComponent("\(name).png"))
            window.close()
        }
        for sheet in ["templates", "focus"] {
            let view: AnyView = sheet == "templates" ? AnyView(RuleTemplateSheet { _ in })
                                                     : AnyView(FocusStartSheet(store: store) {})
            let host = NSHostingView(rootView: view)
            host.frame = NSRect(origin: .zero, size: host.fittingSize)
            let window = NSWindow(contentRect: host.frame, styleMask: [.borderless], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.contentView = host
            window.orderFront(nil)
            try await Task.sleep(nanoseconds: 300_000_000)
            let rep = try XCTUnwrap(host.bitmapImageRepForCachingDisplay(in: host.bounds))
            host.cacheDisplay(in: host.bounds, to: rep)
            try XCTUnwrap(rep.representation(using: .png, properties: [:]))
                .write(to: directory.appendingPathComponent("rules-\(sheet).png"))
            window.close()
        }
    }

    func testHomeGroupsRulesByTimeAndOffersWhatIsMissing() {
        var calendar = Calendar(identifier: .gregorian); calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let noon = calendar.date(from: DateComponents(year: 2026, month: 11, day: 23, hour: 12))!
        let limit = RuleTemplate.dailyLimit.make()
        let night = RuleTemplate.quietHours.make()
        var off = RuleTemplate.schedule.make(); off.enabled = false
        let groups = UnrotHomePage.Groups(rules: [night, off, limit], now: noon, calendar: calendar)
        XCTAssertEqual(groups.running.map(\.id), [limit.id])
        XCTAssertEqual(groups.later.map(\.id), [night.id])
        XCTAssertEqual(groups.off.map(\.id), [off.id])
        XCTAssertEqual(UnrotHomePage.ideas(for: []), [.dailyLimit, .feedsOnly])
        XCTAssertEqual(UnrotHomePage.ideas(for: [limit, night]), [.feedsOnly, .wait])
        XCTAssertEqual(UnrotHomePage.countdown(3 * 3600 + 2 * 60 + 23), "3:02:23")
        XCTAssertEqual(UnrotHomePage.countdown(95), "1:35")
        XCTAssertEqual(UnrotHomePage.dateLine(noon, locale: Locale(identifier: "en_GB")), "Monday 23 November")
    }

    func testProgressUsesOnlyMeasuredDays() {
        var calendar = Calendar(identifier: .gregorian); calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let noon = calendar.date(from: DateComponents(year: 2026, month: 11, day: 23, hour: 12))!
        var book = RuleBook()
        XCTAssertNil(ProgressSummary(book: book, now: noon, calendar: calendar).lessThanBefore)
        XCTAssertEqual(ProgressSummary(book: book, now: noon, calendar: calendar).daysWithUnrot, 0)

        book.rules = [RuleTemplate.dailyLimit.make()]
        book.beforePerDay = 100 * 60
        book.history = [.init(day: "2026-11-21", feedSeconds: 40 * 60, pauses: 2, closed: 1),
                        .init(day: "2026-11-22", feedSeconds: 80 * 60, pauses: 1, closed: 0),
                        .init(day: "2026-10-01", feedSeconds: 0, pauses: 9, closed: 9)]
        book.pausesToday = 3
        let summary = ProgressSummary(book: book, now: noon, calendar: calendar)
        XCTAssertEqual(summary.days.count, 7)
        XCTAssertEqual(summary.days.last?.isToday, true)
        XCTAssertNil(summary.days.first?.feedSeconds, "no record is no bar, not a zero")
        XCTAssertEqual(summary.lessThanBefore ?? 0, 0.4, accuracy: 0.001, "60 m average against 100 m")
        XCTAssertEqual(summary.headline, "40% less feed time")
        XCTAssertEqual(summary.pauses, 6, "this week only")
        XCTAssertEqual(summary.daysWithUnrot, 4)
        XCTAssertEqual(ProgressSummary.plant(feedSeconds: 40 * 60, before: 100 * 60), "UnrotGlassTree")
        XCTAssertEqual(ProgressSummary.plant(feedSeconds: 80 * 60, before: 100 * 60), "UnrotGlassSprout")
        XCTAssertEqual(ProgressSummary.plant(feedSeconds: 300 * 60, before: 100 * 60), "UnrotGlassSeed")
        XCTAssertNil(ProgressSummary.plant(feedSeconds: nil, before: 100 * 60))
    }

    func testMenuPanelSummaryReadsTheDay() {
        var calendar = Calendar(identifier: .gregorian); calendar.timeZone = TimeZone(identifier: "UTC")!
        let noon = calendar.date(from: DateComponents(year: 2026, month: 11, day: 24, hour: 12))!
        var book = RuleBook()
        XCTAssertEqual(MenuPanelSummary(book: book, now: noon, calendar: calendar).ring, 0)
        XCTAssertFalse(MenuPanelSummary(book: book, now: noon, calendar: calendar).hasRules)
        book.roll(to: "2026-11-24")
        let limit = DistractionRule(name: "Feeds", targets: [.site("youtube.com")], action: .dailyLimit(minutesPerDay: 60))
        let work = DistractionRule(name: "Work", targets: [.site("reddit.com")], action: .block,
                                   window: TimeWindow(startMinute: 9 * 60, endMinute: 17 * 60))
        book.rules = [limit, work]
        book.ledger.record(subject: "site:youtube.com", from: noon.addingTimeInterval(-1800), to: noon.addingTimeInterval(-300),
                           calendar: calendar)
        book.refreshUsage()
        let summary = MenuPanelSummary(book: book, now: noon, calendar: calendar)
        XCTAssertEqual(summary.feedSeconds, 1500, accuracy: 0.5)
        XCTAssertEqual(summary.ring, 1500 / book.beforePerDay, accuracy: 0.001)
        XCTAssertEqual(summary.rows.map(\.name), ["Feeds", "Work"])
        XCTAssertEqual(summary.rows[0].fraction ?? 0, 1500.0 / 3600, accuracy: 0.001)
        XCTAssertEqual(summary.rows[1].fraction ?? 0, 3.0 / 8, accuracy: 0.001)
        XCTAssertEqual(summary.days.count, 7)
        XCTAssertTrue(summary.days.last?.isToday ?? false)
        XCTAssertNil(summary.days.first?.share, "a day with no record draws no bar")
    }

    func testOnboardingNumbersAndGoals() {
        XCTAssertEqual(UnrotOnboarding.daysAYear(minutesPerDay: 105), 27)
        XCTAssertEqual(UnrotOnboarding.daysBack(minutesPerDay: 105), 13)
        XCTAssertEqual(UnrotOnboarding.daysAYear(minutesPerDay: 1440), 365)
        XCTAssertEqual(UnrotOnboarding.daysAYear(minutesPerDay: 0), 0)
        XCTAssertEqual(UnrotOnboarding.yearsOfLife(minutesPerDay: 105), "3.6")
        // The person's own words reach the panel, and only when they gave some.
        var book = RuleBook()
        XCTAssertNil(DistractionPanelModel.own(book))
        book.ownReason = "I want my evenings back."
        XCTAssertEqual(DistractionPanelModel.own(book), "You said: “I want my evenings back.”")
        for goal in UnrotOnboarding.Goal.allCases {
            XCTAssertTrue(goal.template.make().problems.isEmpty, "\(goal) must make a rule that works with no choosing")
        }
        XCTAssertEqual(Set(UnrotOnboarding.Goal.allCases.map(\.template)).count, UnrotOnboarding.Goal.allCases.count)
    }

    func testBlockListFieldsTakeOneOrManyEntries() {
        let sites = BlockListEntry.addingSites("https://Example.com/page, reddit.com\nnot a site example.com", to: ["reddit.com"])
        XCTAssertEqual(sites.list, ["reddit.com", "example.com"])
        XCTAssertEqual(sites.added, ["example.com"])
        XCTAssertEqual(sites.duplicates, 2)
        XCTAssertEqual(sites.rejected, ["not", "a", "site"])
        let words = BlockListEntry.addingWords("betting odds, abc\ncasino", to: ["casino"])
        XCTAssertEqual(words.list, ["casino", "betting odds"])
        XCTAssertEqual(words.rejected, ["abc"])
        XCTAssertEqual(words.duplicates, 1)
        XCTAssertTrue(BlockListEntry.addingSites("   ", to: []).added.isEmpty)
    }

    func testSproutStagesFollowTimeGivenBack() {
        XCTAssertEqual(SproutProgress(givenBack: 0).stage, 1)
        let sprout = SproutProgress(givenBack: 10 * 60)
        XCTAssertEqual(sprout.stage, 2)
        XCTAssertEqual(sprout.fraction, 0.5, accuracy: 0.001)
        XCTAssertEqual(sprout.toNext ?? 0, 5 * 60, accuracy: 0.5)
        XCTAssertNil(SproutProgress(givenBack: 90_000).toNext)
        XCTAssertEqual(SproutProgress.parts(101_520).map { $0.0 + $0.1 }, ["1d", "4h", "12m"])
        XCTAssertEqual(SproutProgress.parts(300).map { $0.0 + $0.1 }, ["5m"])
    }

    /// The whole window in the Unrot edition, for review: Today and Limits.
    func testUnrotWindowRendersForVisualReview() async throws {
        let directory = URL(fileURLWithPath: "/tmp/hisn-mac-ui", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        MacEdition.override = .unrot
        let original = AppNavigation.shared.page
        // Sample rules in the shared store for the capture, removed afterwards.
        let store = DistractionStore.shared
        var added: [UUID] = []
        if store.book.rules.isEmpty {
            var feeds = RuleTemplate.dailyLimit.make(); feeds.strictness = .firm
            var chat = RuleTemplate.wait.make(); chat.name = "Games and chat"
            chat.targets = [.app("com.apple.Music"), .site("discord.com"), .site("twitch.tv")]
            var work = RuleTemplate.schedule.make()
            work.window = TimeWindow(startMinute: 0, endMinute: 1439)
            let night = RuleTemplate.quietHours.make()
            for rule in [feeds, chat, work, night] where store.propose(rule, replacing: nil) == .applied { added.append(rule.id) }
            let now = store.clock()
            store.record(subject: "site:youtube.com", from: now.addingTimeInterval(-1500), to: now.addingTimeInterval(-60))
            // Use no rule covers yet, so Today has something to suggest.
            store.record(subject: "site:chess.com", from: now.addingTimeInterval(-6000), to: now.addingTimeInterval(-4300))
            store.record(subject: "site:chess.com", from: now.addingTimeInterval(-4200), to: now.addingTimeInterval(-2500))
            store.tick()
        }
        defer { for id in added { var gentle = store.book.rules.first { $0.id == id }; gentle?.strictness = .gentle
                                  _ = store.propose(nil, replacing: id) } }
        defer { MacEdition.override = nil; AppNavigation.shared.page = original }
        let size = NSSize(width: 1180, height: 820)
        for dark in [false, true] {
            let host = NSHostingView(rootView: ContentView()
                .environment(\.locale, Locale(identifier: "en"))
                .frame(width: size.width, height: size.height))
            let window = NSWindow(contentRect: NSRect(origin: .zero, size: size),
                                  styleMask: [.titled, .closable, .resizable, .fullSizeContentView],
                                  backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.appearance = NSAppearance(named: dark ? .darkAqua : .aqua)
            window.contentView = host
            MainWindow.styleForUnrot(window)
            window.orderFront(nil)
            // The rule sheet and the template sheet in Unrot's dark look.
            if dark {
                var rule = store.book.rules.first ?? RuleTemplate.dailyLimit.make()
                for (name, view) in [("unrot-editor", AnyView(RuleEditor(rule: Binding(get: { rule }, set: { rule = $0 }),
                                                                         isNew: false, message: nil, save: {}, delete: {}, cancel: {})
                                                              .frame(width: 420).background(HnPalette.canvas))),
                                     ("unrot-templates", AnyView(RuleTemplateSheet { _ in })),
                                     ("unrot-focus", AnyView(FocusStartSheet(store: store) {})),
                                     ("unrot-menu", AnyView(UnrotMenuPanel(
                                         summary: MenuPanelSummary(book: store.book, now: store.clock(), calendar: store.calendar),
                                         protection: "Partly protected", now: store.clock()))),
                                     ("unrot-exceptions", AnyView(ExceptionsSheet(store: store) {}
                                                                  .background(HnPalette.canvas))),
                                     ("unrot-breathe-start", AnyView(UnrotBreatheView(onClose: {}).frame(width: 520, height: 680))),
                                     ("unrot-breathe-ready", AnyView(UnrotBreatheView(onClose: {}, clock: { Date(timeIntervalSince1970: 1000) }, stage: .ready,
                                         stageStarted: Date(timeIntervalSince1970: 998.55)).frame(width: 520, height: 680))),
                                     ("unrot-breathe", AnyView(UnrotBreatheView(onClose: {}, clock: { Date(timeIntervalSince1970: 1000) }, stage: .breathing,
                                         stageStarted: Date(timeIntervalSince1970: 987.4)).frame(width: 520, height: 680))),
                                     ("unrot-breathe-done", AnyView(UnrotBreatheView(onClose: {}, stage: .done)
                                         .frame(width: 520, height: 680))),
                                     ("unrot-welcome-1", AnyView(UnrotWelcomeView(initialStep: 0) {})),
                                     ("unrot-welcome-2", AnyView(UnrotWelcomeView(initialStep: 1) {})),
                                     ("unrot-welcome-3", AnyView(UnrotWelcomeView(initialStep: 2) {})),
                                     ("unrot-welcome-4", AnyView(UnrotWelcomeView(initialStep: 3, goals: [.sleep, .evenings]) {})),
                                     ("unrot-welcome-5", AnyView(UnrotWelcomeView(initialStep: 4) {})),
                                     ("unrot-welcome-6", AnyView(UnrotWelcomeView(initialStep: 5, goals: [.sleep, .evenings]) {}))] {
                    let sheetHost = NSHostingView(rootView: view.environment(\.colorScheme, .dark))
                    sheetHost.frame = NSRect(origin: .zero, size: sheetHost.fittingSize)
                    let sheetWindow = NSWindow(contentRect: sheetHost.frame, styleMask: [.borderless], backing: .buffered, defer: false)
                    sheetWindow.isReleasedWhenClosed = false
                    sheetWindow.appearance = NSAppearance(named: .darkAqua)
                    sheetWindow.contentView = sheetHost
                    sheetWindow.orderFront(nil)
                    try await Task.sleep(nanoseconds: 300_000_000)
                    let rep = try XCTUnwrap(sheetHost.bitmapImageRepForCachingDisplay(in: sheetHost.bounds))
                    sheetHost.cacheDisplay(in: sheetHost.bounds, to: rep)
                    try XCTUnwrap(rep.representation(using: .png, properties: [:]))
                        .write(to: directory.appendingPathComponent("\(name).png"))
                    sheetWindow.close()
                }
            }
            for page in [ContentView.Page.today, .progress, .overview, .rules, .lock, .settings, .setup] {
                AppNavigation.shared.page = page
                try await Task.sleep(nanoseconds: 400_000_000)
                host.layoutSubtreeIfNeeded()
                let rep = try XCTUnwrap(host.bitmapImageRepForCachingDisplay(in: host.bounds))
                host.cacheDisplay(in: host.bounds, to: rep)
                try XCTUnwrap(rep.representation(using: .png, properties: [:]))
                    .write(to: directory.appendingPathComponent("unrot-\(page.rawValue)-\(dark ? "dark" : "light").png"))
            }
            if dark {
                // One Settings topic, opened.
                SettingsPage.initialPane = .checking
                AppNavigation.shared.page = .today
                try await Task.sleep(nanoseconds: 200_000_000)
                AppNavigation.shared.page = .settings
                try await Task.sleep(nanoseconds: 400_000_000)
                host.layoutSubtreeIfNeeded()
                let paneRep = try XCTUnwrap(host.bitmapImageRepForCachingDisplay(in: host.bounds))
                host.cacheDisplay(in: host.bounds, to: paneRep)
                try XCTUnwrap(paneRep.representation(using: .png, properties: [:]))
                    .write(to: directory.appendingPathComponent("unrot-settings-checking-dark.png"))
                SettingsPage.initialPane = .mac
                AppNavigation.shared.page = .today
                try await Task.sleep(nanoseconds: 200_000_000)
                AppNavigation.shared.page = .settings
                try await Task.sleep(nanoseconds: 400_000_000)
                host.layoutSubtreeIfNeeded()
                let macRep = try XCTUnwrap(host.bitmapImageRepForCachingDisplay(in: host.bounds))
                host.cacheDisplay(in: host.bounds, to: macRep)
                try XCTUnwrap(macRep.representation(using: .png, properties: [:]))
                    .write(to: directory.appendingPathComponent("unrot-settings-mac-dark.png"))
                SettingsPage.initialPane = nil
                // Today while a Focus session runs.
                _ = store.setException("docs.google.com", on: true)
                if store.startFocus(minutes: 25, intent: "Write the report", targets: [], allowOnly: false,
                                    strictness: .gentle) {
                    AppNavigation.shared.page = .today
                    try await Task.sleep(nanoseconds: 500_000_000)
                    host.layoutSubtreeIfNeeded()
                    let rep = try XCTUnwrap(host.bitmapImageRepForCachingDisplay(in: host.bounds))
                    host.cacheDisplay(in: host.bounds, to: rep)
                    try XCTUnwrap(rep.representation(using: .png, properties: [:]))
                        .write(to: directory.appendingPathComponent("unrot-today-focus-dark.png"))
                    store.endFocus()
                }
                _ = store.setException("docs.google.com", on: false)
            }
            window.close()
        }
    }

    func testEveryPanelStateRendersForVisualReview() async throws {
        let directory = URL(fileURLWithPath: "/tmp/hisn-mac-ui", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let wait = DistractionRule(name: "Games and chat", targets: [.app("com.hnc.Discord")],
                                   action: .wait(seconds: 10, grows: true, opensPerDay: 5, openMinutes: 10))
        let closed = DistractionRule(name: "Work hours", targets: [.app("com.hnc.Discord")], action: .block,
                                     window: TimeWindow(startMinute: 9 * 60, endMinute: 17 * 60), strictness: .firm)
        let locked = DistractionRule(name: "Feeds", targets: [.app("com.hnc.Discord")],
                                     action: .dailyLimit(minutesPerDay: 60), strictness: .locked)
        let icon = NSWorkspace.shared.icon(forFile: "/System/Applications/Music.app")
        let states: [(String, RuleDecision, RuleBook)] = [
            ("wait", .wait(rule: wait.id, seconds: 15, opensLeft: 3, openMinutes: 10), book(wait)),
            ("closed-firm", .block(rule: closed.id, reason: .scheduled, until: now + 3600), book(closed)),
            ("limit-locked", .block(rule: locked.id, reason: .limitReached, until: now + 7200), book(locked)),
        ]
        for (name, decision, book) in states {
            for dark in [false, true] {
                let model = DistractionPanelModel(decision: decision, appName: "Discord", appIcon: icon,
                                                  book: book, now: now)
                let size = NSSize(width: 900, height: 620)
                let host = NSHostingView(rootView: DistractionPanelView(model: model, onChoice: { _ in })
                    .frame(width: size.width, height: size.height)
                    .background(Color(nsColor: .windowBackgroundColor)))
                let window = NSWindow(contentRect: NSRect(origin: .zero, size: size), styleMask: [.borderless],
                                      backing: .buffered, defer: false)
                window.isReleasedWhenClosed = false
                window.appearance = NSAppearance(named: dark ? .darkAqua : .aqua)
                window.contentView = host
                window.orderFront(nil)
                try await Task.sleep(nanoseconds: 250_000_000)
                host.layoutSubtreeIfNeeded()
                let rep = try XCTUnwrap(host.bitmapImageRepForCachingDisplay(in: host.bounds))
                host.cacheDisplay(in: host.bounds, to: rep)
                let data = try XCTUnwrap(rep.representation(using: .png, properties: [:]))
                XCTAssertGreaterThan(data.count, 2000)
                try data.write(to: directory.appendingPathComponent("panel-\(name)-\(dark ? "dark" : "light").png"))
                window.close()
            }
        }
    }
}
