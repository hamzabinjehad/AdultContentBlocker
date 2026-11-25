import SwiftUI

/// The numbers and choices behind Unrot's first run, apart from the window so
/// they can be tested.
enum UnrotOnboarding {
    /// Whole days a year that a daily habit adds up to.
    static func daysAYear(minutesPerDay: Int) -> Int {
        Int((Double(minutesPerDay) * 365 / 1440).rounded())
    }

    /// What comes back if the habit is halved: the goal the app states, not zero.
    static func daysBack(minutesPerDay: Int) -> Int { daysAYear(minutesPerDay: minutesPerDay) / 2 }

    /// The same habit over fifty years, in years, to one decimal: "3.6".
    static func yearsOfLife(minutesPerDay: Int) -> String {
        (Double(minutesPerDay) * 50 / 1440).formatted(.number.precision(.fractionLength(1)))
    }

    static let guessRange = 15...360
    static let guessStep = 15

    enum Goal: String, CaseIterable, Identifiable {
        case sleep, work, evenings, less
        var id: String { rawValue }

        var template: RuleTemplate {
            switch self {
            case .sleep: return .quietHours
            case .work: return .schedule
            case .evenings: return .dailyLimit
            case .less: return .feedsOnly
            }
        }
        var emoji: String { template.emoji }
        var title: LocalizedStringKey {
            switch self {
            case .sleep: return "Sleep"
            case .work: return "Focus at work"
            case .evenings: return "Evenings back"
            case .less: return "Just less scrolling"
            }
        }
        var rule: LocalizedStringKey {
            switch self {
            case .sleep: return "The big feeds close from 22:00 to 07:00"
            case .work: return "The big feeds close from 9 to 5 on workdays"
            case .evenings: return "One hour a day for feeds, then they close"
            case .less: return "Home feeds and short videos are hidden"
            }
        }
    }
}

/// Unrot's first run, on the arc the phone apps use: the problem in the
/// person's own number, how the app answers it, what they want back, and the
/// three things left for them to do.
struct UnrotWelcomeView: View {
    let close: () -> Void
    @ObservedObject private var store = DistractionStore.shared
    @State private var step: Int
    @State private var minutes: Double
    @State private var made: [UnrotOnboarding.Goal: UUID] = [:]
    @State private var error: String?
    @State private var reason: String
    @State private var showPrivateBrowsing = false
    @State private var privateStatus = PrivateBrowsingStatus.current(force: true)

    static let stepCount = 6

    init(initialStep: Int = 0, goals: Set<UnrotOnboarding.Goal> = [], close: @escaping () -> Void) {
        self.close = close
        _step = State(initialValue: min(max(initialStep, 0), Self.stepCount - 1))
        _minutes = State(initialValue: DistractionStore.shared.book.beforePerDay / 60)
        _reason = State(initialValue: DistractionStore.shared.book.ownReason ?? "")
        // Preview only: shows goals as chosen without writing rules.
        _made = State(initialValue: Dictionary(uniqueKeysWithValues: goals.map { ($0, UUID()) }))
    }

    private var perDay: Int { Int(minutes) }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Image("UnrotGlassSprout").resizable().scaledToFill()
                    .frame(width: 24, height: 24).clipShape(Circle()).accessibilityHidden(true)
                Text(verbatim: MacEdition.unrot.displayName).font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(HnPalette.ink)
                Spacer()
                Button("Skip", action: close).buttonStyle(.borderless)
                    .font(.system(.caption).weight(.medium)).foregroundStyle(HnPalette.muted)
            }
            .padding(.horizontal, 28).padding(.top, 20)
            HStack(spacing: 4) {
                ForEach(0..<Self.stepCount, id: \.self) { index in
                    Capsule().fill(index <= step ? HnPalette.accent : Color.white.opacity(0.1)).frame(height: 3)
                }
            }
            .padding(.horizontal, 28).padding(.top, 12)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text("Step \(step + 1) of \(Self.stepCount)"))

            Group {
                switch step {
                case 0: problem
                case 1: tricks
                case 2: solution
                case 3: goals
                case 4: reasonAndPledge
                default: plan
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .padding(.horizontal, 28).padding(.top, 20)

            HStack(spacing: 10) {
                Spacer()
                if step > 0 { HnButton(title: "Back", kind: .quiet) { step -= 1 } }
                if step == Self.stepCount - 1 {
                    HnButton(title: "Open Today", kind: .filled) { finish(.today) }.keyboardShortcut(.defaultAction)
                } else {
                    HnButton(title: step == 0 ? "Let’s get them back" : "Continue", systemImage: "arrow.forward") {
                        if step == 0 { store.setBefore(minutesPerDay: perDay) }
                        if step == 4 { store.setReason(reason) }
                        step += 1
                    }
                    .keyboardShortcut(.defaultAction)
                }
            }
            .padding(.horizontal, 28).padding(.bottom, 22)
        }
        .frame(width: 680, height: 620)
        .background(HnPalette.canvas)
        .sheet(isPresented: $showPrivateBrowsing) { PrivateBrowsingSheet() }
        .onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in
            PrivateBrowsingStatus.invalidateCache()
            privateStatus = PrivateBrowsingStatus.current(force: true)
        }
    }

    private func finish(_ page: ContentView.Page) {
        close()
        AppNavigation.shared.open(page)
    }

    private func eyebrow(_ text: LocalizedStringKey) -> some View {
        Text(text).font(.system(size: 12, weight: .semibold)).foregroundStyle(HnPalette.accentText)
    }

    private func title(_ text: Text) -> some View {
        text.font(.system(size: 27, weight: .bold)).foregroundStyle(HnPalette.ink)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityAddTraits(.isHeader)
    }

    private func bodyLine(_ text: LocalizedStringKey) -> some View {
        Text(text).font(.system(size: 13.5)).foregroundStyle(HnPalette.muted)
            .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: 1 — the problem, in the person's own number

    private var problem: some View {
        let days = UnrotOnboarding.daysAYear(minutesPerDay: perDay)
        let back = UnrotOnboarding.daysBack(minutesPerDay: perDay)
        return VStack(alignment: .leading, spacing: 12) {
            eyebrow("What it adds up to")
            title(Text("About \(RuleSummary.duration(minutes: perDay)) a day is \(days) days a year."))
            bodyLine("Unrot blocks adult content on this Mac. It also gives back the time that feeds take, with no decision from you. How long do they take on a normal day?")
            HStack(spacing: 12) {
                Slider(value: $minutes, in: Double(UnrotOnboarding.guessRange.lowerBound)...Double(UnrotOnboarding.guessRange.upperBound),
                       step: Double(UnrotOnboarding.guessStep))
                    .tint(HnPalette.accent)
                    .accessibilityLabel("Feed time a day")
                Text(verbatim: RuleSummary.duration(minutes: perDay))
                    .font(.system(size: 17, weight: .semibold, design: .rounded)).foregroundStyle(HnPalette.accentText)
                    .monospacedDigit().frame(width: 84, alignment: .trailing)
            }
            YearDots(days: days, back: back).frame(height: 88)
            HStack(spacing: 16) {
                legend(HnPalette.accent, Text("\(days - back) still on feeds"))
                legend(HnPalette.calm, Text("\(back) back if you halve it"))
                legend(Color.white.opacity(0.14), Text("the rest of your year"))
            }
            Text("Halving it is a common, real result. That is the goal, not zero.")
                .font(.system(size: 12.5)).foregroundStyle(HnPalette.muted)
            Text("Over fifty years that is \(UnrotOnboarding.yearsOfLife(minutesPerDay: perDay)) years of waking life.")
                .font(.system(size: 12.5)).foregroundStyle(HnPalette.muted)
        }
    }

    // MARK: 2 — why it is hard, and the answer to each

    private var tricks: some View {
        VStack(alignment: .leading, spacing: 10) {
            eyebrow("Why it’s hard")
            title(Text("It isn’t weak willpower."))
            bodyLine("These are built to keep you. Four traps, and how Unrot answers each.")
            trick(1, "Adult content, the strongest trap", "The same pull as a feed, and much harder to walk away from.",
                  "Blocked in every app and every browser on this Mac.")
            trick(2, "There’s no bottom", "An endless feed never gives you a place to stop.",
                  "A pause before the app opens.")
            trick(3, "A slot machine on your desk", "You never know if the next video is the good one, so you keep pulling.",
                  "Limits and opens you choose ahead.")
            trick(4, "It learns your weak hours", "It is strongest when you are tired, bored or alone.",
                  "Quiet hours and Focus for those times.")
        }
    }

    private func trick(_ number: Int, _ heading: LocalizedStringKey, _ detail: LocalizedStringKey,
                       _ answer: LocalizedStringKey) -> some View {
        HStack(alignment: .center, spacing: 12) {
            Text(verbatim: "\(number)").font(.system(size: 14, weight: .bold, design: .rounded))
                .foregroundStyle(Color(red: 0.16, green: 0.11, blue: 0))
                .frame(width: 28, height: 28).background(Circle().fill(HnPalette.accent))
            VStack(alignment: .leading, spacing: 2) {
                Text(heading).font(.system(size: 14.5, weight: .semibold)).foregroundStyle(HnPalette.ink)
                Text(detail).font(.system(size: 12.5)).foregroundStyle(HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 10)
            HStack(spacing: 6) {
                Image(systemName: "arrow.forward").font(.system(size: 10, weight: .bold))
                Text(answer).font(.system(size: 12.5, weight: .medium))
                    .fixedSize(horizontal: false, vertical: true).multilineTextAlignment(.leading)
            }
            .foregroundStyle(HnPalette.accentText)
            .frame(width: 210, alignment: .leading)
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.white.opacity(0.06)))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Color.white.opacity(0.08), lineWidth: 0.5))
        .accessibilityElement(children: .combine)
    }

    private func legend(_ color: Color, _ text: Text) -> some View {
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 8, height: 8).accessibilityHidden(true)
            text.font(.system(size: 12)).foregroundStyle(HnPalette.muted).monospacedDigit()
        }
    }

    // MARK: 2 — the answer, shown

    private var solution: some View {
        VStack(alignment: .leading, spacing: 12) {
            eyebrow("What helps")
            title(Text("Adult content stays out."))
            HStack(spacing: 12) {
                Text(verbatim: "🛡️").font(.system(size: 30)).accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text("The Shield is what Unrot is for").font(.system(size: 15, weight: .semibold)).foregroundStyle(HnPalette.ink)
                    Text("Adult sites, words and images are blocked in every app and browser, private windows included. A lock makes it hard to turn off.")
                        .font(.system(size: 12.5)).foregroundStyle(HnPalette.muted).fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14).padding(.vertical, 11)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(HnPalette.accent.opacity(0.10)))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(HnPalette.accent.opacity(0.5), lineWidth: 1))
            .accessibilityElement(children: .combine)
            Text("Your Mac’s Screen Time can limit an app. Unrot goes where it stops:")
                .font(.system(size: 12.5)).foregroundStyle(HnPalette.muted)
            HStack(alignment: .top, spacing: 10) {
                demo(emoji: "📰", heading: "Only the feed closes", lines: ["Screen Time closes the whole site.", "Unrot hides only the feed.", "Search and links still work."]) {
                    VStack(spacing: 5) {
                        Text("Feed closed").font(.system(size: 12, weight: .semibold)).foregroundStyle(HnPalette.ink)
                        Capsule().fill(Color.white.opacity(0.12)).frame(width: 96, height: 16)
                            .overlay(Text("Search").font(.system(size: 9)).foregroundStyle(HnPalette.muted))
                    }
                }
                demo(emoji: "🌬️", heading: "A breath first", lines: ["Not a wall, a pause.", "Ten seconds to breathe.", "Then you choose, with opens left."]) {
                    ZStack {
                        Circle().stroke(HnPalette.accent, lineWidth: 3).frame(width: 46, height: 46)
                        Text(verbatim: "10").font(.system(size: 18, weight: .semibold, design: .rounded)).foregroundStyle(HnPalette.ink)
                    }
                }
                demo(emoji: "🔒", heading: "Hard to switch off", lines: ["Screen Time offers Ignore Limit.", "A locked rule offers no way around.", "One emergency pass a week."]) {
                    VStack(spacing: 4) {
                        Text(verbatim: "🔒").font(.system(size: 20))
                        Text("No Ignore button").font(.system(size: 11, weight: .medium)).foregroundStyle(HnPalette.muted)
                    }
                }
            }
            HStack(alignment: .center, spacing: 14) {
                UrgeWave().stroke(HnPalette.accent, style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                    .frame(width: 130, height: 44).accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 3) {
                    Text("An urge is a wave").font(.system(size: 14, weight: .semibold)).foregroundStyle(HnPalette.ink)
                    Text("It rises, peaks and falls, usually within minutes, whether you act on it or not. Notice it, breathe four in and six out, let it pass, then choose.")
                        .font(.system(size: 12.5)).foregroundStyle(HnPalette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.white.opacity(0.06)))
        }
    }

    // MARK: 5 — the person's own reason, and the pledge

    private var reasonAndPledge: some View {
        VStack(alignment: .leading, spacing: 12) {
            eyebrow("Your part")
            title(Text("Why does this matter to you?"))
            bodyLine("Unrot shows your own words when the pull comes: on the pause, on a closed app and while you breathe.")
            TextField(String(localized: "I want my evenings back."), text: $reason)
                .textFieldStyle(.plain).font(.system(size: 18, weight: .medium)).foregroundStyle(HnPalette.ink)
                .padding(.horizontal, 16).frame(height: 52)
                .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.white.opacity(0.06)))
                .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(HnPalette.accent.opacity(0.6), lineWidth: 1))
                .accessibilityLabel("Why does this matter to you?")
            Text("Your pledge").font(.system(size: 14, weight: .medium)).foregroundStyle(HnPalette.ink).padding(.top, 8)
            VStack(alignment: .leading, spacing: 10) {
                pledgeLine("I will pause before I scroll.")
                pledgeLine("When it is hard, I will breathe first.")
                pledgeLine("A slip is not the end. I will come back.")
                if !reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Text(verbatim: "“\(reason.trimmingCharacters(in: .whitespacesAndNewlines))”")
                        .font(.system(size: 15, weight: .medium)).foregroundStyle(HnPalette.accentText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.white.opacity(0.06)))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(HnPalette.accent.opacity(0.35), lineWidth: 1))
            Text("To yourself, not to us. It stays on this Mac.")
                .font(.system(size: 12.5)).foregroundStyle(HnPalette.muted)
        }
    }

    private func pledgeLine(_ text: LocalizedStringKey) -> some View {
        HStack(spacing: 10) {
            Text(verbatim: "🌱").accessibilityHidden(true)
            Text(text).font(.system(size: 14.5)).foregroundStyle(HnPalette.ink)
        }
    }

    private func demo<Mock: View>(emoji: String, heading: LocalizedStringKey, lines: [LocalizedStringKey],
                                  @ViewBuilder mock: () -> Mock) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            mock().frame(maxWidth: .infinity).frame(height: 56)
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Color.black.opacity(0.35)))
                .accessibilityHidden(true)
            HStack(spacing: 6) {
                Text(verbatim: emoji).accessibilityHidden(true)
                Text(heading).font(.system(size: 14, weight: .semibold)).foregroundStyle(HnPalette.ink)
            }
            VStack(alignment: .leading, spacing: 5) {
                ForEach(Array(lines.enumerated()), id: \.offset) { index, line in
                    HStack(alignment: .firstTextBaseline, spacing: 7) {
                        Text(verbatim: "\(index + 1)").font(.system(size: 10, weight: .bold)).foregroundStyle(HnPalette.accentText)
                            .frame(width: 12)
                        Text(line).font(.system(size: 12.5)).foregroundStyle(HnPalette.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.white.opacity(0.06)))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Color.white.opacity(0.08), lineWidth: 0.5))
        .accessibilityElement(children: .combine)
    }

    // MARK: 3 — what the person wants back; each choice is a rule

    private var goals: some View {
        VStack(alignment: .leading, spacing: 12) {
            eyebrow("Your part")
            title(Text("What do you want back?"))
            bodyLine("Adult protection is already yours. Pick what else you want; each becomes a rule you can change on Today.")
            VStack(spacing: 8) {
                HStack(spacing: 12) {
                    Text(verbatim: "🛡️").font(.system(size: 26)).frame(width: 38).accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("No adult content").font(.system(size: 15, weight: .semibold)).foregroundStyle(HnPalette.ink)
                        Text("The core, always on and private. Sites, words and images are blocked.")
                            .font(.system(size: 12.5)).foregroundStyle(HnPalette.muted)
                    }
                    Spacer(minLength: 8)
                    Image(systemName: "lock.fill").font(.system(size: 13)).foregroundStyle(HnPalette.faint)
                }
                .padding(.horizontal, 14).padding(.vertical, 10)
                .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(HnPalette.accent.opacity(0.10)))
                .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(HnPalette.accent.opacity(0.5), lineWidth: 1))
                .accessibilityElement(children: .combine)
                ForEach(UnrotOnboarding.Goal.allCases) { goal in
                    let on = made[goal] != nil
                    Button { toggle(goal) } label: {
                        HoverSurface {
                            HStack(spacing: 12) {
                                Text(verbatim: goal.emoji).font(.system(size: 26)).frame(width: 38).accessibilityHidden(true)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(goal.title).font(.system(size: 15, weight: .semibold)).foregroundStyle(HnPalette.ink)
                                    Text(goal.rule).font(.system(size: 12.5)).foregroundStyle(HnPalette.muted)
                                }
                                Spacer(minLength: 8)
                                Image(systemName: on ? "checkmark.circle.fill" : "circle")
                                    .font(.system(size: 20)).foregroundStyle(on ? HnPalette.accentText : HnPalette.faint)
                            }
                            .padding(.horizontal, 14).padding(.vertical, 10)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
            if let error {
                Text(verbatim: error).font(.system(size: 12.5)).foregroundStyle(HnPalette.ember)
            }
        }
    }

    private func toggle(_ goal: UnrotOnboarding.Goal) {
        error = nil
        if let id = made[goal] {
            if case .applied = store.propose(nil, replacing: id) { made[goal] = nil }
            else { error = String(localized: "This rule can be removed on Today.") }
            return
        }
        let rule = goal.template.make()
        if case .applied = store.propose(rule, replacing: nil) { made[goal] = rule.id }
        else { error = String(localized: "This rule could not be added. Try it from New rule.") }
    }

    // MARK: 4 — the plan, and what is left for the person to do

    private var plan: some View {
        let back = UnrotOnboarding.daysBack(minutesPerDay: perDay)
        let chosen = UnrotOnboarding.Goal.allCases.filter { made[$0] != nil }
        return VStack(alignment: .leading, spacing: 12) {
            eyebrow("Your plan")
            title(Text("Here is how you get \(back) days back."))
            if chosen.isEmpty {
                bodyLine("No rule yet. Make one from New rule on Today whenever you are ready.")
            } else {
                HStack(spacing: 8) {
                    ForEach(chosen) { goal in
                        HStack(spacing: 6) {
                            Text(verbatim: goal.emoji).accessibilityHidden(true)
                            Text(goal.title).font(.system(size: 12.5, weight: .medium)).foregroundStyle(HnPalette.ink)
                        }
                        .padding(.horizontal, 10).frame(height: 28)
                        .background(Capsule().fill(HnPalette.calmSoft))
                    }
                }
            }
            Text("Three things left for you").font(.system(size: 14, weight: .medium)).foregroundStyle(HnPalette.ink)
                .padding(.top, 6)
            VStack(spacing: 8) {
                todo(1, "🛡️", "Turn on the Shield", "First and most important. A short checklist switches on adult protection for every app on this Mac.",
                     button: "Open setup") { finish(.setup) }
                todo(2, "🧩", "Connect your browser", "Sites and feeds are closed inside a connected browser. Apps are closed everywhere.",
                     button: "Show me") {
                    SettingsPage.initialPane = .browsers
                    finish(.settings)
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1) { SettingsPage.initialPane = nil }
                }
                if privateStatus.open.isEmpty {
                    todo(3, "🕶️", "Private windows", "Covered in every browser Unrot can control.", button: nil) {}
                } else {
                    todo(3, "🕶️", "Close the private-window gap", "Private windows skip the extension until you turn them off.",
                         button: "Fix") { showPrivateBrowsing = true }
                }
            }
            Text("When it pulls anyway: Breathe, from the menu bar or Today.")
                .font(.system(size: 12.5)).foregroundStyle(HnPalette.muted)
        }
    }

    private func todo(_ number: Int, _ emoji: String, _ heading: LocalizedStringKey, _ detail: LocalizedStringKey,
                      button: LocalizedStringKey?, action: @escaping () -> Void) -> some View {
        HStack(spacing: 12) {
            Text(verbatim: "\(number)").font(.system(size: 12, weight: .bold, design: .rounded)).foregroundStyle(HnPalette.accentText)
                .frame(width: 22, height: 22).background(Circle().fill(HnPalette.accent.opacity(0.16)))
            Text(verbatim: emoji).font(.system(size: 22)).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(heading).font(.system(size: 14.5, weight: .semibold)).foregroundStyle(HnPalette.ink)
                Text(detail).font(.system(size: 12.5)).foregroundStyle(HnPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 8)
            if let button {
                Button(action: action) {
                    Text(button).font(.system(size: 12, weight: .semibold)).foregroundStyle(HnPalette.ink)
                        .padding(.horizontal, 12).frame(height: 28).background(Capsule().fill(Color.white.opacity(0.12)))
                }
                .buttonStyle(.plain)
            } else {
                Image(systemName: "checkmark.circle.fill").font(.system(size: 18)).foregroundStyle(HnPalette.calm)
                    .accessibilityLabel("Done")
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 11)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.white.opacity(0.06)))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Color.white.opacity(0.08), lineWidth: 0.5))
    }
}

/// One urge, drawn: it rises, peaks and falls on its own.
struct UrgeWave: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let steps = 60
        for index in 0...steps {
            let t = Double(index) / Double(steps)
            // A skewed bell: a quick rise, a slower fall.
            let height = pow(t, 1.4) * exp(-pow((t - 0.42) / 0.26, 2)) * 2.6
            let point = CGPoint(x: rect.minX + rect.width * t, y: rect.maxY - rect.height * min(1, height))
            if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        return path
    }
}

/// A year as 365 dots: the days that stay on feeds, the days that come back if
/// the habit is halved, and the rest.
struct YearDots: View {
    let days: Int
    let back: Int

    var body: some View {
        Canvas { context, size in
            let columns = 53, rows = 7
            let cell = min(size.width / CGFloat(columns), size.height / CGFloat(rows))
            let dot = cell * 0.62
            let left = (size.width - cell * CGFloat(columns)) / 2
            for day in 0..<365 {
                let column = day / rows, row = day % rows
                let rect = CGRect(x: left + CGFloat(column) * cell + (cell - dot) / 2,
                                  y: CGFloat(row) * cell + (cell - dot) / 2, width: dot, height: dot)
                let color: Color = day < days - back ? Color(red: 0.94, green: 0.73, blue: 0.24)
                    : day < days ? Color(red: 0.56, green: 0.78, blue: 0.64) : Color.white.opacity(0.14)
                context.fill(Path(ellipseIn: rect), with: .color(color))
            }
        }
        .accessibilityHidden(true)
    }
}
