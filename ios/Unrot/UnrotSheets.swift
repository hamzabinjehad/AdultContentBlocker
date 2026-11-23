import SwiftUI

/// The daily check-in: mood and what pulls. Only on this phone.
struct UnrotCheckInSheet: View {
    @ObservedObject var store: UnrotStore
    @Environment(\.dismiss) private var dismiss
    @State private var mood: Int?
    @State private var pull: String?

    static let moods: [(String, LocalizedStringKey)] = [("cloud.rain", "u.mood.0"), ("cloud", "u.mood.1"),
        ("cloud.sun", "u.mood.2"), ("sun.max", "u.mood.3"), ("sparkles", "u.mood.4")]
    static let pulls: [(String, LocalizedStringKey)] = [("bored", "u.pull.bored"), ("tired", "u.pull.tired"),
        ("stress", "u.pull.stress"), ("alone", "u.pull.alone"), ("none", "u.pull.none")]

    var body: some View {
        USheet(title: "u.checkin.title", subtitle: "u.checkin.private") {
            HStack(spacing: 6) {
                ForEach(Self.moods.indices, id: \.self) { i in
                    Button { mood = i } label: {
                        VStack(spacing: 6) {
                            Image(systemName: Self.moods[i].0)
                                .font(.title3)
                                .frame(width: 52, height: 52)
                                .background(mood == i ? UPalette.gold.opacity(0.16) : UPalette.card, in: Circle())
                                .overlay { Circle().strokeBorder(mood == i ? UPalette.gold : UPalette.cardLine) }
                            Text(Self.moods[i].1).font(.caption2.weight(mood == i ? .bold : .regular))
                        }
                        .foregroundStyle(mood == i ? UPalette.goldLight : UPalette.ink)
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(UPressStyle())
                    .accessibilityAddTraits(mood == i ? .isSelected : [])
                }
            }
            ULabel("u.checkin.pullTitle").padding(.top, 6)
            ForEach(Self.pulls, id: \.0) { item in
                UOption(title: item.1, selected: pull == item.0) { pull = item.0 }
            }
            UGoldButton(title: "u.checkin.done", wide: true) {
                guard let mood else { return }
                store.checkIn(mood: mood, pull: pull ?? "none")
                dismiss()
            }
            .disabled(mood == nil)
            .opacity(mood == nil ? 0.5 : 1)
            .padding(.top, 6)
        }
        .onAppear {
            mood = store.checkedInToday?.mood
            pull = store.checkedInToday?.pull
        }
    }
}

/// "Had a hard moment": the person's own honest report. Adult content got
/// through breaks the glass; went looking and it was stopped cracks it (Breathe
/// within ten minutes seals it with gold); scrolling only slows the sprout.
struct UnrotHardMomentSheet: View {
    @ObservedObject var store: UnrotStore
    let onBreathe: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var scrolledDone = false

    var body: some View {
        USheet(title: "u.hard.title", subtitle: "u.hard.body") {
            if scrolledDone {
                UCard {
                    Text("u.hard.scrollDone").foregroundStyle(UPalette.ink)
                        .fixedSize(horizontal: false, vertical: true)
                }
                UGoldButton(title: "u.done", wide: true) { dismiss() }
            } else {
                UOption(title: "u.hard.stopped", detail: "u.hard.stoppedDetail",
                        systemImage: "hand.raised", selected: false) {
                    store.crack()
                    dismiss()
                    onBreathe()
                }
                UOption(title: "u.hard.adult", detail: "u.hard.adultDetail",
                        systemImage: "exclamationmark.triangle", selected: false) {
                    store.breakGlass()
                    dismiss()
                }
                UOption(title: "u.hard.scroll", systemImage: "iphone.gen3", selected: false) {
                    scrolledDone = true
                }
                UPillButton(title: "u.hard.notNow") { dismiss() }
            }
        }
    }
}

/// Instead: the person's own ideas from setup. Picking one closes the feed
/// apps for a short while so the idea has room (iOS's shortest schedule is 15 minutes).
struct UnrotInsteadSheet: View {
    @ObservedObject var store: UnrotStore
    let canClose: Bool
    let onGo: () -> Bool
    @Environment(\.dismiss) private var dismiss
    @State private var picked: String?
    @State private var closed: Bool?

    static let ideas: [(String, String, LocalizedStringKey)] = [
        ("sleep", "bed.double", "u.instead.sleep"), ("read", "book", "u.instead.read"),
        ("train", "figure.run", "u.instead.train"), ("friends", "person.2", "u.instead.friends"),
        ("study", "graduationcap", "u.instead.study"), ("make", "paintbrush", "u.instead.make"),
        ("outside", "leaf", "u.instead.outside"), ("family", "phone", "u.instead.family")]

    private var shown: [(String, String, LocalizedStringKey)] {
        let mine = Self.ideas.filter { store.answers.instead.contains($0.0) }
        return mine.isEmpty ? Self.ideas : mine
    }

    var body: some View {
        USheet(title: "u.instead.title", subtitle: "u.instead.body") {
            if let closed {
                UCard {
                    Text(closed ? "u.instead.closed" : "u.instead.open").foregroundStyle(UPalette.ink)
                        .fixedSize(horizontal: false, vertical: true)
                }
                UGoldButton(title: "u.instead.offIGo", wide: true) { dismiss() }
            } else {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                    ForEach(shown, id: \.0) { idea in
                        Button { picked = idea.0 } label: {
                            VStack(alignment: .leading, spacing: 18) {
                                Image(systemName: idea.1).font(.title3)
                                Text(idea.2).font(.subheadline.weight(.bold))
                            }
                            .foregroundStyle(picked == idea.0 ? UPalette.goldLight : UPalette.ink)
                            .frame(maxWidth: .infinity, minHeight: 92, alignment: .bottomLeading)
                            .padding(14)
                            .background(picked == idea.0 ? UPalette.gold.opacity(0.14) : UPalette.card,
                                        in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                            .overlay {
                                RoundedRectangle(cornerRadius: 20, style: .continuous)
                                    .strokeBorder(picked == idea.0 ? UPalette.gold : UPalette.cardLine)
                            }
                        }
                        .buttonStyle(UPressStyle())
                        .accessibilityAddTraits(picked == idea.0 ? .isSelected : [])
                    }
                }
                UGoldButton(title: "u.instead.go", systemImage: "arrow.forward", wide: true) {
                    closed = canClose ? onGo() : false
                }
                .disabled(picked == nil)
                .opacity(picked == nil ? 0.5 : 1)
            }
        }
    }
}

/// My notes as a journal on a calendar: each note sits on its day, a gold dot
/// marks days with notes and a small mood dot the day's check-in. Tap a day to
/// read or add to it; days still to come take no notes.
struct UnrotNotesSheet: View {
    @ObservedObject var store: UnrotStore
    @State private var month = Date()
    @State private var selected = Date()
    @State private var draft = ""
    @FocusState private var focused: Bool
    @Environment(\.calendar) private var calendar

    private var todayKey: String { UnrotFormat.dayKey(Date()) }
    private var selectedKey: String { UnrotFormat.dayKey(selected) }
    private var canWrite: Bool { selectedKey <= todayKey }

    var body: some View {
        USheet(title: "u.notes.title", subtitle: "u.notes.body", tall: true) {
            calendarCard
            dayHeader
            if canWrite { composer }
            let notes = store.notes(on: selected)
            if notes.isEmpty {
                Text(canWrite ? "u.notes.emptyDay" : "u.notes.future").font(.footnote).foregroundStyle(UPalette.dim)
            }
            ForEach(notes) { note in
                UCard(padding: 14) {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(verbatim: "“\(note.text)”").font(.uDisplay(.body)).foregroundStyle(UPalette.ink)
                                .fixedSize(horizontal: false, vertical: true)
                            Text(note.at.formatted(date: .omitted, time: .shortened))
                                .font(.caption2).foregroundStyle(UPalette.dim)
                        }
                        Spacer()
                        Button { store.removeNote(id: note.id) } label: {
                            Image(systemName: "trash").foregroundStyle(UPalette.dim).frame(width: 36, height: 36)
                        }
                        .accessibilityLabel(Text("u.notes.remove"))
                    }
                }
            }
        }
    }

    // MARK: - Calendar

    private var calendarCard: some View {
        UCard(padding: 14) {
            HStack {
                Button { shift(-1) } label: {
                    Image(systemName: "chevron.backward").frame(width: 40, height: 40)
                }
                .accessibilityLabel(Text("u.notes.prevMonth"))
                Spacer()
                Text(month.formatted(.dateTime.month(.wide).year()))
                    .font(.headline).foregroundStyle(UPalette.ink)
                    .accessibilityAddTraits(.isHeader)
                Spacer()
                Button { shift(1) } label: {
                    Image(systemName: "chevron.forward").frame(width: 40, height: 40)
                }
                .disabled(calendar.isDate(month, equalTo: Date(), toGranularity: .month))
                .accessibilityLabel(Text("u.notes.nextMonth"))
            }
            .foregroundStyle(UPalette.goldLight)
            let columns = Array(repeating: GridItem(.flexible(), spacing: 2), count: 7)
            LazyVGrid(columns: columns, spacing: 4) {
                ForEach(Array(NotesCalendar.weekdaySymbols(calendar: calendar).enumerated()), id: \.offset) { _, symbol in
                    Text(verbatim: symbol).font(.caption2.weight(.bold)).foregroundStyle(UPalette.dim)
                        .accessibilityHidden(true)
                }
                ForEach(Array(NotesCalendar.monthGrid(for: month, calendar: calendar).enumerated()), id: \.offset) { _, day in
                    if let day { cell(day) } else { Color.clear.frame(height: 44) }
                }
            }
            HStack(spacing: 14) {
                legend(UPalette.gold, "u.notes.legendNote")
                legend(UPalette.leaf, "u.notes.legendCheckin")
            }
            .padding(.top, 2)
        }
        .gesture(DragGesture(minimumDistance: 30).onEnded { value in
            if value.translation.width < -40 { shift(1) } else if value.translation.width > 40 { shift(-1) }
        })
    }

    private func cell(_ day: Date) -> some View {
        let key = UnrotFormat.dayKey(day)
        let isSelected = key == selectedKey
        let isToday = key == todayKey
        let hasNote = store.noteDays.contains(key)
        let mood = store.checkIns[key]?.mood
        let future = key > todayKey
        return Button { selected = day } label: {
            VStack(spacing: 3) {
                Text(verbatim: "\(calendar.component(.day, from: day))")
                    .font(.subheadline.weight(isToday || isSelected ? .bold : .regular))
                    .foregroundStyle(isSelected ? UPalette.onGold : future ? UPalette.dim.opacity(0.5) : UPalette.ink)
                    .frame(width: 34, height: 34)
                    .background {
                        if isSelected { Circle().fill(UPalette.goldFill) }
                        else if isToday { Circle().strokeBorder(UPalette.gold, lineWidth: 1.5) }
                    }
                HStack(spacing: 3) {
                    Circle().fill(hasNote ? UPalette.gold : .clear).frame(width: 5, height: 5)
                    Circle().fill(mood.map(moodColor) ?? .clear).frame(width: 5, height: 5)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(day.formatted(date: .complete, time: .omitted)))
        .accessibilityValue(hasNote ? Text("u.notes.hasNotes") : Text(verbatim: ""))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func moodColor(_ mood: Int) -> Color {
        [Color(red: 0.55, green: 0.6, blue: 0.85), UPalette.night, UPalette.dim, UPalette.leaf, UPalette.leafBright][max(0, min(4, mood))]
    }

    private func legend(_ color: Color, _ key: LocalizedStringKey) -> some View {
        HStack(spacing: 5) {
            Circle().fill(color).frame(width: 6, height: 6)
            Text(key).font(.caption2).foregroundStyle(UPalette.dim)
        }
    }

    private func shift(_ months: Int) {
        guard let next = calendar.date(byAdding: .month, value: months, to: month) else { return }
        if next > Date() && !calendar.isDate(next, equalTo: Date(), toGranularity: .month) { return }
        month = next
    }

    // MARK: - The day

    private var dayHeader: some View {
        HStack {
            (calendar.isDateInToday(selected) ? Text("u.notes.today")
                 : Text(selected.formatted(.dateTime.weekday(.wide).day().month(.wide))))
                .font(.uDisplay(20)).foregroundStyle(UPalette.ink)
            Spacer()
            if let mood = store.checkIns[selectedKey]?.mood {
                Text(UnrotCheckInSheet.moods[max(0, min(4, mood))].1)
                    .font(.caption.weight(.bold)).foregroundStyle(moodColor(mood))
                    .padding(.horizontal, 10).frame(minHeight: 26)
                    .background(moodColor(mood).opacity(0.14), in: Capsule())
            }
        }
        .padding(.top, 4)
    }

    private var composer: some View {
        HStack(spacing: 8) {
            TextField("u.notes.placeholder", text: $draft, axis: .vertical)
                .lineLimit(1...4)
                .focused($focused)
                .padding(14)
                .background(UPalette.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .overlay { RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(UPalette.gold.opacity(0.6)) }
                .foregroundStyle(UPalette.ink)
            Button {
                store.addNote(draft, on: selected)
                draft = ""
            } label: {
                Image(systemName: "plus").font(.headline.weight(.bold))
                    .foregroundStyle(UPalette.onGold)
                    .frame(width: 48, height: 48)
                    .background(UPalette.goldFill, in: Circle())
            }
            .accessibilityLabel(Text("u.notes.add"))
            .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty)
        }
    }
}

/// Focus now: close the feed apps for a while. Ends on its own.
struct UnrotFocusSheet: View {
    @ObservedObject var store: UnrotStore
    let canClose: Bool
    let onStart: (Int) -> Bool
    let onEnd: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var minutes = 25
    @State private var failed = false

    var body: some View {
        USheet(title: "u.focus.title", subtitle: "u.focus.body") {
            if store.focusActive {
                UCard {
                    Text("u.focus.until \(Date(timeIntervalSince1970: store.focusUntil).formatted(date: .omitted, time: .shortened))")
                        .font(.headline).foregroundStyle(UPalette.ink)
                }
                UPillButton(title: "u.focus.end", systemImage: "stop.fill") { onEnd(); dismiss() }
            } else if !canClose {
                UCard { Text("u.focus.needsRule").foregroundStyle(UPalette.ink).fixedSize(horizontal: false, vertical: true) }
            } else {
                HStack(spacing: 8) {
                    ForEach([15, 25, 45, 60, 90], id: \.self) { value in
                        Button { minutes = value } label: {
                            Text(verbatim: "\(value)")
                                .font(.headline)
                                .frame(maxWidth: .infinity, minHeight: 48)
                                .foregroundStyle(minutes == value ? UPalette.onGold : UPalette.ink)
                                .background {
                                    if minutes == value { Capsule().fill(UPalette.goldFill) } else { Capsule().fill(UPalette.card) }
                                }
                        }
                        .buttonStyle(UPressStyle())
                        .accessibilityLabel(Text("u.focus.minutes \(value)"))
                        .accessibilityAddTraits(minutes == value ? .isSelected : [])
                    }
                }
                if failed {
                    Text("u.focus.failed").font(.footnote).foregroundStyle(UPalette.warn)
                }
                UGoldButton(title: "u.focus.start", systemImage: "scope", wide: true) {
                    if onStart(minutes) { dismiss() } else { failed = true }
                }
            }
        }
    }
}

/// What "time given back" means, and where it comes from on iPhone.
struct UnrotGivenBackInfo: View {
    var body: some View {
        USheet(title: "u.info.title") {
            Text("u.info.body").foregroundStyle(UPalette.ink).fixedSize(horizontal: false, vertical: true)
            Text("u.info.iphone").font(.footnote).foregroundStyle(UPalette.dim).fixedSize(horizontal: false, vertical: true)
            ULabel("u.info.stagesTitle").padding(.top, 4)
            ForEach(0...GlassStage.last, id: \.self) { i in
                HStack {
                    Text(LocalizedStringKey(GlassStage.nameKeys[i])).foregroundStyle(UPalette.ink)
                    Spacer()
                    Text(verbatim: UnrotFormat.short(minutes: GlassStage.thresholds[i])).foregroundStyle(UPalette.dim)
                }
                .font(.subheadline)
            }
            ULabel("u.info.glassTitle").padding(.top, 4)
            Text("u.info.glass").font(.footnote).foregroundStyle(UPalette.dim).fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// Every tool in one place.
struct UnrotMoreSheet: View {
    let onPick: (UnrotTool) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        USheet(title: "u.more.title") {
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 4), spacing: 16) {
                ForEach(UnrotTool.allCases) { tool in
                    UToolButton(title: tool.title, systemImage: tool.icon) {
                        dismiss()
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { onPick(tool) }
                    }
                }
            }
        }
    }
}

enum UnrotTool: String, CaseIterable, Identifiable {
    case breathe, focus, notes, instead, checkIn, hardMoment, rules, lock
    var id: String { rawValue }
    var title: LocalizedStringKey {
        switch self {
        case .breathe: return "u.tool.breathe"
        case .focus: return "u.tool.focus"
        case .notes: return "u.tool.notes"
        case .instead: return "u.tool.instead"
        case .checkIn: return "u.tool.checkin"
        case .hardMoment: return "u.tool.hard"
        case .rules: return "u.tool.rules"
        case .lock: return "u.tool.lock"
        }
    }
    var icon: String { Self.icons[self] ?? "circle" }
    private static let icons: [UnrotTool: String] = [.breathe: "wind", .focus: "scope", .notes: "book.closed",
        .instead: "lightbulb", .checkIn: "checkmark.circle", .hardMoment: "heart",
        .rules: "slider.horizontal.3", .lock: "lock"]
}
