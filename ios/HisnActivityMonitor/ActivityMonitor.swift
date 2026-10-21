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
        // Unreadable evidence must not clear a previously applied OS shield.
        guard let reached = try? AppUsageConfiguration.budgetReached(mode: config.mode, defaults: defaults) else { return }
        if reached {
            config.shield(budget)
        } else {
            budget.clearAllSettings()
        }
    }
    override func eventDidReachThreshold(_ event: DeviceActivityEvent.Name, activity: DeviceActivityName) {
        guard event.rawValue == AppUsageConfiguration.event, activity.rawValue == AppUsageConfiguration.activity,
              let defaults = UserDefaults(suiteName: AppUsageConfiguration.group),
              let config = AppUsageConfiguration.read(defaults), config.mode == .dailyBudget else { return }
        // The threshold callback still applies its shield if persistence fails.
        // Refresh/registration will not clear it based on unreadable markers.
        try? AppUsageConfiguration.markBudgetReached(defaults)
        config.shield(budget)
    }
}
