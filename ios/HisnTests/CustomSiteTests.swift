import Foundation
import XCTest
@testable import HisnMobile

final class CustomSiteTests: XCTestCase {
    func testCanonicalHostMatchesTheMacAndExtensionRules() {
        XCTAssertEqual(CustomSitePolicy.canonicalHost("https://www.Example.com/path?q=1#x"), "example.com")
        XCTAssertEqual(CustomSitePolicy.canonicalHost("  user@m.example.com:8443  "), "m.example.com")
        XCTAssertEqual(CustomSitePolicy.canonicalHost("example.com."), "example.com")
        XCTAssertEqual(CustomSitePolicy.canonicalHost("xn--mgbh0fb.xn--wgbh1c"), "xn--mgbh0fb.xn--wgbh1c")
        for raw in ["", "   ", "localhost", "192.168.1.1", "-bad.com", "bad-.com", "a..com",
                    "مثال.مصر", "exa mple.com", "under_score.com", String(repeating: "a", count: 64) + ".com"] {
            XCTAssertNil(CustomSitePolicy.canonicalHost(raw), raw)
        }
    }

    func testAddingRejectsInvalidCoveredAndOverfullLists() {
        XCTAssertEqual(CustomSitePolicy.adding("not a site", to: []), .invalid)
        XCTAssertEqual(CustomSitePolicy.adding("b.example", to: ["c.example"]), .added(["b.example", "c.example"]))
        XCTAssertEqual(CustomSitePolicy.adding("m.example.com", to: ["example.com"]), .duplicate)
        XCTAssertEqual(CustomSitePolicy.adding("example.com", to: ["example.com"]), .duplicate)
        XCTAssertEqual(CustomSitePolicy.adding("notexample.com", to: ["example.com"]), .added(["example.com", "notexample.com"]))
        let full = (1...CustomSitePolicy.limit).map { "site\($0).example" }.sorted()
        XCTAssertEqual(CustomSitePolicy.adding("another.example", to: full), .full)
    }

    func testAddingAParentFreesTheSubdomainsItCovers() {
        XCTAssertEqual(CustomSitePolicy.adding("example.com", to: ["a.example.com", "b.example.com", "other.org"]),
                       .added(["example.com", "other.org"]))
        let full = (1...CustomSitePolicy.limit).map { "s\($0).example.com" }.sorted()
        XCTAssertEqual(CustomSitePolicy.adding("example.com", to: full), .added(["example.com"]))
    }

    func testCommitmentAllowsAddingButNotRemoving() {
        let sites = ["a.example", "b.example"]
        XCTAssertTrue(CustomSitePolicy.permits(current: sites, proposed: ["a.example"], committed: false))
        XCTAssertFalse(CustomSitePolicy.permits(current: sites, proposed: ["a.example"], committed: true))
        XCTAssertTrue(CustomSitePolicy.permits(current: sites, proposed: sites + ["c.example"], committed: true))
        XCTAssertTrue(CustomSitePolicy.permits(current: ["m.example.com"], proposed: ["example.com"], committed: true),
                      "A parent that still covers a saved site is not loosening")
        XCTAssertFalse(CustomSitePolicy.permits(current: ["example.com"], proposed: ["m.example.com"], committed: true))
    }

    func testStoreRoundTripsAndReportsDamageInsteadOfAnEmptyList() throws {
        let storage = EphemeralRuleStorage()
        let store = CustomSiteStore(storage: storage)
        XCTAssertEqual(try store.load(), [])
        try store.save(["a.example", "b.example"])
        XCTAssertEqual(try CustomSiteStore(storage: storage).load(), ["a.example", "b.example"])
        XCTAssertThrowsError(try store.save(["Not A Host"]))
        XCTAssertThrowsError(try store.save((0...CustomSitePolicy.limit).map { "s\($0).example" }))
        try storage.write(Data("damaged".utf8), forKey: "hisn.mobile.customSites.record")
        XCTAssertThrowsError(try CustomSiteStore(storage: storage).load())
        XCTAssertThrowsError(try store.save([]), "A damaged list is never silently replaced")
    }

    @MainActor func testControllerAddsDuringCommitmentButRefusesRemoval() throws {
        let storage = EphemeralRuleStorage()
        let commitments = MemoryCommitmentStore()
        let controller = ProtectionController(commitmentStore: commitments,
                                              customSiteStore: CustomSiteStore(storage: storage))
        controller.addCustomSite("https://www.first.example/page")
        XCTAssertEqual(controller.customSites, ["first.example"])
        XCTAssertEqual(controller.customSiteMessage, "sites.added")
        controller.addCustomSite("first.example")
        XCTAssertEqual(controller.customSiteMessage, "sites.duplicate")
        controller.startCommitment(days: 7)
        XCTAssertTrue(controller.customSitesLocked)
        controller.addCustomSite("second.example")
        XCTAssertEqual(controller.customSites, ["first.example", "second.example"])
        controller.removeCustomSite("first.example")
        XCTAssertEqual(controller.customSiteMessage, "sites.locked")
        XCTAssertEqual(controller.customSites, ["first.example", "second.example"])
        let relaunched = ProtectionController(commitmentStore: commitments,
                                              customSiteStore: CustomSiteStore(storage: storage))
        XCTAssertEqual(relaunched.customSites, ["first.example", "second.example"])
        #if targetEnvironment(simulator)
        XCTAssertFalse(relaunched.customSitesApplied, "Saved sites are not applied without Screen Time")
        #endif
    }

    @MainActor func testControllerRemovesWithoutCommitmentAndRefusesWithDamagedStorage() throws {
        let storage = EphemeralRuleStorage()
        let controller = ProtectionController(commitmentStore: MemoryCommitmentStore(),
                                              customSiteStore: CustomSiteStore(storage: storage))
        controller.addCustomSite("first.example")
        controller.removeCustomSite("first.example")
        XCTAssertEqual(controller.customSites, [])
        XCTAssertEqual(controller.customSiteMessage, "sites.removed")
        try storage.write(Data("damaged".utf8), forKey: "hisn.mobile.customSites.record")
        let damaged = ProtectionController(commitmentStore: MemoryCommitmentStore(),
                                           customSiteStore: CustomSiteStore(storage: storage))
        XCTAssertFalse(damaged.customSitesStorageHealthy)
        damaged.addCustomSite("second.example")
        XCTAssertEqual(damaged.customSiteMessage, "sites.failed")
    }

    func testCustomSiteTextShipsInEnglishAndArabic() throws {
        let keys = ["sites.title", "sites.placeholder", "sites.add", "sites.empty", "sites.count %lld %lld",
                    "sites.scope", "sites.lockedScope", "sites.storageFailed", "sites.applied",
                    "sites.needsScreenTime", "sites.failed", "sites.invalid", "sites.duplicate",
                    "sites.full", "sites.added", "sites.removed", "sites.locked"]
        for language in ["en", "ar"] {
            let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"))
            let localized = try XCTUnwrap(Bundle(path: path))
            for key in keys {
                let text = localized.localizedString(forKey: key, value: nil, table: nil)
                XCTAssertFalse(text.isEmpty, "\(language): \(key)")
                XCTAssertNotEqual(text, key, "\(language): \(key)")
            }
        }
    }
}
