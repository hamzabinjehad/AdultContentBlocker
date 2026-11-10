import CryptoKit
import XCTest
@testable import HisnMobile

final class SafariGenerationRulesTests: XCTestCase {
    private func fixture(count: Int = 12) -> (Data, SafariGenerationRules.Metadata) {
        let domains = (1...count).map { "fixture\($0).example" }.joined(separator: "\n") + "\n"
        let data = Data(domains.utf8)
        let metadata = SafariGenerationRules.Metadata(
            schema: 1, version: 7, count: count,
            domainsSHA256: SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined(),
            domainsBytes: data.count)
        return (data, metadata)
    }

    func testSplitsTheFirstShardAndKeepsTheGuardsOnTheLast() throws {
        let (domains, metadata) = fixture()
        let first = try JSONSerialization.jsonObject(with:
            SafariGenerationRules.rules(domainsData: domains, metadata: metadata, part: 1)) as? [[String: Any]]
        XCTAssertEqual(first?.count, 12)
        let last = try JSONSerialization.jsonObject(with:
            SafariGenerationRules.rules(domainsData: domains, metadata: metadata, part: 6)) as? [[String: Any]]
        XCTAssertEqual(last?.count, 4, "the last shard owns the four adult-TLD guards")
        XCTAssertTrue(last?.allSatisfy { rule in
            guard let trigger = rule["trigger"] as? [String: String],
                  let filter = trigger["url-filter"] else { return false }
            return ["adult", "porn", "sex", "xxx"].contains { filter.contains("\($0)\\.?") }
        } ?? false)
        XCTAssertFalse((first?.contains { rule in
            (rule["trigger"] as? [String: String])?["url-filter"]?.contains("adult\\.?") == true
        }) ?? true)
    }

    func testActiveGenerationMustMatchItsMetadataAndPointer() throws {
        let (domains, metadata) = fixture()
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("hisn-safari-test-\(UUID().uuidString)", isDirectory: true)
        let generation = root.appendingPathComponent("gen-7", isDirectory: true)
        try FileManager.default.createDirectory(at: generation, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }
        try Data("7\n".utf8).write(to: root.appendingPathComponent("active"))
        try domains.write(to: generation.appendingPathComponent("dns-domains.txt"))
        let meta: [String: Any] = ["schema": 1, "version": 7, "count": metadata.count,
                                   "domains_sha256": metadata.domainsSHA256,
                                   "domains_bytes": metadata.domainsBytes]
        try JSONSerialization.data(withJSONObject: meta)
            .write(to: generation.appendingPathComponent("dns-metadata.json"))

        XCTAssertNotNil(SafariGenerationRules.activeRules(part: 1, root: root))
        var damaged = domains
        damaged[0] ^= 1
        try damaged.write(to: generation.appendingPathComponent("dns-domains.txt"))
        XCTAssertNil(SafariGenerationRules.activeRules(part: 1, root: root),
                     "a changed domain file must fall back to the bundle")
    }
}
