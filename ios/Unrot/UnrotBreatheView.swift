import SwiftUI

/// Breathe, like CleanPath's meditation: a ready screen with the person's own
/// words and rounds, a 3-2-1, a gold flower that opens on the in-breath and
/// closes on the out-breath (4 in, 6 out), then "It passed" with the glass.
/// Only a finished Breathe counts or seals a crack; closing early spends nothing.
struct UnrotBreatheView: View {
    @ObservedObject var store: UnrotStore
    let onClose: () -> Void

    enum Phase: Equatable { case ready, countdown(Date), breathing(Date), done(sealed: Bool) }

    @State private var phase: Phase = .ready
    @State private var rounds = 3
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    static let inhale: TimeInterval = 4
    static let exhale: TimeInterval = 6

    var body: some View {
        ZStack {
            RadialGradient(colors: [Color(red: 0.416, green: 0.290, blue: 0.071),
                                    Color(red: 0.141, green: 0.090, blue: 0.024), .black],
                           center: UnitPoint(x: 0.5, y: 0.47), startRadius: 0, endRadius: 520)
                .ignoresSafeArea()
            switch phase {
            case .ready: ready
            case .countdown(let start): countdown(start)
            case .breathing(let start): breathing(start)
            case .done(let sealed): done(sealed)
            }
        }
        .preferredColorScheme(.dark)
        .statusBarHidden()
    }

    // MARK: - Ready

    private var quote: String? {
        store.notes.first ?? (store.answers.reason.isEmpty ? nil : store.answers.reason)
    }

    private var seconds: Int { Int(Double(rounds) * (Self.inhale + Self.exhale)) }

    private var ready: some View {
        VStack(spacing: 0) {
            topBar(title: "u.breathe.title")
            Spacer(minLength: 20)
            VStack(spacing: 14) {
                Text(verbatim: "“").font(.uDisplay(72)).foregroundStyle(UPalette.gold).frame(height: 40)
                if let quote {
                    Text(verbatim: quote).font(.uDisplay(26)).multilineTextAlignment(.center)
                        .foregroundStyle(Color(red: 1, green: 0.95, blue: 0.81))
                    Text("u.breathe.fromYou").font(.footnote).foregroundStyle(UPalette.dim)
                } else {
                    Text("u.breathe.line").font(.uDisplay(26)).multilineTextAlignment(.center)
                        .foregroundStyle(Color(red: 1, green: 0.95, blue: 0.81))
                }
            }
            .padding(.horizontal, 28)
            if store.glass().look == .cracked {
                Text("u.breathe.seals").font(.footnote.weight(.semibold)).foregroundStyle(UPalette.goldLight)
                    .multilineTextAlignment(.center).padding(.top, 18).padding(.horizontal, 28)
            }
            Spacer(minLength: 20)
            VStack(spacing: 12) {
                HStack {
                    ULabel("u.breathe.rounds")
                    Spacer()
                    Text("u.breathe.length \(seconds)")
                        .font(.caption2.weight(.bold)).foregroundStyle(UPalette.dim)
                }
                HStack(spacing: 8) {
                    ForEach([3, 5, 10], id: \.self) { value in
                        Button { rounds = value } label: {
                            Text(verbatim: "\(value)").font(.headline)
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .foregroundStyle(rounds == value ? Color(red: 0.14, green: 0.09, blue: 0.03) : UPalette.ink)
                                .background(rounds == value ? Color(red: 1, green: 0.97, blue: 0.90) : Color.black.opacity(0.3),
                                            in: Capsule())
                        }
                        .buttonStyle(UPressStyle())
                        .accessibilityLabel(Text("u.breathe.roundsA11y \(value)"))
                        .accessibilityAddTraits(rounds == value ? .isSelected : [])
                    }
                }
            }
            .padding(16)
            .background(Color.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay { RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(UPalette.cardLine) }
            .padding(.horizontal, 20)
            UGoldButton(title: "u.breathe.start", wide: true) {
                phase = reduceMotion ? .breathing(Date()) : .countdown(Date())
            }
            .padding(.horizontal, 20)
            .padding(.top, 14)
            .padding(.bottom, 24)
        }
    }

    // MARK: - 3-2-1

    private func countdown(_ start: Date) -> some View {
        TimelineView(.periodic(from: start, by: 0.25)) { context in
            let left = 3 - Int(context.date.timeIntervalSince(start))
            VStack {
                topBar(title: "u.breathe.getReady")
                Spacer()
                Text(verbatim: "\(max(1, left))")
                    .font(.uDisplay(140, weight: .bold))
                    .foregroundStyle(Color(red: 1, green: 0.97, blue: 0.90))
                    .shadow(color: UPalette.gold.opacity(0.6), radius: 30)
                    .contentTransition(.numericText())
                Spacer()
            }
            .onChange(of: left <= 0) { finished in
                if finished { phase = .breathing(Date()) }
            }
        }
    }

    // MARK: - The flower

    private func breathing(_ start: Date) -> some View {
        let cycle = Self.inhale + Self.exhale
        let total = cycle * Double(rounds)
        return TimelineView(.animation(minimumInterval: 1.0 / 30, paused: false)) { context in
            let t = context.date.timeIntervalSince(start)
            let within = t.truncatingRemainder(dividingBy: cycle)
            let inhaling = within < Self.inhale
            let open = inhaling ? within / Self.inhale : 1 - (within - Self.inhale) / Self.exhale
            let eased = 0.5 - 0.5 * cos(Double.pi * open)
            let round = min(rounds, Int(t / cycle) + 1)
            ZStack {
                VStack {
                    HStack(spacing: 4) {
                        ForEach(0..<rounds, id: \.self) { i in
                            Capsule().fill(i < round ? Color.white : Color.white.opacity(0.2)).frame(height: 5)
                        }
                    }
                    .frame(maxWidth: 160)
                    .padding(.top, 24)
                    .accessibilityElement()
                    .accessibilityLabel(Text("u.breathe.roundOf \(round) \(rounds)"))
                    Spacer()
                }
                Flower(open: reduceMotion ? (inhaling ? 1 : 0.3) : eased)
                    .frame(width: 340, height: 340)
                    .shadow(color: UPalette.gold.opacity(0.45), radius: 40)
                    .accessibilityHidden(true)
                VStack {
                    Spacer()
                    HStack(alignment: .bottom) {
                        Text(inhaling ? "u.breathe.in" : "u.breathe.out")
                            .font(.uDisplay(36))
                            .foregroundStyle(UPalette.ink)
                            .accessibilityAddTraits(.updatesFrequently)
                        Spacer()
                        Button { onClose() } label: {
                            RoundedRectangle(cornerRadius: 4).fill(.white).frame(width: 18, height: 18)
                                .frame(width: 56, height: 56)
                                .background(Color.white.opacity(0.16), in: Circle())
                        }
                        .accessibilityLabel(Text("u.breathe.stop"))
                    }
                    .padding(.horizontal, 28)
                    .padding(.bottom, 36)
                }
            }
            .onChange(of: t >= total) { finished in
                guard finished, case .breathing = phase else { return }
                let sealed = store.finishedBreath()
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                phase = .done(sealed: sealed)
            }
        }
    }

    // MARK: - It passed

    private func done(_ sealed: Bool) -> some View {
        VStack(spacing: 16) {
            topBar(title: nil)
            Spacer()
            Image(sealed ? "glass_gold" : GlassStage.art(stage: GlassStage.stage(givenBackMinutes: store.givenBackMinutes),
                                                         look: store.glass().look, night: false))
                .resizable().scaledToFit().frame(maxWidth: 260)
                .mask(RadialGradient(colors: [.black, .black, .clear], center: .center, startRadius: 0, endRadius: 130))
                .accessibilityHidden(true)
            Text(sealed ? "u.breathe.sealed" : "u.breathe.passed")
                .font(.uDisplay(34)).foregroundStyle(UPalette.ink).multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text(sealed ? "u.glass.goldSub" : "u.breathe.passedSub")
                .font(.subheadline).foregroundStyle(UPalette.dim).multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Text("u.breathe.today \(store.breathsToday())")
                .font(.footnote.weight(.semibold)).foregroundStyle(UPalette.goldLight)
            Spacer()
            UGoldButton(title: "u.done", wide: true, action: onClose)
                .padding(.horizontal, 20).padding(.bottom, 24)
        }
    }

    private func topBar(title: LocalizedStringKey?) -> some View {
        HStack {
            Button(action: onClose) {
                Image(systemName: "xmark").font(.subheadline.weight(.bold)).foregroundStyle(UPalette.ink)
                    .frame(width: 40, height: 40).background(Color.white.opacity(0.12), in: Circle())
            }
            .accessibilityLabel(Text("u.close"))
            Spacer()
            if let title { Text(title).font(.headline.weight(.heavy)).foregroundStyle(UPalette.ink) }
            Spacer()
            Color.clear.frame(width: 40, height: 40)
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
    }
}

/// Eight gold petals that open with the breath.
private struct Flower: View {
    let open: Double

    var body: some View {
        ZStack {
            ForEach(0..<8, id: \.self) { i in
                Ellipse()
                    .fill(LinearGradient(colors: [Color(red: 1, green: 0.89, blue: 0.6).opacity(0.85),
                                                  UPalette.goldDeep.opacity(0.35)],
                                         startPoint: .top, endPoint: .bottom))
                    .overlay { Ellipse().strokeBorder(Color.white.opacity(0.25), lineWidth: 1) }
                    .frame(width: 70 + 40 * open, height: 120 + 30 * open)
                    .offset(y: -(20 + 70 * open))
                    .rotationEffect(.degrees(Double(i) * 45 + 22.5 * open))
                    .blendMode(.plusLighter)
            }
            Circle()
                .fill(RadialGradient(colors: [Color(red: 1, green: 0.97, blue: 0.9), UPalette.gold],
                                     center: .center, startRadius: 0, endRadius: 40))
                .frame(width: 44 + 20 * open, height: 44 + 20 * open)
        }
        .scaleEffect(0.6 + 0.4 * open)
    }
}
