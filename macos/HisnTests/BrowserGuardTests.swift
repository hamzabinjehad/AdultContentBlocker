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

    func testGuardIsOptionalOutsideLocksButAlwaysActiveDuringLocks() {
        XCTAssertFalse(P.isActive(locked: false, requireOutsideLock: false))
        XCTAssertTrue(P.isActive(locked: false, requireOutsideLock: true))
        XCTAssertTrue(P.isActive(locked: true, requireOutsideLock: false))
        XCTAssertTrue(P.isActive(locked: true, requireOutsideLock: true))
    }

    func testAvailableAuthorityDoesNotFallBackToAnEditableLocalHeartbeat() {
        let id = "net.imput.helium"
        XCTAssertEqual(P.checkIn(browser: id, authority: nil, local: now), now)
        XCTAssertNil(P.checkIn(browser: id, authority: [:], local: now))
        XCTAssertNil(P.checkIn(browser: id, authority: ["com.google.Chrome": now], local: now))
        let stale = now.addingTimeInterval(-600)
        XCTAssertEqual(P.checkIn(browser: id, authority: [id: stale], local: now), stale)
    }

    func testBrowserRestartDoesNotResetItsInitialGrace() {
        var session = P.Session()
        let id = "net.imput.helium"
        XCTAssertEqual(session.graceStart(browser: id, now: now, notBefore: now, instance: "first"), now)
        let restarted = now.addingTimeInterval(55)
        XCTAssertEqual(session.graceStart(browser: id, now: restarted, notBefore: now, instance: "second"), now)
        let afterGrace = now.addingTimeInterval(P.launchGrace + 1)
        let start = session.graceStart(browser: id, now: afterGrace, notBefore: now, instance: "third")
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: nil, now: afterGrace,
                                graceStart: start, firstViolation: nil, recentlyClosed: false),
                       .warn(.extensionSilent, closeAt: afterGrace.addingTimeInterval(P.warnSilent)))
    }

    func testWarningSurvivesTheBrowserBeingAbsentAndRestarting() {
        var session = P.Session()
        let id = "net.imput.helium"
        let warned = now.addingTimeInterval(P.launchGrace + 1)
        _ = session.graceStart(browser: id, now: now, notBefore: now, instance: "first")
        session.observe(.warn(.extensionSilent, closeAt: warned.addingTimeInterval(P.warnSilent)),
                        browser: id, at: warned)
        // No observations while the process is stopped: its ledger is retained.
        let restarted = warned.addingTimeInterval(P.warnSilent)
        let start = session.graceStart(browser: id, now: restarted, notBefore: now, instance: "second")
        XCTAssertEqual(session.firstViolation(browser: id), warned)
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: nil, now: restarted,
                                graceStart: start, firstViolation: session.firstViolation(browser: id),
                                recentlyClosed: false), .close(.extensionSilent))
    }

    func testHealthyBrowserGetsOneNewStartupGraceButUnverifiedRestartsDoNot() {
        var session = P.Session()
        let id = "net.imput.helium"
        _ = session.graceStart(browser: id, now: now, notBefore: now, instance: "first")
        session.observe(.ok, browser: id, at: now, verifiedConnection: true)
        let returned = now.addingTimeInterval(3600)
        XCTAssertEqual(session.graceStart(browser: id, now: returned, notBefore: now,
                                         instance: "second"), returned)
        // Unverified startup is visible, and cannot earn another grace.
        session.observe(.warn(.extensionSilent, closeAt: returned.addingTimeInterval(P.warnSilent)),
                        browser: id, at: returned)
        XCTAssertEqual(session.graceStart(browser: id, now: returned.addingTimeInterval(55),
                                         notBefore: now, instance: "third"), returned)
    }

    func testDisconnectedRestartCannotReuseEarlierHealthyConnection() {
        var session = P.Session()
        let id = "net.imput.helium"
        _ = session.graceStart(browser: id, now: now, notBefore: now, instance: "first")
        session.observe(.ok, browser: id, at: now, verifiedConnection: true)
        session.observe(.warn(.extensionSilent, closeAt: now.addingTimeInterval(200)),
                        browser: id, at: now.addingTimeInterval(160))
        XCTAssertEqual(session.graceStart(browser: id, now: now.addingTimeInterval(170),
                                         notBefore: now, instance: "second"), now)
        XCTAssertEqual(session.firstViolation(browser: id), now.addingTimeInterval(160))
    }

    func testDelayedForceCloseRechecksEnforcementRecoveryAndExceptions() {
        XCTAssertFalse(P.shouldForceClose(active: false, coverage: .needsExtension, lastSeen: nil, now: now))
        XCTAssertFalse(P.shouldForceClose(active: true, coverage: .needsExtension, lastSeen: now, now: now))
        XCTAssertFalse(P.shouldForceClose(active: true, coverage: .exempt, lastSeen: nil, now: now))
        XCTAssertFalse(P.shouldForceClose(active: true, coverage: .allowedByUser, lastSeen: nil, now: now))
        XCTAssertTrue(P.shouldForceClose(active: true, coverage: .needsExtension, lastSeen: nil, now: now))
        XCTAssertTrue(P.shouldForceClose(active: true, coverage: .uncovered, lastSeen: now, now: now))
    }

    func testUncoveredBrowserRestartKeepsItsWarningDeadline() {
        var session = P.Session()
        let id = "org.mozilla.firefox"
        session.observe(.warn(.uncovered, closeAt: now.addingTimeInterval(P.warnUncovered)),
                        browser: id, at: now)
        let restarted = now.addingTimeInterval(P.warnUncovered)
        XCTAssertEqual(P.verdict(coverage: .uncovered, lastSeen: nil, now: restarted,
                                graceStart: restarted, firstViolation: session.firstViolation(browser: id),
                                recentlyClosed: false), .close(.uncovered))
    }

    func testRecentlyClosedBrowserCannotGetAnotherLaunchGrace() {
        XCTAssertEqual(verdict(.needsExtension, launchedAgo: 1, repeatOffender: true),
                       .warn(.extensionSilent, closeAt: now.addingTimeInterval(P.warnRepeat)))
    }

    func testWakeAllowsFreshGraceWithoutResettingOnEveryLaunch() {
        var session = P.Session()
        let id = "net.imput.helium"
        _ = session.graceStart(browser: id, now: now, notBefore: now)
        let wake = now.addingTimeInterval(3600)
        XCTAssertEqual(session.graceStart(browser: id, now: wake, notBefore: wake), wake)
        XCTAssertEqual(session.graceStart(browser: id, now: wake.addingTimeInterval(55),
                                         notBefore: wake), wake)
    }

    func testHealthyReconnectClearsWarningButNotSessionGrace() {
        var session = P.Session()
        let id = "net.imput.helium"
        _ = session.graceStart(browser: id, now: now, notBefore: now)
        session.observe(.warn(.extensionSilent, closeAt: now.addingTimeInterval(P.warnSilent)),
                        browser: id, at: now)
        session.observe(.ok, browser: id, at: now.addingTimeInterval(10))
        XCTAssertNil(session.firstViolation(browser: id))
        XCTAssertEqual(session.graceStart(browser: id, now: now.addingTimeInterval(120),
                                         notBefore: now), now)
    }

    func testSeparateBrowsersAndNewEnforcementSessionsHaveSeparateGrace() {
        var session = P.Session()
        _ = session.graceStart(browser: "net.imput.helium", now: now, notBefore: now)
        let later = now.addingTimeInterval(120)
        XCTAssertEqual(session.graceStart(browser: "com.google.Chrome", now: later,
                                         notBefore: now), later)
        session.observe(.warn(.extensionSilent, closeAt: later), browser: "net.imput.helium", at: now)
        XCTAssertNil(session.firstViolation(browser: "com.google.Chrome"))
        session.reset()
        XCTAssertNil(session.firstViolation(browser: "net.imput.helium"))
        XCTAssertEqual(session.graceStart(browser: "net.imput.helium", now: later,
                                         notBefore: now), later)
    }

    func testSafariAndPreviewHaveNoAutomaticExemption() {
        for id in ["com.apple.Safari", "com.apple.SafariTechnologyPreview"] {
            XCTAssertEqual(P.coverage(bundleID: id, linked: [], userAllowed: []), .uncovered)
            XCTAssertEqual(P.coverage(bundleID: id, linked: [], userAllowed: [id]), .uncovered)
            XCTAssertFalse(P.canAllowException(bundleID: id, linked: []))
        }
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

    func testJustLaunchedGetsVisibleRecoveryNotPresumedProtection() {
        XCTAssertEqual(verdict(.needsExtension, lastSeen: nil, launchedAgo: 20),
                       .warn(.extensionSilent, closeAt: now.addingTimeInterval(60)))
    }

    func testInitialRecoveryIsExactlyOneMinuteAndRestartCannotRenewIt() {
        var session = P.Session()
        let id = "net.imput.helium"
        let start = session.graceStart(browser: id, now: now, notBefore: now, instance: "first")
        let initial = P.verdict(coverage: .needsExtension, lastSeen: nil, now: now,
            graceStart: start, firstViolation: nil, recentlyClosed: false)
        XCTAssertEqual(initial, .warn(.extensionSilent, closeAt: now.addingTimeInterval(60)))
        session.observe(initial, browser: id, at: now)
        let restarted = now.addingTimeInterval(55)
        let restartGrace = session.graceStart(browser: id, now: restarted,
                                              notBefore: now, instance: "second")
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: nil, now: restarted,
            graceStart: restartGrace, firstViolation: session.firstViolation(browser: id),
            recentlyClosed: false), .warn(.extensionSilent, closeAt: now.addingTimeInterval(60)))
        for coverage in [P.Coverage.needsExtension, .uncovered] {
            XCTAssertEqual(P.verdict(coverage: coverage, lastSeen: nil,
                now: now.addingTimeInterval(59.999), graceStart: start,
                firstViolation: now, recentlyClosed: false),
                .warn(coverage == .needsExtension ? .extensionSilent : .uncovered,
                      closeAt: now.addingTimeInterval(60)))
            XCTAssertEqual(P.verdict(coverage: coverage, lastSeen: nil,
                now: now.addingTimeInterval(60), graceStart: start,
                firstViolation: now, recentlyClosed: false),
                .close(coverage == .needsExtension ? .extensionSilent : .uncovered))
        }
    }

    func testWakeRecoveryIsVisibleAndKeepsOneDeadline() {
        let wake = now.addingTimeInterval(3600)
        for elapsed in [0.0, 20, 59.999] {
            XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: nil,
                now: wake.addingTimeInterval(elapsed), graceStart: wake,
                firstViolation: now, recentlyClosed: false),
                .warn(.extensionSilent, closeAt: wake.addingTimeInterval(60)))
        }
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: nil,
            now: wake.addingTimeInterval(60), graceStart: wake,
            firstViolation: now, recentlyClosed: false), .close(.extensionSilent))
    }

    func testOldInstanceHeartbeatCannotClearARestartWarning() {
        XCTAssertNil(P.instanceCheckIn(lastSeen: now, launchedAt: now.addingTimeInterval(1)))
        XCTAssertNil(P.instanceCheckIn(lastSeen: nil, launchedAt: now))
        XCTAssertEqual(P.instanceCheckIn(lastSeen: now, launchedAt: now), now)
        XCTAssertEqual(P.instanceCheckIn(lastSeen: now, launchedAt: nil), now)
        XCTAssertEqual(P.instanceCheckIn(lastSeen: now.addingTimeInterval(1),
                                         launchedAt: now), now.addingTimeInterval(1))
        XCTAssertEqual(verdict(.needsExtension, lastSeen: 1, firstViolationAgo: 59), .ok)
        let staleInstance = P.instanceCheckIn(lastSeen: now, launchedAt: now.addingTimeInterval(1))
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: staleInstance,
            now: now.addingTimeInterval(55), graceStart: now, firstViolation: now,
            recentlyClosed: false), .warn(.extensionSilent, closeAt: now.addingTimeInterval(60)))
    }

    func testForceTerminationRespectsCurrentWarningAndWakeRecovery() {
        let earlier = now.addingTimeInterval(-1)
        XCTAssertNil(P.forceRecoveryDeadline(requestedAt: now, awakeSince: earlier,
                                              warningDeadline: nil))
        let wakeDeadline = now.addingTimeInterval(60)
        XCTAssertEqual(P.forceRecoveryDeadline(requestedAt: earlier, awakeSince: now,
                                                warningDeadline: nil), wakeDeadline)
        XCTAssertEqual(P.forceRecoveryDeadline(requestedAt: earlier, awakeSince: now,
            warningDeadline: now.addingTimeInterval(80)), now.addingTimeInterval(80))
        XCTAssertEqual(P.forceRecoveryDeadline(requestedAt: earlier, awakeSince: now,
            warningDeadline: now.addingTimeInterval(10)), wakeDeadline)
        for coverage in [P.Coverage.needsExtension, .uncovered] {
            XCTAssertFalse(P.shouldForceClose(active: true, coverage: coverage,
                lastSeen: nil, now: wakeDeadline.addingTimeInterval(-0.001), notBefore: wakeDeadline))
            XCTAssertTrue(P.shouldForceClose(active: true, coverage: coverage,
                lastSeen: nil, now: wakeDeadline, notBefore: wakeDeadline))
        }
        XCTAssertFalse(P.shouldForceClose(active: true, coverage: .needsExtension,
            lastSeen: now, now: now, profileLoss: true, notBefore: wakeDeadline))
        XCTAssertFalse(P.shouldForceClose(active: false, coverage: .uncovered,
            lastSeen: nil, now: wakeDeadline, notBefore: wakeDeadline))
        XCTAssertFalse(P.shouldForceClose(active: true, coverage: .needsExtension,
            lastSeen: wakeDeadline, now: wakeDeadline, notBefore: wakeDeadline))
    }

    func testRepeatClosureDoesNotOverrideWakeAndRejectsFutureStamps() {
        XCTAssertFalse(P.isRepeatClosure(closedAt: nil, awakeSince: now, now: now))
        XCTAssertFalse(P.isRepeatClosure(closedAt: now.addingTimeInterval(-1),
                                         awakeSince: now, now: now))
        XCTAssertFalse(P.isRepeatClosure(closedAt: now.addingTimeInterval(1),
                                         awakeSince: now, now: now))
        XCTAssertTrue(P.isRepeatClosure(closedAt: now, awakeSince: now, now: now))
        XCTAssertFalse(P.isRepeatClosure(closedAt: now, awakeSince: now,
                                         now: now.addingTimeInterval(P.repeatWindow)))
    }

    func testKnownBrowsersIgnoreSavedAppAllowances() {
        for id in P.knownBrowsers {
            XCTAssertEqual(P.coverage(bundleID: id, linked: [], userAllowed: [id]), .uncovered, id)
            XCTAssertFalse(P.canAllowException(bundleID: id, linked: []), id)
        }
        for id in P.linkRouters {
            XCTAssertEqual(P.coverage(bundleID: id, linked: [], userAllowed: []), .exempt, id)
            XCTAssertFalse(P.canAllowException(bundleID: id, linked: []), id)
        }
        XCTAssertFalse(P.canAllowException(bundleID: "", linked: []))
        XCTAssertFalse(P.canAllowException(bundleID: "net.imput.helium", linked: ["net.imput.helium"]))
        XCTAssertTrue(P.canAllowException(bundleID: "com.example.nonbrowser", linked: []))
    }

    func testHeartbeatTimingBudgetIsIndependentOfExpectedConstants() {
        XCTAssertEqual(P.staleAfter, 90)
        XCTAssertEqual(P.launchGrace, 60)
        XCTAssertEqual(P.warnSilent, 60)
        XCTAssertEqual(P.warnUncovered, 60)
        XCTAssertEqual(P.warnRepeat, 5)
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

/// Preference evidence is configuration evidence, not proof of page scanning.
/// All fixtures are isolated; these tests never inspect the person's profiles.
final class BrowserProfileEvidenceTests: XCTestCase {
    private typealias E = BrowserProfileEvidence
    private let now = Date(timeIntervalSince1970: 1_800_000_000)
    private let id = "hisn-accepted-id"

    private func json(_ value: [String: Any]) throws -> Data {
        try JSONSerialization.data(withJSONObject: value)
    }
    private func snapshot(_ profiles: [String: [String: Data]], ids: Set<String>? = nil,
                          maximumBytes: Int = 20 * 1024 * 1024) throws -> E.Snapshot {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("hisn-guard-profiles-\(UUID())")
        defer { try? FileManager.default.removeItem(at: root) }
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        for (folder, files) in profiles {
            let directory = root.appendingPathComponent(folder)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            for (name, data) in files { try data.write(to: directory.appendingPathComponent(name)) }
        }
        return E.inspect(root: root, ids: ids ?? [id], maximumBytes: maximumBytes)
    }
    private func catalogue(_ entries: [String: Any]) throws -> Data {
        try json(["extensions": ["settings": entries]])
    }

    func testLegacyAndModernExplicitDisablement() {
        XCTAssertEqual(E.entryState(["state": 0]), .explicitLoss)
        XCTAssertEqual(E.entryState(["state": 1]), .noExplicitLoss)
        XCTAssertEqual(E.entryState(["disable_reasons": [1]]), .explicitLoss)
        XCTAssertEqual(E.entryState(["disable_reasons": 4]), .explicitLoss)
        XCTAssertEqual(E.entryState(["disable_reasons": [Int]()]), .noExplicitLoss)
        XCTAssertEqual(E.entryState(["disable_reasons": 0]), .noExplicitLoss)
    }
    func testMissingStateAloneAndMalformedFieldsAreNotRemovalEvidence() {
        let malformed: [Any] = ["invalid", [String: Any](), ["location": 4],
            ["state": false], ["state": 2], ["state": -1], ["state": 0.5], ["state": 0.0],
            ["state": 1, "disable_reasons": "bad"], ["disable_reasons": false],
            ["disable_reasons": [1, "bad"]], ["disable_reasons": [-1]],
            ["disable_reasons": [0]], ["disable_reasons": 1.0], ["disable_reasons": [1.0]],
            ["disable_reasons": Double.nan]]
        for entry in malformed { XCTAssertEqual(E.entryState(entry), .unconfirmed, "\(entry)") }
    }
    func testHealthyDefaultDoesNotHideDisabledSecondProfile() throws {
        let result = try snapshot([
            "Default": ["Preferences": catalogue([id: ["state": 1]])],
            "Profile 1": ["Secure Preferences": catalogue([id: ["disable_reasons": [1]]])]])
        XCTAssertTrue(result.enumerationVerified)
        XCTAssertEqual(result.profiles, [.init(folder: "Default", state: .noExplicitLoss),
                                         .init(folder: "Profile 1", state: .explicitLoss)])
    }
    func testReadableCatalogueWithoutAnAcceptedIDIsExplicitLoss() throws {
        let result = try snapshot(["Default": ["Preferences": catalogue(["another": ["state": 1]])]])
        XCTAssertEqual(result.profiles.first?.state, .explicitLoss)
    }
    func testMetadataOnlyAndEmptyNewProfileStayUnconfirmed() throws {
        let result = try snapshot(["Default": ["Preferences": json(["profile": ["name": "Fixture"]])],
                                   "Profile 1": [:]])
        XCTAssertEqual(result.profiles.map(\.state), [.unconfirmed, .unconfirmed])
    }
    func testCorruptSecurePreferencesCannotBeDeclaredRemoved() throws {
        let result = try snapshot(["Default": ["Preferences": catalogue([:]),
            "Secure Preferences": Data("not-json".utf8)]])
        XCTAssertEqual(result.profiles.first?.state, .unconfirmed)
    }
    func testModernRegisteredCopyWithoutLegacyStateIsNotOff() throws {
        let result = try snapshot(["Default": ["Preferences": json(["profile": [:]]),
            "Secure Preferences": catalogue([id: ["location": 4, "disable_reasons": [Int]()]])]])
        XCTAssertEqual(result.profiles.first?.state, .noExplicitLoss)
    }
    func testConflictingSourcesStayUnconfirmed() throws {
        let result = try snapshot(["Default": ["Preferences": catalogue([id: ["state": 1]]),
            "Secure Preferences": catalogue([id: ["state": 0]])]])
        XCTAssertEqual(result.profiles.first?.state, .unconfirmed)
    }
    func testAlternativeAcceptedCopyPreventsAnExplicitOffVerdict() throws {
        let result = try snapshot(["Default": ["Preferences": catalogue([
            id: ["state": 0], "published-copy": ["disable_reasons": [Int]()]
        ])]], ids: [id, "published-copy"])
        XCTAssertEqual(result.profiles.first?.state, .noExplicitLoss)
    }
    func testMissingRootNoIDsAndGuestProfilesAreNotClaimedVerified() throws {
        let absent = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        XCTAssertEqual(E.inspect(root: absent, ids: [id]), .unavailable)
        XCTAssertEqual(try snapshot(["Default": ["Preferences": catalogue([:])]], ids: []), .unavailable)
        XCTAssertEqual(try snapshot(["Guest Profile": ["Preferences": catalogue([:])]]), .unavailable)
        let regular = try snapshot(["Default": ["Preferences": catalogue([id: ["state": 1]])],
            "Guest Profile": ["Preferences": catalogue([:])]])
        XCTAssertEqual(regular.profiles.count, 1)
    }
    func testWrongCatalogueShapeAndUnknownAcceptedCopyStayUnconfirmed() throws {
        XCTAssertEqual(try snapshot(["Default": ["Preferences": json(["extensions": "bad"])]])
            .profiles.first?.state, .unconfirmed)
        XCTAssertEqual(try snapshot(["Default": ["Preferences": catalogue([id: [String: Any]()])]])
            .profiles.first?.state, .unconfirmed)
    }
    func testReadBudgetExhaustionCannotBeDeclaredRemoval() throws {
        let preferences = try catalogue([:])
        let result = try snapshot(["Default": ["Preferences": preferences],
                                   "Profile 1": ["Preferences": preferences]],
                                  maximumBytes: preferences.count)
        XCTAssertEqual(result.profiles.map(\.state), [.explicitLoss, .unconfirmed])
        XCTAssertEqual(try snapshot(["Default": ["Preferences": preferences]], maximumBytes: 0), .unavailable)
    }
    private func evidence(_ state: E.State) -> E.Snapshot {
        .init(enumerationVerified: true, profiles: [.init(folder: "Profile 1", state: state)])
    }
    func testTwoSeparatedReadsRequiredAndRepeatedFastReadsDoNotConfirm() {
        var check = E.Confirmation()
        check.observe(evidence(.explicitLoss), at: now)
        XCTAssertEqual(check.confirmed(at: now), [])
        check.observe(evidence(.explicitLoss), at: now.addingTimeInterval(1))
        XCTAssertEqual(check.confirmed(at: now.addingTimeInterval(1)), [])
        check.observe(evidence(.explicitLoss), at: now.addingTimeInterval(15))
        XCTAssertEqual(check.confirmed(at: now.addingTimeInterval(15)), ["Profile 1"])
    }
    func testReenabledOrUnknownEvidenceClearsProfileClosureEvidence() {
        for recovered: E.State in [.noExplicitLoss, .unconfirmed] {
            var check = E.Confirmation()
            check.observe(evidence(.explicitLoss), at: now)
            check.observe(evidence(.explicitLoss), at: now.addingTimeInterval(15))
            check.observe(evidence(recovered), at: now.addingTimeInterval(30))
            XCTAssertEqual(check.confirmed(at: now.addingTimeInterval(30)), [])
        }
    }
    func testOldFutureAndUnavailableEvidenceDoNotConfirmClosure() {
        var check = E.Confirmation()
        check.observe(evidence(.explicitLoss), at: now)
        check.observe(evidence(.explicitLoss), at: now.addingTimeInterval(15))
        XCTAssertEqual(check.confirmed(at: now.addingTimeInterval(14)), [])
        XCTAssertEqual(check.confirmed(at: now.addingTimeInterval(61)), [])
        check.observe(evidence(.explicitLoss), at: now.addingTimeInterval(100))
        XCTAssertEqual(check.confirmed(at: now.addingTimeInterval(100)), [])
        check.observe(.unavailable, at: now.addingTimeInterval(115))
        check.observe(evidence(.explicitLoss), at: now.addingTimeInterval(130))
        XCTAssertEqual(check.confirmed(at: now.addingTimeInterval(130)), [])
    }
    func testConfirmedProfileLossOverridesFreshBundleHeartbeatButRecoveryCancelsIt() {
        typealias P = BrowserGuardPolicy
        var session = P.Session()
        let warned = P.verdict(coverage: .needsExtension, lastSeen: now, now: now,
            graceStart: now.addingTimeInterval(-3600), firstViolation: nil,
            recentlyClosed: false, profileLoss: true)
        XCTAssertEqual(warned, .warn(.profileUnprotected, closeAt: now.addingTimeInterval(P.warnSilent)))
        session.observe(warned, browser: id, at: now, verifiedConnection: true)
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: now, now: now.addingTimeInterval(P.warnSilent),
            graceStart: now.addingTimeInterval(-3600), firstViolation: session.firstViolation(browser: id),
            recentlyClosed: false, profileLoss: true), .close(.profileUnprotected))
        let recovered = P.verdict(coverage: .needsExtension, lastSeen: now, now: now,
            graceStart: now.addingTimeInterval(-3600), firstViolation: now,
            recentlyClosed: false, profileLoss: false)
        XCTAssertEqual(recovered, .ok)
        XCTAssertTrue(P.shouldForceClose(active: true, coverage: .needsExtension,
            lastSeen: now, now: now, profileLoss: true))
        XCTAssertFalse(P.shouldForceClose(active: false, coverage: .needsExtension,
            lastSeen: now, now: now, profileLoss: true))
        XCTAssertFalse(P.shouldForceClose(active: true, coverage: .exempt,
            lastSeen: now, now: now, profileLoss: true))
    }
    func testConfirmedOffProfileCannotRenewGraceWithAnotherProfilesHeartbeat() {
        typealias P = BrowserGuardPolicy
        var session = P.Session()
        _ = session.graceStart(browser: id, now: now, notBefore: now, instance: "healthy")
        session.observe(.ok, browser: id, at: now, verifiedConnection: true)
        let warned = now.addingTimeInterval(15)
        for attempt in 0..<5 {
            let time = warned.addingTimeInterval(Double(attempt) * 5)
            let start = session.graceStart(browser: id, now: time, notBefore: now,
                                           instance: "restart-\(attempt)")
            let verdict = P.verdict(coverage: .needsExtension, lastSeen: time, now: time,
                graceStart: start, firstViolation: session.firstViolation(browser: id),
                recentlyClosed: false, profileLoss: true)
            XCTAssertEqual(verdict, .warn(.profileUnprotected, closeAt: warned.addingTimeInterval(P.warnSilent)))
            session.observe(verdict, browser: id, at: time, verifiedConnection: false)
            XCTAssertEqual(session.firstViolation(browser: id), warned)
        }
        let closed = warned.addingTimeInterval(P.warnSilent)
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: closed, now: closed,
            graceStart: closed, firstViolation: session.firstViolation(browser: id),
            recentlyClosed: false, profileLoss: true), .close(.profileUnprotected))
    }
    func testRelaunchRefreshHoldsCachedProfileClosureWithoutResettingWarning() {
        typealias P = BrowserGuardPolicy
        let first = now.addingTimeInterval(-60)
        var session = P.Session()
        session.observe(.warn(.profileUnprotected, closeAt: first.addingTimeInterval(P.warnSilent)),
                        browser: id, at: first)
        let pending = P.verdict(coverage: .needsExtension, lastSeen: now, now: now,
            graceStart: now, firstViolation: session.firstViolation(browser: id),
            recentlyClosed: true, profileLoss: true, profileRefreshPending: true)
        XCTAssertEqual(pending, .warn(.profileUnprotected, closeAt: now.addingTimeInterval(1)))
        session.observe(pending, browser: id, at: now)
        XCTAssertEqual(session.firstViolation(browser: id), first)
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: now, now: now,
            graceStart: now, firstViolation: session.firstViolation(browser: id),
            recentlyClosed: true, profileLoss: true), .close(.profileUnprotected))
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: now, now: now,
            graceStart: now, firstViolation: first, recentlyClosed: true, profileLoss: false), .ok)
        // Refreshing profile settings does not suspend an independent heartbeat failure.
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: nil, now: now,
            graceStart: now, firstViolation: first, recentlyClosed: true,
            profileRefreshPending: true), .close(.extensionSilent))
    }
    func testRunningProfileRequiresAReadingAtOriginalWarningDeadline() {
        typealias P = BrowserGuardPolicy
        let deadline = now.addingTimeInterval(P.warnSilent)
        XCTAssertFalse(P.profileRefreshRequired(profileLoss: true, checkedAt: now,
            now: deadline.addingTimeInterval(-1), firstViolation: now, recentlyClosed: false))
        XCTAssertTrue(P.profileRefreshRequired(profileLoss: true,
            checkedAt: deadline.addingTimeInterval(-15), now: deadline,
            firstViolation: now, recentlyClosed: false))
        XCTAssertTrue(P.profileRefreshRequired(profileLoss: true, checkedAt: nil, now: deadline,
            firstViolation: now, recentlyClosed: false))
        XCTAssertFalse(P.profileRefreshRequired(profileLoss: true, checkedAt: deadline, now: deadline,
            firstViolation: now, recentlyClosed: false))
        XCTAssertTrue(P.profileRefreshRequired(profileLoss: true,
            checkedAt: deadline.addingTimeInterval(1), now: deadline,
            firstViolation: now, recentlyClosed: false))
        XCTAssertFalse(P.profileRefreshRequired(profileLoss: false, checkedAt: nil, now: deadline,
            firstViolation: now, recentlyClosed: false))
    }
    func testRepeatClosureAlsoRequiresDeadlineReadWithoutRenewingWarning() {
        typealias P = BrowserGuardPolicy
        let deadline = now.addingTimeInterval(P.warnRepeat)
        XCTAssertTrue(P.profileRefreshRequired(profileLoss: true, checkedAt: now, now: deadline,
            firstViolation: now, recentlyClosed: true))
        XCTAssertFalse(P.profileRefreshRequired(profileLoss: true, checkedAt: deadline,
            now: deadline.addingTimeInterval(1), firstViolation: now, recentlyClosed: true))
    }
    func testForceRereadNeedsSameStillConfirmedExplicitLoss() {
        let off = E.Snapshot(enumerationVerified: true,
            profiles: [.init(folder: "Profile 1", state: .explicitLoss)])
        XCTAssertTrue(E.lossPersists(in: off, confirmedFolders: ["Profile 1"]))
        XCTAssertFalse(E.lossPersists(in: off, confirmedFolders: []))
        XCTAssertFalse(E.lossPersists(in: off, confirmedFolders: ["Default"]))
        XCTAssertFalse(E.lossPersists(in: .unavailable, confirmedFolders: ["Profile 1"]))
        for state in [E.State.noExplicitLoss, .unconfirmed] {
            XCTAssertFalse(E.lossPersists(in: .init(enumerationVerified: true,
                profiles: [.init(folder: "Profile 1", state: state)]), confirmedFolders: ["Profile 1"]))
        }
    }
}
