import XCTest
@testable import Hisn

/// The browser guard's decisions, pinned case by case.
///
/// Two ways to get this wrong, both expensive: close a browser whose extension
/// is working (the person loses their tabs, and learns to distrust the tool),
/// or leave open one whose extension was switched off (the one-click escape
/// this exists to remove).
final class BrowserGuardPolicyTests: XCTestCase {

    private let now = Date(timeIntervalSince1970: 1_800_000_000)
    private typealias P = BrowserGuardPolicy

    private func verdict(_ coverage: P.Coverage, lastSeen: TimeInterval? = nil,
                         launchedAgo: TimeInterval = 3600,
                         firstViolationAgo: TimeInterval? = nil,
                         repeatOffender: Bool = false) -> P.Verdict {
        P.verdict(coverage: coverage,
                  lastSeen: lastSeen.map { now.addingTimeInterval(-$0) },
                  now: now,
                  graceStart: now.addingTimeInterval(-launchedAgo),
                  firstViolation: firstViolationAgo.map { now.addingTimeInterval(-$0) },
                  recentlyClosed: repeatOffender)
    }

    // MARK: Coverage

    func testSafariIsNeverJudged() {
        XCTAssertEqual(P.coverage(bundleID: "com.apple.Safari", linked: [], userAllowed: []),
                       .exempt)
    }

    func testLinkedChromiumBrowserNeedsItsExtension() {
        XCTAssertEqual(P.coverage(bundleID: "net.imput.helium",
                                  linked: ["net.imput.helium"], userAllowed: []),
                       .needsExtension)
    }

    func testUnknownBrowserIsUncovered() {
        XCTAssertEqual(P.coverage(bundleID: "org.mozilla.firefox",
                                  linked: ["net.imput.helium"], userAllowed: []),
                       .uncovered)
    }

    func testUserAllowanceCannotExemptALinkedBrowser() {
        // Allowing Helium must not let it run with its extension off: the
        // allowance is for apps Hisn cannot cover, not a switch for the guard.
        XCTAssertEqual(P.coverage(bundleID: "net.imput.helium",
                                  linked: ["net.imput.helium"],
                                  userAllowed: ["net.imput.helium"]),
                       .needsExtension)
    }

    func testUserAllowanceCoversAnUncoveredApp() {
        XCTAssertEqual(P.coverage(bundleID: "com.openai.codex", linked: [],
                                  userAllowed: ["com.openai.codex"]),
                       .allowedByUser)
    }

    // MARK: A browser Hisn links

    func testCheckingInKeepsItOpen() {
        XCTAssertEqual(verdict(.needsExtension, lastSeen: 30), .ok)
    }

    func testJustLaunchedGetsAChanceToCheckIn() {
        XCTAssertEqual(verdict(.needsExtension, lastSeen: nil, launchedAgo: 20), .ok)
    }

    func testSilentExtensionIsWarnedNotClosed() {
        XCTAssertEqual(verdict(.needsExtension, lastSeen: 600),
                       .warn(.extensionSilent, closeAt: now.addingTimeInterval(P.warnSilent)))
    }

    func testNeverSeenExtensionPastGraceIsWarned() {
        guard case .warn(.extensionSilent, _) = verdict(.needsExtension, lastSeen: nil) else {
            return XCTFail("a browser that never checked in must be warned")
        }
    }

    func testWarningRunsOutThenCloses() {
        XCTAssertEqual(verdict(.needsExtension, lastSeen: 600,
                               firstViolationAgo: P.warnSilent),
                       .close(.extensionSilent))
    }

    func testWarningInProgressKeepsItsOriginalDeadline() {
        XCTAssertEqual(verdict(.needsExtension, lastSeen: 600, firstViolationAgo: 20),
                       .warn(.extensionSilent,
                             closeAt: now.addingTimeInterval(P.warnSilent - 20)))
    }

    func testExtensionComingBackCancelsTheWarning() {
        XCTAssertEqual(verdict(.needsExtension, lastSeen: 2, firstViolationAgo: 30), .ok)
    }

    func testRelaunchWithoutFixingGetsOnlyAShortWarning() {
        XCTAssertEqual(verdict(.needsExtension, lastSeen: 600, repeatOffender: true),
                       .warn(.extensionSilent, closeAt: now.addingTimeInterval(P.warnRepeat)))
    }

    func testWoundBackClockDoesNotMakeAnOldCheckInLookFresh() {
        // Last seen an hour "from now": the clock was wound back after the
        // extension was switched off. Not evidence of a live extension.
        XCTAssertFalse(P.extensionAlive(lastSeen: now.addingTimeInterval(3600), now: now))
        guard case .warn = verdict(.needsExtension, lastSeen: -3600) else {
            return XCTFail("a check-in stamped in the future must not keep a browser open")
        }
    }

    func testSmallClockDriftIsTolerated() {
        XCTAssertTrue(P.extensionAlive(lastSeen: now.addingTimeInterval(20), now: now))
    }

    func testStaleBoundary() {
        XCTAssertTrue(P.extensionAlive(lastSeen: now.addingTimeInterval(-(P.staleAfter - 1)), now: now))
        XCTAssertFalse(P.extensionAlive(lastSeen: now.addingTimeInterval(-P.staleAfter), now: now))
    }

    // MARK: Uncovered browsers

    func testUncoveredBrowserIsWarnedAtOnce() {
        XCTAssertEqual(verdict(.uncovered, launchedAgo: 1),
                       .warn(.uncovered, closeAt: now.addingTimeInterval(P.warnUncovered)))
    }

    func testUncoveredBrowserClosesAfterItsWarning() {
        XCTAssertEqual(verdict(.uncovered, firstViolationAgo: P.warnUncovered),
                       .close(.uncovered))
    }

    func testAllowedAndExemptAppsAreLeftAlone() {
        XCTAssertEqual(verdict(.allowedByUser), .ok)
        XCTAssertEqual(verdict(.exempt), .ok)
    }

    // MARK: What counts as a browser

    func testBrowserIsAnAppThatDeclaresWebSchemes() {
        let browser: [String: Any] = ["CFBundleURLTypes": [
            ["CFBundleURLName": "Web site URL", "CFBundleURLSchemes": ["http", "https"]],
        ]]
        let other: [String: Any] = ["CFBundleURLTypes": [
            ["CFBundleURLSchemes": ["slack"]],
        ]]
        XCTAssertTrue(P.declaresWebSchemes(infoPlist: browser))
        XCTAssertFalse(P.declaresWebSchemes(infoPlist: other))
        XCTAssertFalse(P.declaresWebSchemes(infoPlist: [:]))
        XCTAssertTrue(P.declaresWebSchemes(infoPlist: ["CFBundleURLTypes": [
            ["CFBundleURLSchemes": ["HTTPS"]]]]))
    }

    func testEveryLinkedBrowserHasABundleIdentifier() {
        for browser in NativeMessagingInstaller.browsers {
            XCTAssertFalse(browser.bundleID.isEmpty, browser.name)
            XCTAssertEqual(P.coverage(bundleID: browser.bundleID,
                                      linked: Set(NativeMessagingInstaller.browsers.map(\.bundleID)),
                                      userAllowed: []),
                           .needsExtension, browser.name)
        }
    }
}

/// The per-browser heartbeat the bridge writes and the guard reads.
final class ExtensionPresenceTests: XCTestCase {

    func testRecordsOverallAndPerBrowser() {
        let name = TestNamespace.make()
        defer { TestNamespace.dispose(name) }
        let d = UserDefaults(suiteName: name)
        let t = Date(timeIntervalSince1970: 1_800_000_000)
        ExtensionPresence.record(browser: "net.imput.helium", at: t, in: d)
        XCTAssertEqual(d?.object(forKey: ExtensionPresence.anyBrowserKey) as? Date, t)
        XCTAssertEqual(ExtensionPresence.lastSeen(browser: "net.imput.helium", in: d), t)
        XCTAssertNil(ExtensionPresence.lastSeen(browser: "com.google.Chrome", in: d))
    }

    func testUnknownLauncherStillRecordsTheOverallStamp() {
        let name = TestNamespace.make()
        defer { TestNamespace.dispose(name) }
        let d = UserDefaults(suiteName: name)
        ExtensionPresence.record(browser: nil, in: d)
        XCTAssertNotNil(d?.object(forKey: ExtensionPresence.anyBrowserKey))
    }

    func testOutermostAppBundleNamesTheBrowser() {
        // A helper nested inside the browser must resolve to the browser.
        let safari = "/Applications/Safari.app/Contents/MacOS/Safari"
        XCTAssertEqual(ExtensionPresence.bundleIdentifier(containing: safari), "com.apple.Safari")
        XCTAssertNil(ExtensionPresence.bundleIdentifier(containing: "/usr/bin/true"))
    }
}
