import XCTest
@testable import Hisn

/// The setup checklist, from evidence alone — and the one part of reading the
/// Mac worth pinning: which browser profiles run the extension.
final class SetupChecklistTests: XCTestCase {

    private func evidence(admin: Bool? = true, hosts: Int? = nil, partner: Bool = false,
                          browsers: [BrowserSetup] = [BrowserSetup(name: "Helium")],
                          relayOff: Bool = false, screenTime: Bool = false,
                          filter: Bool = false) -> SetupEvidence {
        SetupEvidence(isAdmin: admin, hostsEntries: hosts, partnerKeySet: partner,
                      browsers: browsers, privateRelayOff: relayOff,
                      screenTimeAdultFilter: screenTime, systemFilterRunning: filter)
    }

    private let lockedHelium = BrowserSetup(name: "Helium", incognitoLocked: true,
                                            guestLocked: true, dnsLocked: true)

    private func step(_ c: SetupChecklist, _ id: SetupChecklist.Step.ID) -> SetupChecklist.Step {
        c.steps.first { $0.id == id }!
    }

    func testTheStepsComeInSetupOrderWithTheAccountSplitLast() {
        let c = SetupChecklist(evidence())
        XCTAssertEqual(c.steps.map(\.id),
                       [.browsers, .domains, .partner, .profile, .screenTime, .accounts, .systemFilter])
    }

    func testAFreshMacHasEverythingLeftAndSaysWhatToDo() {
        let c = SetupChecklist(evidence(browsers: [BrowserSetup(name: "Helium", extensionOffIn: ["Default"])]))
        XCTAssertEqual(c.doneCount, 0)
        XCTAssertFalse(c.isComplete)
        XCTAssertEqual(step(c, .browsers).action, .browserHelp)
        XCTAssertEqual(step(c, .domains).action, .command("macos/install.sh --hosts"))
        XCTAssertEqual(step(c, .partner).action, .partnerSettings)
        XCTAssertEqual(step(c, .profile).action, .command("macos/install.sh --profile"))
        XCTAssertEqual(step(c, .screenTime).action, .screenTimeSettings)
        XCTAssertEqual(step(c, .accounts).action, .command("macos/setup_guardian.sh --check"))
        XCTAssertTrue(step(c, .browsers).detail.contains("Helium (Default)"), step(c, .browsers).detail)
    }

    func testEverythingDoneIsCompleteWithoutThePaidFilter() {
        let c = SetupChecklist(evidence(admin: false, hosts: 358_239, partner: true,
                                        browsers: [lockedHelium], relayOff: true, screenTime: true))
        XCTAssertTrue(c.isComplete, c.steps.filter { $0.state == .todo }.map(\.title).joined(separator: ", "))
        XCTAssertEqual(step(c, .systemFilter).state, .optional,
                       "the $99 filter is worth having, not required to finish setup")
        XCTAssertEqual(c.required.count, 6)
    }

    func testTheSystemFilterAloneCoversDomains() {
        let c = SetupChecklist(evidence(hosts: 0, filter: true))
        XCTAssertEqual(step(c, .domains).state, .done)
        XCTAssertEqual(step(c, .systemFilter).state, .done)
    }

    func testAFewHandWrittenHostsLinesAreNotABlocklist() {
        XCTAssertEqual(step(SetupChecklist(evidence(hosts: 12)), .domains).state, .todo)
    }

    func testNoChromiumBrowserMeansNothingToInstall() {
        let c = SetupChecklist(evidence(browsers: []))
        XCTAssertEqual(step(c, .browsers).state, .done)
    }

    /// The profile step names what is still open, browser by browser.
    func testTheProfileStepNamesWhatIsOpen() {
        let half = BrowserSetup(name: "Chrome", incognitoLocked: true, guestLocked: false, dnsLocked: true)
        let c = SetupChecklist(evidence(browsers: [lockedHelium, half], relayOff: true))
        let s = step(c, .profile)
        XCTAssertEqual(s.state, .todo)
        XCTAssertTrue(s.detail.contains("guest windows in Chrome"), s.detail)
        XCTAssertFalse(s.detail.contains("Helium"), s.detail)
        XCTAssertFalse(s.detail.contains("Private Relay"), s.detail)
    }

    func testAnUnreadableAdminGroupIsNotCountedAsDone() {
        XCTAssertEqual(step(SetupChecklist(evidence(admin: nil)), .accounts).state, .todo)
        XCTAssertEqual(step(SetupChecklist(evidence(admin: false)), .accounts).state, .done)
    }

    func testEdgeKeepsItsPolicyUnderItsOwnDomain() {
        XCTAssertEqual(SetupEvidence.policyDomain("com.microsoft.edgemac"), "com.microsoft.Edge")
        XCTAssertEqual(SetupEvidence.policyDomain("net.imput.helium"), "net.imput.helium")
    }

    // MARK: - Browser profiles

    /// Every profile must run the extension: a second profile without it is
    /// the whole browser without it.
    func testEveryProfileWithoutTheExtensionIsNamed() throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("hisn-profiles-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: root) }
        let id = "hfhaffbmoeepcdolgejeidkgaoapcjig"
        func profile(_ folder: String, _ prefs: [String: Any], secure: [String: Any]? = nil) throws {
            let dir = root.appendingPathComponent(folder)
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            try JSONSerialization.data(withJSONObject: prefs)
                .write(to: dir.appendingPathComponent("Preferences"))
            if let secure {
                try JSONSerialization.data(withJSONObject: secure)
                    .write(to: dir.appendingPathComponent("Secure Preferences"))
            }
        }
        try profile("Default", ["profile": ["name": "Work"],
                                "extensions": ["settings": [id: ["state": 1]]]])
        try profile("Profile 1", ["profile": ["name": "Side door"]])
        try profile("Profile 2", ["profile": ["name": "Disabled"],
                                  "extensions": ["settings": [id: ["state": 1, "disable_reasons": [1]]]]])
        // Chrome keeps extension state in Secure Preferences.
        try profile("Profile 3", ["profile": ["name": "Secure"]],
                    secure: ["extensions": ["settings": [id: ["state": 1]]]])
        try profile("Guest Profile", ["profile": ["name": "Guest"]])   // not a user profile
        XCTAssertEqual(SetupEvidence.extensionMissing(in: root, ids: [id]), ["Side door", "Disabled"])
    }

    func testABrowserNeverOpenedHasNoProfilesToCheck() {
        let nowhere = FileManager.default.temporaryDirectory
            .appendingPathComponent("hisn-absent-\(UUID().uuidString)")
        XCTAssertEqual(SetupEvidence.extensionMissing(in: nowhere, ids: ["x"]), [])
    }
}
