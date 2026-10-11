import Foundation
import NetworkExtension
import SafariServices
import FamilyControls
import Combine
import ManagedSettings
import CryptoKit

@MainActor
final class ProtectionController: ObservableObject {
    @Published private(set) var dns: MobileProtectionPolicy.DNSState = .unknown
    @Published private(set) var safari: [Bool?] = Array(repeating: nil, count: 4)
    @Published private(set) var safariReloads: [Bool?] = Array(repeating: nil, count: 4)
    @Published private(set) var checkedAt: Date?
    @Published private(set) var busy = false
    @Published private(set) var errorKey: String?
    @Published private(set) var listCount = 0
    @Published private(set) var listVersion = 0
    @Published private(set) var listIntegrityVerified = false
    private var listChecked = false
    private let resourceBundleURL: URL
    @Published private(set) var removal: MobileProtectionPolicy.RemovalState = .notRequested
    private var removalEvidence = MobileProtectionPolicy.RemovalEvidence()
    private var authorizationObservation: AnyCancellable?
    @Published private(set) var commitment: CommitmentPolicy.Session?
    @Published private(set) var commitmentStorageHealthy = true
    @Published private(set) var screenTimeConfigured = false
    @Published private(set) var previouslyReadyContentLayers: Set<String> = []
    @Published private(set) var historyStorageHealthy = true
    private let readinessHistory: ReadinessHistory
    @Published private(set) var now = Date()
    @Published private(set) var clockAssessment: CommitmentClock.Assessment = .consistent
    private let clockSource: () -> (wall: Date, uptime: TimeInterval)
    private let clockMonitor: CommitmentClock
    private let commitmentStore: CommitmentPersistence
    private var ticker: AnyCancellable?
    private let screenTimeStore = ManagedSettingsStore(named: .init("hisn.self-control"))
    private var screenTimeChosenForCommitment = false

    init(commitmentStore: CommitmentPersistence? = nil, readinessHistory: ReadinessHistory? = nil,
         resourceBundleURL: URL = Bundle.main.bundleURL,
         clockSource: @escaping () -> (wall: Date, uptime: TimeInterval) = { (Date(), CommitmentClock.continuousTime) }) {
        self.resourceBundleURL = resourceBundleURL
        self.clockSource = clockSource
        let sample = clockSource()
        clockMonitor = CommitmentClock(wallAnchor: sample.wall, uptimeAnchor: sample.uptime)
        if sample.wall.timeIntervalSinceReferenceDate.isFinite { now = sample.wall }
        let isTest = NSClassFromString("XCTestCase") != nil
        self.commitmentStore = commitmentStore ?? (isTest ? MemoryCommitmentStore() : MobileCommitmentStore())
        self.readinessHistory = readinessHistory ?? .deviceLocal(key: "hisn.mobile.readiness",
            allowed: ["screentime.layer", "dns.title", "safari.title"])
        previouslyReadyContentLayers = self.readinessHistory.known
        historyStorageHealthy = self.readinessHistory.storageHealthy
        loadCommitment()
        updateClock()
        ticker = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
            .sink { [weak self] _ in self?.updateClock() }
        if let data = try? Data(contentsOf: resourceBundleURL.appendingPathComponent("rules-metadata.json")),
           let metadata = try? JSONDecoder().decode(Metadata.self, from: data) {
            listCount = metadata.count; listVersion = metadata.version
        }
        #if targetEnvironment(simulator)
        removal = .unavailable
        #else
        authorizationObservation = AuthorizationCenter.shared.$authorizationStatus
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in self?.observeAuthorization(status) }
        #endif
    }

    var commitmentActive: Bool { commitment?.isActive(at: now) == true }

    private func updateClock() {
        // A forward wall-clock jump cannot end a running app session early.
        // Reboot/termination still requires wall-clock trust; this is not secure time.
        let sample = clockSource()
        clockAssessment = clockMonitor.assessment(wall: sample.wall, uptime: sample.uptime)
        if let elapsed = clockMonitor.elapsedDate(uptime: sample.uptime) { now = elapsed }
    }

    private func loadCommitment() {
        do { commitment = try commitmentStore.load(); commitmentStorageHealthy = true }
        catch { commitmentStorageHealthy = false; errorKey = "commitment.storageFailed" }
    }

    func startCommitment(days: Int) {
        updateClock()
        guard !busy, commitmentStorageHealthy, !commitmentActive, [1, 7, 30, 90].contains(days) else { return }
        let session = CommitmentPolicy.Session(startedAt: now, deadline: now.addingTimeInterval(Double(days) * 86400))
        do {
            try commitmentStore.save(session)
            commitment = session
            screenTimeChosenForCommitment = screenTimeConfigured
            errorKey = nil
        } catch { commitmentStorageHealthy = false; errorKey = "commitment.storageFailed" }
    }

    /// Strengthening only: preserve the start and add time to the chosen end.
    func extendCommitment(days: Int) {
        updateClock()
        guard !busy, commitmentStorageHealthy, commitmentActive, [7, 30].contains(days),
              let next = commitment?.extending(by: Double(days) * 86400) else {
            errorKey = "commitment.extendFailed"; return
        }
        do {
            try commitmentStore.save(next)
            commitment = next
            errorKey = nil
        } catch { commitmentStorageHealthy = false; errorKey = "commitment.storageFailed" }
    }

    /// Independent adult self-control, not a hidden fallback from guardian authorization.
    func enablePersonalScreenTime() async {
        guard !busy else { return }
        busy = true; errorKey = nil
        defer { busy = false }
        #if targetEnvironment(simulator)
        errorKey = "screentime.simulator"
        #else
        do {
            let center = AuthorizationCenter.shared
            // Do not replace existing (potentially child) access with self access.
            if center.authorizationStatus == .notDetermined || center.authorizationStatus == .denied {
                try await center.requestAuthorization(for: .individual)
            }
            observeAuthorization(center.authorizationStatus)
            guard center.authorizationStatus == .approved else {
                errorKey = "screentime.failed"; return
            }
            screenTimeStore.webContent.blockedByFilter = .auto()
            readScreenTimeState()
        } catch { errorKey = "screentime.failed" }
        #endif
    }

    private func readScreenTimeState() {
        #if targetEnvironment(simulator)
        screenTimeConfigured = false
        #else
        if !commitmentActive { screenTimeChosenForCommitment = false }
        if commitmentActive, screenTimeStore.webContent.blockedByFilter == .auto() {
            screenTimeChosenForCommitment = true
        }
        if CommitmentPolicy.shouldRestoreProtection(active: commitmentActive,
            commitmentReadable: commitmentStorageHealthy, historyReadable: historyStorageHealthy,
            previouslyEnabled: screenTimeChosenForCommitment,
            authorized: AuthorizationCenter.shared.authorizationStatus == .approved) {
            // Reassert only Hisn's previously consented filter; do not touch other stores
            // or request new authorization. Revoked OS permission cannot be repaired here.
            screenTimeStore.webContent.blockedByFilter = .auto()
        }
        screenTimeConfigured = AuthorizationCenter.shared.authorizationStatus == .approved
            && screenTimeStore.webContent.blockedByFilter == .auto()
        #endif
        rememberConfiguredLayers()
    }

    private var readyContentLayers: Set<String> {
        var layers: Set<String> = []
        if screenTimeConfigured { layers.insert("screentime.layer") }
        if dns == .enabled { layers.insert("dns.title") }
        if safariConfigured { layers.insert("safari.title") }
        return layers
    }
    var unconfirmedContentLayers: Set<String> {
        previouslyReadyContentLayers.subtracting(readyContentLayers)
    }
    private func rememberConfiguredLayers() {
        readinessHistory.observe(readyContentLayers)
        previouslyReadyContentLayers = readinessHistory.known
        historyStorageHealthy = readinessHistory.storageHealthy
    }

    func disablePersonalScreenTime() {
        updateClock()
        guard !busy, commitmentStorageHealthy, !commitmentActive else { return }
        screenTimeStore.webContent.blockedByFilter = nil
        readScreenTimeState()
    }

    var hasBlockingConfiguration: Bool {
        screenTimeConfigured || dns == .enabled || safariConfigured
    }

    var safariConfigured: Bool {
        listIntegrityVerified && listCount > 0 && listVersion > 0 &&
            MobileProtectionPolicy.safariConfigurationReady(enabled: safari, lastReload: safariReloads)
    }

    var setupAssessment: MobileProtectionPolicy.SetupAssessment {
        MobileProtectionPolicy.setupAssessment(checked: checkedAt != nil, busy: busy,
            listVerified: listIntegrityVerified, listCount: listCount, listVersion: listVersion,
            safari: safari, lastReload: safariReloads, screenTime: screenTimeConfigured, dns: dns,
            storageHealthy: commitmentStorageHealthy && historyStorageHealthy)
    }

    /// Configuration readback only; never a claim that every route is blocked.
    var configuredContentLayers: Int {
        [screenTimeConfigured, dns == .enabled, safariConfigured].filter { $0 }.count
    }

    private func observeAuthorization(_ status: AuthorizationStatus, childRequestSucceeded: Bool = false) {
        let value: MobileProtectionPolicy.FamilyAuthorization
        switch status {
        case .notDetermined: value = .notDetermined
        case .denied: value = .denied
        case .approved: value = .approved
        // Other approval variants do not establish child scope. Stay conservative
        // and compile with older supported SDKs without requesting data access.
        default: value = .unavailable
        }
        removal = removalEvidence.observe(value, childRequestSucceeded: childRequestSucceeded)
        readScreenTimeState()
    }

    /// Explicit guardian action only; never silently fall back to individual authorization.
    func requestGuardianProtection() async {
        guard !busy else { return }
        busy = true; errorKey = nil
        defer { busy = false }
        #if targetEnvironment(simulator)
        removal = .unavailable
        errorKey = "removal.simulator"
        #else
        do {
            try await AuthorizationCenter.shared.requestAuthorization(for: .child)
            observeAuthorization(AuthorizationCenter.shared.authorizationStatus, childRequestSucceeded: true)
            if removal != .guardianRequestAccepted { errorKey = "removal.failed" }
        } catch {
            observeAuthorization(AuthorizationCenter.shared.authorizationStatus)
            errorKey = "removal.failed"
        }
        #endif
    }

    func refresh() async {
        guard !busy else { return }
        busy = true
        errorKey = nil
        defer { busy = false }
        await readState()
    }

    private func readState() async {
        updateClock()
        loadCommitment()
        if !listChecked {
            // Immutable bundled resources need one integrity check per launch,
            // off the UI actor. Enabled Safari switches alone do not prove that
            // usable, nonempty rules shipped with this app.
            let bundleURL = resourceBundleURL
            let result = await Task.detached(priority: .utility) {
                Self.checkBundledList(at: bundleURL)
            }.value
            listCount = result.count
            listVersion = result.version
            listIntegrityVerified = result.verified
            listChecked = true
        }
        readScreenTimeState()
        #if !targetEnvironment(simulator)
        observeAuthorization(AuthorizationCenter.shared.authorizationStatus)
        #endif
        #if targetEnvironment(simulator)
        dns = .unavailable
        #else
        do {
            let manager = NEDNSSettingsManager.shared()
            try await manager.loadFromPreferences()
            let settings = manager.dnsSettings as? NEDNSOverHTTPSSettings
            dns = MobileProtectionPolicy.dnsState(hasConfiguration: manager.dnsSettings != nil,
                isEnabled: manager.isEnabled, serverURL: settings?.serverURL, servers: settings?.servers ?? [],
                matchDomains: settings?.matchDomains)
        } catch { dns = .unavailable }
        #endif
        var states: [Bool?] = []
        for identifier in MobileProtectionPolicy.blockerIdentifiers {
            let enabled: Bool? = await withCheckedContinuation { continuation in
                SFContentBlockerManager.getStateOfContentBlocker(withIdentifier: identifier) { state, error in
                    continuation.resume(returning: error == nil ? state?.isEnabled : nil)
                }
            }
            states.append(enabled)
        }
        safari = states
        rememberConfiguredLayers()
        checkedAt = Date()
    }

    func installDNS() async {
        guard !busy else { return }
        busy = true; errorKey = nil
        defer { busy = false }
        #if targetEnvironment(simulator)
        dns = .unavailable
        errorKey = "dns.simulator"
        #else
        do {
            let manager = NEDNSSettingsManager.shared()
            try await manager.loadFromPreferences()
            let settings = NEDNSOverHTTPSSettings(servers: MobileProtectionPolicy.dnsServers)
            settings.serverURL = MobileProtectionPolicy.dnsURL
            settings.matchDomains = [""]
            manager.dnsSettings = settings
            manager.localizedDescription = "Hisn Family DNS"
            manager.onDemandRules = [NEOnDemandRuleConnect()]
            try await manager.saveToPreferences()
            // Saving is not enabling; read back what iOS actually reports.
            await readState()
        } catch {
            dns = .unavailable
            errorKey = "dns.failed"
        }
        #endif
    }

    func reloadSafari() async {
        guard !busy else { return }
        busy = true; errorKey = nil
        defer { busy = false }
        var reloads: [Bool?] = []
        for identifier in MobileProtectionPolicy.blockerIdentifiers {
            let success: Bool = await withCheckedContinuation { continuation in
                SFContentBlockerManager.reloadContentBlocker(withIdentifier: identifier) { error in
                    continuation.resume(returning: error == nil)
                }
            }
            reloads.append(success)
        }
        safariReloads = reloads
        await readState()
        if reloads.contains(false) { errorKey = "safari.failed" }
    }

    nonisolated static func checkBundledList(at bundleURL: URL) -> (count: Int, version: Int, verified: Bool) {
        guard let data = try? Data(contentsOf: bundleURL.appendingPathComponent("rules-metadata.json")),
              let summary = try? JSONDecoder().decode(Metadata.self, from: data),
              summary.parts == MobileProtectionPolicy.blockerIdentifiers.count else { return (0, 0, false) }
        var parts: [MobileProtectionPolicy.SafariListPart] = []
        for part in 1...MobileProtectionPolicy.blockerIdentifiers.count {
            let directory = bundleURL.appendingPathComponent("PlugIns/Hisn\(part).appex")
            let rulesURL = directory.appendingPathComponent("rules").appendingPathExtension("json")
            guard let raw = try? Data(contentsOf: rulesURL, options: .mappedIfSafe),
                  let metadata = try? Data(contentsOf: directory.appendingPathComponent("rules-metadata.json")),
                  let info = try? JSONDecoder().decode(PartMetadata.self, from: metadata),
                  let rules = try? JSONSerialization.jsonObject(with: raw) as? [[String: Any]] else {
                return (summary.count, summary.version, false)
            }
            let digest = SHA256.hash(data: raw).map { String(format: "%02x", $0) }.joined()
            let blockingRules = rules.allSatisfy { rule in
                let trigger = rule["trigger"] as? [String: Any]
                let action = rule["action"] as? [String: Any]
                return (trigger?["url-filter"] as? String)?.isEmpty == false && action?["type"] as? String == "block"
            }
            parts.append(.init(count: info.count, version: info.version,
                integrityMatches: digest == info.sha256 && rules.count == info.count && blockingRules))
        }
        return (summary.count, summary.version,
            MobileProtectionPolicy.safariListReady(count: summary.count, version: summary.version, parts: parts))
    }

    private struct Metadata: Decodable { let count: Int; let version: Int; let parts: Int }
    private struct PartMetadata: Decodable { let count: Int; let version: Int; let sha256: String }
}
