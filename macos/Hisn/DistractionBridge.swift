import Foundation

/// The browser's side of the distraction rules, spoken over native messaging.
///
/// The extension never holds the rules. For each page it asks the bridge
/// "what should happen here?", and it reports back what the person did: an
/// open after a wait, and how long a site was in front. The bridge process is
/// short-lived and separate from the app, so it writes nothing to the rule
/// book: reports go to an inbox the app drains (`DistractionStore.tick`).
///
/// Only hosts and rule ids cross this channel. Page titles, paths of allowed
/// pages and search text are never stored.
public enum DistractionBridge {
    public static let inboxKey = "unrot.inbox.v1"
    public static let maxInbox = 500

    public struct Report: Codable, Equatable, Sendable {
        public enum Kind: String, Codable, Sendable { case open, usage, snooze }
        public var kind: Kind
        public var rule: UUID?
        public var host: String?
        public var from: Date
        public var to: Date
    }

    /// The reply to `{"type":"distraction","op":"decide","url":…}`.
    public static func decide(url: String, book: RuleBook, inbox: [Report], now: Date,
                              calendar: Calendar) -> [String: Any] {
        guard let page = URL(string: url), let scheme = page.scheme?.lowercased(),
              scheme == "http" || scheme == "https" else { return ["ok": true, "action": "allow"] }
        var day = book.day
        // Reports the app has not drained yet still count: without this, the
        // page reloaded after "Open" would be asked to wait again.
        for report in inbox {
            guard let id = report.rule, let rule = book.rules.first(where: { $0.id == id }) else { continue }
            switch report.kind {
            case .open: day = DistractionEngine.recordingOpen(day, rule: rule, at: report.from)
            case .snooze: day = SnoozePolicy.granting(day, rule: id, at: report.from)
            case .usage: break
            }
        }
        let decision = DistractionEngine.decide(.page(page), at: now, rules: book.rules, day: day,
                                                focus: book.focus, exceptions: book.exceptions, calendar: calendar)
        switch decision {
        case .allow:
            return ["ok": true, "action": "allow"]
        case let .wait(id, seconds, opensLeft, openMinutes):
            var reply: [String: Any] = ["ok": true, "action": "wait", "rule": id.uuidString,
                                        "ruleName": name(of: id, in: book), "seconds": seconds,
                                        "openMinutes": openMinutes]
            if let opensLeft { reply["opensLeft"] = opensLeft }
            if let own = book.ownReason { reply["own"] = own }
            return reply
        case let .block(id, reason, until):
            let rule = book.rules.first { $0.id == id }
            var reply: [String: Any] = ["ok": true, "action": "block", "rule": id.uuidString,
                                        "ruleName": name(of: id, in: book), "reason": reason.rawValue]
            if let own = book.ownReason { reply["own"] = own }
            reply["pass"] = EmergencyPass.available(lastUsed: book.emergencyPassUsed, at: now)
            if let until { reply["until"] = until.timeIntervalSince1970 * 1000 }
            // A feed surface on a page that is otherwise allowed: the extension
            // hides the feed and leaves the page, instead of closing the tab.
            if let rule, let surface = feedOnlySurface(rule: rule, page: page) {
                reply["action"] = "hideFeed"
                reply["surface"] = surface.rawValue
            }
            let strictness = reason == .focus ? (book.focus?.strictness ?? .gentle) : (rule?.strictness ?? .gentle)
            if reason != .focus,
               let delay = SnoozePolicy.delayBeforeSnooze(strictness: strictness,
                                                          snoozesToday: day.snoozesToday[id] ?? 0) {
                reply["snoozeDelay"] = delay
            }
            if reason == .focus, let intent = book.focus?.intent, !intent.isEmpty { reply["intent"] = intent }
            return reply
        }
    }

    /// The home feeds that are part of a page people still need (YouTube's
    /// front page has the search box). Short-video players are whole pages and
    /// are closed like any other.
    static func feedOnlySurface(rule: DistractionRule, page: URL) -> FeedSurface? {
        for case let .surface(surface) in rule.targets where !surface.isShortVideo && surface.matches(page) {
            // A whole-site rule in the same rule closes the page anyway.
            let siteToo = rule.targets.contains { target in
                if case .site = target { return DistractionRule.target(target, covers: .page(page)) }
                return false
            }
            return siteToo ? nil : surface
        }
        return nil
    }

    private static func name(of id: UUID, in book: RuleBook) -> String {
        book.rules.first { $0.id == id }?.name ?? "Focus"
    }

    /// Validates a report from the extension. Usage is cut to a sane length and
    /// to a real host; anything else is dropped.
    public static func report(from message: [String: Any], now: Date) -> Report? {
        switch message["op"] as? String {
        case "opened", "snoozed":
            guard let text = message["rule"] as? String, let id = UUID(uuidString: text) else { return nil }
            return Report(kind: message["op"] as? String == "opened" ? .open : .snooze,
                          rule: id, host: nil, from: now, to: now)
        case "usage":
            guard let raw = message["host"] as? String, let host = RuleTarget.normalizedSite(raw),
                  let seconds = (message["seconds"] as? NSNumber)?.doubleValue,
                  seconds >= 1, seconds <= UsageLedger.maxSpan else { return nil }
            return Report(kind: .usage, rule: nil, host: host, from: now.addingTimeInterval(-seconds), to: now)
        default:
            return nil
        }
    }

    public static func loadInbox(_ defaults: UserDefaults?) -> [Report] {
        guard let data = defaults?.data(forKey: inboxKey) else { return [] }
        return (try? JSONDecoder().decode([Report].self, from: data)) ?? []
    }

    public static func append(_ report: Report, to defaults: UserDefaults?) {
        var inbox = loadInbox(defaults)
        inbox.append(report)
        if inbox.count > maxInbox { inbox.removeFirst(inbox.count - maxInbox) }
        if let data = try? JSONEncoder().encode(inbox) { defaults?.set(data, forKey: inboxKey) }
    }

    public static func loadBook(_ defaults: UserDefaults?) -> RuleBook {
        guard let data = defaults?.data(forKey: DistractionStore.key),
              let book = try? JSONDecoder().decode(RuleBook.self, from: data) else { return RuleBook() }
        return book
    }

    /// Sites people lose time in. The extension reports time for these and for
    /// rule sites only, so ordinary browsing never crosses the channel.
    public static let knownDistracting: Set<String> = [
        "x.com", "instagram.com", "facebook.com", "reddit.com", "tiktok.com", "threads.net", "snapchat.com",
        "pinterest.com", "linkedin.com", "tumblr.com", "bsky.app",
        "youtube.com", "twitch.tv", "netflix.com", "primevideo.com", "disneyplus.com", "vimeo.com", "dailymotion.com",
        "news.google.com", "bbc.com", "cnn.com", "nytimes.com", "theguardian.com", "news.ycombinator.com",
        "spiegel.de", "aljazeera.com",
        "amazon.com", "ebay.com", "aliexpress.com", "temu.com", "etsy.com", "shein.com",
        "store.steampowered.com", "epicgames.com", "roblox.com", "chess.com", "poki.com",
        "discord.com", "web.whatsapp.com", "web.telegram.org", "messenger.com", "slack.com",
    ]

    /// The reply to `op: "watch"`: which hosts the extension must ask about.
    /// `all` is true while a Focus session closes everything but its chosen
    /// sites, because then every page needs a decision.
    public static func watch(book: RuleBook, now: Date) -> [String: Any] {
        var hosts = Set<String>()
        func add(_ targets: Set<RuleTarget>) {
            for target in targets {
                switch target {
                case let .site(host): hosts.insert(host)
                case let .surface(surface): hosts.formUnion(surface.aliases)
                case .app: break
                }
            }
        }
        for rule in book.rules where rule.enabled { add(rule.targets) }
        var all = false
        var focusUntil: Double?
        if let focus = book.focus, focus.isRunning(at: now) {
            add(focus.targets)
            all = focus.allowOnly
            focusUntil = focus.until.timeIntervalSince1970 * 1000
        }
        var reply: [String: Any] = ["ok": true, "all": all, "hosts": hosts.sorted(),
                                    "usageHosts": knownDistracting.union(hosts).sorted()]
        if let focusUntil { reply["focusUntil"] = focusUntil }
        if let reason = book.ownReason { reply["reason"] = reason }
        return reply
    }

    /// The whole native-messaging case, for `HisnBridge/main.swift`.
    public static func handle(_ message: [String: Any], defaults: UserDefaults?, now: Date,
                              calendar: Calendar = .current) -> [String: Any] {
        if message["op"] as? String == "decide" {
            guard let url = message["url"] as? String else { return ["ok": false, "reason": "no-url"] }
            return decide(url: url, book: loadBook(defaults), inbox: loadInbox(defaults), now: now, calendar: calendar)
        }
        if message["op"] as? String == "watch" { return watch(book: loadBook(defaults), now: now) }
        guard let report = report(from: message, now: now) else { return ["ok": false, "reason": "bad-report"] }
        // A snooze the rule's strictness does not offer is refused here, not trusted from the page.
        if report.kind == .snooze, let id = report.rule {
            let book = loadBook(defaults)
            guard let rule = book.rules.first(where: { $0.id == id }),
                  SnoozePolicy.delayBeforeSnooze(strictness: rule.strictness,
                                                 snoozesToday: book.day.snoozesToday[id] ?? 0) != nil
            else { return ["ok": false, "reason": "not-offered"] }
        }
        append(report, to: defaults)
        return ["ok": true]
    }
}

extension RuleBook {
    /// Applies the browser's reports. Called by the app, which owns the book.
    public mutating func drain(_ inbox: [DistractionBridge.Report], calendar: Calendar) {
        for report in inbox {
            switch report.kind {
            case .open:
                guard let id = report.rule, let rule = rules.first(where: { $0.id == id }) else { continue }
                day = DistractionEngine.recordingOpen(day, rule: rule, at: report.from)
                pausesToday += 1
            case .snooze:
                guard let id = report.rule, rules.contains(where: { $0.id == id }) else { continue }
                day = SnoozePolicy.granting(day, rule: id, at: report.from)
            case .usage:
                guard let host = report.host else { continue }
                ledger.record(subject: "site:" + host, from: report.from, to: report.to, calendar: calendar)
            }
        }
    }
}
