import AppKit
import SwiftUI
import UserNotifications

/// During a lock (or an explicit outside-lock opt-in), warn and close
/// non-exempt browsers without a live extension connection.
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
/// While guarding is active, apps identified as browsers are judged:
///
///  * **Safari and other unsupported known browsers** have no connected Hisn
///    extension, so receive the same first one-minute warning and close.
///    Screen Time or DNS alone is not proof that page scanning is present.
///  * **A Chromium browser Hisn links** (`NativeMessagingInstaller.browsers`)
///    stays open for as long as its extension keeps checking in through the
///    bridge. When it stops, the person is warned, and the browser is closed if
///    the extension is not back on within the warning.
///  * **Any other browser** — Firefox, Tor Browser, a Chromium fork downloaded
///    tonight — has nothing of Hisn inside it, so it is closed after a visible
///    one-minute warning. "Browser" is decided from the app's own Info.plist (it declares
///    the `http`/`https` URL schemes, which every browser does so it can be the
///    default), not from a list of names that tomorrow's fork is missing from.
///  * **Unknown apps explicitly allowed** before the lock are left alone to
///    accommodate non-browser apps that register for web links. Known browsers
///    cannot use this exception to bypass their required extension. A custom
///    browser explicitly trusted as an unknown app remains a possible bypass.
///
/// This is not tamper-proof — the process can be quit, and an administrator
/// can do anything. It turns "switch the extension off" from a one-click
/// escape into one that closes the browser, and the installer's LaunchAgent
/// can bring the app back if it is quit when separately installed. A heartbeat
/// identifies a browser bundle, not every profile or private window in it.
public enum BrowserGuardPolicy {
    public static func isActive(locked: Bool, requireOutsideLock: Bool) -> Bool {
        locked || requireOutsideLock
    }

    /// An empty authoritative record is not permission to trust an editable
    /// local heartbeat. Local fallback is only for an unavailable authority.
    public static func checkIn(browser: String, authority: [String: Date]?, local: Date?) -> Date? {
        if let authority { return authority[browser] }
        return local
    }

    /// Session-local timing by browser, not process. An unverified restart
    /// cannot buy a new connection grace or warning countdown.
    public struct Session {
        private var starts: [String: Date] = [:]
        private var violations: [String: Date] = [:]
        private var instances: [String: String] = [:]
        private var healthyRestart: Set<String> = []
        public init() {}
        public mutating func graceStart(browser: String, now: Date, notBefore floor: Date,
                                        instance: String? = nil) -> Date {
            if let instance, instances[browser] != instance {
                // One new grace after verified connectivity, not after merely
                // passing through the unverified startup grace again.
                if instances[browser] != nil, healthyRestart.remove(browser) != nil {
                    starts[browser] = now
                }
                instances[browser] = instance
            }
            if starts[browser] == nil { starts[browser] = now }
            return max(starts[browser]!, floor)
        }
        public func firstViolation(browser: String) -> Date? { violations[browser] }
        public mutating func observe(_ verdict: Verdict, browser: String, at now: Date,
                                      verifiedConnection: Bool = false) {
            switch verdict {
            case .ok:
                violations[browser] = nil
                if verifiedConnection { healthyRestart.insert(browser) }
            case .close:
                violations[browser] = nil
                healthyRestart.remove(browser)
            case .warn:
                healthyRestart.remove(browser)
                if violations[browser] == nil { violations[browser] = now }
            }
        }
        public mutating func reset() {
            starts.removeAll(); violations.removeAll()
            instances.removeAll(); healthyRestart.removeAll()
        }
    }

    public enum Coverage: Equatable {
        /// Non-rendering link router, not a protected web browser.
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
        case profileUnprotected
    }

    public enum Verdict: Equatable {
        case ok
        case warn(Reason, closeAt: Date)
        case close(Reason)
    }

    /// Link routers register for web links only to hand them to a real
    /// browser; they render nothing themselves.
    public static let linkRouters: Set<String> = [
        "com.sindresorhus.Velja", "net.kassett.finicky", "com.choosyosx.Choosy",
    ]

    /// Browsers named outright, in case one does not declare the URL schemes.
    /// Not the primary test — see the type comment — only a backstop.
    public static let knownBrowsers: Set<String> = [
        "com.apple.Safari", "com.apple.SafariTechnologyPreview",
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

    /// Visible recovery window for a just-launched or just-woken browser.
    /// It is a deadline floor, never a hidden period of presumed protection.
    public static let launchGrace: TimeInterval = 60
    /// Three nominal thirty-second check-ins. Leaves room for the ten-second
    /// native timeout and authority refresh; Chrome may still delay alarms.
    public static let staleAfter: TimeInterval = 90
    /// A heartbeat stamped this far in the FUTURE is not evidence of anything:
    /// winding the clock back would otherwise make the last check-in look
    /// fresh for as long as the clock stays wound back.
    public static let clockSkewTolerance: TimeInterval = 60
    public static let warnSilent: TimeInterval = 60
    public static let warnUncovered: TimeInterval = 60
    /// Relaunched without fixing what got it closed: no second long warning.
    public static let warnRepeat: TimeInterval = 5
    public static let repeatWindow: TimeInterval = 10 * 60

    /// Only unknown detected apps may be trusted as false positives.
    /// Stored exceptions cannot exempt a known browser, including Safari.
    public static func canAllowException(bundleID: String, linked: Set<String>) -> Bool {
        !bundleID.isEmpty && !linked.contains(bundleID) &&
            !knownBrowsers.contains(bundleID) && !linkRouters.contains(bundleID)
    }

    public static func coverage(bundleID: String, linked: Set<String>,
                                userAllowed: Set<String>) -> Coverage {
        if linkRouters.contains(bundleID) { return .exempt }
        if linked.contains(bundleID) { return .needsExtension }
        if canAllowException(bundleID: bundleID, linked: linked),
           userAllowed.contains(bundleID) { return .allowedByUser }
        return .uncovered
    }

    /// A previous process's heartbeat cannot verify a newly launched instance.
    public static func instanceCheckIn(lastSeen: Date?, launchedAt: Date?) -> Date? {
        guard let lastSeen else { return nil }
        if let launchedAt, lastSeen < launchedAt { return nil }
        return lastSeen
    }

    public static func isRepeatClosure(closedAt: Date?, awakeSince: Date, now: Date) -> Bool {
        guard let closedAt else { return false }
        let age = now.timeIntervalSince(closedAt)
        return closedAt >= awakeSince && age >= 0 && age < repeatWindow
    }

    /// Pending termination must not override a visible countdown or the
    /// recovery window following a wake that happened after it was requested.
    public static func forceRecoveryDeadline(requestedAt: Date, awakeSince: Date,
                                              warningDeadline: Date?) -> Date? {
        let wakeDeadline = awakeSince > requestedAt ? awakeSince.addingTimeInterval(launchGrace) : nil
        if let wakeDeadline, let warningDeadline { return max(wakeDeadline, warningDeadline) }
        return wakeDeadline ?? warningDeadline
    }

    /// Whether this browser's extension is alive, judged at `now`.
    public static func extensionAlive(lastSeen: Date?, now: Date) -> Bool {
        guard let lastSeen else { return false }
        let age = now.timeIntervalSince(lastSeen)
        return age >= -clockSkewTolerance && age < staleAfter
    }

    /// Recheck delayed force-termination against current consent and recovery,
    /// rather than blindly applying a decision made ten seconds ago.
    public static func shouldForceClose(active: Bool, coverage: Coverage,
                                         lastSeen: Date?, now: Date, profileLoss: Bool = false,
                                         notBefore: Date? = nil) -> Bool {
        guard active, notBefore.map({ now >= $0 }) ?? true else { return false }
        switch coverage {
        case .exempt, .allowedByUser: return false
        case .needsExtension: return profileLoss || !extensionAlive(lastSeen: lastSeen, now: now)
        case .uncovered: return true
        }
    }

    /// Cached loss must not close a browser before a read at its original
    /// warning deadline. Repairing a running profile deserves the same fresh
    /// evidence check as repairing one while its browser was stopped.
    public static func profileRefreshRequired(profileLoss: Bool, checkedAt: Date?, now: Date,
                                               firstViolation: Date?, recentlyClosed: Bool) -> Bool {
        guard profileLoss, let firstViolation else { return false }
        let deadline = firstViolation.addingTimeInterval(recentlyClosed ? warnRepeat : warnSilent)
        guard now >= deadline else { return false }
        guard let checkedAt, checkedAt >= deadline, checkedAt <= now else { return true }
        return false
    }

    /// The verdict for one running browser.
    ///
    /// - Parameters:
    ///   - graceStart: the later of the browser's first observation in this
    ///     enforcement session, the Mac's last wake and the guard's own start.
    ///     Only a restart following verified connectivity can renew it.
    ///   - firstViolation: when the guard first found it in breach, if it has.
    ///     The caller records it the first time this returns `.warn`.
    ///   - recentlyClosed: the guard closed this browser within
    ///     `repeatWindow` and it has been relaunched.
    public static func verdict(coverage: Coverage, lastSeen: Date?, now: Date,
                               graceStart: Date, firstViolation: Date?,
                               recentlyClosed: Bool, profileLoss: Bool = false,
                               profileRefreshPending: Bool = false) -> Verdict {
        let reason: Reason
        let warning: TimeInterval
        switch coverage {
        case .exempt, .allowedByUser:
            return .ok
        case .needsExtension:
            if !profileLoss && extensionAlive(lastSeen: lastSeen, now: now) { return .ok }
            reason = profileLoss ? .profileUnprotected : .extensionSilent
            warning = recentlyClosed ? warnRepeat : warnSilent
        case .uncovered:
            reason = .uncovered
            warning = recentlyClosed ? warnRepeat : warnUncovered
        }
        let since = firstViolation ?? now
        var closeAt = since.addingTimeInterval(warning)
        if reason == .extensionSilent && !recentlyClosed {
            closeAt = max(closeAt, graceStart.addingTimeInterval(launchGrace))
        }
        // A repaired, relaunched browser deserves a new reading before an
        // old cached profile failure can close it again. Keep the original
        // violation timestamp; a genuinely off profile cannot reset its timer.
        if reason == .profileUnprotected && profileRefreshPending && now >= closeAt {
            return .warn(reason, closeAt: now.addingTimeInterval(1))
        }
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
    public static let requireOutsideLockKey = "guardRequireOutsideLock"

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

    public struct ProfileIssue: Identifiable, Equatable {
        public let id: String
        public let browserName: String
        public let unprotected: [String]
        public let unconfirmed: [String]
        public let enumerationVerified: Bool
    }

    @Published public private(set) var alerts: [Alert] = []
    @Published public private(set) var profileIssues: [ProfileIssue] = []

    private var timer: Timer?
    private var started = Date()
    private var awakeSince = Date()
    private var session = BrowserGuardPolicy.Session()
    private var enforcing = false
    private var enforcementBegan = Date()
    private var recentlyClosed: [String: Date] = [:]
    private var notified: Set<pid_t> = []
    private var closing: [pid_t: Task<Void, Never>] = [:]
    private var closingIDs: [pid_t: UUID] = [:]
    private var profileChecks: [String: BrowserProfileEvidence.Confirmation] = [:]
    private var profileCheckedAt: [String: Date] = [:]
    private var profileInstances: [String: Set<String>] = [:]
    private var needsFreshProfile: Set<String> = []
    private var profileScan: Task<Void, Never>?
    private var lastProfileScan: Date?
    private var profileEpoch: UInt64 = 0
    private var schemeCache: [String: Bool] = [:]
    private var panel: NSPanel?

    private var defaults: UserDefaults? { UserDefaults(suiteName: LockStore.appGroup) }

    private init() {}

    /// Optional preference outside a commitment; an active lock always guards
    /// browsers regardless of this ordinary, user-owned preference.
    public var requireOutsideLock: Bool { defaults?.bool(forKey: Self.requireOutsideLockKey) == true }

    public func setRequireOutsideLock(_ enabled: Bool) throws {
        if !enabled, EffectiveLock.isLocked { throw AllowError.locked }
        guard let defaults else { throw AllowError.storageUnavailable }
        defaults.set(enabled, forKey: Self.requireOutsideLockKey)
        guard defaults.object(forKey: Self.requireOutsideLockKey) as? Bool == enabled else {
            throw AllowError.storageUnavailable
        }
        objectWillChange.send()
        tick()
    }

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
        case storageUnavailable
        case extensionRequired
        public var errorDescription: String? {
            switch self {
            case .locked: return String(localized: "A lock is running. Apps can be allowed again once it ends.")
            case .storageUnavailable: return String(localized: "Browser protection settings could not be saved. Try again.")
            case .extensionRequired: return String(localized: "Known browsers need a connected Hisn extension and cannot be allowed as app exceptions.")
            }
        }
    }

    /// Allowing an app is a loosening, so it waits for the lock to end.
    /// Withdrawing an allowance tightens and is always accepted.
    public func setAllowed(_ bundleID: String, _ allow: Bool) throws {
        var set = allowed
        if allow {
            guard !EffectiveLock.isLocked else { throw AllowError.locked }
            guard BrowserGuardPolicy.canAllowException(bundleID: bundleID,
                linked: Set(NativeMessagingInstaller.browsers.map(\.bundleID))) else {
                throw AllowError.extensionRequired
            }
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
            || BrowserGuardPolicy.linkRouters.contains(id) { return true }
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
        // been delivered for yet; renew recovery without hiding its warning.
        let wall = Date()
        if wall.timeIntervalSince(lastTick) > 15 { awakeSince = wall }
        lastTick = wall

        guard BrowserGuardPolicy.isActive(locked: EffectiveLock.isLocked,
                                          requireOutsideLock: requireOutsideLock) else {
            session.reset()
            enforcing = false
            recentlyClosed.removeAll()
            notified.removeAll()
            for task in closing.values { task.cancel() }
            closing.removeAll()
            closingIDs.removeAll()
            profileScan?.cancel()
            profileScan = nil
            lastProfileScan = nil
            profileEpoch &+= 1
            profileChecks.removeAll()
            profileCheckedAt.removeAll()
            profileInstances.removeAll()
            needsFreshProfile.removeAll()
            if !profileIssues.isEmpty { profileIssues = [] }
            if !alerts.isEmpty {
                alerts = []
                updatePanel()
            }
            return
        }
        let now = Date()
        if !enforcing { enforcementBegan = now; enforcing = true }
        let linked = Set(NativeMessagingInstaller.browsers.map(\.bundleID))
        let allowed = effectiveAllowed
        let own = Bundle.main.bundleIdentifier
        // Check-ins from the filter when it answers (the bridge reports them
        // over the code-signed channel); the app's defaults — which a loop of
        // `defaults write` could keep fresh — only when it does not.
        let authorityCheckIns = FilterSync.shared.status?.checkIns
        var next: [Alert] = []
        var running = Set<pid_t>()
        var runningLinked = Set<String>()
        var instances: [String: Set<String>] = [:]

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
            let instance = "\(pid):\(app.launchDate?.timeIntervalSinceReferenceDate ?? 0)"
            if linked.contains(id) {
                runningLinked.insert(id)
                instances[id, default: []].insert(instance)
                if profileInstances[id]?.contains(instance) != true { needsFreshProfile.insert(id) }
            }
            let coverage = BrowserGuardPolicy.coverage(bundleID: id, linked: linked,
                                                       userAllowed: allowed)
            let lastSeen = BrowserGuardPolicy.instanceCheckIn(
                lastSeen: BrowserGuardPolicy.checkIn(browser: id, authority: authorityCheckIns,
                    local: ExtensionPresence.lastSeen(browser: id, in: defaults)),
                launchedAt: app.launchDate)
            let graceStart = session.graceStart(browser: id, now: now,
                notBefore: max(awakeSince, max(started, enforcementBegan)),
                instance: instance)
            let repeatOffender = BrowserGuardPolicy.isRepeatClosure(
                closedAt: recentlyClosed[id], awakeSince: awakeSince, now: now)
            let profileLoss = !(profileChecks[id]?.confirmed(at: now).isEmpty ?? true)
            if coverage == .needsExtension && BrowserGuardPolicy.profileRefreshRequired(
                profileLoss: profileLoss, checkedAt: profileCheckedAt[id], now: now,
                firstViolation: session.firstViolation(browser: id), recentlyClosed: repeatOffender) {
                needsFreshProfile.insert(id)
            }
            let verdict = BrowserGuardPolicy.verdict(
                coverage: coverage,
                lastSeen: lastSeen,
                now: now, graceStart: graceStart,
                firstViolation: session.firstViolation(browser: id),
                recentlyClosed: repeatOffender,
                profileLoss: profileLoss, profileRefreshPending: needsFreshProfile.contains(id))
            let name = app.localizedName ?? id
            let verifiedConnection = !profileLoss && coverage == .needsExtension &&
                BrowserGuardPolicy.extensionAlive(lastSeen: lastSeen, now: now)
            session.observe(verdict, browser: id, at: now, verifiedConnection: verifiedConnection)

            switch verdict {
            case .ok:
                if verifiedConnection { recentlyClosed.removeValue(forKey: id) }
                notified.remove(pid)
                closing.removeValue(forKey: pid)?.cancel()
                closingIDs.removeValue(forKey: pid)
            case let .warn(reason, closeAt):
                next.append(Alert(id: pid, name: name, reason: reason, closeAt: closeAt))
                if notified.insert(pid).inserted {
                    notify(name: name, reason: reason, seconds: closeAt.timeIntervalSince(now))
                }
            case .close:
                NSLog("[Hisn] guard closing disconnected browser %@ (%@)", name, id)
                notified.remove(pid)
                recentlyClosed[id] = now
                close(app)
            }
        }
        // Keep browser-level warning evidence while a process is absent. Clearing
        // it here would make terminate/relaunch loops reset the countdown again.
        notified = notified.filter { running.contains($0) }
        profileInstances = instances
        scanProfiles(for: runningLinked, at: now)
        if next != alerts {
            alerts = next
            updatePanel()
        }
    }

    /// Bounded preference IO is off-main-thread. A cancelled/outdated scan
    /// cannot restore evidence after consent is withdrawn or a lock ends.
    private func scanProfiles(for browserIDs: Set<String>, at now: Date) {
        guard profileScan == nil,
              (!needsFreshProfile.intersection(browserIDs).isEmpty ||
               (lastProfileScan.map({ now.timeIntervalSince($0) >= 15 }) ?? true)),
              let support = FileManager.default.urls(for: .applicationSupportDirectory,
                                                     in: .userDomainMask).first else { return }
        let requests = NativeMessagingInstaller.browsers.filter { browserIDs.contains($0.bundleID) }
            .map { (id: $0.bundleID, name: $0.name, root: support.appendingPathComponent($0.userSupportDir)) }
        let ids = Set(NativeMessagingInstaller.extensionIDs)
        let capturedInstances = profileInstances
        let epoch = profileEpoch
        lastProfileScan = now
        profileScan = Task { @MainActor [weak self] in
            let readings = await Task.detached(priority: .utility) {
                requests.map { request in
                    (id: request.id, name: request.name,
                     snapshot: BrowserProfileEvidence.inspect(root: request.root, ids: ids))
                }
            }.value
            guard !Task.isCancelled, let self, self.profileEpoch == epoch else { return }
            self.profileScan = nil
            let readAt = Date()
            var issues: [ProfileIssue] = []
            for reading in readings {
                guard self.profileInstances[reading.id] == capturedInstances[reading.id] else { continue }
                self.needsFreshProfile.remove(reading.id)
                var check = self.profileChecks[reading.id] ?? BrowserProfileEvidence.Confirmation()
                // A slow scan cannot relabel an older file observation as
                // freshly checked at completion time.
                check.observe(reading.snapshot, at: now)
                self.profileChecks[reading.id] = check
                self.profileCheckedAt[reading.id] = now
                let confirmed = check.confirmed(at: readAt)
                let unconfirmed = reading.snapshot.profiles.filter {
                    $0.state == .unconfirmed || ($0.state == .explicitLoss && !confirmed.contains($0.folder))
                }.map(\.folder)
                if !reading.snapshot.enumerationVerified || !confirmed.isEmpty || !unconfirmed.isEmpty {
                    issues.append(ProfileIssue(id: reading.id, browserName: reading.name,
                        unprotected: confirmed, unconfirmed: unconfirmed,
                        enumerationVerified: reading.snapshot.enumerationVerified))
                }
            }
            if self.profileIssues != issues { self.profileIssues = issues }
            self.tick()
        }
    }

    private func close(_ app: NSRunningApplication) {
        let pid = app.processIdentifier
        guard closing[pid] == nil else { return }
        let requestedAt = Date()
        app.terminate()
        let launchDate = app.launchDate
        let closeID = UUID()
        closingIDs[pid] = closeID
        closing[pid] = Task { @MainActor [weak self] in
            do { try await Task.sleep(nanoseconds: 10_000_000_000) }
            catch { return }
            guard let self else { return }
            defer {
                if self.closingIDs[pid] == closeID {
                    self.closing[pid] = nil
                    self.closingIDs[pid] = nil
                }
            }
            guard !app.isTerminated else { return }
            let id = app.bundleIdentifier ?? app.bundleURL?.path ?? app.executableURL?.path ?? ""
            var currentProfileLoss = false
            if !self.needsFreshProfile.contains(id),
               !(self.profileChecks[id]?.confirmed(at: Date()).isEmpty ?? true),
               let browser = NativeMessagingInstaller.browsers.first(where: { $0.bundleID == id }),
               let support = FileManager.default.urls(for: .applicationSupportDirectory,
                                                      in: .userDomainMask).first {
                let root = support.appendingPathComponent(browser.userSupportDir)
                let ids = Set(NativeMessagingInstaller.extensionIDs)
                // The warning-deadline read cannot prove loss ten seconds
                // later. Do not force a repaired browser using that old read.
                let snapshot = await Task.detached(priority: .utility) {
                    BrowserProfileEvidence.inspect(root: root, ids: ids)
                }.value
                currentProfileLoss = !self.needsFreshProfile.contains(id) &&
                    BrowserProfileEvidence.lossPersists(in: snapshot,
                        confirmedFolders: self.profileChecks[id]?.confirmed(at: Date()) ?? [])
            }
            guard !Task.isCancelled, !app.isTerminated,
                  app.processIdentifier == pid, app.launchDate == launchDate else { return }
            let coverage = BrowserGuardPolicy.coverage(bundleID: id,
                linked: Set(NativeMessagingInstaller.browsers.map(\.bundleID)),
                userAllowed: self.effectiveAllowed)
            let seen = BrowserGuardPolicy.instanceCheckIn(
                lastSeen: BrowserGuardPolicy.checkIn(browser: id, authority: FilterSync.shared.status?.checkIns,
                    local: ExtensionPresence.lastSeen(browser: id, in: self.defaults)),
                launchedAt: launchDate)
            let notBefore = BrowserGuardPolicy.forceRecoveryDeadline(
                requestedAt: requestedAt, awakeSince: self.awakeSince,
                warningDeadline: self.alerts.first(where: { $0.id == pid })?.closeAt)
            if BrowserGuardPolicy.shouldForceClose(
                active: BrowserGuardPolicy.isActive(locked: EffectiveLock.isLocked,
                                                    requireOutsideLock: self.requireOutsideLock),
                coverage: coverage, lastSeen: seen, now: Date(),
                profileLoss: currentProfileLoss, notBefore: notBefore) {
                app.forceTerminate()
            }
        }
    }

    // MARK: Telling the person

    public static func message(name: String, reason: BrowserGuardPolicy.Reason) -> String {
        switch reason {
        case .extensionSilent:
            return String(localized: """
                Hisn cannot confirm an extension connection from \(name). Reinstall \
                or enable it in \(name)’s extensions page and check its app \
                connection, or \(name) will close.
                """)
        case .uncovered:
            return String(localized: """
                \(name) has no Hisn protection inside it, so it cannot stay \
                open while browser guarding is active. Reconnect a supported \
                browser or review your protection settings.
                """)
        case .profileUnprotected:
            return String(localized: """
                \(name) has a standard browser profile with Hisn removed or disabled. \
                Restore the extension in every listed profile or \(name) will close.
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
