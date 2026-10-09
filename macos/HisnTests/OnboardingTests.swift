import AppKit
import SwiftUI
import XCTest
@testable import Hisn

@MainActor
final class OnboardingTests: XCTestCase {
    func testProtectionAndCommitmentScreensRenderInBothLanguages() async throws {
        let originalPage = AppNavigation.shared.page
        defer { AppNavigation.shared.page = originalPage }
        for language in ["en", "ar"] {
            for page in [ContentView.Page.overview, .lock] {
                AppNavigation.shared.page = page
                let view = NSHostingView(rootView: ContentView()
                    .environment(\.locale, Locale(identifier: language))
                    .environment(\.layoutDirection, language == "ar" ? .rightToLeft : .leftToRight)
                    .frame(width: 880, height: 900))
                let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 880, height: 900),
                    styleMask: [.titled], backing: .buffered, defer: false)
                window.isReleasedWhenClosed = false
                window.contentView = view
                defer { window.close() }
                window.orderFront(nil)
                try await Task.sleep(nanoseconds: 300_000_000)
                view.layoutSubtreeIfNeeded()
                let bitmap = try XCTUnwrap(view.bitmapImageRepForCachingDisplay(in: view.bounds))
                view.cacheDisplay(in: view.bounds, to: bitmap)
                var data = try XCTUnwrap(bitmap.representation(using: .png, properties: [:]))
                if ProcessInfo.processInfo.environment["HISN_CAPTURE_UI"] == "1" {
                    NSApp.activate(ignoringOtherApps: true)
                    window.makeKeyAndOrderFront(nil)
                    try await Task.sleep(nanoseconds: 500_000_000)
                    let captureURL = FileManager.default.temporaryDirectory
                        .appendingPathComponent("hisn-plan-\(UUID().uuidString).png")
                    let capture = Process()
                    capture.executableURL = URL(fileURLWithPath: "/usr/sbin/screencapture")
                    capture.arguments = ["-x", "-l", String(window.windowNumber), captureURL.path]
                    try capture.run()
                    capture.waitUntilExit()
                    XCTAssertEqual(capture.terminationStatus, 0)
                    data = try Data(contentsOf: captureURL)
                }
                XCTAssertGreaterThan(data.count, 2000)
                let attachment = XCTAttachment(data: data, uniformTypeIdentifier: "public.png")
                attachment.name = "mac-\(language)-\(page.rawValue)"
                attachment.lifetime = .keepAlways
                add(attachment)
            }
        }
    }
    func testRouterNamesSuggestFamiliesWithoutGuessingFromAddressesOrSpeedLabels() {
        XCTAssertEqual(RouterFamily.suggestion(for: "TP-Link Deco X50"), .deco)
        XCTAssertEqual(RouterFamily.suggestion(for: "ASUS RT-AX88U"), .asus)
        XCTAssertEqual(RouterFamily.suggestion(for: "RT-AX88U"), .asus)
        XCTAssertEqual(RouterFamily.suggestion(for: "FRITZ!Box 7590"), .fritz)
        XCTAssertEqual(RouterFamily.suggestion(for: "GL-MT6000"), .glInet)
        XCTAssertEqual(RouterFamily.suggestion(for: "MikroTik hAP ax3"), .mikroTik)
        XCTAssertNil(RouterFamily.suggestion(for: "192.168.1.1"))
        XCTAssertNil(RouterFamily.suggestion(for: "AX3000 Wi-Fi 6"))
        XCTAssertNil(RouterFamily.suggestion(for: "my ISP gateway"))
        XCTAssertNil(RouterFamily.suggestion(for: "ASUS or NETGEAR"))
        XCTAssertNil(RouterFamily.suggestion(for: "GL-MT6000 with OpenWrt"))
        XCTAssertEqual(RouterFamily.suggestion(for: "ＧＬ-ＭＴ６０００"), .glInet)
        for model in ["GL-AR300M16", "GL-E750V2", "GL-XE300", "GL-B1300", "GL-MG1300"] {
            XCTAssertEqual(RouterFamily.suggestion(for: model), .glInet, model)
        }
        XCTAssertTrue(RouterFamily.deco.usesPhoneApp)
        XCTAssertTrue(RouterFamily.eero.usesPhoneApp)
        XCTAssertTrue(RouterFamily.googleNest.usesPhoneApp)
        XCTAssertFalse(RouterFamily.openWrt.usesPhoneApp)
        XCTAssertEqual(RouterProfile.clean("router\n\0model"), "routermodel")
        XCTAssertEqual(RouterProfile.clean(String(repeating: "x", count: 200)).count, 80)
        XCTAssertTrue(RouterProfile(family: .glInet, model: "GL-SFT1200 (Opal)").excludesBuiltInAdguard)
        XCTAssertTrue(RouterProfile(family: .glInet, model: "GL-AR300M16").excludesBuiltInAdguard)
        XCTAssertFalse(RouterProfile(family: .glInet, model: "GL-MT6000").excludesBuiltInAdguard)
        XCTAssertFalse(RouterProfile(family: .unknown, model: "GL-SFT1200").excludesBuiltInAdguard)
    }

    func testRouterDiscoveryAcceptsOnlyLocalLiteralGatewaysOnPhysicalInterfaces() {
        for address in ["192.168.1.1", "10.0.0.1", "172.16.0.1", "172.31.255.1"] {
            let candidate = RouterDiscovery.candidate(router: address, interface: "en0")
            XCTAssertEqual(candidate?.secureURL.absoluteString, "https://\(address)/")
            XCTAssertEqual(candidate?.localHTTPURL.absoluteString, "http://\(address)/")
        }
        XCTAssertNotNil(RouterDiscovery.candidate(router: "192.168.1.1", interface: "bridge0"))
        for address in ["8.8.8.8", "127.0.0.1", "169.254.1.1", "172.32.0.1", "172.15.0.1",
                        "192.168.1.1@evil.example", "router.example", "192.168.1.1/path",
                        "192.168.1.1:80", "192.168.1.1\n", "192.168.1.1\0evil", "::1",
                        "fe80::1%en0", "192.168.001.001", "", "0.0.0.0"] {
            XCTAssertNil(RouterDiscovery.candidate(router: address, interface: "en0"), address)
        }
        for interface in ["utun0", "ipsec0", "ppp0", "lo0", "en0\n", "unknown"] {
            XCTAssertNil(RouterDiscovery.candidate(router: "192.168.1.1", interface: interface), interface)
        }
        XCTAssertNil(RouterDiscovery.candidate(router: nil, interface: "en0"))
        XCTAssertNil(RouterDiscovery.candidate(router: "192.168.1.1", interface: nil))
    }

    func testRouterGatewayMustBeAUsableNeighborOnTheCurrentSubnet() throws {
        func accepts(_ router: String, _ addresses: [String] = ["192.168.1.20"],
                     _ masks: [String] = ["255.255.255.0"]) throws -> Bool {
            let candidate = try XCTUnwrap(RouterDiscovery.candidate(router: router, interface: "en0"))
            return RouterDiscovery.isOnLocalSubnet(candidate, addresses: addresses, masks: masks)
        }
        XCTAssertTrue(try accepts("192.168.1.1"))
        for router in ["192.168.2.1", "192.168.1.20", "192.168.1.0", "192.168.1.255"] {
            XCTAssertFalse(try accepts(router), router)
        }
        for mask in ["0.0.0.0", "255.0.255.0", "255.255.255.255", "255.255.255.254", "invalid"] {
            XCTAssertFalse(try accepts("192.168.1.1", ["192.168.1.20"], [mask]), mask)
        }
        XCTAssertFalse(try accepts("192.168.1.1", [], []))
        XCTAssertFalse(try accepts("192.168.1.1", ["192.168.1.20"], []))
        XCTAssertFalse(try accepts("192.168.1.1", ["192.168.1.1", "192.168.1.20"],
                                  ["255.255.255.0", "255.255.255.0"]))
        XCTAssertTrue(try accepts("10.0.1.1", ["192.168.1.20", "10.0.1.5"],
                                 ["255.255.255.0", "255.255.255.0"]))
        XCTAssertTrue(try accepts("172.16.1.1", ["172.16.2.5"], ["255.255.0.0"]))
    }

    func testRouterAssistantRendersWithoutDiscoveringOrConfiguringNetwork() async throws {
        for language in ["en", "ar"] {
          for profile in [RouterProfile(), RouterProfile(family: .deco, model: "Deco X50"),
                          RouterProfile(family: .glInet, model: "GL-SFT1200")] {
            let view = NSHostingView(rootView: ScrollView {
                NetworkSetupGuide(initialRouterProfile: profile).padding(28)
            }
            .environment(\.locale, Locale(identifier: language))
            .environment(\.layoutDirection, language == "ar" ? .rightToLeft : .leftToRight)
            .frame(width: 520, height: 1000))
            let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 520, height: 1000),
                styleMask: [.titled], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.contentView = view
            defer { window.close() }
            window.orderFront(nil)
            try await Task.sleep(nanoseconds: 200_000_000)
            view.layoutSubtreeIfNeeded()
            let bitmap = try XCTUnwrap(view.bitmapImageRepForCachingDisplay(in: view.bounds))
            view.cacheDisplay(in: view.bounds, to: bitmap)
            XCTAssertGreaterThanOrEqual(bitmap.pixelsWide, 520)
            if ProcessInfo.processInfo.environment["HISN_CAPTURE_UI"] == "1" {
                NSApp.activate(ignoringOtherApps: true)
                window.makeKeyAndOrderFront(nil)
                try await Task.sleep(nanoseconds: 1_000_000_000)
                let directory = FileManager.default.temporaryDirectory.appendingPathComponent("hisn-router-review")
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                let capture = Process()
                capture.executableURL = URL(fileURLWithPath: "/usr/sbin/screencapture")
                capture.arguments = ["-x", "-l", String(window.windowNumber),
                    directory.appendingPathComponent("setup-\(profile.family.rawValue)-\(language).png").path]
                try capture.run()
                capture.waitUntilExit()
                XCTAssertEqual(capture.terminationStatus, 0)
            }
          }
        }
    }

    func testHistoricalSetupVisitDoesNotCertifyANewProcess() throws {
        let name = TestNamespace.make()
        defer { TestNamespace.dispose(name) }
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        XCTAssertEqual(AppNavigation.firstPage(defaults: defaults, hostingTests: false), .setup)
        XCTAssertEqual(AppNavigation.firstPage(defaults: defaults, hostingTests: false), .setup)
        defaults.set(true, forKey: "hisn.openedOnSetup")
        defaults.set(true, forKey: "hisn.setup.administratorConfirmed")
        defaults.set(true, forKey: "hisn.setup.recoveryConfirmed")
        XCTAssertEqual(AppNavigation.firstPage(defaults: defaults, hostingTests: false), .setup,
                       "a visit or human confirmation is not live protection evidence")
        XCTAssertEqual(AppNavigation.firstPage(defaults: defaults, hostingTests: true), .overview)
    }

    func testExplicitNavigationDoesNotTrapSettingsOrRecoveryBehindSetup() {
        let originalPage = AppNavigation.shared.page
        defer { AppNavigation.shared.page = originalPage }
        for page in ContentView.Page.allCases {
            AppNavigation.shared.open(page)
            XCTAssertEqual(AppNavigation.shared.page, page)
        }
    }

    func testLaptopSetupStatesRenderFromInjectedEvidenceInBothLanguages() async throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let goodChecklist = SetupChecklist(SetupEvidence(isAdmin: true, hostsEntries: nil,
            partnerKeySet: false, browsers: [BrowserSetup(name: "Chrome")],
            privateRelayOff: false, screenTimeAdultFilter: false,
            systemFilterRunning: true, appFilesProtected: true))
        let cases: [(String, LaptopSetupReadiness)] = [
            ("ready", LaptopSetupReadiness(
                protection: ProtectionEvidence(filter: .on(domainCount: 150_000),
                    extensionLastSeen: now, now: now),
                checklist: goodChecklist, requireOutsideLock: true)),
            ("missing", LaptopSetupReadiness(
                protection: ProtectionEvidence(filter: .off, extensionLastSeen: nil,
                    now: now, filterCanRun: false),
                checklist: SetupChecklist(SetupEvidence(isAdmin: true, hostsEntries: nil,
                    partnerKeySet: false, browsers: [], privateRelayOff: false,
                    screenTimeAdultFilter: false, systemFilterRunning: false,
                    appFilesProtected: nil)),
                requireOutsideLock: false)),
            ("checking", LaptopSetupReadiness(
                protection: ProtectionEvidence(filter: .unknown, extensionLastSeen: nil, now: now),
                checklist: nil, requireOutsideLock: false)),
        ]
        XCTAssertTrue(cases[0].1.isReady)
        XCTAssertFalse(cases[1].1.isReady)
        XCTAssertTrue(cases[2].1.isChecking)
        var actions = 0
        for language in ["en", "ar"] {
            for (state, readiness) in cases {
                let view = NSHostingView(rootView: ScrollView {
                    LaptopSetupStatusView(readiness: readiness, review: { _ in actions += 1 })
                        .padding(28)
                }
                .environment(\.locale, Locale(identifier: language))
                .environment(\.layoutDirection, language == "ar" ? .rightToLeft : .leftToRight)
                .frame(width: 520, height: 900))
                let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 520, height: 900),
                    styleMask: [.titled], backing: .buffered, defer: false)
                window.isReleasedWhenClosed = false
                window.contentView = view
                defer { window.close() }
                window.orderFront(nil)
                try await Task.sleep(nanoseconds: 200_000_000)
                view.layoutSubtreeIfNeeded()
                let bitmap = try XCTUnwrap(view.bitmapImageRepForCachingDisplay(in: view.bounds))
                view.cacheDisplay(in: view.bounds, to: bitmap)
                let data = try XCTUnwrap(bitmap.representation(using: .png, properties: [:]))
                XCTAssertGreaterThanOrEqual(bitmap.pixelsWide, 520)
                XCTAssertGreaterThan(data.count, 2000)
                var screenData = data
                if ProcessInfo.processInfo.environment["HISN_CAPTURE_UI"] == "1" {
                    NSApp.activate(ignoringOtherApps: true)
                    window.makeKeyAndOrderFront(nil)
                    try await Task.sleep(nanoseconds: 300_000_000)
                    let captureURL = FileManager.default.temporaryDirectory
                        .appendingPathComponent("hisn-setup-\(UUID().uuidString).png")
                    let capture = Process()
                    capture.executableURL = URL(fileURLWithPath: "/usr/sbin/screencapture")
                    capture.arguments = ["-x", "-l", String(window.windowNumber), captureURL.path]
                    try capture.run()
                    capture.waitUntilExit()
                    XCTAssertEqual(capture.terminationStatus, 0)
                    screenData = try Data(contentsOf: captureURL)
                }
                let attachment = XCTAttachment(data: screenData, uniformTypeIdentifier: "public.png")
                attachment.name = "mac-laptop-setup-\(language)-\(state)"
                attachment.lifetime = .keepAlways
                add(attachment)
            }
        }
        XCTAssertEqual(actions, 0, "rendering a status must not activate a setup action")
    }

    func testSetupRendersAtMinimumWindowWidth() async throws {
        let name = TestNamespace.make()
        defer { TestNamespace.dispose(name) }
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        let evidence = SetupEvidence(isAdmin: true, hostsEntries: nil, partnerKeySet: false,
                                     browsers: [BrowserSetup(name: "Chrome", extensionOffIn: ["Default"])],
                                     privateRelayOff: false, screenTimeAdultFilter: false,
                                     systemFilterRunning: false, appFilesProtected: nil)
        let view = NSHostingView(rootView: ScrollView {
            SetupPage(filter: .shared, goTo: { _ in }, initialChecklist: SetupChecklist(evidence),
                      confirmationDefaults: defaults)
                .padding(28)
        }.frame(width: 520, height: 600))
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 520, height: 600),
                              styleMask: [.titled], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.contentView = view
        defer { window.close() }
        window.orderFront(nil)
        try await Task.sleep(nanoseconds: 200_000_000)
        view.layoutSubtreeIfNeeded()
        let bitmap = try XCTUnwrap(view.bitmapImageRepForCachingDisplay(in: view.bounds))
        view.cacheDisplay(in: view.bounds, to: bitmap)
        XCTAssertGreaterThanOrEqual(bitmap.pixelsWide, 520)
        XCTAssertGreaterThanOrEqual(bitmap.pixelsHigh, 600)
        // Native controls are composited outside cacheDisplay's bitmap. An
        // optional local capture records the real window for visual review.
        if ProcessInfo.processInfo.environment["HISN_CAPTURE_UI"] == "1" {
            let directory = FileManager.default.temporaryDirectory.appendingPathComponent("hisn-ui-review")
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let capture = Process()
            capture.executableURL = URL(fileURLWithPath: "/usr/sbin/screencapture")
            capture.arguments = ["-x", "-l", String(window.windowNumber), directory.appendingPathComponent("setup-native.png").path]
            try capture.run()
            capture.waitUntilExit()
            XCTAssertEqual(capture.terminationStatus, 0, "Native capture requires screen recording permission")
        }
    }

    func testRecoveryAndAccountConfirmationsRenderAtMinimumWidth() async throws {
        let name = TestNamespace.make()
        defer { TestNamespace.dispose(name) }
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        let evidence = SetupEvidence(isAdmin: false, hostsEntries: 400_000, partnerKeySet: true,
            browsers: [BrowserSetup(name: "Chrome", incognitoLocked: true, guestLocked: true,
                dnsLocked: true, extensionManaged: true, nativeLinkProtected: true)],
            privateRelayOff: true, screenTimeAdultFilter: true, systemFilterRunning: true)
        let view = NSHostingView(rootView: ScrollView {
            SetupPage(filter: .shared, goTo: { _ in }, initialChecklist: SetupChecklist(evidence),
                confirmationDefaults: defaults, initialExpandedStages: [.recovery, .accounts])
                .padding(28)
        }.frame(width: 520, height: 600))
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 520, height: 600),
            styleMask: [.titled], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.contentView = view
        defer { window.close() }
        window.orderFront(nil)
        try await Task.sleep(nanoseconds: 200_000_000)
        view.layoutSubtreeIfNeeded()
        let bitmap = try XCTUnwrap(view.bitmapImageRepForCachingDisplay(in: view.bounds))
        view.cacheDisplay(in: view.bounds, to: bitmap)
        XCTAssertGreaterThanOrEqual(bitmap.pixelsWide, 520)
        XCTAssertGreaterThanOrEqual(bitmap.pixelsHigh, 600)
        XCTAssertFalse(defaults.bool(forKey: "hisn.setup.administratorConfirmed"))
        XCTAssertFalse(defaults.bool(forKey: "hisn.setup.recoveryConfirmed"))
    }

    func testNetworkDNSGuideFitsMinimumWidthInBothLanguages() async throws {
        let result = NetworkDNSProbe.Result(checkedAt: Date(),
            a: NetworkDNSProbe.classify(recordType: .a, adult: .addresses(["0.0.0.0"]),
                control: .addresses(["93.184.215.14"])),
            aaaa: NetworkDNSProbe.classify(recordType: .aaaa, adult: .addresses(["::"]),
                control: .addresses(["2606:4700:4700::1111"])),
            hostsEvidence: .clear)
        for language in ["en", "ar"] {
            let view = NSHostingView(rootView: ScrollView {
                NetworkSetupGuide(initialRoute: .routerDNS, initialDNSResult: result)
                    .padding(28)
            }
            .environment(\.locale, Locale(identifier: language))
            .environment(\.layoutDirection, language == "ar" ? .rightToLeft : .leftToRight)
            .frame(width: 520, height: 1100))
            let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 520, height: 1100),
                styleMask: [.titled], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.contentView = view
            defer { window.close() }
            window.orderFront(nil)
            try await Task.sleep(nanoseconds: 200_000_000)
            view.layoutSubtreeIfNeeded()
            let bitmap = try XCTUnwrap(view.bitmapImageRepForCachingDisplay(in: view.bounds))
            view.cacheDisplay(in: view.bounds, to: bitmap)
            XCTAssertGreaterThanOrEqual(bitmap.pixelsWide, 520)
            if ProcessInfo.processInfo.environment["HISN_CAPTURE_UI"] == "1" {
                // Let window/Stage Manager animations settle before recording
                // the test window. Capturing mid-animation distorts the image.
                NSApp.activate(ignoringOtherApps: true)
                window.makeKeyAndOrderFront(nil)
                try await Task.sleep(nanoseconds: 1_000_000_000)
                let directory = FileManager.default.temporaryDirectory.appendingPathComponent("hisn-ui-review")
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                // Preserve the view's text layout even when WindowServer gives
                // screencapture a transformed Stage Manager thumbnail.
                let layout = try XCTUnwrap(bitmap.representation(using: .png, properties: [:]))
                try layout.write(to: directory.appendingPathComponent("network-layout-\(language).png"))
                let capture = Process()
                capture.executableURL = URL(fileURLWithPath: "/usr/sbin/screencapture")
                capture.arguments = ["-x", "-l", String(window.windowNumber),
                    directory.appendingPathComponent("network-\(language).png").path]
                try capture.run()
                capture.waitUntilExit()
                XCTAssertEqual(capture.terminationStatus, 0)
            }
        }
    }
}
