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
        Section {
            VStack(alignment: .leading, spacing: 14) {
                header
                layerChips
                action
                if !assessment.issues.isEmpty { issues }
                Text("hero.scope")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.vertical, 4)
            .accessibilityIdentifier("overview.heroCard")
        }
    }

    private var header: some View {
        HStack(alignment: .top, spacing: 14) {
            ZStack {
                Circle()
                    .fill(tint.opacity(0.12))
                statusIcon
                    .font(.system(size: 28, weight: .semibold))
                    .foregroundStyle(tint)
            }
            .frame(width: 56, height: 56)
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 3) {
                Text(titleKey)
                    .font(.title3.bold())
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                Text(detailKey)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
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
            HStack(spacing: 4) {
                // No dotted SF Symbol literal here: the repository's literal-key
                // scanner would read it as a localization key. The filled state
                // is carried by colour and the capsule.
                Image(systemName: on ? "checkmark" : "circle")
                    .accessibilityHidden(true)
                Text(layer.title)
            }
            .font(.caption.weight(.medium))
            .foregroundStyle(on ? HisnPalette.good : Color.secondary)
            .padding(.horizontal, 9)
            .padding(.vertical, 5)
            .background(
                Capsule().fill(on ? HisnPalette.good.opacity(0.12) : Color.secondary.opacity(0.10))
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
            .disabled(protection.busy)
        case .needsSetup:
            Button(action: onSetup) {
                Label("hero.finishSetup", systemImage: "arrow.forward.circle.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
        case .unchecked:
            Button(action: onRefresh) {
                Label("hero.checkAgain", systemImage: "arrow.clockwise")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(protection.busy)
        case .checking:
            ProgressView("working")
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var issues: some View {
        DisclosureGroup(isExpanded: $showingIssues) {
            ForEach(assessment.issues, id: \.rawValue) { issue in
                Label(LocalizedStringKey(issue.rawValue), systemImage: "arrow.forward.circle")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
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

    @ViewBuilder private var statusIcon: some View {
        switch assessment.state {
        case .configured: Image(systemName: "checkmark.shield.fill")
        case .needsSetup: Image(systemName: "exclamationmark.shield.fill")
        case .unchecked: Image(systemName: "questionmark.circle")
        case .checking: Image(systemName: "arrow.clockwise")
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
