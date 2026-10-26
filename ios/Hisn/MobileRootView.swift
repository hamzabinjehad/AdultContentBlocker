import Security
import SwiftUI

struct MobileRootView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ObservedObject var protection: ProtectionController
    @Binding var language: String
    private enum Confirmation { case dns, guardian }
    @State private var confirmation: Confirmation?
    @State private var selectedTab = 0
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
                List(selection: Binding<Int?>(get: { selectedTab }, set: { if let value = $0 { selectedTab = value } })) {
                    Label("tab.protection", systemImage: "shield.lefthalf.filled").tag(0)
                    Label("tab.setup", systemImage: "checklist").tag(1)
                    Label("tab.settings", systemImage: "gearshape").tag(2)
                }.navigationTitle("app.name")
            } detail: {
                NavigationStack {
                    if selectedTab == 0 { overview.navigationTitle("tab.protection") }
                    else if selectedTab == 1 { setup.navigationTitle("tab.setup") }
                    else { settings.navigationTitle("tab.settings") }
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
        .alert(confirmation == .guardian ? "removal.consent.title" : "dns.consent.title",
               isPresented: Binding(get: { confirmation != nil }, set: { if !$0 { confirmation = nil } }),
               presenting: confirmation) { action in
            Button(action == .guardian ? "removal.request" : "dns.prepare") {
                Task {
                    if action == .guardian { await protection.requestGuardianProtection() }
                    else { await protection.installDNS() }
                }
            }
            Button("cancel", role: .cancel) { }
        } message: { action in
            Text(action == .guardian ? "removal.consent.body" : "dns.consent.body")
        }
        .onAppear { usage.refresh() }
        .onChange(of: protection.checkedAt) { _ in usage.refresh() }
    }

    /// Status first, then the commitment and the person's own sites. Everything
    /// that repeats Setup lives there; overview details stay one tap away.
    private var overview: some View {
        List {
            verifiedSetup(compact: true)
            readiness
            CommitmentSection(protection: protection, vault: vault)
            Section {
                LabeledContent("sites.title") {
                    Text(verbatim: "\(protection.customSites.count)").monospacedDigit()
                }
                Button("setup.continue") { selectedTab = 1 }
            }
            Section {
                DisclosureGroup("overview.details") {
                    Text("overview.body").foregroundStyle(.secondary)
                    Text("setup.status.scope").font(.footnote).foregroundStyle(.secondary)
                    Text("setup.status.mixedcontent").font(.footnote).foregroundStyle(.secondary)
                    Label("browserfirst.quickBody", systemImage: "puzzlepiece.extension").font(.callout)
                    Text("browserfirst.unverified").font(.footnote).foregroundStyle(.secondary)
                    LabeledContent("list.domains", value: protection.listCount.formatted())
                    LabeledContent("list.version", value: String(protection.listVersion))
                    Text("list.body").font(.footnote).foregroundStyle(.secondary)
                    Button("setup.continue") { selectedTab = 1 }
                }
            }
            errorRow
        }
        .listStyle(.insetGrouped)
        .environment(\.defaultMinListRowHeight, 48)
        .refreshable { await protection.refresh() }
        .frame(maxWidth: horizontalSizeClass == .regular ? 860 : .infinity)
        .frame(maxWidth: .infinity)
        .background(Color(uiColor: .systemGroupedBackground))
    }

    /// Guided order: status, then the blocking layers, then the commitment that locks them,
    /// then the passcode vault and the stronger locks that keep Hisn from being removed.
    private var setup: some View {
        List {
            verifiedSetup(compact: false)
            readiness
            setupGuide
            // Step 1: blocking layers.
            Section("safari.title") {
                Text("safari.steps")
                Button("safari.reload") { Task { await protection.reloadSafari() } }
                    .disabled(protection.busy || protection.listCount == 0)
                Text("safari.scope").font(.footnote).foregroundStyle(.secondary)
            }
            safariParts
            BrowserFirstSection()
            ScreenTimeSection(protection: protection)
            CustomSitesSection(protection: protection)
            AppUsageSection(protection: protection, usage: usage)
            Section("dns.title") {
                Text("dns.steps")
                Button("dns.prepare") { confirmation = .dns }.disabled(protection.busy)
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
            // Step 2: the commitment. Step 3: the passcode vault, then stronger locks.
            CommitmentSection(protection: protection, vault: vault)
            PasscodeVaultSection(vault: vault, protection: protection, lockDevice: lockDevice)
            removalSection
            errorRow
        }
        .listStyle(.insetGrouped)
        .environment(\.defaultMinListRowHeight, 48)
        .frame(maxWidth: horizontalSizeClass == .regular ? 860 : .infinity)
        .frame(maxWidth: .infinity)
        .background(Color(uiColor: .systemGroupedBackground))
    }

    private func verifiedSetup(compact: Bool) -> some View {
        let assessment = protection.setupAssessment
        return Section("setup.status.title") {
            setupStatusLabel(assessment.state)
                .font(.title2.bold())
                .accessibilityAddTraits(.isHeader)
                .foregroundStyle(assessment.state == .configured ? HisnPalette.good : HisnPalette.warning)
                .accessibilityIdentifier("setup.currentAssessment")
            if !compact {
                Text("setup.status.scope").font(.footnote).foregroundStyle(.secondary)
            }
            if assessment.state == .unchecked {
                Text("setup.next.check").font(.callout)
            }
            ForEach(assessment.issues, id: \.rawValue) { issue in
                // "Forward" points the reading direction: left in Arabic, right in English.
                Label(LocalizedStringKey(issue.rawValue), systemImage: "arrow.forward.circle")
                    .font(.callout)
            }
            if assessment.state == .checking { ProgressView("working") }
            if let date = protection.checkedAt {
                LabeledContent("status.checked") { Text(date, style: .time) }
                    .font(.footnote).foregroundStyle(.secondary)
            }
            Button("status.refresh") { Task { await protection.refresh() } }
                .disabled(protection.busy)
            if assessment.state == .needsSetup, selectedTab != 1 {
                Button("setup.continue") { selectedTab = 1 }
            }
            if !compact {
                Text("setup.status.mixedcontent").font(.footnote).foregroundStyle(.secondary)
            }
        }
    }

    @ViewBuilder
    private func setupStatusLabel(_ state: MobileProtectionPolicy.SetupState) -> some View {
        switch state {
        case .configured: Label(LocalizedStringKey(state.rawValue), systemImage: "checkmark.shield")
        case .checking, .unchecked: Label(LocalizedStringKey(state.rawValue), systemImage: "arrow.clockwise")
        case .needsSetup: Label(LocalizedStringKey(state.rawValue), systemImage: "exclamationmark.shield")
        }
    }

    private var readiness: some View {
        Section("plan.title") {
            if !protection.historyStorageHealthy {
                Label("plan.historyUnavailable", systemImage: "exclamationmark.triangle")
                    .foregroundStyle(HisnPalette.warning)
            }
            if !protection.unconfirmedContentLayers.isEmpty {
                Label("plan.lost", systemImage: "exclamationmark.triangle")
                    .foregroundStyle(HisnPalette.warning)
                ForEach(protection.unconfirmedContentLayers.sorted(), id: \.self) { key in
                    Text(LocalizedStringKey(key)).font(.callout)
                }
                Button("status.refresh") { Task { await protection.refresh() } }
                    .disabled(protection.busy)
            }
            if usage.health.needsAttention {
                Label(LocalizedStringKey(usage.health.rawValue), systemImage: "exclamationmark.triangle")
                    .foregroundStyle(HisnPalette.warning)
                Text("apps.health.recovery").font(.footnote)
                Button("setup.continue") { selectedTab = 1 }
            }
            Text("plan.layers \(protection.configuredContentLayers)")
                .font(.headline).accessibilityIdentifier("plan.configuredLayers")
            readinessRow("safari.title", ready: protection.safariConfigured,
                detail: Text("plan.safariParts \(enabledSafariParts)"))
            readinessRow("dns.title", ready: protection.dns == .enabled, detail: Text(dnsLabel))
            readinessRow("screentime.layer", ready: protection.screenTimeConfigured,
                detail: Text(LocalizedStringKey(protection.screenTimeState.rawValue)))
            lockRow
            Text("plan.scope").font(.footnote).foregroundStyle(.secondary)
            if protection.checkedAt == nil {
                Label("plan.checkFirst", systemImage: "arrow.clockwise")
                    .font(.footnote)
            }
        }
    }

    private var enabledSafariParts: Int { protection.safari.filter { $0 == true }.count }

    private func readinessRow(_ title: LocalizedStringKey, ready: Bool, detail: Text? = nil) -> some View {
        readinessRow(title, detail: detail) { readinessStatus(ready) }
    }

    /// Title and status side by side, stacked at accessibility text sizes so neither is cut off.
    private func readinessRow<Status: View>(_ title: LocalizedStringKey, detail: Text? = nil,
                                            @ViewBuilder status: () -> Status) -> some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 4) {
                    readinessTitle(title, detail: detail)
                    status()
                }
            } else {
                HStack {
                    readinessTitle(title, detail: detail)
                    Spacer()
                    status()
                }
            }
        }
        .font(.subheadline)
        .accessibilityElement(children: .combine)
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
        let level = Text(LocalizedStringKey(lock.level.rawValue))
        readinessRow("lock.title",
                     detail: lock.selfTested ? level + Text(verbatim: "\n") + Text("lock.selfTested") : level) {
            switch lock.evidence {
            case .verified?:
                Label("lock.evidence.verified", systemImage: "checkmark.seal").foregroundStyle(HisnPalette.good)
            case .attested?:
                Label("lock.evidence.attested", systemImage: "person.fill.checkmark").foregroundStyle(.secondary)
            case nil:
                Label("lock.evidence.none", systemImage: "lock.open").foregroundStyle(.secondary)
            }
        }
        .accessibilityIdentifier("plan.removalProtection")
        if let stale = lock.stale {
            Text(LocalizedStringKey(stale.rawValue)).font(.footnote).foregroundStyle(.secondary)
        }
    }

    /// Setup's three steps in order, each with what Hisn can see of it.
    private var setupGuide: some View {
        Section("guide.title") {
            Text("guide.body").font(.footnote).foregroundStyle(.secondary)
            ForEach(SetupGuide.Step.allCases, id: \.self) { step in
                readinessRow(LocalizedStringKey(step.rawValue)) {
                    switch SetupGuide.status(of: step, hasBlockingLayer: protection.hasBlockingConfiguration,
                                             commitmentActive: protection.commitmentActive,
                                             vaultHoldsPasscode: vault.facts?.holdsPasscode(at: protection.now) == true) {
                    case .done: Label("guide.done", systemImage: "checkmark.circle").foregroundStyle(HisnPalette.good)
                    case .next: Label("guide.next", systemImage: "arrow.forward.circle")
                    case .later: Label("guide.later", systemImage: "circle").foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    private func readinessTitle(_ title: LocalizedStringKey, detail: Text?) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
            if let detail { detail.font(.caption).foregroundStyle(.secondary).monospacedDigit() }
        }
    }

    @ViewBuilder private func readinessStatus(_ ready: Bool) -> some View {
        if ready {
            Label("plan.configured", systemImage: "checkmark.circle").foregroundStyle(HisnPalette.good)
        } else {
            Label("plan.needsSetup", systemImage: "circle").foregroundStyle(.secondary)
        }
    }

    private var removalSection: some View {
        Section("removal.title") {
            Label(LocalizedStringKey(protection.removal.rawValue), systemImage: "lock.shield")
            Text("removal.body").font(.footnote).foregroundStyle(.secondary)
            Button("removal.request") { confirmation = .guardian }.disabled(protection.busy)
            Text("removal.manual").font(.footnote).foregroundStyle(.secondary)
            Text("removal.recovery").font(.footnote).foregroundStyle(.secondary)
            DisclosureGroup("removal.setup.title") {
                Text("removal.setup.mobile").font(.callout).foregroundStyle(.secondary)
                Button("status.refresh") { Task { await protection.refresh() } }
                    .disabled(protection.busy)
            }
            DisclosureGroup("lock.confirmOther") {
                Text("lock.ladder").font(.footnote).foregroundStyle(.secondary)
                LockConfirmationRows(claims: [.familyGuardian, .supervised], vault: vault.facts, now: protection.now,
                                     device: lockDevice)
            }
            Link("removal.guide", destination: URL(string: "https://support.apple.com/en-gb/105121")!)
        }
    }

    private var safariParts: some View {
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

    private var settings: some View {
        Form {
            Section("language.title") {
                Picker("language.title", selection: $language) {
                    Text("language.system").tag("system")
                    Text(verbatim: "العربية").tag("ar")
                    Text(verbatim: "English").tag("en")
                }
            }
            Section("browserfirst.visual.title") {
                Text("browserfirst.visual.body").font(.callout)
                Text("browserfirst.visual.appearance")
                    .font(.footnote).foregroundStyle(.secondary)
                Text("browserfirst.visual.unverified")
                    .font(.footnote).foregroundStyle(.secondary)
                Button("browserfirst.openSetup") { selectedTab = 1 }
            }
            Section("privacy.title") { Text("privacy.body") }
            Section("coverage.title") { Text("coverage.body") }
            Section("apple.title") { Text("apple.body") }
        }
        // The same readable column as the other tabs on a wide iPad.
        .frame(maxWidth: horizontalSizeClass == .regular ? 860 : .infinity)
        .frame(maxWidth: .infinity)
        .background(Color(uiColor: .systemGroupedBackground))
    }

    @ViewBuilder private var errorRow: some View {
        if let error = protection.errorKey {
            Section { Label(LocalizedStringKey(error), systemImage: "exclamationmark.triangle") }
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
    /// Replaced, not updated, so the item is always device-only whatever was there before.
    func write(_ marker: String) -> Bool {
        let removed = SecItemDelete(query as CFDictionary)
        guard removed == errSecSuccess || removed == errSecItemNotFound else { return false }
        var insert = query
        insert[kSecValueData as String] = Data(marker.utf8)
        insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        return SecItemAdd(insert as CFDictionary, nil) == errSecSuccess
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
