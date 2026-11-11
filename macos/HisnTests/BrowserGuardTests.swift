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
                         firstViolationAgo: TimeInterval? = nil,
                         repeatOffender: Bool = false,
                         profileLoss: Bool = false) -> P.Verdict {
        P.verdict(coverage: coverage,
                  lastSeen: lastSeen.map { now.addingTimeInterval(-$0) },
                  now: now,
                  firstViolation: firstViolationAgo.map { now.addingTimeInterval(-$0) },
                  recentlyClosed: repeatOffender,
                  profileLoss: profileLoss)
    }

    // MARK: Coverage

    func testLiveStatusDistinguishesHealthySilentAndUnsupportedBrowsers() {
        XCTAssertEqual(P.connectionState(coverage: .needsExtension, lastSeen: now,
                                        now: now, profileLoss: false), .connected)
        XCTAssertEqual(P.connectionState(coverage: .needsExtension, lastSeen: nil,
                                        now: now, profileLoss: false), .awaitingExtension)
        XCTAssertEqual(P.connectionState(coverage: .uncovered, lastSeen: now,
                                        now: now, profileLoss: false), .unsupported)
        XCTAssertEqual(P.connectionState(coverage: .allowedByUser, lastSeen: nil,
                                        now: now, profileLoss: false), .exempt)
    }

    func testLiveStatusNeverHidesConfirmedProfileLossBehindHealthyReply() {
        XCTAssertEqual(P.connectionState(coverage: .needsExtension, lastSeen: now,
                                        now: now, profileLoss: true), .profileUnprotected)
    }

    func testIndependentBrowserRepliesKeepHealthyBrowserOpenThroughoutRecovery() throws {
        let healthyID = "com.google.Chrome", missingID = "net.imput.helium"
        let key = try XCTUnwrap(BrowserCheckInIdentity.key(browser: healthyID, processID: 101))
        for seconds in [0.0, 30, 59, 60, 120, 600] {
            let time = now.addingTimeInterval(seconds)
            let authority = [key: time]
            let healthy = P.checkIn(browser: healthyID, processID: 101, authority: authority, local: nil)
            let missing = P.checkIn(browser: missingID, processID: 202, authority: authority, local: time)
            XCTAssertEqual(P.connectionState(coverage: .needsExtension, lastSeen: healthy,
                                            now: time, profileLoss: false), .connected)
            XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: healthy, now: time,
                                    firstViolation: now, recentlyClosed: false), .ok)
            XCTAssertNil(missing)
            // Silenced is not disabled: however long the gap, no deadline.
            XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: missing, now: time,
                                    firstViolation: now, recentlyClosed: false),
                           .attention(.extensionSilent))
            XCTAssertEqual(P.connectionState(coverage: .needsExtension, lastSeen: missing,
                                            now: time, profileLoss: false), .awaitingExtension)
        }
    }

    func testReplyRestoredBeforeAnyDeadlineCancelsAttentionAndUpdatesStatus() {
        let restored = now.addingTimeInterval(59)
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: restored, now: restored,
                                firstViolation: now, recentlyClosed: false), .ok)
        XCTAssertEqual(P.connectionState(coverage: .needsExtension, lastSeen: restored,
                                        now: restored, profileLoss: false), .connected)
        let stale = restored.addingTimeInterval(-P.staleAfter)
        XCTAssertEqual(P.connectionState(coverage: .needsExtension, lastSeen: stale,
                                        now: restored, profileLoss: false), .awaitingExtension)
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: stale, now: restored,
                                firstViolation: now, recentlyClosed: false),
                       .attention(.extensionSilent))
    }

    func testGuardIsContinuousWhileHisnRuns() {
        // The former "require outside a lock" opt-in is gone: there is no
        // inactive state, so a lock cannot be what decides whether a browser
        // is guarded.
        XCTAssertTrue(P.isActive())
    }

    func testAvailableAuthorityDoesNotFallBackToAnEditableLocalHeartbeat() {
        let id = "net.imput.helium"
        XCTAssertEqual(P.checkIn(browser: id, authority: nil, local: now), now)
        XCTAssertNil(P.checkIn(browser: id, authority: [:], local: now))
        XCTAssertNil(P.checkIn(browser: id, authority: ["com.google.Chrome": now], local: now))
        let stale = now.addingTimeInterval(-600)
        XCTAssertEqual(P.checkIn(browser: id, authority: [id: stale], local: now), stale)
    }

    func testHealthySiblingProcessCannotVerifyAnUnprotectedInstance() throws {
        let id = "net.imput.helium"
        let key = try XCTUnwrap(BrowserCheckInIdentity.key(browser: id, processID: 101))
        let authority = [id: now, key: now]
        XCTAssertEqual(P.checkIn(browser: id, processID: 101, authority: authority, local: nil), now)
        XCTAssertNil(P.checkIn(browser: id, processID: 202, authority: authority, local: now))
        XCTAssertNil(P.checkIn(browser: id, processID: 202, authority: [id: now], local: now))
        XCTAssertNil(P.checkIn(browser: id, processID: 0, authority: nil, local: now))
        XCTAssertEqual(P.checkIn(browser: id, processID: 202, authority: nil, local: now), now)
    }

    func testHealthySiblingCannotClearStoppedOffenderButVerifiedRepairCan() {
        let id = "net.imput.helium"
        var session = P.Session()
        session.setRunningInstances([id: ["healthy", "missing"]])
        _ = session.graceStart(browser: id, now: now, notBefore: now, instance: "healthy")
        session.observe(.ok, browser: id, at: now, verifiedConnection: true, instance: "healthy")
        session.observe(.warn(.profileUnprotected, closeAt: now.addingTimeInterval(P.warnProfileLoss)),
            browser: id, at: now, instance: "missing")
        session.setRunningInstances([id: ["healthy"]])
        session.observe(.ok, browser: id, at: now.addingTimeInterval(55),
            verifiedConnection: true, instance: "healthy")
        XCTAssertEqual(session.firstViolation(browser: id), now)
        session.setRunningInstances([id: ["healthy", "relaunched"]])
        _ = session.graceStart(browser: id, now: now.addingTimeInterval(P.warnProfileLoss),
            notBefore: now, instance: "relaunched")
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: nil,
            now: now.addingTimeInterval(P.warnProfileLoss),
            firstViolation: session.firstViolation(browser: id), recentlyClosed: false,
            profileLoss: true), .close(.profileUnprotected))
        session.setRunningInstances([id: ["repaired"]])
        session.observe(.ok, browser: id, at: now.addingTimeInterval(P.warnProfileLoss + 10),
            verifiedConnection: true, instance: "repaired")
        XCTAssertFalse(session.hasUnresolvedWarning(browser: id))
    }

    func testRepairingStaggeredOffenderCannotExtendSiblingsVisibleDeadline() {
        let id = "net.imput.helium"
        var session = P.Session()
        session.setRunningInstances([id: ["first", "second"]])
        _ = session.graceStart(browser: id, now: now, notBefore: now, instance: "first")
        session.observe(.warn(.profileUnprotected, closeAt: now.addingTimeInterval(P.warnProfileLoss)),
            browser: id, at: now, instance: "first")
        let later = now.addingTimeInterval(30)
        _ = session.graceStart(browser: id, now: later, notBefore: now, instance: "second")
        let inherited = P.verdict(coverage: .needsExtension, lastSeen: nil, now: later,
            firstViolation: session.firstViolation(browser: id), recentlyClosed: false,
            profileLoss: true)
        XCTAssertEqual(inherited,
                       .warn(.profileUnprotected, closeAt: now.addingTimeInterval(P.warnProfileLoss)))
        session.observe(inherited, browser: id, at: later, instance: "second")
        session.observe(.ok, browser: id, at: now.addingTimeInterval(40),
            verifiedConnection: true, instance: "first")
        XCTAssertEqual(session.firstViolation(browser: id), now)
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: nil,
            now: now.addingTimeInterval(P.warnProfileLoss),
            firstViolation: session.firstViolation(browser: id), recentlyClosed: false,
            profileLoss: true), .close(.profileUnprotected))
    }

    func testForceCloseRequiresConfirmedProfileLoss() throws {
        let id = "net.imput.helium"
        // A wrong-process or missing heartbeat is not evidence for force.
        XCTAssertFalse(P.shouldForceClose(active: true, coverage: .needsExtension,
            lastSeen: nil, now: now))
        XCTAssertFalse(P.shouldForceClose(active: true, coverage: .needsExtension,
            lastSeen: now, now: now))
        XCTAssertTrue(P.shouldForceClose(active: true, coverage: .needsExtension,
            lastSeen: nil, now: now, profileLoss: true))
        // A fresh reply from the same PID cannot cancel a confirmed profile loss.
        let authority = [try XCTUnwrap(BrowserCheckInIdentity.key(browser: id, processID: 101)): now]
        let own = P.checkIn(browser: id, processID: 101, authority: authority, local: nil)
        XCTAssertTrue(P.shouldForceClose(active: true, coverage: .needsExtension,
            lastSeen: own, now: now, profileLoss: true))
        XCTAssertNil(P.instanceCheckIn(lastSeen: own, launchedAt: now.addingTimeInterval(1)))
    }

    func testConcurrentRelaunchAfterClosureGetsFreshFiveMinuteWarning() {
        let id = "net.imput.helium"
        var session = P.Session()
        session.setRunningInstances([id: ["healthy", "missing"]])
        _ = session.graceStart(browser: id, now: now, notBefore: now, instance: "healthy")
        session.observe(.ok, browser: id, at: now, verifiedConnection: true, instance: "healthy")
        session.observe(.warn(.profileUnprotected, closeAt: now.addingTimeInterval(P.warnProfileLoss)),
            browser: id, at: now, instance: "missing")
        session.observe(.close(.profileUnprotected), browser: id,
            at: now.addingTimeInterval(P.warnProfileLoss), instance: "missing")
        session.setRunningInstances([id: ["healthy"]])
        session.observe(.ok, browser: id, at: now.addingTimeInterval(P.warnProfileLoss + 1),
            verifiedConnection: true, instance: "healthy")
        XCTAssertTrue(session.hasUnresolvedWarning(browser: id))
        session.setRunningInstances([id: ["healthy", "restarted"]])
        let returned = now.addingTimeInterval(P.warnProfileLoss + 5)
        _ = session.graceStart(browser: id, now: returned, notBefore: now, instance: "restarted")
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: nil, now: returned,
            firstViolation: session.firstViolation(browser: id), recentlyClosed: false,
            profileLoss: true),
            .warn(.profileUnprotected, closeAt: returned.addingTimeInterval(P.warnProfileLoss)))
        session.observe(.warn(.profileUnprotected,
            closeAt: returned.addingTimeInterval(P.warnProfileLoss)),
            browser: id, at: returned, instance: "restarted")
        session.observe(.ok, browser: id, at: returned, verifiedConnection: true, instance: "healthy")
        XCTAssertEqual(session.firstViolation(browser: id), returned)
    }

    func testHistoricalClosureCannotEraseNewWarningAfterRepairAndRestart() {
        let id = "net.imput.helium"
        var session = P.Session()
        session.setRunningInstances([id: ["healthy", "offender"]])
        _ = session.graceStart(browser: id, now: now, notBefore: now, instance: "healthy")
        session.observe(.ok, browser: id, at: now, verifiedConnection: true, instance: "healthy")
        session.observe(.warn(.profileUnprotected, closeAt: now.addingTimeInterval(P.warnProfileLoss)),
            browser: id, at: now, instance: "offender")
        session.observe(.close(.profileUnprotected), browser: id,
            at: now.addingTimeInterval(P.warnProfileLoss), instance: "offender")
        session.observe(.ok, browser: id, at: now.addingTimeInterval(P.warnProfileLoss + 5),
            verifiedConnection: true, instance: "offender")
        XCTAssertFalse(session.hasUnresolvedWarning(browser: id))
        let lostAgain = now.addingTimeInterval(P.warnProfileLoss + 100)
        session.observe(.warn(.profileUnprotected,
            closeAt: lostAgain.addingTimeInterval(P.warnProfileLoss)),
            browser: id, at: lostAgain, instance: "offender")
        session.setRunningInstances([id: ["healthy", "restarted"]])
        let restarted = lostAgain.addingTimeInterval(40)
        _ = session.graceStart(browser: id, now: restarted, notBefore: now, instance: "restarted")
        XCTAssertEqual(session.firstViolation(browser: id), lostAgain)
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: nil, now: restarted,
            firstViolation: session.firstViolation(browser: id), recentlyClosed: false,
            profileLoss: true),
            .warn(.profileUnprotected, closeAt: lostAgain.addingTimeInterval(P.warnProfileLoss)))
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: nil,
            now: lostAgain.addingTimeInterval(P.warnProfileLoss),
            firstViolation: session.firstViolation(browser: id), recentlyClosed: false,
            profileLoss: true), .close(.profileUnprotected))
    }

    func testWarningSurvivesTheBrowserBeingAbsentAndRestarting() {
        var session = P.Session()
        let id = "net.imput.helium"
        _ = session.graceStart(browser: id, now: now, notBefore: now, instance: "first")
        session.observe(.warn(.profileUnprotected, closeAt: now.addingTimeInterval(P.warnProfileLoss)),
                        browser: id, at: now)
        // No observations while the process is stopped: its ledger is retained.
        let restarted = now.addingTimeInterval(P.warnProfileLoss - 30)
        _ = session.graceStart(browser: id, now: restarted, notBefore: now, instance: "second")
        XCTAssertEqual(session.firstViolation(browser: id), now)
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: nil, now: restarted,
                                firstViolation: session.firstViolation(browser: id),
                                recentlyClosed: false, profileLoss: true),
                       .warn(.profileUnprotected,
                             closeAt: now.addingTimeInterval(P.warnProfileLoss)))
    }

    func testDelayedForceCloseRechecksEnforcementRecoveryAndExceptions() {
        XCTAssertFalse(P.shouldForceClose(active: false, coverage: .needsExtension,
            lastSeen: nil, now: now, profileLoss: true))
        XCTAssertFalse(P.shouldForceClose(active: true, coverage: .needsExtension,
            lastSeen: now, now: now))
        XCTAssertFalse(P.shouldForceClose(active: true, coverage: .exempt,
            lastSeen: nil, now: now, profileLoss: true))
        XCTAssertFalse(P.shouldForceClose(active: true, coverage: .allowedByUser,
            lastSeen: nil, now: now, profileLoss: true))
        XCTAssertTrue(P.shouldForceClose(active: true, coverage: .needsExtension,
            lastSeen: nil, now: now, profileLoss: true))
        XCTAssertTrue(P.shouldForceClose(active: true, coverage: .uncovered, lastSeen: now, now: now))
    }

    func testUncoveredBrowserRestartKeepsItsWarningDeadline() {
        var session = P.Session()
        let id = "org.mozilla.firefox"
        session.observe(.warn(.uncovered, closeAt: now.addingTimeInterval(P.warnUncovered)),
                        browser: id, at: now)
        let restarted = now.addingTimeInterval(P.warnUncovered)
        XCTAssertEqual(P.verdict(coverage: .uncovered, lastSeen: nil, now: restarted,
                                firstViolation: session.firstViolation(browser: id),
                                recentlyClosed: false), .close(.uncovered))
    }

    func testHealthyReconnectClearsWarning() {
        var session = P.Session()
        let id = "net.imput.helium"
        _ = session.graceStart(browser: id, now: now, notBefore: now)
        session.observe(.warn(.profileUnprotected, closeAt: now.addingTimeInterval(P.warnProfileLoss)),
                        browser: id, at: now)
        session.observe(.ok, browser: id, at: now.addingTimeInterval(10))
        XCTAssertNil(session.firstViolation(browser: id))
    }

    func testUnconfirmedProfileAfterWarningCancelsTheCountdown() {
        let id = "net.imput.helium"
        var session = P.Session()
        _ = session.graceStart(browser: id, now: now, notBefore: now, instance: "first")
        session.observe(.warn(.profileUnprotected, closeAt: now.addingTimeInterval(P.warnProfileLoss)),
                        browser: id, at: now, instance: "first")
        XCTAssertNotNil(session.firstViolation(browser: id))
        // The next read cannot reproduce the loss: the evidence is no longer
        // positive, so the browser must not be closed on the old deadline.
        let attention = P.verdict(coverage: .needsExtension, lastSeen: nil,
            now: now.addingTimeInterval(60), firstViolation: session.firstViolation(browser: id),
            recentlyClosed: false, profileLoss: false)
        XCTAssertEqual(attention, .attention(.extensionSilent))
        session.observe(attention, browser: id, at: now.addingTimeInterval(60), instance: "first")
        XCTAssertNil(session.firstViolation(browser: id))
        XCTAssertFalse(P.shouldForceClose(active: true, coverage: .needsExtension,
            lastSeen: nil, now: now.addingTimeInterval(P.warnProfileLoss)))
    }

    func testSeparateBrowsersKeepSeparateWarningsAndResetClearsThem() {
        var session = P.Session()
        session.observe(.warn(.profileUnprotected, closeAt: now.addingTimeInterval(P.warnProfileLoss)),
                        browser: "net.imput.helium", at: now)
        XCTAssertNil(session.firstViolation(browser: "com.google.Chrome"))
        session.reset()
        XCTAssertNil(session.firstViolation(browser: "net.imput.helium"))
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

    func testSilenceIsNeverAClosureEvenAfterHours() {
        XCTAssertEqual(verdict(.needsExtension, lastSeen: nil),
                       .attention(.extensionSilent))
        XCTAssertEqual(verdict(.needsExtension, lastSeen: nil, firstViolationAgo: 4 * 3600),
                       .attention(.extensionSilent))
        XCTAssertEqual(verdict(.needsExtension, lastSeen: 4 * 3600),
                       .attention(.extensionSilent))
        XCTAssertEqual(P.shouldForceClose(active: true, coverage: .needsExtension,
            lastSeen: nil, now: now), false)
    }

    func testConfirmedProfileLossGetsTheFullFiveMinutes() {
        XCTAssertEqual(verdict(.needsExtension, lastSeen: nil,
                               firstViolationAgo: 0, profileLoss: true),
                       .warn(.profileUnprotected,
                             closeAt: now.addingTimeInterval(P.warnProfileLoss)))
        XCTAssertEqual(verdict(.needsExtension, lastSeen: nil,
                               firstViolationAgo: P.warnProfileLoss - 1, profileLoss: true),
                       .warn(.profileUnprotected, closeAt: now.addingTimeInterval(1)))
        XCTAssertEqual(verdict(.needsExtension, lastSeen: nil,
                               firstViolationAgo: P.warnProfileLoss, profileLoss: true),
                       .close(.profileUnprotected))
        // A heartbeat from another profile does not shorten it either.
        XCTAssertEqual(verdict(.needsExtension, lastSeen: 1, profileLoss: true),
                       .warn(.profileUnprotected,
                             closeAt: now.addingTimeInterval(P.warnProfileLoss)))
    }

    func testUncoveredBrowserKeepsItsOneMinuteWarning() {
        XCTAssertEqual(verdict(.uncovered, firstViolationAgo: 59),
                       .warn(.uncovered, closeAt: now.addingTimeInterval(1)))
        XCTAssertEqual(verdict(.uncovered, firstViolationAgo: 60), .close(.uncovered))
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
            now: now.addingTimeInterval(55), firstViolation: now,
            recentlyClosed: false), .attention(.extensionSilent))
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
            let loss = coverage == .needsExtension
            XCTAssertFalse(P.shouldForceClose(active: true, coverage: coverage,
                lastSeen: nil, now: wakeDeadline.addingTimeInterval(-0.001),
                profileLoss: loss, notBefore: wakeDeadline))
            XCTAssertTrue(P.shouldForceClose(active: true, coverage: coverage,
                lastSeen: nil, now: wakeDeadline, profileLoss: loss, notBefore: wakeDeadline))
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

    func testRefusingSameInstanceCannotManufactureFutureForceDeadlines() {
        var session = P.Session()
        let id = "net.imput.helium"
        _ = session.graceStart(browser: id, now: now, notBefore: now, instance: "first")
        session.observe(.warn(.profileUnprotected, closeAt: now.addingTimeInterval(P.warnProfileLoss)),
                        browser: id, at: now)
        let requested = now.addingTimeInterval(P.warnProfileLoss)
        for elapsed in [300.0, 305.2, 310.0, 315.2, 320.0] {
            let time = now.addingTimeInterval(elapsed)
            _ = session.graceStart(browser: id, now: time, notBefore: now, instance: "first")
            let decision = P.verdict(coverage: .needsExtension, lastSeen: nil, now: time,
                firstViolation: session.firstViolation(browser: id),
                recentlyClosed: elapsed > P.warnProfileLoss, profileLoss: true)
            XCTAssertEqual(decision, .close(.profileUnprotected))
            session.observe(decision, browser: id, at: time)
            XCTAssertEqual(session.firstViolation(browser: id), now)
        }
        XCTAssertTrue(P.shouldForceClose(active: true, coverage: .needsExtension,
            lastSeen: nil, now: requested.addingTimeInterval(10), profileLoss: true,
            notBefore: P.forceRecoveryDeadline(requestedAt: requested,
                awakeSince: now, warningDeadline: nil)))
        let relaunched = now.addingTimeInterval(P.warnProfileLoss + 21)
        _ = session.graceStart(browser: id, now: relaunched,
                               notBefore: now, instance: "second")
        XCTAssertNil(session.firstViolation(browser: id))
        // A new process gets the full five-minute warning again, not five seconds.
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: nil, now: relaunched,
            firstViolation: session.firstViolation(browser: id),
            recentlyClosed: true, profileLoss: true),
            .warn(.profileUnprotected,
                  closeAt: relaunched.addingTimeInterval(P.warnProfileLoss)))
    }

    func testHeartbeatTimingBudgetIsIndependentOfExpectedConstants() {
        XCTAssertEqual(P.staleAfter, 90)
        XCTAssertEqual(P.launchGrace, 60)
        XCTAssertEqual(P.warnProfileLoss, 300)
        XCTAssertEqual(P.warnUncovered, 60)
        XCTAssertEqual(P.warnRepeat, 5)
    }

    func testSilentExtensionIsAttentionNotAClosure() {
        XCTAssertEqual(verdict(.needsExtension, lastSeen: 600),
                       .attention(.extensionSilent))
    }

    func testNeverSeenExtensionIsAttentionNotAClosure() {
        guard case .attention(.extensionSilent) = verdict(.needsExtension, lastSeen: nil) else {
            return XCTFail("a browser that never checked in must be shown, not closed")
        }
    }

    func testConfirmedLossWarningRunsOutThenCloses() {
        XCTAssertEqual(verdict(.needsExtension, lastSeen: 600,
                               firstViolationAgo: P.warnProfileLoss, profileLoss: true),
                       .close(.profileUnprotected))
    }

    func testConfirmedLossWarningInProgressKeepsItsOriginalDeadline() {
        XCTAssertEqual(verdict(.needsExtension, lastSeen: 600,
                               firstViolationAgo: 20, profileLoss: true),
                       .warn(.profileUnprotected,
                             closeAt: now.addingTimeInterval(P.warnProfileLoss - 20)))
    }

    func testExtensionComingBackCancelsTheWarning() {
        XCTAssertEqual(verdict(.needsExtension, lastSeen: 2, firstViolationAgo: 30), .ok)
    }

    func testRelaunchWithoutFixingStillGetsTheFullFiveMinutes() {
        XCTAssertEqual(verdict(.needsExtension, lastSeen: 600,
                               repeatOffender: true, profileLoss: true),
                       .warn(.profileUnprotected,
                             closeAt: now.addingTimeInterval(P.warnProfileLoss)))
    }

    func testWoundBackClockDoesNotMakeAnOldCheckInLookFresh() {
        // Last seen an hour "from now": the clock was wound back after the
        // extension was switched off. Not evidence of a live extension.
        XCTAssertFalse(P.extensionAlive(lastSeen: now.addingTimeInterval(3600), now: now))
        guard case .attention = verdict(.needsExtension, lastSeen: -3600) else {
            return XCTFail("a check-in stamped in the future must not keep a browser healthy")
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
        XCTAssertEqual(verdict(.uncovered),
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

    func testProcessStampsAreSeparateAndLegacyStampCannotFillTheGap() {
        let name = TestNamespace.make()
        defer { TestNamespace.dispose(name) }
        let d = UserDefaults(suiteName: name)
        let t = Date(timeIntervalSince1970: 1_800_000_000)
        let id = "net.imput.helium"
        ExtensionPresence.record(browser: id, processID: 101, at: t, in: d)
        XCTAssertEqual(ExtensionPresence.lastSeen(browser: id, processID: 101, in: d), t)
        XCTAssertNil(ExtensionPresence.lastSeen(browser: id, processID: 202, in: d))
        XCTAssertNil(ExtensionPresence.lastSeen(browser: id, processID: 0, in: d))
        ExtensionPresence.record(browser: id, at: t.addingTimeInterval(1), in: d)
        XCTAssertEqual(ExtensionPresence.lastSeen(browser: id, in: d), t.addingTimeInterval(1))
        XCTAssertEqual(ExtensionPresence.lastSeen(browser: id, processID: 101, in: d), t)
        XCTAssertNil(ExtensionPresence.lastSeen(browser: id, processID: 202, in: d))
    }

    func testProcessIdentityRejectsMalformedBrowserNamesAndInvalidPids() {
        for id in ["", ".net", "net..browser", "process:1:browser", "browser/name", String(repeating: "a", count: 256)] {
            XCTAssertNil(BrowserCheckInIdentity.key(browser: id, processID: 101))
        }
        XCTAssertNil(BrowserCheckInIdentity.key(browser: "net.imput.helium", processID: -1))
        XCTAssertEqual(BrowserCheckInIdentity.key(browser: "net.imput.helium", processID: 101),
            "process:101:net.imput.helium")
    }

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
            firstViolation: nil, recentlyClosed: false, profileLoss: true)
        XCTAssertEqual(warned, .warn(.profileUnprotected,
                                     closeAt: now.addingTimeInterval(P.warnProfileLoss)))
        session.observe(warned, browser: id, at: now, verifiedConnection: true)
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: now,
            now: now.addingTimeInterval(P.warnProfileLoss),
            firstViolation: session.firstViolation(browser: id),
            recentlyClosed: false, profileLoss: true), .close(.profileUnprotected))
        let recovered = P.verdict(coverage: .needsExtension, lastSeen: now, now: now,
            firstViolation: now, recentlyClosed: false, profileLoss: false)
        XCTAssertEqual(recovered, .ok)
        XCTAssertTrue(P.shouldForceClose(active: true, coverage: .needsExtension,
            lastSeen: now, now: now, profileLoss: true))
        XCTAssertFalse(P.shouldForceClose(active: false, coverage: .needsExtension,
            lastSeen: now, now: now, profileLoss: true))
        XCTAssertFalse(P.shouldForceClose(active: true, coverage: .exempt,
            lastSeen: now, now: now, profileLoss: true))
    }
    func testConfirmedOffProfileCannotRenewWithAnotherProfilesHeartbeat() {
        typealias P = BrowserGuardPolicy
        var session = P.Session()
        _ = session.graceStart(browser: id, now: now, notBefore: now, instance: "healthy")
        session.observe(.ok, browser: id, at: now, verifiedConnection: true)
        let warned = now.addingTimeInterval(15)
        for attempt in 0..<5 {
            let time = warned.addingTimeInterval(Double(attempt) * 5)
            _ = session.graceStart(browser: id, now: time, notBefore: now,
                                   instance: "restart-\(attempt)")
            let verdict = P.verdict(coverage: .needsExtension, lastSeen: time, now: time,
                firstViolation: session.firstViolation(browser: id),
                recentlyClosed: false, profileLoss: true)
            XCTAssertEqual(verdict, .warn(.profileUnprotected,
                closeAt: warned.addingTimeInterval(P.warnProfileLoss)))
            session.observe(verdict, browser: id, at: time, verifiedConnection: false)
            XCTAssertEqual(session.firstViolation(browser: id), warned)
        }
        let closed = warned.addingTimeInterval(P.warnProfileLoss)
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: closed, now: closed,
            firstViolation: session.firstViolation(browser: id),
            recentlyClosed: false, profileLoss: true), .close(.profileUnprotected))
    }
    func testRelaunchRefreshHoldsCachedProfileClosureWithoutResettingWarning() {
        typealias P = BrowserGuardPolicy
        let first = now.addingTimeInterval(-P.warnProfileLoss)
        var session = P.Session()
        session.observe(.warn(.profileUnprotected, closeAt: first.addingTimeInterval(P.warnProfileLoss)),
                        browser: id, at: first)
        let pending = P.verdict(coverage: .needsExtension, lastSeen: now, now: now,
            firstViolation: session.firstViolation(browser: id),
            recentlyClosed: true, profileLoss: true, profileRefreshPending: true)
        XCTAssertEqual(pending, .warn(.profileUnprotected, closeAt: now.addingTimeInterval(1)))
        session.observe(pending, browser: id, at: now)
        XCTAssertEqual(session.firstViolation(browser: id), first)
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: now, now: now,
            firstViolation: session.firstViolation(browser: id),
            recentlyClosed: true, profileLoss: true), .close(.profileUnprotected))
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: now, now: now,
            firstViolation: first, recentlyClosed: true, profileLoss: false), .ok)
        // Refreshing profile settings does not suspend a missing heartbeat, but
        // a missing heartbeat is still attention, never a closure on its own.
        XCTAssertEqual(P.verdict(coverage: .needsExtension, lastSeen: nil, now: now,
            firstViolation: first, recentlyClosed: true,
            profileRefreshPending: true), .attention(.extensionSilent))
    }
    func testRunningProfileRequiresAReadingAtOriginalWarningDeadline() {
        typealias P = BrowserGuardPolicy
        let deadline = now.addingTimeInterval(P.warnProfileLoss)
        XCTAssertFalse(P.profileRefreshRequired(profileLoss: true, checkedAt: now,
            now: deadline.addingTimeInterval(-1), firstViolation: now))
        XCTAssertTrue(P.profileRefreshRequired(profileLoss: true,
            checkedAt: deadline.addingTimeInterval(-15), now: deadline,
            firstViolation: now))
        XCTAssertTrue(P.profileRefreshRequired(profileLoss: true, checkedAt: nil, now: deadline,
            firstViolation: now))
        XCTAssertFalse(P.profileRefreshRequired(profileLoss: true, checkedAt: deadline, now: deadline,
            firstViolation: now))
        XCTAssertTrue(P.profileRefreshRequired(profileLoss: true,
            checkedAt: deadline.addingTimeInterval(1), now: deadline,
            firstViolation: now))
        XCTAssertFalse(P.profileRefreshRequired(profileLoss: false, checkedAt: nil, now: deadline,
            firstViolation: now))
    }
    func testProfileRefreshDeadlineIsTheClosureDeadline() {
        typealias P = BrowserGuardPolicy
        let deadline = now.addingTimeInterval(P.warnProfileLoss)
        XCTAssertTrue(P.profileRefreshRequired(profileLoss: true, checkedAt: now, now: deadline,
            firstViolation: now))
        XCTAssertFalse(P.profileRefreshRequired(profileLoss: true, checkedAt: deadline,
            now: deadline.addingTimeInterval(1), firstViolation: now))
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
