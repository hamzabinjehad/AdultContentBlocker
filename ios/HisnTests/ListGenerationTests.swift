import CryptoKit
import Foundation
import XCTest
@testable import HisnMobile

final class ListGenerationTests: XCTestCase {
    private struct Fixture {
        let manifest: Data
        let signature: String
        let domains: Data
        let terms: Data
        let publicKey: String
    }

    private func fixture(version: Int = 7) throws -> Fixture {
        let key = Curve25519.Signing.PrivateKey()
        let publicKey = key.publicKey.rawRepresentation.map { String(format: "%02x", $0) }.joined()
        let domains = (1...1_100).map { String(format: "fixture%04d.example", $0) }
            .sorted().joined(separator: "\n") + "\n"
        let terms = try JSONSerialization.data(withJSONObject: [
            "schema": 1,
            "version": version,
            "terms": [["t": "fixtureterm", "w": 8, "l": "en"]],
            "negatives": [],
            "host_terms": [["t": "fixturehost", "kind": "token"]],
            "never_keyword": [],
            "keyword_exempt_hosts": [],
        ], options: [.sortedKeys])
        let domainData = Data(domains.utf8)
        let manifest = try JSONSerialization.data(withJSONObject: [
            "schema": 1,
            "version": version,
            "core_domain_count": 1_100,
            "domain_count": 1_100,
            "artifacts": [
                "domains_core.txt": ["sha256": ListGeneration.sha256Hex(domainData),
                                     "bytes": domainData.count],
                "terms.json": ["sha256": ListGeneration.sha256Hex(terms),
                               "bytes": terms.count],
            ],
        ], options: [.sortedKeys])
        return Fixture(manifest: manifest, signature: try key.signature(for: manifest).map {
            String(format: "%02x", $0)
        }.joined(), domains: domainData, terms: terms, publicKey: publicKey)
    }

    private func question(_ name: String) -> DNSQuestion {
        var bytes: [UInt8] = [0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0]
        for label in name.split(separator: ".") {
            bytes.append(UInt8(label.utf8.count))
            bytes.append(contentsOf: label.utf8)
        }
        bytes += [0, 0, 1, 0, 1]
        return try! DNSMessage.parseQuery(Data(bytes))
    }

    func testVerifiesASignedGenerationAndRejectsTampering() throws {
        let base = try fixture()
        let verified = try ListGeneration.verify(manifestData: base.manifest,
                                                 signatureHex: base.signature,
                                                 domains: base.domains, terms: base.terms,
                                                 publicKeyHex: base.publicKey)
        XCTAssertEqual(verified.version, 7)
        XCTAssertEqual(verified.coreDomainCount, 1_100)

        var badSignature = Array(base.signature)
        badSignature[0] = badSignature[0] == "0" ? "1" : "0"
        XCTAssertThrowsError(try ListGeneration.verify(manifestData: base.manifest,
                                                       signatureHex: String(badSignature),
                                                       domains: base.domains, terms: base.terms,
                                                       publicKeyHex: base.publicKey)) {
            XCTAssertEqual($0 as? ListGeneration.Failure, .signature)
        }
        var changedDomains = base.domains
        changedDomains[0] ^= 1
        XCTAssertThrowsError(try ListGeneration.verify(manifestData: base.manifest,
                                                       signatureHex: base.signature,
                                                       domains: changedDomains, terms: base.terms,
                                                       publicKeyHex: base.publicKey)) {
            XCTAssertEqual($0 as? ListGeneration.Failure, .artifact)
        }
    }

    func testStorePublishesTheGenerationTheProxyLoads() throws {
        let base = try fixture()
        let verified = try ListGeneration.verify(manifestData: base.manifest,
                                                 signatureHex: base.signature,
                                                 domains: base.domains, terms: base.terms,
                                                 publicKeyHex: base.publicKey)
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("hisn-list-test-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }

        XCTAssertEqual(try ListGenerationStore.install(verified, root: root), 7)
        guard let active = ListGenerationStore.activeDirectory(root: root) else {
            return XCTFail("no active generation")
        }
        let loaded = try DNSFilterAssets.load(directory: active)
        XCTAssertEqual(loaded.metadata.version, 7)
        XCTAssertEqual(loaded.metadata.count, 1_100)
        XCTAssertTrue(loaded.termsLoaded)
        XCTAssertEqual(loaded.engine.decide(question("fixture0001.example")), .blocked(.list))
        XCTAssertEqual(loaded.engine.decide(question("fixturehost.example")), .blocked(.hostTerm))
        XCTAssertEqual(loaded.engine.decide(question("example.com")), .allowed)

        let older = try self.fixture(version: 6)
        let olderVerified = try ListGeneration.verify(manifestData: older.manifest,
                                                      signatureHex: older.signature,
                                                      domains: older.domains, terms: older.terms,
                                                      publicKeyHex: older.publicKey)
        XCTAssertThrowsError(try ListGenerationStore.install(olderVerified, root: root)) {
            XCTAssertEqual($0 as? ListGenerationStore.Failure, .rollback)
        }
    }
}
