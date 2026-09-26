import AppKit
import SwiftUI

/// Which page the main window shows, shared so the menu bar can open the
/// window on a particular page.
@MainActor
final class AppNavigation: ObservableObject {
    static let shared = AppNavigation()
    @Published var page: ContentView.Page? = .overview
}

/// The main window, as distinct from the guard's countdown panel and the
/// menu bar item's own window — neither of which may be closed or brought
/// forward as if it were the app.
enum MainWindow {
    static let id = "main"

    static var all: [NSWindow] {
        NSApp.windows.filter { !($0 is NSPanel) && $0.canBecomeMain }
    }

    /// Bring the existing window forward, or open one: a second window over
    /// the same state would only confuse.
    @MainActor static func show(_ page: ContentView.Page, open: OpenWindowAction) {
        AppNavigation.shared.page = page
        if let window = all.first {
            window.makeKeyAndOrderFront(nil)
        } else {
            open(id: id)
        }
        NSApp.activate(ignoringOtherApps: true)
    }
}

/// What the menu bar icon shows: a shield, a closed lock while a lock runs.
///
/// Its own object, publishing only when that changes. The icon once observed
/// LockManager directly, which publishes every second for the countdown; a
/// menu bar label rebuilt every second from the first frame kept the app from
/// ever finishing its launch — no window, no icon, the process idling.
@MainActor
final class MenuBarIcon: ObservableObject {
    static let shared = MenuBarIcon()
    @Published private(set) var symbol = "shield.lefthalf.filled"
    private var timer: Timer?

    private init() {
        update()
        timer = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.update() }
        }
    }

    private func update() {
        let next = LockManager.shared.isLocked ? "lock.shield.fill" : "shield.lefthalf.filled"
        if next != symbol { symbol = next }
    }
}

/// The menu: the two answers the Overview gives — is it protecting, is it
/// locked — and the three ways in. No Quit during a lock, the same rule as
/// the app menu's.
struct StatusMenu: View {
    @ObservedObject private var lock = LockManager.shared
    @ObservedObject private var filter = FilterController.shared
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        let protection = ProtectionStatus(ProtectionEvidence.current(
            filter: filter.availability, authority: FilterSync.shared.status))
        Text(protection.headline)
        if lock.isLocked {
            Text(String(localized: "Locked · \(lock.remainingDescription) left"))
        } else {
            Text("No active lock")
            if let next = ScheduleStore.current().nextWindow(after: lock.now) {
                Text(String(localized: "Daily lock at \(next.start.formatted(date: .omitted, time: .shortened))"))
            }
        }
        Divider()
        Button("Open Hisn") { MainWindow.show(.overview, open: openWindow) }
        if !lock.isLocked {
            Button("Start a lock…") { MainWindow.show(.lock, open: openWindow) }
        }
        Button("Setup") { MainWindow.show(.setup, open: openWindow) }
        // LockManager's answer, not EffectiveLock's: the same facts (mirrors
        // or authority), already read by its tick, where EffectiveLock would
        // read all three mirrors, the Keychain included, on every refresh.
        if !lock.isLocked {
            Divider()
            Button("Quit Hisn") { NSApp.terminate(nil) }
        }
    }
}
