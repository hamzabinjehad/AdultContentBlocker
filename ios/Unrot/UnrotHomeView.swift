import SwiftUI

/// Unrot's Home, laid out like the concept (CleanPath's home with Unrot inside):
/// title and chips, the glass hero, an amber banner when a pane is open, four
/// round tools, check-in and "Had a hard moment", today's three, feed time,
/// and the person's own words. Breathe floats above the tab bar.
struct UnrotHomeView: View {
    @ObservedObject var store: UnrotStore
    let shield: UnrotShieldFacts
    let now: Date
    let onShield: () -> Void
    let onTool: (UnrotTool) -> Void
    let onInfo: () -> Void

    @Environment(\.horizontalSizeClass) private var sizeClass

    private var glass: GlassState { store.glass(now: now) }
    private var usedToday: Int? { store.feed[store.today(now)] }

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                header
                UnrotGlassHero(givenBack: store.givenBackMinutes, glass: glass,
                               night: shield.quietNow, nightEnds: shield.quietEnds,
                               measuring: shield.feedWatched && store.gains.isEmpty,
                               onInfo: onInfo, onBreathe: { onTool(.breathe) })
                if let issue = shield.banner { banner(issue) } else if shield.checked && shield.on[.lock] != true { lockNudge }
                tools
                if glass.look != .cracked {
                    UGoldButton(title: "u.tool.breathe", systemImage: "wind",
                                pulse: store.breathsToday(now) == 0, wide: true) { onTool(.breathe) }
                }
                checkRow
                todaysThree
                feedCard
                fromYou
                thought
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 120)
            .frame(maxWidth: sizeClass == .regular ? 560 : .infinity)
            .frame(maxWidth: .infinity)
        }
        .scrollIndicators(.hidden)
    }

    // MARK: - Header

    private var header: some View {
        UTitleBar(title: "u.tab.home",
                  subtitle: Text(now.formatted(.dateTime.weekday(.wide).day().month(.wide)))) {
            HStack(spacing: 8) {
                Text("u.day \(store.day(now))")
                    .font(.footnote.weight(.bold))
                    .foregroundStyle(UPalette.leafBright)
                    .padding(.horizontal, 12).frame(minHeight: 32)
                    .background(UPalette.leaf.opacity(0.18), in: Capsule())
                Button(action: onShield) { shield.chipView }
                    .buttonStyle(UPressStyle())
                    .accessibilityHint(Text("u.shield.open"))
            }
        }
    }

    private func banner(_ issue: UnrotShieldFacts.Banner) -> some View {
        Button(action: onShield) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(issue.title).font(.subheadline.weight(.bold)).foregroundStyle(.white)
                    Text(issue.detail).font(.footnote).foregroundStyle(UPalette.warnText)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 8)
                Text("u.fix").font(.footnote.weight(.heavy))
                    .foregroundStyle(Color(red: 0.165, green: 0.086, blue: 0.024))
                    .padding(.horizontal, 14).frame(minHeight: 32)
                    .background(UPalette.warn, in: Capsule())
            }
            .padding(14)
            .background(UPalette.warn.opacity(0.14), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay { RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(UPalette.warn.opacity(0.35)) }
        }
        .buttonStyle(UPressStyle())
    }

    /// Once the shield is up, the next thing: make Unrot hard to delete.
    private var lockNudge: some View {
        Button { onTool(.lock) } label: {
            HStack(spacing: 12) {
                Image(systemName: "lock.fill").font(.headline).foregroundStyle(UPalette.onGold)
                    .frame(width: 40, height: 40).background(UPalette.goldFill, in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text("u.lock.nudge").font(.subheadline.weight(.bold)).foregroundStyle(UPalette.ink)
                    Text("u.lock.nudgeSub").font(.footnote).foregroundStyle(UPalette.dim)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 6)
                Image(systemName: "chevron.forward").font(.caption.weight(.bold)).foregroundStyle(UPalette.dim)
            }
            .padding(14)
            .background(UPalette.gold.opacity(0.10), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay { RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(UPalette.gold.opacity(0.35)) }
        }
        .buttonStyle(UPressStyle())
    }

    // MARK: - Tools

    private var tools: some View {
        HStack(spacing: 10) {
            UToolButton(title: "u.tool.focus", systemImage: "scope", active: store.focusActive) { onTool(.focus) }
            UToolButton(title: "u.tool.notes", systemImage: "book.closed") { onTool(.notes) }
            UToolButton(title: "u.tool.instead", systemImage: "lightbulb") { onTool(.instead) }
            UToolButton(title: "u.tool.more", systemImage: "square.grid.2x2") { onTool(.rules) }
        }
        .padding(.top, 4)
    }

    private var checkRow: some View {
        HStack(spacing: 10) {
            Button { onTool(.checkIn) } label: {
                Image(systemName: store.checkedInToday == nil ? "checklist" : "checkmark")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(UPalette.onGold)
                    .frame(width: 78, height: 52)
                    .background(UPalette.goldFill, in: Capsule())
            }
            .buttonStyle(UPressStyle())
            .accessibilityLabel(Text("u.checkin.title"))
            UPillButton(title: "u.hard.button", systemImage: "heart") { onTool(.hardMoment) }
        }
    }

    // MARK: - Today's three

    private var todaysThree: some View {
        let items = TodaysThree.items(checkedIn: store.checkedInToday != nil,
                                      pauses: store.breathsToday(now) + (PauseStore.todayCount(now: now) ?? 0) > 0 ? 1 : 0,
                                      usedToday: usedToday, beforeMinutes: store.answers.beforeMinutes)
        return UCard {
            HStack {
                Text("u.three.title").font(.headline).foregroundStyle(UPalette.ink)
                Spacer()
                Text(verbatim: "\(items.filter(\.done).count)/3").font(.subheadline.weight(.bold))
                    .foregroundStyle(UPalette.goldLight)
            }
            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                Button {
                    switch index {
                    case 0: onTool(.checkIn)
                    case 1: onTool(.breathe)
                    default: onTool(.focus)
                    }
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: item.done ? "checkmark" : ["sun.max", "wind", "hourglass"][index])
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(item.done ? Color(red: 0.07, green: 0.2, blue: 0.04) : .white)
                            .frame(width: 36, height: 36)
                            .background(item.done ? Color(red: 0.561, green: 0.878, blue: 0.416) : Color.white.opacity(0.1),
                                        in: Circle())
                        VStack(alignment: .leading, spacing: 2) {
                            Text(LocalizedStringKey(item.key)).font(.subheadline.weight(.bold))
                                .foregroundStyle(item.done ? UPalette.dim : UPalette.ink)
                                .strikethrough(item.done)
                            Text(LocalizedStringKey(item.key + ".sub")).font(.caption).foregroundStyle(UPalette.dim)
                        }
                        Spacer()
                        if !item.done {
                            Image(systemName: "chevron.forward").font(.caption.weight(.bold)).foregroundStyle(UPalette.dim)
                        }
                    }
                    .padding(.vertical, 4)
                }
                .buttonStyle(.plain)
                .accessibilityValue(item.done ? Text("u.done") : Text(verbatim: ""))
            }
        }
    }

    // MARK: - Feed time

    private var feedCard: some View {
        UCard {
            ULabel("u.feed.title")
            if !shield.feedWatched {
                Text("u.feed.notWatched").font(.subheadline).foregroundStyle(UPalette.ink)
                    .fixedSize(horizontal: false, vertical: true)
                UPillButton(title: "u.feed.choose", systemImage: "apps.iphone") { onTool(.rules) }
            } else {
                let used = usedToday ?? 0
                let before = store.answers.beforeMinutes
                HStack(alignment: .firstTextBaseline) {
                    Text(verbatim: used == 0 ? "< 10 m" : "≥ " + UnrotFormat.short(minutes: used))
                        .font(.uDisplay(30)).foregroundStyle(UPalette.ink)
                    Spacer()
                    if before > 0 {
                        Text("u.feed.aim \(UnrotFormat.short(minutes: before))")
                            .font(.caption).foregroundStyle(UPalette.dim)
                    }
                }
                if before > 0 {
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Color.white.opacity(0.08))
                            Capsule().fill(used <= before ? UPalette.leaf : UPalette.warn)
                                .frame(width: geo.size.width * min(1, Double(used) / Double(max(1, before))))
                        }
                    }
                    .frame(height: 8)
                    .accessibilityHidden(true)
                }
                Text("u.feed.how").font(.caption2).foregroundStyle(UPalette.dim)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    // MARK: - Words

    @ViewBuilder private var fromYou: some View {
        if let note = store.notes.first ?? (store.answers.reason.isEmpty ? nil : store.answers.reason) {
            UCard {
                VStack(spacing: 6) {
                    ULabel("u.fromYou")
                    Text(verbatim: "“\(note)”").font(.uDisplay(22)).foregroundStyle(UPalette.ink)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                    if let peak = peakLine { Text(peak).font(.footnote).foregroundStyle(UPalette.dim) }
                }
                .frame(maxWidth: .infinity)
            }
        }
    }

    private var peakLine: LocalizedStringKey? {
        guard let first = store.answers.when.first else { return nil }
        return uKey("u.peak.", first)
    }

    private var thought: some View {
        let day = Calendar.current.ordinality(of: .day, in: .year, for: now) ?? 0
        return UCard {
            ULabel("u.thought.title")
            Text(uKey("u.thought.", day % 8))
                .font(.uDisplay(21, weight: .bold)).foregroundStyle(UPalette.ink)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}
