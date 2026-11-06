import DeviceActivity
import FamilyControls
import ManagedSettings
import UserNotifications
import Foundation

final class ActivityMonitor: DeviceActivityMonitor {
    private let budget = ManagedSettingsStore(named: .init(AppUsageConfiguration.budgetStoreName))
    private let selfControl = ManagedSettingsStore(named: .init("hisn.self-control"))
    private let always = ManagedSettingsStore(named: .init("hisn.apps.always"))
    private let window = ManagedSettingsStore(named: .init(AppUsageConfiguration.windowStoreName))
    private let guardStore = ManagedSettingsStore(named: .init(AppUsageConfiguration.guardStoreName))
    private let pause = ManagedSettingsStore(named: .init(AppUsageConfiguration.pauseStoreName))
    /// Apple's time over HTTPS, only when the saved reading cannot settle a day.
    private let serverWait: TimeInterval = 5

    override func intervalDidStart(for activity: DeviceActivityName) {
        defer { checkLayers() }
        if activity.rawValue == AppUsageConfiguration.pauseSessionActivity {
            // A new day: any session from yesterday is over, and the pause
            // shield goes back on before the monitors restart.
            endPauseSession()
            return
        }
        if isWindowActivity(activity) {
            guard let defaults = UserDefaults(suiteName: AppUsageConfiguration.group),
                  let config = AppUsageConfiguration.read(defaults),
                  config.mode == .quietHours else {
                // A stale window callback after the rule changed must not keep a shield.
                window.clearAllSettings()
                return
            }
            config.shield(window)
            return
        }
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
        // A pause activity covers the whole day; its end is a second chance to
        // put the shield back if a threshold callback was missed.
        if activity.rawValue == AppUsageConfiguration.pauseSessionActivity {
            endPauseSession()
            return
        }
        // A crossing window has two legs. The evening leg ends at 23:59, but the
        // quiet hours are still running, so only the morning leg (or the single
        // same-day leg) can clear the shield.
        if activity.rawValue == AppUsageConfiguration.windowMorningActivity
            || activity.rawValue == AppUsageConfiguration.windowActivity {
            clearWindowOnlyWhenBothClocksAreOutside()
        }
        checkLayers()
    }

    /// The interval end is driven by the device clock. If Apple's saved reading
    /// still puts the device inside the window, the shield stays; the app clears
    /// it after it can confirm both clocks. An unreadable rule leaves the
    /// existing shield untouched rather than guessing.
    private func clearWindowOnlyWhenBothClocksAreOutside() {
        guard let defaults = UserDefaults(suiteName: AppUsageConfiguration.group),
              let config = AppUsageConfiguration.read(defaults) else { return }
        guard config.mode == .quietHours else {
            // The rule was replaced or removed; a late callback must not keep it.
            window.clearAllSettings()
            return
        }
        let trusted = TrustedTime.AnchorStore(storage: DefaultsRuleStorage(defaults: defaults)).load()?
            .lowerBound(uptime: TrustedTime.uptime, boot: TrustedTime.bootSession)
        if !config.windowActive(deviceNow: Date(), trustedNow: trusted) {
            window.clearAllSettings()
        }
    }

    /// Puts the pause rule's shield back and forgets the session. An unreadable
    /// rule still clears the session record and stops the activity: leaving a
    /// stale session live would be worse than a shield the app can rebuild.
    private func endPauseSession() {
        guard let defaults = UserDefaults(suiteName: AppUsageConfiguration.group) else { return }
        let store = PauseRuleStore(defaults: defaults)
        store.clearSession()
        DeviceActivityCenter().stopMonitoring([.init(AppUsageConfiguration.pauseSessionActivity)])
        guard let config = AppUsageConfiguration.read(defaults), config.mode == .pause else {
            pause.clearAllSettings()
            return
        }
        config.shield(pause)
    }

    private func isWindowActivity(_ activity: DeviceActivityName) -> Bool {
        activity.rawValue == AppUsageConfiguration.windowActivity
            || activity.rawValue == AppUsageConfiguration.windowEveningActivity
            || activity.rawValue == AppUsageConfiguration.windowMorningActivity
    }

    override func intervalWillStartWarning(for activity: DeviceActivityName) {
        checkLayers()
    }

    override func intervalWillEndWarning(for activity: DeviceActivityName) {
        checkLayers()
    }

    override func eventDidReachThreshold(_ event: DeviceActivityEvent.Name, activity: DeviceActivityName) {
        defer { checkLayers() }
        if event.rawValue == AppUsageConfiguration.pauseSessionEvent,
           activity.rawValue == AppUsageConfiguration.pauseSessionActivity {
            // The session's minutes of use are spent: re-shield and stop the
            // activity, in that order, so a stopped monitor never leaves the app
            // open.
            endPauseSession()
            return
        }
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
        var webFilterSet = false
        if case .auto = selfControl.webContent.blockedByFilter { webFilterSet = true }
        let trusted = TrustedTime.AnchorStore(storage: storage).load()?
            .lowerBound(uptime: TrustedTime.uptime, boot: TrustedTime.bootSession)
        let quietHoursActive = config?.mode == .quietHours
            && config?.windowActive(deviceNow: Date(), trustedNow: trusted) == true
        // A pause rule has no shield while its session runs; that is the rule
        // working, not a layer that stopped.
        let pauseSession = PauseRuleStore(defaults: defaults).loadSession().map {
            PauseRule.sessionIsToday($0) && !PauseRule.sessionExpired($0)
        } ?? false
        // The guard is a saved layer like the main rule: expected when saved,
        // observed through its own store, so a lost guard is told, not silent.
        let shields = AppUsageConfiguration.appShieldWatch(config: config,
            guardConfig: AppUsageConfiguration.readGuard(defaults),
            quietHoursActive: quietHoursActive, alwaysShieldSet: hasShield(always),
            windowShieldSet: hasShield(window), guardShieldSet: hasShield(guardStore),
            pauseSessionActive: pauseSession, pauseShieldSet: hasShield(pause))
        let observed = LayerAlertPolicy.Observation(
            authorized: AuthorizationCenter.shared.authorizationStatus == .approved,
            webFilterSet: webFilterSet,
            appShieldsExpected: shields.expected,
            appShieldsSet: shields.set)
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

    private func hasShield(_ store: ManagedSettingsStore) -> Bool {
        let shield = store.shield
        return shield.applications != nil || shield.applicationCategories != nil
            || shield.webDomains != nil || shield.webDomainCategories != nil
    }
}
