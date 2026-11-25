import SwiftUI
import UIKit

/// The Islamic edition after the first run, as in the prototype: Home, Progress, Help in the middle,
/// Library and Tools. Tools opens the existing protection screens unchanged, so nothing is lost.
struct HisnShell: View {
    @ObservedObject var protection: ProtectionController
    @Binding var language: String
    @Environment(\.hisnLook) private var look
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var usage = AppUsageController()
    @StateObject private var vault = PasscodeVaultController()
    @StateObject private var lockDevice = LockConfirmationDevice()
    @State private var tab = 0
    @State private var showingHelp = false
    @State private var showingBreath = false
    @State private var showingSlip = false
    @State private var showingPause = false
    @State private var pausePrompt: HisnPausePrompt?
    @State private var showingSetup = false
    @State private var showingApps = false
    @State private var pauseCount: Int?
    @State private var practiceDays: Set<String> = []
    @State private var streak = 0
    private let helpTag = 9

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack { HisnHomeView(protection: protection, onProgress: { tab = 1 }, onLibrary: { tab = 3 }, onTools: { tab = 4 }) }
                .tabItem { Label(ia("Home", "الرئيسية"), systemImage: "house.fill") }.tag(0)
            NavigationStack { HisnProgressView() }
                .tabItem { Label(ia("Progress", "التقدّم"), systemImage: "chart.bar.fill") }.tag(1)
            Color.clear
                .tabItem { Label(ia("Help", "ساعدني"), systemImage: "exclamationmark.circle.fill") }.tag(helpTag)
            NavigationStack { HisnLibraryView() }
                .tabItem { Label(ia("Library", "المكتبة"), systemImage: "book.fill") }.tag(3)
            NavigationStack { tools }
                .tabItem { Label(ia("Tools", "الأدوات"), systemImage: "square.grid.2x2.fill") }.tag(4)
        }
        .tint(look.pri)
        .onChange(of: tab) { [previous = tab] next in
            guard next == helpTag else { return }
            tab = previous == helpTag ? 0 : previous
            showingHelp = true
        }
        .fullScreenCover(isPresented: $showingHelp) {
            HisnHelpView(onBreathe: { showingBreath = true }, onSlip: { showingSlip = true }, onClose: { showingHelp = false })
                .hisnEnvironment(look)
                .fullScreenCover(isPresented: $showingBreath) { HisnBreatheView(onClose: { showingBreath = false }).hisnEnvironment(look) }
                .fullScreenCover(isPresented: $showingSlip) { HisnComeBackView(onClose: { showingSlip = false; showingHelp = false }).hisnEnvironment(look) }
        }
        .fullScreenCover(isPresented: $showingPause) { PauseView(onClose: { showingPause = false; refreshDerivedState() }) }
        .fullScreenCover(item: $pausePrompt) { prompt in
            // The shield's pause action hands the request to the app; this is the only place it becomes a session.
            PauseRuleGrantView(usage: usage, appName: prompt.request.appName, onClose: { pausePrompt = nil; refreshDerivedState() })
        }
        .onAppear { refreshDerivedState(); takePauseRuleRequest(); Task { await HisnReminders.reschedule() } }
        .onChange(of: scenePhase) { phase in
            if phase == .active { refreshDerivedState(); takePauseRuleRequest() }
        }
        .onChange(of: protection.checkedAt) { _ in refreshDerivedState() }
    }

    private var tools: some View {
        HisnToolsView(
            protection: protection,
            onBreathe: { showingBreath = true; showingHelp = false },
            shield: AnyView(TodayView(protection: protection, usage: usage, streak: streak, practiceDays: practiceDays, pauseCount: pauseCount,
                                      onProtection: { showingSetup = true }, onApps: { showingApps = true }, onPause: { showingPause = true },
                                      onRefresh: { Task { await protection.refresh() } })),
            apps: AnyView(AppsView(protection: protection, usage: usage, onProtection: { showingSetup = true }, onPause: { showingPause = true })),
            practice: AnyView(PracticeView(streak: streak, practiceDays: practiceDays, pauseCount: pauseCount, now: protection.now, onPause: { showingPause = true })),
            more: AnyView(MoreView(protection: protection, usage: usage, vault: vault, lockDevice: lockDevice, language: $language)),
            setup: AnyView(BlockingLayersSetupView(protection: protection)))
            .fullScreenCover(isPresented: $showingBreath) { HisnBreatheView(onClose: { showingBreath = false }).hisnEnvironment(look) }
            .sheet(isPresented: $showingSetup) {
                NavigationStack { BlockingLayersSetupView(protection: protection).toolbar { ToolbarItem(placement: .cancellationAction) { Button(ia("Done", "تم")) { showingSetup = false } } } }
            }
            .sheet(isPresented: $showingApps) {
                NavigationStack { AppsView(protection: protection, usage: usage, onProtection: { showingApps = false; showingSetup = true }, onPause: { showingApps = false; showingPause = true })
                    .toolbar { ToolbarItem(placement: .cancellationAction) { Button(ia("Done", "تم")) { showingApps = false } } } }
            }
    }

    private func refreshDerivedState() {
        usage.refresh(trustedNow: protection.trustedNow)
        pauseCount = PauseStore.todayCount()
        let store = PauseRuleStore.appGroup
        practiceDays = Set(store.practiceDays())
        streak = store.streak(now: protection.now)
    }

    private func takePauseRuleRequest() {
        guard pausePrompt == nil, !showingPause else { return }
        if let request = usage.takePauseRequest() { pausePrompt = HisnPausePrompt(request: request) }
    }
}

private struct HisnPausePrompt: Identifiable {
    let id = UUID()
    let request: PauseRule.Request
}

extension View {
    /// The look and the reading direction every Hisn sheet needs.
    func hisnEnvironment(_ look: HisnLook) -> some View {
        environment(\.hisnLook, look).environment(\.layoutDirection, hisnArabic() ? .rightToLeft : .leftToRight)
    }
}

// MARK: - Shared pieces

/// A plain page in the look: sky colour, a title, scrolling content.
struct HisnPage<Content: View>: View {
    @Environment(\.hisnLook) private var look
    let title: String
    @ViewBuilder let content: Content
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) { content }
                .padding(.horizontal, 18).padding(.bottom, 30).frame(maxWidth: 620).frame(maxWidth: .infinity)
        }
        .background(look.sky.ignoresSafeArea())
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .preferredColorScheme(look.dark ? .dark : .light)
    }
}

struct HisnKicker: View {
    @Environment(\.hisnLook) private var look
    let text: String
    var body: some View { Text(text).font(.system(size: 12.5, weight: .bold)).foregroundColor(look.sub) }
}

/// One saying set like the book, with save and share.
struct HisnSayingCard: View {
    @Environment(\.hisnLook) private var look
    let saying: HisnSaying
    @State private var saved = false
    @State private var expanded = false
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(rtl(saying.text)).font(.custom("GeezaPro", size: 17)).lineSpacing(8).foregroundColor(look.ink)
                .multilineTextAlignment(.leading).environment(\.layoutDirection, .rightToLeft)
                .frame(maxWidth: .infinity, alignment: .trailing)
                .lineLimit(expanded || saying.text.count <= 320 ? nil : 6)
                .fixedSize(horizontal: false, vertical: true)
            if saying.text.count > 320 && !expanded {
                Button(ia("Read it all", "اقرأ كاملًا")) { expanded = true }.font(.system(size: 13, weight: .bold)).foregroundColor(look.pri)
            }
            HStack {
                Text(hisnArabic() ? rtl("— " + saying.source) : "— " + saying.source).font(.system(size: 12, weight: .medium)).foregroundColor(look.sub)
                Spacer()
                ShareLink(item: "«\(saying.text)»\n— \(saying.source)") { Image(systemName: "square.and.arrow.up") }
                    .foregroundColor(look.sub).accessibilityLabel(ia("Share", "مشاركة"))
                Button {
                    var s = HisnSayings.shared.saved
                    if s.contains(saying.index) { s.remove(saying.index) } else { s.insert(saying.index) }
                    HisnSayings.shared.saved = s; saved = s.contains(saying.index)
                } label: { Image(systemName: saved ? "heart.fill" : "heart").foregroundColor(saved ? Color(hex: 0xD2526B) : look.sub) }
                .accessibilityLabel(ia("Save", "حفظ"))
            }
        }
        .padding(14).hisnCard(look)
        .onAppear { saved = HisnSayings.shared.saved.contains(saying.index) }
    }
}

// MARK: - Home

struct HisnHomeView: View {
    @ObservedObject var protection: ProtectionController
    let onProgress: () -> Void
    let onLibrary: () -> Void
    let onTools: () -> Void
    @Environment(\.hisnLook) private var look
    @AppStorage("hisn.onboard.name") private var name = ""
    @State private var prayedMask = HisnPrayers.prayed()
    @State private var skip = 0
    @State private var refresh = 0

    private var nights: Int { HisnJourney.nights }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                hero
                weekStrip
                prayers
                todayCard
                wisdom
                verse
            }
            .padding(.bottom, 30)
            .frame(maxWidth: 620).frame(maxWidth: .infinity)
        }
        .background(look.sky.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .preferredColorScheme(look.dark ? .dark : .light)
        .onAppear { prayedMask = HisnPrayers.prayed(); refresh += 1 }
        .task { await protection.refresh() }
    }

    // The painted hero: greeting and both dates, the nights ring with tonight's watch, the next station, the shield chip.
    private var hero: some View {
        let stations = HisnJourney.stations(for: nights)
        let base = stations.previous?.nights ?? 0
        let progress = nights >= stations.next.nights ? 1 : Double(nights - base) / Double(stations.next.nights - base)
        let missing = HisnPermsPage.missing(protection)
        let start = HisnJourney.date(of: HisnJourney.startDay)
        return VStack(spacing: 12) {
                HStack(spacing: 10) {
                    HisnGateMark().fill(look.ink, style: FillStyle(eoFill: true)).frame(width: 28, height: 28)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(name.isEmpty ? ia("As-salāmu ʿalaykum", "السلام عليكم") : ia("As-salāmu ʿalaykum, \(name)", "السلام عليكم يا \(name)"))
                            .font(.system(size: 16, weight: .bold)).foregroundColor(look.ink)
                        Text(HisnDates.both(Date())).font(.system(size: 12)).foregroundColor(look.sub)
                    }
                    Spacer()
                    Button(action: onTools) {
                        Image(systemName: "person.fill").font(.system(size: 15)).foregroundColor(look.ink)
                            .frame(width: 40, height: 40).background(Circle().fill(look.card.opacity(0.85)))
                    }
                    .accessibilityLabel(ia("Settings", "الإعدادات"))
                }
                .padding(.horizontal, 18).padding(.top, 8)
                Button(action: onProgress) {
                    ZStack {
                        Circle().stroke(look.line.opacity(0.6), lineWidth: 7)
                        Circle().trim(from: 0, to: progress).stroke(look.acc, style: StrokeStyle(lineWidth: 7, lineCap: .round))
                            .rotationEffect(.degrees(-90)).environment(\.layoutDirection, .leftToRight)
                        VStack(spacing: 2) {
                            Text(ia("nights of light", "ليالٍ من النور")).font(.system(size: 12, weight: .semibold)).foregroundColor(look.sub)
                            Text("\(nights)").font(.system(size: 58, weight: .bold, design: .rounded)).foregroundColor(look.ink)
                            TimelineView(.periodic(from: .now, by: 1)) { ctx in
                                Text(Self.watch(ctx.date)).font(.system(size: 13, weight: .semibold, design: .monospaced)).foregroundColor(look.sub)
                            }
                        }
                    }
                    .frame(width: 200, height: 200)
                    .background(Circle().fill(look.card.opacity(0.55)))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(ia("\(nights) nights of light. Your progress", "\(nights) ليالٍ من النور. تقدّمك"))
                Text(ia("Since \(HisnDates.first(start)) (\(HisnDates.second(start)))\n\(max(0, stations.next.nights - nights)) nights to \(stations.next.name)",
                        "منذ \(HisnDates.first(start)) (\(HisnDates.second(start)))\n\(max(0, stations.next.nights - nights)) ليالٍ إلى \(stations.next.name)"))
                    .font(.system(size: 13)).foregroundColor(look.ink).multilineTextAlignment(.center)
                Button(action: onTools) {
                    HStack(spacing: 6) {
                        Image(systemName: "shield.fill").font(.system(size: 13))
                        Text(missing == 0 ? ia("Protected · the filters are on", "محميّ · المرشّحات تعمل")
                             : ia("\(missing) protection\(missing > 1 ? "s" : "") off · fix", "\(missing) من الحماية متوقفة · أصلحها"))
                            .font(.system(size: 13, weight: .semibold))
                    }
                    .foregroundColor(missing == 0 ? look.kept : Color(hex: 0xB86A12))
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(Capsule().fill(look.card.opacity(0.92)))
                }
                .buttonStyle(.plain)
                .padding(.bottom, 26)
        }
        .frame(maxWidth: .infinity)
        .background(
            Image(look.background).resizable().scaledToFill()
                .overlay(LinearGradient(colors: [.clear, .clear, look.sky], startPoint: .top, endPoint: .bottom))
                .ignoresSafeArea(edges: .top)
                .clipped())
    }

    static func watch(_ date: Date) -> String {
        let c = Calendar.current.dateComponents([.hour, .minute, .second], from: date)
        return String(format: "%02d:%02d:%02d", c.hour ?? 0, c.minute ?? 0, c.second ?? 0)
    }

    private var weekStrip: some View {
        HStack(spacing: 4) {
            ForEach((0..<7).reversed(), id: \.self) { back in
                let day = HisnJourney.today - back
                let date = HisnJourney.date(of: day)
                let kept = day < HisnJourney.today && day >= HisnJourney.startDay && !HisnJourney.slipped(day)
                let slip = HisnJourney.slipped(day)
                VStack(spacing: 5) {
                    Text(HisnDates.weekdayLetter(date)).font(.system(size: 11, weight: .semibold)).foregroundColor(look.sub)
                    Text("\(Calendar.current.component(.day, from: date))").font(.system(size: 12, weight: .semibold))
                        .foregroundColor(kept ? Color(hex: 0x3A2200) : look.sub)
                        .frame(width: 32, height: 32)
                        .background(Circle().fill(kept ? Color(hex: 0xD9A441) : .clear))
                        .overlay(Circle().stroke(slip ? Color(hex: 0xC24848) : (back == 0 ? look.pri : look.line), lineWidth: kept ? 0 : (back == 0 ? 2 : 1.5)))
                }
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 14)
        .id(refresh)
    }

    private var prayers: some View {
        let times = HisnPrayers.times()
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                HisnKicker(text: ia("Prayers today", "صلوات اليوم"))
                Spacer()
                Text("\(prayedMask.nonzeroBitCount)/5").font(.system(size: 12.5, weight: .bold)).foregroundColor(look.sub)
            }
            TimelineView(.periodic(from: .now, by: 1)) { ctx in
                let now = HisnPrayers.nowMinutes(ctx.date)
                let next = times.nextOfFive(now)
                VStack(spacing: 10) {
                    HStack(spacing: 6) {
                        ForEach(0..<5, id: \.self) { i in
                            let ok = prayedMask & (1 << i) != 0
                            Button {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                HisnPrayers.toggle(i); prayedMask = HisnPrayers.prayed()
                            } label: {
                                VStack(spacing: 4) {
                                    ZStack {
                                        Circle().fill(ok ? look.kept : look.card).frame(width: 24, height: 24)
                                            .overlay(Circle().stroke(ok ? .clear : look.line, lineWidth: 1.5))
                                        if ok { Image(systemName: "checkmark").font(.system(size: 11, weight: .bold)).foregroundColor(.white) }
                                    }
                                    Text(ia(HisnPrayers.names[i].0, HisnPrayers.names[i].1)).font(.system(size: 12, weight: next == i ? .bold : .medium)).foregroundColor(look.ink)
                                    Text(HisnPrayerTimes.hhmm(times.five[i])).font(.system(size: 12.5, weight: .semibold)).foregroundColor(look.sub)
                                }
                                .frame(maxWidth: .infinity).padding(.vertical, 8)
                                .background(RoundedRectangle(cornerRadius: 14).fill(next == i ? look.chip : look.soft))
                                .overlay(RoundedRectangle(cornerRadius: 14).stroke(next == i ? look.pri : .clear, lineWidth: 1.5))
                            }
                            .buttonStyle(.plain)
                            .accessibilityAddTraits(ok ? .isSelected : [])
                        }
                    }
                    let left = times.minutesUntilNext(now) * 60 - Calendar.current.component(.second, from: ctx.date)
                    Text(ia("\(HisnPrayers.names[next].0) in \(Self.clock(left)) · \(HisnPrayers.place.name)",
                            "\(HisnPrayers.names[next].1) بعد \(Self.clock(left)) · \(HisnPrayers.place.name)"))
                        .font(.system(size: 13)).foregroundColor(look.sub)
                }
            }
        }
        .padding(14).hisnCard(look, radius: 20).padding(.horizontal, 16)
    }

    static func clock(_ seconds: Int) -> String {
        let s = max(0, seconds)
        return String(format: "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    }

    private var todayCard: some View {
        let tasks: [(String, String, String, String, String, String, AnyView)] = [
            ("adhkar", "misbaha", "Morning adhkār", "أذكار الصباح", "From Ḥiṣn al-Muslim · with a counter", "من حصن المسلم · مع العدّاد", AnyView(HisnAdhkarView())),
            ("read", "quran", "Today's reading", "ورد اليوم", "A verse and a scholar's word", "آية وكلمة من العلماء", AnyView(HisnReadingView())),
            ("reflect", "lantern", "Evening reckoning", "محاسبة المساء", "A minute, then a note", "دقيقة، ثم ملاحظة", AnyView(HisnReckoningView()))
        ]
        let done = tasks.filter { HisnJourney.done($0.0) }.count
        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(ia("Today", "اليوم")).font(.system(size: 18, weight: .bold)).foregroundColor(look.ink)
                Spacer()
                Capsule().fill(look.line).frame(width: 80, height: 6)
                    .overlay(alignment: .leading) { Capsule().fill(look.kept).frame(width: 80 * CGFloat(done) / 3, height: 6) }
                    .environment(\.layoutDirection, .leftToRight)
                Text("\(done)/3").font(.system(size: 12.5, weight: .bold)).foregroundColor(look.sub)
            }
            ForEach(tasks, id: \.0) { t in
                let ok = HisnJourney.done(t.0)
                NavigationLink(destination: t.6.hisnEnvironment(look)) {
                    HStack(spacing: 10) {
                        Image(systemName: ok ? "checkmark.circle.fill" : "circle").font(.system(size: 20)).foregroundColor(ok ? look.kept : look.line)
                        HisnIcon(t.1, 34)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(ia(t.2, t.3)).font(.system(size: 15, weight: .bold)).foregroundColor(look.ink)
                            Text(ia(t.4, t.5)).font(.system(size: 12.5)).foregroundColor(look.sub)
                        }
                        Spacer()
                        Image(systemName: hisnArabic() ? "chevron.left" : "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundColor(look.sub)
                    }
                    .padding(.vertical, 6)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(14).hisnCard(look, radius: 20).padding(.horizontal, 16)
        .id(refresh)
    }

    private var wisdom: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                HisnKicker(text: ia("Wisdom for today", "حكمة اليوم"))
                Spacer()
                Button(ia("Another", "غيرها")) { skip += 1 }.font(.system(size: 13, weight: .bold)).foregroundColor(look.pri)
                Button(ia("Library", "المكتبة"), action: onLibrary).font(.system(size: 13, weight: .bold)).foregroundColor(look.pri).padding(.leading, 8)
            }
            if let s = HisnSayings.shared.daily(skip: skip) {
                HisnQuote(text: s.text, source: s.source)
            }
        }
        .padding(14).hisnCard(look, radius: 20).padding(.horizontal, 16)
    }

    private var verse: some View {
        let v = HisnVerses.today
        return VStack(spacing: 8) {
            HStack {
                Text(ia("Verse of the day", "آية اليوم")).font(.system(size: 12.5, weight: .bold)).foregroundColor(look.deepInk.opacity(0.8))
                Spacer()
            }
            HisnVerse(text: v.text, ref: hisnArabic() ? hisnVerseRef(v.ref) : "“\(v.pickthall)” (Pickthall) · \(v.ref)", light: true)
        }
        .padding(16).background(RoundedRectangle(cornerRadius: 20).fill(look.deep)).padding(.horizontal, 16)
    }
}

extension HisnDates {
    /// Both calendars, the one the person's country writes its days in first: Hijri in Saudi Arabia, Gregorian elsewhere.
    static var hijriFirst: Bool { hisnArabic() && (Locale.autoupdatingCurrent.region?.identifier == "SA") }
    static func first(_ d: Date) -> String { hijriFirst ? hijri(d, withYear: false) : gregorian(d) }
    static func second(_ d: Date) -> String { hijriFirst ? gregorian(d) : hijri(d, withYear: false) }
    static func both(_ d: Date) -> String {
        let f = DateFormatter(); f.locale = Locale(identifier: hisnArabic() ? "ar@numbers=latn" : "en"); f.dateFormat = "EEEE"
        return "\(f.string(from: d))\(hisnArabic() ? "،" : ",") \(first(d)) · \(second(d))"
    }
    /// The Hijri months a Gregorian month spans, as "Jumādā al-Ūlā – Jumādā al-Ākhira 1448".
    static func hijriMonths(_ a: Date, _ b: Date) -> String {
        let f = DateFormatter()
        var c = Calendar(identifier: .islamicUmmAlQura)
        let loc = Locale(identifier: hisnArabic() ? "ar@numbers=latn;calendar=islamic-umalqura" : "en@calendar=islamic-umalqura")
        c.locale = loc; f.calendar = c; f.locale = loc
        f.setLocalizedDateFormatFromTemplate("MMMM"); let m1 = f.string(from: a)
        f.setLocalizedDateFormatFromTemplate("MMMMy"); let m2 = f.string(from: b)
        return m1 == f.string(from: b).components(separatedBy: " ").first ? m2 : "\(m1) – \(m2)"
    }
    static func weekdayLetter(_ d: Date) -> String {
        if !hisnArabic() { let f = DateFormatter(); f.locale = Locale(identifier: "en"); f.dateFormat = "EEEEE"; return f.string(from: d) }
        return ["ح", "ن", "ث", "ر", "خ", "ج", "س"][Calendar(identifier: .gregorian).component(.weekday, from: d) - 1]
    }
}

// MARK: - Progress and calendar

struct HisnProgressView: View {
    @Environment(\.hisnLook) private var look
    @State private var month = 0

    var body: some View {
        let nights = HisnJourney.nights
        let st = HisnJourney.stations(for: nights)
        let base = st.previous?.nights ?? 0
        let p = nights >= st.next.nights ? 1 : CGFloat(nights - base) / CGFloat(st.next.nights - base)
        HisnPage(title: ia("Your progress", "تقدّمك")) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .lastTextBaseline, spacing: 8) {
                    Text("\(nights)").font(.system(size: 56, weight: .bold, design: .rounded)).foregroundColor(look.ink)
                    Text(ia("nights of light", "ليالٍ من النور")).font(.system(size: 14)).foregroundColor(look.sub)
                }
                HStack(spacing: 10) {
                    HisnIcon(st.next.icon, 40)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(ia("Next station", "المحطة التالية")).font(.system(size: 11)).foregroundColor(look.sub)
                        Text(st.next.name).font(.system(size: 15, weight: .semibold)).foregroundColor(look.ink)
                        Text(ia("\(max(0, st.next.nights - nights)) nights to go", "بقي \(max(0, st.next.nights - nights)) ليالٍ")).font(.system(size: 12)).foregroundColor(look.acc)
                    }
                    Spacer()
                }
                .padding(10).background(RoundedRectangle(cornerRadius: 16).fill(look.soft))
                GeometryReader { g in
                    Capsule().fill(look.line).overlay(alignment: .leading) { Capsule().fill(look.acc).frame(width: g.size.width * p) }
                }
                .frame(height: 8).environment(\.layoutDirection, hisnArabic() ? .rightToLeft : .leftToRight)
            }
            .padding(16).hisnCard(look, radius: 22)

            ForEach(HisnJourney.stations, id: \.nights) { s in
                let reached = nights >= s.nights
                HStack(spacing: 12) {
                    HisnIcon(s.icon, 38).opacity(reached ? 1 : 0.45)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(s.name).font(.system(size: 15, weight: .bold)).foregroundColor(look.ink)
                        Text(ia("\(s.nights) nights", "\(s.nights) ليلة")).font(.system(size: 12.5)).foregroundColor(look.sub)
                    }
                    Spacer()
                    Text(reached ? ia("Reached", "بلغتها") : ia("\(s.nights - nights) to go", "بقي \(s.nights - nights)"))
                        .font(.system(size: 12, weight: .bold)).foregroundColor(reached ? look.kept : look.sub)
                }
                .padding(12).hisnCard(look, stroke: !reached && s.nights == st.next.nights ? look.acc : nil)
            }

            let week = (0..<7).map { HisnJourney.today - $0 }
            let prayedWeek = week.reduce(0) { $0 + HisnPrayers.prayed($1).nonzeroBitCount }
            let backs = (0..<30).filter { HisnJourney.turnedBack(HisnJourney.today - $0) }.count
            HStack(spacing: 10) {
                stat("\(Int((Double(prayedWeek) / 35 * 100).rounded()))%", ia("Prayers marked, last 7 days", "صلوات علّمتها، آخر 7 أيام"))
                stat("\(backs)", ia("Times you turned back this month", "مرات رجعتَ فيها هذا الشهر"))
            }
            HisnKicker(text: ia("Your calendar", "تقويمك"))
            calendar
            if let s = HisnSayings.shared.moment(.tempted).first(where: { $0.text.count < 260 }) {
                HisnQuote(text: s.text, source: s.source).padding(.top, 6)
            }
        }
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value).font(.system(size: 26, weight: .bold, design: .rounded)).foregroundColor(look.ink)
            Text(label).font(.system(size: 12)).foregroundColor(look.sub).fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading).padding(14).hisnCard(look)
    }

    private var calendar: some View {
        let cal = Calendar(identifier: .gregorian)
        let monthStart = cal.date(byAdding: .month, value: month, to: cal.date(from: cal.dateComponents([.year, .month], from: Date()))!)!
        let days = cal.range(of: .day, in: .month, for: monthStart)!.count
        let lead = (cal.component(.weekday, from: monthStart) - 1)
        let f = DateFormatter(); f.locale = Locale(identifier: hisnArabic() ? "ar@numbers=latn" : "en"); f.dateFormat = "MMMM yyyy"
        return VStack(spacing: 10) {
            HStack {
                Button { month -= 1 } label: { Image(systemName: hisnArabic() ? "chevron.right" : "chevron.left") }.disabled(month <= -11)
                Spacer()
                VStack(spacing: 2) {
                    Text(f.string(from: monthStart)).font(.system(size: 15, weight: .bold)).foregroundColor(look.ink)
                    Text(HisnDates.hijriMonths(monthStart, cal.date(byAdding: .day, value: days - 1, to: monthStart)!)).font(.system(size: 11.5)).foregroundColor(look.sub)
                }
                Spacer()
                Button { month += 1 } label: { Image(systemName: hisnArabic() ? "chevron.left" : "chevron.right") }.disabled(month >= 0)
            }
            .foregroundColor(look.pri)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 7), spacing: 6) {
                ForEach(0..<7, id: \.self) { i in
                    Text(hisnArabic() ? ["ح", "ن", "ث", "ر", "خ", "ج", "س"][i] : ["S", "M", "T", "W", "T", "F", "S"][i])
                        .font(.system(size: 11, weight: .semibold)).foregroundColor(look.sub)
                }
                ForEach(0..<(lead + days), id: \.self) { i in
                    if i < lead { Color.clear.frame(height: 34) } else {
                        let date = cal.date(byAdding: .day, value: i - lead, to: monthStart)!
                        let day = HisnJourney.dayNumber(date)
                        let kept = day < HisnJourney.today && day >= HisnJourney.startDay && !HisnJourney.slipped(day)
                        let slip = HisnJourney.slipped(day)
                        NavigationLink(destination: HisnDayView(day: day).hisnEnvironment(look)) {
                            Text("\(i - lead + 1)").font(.system(size: 12.5, weight: .semibold))
                                .foregroundColor(kept ? Color(hex: 0x3A2200) : (day > HisnJourney.today ? look.sub.opacity(0.4) : look.ink))
                                .frame(width: 34, height: 34)
                                .background(Circle().fill(kept ? (HisnJourney.turnedBack(day) ? Color(hex: 0xF0D9A0) : Color(hex: 0xD9A441)) : .clear))
                                .overlay(Circle().stroke(slip ? Color(hex: 0xC24848) : (day == HisnJourney.today ? look.pri : .clear), lineWidth: 2))
                                .overlay(alignment: .bottom) { if !HisnJourney.note(day).isEmpty { Circle().fill(look.acc).frame(width: 4, height: 4).offset(y: 3) } }
                        }
                        .buttonStyle(.plain)
                        .disabled(day > HisnJourney.today)
                    }
                }
            }
            HStack(spacing: 12) {
                legend(Color(hex: 0xD9A441), ia("Kept", "محفوظة"))
                legend(Color(hex: 0xF0D9A0), ia("Turned back", "رجوع"))
                legend(Color(hex: 0xC24848), ia("Slip", "زلّة"), ring: true)
                legend(look.acc, ia("Note", "ملاحظة"))
            }
            .font(.system(size: 11.5)).foregroundColor(look.sub)
        }
        .padding(14).hisnCard(look, radius: 20)
    }

    private func legend(_ c: Color, _ t: String, ring: Bool = false) -> some View {
        HStack(spacing: 4) {
            Circle().fill(ring ? .clear : c).overlay(Circle().stroke(c, lineWidth: ring ? 2 : 0)).frame(width: 10, height: 10)
            Text(t)
        }
    }
}

/// One day of the calendar: what was kept, the prayers, and the note for that night.
struct HisnDayView: View {
    @Environment(\.hisnLook) private var look
    let day: Int
    @State private var note = ""
    var body: some View {
        let date = HisnJourney.date(of: day)
        HisnPage(title: HisnDates.first(date)) {
            Text(HisnDates.both(date)).font(.system(size: 13)).foregroundColor(look.sub)
            Text(HisnJourney.slipped(day) ? ia("A slip. The door stays open.", "زلّة، والباب ما زال مفتوحًا.")
                 : (day < HisnJourney.startDay ? ia("Before your journey began", "قبل أن تبدأ رحلتك") : ia("A night of light", "ليلة من النور")))
                .font(.system(size: 20, weight: .bold)).foregroundColor(look.ink)
            Text(ia("Prayers marked: \(HisnPrayers.prayed(day).nonzeroBitCount) of 5", "صلوات علّمتها: \(HisnPrayers.prayed(day).nonzeroBitCount) من 5"))
                .font(.system(size: 14)).foregroundColor(look.sub)
            HisnKicker(text: ia("Your note for this night", "ملاحظتك لهذه الليلة"))
            TextField(ia("What you want to remember", "ما تريد أن تتذكّره"), text: $note, axis: .vertical)
                .lineLimit(3...8).padding(14).hisnCard(look)
            HisnPrimaryButton(label: ia("Save it for this night", "احفظها لليلة")) { HisnJourney.setNote(day, note) }
            HisnQuote(text: "السنة شجرة، والشهور فروعها، والأيام أغصانها، والساعات أوراقها، والأنفاس ثمرها.",
                      source: ia("Ibn al-Qayyim, al-Fawāʾid, p. 240", "ابن القيم، الفوائد، ص 240"))
        }
        .onAppear { note = HisnJourney.note(day) }
    }
}

// MARK: - Library

struct HisnLibraryView: View {
    @Environment(\.hisnLook) private var look
    @State private var tab = 0
    @State private var topic: String?
    @State private var query = ""
    var body: some View {
        let lib = HisnSayings.shared
        HisnPage(title: ia("From the scholars", "من كلام العلماء")) {
            Picker("", selection: $tab) {
                Text(ia("Chosen", "مختارات")).tag(0)
                Text(ia("Library", "المكتبة")).tag(1)
                Text(ia("Saved", "المحفوظة")).tag(2)
            }
            .pickerStyle(.segmented)
            HisnSub(text: ia("\(lib.all.count) sayings on purifying the soul, from Ibn al-Qayyim, Ibn al-Jawzī, Ibn Rajab, Ibn Qudāma and others. Each with its book and page, as written.",
                             "\(lib.all.count) قولًا في تزكية النفس من كلام ابن القيم وابن الجوزي وابن رجب وابن قدامة وغيرهم، مع الكتاب والصفحة، بنصّه كما هو."))
            switch tab {
            case 0:
                ForEach((0..<12).compactMap { lib.daily(skip: $0) }, id: \.index) { HisnSayingCard(saying: $0) }
            case 1:
                TextField(ia("Search the sayings", "ابحث في الأقوال"), text: $query).padding(12).hisnCard(look)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        chip(nil, ia("All", "الكل"))
                        ForEach(lib.topics) { chip($0.key, $0.name) }
                    }
                }
                ForEach(lib.filter(topic: topic, query: query).prefix(60), id: \.index) { HisnSayingCard(saying: $0) }
            default:
                let saved = lib.all.filter { lib.saved.contains($0.index) }
                if saved.isEmpty { HisnSub(text: ia("Tap the heart on a saying to keep it here.", "اضغط القلب على أي قول لتحفظه هنا."), center: true).padding(.top, 30) }
                ForEach(saved, id: \.index) { HisnSayingCard(saying: $0) }
            }
        }
    }

    private func chip(_ key: String?, _ label: String) -> some View {
        let on = topic == key
        return Button { topic = key } label: {
            Text(label).font(.system(size: 13, weight: on ? .bold : .regular)).foregroundColor(on ? look.priInk : look.ink)
                .padding(.horizontal, 14).padding(.vertical, 8).background(Capsule().fill(on ? look.pri : look.card))
                .overlay(Capsule().stroke(look.line, lineWidth: on ? 0 : 1))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Tools

struct HisnToolsView: View {
    @Environment(\.hisnLook) private var look
    @ObservedObject var protection: ProtectionController
    let onBreathe: () -> Void
    let shield, apps, practice, more, setup: AnyView
    @AppStorage("hisn.look") private var lookKey = "fajr"
    @AppStorage("hisn.onboard.instead") private var instead = "wudu,quran,rakah"

    var body: some View {
        HisnPage(title: ia("Your tools", "أدواتك")) {
            HisnKicker(text: ia("For right now", "للحظة الحاضرة"))
            group {
                Button(action: onBreathe) { row("wind", ia("Breathe with dhikr", "تنفّس مع الذكر"), ia("Three rounds of istighfār", "ثلاث جولات من الاستغفار")) }.buttonStyle(.plain)
                NavigationLink(destination: HisnProgressView().hisnEnvironment(look)) { row("calendar", ia("My notes", "ملاحظاتي"), ia("On your calendar, one per night", "على تقويمك، ملاحظة لكل ليلة")) }.buttonStyle(.plain)
                NavigationLink(destination: insteadPage) { row("heart", ia("Instead", "البدائل"), ia("Wuḍūʾ, a page of Qurʾān, a walk", "وضوء، صفحة من القرآن، مشي")) }.buttonStyle(.plain)
                NavigationLink(destination: HisnAdhkarView().hisnEnvironment(look)) { row("sparkles", ia("Adhkār", "الأذكار"), ia("Morning and evening, from Ḥiṣn al-Muslim", "الصباح والمساء من حصن المسلم")) }.buttonStyle(.plain)
            }
            HisnKicker(text: ia("Your protection", "حمايتك"))
            group {
                NavigationLink(destination: shield) { row("shield.fill", ia("Shield", "الدرع"), ia("What is on, and what still needs you", "ما يعمل، وما زال يحتاجك")) }.buttonStyle(.plain)
                NavigationLink(destination: setup.navigationTitle(ia("Protection", "الحماية"))) { row("globe", ia("Filters", "المرشّحات"), ia("Safari, the website filter and Screen Time", "سفاري ومرشّح المواقع ومدة استخدام الجهاز")) }.buttonStyle(.plain)
                NavigationLink(destination: apps) { row("apps.iphone", ia("Apps", "التطبيقات"), ia("Close apps, the pause and quiet hours", "إغلاق التطبيقات والوقفة والساعات الهادئة")) }.buttonStyle(.plain)
                NavigationLink(destination: practice) { row("wind", ia("The pause", "الوقفة"), ia("Your pauses and their days", "وقفاتك وأيامها")) }.buttonStyle(.plain)
                NavigationLink(destination: more) { row("lock.fill", ia("Lock and settings", "القفل والإعدادات"), ia("Rules that cannot be loosened, language, more", "قواعد لا تُخفَّف، واللغة، وغيرها")) }.buttonStyle(.plain)
            }
            HisnKicker(text: ia("You", "أنت"))
            group {
                NavigationLink(destination: lookPage) { row("paintpalette.fill", ia("Look", "المظهر"), HisnLook.named(lookKey).name) }.buttonStyle(.plain)
                NavigationLink(destination: HisnCityView().hisnEnvironment(look)) { row("mappin.and.ellipse", ia("City for prayer times", "المدينة لمواقيت الصلاة"), HisnPrayers.place.name) }.buttonStyle(.plain)
            }
        }
    }

    private func group<C: View>(@ViewBuilder _ c: () -> C) -> some View {
        VStack(spacing: 0) { c() }.hisnCard(look, radius: 18)
    }

    private func row(_ symbol: String, _ title: String, _ sub: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol).font(.system(size: 15, weight: .semibold)).foregroundColor(look.pri)
                .frame(width: 36, height: 36).background(Circle().fill(look.soft))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 15, weight: .bold)).foregroundColor(look.ink)
                Text(sub).font(.system(size: 12.5)).foregroundColor(look.sub)
            }
            Spacer()
            Image(systemName: hisnArabic() ? "chevron.left" : "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundColor(look.sub)
        }
        .padding(.horizontal, 14).padding(.vertical, 11).contentShape(Rectangle())
    }

    private var insteadPage: some View {
        let keys = Set(instead.split(separator: ",").map(String.init))
        return HisnPage(title: ia("Instead", "البدائل")) {
            HisnSub(text: ia("What you chose to do instead. Tap to change.", "ما اخترت أن تفعله بدلًا من ذلك. اضغط لتغيّره."))
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                ForEach(HisnContent.instead) { o in
                    let on = keys.contains(o.key)
                    Button {
                        var k = instead.split(separator: ",").map(String.init)
                        if let i = k.firstIndex(of: o.key) { k.remove(at: i) } else { k.append(o.key) }
                        instead = k.joined(separator: ",")
                    } label: {
                        HStack(spacing: 8) { HisnIcon(o.icon, 34); Text(o.label).font(.system(size: 13.5, weight: on ? .semibold : .regular)).foregroundColor(look.ink); Spacer(minLength: 0) }
                            .padding(10).frame(maxWidth: .infinity, minHeight: 58).hisnCard(look, fill: on ? look.chip : nil, stroke: on ? look.pri : nil)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .hisnEnvironment(look)
    }

    private var lookPage: some View {
        HisnPage(title: ia("Look", "المظهر")) {
            ForEach(HisnLook.all, id: \.key) { option in
                Button { lookKey = option.key } label: {
                    HStack(spacing: 12) {
                        Image(option.background).resizable().scaledToFill().frame(width: 64, height: 64).clipShape(RoundedRectangle(cornerRadius: 12))
                        Text(option.name).font(.system(size: 17, weight: .semibold)).foregroundColor(look.ink)
                        Spacer()
                        Image(systemName: option.key == lookKey ? "checkmark.circle.fill" : "circle").font(.system(size: 22)).foregroundColor(option.key == lookKey ? look.pri : look.line)
                    }
                    .padding(12).hisnCard(look, radius: 18, stroke: option.key == lookKey ? look.pri : nil)
                }
                .buttonStyle(.plain)
            }
        }
        .hisnEnvironment(look)
    }
}

struct HisnCityView: View {
    @Environment(\.hisnLook) private var look
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    var body: some View {
        HisnPage(title: ia("City for prayer times", "المدينة لمواقيت الصلاة")) {
            TextField(ia("Search", "ابحث"), text: $query).padding(12).hisnCard(look)
            VStack(spacing: 0) {
                ForEach(HisnPlace.all.filter { query.isEmpty || $0.en.lowercased().contains(query.lowercased()) || $0.ar.contains(query) }) { p in
                    Button { HisnPrayers.setPlace(p.key); dismiss() } label: {
                        HStack {
                            Text(p.name).font(.system(size: 15)).foregroundColor(look.ink)
                            Spacer()
                            if p.key == HisnPrayers.place.key { Image(systemName: "checkmark").foregroundColor(look.pri) }
                        }
                        .padding(.horizontal, 14).padding(.vertical, 12).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    Divider().overlay(look.line)
                }
            }
            .hisnCard(look)
        }
    }
}

// MARK: - Adhkār with the counter

struct HisnAdhkarView: View {
    @Environment(\.hisnLook) private var look
    @State private var evening = Calendar.current.component(.hour, from: Date()) >= 15
    @State private var index = 0
    @State private var counts: [Int: Int] = [:]

    private var list: [HisnDhikr] { evening ? HisnAdhkar.evening : HisnAdhkar.morning }
    private var allDone: Bool { list.allSatisfy { (counts[$0.number] ?? 0) >= $0.count } }

    var body: some View {
        HisnPage(title: ia("Adhkār", "الأذكار")) {
            Picker("", selection: $evening) {
                Text(ia("Morning", "الصباح")).tag(false)
                Text(ia("Evening", "المساء")).tag(true)
            }
            .pickerStyle(.segmented)
            .onChange(of: evening) { _ in index = 0; counts = [:] }
            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(Array(list.enumerated()), id: \.element.number) { i, d in
                            let c = counts[d.number] ?? 0
                            Button { index = i } label: {
                                HStack(spacing: 6) {
                                    Text(d.name).font(.system(size: 13, weight: .bold))
                                    Text("\(min(c, d.count))/\(d.count)\(c >= d.count ? " ✓" : "")").font(.system(size: 12)).foregroundColor(c >= d.count ? look.kept : look.sub)
                                }
                                .foregroundColor(look.ink).padding(.horizontal, 12).padding(.vertical, 9)
                                .hisnCard(look, radius: 14, stroke: i == index ? look.pri : nil)
                            }
                            .buttonStyle(.plain).id(i)
                        }
                    }
                }
                .onChange(of: index) { i in withAnimation { proxy.scrollTo(i, anchor: .center) } }
            }
            let d = list[min(index, list.count - 1)]
            Text("\(index + 1) / \(list.count)").font(.system(size: 12.5, weight: .bold)).foregroundColor(look.sub).frame(maxWidth: .infinity)
            Text(rtl(d.text)).font(.custom("GeezaPro", size: d.text.count > 320 ? 17 : d.text.count > 120 ? 19 : 23)).lineSpacing(10)
                .foregroundColor(look.ink).multilineTextAlignment(.center).environment(\.layoutDirection, .rightToLeft)
                .frame(maxWidth: .infinity).fixedSize(horizontal: false, vertical: true)
            if !d.note.isEmpty {
                Text(rtl(d.note)).font(.system(size: 13)).foregroundColor(look.sub).multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity).padding(.top, 4).overlay(alignment: .top) { Rectangle().fill(look.line).frame(height: 1) }
            }
            Text(ia("Ḥiṣn al-Muslim, no. \(d.number)", "حصن المسلم، رقم \(d.number)")).font(.system(size: 12.5, weight: .semibold)).foregroundColor(look.sub).frame(maxWidth: .infinity)
            if allDone {
                Text(ia("All done. Alḥamdulillāh.", "تمّت الأذكار. الحمد لله.")).font(.system(size: 20, weight: .bold)).foregroundColor(look.ink).frame(maxWidth: .infinity).padding(.top, 10)
            } else {
                let c = counts[d.number] ?? 0
                Button { count(d) } label: {
                    VStack(spacing: 2) {
                        HisnIcon("misbaha", 70)
                        Text("\(c)").font(.system(size: 30, weight: .bold, design: .rounded)).foregroundColor(look.ink)
                        Text(ia("of \(d.count)", "من \(d.count)")).font(.system(size: 12)).foregroundColor(look.sub)
                    }
                    .frame(width: 170, height: 170).background(Circle().fill(look.card)).overlay(Circle().stroke(look.line, lineWidth: 1.5))
                }
                .buttonStyle(.plain).frame(maxWidth: .infinity).padding(.top, 8)
                .accessibilityLabel(ia("Count, \(c) of \(d.count)", "سبّح، \(c) من \(d.count)"))
                Text(ia("Tap the beads to count", "اضغط على المسبحة لتعدّ")).font(.system(size: 13)).foregroundColor(look.sub).frame(maxWidth: .infinity)
            }
        }
    }

    private func count(_ d: HisnDhikr) {
        let next = min(d.count, (counts[d.number] ?? 0) + 1)
        counts[d.number] = next
        UIImpactFeedbackGenerator(style: next >= d.count ? .medium : .light).impactOccurred()
        if next >= d.count, let following = list.firstIndex(where: { (counts[$0.number] ?? 0) < $0.count }) { index = following }
        if allDone { HisnJourney.markDone("adhkar"); UINotificationFeedbackGenerator().notificationOccurred(.success) }
    }
}

// MARK: - Today's reading: the verse, Pickthall's English, and a scholar's word (never our own explanation)

struct HisnReadingView: View {
    @Environment(\.hisnLook) private var look
    @Environment(\.dismiss) private var dismiss
    private struct Reading { let text, ref, pickthall: String }
    private let readings = [
        Reading(text: "قُل لِّلْمُؤْمِنِينَ يَغُضُّوا۟ مِنْ أَبْصَٰرِهِمْ وَيَحْفَظُوا۟ فُرُوجَهُمْ ۚ ذَٰلِكَ أَزْكَىٰ لَهُمْ", ref: "24:30",
                pickthall: "Tell the believing men to lower their gaze and be modest. That is purer for them."),
        Reading(text: "وَمَن يَتَّقِ ٱللَّهَ يَجْعَل لَّهُۥ مَخْرَجًا", ref: "65:2", pickthall: "And whosoever keepeth his duty to Allah, Allah will appoint a way out for him."),
        Reading(text: "أَلَا بِذِكْرِ ٱللَّهِ تَطْمَئِنُّ ٱلْقُلُوبُ", ref: "13:28", pickthall: "Verily in the remembrance of Allah do hearts find rest!"),
        Reading(text: "إِنَّ ٱللَّهَ يُحِبُّ ٱلتَّوَّٰبِينَ وَيُحِبُّ ٱلْمُتَطَهِّرِينَ", ref: "2:222",
                pickthall: "Truly Allah loveth those who turn unto Him, and loveth those who have a care for cleanness.")
    ]
    var body: some View {
        let r = readings[((HisnJourney.today % readings.count) + readings.count) % readings.count]
        HisnPage(title: ia("Today's reading", "ورد اليوم")) {
            HisnVerse(text: r.text, ref: hisnArabic() ? hisnVerseRef(r.ref) : "“\(r.pickthall)” (Pickthall) · \(r.ref)", light: true, size: 25)
                .padding(20).background(RoundedRectangle(cornerRadius: 20).fill(look.deep))
            HisnKicker(text: ia("From the scholars", "من كلام العلماء"))
            if let s = HisnSayings.shared.daily(skip: 3) { HisnSayingCard(saying: s) }
            HisnPrimaryButton(label: ia("Mark as read", "تمّت القراءة")) { HisnJourney.markDone("read"); dismiss() }
        }
    }
}

// MARK: - The evening reckoning

struct HisnReckoningView: View {
    @Environment(\.hisnLook) private var look
    @Environment(\.dismiss) private var dismiss
    @State private var prayed = HisnPrayers.prayed()
    @State private var gaze = -1
    @State private var wird = -1
    @State private var thanks = ""
    @State private var forgiven = false
    @State private var intention = ""

    var body: some View {
        HisnPage(title: ia("A reckoning before sleep", "محاسبة قبل النوم")) {
            HisnQuote(text: "والمقصود أن صلاح القلب بمحاسبة النفس، وفساده بإهمالها والاسترسال معها.",
                      source: ia("Ibn al-Qayyim, Ighāthat al-Lahfān, 1/ 143", "ابن القيم، إغاثة اللهفان، 1/ 143"))
            HisnSub(text: ia("One minute. What was for you today, and what was against you.", "دقيقة واحدة: ما لك اليوم وما عليك."))
            card(ia("Which prayers did you pray on time?", "أيّ الصلوات صلّيتها في وقتها؟")) {
                HStack(spacing: 6) {
                    ForEach(0..<5, id: \.self) { i in
                        let ok = prayed & (1 << i) != 0
                        Button { HisnPrayers.toggle(i); prayed = HisnPrayers.prayed() } label: {
                            Text(ia(HisnPrayers.names[i].0, HisnPrayers.names[i].1)).font(.system(size: 12.5, weight: .semibold))
                                .foregroundColor(ok ? .white : look.ink).frame(maxWidth: .infinity).padding(.vertical, 9)
                                .background(Capsule().fill(ok ? look.kept : look.soft))
                        }
                        .buttonStyle(.plain)
                    }
                }
                let n = prayed.nonzeroBitCount
                Text(ia("\(n) of 5", "\(n) من 5") + (n < 5 ? ia(" · make up what you missed before you sleep", " · اقضِ ما فاتك قبل أن تنام") : ia(" · alḥamdulillāh", " · الحمد لله")))
                    .font(.system(size: 12.5)).foregroundColor(look.sub)
            }
            card(ia("Your gaze today", "بصرك اليوم")) {
                three($gaze, [ia("I guarded it", "حفظتُه"), ia("I struggled", "جاهدتُ"), ia("I slipped", "زللتُ")])
                if gaze == 2 { Text(ia("The door is open. Say istighfār below, and close tomorrow the door it came from.", "الباب مفتوح. استغفر في الأسفل، وأغلق غدًا الباب الذي جاءت منه.")).font(.system(size: 12.5)).foregroundColor(look.sub) }
            }
            card(ia("Your Qurʾān and dhikr", "وردك من القرآن والذكر")) { three($wird, [ia("I read", "قرأتُ"), ia("A little", "قليلًا"), ia("Not today", "لم أقرأ")]) }
            card(ia("One blessing you thank Allah for today", "نعمة تشكر الله عليها اليوم")) {
                TextField(ia("For example: I prayed Fajr with my father", "مثلًا: صلّيت الفجر مع أبي"), text: $thanks).padding(10).background(RoundedRectangle(cornerRadius: 12).fill(look.soft))
            }
            card(ia("Ask forgiveness for what went wrong", "استغفر مما قصّرت فيه")) {
                Text(ia("No need to write it. Allah knows it.", "لا حاجة أن تكتبه، فالله يعلمه.")).font(.system(size: 12.5)).foregroundColor(look.sub)
                Button { forgiven = true; UIImpactFeedbackGenerator(style: .soft).impactOccurred() } label: {
                    Text((forgiven ? "✓ " : "") + ia("Say: astaghfirullāh wa atūbu ilayh", "قل: أستغفر الله وأتوب إليه")).font(.system(size: 15, weight: .semibold))
                        .foregroundColor(forgiven ? .white : look.ink).frame(maxWidth: .infinity).padding(.vertical, 11)
                        .background(Capsule().fill(forgiven ? look.kept : look.soft))
                }
                .buttonStyle(.plain)
            }
            card(ia("One intention for tomorrow", "نيّة واحدة للغد")) {
                Text(ia("It is saved on tonight in your calendar.", "تُحفظ على يومك في التقويم.")).font(.system(size: 12.5)).foregroundColor(look.sub)
                TextField(ia("For example: phone outside the room before I sleep", "مثلًا: أضع الهاتف خارج الغرفة قبل النوم"), text: $intention, axis: .vertical)
                    .lineLimit(2...4).padding(10).background(RoundedRectangle(cornerRadius: 12).fill(look.soft))
            }
            HisnPrimaryButton(label: ia("Save my reckoning", "احفظ محاسبتي")) {
                if !intention.trimmingCharacters(in: .whitespaces).isEmpty { HisnJourney.setNote(HisnJourney.today, intention) }
                if gaze == 2 { HisnJourney.setSlipped(HisnJourney.today, true) }
                HisnJourney.markDone("reflect")
                dismiss()
            }
        }
        .onAppear { intention = HisnJourney.note(HisnJourney.today) }
    }

    private func card<C: View>(_ title: String, @ViewBuilder _ c: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.system(size: 15, weight: .bold)).foregroundColor(look.ink)
            c()
        }
        .padding(14).frame(maxWidth: .infinity, alignment: .leading).hisnCard(look)
    }

    private func three(_ value: Binding<Int>, _ labels: [String]) -> some View {
        HStack(spacing: 6) {
            ForEach(0..<3, id: \.self) { i in
                Button { value.wrappedValue = i } label: {
                    Text(labels[i]).font(.system(size: 13.5, weight: .semibold)).foregroundColor(value.wrappedValue == i ? look.priInk : look.ink)
                        .frame(maxWidth: .infinity).padding(.vertical, 10).background(Capsule().fill(value.wrappedValue == i ? look.pri : look.soft))
                }
                .buttonStyle(.plain)
            }
        }
    }
}

// MARK: - I slipped: come back

struct HisnComeBackView: View {
    @Environment(\.hisnLook) private var look
    let onClose: () -> Void
    var body: some View {
        ZStack {
            LinearGradient(colors: [look.helpTop, look.helpBottom], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            ScrollView {
                VStack(spacing: 14) {
                    HStack {
                        Text(ia("Come back", "عُد")).font(.system(size: 17, weight: .bold)).foregroundColor(look.helpInk)
                        Spacer()
                        Button(action: onClose) { Image(systemName: "xmark").font(.system(size: 17, weight: .semibold)).foregroundColor(look.helpInk).frame(width: 44, height: 44) }
                            .accessibilityLabel(ia("Close", "إغلاق"))
                    }
                    Text(ia("The door is still open", "الباب ما زال مفتوحًا")).font(.system(size: 28, weight: .semibold)).foregroundColor(look.helpInk).multilineTextAlignment(.center)
                    HisnVerse(text: "لَا تَقْنَطُوا۟ مِن رَّحْمَةِ ٱللَّهِ ۚ إِنَّ ٱللَّهَ يَغْفِرُ ٱلذُّنُوبَ جَمِيعًا",
                              ref: hisnArabic() ? hisnVerseRef("39:53") : "“Despair not of the mercy of Allah, Who forgiveth all sins.” (Pickthall) · 39:53", size: 22, ink: look.helpInk)
                    VStack(spacing: 6) {
                        Text(ia("The nights you kept", "لياليك التي حفظتها")).font(.system(size: 12.5, weight: .bold)).foregroundColor(look.helpInk.opacity(0.7))
                        Text("\(HisnJourney.nights)").font(.system(size: 46, weight: .bold, design: .rounded)).foregroundColor(look.helpInk)
                        Text(ia("What passed of your nights stays yours; a new night begins now.", "ما مضى من لياليك باقٍ لك لا يضيع، وتبدأ ليلة جديدة من الآن."))
                            .font(.system(size: 13)).foregroundColor(look.helpInk).multilineTextAlignment(.center)
                    }
                    .padding(16).frame(maxWidth: .infinity).background(panel)
                    VStack(alignment: .leading, spacing: 6) {
                        Text(ia("Three steps back", "ثلاث خطوات للعودة")).font(.system(size: 16, weight: .bold)).foregroundColor(look.helpInk)
                        Text(ia("1 · Regret it in your heart", "1 · الندم بالقلب"))
                        Text(ia("2 · Stop now", "2 · الإقلاع الآن"))
                        Text(ia("3 · Resolve not to return, then wuḍūʾ and two rakʿahs (Abū Dāwūd 1521)", "3 · العزم على ألّا تعود، ثم وضوء وركعتان (أبو داود 1521)"))
                    }
                    .font(.system(size: 14.5)).foregroundColor(look.helpInk).frame(maxWidth: .infinity, alignment: .leading).padding(16).background(panel)
                    HisnQuote(text: "والصحيح أن من التائبين من لا يعود إلى درجته، ومنهم من يعود إلى أعلى منها فيصير خيرًا مما كان قبل الذنب.",
                              source: ia("Ibn Taymiyya, in Tazkiyat al-Nufūs, p. 119", "ابن تيمية، في تزكية النفوس، ص 119"), ink: look.helpInk)
                    HisnPrimaryButton(label: ia("I'm coming back", "سأعود"), color: look.help) {
                        HisnJourney.setSlipped(HisnJourney.today, true)
                        onClose()
                    }
                }
                .padding(.horizontal, 18).padding(.bottom, 24).frame(maxWidth: 560).frame(maxWidth: .infinity)
            }
        }
        .preferredColorScheme(look.dark ? .dark : .light)
    }
    private var panel: some View { RoundedRectangle(cornerRadius: 16).fill(Color.white.opacity(look.dark ? 0.08 : 0.7)) }
}
