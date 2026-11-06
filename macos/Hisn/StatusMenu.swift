import AppKit
import SwiftUI

/// Which page the main window shows, shared so the menu bar can open the
/// window on a particular page.
@MainActor
final class AppNavigation: ObservableObject {
    static let shared = AppNavigation()
    @Published var page: ContentView.Page? = AppNavigation.firstPage()
    /// A menu-bar request to open the site-check sheet on the next window
    /// appearance. The window consumes and clears it.
    @Published var siteCheckRequested = false
    /// A request from the Overview or Setup to open the private-window sheet.
    @Published var privateBrowsingRequested = false
    /// Re-open the short explanation from Settings, for someone who skipped it.
    @Published var welcomeRequested = false

    /// The window opens on the answer, not on the diagnostic list. Setup is
    /// one click away and the Overview's own next-step card leads there when
    /// something is missing; landing on twelve checks before the person knows
    /// what the app does was the first-run problem this fixes.
    static func firstPage(defaults: UserDefaults = .standard,
                          hostingTests: Bool = AppDelegate.isHostingTests) -> ContentView.Page {
        .overview
    }

    func open(_ page: ContentView.Page) {
        self.page = page
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

/// One site blocked from the menu bar: the moment someone wants a site gone
/// is rarely the moment they want to open the Rules page. It edits the same
/// guarded list, so it works during a lock and can never loosen one.
@MainActor
enum QuickBlock {
    static func promptAndAdd() {
        NSApp.activate(ignoringOtherApps: true)
        let alert = NSAlert()
        alert.messageText = String(localized: "Block a site")
        alert.informativeText = String(localized: "Enter a domain or paste a link. Subdomains are included, and adding a block works during a lock.")
        let field = NSTextField(frame: NSRect(x: 0, y: 0, width: 300, height: 24))
        field.placeholderString = "example.com"
        alert.accessoryView = field
        alert.window.initialFirstResponder = field
        alert.addButton(withTitle: String(localized: "Block"))
        alert.addButton(withTitle: String(localized: "Cancel"))
        guard alert.runModal() == .alertFirstButtonReturn else { return }

        let result = NSAlert()
        do {
            switch try SiteLists.addBlock(field.stringValue, locked: LockManager.shared.isLocked) {
            case .added(let host):
                FilterSync.soon()
                result.messageText = String(localized: "\(host) is now blocked.")
                result.informativeText = String(localized: "The filter and the browser extension pick it up within a few seconds. Remove it on the Blocking Rules page.")
            case .alreadyBlocked(let host):
                result.messageText = String(localized: "\(host) is already blocked.")
            case .invalid:
                result.messageText = String(localized: "That is not a domain.")
                result.informativeText = String(localized: "Try something like example.com, or paste the page's link.")
            }
        } catch {
            result.messageText = error.localizedDescription
        }
        result.runModal()
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
    /// The lock's time left, coarse enough for the menu bar: minute
    /// granularity, so it republishes at most once a minute. Nil while no
    /// lock runs, and the label then falls back to the app's name.
    @Published private(set) var remainingText: String?
    private var timer: Timer?

    var image: NSImage { isLocked ? Self.lockedImage : Self.unlockedImage }
    private static let unlockedImage = makeImage(locked: false)
    private static let lockedImage = makeImage(locked: true)

    /// The countdown the menu bar shows next to the shield while a lock
    /// runs — the one fact a glance can answer without opening anything.
    ///
    /// Rounded UP to the next whole minute, the way countdowns read: "2m"
    /// means "up to two minutes left", never "already over". Day/hour/
    /// minute only — a menu bar is not the place for seconds.
    static func coarseRemaining(seconds: TimeInterval) -> String? {
        guard seconds.isFinite, seconds > 0 else { return nil }
        let minutes = Int((seconds / 60).rounded(.up))
        let formatter = DateComponentsFormatter()
        formatter.unitsStyle = .abbreviated
        formatter.allowedUnits = [.day, .hour, .minute]
        formatter.maximumUnitCount = 2
        formatter.zeroFormattingBehavior = .dropLeading
        return formatter.string(from: DateComponents(minute: minutes))
    }

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
        // Coarse on purpose: the text changes at most once a minute, so the
        // poll every two seconds stays cheap and the menu bar never rebuilds
        // per second (see the type comment for why that once mattered).
        let text = next ? Self.coarseRemaining(seconds: LockManager.shared.remaining) : nil
        if text != remainingText { remainingText = text }
    }
}

/// The menu: the two answers the Overview gives — is it protecting, is it
/// locked — and the three ways in. Hiding the interface keeps protection on.
struct StatusMenu: View {
    @ObservedObject private var lock = LockManager.shared
    @ObservedObject private var filter = FilterController.shared
    @ObservedObject private var grayscale = ScreenGrayscale.shared
    @Environment(\.openWindow) private var openWindow

    private var managed: ManagedMode { ManagedMode.current() }

    var body: some View {
        let protection = ProtectionStatus(ProtectionEvidence.live(
            filter: filter.availability, authority: FilterSync.shared.status))
        Text(protection.headline)
        if managed.isManaged {
            Text(String(localized: "Managed by \(managed.displayName)"))
                .foregroundStyle(.secondary)
        }
        if lock.isLocked {
            Text(String(localized: "Locked · \(lock.remainingDescription) left"))
        } else {
            Text("No active lock")
            if let next = ScheduleStore.current().nextWindow(after: lock.now) {
                Text(String(localized: "Daily lock at \(next.start.formatted(date: .omitted, time: .shortened))"))
            }
        }
        if let tally = PauseStore.load(),
           tally.day == PauseTally.dayKey(for: Date()) {
            Text("Pauses today: \(tally.count)")
        }
        // A count from the filter process itself, not from the app: the same
        // XPC evidence the status uses. The filter records no host, app or
        // address — only this number — so showing it on a shared screen is
        // safe. Zero is left out: an empty day is not a fact worth a row.
        if let health = FilterSync.shared.status?.health,
           health.blockedToday > 0,
           health.blockedDay == BlockedFlowTally.dayKey(for: Date()) {
            Text("Blocked connections today: \(health.blockedToday)")
        }
        Divider()
        Button("Open Hisn") {
            MainWindow.show(.overview, open: openWindow)
            // The agent launches with no window. This is the first moment the
            // person asks to see the app, so it is the right moment for the
            // short first-run explanation if it has not been shown yet.
            if !UserDefaults.standard.bool(forKey: "hisn.welcome.completed") {
                AppNavigation.shared.welcomeRequested = true
            }
        }
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
        Button("Take a pause…") { PauseWindowController.shared.show() }
        Button("Block a site…") { QuickBlock.promptAndAdd() }
        Button("Check a website…") {
            AppNavigation.shared.siteCheckRequested = true
            MainWindow.show(.overview, open: openWindow)
        }
        if ScreenGrayscale.isAvailable {
            // A checkmark item. During a lock it can be ticked, not cleared.
            Toggle("Gray screen", isOn: Binding(
                get: { grayscale.isOn },
                set: { grayscale.set($0) }))
                .disabled(grayscale.isOn && (lock.isLocked || ManagedMode.current().isManaged))
        }
        Button("Setup") { MainWindow.show(.setup, open: openWindow) }
        Divider()
        Button("Hide Hisn") { MainWindow.hide() }
    }
}
