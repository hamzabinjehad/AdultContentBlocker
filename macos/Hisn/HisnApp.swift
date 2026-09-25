import SwiftUI

@main
struct HisnApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup { ContentView() }
            // Resizable, with a floor set by `ContentView`. The old fixed
            // 420-point column forced every explanation into caption-sized
            // text; this window is mostly explanations.
            .defaultSize(width: 880, height: 600)
            .commands { CommandGroup(replacing: .newItem) {} }
    }
}

/// Launch-time work.
///
/// The important line is `reassertIfNeeded`. The single most common way a lock
/// silently stops working is that the filter got disabled — by a macOS update,
/// by a crash, or by someone toggling it in System Settings — and nobody
/// noticed. Re-arming on every launch turns a permanent hole into a gap that
/// closes the next time the app opens.
///
/// `installIfNeeded` belongs in the same list for the same reason: a browser
/// extension that can never reach this app is a permanent hole too, just a
/// quieter one — nothing crashes, nothing errors, blocking simply never turns
/// on. See `NativeMessagingInstaller` for why this cannot be a one-time setup
/// step.
final class AppDelegate: NSObject, NSApplicationDelegate {

    /// True when this process is the host for the unit tests. None of the
    /// launch work below may run then: it rewrote the browsers' real
    /// native-messaging manifests to point at the throwaway test build, and
    /// started a browser guard that judged this Mac's browsers against the
    /// short locks the tests write.
    static var isHostingTests: Bool {
        ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
            || NSClassFromString("XCTestCase") != nil
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        guard !Self.isHostingTests else { return }
        NativeMessagingInstaller.installIfNeeded()
        BrowserGuard.shared.start()
        FilterSync.shared.start()
        Task { @MainActor in
            await FilterController.shared.reassertIfNeeded()
            await ListUpdater.shared.updateIfStale()
        }
    }

    /// Quitting must not look like a way out. The lock lives in the filter and
    /// on disk, not in this process, but a user who quits the app and sees the
    /// menu bar item vanish will assume otherwise — so keep it running.
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        !LockStore.isLocked()
    }
}
