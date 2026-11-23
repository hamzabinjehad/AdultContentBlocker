import Combine
import Foundation

/// Everything the distraction rules need between launches: the rules, changes
/// waiting for their cooling-off, a running Focus, the emergency pass, and
/// today's counts. Pure and `Codable`; `DistractionStore` saves it.
public struct RuleBook: Codable, Equatable, Sendable {
    public struct PendingChange: Codable, Equatable, Sendable, Identifiable {
        public var id: UUID
        /// The rule this change replaces (nil for none).
        public var ruleID: UUID
        /// The rule after the change; nil deletes it.
        public var replacement: DistractionRule?
        public var applyAt: Date
    }

    public var rules: [DistractionRule] = []
    public var pending: [PendingChange] = []
    public var focus: FocusSessionState?
    public var emergencyPassUsed: Date?
    public var dayKey: String = ""
    public var day = RuleDay()
    /// Today's usage, for limits and Progress.
    public var ledger = UsageLedger(day: "")
    /// Past days' feed seconds, newest last, for time given back and Progress.
    public var history: [DayTotal] = []
    /// The person's own "before", in seconds of feeds a day (asked in first run).
    public var beforePerDay: Double = 105 * 60

    public struct DayTotal: Codable, Equatable, Sendable {
        public var day: String
        public var feedSeconds: Double
        public var pauses: Int
        public var closed: Int
    }

    public static let maxRules = 100
    public static let maxHistory = 400

    public init() {}

    /// The result of asking for a change.
    public enum Outcome: Equatable {
        case applied
        case scheduled(Date)
        case refused(until: Date?)
        case invalid([String])
    }

    /// Adds, edits or deletes (`new == nil`) a rule under the strictness rules.
    public mutating func propose(_ new: DistractionRule?, replacing id: UUID?, lockUntil: Date?,
                                 at now: Date, calendar: Calendar) -> Outcome {
        if let new, !new.problems.isEmpty { return .invalid(new.problems) }
        let old = id.flatMap { id in rules.first { $0.id == id } }
        if old == nil, new != nil, rules.count >= Self.maxRules { return .invalid(["too-many-rules"]) }
        switch RuleChange.verdict(from: old, to: new, lockUntil: lockUntil, at: now, calendar: calendar) {
        case .now:
            apply(new, replacing: old?.id)
            return .applied
        case .after(let date):
            guard let old else { apply(new, replacing: nil); return .applied }
            // One waiting change per rule: the newest wish replaces the last.
            pending.removeAll { $0.ruleID == old.id }
            pending.append(PendingChange(id: UUID(), ruleID: old.id, replacement: new, applyAt: date))
            return .scheduled(date)
        case .refused(let until):
            return .refused(until: until)
        }
    }

    /// Cancels a waiting change: always allowed, it only keeps the rule as it is.
    public mutating func cancelPending(_ id: UUID) {
        pending.removeAll { $0.id == id }
    }

    /// Applies waiting changes whose time has come. Returns whether anything changed.
    @discardableResult
    public mutating func applyDue(at now: Date) -> Bool {
        let due = pending.filter { $0.applyAt <= now }
        guard !due.isEmpty else { return false }
        pending.removeAll { $0.applyAt <= now }
        for change in due { apply(change.replacement, replacing: change.ruleID) }
        return true
    }

    private mutating func apply(_ new: DistractionRule?, replacing id: UUID?) {
        if let id, let index = rules.firstIndex(where: { $0.id == id }) {
            if let new { rules[index] = new } else { rules.remove(at: index) }
        } else if let new {
            rules.append(new)
        }
    }

    /// Starts a new local day: yesterday's totals go to history, counts reset.
    public mutating func roll(to key: String) {
        guard key != dayKey else { return }
        if !dayKey.isEmpty {
            let feedSeconds = ledger.day == dayKey ? feedSecondsToday() : 0
            history.append(DayTotal(day: dayKey, feedSeconds: feedSeconds,
                                    pauses: day.opens.values.reduce(0, +), closed: closedToday))
            if history.count > Self.maxHistory { history.removeFirst(history.count - Self.maxHistory) }
        }
        dayKey = key
        day = RuleDay()
        ledger = UsageLedger(day: key)
        closedToday = 0
        pausesToday = 0
    }

    /// How many times today a rule closed something, and how many waits were shown.
    public var closedToday = 0
    public var pausesToday = 0

    /// Feed time today: anything any limit, wait or block rule covers, counted once.
    public func feedSecondsToday() -> Double {
        let all = DistractionRule(name: "All", targets: Set(rules.flatMap(\.targets)), action: .block)
        guard !all.targets.isEmpty else { return 0 }
        return ledger.seconds(for: all)
    }

    /// Time given back so far. Past days count in full; today counts the part
    /// of the day that has passed, so the number grows through the day instead
    /// of jumping at midnight, and an early scroll is felt at once.
    public func givenBackSeconds(at now: Date, calendar: Calendar) -> Double {
        let past = TimeGivenBack.seconds(beforePerDay: beforePerDay, feedSecondsByDay: history.map(\.feedSeconds))
        let start = calendar.startOfDay(for: now)
        let fraction = min(1, max(0, now.timeIntervalSince(start) / 86_400))
        return past + max(0, beforePerDay * fraction - feedSecondsToday())
    }

    /// Seconds used today per rule, from the ledger, written into `day`.
    public mutating func refreshUsage() {
        for rule in rules {
            if case .dailyLimit = rule.action { day.usedSeconds[rule.id] = ledger.seconds(for: rule) }
        }
    }

    // MARK: Focus

    public enum FocusEnd: Equatable {
        case now
        case afterWait(seconds: Int)
        case notUntil(Date)
    }

    public mutating func startFocus(_ session: FocusSessionState) -> Bool {
        guard session.until > session.startedAt,
              session.until.timeIntervalSince(session.startedAt) <= FocusSessionState.maxLength else { return false }
        if let focus, focus.isRunning(at: session.startedAt), focus.until > session.until { return false }
        focus = session
        return true
    }

    /// Ending Focus early follows its strictness, like a rule.
    public func howToEndFocus(at now: Date) -> FocusEnd {
        guard let focus, focus.isRunning(at: now) else { return .now }
        switch focus.strictness {
        case .gentle: return .now
        case .firm: return .afterWait(seconds: 30)
        case .locked: return .notUntil(focus.until)
        }
    }

    public mutating func endFocus() { focus = nil }

    // MARK: Emergency pass

    /// Uses the weekly pass: ends Focus and lifts every running locked or firm
    /// rule until the end of its current window (or of the day). The adult
    /// layer is elsewhere and untouched.
    public mutating func useEmergencyPass(at now: Date, calendar: Calendar) -> Bool {
        guard EmergencyPass.available(lastUsed: emergencyPassUsed, at: now) else { return false }
        emergencyPassUsed = now
        focus = nil
        let endOfDay = calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: now)) ?? now
        for rule in rules where rule.isRunning(at: now, calendar: calendar) {
            let end = rule.window?.interval(containing: now, calendar: calendar)?.end ?? endOfDay
            day.snoozedUntil[rule.id] = end
        }
        return true
    }
}

/// Saves the rule book in the app group, next to the other Unrot settings, so
/// the bridge can hand the rules to the browser extensions.
@MainActor
public final class DistractionStore: ObservableObject {
    public static let shared = DistractionStore()
    public static let key = "unrot.rulebook.v1"

    @Published public private(set) var book: RuleBook

    private let defaults: UserDefaults?
    public var calendar = Calendar.current
    public var clock: () -> Date = { LockStore.trustedNow() }
    public var lockUntil: () -> Date? = {
        LockStore.isLocked() ? LockStore.effectiveDeadline(LockStore.read()) : nil
    }

    public init(defaults: UserDefaults? = UserDefaults(suiteName: LockStore.appGroup)) {
        self.defaults = defaults
        if let data = defaults?.data(forKey: Self.key),
           let saved = try? JSONDecoder().decode(RuleBook.self, from: data) {
            book = saved
        } else {
            book = RuleBook()
        }
        tick()
    }

    /// Rolls the day, applies due changes and refreshes limits. Called by the
    /// app watcher every few seconds and before every decision.
    public func tick() {
        let now = clock()
        var next = book
        next.roll(to: UsageLedger.dayKey(now, calendar: calendar))
        next.applyDue(at: now)
        next.refreshUsage()
        if let focus = next.focus, !focus.isRunning(at: now), now >= focus.until { next.focus = nil }
        commit(next)
    }

    @discardableResult
    public func propose(_ rule: DistractionRule?, replacing id: UUID?) -> RuleBook.Outcome {
        var next = book
        let outcome = next.propose(rule, replacing: id, lockUntil: lockUntil(), at: clock(), calendar: calendar)
        commit(next)
        return outcome
    }

    public func cancelPending(_ id: UUID) { mutate { $0.cancelPending(id) } }

    public func decide(_ subject: Subject) -> RuleDecision {
        DistractionEngine.decide(subject, at: clock(), rules: book.rules, day: book.day,
                                 focus: book.focus, calendar: calendar)
    }

    public func record(subject: String, from start: Date, to end: Date) {
        mutate { $0.ledger.record(subject: subject, from: start, to: end, calendar: calendar) }
    }

    public func recordOpen(rule id: UUID) {
        guard let rule = book.rules.first(where: { $0.id == id }) else { return }
        let now = clock()
        mutate { $0.day = DistractionEngine.recordingOpen($0.day, rule: rule, at: now) }
    }

    public func recordPause() { mutate { $0.pausesToday += 1 } }
    public func recordClosed() { mutate { $0.closedToday += 1 } }

    public func snooze(rule id: UUID) {
        let now = clock()
        mutate { $0.day = SnoozePolicy.granting($0.day, rule: id, at: now) }
    }

    public func startFocus(minutes: Int, intent: String, targets: Set<RuleTarget>, allowOnly: Bool,
                           strictness: Strictness) -> Bool {
        let now = clock()
        let session = FocusSessionState(startedAt: now, until: now.addingTimeInterval(TimeInterval(minutes * 60)),
                                        intent: intent, targets: targets, allowOnly: allowOnly,
                                        strictness: strictness)
        var next = book
        guard next.startFocus(session) else { return false }
        commit(next)
        return true
    }

    public func endFocus() { mutate { $0.endFocus() } }

    public func useEmergencyPass() -> Bool {
        var next = book
        let used = next.useEmergencyPass(at: clock(), calendar: calendar)
        if used { commit(next) }
        return used
    }

    public func setBefore(minutesPerDay: Int) {
        mutate { $0.beforePerDay = Double(max(0, min(1440, minutesPerDay)) * 60) }
    }

    private func mutate(_ change: (inout RuleBook) -> Void) {
        var next = book
        change(&next)
        commit(next)
    }

    private func commit(_ next: RuleBook) {
        guard next != book else { return }
        book = next
        if let data = try? JSONEncoder().encode(next) { defaults?.set(data, forKey: Self.key) }
    }
}
