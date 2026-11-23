package app.hisn.android.nativeshell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisn.android.R
import app.hisn.android.FocusSession
import app.hisn.android.PolicyStore
import app.hisn.android.StepIns
import app.hisn.core.DailyAppSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle

/**
 * The top of Progress, laid out like CleanPath's Analytics: a big gauge, two tiles and meters.
 * Their "Recovery %" has no source; ours is less feed time than the person's own before, from
 * Android's usage record, and every meter is a count the phone keeps.
 */
@Composable
internal fun UnrotAnalyticsTop(w: WinsSummary?, day: Int, steps: Map<String, Int>, quiet: DailyAppSchedule? = null) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    var explain by remember { mutableStateOf(false) }
    val gold = Color(0xFFF2C14E)
    val less = if (w != null && w.base.feedMs > 0) (-w.percent).coerceAtLeast(0) else null
    val usageOn = remember(w) { Usage.hasAccess(context) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The gauge: a pill with ticks, the number large, what it means beside it.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Color(0x228FE06A), Color(0x55F2C14E))))
                .border(2.dp, gold.copy(alpha = .35f), CircleShape)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.an_explain)) { explain = true }
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (less != null) {
                Text("$less%", fontSize = 44.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    modifier = Modifier.semantics { heading() })
            } else {
                // No number yet: a seedling in a soft ring instead of a lonely dash.
                Box(Modifier.size(52.dp).background(Color.White.copy(alpha = .08f), CircleShape)
                    .border(1.dp, gold.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
                    Text("🌱", fontSize = 24.sp)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    (if (less != null) stringResource(R.string.an_less_feed) else stringResource(R.string.anp_gauge_wait_title))
                        .uppercase(appLocale()),
                    style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.ExtraBold,
                    modifier = if (less == null) Modifier.semantics { heading() } else Modifier
                )
                Text(
                    when {
                        less != null -> stringResource(R.string.an_less_feed_body,
                            Usage.formatDuration(w!!.base.feedMs), Usage.formatDuration(w.nowFeedMs))
                        !usageOn -> stringResource(R.string.anp_gauge_no_access)
                        else -> stringResource(R.string.anp_gauge_wait_body)
                    },
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFE8DCC3)
                )
            }
            Box(Modifier.size(22.dp).border(1.dp, Color(0xFFE8DCC3), CircleShape), contentAlignment = Alignment.Center) {
                Text("i", fontSize = 12.sp, color = Color(0xFFE8DCC3))
            }
        }
        // Two tiles: days with Unrot and today's check-in.
        val checkIn = remember { CheckIns.today(context) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(stringResource(R.string.an_with_unrot), "🌱 " + stringResource(R.string.an_day, day.coerceAtLeast(1)), Modifier.weight(1f))
            Tile(
                stringResource(R.string.an_today_feel),
                if (checkIn == null) "–" else listOf("😞", "😕", "😐", "🙂", "😄")[checkIn.first.coerceIn(0, 4)] + " " +
                    stringResource(intArrayOf(R.string.checkin_mood_0, R.string.checkin_mood_1, R.string.checkin_mood_2,
                        R.string.checkin_mood_3, R.string.checkin_mood_4)[checkIn.first.coerceIn(0, 4)]),
                Modifier.weight(1f)
            )
        }
        // Meters: what the time is giving back, each fed by a real count this week.
        val pauses = steps[StepIns.BREATHED] ?: 0
        val kept = (steps[StepIns.ADULT] ?: 0) + (steps[StepIns.SITES] ?: 0)
        // Sleep: the quiet hours as saved now (the phone keeps no history of past settings, so
        // this says how many nights a week they cover, not how many were "kept").
        val schedule = quiet ?: remember(steps) {
            runCatching { PolicyStore.get(context).snapshot().schedule }.getOrNull()
        }
        val nights = if (schedule != null && schedule.isConfigured) Integer.bitCount(schedule.days and DailyAppSchedule.ALL_DAYS) else 0
        Meter(
            "☾", Color(0xFF9FB0FF), stringResource(R.string.anp_sleep),
            if (nights > 0) resources.getQuantityString(R.plurals.anp_sleep_body, nights, nights,
                clock(schedule!!.startMinute), clock(schedule.endMinute))
            else stringResource(R.string.anp_sleep_none),
            if (nights > 0) nights / 7f else 0.04f
        )
        // Focus: FocusSession keeps only the running session (its end and its apps), no history,
        // so the meter shows the focus that is on now, or plainly that none is running.
        val focusEnd = remember(steps) { FocusSession.until(context) }
        val focusApps = remember(steps) { FocusSession.apps(context).size }
        Meter(
            "◎", gold, stringResource(R.string.anp_focus),
            if (focusEnd > 0L) resources.getQuantityString(R.plurals.anp_focus_on, focusApps, focusApps,
                clock(java.time.Instant.ofEpochMilli(focusEnd).atZone(java.time.ZoneId.systemDefault()).toLocalTime().let { it.hour * 60 + it.minute }))
            else stringResource(R.string.anp_focus_none),
            if (focusEnd > 0L) ((focusEnd - System.currentTimeMillis()).toFloat() / FOCUS_HOUR).coerceIn(0.1f, 1f) else 0.04f
        )
        Meter("⚡", Color(0xFF8FE06A), stringResource(R.string.an_calm), stringResource(R.string.an_calm_body, pauses), (pauses / 21f).coerceIn(0.04f, 1f))
        Meter("🔒", Color(0xFFE58F7A), stringResource(R.string.an_kept), stringResource(R.string.an_kept_body, kept), if (kept > 0) 1f else 0.04f)
        WeekMoods()
    }
    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            confirmButton = { TextButton(onClick = { explain = false }) { Text(stringResource(R.string.an_got_it)) } },
            title = { Text(stringResource(R.string.an_explain)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.an_explain_less))
                    Text(stringResource(R.string.an_slip_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.an_slip_body))
                }
            }
        )
    }
}

/** One hour, the scale of the Focus bar: what is left of the running focus, full at an hour or more. */
private const val FOCUS_HOUR = 60f * 60_000f

private fun clock(minute: Int): String = LocalTime.of((minute / 60).coerceIn(0, 23), minute % 60).toString()

private val moodColors = listOf(
    Color(0xFFE58F7A), Color(0xFFE8A96A), Color(0xFFD9CBA8), Color(0xFFB5DA82), Color(0xFF8FE06A)
)
private val weekFaces = listOf("😞", "😕", "😐", "🙂", "😄")

/**
 * This week, Monday to Sunday, one dot per day coloured by that day's check-in (CheckIns keeps
 * "<epochDay>:mood", 0..4, for 60 days). Days without a check-in stay hollow, days still ahead
 * are faint, and today wears a gold ring.
 */
@Composable
private fun WeekMoods() {
    val context = LocalContext.current
    val today = LocalDate.now()
    val monday = today.with(DayOfWeek.MONDAY)
    val moods = remember(today, CheckIns.version.intValue) {
        runCatching {
            val p = context.applicationContext.getSharedPreferences("check_ins", android.content.Context.MODE_PRIVATE)
            (0..6).map { i ->
                val key = "${monday.plusDays(i.toLong()).toEpochDay()}:mood"
                if (p.contains(key)) p.getInt(key, 2).coerceIn(0, 4) else null
            }
        }.getOrDefault(List(7) { null })
    }
    val gold = Color(0xFFF2C14E)
    val passed = today.dayOfWeek.value
    val checked = moods.take(passed).count { it != null }
    val moodNames = intArrayOf(R.string.checkin_mood_0, R.string.checkin_mood_1, R.string.checkin_mood_2,
        R.string.checkin_mood_3, R.string.checkin_mood_4).map { stringResource(it) }
    val noCheckIn = stringResource(R.string.anp_week_none)
    val ahead = stringResource(R.string.anp_week_ahead)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(LocalHisnStatusColors.current.card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.anp_week_title), style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).semantics { heading() })
            Text(stringResource(R.string.anp_week_count, checked, passed), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (0..6).forEach { i ->
                val date = monday.plusDays(i.toLong())
                val mood = moods[i]
                val isToday = date == today
                val future = date.isAfter(today)
                val name = date.dayOfWeek.getDisplayName(TextStyle.FULL, appLocale())
                val said = when { future -> ahead; mood == null -> noCheckIn; else -> moodNames[mood] }
                Column(
                    Modifier.weight(1f).semantics(mergeDescendants = true) {
                        contentDescription = "$name: $said"
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier.size(34.dp)
                            .then(if (isToday) Modifier.border(2.dp, gold, CircleShape) else Modifier)
                            .padding(if (isToday) 4.dp else 2.dp)
                            .clip(CircleShape)
                            .then(
                                when {
                                    mood != null -> Modifier.background(moodColors[mood])
                                    future -> Modifier.background(Color.White.copy(alpha = .04f))
                                    else -> Modifier.border(1.5.dp, Color.White.copy(alpha = .22f), CircleShape)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (mood != null) Text(weekFaces[mood], fontSize = 13.sp)
                    }
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, appLocale()),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) gold else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(LocalHisnStatusColors.current.card).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun Meter(icon: String, color: Color, title: String, body: String, fraction: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).background(color.copy(alpha = .2f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 18.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row { Text(title, style = MaterialTheme.typography.titleSmall); Text("  $body", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Box(Modifier.fillMaxWidth().height(10.dp).background(Color.White.copy(alpha = .08f), CircleShape)) {
                Box(Modifier.fillMaxWidth(fraction).height(10.dp).background(color, CircleShape))
            }
        }
    }
}
