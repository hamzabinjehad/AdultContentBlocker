import SwiftUI
import FamilyControls
import UserNotifications

/// The apps the person chose to hold back, and how Hisn holds them.
///
/// One rule, one guard, and an editor per rule. The row is the promise in a
/// sentence; everything else — the picker, the opens, the window — is one tap
/// further in, so the list stays a list of decisions rather than a settings
/// wall.
struct AppsView: View {
    @ObservedObject var protection: ProtectionController
    @ObservedObject var usage: AppUsageController
    let onProtection: () -> Void
    let onPause: () -> Void

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var editingGuard = false
    @State private var editingRule = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HnIntro(title: "apps.screen.title", subtitle: "apps.screen.subtitle")
                statusPill
                ruleCard
                guardCard
                tiles
                scopeNote
                usageError
            }
            .padding(.horizontal, 18)
            .padding(.top, 6)
            .padding(.bottom, 104)
            .hnColumn(regular: horizontalSizeClass == .regular)
        }
        .background(HnPalette.canvas)
        .refreshable { usage.refresh(trustedNow: protection.trustedNow) }
        .sheet(isPresented: $editingRule) {
            RuleEditorView(protection: protection, usage: usage, guardMode: false)
        }
        .sheet(isPresented: $editingGuard) {
            RuleEditorView(protection: protection, usage: usage, guardMode: true)
        }
        .onAppear { usage.refresh(trustedNow: protection.trustedNow) }
    }

    // MARK: - Status

    private var statusPill: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(dotTint)
                .frame(width: 8, height: 8)
                .accessibilityHidden(true)
            Text(statusText)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(HnPalette.ink)
            Spacer(minLength: 0)
            if let opens = usage.opensLeft, usage.configuration?.mode == .pause {
                HnBadge(text: opens == 1 ? "apps.screen.opensLeft.one \(opens)"
                                         : "apps.screen.opensLeft \(opens)",
                        tint: opens > 0 ? HnPalette.calm : HnPalette.ember)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(HnPalette.card, in: Capsule())
        .overlay { Capsule().strokeBorder(HnPalette.line, lineWidth: 1) }
        .accessibilityElement(children: .combine)
    }

    private var dotTint: Color {
        guard let config = usage.configuration else { return HnPalette.muted }
        if config.mode == .pause, usage.opensLeft == 0, !usage.pauseSessionActive {
            return HnPalette.ember
        }
        return HnPalette.calm
    }

    private var statusText: LocalizedStringKey {
        guard let config = usage.configuration else { return "apps.screen.noRule" }
        if config.mode == .pause {
            if usage.pauseSessionActive { return "rule.pause.status.session" }
            if usage.pauseBudgetDamaged { return "apps.screen.unreadable" }
            return (usage.opensLeft ?? 0) > 0 ? "apps.screen.pauseActive" : "apps.screen.closedToday"
        }
        return "apps.screen.blocking"
    }

    // MARK: - Rules

    @ViewBuilder private var ruleCard: some View {
        if let config = usage.configuration {
            Button { editingRule = true } label: {
                ruleRow(config)
            }
            .buttonStyle(.plain)
            .disabled(!canEdit)
            .accessibilityIdentifier("apps.ruleRow")
        } else {
            Button { editingRule = true } label: {
                HnCard {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(spacing: 12) {
                            HnAppIcons(applicationTokens: [], categoryTokens: [],
                                       emptySymbol: "plus", size: 40)
                            VStack(alignment: .leading, spacing: 3) {
                                Text("apps.empty.title")
                                    .font(.hnHeadline())
                                    .foregroundStyle(HnPalette.ink)
                                Text("apps.empty.detail")
                                    .font(.footnote)
                                    .foregroundStyle(HnPalette.muted)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            Spacer(minLength: 0)
                        }
                        HnButton(title: "apps.empty.action", systemImage: "plus",
                                 kind: .filled, fullWidth: false) { editingRule = true }
                    }
                }
            }
            .buttonStyle(.plain)
            .disabled(!canEdit)
            .accessibilityIdentifier("apps.ruleRow")
        }
    }

    private func ruleRow(_ config: AppUsageConfiguration) -> some View {
        HnCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .top, spacing: 14) {
                    HnAppIcons(applicationTokens: config.selection.applicationTokens,
                               categoryTokens: config.selection.categoryTokens, size: 38)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(RuleSummary.name(config.mode))
                            .font(.hnHeadline())
                            .foregroundStyle(HnPalette.ink)
                        Text(RuleSummary.sentence(config))
                            .font(.footnote)
                            .foregroundStyle(HnPalette.muted)
                            .fixedSize(horizontal: false, vertical: true)
                            .multilineTextAlignment(.leading)
                        if let days = RuleSummary.daysPhrase(config) {
                            Text(days)
                                .font(.caption.weight(.medium))
                                .foregroundStyle(HnPalette.muted)
                        }
                    }
                    Spacer(minLength: 8)
                    Image(systemName: "chevron.forward")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(HnPalette.muted.opacity(0.7))
                        .accessibilityHidden(true)
                }
                if config.mode == .pause {
                    pauseMeter(config)
                }
            }
        }
    }

    /// The opens as dots: filled is spent, hollow is left. The count is written
    /// beside them, so the dots are never the only reading.
    private func pauseMeter(_ config: AppUsageConfiguration) -> some View {
        let opens = max(config.opens, 1)
        let used = max(0, opens - (usage.opensLeft ?? opens))
        let status: LocalizedStringKey = usage.pauseSessionActive
            ? "rule.pause.status.session"
            : (RuleSummary.status(config, opensLeft: usage.opensLeft, sessionActive: false)
                ?? "rule.status.on")
        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 7) {
                ForEach(0..<opens, id: \.self) { index in
                    Circle()
                        .fill(index < used ? HnPalette.muted.opacity(0.28) : HnPalette.accent)
                        .frame(width: 9, height: 9)
                }
                Spacer(minLength: 0)
                Text(status)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(HnPalette.muted)
            }
            .accessibilityHidden(true)
            HStack(spacing: 8) {
                Text("apps.pauseMinutes \(config.minutes)")
                    .font(.caption)
                    .foregroundStyle(HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 8)
                if usage.pauseSessionActive {
                    Button {
                        usage.endPauseSessionNow()
                    } label: {
                        Text("today.session.end")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(HnPalette.accentText)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    @ViewBuilder private var guardCard: some View {
        if let guardRule = usage.guardConfiguration {
            Button { editingGuard = true } label: {
                HnCard {
                    HStack(alignment: .top, spacing: 14) {
                        HnAppIcons(applicationTokens: guardRule.selection.applicationTokens,
                                   categoryTokens: guardRule.selection.categoryTokens,
                                   emptySymbol: "network", size: 38)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("apps.guard.title")
                                .font(.hnHeadline())
                                .foregroundStyle(HnPalette.ink)
                            Text("apps.guard.detail")
                                .font(.footnote)
                                .foregroundStyle(HnPalette.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 8)
                        HnBadge(text: "rule.status.on")
                    }
                }
            }
            .buttonStyle(.plain)
            .disabled(!canEdit)
            .accessibilityIdentifier("apps.guardRow")
        } else {
            Button { editingGuard = true } label: {
                HnCard {
                    HStack(alignment: .top, spacing: 14) {
                        Image(systemName: "network")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(HnPalette.accent)
                            .frame(width: 38, height: 38)
                            .background(HnPalette.accentSoft,
                                        in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("apps.guard.title")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(HnPalette.ink)
                            Text("apps.guard.none")
                                .font(.footnote)
                                .foregroundStyle(HnPalette.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 8)
                        Image(systemName: "chevron.forward")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(HnPalette.muted.opacity(0.7))
                            .accessibilityHidden(true)
                    }
                }
            }
            .buttonStyle(.plain)
            .disabled(!canEdit)
            .accessibilityIdentifier("apps.guardRow")
        }
    }

    // MARK: - Tiles

    private var tiles: some View {
        HStack(spacing: 12) {
            Button(action: onPause) {
                tile(systemImage: "wind", title: "apps.tile.pause",
                     detail: pauseDetail, tint: HnPalette.accent)
            }
            .buttonStyle(.plain)
            Button(action: onProtection) {
                tile(systemImage: "shield.lefthalf.filled", title: "apps.tile.protection",
                     detail: "apps.tile.protection.detail \(protection.configuredContentLayers)",
                     tint: HnPalette.calm)
            }
            .buttonStyle(.plain)
        }
    }

    private var pauseDetail: LocalizedStringKey {
        if let count = usage.opensLeft, usage.configuration?.mode == .pause {
            return count == 1 ? "apps.tile.pause.detail.one \(count)"
                              : "apps.tile.pause.detail \(count)"
        }
        return "apps.tile.pause.ready"
    }

    private func tile(systemImage: String, title: LocalizedStringKey, detail: LocalizedStringKey,
                      tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: systemImage)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(tint)
                .frame(width: 44, height: 44)
                .background(tint.opacity(0.12), in: Circle())
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(HnPalette.ink)
                Text(detail)
                    .font(.caption)
                    .foregroundStyle(HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, minHeight: 132, alignment: .topLeading)
        .padding(16)
        .background(HnPalette.card, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .strokeBorder(HnPalette.line, lineWidth: 1)
        }
        .contentShape(Rectangle())
    }

    // MARK: - Notes

    private var scopeNote: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("apps.screen.scope")
            Text(protection.rulesLocked ? "apps.screen.locked" : "apps.screen.permission")
        }
        .font(.caption)
        .foregroundStyle(HnPalette.muted)
        .fixedSize(horizontal: false, vertical: true)
        .padding(.horizontal, 4)
    }

    @ViewBuilder private var usageError: some View {
        if let error = usage.errorKey {
            Label(LocalizedStringKey(error), systemImage: "exclamationmark.triangle")
                .font(.footnote)
                .foregroundStyle(HnPalette.ember)
        }
        if let guardError = usage.guardErrorKey {
            Label(LocalizedStringKey(guardError), systemImage: "exclamationmark.triangle")
                .font(.footnote)
                .foregroundStyle(HnPalette.ember)
        }
    }

    private var canEdit: Bool {
        protection.commitmentStorageHealthy && !protection.rulesLocked
    }
}

/// The editor for one rule: the apps, the way Hisn holds them, and the numbers
/// that way needs. It is a sheet because it changes protection, not something
/// to scroll past.
struct RuleEditorView: View {
    @ObservedObject var protection: ProtectionController
    @ObservedObject var usage: AppUsageController
    let guardMode: Bool
    /// The render tests open one mode without saving it; production always
    /// starts from the saved rule.
    var initialMode: AppUsageConfiguration.Mode?

    @Environment(\.dismiss) private var dismiss
    @Environment(\.locale) private var locale
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var selection = FamilyActivitySelection()
    @State private var mode = AppUsageConfiguration.Mode.pause
    @State private var minutes = PauseRule.defaultSessionMinutes
    @State private var opens = PauseRule.defaultOpens
    @State private var windowStart = AppUsageConfiguration.defaultWindowStart
    @State private var windowEnd = AppUsageConfiguration.defaultWindowEnd
    @State private var days = AppUsageConfiguration.allDays
    @State private var selecting = false
    @State private var confirmSave = false
    @State private var confirmRemove = false
    @State private var errorKey: String?
    @State private var notificationsAllowed = true
    @State private var askedForNotifications = false

    private var locked: Bool { protection.rulesLocked }
    private var storageHealthy: Bool { protection.commitmentStorageHealthy }

    private var chosenCount: Int {
        selection.applicationTokens.count + selection.categoryTokens.count + selection.webDomainTokens.count
    }

    private var windowValid: Bool {
        mode != .quietHours
            || (AppUsageConfiguration.isValidWindow(start: windowStart, end: windowEnd)
                && AppUsageConfiguration.isValidDays(days))
    }

    private var canSave: Bool {
        !locked && storageHealthy && usage.authorized && chosenCount > 0 && windowValid
            && (!guardMode || mode == .always)
    }

    private var effectiveMode: AppUsageConfiguration.Mode { guardMode ? .always : mode }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    appsCard
                    if !guardMode { modeCard }
                    parametersCard
                    saveArea
                    scopeNote
                }
                .padding(.horizontal, 18)
                .padding(.top, 10)
                .padding(.bottom, 40)
                .hnColumn(regular: horizontalSizeClass == .regular)
            }
            .background(HnPalette.canvas)
            .navigationTitle(guardMode ? Text("apps.guard.title") : Text("apps.editor.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("cancel") { dismiss() }
                }
            }
        }
        .familyActivityPicker(isPresented: $selecting, selection: $selection)
        .alert("apps.consent", isPresented: $confirmSave) {
            Button("apps.apply") { save() }
            Button("cancel", role: .cancel) { }
        } message: {
            Text(guardMode ? "apps.guard.consentBody \(chosenCount)" : consentBody)
        }
        .alert("apps.remove.confirm", isPresented: $confirmRemove) {
            Button("apps.remove", role: .destructive) { remove() }
            Button("cancel", role: .cancel) { }
        } message: {
            Text("apps.remove.body")
        }
        .onAppear { load() }
        .task {
            // Only the pre-26.5 handoff needs a notification, and only then is
            // it worth asking.
            if shieldHandoffNeedsNotifications { await loadNotificationState() }
        }
    }

    private var consentBody: LocalizedStringKey {
        mode == .quietHours ? "apps.windowConsent" : "apps.consentBody"
    }

    // MARK: - Apps

    private var appsCard: some View {
        HnCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("apps.editor.apps")
                        .font(.hnHeadline())
                        .foregroundStyle(HnPalette.ink)
                    Spacer(minLength: 8)
                    HnBadge(text: "apps.editor.count \(chosenCount)", tint: HnPalette.accentText)
                }
                if chosenCount == 0 {
                    Text(guardMode ? "apps.editor.noApps" : "apps.editor.noApps")
                        .font(.footnote)
                        .foregroundStyle(HnPalette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(Array(selection.applicationTokens.prefix(5)), id: \.self) { token in
                            HStack(spacing: 12) {
                                Label(token)
                                    .labelStyle(.iconOnly)
                                    .frame(width: 30, height: 30)
                                Label(token)
                                    .font(.subheadline.weight(.medium))
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        if !selection.categoryTokens.isEmpty {
                            Label("apps.editor.categories", systemImage: "square.grid.2x2.fill")
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(HnPalette.ink)
                        }
                        if !selection.webDomainTokens.isEmpty {
                            Label("apps.editor.domains", systemImage: "globe")
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(HnPalette.ink)
                        }
                    }
                }
                HnButton(title: chosenCount == 0 ? "apps.choose" : "apps.chooseAgain",
                         systemImage: "plus", kind: .quiet) { selecting = true }
                    .disabled(locked || !storageHealthy)
            }
        }
    }

    // MARK: - Mode

    private var modeCard: some View {
        HnCard {
            VStack(alignment: .leading, spacing: 4) {
                Text("apps.editor.how")
                    .font(.hnHeadline())
                    .foregroundStyle(HnPalette.ink)
                    .padding(.bottom, 6)
                ForEach(modes, id: \.self) { option in
                    HnOptionRow(systemImage: RuleSummary.icon(option),
                                title: RuleSummary.name(option),
                                detail: modeDetail(option),
                                selected: mode == option) {
                        mode = option
                        if option == .pause {
                            minutes = min(max(minutes, PauseRule.sessionRange.lowerBound),
                                          PauseRule.sessionRange.upperBound)
                        }
                    }
                    .disabled(locked || !storageHealthy)
                }
            }
        }
    }

    private var modes: [AppUsageConfiguration.Mode] { [.pause, .always, .dailyBudget, .quietHours] }

    private func modeDetail(_ mode: AppUsageConfiguration.Mode) -> LocalizedStringKey {
        switch mode {
        case .pause: return "apps.mode.pause.detail"
        case .always: return "apps.mode.always.detail"
        case .dailyBudget: return "apps.mode.budget.detail"
        case .quietHours: return "apps.mode.quiet.detail"
        }
    }

    // MARK: - Numbers

    @ViewBuilder private var parametersCard: some View {
        if effectiveMode != .always {
            HnCard {
                VStack(alignment: .leading, spacing: 16) {
                    Text("apps.editor.tuning")
                        .font(.hnHeadline())
                        .foregroundStyle(HnPalette.ink)
                    switch effectiveMode {
                    case .pause: pauseTuning
                    case .dailyBudget: budgetTuning
                    case .quietHours: quietTuning
                    case .always: EmptyView()
                    }
                }
            }
        }
    }

    private var pauseTuning: some View {
        VStack(alignment: .leading, spacing: 16) {
            HnStepper(title: "apps.editor.opens", value: opens, range: PauseRule.opensRange,
                      suffix: "apps.editor.opens.suffix") { opens = $0 }
            Divider().overlay(HnPalette.line)
            HnStepper(title: "apps.editor.minutes", value: minutes, range: PauseRule.sessionRange,
                      suffix: "apps.editor.minutes.suffix") { minutes = $0 }
            Text("apps.pause.explain \(opens) \(minutes)")
                .font(.footnote)
                .foregroundStyle(HnPalette.muted)
                .fixedSize(horizontal: false, vertical: true)
            Text("apps.pause.handoff")
                .font(.caption)
                .foregroundStyle(HnPalette.muted)
                .fixedSize(horizontal: false, vertical: true)
            if shieldHandoffNeedsNotifications && !notificationsAllowed {
                VStack(alignment: .leading, spacing: 8) {
                    Label("apps.pause.notify.body", systemImage: "bell.badge")
                        .font(.caption)
                        .foregroundStyle(HnPalette.ember)
                        .fixedSize(horizontal: false, vertical: true)
                    HnButton(title: askedForNotifications ? "apps.pause.notify.settings"
                                                          : "apps.pause.notify.allow",
                             kind: .outline, fullWidth: false) {
                        Task { await askForNotifications() }
                    }
                }
            }
        }
    }

    /// iOS 26.5 can open Hisn from the shield itself; before that the shield
    /// posts a notification, which needs permission.
    private var shieldHandoffNeedsNotifications: Bool {
        if #available(iOS 26.5, *) { return false }
        return true
    }

    private func loadNotificationState() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        notificationsAllowed = settings.authorizationStatus == .authorized
            || settings.authorizationStatus == .provisional
    }

    private func askForNotifications() async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        if settings.authorizationStatus == .denied {
            askedForNotifications = true
            if let url = URL(string: UIApplication.openSettingsURLString) {
                await UIApplication.shared.open(url)
            }
            return
        }
        askedForNotifications = true
        let granted = (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false
        notificationsAllowed = granted
    }

    private var budgetTuning: some View {
        VStack(alignment: .leading, spacing: 10) {
            HnStepper(title: "apps.editor.dailyMinutes", value: minutes, range: 15...240, step: 15,
                      suffix: "apps.editor.dailyMinutes.suffix") { minutes = $0 }
            Text("apps.budgetScope")
                .font(.footnote)
                .foregroundStyle(HnPalette.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var quietTuning: some View {
        VStack(alignment: .leading, spacing: 12) {
            daysPicker
            Divider().overlay(HnPalette.line)
            HStack {
                Text("apps.window.title")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(HnPalette.ink)
                Spacer(minLength: 8)
                Menu("apps.windowPreset") {
                    Button("apps.windowLateNight") { windowStart = 22 * 60; windowEnd = 7 * 60 }
                    Button("apps.windowEvening") { windowStart = 20 * 60; windowEnd = 23 * 60 }
                    Button("apps.windowWork") { windowStart = 9 * 60; windowEnd = 17 * 60 }
                }
                .font(.footnote.weight(.semibold))
            }
            DatePicker("apps.windowStart", selection: timeBinding($windowStart),
                       displayedComponents: .hourAndMinute)
            DatePicker("apps.windowEnd", selection: timeBinding($windowEnd),
                       displayedComponents: .hourAndMinute)
            if windowValid {
                Text("apps.windowScope")
                    .font(.footnote)
                    .foregroundStyle(HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
                if windowEnd < windowStart {
                    Label("apps.windowOvernight", systemImage: "moon.stars")
                        .font(.footnote)
                        .foregroundStyle(HnPalette.muted)
                }
            } else {
                Label("apps.windowInvalid", systemImage: "exclamationmark.triangle")
                    .font(.footnote)
                    .foregroundStyle(HnPalette.ember)
            }
        }
    }

    /// Monday-first day chips, with the locale's own initials. A window that
    /// crosses midnight is judged by the day it starts on, which is what the
    /// chips select.
    private var daysPicker: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("apps.days.title")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(HnPalette.ink)
                Spacer(minLength: 8)
                Menu("apps.days.preset") {
                    Button("apps.days.all") { days = AppUsageConfiguration.allDays }
                    Button("apps.days.weekdays") { days = AppUsageConfiguration.weekdayMask }
                    Button("apps.days.weekend") { days = AppUsageConfiguration.weekendMask }
                }
                .font(.footnote.weight(.semibold))
            }
            HStack(spacing: 6) {
                ForEach(0..<7, id: \.self) { index in
                    Button {
                        days ^= (1 << index)
                    } label: {
                        Text(verbatim: weekdaySymbols[index])
                            .font(.caption.weight(.bold))
                            .foregroundStyle(days & (1 << index) != 0 ? .white : HnPalette.ink)
                            .frame(width: 36, height: 36)
                            .background(days & (1 << index) != 0 ? HnPalette.accent : HnPalette.soft,
                                        in: Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text(verbatim: weekdayNames[index]))
                    .accessibilityAddTraits(days & (1 << index) != 0 ? .isSelected : [])
                }
            }
            if AppUsageConfiguration.isValidDays(days) {
                Text("apps.days.scope")
                    .font(.caption)
                    .foregroundStyle(HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                Label("apps.days.none", systemImage: "exclamationmark.triangle")
                    .font(.caption)
                    .foregroundStyle(HnPalette.ember)
            }
        }
    }

    /// Monday-first initials and names for the current UI language.
    private var weekdaySymbols: [String] { weekdayParts(short: true) }
    private var weekdayNames: [String] { weekdayParts(short: false) }

    private func weekdayParts(short: Bool) -> [String] {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = locale
        let symbols = short ? calendar.veryShortWeekdaySymbols : calendar.standaloneWeekdaySymbols
        // Foundation lists Sunday first; the rule lists Monday first.
        return (1...7).map { iso in symbols[iso % 7] }
    }

    // MARK: - Save

    private var saveArea: some View {
        VStack(alignment: .leading, spacing: 12) {
            if locked {
                Label("apps.locked", systemImage: "lock.fill")
                    .font(.footnote)
                    .foregroundStyle(HnPalette.muted)
            }
            if !usage.authorized {
                Text("apps.authorization")
                    .font(.footnote)
                    .foregroundStyle(HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if let errorKey {
                Label(LocalizedStringKey(errorKey), systemImage: "exclamationmark.triangle")
                    .font(.footnote)
                    .foregroundStyle(HnPalette.ember)
            }
            HnButton(title: guardMode ? "apps.guard.apply" : "apps.apply",
                     systemImage: "checkmark", kind: .filled) {
                confirmSave = true
            }
            .disabled(!canSave)
            .opacity(canSave ? 1 : 0.45)
            if (guardMode ? usage.guardConfiguration : usage.configuration) != nil {
                HnButton(title: guardMode ? "apps.guard.remove" : "apps.remove",
                         kind: .text) { confirmRemove = true }
                    .disabled(locked || !storageHealthy)
            }
        }
    }

    private var scopeNote: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(guardMode ? "apps.guard.merge" : "apps.scopeShort")
            Text("apps.editor.scope")
        }
        .font(.caption)
        .foregroundStyle(HnPalette.muted)
        .fixedSize(horizontal: false, vertical: true)
        .padding(.horizontal, 4)
    }

    // MARK: - Wiring

    private func load() {
        usage.refresh(trustedNow: protection.trustedNow)
        let saved = guardMode ? usage.guardConfiguration : usage.configuration
        if let saved {
            selection = saved.selection
            mode = saved.mode
            minutes = saved.minutes
            opens = saved.opens
            windowStart = saved.windowStart
            windowEnd = saved.windowEnd
            days = saved.days
        } else {
            mode = guardMode ? .always : (initialMode ?? .pause)
            minutes = guardMode ? 30 : PauseRule.defaultSessionMinutes
            opens = PauseRule.defaultOpens
        }
    }

    private func save() {
        let config = AppUsageConfiguration(selection: selection, mode: effectiveMode,
                                           minutes: minutes, windowStart: windowStart,
                                           windowEnd: windowEnd, opens: opens, days: days)
        if guardMode {
            usage.applyGuard(config, rulesLocked: locked, storageHealthy: storageHealthy)
            if let guardError = usage.guardErrorKey { errorKey = guardError } else { dismiss() }
        } else {
            usage.apply(config, rulesLocked: locked, storageHealthy: storageHealthy,
                        trustedNow: protection.trustedNow)
            if let applyError = usage.errorKey { errorKey = applyError } else { dismiss() }
        }
    }

    private func remove() {
        if guardMode {
            usage.clearGuard(rulesLocked: locked, storageHealthy: storageHealthy)
        } else {
            usage.clear(rulesLocked: locked, storageHealthy: storageHealthy)
        }
        if usage.errorKey == nil && usage.guardErrorKey == nil { dismiss() }
    }

    private func timeBinding(_ minutes: Binding<Int>) -> Binding<Date> {
        Binding(
            get: { Calendar.current.date(bySettingHour: minutes.wrappedValue / 60,
                                         minute: minutes.wrappedValue % 60,
                                         second: 0, of: Date()) ?? Date() },
            set: { date in
                let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
                minutes.wrappedValue = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
            }
        )
    }
}
