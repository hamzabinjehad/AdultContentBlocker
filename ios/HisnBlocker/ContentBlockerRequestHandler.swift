import CryptoKit
import Foundation

/// Safari loads declarative rules independently of the containing app. No
/// visited URLs or browsing history are exposed to this extension.
final class ContentBlockerRequestHandler: NSObject, NSExtensionRequestHandling {
    func beginRequest(with context: NSExtensionContext) {
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

    private struct Metadata: Decodable { let count: Int; let sha256: String }
    private enum RuleError: Error { case missing, invalid }
}
