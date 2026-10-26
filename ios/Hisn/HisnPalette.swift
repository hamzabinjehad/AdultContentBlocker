import SwiftUI
import UIKit

/// Status colours for text. The system orange and teal are about 2:1 on a white list row;
/// these keep at least 4.5:1 on Hisn's grouped list backgrounds in light and dark mode.
enum HisnPalette {
    static let warningColor = UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 1.0, green: 0.624, blue: 0.039, alpha: 1)    // #FF9F0A
            : UIColor(red: 0.659, green: 0.294, blue: 0.0, alpha: 1)    // #A84B00
    }
    static let goodColor = UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0.251, green: 0.784, blue: 0.878, alpha: 1)  // #40C8E0
            : UIColor(red: 0.0, green: 0.447, blue: 0.498, alpha: 1)    // #00727F
    }
    static let warning = Color(uiColor: warningColor)
    static let good = Color(uiColor: goodColor)

    /// WCAG 2 contrast ratio of two colours as they resolve for `traits`.
    static func contrast(_ first: UIColor, _ second: UIColor, traits: UITraitCollection) -> Double {
        func luminance(_ color: UIColor) -> Double {
            var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
            _ = color.resolvedColor(with: traits).getRed(&red, green: &green, blue: &blue, alpha: &alpha)
            func channel(_ value: CGFloat) -> Double {
                let linear = Double(min(max(value, 0), 1))
                return linear <= 0.04045 ? linear / 12.92 : pow((linear + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
        }
        let lighter = max(luminance(first), luminance(second))
        let darker = min(luminance(first), luminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }
}
