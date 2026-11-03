import Foundation
import ManagedSettings
import ManagedSettingsUI
import UIKit

/// The system-rendered shield for every app, category, site or web domain Hisn
/// shields. It explains which saved rule is holding the shield and speaks the
/// language the person chose in Hisn. It cannot unlock anything: shields are
/// cleared only by Hisn or by iOS when the person revokes access.
final class ShieldConfigurationExtension: ShieldConfigurationDataSource {
    private func makeConfiguration() -> ShieldConfiguration {
        let defaults = UserDefaults(suiteName: AppUsageConfiguration.group)
        let copy = ShieldTextPolicy.shieldCopy(defaults: defaults)
        return ShieldConfiguration(
            backgroundBlurStyle: .systemMaterial,
            backgroundColor: .systemBackground,
            icon: UIImage(systemName: "shield.lefthalf.filled"),
            title: ShieldConfiguration.Label(text: copy.title, color: .label),
            subtitle: ShieldConfiguration.Label(text: copy.subtitle, color: .secondaryLabel),
            primaryButtonLabel: ShieldConfiguration.Label(text: copy.primaryButton, color: .white),
            primaryButtonBackgroundColor: .systemBlue,
            secondaryButtonLabel: nil
        )
    }

    override func configuration(shielding application: Application) -> ShieldConfiguration {
        makeConfiguration()
    }

    override func configuration(shielding application: Application,
                                in category: ActivityCategory) -> ShieldConfiguration {
        makeConfiguration()
    }

    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration {
        makeConfiguration()
    }

    override func configuration(shielding webDomain: WebDomain,
                                in category: ActivityCategory) -> ShieldConfiguration {
        makeConfiguration()
    }
}
