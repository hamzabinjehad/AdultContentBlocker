import Foundation

/// Local redundancy, not an OS lock or an authenticated policy authority.
protocol RuleStorage {
    func object(forKey key: String) -> Any?
    func write(_ data: Data, forKey key: String) throws
    func remove(_ key: String) throws
}

struct DefaultsRuleStorage: RuleStorage {
    let defaults: UserDefaults
    func object(forKey key: String) -> Any? { defaults.object(forKey: key) }
    func write(_ data: Data, forKey key: String) throws {
        defaults.set(data, forKey: key)
        guard defaults.data(forKey: key) == data else { throw MirroredRuleStore.Failure.writeFailed }
    }
    func remove(_ key: String) throws {
        defaults.removeObject(forKey: key)
        guard defaults.object(forKey: key) == nil else { throw MirroredRuleStore.Failure.writeFailed }
    }
}

/// Single-writer store. Readers (including extensions) never repair copies:
/// a stale reader must not overwrite a newer writer. Writes are not an OS
/// transaction; a partially written higher revision is retained as evidence.
final class MirroredRuleStore {
    enum Failure: Error, Equatable {
        case corrupt, unsupportedVersion, conflictingRevision, invalidPayload, revisionOverflow, writeFailed
    }
    struct Record: Codable, Equatable {
        var schemaVersion: Int = 1
        let revision: Int
        let payload: Data?
    }
    let key: String
    private let storage: RuleStorage
    private let valid: (Data) -> Bool
    init(key: String, storage: RuleStorage, valid: @escaping (Data) -> Bool) {
        self.key = key; self.storage = storage; self.valid = valid
    }
    var primaryKey: String { key + ".record" }
    var mirrorKey: String { key + ".mirror" }

    func load() throws -> Record? {
        let objects = [storage.object(forKey: primaryKey), storage.object(forKey: mirrorKey)]
        if objects.allSatisfy({ $0 == nil }) {
            guard let legacy = storage.object(forKey: key) else { return nil }
            guard let data = legacy as? Data, valid(data) else { throw Failure.corrupt }
            return Record(revision: 0, payload: data)
        }
        var records: [Record] = []
        for object in objects.compactMap({ $0 }) {
            guard let data = object as? Data,
                  let record = try? JSONDecoder().decode(Record.self, from: data) else { throw Failure.corrupt }
            guard record.schemaVersion == 1 else { throw Failure.unsupportedVersion }
            guard record.revision > 0 else { throw Failure.corrupt }
            if let payload = record.payload, !valid(payload) { throw Failure.invalidPayload }
            records.append(record)
        }
        if records.count == 2, records[0].revision == records[1].revision, records[0] != records[1] {
            throw Failure.conflictingRevision
        }
        return records.max { $0.revision < $1.revision }
    }

    @discardableResult
    func save(_ payload: Data?) throws -> Record {
        if let payload, !valid(payload) { throw Failure.invalidPayload }
        let revision = try load()?.revision ?? 0
        guard revision < Int.max else { throw Failure.revisionOverflow }
        let record = Record(revision: revision + 1, payload: payload)
        let data = try JSONEncoder().encode(record)
        try storage.write(data, forKey: mirrorKey)
        try storage.write(data, forKey: primaryKey)
        // Keep old clients compatible. A tombstone in the versioned records
        // takes precedence over any stale legacy payload after interruption.
        if let payload { try storage.write(payload, forKey: key) }
        else { try storage.remove(key) }
        return record
    }
}
