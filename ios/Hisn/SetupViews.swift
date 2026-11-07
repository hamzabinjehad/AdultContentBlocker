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
    @State private var showingIssues = false

    private var assessment: MobileProtectionPolicy.SetupAssessment { protection.setupAssessment }

    var body: some View {
        HisnCard(tint: cardTint) {
            VStack(alignment: .leading, spacing: 14) {
                statusHeader

                progressRow

                if !assessment.issues.isEmpty { issueDisclosure }
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
                        Text("setup.next.check")
                        Text("setup.status.scope")
                        Text("plan.scope")
                        Text("setup.status.mixedcontent")
                    }
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(.top, 6)
                } label: {
                    Text("setup.checks.what")
                        .font(.footnote.weight(.medium))
                }
            }
            .accessibilityIdentifier("setup.currentAssessment")
        }
    }

    /// The issues are useful but long. The card shows how many there are; the
    /// sentences are one tap away, just as they are on the overview.
    private var issueDisclosure: some View {
        DisclosureGroup(isExpanded: $showingIssues) {
            VStack(alignment: .leading, spacing: 8) {
                ForEach(assessment.issues, id: \.rawValue) { issue in
                    Label(LocalizedStringKey(issue.rawValue), systemImage: "arrow.forward.circle")
                        .font(.callout)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(.top, 6)
        } label: {
            Group {
                if assessment.issues.count == 1 {
                    Text("hero.issuesOne")
                } else {
                    Text("hero.issues \(assessment.issues.count)")
                }
            }
            .font(.callout.weight(.medium))
            .foregroundStyle(HisnPalette.warning)
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
    @State private var confirmDNSOff = false

    var body: some View {
        List {
            Section("safari.title") {
                Text("safari.steps.short")
                Button("safari.reload") { Task { await protection.reloadSafari() } }
                    .disabled(protection.busy || protection.listCount == 0)
                DisclosureGroup("safari.details") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("safari.steps")
                        Text("safari.scope")
                    }
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(.top, 6)
                }
                .font(.footnote)
            }
            SafariPartsSection(protection: protection)
            ScreenTimeSection(protection: protection)
            CustomSitesSection(protection: protection)
            BrowserFirstSection()
            Section("dns.title") {
                Text("dns.stepsShort")
                if protection.dns == .enabled {
                    Button("dns.turnOff", role: .destructive) { confirmDNSOff = true }
                        .disabled(protection.busy || protection.rulesLocked)
                    if protection.rulesLocked {
                        Text("dns.locked")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    Button("dns.check") { Task { await protection.checkDNSRoute() } }
                        .disabled(protection.busy || protection.dnsRouteCheck == .running)
                    dnsRouteResult
                } else {
                    Button("dns.prepare") { confirmDNS = true }.disabled(protection.busy)
                }
                DisclosureGroup("dns.details") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("dns.steps")
                        Text("dns.scope")
                    }
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(.top, 6)
                }
                .font(.footnote)
                Link("dns.provider", destination: URL(string: "https://developers.cloudflare.com/1.1.1.1/setup/")!)
            }
            Section("family.title") {
                Text("family.steps")
                DisclosureGroup("family.details") {
                    Text("family.scope")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .padding(.top, 6)
                }
                .font(.footnote)
            }
            Section("network.title") {
                Text("network.stepsShort")
                DisclosureGroup("network.details") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("network.steps")
                        Text("network.scope")
                    }
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(.top, 6)
                }
                .font(.footnote)
                DisclosureGroup("network.routerRules") {
                    RouterAppDomainsView()
                        .padding(.top, 6)
                }
                .font(.footnote)
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
        .confirmationDialog("dns.offConfirm.title", isPresented: $confirmDNSOff,
                            titleVisibility: .visible) {
            Button("dns.turnOff", role: .destructive) { Task { await protection.disableDNS() } }
            Button("cancel", role: .cancel) { }
        } message: {
            Text("dns.offConfirm.body")
        }
    }

    @ViewBuilder private var dnsRouteResult: some View {
        switch protection.dnsRouteCheck {
        case .running:
            ProgressView("dns.check.running")
        case .passed:
            Label("dns.check.passed", systemImage: "checkmark.circle")
                .foregroundStyle(HisnPalette.good)
            Text("dns.check.scope")
                .font(.footnote)
                .foregroundStyle(.secondary)
        case .failed:
            Label("dns.check.failed", systemImage: "exclamationmark.triangle")
                .foregroundStyle(HisnPalette.warning)
            Text("dns.check.scope")
                .font(.footnote)
                .foregroundStyle(.secondary)
        case .unchecked, .unavailable:
            EmptyView()
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
            CommitmentSection(protection: protection, vault: vault)
        }
        .listStyle(.insetGrouped)
        .environment(\.defaultMinListRowHeight, 48)
        .background(HisnSurface.grouped)
        .navigationTitle("setup.detail.commitment.title")
    }
}

/// Step 3: the passcode vault and the removal ladder. Their dates are together
/// on purpose: the person must see which one opens first.
struct VaultSetupView: View {
    @ObservedObject var protection: ProtectionController
    @ObservedObject var vault: PasscodeVaultController
    let lockDevice: LockConfirmationDevice

    var body: some View {
        List {
            PasscodeVaultSection(vault: vault, protection: protection, lockDevice: lockDevice)
            LockCenterView(protection: protection, vault: vault, lockDevice: lockDevice)
        }
        .listStyle(.insetGrouped)
        .environment(\.defaultMinListRowHeight, 48)
        .background(HisnSurface.grouped)
        .navigationTitle("vault.title")
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

/// The removal-protection ladder as its own object: the current level first,
/// then one row per rung with what it stops, its exit and one action. Apple
/// gives an app no way to make itself undeletable on an unsupervised iPhone;
/// this ladder is every supported level, and each rung states its own exit.
/// The guardian request and the person's own confirmations live here; the
/// passcode-vault rung refers to the vault section above it.
struct LockCenterView: View {
    @ObservedObject var protection: ProtectionController
    @ObservedObject var vault: PasscodeVaultController
    let lockDevice: LockConfirmationDevice
    @State private var confirmGuardian = false
    @AppStorage(MobileProtectionPolicy.lockAttestationKey) private var lockAttestationData = Data()

    /// The readiness row's own assessment, computed the same way the overview
    /// computes it: attested levels are never shown as verified.
    private var removal: MobileProtectionPolicy.RemovalProtection {
        MobileProtectionPolicy.removalProtection(removal: protection.removal,
            attestation: MobileProtectionPolicy.LockAttestation(storage: lockAttestationData),
            vault: vault.facts, now: protection.now, deviceMarker: lockDevice.marker)
    }

    var body: some View {
        Section("lock.center.title") {
            levelRow
            if let stale = removal.stale {
                Text(LocalizedStringKey(stale.rawValue))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Text("lock.center.intro")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        Section("lock.center.ladder") {
            vaultRung
            guardianRung
            supervisedRung
        }
        Section {
            DisclosureGroup("lock.confirmOther") {
                Text("lock.ladder").font(.footnote).foregroundStyle(.secondary)
                LockConfirmationRows(claims: [.familyGuardian, .supervised], vault: vault.facts,
                                     now: protection.now, device: lockDevice)
            }
            DisclosureGroup("removal.details") {
                VStack(alignment: .leading, spacing: 8) {
                    Text("removal.body")
                    Text("removal.manual")
                    Text("removal.recovery")
                }
                .font(.footnote)
                .foregroundStyle(.secondary)
                .padding(.top, 6)
            }
            .font(.footnote)
        } footer: {
            Text("lock.center.honesty")
        }
        .alert("removal.consent.title", isPresented: $confirmGuardian) {
            Button("removal.request") { Task { await protection.requestGuardianProtection() } }
            Button("cancel", role: .cancel) { }
        } message: {
            Text("removal.consent.body")
        }
    }

    // MARK: - Current level

    /// The short level name the overview rows already use. Hisn cannot read the
    /// Screen Time passcode, the deletion setting or supervision, so the row
    /// carries the evidence beside it, never inside the name.
    private var levelName: LocalizedStringKey {
        switch removal.level {
        case .selfOnly: return "overview.lock.level.selfOnly"
        case .passcodeVault: return "overview.lock.level.vault"
        case .familyGuardian: return "overview.lock.level.guardian"
        case .supervised: return "overview.lock.level.supervised"
        }
    }

    private var levelRow: some View {
        HStack(alignment: .top, spacing: 12) {
            if removal.level == .selfOnly {
                HisnIconBadge(systemName: "lock.open", tint: Color.secondary, size: 38)
                    .accessibilityHidden(true)
            } else {
                HisnIconBadge(systemName: "lock.shield.fill", tint: HisnPalette.good, size: 38)
                    .accessibilityHidden(true)
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(levelName)
                    .font(.headline)
                if removal.selfTested {
                    Text("lock.selfTested").font(.caption).foregroundStyle(.secondary)
                }
                switch removal.evidence {
                case .verified?:
                    HisnStatusPill(text: "lock.evidence.verified", systemImage: "checkmark.seal",
                                   tint: HisnPalette.good)
                case .attested?:
                    HisnStatusPill(text: "lock.evidence.attested", systemImage: "person.fill.checkmark",
                                   tint: .secondary)
                case nil:
                    HisnStatusPill(text: "lock.evidence.none", systemImage: "lock.open", tint: .secondary)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }

    // MARK: - Rungs

    /// One rung: what this level stops, the exit it keeps, and one action.
    private func rungRow(systemName: String, stopsKey: LocalizedStringKey, exitKey: LocalizedStringKey,
                         @ViewBuilder action: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 12) {
                HisnIconBadge(systemName: systemName, tint: Color.accentColor, size: 34)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 3) {
                    Text(stopsKey)
                        .font(.callout)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(exitKey)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            action()
        }
        .padding(.vertical, 6)
    }

    private var vaultRung: some View {
        rungRow(systemName: "lock.rotation",
                stopsKey: "lock.rung.vault.stops",
                exitKey: "lock.rung.vault.exit") {
            Text("lock.rung.vault.how")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var guardianRung: some View {
        rungRow(systemName: "person.2",
                stopsKey: "lock.rung.guardian.stops",
                exitKey: "lock.rung.guardian.exit") {
            Label(LocalizedStringKey(protection.removal.rawValue), systemImage: "lock.shield")
                .font(.caption)
                .foregroundStyle(.secondary)
            Button("removal.request") { confirmGuardian = true }.disabled(protection.busy)
        }
    }

    private var supervisedRung: some View {
        rungRow(systemName: "crown",
                stopsKey: "lock.rung.supervised.stops",
                exitKey: "lock.rung.supervised.exit") {
            DisclosureGroup("lock.rung.supervised.how") {
                VStack(alignment: .leading, spacing: 8) {
                    Text("removal.setup.mobile")
                    Link("removal.guide", destination: URL(string: "https://support.apple.com/en-gb/105121")!)
                }
                .font(.footnote)
                .foregroundStyle(.secondary)
                .padding(.top, 6)
            }
            .font(.callout)
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
