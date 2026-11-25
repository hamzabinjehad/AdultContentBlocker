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
    /// Shown under the bar until the first whole day is counted.
    let hint: LocalizedStringKey?
    let onInfo: () -> Void
    let onBreathe: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var breathe = false
    @State private var counted = false
    @MainActor private static var hasCounted = false

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

    /// Below the tree, the hurt glass is drawn live: the stage's own painting with the
    /// crack (or its gold seam, or the break) drawn over it, so the plant keeps its size.
    /// Off since the painted glass (the owner's pick of 2026-11-24): cracked, gold and
    /// broken each have their own painting, so nothing is drawn over the jar.
    private var liveHurt: Bool { false }

    private var art: some View {
        Image(liveHurt ? GlassStage.art(stage: stage, look: .clear, night: false)
                       : GlassStage.art(stage: stage, look: glass.look, night: night))
            .resizable()
            .scaledToFit()
            .saturation(liveHurt && glass.look == .broken ? 0.35 : 1)
            .brightness(liveHurt && glass.look == .cracked ? -0.06 : 0)
            .overlay { if liveHurt { GlassCracks(look: glass.look) } }
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
        return HStack(alignment: .firstTextBaseline, spacing: 2) {
            if glass.look == .cracked {
                Text(verbatim: UnrotFormat.clock(seconds: glass.sealLeft))
                    .font(.uDisplay(48)).foregroundStyle(UPalette.goldLight)
                    .monospacedDigit()
            } else {
                CountingTime(minutes: Double(counted ? givenBack : 0))
                    .animation(reduceMotion ? nil : .easeOut(duration: 1.1), value: counted)
                    .onAppear {
                        // Counts up once a session; afterwards it just shows.
                        guard !Self.hasCounted else { counted = true; return }
                        Self.hasCounted = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { counted = true }
                    }
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
                    if let hint {
                        Text(hint).font(.caption2).foregroundStyle(UPalette.dim)
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

/// "0d 13h 55m" that can count up: SwiftUI animates `minutes` and each frame
/// shows the whole minutes reached so far.
private struct CountingTime: View, Animatable {
    var minutes: Double
    var animatableData: Double {
        get { minutes }
        set { minutes = newValue }
    }

    var body: some View {
        let p = UnrotFormat.parts(minutes: Int(minutes.rounded()))
        HStack(alignment: .firstTextBaseline, spacing: 2) {
            part(p.d, "u.unit.d")
            part(p.h, "u.unit.h")
            part(p.m, "u.unit.m")
        }
    }

    private func part(_ value: Int, _ unit: LocalizedStringKey) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 1) {
            Text(verbatim: "\(value)").font(.uDisplay(46)).monospacedDigit()
            Text(unit).font(.uDisplay(24)).foregroundStyle(UPalette.dim).padding(.trailing, 6)
        }
    }
}

/// The crack across the glass, in the painting's 0...100 square (the same lines as
/// design/glasshouse-2026-11-24-home and Android's GlassCracks): a dark line with a
/// white edge; sealed, a shimmering gold seam; broken, more lines and shards.
struct GlassCracks: View {
    let look: GlassState.Look
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let crack: [[CGFloat]] = [
        [60, 36, 55, 30, 52, 24], [60, 36, 66, 31, 70, 27], [60, 36, 63, 43, 61, 50, 64, 57],
        [60, 36, 54, 40, 48, 39, 43, 44], [60, 36, 67, 38, 73, 42], [55, 30, 51, 32]
    ]
    private static let more: [[CGFloat]] = [[60, 36, 58, 48, 50, 56], [60, 36, 72, 50]]
    private static let shards: [[CGFloat]] = [
        [30, 74, 35, 71, 37, 75], [66, 75, 72, 73, 71, 77], [44, 76, 47, 72, 50, 75], [58, 77, 62, 74, 63, 77]
    ]

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 30, paused: reduceMotion || look != .gold)) { time in
            Canvas { ctx, size in
                let side = min(size.width, size.height)
                let u = side / 100
                let o = CGPoint(x: (size.width - side) / 2, y: (size.height - side) / 2)
                func path(_ p: [CGFloat], close: Bool = false) -> Path {
                    var path = Path()
                    path.move(to: CGPoint(x: o.x + p[0] * u, y: o.y + p[1] * u))
                    stride(from: 2, to: p.count, by: 2).forEach { path.addLine(to: CGPoint(x: o.x + p[$0] * u, y: o.y + p[$0 + 1] * u)) }
                    if close { path.closeSubpath() }
                    return path
                }
                let gold = look == .gold
                let t = reduceMotion ? 0 : time.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 2.4) / 2.4
                for line in Self.crack + (look == .broken ? Self.more : []) {
                    let p = path(line)
                    ctx.stroke(p, with: .color(gold ? Color(red: 0.48, green: 0.29, blue: 0.03) : Color(red: 0.08, green: 0.05, blue: 0.03).opacity(0.7)),
                               style: StrokeStyle(lineWidth: u * (gold ? 0.8 : 0.6), lineCap: .round, lineJoin: .round))
                    if gold {
                        ctx.stroke(p, with: .color(UPalette.gold.opacity(0.35)), style: StrokeStyle(lineWidth: u * 1.4, lineCap: .round))
                        ctx.stroke(p, with: .color(Color(red: 1, green: 0.82, blue: 0.35)),
                                   style: StrokeStyle(lineWidth: u * 0.45, lineCap: .round, dash: [u * 4, u * 1.2], dashPhase: -CGFloat(t) * u * 5.2))
                    } else {
                        ctx.stroke(p, with: .color(.white.opacity(0.85)), style: StrokeStyle(lineWidth: u * 0.3, lineCap: .round))
                    }
                }
                if look == .broken {
                    for s in Self.shards {
                        let p = path(s, close: true)
                        ctx.fill(p, with: .color(Color(red: 0.87, green: 0.9, blue: 0.93).opacity(0.55)))
                        ctx.stroke(p, with: .color(.white.opacity(0.8)), lineWidth: u * 0.2)
                    }
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
