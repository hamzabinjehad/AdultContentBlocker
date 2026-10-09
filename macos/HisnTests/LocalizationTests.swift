import XCTest
@testable import Hisn

/// The Arabic the app ships, read back from the built bundle.
///
/// `check_localization.py` proves every string has a translation that fits
/// it; these prove the build actually carries them — that the catalog was
/// compiled into `ar.lproj` and that Arabic's plural forms choose right.
final class LocalizationTests: XCTestCase {
    func testBrowserRequirementConsentShipsArabic() throws {
        let ar = try arabic()
        XCTAssertEqual(ar.localizedString(forKey: "Keep browser protection on", value: nil, table: nil),
                       "أبقِ حماية المتصفح مفعّلة")
        XCTAssertEqual(ar.localizedString(forKey: "Require browser protection outside a lock?", value: nil, table: nil),
                       "هل تريد اشتراط حماية المتصفح خارج فترة القفل؟")
        XCTAssertEqual(ar.localizedString(forKey: "Enable requirement", value: nil, table: nil),
                       "تفعيل الاشتراط")
        XCTAssertEqual(ar.localizedString(forKey: "This requirement cannot be turned off while a lock is running.", value: nil, table: nil),
                       "لا يمكن إيقاف هذا الاشتراط أثناء تشغيل القفل.")
        XCTAssertEqual(ar.localizedString(forKey: "Link router only — left open because it hands links to a browser and does not render pages.", value: nil, table: nil),
                       "موجّه روابط فقط — يبقى مفتوحًا لأنه يرسل الروابط إلى متصفح ولا يعرض الصفحات.")
    }

    func testBrowserExceptionLimitsShipArabic() throws {
        let ar = try arabic()
        XCTAssertEqual(ar.localizedString(forKey: "Trust this app without extension checks", value: nil, table: nil),
                       "ثق بهذا التطبيق دون فحوصات الإضافة")
        XCTAssertEqual(ar.localizedString(forKey: "Known browsers need a connected Hisn extension and cannot be allowed as app exceptions.", value: nil, table: nil),
                       "تحتاج المتصفحات المعروفة إلى إضافة حصن متصلة ولا يمكن السماح بها كاستثناءات للتطبيقات.")
    }

    func testProtectionPlanShipsArabicLabels() throws {
        let ar = try arabic()
        XCTAssertEqual(ar.localizedString(forKey: "Protection layers reporting ready", value: nil, table: nil),
                       "طبقات الحماية التي أبلغت عن الجاهزية")
        XCTAssertEqual(ar.localizedString(forKey: "I understand this commitment does not prevent an administrator from removing protection. Keep essential services accessible.", value: nil, table: nil),
                       "أفهم أن هذا الالتزام لا يمنع مسؤول الجهاز من إزالة الحماية. سأُبقي الخدمات الضرورية متاحة.")
    }

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

    func testProtectedSetupShipsArabicLabels() throws {
        let ar = try arabic()
        XCTAssertEqual(ar.localizedString(forKey: "Protected Setup", value: nil, table: nil), "الإعداد المحمي")
        XCTAssertEqual(ar.localizedString(forKey: "Required browser protection", value: nil, table: nil), "حماية المتصفح الإلزامية")
        XCTAssertEqual(ar.localizedString(forKey: "Protected setup checks passed", value: nil, table: nil), "اجتاز الإعداد المحمي الفحوصات")
    }

    func testNetworkSetupShipsArabic() throws {
        let ar = try arabic()
        XCTAssertEqual(ar.localizedString(forKey: "Start with your network", value: nil, table: nil), "ابدأ بحماية شبكتك")
        XCTAssertEqual(ar.localizedString(forKey: "Network protection not verified", value: nil, table: nil), "لم يتم التحقق من حماية الشبكة")
        XCTAssertEqual(ar.localizedString(forKey: "I cannot change this network", value: nil, table: nil), "لا أستطيع تغيير إعدادات هذه الشبكة")
        XCTAssertEqual(ar.localizedString(forKey: "Check Cloudflare DNS on this Mac", value: nil, table: nil), "افحص DNS من Cloudflare على هذا الماك")
        XCTAssertEqual(ar.localizedString(forKey: "DNS sample inconclusive", value: nil, table: nil), "نتيجة عيّنة DNS غير حاسمة")
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
