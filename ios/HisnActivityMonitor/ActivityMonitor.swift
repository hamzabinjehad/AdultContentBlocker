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
        let reached: AppUsageConfiguration.ReachedDay?
        do { reached = try AppUsageConfiguration.reachedRecord(defaults) } catch { return }
        let decision = budgetDay(reached, defaults: defaults)
        // A day confirmed over is settled there, so a later midnight never shields it again.
        if AppUsageConfiguration.intervalStarted(defaults, reached: reached, decision: decision) {
            config.shield(budget)
        } else {
            budget.clearAllSettings()
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
    private func budgetDay(_ reached: AppUsageConfiguration.ReachedDay?,
                           defaults: UserDefaults) -> AppUsageConfiguration.BudgetDay {
        let anchors = TrustedTime.AnchorStore(storage: DefaultsRuleStorage(defaults: defaults))
        let boot = TrustedTime.bootSession
        let uptime = TrustedTime.uptime
        let saved = anchors.load()
        let decision = AppUsageConfiguration.budgetDay(reached: reached, deviceNow: Date(),
                                                       trustedNow: saved?.lowerBound(uptime: uptime, boot: boot))
        // Restate the reading at this moment, so a check after a later reboot never starts
        // from an earlier time than this one did.
        if let rebased = saved?.rebased(uptime: uptime, boot: boot) {
            try? anchors.advance(to: rebased, uptime: uptime, boot: boot)
        }
        guard decision == .unverified,
              let server = AppleServerTime(timeout: serverWait).currentTime(waitingAtMost: serverWait)
        else { return decision }
        let now = TrustedTime.uptime
        // In a boot that already has a reading, a new one may only lower it (see reconcile).
        let reading = TrustedTime.reconcile(reading: server, uptime: now, boot: boot, with: anchors.load())
        try? anchors.save(reading)
        return AppUsageConfiguration.budgetDay(reached: reached, deviceNow: Date(),
                                               trustedNow: reading.lowerBound(uptime: now, boot: boot))
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
        let trusted = TrustedTime.AnchorStore(storage: storage).load()?
            .lowerBound(uptime: TrustedTime.uptime, boot: TrustedTime.bootSession)
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
