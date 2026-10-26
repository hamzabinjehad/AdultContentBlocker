import Foundation
import NetworkExtension
import SafariServices
import FamilyControls
import Combine
import ManagedSettings
import DeviceActivity
import UserNotifications
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
    @Published private(set) var screenTimeAuthorization: MobileProtectionPolicy.FamilyAuthorization = .notDetermined
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
    /// Hisn's own Screen Time web filter and whether iOS grants access to it.
    private let screenTimeFilter: ScreenTimeFilterAccess
    /// Apple's server time carried forward with the monotonic clock: a lower bound of the
    /// real time that a moved device clock cannot raise. nil until Apple's time was read.
    @Published private(set) var trustedNow: Date?
    @Published private(set) var checkingServerTime = false
    /// The last attempt to read Apple's time failed (offline or blocked).
    @Published private(set) var serverTimeFailed = false
    private let serverTime: ServerTimeSource
    /// This boot's identifier; a saved reading from another boot is never taken as exact.
    private let bootSession: () -> String?
    private var anchor: TrustedTime.Anchor?
    /// Starting a commitment needs Apple's time read in this boot: set while Hisn reads it,
    /// and to say why a commitment did not start when it could not.
    @Published private(set) var commitmentStartNote: String?
    private var lastServerAttempt: TimeInterval?
    private var lastTimeCheck: TrustedTime.Verdict?
    private var serverCheck: Task<Void, Never>?
    /// The app group shared with the monitor extension; memory only in tests unless injected.
    private let shared: MonitorSharedState
    private let notifications: NotificationPermission
    @Published private(set) var notificationStatus: NotificationStatus?
    /// The person turned layer alerts on in Hisn.
    @Published private(set) var alertsWanted = false
    @Published private(set) var customSites: [String] = []
    @Published private(set) var customSitesStorageHealthy = true
    /// The Screen Time filter reads back exactly the saved site list. Configuration only.
    @Published private(set) var customSitesApplied = false
    @Published private(set) var customSiteMessage: String?
    private let customSiteStore: CustomSiteStore

    init(commitmentStore: CommitmentPersistence? = nil, readinessHistory: ReadinessHistory? = nil,
         customSiteStore: CustomSiteStore? = nil,
         resourceBundleURL: URL = Bundle.main.bundleURL,
         clockSource: @escaping () -> (wall: Date, uptime: TimeInterval) = { (Date(), CommitmentClock.continuousTime) },
         serverTime: ServerTimeSource? = nil, shared: MonitorSharedState? = nil,
         notifications: NotificationPermission? = nil, screenTimeFilter: ScreenTimeFilterAccess? = nil,
         bootSession: @escaping () -> String? = { TrustedTime.bootSession }) {
        self.bootSession = bootSession
        self.screenTimeFilter = screenTimeFilter ?? ManagedScreenTimeFilter()
        self.customSiteStore = customSiteStore ?? CustomSiteStore.deviceLocal()
        self.resourceBundleURL = resourceBundleURL
        self.clockSource = clockSource
        let sample = clockSource()
        clockMonitor = CommitmentClock(wallAnchor: sample.wall, uptimeAnchor: sample.uptime)
        if sample.wall.timeIntervalSinceReferenceDate.isFinite { now = sample.wall }
        let isTest = NSClassFromString("XCTestCase") != nil
        self.commitmentStore = commitmentStore ?? (isTest ? MemoryCommitmentStore() : MobileCommitmentStore())
        // Tests never reach Apple's server, the real app group or the notification prompt.
        self.serverTime = serverTime ?? (isTest ? UnavailableServerTime() : AppleServerTime())
        self.shared = shared ?? MonitorSharedState.appGroup()
        self.notifications = notifications ?? (isTest ? NoNotificationPermission() : SystemNotificationPermission())
        self.readinessHistory = readinessHistory ?? .deviceLocal(key: "hisn.mobile.readiness",
            allowed: ["screentime.layer", "dns.title", "safari.title"])
        previouslyReadyContentLayers = self.readinessHistory.known
        historyStorageHealthy = self.readinessHistory.storageHealthy
        anchor = self.shared.anchors.load()
        alertsWanted = self.shared.alertsWanted
        loadCustomSites()
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
        screenTimeAuthorization = .unavailable
        #else
        authorizationObservation = AuthorizationCenter.shared.$authorizationStatus
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in self?.observeAuthorization(status) }
        #endif
    }

    /// Where the saved commitment's end stands on both clocks; nil without a commitment.
    /// No reading allowance: the app re-checks every minute, so the end is never early.
    /// An end already confirmed and saved stays passed, whatever the clocks say later.
    var commitmentTimeCheck: TrustedTime.Verdict? {
        commitment.map { session in
            session.endConfirmed ? .passed
                : TrustedTime.verdict(for: session.deadline, deviceNow: now, trustedNow: trustedNow)
        }
    }

    /// Running until both this iPhone's clock and Apple's server time have passed the end.
    /// Offline, or with the clock moved forward, it stays on.
    var commitmentActive: Bool { commitmentTimeCheck.map { $0 != .passed } ?? false }

    private func updateClock() {
        // A forward wall-clock jump cannot end a running app session early, and after a
        // relaunch or reboot the end also needs Apple's server time (see TrustedTime).
        let sample = clockSource()
        clockAssessment = clockMonitor.assessment(wall: sample.wall, uptime: sample.uptime)
        if let elapsed = clockMonitor.elapsedDate(uptime: sample.uptime) { now = elapsed }
        trustedNow = anchor?.lowerBound(uptime: sample.uptime, boot: bootSession())
        let check = commitmentTimeCheck
        if check == .unverified || check == .clockAhead { scheduleServerTimeCheck() }
        if check != lastTimeCheck {
            lastTimeCheck = check
            if check == .passed {
                confirmCommitmentEnd()
                // The end was just confirmed on both clocks: stop watching for layer loss.
                publishWatch(); updateWatchSchedule()
            }
        }
    }

    /// Saves, with the commitment in the Keychain, that its end passed on both clocks, and
    /// restates Apple's time at this moment. Without this, a reboot would bring back only
    /// the older reading (from before the deadline), and the ended commitment would run
    /// again until Apple's time could be read, restoring a filter turned off after the end.
    /// A failed save changes nothing now: this boot's reading still ends it, and the next
    /// refresh tries again.
    private func confirmCommitmentEnd() {
        guard commitmentStorageHealthy, let session = commitment, !session.endConfirmed,
              commitmentTimeCheck == .passed, let trustedNow,
              let confirmed = session.confirmingEnd(at: trustedNow) else { return }
        let sample = clockSource()
        let boot = bootSession()
        if let rebased = anchor?.rebased(uptime: sample.uptime, boot: boot) {
            anchor = rebased
            try? shared.anchors.advance(to: rebased, uptime: sample.uptime, boot: boot)
        }
        do {
            try commitmentStore.save(confirmed)
            commitment = confirmed
        } catch {}
    }

    private func loadCommitment() {
        do { commitment = try commitmentStore.load(); commitmentStorageHealthy = true }
        catch { commitmentStorageHealthy = false; errorKey = "commitment.storageFailed" }
    }

    /// Starts from Apple's time when the device clock is behind it, so a clock set back
    /// before starting cannot shorten the commitment. Without a reading made in this boot
    /// Hisn reads Apple's time first; if it cannot, the commitment does not start and
    /// `commitmentStartNote` says why.
    func startCommitment(days: Int) async {
        updateClock()
        guard !busy, commitmentStorageHealthy, !commitmentActive, [1, 7, 30, 90].contains(days) else { return }
        commitmentStartNote = nil
        if !hasReadingThisBoot {
            busy = true
            commitmentStartNote = "commitment.checkingTime"
            await checkServerTime()
            busy = false
            commitmentStartNote = nil
            updateClock()
            guard hasReadingThisBoot else { commitmentStartNote = "commitment.startNeedsTime"; return }
            guard !busy, commitmentStorageHealthy, !commitmentActive else { return }
        }
        // Record what iOS reports now, not an older reading: this is the person's choice
        // of Hisn's Screen Time filter for the commitment, and only a chosen filter is restored.
        readScreenTimeState()
        // A clock set back before starting must not shorten the commitment in real time.
        let start = max(now, trustedNow ?? now)
        let session = CommitmentPolicy.Session(startedAt: start,
                                               deadline: start.addingTimeInterval(Double(days) * 86400),
                                               screenTimeFilter: screenTimeConfigured)
        do {
            try commitmentStore.save(session)
            commitment = session
            errorKey = nil
        } catch { commitmentStorageHealthy = false; errorKey = "commitment.storageFailed"; return }
        updateClock()
        publishWatch()
        updateWatchSchedule()
    }

    /// Apple's time was read in this boot, so `trustedNow` is exact (within network delay).
    var hasReadingThisBoot: Bool {
        anchor?.age(uptime: clockSource().uptime, boot: bootSession()) != nil
    }

    /// The person turned Hisn's filter on during the commitment (only they can): record
    /// it with the commitment so a relaunch restores it. It is never recorded as off.
    private func recordScreenTimeChoice() {
        guard let session = commitment, session.screenTimeFilter != true else { return }
        let next = session.choosingScreenTimeFilter()
        do {
            try commitmentStore.save(next)
            commitment = next
            publishWatch()
        } catch { commitmentStorageHealthy = false; errorKey = "commitment.storageFailed" }
    }

    // MARK: - Apple server time

    /// Reads Apple's server time and keeps it as the shared anchor, for the commitment
    /// end, the budget day the monitor held, and the monitor's own offline checks.
    /// A failure changes nothing: the commitment stays on and shields stay.
    func checkServerTime() async {
        // One reading at a time; a second caller waits for the one in flight.
        if serverCheck == nil {
            serverCheck = Task { [weak self] in await self?.readServerTime() }
        }
        await serverCheck?.value
        serverCheck = nil
    }

    private func readServerTime() async {
        checkingServerTime = true
        defer { checkingServerTime = false }
        lastServerAttempt = clockSource().uptime
        let reading = await serverTime.currentTime()
        let uptime = clockSource().uptime
        guard let reading, reading.timeIntervalSinceReferenceDate.isFinite, uptime.isFinite, uptime >= 0 else {
            serverTimeFailed = true
            updateClock()
            return
        }
        // In a boot that already has a reading, a new one may only lower it (see reconcile).
        let next = TrustedTime.reconcile(reading: reading, uptime: uptime, boot: bootSession(), with: anchor)
        anchor = next
        try? shared.anchors.save(next)
        serverTimeFailed = false
        updateClock()
        settleHeldBudgetDay()
    }

    /// Automatic checks retry at most once a minute; the person can always check now.
    private func scheduleServerTimeCheck() {
        guard serverCheck == nil else { return }
        let uptime = clockSource().uptime
        if let last = lastServerAttempt, uptime >= last, uptime - last < Self.serverRetryInterval { return }
        lastServerAttempt = uptime
        Task { await checkServerTime() }
    }

    static let serverRetryInterval: TimeInterval = 60
    /// While a commitment or daily budget is on, a reading older than this (or from before
    /// a reboot) is refreshed so the monitor can settle the next day offline.
    static let anchorRefreshInterval: TimeInterval = 6 * 3600

    /// Apple's time is read only when something depends on it: a commitment that has not
    /// ended on both clocks, a budget day the monitor held, or a daily budget.
    var needsServerTime: Bool {
        let age = anchor.flatMap { $0.age(uptime: clockSource().uptime, boot: bootSession()) }
        let stale = age.map { $0 > Self.anchorRefreshInterval } ?? true
        if let check = commitmentTimeCheck, check != .passed {
            if check != .notYet { return true }
            if stale { return true }
        }
        if shared.hasHeldBudgetDay { return true }
        return stale && shared.dailyBudgetConfigured
    }

    /// Finishes a new budget day the monitor could not confirm, with the same both-clocks
    /// rule. Unreadable rules or markers keep the shield.
    private func settleHeldBudgetDay() {
        switch shared.heldDayAction(deviceNow: now, trustedNow: trustedNow) {
        case .none: return
        case .forget: shared.clearHeldBudgetDay()
        case .clearShield:
            #if !targetEnvironment(simulator)
            guard AuthorizationCenter.shared.authorizationStatus == .approved else { return }
            ManagedSettingsStore(named: .init(AppUsageConfiguration.budgetStoreName)).clearAllSettings()
            #endif
            shared.clearHeldBudgetDay()
            // A day over on both clocks is settled, so no later check shields it again.
            shared.settleReachedDay(deviceNow: now, trustedNow: trustedNow)
        }
    }

    // MARK: - Layer alerts

    enum LayerAlertState: Equatable { case off, unchecked, on, notAllowed, notScheduled }

    /// iOS refused the monitor's watch schedule, so alerts would come only at budget times.
    @Published private(set) var watchRefused = false

    var layerAlerts: LayerAlertState {
        guard alertsWanted else { return .off }
        switch notificationStatus {
        case nil: return .unchecked
        case .allowed?: return watchRefused ? .notScheduled : .on
        case .denied?, .notDetermined?: return .notAllowed
        }
    }

    /// Only after the person asked, with Hisn's explanation shown first. iOS shows its
    /// own permission prompt at most once; the monitor extension never asks.
    func enableLayerAlerts() async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        _ = await notifications.request()
        notificationStatus = await notifications.status()
        setAlertsWanted(true)
    }

    /// Alerts are notices, not protection: they can be turned off at any time.
    func disableLayerAlerts() {
        setAlertsWanted(false)
    }

    private func setAlertsWanted(_ wanted: Bool) {
        alertsWanted = wanted
        shared.alertsWanted = wanted
        publishWatch()
        updateWatchSchedule()
    }

    /// What the monitor may check, written for it to read. Nothing while no commitment
    /// runs; an unreadable commitment leaves the last watch in place.
    private func publishWatch() {
        guard commitmentStorageHealthy else { return }
        var watch: LayerAlertPolicy.Watch?
        if let session = commitment, commitmentActive {
            watch = .init(startedAt: session.startedAt, deadline: session.deadline,
                          webFilter: session.screenTimeFilter == true,
                          alertsOn: alertsWanted && notificationStatus == .allowed,
                          texts: Self.alertTexts(languageChoice: MonitorSharedState.languageChoice))
        }
        shared.saveWatch(watch)
    }

    /// Alert text in the language the person chose in Hisn (or the app's system language).
    nonisolated static func alertTexts(languageChoice: String?,
                                       bundle: Bundle = .main) -> [String: LayerAlertPolicy.Text] {
        let language = ["ar", "en"].contains(languageChoice ?? "") ? languageChoice!
            : (bundle.preferredLocalizations.first ?? "en")
        let localized = bundle.path(forResource: language, ofType: "lproj").flatMap(Bundle.init(path:)) ?? bundle
        var texts: [String: LayerAlertPolicy.Text] = [:]
        for layer in LayerAlertPolicy.Layer.allCases {
            let title = localized.localizedString(forKey: layer.titleKey, value: nil, table: nil)
            let body = localized.localizedString(forKey: layer.bodyKey, value: nil, table: nil)
            // A missing translation is no alert, never a raw key.
            guard title != layer.titleKey, body != layer.bodyKey else { continue }
            texts[layer.rawValue] = .init(title: title, body: body)
        }
        return texts
    }

    /// iOS wakes the monitor three times a day during the commitment, only while the
    /// person wants alerts and Screen Time access is granted. Never requests access.
    private func updateWatchSchedule() {
        #if !targetEnvironment(simulator)
        guard commitmentStorageHealthy else { return }
        let center = DeviceActivityCenter()
        let name = DeviceActivityName(LayerAlertPolicy.watchActivity)
        let wanted = commitmentActive && alertsWanted && notificationStatus == .allowed
            && AuthorizationCenter.shared.authorizationStatus == .approved
        let running = center.activities.contains(name)
        if wanted, !running {
            let start = DateComponents(hour: 0, minute: 0), end = DateComponents(hour: 23, minute: 59, second: 59)
            do {
                try center.startMonitoring(name, during: DeviceActivitySchedule(intervalStart: start,
                    intervalEnd: end, repeats: true, warningTime: DateComponents(hour: 12)))
                watchRefused = false
            } catch {
                // Without the midday warning iOS still wakes the monitor at both ends of the day.
                do {
                    try center.startMonitoring(name, during: DeviceActivitySchedule(intervalStart: start,
                        intervalEnd: end, repeats: true))
                    watchRefused = false
                } catch { watchRefused = true }
            }
        } else if !wanted, running {
            center.stopMonitoring([name])
        }
        if !wanted || running { watchRefused = false }
        #endif
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
            publishWatch()
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
            screenTimeFilter.filter = desiredFilter
            readScreenTimeState()
        } catch { errorKey = "screentime.failed" }
        #endif
    }

    private func readScreenTimeState() {
        let authorized = screenTimeFilter.authorized
        let present = Self.isHisnFilter(screenTimeFilter.filter)
        // Hisn's filter on during the commitment was turned on by the person (Hisn cannot
        // turn it off until the end): record that choice with the commitment.
        if authorized, present, commitmentActive, commitmentStorageHealthy {
            recordScreenTimeChoice()
        }
        if CommitmentPolicy.shouldRestoreScreenTimeFilter(session: commitment, active: commitmentActive,
            commitmentReadable: commitmentStorageHealthy, authorized: authorized, filterPresent: present) {
            // Reassert only the filter the person chose for this commitment; do not touch other
            // stores or request new authorization. Revoked OS permission cannot be repaired here.
            if customSitesStorageHealthy || screenTimeFilter.filter == nil {
                screenTimeFilter.filter = desiredFilter
            }
        } else if authorized, customSitesStorageHealthy, present, screenTimeFilter.filter != desiredFilter {
            // The person already consented to this filter; keep their saved sites in it.
            // An unreadable list never replaces the sites the OS already holds.
            screenTimeFilter.filter = desiredFilter
        }
        let applied = screenTimeFilter.filter
        screenTimeConfigured = authorized && Self.isHisnFilter(applied)
        customSitesApplied = screenTimeConfigured && customSitesStorageHealthy && applied == desiredFilter
        rememberConfiguredLayers()
    }

    /// Apple's adult-content filter plus the person's saved sites.
    private var desiredFilter: WebContentSettings.FilterPolicy {
        .auto(Set(customSites.map { WebDomain(domain: $0) }))
    }

    /// Hisn only ever writes `.auto`; any saved site list still counts as Hisn's filter.
    nonisolated static func isHisnFilter(_ policy: WebContentSettings.FilterPolicy?) -> Bool {
        if case .auto = policy { return true }
        return false
    }

    private func loadCustomSites() {
        do { customSites = try customSiteStore.load(); customSitesStorageHealthy = true }
        catch { customSitesStorageHealthy = false; customSitesApplied = false }
    }

    /// Adding a site only tightens protection, so it stays available during a commitment.
    func addCustomSite(_ raw: String) {
        updateClock()
        loadCustomSites()
        guard !busy, customSitesStorageHealthy else { customSiteMessage = "sites.failed"; return }
        switch CustomSitePolicy.adding(raw, to: customSites) {
        case .invalid: customSiteMessage = "sites.invalid"
        case .duplicate: customSiteMessage = "sites.duplicate"
        case .full: customSiteMessage = "sites.full"
        case .added(let next): saveCustomSites(next, success: "sites.added")
        }
    }

    /// Removal waits for the commitment to end; an unreadable commitment counts as running.
    func removeCustomSite(_ host: String) {
        updateClock()
        loadCustomSites()
        guard !busy, customSitesStorageHealthy else { customSiteMessage = "sites.failed"; return }
        let next = customSites.filter { $0 != host }
        guard next != customSites else { return }
        guard CustomSitePolicy.permits(current: customSites, proposed: next,
                                       committed: commitmentActive || !commitmentStorageHealthy) else {
            customSiteMessage = "sites.locked"; return
        }
        saveCustomSites(next, success: "sites.removed")
    }

    var customSitesLocked: Bool { commitmentActive || !commitmentStorageHealthy }

    private func saveCustomSites(_ next: [String], success: String) {
        do {
            try customSiteStore.save(next)
            customSites = next
            customSiteMessage = success
        } catch {
            customSitesStorageHealthy = false
            customSiteMessage = "sites.failed"
            return
        }
        // Applies the list only if the person's filter is already on; never enables it.
        readScreenTimeState()
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
        screenTimeFilter.filter = nil
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
            storageHealthy: commitmentStorageHealthy && historyStorageHealthy,
            screenTimeAccessRemoved: screenTimeState == .accessRemoved)
    }

    /// Separates access turned off in Settings from a filter that was never set up.
    var screenTimeState: MobileProtectionPolicy.ScreenTimeState {
        MobileProtectionPolicy.screenTimeState(authorization: screenTimeAuthorization,
            filterConfigured: screenTimeConfigured,
            previouslyConfigured: previouslyReadyContentLayers.contains("screentime.layer"))
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
        screenTimeAuthorization = value
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
        loadCommitment()
        anchor = shared.anchors.load() ?? anchor
        updateClock()
        // An end confirmed in this boot whose save failed earlier: try again.
        confirmCommitmentEnd()
        loadCustomSites()
        settleHeldBudgetDay()
        notificationStatus = await notifications.status()
        // Never blocks the refresh: offline, the commitment simply stays on.
        if needsServerTime { Task { await checkServerTime() } }
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
        publishWatch()
        updateWatchSchedule()
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

// MARK: - Screen Time filter access

/// Hisn's named Screen Time store and the access iOS grants to it. Reading never asks
/// for access; only `enablePersonalScreenTime` does, after the person confirms.
protocol ScreenTimeFilterAccess: AnyObject {
    var authorized: Bool { get }
    var filter: WebContentSettings.FilterPolicy? { get set }
}

final class ManagedScreenTimeFilter: ScreenTimeFilterAccess {
    private let store = ManagedSettingsStore(named: .init("hisn.self-control"))
    var authorized: Bool {
        #if targetEnvironment(simulator)
        return false
        #else
        return AuthorizationCenter.shared.authorizationStatus == .approved
        #endif
    }
    var filter: WebContentSettings.FilterPolicy? {
        get {
            #if targetEnvironment(simulator)
            return nil
            #else
            return store.webContent.blockedByFilter
            #endif
        }
        set {
            #if !targetEnvironment(simulator)
            store.webContent.blockedByFilter = newValue
            #endif
        }
    }
}

// MARK: - Notification permission

enum NotificationStatus: Equatable { case notDetermined, allowed, denied }

/// Asked only from the app, after the person taps to turn alerts on.
protocol NotificationPermission {
    func status() async -> NotificationStatus
    func request() async -> Bool
}

struct SystemNotificationPermission: NotificationPermission {
    func status() async -> NotificationStatus {
        switch await UNUserNotificationCenter.current().notificationSettings().authorizationStatus {
        case .authorized, .provisional, .ephemeral: return .allowed
        case .denied: return .denied
        default: return .notDetermined
        }
    }
    func request() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])) ?? false
    }
}

/// Tests never show the system prompt.
struct NoNotificationPermission: NotificationPermission {
    func status() async -> NotificationStatus { .notDetermined }
    func request() async -> Bool { false }
}
