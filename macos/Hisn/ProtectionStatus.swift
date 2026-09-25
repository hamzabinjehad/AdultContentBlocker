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

    /// Fewer than this and the hosts file is not a blocklist, it is a few
    /// hand-written lines.
    public static let hostsMinimum = 1000

    /// The extension polls once a minute; a gap this long means it stopped,
    /// not that we caught it between beats.
    public static let extensionStaleAfter: TimeInterval = 5 * 60

    public init(filter: FilterEvidence, extensionLastSeen: Date?, now: Date = Date(),
                hostsEntries: Int? = nil, filterCanRun: Bool = true) {
        self.filter = filter
        self.extensionLastSeen = extensionLastSeen
        self.now = now
        self.hostsEntries = hostsEntries
        self.filterCanRun = filterCanRun
    }

    /// Read fresh from the shared container — the only honest source, since
    /// the filter and the bridge are separate processes and the app reporting
    /// on itself would be no evidence at all.
    ///
    /// `authority` is the filter's own answer over XPC, when it gave one: the
    /// domain count it reports is the count held in the filter's memory. The
    /// defaults key is the fallback, and on a real install it is root's, not
    /// ours — see `PolicyAuthority` — so without an answer the count reads 0
    /// and the row says so rather than guessing.
    public static func current(filter: FilterController.Availability,
                               authority: PolicyStatus? = nil) -> ProtectionEvidence {
        let d = UserDefaults(suiteName: LockStore.appGroup)
        let filterEvidence: FilterEvidence
        switch filter {
        case .unknown:            filterEvidence = .unknown
        case .unavailable(let m): filterEvidence = .unavailable(m)
        case .off:                filterEvidence = .off
        case .on:
            filterEvidence = .on(domainCount: authority?.health.domainCount
                                 ?? d?.integer(forKey: "filterDomainCount") ?? 0)
        }
        return ProtectionEvidence(
            filter: filterEvidence,
            extensionLastSeen: d?.object(forKey: "extensionLastSeen") as? Date,
            hostsEntries: HostsFile.blockingEntries(),
            filterCanRun: FilterLink.shared.isConfigured)
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

    public var title: String {
        switch self {
        case .retryFilterCheck:   return "Check again"
        case .enableFilter:       return "Turn on the system filter"
        case .updateList:         return "Download the block list"
        case .installExtension:   return "How to connect a browser"
        case .reconnectExtension: return "Reconnect the browser"
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
        public var id: String { name }

        public var ok: Bool { state == .ok }
    }

    public let level: Level
    public let headline: String
    public let summary: String
    public let layers: [Layer]

    /// Would a lock started now actually block anything? Consulted by the
    /// Lock page, which repeats the answer at the point of decision.
    public var isEnforcingAnything: Bool { layers.contains(where: \.ok) }

    public init(_ evidence: ProtectionEvidence) {
        let filter = Self.filterLayer(evidence.filter, canRun: evidence.filterCanRun)
        let ext = Self.extensionLayer(lastSeen: evidence.extensionLastSeen, now: evidence.now)
        let hosts = evidence.hostsEntries.map(Self.hostsLayer)
        layers = [filter] + (hosts.map { [$0] } ?? []) + [ext]

        // Two jobs, and protection is active when both are done: something
        // blocks domains for every app (the filter, or failing it the hosts
        // file), and the extension reads pages. The hosts file is not a
        // second requirement next to a running filter — it is the fallback
        // for a Mac without one — so its absence then is not a gap.
        let networkOK = filter.ok || (hosts?.ok ?? false)
        let okCount = layers.filter(\.ok).count
        if layers.contains(where: { $0.state == .checking }) {
            level = .checking
            headline = "Checking status"
            summary = "Reading the system filter’s state…"
        } else if networkOK && ext.ok {
            level = .active
            headline = "Protection active"
            summary = filter.ok
                ? "The system filter and the browser extension are both running."
                : "The hosts-file blocklist and the browser extension are both running. "
                  + "The system filter would add every app and VPN-proof blocking."
        } else if okCount > 0 {
            level = .partial
            headline = "Partially active"
            let working = layers.filter(\.ok).map(\.name).joined(separator: " and ")
            let broken = layers.filter { !$0.ok }
                .map { "\($0.name.lowercased()) — \($0.detail.lowercased())" }
                .joined(separator: "; ")
            summary = "The \(working.lowercased()) is running. The \(broken)."
        } else {
            level = .setupNeeded
            headline = "Setup needed"
            summary = layers.contains(where: { $0.state == .problem })
                ? "Nothing is blocking right now. Fix the steps below."
                : "Nothing is blocking yet. Finish the steps below."
        }
    }

    private static func hostsLayer(_ entries: Int) -> Layer {
        entries >= ProtectionEvidence.hostsMinimum
            ? Layer(name: "Hosts-file blocklist",
                    detail: "\(entries.formatted()) domains blocked for every app",
                    state: .ok, action: nil)
            : Layer(name: "Hosts-file blocklist",
                    detail: "Not installed — run macos/install.sh --hosts",
                    state: .missing, action: nil)
    }

    private static func filterLayer(_ f: FilterEvidence, canRun: Bool) -> Layer {
        if !canRun, f == .off || f == .unknown || { if case .unavailable = f { return true }; return false }() {
            return Layer(name: "System filter",
                         detail: "Needs a build signed with the Apple Developer Program "
                             + "(Setup step 9)",
                         state: .missing, action: nil)
        }
        switch f {
        case .unknown:
            return Layer(name: "System filter", detail: "Checking…",
                         state: .checking, action: nil)
        case .unavailable(let message):
            return Layer(name: "System filter",
                         detail: "Could not read its state: \(message)",
                         state: .problem, action: .retryFilterCheck)
        case .off:
            return Layer(name: "System filter", detail: "Not running",
                         state: .missing, action: .enableFilter)
        case .on(let count) where count == 0:
            return Layer(name: "System filter",
                         detail: "Running, but it has no block list yet",
                         state: .problem, action: .updateList)
        case .on:
            return Layer(name: "System filter", detail: "Running",
                         state: .ok, action: nil)
        }
    }

    private static func extensionLayer(lastSeen: Date?, now: Date) -> Layer {
        guard let lastSeen else {
            return Layer(name: "Browser extension", detail: "Not set up",
                         state: .missing, action: .installExtension)
        }
        if now.timeIntervalSince(lastSeen) < ProtectionEvidence.extensionStaleAfter {
            return Layer(name: "Browser extension", detail: "Connected",
                         state: .ok, action: nil)
        }
        let when = lastSeen.formatted(date: .abbreviated, time: .shortened)
        return Layer(name: "Browser extension",
                     detail: "Not responding — last heard from \(when)",
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

    /// "Standard" and "Strict" are the user-facing names; the store's
    /// `"blocklist"` is an implementation word.
    public static func modeName(_ mode: String) -> String {
        mode == "strict" ? "Strict" : "Standard"
    }

    public init(state: LockStore.LockState, now: Date, pendingRelease: Date?) {
        guard now < state.deadline else {
            isLocked = false
            headline = "No active lock"
            detail = "Blocking still runs; a lock makes it unremovable for a set time."
            return
        }
        isLocked = true
        headline = "Locked until \(state.deadline.formatted(date: .long, time: .shortened))"
        var parts = ["\(Self.modeName(state.mode)) mode"]
        if let pendingRelease {
            parts.append("early release arrives "
                         + pendingRelease.formatted(date: .abbreviated, time: .shortened))
        }
        detail = parts.joined(separator: " · ")
    }
}
