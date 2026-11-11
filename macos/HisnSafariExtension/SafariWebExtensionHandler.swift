import Foundation

/// Safari keeps page text and verdicts inside the web-extension sandbox. This
/// native handler is intentionally empty for the first Mac target: it accepts
/// no browsing data and sends nothing to the containing app. App-group policy
/// bridging is a separate, reviewable step.
final class SafariWebExtensionHandler: NSObject, NSExtensionRequestHandling {
    func beginRequest(with context: NSExtensionContext) {
        context.completeRequest(returningItems: [], completionHandler: nil)
    }
}
