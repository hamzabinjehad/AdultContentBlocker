package app.hisn.android.nativeshell

import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisn.android.PauseEntry
import app.hisn.core.PrayerTimes
import java.time.LocalDate

/** The Islamic edition's tabs: Home, Progress, (Help), Library, Tools. */
internal object IslamicTab {
    const val HOME = 0
    const val PROGRESS = 1
    const val LIBRARY = 2
    const val TOOLS = 3
}

/** The bottom bar's height above the navigation bar; screens leave this much room at the end. */
internal val IslamicBarHeight = 72.dp

@Composable
internal fun IslamicScaffold(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    state: HisnState,
    copy: ProtectionCopy,
    onOpen: (String) -> Unit
) {
    val look = LocalLook.current
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(look.t.sky)) {
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
            modifier = Modifier.fillMaxSize(),
            label = "islamic tab"
        ) { tab ->
            when (tab) {
                IslamicTab.HOME -> IslamicHome(state, onOpen, onSelect)
                IslamicTab.PROGRESS -> IslamicProgress(state, onOpen)
                IslamicTab.LIBRARY -> IslamicLibrary(onOpen)
                else -> IslamicTools(state, onOpen) { onSelect(IslamicTab.LIBRARY) }
            }
        }
        IslamicTabBar(
            selected = selectedTab,
            onSelect = onSelect,
            onHelp = { onOpen("hisn:help") },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun IslamicTabBar(selected: Int, onSelect: (Int) -> Unit, onHelp: () -> Unit, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    Box(modifier.fillMaxWidth()) {
        // A solid surface: the list scrolling underneath must never show through.
        // It also takes every touch on it, so a tap between tabs never reaches the card behind.
        Box(Modifier.matchParentSize().background(look.t.card).pointerInput(Unit) { detectTapGestures { } })
        Box(Modifier.fillMaxWidth().height(1.dp).background(look.t.line))
        Row(
            Modifier
                .fillMaxWidth()
                .background(look.t.card)
                .navigationBarsPadding()
                .height(IslamicBarHeight)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabItem(Icons.Filled.Home, ia("Home", "الرئيسية"), selected == IslamicTab.HOME) { onSelect(IslamicTab.HOME) }
            TabItem(Icons.Filled.InsertChart, ia("Progress", "التقدّم"), selected == IslamicTab.PROGRESS) { onSelect(IslamicTab.PROGRESS) }
            // Help sits in line with the tabs as a flat red block: no gradient, no glow, nothing floating.
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button, onClick = onHelp)
                    .semantics(mergeDescendants = true) { contentDescription = ia("Help now", "ساعدني الآن") },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Box(Modifier.size(width = 48.dp, height = 34.dp).clip(RoundedCornerShape(12.dp)).background(look.t.help), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PriorityHigh, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(3.dp))
                Text(ia("Help", "ساعدني"), color = look.t.help, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clearAndSetSemantics { })
            }
            TabItem(Icons.Filled.MenuBook, ia("Library", "المكتبة"), selected == IslamicTab.LIBRARY) { onSelect(IslamicTab.LIBRARY) }
            TabItem(Icons.Filled.Widgets, ia("Tools", "الأدوات"), selected == IslamicTab.TOOLS) { onSelect(IslamicTab.TOOLS) }
        }
    }
}

@Composable
private fun RowScope.TabItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val look = LocalLook.current
    val color = if (selected) look.t.pri else look.t.sub
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The current tab is marked by a short line on the bar's top edge, not a tinted pill.
        Box(Modifier.width(30.dp).height(3.dp).clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)).background(if (selected) look.t.pri else Color.Transparent))
        Spacer(Modifier.weight(1f))
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = color, fontSize = 11.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        Spacer(Modifier.weight(1f))
    }
}

// ============================ Home ============================

private val STATIONS = listOf(
    Station(7, "medal", "First week", "الأسبوع الأول"),
    Station(14, "lantern", "Bright lamp", "المصباح المضيء"),
    Station(40, "crescent", "Forty nights", "أربعون ليلة"),
    Station(90, "tower", "The fortress", "الحصن")
)

internal data class Station(val nights: Int, val icon: String, val en: String, val ar: String) {
    val name: String get() = ia(en, ar)
}

internal fun stationsFor(nights: Int): Pair<Station?, Station> {
    val next = STATIONS.firstOrNull { it.nights > nights } ?: STATIONS.last()
    val prev = STATIONS.lastOrNull { it.nights <= nights }
    return prev to next
}

internal val ISLAMIC_STATIONS: List<Station> get() = STATIONS

/**
 * The home chip's verdict, from the same checks as the protection screen's hero: the network
 * filter is attached with no fault, and app protection is on with its guard connected. Home never
 * says "protected" when that screen would ask for attention.
 */
internal fun homeProtected(state: HisnState): Boolean {
    val appsOk = state.apps.on && state.guardReady && !state.guardPaused
    return dnsOk(state) && appsOk
}

/** Nights kept: whole days since the start day, so the first night counts once it has passed. */
internal fun nightsOf(state: HisnState): Int = (state.personal.day - 1).coerceAtLeast(0)

@Composable
internal fun IslamicHome(state: HisnState, onOpen: (String) -> Unit, onSelect: (Int) -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    val nights = nightsOf(state)
    val today = LocalDate.now()
    val nowMin = rememberNowMinutes()
    val prayers = remember(today) { HisnPrayers.today(context, today) }
    var prayed by remember(today) { mutableIntStateOf(HisnPrayers.prayed(context, today)) }
    val sayings by rememberSayings()
    var skip by rememberSaveable { mutableIntStateOf(0) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = IslamicBarHeight + 48.dp)
    ) {
        item { HomeHero(state, nights, today, onSettings = { onOpen("hisn:settings") }) { onOpen("hisn:shield") } }
        item { WeekStrip(nights, Modifier.padding(horizontal = 18.dp).padding(top = 0.dp)) { onOpen("hisn:calendar") } }
        item {
            PrayerCard(prayers, nowMin, prayed, Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) { i ->
                prayed = HisnPrayers.togglePrayed(context, i, today)
            }
        }
        item { TodayCard(onOpen, Modifier.padding(horizontal = 18.dp)) }
        item {
            val s = sayings
            val pick = s?.daily(today.toEpochDay(), skip)
            if (pick != null) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp).fillMaxWidth().hisnCard(look).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ia("Wisdom for today", "حكمة اليوم"), color = look.t.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        SmallPill(ia("Another", "غيرها")) { skip++ }
                        Spacer(Modifier.width(6.dp))
                        SmallPill(ia("Library", "المكتبة")) { onSelect(IslamicTab.LIBRARY) }
                    }
                    Spacer(Modifier.height(8.dp))
                    SayingBody(pick, flat = true)
                }
            }
        }
        item { VerseCard(today, Modifier.padding(horizontal = 18.dp)) }
        item {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolTile("shield", ia("Content shield", "درع المحتوى"),
                    if (homeProtected(state)) ia("On", "يعمل") else ia("Needs you", "يحتاجك"), Modifier.weight(1f)) { onOpen("hisn:shield") }
                ToolTile("hourglass", ia("Open Wait", "انتظار الفتح"), ia("Dhikr before a feed", "ذكر قبل التصفّح"), Modifier.weight(1f)) {
                    context.startActivity(Intent(context, app.hisn.android.OpenWaitSettingsActivity::class.java))
                }
            }
            Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolTile("crescent", ia("Calendar", "التقويم"), ia("Notes by night", "ملاحظات كل ليلة"), Modifier.weight(1f)) { onOpen("hisn:calendar") }
                ToolTile("misbaha", ia("Adhkār", "الأذكار"), ia("Morning and evening", "الصباح والمساء"), Modifier.weight(1f)) { onOpen("hisn:adhkar") }
            }
        }
    }
}

@Composable
private fun HomeHero(state: HisnState, nights: Int, today: LocalDate, onSettings: () -> Unit, onTools: () -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    val bg = remember(look) { HisnArt.id(context, look.backgroundName) }
    val (prev, next) = stationsFor(nights)
    val span = next.nights - (prev?.nights ?: 0)
    val progress = if (nights >= next.nights) 1f else (nights - (prev?.nights ?: 0)).toFloat() / span
    val animated by animateFloatAsState(progress, tween(1200), label = "ring")
    val name = state.personal.name.ifBlank { "" }

    Box(Modifier.fillMaxWidth().heightIn(min = 430.dp)) {
        if (bg != 0) {
            Image(
                painterResource(bg), contentDescription = null, contentScale = ContentScale.Crop,
                alignment = Alignment.BottomCenter, modifier = Modifier.matchParentSize()
            )
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to look.t.sky)))
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // The mockup's header: the gate mark, the greeting, and a round button to settings.
                HisnGateMark(30.dp, look.t.pri)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (name.isEmpty()) ia("As-salāmu ʿalaykum", "السلام عليكم") else ia("As-salāmu ʿalaykum, $name", "السلام عليكم يا $name"),
                        color = look.t.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                    // Both calendars, the person's country's own first.
                    Text(bothDates(context, hijriOf(today).label(withYear = true), gregLabel(context, today)), color = look.t.sub, fontSize = 12.sp)
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(look.t.card.copy(alpha = 0.85f)).border(1.dp, look.t.line, CircleShape)
                        .clickable(role = Role.Button, onClick = onSettings).semantics { contentDescription = ia("Settings", "الإعدادات") },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Person, null, tint = look.t.pri, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    val stroke = 7.dp.toPx()
                    val inset = stroke / 2 + 2.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(look.t.ring, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    drawArc(look.t.acc, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(
                    Modifier.size(166.dp).clip(CircleShape).background(look.t.card.copy(alpha = 0.82f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(ia("nights of light", "ليالٍ من النور"), color = look.t.sub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    if (nights == 0) {
                        // A big Arabic zero reads as a dot; the first night says what it is.
                        Text(ia("Night one", "الليلة الأولى"), color = look.t.ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp, modifier = Modifier.padding(vertical = 14.dp))
                    } else {
                        Text(nums(nights), color = look.t.ink, fontSize = 68.sp, fontWeight = FontWeight.Normal, lineHeight = 70.sp)
                    }
                    LiveDayClock()
                }
            }
            Spacer(Modifier.height(10.dp))
            val toGo = (next.nights - nights).coerceAtLeast(0)
            Text(
                if (nights >= STATIONS.last().nights) ia("You reached the fortress", "بلغتَ الحصن")
                else ia("${toGo} nights to ${next.en}", "${nums(toGo)} ليالٍ إلى ${next.ar}"),
                color = look.t.ink, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(look.t.card.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Spacer(Modifier.height(8.dp))
            val warn = !homeProtected(state)
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (warn) Color(0xFFFDECCD) else Color(0xFFE3F5EA))
                    .clickable(role = Role.Button, onClick = onTools)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Shield, null, tint = if (warn) Color(0xFF8A5A00) else Color(0xFF1F7A45), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (warn) ia("Protection needs attention · fix", "الحماية تحتاج إلى مراجعة · أصلحها") else ia("Protected · network and apps", "محميّ · الشبكة والتطبيقات"),
                    color = if (warn) Color(0xFF8A5A00) else Color(0xFF1F7A45), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** Hours, minutes and seconds of tonight's watch, since midnight; ticks every second. */
@Composable
private fun LiveDayClock() {
    val look = LocalLook.current
    var now by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(1000); now = System.currentTimeMillis() }
    }
    val c = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val text = String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), c.get(java.util.Calendar.SECOND))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(look.t.kept))
        Spacer(Modifier.width(6.dp))
        Text(nums(text), color = look.t.ink, fontSize = 12.sp)
    }
}

@Composable
private fun WeekStrip(nights: Int, modifier: Modifier, onClick: () -> Unit) {
    val look = LocalLook.current
    val today = LocalDate.now()
    Row(
        modifier.fillMaxWidth().hisnCard(look).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        for (i in 6 downTo 0) {
            val d = today.minusDays(i.toLong())
            val kept = i in 1..nights
            val isToday = i == 0
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(
                    dayLetter(d.dayOfWeek),
                    color = if (isToday) look.t.ink else look.t.sub, fontSize = 11.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(Modifier.height(5.dp))
                // The date in a plain disc: flat gold for a kept night, a ring otherwise, today outlined.
                Box(
                    Modifier
                        .size(32.dp)
                        .then(if (isToday) Modifier.border(2.dp, look.t.pri, CircleShape).padding(3.dp) else Modifier)
                        .clip(CircleShape)
                        .then(if (kept) Modifier.background(Color(0xFFD9A441)) else Modifier.border(1.5.dp, look.t.line, CircleShape)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(d.dayOfMonth.toString(), color = if (kept) Color(0xFF3A2200) else look.t.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** One letter per weekday. Android's narrow Arabic names are all «ا», so Arabic uses its own. */
internal fun dayLetter(d: java.time.DayOfWeek): String =
    if (isArabicUi()) "نثرخجسح"[d.value - 1].toString()
    else d.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.ENGLISH)

@Composable
private fun PrayerCard(times: PrayerTimes, nowMin: Int, prayed: Int, modifier: Modifier, onToggle: (Int) -> Unit) {
    val look = LocalLook.current
    val five = times.five()
    val next = times.nextOfFive(nowMin)
    val until = times.minutesUntilNext(nowMin)
    Column(modifier.fillMaxWidth().hisnCard(look).padding(12.dp)) {
        Row {
            Text(ia("Prayers today", "صلوات اليوم"), color = look.t.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(ofCount(Integer.bitCount(prayed), 5), color = look.t.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until 5) {
                val done = prayed and (1 shl i) != 0
                val isNext = i == next
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isNext) look.t.chip else look.t.soft)
                        .then(if (isNext) Modifier.border(1.5.dp, look.t.pri, RoundedCornerShape(14.dp)) else Modifier)
                        .clickable(role = Role.Checkbox) { onToggle(i) }
                        .semantics { contentDescription = HisnPrayers.name(i) + if (done) ia(", prayed", "، صُلّيت") else "" }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape)
                            .background(if (done) look.t.kept else look.t.card)
                            .border(1.5.dp, if (done) look.t.kept else look.t.line, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { if (done) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                    Spacer(Modifier.height(3.dp))
                    Text(HisnPrayers.name(i), color = if (isNext) look.t.pri else look.t.sub, fontSize = 11.sp, fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                    Text(nums(PrayerTimes.hhmm(five[i])), color = look.t.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val h = until / 60
        val m = until % 60
        Text(
            ia("${HisnPrayers.namesEn[next]} in ${if (h > 0) "$h h " else ""}$m min", "${HisnPrayers.namesAr[next]} بعد ${if (h > 0) nums(h) + " س " else ""}${nums(m)} د"),
            color = look.t.sub, fontSize = 12.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TodayCard(onOpen: (String) -> Unit, modifier: Modifier) {
    val look = LocalLook.current
    val context = LocalContext.current
    val day = LocalDate.now().toEpochDay()
    val prefs = remember { context.getSharedPreferences("hisn-native", android.content.Context.MODE_PRIVATE) }
    val rows = listOf(
        Triple("hisn:adhkar", "misbaha", ia("Morning adhkār", "أذكار الصباح") to ia("3 min · with a counter", "3 دقائق · مع العدّاد")),
        Triple("hisn:read", "quran", ia("Today's reading", "ورد اليوم") to ia("A few verses · 4 min", "آيات قليلة · 4 دقائق")),
        Triple("hisn:reflect", "lantern", ia("Reckoning before sleep", "محاسبة قبل النوم") to ia("One minute, then a note", "دقيقة، ثم ملاحظة"))
    )
    val done = rows.map { prefs.getBoolean("today.$day.${it.first}", false) }
    Column(modifier.fillMaxWidth().hisnCard(look).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ia("Today", "اليوم"), color = look.t.ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(6.dp).clip(RoundedCornerShape(50)).background(look.t.soft)) {
                Box(Modifier.fillMaxWidth(done.count { it } / 3f).height(6.dp).background(look.t.kept))
            }
            Text(ofCount(done.count { it }, 3), color = look.t.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        rows.forEachIndexed { i, (route, icon, labels) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(route) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(if (done[i]) look.t.kept else Color.Transparent)
                        .border(1.8.dp, if (done[i]) look.t.kept else look.t.line, CircleShape),
                    contentAlignment = Alignment.Center
                ) { if (done[i]) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                Spacer(Modifier.width(10.dp))
                HisnIcon(icon, 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(labels.first, color = if (done[i]) look.t.sub else look.t.ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(labels.second, color = look.t.sub, fontSize = 12.sp)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = look.t.sub)
            }
        }
    }
}

private val VERSES = listOf(
    "أَلَمْ يَعْلَم بِأَنَّ ٱللَّهَ يَرَىٰ" to ("al-ʿAlaq 14" to "العلق 14"),
    "قُل لِّلْمُؤْمِنِينَ يَغُضُّوا۟ مِنْ أَبْصَٰرِهِمْ" to ("an-Nūr 30" to "النور 30"),
    "لَا تَقْنَطُوا۟ مِن رَّحْمَةِ ٱللَّهِ" to ("az-Zumar 53" to "الزمر 53"),
    "وَمَن يَتَّقِ ٱللَّهَ يَجْعَل لَّهُۥ مَخْرَجًا" to ("aṭ-Ṭalāq 2" to "الطلاق 2"),
    "فَإِنِّى قَرِيبٌ" to ("al-Baqara 186" to "البقرة 186"),
    "إِنَّ ٱللَّهَ يُحِبُّ ٱلتَّوَّٰبِينَ" to ("al-Baqara 222" to "البقرة 222"),
    "أَلَا بِذِكْرِ ٱللَّهِ تَطْمَئِنُّ ٱلْقُلُوبُ" to ("ar-Raʿd 28" to "الرعد 28")
)

@Composable
private fun VerseCard(today: LocalDate, modifier: Modifier) {
    val look = LocalLook.current
    val (ar, ref) = VERSES[(today.toEpochDay() % VERSES.size).toInt()]
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(look.t.deep).padding(18.dp)) {
        Row {
            Text(ia("Verse of the day", "آية اليوم"), color = look.t.deepInk.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(ia(ref.first, ref.second), color = look.t.deepInk, fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(ar, color = look.t.deepInk, fontSize = 24.sp, lineHeight = 40.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ToolTile(icon: String, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    val look = LocalLook.current
    Column(modifier.hisnCard(look).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)) {
        HisnIcon(icon, 40.dp)
        Spacer(Modifier.height(4.dp))
        Text(title, color = look.t.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(sub, color = look.t.sub, fontSize = 12.sp)
    }
}

@Composable
internal fun SmallPill(label: String, onClick: () -> Unit) {
    val look = LocalLook.current
    Text(
        label, color = look.t.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(look.t.soft).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

// ============================ Tools ============================

