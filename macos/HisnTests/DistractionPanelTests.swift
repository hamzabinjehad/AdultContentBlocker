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
            window.orderFront(nil)
            for page in [ContentView.Page.today, .focus, .overview] {
                AppNavigation.shared.page = page
                try await Task.sleep(nanoseconds: 400_000_000)
                host.layoutSubtreeIfNeeded()
                let rep = try XCTUnwrap(host.bitmapImageRepForCachingDisplay(in: host.bounds))
                host.cacheDisplay(in: host.bounds, to: rep)
                try XCTUnwrap(rep.representation(using: .png, properties: [:]))
                    .write(to: directory.appendingPathComponent("unrot-\(page.rawValue)-\(dark ? "dark" : "light").png"))
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
