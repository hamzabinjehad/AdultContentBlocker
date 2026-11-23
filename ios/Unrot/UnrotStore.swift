import Foundation
import SwiftUI

/// Everything Unrot remembers about the person, on this iPhone only. Kept in
/// the App Group so the DeviceActivity monitor can add the day's feed time.
/// Nothing here leaves the phone; adult numbers never go on a share card.
@MainActor
final class UnrotStore: ObservableObject {
    struct Answers: Codable, Equatable {
        var name = ""
        /// What they want less of: "short", "feeds", "phone", "adult".
        var lessOf: [String] = []
        /// Their own usual feed time a day before Unrot, in minutes.
        var beforeMinutes = 0
        /// When it gets them: "morning", "work", "evening", "bed".
        var when: [String] = []
        /// How it leaves them: "tired", "behind", "low", "empty".
        var feel: [String] = []
        var reason = ""
        /// Instead ideas: "sleep", "read", "train", "friends", "study", "make", "outside", "family".
        var instead: [String] = []
    }

    struct CheckIn: Codable, Equatable { var mood: Int; var pull: String }

    /// One note in the journal, on the day it was written for.
    struct Note: Codable, Equatable, Identifiable {
        var id = UUID()
        var text: String
        /// Local day key ("2026-11-23").
        var day: String
        var at: Date
    }

    @Published private(set) var onboarded: Bool
    @Published private(set) var startedAt: Date?
    @Published private(set) var answers: Answers
    @Published private(set) var crackedAt: TimeInterval
    @Published private(set) var sealedAt: TimeInterval
    @Published private(set) var sealedDay: String?
    @Published private(set) var brokenAt: TimeInterval
    @Published private(set) var seams: Int
    @Published private(set) var feed: [String: Int]
    @Published private(set) var checkIns: [String: CheckIn]
    /// Every note, newest first.
    @Published private(set) var journal: [Note]
    /// The words alone, newest first (Home and Breathe show the first).
    var notes: [String] { journal.map(\.text) }
    @Published private(set) var breaths: [String: Int]
    @Published private(set) var blockedTries: [String: Int]
    @Published private(set) var focusUntil: TimeInterval
    @Published private(set) var focusMinutes: Int
    @Published private(set) var tourSeen: Bool

    private let defaults: UserDefaults

    enum Key {
        static let onboarded = "unrot.onboarded"
        static let started = "unrot.startedAt"
        static let answers = "unrot.answers"
        static let cracked = "unrot.glass.cracked"
        static let sealed = "unrot.glass.sealed"
        static let sealedDay = "unrot.glass.sealedDay"
        static let broken = "unrot.glass.broken"
        static let seams = "unrot.glass.seams"
        /// Written by the monitor: day key -> highest feed step reached.
        static let feed = "unrot.feed.days"
        static let checkIns = "unrot.checkins"
        /// Before the calendar: plain strings, carried into the journal once.
        static let notes = "unrot.notes"
        static let journal = "unrot.journal"
        static let breaths = "unrot.breaths"
        static let tries = "unrot.tries"
        static let focusUntil = "unrot.focus.until"
        static let focusMinutes = "unrot.focus.minutes"
        static let tour = "unrot.tour"
    }

    /// Tests get a throwaway suite; the app the shared App Group.
    static func appGroup() -> UnrotStore {
        if NSClassFromString("XCTestCase") != nil {
            let name = "unrot.tests.\(UUID().uuidString)"
            return UnrotStore(defaults: UserDefaults(suiteName: name) ?? .standard)
        }
        return UnrotStore(defaults: UserDefaults(suiteName: Edition.appGroup) ?? .standard)
    }

    init(defaults: UserDefaults) {
        self.defaults = defaults
        onboarded = defaults.bool(forKey: Key.onboarded)
        startedAt = defaults.object(forKey: Key.started) as? Date
        answers = Self.decode(Answers.self, defaults.data(forKey: Key.answers)) ?? Answers()
        crackedAt = defaults.double(forKey: Key.cracked)
        sealedAt = defaults.double(forKey: Key.sealed)
        sealedDay = defaults.string(forKey: Key.sealedDay)
        brokenAt = defaults.double(forKey: Key.broken)
        seams = defaults.integer(forKey: Key.seams)
        feed = defaults.dictionary(forKey: Key.feed) as? [String: Int] ?? [:]
        checkIns = Self.decode([String: CheckIn].self, defaults.data(forKey: Key.checkIns)) ?? [:]
        var saved = Self.decode([Note].self, defaults.data(forKey: Key.journal)) ?? []
        if let old = defaults.stringArray(forKey: Key.notes), !old.isEmpty {
            let now = Date()
            saved += old.map { Note(text: $0, day: UnrotFormat.dayKey(now), at: now) }
            if let data = try? JSONEncoder().encode(saved) { defaults.set(data, forKey: Key.journal) }
            defaults.removeObject(forKey: Key.notes)
        }
        journal = saved
        breaths = defaults.dictionary(forKey: Key.breaths) as? [String: Int] ?? [:]
        blockedTries = defaults.dictionary(forKey: Key.tries) as? [String: Int] ?? [:]
        focusUntil = defaults.double(forKey: Key.focusUntil)
        focusMinutes = defaults.integer(forKey: Key.focusMinutes)
        tourSeen = defaults.bool(forKey: Key.tour)
        applyLaunchArguments()
    }

    /// Re-reads what the monitor extension may have written while the app slept.
    func reload() {
        feed = defaults.dictionary(forKey: Key.feed) as? [String: Int] ?? [:]
    }

    // MARK: - Derived

    func today(_ now: Date = Date()) -> String { UnrotFormat.dayKey(now) }

    func day(_ now: Date = Date()) -> Int {
        startedAt.map { UnrotFormat.dayNumber(start: $0, now: now) } ?? 1
    }

    var gains: [String: Int] {
        guard let startedAt else { return [:] }
        return GivenBack.gains(beforeMinutes: answers.beforeMinutes, measured: feed,
                               startDay: UnrotFormat.dayKey(startedAt), today: today())
    }

    var givenBackMinutes: Int { GivenBack.total(gains) }

    func yesterdayGain(_ now: Date = Date()) -> Int? {
        guard let yesterday = Calendar.current.date(byAdding: .day, value: -1, to: now) else { return nil }
        return gains[UnrotFormat.dayKey(yesterday)]
    }

    func glass(now: Date = Date()) -> GlassState {
        GlassState.at(now: now.timeIntervalSince1970, crackedAt: crackedAt, sealedAt: sealedAt,
                      brokenAt: brokenAt, sealedDay: sealedDay, today: today(now))
    }

    func breathsToday(_ now: Date = Date()) -> Int { breaths[today(now)] ?? 0 }
    func triesThisWeek(_ now: Date = Date()) -> Int {
        UnrotFormat.lastDays(7, now: now).reduce(0) { $0 + (blockedTries[UnrotFormat.dayKey($1)] ?? 0) }
    }
    var checkedInToday: CheckIn? { checkIns[today()] }
    var focusActive: Bool { focusUntil > Date().timeIntervalSince1970 }

    // MARK: - Changes

    func finishOnboarding(_ next: Answers, now: Date = Date()) {
        answers = next
        save(next, Key.answers)
        startedAt = startedAt ?? now
        defaults.set(startedAt, forKey: Key.started)
        onboarded = true
        defaults.set(true, forKey: Key.onboarded)
    }

    func update(_ change: (inout Answers) -> Void) {
        var next = answers
        change(&next)
        answers = next
        save(next, Key.answers)
    }

    /// Adult content tried and stopped: the glass cracks.
    func crack(now: Date = Date()) {
        crackedAt = now.timeIntervalSince1970
        defaults.set(crackedAt, forKey: Key.cracked)
        let key = today(now)
        blockedTries[key, default: 0] += 1
        defaults.set(blockedTries, forKey: Key.tries)
    }

    /// Adult content got through: the glass breaks. Time given back is kept.
    func breakGlass(now: Date = Date()) {
        brokenAt = now.timeIntervalSince1970
        defaults.set(brokenAt, forKey: Key.broken)
    }

    /// A finished Breathe: counts the pause and seals a fresh crack with gold.
    /// Returns true when it sealed one.
    @discardableResult
    func finishedBreath(now: Date = Date()) -> Bool {
        let key = today(now)
        breaths[key, default: 0] += 1
        defaults.set(breaths, forKey: Key.breaths)
        PauseStore.record(now: now)
        let t = now.timeIntervalSince1970
        guard GlassState.breathSeals(now: t, crackedAt: crackedAt, sealedAt: sealedAt) else { return false }
        sealedAt = t
        sealedDay = key
        seams += 1
        defaults.set(sealedAt, forKey: Key.sealed)
        defaults.set(key, forKey: Key.sealedDay)
        defaults.set(seams, forKey: Key.seams)
        return true
    }

    func checkIn(mood: Int, pull: String, now: Date = Date()) {
        checkIns[today(now)] = CheckIn(mood: mood, pull: pull)
        save(checkIns, Key.checkIns)
    }

    /// Adds a note for a day (today by default). Never for a day still to come.
    func addNote(_ text: String, on day: Date = Date(), now: Date = Date()) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let key = UnrotFormat.dayKey(day)
        guard !trimmed.isEmpty, key <= UnrotFormat.dayKey(now) else { return }
        journal = (journal + [Note(text: trimmed, day: key, at: now)])
            .sorted { NotesCalendar.newestFirst(($0.day, $0.at), ($1.day, $1.at)) }
        save(journal, Key.journal)
    }

    func removeNote(id: UUID) {
        journal.removeAll { $0.id == id }
        save(journal, Key.journal)
    }

    func notes(on day: Date) -> [Note] {
        let key = UnrotFormat.dayKey(day)
        return journal.filter { $0.day == key }
    }

    var noteDays: Set<String> { Set(journal.map(\.day)) }

    func startedFocus(until: Date, minutes: Int) {
        focusUntil = until.timeIntervalSince1970
        focusMinutes += minutes
        defaults.set(focusUntil, forKey: Key.focusUntil)
        defaults.set(focusMinutes, forKey: Key.focusMinutes)
    }

    func endedFocus(now: Date = Date()) {
        // Minutes not used are not counted.
        let left = max(0, Int((focusUntil - now.timeIntervalSince1970) / 60))
        focusMinutes = max(0, focusMinutes - left)
        focusUntil = 0
        defaults.set(focusUntil, forKey: Key.focusUntil)
        defaults.set(focusMinutes, forKey: Key.focusMinutes)
    }

    func markTourSeen() {
        tourSeen = true
        defaults.set(true, forKey: Key.tour)
    }

    /// Starts over from the welcome. Time given back and the garden are kept.
    func replayOnboarding() {
        onboarded = false
        defaults.set(false, forKey: Key.onboarded)
    }

    // MARK: - Storage

    private func save<T: Encodable>(_ value: T, _ key: String) {
        if let data = try? JSONEncoder().encode(value) { defaults.set(data, forKey: key) }
    }

    private static func decode<T: Decodable>(_ type: T.Type, _ data: Data?) -> T? {
        data.flatMap { try? JSONDecoder().decode(type, from: $0) }
    }

    /// Screenshot and UI-test states: `-unrot-demo <state>`. Debug builds only.
    private func applyLaunchArguments() {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "-unrot-demo"), args.indices.contains(i + 1) else {
            if args.contains("-unrot-fresh") { onboarded = false }
            return
        }
        let state = args[i + 1]
        let now = Date()
        let cal = Calendar.current
        answers = Answers(name: "Sam", lessOf: ["short", "feeds", "adult"], beforeMinutes: 225,
                          when: ["evening", "bed"], feel: ["tired", "behind"],
                          reason: "I want my evenings back for reading and sleep.",
                          instead: ["read", "sleep", "outside"])
        onboarded = true
        tourSeen = true
        startedAt = cal.date(byAdding: .day, value: -8, to: now)
        var demoFeed: [String: Int] = [:]
        for (offset, used) in [190, 170, 150, 140, 120, 130, 100, 90].enumerated() {
            if let d = cal.date(byAdding: .day, value: -(8 - offset), to: now) { demoFeed[UnrotFormat.dayKey(d)] = used }
        }
        demoFeed[today(now)] = 40
        feed = demoFeed
        defaults.set(demoFeed, forKey: Key.feed)
        breaths = [today(now): 2]
        blockedTries = [today(now): 1]
        checkIns = [:]
        let cal2 = Calendar.current
        journal = [(0, "You'll be tired tomorrow."), (2, "Ten pages instead. It worked."),
                   (5, "Evenings are the hard part.")].map { offset, text in
            let d = cal2.date(byAdding: .day, value: -offset, to: now) ?? now
            return Note(text: text, day: UnrotFormat.dayKey(d), at: d)
        }
        checkIns = [UnrotFormat.dayKey(cal2.date(byAdding: .day, value: -1, to: now) ?? now): CheckIn(mood: 3, pull: "tired"),
                    UnrotFormat.dayKey(cal2.date(byAdding: .day, value: -3, to: now) ?? now): CheckIn(mood: 1, pull: "bored")]
        crackedAt = 0; sealedAt = 0; brokenAt = 0; sealedDay = nil
        switch state {
        case "cracked": crackedAt = now.timeIntervalSince1970 - 191
        case "gold": crackedAt = now.timeIntervalSince1970 - 400; sealedAt = now.timeIntervalSince1970 - 100; sealedDay = today(now); seams = 1
        case "broken": brokenAt = now.timeIntervalSince1970 - 60
        case "new": startedAt = now; feed = [:]; breaths = [:]; blockedTries = [:]; journal = []; checkIns = [:]
        case "onboarding": onboarded = false
        default: break
        }
        #endif
    }
}
