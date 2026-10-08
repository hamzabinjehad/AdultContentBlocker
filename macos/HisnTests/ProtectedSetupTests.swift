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
