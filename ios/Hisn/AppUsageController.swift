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
    func refresh() {
        guard let defaults else {
            configuration = nil; registered = false; health = .unavailable
            errorKey = "apps.failed"; return
        }
        do { configuration = try AppUsageConfiguration.load(defaults) }
        catch {
            configuration = nil; registered = false; health = .invalid
            errorKey = "apps.failed"; return
        }
        registered = authorized && configuration?.mode == .dailyBudget
            && DeviceActivityCenter().activities.contains(.init(AppUsageConfiguration.activity))
        health = AppUsageConfiguration.health(storageAvailable: true,
            hasSavedData: configuration != nil,
            valid: configuration != nil, authorized: authorized,
            mode: configuration?.mode, registered: registered)
        // Restore the saved always-block rule after authorized relaunch; never
        // restart budget monitoring here, which could reset older OS counters.
        if authorized, let config = configuration {
            if config.mode == .always { config.shield(always) }
            else if AppUsageConfiguration.budgetReached(mode: config.mode,
                reachedDay: defaults.string(forKey: AppUsageConfiguration.reachedKey)) {
                config.shield(budget)
            }
        }
    }
    func apply(_ next: AppUsageConfiguration, commitmentActive: Bool, storageHealthy: Bool) {
        guard storageHealthy, authorized, next.isValid,
              let defaults else {
            errorKey = "apps.failed"; return
        }
        let store = AppUsageConfiguration.ruleStore(defaults)
        let previous: AppUsageConfiguration?
        let old: Data?
        do {
            old = try store.load()?.payload
            previous = try AppUsageConfiguration.load(defaults)
        } catch { errorKey = "apps.failed"; health = .invalid; return }
        if previous?.permits(next, committed: commitmentActive) == false {
            errorKey = "apps.locked"; return
        }
        if commitmentActive, previous?.mode == .dailyBudget, next.mode == .dailyBudget {
            if #available(iOS 17.4, *) { } else { errorKey = "apps.locked"; return }
        }
        do {
            let encoded = try JSONEncoder().encode(next)
            try store.save(encoded)
            if next.mode == .dailyBudget {
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
                always.clearAllSettings()
                if AppUsageConfiguration.budgetReached(mode: next.mode,
                    reachedDay: defaults.string(forKey: AppUsageConfiguration.reachedKey)) {
                    next.shield(budget)
                }
            } else {
                next.shield(always)
                DeviceActivityCenter().stopMonitoring([.init(AppUsageConfiguration.activity)])
                budget.clearAllSettings()
            }
            errorKey = nil
            refresh()
        } catch {
            // Rollback is a new revision, not a raw overwrite of a stale copy.
            // If recovery itself fails, do not claim healthy enforcement.
            do { try store.save(old); refresh() }
            catch { health = .unavailable }
            errorKey = "apps.failed"
        }
    }

    func clear(commitmentActive: Bool, storageHealthy: Bool) {
        guard !commitmentActive, storageHealthy, authorized,
              let defaults else {
            errorKey = "apps.locked"; return
        }
        do { try AppUsageConfiguration.ruleStore(defaults).save(nil) }
        catch { errorKey = "apps.failed"; health = .unavailable; return }
        DeviceActivityCenter().stopMonitoring([.init(AppUsageConfiguration.activity)])
        always.clearAllSettings(); budget.clearAllSettings()
        defaults.removeObject(forKey: AppUsageConfiguration.reachedKey)
        errorKey = nil
        refresh()
    }
}
