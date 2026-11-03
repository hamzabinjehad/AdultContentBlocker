import Foundation
import FamilyControls
import ManagedSettings

struct AppUsageConfiguration: Codable {
    enum Mode: String, Codable { case always, dailyBudget, quietHours }
    enum Health: String {
        case unchecked = "apps.health.unchecked"
        case unavailable = "apps.health.unavailable"
        case notConfigured = "apps.health.notConfigured"
        case invalid = "apps.health.invalid"
        case permissionMissing = "apps.health.permissionMissing"
        case alwaysConfigured = "apps.health.alwaysConfigured"
        case budgetRegistered = "apps.health.budgetRegistered"
        case budgetStopped = "apps.health.budgetStopped"
        case quietHoursRegistered = "apps.health.quietHoursRegistered"
        case quietHoursStopped = "apps.health.quietHoursStopped"

        var needsAttention: Bool {
            switch self {
            case .unavailable, .invalid, .permissionMissing, .budgetStopped,
                 .quietHoursStopped: return true
            default: return false
            }
        }
    }
    static func health(storageAvailable: Bool, hasSavedData: Bool, valid: Bool,
                       authorized: Bool, mode: Mode?, registered: Bool) -> Health {
        guard storageAvailable else { return .unavailable }
        guard hasSavedData else { return .notConfigured }
        guard valid, let mode else { return .invalid }
        guard authorized else { return .permissionMissing }
        switch mode {
        case .always: return .alwaysConfigured
        case .dailyBudget: return registered ? .budgetRegistered : .budgetStopped
        case .quietHours: return registered ? .quietHoursRegistered : .quietHoursStopped
        }
    }
    var selection: FamilyActivitySelection
    var mode: Mode
    var minutes: Int
    /// Minutes after midnight for a quiet-hours window. They are kept on every
    /// rule so a mode change does not silently lose the person's chosen times.
    var windowStart: Int
    var windowEnd: Int

    init(selection: FamilyActivitySelection, mode: Mode, minutes: Int,
         windowStart: Int = AppUsageConfiguration.defaultWindowStart,
         windowEnd: Int = AppUsageConfiguration.defaultWindowEnd) {
        self.selection = selection
        self.mode = mode
        self.minutes = minutes
        self.windowStart = windowStart
        self.windowEnd = windowEnd
    }

    private enum CodingKeys: String, CodingKey {
        case selection, mode, minutes, windowStart, windowEnd
    }

    /// Rules saved by 1.2.0 have no window fields; they decode with the default
    /// late-night window, which is only used if the person selects quiet hours.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        selection = try container.decode(FamilyActivitySelection.self, forKey: .selection)
        mode = try container.decode(Mode.self, forKey: .mode)
        minutes = try container.decode(Int.self, forKey: .minutes)
        windowStart = try container.decodeIfPresent(Int.self, forKey: .windowStart)
            ?? Self.defaultWindowStart
        windowEnd = try container.decodeIfPresent(Int.self, forKey: .windowEnd)
            ?? Self.defaultWindowEnd
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(selection, forKey: .selection)
        try container.encode(mode, forKey: .mode)
        try container.encode(minutes, forKey: .minutes)
        try container.encode(windowStart, forKey: .windowStart)
        try container.encode(windowEnd, forKey: .windowEnd)
    }

    var isValid: Bool {
        guard !selection.applicationTokens.isEmpty
            || !selection.categoryTokens.isEmpty
            || !selection.webDomainTokens.isEmpty else { return false }
        switch mode {
        case .always: return true
        case .dailyBudget: return (15...240).contains(minutes)
        case .quietHours:
            return Self.isValidWindow(start: windowStart, end: windowEnd)
        }
    }

    // MARK: - Quiet hours

    struct WindowLeg: Equatable {
        let activity: String
        /// Minutes after midnight, inclusive.
        let start: Int
        /// Minutes after midnight, exclusive.
        let end: Int
    }

    /// The schedules to register. A same-day window is one activity; a window
    /// crossing midnight is two same-day activities. The gate that ends the
    /// window only clears on the morning leg (or the single leg).
    struct WindowPlan: Equatable {
        let legs: [WindowLeg]
        var activityNames: [String] { legs.map(\.activity) }
    }

    var windowCrossesMidnight: Bool { windowEnd < windowStart }

    /// A duration in minutes; nil for an unreadable or zero-length window.
    static func windowDuration(start: Int, end: Int) -> Int? {
        guard (0..<1440).contains(start), (0..<1440).contains(end), start != end else { return nil }
        return end > start ? end - start : 1440 - start + end
    }

    /// Apple rejects DeviceActivity schedules shorter than 15 minutes. A window
    /// crossing midnight is split at midnight, so each half must also meet that
    /// minimum. The split uses 23:59:59 as the evening end, which is why a start
    /// later than 23:44 cannot be represented without a sub-15-minute leg.
    static func isValidWindow(start: Int, end: Int) -> Bool {
        guard let duration = windowDuration(start: start, end: end),
              duration >= minimumWindowMinutes else { return false }
        if end < start {
            guard start <= 23 * 60 + 44, end >= minimumWindowMinutes else { return false }
        }
        return true
    }

    /// The schedules for a valid window; nil when the window is not valid.
    static func makeWindowPlan(start: Int, end: Int) -> WindowPlan? {
        guard isValidWindow(start: start, end: end) else { return nil }
        if start < end {
            return WindowPlan(legs: [WindowLeg(activity: windowActivity, start: start, end: end)])
        }
        return WindowPlan(legs: [
            WindowLeg(activity: windowEveningActivity, start: start, end: 23 * 60 + 59),
            WindowLeg(activity: windowMorningActivity, start: 0, end: end),
        ])
    }

    var windowPlan: WindowPlan? {
        Self.makeWindowPlan(start: windowStart, end: windowEnd)
    }

    static func minuteOfDay(_ date: Date, calendar: Calendar = .current) -> Int {
        let parts = calendar.dateComponents([.hour, .minute], from: date)
        return (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
    }

    static func windowActive(start: Int, end: Int, at date: Date,
                             calendar: Calendar = .current) -> Bool {
        guard isValidWindow(start: start, end: end) else { return false }
        let minute = minuteOfDay(date, calendar: calendar)
        return start < end ? (minute >= start && minute < end)
                           : (minute >= start || minute < end)
    }

    /// True if either clock is inside the window. Hisn clears a quiet-hours
    /// shield only when both the device clock and (once read) Apple's time are
    /// outside it, so moving the device clock forward cannot end it early.
    static func windowActive(start: Int, end: Int, deviceNow: Date, trustedNow: Date?,
                             calendar: Calendar = .current) -> Bool {
        if windowActive(start: start, end: end, at: deviceNow, calendar: calendar) { return true }
        guard let trustedNow else { return false }
        return windowActive(start: start, end: end, at: trustedNow, calendar: calendar)
    }

    func windowActive(deviceNow: Date, trustedNow: Date?, calendar: Calendar = .current) -> Bool {
        Self.windowActive(start: windowStart, end: windowEnd, deviceNow: deviceNow,
                          trustedNow: trustedNow, calendar: calendar)
    }

    static let group = "group.app.hisn.mobile"
    static let key = "appUsageConfiguration"
    /// The reached-day marker and its mirror, each a `ReachedDay` record.
    static let reachedKey = "appUsageReachedDay.v2"
    static let reachedMirrorKey = "appUsageReachedDay.v2.mirror"
    /// Markers written before budget days were Gregorian: a day string in whatever calendar
    /// the person read ("2569-10-26" in the Buddhist one, "5787-2-15" in the Hebrew one),
    /// which cannot be read as a Gregorian date. Read only to migrate, then removed.
    static let legacyReachedKeys = ["appUsageReachedDay", "appUsageReachedDay.mirror"]
    /// The monitor kept a reached day's shield at a new interval because Apple's
    /// time could not confirm the day was over. The app finishes the clear later.
    static let heldKey = "appUsageHeldDay"
    static let budgetStoreName = "hisn.apps.budget"
    enum BudgetMarkerFailure: Error { case corrupt, writeFailed }
    static let activity = "hisn.selected.daily"
    static let event = "hisn.selected.budget"
    /// A quiet-hours window that does not cross midnight uses one activity.
    static let windowActivity = "hisn.selected.window"
    /// A window crossing midnight is split into two same-day schedules and the
    /// evening end does not clear the shield; the morning end does. This avoids
    /// relying on an overnight interval whose behaviour Apple does not document.
    static let windowEveningActivity = "hisn.selected.window.evening"
    static let windowMorningActivity = "hisn.selected.window.morning"
    static let windowStoreName = "hisn.apps.window"
    static let defaultWindowStart = 22 * 60
    static let defaultWindowEnd = 7 * 60
    /// Apple rejects DeviceActivity schedules shorter than 15 minutes.
    static let minimumWindowMinutes = 15
    /// A marker counts at most this far ahead of the device's day (or of the later of the
    /// device clock and Apple's time). One further ahead was written under a clock moved far
    /// forward and would otherwise keep the shield every day until that date.
    static let maximumDaysAhead = 2
    /// How far ahead the end of a day `maximumDaysAhead` days ahead can be.
    static let maximumMarkerLead: TimeInterval = Double(maximumDaysAhead + 1) * 86400
    /// Budget days are Gregorian dates in the device's current time zone, whatever
    /// calendar the person reads: switching calendars in Settings must not turn a
    /// saved day into a far-future or far-past one.
    static var dayCalendar: Calendar { dayCalendar(in: .current) }
    static func dayCalendar(in timeZone: TimeZone) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        return calendar
    }
    static func day(_ date: Date = Date(), in timeZone: TimeZone = .current) -> String {
        day(date, calendar: dayCalendar(in: timeZone))
    }
    private static func day(_ date: Date, calendar: Calendar) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return "\(c.year ?? 0)-\(c.month ?? 0)-\(c.day ?? 0)"
    }
    private static let utc = TimeZone(secondsFromGMT: 0)!

    /// The day a daily budget was reached, with the moment that day ends fixed when it was
    /// reached: changing the time zone afterwards does not move the boundary.
    struct ReachedDay: Codable, Equatable {
        /// Gregorian "2026-10-26" in the time zone the budget was reached in.
        let day: String
        let end: Date

        /// A real day whose end is where some time zone's midnight can be (UTC-12 to
        /// UTC+14, and an hour for daylight saving).
        var isValid: Bool {
            guard end.timeIntervalSinceReferenceDate.isFinite,
                  let utcEnd = AppUsageConfiguration.dayEnd(day, in: AppUsageConfiguration.utc) else { return false }
            return (-13 * 3600 ... 15 * 3600).contains(utcEnd.timeIntervalSince(end))
        }
    }

    /// When `day` ends in `timeZone`; nil for anything but a real Gregorian day.
    static func dayEnd(_ day: String, in timeZone: TimeZone = .current) -> Date? {
        let calendar = dayCalendar(in: timeZone)
        guard let parts = checkedDay(day),
              let start = calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
        else { return nil }
        return calendar.date(byAdding: .day, value: 1, to: start)
    }

    static func reachedDay(_ day: String, in timeZone: TimeZone = .current) -> ReachedDay? {
        dayEnd(day, in: timeZone).map { ReachedDay(day: day, end: $0) }
    }

    /// Shared by the foreground app and monitor. Registration/relaunch must
    /// preserve a reached budget, but yesterday's marker must not block today.
    /// A marker from a later day than the device's (its clock was set back) still
    /// counts as reached, up to `maximumDaysAhead` days ahead.
    static func budgetReached(mode: Mode, reachedDay: String?, today: String = day()) -> Bool {
        guard mode == .dailyBudget, let reachedDay,
              let reached = dayEnd(reachedDay, in: utc), let current = dayEnd(today, in: utc),
              let ahead = dayCalendar(in: utc).dateComponents([.day], from: current, to: reached).day
        else { return false }
        return (0...maximumDaysAhead).contains(ahead)
    }

    enum BudgetDay: Equatable {
        /// No budget was reached (or its marker is too far ahead to count): nothing to keep.
        case notReached
        /// The reached day has not ended on the device clock: keep the shield.
        case reached
        /// The reached day ended on the device clock and on Apple's time: clear.
        case over
        /// The device clock says a new day, Apple's time does not confirm it
        /// (offline, or the clock was moved forward): keep the shield.
        case unverified
    }

    /// The day boundary uses the same rule as the commitment end: a reached day is
    /// over only when both the device clock and Apple's time have passed its end. That
    /// end is the one saved with the marker, in the time zone it was reached in.
    static func budgetDay(reached: ReachedDay?, deviceNow: Date, trustedNow: Date?) -> BudgetDay {
        guard let reached else { return .notReached }
        guard reached.isValid else { return .unverified }
        let latest = max(deviceNow, trustedNow ?? deviceNow)
        if reached.end.timeIntervalSince(latest) > maximumMarkerLead { return .notReached }
        switch TrustedTime.verdict(for: reached.end, deviceNow: deviceNow, trustedNow: trustedNow,
                                   skew: TrustedTime.readingSkew) {
        case .notYet: return .reached
        case .unverified, .clockAhead: return .unverified
        case .passed: return .over
        }
    }

    static func heldDay(_ defaults: UserDefaults) -> String? { defaults.object(forKey: heldKey) as? String }
    static func markHeld(_ defaults: UserDefaults, day: String) { defaults.set(day, forKey: heldKey) }
    static func clearHeld(_ defaults: UserDefaults) { defaults.removeObject(forKey: heldKey) }

    enum HeldDayAction: Equatable { case none, clearShield, forget }
    /// What the app does with a day the monitor held. It clears only what the monitor
    /// would have cleared with Apple's time; unreadable rules or markers keep the shield.
    static func heldDayAction(_ defaults: UserDefaults, deviceNow: Date, trustedNow: Date?) -> HeldDayAction {
        guard defaults.object(forKey: heldKey) != nil else { return .none }
        let config: AppUsageConfiguration?
        let reached: ReachedDay?
        do {
            config = try load(defaults)
            reached = try reachedRecord(defaults)
        } catch { return .none }
        return heldDayAction(held: true, mode: config?.mode, reached: reached,
                             deviceNow: deviceNow, trustedNow: trustedNow)
    }

    static func heldDayAction(held: Bool, mode: Mode?, reached: ReachedDay?,
                              deviceNow: Date, trustedNow: Date?) -> HeldDayAction {
        guard held else { return .none }
        // Always-block mode or removed rules: the app already cleared the budget store.
        guard mode == .dailyBudget else { return .forget }
        switch budgetDay(reached: reached, deviceNow: deviceNow, trustedNow: trustedNow) {
        case .notReached, .over: return .clearShield
        case .reached: return .forget
        case .unverified: return .none
        }
    }

    /// The monitor's bookkeeping when a daily interval starts, after `decision`: true keeps
    /// (or puts back) the budget shield, false clears it. A day confirmed over is settled,
    /// so no later midnight (after a reboot, offline) can shield that day again.
    static func intervalStarted(_ defaults: UserDefaults, reached: ReachedDay?, decision: BudgetDay) -> Bool {
        switch decision {
        case .reached:
            clearHeld(defaults)
            return true
        case .notReached:
            clearHeld(defaults)
            return false
        case .over:
            clearHeld(defaults)
            if let reached { settle(defaults, reached: reached) }
            return false
        case .unverified:
            // A new day on the device clock alone (moved forward, or no way to read
            // Apple's time there): keep the shield. The app clears it once it can confirm.
            if let reached { markHeld(defaults, day: reached.day) }
            return true
        }
    }

    /// A reached day confirmed over on both clocks is finished: its markers go, so nothing
    /// shields that day again. A newer day written meanwhile is kept.
    @discardableResult
    static func settle(_ defaults: UserDefaults, reached: ReachedDay) -> Bool {
        guard let current = try? reachedRecord(defaults), current == reached else { return false }
        for key in [reachedKey, reachedMirrorKey] + legacyReachedKeys { defaults.removeObject(forKey: key) }
        return true
    }

    /// Settles the saved reached day if it is over on both clocks (the app, once it has
    /// cleared a day the monitor held).
    static func settleIfOver(_ defaults: UserDefaults, deviceNow: Date, trustedNow: Date?) {
        guard let reached = try? reachedRecord(defaults),
              budgetDay(reached: reached, deviceNow: deviceNow, trustedNow: trustedNow) == .over else { return }
        settle(defaults, reached: reached)
    }

    /// Read the raw objects so damaged marker data is not mistaken for a new
    /// unrestricted day. Either valid local copy can survive accidental loss;
    /// this is redundancy, not authenticated or trusted-clock persistence.
    static func reachedRecord(_ defaults: UserDefaults, now: Date = Date(),
                              personalCalendar: Calendar = .current) throws -> ReachedDay? {
        let objects = [defaults.object(forKey: reachedKey), defaults.object(forKey: reachedMirrorKey)]
        if objects.allSatisfy({ $0 == nil }) {
            return migrateLegacy(defaults, now: now, personalCalendar: personalCalendar)
        }
        let records = objects.compactMap { object -> ReachedDay? in
            guard let data = object as? Data,
                  let record = try? JSONDecoder().decode(ReachedDay.self, from: data), record.isValid else { return nil }
            return record
        }
        guard let latest = records.max(by: { $0.end < $1.end }) else { throw BudgetMarkerFailure.corrupt }
        return latest
    }

    /// The reached day as the app compares it ("2026-10-26").
    static func reachedDay(_ defaults: UserDefaults) throws -> String? {
        try reachedRecord(defaults)?.day
    }

    /// A marker from before budget days were Gregorian names a day in the person's own
    /// calendar, so it cannot be read as a date. Only one naming today (in that calendar or
    /// the Gregorian one) still means something: it becomes today's marker. Any other is
    /// expired: never a far-future day, and never damage that keeps a shield for good.
    private static func migrateLegacy(_ defaults: UserDefaults, now: Date, personalCalendar: Calendar) -> ReachedDay? {
        let legacy = legacyReachedKeys.compactMap { defaults.object(forKey: $0) as? String }
        guard legacyReachedKeys.contains(where: { defaults.object(forKey: $0) != nil }) else { return nil }
        var personal = personalCalendar
        personal.timeZone = .current
        let today = day(now)
        let names = [today, day(now, calendar: personal)]
        let migrated = legacy.contains { names.contains($0) } ? reachedDay(today) : nil
        if let migrated, let data = try? JSONEncoder().encode(migrated) {
            for key in [reachedMirrorKey, reachedKey] { defaults.set(data, forKey: key) }
        }
        for key in legacyReachedKeys { defaults.removeObject(forKey: key) }
        return migrated
    }

    static func budgetReached(mode: Mode, defaults: UserDefaults, today: String = day()) throws -> Bool {
        guard mode == .dailyBudget else { return false }
        return budgetReached(mode: mode, reachedDay: try reachedDay(defaults), today: today)
    }
    /// Saves `today` with the moment it ends in `timeZone` (the current one when reached).
    static func markBudgetReached(_ defaults: UserDefaults, today: String = day(),
                                  timeZone: TimeZone = .current) throws {
        guard let record = reachedDay(today, in: timeZone) else { throw BudgetMarkerFailure.corrupt }
        let data = try JSONEncoder().encode(record)
        for key in [reachedMirrorKey, reachedKey] {
            defaults.set(data, forKey: key)
            guard defaults.object(forKey: key) as? Data == data else { throw BudgetMarkerFailure.writeFailed }
        }
        for key in legacyReachedKeys { defaults.removeObject(forKey: key) }
    }
    static func clearBudgetMarker(_ defaults: UserDefaults) throws {
        for key in [reachedKey, reachedMirrorKey] + legacyReachedKeys {
            defaults.removeObject(forKey: key)
            guard defaults.object(forKey: key) == nil else { throw BudgetMarkerFailure.writeFailed }
        }
    }
    private static func checkedDay(_ raw: String) -> [Int]? {
        let fields = raw.split(separator: "-", omittingEmptySubsequences: false)
        guard fields.count == 3,
              fields.allSatisfy({ !$0.isEmpty && $0.allSatisfy({ $0.isASCII && $0.isNumber }) }),
              let year = Int(fields[0]), let month = Int(fields[1]), let date = Int(fields[2]),
              (1...9999).contains(year), (1...12).contains(month), (1...31).contains(date),
              raw == "\(year)-\(month)-\(date)" else { return nil }
        // A real Gregorian day (no 30 February), whatever calendar the person reads.
        let calendar = dayCalendar(in: utc)
        let parts = DateComponents(year: year, month: month, day: date)
        guard let instant = calendar.date(from: parts),
              calendar.dateComponents([.year, .month, .day], from: instant) == parts else { return nil }
        return [year, month, date]
    }
    static func ruleStore(_ defaults: UserDefaults) -> MirroredRuleStore {
        MirroredRuleStore(key: key, storage: DefaultsRuleStorage(defaults: defaults)) { data in
            (try? JSONDecoder().decode(Self.self, from: data))?.isValid == true
        }
    }
    static func load(_ defaults: UserDefaults) throws -> AppUsageConfiguration? {
        guard let data = try ruleStore(defaults).load()?.payload else { return nil }
        return try JSONDecoder().decode(Self.self, from: data)
    }
    static func read(_ defaults: UserDefaults) -> AppUsageConfiguration? {
        // Monitor callbacks leave existing shields untouched on unreadable rules.
        try? load(defaults)
    }
    /// While committed the saved rule is frozen, not merely prevented from
    /// weakening. A no-op save is still permitted. Equality is explicit because
    /// `FamilyActivitySelection` is not declared Equatable in every SDK.
    func permits(_ next: AppUsageConfiguration, committed: Bool) -> Bool {
        !committed || sameRule(as: next)
    }
    func sameRule(as other: AppUsageConfiguration) -> Bool {
        mode == other.mode && minutes == other.minutes
            && windowStart == other.windowStart && windowEnd == other.windowEnd
            && selection.applicationTokens == other.selection.applicationTokens
            && selection.categoryTokens == other.selection.categoryTokens
            && selection.webDomainTokens == other.selection.webDomainTokens
    }
    func shield(_ store: ManagedSettingsStore) {
        store.shield.applications = selection.applicationTokens.isEmpty ? nil : selection.applicationTokens
        store.shield.applicationCategories = selection.categoryTokens.isEmpty ? nil : .specific(selection.categoryTokens)
        store.shield.webDomains = selection.webDomainTokens.isEmpty ? nil : selection.webDomainTokens
        store.shield.webDomainCategories = selection.categoryTokens.isEmpty ? nil : .specific(selection.categoryTokens)
    }
}
