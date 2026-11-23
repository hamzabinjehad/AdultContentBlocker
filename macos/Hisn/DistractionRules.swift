import Foundation

// MARK: - Unrot's distraction rules on the Mac
//
// One model for every kind of distraction rule the Mac enforces, and one pure
// decision function that every enforcer asks: the app watcher (NSWorkspace),
// the browser extensions (through the bridge) and, for whole sites, the system
// filter. The Android edition has the same rules spread over `OpenWait`,
// `AppTimeLimit`, `DailyAppSchedule` and `FocusSession`; here they share one
// shape so that the Mac's table, inspector and menu bar can show them alike.
//
// Adult content is NOT a distraction rule. It is always on, lives in the
// filter and the extension's own lists, and nothing in this file can turn it
// off or loosen it.
//
// Nothing here reads a clock, the disk or the system: callers pass `now`, a
// calendar and today's usage, so every decision is tested without a Mac.

/// What a rule applies to.
public enum RuleTarget: Hashable, Codable, Sendable {
    /// A Mac app, by bundle identifier ("com.hnc.Discord").
    case app(String)
    /// A site and all of its subdomains ("youtube.com").
    case site(String)
    /// One part of a site — a feed or a short-video player — while the rest of
    /// the site stays usable.
    case surface(FeedSurface)

    /// The form used in stored rules and on the bridge.
    public var key: String {
        switch self {
        case .app(let id): return "app:" + id
        case .site(let host): return "site:" + host
        case .surface(let surface): return "surface:" + surface.rawValue
        }
    }

    public init?(key: String) {
        if key.hasPrefix("app:") {
            let id = String(key.dropFirst(4))
            guard RuleTarget.isBundleID(id) else { return nil }
            self = .app(id)
        } else if key.hasPrefix("site:") {
            guard let host = RuleTarget.normalizedSite(String(key.dropFirst(5))) else { return nil }
            self = .site(host)
        } else if key.hasPrefix("surface:"), let surface = FeedSurface(rawValue: String(key.dropFirst(8))) {
            self = .surface(surface)
        } else {
            return nil
        }
    }

    public init(from decoder: Decoder) throws {
        let key = try decoder.singleValueContainer().decode(String.self)
        guard let target = RuleTarget(key: key) else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath,
                                                    debugDescription: "Unknown rule target \(key)"))
        }
        self = target
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(key)
    }

    /// Lower-case ASCII host with at least one dot, no scheme, path, port or
    /// wildcard. A leading "www." is dropped so "www.youtube.com" and
    /// "youtube.com" are the same rule.
    public static func normalizedSite(_ input: String) -> String? {
        var host = input.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if let range = host.range(of: "://") { host = String(host[range.upperBound...]) }
        if let cut = host.firstIndex(where: { $0 == "/" || $0 == "?" || $0 == "#" || $0 == ":" }) {
            host = String(host[..<cut])
        }
        while host.hasSuffix(".") { host.removeLast() }
        if host.hasPrefix("www.") { host = String(host.dropFirst(4)) }
        let allowed = Set("abcdefghijklmnopqrstuvwxyz0123456789-.")
        guard host.count >= 3, host.count <= 253, host.contains("."),
              !host.hasPrefix("."), !host.hasPrefix("-"),
              !host.contains(".."), host.allSatisfy({ allowed.contains($0) })
        else { return nil }
        return host
    }

    public static func isBundleID(_ id: String) -> Bool {
        let allowed = Set("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-.")
        return id.count >= 3 && id.count <= 255 && id.contains(".")
            && !id.hasPrefix(".") && !id.hasSuffix(".") && id.allSatisfy { allowed.contains($0) }
    }
}

/// The parts of big sites that are the feed, not the tool. "Feeds only" rules
/// close these and leave search, links, messages and uploads working.
///
/// The web paths match `extension/rules/short_video.json` where they overlap,
/// so the Chromium and Safari extensions and this model agree on what a Short
/// or a Reel is.
public enum FeedSurface: String, CaseIterable, Codable, Sendable {
    case youtubeHome, youtubeShorts
    case instagramFeed, instagramReels, instagramExplore
    case xForYou
    case redditHome, redditPopular
    case facebookFeed, facebookReels, facebookWatch
    case tiktok
    case linkedinFeed
    case snapchatSpotlight

    public var site: String {
        switch self {
        case .youtubeHome, .youtubeShorts: return "youtube.com"
        case .instagramFeed, .instagramReels, .instagramExplore: return "instagram.com"
        case .xForYou: return "x.com"
        case .redditHome, .redditPopular: return "reddit.com"
        case .facebookFeed, .facebookReels, .facebookWatch: return "facebook.com"
        case .tiktok: return "tiktok.com"
        case .linkedinFeed: return "linkedin.com"
        case .snapchatSpotlight: return "snapchat.com"
        }
    }

    /// Hosts that are the same site under another name.
    var aliases: [String] {
        switch self {
        case .xForYou: return ["x.com", "twitter.com"]
        default: return [site]
        }
    }

    /// True for the short-video players, which the person usually wants gone
    /// entirely; false for home feeds, which some people keep with a limit.
    public var isShortVideo: Bool {
        switch self {
        case .youtubeShorts, .instagramReels, .facebookReels, .tiktok, .snapchatSpotlight: return true
        default: return false
        }
    }

    /// Whether `url` is this surface. Only the scheme, host and path are read.
    public func matches(_ url: URL) -> Bool {
        guard let scheme = url.scheme?.lowercased(), scheme == "http" || scheme == "https",
              let rawHost = url.host?.lowercased() else { return false }
        guard aliases.contains(where: { rawHost == $0 || rawHost.hasSuffix("." + $0) }) else { return false }
        let path = url.path.lowercased()
        let parts = path.split(separator: "/").map(String.init)
        let first = parts.first ?? ""
        switch self {
        case .youtubeHome: return parts.isEmpty || first == "feed" && (parts.count < 2 || parts[1] != "subscriptions")
        case .youtubeShorts: return first == "shorts"
        case .instagramFeed: return parts.isEmpty
        case .instagramReels: return first == "reel" || first == "reels"
        case .instagramExplore: return first == "explore"
        case .xForYou: return parts.isEmpty || first == "home"
        case .redditHome: return parts.isEmpty || (first == "best" || first == "hot") && parts.count == 1
        case .redditPopular: return first == "r" && parts.count >= 2 && (parts[1] == "popular" || parts[1] == "all")
        case .facebookFeed: return parts.isEmpty || first == "home.php"
        case .facebookReels:
            return first == "reel" || first == "reels" || (first == "watch" && parts.count >= 2 && parts[1].hasPrefix("reel"))
                || (first == "share" && parts.count >= 2 && parts[1] == "reel")
        case .facebookWatch: return first == "watch" && !(parts.count >= 2 && parts[1].hasPrefix("reel"))
        case .tiktok: return true
        case .linkedinFeed: return first == "feed"
        case .snapchatSpotlight: return first == "spotlight"
        }
    }
}

/// Days of the week as a Monday-first bit mask, the same as Android's
/// `DailyAppSchedule.days`, so a rule can move between devices unchanged.
public struct Weekdays: OptionSet, Codable, Hashable, Sendable {
    public let rawValue: Int
    public init(rawValue: Int) { self.rawValue = rawValue & 0x7F }

    public static let monday = Weekdays(rawValue: 1 << 0)
    public static let tuesday = Weekdays(rawValue: 1 << 1)
    public static let wednesday = Weekdays(rawValue: 1 << 2)
    public static let thursday = Weekdays(rawValue: 1 << 3)
    public static let friday = Weekdays(rawValue: 1 << 4)
    public static let saturday = Weekdays(rawValue: 1 << 5)
    public static let sunday = Weekdays(rawValue: 1 << 6)
    public static let workdays: Weekdays = [.monday, .tuesday, .wednesday, .thursday, .friday]
    public static let everyDay = Weekdays(rawValue: 0x7F)

    /// The bit for a date's weekday in `calendar`.
    public static func of(_ date: Date, calendar: Calendar) -> Weekdays {
        // Calendar weekday: 1 = Sunday … 7 = Saturday.
        let weekday = calendar.component(.weekday, from: date)
        let mondayFirst = (weekday + 5) % 7  // Monday 0 … Sunday 6
        return Weekdays(rawValue: 1 << mondayFirst)
    }
}

/// A daily time window, like "9:00 to 17:00 on workdays" or "22:00 to 07:00".
/// Start is inclusive, end exclusive; an end before the start crosses midnight
/// and belongs to the day it starts on (Android's rule, kept identical).
public struct TimeWindow: Codable, Hashable, Sendable {
    public var startMinute: Int
    public var endMinute: Int
    public var days: Weekdays

    public init(startMinute: Int, endMinute: Int, days: Weekdays = .everyDay) {
        self.startMinute = ((startMinute % 1440) + 1440) % 1440
        self.endMinute = ((endMinute % 1440) + 1440) % 1440
        self.days = days
    }

    public var isValid: Bool { startMinute != endMinute && !days.isEmpty }

    /// The running window that contains `now`, as a date interval.
    public func interval(containing now: Date, calendar: Calendar) -> DateInterval? {
        guard isValid else { return nil }
        let today = calendar.startOfDay(for: now)
        for offset in [-1, 0] {
            guard let day = calendar.date(byAdding: .day, value: offset, to: today),
                  days.contains(Weekdays.of(day, calendar: calendar)),
                  let start = calendar.date(byAdding: .minute, value: startMinute, to: day)
            else { continue }
            let length = endMinute > startMinute ? endMinute - startMinute : endMinute + 1440 - startMinute
            guard let end = calendar.date(byAdding: .minute, value: length, to: start) else { continue }
            if now >= start && now < end { return DateInterval(start: start, end: end) }
        }
        return nil
    }

    /// The next time the window starts, at or after `now`, within a week.
    public func nextStart(after now: Date, calendar: Calendar) -> Date? {
        guard isValid else { return nil }
        let today = calendar.startOfDay(for: now)
        for offset in 0...7 {
            guard let day = calendar.date(byAdding: .day, value: offset, to: today),
                  days.contains(Weekdays.of(day, calendar: calendar)),
                  let start = calendar.date(byAdding: .minute, value: startMinute, to: day),
                  start >= now
            else { continue }
            return start
        }
        return nil
    }
}

/// How hard a rule is to get around, chosen per rule. Opal has a similar
/// three-step "difficulty"; Unrot's names say what happens rather than how
/// brave the person is.
public enum Strictness: String, Codable, CaseIterable, Sendable {
    /// "5 more minutes" is always offered, and the rule can be changed at once.
    case gentle
    /// "5 more minutes" waits first, longer each time today; making the rule
    /// weaker waits a cooling-off period.
    case firm
    /// No extra minutes and no weakening while the rule is running. The weekly
    /// emergency pass is the only way out.
    case locked
}

/// What a rule does to its targets.
public enum RuleAction: Codable, Hashable, Sendable {
    /// Closed whenever the rule's window is running (or always, without one).
    case block
    /// Open freely until together the targets reach `minutesPerDay` today.
    case dailyLimit(minutesPerDay: Int)
    /// A breathing pause before every open. `opensPerDay` nil means no cap;
    /// `openMinutes` is how long one open lasts before the pause comes back.
    case wait(seconds: Int, grows: Bool, opensPerDay: Int?, openMinutes: Int)

    public static let waitChoices = [5, 10, 15, 30]
    public static let maxWaitSeconds = 60
    public static let growthSeconds = 5
    public static let limitRange = 5...1440
    public static let openMinuteChoices = [5, 10, 15, 30]
}

/// One rule as the person made it.
public struct DistractionRule: Codable, Identifiable, Hashable, Sendable {
    public var id: UUID
    public var name: String
    public var enabled: Bool
    public var targets: Set<RuleTarget>
    public var action: RuleAction
    /// nil: the rule runs all day, every day.
    public var window: TimeWindow?
    public var strictness: Strictness

    public init(id: UUID = UUID(), name: String, enabled: Bool = true, targets: Set<RuleTarget>,
                action: RuleAction, window: TimeWindow? = nil, strictness: Strictness = .gentle) {
        self.id = id
        self.name = name
        self.enabled = enabled
        self.targets = targets
        self.action = action
        self.window = window
        self.strictness = strictness
    }

    public static let maxTargets = 200

    /// Problems that make the rule unusable, or none.
    public var problems: [String] {
        var found: [String] = []
        if name.trimmingCharacters(in: .whitespaces).isEmpty { found.append("name") }
        if targets.isEmpty { found.append("targets") }
        if targets.count > Self.maxTargets { found.append("too-many-targets") }
        if let window, !window.isValid { found.append("window") }
        switch action {
        case .block: break
        case .dailyLimit(let minutes):
            if !RuleAction.limitRange.contains(minutes) { found.append("limit") }
        case .wait(let seconds, _, let opens, let openMinutes):
            if !(1...RuleAction.maxWaitSeconds).contains(seconds) { found.append("wait") }
            if let opens, opens < 1 || opens > 50 { found.append("opens") }
            if !(1...240).contains(openMinutes) { found.append("open-minutes") }
        }
        return found
    }

    /// Whether the rule is in force at `now`.
    public func isRunning(at now: Date, calendar: Calendar) -> Bool {
        guard enabled, problems.isEmpty else { return false }
        guard let window else { return true }
        return window.interval(containing: now, calendar: calendar) != nil
    }

    /// Whether this rule covers what is in front of the person. A site rule
    /// covers its subdomains; a site rule also covers every surface of that
    /// site; a surface rule covers only its surface.
    public func covers(_ subject: Subject) -> Bool {
        targets.contains { Self.target($0, covers: subject) }
    }

    static func target(_ target: RuleTarget, covers subject: Subject) -> Bool {
        switch (target, subject) {
        case let (.app(id), .app(bundleID)):
            return id == bundleID
        case let (.site(host), .page(url)):
            guard let pageHost = url.host?.lowercased() else { return false }
            let bare = pageHost.hasPrefix("www.") ? String(pageHost.dropFirst(4)) : pageHost
            return bare == host || bare.hasSuffix("." + host)
        case let (.surface(surface), .page(url)):
            return surface.matches(url)
        default:
            return false
        }
    }
}

/// What is in front of the person: an app, or a page in a browser.
public enum Subject: Hashable, Sendable {
    case app(String)
    case page(URL)
}

/// Today's counts for the rules, kept by the caller (the usage ledger and the
/// wait panel). Keyed by rule, because a limit or an open cap is shared by all
/// of a rule's targets — "one hour for YouTube, X and Reddit together".
public struct RuleDay: Codable, Equatable, Sendable {
    /// Seconds spent today on the rule's targets.
    public var usedSeconds: [UUID: Double] = [:]
    /// Opens taken today after a wait.
    public var opens: [UUID: Int] = [:]
    /// An open in progress: until when the targets stay open without a wait.
    public var openUntil: [UUID: Date] = [:]
    /// Extra minutes granted today ("5 more minutes"), and until when.
    public var snoozedUntil: [UUID: Date] = [:]
    public var snoozesToday: [UUID: Int] = [:]

    public init() {}
}

/// The answer an enforcer acts on.
public enum RuleDecision: Equatable, Sendable {
    case allow
    /// Show the wait panel. `opensLeft` nil means no cap.
    case wait(rule: UUID, seconds: Int, opensLeft: Int?, openMinutes: Int)
    /// Close it. `until` is when it opens again by itself, if known.
    case block(rule: UUID, reason: BlockReason, until: Date?)

    public var isBlock: Bool { if case .block = self { return true } else { return false } }
}

public enum BlockReason: String, Codable, Sendable {
    case blocked        // a block rule, all day
    case scheduled      // a block rule inside its window
    case limitReached   // today's minutes are used
    case noOpensLeft    // today's opens are used
    case focus          // a Focus session
}

/// A Focus session the person started: either the chosen targets close, or
/// with `allowOnly`, everything distracting closes except the chosen targets.
public struct FocusSessionState: Codable, Equatable, Sendable {
    public var startedAt: Date
    public var until: Date
    public var intent: String
    public var targets: Set<RuleTarget>
    public var allowOnly: Bool
    public var strictness: Strictness

    public init(startedAt: Date, until: Date, intent: String, targets: Set<RuleTarget>,
                allowOnly: Bool = false, strictness: Strictness = .gentle) {
        self.startedAt = startedAt
        self.until = until
        self.intent = intent
        self.targets = targets
        self.allowOnly = allowOnly
        self.strictness = strictness
    }

    public static let maxLength: TimeInterval = 4 * 60 * 60
    public static let lengthChoices = [15, 25, 45, 60, 90]

    public func isRunning(at now: Date) -> Bool { now >= startedAt && now < until }

    /// Progress from 0 to 1, for the sapling.
    public func progress(at now: Date) -> Double {
        let total = until.timeIntervalSince(startedAt)
        guard total > 0 else { return 1 }
        return min(1, max(0, now.timeIntervalSince(startedAt) / total))
    }
}

public enum DistractionEngine {
    /// The decision for `subject` at `now`. The strongest answer wins:
    /// Focus, then block, then a used-up limit or open cap, then a wait.
    /// Apps and pages Unrot itself needs (`alwaysAllowed`) are never touched.
    public static func decide(_ subject: Subject, at now: Date, rules: [DistractionRule], day: RuleDay,
                              focus: FocusSessionState?, distracting: (Subject) -> Bool = { _ in true },
                              alwaysAllowed: Set<String> = defaultAlwaysAllowed,
                              calendar: Calendar) -> RuleDecision {
        if case .app(let id) = subject, alwaysAllowed.contains(id) { return .allow }

        if let focus, focus.isRunning(at: now) {
            let chosen = focus.targets.contains { DistractionRule.target($0, covers: subject) }
            let closes = focus.allowOnly ? (!chosen && distracting(subject)) : chosen
            if closes { return .block(rule: focusRuleID, reason: .focus, until: focus.until) }
        }

        var waits: [(DistractionRule, Int, Int?, Int)] = []
        var blocks: [RuleDecision] = []
        for rule in rules where rule.isRunning(at: now, calendar: calendar) && rule.covers(subject) {
            if let snooze = day.snoozedUntil[rule.id], snooze > now { continue }
            switch rule.action {
            case .block:
                let until = rule.window?.interval(containing: now, calendar: calendar)?.end
                blocks.append(.block(rule: rule.id, reason: rule.window == nil ? .blocked : .scheduled, until: until))
            case .dailyLimit(let minutes):
                if (day.usedSeconds[rule.id] ?? 0) >= Double(minutes) * 60 {
                    blocks.append(.block(rule: rule.id, reason: .limitReached,
                                         until: nextMidnight(after: now, calendar: calendar)))
                }
            case let .wait(seconds, grows, opensPerDay, openMinutes):
                if let open = day.openUntil[rule.id], open > now { continue }
                let taken = day.opens[rule.id] ?? 0
                if let cap = opensPerDay, taken >= cap {
                    blocks.append(.block(rule: rule.id, reason: .noOpensLeft,
                                         until: nextMidnight(after: now, calendar: calendar)))
                } else {
                    let wait = grows ? min(RuleAction.maxWaitSeconds, seconds + taken * RuleAction.growthSeconds) : seconds
                    waits.append((rule, wait, opensPerDay.map { $0 - taken }, openMinutes))
                }
            }
        }
        if let first = blocks.first {
            // A block without an end beats one that ends; otherwise the one that lasts longest.
            return blocks.max { lhs, rhs in
                guard case let .block(_, _, l) = lhs, case let .block(_, _, r) = rhs else { return false }
                switch (l, r) {
                case (nil, nil): return false
                case (nil, _): return false
                case (_, nil): return true
                case let (a?, b?): return a < b
                }
            } ?? first
        }
        if let longest = waits.max(by: { $0.1 < $1.1 }) {
            return .wait(rule: longest.0.id, seconds: longest.1, opensLeft: longest.2, openMinutes: longest.3)
        }
        return .allow
    }

    /// A stable id for blocks caused by Focus, so the panel can tell them apart.
    public static let focusRuleID = UUID(uuidString: "00000000-0000-0000-0000-0000000F0C05")!

    /// Apps a rule may never close: Unrot, the system's own settings and
    /// security apps, so the person can always reach help and settings.
    public static let defaultAlwaysAllowed: Set<String> = [
        "app.hisn.Hisn", "app.hisn.Unrot",
        "com.apple.finder", "com.apple.systempreferences", "com.apple.Settings",
        "com.apple.SecurityAgent", "com.apple.loginwindow", "com.apple.dock",
        "com.apple.Terminal", "com.apple.ActivityMonitor", "com.apple.FaceTime",
        "com.apple.mobilephone", "com.apple.Passwords", "com.apple.keychainaccess",
    ]

    static func nextMidnight(after now: Date, calendar: Calendar) -> Date? {
        calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: now))
    }

    /// The day after a person chose "Open" on the wait panel.
    public static func recordingOpen(_ day: RuleDay, rule: DistractionRule, at now: Date) -> RuleDay {
        guard case let .wait(_, _, _, openMinutes) = rule.action else { return day }
        var next = day
        next.opens[rule.id, default: 0] += 1
        next.openUntil[rule.id] = now.addingTimeInterval(TimeInterval(openMinutes * 60))
        return next
    }
}

// MARK: - Extra minutes and the way out

public enum SnoozePolicy {
    public static let snoozeMinutes = 5

    /// Before "5 more minutes" is granted: nil when it is not offered at all,
    /// otherwise how many seconds the person must wait first.
    public static func delayBeforeSnooze(strictness: Strictness, snoozesToday: Int) -> Int? {
        switch strictness {
        case .gentle: return 0
        case .firm:
            // 15 s, 30 s, 60 s, 120 s … capped at 5 minutes: each extra five
            // minutes costs more than the last, which is the point.
            let steps = min(snoozesToday, 5)
            return min(300, 15 << steps)
        case .locked: return nil
        }
    }

    public static func granting(_ day: RuleDay, rule: UUID, at now: Date) -> RuleDay {
        var next = day
        next.snoozedUntil[rule] = now.addingTimeInterval(TimeInterval(snoozeMinutes * 60))
        next.snoozesToday[rule, default: 0] += 1
        return next
    }
}

/// Changing a rule. Stronger takes effect now; weaker depends on strictness
/// and on the Mac's lock, the same principle as `LockSchedule`.
public enum RuleChange {
    public enum Verdict: Equatable {
        case now
        /// Saved as pending; applied by itself at this time.
        case after(Date)
        /// Refused until this time (the rule's window end or the lock's end).
        case refused(until: Date?)
    }

    /// Cooling-off for a firm rule: the person decides now, the Mac applies it
    /// later, when the urge that asked for it has usually passed.
    public static let firmDelay: TimeInterval = 10 * 60
    /// The same 24 hours `LockSchedule` makes a weaker schedule wait.
    public static let lockedAllDayDelay: TimeInterval = 24 * 60 * 60

    public static func verdict(from old: DistractionRule?, to new: DistractionRule?, lockUntil: Date?,
                               at now: Date, calendar: Calendar) -> Verdict {
        guard let old else { return .now }  // a new rule only adds
        guard old.enabled, loosens(from: old, to: new) else { return .now }
        if let lockUntil, lockUntil > now { return .refused(until: lockUntil) }
        switch old.strictness {
        case .gentle:
            return .now
        case .firm:
            return .after(now.addingTimeInterval(firmDelay))
        case .locked:
            // An all-day locked rule never stops running, so refusing would be
            // forever. It gets the lock schedule's answer instead: a day's wait.
            if old.window == nil { return .after(now.addingTimeInterval(lockedAllDayDelay)) }
            if old.isRunning(at: now, calendar: calendar) {
                let end = old.window?.interval(containing: now, calendar: calendar)?.end
                return .refused(until: end)
            }
            // A locked rule that is not running right now can be changed, but
            // not in the last hour before it starts.
            if let start = old.window?.nextStart(after: now, calendar: calendar),
               start.timeIntervalSince(now) < 60 * 60 {
                return .refused(until: old.window?.interval(containing: start, calendar: calendar)?.end)
            }
            return .now
        }
    }

    /// Whether `new` lets through anything `old` held back. A removed or
    /// disabled rule loosens; so does a removed target, a gentler action, a
    /// shorter window or a gentler strictness.
    public static func loosens(from old: DistractionRule, to new: DistractionRule?) -> Bool {
        guard let new, new.enabled || !old.enabled else { return old.enabled }
        if !old.targets.isSubset(of: new.targets) { return true }
        if strictnessRank(new.strictness) < strictnessRank(old.strictness) { return true }
        if windowLoosens(from: old.window, to: new.window) { return true }
        switch (old.action, new.action) {
        case (.block, .block): return false
        case (.block, _): return true
        case let (.dailyLimit(a), .dailyLimit(b)): return b > a
        case (.dailyLimit, .block): return false
        case (.dailyLimit, .wait): return true
        case let (.wait(s1, g1, o1, m1), .wait(s2, g2, o2, m2)):
            if s2 < s1 || (g1 && !g2) || m2 > m1 { return true }
            switch (o1, o2) {
            case (nil, _): return false
            case (_?, nil): return true
            case let (a?, b?): return b > a
            }
        case (.wait, .block): return false
        case (.wait, .dailyLimit): return true
        }
    }

    static func strictnessRank(_ s: Strictness) -> Int {
        switch s { case .gentle: return 0; case .firm: return 1; case .locked: return 2 }
    }

    /// A window loosens when it no longer covers every minute it covered:
    /// fewer days, a later start or an earlier end. Going from a window to
    /// all-day is stronger; from all-day to a window is weaker.
    static func windowLoosens(from old: TimeWindow?, to new: TimeWindow?) -> Bool {
        guard let old else { return new != nil }
        guard let new else { return false }
        if !old.days.isSubset(of: new.days) { return true }
        return !minutes(of: old).isSubset(of: minutes(of: new))
    }

    private static func minutes(of window: TimeWindow) -> Set<Int> {
        var set = Set<Int>()
        var m = window.startMinute
        while m != window.endMinute {
            set.insert(m)
            m = (m + 1) % 1440
        }
        return set
    }
}

/// The weekly way out of locked rules and Focus: one use every seven days.
/// It ends every running block for the rest of the current window and is told
/// to the partner, when there is one.
public enum EmergencyPass {
    public static let period: TimeInterval = 7 * 24 * 60 * 60

    public static func available(lastUsed: Date?, at now: Date) -> Bool {
        guard let lastUsed else { return true }
        return now.timeIntervalSince(lastUsed) >= period || now < lastUsed
    }

    public static func nextAvailable(lastUsed: Date?, at now: Date) -> Date? {
        guard let lastUsed, !available(lastUsed: lastUsed, at: now) else { return nil }
        return lastUsed.addingTimeInterval(period)
    }
}

// MARK: - Time on distractions

/// Where the Mac's time went today, built from "this was in front from … to …"
/// reports by the app watcher and the extensions. Intervals that overlap
/// (the app watcher sees Safari, the extension sees YouTube inside it) are
/// counted once per rule, and a day boundary splits an interval.
public struct UsageLedger: Codable, Equatable, Sendable {
    public struct Span: Codable, Equatable, Sendable {
        public var subject: String   // "app:<id>" or "site:<host>"
        public var start: Date
        public var end: Date
    }

    public private(set) var day: String
    public private(set) var spans: [Span] = []

    /// A report longer than this is a sleeping Mac or a stuck clock, not use.
    public static let maxSpan: TimeInterval = 30 * 60
    public static let maxSpans = 20_000

    public init(day: String) { self.day = day }

    public static func dayKey(_ date: Date, calendar: Calendar) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    /// Adds a span, cut to `day` and to `maxSpan`. Spans for another day are ignored.
    public mutating func record(subject: String, from start: Date, to end: Date, calendar: Calendar) {
        guard end > start, spans.count < Self.maxSpans else { return }
        let dayStart = calendar.startOfDay(for: start)
        guard Self.dayKey(dayStart, calendar: calendar) == day,
              let dayEnd = calendar.date(byAdding: .day, value: 1, to: dayStart) else { return }
        let clippedEnd = min(end, dayEnd, start.addingTimeInterval(Self.maxSpan))
        guard clippedEnd > start else { return }
        if let last = spans.last, last.subject == subject, abs(last.end.timeIntervalSince(start)) < 2 {
            spans[spans.count - 1].end = max(last.end, clippedEnd)
        } else {
            spans.append(Span(subject: subject, start: start, end: clippedEnd))
        }
    }

    /// Seconds today on anything `rule` covers. Overlapping spans count once.
    public func seconds(for rule: DistractionRule) -> Double {
        let covered = spans.filter { span in
            if span.subject.hasPrefix("app:") {
                return rule.covers(.app(String(span.subject.dropFirst(4))))
            }
            if span.subject.hasPrefix("site:"), let url = URL(string: "https://" + span.subject.dropFirst(5) + "/") {
                return rule.targets.contains { target in
                    if case .site = target { return DistractionRule.target(target, covers: .page(url)) }
                    return false
                }
            }
            if span.subject.hasPrefix("page:"), let url = URL(string: String(span.subject.dropFirst(5))) {
                return rule.covers(.page(url))
            }
            return false
        }
        return Self.unionSeconds(covered.map { ($0.start, $0.end) })
    }

    /// Seconds per subject, largest first — the inspector's "Top pulls".
    public func topSubjects(limit: Int = 5) -> [(subject: String, seconds: Double)] {
        var bySubject: [String: [(Date, Date)]] = [:]
        for span in spans { bySubject[span.subject, default: []].append((span.start, span.end)) }
        return bySubject.map { ($0.key, Self.unionSeconds($0.value)) }
            .sorted { $0.1 > $1.1 }
            .prefix(limit)
            .map { (subject: $0.0, seconds: $0.1) }
    }

    /// Seconds in each hour of the day, for the hour strip.
    public func secondsByHour(calendar: Calendar) -> [Double] {
        var hours = Array(repeating: 0.0, count: 24)
        // Merge first: Safari and the YouTube tab inside it are one minute, not two.
        for span in Self.union(spans.map { ($0.start, $0.end) }) {
            var cursor = span.0
            while cursor < span.1 {
                let hour = calendar.component(.hour, from: cursor)
                guard let nextHour = calendar.dateInterval(of: .hour, for: cursor)?.end else { break }
                let stop = min(nextHour, span.1)
                hours[hour] += stop.timeIntervalSince(cursor)
                cursor = stop
            }
        }
        return hours
    }

    static func union(_ intervals: [(Date, Date)]) -> [(Date, Date)] {
        var merged: [(Date, Date)] = []
        for interval in intervals.sorted(by: { $0.0 < $1.0 }) {
            if let last = merged.last, interval.0 <= last.1 {
                merged[merged.count - 1].1 = max(last.1, interval.1)
            } else {
                merged.append(interval)
            }
        }
        return merged
    }

    static func unionSeconds(_ intervals: [(Date, Date)]) -> Double {
        union(intervals).reduce(0) { $0 + $1.1.timeIntervalSince($1.0) }
    }
}

/// Time given back: the person's own "before" (asked in first run, or the
/// first week's average) minus today's feed time, never below zero. A heavy
/// day gives back nothing; it never takes anything away. Same rule as Android.
public enum TimeGivenBack {
    public static func seconds(beforePerDay: Double, feedSecondsByDay: [Double]) -> Double {
        feedSecondsByDay.reduce(0) { $0 + max(0, beforePerDay - $1) }
    }

    /// Where each of the sprout's seven stages starts, in seconds given back.
    /// The same thresholds as Android's `glassStageAt` (UnrotGlassHero.kt).
    public static let stageStarts: [Double] = [0, 5 * 60, 15 * 60, 3600, 3 * 3600, 8 * 3600, 24 * 3600]

    /// Stage 1 to 7.
    public static func stage(forSeconds seconds: Double) -> Int {
        (stageStarts.lastIndex { seconds >= $0 } ?? 0) + 1
    }
}
