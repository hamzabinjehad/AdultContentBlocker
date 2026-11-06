import XCTest
@testable import Hisn

/// The private-window gap is the one bypass that can sit under a green
/// extension status. These tests pin the evidence rules and the narrow profile
/// without reading this Mac's real browser policy.
final class PrivateBrowsingTests: XCTestCase {

    private func installed(_ ids: Set<String>) -> (String) -> Bool {
        { ids.contains($0) }
    }

    private func status(installed ids: Set<String>,
                        forced: @escaping (String, String) -> Any? = { _, _ in nil },
                        extensionAllowed: @escaping (String) -> Bool? = { _ in nil })
        -> PrivateBrowsingStatus {
        PrivateBrowsingStatus.read(now: Date(timeIntervalSince1970: 1_800_000_000),
                                   installed: installed(ids),
                                   forced: forced,
                                   extensionAllowed: extensionAllowed)
    }

    func testInstalledBrowsersAreListedAndUncontrolledOnesAreNotCalledProtected() {
        let s = status(installed: ["com.google.Chrome", "net.imput.helium",
                                   "com.apple.Safari", "org.mozilla.firefox"])
        XCTAssertEqual(s.browsers.map(\.name),
                       ["Firefox", "Google Chrome", "Helium", "Safari"])
        XCTAssertEqual(s.open.map(\.name), ["Firefox", "Google Chrome", "Helium"])
        XCTAssertEqual(s.unsupported.map(\.name), ["Safari"])
        XCTAssertFalse(s.isCovered)
        XCTAssertEqual(s.openNames, "Firefox, Google Chrome, and Helium")
    }

    func testAForcedChromiumPolicyIsTheOnlyThingThatClosesChrome() {
        let open = status(installed: ["com.google.Chrome"])
        XCTAssertEqual(open.browsers.first?.state, .open)

        let closed = status(installed: ["com.google.Chrome"]) { key, domain in
            key == "IncognitoModeAvailability" && domain == "com.google.Chrome" ? 1 : nil
        }
        XCTAssertEqual(closed.browsers.first?.state, .disabled)
        XCTAssertTrue(closed.isCovered)
    }

    func testFirefoxNeedsEnterprisePoliciesEnabledAsWellAsThePrivateModeKey() {
        let withoutEnterprise = status(installed: ["org.mozilla.firefox"]) { key, _ in
            key == "DisablePrivateBrowsing" ? true : nil
        }
        XCTAssertEqual(withoutEnterprise.browsers.first?.state, .open)

        let complete = status(installed: ["org.mozilla.firefox"]) { key, _ in
            switch key {
            case "EnterprisePoliciesEnabled": return true
            case "DisablePrivateBrowsing": return true
            default: return nil
            }
        }
        XCTAssertEqual(complete.browsers.first?.state, .disabled)
    }

    func testAnExtensionAllowedInPrivateWindowsCountsAsCoveredButIsDistinctFromPolicy() {
        let s = status(installed: ["com.google.Chrome"]) { _ in true }
        XCTAssertEqual(s.browsers.first?.state, .extensionChecked)
        XCTAssertTrue(s.isCovered)
        XCTAssertEqual(s.extensionChecked.map(\.name), ["Google Chrome"])
    }

    func testTheExtensionIncognitoAnswerExpiresWithItsHeartbeat() throws {
        let name = TestNamespace.make()
        defer { TestNamespace.dispose(name) }
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        ExtensionPresence.record(browser: "com.google.Chrome", processID: 42,
                                 at: now, incognitoAllowed: false, in: defaults)
        XCTAssertEqual(ExtensionPresence.incognitoAllowed(
            browser: "com.google.Chrome", in: defaults, now: now), false)
        XCTAssertNil(ExtensionPresence.incognitoAllowed(
            browser: "com.google.Chrome", in: defaults,
            now: now.addingTimeInterval(ExtensionPresence.incognitoFreshFor + 1)),
            "a stale answer is not evidence about the current browser")
    }

    func testAnUnreadableOrOlderExtensionAnswerIsNotCoverage() {
        let s = status(installed: ["com.google.Chrome"]) { _ in nil }
        XCTAssertEqual(s.browsers.first?.state, .open)
        XCTAssertFalse(s.isCovered)
    }

    func testTheProfileForcesPrivateWindowsInChromiumAndFirefoxOnly() throws {
        let chrome = PrivateBrowsingBrowser(name: "Google Chrome",
            bundleID: "com.google.Chrome", family: .chromium,
            state: .open, canDisable: true)
        let firefox = PrivateBrowsingBrowser(name: "Firefox",
            bundleID: "org.mozilla.firefox", family: .firefox,
            state: .open, canDisable: true)
        let safari = PrivateBrowsingBrowser(name: "Safari",
            bundleID: "com.apple.Safari", family: .webKit,
            state: .unsupported, canDisable: false)

        let profile = PrivateWindowsProfile.makeProfile(browsers: [chrome, firefox, safari])
        XCTAssertEqual(profile["PayloadType"] as? String, "Configuration")
        XCTAssertEqual(profile["PayloadIdentifier"] as? String, "app.hisn.profile.incognito")
        XCTAssertEqual(profile["PayloadRemovalDisallowed"] as? Bool, false)
        let payloads = try XCTUnwrap(profile["PayloadContent"] as? [[String: Any]])
        XCTAssertEqual(payloads.count, 2)
        let chromePayload = try XCTUnwrap(payloads.first { $0["PayloadType"] as? String == "com.google.Chrome" })
        XCTAssertEqual(chromePayload["IncognitoModeAvailability"] as? Int, 1)
        let firefoxPayload = try XCTUnwrap(payloads.first { $0["PayloadType"] as? String == "org.mozilla.firefox" })
        XCTAssertEqual(firefoxPayload["EnterprisePoliciesEnabled"] as? Bool, true)
        XCTAssertEqual(firefoxPayload["DisablePrivateBrowsing"] as? Bool, true)
        XCTAssertFalse(payloads.contains { $0["PayloadType"] as? String == "com.apple.Safari" },
                       "Safari has no supported private-browsing profile key")

        let managed = PrivateWindowsProfile.makeProfile(browsers: [chrome],
                                                         removalDisallowed: true)
        XCTAssertEqual(managed["PayloadRemovalDisallowed"] as? Bool, true,
                       "a managed deployment adds the removal restriction to the narrow profile too")
    }

    func testAPrivateWindowGapIsPartOfTheProtectionStatus() {
        let open = PrivateBrowsingStatus(browsers: [
            PrivateBrowsingBrowser(name: "Google Chrome", bundleID: "com.google.Chrome",
                family: .chromium, state: .open, canDisable: true),
        ])
        let evidence = ProtectionEvidence(
            filter: .on(domainCount: 150_000), extensionLastSeen: Date(),
            privateBrowsing: open)
        let s = ProtectionStatus(evidence)
        XCTAssertEqual(s.level, .partial)
        XCTAssertEqual(s.headline, "Private windows are open")
        XCTAssertEqual(s.layers.first(where: { $0.id == "privateBrowsing" })?.action,
                       .privateBrowsing)
        XCTAssertTrue(s.summary.contains("Private windows"), s.summary)
    }

    func testAClosedGapDoesNotStopTheStatusBeingActive() {
        let closed = PrivateBrowsingStatus(browsers: [
            PrivateBrowsingBrowser(name: "Google Chrome", bundleID: "com.google.Chrome",
                family: .chromium, state: .disabled, canDisable: true),
        ])
        let s = ProtectionStatus(ProtectionEvidence(
            filter: .on(domainCount: 150_000), extensionLastSeen: Date(),
            privateBrowsing: closed))
        XCTAssertEqual(s.level, .active)
        XCTAssertEqual(s.headline, "Protection active")
        XCTAssertEqual(s.layers.first(where: { $0.id == "privateBrowsing" })?.state, .ok)
    }

    func testAnUnsupportedBrowserAloneDoesNotKeepTheMacPermanentlyPartial() {
        let safari = PrivateBrowsingStatus(browsers: [
            PrivateBrowsingBrowser(name: "Safari", bundleID: "com.apple.Safari",
                family: .webKit, state: .unsupported, canDisable: false),
        ])
        let s = ProtectionStatus(ProtectionEvidence(
            filter: .on(domainCount: 150_000), extensionLastSeen: Date(),
            privateBrowsing: safari))
        XCTAssertNil(s.layers.first(where: { $0.id == "privateBrowsing" }))
        XCTAssertEqual(s.level, .active)
    }
}
