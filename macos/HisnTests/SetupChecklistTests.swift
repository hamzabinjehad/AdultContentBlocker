import XCTest
@testable import Hisn

/// The setup checklist, from evidence alone — and the one part of reading the
/// Mac worth pinning: which browser profiles run the extension.
final class SetupChecklistTests: XCTestCase {

    private static let noSafeSearch = SafeSearchDNS(missing: ["Google", "YouTube", "Bing", "DuckDuckGo"])

    private func evidence(admin: Bool? = true, hosts: Int? = nil, bypassesBlocked: Bool = true,
                          safe: SafeSearchDNS = noSafeSearch, partner: Bool = false,
                          browsers: [BrowserSetup] = [BrowserSetup(name: "Helium")],
                          relayOff: Bool = false, screenTime: Bool = false,
                          filter: Bool = false, appFiles: Bool? = false) -> SetupEvidence {
        SetupEvidence(isAdmin: admin, hostsEntries: hosts, dnsBypassesBlocked: bypassesBlocked,
                      safeSearch: safe, partnerKeySet: partner,
                      browsers: browsers, privateRelayOff: relayOff,
                      screenTimeAdultFilter: screenTime, systemFilterRunning: filter,
                      appFilesProtected: appFiles)
    }

    private let lockedHelium = BrowserSetup(name: "Helium", incognitoLocked: true,
                                            guestLocked: true, dnsLocked: true)

    private func step(_ c: SetupChecklist, _ id: SetupChecklist.Step.ID) -> SetupChecklist.Step {
        c.steps.first { $0.id == id }!
    }

    func testTheStepsComeInSetupOrderWithTheAccountSplitLast() {
        let c = SetupChecklist(evidence())
        XCTAssertEqual(c.steps.map(\.id),
                       [.browsers, .domains, .safeSearch, .partner, .profile, .screenTime, .appFiles,
                        .accounts, .systemFilter])
    }

    func testAFreshMacHasEverythingLeftAndSaysWhatToDo() {
        let c = SetupChecklist(evidence(browsers: [BrowserSetup(name: "Helium", extensionOffIn: ["Default"])]))
        XCTAssertEqual(c.doneCount, 0)
        XCTAssertFalse(c.isComplete)
        XCTAssertEqual(step(c, .browsers).action, .browserHelp)
        XCTAssertEqual(step(c, .domains).action, .command("macos/install.sh --hosts"))
        XCTAssertEqual(step(c, .safeSearch).action, .command("macos/install.sh --hosts"))
        XCTAssertEqual(step(c, .partner).action, .partnerSettings)
        XCTAssertEqual(step(c, .profile).action, .command("macos/install.sh --profile"))
        XCTAssertEqual(step(c, .screenTime).action, .screenTimeSettings)
        XCTAssertEqual(step(c, .accounts).action, .command("macos/setup_guardian.sh --check"))
        XCTAssertTrue(step(c, .browsers).detail.contains("Helium (Default)"), step(c, .browsers).detail)
    }

    func testEverythingDoneIsCompleteWithoutThePaidFilter() {
        let c = SetupChecklist(evidence(admin: false, hosts: 358_239, safe: SafeSearchDNS(), partner: true,
                                        browsers: [lockedHelium], relayOff: true, screenTime: true,
                                        appFiles: true))
        XCTAssertTrue(c.isComplete, c.steps.filter { $0.state == .todo }.map(\.title).joined(separator: ", "))
        XCTAssertEqual(step(c, .systemFilter).state, .optional,
                       "the $99 filter is worth having, not required to finish setup")
        XCTAssertEqual(c.required.count, 8)
    }

    func testTheSystemFilterAloneCoversDomains() {
        let c = SetupChecklist(evidence(hosts: 0, filter: true))
        XCTAssertEqual(step(c, .domains).state, .done)
        XCTAssertEqual(step(c, .systemFilter).state, .done)
    }

    /// A hosts file that encrypted DNS walks around is not finished.
    func testAHostsFileWithOpenBypassesIsNotDone() {
        let open = step(SetupChecklist(evidence(hosts: 330_940, bypassesBlocked: false)), .domains)
        XCTAssertEqual(open.state, .todo)
        XCTAssertTrue(open.detail.contains("Private Relay"), open.detail)
        XCTAssertEqual(step(SetupChecklist(evidence(hosts: 330_940, bypassesBlocked: false, filter: true)),
                            .domains).state, .done, "the system filter does not go through DNS")
    }

    func testTheBypassSampleIsReadFromTheHostsFile() {
        let blocked = """
            0.0.0.0 mask.icloud.com mask-h2.icloud.com
            0.0.0.0 mozilla.cloudflare-dns.com # Firefox
            0.0.0.0 dns.google
            """
        XCTAssertTrue(SetupEvidence.dnsBypassesBlocked(hosts: blocked))
        XCTAssertFalse(SetupEvidence.dnsBypassesBlocked(hosts: "0.0.0.0 mask.icloud.com"))
        XCTAssertFalse(SetupEvidence.dnsBypassesBlocked(
            hosts: "# 0.0.0.0 mask.icloud.com\n0.0.0.0 mozilla.cloudflare-dns.com\n0.0.0.0 dns.google"))
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

    /// A moved address breaks the engine, which is worse than not forcing it:
    /// the step says which one, and how to fix it.
    func testAStaleSafeSearchAddressIsNamed() {
        let s = step(SetupChecklist(evidence(safe: SafeSearchDNS(stale: ["Bing"]))), .safeSearch)
        XCTAssertEqual(s.state, .todo)
        XCTAssertTrue(s.detail.contains("Bing"), s.detail)
        XCTAssertTrue(s.detail.contains("stops loading"), s.detail)
    }

    // MARK: - SafeSearch in /etc/hosts

    private let dns: (String) -> Set<String>? = { host in
        ["forcesafesearch.google.com": ["216.239.38.120"], "restrict.youtube.com": ["216.239.38.120"],
         "strict.bing.com": ["150.171.27.16", "150.171.28.16"],
         "safe.duckduckgo.com": ["40.114.177.246"]][host]
    }

    func testEveryEngineMappedIsForced() {
        let hosts = """
            127.0.0.1 localhost
            # >>> hisn safesearch begin >>>
            216.239.38.120 www.google.com
            216.239.38.120\twww.youtube.com
            150.171.28.16 www.bing.com
            40.114.177.246 duckduckgo.com www.duckduckgo.com
            """
        XCTAssertEqual(SetupEvidence.safeSearchDNS(hosts: hosts, resolve: dns), SafeSearchDNS())
    }

    func testMissingBlockedAndMovedEnginesAreToldApart() {
        let hosts = """
            216.239.38.120 www.google.com
            0.0.0.0 www.youtube.com
            204.79.197.220 www.bing.com
            # 40.114.177.246 duckduckgo.com
            """
        let r = SetupEvidence.safeSearchDNS(hosts: hosts, resolve: dns)
        XCTAssertEqual(r.missing, ["YouTube", "DuckDuckGo"], "a sinkhole or a comment is no SafeSearch")
        XCTAssertEqual(r.stale, ["Bing"], "Bing's old address")
    }

    func testOfflineAPresentLineCountsAsForced() {
        let r = SetupEvidence.safeSearchDNS(hosts: "150.171.28.16 www.bing.com", resolve: { _ in nil })
        XCTAssertEqual(r.stale, [])
        XCTAssertEqual(r.missing, ["Google", "YouTube", "DuckDuckGo"])
    }

    /// The browser link runs the bridge inside the bundle: a bundle the user
    /// owns is a program they can swap, split or no split.
    func testAppFilesTheUserOwnsAreNotDone() {
        XCTAssertEqual(step(SetupChecklist(evidence(appFiles: false)), .appFiles).action,
                       .command("macos/install.sh"))
        XCTAssertEqual(step(SetupChecklist(evidence(appFiles: nil)), .appFiles).state, .todo)
        XCTAssertEqual(step(SetupChecklist(evidence(appFiles: true)), .appFiles).state, .done)
    }

    func testAppFilesOwnedByTheUserAreReadAsUnprotected() throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("hisn-bundle-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: root) }
        let bundle = root.appendingPathComponent("Hisn.app")
        try FileManager.default.createDirectory(at: bundle.appendingPathComponent("Contents/MacOS"),
                                                withIntermediateDirectories: true)
        XCTAssertNil(SetupEvidence.appFilesProtected(bundle: bundle.path), "no bridge: not installed")
        FileManager.default.createFile(atPath: bundle.appendingPathComponent("Contents/MacOS/HisnBridge").path,
                                       contents: Data("#!/bin/sh".utf8))
        XCTAssertEqual(SetupEvidence.appFilesProtected(bundle: bundle.path), false)
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
