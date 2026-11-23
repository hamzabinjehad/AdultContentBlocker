import SwiftUI

/// CleanPath's home hero with Unrot inside it: the painted sprout under glass,
/// its stage in laurels, one big number (time given back, never reset) and the
/// next stage with a segmented bar. The picture follows the glass first (cracked,
/// gold, broken), then the night, then the stage.
struct UnrotGlassHero: View {
    let givenBack: Int
    let glass: GlassState
    let night: Bool
    let nightEnds: String?
    let measuring: Bool
    let onInfo: () -> Void
    let onBreathe: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var breathe = false

    private var stage: Int { GlassStage.stage(givenBackMinutes: givenBack) }

    var body: some View {
        VStack(spacing: 0) {
            art.tourSpot(.sprout)
            dots.padding(.top, 2)
            laurel.padding(.top, 12)
            subline.padding(.top, 4)
            bigNumber.padding(.top, 2).tourSpot(.number)
            footer.padding(.top, 10).tourSpot(.next)
        }
        .frame(maxWidth: .infinity)
        .onAppear { breathe = true }
    }

    // MARK: - Picture

    private var art: some View {
        Image(GlassStage.art(stage: stage, look: glass.look, night: night))
            .resizable()
            .scaledToFit()
            .frame(maxWidth: 300)
            .mask(RadialGradient(colors: [.black, .black, .clear], center: .center, startRadius: 0, endRadius: 150))
            .scaleEffect(breathe && !reduceMotion && glass.look == .clear ? 1.02 : 1)
            .animation(reduceMotion ? nil : .easeInOut(duration: 4).repeatForever(autoreverses: true), value: breathe)
            .shadow(color: glowColor.opacity(0.35), radius: 40)
            .accessibilityLabel(Text("u.glass.image \(stageName)"))
            .accessibilityAddTraits(.isImage)
    }

    private var glowColor: Color {
        switch glass.look {
        case .cracked: return UPalette.warn
        case .broken: return .gray
        case .gold: return UPalette.gold
        case .clear: return night ? UPalette.night : UPalette.gold
        }
    }

    private var dots: some View {
        HStack(spacing: 5) {
            ForEach(0...GlassStage.last, id: \.self) { i in
                Capsule()
                    .fill(i <= stage ? Color.white : Color.white.opacity(0.28))
                    .frame(width: i == stage ? 18 : 5, height: 5)
            }
        }
        .accessibilityHidden(true)
    }

    // MARK: - Words

    private var stageName: Text {
        if night && glass.look == .clear { return Text("u.glass.resting") }
        switch glass.look {
        case .cracked: return Text("u.glass.cracked")
        case .gold: return Text("u.glass.gold")
        case .broken: return Text("u.glass.broken")
        case .clear: return Text(LocalizedStringKey(GlassStage.nameKeys[stage]))
        }
    }

    private var laurel: some View {
        HStack(spacing: 10) {
            Text(verbatim: "❦").foregroundStyle(UPalette.gold).scaleEffect(x: -1)
            stageName
                .font(.subheadline.weight(.heavy))
                .tracking(1.2)
                .textCase(.uppercase)
                .foregroundStyle(.white)
            Text(verbatim: "❦").foregroundStyle(UPalette.gold)
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    @ViewBuilder private var subline: some View {
        switch glass.look {
        case .cracked:
            Text("u.glass.crackedHint")
                .font(.footnote.weight(.semibold)).foregroundStyle(UPalette.goldLight)
                .multilineTextAlignment(.center)
        case .gold:
            Text("u.glass.goldSub").font(.footnote).foregroundStyle(UPalette.dim).multilineTextAlignment(.center)
        case .broken:
            Text("u.glass.brokenSub").font(.footnote).foregroundStyle(UPalette.dim).multilineTextAlignment(.center)
        case .clear:
            if night, let nightEnds {
                Text("u.glass.restingSub \(nightEnds)").font(.footnote).foregroundStyle(UPalette.night)
            } else {
                Button(action: onInfo) {
                    HStack(spacing: 5) {
                        Text("u.glass.givenBack")
                        Image(systemName: "info.circle")
                    }
                    .font(.footnote)
                    .foregroundStyle(UPalette.dim)
                }
                .accessibilityHint(Text("u.glass.info"))
            }
        }
    }

    private var bigNumber: some View {
        let p = UnrotFormat.parts(minutes: givenBack)
        return HStack(alignment: .firstTextBaseline, spacing: 2) {
            if glass.look == .cracked {
                Text(verbatim: UnrotFormat.clock(seconds: glass.sealLeft))
                    .font(.uDisplay(48)).foregroundStyle(UPalette.goldLight)
                    .monospacedDigit()
            } else {
                part(p.d, "u.unit.d")
                part(p.h, "u.unit.h")
                part(p.m, "u.unit.m")
            }
        }
        .foregroundStyle(UPalette.ink)
        .lineLimit(1)
        .minimumScaleFactor(0.4)
        .padding(.horizontal, 12)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(glass.look == .cracked
                            ? Text("u.glass.crackedSub \(UnrotFormat.clock(seconds: glass.sealLeft))")
                            : Text("u.glass.timerA11y \(UnrotFormat.short(minutes: givenBack))"))
    }

    private func part(_ value: Int, _ unit: LocalizedStringKey) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 1) {
            Text(verbatim: "\(value)").font(.uDisplay(46)).monospacedDigit()
            Text(unit).font(.uDisplay(24)).foregroundStyle(UPalette.dim).padding(.trailing, 6)
        }
    }

    @ViewBuilder private var footer: some View {
        switch glass.look {
        case .cracked:
            VStack(spacing: 10) {
                Text("u.glass.nothingLost").font(.footnote).foregroundStyle(UPalette.dim)
                UGoldButton(title: "u.glass.sealButton", systemImage: "wind", pulse: true, action: onBreathe)
            }
        case .broken:
            EmptyView()
        default:
            if let left = GlassStage.minutesToNext(givenBackMinutes: givenBack) {
                VStack(spacing: 8) {
                    HStack(spacing: 6) {
                        Text(LocalizedStringKey(GlassStage.nameKeys[stage + 1]))
                            .font(.caption.weight(.heavy)).tracking(0.8).textCase(.uppercase)
                            .foregroundStyle(.white)
                        Text("u.glass.next \(UnrotFormat.short(minutes: left))")
                            .font(.caption).foregroundStyle(UPalette.dim)
                    }
                    segments
                    if measuring {
                        Text("u.glass.measuring").font(.caption2).foregroundStyle(UPalette.dim)
                            .multilineTextAlignment(.center)
                    }
                }
                .padding(.horizontal, 28)
            } else {
                Text("u.glass.top").font(.footnote).foregroundStyle(UPalette.goldLight)
            }
        }
    }

    /// Six steps between the seven stages: done ones full, the current one part-filled.
    private var segments: some View {
        let part = GlassStage.part(givenBackMinutes: givenBack)
        return HStack(spacing: 4) {
            ForEach(0..<GlassStage.last, id: \.self) { i in
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.white.opacity(0.1))
                        Capsule()
                            .fill(LinearGradient(colors: [Color(red: 0.788, green: 0.553, blue: 0.133), UPalette.goldLight],
                                                 startPoint: .leading, endPoint: .trailing))
                            .frame(width: geo.size.width * (i < stage ? 1 : i == stage ? part : 0))
                    }
                }
                .frame(height: 6)
            }
        }
        .accessibilityHidden(true)
    }
}
