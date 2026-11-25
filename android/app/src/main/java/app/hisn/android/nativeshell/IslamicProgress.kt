package app.hisn.android.nativeshell

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

/** Per-night records the reader keeps: the reflection's note and ratings. */
internal object HisnNights {
    private fun prefs(c: Context) = c.getSharedPreferences("hisn-native", Context.MODE_PRIVATE)
    fun note(c: Context, day: Long): String = prefs(c).getString("note.$day", "") ?: ""
    fun setNote(c: Context, day: Long, text: String) = prefs(c).edit().putString("note.$day", text).apply()
    fun ratings(c: Context, day: Long): List<Int> =
        (prefs(c).getString("rate.$day", "") ?: "").split(',').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 5 } ?: emptyList()
    fun setRatings(c: Context, day: Long, r: List<Int>) = prefs(c).edit().putString("rate.$day", r.joinToString(",")).apply()
    fun thanks(c: Context, day: Long): String = prefs(c).getString("thanks.$day", "") ?: ""
    fun setThanks(c: Context, day: Long, text: String) = prefs(c).edit().putString("thanks.$day", text).apply()
    fun startDay(c: Context, state: HisnState): Long = LocalDate.now().toEpochDay() - (state.personal.day - 1).coerceAtLeast(0)
    fun markToday(c: Context, route: String) = prefs(c).edit().putBoolean("today.${LocalDate.now().toEpochDay()}.$route", true).apply()
}

@Composable
internal fun IslamicProgress(state: HisnState, onOpen: (String) -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    val nights = nightsOf(state)
    val (prev, next) = stationsFor(nights)
    val today = LocalDate.now()
    val prayedWeek = remember(today) { (0L until 7L).sumOf { Integer.bitCount(HisnPrayers.prayed(context, today.minusDays(it))) } }
    val notes = remember(today) { (0L..nights.toLong()).count { HisnNights.note(context, today.toEpochDay() - it).isNotBlank() } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = IslamicBarHeight + 40.dp)) {
        item {
            Text(ia("Your progress", "تقدّمك"), color = look.t.ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp))
        }
        item {
            Column(Modifier.padding(horizontal = 18.dp).fillMaxWidth().hisnCard(look, 22.dp).padding(16.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    if (nights == 0) {
                        Text(ia("Night one", "الليلة الأولى"), color = look.t.ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text(nums(nights), color = look.t.ink, fontSize = 56.sp, lineHeight = 58.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(ia("nights of light", "ليالٍ من النور"), color = look.t.sub, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(look.t.soft).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    HisnIcon(next.icon, 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(ia("Next station", "المحطة التالية"), color = look.t.sub, fontSize = 11.sp)
                        Text(next.name, color = look.t.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(ia("${(next.nights - nights).coerceAtLeast(0)} nights to go", "بقي ${nums((next.nights - nights).coerceAtLeast(0))} ليالٍ"), color = look.t.acc, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                val base = prev?.nights ?: 0
                val p = if (nights >= next.nights) 1f else (nights - base).toFloat() / (next.nights - base)
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(look.t.ring)) {
                    Box(Modifier.fillMaxWidth(p).height(8.dp).background(Brush.horizontalGradient(listOf(Color(0xFFE9C46A), look.t.acc))))
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                ISLAMIC_STATIONS.forEachIndexed { i, s ->
                    val done = nights >= s.nights
                    val cur = s == next && !done
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(look.t.card)
                                .then(if (cur) Modifier.border(3.dp, look.t.acc, CircleShape) else Modifier)
                                .alpha(if (done || cur) 1f else 0.5f),
                            contentAlignment = Alignment.Center
                        ) { HisnIcon(s.icon, 36.dp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, color = look.t.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(ia("${s.nights} nights", "${nums(s.nights)} ليلة"), color = look.t.sub, fontSize = 12.sp)
                        }
                        Text(
                            when { done -> ia("Reached", "بلغتها"); cur -> ia("${s.nights - nights} left", "بقي ${nums(s.nights - nights)}"); else -> "" },
                            color = if (done) look.t.kept else look.t.acc, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    if (i == 0 && s.nights <= nights && next == ISLAMIC_STATIONS.getOrNull(1)) {
                        Text("●  " + ia("You are here · night $nights", if (nights == 0) "أنت هنا: الليلة الأولى" else "أنت هنا: الليلة ${nums(nights)}"), color = look.t.pri, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 18.dp))
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox(ia("Nights kept", "ليالٍ محفوظة"), bigNum(nights), ia("since you began", "منذ بدايتك"), Modifier.weight(1f))
                StatBox(ia("Prayers marked", "صلوات سجّلتها"), ofCount(prayedWeek, 35), ia("last 7 days", "آخر 7 أيام"), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox(ia("Open Wait pauses", "مرات انتظار الفتح"), bigNum(state.personal.pauses), ia("times you waited", "مرة انتظرتَ"), Modifier.weight(1f))
                StatBox(ia("Notes", "ملاحظات"), bigNum(notes), ia("in your calendar", "في تقويمك"), Modifier.weight(1f))
            }
        }
        item {
            Row(
                Modifier.padding(18.dp).fillMaxWidth().hisnCard(look).clickable { onOpen("hisn:calendar") }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HisnIcon("crescent", 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ia("Your calendar", "تقويمك"), color = look.t.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(ia("Every night, with its Hijri date and your notes", "كل ليلة بتاريخها الهجري وملاحظاتك"), color = look.t.sub, fontSize = 12.sp)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = look.t.sub)
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, sub: String, modifier: Modifier) {
    val look = LocalLook.current
    Column(modifier.hisnCard(look).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, color = look.t.sub, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = look.t.ink, fontSize = 26.sp)
        Text(sub, color = look.t.sub, fontSize = 11.5.sp)
    }
}

// ============================ Calendar ============================

@Composable
internal fun IslamicCalendar(state: HisnState, onBack: () -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    val today = LocalDate.now()
    val start = remember { HisnNights.startDay(context, state) }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val ym = YearMonth.parse(month)
    var sel by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var noteVersion by remember { mutableLongStateOf(0) }

    IslamicPage(ia("Your calendar", "تقويمك"), onBack) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = ym.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, ia("Previous month", "الشهر السابق"), tint = look.t.ink) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ym.month.getDisplayName(TextStyle.FULL, uiLocale()) + " " + nums(ym.year), color = look.t.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                val h1 = hijriOf(ym.atDay(1)); val h2 = hijriOf(ym.atEndOfMonth())
                Text(if (h1.month == h2.month) h1.monthName() + nums(" ${h1.year}") else h1.monthName() + " – " + h2.monthName() + nums(" ${h2.year}"), color = look.t.sub, fontSize = 12.sp)
            }
            IconButton(onClick = { if (ym < YearMonth.from(today)) month = ym.plusMonths(1).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, ia("Next month", "الشهر التالي"), tint = if (ym < YearMonth.from(today)) look.t.ink else look.t.line)
            }
        }
        // Week headers start on Sunday, as in the Arab world's calendars.
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY).forEach { d ->
                Text(dayLetter(d), color = if (d == DayOfWeek.FRIDAY) look.t.acc else look.t.sub,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        val pad = ym.atDay(1).dayOfWeek.value % 7
        val cells = pad + ym.lengthOfMonth()
        for (row in 0 until (cells + 6) / 7) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until 7) {
                    val idx = row * 7 + col - pad
                    if (idx < 0 || idx >= ym.lengthOfMonth()) { Spacer(Modifier.weight(1f)); continue }
                    val date = ym.atDay(idx + 1)
                    val ed = date.toEpochDay()
                    val future = date.isAfter(today)
                    val before = ed < start
                    val kept = !future && !before && ed < today.toEpochDay()
                    val h = hijriOf(date)
                    val hasNote = noteVersion >= 0 && HisnNights.note(context, ed).isNotBlank()
                    Box(
                        Modifier.weight(1f).aspectRatio(0.92f).clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    kept -> Brush.linearGradient(listOf(Color(0xFFF6DC9C), Color(0xFFD89C40)))
                                    future || before -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                    else -> Brush.linearGradient(listOf(look.t.soft, look.t.soft))
                                }
                            )
                            .then(if (future || before) Modifier.border(1.dp, look.t.line, RoundedCornerShape(12.dp)) else Modifier)
                            .then(if (ed == today.toEpochDay()) Modifier.border(2.5.dp, look.t.pri, RoundedCornerShape(12.dp)) else Modifier)
                            .then(if (ed == sel) Modifier.border(2.dp, look.t.acc, RoundedCornerShape(12.dp)) else Modifier)
                            .clickable(enabled = !future) { sel = ed },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(nums(date.dayOfMonth), color = if (kept) Color(0xFF3A2200) else if (future || before) look.t.sub else look.t.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(nums(h.day), color = (if (kept) Color(0xFF3A2200) else look.t.sub).copy(alpha = 0.7f), fontSize = 9.sp)
                        }
                        if (h.day in 13..15) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(6.dp).clip(CircleShape).background(Color.White).border(1.dp, look.t.acc, CircleShape))
                        if (hasNote) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).width(14.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (kept) Color(0xFF3A2200) else look.t.pri))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            Text(ia("Gold: a night kept · dot: the white days (13–15) · bar: a note", "ذهبي: ليلة محفوظة · نقطة: الأيام البيض · خط: ملاحظة"), color = look.t.sub, fontSize = 11.5.sp, textAlign = TextAlign.Center)
        }
        val selDate = LocalDate.ofEpochDay(sel)
        val note = remember(sel, noteVersion) { HisnNights.note(context, sel) }
        val rates = remember(sel, noteVersion) { HisnNights.ratings(context, sel) }
        Column(Modifier.fillMaxWidth().hisnCard(look).padding(16.dp)) {
            Text(
                if (sel == today.toEpochDay()) ia("Tonight", "الليلة") else selDate.dayOfWeek.getDisplayName(TextStyle.FULL, uiLocale()) + " " + nums(selDate.dayOfMonth),
                color = look.t.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
            Text(hijriOf(selDate).label(withYear = true), color = look.t.sub, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            if (note.isNotBlank()) Text("“$note”", color = look.t.ink, fontSize = 16.sp, lineHeight = 24.sp)
            else Text(ia("No note this night. The evening reflection writes one.", "لا ملاحظة في هذه الليلة. محاسبة المساء تكتبها."), color = look.t.sub, fontSize = 13.sp)
            if (rates.size == 5) {
                Spacer(Modifier.height(8.dp))
                listOf(ia("Prayer", "الصلاة"), ia("Gaze", "البصر"), ia("Qurʾān", "القرآن"), ia("Calm", "السكينة"), ia("Energy", "النشاط")).forEachIndexed { i, label ->
                    // The reckoning no longer asks calm and energy; a night without them shows three rows.
                    if (rates[i] > 0) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(label, color = look.t.sub, fontSize = 12.sp, modifier = Modifier.width(64.dp))
                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)).background(look.t.line)) {
                            Box(Modifier.fillMaxWidth(rates[i] / 10f).fillMaxHeight().background(look.t.pri))
                        }
                        Text(nums(rates[i]), color = look.t.ink, fontSize = 12.sp, modifier = Modifier.width(22.dp), textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}
