import XCTest
@testable import Hisn

final class ExtraSitesTests: XCTestCase {
    func testSuggestionsAreUniqueNormalisedDomains() {
        XCTAssertFalse(ExtraSites.suggestions.isEmpty)
        XCTAssertEqual(Set(ExtraSites.suggestions).count, ExtraSites.suggestions.count)
        for domain in ExtraSites.suggestions {
            XCTAssertEqual(SiteLists.normalize(domain), domain,
                           "\(domain) must be a bare registrable domain")
        }
    }

    func testTogglingAddsThenRemovesTheExactSite() {
        let added = ExtraSites.toggling("youtube.com", in: [])
        XCTAssertEqual(added, ["youtube.com"])
        XCTAssertEqual(ExtraSites.coverage(of: "youtube.com", in: added), .exact)
        XCTAssertEqual(ExtraSites.toggling("youtube.com", in: added), [])
    }

    func testTogglingKeepsOtherEntriesAndSorts() {
        let start = ["reddit.com", "example.com"]
        let added = ExtraSites.toggling("youtube.com", in: start)
        XCTAssertEqual(added, ["example.com", "reddit.com", "youtube.com"])
        XCTAssertEqual(ExtraSites.toggling("youtube.com", in: added),
                       ["example.com", "reddit.com"])
    }

    func testTogglingIgnoresADomainThatIsNotASuggestion() {
        let start = ["example.com"]
        XCTAssertEqual(ExtraSites.toggling("not-in-the-list.test", in: start), start)
    }

    func testParentRuleReadsAsBlockedAndIsNotRemoved() {
        // A wider hand-written rule covers the suggestion. Clearing the chip
        // must not remove the parent, because that would unblock its siblings.
        let blocks = ["ycombinator.com"]
        XCTAssertEqual(ExtraSites.coverage(of: "news.ycombinator.com", in: blocks),
                       .parent("ycombinator.com"))
        XCTAssertEqual(ExtraSites.toggling("news.ycombinator.com", in: blocks), blocks)
    }

    func testAddedCountCountsExactAndParentCoverage() {
        let blocks = ["youtube.com", "ycombinator.com", "example.com"]
        XCTAssertEqual(ExtraSites.addedCount(in: blocks), 2)
        XCTAssertFalse(ExtraSites.coverage(of: "example.com", in: blocks).isBlocked)
    }
}
