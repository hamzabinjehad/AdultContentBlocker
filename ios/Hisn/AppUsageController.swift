import Foundation
import FamilyControls
import DeviceActivity
import ManagedSettings
import Combine

@MainActor final class AppUsageController: ObservableObject {
    @Published private(set) var configuration: AppUsageConfiguration?
    /// The saved browser/VPN guard, or nil when none is saved. Read-only here;
    /// `applyGuard` and `clearGuard` are the only writers.
    @Published private(set) var guardConfiguration: AppUsageConfiguration?
    @Published private(set) var registered = false
    @Published private(set) var errorKey: String?
    /// The guard's own message, so a refused guard never hides behind the main
    /// rule's error row.
    @Published private(set) var guardErrorKey: String?
    @Published private(set) var health: AppUsageConfiguration.Health = .unchecked
    /// A pause rule's opens left today, nil when the saved rule is not a pause
    /// rule. Kept here so every screen shows the same number the shield does.
    @Published private(set) var opensLeft: Int?
    /// True when today's opens could not be read. The shield stays closed; the
    /// app says why instead of showing a made-up count.
    @Published private(set) var pauseBudgetDamaged = false
    /// True while a granted pause session is running and the shield is cleared.
    @Published private(set) var pauseSessionActive = false
    private let always = ManagedSettingsStore(named: .init("hisn.apps.always"))
    private let budget = ManagedSettingsStore(named: .init("hisn.apps.budget"))
    private let window = ManagedSettingsStore(named: .init(AppUsageConfiguration.windowStoreName))
    private let guardStore = ManagedSettingsStore(named: .init(AppUsageConfiguration.guardStoreName))
    private let pauseStore = ManagedSettingsStore(named: .init(AppUsageConfiguration.pauseStoreName))
    private var authorizationObservation: AnyCancellable?
    private let defaults: UserDefaults?
    init(defaults: UserDefaults? = UserDefaults(suiteName: AppUsageConfiguration.group)) {
        self.defaults = defaults
        #if !targetEnvironment(simulator)
        authorizationObservation = AuthorizationCenter.shared.$authorizationStatus
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.refresh() }
        #endif
    }
    var authorized: Bool {
        #if targetEnvironment(simulator)
        return false
        #else
        return AuthorizationCenter.shared.authorizationStatus == .approved
        #endif
    }
    func refresh(trustedNow: Date? = nil) {
        guard let defaults else {
            configuration = nil; registered = false; health = .unavailable
            errorKey = "apps.failed"; return
        }
        let reachedDay: String?
        do {
            configuration = try AppUsageConfiguration.load(defaults)
            reachedDay = configuration?.mode == .dailyBudget ? try AppUsageConfiguration.reachedDay(defaults) : nil
        }
        catch {
            configuration = nil; registered = false; health = .invalid
            errorKey = "apps.failed"; return
        }
        // An unreadable guard record reads as no guard; it is never silently
        // replaced with a fresh one over damage the person has not seen.
        guardConfiguration = AppUsageConfiguration.readGuard(defaults)
        // Querying DeviceActivity without Family Controls authorization logs an
        // XPC error and, in the hosted test host, repeatedly doing that during a
        // render made the test process exit. Unauthorized means nothing can be
        // registered, so do not ask.
        let activities = authorized ? DeviceActivityCenter().activities : []
        switch configuration?.mode {
        case .dailyBudget?:
            registered = authorized
                && activities.contains(.init(AppUsageConfiguration.activity))
        case .quietHours?:
            registered = authorized && (configuration?.windowPlan?.activityNames.allSatisfy {
                activities.contains(.init($0))
            } ?? false)
        case .pause?:
            registered = authorized
                && activities.contains(.init(AppUsageConfiguration.pauseSessionActivity))
        case .always?, nil:
            registered = false
        }
        if let config = configuration, config.mode == .pause {
            let budget = PauseRuleStore(defaults: defaults).budget(opens: config.opens)
            opensLeft = budget.remaining
            pauseBudgetDamaged = budget == .unreadable
        } else {
            opensLeft = nil
            pauseBudgetDamaged = false
        }
        health = AppUsageConfiguration.health(storageAvailable: true,
            hasSavedData: configuration != nil,
            valid: configuration != nil, authorized: authorized,
            mode: configuration?.mode, registered: registered)
        // Restore the saved rules after an authorized relaunch. Never restart
        // budget monitoring here, which could reset older OS counters. A quiet-
        // hours shield is reconciled against both clocks, so moving the device
        // clock forward cannot end it early; outside the window it is cleared.
        // The guard is pure ManagedSettings, so restoring it is one call.
        if authorized { guardConfiguration?.shield(guardStore) }
        if authorized, let config = configuration {
            switch config.mode {
            case .always:
                config.shield(always)
                budget.clearAllSettings()
                window.clearAllSettings()
            case .dailyBudget:
                always.clearAllSettings()
                window.clearAllSettings()
                if AppUsageConfiguration.budgetReached(mode: config.mode, reachedDay: reachedDay) {
                    config.shield(budget)
                }
            case .pause:
                always.clearAllSettings()
                budget.clearAllSettings()
                window.clearAllSettings()
                reconcilePause(config: config, registered: registered)
            case .quietHours:
                always.clearAllSettings()
                budget.clearAllSettings()
                // An explicit reading from the app wins; otherwise use the shared
                // anchor the monitor can also read. A missing reading falls back
                // to the device clock, as the in-app scope copy says.
                if config.windowActive(deviceNow: Date(),
                                       trustedNow: trustedNow ?? sharedTrustedNow) {
                    config.shield(window)
                } else {
                    window.clearAllSettings()
                }
            }
        }
    }

    func apply(_ next: AppUsageConfiguration, rulesLocked: Bool, storageHealthy: Bool,
               trustedNow: Date? = nil) {
        guard !rulesLocked else { errorKey = "apps.locked"; return }
        guard storageHealthy, authorized, let defaults else {
            errorKey = "apps.failed"; return
        }
        guard next.isValid else {
            errorKey = next.mode == .quietHours
                && !AppUsageConfiguration.isValidWindow(start: next.windowStart, end: next.windowEnd)
                ? "apps.windowInvalid" : "apps.failed"
            return
        }
        let store = AppUsageConfiguration.ruleStore(defaults)
        let old: Data?
        let reachedDay: String?
        do {
            old = try store.load()?.payload
            reachedDay = next.mode == .dailyBudget ? try AppUsageConfiguration.reachedDay(defaults) : nil
        } catch { errorKey = "apps.failed"; health = .invalid; return }
        do {
            let encoded = try JSONEncoder().encode(next)
            try store.save(encoded)
            switch next.mode {
            case .dailyBudget:
                let schedule = DeviceActivitySchedule(intervalStart: DateComponents(hour: 0, minute: 0),
                    intervalEnd: DateComponents(hour: 23, minute: 59, second: 59), repeats: true)
                let event: DeviceActivityEvent
                if #available(iOS 17.4, *) {
                    event = DeviceActivityEvent(applications: next.selection.applicationTokens,
                        categories: next.selection.categoryTokens, webDomains: next.selection.webDomainTokens,
                        threshold: DateComponents(minute: next.minutes), includesPastActivity: true)
                } else {
                    event = DeviceActivityEvent(applications: next.selection.applicationTokens,
                        categories: next.selection.categoryTokens, webDomains: next.selection.webDomainTokens,
                        threshold: DateComponents(minute: next.minutes))
                }
                try DeviceActivityCenter().startMonitoring(.init(AppUsageConfiguration.activity), during: schedule,
                    events: [.init(AppUsageConfiguration.event): event])
                stopWindowMonitoring()
                always.clearAllSettings()
                budget.clearAllSettings()
                clearPauseRule()
                if AppUsageConfiguration.budgetReached(mode: next.mode, reachedDay: reachedDay) {
                    next.shield(budget)
                }
            case .quietHours:
                guard let plan = next.windowPlan else {
                    throw WindowRegistration.invalidWindow
                }
                try startWindowMonitoring(plan)
                DeviceActivityCenter().stopMonitoring([.init(AppUsageConfiguration.activity)])
                always.clearAllSettings()
                budget.clearAllSettings()
                clearPauseRule()
                if next.windowActive(deviceNow: Date(),
                                     trustedNow: trustedNow ?? sharedTrustedNow) {
                    next.shield(window)
                } else {
                    window.clearAllSettings()
                }
            case .always:
                next.shield(always)
                DeviceActivityCenter().stopMonitoring([.init(AppUsageConfiguration.activity)])
                stopWindowMonitoring()
                budget.clearAllSettings()
                window.clearAllSettings()
                clearPauseRule()
            case .pause:
                next.shield(pauseStore)
                DeviceActivityCenter().stopMonitoring([.init(AppUsageConfiguration.activity)])
                stopWindowMonitoring()
                always.clearAllSettings()
                budget.clearAllSettings()
                window.clearAllSettings()
                clearPauseRule()
                next.shield(pauseStore)
                pauseSessionActive = false
            }
            errorKey = nil
            refresh(trustedNow: trustedNow)
        } catch {
            // Rollback is a new revision, not a raw overwrite of a stale copy.
            // If recovery itself fails, do not claim healthy enforcement.
            do { try store.save(old); refresh(trustedNow: trustedNow) }
            catch { health = .unavailable }
            errorKey = error is WindowRegistration ? "apps.windowInvalid" : "apps.failed"
        }
    }

    func clear(rulesLocked: Bool, storageHealthy: Bool) {
        guard !rulesLocked, storageHealthy, authorized,
              let defaults else {
            errorKey = "apps.locked"; return
        }
        do { try AppUsageConfiguration.ruleStore(defaults).save(nil) }
        catch { errorKey = "apps.failed"; health = .unavailable; return }
        DeviceActivityCenter().stopMonitoring([.init(AppUsageConfiguration.activity)])
        stopWindowMonitoring()
        always.clearAllSettings(); budget.clearAllSettings(); window.clearAllSettings()
        clearPauseRule()
        pauseStore.clearAllSettings()
        do { try AppUsageConfiguration.clearBudgetMarker(defaults) }
        catch { errorKey = "apps.failed"; health = .unavailable; return }
        errorKey = nil
        refresh()
    }

    // MARK: - Browser and VPN guard

    /// The guard is a second, independent always-block rule. While a commitment
    /// runs it is frozen exactly like the main rule: the caller passes the same
    /// lock, and any change — including a no-op save — is refused.
    func applyGuard(_ next: AppUsageConfiguration, rulesLocked: Bool, storageHealthy: Bool) {
        guard !rulesLocked else { guardErrorKey = "apps.locked"; return }
        guard storageHealthy, let defaults else { guardErrorKey = "apps.failed"; return }
        guard next.isGuardValid else { guardErrorKey = "apps.guard.empty"; return }
        guard authorized else { guardErrorKey = "apps.failed"; return }
        do {
            let encoded = try JSONEncoder().encode(next)
            try AppUsageConfiguration.guardRuleStore(defaults).save(encoded)
            next.shield(guardStore)
            guardErrorKey = nil
            refresh()
        } catch {
            guardErrorKey = "apps.failed"
        }
    }

    func clearGuard(rulesLocked: Bool, storageHealthy: Bool) {
        guard !rulesLocked else { guardErrorKey = "apps.locked"; return }
        guard storageHealthy, let defaults else { guardErrorKey = "apps.failed"; return }
        guard authorized else { guardErrorKey = "apps.failed"; return }
        do {
            // A tombstone, so a stale legacy copy cannot resurrect a cleared guard.
            try AppUsageConfiguration.guardRuleStore(defaults).save(nil)
            guardStore.clearAllSettings()
            guardErrorKey = nil
            refresh()
        } catch {
            guardErrorKey = "apps.failed"
        }
    }

    // MARK: - Pause rules

    /// What a pause grant did, so the pause screen can say it plainly.
    enum PauseGrant: Equatable {
        case granted(minutes: Int)
        case noOpens
        case unreadable
        case failed(String)
    }

    /// Spends one open and buys a session, in the only safe order: the monitor
    /// starts first, the record is written second, and the shield is cleared
    /// last. Any failure before the last step leaves the app shielded.
    @discardableResult
    func grantPauseSession(now: Date = Date()) -> PauseGrant {
        guard let defaults, authorized,
              let config = configuration, config.mode == .pause, config.isValid
        else { return .failed("apps.failed") }
        guard !pauseSessionActive else { return .failed("apps.pauseLive") }
        let store = PauseRuleStore(defaults: defaults)
        switch store.budget(opens: config.opens, now: now) {
        case .exhausted: return .noOpens
        case .unreadable: return .unreadable
        case .opens: break
        }
        do {
            try startPauseSession(config)
        } catch {
            return .failed("apps.pauseFailed")
        }
        guard store.saveSession(.init(startedAt: now, minutes: config.minutes)) else {
            stopPauseSession()
            return .failed("apps.pauseFailed")
        }
        guard let next = PauseRule.consume(store.dayRecord(), opens: config.opens, now: now),
              store.saveDay(next) else {
            stopPauseSession()
            store.clearSession()
            return .failed("apps.pauseFailed")
        }
        pauseStore.clearAllSettings()
        refresh()
        return .granted(minutes: config.minutes)
    }

    /// The handoff the shield left, if it is still fresh. Consuming it removes it,
    /// so a second foreground cannot present the same pause twice.
    func takePauseRequest(now: Date = Date()) -> PauseRule.Request? {
        guard let defaults, let config = configuration, config.mode == .pause else { return nil }
        let store = PauseRuleStore(defaults: defaults)
        guard let request = store.consumeRequest(),
              PauseRule.requestIsFresh(request, now: now) else { return nil }
        return request
    }

    /// One activity covers the day and the session is a usage threshold inside
    /// it: a session that starts at 23:50 is not rejected for being shorter than
    /// Apple's 15-minute schedule minimum.
    private func startPauseSession(_ config: AppUsageConfiguration) throws {
        let schedule = DeviceActivitySchedule(
            intervalStart: DateComponents(hour: 0, minute: 0),
            intervalEnd: DateComponents(hour: 23, minute: 59, second: 59), repeats: true)
        let event: DeviceActivityEvent
        if #available(iOS 17.4, *) {
            event = DeviceActivityEvent(applications: config.selection.applicationTokens,
                categories: config.selection.categoryTokens, webDomains: config.selection.webDomainTokens,
                threshold: DateComponents(minute: config.minutes), includesPastActivity: false)
        } else {
            event = DeviceActivityEvent(applications: config.selection.applicationTokens,
                categories: config.selection.categoryTokens, webDomains: config.selection.webDomainTokens,
                threshold: DateComponents(minute: config.minutes))
        }
        try DeviceActivityCenter().startMonitoring(
            .init(AppUsageConfiguration.pauseSessionActivity), during: schedule,
            events: [.init(AppUsageConfiguration.pauseSessionEvent): event])
    }

    private func stopPauseSession() {
        DeviceActivityCenter().stopMonitoring([.init(AppUsageConfiguration.pauseSessionActivity)])
        if let defaults { PauseRuleStore(defaults: defaults).clearSession() }
        pauseSessionActive = false
    }

    /// Keeps the pause shield and the saved session in agreement. A session is
    /// live only while its activity is registered, it belongs to today and it
    /// has not outlived any possible reading of its minutes. Anything else is
    /// re-shielded, which is the safe direction.
    private func reconcilePause(config: AppUsageConfiguration, registered: Bool) {
        guard let defaults else { return }
        let store = PauseRuleStore(defaults: defaults)
        if let session = store.loadSession(),
           PauseRule.sessionIsToday(session), !PauseRule.sessionExpired(session), registered {
            pauseStore.clearAllSettings()
            pauseSessionActive = true
            return
        }
        if store.loadSession() != nil {
            DeviceActivityCenter().stopMonitoring([.init(AppUsageConfiguration.pauseSessionActivity)])
            store.clearSession()
        }
        config.shield(pauseStore)
        pauseSessionActive = false
    }

    /// Removes a pause rule's session and request without touching its shield:
    /// callers apply or clear the store themselves.
    private func clearPauseRule() {
        if let defaults {
            let store = PauseRuleStore(defaults: defaults)
            store.clearSession()
            store.clearRequest()
        }
        DeviceActivityCenter().stopMonitoring([.init(AppUsageConfiguration.pauseSessionActivity)])
        pauseSessionActive = false
    }

    // MARK: - Quiet-hours scheduling

    private enum WindowRegistration: Error { case invalidWindow }

    /// DeviceActivity rejects intervals shorter than 15 minutes. A window that
    /// crosses midnight is registered as two same-day schedules; the monitor
    /// keeps the shield through the evening end and clears it at the morning end.
    private func startWindowMonitoring(_ plan: AppUsageConfiguration.WindowPlan) throws {
        let center = DeviceActivityCenter()
        var started: [DeviceActivityName] = []
        do {
            for leg in plan.legs {
                let schedule = DeviceActivitySchedule(
                    intervalStart: time(leg.start), intervalEnd: time(leg.end), repeats: true)
                try center.startMonitoring(.init(leg.activity), during: schedule)
                started.append(.init(leg.activity))
            }
        } catch {
            center.stopMonitoring(started)
            throw error
        }
    }

    private func stopWindowMonitoring() {
        DeviceActivityCenter().stopMonitoring([
            .init(AppUsageConfiguration.windowActivity),
            .init(AppUsageConfiguration.windowEveningActivity),
            .init(AppUsageConfiguration.windowMorningActivity),
        ])
    }

    private func time(_ minutes: Int) -> DateComponents {
        DateComponents(hour: minutes / 60, minute: minutes % 60, second: 0)
    }

    /// The app group's saved reading of Apple's time, carried forward with the
    /// monotonic clock. It is a lower bound, so it can delay a clear but cannot
    /// move one earlier. nil until Apple's time has been read.
    private var sharedTrustedNow: Date? {
        guard let defaults else { return nil }
        return TrustedTime.AnchorStore(storage: DefaultsRuleStorage(defaults: defaults)).load()?
            .lowerBound(uptime: TrustedTime.uptime, boot: TrustedTime.bootSession)
    }
}
