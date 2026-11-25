import SwiftUI

/// Your wood: every day with Unrot is a tree on the hill, the day the glass
/// broke stays a dry tree, and today is the sapling still growing. The hill is
/// drawn (not a generated picture) and the trees are the painted family shared
/// with Android. It opens Progress, as in the prototype.
struct UnrotWoodCard: View {
    @ObservedObject var store: UnrotStore
    let now: Date
    @State private var sway = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let maxTrees = 24

    private struct Tree { let art: String; let size: Double }

    private var trees: [Tree] {
        let day = max(1, store.day(now))
        let calendar = Calendar.current
        let brokenDay = store.brokenAt > 0 ? UnrotFormat.dayKey(Date(timeIntervalSince1970: store.brokenAt)) : nil
        var list: [Tree] = []
        for number in stride(from: 1, to: day, by: 1) {
            guard let date = calendar.date(byAdding: .day, value: number - day, to: now) else { continue }
            let key = UnrotFormat.dayKey(date)
            if key == brokenDay { list.append(Tree(art: "tree_dry", size: 0.9)); continue }
            let kind = WoodLayout.kind(dayNumber: number)
            let month = calendar.component(.month, from: date)
            let winter = month == 12 || month <= 2
            list.append(Tree(art: kind == "pine" && winter ? "tree_winter" : "tree_" + kind, size: 0.82 + Double((number * 37) % 19) / 100))
        }
        list = Array(list.suffix(Self.maxTrees - 1))
        list.append(Tree(art: "tree_sapling", size: 0.8))
        return list
    }

    var body: some View {
        let trees = self.trees
        VStack(alignment: .leading, spacing: 4) {
            VStack(alignment: .leading, spacing: 2) {
                Text("u.wood.title").font(.uDisplay(22)).foregroundStyle(UPalette.ink)
                    .accessibilityAddTraits(.isHeader)
                Text("u.wood.sub \(trees.count)").font(.caption).foregroundStyle(UPalette.dim)
            }
            .padding(.horizontal, 18).padding(.top, 16)
            Canvas { context, size in
                Self.drawHill(&context, size)
                let spots = WoodLayout.spots(count: trees.count)
                for index in spots.indices.sorted(by: { spots[$0].y < spots[$1].y }) {
                    let spot = spots[index], tree = trees[index]
                    let side = size.width * 0.105 * spot.scale * tree.size * 1.3
                    let root = CGPoint(x: spot.x * size.width, y: spot.y * size.height)
                    var layer = context
                    if !reduceMotion {
                        layer.translateBy(x: root.x, y: root.y)
                        layer.rotate(by: .degrees((sway ? 1.3 : -1.3) * (index % 2 == 0 ? 1 : -1)))
                        layer.translateBy(x: -root.x, y: -root.y)
                    }
                    layer.draw(context.resolve(Image(tree.art)),
                               in: CGRect(x: root.x - side / 2, y: root.y - side * 0.9, width: side, height: side))
                }
            }
            .aspectRatio(360.0 / 300.0, contentMode: .fit)
            .accessibilityLabel(Text("u.wood.sub \(trees.count)"))
            Text("u.wood.today").font(.subheadline.weight(.medium)).foregroundStyle(UPalette.ink)
                .padding(.horizontal, 18).padding(.bottom, 14)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(red: 0.082, green: 0.063, blue: 0.039), in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 3.5).repeatForever(autoreverses: true)) { sway = true }
        }
    }

    private static func rgb(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255).opacity(alpha)
    }

    /// The hill in the picture's own 360 × 300 units: a glow behind the crest, a
    /// far hill, the grass slope, the sandy path (trees keep off it), tufts and flowers.
    private static func drawHill(_ context: inout GraphicsContext, _ size: CGSize) {
        let k = size.width / 360
        func p(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x * k, y: y * k) }
        context.fill(Path(ellipseIn: CGRect(x: 30 * k, y: -26 * k, width: 300 * k, height: 300 * k)),
                     with: .radialGradient(Gradient(colors: [rgb(0xFFD98A, 0.5), rgb(0xF0B93C, 0.16), .clear]),
                                           center: p(180, 124), startRadius: 0, endRadius: 150 * k))
        let stars: [(Double, Double)] = [(22, 30), (58, 62), (96, 22), (128, 54), (226, 34), (262, 66), (300, 24), (338, 58), (40, 96), (318, 100), (166, 18), (204, 70)]
        for (i, s) in stars.enumerated() {
            let r = (i % 3 == 0 ? 1.4 : 0.9) * k
            context.fill(Path(ellipseIn: CGRect(x: s.0 * k - r, y: s.1 * k - r, width: r * 2, height: r * 2)), with: .color(rgb(0xFFE9B0, 0.7)))
        }
        var far = Path()
        far.move(to: p(-20, 178)); far.addQuadCurve(to: p(150, 150), control: p(70, 128)); far.addQuadCurve(to: p(380, 160), control: p(230, 172))
        far.addLine(to: p(380, 300)); far.addLine(to: p(-20, 300)); far.closeSubpath()
        context.fill(far, with: .color(rgb(0x1D3F27, 0.7)))
        var slope = Path()
        slope.move(to: p(-10, 150)); slope.addQuadCurve(to: p(370, 150), control: p(180, 98))
        slope.addLine(to: p(370, 300)); slope.addLine(to: p(-10, 300)); slope.closeSubpath()
        context.fill(slope, with: .linearGradient(Gradient(stops: [.init(color: rgb(0x6AA544), location: 0), .init(color: rgb(0x3F7D38), location: 0.35), .init(color: rgb(0x173A22), location: 1)]),
                                                  startPoint: p(180, 120), endPoint: p(180, 300)))
        var light = Path()
        light.move(to: p(30, 140)); light.addQuadCurve(to: p(330, 140), control: p(180, 104)); light.addQuadCurve(to: p(30, 140), control: p(180, 120)); light.closeSubpath()
        context.fill(light, with: .color(rgb(0xFFF0B0, 0.28)))
        var path = Path()
        path.move(to: p(184, 126))
        path.addCurve(to: p(186, 204), control1: p(172, 152), control2: p(206, 172))
        path.addCurve(to: p(140, 300), control1: p(170, 230), control2: p(136, 250))
        path.addLine(to: p(190, 300))
        path.addCurve(to: p(224, 208), control1: p(186, 258), control2: p(212, 238))
        path.addCurve(to: p(190, 126), control1: p(238, 172), control2: p(198, 152))
        path.closeSubpath()
        context.fill(path, with: .linearGradient(Gradient(colors: [rgb(0xE2C48A, 0.9), rgb(0x8A6A3F, 0.9)]), startPoint: p(180, 126), endPoint: p(180, 300)))
        let tufts: [(Double, Double)] = [(36, 196), (70, 232), (104, 178), (128, 248), (58, 270), (246, 186), (280, 226), (312, 196), (330, 250), (262, 268), (96, 214), (296, 160), (150, 284), (232, 244)]
        for t in tufts {
            var blade = Path()
            for d in [(-3.0, -7.0), (0.0, -9.0), (3.0, -7.0)] { blade.move(to: p(t.0, t.1)); blade.addLine(to: p(t.0 + d.0, t.1 + d.1)) }
            context.stroke(blade, with: .color(rgb(0x9FD070, 0.8)), style: StrokeStyle(lineWidth: 1.3 * k, lineCap: .round))
        }
        let dots: [(Double, Double)] = [(48, 214), (118, 262), (86, 190), (270, 204), (322, 228), (244, 262), (300, 276), (140, 222)]
        for (i, d) in dots.enumerated() {
            context.fill(Path(ellipseIn: CGRect(x: d.0 * k - 1.6 * k, y: d.1 * k - 1.6 * k, width: 3.2 * k, height: 3.2 * k)), with: .color(i % 2 == 0 ? rgb(0xFFF6D8) : rgb(0xFFD76A)))
        }
        context.fill(Path(CGRect(x: 0, y: size.height * 0.8, width: size.width, height: size.height * 0.2)),
                     with: .linearGradient(Gradient(colors: [.clear, rgb(0x15100A)]), startPoint: CGPoint(x: 0, y: size.height * 0.8), endPoint: CGPoint(x: 0, y: size.height)))
    }
}
