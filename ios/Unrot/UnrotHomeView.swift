import SwiftUI

/// Unrot's Today, as the phone prototype draws it (design/unrot-phone-prototype-2026-11-24):
/// the glass alone on the dark, its stage, one running count of clean time, the next
/// stage, three small numbers, four round tools, one line about adult content and
/// Today's three. Help now sits under the thumb (UnrotRootView draws it over the tab bar).
/// It answers three questions: is it working, how am I doing, what do I do right now.
struct UnrotHomeView: View {
    @ObservedObject var store: UnrotStore
    let shield: UnrotShieldFacts
    let now: Date
    let onShield: () -> Void
    let onTool: (UnrotTool) -> Void
    let onInfo: () -> Void
    var onRecap: () -> Void = {}
    var onProgress: () -> Void = {}

    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(\.uTones) private var t
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var tourStep: TourSpot?

    private var glass: GlassState { store.glass(now: now) }
    private var cracked: Bool { glass.look == .cracked }
    private var night: Bool { shield.quietNow && glass.look != .cracked && glass.look != .broken }
    private var usedToday: Int? { store.feed[store.today(now)] }
    private var stage: Int { GlassStage.stage(givenBackMinutes: store.givenBackMinutes) }
    private var stageName: Text { Text(LocalizedStringKey(GlassStage.nameKeys[stage])) }

    var body: some View {
        ScrollViewReader { scroller in
        ScrollView {
            VStack(spacing: 0) {
                header
                glassScene
                nextStage.tourSpot(.next)
                tiles.padding(.top, 2)
                tools.padding(.top, 14).tourSpot(.tools)
                if store.focusActive { focusLine.padding(.top, 12) }
                adultRow.padding(.top, 14)
                if let recap = store.pendingRecap(now: now) { recapCard(recap).padding(.top, 10) }
                todaysThree
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 200)
            .frame(maxWidth: sizeClass == .regular ? 560 : .infinity)
            .frame(maxWidth: .infinity)
        }
        .scrollIndicators(.hidden)
        .scrollDisabled(tourStep != nil)
        .overlayPreferenceValue(TourAnchors.self) { anchors in
            if let step = tourStep {
                GeometryReader { geo in
                    TourOverlay(spot: step, rect: anchors[step.rawValue].map { geo[$0] },
                                onNext: { advanceTour(scroller) }, onSkip: endTour)
                }
            }
        }
        .onAppear {
            if !store.tourSeen && tourStep == nil {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { withAnimation { tourStep = .sprout } }
            }
        }
        .onChange(of: store.tourSeen) { seen in
            if !seen && tourStep == nil {
                scroller.scrollTo("tour-0", anchor: .top)
                withAnimation { tourStep = .sprout }
            }
        }
        }
    }

    private func advanceTour(_ scroller: ScrollViewProxy) {
        guard let step = tourStep, let next = TourSpot(rawValue: step.rawValue + 1) else { return endTour() }
        withAnimation(.easeInOut(duration: 0.35)) {
            scroller.scrollTo("tour-\(next.rawValue)", anchor: .center)
            tourStep = next
        }
    }

    private func endTour() {
        withAnimation { tourStep = nil }
        store.markTourSeen()
    }

    // MARK: - Header

    /// The small gold date line, the title, and the five panes as a tiny dome.
    private var header: some View {
        HStack(alignment: .top, spacing: 8) {
            VStack(alignment: .leading, spacing: 0) {
                let date = now.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated))
                Text("u.td.eyebrow \(date) \(store.day(now))")
                    .font(.system(size: 11.5, weight: .bold)).tracking(1.8).textCase(.uppercase)
                    .foregroundStyle(t.gold).lineLimit(1)
                    .padding(.top, 8)
                Text("u.tab.home").font(.uDisplay(30)).foregroundStyle(t.ink)
                    .accessibilityAddTraits(.isHeader)
            }
            Spacer(minLength: 0)
            Button(action: onShield) { paneChip }
                .buttonStyle(UPressStyle())
                .accessibilityHint(Text("u.shield.open"))
        }
        .frame(minHeight: 60, alignment: .top)
        .zIndex(1)
    }

    /// The five panes, closed in gold or open as an amber outline, and one word.
    private var paneChip: some View {
        let panes = UnrotShieldFacts.Pane.allCases.map { shield.on[$0] == true }
        let open = panes.filter { !$0 }.count
        let warn = cracked || !shield.checked || open > 0
        return HStack(spacing: 9) {
            PaneDome(closed: panes, cracked: cracked)
            Group {
                if cracked { Text("u.td.chip.cracked") }
                else if !shield.checked { Text("u.chip.checking") }
                else if open == 0 { Text("u.td.chip.closed") }
                else { Text("u.chip.open \(open)") }
            }
            .font(.system(size: 12.5, weight: .semibold)).lineLimit(1).fixedSize()
            .foregroundStyle(warn ? Color(red: 1, green: 0.773, blue: 0.541) : Color(red: 0.953, green: 0.918, blue: 0.839))
        }
        .padding(.leading, 10).padding(.trailing, 12)
        .frame(height: 34)
        .background(LinearGradient(colors: [Color(white: 0.21), Color(white: 0.14)], startPoint: .top, endPoint: .bottom), in: Capsule())
        .overlay { Capsule().strokeBorder(Color.white.opacity(0.08)) }
        .shadow(color: .black.opacity(0.3), radius: 5, y: 4)
        .frame(minHeight: 44)
        .padding(.top, 2)
    }

    // MARK: - The glass

    private var glassScene: some View {
        Button {
            if cracked { onTool(.breathe) } else { onProgress() }
        } label: {
            VStack(spacing: 0) {
                LiveGlass(art: GlassStage.art(stage: stage, look: glass.look, night: night),
                          still: cracked, night: night)
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 22)
                    .tourSpot(.sprout)
                Group {
                    if cracked { Text("u.td.stage.cracked \(clock(store.crackedAt))") }
                    else if night { Text("u.td.stage.resting") }
                    else if glass.look == .gold { Text("u.td.stage.gold \(store.day(now))") }
                    else if glass.look == .broken { Text("u.td.stage.broken") }
                    else { Text("u.td.stage.day \(stageName) \(store.day(now))") }
                }
                .font(.system(size: 12, weight: .bold)).tracking(1.7).textCase(.uppercase)
                .foregroundStyle(t.gold)
                .padding(.top, -30)
                count.padding(.top, 6).tourSpot(.number)
            }
        }
        .buttonStyle(.plain)
        .padding(.top, -46)
        .accessibilityHint(cracked ? Text("u.td.help.seal") : Text("u.tab.progress"))
    }

    /// "3d 14h 22m" of clean time, or while cracked the minutes left to seal it.
    @ViewBuilder private var count: some View {
        if cracked {
            TimelineView(.periodic(from: .now, by: 1)) { context in
                let left = max(0, Int(store.glass(now: context.date).sealLeft))
                VStack(spacing: 2) {
                    Text(verbatim: String(format: "%d:%02d", left / 60, left % 60))
                        .font(.uDisplay(50)).monospacedDigit().foregroundStyle(t.ink)
                    Text("u.td.sealLeft").font(.subheadline).foregroundStyle(t.mut)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text("u.td.sealA11y \(left / 60) \(left % 60)"))
            }
        } else {
            TimelineView(.periodic(from: .now, by: 30)) { context in
                let since = cleanSince(context.date)
                let minutes = max(0, Int(context.date.timeIntervalSince(since) / 60))
                let d = minutes / 1440, h = (minutes / 60) % 24, m = minutes % 60
                VStack(spacing: 2) {
                    (Text(verbatim: "\(d)") + unit("u.unit.d") + Text(verbatim: "  \(h)") + unit("u.unit.h")
                        + Text(verbatim: "  \(m)") + unit("u.unit.m"))
                        .font(.uDisplay(50)).foregroundStyle(t.ink)
                        .lineLimit(1).minimumScaleFactor(0.6)
                    Group {
                        if Calendar.current.isDate(since, inSameDayAs: context.date) { Text("u.td.cleanToday") }
                        else { Text("u.td.cleanSince \(sinceWords(since, now: context.date))") }
                    }
                    .font(.subheadline).foregroundStyle(t.mut)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text("u.td.cleanA11y \(d) \(h) \(m)"))
            }
        }
    }

    private func unit(_ key: LocalizedStringKey) -> Text {
        Text(key).font(.system(size: 19, weight: .medium)).foregroundColor(t.mut)
    }

    /// Clean time counts from the start, or from the last time it got through.
    private func cleanSince(_ date: Date) -> Date {
        let cal = Calendar.current
        let start = cal.date(byAdding: .day, value: -(max(1, store.day(date)) - 1), to: cal.startOfDay(for: date)) ?? date
        let broken = store.brokenAt > 0 ? Date(timeIntervalSince1970: store.brokenAt) : .distantPast
        return max(start, broken)
    }

    /// "Saturday" within the last week, "12 Nov" before that.
    private func sinceWords(_ date: Date, now: Date) -> String {
        let days = Calendar.current.dateComponents([.day], from: Calendar.current.startOfDay(for: date),
                                                   to: Calendar.current.startOfDay(for: now)).day ?? 0
        return days <= 6 ? date.formatted(.dateTime.weekday(.wide)) : date.formatted(.dateTime.day().month(.abbreviated))
    }

    private func clock(_ at: TimeInterval) -> String {
        at > 0 ? Date(timeIntervalSince1970: at).formatted(date: .omitted, time: .shortened) : ""
    }

    // MARK: - Next stage

    @ViewBuilder private var nextStage: some View {
        if cracked {
            Button { onTool(.breathe) } label: {
                card(warn: true) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("u.td.nothingLost").font(.system(size: 15, weight: .bold)).foregroundStyle(t.ink)
                        let days = cleanDays
                        Group {
                            if days > 0 { Text("u.td.sealBody \(days)") } else { Text("u.td.sealBodyNew") }
                        }
                        .font(.system(size: 12.5)).foregroundStyle(t.mut)
                        .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            .buttonStyle(UPressStyle())
            .padding(.top, 16).padding(.bottom, 10)
        } else {
            Button(action: onProgress) {
                card(warn: false) {
                    Image(stage >= 5 ? "glass_tree" : GlassStage.art(stage: stage + 1, look: .clear, night: false))
                        .resizable().scaledToFill()
                        .frame(width: 50, height: 50)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .saturation(0.5).brightness(-0.08)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 6) {
                        let given = store.givenBackMinutes
                        if let left = GlassStage.minutesToNext(givenBackMinutes: given) {
                            let leftText = UnrotFormat.short(minutes: max(1, left))
                            let givenText = UnrotFormat.short(minutes: max(0, given))
                            let nextText = UnrotFormat.short(minutes: GlassStage.thresholds[stage + 1])
                            (Text(LocalizedStringKey(GlassStage.nameKeys[stage + 1])).foregroundColor(t.ink)
                             + Text(verbatim: " ")
                             + Text("u.td.nextIn \(leftText)").foregroundColor(t.mut).fontWeight(.medium))
                                .font(.system(size: 15, weight: .bold))
                            goldBar(GlassStage.part(givenBackMinutes: given))
                            Text("u.td.nextOf \(givenText) \(nextText)")
                                .font(.system(size: 12.5)).foregroundStyle(t.mut)
                        } else {
                            Text("u.td.nextTop").font(.system(size: 15, weight: .bold)).foregroundStyle(t.ink)
                            goldBar(1)
                            Text("u.td.nextTopBody \(UnrotFormat.short(minutes: given))").font(.system(size: 12.5)).foregroundStyle(t.mut)
                        }
                    }
                }
            }
            .buttonStyle(UPressStyle())
            .padding(.top, 16).padding(.bottom, 10)
        }
    }

    private var cleanDays: Int {
        max(0, Calendar.current.dateComponents([.day], from: cleanSince(now), to: now).day ?? 0)
    }

    private func card<C: View>(warn: Bool, @ViewBuilder _ content: () -> C) -> some View {
        HStack(spacing: 12) {
            content()
            Spacer(minLength: 0)
            Image(systemName: "chevron.forward").font(.caption.weight(.bold)).foregroundStyle(t.mut)
        }
        .padding(.leading, warn ? 16 : 10).padding(.trailing, 14).padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(t.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay {
            if warn { RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(Color(red: 0.949, green: 0.651, blue: 0.353).opacity(0.5), lineWidth: 1.5) }
        }
        .contentShape(Rectangle())
    }

    private func goldBar(_ part: Double) -> some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(t.track)
                Capsule().fill(LinearGradient(colors: [t.goldBot, t.goldTop], startPoint: .leading, endPoint: .trailing))
                    .frame(width: max(part > 0 ? 5 : 0, geo.size.width * part))
            }
        }
        .frame(height: 5)
        .accessibilityHidden(true)
    }

    // MARK: - Three small numbers

    private var tiles: some View {
        let panes = UnrotShieldFacts.Pane.allCases.map { shield.on[$0] == true }
        return HStack(spacing: 8) {
            if cracked {
                tile(Text(verbatim: "1"), Text("u.td.tile.page"), action: onShield)
            } else {
                tile(Text(verbatim: "\(panes.filter { $0 }.count) / \(panes.count)"), Text("u.td.tile.panes"), action: onShield)
            }
            tile(Text(verbatim: "\(treeCount)"), Text("u.td.tile.trees"), action: onProgress)
            tile(Text(verbatim: backToday.map { UnrotFormat.short(minutes: $0) } ?? "–"), Text("u.td.tile.back"), action: onProgress)
        }
    }

    private func tile(_ value: Text, _ label: Text, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 2) {
                value.font(.uDisplay(20)).foregroundStyle(t.ink).lineLimit(1).minimumScaleFactor(0.7)
                label.font(.system(size: 12)).foregroundStyle(t.mut).multilineTextAlignment(.center).lineLimit(2)
            }
            .frame(maxWidth: .infinity, minHeight: 56)
            .padding(.vertical, 10).padding(.horizontal, 6)
            .background(t.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(UPressStyle())
        .accessibilityElement(children: .combine)
    }

    /// One tree for every whole day so far, except the day it got through.
    private var treeCount: Int {
        let day = max(1, store.day(now))
        guard store.brokenAt > 0 else { return day - 1 }
        let broken = Calendar.current.startOfDay(for: Date(timeIntervalSince1970: store.brokenAt))
        let today = Calendar.current.startOfDay(for: now)
        return max(0, day - 1 - (broken < today ? 1 : 0))
    }

    /// Your before, less today's feed time so far.
    private var backToday: Int? {
        let before = store.answers.beforeMinutes
        guard before > 0, let used = usedToday else { return nil }
        return max(0, before - used)
    }

    // MARK: - Four round tools

    private var tools: some View {
        HStack(spacing: 0) {
            roundTool("obj_pause", "u.tool.breathe") { onTool(.breathe) }.tourSpot(.breathe)
            roundTool("obj_target", "u.tool.focus", lit: store.focusActive) { onTool(.focus) }
            roundTool("obj_pencil", "u.td.notes") { onTool(.notes) }
            roundTool("obj_dial", "u.tool.more") { onTool(.rules) }
        }
    }

    private func roundTool(_ art: String, _ title: LocalizedStringKey, lit: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 7) {
                UObjectWell(art: art, size: 60, inset: 10)
                    .overlay { if lit { Circle().strokeBorder(t.gold, lineWidth: 2) } }
                Text(title).font(.system(size: 12.5, weight: .medium)).foregroundStyle(t.ink).lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(UPressStyle())
    }

    // MARK: - Adult content, one line

    private var adultRow: some View {
        let panes = UnrotShieldFacts.Pane.allCases.map { shield.on[$0] == true }
        let open = panes.filter { !$0 }.count
        let warn = cracked || (shield.checked && open > 0)
        return Button { cracked ? onTool(.breathe) : onShield() } label: {
            HStack(spacing: 12) {
                UObjectWell(art: "obj_shield", size: 36, inset: 4, corner: 10)
                VStack(alignment: .leading, spacing: 1) {
                    Group {
                        if cracked { Text("u.td.adult.cracked \(clock(store.crackedAt))") }
                        else if !shield.checked || open == 0 { Text("u.td.adult.closed") }
                        else { Text("u.td.adult.open \(open)") }
                    }
                    .font(.system(size: 16, weight: .semibold)).foregroundStyle(t.ink).lineLimit(1)
                    Group {
                        if cracked { Text("u.td.adult.crackedBody") }
                        else if !shield.checked || open == 0 { Text("u.td.adult.closedBody \(panes.count - open) \(panes.count)") }
                        else { Text("u.td.adult.openBody") }
                    }
                    .font(.system(size: 13)).foregroundStyle(t.mut).lineLimit(1)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.forward").font(.caption.weight(.bold)).foregroundStyle(t.mut)
            }
            .padding(.horizontal, 14).padding(.vertical, 13)
            .background(t.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay {
                if warn { RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Color(red: 0.949, green: 0.651, blue: 0.353).opacity(0.5), lineWidth: 1.5) }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(UPressStyle())
    }

    // MARK: - Today's three

    private var todaysThree: some View {
        let items = TodaysThree.items(checkedIn: store.checkedInToday != nil,
                                      pauses: store.breathsToday(now) + (PauseStore.todayCount(now: now) ?? 0) > 0 ? 1 : 0,
                                      usedToday: usedToday, beforeMinutes: store.answers.beforeMinutes)
        let before = store.answers.beforeMinutes
        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text("u.three.title").font(.uDisplay(19)).foregroundStyle(t.ink).accessibilityAddTraits(.isHeader)
                Spacer()
                Text("u.td.threeCount \(items.filter(\.done).count)").font(.system(size: 13)).foregroundStyle(t.mut)
            }
            .padding(.top, 22).padding(.bottom, 10)
            VStack(spacing: 0) {
                threeRow(0, "obj_tick", done: items[0].done,
                         title: items[0].done ? "u.td.three.checked" : "u.three.checkin",
                         sub: items[0].done ? Text("u.td.three.doneToday") : Text("u.td.three.checkinSub"))
                Divider().overlay(Color.white.opacity(0.06)).padding(.leading, 56)
                threeRow(1, "obj_pause", done: items[1].done, title: "u.td.three.pause",
                         sub: items[1].done ? Text("u.td.three.doneToday") : Text("u.td.three.notYet"))
                Divider().overlay(Color.white.opacity(0.06)).padding(.leading, 56)
                threeRow(2, "obj_hourglass", done: items[2].done, title: "u.td.three.under",
                         sub: before > 0 && usedToday != nil
                            ? Text("u.td.three.underSub \(UnrotFormat.short(minutes: usedToday ?? 0)) \(UnrotFormat.short(minutes: before))")
                            : Text("u.td.three.underUnknown"))
            }
            .background(t.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
    }

    private func threeRow(_ index: Int, _ art: String, done: Bool, title: LocalizedStringKey, sub: Text) -> some View {
        Button {
            switch index {
            case 0: onTool(.checkIn)
            case 1: onTool(.breathe)
            default: onProgress()
            }
        } label: {
            HStack(spacing: 12) {
                UObjectWell(art: art, size: 30, inset: 3, corner: 8)
                    .overlay(alignment: .bottomTrailing) {
                        if done {
                            Image(systemName: "checkmark").font(.system(size: 8, weight: .black)).foregroundStyle(t.goldInk)
                                .frame(width: 14, height: 14).background(t.gold, in: Circle()).offset(x: 4, y: 4)
                        }
                    }
                VStack(alignment: .leading, spacing: 1) {
                    Text(title).font(.system(size: 15)).foregroundStyle(done ? t.mut : t.ink)
                    sub.font(.system(size: 13)).foregroundStyle(t.mut)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.forward").font(.caption.weight(.bold)).foregroundStyle(t.mut)
            }
            .padding(.horizontal, 14).padding(.vertical, 13)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityValue(done ? Text("u.done") : Text(verbatim: ""))
    }

    // MARK: - Weekly recap

    /// Early in a new week: last week in one look, until opened or put away.
    private func recapCard(_ recap: WeeklyRecap) -> some View {
        HStack(spacing: 12) {
            Button(action: onRecap) {
                HStack(spacing: 12) {
                    UObjectWell(art: "obj_medal", size: 36, inset: 4, corner: 10)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("u.recap.card").font(.subheadline.weight(.bold)).foregroundStyle(t.ink)
                        let given = UnrotFormat.short(minutes: recap.stats.givenBack)
                        Text("u.recap.cardSub \(given)").font(.caption).foregroundStyle(t.mut)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.forward").font(.caption.weight(.bold)).foregroundStyle(t.mut)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Button { withAnimation { store.markRecapSeen(recap.weekKey) } } label: {
                Image(systemName: "xmark").font(.caption.weight(.bold)).foregroundStyle(t.mut)
                    .frame(width: 32, height: 32).background(Color.white.opacity(0.06), in: Circle())
            }
            .accessibilityLabel(Text("u.recap.dismiss"))
        }
        .padding(14)
        .background(t.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay { RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(t.gold.opacity(0.35)) }
        .transition(.opacity.combined(with: .move(edge: .top)))
    }

    // MARK: - Focus running

    /// While Focus runs: a slim gold line with what is left; tap opens it.
    private var focusLine: some View {
        TimelineView(.periodic(from: .now, by: 30)) { context in
            let left = (FocusClock.remaining(until: store.focusUntil, now: context.date) + 59) / 60
            Button { onTool(.focus) } label: {
                HStack(spacing: 8) {
                    Image(systemName: "scope").font(.subheadline.weight(.bold))
                    Text("u.home.focusLeft \(UnrotFormat.short(minutes: left))").font(.subheadline.weight(.bold))
                    Spacer()
                    Image(systemName: "chevron.forward").font(.caption.weight(.bold))
                }
                .foregroundStyle(t.goldInk)
                .padding(.horizontal, 16).frame(minHeight: 46)
                .background(t.goldFill, in: Capsule())
            }
            .buttonStyle(UPressStyle())
        }
    }
}

/// The five panes of the glass as a tiny dome: closed in gold, open as an amber outline.
struct PaneDome: View {
    let closed: [Bool]
    var cracked = false
    @Environment(\.uTones) private var t

    var body: some View {
        let heights: [CGFloat] = [9, 13, 15, 13, 9]
        VStack(spacing: 1) {
            HStack(alignment: .bottom, spacing: 2) {
                ForEach(0..<5, id: \.self) { i in
                    if closed.indices.contains(i) && closed[i] {
                        RoundedRectangle(cornerRadius: 2)
                            .fill(LinearGradient(colors: [Color(red: 1, green: 0.882, blue: 0.604), t.goldBot], startPoint: .top, endPoint: .bottom))
                            .frame(width: 4, height: heights[i])
                    } else {
                        RoundedRectangle(cornerRadius: 2)
                            .strokeBorder(Color(red: 0.949, green: 0.651, blue: 0.353), lineWidth: 1.2)
                            .frame(width: 4, height: heights[i])
                    }
                }
            }
            Rectangle().fill(Color(red: 0.553, green: 0.518, blue: 0.447)).frame(width: 28, height: 2)
        }
        .overlay {
            if cracked {
                Path { p in
                    p.move(to: CGPoint(x: 17, y: 0)); p.addLine(to: CGPoint(x: 14, y: 5))
                    p.addLine(to: CGPoint(x: 18, y: 8)); p.addLine(to: CGPoint(x: 15, y: 15))
                }
                .stroke(Color(white: 0.12), style: StrokeStyle(lineWidth: 1.6, lineJoin: .round))
            }
        }
        .frame(width: 28, height: 18)
        .accessibilityHidden(true)
    }
}

/// A well in the colour of the moment, lit gold from above, so the black-and-gold
/// object stands out instead of sinking into the dark.
struct UObjectWell: View {
    let art: String
    var size: CGFloat = 60
    var inset: CGFloat = 10
    /// nil for a circle.
    var corner: CGFloat? = nil
    @Environment(\.uTones) private var t

    var body: some View {
        let fill = RadialGradient(stops: [.init(color: t.gold.opacity(0.46), location: 0),
                                          .init(color: t.gold.opacity(0.16), location: 0.62),
                                          .init(color: .clear, location: 1)],
                                  center: UnitPoint(x: 0.5, y: 0.22), startRadius: 0, endRadius: size * 0.8)
        Group {
            if let corner {
                RoundedRectangle(cornerRadius: corner, style: .continuous).fill(t.raised)
                    .overlay { RoundedRectangle(cornerRadius: corner, style: .continuous).fill(fill) }
                    .overlay { RoundedRectangle(cornerRadius: corner, style: .continuous).strokeBorder(t.gold.opacity(0.34)) }
            } else {
                Circle().fill(t.raised).overlay { Circle().fill(fill) }
                    .overlay { Circle().strokeBorder(t.gold.opacity(0.34)) }
            }
        }
        .frame(width: size, height: size)
        .overlay {
            Image(art).resizable().scaledToFit().padding(inset)
                .shadow(color: .black.opacity(0.45), radius: 2, y: 3)
        }
        .shadow(color: .black.opacity(0.35), radius: 6, y: 5)
        .accessibilityHidden(true)
    }
}

/// The painted jar, melting into the page on every side: it breathes, the lamp glows
/// and gold motes rise. Still while cracked; nothing moves with Reduce Motion.
struct LiveGlass: View {
    let art: String
    var still = false
    var night = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var breathe = false

    var body: some View {
        Image(art)
            .resizable()
            .scaledToFill()
            .aspectRatio(1, contentMode: .fit)
            .scaleEffect(x: breathe ? 1.012 : 1, y: breathe ? 1.018 : 1, anchor: UnitPoint(x: 0.5, y: 0.78))
            .mask(EllipticalGradient(stops: [.init(color: .black, location: 0), .init(color: .black, location: 0.56),
                                             .init(color: .black.opacity(0.5), location: 0.78), .init(color: .clear, location: 1)],
                                     center: UnitPoint(x: 0.5, y: 0.51), startRadiusFraction: 0, endRadiusFraction: 0.57))
            .overlay {
                RadialGradient(colors: [Color(red: 1, green: 0.84, blue: 0.47).opacity(breathe ? 0.16 : 0.08), .clear],
                               center: UnitPoint(x: 0.5, y: 0.4), startRadius: 0, endRadius: 170)
                    .blendMode(.screen)
                    .allowsHitTesting(false)
            }
            .overlay { if !reduceMotion && !still { Motes() } }
            .animation(reduceMotion || still ? nil : .easeInOut(duration: night ? 6 : 3.5).repeatForever(autoreverses: true), value: breathe)
            .onAppear { breathe = true }
            .accessibilityLabel(Text("u.td.glassA11y"))
            .accessibilityAddTraits(.isImage)
    }
}

/// Seven gold motes rising slowly from the moss.
private struct Motes: View {
    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 30)) { context in
            Canvas { gc, size in
                let time = context.date.timeIntervalSinceReferenceDate
                for k in 0..<7 {
                    let phase = (time / 7 + Double(k) / 7).truncatingRemainder(dividingBy: 1)
                    let x = size.width * (0.24 + Double(k) * 0.085) + sin(time * 0.6 + Double(k)) * 4
                    let y = size.height * (0.66 - phase * 0.5)
                    let alpha = sin(phase * .pi) * 0.85
                    gc.fill(Path(ellipseIn: CGRect(x: x, y: y, width: 3, height: 3)),
                            with: .color(Color(red: 1, green: 0.882, blue: 0.604).opacity(alpha)))
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
