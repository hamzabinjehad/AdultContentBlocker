import SwiftUI

/// The last seven days as numbers, worked out from the rule book. Only what
/// this Mac measured: a day without data is empty, never a guess.
struct ProgressSummary: Equatable {
    struct Day: Equatable, Identifiable {
        var key: String
        var date: Date
        /// nil: no record for that day (Unrot had no rules, or was not installed).
        var feedSeconds: Double?
        var isToday: Bool
        var id: String { key }
    }

    var days: [Day]
    var beforePerDay: Double
    /// How much less feed time than the person's "before", over the days with
    /// data, as a share from -1 (twice as much) to 1 (none at all). nil without data.
    var lessThanBefore: Double?
    var daysWithUnrot: Int
    var givenBack: Double
    var pauses: Int
    var closed: Int

    init(book: RuleBook, now: Date, calendar: Calendar) {
        beforePerDay = book.beforePerDay
        let today = calendar.startOfDay(for: now)
        let byKey = Dictionary(book.history.map { ($0.day, $0) }, uniquingKeysWith: { _, new in new })
        var built: [Day] = []
        for offset in (0..<7).reversed() {
            guard let date = calendar.date(byAdding: .day, value: -offset, to: today) else { continue }
            let key = UsageLedger.dayKey(date, calendar: calendar)
            let isToday = offset == 0
            let seconds: Double? = isToday ? (book.rules.isEmpty ? nil : book.feedSecondsToday())
                                           : byKey[key]?.feedSeconds
            built.append(Day(key: key, date: date, feedSeconds: seconds, isToday: isToday))
        }
        days = built
        // Today is still running, so the comparison uses finished days when there are any.
        let finished = built.filter { !$0.isToday }.compactMap(\.feedSeconds)
        if !finished.isEmpty, beforePerDay > 0 {
            let average = finished.reduce(0, +) / Double(finished.count)
            lessThanBefore = max(-1, min(1, 1 - average / beforePerDay))
        } else {
            lessThanBefore = nil
        }
        daysWithUnrot = book.history.count + (book.rules.isEmpty ? 0 : 1)
        givenBack = book.givenBackSeconds(at: now, calendar: calendar)
        let week = Set(built.map(\.key))
        pauses = book.history.filter { week.contains($0.day) }.reduce(book.pausesToday) { $0 + $1.pauses }
        closed = book.history.filter { week.contains($0.day) }.reduce(book.closedToday) { $0 + $1.closed }
    }

    /// The plant a day earned: a tree for a light day, a sprout for a day
    /// under the "before", a seed for a heavy one. Never a dead plant.
    static func plant(feedSeconds: Double?, before: Double) -> String? {
        guard let feedSeconds else { return nil }
        if feedSeconds <= before * 0.5 { return "UnrotGlassTree" }
        if feedSeconds <= before { return "UnrotGlassSprout" }
        return "UnrotGlassSeed"
    }

    var headline: String {
        guard let less = lessThanBefore else { return String(localized: "Your first week") }
        let percent = Int((abs(less) * 100).rounded())
        return less >= 0 ? String(localized: "\(percent)% less feed time")
                         : String(localized: "\(percent)% more feed time")
    }
}

/// Progress, in the spirit of CleanPath's Analytics page but with nothing
/// invented: the week against your own "before", a garden of days, and where
/// today went.
struct UnrotProgressPage: View {
    @ObservedObject var store: DistractionStore
    private let refresh = Timer.publish(every: 30, on: .main, in: .common).autoconnect()

    init(store: DistractionStore = .shared) { self.store = store }

    var body: some View {
        let now = store.clock()
        let summary = ProgressSummary(book: store.book, now: now, calendar: store.calendar)
        VStack(alignment: .leading, spacing: 22) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Progress").font(.system(size: 24, weight: .bold)).foregroundStyle(HnPalette.ink)
                Text("Measured on this Mac. Nothing here leaves it.")
                    .font(.system(size: 12.5)).foregroundStyle(HnPalette.muted)
            }
            gauge(summary)
            HStack(spacing: 12) {
                stat(RuleSummary.duration(minutes: Int(summary.givenBack / 60)), "Given back")
                stat("\(summary.daysWithUnrot)", "Days with Unrot")
                stat("\(summary.pauses)", "Pauses this week")
                stat("\(summary.closed)", "Closed this week")
            }
            week(summary)
            garden(summary)
            hours(now)
        }
        .onReceive(refresh) { _ in store.tick() }
        .onAppear { store.tick() }
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) { content() }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.white.opacity(0.06)))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Color.white.opacity(0.08), lineWidth: 0.5))
    }

    private func header(_ title: LocalizedStringKey) -> some View {
        Text(title).font(.system(size: 14, weight: .medium)).foregroundStyle(HnPalette.ink)
            .accessibilityAddTraits(.isHeader)
    }

    // MARK: Gauge

    private func gauge(_ summary: ProgressSummary) -> some View {
        let less = summary.lessThanBefore
        let fill = less.map { max(0, min(1, $0)) } ?? 0
        return card {
            HStack(alignment: .center, spacing: 18) {
                ZStack {
                    ZStack {
                        Circle().trim(from: 0.15, to: 0.85)
                            .stroke(Color.white.opacity(0.1), style: StrokeStyle(lineWidth: 10, lineCap: .round))
                        Circle().trim(from: 0.15, to: 0.15 + 0.7 * fill)
                            .stroke(LinearGradient(colors: [HnPalette.calm, HnPalette.accent], startPoint: .leading, endPoint: .trailing),
                                    style: StrokeStyle(lineWidth: 10, lineCap: .round))
                    }
                    .rotationEffect(.degrees(90))
                    Text(verbatim: less.map { "\(Int((abs($0) * 100).rounded()))%" } ?? "–")
                        .font(.system(size: 24, weight: .bold, design: .rounded)).foregroundStyle(HnPalette.ink)
                }
                .frame(width: 96, height: 96)
                .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 4) {
                    Text(verbatim: summary.headline).font(.system(size: 20, weight: .bold)).foregroundStyle(HnPalette.ink)
                    Text(less == nil
                         ? "After your first full day with a rule, this shows your feed time against your own “before”."
                         : "Against your own “before” of \(RuleSummary.duration(minutes: Int(summary.beforePerDay / 60))) a day, over the finished days of this week.")
                        .font(.system(size: 12.5)).foregroundStyle(HnPalette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("A heavy day adds nothing and takes nothing away.")
                        .font(.system(size: 12)).foregroundStyle(HnPalette.faint)
                }
            }
        }
    }

    private func stat(_ value: String, _ label: LocalizedStringKey) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(verbatim: value).font(.system(size: 22, weight: .semibold, design: .rounded)).monospacedDigit()
                .foregroundStyle(HnPalette.ink).lineLimit(1).minimumScaleFactor(0.7)
            Text(label).font(.system(size: 12)).foregroundStyle(HnPalette.muted).lineLimit(1)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.white.opacity(0.06)))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Color.white.opacity(0.08), lineWidth: 0.5))
    }

    // MARK: Week

    private func week(_ summary: ProgressSummary) -> some View {
        let top = max(summary.beforePerDay * 1.25, summary.days.compactMap(\.feedSeconds).max() ?? 0, 60)
        let symbols = store.calendar.veryShortWeekdaySymbols
        return VStack(alignment: .leading, spacing: 8) {
            header("Feed time, last 7 days")
            card {
                GeometryReader { proxy in
                    let height = proxy.size.height
                    ZStack(alignment: .bottomLeading) {
                        // Your "before", as a dashed line across the week.
                        Path { path in
                            let y = height * (1 - summary.beforePerDay / top)
                            path.move(to: CGPoint(x: 0, y: y)); path.addLine(to: CGPoint(x: proxy.size.width, y: y))
                        }
                        .stroke(Color.white.opacity(0.35), style: StrokeStyle(lineWidth: 1, dash: [4, 4]))
                        HStack(alignment: .bottom, spacing: 10) {
                            ForEach(summary.days) { day in
                                let share = (day.feedSeconds ?? 0) / top
                                RoundedRectangle(cornerRadius: 6, style: .continuous)
                                    .fill(day.feedSeconds == nil ? AnyShapeStyle(Color.white.opacity(0.05))
                                          : (day.feedSeconds ?? 0) > summary.beforePerDay ? AnyShapeStyle(HnPalette.ember)
                                          : AnyShapeStyle(LinearGradient(colors: [HnPalette.accent, HnPalette.accent.opacity(0.55)],
                                                                         startPoint: .top, endPoint: .bottom)))
                                    .frame(height: day.feedSeconds == nil ? 4 : max(4, height * share))
                                    .overlay(alignment: .top) {
                                        if day.isToday { Circle().fill(HnPalette.ink).frame(width: 4, height: 4).offset(y: -9) }
                                    }
                                    .frame(maxWidth: .infinity)
                                    .help(day.feedSeconds.map { RuleSummary.duration(minutes: Int($0 / 60)) } ?? "")
                            }
                        }
                    }
                }
                .frame(height: 120)
                HStack(spacing: 10) {
                    ForEach(summary.days) { day in
                        Text(verbatim: symbols[store.calendar.component(.weekday, from: day.date) - 1])
                            .font(.system(size: 11, weight: day.isToday ? .bold : .regular))
                            .foregroundStyle(day.isToday ? HnPalette.ink : HnPalette.muted)
                            .frame(maxWidth: .infinity)
                    }
                }
                Text("The dashed line is your “before”. Gold is under it, amber is over it.")
                    .font(.system(size: 12)).foregroundStyle(HnPalette.faint)
            }
        }
    }

    private var todayFraction: Double {
        let now = store.clock()
        return min(1, max(0.02, now.timeIntervalSince(store.calendar.startOfDay(for: now)) / 86_400))
    }

    // MARK: Garden

    private func garden(_ summary: ProgressSummary) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            header("Your garden")
            card {
                HStack(spacing: 10) {
                    ForEach(summary.days) { day in
                        VStack(spacing: 4) {
                            // Today is not over: it is judged against the part of the day that has passed.
                            let before = day.isToday ? summary.beforePerDay * todayFraction : summary.beforePerDay
                            if let plant = ProgressSummary.plant(feedSeconds: day.feedSeconds, before: before) {
                                Image(plant).resizable().scaledToFill()
                                    .frame(maxWidth: .infinity).aspectRatio(1, contentMode: .fit)
                                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                                    .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .stroke(day.isToday ? HnPalette.accent : Color.clear, lineWidth: 2))
                            } else {
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .stroke(Color.white.opacity(0.12), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                                    .aspectRatio(1, contentMode: .fit)
                            }
                        }
                    }
                }
                Text("One glass a day. A light day grows a tree, a heavy day stays a seed. Nothing dies.")
                    .font(.system(size: 12)).foregroundStyle(HnPalette.faint)
            }
        }
    }

    // MARK: Hours

    private func hours(_ now: Date) -> some View {
        let seconds = store.book.ledger.secondsByHour(calendar: store.calendar)
        let current = store.calendar.component(.hour, from: now)
        let top = store.book.ledger.topSubjects(limit: 4).filter { $0.seconds >= 60 }
        return VStack(alignment: .leading, spacing: 8) {
            header("Today on this Mac")
            card {
                HStack(alignment: .bottom, spacing: 3) {
                    ForEach(0..<24, id: \.self) { hour in
                        let share = min(1, seconds[hour] / 3600)
                        RoundedRectangle(cornerRadius: 2, style: .continuous)
                            .fill(share > 0 ? HnPalette.accent.opacity(0.35 + 0.65 * share) : Color.white.opacity(0.07))
                            .frame(height: 8 + 40 * share)
                            .overlay(alignment: .bottom) {
                                if hour == current { Circle().fill(HnPalette.ink).frame(width: 3, height: 3).offset(y: 7) }
                            }
                    }
                }
                .frame(height: 52, alignment: .bottom)
                .padding(.bottom, 4)
                .accessibilityHidden(true)
                HStack {
                    ForEach([0, 6, 12, 18, 24], id: \.self) { mark in
                        Text(verbatim: "\(mark)").font(.system(size: 10)).foregroundStyle(HnPalette.faint)
                        if mark != 24 { Spacer() }
                    }
                }
                .accessibilityHidden(true)
                if top.isEmpty {
                    Text("Nothing counted yet today. Unrot counts only which app is in front, never what is in it.")
                        .font(.system(size: 12)).foregroundStyle(HnPalette.faint)
                } else {
                    ForEach(Array(top.enumerated()), id: \.offset) { _, item in
                        HStack(spacing: 8) {
                            let target = RuleTarget(key: item.subject)
                            if let target { TargetBadge(target: target) }
                            Text(verbatim: target.map(RuleSummary.targetName) ?? item.subject)
                                .font(.system(size: 13)).foregroundStyle(HnPalette.ink).lineLimit(1)
                            Spacer()
                            Text(verbatim: RuleSummary.duration(minutes: Int(item.seconds / 60)))
                                .font(.system(size: 12.5)).foregroundStyle(HnPalette.muted).monospacedDigit()
                        }
                    }
                }
            }
        }
    }
}
