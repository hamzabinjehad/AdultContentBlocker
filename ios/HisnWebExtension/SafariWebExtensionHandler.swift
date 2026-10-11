import Foundation

/// Text and verdicts stay in Safari's JavaScript extension sandbox. This target
/// does not accept native messages or send browsing activity to the app.
final class SafariWebExtensionHandler: NSObject, NSExtensionRequestHandling {
    func beginRequest(with context: NSExtensionContext) {
        context.completeRequest(returningItems: [], completionHandler: nil)
    }
}
