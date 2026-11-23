import SwiftUI

/// First run in four chapters (You → Why it's hard → What helps → Your plan),
/// CleanPath's order with Unrot's own content: one question a screen, a
/// progress bar, a picture built only from the person's answers (no scare
/// score), setup, a hold-to-commit pledge, then the seed is planted.
struct UnrotOnboarding: View {
    @ObservedObject var store: UnrotStore
    @ObservedObject var protection: ProtectionController
    let ruleSaved: Bool
    let onChooseApps: () -> Void
    let onLock: () -> Void
    let locked: Bool

    enum Step: Int, CaseIterable {
        case welcome, name, lessOf, before, when, feel, picture, tricks, wave, instead, reason, setup, pledge, plant
        var chapter: LocalizedStringKey {
            switch self {
            case .welcome, .name, .lessOf, .before, .when, .feel: return "u.ob.ch.you"
            case .picture, .tricks: return "u.ob.ch.why"
            case .wave, .instead: return "u.ob.ch.helps"
            default: return "u.ob.ch.plan"
            }
        }
    }

    @State private var step: Step = .welcome
    @State private var answers = UnrotStore.Answers(beforeMinutes: 180)
    @State private var held = false
    @FocusState private var typing: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            RadialGradient(colors: [Color(red: 0.141, green: 0.102, blue: 0.043), UPalette.base],
                           center: .top, startRadius: 0, endRadius: 600).ignoresSafeArea()
            VStack(spacing: 0) {
                if step != .welcome && step != .plant { bar }
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) { content }
                        .padding(.horizontal, 22)
                        .padding(.top, 18)
                        .frame(maxWidth: 560)
                        .frame(maxWidth: .infinity)
                }
                .scrollIndicators(.hidden)
                .scrollDismissesKeyboard(.interactively)
                footer
            }
        }
        .preferredColorScheme(.dark)
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.25), value: step)
    }

    // MARK: - Chrome

    private var bar: some View {
        HStack(spacing: 12) {
            Button { go(-1) } label: {
                Image(systemName: "chevron.backward").font(.headline).foregroundStyle(UPalette.ink)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel(Text("u.back"))
            VStack(alignment: .leading, spacing: 6) {
                Text(step.chapter).font(.caption2.weight(.bold)).tracking(1.2).textCase(.uppercase)
                    .foregroundStyle(UPalette.goldLight)
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.white.opacity(0.12))
                        Capsule().fill(UPalette.gold)
                            .frame(width: geo.size.width * Double(step.rawValue) / Double(Step.allCases.count - 1))
                    }
                }
                .frame(height: 5)
            }
            .accessibilityElement()
            .accessibilityLabel(Text("u.ob.progress \(step.rawValue) \(Step.allCases.count - 1)"))
            Color.clear.frame(width: 44, height: 44)
        }
        .padding(.horizontal, 10)
    }

    @ViewBuilder private var footer: some View {
        VStack(spacing: 10) {
            switch step {
            case .welcome:
                UGoldButton(title: "u.ob.start", wide: true) { go(1) }
            case .pledge:
                HoldButton(done: $held) { go(1) }
            case .plant:
                UGoldButton(title: "u.ob.plant", systemImage: "leaf.fill", wide: true) {
                    store.finishOnboarding(answers)
                }
            case .name, .reason:
                UGoldButton(title: "u.continue", wide: true) { typing = false; go(1) }
                Button("u.skip") { typing = false; go(1) }.font(.subheadline).foregroundStyle(UPalette.dim)
            case .setup:
                UGoldButton(title: "u.continue", wide: true) { go(1) }
                Button("u.ob.later") { go(1) }.font(.subheadline).foregroundStyle(UPalette.dim)
            default:
                UGoldButton(title: "u.continue", wide: true) { go(1) }
                    .disabled(!canContinue).opacity(canContinue ? 1 : 0.45)
            }
        }
        .padding(.horizontal, 22)
        .padding(.bottom, 16)
        .padding(.top, 8)
    }

    private var canContinue: Bool {
        switch step {
        case .lessOf: return !answers.lessOf.isEmpty
        default: return true
        }
    }

    private func go(_ delta: Int) {
        guard let next = Step(rawValue: step.rawValue + delta) else { return }
        step = next
    }

    private func question(_ key: LocalizedStringKey, _ sub: LocalizedStringKey? = nil) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(key).font(.uDisplay(30)).foregroundStyle(UPalette.ink)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            if let sub {
                Text(sub).font(.subheadline).foregroundStyle(UPalette.dim).fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.bottom, 6)
    }

    private func toggle(_ list: inout [String], _ value: String) {
        if let i = list.firstIndex(of: value) { list.remove(at: i) } else { list.append(value) }
    }

    // MARK: - Screens

    @ViewBuilder private var content: some View {
        switch step {
        case .welcome: welcome
        case .name:
            question("u.ob.name.q", "u.ob.name.sub")
            TextField("u.ob.name.placeholder", text: $answers.name)
                .font(.uDisplay(24)).foregroundStyle(UPalette.ink)
                .textContentType(.givenName)
                .focused($typing)
                .padding(16)
                .background(UPalette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                .overlay { RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(UPalette.gold.opacity(0.7)) }
        case .lessOf:
            question("u.ob.less.q", "u.ob.multi")
            ForEach([("short", "play.rectangle.on.rectangle", "u.ob.less.short"), ("feeds", "list.bullet.rectangle", "u.ob.less.feeds"),
                     ("phone", "iphone", "u.ob.less.phone"), ("adult", "eye.slash", "u.ob.less.adult")], id: \.0) { item in
                UOption(title: LocalizedStringKey(item.2), systemImage: item.1,
                        selected: answers.lessOf.contains(item.0), multiple: true) { toggle(&answers.lessOf, item.0) }
            }
        case .before:
            question("u.ob.before.q", "u.ob.before.sub")
            ForEach([60, 120, 180, 240, 300, 420], id: \.self) { value in
                UOption(title: value == 420 ? "u.ob.before.more" : uKey("u.ob.before.h", value / 60),
                        selected: answers.beforeMinutes == value) { answers.beforeMinutes = value }
            }
        case .when:
            question("u.ob.when.q", "u.ob.multi")
            ForEach([("morning", "sunrise"), ("work", "briefcase"), ("evening", "sunset"), ("bed", "moon.zzz")], id: \.0) { item in
                UOption(title: uKey("u.ob.when.", item.0), systemImage: item.1,
                        selected: answers.when.contains(item.0), multiple: true) { toggle(&answers.when, item.0) }
            }
        case .feel:
            question("u.ob.feel.q", "u.ob.multi")
            ForEach([("tired", "battery.25"), ("behind", "clock.arrow.circlepath"), ("low", "cloud.drizzle"), ("empty", "circle.dashed")], id: \.0) { item in
                UOption(title: uKey("u.ob.feel.", item.0), systemImage: item.1,
                        selected: answers.feel.contains(item.0), multiple: true) { toggle(&answers.feel, item.0) }
            }
        case .picture: picture
        case .tricks: tricks
        case .wave: wave
        case .instead:
            question("u.ob.instead.q \(UnrotFormat.short(minutes: answers.beforeMinutes))", "u.ob.instead.sub")
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                ForEach(UnrotInsteadSheet.ideas, id: \.0) { idea in
                    UOption(title: idea.2, systemImage: idea.1, selected: answers.instead.contains(idea.0), multiple: true) {
                        toggle(&answers.instead, idea.0)
                    }
                }
            }
        case .reason:
            question("u.ob.reason.q", "u.ob.reason.sub")
            TextField("u.ob.reason.placeholder", text: $answers.reason, axis: .vertical)
                .lineLimit(3...6)
                .font(.uDisplay(22)).foregroundStyle(UPalette.ink)
                .focused($typing)
                .padding(16)
                .background(UPalette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                .overlay { RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(UPalette.gold.opacity(0.7)) }
        case .setup: setup
        case .pledge: pledge
        case .plant: plant
        }
    }

    private var welcome: some View {
        VStack(spacing: 18) {
            Image("glass_seed").resizable().scaledToFit().frame(maxWidth: 280)
                .mask(RadialGradient(colors: [.black, .black, .clear], center: .center, startRadius: 0, endRadius: 140))
                .accessibilityHidden(true)
                .padding(.top, 30)
            Text(verbatim: "Unrot").font(.uDisplay(44, weight: .bold)).foregroundStyle(UPalette.ink)
            Text("u.ob.welcome").font(.uDisplay(22)).foregroundStyle(UPalette.ink).multilineTextAlignment(.center)
            Text("u.ob.welcomeSub").font(.subheadline).foregroundStyle(UPalette.dim).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }

    private var picture: some View {
        let yearDays = answers.beforeMinutes * 365 / 1440
        let years = Double(answers.beforeMinutes) * 365 * 50 / 1440 / 365
        let yearsText = years.formatted(.number.precision(.fractionLength(1)))
        return VStack(alignment: .leading, spacing: 14) {
            question("u.ob.picture.q")
            UCard(highlight: UPalette.gold.opacity(0.5)) {
                Text(verbatim: "\(yearDays)").font(.uDisplay(64, weight: .bold)).foregroundStyle(UPalette.goldLight)
                Text("u.ob.picture.days \(UnrotFormat.short(minutes: answers.beforeMinutes))")
                    .font(.subheadline).foregroundStyle(UPalette.ink).fixedSize(horizontal: false, vertical: true)
            }
            UCard {
                Text("u.ob.picture.years \(yearsText)")
                    .font(.subheadline).foregroundStyle(UPalette.ink).fixedSize(horizontal: false, vertical: true)
            }
            if !answers.when.isEmpty {
                Text("u.ob.picture.when").font(.footnote).foregroundStyle(UPalette.dim)
            }
            Text("u.ob.picture.note").font(.footnote).foregroundStyle(UPalette.dim).fixedSize(horizontal: false, vertical: true)
        }
    }

    private var tricks: some View {
        VStack(alignment: .leading, spacing: 12) {
            question("u.ob.tricks.q", "u.ob.tricks.sub")
            ForEach(1...4, id: \.self) { i in
                UCard(padding: 14) {
                    HStack(alignment: .top, spacing: 12) {
                        Text(verbatim: "\(i)").font(.uDisplay(20, weight: .bold)).foregroundStyle(UPalette.onGold)
                            .frame(width: 34, height: 34).background(UPalette.goldFill, in: Circle())
                        VStack(alignment: .leading, spacing: 4) {
                            Text(uKey("u.ob.trick", i)).font(.headline).foregroundStyle(UPalette.ink)
                            Text(uKey("u.ob.trick", "\(i).body")).font(.footnote).foregroundStyle(UPalette.dim)
                                .fixedSize(horizontal: false, vertical: true)
                            Text(uKey("u.ob.trick", "\(i).answer")).font(.footnote.weight(.semibold))
                                .foregroundStyle(UPalette.goldLight).fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
            }
        }
    }

    private var wave: some View {
        VStack(alignment: .leading, spacing: 14) {
            question("u.ob.wave.q", "u.ob.wave.sub")
            WaveShape().stroke(UPalette.gold, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                .frame(height: 120).accessibilityHidden(true)
            ForEach(["u.ob.wave.1", "u.ob.wave.2", "u.ob.wave.3"], id: \.self) { key in
                Label(LocalizedStringKey(key), systemImage: "checkmark.circle.fill")
                    .font(.subheadline).foregroundStyle(UPalette.ink)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Text("u.ob.wave.source").font(.caption).foregroundStyle(UPalette.dim).fixedSize(horizontal: false, vertical: true)
        }
    }

    private var setup: some View {
        VStack(alignment: .leading, spacing: 12) {
            question("u.ob.setup.q", "u.ob.setup.sub4")
            setupRow(n: 1, title: "u.ob.setup.screenTime", detail: "u.ob.setup.screenTimeDetail",
                     done: protection.screenTimeConfigured) {
                Task { await protection.enablePersonalScreenTime() }
            }
            setupRow(n: 2, title: "u.ob.setup.apps", detail: "u.ob.setup.appsDetail", done: ruleSaved, action: onChooseApps)
            setupRow(n: 3, title: "u.ob.setup.safari", detail: "u.ob.setup.safariDetail",
                     done: protection.safariConfigured) {
                if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
            }
            setupRow(n: 4, title: "u.ob.setup.lock", detail: "u.ob.setup.lockDetail", done: locked, action: onLock)
            Text("u.ob.setup.note").font(.caption).foregroundStyle(UPalette.dim).fixedSize(horizontal: false, vertical: true)
        }
    }

    private func setupRow(n: Int, title: LocalizedStringKey, detail: LocalizedStringKey, done: Bool,
                          action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Group {
                    if done { Image(systemName: "checkmark").font(.footnote.weight(.heavy)) }
                    else { Text(verbatim: "\(n)").font(.footnote.weight(.bold)) }
                }
                .foregroundStyle(done ? Color(red: 0.06, green: 0.13, blue: 0.06) : UPalette.goldLight)
                .frame(width: 30, height: 30)
                .background(done ? UPalette.leaf : .clear, in: Circle())
                .overlay { Circle().strokeBorder(done ? UPalette.leaf : UPalette.gold, lineWidth: 2) }
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.subheadline.weight(.bold)).foregroundStyle(UPalette.ink)
                    Text(detail).font(.caption).foregroundStyle(UPalette.dim).fixedSize(horizontal: false, vertical: true)
                }
                Spacer()
                if !done {
                    Text("u.ob.setup.go").font(.caption.weight(.bold)).foregroundStyle(UPalette.goldLight)
                        .padding(.horizontal, 12).frame(minHeight: 30)
                        .background(UPalette.gold.opacity(0.18), in: Capsule())
                }
            }
            .padding(14)
            .background(UPalette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay { RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(UPalette.cardLine) }
        }
        .buttonStyle(UPressStyle())
        .disabled(done)
    }

    private var pledge: some View {
        VStack(alignment: .leading, spacing: 14) {
            question("u.ob.pledge.q", "u.ob.pledge.sub")
            UCard(highlight: UPalette.gold.opacity(0.5)) {
                ForEach(["u.ob.pledge.1", "u.ob.pledge.2", "u.ob.pledge.3"], id: \.self) { key in
                    Label(LocalizedStringKey(key), systemImage: "leaf")
                        .font(.body).foregroundStyle(UPalette.ink).fixedSize(horizontal: false, vertical: true)
                }
                if !answers.reason.isEmpty {
                    Text(verbatim: "“\(answers.reason)”").font(.uDisplay(.body)).foregroundStyle(UPalette.goldLight)
                        .padding(.top, 4)
                }
            }
        }
    }

    private var plant: some View {
        VStack(spacing: 16) {
            Image("glass_sprout").resizable().scaledToFit().frame(maxWidth: 300)
                .mask(RadialGradient(colors: [.black, .black, .clear], center: .center, startRadius: 0, endRadius: 150))
                .accessibilityHidden(true)
                .padding(.top, 30)
            (answers.name.isEmpty ? Text("u.ob.plant.title") : Text("u.ob.plant.titleNamed \(answers.name)"))
                .font(.uDisplay(32)).foregroundStyle(UPalette.ink).multilineTextAlignment(.center)
            Text("u.ob.plant.sub").font(.subheadline).foregroundStyle(UPalette.dim).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }
}

/// Hold to commit: a ring fills while the finger stays down (VoiceOver: a plain button).
private struct HoldButton: View {
    @Binding var done: Bool
    let onDone: () -> Void
    @State private var pressing = false
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver

    var body: some View {
        ZStack {
            Capsule().fill(UPalette.toolFill)
            GeometryReader { geo in
                Capsule().fill(UPalette.goldFill)
                    .frame(width: pressing || done ? geo.size.width : 0)
                    .animation(pressing ? .linear(duration: 1.6) : .easeOut(duration: 0.2), value: pressing)
            }
            .clipShape(Capsule())
            Text(done ? "u.ob.pledge.done" : "u.ob.pledge.hold")
                .font(.headline.weight(.heavy))
                .foregroundStyle(pressing || done ? UPalette.onGold : UPalette.ink)
        }
        .frame(height: 56)
        .contentShape(Capsule())
        .onLongPressGesture(minimumDuration: 1.6, maximumDistance: 40) {
            done = true
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            onDone()
        } onPressingChanged: { pressing = $0 }
        .accessibilityElement()
        .accessibilityLabel(Text("u.ob.pledge.hold"))
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { done = true; onDone() }
    }
}

/// An urge rises, peaks and falls.
private struct WaveShape: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: rect.minX, y: rect.maxY - 6))
        p.addCurve(to: CGPoint(x: rect.midX * 0.8, y: rect.minY + 8),
                   control1: CGPoint(x: rect.width * 0.2, y: rect.maxY),
                   control2: CGPoint(x: rect.width * 0.3, y: rect.minY))
        p.addCurve(to: CGPoint(x: rect.maxX, y: rect.maxY - 6),
                   control1: CGPoint(x: rect.width * 0.6, y: rect.minY + 10),
                   control2: CGPoint(x: rect.width * 0.75, y: rect.maxY))
        return p
    }
}
