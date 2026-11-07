import SwiftUI

/// Everything that is not a daily decision: the checks behind the protection,
/// the locks that keep it, and the app's settings. Today links here with one
/// tap when a layer needs attention.
struct MoreView: View {
    @ObservedObject var protection: ProtectionController
    @ObservedObject var usage: AppUsageController
    @ObservedObject var vault: PasscodeVaultController
    let lockDevice: LockConfirmationDevice
    @Binding var language: String

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var showingSites = false
    @State private var showingNotices = false

    private var assessment: MobileProtectionPolicy.SetupAssessment { protection.setupAssessment }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HnIntro(title: "more.title", subtitle: "more.subtitle")
                protectionCard
                HnSectionLabel("more.hold.title")
                holdCard
                HnSectionLabel("more.settings.title")
                settingsCard
                aboutCard
                footer
            }
            .padding(.horizontal, 18)
            .padding(.top, 6)
            .padding(.bottom, 104)
            .hnColumn(regular: horizontalSizeClass == .regular)
        }
        .background(HnPalette.canvas)
        .sheet(isPresented: $showingSites) {
            NavigationStack {
                List { CustomSitesSection(protection: protection) }
                    .listStyle(.insetGrouped)
                    .navigationTitle("sites.title")
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) {
                            Button("notices.close") { showingSites = false }
                        }
                    }
            }
        }
        .sheet(isPresented: $showingNotices) { NoticesView() }
    }

    // MARK: - Protection

    private var protectionCard: some View {
        NavigationLink {
            ProtectionDetailView(protection: protection, usage: usage, vault: vault,
                                 lockDevice: lockDevice)
        } label: {
            HnCard {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(alignment: .top, spacing: 14) {
                        HnRing(value: Double(protection.configuredContentLayers) / 3,
                               size: 58, lineWidth: 6,
                               tint: protection.configuredContentLayers == 3
                                   ? HnPalette.calm : HnPalette.accent,
                               done: protection.configuredContentLayers, total: 3)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("more.protection.title")
                                .font(.hnHeadline())
                                .foregroundStyle(HnPalette.ink)
                            Text(LocalizedStringKey(assessment.state.rawValue))
                                .font(.footnote)
                                .foregroundStyle(assessment.state == .configured
                                                 ? HnPalette.calm : HnPalette.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 8)
                        Image(systemName: "chevron.forward")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(HnPalette.muted.opacity(0.7))
                            .accessibilityHidden(true)
                    }
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: 8) { levelChips }
                        VStack(alignment: .leading, spacing: 6) { levelChips }
                    }
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("more.protectionRow")
    }

    @ViewBuilder private var levelChips: some View {
        chip("safari", title: "layer.safari", ready: protection.safariConfigured)
        chip("network", title: "layer.dns", ready: protection.dns == .enabled)
        chip("hourglass", title: "layer.screenTime", ready: protection.screenTimeConfigured)
    }

    private func chip(_ icon: String, title: LocalizedStringKey, ready: Bool) -> some View {
        HStack(spacing: 4) {
            Image(systemName: ready ? "checkmark" : "minus")
                .font(.system(size: 9, weight: .heavy))
                .accessibilityHidden(true)
            Text(title)
                .font(.caption2.weight(.semibold))
        }
        .foregroundStyle(ready ? HnPalette.calm : HnPalette.muted)
        .padding(.horizontal, 9)
        .padding(.vertical, 5)
        .background(HnPalette.soft, in: Capsule())
        .accessibilityElement(children: .combine)
        .accessibilityValue(Text(ready ? "plan.configured" : "plan.needsSetup"))
    }

    // MARK: - Hold it in place

    private var holdCard: some View {
        HnCard {
            VStack(alignment: .leading, spacing: 16) {
                HnNavigationRow(systemImage: "checkmark.shield",
                                title: "guide.layers",
                                detail: "more.layers.detail") {
                    BlockingLayersSetupView(protection: protection)
                }
                Divider().overlay(HnPalette.line)
                HnNavigationRow(systemImage: "lock",
                                title: "guide.commitment",
                                detail: "more.commitment.detail",
                                tint: HnPalette.calm) {
                    CommitmentSetupView(protection: protection, usage: usage, vault: vault)
                }
                Divider().overlay(HnPalette.line)
                HnNavigationRow(systemImage: "key",
                                title: "guide.vault",
                                detail: "more.vault.detail",
                                tint: HnPalette.ember) {
                    VaultSetupView(protection: protection, vault: vault, lockDevice: lockDevice)
                }
                Divider().overlay(HnPalette.line)
                HnLinkRow(systemImage: "safari",
                          title: "sites.title",
                          detail: "more.sites.detail") {
                    showingSites = true
                }
            }
        }
    }

    // MARK: - Settings

    private var settingsCard: some View {
        HnCard {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 14) {
                    Image(systemName: "globe")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(HnPalette.accent)
                        .frame(width: 38, height: 38)
                        .background(HnPalette.accentSoft, in: Circle())
                        .accessibilityHidden(true)
                    Text("language.title")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(HnPalette.ink)
                    Spacer(minLength: 8)
                    Picker("language.title", selection: $language) {
                        Text("language.system").tag("system")
                        Text(verbatim: "العربية").tag("ar")
                        Text(verbatim: "English").tag("en")
                    }
                    .labelsHidden()
                    .pickerStyle(.menu)
                }
                Divider().overlay(HnPalette.line)
                disclosure(title: "gray.title", systemImage: "circle.lefthalf.filled") {
                    Text("settings.gray.summary")
                    Text("gray.steps")
                    Text("gray.body")
                    Text("gray.shortcut")
                }
                Divider().overlay(HnPalette.line)
                disclosure(title: "privacy.title", systemImage: "hand.raised") {
                    Text("settings.privacy.summary")
                    Text("privacy.body")
                }
                Divider().overlay(HnPalette.line)
                HnLinkRow(systemImage: "doc.text", title: "privacy.policy",
                          trailing: nil, tint: HnPalette.muted) {
                    open(URL(string: "https://github.com/hamzabinjehad/AdultContentBlocker/blob/main/PRIVACY.md")!)
                }
                Divider().overlay(HnPalette.line)
                HnLinkRow(systemImage: "questionmark.circle", title: "support.page",
                          tint: HnPalette.muted) {
                    open(URL(string: "https://github.com/hamzabinjehad/AdultContentBlocker/blob/main/SUPPORT.md")!)
                }
                Divider().overlay(HnPalette.line)
                HnLinkRow(systemImage: "doc.on.doc", title: "notices.open",
                          tint: HnPalette.muted) {
                    showingNotices = true
                }
            }
        }
    }

    private func disclosure<Content: View>(title: LocalizedStringKey, systemImage: String,
                                           @ViewBuilder content: @escaping () -> Content) -> some View {
        DisclosureGroup {
            VStack(alignment: .leading, spacing: 8) {
                content()
            }
            .font(.footnote)
            .foregroundStyle(HnPalette.muted)
            .padding(.top, 8)
        } label: {
            HStack(spacing: 14) {
                Image(systemName: systemImage)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(HnPalette.muted)
                    .frame(width: 38, height: 38)
                    .background(HnPalette.soft, in: Circle())
                    .accessibilityHidden(true)
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(HnPalette.ink)
            }
        }
        .tint(HnPalette.muted)
    }

    private func open(_ url: URL) {
        UIApplication.shared.open(url)
    }

    // MARK: - About

    private var aboutCard: some View {
        HnCard {
            HStack(spacing: 14) {
                HnMark(size: 40)
                VStack(alignment: .leading, spacing: 3) {
                    Text("app.name")
                        .font(.hnHeadline())
                        .foregroundStyle(HnPalette.ink)
                    Text("settings.about.tagline")
                        .font(.footnote)
                        .foregroundStyle(HnPalette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 8)
                Text(verbatim: appVersion)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(HnPalette.muted)
            }
        }
    }

    private var appVersion: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(version) (\(build))"
    }

    private var footer: some View {
        Text("more.footer")
            .font(.caption)
            .foregroundStyle(HnPalette.muted)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 4)
    }
}

/// The evidence behind the hero: what is on, what stopped, and the exact checks.
/// Nothing here is claimed as proof of complete blocking.
struct ProtectionDetailView: View {
    @ObservedObject var protection: ProtectionController
    @ObservedObject var usage: AppUsageController
    @ObservedObject var vault: PasscodeVaultController
    let lockDevice: LockConfirmationDevice

    @AppStorage(MobileProtectionPolicy.lockAttestationKey) private var lockAttestationData = Data()
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    private var removal: MobileProtectionPolicy.RemovalProtection {
        MobileProtectionPolicy.removalProtection(
            removal: protection.removal,
            attestation: MobileProtectionPolicy.LockAttestation(storage: lockAttestationData),
            vault: vault.facts, now: protection.now, deviceMarker: lockDevice.marker)
    }

    private var doneSteps: Int {
        SetupGuide.Step.allCases.filter { setupStatus($0) == .done }.count
    }

    private func setupStatus(_ step: SetupGuide.Step) -> SetupGuide.Status {
        SetupGuide.status(of: step,
                          hasBlockingLayer: protection.hasBlockingConfiguration,
                          commitmentActive: protection.commitmentActive,
                          vaultHoldsPasscode: vault.facts?.holdsPasscode(at: protection.now) == true)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                SetupStatusCard(protection: protection, doneSteps: doneSteps,
                                totalSteps: SetupGuide.Step.allCases.count)
                ProtectionPlanCard(protection: protection, removal: removal,
                                   onSetup: { })
                ProtectionWarningsCard(protection: protection, usage: usage, onSetup: { })
                checksCard
                Text("setup.checks.footnote")
                    .font(.footnote)
                    .foregroundStyle(HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 4)
                if let error = protection.errorKey {
                    Label(LocalizedStringKey(error), systemImage: "exclamationmark.triangle")
                        .font(.footnote)
                        .foregroundStyle(HnPalette.ember)
                }
            }
            .padding(.horizontal, 18)
            .padding(.top, 8)
            .padding(.bottom, 60)
            .hnColumn(regular: horizontalSizeClass == .regular)
        }
        .background(HnPalette.canvas)
        .navigationTitle("more.protection.title")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await protection.refresh() }
    }

    private var checksCard: some View {
        HnCard {
            VStack(alignment: .leading, spacing: 0) {
                Text("setup.checks.title")
                    .font(.hnHeadline())
                    .foregroundStyle(HnPalette.ink)
                    .padding(.bottom, 4)
                checkRow("safari.title", ready: protection.safariConfigured,
                         detail: Text("plan.safariParts \(protection.safari.filter { $0 == true }.count)"))
                Divider().overlay(HnPalette.line)
                checkRow("dns.title", ready: protection.dns == .enabled, detail: Text(dnsLabel))
                Divider().overlay(HnPalette.line)
                checkRow("screentime.layer", ready: protection.screenTimeConfigured,
                         detail: Text(screenTimeCheckDetail))
                Divider().overlay(HnPalette.line)
                lockRow
            }
        }
    }

    private func checkRow(_ title: LocalizedStringKey, ready: Bool, detail: Text? = nil) -> some View {
        HisnAdaptiveRow(title: title, detail: detail) {
            HisnStatusPill(text: ready ? "plan.configured" : "plan.needsSetup",
                           systemImage: ready ? "checkmark" : "circle",
                           tint: ready ? HnPalette.calm : .secondary)
        }
        .padding(.vertical, 12)
    }

    private var lockRow: some View {
        let lock = removal
        return HisnAdaptiveRow(
            title: "lock.title",
            detail: lock.selfTested
                ? Text(removalLevel) + Text(verbatim: "\n") + Text("lock.selfTested")
                : Text(removalLevel)
        ) {
            switch lock.evidence {
            case .verified?:
                HisnStatusPill(text: "lock.evidence.verified", systemImage: "checkmark.seal",
                               tint: HnPalette.calm)
            case .attested?:
                HisnStatusPill(text: "lock.evidence.attested", systemImage: "person.fill.checkmark",
                               tint: .secondary)
            case nil:
                HisnStatusPill(text: "lock.evidence.none", systemImage: "lock.open", tint: .secondary)
            }
        }
        .padding(.vertical, 12)
        .accessibilityIdentifier("plan.removalProtection")
    }

    private var removalLevel: LocalizedStringKey {
        switch removal.level {
        case .selfOnly: return "overview.lock.level.selfOnly"
        case .passcodeVault: return "overview.lock.level.vault"
        case .familyGuardian: return "overview.lock.level.guardian"
        case .supervised: return "overview.lock.level.supervised"
        }
    }

    private var dnsLabel: LocalizedStringKey {
        switch protection.dns {
        case .unknown: return "status.unknown"
        case .absent: return "dns.absent"
        case .saved: return "dns.saved"
        case .enabled: return "dns.enabled"
        case .differentConfiguration: return "dns.different"
        case .unavailable: return "dns.unavailable"
        }
    }

    private var screenTimeCheckDetail: LocalizedStringKey {
        switch protection.screenTimeState {
        case .unavailable: return "overview.screentimeUnavailable"
        default: return LocalizedStringKey(protection.screenTimeState.rawValue)
        }
    }
}

/// Setup's order: blocking layers, then the commitment that locks them, then the
/// passcode vault. Each raw value is the step's text key.
enum SetupGuide {
    enum Step: String, CaseIterable {
        case layers = "guide.layers"
        case commitment = "guide.commitment"
        case vault = "guide.vault"
    }
    enum Status: Equatable { case done, next, later }

    /// The first step not done is next. A commitment running with no layer on sends the
    /// person back to step 1 rather than reading as progress.
    static func status(of step: Step, hasBlockingLayer: Bool, commitmentActive: Bool,
                       vaultHoldsPasscode: Bool) -> Status {
        func done(_ step: Step) -> Bool {
            switch step {
            case .layers: return hasBlockingLayer
            case .commitment: return commitmentActive
            case .vault: return vaultHoldsPasscode
            }
        }
        if done(step) { return .done }
        return Step.allCases.first { !done($0) } == step ? .next : .later
    }
}
