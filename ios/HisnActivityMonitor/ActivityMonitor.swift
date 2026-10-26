import DeviceActivity
import FamilyControls
import ManagedSettings
import UserNotifications
import Foundation

final class ActivityMonitor: DeviceActivityMonitor {
    private let budget = ManagedSettingsStore(named: .init(AppUsageConfiguration.budgetStoreName))
    private let selfControl = ManagedSettingsStore(named: .init("hisn.self-control"))
    private let always = ManagedSettingsStore(named: .init("hisn.apps.always"))
    /// Apple's time over HTTPS, only when the saved reading cannot settle a day.
    private let serverWait: TimeInterval = 5

    override func intervalDidStart(for activity: DeviceActivityName) {
        defer { checkLayers() }
        guard activity.rawValue == AppUsageConfiguration.activity,
              let defaults = UserDefaults(suiteName: AppUsageConfiguration.group),
              let config = AppUsageConfiguration.read(defaults), config.mode == .dailyBudget else { return }
        // A registration callback in the same day must not clear an already reached budget.
        // Unreadable evidence must not clear a previously applied OS shield.
        let reachedDay: String?
        do { reachedDay = try AppUsageConfiguration.reachedDay(defaults) } catch { return }
        switch budgetDay(reachedDay, defaults: defaults) {
        case .reached:
            config.shield(budget)
            AppUsageConfiguration.clearHeld(defaults)
        case .notReached, .over:
            budget.clearAllSettings()
            AppUsageConfiguration.clearHeld(defaults)
        case .unverified:
            // A new day on the device clock alone (moved forward, or no way to read
            // Apple's time here): keep the shield. The app clears it once it can confirm.
            config.shield(budget)
            if let reachedDay { AppUsageConfiguration.markHeld(defaults, day: reachedDay) }
        }
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        checkLayers()
    }

    override func intervalWillStartWarning(for activity: DeviceActivityName) {
        checkLayers()
    }

    override func intervalWillEndWarning(for activity: DeviceActivityName) {
        checkLayers()
    }

    override func eventDidReachThreshold(_ event: DeviceActivityEvent.Name, activity: DeviceActivityName) {
        defer { checkLayers() }
        guard event.rawValue == AppUsageConfiguration.event, activity.rawValue == AppUsageConfiguration.activity,
              let defaults = UserDefaults(suiteName: AppUsageConfiguration.group),
              let config = AppUsageConfiguration.read(defaults), config.mode == .dailyBudget else { return }
        // The threshold callback still applies its shield if persistence fails.
        // Refresh/registration will not clear it based on unreadable markers.
        try? AppUsageConfiguration.markBudgetReached(defaults)
        config.shield(budget)
    }

    /// The saved reading of Apple's time first (works offline); Apple's server only if
    /// that cannot confirm the new day. Any failure keeps the shield.
    private func budgetDay(_ reachedDay: String?, defaults: UserDefaults) -> AppUsageConfiguration.BudgetDay {
        let anchors = TrustedTime.AnchorStore(storage: DefaultsRuleStorage(defaults: defaults))
        let saved = anchors.load()?.lowerBound(uptime: TrustedTime.uptime)
        let decision = AppUsageConfiguration.budgetDay(reachedDay: reachedDay, deviceNow: Date(), trustedNow: saved)
        guard decision == .unverified,
              let server = AppleServerTime(timeout: serverWait).currentTime(waitingAtMost: serverWait)
        else { return decision }
        try? anchors.save(TrustedTime.Anchor(server: server, uptime: TrustedTime.uptime))
        return AppUsageConfiguration.budgetDay(reachedDay: reachedDay, deviceNow: Date(), trustedNow: server)
    }

    /// Re-checks Hisn's own Screen Time settings during a commitment and tells the
    /// person, in their language, which layer stopped. Read-only: it restores nothing
    /// and never asks for notification permission (the app asks, with an explanation).
    private func checkLayers() {
        guard let defaults = UserDefaults(suiteName: AppUsageConfiguration.group) else { return }
        let storage = DefaultsRuleStorage(defaults: defaults)
        guard let watch = LayerAlertPolicy.loadWatch(storage), watch.alertsOn else { return }
        let config = AppUsageConfiguration.read(defaults)
        let shield = always.shield
        var webFilterSet = false
        if case .auto = selfControl.webContent.blockedByFilter { webFilterSet = true }
        let observed = LayerAlertPolicy.Observation(
            authorized: AuthorizationCenter.shared.authorizationStatus == .approved,
            webFilterSet: webFilterSet,
            appShieldsExpected: config?.mode == .always,
            appShieldsSet: shield.applications != nil || shield.applicationCategories != nil
                || shield.webDomains != nil || shield.webDomainCategories != nil)
        let trusted = TrustedTime.AnchorStore(storage: storage).load()?.lowerBound(uptime: TrustedTime.uptime)
        let posted = LayerAlertPolicy.posted(LayerAlertPolicy.loadMemory(storage), for: watch)
        let decision = LayerAlertPolicy.decide(watch: watch, deviceNow: Date(), trustedNow: trusted,
                                               observed: observed, alreadyPosted: posted)
        for layer in decision.post {
            guard let text = watch.texts[layer.rawValue] else { continue }
            let content = UNMutableNotificationContent()
            content.title = text.title
            content.body = text.body
            UNUserNotificationCenter.current().add(UNNotificationRequest(
                identifier: "hisn.layer.\(layer.rawValue)", content: content, trigger: nil))
        }
        try? LayerAlertPolicy.saveMemory(.init(startedAt: watch.startedAt, layers: decision.remember), to: storage)
    }
}
