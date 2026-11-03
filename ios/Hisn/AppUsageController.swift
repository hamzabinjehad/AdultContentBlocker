import Foundation
import FamilyControls
import DeviceActivity
import ManagedSettings
import Combine

@MainActor final class AppUsageController: ObservableObject {
    @Published private(set) var configuration: AppUsageConfiguration?
    @Published private(set) var registered = false
    @Published private(set) var errorKey: String?
    @Published private(set) var health: AppUsageConfiguration.Health = .unchecked
    private let always = ManagedSettingsStore(named: .init("hisn.apps.always"))
    private let budget = ManagedSettingsStore(named: .init("hisn.apps.budget"))
    private let window = ManagedSettingsStore(named: .init(AppUsageConfiguration.windowStoreName))
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
        case .always?, nil:
            registered = false
        }
        health = AppUsageConfiguration.health(storageAvailable: true,
            hasSavedData: configuration != nil,
            valid: configuration != nil, authorized: authorized,
            mode: configuration?.mode, registered: registered)
        // Restore the saved rule after an authorized relaunch. Never restart
        // budget monitoring here, which could reset older OS counters. A quiet-
        // hours shield is reconciled against both clocks, so moving the device
        // clock forward cannot end it early; outside the window it is cleared.
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
        do { try AppUsageConfiguration.clearBudgetMarker(defaults) }
        catch { errorKey = "apps.failed"; health = .unavailable; return }
        errorKey = nil
        refresh()
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
