import SwiftUI
import FamilyControls
import ManagedSettings

/// Hisn's 1.7.0 surfaces.
///
/// The app used to speak in system grouped lists and status rows. This pass
/// moves it to one calm canvas — warm paper in light mode, near-black in dark —
/// with large rounded type, soft cards and a single filled action per screen.
/// The colours are chosen for 4.5:1 body text on their own surface, and every
/// state is also carried by words, never by colour alone.
enum HnPalette {
    static let canvas = dynamic(light: 0xF5F2EB, dark: 0x0A0B10)
    static let card = dynamic(light: 0xFFFFFF, dark: 0x15171F)
    static let soft = dynamic(light: 0xECE8DE, dark: 0x1E212B)
    static let ink = dynamic(light: 0x14141B, dark: 0xF4F2EC)
    static let muted = dynamic(light: 0x5C5C68, dark: 0xA2A2AE)
    static let line = dynamic(light: 0x000000, dark: 0xFFFFFF, lightAlpha: 0.07, darkAlpha: 0.09)
    /// The one accent. White on it is 4.9:1; it on white is 4.9:1.
    static let accent = Color(red: 0.36, green: 0.36, blue: 0.96)
    /// The accent for text, not fills: 6.9:1 on white in light mode and 8.1:1 on
    /// the dark card, where the fill accent is only 3.5:1.
    static let accentText = dynamic(light: 0x4343D9, dark: 0xA5A4FF)
    static let accentSoft = dynamic(light: 0xE7E6FF, dark: 0x24243D)
    /// The pause screen and the shield: the same deep navy in both modes.
    static let inkCard = Color(red: 0.043, green: 0.047, blue: 0.098)
    static let onInk = Color.white
    static let onInkMuted = Color(white: 0.74)
    static let calm = dynamic(light: 0x2C7A58, dark: 0x65C79A)
    static let calmSoft = dynamic(light: 0xE0F1E8, dark: 0x17301F)
    static let ember = dynamic(light: 0xA9480A, dark: 0xF2A467)
    static let emberSoft = dynamic(light: 0xFBEADF, dark: 0x33200F)
    /// On the deep card, which is dark in both modes, the light-mode accent and
    /// calm are too dark to read. These are their on-ink counterparts (>= 7:1
    /// against `inkCard`).
    static let onInkAccent = Color(red: 0.74, green: 0.74, blue: 1.0)
    static let onInkCalm = Color(red: 0.47, green: 0.85, blue: 0.67)
    static let onInkEmber = Color(red: 0.99, green: 0.75, blue: 0.48)

    private static func dynamic(light: UInt32, dark: UInt32,
                                lightAlpha: CGFloat = 1, darkAlpha: CGFloat = 1) -> Color {
        Color(uiColor: UIColor { traits in
            let value = traits.userInterfaceStyle == .dark ? dark : light
            let alpha = traits.userInterfaceStyle == .dark ? darkAlpha : lightAlpha
            return UIColor(red: CGFloat((value >> 16) & 0xFF) / 255,
                           green: CGFloat((value >> 8) & 0xFF) / 255,
                           blue: CGFloat(value & 0xFF) / 255,
                           alpha: alpha)
        })
    }
}

extension Font {
    /// Rounded, large and quiet: the words carry the weight, not the chrome.
    static func hnHero() -> Font { .system(.largeTitle, design: .rounded).weight(.bold) }
    static func hnTitle() -> Font { .system(.title2, design: .rounded).weight(.bold) }
    static func hnHeadline() -> Font { .system(.headline, design: .rounded) }
    static func hnNumber() -> Font {
        .system(.largeTitle, design: .rounded).weight(.heavy).monospacedDigit()
    }
    static func hnSmallNumber() -> Font {
        .system(.headline, design: .rounded).weight(.bold).monospacedDigit()
    }
}

// MARK: - Surfaces

/// The canvas every tab sits on.
struct HnCanvas<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        ZStack {
            HnPalette.canvas.ignoresSafeArea()
            content
        }
    }
}

/// One soft card. No shadow in dark mode, where a shadow only muddies the ink.
struct HnCard<Content: View>: View {
    var padding: CGFloat
    var tint: Color?
    let content: Content
    @Environment(\.colorScheme) private var colorScheme

    init(padding: CGFloat = 20, tint: Color? = nil, @ViewBuilder content: () -> Content) {
        self.padding = padding
        self.tint = tint
        self.content = content()
    }

    var body: some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(padding)
            .background {
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(HnPalette.card)
                    .overlay {
                        if let tint {
                            RoundedRectangle(cornerRadius: 28, style: .continuous)
                                .fill(tint.opacity(0.10))
                        }
                    }
            }
            .overlay {
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .strokeBorder(HnPalette.line, lineWidth: 1)
            }
            .shadow(color: colorScheme == .dark ? .clear : .black.opacity(0.05),
                    radius: 18, x: 0, y: 8)
    }
}

/// The deep card used once per screen for the thing that matters most: tonight's
/// state, the pause, the streak. It is the same navy as the shield, so the
/// system screen and the app screen feel like one voice.
struct HnInkCard<Content: View>: View {
    var padding: CGFloat
    let content: Content

    init(padding: CGFloat = 22, @ViewBuilder content: () -> Content) {
        self.padding = padding
        self.content = content()
    }

    var body: some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(padding)
            .background {
                ZStack {
                    RoundedRectangle(cornerRadius: 30, style: .continuous)
                        .fill(HnPalette.inkCard)
                    RadialGradient(colors: [HnPalette.accent.opacity(0.42), .clear],
                                   center: .topTrailing, startRadius: 4, endRadius: 260)
                        .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
                    RadialGradient(colors: [HnPalette.accent.opacity(0.18), .clear],
                                   center: .bottomLeading, startRadius: 2, endRadius: 220)
                        .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
                }
            }
            .overlay {
                RoundedRectangle(cornerRadius: 30, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.08), lineWidth: 1)
            }
    }
}

// MARK: - Type

/// A screen's opening line: one large title, one short subtitle.
struct HnIntro: View {
    let title: LocalizedStringKey
    var subtitle: LocalizedStringKey?
    var onInk = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.hnHero())
                .foregroundStyle(onInk ? HnPalette.onInk : HnPalette.ink)
                .accessibilityAddTraits(.isHeader)
            if let subtitle {
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(onInk ? HnPalette.onInkMuted : HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// A small word in a capsule: "On", "1 of 3 opens".
struct HnBadge: View {
    let text: LocalizedStringKey
    var tint: Color = HnPalette.accentText
    var onInk = false

    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .foregroundStyle(tint)
            .padding(.horizontal, 11)
            .padding(.vertical, 6)
            .background(Capsule().fill(tint.opacity(onInk ? 0.18 : 0.12)))
    }
}

// MARK: - Actions

/// The one filled action on a screen, plus its quieter siblings.
struct HnButton: View {
    enum Kind { case filled, quiet, outline, onInk, text, textOnInk }

    let title: LocalizedStringKey
    var systemImage: String?
    var kind: Kind = .filled
    var fullWidth = true
    var loading = false
    let action: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        Button(action: action) {
            HStack(spacing: 9) {
                if loading {
                    ProgressView().tint(foreground)
                } else if let systemImage {
                    Image(systemName: systemImage).font(.body.weight(.semibold))
                }
                Text(title)
                    .font(.hnHeadline())
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? 3 : 2)
                    .multilineTextAlignment(.center)
            }
            .foregroundStyle(foreground)
            .frame(maxWidth: fullWidth ? .infinity : nil)
            .padding(.horizontal, fullWidth ? 18 : 20)
            .frame(minHeight: 52)
            .background(background)
            .clipShape(Capsule())
            .overlay {
                if kind == .outline {
                    Capsule().strokeBorder(HnPalette.line, lineWidth: 1)
                }
            }
        }
        .buttonStyle(.plain)
        .disabled(loading)
    }

    private var foreground: Color {
        switch kind {
        case .filled: return .white
        case .onInk: return HnPalette.inkCard
        case .quiet: return HnPalette.ink
        case .outline, .text: return HnPalette.accentText
        case .textOnInk: return HnPalette.onInkAccent
        }
    }

    @ViewBuilder private var background: some View {
        switch kind {
        case .filled: HnPalette.accent
        case .onInk: Color.white
        case .quiet: HnPalette.soft
        case .outline, .text, .textOnInk: Color.clear
        }
    }
}

/// The visual of a row that goes somewhere. Kept separate from the tappable
/// wrapper so a `NavigationLink` and a sheet button look identical.
struct HnRowContent: View {
    let systemImage: String
    let title: LocalizedStringKey
    var detail: LocalizedStringKey?
    var trailing: LocalizedStringKey?
    var tint: Color = HnPalette.accent
    var showsChevron = true

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: systemImage)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(tint)
                .frame(width: 42, height: 42)
                .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(HnPalette.ink)
                    .multilineTextAlignment(.leading)
                if let detail {
                    Text(detail)
                        .font(.footnote)
                        .foregroundStyle(HnPalette.muted)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 8)
            if let trailing {
                Text(trailing)
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(HnPalette.muted)
                    .multilineTextAlignment(.trailing)
            }
            if showsChevron {
                Image(systemName: "chevron.forward")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(HnPalette.muted.opacity(0.7))
                    .accessibilityHidden(true)
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
    }
}

/// A tappable row inside a card.
struct HnLinkRow: View {
    let systemImage: String
    let title: LocalizedStringKey
    var detail: LocalizedStringKey?
    var trailing: LocalizedStringKey?
    var tint: Color = HnPalette.accent
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HnRowContent(systemImage: systemImage, title: title, detail: detail,
                         trailing: trailing, tint: tint)
        }
        .buttonStyle(.plain)
    }
}

/// A row that pushes a destination.
struct HnNavigationRow<Destination: View>: View {
    let systemImage: String
    let title: LocalizedStringKey
    var detail: LocalizedStringKey?
    var trailing: LocalizedStringKey?
    var tint: Color = HnPalette.accent
    @ViewBuilder let destination: () -> Destination

    var body: some View {
        NavigationLink {
            destination()
        } label: {
            HnRowContent(systemImage: systemImage, title: title, detail: detail,
                         trailing: trailing, tint: tint)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Numbers

/// A progress ring. Used for the protection layers; the number is also written
/// beside it, so the ring is never the only carrier of the state.
struct HnRing: View {
    let value: Double
    var size: CGFloat = 74
    var lineWidth: CGFloat = 7
    var tint: Color = HnPalette.accent
    /// Shown as a ratio, which keeps its order in Arabic as well as English.
    let done: Int
    let total: Int
    var onInk = false

    var body: some View {
        ZStack {
            Circle()
                .stroke(onInk ? Color.white.opacity(0.16) : HnPalette.soft, lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: min(max(value, 0), 1))
                .stroke(tint, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
            Text(verbatim: "\(done)/\(total)")
                .font(.hnSmallNumber())
                .foregroundStyle(onInk ? HnPalette.onInk : HnPalette.ink)
                .minimumScaleFactor(0.6)
                .lineLimit(1)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// Seven dots: the last week of practice. A filled dot is a day with a finished
/// pause; today has a ring around it.
struct HnWeekDots: View {
    /// Oldest first, seven entries.
    let days: [HnWeekDots.Day]

    struct Day: Equatable {
        let letter: String
        let done: Bool
        let today: Bool
    }

    var body: some View {
        HStack(spacing: 8) {
            ForEach(Array(days.enumerated()), id: \.offset) { _, day in
                VStack(spacing: 6) {
                    ZStack {
                        Circle()
                            .fill(day.done ? HnPalette.accent : Color.clear)
                            .frame(width: 26, height: 26)
                        Circle()
                            .strokeBorder(day.done ? Color.clear
                                                   : HnPalette.muted.opacity(0.35), lineWidth: 1.5)
                            .frame(width: 26, height: 26)
                        if day.done {
                            Image(systemName: "checkmark")
                                .font(.system(size: 11, weight: .heavy))
                                .foregroundStyle(.white)
                        }
                        if day.today {
                            Circle()
                                .strokeBorder(HnPalette.accent, lineWidth: 2)
                                .frame(width: 34, height: 34)
                        }
                    }
                    .frame(width: 34, height: 34)
                    Text(verbatim: day.letter)
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(HnPalette.muted)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Controls

/// A −/+ stepper in a soft capsule, sized for one thumb.
struct HnStepper: View {
    let title: LocalizedStringKey
    let value: Int
    let range: ClosedRange<Int>
    var step = 1
    var suffix: LocalizedStringKey?
    let onChange: (Int) -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(HnPalette.ink)
                if let suffix {
                    Text(suffix)
                        .font(.caption)
                        .foregroundStyle(HnPalette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 8)
            HStack(spacing: 4) {
                stepButton("minus", enabled: value - step >= range.lowerBound) {
                    onChange(max(range.lowerBound, value - step))
                }
                Text(verbatim: "\(value)")
                    .font(.hnSmallNumber())
                    .foregroundStyle(HnPalette.ink)
                    .frame(minWidth: dynamicTypeSize.isAccessibilitySize ? 44 : 34)
                    .monospacedDigit()
                stepButton("plus", enabled: value + step <= range.upperBound) {
                    onChange(min(range.upperBound, value + step))
                }
            }
            .padding(4)
            .background(HnPalette.soft, in: Capsule())
        }
    }

    private func stepButton(_ symbol: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(enabled ? HnPalette.ink : HnPalette.muted.opacity(0.5))
                .frame(width: 34, height: 34)
                .background(HnPalette.card, in: Circle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(Text(verbatim: symbol == "plus" ? "+" : "−"))
    }
}

/// One tappable option in a rule editor: icon, name, one honest line.
struct HnOptionRow: View {
    let systemImage: String
    let title: LocalizedStringKey
    let detail: LocalizedStringKey
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 13) {
                Image(systemName: systemImage)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(selected ? .white : HnPalette.accent)
                    .frame(width: 40, height: 40)
                    .background(selected ? HnPalette.accent : HnPalette.accentSoft,
                                in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(HnPalette.ink)
                    Text(detail)
                        .font(.caption)
                        .foregroundStyle(HnPalette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                        .multilineTextAlignment(.leading)
                }
                Spacer(minLength: 8)
                if selected {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(HnPalette.accent)
                        .accessibilityHidden(true)
                } else {
                    Image(systemName: "circle")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(HnPalette.muted.opacity(0.4))
                        .accessibilityHidden(true)
                }
            }
            .padding(10)
            .background(selected ? HnPalette.accentSoft.opacity(0.6) : Color.clear,
                        in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

// MARK: - The app's own icons

/// The chosen apps as the system draws them: FamilyControls tokens render the
/// real icon without revealing which app it is to Hisn.
struct HnAppIcons: View {
    let applicationTokens: Set<ApplicationToken>
    let categoryTokens: Set<ActivityCategoryToken>
    var emptySymbol = "app"
    var size: CGFloat = 30
    var maxShown = 3

    private var count: Int {
        applicationTokens.count + (categoryTokens.isEmpty ? 0 : 1)
    }

    var body: some View {
        HStack(spacing: -6) {
            if count == 0 {
                Image(systemName: emptySymbol)
                    .font(.system(size: size * 0.48, weight: .medium))
                    .foregroundStyle(HnPalette.muted)
                    .frame(width: size, height: size)
                    .background(HnPalette.soft, in: RoundedRectangle(cornerRadius: size * 0.3, style: .continuous))
                    .accessibilityHidden(true)
            } else {
                ForEach(Array(applicationTokens.prefix(maxShown)), id: \.self) { token in
                    Label(token)
                        .labelStyle(.iconOnly)
                        .frame(width: size, height: size)
                        .clipShape(RoundedRectangle(cornerRadius: size * 0.28, style: .continuous))
                        .background(HnPalette.card, in: RoundedRectangle(cornerRadius: size * 0.28, style: .continuous))
                        .overlay {
                            RoundedRectangle(cornerRadius: size * 0.28, style: .continuous)
                                .strokeBorder(HnPalette.card, lineWidth: 2)
                        }
                }
                if !categoryTokens.isEmpty {
                    Image(systemName: "square.grid.2x2.fill")
                        .font(.system(size: size * 0.4, weight: .semibold))
                        .foregroundStyle(HnPalette.accent)
                        .frame(width: size, height: size)
                        .background(HnPalette.accentSoft, in: RoundedRectangle(cornerRadius: size * 0.28, style: .continuous))
                        .overlay {
                            RoundedRectangle(cornerRadius: size * 0.28, style: .continuous)
                                .strokeBorder(HnPalette.card, lineWidth: 2)
                        }
                        .accessibilityHidden(true)
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("apps.icons.a11y"))
    }
}

/// A quiet heading for a group of cards.
struct HnSectionLabel: View {
    let title: LocalizedStringKey

    init(_ title: LocalizedStringKey) {
        self.title = title
    }

    var body: some View {
        Text(title)
            .font(.footnote.weight(.bold))
            .textCase(.uppercase)
            .foregroundStyle(HnPalette.muted)
            .tracking(0.6)
            .accessibilityAddTraits(.isHeader)
    }
}

/// The app's mark: three soft bars. Drawn, not bundled, so it scales with the
/// type and carries no asset weight.
struct HnMark: View {
    var size: CGFloat = 30
    var tint: Color = HnPalette.accent

    var body: some View {
        VStack(spacing: size * 0.13) {
            Capsule().fill(tint).frame(width: size * 0.62, height: size * 0.2)
            Capsule().fill(tint).frame(width: size * 0.44, height: size * 0.2)
            Capsule().fill(tint).frame(width: size * 0.78, height: size * 0.22)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

extension View {
    /// The readable column shared by every tab on iPad.
    func hnColumn(regular: Bool) -> some View {
        frame(maxWidth: regular ? 760 : .infinity)
            .frame(maxWidth: .infinity)
    }
}
