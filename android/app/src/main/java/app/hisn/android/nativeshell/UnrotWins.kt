package app.hisn.android.nativeshell

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas as ComposeCanvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisn.android.R
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/*
 * "Your wins": the person before Unrot, next to the person now.
 *
 * Before is one set of numbers saved once: the person's own feed habits in the days Android had
 * recorded before Unrot started (or, with no earlier record, their first week). Now is the last
 * seven whole days. Time given back adds, per finished day, how far under "before" that day was;
 * a heavy day adds nothing and takes nothing away, so the total only grows. Everything is read
 * on the phone from Android's usage record and kept on the phone.
 */

/** The saved starting point. Minutes of the day are -1 when unknown. */
internal class WinsBaseline(
    val feedMs: Long,
    val opens: Int,
    val firstMinute: Int,
    val lastMinute: Int,
    val longestMs: Long,
    val hours: LongArray?,
    /** True when no earlier record existed and the first week stands in for "before". */
    val firstWeek: Boolean
)

internal class WinsSummary(
    val day: Int,
    val start: LocalDate,
    val base: WinsBaseline,
    /** Average feed time a day over the last seven whole days (fewer when Unrot is newer). */
    val nowFeedMs: Long,
    val nowDays: Int,
    /** Whole days since the start, oldest first, for the line. */
    val daily: List<Long>,
    val givenBackMs: Long,
    val yesterdayGainMs: Long,
    val nowOpens: Int?,
    val nowFirst: Int?,
    val nowLast: Int?,
    val nowLongestMs: Long?,
    val nowHours: LongArray?,
    /** The lightest whole day since the start, for heavier stretches. */
    val lightestDate: LocalDate? = null,
    val lightestMs: Long = 0L,
    /** Where the last week's feed time went most: one app and its daily average. */
    val topApp: String? = null,
    val topAppMs: Long = 0L,
    /** Start hour of the four hours with the most feed time lately, or -1. */
    val peakStart: Int = -1
) {
    /** True when the last days were not lighter than before: show what went well, not a rising line. */
    val heavier: Boolean get() = percent >= 15 && nowFeedMs - base.feedMs >= 10 * 60_000L
    /** Change in feed time against before, as a whole percent (negative is less). */
    val percent: Int get() = if (base.feedMs <= 0) 0 else (((nowFeedMs - base.feedMs) * 100.0) / base.feedMs).roundToInt()
}

internal object Wins {
    private const val PREFS = "unrot_wins"
    /** Set by UI tests so screens depend only on their fake state. */
    @Volatile var disabled = false
    private const val MIN_BEFORE_DAYS = 3

    private fun avgMinute(values: List<Int>): Int? = values.filter { it >= 0 }.takeIf { it.isNotEmpty() }
        ?.let { (it.sum().toDouble() / it.size).roundToInt() }

    private fun baselineOf(days: List<FeedDay>, firstWeek: Boolean): WinsBaseline? {
        val used = days.filter { it.totalMs > 0 }
        if (used.size < MIN_BEFORE_DAYS) return null
        val detailed = used.filter { it.detailed }
        val hasDetail = detailed.size >= 2
        return WinsBaseline(
            feedMs = used.sumOf { it.totalMs } / used.size,
            opens = if (hasDetail) (detailed.sumOf { it.opens }.toDouble() / detailed.size).roundToInt() else -1,
            firstMinute = if (hasDetail) avgMinute(detailed.map { it.firstMinute }) ?: -1 else -1,
            lastMinute = if (hasDetail) avgMinute(detailed.map { it.lastMinute }) ?: -1 else -1,
            longestMs = if (hasDetail) detailed.sumOf { it.longestMs } / detailed.size else -1L,
            hours = if (hasDetail) LongArray(24) { h -> detailed.sumOf { it.hours[h] } / detailed.size } else null,
            firstWeek = firstWeek
        )
    }

    private fun save(context: Context, startDay: Long, b: WinsBaseline) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("start", startDay)
            .putLong("feed", b.feedMs).putInt("opens", b.opens)
            .putInt("first", b.firstMinute).putInt("last", b.lastMinute)
            .putLong("longest", b.longestMs)
            .putString("hours", b.hours?.joinToString(","))
            .putBoolean("firstWeek", b.firstWeek)
            .apply()
    }

    private fun load(context: Context, startDay: Long): WinsBaseline? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("feed") || p.getLong("start", Long.MIN_VALUE) != startDay) return null
        return WinsBaseline(
            feedMs = p.getLong("feed", 0L), opens = p.getInt("opens", -1),
            firstMinute = p.getInt("first", -1), lastMinute = p.getInt("last", -1),
            longestMs = p.getLong("longest", -1L),
            hours = p.getString("hours", null)?.split(',')?.mapNotNull { it.toLongOrNull() }
                ?.takeIf { it.size == 24 }?.toLongArray(),
            firstWeek = p.getBoolean("firstWeek", false)
        )
    }

    /** The whole picture, or null when there is no "before" yet. Reads usage: call off the main thread. */
    fun read(context: Context, state: HisnState): WinsSummary? {
        val day = state.personal.day
        if (disabled || day <= 0 || !Usage.hasAccess(context)) return null
        val history = FeedHistories.read(context, FeedHistories.feedApps(context, state)) ?: return null
        val today = LocalDate.now()
        val start = today.minusDays((day - 1).toLong())
        val startDay = start.toEpochDay()

        var base = load(context, startDay)
        if (base == null) {
            val before = history.days.filter { it.date.isBefore(start) }.takeLast(14)
            base = baselineOf(before, firstWeek = false)
            if (base == null && day > 7) {
                val firstWeek = history.days.filter { !it.date.isBefore(start) && it.date.isBefore(start.plusDays(7)) }
                base = baselineOf(firstWeek, firstWeek = true)
            }
            base?.let { save(context, startDay, it) } ?: return null
        }

        // Whole days since the start that Android still remembers.
        val whole = history.days.filter { !it.date.isBefore(start) && it.date.isBefore(today) }
        val gains = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = gains.edit()
        // A first-week "before" counts gains only after that week.
        val countFrom = if (base.firstWeek) start.plusDays(7) else start
        whole.forEach { d ->
            if (!d.date.isBefore(countFrom)) {
                edit.putLong("gain:${d.date.toEpochDay()}", (base.feedMs - d.totalMs).coerceAtLeast(0L))
            }
        }
        edit.apply()
        val givenBack = gains.all.entries.filter { it.key.startsWith("gain:") }
            .filter { (it.key.removePrefix("gain:").toLongOrNull() ?: -1L) >= countFrom.toEpochDay() }
            .sumOf { (it.value as? Long) ?: 0L }
        val yesterday = today.minusDays(1)
        val yesterdayGain = if (yesterday.isBefore(countFrom)) 0L else gains.getLong("gain:${yesterday.toEpochDay()}", 0L)

        val recent = whole.takeLast(7)
        if (recent.isEmpty()) return null
        val detailed = recent.filter { it.detailed }
        val hasDetail = detailed.size >= 2
        return WinsSummary(
            day = day, start = start, base = base,
            nowFeedMs = recent.sumOf { it.totalMs } / recent.size,
            nowDays = recent.size,
            daily = whole.map { it.totalMs },
            givenBackMs = givenBack,
            yesterdayGainMs = yesterdayGain,
            nowOpens = if (hasDetail) (detailed.sumOf { it.opens }.toDouble() / detailed.size).roundToInt() else null,
            nowFirst = if (hasDetail) avgMinute(detailed.map { it.firstMinute }) else null,
            nowLast = if (hasDetail) avgMinute(detailed.map { it.lastMinute }) else null,
            nowLongestMs = if (hasDetail) detailed.sumOf { it.longestMs } / detailed.size else null,
            nowHours = if (hasDetail) LongArray(24) { h -> detailed.sumOf { it.hours[h] } / detailed.size } else null,
            lightestDate = whole.minByOrNull { it.totalMs }?.date,
            lightestMs = whole.minOfOrNull { it.totalMs } ?: 0L,
            topApp = recent.flatMap { it.perApp.entries }.groupBy({ it.key }, { it.value })
                .mapValues { it.value.sum() }.maxByOrNull { it.value }?.key,
            topAppMs = recent.flatMap { it.perApp.entries }.groupBy({ it.key }, { it.value })
                .mapValues { it.value.sum() }.maxOfOrNull { it.value }?.let { it / recent.size } ?: 0L,
            peakStart = if (hasDetail) {
                val hrs = LongArray(24) { h -> detailed.sumOf { it.hours[h] } }
                (0..20).maxByOrNull { st -> (st until st + 4).sumOf { hrs[it] } } ?: -1
            } else -1
        )
    }

    /** The saved "before" a day in feed apps, or -1 before the app has worked one out. */
    fun savedBeforeMs(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).let { if (it.contains("feed")) it.getLong("feed", -1L) else -1L }

    /** Apps the person said are not feeds for them (Snapchat for messages, YouTube for lessons). */
    fun notFeeds(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("notFeeds", emptySet()) ?: emptySet()

    /** Marks or unmarks an app; "before" and the given-back days are worked out again without it. */
    fun setNotFeed(context: Context, pkg: String, notFeed: Boolean) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = notFeeds(context).toMutableSet().apply { if (notFeed) add(pkg) else remove(pkg) }
        val edit = p.edit().putStringSet("notFeeds", set)
        listOf("start", "feed", "opens", "first", "last", "longest", "hours", "firstWeek").forEach { edit.remove(it) }
        p.all.keys.filter { it.startsWith("gain:") }.forEach { edit.remove(it) }
        edit.apply()
    }

    /** "3 h 10", "45 m": short enough for a big number. */
    fun short(ms: Long): String {
        val minutes = (ms.coerceAtLeast(0L) / 60_000L)
        return if (minutes < 60) "$minutes m" else "${minutes / 60} h ${"%02d".format(minutes % 60)}"
    }

    /** Minutes on a day that runs 05:00 → 05:00, so 00:40 counts as later than 22:50. */
    fun late(minute: Int): Int = ((minute - 300) % 1440 + 1440) % 1440

    fun clock(minute: Int): String {
        val m = ((minute % 1440) + 1440) % 1440
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /** How many of the person's own "instead" the hours would buy, with the minutes one takes. */
    fun insteadUnits(key: String): Int = when (key) {
        "sleep" -> 60; "read" -> 30; "train" -> 45; "friends" -> 120
        "study" -> 50; "make" -> 60; "outside" -> 30; else -> 60
    }

    fun insteadPlural(key: String): Int = when (key) {
        "sleep" -> R.plurals.wins_instead_sleep; "read" -> R.plurals.wins_instead_read
        "train" -> R.plurals.wins_instead_train; "friends" -> R.plurals.wins_instead_friends
        "study" -> R.plurals.wins_instead_study; "make" -> R.plurals.wins_instead_make
        "outside" -> R.plurals.wins_instead_outside; else -> R.plurals.wins_instead_family
    }
}

private val WinsSage = Color(0xFF9EC39A)
private val WinsSageDark = Color(0xFF3F6B45)
private val WinsTomato = Color(0xFFE5553A)
private val WinsButter = Color(0xFFF4C34E)

@Composable
private fun WinsCard(color: Color = LocalHisnStatusColors.current.card, index: Int = 1, onClick: (() -> Unit)? = null,
                     content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.large
    val haptics = rememberHisnHaptics()
    Column(
        Modifier
            .riseIn(index)
            .fillMaxWidth()
            .unrotFrame(shape)
            .background(color, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button) { haptics.tap(); onClick() } else Modifier)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        content = content
    )
}

@Composable
private fun Chip(text: String, color: Color = WinsSage) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = UnrotInk,
        modifier = Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
private fun Caps(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(appLocale()), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp), color = color)
}

/** Today's main card: before Unrot crossed out, the last week large, and the line since day 1. */
@Composable
internal fun UnrotBeforeNowCard(summary: WinsSummary, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pct = summary.percent
    WinsCard(index = 1, onClick = onOpen) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Caps(stringResource(R.string.wins_feeds_day, summary.day), Modifier.weight(1f))
            // Only a drop gets a chip; a heavier stretch is said in words below, never as a red number.
            if (pct < 0) Chip(stringResource(R.string.wins_down, abs(pct)))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Caps(stringResource(if (summary.base.firstWeek) R.string.wins_first_week else R.string.wins_before))
                Text(Wins.short(summary.base.feedMs),
                    style = MaterialTheme.typography.headlineMedium.storySerif().copy(textDecoration = TextDecoration.LineThrough),
                    color = colors.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp).size(20.dp))
            Column(Modifier.weight(1.2f)) {
                Caps(stringResource(when {
                    summary.nowDays >= 7 -> R.string.wins_last_week
                    summary.nowDays == 1 -> R.string.wins_yesterday_label
                    else -> R.string.wins_so_far
                }), colors.onSurface)
                Text(Wins.short(countUp(summary.nowFeedMs / 60_000L) * 60_000L),
                    style = MaterialTheme.typography.displaySmall.storySerif(),
                    modifier = Modifier.semantics { heading() })
            }
        }
        if (pct > 0) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.wins_heavier), style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant)
        } else {
            // The two numbers above, said as a sentence for anyone who does not read the layout.
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.wins_plain, Wins.short(summary.nowFeedMs), Wins.short(summary.base.feedMs)),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        if (summary.daily.size >= 2) {
            Spacer(Modifier.height(12.dp))
            BeforeLine(summary)
        }
    }
}

@Composable
private fun Caps(text: String, modifier: Modifier) {
    Text(text.uppercase(appLocale()), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun BeforeLine(summary: WinsSummary) {
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val progress = grow(1f, 200)
    val locale = appLocale()
    val startLabel = summary.start.format(DateTimeFormatter.ofPattern("d MMM", locale))
    val description = stringResource(R.string.wins_line_description, Wins.short(summary.base.feedMs), Wins.short(summary.nowFeedMs))
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(stringResource(R.string.wins_before_line, Wins.short(summary.base.feedMs)),
                style = MaterialTheme.typography.labelSmall, color = WinsTomato)
        }
        ComposeCanvas(Modifier.fillMaxWidth().height(64.dp).semantics { contentDescription = description }) {
            val values = summary.daily
            val top = maxOf(summary.base.feedMs, values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat() * 1.1f
            val h = size.height
            val w = size.width
            fun y(v: Long) = h - (v / top) * (h - 6f) - 3f
            val baseY = y(summary.base.feedMs)
            drawLine(WinsTomato, Offset(0f, baseY), Offset(w, baseY), strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 9f)))
            val step = w / (values.size - 1).coerceAtLeast(1)
            val shown = (values.size * progress).coerceAtLeast(1f)
            val line = Path()
            val fill = Path()
            values.forEachIndexed { i, v ->
                if (i > shown) return@forEachIndexed
                val x = i * step
                if (i == 0) { line.moveTo(x, y(v)); fill.moveTo(x, h); fill.lineTo(x, y(v)) } else { line.lineTo(x, y(v)); fill.lineTo(x, y(v)) }
            }
            val lastI = minOf(values.lastIndex, shown.toInt())
            fill.lineTo(lastI * step, h); fill.close()
            drawPath(fill, WinsSage.copy(alpha = 0.5f))
            drawPath(line, ink, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val end = Offset(lastI * step, y(values[lastI]))
            drawCircle(WinsButter, 6.dp.toPx(), end)
            drawCircle(UnrotInk, 6.dp.toPx(), end, style = Stroke(2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(stringResource(R.string.wins_axis_start, startLabel), style = MaterialTheme.typography.labelSmall,
                color = muted, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.wins_axis_end), style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}

/** Hours given back: one tile per hour toward the next round number, and what they would buy. */
@Composable
internal fun UnrotGivenBackCard(summary: WinsSummary, onShare: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val dark = LocalDarkTheme.current
    val card = if (dark) Color(0xFF3A3016) else Color(0xFFFCEFC8)
    val sub = if (dark) Color(0xFFE6D3A0) else Color(0xFF7A6A3A)
    val hours = (summary.givenBackMs / 3_600_000L).toInt()
    val minutes = ((summary.givenBackMs / 60_000L) % 60).toInt()
    // The next round number: a day, two days, a week, then each further week.
    val goal = when {
        hours < 24 -> 24; hours < 48 -> 48; hours < 168 -> 168
        else -> ((hours / 168) + 1) * 168
    }
    val since = summary.start.format(DateTimeFormatter.ofPattern("d MMM", appLocale()))
    WinsCard(color = card, index = 2) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Caps(stringResource(R.string.wins_given_since, since), sub)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${countUp(hours.toLong())} h", style = MaterialTheme.typography.headlineLarge.storySerif())
                    Text(" $minutes m", style = MaterialTheme.typography.titleMedium, color = sub,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            if (summary.yesterdayGainMs >= 60_000L) {
                Text(stringResource(R.string.wins_yesterday, Wins.short(summary.yesterdayGainMs)),
                    style = MaterialTheme.typography.labelLarge, color = if (dark) WinsSage else WinsSageDark)
            }
        }
        if (summary.givenBackMs < 60_000L) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.wins_given_empty, Wins.short(summary.base.feedMs)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(10.dp))
        HourTiles(filled = hours, goal = goal, ink = if (dark) Color(0xFFF3ECDF) else UnrotInk)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(stringResource(R.string.wins_tile_note), style = MaterialTheme.typography.labelSmall, color = sub,
                modifier = Modifier.weight(1f))
            Text(stringResource(R.string.wins_tile_goal, goal - hours, goalName(goal)),
                style = MaterialTheme.typography.labelSmall, color = sub)
        }
        val instead = androidx.compose.runtime.remember {
            UnrotAnswers.INSTEAD.firstOrNull { it in UnrotAnswers.instead(context) }
        }
        if (instead != null && summary.givenBackMs >= 30 * 60_000L) {
            val count = (summary.givenBackMs / 60_000L / Wins.insteadUnits(instead)).toInt()
            if (count >= 1) {
                Spacer(Modifier.height(10.dp))
                Text(
                    resources.getQuantityString(Wins.insteadPlural(instead), count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .unrotFrame(RoundedCornerShape(14.dp))
                        .background(LocalHisnStatusColors.current.card, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
        if (summary.givenBackMs >= 60_000L || summary.percent < 0) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onShare) {
                Icon(Icons.Rounded.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.wins_share))
            }
        }
    }
}

@Composable
private fun goalName(goal: Int): String = when (goal) {
    24 -> stringResource(R.string.wins_goal_day)
    48 -> stringResource(R.string.wins_goal_two_days)
    168 -> stringResource(R.string.wins_goal_week)
    else -> pluralStringResource(R.plurals.wins_goal_weeks, goal / 168, goal / 168)
}

@Composable
private fun HourTiles(filled: Int, goal: Int, ink: Color) {
    // Up to 24 tiles on a row; past a day each tile stands for more hours.
    val perTile = ((goal + 23) / 24).coerceAtLeast(1)
    val tiles = (goal / perTile).coerceAtMost(24)
    val lit = (filled / perTile).coerceAtMost(tiles)
    val shown = grow(lit.toFloat(), 150)
    ComposeCanvas(Modifier.fillMaxWidth().height(16.dp)) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (tiles - 1)) / tiles
        val side = minOf(w, size.height)
        for (i in 0 until tiles) {
            val x = i * (w + gap)
            val on = i < shown
            val newest = i == lit - 1
            val fill = when { newest -> WinsTomato; on -> WinsButter; else -> Color.Transparent }
            drawRoundRect(fill, Offset(x, (size.height - side) / 2), Size(w, side), CornerRadius(3.dp.toPx()))
            drawRoundRect(if (on) ink else ink.copy(alpha = 0.3f), Offset(x, (size.height - side) / 2), Size(w, side),
                CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        }
    }
}

/** The top of Progress: before → now for every measure, and where in the day the time came back. */
@Composable
internal fun UnrotWinsSection(summary: WinsSummary, onShare: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val goals = UnrotAnswers.goals(LocalContext.current)
    WinsCard(index = 0) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Caps(stringResource(R.string.wins_section), Modifier.weight(1f))
            if (summary.percent < 0 || summary.givenBackMs >= 60_000L) TextButton(onClick = onShare) {
                Icon(Icons.Rounded.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.wins_share))
            }
        }
        val b = summary.base
        Dumbbell(stringResource(R.string.wins_feed_time), b.feedMs.toFloat(), summary.nowFeedMs.toFloat(),
            Wins.short(b.feedMs), Wins.short(summary.nowFeedMs), lowerIsBetter = true, tag = null,
            delta = if (summary.percent < 0) stringResource(R.string.wins_down, abs(summary.percent)) else null)
        if (b.opens >= 0 && summary.nowOpens != null) {
            val d = b.opens - summary.nowOpens
            Dumbbell(stringResource(R.string.wins_opens), b.opens.toFloat(), summary.nowOpens.toFloat(),
                "${b.opens}", "${summary.nowOpens}", lowerIsBetter = true, tag = null,
                delta = if (d > 0) stringResource(R.string.wins_fewer, d) else null)
        }
        if (b.firstMinute >= 0 && summary.nowFirst != null) {
            val d = Wins.late(summary.nowFirst) - Wins.late(b.firstMinute)
            Dumbbell(stringResource(R.string.wins_first), b.firstMinute.toFloat(), summary.nowFirst.toFloat(),
                Wins.clock(b.firstMinute), Wins.clock(summary.nowFirst), lowerIsBetter = false,
                tag = if ("mornings" in goals) stringResource(R.string.wins_tag_mornings) else null,
                delta = if (d >= 10) stringResource(R.string.wins_later, Wins.short(d * 60_000L)) else null, clock = true)
        }
        if (b.lastMinute >= 0 && summary.nowLast != null) {
            val d = Wins.late(b.lastMinute) - Wins.late(summary.nowLast)
            Dumbbell(stringResource(R.string.wins_last), b.lastMinute.toFloat(), summary.nowLast.toFloat(),
                Wins.clock(b.lastMinute), Wins.clock(summary.nowLast), lowerIsBetter = true,
                tag = if ("sleep" in goals) stringResource(R.string.wins_tag_sleep) else null,
                delta = if (d >= 10) stringResource(R.string.wins_earlier, Wins.short(d * 60_000L)) else null, clock = true)
        }
        if (b.longestMs > 0 && summary.nowLongestMs != null) {
            val d = b.longestMs - summary.nowLongestMs
            Dumbbell(stringResource(R.string.wins_longest), b.longestMs.toFloat(), summary.nowLongestMs.toFloat(),
                Wins.short(b.longestMs), Wins.short(summary.nowLongestMs), lowerIsBetter = true,
                tag = if ("focus" in goals) stringResource(R.string.wins_tag_focus) else null,
                delta = if (d >= 60_000L) stringResource(R.string.wins_shorter, Wins.short(d)) else null)
        }
    }
    val before = summary.base.hours
    val now = summary.nowHours
    if (before != null && now != null && before.sum() > 0) {
        Spacer(Modifier.height(8.dp))
        WinsCard(index = 1) {
            Caps(stringResource(R.string.wins_where))
            Spacer(Modifier.height(10.dp))
            HourStripRow(stringResource(R.string.wins_before_short), before, WinsTomato, maxOf(before.max(), now.max()))
            Spacer(Modifier.height(6.dp))
            HourStripRow(stringResource(R.string.wins_now), now, WinsSageDark, maxOf(before.max(), now.max()))
            Row(Modifier.fillMaxWidth().padding(start = 56.dp, top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("0", "6", "12", "18", "24").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
            // The four-hour stretch that lost the most feed time.
            val drops = (0..20).map { s -> s to (s until s + 4).sumOf { before[it] - now[it] } }
            val (s, drop) = drops.maxBy { it.second }
            if (drop >= 10 * 60_000L) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.wins_biggest, Wins.clock(s * 60), Wins.clock((s + 4) * 60), Wins.short(drop)),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun HourStripRow(label: String, hours: LongArray, color: Color, top: Long) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.width(56.dp))
        val g = grow(1f, 100)
        ComposeCanvas(Modifier.weight(1f).height(26.dp)) {
            val gap = 2.dp.toPx()
            val w = (size.width - gap * 23) / 24
            for (h in 0 until 24) {
                val v = if (top > 0) hours[h].toFloat() / top else 0f
                drawRoundRect(color.copy(alpha = if (v <= 0f) 0.08f else (0.18f + 0.82f * v) * g),
                    Offset(h * (w + gap), 0f), Size(w, size.height), CornerRadius(3.dp.toPx()))
            }
        }
    }
}

/** One measure: a red dot (before) moving to a green dot (now) on a shared scale. */
@Composable
private fun Dumbbell(label: String, before: Float, now: Float, beforeText: String, nowText: String,
                     lowerIsBetter: Boolean, tag: String?, delta: String?, clock: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val better = if (lowerIsBetter) now < before else now > before
    Column(Modifier.padding(top = 12.dp).semantics(mergeDescendants = true) { }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            if (tag != null) {
                Spacer(Modifier.width(6.dp))
                Text(tag, style = MaterialTheme.typography.labelSmall, color = UnrotInk,
                    modifier = Modifier.border(1.5.dp, UnrotInk, RoundedCornerShape(50))
                        .background(WinsButter, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp))
            }
            Spacer(Modifier.weight(1f))
            if (delta != null) Text(delta, style = MaterialTheme.typography.labelLarge,
                color = if (better) (if (LocalDarkTheme.current) WinsSage else WinsSageDark) else colors.onSurfaceVariant)
        }
        // Clock times sit on a day from 05:00 to 05:00 so late nights stay in order.
        fun norm(v: Float) = if (clock) ((v - 300f + 1440f) % 1440f) else v
        val lo = if (clock) 0f else 0f
        val hi = if (clock) 1440f else maxOf(before, now).coerceAtLeast(1f) * 1.12f
        val g = grow(1f, 150)
        val ink = colors.onSurface
        ComposeCanvas(Modifier.fillMaxWidth().height(20.dp).padding(top = 4.dp)) {
            val w = size.width
            val cy = size.height / 2
            val r = 6.dp.toPx()
            fun x(v: Float) = r + (norm(v) - lo) / (hi - lo) * (w - 2 * r)
            drawLine(ink.copy(alpha = 0.12f), Offset(0f, cy), Offset(w, cy), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            val xb = x(before)
            val xn = xb + (x(now) - xb) * g
            drawLine(ink, Offset(xb, cy), Offset(xn, cy), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(WinsTomato, r, Offset(xb, cy)); drawCircle(UnrotInk, r, Offset(xb, cy), style = Stroke(1.5.dp.toPx()))
            drawCircle(if (better) WinsSage else WinsButter, r, Offset(xn, cy)); drawCircle(UnrotInk, r, Offset(xn, cy), style = Stroke(1.5.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.wins_was, beforeText), style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(nowText, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
    }
}

/**
 * Share card "A · Rows": a 1080×1920 picture with a headline, before/now bars for up to three
 * measures, and the app's name. No app names, no times of day beyond the person's own averages.
 */
internal fun drawWinsCard(context: Context, s: WinsSummary): Bitmap {
        val res = context.resources
        val w = 1080; val h = 1920
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        // Night gold, like the app: warm black, ivory type, gold for what got better.
        val ink = 0xFFF3ECDF.toInt(); val muted = 0xFFA39A86.toInt(); val gold = 0xFFF2C94F.toInt()
        val night = 0xFF0E0D0B.toInt(); val dim = 0xFF2B2822.toInt()
        c.drawColor(night)
        c.drawCircle(w * 0.5f, 0f, w * 0.95f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(w * 0.5f, 0f, w * 0.95f, intArrayOf(0x40F2C94F, 0x00F2C94F),
                null, android.graphics.Shader.TileMode.CLAMP)
        })
        val serif = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        val sans = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        fun paint(color: Int, size: Float, face: Typeface = sans, align: Paint.Align = Paint.Align.LEFT) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = face; textAlign = align }
        val margin = 80f
        // Brand and day.
        c.drawText("un", margin, 170f, paint(ink, 78f, serif))
        val unW = paint(ink, 78f, serif).measureText("un")
        c.drawText("rot", margin + unW, 170f, paint(gold, 78f, serif))
        c.drawText(res.getString(R.string.wins_card_days, s.day).uppercase(), w - margin, 165f,
            paint(muted, 34f, sans, Paint.Align.RIGHT))
        // Headline.
        val pct = -s.percent
        val head = if (pct > 0) res.getString(R.string.wins_card_head, pct) else res.getString(R.string.wins_card_head_even)
        var y = 360f
        head.split('\n').forEachIndexed { i, line ->
            c.drawText(line, margin, y, paint(if (i == 0) ink else gold, 132f, serif)); y += 140f
        }
        y += 40f
        // Rows.
        data class RowSpec(val label: String, val before: Float, val now: Float, val bt: String, val nt: String, val delta: String?)
        val rows = buildList {
            add(RowSpec(res.getString(R.string.wins_feed_time), s.base.feedMs.toFloat(), s.nowFeedMs.toFloat(),
                Wins.short(s.base.feedMs), Wins.short(s.nowFeedMs), if (pct > 0) "−$pct%" else null))
            if (s.base.opens >= 0 && s.nowOpens != null) add(RowSpec(res.getString(R.string.wins_opens), s.base.opens.toFloat(),
                s.nowOpens.toFloat(), "${s.base.opens}", "${s.nowOpens}", (s.base.opens - s.nowOpens).takeIf { it > 0 }?.let { "−$it" }))
            if (s.base.lastMinute >= 0 && s.nowLast != null) {
                fun late(m: Int) = ((m - 300 + 1440) % 1440).toFloat()
                add(RowSpec(res.getString(R.string.wins_last), late(s.base.lastMinute), late(s.nowLast),
                    Wins.clock(s.base.lastMinute), Wins.clock(s.nowLast),
                    (Wins.late(s.base.lastMinute) - Wins.late(s.nowLast)).takeIf { it >= 10 }?.let { res.getString(R.string.wins_earlier, Wins.short(it * 60_000L)) }))
            }
        }.take(3)
        val label = paint(ink, 46f, sans)
        val small = paint(muted, 30f, sans)
        val value = paint(ink, 44f, serif, Paint.Align.RIGHT)
        rows.forEach { r ->
            c.drawText(r.label, margin, y, label)
            r.delta?.let { d ->
                val chip = paint(gold, 32f, sans)
                val cw = chip.measureText(d) + 44f
                val rect = RectF(w - margin - cw, y - 44f, w - margin, y + 10f)
                c.drawRoundRect(rect, 30f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A2416.toInt() })
                c.drawRoundRect(rect, 30f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x80F2C94F.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f })
                c.drawText(d, rect.left + 22f, y - 6f, chip)
            }
            y += 40f
            val top = maxOf(r.before, r.now).coerceAtLeast(1f)
            val barX = margin + 150f
            val barW = w - margin - barX
            listOf(Triple(res.getString(R.string.wins_before_short), r.before, r.bt), Triple(res.getString(R.string.wins_now), r.now, r.nt))
                .forEachIndexed { i, (name, v, text) ->
                    c.drawText(name, margin, y + 46f, small)
                    val len = (barW * (v / top)).coerceAtLeast(160f)
                    val rect = RectF(barX, y, barX + len, y + 64f)
                    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        if (i == 0) color = dim
                        else shader = android.graphics.LinearGradient(rect.left, 0f, rect.right, 0f,
                            0xFFE0A532.toInt(), 0xFFF9DE85.toInt(), android.graphics.Shader.TileMode.CLAMP)
                    }
                    c.drawRoundRect(rect, 20f, 20f, fill)
                    if (i == 0) {
                        val hatch = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF35312A.toInt(); strokeWidth = 12f }
                        c.save(); c.clipRect(rect)
                        var hx = rect.left - rect.height()
                        while (hx < rect.right) { c.drawLine(hx, rect.bottom, hx + rect.height(), rect.top, hatch); hx += 26f }
                        c.restore()
                    }
                    c.drawText(text, rect.right - 22f, y + 47f, value.apply { color = if (i == 0) muted else 0xFF1A1915.toInt() })
                    y += 84f
                }
            y += 56f
        }
        // Given back line.
        if (s.givenBackMs >= 3_600_000L) {
            c.drawText(res.getString(R.string.wins_card_given, (s.givenBackMs / 3_600_000L).toInt()), margin, y + 20f,
                paint(ink, 50f, serif))
        }
        // Footer.
        c.drawText(res.getString(R.string.wins_card_cta), w - margin, h - 150f, paint(ink, 36f, sans, Paint.Align.RIGHT))
        c.drawText(res.getString(R.string.wins_card_where), w - margin, h - 100f, paint(muted, 32f, sans, Paint.Align.RIGHT))
        val markY = h - 150f
        c.drawText("un", margin, markY + 20f, paint(ink, 64f, serif))
        c.drawText("rot", margin + paint(ink, 64f, serif).measureText("un"), markY + 20f, paint(gold, 64f, serif))

        return bitmap
}

internal fun shareWinsCard(context: Context, s: WinsSummary) {
    runCatching {
        val res = context.resources
        val bitmap = drawWinsCard(context, s)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "wins.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".share", file)
        val text = res.getString(R.string.wins_share_text, "https://play.google.com/store/apps/details?id=" + context.packageName)
        val send = Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}


/**
 * Adult content stays a core part of Unrot: what the filter kept out this week, on this phone
 * only. It never appears on a share card.
 */
@Composable
internal fun UnrotKeptOutCard(state: HisnState, resumes: Int) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val week = androidx.compose.runtime.remember(resumes) { app.hisn.android.StepIns.range(context, today.minusDays(6), today) }
    val adult = week[app.hisn.android.StepIns.ADULT] ?: 0
    val sites = week[app.hisn.android.StepIns.SITES] ?: 0
    val filterOn = state.dns.on
    val colors = MaterialTheme.colorScheme
    WinsCard(index = 2) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(20.dp),
                tint = if (filterOn) (if (LocalDarkTheme.current) WinsSage else WinsSageDark) else colors.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Caps(stringResource(R.string.wins_kept_title))
        }
        Spacer(Modifier.height(10.dp))
        if (!filterOn) {
            Text(stringResource(R.string.wins_kept_off), style = MaterialTheme.typography.bodyLarge)
        } else if (adult == 0 && sites == 0) {
            Text(stringResource(R.string.wins_kept_none), style = MaterialTheme.typography.bodyLarge)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeptStat(countUp(adult.toLong()).toString(), pluralStringResource(R.plurals.wins_kept_adult, adult), Modifier.weight(1f))
                KeptStat(countUp(sites.toLong()).toString(), pluralStringResource(R.plurals.wins_kept_sites, sites), Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.wins_kept_private), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun KeptStat(number: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(number, style = MaterialTheme.typography.headlineMedium.storySerif())
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


/**
 * A heavier stretch, said straight: how much it was, how much more than before, and what that
 * pace costs in a year. Warm tint, red only on the numbers, and one fix right under it.
 */
@Composable
internal fun UnrotHeavyCard(summary: WinsSummary, state: HisnState, resumes: Int, onOpen: (String) -> Unit, onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    // Premium and calm: the app's own card, a soft coral only on the numbers, no alarm red.
    val warn = if (dark) Color(0xFFF0A27A) else Color(0xFFB5532C)
    val tint = LocalHisnStatusColors.current.card
    val ink = if (dark) Color(0xFFF3ECDF) else UnrotInk
    val shape = MaterialTheme.shapes.large
    val yearHours = (summary.nowFeedMs * 365 / 3_600_000L).toInt()
    val yearDays = yearHours / 24
    Column(
        Modifier
            .riseIn(1)
            .fillMaxWidth()
            .unrotFrame(shape)
            .background(tint, shape)
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(warn.copy(alpha = if (dark) 0.16f else 0.10f), Color.Transparent),
                    center = Offset(size.width, 0f), radius = size.width * 0.7f), radius = size.width * 0.7f,
                    center = Offset(size.width, 0f))
            }
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Caps(stringResource(R.string.heavy_label, summary.day), warn)
            }
            Float3D(app.hisn.android.R.drawable.unrot_3d_battery, 48.dp)
        }
        Spacer(Modifier.height(4.dp))
        val amount = Wins.short(summary.nowFeedMs)
        val head = if (summary.nowDays == 1) stringResource(R.string.heavy_head_yesterday, amount)
            else stringResource(R.string.heavy_head_week, amount)
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                val at = head.indexOf(amount)
                if (at < 0) append(head) else {
                    append(head.substring(0, at))
                    withStyle(androidx.compose.ui.text.SpanStyle(color = warn)) { append(amount) }
                    append(head.substring(at + amount.length))
                }
            },
            style = MaterialTheme.typography.headlineSmall.storySerif(),
            color = ink,
            modifier = Modifier.semantics { heading() }
        )
        if (summary.percent > 0) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.heavy_more, summary.percent, Wins.short(summary.base.feedMs)),
                style = MaterialTheme.typography.titleMedium, color = warn)
        }
        Spacer(Modifier.height(12.dp))
        HeavyBars(summary, ink, warn)
        Spacer(Modifier.height(6.dp))
        HeavyFact(stringResource(R.string.heavy_year, java.text.NumberFormat.getIntegerInstance(appLocale()).format(yearHours), yearDays), ink)
        if (summary.nowOpens != null && summary.nowOpens > 0) {
            HeavyFact(stringResource(R.string.heavy_opens_day, summary.nowOpens), ink)
        }
        val evening = summary.peakStart >= 18 || summary.peakStart in 0..3
        val app = summary.topApp
        if (app != null && summary.topAppMs >= 20 * 60_000L) {
            HeavyFact(stringResource(R.string.heavy_where, appLabel(context, app), Wins.short(summary.topAppMs)), ink)
            // Not every hour in an app is a feed: chats, lessons. One tap takes it out of the count.
            val snackbar = LocalHisnSnackbar.current
            val haptics = rememberHisnHaptics()
            val label = appLabel(context, app)
            val undoLabel = stringResource(R.string.heavy_not_feed_undo)
            val doneText = stringResource(R.string.heavy_not_feed_done, label)
            Text(stringResource(R.string.heavy_not_feed, label), color = warn,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .padding(start = 16.dp, top = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                        haptics.tap()
                        Wins.setNotFeed(context, app, true)
                        onChanged()
                        snackbar(HisnMessage(doneText, undoLabel) { Wins.setNotFeed(context, app, false); onChanged() })
                    }
                    .padding(vertical = 12.dp))
            Spacer(Modifier.height(4.dp))
            val quiet = evening && summary.peakStart >= 0
            androidx.compose.material3.Button(
                onClick = { onOpen(if (quiet) "schedule" else "limit") },
                shape = RoundedCornerShape(30.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (dark) Color(0xFFF2C94F) else UnrotInk,
                    contentColor = if (dark) Color(0xFF14130F) else Color(0xFFFFFDF8))
            ) {
                Text(if (quiet) stringResource(R.string.heavy_fix_quiet, Wins.clock(summary.peakStart * 60))
                    else stringResource(R.string.heavy_fix_limit, appLabel(context, app)))
            }
        }
    }
}

@Composable
private fun HeavyFact(text: String, ink: Color) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 8.dp).size(6.dp).background(ink, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = ink)
    }
}


/** Before and the latest stretch as two bars on one scale; only the latest one is warm. */
@Composable
private fun HeavyBars(summary: WinsSummary, ink: Color, warn: Color) {
    val top = maxOf(summary.base.feedMs, summary.nowFeedMs).coerceAtLeast(1L).toFloat()
    val g = grow(1f, 120)
    val rows = listOf(
        Triple(stringResource(R.string.wins_before_short), summary.base.feedMs, false),
        Triple(stringResource(if (summary.nowDays == 1) R.string.wins_yesterday_label else R.string.wins_last_week),
            summary.nowFeedMs, true)
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (name, ms, now) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.7f),
                    modifier = Modifier.width(78.dp))
                Box(Modifier.weight(1f).height(22.dp)) {
                    val frac = (ms / top * g).coerceIn(0.04f, 1f)
                    Box(
                        Modifier.fillMaxWidth(frac).height(22.dp)
                            .background(
                                if (now) Brush.horizontalGradient(listOf(warn.copy(alpha = 0.55f), warn))
                                else Brush.horizontalGradient(listOf(ink.copy(alpha = 0.14f), ink.copy(alpha = 0.14f))),
                                RoundedCornerShape(8.dp))
                    )
                }
                Text(Wins.short(ms), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (now) warn else ink, modifier = Modifier.padding(start = 8.dp).width(56.dp))
            }
        }
    }
}
