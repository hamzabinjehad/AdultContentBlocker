import SwiftUI

/// The optional distraction-site chips, shared by first run and Blocking
/// Rules.
///
/// The list is a set of exact sites, not categories: every chip names the site
/// it would block. A selected chip is in the saved list; an unselected one is
/// not. The parent view owns the list, so the picker works against a draft on
/// Blocking Rules and against the saved list in first run.
struct ExtraSitesPicker: View {
    let blocks: [String]
    var isLocked = false
    var showsHeader = true
    let toggle: (String) -> Void

    private var added: Int { ExtraSites.addedCount(in: blocks) }

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            if showsHeader {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text("Popular distraction sites")
                        .font(.system(.callout).weight(.medium))
                        .foregroundStyle(HnPalette.ink)
                    HnExplain {
                        Text("Adds these sites to your block list, on top of the adult-content lists. Optional — nothing here is needed for adult-content protection.")
                        Text("A site covered by a wider rule you added is shown as blocked and left alone.")
                    }
                    Spacer(minLength: 0)
                    Text(verbatim: "\(added)/\(ExtraSites.suggestions.count)")
                        .font(.system(.caption).weight(.medium).monospacedDigit())
                        .foregroundStyle(added > 0 ? HnPalette.accentText : HnPalette.muted)
                        .accessibilityLabel(Text("Extra sites added"))
                        .accessibilityValue(Text(verbatim: "\(added) of \(ExtraSites.suggestions.count)"))
                }
            }

            HnChipFlow(items: ExtraSites.suggestions, spacing: 7) { domain in
                ExtraSiteChip(domain: domain,
                              coverage: ExtraSites.coverage(of: domain, in: blocks),
                              isLocked: isLocked,
                              toggle: { toggle(domain) })
            }

            if isLocked {
                Text("Adding works now. Removing waits.")
                    .font(.hnCaption())
                    .foregroundStyle(HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text("Popular distraction sites"))
        .accessibilityIdentifier("extraSites.picker")
    }
}

/// One site chip. The whole pill is the target, which is why this is a button
/// with a plain style rather than a control with a separate check box.
private struct ExtraSiteChip: View {
    let domain: String
    let coverage: ExtraSites.Coverage
    let isLocked: Bool
    let toggle: () -> Void

    @State private var hovered = false

    private var blocked: Bool { coverage.isBlocked }

    private var parent: String? {
        if case let .parent(entry) = coverage { return entry }
        return nil
    }

    /// A lock stops removals, not additions; a parent rule is never edited
    /// from here because that would unblock the parent's other sites too.
    private var canToggle: Bool { parent == nil && !(isLocked && blocked) }

    /// The selected state owns the accent border; an unselected chip only
    /// strengthens its hairline on hover, so a hovered pill cannot be mistaken
    /// for a selected one in a capture or at a glance.
    private var borderTint: Color {
        if blocked { return HnPalette.accent.opacity(hovered ? 0.45 : 0.32) }
        return hovered && canToggle ? HnPalette.lineStrong : HnPalette.line
    }

    private var helpText: Text {
        if let parent {
            return Text("Already blocked by") + Text(verbatim: " \(parent)")
        }
        return Text(blocked ? "Remove from block list" : "Add to block list")
    }

    var body: some View {
        Button(action: toggle) {
            HStack(spacing: 5) {
                Image(systemName: blocked ? "checkmark" : "plus")
                    .font(.system(size: 9, weight: .bold))
                    .frame(width: 9)
                    .accessibilityHidden(true)
                Text(verbatim: domain)
                    .font(.system(.caption).weight(.medium))
                    .lineLimit(1)
            }
            .foregroundStyle(blocked ? HnPalette.accentText : HnPalette.ink)
            .padding(.horizontal, 10)
            .frame(height: 26)
            .background(blocked ? HnPalette.accentSoft : HnPalette.soft, in: Capsule())
            .overlay {
                Capsule()
                    .strokeBorder(borderTint, lineWidth: 1)
            }
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .disabled(!canToggle)
        .opacity(canToggle ? 1 : 0.62)
        .onHover { hovered = $0 }
        .help(helpText)
        .accessibilityLabel(Text(verbatim: domain))
        .accessibilityValue(Text(blocked ? "Blocked" : "Not blocked"))
        .accessibilityHint(helpText)
        .accessibilityAddTraits(blocked ? [.isSelected] : [])
        .accessibilityIdentifier("extraSites.\(domain)")
    }
}

/// The chip flow, taking the `Layout` on macOS 13 and the measured fallback
/// below it. The chips decide their own width; the container decides where the
/// line ends.
private struct HnChipFlow<Content: View>: View {
    let items: [String]
    let spacing: CGFloat
    let content: (String) -> Content

    var body: some View {
        if #available(macOS 13.0, *), !HnCompat.forceLegacy {
            HnFlowLayout(spacing: spacing) {
                ForEach(items, id: \.self) { content($0) }
            }
        } else {
            HnMeasuredChipFlow(items: items, spacing: spacing, content: content)
        }
    }
}

/// The macOS 12 spelling of `HnFlowLayout`.
///
/// A chip's width is its natural size, so each row is the longest run that
/// still fits the width the flow is given. Widths and the container width are
/// measured with preferences; both are stable for a given text and font, so
/// the flow settles after one layout pass and does not oscillate.
private struct HnMeasuredChipFlow<Content: View>: View {
    let items: [String]
    let spacing: CGFloat
    let content: (String) -> Content

    @State private var widths: [String: CGFloat] = [:]
    @State private var available: CGFloat = 0

    private var rows: [[String]] {
        guard available > 0 else { return items.map { [$0] } }
        var rows: [[String]] = []
        var row: [String] = []
        var used: CGFloat = 0
        for item in items {
            let width = widths[item] ?? 0
            if row.isEmpty {
                row = [item]
                used = width
            } else if used + spacing + width <= available {
                row.append(item)
                used += spacing + width
            } else {
                rows.append(row)
                row = [item]
                used = width
            }
        }
        if !row.isEmpty { rows.append(row) }
        return rows
    }

    var body: some View {
        VStack(alignment: .leading, spacing: spacing) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack(spacing: spacing) {
                    ForEach(row, id: \.self) { item in
                        content(item)
                            .fixedSize()
                            .background(GeometryReader { proxy in
                                Color.clear.preference(key: HnChipWidthKey.self,
                                                       value: [item: proxy.size.width])
                            })
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(GeometryReader { proxy in
            Color.clear
                .onAppear { available = proxy.size.width }
                .onChange(of: proxy.size.width) { available = $0 }
        })
        .onPreferenceChange(HnChipWidthKey.self) { measured in
            widths.merge(measured) { _, new in new }
        }
    }
}

private struct HnChipWidthKey: PreferenceKey {
    static var defaultValue: [String: CGFloat] = [:]

    static func reduce(value: inout [String: CGFloat], nextValue: () -> [String: CGFloat]) {
        value.merge(nextValue()) { _, new in new }
    }
}

/// A wrapping row of variable-width chips.
///
/// `LazyVGrid` gives every cell the same width, which turns twelve short
/// domains into a sparse table. This keeps each pill at its natural width and
/// wraps at the card's edge. SwiftUI mirrors a custom layout's coordinate space
/// for right-to-left languages, so the placement below is always written from
/// the leading edge; the framework flips it, including the order and each
/// chip's own icon/text order.
@available(macOS 13.0, *)
private struct HnFlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews,
                      cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        let sizes = subviews.map { $0.sizeThatFits(.unspecified) }
        guard !sizes.isEmpty else { return .zero }

        var width: CGFloat = 0
        var height: CGFloat = 0
        var lineWidth: CGFloat = 0
        var lineHeight: CGFloat = 0

        for size in sizes {
            let next = lineWidth == 0 ? size.width : lineWidth + spacing + size.width
            if lineWidth > 0, maxWidth.isFinite, next > maxWidth {
                width = max(width, lineWidth)
                height += lineHeight + spacing
                lineWidth = size.width
                lineHeight = size.height
            } else {
                lineWidth = next
                lineHeight = max(lineHeight, size.height)
            }
        }
        width = max(width, lineWidth)
        height += lineHeight

        if maxWidth.isFinite { width = maxWidth }
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize,
                       subviews: Subviews, cache: inout ()) {
        guard !subviews.isEmpty, bounds.width > 0 else { return }
        let sizes = subviews.map { $0.sizeThatFits(.unspecified) }
        var y = bounds.minY
        var start = 0

        while start < subviews.count {
            var end = start
            var lineWidth = sizes[start].width
            while end + 1 < subviews.count {
                let candidate = lineWidth + spacing + sizes[end + 1].width
                if candidate > bounds.width { break }
                end += 1
                lineWidth = candidate
            }

            var x = bounds.minX
            var lineHeight: CGFloat = 0
            for index in start...end {
                let size = sizes[index]
                subviews[index].place(
                    at: CGPoint(x: x, y: y),
                    anchor: .topLeading,
                    proposal: ProposedViewSize(width: size.width, height: size.height))
                x += size.width + spacing
                lineHeight = max(lineHeight, size.height)
            }

            y += lineHeight + spacing
            start = end + 1
        }
    }
}
