import AppKit
import SwiftUI

/// Hisn's macOS surfaces.
///
/// The window is a quiet, paper-like surface with ink typography and one deep
/// blue accent. It is deliberately closer to a well-set book than to a web
/// dashboard: a single large statement per page, compact rows, hairlines
/// instead of a card for every control, and a serif face reserved for the one
/// number or sentence the page is really about.
///
/// The vocabulary stays small: one filled action, one accent, one hairline.
/// State is carried by a word and a symbol; colour is the third signal.
enum HnPalette {
    static let canvas = dynamic(light: 0xF4F1EB, dark: 0x151412)
    static let sidebar = dynamic(light: 0xECE7DE, dark: 0x1C1A17)
    static let card = dynamic(light: 0xFFFFFF, dark: 0x24211C)
    /// A quiet fill for segments, disabled states and grouping.
    static let soft = dynamic(light: 0x000000, dark: 0xFFFFFF, lightAlpha: 0.045, darkAlpha: 0.07)
    /// A field or well inside a surface.
    static let raised = dynamic(light: 0xF6F3EC, dark: 0x302C26)
    static let ink = dynamic(light: 0x1D1B18, dark: 0xF2EEE6)
    static let muted = dynamic(light: 0x6E675D, dark: 0xA9A296)
    static let faint = dynamic(light: 0x766D61, dark: 0xA29A8D)
    static let line = dynamic(light: 0x000000, dark: 0xFFFFFF, lightAlpha: 0.10, darkAlpha: 0.11)
    static let lineStrong = dynamic(light: 0x000000, dark: 0xFFFFFF, lightAlpha: 0.17, darkAlpha: 0.19)

    /// The app's one accent: a deep ink blue, fixed so selection, sliders and
    /// the primary action agree even when the system accent is graphite.
    static let accent = dynamic(light: 0x2A4C8A, dark: 0x93AEFF)
    static let onAccent = dynamic(light: 0xFFFFFF, dark: 0x15223E)
    static let accentText = dynamic(light: 0x234174, dark: 0xA9C0FF)
    static let accentSoft = dynamic(light: 0x2A4C8A, dark: 0x93AEFF, lightAlpha: 0.10, darkAlpha: 0.16)

    static let calm = dynamic(light: 0x3E6B50, dark: 0x8FC6A2)
    static let calmSoft = dynamic(light: 0x3E6B50, dark: 0x8FC6A2, lightAlpha: 0.11, darkAlpha: 0.13)
    static let ember = dynamic(light: 0xA0641F, dark: 0xDFAC6D)
    static let emberSoft = dynamic(light: 0xA0641F, dark: 0xDFAC6D, lightAlpha: 0.10, darkAlpha: 0.13)
    static let danger = dynamic(light: 0x9C3B32, dark: 0xEE9A90)
    static let dangerSoft = dynamic(light: 0x9C3B32, dark: 0xEE9A90, lightAlpha: 0.10, darkAlpha: 0.13)

    private static func dynamic(light: UInt32, dark: UInt32,
                                lightAlpha: CGFloat = 1, darkAlpha: CGFloat = 1) -> Color {
        Color(nsColor: NSColor(name: nil) { appearance in
            let isDark = appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
            let value = isDark ? dark : light
            return NSColor(red: CGFloat((value >> 16) & 0xFF) / 255,
                           green: CGFloat((value >> 8) & 0xFF) / 255,
                           blue: CGFloat(value & 0xFF) / 255,
                           alpha: isDark ? darkAlpha : lightAlpha)
        })
    }
}

extension Font {
    /// The one editorial voice: a serif statement, used once per page.
    static func hnDisplay() -> Font { .system(size: 32, weight: .semibold, design: .serif) }
    static func hnDisplaySmall() -> Font { .system(size: 22, weight: .semibold, design: .serif) }
    /// A measured number, in the same serif voice.
    static func hnFigure(_ size: CGFloat = 40) -> Font {
        .system(size: size, weight: .semibold, design: .serif)
    }
    static func hnPageTitle() -> Font { .system(size: 28, weight: .bold) }
    static func hnHero() -> Font { .system(size: 22, weight: .semibold) }
    static func hnTitle() -> Font { .system(size: 17, weight: .semibold) }
    static func hnCardTitle() -> Font { .system(size: 14, weight: .semibold) }
    static func hnHeadline() -> Font { .system(size: 13, weight: .semibold) }
    static func hnBody() -> Font { .system(size: 13) }
    static func hnCallout() -> Font { .system(size: 13, weight: .medium) }
    static func hnCaption() -> Font { .system(size: 12) }
    static func hnSection() -> Font { .system(size: 11, weight: .semibold) }
    static func hnNumber(_ size: CGFloat) -> Font {
        .system(size: size, weight: .semibold).monospacedDigit()
    }
}

// MARK: - Surfaces

/// The canvas every page sits on.
struct HnCanvas<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        ZStack {
            HnPalette.canvas.ignoresSafeArea()
            content
        }
    }
}

/// One flat surface: a quiet fill, a separator hairline, no shadow. Cards
/// group; they are not the layout.
struct HnCard<Content: View>: View {
    var padding: CGFloat = 20
    let content: Content

    init(padding: CGFloat = 20, @ViewBuilder content: () -> Content) {
        self.padding = padding
        self.content = content()
    }

    var body: some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(padding)
            .background {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(HnPalette.card)
            }
            .overlay {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(HnPalette.line, lineWidth: 1)
                    .accessibilityHidden(true)
                    .allowsHitTesting(false)
            }
    }
}

/// A native-style settings group: the tracked label sits outside one inset
/// surface, and rows are separated by hairlines. This is the Mac's own grouped
/// form, not a card per control.
struct HnGroup<Content: View>: View {
    let title: LocalizedStringKey
    var subtitle: LocalizedStringKey? = nil
    let content: Content

    init(title: LocalizedStringKey, subtitle: LocalizedStringKey? = nil,
         @ViewBuilder content: () -> Content) {
        self.title = title
        self.subtitle = subtitle
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                HnSectionLabel(title: title)
                if let subtitle {
                    HnExplain { Text(subtitle) }
                }
                Spacer(minLength: 0)
            }
            HnCard(padding: 0) {
                VStack(spacing: 0) {
                    content
                }
            }
        }
    }
}

/// One row inside `HnGroup`.
struct HnGroupRow<Content: View>: View {
    var padding: CGFloat = 16
    let content: Content

    init(padding: CGFloat = 16, @ViewBuilder content: () -> Content) {
        self.padding = padding
        self.content = content()
    }

    var body: some View {
        content
            .padding(.horizontal, padding)
            .padding(.vertical, 11)
    }
}

/// A rule between rows, never a box.
struct HnHairline: View {
    var inset: CGFloat = 0
    var body: some View {
        Rectangle()
            .fill(HnPalette.line)
            .frame(height: 1)
            .padding(.leading, inset)
            .accessibilityHidden(true)
    }
}

// MARK: - Type

/// The small tracked label above a section. The tracking is disabled in
/// Arabic, where letter spacing breaks the joins.
struct HnSectionLabel: View {
    let title: LocalizedStringKey
    var systemImage: String? = nil
    var trailing: Text? = nil
    @Environment(\.layoutDirection) private var direction

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            if let systemImage {
                Image(systemName: systemImage)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(HnPalette.faint)
                    .accessibilityHidden(true)
            }
            Text(title)
                .font(.hnSection())
                .tracking(direction == .rightToLeft ? 0 : 0.8)
                .textCase(.uppercase)
                .foregroundStyle(HnPalette.muted)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 8)
            if let trailing {
                trailing
                    .font(.hnCaption())
                    .foregroundStyle(HnPalette.faint)
            }
        }
        .accessibilityElement(children: .contain)
    }
}

/// The top of a page: a tracked page name, one serif statement, one short
/// caption. The statement is the only display type in the window.
struct HnPageHeader: View {
    @Environment(\.layoutDirection) private var direction
    let eyebrow: LocalizedStringKey
    let title: Text
    let caption: Text?

    init(eyebrow: LocalizedStringKey, title: LocalizedStringKey,
         caption: LocalizedStringKey? = nil) {
        self.eyebrow = eyebrow
        self.title = Text(title)
        self.caption = caption.map { Text($0) }
    }

    init(eyebrow: LocalizedStringKey, verbatim title: String, caption: String? = nil) {
        self.eyebrow = eyebrow
        self.title = Text(verbatim: title)
        self.caption = caption.map { Text(verbatim: $0) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Rectangle()
                    .fill(HnPalette.accent)
                    .frame(width: 18, height: 2)
                    .accessibilityHidden(true)
                Text(eyebrow)
                    .font(.hnSection())
                    .tracking(direction == .rightToLeft ? 0 : 0.8)
                    .textCase(.uppercase)
                    .foregroundStyle(HnPalette.muted)
            }
            title
                .font(.hnDisplay())
                .foregroundStyle(HnPalette.ink)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            if let caption {
                caption
                    .font(.hnBody())
                    .foregroundStyle(HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: 560, alignment: .leading)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Two columns when there is room, one when there is not. A custom layout
/// rather than `ViewThatFits`: the layer details have a large ideal width, so
/// `ViewThatFits` fell back to a single column at a window size that had room
/// for both. SwiftUI mirrors this layout's coordinates in right-to-left, so
/// the main column stays on the reading side.
struct HnTwoColumn<Main: View, Rail: View>: View {
    var breakpoint: CGFloat = 740
    var spacing: CGFloat = 28
    var railWidth: CGFloat = 260
    let main: Main
    let rail: Rail

    init(breakpoint: CGFloat = 740, spacing: CGFloat = 28, railWidth: CGFloat = 260,
         @ViewBuilder main: () -> Main, @ViewBuilder rail: () -> Rail) {
        self.breakpoint = breakpoint
        self.spacing = spacing
        self.railWidth = railWidth
        self.main = main()
        self.rail = rail()
    }

    var body: some View {
        if #available(macOS 13.0, *), !HnCompat.forceLegacy {
            HnTwoColumnLayout(breakpoint: breakpoint, spacing: spacing, railWidth: railWidth) {
                main
                rail
            }
        } else {
            HnTwoColumnFallback(breakpoint: breakpoint, spacing: spacing, railWidth: railWidth,
                                main: main, rail: rail)
        }
    }
}

/// The macOS 12 spelling of `HnTwoColumn`: the same breakpoint and columns,
/// chosen from the width the view is actually given rather than from a
/// proposal.
///
/// The width arrives one turn after the first frame, so the very first frame
/// is drawn as one column and reflows if the window is wide. Reading it before
/// layout would mean `GeometryReader` owning the height, which is the wrong
/// trade inside a scrolling page.
private struct HnTwoColumnFallback<Main: View, Rail: View>: View {
    let breakpoint: CGFloat
    let spacing: CGFloat
    let railWidth: CGFloat
    let main: Main
    let rail: Rail

    @State private var width: CGFloat = 0

    var body: some View {
        Group {
            if width >= breakpoint {
                HStack(alignment: .top, spacing: spacing) {
                    main.frame(width: max(0, width - railWidth - spacing), alignment: .leading)
                    rail.frame(width: railWidth, alignment: .leading)
                }
            } else {
                VStack(alignment: .leading, spacing: spacing) {
                    main
                    rail.frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(GeometryReader { proxy in
            Color.clear
                .onAppear { width = proxy.size.width }
                .onChange(of: proxy.size.width) { width = $0 }
        })
    }
}

@available(macOS 13.0, *)
private struct HnTwoColumnLayout: Layout {
    var breakpoint: CGFloat
    var spacing: CGFloat
    var railWidth: CGFloat

    private func mainWidth(for width: CGFloat) -> CGFloat {
        max(0, width - railWidth - spacing)
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews,
                      cache: inout ()) -> CGSize {
        guard subviews.count == 2 else { return .zero }
        let width = proposal.width ?? breakpoint
        if width >= breakpoint {
            let mw = mainWidth(for: width)
            let main = subviews[0].sizeThatFits(ProposedViewSize(width: mw, height: nil))
            let rail = subviews[1].sizeThatFits(ProposedViewSize(width: railWidth, height: nil))
            return CGSize(width: width, height: max(main.height, rail.height))
        }
        let main = subviews[0].sizeThatFits(ProposedViewSize(width: width, height: nil))
        let rail = subviews[1].sizeThatFits(ProposedViewSize(width: width, height: nil))
        return CGSize(width: width, height: main.height + spacing + rail.height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize,
                       subviews: Subviews, cache: inout ()) {
        guard subviews.count == 2 else { return }
        if bounds.width >= breakpoint {
            let mw = mainWidth(for: bounds.width)
            let main = subviews[0].sizeThatFits(ProposedViewSize(width: mw, height: nil))
            subviews[0].place(at: CGPoint(x: bounds.minX, y: bounds.minY),
                              anchor: .topLeading,
                              proposal: ProposedViewSize(width: mw, height: main.height))
            subviews[1].place(at: CGPoint(x: bounds.minX + mw + spacing, y: bounds.minY),
                              anchor: .topLeading,
                              proposal: ProposedViewSize(width: railWidth, height: nil))
        } else {
            let main = subviews[0].sizeThatFits(ProposedViewSize(width: bounds.width, height: nil))
            subviews[0].place(at: CGPoint(x: bounds.minX, y: bounds.minY),
                              anchor: .topLeading,
                              proposal: ProposedViewSize(width: bounds.width, height: main.height))
            subviews[1].place(at: CGPoint(x: bounds.minX, y: bounds.minY + main.height + spacing),
                              anchor: .topLeading,
                              proposal: ProposedViewSize(width: bounds.width, height: nil))
        }
    }
}

/// The older section heading, kept for the setup and network guides. It is a
/// quiet title case label rather than a second page title.
struct HnSectionHeader: View {
    let title: LocalizedStringKey
    var systemImage: String?

    var body: some View {
        HStack(spacing: 6) {
            if let systemImage {
                Image(systemName: systemImage)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(HnPalette.muted)
                    .accessibilityHidden(true)
            }
            Text(title)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(HnPalette.ink)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 8)
        }
        .accessibilityElement(children: .contain)
    }
}

/// A small word in a rectangle: state names, counts. Not a pill — pills are
/// reserved for selections that behave like filters.
struct HnBadge: View {
    let label: Text
    var tint: Color = HnPalette.accentText

    init(text: LocalizedStringKey, tint: Color = HnPalette.accentText) {
        self.label = Text(text)
        self.tint = tint
    }

    init(verbatim: String, tint: Color = HnPalette.accentText) {
        self.label = Text(verbatim: verbatim)
        self.tint = tint
    }

    var body: some View {
        label
            .font(.system(size: 10.5, weight: .semibold))
            .foregroundStyle(tint)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(RoundedRectangle(cornerRadius: 4, style: .continuous)
                .fill(tint.opacity(0.10)))
            .fixedSize()
    }
}

// MARK: - Explain

/// The one place an explanation is allowed: a small info button that opens a
/// popover. Icon-only on purpose — a row of "Explain" labels competes with the
/// controls it is meant to clarify.
struct HnExplain<Content: View>: View {
    var title: LocalizedStringKey = "Explain"
    @ViewBuilder let content: Content
    @State private var showing = false
    @State private var hovered = false

    init(title: LocalizedStringKey = "Explain", @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        Button { showing.toggle() } label: {
            Image(systemName: "info.circle")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(hovered || showing ? HnPalette.accentText : HnPalette.faint)
                .frame(width: 20, height: 20)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { hovered = $0 }
        .help(title)
        .popover(isPresented: $showing, arrowEdge: .bottom) {
            VStack(alignment: .leading, spacing: 8) {
                content
            }
            .font(.system(size: 12))
            .foregroundStyle(HnPalette.muted)
            .fixedSize(horizontal: false, vertical: true)
            .frame(width: 320, alignment: .leading)
            .padding(16)
        }
        .accessibilityLabel(title)
    }
}

// MARK: - Actions

/// The one filled action on a screen, plus its quieter siblings. Mac-sized:
/// 28 points tall rather than the phone's 52.
struct HnButton: View {
    enum Kind { case filled, quiet, outline, text }

    let label: Text
    var systemImage: String?
    var kind: Kind = .filled
    var fullWidth = false
    var loading = false
    var compact = false
    var action: () -> Void

    @Environment(\.isEnabled) private var isEnabled
    @State private var hovered = false

    init(title: LocalizedStringKey, systemImage: String? = nil, kind: Kind = .filled,
         fullWidth: Bool = false, loading: Bool = false, compact: Bool = false,
         action: @escaping () -> Void) {
        self.label = Text(title)
        self.systemImage = systemImage
        self.kind = kind
        self.fullWidth = fullWidth
        self.loading = loading
        self.compact = compact
        self.action = action
    }

    /// For a title assembled at runtime from localized fragments (a length, a
    /// count). The pieces are already localized; the whole is not a new key.
    init(text: String, systemImage: String? = nil, kind: Kind = .filled,
         fullWidth: Bool = false, loading: Bool = false, compact: Bool = false,
         action: @escaping () -> Void) {
        self.label = Text(verbatim: text)
        self.systemImage = systemImage
        self.kind = kind
        self.fullWidth = fullWidth
        self.loading = loading
        self.compact = compact
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if loading {
                    ProgressView().controlSize(.small)
                } else if let systemImage {
                    Image(systemName: systemImage)
                        .font(.system(size: compact ? 10 : 11, weight: .semibold))
                }
                label
                    .font(.system(size: compact ? 12 : 13, weight: .semibold))
                    .lineLimit(fullWidth ? 2 : 1)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .foregroundStyle(foreground)
            .frame(maxWidth: fullWidth ? .infinity : nil)
            .padding(.horizontal, compact ? 9 : 12)
            .padding(.vertical, fullWidth ? 6 : 0)
            .frame(minHeight: compact ? 28 : 32)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
            .overlay {
                if kind == .outline || (kind == .filled && !isEnabled) {
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .strokeBorder(HnPalette.lineStrong, lineWidth: 1)
                }
            }
            .contentShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(loading)
        .help(label)
        .onHover { hovered = $0 }
    }

    private var foreground: Color {
        switch kind {
        case .filled: return isEnabled ? HnPalette.onAccent : HnPalette.muted
        case .quiet, .outline: return isEnabled ? HnPalette.ink : HnPalette.faint
        case .text: return isEnabled ? HnPalette.accentText : HnPalette.faint
        }
    }

    @ViewBuilder private var background: some View {
        switch kind {
        case .filled:
            isEnabled ? (hovered ? HnPalette.accent.opacity(0.88) : HnPalette.accent) : HnPalette.soft
        case .quiet:
            isEnabled ? (hovered ? HnPalette.line : HnPalette.soft) : HnPalette.soft.opacity(0.5)
        case .outline, .text:
            hovered && isEnabled ? HnPalette.soft : Color.clear
        }
    }
}

/// A compact icon button, for toolbar and card corners.
struct HnIconButtonStyle: ButtonStyle {
    var tint: Color = HnPalette.ink
    var size: CGFloat = 26

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(tint)
            .frame(width: size, height: size)
            .background(HnPalette.soft.opacity(configuration.isPressed ? 1 : 0.65),
                        in: RoundedRectangle(cornerRadius: 5, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: 5, style: .continuous))
    }
}

/// A selection chip for presets and filters. The selected state is a quiet
/// accent wash, not a saturated pill.
struct HnChip: View {
    let label: Text
    let selected: Bool
    var action: () -> Void
    @State private var hovered = false

    init(title: LocalizedStringKey, selected: Bool, action: @escaping () -> Void) {
        self.label = Text(title)
        self.selected = selected
        self.action = action
    }

    init(verbatim title: String, selected: Bool, action: @escaping () -> Void) {
        self.label = Text(verbatim: title)
        self.selected = selected
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            label
                .font(.system(size: 11.5, weight: .medium))
                .foregroundStyle(selected ? HnPalette.accentText : HnPalette.ink)
                .lineLimit(1)
                .padding(.horizontal, 10)
                .frame(height: 26)
                .background(selected ? HnPalette.accentSoft
                              : hovered ? HnPalette.line : HnPalette.soft,
                            in: RoundedRectangle(cornerRadius: 5, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 5, style: .continuous)
                        .strokeBorder(selected ? HnPalette.accent.opacity(0.35) : HnPalette.line,
                                      lineWidth: 1)
                }
                .contentShape(RoundedRectangle(cornerRadius: 5, style: .continuous))
        }
        .buttonStyle(.plain)
        .onHover { hovered = $0 }
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}

// MARK: - Rows

/// The visual of a row that goes somewhere. Kept separate from the tappable
/// wrapper so a button and a disclosure look identical.
struct HnRowContent: View {
    let systemImage: String
    let title: LocalizedStringKey
    var detail: LocalizedStringKey?
    var trailing: LocalizedStringKey?
    var tint: Color = HnPalette.muted
    var showsChevron = true

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: systemImage)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(tint)
                .frame(width: 16)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(HnPalette.ink)
                    .multilineTextAlignment(.leading)
                if let detail {
                    Text(detail)
                        .font(.hnCaption())
                        .foregroundStyle(HnPalette.muted)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 8)
            if let trailing {
                Text(trailing)
                    .font(.hnCaption())
                    .foregroundStyle(HnPalette.muted)
                    .multilineTextAlignment(.trailing)
            }
            if showsChevron {
                Image(systemName: "chevron.forward")
                    .font(.system(size: 9, weight: .semibold))
                    .foregroundStyle(HnPalette.faint)
                    .accessibilityHidden(true)
            }
        }
        .padding(.vertical, 3)
        .contentShape(Rectangle())
    }
}

struct HnLinkRow: View {
    let systemImage: String
    let title: LocalizedStringKey
    var detail: LocalizedStringKey?
    var trailing: LocalizedStringKey?
    var tint: Color = HnPalette.muted
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HnRowContent(systemImage: systemImage, title: title, detail: detail,
                         trailing: trailing, tint: tint)
        }
        .buttonStyle(.plain)
    }
}

/// A labelled row for toggles: the switch is the control, the words are the
/// row. The detail stays in a small info popover rather than a paragraph.
struct HnToggleRow: View {
    let title: LocalizedStringKey
    var detail: LocalizedStringKey?
    @Binding var isOn: Bool

    var body: some View {
        HStack(spacing: 10) {
            HStack(spacing: 5) {
                Text(title)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(HnPalette.ink)
                if let detail {
                    HnExplain { Text(detail) }
                }
            }
            Spacer(minLength: 8)
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .toggleStyle(.switch)
                .controlSize(.small)
                .accessibilityLabel(Text(title))
        }
    }
}

// MARK: - Notices

/// A warning or explanation, inline under the status it qualifies. A left
/// accent rule rather than a speech bubble: it reads as an annotation on the
/// page, not as another card.
struct HnNotice: View {
    enum Level { case info, caution, problem }

    let title: LocalizedStringKey?
    let message: Text
    var systemImage: String
    var level: Level = .caution
    var action: (title: LocalizedStringKey, action: () -> Void)?

    init(title: LocalizedStringKey?, message: LocalizedStringKey,
         systemImage: String, level: Level = .caution,
         action: (title: LocalizedStringKey, action: () -> Void)? = nil) {
        self.title = title
        self.message = Text(message)
        self.systemImage = systemImage
        self.level = level
        self.action = action
    }

    init(title: LocalizedStringKey?, verbatim message: String,
         systemImage: String, level: Level = .caution,
         action: (title: LocalizedStringKey, action: () -> Void)? = nil) {
        self.title = title
        self.message = Text(verbatim: message)
        self.systemImage = systemImage
        self.level = level
        self.action = action
    }

    private var tint: Color {
        switch level {
        case .info: return HnPalette.accent
        case .caution: return HnPalette.ember
        case .problem: return HnPalette.danger
        }
    }

    var body: some View {
        HStack(spacing: 0) {
            Rectangle()
                .fill(tint)
                .frame(width: 2)
                .accessibilityHidden(true)
            HStack(alignment: .top, spacing: 9) {
                Image(systemName: systemImage)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(tint)
                    .padding(.top, 2)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    if let title {
                        Text(title)
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundStyle(HnPalette.ink)
                    }
                    message
                        .font(.hnCaption())
                        .foregroundStyle(HnPalette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                    if let action {
                        Button(action.title, action: action.action)
                            .buttonStyle(.plain)
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(HnPalette.accentText)
                            .padding(.top, 1)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 11)
            .padding(.vertical, 8)
        }
        .background(tint.opacity(0.055),
                    in: RoundedRectangle(cornerRadius: 6, style: .continuous))
        .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Numbers

/// A progress ring. The number is also written in the middle, so the ring is
/// never the only carrier of the state.
struct HnRing: View {
    let done: Int
    let total: Int
    var size: CGFloat = 52
    var lineWidth: CGFloat = 4
    var tint: Color = HnPalette.accent

    private var fraction: Double {
        guard total > 0 else { return 0 }
        return min(1, max(0, Double(done) / Double(total)))
    }

    var body: some View {
        ZStack {
            Circle()
                .stroke(HnPalette.line, lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: fraction)
                .stroke(tint, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
            Text(verbatim: "\(done)/\(total)")
                .font(.system(size: size * 0.24, weight: .semibold).monospacedDigit())
                .foregroundStyle(HnPalette.ink)
                .minimumScaleFactor(0.6)
                .lineLimit(1)
        }
        .frame(width: size, height: size)
    }
}

/// One number with its label: the right rail's small unit of evidence.
struct HnStat: View {
    let value: String
    let label: LocalizedStringKey
    var detail: String? = nil
    var tint: Color = HnPalette.ink

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(verbatim: value)
                .font(.hnFigure(26))
                .foregroundStyle(tint)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .monospacedDigit()
            Text(label)
                .font(.hnCaption())
                .foregroundStyle(HnPalette.muted)
            if let detail {
                Text(verbatim: detail)
                    .font(.hnCaption())
                    .foregroundStyle(HnPalette.faint)
                    .lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Fields

/// A text field that belongs on the surface, not a system bevel floating in it.
struct HnField: ViewModifier {
    var monospaced = false

    func body(content: Content) -> some View {
        content
            .textFieldStyle(.plain)
            .font(monospaced ? .system(size: 12, design: .monospaced) : .hnBody())
            .foregroundStyle(HnPalette.ink)
            .padding(.horizontal, 8)
            .frame(minHeight: 32)
            .background(HnPalette.raised,
                        in: RoundedRectangle(cornerRadius: 5, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 5, style: .continuous)
                    .strokeBorder(HnPalette.line, lineWidth: 1)
            }
    }
}

extension View {
    func hnField(monospaced: Bool = false) -> some View {
        modifier(HnField(monospaced: monospaced))
    }

    /// The readable column shared by every page.
    func hnColumn() -> some View {
        frame(maxWidth: 980, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .center)
    }
}

/// The small brand mark: a shield in a flat accent tile.
struct HnMark: View {
    var size: CGFloat = 28

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.24, style: .continuous)
                .fill(HnPalette.accent)
            Image(systemName: "shield.lefthalf.filled")
                .font(.system(size: size * 0.48, weight: .semibold))
                .foregroundStyle(.white)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}
