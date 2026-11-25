package app.hisn.android.nativeshell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The Islamic edition's onboarding pages that tell the person what is happening to them, what Hisn
 * does about it, and which look they want. They sit inside the shared Onboarding flow, which keeps
 * the permission setup.
 */

private val PULLS = mapOf(
    "adult" to ("Adult content" to "محتوى الزنا"),
    "shorts" to ("Short videos" to "المقاطع القصيرة"),
    "feeds" to ("The phone in bed" to "تصفّح الهاتف في السرير"),
    "time" to ("The phone at prayer" to "الهاتف وقت الصلاة"),
    "games" to ("Games" to "الألعاب"),
    "chats" to ("Chats" to "المحادثات"),
    "series" to ("Series and films" to "المسلسلات والأفلام")
)

private val WHEN_HARD = mapOf(
    "night" to Triple("Late at night", "ليلًا", "when the house is quiet and the phone is in your hand" to "حين يهدأ البيت ويكون الهاتف في يدك"),
    "alone" to Triple("When alone", "حين تكون وحدك", "when nobody sees but Allah" to "حين لا يراك إلا الله"),
    "bored" to Triple("When bored", "عند الفراغ", "when there is nothing to do and the feed is one tap away" to "حين لا شيء تفعله والمقاطع على بُعد لمسة"),
    "stressed" to Triple("After a hard day", "بعد يوم صعب", "when you are tired and want to numb the day" to "حين تتعب وتبحث عمّا يُنسيك يومك"),
    "waking" to Triple("On waking", "عند الاستيقاظ", "when the phone is the first thing in your hand, before the day has begun" to "حين يكون الهاتف أول ما تمسكه قبل أن يبدأ يومك"),
    "home" to Triple("Alone at home", "وحدك في البيت", "when nobody is around and nothing stands between you and the screen" to "حين لا يكون حولك أحد، ولا شيء بينك وبين الشاشة"),
    "sad" to Triple("After a quarrel or sadness", "بعد خلاف أو حزن", "when the heart is sore and looks for something to forget with" to "حين يضيق صدرك وتبحث عمّا تنسى به"),
    "travel" to Triple("Travelling or on holiday", "في السفر أو الإجازة", "when the usual day is gone and with it the usual guards" to "حين يغيب يومك المعتاد ويغيب معه ما كان يحفظك"),
    "morning" to Triple("In the morning", "في الصباح", "before the day has started" to "قبل أن يبدأ يومك")
)

private val CYCLE = listOf(
    Triple("A glance", "نظرة", ("The shield closes adult sites, words and images" to "الدرع يغلق المواقع والكلمات والصور")),
    Triple("A thought", "خطرة", ("Help now: your promise, dhikr, a breath" to "ساعدني الآن: عهدك، وذكر، ونَفَس")),
    Triple("A wish", "شهوة", ("Open Wait: dhikr before a feed opens" to "انتظار الفتح: ذكر قبل فتح المقاطع")),
    Triple("The act", "الفعل", ("The lock keeps closed what you closed" to "القفل يُبقي مغلقًا ما أغلقته")),
    Triple("Regret", "الندم", ("Coming back: no reset, the door open" to "العودة: لا تصفير، والباب مفتوح"))
)

private const val CHAIN = "دافع الخطرة؛ فإن لم تفعل صارت فكرةً؛ فدافع الفكرة؛ فإن لم تفعل صارت شهوةً؛ فحاربها؛ فإن لم تفعل صارت عزيمةً وهمّةً؛ فإن لم تدافعها صارت فعلًا؛ فإن لم تتداركه بضدّه صار عادةً، فيصعب عليك الانتقال عنها."

@Composable
private fun OnbCard(content: @Composable () -> Unit) {
    val look = LocalLook.current
    Column(Modifier.fillMaxWidth().hisnCard(look, 16.dp).padding(14.dp)) { content() }
}

/** "This is what is happening": the person's own answers, the cost, and the five-step cycle. */
@Composable
internal fun IslamicDiagnosisPage(name: String, targets: Set<String>, hours: String, triggers: Set<String>) {
    val look = LocalLook.current
    Text(ia("What is happening", "هذا ما يحدث معك"), color = look.t.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Text(
        if (name.isBlank()) ia("It is not that you are weak. It is a cycle.", "ليست المشكلة أنك ضعيف، إنها دائرة.")
        else ia("\u200E$name, it is not that you are weak. It is a cycle.", "يا $name، ليست المشكلة أنك ضعيف، إنها دائرة."),
        color = look.t.ink, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold
    )
    val pulls = (if (targets.isEmpty()) setOf("adult") else targets).mapNotNull { PULLS[it] }.map { ia(it.first, it.second) }
    OnbCard {
        Text(ia("What pulls you", "ما يجذبك"), color = look.t.sub, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        Text(pulls.joinToString(ia(", ", "، ")), color = look.t.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
    val hard = triggers.mapNotNull { WHEN_HARD[it] }
    if (hard.isNotEmpty()) OnbCard {
        Text(ia("When it is hardest", "متى يصعب"), color = look.t.sub, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        Text(hard.joinToString(ia(", ", "، ")) { ia(it.first, it.second) }, color = look.t.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(ia(hard.first().third.first, hard.first().third.second), color = look.t.sub, fontSize = 12.5.sp)
    }
    val perDay = hoursPerDay(hours)
    if (perDay > 0) OnbCard {
        val days = Math.round(perDay * 365 / 24).toInt()
        Text(ia("What it costs", "ما يكلّفك"), color = look.t.sub, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        Text(ia("About $days full days a year", "قرابة ${nums(days)} يومًا كاملًا في السنة"), color = look.t.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(ia("Hours that could be Qurʾān, sleep, family, and Fajr on time.", "ساعات كان يمكن أن تكون قرآنًا ونومًا وأهلًا وفجرًا في وقته."), color = look.t.sub, fontSize = 12.5.sp)
    }
    Text(ia("Every slip follows the same five steps", "كل زلّة تمرّ بالخطوات الخمس نفسها"), color = look.t.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth().hisnCard(look, 16.dp).padding(vertical = 10.dp, horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        CYCLE.forEachIndexed { i, (en, ar, _) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(if (i == CYCLE.lastIndex) Color(0xFFFBE3E3) else look.t.soft),
                    contentAlignment = Alignment.Center
                ) { Text(nums(i + 1), color = if (i == CYCLE.lastIndex) Color(0xFFC24848) else look.t.pri, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(4.dp))
                Text(ia(en, ar), color = look.t.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
    }
    Column(Modifier.fillMaxWidth().hisnCard(look, 16.dp).padding(14.dp)) {
        Text(rtlLine("«$CHAIN»"), color = look.t.ink, fontSize = 16.sp, lineHeight = 28.sp,
            style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl), modifier = Modifier.fillMaxWidth())
        Text(ia("Ibn al-Qayyim · al-Fawāʾid, p. 45", "ابن القيم · الفوائد، ص 45"), color = look.t.acc, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    }
    Text(ia("The earlier you stop it, the easier it is. That is what Hisn is built for.", "وكلما أوقفتها أبكر كان أسهل. ولهذا صُنع حصن."), color = look.t.sub, fontSize = 13.5.sp)
}

private val REMEDY = listOf(
    Remedy("adult", "shield", "Adult content finds you" to "المحتوى الإباحي يصل إليك",
        "A shield in every app and browser: adult sites, searches, words and images closed, even in private tabs." to "درع في كل تطبيق ومتصفح: تُغلق المواقع والبحث والكلمات والصور، حتى في التصفّح الخفي."),
    Remedy("shorts", "hourglass", "Feeds take your hours" to "المقاطع تأخذ ساعاتك",
        "Short-video feeds closed, and Open Wait asks for dhikr before any feed opens." to "تُغلق المقاطع القصيرة، وينتظرك «انتظار الفتح» بذكر قبل أي تصفّح."),
    Remedy("night", "lantern", "Nights alone with the phone" to "الليل وحدك مع الهاتف",
        "Quiet hours: the risky apps sleep at the times you choose." to "ساعات هادئة: تنام التطبيقات الخطرة في الأوقات التي تختارها."),
    Remedy("", "hands", "The hard minute" to "اللحظة الصعبة",
        "One red button: your own promise, a verse, dhikr with your breath, then wuḍūʾ and two rakʿahs." to "زرّ أحمر واحد: عهدك أنت، وآية، وذكر مع أنفاسك، ثم وضوء وركعتان."),
    Remedy("", "ibrik", "After a slip" to "بعد الزلّة",
        "No reset to zero. Three steps back, and your kept nights stay." to "لا تصفير للعدّاد. ثلاث خطوات للعودة، ولياليك المحفوظة باقية."),
    Remedy("", "quran", "An empty heart" to "قلب فارغ",
        "Each day a saying from Ibn al-Qayyim, Ibn al-Jawzī and Ibn Rajab, adhkār with a counter, and a one-minute reflection." to "كل يوم قول من ابن القيم وابن الجوزي وابن رجب، وأذكار بعدّاد، ومحاسبة في دقيقة.")
)

private data class Remedy(val key: String, val icon: String, val problem: Pair<String, String>, val answer: Pair<String, String>)

/** "The way out": each problem with what Hisn does about it, the person's own first. */
@Composable
internal fun IslamicWayOutPage(targets: Set<String>, triggers: Set<String>) {
    val look = LocalLook.current
    fun mine(r: Remedy) = r.key.isNotEmpty() && (r.key in targets || r.key in triggers ||
        (r.key == "shorts" && ("feeds" in targets || "time" in targets)) || (r.key == "adult" && targets.isEmpty()))
    Text(ia("The way out", "المخرج"), color = look.t.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Text(ia("Here is what Hisn does for you", "هذا ما يفعله حصن لأجلك"), color = look.t.ink, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
    (REMEDY.filter { mine(it) } + REMEDY.filterNot { mine(it) }).forEach { r ->
        Row(
            Modifier.fillMaxWidth().hisnCard(look, 16.dp)
                .then(if (mine(r)) Modifier.border(1.5.dp, look.t.acc, RoundedCornerShape(16.dp)) else Modifier)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HisnIcon(r.icon, 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ia(r.problem.first, r.problem.second), color = look.t.sub, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    if (mine(r)) {
                        Spacer(Modifier.width(6.dp))
                        Text(ia("for you", "لك"), color = Color.White, fontSize = 10.5.sp, modifier = Modifier.clip(RoundedCornerShape(50)).background(look.t.acc).padding(horizontal = 7.dp, vertical = 1.dp))
                    }
                }
                Text(ia(r.answer.first, r.answer.second), color = look.t.ink, fontSize = 13.5.sp, lineHeight = 19.sp)
            }
        }
    }
    Text(ia("Where Hisn breaks the cycle", "أين يكسر حصن الدائرة"), color = look.t.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    CYCLE.forEachIndexed { i, (en, ar, how) ->
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(look.t.kept), contentAlignment = Alignment.Center) {
                Text(nums(i + 1), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(ia(en, ar) + " · " + ia(how.first, how.second), color = look.t.sub, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
    Text(ia("You do the turning. Allah gives the strength. Hisn only closes the doors.", "أنت تتوب، والله يعين، وحصن يغلق الأبواب فقط."),
        color = look.t.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(look.t.soft).padding(14.dp))
}

/** "Which look feels right?": the whole app changes as soon as one is tapped. */
@Composable
internal fun IslamicLookPage() {
    val look = LocalLook.current
    val context = LocalContext.current
    Text(ia("Which look feels right?", "أيّ طابع يريحك؟"), color = look.t.ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    Text(ia("The whole app takes it. You can change it any time in Tools.", "يتلوّن به التطبيق كلّه، ويمكنك تغييره متى شئت من الأدوات."), color = look.t.sub, fontSize = 14.sp)
    HisnLook.entries.forEach { l ->
        val on = l == look
        val bg = remember(l) { HisnArt.id(context, l.backgroundName) }
        Row(
            Modifier.fillMaxWidth().hisnCard(look, 18.dp)
                .border(2.dp, if (on) look.t.acc else Color.Transparent, RoundedCornerShape(18.dp))
                .clickable(role = Role.RadioButton) { HisnLooks.set(context, l) }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(width = 64.dp, height = 80.dp).clip(RoundedCornerShape(12.dp)).background(l.t.sky)) {
                if (bg != 0) Image(painterResource(bg), null, contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter, modifier = Modifier.matchParentSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(when (l) { HisnLook.FAJR -> ia("Fajr", "الفجر"); HisnLook.MADINAH -> ia("Madinah", "المدينة"); HisnLook.LAYL -> ia("Layl", "الليل") },
                    color = look.t.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(when (l) {
                    HisnLook.FAJR -> ia("Dawn rose and indigo", "وردي الفجر والنيلي")
                    HisnLook.MADINAH -> ia("Ivory, emerald and gold", "عاجي وزمردي وذهبي")
                    HisnLook.LAYL -> ia("Night indigo and lamp gold", "نيلي الليل وذهب السراج")
                }, color = look.t.sub, fontSize = 12.5.sp)
            }
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(if (on) look.t.acc else Color.Transparent).border(1.5.dp, if (on) look.t.acc else look.t.line, CircleShape),
                contentAlignment = Alignment.Center
            ) { if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
        }
    }
}
