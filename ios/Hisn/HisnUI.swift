import SwiftUI
import UIKit

/// The shared surfaces for Hisn's iPhone and iPad screens.
///
/// The 1.5.0 pass moves the app from one long grouped list to a small set of
/// cards: a status card, one card per decision, and the evidence behind a
/// disclosure or a drill-in. The surfaces are built from system colours so
/// they follow light/dark mode and Increase Contrast without a second palette.
enum HisnSurface {
    static let grouped = Color(uiColor: .systemGroupedBackground)
    static let card = Color(uiColor: .secondarySystemGroupedBackground)
    static let border = Color(uiColor: .separator).opacity(0.45)
    static let fill = Color(uiColor: .tertiarySystemFill)
}

/// One card. `tint` adds a very light wash used by the status card only; it is
/// kept under 12% so primary and secondary text keep their system contrast.
struct HisnCard<Content: View>: View {
    var tint: Color?
    @ViewBuilder let content: Content

    @Environment(\.colorScheme) private var colorScheme

    init(tint: Color? = nil, @ViewBuilder content: () -> Content) {
        self.tint = tint
        self.content = content()
    }

    var body: some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(18)
            .background {
                ZStack {
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .fill(HisnSurface.card)
                    if let tint {
                        RoundedRectangle(cornerRadius: 22, style: .continuous)
                            .fill(
                                LinearGradient(
                                    colors: [tint.opacity(0.12), tint.opacity(0.015)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                    }
                }
            }
            .overlay {
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .strokeBorder(HisnSurface.border, lineWidth: 1)
                    .allowsHitTesting(false)
            }
            .shadow(color: colorScheme == .dark ? .clear : .black.opacity(0.045),
                    radius: 14, x: 0, y: 6)
    }
}

/// A symbol in a soft circle. The circle is decoration; the label beside it
/// carries the meaning, so the icon is hidden from VoiceOver.
struct HisnIconBadge: View {
    let systemName: String
    var tint: Color = .accentColor
    var size: CGFloat = 44

    var body: some View {
        Image(systemName: systemName)
            .font(.system(size: size * 0.42, weight: .semibold))
            .foregroundStyle(tint)
            .frame(width: size, height: size)
            .background(tint.opacity(0.12), in: Circle())
            .accessibilityHidden(true)
    }
}

/// A compact state label. At 12% tint the existing `HisnPalette` status
/// colours stay above 4.5:1 on the card in light and dark mode.
struct HisnStatusPill: View {
    let text: LocalizedStringKey
    var systemImage: String?
    var tint: Color = .secondary

    var body: some View {
        HStack(spacing: 4) {
            if let systemImage {
                Image(systemName: systemImage)
                    .accessibilityHidden(true)
            }
            Text(text)
        }
        .font(.caption.weight(.semibold))
        .foregroundStyle(tint)
        .padding(.horizontal, 9)
        .padding(.vertical, 5)
        .background(tint.opacity(0.12), in: Capsule())
        .accessibilityElement(children: .combine)
    }
}

/// A thin progress bar for setup completion, never for a streak or a score.
struct HisnProgressBar: View {
    let value: Double
    var tint: Color = .accentColor

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(HisnSurface.fill)
                Capsule()
                    .fill(tint)
                    .frame(width: proxy.size.width * min(max(value, 0), 1))
            }
        }
        .frame(height: 8)
        .accessibilityHidden(true)
    }
}

/// A small uppercase heading above a group of cards.
struct HisnSectionLabel: View {
    let title: LocalizedStringKey

    init(_ title: LocalizedStringKey) {
        self.title = title
    }

    var body: some View {
        Text(title)
            .font(.footnote.weight(.semibold))
            .textCase(.uppercase)
            .foregroundStyle(.secondary)
            .accessibilityAddTraits(.isHeader)
    }
}

/// A hairline that reads as structure inside a card rather than as a list
/// separator. `Divider` inherits the system separator colour.
struct HisnDivider: View {
    var body: some View {
        Divider().overlay(HisnSurface.border)
    }
}

/// One title/value row that survives accessibility text sizes: side by side
/// normally, stacked when the text would otherwise be cut off.
struct HisnAdaptiveRow<Content: View>: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    let title: LocalizedStringKey
    let detail: Text?
    let content: Content

    init(title: LocalizedStringKey, detail: Text? = nil, @ViewBuilder content: () -> Content) {
        self.title = title
        self.detail = detail
        self.content = content()
    }

    var body: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 8) {
                    titleBlock
                    content
                }
            } else {
                HStack(alignment: .firstTextBaseline, spacing: 12) {
                    titleBlock
                    Spacer(minLength: 12)
                    content
                }
            }
        }
    }

    private var titleBlock: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.subheadline.weight(.medium))
            if let detail {
                detail
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

/// The readable column shared by all three tabs on iPad.
extension View {
    func hisnReadableColumn(regular: Bool) -> some View {
        frame(maxWidth: regular ? 860 : .infinity)
            .frame(maxWidth: .infinity)
    }
}
