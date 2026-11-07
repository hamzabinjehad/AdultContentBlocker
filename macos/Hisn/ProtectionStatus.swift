import Foundation

/// The two questions the main window has to answer separately: *am I
/// protected?* and *am I locked?*
///
/// They used to be one headline. "Not locked" led the window, which reads as
/// "not protected" — while the filter may well have been blocking 150k domains
/// — and "Protected" only ever appeared during a lock, so a person who had
/// finished setup and not yet committed to a lock saw the same grey as one who
/// had installed nothing. Splitting them lets each say the true thing.
///
/// Everything in this file is a plain value computed from evidence, so the
/// tests can pin every state without standing up a filter or a view. The rule
/// that matters most: **a missing answer is never a positive status.** A layer
/// we have not heard from is "checking" or "not set up"; it is never "running".

// MARK: - Evidence

/// What the app can actually observe about the system filter.
public enum FilterEvidence: Equatable {
    /// `NEFilterManager` has not answered yet. Distinct from `.off`, because
    /// the app starts with no answer and showing "not running" during those
    /// first milliseconds is a lie in the alarming direction.
    case unknown
    /// The query failed. The message is the system's own.
    case unavailable(String)
    case off
    /// Running, with the number of domains the filter process reports holding.
    /// Zero is the "looks on, blocks nothing" state the threat model rates
    /// worse than being switched off.
    case on(domainCount: Int)
    /// Switched on in System Settings, but the filter process does not answer
    /// over XPC — stopped, crashed, or not yet approved. Distinct from `.on`:
    /// the domain count that would otherwise be shown comes from a defaults
    /// key the user can write, and said "Running" for a dead filter.
    case silent
}

/// Every fact the status is computed from, gathered in one place so the view
/// and the tests feed the same function.
public struct ProtectionEvidence: Equatable {
    public var filter: FilterEvidence
    /// When the browser extension last polled the bridge, or nil if never.
    public var extensionLastSeen: Date?
    public var now: Date
    /// Blocking entries in /etc/hosts, or nil when not measured (then the
    /// row is left out rather than guessed).
    public var hostsEntries: Int?
    /// Whether this build could run the system filter at all. An unsigned
    /// build — no paid Apple team — cannot, and its filter row says so
    /// instead of offering a button that can only fail.
    public var filterCanRun: Bool
    /// Why `filterCanRun` is false, when the build itself is the reason:
    /// an ad-hoc binary and a bundle missing its filter extension need
    /// different remedies, so the row names the one that applies.
    public var filterBlock: FilterBuildBlock?
    /// Live authority diagnostics, separate from whether its process runs.
    public var policyRecoveryRequired: Bool
    public var policyPersistenceError: String?
    /// Installed browsers whose private windows are not covered. Nil when the
    /// caller did not read private-browsing evidence, so unit tests and
    /// surfaces that do not own browser policy keep the old layer set.
    public var privateBrowsing: PrivateBrowsingStatus?
    /// The managed-organization profile, when one supplied a forced flag.
    public var managedMode: ManagedMode?

    /// Fewer than this and the hosts file is not a blocklist, it is a few
    /// hand-written lines.
    public static let hostsMinimum = 1000

    /// Match the guard's missed-heartbeat window for thirty-second check-ins,
    /// so Overview does not call a stale connection healthy. This global stamp
    /// still does not verify every browser or profile.
    public static let extensionStaleAfter: TimeInterval = BrowserGuardPolicy.staleAfter

    public init(filter: FilterEvidence, extensionLastSeen: Date?, now: Date = Date(),
                hostsEntries: Int? = nil, filterCanRun: Bool = true,
                filterBlock: FilterBuildBlock? = nil,
                policyRecoveryRequired: Bool = false, policyPersistenceError: String? = nil,
                privateBrowsing: PrivateBrowsingStatus? = nil,
                managedMode: ManagedMode? = nil) {
        self.filter = filter
        self.extensionLastSeen = extensionLastSeen
        self.now = now
        self.hostsEntries = hostsEntries
        self.filterCanRun = filterCanRun
        self.filterBlock = filterBlock
        self.policyRecoveryRequired = policyRecoveryRequired
        self.policyPersistenceError = policyPersistenceError
        self.privateBrowsing = privateBrowsing
        self.managedMode = managedMode
    }

    /// Read fresh from the shared container — the only honest source, since
    /// the filter and the bridge are separate processes and the app reporting
    /// on itself would be no evidence at all.
    ///
    /// `authority` is the filter's own answer over XPC, when it gave one: the
    /// domain count it reports is the count held in the filter's memory.
    /// Without a configured, answering authority the filter is silent, even
    /// if a user-writable defaults key contains a plausible domain count.
    public static func current(filter: FilterController.Availability,
                               authority: PolicyStatus? = nil) -> ProtectionEvidence {
        let d = UserDefaults(suiteName: LockStore.appGroup)
        let filterEvidence: FilterEvidence
        switch filter {
        case .unknown:            filterEvidence = .unknown
        case .unavailable(let m): filterEvidence = .unavailable(m)
        case .off:                filterEvidence = .off
        case .on:
            if let authority, FilterLink.shared.isConfigured {
                filterEvidence = .on(domainCount: authority.health.domainCount)
            } else {
                // User-writable domain counts are never provider liveness.
                filterEvidence = .silent
            }
        }
        return ProtectionEvidence(
            filter: filterEvidence,
            extensionLastSeen: d?.object(forKey: "extensionLastSeen") as? Date,
            hostsEntries: HostsFile.blockingEntries(),
            filterCanRun: FilterLink.shared.isConfigured,
            filterBlock: FilterLink.shared.buildBlock,
            policyRecoveryRequired: authority?.record.recoveryRequired ?? false,
            policyPersistenceError: authority?.persistenceError)
    }

    /// The same evidence plus the browser policy the window shows. Kept
    /// separate from `current` so tests and non-browser surfaces do not
    /// inherit whatever browsers happen to be installed on the test host.
    public static func live(filter: FilterController.Availability,
                            authority: PolicyStatus? = nil,
                            privateBrowsing: PrivateBrowsingStatus? = PrivateBrowsingStatus.current(),
                            managedMode: ManagedMode? = ManagedMode.current())
        -> ProtectionEvidence {
        var evidence = current(filter: filter, authority: authority)
        evidence.privateBrowsing = privateBrowsing
        evidence.managedMode = managedMode
        return evidence
    }
}

/// The hosts-file layer (`macos/block_dns.sh`), measured, not assumed.
public enum HostsFile {
    private static var cache: (mtime: Date, count: Int)?

    /// Lines that sink a name to 0.0.0.0. Re-counted only when the file
    /// changes: the Overview asks every second, and the file holds several
    /// hundred thousand lines.
    public static func blockingEntries(path: String = "/etc/hosts") -> Int? {
        guard let mtime = (try? FileManager.default.attributesOfItem(atPath: path))?[.modificationDate]
                as? Date else { return nil }
        if let cache, cache.mtime == mtime { return cache.count }
        guard let data = FileManager.default.contents(atPath: path) else { return nil }
        let count = count(in: data)
        cache = (mtime, count)
        return count
    }

    static func count(in data: Data) -> Int {
        let needle = Array("0.0.0.0".utf8)
        var n = 0
        data.withUnsafeBytes { (raw: UnsafeRawBufferPointer) in
            let b = raw.bindMemory(to: UInt8.self)
            var lineStart = true
            var i = 0
            while i < b.count {
                if lineStart, i + needle.count < b.count,
                   b[i] == needle[0], Array(b[i..<i + needle.count]) == needle,
                   b[i + needle.count] == 0x20 || b[i + needle.count] == 0x09 {
                    n += 1
                }
                lineStart = b[i] == 0x0A
                i += 1
            }
        }
        return n
    }
}

// MARK: - Actions

/// The specific next step a problem row offers. Every problem gets one, so
/// the window never says "something is wrong" without saying what to press.
public enum StatusAction: Equatable {
    case retryFilterCheck
    case enableFilter
    case updateList
    case installExtension
    case reconnectExtension
    case privateBrowsing

    public var title: String {
        switch self {
        case .retryFilterCheck:   return String(localized: "Check again")
        case .enableFilter:       return String(localized: "Turn on the system filter")
        case .updateList:         return String(localized: "Download the block list")
        case .installExtension:   return String(localized: "How to connect a browser")
        case .reconnectExtension: return String(localized: "Reconnect the browser")
        case .privateBrowsing:    return String(localized: "Review private windows")
        }
    }
}

// MARK: - Protection

public struct ProtectionStatus: Equatable {

    public enum Level: Equatable {
        case checking, active, partial, setupNeeded
    }

    /// One enforcement layer as the window should describe it.
    public struct Layer: Identifiable, Equatable {
        public enum State: Equatable {
            case ok
            case checking
            /// Was set up, is not working — the alarming kind.
            case problem
            /// Never set up — the calm kind.
            case missing
        }
        public let name: String
        public let detail: String
        public let state: State
        public let action: StatusAction?
        public private(set) var identity: String? = nil
        public var id: String { identity ?? name }
        func identified(as identity: String) -> Layer {
            var layer = self
            layer.identity = identity
            return layer
        }

        public var ok: Bool { state == .ok }
    }

    public let level: Level
    public let headline: String
    public let summary: String
    public let layers: [Layer]
    public let managed: ManagedMode?

    public var isManaged: Bool { managed?.isManaged == true }

    /// The line under the headline. `summary` names every broken layer and is
    /// right for VoiceOver and diagnostics; this is the short version the
    /// Overview can show without turning the status hero into a paragraph.
    public var heroSummary: String {
        if level == .active || level == .checking || isPrivateWindowOnlyGap { return summary }
        let ready = layers.filter(\.ok).count
        return String(localized: "\(ready) of \(layers.count) layers ready. The rest need attention.")
    }

    /// Every enforcement layer except private windows is ready. The
    /// browser-extension status is green, which is exactly when a person
    /// would otherwise assume private windows are covered too.
    public var isPrivateWindowOnlyGap: Bool {
        level == .partial
            && layers.contains { $0.id == "privateBrowsing" && $0.state == .problem }
            && layers.allSatisfy { $0.ok || $0.id == "privateBrowsing" }
    }

    /// Would a lock started now actually block anything? Consulted by the
    /// Lock page, which repeats the answer at the point of decision.
    public var isEnforcingAnything: Bool { layers.contains(where: \.ok) }
    public var hasProblems: Bool { layers.contains { $0.state == .problem } }
    public func unconfirmedLayers(previouslyReady: Set<String>) -> [Layer] {
        layers.filter { !$0.ok && previouslyReady.contains($0.id) }
    }

    public init(_ evidence: ProtectionEvidence) {
        let filter: Layer
        if evidence.policyRecoveryRequired, case .on = evidence.filter {
            filter = Layer(name: String(localized: "System filter"),
                           detail: String(localized: "Restricting traffic during policy recovery"),
                           state: .ok, action: nil)
        } else {
            filter = Self.filterLayer(evidence.filter, canRun: evidence.filterCanRun,
                                      block: evidence.filterBlock,
                                      managedFilter: evidence.managedMode?.filterManaged == true)
        }
        let ext = Self.extensionLayer(lastSeen: evidence.extensionLastSeen, now: evidence.now)
        let hosts = evidence.hostsEntries.map(Self.hostsLayer)
        let privateLayer = evidence.privateBrowsing.flatMap(Self.privateBrowsingLayer)
        let managedLayer: Layer? = evidence.managedMode?.isManaged == true
            ? Layer(name: String(localized: "Organization policy"),
                    detail: evidence.managedMode?.policyDetail ?? "",
                    state: .ok, action: nil)
            : nil
        let policy: Layer?
        if evidence.policyRecoveryRequired {
            policy = Layer(name: String(localized: "Saved protection policy"),
                           detail: String(localized: "Protection is restricted because its saved policy could not be read. Ask the administrator to restore it."),
                           state: .problem, action: nil)
        } else if evidence.policyPersistenceError != nil {
            policy = Layer(name: String(localized: "Saved protection policy"),
                           detail: String(localized: "Changes could not be saved. The previous policy remains in force. Ask the administrator to check storage and permissions."),
                           state: .problem, action: nil)
        } else {
            policy = nil
        }
        layers = [filter.identified(as: "filter")] + (hosts.map { [$0.identified(as: "hosts")] } ?? [])
            + [ext.identified(as: "extension")]
            + (privateLayer.map { [$0.identified(as: "privateBrowsing")] } ?? [])
            + (managedLayer.map { [$0.identified(as: "managed")] } ?? [])
            + (policy.map { [$0.identified(as: "policy")] } ?? [])
        managed = evidence.managedMode

        // A hosts fallback only covers clients using the OS resolver. Keep
        // it useful, but never promote it to the system filter's coverage.
        let okCount = layers.filter(\.ok).count
        if layers.contains(where: { $0.state == .checking }) {
            level = .checking
            headline = String(localized: "Checking status")
            summary = String(localized: "Reading the system filter’s state…")
        } else if filter.ok && ext.ok && (privateLayer?.ok ?? true) && policy == nil {
            level = .active
            headline = String(localized: "Protection active")
            summary = privateLayer == nil
                ? String(localized: "The system filter and the browser extension are both running.")
                : String(localized: "The system filter, the browser extension and private windows are all covered.")
        } else if privateLayer?.state == .problem && filter.ok && ext.ok && policy == nil {
            // This is the specific state the person must not miss: every other
            // layer is green, but a private window has no Hisn inside it.
            level = .partial
            headline = String(localized: "Private windows are open")
            summary = String(localized: "Everything else is running. Private windows skip the browser extension, so Hisn cannot check those pages.")
        } else if okCount > 0 {
            level = .partial
            headline = String(localized: "Partially active")
            // Lists, not a sentence glued from lowercased names: that read
            // well in exactly one language.
            let working = layers.filter(\.ok).map(\.name).formatted(.list(type: .and))
            let broken = layers.filter { !$0.ok }
                .map { String(localized: "\($0.name) (\($0.detail))") }
                .formatted(.list(type: .and))
            summary = String(localized: "Running: \(working). Needs attention: \(broken).")
        } else {
            level = .setupNeeded
            headline = String(localized: "Setup needed")
            summary = layers.contains(where: { $0.state == .problem })
                ? String(localized: "Nothing is blocking right now. Fix the steps below.")
                : String(localized: "Nothing is blocking yet. Finish the steps below.")
        }
    }

    private static func hostsLayer(_ entries: Int) -> Layer {
        entries >= ProtectionEvidence.hostsMinimum
            ? Layer(name: String(localized: "Hosts-file blocklist"),
                    detail: String(localized: "\(entries.formatted()) domains listed for clients using the system resolver"),
                    state: .ok, action: nil)
            : Layer(name: String(localized: "Hosts-file blocklist"),
                    detail: String(localized: "Not installed — run macos/install.sh --hosts"),
                    state: .missing, action: nil)
    }

    private static func filterLayer(_ f: FilterEvidence, canRun: Bool,
                                    block: FilterBuildBlock?,
                                    managedFilter: Bool = false) -> Layer {
        let name = String(localized: "System filter")
        if !canRun {
            // Name the cause that applies. A block with no diagnosis keeps the
            // generic line: a state invented from missing evidence would be
            // worse than an unspecific one.
            return Layer(name: name,
                         detail: block?.explanation
                            ?? String(localized: "Needs a build signed with the Apple Developer Program (Setup step 9)"),
                         state: .missing, action: nil)
        }
        switch f {
        case .unknown:
            return Layer(name: name, detail: String(localized: "Checking…"),
                         state: .checking, action: nil)
        case .unavailable(let message):
            return Layer(name: name,
                         detail: String(localized: "Could not read its state: \(message)"),
                         state: .problem, action: .retryFilterCheck)
        case .off:
            if managedFilter {
                // The app cannot start a profile-owned filter, and an Enable
                // button would fail. Name the owner and leave the action out.
                return Layer(name: name,
                             detail: String(localized: "Managed by your organization, but not running. Ask your administrator to check the profile."),
                             state: .problem, action: nil)
            }
            return Layer(name: name, detail: String(localized: "Not running"),
                         state: .missing, action: .enableFilter)
        case .silent:
            return Layer(name: name,
                         detail: String(localized: "Switched on, but not answering — open Hisn again, or restart the Mac"),
                         state: .problem, action: .retryFilterCheck)
        case .on(let count) where count <= 0:
            return Layer(name: name,
                         detail: String(localized: "Running, but it has no block list yet"),
                         state: .problem, action: .updateList)
        case .on:
            return Layer(name: name, detail: String(localized: "Running"),
                         state: .ok, action: nil)
        }
    }

    private static func privateBrowsingLayer(_ status: PrivateBrowsingStatus) -> Layer? {
        // Safari and hardened forks have no supported policy; the browser
        // guard still acts on them, but they are not a policy layer. Listing
        // them here would keep every Mac permanently "partial" and teach the
        // person to ignore the status.
        guard status.hasControllableBrowser else { return nil }
        let name = String(localized: "Private windows")
        let openNames = status.openNames
        if !status.open.isEmpty {
            return Layer(name: name,
                         detail: String(localized: "Open in \(openNames) — those windows have no Hisn inside"),
                         state: .problem, action: .privateBrowsing)
        }
        let disabledNames = status.disabled.map(\.name).formatted(.list(type: .and))
        let extensionNames = status.extensionChecked.map(\.name).formatted(.list(type: .and))
        let detail: String
        if !status.disabled.isEmpty && !status.extensionChecked.isEmpty {
            detail = String(localized: "Off in \(disabledNames); checked by the extension in \(extensionNames)")
        } else if !status.disabled.isEmpty {
            detail = String(localized: "Off in \(disabledNames)")
        } else {
            detail = String(localized: "Checked by the extension in \(extensionNames)")
        }
        return Layer(name: name, detail: detail, state: .ok, action: nil)
    }

    private static func extensionLayer(lastSeen: Date?, now: Date) -> Layer {
        let name = String(localized: "Browser extension")
        guard let lastSeen else {
            return Layer(name: name, detail: String(localized: "Not set up"),
                         state: .missing, action: .installExtension)
        }
        let age = now.timeIntervalSince(lastSeen)
        guard age.isFinite, age >= 0 else {
            return Layer(name: name,
                         detail: String(localized: "Browser heartbeat has an invalid time. Reconnect the browser."),
                         state: .problem, action: .reconnectExtension)
        }
        if age < ProtectionEvidence.extensionStaleAfter {
            return Layer(name: name, detail: String(localized: "Connected"),
                         state: .ok, action: nil)
        }
        let when = lastSeen.formatted(date: .abbreviated, time: .shortened)
        return Layer(name: name,
                     detail: String(localized: "Not responding — last heard from \(when)"),
                     state: .problem, action: .reconnectExtension)
    }
}

// MARK: - Lock

/// The lock, described on its own. Whether a lock is running says nothing
/// about whether anything enforces it — that is `ProtectionStatus`'s job — so
/// the two are never blended into one word here.
public struct LockStatus: Equatable {
    public let isLocked: Bool
    public let headline: String
    public let detail: String?

    /// "Standard mode" and "Strict mode" are the user-facing names; the
    /// store's `"blocklist"` is an implementation word. Whole phrases, not a
    /// name plus "mode": Arabic puts the noun first («الوضع الصارم»).
    public static func modeLabel(_ mode: String) -> String {
        mode == "strict" ? String(localized: "Strict mode") : String(localized: "Standard mode")
    }

    public init(state: LockStore.LockState, now: Date, pendingRelease: Date?) {
        guard now < state.deadline else {
            isLocked = false
            headline = String(localized: "No active lock")
            detail = String(localized: "Blocking still runs; a lock makes it unremovable for a set time.")
            return
        }
        isLocked = true
        let until = state.deadline.formatted(date: .long, time: .shortened)
        headline = String(localized: "Locked until \(until)")
        var parts = [Self.modeLabel(state.mode)]
        if let pendingRelease {
            let when = pendingRelease.formatted(date: .abbreviated, time: .shortened)
            parts.append(String(localized: "early release arrives \(when)"))
        }
        detail = parts.joined(separator: " · ")
    }
}

/// Whether the downloaded generation is old enough that the Details page should
/// say so. The bundled seed is always present; a missing update is not a missing
/// layer. HEALTH.md sets seven days as the freshness line.
enum ListUpdateFreshness {
    static let staleAfter: TimeInterval = 7 * 86_400

    static func isStale(lastUpdate: Date?, now: Date = Date()) -> Bool {
        guard let lastUpdate else { return false }
        return now.timeIntervalSince(lastUpdate) > staleAfter
    }
}

/// What the root guardian daemon left in its local log, read-only for the app.
///
/// The log is root-owned and mode 644; the Mac app is not sandboxed, so a
/// standard user can read it but not change it. Only the last action is shown:
/// an account name, an action token and a timestamp. No browsing data exists in
/// the file.
struct GuardianLogStatus: Equatable {
    let installed: Bool
    let action: String?
    let at: Date?

    static let daemonPlist = URL(fileURLWithPath: "/Library/LaunchDaemons/app.hisn.guardian.plist")
    static let logURL = URL(fileURLWithPath: "/Library/Application Support/Hisn/guardian.log")

    static func read(plistURL: URL = daemonPlist, logURL: URL = logURL) -> GuardianLogStatus {
        let installed = FileManager.default.fileExists(atPath: plistURL.path)
        guard installed, let text = try? String(contentsOf: logURL, encoding: .utf8) else {
            return GuardianLogStatus(installed: installed, action: nil, at: nil)
        }
        let last = text.split(separator: "\n").last.map(String.init)
        guard let last, !last.isEmpty else {
            return GuardianLogStatus(installed: true, action: nil, at: nil)
        }
        let parts = last.split(separator: " ", maxSplits: 1).map(String.init)
        guard parts.count == 2 else {
            return GuardianLogStatus(installed: true, action: nil, at: nil)
        }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        let date = formatter.date(from: parts[0])
        let action = parts[1].split(separator: " ").first.map(String.init)
        return GuardianLogStatus(installed: true, action: action, at: date)
    }

    /// Known daemon actions become text a person can read; an unknown action is
    /// shown as generic activity rather than hidden or rendered as a token.
    static func label(for action: String) -> String {
        switch action {
        case "agent-reloaded": return String(localized: "Login agent restored")
        case "agent-reload-failed": return String(localized: "Guardian could not restore the login agent")
        case "account-missing": return String(localized: "Protected account missing")
        case "config-unreadable": return String(localized: "Guardian config unreadable")
        default: return String(localized: "Guardian activity")
        }
    }
}
