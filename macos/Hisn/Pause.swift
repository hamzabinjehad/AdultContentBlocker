import AppKit
import SwiftUI

/// A private pause on the Mac: one slow breath, a reminder, a question about
/// what the person needs, and today's count.
///
/// The browser extension's block page already offers this at the moment a
/// blocked page opens. The Mac window is for the other moment — an urge before
/// anyone types an address — and it follows the same rules: walking away is the
/// easiest action on the screen, nothing is delayed, and the only thing kept is
/// a day and a number. No host, no reason, no history, no network.

/// The local tally. One calendar day, one count, the time of the last pause.
public struct PauseTally: Codable, Equatable, Sendable {
    public var day: String
    public var count: Int
    public var at: Date

    /// Block pages that open this soon after the counted one are the same
    /// moment — a reload, a redirect, a second click — and do not count twice.
    public static let mergeWindow: TimeInterval = 30
    /// A ceiling that keeps a corrupted or edited store bounded.
    public static let maximum = 9_999

    public init(day: String, count: Int, at: Date) {
        self.day = day
        self.count = count
        self.at = at
    }

    public static func dayKey(for date: Date, calendar: Calendar = .current) -> String {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d",
                      parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }

    /// The tally after a pause at `now`. A value from another day, or one that
    /// is malformed, starts over at 1; one inside the merge window is returned
    /// unchanged.
    public static func recording(_ stored: PauseTally?, now: Date,
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
}

/// Where the Mac's pause tally lives. UserDefaults is enough: it is a count,
/// not evidence, and the app never reports it anywhere.
public enum PauseStore {
    public static let key = "hisn.pause.tally"

    public static func load(defaults: UserDefaults = .standard) -> PauseTally? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(PauseTally.self, from: data)
    }

    @discardableResult
    public static func record(now: Date = Date(),
                              defaults: UserDefaults = .standard) -> PauseTally {
        let tally = PauseTally.recording(load(defaults: defaults), now: now)
        if let data = try? JSONEncoder().encode(tally) {
            defaults.set(data, forKey: key)
        }
        return tally
    }
}

/// The breath the pause window guides: in for four seconds, out for six,
/// three times. A longer exhale is the calming half.
public enum PauseBreath {
    public static let inhale: TimeInterval = 4
    public static let exhale: TimeInterval = 6
    public static let cycles = 3
    public static let cycleLength = inhale + exhale
    public static let total = cycleLength * Double(cycles)

    public struct Snapshot: Equatable, Sendable {
        public let inhaling: Bool
        /// 0.55 at the bottom of the breath, 1.0 at the top.
        public let scale: Double
        /// 1-based, for a quiet "1 of 3".
        public let cycle: Int
        public let finished: Bool
        public let progress: Double
    }

    public static func snapshot(at elapsed: TimeInterval) -> Snapshot {
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
public enum PauseNeed: String, CaseIterable, Identifiable, Sendable {
    case tired, lonely, stressed, bored

    public var id: String { rawValue }

    public var title: LocalizedStringKey {
        switch self {
        case .tired:    return "Rest"
        case .lonely:   return "Company"
        case .stressed: return "Calm"
        case .bored:    return "Something to do"
        }
    }

    public var tip: LocalizedStringKey {
        switch self {
        case .tired:
            return "Put the screen away and lie down, even for ten minutes. Tiredness makes urges louder."
        case .lonely:
            return "Message or call someone you trust, even just to say hello."
        case .stressed:
            return "Step outside or wash your face, then take five slow breaths."
        case .bored:
            return "Pick one small task you have been putting off and give it ten minutes."
        }
    }
}

/// Which reminder to show: they take turns through the day, so the same words
/// do not meet the person every time. 1-based, like the extension's keys.
public func pauseReminderIndex(forCount count: Int) -> Int {
    let n = count > 0 ? count : 1
    return ((n - 1) % 5) + 1
}

public func pauseReminder(_ index: Int) -> LocalizedStringKey {
    switch index {
    case 1:
        return "An urge rises, peaks and fades, usually within minutes. You don’t have to act on it."
    case 2:
        return "You set this up on a calmer day, for a moment like this one."
    case 3:
        return "A slip doesn’t erase your progress. Walking away right now still counts."
    case 4:
        return "Ask what led here: the time, the place, the mood. That is what to change next."
    default:
        return "Stand up, change rooms, drink some water. Moving your body moves your mind."
    }
}

// MARK: - Window

/// One pause window at a time. Closing it releases it; opening it again starts
/// a fresh breath and records the new pause (merged if it is the same moment).
@MainActor
final class PauseWindowController: NSObject, NSWindowDelegate {
    static let shared = PauseWindowController()

    private var window: NSWindow?

    func show() {
        if let window, window.isVisible {
            NSApp.activate(ignoringOtherApps: true)
            window.makeKeyAndOrderFront(nil)
            return
        }
        let view = PauseView { [weak self] in self?.window?.close() }
        let window = NSWindow(contentViewController: NSHostingController(rootView: view))
        window.title = String(localized: "Take a pause…")
        window.styleMask = [.titled, .closable]
        // The pause follows the system appearance like every other surface.
        window.titlebarAppearsTransparent = true
        window.backgroundColor = .windowBackgroundColor
        window.isReleasedWhenClosed = false
        window.delegate = self
        window.center()
        window.setContentSize(NSSize(width: 460, height: 640))
        window.minSize = NSSize(width: 420, height: 560)
        self.window = window
        NSApp.activate(ignoringOtherApps: true)
        window.makeKeyAndOrderFront(nil)
    }

    func windowWillClose(_ notification: Notification) {
        if (notification.object as? NSWindow) === window { window = nil }
    }
}

// MARK: - View

/// The pause itself. The close action is always available; nothing on this
/// screen waits for a timer to let the person leave.
public struct PauseView: View {
    let onClose: () -> Void
    var defaults: UserDefaults
    var clock: () -> Date

    @State private var started: Date
    @State private var tally: PauseTally?
    @State private var selectedNeed: PauseNeed?

    public init(onClose: @escaping () -> Void,
                defaults: UserDefaults = .standard,
                clock: @escaping () -> Date = { Date() },
                startedAt: Date = Date()) {
        self.onClose = onClose
        self.defaults = defaults
        self.clock = clock
        _started = State(initialValue: startedAt)
    }

    public var body: some View {
        VStack(spacing: 16) {
            VStack(spacing: 6) {
                HnMark(size: 26)
                    .padding(.bottom, 4)
                Text("Take a moment before you go on")
                    .font(.system(size: 25, weight: .semibold, design: .serif))
                    .foregroundStyle(HnPalette.ink)
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                Text("A private pause. Nothing here is recorded except today’s count.")
                    .font(.hnCallout())
                    .foregroundStyle(HnPalette.muted)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }

            TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: false)) { _ in
                let elapsed = max(0, clock().timeIntervalSince(started))
                let breath = PauseBreath.snapshot(at: elapsed)
                if breath.finished {
                    completion
                } else {
                    breathing(breath)
                }
            }
            .frame(maxHeight: .infinity)

            footer
        }
        .padding(26)
        .frame(minWidth: 420, minHeight: 560)
        .background(HnPalette.canvas)
        .onAppear {
            guard tally == nil else { return }
            tally = PauseStore.record(now: clock(), defaults: defaults)
        }
    }

    private func breathing(_ breath: PauseBreath.Snapshot) -> some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .stroke(HnPalette.line, lineWidth: 1)
                    .frame(width: 262, height: 262)
                Circle()
                    .trim(from: 0, to: breath.progress)
                    .stroke(HnPalette.accent, style: StrokeStyle(lineWidth: 2, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .frame(width: 262, height: 262)
                    .accessibilityHidden(true)
                Circle()
                    .fill(RadialGradient(colors: [
                        HnPalette.accent.opacity(0.34),
                        HnPalette.accent.opacity(0.05),
                    ], center: .center, startRadius: 8, endRadius: 130))
                    .frame(width: 230, height: 230)
                    .scaleEffect(breath.scale)
                    .accessibilityHidden(true)
                VStack(spacing: 7) {
                    Image(systemName: "wind")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(HnPalette.accent)
                        .accessibilityHidden(true)
                    Text(breath.inhaling ? "Breathe in…" : "Breathe out slowly…")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(HnPalette.ink)
                        .multilineTextAlignment(.center)
                    HStack(spacing: 4) {
                        ForEach(0..<PauseBreath.cycles, id: \.self) { index in
                            Capsule()
                                .fill(index < breath.cycle ? HnPalette.accent : HnPalette.line)
                                .frame(width: 11, height: 2)
                        }
                    }
                    .padding(.top, 2)
                    .accessibilityHidden(true)
                }
            }
            .frame(width: 280, height: 280)
            .accessibilityElement(children: .combine)
            .accessibilityLabel(Text(breath.inhaling ? "Breathe in…" : "Breathe out slowly…"))
            .accessibilityValue(Text("\(breath.cycle) of \(PauseBreath.cycles)"))

            Text(pauseReminder(pauseReminderIndex(forCount: tally?.count ?? 1)))
                .font(.hnCallout())
                .foregroundStyle(HnPalette.muted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 10)
        }
    }

    private var completion: some View {
        VStack(spacing: 16) {
            Label("Well done. The urge passes faster than it feels.",
                  systemImage: "checkmark.circle.fill")
                .font(.system(size: 18, weight: .semibold, design: .serif))
                .foregroundStyle(HnPalette.calm)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            Text("What do you need right now?")
                .font(.hnCallout())
                .foregroundStyle(HnPalette.muted)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                ForEach(PauseNeed.allCases) { need in
                    HnButton(title: need.title,
                             kind: selectedNeed == need ? .filled : .quiet,
                             fullWidth: true) {
                        selectedNeed = need
                    }
                }
            }
            .frame(maxWidth: 360)

            if let selectedNeed {
                Text(selectedNeed.tip)
                    .font(.hnCallout())
                    .foregroundStyle(HnPalette.muted)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 8)
                    .transition(.opacity)
            }

            HnButton(title: "Breathe again", systemImage: "arrow.clockwise", kind: .text) {
                selectedNeed = nil
                started = Date()
            }
        }
        .padding(.top, 8)
    }

    private var footer: some View {
        VStack(spacing: 7) {
            HnHairline()
            if let tally {
                HStack(alignment: .firstTextBaseline) {
                    Text("Pauses today")
                        .font(.hnCaption())
                        .foregroundStyle(HnPalette.muted)
                    Spacer()
                    Text(verbatim: "\(tally.count)")
                        .font(.hnFigure(20))
                        .foregroundStyle(HnPalette.ink)
                }
            }
            Text("Each time you walk away, the habit gets weaker.")
                .font(.hnCaption())
                .foregroundStyle(HnPalette.faint)
                .multilineTextAlignment(.center)
            HnButton(title: "Close", kind: .outline) { onClose() }
                .keyboardShortcut(.cancelAction)
                .padding(.top, 2)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 4)
    }
}
