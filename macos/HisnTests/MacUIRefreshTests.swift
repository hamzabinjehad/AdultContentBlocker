import AppKit
import SwiftUI
import XCTest
@testable import Hisn

/// Layout captures for the Mac UI pass.
///
/// These are design-review artifacts, not pixel assertions: they render every
/// page at a comfortable window size in both appearances so a change to the
/// shared palette, a card or a page header can be looked at before it ships.
/// The view bitmap is always produced; the window capture is best-effort
/// because a headless runner may not have screen-recording permission.
@MainActor
final class MacUIRefreshTests: XCTestCase {
    private let directory = URL(fileURLWithPath: "/tmp/hisn-mac-ui", isDirectory: true)
    private let size = NSSize(width: 1120, height: 860)

    /// Render at one point per pixel. The window's backing store is 2x on a
    /// retina Mac; capturing every page at 2x doubled the pixels and most of
    /// this suite's runtime for no review benefit.
    private func png(of view: NSView) throws -> Data {
        let rep = try XCTUnwrap(NSBitmapImageRep(
            bitmapDataPlanes: nil,
            pixelsWide: Int(view.bounds.width),
            pixelsHigh: Int(view.bounds.height),
            bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
            colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0))
        rep.size = view.bounds.size
        view.cacheDisplay(in: view.bounds, to: rep)
        return try XCTUnwrap(rep.representation(using: .png, properties: [:]))
    }

    func testEveryPageRendersForVisualReview() async throws {
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let original = AppNavigation.shared.page
        defer { AppNavigation.shared.page = original }

        // One window per appearance, then switch the page inside it. A fresh
        // hosting view for every capture spent more time in setup than in
        // rendering, and the suite pays for this test on every run.
        for dark in [false, true] {
            let host = NSHostingView(rootView: ContentView()
                .environment(\.locale, Locale(identifier: "en"))
                .environment(\.layoutDirection, .leftToRight)
                .frame(width: size.width, height: size.height))
            let window = NSWindow(
                contentRect: NSRect(origin: .zero, size: size),
                styleMask: [.titled, .closable, .resizable, .fullSizeContentView],
                backing: .buffered, defer: false)
            window.title = "Hisn"
            window.isReleasedWhenClosed = false
            window.appearance = NSAppearance(named: dark ? .darkAqua : .aqua)
            window.contentView = host
            defer { window.close() }
            window.orderFront(nil)
            try await Task.sleep(nanoseconds: 300_000_000)

            for page in ContentView.Page.allCases {
                AppNavigation.shared.page = page
                try await Task.sleep(nanoseconds: 300_000_000)
                host.layoutSubtreeIfNeeded()
                let name = "\(dark ? "dark" : "light")-\(page.rawValue)"
                let data = try png(of: host)
                XCTAssertGreaterThan(data.count, 2000)
                try data.write(to: directory.appendingPathComponent("\(name)-view.png"))
            }
            window.close()
        }
    }

    /// The macOS 12 fallbacks — the hand-built split, the row stack instead of
    /// `Grid`, the measured chip flow, the cleared text backgrounds — rendered
    /// on a newer Mac by forcing the legacy branch. Same review artifact as the
    /// pages above: a fallback that only has to compile is a fallback nobody
    /// has looked at.
    func testLegacyPagesRenderForVisualReview() async throws {
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let original = AppNavigation.shared.page
        HnCompat.forceLegacy = true
        defer {
            HnCompat.forceLegacy = false
            AppNavigation.shared.page = original
        }

        let host = NSHostingView(rootView: ContentView()
            .environment(\.locale, Locale(identifier: "en"))
            .environment(\.layoutDirection, .leftToRight)
            .frame(width: size.width, height: size.height))
        let window = NSWindow(
            contentRect: NSRect(origin: .zero, size: size),
            styleMask: [.titled, .closable, .resizable, .fullSizeContentView],
            backing: .buffered, defer: false)
        window.title = "Hisn"
        window.isReleasedWhenClosed = false
        window.contentView = host
        defer { window.close() }
        window.orderFront(nil)
        try await Task.sleep(nanoseconds: 400_000_000)

        for page in ContentView.Page.allCases {
            AppNavigation.shared.page = page
            try await Task.sleep(nanoseconds: 400_000_000)
            host.layoutSubtreeIfNeeded()
            let data = try png(of: host)
            XCTAssertGreaterThan(data.count, 2000)
            try data.write(to: directory.appendingPathComponent("legacy-\(page.rawValue)-view.png"))
        }
    }

    /// SwiftUI installs the window toolbar from the view tree, so the macOS 12
    /// `HSplitView` path must keep the search and lock buttons that the
    /// macOS 13 `NavigationSplitView` path shows in the title bar. Only the
    /// sidebar toggle and its separator are allowed to differ — they are the
    /// `NavigationSplitView` chrome, and an `HSplitView` cannot collapse.
    func testTheToolbarButtonsSurviveTheMacOS12Split() async throws {
        func toolbarButtons(legacy: Bool) async throws -> [String] {
            HnCompat.forceLegacy = legacy
            defer { HnCompat.forceLegacy = false }
            let host = NSHostingView(rootView: ContentView()
                .environment(\.locale, Locale(identifier: "en"))
                .frame(width: 900, height: 700))
            let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 900, height: 700),
                                  styleMask: [.titled, .closable, .resizable],
                                  backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.contentView = host
            defer { window.close() }
            window.orderFront(nil)
            try await Task.sleep(nanoseconds: 400_000_000)
            // The app's own buttons are the items with generated identifiers;
            // the rest are AppKit's and SwiftUI's title-bar chrome.
            return (window.toolbar?.items ?? [])
                .map(\.itemIdentifier.rawValue)
                .filter { !$0.hasPrefix("NSToolbar") && !$0.hasPrefix("com.apple.") }
        }

        let modern = try await toolbarButtons(legacy: false)
        let legacy = try await toolbarButtons(legacy: true)
        XCTAssertEqual(modern.count, 2, "expected search and lock buttons on the current path")
        XCTAssertEqual(legacy.count, modern.count, "the macOS 12 split lost toolbar buttons")
    }

    func testOverviewRendersAtTheMinimumWidth() async throws {
        let original = AppNavigation.shared.page
        defer { AppNavigation.shared.page = original }
        AppNavigation.shared.page = .overview
        let host = NSHostingView(rootView: ContentView()
            .environment(\.locale, Locale(identifier: "en"))
            .environment(\.layoutDirection, .leftToRight)
            .frame(width: 760, height: 900))
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 760, height: 900),
                              styleMask: [.titled], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.contentView = host
        defer { window.close() }
        window.orderFront(nil)
        try await Task.sleep(nanoseconds: 400_000_000)
        host.layoutSubtreeIfNeeded()
        let data = try png(of: host)
        XCTAssertGreaterThan(data.count, 2000)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try data.write(to: directory.appendingPathComponent("min-width-overview.png"))
    }

    /// Setup is the longest page; the pages capture above runs it without a
    /// checklist in the test host. This one injects evidence so the stage
    /// cards, the expanded installation steps and the network guide are all in
    /// the frame.
    func testSetupWithEvidenceRendersForReview() async throws {
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let name = TestNamespace.make()
        defer { TestNamespace.dispose(name) }
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        let evidence = SetupEvidence(isAdmin: true, hostsEntries: nil, partnerKeySet: false,
                                     browsers: [BrowserSetup(name: "Chrome", extensionOffIn: ["Default"]),
                                                BrowserSetup(name: "Helium")],
                                     privateRelayOff: false, screenTimeAdultFilter: false,
                                     systemFilterRunning: false, appFilesProtected: nil)
        for dark in [false] {
            let view = NSHostingView(rootView: ScrollView {
                SetupPage(filter: .shared, goTo: { _ in },
                          initialChecklist: SetupChecklist(evidence),
                          confirmationDefaults: defaults,
                          initialExpandedStages: [.installation])
                    .padding(32)
            }
            .background(HnPalette.canvas)
            .frame(width: 1120, height: 1100))
            let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 1120, height: 1100),
                                  styleMask: [.titled], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.appearance = NSAppearance(named: dark ? .darkAqua : .aqua)
            window.contentView = view
            defer { window.close() }
            window.orderFront(nil)
            try await Task.sleep(nanoseconds: 450_000_000)
            view.layoutSubtreeIfNeeded()
            let data = try png(of: view)
            XCTAssertGreaterThan(data.count, 2000)
            try data.write(to: directory.appendingPathComponent("\(dark ? "dark" : "light")-setup-evidence.png"))
            window.close()
        }
    }

    /// The Arabic window is not a translation of the English layout: the
    /// sidebar, the badge, the ring and the two actions all read right to
    /// left. This is a layout capture, not a translation audit — the XLIFF
    /// check owns completeness.
    func testOverviewAndLockRenderInArabic() async throws {
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let original = AppNavigation.shared.page
        defer { AppNavigation.shared.page = original }
        let host = NSHostingView(rootView: ContentView()
            .environment(\.locale, Locale(identifier: "ar"))
            .environment(\.layoutDirection, .rightToLeft)
            .frame(width: size.width, height: size.height))
        let window = NSWindow(contentRect: NSRect(origin: .zero, size: size),
                              styleMask: [.titled, .closable, .resizable],
                              backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.appearance = NSAppearance(named: .aqua)
        window.contentView = host
        defer { window.close() }
        window.orderFront(nil)
        try await Task.sleep(nanoseconds: 300_000_000)
        for page in [ContentView.Page.overview, .lock, .rules] {
            AppNavigation.shared.page = page
            try await Task.sleep(nanoseconds: 300_000_000)
            host.layoutSubtreeIfNeeded()
            let data = try png(of: host)
            XCTAssertGreaterThan(data.count, 2000)
            try data.write(to: directory.appendingPathComponent("arabic-\(page.rawValue).png"))
        }
        window.close()
    }

    /// The new first-run decision and the chip picker, in both directions.
    /// The capture is deliberately taken with a few chips selected so the
    /// selected, unselected and parent-covered states can all be reviewed.
    func testExtraSitesRenderForReview() async throws {
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        for language in ["en", "ar"] {
            let host = NSHostingView(rootView: WelcomeView(initialStep: 1)
                .environment(\.locale, Locale(identifier: language))
                .environment(\.layoutDirection, language == "ar" ? .rightToLeft : .leftToRight)
                .frame(width: 600, height: 560))
            let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 640, height: 600),
                                  styleMask: [.titled], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.contentView = host
            defer { window.close() }
            window.orderFront(nil)
            try await Task.sleep(nanoseconds: 350_000_000)
            host.layoutSubtreeIfNeeded()
            let data = try png(of: host)
            XCTAssertGreaterThan(data.count, 2000)
            try data.write(to: directory.appendingPathComponent("extra-sites-\(language).png"))
            window.close()
        }

        for language in ["en", "ar"] {
            let picker = ExtraSitesPicker(
                blocks: ["youtube.com", "reddit.com", "x.com", "ycombinator.com"],
                isLocked: false) { _ in }
            let host = NSHostingView(rootView: HnCard { picker.padding(4) }
                .environment(\.locale, Locale(identifier: language))
                .environment(\.layoutDirection, language == "ar" ? .rightToLeft : .leftToRight)
                .frame(width: 760, height: 240)
                .padding(24))
            let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 808, height: 288),
                                  styleMask: [.titled], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.contentView = host
            defer { window.close() }
            window.orderFront(nil)
            try await Task.sleep(nanoseconds: 300_000_000)
            host.layoutSubtreeIfNeeded()
            let data = try png(of: host)
            XCTAssertGreaterThan(data.count, 2000)
            try data.write(to: directory.appendingPathComponent("extra-sites-picker-\(language).png"))
            window.close()
        }
    }

    func testSheetsRenderForReview() async throws {
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let cases: [(String, AnyView, NSSize)] = [
            ("welcome-en", AnyView(WelcomeView()
                .environment(\.locale, Locale(identifier: "en"))), NSSize(width: 640, height: 560)),
            ("welcome-ar", AnyView(WelcomeView()
                .environment(\.locale, Locale(identifier: "ar"))
                .environment(\.layoutDirection, .rightToLeft)), NSSize(width: 640, height: 560)),
            ("private-en", AnyView(PrivateBrowsingSheet()
                .environment(\.locale, Locale(identifier: "en"))), NSSize(width: 600, height: 560)),
            ("pause-en", AnyView(PauseView(onClose: {})), NSSize(width: 460, height: 640)),
        ]
        for (name, view, sheetSize) in cases {
            let host = NSHostingView(rootView: view.frame(width: sheetSize.width, height: sheetSize.height))
            let window = NSWindow(contentRect: NSRect(origin: .zero, size: sheetSize),
                                  styleMask: [.titled], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.contentView = host
            defer { window.close() }
            window.orderFront(nil)
            try await Task.sleep(nanoseconds: 350_000_000)
            host.layoutSubtreeIfNeeded()
            let data = try png(of: host)
            XCTAssertGreaterThan(data.count, 2000)
            try data.write(to: directory.appendingPathComponent("\(name).png"))
            window.close()
        }
    }
}
