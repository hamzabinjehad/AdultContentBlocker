import FamilyControls
import XCTest
@testable import HisnMobile

/// The shield extensions are system processes: these tests pin the language
/// handoff, the reason a shield is shown, and that every reason has complete
/// English and Arabic copy. They do not prove the system renders the shield.
final class ShieldTextPolicyTests: XCTestCase {
    private var empty: FamilyActivitySelection { FamilyActivitySelection() }

    private func date(_ hour: Int, _ minute: Int = 0) -> Date {
        AppUsageConfiguration.dayCalendar(in: .current)
            .date(from: DateComponents(year: 2026, month: 11, day: 3, hour: hour, minute: minute))!
    }

    private func quietHours() -> AppUsageConfiguration {
        AppUsageConfiguration(selection: empty, mode: .quietHours, minutes: 30,
                              windowStart: 22 * 60, windowEnd: 7 * 60)
    }

    func testLanguageChoiceOverridesTheSystemLanguage() {
        typealias P = ShieldTextPolicy
        XCTAssertEqual(P.resolveLanguage(storedChoice: "ar", preferredLanguages: ["en-US"]), .arabic)
        XCTAssertEqual(P.resolveLanguage(storedChoice: "en", preferredLanguages: ["ar-SA"]), .english)
        XCTAssertEqual(P.resolveLanguage(storedChoice: "system", preferredLanguages: ["ar-SA", "en"]), .arabic)
        XCTAssertEqual(P.resolveLanguage(storedChoice: "system", preferredLanguages: ["en-GB"]), .english)
        XCTAssertEqual(P.resolveLanguage(storedChoice: nil, preferredLanguages: ["ar"]), .arabic)
        XCTAssertEqual(P.resolveLanguage(storedChoice: "fr", preferredLanguages: ["fr-FR"]), .english)
    }

    func testLanguageChoiceIsStoredAndReadBack() throws {
        let name = "hisn.tests.shield." + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }

        XCTAssertNil(ShieldTextPolicy.storedLanguageChoice(in: defaults))
        XCTAssertEqual(ShieldTextPolicy.language(defaults: defaults,
                                                 preferredLanguages: ["ar-SA"]), .arabic)
        ShieldTextPolicy.store(languageChoice: "ar", defaults: defaults)
        XCTAssertEqual(ShieldTextPolicy.storedLanguageChoice(in: defaults), "ar")
        XCTAssertEqual(ShieldTextPolicy.language(defaults: defaults,
                                                 preferredLanguages: ["en-US"]), .arabic)
    }

    func testReasonFollowsTheSavedMode() {
        typealias P = ShieldTextPolicy
        XCTAssertEqual(P.reason(configuration: nil, budgetReached: true,
                                now: date(23), trustedNow: nil), .unknown)
        XCTAssertEqual(P.reason(configuration: AppUsageConfiguration(selection: empty, mode: .always, minutes: 30),
                                budgetReached: false, now: date(23), trustedNow: nil), .always)
        let budget = AppUsageConfiguration(selection: empty, mode: .dailyBudget, minutes: 30)
        XCTAssertEqual(P.reason(configuration: budget, budgetReached: true,
                                now: date(23), trustedNow: nil), .dailyBudget)
        XCTAssertEqual(P.reason(configuration: budget, budgetReached: false,
                                now: date(23), trustedNow: nil), .unknown)
        XCTAssertEqual(P.reason(configuration: quietHours(), budgetReached: false,
                                now: date(23), trustedNow: nil), .quietHours)
        XCTAssertEqual(P.reason(configuration: quietHours(), budgetReached: false,
                                now: date(12), trustedNow: nil), .unknown)
    }

    func testQuietHoursUseEitherClock() {
        // Device clock outside, Apple's reading inside: still quiet hours.
        XCTAssertEqual(ShieldTextPolicy.reason(configuration: quietHours(), budgetReached: false,
                                               now: date(8), trustedNow: date(6)), .quietHours)
        // Device clock inside, Apple's reading outside: still quiet hours.
        XCTAssertEqual(ShieldTextPolicy.reason(configuration: quietHours(), budgetReached: false,
                                               now: date(23), trustedNow: date(12)), .quietHours)
        // Both outside: no quiet-hours claim.
        XCTAssertEqual(ShieldTextPolicy.reason(configuration: quietHours(), budgetReached: false,
                                               now: date(12), trustedNow: date(8)), .unknown)
    }

    func testEveryReasonHasCompleteCopyInBothLanguages() throws {
        let arabic = try NSRegularExpression(pattern: "[\\u0600-\\u06FF]")
        for reason in [ShieldTextPolicy.Reason.always, .dailyBudget, .quietHours, .unknown] {
            let english = ShieldTextPolicy.copy(for: reason, language: .english)
            let arabicCopy = ShieldTextPolicy.copy(for: reason, language: .arabic)
            for copy in [english, arabicCopy] {
                XCTAssertFalse(copy.title.isEmpty, "\(reason) title")
                XCTAssertFalse(copy.subtitle.isEmpty, "\(reason) subtitle")
                XCTAssertFalse(copy.primaryButton.isEmpty, "\(reason) button")
            }
            for value in [arabicCopy.title, arabicCopy.subtitle, arabicCopy.primaryButton] {
                XCTAssertNotNil(arabic.firstMatch(in: value, range: NSRange(value.startIndex..., in: value)),
                                "Arabic copy must be written in Arabic: \(value)")
            }
            XCTAssertNotEqual(english.title, arabicCopy.title, "\(reason) should be translated")
            XCTAssertNotEqual(english.subtitle, arabicCopy.subtitle, "\(reason) should be translated")
        }
    }

    func testUnknownCopyDoesNotClaimABudgetOrWindow() {
        let copy = ShieldTextPolicy.copy(for: .unknown, language: .english)
        XCTAssertFalse(copy.subtitle.lowercased().contains("daily interval"))
        XCTAssertFalse(copy.subtitle.lowercased().contains("quiet-hours"))
        XCTAssertTrue(copy.subtitle.contains("review"))
    }

    func testShieldCopyWorksWithoutAnAppGroup() {
        let copy = ShieldTextPolicy.shieldCopy(defaults: nil,
                                               now: date(23),
                                               preferredLanguages: ["ar-SA"])
        XCTAssertEqual(copy, ShieldTextPolicy.copy(for: .unknown, language: .arabic))
        XCTAssertNil(ShieldTextPolicy.configuration(in: nil))
        XCTAssertFalse(ShieldTextPolicy.budgetReachedToday(in: nil))
        XCTAssertNil(ShieldTextPolicy.trustedNow(in: nil))
    }
}
