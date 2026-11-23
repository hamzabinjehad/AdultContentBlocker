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
