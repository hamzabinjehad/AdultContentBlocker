import AppKit
import SwiftUI
import XCTest
@testable import Hisn

/// The pieces that let the app build and run on macOS 12: the lock the newer
/// OS ships, and the two questions the newer OS answers for us.
final class CompatibilityTests: XCTestCase {

    /// `OSAllocatedUnfairLock` is macOS 13; this stands in for it in the filter's
    /// per-flow path, so it has to actually exclude — an increment lost here
    /// would be a wrong answer on a connection.
    func testTheLockSerialisesConcurrentMutation() {
        let lock = HnLock(initialState: 0)
        DispatchQueue.concurrentPerform(iterations: 2_000) { _ in
            lock.withLock { $0 += 1 }
        }
        XCTAssertEqual(lock.withLock { $0 }, 2_000)
    }

    func testTheVoidLockRunsTheBodyOnceAndReturnsItsValue() {
        let lock = HnLock()
        var runs = 0
        let value: Int = lock.withLock {
            runs += 1
            return 42
        }
        XCTAssertEqual(runs, 1)
        XCTAssertEqual(value, 42)
    }

    func testTheVoidLockAlsoHasTheUncheckedSpelling() {
        let lock = HnLock()
        var runs = 0
        lock.withLockUnchecked { runs += 1 }
        XCTAssertEqual(runs, 1)
    }

    /// `Locale.region` is macOS 13; the identifier it is built from is not.
    func testTheRegionComesFromTheLocaleIdentifier() {
        XCTAssertEqual(AppLanguage.regionCode(from: "en_US"), "US")
        XCTAssertEqual(AppLanguage.regionCode(from: "ar_SA"), "SA")
        XCTAssertEqual(AppLanguage.regionCode(from: "en_US@calendar=gregorian"), "US")
        XCTAssertNil(AppLanguage.regionCode(from: "en"))
        XCTAssertNil(AppLanguage.regionCode(from: ""))
    }

    /// The macOS 12 branch of the filter's XPC admission check. Nothing the
    /// test host can prove should be accepted: it is ad-hoc signed, so a team
    /// requirement must fail, and a requirement that does not parse must fail.
    func testThePeerCheckAcceptsNothingItCannotProve() {
        XCTAssertFalse(FilterXPC.process(pid: 0, satisfies: "anchor apple generic"))
        XCTAssertFalse(FilterXPC.process(pid: -1, satisfies: "anchor apple generic"))
        XCTAssertFalse(FilterXPC.process(pid: getpid(), satisfies: "anchor apple generic"))
        XCTAssertFalse(FilterXPC.process(pid: getpid(), satisfies: "not a requirement ("))
    }
}

/// The close button hides the main window instead of destroying it — macOS 12
/// has no `openWindow` with which to bring a closed `WindowGroup` window back.
@MainActor
final class MainWindowCloseProxyTests: XCTestCase {
    private final class ForwardedDelegate: NSObject, NSWindowDelegate {
        var becameKey = 0

        func windowDidBecomeKey(_ notification: Notification) {
            becameKey += 1
        }
    }

    func testClosingHidesTheWindow() {
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 320, height: 200),
                              styleMask: [.titled, .closable, .resizable],
                              backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFront(nil)
        defer { window.close() }
        let proxy = MainWindowCloseProxy(forwarding: nil)

        XCTAssertTrue(window.isVisible)
        XCTAssertFalse(proxy.windowShouldClose(window))
        XCTAssertFalse(window.isVisible)
    }

    /// `MainWindow.adopt` hangs off this probe: it is the only way to reach
    /// the window SwiftUI made, and without it closing would still destroy the
    /// window on every macOS version.
    func testTheWindowProbeFindsItsWindow() async throws {
        var found: NSWindow?
        let host = NSHostingView(rootView: Color.clear
            .background(HnWindowReader { found = $0 }))
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 320, height: 200),
                              styleMask: [.titled, .closable], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.contentView = host
        defer { window.close() }
        window.orderFront(nil)
        try await Task.sleep(nanoseconds: 300_000_000)
        XCTAssertTrue(found === window)
    }

    func testEverythingElseReachesSwiftUIsDelegate() {
        let forwarded = ForwardedDelegate()
        let proxy = MainWindowCloseProxy(forwarding: forwarded)
        let becameKey = #selector(NSWindowDelegate.windowDidBecomeKey(_:))

        XCTAssertTrue(proxy.responds(to: becameKey))
        XCTAssertTrue((proxy.forwardingTarget(for: becameKey) as AnyObject?) === forwarded)
        XCTAssertFalse(proxy.responds(to: #selector(NSWindowDelegate.windowDidResize(_:))))
    }
}
