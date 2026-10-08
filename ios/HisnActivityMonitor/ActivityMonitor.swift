import DeviceActivity
import ManagedSettings
import Foundation

final class ActivityMonitor: DeviceActivityMonitor {
    private let budget = ManagedSettingsStore(named: .init("hisn.apps.budget"))
    override func intervalDidStart(for activity: DeviceActivityName) {
        guard activity.rawValue == AppUsageConfiguration.activity,
              let defaults = UserDefaults(suiteName: AppUsageConfiguration.group),
              let config = AppUsageConfiguration.read(defaults), config.mode == .dailyBudget else { return }
        // A registration callback in the same day must not clear an already reached budget.
        if AppUsageConfiguration.budgetReached(mode: config.mode,
            reachedDay: defaults.string(forKey: AppUsageConfiguration.reachedKey)) {
            config.shield(budget)
        } else {
            budget.clearAllSettings()
        }
    }
    override func eventDidReachThreshold(_ event: DeviceActivityEvent.Name, activity: DeviceActivityName) {
        guard event.rawValue == AppUsageConfiguration.event, activity.rawValue == AppUsageConfiguration.activity,
              let defaults = UserDefaults(suiteName: AppUsageConfiguration.group),
              let config = AppUsageConfiguration.read(defaults), config.mode == .dailyBudget else { return }
        defaults.set(AppUsageConfiguration.day(), forKey: AppUsageConfiguration.reachedKey)
        config.shield(budget)
    }
}
