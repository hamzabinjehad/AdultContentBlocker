import SwiftUI

/// The first thing the overview says: is protection on, what is on, and what is
/// the one next action. The full diagnostics stay one disclosure below rather
/// than meeting the person as a wall of text.
struct ProtectionHero: View {
    @ObservedObject var protection: ProtectionController
    let onSetup: () -> Void
    let onRefresh: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var showingIssues = false

    private var assessment: MobileProtectionPolicy.SetupAssessment { protection.setupAssessment }

    var body: some View {
        HisnCard(tint: tint) {
            VStack(alignment: .leading, spacing: 16) {
                header
                layerChips
                action
                statusLine
                if !assessment.issues.isEmpty {
                    HisnDivider()
                    issues
                }
                Text("hero.scope")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .accessibilityIdentifier("overview.heroCard")
        }
    }

    @ViewBuilder private var header: some View {
        if dynamicTypeSize.isAccessibilitySize {
            VStack(alignment: .leading, spacing: 12) {
                statusBadge
                headerText
            }
        } else {
            HStack(alignment: .top, spacing: 14) {
                statusBadge
                headerText
                Spacer(minLength: 0)
            }
        }
    }

    private var headerText: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(titleKey)
                .font(.title3.bold())
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Text(detailKey)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var layerChips: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 8) { chips }
            VStack(alignment: .leading, spacing: 6) { chips }
        }
    }

    @ViewBuilder private var chips: some View {
        ForEach(HeroLayer.allCases) { layer in
            let on = isOn(layer)
            HStack(spacing: 5) {
                // No dotted SF Symbol literal here: the repository's literal-key
                // scanner would read it as a localization key. The filled state
                // is carried by colour and the capsule.
                Image(systemName: on ? "checkmark" : "circle")
                    .accessibilityHidden(true)
                Text(layer.title)
            }
            .font(.caption.weight(.semibold))
            .foregroundStyle(on ? HisnPalette.good : Color.secondary)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(
                Capsule().fill(on ? HisnPalette.good.opacity(0.12) : HisnSurface.fill)
            )
            .accessibilityElement(children: .combine)
            .accessibilityLabel(Text(layer.title))
            .accessibilityValue(Text(layerState(on)))
        }
    }

    @ViewBuilder private var action: some View {
        switch assessment.state {
        case .configured:
            Button(action: onRefresh) {
                Label("hero.checkAgain", systemImage: "arrow.clockwise")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .controlSize(.large)
            .disabled(protection.busy)
        case .needsSetup:
            Button(action: onSetup) {
                Label("hero.finishSetup", systemImage: "arrow.forward.circle.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        case .unchecked:
            Button(action: onRefresh) {
                Label("hero.checkAgain", systemImage: "arrow.clockwise")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(protection.busy)
        case .checking:
            ProgressView("working")
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    @ViewBuilder private var statusLine: some View {
        if let date = protection.checkedAt {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 3) {
                    Label("status.checked", systemImage: "clock")
                    Text(date, style: .time).monospacedDigit()
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            } else {
                HStack(spacing: 6) {
                    Image(systemName: "clock")
                        .accessibilityHidden(true)
                    Text("status.checked")
                    Spacer(minLength: 8)
                    Text(date, style: .time)
                        .monospacedDigit()
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }
        }
    }

    private var issues: some View {
        DisclosureGroup(isExpanded: $showingIssues) {
            VStack(alignment: .leading, spacing: 10) {
                ForEach(assessment.issues, id: \.rawValue) { issue in
                    Label(LocalizedStringKey(issue.rawValue), systemImage: "arrow.forward.circle")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(.top, 6)
        } label: {
            issueLabel
                .font(.callout.weight(.medium))
                .foregroundStyle(HisnPalette.warning)
        }
    }

    @ViewBuilder private var issueLabel: some View {
        if assessment.issues.count == 1 {
            Text("hero.issuesOne")
        } else {
            Text("hero.issues \(assessment.issues.count)")
        }
    }

    private func isOn(_ layer: HeroLayer) -> Bool {
        switch layer {
        case .safari: return protection.safariConfigured
        case .dns: return protection.dns == .enabled
        case .screenTime: return protection.screenTimeConfigured
        }
    }

    private func layerState(_ on: Bool) -> LocalizedStringKey {
        on ? "hero.layerOn" : "hero.layerOff"
    }

    private var titleKey: LocalizedStringKey {
        switch assessment.state {
        case .configured: return "hero.on"
        case .needsSetup: return "hero.attention"
        case .unchecked: return "hero.unchecked"
        case .checking: return "hero.checking"
        }
    }

    private var detailKey: LocalizedStringKey {
        switch assessment.state {
        case .configured: return "hero.onDetail"
        case .needsSetup: return "hero.attentionDetail"
        case .unchecked: return "hero.uncheckedDetail"
        case .checking: return "hero.checkingDetail"
        }
    }

    @ViewBuilder private var statusBadge: some View {
        switch assessment.state {
        case .configured:
            HisnIconBadge(systemName: "checkmark.shield.fill", tint: tint, size: 58)
        case .needsSetup:
            HisnIconBadge(systemName: "exclamationmark.shield.fill", tint: tint, size: 58)
        case .unchecked:
            HisnIconBadge(systemName: "questionmark.circle", tint: tint, size: 58)
        case .checking:
            HisnIconBadge(systemName: "arrow.clockwise", tint: tint, size: 58)
        }
    }

    private var tint: Color {
        switch assessment.state {
        case .configured: return HisnPalette.good
        case .needsSetup: return HisnPalette.warning
        case .unchecked, .checking: return .accentColor
        }
    }
}

private enum HeroLayer: CaseIterable, Identifiable {
    case safari, dns, screenTime

    var id: String {
        switch self {
        case .safari: return "safari"
        case .dns: return "dns"
        case .screenTime: return "screenTime"
        }
    }

    var title: LocalizedStringKey {
        switch self {
        case .safari: return "hero.safari"
        case .dns: return "hero.dns"
        case .screenTime: return "hero.screenTime"
        }
    }
}
