package app.hisn.android.nativeshell

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisn.core.PersonalProfile
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * The Islamic edition's first run, built from the Hisn prototype: a calm opening, a short quiz in
 * four chapters (You, Why it's hard, What helps, Your plan), the diagnosis and the way out, the
 * real Android switches, five promises held with a verse each, the 40-night plan, and "Hisn is
 * free". Every answer is kept on the phone and changes something in the app.
 */

private enum class HisnStep(val chapter: Int = -1, val deep: Boolean = false, val land: Boolean = true) {
    Splash, Intro(deep = true), How, Welcome(deep = true),
    Name(0), Look(0), Speech(0), Age(0), Guard(0), Hardest(0), Prayers(0),
    Usage(1), Slide0(1, deep = true), Slide1(1, deep = true), Slide2(1, deep = true), Diagnosis(1), Soft(1), Harm(1, deep = true),
    WayOut(2), Goals(2), Instead(2), Reason(2),
    Told(3), Perms(3), Try(3), Begin(3, deep = true), Pledge(3), Plan(3), Notify(3), Free
}

private val FixedSteps = setOf(
    HisnStep.Splash, HisnStep.Intro, HisnStep.How, HisnStep.Welcome, HisnStep.Slide0, HisnStep.Slide1, HisnStep.Slide2,
    HisnStep.Begin, HisnStep.Pledge, HisnStep.Notify, HisnStep.Free, HisnStep.Harm
)

private val ChapterNames = listOf("You" to "أنت", "Why it's hard" to "لماذا يصعب", "What helps" to "ما يُعين", "Your plan" to "خطّتك")

private data class Opt(val key: String, val en: String, val ar: String)

private val GuardOpts = listOf(
    Opt("adult", "My gaze: adult content", "بصري: محتوى الزنا"),
    Opt("shorts", "My time: Shorts, Reels, feeds", "وقتي: المقاطع القصيرة والتمرير"),
    Opt("time", "My prayers: the phone at ṣalāh", "صلاتي: الهاتف وقت الصلاة"),
    Opt("feeds", "My sleep: the phone in bed before sleep", "نومي: تصفّح الهاتف في السرير قبل النوم"),
    Opt("games", "My hours: games", "ساعاتي: الألعاب"),
    Opt("chats", "My chats: talk I should not be in", "محادثاتي: كلام لا يرضي الله"),
    Opt("series", "My evenings: series and films", "سهراتي: المسلسلات والأفلام")
)
private val HardestOpts = listOf(
    Opt("night", "Late at night, alone", "في آخر الليل، وحدي"),
    Opt("stressed", "After a hard day", "بعد يوم متعب"),
    Opt("bored", "When I'm bored", "حين أشعر بالملل"),
    Opt("waking", "When I wake up", "عند الاستيقاظ من النوم"),
    Opt("home", "Alone at home", "حين أكون وحدي في البيت"),
    Opt("sad", "After a quarrel or sadness", "بعد خلاف أو حزن"),
    Opt("travel", "When travelling or on holiday", "في السفر أو الإجازة"),
    Opt("unsure", "I'm not sure yet", "لست متأكدًا بعد")
)
private val PrayerOpts = listOf(
    Opt("all", "All five, mostly on time", "الخمس، وغالبًا في وقتها"),
    Opt("most", "Most of them", "معظمها"),
    Opt("few", "A few", "بعضها"),
    Opt("again", "I want to start again", "أريد أن أبدأ من جديد")
)
private val SpeechOpts = listOf(
    Opt("m", "As a brother · أخي", "بصيغة المذكّر · أخي"),
    Opt("f", "As a sister · أختي", "بصيغة المؤنّث · أختي"),
    Opt("n", "I prefer not to say", "أفضّل ألّا أذكر")
)
private val AgeOpts = listOf(
    Opt("u18", "Under 18", "أقل من 18"),
    Opt("18to24", "18 to 24", "من 18 إلى 24"),
    Opt("25to34", "25 to 34", "من 25 إلى 34"),
    Opt("35to44", "35 to 44", "من 35 إلى 44"),
    Opt("45up", "45 or older", "45 فأكثر")
)
private val HoursOpts = listOf(
    Opt("lt2", "Less than 2 hours", "أقل من ساعتين"),
    Opt("2to4", "2 to 4 hours", "من ساعتين إلى أربع"),
    Opt("4to6", "4 to 6 hours", "من أربع إلى ست ساعات"),
    Opt("6to8", "6 to 8 hours", "من ست إلى ثماني ساعات"),
    Opt("gt8", "More than 8 hours", "أكثر من ثماني ساعات")
)
private val GoalOpts = listOf(
    Opt("pray", "Pray on time", "الصلاة في وقتها"), Opt("gaze", "Lower my gaze", "غضّ بصري"),
    Opt("quran", "Qur'an every day", "القرآن كل يوم"), Opt("near", "Nearer to Allah", "القرب من الله"),
    Opt("marriage", "A stronger marriage", "زواج أقوى"), Opt("family", "Better with family", "علاقة أطيب مع الأهل"),
    Opt("focus", "Focus", "التركيز"), Opt("energy", "More energy", "طاقة أكبر"),
    Opt("sleep", "Sleep before midnight", "النوم قبل منتصف الليل"), Opt("fast", "Fast Mondays and Thursdays", "صيام الاثنين والخميس")
)
internal data class InsteadOpt(val key: String, val icon: String, val en: String, val ar: String)
internal val InsteadOpts = listOf(
    InsteadOpt("wudu", "ibrik", "Make wuḍūʾ", "الوضوء"), InsteadOpt("quran", "quran", "A page of Qur'an", "صفحة من القرآن"),
    InsteadOpt("masjid", "mosque", "Walk to the masjid", "المشي إلى المسجد"), InsteadOpt("parents", "hands", "Call my parents", "الاتصال بوالديّ"),
    InsteadOpt("rakah", "rug", "Two rakʿahs", "ركعتان"), InsteadOpt("family", "dates", "Eat with family", "الطعام مع الأهل"),
    InsteadOpt("outside", "olive", "Sit outside", "الجلوس في الخارج"), InsteadOpt("sleep", "crescent", "Sleep early", "النوم مبكرًا")
)
private data class Slide(val icon: String, val en: String, val ar: String, val bodyEn: String, val bodyAr: String, val verse: String, val src: String)
private val Slides = listOf(
    Slide("lantern", "It never starts with the worst step", "لا يبدأ الأمر بأسوأ خطوة",
        "A feed, then a search, then a page. Hisn closes the first doors.",
        "مقطع، ثم بحث، ثم صفحة. وحصن يغلق الأبواب الأولى.",
        "لَا تَتَّبِعُوا۟ خُطُوَٰتِ ٱلشَّيْطَٰنِ", "24:21"),
    Slide("shield", "The gaze comes first", "النظر أولًا",
        "The command to lower the gaze comes before the command to guard yourself. What you don't look at, you don't chase.",
        "جاء الأمر بغضّ البصر قبل الأمر بحفظ الفرج. وما لا تنظر إليه لا تلهث وراءه.",
        "يَغُضُّوا۟ مِنْ أَبْصَٰرِهِمْ وَيَحْفَظُوا۟ فُرُوجَهُمْ", "24:30"),
    Slide("hands", "The door is never closed", "الباب لا يُغلق أبدًا",
        "However many times it happened, Allah forgives all sins for the one who turns back. That is where Hisn starts.",
        "مهما تكرّر الأمر، فإن الله يغفر الذنوب جميعًا لمن رجع إليه. ومن هنا يبدأ حصن.",
        "لَا تَقْنَطُوا۟ مِن رَّحْمَةِ ٱللَّهِ", "39:53")
)
private data class PledgeLine(val en: String, val ar: String, val verse: String, val meaning: String, val src: String)
private val Pledges = listOf(
    PledgeLine("Will you seek Allah's help, and not only your own strength?", "هل تستعين بالله ولا تتّكل على نفسك؟",
        "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ", "Thee (alone) we worship; Thee (alone) we ask for help.", "1:5"),
    PledgeLine("Will you guard your eyes and your heart from what leads to it?", "هل تحفظ عينيك وقلبك مما يقود إليه؟",
        "قُل لِّلْمُؤْمِنِينَ يَغُضُّوا۟ مِنْ أَبْصَٰرِهِمْ", "Tell the believing men to lower their gaze.", "24:30"),
    PledgeLine("Will you stop at the first step, not the last?", "هل تتوقّف عند الخطوة الأولى، لا الأخيرة؟",
        "لَا تَتَّبِعُوا۟ خُطُوَٰتِ ٱلشَّيْطَٰنِ", "Follow not the footsteps of the devil.", "24:21"),
    PledgeLine("Do you believe Allah will make a way out for you?", "هل توقن أن الله سيجعل لك مخرجًا؟",
        "وَمَن يَتَّقِ ٱللَّهَ يَجْعَل لَّهُۥ مَخْرَجًا", "Whosoever keepeth his duty to Allah, Allah will appoint a way out for him.", "65:2"),
    PledgeLine("If you fall, will you remember Allah and come back?", "إن سقطت، هل تذكر الله وتعود؟",
        "ذَكَرُوا۟ ٱللَّهَ فَٱسْتَغْفَرُوا۟ لِذُنُوبِهِمْ", "…remember Allah and implore forgiveness for their sins.", "3:135")
)
private val SurahNames = mapOf("1" to "الفاتحة", "24" to "النور", "65" to "الطلاق", "3" to "آل عمران", "39" to "الزمر", "17" to "الإسراء")
private val SurahNamesEn = mapOf("17" to "al-Isrāʾ", "39" to "az-Zumar")

/** One harm of sins in Ibn al-Qayyim's own words, with its page in ad-Dāʾ wa ad-Dawāʾ (ʿAṭāʾāt al-ʿIlm edition). */
private data class HarmLine(val icon: String, val en: String, val ar: String, val quote: String, val page: Int)
private const val HarmLead = "أن الذنوب تضر ولابد، وأن ضررها في القلوب؛ كضرر السموم في الأبدان، على اختلاف درجاتها في الضرر."
private const val HarmLeadPage = 98
private val HarmLines = listOf(
    HarmLine("heart_dark", "It puts out the light of the heart", "تطفئ نور القلب", "العلم نور يقذفه الله في القلب، والمعصية تطفئ ذلك النور.", 132),
    HarmLine("heart_cage", "It leaves a loneliness in the heart", "تورث وحشة في القلب", "وليس على القلب أمَرُّ من وحشة الذنب على الذنب", 134),
    HarmLine("heart_sealed", "It rusts the heart until it is sealed", "تُصدئ القلب حتى يُختم عليه", "أن القلب يصدأ من المعصية، فإن زادت غلب الصدأ حتى يصير رانًا، ثم يغلب حتى يصير طبعًا وقفلًا وختمًا، فيصير القلب في غشاوة وغلاف.", 149),
    HarmLine("heart_chain", "One sin calls the next, until it is a habit", "تدعو أختها حتى تصير عادة", "وكذلك جانب السيئات أيضًا، حتى تصير الطاعات والمعاصي هيئات راسخة وصفات لازمة وملكات ثابتة.", 139),
    HarmLine("heart_dry", "It takes the blessing out of life and provision", "تمحق بركة العمر والرزق", "ومن عقوبات المعاصي: أنها تمحق بركة العمر، وبركة الرزق، وبركة العلم، وبركة العمل، وبركة الطاعة.", 199),
    HarmLine("heart_dust", "It grows small in your eyes", "تهون في عينك", "العبد لا يزال يرتكب الذنب، حتى يهون عليه، ويصغر في قلبه. وذلك علامة الهلاك", 58)
)

/** "سورة النور، الآية 30" in Arabic, "24:30" in English. */
private fun verseRef(ref: String): String {
    if (!isArabicUi()) return ref
    val (s, a) = ref.split(':')
    return "سورة ${SurahNames[s] ?: s}، الآية ${nums(a)}"
}

private const val Prefs = "hisn-native"

@Composable
fun HisnOnboarding(
    actions: OnboardingActions,
    directBuild: Boolean,
    initialName: String = "",
    initialReason: String = "",
    refresh: Int = 0
) {
    val context = LocalContext.current
    val look = LocalLook.current
    val haptics = rememberHisnHaptics()
    val reduceMotion = LocalReduceMotion.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    var step by rememberSaveable { mutableStateOf(HisnStep.Splash) }
    var forward by rememberSaveable { mutableStateOf(true) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var speech by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var guard by rememberSaveable { mutableStateOf("adult") }
    var hardest by rememberSaveable { mutableStateOf("") }
    var prayers by rememberSaveable { mutableStateOf("") }
    var hours by rememberSaveable { mutableStateOf("") }
    var goals by rememberSaveable { mutableStateOf("pray,gaze,near") }
    var instead by rememberSaveable { mutableStateOf("wudu,quran,rakah") }
    var reason by rememberSaveable { mutableStateOf(initialReason) }
    var how by rememberSaveable { mutableIntStateOf(0) }
    var pledge by rememberSaveable { mutableIntStateOf(0) }
    var harm by rememberSaveable { mutableIntStateOf(0) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var showPausePreview by rememberSaveable { mutableStateOf(false) }

    if (showPausePreview) {
        val dismiss = { showPausePreview = false }
        BackHandler(onBack = dismiss)
        OpenWaitScreen(
            appLabel = ia("Instagram", "إنستغرام"), appPackage = "", seconds = 5, openedToday = 0, closedToday = 0,
            reason = reason.trim(), onClose = dismiss, onOpen = dismiss, preview = true
        )
        return
    }

    val notificationsGranted = remember(refresh) { actions.granted("notifications") }
    val steps = remember(notificationsGranted, step) {
        HisnStep.entries.filter { it != HisnStep.Notify || !notificationsGranted || step == HisnStep.Notify }
    }
    val index = steps.indexOf(step)
    fun set(s: Set<String>) = s.sorted().joinToString(",")
    fun split(v: String) = v.split(',').filter { it.isNotEmpty() }.toSet()

    fun save() {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit()
            .putString("onboard.speech", speech).putString("onboard.age", age).putString("onboard.prayers", prayers)
            .putString("onboard.goals", goals).putString("onboard.instead", instead)
            .putString("onboard.guard", guard).putString("onboard.hardest", hardest).apply()
        val words = reason.trim()
        actions.savePersonal(name.trim(), if (words.isEmpty()) "" else PersonalProfile.CUSTOM, words)
    }

    fun go(target: HisnStep, ahead: Boolean = true) {
        forward = ahead
        step = target
    }
    fun next() {
        if (step == HisnStep.Reason) save()
        if (step == HisnStep.Free) {
            save()
            actions.completeSetup()
            return
        }
        steps.getOrNull(index + 1)?.let { go(it) }
    }
    fun back() {
        if (step == HisnStep.How && how > 0) { how--; return }
        if (step == HisnStep.Pledge && pledge > 0) { pledge--; return }
        if (step == HisnStep.Harm && harm > 0) { harm--; return }
        steps.getOrNull(index - 1)?.let { go(it, ahead = false) }
    }
    BackHandler(enabled = index > 0, onBack = ::back)

    val targets = split(guard)
    // Several hard times may be chosen; the first by weight leads the pages that name only one.
    val hardSet = split(hardest)
    val hardMain = listOf("night", "stressed", "bored", "waking", "home", "sad", "travel", "unsure").firstOrNull { it in hardSet } ?: ""
    val triggers = buildSet {
        if ("night" in hardSet) { add("night"); add("alone") }
        if ("stressed" in hardSet) add("stressed")
        if ("bored" in hardSet) add("bored")
        listOf("waking", "home", "sad", "travel").forEach { if (it in hardSet) add(it) }
    }

    // The live state of the two layers, read again whenever a system setting changes.
    val states = remember(refresh, step) { listOf("dns", "accessibility").associateWith(actions::protectionState) }
    val optional = remember(refresh, step) { listOf("usage", "notifications", "battery").associateWith(actions::granted) }
    val checks = remember(refresh, step) { listOf("tags", "images", "allapps").associateWith(actions::checkOn) }
    val guardOn = remember(refresh, step) { directBuild && actions.guardOn() }

    val deep = step.deep
    val ink = if (deep) look.t.deepInk else look.t.ink
    val bg = remember(look) { HisnArt.id(context, look.backgroundName) }

    CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides ink) {
        val tapAnywhere = step == HisnStep.Splash || step == HisnStep.Intro
        Box(
            Modifier.fillMaxSize().background(if (deep) look.t.deep else look.t.sky)
                .then(if (tapAnywhere) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { next() } else Modifier)
        ) {
            if (step.land && bg != 0) {
                Image(
                    painterResource(bg), null, contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter,
                    alpha = if (deep) 0.55f else 1f,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f).align(Alignment.BottomCenter)
                )
                Box(Modifier.fillMaxWidth().fillMaxHeight(0.5f).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(if (deep) look.t.deep else look.t.sky, Color.Transparent), endY = 260f)))
            }
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxSize().align(Alignment.TopCenter)
                    .statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 22.dp)
            ) {
                if (step.chapter >= 0) ChapterBar(step, steps, deep, ::back)
                else Spacer(Modifier.height(12.dp))
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val sign = (if (forward) 1 else -1) * (if (rtl) -1 else 1)
                        if (reduceMotion) fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                        else (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { sign * it / 8 }) togetherWith fadeOut(tween(140))
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    label = "hisn onboarding"
                ) { shown ->
                    // Centred pages lay out with weights on the full height; the rest scroll.
                    val fixed = shown in FixedSteps
                    Column(
                        (if (fixed) Modifier.fillMaxSize() else Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
                            .padding(top = 10.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (shown) {
                            HisnStep.Splash -> SplashPage()
                            HisnStep.Intro -> IntroPage()
                            HisnStep.How -> HowPage(how)
                            HisnStep.Welcome -> WelcomeHisnPage()
                            HisnStep.Name -> {
                                Title(ia("Last, a little about you", "أولًا، قليلٌ عنك"))
                                Sub(ia("What should Hisn call you?", "بماذا يناديك حصن؟"))
                                Field(name, ia("Your first name", "اسمك الأول"), single = true) { name = it.take(25) }
                            }
                            HisnStep.Look -> IslamicLookPage()
                            HisnStep.Speech -> Quiz(ia("How should Hisn speak to you?", "كيف يخاطبك حصن؟"),
                                ia("Arabic changes with this.", "تتغيّر صيغة الخطاب بحسب اختيارك."), SpeechOpts, setOf(speech)) {
                                haptics.tick(); speech = it; next()
                            }
                            HisnStep.Age -> Quiz(ia("How old are you?", "كم عمرك؟"),
                                ia("So Hisn speaks to you and suggests what fits your age.", "ليخاطبك حصن ويقترح عليك ما يناسب عمرك."), AgeOpts, setOf(age)) {
                                haptics.tick(); age = it; next()
                            }
                            HisnStep.Guard -> Quiz(ia("What do you want to guard?", "ماذا تريد أن تحفظ؟"),
                                ia("Choose all that fit.", "اختر كل ما ينطبق عليك."), GuardOpts, targets) {
                                haptics.tick(); guard = set(if (it in targets) targets - it else targets + it)
                            }
                            HisnStep.Hardest -> Quiz(ia("When is it hardest?", "متى يكون الأمر أصعب؟"),
                                ia("Choose all that fit.", "اختر كل ما ينطبق عليك."), HardestOpts, hardSet) {
                                haptics.tick(); hardest = set(if (it in hardSet) hardSet - it else hardSet + it)
                            }
                            HisnStep.Prayers -> Quiz(ia("How are your five prayers right now?", "كيف حال صلواتك الخمس الآن؟"),
                                ia("No judgement. This only sets your reminders.", "لا لوم هنا. هذا يضبط تذكيراتك فقط."), PrayerOpts, setOf(prayers)) {
                                haptics.tick(); prayers = it; next()
                            }
                            HisnStep.Usage -> UsagePage(hours, optional["usage"] == true, onUsage = { actions.request("usage") }) {
                                haptics.tick(); hours = it; next()
                            }
                            HisnStep.Slide0 -> SlidePage(0)
                            HisnStep.Slide1 -> SlidePage(1)
                            HisnStep.Slide2 -> SlidePage(2)
                            HisnStep.Diagnosis -> IslamicDiagnosisPage(name.trim(), targets, hours, triggers)
                            HisnStep.Soft -> SoftPage()
                            HisnStep.Harm -> HarmPage(harm)
                            HisnStep.WayOut -> IslamicWayOutPage(targets, triggers)
                            HisnStep.Goals -> GoalsHisnPage(split(goals)) { haptics.tick(); val g = split(goals); goals = set(if (it in g) g - it else g + it) }
                            HisnStep.Instead -> InsteadPage(split(instead)) { haptics.tick(); val g = split(instead); instead = set(if (it in g) g - it else g + it) }
                            HisnStep.Reason -> ReasonHisnPage(reason) { reason = it.take(PersonalProfile.MAX_REASON_CODE_POINTS) }
                            HisnStep.Told -> ToldPage(name.trim(), targets, hardMain, prayers)
                            HisnStep.Perms -> PermsPage(states, optional, checks, directBuild, guardOn, actions)
                            HisnStep.Try -> TryPage(states, tried) { tried = true; showPausePreview = true }
                            HisnStep.Begin -> BeginPage(name.trim())
                            HisnStep.Pledge -> PledgePage(pledge) {
                                haptics.confirm()
                                if (pledge < Pledges.lastIndex) pledge++ else next()
                            }
                            HisnStep.Plan -> PlanHisnPage(name.trim())
                            HisnStep.Notify -> NotifyPage()
                            HisnStep.Free -> FreePage()
                        }
                    }
                }
                BottomActions(
                    step = step, deep = deep, how = how, pledge = pledge, harm = harm,
                    perms = states.values.count { it == OnboardingProtectionState.OFF },
                    hasGoals = goals.isNotEmpty(), hasInstead = instead.isNotEmpty(),
                    onNext = {
                        when (step) {
                            HisnStep.How -> if (how < 2) how++ else next()
                            HisnStep.Harm -> if (harm <= HarmLines.size) harm++ else next()
                            else -> next()
                        }
                    },
                    onSkipQuestions = { save(); go(HisnStep.Perms) },
                    onNotify = { actions.request("notifications"); next() },
                    onBackPledge = ::back
                )
            }
        }
    }
}

// ---------------------------------------------------------------- frame

@Composable
private fun ChapterBar(step: HisnStep, steps: List<HisnStep>, deep: Boolean, onBack: () -> Unit) {
    val look = LocalLook.current
    val c = step.chapter
    val inChapter = steps.filter { it.chapter == c }
    val pos = (inChapter.indexOf(step) + 1f) / inChapter.size
    val animated by animateFloatAsState(pos, tween(300), label = "chapter")
    val track = if (deep) Color.White.copy(alpha = 0.22f) else look.t.line
    val fill = if (deep) Color.White else look.t.pri
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, ia("Back", "رجوع"), tint = if (deep) look.t.deepInk else look.t.ink)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(4) { k ->
                val f = when { k < c -> 1f; k == c -> animated; else -> 0f }
                Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(track)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(CircleShape).background(fill))
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(ia(ChapterNames[c].first, ChapterNames[c].second), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = if (deep) look.t.deepInk.copy(alpha = 0.8f) else look.t.sub)
    }
}

@Composable
private fun BottomActions(
    step: HisnStep, deep: Boolean, how: Int, pledge: Int, harm: Int, perms: Int, hasGoals: Boolean, hasInstead: Boolean,
    onNext: () -> Unit, onSkipQuestions: () -> Unit, onNotify: () -> Unit, onBackPledge: () -> Unit
) {
    val look = LocalLook.current
    val arrow = ia(" →", " ←")
    // Over the landscape the bottom is dark in every look, so the small print there is white.
    val quiet = if (step.land || deep) Color.White.copy(alpha = 0.88f) else look.t.sub
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (step) {
            HisnStep.Splash, HisnStep.Intro -> Text(ia("Tap to continue", "اضغط للمتابعة"), textAlign = TextAlign.Center, fontSize = 13.sp,
                color = quiet, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
            HisnStep.Welcome -> {
                PillButton(ia("Start the questions", "ابدأ الأسئلة") + arrow, white = true, onClick = onNext)
                TextLink(ia("Skip the questions", "تخطَّ الأسئلة"), look.t.deepInk, onSkipQuestions)
            }
            HisnStep.Speech, HisnStep.Age, HisnStep.Prayers, HisnStep.Usage -> {
                TextLink(ia("Skip this question", "تخطَّ هذا السؤال"), quiet, onNext)
            }
            HisnStep.Pledge -> {
                TextLink(if (pledge == 0) ia("Not now", "ليس الآن") else ia("Previous", "السابق"), quiet) {
                    if (pledge == 0) onNext() else onBackPledge()
                }
            }
            HisnStep.Notify -> {
                PillButton(ia("Yes, remind me", "نعم، ذكّرني"), onClick = onNotify)
                TextLink(ia("Later", "لاحقًا"), quiet, onNext)
            }
            HisnStep.Perms -> PillButton(
                if (perms > 0) ia("$perms still needed · continue anyway", "${nums(perms)} ما زال ضروريًا، تابع على أي حال") else ia("Continue", "متابعة") + arrow,
                dim = perms > 0, onClick = onNext
            )
            else -> {
                val label = when (step) {
                    HisnStep.How -> if (how < 2) ia("Next", "التالي") + arrow else ia("Got it", "فهمت") + arrow
                    HisnStep.Name -> ia("Continue", "متابعة") + arrow
                    HisnStep.Slide0, HisnStep.Slide1, HisnStep.Slide2 -> ia("Next", "التالي") + arrow
                    HisnStep.Harm -> when {
                        harm == 0 -> ia("Begin", "ابدأ") + arrow
                        harm > HarmLines.size -> ia("Show me the way", "أرني الطريق") + arrow
                        else -> ia("Next", "التالي") + arrow
                    }
                    HisnStep.WayOut -> ia("Build my plan", "ابنِ خطّتي") + arrow
                    HisnStep.Goals -> ia("Choose my goals", "اعتمد أهدافي") + arrow
                    HisnStep.Reason -> ia("Save my line", "احفظ سطري") + arrow
                    HisnStep.Told -> ia("That's right", "هذا صحيح") + arrow
                    HisnStep.Try -> ia("Continue", "متابعة") + arrow
                    HisnStep.Begin -> ia("Make my promises", "أعطِ عهودي") + arrow
                    HisnStep.Plan -> ia("Begin", "لنبدأ") + arrow
                    HisnStep.Free -> ia("Open Hisn", "افتح حصنًا") + arrow
                    else -> ia("Continue", "متابعة") + arrow
                }
                val off = (step == HisnStep.Goals && !hasGoals) || (step == HisnStep.Instead && !hasInstead)
                PillButton(label, white = deep, dim = off, onClick = { if (!off) onNext() })
            }
        }
    }
}

@Composable
private fun TextLink(label: String, color: Color, onClick: () -> Unit) {
    Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp))
}

/** The deep pill of the prototype; white on the deep pages. */
@Composable
private fun PillButton(label: String, white: Boolean = false, dim: Boolean = false, onClick: () -> Unit) {
    val look = LocalLook.current
    val bg = if (white) Color.White else look.t.pri
    val fg = if (white) look.t.deep else look.t.priInk
    Text(
        label, color = fg.copy(alpha = if (dim) 0.7f else 1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(28.dp))
            .background(bg.copy(alpha = if (dim) 0.55f else 1f)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 16.dp)
    )
}

private val serif: FontFamily? get() = if (isArabicUi()) null else FontFamily.Serif

@Composable
private fun Title(text: String, center: Boolean = false, size: TextUnit = 26.sp, color: Color? = null) {
    Text(
        text, color = color ?: androidx.compose.material3.LocalContentColor.current, fontSize = size, lineHeight = size * 1.3f,
        fontWeight = FontWeight.Bold, fontFamily = serif, textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.fillMaxWidth().semantics { heading() }
    )
}

@Composable
private fun Sub(text: String, center: Boolean = false, color: Color? = null) {
    Text(text, color = color ?: LocalLook.current.t.sub, fontSize = 14.5.sp, lineHeight = 21.sp,
        textAlign = if (center) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Verse(text: String, ref: String, light: Boolean) {
    val look = LocalLook.current
    val mark = if (light) Color(0xFFE9C46A) else look.t.acc
    // The verse in the Naskh hand between its marks.
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(androidx.compose.ui.text.SpanStyle(color = mark)); append("\uFD3F "); pop()
            append(text)
            pushStyle(androidx.compose.ui.text.SpanStyle(color = mark)); append(" \uFD3E"); pop()
        },
        color = if (light) look.t.deepInk else look.t.ink, fontSize = 22.sp, lineHeight = 40.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Serif,
        style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl), modifier = Modifier.fillMaxWidth())
    Text(ref, color = if (light) look.t.deepInk.copy(alpha = 0.75f) else look.t.sub, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth())
}

/** Hisn's flat gate mark, from the prototype's logo. */
@Composable
internal fun HisnGateMark(size: Dp, color: Color) {
    val path = remember {
        PathParser().parsePathString(
            "M10 7h8v5h6V7h16v5h6V7h8v27c0 13-9 21.5-22 27C19 55.5 10 47 10 34zM24 52V36.5Q24 27.5 32 22.5Q40 27.5 40 36.5V52z"
        ).toPath().apply { fillType = PathFillType.EvenOdd }
    }
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 64f, pivot = Offset.Zero) { drawPath(path, color) }
    }
}

@Composable
private fun ColumnScope.Grow(weight: Float = 1f) = Spacer(Modifier.weight(weight).heightIn(min = 8.dp))

@Composable
private fun NumberedCard(n: Int, label: String, on: Boolean, onClick: () -> Unit) {
    val look = LocalLook.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).hisnCard(look, 16.dp)
            .border(1.5.dp, if (on) look.t.pri else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(role = Role.Checkbox, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(if (on) look.t.pri else look.t.soft), contentAlignment = Alignment.Center) {
            if (on) Icon(Icons.Rounded.Check, null, tint = look.t.priInk, modifier = Modifier.size(16.dp))
            else Text(nums(n), color = look.t.pri, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = look.t.ink, fontSize = 15.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun Quiz(q: String, sub: String?, opts: List<Opt>, chosen: Set<String>, onPick: (String) -> Unit) {
    Title(q)
    if (sub != null) Sub(sub)
    Spacer(Modifier.height(2.dp))
    opts.forEachIndexed { i, o -> NumberedCard(i + 1, ia(o.en, o.ar), o.key in chosen) { onPick(o.key) } }
}

@Composable
private fun Field(value: String, hint: String, single: Boolean, onChange: (String) -> Unit) {
    val look = LocalLook.current
    TextField(
        value = value, onValueChange = onChange, singleLine = single, minLines = if (single) 1 else 3,
        placeholder = { Text(hint, color = look.t.sub) },
        keyboardOptions = KeyboardOptions(capitalization = if (single) KeyboardCapitalization.Words else KeyboardCapitalization.Sentences),
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = look.t.card, unfocusedContainerColor = look.t.card,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = look.t.ink, unfocusedTextColor = look.t.ink, cursorColor = look.t.pri
        ),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp),
        modifier = Modifier.fillMaxWidth().hisnCard(look, 16.dp)
    )
}

// ---------------------------------------------------------------- pages

/** What leads to it: the suggestive clips that find a person, then the explicit content they look for. */
@Composable
private fun SoftPage() {
    val look = LocalLook.current
    @Composable fun Part(label: String, title: String, body: String) {
        Column(Modifier.fillMaxWidth().hisnCard(look, 16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = look.t.sub, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            Text(title, color = look.t.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(body, color = look.t.sub, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
    Text(ia("Where it starts", "من أين يبدأ"), color = look.t.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Title(ia("Zina does not start with zina", "الزنا لا يبدأ بالزنا"))
    Part(ia("1 · What finds you", "1 · ما يأتيك دون أن تطلبه"), ia("Suggestive clips", "المقاطع المثيرة"),
        ia("Revealing dress, dancing and hints in short videos, ads and series. It looks ordinary, but it wakes desire and trains the eye to want more.",
            "لباس فاضح ورقص وإيحاءات في المقاطع القصيرة والإعلانات والمسلسلات. يبدو أمرًا عاديًّا، لكنه يوقظ الشهوة ويعوّد العين أن تطلب المزيد."))
    Text("↓", color = look.t.sub, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Part(ia("2 · What you then look for", "2 · ما تبحث عنه بعدها"), ia("Explicit zina content", "مقاطع الزنا الصريحة"),
        ia("The eye that got used to a little asks for more, and a search follows. Almost nobody begins here; they arrive here.",
            "العين التي اعتادت القليل تطلب الأكثر، فيأتي البحث. قلّ من يبدأ من هنا، وإنما يصل إليه."))
    Verse("وَلَا تَقْرَبُوا۟ ٱلزِّنَىٰٓ ۖ إِنَّهُۥ كَانَ فَٰحِشَةً وَسَآءَ سَبِيلًا", verseRef("17:32"), false)
    // English meaning: Pickthall's published translation, not ours.
    if (!isArabicUi()) Sub("“And come not near unto adultery. Lo! it is an abomination and an evil way.” (Pickthall)", center = true)
    // The meaning is a scholar's, not ours: al-Saʿdī's tafsīr of this verse, as quran.com carries it.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(look.t.line))
        Text(rtlLine("«والنهي عن قربانه أبلغ من النهي عن مجرد فعله لأن ذلك يشمل النهي عن جميع مقدماته ودواعيه»"), color = look.t.ink, fontSize = 17.sp, lineHeight = 30.sp, fontFamily = serif, textAlign = TextAlign.Center,
            style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl), modifier = Modifier.fillMaxWidth())
        if (!isArabicUi()) Sub("al-Saʿdī explains that forbidding coming near it is stronger than forbidding the act alone, because it covers everything that leads to it.", center = true)
        Text(ia("— al-Saʿdī · Taysīr al-Karīm ar-Raḥmān, on al-Isrāʾ 32", rtlLine("— السعدي · تيسير الكريم الرحمن، تفسير الإسراء 32")), color = look.t.sub, fontSize = 12.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().height(1.dp).background(look.t.line))
    }
    Sub(ia("Hisn closes both: the suggestive clips inside apps, and the zina sites.", "حصن يغلق الاثنين: المقاطع المثيرة داخل التطبيقات، ومواقع الزنا."), center = true)
}

/** The harms of sins from ad-Dāʾ wa ad-Dawāʾ, one to a screen: an opening, each harm, then the cure. */
@Composable
private fun ColumnScope.HarmPage(i: Int) {
    val look = LocalLook.current
    val n = HarmLines.size
    val soft = look.t.deepInk.copy(alpha = 0.78f)
    @Composable fun Quote(text: String, page: Int) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(look.t.deepInk.copy(alpha = 0.3f)))
            Text(rtlLine("«$text»"), color = look.t.deepInk, fontSize = 18.sp, lineHeight = 32.sp, fontFamily = serif, textAlign = TextAlign.Center,
                style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl), modifier = Modifier.fillMaxWidth())
            Text(ia("— Ibn al-Qayyim · ad-Dāʾ wa ad-Dawāʾ, p. $page", rtlLine("— ابن القيم · الداء والدواء، ص $page")), color = soft, fontSize = 12.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Box(Modifier.fillMaxWidth().height(1.dp).background(look.t.deepInk.copy(alpha = 0.3f)))
        }
    }
    @Composable fun Dots() {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(n + 2) { k ->
                Box(Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (k == i) 20.dp else 6.dp).clip(CircleShape)
                    .background(look.t.deepInk.copy(alpha = if (k == i) 1f else 0.3f)))
            }
        }
    }
    Grow()
    when {
        i == 0 -> {
            // A tarnished, cracked heart: the picture of what the page is about.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HisnIcon("heart", 130.dp) }
            Text(ia("From the book ad-Dāʾ wa ad-Dawāʾ", "من كتاب «الداء والدواء» لابن القيم"), color = soft, fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Title(ia("What sins do to the heart", "ما تفعله الذنوب بالقلب"), center = true, size = 27.sp)
            Quote(HarmLead, HarmLeadPage)
            Sub(ia("Six harms, one at a time. Then the cure.", "ستة آثار، واحدًا واحدًا. ثم الدواء."), center = true, color = soft)
        }
        i <= n -> {
            val h = HarmLines[i - 1]
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HisnIcon(h.icon, 130.dp) }
            Text(ia("Harm $i of $n", "الأثر $i من $n"), color = soft, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Title(ia(h.en, h.ar), center = true, size = 27.sp)
            Quote(h.quote, h.page)
        }
        else -> {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HisnIcon("heart_gold", 130.dp) }
            Text(ia("And the cure", "والدواء"), color = soft, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Title(ia("Every illness has a cure", "لكلّ داءٍ دواء"), center = true, size = 27.sp)
            Sub(ia("Repentance wipes the sin out with Allah. Its effects in this life stay until you change yourself.",
                "التوبة تمحو الذنب عند الله. أما آثاره في دنياك فتبقى حتى تغيّر نفسك."), center = true, color = soft)
            Verse("لَا تَقْنَطُوا۟ مِن رَّحْمَةِ ٱللَّهِ", verseRef("39:53"), true)
            // English meaning: Pickthall's published translation, not ours.
            if (!isArabicUi()) Sub("“Despair not of the mercy of Allah, Who forgiveth all sins.” (Pickthall)", center = true, color = soft)
        }
    }
    Grow()
    Dots()
}

@Composable
private fun ColumnScope.SplashPage() {
    val look = LocalLook.current
    Column(
        Modifier.fillMaxWidth().weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Grow()
        HisnGateMark(92.dp, look.t.ink)
        Title(ia("Hisn", "حِصن"), center = true, size = 30.sp)
        Verse("قُل لِّلْمُؤْمِنِينَ يَغُضُّوا۟ مِنْ أَبْصَٰرِهِمْ", if (isArabicUi()) verseRef("24:30") else "\"Tell the believing men to lower their gaze\" (Pickthall) · 24:30", false)
        Grow(2f)
    }
}

@Composable
private fun ColumnScope.IntroPage() {
    val look = LocalLook.current
    Column(
        Modifier.fillMaxWidth().weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Grow()
        HisnIcon("tower", 110.dp)
        Title(ia("Every phone has a door.", "في كل هاتفٍ باب."), center = true, size = 30.sp)
        Text(ia("Hisn guards it, so your heart can rest.", "وحصنٌ يحرسه، ليطمئنّ قلبك."), color = look.t.deepInk.copy(alpha = 0.8f),
            fontSize = 20.sp, lineHeight = 30.sp, textAlign = TextAlign.Center, fontFamily = serif, modifier = Modifier.fillMaxWidth())
        Grow()
        // As in the prototype: Aḥmad Farīd on patience as a fortress, with the page.
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(look.t.deepInk.copy(alpha = 0.25f)))
            Text(rtlLine("«…جعل الصبرَ جوادًا لا يكبو، وصارمًا لا ينبو، وجندًا غالبًا لا يُهزم، وحِصنًا حصينًا لا يُهدم، فهو والنصرُ أخوان شقيقان.»"),
                color = look.t.deepInk, fontSize = 17.sp, lineHeight = 31.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
            if (!isArabicUi()) Text("Allah made patience a steed that does not stumble, a blade that does not blunt, an army that is not defeated, and a fortress that is not torn down. Patience and victory are brothers.",
                color = look.t.deepInk.copy(alpha = 0.8f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(ia("— Tazkiyat al-Nufūs, p. 76", rtlLine("— تزكية النفوس، ص 76")), color = look.t.deepInk.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center)
            Box(Modifier.fillMaxWidth().height(1.dp).background(look.t.deepInk.copy(alpha = 0.25f)))
        }
        Grow()
    }
}

@Composable
private fun ColumnScope.HowPage(frame: Int) {
    val look = LocalLook.current
    Text(ia("How Hisn works · ${frame + 1}/3", "كيف يعمل حصن: ${nums(frame + 1)} من ${nums(3)}"), color = look.t.sub, fontSize = 12.sp,
        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Grow()
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).aspectRatio(1.2f).hisnCard(look, 22.dp), contentAlignment = Alignment.Center) {
        when (frame) {
            0 -> Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("●●●●●●.com", color = look.t.sub, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().clip(CircleShape).background(look.t.soft).padding(horizontal = 12.dp, vertical = 8.dp))
                repeat(3) { Box(Modifier.fillMaxWidth(if (it == 2) 0.6f else 1f).height(if (it == 0) 70.dp else 14.dp).clip(RoundedCornerShape(8.dp)).background(look.t.soft)) }
            }
            1 -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HisnIcon("shield", 64.dp)
                Text(ia("Hisn closed this page", "أغلق حصن هذه الصفحة"), color = look.t.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(ia("Nothing was saved", "لم يُحفظ شيء"), color = look.t.sub, fontSize = 13.sp)
            }
            else -> Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(true, true, false, true, true, true, true).forEachIndexed { i, ok ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(dayLetter(LocalDate.now().minusDays((6 - i).toLong()).dayOfWeek), color = look.t.sub, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.size(30.dp).clip(CircleShape).background(if (ok) look.t.kept else look.t.soft)
                            .then(if (i == 6) Modifier.border(2.dp, look.t.acc, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                            Text(nums(LocalDate.now().minusDays((6 - i).toLong()).dayOfMonth), color = if (ok) Color.White else look.t.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    Title(
        when (frame) {
            0 -> ia("You open something without thinking", "تفتح شيئًا دون أن تنتبه")
            1 -> ia("Hisn closes it. No alarm, no shame.", "يُغلقه حصن، بلا صراخ ولا لوم.")
            else -> ia("You breathe, turn back, and the night still counts.", "تتنفّس وترجع، وتبقى ليلتك محسوبة.")
        }, center = true, size = 22.sp
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(3) { k -> Box(Modifier.padding(3.dp).size(if (k == frame) 18.dp else 7.dp, 7.dp).clip(CircleShape).background(if (k == frame) look.t.pri else look.t.line)) }
    }
    Grow()
}

@Composable
private fun ColumnScope.WelcomeHisnPage() {
    val look = LocalLook.current
    Spacer(Modifier.height(18.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HisnGateMark(64.dp, look.t.deepInk) }
    Sub(ia("Welcome to", "أهلًا بك في"), center = true, color = look.t.deepInk.copy(alpha = 0.8f))
    Title(ia("Hisn", "حِصن"), center = true, size = 42.sp)
    Sub(ia("Allah has given you a way to guard your gaze and your heart. We're here to help you take it.",
        "جعل الله لك سبيلًا تحفظ به بصرك وقلبك، ونحن هنا لنُعينك عليه."), center = true, color = look.t.deepInk.copy(alpha = 0.85f))
    Grow()
    listOf(
        ia("Closes zina content on this phone", "يحجب محتوى الزنا على هاتفك"),
        ia("Help in the hard minute", "عونٌ في اللحظة الصعبة"),
        ia("Nearer to Allah, every day", "أقرب إلى الله كل يوم")
    ).forEach {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(it, color = look.t.deepInk, fontSize = 14.sp, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 16.dp, vertical = 9.dp))
        }
    }
}

@Composable
private fun ColumnScope.UsagePage(hours: String, usageOn: Boolean, onUsage: () -> Unit, onPick: (String) -> Unit) {
    val look = LocalLook.current
    Title(ia("How many hours a day are you on your phone?", "كم ساعة تقضي على هاتفك في اليوم؟"))
    Sub(ia("A rough guess is fine.", "التقدير التقريبي يكفي."))
    HoursOpts.forEachIndexed { i, o -> NumberedCard(i + 1, ia(o.en, o.ar), o.key == hours) { onPick(o.key) } }
    Row(
        Modifier.fillMaxWidth().hisnCard(look, 16.dp).clickable(enabled = !usageOn, role = Role.Switch, onClick = onUsage).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Timer, null, tint = look.t.pri)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ia("Usage access", "الوصول إلى الاستخدام"), color = look.t.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(ia("For your real numbers, read on this phone, never a guess", "لأرقامك الحقيقية، تُقرأ على هاتفك، لا تخمينًا"), color = look.t.sub, fontSize = 12.5.sp)
        }
        HisnSwitch(usageOn) { if (!usageOn) onUsage() }
    }
}

@Composable
private fun ColumnScope.SlidePage(i: Int) {
    val look = LocalLook.current
    val s = Slides[i]
    Grow()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HisnIcon(s.icon, 150.dp) }
    Title(ia(s.en, s.ar), center = true, size = 27.sp)
    Sub(ia(s.bodyEn, s.bodyAr), center = true, color = look.t.deepInk.copy(alpha = 0.85f))
    Verse(s.verse, verseRef(s.src), true)
    Grow()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(3) { k -> Box(Modifier.padding(3.dp).size(if (k == i) 18.dp else 7.dp, 7.dp).clip(CircleShape).background(if (k == i) Color.White else Color.White.copy(alpha = 0.35f))) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalsHisnPage(chosen: Set<String>, onToggle: (String) -> Unit) {
    val look = LocalLook.current
    Title(ia("Follow your goals", "حدّد أهدافك"))
    Sub(ia("Every path is different. Choose what you want to work on.", "لكلٍّ طريقه. اختر ما تريد أن تعمل عليه."))
    GoalOpts.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { o ->
                val on = o.key in chosen
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(if (on) look.t.chip else look.t.card)
                        .border(1.5.dp, if (on) look.t.pri else look.t.line, RoundedCornerShape(16.dp))
                        .clickable(role = Role.Checkbox) { onToggle(o.key) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HisnIcon(GoalIcons[o.key] ?: "mosque", 34.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(ia(o.en, o.ar), color = look.t.ink, fontSize = 13.5.sp, lineHeight = 18.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(20.dp).clip(CircleShape).then(if (on) Modifier.background(look.t.pri) else Modifier.border(1.5.dp, look.t.line, CircleShape)),
                        contentAlignment = Alignment.Center
                    ) { if (on) Icon(Icons.Rounded.Check, null, tint = look.t.priInk, modifier = Modifier.size(13.dp)) }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

private val GoalIcons = mapOf(
    "pray" to "mosque", "gaze" to "shield", "quran" to "quran", "near" to "hands", "marriage" to "rings", "family" to "home",
    "focus" to "hourglass", "energy" to "sun", "sleep" to "crescent", "fast" to "dates"
)

@Composable
private fun InsteadPage(chosen: Set<String>, onToggle: (String) -> Unit) {
    val look = LocalLook.current
    Title(ia("When the pull comes, what will you do instead?", "حين يأتي الإغراء، ماذا تفعل بدلًا منه؟"))
    Sub(ia("Pick three or more. Hisn offers them in hard moments.", "اختر ثلاثة أو أكثر، يعرضها حصن عليك في اللحظات الصعبة."))
    Column(Modifier.fillMaxWidth().hisnCard(look, 16.dp).padding(14.dp)) {
        Text(rtlLine("«ونفسك إن لم تشغلها بالحق وإلا شغلتك بالباطل.»"), color = look.t.ink, fontSize = 15.sp, lineHeight = 26.sp,
            style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl), modifier = Modifier.fillMaxWidth())
        if (!isArabicUi()) Text("If you do not keep your soul busy with what is true, it will keep you busy with what is false.", color = look.t.sub, fontSize = 12.5.sp)
        Text(ia("al-Shāfiʿī, quoted in al-Jawāb al-Kāfī, p. 156", "الشافعي، أورده ابن القيم في الجواب الكافي، ص 156"), color = look.t.acc, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    }
    InsteadOpts.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { o ->
                val on = o.key in chosen
                Row(
                    Modifier.weight(1f).heightIn(min = 60.dp).hisnCard(look, 16.dp, if (on) look.t.soft else look.t.card)
                        .border(1.5.dp, if (on) look.t.pri else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable(role = Role.Checkbox) { onToggle(o.key) }.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HisnIcon(o.icon, 36.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(ia(o.en, o.ar), color = look.t.ink, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReasonHisnPage(reason: String, onChange: (String) -> Unit) {
    val look = LocalLook.current
    Title(ia("Write one line to yourself for a hard minute", "اكتب سطرًا لنفسك تقرؤه في اللحظة الصعبة"))
    Sub(ia("You will see it first when you tap Help now.", "ستراه أولًا حين تضغط «ساعدني الآن»."))
    Field(reason, ia("I leave this out of fear of Allah and love for Him.", "أترك هذا خوفًا من الله وحبًّا له."), single = false, onChange = onChange)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            // Between fear of Allah and love for Him, as the owner asked; not worldly reasons.
            ia("Out of fear of Allah", "خوفًا من الله"), ia("Out of love for Allah", "حبًّا لله"),
            ia("Hoping for His mercy and Paradise", "رجاءً في رحمته وجنّته"), ia("Shy before Him who sees me", "حياءً ممّن يراني")
        ).forEach {
            Text(it, color = look.t.ink, fontSize = 13.5.sp, modifier = Modifier.clip(CircleShape).background(look.t.card)
                .border(1.dp, look.t.line, CircleShape).clickable(role = Role.Button) { onChange(it) }.padding(horizontal = 13.dp, vertical = 9.dp))
        }
    }
}

@Composable
private fun ToldPage(name: String, targets: Set<String>, hardest: String, prayers: String) {
    val look = LocalLook.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Title(ia("Analysis done", "اكتمل التحليل")) }
        Icon(Icons.Rounded.Check, null, tint = look.t.kept, modifier = Modifier.size(28.dp))
    }
    Sub(if (name.isEmpty()) ia("This is what you told us, and what Hisn will do.", "هذا ما أخبرتنا به، وهذا ما سيفعله حصن.")
    else ia("\u200E$name, this is what you told us, and what Hisn will do.", "يا $name، هذا ما أخبرتنا به، وهذا ما سيفعله حصن."))
    val rows = buildList {
        when (hardest) {
            "night" -> add(Triple(Icons.Rounded.NightsStay, ia("Nights are hardest", "الليل أصعب أوقاتك"), ia("Quiet hours in Rules close the feeds from ʿIshāʾ to Fajr, so the night is for sleep", "الساعات الهادئة في القواعد تُغلق المقاطع من بعد العشاء إلى الفجر، فيبقى ليلك للنوم")))
            "stressed" -> add(Triple(Icons.Rounded.Favorite, ia("Hard days pull you", "اليوم المتعب يضعفك"), ia("«Help now» is the red button: wuḍūʾ, dhikr, and the reason you wrote", "«ساعدني الآن» هو الزر الأحمر: وضوء وذكر والسبب الذي كتبته أنت")))
            "waking" -> add(Triple(Icons.Rounded.Schedule, ia("Waking up is your weak hour", "أول الصباح وقت ضعفك"), ia("Morning adhkār are the first thing on home, before any feed", "أذكار الصباح أول ما تراه في الرئيسية، قبل أي مقطع")))
            "home" -> add(Triple(Icons.Rounded.Shield, ia("Alone at home is hardest", "الخلوة في البيت أصعب أوقاتك"), ia("The shield does not need anyone watching: it stays closed when you are alone", "الدرع لا يحتاج من يراقبك، فيبقى مغلقًا وأنت وحدك")))
            "sad" -> add(Triple(Icons.Rounded.Favorite, ia("Sadness pulls you", "الحزن يضعفك"), ia("«Help now» gives you wuḍūʾ and two rakʿahs before the screen", "«ساعدني الآن» يذكّرك بالوضوء وركعتين قبل الشاشة")))
            "travel" -> add(Triple(Icons.Rounded.Language, ia("Travel loosens your guard", "السفر يُضعف حراستك"), ia("The lock keeps what you closed shut, wherever you are", "القفل يُبقي ما أغلقته مغلقًا أينما كنت")))
            "bored" -> add(Triple(Icons.Rounded.PauseCircle, ia("Boredom opens the feed", "الملل يفتح المقاطع"), ia("A short wait with dhikr before the apps you choose, so you open them on purpose", "انتظار قصير مع ذكر قبل التطبيقات التي تختارها، فلا تفتحها إلا عن قصد")))
        }
        when (prayers) {
            "all" -> add(Triple(Icons.Rounded.Schedule, ia("Your prayers are steady", "صلواتك ثابتة"), ia("Prayer times on home: mark each one with a tap and keep your count", "مواقيت الصلاة في الرئيسية: علّم كل صلاة بلمسة واحفظ عدّك")))
            "most", "few" -> add(Triple(Icons.Rounded.Schedule, ia("Most prayers, not all", "معظم الصلوات، لا كلها"), ia("The next prayer counts down on home, so none passes unnoticed", "عدّ تنازلي للصلاة التالية في الرئيسية، فلا تفوتك صلاة دون أن تنتبه")))
            "again" -> add(Triple(Icons.Rounded.Schedule, ia("You want to start again", "تريد أن تبدأ من جديد"), ia("One prayer at a time, each marked on home", "صلاة بعد صلاة، تُعلَّم كل واحدة في الرئيسية")))
        }
        if ("adult" in targets || targets.isEmpty()) add(Triple(Icons.Rounded.Shield, ia("Your gaze", "بصرك"), ia("Zina sites, searches, words and images are closed before you see them", "تُغلق مواقع الزنا وبحثه وكلماته وصوره قبل أن تراها")))
        if ("shorts" in targets || "feeds" in targets) add(Triple(Icons.Rounded.Timer, ia("Your time", "وقتك"), ia("Shorts and Reels closed; the rest of each app keeps working", "تُغلق الشورتس والريلز، ويبقى باقي التطبيق يعمل")))
        if ("games" in targets || "series" in targets) add(Triple(Icons.Rounded.Timer, ia("Your hours", "ساعاتك"), ia("A daily limit on the games and shows you choose, in Rules", "حدّ يومي للألعاب والمسلسلات التي تختارها، من القواعد")))
        if ("chats" in targets) add(Triple(Icons.Rounded.Title, ia("Your chats", "محادثاتك"), ia("Zina words are closed inside chat apps too", "تُغلق كلمات الزنا داخل تطبيقات المحادثة أيضًا")))
        add(Triple(Icons.Rounded.Favorite, ia("You may feel far from Allah after", "قد تشعر بالبعد عن الله بعدها"), ia("The door back is always near: no reset, no shame", "باب العودة قريب منك دائمًا: لا تصفير ولا تعيير")))
    }
    rows.forEach { (icon, a, b) ->
        Row(Modifier.fillMaxWidth().hisnCard(look, 16.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(look.t.soft), contentAlignment = Alignment.Center) { Icon(icon, null, tint = look.t.pri, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a, color = look.t.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(b, color = look.t.sub, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun HisnSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val look = LocalLook.current
    Switch(
        checked = on, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(checkedTrackColor = look.t.kept, checkedThumbColor = Color.White, uncheckedTrackColor = look.t.soft, uncheckedBorderColor = look.t.line, uncheckedThumbColor = look.t.sub)
    )
}

@Composable
private fun PermRow(icon: ImageVector, title: String, body: String, needed: Boolean, on: Boolean, starting: Boolean = false, onClick: () -> Unit) {
    val look = LocalLook.current
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch, onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(look.t.soft), contentAlignment = Alignment.Center) { Icon(icon, null, tint = look.t.pri, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = look.t.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (needed) {
                    Spacer(Modifier.width(6.dp))
                    Text(ia("needed", "ضروري"), color = look.t.acc, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).background(look.t.soft).padding(horizontal = 7.dp, vertical = 1.dp))
                }
            }
            Text(if (starting) ia("Starting…", "يبدأ…") else body, color = look.t.sub, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
        HisnSwitch(on) { onClick() }
    }
}

@Composable
private fun PermsPage(
    states: Map<String, OnboardingProtectionState>, optional: Map<String, Boolean>, checks: Map<String, Boolean>,
    directBuild: Boolean, guardOn: Boolean, actions: OnboardingActions
) {
    val look = LocalLook.current
    var askAll by remember { mutableStateOf(false) }
    if (askAll) ScanAllDialog(onConfirm = { askAll = false; actions.setCheck("allapps", true) }, onDismiss = { askAll = false })
    Title(ia("Let Hisn stand guard", "دع حصنًا يقف حارسًا"))
    Sub(ia("Two are needed. The rest make it stronger.", "اثنان ضروريان، والباقي يزيده قوة."))
    val app = states.getValue("accessibility")
    val dns = states.getValue("dns")
    Column(Modifier.fillMaxWidth().hisnCard(look, 18.dp).padding(vertical = 4.dp)) {
        PermRow(Icons.Rounded.Shield, ia("App protection", "حماية التطبيقات"), ia("Reads words and closes the apps and parts you chose", "يقرأ الكلمات ويغلق التطبيقات والأجزاء التي اخترتها"),
            true, app == OnboardingProtectionState.ON, app == OnboardingProtectionState.STARTING) { actions.request("accessibility") }
        PermRow(Icons.Rounded.Language, ia("Website filter", "مرشّح المواقع"), ia("A local VPN that answers adult sites with nothing", "شبكة محلية لا تُجيب مواقع الزنا"),
            true, dns == OnboardingProtectionState.ON, dns == OnboardingProtectionState.STARTING) { actions.request("dns") }
        PermRow(Icons.Rounded.Timer, ia("Usage access", "الوصول إلى الاستخدام"), ia("Your real numbers and daily limits", "أرقامك الحقيقية والحدود اليومية"),
            false, optional["usage"] == true) { actions.request("usage") }
        PermRow(Icons.Rounded.Notifications, ia("Notifications", "الإشعارات"), ia("Prayer and reflection reminders", "تذكير الصلاة والمحاسبة"),
            false, optional["notifications"] == true) { actions.request("notifications") }
        PermRow(Icons.Rounded.BatteryChargingFull, ia("Battery", "البطارية"), ia("So Android doesn't stop Hisn at night", "حتى لا يوقف أندرويد حصنًا ليلًا"),
            false, optional["battery"] == true) { actions.request("battery") }
    }
    Text(ia("Inside apps and browsers", "داخل التطبيقات والمتصفحات"), color = look.t.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Column(Modifier.fillMaxWidth().hisnCard(look, 18.dp).padding(vertical = 4.dp)) {
        PermRow(Icons.Rounded.Title, ia("Adult words", "كلمات الزنا"), ia("Searches and pages with adult words are closed", "يُغلق البحث والصفحات ذات الكلمات الإباحية"),
            false, checks["tags"] == true) { actions.setCheck("tags", checks["tags"] != true) }
        PermRow(Icons.Rounded.Image, ia("Images", "الصور"), ia("Pictures checked on this phone, never uploaded", "تُفحص الصور على هاتفك ولا تُرفع"),
            false, checks["images"] == true) { actions.setCheck("images", checks["images"] != true) }
        PermRow(Icons.Rounded.Shield, ia("Every app", "كل التطبيقات"), ia("Words and images checked in every app, not only the ones you chose", "تُفحص الكلمات والصور في كل تطبيق، لا في التي اخترتها فقط"),
            false, checks["allapps"] == true) { if (checks["allapps"] == true) actions.setCheck("allapps", false) else askAll = true }
        if (directBuild) PermRow(Icons.Rounded.Lock, ia("Uninstall guard", "حماية من الحذف"), ia("Hisn can't be removed in a weak minute", "لا يُحذف حصن في لحظة ضعف"),
            false, guardOn) { if (!guardOn) actions.setGuard() }
    }
}

@Composable
private fun TryPage(states: Map<String, OnboardingProtectionState>, tried: Boolean, onTry: () -> Unit) {
    val look = LocalLook.current
    Title(ia("See it work before you trust it", "جرّبه قبل أن تثق به"))
    Sub(ia("Your walls, as they stand right now, and the pause you will see before a feed.", "أسوارك كما هي الآن، والوقفة التي ستراها قبل أي تصفّح."))
    listOf(
        Triple("shield", ia("Website filter", "مرشّح المواقع"), states.getValue("dns")),
        Triple("padlock", ia("App protection", "حماية التطبيقات"), states.getValue("accessibility"))
    ).forEach { (icon, label, state) ->
        val on = state == OnboardingProtectionState.ON
        Row(
            Modifier.fillMaxWidth().hisnCard(look, 16.dp).border(1.5.dp, if (on) look.t.kept else look.t.help.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HisnIcon(icon, 38.dp)
            Spacer(Modifier.width(12.dp))
            Text(label, color = look.t.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (on) ia("Standing", "قائم") else if (state == OnboardingProtectionState.STARTING) ia("Starting", "يبدأ") else ia("Off", "متوقف"),
                color = if (on) look.t.kept else look.t.help, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
    Row(
        Modifier.fillMaxWidth().hisnCard(look, 16.dp).clickable(role = Role.Button, onClick = onTry).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HisnIcon("hourglass", 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ia("Try the pause before an app", "جرّب الوقفة قبل التطبيق"), color = look.t.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(if (tried) ia("Seen. You can set it for any app in Rules.", "رأيتها. يمكنك ضبطها لأي تطبيق من القواعد.")
            else ia("Five seconds with dhikr, as it will appear", "خمس ثوانٍ مع الذكر، كما ستظهر لك"), color = look.t.sub, fontSize = 12.5.sp)
        }
        Icon(Icons.Rounded.TouchApp, null, tint = look.t.pri)
    }
}

@Composable
private fun ColumnScope.BeginPage(name: String) {
    val look = LocalLook.current
    Spacer(Modifier.height(16.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HisnIcon("crescent", 72.dp) }
    Title(if (name.isEmpty()) ia("Today is a new beginning", "اليوم بداية جديدة") else ia("\u200E$name, today is a new beginning", "يا $name، اليوم بداية جديدة"), center = true)
    Sub(ia("Five short promises, to Allah and to yourself. Each one has a verse.", "خمسة عهود قصيرة، مع الله ومع نفسك، ومع كل عهد آية."), center = true, color = look.t.deepInk.copy(alpha = 0.85f))
    Grow()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HisnIcon("hands", 170.dp) }
    Grow()
}

@Composable
private fun ColumnScope.PledgePage(index: Int, onPromise: () -> Unit) {
    val look = LocalLook.current
    val p = Pledges[index]
    Text(ia("Promise ${index + 1} / 5", "العهد ${nums(index + 1)} من ${nums(5)}"), color = look.t.sub, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Grow()
    Title(ia(p.en, p.ar), center = true, size = 24.sp)
    Verse(p.verse, if (isArabicUi()) verseRef(p.src) else "${p.meaning} · ${p.src}", false)
    Grow(2f)
    androidx.compose.runtime.key(index) { HoldToPromiseHisn(onPromise) }
}

@Composable
private fun HoldToPromiseHisn(onDone: () -> Unit) {
    val look = LocalLook.current
    var holding by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val fill = remember { Animatable(0f) }
    LaunchedEffect(holding) {
        if (holding && !done) {
            fill.animateTo(1f, tween(((1f - fill.value) * 1400).toInt(), easing = LinearEasing))
            done = true
            delay(350)
            onDone()
        } else if (!done) fill.animateTo(0f, tween(160))
    }
    val label = if (done) ia("Promised", "عاهدت") else ia("Hold to promise", "اضغط مطوّلًا لتعاهد")
    val fillColor = look.t.priInk.copy(alpha = 0.28f)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CircleShape).background(look.t.pri)
            .drawBehind {
                val w = size.width * fill.value
                val left = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f
                drawRect(fillColor, topLeft = Offset(left, 0f), size = Size(w, size.height))
            }
            .semantics(mergeDescendants = true) { role = Role.Button; onClick(label) { done = true; onDone(); true } }
            .pointerInput(Unit) { detectTapGestures(onPress = { holding = true; tryAwaitRelease(); holding = false }) }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (done) Icons.Rounded.Check else Icons.Rounded.TouchApp, null, tint = look.t.priInk)
            Text(label, color = look.t.priInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlanHisnPage(name: String) {
    val look = LocalLook.current
    val end = LocalDate.now().plusDays(40)
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, tint = look.t.kept, modifier = Modifier.size(32.dp)) }
    Title(if (name.isEmpty()) ia("Here is your plan", "هذه خطّتك") else ia("\u200E$name, here is your plan", "يا $name، هذه خطّتك"), center = true)
    Column(Modifier.fillMaxWidth().hisnCard(look, 18.dp).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(ia("If every night is clean", "إن صفت كل ليلة"), color = look.t.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(hijriOf(end).label(withYear = true), color = look.t.pri, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = serif)
        Text(gregLabel(context, end, withYear = true), color = look.t.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(ia("40 nights of light · a slip doesn't reset the plan. This is counting, not a promise.", "أربعون ليلة من النور، والزلّة لا تُعيد الخطة من الصفر. هذا عدٌّ لا وعد."),
            color = look.t.sub, fontSize = 12.5.sp, textAlign = TextAlign.Center)
    }
    listOf(
        Triple("1–7", ia("Close the doors", "أغلق الأبواب"), ia("Walls up, quiet nights", "الأسوار قائمة، والليالي هادئة")),
        Triple("8–14", ia("Fill the time", "املأ الوقت"), ia("Your Instead list", "قائمة البدائل")),
        Triple("15–30", ia("Build the habit", "ابنِ العادة"), ia("Fajr and one page a day", "الفجر وصفحة كل يوم")),
        Triple("31–40", ia("Steady", "الثبات"), ia("Your first milestone: the crescent", "أول محطة لك: الهلال"))
    ).forEach { (n, a, b) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(nums(n), color = look.t.pri, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.width(58.dp).clip(RoundedCornerShape(12.dp)).background(look.t.soft).padding(vertical = 10.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(a, color = look.t.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(b, color = look.t.sub, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ColumnScope.NotifyPage() {
    val look = LocalLook.current
    Spacer(Modifier.height(10.dp))
    listOf(
        Triple(ia("Fajr soon", "الفجر قريبًا"), ia("Start the day with the One who keeps you.", "ابدأ يومك مع من يحفظك."), ia("now", "الآن")),
        Triple(ia("Verse for today", "آية اليوم"), "أَلَمْ يَعْلَم بِأَنَّ ٱللَّهَ يَرَىٰ", nums("07:00")),
        Triple(ia("Seven nights", "سبع ليالٍ"), ia("A full week, alḥamdulillāh. Tap to see it.", "أسبوع كامل، الحمد لله. اضغط لتراه."), ia("yesterday", "أمس"))
    ).forEachIndexed { i, (a, b, t) ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = (i * 8).dp).hisnCard(look, 18.dp, look.t.card.copy(alpha = 0.94f)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(look.t.deep), contentAlignment = Alignment.Center) { HisnGateMark(20.dp, look.t.deepInk) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(a, color = look.t.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(b, color = look.t.sub, fontSize = 13.sp)
            }
            Text(t, color = look.t.sub, fontSize = 11.sp)
        }
    }
    Grow()
    Title(ia("Let us help you stay strong", "دعنا نُعينك على الثبات"), center = true)
    Sub(ia("Prayer and reflection reminders. Gentle, never more than four a day.", "تذكير بالصلاة والمحاسبة. لطيفة، ولا تزيد على أربعة في اليوم."), center = true)
    Grow()
}

@Composable
private fun ColumnScope.FreePage() {
    Grow()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HisnIcon("medal", 150.dp) }
    Title(ia("Hisn is free", "حصن مجاني"), center = true, size = 30.sp)
    Sub(ia("No ads, no subscription.", "بلا إعلانات ولا اشتراك."), center = true)
    Grow(2f)
}
