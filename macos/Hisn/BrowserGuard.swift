import AppKit
import SwiftUI
import UserNotifications

/// During a lock (or an explicit outside-lock opt-in), watch non-exempt
/// browsers and close one only when its standard profile confirms that the
/// Hisn extension was disabled or removed.
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
/// Silence is not evidence of that click. Chrome and Chromium forks delay
/// service-worker alarms, sleep, and restart their workers; a missed heartbeat
/// while the extension is still installed must not cost the person their
/// tabs. A browser with no answer is shown as awaiting the extension and stays
/// open. Only two consistent profile readings that the extension is explicitly
/// disabled or absent start the visible five-minute close countdown.
///
/// THE TRADE-OFF
/// -------------
/// Someone who removes the native-host link while leaving the extension
/// installed keeps the browser open. That is deliberate: the heartbeat is a
/// lagging signal, not proof of a click, and the extension still carries its
/// declarative rules and fails closed to strict mode during a lock. Someone
/// who disables or uninstalls the extension is caught by the profile evidence,
/// which is the only positive evidence this guard accepts for a closure.
///
/// While guarding is active, apps identified as browsers are judged:
///
///  * **Safari and other unsupported known browsers** have no connected Hisn
///    extension, so receive the same first one-minute warning and close.
///    Screen Time or DNS alone is not proof that page scanning is present.
///  * **A Chromium browser Hisn links** (`NativeMessagingInstaller.browsers`)
///    stays open for as long as its extension keeps checking in through the
///    bridge. When it stops, the person is told the extension is not replying
///    but the browser stays open. It closes only when two separated profile
///    readings confirm the extension was disabled or removed, after a visible
///    five-minute countdown.
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
/// identifies the native host's browser process, not every profile or private
/// window in it. Actual browser parent attribution needs deployment verification.
public enum BrowserGuardPolicy {
    /// Browser protection is continuous while Hisn runs. A lock no longer
    /// changes whether the guard is active, and there is no opt-out: the
    /// former "require outside a lock" setting is gone, so a browser that
    /// loses the extension is warned and then closed at any time of day.
    public static func isActive() -> Bool { true }

    /// An empty authoritative record is not permission to trust an editable
    /// local heartbeat. Local fallback is only for an unavailable authority.
    public static func checkIn(browser: String, authority: [String: Date]?, local: Date?) -> Date? {
        if let authority { return authority[browser] }
        return local
    }

    /// Never borrow a bundle-level or another process's healthy check-in.
    /// Older bridges need updating together with the app before guarding.
    public static func checkIn(browser: String, processID: Int32,
                                authority: [String: Date]?, local: Date?) -> Date? {
        guard let key = BrowserCheckInIdentity.key(browser: browser, processID: processID) else { return nil }
        if let authority { return authority[key] }
        return local
    }

    /// Session-local warning bookkeeping by browser, not process. An
    /// unverified restart cannot buy a new confirmed-loss countdown.
    public struct Session {
        private var starts: [String: Date] = [:]
        private var violations: [String: Date] = [:]
        private var instances: [String: String] = [:]
        private var healthyRestart: Set<String> = []
        private var closedBrowsers: Set<String> = []
        private var activeInstances: [String: Set<String>] = [:]
        private var instanceViolations: [String: [String: Date]] = [:]
        private var verifiedInstances: [String: Set<String>] = [:]
        private var closedInstances: [String: Set<String>] = [:]
        public init() {}
        /// Snapshot the complete pass before observing any process. A healthy
        /// sibling must not erase another running process's recovery deadline.
        public mutating func setRunningInstances(_ instances: [String: Set<String>]) {
            activeInstances = instances
        }
        public func hasUnresolvedWarning(browser: String) -> Bool { violations[browser] != nil }
        /// Record this process's identity transition for the session ledger.
        /// The returned first-observation time is diagnostic only: a lost
        /// heartbeat no longer sets a closure deadline, but the ledger still
        /// decides whether a confirmed profile-loss countdown is inherited or
        /// cleared by a restart.
        public mutating func graceStart(browser: String, now: Date, notBefore floor: Date,
                                        instance: String? = nil) -> Date {
            if (activeInstances[browser]?.count ?? 0) > 1 {
                if let instance, instanceViolations[browser]?[instance] == nil,
                   !(verifiedInstances[browser]?.contains(instance) ?? false) {
                    // A new offender after a completed closure gets the short
                    // repeat warning. A still-running refusing process does not.
                    let retired = (closedInstances[browser] ?? [])
                        .subtracting(activeInstances[browser] ?? [])
                    for old in retired { instanceViolations[browser]?[old] = nil }
                    closedInstances[browser]?.subtract(retired)
                    if !retired.isEmpty {
                        violations[browser] = instanceViolations[browser]?.values.min()
                    }
                }
                if starts[browser] == nil { starts[browser] = now }
                healthyRestart.remove(browser)
                return max(starts[browser]!, floor)
            }
            if let instance, instances[browser] != instance {
                // A genuinely new process after closure gets its repeat warning.
                // The still-running process refusing quit keeps its old deadline.
                if closedBrowsers.remove(browser) != nil { violations[browser] = nil }
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
                                      verifiedConnection: Bool = false, instance: String? = nil) {
            if let instance {
                switch verdict {
                case .warn:
                    // A new warning supersedes this instance's older closure.
                    // Its next restart must preserve the current deadline.
                    closedInstances[browser]?.remove(instance)
                    verifiedInstances[browser]?.remove(instance)
                    if instanceViolations[browser]?[instance] == nil {
                        // This process already inherited the browser's visible
                        // deadline. Repairing a sibling must not extend it.
                        instanceViolations[browser, default: [:]][instance] = violations[browser] ?? now
                    }
                case .ok:
                    if verifiedConnection { closedInstances[browser]?.remove(instance) }
                    // An already-healthy sibling cannot clear a stopped
                    // offender's deadline and grant its relaunch another minute.
                    let newlyVerified = verifiedConnection &&
                        !(verifiedInstances[browser]?.contains(instance) ?? false)
                    if newlyVerified && (activeInstances[browser]?.count ?? 0) <= 1 {
                        instanceViolations[browser] = nil
                    } else {
                        instanceViolations[browser]?[instance] = nil
                    }
                    if verifiedConnection { verifiedInstances[browser, default: []].insert(instance) }
                case .close:
                    closedInstances[browser, default: []].insert(instance)
                case .attention:
                    // No readable profile says the extension was disabled or
                    // removed, and no heartbeat says it is alive. That is not
                    // positive evidence for a closure: drop this instance's
                    // pending deadline rather than manufacturing one.
                    closedInstances[browser]?.remove(instance)
                    verifiedInstances[browser]?.remove(instance)
                    instanceViolations[browser]?[instance] = nil
                }
                if case .ok = verdict, let unresolved = instanceViolations[browser]?.values.min() {
                    violations[browser] = unresolved
                    healthyRestart.remove(browser)
                    return
                }
                if case .attention = verdict,
                   let unresolved = instanceViolations[browser]?.values.min() {
                    // A sibling still has confirmed profile loss; keep that
                    // browser-level deadline instead of clearing it here.
                    violations[browser] = unresolved
                    healthyRestart.remove(browser)
                    return
                }
            }
            switch verdict {
            case .ok:
                violations[browser] = nil
                closedBrowsers.remove(browser)
                if verifiedConnection { healthyRestart.insert(browser) }
            case .close:
                // Keep the unresolved deadline while this same instance resists
                // termination; manufactured repeat warnings can starve force-close.
                closedBrowsers.insert(browser)
                healthyRestart.remove(browser)
            case .warn:
                // A wake recovery warning supersedes a pending close decision.
                // Its unverified restarts must retain this same warning deadline.
                closedBrowsers.remove(browser)
                healthyRestart.remove(browser)
                if violations[browser] == nil { violations[browser] = now }
            case .attention:
                // Degraded, not closing. Clear a browser-level deadline only
                // when no running process still carries confirmed loss.
                if instanceViolations[browser]?.values.min() == nil {
                    violations[browser] = nil
                }
                closedBrowsers.remove(browser)
                healthyRestart.remove(browser)
            }
        }
        public mutating func reset() {
            starts.removeAll(); violations.removeAll()
            instances.removeAll(); healthyRestart.removeAll(); closedBrowsers.removeAll()
            activeInstances.removeAll(); instanceViolations.removeAll(); verifiedInstances.removeAll()
            closedInstances.removeAll()
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
        /// Degraded but not closing: the extension has not answered and no
        /// profile confirms it was disabled or removed. There is deliberately
        /// no deadline — a late alarm is not a reason to close a browser.
        case attention(Reason)
        case warn(Reason, closeAt: Date)
        case close(Reason)
    }

    /// Diagnostics use exactly the enforcement evidence, never a sibling's
    /// bundle-level heartbeat. Connected does not certify every private tab.
    public enum ConnectionState: Equatable {
        case connected, awaitingExtension, profileUnprotected, unsupported, exempt
    }

    public static func connectionState(coverage: Coverage, lastSeen: Date?,
                                       now: Date, profileLoss: Bool) -> ConnectionState {
        switch coverage {
        case .exempt, .allowedByUser: return .exempt
        case .uncovered: return .unsupported
        case .needsExtension:
            if profileLoss { return .profileUnprotected }
            return extensionAlive(lastSeen: lastSeen, now: now) ? .connected : .awaitingExtension
        }
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

    /// A pending force-close waits this long after a wake before it may act,
    /// so a decision made before sleep does not terminate the browser the
    /// moment the lid opens. It is a recovery floor, not presumed protection.
    public static let launchGrace: TimeInterval = 60
    /// Three nominal thirty-second check-ins. Leaves room for the ten-second
    /// native timeout and authority refresh; Chrome may still delay alarms.
    /// This is the age at which the app stops calling the connection live; it
    /// is no longer a closure deadline.
    public static let staleAfter: TimeInterval = 90
    /// A heartbeat stamped this far in the FUTURE is not evidence of anything:
    /// winding the clock back would otherwise make the last check-in look
    /// fresh for as long as the clock stays wound back.
    public static let clockSkewTolerance: TimeInterval = 60
    /// The visible countdown before a browser whose standard profile confirms
    /// the extension is disabled or removed is closed. Five minutes, because
    /// a false closure costs the person their open work and because Chrome
    /// may disable an extension after an update through no action of theirs.
    public static let warnProfileLoss: TimeInterval = 300
    public static let warnUncovered: TimeInterval = 60
    /// An unsupported browser relaunched without fixing what got it closed
    /// gets a short warning, not a second minute. A linked browser keeps the
    /// full five minutes because its profile is the only positive evidence.
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
    /// rather than blindly applying a decision made ten seconds ago. A linked
    /// browser is force-closable only while its profile still confirms the
    /// extension is disabled or removed; a heartbeat that merely lapsed never
    /// reaches this path.
    public static func shouldForceClose(active: Bool, coverage: Coverage,
                                         lastSeen: Date?, now: Date, profileLoss: Bool = false,
                                         notBefore: Date? = nil) -> Bool {
        guard active, notBefore.map({ now >= $0 }) ?? true else { return false }
        switch coverage {
        case .exempt, .allowedByUser: return false
        case .needsExtension: return profileLoss
        case .uncovered: return true
        }
    }

    /// Cached loss must not close a browser before a read at its original
    /// warning deadline. Repairing a running profile deserves the same fresh
    /// evidence check as repairing one while its browser was stopped.
    public static func profileRefreshRequired(profileLoss: Bool, checkedAt: Date?, now: Date,
                                               firstViolation: Date?) -> Bool {
        guard profileLoss, let firstViolation else { return false }
        let deadline = firstViolation.addingTimeInterval(warnProfileLoss)
        guard now >= deadline else { return false }
        guard let checkedAt, checkedAt >= deadline, checkedAt <= now else { return true }
        return false
    }

    /// The verdict for one running browser.
    ///
    /// - Parameters:
    ///   - firstViolation: when the guard first confirmed profile loss, if it
    ///     has. The caller records it the first time this returns `.warn`.
    ///   - recentlyClosed: the guard closed this browser within
    ///     `repeatWindow` and it has been relaunched.
    public static func verdict(coverage: Coverage, lastSeen: Date?, now: Date,
                               firstViolation: Date?,
                               recentlyClosed: Bool, profileLoss: Bool = false,
                               profileRefreshPending: Bool = false) -> Verdict {
        let reason: Reason
        let warning: TimeInterval
        switch coverage {
        case .exempt, .allowedByUser:
            return .ok
        case .needsExtension:
            if !profileLoss && extensionAlive(lastSeen: lastSeen, now: now) { return .ok }
            guard profileLoss else {
                // No heartbeat, but no readable profile says the extension was
                // disabled or removed either. Silence alone never closes a
                // browser: Chrome delays alarms, workers sleep, and a bridge
                // can be fixed. Keep it open and say the connection is missing.
                return .attention(.extensionSilent)
            }
            reason = .profileUnprotected
            warning = warnProfileLoss
        case .uncovered:
            reason = .uncovered
            warning = recentlyClosed ? warnRepeat : warnUncovered
        }
        let since = firstViolation ?? now
        let closeAt = since.addingTimeInterval(warning)
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

    public struct Connection: Identifiable, Equatable {
        public let id: pid_t
        public let bundleID: String
        public let name: String
        public let state: BrowserGuardPolicy.ConnectionState
        public let lastSeen: Date?
        public let checkedAt: Date
        public let verdict: BrowserGuardPolicy.Verdict?
    }

    @Published public private(set) var alerts: [Alert] = []
    @Published public private(set) var profileIssues: [ProfileIssue] = []
    @Published public private(set) var connections: [Connection] = []
    @Published public private(set) var lastChecked: Date?

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
    private var panelAlertCount = 0

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
        let monitor = Timer(timeInterval: 5, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tick() }
        }
        // Menu tracking and drag interactions must not suspend monitoring.
        // This still is not a real-time guarantee during sleep or OS stalls.
        RunLoop.main.add(monitor, forMode: .common)
        timer = monitor
        tick()
    }

    // MARK: Allowed apps

    public var allowed: Set<String> {
        Set(defaults?.stringArray(forKey: Self.allowedKey) ?? [])
    }

    /// What is actually allowed right now: while the filter's authority holds
    /// a lock, only apps BOTH copies allow. Keep its last confirmed constraint
    /// during IPC outages, rather than trusting newly edited local exceptions.
    var effectiveAllowed: Set<String> {
        FilterSync.shared.effectiveGuardAllowed(local: allowed)
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

    /// Open linked browsers whose extension is not answering. They stay open;
    /// the Protection screen names the gap without a countdown.
    public var awaitingExtension: [Connection] {
        connections.filter { $0.state == .awaitingExtension }
    }

    /// Every app on this Mac that can open web pages, with how the guard
    /// would treat it — installed ones from Launch Services, plus whatever is
    /// running now.
    public func candidates() -> [Candidate] {
        let linked = Set(NativeMessagingInstaller.browsers.map(\.bundleID))
        let allowed = effectiveAllowed
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

    /// Observe open browsers even when enforcement is not enabled. This is
    /// read-only: opening the status screen must not grant closure consent.
    private func runningBrowsers(linked: Set<String>) -> [NSRunningApplication] {
        let own = Bundle.main.bundleIdentifier
        return NSWorkspace.shared.runningApplications.filter { app in
            guard app.activationPolicy != .prohibited, !app.isTerminated else { return false }
            let id = app.bundleIdentifier ?? app.bundleURL?.path ?? app.executableURL?.path ?? ""
            return !id.isEmpty && id != own && isBrowser(id: id, bundleURL: app.bundleURL, linked: linked)
        }
    }

    private func connection(for app: NSRunningApplication, linked: Set<String>, now: Date,
                            verdict: BrowserGuardPolicy.Verdict? = nil) -> Connection {
        let id = app.bundleIdentifier ?? app.bundleURL?.path ?? app.executableURL?.path ?? ""
        let lastSeen = BrowserGuardPolicy.instanceCheckIn(lastSeen: BrowserGuardPolicy.checkIn(
            browser: id, processID: app.processIdentifier, authority: FilterSync.shared.guardCheckIns,
            local: ExtensionPresence.lastSeen(browser: id, processID: app.processIdentifier, in: defaults)),
            launchedAt: app.launchDate)
        let state = BrowserGuardPolicy.connectionState(
            coverage: BrowserGuardPolicy.coverage(bundleID: id, linked: linked, userAllowed: effectiveAllowed),
            lastSeen: lastSeen, now: now,
            profileLoss: !(profileChecks[id]?.confirmed(at: now).isEmpty ?? true))
        return Connection(id: app.processIdentifier, bundleID: id, name: app.localizedName ?? id,
                          state: state, lastSeen: lastSeen, checkedAt: now, verdict: verdict)
    }

    func tick() {
        // A long gap between ticks is a sleep the wake notification has not
        // been delivered for yet; renew recovery without hiding its warning.
        let wall = Date()
        if wall.timeIntervalSince(lastTick) > 15 { awakeSince = wall }
        lastTick = wall

        let linked = Set(NativeMessagingInstaller.browsers.map(\.bundleID))
        let applications = runningBrowsers(linked: linked)
        lastChecked = wall

        guard BrowserGuardPolicy.isActive() else {
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
            connections = applications.map { connection(for: $0, linked: linked, now: wall) }
            return
        }
        let now = Date()
        if !enforcing { enforcementBegan = now; enforcing = true }
        let allowed = effectiveAllowed
        // Check-ins from the filter when it answers (the bridge reports them
        // over the code-signed channel). A remembered locked authority outage
        // supplies an empty set, not permission to forge local freshness.
        // Local defaults remain the fallback without such a confirmed lock.
        let authorityCheckIns = FilterSync.shared.guardCheckIns
        var next: [Alert] = []
        var running = Set<pid_t>()
        var runningLinked = Set<String>()
        var instances: [String: Set<String>] = [:]
        var nextConnections: [Connection] = []

        // Regular AND accessory apps: a browser relaunched as an agent
        // (LSUIElement) still shows web pages. An app with no bundle id is
        // judged by its path.
        var allInstances: [String: Set<String>] = [:]
        for app in applications {
            let id = app.bundleIdentifier ?? app.bundleURL?.path ?? app.executableURL?.path ?? ""
            allInstances[id, default: []].insert("\(app.processIdentifier):\(app.launchDate?.timeIntervalSinceReferenceDate ?? 0)")
        }
        session.setRunningInstances(allInstances)
        for app in applications {
            let id = app.bundleIdentifier ?? app.bundleURL?.path ?? app.executableURL?.path ?? ""
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
                lastSeen: BrowserGuardPolicy.checkIn(browser: id, processID: pid, authority: authorityCheckIns,
                    local: ExtensionPresence.lastSeen(browser: id, processID: pid, in: defaults)),
                launchedAt: app.launchDate)
            // The session ledger still tracks which process is speaking, even
            // though a missing heartbeat no longer sets a close deadline.
            _ = session.graceStart(browser: id, now: now,
                notBefore: max(awakeSince, max(started, enforcementBegan)),
                instance: instance)
            let repeatOffender = BrowserGuardPolicy.isRepeatClosure(
                closedAt: recentlyClosed[id], awakeSince: awakeSince, now: now)
            let profileLoss = !(profileChecks[id]?.confirmed(at: now).isEmpty ?? true)
            if coverage == .needsExtension && BrowserGuardPolicy.profileRefreshRequired(
                profileLoss: profileLoss, checkedAt: profileCheckedAt[id], now: now,
                firstViolation: session.firstViolation(browser: id)) {
                needsFreshProfile.insert(id)
            }
            let verdict = BrowserGuardPolicy.verdict(
                coverage: coverage,
                lastSeen: lastSeen,
                now: now,
                firstViolation: session.firstViolation(browser: id),
                recentlyClosed: repeatOffender,
                profileLoss: profileLoss, profileRefreshPending: needsFreshProfile.contains(id))
            let name = app.localizedName ?? id
            let verifiedConnection = !profileLoss && coverage == .needsExtension &&
                BrowserGuardPolicy.extensionAlive(lastSeen: lastSeen, now: now)
            session.observe(verdict, browser: id, at: now, verifiedConnection: verifiedConnection, instance: instance)
            // Publish the same sampled evidence used for this decision, not a
            // second read that could label a warning as connected.
            nextConnections.append(Connection(id: pid, bundleID: id, name: name,
                state: BrowserGuardPolicy.connectionState(coverage: coverage, lastSeen: lastSeen,
                                                         now: now, profileLoss: profileLoss),
                lastSeen: lastSeen, checkedAt: now, verdict: verdict))

            switch verdict {
            case .ok:
                if verifiedConnection && !session.hasUnresolvedWarning(browser: id) {
                    recentlyClosed.removeValue(forKey: id)
                }
                notified.remove(pid)
                closing.removeValue(forKey: pid)?.cancel()
                closingIDs.removeValue(forKey: pid)
            case let .warn(reason, closeAt):
                next.append(Alert(id: pid, name: name, reason: reason, closeAt: closeAt))
                if notified.insert(pid).inserted {
                    notify(name: name, reason: reason, seconds: closeAt.timeIntervalSince(now))
                }
            case .attention:
                // The extension is not answering, but nothing proves it was
                // disabled or removed. Leave the browser open, show the gap in
                // the connection list, and cancel any pending force-close from
                // profile evidence that no longer holds.
                notified.remove(pid)
                recentlyClosed.removeValue(forKey: id)
                closing.removeValue(forKey: pid)?.cancel()
                closingIDs.removeValue(forKey: pid)
            case .close:
                NSLog("[Hisn] guard closing browser with confirmed profile loss %@ (%@)", name, id)
                notified.remove(pid)
                recentlyClosed[id] = now
                close(app)
            }
        }
        // Keep browser-level warning evidence while a process is absent. Clearing
        // it here would make terminate/relaunch loops reset the countdown again.
        notified = notified.filter { running.contains($0) }
        profileInstances = instances
        connections = nextConnections
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
                lastSeen: BrowserGuardPolicy.checkIn(browser: id, processID: pid, authority: FilterSync.shared.guardCheckIns,
                    local: ExtensionPresence.lastSeen(browser: id, processID: pid, in: self.defaults)),
                launchedAt: launchDate)
            let notBefore = BrowserGuardPolicy.forceRecoveryDeadline(
                requestedAt: requestedAt, awakeSince: self.awakeSince,
                warningDeadline: self.alerts.first(where: { $0.id == pid })?.closeAt)
            if BrowserGuardPolicy.shouldForceClose(
                active: BrowserGuardPolicy.isActive(),
                coverage: coverage, lastSeen: seen, now: Date(),
                profileLoss: currentProfileLoss, notBefore: notBefore) {
                app.forceTerminate()
            }
        }
    }

    // MARK: Telling the person

    /// A linked browser that is not answering and has no readable profile
    /// evidence of disablement or removal. It is deliberately not an alert:
    /// there is no countdown and no closure.
    public static func attentionMessage(name: String) -> String {
        String(localized: """
            Hisn cannot confirm an extension connection from \(name). The browser \
            stays open; page-content checks may be reduced until the extension \
            reconnects.
            """)
    }

    public static func message(name: String, reason: BrowserGuardPolicy.Reason) -> String {
        switch reason {
        case .extensionSilent:
            return attentionMessage(name: name)
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
            panelAlertCount = 0
            return
        }
        if panel == nil {
            let p = NSPanel(contentRect: NSRect(x: 0, y: 0, width: 460, height: 320),
                            styleMask: [.titled, .fullSizeContentView, .utilityWindow],
                            backing: .buffered, defer: false)
            p.title = String(localized: "Hisn")
            p.titleVisibility = .hidden
            p.titlebarAppearsTransparent = true
            p.standardWindowButton(.closeButton)?.isHidden = true
            p.standardWindowButton(.miniaturizeButton)?.isHidden = true
            p.standardWindowButton(.zoomButton)?.isHidden = true
            p.isMovableByWindowBackground = true
            p.level = .floating
            p.isFloatingPanel = true
            p.hidesOnDeactivate = false
            p.becomesKeyOnlyIfNeeded = true
            p.contentMinSize = NSSize(width: 460, height: 280)
            p.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
            p.contentView = NSHostingView(rootView: GuardAlertView(guardian: self))
            panel = p
        }
        if let panel, panelAlertCount != alerts.count {
            let availableHeight = NSScreen.main?.visibleFrame.height ?? 800
            let height = min(max(320, CGFloat(alerts.count) * 300 + 24), min(640, availableHeight * 0.8))
            panel.setContentSize(NSSize(width: 460, height: height))
            panelAlertCount = alerts.count
        }
        if let panel, !panel.isVisible, let screen = NSScreen.main {
            let frame = screen.visibleFrame
            panel.setFrameTopLeftPoint(NSPoint(x: frame.maxX - panel.frame.width - 20, y: frame.maxY - 20))
        }
        panel?.orderFrontRegardless()
    }
}

/// The countdown panel's content: one calm card per browser that is about to
/// close. It explains why, counts down, and offers one way forward — bring the
/// browser forward so the extension can be restored. There is no dismiss or
/// bypass action; restoring the extension is what cancels the close.
struct GuardAlertView: View {
    @ObservedObject var guardian: BrowserGuard

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            ScrollView {
                VStack(spacing: 14) {
                    ForEach(guardian.alerts) { alert in
                        GuardAlertCard(alert: alert, now: context.date) {
                            NSRunningApplication(processIdentifier: alert.id)?
                                .activate(options: [.activateAllWindows])
                        }
                    }
                }
                .padding(22)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(HnPalette.canvas.ignoresSafeArea())
            .preferredColorScheme(.dark)
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text("Browser protection needs attention"))
    }
}

/// One browser's intervention card. Kept separate from the panel so the render
/// tests can inspect it without an alert being live.
struct GuardAlertCard: View {
    let alert: BrowserGuard.Alert
    let now: Date
    let open: () -> Void

    private var remaining: Int {
        max(0, Int(alert.closeAt.timeIntervalSince(now).rounded()))
    }

    /// The app's fixed system blue: this panel must look the same on every
    /// Mac, including a graphite accent or a test host.
    private var accent: Color { HnPalette.accent }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top, spacing: 13) {
                Image(systemName: "shield.lefthalf.filled")
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 52, height: 52)
                    .background(accent, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 3) {
                    Text("Protection interrupted")
                        .font(.caption.weight(.semibold))
                        .textCase(.uppercase)
                        .foregroundStyle(HnPalette.muted)
                    Text("\(alert.name) is about to close")
                        .font(.title3.weight(.bold))
                        .foregroundStyle(HnPalette.ink)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }

            Text(BrowserGuard.message(name: alert.name, reason: alert.reason))
                .font(.callout)
                .foregroundStyle(HnPalette.muted)
                .fixedSize(horizontal: false, vertical: true)

            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(verbatim: "\(remaining)")
                    .font(.system(size: 40, weight: .bold))
                    .monospacedDigit()
                    .foregroundStyle(HnPalette.ink)
                Text("seconds left")
                    .font(.callout)
                    .foregroundStyle(HnPalette.muted)
                Spacer(minLength: 0)
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel(Text("Closes in \(remaining) seconds"))

            Button(action: open) {
                Label("Open \(alert.name)", systemImage: "arrow.up.forward.app")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .foregroundStyle(.white)
                    .background(accent, in: RoundedRectangle(cornerRadius: 7, style: .continuous))
            }
            .buttonStyle(.plain)

            Text("Restoring the extension cancels the close.")
                .font(.caption)
                .foregroundStyle(HnPalette.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(18)
        .background(HnPalette.card, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(HnPalette.line, lineWidth: 1)
                .accessibilityHidden(true)
        }
        .accessibilityElement(children: .contain)
    }
}
