import SwiftUI
import AppKit
import CoreServices

@main
struct HisnApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var delegate
    @StateObject private var icon = MenuBarIcon.shared

    var body: some Scene {
        WindowGroup(id: MainWindow.id) { ContentView() }
            // Resizable, with a floor set by `ContentView`. The old fixed
            // 420-point column forced every explanation into caption-sized
            // text; this window is mostly explanations.
            .defaultSize(width: 880, height: 600)
            .commands { CommandGroup(replacing: .newItem) {} }
        // Started at login the app has no window; this is the way back to it,
        // and the lock's time left without opening anything.
        MenuBarExtra(String(localized: "Hisn"), systemImage: icon.symbol) {
            StatusMenu()
        }
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
        // The login agent lives in /Library/LaunchAgents, which loads in every
        // account — the partner's administrator account too. It names the
        // account it protects; anywhere else, leave quietly. A successful
        // exit, so KeepAlive does not bring it back.
        let args = CommandLine.arguments
        if let i = args.firstIndex(of: "--for-user"), i + 1 < args.count,
           args[i + 1] != NSUserName() {
            exit(0)
        }
        // One copy of this app bundle at a time: the LaunchAgent starts one at
        // login, and a second from Finder would run a second guard. A build of
        // Hisn from elsewhere (Xcode) is a different bundle and may coexist.
        if let other = NSRunningApplication.runningApplications(
                withBundleIdentifier: Bundle.main.bundleIdentifier ?? "")
            .first(where: { $0.processIdentifier != getpid()
                            && $0.bundleURL?.standardizedFileURL
                               == Bundle.main.bundleURL.standardizedFileURL }) {
            other.activate()
            exit(0)
        }
        NativeMessagingInstaller.installIfNeeded()
        AppLanguage.publish()
        BrowserGuard.shared.start()
        FilterSync.shared.start()
        Task { @MainActor in
            await FilterController.shared.reassertIfNeeded()
            await ListUpdater.shared.updateIfStale()
        }
        // Started at login by the LaunchAgent (`macos/install.sh`): run the
        // guard and the updater without putting a window in front of anyone.
        if CommandLine.arguments.contains("--background") {
            // Not the guard's countdown panel, which may already be up.
            DispatchQueue.main.async {
                MainWindow.all.forEach { $0.close() }
            }
        }
    }

    /// Quitting must not look like a way out. The lock lives in the filter and
    /// on disk, not in this process, but a user who quits the app and sees the
    /// menu bar item vanish will assume otherwise — so keep it running.
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        !EffectiveLock.isLocked
    }

    /// During a lock this process is the browser guard, so Quit is refused.
    /// A logout, restart or shutdown is always let through — refusing those
    /// would hold the whole Mac hostage — and a force-quit cannot be refused
    /// at all; the LaunchAgent's KeepAlive brings the app straight back.
    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        guard !Self.isHostingTests, EffectiveLock.isLocked, !Self.systemIsEndingSession else {
            return .terminateNow
        }
        let alert = NSAlert()
        alert.messageText = String(localized: "Hisn keeps running during a lock")
        alert.informativeText = String(localized: """
            While a lock runs, Hisn watches that every browser \
            has its protection on. Close the window instead — Hisn stays in \
            the background.
            """)
        alert.addButton(withTitle: String(localized: "OK"))
        alert.runModal()
        MainWindow.all.forEach { $0.close() }
        return .terminateCancel
    }

    /// Whether this quit comes from the system ending the session.
    static var systemIsEndingSession: Bool {
        guard let event = NSAppleEventManager.shared().currentAppleEvent,
              event.eventID == AEEventID(kAEQuitApplication),
              let reason = event.attributeDescriptor(forKeyword: AEKeyword(kAEQuitReason))?
                  .enumCodeValue
        else { return false }
        return [kAELogOut, kAEReallyLogOut, kAEShowRestartDialog, kAEShowShutdownDialog,
                kAERestart, kAEShutDown].map { OSType($0) }.contains(reason)
    }
}
