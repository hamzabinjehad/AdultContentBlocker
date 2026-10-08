import Foundation
import CoreFoundation

/// Runtime evidence, deliberately separate from the conservative setup checklist.
/// Reads standard Chromium profiles only; never writes preferences or reads history.
enum BrowserProfileEvidence {
    enum State: Equatable, Sendable {
        case noExplicitLoss, explicitLoss, unconfirmed
    }
    struct Profile: Equatable, Sendable {
        let folder: String
        let state: State
    }
    struct Snapshot: Equatable, Sendable {
        let enumerationVerified: Bool
        let profiles: [Profile]
        static let unavailable = Snapshot(enumerationVerified: false, profiles: [])
    }

    /// A force-close reread needs current explicit loss in a folder whose
    /// repeated confirmation is still fresh. A new, different failure does
    /// not inherit another folder's confirmation.
    static func lossPersists(in snapshot: Snapshot, confirmedFolders: [String]) -> Bool {
        guard snapshot.enumerationVerified else { return false }
        let confirmed = Set(confirmedFolders)
        return snapshot.profiles.contains { $0.state == .explicitLoss && confirmed.contains($0.folder) }
    }

    /// Two separated, consistent readings are required before profile evidence
    /// overrides a browser heartbeat. Unknown data is never removal evidence.
    struct Confirmation: Sendable {
        static let minimumSpacing: TimeInterval = 10
        static let maximumAge: TimeInterval = 45
        private struct Sample: Sendable { var count: Int; var at: Date }
        private var samples: [String: Sample] = [:]

        mutating func observe(_ snapshot: Snapshot, at now: Date) {
            guard snapshot.enumerationVerified else { samples.removeAll(); return }
            let off = Set(snapshot.profiles.filter { $0.state == .explicitLoss }.map(\.folder))
            samples = samples.filter { off.contains($0.key) }
            for folder in off {
                if let old = samples[folder] {
                    let age = now.timeIntervalSince(old.at)
                    if age >= 0 && age < Self.minimumSpacing { continue }
                    if age >= Self.minimumSpacing && age <= Self.maximumAge {
                        samples[folder] = Sample(count: 2, at: now)
                        continue
                    }
                }
                samples[folder] = Sample(count: 1, at: now)
            }
        }

        func confirmed(at now: Date) -> [String] {
            samples.compactMap { folder, sample in
                let age = now.timeIntervalSince(sample.at)
                return sample.count >= 2 && age >= 0 && age <= Self.maximumAge ? folder : nil
            }.sorted()
        }
    }

    static func inspect(root: URL, ids: Set<String>, maximumBytes: Int = 20 * 1024 * 1024) -> Snapshot {
        guard !ids.isEmpty, maximumBytes > 0,
              let names = try? FileManager.default.contentsOfDirectory(atPath: root.path)
        else { return .unavailable }
        let folders = names.filter { $0 == "Default" || $0.hasPrefix("Profile ") }.sorted()
        guard !folders.isEmpty, folders.count <= 256 else { return .unavailable }
        var remainingBytes = min(maximumBytes, 20 * 1024 * 1024)
        return Snapshot(enumerationVerified: true, profiles: folders.map { folder in
            Profile(folder: folder, state: inspectProfile(root.appendingPathComponent(folder), ids: ids,
                                                         remainingBytes: &remainingBytes))
        })
    }

    private static func inspectProfile(_ directory: URL, ids: Set<String>, remainingBytes: inout Int) -> State {
        let fm = FileManager.default
        guard let files = try? fm.contentsOfDirectory(atPath: directory.path) else { return .unconfirmed }
        var records: [String: [State]] = [:]
        var catalogueSeen = false
        for name in ["Preferences", "Secure Preferences"] {
            let url = directory.appendingPathComponent(name)
            guard files.contains(name) else { continue }
            // Bound IO and allocation; oversized/newer/unreadable formats are
            // unconfirmed, never reasons to close a browser.
            guard let data = read(url, remainingBytes: &remainingBytes),
                  let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            else { return .unconfirmed }
            guard let rawExtensions = json["extensions"] else { continue }
            guard let extensions = rawExtensions as? [String: Any] else { return .unconfirmed }
            guard let rawSettings = extensions["settings"] else { continue }
            guard let settings = rawSettings as? [String: Any] else { return .unconfirmed }
            catalogueSeen = true
            for id in ids {
                if let entry = settings[id] {
                    records[id, default: []].append(entryState(entry))
                }
            }
        }
        guard catalogueSeen else { return .unconfirmed }
        var possibleUnknownCopy = false
        for states in records.values {
            // Conflicting readable sources are not reliable closure evidence.
            if states.allSatisfy({ $0 == .noExplicitLoss }) { return .noExplicitLoss }
            if !states.allSatisfy({ $0 == .explicitLoss }) { possibleUnknownCopy = true }
        }
        if possibleUnknownCopy { return .unconfirmed }
        // Either every registered accepted copy is explicitly off, or the
        // readable catalogue has no accepted copy at all.
        return .explicitLoss
    }

    private static func read(_ url: URL, remainingBytes: inout Int) -> Data? {
        guard remainingBytes > 0,
              let values = try? url.resourceValues(forKeys: [.isRegularFileKey, .fileSizeKey]),
              values.isRegularFile == true, let size = values.fileSize, size <= remainingBytes,
              let handle = try? FileHandle(forReadingFrom: url) else { return nil }
        defer { try? handle.close() }
        // Bound the actual read too: the browser may replace/grow the file
        // after the size check. Malformed JSON still consumes this IO budget.
        guard let data = try? handle.read(upToCount: remainingBytes + 1) else {
            remainingBytes = 0
            return nil
        }
        guard data.count <= remainingBytes else { remainingBytes = 0; return nil }
        remainingBytes -= data.count
        return data
    }

    static func entryState(_ raw: Any) -> State {
        guard let entry = raw as? [String: Any], !entry.isEmpty else { return .unconfirmed }
        var state: Int?
        if let rawState = entry["state"] {
            guard let number = integer(rawState), [0, 1].contains(number) else { return .unconfirmed }
            state = number
        }
        var disabled: Bool?
        if let rawReasons = entry["disable_reasons"] {
            if let list = rawReasons as? [Any] {
                let numbers = list.compactMap(integer)
                guard numbers.count == list.count, numbers.allSatisfy({ $0 > 0 }) else {
                    return .unconfirmed
                }
                disabled = !numbers.isEmpty
            } else {
                guard let mask = integer(rawReasons) else { return .unconfirmed }
                disabled = mask > 0
            }
        }
        if state == 0 || disabled == true { return .explicitLoss }
        // Chromium removed the legacy state key. Recognized empty disable
        // reasons are not a missing extension; absence of both stays unknown.
        if state == 1 || disabled == false { return .noExplicitLoss }
        return .unconfirmed
    }

    private static func integer(_ raw: Any) -> Int? {
        guard let number = raw as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID(),
              !["f", "d"].contains(String(cString: number.objCType)),
              number.doubleValue.isFinite, number.intValue >= 0,
              number.doubleValue == Double(number.intValue) else { return nil }
        return number.intValue
    }
}
