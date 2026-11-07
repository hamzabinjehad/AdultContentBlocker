import SwiftUI
import UIKit

/// A private pause on iPhone and iPad: one slow breath, a reminder, a
/// question about what the person needs, and today's count.
///
/// It follows the same rules as the Mac pause and the extension's block page:
/// walking away is the easiest action on the screen, nothing is delayed, and
/// the only thing kept is a day and a number. No host, no reason, no history,
/// no network. It changes no protection rule, so it stays available during a
/// commitment.

/// The local tally. One calendar day, one count, the time of the last pause.
struct PauseTally: Codable, Equatable {
    var day: String
    var count: Int
    var at: Date

    /// Opening the pause again this soon is the same moment — a rotation, a
    /// second tap, a sheet reopened — and does not count twice.
    static let mergeWindow: TimeInterval = 30
    /// A ceiling that keeps a corrupted or edited store bounded.
    static let maximum = 9_999

    static func dayKey(for date: Date, calendar: Calendar = .current) -> String {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d",
                      parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }

    /// The tally after a pause at `now`. A value from another day, or one that
    /// is malformed, starts over at 1; one inside the merge window is returned
    /// unchanged.
    static func recording(_ stored: PauseTally?, now: Date,
                          calendar: Calendar = .current) -> PauseTally {
        let day = dayKey(for: now, calendar: calendar)
        guard let stored, stored.day == day, stored.count > 0,
              stored.at.timeIntervalSince1970.isFinite else {
            return PauseTally(day: day, count: 1, at: now)
        }
        let since = now.timeIntervalSince(stored.at)
        if since >= 0 && since < mergeWindow { return stored }
        return PauseTally(day: day, count: min(stored.count + 1, maximum), at: now)
    }

    /// Today's count for the overview, or nil when the stored tally is from
    /// another day or cannot be read. A stale count is never presented as today.
    static func todayCount(_ stored: PauseTally?, now: Date,
                           calendar: Calendar = .current) -> Int? {
        guard let stored, stored.day == dayKey(for: now, calendar: calendar),
              stored.count > 0, stored.at.timeIntervalSince1970.isFinite else { return nil }
        return stored.count
    }
}

/// Where the pause tally lives. UserDefaults is enough: it is a count, not
/// evidence, and the app never reports it anywhere.
enum PauseStore {
    static let key = "hisn.pause.tally"

    static func load(defaults: UserDefaults = .standard) -> PauseTally? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(PauseTally.self, from: data)
    }

    @discardableResult
    static func record(now: Date = Date(),
                       defaults: UserDefaults = .standard) -> PauseTally {
        let tally = PauseTally.recording(load(defaults: defaults), now: now)
        if let data = try? JSONEncoder().encode(tally) {
            defaults.set(data, forKey: key)
        }
        return tally
    }

    /// The count the overview may show: today's tally only, never yesterday's.
    static func todayCount(now: Date = Date(),
                           defaults: UserDefaults = .standard) -> Int? {
        PauseTally.todayCount(load(defaults: defaults), now: now)
    }
}

/// The breath the pause guides: in for four seconds, out for six, three times.
/// A longer exhale is the calming half.
enum PauseBreath {
    static let inhale: TimeInterval = 4
    static let exhale: TimeInterval = 6
    static let cycles = 3
    static let cycleLength = inhale + exhale
    static let total = cycleLength * Double(cycles)

    struct Snapshot: Equatable {
        let inhaling: Bool
        /// 0.55 at the bottom of the breath, 1.0 at the top.
        let scale: Double
        /// 1-based, for a quiet "1 of 3".
        let cycle: Int
        let finished: Bool
        let progress: Double
    }

    static func snapshot(at elapsed: TimeInterval) -> Snapshot {
        let seconds = max(0, elapsed)
        if seconds >= total {
            return Snapshot(inhaling: false, scale: 0.55, cycle: cycles,
                            finished: true, progress: 1)
        }
        let cycle = Int(seconds / cycleLength)
        let within = seconds - Double(cycle) * cycleLength
        let inhaling = within < inhale
        let phaseProgress = inhaling ? within / inhale : (within - inhale) / exhale
        let scale = inhaling
            ? 0.55 + 0.45 * phaseProgress
            : 1.0 - 0.45 * phaseProgress
        return Snapshot(inhaling: inhaling, scale: scale, cycle: cycle + 1,
                        finished: false, progress: seconds / total)
    }
}

/// What the person says they need. The tips meet the need without the screen.
enum PauseNeed: String, CaseIterable, Identifiable {
    case tired, lonely, stressed, bored

    var id: String { rawValue }

    var title: LocalizedStringKey {
        switch self {
        case .tired:    return "pause.need.tired"
        case .lonely:   return "pause.need.lonely"
        case .stressed: return "pause.need.stressed"
        case .bored:    return "pause.need.bored"
        }
    }

    var tip: LocalizedStringKey {
        switch self {
        case .tired:    return "pause.tip.tired"
        case .lonely:   return "pause.tip.lonely"
        case .stressed: return "pause.tip.stressed"
        case .bored:    return "pause.tip.bored"
        }
    }
}

/// Which reminder to show: they take turns through the day, so the same words
/// do not meet the person every time. 1-based, like the extension's keys.
func pauseReminderIndex(forCount count: Int) -> Int {
    let n = count > 0 ? count : 1
    return ((n - 1) % 5) + 1
}

func pauseReminder(_ index: Int) -> LocalizedStringKey {
    switch index {
    case 1:  return "pause.reminder.1"
    case 2:  return "pause.reminder.2"
    case 3:  return "pause.reminder.3"
    case 4:  return "pause.reminder.4"
    default: return "pause.reminder.5"
    }
}

// MARK: - Views

/// The one positive action on the old overview, kept for the Mac-style card
/// layout. The 1.7.0 tabs use their own practice card.
struct PauseCard: View {
    var count: Int?
    let action: () -> Void
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        Button(action: action) {
            HisnCard {
                if dynamicTypeSize.isAccessibilitySize {
                    VStack(alignment: .leading, spacing: 12) {
                        HisnIconBadge(systemName: "wind", tint: .accentColor, size: 46)
                        pauseText
                    }
                } else {
                    HStack(spacing: 14) {
                        HisnIconBadge(systemName: "wind", tint: .accentColor, size: 46)
                        pauseText
                        Spacer(minLength: 8)
                        Image(systemName: "chevron.forward")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.tertiary)
                            .accessibilityHidden(true)
                    }
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text("pause.open"))
    }

    private var pauseText: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text("pause.open")
                .font(.headline)
            Text("pause.cardBody")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            if let count, count > 0 {
                Text("pause.count \(count)")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.secondary)
                    .monospacedDigit()
            }
        }
    }
}

/// The pause itself, in the same deep navy as the shield: the system screen and
/// the app agree about what a pause is. The close control is always available;
/// nothing on this screen waits for a timer to let the person leave.
struct PauseView: View {
    let onClose: () -> Void
    var defaults: UserDefaults
    var clock: () -> Date
    /// The practice history the finished breath is written to. The app group by
    /// default, so Today and Practice read the same streak.
    var ruleStore: PauseRuleStore = .appGroup

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var started: Date
    @State private var tally: PauseTally?
    @State private var selectedNeed: PauseNeed?
    @State private var practiceRecorded = false

    init(onClose: @escaping () -> Void,
         defaults: UserDefaults = .standard,
         clock: @escaping () -> Date = { Date() },
         startedAt: Date = Date(),
         ruleStore: PauseRuleStore = .appGroup) {
        self.onClose = onClose
        self.defaults = defaults
        self.clock = clock
        self.ruleStore = ruleStore
        _started = State(initialValue: startedAt)
    }

    var body: some View {
        ZStack {
            HnPalette.inkCard.ignoresSafeArea()
            RadialGradient(colors: [HnPalette.accent.opacity(0.34), .clear],
                           center: .top, startRadius: 10, endRadius: 520)
                .ignoresSafeArea()
            VStack(spacing: 0) {
                closeRow
                    .padding(.horizontal, 24)
                    .padding(.top, 10)
                GeometryReader { inner in
                    ScrollView {
                        VStack(spacing: 20) {
                            titleBlock
                            TimelineView(.animation(minimumInterval: reduceMotion ? 1 : 1.0 / 30.0,
                                                    paused: false)) { _ in
                                let elapsed = max(0, clock().timeIntervalSince(started))
                                let breath = PauseBreath.snapshot(at: elapsed)
                                Group {
                                    if breath.finished {
                                        completion
                                    } else {
                                        breathing(breath)
                                    }
                                }
                                .frame(minHeight: breath.finished ? nil : 330)
                            }
                            footer
                        }
                        .padding(.horizontal, 24)
                        .padding(.vertical, 18)
                        .frame(minHeight: inner.size.height)
                        .frame(maxWidth: 560)
                        .frame(maxWidth: .infinity)
                    }
                }
            }
        }
        .onAppear {
            guard tally == nil else { return }
            tally = PauseStore.record(now: clock(), defaults: defaults)
        }
    }

    private var titleBlock: some View {
        VStack(spacing: 6) {
            Text("pause.title")
                .font(.system(.title2, design: .rounded).weight(.bold))
                .foregroundStyle(HnPalette.onInk)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text("pause.body")
                .font(.callout)
                .foregroundStyle(HnPalette.onInkMuted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var closeRow: some View {
        HStack {
            Spacer()
            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(HnPalette.onInk)
                    .frame(width: 44, height: 44)
                    .background(Color.white.opacity(0.10), in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("pause.close"))
        }
    }

    private func breathing(_ breath: PauseBreath.Snapshot) -> some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .stroke(Color.white.opacity(0.14), lineWidth: 2)
                    .frame(width: 250, height: 250)
                Circle()
                    .fill(RadialGradient(colors: [
                        HnPalette.accent.opacity(0.55),
                        HnPalette.accent.opacity(0.12),
                    ], center: .center, startRadius: 8, endRadius: 125))
                    .frame(width: 236, height: 236)
                    .scaleEffect(breath.scale)
                    .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: breath.scale)
                    .accessibilityHidden(true)
                VStack(spacing: 8) {
                    HnMark(size: 30, tint: .white)
                    Text(breathLabel(breath.inhaling))
                        .font(.hnHeadline())
                        .foregroundStyle(HnPalette.onInk)
                        .multilineTextAlignment(.center)
                }
            }
            .frame(width: 260, height: 260)
            .accessibilityElement(children: .combine)
            .accessibilityLabel(Text(breathLabel(breath.inhaling)))

            ProgressView(value: breath.progress)
                .progressViewStyle(.linear)
                .tint(HnPalette.accent)
                .background(Color.white.opacity(0.14))
                .frame(maxWidth: 260)
                .accessibilityLabel(Text("pause.progress"))

            Text(pauseReminder(pauseReminderIndex(forCount: tally?.count ?? 1)))
                .font(.callout)
                .foregroundStyle(HnPalette.onInkMuted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 8)
        }
    }

    private var completion: some View {
        VStack(spacing: 16) {
            HStack(spacing: 8) {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(HnPalette.calm)
                    .accessibilityHidden(true)
                Text("pause.done")
                    .font(.hnHeadline())
                    .foregroundStyle(HnPalette.onInk)
            }
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)

            Text("pause.question")
                .font(.callout)
                .foregroundStyle(HnPalette.onInkMuted)

            LazyVGrid(columns: columns, spacing: 10) {
                ForEach(PauseNeed.allCases) { need in
                    Button {
                        selectedNeed = need
                    } label: {
                        Text(need.title)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(selectedNeed == need ? .white : HnPalette.onInk)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 11)
                            .background(selectedNeed == need ? HnPalette.accent
                                                            : Color.white.opacity(0.08),
                                        in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(maxWidth: 360)

            if let selectedNeed {
                Text(selectedNeed.tip)
                    .font(.callout)
                    .foregroundStyle(HnPalette.onInkMuted)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 8)
                    .transition(.opacity)
            }

            Button {
                selectedNeed = nil
                started = clock()
            } label: {
                Text("pause.again")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(HnPalette.onInkAccent)
            }
            .buttonStyle(.plain)
        }
        .padding(.top, 8)
        .onAppear(perform: recordPracticeOnce)
    }

    /// The breath finishing is the practice. It is recorded once per view, so
    /// replaying the completion state does not add a second day.
    private func recordPracticeOnce() {
        guard !practiceRecorded else { return }
        practiceRecorded = true
        ruleStore.recordPractice(now: clock())
    }

    private var columns: [GridItem] {
        dynamicTypeSize.isAccessibilitySize
            ? [GridItem(.flexible())]
            : [GridItem(.flexible()), GridItem(.flexible())]
    }

    private func breathLabel(_ inhaling: Bool) -> LocalizedStringKey {
        inhaling ? "pause.inhale" : "pause.exhale"
    }

    private var footer: some View {
        VStack(spacing: 6) {
            if let tally {
                Text("pause.count \(tally.count)")
                    .font(.callout.weight(.medium))
                    .foregroundStyle(HnPalette.onInk)
                    .monospacedDigit()
            }
            Text("pause.footer")
                .font(.caption)
                .foregroundStyle(HnPalette.onInkMuted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }
}
