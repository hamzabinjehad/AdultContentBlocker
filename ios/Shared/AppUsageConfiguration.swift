import Foundation
import FamilyControls
import ManagedSettings

struct AppUsageConfiguration: Codable {
    enum Mode: String, Codable { case always, dailyBudget }
    enum Health: String {
        case unchecked = "apps.health.unchecked"
        case unavailable = "apps.health.unavailable"
        case notConfigured = "apps.health.notConfigured"
        case invalid = "apps.health.invalid"
        case permissionMissing = "apps.health.permissionMissing"
        case alwaysConfigured = "apps.health.alwaysConfigured"
        case budgetRegistered = "apps.health.budgetRegistered"
        case budgetStopped = "apps.health.budgetStopped"

        var needsAttention: Bool {
            switch self {
            case .unavailable, .invalid, .permissionMissing, .budgetStopped: return true
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
        if mode == .always { return .alwaysConfigured }
        return registered ? .budgetRegistered : .budgetStopped
    }
    var selection: FamilyActivitySelection
    var mode: Mode
    var minutes: Int
    var isValid: Bool {
        (15...240).contains(minutes) && (!selection.applicationTokens.isEmpty
            || !selection.categoryTokens.isEmpty || !selection.webDomainTokens.isEmpty)
    }
    static let group = "group.app.hisn.mobile"
    static let key = "appUsageConfiguration"
    static let reachedKey = "appUsageReachedDay"
    static let reachedMirrorKey = "appUsageReachedDay.mirror"
    enum BudgetMarkerFailure: Error { case corrupt, writeFailed }
    static let activity = "hisn.selected.daily"
    static let event = "hisn.selected.budget"
    static func day(_ date: Date = Date()) -> String {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return "\(c.year ?? 0)-\(c.month ?? 0)-\(c.day ?? 0)"
    }
    /// Shared by the foreground app and monitor. Registration/relaunch must
    /// preserve a reached budget, but yesterday's marker must not block today.
    static func budgetReached(mode: Mode, reachedDay: String?, today: String = day()) -> Bool {
        mode == .dailyBudget && reachedDay == today
    }
    /// Read the raw objects so damaged marker data is not mistaken for a new
    /// unrestricted day. Either valid local copy can survive accidental loss;
    /// this is redundancy, not authenticated or trusted-clock persistence.
    static func reachedDay(_ defaults: UserDefaults) throws -> String? {
        let objects = [defaults.object(forKey: reachedKey), defaults.object(forKey: reachedMirrorKey)]
        if objects.allSatisfy({ $0 == nil }) { return nil }
        let values = objects.compactMap { object -> (String, [Int])? in
            guard let raw = object as? String, let components = checkedDay(raw) else { return nil }
            return (raw, components)
        }
        guard let latest = values.max(by: { $0.1.lexicographicallyPrecedes($1.1) }) else {
            throw BudgetMarkerFailure.corrupt
        }
        return latest.0
    }
    static func budgetReached(mode: Mode, defaults: UserDefaults, today: String = day()) throws -> Bool {
        guard mode == .dailyBudget else { return false }
        return budgetReached(mode: mode, reachedDay: try reachedDay(defaults), today: today)
    }
    static func markBudgetReached(_ defaults: UserDefaults, today: String = day()) throws {
        guard checkedDay(today) != nil else { throw BudgetMarkerFailure.corrupt }
        for key in [reachedMirrorKey, reachedKey] {
            defaults.set(today, forKey: key)
            guard defaults.object(forKey: key) as? String == today else { throw BudgetMarkerFailure.writeFailed }
        }
    }
    static func clearBudgetMarker(_ defaults: UserDefaults) throws {
        for key in [reachedKey, reachedMirrorKey] {
            defaults.removeObject(forKey: key)
            guard defaults.object(forKey: key) == nil else { throw BudgetMarkerFailure.writeFailed }
        }
    }
    private static func checkedDay(_ raw: String) -> [Int]? {
        let fields = raw.split(separator: "-", omittingEmptySubsequences: false)
        guard fields.count == 3,
              fields.allSatisfy({ !$0.isEmpty && $0.allSatisfy({ $0.isASCII && $0.isNumber }) }),
              let year = Int(fields[0]), let month = Int(fields[1]), let date = Int(fields[2]),
              (1...9999).contains(year), (1...13).contains(month), (1...31).contains(date),
              raw == "\(year)-\(month)-\(date)" else { return nil }
        // Use the same calendar that produced day(); do not break a saved
        // shield just because the person uses a non-Gregorian calendar.
        let calendar = Calendar.current
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
    func permits(_ next: AppUsageConfiguration, committed: Bool) -> Bool {
        guard committed else { return true }
        guard Self.preserves(selection.applicationTokens, next.selection.applicationTokens),
              Self.preserves(selection.categoryTokens, next.selection.categoryTokens),
              Self.preserves(selection.webDomainTokens, next.selection.webDomainTokens) else { return false }
        if mode == .always { return next.mode == .always }
        return next.mode == .always || next.minutes <= minutes
    }
    static func preserves<T: Hashable>(_ current: Set<T>, _ proposed: Set<T>) -> Bool {
        current.isSubset(of: proposed)
    }
    func shield(_ store: ManagedSettingsStore) {
        store.shield.applications = selection.applicationTokens.isEmpty ? nil : selection.applicationTokens
        store.shield.applicationCategories = selection.categoryTokens.isEmpty ? nil : .specific(selection.categoryTokens)
        store.shield.webDomains = selection.webDomainTokens.isEmpty ? nil : selection.webDomainTokens
        store.shield.webDomainCategories = selection.categoryTokens.isEmpty ? nil : .specific(selection.categoryTokens)
    }
}
