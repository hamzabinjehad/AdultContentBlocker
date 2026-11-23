import SwiftUI

/// A tapped day, as a sheet identity.
struct DayRef: Identifiable, Equatable {
    let date: Date
    var id: String { UnrotFormat.dayKey(date) }
}

/// One day, opened from Progress: its plant, feed time against before, time
/// given back, check-in, pauses, notes and (privately) what was stopped.
/// Swipe or use the arrows to walk through days; days to come are closed.
struct UnrotDayView: View {
    @ObservedObject var store: UnrotStore
    @State var date: Date
    let onOpenNotes: () -> Void
    @Environment(\.calendar) private var calendar

    private var summary: DaySummary { store.summary(for: date) }
    private var canGoForward: Bool { !calendar.isDateInToday(date) }

    var body: some View {
        USheet(title: titleKey, tall: true) {
            header
            plant
            numbers
            checkIn
            notes
            if summary.triesStopped > 0 { keptOut }
        }
        .gesture(DragGesture(minimumDistance: 40).onEnded { value in
            if value.translation.width < -60 { move(1) } else if value.translation.width > 60 { move(-1) }
        })
    }

    private var titleKey: LocalizedStringKey { calendar.isDateInToday(date) ? "u.notes.today" : "u.day.title" }

    // MARK: - Header

    private var header: some View {
        HStack {
            Button { move(-1) } label: { Image(systemName: "chevron.backward").frame(width: 44, height: 44) }
                .accessibilityLabel(Text("u.day.previous"))
            Spacer()
            Text(date.formatted(.dateTime.weekday(.wide).day().month(.wide)))
                .font(.headline).foregroundStyle(UPalette.ink)
                .accessibilityAddTraits(.isHeader)
            Spacer()
            Button { move(1) } label: { Image(systemName: "chevron.forward").frame(width: 44, height: 44) }
                .disabled(!canGoForward)
                .opacity(canGoForward ? 1 : 0.3)
                .accessibilityLabel(Text("u.day.next"))
        }
        .foregroundStyle(UPalette.goldLight)
    }

    private func move(_ days: Int) {
        guard let next = calendar.date(byAdding: .day, value: days, to: date),
              calendar.startOfDay(for: next) <= calendar.startOfDay(for: Date()) else { return }
        withAnimation(.easeInOut(duration: 0.2)) { date = next }
        UISelectionFeedbackGenerator().selectionChanged()
    }

    // MARK: - The plant

    private var plant: some View {
        let s = summary
        let size: Double = s.isToday ? 0.35 : s.gain.map { 0.2 + 0.8 * min(1, Double($0) / Double(max(1, s.before)) * 2) } ?? 0
        return VStack(spacing: 6) {
            Pot(size: size).frame(width: 90, height: 110)
            Text(plantLine(s)).font(.footnote).foregroundStyle(UPalette.dim).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }

    private func plantLine(_ s: DaySummary) -> LocalizedStringKey {
        if s.beforeStart { return "u.day.beforeStart" }
        if s.isToday { return "u.day.growing" }
        if s.gain == nil { return "u.day.noReading" }
        return (s.gain ?? 0) > 0 ? "u.day.grew" : "u.day.small"
    }

    // MARK: - Numbers

    private var numbers: some View {
        let s = summary
        return HStack(spacing: 10) {
            tile(label: "u.day.feed",
                 value: s.feed.map { $0 == 0 ? "< 10 m" : "≥ " + UnrotFormat.short(minutes: $0) } ?? "—",
                 detail: s.difference.map { diffText($0) }, tint: s.difference.map { $0 <= 0 ? UPalette.leaf : UPalette.warn })
            tile(label: "u.day.given",
                 value: s.isToday ? "…" : s.gain.map { UnrotFormat.short(minutes: $0) } ?? "—",
                 detail: s.isToday ? Text("u.day.countedTomorrow") : nil, tint: UPalette.goldLight)
            tile(label: "u.day.pauses", value: "\(s.pauses)", detail: nil, tint: UPalette.ink)
        }
    }

    private func diffText(_ minutes: Int) -> Text {
        let amount = UnrotFormat.short(minutes: abs(minutes))
        return minutes <= 0 ? Text("u.day.under \(amount)") : Text("u.day.over \(amount)")
    }

    private func tile(label: LocalizedStringKey, value: String, detail: Text?, tint: Color?) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.caption2.weight(.bold)).textCase(.uppercase).foregroundStyle(UPalette.dim)
            Text(verbatim: value).font(.uDisplay(22)).foregroundStyle(UPalette.ink).lineLimit(1).minimumScaleFactor(0.6)
            if let detail { detail.font(.caption2).foregroundStyle(tint ?? UPalette.dim).fixedSize(horizontal: false, vertical: true) }
        }
        .frame(maxWidth: .infinity, minHeight: 92, alignment: .topLeading)
        .padding(12)
        .background(UPalette.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay { RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(UPalette.cardLine) }
        .accessibilityElement(children: .combine)
    }

    // MARK: - Check-in

    private var checkIn: some View {
        UCard(padding: 14) {
            ULabel("u.day.checkin")
            if let mood = summary.mood {
                let entry = UnrotCheckInSheet.moods[max(0, min(4, mood))]
                HStack(spacing: 12) {
                    Image(systemName: entry.0).font(.title3).foregroundStyle(UPalette.goldLight)
                        .frame(width: 44, height: 44).background(UPalette.gold.opacity(0.12), in: Circle())
                    VStack(alignment: .leading, spacing: 2) {
                        Text(entry.1).font(.headline).foregroundStyle(UPalette.ink)
                        if let pull = summary.pull {
                            let pullName = Text(uKey("u.pull.", pull))
                            Text("u.day.pulledBy \(pullName)").font(.footnote).foregroundStyle(UPalette.dim)
                        }
                    }
                }
            } else {
                Text("u.day.noCheckin").font(.footnote).foregroundStyle(UPalette.dim)
            }
        }
    }

    // MARK: - Notes

    private var notes: some View {
        UCard(padding: 14) {
            HStack {
                ULabel("u.notes.title")
                Spacer()
                Button(action: onOpenNotes) {
                    Text("u.day.openNotes").font(.caption.weight(.bold)).foregroundStyle(UPalette.goldLight)
                }
            }
            if summary.notes.isEmpty {
                Text("u.notes.emptyDay").font(.footnote).foregroundStyle(UPalette.dim)
            }
            ForEach(summary.notes, id: \.self) { note in
                Text(verbatim: "“\(note)”").font(.uDisplay(.body)).foregroundStyle(UPalette.ink)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    // MARK: - Kept out (private)

    private var keptOut: some View {
        HStack(spacing: 10) {
            Image(systemName: "shield.lefthalf.filled").foregroundStyle(UPalette.leaf)
            Text("u.day.stopped \(summary.triesStopped)").font(.footnote).foregroundStyle(UPalette.ink)
            Spacer()
            Image(systemName: "lock.fill").font(.caption).foregroundStyle(UPalette.dim)
                .accessibilityLabel(Text("u.kept.private"))
        }
        .padding(14)
        .background(UPalette.leaf.opacity(0.08), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}
