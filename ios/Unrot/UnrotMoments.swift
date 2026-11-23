import SwiftUI

// MARK: - First-open tour

/// The five places the first-open tour lights up, in order (concept: "First open: the tour").
enum TourSpot: Int, CaseIterable {
    case sprout, number, next, tools, breathe
    var titleKey: LocalizedStringKey { uKey("u.tour.title.", rawValue) }
    var bodyKey: LocalizedStringKey { uKey("u.tour.body.", rawValue) }
}

struct TourAnchors: PreferenceKey {
    static var defaultValue: [Int: Anchor<CGRect>] = [:]
    static func reduce(value: inout [Int: Anchor<CGRect>], nextValue: () -> [Int: Anchor<CGRect>]) {
        value.merge(nextValue()) { $1 }
    }
}

extension View {
    /// Marks this view as one stop of the first-open tour.
    func tourSpot(_ spot: TourSpot) -> some View {
        anchorPreference(key: TourAnchors.self, value: .bounds) { [spot.rawValue: $0] }
            .id("tour-\(spot.rawValue)")
    }
}

/// Dims the screen except one rounded spotlight, with a card that explains it.
/// Tap anywhere to go on; it never comes back unless asked for in You.
struct TourOverlay: View {
    let spot: TourSpot
    let rect: CGRect?
    let onNext: () -> Void
    let onSkip: () -> Void

    var body: some View {
        GeometryReader { geo in
            let hole = (rect ?? .zero).insetBy(dx: -10, dy: -10)
            ZStack(alignment: .topLeading) {
                Path { p in
                    p.addRect(CGRect(origin: .zero, size: geo.size).insetBy(dx: -400, dy: -400))
                    if rect != nil { p.addRoundedRect(in: hole, cornerSize: CGSize(width: 26, height: 26)) }
                }
                .fill(Color.black.opacity(0.78), style: FillStyle(eoFill: true))
                .contentShape(Rectangle())
                .onTapGesture(perform: onNext)
                .accessibilityHidden(true)
                card
                    .frame(width: min(geo.size.width - 40, 380))
                    .position(x: geo.size.width / 2,
                              y: cardY(hole: hole, height: geo.size.height))
            }
        }
        .ignoresSafeArea()
        .transition(.opacity)
    }

    private func cardY(hole: CGRect, height: CGFloat) -> CGFloat {
        guard rect != nil else { return height / 2 }
        // Under the spotlight when there is room, above it otherwise.
        return hole.maxY + 110 < height - 40 ? hole.maxY + 100 : max(120, hole.minY - 100)
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(spot.titleKey).font(.uDisplay(21)).foregroundStyle(Color(red: 0.14, green: 0.09, blue: 0.03))
                .accessibilityAddTraits(.isHeader)
            Text(spot.bodyKey).font(.subheadline).foregroundStyle(Color(red: 0.14, green: 0.09, blue: 0.03).opacity(0.85))
                .fixedSize(horizontal: false, vertical: true)
            HStack {
                Text("u.tour.step \(spot.rawValue + 1) \(TourSpot.allCases.count)")
                    .font(.caption).foregroundStyle(Color(red: 0.49, green: 0.43, blue: 0.33))
                Spacer()
                if spot != TourSpot.allCases.last {
                    Button("u.tour.skip", action: onSkip).font(.footnote)
                        .foregroundStyle(Color(red: 0.49, green: 0.43, blue: 0.33))
                }
                Button(action: onNext) {
                    Text(spot == TourSpot.allCases.last ? "u.tour.done" : "u.tour.next")
                        .font(.footnote.weight(.bold)).foregroundStyle(Color(red: 1, green: 0.97, blue: 0.9))
                        .padding(.horizontal, 16).frame(minHeight: 36)
                        .background(Color(red: 0.14, green: 0.09, blue: 0.03), in: Capsule())
                }
            }
        }
        .padding(16)
        .background(Color(red: 1, green: 0.973, blue: 0.902), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .shadow(color: .black.opacity(0.5), radius: 20, y: 10)
        .accessibilityElement(children: .contain)
    }
}

// MARK: - Stage-up moment

/// When the sprout reaches a new stage: the painting, the stage in laurels,
/// what it took, and a share card that never carries adult numbers.
struct UnrotStageUpView: View {
    let stage: Int
    let givenBack: Int
    let day: Int
    let onClose: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var grown = false

    var body: some View {
        ZStack {
            UBackdrop()
            Sparkles().opacity(grown ? 1 : 0).accessibilityHidden(true)
            VStack(spacing: 14) {
                Spacer()
                Text("u.stageUp.eyebrow").font(.caption.weight(.heavy)).tracking(1.6).textCase(.uppercase)
                    .foregroundStyle(UPalette.goldLight)
                Image(GlassStage.art(stage: stage, look: .clear, night: false))
                    .resizable().scaledToFit().frame(maxWidth: 300)
                    .mask(RadialGradient(colors: [.black, .black, .clear], center: .center, startRadius: 0, endRadius: 150))
                    .scaleEffect(grown || reduceMotion ? 1 : 0.82)
                    .shadow(color: UPalette.gold.opacity(0.5), radius: 40)
                    .accessibilityHidden(true)
                HStack(spacing: 10) {
                    Text(verbatim: "❦").foregroundStyle(UPalette.gold).scaleEffect(x: -1)
                    Text(LocalizedStringKey(GlassStage.nameKeys[stage]))
                        .font(.uDisplay(34)).foregroundStyle(UPalette.ink)
                    Text(verbatim: "❦").foregroundStyle(UPalette.gold)
                }
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(.isHeader)
                let given = UnrotFormat.short(minutes: givenBack)
                Text("u.stageUp.body \(given)")
                    .font(.subheadline).foregroundStyle(UPalette.dim).multilineTextAlignment(.center)
                    .padding(.horizontal, 30)
                if let left = GlassStage.minutesToNext(givenBackMinutes: givenBack) {
                    let nextName = Text(LocalizedStringKey(GlassStage.nameKeys[min(stage + 1, GlassStage.last)]))
                    let leftText = UnrotFormat.short(minutes: left)
                    Text("u.stageUp.next \(nextName) \(leftText)")
                        .font(.footnote.weight(.semibold)).foregroundStyle(UPalette.goldLight)
                }
                Spacer()
                ShareCardLink(stage: stage, givenBack: givenBack, day: day)
                    .padding(.horizontal, 20)
                UGoldButton(title: "u.stageUp.keep", wide: true, action: onClose)
                    .padding(.horizontal, 20).padding(.bottom, 20)
            }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            withAnimation(reduceMotion ? nil : .spring(response: 0.7, dampingFraction: 0.6)) { grown = true }
        }
    }
}

private struct Sparkles: View {
    var body: some View {
        Canvas { context, size in
            var generator = SplitMix(seed: 7)
            for _ in 0..<40 {
                let x = Double(generator.next() % 1000) / 1000 * size.width
                let y = Double(generator.next() % 1000) / 1000 * size.height * 0.7
                let r = 1.5 + Double(generator.next() % 30) / 10
                context.fill(Path(ellipseIn: CGRect(x: x, y: y, width: r, height: r)),
                             with: .color(UPalette.goldLight.opacity(0.4 + Double(generator.next() % 60) / 100)))
            }
        }
        .ignoresSafeArea()
    }
}

/// A fixed sequence, so the sparkles do not jump between redraws.
private struct SplitMix {
    var state: UInt64
    init(seed: UInt64) { state = seed }
    mutating func next() -> UInt64 {
        state &+= 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        return z ^ (z >> 31)
    }
}

// MARK: - Share card

/// What a share card may say: growth only. Adult numbers, notes, check-ins and
/// the reason never leave the phone (brainrot-positioning: private by default).
enum ShareCardContent {
    struct Lines: Equatable { let stageKey: String; let givenBack: String; let day: Int }
    static func lines(stage: Int, givenBack: Int, day: Int) -> Lines {
        Lines(stageKey: GlassStage.nameKeys[max(0, min(GlassStage.last, stage))],
              givenBack: UnrotFormat.short(minutes: givenBack), day: day)
    }
}

/// The card as drawn into the shared image.
struct ShareCard: View {
    let stage: Int
    let givenBack: Int
    let day: Int

    var body: some View {
        let lines = ShareCardContent.lines(stage: stage, givenBack: givenBack, day: day)
        VStack(spacing: 14) {
            Text(verbatim: "Unrot").font(.system(size: 22, weight: .bold, design: .serif)).foregroundStyle(UPalette.goldLight)
            Image(GlassStage.art(stage: stage, look: .clear, night: false))
                .resizable().scaledToFit().frame(width: 300)
                .mask(RadialGradient(colors: [.black, .black, .clear], center: .center, startRadius: 0, endRadius: 150))
            Text(LocalizedStringKey(lines.stageKey)).font(.system(size: 30, weight: .semibold, design: .serif))
                .foregroundStyle(UPalette.ink)
            Text("u.share.given \(lines.givenBack)").font(.system(size: 20, weight: .semibold))
                .foregroundStyle(UPalette.ink)
            Text("u.share.day \(lines.day)").font(.system(size: 15)).foregroundStyle(UPalette.dim)
        }
        .padding(36)
        .frame(width: 420, height: 620)
        .background(UBackdrop())
    }
}

/// Renders the card and offers it to the share sheet.
struct ShareCardLink: View {
    let stage: Int
    let givenBack: Int
    let day: Int
    @Environment(\.locale) private var locale
    @Environment(\.layoutDirection) private var direction

    var body: some View {
        if let image = render() {
            ShareLink(item: image, preview: SharePreview(Text("u.share.title"), image: image)) {
                HStack(spacing: 8) {
                    Image(systemName: "square.and.arrow.up")
                    Text("u.share.button").font(.subheadline.weight(.bold))
                }
                .foregroundStyle(UPalette.ink)
                .frame(maxWidth: .infinity, minHeight: 46)
                .background(UPalette.toolFill, in: Capsule())
                .overlay { Capsule().strokeBorder(Color.white.opacity(0.10)) }
            }
            .buttonStyle(UPressStyle())
        }
    }

    @MainActor private func render() -> Image? {
        let renderer = ImageRenderer(content: ShareCard(stage: stage, givenBack: givenBack, day: day)
            .environment(\.locale, locale).environment(\.layoutDirection, direction))
        renderer.scale = 3
        return renderer.uiImage.map { Image(uiImage: $0) }
    }
}
