import CryptoKit
import Foundation
import XCTest

final class SafariWebScannerPackageTests: XCTestCase {
    private var scannerURL: URL {
        Bundle.main.bundleURL.appendingPathComponent("PlugIns/HisnText.appex")
    }

    private func json(_ filename: String) throws -> [String: Any] {
        let raw = try Data(contentsOf: scannerURL.appendingPathComponent(filename))
        return try XCTUnwrap(JSONSerialization.jsonObject(with: raw) as? [String: Any])
    }

    func testSafariTextScannerShipsAsAnAdditionalWebExtension() throws {
        let info = try jsonPlist(at: scannerURL.appendingPathComponent("Info.plist"))
        XCTAssertEqual(info["CFBundleIdentifier"] as? String, "app.hisn.mobile.HisnText")
        let configuration = try XCTUnwrap(info["NSExtension"] as? [String: Any])
        XCTAssertEqual(configuration["NSExtensionPointIdentifier"] as? String, "com.apple.Safari.web-extension")
        for part in 1...4 {
            XCTAssertTrue(FileManager.default.fileExists(atPath:
                Bundle.main.bundleURL.appendingPathComponent("PlugIns/Hisn\(part).appex/rules.json").path))
        }
    }

    func testSafariScannerResourcesAreNonemptyAndMatchBuildMetadata() throws {
        let metadata = try json("scanner-metadata.json")
        XCTAssertEqual(metadata["schema"] as? Int, 1)
        XCTAssertGreaterThan(try XCTUnwrap(metadata["version"] as? Int), 0)
        let resources = try XCTUnwrap(metadata["resources"] as? [String: [String: Any]])
        let expected: Set<String> = ["manifest.json", "background.js", "blocked.html", "blocked.js",
                                     "content/scan.js", "content/feed.js", "lib/score.js", "lib/normalize.js",
                                     "seed/terms.json", "icons/icon-32.png", "icons/icon-128.png"]
        XCTAssertEqual(Set(resources.keys), expected)
        for (name, entry) in resources {
            let raw = try Data(contentsOf: scannerURL.appendingPathComponent(name))
            XCTAssertFalse(raw.isEmpty, name)
            XCTAssertEqual(entry["bytes"] as? Int, raw.count, name)
            let digest = SHA256.hash(data: raw).map { String(format: "%02x", $0) }.joined()
            XCTAssertEqual(entry["sha256"] as? String, digest, name)
        }
        let terms = try json("seed/terms.json")
        XCTAssertEqual(terms["version"] as? Int, metadata["version"] as? Int)
        XCTAssertFalse(try XCTUnwrap(terms["terms"] as? [[String: Any]]).isEmpty)
    }

    func testSafariScannerManifestUsesStaticOrderedSharedScriptsAndNoNativeMessaging() throws {
        let manifest = try json("manifest.json")
        XCTAssertEqual(manifest["manifest_version"] as? Int, 3)
        XCTAssertEqual(manifest["name"] as? String, "Hisn Text")
        XCTAssertNil(manifest["key"])
        XCTAssertNil(manifest["declarative_net_request"])
        XCTAssertNil(manifest["permissions"])
        let background = try XCTUnwrap(manifest["background"] as? [String: Any])
        XCTAssertEqual(background["service_worker"] as? String, "background.js")
        XCTAssertEqual(background["type"] as? String, "module")
        let scripts = try XCTUnwrap(manifest["content_scripts"] as? [[String: Any]])
        XCTAssertEqual(scripts.count, 1)
        let script = try XCTUnwrap(scripts.first)
        XCTAssertEqual(script["js"] as? [String], ["content/feed.js", "content/scan.js"])
        XCTAssertEqual(script["matches"] as? [String], ["http://*/*", "https://*/*"])
        XCTAssertEqual(script["run_at"] as? String, "document_start")
        XCTAssertEqual(script["all_frames"] as? Bool, true)
    }

    private func jsonPlist(at url: URL) throws -> [String: Any] {
        try XCTUnwrap(PropertyListSerialization.propertyList(from: Data(contentsOf: url),
                                                            options: [], format: nil) as? [String: Any])
    }
}
