import Foundation

/// A ScreenZen-style pause rule: the shield asks a question instead of only
/// closing the app, and a deliberate pause earns a short, limited session.
///
/// The engine is pure — small records and dates in, records and verdicts out —
/// because four processes share it: the app, the shield configuration
/// extension, the shield action extension and the activity monitor. The tests
/// pin it without Apple's Screen Time service, which is what makes the budget
/// auditable at all. Nothing here claims that a shield appears or that a
/// session ends on a device: that needs the Family Controls entitlement and a
/// signed build.
enum PauseRule {
    // MARK: - Limits the editor offers

    /// Opens per day. Small numbers on purpose: the point is friction, not
    /// accounting.
    static let opensRange = 1...10
    /// Minutes of use one open buys.
    static let sessionRange = 1...30
    static let defaultOpens = 3
    static let defaultSessionMinutes = 5
    /// The breath before the open button becomes available. Low enough not to
    /// feel punitive, high enough to interrupt the reach.
    static let pauseSeconds: TimeInterval = 10
    /// A handoff from the shield older than this is ignored rather than meeting
    /// the person with a pause for an app they opened ten minutes ago.
    static let requestLifetime: TimeInterval = 10 * 60

    // MARK: - Records

    /// What today's pause rule has spent. `used` counts granted sessions, not
    /// taps: a person who closes the pause spends nothing.
    struct DayRecord: Codable, Equatable {
        var day: String
        var used: Int
        var at: Date

        var isValid: Bool {
            at.timeIntervalSince1970.isFinite
                && (0...Self.maximumUsed).contains(used)
                && AppUsageConfiguration.isValidDayName(day)
        }

        /// A ceiling that keeps a corrupted or edited record bounded; the budget
        /// itself never reaches it.
        static let maximumUsed = 9_999
    }

    /// One granted session. The monitor ends it on a usage threshold or at the
    /// end of the day; the app ends it when the activity is no longer registered.
    struct SessionRecord: Codable, Equatable {
        var startedAt: Date
        var minutes: Int

        var isValid: Bool {
            startedAt.timeIntervalSince1970.isFinite
                && sessionRange.contains(minutes)
        }
    }

    /// What the shield hands to the app: tapping the shield's pause button
    /// cannot open Hisn directly on every iOS version, so the extension writes
    /// this and the app consumes it when it next comes forward.
    struct Request: Codable, Equatable {
        var at: Date
        var appName: String?

        var isValid: Bool {
            at.timeIntervalSince1970.isFinite
                && (appName?.count ?? 0) <= 120
        }
    }

    /// How many opens are left today, and whether the record can be trusted.
    enum Budget: Equatable {
        case opens(remaining: Int)
        case exhausted
        /// Both saved copies are damaged. Hisn does not guess a fresh budget:
        /// the day stays closed rather than handing out opens a timestamp bug
        /// could have created.
        case unreadable

        var remaining: Int {
            switch self {
            case .opens(let remaining): return remaining
            case .exhausted, .unreadable: return 0
            }
        }

        var canOpen: Bool { remaining > 0 }
    }

    // MARK: - Budget

    /// Gregorian day in the device's time zone, the same day the app's other
    /// records use: a person on a Buddhist or Hebrew calendar must not see their
    /// budget reset on a different date.
    static func dayKey(_ now: Date = Date(), timeZone: TimeZone = .current) -> String {
        AppUsageConfiguration.day(now, in: timeZone)
    }

    /// Today's budget. A record from another day is no record at all: the budget
    /// is daily by construction, so a saved yesterday never closes today.
    static func budget(_ record: DayRecord?, opens: Int, now: Date = Date(),
                       timeZone: TimeZone = .current) -> Budget {
        let allowed = min(max(opens, opensRange.lowerBound), opensRange.upperBound)
        guard let record else { return .opens(remaining: allowed) }
        guard record.day == dayKey(now, timeZone: timeZone) else { return .opens(remaining: allowed) }
        let remaining = allowed - record.used
        return remaining > 0 ? .opens(remaining: remaining) : .exhausted
    }

    /// The record after one granted open, or nil when nothing is left. A record
    /// from another day starts over; a same-day record carries forward.
    static func consume(_ record: DayRecord?, opens: Int, now: Date = Date(),
                        timeZone: TimeZone = .current) -> DayRecord? {
        guard budget(record, opens: opens, now: now, timeZone: timeZone).canOpen else { return nil }
        let day = dayKey(now, timeZone: timeZone)
        let used = record?.day == day ? (record?.used ?? 0) + 1 : 1
        return DayRecord(day: day, used: used, at: now)
    }

    /// The opens used today, for copy like "2 of 3 opens used".
    static func usedToday(_ record: DayRecord?, now: Date = Date(),
                          timeZone: TimeZone = .current) -> Int {
        guard let record, record.day == dayKey(now, timeZone: timeZone) else { return 0 }
        return record.used
    }

    // MARK: - Session

    /// Whether the saved session is still inside the day it started, so a
    /// session recorded before midnight never unshields the next morning.
    static func sessionIsToday(_ session: SessionRecord, now: Date = Date(),
                               timeZone: TimeZone = .current) -> Bool {
        session.isValid && dayKey(session.startedAt, timeZone: timeZone) == dayKey(now, timeZone: timeZone)
    }

    /// A granted session older than this is not live whatever the activity
    /// registry says: it is a stale record, and the shield goes back on.
    static func sessionExpired(_ session: SessionRecord, now: Date = Date()) -> Bool {
        guard session.isValid else { return true }
        // A session can only buy minutes of use, never a whole day per open.
        return now.timeIntervalSince(session.startedAt) > TimeInterval(session.minutes * 60) * 12
    }

    // MARK: - Handoff

    static func requestIsFresh(_ request: Request?, now: Date = Date()) -> Bool {
        guard let request, request.isValid else { return false }
        let age = now.timeIntervalSince(request.at)
        return age >= 0 && age <= requestLifetime
    }

    // MARK: - Practice streak

    /// The calm streak: consecutive days with a finished pause, counting back
    /// from today, or from yesterday while today is still open. Only the day
    /// names are kept, never a time or a reason.
    static func streak(days: Set<String>, now: Date = Date(), timeZone: TimeZone = .current) -> Int {
        let calendar = AppUsageConfiguration.dayCalendar(in: timeZone)
        let today = dayKey(now, timeZone: timeZone)
        var cursor = now
        if !days.contains(today) {
            guard let yesterday = calendar.date(byAdding: .day, value: -1, to: now) else { return 0 }
            cursor = yesterday
        }
        var count = 0
        while days.contains(dayKey(cursor, timeZone: timeZone)) {
            count += 1
            guard let previous = calendar.date(byAdding: .day, value: -1, to: cursor) else { break }
            cursor = previous
            if count > 3_650 { break }
        }
        return count
    }

    /// Adds today to a bounded history of practice days, newest first.
    static func recordingPractice(days: [String], now: Date = Date(),
                                  timeZone: TimeZone = .current) -> [String] {
        let today = dayKey(now, timeZone: timeZone)
        guard AppUsageConfiguration.isValidDayName(today) else { return days }
        var next = days.filter { $0 != today && AppUsageConfiguration.isValidDayName($0) }
        next.insert(today, at: 0)
        return Array(next.prefix(400))
    }
}

/// Where the pause records live. The app group is shared by the app and every
/// extension; the mirrored day record survives a half-written update the way
/// the app's other records do. Reads never repair copies — a stale reader must
/// not overwrite a newer writer.
struct PauseRuleStore {
    enum DayState: Equatable {
        case absent
        case valid(PauseRule.DayRecord)
        case damaged
    }

    static let dayKey = "hisn.pause.day"
    static let dayMirrorKey = "hisn.pause.day.mirror"
    static let sessionKey = "hisn.pause.session"
    static let requestKey = "hisn.pause.request"
    static let practiceKey = "hisn.pause.practice"
    static let practiceMirrorKey = "hisn.pause.practice.mirror"

    let defaults: UserDefaults?

    init(defaults: UserDefaults?) {
        self.defaults = defaults
    }

    /// The app and its extensions all open the same suite; a missing suite means
    /// no entitlement or a test, and nothing is pretended about it.
    static var appGroup: PauseRuleStore {
        PauseRuleStore(defaults: UserDefaults(suiteName: AppUsageConfiguration.group))
    }

    // MARK: - Day

    func loadDay() -> DayState {
        guard let defaults else { return .absent }
        let objects = [defaults.object(forKey: Self.dayKey), defaults.object(forKey: Self.dayMirrorKey)]
        if objects.allSatisfy({ $0 == nil }) { return .absent }
        let records = objects.compactMap { object -> PauseRule.DayRecord? in
            guard let data = object as? Data,
                  let record = try? JSONDecoder().decode(PauseRule.DayRecord.self, from: data),
                  record.isValid else { return nil }
            return record
        }
        guard !records.isEmpty else { return .damaged }
        // Two valid copies should agree on the day. A disagreement is a
        // conflicting write, not something to resolve in the person's favour.
        if records.count == 2, records[0].day != records[1].day { return .damaged }
        return .valid(records.max { $0.at < $1.at } ?? records[0])
    }

    @discardableResult
    func saveDay(_ record: PauseRule.DayRecord) -> Bool {
        guard let defaults, record.isValid,
              let data = try? JSONEncoder().encode(record) else { return false }
        for key in [Self.dayMirrorKey, Self.dayKey] { defaults.set(data, forKey: key) }
        return defaults.data(forKey: Self.dayKey) == data
    }

    func dayRecord() -> PauseRule.DayRecord? {
        if case .valid(let record) = loadDay() { return record }
        return nil
    }

    /// What the shield should treat as today's budget.
    func budget(opens: Int, now: Date = Date(), timeZone: TimeZone = .current) -> PauseRule.Budget {
        switch loadDay() {
        case .absent: return PauseRule.budget(nil, opens: opens, now: now, timeZone: timeZone)
        case .valid(let record): return PauseRule.budget(record, opens: opens, now: now, timeZone: timeZone)
        case .damaged: return .unreadable
        }
    }

    // MARK: - Session

    func loadSession() -> PauseRule.SessionRecord? {
        guard let defaults, let data = defaults.data(forKey: Self.sessionKey),
              let record = try? JSONDecoder().decode(PauseRule.SessionRecord.self, from: data),
              record.isValid else { return nil }
        return record
    }

    @discardableResult
    func saveSession(_ record: PauseRule.SessionRecord) -> Bool {
        guard let defaults, record.isValid,
              let data = try? JSONEncoder().encode(record) else { return false }
        defaults.set(data, forKey: Self.sessionKey)
        return true
    }

    func clearSession() {
        defaults?.removeObject(forKey: Self.sessionKey)
    }

    // MARK: - Request

    func loadRequest() -> PauseRule.Request? {
        guard let defaults, let data = defaults.data(forKey: Self.requestKey),
              let request = try? JSONDecoder().decode(PauseRule.Request.self, from: data),
              request.isValid else { return nil }
        return request
    }

    @discardableResult
    func saveRequest(_ request: PauseRule.Request) -> Bool {
        guard let defaults, request.isValid,
              let data = try? JSONEncoder().encode(request) else { return false }
        defaults.set(data, forKey: Self.requestKey)
        return true
    }

    /// Reads and removes the handoff in one step so a double foreground cannot
    /// present the same pause twice. A failed removal still returns the request:
    /// its age bounds the repeat.
    func consumeRequest() -> PauseRule.Request? {
        guard let defaults, let request = loadRequest() else { return nil }
        defaults.removeObject(forKey: Self.requestKey)
        return request
    }

    func clearRequest() {
        defaults?.removeObject(forKey: Self.requestKey)
    }

    // MARK: - Practice history

    func practiceDays() -> [String] {
        guard let defaults else { return [] }
        let objects = [defaults.object(forKey: Self.practiceKey),
                       defaults.object(forKey: Self.practiceMirrorKey)]
        for object in objects {
            guard let data = object as? Data,
                  let days = try? JSONDecoder().decode([String].self, from: data) else { continue }
            return days.filter(AppUsageConfiguration.isValidDayName)
        }
        return []
    }

    @discardableResult
    func recordPractice(now: Date = Date(), timeZone: TimeZone = .current) -> [String] {
        let days = PauseRule.recordingPractice(days: practiceDays(), now: now, timeZone: timeZone)
        guard let defaults, let data = try? JSONEncoder().encode(days) else { return days }
        for key in [Self.practiceMirrorKey, Self.practiceKey] { defaults.set(data, forKey: key) }
        return days
    }

    func streak(now: Date = Date(), timeZone: TimeZone = .current) -> Int {
        PauseRule.streak(days: Set(practiceDays()), now: now, timeZone: timeZone)
    }
}
