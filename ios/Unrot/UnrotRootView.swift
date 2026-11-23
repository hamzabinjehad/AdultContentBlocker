import DeviceActivity
import FamilyControls
import ManagedSettings
import SwiftUI
import WidgetKit

/// Unrot's four places — Home, Shield, Progress, You — on a floating pill bar,
/// with Breathe always one tap away. Protection itself is Hisn's proven engine
/// (ProtectionController, AppUsageController); this file is Unrot's face on it.
struct UnrotRootView: View {
    @ObservedObject var protection: ProtectionController
    @Binding var language: String
    @StateObject private var store = UnrotStore.appGroup()
    @StateObject private var usage = AppUsageController()
    @StateObject private var vault = PasscodeVaultController()
    @StateObject private var lockDevice = LockConfirmationDevice()
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage(MobileProtectionPolicy.lockAttestationKey) private var lockAttestationData = Data()

    enum Tab: Int, CaseIterable { case home, shield, progress, you }
    enum Sheet: String, Identifiable { case checkIn, hard, instead, notes, focus, more, info, rule, lock; var id: String { rawValue } }

    @State private var tab: Tab = .home
    @State private var sheet: Sheet?
    @State private var breathing = false
    @State private var pausePrompt: UnrotPausePrompt?
    @State private var stageUp: StageUp?
    @State private var notesStart: Date?

    var body: some View {
        Group {
            if store.onboarded {
                main
            } else {
                UnrotOnboarding(store: store, protection: protection,
                                ruleSaved: usage.configuration != nil,
                                onChooseApps: { sheet = .rule }, onLock: { sheet = .lock },
                                locked: facts.on[.lock] == true)
            }
        }
        .preferredColorScheme(.dark)
        .sheet(item: $sheet) { which in sheetView(which) }
        .fullScreenCover(isPresented: $breathing) {
            UnrotBreatheView(store: store) { breathing = false }
        }
        .fullScreenCover(item: $stageUp) { up in
            UnrotStageUpView(stage: up.stage, givenBack: store.givenBackMinutes, day: store.day()) { stageUp = nil }
        }
        .fullScreenCover(item: $pausePrompt) { prompt in
            // The shield's "pause first" lands here, exactly as in Hisn.
            PauseRuleGrantView(usage: usage, appName: prompt.request.appName) { pausePrompt = nil }
        }
        .onOpenURL { url in
            guard let link = UnrotLink(url: url), store.onboarded else { return }
            sheet = nil
            switch link {
            case .home: tab = .home
            case .breathe: tab = .home; breathing = true
            case .notes: tab = .home; sheet = .notes
            case .checkin: tab = .home; sheet = .checkIn
            case .focus: tab = .home; sheet = .focus
            }
        }
        .onAppear {
            refresh()
            #if DEBUG
            // Screenshots: `-unrot-open notes` opens one sheet at launch.
            let args = ProcessInfo.processInfo.arguments
            if let i = args.firstIndex(of: "-unrot-open"), args.indices.contains(i + 1) {
                if args[i + 1] == "stageUp" {
                    stageUp = StageUp(stage: GlassStage.stage(givenBackMinutes: store.givenBackMinutes))
                } else {
                    sheet = Sheet(rawValue: args[i + 1])
                }
            }
            #endif
        }
        .onChange(of: scenePhase) { phase in if phase == .active { refresh() } }
        .onChange(of: protection.checkedAt) { _ in usage.refresh(trustedNow: protection.trustedNow) }
        .onChange(of: usage.configuration?.selection) { _ in UnrotScreenTime.watchFeeds(usage.configuration) }
        .onChange(of: store.crackedAt) { _ in publishWidget() }
        .onChange(of: store.sealedAt) { _ in publishWidget() }
        .onChange(of: store.brokenAt) { _ in publishWidget() }
        .onChange(of: scenePhase) { phase in if phase == .background { publishWidget() } }
    }

    // MARK: - Main

    private var main: some View {
        ZStack(alignment: .bottom) {
            UBackdrop(mood: backdropMood)
            TabView(selection: $tab) {
                NavigationStack { home.toolbar(.hidden, for: .navigationBar).toolbar(.hidden, for: .tabBar) }.tag(Tab.home)
                NavigationStack { shield.toolbar(.hidden, for: .navigationBar).toolbar(.hidden, for: .tabBar) }.tag(Tab.shield)
                NavigationStack { progress.toolbar(.hidden, for: .navigationBar).toolbar(.hidden, for: .tabBar) }.tag(Tab.progress)
                NavigationStack { you.toolbar(.hidden, for: .navigationBar).toolbar(.hidden, for: .tabBar) }.tag(Tab.you)
            }
            .toolbar(.hidden, for: .tabBar)
            .modifier(ClearTabBackground())
            statusScrim
            if store.tourSeen || tab != .home { dock }
        }
    }

    /// The status bar never sits on top of scrolled text: the room darkens under it.
    private var statusScrim: some View {
        GeometryReader { geo in
            VStack(spacing: 0) {
                LinearGradient(stops: [.init(color: UPalette.base, location: 0),
                                       .init(color: UPalette.base.opacity(0.85), location: 0.6),
                                       .init(color: UPalette.base.opacity(0), location: 1)],
                               startPoint: .top, endPoint: .bottom)
                    .frame(height: geo.safeAreaInsets.top + 22)
                Spacer(minLength: 0)
            }
            .ignoresSafeArea()
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private var backdropMood: UBackdrop.Mood {
        let glass = store.glass(now: protection.now)
        if glass.look == .cracked || glass.look == .broken { return .wilt }
        if facts.quietNow { return .night }
        return .day
    }

    private var home: some View {
        UnrotHomeView(store: store, shield: facts, now: protection.now,
                      onShield: { tab = .shield }, onTool: open, onInfo: { sheet = .info })
            .background(UBackdrop(mood: backdropMood))
    }

    private var shield: some View {
        UnrotShieldView(protection: protection, usage: usage, vault: vault, lockDevice: lockDevice,
                        facts: facts, onAddRule: { sheet = .rule })
            .background(UBackdrop(mood: facts.checked && !facts.openPanes.isEmpty ? .warn : .day))
    }

    private var progress: some View {
        UnrotProgressView(store: store, facts: facts, now: protection.now,
                          onOpenNotes: { day in notesStart = day; sheet = .notes },
                          onCheckIn: { sheet = .checkIn })
            .background(UBackdrop())
    }

    private var you: some View {
        UnrotYouView(store: store, protection: protection, usage: usage, vault: vault,
                     lockDevice: lockDevice, language: $language, onTool: open, onHome: { tab = .home })
            .background(UBackdrop())
    }

    /// Breathe floats over Home; the pill bar is under everything.
    private var dock: some View {
        VStack(spacing: 12) {
            HStack(spacing: 4) {
                ForEach(Tab.allCases, id: \.self) { item in
                    Button {
                        if tab != item { UISelectionFeedbackGenerator().selectionChanged() }
                        tab = item
                    } label: {
                        VStack(spacing: 3) {
                            Image(systemName: icon(item)).font(.system(size: 18, weight: .semibold))
                            Text(uKey("u.tab.", ["home", "shield", "progress", "you"][item.rawValue]))
                                .font(.caption2.weight(tab == item ? .bold : .medium))
                        }
                        .foregroundStyle(tab == item ? Color(red: 0.07, green: 0.05, blue: 0.02) : UPalette.dim)
                        .frame(maxWidth: .infinity, minHeight: 52)
                        .background(tab == item ? Color.white : .clear, in: Capsule())
                        .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(tab == item ? [.isSelected, .isButton] : .isButton)
                }
            }
            .padding(6)
            .background(UPalette.navBar, in: Capsule())
            .overlay { Capsule().strokeBorder(Color.white.opacity(0.08)) }
            .shadow(color: .black.opacity(0.5), radius: 16, y: 6)
            .frame(maxWidth: 520)
            // Like iOS's own tab bar: labels stop growing past a size, so they never wrap.
            .dynamicTypeSize(...DynamicTypeSize.xLarge)
        }
        .padding(.horizontal, 14)
        .padding(.bottom, 6)
        .padding(.top, 28)
        .background(alignment: .bottom) {
            // Content fades out behind the bar instead of sitting under it.
            LinearGradient(stops: [.init(color: UPalette.base.opacity(0), location: 0),
                                   .init(color: UPalette.base, location: 0.45)],
                           startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()
                .allowsHitTesting(false)
        }
    }

    private func icon(_ tab: Tab) -> String { ["house", "shield", "chart.bar", "person"][tab.rawValue] }

    // MARK: - Tools and sheets

    private func open(_ tool: UnrotTool) {
        switch tool {
        case .breathe: breathing = true
        case .focus: sheet = .focus
        case .notes: notesStart = nil; sheet = .notes
        case .instead: sheet = .instead
        case .checkIn: sheet = .checkIn
        case .hardMoment: sheet = .hard
        case .rules: sheet = tab == .home ? .more : .rule
        case .lock: sheet = .lock
        }
    }

    @ViewBuilder private func sheetView(_ which: Sheet) -> some View {
        switch which {
        case .checkIn: UnrotCheckInSheet(store: store)
        case .hard: UnrotHardMomentSheet(store: store) { DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { breathing = true } }
        case .instead:
            UnrotInsteadSheet(store: store, canClose: usage.configuration != nil,
                              onChooseApps: { sheet = .rule }) { startFocus(minutes: 15) }
        case .notes: UnrotNotesSheet(store: store, startDay: notesStart)
        case .focus:
            UnrotFocusSheet(store: store, canClose: usage.configuration != nil,
                            onChooseApps: { sheet = .rule },
                            onStart: { startFocus(minutes: $0) }, onEnd: endFocus)
        case .more:
            UnrotMoreSheet { tool in
                if tool == .rules { tab = .shield; sheet = .rule } else { open(tool) }
            }
        case .info: UnrotGivenBackInfo()
        case .lock:
            NavigationStack {
                UnrotLockView(protection: protection, vault: vault, lockDevice: lockDevice)
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) { Button("u.done") { sheet = nil } }
                    }
            }
            .preferredColorScheme(.dark)
        case .rule:
            NavigationStack {
                RuleEditorView(protection: protection, usage: usage, guardMode: false)
            }
            .preferredColorScheme(.dark)
        }
    }

    private func startFocus(minutes: Int) -> Bool {
        guard let config = usage.configuration,
              let until = UnrotScreenTime.startFocus(config.selection, minutes: minutes) else { return false }
        store.startedFocus(until: until, minutes: max(15, minutes))
        return true
    }

    private func endFocus() {
        UnrotScreenTime.endFocus()
        store.endedFocus()
    }

    // MARK: - State

    private func refresh() {
        store.reload()
        usage.refresh(trustedNow: protection.trustedNow)
        if store.focusUntil > 0 && !store.focusActive { endFocus() }
        UnrotScreenTime.watchFeeds(usage.configuration)
        publishWidget()
        // New lines for the coming week, at the person's current times.
        if UserDefaults.standard.bool(forKey: ReminderScheduler.enabledKey) {
            let when = store.answers.when
            Task { await ReminderScheduler.reschedule(on: true, when: when) }
        }
        if pausePrompt == nil, !breathing, let request = usage.takePauseRequest() {
            pausePrompt = UnrotPausePrompt(request: request)
        }
        if store.onboarded, store.tourSeen, stageUp == nil, pausePrompt == nil, let stage = store.stageToCelebrate() {
            stageUp = StageUp(stage: stage)
        }
    }

    /// How hard Unrot is to delete, never higher than it can show.
    private var removalLevel: MobileProtectionPolicy.LockLevel {
        MobileProtectionPolicy.removalProtection(
            removal: protection.removal,
            attestation: MobileProtectionPolicy.LockAttestation(storage: lockAttestationData),
            vault: vault.facts, now: protection.now, deviceMarker: lockDevice.marker).level
    }

    /// The Home Screen widget's copy of growth and the glass (nothing private).
    private func publishWidget() {
        let snapshot = WidgetSnapshot(givenBack: store.givenBackMinutes, day: store.day(),
                                      crackedAt: store.crackedAt, sealedAt: store.sealedAt, brokenAt: store.brokenAt,
                                      sealedDay: store.sealedDay, night: facts.quietNow, updated: Date())
        guard let data = try? JSONEncoder().encode(snapshot),
              let shared = UserDefaults(suiteName: Edition.appGroup),
              shared.data(forKey: WidgetSnapshot.key) != data else { return }
        shared.set(data, forKey: WidgetSnapshot.key)
        WidgetCenter.shared.reloadAllTimelines()
    }

    private var facts: UnrotShieldFacts {
        var f = UnrotShieldFacts()
        f.checked = protection.checkedAt != nil
        f.on = [.feeds: usage.configuration != nil,
                .safari: protection.safariConfigured,
                .everyApp: protection.dns == .enabled,
                .screenTime: protection.screenTimeConfigured,
                .lock: UnrotLockFlow.isLocked(removalLevel)]
        f.setupLeft = protection.setupAssessment.issues.count
        if let config = usage.configuration, config.mode == .quietHours,
           config.windowActive(deviceNow: protection.now, trustedNow: protection.trustedNow) {
            f.quietNow = true
            f.quietEnds = RuleSummary.time(config.windowEnd)
        }
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-unrot-night") { f.quietNow = true; f.quietEnds = "07:00" }
        #endif
        f.feedWatched = usage.configuration != nil
        f.listCount = protection.listCount
        f.customSites = protection.customSites.count
        f.ruleApps = usage.configuration.map { $0.selection.applicationTokens.count + $0.selection.categoryTokens.count } ?? 0
        return f
    }
}

private struct StageUp: Identifiable {
    let stage: Int
    var id: Int { stage }
}

private struct UnrotPausePrompt: Identifiable {
    let id = UUID()
    let request: PauseRule.Request
}

/// Lets the room's light show through the TabView on every iOS version.
private struct ClearTabBackground: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 16.4, *) {
            content.toolbarBackground(.hidden, for: .tabBar)
        } else {
            content
        }
    }
}

/// The two Screen Time pieces Unrot adds on top of Hisn's rules. Both use the
/// apps the person already chose for their rule; neither reads any usage data.
enum UnrotScreenTime {
    static let focusActivity = "unrot.focus"
    private static var focusStore: ManagedSettingsStore { ManagedSettingsStore(named: .init(focusActivity)) }

    /// Closes the chosen apps now; the monitor lifts it at the end. iOS's
    /// shortest schedule is 15 minutes, so shorter asks become 15.
    static func startFocus(_ selection: FamilyActivitySelection, minutes: Int) -> Date? {
        #if targetEnvironment(simulator)
        return nil
        #else
        let now = Date()
        let length = max(15, minutes)
        let end = now.addingTimeInterval(TimeInterval(length * 60))
        let cal = Calendar.current
        let schedule = DeviceActivitySchedule(
            intervalStart: cal.dateComponents([.hour, .minute, .second], from: now),
            intervalEnd: cal.dateComponents([.hour, .minute, .second], from: end),
            repeats: false)
        do {
            let center = DeviceActivityCenter()
            center.stopMonitoring([.init(focusActivity)])
            try center.startMonitoring(.init(focusActivity), during: schedule)
        } catch {
            return nil
        }
        let store = focusStore
        store.shield.applications = selection.applicationTokens.isEmpty ? nil : selection.applicationTokens
        store.shield.applicationCategories = selection.categoryTokens.isEmpty ? nil : .specific(selection.categoryTokens)
        return end
        #endif
    }

    static func endFocus() {
        #if !targetEnvironment(simulator)
        focusStore.clearAllSettings()
        DeviceActivityCenter().stopMonitoring([.init(focusActivity)])
        #endif
        UserDefaults(suiteName: Edition.appGroup)?.set(0, forKey: UnrotStore.Key.focusUntil)
    }

    /// One daily activity with an event per step for the rule's apps. Restarted
    /// only when the apps change, so a running day's count is never reset.
    static func watchFeeds(_ config: AppUsageConfiguration?) {
        #if !targetEnvironment(simulator)
        let center = DeviceActivityCenter()
        let name = DeviceActivityName(FeedWatch.activity)
        guard let config else {
            center.stopMonitoring([name])
            UserDefaults.standard.removeObject(forKey: "unrot.feed.watched")
            return
        }
        guard let data = try? JSONEncoder().encode(config.selection) else { return }
        let signature = data.base64EncodedString()
        if UserDefaults.standard.string(forKey: "unrot.feed.watched") == signature,
           center.activities.contains(name) { return }
        var events: [DeviceActivityEvent.Name: DeviceActivityEvent] = [:]
        for step in FeedWatch.steps {
            events[.init(FeedWatch.eventName(step))] = DeviceActivityEvent(
                applications: config.selection.applicationTokens,
                categories: config.selection.categoryTokens,
                webDomains: config.selection.webDomainTokens,
                threshold: DateComponents(minute: step))
        }
        let day = DeviceActivitySchedule(intervalStart: DateComponents(hour: 0, minute: 0),
                                         intervalEnd: DateComponents(hour: 23, minute: 59), repeats: true)
        do {
            center.stopMonitoring([name])
            try center.startMonitoring(name, during: day, events: events)
            UserDefaults.standard.set(signature, forKey: "unrot.feed.watched")
        } catch {
            UserDefaults.standard.removeObject(forKey: "unrot.feed.watched")
        }
        #endif
    }
}
