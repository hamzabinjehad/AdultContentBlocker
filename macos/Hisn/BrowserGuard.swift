import AppKit
import SwiftUI
import UserNotifications

/// During a lock, close any browser that Hisn is not running inside.
///
/// THE HOLE THIS CLOSES
/// --------------------
/// Without the paid Apple entitlements there is no socket filter, and the
/// browser extension is the layer that reads page text and carries the domain
/// rules inside the browser. It can be switched off with one click on the
/// extensions page — the development Mac was found with it switched off — and
/// until it is force-installed from a store, nothing stops that click. A lock
/// that one click undoes is not a lock.
///
/// So while a lock runs, every app that can open web pages is judged:
///
///  * **Safari** is left alone. It is covered by Screen Time's own web filter,
///    the hosts file and the socket filter; no Hisn extension runs there.
///  * **A Chromium browser Hisn links** (`NativeMessagingInstaller.browsers`)
///    stays open for as long as its extension keeps checking in through the
///    bridge. When it stops, the person is warned, and the browser is closed if
///    the extension is not back on within the warning.
///  * **Any other browser** — Firefox, Tor Browser, a Chromium fork downloaded
///    tonight — has nothing of Hisn inside it, so it is closed after a short
///    warning. "Browser" is decided from the app's own Info.plist (it declares
///    the `http`/`https` URL schemes, which every browser does so it can be the
///    default), not from a list of names that tomorrow's fork is missing from.
///  * **Apps the person allowed** before the lock started are left alone. An
///    app that registers for web links without being a browser shows up here
///    and needs allowing once; the allowance can only be added while unlocked.
///
/// This is not tamper-proof — the process can be quit, and an administrator
/// can do anything. It turns "switch the extension off" from a one-click
/// escape into one that closes the browser, and the installer's LaunchAgent
/// brings the app back if it is quit.
public enum BrowserGuardPolicy {

    public enum Coverage: Equatable {
        /// Covered by other layers; never judged.
        case exempt
        /// A browser Hisn's extension runs in — fine while it checks in.
        case needsExtension
        /// Opens web pages with nothing of Hisn inside.
        case uncovered
        /// The person allowed it before the lock.
        case allowedByUser
    }

    public enum Reason: Equatable {
        case extensionSilent
        case uncovered
    }

    public enum Verdict: Equatable {
        case ok
        case warn(Reason, closeAt: Date)
        case close(Reason)
    }

    /// Safari and its preview build: WebKit, covered by Screen Time's filter.
    public static let exempt: Set<String> = [
        "com.apple.Safari", "com.apple.SafariTechnologyPreview",
    ]

    /// Link routers register for web links only to hand them to a real
    /// browser; they render nothing themselves.
    public static let linkRouters: Set<String> = [
        "com.sindresorhus.Velja", "net.kassett.finicky", "com.choosyosx.Choosy",
    ]

    /// Browsers named outright, in case one does not declare the URL schemes.
    /// Not the primary test — see the type comment — only a backstop.
    public static let knownBrowsers: Set<String> = [
        "org.mozilla.firefox", "org.mozilla.firefoxdeveloperedition",
        "org.mozilla.nightly", "org.torproject.torbrowser",
        "net.mullvad.mullvadbrowser", "com.kagi.kagimacOS",
        "com.duckduckgo.macos.browser", "io.gitlab.librewolf-community",
        "net.waterfox.waterfox", "app.zen-browser.zen", "one.ablaze.floorp",
        "com.operasoftware.OperaGX", "ru.yandex.desktop.yandex-browser",
        "org.chromium.Thorium", "ai.perplexity.comet", "company.thebrowser.dia",
        "com.openai.atlas", "org.ladybird.Ladybird",
        "com.google.Chrome.beta", "com.google.Chrome.dev", "com.google.Chrome.canary",
    ]

    /// How long a just-launched (or just-woken) browser has to check in. The
    /// extension checks in as its worker starts, normally within seconds; the
    /// minute covers a restart inside the worker's thirty-second poll
    /// throttle, after which the next one-minute alarm does it.
    public static let launchGrace: TimeInterval = 60
    /// Two and a half missed one-minute heartbeats.
    public static let staleAfter: TimeInterval = 150
    /// A heartbeat stamped this far in the FUTURE is not evidence of anything:
    /// winding the clock back would otherwise make the last check-in look
    /// fresh for as long as the clock stays wound back.
    public static let clockSkewTolerance: TimeInterval = 60
    public static let warnSilent: TimeInterval = 45
    public static let warnUncovered: TimeInterval = 15
    /// Relaunched without fixing what got it closed: no second long warning.
    public static let warnRepeat: TimeInterval = 5
    public static let repeatWindow: TimeInterval = 10 * 60

    public static func coverage(bundleID: String, linked: Set<String>,
                                userAllowed: Set<String>) -> Coverage {
        if exempt.contains(bundleID) || linkRouters.contains(bundleID) { return .exempt }
        if linked.contains(bundleID) { return .needsExtension }
        if userAllowed.contains(bundleID) { return .allowedByUser }
        return .uncovered
    }

    /// Whether this browser's extension is alive, judged at `now`.
    public static func extensionAlive(lastSeen: Date?, now: Date) -> Bool {
        guard let lastSeen else { return false }
        let age = now.timeIntervalSince(lastSeen)
        return age >= -clockSkewTolerance && age < staleAfter
    }

    /// The verdict for one running browser.
    ///
    /// - Parameters:
    ///   - graceStart: the later of its launch, the Mac's last wake and the
    ///     guard's own start — the moment from which it has had a fair chance
    ///     to check in.
    ///   - firstViolation: when the guard first found it in breach, if it has.
    ///     The caller records it the first time this returns `.warn`.
    ///   - recentlyClosed: the guard closed this browser within
    ///     `repeatWindow` and it has been relaunched.
    public static func verdict(coverage: Coverage, lastSeen: Date?, now: Date,
                               graceStart: Date, firstViolation: Date?,
                               recentlyClosed: Bool) -> Verdict {
        let reason: Reason
        let warning: TimeInterval
        switch coverage {
        case .exempt, .allowedByUser:
            return .ok
        case .needsExtension:
            if extensionAlive(lastSeen: lastSeen, now: now) { return .ok }
            if now.timeIntervalSince(graceStart) < launchGrace { return .ok }
            reason = .extensionSilent
            warning = recentlyClosed ? warnRepeat : warnSilent
        case .uncovered:
            reason = .uncovered
            warning = recentlyClosed ? warnRepeat : warnUncovered
        }
        let since = firstViolation ?? now
        let closeAt = since.addingTimeInterval(warning)
        return now >= closeAt ? .close(reason) : .warn(reason, closeAt: closeAt)
    }

    /// Whether an app bundle carries a browser engine: Gecko (`XUL`), or a
    /// Chromium framework with renderer helpers that is NOT Electron —
    /// Electron apps (Slack, VS Code, Claude) share the layout and are not
    /// browsers. A renamed Chromium fork with its URL schemes stripped is
    /// still caught here.
    public static func bundlesBrowserEngine(at app: URL) -> Bool {
        let fm = FileManager.default
        if fm.fileExists(atPath: app.appendingPathComponent("Contents/MacOS/XUL").path) {
            return true
        }
        let frameworks = app.appendingPathComponent("Contents/Frameworks")
        guard let names = try? fm.contentsOfDirectory(atPath: frameworks.path) else { return false }
        for name in names where name.hasSuffix(" Framework.framework") && !name.hasPrefix("Electron") {
            let helpers = frameworks.appendingPathComponent(name)
                .appendingPathComponent("Versions/Current/Helpers")
            if let inside = try? fm.contentsOfDirectory(atPath: helpers.path),
               inside.contains(where: { $0.hasSuffix("Helper (Renderer).app") }) {
                return true
            }
        }
        return false
    }

    /// Whether an app declares the web URL schemes — the test for "browser".
    public static func declaresWebSchemes(infoPlist: [String: Any]) -> Bool {
        guard let types = infoPlist["CFBundleURLTypes"] as? [[String: Any]] else { return false }
        return types.contains { type in
            let schemes = (type["CFBundleURLSchemes"] as? [String]) ?? []
            return schemes.contains { ["http", "https"].contains($0.lowercased()) }
        }
    }
}

// MARK: - Runtime

@MainActor
public final class BrowserGuard: ObservableObject {

    public static let shared = BrowserGuard()

    /// Apps the person allowed to stay open during a lock. Add-only while
    /// unlocked; see `setAllowed`.
    public static let allowedKey = "guardAllowedApps"

    /// A browser the guard is about to close, for the warning panel and the
    /// Overview banner.
    public struct Alert: Identifiable, Equatable {
        public let id: pid_t
        public let name: String
        public let reason: BrowserGuardPolicy.Reason
        public let closeAt: Date
    }

    /// An app on this Mac that can open web pages, as the Browsers section
    /// lists it.
    public struct Candidate: Identifiable, Equatable {
        public var id: String { bundleID }
        public let bundleID: String
        public let name: String
        public let coverage: BrowserGuardPolicy.Coverage
    }

    @Published public private(set) var alerts: [Alert] = []

    private var timer: Timer?
    private var started = Date()
    private var awakeSince = Date()
    private var violations: [pid_t: Date] = [:]
    private var recentlyClosed: [String: Date] = [:]
    private var notified: Set<pid_t> = []
    private var schemeCache: [String: Bool] = [:]
    private var panel: NSPanel?

    private var defaults: UserDefaults? { UserDefaults(suiteName: LockStore.appGroup) }

    private init() {}

    public func start() {
        guard timer == nil else { return }
        started = Date()
        awakeSince = started
        let center = NSWorkspace.shared.notificationCenter
        center.addObserver(forName: NSWorkspace.didWakeNotification, object: nil,
                           queue: .main) { [weak self] _ in
            Task { @MainActor in self?.awakeSince = Date() }
        }
        center.addObserver(forName: NSWorkspace.didLaunchApplicationNotification,
                           object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.tick() }
        }
        timer = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tick() }
        }
        tick()
    }

    // MARK: Allowed apps

    public var allowed: Set<String> {
        Set(defaults?.stringArray(forKey: Self.allowedKey) ?? [])
    }

    /// What is actually allowed right now: while the filter's authority holds
    /// a lock, only apps BOTH copies allow — a `defaults write` of the app's
    /// list mid-lock cannot exempt anything.
    var effectiveAllowed: Set<String> {
        guard let status = FilterSync.shared.status, status.isLocked else { return allowed }
        return allowed.intersection(status.record.guardAllowed)
    }

    /// FilterSync's write of the merged list. Not a person's edit, so no lock
    /// check: the merge only ever narrows it while locked.
    func replaceAllowed(_ apps: Set<String>) {
        defaults?.set(apps.sorted(), forKey: Self.allowedKey)
        objectWillChange.send()
    }

    public enum AllowError: LocalizedError {
        case locked
        public var errorDescription: String? {
            String(localized: "A lock is running. Apps can be allowed again once it ends.")
        }
    }

    /// Allowing an app is a loosening, so it waits for the lock to end.
    /// Withdrawing an allowance tightens and is always accepted.
    public func setAllowed(_ bundleID: String, _ allow: Bool) throws {
        var set = allowed
        if allow {
            guard !EffectiveLock.isLocked else { throw AllowError.locked }
            set.insert(bundleID)
        } else {
            set.remove(bundleID)
        }
        defaults?.set(set.sorted(), forKey: Self.allowedKey)
        objectWillChange.send()
        FilterSync.soon()
    }

    /// Every app on this Mac that can open web pages, with how the guard
    /// would treat it — installed ones from Launch Services, plus whatever is
    /// running now.
    public func candidates() -> [Candidate] {
        let linked = Set(NativeMessagingInstaller.browsers.map(\.bundleID))
        let allowed = allowed
        var urls = NSWorkspace.shared.urlsForApplications(
            toOpen: URL(string: "https://example.com")!)
        urls += NSWorkspace.shared.runningApplications.compactMap(\.bundleURL)
        var seen = Set<String>()
        var out: [Candidate] = []
        for url in urls {
            guard let bundle = Bundle(url: url), let id = bundle.bundleIdentifier,
                  isBrowser(id: id, bundleURL: url, linked: linked),
                  seen.insert(id).inserted else { continue }
            let name = FileManager.default.displayName(atPath: url.path)
                .replacingOccurrences(of: ".app", with: "")
            out.append(Candidate(bundleID: id, name: name,
                                 coverage: BrowserGuardPolicy.coverage(
                                    bundleID: id, linked: linked, userAllowed: allowed)))
        }
        return out.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    // MARK: Judging

    private func isBrowser(id: String, bundleURL: URL?, linked: Set<String>) -> Bool {
        if linked.contains(id) || BrowserGuardPolicy.knownBrowsers.contains(id)
            || BrowserGuardPolicy.exempt.contains(id) { return true }
        guard let bundleURL else { return false }
        // Keyed by path AND modification date: a browser copied over a path
        // already judged "not a browser" must be judged again.
        let mtime = (try? bundleURL.resourceValues(forKeys: [.contentModificationDateKey]))?
            .contentModificationDate?.timeIntervalSince1970 ?? 0
        let key = "\(bundleURL.path)@\(mtime)"
        if let hit = schemeCache[key] { return hit }
        let info = Bundle(url: bundleURL)?.infoDictionary ?? [:]
        // Declaring the web schemes is how a browser asks to be the default;
        // a copy with that stripped out still carries its engine.
        let result = BrowserGuardPolicy.declaresWebSchemes(infoPlist: info)
            || BrowserGuardPolicy.bundlesBrowserEngine(at: bundleURL)
        schemeCache[key] = result
        return result
    }

    private var lastTick = Date()

    func tick() {
        // A long gap between ticks is a sleep the wake notification has not
        // been delivered for yet; judging now would warn about every browser.
        let wall = Date()
        if wall.timeIntervalSince(lastTick) > 15 { awakeSince = wall }
        lastTick = wall

        guard EffectiveLock.isLocked else {
            if !alerts.isEmpty || !violations.isEmpty {
                alerts = []
                violations.removeAll()
                notified.removeAll()
                updatePanel()
            }
            return
        }
        let now = Date()
        let linked = Set(NativeMessagingInstaller.browsers.map(\.bundleID))
        let allowed = effectiveAllowed
        let own = Bundle.main.bundleIdentifier
        // Check-ins from the filter when it answers (the bridge reports them
        // over the code-signed channel); the app's defaults — which a loop of
        // `defaults write` could keep fresh — only when it does not.
        let authorityCheckIns = FilterSync.shared.status?.checkIns
        var next: [Alert] = []
        var running = Set<pid_t>()

        // Regular AND accessory apps: a browser relaunched as an agent
        // (LSUIElement) still shows web pages. An app with no bundle id is
        // judged by its path.
        for app in NSWorkspace.shared.runningApplications
        where app.activationPolicy != .prohibited && !app.isTerminated {
            let id = app.bundleIdentifier ?? app.bundleURL?.path ?? app.executableURL?.path ?? ""
            guard !id.isEmpty, id != own,
                  isBrowser(id: id, bundleURL: app.bundleURL, linked: linked)
            else { continue }
            let pid = app.processIdentifier
            running.insert(pid)
            let coverage = BrowserGuardPolicy.coverage(bundleID: id, linked: linked,
                                                       userAllowed: allowed)
            let graceStart = [app.launchDate ?? .distantPast, awakeSince, started].max()!
            let repeatOffender = recentlyClosed[id].map {
                now.timeIntervalSince($0) < BrowserGuardPolicy.repeatWindow } ?? false
            let verdict = BrowserGuardPolicy.verdict(
                coverage: coverage,
                // The filter's record when it has one for this browser (a
                // forged local stamp cannot outlive it going stale); the app's
                // own only for a browser the filter has never heard from.
                lastSeen: authorityCheckIns?[id]
                    ?? ExtensionPresence.lastSeen(browser: id, in: defaults),
                now: now, graceStart: graceStart,
                firstViolation: violations[pid],
                recentlyClosed: repeatOffender)
            let name = app.localizedName ?? id

            switch verdict {
            case .ok:
                violations[pid] = nil
                notified.remove(pid)
            case let .warn(reason, closeAt):
                if violations[pid] == nil { violations[pid] = now }
                next.append(Alert(id: pid, name: name, reason: reason, closeAt: closeAt))
                if notified.insert(pid).inserted {
                    notify(name: name, reason: reason, seconds: closeAt.timeIntervalSince(now))
                }
            case .close:
                NSLog("[Hisn] guard closing %@ (%@) during a lock", name, id)
                violations[pid] = nil
                notified.remove(pid)
                recentlyClosed[id] = now
                close(app)
            }
        }
        violations = violations.filter { running.contains($0.key) }
        notified = notified.filter { running.contains($0) }
        if next != alerts {
            alerts = next
            updatePanel()
        }
    }

    private func close(_ app: NSRunningApplication) {
        app.terminate()
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 10_000_000_000)
            if !app.isTerminated { app.forceTerminate() }
        }
    }

    // MARK: Telling the person

    public static func message(name: String, reason: BrowserGuardPolicy.Reason) -> String {
        switch reason {
        case .extensionSilent:
            return String(localized: """
                The Hisn extension stopped checking in from \(name). Turn it \
                back on in \(name)’s extensions page, or \(name) will close.
                """)
        case .uncovered:
            return String(localized: """
                \(name) has no Hisn protection inside it, so it cannot stay \
                open during a lock. Use Safari or a browser with the Hisn \
                extension.
                """)
        }
    }

    private func notify(name: String, reason: BrowserGuardPolicy.Reason, seconds: TimeInterval) {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
            guard granted else { return }
            let content = UNMutableNotificationContent()
            content.title = String(localized: "Hisn will close \(name) in \(Int(seconds.rounded())) seconds")
            content.body = Self.message(name: name, reason: reason)
            content.sound = .default
            center.add(UNNotificationRequest(identifier: "guard.\(name)",
                                             content: content, trigger: nil))
        }
    }

    /// A small floating panel with the countdown, above every window — the
    /// notification alone can be missed or switched off.
    private func updatePanel() {
        if alerts.isEmpty {
            panel?.orderOut(nil)
            return
        }
        if panel == nil {
            let p = NSPanel(contentRect: NSRect(x: 0, y: 0, width: 380, height: 150),
                            styleMask: [.titled, .nonactivatingPanel, .utilityWindow],
                            backing: .buffered, defer: false)
            p.title = String(localized: "Hisn")
            p.level = .floating
            p.isFloatingPanel = true
            p.hidesOnDeactivate = false
            p.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
            p.contentView = NSHostingView(rootView: GuardAlertView(guardian: self))
            panel = p
        }
        if let panel, !panel.isVisible, let screen = NSScreen.main {
            let frame = screen.visibleFrame
            panel.setFrameTopLeftPoint(NSPoint(x: frame.maxX - 400, y: frame.maxY - 20))
        }
        panel?.orderFrontRegardless()
    }
}

/// The countdown panel's content.
struct GuardAlertView: View {
    @ObservedObject var guardian: BrowserGuard

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            VStack(alignment: .leading, spacing: 10) {
                ForEach(guardian.alerts) { alert in
                    VStack(alignment: .leading, spacing: 4) {
                        Label(String(localized: """
                              \(alert.name) closes in \
                              \(max(0, Int(alert.closeAt.timeIntervalSince(context.date).rounded()))) s
                              """),
                              systemImage: "exclamationmark.shield.fill")
                            .font(.headline)
                            .foregroundStyle(.orange)
                        Text(BrowserGuard.message(name: alert.name, reason: alert.reason))
                            .font(.callout)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            .padding(16)
            .frame(width: 380, alignment: .leading)
        }
    }
}
