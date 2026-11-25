import AppKit
import SwiftUI

/// The breath Unrot guides, the same two patterns as the Android app: a
/// physiological sigh (in, a short top-up, a long out) and an even 4·6.
/// Every breath is ten seconds, so the rounds alone set the length.
public enum BreathPattern: String, CaseIterable, Identifiable, Sendable {
    case sigh, even

    public enum Phase: Equatable, Sendable { case breatheIn, topUp, out }

    /// The flower's size between breaths.
    public static let rest = 0.55
    public static let defaultRounds = 3
    public static let roundChoices = [3, 5, 10]
    public static let countdown: TimeInterval = 3

    public var id: String { rawValue }

    public static func fromKey(_ key: String?) -> BreathPattern {
        key.flatMap(BreathPattern.init(rawValue:)) ?? .sigh
    }

    public var phases: [Phase] { self == .sigh ? [.breatheIn, .topUp, .out] : [.breatheIn, .out] }

    public func duration(_ index: Int) -> TimeInterval {
        self == .sigh ? [3, 1, 6][index] : [4, 6][index]
    }

    public var breathLength: TimeInterval { 10 }

    public func ringTarget(_ phase: Phase) -> Double {
        switch phase {
        case .breatheIn: return self == .sigh ? 0.86 : 1
        case .topUp: return 1
        case .out: return Self.rest
        }
    }

    public struct Position: Equatable, Sendable {
        /// 0-based.
        public let breath: Int
        public let index: Int
        public let phase: Phase
        public let done: TimeInterval
        public let length: TimeInterval
        public let finished: Bool
        /// The flower: eased from where the last step left it to this step's size.
        public let scale: Double
        /// 0…1 over the whole session.
        public let progress: Double

        /// A partial second counts as one, so the count never shows 0 mid-step.
        public var secondsLeft: Int { finished ? 0 : max(0, Int((length - done).rounded(.up))) }
    }

    public func position(at elapsed: TimeInterval, rounds: Int) -> Position {
        let rounds = max(1, rounds)
        let total = breathLength * Double(rounds)
        let clamped = min(max(0, elapsed), total)
        if clamped >= total {
            let last = phases.count - 1
            return Position(breath: rounds - 1, index: last, phase: phases[last], done: duration(last),
                            length: duration(last), finished: true, scale: Self.rest, progress: 1)
        }
        let breath = min(Int(clamped / breathLength), rounds - 1)
        var within = clamped - Double(breath) * breathLength
        var index = 0
        while index < phases.count - 1, within >= duration(index) {
            within -= duration(index)
            index += 1
        }
        let from = index == 0 ? Self.rest : ringTarget(phases[index - 1])
        let to = ringTarget(phases[index])
        let t = min(1, max(0, within / duration(index)))
        let eased = t * t * (3 - 2 * t)
        return Position(breath: breath, index: index, phase: phases[index], done: within,
                        length: duration(index), finished: false,
                        scale: from + (to - from) * eased, progress: clamped / total)
    }

    /// "30 sec", "1 min", "1 min 40 sec".
    public static func lengthText(rounds: Int) -> String {
        let seconds = 10 * rounds
        if seconds < 60 { return String(localized: "\(seconds) sec") }
        if seconds % 60 == 0 { return String(localized: "\(seconds / 60) min") }
        return String(localized: "\(seconds / 60) min \(seconds % 60) sec")
    }
}

/// What to do instead, offered once the breath is over.
enum BreatheAlternative: String, CaseIterable, Identifiable {
    case walk, water, text, read
    var id: String { rawValue }
    var emoji: String {
        switch self {
        case .walk: return "🚶"
        case .water: return "💧"
        case .text: return "💬"
        case .read: return "📖"
        }
    }
    var title: LocalizedStringKey {
        switch self {
        case .walk: return "Walk for 5 minutes"
        case .water: return "Drink a glass of water"
        case .text: return "Text someone you like"
        case .read: return "Read 10 pages"
        }
    }
}

/// Unrot's Breathe, the Android flow on a Mac window: choose, 3 · 2 · 1, the
/// gold flower that opens on the in-breath, then what to do next. Closing is
/// always one key away (Esc or the window's own button); nothing waits on a timer.
struct UnrotBreatheView: View {
    enum Stage: Equatable { case start, ready, breathing, done }

    static let patternKey = "unrot.breathe.pattern"
    static let roundsKey = "unrot.breathe.rounds"
    static let night = Color(red: 0x0E / 255, green: 0x0D / 255, blue: 0x0B / 255)
    private static let gold = Color(red: 0xF0 / 255, green: 0xB9 / 255, blue: 0x3C / 255)
    private static let goldInk = Color(red: 0x2A / 255, green: 0x1D / 255, blue: 0x00 / 255)
    private static let cream = Color(red: 0.96, green: 0.94, blue: 0.89)

    let onClose: () -> Void
    var defaults: UserDefaults
    var clock: () -> Date

    @State private var stage: Stage
    @State private var stageStarted: Date
    @State private var pattern: BreathPattern
    @State private var rounds: Int
    @State private var tally: PauseTally?
    @State private var chosen: BreatheAlternative?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(onClose: @escaping () -> Void, defaults: UserDefaults = .standard,
         clock: @escaping () -> Date = { Date() }, stage: Stage = .start, stageStarted: Date? = nil) {
        self.onClose = onClose
        self.defaults = defaults
        self.clock = clock
        _stage = State(initialValue: stage)
        _stageStarted = State(initialValue: stageStarted ?? clock())
        _pattern = State(initialValue: BreathPattern.fromKey(defaults.string(forKey: Self.patternKey)))
        let saved = defaults.integer(forKey: Self.roundsKey)
        _rounds = State(initialValue: BreathPattern.roundChoices.contains(saved) ? saved : BreathPattern.defaultRounds)
    }

    private var elapsed: TimeInterval { max(0, clock().timeIntervalSince(stageStarted)) }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            Group {
                switch stage {
                case .start: start
                case .ready: ready
                case .breathing: breathing
                case .done: done
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .padding(.horizontal, 28).padding(.top, 14).padding(.bottom, 24)
        .frame(minWidth: 480, minHeight: 620)
        .background(Self.night)
        .environment(\.colorScheme, .dark)
        .onAppear {
            guard tally == nil else { return }
            tally = PauseStore.record(now: clock(), defaults: defaults)
        }
        .onReceive(Timer.publish(every: 0.1, on: .main, in: .common).autoconnect()) { _ in advance() }
    }

    private func go(_ next: Stage) {
        stageStarted = clock()
        stage = next
    }

    private func advance() {
        switch stage {
        case .ready where elapsed >= BreathPattern.countdown:
            go(.breathing)
        case .breathing where elapsed >= pattern.breathLength * Double(rounds):
            chosen = nil
            go(.done)
        default:
            break
        }
    }

    // MARK: Top bar

    private var topBar: some View {
        HStack(spacing: 10) {
            if stage == .start || stage == .breathing {
                HStack(spacing: 2) {
                    ForEach(BreathPattern.allCases) { option in
                        Button {
                            guard option != pattern else { return }
                            pattern = option
                            defaults.set(option.rawValue, forKey: Self.patternKey)
                            if stage == .breathing { stageStarted = clock() }
                        } label: {
                            Text(option == .sigh ? "Sigh" : "Even 4·6")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundStyle(option == pattern ? Self.goldInk : Self.cream.opacity(0.8))
                                .padding(.horizontal, 12).frame(height: 26)
                                .background(Capsule().fill(option == pattern ? Self.gold : Color.clear))
                                .contentShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(option == pattern ? .isSelected : [])
                    }
                }
                .padding(2)
                .background(Capsule().fill(Self.cream.opacity(0.08)))
                .accessibilityElement(children: .contain)
                .accessibilityLabel("Breathing pattern")
            }
            Spacer(minLength: 8)
            if stage == .done, let count = tally?.count, count > 0 {
                Text("\(count) pauses today")
                    .font(.system(size: 12, weight: .medium)).foregroundStyle(Self.cream)
                    .padding(.horizontal, 12).frame(height: 26)
                    .background(Capsule().fill(Self.cream.opacity(0.1)))
            }
            // Esc closes from every stage, with or without a visible button.
            Button("Close", action: onClose).keyboardShortcut(.cancelAction).opacity(0).frame(width: 0, height: 0)
                .accessibilityHidden(true)
        }
        .frame(height: 30)
    }

    // MARK: Stages

    private var start: some View {
        VStack(spacing: 0) {
            Text("The pull rises, peaks and passes. You only have to wait it out.")
                .font(.system(size: 21, weight: .semibold)).foregroundStyle(Self.cream)
                .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: 380).padding(.top, 18)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 12)
            // The flower waits closed: it only opens once the first breath begins.
            BreathFlower(scale: BreathPattern.rest, progress: 0).frame(width: 250, height: 250)
            Spacer(minLength: 12)
            HStack {
                Text("Rounds").font(.system(size: 13, weight: .medium)).foregroundStyle(Self.cream.opacity(0.7))
                Spacer()
                Text(verbatim: BreathPattern.lengthText(rounds: rounds))
                    .font(.system(size: 13, weight: .medium)).foregroundStyle(Self.gold).monospacedDigit()
            }
            .frame(maxWidth: 380)
            HStack(spacing: 10) {
                ForEach(BreathPattern.roundChoices, id: \.self) { option in
                    Button {
                        rounds = option
                        defaults.set(option, forKey: Self.roundsKey)
                    } label: {
                        Text(verbatim: "\(option)")
                            .font(.system(size: 20, weight: .semibold, design: .rounded))
                            .foregroundStyle(option == rounds ? Self.goldInk : Self.cream)
                            .frame(maxWidth: .infinity).frame(height: 46)
                            .background(RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(option == rounds ? Self.gold : Self.cream.opacity(0.07)))
                            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(option == rounds ? Self.gold : Self.cream.opacity(0.16), lineWidth: 1))
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text("Rounds") + Text(verbatim: " \(option)"))
                    .accessibilityAddTraits(option == rounds ? .isSelected : [])
                }
            }
            .frame(maxWidth: 380).padding(.top, 8)
            goldButton("Start") { go(.ready) }
                .keyboardShortcut(.defaultAction)
                .padding(.top, 14)
        }
    }

    private var ready: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: false)) { _ in
            let time = min(elapsed, BreathPattern.countdown - 0.001)
            let number = 3 - Int(time)
            let fraction = time - Double(Int(time))
            let eased = fraction * fraction * (3 - 2 * fraction)
            VStack(spacing: 0) {
                Spacer(minLength: 12)
                ZStack {
                    BreathFlower(scale: BreathPattern.rest, progress: 0)
                    Text(verbatim: "\(number)")
                        .font(.system(size: 64, weight: .semibold, design: .rounded)).foregroundStyle(Self.goldInk)
                        .scaleEffect(reduceMotion ? 1 : 1.18 - 0.18 * eased)
                        .opacity(reduceMotion ? 1 : min(1, min(fraction / 0.2, (1 - fraction) / 0.15)))
                        .accessibilityLabel("Starting in \(number)")
                }
                .frame(width: 270, height: 270)
                Text("Get ready")
                    .font(.system(size: 28, weight: .semibold)).foregroundStyle(Self.cream).padding(.top, 22)
                    .accessibilityAddTraits(.isHeader)
                Text("Sit back and let your shoulders drop")
                    .font(.system(size: 14)).foregroundStyle(Self.cream.opacity(0.75)).padding(.top, 5)
                Spacer(minLength: 12)
            }
        }
    }

    private var breathing: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: false)) { _ in
            let position = pattern.position(at: elapsed, rounds: rounds)
            let total = Int(pattern.breathLength) * rounds
            VStack(spacing: 0) {
                Spacer(minLength: 12)
                ZStack {
                    BreathFlower(scale: reduceMotion ? 0.84 : position.scale, progress: position.progress)
                    Text(verbatim: "\(max(1, position.secondsLeft))")
                        .font(.system(size: 46, weight: .semibold, design: .rounded)).foregroundStyle(Self.goldInk)
                        .monospacedDigit()
                }
                .frame(width: 270, height: 270)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(word(position))
                .accessibilityValue("\(min(total, Int(elapsed))) of \(total) seconds")
                Text(word(position))
                    .font(.system(size: 28, weight: .semibold)).foregroundStyle(Self.cream).padding(.top, 22)
                    .accessibilityAddTraits(.isHeader)
                Text(hint(position))
                    .font(.system(size: 14)).foregroundStyle(Self.cream.opacity(0.75)).padding(.top, 5)
                HStack(spacing: 6) {
                    ForEach(Array(pattern.phases.enumerated()), id: \.offset) { index, phase in
                        Text(step(phase))
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(index == position.index ? Self.goldInk
                                             : Self.cream.opacity(index < position.index ? 0.4 : 0.7))
                            .padding(.horizontal, 12).frame(height: 26)
                            .background(Capsule().fill(index == position.index ? Self.gold : Color.clear))
                            .overlay(Capsule().stroke(index == position.index ? Self.gold : Self.cream.opacity(0.22), lineWidth: 1))
                    }
                }
                .padding(.top, 16)
                .accessibilityHidden(true)
                Text("Breath \(position.breath + 1) of \(rounds)")
                    .font(.system(size: 12, weight: .medium)).foregroundStyle(Self.cream.opacity(0.6))
                    .monospacedDigit().padding(.top, 10)
                if let reason = DistractionStore.shared.book.ownReason {
                    Text(verbatim: "“\(reason)”").font(.system(size: 15, weight: .medium))
                        .foregroundStyle(Self.cream.opacity(0.88)).multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true).padding(.top, 14)
                }
                Spacer(minLength: 12)
            }
        }
    }

    private var done: some View {
        VStack(spacing: 0) {
            Image("UnrotGlassSprout").resizable().scaledToFill()
                .frame(width: 132, height: 132).clipShape(Circle()).padding(.top, 6)
                .accessibilityHidden(true)
            Text("The pull is smaller now.")
                .font(.system(size: 26, weight: .semibold)).foregroundStyle(Self.cream).padding(.top, 14)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text("It passes faster than it feels. What would help next?")
                .font(.system(size: 14)).foregroundStyle(Self.cream.opacity(0.75)).padding(.top, 5)
                .multilineTextAlignment(.center)
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible())], spacing: 8) {
                ForEach(BreatheAlternative.allCases) { option in
                    Button { chosen = option } label: {
                        HStack(spacing: 10) {
                            Text(verbatim: option.emoji).font(.system(size: 20)).accessibilityHidden(true)
                            Text(option.title).font(.system(size: 14, weight: .medium))
                                .foregroundStyle(chosen == option ? Self.goldInk : Self.cream)
                                .lineLimit(1).minimumScaleFactor(0.8)
                            Spacer(minLength: 0)
                        }
                        .padding(.horizontal, 14).frame(height: 48)
                        .background(RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(chosen == option ? Self.gold : Self.cream.opacity(0.07)))
                        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(chosen == option ? Self.gold : Self.cream.opacity(0.14), lineWidth: 1))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(chosen == option ? .isSelected : [])
                }
            }
            .frame(maxWidth: 420).padding(.top, 18)
            Text("Good choice. Go do it now; this will be here.")
                .font(.system(size: 13)).foregroundStyle(Self.gold).padding(.top, 10)
                .opacity(chosen == nil ? 0 : 1)
                .accessibilityHidden(chosen == nil)
            Spacer(minLength: 10)
            Button { go(.ready) } label: {
                Label("Breathe again", systemImage: "arrow.clockwise")
                    .font(.system(size: 13, weight: .medium)).foregroundStyle(Self.cream)
                    .padding(.horizontal, 10).frame(height: 30).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            goldButton("Back to my day", action: onClose)
                .keyboardShortcut(.defaultAction)
                .padding(.top, 8)
        }
    }

    // MARK: Words

    private func word(_ position: BreathPattern.Position) -> LocalizedStringKey {
        switch position.phase {
        case .breatheIn: return "Breathe in…"
        case .topUp: return "A little more…"
        case .out: return "Slowly out…"
        }
    }

    private func hint(_ position: BreathPattern.Position) -> LocalizedStringKey {
        switch (pattern, position.phase) {
        case (.even, .breatheIn): return "Through your nose, for four"
        case (.even, _): return "Out through your mouth, for six"
        case (_, .breatheIn): return "Slowly, through your nose"
        case (_, .topUp): return "A second short sip of air to fill up"
        case (_, .out): return "Let it all go, longer than the breath in"
        }
    }

    private func step(_ phase: BreathPattern.Phase) -> LocalizedStringKey {
        switch phase {
        case .breatheIn: return "in"
        case .topUp: return "top up"
        case .out: return "slowly out"
        }
    }

    private func goldButton(_ title: LocalizedStringKey, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.system(size: 16, weight: .semibold)).foregroundStyle(Self.goldInk)
                .frame(maxWidth: 380).frame(height: 46)
                .background(Capsule().fill(LinearGradient(colors: [Color(red: 0.96, green: 0.78, blue: 0.35), Self.gold],
                                                          startPoint: .top, endPoint: .bottom)))
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// A gold flower of five layers inside a thin arc that fills over the whole
/// session. It opens on the in-breath and closes on the out-breath.
struct BreathFlower: View {
    let scale: Double
    let progress: Double

    var body: some View {
        Canvas { context, size in
            let stroke: CGFloat = 3
            let radius = min(size.width, size.height) / 2 - stroke
            let centre = CGPoint(x: size.width / 2, y: size.height / 2)
            let ring = Path(ellipseIn: CGRect(x: centre.x - radius, y: centre.y - radius, width: radius * 2, height: radius * 2))
            context.stroke(ring, with: .color(.white.opacity(0.12)), lineWidth: stroke)
            if progress > 0 {
                var arc = Path()
                arc.addArc(center: centre, radius: radius, startAngle: .degrees(-90),
                           endAngle: .degrees(-90 + 360 * min(1, progress)), clockwise: false)
                context.stroke(arc, with: .color(Color(red: 0.94, green: 0.73, blue: 0.24)),
                               style: StrokeStyle(lineWidth: stroke, lineCap: .round))
            }
            let inner = radius - 16
            let glow = inner * 1.05
            context.fill(Path(ellipseIn: CGRect(x: centre.x - glow, y: centre.y - glow, width: glow * 2, height: glow * 2)),
                         with: .radialGradient(Gradient(colors: [Color(red: 0.94, green: 0.73, blue: 0.24)
                                                                    .opacity(0.35 * min(1, max(0.4, scale))), .clear]),
                                               center: centre, startRadius: 0, endRadius: glow))
            let colours: [Color] = [Color(red: 0.79, green: 0.55, blue: 0.13), Color(red: 0.88, green: 0.66, blue: 0.20),
                                    Color(red: 0.94, green: 0.73, blue: 0.24), Color(red: 1.0, green: 0.85, blue: 0.47),
                                    Color(red: 1.0, green: 0.95, blue: 0.77)]
            for (layer, ratio) in [1.0, 0.8, 0.6, 0.41, 0.22].enumerated() {
                let big = inner * ratio * (0.62 + 0.38 * scale)
                var petal = Path()
                for degree in stride(from: 0, through: 360, by: 3) {
                    let t = Double(degree) * .pi / 180
                    let r = big * (0.8 + 0.2 * pow(abs(cos(2.5 * t)), 0.55))
                    let point = CGPoint(x: centre.x + r * cos(t - .pi / 2), y: centre.y + r * sin(t - .pi / 2))
                    if degree == 0 { petal.move(to: point) } else { petal.addLine(to: point) }
                }
                petal.closeSubpath()
                context.fill(petal, with: .color(colours[layer]))
            }
        }
        .accessibilityHidden(true)
    }
}
