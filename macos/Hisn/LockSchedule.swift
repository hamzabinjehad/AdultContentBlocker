import Foundation

/// A lock that starts itself every day — "every night from 22:00 to 07:00".
///
/// The strongest commitment is the one that does not wait to be made at the
/// weak moment. At the window's start the app begins an ordinary lock that
/// ends with the window, through `LockManager.start`, so everything a lock
/// already does (the filter, the extension, the browser guard, the partner
/// and 48-hour exits) applies unchanged; a longer lock already running is left
/// alone.
///
/// The schedule obeys the rule every other setting does: making it stronger
/// takes effect at once, making it weaker waits — here 24 hours, and the
/// window does not have to be running for that to apply. Without the delay,
/// switching the schedule off at 21:59 would be the way out of the lock at
/// 22:00, and the schedule would protect exactly the evenings it was not
/// needed.
public struct LockSchedule: Codable, Equatable {
    public var enabled: Bool
    /// Minutes after local midnight. A window that crosses midnight
    /// (22:00–07:00, start > end) is the usual case.
    public var start: Int
    public var end: Int
    public var strict: Bool

    public init(enabled: Bool, start: Int, end: Int, strict: Bool) {
        self.enabled = enabled
        self.start = ((start % 1440) + 1440) % 1440
        self.end = ((end % 1440) + 1440) % 1440
        self.strict = strict
    }

    /// What the page shows before anything is saved: off, 22:00–07:00.
    public static let suggested = LockSchedule(enabled: false, start: 22 * 60, end: 7 * 60, strict: false)

    public var lengthMinutes: Int { end > start ? end - start : end + 1440 - start }

    /// The window that contains `now`, if any.
    public func window(containing now: Date, calendar: Calendar = .current) -> DateInterval? {
        guard enabled, start != end else { return nil }
        let today = calendar.startOfDay(for: now)
        // A window crossing midnight that contains `now` began yesterday or today.
        for dayOffset in [-1, 0] {
            guard let day = calendar.date(byAdding: .day, value: dayOffset, to: today),
                  let begins = calendar.date(byAdding: .minute, value: start, to: day)
            else { continue }
            let window = DateInterval(start: begins, duration: TimeInterval(lengthMinutes * 60))
            if now >= window.start && now < window.end { return window }
        }
        return nil
    }

    /// The window to lock now: the one containing `now`, when the lock that
    /// is running (if any) ends before it does. Nil when nothing needs doing.
    public func lockNeeded(at now: Date, lockedUntil: Date?,
                           calendar: Calendar = .current) -> DateInterval? {
        guard let window = window(containing: now, calendar: calendar) else { return nil }
        // A minute of slack, so a lock already ending with the window is not
        // restarted every tick over rounding.
        if let until = lockedUntil, until >= window.end.addingTimeInterval(-60) { return nil }
        // Shorter than the shortest lock: the window is as good as over.
        guard window.end.timeIntervalSince(now) >= LockManager.minimumLock else { return nil }
        return window
    }

    /// Whether `next` protects less than this schedule does: off where this
    /// was on, standard where this was strict, or a window that no longer
    /// covers every minute of this one.
    public func isLoosened(by next: LockSchedule) -> Bool {
        guard enabled else { return false }
        if !next.enabled { return true }
        if strict && !next.strict { return true }
        return !next.covers(self)
    }

    /// Every minute of `other`'s window lies inside this one.
    func covers(_ other: LockSchedule) -> Bool {
        let offset = ((other.start - start) % 1440 + 1440) % 1440
        return offset + other.lengthMinutes <= lengthMinutes
    }
}

/// Where the schedule and a waiting weaker change are kept, and the rule
/// between them. In the app group beside the lock, under the same test
/// namespace.
public enum ScheduleStore {
    /// How long a weaker schedule waits.
    public static let loosenDelay: TimeInterval = 24 * 3600

    private static let key = "lockSchedule"
    private static let pendingKey = "lockSchedulePending"
    private static let pendingAtKey = "lockSchedulePendingAt"

    private static var defaults: UserDefaults? { UserDefaults(suiteName: LockStore.appGroup) }

    public static func current() -> LockSchedule {
        decode(defaults?.data(forKey: key)) ?? .suggested
    }

    /// A weaker schedule waiting for its time, and when that is.
    public static func pending() -> (schedule: LockSchedule, at: Date)? {
        guard let s = decode(defaults?.data(forKey: pendingKey)),
              let at = defaults?.object(forKey: pendingAtKey) as? Date else { return nil }
        return (s, at)
    }

    public enum Outcome: Equatable {
        case applied
        case waiting(until: Date)
    }

    /// Save a change: at once when it does not weaken the schedule, otherwise
    /// in `loosenDelay`. Any new request replaces a waiting one — and a
    /// weaker one restarts the wait, so a change cannot be queued early and
    /// swapped for a bigger one later.
    @discardableResult
    public static func request(_ next: LockSchedule, now: Date = LockStore.trustedNow()) -> Outcome {
        clearPending()
        if current().isLoosened(by: next) {
            let at = now.addingTimeInterval(loosenDelay)
            defaults?.set(encode(next), forKey: pendingKey)
            defaults?.set(at, forKey: pendingAtKey)
            return .waiting(until: at)
        }
        defaults?.set(encode(next), forKey: key)
        return .applied
    }

    /// Apply a waiting change whose time has come. True when it did.
    @discardableResult
    public static func applyDue(now: Date = LockStore.trustedNow()) -> Bool {
        guard let waiting = pending(), now >= waiting.at else { return false }
        defaults?.set(encode(waiting.schedule), forKey: key)
        clearPending()
        return true
    }

    /// Withdrawing a weaker change is always allowed.
    public static func cancelPending() { clearPending() }

    private static func clearPending() {
        defaults?.removeObject(forKey: pendingKey)
        defaults?.removeObject(forKey: pendingAtKey)
    }

    private static func encode(_ s: LockSchedule) -> Data? { try? JSONEncoder().encode(s) }
    private static func decode(_ d: Data?) -> LockSchedule? {
        d.flatMap { try? JSONDecoder().decode(LockSchedule.self, from: $0) }
    }
}
