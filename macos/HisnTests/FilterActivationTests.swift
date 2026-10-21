import SystemExtensions
import XCTest
@testable import Hisn

final class FilterActivationTests: XCTestCase {
    func testConfiguredBridgeNeverReportsEditableMirrorsAsAnUnlockedAuthority() {
        let local = PolicyView(lock: nil, effectiveDeadline: nil,
                               allowlist: [], customBlocks: [], customTerms: [], blockedApps: [],
                               inspection: .default)
        let reply = BridgePolicy.reply(local: local, authority: nil,
                                       requiresAuthority: true, listVersion: 1)
        XCTAssertEqual(reply["ok"] as? Bool, false)
        XCTAssertEqual(reply["reason"] as? String, "authority-unreachable")
        XCTAssertNil(reply["lockUntil"], "the browser must reject this as a heartbeat")
    }

    func testBrowserOnlyBridgeStillSupportsLocalPolicy() {
        let local = PolicyView(lock: nil, effectiveDeadline: nil,
                               allowlist: [], customBlocks: ["blocked.example"], customTerms: [],
                               blockedApps: [], inspection: .default)
        let reply = BridgePolicy.reply(local: local, authority: nil,
                                       requiresAuthority: false, listVersion: 1)
        XCTAssertEqual(reply["lockUntil"] as? Double, 0)
        XCTAssertEqual(reply["customBlocks"] as? [String], ["blocked.example"])
    }

    private func activation(_ result: OSSystemExtensionRequest.Result) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let delegate = ActivationDelegate(continuation: continuation, needsApproval: {})
            let request = OSSystemExtensionRequest.activationRequest(
                forExtensionWithIdentifier: "app.hisn.Hisn.HisnFilter", queue: .main)
            delegate.request(request, didFinishWithResult: result)
            // A duplicate system callback must not resume the continuation twice.
            delegate.request(request, didFinishWithResult: result)
        }
    }

    func testCompletedActivationAllowsConfiguration() async throws {
        try await activation(.completed)
    }

    func testRebootPendingActivationDoesNotAllowConfiguration() async {
        do {
            try await activation(.willCompleteAfterReboot)
            XCTFail("A pending restart must not be reported as a running filter")
        } catch FilterController.FilterError.activationRequiresRestart {
            // Configuration must wait for the next launch after a restart.
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testApprovalCallbackDoesNotFinishActivation() async throws {
        var approvals = 0
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let delegate = ActivationDelegate(continuation: continuation) { approvals += 1 }
            let request = OSSystemExtensionRequest.activationRequest(
                forExtensionWithIdentifier: "app.hisn.Hisn.HisnFilter", queue: .main)
            delegate.requestNeedsUserApproval(request)
            XCTAssertEqual(approvals, 1)
            delegate.request(request, didFinishWithResult: .completed)
        }
    }
}

@MainActor
final class FilterRecoveryTests: XCTestCase {
    private enum Failure: Error { case activation }

    func testDisabledAndUnresponsiveConfigurationsAreRecovered() async {
        for condition in [FilterRecoveryCoordinator.Condition.disabled, .unresponsive] {
            let runner = FilterRecoveryCoordinator()
            var attempts: [FilterRecoveryCoordinator.Condition] = []
            await runner.run(eligible: { true }, inspect: { condition }, recover: {
                attempts.append($0)
            })
            XCTAssertEqual(attempts, [condition])
        }
    }

    func testHealthyOrUnreadablePreferencesNeverTriggerWrites() async {
        for condition in [FilterRecoveryCoordinator.Condition.healthy, .unavailable] {
            await FilterRecoveryCoordinator().run(eligible: { true }, inspect: { condition },
                                                 recover: { _ in XCTFail("unexpected write") })
        }
    }

    func testApprovalRestartAndUnsignedBuildGatesSkipEvenTheRead() async {
        await FilterRecoveryCoordinator().run(eligible: { false },
                                             inspect: { XCTFail("unexpected read"); return .disabled },
                                             recover: { _ in XCTFail("unexpected activation") })
    }

    func testEligibilityIsCheckedAgainAfterSuspension() async {
        var eligible = true
        await FilterRecoveryCoordinator().run(eligible: { eligible }, inspect: {
            eligible = false // approval or restart becomes pending while loading
            return .disabled
        }, recover: { _ in XCTFail("state changed during inspection") })
    }

    func testExpiredLockDoesNotReactivateAnIntentionallyDisabledFilter() async {
        var locked = true
        await FilterRecoveryCoordinator().run(eligible: { true }, inspect: {
            locked = false
            return .disabled
        }, stillNeeded: { $0 != .disabled || locked },
           recover: { _ in XCTFail("the lock ended before recovery") })
    }

    func testManualDisableDuringProviderQueryCancelsAutomaticRecovery() async {
        var configurationRevision = 0
        let revision = configurationRevision
        await FilterRecoveryCoordinator().run(eligible: { configurationRevision == revision },
                                             inspect: {
            configurationRevision += 1 // disable started during the provider query
            return .unresponsive
        }, recover: { _ in XCTFail("automatic recovery must respect the manual change") })
    }

    func testFailedRecoveryUsesMonotonicBackoffAndCapsIt() async {
        var uptime: TimeInterval = 100
        var attempts = 0
        let runner = FilterRecoveryCoordinator(uptime: { uptime })
        func tick() async {
            await runner.run(eligible: { true }, inspect: { .disabled }, recover: { _ in
                attempts += 1
                throw Failure.activation
            })
        }
        await tick()
        uptime = 159; await tick(); XCTAssertEqual(attempts, 1)
        uptime = 160; await tick(); XCTAssertEqual(attempts, 2)
        uptime = 279; await tick(); XCTAssertEqual(attempts, 2)
        uptime = 280; await tick(); XCTAssertEqual(attempts, 3)
        uptime = 519; await tick(); XCTAssertEqual(attempts, 3)
        uptime = 520; await tick(); XCTAssertEqual(attempts, 4)
        uptime = 819; await tick(); XCTAssertEqual(attempts, 4)
        uptime = 820; await tick(); XCTAssertEqual(attempts, 5)
    }

    func testSuccessfulRecoveryAlsoWaitsBeforeRetryingAnUnresponsiveProvider() async {
        var uptime: TimeInterval = 0
        var attempts = 0
        let runner = FilterRecoveryCoordinator(uptime: { uptime })
        func tick() async {
            await runner.run(eligible: { true }, inspect: { .unresponsive },
                             recover: { _ in attempts += 1 })
        }
        await tick()
        uptime = 59; await tick(); XCTAssertEqual(attempts, 1)
        uptime = 60; await tick(); XCTAssertEqual(attempts, 2)
    }

    func testOverlappingChecksDoNotStartAnotherInspectionOrActivation() async {
        let runner = FilterRecoveryCoordinator()
        var attempts = 0
        await runner.run(eligible: { true }, inspect: {
            await runner.run(eligible: { true },
                             inspect: { XCTFail("overlapping read"); return .disabled },
                             recover: { _ in XCTFail("overlapping activation") })
            return .disabled
        }, recover: { _ in
            attempts += 1
            await runner.run(eligible: { true },
                             inspect: { XCTFail("activation is still pending"); return .disabled },
                             recover: { _ in XCTFail("duplicate activation") })
        })
        XCTAssertEqual(attempts, 1)
    }

    func testAHealthyProviderClearsBackoffAfterRepeatedFailures() async {
        var uptime: TimeInterval = 0
        let runner = FilterRecoveryCoordinator(uptime: { uptime })
        await runner.run(eligible: { true }, inspect: { .disabled },
                         recover: { _ in throw Failure.activation })
        uptime = 60
        await runner.run(eligible: { true }, inspect: { .healthy }, recover: { _ in
            XCTFail("healthy provider")
        })
        var recovered = false
        await runner.run(eligible: { true }, inspect: { .disabled },
                         recover: { _ in recovered = true })
        XCTAssertTrue(recovered)
    }

    func testLostAuthorityDoesNotEndTheLastConfirmedLock() {
        var evidence = AuthorityLockEvidence()
        evidence.observe(locked: nil)
        XCTAssertFalse(evidence.locked, "absence is not evidence of a previous lock")
        evidence.observe(locked: true)
        evidence.observe(locked: nil)
        XCTAssertTrue(evidence.locked, "an IPC failure must not grant release")
        evidence.observe(locked: false)
        XCTAssertFalse(evidence.locked, "only a fresh authority reply ends the cached lock")
    }

    func testMissingInitialAuthorityDoesNotInventALockedAllowSet() {
        var evidence = AuthorityLockEvidence()
        let local: Set<String> = ["custom.browser"]
        evidence.observe(locked: nil)
        XCTAssertEqual(evidence.effectiveGuardAllowed(local: local), local)
        XCTAssertFalse(evidence.locked)
    }

    func testLostLockedAuthorityDoesNotAdmitNewLocalBrowserExceptions() {
        var evidence = AuthorityLockEvidence()
        evidence.observe(locked: true, guardAllowed: ["trusted.link.app"])
        evidence.observe(locked: nil)
        let allowed = evidence.effectiveGuardAllowed(
            local: ["trusted.link.app", "custom.browser"])
        XCTAssertEqual(allowed, ["trusted.link.app"])
        XCTAssertEqual(BrowserGuardPolicy.coverage(bundleID: "custom.browser",
            linked: [], userAllowed: allowed), .uncovered,
            "editable defaults must not exempt a custom browser during an IPC outage")
        XCTAssertTrue(evidence.locked)
    }

    func testLocalRemovalStillTightensRememberedLockedExceptions() {
        var evidence = AuthorityLockEvidence()
        evidence.observe(locked: true, guardAllowed: ["first.app", "second.app"])
        evidence.observe(locked: nil)
        XCTAssertEqual(evidence.effectiveGuardAllowed(local: ["second.app"]), ["second.app"])
        XCTAssertTrue(evidence.effectiveGuardAllowed(local: []).isEmpty)
    }

    func testConfirmedLockWithoutAllowSetPermitsNoExceptions() {
        var evidence = AuthorityLockEvidence()
        evidence.observe(locked: true)
        XCTAssertTrue(evidence.effectiveGuardAllowed(local: ["custom.browser"]).isEmpty)
        evidence.observe(locked: nil)
        XCTAssertTrue(evidence.effectiveGuardAllowed(local: ["custom.browser"]).isEmpty)
    }

    func testMissingAllowSetDoesNotErasePreviouslyConfirmedLockedExceptions() {
        var evidence = AuthorityLockEvidence()
        evidence.observe(locked: true, guardAllowed: ["first.app"])
        evidence.observe(locked: true)
        XCTAssertEqual(evidence.effectiveGuardAllowed(local: ["first.app", "second.app"]),
                       ["first.app"])
    }

    func testNewLockedAuthorityReplyReplacesItsRememberedAllowSet() {
        var evidence = AuthorityLockEvidence()
        evidence.observe(locked: true, guardAllowed: ["first.app", "second.app"])
        evidence.observe(locked: true, guardAllowed: ["second.app"])
        XCTAssertEqual(evidence.effectiveGuardAllowed(local: ["first.app", "second.app"]),
                       ["second.app"])
        evidence.observe(locked: true, guardAllowed: [])
        XCTAssertTrue(evidence.effectiveGuardAllowed(local: ["second.app"]).isEmpty)
    }

    func testOnlyConfirmedUnlockReleasesCachedAuthorityExceptionConstraint() {
        var evidence = AuthorityLockEvidence()
        evidence.observe(locked: true, guardAllowed: ["first.app"])
        evidence.observe(locked: nil, guardAllowed: ["custom.browser"])
        XCTAssertTrue(evidence.effectiveGuardAllowed(local: ["custom.browser"]).isEmpty,
                      "an allow-set without an authority verdict cannot release or replace the constraint")
        evidence.observe(locked: false)
        XCTAssertEqual(evidence.effectiveGuardAllowed(local: ["custom.browser"]), ["custom.browser"])
        evidence.observe(locked: true)
        XCTAssertTrue(evidence.effectiveGuardAllowed(local: ["first.app"]).isEmpty,
                      "a later lock cannot inherit exceptions from the earlier lock")
    }

    func testNoRememberedAuthorityLockPreservesLocalHeartbeatFallback() {
        var evidence = AuthorityLockEvidence()
        evidence.observe(locked: nil)
        XCTAssertNil(evidence.effectiveGuardCheckIns(live: nil))
        let stamp = Date(timeIntervalSince1970: 1_800_000_000)
        XCTAssertEqual(BrowserGuardPolicy.checkIn(browser: "custom.browser", processID: 42,
            authority: evidence.effectiveGuardCheckIns(live: nil), local: stamp), stamp)
    }

    func testLockedAuthorityOutageCannotTrustForgedLocalHeartbeat() {
        var evidence = AuthorityLockEvidence()
        evidence.observe(locked: true, guardAllowed: [])
        evidence.observe(locked: nil)
        let checkIns = evidence.effectiveGuardCheckIns(live: nil)
        XCTAssertEqual(checkIns, [:], "missing IPC is authoritative absence, not local fallback")
        let stamp = Date(timeIntervalSince1970: 1_800_000_000)
        XCTAssertNil(BrowserGuardPolicy.checkIn(browser: "custom.browser", processID: 42,
            authority: checkIns, local: stamp))
        XCTAssertEqual(BrowserGuardPolicy.verdict(coverage: .needsExtension, lastSeen: nil,
            now: stamp, graceStart: stamp, firstViolation: nil, recentlyClosed: false),
            .warn(.extensionSilent, closeAt: stamp.addingTimeInterval(BrowserGuardPolicy.warnSilent)),
            "authority loss keeps the ordinary warning, not an immediate shutdown")
    }

    func testLiveAuthorityHeartbeatIsNeverCachedAsFreshDuringOutage() {
        var evidence = AuthorityLockEvidence()
        evidence.observe(locked: true, guardAllowed: [])
        let stamp = Date(timeIntervalSince1970: 1_800_000_000)
        let live = ["browser#42": stamp]
        XCTAssertEqual(evidence.effectiveGuardCheckIns(live: live), live)
        XCTAssertEqual(evidence.effectiveGuardCheckIns(live: [:]), [:])
        XCTAssertEqual(evidence.effectiveGuardCheckIns(live: nil), [:],
                       "even a just-read positive heartbeat is not cached liveness")
    }

    func testConfirmedUnlockRestoresLocalHeartbeatFallback() {
        var evidence = AuthorityLockEvidence()
        evidence.observe(locked: true, guardAllowed: [])
        XCTAssertEqual(evidence.effectiveGuardCheckIns(live: nil), [:])
        evidence.observe(locked: false)
        XCTAssertNil(evidence.effectiveGuardCheckIns(live: nil))
        let live = ["browser#42": Date(timeIntervalSince1970: 1_800_000_000)]
        XCTAssertEqual(evidence.effectiveGuardCheckIns(live: live), live,
                       "live authority records take precedence even outside its lock")
    }
}
