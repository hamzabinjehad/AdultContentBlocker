import CryptoKit
import Foundation

/// Builds one Safari content-blocker shard from the active downloaded
/// generation. The six blocker extensions otherwise carry the list compiled at
/// build time, so a new domain waited for an app update on the browser side
/// even though the DNS proxy had already received it.
///
/// Nothing here trusts bytes by itself: the App Group metadata pins the domain
/// file's size, count and SHA-256, and the domain file was written only after
/// `ListGeneration` verified the signed manifest. A missing, damaged or
/// mismatched generation returns nil and the handler keeps the signed bundle.
enum SafariGenerationRules {
    static let parts = 6
    static let ruleLimit = 40_000
    static let group = "group.app.hisn.mobile"
    static let folder = "list-generations"
    static let domainsName = "dns-domains.txt"
    static let metadataName = "dns-metadata.json"
    static let shardReadyName = "safari-rules.ready"

    enum Failure: Error, Equatable {
        case missing
        case metadata
        case domains
        case part
    }

    struct Metadata: Decodable {
        let schema: Int
        let version: Int
        let count: Int
        let domainsSHA256: String
        let domainsBytes: Int

        enum CodingKeys: String, CodingKey {
            case schema, version, count
            case domainsSHA256 = "domains_sha256"
            case domainsBytes = "domains_bytes"
        }
    }

    /// The prebuilt shard for `part` (1-based), or nil to use the bundle. The
    /// app writes all six after installing a generation; the extension reads
    /// one file instead of parsing and building 40,000 rules in its own memory.
    static func activeShard(part: Int, root: URL? = defaultRoot()) -> URL? {
        guard (1...parts).contains(part), let root else { return nil }
        guard let text = try? String(contentsOf: root.appendingPathComponent("active"),
                                     encoding: .utf8),
              let first = text.split(whereSeparator: \.isNewline).first,
              let version = Int(first), version >= 1 else { return nil }
        let directory = root.appendingPathComponent("gen-\(version)", isDirectory: true)
        // The marker is written only after all six shards are durable. A
        // partial write therefore falls back as a whole instead of mixing
        // generations between extensions.
        guard FileManager.default.fileExists(
            atPath: directory.appendingPathComponent(shardReadyName).path) else { return nil }
        let file = directory.appendingPathComponent(shardName(part))
        guard let attributes = try? FileManager.default.attributesOfItem(atPath: file.path),
              let size = attributes[.size] as? NSNumber, size.intValue > 0 else { return nil }
        return file
    }

    /// Build and atomically publish all six shards into the active generation
    /// directory. The caller has already verified the signed manifest; a
    /// failure here leaves Safari on the bundle rules without touching DNS.
    static func writeShards(domainsData: Data, metadata: Metadata, directory: URL) throws {
        let marker = directory.appendingPathComponent(shardReadyName)
        try? FileManager.default.removeItem(at: marker)
        for part in 1...parts {
            let data = try rules(domainsData: domainsData, metadata: metadata, part: part)
            try data.write(to: directory.appendingPathComponent(shardName(part)), options: .atomic)
        }
        try Data("ready\n".utf8).write(to: marker, options: .atomic)
    }

    static func shardName(_ part: Int) -> String { "safari-rules-\(part).json" }

    /// The JSON Safari loads for `part` (1-based), or nil to use the bundle.
    static func activeRules(part: Int, root: URL? = defaultRoot()) -> Data? {
        guard (1...parts).contains(part), let root else { return nil }
        guard let text = try? String(contentsOf: root.appendingPathComponent("active"),
                                     encoding: .utf8),
              let first = text.split(whereSeparator: \.isNewline).first,
              let version = Int(first), version >= 1 else { return nil }
        let directory = root.appendingPathComponent("gen-\(version)", isDirectory: true)
        guard let metadataData = try? Data(contentsOf: directory.appendingPathComponent(metadataName)),
              let metadata = try? JSONDecoder().decode(Metadata.self, from: metadataData),
              metadata.schema == 1, metadata.count > 0,
              metadata.count <= parts * ruleLimit,
              metadata.domainsBytes > 0 else { return nil }
        guard let domains = try? Data(contentsOf: directory.appendingPathComponent(domainsName)),
              domains.count == metadata.domainsBytes,
              sha256Hex(domains) == metadata.domainsSHA256.lowercased() else { return nil }
        return try? rules(domainsData: domains, metadata: metadata, part: part)
    }

    /// Pure shard builder; tests call it directly with a fixture.
    static func rules(domainsData: Data, metadata: Metadata, part: Int) throws -> Data {
        guard (1...parts).contains(part) else { throw Failure.part }
        let all = String(decoding: domainsData, as: UTF8.self)
            .split(separator: "\n", omittingEmptySubsequences: true)
            .map(String.init)
            .filter { !$0.hasPrefix("#") }
        guard all.count == metadata.count else { throw Failure.domains }
        let start = (part - 1) * ruleLimit
        let end = min(start + ruleLimit, all.count)
        var rules: [[String: Any]] = []
        if start < end {
            rules = all[start..<end].map(domainRule)
        }
        // The last shard owns the four sponsored adult TLDs, as the build-time
        // compiler does, so the shard capacity stays within 40,000 rules.
        if part == parts {
            rules += AdultTldGuard.tlds.sorted().map(tldRule)
        }
        guard rules.count <= ruleLimit else { throw Failure.part }
        return try JSONSerialization.data(withJSONObject: rules)
    }

    static func defaultRoot() -> URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group)?
            .appendingPathComponent(folder, isDirectory: true)
    }

    private static func domainRule(_ domain: String) -> [String: Any] {
        let escaped = NSRegularExpression.escapedPattern(for: domain)
        return ["trigger": ["url-filter": "^https?://([^/?#]*@)?([a-z0-9-]+\\.)*\(escaped)\\.?[:/?#]"],
                "action": ["type": "block"]]
    }

    private static func tldRule(_ tld: String) -> [String: Any] {
        ["trigger": ["url-filter": "^https?://([^/?#]*@)?([a-z0-9-]+\\.)*\(tld)\\.?[:/?#]"],
         "action": ["type": "block"]]
    }

    private static func sha256Hex(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }
}
