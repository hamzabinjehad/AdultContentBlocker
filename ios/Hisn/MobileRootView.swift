import Security
import SwiftUI

/// The app's four places: what is true today, the apps Hisn holds, the pause
/// practice, and everything that is not a daily decision. The order is the same
/// on iPhone and iPad; the iPad splits it into a sidebar.
struct MobileRootView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @ObservedObject var protection: ProtectionController
    @Binding var language: String
    @State private var selectedTab = 0
    @State private var showingPause = false
    @State private var pauseCount: Int?
    @State private var practiceDays: Set<String> = []
    @State private var streak = 0
    @State private var pausePrompt: PauseRulePrompt?
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var usage = AppUsageController()
    @StateObject private var vault: PasscodeVaultController
    /// The marker that confirmation must carry to count on this install on this iPhone.
    @StateObject private var lockDevice: LockConfirmationDevice

    init(protection: ProtectionController, language: Binding<String>, initialTab: Int = 0,
         vault: PasscodeVaultController? = nil, lockDevice: LockConfirmationDevice? = nil) {
        self.protection = protection
        self._language = language
        self._selectedTab = State(initialValue: (0...3).contains(initialTab) ? initialTab : 0)
        self._vault = StateObject(wrappedValue: vault ?? PasscodeVaultController())
        self._lockDevice = StateObject(wrappedValue: lockDevice ?? LockConfirmationDevice())
    }

    var body: some View {
        Group {
            if horizontalSizeClass == .regular {
                NavigationSplitView {
                    List(selection: Binding<Int?>(get: { selectedTab },
                                                  set: { if let value = $0 { selectedTab = value } })) {
                        Label("tab.today", systemImage: "house").tag(0)
                        Label("tab.apps", systemImage: "shield.lefthalf.filled").tag(1)
                        Label("tab.practice", systemImage: "wind").tag(2)
                        Label("tab.more", systemImage: "ellipsis.circle").tag(3)
                    }
                    .navigationTitle("app.name")
                    .tint(HnPalette.accent)
                } detail: {
                    NavigationStack {
                        tabContent
                            .toolbar(.hidden, for: .navigationBar)
                    }
                }
            } else {
                TabView(selection: $selectedTab) {
                    NavigationStack { today.toolbar(.hidden, for: .navigationBar) }
                        .tabItem { Label("tab.today", systemImage: "house") }
                        .tag(0)
                    NavigationStack { apps.toolbar(.hidden, for: .navigationBar) }
                        .tabItem { Label("tab.apps", systemImage: "shield.lefthalf.filled") }
                        .tag(1)
                    NavigationStack { practice.toolbar(.hidden, for: .navigationBar) }
                        .tabItem { Label("tab.practice", systemImage: "wind") }
                        .tag(2)
                    NavigationStack { more.toolbar(.hidden, for: .navigationBar) }
                        .tabItem { Label("tab.more", systemImage: "ellipsis.circle") }
                        .tag(3)
                }
                .tint(HnPalette.accent)
            }
        }
        .fullScreenCover(isPresented: $showingPause) {
            PauseView(onClose: {
                showingPause = false
                refreshDerivedState()
            })
        }
        .fullScreenCover(item: $pausePrompt) { prompt in
            // The shield's pause action hands the request to the app; this is
            // the only place it becomes a session. Closing spends nothing.
            PauseRuleGrantView(usage: usage, appName: prompt.request.appName,
                               onClose: {
                                   pausePrompt = nil
                                   refreshDerivedState()
                               })
        }
        .onAppear {
            refreshDerivedState()
            takePauseRuleRequest()
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                refreshDerivedState()
                takePauseRuleRequest()
            }
        }
        .onChange(of: protection.checkedAt) { _ in refreshDerivedState() }
    }

    @ViewBuilder private var tabContent: some View {
        switch selectedTab {
        case 1: apps
        case 2: practice
        case 3: more
        default: today
        }
    }

    private var today: some View {
        TodayView(protection: protection,
                  usage: usage,
                  streak: streak,
                  practiceDays: practiceDays,
                  pauseCount: pauseCount,
                  onProtection: { selectedTab = 3 },
                  onApps: { selectedTab = 1 },
                  onPause: { showingPause = true },
                  onRefresh: { Task { await protection.refresh() } })
    }

    private var apps: some View {
        AppsView(protection: protection,
                 usage: usage,
                 onProtection: { selectedTab = 3 },
                 onPause: { showingPause = true })
    }

    private var practice: some View {
        PracticeView(streak: streak,
                     practiceDays: practiceDays,
                     pauseCount: pauseCount,
                     now: protection.now,
                     onPause: { showingPause = true })
    }

    private var more: some View {
        MoreView(protection: protection,
                 usage: usage,
                 vault: vault,
                 lockDevice: lockDevice,
                 language: $language)
    }

    private func refreshDerivedState() {
        usage.refresh(trustedNow: protection.trustedNow)
        pauseCount = PauseStore.todayCount()
        let store = PauseRuleStore.appGroup
        practiceDays = Set(store.practiceDays())
        streak = store.streak(now: protection.now)
    }

    /// Consume a handoff at most once per foreground, and only while no other
    /// prompt is up. The request is already age-bounded by the controller, so
    /// a stale one from an app opened ten minutes ago is ignored, not shown.
    private func takePauseRuleRequest() {
        guard pausePrompt == nil, !showingPause else { return }
        if let request = usage.takePauseRequest() {
            pausePrompt = PauseRulePrompt(request: request)
        }
    }
}

/// A sheet identity for one handoff; the request itself is not Identifiable.
private struct PauseRulePrompt: Identifiable {
    let id = UUID()
    let request: PauseRule.Request
}

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
