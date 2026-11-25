import SwiftUI

/// Unrot's one page, organised by time the way a day is: what is closed now,
/// what comes later today, and a few ideas. Rules open in a sheet; there is no
/// second page to manage them on.
struct UnrotHomePage: View {
    @ObservedObject var store: DistractionStore
    @ObservedObject private var navigation = AppNavigation.shared

    @State private var editing: DistractionRule?
    @State private var editingIsNew = false
    @State private var message: (text: String, isError: Bool)?
    @State private var showingTemplates = false
    @State private var showingFocus = false
    @State private var showingExceptions = false
    private let refresh = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    init(store: DistractionStore = .shared) { self.store = store }

    private var now: Date { store.clock() }

    /// The rules split by time. Pure, so the grouping is tested.
    struct Groups: Equatable {
        var running: [DistractionRule] = []
        var later: [DistractionRule] = []
        var off: [DistractionRule] = []

        init(rules: [DistractionRule], now: Date, calendar: Calendar) {
            for rule in rules {
                if !rule.enabled { off.append(rule) }
                else if rule.isRunning(at: now, calendar: calendar) { running.append(rule) }
                else { later.append(rule) }
            }
            later.sort {
                ($0.window?.nextStart(after: now, calendar: calendar) ?? .distantFuture)
                    < ($1.window?.nextStart(after: now, calendar: calendar) ?? .distantFuture)
            }
        }
    }

    /// Templates the person has nothing like yet, offered as one-click ideas.
    static func ideas(for rules: [DistractionRule]) -> [RuleTemplate] {
        let hasLimit = rules.contains { if case .dailyLimit = $0.action { return true } else { return false } }
        let hasWait = rules.contains { if case .wait = $0.action { return true } else { return false } }
        let hasFeeds = rules.contains(where: RuleTile.feedsOnly)
        let hasNight = rules.contains { ($0.window.map { $0.endMinute < $0.startMinute }) ?? false }
        var out: [RuleTemplate] = []
        if !hasLimit { out.append(.dailyLimit) }
        if !hasFeeds { out.append(.feedsOnly) }
        if !hasNight { out.append(.quietHours) }
        if !hasWait { out.append(.wait) }
        return Array(out.prefix(2))
    }

    var body: some View {
        let book = store.book
        let groups = Groups(rules: book.rules, now: now, calendar: store.calendar)
        VStack(alignment: .leading, spacing: 22) {
            header(book)
            shieldRow
            section("Now") {
                if let focus = book.focus, focus.isRunning(at: now) { focusRow(focus) }
                ForEach(groups.running) { rule in row(rule, book: book, state: .running) }
                if groups.running.isEmpty && !(book.focus?.isRunning(at: now) ?? false) {
                    Text(book.rules.isEmpty ? "No rules yet. Start with an idea below."
                                            : "Nothing is closed right now.")
                        .font(.system(size: 13)).foregroundStyle(HnPalette.muted)
                        .padding(.vertical, 4)
                }
            }
            if !groups.later.isEmpty || !groups.off.isEmpty {
                section("Upcoming") {
                    ForEach(groups.later) { rule in row(rule, book: book, state: .later) }
                    ForEach(groups.off) { rule in row(rule, book: book, state: .off) }
                }
            }
            if !book.pending.isEmpty { pending(book) }
            let fromUse = book.suggestions(isDistracting: AppCatalog.isDistracting)
            if !fromUse.isEmpty {
                section("From your own use") {
                    ForEach(fromUse) { suggestion in usageIdea(suggestion) }
                }
            }
            section("Ideas") {
                HStack(spacing: 10) {
                    quick("Focus now", symbol: "play.fill") { showingFocus = true }
                    quick("Breathe", symbol: "wind") { PauseWindowController.shared.show() }
                }
                ForEach(Self.ideas(for: book.rules)) { template in idea(template) }
            }
            Button { showingExceptions = true } label: {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.seal").font(.system(size: 12, weight: .semibold))
                    Text(book.exceptions.isEmpty ? "Sites that are never closed"
                                                 : "Never closed · \(book.exceptions.count) sites")
                        .font(.system(size: 13))
                    Image(systemName: "chevron.forward").font(.system(size: 9, weight: .semibold))
                }
                .foregroundStyle(HnPalette.muted)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .sheet(isPresented: $showingTemplates) {
            RuleTemplateSheet { template in
                showingTemplates = false
                if let template { begin(template.make(), isNew: true) }
            }
        }
        .sheet(isPresented: $showingFocus) { FocusStartSheet(store: store) { showingFocus = false } }
        .sheet(isPresented: $showingExceptions) { ExceptionsSheet(store: store) { showingExceptions = false } }
        .sheet(item: $editing) { _ in editorSheet }
        .onReceive(refresh) { _ in store.tick() }
        .onAppear {
            store.tick()
            if navigation.newRuleRequested { navigation.newRuleRequested = false; showingTemplates = true }
        }
        .onChange(of: navigation.newRuleRequested) { requested in
            guard requested else { return }
            navigation.newRuleRequested = false
            showingTemplates = true
        }
    }

    // MARK: Shield first

    /// Adult protection is what the app is for, so its state leads the day;
    /// rules, limits and Focus sit under it.
    private var shieldRow: some View {
        let status = ProtectionStatus(ProtectionEvidence.live(filter: FilterController.shared.availability,
                                                              authority: FilterSync.shared.status))
        let working = status.layers.filter(\.ok).count
        let whole = working == status.layers.count
        return Button { navigation.page = .overview } label: {
            HoverSurface {
                HStack(spacing: 12) {
                    Text(verbatim: "🛡️").font(.system(size: 30)).frame(width: 40, height: 40).accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Adult content").font(.system(size: 16, weight: .medium)).foregroundStyle(HnPalette.ink)
                        Text("\(working) of \(status.layers.count) layers working")
                            .font(.system(size: 12.5)).foregroundStyle(HnPalette.muted).monospacedDigit()
                    }
                    Spacer(minLength: 8)
                    HStack(spacing: 6) {
                        Circle().fill(whole ? HnPalette.calm : HnPalette.ember).frame(width: 7, height: 7)
                        Text(verbatim: status.plainHeadline).font(.system(size: 12, weight: .medium))
                            .foregroundStyle(whole ? HnPalette.calm : HnPalette.ember)
                    }
                    .padding(.horizontal, 11).frame(height: 30)
                    .background(Capsule().fill(whole ? HnPalette.calmSoft : HnPalette.emberSoft))
                    Image(systemName: "chevron.forward").font(.system(size: 11, weight: .semibold)).foregroundStyle(HnPalette.faint)
                }
                .padding(.horizontal, 16).padding(.vertical, 13)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("today.shield")
    }

    // MARK: Header

    private func header(_ book: RuleBook) -> some View {
        let feed = RuleSummary.duration(minutes: Int(book.feedSecondsToday() / 60))
        return HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Today").font(.system(size: 24, weight: .bold)).foregroundStyle(HnPalette.ink)
                Text("\(Self.dateLine(now)) · \(feed) in feeds · \(book.pausesToday) pauses · \(book.closedToday) closed")
                    .font(.system(size: 12.5)).foregroundStyle(HnPalette.muted).monospacedDigit()
            }
            Spacer(minLength: 12)
            round("magnifyingglass", help: "Check a website") { navigation.siteCheckRequested = true }
            round("wind", help: "A slow breath, any time") { PauseWindowController.shared.show() }
        }
    }

    /// A small round button beside the title, where Opal keeps its two.
    private func round(_ symbol: String, help: LocalizedStringKey, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: 12, weight: .semibold)).foregroundStyle(HnPalette.muted)
                .frame(width: 30, height: 30)
                .background(Circle().fill(Color.white.opacity(0.08)))
                .overlay(Circle().stroke(Color.white.opacity(0.1), lineWidth: 0.5))
        }
        .buttonStyle(.plain)
        .help(help)
    }

    private func section<Content: View>(_ title: LocalizedStringKey, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.system(size: 14, weight: .medium)).foregroundStyle(HnPalette.ink)
                .accessibilityAddTraits(.isHeader)
            content()
        }
    }

    // MARK: Rows

    private enum RowState { case running, later, off }

    private func surface<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        HoverSurface(content: content)
    }

    private func row(_ rule: DistractionRule, book: RuleBook, state: RowState) -> some View {
        Button { begin(rule, isNew: false) } label: {
            surface {
                VStack(spacing: 0) {
                    HStack(spacing: 12) {
                        RuleGlyph(rule: rule, size: 40)
                            .opacity(state == .off ? 0.45 : 1)
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 5) {
                                Text(verbatim: rule.name).font(.system(size: 16, weight: .medium)).foregroundStyle(HnPalette.ink)
                                if rule.strictness == .locked {
                                    Image(systemName: "lock.fill")
                                        .font(.system(size: 9)).foregroundStyle(HnPalette.faint)
                                        .help(RuleSummary.strictnessDetail(.locked))
                                }
                            }
                            Text(verbatim: subtitle(rule, book: book, state: state))
                                .font(.system(size: 12.5)).foregroundStyle(HnPalette.muted).lineLimit(1).monospacedDigit()
                        }
                        Spacer(minLength: 8)
                        statusPill(rule, state: state)
                        Image(systemName: "chevron.forward").font(.system(size: 11, weight: .semibold)).foregroundStyle(HnPalette.faint)
                    }
                    .padding(.horizontal, 16).padding(.vertical, 15)
                    if state == .running, let fraction = progress(rule, book: book) {
                        GeometryReader { proxy in
                            ZStack(alignment: .leading) {
                                Rectangle().fill(Color.white.opacity(0.06))
                                Rectangle().fill(LinearGradient(colors: [HnPalette.accent.opacity(0.5), HnPalette.accent],
                                                                startPoint: .leading, endPoint: .trailing))
                                    .frame(width: proxy.size.width * fraction)
                            }
                        }
                        .frame(height: 2)
                        .padding(.horizontal, 16)
                        .accessibilityHidden(true)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: "\(rule.name), \(subtitle(rule, book: book, state: state))"))
    }

    private func subtitle(_ rule: DistractionRule, book: RuleBook, state: RowState) -> String {
        switch state {
        case .off:
            return RuleSummary.kindTitle(rule.action, hasWindow: rule.window != nil)
        case .later:
            if let window = rule.window {
                return "\(RuleSummary.days(window.days)), \(RuleSummary.clock(window.startMinute)) – \(RuleSummary.clock(window.endMinute))"
            }
            return RuleSummary.detail(rule, day: book.day)
        case .running:
            if case .block = rule.action, let end = rule.window?.interval(containing: now, calendar: store.calendar)?.end {
                let left = max(1, Int(end.timeIntervalSince(now) / 60))
                return String(localized: "\(RuleSummary.duration(minutes: left)) left · until \(DistractionPanelModel.time(end, now: now))")
            }
            return RuleSummary.detail(rule, day: book.day)
        }
    }

    /// The thin line under a running rule: how much of its time or window is used.
    private func progress(_ rule: DistractionRule, book: RuleBook) -> Double? {
        if case .dailyLimit(let minutes) = rule.action {
            return min(1, (book.day.usedSeconds[rule.id] ?? 0) / Double(minutes * 60))
        }
        if let interval = rule.window?.interval(containing: now, calendar: store.calendar), interval.duration > 0 {
            return min(1, max(0, now.timeIntervalSince(interval.start) / interval.duration))
        }
        return nil
    }

    private func statusPill(_ rule: DistractionRule, state: RowState) -> some View {
        let targets = rule.targets.sorted { RuleSummary.targetName($0) < RuleSummary.targetName($1) }
        return HStack(spacing: 6) {
            if state == .running {
                Circle().fill(HnPalette.calm).frame(width: 7, height: 7)
                Text(verbatim: Self.runningWord(rule.action))
                    .font(.system(size: 12, weight: .medium)).foregroundStyle(HnPalette.calm)
            } else if state == .later, let start = rule.window?.nextStart(after: now, calendar: store.calendar) {
                Image(systemName: "clock").font(.system(size: 10, weight: .semibold)).foregroundStyle(HnPalette.muted)
                Text("Starting in \(RuleSummary.duration(minutes: max(1, Int(start.timeIntervalSince(now) / 60))))")
                    .font(.system(size: 12, weight: .medium)).foregroundStyle(HnPalette.muted)
            }
            HStack(spacing: -4) {
                ForEach(Array(targets.prefix(3)), id: \.self) { target in TargetBadge(target: target) }
            }
            if targets.count > 3 {
                Text(verbatim: "+\(targets.count - 3)").font(.system(size: 11, weight: .medium)).foregroundStyle(HnPalette.muted)
            }
            if state == .off {
                Text("Off").font(.system(size: 11, weight: .semibold)).foregroundStyle(HnPalette.muted)
            }
        }
        .padding(.horizontal, 11).frame(height: 30)
        .background(Capsule().fill(state == .running ? HnPalette.calmSoft : Color.white.opacity(0.07)))
    }

    /// The one word in a running rule's pill.
    static func runningWord(_ action: RuleAction) -> String {
        switch action {
        case .block: return String(localized: "Closed")
        case .dailyLimit: return String(localized: "Limiting")
        case .wait: return String(localized: "Pausing")
        }
    }

    private func focusRow(_ focus: FocusSessionState) -> some View {
        surface {
            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    Text(verbatim: "🎯").font(.system(size: 32)).frame(width: 40, height: 40).accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(verbatim: focus.intent.isEmpty ? String(localized: "Focus") : focus.intent)
                            .font(.system(size: 15, weight: .semibold)).foregroundStyle(HnPalette.ink).lineLimit(1)
                        Text("Focus until \(DistractionPanelModel.time(focus.until, now: now))")
                            .font(.system(size: 12.5)).foregroundStyle(HnPalette.muted)
                    }
                    Spacer(minLength: 8)
                    Text(verbatim: Self.countdown(focus.until.timeIntervalSince(now)))
                        .font(.system(size: 20, weight: .semibold, design: .rounded)).monospacedDigit()
                        .foregroundStyle(HnPalette.accentText)
                    switch store.book.howToEndFocus(at: now) {
                    case .now, .afterWait:
                        Button("End Focus") { store.endFocus() }.buttonStyle(.bordered).controlSize(.small)
                    case .notUntil:
                        Image(systemName: "lock.fill").font(.system(size: 11)).foregroundStyle(HnPalette.faint)
                    }
                }
                .padding(.horizontal, 14).padding(.vertical, 12)
                GeometryReader { proxy in
                    ZStack(alignment: .leading) {
                        Rectangle().fill(Color.white.opacity(0.06))
                        Rectangle().fill(HnPalette.accent).frame(width: proxy.size.width * focus.progress(at: now))
                    }
                }
                .frame(height: 2).padding(.horizontal, 16)
            }
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
    }

    /// "Monday, 24 November", in the person's own format.
    static func dateLine(_ date: Date, locale: Locale = .autoupdatingCurrent) -> String {
        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.setLocalizedDateFormatFromTemplate("EEEEdMMMM")
        return formatter.string(from: date)
    }

    static func countdown(_ seconds: TimeInterval) -> String {
        let total = max(0, Int(seconds))
        let h = total / 3600, m = (total % 3600) / 60, s = total % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%d:%02d", m, s)
    }

    // MARK: Ideas

    private func quick(_ title: LocalizedStringKey, symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            surface {
                HStack(spacing: 8) {
                    Image(systemName: symbol).font(.system(size: 13, weight: .bold)).foregroundStyle(HnPalette.accentText)
                    Text(title).font(.system(size: 14, weight: .semibold)).foregroundStyle(HnPalette.ink)
                }
                .frame(maxWidth: .infinity).frame(height: 48)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// A limit offered from what the ledger measured. It opens the editor filled in; nothing is made without a look.
    private func usageIdea(_ suggestion: UsageSuggestion) -> some View {
        let target = suggestion.target
        let name = target.map(RuleSummary.targetName) ?? suggestion.subject
        let line = suggestion.days > 1
            ? String(localized: "About \(RuleSummary.duration(minutes: Int(suggestion.perDay / 60))) a day over \(suggestion.days) days")
            : String(localized: "\(RuleSummary.duration(minutes: Int(suggestion.today / 60))) today")
        return HStack(spacing: 12) {
            if let target { TargetBadge(target: target).scaleEffect(1.7).frame(width: 40, height: 40) }
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: name).font(.system(size: 15, weight: .semibold)).foregroundStyle(HnPalette.ink)
                Text(verbatim: line).font(.system(size: 12.5)).foregroundStyle(HnPalette.muted).lineLimit(1).monospacedDigit()
            }
            Spacer(minLength: 8)
            Button {
                guard let target else { return }
                begin(DistractionRule(name: name, targets: [target],
                                      action: .dailyLimit(minutesPerDay: suggestion.limitMinutes), emoji: "⏳"), isNew: true)
            } label: {
                Text("Limit to \(RuleSummary.duration(minutes: suggestion.limitMinutes))")
                    .font(.system(size: 12, weight: .semibold))
                    .padding(.horizontal, 10).frame(height: 26)
                    .background(Capsule().fill(Color.white.opacity(0.1)))
                    .foregroundStyle(HnPalette.ink)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
            .stroke(HnPalette.accent.opacity(0.35), style: StrokeStyle(lineWidth: 1, dash: [4, 4])))
        .accessibilityElement(children: .combine)
    }

    private func idea(_ template: RuleTemplate) -> some View {
        HStack(spacing: 12) {
            Text(verbatim: template.emoji).font(.system(size: 32)).frame(width: 40, height: 40).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: template.title).font(.system(size: 15, weight: .semibold)).foregroundStyle(HnPalette.ink)
                Text(verbatim: template.blurb).font(.system(size: 12.5)).foregroundStyle(HnPalette.muted).lineLimit(2)
            }
            Spacer(minLength: 8)
            Button { begin(template.make(), isNew: true) } label: {
                Label("Add", systemImage: "plus").font(.system(size: 12, weight: .semibold))
                    .padding(.horizontal, 10).frame(height: 26)
                    .background(Capsule().fill(Color.white.opacity(0.1)))
                    .foregroundStyle(HnPalette.ink)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
            .stroke(Color.white.opacity(0.12), style: StrokeStyle(lineWidth: 1, dash: [4, 4])))
    }

    private func pending(_ book: RuleBook) -> some View {
        section("Waiting to apply") {
            ForEach(book.pending) { change in
                let name = book.rules.first { $0.id == change.ruleID }?.name ?? ""
                HnNotice(title: nil,
                         message: change.replacement == nil
                            ? "“\(name)” will be removed at \(DistractionPanelModel.time(change.applyAt, now: now))."
                            : "A weaker “\(name)” applies at \(DistractionPanelModel.time(change.applyAt, now: now)).",
                         systemImage: "clock", level: .info,
                         action: (title: "Keep it as it is", action: { store.cancelPending(change.id) }))
            }
        }
    }

    // MARK: Editing

    private func begin(_ rule: DistractionRule, isNew: Bool) {
        message = nil
        editingIsNew = isNew
        editing = rule
    }

    @ViewBuilder
    private var editorSheet: some View {
        if let binding = Binding($editing) {
            ScrollView {
                RuleEditor(rule: binding, isNew: editingIsNew, message: message, save: {
                    guard var rule = editing else { return }
                    rule.name = rule.name.trimmingCharacters(in: .whitespaces)
                    let outcome = store.propose(rule, replacing: editingIsNew ? nil : rule.id)
                    if case .applied = outcome { editing = nil } else { message = RuleSummary.outcome(outcome) }
                }, delete: {
                    guard let rule = editing else { return }
                    if editingIsNew { editing = nil; return }
                    let outcome = store.propose(nil, replacing: rule.id)
                    if case .applied = outcome { editing = nil } else { message = RuleSummary.outcome(outcome) }
                }, cancel: { editing = nil })
            }
            .frame(width: 420, height: 640)
            .background(HnPalette.canvas)
        }
    }
}

/// Sites no rule may close: the music half of YouTube, a work tool on a site
/// that is otherwise limited. Adult protection is separate and ignores this list.
struct ExceptionsSheet: View {
    @ObservedObject var store: DistractionStore
    let done: () -> Void
    @State private var newSite = ""
    @State private var message: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                Text(verbatim: "✅").font(.system(size: 30)).accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Never closed").font(.hnTitle())
                    Text("These sites stay open under every rule and in Focus. Adult protection ignores this list.")
                        .font(.hnCaption()).foregroundStyle(HnPalette.muted).fixedSize(horizontal: false, vertical: true)
                }
            }
            let hosts = store.book.exceptions.sorted()
            if hosts.isEmpty {
                Text("Nothing here yet. For example, music.youtube.com.")
                    .font(.hnBody()).foregroundStyle(HnPalette.muted)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(hosts.enumerated()), id: \.element) { index, host in
                        HStack(spacing: 8) {
                            Image(systemName: "globe").foregroundStyle(HnPalette.muted).frame(width: 18)
                            Text(verbatim: host).font(.hnBody()).foregroundStyle(HnPalette.ink)
                            Spacer()
                            Button { store.setException(host, on: false); message = nil } label: {
                                Image(systemName: "minus.circle.fill").foregroundStyle(HnPalette.faint)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(Text("Remove \(host)"))
                        }
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        if index < hosts.count - 1 { HnHairline().padding(.leading, 38) }
                    }
                }
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(HnPalette.card))
            }
            HStack(spacing: 6) {
                TextField("Add a site, like music.youtube.com", text: $newSite)
                    .textFieldStyle(.roundedBorder).onSubmit(add)
                Button("Add", action: add).disabled(newSite.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            if let message {
                Text(verbatim: message).font(.hnCaption()).foregroundStyle(HnPalette.danger)
                    .fixedSize(horizontal: false, vertical: true)
            }
            HStack {
                Spacer()
                Button("Done", action: done).keyboardShortcut(.defaultAction)
            }
        }
        .padding(20)
        .frame(width: 440)
        .background(HnPalette.canvas)
    }

    private func add() {
        switch store.setException(newSite, on: true) {
        case .applied:
            newSite = ""; message = nil
        case .refused(let until):
            message = until.map { String(localized: "A locked rule covers this site. You can add it after \(DistractionPanelModel.time($0, now: Date())).") }
                ?? String(localized: "A locked rule covers this site, so it can’t be added right now.")
        case .invalid, .scheduled:
            message = String(localized: "That doesn’t look like a site address.")
        }
    }
}

/// The rounded surface every row sits on. It lifts a little under the pointer,
/// so a row reads as something to click before it is clicked.
struct HoverSurface<Content: View>: View {
    @ViewBuilder let content: Content
    @State private var hovered = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        content
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.white.opacity(hovered ? 0.10 : 0.06)))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(hovered ? 0.16 : 0.08), lineWidth: 0.5))
            .onHover { inside in
                if reduceMotion { hovered = inside } else { withAnimation(.easeOut(duration: 0.12)) { hovered = inside } }
            }
    }
}

/// A small round badge for one target: the app's own icon, or the site's initial.
struct TargetBadge: View {
    let target: RuleTarget

    var body: some View {
        Group {
            if case .app(let id) = target, let icon = AppCatalog.icon(for: id) {
                Image(nsImage: icon).resizable()
            } else {
                let name = RuleSummary.targetName(target)
                ZStack {
                    Circle().fill(Self.color(for: name))
                    Text(verbatim: String(name.prefix(1)).uppercased())
                        .font(.system(size: 9, weight: .bold)).foregroundColor(.white)
                }
            }
        }
        .frame(width: 18, height: 18)
        .clipShape(Circle())
        .overlay(Circle().stroke(Color.black.opacity(0.35), lineWidth: 1))
        .accessibilityHidden(true)
    }

    /// A steady colour per name, so YouTube is always the same dot.
    static func color(for name: String) -> Color {
        let known: [String: Color] = ["youtube.com": .red, "x.com": Color(white: 0.2), "reddit.com": .orange,
                                      "instagram.com": .pink, "tiktok.com": Color(white: 0.15),
                                      "facebook.com": .blue, "discord.com": .indigo, "twitch.tv": .purple]
        if let color = known[name] { return color }
        let hue = Double(name.unicodeScalars.reduce(0) { ($0 &* 31 &+ Int($1.value)) % 360 }) / 360
        return Color(hue: hue, saturation: 0.55, brightness: 0.7)
    }
}

/// The sprout, large in the sidebar: the thing the whole app is about, always
/// in view, the way Opal keeps its gem there.
struct UnrotSidebarGlass: View {
    @ObservedObject var store: DistractionStore
    let open: () -> Void
    @State private var glowing = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let sprout = SproutProgress(givenBack: store.book.givenBackSeconds(at: store.clock(), calendar: store.calendar))
        Button(action: open) {
            VStack(spacing: 6) {
                Image(SproutProgress.image(sprout.stage)).resizable().interpolation(.high).scaledToFill()
                    .frame(width: 150, height: 150)
                    .mask(RadialGradient(colors: [.black, .black, .clear], center: .center, startRadius: 40, endRadius: 76))
                    // The light under the glass breathes slowly, about one breath in ten seconds.
                    .shadow(color: Color(red: 0.94, green: 0.73, blue: 0.24).opacity(glowing ? 0.38 : 0.18),
                            radius: glowing ? 24 : 14)
                    .onAppear {
                        guard !reduceMotion, !AppDelegate.isHostingTests else { return }
                        withAnimation(.easeInOut(duration: 5).repeatForever(autoreverses: true)) { glowing = true }
                    }
                Text(verbatim: SproutProgress.parts(sprout.givenBack).map { $0.0 + $0.1 }.joined(separator: " "))
                    .font(.system(size: 20, weight: .semibold, design: .rounded)).monospacedDigit()
                    .foregroundStyle(HnPalette.ink)
                Text("given back · \(SproutProgress.name(sprout.stage))")
                    .font(.system(size: 11.5)).foregroundStyle(HnPalette.muted)
                HStack(spacing: 3) {
                    ForEach(1...7, id: \.self) { index in
                        Capsule().fill(index <= sprout.stage ? HnPalette.accent : Color.white.opacity(0.12))
                            .frame(height: 3)
                    }
                }
                .frame(width: 110)
                .padding(.top, 2)
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("\(SproutProgress.name(sprout.stage)), stage \(sprout.stage) of 7"))
    }
}

/// The gold pill at the head of the sidebar: the one thing you come here to do.
struct UnrotNewRuleButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: "plus").font(.system(size: 12, weight: .bold))
                Text("New rule").font(.system(size: 13, weight: .semibold))
            }
            .frame(maxWidth: .infinity).frame(height: 34)
            .foregroundColor(Color(red: 0.17, green: 0.11, blue: 0.01))
            .background(Capsule().fill(LinearGradient(colors: [Color(red: 0.99, green: 0.86, blue: 0.52),
                                                               Color(red: 0.90, green: 0.66, blue: 0.18)],
                                                      startPoint: .topLeading, endPoint: .bottomTrailing)))
            .overlay(Capsule().stroke(Color.white.opacity(0.4), lineWidth: 0.5))
            .shadow(color: Color(red: 0.94, green: 0.73, blue: 0.24).opacity(0.35), radius: 10, y: 2)
        }
        .buttonStyle(.plain)
        .keyboardShortcut("n", modifiers: .command)
    }
}
