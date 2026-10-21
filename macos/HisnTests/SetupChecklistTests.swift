import XCTest
@testable import Hisn

/// The setup checklist, from evidence alone — and the one part of reading the
/// Mac worth pinning: which browser profiles run the extension.
final class SetupChecklistTests: XCTestCase {

    private static let noSafeSearch = SafeSearchDNS(missing: ["Google", "YouTube", "Bing", "DuckDuckGo", "Brave Search"])

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
                                            guestLocked: true, dnsLocked: true,
                                            extensionManaged: true, nativeLinkProtected: true)

    private func step(_ c: SetupChecklist, _ id: SetupChecklist.Step.ID) -> SetupChecklist.Step {
        c.steps.first { $0.id == id }!
    }

    func testTheStepsComeInSetupOrderWithTheAccountSplitLast() {
        let c = SetupChecklist(evidence())
        XCTAssertEqual(c.steps.map(\.id),
                       [.browsers, .extensionManagement, .domains, .safeSearch, .partner, .profile, .screenTime, .appFiles,
                        .accounts, .systemFilter])
    }

    func testRemovalProtectionNeedsAnInstalledBrowserAndBothVerifiedLayers() {
        XCTAssertEqual(step(SetupChecklist(evidence(browsers: [])), .extensionManagement).state, .todo)
        for browser in [BrowserSetup(name: "Chrome", extensionManaged: true),
                        BrowserSetup(name: "Chrome", nativeLinkProtected: true)] {
            XCTAssertEqual(step(SetupChecklist(evidence(browsers: [browser])), .extensionManagement).state, .todo)
        }
        XCTAssertEqual(step(SetupChecklist(evidence(browsers: [lockedHelium])), .extensionManagement).state, .done)
    }

    func testAFreshMacHasEverythingLeftAndSaysWhatToDo() {
        let c = SetupChecklist(evidence(browsers: [BrowserSetup(name: "Helium", extensionOffIn: ["Default"])]))
        XCTAssertEqual(c.doneCount, 0)
        XCTAssertFalse(c.isComplete)
        XCTAssertEqual(step(c, .browsers).action, .browserHelp)
        XCTAssertEqual(step(c, .domains).action, .enableFilter)
        XCTAssertEqual(step(c, .safeSearch).action, .command("macos/install.sh --hosts"))
        XCTAssertEqual(step(c, .partner).action, .partnerSettings)
        XCTAssertEqual(step(c, .profile).action, .deviceManagementSettings)
        XCTAssertEqual(step(c, .screenTime).action, .screenTimeSettings)
        XCTAssertEqual(step(c, .accounts).action, .accountsSettings)
        XCTAssertTrue(step(c, .browsers).detail.contains("Helium (Default)"), step(c, .browsers).detail)
    }

    func testLegacyChecklistCompletionDoesNotCertifyLaptopWideProtection() {
        let c = SetupChecklist(evidence(admin: false, hosts: 358_239, safe: SafeSearchDNS(), partner: true,
                                        browsers: [lockedHelium], relayOff: true, screenTime: true,
                                        appFiles: true))
        XCTAssertTrue(c.isComplete, c.steps.filter { $0.state == .todo }.map(\.title).joined(separator: ", "))
        XCTAssertEqual(step(c, .systemFilter).state, .optional,
                       "legacy checklist completion remains separate from verified laptop-wide coverage")
        XCTAssertEqual(c.required.count, 9)
        let readiness = LaptopSetupReadiness(
            protection: ProtectionEvidence(filter: .off, extensionLastSeen: Date(),
                hostsEntries: 358_239, filterCanRun: false),
            checklist: c, requireOutsideLock: true)
        XCTAssertFalse(readiness.isReady, "legacy completion is not verified laptop-wide coverage")
        XCTAssertTrue(readiness.issues.contains(.signedBuild))
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

    func testNoSupportedBrowserDoesNotClaimSafariPageScanning() {
        let c = SetupChecklist(evidence(browsers: []))
        let browser = step(c, .browsers)
        XCTAssertEqual(browser.state, .todo)
        XCTAssertEqual(browser.action, .browserHelp)
        XCTAssertTrue(browser.detail.contains("no verified Hisn page scanner"), browser.detail)
        XCTAssertFalse(c.isComplete)
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
         "safe.duckduckgo.com": ["40.114.177.246"], "forcesafe.search.brave.com": ["192.0.2.5"]][host]
    }

    func testEveryEngineMappedIsForced() {
        let hosts = """
            127.0.0.1 localhost
            # >>> hisn safesearch begin >>>
            216.239.38.120 www.google.com
            216.239.38.120\twww.youtube.com
            150.171.28.16 www.bing.com
            40.114.177.246 duckduckgo.com www.duckduckgo.com
            192.0.2.5 search.brave.com
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
        XCTAssertEqual(r.missing, ["YouTube", "DuckDuckGo", "Brave Search"], "a sinkhole or a comment is no SafeSearch")
        XCTAssertEqual(r.stale, ["Bing"], "Bing's old address")
    }

    func testOfflineAPresentLineCountsAsForced() {
        let r = SetupEvidence.safeSearchDNS(hosts: "150.171.28.16 www.bing.com", resolve: { _ in nil })
        XCTAssertEqual(r.stale, [])
        XCTAssertEqual(r.missing, ["Google", "YouTube", "DuckDuckGo", "Brave Search"])
    }

    /// The browser link runs the bridge inside the bundle: a bundle the user
    /// owns is a program they can swap, split or no split.
    func testAppFilesTheUserOwnsAreNotDone() {
        XCTAssertEqual(step(SetupChecklist(evidence(appFiles: false)), .appFiles).action,
                       .applicationsFolder)
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

    // MARK: - Login-agent configuration (injected, never installed)

    private var installedAgent: [String: Any] {
        ["Label": "app.hisn.agent",
         "ProgramArguments": ["/Applications/Hisn.app/Contents/MacOS/Hisn", "--background", "--for-user", "daily"],
         "RunAtLoad": true, "KeepAlive": ["SuccessfulExit": false],
         "ThrottleInterval": 10, "LimitLoadToSessionType": "Aqua", "ProcessType": "Interactive"]
    }

    private func protectedAgentAttributes(size: Int) -> [FileAttributeKey: Any] {
        [.type: FileAttributeType.typeRegular, .ownerAccountName: "root", .ownerAccountID: 0,
         .posixPermissions: 0o644, .size: size]
    }

    private func agentMatches(_ plist: Any, user: String = "daily", writable: Bool = false,
                              format: PropertyListSerialization.PropertyListFormat = .xml,
                              attributes: ([FileAttributeKey: Any]) -> [FileAttributeKey: Any] = { $0 }) throws -> Bool {
        let data = try PropertyListSerialization.data(fromPropertyList: plist, format: format, options: 0)
        return SetupEvidence.launchAgentConfigurationIsProtected(data,
            attributes: attributes(protectedAgentAttributes(size: data.count)),
            writable: writable, user: user)
    }

    func testOnlyMatchingProtectedAgentConfigurationsPassInXMLAndBinary() throws {
        XCTAssertTrue(try agentMatches(installedAgent))
        XCTAssertTrue(try agentMatches(installedAgent, format: .binary))
        XCTAssertFalse(try agentMatches(installedAgent, user: "another-account"))
        XCTAssertFalse(try agentMatches(installedAgent, user: ""))
    }

    func testRootOwnershipAloneDoesNotProtectTheAgent() throws {
        for key in [FileAttributeKey.ownerAccountName, .ownerAccountID, .posixPermissions, .size, .type] {
            XCTAssertFalse(try agentMatches(installedAgent, attributes: { attributes in
                var copy = attributes; copy.removeValue(forKey: key); return copy
            }), "missing \(key.rawValue) is not verified")
        }
        XCTAssertFalse(try agentMatches(installedAgent, attributes: { attributes in
            var copy = attributes; copy[.ownerAccountName] = "daily"; return copy
        }))
        XCTAssertFalse(try agentMatches(installedAgent, attributes: { attributes in
            var copy = attributes; copy[.ownerAccountID] = 501; return copy
        }))
        for mode in [0o664, 0o646, 0o666, 0o777] {
            XCTAssertFalse(try agentMatches(installedAgent, attributes: { attributes in
                var copy = attributes; copy[.posixPermissions] = mode; return copy
            }), "mode \(String(mode, radix: 8))")
        }
        XCTAssertFalse(try agentMatches(installedAgent, writable: true), "ACL/current-user writability also fails")
        XCTAssertTrue(try agentMatches(installedAgent, attributes: { attributes in
            var copy = attributes; copy[.posixPermissions] = 0o600; return copy
        }))
    }

    func testAgentSymlinksAndNonRegularFilesNeverPass() throws {
        for type in [FileAttributeType.typeSymbolicLink, .typeDirectory, .typeSocket] {
            XCTAssertFalse(try agentMatches(installedAgent, attributes: { attributes in
                var copy = attributes; copy[.type] = type; return copy
            }))
        }
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("hisn-agent-link-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: root) }
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let file = root.appendingPathComponent("actual.plist")
        let link = root.appendingPathComponent("agent.plist")
        try PropertyListSerialization.data(fromPropertyList: installedAgent, format: .xml, options: 0).write(to: file)
        try FileManager.default.createSymbolicLink(at: link, withDestinationURL: file)
        let actual = try FileManager.default.attributesOfItem(atPath: link.path)
        XCTAssertEqual(actual[.type] as? FileAttributeType, .typeSymbolicLink,
                       "the production metadata read must describe the link, not its target")
    }

    func testAgentIdentityAndProgramArgumentsMustBeExact() throws {
        var wrong = installedAgent
        wrong["Label"] = "app.unrelated.agent"
        XCTAssertFalse(try agentMatches(wrong))
        let arguments = installedAgent["ProgramArguments"] as! [String]
        for index in arguments.indices {
            var changed = arguments; changed[index] = "wrong"
            wrong = installedAgent; wrong["ProgramArguments"] = changed
            XCTAssertFalse(try agentMatches(wrong), "argument \(index)")
        }
        for changed in [Array(arguments.dropLast()), arguments + ["--extra"], ["/usr/bin/true"]] {
            wrong = installedAgent; wrong["ProgramArguments"] = changed
            XCTAssertFalse(try agentMatches(wrong))
        }
        for key in ["Program", "BundleProgram"] {
            wrong = installedAgent; wrong[key] = "/usr/bin/true"
            XCTAssertFalse(try agentMatches(wrong), "\(key) can override the executable")
        }
    }

    func testAgentStartupAndFailureRecoveryRequireRealPlistBooleans() throws {
        for value in [false, 1, "true"] as [Any] {
            var wrong = installedAgent; wrong["RunAtLoad"] = value
            XCTAssertFalse(try agentMatches(wrong))
        }
        var wrong = installedAgent; wrong.removeValue(forKey: "RunAtLoad")
        XCTAssertFalse(try agentMatches(wrong))
        for value in [false, true, [String: Any](), ["SuccessfulExit": true],
                      ["SuccessfulExit": 0], ["NetworkState": true],
                      ["SuccessfulExit": false, "NetworkState": true]] as [Any] {
            wrong = installedAgent; wrong["KeepAlive"] = value
            XCTAssertFalse(try agentMatches(wrong), "only the known account-safe recovery configuration passes")
        }
        wrong = installedAgent; wrong.removeValue(forKey: "KeepAlive")
        XCTAssertFalse(try agentMatches(wrong))
    }

    func testAgentStartupOverridesAndUnboundedRecoveryDelayAreRejected() throws {
        for key in ["Disabled", "LaunchOnlyOnce"] {
            var wrong = installedAgent; wrong[key] = true
            XCTAssertFalse(try agentMatches(wrong))
            wrong[key] = 0
            XCTAssertFalse(try agentMatches(wrong))
            wrong[key] = false
            XCTAssertTrue(try agentMatches(wrong))
        }
        var wrong = installedAgent; wrong["UserName"] = "other"
        XCTAssertFalse(try agentMatches(wrong))
        wrong = installedAgent; wrong["LimitLoadToSessionType"] = "Background"
        XCTAssertFalse(try agentMatches(wrong))
        for value in [0, 61, 10.5, true, "10"] as [Any] {
            wrong = installedAgent; wrong["ThrottleInterval"] = value
            XCTAssertFalse(try agentMatches(wrong))
        }
    }

    func testMalformedOversizedAndChangingAgentFilesAreNotVerified() throws {
        for data in [Data(), Data("not a plist".utf8), Data(repeating: 0, count: 65_537)] {
            XCTAssertFalse(SetupEvidence.launchAgentConfigurationIsProtected(data,
                attributes: protectedAgentAttributes(size: data.count), writable: false, user: "daily"))
        }
        XCTAssertFalse(try agentMatches(["not", "a", "dictionary"]))
        XCTAssertFalse(try agentMatches(installedAgent, attributes: { attributes in
            var copy = attributes; copy[.size] = (attributes[.size] as! Int) + 1; return copy
        }), "a changed file size cannot certify the earlier snapshot")
    }

    func testInjectedBundlePathDoesNotReadOrRequireAnInstalledApp() throws {
        var plist = installedAgent
        plist["ProgramArguments"] = ["/tmp/fixture/Hisn.app/Contents/MacOS/Hisn", "--background", "--for-user", "daily"]
        let data = try PropertyListSerialization.data(fromPropertyList: plist, format: .binary, options: 0)
        XCTAssertFalse(try agentMatches(plist), "the default installed path is still required")
        XCTAssertTrue(SetupEvidence.launchAgentConfigurationIsProtected(data,
            attributes: protectedAgentAttributes(size: data.count), writable: false,
            bundle: "/tmp/fixture/Hisn.app", user: "daily"))
    }

    func testEdgeKeepsItsPolicyUnderItsOwnDomain() {
        XCTAssertEqual(SetupEvidence.policyDomain("com.microsoft.edgemac"), "com.microsoft.Edge")
        XCTAssertEqual(SetupEvidence.policyDomain("net.imput.helium"), "net.imput.helium")
    }

    // MARK: - Browser profiles

    private func browserProfileRoot(_ profiles: [String: [String: Data]]) throws -> URL {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("hisn-setup-profiles-\(UUID().uuidString)")
        addTeardownBlock { try? FileManager.default.removeItem(at: root) }
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        for (folder, files) in profiles {
            let directory = root.appendingPathComponent(folder)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            for (file, data) in files { try data.write(to: directory.appendingPathComponent(file)) }
        }
        return root
    }

    private func browserPreferences(_ entries: [String: Any], name: String? = nil) throws -> Data {
        var preferences: [String: Any] = ["extensions": ["settings": entries]]
        if let name { preferences["profile"] = ["name": name] }
        return try JSONSerialization.data(withJSONObject: preferences)
    }

    func testModernEnablementUsesTheRuntimeInterpretation() throws {
        let id = "hfhaffbmoeepcdolgejeidkgaoapcjig"
        let root = try browserProfileRoot([
            "Default": ["Preferences": browserPreferences([id: ["disable_reasons": [Int](), "location": 7]], name: "Work")],
            "Profile 1": ["Secure Preferences": browserPreferences([id: ["disable_reasons": 0, "location": 9]], name: "Personal")]
        ])
        let setup = SetupEvidence.inspectBrowserProfiles(in: root, ids: [id])
        XCTAssertTrue(setup.checked)
        XCTAssertEqual(setup.missing, [])
        XCTAssertEqual(BrowserProfileEvidence.inspect(root: root, ids: [id]).profiles.map(\.state),
                       [.noExplicitLoss, .noExplicitLoss])
        let managed = SetupEvidence.inspectBrowserProfiles(in: root, ids: [id], requireManaged: true)
        XCTAssertTrue(managed.checked)
        XCTAssertEqual(managed.missing, [], "modern policy installs need no legacy state key")
    }

    func testSetupStillRequiresAPolicyInstallLocationForManagedProtection() throws {
        let id = "hfhaffbmoeepcdolgejeidkgaoapcjig"
        let entries: [Any] = [["state": 1], ["state": 1, "location": 4],
                             ["disable_reasons": 0, "location": true]]
        for entry in entries {
            let root = try browserProfileRoot([
                "Default": ["Preferences": browserPreferences([id: entry], name: "Local copy")]
            ])
            let regular = SetupEvidence.inspectBrowserProfiles(in: root, ids: [id])
            XCTAssertTrue(regular.checked)
            XCTAssertEqual(regular.missing, [])
            let managed = SetupEvidence.inspectBrowserProfiles(in: root, ids: [id], requireManaged: true)
            XCTAssertFalse(managed.checked)
            XCTAssertEqual(managed.missing, ["Local copy"])
        }
        XCTAssertEqual(BrowserProfileEvidence.entryState(["disable_reasons": 0, "location": 7.0],
                                                         requireManaged: true), .unconfirmed)
    }

    func testConflictingOrCorruptSourcesCannotCertifySetup() throws {
        let id = "hfhaffbmoeepcdolgejeidkgaoapcjig"
        let healthy = try browserPreferences([id: ["state": 1]], name: "Work")
        for secure in [try browserPreferences([id: ["state": 0]]), Data("not-json".utf8)] {
            let root = try browserProfileRoot([
                "Default": ["Preferences": healthy, "Secure Preferences": secure]
            ])
            let result = SetupEvidence.inspectBrowserProfiles(in: root, ids: [id])
            XCTAssertFalse(result.checked)
            XCTAssertEqual(result.missing, ["Work"])
            XCTAssertEqual(BrowserProfileEvidence.inspect(root: root, ids: [id]).profiles.first?.state,
                           .unconfirmed)
        }
    }

    func testModernDisablementAndMalformedRecordsRemainMissing() throws {
        let id = "hfhaffbmoeepcdolgejeidkgaoapcjig"
        let disabledRoot = try browserProfileRoot([
            "Default": ["Preferences": browserPreferences([id: ["disable_reasons": [1]]], name: "Disabled")]
        ])
        let disabled = SetupEvidence.inspectBrowserProfiles(in: disabledRoot, ids: [id])
        XCTAssertTrue(disabled.checked)
        XCTAssertEqual(disabled.missing, ["Disabled"])
        for entry: [String: Any] in [["state": true], ["disable_reasons": false],
                                     ["state": 1, "disable_reasons": ["unknown"]], ["location": 7]] {
            let root = try browserProfileRoot([
                "Default": ["Preferences": browserPreferences([id: entry])]
            ])
            let result = SetupEvidence.inspectBrowserProfiles(in: root, ids: [id])
            XCTAssertFalse(result.checked)
            XCTAssertEqual(result.missing, ["Default"])
        }
    }

    func testAlternativeAcceptedEnabledCopyCanCertifySetup() throws {
        let root = try browserProfileRoot([
            "Default": ["Preferences": browserPreferences([
                "old-copy": ["state": 0], "published-copy": ["disable_reasons": 0, "location": 7]])]
        ])
        let result = SetupEvidence.inspectBrowserProfiles(in: root, ids: ["old-copy", "published-copy"])
        XCTAssertTrue(result.checked)
        XCTAssertEqual(result.missing, [])
    }

    func testSetupSharesTheTotalPreferencesReadBudget() throws {
        let id = "hfhaffbmoeepcdolgejeidkgaoapcjig"
        let preferences = try browserPreferences([id: ["state": 1]])
        let root = try browserProfileRoot([
            "Default": ["Preferences": preferences], "Profile 1": ["Preferences": preferences]
        ])
        let exhausted = SetupEvidence.inspectBrowserProfiles(in: root, ids: [id], maximumBytes: preferences.count)
        XCTAssertFalse(exhausted.checked)
        XCTAssertEqual(exhausted.missing, ["Profile 1"])
        let oversized = SetupEvidence.inspectBrowserProfiles(in: root, ids: [id], maximumBytes: preferences.count - 1)
        XCTAssertFalse(oversized.checked)
        XCTAssertEqual(oversized.missing, ["Default", "Profile 1"])
        XCTAssertFalse(SetupEvidence.inspectBrowserProfiles(in: root, ids: [id], maximumBytes: 0).checked)
        XCTAssertFalse(SetupEvidence.inspectBrowserProfiles(in: root, ids: []).checked)
    }

    func testSetupBoundsTheNumberOfProfilesItChecks() throws {
        var profiles: [String: [String: Data]] = [:]
        for index in 0...256 { profiles["Profile \(index)"] = [:] }
        let root = try browserProfileRoot(profiles)
        let result = SetupEvidence.inspectBrowserProfiles(in: root, ids: ["hisn-copy"])
        XCTAssertFalse(result.checked)
        XCTAssertEqual(result.missing, [], "an oversized enumeration is unknown rather than verified")
    }

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
