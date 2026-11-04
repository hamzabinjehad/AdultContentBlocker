import SwiftUI
import AppKit
import CoreServices

/// Keep ordinary Quit requests from stopping background protection, while
/// allowing the system to end a session and an unlocked language restart.
enum AppLifecyclePolicy {
    static func permitsTermination(hostingTests: Bool, endingSession: Bool,
                                   restarting: Bool, locked: @autoclosure () -> Bool) -> Bool {
        // Logout and test shutdown must not wait on lock stores or Keychain.
        hostingTests || endingSession || (restarting && !locked())
    }

    /// A managed duplicate must leave launchd ready to retry if the existing
    /// Finder-launched copy later stops. Manual duplicates can leave normally.
    static func duplicateExitStatus(managed: Bool) -> Int32 {
        managed ? EXIT_FAILURE : EXIT_SUCCESS
    }
}

@main
struct HisnApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var delegate
    @StateObject private var icon = MenuBarIcon.shared
    @Environment(\.openWindow) private var openWindow

    var body: some Scene {
        WindowGroup(id: MainWindow.id) { ContentView() }
            // Resizable, with a floor set by `ContentView`. The old fixed
            // 420-point column forced every explanation into caption-sized
            // text; this window is mostly explanations.
            .defaultSize(width: 980, height: 700)
            .commands {
                CommandGroup(replacing: .newItem) {}
                CommandMenu("Go") {
                    ForEach(ContentView.Page.allCases) { page in
                        Button(page.title) { MainWindow.show(page, open: openWindow) }
                            .keyboardShortcut(shortcut(for: page), modifiers: .command)
                    }
                }
                CommandMenu("Protection") {
                    Button("Check again") {
                        Task {
                            await FilterController.shared.refresh()
                            await FilterSync.shared.sync()
                        }
                    }
                    .keyboardShortcut("r", modifiers: .command)
                    Button("Take a pause…") { PauseWindowController.shared.show() }
                        .keyboardShortcut("p", modifiers: [.command, .shift])
                    Button("Check a website…") {
                        AppNavigation.shared.siteCheckRequested = true
                        MainWindow.show(.overview, open: openWindow)
                    }
                    .keyboardShortcut("k", modifiers: .command)
                    Button("Start a lock…") { MainWindow.show(.lock, open: openWindow) }
                        .keyboardShortcut("l", modifiers: [.command, .shift])
                }
                CommandGroup(replacing: .appTermination) {
                    Button("Hide Hisn") { MainWindow.hide() }
                        .keyboardShortcut("q")
                }
            }
        // Started at login the app has no window; this is the way back to it,
        // and the lock's time left without opening anything.
        MenuBarExtra {
            StatusMenu()
        } label: {
            Label {
                Text("Hisn")
            } icon: {
                Image(nsImage: icon.image)
            }
        }
    }

    private func shortcut(for page: ContentView.Page) -> KeyEquivalent {
        switch page {
        case .overview: return "1"
        case .setup:    return "2"
        case .rules:    return "3"
        case .lock:     return "4"
        case .settings: return "5"
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

    private var protectionActivity: NSObjectProtocol?
    static var restartRequested = false

    static var isAgentManaged: Bool {
        CommandLine.arguments.contains("--background")
            && CommandLine.arguments.contains("--for-user")
    }

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
        // One guard per app bundle. A managed duplicate exits unsuccessfully
        // so launchd retries: a Finder-launched copy must not permanently take
        // away supervision if it is later force-quit. Other accounts exited
        // successfully above and are never retried.
        if let other = NSRunningApplication.runningApplications(
                withBundleIdentifier: Bundle.main.bundleIdentifier ?? "")
            .first(where: { $0.processIdentifier != getpid()
                            && $0.bundleURL?.standardizedFileURL
                               == Bundle.main.bundleURL.standardizedFileURL }) {
            if !Self.isAgentManaged { other.activate() }
            exit(AppLifecyclePolicy.duplicateExitStatus(managed: Self.isAgentManaged))
        }
        // Hidden windows must not let App Nap defer browser checks or daily
        // lock timers. This activity still allows normal system sleep.
        protectionActivity = ProcessInfo.processInfo.beginActivity(
            options: .userInitiatedAllowingIdleSystemSleep,
            reason: "Maintain browser protection and scheduled locks")
        NativeMessagingInstaller.installIfNeeded()
        AppLanguage.publish()
        BrowserGuard.shared.start()
        FilterSync.shared.start()
        Task { @MainActor in ScreenGrayscale.shared.start() }
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

    /// Closing the window never quits. It used to, whenever no lock ran — and
    /// started at login with its window closed, the app then exited at once,
    /// so the daily lock had no process to start it and the menu bar icon was
    /// gone. Hisn stays in the background, and the window comes back from the
    /// menu bar or the Dock.
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        false
    }

    /// Ordinary Quit closes the interface while protection keeps running.
    /// Logout, restart and shutdown remain available; the installed agent
    /// restores the app after a crash or force-quit.
    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        let endingSession = Self.systemIsEndingSession
        if AppLifecyclePolicy.permitsTermination(hostingTests: Self.isHostingTests,
                endingSession: endingSession,
                restarting: Self.restartRequested, locked: EffectiveLock.isLocked) {
            if endingSession { Self.restartRequested = false }
            return .terminateNow
        }
        Self.restartRequested = false
        MainWindow.hide()
        return .terminateCancel
    }

    func applicationWillTerminate(_ notification: Notification) {
        if let protectionActivity {
            ProcessInfo.processInfo.endActivity(protectionActivity)
            self.protectionActivity = nil
        }
        // A successful exit would stand the agent down until the next login.
        // A controlled language restart keeps the replacement supervised.
        if !Self.isHostingTests, Self.restartRequested, Self.isAgentManaged {
            exit(EXIT_FAILURE)
        }
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
