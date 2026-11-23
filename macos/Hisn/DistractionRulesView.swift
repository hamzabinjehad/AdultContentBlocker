import AppKit
import SwiftUI
import UniformTypeIdentifiers

// MARK: - Words for a rule

/// One-line descriptions of a rule, shared by the list, the menu bar and the
/// panel. Kept out of the views so the words are tested.
enum RuleSummary {
    static func kindTitle(_ action: RuleAction, hasWindow: Bool) -> String {
        switch action {
        case .block: return hasWindow ? String(localized: "Schedule") : String(localized: "Always closed")
        case .dailyLimit: return String(localized: "Daily limit")
        case .wait: return String(localized: "Wait before opening")
        }
    }

    static func symbol(_ action: RuleAction, hasWindow: Bool) -> String {
        switch action {
        case .block: return hasWindow ? "calendar" : "nosign"
        case .dailyLimit: return "hourglass"
        case .wait: return "pause.fill"
        }
    }

    static func duration(minutes: Int) -> String {
        let h = minutes / 60, m = minutes % 60
        if h > 0 && m > 0 { return String(localized: "\(h) h \(m) m") }
        if h > 0 { return String(localized: "\(h) h") }
        return String(localized: "\(m) m")
    }

    static func clock(_ minute: Int) -> String {
        var components = DateComponents()
        components.hour = minute / 60
        components.minute = minute % 60
        let date = Calendar.current.date(from: components) ?? Date()
        let formatter = DateFormatter()
        formatter.setLocalizedDateFormatFromTemplate("jj:mm")
        return formatter.string(from: date)
    }

    static func days(_ days: Weekdays) -> String {
        if days == .everyDay { return String(localized: "Every day") }
        if days == .workdays { return String(localized: "Mon–Fri") }
        if days == [.saturday, .sunday] { return String(localized: "Weekends") }
        let symbols = Calendar.current.shortWeekdaySymbols  // Sunday first
        let order: [(Weekdays, Int)] = [(.monday, 1), (.tuesday, 2), (.wednesday, 3), (.thursday, 4),
                                        (.friday, 5), (.saturday, 6), (.sunday, 0)]
        return order.filter { days.contains($0.0) }.map { symbols[$0.1] }.joined(separator: " ")
    }

    /// "22 m left today", "10 s pause · 2 of 5 opens left", "09:00 – 17:00 · Mon–Fri".
    static func detail(_ rule: DistractionRule, day: RuleDay) -> String {
        var parts: [String] = []
        switch rule.action {
        case .block:
            break
        case .dailyLimit(let minutes):
            let used = Int((day.usedSeconds[rule.id] ?? 0) / 60)
            let left = max(0, minutes - used)
            parts.append(left == 0 ? String(localized: "Used for today")
                                   : String(localized: "\(duration(minutes: left)) left of \(duration(minutes: minutes))"))
        case let .wait(seconds, _, opens, _):
            parts.append(String(localized: "\(seconds) s pause"))
            if let opens {
                let left = max(0, opens - (day.opens[rule.id] ?? 0))
                parts.append(String(localized: "\(left) of \(opens) opens left"))
            }
        }
        if let window = rule.window {
            parts.append("\(clock(window.startMinute)) – \(clock(window.endMinute))")
            if window.days != .everyDay { parts.append(days(window.days)) }
        } else if case .block = rule.action {
            parts.append(String(localized: "All day"))
        }
        return parts.joined(separator: " · ")
    }

    static func targetName(_ target: RuleTarget) -> String {
        switch target {
        case .app(let id): return AppCatalog.name(for: id)
        case .site(let host): return host
        case .surface(let surface): return surfaceName(surface)
        }
    }

    static func surfaceName(_ surface: FeedSurface) -> String {
        switch surface {
        case .youtubeHome: return String(localized: "YouTube home feed")
        case .youtubeShorts: return String(localized: "YouTube Shorts")
        case .instagramFeed: return String(localized: "Instagram feed")
        case .instagramReels: return String(localized: "Instagram Reels")
        case .instagramExplore: return String(localized: "Instagram Explore")
        case .xForYou: return String(localized: "X For You")
        case .redditHome: return String(localized: "Reddit front page")
        case .redditPopular: return String(localized: "Reddit Popular")
        case .facebookFeed: return String(localized: "Facebook feed")
        case .facebookReels: return String(localized: "Facebook Reels")
        case .facebookWatch: return String(localized: "Facebook Watch")
        case .tiktok: return String(localized: "TikTok")
        case .linkedinFeed: return String(localized: "LinkedIn feed")
        case .snapchatSpotlight: return String(localized: "Snapchat Spotlight")
        }
    }

    static func targets(_ rule: DistractionRule, limit: Int = 3) -> String {
        let names = rule.targets.map(targetName).sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
        guard names.count > limit else { return names.joined(separator: ", ") }
        let more = names.count - limit
        return String(localized: "\(names.prefix(limit).joined(separator: ", ")) and \(more) more")
    }

    static func strictness(_ value: Strictness) -> String {
        switch value {
        case .gentle: return String(localized: "Gentle")
        case .firm: return String(localized: "Firm")
        case .locked: return String(localized: "Locked")
        }
    }

    static func strictnessDetail(_ value: Strictness) -> String {
        switch value {
        case .gentle: return String(localized: "“5 more minutes” any time. Changes apply at once.")
        case .firm: return String(localized: "“5 more minutes” after a wait that grows each time. Making the rule weaker takes 10 minutes.")
        case .locked: return String(localized: "No extra minutes and no weaker changes while it runs. One emergency pass a week.")
        }
    }

    static func outcome(_ outcome: RuleBook.Outcome) -> (text: String, isError: Bool) {
        switch outcome {
        case .applied:
            return (String(localized: "Saved."), false)
        case .scheduled(let date):
            return (String(localized: "This makes the rule weaker, so it applies at \(DistractionPanelModel.time(date, now: Date())). You can cancel it until then."), false)
        case .refused(let until):
            if let until {
                return (String(localized: "This rule can’t be made weaker until \(DistractionPanelModel.time(until, now: Date()))."), true)
            }
            return (String(localized: "This rule can’t be made weaker right now."), true)
        case .invalid(let problems):
            if problems.contains("targets") { return (String(localized: "Add at least one app or site."), true) }
            if problems.contains("name") { return (String(localized: "Give the rule a name."), true) }
            if problems.contains("window") { return (String(localized: "Start and end can’t be the same time, and at least one day is needed."), true) }
            return (String(localized: "Something in this rule is out of range."), true)
        }
    }
}

/// Names and icons of installed apps, by bundle identifier.
enum AppCatalog {
    static func url(for bundleID: String) -> URL? {
        NSWorkspace.shared.urlForApplication(withBundleIdentifier: bundleID)
    }

    static func name(for bundleID: String) -> String {
        if let url = url(for: bundleID) {
            return FileManager.default.displayName(atPath: url.path).replacingOccurrences(of: ".app", with: "")
        }
        return bundleID.split(separator: ".").last.map(String.init) ?? bundleID
    }

    static func icon(for bundleID: String) -> NSImage? {
        url(for: bundleID).map { NSWorkspace.shared.icon(forFile: $0.path) }
    }

    /// Apps that are open now and can be chosen: regular apps, not Unrot or the system's own.
    static func running() -> [(id: String, name: String)] {
        NSWorkspace.shared.runningApplications
            .filter { $0.activationPolicy == .regular }
            .compactMap { app in app.bundleIdentifier.map { (id: $0, name: app.localizedName ?? $0) } }
            .filter { !DistractionEngine.defaultAlwaysAllowed.contains($0.id) && $0.id != Bundle.main.bundleIdentifier }
            .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    /// Lets the person pick apps from /Applications; returns their bundle identifiers.
    @MainActor
    static func choose() -> [String] {
        let panel = NSOpenPanel()
        panel.allowedContentTypes = [.application]
        panel.allowsMultipleSelection = true
        panel.canChooseDirectories = false
        panel.directoryURL = URL(fileURLWithPath: "/Applications")
        panel.prompt = String(localized: "Add")
        guard panel.runModal() == .OK else { return [] }
        return panel.urls.compactMap { Bundle(url: $0)?.bundleIdentifier }
            .filter { !DistractionEngine.defaultAlwaysAllowed.contains($0) }
    }
}

// MARK: - Templates

/// What "New rule" offers. Each is a complete, sensible rule the person then adjusts.
enum RuleTemplate: String, CaseIterable, Identifiable {
    case dailyLimit, wait, schedule, feedsOnly, quietHours, alwaysClosed

    var id: String { rawValue }

    static let commonSites: Set<RuleTarget> = [.site("youtube.com"), .site("x.com"), .site("reddit.com"),
                                               .site("instagram.com"), .site("tiktok.com"), .site("facebook.com")]

    var title: String {
        switch self {
        case .dailyLimit: return String(localized: "Daily limit")
        case .wait: return String(localized: "Wait before opening")
        case .schedule: return String(localized: "Work hours")
        case .feedsOnly: return String(localized: "Feeds only")
        case .quietHours: return String(localized: "Quiet hours")
        case .alwaysClosed: return String(localized: "Always closed")
        }
    }

    var blurb: String {
        switch self {
        case .dailyLimit: return String(localized: "One hour a day for feeds, shared. Then they close until tomorrow.")
        case .wait: return String(localized: "A short breath and a question before an app or site opens.")
        case .schedule: return String(localized: "Close chosen apps and sites from 9 to 5 on workdays.")
        case .feedsOnly: return String(localized: "Hide home feeds and short videos. Search and links keep working.")
        case .quietHours: return String(localized: "Everything distracting closes at night, 22:00 to 07:00.")
        case .alwaysClosed: return String(localized: "Something you’ve decided you’re done with.")
        }
    }

    var symbol: String {
        switch self {
        case .dailyLimit: return "hourglass"
        case .wait: return "pause.fill"
        case .schedule: return "calendar"
        case .feedsOnly: return "rectangle.stack"
        case .quietHours: return "moon.fill"
        case .alwaysClosed: return "nosign"
        }
    }

    var tint: Color {
        switch self {
        case .dailyLimit: return Color(red: 0.86, green: 0.62, blue: 0.13)
        case .wait: return Color(red: 0.90, green: 0.47, blue: 0.14)
        case .schedule: return Color(red: 0.30, green: 0.36, blue: 0.78)
        case .feedsOnly: return Color(red: 0.52, green: 0.38, blue: 0.94)
        case .quietHours: return Color(red: 0.36, green: 0.38, blue: 0.45)
        case .alwaysClosed: return Color(red: 0.80, green: 0.30, blue: 0.28)
        }
    }

    func make() -> DistractionRule {
        switch self {
        case .dailyLimit:
            return DistractionRule(name: String(localized: "Feeds"), targets: Self.commonSites,
                                   action: .dailyLimit(minutesPerDay: 60))
        case .wait:
            return DistractionRule(name: String(localized: "Pause first"), targets: [],
                                   action: .wait(seconds: 10, grows: true, opensPerDay: 5, openMinutes: 10))
        case .schedule:
            return DistractionRule(name: String(localized: "Work hours"), targets: Self.commonSites, action: .block,
                                   window: TimeWindow(startMinute: 9 * 60, endMinute: 17 * 60, days: .workdays))
        case .feedsOnly:
            return DistractionRule(name: String(localized: "Feeds only"),
                                   targets: Set(FeedSurface.allCases.filter { $0 != .tiktok }.map(RuleTarget.surface)),
                                   action: .block)
        case .quietHours:
            return DistractionRule(name: String(localized: "Quiet hours"), targets: Self.commonSites, action: .block,
                                   window: TimeWindow(startMinute: 22 * 60, endMinute: 7 * 60), strictness: .firm)
        case .alwaysClosed:
            return DistractionRule(name: String(localized: "Closed"), targets: [], action: .block)
        }
    }
}

// MARK: - The page

/// Limits, waits, schedules and Focus: the list of rules, with the selected
/// rule edited in a column beside it (an inspector), the way Mail and Reminders do.
struct DistractionRulesPage: View {
    @ObservedObject private var store: DistractionStore
    @State private var selection: UUID?
    @State private var draft: DistractionRule?
    @State private var isNew = false

    /// `editing` opens the page with that rule already in the inspector.
    init(store: DistractionStore = .shared, editing: DistractionRule? = nil) {
        self.store = store
        _selection = State(initialValue: editing?.id)
        _draft = State(initialValue: editing)
    }
    @State private var showingTemplates = false
    @State private var showingFocus = false
    @State private var message: (text: String, isError: Bool)?
    private let refresh = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            HnPageHeader(eyebrow: "Limits and Focus", title: headline,
                         caption: "Rules for the apps and sites that pull you away. Adult protection is separate and always on.")
            today
            HnTwoColumn(breakpoint: 820, spacing: 24, railWidth: 340) {
                VStack(alignment: .leading, spacing: 18) {
                    focusCard
                    rulesList
                    pendingList
                }
            } rail: {
                inspector
            }
        }
        .sheet(isPresented: $showingTemplates) {
            RuleTemplateSheet { template in
                showingTemplates = false
                guard let template else { return }
                let rule = template.make()
                draft = rule
                isNew = true
                selection = rule.id
                message = nil
            }
        }
        .sheet(isPresented: $showingFocus) { FocusStartSheet(store: store) { showingFocus = false } }
        .onReceive(refresh) { _ in store.tick() }
        .onAppear { store.tick() }
    }

    private var headline: LocalizedStringKey {
        let running = store.book.rules.filter { $0.isRunning(at: store.clock(), calendar: store.calendar) }.count
        if store.book.rules.isEmpty { return "No rules yet" }
        return running == 0 ? "Nothing is running right now" : "\(running) rules running"
    }

    // MARK: Today strip

    private var today: some View {
        let book = store.book
        let feed = Int(book.feedSecondsToday() / 60)
        let given = Int(book.givenBackSeconds(at: store.clock(), calendar: store.calendar) / 60)
        return HnCard(padding: 16) {
            HStack(alignment: .top, spacing: 28) {
                HnStat(value: RuleSummary.duration(minutes: feed), label: "On your rules’ apps and sites today")
                HnStat(value: "\(book.pausesToday)", label: "Pauses")
                HnStat(value: "\(book.closedToday)", label: "Times closed")
                HnStat(value: RuleSummary.duration(minutes: given), label: "Given back",
                       tint: HnPalette.calm)
                Spacer(minLength: 0)
            }
        }
    }

    // MARK: Focus

    @ViewBuilder
    private var focusCard: some View {
        let now = store.clock()
        if let focus = store.book.focus, focus.isRunning(at: now) {
            HnCard(padding: 16) {
                HStack(spacing: 14) {
                    ZStack {
                        Circle().stroke(HnPalette.line, lineWidth: 4)
                        Circle().trim(from: 0, to: focus.progress(at: now))
                            .stroke(HnPalette.accent, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                            .rotationEffect(.degrees(-90))
                        Image(systemName: "leaf.fill").font(.system(size: 13)).foregroundStyle(HnPalette.calm)
                    }
                    .frame(width: 40, height: 40)
                    .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Focus until \(DistractionPanelModel.time(focus.until, now: now))")
                            .font(.hnCardTitle()).foregroundStyle(HnPalette.ink)
                        Text(verbatim: focus.intent.isEmpty ? RuleSummary.strictness(focus.strictness)
                                                            : focus.intent)
                            .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                    }
                    Spacer()
                    switch store.book.howToEndFocus(at: now) {
                    case .now, .afterWait:
                        HnButton(title: "End Focus", kind: .outline) { store.endFocus() }
                    case .notUntil:
                        HnBadge(text: "Locked", tint: HnPalette.ember)
                    }
                }
            }
        } else {
            HnCard(padding: 16) {
                HStack(spacing: 14) {
                    Image(systemName: "target").font(.system(size: 22)).foregroundStyle(HnPalette.accent)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Focus").font(.hnCardTitle()).foregroundStyle(HnPalette.ink)
                        Text("A timed block you start yourself. Distractions close until it ends.")
                            .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                    }
                    Spacer()
                    HnButton(title: "Start Focus…", systemImage: "play.fill") { showingFocus = true }
                }
            }
        }
    }

    // MARK: List

    private var rulesList: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                HnSectionLabel(title: "Your rules")
                Spacer()
                HnButton(title: "New rule…", systemImage: "plus", kind: .quiet, compact: true) {
                    showingTemplates = true
                }
            }
            if store.book.rules.isEmpty && draft == nil {
                HnCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Start with one rule").font(.hnTitle()).foregroundStyle(HnPalette.ink)
                        Text("Most people start with a daily limit on feeds, or a short pause before the app they open without thinking.")
                            .font(.hnBody()).foregroundStyle(HnPalette.muted)
                            .fixedSize(horizontal: false, vertical: true)
                        HnButton(title: "Choose a rule…", systemImage: "plus") { showingTemplates = true }
                    }
                }
            } else {
                VStack(spacing: 0) {
                    let rules = store.book.rules
                    ForEach(Array(rules.enumerated()), id: \.element.id) { index, rule in
                        RuleRow(rule: rule, day: store.book.day,
                                running: rule.isRunning(at: store.clock(), calendar: store.calendar),
                                selected: selection == rule.id) {
                            selection = rule.id
                            draft = rule
                            isNew = false
                            message = nil
                        }
                        if index < rules.count - 1 { HnHairline().padding(.leading, 50) }
                    }
                }
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(HnPalette.card))
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(HnPalette.line, lineWidth: 0.5))
            }
        }
    }

    @ViewBuilder
    private var pendingList: some View {
        if !store.book.pending.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                HnSectionLabel(title: "Waiting to apply")
                ForEach(store.book.pending) { change in
                    let name = store.book.rules.first { $0.id == change.ruleID }?.name ?? ""
                    HnNotice(title: nil,
                             message: change.replacement == nil
                                ? "“\(name)” will be removed at \(DistractionPanelModel.time(change.applyAt, now: store.clock()))."
                                : "A weaker “\(name)” applies at \(DistractionPanelModel.time(change.applyAt, now: store.clock())).",
                             systemImage: "clock", level: .info,
                             action: (title: "Keep it as it is", action: { store.cancelPending(change.id) }))
                }
            }
        }
    }

    // MARK: Inspector

    @ViewBuilder
    private var inspector: some View {
        if let binding = Binding($draft) {
            RuleEditor(rule: binding, isNew: isNew, message: message,
                       save: save, delete: delete,
                       cancel: { draft = nil; selection = nil; message = nil })
        } else {
            HnCard {
                VStack(alignment: .leading, spacing: 8) {
                    Image(systemName: "sidebar.right").font(.system(size: 20)).foregroundStyle(HnPalette.faint)
                    Text("Select a rule to change it").font(.hnCardTitle()).foregroundStyle(HnPalette.ink)
                    Text("Making a rule stronger applies at once. Making it weaker follows the rule’s strictness.")
                        .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private func save() {
        guard var rule = draft else { return }
        rule.name = rule.name.trimmingCharacters(in: .whitespaces)
        let outcome = store.propose(rule, replacing: isNew ? nil : rule.id)
        message = RuleSummary.outcome(outcome)
        if case .applied = outcome {
            isNew = false
            draft = store.book.rules.first { $0.id == rule.id }
        }
    }

    private func delete() {
        guard let rule = draft else { return }
        if isNew { draft = nil; selection = nil; return }
        let outcome = store.propose(nil, replacing: rule.id)
        if case .applied = outcome {
            draft = nil; selection = nil; message = nil
        } else {
            message = RuleSummary.outcome(outcome)
        }
    }
}

/// A coloured rounded tile with a symbol, like the icons in System Settings.
struct RuleTile: View {
    let symbol: String
    let tint: Color
    var size: CGFloat = 26

    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.28, style: .continuous)
            .fill(LinearGradient(colors: [tint.opacity(0.85), tint], startPoint: .top, endPoint: .bottom))
            .frame(width: size, height: size)
            .overlay(Image(systemName: symbol).font(.system(size: size * 0.5, weight: .semibold)).foregroundColor(.white))
            .accessibilityHidden(true)
    }

    static func feedsOnly(_ rule: DistractionRule) -> Bool {
        !rule.targets.isEmpty && rule.targets.allSatisfy { if case .surface = $0 { return true } else { return false } }
    }

    static func symbol(for rule: DistractionRule) -> String {
        if case .block = rule.action, feedsOnly(rule) { return RuleTemplate.feedsOnly.symbol }
        return RuleSummary.symbol(rule.action, hasWindow: rule.window != nil)
    }

    static func tint(for rule: DistractionRule) -> Color {
        switch rule.action {
        case .block:
            if rule.targets.allSatisfy({ if case .surface = $0 { return true } else { return false } }), !rule.targets.isEmpty {
                return RuleTemplate.feedsOnly.tint
            }
            return rule.window == nil ? RuleTemplate.alwaysClosed.tint : RuleTemplate.schedule.tint
        case .dailyLimit: return RuleTemplate.dailyLimit.tint
        case .wait: return RuleTemplate.wait.tint
        }
    }
}

private struct RuleRow: View {
    let rule: DistractionRule
    let day: RuleDay
    let running: Bool
    let selected: Bool
    let select: () -> Void
    @State private var hovered = false

    var body: some View {
        Button(action: select) {
            HStack(spacing: 12) {
                RuleTile(symbol: RuleTile.symbol(for: rule), tint: RuleTile.tint(for: rule))
                    .opacity(rule.enabled ? 1 : 0.4)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(verbatim: rule.name).font(.system(size: 13, weight: .semibold)).foregroundStyle(HnPalette.ink)
                        if rule.strictness != .gentle {
                            Image(systemName: rule.strictness == .locked ? "lock.fill" : "hand.raised.fill")
                                .font(.system(size: 9)).foregroundStyle(HnPalette.faint)
                                .help(RuleSummary.strictness(rule.strictness))
                        }
                    }
                    Text(verbatim: RuleSummary.targets(rule))
                        .font(.hnCaption()).foregroundStyle(HnPalette.muted).lineLimit(1)
                    Text(verbatim: RuleSummary.detail(rule, day: day))
                        .font(.hnCaption()).foregroundStyle(HnPalette.faint).lineLimit(1)
                }
                Spacer(minLength: 8)
                if case .dailyLimit(let minutes) = rule.action {
                    ProgressView(value: min(1, (day.usedSeconds[rule.id] ?? 0) / Double(minutes * 60)))
                        .progressViewStyle(.linear).frame(width: 70)
                        .tint(RuleTile.tint(for: rule))
                }
                if !rule.enabled {
                    HnBadge(text: "Off", tint: HnPalette.muted)
                } else if running {
                    HnBadge(text: "On", tint: HnPalette.calm)
                } else {
                    HnBadge(text: "Later", tint: HnPalette.muted)
                }
                Image(systemName: "chevron.forward").font(.system(size: 10, weight: .semibold)).foregroundStyle(HnPalette.faint)
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(selected ? HnPalette.accentSoft : (hovered ? HnPalette.soft : Color.clear))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { hovered = $0 }
        .accessibilityLabel(Text(verbatim: "\(rule.name), \(RuleSummary.detail(rule, day: day))"))
    }
}

// MARK: - Editor

private struct RuleEditor: View {
    @Binding var rule: DistractionRule
    let isNew: Bool
    let message: (text: String, isError: Bool)?
    let save: () -> Void
    let delete: () -> Void
    let cancel: () -> Void

    @State private var newSite = ""
    @State private var siteError = false

    private enum Kind: Int, CaseIterable { case block, limit, wait }

    private var kind: Binding<Kind> {
        Binding(get: {
            switch rule.action {
            case .block: return .block
            case .dailyLimit: return .limit
            case .wait: return .wait
            }
        }, set: { new in
            switch new {
            case .block: rule.action = .block
            case .limit: rule.action = .dailyLimit(minutesPerDay: 60)
            case .wait: rule.action = .wait(seconds: 10, grows: true, opensPerDay: 5, openMinutes: 10)
            }
        })
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 10) {
                RuleTile(symbol: RuleTile.symbol(for: rule), tint: RuleTile.tint(for: rule), size: 34)
                VStack(alignment: .leading, spacing: 1) {
                    TextField("Name", text: $rule.name)
                        .textFieldStyle(.plain).font(.system(size: 15, weight: .semibold))
                    Text(verbatim: RuleSummary.kindTitle(rule.action, hasWindow: rule.window != nil))
                        .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                }
                Toggle("", isOn: $rule.enabled).toggleStyle(.switch).labelsHidden().controlSize(.small)
                    .help("Turn this rule on or off")
            }

            section("What it does") {
                Picker("", selection: kind) {
                    Text("Close").tag(Kind.block)
                    Text("Limit").tag(Kind.limit)
                    Text("Pause").tag(Kind.wait)
                }
                .pickerStyle(.segmented).labelsHidden()
                actionControls
            }

            section("Apps and sites") { targets }
            section("When") { schedule }
            section("When I want more") { strictness }

            if let message {
                Text(verbatim: message.text).font(.hnCaption())
                    .foregroundStyle(message.isError ? HnPalette.danger : HnPalette.calm)
                    .fixedSize(horizontal: false, vertical: true)
            }
            HStack {
                Button(isNew ? "Discard" : "Delete…", role: .destructive, action: delete)
                    .buttonStyle(.bordered).controlSize(.regular)
                Spacer()
                Button("Close", action: cancel).buttonStyle(.bordered)
                Button(isNew ? "Add rule" : "Save", action: save)
                    .buttonStyle(.borderedProminent).tint(HnPalette.accent)
                    .keyboardShortcut(.defaultAction)
            }
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(HnPalette.card))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(HnPalette.line, lineWidth: 0.5))
    }

    private func section<Content: View>(_ title: LocalizedStringKey, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HnSectionLabel(title: title)
            content()
        }
    }

    private func row<Content: View>(_ title: LocalizedStringKey, @ViewBuilder control: () -> Content) -> some View {
        HStack {
            Text(title).font(.hnBody()).foregroundStyle(HnPalette.ink)
            Spacer(minLength: 8)
            control()
        }
    }

    // MARK: Action

    @ViewBuilder
    private var actionControls: some View {
        switch rule.action {
        case .block:
            Text("Closed while the rule runs.").font(.hnCaption()).foregroundStyle(HnPalette.muted)
        case .dailyLimit(let minutes):
            row("Together, per day") {
                Stepper(value: Binding(get: { minutes }, set: { rule.action = .dailyLimit(minutesPerDay: $0) }),
                        in: 5...600, step: minutes < 60 ? 5 : 15) {
                    Text(verbatim: RuleSummary.duration(minutes: minutes)).monospacedDigit()
                }
            }
            Text("All the apps and sites in this rule share the time.").font(.hnCaption()).foregroundStyle(HnPalette.muted)
        case let .wait(seconds, grows, opens, openMinutes):
            row("Breathe for") {
                Picker("", selection: Binding(get: { seconds }, set: {
                    rule.action = .wait(seconds: $0, grows: grows, opensPerDay: opens, openMinutes: openMinutes)
                })) {
                    ForEach(RuleAction.waitChoices, id: \.self) { Text("\($0) seconds").tag($0) }
                }.labelsHidden().frame(width: 130)
            }
            Toggle("Longer each time I open it today", isOn: Binding(get: { grows }, set: {
                rule.action = .wait(seconds: seconds, grows: $0, opensPerDay: opens, openMinutes: openMinutes)
            })).font(.hnBody())
            row("Opens a day") {
                Picker("", selection: Binding(get: { opens ?? 0 }, set: {
                    rule.action = .wait(seconds: seconds, grows: grows, opensPerDay: $0 == 0 ? nil : $0, openMinutes: openMinutes)
                })) {
                    Text("No limit").tag(0)
                    ForEach([1, 2, 3, 5, 8, 10], id: \.self) { Text(verbatim: "\($0)").tag($0) }
                }.labelsHidden().frame(width: 130)
            }
            row("Each open lasts") {
                Picker("", selection: Binding(get: { openMinutes }, set: {
                    rule.action = .wait(seconds: seconds, grows: grows, opensPerDay: opens, openMinutes: $0)
                })) {
                    ForEach(RuleAction.openMinuteChoices, id: \.self) { Text("\($0) minutes").tag($0) }
                }.labelsHidden().frame(width: 130)
            }
        }
    }

    // MARK: Targets

    private var sortedTargets: [RuleTarget] {
        rule.targets.sorted { RuleSummary.targetName($0).localizedCaseInsensitiveCompare(RuleSummary.targetName($1)) == .orderedAscending }
    }

    private var targets: some View {
        VStack(alignment: .leading, spacing: 6) {
            if rule.targets.isEmpty {
                Text("Nothing chosen yet.").font(.hnCaption()).foregroundStyle(HnPalette.muted)
            }
            ForEach(sortedTargets, id: \.self) { target in
                HStack(spacing: 8) {
                    targetIcon(target)
                    Text(verbatim: RuleSummary.targetName(target)).font(.hnBody()).foregroundStyle(HnPalette.ink).lineLimit(1)
                    Spacer(minLength: 4)
                    Button { rule.targets.remove(target) } label: {
                        Image(systemName: "minus.circle.fill").foregroundStyle(HnPalette.faint)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text("Remove \(RuleSummary.targetName(target))"))
                }
            }
            HStack(spacing: 6) {
                TextField("Add a site, like reddit.com", text: $newSite)
                    .textFieldStyle(.roundedBorder).onSubmit(addSite)
                Button("Add", action: addSite).disabled(newSite.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            if siteError {
                Text("That doesn’t look like a site address.").font(.hnCaption()).foregroundStyle(HnPalette.danger)
            }
            HStack(spacing: 6) {
                Menu {
                    let running = AppCatalog.running().filter { !rule.targets.contains(.app($0.id)) }
                    if !running.isEmpty {
                        Section("Open now") {
                            ForEach(running, id: \.id) { app in
                                Button(app.name) { rule.targets.insert(.app(app.id)) }
                            }
                        }
                    }
                    Button("Choose from Applications…") {
                        for id in AppCatalog.choose() { rule.targets.insert(.app(id)) }
                    }
                } label: { Label("Add app", systemImage: "app.badge") }
                .fixedSize()
                Menu {
                    ForEach(FeedSurface.allCases, id: \.self) { surface in
                        Button {
                            let target = RuleTarget.surface(surface)
                            if rule.targets.contains(target) { rule.targets.remove(target) } else { rule.targets.insert(target) }
                        } label: {
                            if rule.targets.contains(.surface(surface)) {
                                Label(RuleSummary.surfaceName(surface), systemImage: "checkmark")
                            } else {
                                Text(verbatim: RuleSummary.surfaceName(surface))
                            }
                        }
                    }
                } label: { Label("Feeds", systemImage: "rectangle.stack") }
                .fixedSize()
            }
            if rule.targets.contains(where: { if case .app = $0 { return false } else { return true } }) {
                Text("Sites and feeds need Unrot’s browser extension to be on.")
                    .font(.hnCaption()).foregroundStyle(HnPalette.faint)
            }
        }
    }

    @ViewBuilder
    private func targetIcon(_ target: RuleTarget) -> some View {
        switch target {
        case .app(let id):
            if let icon = AppCatalog.icon(for: id) {
                Image(nsImage: icon).resizable().frame(width: 18, height: 18)
            } else {
                Image(systemName: "app").frame(width: 18, height: 18).foregroundStyle(HnPalette.muted)
            }
        case .site:
            Image(systemName: "globe").frame(width: 18, height: 18).foregroundStyle(HnPalette.muted)
        case .surface:
            Image(systemName: "rectangle.stack").frame(width: 18, height: 18).foregroundStyle(HnPalette.muted)
        }
    }

    private func addSite() {
        guard let host = RuleTarget.normalizedSite(newSite) else { siteError = true; return }
        rule.targets.insert(.site(host))
        newSite = ""
        siteError = false
    }

    // MARK: Schedule

    private func time(_ keyPath: WritableKeyPath<TimeWindow, Int>) -> Binding<Date> {
        Binding(get: {
            let minute = rule.window?[keyPath: keyPath] ?? 0
            return Calendar.current.date(bySettingHour: minute / 60, minute: minute % 60, second: 0, of: Date()) ?? Date()
        }, set: { date in
            let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
            var window = rule.window ?? TimeWindow(startMinute: 9 * 60, endMinute: 17 * 60)
            window[keyPath: keyPath] = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
            rule.window = window
        })
    }

    @ViewBuilder
    private var schedule: some View {
        Picker("", selection: Binding(get: { rule.window != nil }, set: { timed in
            rule.window = timed ? (rule.window ?? TimeWindow(startMinute: 9 * 60, endMinute: 17 * 60, days: .workdays)) : nil
        })) {
            Text("All day").tag(false)
            Text("At set hours").tag(true)
        }
        .pickerStyle(.segmented).labelsHidden()
        if let window = rule.window {
            HStack(spacing: 8) {
                DatePicker("", selection: time(\.startMinute), displayedComponents: .hourAndMinute).labelsHidden()
                Text("to").foregroundStyle(HnPalette.muted)
                DatePicker("", selection: time(\.endMinute), displayedComponents: .hourAndMinute).labelsHidden()
                if window.endMinute < window.startMinute {
                    Text("next day").font(.hnCaption()).foregroundStyle(HnPalette.faint)
                }
            }
            WeekdayPicker(days: Binding(get: { window.days }, set: { rule.window?.days = $0 }))
        }
    }

    // MARK: Strictness

    private var strictness: some View {
        VStack(alignment: .leading, spacing: 6) {
            Picker("", selection: $rule.strictness) {
                ForEach(Strictness.allCases, id: \.self) { Text(verbatim: RuleSummary.strictness($0)).tag($0) }
            }
            .pickerStyle(.segmented).labelsHidden()
            Text(verbatim: RuleSummary.strictnessDetail(rule.strictness))
                .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// Seven round day buttons, starting on the calendar's first weekday.
struct WeekdayPicker: View {
    @Binding var days: Weekdays

    private static let all: [(Weekdays, Int)] = [(.sunday, 0), (.monday, 1), (.tuesday, 2), (.wednesday, 3),
                                                (.thursday, 4), (.friday, 5), (.saturday, 6)]

    var body: some View {
        let first = Calendar.current.firstWeekday - 1
        let ordered = Array(Self.all[first...] + Self.all[..<first])
        HStack(spacing: 4) {
            ForEach(ordered, id: \.1) { day, index in
                let on = days.contains(day)
                Button {
                    if on { days.remove(day) } else { days.insert(day) }
                } label: {
                    Text(verbatim: Calendar.current.veryShortWeekdaySymbols[index])
                        .font(.system(size: 11.5, weight: on ? .semibold : .regular))
                        .frame(maxWidth: .infinity).frame(height: 24)
                        .foregroundStyle(on ? HnPalette.onAccent : HnPalette.muted)
                        .background(RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill(on ? HnPalette.accent : HnPalette.soft))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(verbatim: Calendar.current.weekdaySymbols[index]))
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
    }
}

// MARK: - Sheets

struct RuleTemplateSheet: View {
    let choose: (RuleTemplate?) -> Void
    @State private var hovered: RuleTemplate?

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 2) {
                Text("New rule").font(.hnTitle())
                Text("Start from what you want less of. You can change everything after.")
                    .font(.hnCaption()).foregroundStyle(HnPalette.muted)
            }
            let columns = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10), GridItem(.flexible())]
            LazyVGrid(columns: columns, spacing: 10) {
                ForEach(RuleTemplate.allCases) { template in
                    Button { choose(template) } label: {
                        VStack(alignment: .leading, spacing: 8) {
                            RuleTile(symbol: template.symbol, tint: template.tint, size: 32)
                            Text(verbatim: template.title).font(.system(size: 13, weight: .semibold)).foregroundStyle(HnPalette.ink)
                            Text(verbatim: template.blurb).font(.hnCaption()).foregroundStyle(HnPalette.muted)
                                .multilineTextAlignment(.leading).fixedSize(horizontal: false, vertical: true)
                            Spacer(minLength: 0)
                        }
                        .padding(14).frame(maxWidth: .infinity, minHeight: 128, alignment: .topLeading)
                        .background(RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(hovered == template ? HnPalette.raised : HnPalette.card))
                        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .stroke(hovered == template ? HnPalette.accent : HnPalette.line, lineWidth: hovered == template ? 1.5 : 0.5))
                    }
                    .buttonStyle(.plain)
                    .onHover { hovered = $0 ? template : nil }
                }
            }
            HStack {
                Spacer()
                Button("Cancel") { choose(nil) }.keyboardShortcut(.cancelAction)
            }
        }
        .padding(20)
        .frame(width: 640)
        .background(HnPalette.canvas)
    }
}

struct FocusStartSheet: View {
    @ObservedObject var store: DistractionStore
    let done: () -> Void

    @State private var minutes = 25
    @State private var intent = ""
    @State private var strictness: Strictness = .firm
    @State private var allowOnly = false
    @State private var failed = false

    /// What Focus closes when the person has no rules yet.
    private var targets: Set<RuleTarget> {
        let own = Set(store.book.rules.flatMap(\.targets))
        return own.isEmpty ? RuleTemplate.commonSites : own.union(RuleTemplate.commonSites)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 12) {
                RuleTile(symbol: "target", tint: Color(red: 0.16, green: 0.50, blue: 0.92), size: 36)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Start Focus").font(.hnTitle())
                    Text("Everything in your rules, and the big feeds, close until it ends.")
                        .font(.hnCaption()).foregroundStyle(HnPalette.muted)
                }
            }
            VStack(alignment: .leading, spacing: 8) {
                HnSectionLabel(title: "For how long")
                Picker("", selection: $minutes) {
                    ForEach(FocusSessionState.lengthChoices, id: \.self) { Text(verbatim: RuleSummary.duration(minutes: $0)).tag($0) }
                }.pickerStyle(.segmented).labelsHidden()
            }
            VStack(alignment: .leading, spacing: 8) {
                HnSectionLabel(title: "What is it for?")
                TextField("Write the report", text: $intent).textFieldStyle(.roundedBorder)
                Text("Shown to you if you reach for a closed app.").font(.hnCaption()).foregroundStyle(HnPalette.faint)
            }
            VStack(alignment: .leading, spacing: 8) {
                HnSectionLabel(title: "If I want to stop early")
                Picker("", selection: $strictness) {
                    Text("I can end it").tag(Strictness.gentle)
                    Text("After a pause").tag(Strictness.firm)
                    Text("Not until it ends").tag(Strictness.locked)
                }.pickerStyle(.segmented).labelsHidden()
            }
            if failed {
                Text("A longer Focus is already running.").font(.hnCaption()).foregroundStyle(HnPalette.danger)
            }
            HStack {
                Spacer()
                Button("Cancel", action: done).keyboardShortcut(.cancelAction)
                Button("Start \(RuleSummary.duration(minutes: minutes))") {
                    if store.startFocus(minutes: minutes, intent: intent.trimmingCharacters(in: .whitespaces),
                                        targets: targets, allowOnly: allowOnly, strictness: strictness) {
                        done()
                    } else {
                        failed = true
                    }
                }
                .buttonStyle(.borderedProminent).tint(HnPalette.accent).keyboardShortcut(.defaultAction)
            }
        }
        .padding(20)
        .frame(width: 440)
        .background(HnPalette.canvas)
    }
}
