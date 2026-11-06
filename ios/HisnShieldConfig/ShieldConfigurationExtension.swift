import Foundation
import ManagedSettings
import ManagedSettingsUI
import UIKit

/// The system-rendered shield. A pause rule gets a calm, dark full-screen
/// question with the app's name, the opens left today, and two actions: close,
/// or open Hisn for the pause. Every other rule keeps a hard shield with one
/// close action.
///
/// The shield cannot unlock anything by itself: a pause session is granted in
/// the app, after the pause, and the app re-arms the shield when that session
/// ends. This extension only draws and reads.
final class ShieldConfigurationExtension: ShieldConfigurationDataSource {
    private enum Ink {
        /// The pause screen's deep navy, the same in light and dark mode: a
        /// shield is a moment, not part of the app's surfaces.
        static let background = UIColor(red: 0.043, green: 0.047, blue: 0.098, alpha: 1)
        static let title = UIColor.white
        static let subtitle = UIColor(white: 0.72, alpha: 1)
        static let action = UIColor(red: 0.36, green: 0.36, blue: 0.96, alpha: 1)
        static let quietAction = UIColor(white: 1, alpha: 0.12)
    }

    /// The three soft bars from the app's mark, drawn rather than bundled so
    /// the extension carries no asset catalogue.
    private static let mark: UIImage = {
        let size = CGSize(width: 120, height: 120)
        return UIGraphicsImageRenderer(size: size).image { context in
            let color = UIColor(red: 0.42, green: 0.42, blue: 0.99, alpha: 1)
            color.setFill()
            let bars: [(CGRect, CGFloat)] = [
                (CGRect(x: 34, y: 26, width: 52, height: 20), 10),
                (CGRect(x: 42, y: 52, width: 36, height: 20), 10),
                (CGRect(x: 28, y: 78, width: 64, height: 22), 11),
            ]
            for (rect, radius) in bars {
                UIBezierPath(roundedRect: rect, cornerRadius: radius).fill()
            }
        }.withRenderingMode(.alwaysOriginal)
    }()

    private func configuration(appName: String?) -> ShieldConfiguration {
        let defaults = UserDefaults(suiteName: AppUsageConfiguration.group)
        let copy = ShieldTextPolicy.shieldCopy(defaults: defaults, appName: appName)
        let pauseRule = ShieldTextPolicy.configuration(in: defaults)?.mode == .pause
        guard pauseRule else {
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
        return ShieldConfiguration(
            backgroundBlurStyle: nil,
            backgroundColor: Ink.background,
            icon: Self.mark,
            title: ShieldConfiguration.Label(text: copy.title, color: Ink.title),
            subtitle: ShieldConfiguration.Label(text: copy.subtitle, color: Ink.subtitle),
            primaryButtonLabel: ShieldConfiguration.Label(text: copy.primaryButton, color: .white),
            primaryButtonBackgroundColor: Ink.quietAction,
            secondaryButtonLabel: copy.secondaryButton.map {
                ShieldConfiguration.Label(text: $0, color: Ink.action)
            }
        )
    }

    override func configuration(shielding application: Application) -> ShieldConfiguration {
        configuration(appName: application.localizedDisplayName)
    }

    override func configuration(shielding application: Application,
                                in category: ActivityCategory) -> ShieldConfiguration {
        configuration(appName: application.localizedDisplayName)
    }

    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration {
        configuration(appName: webDomain.domain)
    }

    override func configuration(shielding webDomain: WebDomain,
                                in category: ActivityCategory) -> ShieldConfiguration {
        configuration(appName: webDomain.domain)
    }
}
