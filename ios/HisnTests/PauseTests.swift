import SwiftUI
import UIKit
import XCTest
@testable import HisnMobile

/// The iOS pause is a private breathing exercise, not a protection layer. These
/// tests pin what it stores (a day and a count), what it does at a day boundary,
/// the breath it guides, its English/Arabic copy, and that both stages render.
@MainActor
final class PauseTests: XCTestCase {
    private func utcCalendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    private func defaults() throws -> (UserDefaults, String) {
        let name = "hisn.tests.pause." + UUID().uuidString
        return (try XCTUnwrap(UserDefaults(suiteName: name)), name)
    }

    func testTallyStartsAtOneAndMergesTheSameMoment() {
        let base = Date(timeIntervalSince1970: 1_800_000_000)
        let first = PauseTally.recording(nil, now: base)
        XCTAssertEqual(first.count, 1)

        let reload = PauseTally.recording(first, now: base.addingTimeInterval(29.9))
        XCTAssertEqual(reload.count, 1, "a reopened sheet inside the merge window is the same pause")
        XCTAssertEqual(reload.at, first.at)

        let later = PauseTally.recording(first, now: base.addingTimeInterval(30))
        XCTAssertEqual(later.count, 2)
        XCTAssertEqual(later.at, base.addingTimeInterval(30))
    }

    func testAnotherDayStartsOver() {
        let calendar = utcCalendar()
        let before = Date(timeIntervalSince1970: 1_800_000_000)
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
        let (defaults, name) = try defaults()
        defer { defaults.removePersistentDomain(forName: name) }
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

    func testPauseCopyShipsInBothLanguages() throws {
        let keys = ["pause.open", "pause.cardBody", "pause.title", "pause.body",
                    "pause.inhale", "pause.exhale", "pause.progress", "pause.done",
                    "pause.question", "pause.again", "pause.count %lld", "pause.footer",
                    "pause.close", "pause.reminder.1", "pause.reminder.2", "pause.reminder.3",
                    "pause.reminder.4", "pause.reminder.5", "pause.need.tired", "pause.need.lonely",
                    "pause.need.stressed", "pause.need.bored", "pause.tip.tired", "pause.tip.lonely",
                    "pause.tip.stressed", "pause.tip.bored"]
        for language in ["en", "ar"] {
            let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"))
            let localized = try XCTUnwrap(Bundle(path: path))
            for key in keys {
                let text = localized.localizedString(forKey: key, value: nil, table: nil)
                XCTAssertFalse(text.isEmpty, "\(language): \(key)")
                XCTAssertNotEqual(text, key, "\(language): \(key) must not fall back to its key")
            }
        }
    }

    func testPauseRendersBreathingAndNeedsInBothLanguages() async throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        for language in ["en", "ar"] {
            for (stage, elapsed) in [("breathing", 0.0), ("needs", PauseBreath.total + 1)] {
                let (defaults, name) = try defaults()
                defer { defaults.removePersistentDomain(forName: name) }
                let view = PauseView(onClose: {}, defaults: defaults, clock: { now },
                                     startedAt: now.addingTimeInterval(-elapsed))
                    .environment(\.locale, Locale(identifier: language))
                    .environment(\.layoutDirection, language == "ar" ? .rightToLeft : .leftToRight)
                let image = try await render(view, size: CGSize(width: 390, height: 844))
                let pixels = try XCTUnwrap(image.cgImage?.dataProvider?.data) as Data
                let shades = Set(stride(from: 0, to: pixels.count, by: 97).map { pixels[$0] })
                XCTAssertGreaterThan(shades.count, 8, "A blank image is not evidence of rendered UI")
                let attachment = XCTAttachment(image: image)
                attachment.name = "pause-\(language)-\(stage)"
                attachment.lifetime = .keepAlways
                add(attachment)
            }
        }
    }

    func testPauseRendersAtAccessibilityTextSizes() async throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let (defaults, name) = try defaults()
        defer { defaults.removePersistentDomain(forName: name) }
        let view = PauseView(onClose: {}, defaults: defaults, clock: { now },
                             startedAt: now.addingTimeInterval(-(PauseBreath.total + 1)))
            .environment(\.dynamicTypeSize, .accessibility5)
            .environment(\.layoutDirection, .rightToLeft)
            .environment(\.locale, Locale(identifier: "ar"))
        let image = try await render(view, size: CGSize(width: 390, height: 844))
        XCTAssertEqual(image.size, CGSize(width: 390, height: 844))
    }

    private func render<V: View>(_ view: V, size: CGSize) async throws -> UIImage {
        let host = UIHostingController(rootView: view.frame(width: size.width, height: size.height))
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(origin: .zero, size: size)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        host.view.frame = CGRect(origin: .zero, size: size)
        try await Task.sleep(nanoseconds: 400_000_000)
        host.view.layoutIfNeeded()
        return UIGraphicsImageRenderer(size: size).image { _ in
            host.view.drawHierarchy(in: CGRect(origin: .zero, size: size), afterScreenUpdates: true)
        }
    }
}
