package app.hisn.android.nativeshell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisn.core.Sayings
import kotlinx.coroutines.delay
import java.time.LocalDate

/*
 * The Islamic edition's moments, drawn as the Hisn mockup draws them: a page closed (deep, calm,
 * the hadith on the sudden glance), a feed or app closed (light, the hours it takes), the few
 * seconds before an app opens (dhikr while you wait), and the breathing pause (istighfār with
 * the breath, then wuḍūʾ and two rakʿahs). Each keeps every action the shared screens had.
 */

/** One saying for this moment, steady for the day, from the bundled sayings. */
@Composable
private fun momentSaying(moment: Sayings.Moment, max: Int = 220): Sayings.Saying? {
    val sayings by rememberSayings()
    return remember(sayings, moment) {
        // Sayings tied to a season (Ramaḍān, "this month") would read oddly on an ordinary day.
        val pool = sayings?.moment(moment)?.filter { it.text.length <= max && Seasonal.none { w -> w in it.text } }.orEmpty()
        if (pool.isEmpty()) null else pool[(LocalDate.now().toEpochDay() % pool.size).toInt()]
    }
}

/** A quote on a translucent card, for deep and light backgrounds alike. */
@Composable
private fun QuoteCard(text: String, source: String, light: Boolean) {
    val look = LocalLook.current
    val ink = if (light) look.t.deepInk else look.t.ink
    // Set like a page of the book: a rule above and below, no card, no tint.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.25f)))
        Text(rtlLine("«$text»"), color = ink, fontSize = 17.sp, lineHeight = 31.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, textAlign = TextAlign.Center,
            style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl), modifier = Modifier.fillMaxWidth())
        Text(if (isArabicUi()) rtlLine("— $source") else "— $source", color = ink.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.25f)))
    }
}

@Composable
private fun SayingCard(s: Sayings.Saying?, light: Boolean) {
    if (s == null) return
    val author = if (isArabicUi()) s.book.authorAr else s.book.authorEn
    QuoteCard(s.text, "$author · ${s.book.title}، ${ia("p.", "ص")} ${nums(s.pageLabel())}", light)
}

@Composable
private fun MomentButton(label: String, white: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val look = LocalLook.current
    val bg = if (white) Color.White else look.t.pri
    val fg = if (white) look.t.deep else look.t.priInk
    Text(
        label, color = fg.copy(alpha = if (enabled) 1f else 0.5f), fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(28.dp)).background(bg.copy(alpha = if (enabled) 1f else 0.45f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 16.dp)
    )
}

@Composable
private fun MomentLink(label: String, color: Color, onClick: () -> Unit) {
    Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 13.dp))
}

/** The frame: sky or deep, the look's hills at the bottom, a centred column, actions pinned below. */
@Composable
private fun MomentFrame(deep: Boolean, actions: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    val bg = remember(look) { HisnArt.id(context, look.backgroundName) }
    Box(Modifier.fillMaxSize().background(if (deep) look.t.deep else look.t.sky)) {
        if (bg != 0) {
            Image(painterResource(bg), null, contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter,
                alpha = if (deep) 0.5f else 1f, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.45f).align(Alignment.BottomCenter))
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.45f).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(if (deep) look.t.deep else look.t.sky, Color.Transparent), endY = 240f)))
        }
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize().align(Alignment.TopCenter).statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 28.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), content = content
            )
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = actions)
        }
    }
}

// ---------------------------------------------------------------- a page or feed was closed

/**
 * The block screen in the Islamic edition. [adult] pages get the deep page with the hadith on the
 * sudden glance; feeds, apps, limits and quiet hours get the light page with what still works.
 */
@Composable
fun HisnBlockMoment(
    adult: Boolean,
    title: String,
    body: String,
    note: String,
    tally: Int,
    restLabel: String?,
    onHome: () -> Unit,
    onBreathe: () -> Unit,
    onRest: (() -> Unit)?
) {
    val look = LocalLook.current
    val time = momentSaying(Sayings.Moment.TIME)
    MomentFrame(
        deep = adult,
        actions = {
            if (adult) {
                MomentButton(ia("Breathe with dhikr", "تنفّس مع الذكر"), white = true, onClick = onBreathe)
                MomentLink(ia("Back to home", "العودة إلى الرئيسية"), look.t.deepInk, onHome)
            } else {
                if (onRest != null && restLabel != null) MomentButton(ia("Back to the rest of $restLabel", "العودة إلى بقية $restLabel"), white = false, onClick = onRest)
                else MomentButton(ia("Back to home", "العودة إلى الرئيسية"), white = false, onClick = onHome)
                MomentLink(ia("Breathe with dhikr", "تنفّس مع الذكر"), Color.White.copy(alpha = 0.92f), onBreathe)
                if (onRest != null) MomentLink(ia("Do something else", "افعل شيئًا آخر"), Color.White.copy(alpha = 0.92f), onHome)
            }
        }
    ) {
        HisnIcon(if (adult) "shield" else "hourglass", 104.dp)
        Text(title, color = if (adult) look.t.deepInk else look.t.ink, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading(); liveRegion = LiveRegionMode.Polite })
        Text(
            if (adult) ia("Lower your gaze for a moment. Nothing about it was saved.", "غُضّ بصرك لحظة. لم يُحفظ شيء عمّا ظهر.") else body,
            color = if (adult) look.t.deepInk.copy(alpha = 0.85f) else look.t.sub, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (note.isNotEmpty()) {
            Text(ia("“$note”", rtlLine("«$note»")), color = if (adult) look.t.deepInk else look.t.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (adult) Color.White.copy(alpha = 0.1f) else look.t.card).padding(14.dp))
        }
        if (adult) {
            QuoteCard("سألتُ رسول الله ﷺ عن نظر الفُجاءة، فأمرني أن أصرف بصري.", ia("Jarīr, narrated by Muslim 2159", "عن جرير، رواه مسلم 2159"), light = true)
            Text(ia("A glance you didn't choose is not counted against you. Turn your eyes away now.", "النظرة التي لم تقصدها لا إثم عليك فيها، فاصرف بصرك الآن."),
                color = look.t.deepInk.copy(alpha = 0.8f), fontSize = 13.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        } else {
            if (tally > 0) Column(Modifier.fillMaxWidth().hisnCard(look, 18.dp).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ia("Closed for you today", "ما أغلقه حصن لك اليوم"), color = look.t.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(if (tally == 1) ia("Once", "مرة واحدة") else ia("$tally times", timesAr(tally)), color = look.t.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            SayingCard(time, light = false)
        }
    }
}

// ---------------------------------------------------------------- the wait before an app opens

private val Seasonal = listOf("رمضان", "هذا الشهر", "الشهر", "العشر", "عرفة", "الحج")

/** Arabic counts times as مرة واحدة، مرتين، 3–10 مرات، 11+ مرة. */
internal fun timesAr(n: Int): String = when {
    n == 1 -> "مرة واحدة"
    n == 2 -> "مرتين"
    n in 3..10 -> "${nums(n)} مرات"
    else -> "${nums(n)} مرة"
}

/** Arabic counts seconds in four forms: ثانية واحدة، ثانيتان، 3–10 ثوانٍ، 11+ ثانية. */
private fun secondsAr(n: Int): String = when {
    n == 1 -> "ثانية واحدة"
    n == 2 -> "ثانيتين"
    n in 3..10 -> "${nums(n)} ثوانٍ"
    else -> "${nums(n)} ثانية"
}

private val WaitDhikr = listOf("سُبْحَانَ ٱللَّٰهِ وَبِحَمْدِهِ", "أَسْتَغْفِرُ ٱللَّٰهَ", "لَا إِلَٰهَ إِلَّا ٱللَّٰهُ", "ٱلْحَمْدُ لِلَّٰهِ")

@Composable
fun HisnOpenWait(
    appLabel: String,
    seconds: Int,
    openedToday: Int,
    closedToday: Int,
    reason: String,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    onOpenFor: ((Int) -> Unit)?
) {
    val look = LocalLook.current
    var left by rememberSaveable { mutableIntStateOf(seconds) }
    LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- } }
    val dhikr = WaitDhikr[((seconds - left) / 3) % WaitDhikr.size]
    val saying = momentSaying(Sayings.Moment.TIME)
    MomentFrame(
        deep = false,
        actions = {
            MomentButton(ia("I don't need it", "لا أحتاجه"), white = false, onClick = onClose)
            MomentLink(
                if (left > 0) ia("Open $appLabel in $left s", "افتح $appLabel بعد ${nums(left)} ث") else ia("Open $appLabel", "افتح $appLabel"),
                Color.White.copy(alpha = if (left > 0) 0.6f else 0.95f)
            ) { if (left == 0) onOpen() }
            if (onOpenFor != null && left == 0) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                listOf(5, 15).forEach { m ->
                    Text(ia("for $m min", "لمدة ${nums(m)} د"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                            .clickable(role = Role.Button) { onOpenFor(m) }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
    ) {
        HisnIcon("hourglass", 96.dp)
        Text(
            if (left > 0) ia("$appLabel opens in $left seconds", "$appLabel يُفتح بعد ${secondsAr(left)}") else ia("$appLabel can open now", "يمكن فتح $appLabel الآن"),
            color = look.t.ink, fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading(); liveRegion = LiveRegionMode.Polite }
        )
        Text(dhikr, color = look.t.pri, fontSize = 30.sp, lineHeight = 46.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(ia("Say it while you wait", "قلها وأنت تنتظر"), color = look.t.sub, fontSize = 13.sp)
        if (reason.isNotEmpty()) Text(ia("“$reason”", rtlLine("«$reason»")), color = look.t.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().hisnCard(look, 16.dp).padding(14.dp))
        if (openedToday + closedToday > 0) Text(
            ia("Opened $openedToday times today · $closedToday times you didn't need it", "فُتح اليوم ${timesAr(openedToday)}، ولم تحتجه ${timesAr(closedToday)}"),
            color = look.t.ink, fontSize = 13.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().hisnCard(look, 16.dp).padding(12.dp)
        )
        SayingCard(saying, light = false)
    }
}

// ---------------------------------------------------------------- the breathing pause

private val BreathDhikr = listOf("أَسْتَغْفِرُ ٱللَّٰهَ", "سُبْحَانَ ٱللَّٰهِ", "ٱلْحَمْدُ لِلَّٰهِ")
private const val Rounds = 3
private const val InMs = 4000
private const val OutMs = 6000

@Composable
fun HisnBreathe(reason: String, still: Boolean, onClose: () -> Unit) {
    val look = LocalLook.current
    var round by rememberSaveable { mutableIntStateOf(0) }
    var phase by rememberSaveable { mutableIntStateOf(0) } // 0 in, 1 out
    val size = remember { Animatable(0.62f) }
    LaunchedEffect(Unit) {
        while (round < Rounds) {
            phase = 0
            if (still) delay(InMs.toLong()) else size.animateTo(1f, tween(InMs, easing = FastOutSlowInEasing))
            phase = 1
            if (still) delay(OutMs.toLong()) else size.animateTo(0.62f, tween(OutMs, easing = FastOutSlowInEasing))
            round++
        }
    }
    val done = round >= Rounds
    val saying = momentSaying(Sayings.Moment.WEAK)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(look.t.helpTop, look.t.helpBottom)))) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize().align(Alignment.TopCenter).statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, ia("Close", "إغلاق"), tint = look.t.helpInk) }
                Text(ia("Steady yourself", "تمالك نفسك"), color = look.t.helpInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!done) {
                    Text(ia("Let's stop together", "لنتوقّف معًا"), color = look.t.helpInk, fontSize = 27.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(ia("The urge passes and your promise stays. Breathe with the circle.", "الرغبة تمضي، ووعدك يبقى. تنفّس مع الدائرة."),
                        color = look.t.helpInk.copy(alpha = 0.8f), fontSize = 14.5.sp, textAlign = TextAlign.Center)
                    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(230.dp).scale(size.value).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(look.t.acc.copy(alpha = 0.55f), look.t.acc.copy(alpha = 0.08f)))))
                        Text(if (phase == 0) ia("Breathe in", "شهيق") else ia("Breathe out", "زفير"), color = look.t.helpInk, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                    Text(BreathDhikr[round.coerceAtMost(Rounds - 1)], color = look.t.helpInk, fontSize = 30.sp, lineHeight = 46.sp, textAlign = TextAlign.Center)
                    Text(ia("Round ${round + 1} of $Rounds", "الجولة ${nums(round + 1)} من ${nums(Rounds)}"), color = look.t.helpInk.copy(alpha = 0.7f), fontSize = 13.sp)
                } else {
                    HisnIcon("ibrik", 84.dp)
                    Text(ia("It passed, alḥamdulillāh", "مرّت، والحمد لله"), color = look.t.helpInk, fontSize = 27.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(ia("One more step makes it stay passed", "خطوة أخرى تُبقيها بعيدة"), color = look.t.helpInk.copy(alpha = 0.8f), fontSize = 14.5.sp)
                    listOf(
                        Triple("ibrik", ia("Make wuḍūʾ", "توضّأ"), ia("Water and a fresh start", "ماء وبداية جديدة")),
                        Triple("rug", ia("Pray two rakʿahs", "صلِّ ركعتين"), ia("Away from the phone", "بعيدًا عن الهاتف")),
                        Triple("hands", ia("Say a du'a", "ادعُ"), "يا مقلّب القلوب ثبّت قلبي على دينك")
                    ).forEach { (icon, a, b) ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = if (look.dark) 0.08f else 0.7f)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            HisnIcon(icon, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(a, color = look.t.helpInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(b, color = look.t.helpInk.copy(alpha = 0.8f), fontSize = 13.sp)
                            }
                        }
                    }
                }
                if (reason.isNotEmpty()) Text(ia("“$reason”", rtlLine("«$reason»")), color = look.t.helpInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = if (look.dark) 0.08f else 0.55f)).padding(14.dp))
                if (saying != null) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(look.t.card).padding(14.dp)) { SayingBody(saying, flat = true) }
            }
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                MomentButton(if (done) ia("Done, back to my day", "تمّ، أعود إلى يومي") else ia("I'm steady now", "تمالكت نفسي"), white = false, onClick = onClose)
            }
        }
    }
}
