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
        // This exact allowlist includes the shared opt-in visual runtime, not
        // build dependencies, Chrome policies, a native host or remote models.
        let expected: Set<String> = [
            "manifest.json", "background.js", "background.html", "blocked.html", "blocked.js",
            "options.html", "options.js", "options.css",
            "content/scan.js", "content/feed.js", "content/visual.js", "content/visual-effects.js",
            "content/visual-gate.css", "lib/score.js", "lib/normalize.js",
            "seed/terms.json", "icons/icon-32.png", "icons/icon-128.png",
            "visual/capture.js", "visual/broker.js", "visual/decisions.js", "visual/queue.js",
            "visual/verdict-cache.js",
            "visual/assets.json", "visual/engine.bundle.js", "visual/engine.bundle.js.LEGAL.txt",
            "visual/models/blazeface.json", "visual/models/blazeface.bin",
            "visual/models/gender.json", "visual/models/gender.bin",
            "visual/notices/human-models-MIT.txt", "visual/notices/face-classification-MIT.txt",
            "visual/notices/mediapipe-Apache-2.0.txt", "visual/notices/tensorflowjs-Apache-2.0.txt",
            "visual/notices/nsfwjs-LICENSE.txt", "visual/notices/@vladmandic-human-LICENSE.txt",
            "visual/notices/buffer-LICENSE.txt"
        ]
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
        XCTAssertEqual(manifest["permissions"] as? [String], ["storage"])
        XCTAssertEqual(manifest["host_permissions"] as? [String], ["http://*/*", "https://*/*"])
        let background = try XCTUnwrap(manifest["background"] as? [String: Any])
        XCTAssertEqual(Set(background.keys), ["page", "persistent"])
        XCTAssertEqual(background["page"] as? String, "background.html")
        XCTAssertEqual(background["persistent"] as? Bool, false)
        let backgroundPage = try String(contentsOf: scannerURL.appendingPathComponent("background.html"),
                                        encoding: .utf8)
        XCTAssertTrue(backgroundPage.contains("type=\"module\" src=\"background.js\""))
        let options = try XCTUnwrap(manifest["options_ui"] as? [String: Any])
        XCTAssertEqual(options["page"] as? String, "options.html")
        let action = try XCTUnwrap(manifest["action"] as? [String: Any])
        XCTAssertEqual(action["default_popup"] as? String, "options.html")
        let scripts = try XCTUnwrap(manifest["content_scripts"] as? [[String: Any]])
        XCTAssertEqual(scripts.count, 1)
        let script = try XCTUnwrap(scripts.first)
        XCTAssertEqual(script["js"] as? [String],
                       ["content/feed.js", "content/scan.js", "visual/capture.js",
                        "content/visual-effects.js", "content/visual.js"])
        XCTAssertEqual(script["matches"] as? [String], ["http://*/*", "https://*/*"])
        XCTAssertEqual(script["run_at"] as? String, "document_start")
        XCTAssertEqual(script["all_frames"] as? Bool, true)
    }

    func testVisualModelsReferenceOnlyTheirBundledWeightShards() throws {
        let metadata = try json("visual/assets.json")
        XCTAssertEqual(metadata["schema"] as? Int, 1)
        let revision = try XCTUnwrap(metadata["revision"] as? String)
        XCTAssertEqual(revision.count, 40)
        XCTAssertTrue(revision.allSatisfy { "0123456789abcdef".contains($0) })
        for model in ["blazeface", "gender"] {
            let graph = try json("visual/models/\(model).json")
            let groups = try XCTUnwrap(graph["weightsManifest"] as? [[String: Any]])
            let paths = try groups.flatMap { try XCTUnwrap($0["paths"] as? [String]) }
            XCTAssertEqual(paths, ["\(model).bin"], model)
        }
    }

    private func jsonPlist(at url: URL) throws -> [String: Any] {
        try XCTUnwrap(PropertyListSerialization.propertyList(from: Data(contentsOf: url),
                                                            options: [], format: nil) as? [String: Any])
    }
}
