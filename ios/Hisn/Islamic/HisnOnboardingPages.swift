import SwiftUI
import UserNotifications

// The first-run pages that read the person's answers back and set Hisn up, as in the prototype.
// On iPhone they name only what iPhone can do: Safari's filter, the website filter for every app,
// Screen Time for the apps the person chooses, the pause, quiet hours and the lock.

enum HisnAnswers {
    static let pulls: [String: (String, String)] = [
        "adult": ("Zina content", "محتوى الزنا"), "shorts": ("Short videos", "المقاطع القصيرة"),
        "feeds": ("The phone in bed", "تصفّح الهاتف في السرير"), "time": ("The phone at prayer", "الهاتف وقت الصلاة"),
        "games": ("Games", "الألعاب"), "chats": ("Chats", "المحادثات"), "series": ("Series and films", "المسلسلات والأفلام")
    ]
    static let whenHard: [String: (String, String, String, String)] = [
        "night": ("Late at night", "ليلًا وأنت وحدك", "when the house is quiet and the phone is in your hand", "حين يهدأ البيت، ويكون الهاتف في يدك، ولا يراك أحد"),
        "stressed": ("After a hard day", "بعد يوم صعب", "when you are tired and want to numb the day", "حين تتعب وتبحث عمّا يُنسيك يومك"),
        "bored": ("When bored", "عند الفراغ", "when there is nothing to do and the feed is one tap away", "حين لا شيء تفعله والمقاطع على بُعد لمسة"),
        "waking": ("On waking", "عند الاستيقاظ", "when the phone is the first thing in your hand, before the day has begun", "حين يكون الهاتف أول ما تمسكه قبل أن يبدأ يومك"),
        "home": ("Alone at home", "وحدك في البيت", "when nobody is around and nothing stands between you and the screen", "حين لا يكون حولك أحد، ولا شيء بينك وبين الشاشة"),
        "sad": ("After a quarrel or sadness", "بعد خلاف أو حزن", "when the heart is sore and looks for something to forget with", "حين يضيق صدرك وتبحث عمّا تنسى به"),
        "travel": ("Travelling or on holiday", "في السفر أو الإجازة", "when the usual day is gone and with it the usual guards", "حين يغيب يومك المعتاد ويغيب معه ما كان يحفظك")
    ]
    static let hours = [
        HisnContent.Opt(key: "lt2", en: "Less than 2 hours", ar: "أقل من ساعتين"), HisnContent.Opt(key: "2to4", en: "2 to 4 hours", ar: "من ساعتين إلى أربع"),
        HisnContent.Opt(key: "4to6", en: "4 to 6 hours", ar: "من أربع إلى ست ساعات"), HisnContent.Opt(key: "6to8", en: "6 to 8 hours", ar: "من ست إلى ثماني ساعات"),
        HisnContent.Opt(key: "gt8", en: "More than 8 hours", ar: "أكثر من ثماني ساعات")
    ]
    static let hoursAverage: [String: Double] = ["lt2": 1.5, "2to4": 3, "4to6": 5, "6to8": 7, "gt8": 9]

    /// Al-Fawāʾid, p. 45: Ibn al-Qayyim on how a passing thought becomes a habit.
    static let chain = "دافع الخطرة؛ فإن لم تفعل صارت فكرةً؛ فدافع الفكرة؛ فإن لم تفعل صارت شهوةً؛ فحاربها؛ فإن لم تفعل صارت عزيمةً وهمّةً؛ فإن لم تدافعها صارت فعلًا؛ فإن لم تتداركه بضدّه صار عادةً، فيصعب عليك الانتقال عنها."
    static let steps = [("A glance", "نظرة"), ("A thought", "خطرة"), ("An idea", "فكرة"), ("A wish", "شهوة"), ("The act", "فعل")]
}

private struct OnbCard<Content: View>: View {
    @Environment(\.hisnLook) private var look
    var highlight = false
    @ViewBuilder let content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 4) { content }
            .frame(maxWidth: .infinity, alignment: .leading).padding(14)
            .hisnCard(look, stroke: highlight ? look.acc : nil)
    }
}

private struct Kicker: View {
    @Environment(\.hisnLook) private var look
    let text: String
    var body: some View { Text(text).font(.system(size: 12, weight: .bold)).foregroundColor(look.sub) }
}

// MARK: How many hours

struct HisnHoursPage: View {
    @Environment(\.hisnLook) private var look
    @Binding var hours: String
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HisnTitle(text: ia("How many hours a day are you on your phone?", "كم ساعة تقضي على هاتفك في اليوم؟"))
            HisnSub(text: ia("A rough guess is fine. iPhone keeps the real numbers in Screen Time.", "التقدير التقريبي يكفي. والأرقام الحقيقية في «مدة استخدام الجهاز» على هاتفك."))
            ForEach(Array(HisnAnswers.hours.enumerated()), id: \.element.key) { i, o in
                let on = hours == o.key
                Button { UIImpactFeedbackGenerator(style: .light).impactOccurred(); hours = o.key } label: {
                    HStack(spacing: 12) {
                        ZStack {
                            Circle().fill(on ? look.pri : look.soft).frame(width: 28, height: 28)
                            if on { Image(systemName: "checkmark").font(.system(size: 12, weight: .bold)).foregroundColor(look.priInk) }
                            else { Text("\(i + 1)").font(.system(size: 12, weight: .bold)).foregroundColor(look.sub) }
                        }
                        Text(o.label).font(.system(size: 16, weight: on ? .semibold : .regular)).foregroundColor(look.ink)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 14).frame(minHeight: 56).hisnCard(look, stroke: on ? look.pri : nil)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

// MARK: What is happening

struct HisnDiagnosisPage: View {
    @Environment(\.hisnLook) private var look
    let name: String
    let guards: Set<String>
    let hardest: Set<String>
    let hours: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Kicker(text: ia("What is happening", "هذا ما يحدث معك"))
            HisnTitle(text: name.isEmpty ? ia("It is not that you are weak. It is a cycle.", "ليست المشكلة أنك ضعيف، إنها دائرة.")
                      : ia("\(name), it is not that you are weak. It is a cycle.", "يا \(name)، ليست المشكلة أنك ضعيف، إنها دائرة."), size: 24)
            let pulls = (guards.isEmpty ? ["adult"] : HisnContent.guards.map(\.key).filter { guards.contains($0) }).compactMap { HisnAnswers.pulls[$0] }
            OnbCard {
                Kicker(text: ia("What pulls you", "ما يجذبك"))
                Text(pulls.map { ia($0.0, $0.1) }.joined(separator: ia(", ", "، "))).font(.system(size: 15, weight: .semibold)).foregroundColor(look.ink)
            }
            let hard = HisnContent.hardest.map(\.key).filter { hardest.contains($0) }.compactMap { HisnAnswers.whenHard[$0] }
            if let first = hard.first {
                OnbCard {
                    Kicker(text: ia("When it is hardest", "متى يصعب"))
                    Text(hard.map { ia($0.0, $0.1) }.joined(separator: ia(", ", "، "))).font(.system(size: 15, weight: .semibold)).foregroundColor(look.ink)
                    Text(ia(first.2, first.3)).font(.system(size: 13)).foregroundColor(look.sub)
                }
            }
            if let h = HisnAnswers.hoursAverage[hours] {
                OnbCard {
                    Kicker(text: ia("What it costs you", "ما يكلّفك"))
                    let days = Int((h * 365 / 24).rounded())
                    Text(ia("About \(Int(h.rounded())) hours a day · about \(days) whole days a year", "قرابة \(Int(h.rounded())) ساعات يوميًا · قرابة \(days) يومًا كاملًا في السنة"))
                        .font(.system(size: 15, weight: .semibold)).foregroundColor(look.ink)
                    Text(rtl("«لا تزول قدما عبدٍ يوم القيامة حتى يُسأل عن عمره فيمَ أفناه» (الترمذي 2417)"))
                        .font(.system(size: 13)).foregroundColor(look.sub).fixedSize(horizontal: false, vertical: true)
                }
            }
            Kicker(text: ia("Every slip passes the same five steps", "كل زلّة تمرّ بالخطوات الخمس نفسها"))
            HisnChainRow()
            HisnQuote(text: HisnAnswers.chain, source: ia("Ibn al-Qayyim, al-Fawāʾid, p. 45", "ابن القيم، الفوائد، ص 45"))
            HisnSub(text: ia("The earlier you stop it, the easier it is. That is what Hisn is for.", "وكلما أوقفتها أبكر كان أسهل. ولهذا صُنع حصن."), center: true)
        }
    }
}

/// نظرة ← خطرة ← فكرة ← شهوة ← فعل, numbered, the act in red.
struct HisnChainRow: View {
    @Environment(\.hisnLook) private var look
    var body: some View {
        HStack(spacing: 2) {
            ForEach(0..<5, id: \.self) { i in
                VStack(spacing: 4) {
                    Text("\(i + 1)").font(.system(size: 11, weight: .bold)).foregroundColor(i == 4 ? Color(hex: 0xC24848) : look.pri)
                        .frame(width: 24, height: 24).background(Circle().fill(i == 4 ? Color(hex: 0xFBE3E3) : look.soft))
                    Text(ia(HisnAnswers.steps[i].0, HisnAnswers.steps[i].1)).font(.system(size: 13, weight: .semibold)).foregroundColor(look.ink)
                        .lineLimit(1).minimumScaleFactor(0.7)
                }
                .frame(maxWidth: .infinity)
                if i < 4 { Text(hisnArabic() ? "←" : "→").font(.system(size: 13)).foregroundColor(look.sub) }
            }
        }
        .padding(.vertical, 12).padding(.horizontal, 6).hisnCard(look)
        .environment(\.layoutDirection, hisnArabic() ? .rightToLeft : .leftToRight)
    }
}

// MARK: The way out

struct HisnWayOutPage: View {
    @Environment(\.hisnLook) private var look
    let guards: Set<String>
    let hardest: Set<String>

    private struct Remedy { let key, icon, problemEn, problemAr, answerEn, answerAr: String }
    private let remedies = [
        Remedy(key: "adult", icon: "shield", problemEn: "Zina content reaches you", problemAr: "محتوى الزنا يصل إليك",
               answerEn: "Safari's filter and the website filter close zina sites in Safari and in every app.",
               answerAr: "مرشّح سفاري ومرشّح المواقع يغلقان مواقع الزنا في سفاري وفي كل التطبيقات."),
        Remedy(key: "shorts", icon: "hourglass", problemEn: "The feeds take your hours", problemAr: "المقاطع تأخذ ساعاتك",
               answerEn: "Screen Time closes the apps you choose, and a pause stands before the ones you keep.",
               answerAr: "تُغلق «مدة استخدام الجهاز» التطبيقات التي تختارها، وتقف الوقفة قبل ما تُبقيه."),
        Remedy(key: "night", icon: "crescent", problemEn: "Night alone with the phone", problemAr: "الليل وحدك مع الهاتف",
               answerEn: "Quiet hours close the apps you chose from ʿIshāʾ to Fajr.", answerAr: "الساعات الهادئة تُغلق التطبيقات التي اخترتها من العشاء إلى الفجر."),
        Remedy(key: "time", icon: "mosque", problemEn: "The phone keeps you from prayer", problemAr: "الهاتف يشغلك عن الصلاة",
               answerEn: "Prayer times on Home, with the next one counting down.", answerAr: "مواقيت الصلاة في الرئيسية، والعدّ التنازلي للصلاة التالية أمامك."),
        Remedy(key: "", icon: "phonedown", problemEn: "The hard minute", problemAr: "اللحظة الصعبة",
               answerEn: "One button: your own promise, a verse, dhikr with your breath, then wuḍūʾ and two rakʿahs.",
               answerAr: "زرّ واحد: عهدك أنت، وآية، وذكر مع أنفاسك، ثم وضوء وركعتان."),
        Remedy(key: "", icon: "hands", problemEn: "After a slip", problemAr: "بعد الزلّة",
               answerEn: "This is a way of life, not days you count. If you slip, what passed is not lost: three steps back, and the door open.",
               answerAr: "هذا طريق حياة تسير فيه، لا أيام تعدّها. إن زللتَ لم يضِع ما مضى: ثلاث خطوات للعودة، والباب مفتوح."),
        Remedy(key: "", icon: "quran", problemEn: "An empty heart", problemAr: "قلب فارغ",
               answerEn: "Every day a saying from Ibn al-Qayyim, Ibn al-Jawzī and Ibn Rajab, adhkār with a counter, and a reckoning in a minute.",
               answerAr: "كل يوم قول من ابن القيم وابن الجوزي وابن رجب، وأذكار بعدّاد، ومحاسبة في دقيقة.")
    ]
    private let breaks = [("Safari's filter and the website filter close zina sites", "مرشّح سفاري ومرشّح المواقع يغلقان مواقع الزنا"),
                          ("Help now: your promise, dhikr, a breath", "ساعدني الآن: عهدك، وذكر، ونَفَس"),
                          ("The pause: a moment before the apps you chose open", "الوقفة: لحظة قبل فتح التطبيقات التي اخترتها"),
                          ("The lock: what you closed stays closed in the hard minute", "القفل: ما أغلقته يبقى مغلقًا في اللحظة الصعبة"),
                          ("Coming back: no reset, three steps, the door open", "العودة: لا تصفير، ثلاث خطوات، والباب مفتوح")]

    private func mine(_ key: String) -> Bool {
        guard !key.isEmpty else { return false }
        if key == "adult" { return guards.contains("adult") || guards.isEmpty }
        if key == "shorts" { return !guards.intersection(["shorts", "feeds", "games", "series"]).isEmpty }
        if key == "night" { return !hardest.intersection(["night", "home"]).isEmpty || guards.contains("feeds") }
        return guards.contains(key)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Kicker(text: ia("The way out", "المخرج"))
            HisnTitle(text: ia("Here is what Hisn does for you", "هذا ما يفعله حصن لأجلك"), size: 24)
            ForEach(remedies.filter { mine($0.key) } + remedies.filter { !mine($0.key) }, id: \.problemEn) { r in
                HStack(spacing: 10) {
                    HisnIcon(r.icon, 38)
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 6) {
                            Text(ia(r.problemEn, r.problemAr)).font(.system(size: 12, weight: .semibold)).foregroundColor(look.sub)
                            if mine(r.key) {
                                Text(ia("for you", "لك")).font(.system(size: 10.5, weight: .bold)).foregroundColor(.white)
                                    .padding(.horizontal, 7).padding(.vertical, 1).background(Capsule().fill(look.acc))
                            }
                        }
                        Text(ia(r.answerEn, r.answerAr)).font(.system(size: 13.5)).foregroundColor(look.ink).fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                }
                .padding(12).hisnCard(look, stroke: mine(r.key) ? look.acc : nil)
            }
            Kicker(text: ia("Where Hisn breaks the cycle", "أين يكسر حصن الدائرة"))
            ForEach(0..<5, id: \.self) { i in
                HStack(alignment: .top, spacing: 10) {
                    Text("\(i + 1)").font(.system(size: 11, weight: .bold)).foregroundColor(.white).frame(width: 22, height: 22).background(Circle().fill(look.kept))
                    Text(ia(HisnAnswers.steps[i].0, HisnAnswers.steps[i].1) + " · " + ia(breaks[i].0, breaks[i].1))
                        .font(.system(size: 13)).foregroundColor(look.sub).fixedSize(horizontal: false, vertical: true)
                }
            }
            Text(ia("You do the turning. Allah gives the strength. Hisn only closes the doors.", "أنت تتوب، والله يعين، وحصن يغلق الأبواب فقط."))
                .font(.system(size: 15, weight: .semibold)).foregroundColor(look.ink).multilineTextAlignment(.center)
                .frame(maxWidth: .infinity).padding(14).background(RoundedRectangle(cornerRadius: 16).fill(look.soft))
        }
    }
}

// MARK: Suggested rules, from the person's answers

struct HisnSuggestPage: View {
    @Environment(\.hisnLook) private var look
    let name: String
    let guards: Set<String>
    let hardest: Set<String>

    struct Rule: Identifiable { let id: String; let icon, en, ar, whyEn, whyAr: String }
    static func rules(guards: Set<String>, hardest: Set<String>) -> [Rule] {
        var out = [
            Rule(id: "safari", icon: "shield", en: "Safari's filter", ar: "مرشّح سفاري", whyEn: "Zina sites closed before the page opens", whyAr: "تُغلق مواقع الزنا قبل أن تُفتح الصفحة"),
            Rule(id: "dns", icon: "tower", en: "The website filter for every app", ar: "مرشّح المواقع لكل التطبيقات", whyEn: "The same list in every app and browser", whyAr: "القائمة نفسها في كل تطبيق ومتصفح")
        ]
        if !guards.intersection(["shorts", "feeds", "games", "series", "chats"]).isEmpty {
            out.append(Rule(id: "apps", icon: "hourglass", en: "Close the apps that take your time", ar: "أغلق التطبيقات التي تأخذ وقتك",
                            whyEn: "You choose them in Screen Time; the rest of the phone stays", whyAr: "تختارها في «مدة استخدام الجهاز» ويبقى باقي الهاتف"))
        }
        if !hardest.intersection(["night", "home", "waking"]).isEmpty || guards.contains("feeds") {
            out.append(Rule(id: "quiet", icon: "crescent", en: "Quiet nights", ar: "الليالي الهادئة",
                            whyEn: "The apps you chose sleep from ʿIshāʾ to Fajr", whyAr: "تنام التطبيقات التي اخترتها من بعد العشاء إلى الفجر"))
        }
        if !hardest.intersection(["bored", "stressed", "sad"]).isEmpty {
            out.append(Rule(id: "pause", icon: "misbaha", en: "A pause before the apps you keep", ar: "وقفة قبل التطبيقات التي تُبقيها",
                            whyEn: "A moment to decide, instead of opening without thinking", whyAr: "لحظة تقرّر فيها، بدل أن تفتح دون أن تنتبه"))
        }
        return out
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Kicker(text: ia("What helps", "ما يُعين"))
            HisnTitle(text: name.isEmpty ? ia("A plan from your own answers", "خطة من إجاباتك أنت") : ia("\(name), a plan from your own answers", "يا \(name)، هذه خطة من إجاباتك أنت"), size: 24)
            HisnSub(text: ia("Zina sites are always closed. The rest we chose from your answers; you turn each on in the next step.",
                             "مواقع الزنا محجوبة دائمًا. والباقي اخترناه لك من إجاباتك، وتفعّل كل واحدة في الخطوة التالية."))
            ForEach(Self.rules(guards: guards, hardest: hardest)) { r in
                HStack(spacing: 10) {
                    HisnIcon(r.icon, 36)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(ia(r.en, r.ar)).font(.system(size: 15, weight: .bold)).foregroundColor(look.ink)
                        Text(ia(r.whyEn, r.whyAr)).font(.system(size: 13)).foregroundColor(look.sub).fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: "checkmark.circle.fill").foregroundColor(look.kept)
                }
                .padding(12).hisnCard(look)
            }
        }
    }
}

// MARK: Let Hisn stand guard (the real setup)

struct HisnPermsPage: View {
    @Environment(\.hisnLook) private var look
    @ObservedObject var protection: ProtectionController
    @State private var showingSetup = false

    static func missing(_ p: ProtectionController) -> Int { [p.safariConfigured, p.dns == .enabled].filter { !$0 }.count }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HisnTitle(text: ia("Let Hisn stand guard", "دع حصنًا يقف حارسًا"))
            HisnSub(text: ia("Hisn cannot protect you without the first two. The rest make it stronger.", "لا يستطيع حصن حمايتك بدون الأوّلَين، والباقي يزيده قوة."))
            VStack(spacing: 0) {
                row("safari", ia("Safari's filter", "مرشّح سفاري"), ia("Closes zina sites in Safari", "يغلق مواقع الزنا في سفاري"), required: true, on: protection.safariConfigured)
                Divider().overlay(look.line)
                row("globe", ia("Website filter", "مرشّح المواقع"), ia("The same list in every app", "القائمة نفسها في كل التطبيقات"), required: true, on: protection.dns == .enabled)
                Divider().overlay(look.line)
                row("hourglass", ia("Screen Time", "مدة استخدام الجهاز"), ia("Closes the apps you choose", "يغلق التطبيقات التي تختارها"), required: false, on: protection.screenTimeConfigured)
            }
            .hisnCard(look, radius: 18)
            HisnPrimaryButton(label: ia("Show me where each switch is", "أرني أين كل مفتاح"), white: false) { showingSetup = true }
        }
        .sheet(isPresented: $showingSetup, onDismiss: { Task { await protection.refresh() } }) {
            NavigationStack {
                BlockingLayersSetupView(protection: protection)
                    .navigationTitle(ia("Protection", "الحماية"))
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button(ia("Done", "تمّ")) { showingSetup = false } } }
            }
        }
        .task { await protection.refresh() }
    }

    private func row(_ symbol: String, _ title: String, _ body: String, required: Bool, on: Bool) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol).font(.system(size: 16, weight: .semibold)).foregroundColor(look.pri)
                .frame(width: 38, height: 38).background(Circle().fill(look.soft))
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(title).font(.system(size: 15, weight: .bold)).foregroundColor(look.ink)
                    if required {
                        Text(ia("Needed", "ضروري")).font(.system(size: 11, weight: .bold)).foregroundColor(look.acc)
                            .padding(.horizontal, 8).padding(.vertical, 2).background(Capsule().fill(look.soft))
                    }
                }
                Text(body).font(.system(size: 12.5)).foregroundColor(look.sub)
            }
            Spacer(minLength: 0)
            Image(systemName: on ? "checkmark.circle.fill" : "circle").font(.system(size: 22)).foregroundColor(on ? look.kept : look.line)
                .accessibilityLabel(on ? ia("On", "يعمل") : ia("Off", "متوقف"))
        }
        .padding(14)
    }
}

// MARK: Try the pause

struct HisnTryPage: View {
    @Environment(\.hisnLook) private var look
    @Binding var tried: Bool
    @State private var showing = false
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HisnTitle(text: ia("Try it before you trust it", "جرّبه قبل أن تثق به"))
            HisnSub(text: ia("This is the pause you will see before an app you chose.", "هذه هي الوقفة التي ستراها قبل التطبيق الذي اخترته."))
            Button { showing = true } label: {
                HStack(spacing: 12) {
                    HisnIcon("misbaha", 40)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(ia("Try the pause", "جرّب الوقفة")).font(.system(size: 15, weight: .bold)).foregroundColor(look.ink)
                        Text(tried ? ia("Seen. You can set it for any app in Apps.", "رأيتها. يمكنك ضبطها لأي تطبيق من صفحة التطبيقات.")
                             : ia("As it will appear to you", "كما ستظهر لك")).font(.system(size: 12.5)).foregroundColor(look.sub)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: tried ? "checkmark.circle.fill" : "play.circle.fill").font(.system(size: 24)).foregroundColor(tried ? look.kept : look.pri)
                }
                .padding(14).hisnCard(look)
            }
            .buttonStyle(.plain)
        }
        .fullScreenCover(isPresented: $showing) { PauseView(onClose: { showing = false; tried = true }) }
    }
}

// MARK: Reminders

enum HisnReminders {
    static var wanted: Bool { UserDefaults.standard.bool(forKey: "hisn.reminders") }

    /// Asks once, then keeps a week of reminders ahead: ten minutes before Fajr, and the reckoning at 21:30.
    static func enable() async -> Bool {
        let granted = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])) ?? false
        UserDefaults.standard.set(granted, forKey: "hisn.reminders")
        if granted { await reschedule() }
        return granted
    }

    static func reschedule() async {
        guard wanted else { return }
        let center = UNUserNotificationCenter.current()
        let ids = (0..<8).map { "hisn.fajr.\($0)" } + ["hisn.reckoning"]
        center.removePendingNotificationRequests(withIdentifiers: ids)
        for i in 0..<8 {
            guard let day = Calendar.current.date(byAdding: .day, value: i, to: Date()) else { continue }
            let fajr = HisnPrayers.times(on: day).five[0] - 10
            var parts = Calendar.current.dateComponents([.year, .month, .day], from: day)
            parts.hour = fajr / 60; parts.minute = fajr % 60
            guard let when = Calendar.current.date(from: parts), when > Date() else { continue }
            let content = UNMutableNotificationContent()
            content.title = ia("Fajr in 10 minutes", "الفجر بعد 10 دقائق")
            content.body = ia("Start the day with the One who keeps you.", "ابدأ يومك مع من يحفظك.")
            try? await center.add(UNNotificationRequest(identifier: "hisn.fajr.\(i)", content: content,
                                                        trigger: UNCalendarNotificationTrigger(dateMatching: parts, repeats: false)))
        }
        let content = UNMutableNotificationContent()
        content.title = ia("The evening reckoning", "محاسبة المساء")
        content.body = ia("One minute before you sleep.", "دقيقة واحدة قبل أن تنام.")
        try? await center.add(UNNotificationRequest(identifier: "hisn.reckoning", content: content,
                                                    trigger: UNCalendarNotificationTrigger(dateMatching: DateComponents(hour: 21, minute: 30), repeats: true)))
    }
}

struct HisnNotifyPage: View {
    @Environment(\.hisnLook) private var look
    var body: some View {
        VStack(spacing: 12) {
            Spacer(minLength: 20)
            ForEach([(ia("Fajr in 10 minutes", "الفجر بعد 10 دقائق"), ia("Start the day with the One who keeps you.", "ابدأ يومك مع من يحفظك."), ia("now", "الآن")),
                     (ia("The evening reckoning", "محاسبة المساء"), ia("One minute before you sleep.", "دقيقة واحدة قبل أن تنام."), "21:30")], id: \.0) { n in
                HStack(alignment: .top, spacing: 10) {
                    HisnGateMark().fill(.white, style: FillStyle(eoFill: true)).frame(width: 20, height: 20).padding(6)
                        .background(RoundedRectangle(cornerRadius: 8).fill(look.pri))
                    VStack(alignment: .leading, spacing: 2) {
                        HStack { Text(n.0).font(.system(size: 14, weight: .bold)); Spacer(); Text(n.2).font(.system(size: 12)).foregroundColor(look.sub) }
                        Text(n.1).font(.system(size: 13.5)).foregroundColor(look.ink)
                    }
                }
                .foregroundColor(look.ink).padding(12).background(RoundedRectangle(cornerRadius: 18).fill(look.card.opacity(0.92)))
            }
            Spacer(minLength: 20)
            HisnTitle(text: ia("Let us help you stay strong", "دعنا نُعينك على الثبات"), center: true)
            HisnSub(text: ia("A reminder before Fajr, and the reckoning in the evening. Twice a day, no more.", "تذكير قبل الفجر، ومحاسبة في المساء. مرتان في اليوم لا أكثر."), center: true)
        }
    }
}
