import XCTest
@testable import Hisn

/// The site checker names the rule that decided an answer. These tests pin the
/// order, because a wrong order would either let an allowance outrank a block
/// or call an unreadable list “not blocked”.
final class SiteCheckerTests: XCTestCase {

    private func check(_ raw: String,
                       blocks: [String] = [],
                       allows: [String] = [],
                       strict: Bool = false,
                       keywords: Bool = true,
                       list: (String) -> (version: Int, listed: Bool, keyword: Bool,
                                          source: SiteCheckResult.ListSource)?
                           = { _ in (7, false, false, .installed) })
        -> SiteCheckResult {
        SiteCheckRules.evaluate(raw: raw, blocks: blocks, allows: allows,
                                strict: strict, keywordsEnabled: keywords, list: list)
    }

    func testInvalidTextNeverBecomesAllowed() {
        XCTAssertEqual(check("not a domain").outcome, .invalid)
        XCTAssertEqual(check("").outcome, .invalid)
        XCTAssertEqual(check("http://").outcome, .invalid)
        XCTAssertEqual(check("127.0.0.1").outcome, .invalid)
    }

    func testFullLinksNormalizeToTheDomain() {
        let result = check("https://www.Example.com/watch?v=1")
        XCTAssertEqual(result.host, "example.com")
        XCTAssertEqual(result.outcome, .allowed)
        XCTAssertEqual(result.source, .notListed)
    }

    func testHandBlockOutranksHandAllowance() {
        let result = check("cdn.example.com", blocks: ["example.com"], allows: ["example.com"])
        XCTAssertEqual(result.outcome, .blocked)
        XCTAssertEqual(result.source, .customBlock)
    }

    func testHandAllowanceOutranksThePublishedList() {
        let result = check("youtube.com", allows: ["youtube.com"],
                           list: { _ in (9, true, false, .installed) })
        XCTAssertEqual(result.outcome, .allowed)
        XCTAssertEqual(result.source, .allowlist)
    }

    func testStrictModeAllowsOnlyTheAllowance() {
        let known = check("youtube.com", allows: ["youtube.com"], strict: true)
        XCTAssertEqual(known.outcome, .allowed)
        XCTAssertEqual(known.source, .allowlist)

        let other = check("example.com", strict: true)
        XCTAssertEqual(other.outcome, .blocked)
        XCTAssertEqual(other.source, .strictMode)
        XCTAssertNil(other.listVersion)
    }

    func testStrictModeDoesNotNeedTheList() {
        var asked = false
        let result = check("example.com", strict: true, list: { _ in
            asked = true
            return nil
        })
        XCTAssertEqual(result.outcome, .blocked)
        XCTAssertFalse(asked)
    }

    func testPublishedListMatchIsNamed() {
        let result = check("example.com", list: { _ in (12, true, false, .installed) })
        XCTAssertEqual(result.outcome, .blocked)
        XCTAssertEqual(result.source, .publishedList)
        XCTAssertEqual(result.listVersion, 12)
    }

    func testKeywordLayerIsNamedAndRespectsItsSwitch() {
        let on = check("example.com", keywords: true, list: { _ in (12, false, true, .installed) })
        XCTAssertEqual(on.outcome, .blocked)
        XCTAssertEqual(on.source, .keyword)

        let off = check("example.com", keywords: false, list: { _ in (12, false, true, .installed) })
        XCTAssertEqual(off.outcome, .allowed)
        XCTAssertEqual(off.source, .notListed)
    }

    func testBuiltInRuleCannotBeAllowed() {
        let result = check("nitter.net", allows: ["nitter.net"],
                           list: { _ in (12, false, false, .bundledCore) })
        XCTAssertEqual(result.outcome, .blocked)
        XCTAssertEqual(result.source, .builtIn)
    }

    func testUnreadableListIsNotAnAllowance() {
        let result = check("example.com", list: { _ in nil })
        XCTAssertEqual(result.outcome, .listUnavailable)
        XCTAssertEqual(result.source, .none)
        XCTAssertEqual(result.listSource, .unavailable)
        XCTAssertNil(result.listVersion)
    }

    func testBundledSeedIsReportedAsTheSource() {
        let result = check("example.com", list: { _ in (5, false, false, .bundledCore) })
        XCTAssertEqual(result.outcome, .allowed)
        XCTAssertEqual(result.source, .notListed)
        XCTAssertEqual(result.listSource, .bundledCore)
    }

    func testUnreadableListStillReportsAHandBlock() {
        let result = check("example.com", blocks: ["example.com"], list: { _ in nil })
        XCTAssertEqual(result.outcome, .blocked)
        XCTAssertEqual(result.source, .customBlock)
    }

    func testAdultTldIsNamedEvenWhenTheListCannotBeRead() {
        let result = check("new.porn", list: { _ in nil })
        XCTAssertEqual(result.outcome, .blocked)
        XCTAssertEqual(result.source, .adultTld)
        XCTAssertEqual(result.listSource, .none)
        XCTAssertNil(result.listVersion)

        let listed = check("cdn.new.xxx", list: { _ in (12, false, false, .installed) })
        XCTAssertEqual(listed.source, .adultTld)
    }

    func testAllowanceOutranksTheAdultTldRail() {
        let result = check("new.porn", allows: ["new.porn"])
        XCTAssertEqual(result.outcome, .allowed)
        XCTAssertEqual(result.source, .allowlist)
    }

    func testCamAndOrdinaryTldsAreNotGuarded() {
        for host in ["example.cam", "example.com", "porn.example.com"] {
            let result = check(host)
            XCTAssertEqual(result.outcome, .allowed, host)
            XCTAssertEqual(result.source, .notListed, host)
        }
    }

    func testSubdomainOfAPublishedEntryMatches() {
        // The provider is the store's own parent-domain walk; the checker
        // passes the full host to it rather than re-implementing the walk.
        let result = check("cdn.example.com", list: { host in
            (12, host.hasSuffix("example.com"), false, .installed)
        })
        XCTAssertEqual(result.outcome, .blocked)
        XCTAssertEqual(result.source, .publishedList)
    }

    func testResultValuesAreIndependentOfTheView() {
        let result = check("example.com")
        XCTAssertEqual(result, SiteCheckResult(host: "example.com", outcome: .allowed,
                                               source: .notListed, listVersion: 7,
                                               listSource: .installed))
    }
}
