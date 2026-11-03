import AppKit
import SwiftUI
import XCTest
@testable import Hisn

/// The Mac pause is a private breathing exercise, not a protection layer. These
/// tests pin what it stores (a day and a count), what it does at a day boundary,
/// the breath it guides, and that both languages render.
@MainActor
final class PauseTests: XCTestCase {
    private func utcCalendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    func testTallyStartsAtOneAndMergesTheSameMoment() {
        let base = Date(timeIntervalSince1970: 1_800_000_000)
        let first = PauseTally.recording(nil, now: base)
        XCTAssertEqual(first.count, 1)

        let reload = PauseTally.recording(first, now: base.addingTimeInterval(29.9))
        XCTAssertEqual(reload.count, 1, "a reload inside the merge window is the same pause")
        XCTAssertEqual(reload.at, first.at)

        let later = PauseTally.recording(first, now: base.addingTimeInterval(30))
        XCTAssertEqual(later.count, 2)
        XCTAssertEqual(later.at, base.addingTimeInterval(30))
    }

    func testAnotherDayStartsOver() {
        let calendar = utcCalendar()
        let before = Date(timeIntervalSince1970: 1_800_000_000) // 2027-01-15 UTC
        let after = before.addingTimeInterval(24 * 3600)
        let first = PauseTally.recording(nil, now: before, calendar: calendar)
        let next = PauseTally.recording(first, now: after, calendar: calendar)
        XCTAssertEqual(next.count, 1)
        XCTAssertNotEqual(next.day, first.day)
    }

    func testMalformedOrOverflowingTallyCannotGrowUnbounded() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let day = PauseTally.dayKey(for: now, calendar: utcCalendar())
        let zero = PauseTally(day: day, count: 0, at: now)
        XCTAssertEqual(PauseTally.recording(zero, now: now.addingTimeInterval(60),
                                            calendar: utcCalendar()).count, 1)
        let huge = PauseTally(day: day, count: PauseTally.maximum, at: now.addingTimeInterval(-60))
        XCTAssertEqual(PauseTally.recording(huge, now: now,
                                            calendar: utcCalendar()).count, PauseTally.maximum)
    }

    func testBreathTimelineMovesThroughThreeCyclesAndFinishes() {
        XCTAssertEqual(PauseBreath.total, 30)
        let start = PauseBreath.snapshot(at: 0)
        XCTAssertTrue(start.inhaling)
        XCTAssertEqual(start.cycle, 1)
        XCTAssertEqual(start.scale, 0.55, accuracy: 0.0001)
        XCTAssertFalse(start.finished)

        let top = PauseBreath.snapshot(at: 4)
        XCTAssertFalse(top.inhaling)
        XCTAssertEqual(top.scale, 1.0, accuracy: 0.0001)

        let second = PauseBreath.snapshot(at: 10)
        XCTAssertTrue(second.inhaling)
        XCTAssertEqual(second.cycle, 2)

        let almost = PauseBreath.snapshot(at: 29.9)
        XCTAssertFalse(almost.finished)
        XCTAssertLessThan(almost.progress, 1)

        let done = PauseBreath.snapshot(at: 30)
        XCTAssertTrue(done.finished)
        XCTAssertEqual(done.progress, 1)
    }

    func testRemindersTakeTurns() {
        XCTAssertEqual(pauseReminderIndex(forCount: 0), 1)
        XCTAssertEqual(pauseReminderIndex(forCount: 1), 1)
        XCTAssertEqual(pauseReminderIndex(forCount: 5), 5)
        XCTAssertEqual(pauseReminderIndex(forCount: 6), 1)
        XCTAssertEqual(pauseReminderIndex(forCount: 10), 5)
    }

    func testPauseStoreWritesOnlyADayCountAndTime() throws {
        let name = TestNamespace.make()
        defer { TestNamespace.dispose(name) }
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        let now = Date(timeIntervalSince1970: 1_800_000_000)

        let first = PauseStore.record(now: now, defaults: defaults)
        XCTAssertEqual(first.count, 1)
        let stored = try XCTUnwrap(PauseStore.load(defaults: defaults))
        XCTAssertEqual(stored, first)
        XCTAssertEqual(stored.day, PauseTally.dayKey(for: now))

        let merged = PauseStore.record(now: now.addingTimeInterval(5), defaults: defaults)
        XCTAssertEqual(merged.count, 1)
        let second = PauseStore.record(now: now.addingTimeInterval(60), defaults: defaults)
        XCTAssertEqual(second.count, 2)
    }

    func testPauseRendersInBothLanguages() async throws {
        try await render(suffix: "breathing", elapsed: 0)
        try await render(suffix: "needs", elapsed: PauseBreath.total + 1)
    }

    func testOverviewRendersThePauseEntryInBothLanguages() async throws {
        let originalPage = AppNavigation.shared.page
        defer { AppNavigation.shared.page = originalPage }
        AppNavigation.shared.page = .overview

        for language in ["en", "ar"] {
            let view = NSHostingView(rootView: ContentView()
                .environment(\.locale, Locale(identifier: language))
                .environment(\.layoutDirection, language == "ar" ? .rightToLeft : .leftToRight)
                .frame(width: 880, height: 1000))
            let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 880, height: 1000),
                                  styleMask: [.titled], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.contentView = view
            defer { window.close() }
            window.orderFront(nil)
            try await Task.sleep(nanoseconds: 400_000_000)
            view.layoutSubtreeIfNeeded()
            let bitmap = try XCTUnwrap(view.bitmapImageRepForCachingDisplay(in: view.bounds))
            view.cacheDisplay(in: view.bounds, to: bitmap)
            let data = try XCTUnwrap(bitmap.representation(using: .png, properties: [:]))
            XCTAssertGreaterThan(data.count, 2000)
        }
    }

    func testGuardAlertRendersInBothLanguages() async throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let alert = BrowserGuard.Alert(id: 1, name: "Chrome",
                                       reason: .extensionSilent,
                                       closeAt: now.addingTimeInterval(42))
        for language in ["en", "ar"] {
            let view = NSHostingView(rootView: ZStack {
                Color(red: 0.03, green: 0.04, blue: 0.11).ignoresSafeArea()
                GuardAlertCard(alert: alert, now: now, open: {})
                    .padding(22)
            }
            .environment(\.locale, Locale(identifier: language))
            .environment(\.layoutDirection, language == "ar" ? .rightToLeft : .leftToRight)
            .frame(width: 460, height: 320))
            let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 460, height: 320),
                                  styleMask: [.titled], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.contentView = view
            defer { window.close() }
            window.orderFront(nil)
            try await Task.sleep(nanoseconds: 300_000_000)
            view.layoutSubtreeIfNeeded()
            let bitmap = try XCTUnwrap(view.bitmapImageRepForCachingDisplay(in: view.bounds))
            view.cacheDisplay(in: view.bounds, to: bitmap)
            let data = try XCTUnwrap(bitmap.representation(using: .png, properties: [:]))
            XCTAssertGreaterThan(data.count, 2000, "\(language) guard card should render")
        }
    }

    private func render(suffix: String, elapsed: TimeInterval) async throws {
        let name = TestNamespace.make()
        defer { TestNamespace.dispose(name) }
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        let now = Date(timeIntervalSince1970: 1_800_000_000)

        for language in ["en", "ar"] {
            let view = NSHostingView(rootView: PauseView(
                onClose: {}, defaults: defaults, clock: { now },
                startedAt: now.addingTimeInterval(-elapsed))
                .environment(\.locale, Locale(identifier: language))
                .environment(\.layoutDirection, language == "ar" ? .rightToLeft : .leftToRight)
                .frame(width: 460, height: 640))
            let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 460, height: 640),
                                  styleMask: [.titled], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.contentView = view
            defer { window.close() }
            window.orderFront(nil)
            try await Task.sleep(nanoseconds: 300_000_000)
            view.layoutSubtreeIfNeeded()
            let bitmap = try XCTUnwrap(view.bitmapImageRepForCachingDisplay(in: view.bounds))
            view.cacheDisplay(in: view.bounds, to: bitmap)
            let data = try XCTUnwrap(bitmap.representation(using: .png, properties: [:]))
            XCTAssertGreaterThan(data.count, 2000, "\(language) \(suffix) should render content")
        }
    }
}
