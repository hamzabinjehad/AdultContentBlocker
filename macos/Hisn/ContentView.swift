import SwiftUI

/// Main window.
///
/// Organised around the three questions a person opens it to answer — *am I
/// protected?*, *what is being blocked?*, *what can I change?* — as pages in a
/// sidebar: Overview, Setup, Blocking Rules, Lock, Settings. Each common task
/// has one obvious home, and the window resizes so longer text has room.
///
/// The interface has one job beyond starting a lock: make the state honest.
/// A self-control tool that *looks* armed while the filter is off is worse than
/// no tool, because the person stops being careful. So the Overview reports
/// what is actually enforced (`ProtectionStatus`), separately from whether a
/// lock is recorded (`LockStatus`), and never blends the two into one word.
struct ContentView: View {
    @StateObject private var lock = LockManager.shared
    @StateObject private var filter = FilterController.shared
    @ObservedObject private var navigation = AppNavigation.shared
    @State private var activeSheet: ActiveSheet?
    @AppStorage("hisn.welcome.completed") private var welcomeCompleted = false

    private enum ActiveSheet: String, Identifiable {
        case siteCheck, privateBrowsing, welcome
        var id: String { rawValue }
    }

    private var page: Page? { navigation.page }

    /// The pinned Lock action bar reads the same status the Overview does.
    private var lockPageStatus: ProtectionStatus {
        ProtectionStatus(ProtectionEvidence.live(filter: filter.availability,
                                                    authority: FilterSync.shared.status))
    }

    enum Page: String, CaseIterable, Identifiable {
        case overview, setup, rules, lock, settings

        var id: String { rawValue }

        var title: LocalizedStringKey {
            switch self {
            case .overview: return "Overview"
            case .setup:    return "Protected Setup"
            case .rules:    return "Blocking Rules"
            case .lock:     return "Lock"
            case .settings: return "Settings"
            }
        }

        var systemImage: String {
            switch self {
            case .overview: return "checkmark.shield"
            case .setup:    return "checklist"
            case .rules:    return "list.bullet.rectangle"
            case .lock:     return "lock"
            case .settings: return "gearshape"
            }
        }
        var subtitle: LocalizedStringKey {
            switch self {
            case .overview: return "design.page.overview"
            case .setup: return "design.page.setup"
            case .rules: return "design.page.rules"
            case .lock: return "design.page.lock"
            case .settings: return "design.page.settings"
            }
        }
    }

    private enum SidebarGroup: String, CaseIterable {
        case protection, commitment, app

        var title: LocalizedStringKey {
            switch self {
            case .protection: return "Protection"
            case .commitment: return "Commitment"
            case .app:        return "App"
            }
        }

        var pages: [Page] {
            switch self {
            case .protection: return [.overview, .setup, .rules]
            case .commitment: return [.lock]
            case .app:        return [.settings]
            }
        }
    }

    var body: some View {
        NavigationSplitView {
            VStack(spacing: 0) {
                brandHeader
                VStack(alignment: .leading, spacing: 18) {
                    ForEach(SidebarGroup.allCases, id: \.self) { group in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(group.title)
                                .font(.system(size: 9.5, weight: .semibold))
                                .textCase(.uppercase)
                                .tracking(0.9)
                                .foregroundStyle(HnPalette.faint)
                                .padding(.horizontal, 10)
                                .padding(.bottom, 2)
                            ForEach(group.pages) { page in
                                SidebarRow(page: page,
                                           selected: (navigation.page ?? .overview) == page,
                                           lockText: page == .lock && lock.isLocked ? lock.remainingDescription : nil) {
                                    navigation.page = page
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 8)
                .padding(.top, 4)
                Spacer(minLength: 0)

                SidebarStatus(lock: lock, filter: filter) { navigation.page = .overview }
                    .padding(.horizontal, 8)
                    .padding(.bottom, 10)
            }
            .background(HnPalette.sidebar)
            .navigationSplitViewColumnWidth(min: 212, ideal: 232, max: 268)
        } detail: {
            HnCanvas {
                ScrollView {
                    Group {
                        switch page ?? .overview {
                        case .overview:
                            OverviewPage(lock: lock, filter: filter,
                                         openSiteCheck: { activeSheet = .siteCheck }) { navigation.page = $0 }
                        case .setup:
                            SetupPage(filter: filter) { navigation.page = $0 }
                        case .rules:
                            RulesPage(isLocked: lock.isLocked,
                                      managed: ManagedMode.current(),
                                      openSiteCheck: { activeSheet = .siteCheck })
                        case .lock:
                            LockPage(lock: lock, filter: filter)
                        case .settings:
                            SettingsPage(isLocked: lock.isLocked, managed: ManagedMode.current())
                        }
                    }
                    .hnColumn()
                    .padding(.horizontal, 32)
                    .padding(.top, 28)
                    .padding(.bottom, 40)
                }
                .id(page ?? .overview)
                .scrollContentBackground(.hidden)
                .background(HnPalette.canvas)
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    if page == .lock && !lock.isLocked {
                        LockActionBar(lock: lock, status: lockPageStatus)
                    }
                }
            }
            .navigationTitle("Hisn")
            .toolbar {
                ToolbarItemGroup(placement: .primaryAction) {
                    Button { activeSheet = .siteCheck } label: {
                        Image(systemName: "magnifyingglass")
                    }
                    .help("Check a website")

                    if page != .lock {
                        let title: LocalizedStringKey = lock.isLocked ? "Manage lock" : "Start a lock…"
                        Button { navigation.page = .lock } label: {
                            Label(title, systemImage: lock.isLocked ? "lock.fill" : "lock.open")
                                .labelStyle(.titleAndIcon)
                                .font(.system(size: 12, weight: .semibold))
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(HnPalette.accent)
                        .help(title)
                    }
                }
            }
        }
        .tint(HnPalette.accent)
        .frame(minWidth: 760, minHeight: 560)
        .sheet(item: $activeSheet) { sheet in
            switch sheet {
            case .siteCheck:
                SiteCheckSheet(isLocked: lock.isLocked,
                               strict: lock.isLocked && lock.state.mode == "strict")
            case .privateBrowsing:
                PrivateBrowsingSheet()
            case .welcome:
                WelcomeView()
            }
        }
        .onChange(of: navigation.siteCheckRequested) { requested in
            guard requested else { return }
            navigation.siteCheckRequested = false
            activeSheet = .siteCheck
        }
        .onChange(of: navigation.privateBrowsingRequested) { requested in
            guard requested else { return }
            navigation.privateBrowsingRequested = false
            activeSheet = .privateBrowsing
        }
        .onChange(of: navigation.welcomeRequested) { requested in
            guard requested else { return }
            navigation.welcomeRequested = false
            activeSheet = .welcome
        }
        .onAppear {
            // A menu-bar request can arrive before this window exists; the
            // change handler only sees requests made after the view appears.
            if navigation.siteCheckRequested {
                navigation.siteCheckRequested = false
                activeSheet = .siteCheck
            }
            if navigation.privateBrowsingRequested {
                navigation.privateBrowsingRequested = false
                activeSheet = .privateBrowsing
            }
            if navigation.welcomeRequested {
                navigation.welcomeRequested = false
                activeSheet = .welcome
            }
            // A login-agent launch creates a window only to close it again.
            // Presenting the welcome there marked it seen before the person
            // ever saw it; the menu bar's Open Hisn asks for it explicitly.
            if !welcomeCompleted, !AppDelegate.isHostingTests,
               !AppDelegate.isAgentManaged {
                activeSheet = .welcome
            }
        }
        .task {
            guard !AppDelegate.isHostingTests else { return }
            await filter.reassertIfNeeded()
        }
    }

    private var pageTitle: LocalizedStringKey {
        (page ?? .overview).title
    }

    private var pageSubtitle: LocalizedStringKey {
        (page ?? .overview).subtitle
    }

    private func accessibilityLabel(for page: Page) -> Text {
        guard page == .lock, lock.isLocked else { return Text(page.title) }
        return Text(page.title) + Text(verbatim: " · ") + Text("Locked · \(lock.remainingDescription) left")
    }

    private var brandHeader: some View {
        HStack(spacing: 9) {
            HnMark(size: 26)
            VStack(alignment: .leading, spacing: 0) {
                Text("Hisn")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(HnPalette.ink)
                Text(verbatim: "حصن")
                    .font(.system(size: 10.5))
                    .foregroundStyle(HnPalette.muted)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.top, 18)
        .padding(.bottom, 12)
    }
}

/// One sidebar row. A quiet wash and a two-point accent rule mark the
/// selected page; the label stays in ink so it reads like a Mac sidebar rather
/// than a toolbar pill.
private struct SidebarRow: View {
    let page: ContentView.Page
    let selected: Bool
    let lockText: String?
    let action: () -> Void

    @State private var hovered = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: 9) {
                Image(systemName: page.systemImage)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(selected ? HnPalette.accentText : HnPalette.muted)
                    .frame(width: 16)
                    .accessibilityHidden(true)
                Text(page.title)
                    .font(.system(size: 12.5, weight: selected ? .semibold : .regular))
                    .foregroundStyle(HnPalette.ink)
                    .lineLimit(1)
                Spacer(minLength: 4)
                if let lockText {
                    Text(verbatim: lockText)
                        .font(.system(size: 9.5, weight: .medium))
                        .foregroundStyle(HnPalette.faint)
                        .lineLimit(1)
                }
            }
            .padding(.horizontal, 10)
            .frame(height: 29)
            .background {
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .fill(selected ? HnPalette.accentSoft
                          : hovered ? HnPalette.soft : Color.clear)
            }
            .overlay(alignment: .leading) {
                if selected {
                    RoundedRectangle(cornerRadius: 1, style: .continuous)
                        .fill(HnPalette.accent)
                        .frame(width: 2, height: 15)
                        .padding(.leading, 2)
                }
            }
            .contentShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
        }
        .buttonStyle(.plain)
        .onHover { hovered = $0 }
        .accessibilityAddTraits(selected ? [.isSelected] : [])
        .accessibilityLabel(lockText.map {
            Text(page.title) + Text(verbatim: " · ") + Text("Locked · \($0) left")
        } ?? Text(page.title))
    }
}

// MARK: - Shared pieces

/// The status in the words a person would use, rather than the diagnostic
/// vocabulary in the model. The model keeps the full, precise summary for
/// VoiceOver and the layer rows.
extension ProtectionStatus {
    var plainHeadline: String {
        switch level {
        case .checking:    return String(localized: "Checking this Mac")
        case .active:      return String(localized: "Protected")
        case .partial:     return isPrivateWindowOnlyGap
            ? String(localized: "One gap left")
            : String(localized: "Partly protected")
        case .setupNeeded: return String(localized: "Not protected yet")
        }
    }

    var plainSummary: String {
        switch level {
        case .checking:
            return String(localized: "Reading the system filter’s state…")
        case .active:
            return String(localized: "Every layer Hisn can check is running.")
        case .partial:
            let ready = layers.filter(\.ok).count
            return String(localized: "\(ready) of \(layers.count) layers are working. Start with the first row that needs attention.")
        case .setupNeeded:
            return layers.contains(where: { $0.state == .problem })
                ? String(localized: "Nothing is blocking right now. Fix the first step below.")
                : String(localized: "Nothing is blocking yet. The first step is below.")
        }
    }
}

/// One status language across the sidebar, the hero and the layer tiles.
private extension ProtectionStatus.Level {
    var tint: Color {
        switch self {
        case .active:      return HnPalette.calm
        case .partial:     return HnPalette.ember
        case .setupNeeded: return HnPalette.muted
        case .checking:    return HnPalette.muted
        }
    }

    var symbolName: String {
        switch self {
        case .active:      return "checkmark.shield.fill"
        case .partial:     return "exclamationmark.shield.fill"
        case .setupNeeded: return "shield"
        case .checking:    return "shield"
        }
    }
}

private extension ProtectionStatus.Layer {
    var tint: Color {
        switch state {
        case .ok:       return HnPalette.calm
        case .problem:  return HnPalette.ember
        case .missing:  return HnPalette.muted
        case .checking: return HnPalette.muted
        }
    }

    var symbolName: String {
        switch state {
        case .ok:       return "checkmark.circle.fill"
        case .problem:  return "exclamationmark.circle.fill"
        case .missing:  return "circle"
        case .checking: return "ellipsis.circle"
        }
    }

    var stateLabel: LocalizedStringKey {
        switch state {
        case .ok:       return "Ready"
        case .problem:  return "Needs attention"
        case .missing:  return "Not set up"
        case .checking: return "Checking"
        }
    }
}

/// One layer in the Overview sheet: a state dot, the layer's own word, the
/// evidence line and the state. The dot is the colour; the words carry the
/// answer.
private struct OverviewLayerRow: View {
    let layer: ProtectionStatus.Layer

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Circle()
                .fill(layer.tint)
                .frame(width: 6, height: 6)
                .padding(.top, 6)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(layer.name)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(HnPalette.ink)
                    Spacer(minLength: 8)
                    Text(layer.stateLabel)
                        .font(.system(size: 10.5, weight: .semibold))
                        .foregroundStyle(layer.tint)
                }
                Text(layer.detail)
                    .font(.hnCaption())
                    .foregroundStyle(HnPalette.muted)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.vertical, 9)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text(layer.name) + Text(verbatim: ": ") + Text(layer.stateLabel))
        .accessibilityHint(Text(layer.detail))
    }
}

/// One layer as a plain row inside the status card. The detail is a help tag,
/// not a sentence on the page.
private struct LayerStatusRow: View {
    let layer: ProtectionStatus.Layer

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: layer.symbolName)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(layer.tint)
                .frame(width: 16)
                .accessibilityHidden(true)
            Text(layer.name)
                .font(.system(.callout))
                .foregroundStyle(HnPalette.ink)
                .lineLimit(1)
            Spacer(minLength: 8)
            Text(layer.stateLabel)
                .font(.system(.caption))
                .foregroundStyle(layer.tint)
        }
        .padding(.vertical, 5)
        .help(layer.detail)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text(layer.name) + Text(verbatim: ": ") + Text(layer.stateLabel))
        .accessibilityHint(Text(layer.detail))
    }
}

/// Draws a fraction as a ring, with the count inside. Used by Setup so the
/// progress is visible before the step list rather than inferred from a thin
/// system bar.
private struct ProgressRing: View {
    let done: Int
    let total: Int
    var diameter: CGFloat = 66

    private var complete: Bool { total > 0 && done >= total }

    var body: some View {
        HnRing(done: done, total: total, size: diameter,
               tint: complete ? HnPalette.calm : HnPalette.accent)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text("Setup progress"))
            .accessibilityValue(Text(String(localized: "\(done) of \(total) steps done.")))
    }
}

/// Always-visible answer to the app's two questions. The sidebar is the one
/// surface present on every page, so protection and lock state belong here
/// rather than only on Overview.
private struct SidebarStatus: View {
    @ObservedObject var lock: LockManager
    @ObservedObject var filter: FilterController
    @ObservedObject private var sync = FilterSync.shared
    let open: () -> Void

    @State private var hovered = false

    var body: some View {
        let protection = ProtectionStatus(ProtectionEvidence.live(
            filter: filter.availability, authority: sync.status))
        Button(action: open) {
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: 7) {
                    Circle()
                        .fill(protection.level.tint)
                        .frame(width: 7, height: 7)
                        .accessibilityHidden(true)
                    Text(verbatim: protection.plainHeadline)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(HnPalette.ink)
                        .lineLimit(1)
                    Spacer(minLength: 4)
                    Image(systemName: "chevron.forward")
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundStyle(HnPalette.faint)
                        .accessibilityHidden(true)
                }
                Text(lock.isLocked
                     ? String(localized: "Locked · \(lock.remainingDescription) left")
                     : String(localized: "No lock running"))
                    .font(.system(size: 10.5))
                    .foregroundStyle(HnPalette.muted)
                    .lineLimit(1)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 9)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(hovered ? HnPalette.soft : Color.clear,
                        in: RoundedRectangle(cornerRadius: 6, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { hovered = $0 }
        .overlay(alignment: .top) {
            HnHairline().offset(y: -9)
        }
        .help("Overview")
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("sidebar.status")
    }
}

/// A titled block with breathing room. The page's purpose lives in the
/// `Explain` button beside the title, never as a paragraph under it.
private struct PageSection<Content: View>: View {
    let title: LocalizedStringKey
    var subtitle: LocalizedStringKey? = nil
    var systemImage: String? = nil
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                HnSectionLabel(title: title, systemImage: systemImage)
                if let subtitle {
                    HnExplain { Text(subtitle) }
                }
                Spacer(minLength: 0)
            }
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// A quiet line that says the protections are not the person's to remove.
/// It is informational: the app has already hidden the local bypass controls.
private struct ManagedBanner: View {
    let managed: ManagedMode

    var body: some View {
        HnNotice(title: nil,
                 verbatim: String(localized: "Managed by \(managed.displayName). Your organization controls these protections."),
                 systemImage: "building.2.fill", level: .info)
    }
}

/// The card the status lines sit on.
private struct Card<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        HnCard { content }
    }
}

/// Save/Revert row for an editable section. Sections save on their own
/// button rather than a window-wide one so a refused half of one edit cannot
/// take an unrelated edit down with it.
private struct SaveRow: View {
    let message: String?
    let isError: Bool
    let canSave: Bool
    let save: () -> Void
    let revert: () -> Void

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            if let message {
                Text(message)
                    .font(.caption)
                    .foregroundStyle(isError ? Color.red : Color.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer()
            Button("Revert", action: revert)
                .buttonStyle(.bordered)
                .controlSize(.small)
                .disabled(!canSave)
            Button("Save", action: save)
                .buttonStyle(.borderedProminent)
                .tint(HnPalette.accent)
                .controlSize(.small)
                .disabled(!canSave)
                .keyboardShortcut(.defaultAction)
        }
    }
}

// MARK: - Overview

/// Am I protected? Am I locked? And, for anything not right, what do I press?
private struct OverviewPage: View {
    @ObservedObject var lock: LockManager
    @ObservedObject var filter: FilterController
    @ObservedObject private var sync = FilterSync.shared
    let openSiteCheck: () -> Void
    let goTo: (ContentView.Page) -> Void

    @State private var previouslyReadyLayers: Set<String> = []
    @State private var historyStorageHealthy = true
    @State private var clockAssessment: CommitmentClock.Assessment = .consistent
    @State private var clockMonitor = CommitmentClock(wallAnchor: Date(), uptimeAnchor: CommitmentClock.continuousTime)
    @State private var readinessHistory = ReadinessHistory.deviceLocal(key: "hisn.mac.readiness",
        allowed: ["filter", "hosts", "extension"])

    private func rememberReadiness(_ status: ProtectionStatus) {
        readinessHistory.observe(Set(status.layers.filter(\.ok).map(\.id)))
        previouslyReadyLayers = readinessHistory.known
        historyStorageHealthy = readinessHistory.storageHealthy
    }

    private var lostLayers: [ProtectionStatus.Layer] {
        protection.unconfirmedLayers(previouslyReady: previouslyReadyLayers)
    }

    /// Re-read on every render. `lock.now` ticks once a second, so the
    /// extension heartbeat and the filter's domain count are never more than a
    /// second stale here without any extra plumbing.
    private var protection: ProtectionStatus {
        ProtectionStatus(protectionEvidence)
    }

    private var protectionEvidence: ProtectionEvidence {
        ProtectionEvidence.live(filter: filter.availability, authority: sync.status)
    }

    private var laptopReadiness: LaptopSetupReadiness {
        LaptopSetupReadiness(protection: protectionEvidence,
            checklist: readingSetup ? nil : setup,
            browserWarning: !guardian.alerts.isEmpty,
            hasAppExceptions: !guardian.allowed.isEmpty,
            filterBlock: FilterLink.shared.buildBlock)
    }

    private var lockStatus: LockStatus {
        LockStatus(state: lock.state, now: lock.now,
                   pendingRelease: lock.pendingSelfRelease)
    }

    @ObservedObject private var guardian = BrowserGuard.shared
    @State private var setup: SetupChecklist?
    @State private var readingSetup = false

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            HnPageHeader(eyebrow: "Protection",
                         verbatim: protection.plainHeadline,
                         caption: protection.plainSummary)

            if let managed = protection.managed, managed.isManaged {
                ManagedBanner(managed: managed)
            }
            ForEach(guardian.alerts) { alert in
                HnNotice(title: nil,
                         verbatim: String(localized: "\(alert.name) is about to close.") + " "
                             + BrowserGuard.message(name: alert.name, reason: alert.reason),
                         systemImage: "exclamationmark.shield.fill", level: .caution)
            }

            // Warnings stay one line and sit under the state they qualify.
            if clockAssessment != .consistent {
                HnNotice(title: nil,
                         message: "Check automatic date and time.",
                         systemImage: "clock.badge.exclamationmark", level: .caution)
            }
            if !historyStorageHealthy {
                HnNotice(title: nil,
                         message: "Saved protection history could not be read.",
                         systemImage: "exclamationmark.triangle", level: .caution)
            }
            if !lostLayers.isEmpty {
                HnNotice(title: nil,
                         message: "Previously ready layers are no longer confirmed.",
                         systemImage: "exclamationmark.triangle", level: .caution,
                         action: ("Continue setup", { goTo(.setup) }))
            } else if lock.isLocked && protection.hasProblems && protection.isEnforcingAnything {
                HnNotice(title: nil,
                         message: "Some layers need attention. The lock stays active.",
                         systemImage: "exclamationmark.triangle", level: .caution,
                         action: ("Continue setup", { goTo(.setup) }))
            }
            if lock.isLocked && !protection.isEnforcingAnything
                && protection.level != .checking {
                HnNotice(title: nil,
                         message: "This lock blocks nothing until setup is finished.",
                         systemImage: "exclamationmark.triangle.fill", level: .problem)
            }

            HnTwoColumn(railWidth: 260, main: { layersSheet }, rail: { rail })
        }
        .onAppear {
            rememberReadiness(protection)
        }
        .onChange(of: lock.now) { _ in
            clockAssessment = clockMonitor.assessment(wall: Date(), uptime: CommitmentClock.continuousTime)
        }
        .onChange(of: protection) { current in
            rememberReadiness(current)
        }
        .task { await readSetup() }
        .onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in
            Task { await readSetup() }
        }
        .onChange(of: filter.isEnabled) { _ in Task { await readSetup() } }
        .onReceive(Timer.publish(every: 30, on: .main, in: .common).autoconnect()) { _ in
            Task { await readSetup() }
        }
    }

    /// The evidence, as a table. One sheet, one row per layer, no nested cards.
    private var layersSheet: some View {
        HnCard(padding: 0) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    HnSectionLabel(title: "Layers",
                                   trailing: Text(verbatim: "\(protection.layers.filter(\.ok).count) of \(protection.layers.count) ready"))
                    HnExplain {
                        Text(protection.summary)
                        Text("Readiness is not proof that every route is blocked. Router protection is checked separately.")
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 15)
                .padding(.bottom, 8)

                ForEach(Array(protection.layers.enumerated()), id: \.element.id) { index, layer in
                    OverviewLayerRow(layer: layer)
                        .padding(.horizontal, 16)
                    if index < protection.layers.count - 1 {
                        HnHairline(inset: 16)
                    }
                }

                HnHairline()
                HStack(spacing: 8) {
                    if let checked = guardian.lastChecked {
                        Text("Last check")
                        Text(checked, style: .time)
                    } else {
                        Text("Not checked yet")
                    }
                    Spacer(minLength: 8)
                    HnButton(title: "Open setup", kind: .text) { goTo(.setup) }
                }
                .font(.hnCaption())
                .foregroundStyle(HnPalette.faint)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Protection: \(protection.headline). \(protection.summary)")
    }

    /// The right rail is deliberately not a card. It is the page's decision,
    /// its numbers and its two quiet tools, separated by hairlines.
    private var rail: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 8) {
                HnSectionLabel(title: "Next step")
                Text(verbatim: railHeadline)
                    .font(.hnDisplaySmall())
                    .foregroundStyle(HnPalette.ink)
                    .fixedSize(horizontal: false, vertical: true)
                Text(verbatim: railCaption)
                    .font(.hnCaption())
                    .foregroundStyle(HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)

                if privateGap {
                    HnButton(title: "Prevent private browsing…", systemImage: "eye.slash",
                             fullWidth: true) {
                        AppNavigation.shared.privateBrowsingRequested = true
                    }
                    if !lock.isLocked {
                        HnButton(title: "Review setup", kind: .quiet, fullWidth: true) {
                            goTo(.setup)
                        }
                    }
                } else {
                    HnButton(title: primaryActionTitle, systemImage: primaryActionIcon,
                             fullWidth: true, loading: primaryActionDisabled) {
                        goTo(primaryActionPage)
                    }
                }
            }

            HnHairline()

            VStack(alignment: .leading, spacing: 12) {
                HnSectionLabel(title: "Today")
                HStack(alignment: .top, spacing: 16) {
                    if let blocked = todayBlocked {
                        HnStat(value: "\(blocked)", label: "Blocked today")
                    }
                    HnStat(value: "\(pausesToday)", label: "Pauses today")
                }
                if let next = nextLock {
                    HStack(spacing: 6) {
                        Image(systemName: "clock")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundStyle(HnPalette.faint)
                            .accessibilityHidden(true)
                        Text(verbatim: String(localized: "Next daily lock \(next.start.formatted(date: .omitted, time: .shortened))–\(next.end.formatted(date: .omitted, time: .shortened))."))
                    }
                    .font(.hnCaption())
                    .foregroundStyle(HnPalette.muted)
                } else {
                    HStack(spacing: 6) {
                        Text("No daily lock scheduled.")
                            .font(.hnCaption())
                            .foregroundStyle(HnPalette.faint)
                        Spacer(minLength: 8)
                        HnButton(title: "Set a daily lock", kind: .text) {
                            goTo(.lock)
                        }
                    }
                }
            }

            HnHairline()

            VStack(alignment: .leading, spacing: 2) {
                HnSectionLabel(title: "Tools")
                    .padding(.bottom, 4)
                HnLinkRow(systemImage: "wind", title: "Take a pause…") {
                    PauseWindowController.shared.show()
                }
                .accessibilityIdentifier("overview.pause")
                HnLinkRow(systemImage: "magnifyingglass", title: "Check a website") {
                    openSiteCheck()
                }
                .accessibilityIdentifier("overview.siteCheck")
            }

            HnHairline()
            HStack(spacing: 6) {
                Image(systemName: "lock.shield")
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(HnPalette.faint)
                Text("Matching stays on this Mac. There is no account and no browsing log.")
                    .font(.hnCaption())
                    .foregroundStyle(HnPalette.faint)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var railHeadline: String {
        if lock.isLocked { return String(localized: "Locked") }
        if !laptopReadiness.isReady { return String(localized: "Finish setup") }
        return String(localized: "No lock running")
    }

    private var railCaption: String {
        if lock.isLocked {
            return lockStatus.detail ?? String(localized: "The lock is running. Manage it to see the time left and the way out.")
        }
        if !laptopReadiness.isReady {
            return String(localized: "Some layers still need attention. A lock can run now, but it blocks only what is ready.")
        }
        return String(localized: "Protection stays on. A lock makes it harder to turn off.")
    }

    private var pausesToday: Int {
        guard let tally = PauseStore.load(),
              tally.day == PauseTally.dayKey(for: lock.now) else { return 0 }
        return tally.count
    }

    private var nextLock: DateInterval? {
        ScheduleStore.current().nextWindow(after: lock.now)
    }

    private var primaryActionIcon: String {
        if lock.isLocked { return "lock.fill" }
        if protection.level == .checking { return "arrow.clockwise" }
        if !laptopReadiness.isReady { return "checklist" }
        return "lock.open"
    }

    private func readSetup() async {
        guard !readingSetup, !AppDelegate.isHostingTests else { return }
        readingSetup = true
        defer { readingSetup = false }
        let running = LaptopSetupReadiness.filterIsReady(protectionEvidence)
        let locked = lock.isLocked
        var evidence = await Task.detached {
            SetupEvidence.current(systemFilterRunning: running, lockActive: locked,
                                  filterBlock: FilterLink.shared.buildBlock)
        }.value
        // Never certify the filter from the pre-scan snapshot or from a
        // restrictive recovery state that intentionally reports a live layer.
        evidence.systemFilterRunning = LaptopSetupReadiness.filterIsReady(protectionEvidence)
        setup = SetupChecklist(evidence)
    }

    /// Denied flows the filter itself reports for today, or nil before it has
    /// answered and when the reply belongs to another day. A count only: the
    /// filter keeps no host, app or address, so it is safe to show beside the
    /// evidence.
    private var todayBlocked: Int? {
        guard let health = sync.status?.health,
              health.blockedToday > 0,
              health.blockedDay == BlockedFlowTally.dayKey(for: Date()) else { return nil }
        return health.blockedToday
    }

    private var privateLayer: ProtectionStatus.Layer? {
        protection.layers.first { $0.id == "privateBrowsing" }
    }

    private var privateGap: Bool { privateLayer?.state == .problem }

    /// The main call to action follows the actual state: finish setup when a
    /// layer is missing, otherwise start or manage the lock. “Start a lock”
    /// always remains reachable as the secondary action once anything enforces.
    private var primaryActionTitle: LocalizedStringKey {
        if lock.isLocked { return "Manage lock" }
        if protection.level == .checking { return "Checking…" }
        if !laptopReadiness.isReady {
            return protection.isEnforcingAnything ? "Review setup" : "Finish setup"
        }
        return "Start a lock…"
    }

    private var primaryActionPage: ContentView.Page {
        if lock.isLocked { return .lock }
        if protection.level == .checking { return .overview }
        if !laptopReadiness.isReady { return .setup }
        return .lock
    }

    private var primaryActionDisabled: Bool {
        !lock.isLocked && protection.level == .checking
    }


}

/// “Would this be blocked?” — the question every list editor otherwise answers
/// only by trial and error.
///
/// The sheet is deliberately honest about its reach: it checks the address
/// against the installed list and the person's own lists, and says when the
/// installed list could not be read. It never calls a page safe.
private struct SiteCheckSheet: View {
    let isLocked: Bool
    let strict: Bool

    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var result: SiteCheckResult?
    @State private var checking = false
    @State private var notice: String?
    @FocusState private var fieldFocused: Bool

    private var normalized: String? { SiteLists.normalize(query) }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(HnPalette.accent)
                    .frame(width: 20, height: 24)
                    .accessibilityHidden(true)
                Text("Check a website")
                    .font(.system(size: 22, weight: .semibold, design: .serif))
                    .foregroundStyle(HnPalette.ink)
                HnExplain {
                    Text("A domain or a full link both work. Subdomains are covered by a parent domain.")
                    Text("""
                         This checks the address against the lists on this Mac. \
                         It does not check page text, images or video, or whether \
                         a browser profile has the extension.
                         """)
                }
                Spacer()
                Button { dismiss() } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 17))
                        .foregroundStyle(HnPalette.faint)
                }
                .buttonStyle(.plain)
                .keyboardShortcut(.cancelAction)
                .help("Close")
                .accessibilityLabel(Text("Close"))
            }

            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    TextField("Enter a website or paste a link", text: $query)
                        .hnField()
                        .focused($fieldFocused)
                        .onSubmit(run)
                        .accessibilityIdentifier("siteCheck.field")
                    HnButton(title: checking ? "Checking…" : "Check",
                             systemImage: "arrow.forward", loading: checking) {
                        run()
                    }
                    .disabled(normalized == nil || checking)
                    .accessibilityIdentifier("siteCheck.check")
                }
            }

            if checking {
                HStack(spacing: 8) {
                    ProgressView().controlSize(.small)
                    Text("Checking the lists on this Mac…")
                        .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                }
            } else if let result {
                resultCard(result)
            }

            if let notice {
                Text(verbatim: notice)
                    .font(.hnCallout())
                    .foregroundStyle(HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: 0)

            Divider().overlay(HnPalette.line)
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Spacer(minLength: 0)
                if canAdd {
                    HnButton(title: "Add to block list", kind: .quiet, compact: true) { add() }
                        .disabled(checking)
                        .accessibilityIdentifier("siteCheck.addBlock")
                }
            }
        }
        .padding(24)
        .frame(width: 580)
        .background(HnPalette.canvas)
        .task {
            fieldFocused = true
            SiteCheckService.prewarm()
        }
        .onChange(of: query) { _ in notice = nil }
    }

    private var canAdd: Bool {
        guard let result else { return false }
        return result.outcome != .blocked && result.host != nil
    }

    private func run() {
        guard normalized != nil, !checking else { return }
        checking = true
        result = nil
        notice = nil
        let keywords = Inspection.read().hostKeywords
        Task {
            let answer = await SiteCheckService.check(raw: query, strict: strict,
                                                      keywordsEnabled: keywords)
            result = answer
            checking = false
        }
    }

    private func add() {
        guard let host = result?.host else { return }
        do {
            switch try SiteLists.addBlock(host, locked: isLocked) {
            case .added:
                notice = String(localized: "Added to the block list.")
                run()
            case .alreadyBlocked:
                notice = String(localized: "Already on the block list.")
            case .invalid:
                notice = String(localized: "That is not a domain.")
            }
        } catch {
            notice = error.localizedDescription
        }
    }

    private func tint(_ result: SiteCheckResult) -> Color {
        switch result.outcome {
        case .blocked:        return .green
        case .allowed:        return .secondary
        case .listUnavailable: return .orange
        case .invalid:        return .orange
        }
    }

    private func symbol(_ result: SiteCheckResult) -> String {
        switch result.outcome {
        case .blocked:        return "checkmark.shield.fill"
        case .allowed:        return "shield.slash"
        case .listUnavailable: return "questionmark.circle"
        case .invalid:        return "exclamationmark.circle"
        }
    }

    private func headline(_ result: SiteCheckResult) -> LocalizedStringKey {
        switch result.outcome {
        case .blocked:         return "This site is blocked"
        case .allowed:         return "This site is not blocked"
        case .listUnavailable: return "Your lists do not block this site"
        case .invalid:         return "That is not a website address"
        }
    }

    private func detail(_ result: SiteCheckResult) -> String {
        switch result.outcome {
        case .invalid:
            return String(localized: "Try something like example.com, or paste the page’s link.")
        case .listUnavailable:
            return String(localized: """
                The installed block list could not be read, so this answer \
                does not include it. Your own lists were checked.
                """)
        case .blocked:
            switch result.source {
            case .builtIn:
                return String(localized: "It matches a built-in Hisn rule for search results or viewer pages.")
            case .customBlock:
                return String(localized: "It is on your block list.")
            case .publishedList:
                if result.listSource == .bundledCore {
                    if let version = result.listVersion {
                        return String(localized: "It is on the bundled core list (version \(version)).")
                    }
                    return String(localized: "It is on the bundled core list.")
                }
                if let version = result.listVersion {
                    return String(localized: "It is on the installed block list (version \(version)).")
                }
                return String(localized: "It is on the installed block list.")
            case .keyword:
                return String(localized: "Its address matches a blocked keyword.")
            case .adultTld:
                return String(localized: "It ends in an adult-only top-level domain, so every name under it is blocked.")
            case .strictMode:
                return String(localized: "Strict mode is on, and this site is not on your allow list.")
            case .allowlist, .notListed, .none:
                return ""
            }
        case .allowed:
            switch result.source {
            case .allowlist:
                return String(localized: "It is on your allow list. Neither the published list nor strict mode will block it.")
            case .notListed:
                if result.listSource == .bundledCore {
                    if let version = result.listVersion {
                        return String(localized: "It is not on the bundled core list (version \(version)) or your own lists.")
                    }
                    return String(localized: "It is not on the bundled core list or your own lists.")
                }
                if let version = result.listVersion {
                    return String(localized: "It is not on the installed block list (version \(version)) or your own lists.")
                }
                return String(localized: "It is not on the installed block list or your own lists.")
            default:
                return ""
            }
        }
    }

    private func resultCard(_ result: SiteCheckResult) -> some View {
        let colour = tint(result)
        return HStack(alignment: .top, spacing: 12) {
            Image(systemName: symbol(result))
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(colour)
                .frame(width: 36, height: 36)
                .background(colour.opacity(0.12),
                            in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(headline(result))
                    .font(.system(.callout).weight(.semibold))
                    .foregroundStyle(HnPalette.ink)
                let detail = detail(result)
                if !detail.isEmpty {
                    Text(verbatim: detail)
                        .font(.hnCaption())
                        .foregroundStyle(HnPalette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if let host = result.host {
                    Text(verbatim: host)
                        .font(.system(.caption, design: .monospaced))
                        .foregroundStyle(HnPalette.faint)
                        .textSelection(.enabled)
                }
                if result.listSource == .bundledCore {
                    Text("This answer uses the bundled core list. The full list has not been installed on this Mac.")
                        .font(.hnCaption())
                        .foregroundStyle(HnPalette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colour.opacity(0.07), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .strokeBorder(colour.opacity(0.20), lineWidth: 1)
                .accessibilityHidden(true)
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("siteCheck.result")
    }
}

/// How to connect a browser, in the app rather than a document. The full
/// guided onboarding replaces this; until then it is the shortest honest
/// version that does not send anyone to a terminal.
private struct BrowserSetupHelp: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 11) {
                Image(systemName: "puzzlepiece.extension")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(HnPalette.accent)
                    .frame(width: 20, height: 24)
                    .accessibilityHidden(true)
                Text("Connect a browser")
                    .font(.system(size: 22, weight: .semibold, design: .serif))
                    .foregroundStyle(HnPalette.ink)
                HnExplain {
                    Text("When enabled, the system filter checks network hostnames across this Mac. The extension separately checks webpage content in supported Chromium browsers, including Chrome, Brave and Helium.")
                    Text("When enabled, the system filter also checks Safari and Firefox connections; this extension does not run in those browsers.")
                }
            }
            .padding(.bottom, 14)
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Card {
                        VStack(alignment: .leading, spacing: 14) {
                            step(1, """
                                    Install the Hisn Protection extension in the browser, \
                                    or ask whoever set up this Mac to install it for you.
                                    """)
                            step(2, "Open any web page. The extension contacts this app within a minute.")
                            step(3, "Come back here — the Browser extension row reads Connected once it has.")
                        }
                    }
                    ForEach(NativeMessagingInstaller.storeURLs, id: \.self) { url in
                        Link(destination: url) {
                            Label("Get browser extension", systemImage: "arrow.up.right.square")
                        }
                    }
                    ExtensionVisualGuidance()
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.trailing, 6)
            }
            Divider().overlay(HnPalette.line).padding(.vertical, 16)
            HStack {
                Spacer()
                HnButton(title: "Done", kind: .filled) { dismiss() }
                    .keyboardShortcut(.defaultAction)
            }
        }
        .padding(24)
        .frame(width: 560, height: 590)
        .background(HnPalette.canvas)
    }

    private func step(_ n: Int, _ text: LocalizedStringKey) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Text(verbatim: "\(n).").font(.callout.weight(.semibold)).monospacedDigit()
            Text(text).font(.callout).fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// Browser-owned preferences must not be presented as controls the native app
/// can read or change. Keep a discoverable, honest route to the extension UI.
private struct ExtensionVisualGuidance: View {
    var body: some View {
        HStack(spacing: 6) {
            Label("Visual checks in the extension", systemImage: "eye")
                .font(.system(.callout).weight(.medium))
                .foregroundStyle(HnPalette.ink)
            HnExplain {
                Text("Open the Hisn extension, choose Manage protection, then Visual checks. Adult-media checks and person blur are separate choices, saved in that browser.")
                Text("Person blur uses detected faces and can be off, all people, women or men. The women and men options estimate appearance; they do not identify a person or mean their image is adult content.")
                Text("The app does not verify visual-check settings. Accessible images and sampled video frames can be checked locally; unreadable media and missed detections remain possible.")
            }
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("settings.extensionVisualGuidance")
    }
}

// MARK: - Setup

/// The steps of `docs/SETUP.md`, each read from this Mac: what is done, what
/// is left, and for each one left, what to do. Re-read on appear and on
/// "Check again" — most steps happen outside the app (a profile installed, a
/// password handed over), so there is nothing to observe live.
struct SetupPage: View {
    @ObservedObject var filter: FilterController
    @ObservedObject private var sync = FilterSync.shared
    @ObservedObject private var guardian = BrowserGuard.shared
    @ObservedObject private var lock = LockManager.shared
    let goTo: (ContentView.Page) -> Void

    @State private var checklist: SetupChecklist?
    @State private var checking = false
    @State private var showBrowserHelp = false
    @State private var copied: String?
    @State private var expandedStages: Set<ProtectedSetup.Stage.ID> = []
    @State private var showNetwork = false
    @State private var lastChecked: Date?
    @State private var privateStatus = PrivateBrowsingStatus.current(force: true)
    @AppStorage("hisn.setup.administratorConfirmed") private var administratorConfirmed = false
    @AppStorage("hisn.setup.recoveryConfirmed") private var recoveryConfirmed = false

    private var protectedSetup: ProtectedSetup? {
        checklist.map { ProtectedSetup(checklist: $0, administratorConfirmed: administratorConfirmed,
                                       recoveryConfirmed: recoveryConfirmed) }
    }

    private var protectionEvidence: ProtectionEvidence {
        ProtectionEvidence.live(filter: filter.availability, authority: sync.status)
    }

    private var managed: ManagedMode { protectionEvidence.managedMode ?? .none }

    private var laptopReadiness: LaptopSetupReadiness {
        LaptopSetupReadiness(protection: protectionEvidence,
            checklist: checking ? nil : checklist,
            browserWarning: !guardian.alerts.isEmpty,
            hasAppExceptions: !guardian.allowed.isEmpty,
            filterBlock: FilterLink.shared.buildBlock)
    }

    init(filter: FilterController, goTo: @escaping (ContentView.Page) -> Void,
         initialChecklist: SetupChecklist? = nil,
         confirmationDefaults: UserDefaults = .standard,
         initialExpandedStages: Set<ProtectedSetup.Stage.ID>? = nil) {
        self.filter = filter
        self.goTo = goTo
        _checklist = State(initialValue: initialChecklist)
        _administratorConfirmed = AppStorage(wrappedValue: false, "hisn.setup.administratorConfirmed", store: confirmationDefaults)
        _recoveryConfirmed = AppStorage(wrappedValue: false, "hisn.setup.recoveryConfirmed", store: confirmationDefaults)
        var expanded = initialExpandedStages ?? []
        if initialExpandedStages == nil, let checklist = initialChecklist {
            let setup = ProtectedSetup(checklist: checklist,
                administratorConfirmed: confirmationDefaults.bool(forKey: "hisn.setup.administratorConfirmed"),
                recoveryConfirmed: confirmationDefaults.bool(forKey: "hisn.setup.recoveryConfirmed"))
            if let next = setup.nextStage { expanded.insert(next.id) }
        }
        _expandedStages = State(initialValue: expanded)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            HnPageHeader(eyebrow: "Setup", title: "Finish protecting this Mac",
                         caption: "The checks below turn the layers on. Work through them in order.")
            if managed.isManaged {
                ManagedBanner(managed: managed)
            }
            // One card answers both questions the page exists for: is this Mac
            // protected, and how far through setup am I?
            Card {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(alignment: .top, spacing: 16) {
                        if let setup = protectedSetup {
                            ProgressRing(done: setup.doneCount, total: setup.totalCount)
                                .padding(.top, 2)
                        }
                        TimelineView(.periodic(from: .now, by: 10)) { _ in
                            LaptopSetupStatusView(readiness: laptopReadiness, review: reviewSetupIssue)
                        }
                    }
                    if let setup = protectedSetup {
                        Divider()
                        HStack(alignment: .firstTextBaseline, spacing: 10) {
                            if let next = setup.nextStage {
                                Label(String(localized: "Next: \(next.title)"),
                                      systemImage: "arrow.right.circle.fill")
                                    .font(.callout.weight(.medium))
                                    .lineLimit(1)
                            }
                            Spacer(minLength: 8)
                            Text(String(localized: "\(setup.doneCount) of \(setup.totalCount) steps done."))
                                .font(.callout.weight(.medium))
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }

            HStack(spacing: 10) {
                if let lastChecked {
                    Text("Last checked").foregroundStyle(HnPalette.muted)
                    Text(lastChecked, style: .time).foregroundStyle(HnPalette.muted)
                } else {
                    Text("Not checked yet").foregroundStyle(HnPalette.faint)
                }
                Spacer()
                HnButton(title: "Check again", systemImage: "arrow.clockwise",
                         kind: .quiet, loading: checking) {
                    Task { await refresh() }
                }
            }
            .font(.hnCaption())

            // Private windows are the one profile item with a narrow fix: a
            // single policy disables them without touching DNS.
            if !privateStatus.open.isEmpty {
                HnNotice(title: LocalizedStringKey("Private windows are open in \(privateStatus.openNames)."),
                         message: "Those windows skip the browser extension.",
                         systemImage: "eye.slash.fill", level: .caution,
                         action: ("Prevent private browsing…", {
                             AppNavigation.shared.privateBrowsingRequested = true
                         }))
            }

            if let setup = protectedSetup {
                ForEach(setup.stages) { stage in
                    stageCard(stage, setup: setup)
                }
            }

            DisclosureGroup(isExpanded: $showNetwork) {
                NetworkSetupGuide()
                    .padding(.top, 8)
            } label: {
                HnSectionLabel(title: "Network protection",
                               trailing: Text("Optional"))
            }
            .font(.hnBody())
        }
        .task { await refresh() }
        // Most steps happen outside the app — a command in Terminal, a
        // profile in System Settings — so read the Mac again on the way back.
        .onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in
            Task { await refresh() }
        }
        .sheet(isPresented: $showBrowserHelp) { BrowserSetupHelp() }
        .onChange(of: filter.isEnabled) { _ in Task { await refresh() } }
        .onChange(of: checklist) { _ in
            if expandedStages.isEmpty, let next = protectedSetup?.nextStage { expandedStages.insert(next.id) }
        }
        .onReceive(Timer.publish(every: 30, on: .main, in: .common).autoconnect()) { _ in
            Task { await refresh() }
        }
    }

    /// One stage as a card: a calm header row, and the steps as rows inside it
    /// rather than a card inside a card. The open stage is the only one that
    /// spends vertical space; every closed stage is one line.
    @ViewBuilder
    private func stageCard(_ stage: ProtectedSetup.Stage, setup: ProtectedSetup) -> some View {
        let expanded = expandedStages.contains(stage.id)
        let done = stage.steps.filter { $0.state == .done }.count
        HnCard(padding: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Button {
                    withAnimation(.easeInOut(duration: 0.18)) {
                        if expanded { expandedStages.remove(stage.id) }
                        else { expandedStages.insert(stage.id) }
                    }
                } label: {
                    HStack(spacing: 9) {
                        Image(systemName: stage.isComplete ? "checkmark.circle.fill" : "circle")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(stage.isComplete ? HnPalette.calm : HnPalette.faint)
                            .accessibilityHidden(true)
                        Text(stage.title)
                            .font(.system(.callout).weight(.medium))
                            .foregroundStyle(HnPalette.ink)
                        if !stage.isComplete, stage.id == setup.nextStage?.id {
                            HnBadge(text: "Next")
                        }
                        Spacer(minLength: 8)
                        Text(verbatim: "\(done)/\(stage.steps.count)")
                            .font(.system(.caption))
                            .monospacedDigit()
                            .foregroundStyle(HnPalette.muted)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundStyle(HnPalette.faint)
                            .rotationEffect(.degrees(expanded ? 180 : 0))
                            .accessibilityHidden(true)
                    }
                    .padding(.horizontal, 16)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                if expanded {
                    Divider().overlay(HnPalette.line)
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(Array(stage.steps.enumerated()), id: \.element.id) { index, step in
                            row(step)
                            if index < stage.steps.count - 1 {
                                Divider().overlay(HnPalette.line).padding(.leading, 25)
                            }
                        }
                        if stage.id == .recovery {
                            stageConfirmation(
                                "I know how to contact my trusted person and recover access if something goes wrong.",
                                note: "Confirmed by you, not checked by Hisn. Keep recovery details somewhere you can reach without this Mac.",
                                isOn: $recoveryConfirmed, disabled: false)
                        }
                        if stage.id == .accounts {
                            stageConfirmation(
                                "A trusted person keeps the separate administrator password, and I use only this standard account.",
                                note: "Do this last. Keep a separate working administrator account; never remove the last administrator. Confirmed by you. Hisn can check account permissions, but cannot know who holds a password.",
                                isOn: $administratorConfirmed,
                                disabled: lock.isLocked || managed.isManaged || stage.steps.contains { $0.state != .done })
                            if let authority = checklist?.authority {
                                AuthorityCard(authority: authority, isLocked: lock.isLocked)
                                    .padding(.top, 8)
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 2)
                    .padding(.bottom, 10)
                }
            }
        }
    }

    private func stageConfirmation(_ title: LocalizedStringKey, note: LocalizedStringKey,
                                   isOn: Binding<Bool>, disabled: Bool) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Toggle(title, isOn: isOn)
                .toggleStyle(.checkbox)
                .font(.hnCallout())
                .fixedSize(horizontal: false, vertical: true)
                .disabled(disabled)
            HnExplain { Text(note) }
            Spacer(minLength: 0)
        }
        .padding(.top, 12)
    }

    private func row(_ step: SetupChecklist.Step) -> some View {
        HStack(alignment: .top, spacing: 9) {
            Image(systemName: step.state == .done ? "checkmark.circle.fill" : "circle")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(step.state == .done ? HnPalette.calm : HnPalette.faint)
                .padding(.top, 2)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 5) {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(step.title)
                        .font(.system(.callout).weight(.medium))
                        .foregroundStyle(HnPalette.ink)
                    HnExplain { Text(verbatim: step.detail) }
                    Spacer(minLength: 0)
                }
                if case let .command(line) = step.action {
                        DisclosureGroup("Administrator setup") {
                            HStack(spacing: 8) {
                                Text(verbatim: line)
                                    .font(.callout.monospaced())
                                    .fixedSize(horizontal: false, vertical: true)
                                    .textSelection(.enabled)
                                    .environment(\.layoutDirection, .leftToRight)
                                Button(copied == line ? "Copied" : "Copy") {
                                    NSPasteboard.general.clearContents()
                                    NSPasteboard.general.setString(line, forType: .string)
                                    copied = line
                                }
                                .controlSize(.small)
                            }
                            .padding(.top, 2)
                            if line == SetupChecklist.signedBuildCommand {
                                Text("Replace YOUR_TEAM_ID with the 10-character team ID from your Apple Developer account.")
                                    .font(.caption).foregroundStyle(.secondary)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .padding(.top, 4)
                    }
                stepAction(step)
                if step.id == .extensionManagement {
                        DisclosureGroup("removal.setup.title") {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("removal.setup.publish")
                                Text("removal.setup.consent")
                                Text("removal.setup.verify")
                                Text("removal.setup.limit").foregroundStyle(.secondary)
                                Link("removal.setup.guide", destination: URL(string: "https://support.google.com/chrome/a/answer/7517624?hl=en")!)
                                Button("Open Device Management") {
                                    openSystemSettings("com.apple.Profiles-Settings.extension")
                                }
                                Button("Check again") { Task { await refresh() } }
                                    .disabled(checking)
                            }
                            .font(.callout)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.top, 8)
                        }
                    }
                if step.id == .systemFilter && step.state != .done {
                    FilterSetupControl(filter: filter, showsBuildBlock: false)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 11)
        .accessibilityElement(children: .contain)
    }

    @ViewBuilder private func stepAction(_ step: SetupChecklist.Step) -> some View {
        switch step.action {
        case .browserHelp:
            HnButton(title: "How to connect a browser", kind: .quiet, compact: true) { showBrowserHelp = true }
        case .partnerSettings:
            HnButton(title: "Open Settings", kind: .quiet, compact: true) { goTo(.settings) }
        case .screenTimeSettings:
            HnButton(title: "Open Screen Time", kind: .quiet, compact: true) {
                if let url = URL(string: "x-apple.systempreferences:com.apple.Screen-Time-Settings.extension") {
                    NSWorkspace.shared.open(url)
                }
            }
        case .enableFilter:
            FilterSetupControl(filter: filter)
        case .deviceManagementSettings:
            if step.id != .extensionManagement {
                HnButton(title: "Open Device Management", kind: .quiet, compact: true) {
                    openSystemSettings("com.apple.Profiles-Settings.extension")
                }
            }
        case .accountsSettings:
            HnButton(title: "Open Users & Groups", kind: .quiet, compact: true) {
                openSystemSettings("com.apple.Users-Groups-Settings.extension")
            }
            .disabled(lock.isLocked)
        case .applicationsFolder:
            HnButton(title: "Open Applications", kind: .quiet, compact: true) {
                NSWorkspace.shared.open(URL(fileURLWithPath: "/Applications", isDirectory: true))
            }
        case .command, .none:
            EmptyView()
        }
    }

    private func openSystemSettings(_ pane: String) {
        guard let url = URL(string: "x-apple.systempreferences:\(pane)") else { return }
        if !NSWorkspace.shared.open(url) {
            NSWorkspace.shared.open(URL(fileURLWithPath: "/System/Applications/System Settings.app"))
        }
    }

    private func reviewSetupIssue(_ issue: LaptopSetupReadiness.Issue) {
        switch issue {
        case .appExceptions:
            goTo(.rules)
        case .browserProfiles, .browserConnection, .browserWarning:
            showBrowserHelp = true
        case .signedBuild, .filter, .policy, .installation:
            expandedStages.insert(.installation)
        }
    }

    private func refresh() async {
        guard !checking, !AppDelegate.isHostingTests else { return }
        checking = true
        defer { checking = false }
        await filter.refresh()
        await FilterSync.shared.sync()
        let running = LaptopSetupReadiness.filterIsReady(protectionEvidence)
        let locked = lock.isLocked
        var evidence = await Task.detached { SetupEvidence.current(systemFilterRunning: running, lockActive: locked, filterBlock: FilterLink.shared.buildBlock) }.value
        // The filter can change while disk/DNS evidence is being collected.
        evidence.systemFilterRunning = LaptopSetupReadiness.filterIsReady(protectionEvidence)
        checklist = SetupChecklist(evidence)
        PrivateBrowsingStatus.invalidateCache()
        privateStatus = PrivateBrowsingStatus.current(force: true)
        lastChecked = Date()
    }
}

/// The visible authority evidence, beside the account-split confirmation.
/// The app only reads; it never creates the guardian or stores its password.
private struct AuthorityCard: View {
    let authority: MacAuthority
    let isLocked: Bool
    @State private var copied: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .foregroundStyle(color)
                    .accessibilityHidden(true)
                Text("Protection authority")
                    .font(.system(.callout).weight(.semibold))
                    .foregroundStyle(HnPalette.ink)
                HnExplain {
                    Text("Hisn does not create the guardian account or store its password. A separate person types it.")
                }
                Spacer()
                Text(levelText)
                    .font(.system(.caption).weight(.semibold))
                    .foregroundStyle(color)
            }
            row("Daily account", dailyText, ok: authority.dailyUserIsAdmin == false)
            row("Guardian account", guardianText, ok: authority.guardian == .ready)
            row("Guardian daemon", daemonText, ok: authority.daemon == .loaded)
            if isLocked {
                Label("A lock is running, so administrator and removal changes are disabled until it ends.",
                      systemImage: "lock.fill")
                    .font(.hnCaption())
                    .foregroundStyle(HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                if authority.level != .verified || authority.dailyUserIsAdmin == true {
                    command("Guardian setup command", MacAuthority.guardianSetupCommand)
                }
                if authority.mayOfferRestoreCommand {
                    command("Restore administrator command",
                            MacAuthority.restoreAdminCommand(dailyUser: authority.dailyUser))
                }
            }
        }
        .padding(14)
        .background(HnPalette.raised,
                    in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(HnPalette.line, lineWidth: 1)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("setup.authority")
    }

    private var icon: String {
        switch authority.level {
        case .verified: return "checkmark.shield.fill"
        case .partial: return "exclamationmark.shield.fill"
        case .unavailable: return "questionmark.circle"
        }
    }

    private var color: Color {
        switch authority.level {
        case .verified: return HnPalette.calm
        case .partial: return HnPalette.ember
        case .unavailable: return HnPalette.muted
        }
    }

    private var levelText: LocalizedStringKey {
        switch authority.level {
        case .verified: return "Verified"
        case .partial: return "Not verified"
        case .unavailable: return "Unavailable"
        }
    }

    private var dailyText: LocalizedStringKey {
        switch authority.dailyUserIsAdmin {
        case .some(false): return "Standard account"
        case .some(true): return "Administrator"
        case .none: return "Could not read"
        }
    }

    private var guardianText: LocalizedStringKey {
        switch authority.guardian {
        case .ready: return "Ready"
        case .missing: return "Missing"
        case .notAdmin: return "Not an administrator"
        case .noSecureToken: return "No secure token"
        case .unreadable: return "Could not read"
        }
    }

    private var daemonText: LocalizedStringKey {
        switch authority.daemon {
        case .loaded: return "Loaded"
        case .notLoaded: return "Not loaded"
        case .missing: return "Missing"
        }
    }

    private func row(_ title: LocalizedStringKey, _ value: LocalizedStringKey, ok: Bool) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Image(systemName: ok ? "checkmark.circle.fill" : "exclamationmark.circle")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(ok ? HnPalette.calm : HnPalette.ember)
                .accessibilityHidden(true)
            Text(title).font(.hnCaption()).foregroundStyle(HnPalette.ink)
            Spacer()
            Text(value).font(.hnCaption()).foregroundStyle(HnPalette.muted)
                .multilineTextAlignment(.trailing)
        }
    }

    private func command(_ title: LocalizedStringKey, _ line: String) -> some View {
        DisclosureGroup(title) {
            HStack(alignment: .top, spacing: 8) {
                Text(verbatim: line)
                    .font(.callout.monospaced())
                    .textSelection(.enabled)
                    .fixedSize(horizontal: false, vertical: true)
                    .environment(\.layoutDirection, .leftToRight)
                Button(copied == line ? "Copied" : "Copy") {
                    NSPasteboard.general.clearContents()
                    NSPasteboard.general.setString(line, forType: .string)
                    copied = line
                }
                .controlSize(.small)
            }
            .padding(.top, 2)
        }
    }
}

// MARK: - Lock

/// Starting a lock, or watching one run.
/// The units the custom lock length offers. File-scope so `LockDraft`, which
/// outlives the Lock page, can name it.
enum LockUnit: String, CaseIterable, Identifiable {
    // Minutes exist so a lock can be TRIED. Without them the shortest
    // startable lock is an hour, and an hour that cannot be shortened,
    // cancelled or escaped is a steep price for finding out what the
    // button does — so nobody tries it, and the first real lock someone
    // starts is also the first one they have ever seen run.
    //
    // This does not weaken anything: `LockManager.validated` still refuses
    // anything under `minimumLock`, so the floor is 60 seconds either way.
    case minutes = "minutes"
    case hours = "hours"
    case days = "days"
    case weeks = "weeks"

    var id: String { rawValue }
    var title: String {
        switch self {
        case .minutes: return String(localized: "minutes")
        case .hours:   return String(localized: "hours")
        case .days:    return String(localized: "days")
        case .weeks:   return String(localized: "weeks")
        }
    }
    var seconds: TimeInterval {
        switch self {
        case .minutes: return 60
        case .hours:   return 3600
        case .days:    return 86400
        case .weeks:   return 7 * 86400
        }
    }
}

/// The Lock page's choices, lifted out of the page.
///
/// The pinned action bar lives in the window chrome, outside the page's scroll
/// view, so the form and the button cannot share `@State`. One draft object
/// holds what the person has chosen; both views read and write it.
final class LockDraft: ObservableObject {
    static let shared = LockDraft()

    @Published var duration: LockManager.Duration = .custom
    @Published var customAmount = "5"
    @Published var customUnit: LockUnit = .minutes
    @Published var strict = false
    @Published var fixedCommitment = true
    @Published var understandsLimits = false

    /// The length the button would start, or nil when nothing valid is typed.
    /// Nil disables the button rather than substituting a nearby legal value —
    /// a lock cannot be shortened afterwards, so quietly turning a mistyped
    /// 3650 into 365 would commit someone to a year they never chose.
    var selectedSeconds: TimeInterval? {
        if let preset = duration.seconds { return preset }
        guard let amount = Double(customAmount.trimmingCharacters(in: .whitespaces))
        else { return nil }
        return LockManager.validated(seconds: amount * customUnit.seconds)
    }

    var startButtonTitle: String {
        guard let seconds = selectedSeconds else { return String(localized: "Start lock") }
        return String(localized: "Start a lock of \(LockManager.describe(seconds))")
    }

    var confirmTitle: String {
        guard let seconds = selectedSeconds else { return String(localized: "Start lock") }
        return String(localized: "Start a lock of \(LockManager.describe(seconds))?")
    }

    /// Names the wall-clock moment, not just the length. "90 days" is abstract
    /// in a way "18 February" is not, and the point of this text is that nobody
    /// starts a lock they misjudged.
    func confirmMessage(enforcing: Bool) -> String {
        guard let seconds = selectedSeconds else { return "" }
        let end = Date().addingTimeInterval(seconds).formatted()
        var msg = String(localized: "You will not be able to turn this off until \(end).")
        if fixedCommitment {
            msg += "\n\n" + String(localized: "No early self-release during a fixed commitment. Partner recovery remains available; this does not prevent an administrator from removing protection.")
        }
        if !enforcing {
            msg += "\n\n" + String(localized: """
                Nothing is set up to enforce it yet, so it will run but \
                block nothing until you finish setup.
                """)
        }
        return msg
    }
}

/// The decision and its consequence, pinned to the window's bottom edge while
/// the options scroll. It lives outside the page's scroll view, so it reads the
/// shared `LockDraft`; the line is derived from the same status the Overview
/// shows, so the promise and the lock cannot disagree.
private struct LockActionBar: View {
    @ObservedObject var lock: LockManager
    @ObservedObject private var draft = LockDraft.shared
    let status: ProtectionStatus

    @State private var showConfirm = false
    @State private var error: String?

    private var readyLayers: Int { status.layers.filter(\.ok).count }

    var body: some View {
        HStack(alignment: .center, spacing: 18) {
            HStack(alignment: .top, spacing: 8) {
                Image(systemName: status.isEnforcingAnything ? "checkmark.shield.fill" : "exclamationmark.triangle.fill")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(status.isEnforcingAnything ? HnPalette.calm : HnPalette.ember)
                    .padding(.top, 1)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: status.isEnforcingAnything
                         ? String(localized: "Enforces \(readyLayers) of \(status.layers.count) layers.")
                         : String(localized: "Blocks nothing until setup is finished."))
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(HnPalette.ink)
                    Text("The lock cannot be shortened after it starts.")
                        .font(.hnCaption())
                        .foregroundStyle(HnPalette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            HnButton(text: draft.startButtonTitle, systemImage: "lock.fill") {
                showConfirm = true
            }
            .disabled(draft.selectedSeconds == nil || !draft.understandsLimits)
            .confirmationDialog(draft.confirmTitle, isPresented: $showConfirm,
                                titleVisibility: .visible) {
                Button("Start the lock", role: .destructive) { start() }
                Button("Not yet", role: .cancel) {}
            } message: {
                Text(draft.confirmMessage(enforcing: status.isEnforcingAnything))
            }
        }
        .padding(.horizontal, 32)
        .padding(.vertical, 10)
        .frame(maxWidth: 980, alignment: .leading)
        .frame(maxWidth: .infinity, alignment: .center)
        .background(HnPalette.card)
        .overlay(alignment: .top) { Rectangle().fill(HnPalette.line).frame(height: 1) }
        .alert("Something went wrong", isPresented: .constant(error != nil)) {
            Button("OK") { error = nil }
        } message: { Text(error ?? "") }
    }

    private func start() {
        guard draft.understandsLimits, let seconds = draft.selectedSeconds else { return }
        Task {
            do {
                try await lock.start(seconds: seconds, strict: draft.strict,
                                     fixedCommitment: draft.fixedCommitment)
            } catch { self.error = error.localizedDescription }
        }
    }
}

private struct LockPage: View {
    @ObservedObject var lock: LockManager
    @ObservedObject var filter: FilterController

    @ObservedObject private var draft = LockDraft.shared
    @State private var error: String?
    @State private var showReleaseConfirm = false
    @State private var showExtendConfirm = false
    @State private var authority: MacAuthority?

    /// Kept as the page-local name for the shared unit list.
    typealias CustomUnit = LockUnit

    /// Would a lock started now block anything? Read from the same status the
    /// Overview shows, so the two can never disagree.
    private var protectionStatus: ProtectionStatus {
        ProtectionStatus(ProtectionEvidence.live(filter: filter.availability,
                                                    authority: FilterSync.shared.status))
    }

    private var enforcingAnything: Bool { protectionStatus.isEnforcingAnything }

    /// The length the button would start, or nil when there is nothing valid to
    /// start. Nil disables the button rather than substituting a nearby legal
    /// value — a lock cannot be shortened afterwards, so quietly turning a
    /// mistyped 3650 into 365 would commit someone to a year they never chose.
    private var selectedSeconds: TimeInterval? { draft.selectedSeconds }

    var body: some View {
        VStack(alignment: .leading, spacing: 26) {
            HnPageHeader(eyebrow: "Commitment", title: "Make it harder to stop",
                         caption: "A lock is a promise with a deadline.")
            Group {
                if lock.isLocked { activeLock } else { setup }
            }
            Divider()
            ScheduleSection()
        }
        .alert("Something went wrong", isPresented: .constant(error != nil)) {
            Button("OK") { error = nil }
        } message: { Text(error ?? "") }
        .onChange(of: draft.duration) { _ in draft.understandsLimits = false }
        .onChange(of: draft.customAmount) { _ in draft.understandsLimits = false }
        .onChange(of: draft.customUnit) { _ in draft.understandsLimits = false }
        .onChange(of: draft.fixedCommitment) { _ in draft.understandsLimits = false }
        .onChange(of: draft.strict) { _ in draft.understandsLimits = false }
        .onChange(of: lock.isLocked) { _ in draft.understandsLimits = false }
        .task {
            let locked = lock.isLocked
            authority = await Task.detached {
                MacAuthority.current(lockActive: locked)
            }.value
        }
        .confirmationDialog("Add seven days to this lock?", isPresented: $showExtendConfirm,
                            titleVisibility: .visible) {
            Button("Add a week", role: .destructive) {
                do { try lock.extend(by: 7 * 86400) }
                catch { self.error = error.localizedDescription }
            }
            Button("Not yet", role: .cancel) { }
        } message: {
            Text("A fixed commitment stays fixed for the added time. This does not weaken any existing restrictions.")
            if let next = lock.state.extending(by: 7 * 86400) {
                Text(next.deadline, format: .dateTime.day().month().year().hour().minute())
            }
        }
    }

    // MARK: Not locked

    private var setup: some View {
        VStack(alignment: .leading, spacing: 22) {
            VStack(alignment: .leading, spacing: 8) {
                HnSectionLabel(title: "Duration")
                HnCard {
                    VStack(alignment: .leading, spacing: 14) {
                        HStack(spacing: 5) {
                            ForEach(LockManager.Duration.allCases) { choice in
                                HnChip(verbatim: choice.title,
                                       selected: draft.duration == choice) {
                                    draft.duration = choice
                                }
                            }
                        }

                        if draft.duration == .custom {
                            HStack(spacing: 8) {
                                TextField("How long", text: $draft.customAmount)
                                    .hnField()
                                    .frame(width: 92)
                                    .accessibilityLabel(Text("How long"))
                                Picker("Unit", selection: $draft.customUnit) {
                                    ForEach(CustomUnit.allCases) { Text($0.title).tag($0) }
                                }
                                .labelsHidden()
                                .frame(width: 118)
                                Spacer(minLength: 0)
                            }
                        }

                        HnHairline()

                        HStack(alignment: .firstTextBaseline, spacing: 16) {
                            VStack(alignment: .leading, spacing: 2) {
                                if let seconds = selectedSeconds {
                                    Text(verbatim: LockManager.describe(seconds))
                                        .font(.hnFigure(32))
                                        .foregroundStyle(HnPalette.ink)
                                    Text(verbatim: lengthHint)
                                        .font(.hnCaption())
                                        .foregroundStyle(HnPalette.muted)
                                } else {
                                    Text(verbatim: lengthHint)
                                        .font(.hnDisplaySmall())
                                        .foregroundStyle(isCustomInvalid ? HnPalette.danger : HnPalette.muted)
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                            }
                            Spacer(minLength: 0)
                        }
                    }
                }
            }

            VStack(alignment: .leading, spacing: 8) {
                HnSectionLabel(title: "Mode")
                HnCard {
                    VStack(alignment: .leading, spacing: 0) {
                        modeBlock(title: "Fixed commitment until the chosen deadline",
                                  caption: draft.fixedCommitment
                                    ? "No early self-release"
                                    : "48-hour release or partner approval",
                                  detail: draft.fixedCommitment
                                    ? "No early self-release during a fixed commitment. Partner recovery remains available; this does not prevent an administrator from removing protection."
                                    : """
                                      Once started, you cannot shorten or cancel this. \
                                      Early release takes 48 hours, or an approval from \
                                      your accountability partner.
                                      """,
                                  isOn: $draft.fixedCommitment)
                        HnHairline().padding(.vertical, 12)
                        modeBlock(title: "Strict mode",
                                  caption: "Only allowlisted sites stay reachable",
                                  detail: "Blocks everything except sites you allow. A block list can never cover every site; this can.",
                                  isOn: $draft.strict)
                    }
                }
            }

            if authority?.dailyUserIsAdmin == true {
                HnNotice(title: nil,
                         message: "This account is still an administrator. It can remove Hisn during the lock. Complete the guardian split in Setup first.",
                         systemImage: "exclamationmark.triangle.fill", level: .problem)
            }

            HnHairline()
            Toggle(isOn: $draft.understandsLimits) {
                Text("I understand this commitment does not prevent an administrator from removing protection. Keep essential services accessible.")
                    .font(.hnCaption())
                    .foregroundStyle(HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .toggleStyle(.checkbox)
        }
    }

    /// One mode as a title, a switch, and the sentence that says what the
    /// switch means. The detail follows the choice, so the fixed-commitment
    /// sentence is never on screen while the fixed commitment is off.
    private func modeBlock(title: LocalizedStringKey, caption: LocalizedStringKey,
                           detail: LocalizedStringKey,
                           isOn: Binding<Bool>) -> some View {
        HStack(alignment: .center, spacing: 10) {
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: 4) {
                    Text(title)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(HnPalette.ink)
                        .fixedSize(horizontal: false, vertical: true)
                    HnExplain { Text(detail) }
                }
                Text(caption)
                    .font(.hnCaption())
                    .foregroundStyle(HnPalette.muted)
            }
            Spacer(minLength: 8)
            Toggle(isOn: isOn) { EmptyView() }
                .labelsHidden()
                .toggleStyle(.switch)
                .controlSize(.small)
                .accessibilityLabel(Text(title))
        }
    }

    // MARK: Locked

    private var activeLock: some View {
        VStack(alignment: .leading, spacing: 20) {
            HnCard {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(alignment: .center, spacing: 8) {
                        HnBadge(verbatim: LockStatus.modeLabel(lock.state.mode),
                                tint: lock.state.mode == "strict" ? HnPalette.ember : HnPalette.accent)
                        Spacer(minLength: 8)
                        Image(systemName: lock.state.mode == "strict" ? "lock.shield.fill" : "lock.fill")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(HnPalette.accent)
                            .accessibilityHidden(true)
                    }
                    Text(verbatim: lock.remainingDescription)
                        .font(.hnNumber(36))
                        .foregroundStyle(HnPalette.ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                    Text(verbatim: String(localized: "Ends \(lock.state.deadline.formatted(date: .long, time: .shortened))."))
                        .font(.hnCallout())
                        .foregroundStyle(HnPalette.muted)
                    ProgressView(value: lockProgress)
                        .progressViewStyle(.linear)
                        .tint(HnPalette.accent)
                        .accessibilityLabel(Text("Lock progress"))
                    HStack {
                        Text(verbatim: String(localized: "Started \(lock.state.startedAt.formatted(date: .abbreviated, time: .shortened))"))
                        Spacer()
                        Text(verbatim: String(localized: "Ends \(lock.state.deadline.formatted(date: .abbreviated, time: .shortened))"))
                    }
                    .font(.hnCaption())
                    .foregroundStyle(HnPalette.faint)
                }
            }

            if let pending = lock.pendingSelfRelease {
                HnNotice(title: "Early release requested",
                         verbatim: String(localized: "Unlocks \(pending.formatted()). You can cancel this, but you cannot speed it up."),
                         systemImage: "hourglass", level: .info,
                         action: ("Cancel the request", { lock.cancelSelfRelease() }))
            }

            if lock.hasFixedCommitment {
                HnNotice(title: nil,
                         message: "Your fixed commitment has not finished. Self-release is unavailable until it ends.",
                         systemImage: "lock.fill", level: .info)
            }

            VStack(alignment: .leading, spacing: 10) {
                HnSectionHeader(title: "Make it stronger", systemImage: "arrow.up.shield")
                HnCard {
                    HStack(spacing: 10) {
                        HnButton(title: "Add a week", systemImage: "plus", kind: .quiet) {
                            showExtendConfirm = true
                        }
                        if lock.state.mode != "strict" {
                            HnButton(title: "Switch to strict", systemImage: "lock.shield", kind: .quiet) {
                                do { try lock.tightenToStrict() }
                                catch { self.error = error.localizedDescription }
                            }
                        }
                        Spacer(minLength: 0)
                    }
                }
            }

            VStack(alignment: .leading, spacing: 8) {
                HnSectionHeader(title: "Your partner can end it now", systemImage: "person.badge.shield.checkmark")
                HnCard { PartnerReleaseCard(lock: lock) }
            }

            if lock.pendingSelfRelease == nil && !lock.hasFixedCommitment {
                // The way out. Without this button the request could not be
                // created at all, and the panel above — which only ever offered
                // to *cancel* one — was showing an exit nobody could reach.
                //
                // Deliberately quiet, and deliberately present. The threat model
                // is explicit that a lock with no exit is not the safer design:
                // it is the one people uninstall pre-emptively, and it is
                // dangerous when someone genuinely needs the web. What keeps it
                // honest is that the exit is slow and cannot be hurried.
                VStack(alignment: .leading, spacing: 8) {
                    HnSectionHeader(title: "Ending early", systemImage: "arrow.uturn.backward")
                    HnCard {
                        HStack(spacing: 8) {
                            HnButton(title: "Request early release…", kind: .outline) {
                                showReleaseConfirm = true
                            }
                            HnExplain {
                                Text(verbatim: String(localized: """
                                    A release request takes \(LockManager.describe(LockManager.selfReleaseDelay)) \
                                    to arrive and cannot be hurried. Your accountability \
                                    partner can approve one immediately.
                                    """))
                            }
                            Spacer(minLength: 0)
                        }
                    }
                    .confirmationDialog(
                        "Request early release?",
                        isPresented: $showReleaseConfirm, titleVisibility: .visible
                    ) {
                        Button("Request it") { requestRelease() }
                        Button("Never mind", role: .cancel) {}
                    } message: {
                        Text("""
                             The lock would end \(Date().addingTimeInterval(LockManager.selfReleaseDelay).formatted()). \
                             You can cancel the request at any time, but you \
                             cannot make it arrive sooner.
                             """)
                    }
                }
            }
        }
    }

    /// How much of the lock has elapsed, for the progress bar. Guarded against
    /// a zero-length or clock-skewed state rather than letting SwiftUI draw a
    /// NaN.
    private var lockProgress: Double {
        let total = lock.state.deadline.timeIntervalSince(lock.state.startedAt)
        guard total > 0 else { return 0 }
        let elapsed = lock.now.timeIntervalSince(lock.state.startedAt)
        return min(1, max(0, elapsed / total))
    }

    /// Only once something has been typed. An empty field is a person who has
    /// not finished, not a person who is wrong, and colouring it red says the
    /// opposite.
    private var isCustomInvalid: Bool {
        draft.duration == .custom && !draft.customAmount.isEmpty && selectedSeconds == nil
    }

    private var lengthHint: String {
        let shortest = LockManager.describe(LockManager.minimumLock)
        let longest = LockManager.describe(LockManager.maximumLock)
        if isCustomInvalid {
            return String(localized: "Enter a length between \(shortest) and \(longest).")
        }
        guard let seconds = selectedSeconds else {
            return String(localized: "Between \(shortest) and \(longest).")
        }
        let end = Date().addingTimeInterval(seconds).formatted(date: .abbreviated, time: .shortened)
        return String(localized: "Ends \(end).")
    }

    /// The confirmation-dialog body: the wall-clock deadline, and — when
    /// nothing is set up to enforce it — the fact that the lock will block
    /// nothing, repeated here so it cannot be clicked past by reflex from the
    /// Start button.
    private func requestRelease() {
        do { _ = try lock.requestSelfRelease() }
        catch { self.error = error.localizedDescription }
    }
}

// MARK: - Daily lock

/// The schedule: a lock that starts itself each day (`LockSchedule`). Shown
/// in both states — tightening it is always allowed, and a lock running now
/// is exactly when someone wants to make sure tomorrow night is covered too.
private struct ScheduleSection: View {
    @State private var draft = ScheduleStore.current()
    @State private var saved = ScheduleStore.current()
    @State private var pending = ScheduleStore.pending()
    @State private var message: String?

    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                HnSectionLabel(title: "Every day")
                HnExplain {
                    Text("""
                         A lock that starts itself at the same time each day and ends with the \
                         window. Making it stronger takes effect now; making it weaker waits \
                         24 hours, so switching it off at the last minute is no way out.
                         """)
                }
            }

            HnCard {
                VStack(alignment: .leading, spacing: 12) {
                    HnToggleRow(title: "Lock every day", isOn: $draft.enabled)

                    if draft.enabled, !expanded {
                        HStack(spacing: 8) {
                            Text("From")
                            Text(verbatim: clock(draft.start))
                            Text("until")
                            Text(verbatim: clock(draft.end))
                            if draft.strict {
                                Text("·")
                                Text("Strict mode")
                            }
                            Spacer(minLength: 8)
                            HnButton(title: "Edit", kind: .text) { expanded = true }
                        }
                        .font(.hnBody())
                        .foregroundStyle(HnPalette.muted)
                    }

                    if expanded {
                        HStack(spacing: 16) {
                            DatePicker("From", selection: time(\.start), displayedComponents: .hourAndMinute)
                            DatePicker("Until", selection: time(\.end), displayedComponents: .hourAndMinute)
                            Spacer(minLength: 0)
                        }
                        .font(.hnBody())
                        .disabled(!draft.enabled)

                        HnToggleRow(title: "Strict mode", isOn: $draft.strict)
                            .disabled(!draft.enabled)
                    }

                    // Saving inside an open window starts the lock at once —
                    // said before Save, not discovered after it.
                    if draft.enabled, draft != saved,
                       let window = draft.window(containing: LockStore.trustedNow()) {
                        HnNotice(title: nil,
                                 verbatim: String(localized: "The window is open now: saving starts a lock until \(window.end.formatted(date: .omitted, time: .shortened))."),
                                 systemImage: "exclamationmark.triangle.fill", level: .caution)
                    }

                    if let pending {
                        HStack(alignment: .firstTextBaseline, spacing: 10) {
                            Text(verbatim: String(localized: "A weaker schedule takes effect \(pending.at.formatted(date: .abbreviated, time: .shortened))."))
                                .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                                .fixedSize(horizontal: false, vertical: true)
                            Spacer(minLength: 8)
                            HnButton(title: "Cancel it", kind: .text) {
                                ScheduleStore.cancelPending()
                                reload()
                            }
                        }
                    }

                    if expanded || draft != saved {
                        SaveRow(message: message, isError: false, canSave: draft != saved,
                                save: save, revert: { reload() })
                    }
                }
            }
        }
        .onAppear { reload() }
        .onChange(of: draft) { new in
            if new != saved { expanded = true }
        }
    }

    private var summary: String {
        var text = String(localized: "\(clock(draft.start))–\(clock(draft.end))")
        if draft.strict { text += " · " + String(localized: "Strict mode") }
        return text
    }

    private func clock(_ minutes: Int) -> String {
        let start = Calendar.current.startOfDay(for: Date())
            .addingTimeInterval(TimeInterval(minutes * 60))
        return start.formatted(date: .omitted, time: .shortened)
    }

    /// A time of day in minutes, as the Date a DatePicker wants.
    private func time(_ key: WritableKeyPath<LockSchedule, Int>) -> Binding<Date> {
        Binding(
            get: {
                Calendar.current.startOfDay(for: Date())
                    .addingTimeInterval(TimeInterval(draft[keyPath: key] * 60))
            },
            set: { date in
                let c = Calendar.current.dateComponents([.hour, .minute], from: date)
                draft[keyPath: key] = (c.hour ?? 0) * 60 + (c.minute ?? 0)
            })
    }

    private func save() {
        switch ScheduleStore.request(draft) {
        case .applied:
            reload(message: String(localized: "Saved."))
        case let .waiting(until):
            reload(message: String(localized: "This makes the schedule weaker, so it takes effect \(until.formatted(date: .abbreviated, time: .shortened))."))
        }
    }

    private func reload(message: String? = nil) {
        saved = ScheduleStore.current()
        draft = saved
        pending = ScheduleStore.pending()
        self.message = message
    }
}

// MARK: - Blocking Rules

/// What is being blocked, beyond the published list: the person's own sites,
/// words and apps.
///
/// Reachable in both states on purpose. Adding a block and dropping an
/// allowance are the two edits that stay legal during a lock, and they are
/// exactly the ones someone wants at the moment they find a site the list
/// missed.
private struct RulesPage: View {
    let isLocked: Bool
    let managed: ManagedMode
    let openSiteCheck: () -> Void

    private var restricted: Bool { isLocked || managed.isManaged }

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            HnPageHeader(eyebrow: "Blocking Rules", title: "What gets blocked",
                         caption: "The published list, plus anything you add.")
            if managed.isManaged {
                ManagedBanner(managed: managed)
            } else if isLocked {
                HnNotice(title: nil,
                         message: "A lock is running. Add blocks and remove allowances; the reverse waits.",
                         systemImage: "lock.fill", level: .info)
            }
            SiteListsSection(isLocked: restricted, openSiteCheck: openSiteCheck)
            UserBlocksSection(isLocked: restricted)
            BrowsersSection(isLocked: restricted)
            HnCard { BrowserFirstGuide() }
        }
    }
}

/// Where the person writes their own two lists.
///
/// Both are edited together and saved together, because the guard that matters
/// is about the pair: while a lock runs you may add blocks and withdraw
/// allowances, and neither of the opposites. Saving them separately would let
/// a refused half leave the other half applied.
///
/// The two lists share one card and one pair of Save/Revert buttons, with a
/// segmented control choosing which one is on screen. The old page printed both
/// editors in full, so the allowlist — usually the shorter, quieter list — took
/// the same vertical space and the same visual weight as the block list. The
/// segment labels keep both list names visible, and the counts and the pair
/// guard are unchanged.
struct SiteListsSection: View {
    let isLocked: Bool
    let openSiteCheck: () -> Void

    /// The add field's placeholder. Held in a String property so SwiftUI uses
    /// the verbatim initializer: an example domain is not UI copy and must not
    /// become a translation key.
    private let exampleDomain = "example.com"

    private enum ListTab: String, CaseIterable, Identifiable {
        case block, allow

        var id: String { rawValue }

        var title: LocalizedStringKey {
            switch self {
            case .block: return "Block list"
            case .allow: return "Allow list"
            }
        }

        var heading: LocalizedStringKey {
            switch self {
            case .block: return "Always block these"
            case .allow: return "Always allowed"
            }
        }

        /// A sentence under the segmented control. It says what the list is
        /// for without a paragraph; the longer rules live in Explain.
        var shortRule: LocalizedStringKey {
            switch self {
            case .block:
                return "On top of the published list."
            case .allow:
                return "Reachable even if the published list names it. In strict mode nothing else is reachable."
            }
        }

        var explanation: LocalizedStringKey {
            switch self {
            case .block:
                return """
                    A site here is blocked in every app Hisn runs. Subdomains are \
                    included, so example.com also covers cdn.example.com. Adding a \
                    block works during a lock.
                    """
            case .allow:
                return """
                    A site here stays reachable even when the published list names \
                    it. In strict mode only these sites are reachable. Withdrawing \
                    an allowance works during a lock; adding one waits until it ends.
                    """
            }
        }

        var empty: LocalizedStringKey {
            switch self {
            case .block: return "No extra sites. The published list still applies."
            case .allow: return "Nothing allowed beyond the published list."
            }
        }
    }

    @State private var tab: ListTab = .block
    @State private var blockText = ""
    @State private var allowText = ""
    @State private var savedBlockText = ""
    @State private var savedAllowText = ""
    @State private var newBlock = ""
    @State private var newAllow = ""
    @State private var message: String?
    @State private var isError = false

    private var isDirty: Bool {
        blockText != savedBlockText || allowText != savedAllowText
    }

    private var activeText: Binding<String> {
        tab == .block ? $blockText : $allowText
    }

    private var activeNewEntry: Binding<String> {
        tab == .block ? $newBlock : $newAllow
    }

    private var activeDomains: [String] {
        SiteLists.parse(activeText.wrappedValue).domains
    }

    private var activeSummary: (text: String, hasProblem: Bool) {
        Self.summarize(activeText.wrappedValue)
    }

    var body: some View {
        PageSection(title: "Websites",
                    subtitle: "Your own block and allow lists, on top of the published list.",
                    systemImage: "globe") {
            Card {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 8) {
                        Picker("List", selection: $tab) {
                            ForEach(ListTab.allCases) { list in
                                Text(list.title).tag(list)
                            }
                        }
                        .pickerStyle(.segmented)
                        .labelsHidden()
                        .frame(maxWidth: 260)

                        Spacer(minLength: 8)

                        HnButton(title: "Check a website", systemImage: "magnifyingglass",
                                 kind: .quiet, compact: true) {
                            openSiteCheck()
                        }
                        .help("Check a website")
                    }

                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text(tab.shortRule)
                            .font(.hnCaption())
                            .foregroundStyle(HnPalette.muted)
                            .fixedSize(horizontal: false, vertical: true)
                        HnExplain { Text(tab.explanation) }
                        Spacer(minLength: 0)
                    }

                    if tab == .block {
                        ExtraSitesPicker(blocks: SiteLists.parse(blockText).domains,
                                         isLocked: isLocked) { domain in
                            toggleSuggestion(domain)
                        }
                        Divider().overlay(HnPalette.line).padding(.vertical, 2)
                    }

                    domainEditor

                    SaveRow(message: message, isError: isError, canSave: isDirty,
                            save: save, revert: load)
                }
            }
        }
        .onAppear(perform: load)
        .onChange(of: tab) { _ in message = nil }
    }

    /// The rows, the one-line add field, and the bulk editor for the selected
    /// list. Both lists keep their own draft text, so switching tabs never
    /// discards an edit or hides a dirty state from Save.
    private var domainEditor: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(tab.heading)
                    .font(.system(.callout).weight(.medium))
                    .foregroundStyle(HnPalette.ink)
                if !activeSummary.text.isEmpty {
                    Text(verbatim: activeSummary.text)
                        .font(.hnCaption())
                        .foregroundStyle(activeSummary.hasProblem ? HnPalette.ember : HnPalette.muted)
                }
                Spacer(minLength: 0)
            }

            if activeDomains.isEmpty {
                HStack(spacing: 8) {
                    Image(systemName: "tray")
                        .foregroundStyle(HnPalette.faint)
                        .accessibilityHidden(true)
                    Text(tab.empty)
                        .font(.hnCaption())
                        .foregroundStyle(HnPalette.muted)
                    Spacer(minLength: 0)
                }
                .padding(.vertical, 6)
            } else {
                VStack(spacing: 0) {
                    ForEach(activeDomains, id: \.self) { domain in
                        HStack(spacing: 10) {
                            Image(systemName: "globe")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(HnPalette.faint)
                                .accessibilityHidden(true)
                            Text(domain)
                                .font(.system(.callout, design: .monospaced))
                                .foregroundStyle(HnPalette.ink)
                                .lineLimit(1)
                                .truncationMode(.middle)
                            Spacer(minLength: 8)
                            Button { removeActive(domain) } label: {
                                Image(systemName: "trash")
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundStyle(HnPalette.faint)
                            }
                            .buttonStyle(.borderless)
                            .help("Remove")
                            .accessibilityLabel(Text("Remove"))
                        }
                        .padding(.vertical, 6)
                        if domain != activeDomains.last {
                            Divider()
                        }
                    }
                }
            }

            HStack(spacing: 8) {
                // A String variable, not a literal: the placeholder is an
                // example domain, not UI copy.
                TextField(exampleDomain, text: activeNewEntry)
                    .hnField()
                    .onSubmit { addActive() }
                    .accessibilityLabel(Text(tab.heading))
                HnButton(title: "Add", kind: .quiet) { addActive() }
                    .disabled(SiteLists.normalize(activeNewEntry.wrappedValue) == nil)
            }

            // Bulk editing is one paste away, with the rules that apply to it
            // next to the box rather than at the top of the section.
            DisclosureGroup("Paste a list") {
                VStack(alignment: .leading, spacing: 6) {
                    Text("""
                         One domain per line — a full URL is fine, it becomes \
                         just the domain. Subdomains are included automatically, \
                         so example.com also covers cdn.example.com.
                         """)
                        .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                    TextEditor(text: activeText)
                        .font(.system(.callout, design: .monospaced))
                        .foregroundStyle(HnPalette.ink)
                        .scrollContentBackground(.hidden)
                        .padding(8)
                        .background(HnPalette.raised,
                                    in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                        .accessibilityLabel(Text(tab.heading))
                        .frame(height: 88)
                        .overlay(RoundedRectangle(cornerRadius: 9, style: .continuous)
                            .strokeBorder(activeSummary.hasProblem ? HnPalette.ember.opacity(0.7)
                                                                   : HnPalette.lineStrong, lineWidth: 1))
                }
                .padding(.top, 6)
            }
            .font(.callout)
        }
    }

    private func load() {
        savedBlockText = SiteLists.customBlocks().joined(separator: "\n")
        savedAllowText = SiteLists.allowlist().joined(separator: "\n")
        blockText = savedBlockText
        allowText = savedAllowText
        newBlock = ""
        newAllow = ""
        message = nil
    }

    /// A chip edits the block-list draft only. The list stays the single
    /// source of truth, so a chip cannot be selected while its domain is not
    /// saved in the list below it.
    private func toggleSuggestion(_ domain: String) {
        let current = SiteLists.parse(blockText).domains
        let next = ExtraSites.toggling(domain, in: current)
        guard next != current else { return }
        blockText = next.joined(separator: "\n")
    }

    /// Rows and the add field both write into the same newline text the save
    /// path already parses, so there is one source of truth and no second
    /// format to keep in sync.
    private func addActive() {
        add(to: activeText, from: activeNewEntry)
    }

    private func removeActive(_ domain: String) {
        remove(domain, from: activeText)
    }

    private func add(to text: Binding<String>, from entry: Binding<String>) {
        guard let updated = Self.adding(entry.wrappedValue, to: text.wrappedValue) else { return }
        text.wrappedValue = updated
        entry.wrappedValue = ""
    }

    private func remove(_ domain: String, from text: Binding<String>) {
        text.wrappedValue = Self.removing(domain, from: text.wrappedValue)
    }

    /// The add field's whole behaviour, pure so it can be tested without a
    /// window. Returns nil when the typed text is not a domain at all — the
    /// button is disabled in that case — and leaves the list unchanged for a
    /// domain that is already there.
    static func adding(_ entry: String, to text: String) -> String? {
        guard let domain = SiteLists.normalize(entry) else { return nil }
        var domains = SiteLists.parse(text).domains
        guard !domains.contains(domain) else { return text }
        domains.append(domain)
        return domains.joined(separator: "\n")
    }

    static func removing(_ domain: String, from text: String) -> String {
        SiteLists.parse(text).domains.filter { $0 != domain }.joined(separator: "\n")
    }

    /// Live one-line summary of a domain editor's contents.
    static func summarize(_ text: String) -> (text: String, hasProblem: Bool) {
        let r = SiteLists.parse(text)
        let n = r.domains.count
        if r.ignored == 0 {
            return (n == 0 ? "" : String(localized: "\(n) domains"), false)
        }
        return (String(localized: "\(n) valid · \(r.ignored) skipped (not a domain)"), true)
    }

    private func save() {
        let blocks = SiteLists.parse(blockText)
        let allows = SiteLists.parse(allowText)
        do {
            try SiteLists.save(customBlocks: blocks.domains,
                               allowlist: allows.domains,
                               locked: isLocked)
            FilterSync.soon()
        } catch {
            isError = true
            message = error.localizedDescription
            return
        }

        // Saved. Say so — and say what was NOT saved. A line that was silently
        // dropped looks exactly like one that saved, and someone who mistyped a
        // domain would otherwise walk away believing it is blocked.
        let ignored = blocks.ignored + allows.ignored
        savedBlockText = blocks.domains.joined(separator: "\n")
        savedAllowText = allows.domains.joined(separator: "\n")
        isError = false
        if ignored == 0 {
            blockText = savedBlockText
            allowText = savedAllowText
            message = String(localized: "Saved.")
        } else {
            // Leave the rejected lines in the editor so they can be fixed.
            message = String(localized: "Saved. Left out because they are not domains: \(ignored).")
        }
    }
}
/// Words and apps the person blocks by hand.
///
/// Separate from `SiteListsSection` because the two ask different questions.
/// A site list is "which addresses", answered from memory. This is "which
/// words" and "which apps" — the first needs a warning about how easily a bad
/// word blocks the wrong thing, the second needs a file picker.
struct UserBlocksSection: View {
    let isLocked: Bool

    @State private var wordsText = ""
    @State private var apps: [String] = []
    @State private var savedWordsText = ""
    @State private var savedApps: [String] = []
    @State private var message: String?
    @State private var isError = false

    private var isDirty: Bool { wordsText != savedWordsText || apps != savedApps }

    var body: some View {
        HnGroup(title: "Your words and apps",
                subtitle: "Blocked on top of the built-in lists.") {
            HnGroupRow {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 6) {
                        Text("Words")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(HnPalette.ink)
                        HnExplain {
                            Text("""
                                 One per line. A page is blocked when these appear often \
                                 enough in it — so a common word blocks ordinary pages \
                                 too. Minimum \(UserBlocks.minimumTermLength) letters.
                                 """)
                        }
                        Spacer(minLength: 0)
                    }
                    ZStack(alignment: .topLeading) {
                        if wordsText.isEmpty {
                            Text("gambling\nbetting odds")
                                .font(.system(size: 12, design: .monospaced))
                                .foregroundStyle(HnPalette.faint)
                                .padding(.horizontal, 5).padding(.vertical, 8)
                                .allowsHitTesting(false)
                        }
                        TextEditor(text: $wordsText)
                            .font(.system(size: 12, design: .monospaced))
                            .scrollContentBackground(.hidden)
                            .accessibilityLabel("Words")
                    }
                    .frame(height: 84)
                    .padding(.horizontal, 6)
                    .background(HnPalette.raised,
                                in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .strokeBorder(wordsSummary.hasProblem ? HnPalette.ember.opacity(0.7)
                                                              : HnPalette.line, lineWidth: 1))
                    Text(verbatim: wordsSummary.text)
                        .font(.hnCaption())
                        .foregroundStyle(wordsSummary.hasProblem ? HnPalette.ember : HnPalette.muted)
                        .frame(minHeight: 12, alignment: .leading)
                }
            }

            HnHairline(inset: 16)

            HnGroupRow {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 6) {
                        Text("Apps")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(HnPalette.ink)
                        HnExplain {
                            Text("""
                                 Blocks the app's internet access. It still opens, and \
                                 still works offline — stopping a launch needs Screen \
                                 Time, which no app can do for you.
                                 """)
                        }
                        Spacer()
                        HnButton(title: "Add app…", systemImage: "plus", kind: .quiet,
                                 compact: true) { pickApp() }
                    }

                    if apps.isEmpty {
                        HStack(spacing: 8) {
                            Image(systemName: "tray")
                                .foregroundStyle(HnPalette.faint)
                                .accessibilityHidden(true)
                            Text("No apps blocked.")
                                .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                            Spacer(minLength: 0)
                        }
                        .padding(.vertical, 3)
                    } else {
                        VStack(spacing: 0) {
                            ForEach(apps, id: \.self) { id in
                                HStack(spacing: 9) {
                                    appIcon(for: id)
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(verbatim: displayName(for: id))
                                            .font(.system(size: 12.5))
                                        Text(verbatim: id)
                                            .font(.system(size: 10.5))
                                            .foregroundStyle(HnPalette.faint)
                                            .lineLimit(1)
                                            .truncationMode(.middle)
                                    }
                                    Spacer(minLength: 8)
                                    Button {
                                        apps.removeAll { $0 == id }
                                    } label: {
                                        Image(systemName: "minus.circle")
                                    }
                                    .buttonStyle(.borderless)
                                    .disabled(isLocked && savedApps.contains(id))
                                    .accessibilityLabel("Remove \(displayName(for: id))")
                                    .help(isLocked && savedApps.contains(id)
                                          ? "Cannot remove while a lock is running"
                                          : "Remove")
                                }
                                .padding(.vertical, 5)
                                if id != apps.last { HnHairline(inset: 27) }
                            }
                        }
                    }
                }
            }

            HnHairline(inset: 16)
            HnGroupRow {
                SaveRow(message: message, isError: isError, canSave: isDirty,
                        save: save, revert: load)
            }
        }
        .onAppear(perform: load)
    }

    @ViewBuilder private func appIcon(for bundleID: String) -> some View {
        if let url = NSWorkspace.shared.urlForApplication(withBundleIdentifier: bundleID) {
            Image(nsImage: NSWorkspace.shared.icon(forFile: url.path))
                .resizable()
                .frame(width: 18, height: 18)
                .accessibilityHidden(true)
        } else {
            Image(systemName: "app")
                .font(.callout)
                .foregroundStyle(.secondary)
                .frame(width: 18, height: 18)
                .accessibilityHidden(true)
        }
    }

    private func load() {
        savedWordsText = UserBlocks.terms().joined(separator: "\n")
        savedApps = UserBlocks.apps()
        wordsText = savedWordsText
        apps = savedApps
        message = nil
    }

    private var wordsSummary: (text: String, hasProblem: Bool) {
        Self.summarizeWords(wordsText)
    }

    /// Live one-line summary of the words box: valid count, and any reason a
    /// line will not be used (too short, unusable, or over the cap). Static and
    /// pure so it is testable without standing up the view.
    static func summarizeWords(_ text: String) -> (text: String, hasProblem: Bool) {
        let r = UserBlocks.parseTerms(text)
        var problems: [String] = []
        if !r.tooShort.isEmpty {
            problems.append(String(localized: """
                too short: \(r.tooShort.count) (minimum \(UserBlocks.minimumTermLength) letters)
                """))
        }
        if r.ignored > 0 { problems.append(String(localized: "unusable: \(r.ignored)")) }
        if r.terms.count > UserBlocks.maximumTerms {
            problems.append(String(localized: "over the limit of \(UserBlocks.maximumTerms) words"))
        }
        if problems.isEmpty {
            let n = r.terms.count
            return (n == 0 ? "" : String(localized: "\(n) words"), false)
        }
        return (String(localized: "\(r.terms.count) valid") + " · "
                + problems.formatted(.list(type: .and)), true)
    }

    private func displayName(for bundleID: String) -> String {
        guard let url = NSWorkspace.shared
            .urlForApplication(withBundleIdentifier: bundleID) else { return bundleID }
        return FileManager.default.displayName(atPath: url.path)
    }

    /// Pick the app rather than type its identifier. The filter compares a
    /// signing identifier, which is not something anyone knows by heart, and a
    /// typo produces a rule that silently never matches.
    private func pickApp() {
        let panel = NSOpenPanel()
        panel.allowedContentTypes = [.application]
        panel.allowsMultipleSelection = true
        panel.directoryURL = URL(fileURLWithPath: "/Applications")
        panel.prompt = String(localized: "Block")
        guard panel.runModal() == .OK else { return }

        var added = 0, failed: [String] = []
        for url in panel.urls {
            if let id = UserBlocks.bundleIdentifier(forAppAt: url) {
                if !apps.contains(id) { apps.append(id); added += 1 }
            } else {
                failed.append(FileManager.default.displayName(atPath: url.path))
            }
        }
        apps.sort()
        isError = !failed.isEmpty
        message = failed.isEmpty
            ? (added == 0 ? String(localized: "Already on the list.") : nil)
            : String(localized: "Could not read an identifier for \(failed.formatted(.list(type: .and))).")
    }

    private func save() {
        let parsed = UserBlocks.parseTerms(wordsText)
        do {
            try UserBlocks.save(terms: parsed.terms, apps: apps, locked: isLocked)
            FilterSync.soon()
        } catch {
            isError = true
            message = error.localizedDescription
            return
        }

        // Say what was dropped. A word silently left out looks identical to
        // one that saved, and the person finds out only when the page they
        // expected to be blocked opens normally.
        var notes: [String] = []
        if !parsed.tooShort.isEmpty {
            notes.append(String(localized: "Too short to use: \(parsed.tooShort.formatted(.list(type: .and))).")) 
        }
        if parsed.ignored > 0 {
            notes.append(String(localized: "Lines that were not usable words: \(parsed.ignored)."))
        }
        savedWordsText = parsed.terms.joined(separator: "\n")
        savedApps = apps
        isError = false
        if notes.isEmpty {
            wordsText = savedWordsText
            message = String(localized: "Saved.")
        } else {
            message = ([String(localized: "Saved.")] + notes).joined(separator: " ")
        }
    }
}

// MARK: - Settings

/// The content-inspection settings, and the technical details that used to
/// crowd the status header.
///
/// Only layers that actually run appear here. A switch for a feature that is
/// not built would be the "looks protected, is not" failure the threat model
/// rates as worse than being switched off — so there is no image control until
/// there is image classification.
///
/// The copy under each control says WHERE it applies, because the two layers
/// have genuinely different reach and a user who assumes otherwise is being
/// misled: keyword matching runs in the system filter and therefore covers
/// every browser and app, while page-text checking exists only inside the
/// Chrome extension.
/// Which apps that open web pages may stay open during a lock.
///
/// The guard decides "browser" from what an app declares, so an app that
/// registers for web links without being a browser shows up here too — that
/// is the price of catching tomorrow's Chromium fork without a list update,
/// and the reason this section exists. Allowing waits for the lock to end;
/// withdrawing an allowance is always accepted.
struct BrowsersSection: View {
    let isLocked: Bool

    @ObservedObject private var guardian = BrowserGuard.shared
    @State private var candidates: [BrowserGuard.Candidate] = []
    @State private var error: String?

    var body: some View {
        PageSection(title: "Browser protection",
                subtitle: """
                    While browser protection is active, losing the extension \
                    connection starts a visible one-minute recovery countdown. \
                    Restore protection or the browser closes. Safari and other \
                    unsupported browsers also close.
                    """,
                systemImage: "safari") {
            HnCard {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 6) {
                        Label("Browser protection is always on while Hisn runs.", systemImage: "lock.shield")
                            .font(.system(.callout).weight(.medium))
                            .foregroundStyle(HnPalette.calm)
                        HnExplain {
                            Text("There is no off switch and no lock needed: if the extension stops answering, the browser shows a countdown and then closes.")
                            Text("browser.live.cadence")
                            Text("""
                                Hisn stays in the background when its window closes; the separately \
                                installed login agent restarts it after a crash or force-quit. \
                                Detection is periodic, not instant. Standard Default/Profile folders \
                                are checked; custom paths, guest and private windows are unverified. \
                                Explicitly trusted unknown apps bypass extension checks. Administrator \
                                changes can bypass the guard. Save work before enabling it.
                                """)
                        }
                        Spacer(minLength: 0)
                    }

                    Divider()

                    HStack {
                        Text("browser.live.title")
                            .font(.system(.callout).weight(.medium))
                            .foregroundStyle(HnPalette.ink)
                        Spacer()
                        if let checked = guardian.lastChecked {
                            Text(checked, style: .time)
                                .font(.hnCaption()).foregroundStyle(HnPalette.faint)
                        }
                        HnButton(title: "browser.live.refresh", systemImage: "arrow.clockwise",
                                 kind: .quiet, compact: true) { guardian.tick(); reload() }
                    }
                    if guardian.connections.isEmpty {
                        Text("browser.live.none")
                            .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                    }
                    ForEach(guardian.connections) { connection in
                        connectionRow(connection)
                    }
                }
            }

            if !guardian.profileIssues.isEmpty {
                HnCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Standard profile checks")
                            .font(.system(.callout).weight(.medium))
                            .foregroundStyle(HnPalette.ink)
                        ForEach(guardian.profileIssues) { issue in
                            profileIssueRow(issue)
                        }
                    }
                }
            }

            HnCard {
                VStack(alignment: .leading, spacing: 0) {
                    if candidates.isEmpty {
                        Text("No browsers found.")
                            .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                    }
                    ForEach(Array(candidates.enumerated()), id: \.element.id) { index, c in
                        HStack(alignment: .center, spacing: 8) {
                            Text(verbatim: c.name)
                                .font(.system(.callout).weight(.medium))
                                .foregroundStyle(HnPalette.ink)
                            HnExplain { Text(verbatim: describe(c.coverage)) }
                            if !guardian.connections.contains(where: { $0.bundleID == c.bundleID }) {
                                Text("browser.live.notRunning")
                                    .font(.hnCaption()).foregroundStyle(HnPalette.faint)
                            }
                            Spacer(minLength: 8)
                            if BrowserGuardPolicy.canAllowException(bundleID: c.bundleID,
                                linked: Set(NativeMessagingInstaller.browsers.map(\.bundleID))) {
                                Toggle(isOn: Binding(
                                    get: { c.coverage == .allowedByUser },
                                    set: { allow in
                                        do { try guardian.setAllowed(c.bundleID, allow) }
                                        catch { self.error = error.localizedDescription }
                                        reload()
                                    })) {
                                    Text("Trust this app without extension checks")
                                        .font(.hnCaption())
                                        .foregroundStyle(HnPalette.muted)
                                }
                                .toggleStyle(.switch)
                                .controlSize(.small)
                                .disabled(isLocked && c.coverage == .uncovered)
                            }
                        }
                        .padding(.vertical, 7)
                        if index < candidates.count - 1 { Divider() }
                    }
                }
            }
            if let error {
                Text(error).font(.hnCaption()).foregroundStyle(HnPalette.danger)
            }
        }
        .onAppear(perform: reload)
        .onChange(of: guardian.connections.map(\.id).sorted()) { _ in reload() }
        .onChange(of: guardian.effectiveAllowed) { _ in reload() }
    }

    private func connectionRow(_ connection: BrowserGuard.Connection) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack {
                Text(verbatim: connection.name).font(.callout.weight(.medium))
                Spacer()
                Text(verbatim: "PID \(connection.id)")
                    .font(.caption2.monospaced()).foregroundStyle(.secondary)
            }
            switch connection.state {
            case .connected:
                Label("browser.live.connected", systemImage: "checkmark.shield")
                    .foregroundStyle(.green)
            case .awaitingExtension:
                Label("browser.live.waiting", systemImage: "antenna.radiowaves.left.and.right.slash")
                    .foregroundStyle(.orange)
            case .profileUnprotected:
                Label("browser.live.profileLoss", systemImage: "exclamationmark.shield")
                    .foregroundStyle(.orange)
            case .unsupported:
                Label("browser.live.unsupported", systemImage: "exclamationmark.triangle")
                    .foregroundStyle(.orange)
            case .exempt:
                Label("browser.live.exempt", systemImage: "info.circle")
                    .foregroundStyle(.secondary)
            }
            if let lastSeen = connection.lastSeen {
                HStack {
                    Text("browser.live.lastReply")
                    Text(lastSeen, style: .time)
                }.font(.caption).foregroundStyle(.secondary)
            }
            if let verdict = connection.verdict {
                switch verdict {
                case .ok: EmptyView()
                case let .warn(_, closeAt):
                    let seconds = max(0, Int(ceil(closeAt.timeIntervalSince(connection.checkedAt))))
                    Text("browser.live.countdown \(seconds)")
                        .font(.callout.weight(.medium)).foregroundStyle(.orange)
                case .close:
                    Text("browser.live.closing").font(.callout).foregroundStyle(.red)
                }
            }
        }
        .font(.hnCaption())
        .fixedSize(horizontal: false, vertical: true)
        .padding(8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(HnPalette.soft,
                    in: RoundedRectangle(cornerRadius: 7, style: .continuous))
    }

    private func profileIssueRow(_ issue: BrowserGuard.ProfileIssue) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(verbatim: issue.browserName).font(.body.weight(.medium))
            if !issue.unprotected.isEmpty {
                HStack(spacing: 6) {
                    Label("Extension removed or disabled: \(issue.unprotected.joined(separator: ", "))",
                          systemImage: "exclamationmark.shield")
                        .font(.caption).foregroundStyle(HnPalette.ember)
                    HnExplain {
                        Text("Restore Hisn in these profiles; browser guarding can close this browser.")
                    }
                }
            }
            if !issue.unconfirmed.isEmpty {
                Label("Not confirmed: \(issue.unconfirmed.joined(separator: ", "))",
                      systemImage: "questionmark.circle")
                    .font(.callout).foregroundStyle(.secondary)
            }
            if !issue.enumerationVerified {
                HStack(spacing: 6) {
                    Text("Not verified")
                        .font(.caption).foregroundStyle(.secondary)
                    HnExplain {
                        Text("The standard profile list could not be verified. This unknown result alone does not close the browser.")
                    }
                }
            }
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    private func reload() { candidates = guardian.candidates() }

    private func describe(_ coverage: BrowserGuardPolicy.Coverage) -> String {
        switch coverage {
        case .exempt:
            return String(localized: "Link router only — left open because it hands links to a browser and does not render pages.")
        case .needsExtension:
            return String(localized: """
                Closed while browser guarding is active if its extension stops \
                checking in or is confirmed missing or disabled in a standard profile.
                """)
        case .uncovered:
            return String(localized: "No Hisn protection inside — closed while browser guarding is active.")
        case .allowedByUser:
            return String(localized: "Trusted app exception — stays open without extension checks. Only use this for a non-browser app.")
        }
    }
}

/// The accountability partner's key: set up together, before a lock.
///
/// The partner makes a key pair on their own device with the Hisn Partner page
/// (`partner/index.html`) and reads out, or sends, the public half. Hisn keeps
/// only that half — nothing here can approve anything by itself.
struct PartnerSection: View {
    let isLocked: Bool
    let managed: ManagedMode

    @State private var keyText = ""
    @State private var current = PartnerService.currentKey()
    @State private var message: String?
    @State private var isError = false

    var body: some View {
        HnGroup(title: "Accountability partner",
                subtitle: """
                    Someone you trust who can end a lock early by approving \
                    it — immediately, but never without them. They open the Hisn \
                    Partner page on their own phone or computer, create a key \
                    there, and send you the code it shows under “Your key”.
                    """) {
            HnGroupRow {
                VStack(alignment: .leading, spacing: 10) {
                    if let current {
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            Text(verbatim: String(localized: "Partner key set · \(PartnerService.fingerprint(current))"))
                                .font(.hnBody())
                                .foregroundStyle(HnPalette.calm)
                            HnExplain {
                                Text("Check with your partner that their page shows the same fingerprint.")
                            }
                            Spacer(minLength: 8)
                            HnButton(title: "Remove", kind: .quiet, compact: true) {
                                save(nil)
                            }
                            .help("Removing the key removes the immediate exit. It is always allowed; setting a new one waits for no lock.")
                        }
                    }
                    if (!isLocked && !managed.isManaged) || current == nil {
                        HStack(spacing: 8) {
                            TextField("HISN-PK-…", text: $keyText)
                                .hnField(monospaced: true)
                            HnButton(title: current == nil ? "Save key" : "Replace key",
                                     kind: .quiet) {
                                save(keyText)
                            }
                            .disabled(keyText.trimmingCharacters(in: .whitespaces).isEmpty || isLocked || managed.isManaged)
                        }
                        if managed.isManaged {
                            Text("Managed by \(managed.displayName). Changing the partner key requires your administrator.")
                                .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                        } else if isLocked {
                            Text("A lock is running, so a partner key can only be added once it ends.")
                                .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                        }
                    }
                    if let message {
                        Text(verbatim: message).font(.hnCaption())
                            .foregroundStyle(isError ? HnPalette.danger : HnPalette.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
    }

    private func save(_ key: String?) {
        do {
            try PartnerService.saveKey(key, locked: isLocked || managed.isManaged)
            FilterSync.soon()
            current = PartnerService.currentKey()
            keyText = ""
            isError = false
            message = key == nil ? String(localized: "Partner key removed.")
                                 : String(localized: "Partner key saved.")
        } catch {
            isError = true
            message = error.localizedDescription
        }
    }
}

/// The partner route out of a running lock: send the code, paste the approval.
struct PartnerReleaseCard: View {
    @ObservedObject var lock: LockManager

    @State private var approval = ""
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        if PartnerService.currentKey() == nil {
            HStack(spacing: 8) {
                Text("No partner set up.").font(.hnCallout()).foregroundStyle(HnPalette.muted)
                HnExplain {
                    Text("""
                         No accountability partner is set up, so the only way out is the \
                         request below. You can add a partner in Settings after this lock.
                         """)
                }
                Spacer(minLength: 0)
            }
        } else if let code = lock.partnerChallenge {
            VStack(alignment: .leading, spacing: 8) {
                Text("1. Send this code to your partner:").font(.hnCallout())
                HStack(spacing: 8) {
                    Text(verbatim: code)
                        .font(.system(.callout, design: .monospaced))
                        .foregroundStyle(HnPalette.ink)
                        .textSelection(.enabled)
                    Spacer(minLength: 8)
                    Button("Copy") {
                        NSPasteboard.general.clearContents()
                        NSPasteboard.general.setString(code, forType: .string)
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                }
                HStack(spacing: 6) {
                    Text("2. Paste their approval here:").font(.hnCallout())
                    HnExplain {
                        Text("""
                             If they agree, their Hisn Partner page gives them an \
                             approval to send back.
                             """)
                    }
                    Spacer(minLength: 0)
                }
                HStack(spacing: 8) {
                    TextField("HISN-OK-…", text: $approval)
                        .hnField(monospaced: true)
                    HnButton(title: "End the lock", kind: .quiet) { release() }
                        .disabled(approval.trimmingCharacters(in: .whitespaces).isEmpty || busy)
                }
                if let error {
                    Text(error).font(.hnCaption()).foregroundStyle(HnPalette.danger)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private func release() {
        busy = true
        Task {
            defer { busy = false }
            do {
                try await lock.releaseWithPartnerApproval(approval)
                error = nil
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
}

private struct SettingsPage: View {
    let isLocked: Bool
    let managed: ManagedMode

    @State private var settings = Inspection.Settings.default
    @State private var saved = Inspection.Settings.default
    @State private var message: String?
    @State private var isError = false
    @State private var showBrowserHelp = false

    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            HnPageHeader(eyebrow: "Settings", title: "How Hisn checks",
                         caption: "The detail behind the status.")
            if managed.isManaged {
                ManagedBanner(managed: managed)
            }

            HnGroup(title: "Content checking") {
                HnGroupRow {
                    HnToggleRow(title: "Match keywords in addresses",
                                detail: "Catches a site registered today, before any list has it. Works in every app on this Mac.",
                                isOn: $settings.hostKeywords)
                }
                HnHairline(inset: 16)
                HnGroupRow {
                    HnToggleRow(title: "Check the words on a page",
                                detail: "Checks changing page text in supported Chromium browsers with Hisn connected. Detected X/Twitter posts are hidden individually; other sites use page-level checking. Images and videos without meaningful text can be missed.",
                                isOn: $settings.text)
                }
                HnHairline(inset: 16)
                HnGroupRow {
                    VStack(alignment: .leading, spacing: 7) {
                        HStack(spacing: 6) {
                            Text("How strict")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundStyle(HnPalette.ink)
                            HnExplain {
                                Text("""
                                     Higher catches more, and blocks more pages that turn out \
                                     to be innocent. Medical and reference sites are exempt \
                                     from this check at any setting.
                                     """)
                            }
                            Spacer()
                            HnBadge(verbatim: "\(settings.textSensitivity)")
                        }
                        Slider(value: Binding(
                            get: { Double(settings.textSensitivity) },
                            set: { settings.textSensitivity = Int($0) }
                        ), in: 0...100, step: 5) {
                            Text("How strict")
                        }
                        .labelsHidden()
                        .tint(HnPalette.accent)
                        .disabled(!settings.text)
                    }
                    .opacity(settings.text ? 1 : 0.5)
                }
                HnHairline(inset: 16)
                HnGroupRow {
                    SaveRow(message: message, isError: isError,
                            canSave: settings != saved, save: save, revert: load)
                }
            }

            HnGroup(title: "Browser extension") {
                HnGroupRow {
                    ExtensionVisualGuidance()
                }
                HnHairline(inset: 16)
                HnGroupRow {
                    HnLinkRow(systemImage: "puzzlepiece.extension",
                              title: "How to connect a browser") {
                        showBrowserHelp = true
                    }
                }
                HnHairline(inset: 16)
                HnGroupRow {
                    HnLinkRow(systemImage: "shield.lefthalf.filled",
                              title: "How Hisn works") {
                        AppNavigation.shared.welcomeRequested = true
                    }
                }
            }

            PartnerSection(isLocked: isLocked, managed: managed)
            GrayscaleSection(isLocked: isLocked, managed: managed)
            LanguageSection()
            DetailsSection()
        }
        .onAppear(perform: load)
        .sheet(isPresented: $showBrowserHelp) { BrowserSetupHelp() }
    }

    private func load() {
        saved = Inspection.read()
        settings = saved
        message = nil
    }

    private func save() {
        do {
            try Inspection.save(settings, locked: isLocked || managed.isManaged)
            FilterSync.soon()
            saved = settings
            isError = false
            message = String(localized: "Saved.")
        } catch {
            isError = true
            message = error.localizedDescription
        }
    }
}

/// The whole screen in gray. See `ScreenGrayscale` for how, and why a lock
/// keeps it on.
private struct GrayscaleSection: View {
    let isLocked: Bool
    let managed: ManagedMode
    @ObservedObject private var grayscale = ScreenGrayscale.shared

    var body: some View {
        HnGroup(title: "Gray screen",
                subtitle: "Shows everything on this Mac in shades of gray. Apps keep working; images and videos lose most of their pull.") {
            HnGroupRow {
                VStack(alignment: .leading, spacing: 7) {
                    HnToggleRow(title: "Make the screen gray",
                                isOn: Binding(get: { grayscale.isOn }, set: { grayscale.set($0) }))
                        .disabled(!ScreenGrayscale.isAvailable
                                  || (grayscale.isOn && (isLocked || managed.isManaged)))

                    if !ScreenGrayscale.isAvailable {
                        Text("This version of macOS does not let Hisn change the screen. Turn on Grayscale in System Settings › Accessibility › Display › Color Filters instead.")
                            .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    } else if grayscale.isOn && managed.isManaged {
                        Text("Managed by \(managed.displayName). The screen stays gray while the organization requires it.")
                            .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    } else if grayscale.isOn && isLocked {
                        Text("A lock is running. The screen stays gray until it ends.")
                            .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
    }
}

/// The app's language. The extension has its own switch in its popup; the
/// two are set separately because each lives in a different program.
private struct LanguageSection: View {
    @ObservedObject private var lock = LockManager.shared
    @State private var choice = AppLanguage.chosen

    var body: some View {
        HnGroup(title: "Language") {
            HnGroupRow {
                VStack(alignment: .leading, spacing: 8) {
                    Picker("Language", selection: $choice) {
                        ForEach(AppLanguage.allCases) { Text(verbatim: $0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                    .onChange(of: choice) { AppLanguage.choose($0) }

                    if AppLanguage.needsRestart(for: choice) {
                        if lock.isLocked {
                            Text("Hisn switches language the next time it starts — after this lock, or when you next log in.")
                                .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        } else {
                            HStack {
                                Text("Hisn switches language when it restarts.")
                                    .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                                Spacer()
                                HnButton(title: "Restart now", kind: .text) {
                                    AppLanguage.restart()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/// The numbers. Useful when something is wrong, noise when nothing is — so
/// they live here rather than on the Overview.
private struct DetailsSection: View {
    @State private var showingNotices = false
    private var defaults: UserDefaults? { UserDefaults(suiteName: LockStore.appGroup) }

    var body: some View {
        HnGroup(title: "Details") {
            HnGroupRow {
                VStack(alignment: .leading, spacing: 10) {
                    Grid(alignment: .leading, horizontalSpacing: 16, verticalSpacing: 6) {
                        row("Block list version",
                            value: defaults.map { "\($0.integer(forKey: "listVersion"))" } ?? "—")
                        row("Domains in the filter",
                            value: defaults.map { $0.integer(forKey: "filterDomainCount").formatted() } ?? "—")
                        row("Last list check",
                            value: (defaults?.object(forKey: "lastListCheck") as? Date)?
                                .formatted(date: .abbreviated, time: .shortened) ?? String(localized: "never"))
                        row("Last list update",
                            value: lastListUpdate?.formatted(date: .abbreviated, time: .shortened)
                                ?? String(localized: "never"))
                        if ListUpdateFreshness.isStale(lastUpdate: lastListUpdate) {
                            Text("The block list has not updated in over a week. The previous signed list is still enforced.")
                                .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                        }
                        if listUpdateFailed {
                            Text("The last block-list update failed. The previous signed list is still enforced.")
                                .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                        }
                        row("Browser last connected",
                            value: (defaults?.object(forKey: "extensionLastSeen") as? Date)?
                                .formatted(date: .abbreviated, time: .shortened) ?? String(localized: "never"))
                        row("App version",
                            value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—")
                        row("Guardian daemon",
                            value: guardian.installed ? String(localized: "Installed") : String(localized: "Not installed"))
                        if let action = guardian.action {
                            let when = guardian.at?.formatted(date: .abbreviated, time: .shortened) ?? ""
                            row("Last guardian event",
                                value: GuardianLogStatus.label(for: action) + (when.isEmpty ? "" : " · " + when))
                        }
                    }
                    .font(.system(size: 12))
                    HnButton(title: "Licenses and sources", kind: .text) { showingNotices = true }
                }
            }
        }
        .sheet(isPresented: $showingNotices) { MacNoticesView() }
    }

    private var guardian: GuardianLogStatus { .read() }

    private var lastListUpdate: Date? {
        defaults?.object(forKey: "lastListUpdate") as? Date
    }

    private var listUpdateFailed: Bool {
        defaults?.string(forKey: "lastListError") != nil
    }

    private func row(_ label: LocalizedStringKey, value: String) -> some View {
        GridRow {
            Text(label).foregroundStyle(.secondary)
            Text(verbatim: value).monospacedDigit().textSelection(.enabled)
        }
        .accessibilityElement(children: .combine)
    }
}


/// The bundled third-party texts, shown offline. Generated by script/notices.py.
private struct MacNoticesView: View {
    @Environment(\.dismiss) private var dismiss

    private var text: String? {
        guard let url = Bundle.main.url(forResource: "Notices", withExtension: "txt") else { return nil }
        return try? String(contentsOf: url, encoding: .utf8)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Licenses and sources").font(.title2)
                Spacer()
                Button("Close") { dismiss() }.keyboardShortcut(.cancelAction)
            }
            if let text {
                ScrollView {
                    Text(verbatim: text)
                        .font(.system(.caption, design: .monospaced))
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                }
                .background(Color(nsColor: .textBackgroundColor))
                .clipShape(RoundedRectangle(cornerRadius: 8))
            } else {
                Text("The notices could not be read from this app. Reinstall Hisn from a trusted copy.")
                    .foregroundStyle(.secondary)
            }
        }
        .padding(20)
        .frame(minWidth: 640, minHeight: 540)
    }
}
