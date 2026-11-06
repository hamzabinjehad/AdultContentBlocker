import Foundation
import ManagedSettings
import UserNotifications

/// The shield's two buttons.
///
/// A hard rule has one action: close. A pause rule adds "Pause first", which
/// never opens the shielded app — it hands a request to Hisn, where the pause
/// and the session live. On iOS 26.5 and later the shield can open Hisn
/// directly; earlier versions post a notification that opens Hisn when tapped.
/// The request is bounded by its age, so a stale one is ignored.
final class ShieldActionExtension: ShieldActionDelegate {
    private enum Handoff {
        static let identifier = "hisn.pause.handoff"
    }

    /// The shield's completion handler must be called exactly once: the
    /// notification callback and the timeout race each other.
    private final class Once {
        private let lock = NSLock()
        private var finished = false
        func run(_ block: () -> Void) {
            lock.lock()
            let first = !finished
            finished = true
            lock.unlock()
            if first { block() }
        }
    }

    override func handle(action: ShieldAction, for application: ApplicationToken,
                         completionHandler: @escaping (ShieldActionResponse) -> Void) {
        respond(action: action,
                appName: Application(token: application).localizedDisplayName,
                completionHandler: completionHandler)
    }

    override func handle(action: ShieldAction, for category: ActivityCategoryToken,
                         completionHandler: @escaping (ShieldActionResponse) -> Void) {
        respond(action: action, appName: nil, completionHandler: completionHandler)
    }

    override func handle(action: ShieldAction, for webDomain: WebDomainToken,
                         completionHandler: @escaping (ShieldActionResponse) -> Void) {
        respond(action: action,
                appName: WebDomain(token: webDomain).domain,
                completionHandler: completionHandler)
    }

    /// Only the secondary button of a pause rule with opens left does anything
    /// but close. Everything else stays the hard shield it was.
    private func respond(action: ShieldAction, appName: String?,
                         completionHandler: @escaping (ShieldActionResponse) -> Void) {
        guard action == .secondaryButtonPressed,
              let defaults = UserDefaults(suiteName: AppUsageConfiguration.group),
              let config = AppUsageConfiguration.read(defaults),
              config.mode == .pause,
              PauseRuleStore(defaults: defaults).budget(opens: config.opens).canOpen
        else {
            completionHandler(.close)
            return
        }
        PauseRuleStore(defaults: defaults).saveRequest(
            PauseRule.Request(at: Date(), appName: appName))
        if #available(iOS 26.5, *) {
            completionHandler(.openParentalControlsApp)
        } else {
            let once = Once()
            postHandoff(appName: appName, defaults: defaults) {
                once.run { completionHandler(.close) }
            }
        }
    }

    /// The earlier handoff: a notification, because an extension cannot launch
    /// its app. Without notification permission the request is still saved, so
    /// opening Hisn within the request's lifetime finds it waiting.
    private func postHandoff(appName: String?, defaults: UserDefaults,
                             done: @escaping () -> Void) {
        let copy = ShieldTextPolicy.handoffNotification(
            appName: appName,
            language: ShieldTextPolicy.language(defaults: defaults))
        let content = UNMutableNotificationContent()
        content.title = copy.title
        content.body = copy.body
        content.sound = .default
        UNUserNotificationCenter.current().add(UNNotificationRequest(
            identifier: Handoff.identifier, content: content, trigger: nil)) { _ in
            done()
        }
        // A callback that never arrives must not hold the shield open.
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) { done() }
    }
}
