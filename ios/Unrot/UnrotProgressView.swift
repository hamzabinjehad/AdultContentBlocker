import Charts
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
    @State private var range: ChartRange = .week
    @State private var gardenMonth = false
    @State private var pathOpen = false
    private static let trendIcons = ["arrow.down.right", "arrow.up.right"]
    @State private var scrubDay: Date?
    @State private var openDay: DayRef?
    var onOpenNotes: (Date) -> Void = { _ in }
    var onCheckIn: () -> Void = {}
    @Environment(\.horizontalSizeClass) private var sizeClass

    private var week: [Date] { UnrotFormat.lastDays(7, now: now) }
    private var measuredWeek: [Int] {
        week.dropLast().compactMap { store.feed[UnrotFormat.dayKey($0)] }
    }
    private var nowAverage: Int? {
        measuredWeek.isEmpty ? nil : measuredWeek.reduce(0, +) / measuredWeek.count
    }

    /// Growth only: stage, time given back and the day. Never adult numbers.
    @MainActor private var shareImage: Image? {
        let renderer = ImageRenderer(content: ShareCard(stage: GlassStage.stage(givenBackMinutes: store.givenBackMinutes),
                                                        givenBack: store.givenBackMinutes, day: store.day(now)))
        renderer.scale = 3
        return renderer.uiImage.map { Image(uiImage: $0) }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                UTitleBar(title: "u.tab.progress") {
                    ShareLink(item: shareImage ?? Image(systemName: "leaf"),
                              preview: SharePreview(Text("u.share.title"), image: shareImage ?? Image(systemName: "leaf"))) {
                        Image(systemName: "square.and.arrow.up").font(.title3).foregroundStyle(UPalette.ink)
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel(Text("u.share.button"))
                    Button { explain = true } label: {
                        Image(systemName: "questionmark.circle").font(.title3).foregroundStyle(UPalette.ink)
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel(Text("u.progress.explain"))
                }
                gauge
                tiles
                garden
                path
                bars
                meters
                moodWeek
                keptOut
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 120)
            .frame(maxWidth: sizeClass == .regular ? 560 : .infinity)
            .frame(maxWidth: .infinity)
        }
        .scrollIndicators(.hidden)
        .sheet(isPresented: $explain) { explainer }
        .sheet(item: $openDay) { ref in
            UnrotDayView(store: store, date: ref.date) {
                let day = ref.date
                openDay = nil
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) { onOpenNotes(day) }
            }
        }
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
            Button(action: onCheckIn) {
                // ✓ once checked in today; + invites the check-in.
                tile(value: store.checkedInToday == nil ? "+" : "✓", label: "u.tile.checkin")
            }
            .buttonStyle(UPressStyle())
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
            HStack {
                ULabel(gardenMonth ? "u.garden.monthTitle" : "u.garden.title")
                Spacer()
                Button {
                    withAnimation(.easeInOut(duration: 0.25)) { gardenMonth.toggle() }
                } label: {
                    Label(gardenMonth ? "u.garden.showWeek" : "u.garden.showMonth",
                          systemImage: gardenMonth ? "rectangle.split.3x1" : "calendar")
                        .font(.caption.weight(.bold)).foregroundStyle(UPalette.goldLight)
                }
            }
            if gardenMonth { monthGarden } else { weekGarden }
            Text("u.garden.note").font(.caption).foregroundStyle(UPalette.dim)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// The whole month as small pots under their weekdays; tap any past day.
    private var monthGarden: some View {
        let cal = Calendar.current
        let columns = Array(repeating: GridItem(.flexible(), spacing: 2), count: 7)
        return VStack(spacing: 6) {
            Text(now.formatted(.dateTime.month(.wide).year())).font(.footnote.weight(.semibold)).foregroundStyle(UPalette.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
            LazyVGrid(columns: columns, spacing: 6) {
                ForEach(Array(NotesCalendar.weekdaySymbols(calendar: cal).enumerated()), id: \.offset) { _, symbol in
                    Text(verbatim: symbol).font(.caption2.weight(.bold)).foregroundStyle(UPalette.dim).accessibilityHidden(true)
                }
                ForEach(Array(NotesCalendar.monthGrid(for: now, calendar: cal).enumerated()), id: \.offset) { _, day in
                    if let day {
                        let key = UnrotFormat.dayKey(day)
                        let future = key > store.today(now)
                        Button { openDay = DayRef(date: day) } label: {
                            VStack(spacing: 1) {
                                Pot(size: future ? 0 : plantSize(gain: store.gains[key], today: key == store.today(now)))
                                    .frame(width: 30, height: 30)
                                    .opacity(future ? 0.25 : 1)
                                Text(verbatim: "\(cal.component(.day, from: day))").font(.system(size: 9))
                                    .foregroundStyle(key == store.today(now) ? UPalette.goldLight : UPalette.dim)
                            }
                            .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.plain)
                        .disabled(future)
                        .accessibilityLabel(Text(day.formatted(date: .complete, time: .omitted)))
                        .accessibilityValue(store.gains[key].map { Text("u.garden.gain \(UnrotFormat.short(minutes: $0))") } ?? Text(verbatim: ""))
                    } else {
                        Color.clear.frame(height: 40)
                    }
                }
            }
        }
    }

    private var weekGarden: some View {
            HStack(alignment: .bottom, spacing: 6) {
                ForEach(week, id: \.self) { date in
                    let key = UnrotFormat.dayKey(date)
                    let isToday = key == store.today(now)
                    let gain = store.gains[key]
                    Button { openDay = DayRef(date: date) } label: {
                    VStack(spacing: 4) {
                        Pot(size: plantSize(gain: gain, today: isToday))
                            .frame(height: 64)
                        Text(date.formatted(.dateTime.weekday(.narrow)))
                            .font(.caption2.weight(isToday ? .bold : .regular))
                            .foregroundStyle(isToday ? UPalette.goldLight : UPalette.dim)
                    }
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                    }
                    .buttonStyle(UPressStyle())
                    .accessibilityElement()
                    .accessibilityLabel(Text(date.formatted(.dateTime.weekday(.wide))))
                    .accessibilityAddTraits(.isButton)
                    .accessibilityValue(gain.map { Text("u.garden.gain \(UnrotFormat.short(minutes: $0))") }
                                        ?? Text(isToday ? "u.garden.growing" : "u.garden.none"))
                }
            }
    }

    // MARK: - Your path

    /// The seven stages, the day each was reached (tap opens it) and how far the next is.
    private var path: some View {
        let steps = StagePath.steps(gains: store.gains, startDay: store.startedAt.map { UnrotFormat.dayKey($0) })
        let current = GlassStage.stage(givenBackMinutes: store.givenBackMinutes)
        return UCard {
            HStack {
                ULabel("u.path.title")
                Spacer()
                Button {
                    withAnimation(.easeInOut(duration: 0.25)) { pathOpen.toggle() }
                } label: {
                    Text(pathOpen ? "u.path.less" : "u.path.all").font(.caption.weight(.bold)).foregroundStyle(UPalette.goldLight)
                }
            }
            if !pathOpen { compactPath(steps: steps, current: current) }
            if pathOpen {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(steps, id: \.stage) { step in
                    let reachedDate = step.reached.flatMap { UnrotFormat.date(fromKey: $0) }
                    Button { if let reachedDate { openDay = DayRef(date: reachedDate) } } label: {
                        HStack(alignment: .center, spacing: 12) {
                            ZStack {
                                Circle().fill(step.stage <= current ? AnyShapeStyle(UPalette.goldFill) : AnyShapeStyle(Color.white.opacity(0.08)))
                                Image(systemName: step.stage <= current ? "checkmark" : step.stage == current + 1 ? "leaf" : "lock.fill")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(step.stage <= current ? UPalette.onGold : UPalette.dim)
                            }
                            .frame(width: 30, height: 30)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(LocalizedStringKey(GlassStage.nameKeys[step.stage])).font(.subheadline.weight(.bold))
                                    .foregroundStyle(step.stage <= current + 1 ? UPalette.ink : UPalette.dim)
                                if let reachedDate {
                                    Text(reachedDate.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated)))
                                        .font(.caption).foregroundStyle(UPalette.dim)
                                } else if step.stage == current + 1, let left = GlassStage.minutesToNext(givenBackMinutes: store.givenBackMinutes) {
                                    Text("u.glass.next \(UnrotFormat.short(minutes: left))").font(.caption).foregroundStyle(UPalette.goldLight)
                                } else {
                                    Text(verbatim: UnrotFormat.short(minutes: GlassStage.thresholds[step.stage]))
                                        .font(.caption).foregroundStyle(UPalette.dim.opacity(0.7))
                                }
                            }
                            Spacer()
                            if reachedDate != nil {
                                Image(systemName: "chevron.forward").font(.caption2.weight(.bold)).foregroundStyle(UPalette.dim)
                            }
                        }
                        .padding(.vertical, 6)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(reachedDate == nil)
                    .accessibilityElement(children: .combine)
                    if step.stage < GlassStage.last {
                        Rectangle().fill(step.stage < current ? UPalette.gold : Color.white.opacity(0.1))
                            .frame(width: 2, height: 10).padding(.leading, 14)
                            .accessibilityHidden(true)
                    }
                }
            }
            }
        }
    }

    /// Seven dots on one line, the current stage named, and the next one's distance.
    private func compactPath(steps: [StagePath.Step], current: Int) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 0) {
                ForEach(steps, id: \.stage) { step in
                    Circle()
                        .fill(step.stage <= current ? AnyShapeStyle(UPalette.goldFill) : AnyShapeStyle(Color.white.opacity(0.12)))
                        .frame(width: step.stage == current ? 18 : 10, height: step.stage == current ? 18 : 10)
                        .overlay { if step.stage == current { Circle().stroke(UPalette.gold.opacity(0.35), lineWidth: 5) } }
                    if step.stage < GlassStage.last {
                        Rectangle().fill(step.stage < current ? UPalette.gold : Color.white.opacity(0.12)).frame(height: 2)
                    }
                }
            }
            .padding(.horizontal, 4)
            .accessibilityHidden(true)
            HStack(alignment: .firstTextBaseline) {
                Text(LocalizedStringKey(GlassStage.nameKeys[current])).font(.headline).foregroundStyle(UPalette.ink)
                if let key = steps[current].reached, let date = UnrotFormat.date(fromKey: key) {
                    Text(date.formatted(.dateTime.day().month(.abbreviated))).font(.caption).foregroundStyle(UPalette.dim)
                }
                Spacer()
                if current < GlassStage.last, let left = GlassStage.minutesToNext(givenBackMinutes: store.givenBackMinutes) {
                    let nextName = Text(LocalizedStringKey(GlassStage.nameKeys[current + 1]))
                    let leftText = UnrotFormat.short(minutes: left)
                    Text("u.stageUp.next \(nextName) \(leftText)").font(.caption.weight(.semibold)).foregroundStyle(UPalette.goldLight)
                }
            }
            .accessibilityElement(children: .combine)
        }
    }

    /// 0 = empty pot, then up to 1 by the day's gain against the person's before.
    private func plantSize(gain: Int?, today: Bool) -> Double {
        if today { return 0.35 }
        guard let gain else { return 0 }
        let before = max(1, store.answers.beforeMinutes)
        return 0.2 + 0.8 * min(1, Double(gain) / Double(before) * 2)
    }

    // MARK: - Feed chart (Swift Charts)

    /// Feed time per day for the chosen range, with the before line. Tap or drag
    /// across the bars to read a day; tap opens it.
    private var bars: some View {
        let days = UnrotFormat.lastDays(range.rawValue, now: now)
        let points = days.map { (date: $0, minutes: store.feed[UnrotFormat.dayKey($0)]) }
        let before = store.answers.beforeMinutes
        let shown = scrubDay.flatMap { d in points.first { Calendar.current.isDate($0.date, inSameDayAs: d) } }
        return UCard {
            HStack {
                ULabel(range == .week ? "u.week.title" : "u.range.monthTitle")
                Spacer()
                HStack(spacing: 2) {
                    ForEach(ChartRange.allCases, id: \.self) { value in
                        Button {
                            withAnimation(.easeInOut(duration: 0.25)) { range = value }
                        } label: {
                            Text(LocalizedStringKey(value.titleKey))
                                .font(.caption.weight(range == value ? .bold : .regular))
                                .foregroundStyle(range == value ? Color(red: 0.14, green: 0.09, blue: 0.03) : UPalette.dim)
                                .padding(.horizontal, 12).frame(minHeight: 32)
                                .background(range == value ? Color(red: 1, green: 0.97, blue: 0.90) : .clear, in: Capsule())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(range == value ? .isSelected : [])
                    }
                }
                .padding(3)
                .background(Color.white.opacity(0.08), in: Capsule())
            }
            if let trend = WeekTrend.make(days14: UnrotFormat.lastDays(14, now: now).map { UnrotFormat.dayKey($0) },
                                          today: store.today(now), feed: store.feed) {
                // Only the change: the card's own "a day" figure is in the numbers below.
                let change = "\(abs(trend.percent))%"
                HStack(spacing: 6) {
                    Image(systemName: Self.trendIcons[trend.percent <= 0 ? 0 : 1])
                    Text(trend.percent <= 0 ? "u.trend.downOnly \(change)" : "u.trend.upOnly \(change)")
                }
                .font(.footnote.weight(.semibold))
                .foregroundStyle(trend.percent <= 0 ? UPalette.leaf : UPalette.warn)
            }
            // The day under the finger, or the hint.
            Group {
                if let shown {
                    HStack(spacing: 6) {
                        Text(shown.date.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated)))
                            .foregroundStyle(UPalette.ink)
                        Text(verbatim: shown.minutes.map { UnrotFormat.short(minutes: $0) } ?? "—")
                            .foregroundStyle(UPalette.goldLight)
                    }
                    .font(.footnote.weight(.semibold))
                } else {
                    Text("u.chart.tapHint").font(.footnote).foregroundStyle(UPalette.dim)
                }
            }
            .frame(height: 18, alignment: .leading)
            Chart {
                ForEach(points, id: \.date) { point in
                    if let minutes = point.minutes {
                        BarMark(x: .value("Day", point.date, unit: .day), y: .value("Minutes", minutes))
                            .foregroundStyle(LinearGradient(colors: [UPalette.goldLight, Color(red: 0.788, green: 0.553, blue: 0.133)],
                                                            startPoint: .top, endPoint: .bottom))
                            .cornerRadius(range == .week ? 7 : 3)
                            .opacity(scrubDay == nil || Calendar.current.isDate(point.date, inSameDayAs: scrubDay!) ? 1 : 0.45)
                    }
                }
                if before > 0 {
                    RuleMark(y: .value("Before", before))
                        .foregroundStyle(Color.white.opacity(0.7))
                        .lineStyle(StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
                        .annotation(position: .bottom, alignment: .trailing, spacing: 3) {
                            Text("u.feed.aim \(UnrotFormat.short(minutes: before))")
                                .font(.caption2.weight(.semibold)).foregroundStyle(UPalette.dim)
                        }
                }
            }
            .chartYScale(domain: 0...max(before, points.compactMap(\.minutes).max() ?? 0, 60))
            // Every day of the range has its place, measured or not.
            .chartXScale(domain: (days.first ?? now)...(Calendar.current.date(byAdding: .day, value: 1, to: days.last ?? now) ?? now))
            .chartYAxis(.hidden)
            .chartXAxis {
                AxisMarks(values: .stride(by: .day, count: range == .week ? 1 : 7)) { _ in
                    AxisValueLabel(format: range == .week ? .dateTime.weekday(.narrow) : .dateTime.day().month(.defaultDigits),
                                   centered: true)
                        .foregroundStyle(UPalette.dim)
                }
            }
            .chartOverlay { proxy in
                GeometryReader { geo in
                    Rectangle().fill(.clear).contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance: 0)
                            .onChanged { value in scrubDay = day(at: value.location, proxy: proxy, geo: geo) }
                            .onEnded { value in
                                if let d = day(at: value.location, proxy: proxy, geo: geo),
                                   abs(value.translation.width) < 8 { openDay = DayRef(date: d) }
                                scrubDay = nil
                            })
                }
            }
            .frame(height: 150)
            .overlay {
                if points.allSatisfy({ $0.minutes == nil }) {
                    VStack(spacing: 6) {
                        Image(systemName: "chart.bar").font(.title2).foregroundStyle(UPalette.dim)
                        Text("u.chart.empty").font(.footnote).foregroundStyle(UPalette.dim).multilineTextAlignment(.center)
                    }
                    .padding(.horizontal, 24)
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text("u.week.title"))
            .accessibilityChartDescriptor(FeedChartDescriptor(points: points))
            rangeNumbers(days)
            Text(facts.feedWatched ? "u.week.legend" : "u.feed.notWatched")
                .font(.caption).foregroundStyle(UPalette.dim).fixedSize(horizontal: false, vertical: true)
        }
    }

    /// Average, days under before, the lightest day (tap opens it) and time given back in the range.
    private func rangeNumbers(_ days: [Date]) -> some View {
        let stats = RangeStats.make(days: days.map { UnrotFormat.dayKey($0) }, today: store.today(now),
                                    feed: store.feed, gains: store.gains, before: store.answers.beforeMinutes)
        let lightestDate = stats.lightest.flatMap { l in days.first { UnrotFormat.dayKey($0) == l.key } }
        return HStack(spacing: 0) {
            stat(stats.average.map { UnrotFormat.short(minutes: $0) } ?? "—", "u.stat.average")
            stat("\(stats.underBefore)/\(stats.measuredDays)", "u.stat.under")
            Button { if let lightestDate { openDay = DayRef(date: lightestDate) } } label: {
                stat(lightestDate.map { $0.formatted(.dateTime.weekday(.abbreviated)) } ?? "—", "u.stat.lightest", tappable: lightestDate != nil)
            }
            .buttonStyle(.plain)
            .disabled(lightestDate == nil)
            stat(UnrotFormat.short(minutes: stats.givenBack), "u.stat.given")
        }
        .padding(.vertical, 6)
        .background(Color.white.opacity(0.04), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func stat(_ value: String, _ label: LocalizedStringKey, tappable: Bool = false) -> some View {
        VStack(spacing: 2) {
            Text(verbatim: value).font(.subheadline.weight(.bold))
                .foregroundStyle(tappable ? UPalette.goldLight : UPalette.ink).lineLimit(1).minimumScaleFactor(0.7)
            Text(label).font(.caption2).foregroundStyle(UPalette.dim).multilineTextAlignment(.center).lineLimit(2)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }

    private func day(at location: CGPoint, proxy: ChartProxy, geo: GeometryProxy) -> Date? {
        let origin = geo[proxy.plotAreaFrame].origin
        guard let date: Date = proxy.value(atX: location.x - origin.x) else { return nil }
        let day = Calendar.current.startOfDay(for: date)
        return day <= Calendar.current.startOfDay(for: now) ? day : nil
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

    // MARK: - How the week felt

    private var moodWeek: some View {
        let entries = week.map { store.checkIns[UnrotFormat.dayKey($0)] }
        let pull = MoodWeek.topPull(entries.compactMap { $0?.pull })
        return UCard {
            ULabel("u.mood.weekTitle")
            HStack(spacing: 6) {
                ForEach(Array(zip(week, entries)), id: \.0) { date, entry in
                    Button { openDay = DayRef(date: date) } label: {
                    VStack(spacing: 6) {
                        Image(systemName: entry.map { UnrotCheckInSheet.moods[max(0, min(4, $0.mood))].0 } ?? "circle.dotted")
                            .font(.body)
                            .foregroundStyle(entry == nil ? UPalette.dim.opacity(0.5) : UPalette.goldLight)
                            .frame(width: 36, height: 36)
                            .background(entry == nil ? Color.clear : UPalette.gold.opacity(0.12), in: Circle())
                        Text(date.formatted(.dateTime.weekday(.narrow))).font(.caption2).foregroundStyle(UPalette.dim)
                    }
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                    }
                    .buttonStyle(UPressStyle())
                    .accessibilityElement()
                    .accessibilityLabel(Text(date.formatted(.dateTime.weekday(.wide))))
                    .accessibilityAddTraits(.isButton)
                    .accessibilityValue(entry.map { Text(UnrotCheckInSheet.moods[max(0, min(4, $0.mood))].1) } ?? Text("u.garden.none"))
                }
            }
            if let pull {
                let pullName = Text(uKey("u.pull.", pull))
                Text("u.mood.topPull \(pullName)").font(.footnote).foregroundStyle(UPalette.ink)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                Text("u.mood.empty").font(.footnote).foregroundStyle(UPalette.dim)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
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
struct Pot: View {
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

/// VoiceOver's audio graph for the feed chart.
struct FeedChartDescriptor: AXChartDescriptorRepresentable {
    let points: [(date: Date, minutes: Int?)]

    func makeChartDescriptor() -> AXChartDescriptor {
        let measured = points.compactMap { p in p.minutes.map { (p.date, $0) } }
        let x = AXCategoricalDataAxisDescriptor(title: "Day",
            categoryOrder: measured.map { $0.0.formatted(.dateTime.weekday(.abbreviated).day()) })
        let y = AXNumericDataAxisDescriptor(title: "Minutes", range: 0...Double(max(60, measured.map(\.1).max() ?? 0)),
                                            gridlinePositions: []) { "\(Int($0)) min" }
        let series = AXDataSeriesDescriptor(name: "Feed time", isContinuous: false,
            dataPoints: measured.map { .init(x: $0.0.formatted(.dateTime.weekday(.abbreviated).day()), y: Double($0.1)) })
        return AXChartDescriptor(title: "Feed time", summary: nil, xAxis: x, yAxis: y, additionalAxes: [], series: [series])
    }
}

