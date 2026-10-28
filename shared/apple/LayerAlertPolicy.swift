import Foundation

/// Layer-loss alerts during a commitment. The app writes the watch (what the person
/// chose, and the alert text in their language); the monitor extension only reads it,
/// compares it with Hisn's own Screen Time settings, and posts a local notification
/// when a layer it expects is gone. It never turns anything back on and never asks
/// for permission: only the person can restore a layer.
enum LayerAlertPolicy {
    static let watchKey = "layerAlerts.watch"
    static let memoryKey = "layerAlerts.posted"
    /// Registered only while alerts are on during a commitment, so iOS wakes the monitor
    /// at midnight, around noon and just before midnight even without a daily budget.
    static let watchActivity = "hisn.commitment.watch"

    enum Layer: String, Codable, CaseIterable, Comparable {
        case screenTimeAccess, webFilter, appShields
        var titleKey: String { "alert.\(rawValue).title" }
        var bodyKey: String { "alert.\(rawValue).body" }
        static func < (lhs: Layer, rhs: Layer) -> Bool {
            allCases.firstIndex(of: lhs)! < allCases.firstIndex(of: rhs)!
        }
    }

    struct Text: Codable, Equatable {
        let title: String
        let body: String
    }

    /// Written by the app only, while a commitment is saved.
    struct Watch: Codable, Equatable {
        let startedAt: Date
        let deadline: Date
        /// The person's Screen Time filter choice, recorded with the commitment.
        let webFilter: Bool
        /// The person turned alerts on in Hisn (and allowed notifications).
        let alertsOn: Bool
        /// Already in the language the person chose in Hisn, keyed by `Layer.rawValue`.
        let texts: [String: Text]
    }

    /// What the monitor reads back from iOS when it runs.
    struct Observation: Equatable {
        var authorized: Bool
        var webFilterSet: Bool
        /// Always-block rules are saved, so Hisn's always store should shield them.
        var appShieldsExpected: Bool
        var appShieldsSet: Bool
    }

    /// Layers that are gone. Turning Screen Time access off makes iOS remove every
    /// Hisn setting at once, so that is one alert, not three. An authorization status
    /// alone, with every expected setting still in place, is not treated as a loss.
    static func lostLayers(_ watch: Watch, observed: Observation) -> Set<Layer> {
        var missing: Set<Layer> = []
        if watch.webFilter && !observed.webFilterSet { missing.insert(.webFilter) }
        if observed.appShieldsExpected && !observed.appShieldsSet { missing.insert(.appShields) }
        if !missing.isEmpty && !observed.authorized { return [.screenTimeAccess] }
        return missing
    }

    struct Decision: Equatable {
        /// Notifications to post now, in a stable order.
        let post: [Layer]
        /// Layers already announced for this commitment; a layer that comes back and
        /// goes again is announced again.
        let remember: Set<Layer>
    }

    /// Only while the commitment runs (the same both-clocks rule as its end, with no
    /// allowance: alerts stop no earlier than the commitment does), only when the person
    /// turned alerts on, only for layers with text, and once per loss.
    static func decide(watch: Watch?, deviceNow: Date, trustedNow: Date?, observed: Observation,
                       alreadyPosted: Set<Layer>) -> Decision {
        guard let watch, watch.alertsOn,
              TrustedTime.verdict(for: watch.deadline, deviceNow: deviceNow, trustedNow: trustedNow) != .passed
        else { return Decision(post: [], remember: []) }
        let lost = lostLayers(watch, observed: observed)
        let post = lost.subtracting(alreadyPosted).filter { watch.texts[$0.rawValue] != nil }.sorted()
        return Decision(post: post, remember: lost.intersection(alreadyPosted).union(post))
    }

    /// Posted layers belong to one commitment; a new one starts with none.
    struct Memory: Codable, Equatable {
        let startedAt: Date
        let layers: Set<Layer>
    }

    static func posted(_ memory: Memory?, for watch: Watch?) -> Set<Layer> {
        guard let memory, let watch, memory.startedAt == watch.startedAt else { return [] }
        return memory.layers
    }

    // MARK: - Shared storage (app group)

    static func loadWatch(_ storage: RuleStorage) -> Watch? {
        (storage.object(forKey: watchKey) as? Data).flatMap { try? JSONDecoder().decode(Watch.self, from: $0) }
    }

    /// Writes only when something changed; nil removes it.
    static func saveWatch(_ watch: Watch?, to storage: RuleStorage) throws {
        guard loadWatch(storage) != watch || (watch == nil && storage.object(forKey: watchKey) != nil) else { return }
        if let watch { try storage.write(JSONEncoder().encode(watch), forKey: watchKey) }
        else { try storage.remove(watchKey) }
    }

    static func loadMemory(_ storage: RuleStorage) -> Memory? {
        (storage.object(forKey: memoryKey) as? Data).flatMap { try? JSONDecoder().decode(Memory.self, from: $0) }
    }

    static func saveMemory(_ memory: Memory, to storage: RuleStorage) throws {
        guard loadMemory(storage) != memory else { return }
        try storage.write(JSONEncoder().encode(memory), forKey: memoryKey)
    }
}
