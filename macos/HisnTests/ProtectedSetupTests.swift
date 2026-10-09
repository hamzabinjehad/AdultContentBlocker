import XCTest
@testable import Hisn

final class ProtectedSetupTests: XCTestCase {
    private let id = "hfhaffbmoeepcdolgejeidkgaoapcjig"

    private func evidence(filter: Bool = true, admin: Bool? = false,
                          files: Bool? = true, managed: Bool = true,
                          link: Bool = true, checked: Bool = true) -> SetupEvidence {
        SetupEvidence(isAdmin: admin, hostsEntries: 400_000, partnerKeySet: true,
                      browsers: [BrowserSetup(name: "Chrome", incognitoLocked: true,
                          guestLocked: true, dnsLocked: true, profilesChecked: checked,
                          extensionManaged: managed, nativeLinkProtected: link)],
                      privateRelayOff: true, screenTimeAdultFilter: true,
                      systemFilterRunning: filter, appFilesProtected: files)
    }

    private func setup(_ evidence: SetupEvidence, administrator: Bool = true,
                       recovery: Bool = true) -> ProtectedSetup {
        ProtectedSetup(checklist: SetupChecklist(evidence), administratorConfirmed: administrator,
                       recoveryConfirmed: recovery)
    }

    func testCompleteNeedsBothEvidenceAndHumanConfirmations() {
        let complete = setup(evidence())
        XCTAssertTrue(complete.isComplete)
        XCTAssertEqual(complete.doneCount, complete.totalCount)
        XCTAssertNil(complete.nextStage)
        XCTAssertNil(complete.nextStep)
        let confirmations = setup(evidence(), administrator: false, recovery: false)
        XCTAssertTrue(confirmations.deviceChecksPassed)
        XCTAssertFalse(confirmations.isComplete)
        XCTAssertEqual(confirmations.nextStage?.id, .recovery)
        XCTAssertEqual(confirmations.doneCount, confirmations.totalCount - 2)
    }

    func testHostsCannotSubstituteForSystemFilterInProtectedSetup() {
        let checklist = SetupChecklist(evidence(filter: false))
        XCTAssertTrue(checklist.isComplete, "legacy browser/hosts setup remains supported")
        let protected = ProtectedSetup(checklist: checklist, administratorConfirmed: true, recoveryConfirmed: true)
        XCTAssertFalse(protected.isComplete)
        XCTAssertEqual(protected.nextStep?.id, .systemFilter)
        XCTAssertEqual(protected.nextStage?.id, .installation)
    }

    func testUnreadableAndRemovableProtectionNeverPasses() {
        for e in [evidence(admin: nil), evidence(admin: true), evidence(files: nil), evidence(files: false),
                  evidence(managed: false), evidence(link: false), evidence(checked: false)] {
            XCTAssertFalse(setup(e).isComplete, "confirmations cannot override failed checks")
        }
    }

    func testAllChecklistStepsAppearExactlyOnceAndAccountChangeComesLast() {
        let value = setup(evidence())
        let ids = value.stages.flatMap(\.steps).map(\.id)
        XCTAssertEqual(Set(ids).count, ids.count)
        XCTAssertEqual(Set(ids), Set(SetupChecklist(evidence()).steps.map(\.id)))
        XCTAssertEqual(value.stages.map(\.id), [.installation, .browser, .recovery, .accounts])
        XCTAssertEqual(value.stages.last?.steps.map(\.id), [.accounts])
    }

    func testRegressionReopensACompletedSetup() {
        XCTAssertTrue(setup(evidence()).isComplete)
        XCTAssertEqual(setup(evidence(link: false)).nextStage?.id, .browser)
        XCTAssertEqual(setup(evidence(admin: true)).nextStage?.id, .accounts)
        XCTAssertEqual(setup(evidence(filter: false)).nextStage?.id, .installation)
    }

    func testOnlyExactForcedExtensionPoliciesCount() {
        func check(_ settings: Any?, _ list: Any? = nil) -> Bool {
            SetupEvidence.extensionIsManaged(ids: [id], settings: settings, forceList: list)
        }
        let forced = [id: ["installation_mode": "force_installed", "update_url": "https://clients2.google.com/service/update2/crx"]]
        XCTAssertTrue(check(forced))
        XCTAssertTrue(check(nil, [id + ";https://clients2.google.com/service/update2/crx"]))
        XCTAssertFalse(check(["*": ["installation_mode": "force_installed", "update_url": "https://example.org/"]]))
        XCTAssertFalse(check(["another-id": forced[id]!]))
        XCTAssertFalse(check([id: ["installation_mode": "normal_installed", "update_url": "https://example.org/"]]))
        XCTAssertFalse(check([id: ["installation_mode": "allowed"]], [id + ";https://example.org/"]))
        XCTAssertFalse(check([id: ["installation_mode": "force_installed", "update_url": "http://example.org/"]]))
        XCTAssertFalse(check(nil, [id + ";"]))
        XCTAssertFalse(check(nil, [id]))
        XCTAssertFalse(SetupEvidence.extensionIsManaged(ids: [], settings: forced, forceList: nil))
    }

    func testBrowserWithoutReadableProfilesIsNotVerified() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        XCTAssertFalse(SetupEvidence.inspectBrowserProfiles(in: root, ids: [id]).checked)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        XCTAssertFalse(SetupEvidence.inspectBrowserProfiles(in: root, ids: [id]).checked)
        let profile = root.appendingPathComponent("Default")
        try FileManager.default.createDirectory(at: profile, withIntermediateDirectories: true)
        try Data("not JSON".utf8).write(to: profile.appendingPathComponent("Preferences"))
        XCTAssertEqual(SetupEvidence.inspectBrowserProfiles(in: root, ids: [id]).missing, ["Default"])
        let incomplete: [String: Any] = ["extensions": ["settings": [id: [String: Any]()]]]
        try JSONSerialization.data(withJSONObject: incomplete).write(to: profile.appendingPathComponent("Preferences"))
        XCTAssertEqual(SetupEvidence.extensionMissing(in: root, ids: [id]), ["Default"])
    }

    func testManagedIDMustActuallyRunInEveryProfile() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let profile = root.appendingPathComponent("Default")
        try FileManager.default.createDirectory(at: profile, withIntermediateDirectories: true)
        let preferences = ["extensions": ["settings": [id: ["state": 1]]]]
        try JSONSerialization.data(withJSONObject: preferences).write(to: profile.appendingPathComponent("Preferences"))
        XCTAssertEqual(SetupEvidence.extensionMissing(in: root, ids: [id]), [])
        XCTAssertEqual(SetupEvidence.extensionMissing(in: root, ids: ["another-id"]), ["Default"])
    }

    func testBrowserMustReportAPolicyInstalledExtensionNotAnUnpackedCopy() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let profile = root.appendingPathComponent("Default")
        try FileManager.default.createDirectory(at: profile, withIntermediateDirectories: true)
        for location in [1, 4, 5, 7, 8, 9, 10] {
            let prefs = ["extensions": ["settings": [id: ["state": 1, "location": location]]]]
            try JSONSerialization.data(withJSONObject: prefs).write(to: profile.appendingPathComponent("Preferences"))
            XCTAssertEqual(SetupEvidence.inspectBrowserProfiles(in: root, ids: [id], requireManaged: true).missing,
                           [7, 9].contains(location) ? [] : ["Default"], "location \(location)")
        }
    }

    func testProtectedLinkRejectsWrongPathsAndUnexpectedOrigins() throws {
        var manifest: [String: Any] = ["name": "app.hisn.bridge", "type": "stdio",
            "path": "/Applications/Hisn.app/Contents/MacOS/HisnBridge",
            "allowed_origins": ["chrome-extension://\(id)/"]]
        func matches() throws -> Bool {
            SetupEvidence.bridgeManifestMatches(try JSONSerialization.data(withJSONObject: manifest), ids: [id])
        }
        XCTAssertTrue(try matches())
        manifest["path"] = "/Users/example/Downloads/HisnBridge"
        XCTAssertFalse(try matches())
        manifest["path"] = "/Applications/Hisn.app/Contents/MacOS/HisnBridge"
        manifest["allowed_origins"] = ["*"]
        XCTAssertFalse(try matches())
        manifest["allowed_origins"] = []
        XCTAssertFalse(try matches())
        XCTAssertFalse(SetupEvidence.bridgeManifestMatches(Data("{}".utf8), ids: [id]))
    }
}

/// Functional setup is computed from this session's evidence, independently
/// of the extra administrator/accountability hardening checklist.
final class LaptopSetupReadinessTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    private func protection(filter: FilterEvidence = .on(domainCount: 150_000),
                            signed: Bool = true, recovery: Bool = false,
                            persistenceError: String? = nil,
                            heartbeatAge: TimeInterval? = 30,
                            hosts: Int? = nil) -> ProtectionEvidence {
        ProtectionEvidence(filter: filter,
            extensionLastSeen: heartbeatAge.map { now.addingTimeInterval(-$0) }, now: now,
            hostsEntries: hosts, filterCanRun: signed,
            policyRecoveryRequired: recovery, policyPersistenceError: persistenceError)
    }

    private func checklist(browsers: [BrowserSetup] = [BrowserSetup(name: "Chrome")],
                           files: Bool? = true,
                           claimsRunningFilter: Bool = true) -> SetupChecklist {
        // No partner/admin separation, Screen Time or managed-profile assertions
        // are fabricated here: those are separate hardening, not basic liveness.
        SetupChecklist(SetupEvidence(isAdmin: true, hostsEntries: nil,
            partnerKeySet: false, browsers: browsers, privateRelayOff: false,
            screenTimeAdultFilter: false, systemFilterRunning: claimsRunningFilter,
            appFilesProtected: files))
    }

    private func readiness(_ evidence: ProtectionEvidence,
                           checklist: SetupChecklist? = nil,
                           alwaysOn: Bool = true,
                           warning: Bool = false,
                           exceptions: Bool = false) -> LaptopSetupReadiness {
        LaptopSetupReadiness(protection: evidence, checklist: checklist ?? self.checklist(),
            requireOutsideLock: alwaysOn, browserWarning: warning,
            hasAppExceptions: exceptions)
    }

    func testLiveFunctionalChecksDoNotRequireExternalControlConfirmations() {
        let checked = checklist()
        XCTAssertFalse(checked.isComplete)
        XCTAssertFalse(ProtectedSetup(checklist: checked, administratorConfirmed: false,
            recoveryConfirmed: false).isComplete)
        let value = readiness(protection(), checklist: checked)
        XCTAssertTrue(value.isReady)
        XCTAssertFalse(value.isChecking)
        XCTAssertTrue(value.issues.isEmpty)
    }

    func testHostsAndAFreshConnectionNeverSubstituteForTheSystemFilter() {
        for signed in [false, true] {
            let evidence = protection(filter: .off, signed: signed, hosts: 400_000)
            XCTAssertTrue(ProtectionStatus(evidence).isEnforcingAnything)
            let value = readiness(evidence)
            XCTAssertFalse(value.isReady)
            XCTAssertTrue(value.issues.contains(signed ? .filter : .signedBuild))
        }
    }

    func testLiveFilterOverridesAStaleSuccessfulChecklist() {
        let cached = checklist(claimsRunningFilter: true)
        XCTAssertEqual(cached.steps.first { $0.id == .systemFilter }?.state, .done)
        for filter in [FilterEvidence.off, .silent, .unavailable("unreachable"),
                       .on(domainCount: 0), .on(domainCount: -1)] {
            let value = readiness(protection(filter: filter), checklist: cached)
            XCTAssertFalse(value.isReady, "live state \(filter) overrides a saved snapshot")
            XCTAssertTrue(value.issues.contains(.filter))
        }
    }

    func testMissingChecklistWaitsForFreshEvidenceEvenWithHealthyProcesses() {
        let value = LaptopSetupReadiness(protection: protection(), checklist: nil,
            requireOutsideLock: true)
        XCTAssertTrue(value.isChecking)
        XCTAssertFalse(value.isReady)
    }

    func testUnknownSignedFilterStaysChecking() {
        let value = readiness(protection(filter: .unknown))
        XCTAssertTrue(value.isChecking)
        XCTAssertFalse(value.isReady)
        XCTAssertTrue(value.issues.contains(.filter))
    }

    func testUnsignedBuildCannotBorrowHealthyLookingFilterEvidence() {
        let evidence = protection(signed: false)
        XCTAssertFalse(LaptopSetupReadiness.filterIsReady(evidence))
        XCTAssertTrue(readiness(evidence).issues.contains(.signedBuild))
        XCTAssertFalse(readiness(evidence).isReady)
    }

    func testPolicyRecoveryNeverCertifiesSetupEvenWhenTrafficIsRestricted() {
        for count in [0, 150_000] {
            let evidence = protection(filter: .on(domainCount: count), recovery: true)
            XCTAssertTrue(ProtectionStatus(evidence).layers.first?.ok == true,
                          "policy recovery can still restrict traffic")
            XCTAssertFalse(LaptopSetupReadiness.filterIsReady(evidence))
            let value = readiness(evidence)
            XCTAssertFalse(value.isReady)
            XCTAssertTrue(value.issues.contains(.filter))
            XCTAssertTrue(value.issues.contains(.policy))
        }
    }

    func testFailedPolicyPersistenceCannotCertifySetup() {
        let evidence = protection(persistenceError: "disk full")
        XCTAssertFalse(LaptopSetupReadiness.filterIsReady(evidence))
        let value = readiness(evidence)
        XCTAssertFalse(value.isReady)
        XCTAssertTrue(value.issues.contains(.filter))
        XCTAssertTrue(value.issues.contains(.policy))
    }

    func testMissingUnreadableOrUnprotectedProfilesPreventReadiness() {
        let cases: [[BrowserSetup]] = [
            [],
            [BrowserSetup(name: "Chrome", profilesChecked: false)],
            [BrowserSetup(name: "Chrome", extensionOffIn: ["Personal"])],
            [BrowserSetup(name: "Chrome"),
             BrowserSetup(name: "Edge", extensionOffIn: ["Other account"])],
            [BrowserSetup(name: "Chrome"),
             BrowserSetup(name: "Edge", profilesChecked: false)],
        ]
        for browsers in cases {
            let value = readiness(protection(), checklist: checklist(browsers: browsers))
            XCTAssertFalse(value.isReady)
            XCTAssertTrue(value.issues.contains(.browserProfiles))
        }
    }

    func testMissingAndWritableInstallationFilesPreventReadiness() {
        for files in [Optional<Bool>.none, .some(false)] {
            let value = readiness(protection(), checklist: checklist(files: files))
            XCTAssertFalse(value.isReady)
            XCTAssertTrue(value.issues.contains(.installation))
        }
    }

    func testFreshGlobalHeartbeatCannotOverrideAMissingProfile() {
        let checked = checklist(browsers: [BrowserSetup(name: "Chrome"),
            BrowserSetup(name: "Edge", extensionOffIn: ["Default"])])
        let value = readiness(protection(heartbeatAge: 0), checklist: checked)
        XCTAssertFalse(value.issues.contains(.browserConnection))
        XCTAssertTrue(value.issues.contains(.browserProfiles))
        XCTAssertFalse(value.isReady)
    }

    func testBrowserConnectionUsesTheGuardStalenessBoundaryAndRejectsFutureTime() {
        XCTAssertTrue(readiness(protection(heartbeatAge: 89.999)).isReady)
        for age in [Optional<TimeInterval>.none, .some(90), .some(91), .some(-1)] {
            let value = readiness(protection(heartbeatAge: age))
            XCTAssertFalse(value.isReady)
            XCTAssertTrue(value.issues.contains(.browserConnection))
        }
    }

    func testSessionOnlyGuardCannotClaimOutsideCommitmentReadiness() {
        let value = readiness(protection(), alwaysOn: false)
        XCTAssertFalse(value.isReady)
        XCTAssertTrue(value.issues.contains(.browserGuard))
    }

    func testBrowserWarningAndAppExceptionsMustBeResolved() {
        let warning = readiness(protection(), warning: true)
        XCTAssertFalse(warning.isReady)
        XCTAssertTrue(warning.issues.contains(.browserWarning))
        let exceptions = readiness(protection(), exceptions: true)
        XCTAssertFalse(exceptions.isReady)
        XCTAssertTrue(exceptions.issues.contains(.appExceptions))
    }

    func testReadinessRegressionAndRecoveryComeFromCurrentInputsOnly() {
        XCTAssertTrue(readiness(protection()).isReady)
        XCTAssertFalse(readiness(protection(filter: .silent)).isReady)
        XCTAssertFalse(readiness(protection(), warning: true).isReady)
        XCTAssertTrue(readiness(protection()).isReady)
    }

    func testFilterReadyRequiresPositiveDomainsAndHealthyLiveEvidence() {
        XCTAssertTrue(LaptopSetupReadiness.filterIsReady(protection(filter: .on(domainCount: 1))))
        for filter in [FilterEvidence.unknown, .off, .silent, .unavailable("unavailable"),
                       .on(domainCount: 0), .on(domainCount: -1)] {
            XCTAssertFalse(LaptopSetupReadiness.filterIsReady(protection(filter: filter)))
        }
    }
}
