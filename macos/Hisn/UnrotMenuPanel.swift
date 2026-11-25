import AppKit
import SwiftUI

/// What the menu bar panel shows, gathered once so it can be tested without a
/// status item: today's feed time against the person's usual day, what is
/// running now, and the last seven days.
struct MenuPanelSummary: Equatable {
    struct Row: Equatable, Identifiable {
        var id: UUID
        var emoji: String
        var name: String
        var line: String
        /// How much of the limit or window is used, when the rule has one.
        var fraction: Double?
    }

    struct Day: Equatable, Identifiable {
        var id: String
        var letter: String
        /// Share of the usual day, 0…1. nil: no record for that day.
        var share: Double?
        var isToday: Bool
    }

    var feedSeconds: Double
    var usualSeconds: Double
    var hasRules: Bool
    var givenBack: String
    var stage: Int
    var focus: (intent: String, until: Date)?
    var rows: [Row]
    var moreRunning: Int
    var days: [Day]

    /// The ring: feed time today as a share of the usual day.
    var ring: Double { usualSeconds > 0 ? min(1, feedSeconds / usualSeconds) : 0 }

    static func == (a: MenuPanelSummary, b: MenuPanelSummary) -> Bool {
        a.feedSeconds == b.feedSeconds && a.usualSeconds == b.usualSeconds && a.hasRules == b.hasRules
            && a.givenBack == b.givenBack && a.stage == b.stage && a.rows == b.rows
            && a.moreRunning == b.moreRunning && a.days == b.days
            && a.focus?.intent == b.focus?.intent && a.focus?.until == b.focus?.until
    }

    static let maxRows = 3

    init(book: RuleBook, now: Date, calendar: Calendar) {
        feedSeconds = book.rules.isEmpty ? 0 : book.feedSecondsToday()
        usualSeconds = book.beforePerDay
        hasRules = !book.rules.isEmpty
        let sprout = SproutProgress(givenBack: book.givenBackSeconds(at: now, calendar: calendar))
        givenBack = SproutProgress.parts(sprout.givenBack).map { $0.0 + $0.1 }.joined(separator: " ")
        stage = sprout.stage
        focus = book.focus.flatMap { $0.isRunning(at: now) ? ($0.intent, $0.until) : nil }

        let running = book.rules.filter { $0.enabled && $0.isRunning(at: now, calendar: calendar) }
        rows = running.prefix(Self.maxRows).map { rule in
            var line = RuleSummary.detail(rule, day: book.day)
            var fraction: Double?
            if case .dailyLimit(let minutes) = rule.action {
                fraction = min(1, (book.day.usedSeconds[rule.id] ?? 0) / Double(max(1, minutes) * 60))
            } else if let interval = rule.window?.interval(containing: now, calendar: calendar), interval.duration > 0 {
                fraction = min(1, max(0, now.timeIntervalSince(interval.start) / interval.duration))
                if case .block = rule.action {
                    line = String(localized: "Closed until \(DistractionPanelModel.time(interval.end, now: now))")
                }
            }
            return Row(id: rule.id, emoji: rule.emoji ?? "⏳", name: rule.name, line: line, fraction: fraction)
        }
        moreRunning = max(0, running.count - Self.maxRows)

        let week = ProgressSummary(book: book, now: now, calendar: calendar)
        let symbols = calendar.veryShortStandaloneWeekdaySymbols
        days = week.days.map { day in
            let weekday = calendar.component(.weekday, from: day.date)
            return Day(id: day.key, letter: symbols[(weekday - 1) % symbols.count],
                       share: day.feedSeconds.map { book.beforePerDay > 0 ? min(1, $0 / book.beforePerDay) : 0 },
                       isToday: day.isToday)
        }
    }
}

/// Unrot's menu bar panel: the day at a glance and the three things worth
/// doing from anywhere. The full command menu stays one click away.
struct UnrotMenuPanel: View {
    let summary: MenuPanelSummary
    let protection: String
    let now: Date
    var onFocus: () -> Void = {}
    var onBreathe: () -> Void = {}
    var onOpen: () -> Void = {}
    var onMore: () -> Void = {}

    static let width: CGFloat = 340

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            header
            nowSection
            weekSection
            actions
            footer
        }
        .padding(16)
        .frame(width: Self.width)
        .background(HnPalette.canvas)
        .environment(\.colorScheme, .dark)
    }

    private var header: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle().stroke(Color.white.opacity(0.1), lineWidth: 7)
                Circle().trim(from: 0, to: summary.ring)
                    .stroke(AngularGradient(colors: [HnPalette.accent.opacity(0.55), HnPalette.accent], center: .center),
                            style: StrokeStyle(lineWidth: 7, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                VStack(spacing: 0) {
                    Text(verbatim: RuleSummary.duration(minutes: Int(summary.feedSeconds / 60)))
                        .font(.system(size: 17, weight: .semibold, design: .rounded)).foregroundStyle(HnPalette.ink)
                        .monospacedDigit().minimumScaleFactor(0.7).lineLimit(1)
                    Text("in feeds").font(.system(size: 10)).foregroundStyle(HnPalette.muted)
                }
                .padding(.horizontal, 10)
            }
            .frame(width: 84, height: 84)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text("\(RuleSummary.duration(minutes: Int(summary.feedSeconds / 60))) in feeds today"))
            VStack(alignment: .leading, spacing: 3) {
                HStack(alignment: .firstTextBaseline, spacing: 5) {
                    Text(verbatim: summary.givenBack)
                        .font(.system(size: 24, weight: .semibold, design: .rounded)).foregroundStyle(HnPalette.accentText)
                        .monospacedDigit()
                    Text("given back").font(.system(size: 12)).foregroundStyle(HnPalette.muted)
                }
                Text(verbatim: SproutProgress.name(summary.stage))
                    .font(.system(size: 13, weight: .medium)).foregroundStyle(HnPalette.ink)
                Text(summary.hasRules
                     ? "Your usual day: \(RuleSummary.duration(minutes: Int(summary.usualSeconds / 60)))"
                     : "No rules yet. The ring fills once one runs.")
                    .font(.system(size: 11.5)).foregroundStyle(HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
    }

    private func label(_ title: LocalizedStringKey) -> some View {
        Text(title).font(.system(size: 12, weight: .medium)).foregroundStyle(HnPalette.muted)
            .accessibilityAddTraits(.isHeader)
    }

    private var nowSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            label("Now")
            if let focus = summary.focus {
                row(emoji: "🎯", name: focus.intent.isEmpty ? String(localized: "Focus") : focus.intent,
                    line: String(localized: "Focus until \(DistractionPanelModel.time(focus.until, now: now))"),
                    trailing: UnrotHomePage.countdown(focus.until.timeIntervalSince(now)), fraction: nil)
            }
            ForEach(summary.rows) { item in
                row(emoji: item.emoji, name: item.name, line: item.line, trailing: nil, fraction: item.fraction)
            }
            if summary.rows.isEmpty && summary.focus == nil {
                Text("Nothing is closed right now.").font(.system(size: 12.5)).foregroundStyle(HnPalette.muted)
                    .padding(.vertical, 4)
            }
            if summary.moreRunning > 0 {
                Text("+\(summary.moreRunning) more").font(.system(size: 11.5)).foregroundStyle(HnPalette.faint)
            }
        }
    }

    private func row(emoji: String, name: String, line: String, trailing: String?, fraction: Double?) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Text(verbatim: emoji).font(.system(size: 20)).frame(width: 26).accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 1) {
                    Text(verbatim: name).font(.system(size: 13, weight: .medium)).foregroundStyle(HnPalette.ink).lineLimit(1)
                    Text(verbatim: line).font(.system(size: 11.5)).foregroundStyle(HnPalette.muted).lineLimit(1)
                        .monospacedDigit()
                }
                Spacer(minLength: 6)
                if let trailing {
                    Text(verbatim: trailing).font(.system(size: 14, weight: .semibold, design: .rounded))
                        .foregroundStyle(HnPalette.accentText).monospacedDigit()
                }
            }
            .padding(.horizontal, 10).padding(.vertical, 8)
            if let fraction {
                GeometryReader { proxy in
                    ZStack(alignment: .leading) {
                        Rectangle().fill(Color.white.opacity(0.06))
                        Rectangle().fill(HnPalette.accent).frame(width: proxy.size.width * fraction)
                    }
                }
                .frame(height: 2).accessibilityHidden(true)
            }
        }
        .background(Color.white.opacity(0.06))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    private var weekSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            label("Last 7 days")
            HStack(alignment: .bottom, spacing: 8) {
                ForEach(summary.days) { day in
                    VStack(spacing: 4) {
                        ZStack(alignment: .bottom) {
                            RoundedRectangle(cornerRadius: 4, style: .continuous).fill(Color.white.opacity(0.06))
                            if let share = day.share {
                                RoundedRectangle(cornerRadius: 4, style: .continuous)
                                    .fill(day.isToday ? HnPalette.accent : HnPalette.accent.opacity(0.45))
                                    .frame(height: max(3, 40 * share))
                            }
                        }
                        .frame(height: 40)
                        Text(verbatim: day.letter).font(.system(size: 10, weight: day.isToday ? .bold : .regular))
                            .foregroundStyle(day.isToday ? HnPalette.ink : HnPalette.faint)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .accessibilityHidden(true)
            Text("A full bar is your usual day. Shorter is time given back.")
                .font(.system(size: 11)).foregroundStyle(HnPalette.faint)
        }
    }

    private var actions: some View {
        HStack(spacing: 8) {
            Button(action: onFocus) {
                Label(summary.focus == nil ? "Focus 25 m" : "Focus is on", systemImage: "play.fill")
                    .font(.system(size: 13, weight: .semibold)).foregroundStyle(Color(red: 0.16, green: 0.11, blue: 0))
                    .frame(maxWidth: .infinity).frame(height: 34)
                    .background(Capsule().fill(HnPalette.accent))
            }
            .buttonStyle(.plain)
            .disabled(summary.focus != nil)
            .opacity(summary.focus == nil ? 1 : 0.5)
            Button(action: onBreathe) {
                Label("Breathe", systemImage: "wind")
                    .font(.system(size: 13, weight: .semibold)).foregroundStyle(HnPalette.ink)
                    .frame(maxWidth: .infinity).frame(height: 34)
                    .background(Capsule().fill(Color.white.opacity(0.1)))
            }
            .buttonStyle(.plain)
        }
    }

    private var footer: some View {
        HStack(spacing: 8) {
            Circle().fill(HnPalette.calm).frame(width: 6, height: 6).accessibilityHidden(true)
            Text(verbatim: protection).font(.system(size: 11.5)).foregroundStyle(HnPalette.muted).lineLimit(1)
            Spacer(minLength: 8)
            Button(action: onOpen) {
                Text("Open").font(.system(size: 12, weight: .medium)).foregroundStyle(HnPalette.ink)
                    .padding(.horizontal, 10).frame(height: 24).background(Capsule().fill(Color.white.opacity(0.1)))
            }
            .buttonStyle(.plain)
            Button(action: onMore) {
                Image(systemName: "ellipsis").font(.system(size: 12, weight: .bold)).foregroundStyle(HnPalette.ink)
                    .frame(width: 28, height: 24).background(Capsule().fill(Color.white.opacity(0.1)))
            }
            .buttonStyle(.plain)
            .help("Lock, block a site and more")
            .accessibilityLabel("More")
        }
    }
}
