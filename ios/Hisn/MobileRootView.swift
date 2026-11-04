import Security
import SwiftUI

struct MobileRootView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ObservedObject var protection: ProtectionController
    @Binding var language: String
    @State private var selectedTab = 0
    @State private var showingNotices = false
    @State private var showingPause = false
    @State private var showingLock = false
    @State private var showingSites = false
    @State private var showingAppLimits = false
    @State private var pauseCount: Int?
    @StateObject private var usage = AppUsageController()
    @StateObject private var vault: PasscodeVaultController
    /// The person's own confirmation of a lock Hisn cannot read; empty means none.
    @AppStorage(MobileProtectionPolicy.lockAttestationKey) private var lockAttestationData = Data()
    /// The marker that confirmation must carry to count on this install on this iPhone.
    @StateObject private var lockDevice: LockConfirmationDevice

    init(protection: ProtectionController, language: Binding<String>, initialTab: Int = 0,
         vault: PasscodeVaultController? = nil, lockDevice: LockConfirmationDevice? = nil) {
        self.protection = protection
        self._language = language
        self._selectedTab = State(initialValue: (0...2).contains(initialTab) ? initialTab : 0)
        self._vault = StateObject(wrappedValue: vault ?? PasscodeVaultController())
        self._lockDevice = StateObject(wrappedValue: lockDevice ?? LockConfirmationDevice())
    }

    var body: some View {
        Group {
            if horizontalSizeClass == .regular {
                NavigationSplitView {
                    List(selection: Binding<Int?>(get: { selectedTab },
                                                  set: { if let value = $0 { selectedTab = value } })) {
                        Label("tab.protection", systemImage: "shield.lefthalf.filled").tag(0)
                        Label("tab.setup", systemImage: "checklist").tag(1)
                        Label("tab.settings", systemImage: "gearshape").tag(2)
                    }
                    .navigationTitle("app.name")
                } detail: {
                    NavigationStack {
                        tabContent.navigationTitle(pageTitle)
                    }
                }
            } else {
                TabView(selection: $selectedTab) {
                    NavigationStack { overview.navigationTitle("app.name") }
                        .tabItem { Label("tab.protection", systemImage: "shield.lefthalf.filled") }
                        .tag(0)
                    NavigationStack { setup.navigationTitle("tab.setup") }
                        .tabItem { Label("tab.setup", systemImage: "checklist") }
                        .tag(1)
                    NavigationStack { settings.navigationTitle("tab.settings") }
                        .tabItem { Label("tab.settings", systemImage: "gearshape") }
                        .tag(2)
                }
            }
        }
        .fullScreenCover(isPresented: $showingPause) {
            PauseView(onClose: { showingPause = false })
        }
        .sheet(isPresented: $showingLock) {
            NavigationStack {
                List { CommitmentSection(protection: protection, vault: vault) }
                    .listStyle(.insetGrouped)
                    .navigationTitle("commitment.sheet.title")
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) {
                            Button("notices.close") { showingLock = false }
                        }
                    }
            }
        }
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
        .sheet(isPresented: $showingAppLimits) {
            NavigationStack {
                List { AppUsageSection(protection: protection, usage: usage) }
                    .listStyle(.insetGrouped)
                    .navigationTitle("apps.sheet.title")
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) {
                            Button("notices.close") { showingAppLimits = false }
                        }
                    }
            }
        }
        .onAppear { refreshDerivedState() }
        .onChange(of: protection.checkedAt) { _ in refreshDerivedState() }
        .onChange(of: showingPause) { isShowing in
            if !isShowing { pauseCount = PauseStore.todayCount() }
        }
    }

    private func refreshDerivedState() {
        usage.refresh(trustedNow: protection.trustedNow)
        pauseCount = PauseStore.todayCount()
    }

    @ViewBuilder private var tabContent: some View {
        switch selectedTab {
        case 1: setup
        case 2: settings
        default: overview
        }
    }

    private var pageTitle: LocalizedStringKey {
        switch selectedTab {
        case 1: return "tab.setup"
        case 2: return "tab.settings"
        default: return "app.name"
        }
    }

    // MARK: - Overview

    /// Status, then the one positive action, then the plan, lock and rules.
    /// Everything that repeats Setup stays one tap away.
    private var overview: some View {
        ScrollView {
            VStack(spacing: 16) {
                ProtectionHero(protection: protection,
                               onSetup: { selectedTab = 1 },
                               onRefresh: { Task { await protection.refresh() } })
                ProtectionWarningsCard(protection: protection, usage: usage,
                                       onSetup: { selectedTab = 1 })
                PauseCard(count: pauseCount) { showingPause = true }
                ProtectionPlanCard(protection: protection, removal: removalProtection,
                                   onSetup: { selectedTab = 1 })
                ProtectionLockCard(protection: protection,
                                   onOpen: { showingLock = true })
                RulesCard(protection: protection, usage: usage,
                          onSites: { showingSites = true },
                          onApps: { showingAppLimits = true })
                overviewDetailsCard
                errorCard
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 92)
            .hisnReadableColumn(regular: horizontalSizeClass == .regular)
        }
        .background(HisnSurface.grouped)
        .refreshable { await protection.refresh() }
        .toolbar { pauseToolbar }
    }

    private var overviewDetailsCard: some View {
        HisnCard {
            DisclosureGroup("overview.details") {
                VStack(alignment: .leading, spacing: 12) {
                    Text("overview.body")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Text("setup.status.scope")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Text("setup.status.mixedcontent")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    HisnDivider()
                    Label("browserfirst.quickBody", systemImage: "puzzlepiece.extension")
                        .font(.callout)
                    Text("browserfirst.unverified")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    HisnDivider()
                    LabeledContent("list.domains", value: protection.listCount.formatted())
                    LabeledContent("list.version", value: String(protection.listVersion))
                    if let built = protection.listBuilt {
                        LabeledContent("list.built",
                                       value: built.formatted(date: .abbreviated, time: .omitted))
                        if SeedFreshness.stale(built: built, now: Date()) {
                            Text("list.stale")
                                .font(.footnote)
                                .foregroundStyle(HisnPalette.warning)
                        }
                    }
                    Text("list.body")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Button("setup.continue") { selectedTab = 1 }
                }
                .padding(.top, 8)
            }
            .font(.headline)
        }
    }

    // MARK: - Setup

    private var setup: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                SetupStatusCard(protection: protection, doneSteps: doneSetupSteps,
                                totalSteps: SetupGuide.Step.allCases.count)

                VStack(alignment: .leading, spacing: 10) {
                    HisnSectionLabel("setup.steps.title")
                    Text("setup.steps.subtitle")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    ForEach(SetupGuide.Step.allCases, id: \.self) { step in
                        NavigationLink {
                            stepDestination(step)
                        } label: {
                            SetupStepCard(step: step, status: setupStatus(step))
                        }
                        .buttonStyle(.plain)
                    }
                }

                setupChecksCard
                errorCard
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 92)
            .hisnReadableColumn(regular: horizontalSizeClass == .regular)
        }
        .background(HisnSurface.grouped)
        .toolbar { pauseToolbar }
    }

    private var doneSetupSteps: Int {
        SetupGuide.Step.allCases.filter { setupStatus($0) == .done }.count
    }

    private func setupStatus(_ step: SetupGuide.Step) -> SetupGuide.Status {
        SetupGuide.status(
            of: step,
            hasBlockingLayer: protection.hasBlockingConfiguration,
            commitmentActive: protection.commitmentActive,
            vaultHoldsPasscode: vault.facts?.holdsPasscode(at: protection.now) == true
        )
    }

    @ViewBuilder private func stepDestination(_ step: SetupGuide.Step) -> some View {
        switch step {
        case .layers:
            BlockingLayersSetupView(protection: protection)
        case .commitment:
            CommitmentSetupView(protection: protection, usage: usage, vault: vault)
        case .vault:
            VaultSetupView(protection: protection, vault: vault, lockDevice: lockDevice)
        }
    }

    /// The evidence Setup owns: the four checks and their exact state. It comes
    /// after the steps so the next action is never below a diagnostic list.
    private var setupChecksCard: some View {
        HisnCard {
            VStack(alignment: .leading, spacing: 0) {
                Text("setup.checks.title")
                    .font(.headline)
                    .accessibilityAddTraits(.isHeader)
                    .padding(.bottom, 2)
                checkRow("safari.title", ready: protection.safariConfigured,
                         detail: Text("plan.safariParts \(enabledSafariParts)"))
                HisnDivider()
                checkRow("dns.title", ready: protection.dns == .enabled, detail: Text(dnsLabel))
                HisnDivider()
                checkRow("screentime.layer", ready: protection.screenTimeConfigured,
                         detail: Text(LocalizedStringKey(protection.screenTimeState.rawValue)))
                HisnDivider()
                lockRow
                Text("plan.scope")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(.top, 10)
                if protection.checkedAt == nil {
                    Label("plan.checkFirst", systemImage: "arrow.clockwise")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .padding(.top, 6)
                }
            }
        }
    }

    private var enabledSafariParts: Int { protection.safari.filter { $0 == true }.count }

    private func checkRow(_ title: LocalizedStringKey, ready: Bool, detail: Text? = nil) -> some View {
        HisnAdaptiveRow(title: title, detail: detail) {
            HisnStatusPill(
                text: ready ? "plan.configured" : "plan.needsSetup",
                systemImage: ready ? "checkmark" : "circle",
                tint: ready ? HisnPalette.good : .secondary
            )
        }
        .padding(.vertical, 11)
    }

    /// The person's lock level from the plan's ladder. Hisn cannot read the Screen Time passcode,
    /// the deletion setting or supervision, so those show as confirmed by the person, never verified.
    private var removalProtection: MobileProtectionPolicy.RemovalProtection {
        MobileProtectionPolicy.removalProtection(removal: protection.removal,
            attestation: MobileProtectionPolicy.LockAttestation(storage: lockAttestationData),
            vault: vault.facts, now: protection.now, deviceMarker: lockDevice.marker)
    }

    @ViewBuilder private var lockRow: some View {
        let lock = removalProtection
        HisnAdaptiveRow(
            title: "lock.title",
            detail: lock.selfTested
                ? Text(LocalizedStringKey(lock.level.rawValue)) + Text(verbatim: "\n") + Text("lock.selfTested")
                : Text(LocalizedStringKey(lock.level.rawValue))
        ) {
            switch lock.evidence {
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
        .padding(.vertical, 11)
        .accessibilityIdentifier("plan.removalProtection")
        if let stale = lock.stale {
            Text(LocalizedStringKey(stale.rawValue))
                .font(.footnote)
                .foregroundStyle(.secondary)
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

    // MARK: - Settings

    private var settings: some View {
        ScrollView {
            VStack(spacing: 16) {
                languageCard
                safariExtensionCard
                grayScreenCard
                privacyCard
                aboutCard
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 92)
            .hisnReadableColumn(regular: horizontalSizeClass == .regular)
        }
        .background(HisnSurface.grouped)
        .toolbar { pauseToolbar }
        .sheet(isPresented: $showingNotices) { NoticesView() }
    }

    private var languageCard: some View {
        HisnCard {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 14) {
                        HisnIconBadge(systemName: "globe", tint: .accentColor, size: 42)
                        Text("language.title")
                            .font(.headline)
                    }
                    languagePicker
                }
            } else {
                HStack(spacing: 14) {
                    HisnIconBadge(systemName: "globe", tint: .accentColor, size: 42)
                    Text("language.title")
                        .font(.headline)
                    Spacer(minLength: 8)
                    languagePicker
                }
            }
        }
    }

    private var languagePicker: some View {
        Picker("language.title", selection: $language) {
            Text("language.system").tag("system")
            Text(verbatim: "العربية").tag("ar")
            Text(verbatim: "English").tag("en")
        }
        .labelsHidden()
        .pickerStyle(.menu)
    }

    private var safariExtensionCard: some View {
        HisnCard {
            VStack(alignment: .leading, spacing: 12) {
                if dynamicTypeSize.isAccessibilitySize {
                    HisnIconBadge(systemName: "puzzlepiece.extension", tint: .accentColor, size: 42)
                }
                HStack(alignment: .top, spacing: 14) {
                    if !dynamicTypeSize.isAccessibilitySize {
                        HisnIconBadge(systemName: "puzzlepiece.extension", tint: .accentColor, size: 42)
                    }
                    VStack(alignment: .leading, spacing: 3) {
                        Text("settings.safari.title")
                            .font(.headline)
                        Text("browserfirst.quickBody")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                }
                Button("browserfirst.openSetup") { selectedTab = 1 }
                    .buttonStyle(.bordered)
            }
        }
    }

    private var grayScreenCard: some View {
        HisnCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 14) {
                    HisnIconBadge(systemName: "circle.lefthalf.filled",
                                  tint: .secondary, size: 42)
                    Text("gray.title")
                        .font(.headline)
                    Spacer(minLength: 0)
                }
                Text("gray.body")
                    .font(.callout)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "1.circle.fill")
                        .foregroundStyle(.secondary)
                        .accessibilityHidden(true)
                    Text("gray.steps")
                        .font(.callout)
                        .fixedSize(horizontal: false, vertical: true)
                }
                DisclosureGroup("settings.gray.more") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("gray.shortcut")
                        Text("gray.automation")
                    }
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(.top, 6)
                }
                .font(.footnote.weight(.medium))
            }
        }
        .accessibilityIdentifier("settings.grayScreen")
    }

    private var privacyCard: some View {
        HisnCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 14) {
                    HisnIconBadge(systemName: "hand.raised", tint: HisnPalette.good, size: 42)
                    Text("privacy.title")
                        .font(.headline)
                    Spacer(minLength: 0)
                }
                Text("settings.privacy.summary")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                DisclosureGroup("settings.privacy.details") {
                    Text("privacy.body")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .padding(.top, 6)
                }
                .font(.footnote.weight(.medium))
                linkRow("privacy.policy", systemImage: "doc.text",
                        destination: URL(string: "https://github.com/hamzabinjehad/AdultContentBlocker/blob/main/PRIVACY.md")!)
                linkRow("support.page", systemImage: "questionmark.circle",
                        destination: URL(string: "https://github.com/hamzabinjehad/AdultContentBlocker/blob/main/SUPPORT.md")!)
                Button { showingNotices = true } label: {
                    settingsLinkRow("notices.open", systemImage: "doc.on.doc")
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func linkRow(_ title: LocalizedStringKey, systemImage: String, destination: URL) -> some View {
        Link(destination: destination) {
            settingsLinkRow(title, systemImage: systemImage)
        }
        .buttonStyle(.plain)
    }

    private func settingsLinkRow(_ title: LocalizedStringKey, systemImage: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: systemImage)
                .font(.callout)
                .foregroundStyle(.secondary)
                .frame(width: 22)
                .accessibilityHidden(true)
            Text(title)
                .font(.subheadline.weight(.medium))
            Spacer(minLength: 8)
            Image(systemName: "chevron.forward")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.tertiary)
                .accessibilityHidden(true)
        }
        .padding(.vertical, 6)
        .contentShape(Rectangle())
    }

    private var aboutCard: some View {
        HisnCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 14) {
                    HisnIconBadge(systemName: "shield.lefthalf.filled",
                                  tint: HisnPalette.good, size: 54)
                    VStack(alignment: .leading, spacing: 3) {
                        Text("app.name")
                            .font(.title3.bold())
                        Text("settings.about.tagline")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                }
                LabeledContent("about.version", value: appVersion)
                DisclosureGroup("coverage.title") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("coverage.body")
                        Text("apple.body")
                    }
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(.top, 6)
                }
                .font(.footnote.weight(.medium))
            }
        }
    }

    private var appVersion: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(version) (\(build))"
    }

    /// The pause is always one tap away and changes no rule, so it is available
    /// during a commitment too.
    @ToolbarContentBuilder
    private var pauseToolbar: some ToolbarContent {
        ToolbarItem(placement: .navigationBarTrailing) {
            Button { showingPause = true } label: {
                Image(systemName: "wind")
            }
            .accessibilityLabel(Text("pause.open"))
        }
    }

    @ViewBuilder private var errorCard: some View {
        if let error = protection.errorKey {
            HisnCard {
                Label(LocalizedStringKey(error), systemImage: "exclamationmark.triangle")
                    .foregroundStyle(HisnPalette.warning)
            }
        }
    }
}

/// Setup's order: blocking layers, then the commitment that locks them, then the passcode vault.
/// Each raw value is the step's text key.
enum SetupGuide {
    enum Step: String, CaseIterable {
        case layers = "guide.layers"
        case commitment = "guide.commitment"
        case vault = "guide.vault"
    }
    enum Status: Equatable { case done, next, later }

    /// The first step not done is next. A commitment running with no layer on sends the person
    /// back to step 1 rather than reading as progress.
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

/// The person's own confirmation of a lock Hisn cannot read, always shown as confirmed by them,
/// never as verified. It counts only on the install and iPhone it was given on: a backup restored
/// onto another iPhone, or onto this one after an erase, brings it back without its device marker,
/// so it no longer counts. The self-test is optional.
struct LockConfirmationRows: View {
    typealias Claim = MobileProtectionPolicy.LockAttestation.Claim
    let claims: [Claim]
    let vault: MobileProtectionPolicy.VaultFacts?
    let now: Date
    @ObservedObject var device: LockConfirmationDevice
    @AppStorage(MobileProtectionPolicy.lockAttestationKey) private var data = Data()
    @State private var pending: Claim?
    @State private var saveFailed = false

    private var current: MobileProtectionPolicy.LockAttestation? {
        MobileProtectionPolicy.LockAttestation(storage: data)
    }

    /// What this place shows.
    enum Display: Equatable {
        /// A confirmation of one of these claims counts: shown with the self-test and a way to withdraw it.
        case confirmed(Claim)
        /// None of these counts. A saved confirmation that no longer counts is reported with why;
        /// these claims may be confirmed (only higher ones while another claim still counts).
        case choose(stale: MobileProtectionPolicy.StaleReason?, offered: [Claim])
    }

    static func display(claims: [Claim], current: MobileProtectionPolicy.LockAttestation?,
                        vault: MobileProtectionPolicy.VaultFacts?, now: Date, deviceMarker: String?) -> Display {
        let counting = current.flatMap {
            MobileProtectionPolicy.attestationApplies($0, vault: vault, now: now, deviceMarker: deviceMarker) ? $0 : nil
        }
        if let counting, claims.contains(counting.claim) { return .confirmed(counting.claim) }
        let stale = MobileProtectionPolicy.staleReason(current, vault: vault, now: now, deviceMarker: deviceMarker)
        let offered = counting.map { counting in claims.filter { $0.level > counting.claim.level } } ?? claims
        return .choose(stale: stale, offered: offered)
    }

    /// A passcode-vault confirmation only while the vault keeps the passcode hidden: never while it
    /// is still on screen in its setup window.
    static func canConfirm(_ claim: Claim, vault: MobileProtectionPolicy.VaultFacts?, now: Date) -> Bool {
        claim != .passcodeVault || vault?.holdsPasscode(at: now) == true
    }

    var body: some View {
        switch Self.display(claims: claims, current: current, vault: vault, now: now, deviceMarker: device.marker) {
        case .confirmed(let claim):
            Label(LocalizedStringKey(Self.confirmedKey(claim)), systemImage: "person.fill.checkmark")
                .font(.callout)
            Toggle("lock.selfTest", isOn: selfTested)
            Button("lock.withdraw") { data = Data() }
        case .choose(let stale, let offered):
            if let stale {
                Text(LocalizedStringKey(stale.rawValue)).font(.footnote).foregroundStyle(.secondary)
                Button("lock.withdraw") { data = Data() }
            }
            ForEach(offered, id: \.self) { claim in
                Button(LocalizedStringKey(Self.confirmKey(claim))) { pending = claim }
                    .disabled(!Self.canConfirm(claim, vault: vault, now: now))
                    .alert("lock.confirm.title",
                           isPresented: Binding(get: { pending == claim }, set: { if !$0 { pending = nil } })) {
                        Button("lock.confirm.button") { confirm(claim) }
                        Button("cancel", role: .cancel) { }
                    } message: {
                        Text(LocalizedStringKey(Self.confirmBodyKey(claim)))
                    }
            }
            if saveFailed {
                Label("lock.saveFailed", systemImage: "exclamationmark.triangle")
                    .font(.footnote).foregroundStyle(HisnPalette.warning)
            }
            if !offered.isEmpty {
                Text("lock.confirm.hint").font(.footnote).foregroundStyle(.secondary)
            }
        }
    }

    private var selfTested: Binding<Bool> {
        Binding(get: { current?.selfTested == true }, set: { value in
            guard var next = current else { return }
            next.selfTested = value
            data = next.storage
        })
    }

    /// Saved with this install's device marker. If the marker cannot be kept, nothing is saved.
    private func confirm(_ claim: Claim) {
        guard Self.canConfirm(claim, vault: vault, now: now) else { return }
        guard let marker = device.establish() else { saveFailed = true; return }
        saveFailed = false
        data = MobileProtectionPolicy.LockAttestation(claim: claim, vaultCreatedAt: vault?.createdAt,
                                                      confirmedAt: Date(), deviceMarker: marker).storage
    }

    static func confirmKey(_ claim: Claim) -> String {
        switch claim {
        case .passcodeVault: return "lock.confirm.passcodeVault"
        case .familyGuardian: return "lock.confirm.familyGuardian"
        case .supervised: return "lock.confirm.supervised"
        }
    }
    static func confirmBodyKey(_ claim: Claim) -> String {
        switch claim {
        case .passcodeVault: return "lock.confirmBody.passcodeVault"
        case .familyGuardian: return "lock.confirmBody.familyGuardian"
        case .supervised: return "lock.confirmBody.supervised"
        }
    }
    static func confirmedKey(_ claim: Claim) -> String {
        switch claim {
        case .passcodeVault: return "lock.confirmed.passcodeVault"
        case .familyGuardian: return "lock.confirmed.familyGuardian"
        case .supervised: return "lock.confirmed.supervised"
        }
    }
}

// MARK: - Device marker for lock confirmations

/// One place the device marker is kept.
protocol LockMarkerStore {
    func read() -> String?
    func write(_ marker: String) -> Bool
}

/// Device-only Keychain: never synced, and never restored onto another iPhone.
struct KeychainLockMarkerStore: LockMarkerStore {
    var service = "hisn.mobile.lockMarker"
    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
         kSecAttrAccount as String: "marker"]
    }
    func read() -> String? {
        var search = query
        search[kSecReturnData as String] = true
        search[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(search as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }
    /// The item as it is added: device-only, never synced.
    func item(_ marker: String) -> [String: Any] {
        var insert = query
        insert[kSecValueData as String] = Data(marker.utf8)
        insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        return insert
    }
    /// Replaced, not updated, so the item is always device-only whatever was there before.
    func write(_ marker: String) -> Bool {
        let removed = SecItemDelete(query as CFDictionary)
        guard removed == errSecSuccess || removed == errSecItemNotFound else { return false }
        return SecItemAdd(item(marker) as CFDictionary, nil) == errSecSuccess
    }
}

/// A file excluded from every backup, so no restore brings it back, not even onto this iPhone
/// after it was erased (which a device-only Keychain item survives).
struct FileLockMarkerStore: LockMarkerStore {
    let url: URL
    static let standard = FileLockMarkerStore(url: URL.applicationSupportDirectory
        .appendingPathComponent("DeviceOnly", isDirectory: true).appendingPathComponent("lock-marker"))

    func read() -> String? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return String(data: data, encoding: .utf8)
    }
    func write(_ marker: String) -> Bool {
        var excluded = URLResourceValues()
        excluded.isExcludedFromBackup = true
        var folder = url.deletingLastPathComponent()
        var file = url
        do {
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            try folder.setResourceValues(excluded)
            try Data(marker.utf8).write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            // An atomic write replaces the file, so the exclusion is set on the new one.
            try file.setResourceValues(excluded)
            return true
        } catch {
            return false
        }
    }
}

/// Tests never touch the person's Keychain or files.
final class MemoryLockMarkerStore: LockMarkerStore {
    var value: String?
    var failing = false
    init(_ value: String? = nil) { self.value = value }
    func read() -> String? { failing ? nil : value }
    func write(_ marker: String) -> Bool {
        guard !failing else { return false }
        value = marker
        return true
    }
}

/// The marker lock confirmations carry: one random value kept both in the device-only Keychain
/// (never moved to another iPhone) and in a file no backup includes (never restored, even onto
/// this iPhone). A confirmation counts only while its marker is the one both still hold.
@MainActor final class LockConfirmationDevice: ObservableObject {
    @Published private(set) var marker: String?
    private let stores: [LockMarkerStore]

    init(stores: [LockMarkerStore]? = nil) {
        self.stores = stores ?? (NSClassFromString("XCTestCase") == nil
            ? [KeychainLockMarkerStore(), FileLockMarkerStore.standard] : [MemoryLockMarkerStore()])
        reload()
    }

    func reload() { marker = MobileProtectionPolicy.agreedMarker(stores.map { $0.read() }) }

    /// The marker a new confirmation is saved with: the one every store agrees on, or a new one
    /// written to all of them. A new one only retires confirmations that already did not count.
    /// nil when it cannot be kept, and the confirmation is then not saved.
    func establish() -> String? {
        reload()
        if let marker { return marker }
        let fresh = UUID().uuidString
        let written = stores.allSatisfy { $0.write(fresh) }
        reload()
        return written && marker == fresh ? fresh : nil
    }
}

/// The bundled third-party texts, shown offline. Generated by script/notices.py.
struct NoticesView: View {
    @Environment(\.dismiss) private var dismiss

    private var text: String? {
        guard let url = Bundle.main.url(forResource: "Notices", withExtension: "txt") else { return nil }
        return try? String(contentsOf: url, encoding: .utf8)
    }

    var body: some View {
        NavigationStack {
            Group {
                if let text {
                    ScrollView {
                        Text(verbatim: text)
                            .font(.system(.caption, design: .monospaced))
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding()
                    }
                } else {
                    Text("notices.unavailable")
                        .foregroundStyle(HisnPalette.warning)
                        .padding()
                }
            }
            .navigationTitle("notices.title")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("notices.close") { dismiss() } } }
        }
    }
}
