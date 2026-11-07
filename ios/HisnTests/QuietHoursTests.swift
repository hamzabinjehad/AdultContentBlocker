import FamilyControls
import SwiftUI
import UIKit
import XCTest
@testable import HisnMobile

/// Quiet hours are a daily DeviceActivity window for the selected apps. These
/// tests pin the rule model, the midnight split, both-clock activity checks and
/// the honest health states without needing Apple's real Screen Time service.
final class QuietHoursTests: XCTestCase {
    private var empty: FamilyActivitySelection { FamilyActivitySelection() }

    func testWindowDurationAndValidation() {
        typealias C = AppUsageConfiguration
        XCTAssertEqual(C.windowDuration(start: 22 * 60, end: 7 * 60), 9 * 60)
        XCTAssertEqual(C.windowDuration(start: 9 * 60, end: 17 * 60), 8 * 60)
        XCTAssertNil(C.windowDuration(start: 60, end: 60))
        XCTAssertNil(C.windowDuration(start: -1, end: 60))
        XCTAssertNil(C.windowDuration(start: 60, end: 1440))

        XCTAssertTrue(C.isValidWindow(start: 22 * 60, end: 7 * 60))
        XCTAssertTrue(C.isValidWindow(start: 9 * 60, end: 17 * 60))
        XCTAssertTrue(C.isValidWindow(start: 0, end: 15))
        XCTAssertFalse(C.isValidWindow(start: 60, end: 60), "zero-length")
        XCTAssertFalse(C.isValidWindow(start: 60, end: 74), "under Apple's 15-minute minimum")
        XCTAssertTrue(C.isValidWindow(start: 60, end: 75), "exactly 15 minutes")
        // A crossing window is split at midnight; each half must stand alone.
        XCTAssertFalse(C.isValidWindow(start: 23 * 60 + 45, end: 30), "the evening leg would be too short")
        XCTAssertTrue(C.isValidWindow(start: 23 * 60 + 44, end: 30))
        XCTAssertFalse(C.isValidWindow(start: 22 * 60, end: 10), "the morning leg would be too short")
        XCTAssertTrue(C.isValidWindow(start: 22 * 60, end: 15))
    }

    func testSameDayWindowRegistersOneActivity() throws {
        let plan = try XCTUnwrap(AppUsageConfiguration.makeWindowPlan(start: 9 * 60, end: 17 * 60))
        XCTAssertEqual(plan.legs, [
            .init(activity: AppUsageConfiguration.windowActivity, start: 9 * 60, end: 17 * 60),
        ])
        XCTAssertEqual(plan.activityNames, [AppUsageConfiguration.windowActivity])
    }

    func testCrossingWindowSplitsAtMidnightAndKeepsBothLegsSameDay() throws {
        let plan = try XCTUnwrap(AppUsageConfiguration.makeWindowPlan(start: 22 * 60, end: 7 * 60))
        XCTAssertEqual(plan.legs, [
            .init(activity: AppUsageConfiguration.windowEveningActivity,
                  start: 22 * 60, end: 23 * 60 + 59),
            .init(activity: AppUsageConfiguration.windowMorningActivity,
                  start: 0, end: 7 * 60),
        ])
        XCTAssertEqual(plan.activityNames, [
            AppUsageConfiguration.windowEveningActivity,
            AppUsageConfiguration.windowMorningActivity,
        ])
        XCTAssertTrue(AppUsageConfiguration(selection: empty, mode: .quietHours,
                                            minutes: 30, windowStart: 22 * 60, windowEnd: 7 * 60)
            .windowCrossesMidnight)
    }

    func testWindowActiveUsesTheRightSideOfMidnight() {
        typealias C = AppUsageConfiguration
        func at(_ hour: Int, _ minute: Int = 0) -> Date {
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = .current
            return calendar.date(from: DateComponents(year: 2026, month: 11, day: 3,
                                                      hour: hour, minute: minute))!
        }
        XCTAssertTrue(C.windowActive(start: 22 * 60, end: 7 * 60, at: at(22)))
        XCTAssertTrue(C.windowActive(start: 22 * 60, end: 7 * 60, at: at(23, 59)))
        XCTAssertTrue(C.windowActive(start: 22 * 60, end: 7 * 60, at: at(0)))
        XCTAssertTrue(C.windowActive(start: 22 * 60, end: 7 * 60, at: at(6, 59)))
        XCTAssertFalse(C.windowActive(start: 22 * 60, end: 7 * 60, at: at(7)), "the end minute is outside")
        XCTAssertFalse(C.windowActive(start: 22 * 60, end: 7 * 60, at: at(21, 59)))

        XCTAssertTrue(C.windowActive(start: 9 * 60, end: 17 * 60, at: at(9)))
        XCTAssertFalse(C.windowActive(start: 9 * 60, end: 17 * 60, at: at(17)))
        XCTAssertFalse(C.windowActive(start: 9 * 60, end: 17 * 60, at: at(8, 59)))
    }

    func testEitherClockInsideTheWindowKeepsTheShield() {
        typealias C = AppUsageConfiguration
        func at(_ hour: Int) -> Date {
            let calendar = C.dayCalendar(in: .current)
            return calendar.date(from: DateComponents(year: 2026, month: 11, day: 3, hour: hour))!
        }
        // Device clock moved past the window, Apple's time still inside: protected.
        XCTAssertTrue(C.windowActive(start: 22 * 60, end: 7 * 60,
                                     deviceNow: at(8), trustedNow: at(6)))
        // Device clock moved into the window, Apple's time outside: still protected.
        XCTAssertTrue(C.windowActive(start: 22 * 60, end: 7 * 60,
                                     deviceNow: at(23), trustedNow: at(12)))
        // Both clocks outside: clear.
        XCTAssertFalse(C.windowActive(start: 22 * 60, end: 7 * 60,
                                      deviceNow: at(12), trustedNow: at(8)))
        // No Apple reading yet: the device clock is the only one.
        XCTAssertTrue(C.windowActive(start: 22 * 60, end: 7 * 60,
                                     deviceNow: at(6), trustedNow: nil))
        // Both clocks outside, so the shield can be cleared.
        XCTAssertFalse(C.windowActive(start: 22 * 60, end: 7 * 60,
                                      deviceNow: at(8), trustedNow: at(7)))
    }

    func testHealthSeparatesRegisteredFromStoppedQuietHours() {
        typealias C = AppUsageConfiguration
        XCTAssertEqual(C.health(storageAvailable: true, hasSavedData: true, valid: true,
                                authorized: true, mode: .quietHours, registered: true),
                       .quietHoursRegistered)
        XCTAssertEqual(C.health(storageAvailable: true, hasSavedData: true, valid: true,
                                authorized: true, mode: .quietHours, registered: false),
                       .quietHoursStopped)
        XCTAssertTrue(C.Health.quietHoursStopped.needsAttention)
        XCTAssertFalse(C.Health.quietHoursRegistered.needsAttention)
        XCTAssertEqual(C.health(storageAvailable: true, hasSavedData: true, valid: true,
                                authorized: false, mode: .quietHours, registered: true),
                       .permissionMissing)
    }

    func testQuietHoursKeepTheirWindowAndPlan() {
        let rule = AppUsageConfiguration(selection: empty, mode: .quietHours, minutes: 30,
                                         windowStart: 22 * 60, windowEnd: 7 * 60)
        XCTAssertEqual(rule.windowStart, 22 * 60)
        XCTAssertEqual(rule.windowEnd, 7 * 60)
        XCTAssertEqual(rule.windowPlan?.legs.count, 2)
        // The empty picker selection is invalid on its own; the window is not the
        // reason a rule cannot be applied.
        XCTAssertTrue(AppUsageConfiguration.isValidWindow(start: rule.windowStart, end: rule.windowEnd))
    }

    func testInvalidWindowIsNotValidAndHasNoPlan() {
        let rule = AppUsageConfiguration(selection: empty, mode: .quietHours, minutes: 30,
                                         windowStart: 22 * 60, windowEnd: 22 * 60)
        XCTAssertFalse(rule.isValid)
        XCTAssertNil(rule.windowPlan)
    }

    func testRulesSavedBeforeQuietHoursDecodeWithDefaults() throws {
        let rule = AppUsageConfiguration(selection: empty, mode: .always, minutes: 30)
        let data = try JSONEncoder().encode(rule)
        var object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        object.removeValue(forKey: "windowStart")
        object.removeValue(forKey: "windowEnd")
        let old = try JSONSerialization.data(withJSONObject: object)
        let decoded = try JSONDecoder().decode(AppUsageConfiguration.self, from: old)
        XCTAssertEqual(decoded.mode, .always)
        XCTAssertEqual(decoded.windowStart, AppUsageConfiguration.defaultWindowStart)
        XCTAssertEqual(decoded.windowEnd, AppUsageConfiguration.defaultWindowEnd)
    }

    func testQuietHoursWindowIsFrozenDuringACommitment() {
        let first = AppUsageConfiguration(selection: empty, mode: .quietHours, minutes: 30,
                                          windowStart: 22 * 60, windowEnd: 7 * 60)
        let later = AppUsageConfiguration(selection: empty, mode: .quietHours, minutes: 30,
                                          windowStart: 23 * 60, windowEnd: 7 * 60)
        XCTAssertFalse(first.permits(later, committed: true))
        XCTAssertTrue(first.permits(first, committed: true))
        XCTAssertTrue(first.permits(later, committed: false))
        XCTAssertFalse(first.permits(AppUsageConfiguration(selection: empty, mode: .always, minutes: 30),
                                     committed: true))
    }

    func testCrossingWindowHealthNamesAreStable() {
        // The monitor matches callbacks by these strings; changing one without the
        // other would silently leave a shield in place or clear it at noon.
        XCTAssertEqual(AppUsageConfiguration.windowActivity, "hisn.selected.window")
        XCTAssertEqual(AppUsageConfiguration.windowEveningActivity, "hisn.selected.window.evening")
        XCTAssertEqual(AppUsageConfiguration.windowMorningActivity, "hisn.selected.window.morning")
        XCTAssertEqual(AppUsageConfiguration.windowStoreName, "hisn.apps.window")
    }


    // MARK: - Weekdays

    private var utc: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    /// 2 November 2026 is a Monday; the crossing-window cases lean on that.
    private func day(_ monthDay: Int, _ hour: Int, _ minute: Int = 0) -> Date {
        utc.date(from: DateComponents(year: 2026, month: 11, day: monthDay,
                                      hour: hour, minute: minute))!
    }

    func testEveryDayIsTheDefaultForRulesSavedBeforeWeekdays() {
        typealias C = AppUsageConfiguration
        XCTAssertEqual(C.allDays, 0b111_1111)
        let rule = C(selection: empty, mode: .quietHours, minutes: 30,
                     windowStart: 22 * 60, windowEnd: 7 * 60)
        XCTAssertEqual(rule.days, C.allDays)
        XCTAssertTrue(C.isValidDays(rule.days))
        XCTAssertFalse(C.isValidDays(0), "no day is never a valid window")
        XCTAssertFalse(C.isValidDays(0b1000_0000), "only seven bits exist")
    }

    func testDaysAreMondayFirst() {
        typealias C = AppUsageConfiguration
        XCTAssertEqual(C.dayBit(day(2, 9), calendar: utc), 0b000_0001, "Monday")
        XCTAssertEqual(C.dayBit(day(3, 9), calendar: utc), 0b000_0010, "Tuesday")
        XCTAssertEqual(C.dayBit(day(6, 9), calendar: utc), 0b001_0000, "Friday")
        XCTAssertEqual(C.dayBit(day(7, 9), calendar: utc), 0b010_0000, "Saturday")
        XCTAssertEqual(C.dayBit(day(8, 9), calendar: utc), 0b100_0000, "Sunday")
        XCTAssertEqual(C.weekdayMask, 0b001_1111)
        XCTAssertEqual(C.weekendMask, 0b110_0000)
    }

    func testACrossingWindowBelongsToTheDayItStarts() {
        typealias C = AppUsageConfiguration
        // Saturday 01:00 is Friday night: a weekdays-only window still holds.
        XCTAssertEqual(C.windowStartDay(start: 22 * 60, end: 7 * 60, at: day(7, 1), calendar: utc),
                       utc.startOfDay(for: day(6, 12)))
        XCTAssertTrue(C.windowActive(start: 22 * 60, end: 7 * 60, days: C.weekdayMask,
                                     at: day(7, 1), calendar: utc))
        // Saturday 23:00 begins a Saturday window: weekends only.
        XCTAssertTrue(C.windowActive(start: 22 * 60, end: 7 * 60, days: C.weekendMask,
                                     at: day(7, 23), calendar: utc))
        XCTAssertFalse(C.windowActive(start: 22 * 60, end: 7 * 60, days: C.weekdayMask,
                                      at: day(7, 23), calendar: utc))
        // Sunday 01:00 is still Saturday night.
        XCTAssertTrue(C.windowActive(start: 22 * 60, end: 7 * 60, days: C.weekendMask,
                                     at: day(8, 1), calendar: utc))
        XCTAssertFalse(C.windowActive(start: 22 * 60, end: 7 * 60, days: C.weekdayMask,
                                      at: day(8, 1), calendar: utc))
        // Sunday 23:00 begins a Sunday window: not a weekday.
        XCTAssertFalse(C.windowActive(start: 22 * 60, end: 7 * 60, days: C.weekdayMask,
                                      at: day(8, 23), calendar: utc))
    }

    func testASameDayWindowUsesItsOwnDay() {
        typealias C = AppUsageConfiguration
        XCTAssertTrue(C.windowActive(start: 9 * 60, end: 17 * 60, days: C.weekdayMask,
                                     at: day(2, 10), calendar: utc), "Monday")
        XCTAssertFalse(C.windowActive(start: 9 * 60, end: 17 * 60, days: C.weekdayMask,
                                      at: day(7, 10), calendar: utc), "Saturday")
        XCTAssertTrue(C.windowActive(start: 9 * 60, end: 17 * 60, days: C.weekendMask,
                                     at: day(7, 10), calendar: utc))
    }

    func testAMorningLegBelongsToTheEveningBefore() {
        typealias C = AppUsageConfiguration
        // Saturday 00:10 keeps Friday night's shield when Friday is chosen.
        XCTAssertTrue(C.windowLegAllowed(C.windowMorningActivity, days: C.weekdayMask,
                                         at: day(7, 0, 10), calendar: utc))
        XCTAssertFalse(C.windowLegAllowed(C.windowMorningActivity, days: C.weekdayMask,
                                          at: day(8, 0, 10), calendar: utc), "Sunday night is not a weekday night")
        XCTAssertTrue(C.windowLegAllowed(C.windowEveningActivity, days: C.weekendMask,
                                         at: day(7, 22), calendar: utc))
    }

    func testEitherClockUsesItsOwnStartDay() {
        typealias C = AppUsageConfiguration
        // Device clock says Saturday evening (not a weekday); Apple's reading
        // still puts the instant on Friday night in another zone: keep it.
        XCTAssertTrue(C.windowActive(start: 22 * 60, end: 7 * 60, days: C.weekdayMask,
                                     deviceNow: day(7, 22), trustedNow: day(6, 23), calendar: utc))
        // Both clocks on unselected start days: clear.
        XCTAssertFalse(C.windowActive(start: 22 * 60, end: 7 * 60, days: C.weekdayMask,
                                      deviceNow: day(7, 22), trustedNow: day(7, 23), calendar: utc))
    }

    func testQuietHoursWithNoChosenDayIsNotValid() {
        typealias C = AppUsageConfiguration
        // The rule's own validity also requires a selection, which the
        // simulator's picker cannot produce; the day clause is pinned directly.
        XCTAssertFalse(C.isValidDays(0))
        XCTAssertTrue(C.isValidDays(C.weekdayMask))
        XCTAssertTrue(C.isValidDays(C.weekendMask))
        XCTAssertTrue(C.isValidWindow(start: 22 * 60, end: 7 * 60))
    }

    func testWeekdaysAreFrozenDuringACommitment() {
        typealias C = AppUsageConfiguration
        let everyDay = C(selection: empty, mode: .quietHours, minutes: 30,
                         windowStart: 22 * 60, windowEnd: 7 * 60)
        let weekdays = C(selection: empty, mode: .quietHours, minutes: 30,
                         windowStart: 22 * 60, windowEnd: 7 * 60, days: C.weekdayMask)
        XCTAssertFalse(everyDay.permits(weekdays, committed: true))
        XCTAssertTrue(everyDay.permits(everyDay, committed: true))
        XCTAssertTrue(everyDay.permits(weekdays, committed: false))
    }

    func testRulesSavedBeforeWeekdaysDecodeAsEveryDay() throws {
        let rule = AppUsageConfiguration(selection: empty, mode: .quietHours, minutes: 30,
                                         windowStart: 22 * 60, windowEnd: 7 * 60,
                                         days: AppUsageConfiguration.weekendMask)
        var object = try XCTUnwrap(JSONSerialization.jsonObject(
            with: JSONEncoder().encode(rule)) as? [String: Any])
        object.removeValue(forKey: "days")
        let decoded = try JSONDecoder().decode(
            AppUsageConfiguration.self, from: JSONSerialization.data(withJSONObject: object))
        XCTAssertEqual(decoded.days, AppUsageConfiguration.allDays)
    }

    @MainActor func testQuietHoursEditorRendersInBothLanguages() async throws {
        let protection = ProtectionController(commitmentStore: MemoryCommitmentStore())
        let usage = AppUsageController(defaults: nil)
        for language in ["en", "ar"] {
            let view = RuleEditorView(protection: protection, usage: usage,
                                      guardMode: false, initialMode: .quietHours)
            .environment(\.locale, Locale(identifier: language))
            .environment(\.layoutDirection, language == "ar" ? .rightToLeft : .leftToRight)
            let image = try await render(view, size: CGSize(width: 390, height: 844))
            let pixels = try XCTUnwrap(image.cgImage?.dataProvider?.data) as Data
            let shades = Set(stride(from: 0, to: pixels.count, by: 97).map { pixels[$0] })
            XCTAssertGreaterThan(shades.count, 8, "A blank image is not evidence of rendered UI")
            let attachment = XCTAttachment(image: image)
            attachment.name = "quiet-hours-\(language)"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
    }

    @MainActor private func render<V: View>(_ view: V, size: CGSize) async throws -> UIImage {
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
