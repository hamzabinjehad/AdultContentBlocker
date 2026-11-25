import Foundation

// Unrot's pure rules, ported from Android (app.hisn.core.GlassState and the
// nativeshell Unrot files) so both phones behave the same. Nothing here touches
// storage, the clock or Screen Time; UnrotCoreTests pins every rule.

/// What the glass dome looks like right now (design/glasshouse-2026-11-23).
///
/// - Adult content tried and stopped cracks the glass for `sealWindow`.
/// - A finished Breathe inside that window seals the crack with gold, like
///   kintsugi. The gold shows for the rest of that day; the seams are kept.
/// - The glass only breaks when the person says adult content got through:
///   Unrot cannot see what it did not block. It shows for `brokenShows`.
struct GlassState: Equatable {
    static let sealWindow: TimeInterval = 10 * 60
    static let brokenShows: TimeInterval = 60 * 60

    enum Look: String, Equatable { case clear, cracked, gold, broken }

    let look: Look
    /// While cracked: how long is left to seal it. Zero otherwise.
    let sealLeft: TimeInterval

    /// Times are seconds since 1970, 0 for never. `sealedDay` and `today` are
    /// local day keys ("2026-11-23").
    static func at(now: TimeInterval, crackedAt: TimeInterval, sealedAt: TimeInterval,
                   brokenAt: TimeInterval, sealedDay: String?, today: String) -> GlassState {
        // A clock moved backwards never leaves the glass stuck in a state.
        if brokenAt > 0, brokenAt <= now, now - brokenAt < brokenShows, brokenAt >= crackedAt {
            return GlassState(look: .broken, sealLeft: 0)
        }
        let sealedThisCrack = crackedAt > 0 && sealedAt >= crackedAt
        if crackedAt > 0, crackedAt <= now, !sealedThisCrack, now - crackedAt < sealWindow {
            return GlassState(look: .cracked, sealLeft: sealWindow - (now - crackedAt))
        }
        if sealedAt > 0, sealedAt <= now, sealedDay == today { return GlassState(look: .gold, sealLeft: 0) }
        return GlassState(look: .clear, sealLeft: 0)
    }

    /// A breath at `now` seals the crack only inside the window and only once.
    static func breathSeals(now: TimeInterval, crackedAt: TimeInterval, sealedAt: TimeInterval) -> Bool {
        crackedAt > 0 && sealedAt < crackedAt && now >= crackedAt && now - crackedAt < sealWindow
    }
}

/// The sprout's seven stages, by time given back only, so it never shrinks.
enum GlassStage {
    /// Seed, Sprout, Two leaves, Young plant, Bud, Flower, Tree (minutes).
    static let thresholds = [0, 5, 15, 60, 3 * 60, 8 * 60, 24 * 60]
    static let nameKeys = ["u.stage.seed", "u.stage.sprout", "u.stage.twoLeaves", "u.stage.young",
                           "u.stage.bud", "u.stage.flower", "u.stage.tree"]
    static var last: Int { thresholds.count - 1 }

    static func stage(givenBackMinutes: Int) -> Int {
        thresholds.lastIndex { givenBackMinutes >= $0 } ?? 0
    }

    /// Minutes left to the next stage; nil at the last one.
    static func minutesToNext(givenBackMinutes: Int) -> Int? {
        let current = stage(givenBackMinutes: givenBackMinutes)
        guard current < last else { return nil }
        return thresholds[current + 1] - max(0, givenBackMinutes)
    }

    /// How far into the current step, 0...1 (1 at the last stage).
    static func part(givenBackMinutes: Int) -> Double {
        let current = stage(givenBackMinutes: givenBackMinutes)
        guard current < last else { return 1 }
        let span = Double(thresholds[current + 1] - thresholds[current])
        return min(1, max(0, Double(givenBackMinutes - thresholds[current]) / span))
    }

    /// The painted picture: one seed, one sprout for the growing stages, one tree.
    static func art(stage: Int, look: GlassState.Look, night: Bool) -> String {
        switch look {
        case .broken: return "glass_broken"
        case .cracked: return "glass_cracked"
        case .gold: return "glass_gold"
        case .clear: break
        }
        if night { return "glass_night" }
        if stage <= 0 { return "glass_seed" }
        if stage >= last { return "glass_tree" }
        return "glass_sprout"
    }
}

/// Feed time on iPhone. iOS never hands an app its usage numbers, but the
/// DeviceActivity monitor is told when the chosen apps pass a threshold. Unrot
/// registers one event per step and keeps the highest step reached each day:
/// a lower bound of feed time, to the step, that iOS itself reports.
enum FeedWatch {
    static let activity = "unrot.feed.watch"
    static let eventPrefix = "unrot.feed."
    /// Ten-minute steps to six hours, then wider ones: 40 events in all.
    static let steps: [Int] = Array(stride(from: 10, through: 360, by: 10)) + [420, 480, 600, 720]

    static func eventName(_ minutes: Int) -> String { eventPrefix + String(minutes) }

    static func minutes(event: String) -> Int? {
        guard event.hasPrefix(eventPrefix), let value = Int(event.dropFirst(eventPrefix.count)),
              steps.contains(value) else { return nil }
        return value
    }

    /// The day's record only ever rises; a callback for a lower step changes nothing.
    static func recording(_ minutes: Int, day: String, into record: [String: Int]) -> [String: Int] {
        var next = record
        next[day] = max(record[day] ?? 0, minutes)
        // Keep about two months, enough for the progress views.
        if next.count > 70 { for key in next.keys.sorted().prefix(next.count - 70) { next[key] = nil } }
        return next
    }
}

/// Time given back, the way Android counts it: for each whole day since the
/// start that has a measurement, the person's "before" minus that day's feed
/// time, never below zero. Kept days are never taken away.
enum GivenBack {
    static func gains(beforeMinutes: Int, measured: [String: Int], startDay: String, today: String) -> [String: Int] {
        guard beforeMinutes > 0 else { return [:] }
        var out: [String: Int] = [:]
        for (day, used) in measured where day >= startDay && day < today {
            out[day] = max(0, beforeMinutes - used)
        }
        return out
    }

    static func total(_ gains: [String: Int]) -> Int { gains.values.reduce(0, +) }

    /// Change against before as a whole percent (negative is less feed time).
    static func percent(before: Int, now: Int) -> Int {
        guard before > 0 else { return 0 }
        return Int((Double(now - before) * 100 / Double(before)).rounded())
    }
}

/// Days and durations as Unrot shows them.
enum UnrotFormat {
    /// "0d 0h 59m" parts for the big number.
    static func parts(minutes: Int) -> (d: Int, h: Int, m: Int) {
        let m = max(0, minutes)
        return (m / 1440, (m % 1440) / 60, m % 60)
    }

    /// "1 h 06", "45 m".
    static func short(minutes: Int) -> String {
        let m = max(0, minutes)
        return m >= 60 ? String(format: "%d h %02d", m / 60, m % 60) : "\(m) m"
    }

    /// "6:49" for a countdown.
    static func clock(seconds: TimeInterval) -> String {
        let s = max(0, Int(seconds.rounded(.up)))
        return String(format: "%d:%02d", s / 60, s % 60)
    }

    /// The local day a key names ("2026-11-23" → that midnight).
    static func date(fromKey key: String, calendar: Calendar = .current) -> Date? {
        let parts = key.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        return calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
    }

    static func dayKey(_ date: Date, calendar: Calendar = .current) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    /// Feed readings from the start day on. Days before Unrot began (left by an
    /// earlier setup, or a reset start) must not show as progress.
    static func since(_ start: Date?, _ days: [String: Int], calendar: Calendar = .current) -> [String: Int] {
        guard let start else { return days }
        let first = dayKey(start, calendar: calendar)
        return days.filter { $0.key >= first }
    }

    /// Day 1 is the start day.
    static func dayNumber(start: Date, now: Date, calendar: Calendar = .current) -> Int {
        let a = calendar.startOfDay(for: start), b = calendar.startOfDay(for: now)
        return max(1, (calendar.dateComponents([.day], from: a, to: b).day ?? 0) + 1)
    }

    /// The last `count` day keys ending today, oldest first.
    static func lastDays(_ count: Int, now: Date, calendar: Calendar = .current) -> [Date] {
        (0..<count).reversed().compactMap { calendar.date(byAdding: .day, value: -$0, to: calendar.startOfDay(for: now)) }
    }
}

/// Today's three, CleanPath's "Today's mission" with Unrot's own goals.
enum TodaysThree {
    struct Item: Equatable { let key: String; let done: Bool }

    static func items(checkedIn: Bool, pauses: Int, usedToday: Int?, beforeMinutes: Int) -> [Item] {
        [Item(key: "u.three.checkin", done: checkedIn),
         Item(key: "u.three.pause", done: pauses > 0),
         Item(key: "u.three.under", done: usedToday.map { beforeMinutes > 0 && $0 < beforeMinutes } ?? false)]
    }
}

/// The shield's state as one word on the header chip, and the dome's panes.
enum ShieldSummary {
    enum Chip: Equatable { case on, open(Int), steps(Int), night }

    static func chip(openPanes: Int, setupLeft: Int, quietNow: Bool) -> Chip {
        if setupLeft > 0 { return .steps(setupLeft) }
        if openPanes > 0 { return .open(openPanes) }
        return quietNow ? .night : .on
    }
}

/// The notes calendar: a month as weeks of days, and the journal kept newest first.
enum NotesCalendar {
    /// The month's days with leading blanks so the first lands under its weekday,
    /// starting on the calendar's first weekday. Always whole weeks.
    static func monthGrid(for date: Date, calendar: Calendar = .current) -> [Date?] {
        guard let interval = calendar.dateInterval(of: .month, for: date),
              let count = calendar.range(of: .day, in: .month, for: date)?.count else { return [] }
        let weekday = calendar.component(.weekday, from: interval.start)
        let lead = (weekday - calendar.firstWeekday + 7) % 7
        var cells: [Date?] = Array(repeating: nil, count: lead)
        for offset in 0..<count {
            cells.append(calendar.date(byAdding: .day, value: offset, to: interval.start))
        }
        while cells.count % 7 != 0 { cells.append(nil) }
        return cells
    }

    /// Weekday initials in the calendar's order.
    static func weekdaySymbols(calendar: Calendar = .current) -> [String] {
        let symbols = calendar.veryShortStandaloneWeekdaySymbols
        let first = calendar.firstWeekday - 1
        return Array(symbols[first...] + symbols[..<first])
    }

    /// Newest day first, then newest written first within a day.
    static func newestFirst(_ a: (day: String, at: Date), _ b: (day: String, at: Date)) -> Bool {
        a.day != b.day ? a.day > b.day : a.at > b.at
    }
}

/// The week's check-ins in one line: the most common pull, if any day named one.
enum MoodWeek {
    static func topPull(_ pulls: [String]) -> String? {
        let named = pulls.filter { $0 != "none" }
        let counts = Dictionary(named.map { ($0, 1) }, uniquingKeysWith: +)
        return counts.max { a, b in a.value == b.value ? a.key > b.key : a.value < b.value }?.key
    }
}

/// Unrot's own links (widgets, Shortcuts): unrot://breathe, unrot://notes, unrot://home.
enum UnrotLink: String, CaseIterable {
    case home, breathe, notes, checkin, focus
    static let scheme = "unrot"
    var url: URL { URL(string: "\(Self.scheme)://\(rawValue)")! }
    init?(url: URL) {
        guard url.scheme == Self.scheme, let host = url.host, let link = UnrotLink(rawValue: host) else { return nil }
        self = link
    }
}

/// What the Home Screen widget may show, written by the app to the App Group.
/// Growth and the glass only: no adult counts, notes or check-ins.
struct WidgetSnapshot: Codable, Equatable {
    static let key = "unrot.widget"
    var givenBack: Int
    var day: Int
    var crackedAt: TimeInterval
    var sealedAt: TimeInterval
    var brokenAt: TimeInterval
    var sealedDay: String?
    var night: Bool
    var updated: Date

    func glass(at date: Date) -> GlassState {
        GlassState.at(now: date.timeIntervalSince1970, crackedAt: crackedAt, sealedAt: sealedAt,
                      brokenAt: brokenAt, sealedDay: sealedDay, today: UnrotFormat.dayKey(date))
    }

    /// When the picture next changes on its own: a crack's window closing or a break's hour ending.
    func nextChange(after date: Date) -> Date? {
        let t = date.timeIntervalSince1970
        return [crackedAt + GlassState.sealWindow, brokenAt + GlassState.brokenShows]
            .filter { $0 > t }.min().map { Date(timeIntervalSince1970: $0 + 1) }
    }
}

/// Everything Unrot knows about one day, for the day page. Built from the
/// store's records; nothing is stored twice.
struct DaySummary: Equatable {
    let key: String
    /// Feed time Screen Time reported (lower bound, ten-minute steps); nil when not measured.
    let feed: Int?
    let before: Int
    /// Time given back that day; nil for today (counted tomorrow) or unmeasured days.
    let gain: Int?
    let mood: Int?
    let pull: String?
    let pauses: Int
    let triesStopped: Int
    let notes: [String]
    let isToday: Bool
    let isFuture: Bool
    let beforeStart: Bool

    /// Feed time against before, in minutes (negative is less); nil when unmeasured.
    var difference: Int? { feed.map { $0 - before } }

    static func make(key: String, today: String, startDay: String?, before: Int, feed: [String: Int],
                     gains: [String: Int], mood: (Int, String)?, pauses: Int, tries: Int, notes: [String]) -> DaySummary {
        DaySummary(key: key, feed: feed[key], before: before, gain: gains[key], mood: mood?.0,
                   pull: mood.map { $0.1 }.flatMap { $0 == "none" ? nil : $0 },
                   pauses: pauses, triesStopped: tries, notes: notes,
                   isToday: key == today, isFuture: key > today,
                   beforeStart: startDay.map { key < $0 } ?? false)
    }
}

/// Days for the progress chart: the last week or the last four.
enum ChartRange: Int, CaseIterable {
    case week = 7, month = 28
    var titleKey: String { self == .week ? "u.range.week" : "u.range.month" }
}

/// The four numbers under the progress chart, for a range of days.
struct RangeStats: Equatable {
    /// Average measured feed time a day; nil with no measured day.
    let average: Int?
    let underBefore: Int
    let measuredDays: Int
    /// The day key and minutes of the lightest measured day.
    let lightest: (key: String, minutes: Int)?
    let givenBack: Int

    static func == (a: RangeStats, b: RangeStats) -> Bool {
        a.average == b.average && a.underBefore == b.underBefore && a.measuredDays == b.measuredDays
            && a.lightest?.key == b.lightest?.key && a.lightest?.minutes == b.lightest?.minutes && a.givenBack == b.givenBack
    }

    /// `days` are day keys; today is left out of averages because it is not over.
    static func make(days: [String], today: String, feed: [String: Int], gains: [String: Int], before: Int) -> RangeStats {
        let whole = days.filter { $0 < today }
        let measured = whole.compactMap { key in feed[key].map { (key: key, minutes: $0) } }
        let total = measured.reduce(0) { $0 + $1.minutes }
        return RangeStats(average: measured.isEmpty ? nil : total / measured.count,
                          underBefore: before > 0 ? measured.filter { $0.minutes < before }.count : 0,
                          measuredDays: measured.count,
                          lightest: measured.min { $0.minutes == $1.minutes ? $0.key > $1.key : $0.minutes < $1.minutes },
                          givenBack: whole.reduce(0) { $0 + (gains[$1] ?? 0) })
    }
}

/// The seven stages as a path: the day each was reached, from the days' gains.
enum StagePath {
    struct Step: Equatable {
        let stage: Int
        /// Day key the running total first reached this stage; nil while ahead.
        let reached: String?
    }

    /// Stage 0 (the seed) is reached on the start day.
    static func steps(gains: [String: Int], startDay: String?) -> [Step] {
        var total = 0
        var reached: [Int: String] = [:]
        if let startDay { reached[0] = startDay }
        for day in gains.keys.sorted() {
            total += gains[day] ?? 0
            for (stage, threshold) in GlassStage.thresholds.enumerated() where stage > 0 && reached[stage] == nil && total >= threshold {
                reached[stage] = day
            }
        }
        return GlassStage.thresholds.indices.map { Step(stage: $0, reached: reached[$0]) }
    }
}

/// This week's average feed time against last week's, both over whole measured days.
struct WeekTrend: Equatable {
    let thisWeek: Int
    let lastWeek: Int
    var percent: Int { GivenBack.percent(before: lastWeek, now: thisWeek) }

    /// `days` oldest first, ending today; today is left out. Needs two measured days in each week.
    static func make(days14: [String], today: String, feed: [String: Int]) -> WeekTrend? {
        let whole = days14.filter { $0 < today }
        guard whole.count >= 8 else { return nil }
        func average(_ keys: ArraySlice<String>) -> Int? {
            let values = keys.compactMap { feed[$0] }
            return values.count >= 2 ? values.reduce(0, +) / values.count : nil
        }
        let split = whole.count - 7
        guard let now = average(whole[split...]), let before = average(whole[..<split]), before > 0 else { return nil }
        return WeekTrend(thisWeek: now, lastWeek: before)
    }
}

/// "Showing up": the day count as milestones, never reset by a heavy day.
/// The line belongs to the last milestone reached; the bar runs to the next.
enum ShowingUp {
    static let milestones = [1, 3, 7, 14, 30, 60, 90, 180, 365]

    struct Line: Equatable {
        let day: Int
        let reached: Int
        let next: Int?
        /// 0...1 from the last milestone to the next (1 past the last).
        let part: Double
        var lineKey: String { "u.showing.\(reached)" }
        var daysToNext: Int? { next.map { $0 - day } }
    }

    static func make(day: Int) -> Line {
        let d = max(1, day)
        let reached = milestones.last { $0 <= d } ?? 1
        let next = milestones.first { $0 > d }
        let part = next.map { Double(d - reached) / Double($0 - reached) } ?? 1
        return Line(day: d, reached: reached, next: next, part: part)
    }
}

/// One day's feed time against the person's own usual day, for a bar centred
/// on "before": negative is under (good), positive is over. `part` is the
/// share of half the bar, capped at 1.
struct AgainstBefore: Equatable {
    let difference: Int
    let part: Double

    static func make(feed: Int?, before: Int) -> AgainstBefore? {
        guard let feed, before > 0 else { return nil }
        let diff = feed - before
        return AgainstBefore(difference: diff, part: min(1, Double(abs(diff)) / Double(before)))
    }
}

/// Focus as a clock: what is left, as "mm:ss" (or "h:mm:ss"), and how much of
/// the session is still ahead for the ring.
enum FocusClock {
    static func remaining(until: TimeInterval, now: Date) -> Int {
        max(0, Int((until - now.timeIntervalSince1970).rounded(.up)))
    }

    static func text(seconds: Int) -> String {
        let h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%02d:%02d", m, s)
    }

    /// 1 at the start, 0 at the end.
    static func part(until: TimeInterval, lengthMinutes: Int, now: Date) -> Double {
        guard lengthMinutes > 0 else { return 0 }
        return min(1, Double(remaining(until: until, now: now)) / Double(lengthMinutes * 60))
    }

    /// The picker's steps: 5 to 180 minutes, by 5.
    static func step(_ minutes: Int, by delta: Int) -> Int { min(180, max(5, minutes + delta)) }
}

/// After a check-in: one small thing that fits what the person said, in the
/// spirit of "what do you need?" (CleanPath's best idea, without its scores).
enum CheckInNext: String, Equatable {
    case breathe, instead, rest, reachOut, gentle, keep

    static func suggest(mood: Int, pull: String) -> CheckInNext {
        switch pull {
        case "stress": return .breathe
        case "bored": return .instead
        case "tired": return .rest
        case "alone": return .reachOut
        default: return mood <= 1 ? .gentle : .keep
        }
    }

    var icon: String { Self.icons[self] ?? "sparkles" }
    private static let icons: [CheckInNext: String] = [.breathe: "wind", .instead: "lightbulb", .rest: "bed.double",
                                                       .reachOut: "message", .gentle: "heart", .keep: "sun.max"]
    var titleKey: String { "u.next.\(rawValue)" }
    var bodyKey: String { "u.next.\(rawValue).body" }
    /// The button, when there is one to press.
    var actionKey: String? { self == .keep ? nil : "u.next.\(rawValue).action" }
}

/// Last week in one look: the calendar week before this one, its numbers,
/// and how its feed time compares with the week before it.
struct WeeklyRecap: Equatable {
    let weekKey: String
    let days: [String]
    let stats: RangeStats
    let pauses: Int
    let checkIns: Int
    let topPull: String?
    /// Change in average feed time against the week before, in percent
    /// (negative is less); nil without measured days in both.
    let change: Int?

    var hasAnything: Bool { stats.measuredDays > 0 || pauses > 0 || checkIns > 0 || stats.givenBack > 0 }

    /// The seven day keys of the week before `now`'s week, and of the one before that.
    static func weeks(now: Date, calendar: Calendar = .current) -> (last: [String], before: [String])? {
        guard let thisWeek = calendar.dateInterval(of: .weekOfYear, for: now)?.start,
              let lastStart = calendar.date(byAdding: .day, value: -7, to: thisWeek),
              let beforeStart = calendar.date(byAdding: .day, value: -14, to: thisWeek) else { return nil }
        func keys(_ start: Date) -> [String] {
            (0..<7).compactMap { calendar.date(byAdding: .day, value: $0, to: start) }.map { UnrotFormat.dayKey($0, calendar: calendar) }
        }
        return (keys(lastStart), keys(beforeStart))
    }

    static func make(now: Date, feed: [String: Int], gains: [String: Int], before: Int,
                     breaths: [String: Int], pulls: [String: String], calendar: Calendar = .current) -> WeeklyRecap? {
        guard let weeks = weeks(now: now, calendar: calendar) else { return nil }
        let today = UnrotFormat.dayKey(now, calendar: calendar)
        let stats = RangeStats.make(days: weeks.last, today: today, feed: feed, gains: gains, before: before)
        let earlier = RangeStats.make(days: weeks.before, today: today, feed: feed, gains: gains, before: before)
        let change = stats.average.flatMap { now in
            earlier.average.flatMap { $0 > 0 ? Int((Double(now - $0) / Double($0) * 100).rounded()) : nil }
        }
        let weekPulls = weeks.last.compactMap { pulls[$0] }
        return WeeklyRecap(weekKey: weeks.last[0], days: weeks.last, stats: stats,
                           pauses: weeks.last.reduce(0) { $0 + (breaths[$1] ?? 0) },
                           checkIns: weekPulls.count, topPull: MoodWeek.topPull(weekPulls), change: change)
    }
}

/// When the pull comes: finished pauses by hour of day, the busiest two-hour
/// window, and the gentle reminder that lands just before it.
enum PullHours {
    static let minimumPauses = 5

    static func buckets(_ times: [TimeInterval], calendar: Calendar = .current) -> [Int] {
        var counts = Array(repeating: 0, count: 24)
        for t in times { counts[calendar.component(.hour, from: Date(timeIntervalSince1970: t))] += 1 }
        return counts
    }

    /// The first hour of the busiest two-hour window (wrapping past midnight),
    /// once there are enough pauses to say anything.
    static func peak(_ buckets: [Int]) -> Int? {
        guard buckets.count == 24, buckets.reduce(0, +) >= minimumPauses else { return nil }
        let best = (0..<24).max { a, b in
            let sa = buckets[a] + buckets[(a + 1) % 24], sb = buckets[b] + buckets[(b + 1) % 24]
            return sa == sb ? a > b : sa < sb
        }
        guard let best, buckets[best] + buckets[(best + 1) % 24] >= 3 else { return nil }
        return best
    }

}

// MARK: - The colour of the moment

/// Living colour: the app has no single colour, it takes the colour of what the
/// person is doing. One state at a time, the most urgent first, so a cracked
/// glass is never hidden behind a running Focus session. Same table as
/// Android's `GlassMood`.
enum UnrotMood: String, Equatable {
    /// Clean days, the plant is rising. Green ground.
    case growing
    /// Focus is running. Cool and still.
    case working
    /// The glass is cracked: the seal window is open. Ember.
    case hard
    /// Sealed with gold today. The lamp-lit room.
    case rest
    /// Quiet hours are on now. Indigo, low light.
    case night
    /// The glass is broken. Colour drains out until it is whole again.
    case slip

    static func of(look: GlassState.Look, focusRunning: Bool, quietNow: Bool) -> UnrotMood {
        if look == .broken { return .slip }
        if look == .cracked { return .hard }
        if quietNow { return .night }
        if focusRunning { return .working }
        if look == .gold { return .rest }
        return .growing
    }
}

// MARK: - Unrot Plus

/// What Unrot Plus sells, and what it never does. One table, so the line between
/// free and paid is decided in one place. Same numbers as Android's `PlusGate`.
///
/// Never sold: blocking adult content (every pane), Breathe and Help now, the
/// glass, the lock, quiet hours, and the clean wood with its earned trees.
enum PlusGate {
    /// Time rules a free account can keep (the adult filter is not counted).
    static let freeRules = 3
    /// Woods a free account can keep: the clean wood and one more.
    static let freeWoods = 2
    /// Days of history a free account can look back over.
    static let freeHistoryDays = 14

    enum Feature { case moreRules, moreWoods, treeKinds, seasons, woodWidget, longHistory }

    /// Whether `feature` is open. `have` is how many the person already keeps
    /// (rules or woods); it is ignored for features that are simply on or off.
    static func open(_ feature: Feature, plus: Bool, have: Int = 0) -> Bool {
        if plus { return true }
        switch feature {
        case .moreRules: return have < freeRules
        case .moreWoods: return have < freeWoods
        default: return false
        }
    }

    /// Plus is on while a subscription runs or for good after a one-time purchase.
    static func active(now: TimeInterval, paidUntil: TimeInterval, lifetime: Bool) -> Bool {
        lifetime || paidUntil > now
    }
}

// MARK: - Your wood

/// Where trees stand on the drawn hill and which kind a day grew. Pure, so
/// Android's wood and this one stay the same picture (same numbers as
/// `placeTrees` and `TreeKind` in UnrotWood.kt).
enum WoodLayout {
    struct Spot: Equatable { let x: Double; let y: Double; let scale: Double }

    /// The crest of the hill, in the picture's own 360 × 300 units.
    static func crest(_ x: Double) -> Double { 124 + 15 * pow((x - 180) / 150, 2) }

    /// Oldest days at the back, newest at the front, in one to four rows. Trees
    /// stand on the two grass banks only: the path down the middle stays clear.
    /// Positions are fractions of the picture.
    static func spots(count n: Int) -> [Spot] {
        guard n > 0 else { return [] }
        let rows = n <= 6 ? 1 : n <= 18 ? 2 : n <= 32 ? 3 : 4
        let per = max(1, Int((Double(n) / Double(rows)).rounded(.up)))
        return (0..<n).map { i in
            let r = i / per, c = i % per
            let inRow = min(per, n - r * per)
            let u = (Double(c) + 0.5 + (r % 2 == 1 ? 0.22 : -0.08)) / Double(inRow)
            let x = u < 0.5 ? 38 + u * 2 * 114 : 232 + (u - 0.5) * 2 * 92
            let y = crest(x) + (rows == 1 ? 44 : 20) + Double(r) * 33
            return Spot(x: x / 360, y: y / 300, scale: (0.74 + Double(r) * 0.13) * (n <= 8 ? 1.7 : 1))
        }
    }

    /// Kinds are earned with days: oak from day 1, pine 7, cherry 14, lemon 21, olive 30, willow 45.
    static let kinds: [(name: String, day: Int)] = [("oak", 1), ("pine", 7), ("cherry", 14), ("lemon", 21), ("olive", 30), ("willow", 45)]

    /// The kind of tree day `dayNumber` grew: one of the kinds unlocked by then, picked steadily.
    static func kind(dayNumber: Int) -> String {
        let open = kinds.filter { $0.day <= max(1, dayNumber) }
        return open[(dayNumber * 7) % open.count].name
    }
}
