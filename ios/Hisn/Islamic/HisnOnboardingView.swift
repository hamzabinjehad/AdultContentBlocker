import SwiftUI
import UIKit

/// The Islamic edition's first run, page for page with the approved prototype
/// (`design/hisn-unchaind-2026-11-23`): who you are, why it is hard, what helps, your plan.
/// Every answer is kept and changes what the person sees afterwards.
struct HisnOnboardingView: View {
    enum Step: Int, CaseIterable {
        case splash, intro, how, welcome
        case name, look, speech, age, guards, hardest, prayers
        case hours, slide0, slide1, slide2, diag, soft, harm
        case wayOut, goals, instead, reason, suggest
        case told, perms, tryPause, begin, pledge, plan, notify, free

        /// -1 for the pages outside the four chapters.
        var chapter: Int {
            switch self {
            case .splash, .intro, .how, .welcome, .begin, .pledge, .free: return -1
            case .name, .look, .speech, .age, .guards, .hardest, .prayers: return 0
            case .hours, .slide0, .slide1, .slide2, .diag, .soft, .harm: return 1
            case .wayOut, .goals, .instead, .reason, .suggest: return 2
            case .told, .perms, .tryPause, .plan, .notify: return 3
            }
        }
        /// Pages on the deep colour of the look.
        var deep: Bool { [.intro, .welcome, .slide0, .slide1, .slide2, .harm, .begin].contains(self) }
        /// Every page stands on the look's painted landscape, as in the prototype.
        var land: Bool { true }
    }

    let onDone: () -> Void
    @ObservedObject var protection: ProtectionController
    @AppStorage("hisn.look") private var lookKey = "fajr"
    @AppStorage("hisn.onboard.name") private var name = ""
    @AppStorage("hisn.onboard.speech") private var speech = ""
    @AppStorage("hisn.onboard.age") private var age = ""
    @AppStorage("hisn.onboard.guard") private var guards = "adult"
    @AppStorage("hisn.onboard.hardest") private var hardest = ""
    @AppStorage("hisn.onboard.prayers") private var prayers = ""
    @AppStorage("hisn.onboard.hours") private var hours = ""
    @State private var tried = false
    @AppStorage("hisn.onboard.goals") private var goals = "pray,gaze,near"
    @AppStorage("hisn.onboard.instead") private var instead = "wudu,quran,rakah"
    @AppStorage("hisn.onboard.reason") private var reason = ""
    @AppStorage("hisn.onboard.step") private var savedStep = 0
    @State private var step: Step = .splash
    @State private var harm = 0
    @State private var howFrame = 0
    @State private var pledge = 0
    @FocusState private var typing: Bool

    private var look: HisnLook { HisnLook.named(lookKey) }

    var body: some View {
        let deep = step.deep
        ZStack {
            (deep ? look.deep : look.sky).ignoresSafeArea()
            if step.land { landscape(deep: deep) }
            VStack(spacing: 0) {
                header(deep: deep)
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) { page }
                        .padding(.horizontal, 22).padding(.top, 10).padding(.bottom, 16)
                        .frame(maxWidth: 560).frame(maxWidth: .infinity)
                }
                .scrollDismissesKeyboard(.interactively)
                actions(deep: deep).padding(.horizontal, 22).padding(.bottom, 12).frame(maxWidth: 560)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { if step == .splash || step == .intro { next() } }
        .environment(\.hisnLook, look)
        .preferredColorScheme(look.dark || deep ? .dark : .light)
        .animation(.easeInOut(duration: 0.22), value: step)
        .animation(.easeInOut(duration: 0.22), value: harm)
        .onAppear { step = Step(rawValue: savedStep) ?? .splash }
        .onChange(of: step) { savedStep = $0.rawValue }
    }

    /// The look's painting over the lower half, fading into the page colour above it.
    private func landscape(deep: Bool) -> some View {
        GeometryReader { proxy in
            VStack(spacing: 0) {
                Spacer(minLength: 0)
                Image(look.background).resizable().scaledToFill()
                    .frame(width: proxy.size.width, height: proxy.size.height * 0.5, alignment: .bottom)
                    .clipped()
                    .opacity(deep ? 0.55 : 1)
                    .overlay(alignment: .top) {
                        LinearGradient(colors: [deep ? look.deep : look.sky, .clear], startPoint: .top, endPoint: .bottom).frame(height: 130)
                    }
            }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    // MARK: Header: back, the four chapters, the chapter's name

    @ViewBuilder private func header(deep: Bool) -> some View {
        let ink = deep ? look.deepInk : look.ink
        HStack(spacing: 10) {
            if step != .splash && step != .intro && step != .how && step != .welcome {
                Button(action: back) {
                    Image(systemName: "arrow.backward").font(.system(size: 18, weight: .semibold)).foregroundColor(ink)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel(ia("Back", "رجوع"))
            }
            if step.chapter >= 0 {
                HStack(spacing: 5) {
                    ForEach(0..<4, id: \.self) { i in
                        Capsule().fill(i <= step.chapter ? (deep ? look.deepInk : look.pri) : ink.opacity(0.15)).frame(height: 4)
                    }
                }
                Text(ia(HisnContent.chapters[step.chapter].0, HisnContent.chapters[step.chapter].1))
                    .font(.system(size: 12, weight: .bold)).foregroundColor(ink.opacity(0.7))
            } else {
                Spacer()
            }
        }
        .frame(height: 48).padding(.horizontal, 12)
    }

    // MARK: Pages

    @ViewBuilder private var page: some View {
        switch step {
        case .splash:
            Spacer(minLength: 110)
            HStack { Spacer(); HisnGateMark().fill(look.ink, style: FillStyle(eoFill: true)).frame(width: 92, height: 92); Spacer() }
            HisnTitle(text: ia("Hisn", "حِصن"), center: true, size: 30)
            HisnVerse(text: "قُل لِّلْمُؤْمِنِينَ يَغُضُّوا۟ مِنْ أَبْصَٰرِهِمْ",
                      ref: hisnArabic() ? hisnVerseRef("24:30") : "“Tell the believing men to lower their gaze” (Pickthall) · 24:30")
        case .intro:
            Spacer(minLength: 40)
            HStack { Spacer(); HisnIcon("tower", 110); Spacer() }
            HisnTitle(text: ia("Every phone has a door.", "في كل هاتفٍ باب."), center: true, size: 30, light: true)
            HisnSub(text: ia("Hisn guards it, so your heart can rest.", "وحصنٌ يحرسه، ليطمئنّ قلبك."), center: true, light: true)
            Spacer(minLength: 24)
            HisnQuote(text: "…جعل الصبرَ جوادًا لا يكبو، وصارمًا لا ينبو، وجندًا غالبًا لا يُهزم، وحِصنًا حصينًا لا يُهدم، فهو والنصرُ أخوان شقيقان.",
                      source: ia("Tazkiyat al-Nufūs, p. 76", "تزكية النفوس، ص 76"),
                      english: "Allah made patience a steed that does not stumble, a blade that does not blunt, an army that is not defeated, and a fortress that is not torn down. Patience and victory are brothers.",
                      light: true)
        case .how: howPage
        case .welcome:
            // As in the prototype: the gate mark, the name, one line, then three promises as soft pills above the landscape.
            Spacer(minLength: 18)
            HStack { Spacer(); HisnGateMark().fill(look.deepInk, style: FillStyle(eoFill: true)).frame(width: 64, height: 64); Spacer() }
            HisnSub(text: ia("Welcome to", "أهلًا بك في"), center: true, light: true)
            HisnTitle(text: ia("Hisn", "حِصن"), center: true, size: 44, light: true)
            HisnSub(text: ia("Allah has given you a way to guard your gaze and your heart. We're here to help you take it.",
                             "جعل الله لك سبيلًا تحفظ به بصرك وقلبك، ونحن هنا لنُعينك عليه."), center: true, light: true)
            Spacer(minLength: 190)
            VStack(spacing: 10) {
                ForEach([ia("Closes zina content on this phone", "يحجب محتوى الزنا على هاتفك"),
                         ia("Help in the hard minute", "عونٌ في اللحظة الصعبة"),
                         ia("Nearer to Allah, every day", "أقرب إلى الله كل يوم")], id: \.self) { line in
                    Text(line).font(.system(size: 14.5, weight: .medium)).foregroundColor(look.deepInk)
                        .padding(.horizontal, 16).padding(.vertical, 9)
                        .background(Capsule().fill(Color.white.opacity(0.14)))
                }
            }
            .frame(maxWidth: .infinity)
        case .name:
            HisnTitle(text: ia("What should we call you?", "بماذا نناديك؟"))
            HisnSub(text: ia("Your first name is enough. You can skip this.", "اسمك الأول يكفي، ويمكنك تخطّي هذا."))
            TextField(ia("Your name", "اسمك"), text: $name)
                .focused($typing).submitLabel(.done).textInputAutocapitalization(.words).autocorrectionDisabled()
                .font(.system(size: 18)).foregroundColor(look.ink).padding(16).hisnCard(look, radius: 16)
        case .look:
            HisnTitle(text: ia("Choose your look", "اختر المظهر"))
            HisnSub(text: ia("You can change it any time.", "يمكنك تغييره في أي وقت."))
            ForEach(HisnLook.all, id: \.key) { option in
                Button { tick(); lookKey = option.key } label: {
                    HStack(spacing: 12) {
                        Image(option.background).resizable().scaledToFill().frame(width: 64, height: 64)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        Text(option.name).font(.system(size: 17, weight: .semibold)).foregroundColor(look.ink)
                        Spacer()
                        Tick(on: option.key == lookKey)
                    }
                    .padding(12).hisnCard(look, radius: 18, stroke: option.key == lookKey ? look.pri : nil)
                }
                .buttonStyle(.plain)
            }
        case .speech:
            quiz(ia("How should Hisn speak to you?", "كيف يخاطبك حصن؟"), nil, HisnContent.speech, [speech]) { speech = $0 }
        case .age:
            quiz(ia("How old are you?", "كم عمرك؟"), ia("So the words fit you.", "حتى يناسبك الكلام."), HisnContent.ages, [age]) { age = $0 }
        case .guards:
            quiz(ia("What do you want to guard?", "ما الذي تريد أن تحفظه؟"), ia("Choose all that fit.", "اختر كل ما ينطبق عليك."),
                 HisnContent.guards, set(guards)) { guards = toggled(guards, $0) }
        case .hardest:
            quiz(ia("When is it hardest?", "متى يكون الأمر أصعب؟"), ia("Choose all that fit.", "اختر كل ما ينطبق عليك."),
                 HisnContent.hardest, set(hardest)) { hardest = toggled(hardest, $0) }
        case .prayers:
            quiz(ia("How are your prayers these days?", "كيف صلاتك هذه الأيام؟"), ia("Between you and Allah. No judgement here.", "بينك وبين الله، ولا حكم هنا."),
                 HisnContent.prayers, [prayers]) { prayers = $0 }
        case .hours: HisnHoursPage(hours: $hours)
        case .diag: HisnDiagnosisPage(name: name, guards: set(guards), hardest: set(hardest), hours: hours)
        case .wayOut: HisnWayOutPage(guards: set(guards), hardest: set(hardest))
        case .suggest: HisnSuggestPage(name: name, guards: set(guards), hardest: set(hardest))
        case .perms: HisnPermsPage(protection: protection)
        case .tryPause: HisnTryPage(tried: $tried)
        case .notify: HisnNotifyPage()
        case .slide0, .slide1, .slide2:
            let slide = HisnContent.slides[step.rawValue - Step.slide0.rawValue]
            Spacer(minLength: 40)
            HStack { Spacer(); HisnIcon(slide.icon, 150); Spacer() }
            HisnTitle(text: ia(slide.en, slide.ar), center: true, size: 27, light: true)
            HisnSub(text: ia(slide.bodyEn, slide.bodyAr), center: true, light: true)
            HisnVerse(text: slide.verse, ref: hisnVerseRef(slide.src), light: true)
        case .soft: softPage
        case .harm: harmPage
        case .goals:
            HisnTitle(text: ia("Follow your goals", "حدّد أهدافك"))
            HisnSub(text: ia("Every path is different. Choose what you want to work on.", "لكلٍّ طريقه. اختر ما تريد أن تعمل عليه."))
            pictureGrid(HisnContent.goals, chosen: set(goals)) { goals = toggled(goals, $0) }
        case .instead:
            HisnTitle(text: ia("What will you do instead?", "ماذا ستفعل بدلًا من ذلك؟"))
            HisnSub(text: ia("Help now will remind you of these.", "سيذكّرك «ساعدني الآن» بها."))
            pictureGrid(HisnContent.instead, chosen: set(instead)) { instead = toggled(instead, $0) }
        case .reason:
            HisnTitle(text: ia("Write one line to yourself for a hard minute", "اكتب سطرًا لنفسك تقرؤه في اللحظة الصعبة"))
            HisnSub(text: ia("You will see it first when you tap Help now.", "ستراه أولًا حين تضغط «ساعدني الآن»."))
            TextField(ia("I leave this out of fear of Allah and love for Him.", "أترك هذا خوفًا من الله وحبًّا له."), text: $reason, axis: .vertical)
                .focused($typing).lineLimit(2...4).font(.system(size: 17)).foregroundColor(look.ink).padding(16).hisnCard(look, radius: 16)
            FlowChips(items: HisnContent.reasons.map { ia($0.0, $0.1) }) { reason = $0.replacingOccurrences(of: "\u{200F}", with: "").replacingOccurrences(of: "\u{200E}", with: "") }
        case .told:
            HStack {
                HisnTitle(text: ia("Analysis done", "اكتمل التحليل"))
                Image(systemName: "checkmark").font(.system(size: 22, weight: .bold)).foregroundColor(look.kept)
            }
            HisnSub(text: name.isEmpty ? ia("This is what you told us, and what Hisn will do.", "هذا ما أخبرتنا به، وهذا ما سيفعله حصن.")
                    : ia("\(name), this is what you told us, and what Hisn will do.", "يا \(name)، هذا ما أخبرتنا به، وهذا ما سيفعله حصن."))
            ForEach(Array(HisnContent.told(hardest: set(hardest), prayers: prayers, guards: set(guards)).enumerated()), id: \.offset) { _, row in
                HStack(spacing: 12) {
                    Image(systemName: row.symbol).font(.system(size: 17)).foregroundColor(look.pri)
                        .frame(width: 38, height: 38).background(Circle().fill(look.soft))
                    VStack(alignment: .leading, spacing: 3) {
                        Text(row.title).font(.system(size: 15, weight: .bold)).foregroundColor(look.ink)
                        Text(row.body).font(.system(size: 13)).foregroundColor(look.sub).fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                }
                .padding(14).hisnCard(look)
            }
        case .begin:
            Spacer(minLength: 60)
            HStack { Spacer(); HisnIcon("hands", 130); Spacer() }
            HisnTitle(text: name.isEmpty ? ia("Today is a new beginning", "اليوم بداية جديدة") : ia("\(name), today is a new beginning", "يا \(name)، اليوم بداية جديدة"),
                      center: true, light: true)
            HisnSub(text: ia("Five short promises, to Allah and to yourself. Each one has a verse.", "خمسة عهود قصيرة، مع الله ومع نفسك، ومع كل عهد آية."), center: true, light: true)
        case .pledge:
            let line = HisnContent.pledges[pledge]
            Spacer(minLength: 30)
            Text(ia("Promise \(pledge + 1) / 5", "العهد \(pledge + 1) من 5")).font(.system(size: 13, weight: .bold)).foregroundColor(look.sub)
                .frame(maxWidth: .infinity)
            HisnTitle(text: ia(line.en, line.ar), center: true, size: 24)
            HisnVerse(text: line.verse, ref: hisnArabic() ? hisnVerseRef(line.src) : "“\(line.meaning)” (Pickthall) · \(line.src)")
                .padding(.vertical, 10)
            HoldToPromise(key: pledge) { tick(); if pledge < 4 { pledge += 1 } else { go(.plan) } }
                .frame(maxWidth: .infinity).padding(.top, 10)
        case .plan: planPage
        case .free:
            Spacer(minLength: 60)
            HStack { Spacer(); HisnIcon("lantern", 130); Spacer() }
            HisnTitle(text: ia("Hisn is free", "حصن مجاني"), center: true, size: 30)
            HisnSub(text: ia("No ads, no subscription.", "بلا إعلانات ولا اشتراك."), center: true)
            HisnSub(text: ia("Next: turn on the protection. It takes two minutes.", "التالي: تفعيل الحماية، ويأخذ دقيقتين."), center: true)
        }
    }

    /// "How Hisn works" in three frames: you open something, Hisn closes it, you turn back and the night still counts.
    @ViewBuilder private var howPage: some View {
        Text(ia("How Hisn works · \(howFrame + 1)/3", "كيف يعمل حصن · \(howFrame + 1)/3")).font(.system(size: 12, weight: .bold))
            .foregroundColor(look.sub).frame(maxWidth: .infinity).padding(.top, 10)
        Spacer(minLength: 60)
        Group {
            switch howFrame {
            case 0:
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 6) {
                        Image(systemName: "lock.fill").font(.system(size: 11))
                        Text("●●●●●●.com").font(.system(size: 13, weight: .medium))
                    }
                    .foregroundColor(look.sub).padding(.horizontal, 12).padding(.vertical, 7).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Capsule().fill(look.soft))
                    ForEach([1.0, 0.8, 0.6], id: \.self) { w in
                        RoundedRectangle(cornerRadius: 4).fill(look.line).frame(height: 10).frame(maxWidth: .infinity, alignment: .leading)
                            .scaleEffect(x: w, y: 1, anchor: .leading)
                    }
                }
            case 1:
                VStack(spacing: 6) {
                    HisnIcon("shield", 48)
                    Text(ia("Hisn closed this page", "أغلق حصن هذه الصفحة")).font(.system(size: 16, weight: .bold)).foregroundColor(look.ink)
                    Text(ia("Nothing was saved", "لم يُحفظ شيء")).font(.system(size: 13)).foregroundColor(look.sub)
                }
                .frame(maxWidth: .infinity).padding(.vertical, 10)
            default:
                let days = hisnArabic() ? ["ح", "ن", "ث", "ر", "خ", "ج", "س"] : ["S", "M", "T", "W", "T", "F", "S"]
                let marks: [(Int, Bool)] = [(17, true), (18, true), (19, false), (20, true), (21, true), (22, true), (23, false)]
                HStack(spacing: 6) {
                    ForEach(0..<7, id: \.self) { i in
                        VStack(spacing: 6) {
                            Text(days[i]).font(.system(size: 11, weight: .semibold)).foregroundColor(look.sub)
                            Text("\(marks[i].0)").font(.system(size: 12, weight: .semibold))
                                .foregroundColor(marks[i].1 ? Color(hex: 0x3A2200) : look.ink)
                                .frame(width: 32, height: 32)
                                .background(Circle().fill(marks[i].1 ? Color(hex: 0xD9A441) : Color.clear))
                                .overlay(Circle().stroke(Color(hex: 0xD9A441), lineWidth: marks[i].1 ? 0 : 2))
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
            }
        }
        .padding(16).frame(maxWidth: .infinity).hisnCard(look, radius: 20)
        HisnTitle(text: [ia("You open something without thinking", "تفتح شيئًا دون أن تنتبه"),
                         ia("Hisn closes it. No alarm, no shame.", "يُغلقه حصن، بلا صراخ ولا لوم."),
                         ia("You breathe, turn back, and the night still counts.", "تتنفّس وترجع، وتبقى ليلتك محسوبة.")][howFrame],
                  center: true, size: 23)
            .padding(.top, 8)
        HStack(spacing: 6) {
            ForEach(0..<3, id: \.self) { i in
                Capsule().fill(look.pri.opacity(i == howFrame ? 1 : 0.25)).frame(width: i == howFrame ? 18 : 6, height: 6)
            }
        }
        .frame(maxWidth: .infinity).padding(.top, 6)
    }

    /// "Zina does not start with zina": the suggestive, then the explicit, with al-Saʿdī on the verse.
    @ViewBuilder private var softPage: some View {
        Text(ia("Where it starts", "من أين يبدأ")).font(.system(size: 12, weight: .bold)).foregroundColor(look.sub)
        HisnTitle(text: ia("Zina does not start with zina", "الزنا لا يبدأ بالزنا"))
        part(ia("1 · What finds you", "1 · ما يأتيك دون أن تطلبه"), ia("Suggestive clips", "المقاطع المثيرة"),
             ia("Revealing dress, dancing and hints in short videos, ads and series. It looks ordinary, but it wakes desire and trains the eye to want more.",
                "لباس فاضح ورقص وإيحاءات في المقاطع القصيرة والإعلانات والمسلسلات. يبدو أمرًا عاديًّا، لكنه يوقظ الشهوة ويعوّد العين أن تطلب المزيد."))
        Text("↓").font(.system(size: 18)).foregroundColor(look.sub).frame(maxWidth: .infinity)
        part(ia("2 · What you then look for", "2 · ما تبحث عنه بعدها"), ia("Explicit zina content", "مقاطع الزنا الصريحة"),
             ia("The eye that got used to a little asks for more, and a search follows. Almost nobody begins here; they arrive here.",
                "العين التي اعتادت القليل تطلب الأكثر، فيأتي البحث. قلّ من يبدأ من هنا، وإنما يصل إليه."))
        HisnVerse(text: "وَلَا تَقْرَبُوا۟ ٱلزِّنَىٰٓ ۖ إِنَّهُۥ كَانَ فَٰحِشَةً وَسَآءَ سَبِيلًا",
                  ref: hisnArabic() ? hisnVerseRef("17:32") : "“And come not near unto adultery. Lo! it is an abomination and an evil way.” (Pickthall) · 17:32")
        HisnQuote(text: HisnContent.saadi,
                  source: ia("al-Saʿdī · Taysīr al-Karīm ar-Raḥmān, on al-Isrāʾ 32", "السعدي · تيسير الكريم الرحمن، تفسير الإسراء 32"),
                  english: "al-Saʿdī explains that forbidding coming near it is stronger than forbidding the act alone, because it covers everything that leads to it.")
        HisnSub(text: ia("Hisn closes both: the apps you choose, and the zina sites.", "حصن يغلق الاثنين: التطبيقات التي تختارها، ومواقع الزنا."), center: true)
    }

    private func part(_ step: String, _ title: String, _ body: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(step).font(.system(size: 12, weight: .bold)).foregroundColor(look.acc)
            Text(title).font(.system(size: 16, weight: .bold)).foregroundColor(look.ink)
            Text(body).font(.system(size: 13.5)).lineSpacing(3).foregroundColor(look.sub).fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading).padding(14).hisnCard(look)
    }

    /// The harms of sins from «الداء والدواء», one to a page, then the cure.
    @ViewBuilder private var harmPage: some View {
        let count = HisnContent.harms.count
        let source: (Int) -> String = { ia("Ibn al-Qayyim · ad-Dāʾ wa ad-Dawāʾ, p. \($0)", "ابن القيم · الداء والدواء، ص \($0)") }
        Spacer(minLength: 24)
        if harm == 0 {
            HStack { Spacer(); HisnIcon("heart", 130); Spacer() }
            caption(ia("From the book ad-Dāʾ wa ad-Dawāʾ", "من كتاب «الداء والدواء» لابن القيم"))
            HisnTitle(text: ia("What sins do to the heart", "ما تفعله الذنوب بالقلب"), center: true, size: 27, light: true)
            HisnQuote(text: HisnContent.harmLead, source: source(HisnContent.harmLeadPage), light: true)
            HisnSub(text: ia("Six harms, one at a time. Then the cure.", "ستة آثار، واحدًا واحدًا. ثم الدواء."), center: true, light: true)
        } else if harm <= count {
            let line = HisnContent.harms[harm - 1]
            HStack { Spacer(); HisnIcon(line.icon, 130); Spacer() }
            caption(ia("Harm \(harm) of \(count)", "الأثر \(harm) من \(count)"))
            HisnTitle(text: ia(line.en, line.ar), center: true, size: 27, light: true)
            HisnQuote(text: line.quote, source: source(line.page), light: true)
        } else {
            HStack { Spacer(); HisnIcon("heart_gold", 130); Spacer() }
            caption(ia("And the cure", "والدواء"))
            HisnTitle(text: ia("Every illness has a cure", "لكلّ داءٍ دواء"), center: true, size: 27, light: true)
            HisnSub(text: ia("Repentance wipes the sin out with Allah. Its effects in this life stay until you change yourself.",
                             "التوبة تمحو الذنب عند الله. أما آثاره في دنياك فتبقى حتى تغيّر نفسك."), center: true, light: true)
            HisnVerse(text: "لَا تَقْنَطُوا۟ مِن رَّحْمَةِ ٱللَّهِ",
                      ref: hisnArabic() ? hisnVerseRef("39:53") : "“Despair not of the mercy of Allah, Who forgiveth all sins.” (Pickthall) · 39:53", light: true)
        }
        HStack(spacing: 5) {
            ForEach(0..<(count + 2), id: \.self) { i in
                Capsule().fill(look.deepInk.opacity(i == harm ? 1 : 0.3)).frame(width: i == harm ? 18 : 5, height: 5)
            }
        }
        .frame(maxWidth: .infinity).padding(.top, 8)
    }

    private func caption(_ text: String) -> some View {
        Text(text).font(.system(size: 12.5, weight: .bold)).foregroundColor(look.deepInk.opacity(0.75)).frame(maxWidth: .infinity)
    }

    /// Forty nights in four stretches. Counting, not a promise.
    @ViewBuilder private var planPage: some View {
        let end = Calendar.current.date(byAdding: .day, value: 40, to: Date()) ?? Date()
        HisnTitle(text: name.isEmpty ? ia("Here is your plan", "هذه خطّتك") : ia("\(name), here is your plan", "يا \(name)، هذه خطّتك"), center: true)
        VStack(spacing: 4) {
            Text(ia("If every night is clean", "إن صفت كل ليلة")).font(.system(size: 12, weight: .bold)).foregroundColor(look.sub)
            Text(HisnDates.hijri(end)).font(.system(size: 22, weight: .bold)).foregroundColor(look.pri)
            Text(HisnDates.gregorian(end, withYear: true)).font(.system(size: 14, weight: .semibold)).foregroundColor(look.ink)
            Text(ia("40 nights of light · a slip doesn't reset the plan. This is counting, not a promise.", "أربعون ليلة من النور، والزلّة لا تُعيد الخطة من الصفر. هذا عدٌّ لا وعد."))
                .font(.system(size: 12.5)).foregroundColor(look.sub).multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity).padding(16).hisnCard(look, radius: 18)
        ForEach([("1–7", ia("Close the doors", "أغلق الأبواب"), ia("Walls up, quiet nights", "الأسوار قائمة، والليالي هادئة")),
                 ("8–14", ia("Fill the time", "املأ الوقت"), HisnContent.instead.filter { set(instead).contains($0.key) }.map(\.label).joined(separator: ia(", ", "، "))),
                 ("15–30", ia("Build the habit", "ابنِ العادة"), HisnContent.goals.filter { set(goals).contains($0.key) }.map(\.label).joined(separator: ia(", ", "، "))),
                 ("31–40", ia("Steady", "الثبات"), ia("Your first milestone: the crescent", "أول محطة لك: الهلال"))], id: \.0) { row in
            HStack(spacing: 12) {
                Text(row.0).font(.system(size: 13, weight: .bold)).foregroundColor(look.pri).frame(width: 56, height: 34)
                    .background(Capsule().fill(look.chip))
                VStack(alignment: .leading, spacing: 2) {
                    Text(row.1).font(.system(size: 15, weight: .bold)).foregroundColor(look.ink)
                    if !row.2.isEmpty { Text(row.2).font(.system(size: 13)).foregroundColor(look.sub).fixedSize(horizontal: false, vertical: true) }
                }
                Spacer(minLength: 0)
            }
            .padding(12).hisnCard(look)
        }
    }

    // MARK: Building blocks

    @ViewBuilder private func quiz(_ title: String, _ sub: String?, _ options: [HisnContent.Opt], _ chosen: Set<String>, pick: @escaping (String) -> Void) -> some View {
        HisnTitle(text: title)
        if let sub { HisnSub(text: sub) }
        ForEach(Array(options.enumerated()), id: \.element.key) { index, option in
            let on = chosen.contains(option.key)
            Button { tick(); pick(option.key) } label: {
                HStack(spacing: 12) {
                    ZStack {
                        Circle().fill(on ? look.pri : look.soft).frame(width: 28, height: 28)
                        if on { Image(systemName: "checkmark").font(.system(size: 12, weight: .bold)).foregroundColor(look.priInk) }
                        else { Text("\(index + 1)").font(.system(size: 12, weight: .bold)).foregroundColor(look.sub) }
                    }
                    Text(option.label).font(.system(size: 16, weight: on ? .semibold : .regular)).foregroundColor(look.ink)
                        .multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 14).frame(minHeight: 56).hisnCard(look, stroke: on ? look.pri : nil)
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(on ? .isSelected : [])
        }
    }

    private func pictureGrid(_ options: [HisnContent.IconOpt], chosen: Set<String>, pick: @escaping (String) -> Void) -> some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
            ForEach(options) { option in
                let on = chosen.contains(option.key)
                Button { tick(); pick(option.key) } label: {
                    HStack(spacing: 8) {
                        HisnIcon(option.icon, 34)
                        Text(option.label).font(.system(size: 13.5, weight: on ? .semibold : .regular)).foregroundColor(look.ink)
                            .multilineTextAlignment(.leading).fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 0)
                        Tick(on: on, size: 20)
                    }
                    .padding(10).frame(maxWidth: .infinity, minHeight: 60).hisnCard(look, fill: on ? look.chip : nil, stroke: on ? look.pri : nil)
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
    }

    // MARK: Actions

    @ViewBuilder private func actions(deep: Bool) -> some View {
        if step == .splash || step == .intro {
            Button(ia("Tap to continue", "اضغط للمتابعة"), action: next)
                .font(.system(size: 15, weight: .semibold)).foregroundColor(.white.opacity(0.9)).frame(maxWidth: .infinity, minHeight: 52)
        } else if step == .notify {
            HisnPrimaryButton(label: ia("Yes, remind me", "نعم، ذكّرني")) { Task { _ = await HisnReminders.enable(); advance() } }
            Button(ia("Later", "لاحقًا")) { advance() }
                .font(.system(size: 14, weight: .semibold)).foregroundColor(.white.opacity(0.9)).frame(minHeight: 44)
        } else if step != .pledge {
            HisnPrimaryButton(label: nextLabel, white: deep, enabled: canContinue, action: next)
            if [.name, .speech, .age, .prayers, .hours, .tryPause].contains(step) {
                Button(ia("Skip this question", "تخطَّ هذا السؤال")) { advance() }
                    .font(.system(size: 14, weight: .semibold)).foregroundColor(step.land ? .white.opacity(0.9) : look.sub).frame(minHeight: 44)
            }
        }
    }

    private var nextLabel: String {
        switch step {
        case .how: return howFrame < 2 ? ia("Next →", "التالي ←") : ia("Got it →", "فهمت ←")
        case .welcome: return ia("Start the questions →", "ابدأ الأسئلة ←")
        case .harm: return harm > HisnContent.harms.count ? ia("Show me the way →", "أرني الطريق ←") : ia("Next →", "التالي ←")
        case .slide0, .slide1, .slide2, .soft: return ia("Next →", "التالي ←")
        case .goals: return ia("Choose my goals →", "اعتمد أهدافي ←")
        case .diag: return ia("Show me the way out →", "أرني المخرج ←")
        case .wayOut: return ia("Build my plan →", "ابنِ خطّتي ←")
        case .suggest: return ia("Use these →", "اعتمدها ←")
        case .perms:
            let missing = HisnPermsPage.missing(protection)
            return missing == 0 ? ia("Continue →", "متابعة ←") : ia("\(missing) still needed, continue anyway →", "\(missing) ما زال ضروريًا، تابع على أي حال ←")
        case .tryPause: return ia("Continue →", "متابعة ←")
        case .reason: return ia("Save my line →", "احفظ سطري ←")
        case .told: return ia("That's right →", "هذا صحيح ←")
        case .begin: return ia("Make my promises →", "أعطِ عهودي ←")
        case .plan: return ia("Begin →", "لنبدأ ←")
        case .free: return ia("Open Hisn →", "افتح حصنًا ←")
        default: return ia("Continue →", "متابعة ←")
        }
    }

    private var canContinue: Bool {
        switch step {
        case .guards: return !set(guards).isEmpty
        case .hardest: return !set(hardest).isEmpty
        case .goals: return !set(goals).isEmpty
        default: return true
        }
    }

    private func next() {
        typing = false
        if step == .harm, harm <= HisnContent.harms.count { harm += 1; return }
        if step == .how, howFrame < 2 { howFrame += 1; return }
        if step == .free { HisnJourney.begin(); onDone(); return }
        advance()
    }

    private func advance() {
        typing = false
        if let following = Step(rawValue: step.rawValue + 1) { go(following) }
    }

    private func back() {
        typing = false
        if step == .harm, harm > 0 { harm -= 1; return }
        if step == .how, howFrame > 0 { howFrame -= 1; return }
        if step == .pledge, pledge > 0 { pledge -= 1; return }
        if let previous = Step(rawValue: step.rawValue - 1) { go(previous) }
    }

    private func go(_ target: Step) { step = target }
    private func tick() { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    private func set(_ value: String) -> Set<String> { Set(value.split(separator: ",").map(String.init)) }
    private func toggled(_ value: String, _ key: String) -> String {
        var keys = value.split(separator: ",").map(String.init)
        if let index = keys.firstIndex(of: key) { keys.remove(at: index) } else { keys.append(key) }
        return keys.joined(separator: ",")
    }
}

/// The round tick of a chosen card.
private struct Tick: View {
    @Environment(\.hisnLook) private var look
    let on: Bool
    var size: CGFloat = 24
    var body: some View {
        ZStack {
            if on {
                Circle().fill(look.pri)
                Image(systemName: "checkmark").font(.system(size: size * 0.5, weight: .bold)).foregroundColor(look.priInk)
            } else {
                Circle().stroke(look.line, lineWidth: 1.5)
            }
        }
        .frame(width: size, height: size)
    }
}

/// Ready-made reasons the person can tap into their line.
private struct FlowChips: View {
    @Environment(\.hisnLook) private var look
    let items: [String]
    let pick: (String) -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(items, id: \.self) { item in
                Button { pick(item) } label: {
                    Text(item).font(.system(size: 14)).foregroundColor(look.ink).padding(.horizontal, 14).padding(.vertical, 10)
                        .background(Capsule().fill(look.card)).overlay(Capsule().stroke(look.line, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
        }
    }
}

/// Hold for a second and a half to make the promise; the phone taps as the ring fills.
private struct HoldToPromise: View {
    @Environment(\.hisnLook) private var look
    let key: Int
    let onDone: () -> Void
    @State private var progress: CGFloat = 0
    @State private var holding = false
    @State private var timer: Timer?

    var body: some View {
        VStack(spacing: 10) {
            ZStack {
                Circle().stroke(look.line, lineWidth: 6)
                // Fills clockwise from the top in both languages; nothing is drawn before the press.
                if progress > 0 {
                    Circle().trim(from: 0, to: progress).stroke(look.pri, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                        .rotationEffect(.degrees(-90)).environment(\.layoutDirection, .leftToRight)
                }
                HisnIcon("hands", 64)
            }
            .frame(width: 124, height: 124)
            .contentShape(Circle())
            .gesture(DragGesture(minimumDistance: 0).onChanged { _ in start() }.onEnded { _ in stop() })
            .accessibilityElement()
            .accessibilityLabel(ia("Hold to promise", "اضغط مطوّلًا لتعاهد"))
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { onDone() }
            Text(ia("Hold to promise", "اضغط مطوّلًا لتعاهد")).font(.system(size: 14, weight: .semibold)).foregroundColor(look.sub)
        }
        .onChange(of: key) { _ in progress = 0 }
        .onDisappear { timer?.invalidate() }
    }

    private func start() {
        guard !holding else { return }
        holding = true
        let haptic = UIImpactFeedbackGenerator(style: .soft)
        timer = Timer.scheduledTimer(withTimeInterval: 0.03, repeats: true) { timer in
            progress = min(1, progress + 0.02)
            if Int(progress * 50) % 6 == 0 { haptic.impactOccurred(intensity: 0.4 + progress * 0.6) }
            if progress >= 1 {
                timer.invalidate()
                holding = false
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                onDone()
            }
        }
    }

    private func stop() {
        guard holding else { return }
        holding = false
        timer?.invalidate()
        withAnimation(.easeOut(duration: 0.25)) { progress = 0 }
    }
}

/// Hijri first, always; then the Gregorian date as the person's own country writes it, with 1 2 3 digits.
enum HisnDates {
    private static func locale(_ calendar: String?) -> Locale {
        guard hisnArabic() else { return Locale(identifier: calendar.map { "en@calendar=\($0)" } ?? "en") }
        let region = Locale.autoupdatingCurrent.region?.identifier ?? "EG"
        return Locale(identifier: "ar_\(region)@numbers=latn" + (calendar.map { ";calendar=\($0)" } ?? ""))
    }

    static func hijri(_ date: Date, withYear: Bool = true) -> String {
        let formatter = DateFormatter()
        var calendar = Calendar(identifier: .islamicUmmAlQura)
        calendar.locale = locale("islamic-umalqura")
        formatter.calendar = calendar
        formatter.locale = locale("islamic-umalqura")
        formatter.setLocalizedDateFormatFromTemplate(withYear ? "dMMMMy" : "dMMMM")
        return formatter.string(from: date)
    }

    static func gregorian(_ date: Date, withYear: Bool = false) -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = locale(nil)
        formatter.setLocalizedDateFormatFromTemplate(withYear ? "dMMMMy" : "dMMMM")
        return formatter.string(from: date)
    }
}

/// Hisn's flat gate mark, from the prototype's logo (64 × 64 drawing, even-odd fill).
struct HisnGateMark: Shape {
    func path(in rect: CGRect) -> Path {
        let k = min(rect.width, rect.height) / 64
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: rect.minX + x * k, y: rect.minY + y * k) }
        var path = Path()
        path.move(to: p(10, 7))
        for (x, y) in [(18, 7), (18, 12), (24, 12), (24, 7), (40, 7), (40, 12), (46, 12), (46, 7), (54, 7), (54, 34)] as [(CGFloat, CGFloat)] {
            path.addLine(to: p(x, y))
        }
        path.addCurve(to: p(32, 61), control1: p(54, 47), control2: p(45, 55.5))
        path.addCurve(to: p(10, 34), control1: p(19, 55.5), control2: p(10, 47))
        path.closeSubpath()
        path.move(to: p(24, 52))
        path.addLine(to: p(24, 36.5))
        path.addQuadCurve(to: p(32, 22.5), control: p(24, 27.5))
        path.addQuadCurve(to: p(40, 36.5), control: p(40, 27.5))
        path.addLine(to: p(40, 52))
        path.closeSubpath()
        return path
    }
}
