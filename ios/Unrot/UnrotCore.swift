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

    static func dayKey(_ date: Date, calendar: Calendar = .current) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
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
