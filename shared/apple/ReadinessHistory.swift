import Foundation

/// Bounded configuration history, not a browsing or tamper log.
final class ReadinessHistory {
    private let store: MirroredRuleStore
    private let allowed: Set<String>
    private var persisted: Set<String> = []
    private(set) var known: Set<String> = []
    private(set) var storageHealthy = true
    init(key: String, storage: RuleStorage, allowed: Set<String>) {
        self.allowed = allowed
        store = MirroredRuleStore(key: key, storage: storage) { data in
            guard let ids = try? JSONDecoder().decode([String].self, from: data) else { return false }
            return ids.count <= allowed.count && Set(ids).count == ids.count && Set(ids).isSubset(of: allowed)
        }
        reload()
    }
    private func reload() {
        do {
            persisted = []
            if let data = try store.load()?.payload {
                persisted = Set(try JSONDecoder().decode([String].self, from: data))
                known.formUnion(persisted)
            }
            storageHealthy = true
        } catch { storageHealthy = false }
    }
    func observe(_ ready: Set<String>) {
        // UI-actor writers merge the latest state so two Mac windows cannot
        // replace each other's observations. Never repair from a stale reader.
        reload()
        let next = known.union(ready.intersection(allowed))
        known = next
        guard next != persisted else { return }
        guard storageHealthy else { return }
        do { try store.save(JSONEncoder().encode(next.sorted())) }
        catch { storageHealthy = false }
    }
    static func deviceLocal(key: String, allowed: Set<String>) -> ReadinessHistory {
        let storage: RuleStorage = NSClassFromString("XCTestCase") == nil
            ? DefaultsRuleStorage(defaults: .standard) : EphemeralRuleStorage()
        return ReadinessHistory(key: key, storage: storage, allowed: allowed)
    }
}

final class EphemeralRuleStorage: RuleStorage {
    private var values: [String: Data] = [:]
    func object(forKey key: String) -> Any? { values[key] }
    func write(_ data: Data, forKey key: String) throws { values[key] = data }
    func remove(_ key: String) throws { values.removeValue(forKey: key) }
}
