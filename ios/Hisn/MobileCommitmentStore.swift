import Foundation
import Security

protocol CommitmentPersistence {
    func load() throws -> CommitmentPolicy.Session?
    func save(_ session: CommitmentPolicy.Session) throws
}

/// Ordinary app-owned persistence. Not an unremovable device-management profile.
/// Mirrors help with accidental data loss; they do not defeat device erasure.
final class MobileCommitmentStore: CommitmentPersistence {
    enum Failure: Error { case inaccessible, corrupt, invalid, writeFailed }
    private let defaults: UserDefaults
    private let service: String
    init(defaults: UserDefaults = .standard, service: String = "app.hisn.mobile.commitment") {
        self.defaults = defaults; self.service = service
    }
    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
         kSecAttrAccount as String: "session"]
    }
    func load() throws -> CommitmentPolicy.Session? {
        var search = query
        search[kSecReturnData as String] = true
        search[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        let status = SecItemCopyMatching(search as CFDictionary, &item)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw Failure.inaccessible }
        // Read the raw object: data(forKey:) turns a wrong-type value into nil,
        // which previously made corrupt storage look like an unlocked fresh app.
        if status == errSecSuccess, !(item is Data) { throw Failure.corrupt }
        let latest: CommitmentPolicy.Session?
        do { latest = try CommitmentPolicy.restoredSession(from: [defaults.object(forKey: service), item]) }
        catch { throw Failure.corrupt }
        guard let latest else { return nil }
        // Do not label persistence healthy unless the surviving copy is mirrored.
        try save(latest)
        return latest
    }
    func save(_ session: CommitmentPolicy.Session) throws {
        guard session.isValid() else { throw Failure.invalid }
        let data = try JSONEncoder().encode(session)
        let status = SecItemUpdate(query as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if status == errSecItemNotFound {
            var insert = query
            insert[kSecValueData as String] = data
            insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            guard SecItemAdd(insert as CFDictionary, nil) == errSecSuccess else { throw Failure.writeFailed }
        } else if status != errSecSuccess { throw Failure.writeFailed }
        defaults.set(data, forKey: service)
        guard defaults.data(forKey: service) == data else { throw Failure.writeFailed }
    }
}

/// Tests never touch the installed person's commitment or Keychain.
final class MemoryCommitmentStore: CommitmentPersistence {
    var session: CommitmentPolicy.Session?
    func load() throws -> CommitmentPolicy.Session? { session }
    func save(_ session: CommitmentPolicy.Session) throws { self.session = session }
}

/// What the app shares with its monitor extension through the app group: Apple's time
/// reading, the commitment watch for layer alerts, the alerts choice and the budget day
/// the monitor held. Configuration only; nothing about browsing. A copy of the
/// commitment's dates, not the commitment itself, which stays in the Keychain.
final class MonitorSharedState {
    static let alertsWantedKey = "layerAlerts.wanted"
    let storage: RuleStorage
    private let defaults: UserDefaults?

    /// nil keeps everything in memory (tests, or an app group iOS did not provide).
    init(defaults: UserDefaults?) {
        self.defaults = defaults
        storage = defaults.map { DefaultsRuleStorage(defaults: $0) } ?? EphemeralRuleStorage()
    }

    /// The real app group; tests get memory only.
    static func appGroup() -> MonitorSharedState {
        MonitorSharedState(defaults: NSClassFromString("XCTestCase") == nil
            ? UserDefaults(suiteName: AppUsageConfiguration.group) : nil)
    }

    var anchors: TrustedTime.AnchorStore { TrustedTime.AnchorStore(storage: storage) }

    private var memoryAlertsWanted = false
    var alertsWanted: Bool {
        get { defaults?.bool(forKey: Self.alertsWantedKey) ?? memoryAlertsWanted }
        set { memoryAlertsWanted = newValue; defaults?.set(newValue, forKey: Self.alertsWantedKey) }
    }

    func saveWatch(_ watch: LayerAlertPolicy.Watch?) { try? LayerAlertPolicy.saveWatch(watch, to: storage) }

    var hasHeldBudgetDay: Bool { defaults.map { AppUsageConfiguration.heldDay($0) != nil } ?? false }

    var dailyBudgetConfigured: Bool {
        guard let defaults else { return false }
        return (try? AppUsageConfiguration.load(defaults))?.mode == .dailyBudget
    }

    func heldDayAction(deviceNow: Date, trustedNow: Date?) -> AppUsageConfiguration.HeldDayAction {
        defaults.map { AppUsageConfiguration.heldDayAction($0, deviceNow: deviceNow, trustedNow: trustedNow) } ?? .none
    }

    func clearHeldBudgetDay() { defaults.map(AppUsageConfiguration.clearHeld) }

    /// The language picked in Hisn's Settings ("system", "ar" or "en").
    static var languageChoice: String? { UserDefaults.standard.string(forKey: "hisn.mobile.language") }
}
