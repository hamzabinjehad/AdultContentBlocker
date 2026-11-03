import Foundation
import ManagedSettings

/// Hisn's shield has one button and no unlock path. `.close` dismisses the
/// shield view; the ManagedSettings shield remains applied, so the app or site
/// stays inaccessible until the saved rule ends or the person changes it in
/// Hisn (outside a commitment). No action here reads or writes the rule.
final class ShieldActionExtension: ShieldActionDelegate {
    override func handle(action: ShieldAction, for application: ApplicationToken,
                         completionHandler: @escaping (ShieldActionResponse) -> Void) {
        completionHandler(.close)
    }

    override func handle(action: ShieldAction, for category: ActivityCategoryToken,
                         completionHandler: @escaping (ShieldActionResponse) -> Void) {
        completionHandler(.close)
    }

    override func handle(action: ShieldAction, for webDomain: WebDomainToken,
                         completionHandler: @escaping (ShieldActionResponse) -> Void) {
        completionHandler(.close)
    }
}
