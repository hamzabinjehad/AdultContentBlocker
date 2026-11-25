import Foundation

/// The words of the Islamic edition's first run and help page. They are the same as Android's
/// `OnboardingHisn.kt`. Verses are never translated here: English lines are Pickthall's, as
/// corpus.quran.com carries them, and scholars are quoted in their own words with the page.
enum HisnContent {
    struct Opt: Identifiable { let key, en, ar: String; var id: String { key }; var label: String { ia(en, ar) } }
    struct IconOpt: Identifiable { let key, icon, en, ar: String; var id: String { key }; var label: String { ia(en, ar) } }
    struct Slide { let icon, en, ar, bodyEn, bodyAr, verse, src: String }
    struct Pledge { let en, ar, verse, meaning, src: String }
    struct Harm { let icon, en, ar, quote: String; let page: Int }
    struct Advice { let icon: String?; let en, ar, verse, meaning, refEn, refAr, bodyEn, bodyAr: String }

    static let chapters = [("You", "أنت"), ("Why it's hard", "لماذا يصعب"), ("What helps", "ما يُعين"), ("Your plan", "خطّتك")]

    static let guards = [
        Opt(key: "adult", en: "My gaze: adult content", ar: "بصري: محتوى الزنا"),
        Opt(key: "shorts", en: "My time: Shorts, Reels, feeds", ar: "وقتي: المقاطع القصيرة والتمرير"),
        Opt(key: "time", en: "My prayers: the phone at ṣalāh", ar: "صلاتي: الهاتف وقت الصلاة"),
        Opt(key: "feeds", en: "My sleep: the phone in bed before sleep", ar: "نومي: تصفّح الهاتف في السرير قبل النوم"),
        Opt(key: "games", en: "My hours: games", ar: "ساعاتي: الألعاب"),
        Opt(key: "chats", en: "My chats: talk I should not be in", ar: "محادثاتي: كلام لا يرضي الله"),
        Opt(key: "series", en: "My evenings: series and films", ar: "سهراتي: المسلسلات والأفلام")
    ]
    static let hardest = [
        Opt(key: "night", en: "Late at night, alone", ar: "في آخر الليل، وحدي"),
        Opt(key: "stressed", en: "After a hard day", ar: "بعد يوم متعب"),
        Opt(key: "bored", en: "When I'm bored", ar: "حين أشعر بالملل"),
        Opt(key: "waking", en: "When I wake up", ar: "عند الاستيقاظ من النوم"),
        Opt(key: "home", en: "Alone at home", ar: "حين أكون وحدي في البيت"),
        Opt(key: "sad", en: "After a quarrel or sadness", ar: "بعد خلاف أو حزن"),
        Opt(key: "travel", en: "When travelling or on holiday", ar: "في السفر أو الإجازة"),
        Opt(key: "unsure", en: "I'm not sure yet", ar: "لست متأكدًا بعد")
    ]
    static let prayers = [
        Opt(key: "all", en: "All five, mostly on time", ar: "الخمس، وغالبًا في وقتها"),
        Opt(key: "most", en: "Most of them", ar: "معظمها"),
        Opt(key: "few", en: "A few", ar: "بعضها"),
        Opt(key: "again", en: "I want to start again", ar: "أريد أن أبدأ من جديد")
    ]
    static let speech = [
        Opt(key: "m", en: "As a brother · أخي", ar: "بصيغة المذكّر · أخي"),
        Opt(key: "f", en: "As a sister · أختي", ar: "بصيغة المؤنّث · أختي"),
        Opt(key: "n", en: "I prefer not to say", ar: "أفضّل ألّا أذكر")
    ]
    static let ages = [
        Opt(key: "u18", en: "Under 18", ar: "أقل من 18"), Opt(key: "18to24", en: "18 to 24", ar: "من 18 إلى 24"),
        Opt(key: "25to34", en: "25 to 34", ar: "من 25 إلى 34"), Opt(key: "35to44", en: "35 to 44", ar: "من 35 إلى 44"),
        Opt(key: "45up", en: "45 or older", ar: "45 فأكثر")
    ]
    static let goals = [
        IconOpt(key: "pray", icon: "mosque", en: "Pray on time", ar: "الصلاة في وقتها"),
        IconOpt(key: "gaze", icon: "shield", en: "Lower my gaze", ar: "غضّ بصري"),
        IconOpt(key: "quran", icon: "quran", en: "Qur'an every day", ar: "القرآن كل يوم"),
        IconOpt(key: "near", icon: "hands", en: "Nearer to Allah", ar: "القرب من الله"),
        IconOpt(key: "marriage", icon: "rings", en: "A stronger marriage", ar: "زواج أقوى"),
        IconOpt(key: "family", icon: "home", en: "Better with family", ar: "علاقة أطيب مع الأهل"),
        IconOpt(key: "focus", icon: "hourglass", en: "Focus", ar: "التركيز"),
        IconOpt(key: "energy", icon: "sun", en: "More energy", ar: "طاقة أكبر"),
        IconOpt(key: "sleep", icon: "crescent", en: "Sleep before midnight", ar: "النوم قبل منتصف الليل"),
        IconOpt(key: "fast", icon: "dates", en: "Fast Mondays and Thursdays", ar: "صيام الاثنين والخميس")
    ]
    static let instead = [
        IconOpt(key: "wudu", icon: "ibrik", en: "Make wuḍūʾ", ar: "الوضوء"),
        IconOpt(key: "quran", icon: "quran", en: "A page of Qur'an", ar: "صفحة من القرآن"),
        IconOpt(key: "masjid", icon: "mosque", en: "Walk to the masjid", ar: "المشي إلى المسجد"),
        IconOpt(key: "parents", icon: "hands", en: "Call my parents", ar: "الاتصال بوالديّ"),
        IconOpt(key: "rakah", icon: "rug", en: "Two rakʿahs", ar: "ركعتان"),
        IconOpt(key: "family", icon: "dates", en: "Eat with family", ar: "الطعام مع الأهل"),
        IconOpt(key: "outside", icon: "olive", en: "Sit outside", ar: "الجلوس في الخارج"),
        IconOpt(key: "sleep", icon: "crescent", en: "Sleep early", ar: "النوم مبكرًا")
    ]
    static let reasons = [
        ("Out of fear of Allah", "خوفًا من الله"), ("Out of love for Allah", "حبًّا لله"),
        ("Hoping for His mercy and Paradise", "رجاءً في رحمته وجنّته"), ("Shy before Him who sees me", "حياءً ممّن يراني")
    ]

    static let slides = [
        Slide(icon: "lantern", en: "It never starts with the worst step", ar: "لا يبدأ الأمر بأسوأ خطوة",
              bodyEn: "A feed, then a search, then a page. Hisn closes the first doors.",
              bodyAr: "مقطع، ثم بحث، ثم صفحة. وحصن يغلق الأبواب الأولى.",
              verse: "لَا تَتَّبِعُوا۟ خُطُوَٰتِ ٱلشَّيْطَٰنِ", src: "24:21"),
        Slide(icon: "shield", en: "The gaze comes first", ar: "النظر أولًا",
              bodyEn: "The command to lower the gaze comes before the command to guard yourself. What you don't look at, you don't chase.",
              bodyAr: "جاء الأمر بغضّ البصر قبل الأمر بحفظ الفرج. وما لا تنظر إليه لا تلهث وراءه.",
              verse: "يَغُضُّوا۟ مِنْ أَبْصَٰرِهِمْ وَيَحْفَظُوا۟ فُرُوجَهُمْ", src: "24:30"),
        Slide(icon: "hands", en: "The door is never closed", ar: "الباب لا يُغلق أبدًا",
              bodyEn: "However many times it happened, Allah forgives all sins for the one who turns back. That is where Hisn starts.",
              bodyAr: "مهما تكرّر الأمر، فإن الله يغفر الذنوب جميعًا لمن رجع إليه. ومن هنا يبدأ حصن.",
              verse: "لَا تَقْنَطُوا۟ مِن رَّحْمَةِ ٱللَّهِ", src: "39:53")
    ]

    static let pledges = [
        Pledge(en: "Will you seek Allah's help, and not only your own strength?", ar: "هل تستعين بالله ولا تتّكل على نفسك؟",
               verse: "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ", meaning: "Thee (alone) we worship; Thee (alone) we ask for help.", src: "1:5"),
        Pledge(en: "Will you guard your eyes and your heart from what leads to it?", ar: "هل تحفظ عينيك وقلبك مما يقود إليه؟",
               verse: "قُل لِّلْمُؤْمِنِينَ يَغُضُّوا۟ مِنْ أَبْصَٰرِهِمْ", meaning: "Tell the believing men to lower their gaze.", src: "24:30"),
        Pledge(en: "Will you stop at the first step, not the last?", ar: "هل تتوقّف عند الخطوة الأولى، لا الأخيرة؟",
               verse: "لَا تَتَّبِعُوا۟ خُطُوَٰتِ ٱلشَّيْطَٰنِ", meaning: "Follow not the footsteps of the devil.", src: "24:21"),
        Pledge(en: "Do you believe Allah will make a way out for you?", ar: "هل توقن أن الله سيجعل لك مخرجًا؟",
               verse: "وَمَن يَتَّقِ ٱللَّهَ يَجْعَل لَّهُۥ مَخْرَجًا", meaning: "Whosoever keepeth his duty to Allah, Allah will appoint a way out for him.", src: "65:2"),
        Pledge(en: "If you fall, will you remember Allah and come back?", ar: "إن سقطت، هل تذكر الله وتعود؟",
               verse: "ذَكَرُوا۟ ٱللَّهَ فَٱسْتَغْفَرُوا۟ لِذُنُوبِهِمْ", meaning: "…remember Allah and implore forgiveness for their sins.", src: "3:135")
    ]
    static let promises = [
        ("I seek Allah's help, not only my own strength", "أستعين بالله لا بقوّتي وحدي"),
        ("I will guard my eyes and my heart", "سأحفظ بصري وقلبي"),
        ("I will stop at the first step", "سأتوقّف عند الخطوة الأولى"),
        ("If I fall, I will come back", "إن سقطتُ فسأرجع")
    ]

    static let surahNames = ["1": "الفاتحة", "2": "البقرة", "3": "آل عمران", "7": "الأعراف", "17": "الإسراء", "24": "النور",
                             "29": "العنكبوت", "39": "الزمر", "65": "الطلاق"]

    // Ibn al-Qayyim, ad-Dāʾ wa ad-Dawāʾ (ʿAṭāʾāt al-ʿIlm edition), in his own words with the page.
    static let harmLead = "أن الذنوب تضر ولابد، وأن ضررها في القلوب؛ كضرر السموم في الأبدان، على اختلاف درجاتها في الضرر."
    static let harmLeadPage = 98
    static let harms = [
        Harm(icon: "heart_dark", en: "It puts out the light of the heart", ar: "تطفئ نور القلب", quote: "العلم نور يقذفه الله في القلب، والمعصية تطفئ ذلك النور.", page: 132),
        Harm(icon: "heart_cage", en: "It leaves a loneliness in the heart", ar: "تورث وحشة في القلب", quote: "وليس على القلب أمَرُّ من وحشة الذنب على الذنب", page: 134),
        Harm(icon: "heart_sealed", en: "It rusts the heart until it is sealed", ar: "تُصدئ القلب حتى يُختم عليه",
             quote: "أن القلب يصدأ من المعصية، فإن زادت غلب الصدأ حتى يصير رانًا، ثم يغلب حتى يصير طبعًا وقفلًا وختمًا، فيصير القلب في غشاوة وغلاف.", page: 149),
        Harm(icon: "heart_chain", en: "One sin calls the next, until it is a habit", ar: "تدعو أختها حتى تصير عادة",
             quote: "وكذلك جانب السيئات أيضًا، حتى تصير الطاعات والمعاصي هيئات راسخة وصفات لازمة وملكات ثابتة.", page: 139),
        Harm(icon: "heart_dry", en: "It takes the blessing out of life and provision", ar: "تمحق بركة العمر والرزق",
             quote: "ومن عقوبات المعاصي: أنها تمحق بركة العمر، وبركة الرزق، وبركة العلم، وبركة العمل، وبركة الطاعة.", page: 199),
        Harm(icon: "heart_dust", en: "It grows small in your eyes", ar: "تهون في عينك",
             quote: "العبد لا يزال يرتكب الذنب، حتى يهون عليه، ويصغر في قلبه. وذلك علامة الهلاك", page: 58)
    ]

    // al-Saʿdī's tafsīr of al-Isrāʾ 32, as quran.com carries it.
    static let saadi = "والنهي عن قربانه أبلغ من النهي عن مجرد فعله لأن ذلك يشمل النهي عن جميع مقدماته ودواعيه"

    /// What Islam advises for the hard minute, before anything of ours.
    static let advice = [
        Advice(icon: "ibrik", en: "Make wuḍūʾ and pray two rakʿahs", ar: "توضّأ وصلِّ ركعتين",
               verse: "إِنَّ ٱلصَّلَوٰةَ تَنْهَىٰ عَنِ ٱلْفَحْشَآءِ وَٱلْمُنكَرِ", meaning: "Lo! worship preserveth from lewdness and iniquity.",
               refEn: "al-ʿAnkabūt 45", refAr: "سورة العنكبوت، الآية 45", bodyEn: "Get up from where you are now.", bodyAr: "قم من مكانك الآن."),
        Advice(icon: nil, en: "Seek refuge in Allah and change your place", ar: "استعذ بالله وغيّر مكانك",
               verse: "وَإِمَّا يَنزَغَنَّكَ مِنَ ٱلشَّيْطَٰنِ نَزْغٌ فَٱسْتَعِذْ بِٱللَّهِ", meaning: "And if a slander from the devil wound thee, then seek refuge in Allah.",
               refEn: "al-Aʿrāf 200", refAr: "سورة الأعراف، الآية 200",
               bodyEn: "Say: aʿūdhu billāhi mina sh-shayṭāni r-rajīm. Leave the room, and do not stay alone.",
               bodyAr: "قل: أعوذ بالله من الشيطان الرجيم، واخرج من الغرفة، ولا تبقَ وحدك."),
        Advice(icon: nil, en: "This is a test, and Allah sees your patience now", ar: "هذا ابتلاء، والله يرى صبرك الآن",
               verse: "أَحَسِبَ ٱلنَّاسُ أَن يُتْرَكُوٓا۟ أَن يَقُولُوٓا۟ ءَامَنَّا وَهُمْ لَا يُفْتَنُونَ",
               meaning: "Do men imagine that they will be left (at ease) because they say, We believe, and will not be tested with affliction?",
               refEn: "al-ʿAnkabūt 2", refAr: "سورة العنكبوت، الآية 2", bodyEn: "Be patient for this one minute, for Allah.", bodyAr: "اصبر هذه الدقيقة لله.")
    ]

    /// What the person said, read back with what they get from it.
    static func told(hardest: Set<String>, prayers: String, guards: Set<String>) -> [(symbol: String, title: String, body: String)] {
        var rows: [(String, String, String)] = []
        let main = ["night", "stressed", "bored", "waking", "home", "sad", "travel"].first { hardest.contains($0) }
        switch main {
        case "night": rows.append(("moon.fill", ia("Nights are hardest", "الليل أصعب أوقاتك"),
                                   ia("Quiet hours close the feeds from ʿIshāʾ to Fajr, so the night is for sleep", "الساعات الهادئة تُغلق المقاطع من بعد العشاء إلى الفجر، فيبقى ليلك للنوم")))
        case "stressed": rows.append(("heart.fill", ia("Hard days pull you", "اليوم المتعب يضعفك"),
                                      ia("«Help now» is the red button: wuḍūʾ, dhikr, and the reason you wrote", "«ساعدني الآن» هو الزر الأحمر: وضوء وذكر والسبب الذي كتبته أنت")))
        case "bored": rows.append(("pause.circle.fill", ia("Boredom opens the feed", "الملل يفتح المقاطع"),
                                   ia("A pause before the apps you choose, so you open them on purpose", "وقفة قبل التطبيقات التي تختارها، فلا تفتحها إلا عن قصد")))
        case "waking": rows.append(("sunrise.fill", ia("Waking up is your weak hour", "أول الصباح وقت ضعفك"),
                                    ia("Begin the day with the morning adhkār before any feed", "ابدأ يومك بأذكار الصباح قبل أي مقطع")))
        case "home": rows.append(("shield.fill", ia("Alone at home is hardest", "الخلوة في البيت أصعب أوقاتك"),
                                  ia("The shield does not need anyone watching: it stays closed when you are alone", "الدرع لا يحتاج من يراقبك، فيبقى مغلقًا وأنت وحدك")))
        case "sad": rows.append(("heart.fill", ia("Sadness pulls you", "الحزن يضعفك"),
                                 ia("«Help now» gives you wuḍūʾ and two rakʿahs before the screen", "«ساعدني الآن» يذكّرك بالوضوء وركعتين قبل الشاشة")))
        case "travel": rows.append(("globe", ia("Travel loosens your guard", "السفر يُضعف حراستك"),
                                    ia("The lock keeps what you closed shut, wherever you are", "القفل يُبقي ما أغلقته مغلقًا أينما كنت")))
        default: break
        }
        switch prayers {
        case "all": rows.append(("clock.fill", ia("Your prayers are steady", "صلواتك ثابتة"), ia("Keep them first: the phone waits, the prayer does not", "أبقِها أولًا: الهاتف ينتظر، والصلاة لا تنتظر")))
        case "most", "few": rows.append(("clock.fill", ia("Most prayers, not all", "معظم الصلوات، لا كلها"), ia("One more prayer on time this week is the first step", "صلاة واحدة إضافية في وقتها هذا الأسبوع هي الخطوة الأولى")))
        case "again": rows.append(("clock.fill", ia("You want to start again", "تريد أن تبدأ من جديد"), ia("One prayer at a time, starting with the next one", "صلاة بعد صلاة، وابدأ بالصلاة القادمة")))
        default: break
        }
        if guards.contains("adult") || guards.isEmpty {
            rows.append(("shield.fill", ia("Your gaze", "بصرك"), ia("Zina sites are closed in Safari and in every app before you see them", "تُغلق مواقع الزنا في سفاري وفي كل تطبيق قبل أن تراها")))
        }
        if guards.contains("shorts") || guards.contains("feeds") {
            rows.append(("timer", ia("Your time", "وقتك"), ia("The apps you choose are closed with Screen Time", "تُغلق التطبيقات التي تختارها عبر «مدة استخدام الجهاز»")))
        }
        if guards.contains("games") || guards.contains("series") {
            rows.append(("timer", ia("Your hours", "ساعاتك"), ia("Close the games and shows you choose, from Apps", "أغلق الألعاب والمسلسلات التي تختارها، من صفحة التطبيقات")))
        }
        rows.append(("heart.fill", ia("You may feel far from Allah after", "قد تشعر بالبعد عن الله بعدها"),
                     ia("The door back is always near: no reset, no shame", "باب العودة قريب منك دائمًا: لا تصفير ولا تعيير")))
        return rows.map { (symbol: $0.0, title: $0.1, body: $0.2) }
    }
}
