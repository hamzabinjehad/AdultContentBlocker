import AppKit
import SwiftUI

/// Which page the main window shows, shared so the menu bar can open the
/// window on a particular page.
@MainActor
final class AppNavigation: ObservableObject {
    static let shared = AppNavigation()
    @Published var page: ContentView.Page? = AppNavigation.firstPage()

    private static let seenSetupKey = "hisn.openedOnSetup"

    /// The very first window opens on Setup: someone who has just installed
    /// Hisn needs the list of what is left, not a status saying most of it is
    /// missing. Every later window opens on the Overview.
    static func firstPage(defaults: UserDefaults = .standard,
                          hostingTests: Bool = AppDelegate.isHostingTests) -> ContentView.Page {
        guard !hostingTests, !defaults.bool(forKey: seenSetupKey) else { return .overview }
        return .setup
    }

    func markSetupSeen() {
        guard !AppDelegate.isHostingTests else { return }
        UserDefaults.standard.set(true, forKey: Self.seenSetupKey)
    }

    func open(_ page: ContentView.Page) {
        self.page = page == .overview && !UserDefaults.standard.bool(forKey: Self.seenSetupKey)
            ? .setup : page
        markSetupSeen()
    }
}

/// The main window, as distinct from the guard's countdown panel and the
/// menu bar item's own window — neither of which may be closed or brought
/// forward as if it were the app.
enum MainWindow {
    static let id = "main"

    static var all: [NSWindow] {
        NSApp.windows.filter { !($0 is NSPanel) && $0.canBecomeMain }
    }

    @MainActor static func hide() {
        // Keep the browser guard's warning panel visible above other apps.
        // Hiding the entire application would hide that countdown too.
        all.forEach { $0.close() }
    }

    /// Bring the existing window forward, or open one: a second window over
    /// the same state would only confuse.
    @MainActor static func show(_ page: ContentView.Page, open: OpenWindowAction) {
        AppNavigation.shared.open(page)
        if let window = all.first {
            window.makeKeyAndOrderFront(nil)
        } else {
            open(id: id)
        }
        NSApp.activate(ignoringOtherApps: true)
    }
}

/// A lock started from the menu bar, confirmed in a dialog that names the
/// moment it ends — the same fact the Lock page's confirmation gives, since a
/// lock cannot be shortened once it starts.
@MainActor
enum QuickLock {
    static let lengths: [TimeInterval] = [3600, 86400, 7 * 86400]

    static func confirmAndStart(_ seconds: TimeInterval) {
        NSApp.activate(ignoringOtherApps: true)
        let end = Date().addingTimeInterval(seconds).formatted()
        let alert = NSAlert()
        alert.messageText = String(localized: "Start a lock of \(LockManager.describe(seconds))?")
        alert.informativeText = String(localized: "You will not be able to turn this off until \(end).")
        alert.addButton(withTitle: String(localized: "Start the lock"))
        alert.addButton(withTitle: String(localized: "Not yet"))
        guard alert.runModal() == .alertFirstButtonReturn else { return }
        Task {
            do {
                try await LockManager.shared.start(seconds: seconds, strict: false)
            } catch {
                let failed = NSAlert(error: error)
                failed.runModal()
            }
        }
    }
}

/// Hisn's shield and gateway, with a small badge while a lock runs.
///
/// Its own object, publishing only when that changes. The icon once observed
/// LockManager directly, which publishes every second for the countdown; a
/// menu bar label rebuilt every second from the first frame kept the app from
/// ever finishing its launch — no window, no icon, the process idling.
@MainActor
final class MenuBarIcon: ObservableObject {
    static let shared = MenuBarIcon()
    @Published private(set) var isLocked = false
    private var timer: Timer?

    var image: NSImage { isLocked ? Self.lockedImage : Self.unlockedImage }
    private static let unlockedImage = makeImage(locked: false)
    private static let lockedImage = makeImage(locked: true)

    /// A single template image lets MenuBarExtra tint the mark and badge
    /// together. Both states have the same size, so nearby items stay put.
    /// Cache them rather than redrawing on every lock-status poll.
    private static func makeImage(locked: Bool) -> NSImage {
        guard let mark = NSImage(named: "HisnMenuBar") else {
            assertionFailure("Missing HisnMenuBar image asset")
            return NSImage(systemSymbolName: "shield.fill", accessibilityDescription: nil)
                ?? NSImage()
        }
        let badge = NSImage(systemSymbolName: "lock.fill", accessibilityDescription: nil)?
            .withSymbolConfiguration(.init(paletteColors: [.black]))
        let image = NSImage(size: NSSize(width: 22, height: 20), flipped: false) { _ in
            mark.draw(in: NSRect(x: 0, y: 0, width: 20, height: 20))
            if locked, let badge, let context = NSGraphicsContext.current?.cgContext {
                // Clear space around the badge instead of painting a background
                // that would disagree with the menu bar in another appearance.
                context.saveGState()
                context.setBlendMode(.clear)
                NSBezierPath(roundedRect: NSRect(x: 14, y: 0, width: 8, height: 10),
                             xRadius: 2, yRadius: 2).fill()
                context.restoreGState()
                badge.draw(in: NSRect(x: 15, y: 0, width: 7, height: 9))
            }
            return true
        }
        image.isTemplate = true
        return image
    }

    private init() {
        update()
        timer = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.update() }
        }
    }

    private func update() {
        let next = LockManager.shared.isLocked
        if next != isLocked { isLocked = next }
    }
}

/// The menu: the two answers the Overview gives — is it protecting, is it
/// locked — and the three ways in. Hiding the interface keeps protection on.
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
            // The moment a lock matters most is the moment it is hardest to
            // go and set one up: one click from the menu bar, then a
            // confirmation that names the end.
            Menu("Lock now") {
                ForEach(QuickLock.lengths, id: \.self) { seconds in
                    Button(LockManager.describe(seconds)) { QuickLock.confirmAndStart(seconds) }
                }
            }
            Button("Start a lock…") { MainWindow.show(.lock, open: openWindow) }
        }
        Button("Setup") { MainWindow.show(.setup, open: openWindow) }
        Divider()
        Button("Hide Hisn") { MainWindow.hide() }
    }
}
