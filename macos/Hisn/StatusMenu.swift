import AppKit
import Combine
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

/// The main window, as distinct from the guard's countdown panel and the menu
/// bar item's own menu — neither of which may be closed or brought forward as
/// if it were the app.
///
/// The window is opened once and hidden, never closed. macOS 13's SwiftUI has
/// `openWindow` to bring back a window that was closed; macOS 12 has no such
/// call, and `WindowGroup` does not promise to remake a destroyed window. So
/// the close button hides the window instead (see `MainWindowCloseProxy`), the
/// window outlives every close, and every "open" path is the same
/// `makeKeyAndOrderFront` on either macOS version.
enum MainWindow {
    /// Whether the first launch has already been given the design's window
    /// size. Set once, so a frame the person resized is never overridden.
    static let framedDefaultsKey = "hisn.window.framed"

    private static weak var adoptedWindow: NSWindow?
    /// `NSWindow.delegate` is weak; without this the proxy would be released
    /// immediately and the close button would go back to destroying the window.
    private static var closeProxy: MainWindowCloseProxy?

    static var all: [NSWindow] {
        NSApp.windows.filter { !($0 is NSPanel) && $0.canBecomeMain }
    }

    /// Take over the window SwiftUI created: give the first launch the room
    /// the design asks for (`.defaultSize` is macOS 13), and make closing hide
    /// rather than destroy it.
    @MainActor
    static func adopt(_ window: NSWindow) {
        // The layout tests host `ContentView` in throwaway windows at chosen
        // sizes; adopting one would resize it and install a close button
        // policy the test is not asking about.
        guard !AppDelegate.isHostingTests else { return }
        guard adoptedWindow !== window else { return }
        adoptedWindow = window

        let defaults = UserDefaults.standard
        if !defaults.bool(forKey: framedDefaultsKey) {
            defaults.set(true, forKey: framedDefaultsKey)
            if window.frame.width < 900 || window.frame.height < 640 {
                window.setContentSize(NSSize(width: 980, height: 700))
                window.center()
            }
        }

        if !(window.delegate is MainWindowCloseProxy) {
            let proxy = MainWindowCloseProxy(forwarding: window.delegate)
            closeProxy = proxy
            window.delegate = proxy
        }

        // Started by the login agent, the window group still makes a window;
        // it must not appear in front of anyone. Hiding here as well as in
        // `applicationDidFinishLaunching` covers a window made after that
        // call returned.
        if hidesOnAdoption {
            window.orderOut(nil)
        }
    }

    /// The login agent's `--background` launch.
    private static var hidesOnAdoption: Bool {
        CommandLine.arguments.contains("--background")
    }

    @MainActor static func hide() {
        // Keep the browser guard's warning panel visible above other apps.
        // Hiding the entire application would hide that countdown too.
        all.forEach { $0.orderOut(nil) }
    }

    /// Bring the existing window forward. It is hidden, not gone, so there is
    /// no window to create — on any supported macOS.
    @MainActor static func show(_ page: ContentView.Page, retrying: Bool = true) {
        AppNavigation.shared.open(page)
        NSApp.activate(ignoringOtherApps: true)
        if let window = adoptedWindow ?? all.first {
            if window.isMiniaturized { window.deminiaturize(nil) }
            window.makeKeyAndOrderFront(nil)
        } else if retrying {
            // A login-agent launch reaches the menu bar before the window
            // group has made its window. Ask again on the next turn instead of
            // dropping the request.
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 200_000_000)
                show(page, retrying: false)
            }
        }
    }
}

/// The window's delegate, with `windowShouldClose` answered by hiding and a
/// passthrough for everything SwiftUI used to receive. Only that one question
/// changes; size restoration, key-window handling and the rest still reach
/// SwiftUI's own delegate.
final class MainWindowCloseProxy: NSObject, NSWindowDelegate {
    private weak var forwarded: NSWindowDelegate?

    init(forwarding forwarded: NSWindowDelegate?) {
        self.forwarded = forwarded
    }

    func windowShouldClose(_ sender: NSWindow) -> Bool {
        sender.orderOut(nil)
        return false
    }

    override func responds(to aSelector: Selector!) -> Bool {
        if super.responds(to: aSelector) { return true }
        return forwarded?.responds(to: aSelector) ?? false
    }

    override func forwardingTarget(for aSelector: Selector!) -> Any? {
        if let forwarded, forwarded.responds(to: aSelector) { return forwarded }
        return super.forwardingTarget(for: aSelector)
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

    /// A single template image lets the status item tint the mark and badge
    /// together in either menu bar appearance. Both states have the same size,
    /// so nearby items stay put. Cache them rather than redrawing on every
    /// lock-status poll.
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

// MARK: - The menu, as data

/// One row of the menu bar menu. The content is built as values so it can be
/// asserted without opening a menu, and rendered into `NSMenu` — the same
/// native menu `MenuBarExtra` showed on macOS 13, now on every supported
/// macOS.
enum StatusMenuEntry: Equatable {
    /// A fact, not a command: disabled when rendered.
    case info(String)
    case separator
    case action(StatusMenuAction, title: String, symbol: String?)
    case toggle(title: String, symbol: String?, on: Bool, enabled: Bool)
    /// The "Lock now" submenu: one command per length.
    case lockLengths(title: String, symbol: String?, lengths: [TimeInterval])
}

enum StatusMenuAction: Equatable {
    case openOverview
    case openLockPage
    case openSetup
    case hide
    case startLock(TimeInterval)
    case pause
    case blockSite
    case checkSite
    case toggleGrayscale
}

/// The menu's facts, gathered once. Separating them from the rows lets the
/// order and presence of the rows be tested — a menu whose lock commands
/// quietly disappear during a lock is a real failure — without a running
/// filter, a lock store or a particular language.
struct StatusMenuState: Equatable {
    enum Lock: Equatable {
        case unlocked
        case locked(remaining: String)
    }

    struct Grayscale: Equatable {
        var isOn: Bool
        /// False while a lock or a managed install forbids turning gray off.
        var canChange: Bool
    }

    var protectionHeadline: String
    var managedName: String?
    var lock: Lock
    var nextLockStart: Date?
    var pausesToday: Int?
    var blockedToday: Int?
    var lockLengths: [TimeInterval]
    var grayscale: Grayscale?

    var isLocked: Bool {
        if case .locked = lock { return true }
        return false
    }
}

enum StatusMenuModel {
    /// Read the live state. Everything the menu shows is a fact the menu is
    /// rebuilt from as it opens, not a subscription.
    @MainActor
    static func liveState(lock: LockManager,
                          filter: FilterController,
                          grayscale: ScreenGrayscale,
                          authority: PolicyStatus? = nil,
                          today: Date = Date()) -> StatusMenuState {
        let status = authority ?? FilterSync.shared.status
        let protection = ProtectionStatus(ProtectionEvidence.live(
            filter: filter.availability, authority: status))
        let managed = ManagedMode.current()
        let lockState: StatusMenuState.Lock = lock.isLocked
            ? .locked(remaining: lock.remainingDescription)
            : .unlocked
        let pauses = PauseStore.load().flatMap {
            $0.day == PauseTally.dayKey(for: today) ? $0.count : nil
        }
        // A count from the filter process itself, not from the app: the same
        // XPC evidence the status uses. The filter records no host, app or
        // address — only this number — so showing it on a shared screen is
        // safe. Zero is left out: an empty day is not a fact worth a row.
        let blocked: Int? = {
            guard let health = status?.health,
                  health.blockedToday > 0,
                  health.blockedDay == BlockedFlowTally.dayKey(for: today)
            else { return nil }
            return health.blockedToday
        }()
        let grayscaleState: StatusMenuState.Grayscale? = ScreenGrayscale.isAvailable
            ? .init(isOn: grayscale.isOn,
                    canChange: !(grayscale.isOn && (lock.isLocked || managed.isManaged)))
            : nil
        return StatusMenuState(
            protectionHeadline: protection.headline,
            managedName: managed.isManaged ? managed.displayName : nil,
            lock: lockState,
            nextLockStart: lock.isLocked ? nil : ScheduleStore.current().nextWindow(after: lock.now)?.start,
            pausesToday: pauses,
            blockedToday: blocked,
            lockLengths: QuickLock.lengths,
            grayscale: grayscaleState)
    }

    /// Everything the menu shows, in order, from the state it shows it for.
    static func entries(for state: StatusMenuState) -> [StatusMenuEntry] {
        var entries: [StatusMenuEntry] = [.info(state.protectionHeadline)]

        if let managed = state.managedName {
            entries.append(.info(String(localized: "Managed by \(managed)")))
        }
        switch state.lock {
        case let .locked(remaining):
            entries.append(.info(String(localized: "Locked · \(remaining) left")))
        case .unlocked:
            entries.append(.info(String(localized: "No active lock")))
            if let start = state.nextLockStart {
                let time = start.formatted(date: .omitted, time: .shortened)
                entries.append(.info(String(localized: "Daily lock at \(time)")))
            }
        }
        if let count = state.pausesToday {
            entries.append(.info(String(localized: "Pauses today: \(count)")))
        }
        if let count = state.blockedToday {
            entries.append(.info(String(localized: "Blocked connections today: \(count)")))
        }

        entries.append(.separator)
        entries.append(.action(.openOverview, title: String(localized: "Open Hisn"),
                               symbol: "macwindow"))
        if !state.isLocked {
            // The moment a lock matters most is the moment it is hardest to
            // go and set one up: one click from the menu bar, then a
            // confirmation that names the end.
            entries.append(.lockLengths(title: String(localized: "Lock now"),
                                        symbol: "lock.fill", lengths: state.lockLengths))
            entries.append(.action(.openLockPage, title: String(localized: "Start a lock…"),
                                   symbol: "clock"))
        }
        entries.append(.action(.pause, title: String(localized: "Take a pause…"), symbol: "wind"))
        entries.append(.action(.blockSite, title: String(localized: "Block a site…"),
                               symbol: "hand.raised"))
        entries.append(.action(.checkSite, title: String(localized: "Check a website…"),
                               symbol: "magnifyingglass"))
        if let grayscale = state.grayscale {
            // A checkmark item. During a lock it can be ticked, not cleared.
            entries.append(.toggle(title: String(localized: "Gray screen"), symbol: nil,
                                   on: grayscale.isOn, enabled: grayscale.canChange))
        }
        entries.append(.action(.openSetup, title: String(localized: "Setup"),
                               symbol: "checklist"))
        entries.append(.separator)
        entries.append(.action(.hide, title: String(localized: "Hide Hisn"),
                               symbol: "eye.slash"))
        return entries
    }
}

/// The menu bar item itself.
///
/// A `MenuBarExtra` scene is macOS 13; before that a status item is the same
/// thing by hand, and it is what Hisn uses on every version so the menu and
/// the countdown behave the same everywhere. The label follows `MenuBarIcon`,
/// which publishes at most once a minute while a lock runs; the menu is rebuilt
/// as it opens, because its facts (lock, pauses, filter status) are live.
@MainActor
final class StatusItemController: NSObject, NSMenuDelegate {
    static let shared = StatusItemController()

    /// The installed item, readable by the tests that prove one is created;
    /// nothing else reads it.
    private(set) var statusItem: NSStatusItem?
    private var menu: NSMenu?
    private var iconObservation: AnyCancellable?
    private var started = false

    func start() {
        guard !started else { return }
        started = true

        let item = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        item.button?.imagePosition = .imageLeading
        let menu = NSMenu()
        // The rows decide their own enabled state; responder-chain
        // validation would enable the disabled fact rows.
        menu.autoenablesItems = false
        menu.delegate = self
        item.menu = menu
        statusItem = item
        self.menu = menu

        // `objectWillChange` fires before the change lands; read the new value
        // on the next turn, which is also when the status item redraws.
        iconObservation = MenuBarIcon.shared.objectWillChange.sink { [weak self] _ in
            Task { @MainActor in self?.refreshIcon() }
        }
        refreshIcon()
    }

    /// Take the item out of the menu bar and stop following the icon. The app
    /// never calls this; the tests use it so a test run leaves at most one
    /// item behind while it runs.
    func stop() {
        guard started else { return }
        started = false
        iconObservation?.cancel()
        iconObservation = nil
        if let statusItem { NSStatusBar.system.removeStatusItem(statusItem) }
        statusItem = nil
        menu = nil
    }

    private func refreshIcon() {
        guard let button = statusItem?.button else { return }
        let icon = MenuBarIcon.shared
        button.image = icon.image
        button.title = icon.remainingText ?? String(localized: "Hisn")
    }

    // MARK: NSMenuDelegate

    func menuNeedsUpdate(_ menu: NSMenu) {
        menu.removeAllItems()
        for entry in StatusMenuModel.entries(for: StatusMenuModel.liveState(
            lock: .shared, filter: .shared, grayscale: .shared)) {
            menu.addItem(item(for: entry))
        }
    }

    func item(for entry: StatusMenuEntry) -> NSMenuItem {
        switch entry {
        case let .info(text):
            let item = NSMenuItem(title: text, action: nil, keyEquivalent: "")
            item.isEnabled = false
            return item

        case .separator:
            return .separator()

        case let .action(action, title, symbol):
            let item = command(title: title, symbol: symbol, action: action)
            return item

        case let .toggle(title, symbol, on, enabled):
            let item = command(title: title, symbol: symbol, action: .toggleGrayscale)
            item.state = on ? .on : .off
            item.isEnabled = enabled
            return item

        case let .lockLengths(title, symbol, lengths):
            let item = NSMenuItem(title: title, action: nil, keyEquivalent: "")
            item.image = Self.image(symbol)
            let submenu = NSMenu()
            submenu.autoenablesItems = false
            for seconds in lengths {
                let length = command(title: LockManager.describe(seconds), symbol: nil,
                                     action: .startLock(seconds))
                submenu.addItem(length)
            }
            item.submenu = submenu
            return item
        }
    }

    private func command(title: String, symbol: String?, action: StatusMenuAction) -> NSMenuItem {
        let item = NSMenuItem(title: title, action: #selector(performMenuAction(_:)), keyEquivalent: "")
        item.target = self
        item.representedObject = StatusMenuActionBox(action)
        item.image = Self.image(symbol)
        return item
    }

    @objc private func performMenuAction(_ sender: NSMenuItem) {
        guard let box = sender.representedObject as? StatusMenuActionBox else { return }
        switch box.action {
        case .openOverview:
            MainWindow.show(.overview)
            // The agent launches with no window. This is the first moment the
            // person asks to see the app, so it is the right moment for the
            // short first-run explanation if it has not been shown yet.
            if !UserDefaults.standard.bool(forKey: "hisn.welcome.completed") {
                AppNavigation.shared.welcomeRequested = true
            }
        case .openLockPage:
            MainWindow.show(.lock)
        case .openSetup:
            MainWindow.show(.setup)
        case .hide:
            MainWindow.hide()
        case let .startLock(seconds):
            QuickLock.confirmAndStart(seconds)
        case .pause:
            PauseWindowController.shared.show()
        case .blockSite:
            QuickBlock.promptAndAdd()
        case .checkSite:
            AppNavigation.shared.siteCheckRequested = true
            MainWindow.show(.overview)
        case .toggleGrayscale:
            _ = ScreenGrayscale.shared.set(!ScreenGrayscale.shared.isOn)
        }
    }

    private static func image(_ symbol: String?) -> NSImage? {
        symbol.flatMap { NSImage(systemSymbolName: $0, accessibilityDescription: nil) }
    }
}

/// `NSMenuItem.representedObject` needs an object; the menu commands are a
/// Swift enum.
private final class StatusMenuActionBox: NSObject {
    let action: StatusMenuAction

    init(_ action: StatusMenuAction) {
        self.action = action
    }
}
