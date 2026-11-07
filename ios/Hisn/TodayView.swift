import SwiftUI
import FamilyControls

/// How a saved app rule is described in one line. The rule is the same object
/// everywhere, so the words live in one place and cannot drift between Today
/// and Apps.
enum RuleSummary {
    static func name(_ mode: AppUsageConfiguration.Mode) -> LocalizedStringKey {
        switch mode {
        case .pause: return "apps.pause"
        case .always: return "apps.always"
        case .dailyBudget: return "apps.budget"
        case .quietHours: return "apps.quietHours"
        }
    }

    private static let pauseIcon = "pause.circle"
    private static let alwaysIcon = "hand.raised"
    private static let budgetIcon = "hourglass"
    private static let quietIcon = "moon.stars"

    static func icon(_ mode: AppUsageConfiguration.Mode) -> String {
        switch mode {
        case .pause: return pauseIcon
        case .always: return alwaysIcon
        case .dailyBudget: return budgetIcon
        case .quietHours: return quietIcon
        }
    }

    /// One sentence: what the rule does, in the person's own terms.
    static func sentence(_ config: AppUsageConfiguration) -> LocalizedStringKey {
        switch config.mode {
        case .pause:
            return config.opens == 1
                ? "rule.pause.sentence.one \(config.opens) \(config.minutes)"
                : "rule.pause.sentence \(config.opens) \(config.minutes)"
        case .always:
            return "rule.always.sentence"
        case .dailyBudget:
            return "rule.budget.sentence \(config.minutes)"
        case .quietHours:
            return "rule.quiet.sentence \(time(config.windowStart)) \(time(config.windowEnd))"
        }
    }

    /// Which weekdays a quiet-hours rule runs on, when it is not every day.
    static func daysPhrase(_ config: AppUsageConfiguration) -> LocalizedStringKey? {
        guard config.mode == .quietHours else { return nil }
        if config.days == AppUsageConfiguration.allDays { return "rule.days.everyDay" }
        if config.days == AppUsageConfiguration.weekdayMask { return "rule.days.weekdays" }
        if config.days == AppUsageConfiguration.weekendMask { return "rule.days.weekend" }
        let count = (0..<7).filter { config.days & (1 << $0) != 0 }.count
        return "rule.days.count \(count)"
    }

    /// The badge on the rule row, when there is something worth saying.
    static func status(_ config: AppUsageConfiguration, opensLeft: Int?,
                       sessionActive: Bool) -> LocalizedStringKey? {
        switch config.mode {
        case .pause:
            if sessionActive { return "rule.pause.status.session" }
            guard let opensLeft else { return nil }
            return opensLeft > 0 ? "rule.pause.status.left \(opensLeft)" : "rule.pause.status.none"
        case .always, .dailyBudget, .quietHours:
            return "rule.status.on"
        }
    }

    /// Minutes after midnight as a localized clock time; the format follows the
    /// person's locale, and the string is data, never a localization key.
    static func time(_ minutes: Int) -> String {
        let date = Calendar.current.date(bySettingHour: minutes / 60,
                                         minute: minutes % 60,
                                         second: 0, of: Date()) ?? Date()
        let formatter = DateFormatter()
        formatter.locale = .autoupdatingCurrent
        formatter.timeStyle = .short
        formatter.dateStyle = .none
        return formatter.string(from: date)
    }
}

/// What the person sees first: whether Hisn is holding, the week's practice,
/// and the two things they can do now. The evidence lives one tab away.
struct TodayView: View {
    @ObservedObject var protection: ProtectionController
    @ObservedObject var usage: AppUsageController
    let streak: Int
    let practiceDays: Set<String>
    let pauseCount: Int?
    let onProtection: () -> Void
    let onApps: () -> Void
    let onPause: () -> Void
    let onRefresh: () -> Void

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private var assessment: MobileProtectionPolicy.SetupAssessment { protection.setupAssessment }
    private var layers: Int { protection.configuredContentLayers }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                header
                hero
                sessionStrip
                lockStrip
                StreakCard(streak: streak, practiceDays: practiceDays,
                           now: protection.now, pauseCount: pauseCount)
                appsCard
                practiceCard
                if !assessment.issues.isEmpty { issuesCard }
                errorCard
            }
            .padding(.horizontal, 18)
            .padding(.top, 6)
            .padding(.bottom, 104)
            .hnColumn(regular: horizontalSizeClass == .regular)
        }
        .background(HnPalette.canvas)
        .refreshable { onRefresh() }
    }

    // MARK: - Header

    private var header: some View {
        HStack(spacing: 10) {
            HnMark(size: 26)
            VStack(alignment: .leading, spacing: 0) {
                Text(verbatim: "Hisn")
                    .font(.hnHeadline())
                    .foregroundStyle(HnPalette.ink)
                Text("today.date")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(HnPalette.muted)
                    .textCase(.uppercase)
                    .tracking(0.5)
            }
            Spacer(minLength: 0)
            Button(action: onProtection) {
                Image(systemName: "shield.lefthalf.filled")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(HnPalette.ink)
                    .frame(width: 42, height: 42)
                    .background(HnPalette.card, in: Circle())
                    .overlay { Circle().strokeBorder(HnPalette.line, lineWidth: 1) }
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("today.protection.a11y"))
        }
        .padding(.top, 8)
    }

    // MARK: - Hero

    private var hero: some View {
        HnInkCard {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .top, spacing: 16) {
                    VStack(alignment: .leading, spacing: 8) {
                        HnBadge(text: heroBadge, tint: heroBadgeTint, onInk: true)
                        Text(heroTitle)
                            .font(.system(.title, design: .rounded).weight(.bold))
                            .foregroundStyle(HnPalette.onInk)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityAddTraits(.isHeader)
                        Text(heroDetail)
                            .font(.subheadline)
                            .foregroundStyle(HnPalette.onInkMuted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                    if !dynamicTypeSize.isAccessibilitySize {
                        HnRing(value: Double(layers) / 3, tint: HnPalette.onInkCalm,
                               done: layers, total: 3, onInk: true)
                    }
                }
                if dynamicTypeSize.isAccessibilitySize {
                    HnRing(value: Double(layers) / 3, tint: HnPalette.onInkCalm,
                           done: layers, total: 3, onInk: true)
                }
                layerChips
                HnButton(title: heroAction, systemImage: heroActionIcon,
                         kind: .onInk, loading: protection.busy, action: heroActionTap)
                Text("today.hero.scope")
                    .font(.caption)
                    .foregroundStyle(HnPalette.onInkMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityIdentifier("today.heroCard")
    }

    private var heroBadge: LocalizedStringKey {
        switch assessment.state {
        case .configured: return "today.badge.protected"
        case .needsSetup: return "today.badge.needsStep"
        case .unchecked: return "today.badge.unchecked"
        case .checking: return "today.badge.checking"
        }
    }

    private var heroBadgeTint: Color {
        switch assessment.state {
        case .configured: return HnPalette.calm
        case .needsSetup, .checking: return HnPalette.ember
        case .unchecked: return HnPalette.accent
        }
    }

    private var heroTitle: LocalizedStringKey {
        switch assessment.state {
        case .configured: return "today.title.protected"
        case .needsSetup: return "today.title.needsStep"
        case .unchecked: return "today.title.unchecked"
        case .checking: return "today.title.checking"
        }
    }

    private var heroDetail: LocalizedStringKey {
        switch assessment.state {
        case .configured: return "today.detail.protected"
        case .needsSetup: return "today.detail.needsStep"
        case .unchecked: return "today.detail.unchecked"
        case .checking: return "today.detail.checking"
        }
    }

    private var heroAction: LocalizedStringKey {
        switch assessment.state {
        case .configured, .checking: return "today.action.check"
        case .needsSetup: return "today.action.finish"
        case .unchecked: return "today.action.check"
        }
    }

    private static let refreshIcon = "arrow.clockwise"
    private static let forwardIcon = "arrow.forward"

    private var heroActionIcon: String {
        switch assessment.state {
        case .configured, .checking: return Self.refreshIcon
        case .needsSetup: return Self.forwardIcon
        case .unchecked: return Self.refreshIcon
        }
    }

    private func heroActionTap() {
        switch assessment.state {
        case .needsSetup: onProtection()
        default: onRefresh()
        }
    }

    private var layerChips: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 8) { layerChipsContent }
            VStack(alignment: .leading, spacing: 6) { layerChipsContent }
        }
    }

    @ViewBuilder private var layerChipsContent: some View {
        chip("safari", title: "layer.safari", ready: protection.safariConfigured)
        chip("network", title: "layer.dns", ready: protection.dns == .enabled)
        chip("hourglass", title: "layer.screenTime", ready: protection.screenTimeConfigured)
    }

    private func chip(_ icon: String, title: LocalizedStringKey, ready: Bool) -> some View {
        HStack(spacing: 5) {
            Image(systemName: ready ? "checkmark" : "minus")
                .font(.system(size: 10, weight: .heavy))
                .accessibilityHidden(true)
            Image(systemName: icon)
                .font(.system(size: 11, weight: .semibold))
                .accessibilityHidden(true)
            Text(title)
                .font(.caption.weight(.semibold))
        }
        .foregroundStyle(ready ? HnPalette.onInkCalm : HnPalette.onInkMuted)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(Color.white.opacity(0.08), in: Capsule())
        .accessibilityElement(children: .combine)
        .accessibilityValue(Text(ready ? "plan.configured" : "plan.needsSetup"))
    }

    // MARK: - Apps

    private var appsCard: some View {
        HnCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("today.apps.title")
                        .font(.hnHeadline())
                        .foregroundStyle(HnPalette.ink)
                        .accessibilityAddTraits(.isHeader)
                    Spacer(minLength: 8)
                    Button(action: onApps) {
                        Text("today.apps.seeAll")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(HnPalette.accentText)
                    }
                    .buttonStyle(.plain)
                }
                ruleRow
                if let guardRule = usage.guardConfiguration {
                    Divider().overlay(HnPalette.line)
                    guardRow(guardRule)
                }
            }
        }
    }

    @ViewBuilder private var ruleRow: some View {
        if let config = usage.configuration {
            Button(action: onApps) {
                HStack(spacing: 13) {
                    HnAppIcons(applicationTokens: config.selection.applicationTokens,
                               categoryTokens: config.selection.categoryTokens)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(RuleSummary.name(config.mode))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(HnPalette.ink)
                        Text(RuleSummary.sentence(config))
                            .font(.footnote)
                            .foregroundStyle(HnPalette.muted)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)
                        if let days = RuleSummary.daysPhrase(config) {
                            Text(days)
                                .font(.caption.weight(.medium))
                                .foregroundStyle(HnPalette.muted)
                        }
                    }
                    Spacer(minLength: 8)
                    if let status = RuleSummary.status(config, opensLeft: usage.opensLeft,
                                                       sessionActive: usage.pauseSessionActive) {
                        HnBadge(text: status, tint: badgeTint(config))
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        } else {
            Button(action: onApps) {
                HStack(spacing: 13) {
                    HnAppIcons(applicationTokens: [], categoryTokens: [],
                               emptySymbol: "plus", size: 34)
                    VStack(alignment: .leading, spacing: 3) {
                        Text("today.apps.empty.title")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(HnPalette.ink)
                        Text("today.apps.empty.detail")
                            .font(.footnote)
                            .foregroundStyle(HnPalette.muted)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 8)
                    Image(systemName: "chevron.forward")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(HnPalette.muted.opacity(0.7))
                        .accessibilityHidden(true)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    private func badgeTint(_ config: AppUsageConfiguration) -> Color {
        guard config.mode == .pause else { return HnPalette.accent }
        return (usage.opensLeft ?? 0) > 0 || usage.pauseSessionActive ? HnPalette.calm : HnPalette.ember
    }

    private func guardRow(_ config: AppUsageConfiguration) -> some View {
        Button(action: onApps) {
            HStack(spacing: 13) {
                HnAppIcons(applicationTokens: config.selection.applicationTokens,
                           categoryTokens: config.selection.categoryTokens,
                           emptySymbol: "network", size: 30)
                VStack(alignment: .leading, spacing: 3) {
                    Text("today.apps.guard.title")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(HnPalette.ink)
                    Text("today.apps.guard.detail")
                        .font(.footnote)
                        .foregroundStyle(HnPalette.muted)
                }
                Spacer(minLength: 8)
                HnBadge(text: "rule.status.on", tint: HnPalette.accent)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // MARK: - Running state

    /// A session that is running is visible and can be ended here: the person
    /// should never have to remember whether their minutes are still open.
    @ViewBuilder private var sessionStrip: some View {
        if usage.pauseSessionActive {
            HnCard(tint: HnPalette.calmSoft) {
                HStack(alignment: .center, spacing: 12) {
                    Image(systemName: "pause.circle.fill")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(HnPalette.calm)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("today.session.title")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(HnPalette.ink)
                        Text("today.session.detail")
                            .font(.caption)
                            .foregroundStyle(HnPalette.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 8)
                    Button {
                        usage.endPauseSessionNow()
                    } label: {
                        Text("today.session.end")
                            .font(.footnote.weight(.bold))
                            .foregroundStyle(HnPalette.accentText)
                    }
                    .buttonStyle(.plain)
                }
            }
            .accessibilityIdentifier("today.sessionCard")
        }
    }

    /// The commitment is the app's own promise; it belongs on Today, not only in
    /// More. It has no action here because ending it early is not something the
    /// app offers.
    @ViewBuilder private var lockStrip: some View {
        if protection.commitmentActive, protection.commitment != nil {
            HnCard {
                HStack(alignment: .center, spacing: 12) {
                    Image(systemName: "lock.fill")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(HnPalette.calm)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("today.lock.title \(lockDeadline)")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(HnPalette.ink)
                        Text("today.lock.detail")
                            .font(.caption)
                            .foregroundStyle(HnPalette.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                }
            }
            .accessibilityIdentifier("today.lockCard")
        }
    }

    private var lockDeadline: String {
        protection.commitment?.deadline.formatted(date: .abbreviated, time: .omitted) ?? ""
    }

    // MARK: - Practice

    private var practiceCard: some View {
        Button(action: onPause) {
            HnCard(tint: HnPalette.accentSoft) {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 12) {
                        Image(systemName: "wind")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundStyle(HnPalette.accent)
                            .frame(width: 46, height: 46)
                            .background(HnPalette.accent.opacity(0.12), in: Circle())
                            .accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("today.practice.title")
                                .font(.hnHeadline())
                                .foregroundStyle(HnPalette.ink)
                            Text("today.practice.detail")
                                .font(.footnote)
                                .foregroundStyle(HnPalette.muted)
                                .multilineTextAlignment(.leading)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 0)
                    }
                    HStack {
                        Text("today.practice.action")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(HnPalette.accentText)
                        Image(systemName: "arrow.forward")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(HnPalette.accentText)
                            .accessibilityHidden(true)
                        Spacer(minLength: 0)
                    }
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityHint(Text("today.practice.hint"))
        .accessibilityIdentifier("today.practiceCard")
    }

    // MARK: - Issues

    private var issuesCard: some View {
        HnCard(tint: HnPalette.emberSoft) {
            VStack(alignment: .leading, spacing: 12) {
                Label("today.issues.title", systemImage: "exclamationmark.triangle.fill")
                    .font(.hnHeadline())
                    .foregroundStyle(HnPalette.ember)
                ForEach(assessment.issues, id: \.self) { issue in
                    Text(LocalizedStringKey(issue.rawValue))
                        .font(.footnote)
                        .foregroundStyle(HnPalette.ink)
                        .fixedSize(horizontal: false, vertical: true)
                }
                HnButton(title: "today.issues.action", kind: .outline, fullWidth: false,
                         action: onProtection)
            }
        }
        .accessibilityIdentifier("today.issuesCard")
    }

    @ViewBuilder private var errorCard: some View {
        if let error = protection.errorKey {
            Label(LocalizedStringKey(error), systemImage: "exclamationmark.triangle")
                .font(.footnote)
                .foregroundStyle(HnPalette.ember)
        }
    }
}
