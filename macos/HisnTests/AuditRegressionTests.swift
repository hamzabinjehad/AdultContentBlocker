import XCTest
@testable import Hisn

/// One test per finding of the macOS audit (2026-09-25) — each a way a lock
/// could be ended, loosened or left unenforced that is now closed.
final class AuditRegressionTests: XCTestCase {

    private let now = Date(timeIntervalSince1970: 1_800_000_000)
    private let day: TimeInterval = 86_400

    private func lock(days: Double, nonce: String = "nonceA", mode: String = "blocklist",
                      release: Date? = nil, started: Date? = nil) -> LockStore.LockState {
        LockStore.LockState(deadline: now.addingTimeInterval(days * day), mode: mode,
                            startedAt: started ?? now, selfReleaseAt: release, releaseNonce: nonce)
    }

    private func view(_ l: LockStore.LockState?, locked: Bool? = nil, allows: [String] = [],
                      guardAllowed: [String] = [], effective: Date? = nil) -> PolicyView {
        PolicyView(lock: l, effectiveDeadline: effective ?? l?.deadline,
                   locked: locked ?? (l != nil),
                   allowlist: allows, customBlocks: [], customTerms: [], blockedApps: [],
                   inspection: .default, guardAllowed: guardAllowed)
    }

    // 1 & 2 — the app's clock is forged, its copy reads "unlocked"; the
    // authority, judged by its own clock, still holds the lock.
    func testAForgedAppClockCannotUnlockThroughTheMerge() {
        let appSaysOver = view(nil, locked: false)
        let authority = view(lock(days: 5, mode: "strict"))
        let merged = PolicyMerge.stricter(editor: appSaysOver, other: authority)
        XCTAssertTrue(merged.locked)
        let reply = merged.bridgeReply(listVersion: 1)
        XCTAssertEqual(reply["mode"] as? String, "strict", "the browser stays locked")
        XCTAssertEqual(reply["lockUntil"] as? Double,
                       now.addingTimeInterval(5 * day).timeIntervalSince1970 * 1000)
    }

    // 4 — an early-release request made in the app reaches the authority
    // instead of being cancelled by the sync.
    func testAReleaseRequestSurvivesTheMerge() {
        let asked = now.addingTimeInterval(2 * day)
        let app = view(lock(days: 30, release: asked), effective: asked)
        let authority = view(lock(days: 30))
        let merged = PolicyMerge.stricter(editor: app, other: authority)
        XCTAssertEqual(merged.lock?.selfReleaseAt, asked, "the request is passed on")
        XCTAssertEqual(merged.effectiveDeadline, now.addingTimeInterval(30 * day),
                       "and ends nothing early until the authority has seen it")
    }

    func testCancellingARequestAlsoTravelsFromTheApp() {
        let asked = now.addingTimeInterval(2 * day)
        let app = view(lock(days: 30))
        let authority = view(lock(days: 30, release: asked), effective: now.addingTimeInterval(3 * day))
        XCTAssertNil(PolicyMerge.stricter(editor: app, other: authority).lock?.selfReleaseAt)
    }

    // 7 — an authority that has not heard of the lock yet does not wipe the
    // allowlist by intersection.
    func testAFreshAuthorityDoesNotWipeTheAllowlist() {
        let app = view(lock(days: 7, mode: "strict"), allows: ["work.example", "bank.example"])
        let authority = view(nil, locked: false, allows: [])
        XCTAssertEqual(PolicyMerge.stricter(editor: app, other: authority).allowlist,
                       ["work.example", "bank.example"])
    }

    func testTwoLockedCopiesStillIntersect() {
        let app = view(lock(days: 7), allows: ["a.example", "forged.example"])
        let authority = view(lock(days: 7), allows: ["a.example"])
        XCTAssertEqual(PolicyMerge.stricter(editor: app, other: authority).allowlist, ["a.example"])
    }

    // 8 — mirrors replaced by a different (later) lock: the copies converge on
    // the authority's identity with the later deadline.
    func testTheCopiesConvergeOnOneLock() {
        let appLock = lock(days: 40, nonce: "nonceB", started: now.addingTimeInterval(3600))
        let merged = PolicyMerge.stricter(editor: view(appLock), other: view(lock(days: 10)))
        XCTAssertEqual(merged.lock?.releaseNonce, "nonceA", "the authority names the lock")
        XCTAssertEqual(merged.lock?.startedAt, now)
        XCTAssertEqual(merged.lock?.deadline, now.addingTimeInterval(40 * day), "the later deadline")
    }

    func testAdoptTakesTheAuthoritysLockButNeverShortens() {
        let current = lock(days: 10, nonce: "nonceB")
        XCTAssertNotNil(LockStore.refusal(current: current, proposed: lock(days: 12), now: now),
                        "an ordinary write may not change the lock's identity")
        XCTAssertNil(LockStore.refusal(current: current, proposed: lock(days: 12), now: now,
                                       adoptingAuthority: true))
        XCTAssertNotNil(LockStore.refusal(current: current, proposed: lock(days: 5), now: now,
                                          adoptingAuthority: true),
                        "adopting never shortens")
    }

    // 11 — a lock from before nonces cannot be given an earlier lock's identity.
    func testARunningLocksIdentityCannotChange() {
        var legacy = lock(days: 10)
        legacy.releaseNonce = nil
        XCTAssertNotNil(LockStore.refusal(current: legacy, proposed: lock(days: 10), now: now),
                        "no nonce may be added to a running lock")
        XCTAssertNotNil(LockStore.refusal(current: lock(days: 10),
                                          proposed: lock(days: 11, started: now.addingTimeInterval(-day)),
                                          now: now),
                        "the start time of a running lock is fixed")
    }

    // 5 — the guard's allowances live in the authority too, add-only unlocked.
    func testGuardAllowancesOnlyGrowWhileUnlocked() {
        var r = PolicyRecord()
        guard case let .success(allowed) = PolicyAuthority.apply(.setGuardAllowed(["com.openai.codex"]),
                                                                 to: r, wall: now),
              case let .success(locked) = PolicyAuthority.apply(.proposeLock(lock(days: 3)),
                                                                to: allowed, wall: now)
        else { return XCTFail("setup refused") }
        r = locked
        guard case .failure = PolicyAuthority.apply(
            .setGuardAllowed(["com.openai.codex", "org.torproject.torbrowser"]), to: r, wall: now) else {
            return XCTFail("Tor Browser was allowed mid-lock")
        }
        guard case .success = PolicyAuthority.apply(.setGuardAllowed([]), to: r, wall: now) else {
            return XCTFail("withdrawing an allowance must always work")
        }
        let merged = PolicyMerge.stricter(
            editor: view(lock(days: 3), guardAllowed: ["com.openai.codex", "org.torproject.torbrowser"]),
            other: view(lock(days: 3), guardAllowed: ["com.openai.codex"]))
        XCTAssertEqual(merged.guardAllowed, ["com.openai.codex"], "a forged local allowance is dropped")
    }

    // 5 — check-ins reach the authority, are reported, and are not policy.
    func testCheckInsAreHeldInMemoryOnly() throws {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("hisn-audit-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: dir) }
        let service = PolicyService(store: PolicyStore(directory: dir), clock: { self.now })
        let reply = service.handle(.checkIn(browser: "net.imput.helium"))
        XCTAssertTrue(reply.accepted)
        XCTAssertEqual(service.status().checkIns?["net.imput.helium"], now)
        XCTAssertEqual(service.current().revision, 0, "a check-in is not a change of policy")
        XCTAssertFalse(FileManager.default.fileExists(atPath: dir.appendingPathComponent("policy.b.json").path)
                       && FileManager.default.fileExists(atPath: dir.appendingPathComponent("policy.a.json").path))
    }

    // 10 — a record written before a field existed still decodes.
    func testARecordFromAnOlderBuildStillDecodes() throws {
        let old = #"{"lock":{"deadline":800000000,"mode":"strict","startedAt":700000000},"allowlist":["a.example"],"revision":3,"inspection":{"text":true}}"#
        let r = try JSONDecoder().decode(PolicyRecord.self, from: Data(old.utf8))
        XCTAssertEqual(r.lock?.mode, "strict", "the running lock survives the upgrade")
        XCTAssertEqual(r.allowlist, ["a.example"])
        XCTAssertEqual(r.inspection.textSensitivity, 50, "a missing setting takes its default")
        XCTAssertEqual(r.guardAllowed, [])
        XCTAssertEqual(r.revision, 3)
    }

    func testUnreadableCopiesAreSetAsideNotOverwritten() throws {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("hisn-audit-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: dir) }
        let store = PolicyStore(directory: dir)
        try Data("not json".utf8).write(to: dir.appendingPathComponent("policy.a.json"))
        XCTAssertEqual(store.load(), PolicyRecord())
        let names = try FileManager.default.contentsOfDirectory(atPath: dir.path)
        XCTAssertTrue(names.contains { $0.hasPrefix("policy.a.json.unreadable-") },
                      "the unreadable copy is kept for a person to look at")
    }

    // 15 — a filter that is switched on but does not answer is not "Running".
    func testASilentFilterIsAProblem() {
        let s = ProtectionStatus(ProtectionEvidence(filter: .silent, extensionLastSeen: now,
                                                    now: now))
        XCTAssertEqual(s.layers[0].state, .problem)
        XCTAssertEqual(s.layers[0].action, .retryFilterCheck)
    }

    // 6 — a browser is recognised by its engine, not only by its Info.plist.
    func testBrowserEnginesAreRecognisedButElectronIsNot() throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("hisn-engines-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: root) }
        func app(_ name: String, _ paths: [String]) throws -> URL {
            let url = root.appendingPathComponent("\(name).app")
            for p in paths {
                try FileManager.default.createDirectory(
                    at: url.appendingPathComponent(p), withIntermediateDirectories: true)
            }
            return url
        }
        let gecko = try app("Renamed", ["Contents/MacOS/XUL"])
        let chromium = try app("Stripped", [
            "Contents/Frameworks/Stripped Framework.framework/Versions/Current/Helpers/Stripped Helper (Renderer).app"])
        let electron = try app("Chat", [
            "Contents/Frameworks/Electron Framework.framework/Versions/Current/Helpers/Chat Helper (Renderer).app"])
        let plain = try app("Notes", ["Contents/MacOS"])
        XCTAssertTrue(BrowserGuardPolicy.bundlesBrowserEngine(at: gecko))
        XCTAssertTrue(BrowserGuardPolicy.bundlesBrowserEngine(at: chromium))
        XCTAssertFalse(BrowserGuardPolicy.bundlesBrowserEngine(at: electron), "Electron apps are not browsers")
        XCTAssertFalse(BrowserGuardPolicy.bundlesBrowserEngine(at: plain))
    }
}
