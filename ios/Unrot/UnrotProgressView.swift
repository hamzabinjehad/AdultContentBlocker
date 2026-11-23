import SwiftUI

/// Progress, like CleanPath's analytics with Unrot's garden: the gauge (feed
/// time against the person's before), two tiles, the garden of the last seven
/// days, the week's feed bars, the calm meters and the private "kept out" card.
/// A heavy day is a small plant, never a dead one; nothing here is ever wiped.
struct UnrotProgressView: View {
    @ObservedObject var store: UnrotStore
    let facts: UnrotShieldFacts
    let now: Date
    @State private var explain = false
    @Environment(\.horizontalSizeClass) private var sizeClass

    private var week: [Date] { UnrotFormat.lastDays(7, now: now) }
    private var measuredWeek: [Int] {
        week.dropLast().compactMap { store.feed[UnrotFormat.dayKey($0)] }
    }
    private var nowAverage: Int? {
        measuredWeek.isEmpty ? nil : measuredWeek.reduce(0, +) / measuredWeek.count
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                UTitleBar(title: "u.tab.progress") {
                    Button { explain = true } label: {
                        Image(systemName: "questionmark.circle").font(.title3).foregroundStyle(UPalette.ink)
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel(Text("u.progress.explain"))
                }
                gauge
                tiles
                garden
                bars
                meters
                keptOut
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 120)
            .frame(maxWidth: sizeClass == .regular ? 560 : .infinity)
            .frame(maxWidth: .infinity)
        }
        .scrollIndicators(.hidden)
        .sheet(isPresented: $explain) { explainer }
    }

    // MARK: - Gauge

    private var gauge: some View {
        let before = store.answers.beforeMinutes
        let percent = nowAverage.map { GivenBack.percent(before: before, now: $0) }
        let nowText = nowAverage.map { UnrotFormat.short(minutes: $0) } ?? "—"
        return Button { explain = true } label: {
            HStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 0) {
                    if let percent {
                        Text(verbatim: "\(abs(percent))").font(.uDisplay(54, weight: .bold))
                        + Text(verbatim: "%").font(.uDisplay(24))
                    } else {
                        Text(verbatim: "—").font(.uDisplay(54, weight: .bold))
                    }
                }
                .foregroundStyle(.white)
                VStack(alignment: .leading, spacing: 4) {
                    Text(percent.map { $0 <= 0 ? "u.gauge.less" : "u.gauge.more" } ?? "u.gauge.waiting")
                        .font(.caption.weight(.heavy)).tracking(1).textCase(.uppercase).foregroundStyle(.white)
                    Text("u.gauge.detail \(UnrotFormat.short(minutes: before)) \(nowText)")
                        .font(.caption).foregroundStyle(Color(red: 0.91, green: 0.86, blue: 0.76))
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 24).padding(.vertical, 20)
            .background(LinearGradient(colors: [Color(red: 0.561, green: 0.878, blue: 0.416).opacity(0.10),
                                                UPalette.gold.opacity(0.28)],
                                       startPoint: .leading, endPoint: .trailing), in: Capsule())
            .overlay { Capsule().strokeBorder(UPalette.gold.opacity(0.3), lineWidth: 1.5) }
        }
        .buttonStyle(UPressStyle())
        .accessibilityElement(children: .combine)
    }

    private var tiles: some View {
        HStack(spacing: 10) {
            tile(value: "\(store.day(now))", label: "u.tile.days")
            tile(value: store.checkedInToday.map { _ in "✓" } ?? "—", label: "u.tile.checkin")
            tile(value: UnrotFormat.short(minutes: store.givenBackMinutes), label: "u.tile.givenBack")
        }
    }

    private func tile(value: String, label: LocalizedStringKey) -> some View {
        VStack(spacing: 2) {
            Text(verbatim: value).font(.uDisplay(24)).foregroundStyle(UPalette.ink).lineLimit(1).minimumScaleFactor(0.6)
            Text(label).font(.caption2).foregroundStyle(UPalette.dim).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, minHeight: 74)
        .padding(.horizontal, 6)
        .background(LinearGradient(colors: [Color(red: 0.165, green: 0.149, blue: 0.133),
                                            Color(red: 0.102, green: 0.090, blue: 0.078)],
                                   startPoint: .top, endPoint: .bottom),
                    in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    // MARK: - Garden

    private var garden: some View {
        UCard {
            ULabel("u.garden.title")
            HStack(alignment: .bottom, spacing: 6) {
                ForEach(week, id: \.self) { date in
                    let key = UnrotFormat.dayKey(date)
                    let isToday = key == store.today(now)
                    let gain = store.gains[key]
                    VStack(spacing: 4) {
                        Pot(size: plantSize(gain: gain, today: isToday))
                            .frame(height: 64)
                        Text(date.formatted(.dateTime.weekday(.narrow)))
                            .font(.caption2.weight(isToday ? .bold : .regular))
                            .foregroundStyle(isToday ? UPalette.goldLight : UPalette.dim)
                    }
                    .frame(maxWidth: .infinity)
                    .accessibilityElement()
                    .accessibilityLabel(Text(date.formatted(.dateTime.weekday(.wide))))
                    .accessibilityValue(gain.map { Text("u.garden.gain \(UnrotFormat.short(minutes: $0))") }
                                        ?? Text(isToday ? "u.garden.growing" : "u.garden.none"))
                }
            }
            Text("u.garden.note").font(.caption).foregroundStyle(UPalette.dim)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// 0 = empty pot, then up to 1 by the day's gain against the person's before.
    private func plantSize(gain: Int?, today: Bool) -> Double {
        if today { return 0.35 }
        guard let gain else { return 0 }
        let before = max(1, store.answers.beforeMinutes)
        return 0.2 + 0.8 * min(1, Double(gain) / Double(before) * 2)
    }

    // MARK: - Week bars

    private var bars: some View {
        let values = week.map { store.feed[UnrotFormat.dayKey($0)] }
        let top = max(store.answers.beforeMinutes, values.compactMap { $0 }.max() ?? 0, 60)
        return UCard {
            ULabel("u.week.title")
            HStack(alignment: .bottom, spacing: 8) {
                ForEach(Array(zip(week, values)), id: \.0) { date, value in
                    VStack(spacing: 4) {
                        if let value {
                            RoundedRectangle(cornerRadius: 7, style: .continuous)
                                .fill(LinearGradient(colors: [UPalette.goldLight, Color(red: 0.788, green: 0.553, blue: 0.133)],
                                                     startPoint: .top, endPoint: .bottom))
                                .frame(height: max(6, 110 * Double(value) / Double(top)))
                        } else {
                            RoundedRectangle(cornerRadius: 7, style: .continuous)
                                .strokeBorder(Color.white.opacity(0.2), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                                .frame(height: 110)
                        }
                        Text(date.formatted(.dateTime.weekday(.narrow))).font(.caption2).foregroundStyle(UPalette.dim)
                    }
                    .frame(maxWidth: .infinity)
                    .accessibilityElement()
                    .accessibilityLabel(Text(date.formatted(.dateTime.weekday(.wide))))
                    .accessibilityValue(Text(verbatim: value.map { UnrotFormat.short(minutes: $0) } ?? "—"))
                }
            }
            .frame(height: 130, alignment: .bottom)
            .overlay(alignment: .bottom) {
                if store.answers.beforeMinutes > 0 {
                    GeometryReader { geo in
                        let y = 110 * Double(store.answers.beforeMinutes) / Double(top)
                        Path { p in
                            p.move(to: CGPoint(x: 0, y: geo.size.height - 20 - y))
                            p.addLine(to: CGPoint(x: geo.size.width, y: geo.size.height - 20 - y))
                        }
                        .stroke(Color.white.opacity(0.7), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
                    }
                    .accessibilityHidden(true)
                }
            }
            Text(facts.feedWatched ? "u.week.legend" : "u.feed.notWatched")
                .font(.caption).foregroundStyle(UPalette.dim).fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: - Meters

    private var meters: some View {
        let breaths = week.reduce(0) { $0 + (store.breaths[UnrotFormat.dayKey($1)] ?? 0) }
        let checkIns = week.filter { store.checkIns[UnrotFormat.dayKey($0)] != nil }.count
        return UCard {
            ULabel("u.meters.title")
            meter(icon: "wind", tint: UPalette.gold, title: "u.meter.calm", value: "\(breaths)", part: Double(breaths) / 14)
            meter(icon: "checkmark.circle", tint: UPalette.leaf, title: "u.meter.checkins",
                  value: "\(checkIns)/7", part: Double(checkIns) / 7)
            meter(icon: "scope", tint: UPalette.night, title: "u.meter.focus",
                  value: UnrotFormat.short(minutes: store.focusMinutes), part: Double(store.focusMinutes) / 600)
        }
    }

    private func meter(icon: String, tint: Color, title: LocalizedStringKey, value: String, part: Double) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon).foregroundStyle(tint).font(.body.weight(.semibold))
                .frame(width: 40, height: 40)
                .background(tint.opacity(0.14), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(title).font(.subheadline.weight(.bold)).foregroundStyle(UPalette.ink)
                    Spacer()
                    Text(verbatim: value).font(.subheadline).foregroundStyle(UPalette.dim)
                }
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.white.opacity(0.08))
                        Capsule().fill(tint).frame(width: geo.size.width * min(1, max(0.02, part)))
                    }
                }
                .frame(height: 8)
            }
        }
        .accessibilityElement(children: .combine)
    }

    // MARK: - Kept out (private)

    private var keptOut: some View {
        UCard {
            HStack(spacing: 8) {
                Image(systemName: "shield.lefthalf.filled").foregroundStyle(UPalette.leaf)
                ULabel("u.kept.title")
            }
            HStack(spacing: 10) {
                keptTile("\([UnrotShieldFacts.Pane.safari, .everyApp, .screenTime].filter { facts.on[$0] == true }.count)/3",
                         "u.kept.layers")
                keptTile("\(store.triesThisWeek(now))", "u.kept.tries")
                keptTile("\(store.seams)", "u.kept.seams")
            }
            Label("u.kept.private", systemImage: "lock.fill").font(.caption).foregroundStyle(UPalette.dim)
        }
    }

    private func keptTile(_ value: String, _ label: LocalizedStringKey) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(verbatim: value).font(.uDisplay(22)).foregroundStyle(UPalette.ink)
            Text(label).font(.caption2).foregroundStyle(UPalette.dim).fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(Color.white.opacity(0.05), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    private var explainer: some View {
        USheet(title: "u.explain.title") {
            Text("u.explain.gauge").foregroundStyle(UPalette.ink).fixedSize(horizontal: false, vertical: true)
            ULabel("u.explain.slipTitle")
            Text("u.explain.slip").foregroundStyle(UPalette.ink).fixedSize(horizontal: false, vertical: true)
            Text("u.info.iphone").font(.footnote).foregroundStyle(UPalette.dim).fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// A pot with a plant whose size is the day's gain. Never dead.
private struct Pot: View {
    let size: Double

    var body: some View {
        Canvas { context, canvas in
            let w = canvas.width, h = canvas.height
            let potTop = h * 0.68
            var pot = Path()
            pot.move(to: CGPoint(x: w * 0.22, y: potTop))
            pot.addLine(to: CGPoint(x: w * 0.78, y: potTop))
            pot.addLine(to: CGPoint(x: w * 0.68, y: h))
            pot.addLine(to: CGPoint(x: w * 0.32, y: h))
            pot.closeSubpath()
            context.fill(pot, with: .color(Color(red: 0.70, green: 0.42, blue: 0.24)))
            guard size > 0 else { return }
            let stem = potTop * size * 0.9
            var line = Path()
            line.move(to: CGPoint(x: w / 2, y: potTop))
            line.addLine(to: CGPoint(x: w / 2, y: potTop - stem))
            context.stroke(line, with: .color(Color(red: 0.36, green: 0.62, blue: 0.33)), lineWidth: 2.5)
            let leaf = max(5, 16 * size)
            for (dx, dy) in [(-1.0, 0.55), (1.0, 0.8)] {
                let y = potTop - stem * dy
                let rect = CGRect(x: w / 2 + (dx < 0 ? -leaf : 0), y: y - leaf * 0.35, width: leaf, height: leaf * 0.7)
                context.fill(Path(ellipseIn: rect), with: .color(UPalette.leaf))
            }
            if size >= 0.85 {
                context.fill(Path(ellipseIn: CGRect(x: w / 2 - 5, y: potTop - stem - 5, width: 10, height: 10)),
                             with: .color(UPalette.gold))
            }
        }
    }
}
