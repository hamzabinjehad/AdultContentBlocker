package app.hisn.android.nativeshell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisn.android.PauseEntry
import app.hisn.core.PrayerPlaces
import app.hisn.core.Sayings
import java.time.LocalDate

/** Routes under "hisn:", opened from the Islamic edition's screens. */
@Composable
internal fun IslamicDetail(detail: String, state: HisnState, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    when (detail) {
        "hisn:help" -> HelpNow(state, onBack, onNavigate)
        "hisn:slip" -> ComeBack(state, onBack)
        "hisn:calendar" -> IslamicCalendar(state, onBack)
        "hisn:adhkar" -> Adhkar(onBack)
        "hisn:read" -> TodaysReading(onBack)
        "hisn:reflect" -> Reflection(onBack, onNavigate)
        "hisn:city" -> CityPicker(onBack)
        "hisn:shield" -> ShieldPage(state, onBack, onNavigate)
        "hisn:settings" -> HisnSettingsPage(state, onBack, onNavigate)
        "hisn:moresettings" -> HisnMoreSettings(state, onBack, onNavigate)
        else -> IslamicPage(ia("Not found", "غير موجود"), onBack) {}
    }
}

/** A plain Islamic detail page: back, title, scrolling content on the look's sky. */
@Composable
internal fun IslamicPage(title: String, onBack: () -> Unit, background: Brush? = null, close: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val look = LocalLook.current
    Column(
        Modifier.fillMaxSize().background(background ?: Brush.linearGradient(listOf(look.t.sky, look.t.sky)))
            .statusBarsPadding().navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(if (close) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack, ia("Back", "رجوع"), tint = look.t.ink)
            }
            Text(title, color = look.t.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp), content = content)
    }
}

@Composable
internal fun PrimaryButton(label: String, modifier: Modifier = Modifier, color: Color? = null, onClick: () -> Unit) {
    val look = LocalLook.current
    Text(
        label, color = if (color == null) look.t.priInk else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(color ?: look.t.pri)
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 15.dp)
    )
}

@Composable
private fun QuranLine(text: String, ref: String, size: Int = 24) {
    val look = LocalLook.current
    // The verse in the Naskh hand between its marks, then where it is from.
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(androidx.compose.ui.text.SpanStyle(color = look.t.acc)); append("﴿ "); pop()
            append(text)
            pushStyle(androidx.compose.ui.text.SpanStyle(color = look.t.acc)); append(" ﴾"); pop()
        },
        color = look.t.helpInk, fontSize = size.sp, lineHeight = (size * 1.75f).sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
        textAlign = TextAlign.Center, style = TextStyle(textDirection = TextDirection.Rtl), modifier = Modifier.fillMaxWidth()
    )
    Text(ref, color = look.t.helpInk.copy(alpha = 0.75f), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

/** One piece of advice on the help page: what to do, the verse it stands on, and the plain step. */
@Composable
private fun AdviceCard(icon: String?, title: String, verse: String?, ref: String?, body: String) {
    val look = LocalLook.current
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = if (look.dark) 0.08f else 0.7f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { HisnIcon(icon, 34.dp); Spacer(Modifier.width(10.dp)) }
            Text(title, color = look.t.helpInk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        if (verse != null && ref != null) QuranLine(verse, ref, 19)
        Text(body, color = look.t.helpInk.copy(alpha = 0.85f), fontSize = 13.5.sp, lineHeight = 20.sp)
    }
}

// ---------------- Help now ----------------

private val PROMISES = listOf(
    "I seek Allah's help, not only my own strength" to "أستعين بالله لا بقوّتي وحدي",
    "I will guard my eyes and my heart" to "سأحفظ بصري وقلبي",
    "I will stop at the first step" to "سأتوقّف عند الخطوة الأولى",
    "If I fall, I will come back" to "إن سقطتُ فسأرجع"
)

@Composable
private fun HelpNow(state: HisnState, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    val sayings by rememberSayings()
    var n by rememberSaveable { mutableIntStateOf(0) }
    val pool = remember(sayings) { sayings?.moment(Sayings.Moment.TEMPTED)?.filter { it.text.length <= 300 } ?: emptyList() }
    IslamicPage(ia("Help now", "ساعدني الآن"), onBack, Brush.verticalGradient(listOf(look.t.helpTop, look.t.helpBottom)), close = true) {
        HisnIcon("phonedown", 74.dp, Modifier.align(Alignment.CenterHorizontally))
        Text(ia("You made a promise to Allah, and He is near", "عاهدتَ الله، وهو قريب"), color = look.t.helpInk, fontSize = 26.sp, lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        QuranLine("فَإِنِّى قَرِيبٌ", ia("“Then surely I am nigh.” (Pickthall) · al-Baqara 186", "سورة البقرة، الآية 186"))
        // The line the person wrote for this minute during setup comes first.
        val mine = state.personal.reason.trim()
        if (mine.isNotEmpty()) {
            Text(ia("“$mine”", rtlLine("«$mine»")), color = look.t.helpInk, fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = if (look.dark) 0.08f else 0.55f)).padding(14.dp))
        }
        // What Islam advises for this minute comes before anything of ours.
        AdviceCard("ibrik", ia("Make wuḍūʾ and pray two rakʿahs", "توضّأ وصلِّ ركعتين"),
            "إِنَّ ٱلصَّلَوٰةَ تَنْهَىٰ عَنِ ٱلْفَحْشَآءِ وَٱلْمُنكَرِ", ia("“Lo! worship preserveth from lewdness and iniquity.” (Pickthall) · al-ʿAnkabūt 45", "سورة العنكبوت، الآية 45"),
            ia("Get up from where you are now.", "قم من مكانك الآن."))
        AdviceCard(null, ia("Seek refuge in Allah and change your place", "استعذ بالله وغيّر مكانك"),
            "وَإِمَّا يَنزَغَنَّكَ مِنَ ٱلشَّيْطَٰنِ نَزْغٌ فَٱسْتَعِذْ بِٱللَّهِ", ia("“And if a slander from the devil wound thee, then seek refuge in Allah.” (Pickthall) · al-Aʿrāf 200", "سورة الأعراف، الآية 200"),
            ia("Say: aʿūdhu billāhi mina sh-shayṭāni r-rajīm. Leave the room, and do not stay alone.", "قل: أعوذ بالله من الشيطان الرجيم، واخرج من الغرفة، ولا تبقَ وحدك."))
        AdviceCard(null, ia("This is a test, and Allah sees your patience now", "هذا ابتلاء، والله يرى صبرك الآن"),
            "أَحَسِبَ ٱلنَّاسُ أَن يُتْرَكُوٓا۟ أَن يَقُولُوٓا۟ ءَامَنَّا وَهُمْ لَا يُفْتَنُونَ", ia("“Do men imagine that they will be left (at ease) because they say, We believe, and will not be tested with affliction?” (Pickthall) · al-ʿAnkabūt 2", "سورة العنكبوت، الآية 2"),
            ia("Be patient for this one minute, for Allah.", "اصبر هذه الدقيقة لله."))
        Spacer(Modifier.height(12.dp))
        Text(ia("What you promised:", "ما عاهدتَ عليه:"), color = look.t.helpInk.copy(alpha = 0.8f), fontSize = 13.sp)
        PROMISES.forEach { (en, ar) ->
            Row(Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = if (look.dark) 0.08f else 0.7f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, null, tint = look.t.kept, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(ia(en, ar), color = look.t.helpInk, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (pool.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(look.t.card).padding(14.dp)) {
                SayingBody(pool[n % pool.size], flat = true)
            }
            Text(ia("Another word", "كلمة أخرى"), color = look.t.helpInk, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.4f)).clickable { n++ }.padding(horizontal = 12.dp, vertical = 6.dp))
        }
        // What the person chose to do instead, during setup.
        val instead = remember {
            val keys = context.getSharedPreferences("hisn-native", android.content.Context.MODE_PRIVATE).getString("onboard.instead", "wudu,quran,rakah").orEmpty().split(',')
            InsteadOpts.filter { it.key in keys }
        }
        if (instead.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(ia("Then get up and do one of yours:", "ثم قم وافعل واحدًا مما اخترت:"), color = look.t.helpInk.copy(alpha = 0.8f), fontSize = 13.sp)
            instead.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { o ->
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = if (look.dark) 0.08f else 0.7f)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            HisnIcon(o.icon, 30.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(ia(o.en, o.ar), color = look.t.helpInk, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(ia("Breathe with dhikr · 30 seconds", "تنفّس مع الذكر، 30 ثانية"), color = look.t.help) {
            context.startActivity(PauseEntry.intent(context))
        }
        Spacer(Modifier.height(10.dp))
        Text(ia("I slipped", "زلَلت"), color = look.t.helpInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).border(1.5.dp, look.t.helpInk.copy(alpha = 0.4f), RoundedCornerShape(26.dp))
                .clickable { onNavigate("hisn:slip") }.padding(vertical = 14.dp))
    }
}

@Composable
private fun ComeBack(state: HisnState, onBack: () -> Unit) {
    val look = LocalLook.current
    val sayings by rememberSayings()
    val pick = remember(sayings) { sayings?.moment(Sayings.Moment.AFTER_SLIP)?.firstOrNull { it.text.length <= 260 } }
    IslamicPage(ia("Come back", "ارجع"), onBack, Brush.verticalGradient(listOf(look.t.helpTop, look.t.helpBottom)), close = true) {
        Text(ia("The door is still open", "الباب ما زال مفتوحًا"), color = look.t.helpInk, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        QuranLine("لَا تَقْنَطُوا۟ مِن رَّحْمَةِ ٱللَّهِ ۚ إِنَّ ٱللَّهَ يَغْفِرُ ٱلذُّنُوبَ جَمِيعًا", ia("“Despair not of the mercy of Allah, Who forgiveth all sins.” (Pickthall) · az-Zumar 53", "سورة الزمر، الآية 53"))
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = if (look.dark) 0.08f else 0.7f)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(ia("Nights of light, kept", "ليالٍ من النور، محفوظة"), color = look.t.helpInk.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            // A lone Arabic 0 reads as a dot, so a first night is said in words.
            val kept = nightsOf(state)
            if (kept > 0) Text(nums(kept), color = look.t.helpInk, fontSize = 48.sp)
            else Text(ia("Your first night starts tonight", "أولى لياليك تبدأ الليلة"), color = look.t.helpInk, fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
            Text(ia("A slip does not erase them. Nothing resets to zero.", "الزلّة لا تمحوها، ولا شيء يعود إلى الصفر."), color = look.t.helpInk, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = if (look.dark) 0.08f else 0.7f)).padding(16.dp)) {
            Text(ia("Three steps back", "ثلاث خطوات للعودة"), color = look.t.helpInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            listOf(ia("Regret it in your heart", "الندم بالقلب"), ia("Stop now", "الإقلاع الآن"), ia("Resolve not to return", "العزم على ألّا تعود")).forEachIndexed { i, t ->
                Text(nums("${i + 1}") + ia(". ", "- ") + t, color = look.t.helpInk, fontSize = 14.5.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
            Text(ia("Then wuḍūʾ and two rakʿahs (Abū Dāwūd 1521).", "ثم وضوء وركعتان (رواه أبو داود 1521)."), color = look.t.helpInk.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (pick != null) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(look.t.card).padding(14.dp)) { SayingBody(pick, flat = true) }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(ia("I'm coming back", "أعود الآن"), onClick = onBack)
    }
}

// ---------------- Adhkar ----------------

/** One dhikr as the book has it. [note] is what the book adds beside it (the evening wording, when to say it); [whenSaid] is m, e or b for both. */
private data class Dhikr(val ar: String, val en: String, val count: Int, val src: String, val note: String = "", val whenSaid: Char = 'b')

// The whole morning-and-evening chapter of Ḥiṣn al-Muslim (Saʿīd al-Qaḥṭānī), word for word as hisnmuslim.com carries it,
// with the book's number. Nothing here is our wording except the short English name.
private val HISN_ADHKAR = listOf(
    Dhikr("أَعُوذُ بِاللَّهِ مِنَ الشَّيطَانِ الرَّجِيمِ ﴿اللَّهُ لاَ إِلَهَ إِلاَّ هُوَ الْحَيُّ الْقَيُّومُ لاَ تَأْخُذُهُ سِنَةٌ وَلاَ نَوْمٌ لَّهُ مَا فِي السَّمَوَاتِ وَمَا فِي الأَرْضِ مَن ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلاَّ بِإِذْنِهِ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ وَلاَ يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلاَّ بِمَا شَاء وَسِعَ كُرْسِيُّهُ السَّمَوَاتِ وَالْأَرْضَ وَلاَ يَؤُودُهُ حِفْظُهُمَا وَهُوَ الْعَلِيُّ الْعَظِيمُ﴾.", "Āyat al-Kursī", 1, "حصن المسلم، رقم 75", "", 'b'),
    Dhikr("بسم الله الرحمن الرحيم ﴿قُلْ هُوَ اللَّهُ أَحَدٌ* اللَّهُ الصَّمَدُ* لَمْ يَلِدْ وَلَمْ يُولَدْ* وَلَمْ يَكُن لَّهُ كُفُواً أَحَدٌ﴾. بسم الله الرحمن الرحيم ﴿قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ* مِن شَرِّ مَا خَلَقَ* وَمِن شَرِّ غَاسِقٍ إِذَا وَقَبَ* وَمِن شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ* وَمِن شَرِّ حَاسِدٍ إِذَا حَسَدَ﴾. بسم الله الرحمن الرحيم ﴿قُلْ أَعُوذُ بِرَبِّ النَّاسِ* مَلِكِ النَّاسِ* إِلَهِ النَّاسِ* مِن شَرِّ الْوَسْوَاسِ الْخَنَّاسِ* الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ* مِنَ الْجِنَّةِ وَ النَّاسِ﴾.", "al-Ikhlāṣ, al-Falaq, an-Nās", 3, "حصن المسلم، رقم 76", "", 'b'),
    Dhikr("أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لاَ إِلَهَ إلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذَا الْيَوْمِ وَخَيرَ مَا بَعْدَهُ، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذَا الْيَوْمِ وَشَرِّ مَا بَعْدَهُ، رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ.", "We have reached the morning", 1, "حصن المسلم، رقم 77", "وإذا أمسى قال: أمسينا وأمسى الملك للَّه. وإذا أمسى قال: رب أسألك خير ما في هذه الليلة، وخير ما بعدها، وأعوذ بك من شر ما في هذه الليلة، وشر ما بعدها.", 'b'),
    Dhikr("اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ وَإِلَيْكَ النُّشُورُ.", "By You we enter the morning", 1, "حصن المسلم، رقم 78", "وإذا أمسى قال: اللَّهم بك أمسينا، وبك أصبحنا، وبك نحيا، وبك نموت، وإليك المصير.", 'b'),
    Dhikr("اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلاَّ أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لاَ يَغْفِرُ الذُّنوبَ إِلاَّ أَنْتَ.", "The master of seeking forgiveness", 1, "حصن المسلم، رقم 79", "", 'b'),
    Dhikr("اللَّهُمَّ إِنِّي أَصْبَحْتُ أُشْهِدُكَ، وَأُشْهِدُ حَمَلَةَ عَرْشِكَ، وَمَلاَئِكَتِكَ، وَجَمِيعَ خَلْقِكَ، أَنَّكَ أَنْتَ اللَّهُ لَا إِلَهَ إِلاَّ أَنْتَ وَحْدَكَ لاَ شَرِيكَ لَكَ، وَأَنَّ مُحَمَّداً عَبْدُكَ وَرَسُولُكَ.", "I call You to witness", 4, "حصن المسلم، رقم 80", "وإذا أمسى قال: اللَّهم إني أمسيت.", 'b'),
    Dhikr("اللَّهُمَّ مَا أَصْبَحَ بِي مِنْ نِعْمَةٍ أَوْ بِأَحَدٍ مِنْ خَلْقِكَ فَمِنْكَ وَحْدَكَ لاَ شَرِيكَ لَكَ، فَلَكَ الْحَمْدُ وَلَكَ الشُّكْرُ.", "Every blessing is from You", 1, "حصن المسلم، رقم 81", "وإذا أمسى قال: اللَّهم ما أمسى بي.", 'b'),
    Dhikr("اللَّهُمَّ عَافِنِي فِي بَدَنِي، اللَّهُمَّ عَافِنِي فِي سَمْعِي، اللَّهُمَّ عَافِنِي فِي بَصَرِي، لاَ إِلَهَ إِلاَّ أَنْتَ. اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْكُفْرِ، وَالفَقْرِ، وَأَعُوذُ بِكَ مِنْ عَذَابِ القَبْرِ، لاَ إِلَهَ إِلاَّ أَنْتَ.", "Health in body, hearing and sight", 3, "حصن المسلم، رقم 82", "", 'b'),
    Dhikr("حَسْبِيَ اللَّهُ لاَ إِلَهَ إِلاَّ هُوَ عَلَيهِ تَوَكَّلتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ.", "Allah is enough for me", 7, "حصن المسلم، رقم 83", "", 'b'),
    Dhikr("اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي الدُّنْيَا وَالآخِرَةِ، اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ: فِي دِينِي وَدُنْيَايَ وَأَهْلِي، وَمَالِي، اللَّهُمَّ اسْتُرْ عَوْرَاتِي، وَآمِنْ رَوْعَاتِي، اللَّهُمَّ احْفَظْنِي مِنْ بَينِ يَدَيَّ، وَمِنْ خَلْفِي، وَعَنْ يَمِينِي، وَعَنْ شِمَالِي، وَمِنْ فَوْقِي، وَأَعُوذُ بِعَظَمَتِكَ أَنْ أُغْتَالَ مِنْ تَحْتِي.", "Pardon and well-being", 1, "حصن المسلم، رقم 84", "", 'b'),
    Dhikr("اللَّهُمَّ عَالِمَ الغَيْبِ وَالشَّهَادَةِ فَاطِرَ السَّمَوَاتِ وَالْأَرْضِ، رَبَّ كُلِّ شَيْءٍ وَمَلِيكَهُ، أَشْهَدُ أَنْ لاَ إِلَهَ إِلاَّ أَنْتَ، أَعُوذُ بِكَ مِنْ شَرِّ نَفْسِي، وَمِنْ شَرِّ الشَّيْطانِ وَشَرَكِهِ، وَأَنْ أَقْتَرِفَ عَلَى نَفْسِي سُوءاً، أَوْ أَجُرَّهُ إِلَى مُسْلِمٍ.", "Knower of the unseen and the seen", 1, "حصن المسلم، رقم 85", "", 'b'),
    Dhikr("بِسْمِ اللَّهِ الَّذِي لاَ يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلاَ فِي السّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ.", "In the name of Allah", 3, "حصن المسلم، رقم 86", "", 'b'),
    Dhikr("رَضِيتُ بِاللَّهِ رَبَّاً، وَبِالْإِسْلاَمِ دِيناً، وَبِمُحَمَّدٍ صلى الله عليه وسلم نَبِيّاً.", "I am pleased with Allah as Lord", 3, "حصن المسلم، رقم 87", "", 'b'),
    Dhikr("يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغيثُ أَصْلِحْ لِي شَأْنِيَ كُلَّهُ وَلاَ تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ.", "O Ever-Living, O Sustainer", 1, "حصن المسلم، رقم 88", "", 'b'),
    Dhikr("أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ رَبِّ الْعَالَمِينَ، اللَّهُـمَّ إِنِّي أَسْأَلُكَ خَيْرَ هَذَا الْيَوْمِ:فَتْحَهُ، وَنَصْرَهُ، وَنورَهُ، وَبَرَكَتَهُ، وَهُدَاهُ، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِيهِ وَشَرِّ مَا بَعْدَهُ.", "The good of this day", 1, "حصن المسلم، رقم 89", "وإذا أمسى قال: أمسينا وأمسى الملك للَّه ربّ العالمين اللَّهم إني أسألك خير هذه الليلة: فتحها، ونصرها، ونورها، وبركتها، وهداها، وأعوذ بك من شر ما فيها، وشر ما بعدها.", 'b'),
    Dhikr("أَصْبَحْنا عَلَى فِطْرَةِ الْإِسْلاَمِ، وَعَلَى كَلِمَةِ الْإِخْلاَصِ، وَعَلَى دِينِ نَبِيِّنَا مُحَمَّدٍ صلى الله عليه وسلم، وَعَلَى مِلَّةِ أَبِينَا إِبْرَاهِيمَ، حَنِيفاً مُسْلِماً وَمَا كَانَ مِنَ الْمُشرِكِينَ.", "Upon the fiṭra of Islam", 1, "حصن المسلم، رقم 90", "وإذا أمسى قال: أمسينا على فطرة الإسلام.", 'b'),
    Dhikr("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ.", "Glory and praise, 100", 100, "حصن المسلم، رقم 91", "مائة مرَّةٍ.", 'b'),
    Dhikr("لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ، وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ.", "Lā ilāha illa Allāh, 10", 10, "حصن المسلم، رقم 92", "عشرَ مرَّات، أو مرَّةً واحدةً عندَ الكَسَلِ.", 'b'),
    Dhikr("لاَ إِلَهَ إِلاَّ اللَّهُ، وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ.", "Lā ilāha illa Allāh, 100", 100, "حصن المسلم، رقم 93", "مائةَ مرَّةٍ إذا أصبحَ.", 'm'),
    Dhikr("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ: عَدَدَ خَلْقِهِ، وَرِضَا نَفْسِهِ، وَزِنَةَ عَرْشِهِ، وَمِدَادَ كَلِمَاتِهِ.", "Glory by the number of His creation", 3, "حصن المسلم، رقم 94", "ثلاثَ مرَّاتٍ إذا أصبحَ.", 'm'),
    Dhikr("اللَّهُمَّ إِنِّي أَسْأَلُكَ عِلْماً نَافِعاً، وَرِزْقاً طَيِّباً، وَعَمَلاً مُتَقَبَّلاً.", "Beneficial knowledge", 1, "حصن المسلم، رقم 95", "إذا أصبحَ.", 'm'),
    Dhikr("أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ.", "I ask Allah's forgiveness", 100, "حصن المسلم، رقم 96", "مِائَةَ مَرَّةٍ فِي الْيَوْمِ.", 'b'),
    Dhikr("أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ.", "The perfect words of Allah", 3, "حصن المسلم، رقم 97", "ثلاثَ مرَّاتٍ إذا أمسى.", 'e'),
    Dhikr("اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبَيِّنَا مُحَمَّدٍ.", "Blessings on the Prophet ﷺ", 10, "حصن المسلم، رقم 98", "", 'b')
)
private val MORNING = HISN_ADHKAR.filter { it.whenSaid != 'e' }
private val EVENING = HISN_ADHKAR.filter { it.whenSaid != 'm' }

@Composable
private fun Adhkar(onBack: () -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    val evening = rememberNowMinutes() >= 15 * 60
    var set by rememberSaveable { mutableStateOf(if (evening) 1 else 0) }
    val list = if (set == 0) MORNING else EVENING
    val counts = remember(set) { mutableStateListOf(*Array(list.size) { 0 }) }
    IslamicPage(ia("Adhkār", "الأذكار"), onBack) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(look.t.soft).padding(4.dp)) {
            listOf(ia("Morning", "الصباح"), ia("Evening", "المساء")).forEachIndexed { i, label ->
                Text(label, color = if (set == i) look.t.ink else look.t.sub, fontSize = 14.sp, fontWeight = if (set == i) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (set == i) look.t.card else Color.Transparent).clickable { set = i }.padding(vertical = 9.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        list.forEachIndexed { i, d ->
            val done = counts[i] >= d.count
            Column(
                Modifier.padding(vertical = 5.dp).fillMaxWidth().hisnCard(look, color = if (done) look.t.chip else look.t.card)
                    .clickable(enabled = !done) {
                        counts[i] = counts[i] + 1
                        if (counts.withIndex().all { (k, c) -> c >= list[k].count }) HisnNights.markToday(context, "hisn:adhkar")
                    }.padding(14.dp)
            ) {
                Text(d.ar, color = look.t.ink, fontSize = 18.sp, lineHeight = 32.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, style = TextStyle(textDirection = TextDirection.Rtl), modifier = Modifier.fillMaxWidth())
                if (d.note.isNotEmpty()) Text(d.note, color = look.t.sub, fontSize = 13.sp, lineHeight = 22.sp, style = TextStyle(textDirection = TextDirection.Rtl),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                if (!isArabicUi()) Text(d.en, color = look.t.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.src, color = look.t.acc, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.clip(RoundedCornerShape(50)).background(if (done) look.t.kept else look.t.soft).padding(horizontal = 12.dp, vertical = 5.dp)) {
                        Text(ofCount(counts[i], d.count), color = if (done) Color.White else look.t.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text(ia("Tap a card to count. A finished set ticks off Today.", "اضغط البطاقة للعدّ. وتكتمل مهمة اليوم حين تُتمّ الأذكار."), color = look.t.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

// ---------------- Today's reading ----------------

/** The verse, Pickthall's English and the reference. No explanation of our own: a scholar's saying follows instead. */
private data class Reading(val ar: String, val en: String, val ref: String, val refAr: String)

private val READINGS = listOf(
    Reading("قُل لِّلْمُؤْمِنِينَ يَغُضُّوا۟ مِنْ أَبْصَٰرِهِمْ وَيَحْفَظُوا۟ فُرُوجَهُمْ ۚ ذَٰلِكَ أَزْكَىٰ لَهُمْ", "Tell the believing men to lower their gaze and be modest. That is purer for them.", "an-Nūr 30", "النور 30"),
    Reading("وَمَن يَتَّقِ ٱللَّهَ يَجْعَل لَّهُۥ مَخْرَجًا", "Whosoever keepeth his duty to Allah, Allah will appoint a way out for him.", "aṭ-Ṭalāq 2", "الطلاق 2"),
    Reading("أَلَا بِذِكْرِ ٱللَّهِ تَطْمَئِنُّ ٱلْقُلُوبُ", "Verily in the remembrance of Allah do hearts find rest!", "ar-Raʿd 28", "الرعد 28"),
    Reading("إِنَّ ٱللَّهَ يُحِبُّ ٱلتَّوَّٰبِينَ وَيُحِبُّ ٱلْمُتَطَهِّرِينَ", "Truly Allah loveth those who turn unto Him, and loveth those who have a care for cleanness.", "al-Baqara 222", "البقرة 222")
)

@Composable
private fun TodaysReading(onBack: () -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    val r = READINGS[(LocalDate.now().toEpochDay() % READINGS.size).toInt()]
    IslamicPage(ia("Today's reading", "ورد اليوم"), onBack) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(look.t.deep).padding(20.dp)) {
            Text(r.ar, color = look.t.deepInk, fontSize = 26.sp, lineHeight = 44.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (!isArabicUi()) Text("“${r.en}” (Pickthall)", color = look.t.deepInk.copy(alpha = 0.8f), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Text(ia(r.ref, r.refAr), color = look.t.deepInk.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        val sayings by rememberSayings()
        val pick = sayings?.daily(LocalDate.now().toEpochDay(), 3)
        if (pick != null) {
            Column(Modifier.fillMaxWidth().hisnCard(look).padding(16.dp)) {
                Text(ia("From the scholars", "من كلام العلماء"), color = look.t.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                SayingBody(pick, flat = true)
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(ia("Mark as read", "تمّت القراءة")) { HisnNights.markToday(context, "hisn:read"); onBack() }
    }
}

// ---------------- Evening reflection ----------------

/**
 * Muḥāsaba before sleep, in a minute: which prayers were on time, the gaze, the wird, one thanks,
 * istighfār and one intention for tomorrow. It replaced five 1–10 scales, which nobody can answer
 * honestly about prayer. The first three still feed the night's bars on the calendar.
 */
@Composable
private fun Reflection(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    val day = LocalDate.now().toEpochDay()
    val saved = remember { HisnNights.ratings(context, day) }
    var prayed by remember { mutableStateOf(HisnPrayers.prayed(context)) }
    var gaze by rememberSaveable { mutableStateOf(when (saved.getOrNull(1)) { 10 -> 0; 6 -> 1; 2 -> 2; else -> -1 }) }
    var wird by rememberSaveable { mutableStateOf(when (saved.getOrNull(2)) { 10 -> 0; 5 -> 1; 1 -> 2; else -> -1 }) }
    var thanks by rememberSaveable { mutableStateOf(HisnNights.thanks(context, day)) }
    var forgiven by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf(HisnNights.note(context, day)) }
    val count = Integer.bitCount(prayed)

    @Composable fun Card(title: String, sub: String? = null, content: @Composable ColumnScope.() -> Unit) {
        Column(Modifier.padding(vertical = 5.dp).fillMaxWidth().hisnCard(look).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = look.t.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, color = look.t.sub, fontSize = 12.5.sp, lineHeight = 17.sp)
            content()
        }
    }
    @Composable fun Three(chosen: Int, labels: List<String>, onPick: (Int) -> Unit) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(look.t.chip).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { k, label ->
                val on = chosen == k
                Text(label, color = if (on) look.t.ink else look.t.sub, fontSize = 13.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) look.t.card else Color.Transparent)
                        .clickable(role = Role.RadioButton) { onPick(k) }.padding(vertical = 9.dp))
            }
        }
    }
    @Composable fun Line(value: String, hint: String, onChange: (String) -> Unit) {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).border(1.5.dp, look.t.line, RoundedCornerShape(12.dp)).padding(12.dp)) {
            if (value.isEmpty()) Text(hint, color = look.t.sub, fontSize = 15.sp)
            BasicTextField(value, onChange, textStyle = TextStyle(color = look.t.ink, fontSize = 15.sp, textDirection = TextDirection.Content), cursorBrush = SolidColor(look.t.pri), modifier = Modifier.fillMaxWidth())
        }
    }

    IslamicPage(ia("A reckoning before sleep", "محاسبة قبل النوم"), onBack) {
        Text(ia("One minute. What was for you today, and what was against you.", "دقيقة واحدة: ما لك اليوم وما عليك."), color = look.t.sub, fontSize = 13.5.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        Card(ia("Which prayers did you pray on time?", "أيّ الصلوات صلّيتها في وقتها؟")) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (k in 0 until 5) {
                    val on = prayed and (1 shl k) != 0
                    Text(HisnPrayers.name(k), color = if (on) look.t.priInk else look.t.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) look.t.pri else look.t.soft)
                            .clickable(role = Role.Checkbox) { prayed = HisnPrayers.togglePrayed(context, k) }.padding(vertical = 10.dp))
                }
            }
            Text(ofCount(count, 5) + if (count < 5) ia(" · make up what you missed before you sleep", " · اقضِ ما فاتك قبل أن تنام") else ia(" · alḥamdulillāh", " · الحمد لله"),
                color = look.t.sub, fontSize = 12.5.sp)
        }
        Card(ia("Your gaze today", "بصرك اليوم")) {
            Three(gaze, listOf(ia("I guarded it", "حفظتُه"), ia("I struggled", "جاهدتُ"), ia("I slipped", "زللتُ"))) { gaze = it }
            if (gaze == 2) Text(ia("The door is open. Say istighfār below, and close tomorrow the door it came from.", "الباب مفتوح. استغفر في الأسفل، وأغلق غدًا الباب الذي جاءت منه."),
                color = look.t.sub, fontSize = 12.5.sp, lineHeight = 18.sp)
        }
        Card(ia("Your Qurʾān and dhikr", "وردك من القرآن والذكر")) {
            Three(wird, listOf(ia("I read", "قرأتُ"), ia("A little", "قليلًا"), ia("Not today", "لم أقرأ"))) { wird = it }
        }
        Card(ia("One blessing you thank Allah for today", "نعمة تشكر الله عليها اليوم")) {
            Line(thanks, ia("For example: I prayed Fajr with my father", "مثلًا: صلّيت الفجر مع أبي")) { thanks = it.take(160) }
        }
        Card(ia("Ask forgiveness for what went wrong", "استغفر مما قصّرت فيه"), ia("No need to write it. Allah knows it.", "لا حاجة أن تكتبه، فالله يعلمه.")) {
            Text((if (forgiven) "✓ " else "") + ia("Astaghfirullāh wa atūbu ilayh", "أستغفر الله وأتوب إليه"),
                color = if (forgiven) look.t.kept else look.t.priInk, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .then(if (forgiven) Modifier.border(1.5.dp, look.t.kept, RoundedCornerShape(24.dp)) else Modifier.background(look.t.pri))
                    .clickable(role = Role.Button) { forgiven = true }.padding(vertical = 13.dp))
        }
        Card(ia("One intention for tomorrow", "نيّة واحدة للغد"), ia("It is saved on tonight in your calendar.", "تُحفظ على هذه الليلة في تقويمك.")) {
            Line(note, ia("For example: phone outside the room before I sleep", "مثلًا: أضع الهاتف خارج الغرفة قبل النوم")) { note = it.take(400) }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(ia("Save my reckoning", "احفظ محاسبتي")) {
            // Bars on the calendar: prayers out of five, then the gaze and the wird; the old calm and energy rows stay empty.
            if (gaze >= 0 && wird >= 0) HisnNights.setRatings(context, day, listOf(count * 2, listOf(10, 6, 2)[gaze], listOf(10, 5, 1)[wird], 0, 0))
            HisnNights.setThanks(context, day, thanks.trim())
            HisnNights.setNote(context, day, note.trim())
            HisnNights.markToday(context, "hisn:reflect")
            onBack()
            onNavigate("hisn:calendar")
        }
    }
}

// ---------------- City for prayer times ----------------

@Composable
private fun CityPicker(onBack: () -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    var current by remember { mutableStateOf(HisnPrayers.place(context).key) }
    IslamicPage(ia("City for prayer times", "مدينة مواقيت الصلاة"), onBack) {
        Text(ia("Times are worked out on this phone. Nothing is sent anywhere, and no location permission is used.", "تُحسب المواقيت على هذا الهاتف، ولا يُرسل شيء، ولا يُطلب إذن الموقع."),
            color = look.t.sub, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
        // The chosen city comes first, so it is never lost far down the list.
        val ordered = remember(current) { PrayerPlaces.ALL.filter { it.key == current } + PrayerPlaces.ALL.filter { it.key != current } }
        ordered.forEach { p ->
            val on = p.key == current
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) look.t.soft else Color.Transparent)
                    .clickable { HisnPrayers.setPlace(context, p.key); current = p.key }.padding(vertical = 12.dp, horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(if (isArabicUi()) p.ar else p.en, color = look.t.ink, fontSize = 15.sp)
                    Text(methodName(p.method), color = look.t.sub, fontSize = 11.5.sp)
                }
                if (p.key == current) Icon(Icons.Filled.Check, null, tint = look.t.pri)
            }
        }
    }
}

/** The calculation method's name in the reader's language. */
internal fun methodName(m: app.hisn.core.PrayerTimes.Method): String = if (!isArabicUi()) m.label else when (m) {
    app.hisn.core.PrayerTimes.Method.MWL -> "رابطة العالم الإسلامي"
    app.hisn.core.PrayerTimes.Method.UMM_AL_QURA -> "أم القرى، مكة"
    app.hisn.core.PrayerTimes.Method.EGYPT -> "الهيئة المصرية العامة للمساحة"
    app.hisn.core.PrayerTimes.Method.ISNA -> "الجمعية الإسلامية لأمريكا الشمالية"
    app.hisn.core.PrayerTimes.Method.KARACHI -> "جامعة العلوم الإسلامية، كراتشي"
}

// ---------------- Settings: look, prayer, then everything else ----------------


@Composable
internal fun LookChip(l: HisnLook, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val look = LocalLook.current
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(look.t.card).border(2.dp, if (on) look.t.acc else look.t.line, RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton, onClick = onClick).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(l.t.sky, l.t.pri, l.t.acc).forEach { Box(Modifier.size(14.dp).clip(CircleShape).background(it).border(1.dp, look.t.line, CircleShape)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(when (l) { HisnLook.FAJR -> ia("Fajr", "الفجر"); HisnLook.MADINAH -> ia("Madinah", "المدينة"); HisnLook.LAYL -> ia("Layl", "الليل") },
            color = look.t.ink, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
    }
}
