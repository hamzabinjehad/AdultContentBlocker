import XCTest
@testable import Hisn

/// The status the Overview leads with, pinned state by state.
///
/// The honest-status promise turns on these: a wrong answer either alarms a
/// correctly-set-up user or reassures one whose protection enforces nothing.
/// Above all, evidence we do not have must never read as protection we do.
final class ProtectionStatusTests: XCTestCase {

    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    private func status(filter: FilterEvidence,
                        extensionSeen: TimeInterval? = nil) -> ProtectionStatus {
        ProtectionStatus(ProtectionEvidence(
            filter: filter,
            extensionLastSeen: extensionSeen.map { now.addingTimeInterval(-$0) },
            now: now))
    }

    // MARK: Levels

    func testFreshMachineNeedsSetup() {
        let s = status(filter: .off)
        XCTAssertEqual(s.level, .setupNeeded)
        XCTAssertEqual(s.headline, "Setup needed")
        XCTAssertFalse(s.isEnforcingAnything)
        // Both rows are the calm "never set up" kind, each with a next step.
        XCTAssertEqual(s.layers.map(\.state), [.missing, .missing])
        XCTAssertEqual(s.layers.map(\.action), [.enableFilter, .installExtension])
    }

    func testBothLayersRunningIsActive() {
        let s = status(filter: .on(domainCount: 150_000), extensionSeen: 30)
        XCTAssertEqual(s.level, .active)
        XCTAssertEqual(s.headline, "Protection active")
        XCTAssertTrue(s.isEnforcingAnything)
        XCTAssertTrue(s.layers.allSatisfy(\.ok))
        XCTAssertTrue(s.layers.allSatisfy { $0.action == nil })
    }

    func testOneLayerRunningIsPartial() {
        let s = status(filter: .on(domainCount: 150_000))
        XCTAssertEqual(s.level, .partial)
        XCTAssertEqual(s.headline, "Partially active")
        XCTAssertTrue(s.isEnforcingAnything)
        // The summary names what is missing, in words, not a count.
        XCTAssertTrue(s.summary.localizedCaseInsensitiveContains("browser extension"), s.summary)
        XCTAssertTrue(s.summary.localizedCaseInsensitiveContains("not set up"), s.summary)
    }

    // MARK: A missing answer is never a positive status

    func testUnansweredFilterQueryIsChecking() {
        let s = status(filter: .unknown, extensionSeen: 30)
        XCTAssertEqual(s.level, .checking)
        XCTAssertEqual(s.headline, "Checking status")
        // Not "partial", not "active": we do not know yet, and the extension
        // being fine does not answer the filter question.
        XCTAssertEqual(s.layers[0].state, .checking)
        XCTAssertNil(s.layers[0].action)
    }

    func testFailedFilterQueryIsAProblemWithARetry() {
        let s = status(filter: .unavailable("boom"), extensionSeen: 30)
        XCTAssertEqual(s.level, .partial)
        XCTAssertEqual(s.layers[0].state, .problem)
        XCTAssertEqual(s.layers[0].action, .retryFilterCheck)
        XCTAssertTrue(s.layers[0].detail.contains("boom"))
    }

    func testFilterWithNoListIsNotEnforcing() {
        // Enabled but empty is the "looks on, blocks nothing" state.
        let s = status(filter: .on(domainCount: 0))
        XCTAssertEqual(s.level, .setupNeeded)
        XCTAssertFalse(s.isEnforcingAnything)
        XCTAssertEqual(s.layers[0].state, .problem)
        XCTAssertEqual(s.layers[0].action, .updateList)
        // Something was set up and broke, so the summary says "fix", not
        // "finish".
        XCTAssertTrue(s.summary.contains("Fix"))
    }

    func testExtensionLivenessIsTimeBounded() {
        XCTAssertTrue(status(filter: .off, extensionSeen: 4 * 60).layers[1].ok)

        let stale = status(filter: .off, extensionSeen: 10 * 60).layers[1]
        XCTAssertFalse(stale.ok)
        XCTAssertEqual(stale.state, .problem)
        XCTAssertEqual(stale.action, .reconnectExtension)
        XCTAssertTrue(stale.detail.hasPrefix("Not responding"))
    }

    // MARK: Without the paid filter

    func testHostsFileAndExtensionAreProtectionWithoutTheFilter() {
        let s = ProtectionStatus(ProtectionEvidence(
            filter: .off, extensionLastSeen: now.addingTimeInterval(-30), now: now,
            hostsEntries: 358_239, filterCanRun: false))
        XCTAssertEqual(s.level, .active)
        XCTAssertEqual(s.layers.map(\.name),
                       ["System filter", "Hosts-file blocklist", "Browser extension"])
        // The filter row says why, and offers no button that can only fail.
        XCTAssertEqual(s.layers[0].state, .missing)
        XCTAssertNil(s.layers[0].action)
        XCTAssertTrue(s.layers[0].detail.contains("Apple Developer Program"))
        XCTAssertTrue(s.summary.contains("hosts-file"))
    }

    func testAHandfulOfHostsLinesIsNotABlocklist() {
        let s = ProtectionStatus(ProtectionEvidence(
            filter: .off, extensionLastSeen: now.addingTimeInterval(-30), now: now,
            hostsEntries: 12, filterCanRun: false))
        XCTAssertEqual(s.level, .partial)
        XCTAssertEqual(s.layers[1].state, .missing)
    }

    func testHostsWithoutTheExtensionIsPartial() {
        let s = ProtectionStatus(ProtectionEvidence(
            filter: .off, extensionLastSeen: nil, now: now,
            hostsEntries: 200_000, filterCanRun: false))
        XCTAssertEqual(s.level, .partial)
    }

    func testRunningFilterDoesNotNeedTheHostsFile() {
        let s = ProtectionStatus(ProtectionEvidence(
            filter: .on(domainCount: 900_000), extensionLastSeen: now.addingTimeInterval(-30),
            now: now, hostsEntries: 0))
        XCTAssertEqual(s.level, .active)
    }

    func testHostsCounting() {
        let text = "127.0.0.1 localhost\n0.0.0.0 a.example\n0.0.0.0\tb.example\n"
            + "# 0.0.0.0 commented.example\n  0.0.0.0 indented.example\n0.0.0.0.evil\n"
        XCTAssertEqual(HostsFile.count(in: Data(text.utf8)), 2)
        XCTAssertEqual(HostsFile.count(in: Data("0.0.0.0 first.example".utf8)), 1)
    }

    // MARK: Evidence is read from the shared container

    func testCurrentEvidenceReadsTheContainer() {
        let namespace = TestNamespace.make()
        let saved = LockStore.appGroup
        LockStore.appGroup = namespace
        defer {
            LockStore.appGroup = saved
            TestNamespace.dispose(namespace)
        }
        let d = UserDefaults(suiteName: namespace)!

        XCTAssertEqual(ProtectionEvidence.current(filter: .unknown).filter, .unknown)
        XCTAssertEqual(ProtectionEvidence.current(filter: .on).filter, .on(domainCount: 0))

        d.set(150_000, forKey: "filterDomainCount")
        d.set(now, forKey: "extensionLastSeen")
        let e = ProtectionEvidence.current(filter: .on)
        XCTAssertEqual(e.filter, .on(domainCount: 150_000))
        XCTAssertEqual(e.extensionLastSeen, now)
    }
}

/// The lock line, described on its own terms.
final class LockStatusTests: XCTestCase {

    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    func testNoLock() {
        let s = LockStatus(state: .unlocked, now: now, pendingRelease: nil)
        XCTAssertFalse(s.isLocked)
        XCTAssertEqual(s.headline, "No active lock")
    }

    func testExpiredDeadlineIsNoLock() {
        let state = LockStore.LockState(deadline: now.addingTimeInterval(-1),
                                        mode: "blocklist", startedAt: now)
        XCTAssertFalse(LockStatus(state: state, now: now, pendingRelease: nil).isLocked)
    }

    func testActiveLockNamesTheDateAndMode() {
        let deadline = now.addingTimeInterval(7 * 86400)
        let state = LockStore.LockState(deadline: deadline, mode: "blocklist", startedAt: now)
        let s = LockStatus(state: state, now: now, pendingRelease: nil)
        XCTAssertTrue(s.isLocked)
        XCTAssertTrue(s.headline.hasPrefix("Locked until "))
        XCTAssertTrue(s.headline.contains(deadline.formatted(date: .long, time: .shortened)))
        XCTAssertEqual(s.detail, "Standard mode")

        let strict = LockStore.LockState(deadline: deadline, mode: "strict", startedAt: now)
        XCTAssertEqual(LockStatus(state: strict, now: now, pendingRelease: nil).detail,
                       "Strict mode")
    }

    func testPendingReleaseIsShown() {
        let deadline = now.addingTimeInterval(7 * 86400)
        let release = now.addingTimeInterval(2 * 86400)
        let state = LockStore.LockState(deadline: deadline, mode: "blocklist",
                                        startedAt: now, selfReleaseAt: release)
        let s = LockStatus(state: state, now: now, pendingRelease: release)
        XCTAssertTrue(s.detail?.contains("early release arrives") ?? false)
    }
}
