import XCTest
@testable import Hisn

/// The Arabic the app ships, read back from the built bundle.
///
/// `check_localization.py` proves every string has a translation that fits
/// it; these prove the build actually carries them — that the catalog was
/// compiled into `ar.lproj` and that Arabic's plural forms choose right.
final class LocalizationTests: XCTestCase {

    private func arabic() throws -> Bundle {
        try XCTUnwrap(Bundle(for: LockManager.self).path(forResource: "ar", ofType: "lproj")
                        .flatMap(Bundle.init(path:)),
                      "the app bundle has no ar.lproj")
    }

    func testTheAppShipsArabic() throws {
        let ar = try arabic()
        XCTAssertEqual(ar.localizedString(forKey: "Overview", value: nil, table: nil), "نظرة عامة")
        XCTAssertEqual(ar.localizedString(forKey: "Locked until %@", value: nil, table: nil),
                       "مقفل حتى %@")
    }

    /// Six plural categories, and each count lands in its own.
    func testArabicPluralsChooseTheirForm() throws {
        let format = try arabic().localizedString(forKey: "%lld domains", value: nil, table: nil)
        let latn = Locale(identifier: "ar@numbers=latn")
        func say(_ n: Int) -> String { String(format: format, locale: latn, n) }
        XCTAssertEqual(say(1), "نطاق واحد")
        XCTAssertEqual(say(2), "نطاقان")
        XCTAssertEqual(say(5), "5 نطاقات")
        XCTAssertEqual(say(11), "11 نطاقًا")
        XCTAssertEqual(say(100), "100 نطاق")
    }

    /// English plurals come from the same catalog, so they must survive it.
    func testEnglishPluralsStillAgree() throws {
        let en = try XCTUnwrap(Bundle(for: LockManager.self).path(forResource: "en", ofType: "lproj")
                                .flatMap(Bundle.init(path:)))
        let format = en.localizedString(forKey: "%lld words", value: nil, table: nil)
        XCTAssertEqual(String(format: format, locale: Locale(identifier: "en"), 1), "1 word")
        XCTAssertEqual(String(format: format, locale: Locale(identifier: "en"), 3), "3 words")
    }

    /// Latin digits in Arabic, as the extension uses: the locale the language
    /// switch writes must format numbers that way.
    func testArabicChoiceKeepsLatinDigits() {
        let locale = Locale(identifier: "ar_US@numbers=latn")
        XCTAssertEqual(12345.formatted(.number.locale(locale).grouping(.never)), "12345")
    }
}
