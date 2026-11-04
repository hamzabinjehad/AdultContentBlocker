import SwiftUI

// MARK: - Setup overview

/// One of the three setup steps. The card is the tap target; the detailed
/// controls live one level down, so the main Setup page stays a short plan
/// rather than a form with every switch at once.
struct SetupStepCard: View {
    let step: SetupGuide.Step
    let status: SetupGuide.Status
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        HisnCard {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(alignment: .top, spacing: 12) {
                        stepBadge
                        stepText
                    }
                    HStack(spacing: 10) {
                        statusPill
                        Spacer(minLength: 0)
                        chevron
                    }
                }
            } else {
                HStack(alignment: .top, spacing: 14) {
                    stepBadge
                    stepText
                    Spacer(minLength: 8)
                    VStack(alignment: .trailing, spacing: 8) {
                        statusPill
                        chevron
                    }
                }
            }
        }
    }

    private var stepText: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(step.titleKey)
                .font(.headline)
                .fixedSize(horizontal: false, vertical: true)
            Text(step.detailKey)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var chevron: some View {
        Image(systemName: "chevron.forward")
            .font(.caption.weight(.semibold))
            .foregroundStyle(.tertiary)
            .accessibilityHidden(true)
    }

    private var stepBadge: some View {
        Group {
            if status == .done {
                HisnIconBadge(systemName: "checkmark", tint: HisnPalette.good, size: 42)
            } else {
                Text(verbatim: "\(step.number)")
                    .font(.headline.monospacedDigit())
                    .foregroundStyle(status == .next ? Color.accentColor : Color.secondary)
                    .frame(width: 42, height: 42)
                    .background(
                        (status == .next ? Color.accentColor : Color.secondary).opacity(0.12),
                        in: Circle()
                    )
            }
        }
        .accessibilityHidden(true)
    }

    @ViewBuilder private var statusPill: some View {
        switch status {
        case .done:
            HisnStatusPill(text: "guide.done", systemImage: "checkmark", tint: HisnPalette.good)
        case .next:
            HisnStatusPill(text: "guide.next", systemImage: "arrow.forward", tint: .accentColor)
        case .later:
            HisnStatusPill(text: "guide.later", systemImage: nil, tint: .secondary)
        }
    }
}

/// The Setup page's first card: the verified state, progress, issues, and the
/// one refresh action. The long scope text is available behind a disclosure.
struct SetupStatusCard: View {
    @ObservedObject var protection: ProtectionController
    let doneSteps: Int
    let totalSteps: Int

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var showingScope = false

    private var assessment: MobileProtectionPolicy.SetupAssessment { protection.setupAssessment }

    var body: some View {
        HisnCard(tint: cardTint) {
            VStack(alignment: .leading, spacing: 14) {
                statusHeader

                progressRow

                if assessment.state == .unchecked {
                    Text("setup.next.check")
                        .font(.callout)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if !assessment.issues.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(assessment.issues, id: \.rawValue) { issue in
                            Label(LocalizedStringKey(issue.rawValue), systemImage: "arrow.forward.circle")
                                .font(.callout)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                if assessment.state == .checking { ProgressView("working") }

                HStack(spacing: 12) {
                    if let date = protection.checkedAt {
                        HStack(spacing: 4) {
                            Text("status.checked")
                            Text(date, style: .time).monospacedDigit()
                        }
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 8)
                    Button("status.refresh") { Task { await protection.refresh() } }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                        .disabled(protection.busy)
                }

                DisclosureGroup(isExpanded: $showingScope) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("setup.status.scope").font(.footnote).foregroundStyle(.secondary)
                        Text("setup.status.mixedcontent").font(.footnote).foregroundStyle(.secondary)
                    }
                    .padding(.top, 6)
                } label: {
                    Text("setup.checks.what")
                        .font(.footnote.weight(.medium))
                }
            }
            .accessibilityIdentifier("setup.currentAssessment")
        }
    }

    @ViewBuilder private var progressRow: some View {
        let bar = HisnProgressBar(value: Double(doneSteps) / Double(totalSteps), tint: tint)
        let count = Text("setup.progress \(doneSteps) \(totalSteps)")
            .font(.caption.weight(.medium))
            .foregroundStyle(.secondary)
            .monospacedDigit()
        if dynamicTypeSize.isAccessibilitySize {
            VStack(alignment: .leading, spacing: 6) {
                bar
                count
            }
            .accessibilityElement(children: .combine)
        } else {
            HStack(spacing: 10) {
                bar
                count
            }
            .accessibilityElement(children: .combine)
        }
    }

    @ViewBuilder private var statusHeader: some View {
        if dynamicTypeSize.isAccessibilitySize {
            VStack(alignment: .leading, spacing: 12) {
                statusBadge
                statusLabel
            }
        } else {
            HStack(alignment: .top, spacing: 14) {
                statusBadge
                statusLabel
                Spacer(minLength: 0)
            }
        }
    }

    private var statusLabel: some View {
        Text(LocalizedStringKey(assessment.state.rawValue))
            .font(.title3.bold())
            .foregroundStyle(tint)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityAddTraits(.isHeader)
    }

    @ViewBuilder private var statusBadge: some View {
        switch assessment.state {
        case .configured:
            HisnIconBadge(systemName: "checkmark.shield.fill", tint: tint, size: 54)
        case .needsSetup:
            HisnIconBadge(systemName: "exclamationmark.shield.fill", tint: tint, size: 54)
        case .unchecked, .checking:
            HisnIconBadge(systemName: "arrow.clockwise", tint: tint, size: 54)
        }
    }

    private var tint: Color {
        switch assessment.state {
        case .configured: return HisnPalette.good
        case .needsSetup: return HisnPalette.warning
        case .unchecked, .checking: return .accentColor
        }
    }

    private var cardTint: Color { tint }
}

// MARK: - Setup details

/// Step 1: every layer that blocks a request or an app. The section order is
/// the order the person meets them: Safari, Screen Time, their own sites, DNS,
/// then the optional family, router and Safari-extension layers.
struct BlockingLayersSetupView: View {
    @ObservedObject var protection: ProtectionController
    @State private var confirmDNS = false

    var body: some View {
        List {
            Section("safari.title") {
                Text("safari.steps")
                Button("safari.reload") { Task { await protection.reloadSafari() } }
                    .disabled(protection.busy || protection.listCount == 0)
                Text("safari.scope").font(.footnote).foregroundStyle(.secondary)
            }
            SafariPartsSection(protection: protection)
            ScreenTimeSection(protection: protection)
            CustomSitesSection(protection: protection)
            BrowserFirstSection()
            Section("dns.title") {
                Text("dns.steps")
                Button("dns.prepare") { confirmDNS = true }.disabled(protection.busy)
                Text("dns.scope").font(.footnote).foregroundStyle(.secondary)
                Link("dns.provider", destination: URL(string: "https://developers.cloudflare.com/1.1.1.1/setup/")!)
            }
            Section("family.title") {
                Text("family.steps")
                Text("family.scope").font(.footnote).foregroundStyle(.secondary)
            }
            Section("network.title") {
                RouterAppDomainsView()
                Text("network.steps")
                Text("network.scope").font(.footnote).foregroundStyle(.secondary)
                Link("network.guide", destination: URL(string: "https://developers.cloudflare.com/1.1.1.1/setup/router/")!)
            }
        }
        .listStyle(.insetGrouped)
        .environment(\.defaultMinListRowHeight, 48)
        .background(HisnSurface.grouped)
        .navigationTitle("setup.detail.layers.title")
        .alert("dns.consent.title", isPresented: $confirmDNS) {
            Button("dns.prepare") { Task { await protection.installDNS() } }
            Button("cancel", role: .cancel) { }
        } message: {
            Text("dns.consent.body")
        }
    }
}

/// Step 2: the rules a commitment freezes, then the commitment itself.
struct CommitmentSetupView: View {
    @ObservedObject var protection: ProtectionController
    @ObservedObject var usage: AppUsageController
    @ObservedObject var vault: PasscodeVaultController

    var body: some View {
        List {
            AppUsageSection(protection: protection, usage: usage)
            CommitmentSection(protection: protection, vault: vault)
        }
        .listStyle(.insetGrouped)
        .environment(\.defaultMinListRowHeight, 48)
        .background(HisnSurface.grouped)
        .navigationTitle("setup.detail.commitment.title")
    }
}

/// Step 3: the passcode vault and the stronger removal locks. Their dates are
/// together on purpose: the person must see which one opens first.
struct VaultSetupView: View {
    @ObservedObject var protection: ProtectionController
    @ObservedObject var vault: PasscodeVaultController
    let lockDevice: LockConfirmationDevice

    var body: some View {
        List {
            PasscodeVaultSection(vault: vault, protection: protection, lockDevice: lockDevice)
            RemovalProtectionSection(protection: protection, vault: vault, lockDevice: lockDevice)
        }
        .listStyle(.insetGrouped)
        .environment(\.defaultMinListRowHeight, 48)
        .background(HisnSurface.grouped)
        .navigationTitle("setup.detail.vault.title")
    }
}

// MARK: - Reused sections

/// The six Safari parts, kept behind a disclosure. Each part is still checked
/// and named separately; this is evidence, not a second setup step.
struct SafariPartsSection: View {
    @ObservedObject var protection: ProtectionController

    var body: some View {
        Section("safari.parts") {
            DisclosureGroup("safari.parts.details") {
                ForEach(0..<MobileProtectionPolicy.blockerIdentifiers.count, id: \.self) { index in
                    HStack {
                        Text(verbatim: "Hisn \(index + 1)")
                        Spacer()
                        let state = MobileProtectionPolicy.safariPartState(enabled: protection.safari[index],
                            lastReload: protection.safariReloads[index])
                        switch state {
                        case .enabled: Label("part.enabled", systemImage: "checkmark.circle")
                        case .disabled: Label("part.disabled", systemImage: "circle")
                        case .unknown: Label("status.unknown", systemImage: "questionmark.circle")
                        case .reloadFailed: Label("part.failed", systemImage: "exclamationmark.triangle")
                        }
                    }
                    .font(.subheadline)
                    .accessibilityElement(children: .combine)
                }
            }
        }
    }
}

/// Guardian authorization and the person's own confirmation of locks Hisn
/// cannot read. Self-contained so Setup's step 3 owns its alert.
struct RemovalProtectionSection: View {
    @ObservedObject var protection: ProtectionController
    @ObservedObject var vault: PasscodeVaultController
    let lockDevice: LockConfirmationDevice
    @State private var confirmGuardian = false

    var body: some View {
        Section("removal.title") {
            Label(LocalizedStringKey(protection.removal.rawValue), systemImage: "lock.shield")
            Text("removal.body").font(.footnote).foregroundStyle(.secondary)
            Button("removal.request") { confirmGuardian = true }.disabled(protection.busy)
            Text("removal.manual").font(.footnote).foregroundStyle(.secondary)
            Text("removal.recovery").font(.footnote).foregroundStyle(.secondary)
            DisclosureGroup("removal.setup.title") {
                Text("removal.setup.mobile").font(.callout).foregroundStyle(.secondary)
                Button("status.refresh") { Task { await protection.refresh() } }
                    .disabled(protection.busy)
            }
            DisclosureGroup("lock.confirmOther") {
                Text("lock.ladder").font(.footnote).foregroundStyle(.secondary)
                LockConfirmationRows(claims: [.familyGuardian, .supervised], vault: vault.facts,
                                     now: protection.now, device: lockDevice)
            }
            Link("removal.guide", destination: URL(string: "https://support.apple.com/en-gb/105121")!)
        }
        .alert("removal.consent.title", isPresented: $confirmGuardian) {
            Button("removal.request") { Task { await protection.requestGuardianProtection() } }
            Button("cancel", role: .cancel) { }
        } message: {
            Text("removal.consent.body")
        }
    }
}

// MARK: - Step copy and state

extension SetupGuide.Step {
    var number: Int {
        switch self {
        case .layers: return 1
        case .commitment: return 2
        case .vault: return 3
        }
    }

    var titleKey: LocalizedStringKey {
        switch self {
        case .layers: return "setup.step.layers"
        case .commitment: return "setup.step.commitment"
        case .vault: return "setup.step.vault"
        }
    }

    var detailKey: LocalizedStringKey {
        switch self {
        case .layers: return "setup.step.layers.detail"
        case .commitment: return "setup.step.commitment.detail"
        case .vault: return "setup.step.vault.detail"
        }
    }
}
