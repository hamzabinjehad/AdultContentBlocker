import CryptoKit
import Foundation

/// Safari loads declarative rules independently of the containing app. No
/// visited URLs or browsing history are exposed to this extension.
final class ContentBlockerRequestHandler: NSObject, NSExtensionRequestHandling {
    func beginRequest(with context: NSExtensionContext) {
        // A verified downloaded generation wins over the rules compiled into
        // this extension. A missing or damaged generation falls through to the
        // signed bundle instead of disabling Safari blocking.
        if let dynamic = dynamicRules(), let provider = NSItemProvider(contentsOf: dynamic) {
            let item = NSExtensionItem()
            item.attachments = [provider]
            context.completeRequest(returningItems: [item])
            return
        }
        do {
            guard let rules = Bundle.main.url(forResource: "rules", withExtension: "json"),
                  let metadata = Bundle.main.url(forResource: "rules-metadata", withExtension: "json") else {
                throw RuleError.missing
            }
            let raw = try Data(contentsOf: rules, options: .mappedIfSafe)
            let info = try JSONDecoder().decode(Metadata.self, from: Data(contentsOf: metadata))
            let hash = SHA256.hash(data: raw).map { String(format: "%02x", $0) }.joined()
            guard hash == info.sha256, (1...40_000).contains(info.count),
                  let provider = NSItemProvider(contentsOf: rules) else { throw RuleError.invalid }
            let item = NSExtensionItem()
            item.attachments = [provider]
            context.completeRequest(returningItems: [item])
        } catch {
            // Never replace a broken resource with a successful empty ruleset.
            context.cancelRequest(withError: error)
        }
    }

    /// The active generation's shard for this extension, written to a fresh
    /// temporary file because NSItemProvider reads after this method returns.
    private func dynamicRules() -> URL? {
        let identifier = Bundle.main.bundleIdentifier ?? ""
        guard let suffix = identifier.split(separator: ".").last,
              suffix.hasPrefix("blocker"),
              let part = Int(suffix.dropFirst("blocker".count)),
              let data = SafariGenerationRules.activeRules(part: part) else { return nil }
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("hisn-safari-\(UUID().uuidString).json")
        do {
            try data.write(to: url, options: .atomic)
            return url
        } catch {
            return nil
        }
    }

    private struct Metadata: Decodable { let count: Int; let sha256: String }
    private enum RuleError: Error { case missing, invalid }
}
