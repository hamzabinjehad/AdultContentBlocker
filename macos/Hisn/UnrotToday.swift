import SwiftUI

/// The sprout under glass, as numbers: which stage, how far to the next.
struct SproutProgress: Equatable {
    var stage: Int          // 1...7
    var givenBack: Double   // seconds
    var fraction: Double    // progress inside the stage, 0...1
    var toNext: Double?     // seconds to the next stage; nil at the last

    init(givenBack: Double) {
        self.givenBack = max(0, givenBack)
        stage = TimeGivenBack.stage(forSeconds: self.givenBack)
        let starts = TimeGivenBack.stageStarts
        if stage < starts.count {
            let from = starts[stage - 1], to = starts[stage]
            fraction = min(1, max(0, (self.givenBack - from) / (to - from)))
            toNext = to - self.givenBack
        } else {
            fraction = 1
            toNext = nil
        }
    }

    static func name(_ stage: Int) -> String {
        switch stage {
        case 1: return String(localized: "Seed")
        case 2: return String(localized: "Sprout")
        case 3: return String(localized: "Two leaves")
        case 4: return String(localized: "Young plant")
        case 5: return String(localized: "Bud")
        case 6: return String(localized: "Flower")
        default: return String(localized: "Tree")
        }
    }

    /// The painted glass for a stage. Three paintings cover seven stages until
    /// the in-between ones are painted.
    static func image(_ stage: Int) -> String {
        switch stage {
        case 1: return "UnrotGlassSeed"
        case 2...4: return "UnrotGlassSprout"
        default: return "UnrotGlassTree"
        }
    }

    /// "1d 4h 12m" split for the big number: value and unit pairs.
    static func parts(_ seconds: Double) -> [(String, String)] {
        let minutes = Int(seconds / 60)
        let d = minutes / 1440, h = (minutes % 1440) / 60, m = minutes % 60
        var out: [(String, String)] = []
        if d > 0 { out.append(("\(d)", String(localized: "d"))) }
        if d > 0 || h > 0 { out.append(("\(h)", String(localized: "h"))) }
        out.append(("\(m)", String(localized: "m")))
        return out
    }
}

/// Unrot's first page: the sprout, today's numbers, and what is running.
/// Laid out for a Mac window: the scene and the lists side by side, a narrow
/// column of facts at the trailing edge.
struct UnrotTodayPage: View {
    @ObservedObject var store: DistractionStore
    let open: (ContentView.Page) -> Void
    @State private var showingFocus = false
    private let refresh = Timer.publish(every: 20, on: .main, in: .common).autoconnect()

    init(store: DistractionStore = .shared, open: @escaping (ContentView.Page) -> Void) {
        self.store = store
        self.open = open
    }

    private var now: Date { store.clock() }

    var body: some View {
        let book = store.book
        let sprout = SproutProgress(givenBack: book.givenBackSeconds(at: now, calendar: store.calendar))
        VStack(alignment: .leading, spacing: 18) {
            hero(sprout)
            HnTwoColumn(breakpoint: 780, spacing: 22, railWidth: 280) {
                VStack(alignment: .leading, spacing: 18) {
                    tiles(book)
                    running(book)
                }
            } rail: {
                VStack(alignment: .leading, spacing: 18) {
                    hours(book)
                    pulls(book)
                }
            }
        }
        .sheet(isPresented: $showingFocus) { FocusStartSheet(store: store) { showingFocus = false } }
        .onReceive(refresh) { _ in store.tick() }
        .onAppear { store.tick() }
    }

    // MARK: Hero

    private func hero(_ sprout: SproutProgress) -> some View {
        let gold = Color(red: 0.94, green: 0.73, blue: 0.24)
        let cream = Color(red: 0.97, green: 0.93, blue: 0.85)
        let dim = Color(red: 0.80, green: 0.74, blue: 0.62)
        return HStack(alignment: .center, spacing: 8) {
            Image(SproutProgress.image(sprout.stage))
                .resizable().interpolation(.high).scaledToFill()
                .frame(width: 230, height: 230)
                .mask(RadialGradient(colors: [.black, .black, .clear], center: .center, startRadius: 60, endRadius: 118))
                .accessibilityLabel(Text(verbatim: SproutProgress.name(sprout.stage)))
            VStack(alignment: .leading, spacing: 6) {
                Text("\(SproutProgress.name(sprout.stage)) · stage \(sprout.stage) of 7")
                    .font(.system(size: 11, weight: .bold)).tracking(1.2).textCase(.uppercase)
                    .foregroundColor(gold)
                HStack(alignment: .firstTextBaseline, spacing: 2) {
                    ForEach(Array(SproutProgress.parts(sprout.givenBack).enumerated()), id: \.offset) { _, part in
                        Text(verbatim: part.0).font(.system(size: 46, weight: .bold, design: .serif)).foregroundColor(cream)
                        Text(verbatim: part.1).font(.system(size: 22, weight: .semibold, design: .serif)).foregroundColor(dim)
                            .padding(.trailing, 6)
                    }
                }
                .monospacedDigit()
                .accessibilityElement(children: .combine)
                Text("given back from feeds, on this Mac").font(.system(size: 13)).foregroundColor(dim)
                HStack(spacing: 4) {
                    ForEach(1...7, id: \.self) { index in
                        GeometryReader { proxy in
                            ZStack(alignment: .leading) {
                                Capsule().fill(Color.white.opacity(0.14))
                                Capsule().fill(gold)
                                    .frame(width: proxy.size.width * (index < sprout.stage ? 1 : index == sprout.stage ? sprout.fraction : 0))
                            }
                        }
                        .frame(height: 5)
                    }
                }
                .frame(maxWidth: 360)
                .padding(.top, 8)
                .accessibilityHidden(true)
                if let toNext = sprout.toNext {
                    Text("\(SproutProgress.name(sprout.stage + 1)) in \(RuleSummary.duration(minutes: max(1, Int(toNext / 60))))")
                        .font(.system(size: 12.5)).foregroundColor(dim)
                } else {
                    Text("Fully grown. It keeps counting.").font(.system(size: 12.5)).foregroundColor(dim)
                }
                HStack(spacing: 8) {
                    heroButton("Breathe", symbol: "wind", filled: true) { PauseWindowController.shared.show() }
                    if let focus = store.book.focus, focus.isRunning(at: now) {
                        heroButton("Focus until \(DistractionPanelModel.time(focus.until, now: now))", symbol: "target", filled: false) { open(.focus) }
                    } else {
                        heroButton("Start Focus…", symbol: "target", filled: false) { showingFocus = true }
                    }
                }
                .padding(.top, 10)
            }
            .padding(.vertical, 22)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .frame(maxWidth: .infinity, minHeight: 250, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(RadialGradient(colors: [Color(red: 0.36, green: 0.24, blue: 0.08), Color(red: 0.13, green: 0.09, blue: 0.04),
                                              Color(red: 0.08, green: 0.05, blue: 0.02)],
                                     center: UnitPoint(x: 0.16, y: 0.3), startRadius: 10, endRadius: 520))
        )
        .environment(\.colorScheme, .dark)
    }

    private func heroButton(_ title: LocalizedStringKey, symbol: String, filled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: symbol)
                .font(.system(size: 13, weight: .semibold))
                .padding(.horizontal, 14).frame(height: 32)
                .foregroundColor(filled ? Color(red: 0.17, green: 0.11, blue: 0.01) : Color(red: 0.97, green: 0.93, blue: 0.85))
                .background(Capsule().fill(filled
                    ? AnyShapeStyle(LinearGradient(colors: [Color(red: 0.95, green: 0.76, blue: 0.35), Color(red: 0.85, green: 0.60, blue: 0.12)],
                                                   startPoint: .top, endPoint: .bottom))
                    : AnyShapeStyle(Color.white.opacity(0.13))))
                .overlay(Capsule().stroke(Color.white.opacity(filled ? 0.3 : 0.18), lineWidth: 0.5))
        }
        .buttonStyle(.plain)
    }

    // MARK: Tiles

    private func tiles(_ book: RuleBook) -> some View {
        let feed = book.feedSecondsToday()
        let aim = max(60, book.beforePerDay)
        return HStack(alignment: .top, spacing: 12) {
            tile {
                HnSectionLabel(title: "In feeds today")
                Text(verbatim: RuleSummary.duration(minutes: Int(feed / 60))).font(.hnFigure(28)).foregroundStyle(HnPalette.ink)
                ProgressView(value: min(1, feed / aim)).tint(feed > aim ? HnPalette.ember : HnPalette.accent)
                Text("Before Unrot: \(RuleSummary.duration(minutes: Int(aim / 60))) a day")
                    .font(.hnCaption()).foregroundStyle(HnPalette.muted)
            }
            tile {
                HnSectionLabel(title: "Pauses")
                Text(verbatim: "\(book.pausesToday)").font(.hnFigure(28)).foregroundStyle(HnPalette.ink)
                Text("Times you stopped to breathe first").font(.hnCaption()).foregroundStyle(HnPalette.muted)
            }
            tile {
                HnSectionLabel(title: "Closed")
                Text(verbatim: "\(book.closedToday)").font(.hnFigure(28)).foregroundStyle(HnPalette.ink)
                Text("Times a rule kept something shut").font(.hnCaption()).foregroundStyle(HnPalette.muted)
            }
        }
    }

    private func tile<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6) { content() }
            .padding(14)
            .frame(maxWidth: .infinity, minHeight: 112, alignment: .topLeading)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(HnPalette.card))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(HnPalette.line, lineWidth: 0.5))
    }

    // MARK: Running

    private func running(_ book: RuleBook) -> some View {
        let rules = book.rules.filter { $0.isRunning(at: now, calendar: store.calendar) }
        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                HnSectionLabel(title: "Running now")
                Spacer()
                HnButton(title: "All rules", kind: .text, compact: true) { open(.focus) }
            }
            if rules.isEmpty {
                HnCard(padding: 16) {
                    HStack(spacing: 12) {
                        Image(systemName: "plus.circle.fill").font(.system(size: 22)).foregroundStyle(HnPalette.accent)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(book.rules.isEmpty ? "No rules yet" : "Nothing is running right now")
                                .font(.hnCardTitle()).foregroundStyle(HnPalette.ink)
                            Text("A daily limit on feeds is a good first one.").font(.hnCaption()).foregroundStyle(HnPalette.muted)
                        }
                        Spacer()
                        HnButton(title: "Add a rule", kind: .outline) { open(.focus) }
                    }
                }
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(rules.enumerated()), id: \.element.id) { index, rule in
                        Button { open(.focus) } label: {
                            HStack(spacing: 12) {
                                RuleTile(symbol: RuleTile.symbol(for: rule), tint: RuleTile.tint(for: rule))
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(verbatim: rule.name).font(.system(size: 13, weight: .semibold)).foregroundStyle(HnPalette.ink)
                                    Text(verbatim: "\(RuleSummary.targets(rule, limit: 2)) · \(RuleSummary.detail(rule, day: book.day))")
                                        .font(.hnCaption()).foregroundStyle(HnPalette.muted).lineLimit(1)
                                }
                                Spacer(minLength: 8)
                                if case .dailyLimit(let minutes) = rule.action {
                                    ProgressView(value: min(1, (book.day.usedSeconds[rule.id] ?? 0) / Double(minutes * 60)))
                                        .frame(width: 80).tint(RuleTile.tint(for: rule))
                                }
                                Image(systemName: "chevron.forward").font(.system(size: 10, weight: .semibold)).foregroundStyle(HnPalette.faint)
                            }
                            .padding(.horizontal, 12).padding(.vertical, 9)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        if index < rules.count - 1 { HnHairline().padding(.leading, 50) }
                    }
                }
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(HnPalette.card))
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(HnPalette.line, lineWidth: 0.5))
            }
        }
    }

    // MARK: Rail

    private func hours(_ book: RuleBook) -> some View {
        let seconds = book.ledger.secondsByHour(calendar: store.calendar)
        let current = store.calendar.component(.hour, from: now)
        return VStack(alignment: .leading, spacing: 8) {
            HnSectionLabel(title: "This Mac today, hour by hour")
            HStack(alignment: .bottom, spacing: 3) {
                ForEach(0..<24, id: \.self) { hour in
                    let share = min(1, seconds[hour] / 3600)
                    RoundedRectangle(cornerRadius: 2, style: .continuous)
                        .fill(share > 0 ? HnPalette.accent.opacity(0.35 + 0.65 * share) : HnPalette.soft)
                        .frame(height: 8 + 34 * share)
                        .overlay(alignment: .bottom) {
                            if hour == current {
                                Circle().fill(HnPalette.ink).frame(width: 3, height: 3).offset(y: 7)
                            }
                        }
                }
            }
            .frame(height: 46, alignment: .bottom)
            .padding(.bottom, 6)
            HStack {
                ForEach([0, 6, 12, 18, 24], id: \.self) { mark in
                    Text(verbatim: "\(mark)").font(.system(size: 10)).foregroundStyle(HnPalette.faint)
                    if mark != 24 { Spacer() }
                }
            }
            .accessibilityHidden(true)
        }
    }

    private func pulls(_ book: RuleBook) -> some View {
        let top = book.ledger.topSubjects(limit: 5).filter { $0.seconds >= 60 }
        return VStack(alignment: .leading, spacing: 8) {
            HnSectionLabel(title: "Most time")
            if top.isEmpty {
                Text("Nothing yet today. Unrot counts only the app in front, and keeps it on this Mac.")
                    .font(.hnCaption()).foregroundStyle(HnPalette.muted).fixedSize(horizontal: false, vertical: true)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(top.enumerated()), id: \.offset) { index, item in
                        HStack(spacing: 8) {
                            if item.subject.hasPrefix("app:"), let icon = AppCatalog.icon(for: String(item.subject.dropFirst(4))) {
                                Image(nsImage: icon).resizable().frame(width: 18, height: 18)
                            } else {
                                Image(systemName: "globe").frame(width: 18, height: 18).foregroundStyle(HnPalette.muted)
                            }
                            Text(verbatim: item.subject.hasPrefix("app:") ? AppCatalog.name(for: String(item.subject.dropFirst(4)))
                                                                         : String(item.subject.split(separator: ":", maxSplits: 1).last ?? ""))
                                .font(.hnBody()).foregroundStyle(HnPalette.ink).lineLimit(1)
                            Spacer(minLength: 6)
                            Text(verbatim: RuleSummary.duration(minutes: Int(item.seconds / 60)))
                                .font(.hnCaption()).foregroundStyle(HnPalette.muted).monospacedDigit()
                        }
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        if index < top.count - 1 { HnHairline().padding(.leading, 38) }
                    }
                }
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(HnPalette.card))
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(HnPalette.line, lineWidth: 0.5))
            }
        }
    }
}

/// The sprout at the foot of the sidebar: always in view, like Opal's gem.
struct UnrotSidebarSprout: View {
    @ObservedObject var store: DistractionStore
    let open: () -> Void

    var body: some View {
        let sprout = SproutProgress(givenBack: store.book.givenBackSeconds(at: store.clock(), calendar: store.calendar))
        Button(action: open) {
            HStack(spacing: 10) {
                Image(SproutProgress.image(sprout.stage)).resizable().scaledToFill()
                    .frame(width: 38, height: 38).clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                VStack(alignment: .leading, spacing: 1) {
                    Text(verbatim: SproutProgress.name(sprout.stage)).font(.system(size: 12.5, weight: .semibold)).foregroundStyle(HnPalette.ink)
                    Text(verbatim: SproutProgress.parts(sprout.givenBack).map { $0.0 + $0.1 }.joined(separator: " "))
                        .font(.system(size: 11.5)).foregroundStyle(HnPalette.muted).monospacedDigit()
                }
                Spacer(minLength: 0)
            }
            .padding(8)
            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(HnPalette.soft))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("\(SproutProgress.name(sprout.stage)), stage \(sprout.stage) of 7"))
    }
}
