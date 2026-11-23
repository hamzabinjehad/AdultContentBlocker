package app.hisn.android.nativeshell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hisn.android.R
import app.hisn.android.StepIns
import app.hisn.core.PersonalProfile
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Unrot's Progress, round 2. Five questions, always in the same order, one card each:
 *   1 How was this week?   2 Where did it go?   3 Is Unrot helping?
 *   4 When is it hardest?  5 Are you getting it back?
 * Tapping a day opens it against the person's own usual (Opal's best screen, without its
 * invented score and without locking past days). Every number is read on the phone from
 * Android's own record or from Unrot's own day counts; nothing is projected or scored.
 */

private val Good = Color(0xFF3F8A5A)
private val Bad = Color(0xFFD2543A)

/** The language the app is really showing: Arabic or English, never the phone's third language. */
@Composable
internal fun appLocale(): Locale {
    val system = androidx.core.os.ConfigurationCompat.getLocales(LocalConfiguration.current)[0]
    return if (system?.language == "ar") system else Locale.ENGLISH
}

private fun dur(ms: Long): String = Usage.formatDuration(kotlin.math.abs(ms))

private fun clock(minute: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", (minute / 60) % 24, minute % 60)

private val QuestionTiles = listOf(TileButter, TileBlush, TileSage, TileSky, TilePaper)

@Composable
private fun Question(number: Int, text: Int) {
    // A quiet serif heading; numbered circles skipped numbers when a section was hidden.
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleLarge.storySerif(),
        modifier = Modifier.padding(start = 2.dp, top = 20.dp, bottom = 2.dp).riseIn(number).semantics { heading() }
    )
}

@Composable
private fun ProgressCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.extraLarge
    Column(
        Modifier
            .riseIn(1)
            .fillMaxWidth()
            .unrotFrame(shape)
            .background(LocalHisnStatusColors.current.card, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Delta(text: String, good: Boolean?) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = when (good) { true -> Good; false -> Bad; null -> MaterialTheme.colorScheme.onSurfaceVariant }
    )
}

/** "↓ 48 min" / "↑ 25 min" for a change in time; null when it is under a minute. */
private fun timeDelta(diff: Long): Pair<String, Boolean>? =
    if (kotlin.math.abs(diff) < 60_000L) null else (if (diff < 0) "↓ " else "↑ ") + dur(diff) to (diff < 0)

@Composable
internal fun UnrotProgress(state: HisnState, padding: PaddingValues, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    var resumes by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { resumes++; onPauseOrDispose { } }
    val usageOn = remember(resumes) { Usage.hasAccess(context) }
    val apps = remember(resumes, state.blockedApps.size, state.appLimits.size) { FeedHistories.feedApps(context, state) }
    val ruled = remember(resumes, state.blockedApps.size, state.appLimits.size, state.shortVideos.on) {
        FeedHistories.ruledApps(context, state)
    }
    val history by produceState<FeedHistory?>(null, resumes, apps, usageOn) {
        if (!usageOn) { value = null; return@produceState }
        value = withContext(Dispatchers.IO) { FeedHistories.read(context, apps, fresh = resumes > 1) }
    }
    val today = LocalDate.now()
    val steps = remember(resumes) { StepIns.range(context, today.minusDays(6), today) }
    val goals = remember(resumes) { UnrotAnswers.goals(context) }
    val onDay = { date: LocalDate -> onOpen("progress:day:${date.toEpochDay()}") }
    val wins by produceState<WinsSummary?>(null, resumes, usageOn, state.personal.day) {
        value = if (!usageOn) null else withContext(Dispatchers.IO) { runCatching { Wins.read(context, state) }.getOrNull() }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // CleanPath's Analytics top: gauge, two tiles, meters (see UnrotAnalyticsTop.kt).
        item(key = "analytics-top") { UnrotAnalyticsTop(wins, state.personal.day, steps) }
        // The day count and the next milestone (moved from Home, which now ends like the mockup).
        item(key = "day") {
            UnrotDayCard(
                day = state.personal.day,
                pauses = state.personal.pauses,
                onStart = { onOpen("personal") },
                hero = false,
                sticker = MilestoneMemory.celebrated(context)
            )
        }
        // One swaying cartoon tree per day (UnrotWood.kt, from the trees-and-animations work).
        wins?.let { w -> item(key = "wood") { UnrotWoodCard(w) } }
        wins?.let { w ->
            if (!w.heavier) item { UnrotProgressHero(w) }
            item {
                if (w.heavier) UnrotHeavyCard(w, state, resumes, onOpen, onChanged = { resumes++ })
                else UnrotWinsSection(w, onShare = { shareWinsCard(context, w) })
            }
            item { UnrotSpreadCard(w) }
        }
        item { UnrotKeptOutCard(state, resumes) }
        item { Question(1, R.string.pq_week) }
        item {
            if (!usageOn) UsageInvite { state.openPermission("usage") } else WeekQuestion(history, onDay)
        }
        if (usageOn) {
            item { Question(2, R.string.pq_where) }
            item { AppsQuestion(history, ruled) }
        }
        item { Question(3, R.string.pq_helping) }
        item { HelpingQuestion(history, steps) }
        val peak = state.personal.peakStart >= 0
        if (peak || history != null) {
            item { Question(4, R.string.pq_hardest) }
            item { if (peak) HardHoursCard(state) else history?.let { FeedHoursCard(it) } }
        }
        item { Question(5, R.string.pq_back) }
        item { GoalsQuestion(history, goals, state.blockedLookups, onEdit = { onOpen("personal") }) }
        item { UnrotMedalWall(state.personal.day) }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.pp_private), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

// 1 · How was this week?

@Composable
private fun WeekQuestion(history: FeedHistory?, onDay: (LocalDate) -> Unit) {
    val haptics = rememberHisnHaptics()
    var four by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val resources = LocalResources.current
    ProgressCard {
        ConnectedChoice(
            options = listOf(false, true),
            selected = four,
            label = { stringResource(if (it) R.string.pw_four else R.string.pw_this_week) },
            onSelect = { four = it }
        )
        Spacer(Modifier.height(8.dp))
        if (history == null) {
            LoadingBlocks(14, 34, chart = true)
            return@ProgressCard
        }
        if (!four) {
            val week = history.week(0)
            val total = week.sumOf { it.totalMs }
            val last = history.lastWeekComparable({ it.totalMs })
            val usedLastWeek = history.week(1).count { it.totalMs > 0 } >= app.hisn.core.FeedWeek.MIN_DAYS_TO_COMPARE
            Label(stringResource(R.string.pw_label))
            val diff = if (last != null && usedLastWeek) total - last else null
            Text(
                when {
                    diff == null -> dur(total)
                    diff <= -5 * 60_000L -> stringResource(R.string.pw_less, dur(diff))
                    diff >= 5 * 60_000L -> stringResource(R.string.pw_more, dur(diff))
                    else -> stringResource(R.string.pw_same)
                },
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                if (diff == null) stringResource(R.string.pw_no_compare)
                else stringResource(R.string.pw_than, dur(total)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val average = if (usedLastWeek) history.week(1).sumOf { it.totalMs } / 7 else null
            WeekBars(week, average, onDay)
            Text(stringResource(R.string.pw_tap_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val today = LocalDate.now()
                androidx.compose.material3.OutlinedButton(onClick = { haptics.tap(); onDay(today) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pw_open_today))
                }
                androidx.compose.material3.OutlinedButton(onClick = { haptics.tap(); onDay(today.minusDays(1)) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pw_open_yesterday))
                }
            }
        } else {
            val weeks = history.fourWeeks()
            val known = history.days.count { it.totalMs > 0 } >= 21
            Label(stringResource(R.string.pw4_label))
            Text(stringResource(R.string.pw4_head, dur(weeks.last())), style = MaterialTheme.typography.headlineMedium)
            if (known) {
                Text(stringResource(R.string.pw4_sub, dur(weeks.dropLast(1).sum() / 3)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val labels = listOf(
                resources.getString(R.string.pw4_ago, 3), resources.getString(R.string.pw4_ago, 2),
                resources.getString(R.string.pw4_last), resources.getString(R.string.pw4_this)
            )
            Bars(weeks.toList(), labels, highlight = 3, average = null, onTap = null)
        }
    }
}

@Composable
private fun WeekBars(week: List<FeedDay>, average: Long?, onDay: (LocalDate) -> Unit) {
    val locale = appLocale()
    val labels = week.map { it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) }
    Bars(week.map { it.totalMs }, labels, highlight = week.lastIndex, average = average,
        onTap = { onDay(week[it].date) },
        averageLabel = average?.let { stringResource(R.string.pw_avg_line, dur(it)) })
}

/** Bars with an optional dashed average line; the highlighted bar is today or this week. */
@Composable
private fun Bars(
    values: List<Long>,
    labels: List<String>,
    highlight: Int,
    average: Long?,
    onTap: ((Int) -> Unit)?,
    averageLabel: String? = null
) {
    val haptics = rememberHisnHaptics()
    val colors = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val top = (values + listOfNotNull(average)).maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val chartHeight = 132.dp
    val ink = colors.onSurface
    Box(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().height(chartHeight),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            values.forEachIndexed { index, value ->
                val tap = onTap
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (tap != null) Modifier.clickable(role = Role.Button) { haptics.tap(); tap(index) } else Modifier)
                        .semantics(mergeDescendants = true) { contentDescription = "${labels[index]} ${dur(value)}" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    val share = grow((value.toFloat() / top).coerceIn(0f, 1f), delayMs = 120 + index * 60)
                    val isHighlight = index == highlight
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .fillMaxHeight(share.coerceAtLeast(0.03f))
                            .background(
                                Brush.verticalGradient(when {
                                    isHighlight -> listOf(Color(0xFFF9DE85), Color(0xFFF4C34E), Color(0xFFD99A2B))
                                    dark -> listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.07f))
                                    else -> listOf(UnrotInk.copy(alpha = 0.17f), UnrotInk.copy(alpha = 0.06f))
                                }),
                                RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                            )
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(labels[index], style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        color = if (isHighlight) colors.onSurface else colors.onSurfaceVariant)
                }
            }
        }
        if (average != null && average > 0) {
            // The labels row is about 22dp tall; the bars stand on it.
            val barArea = chartHeight - 22.dp
            val fromBottom = barArea * grow((average.toFloat() / top).coerceIn(0f, 1f), delayMs = 500)
            Canvas(Modifier.fillMaxWidth().height(chartHeight)) {
                val y = size.height - 22.dp.toPx() - fromBottom.toPx()
                drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
            if (averageLabel != null) {
                Text(
                    averageLabel,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .offset(y = barArea - fromBottom - 18.dp)
                        .background(LocalHisnStatusColors.current.card, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp)
                )
            }
        }
    }
}

// 2 · Where did it go?

@Composable
private fun AppsQuestion(history: FeedHistory?, ruled: Set<String>) {
    val context = LocalContext.current
    ProgressCard {
        if (history == null) { LoadingBlocks(44, 44); return@ProgressCard }
        val week = history.week(0)
        val totals = history.apps.associateWith { pkg -> week.sumOf { it.perApp[pkg] ?: 0L } }
            .filterValues { it >= 60_000L }
            .entries.sortedByDescending { it.value }
        if (totals.isEmpty()) {
            Text(stringResource(R.string.pa_none), style = MaterialTheme.typography.bodyMedium)
            return@ProgressCard
        }
        val top = totals.first().value.coerceAtLeast(1L)
        val lastKnown = history.week(1).count { it.totalMs > 0 } >= app.hisn.core.FeedWeek.MIN_DAYS_TO_COMPARE
        totals.take(5).forEach { (pkg, ms) ->
            val last = if (lastKnown) history.lastWeekComparable({ it.perApp[pkg] ?: 0L }) else null
            val delta = last?.let { timeDelta(ms - it) }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIcon(pkg, 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(appLabel(context, pkg), style = MaterialTheme.typography.titleSmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(6.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(3.dp))) {
                        Box(Modifier.fillMaxWidth(grow((ms.toFloat() / top).coerceIn(0.03f, 1f), 200)).height(6.dp)
                            .background(if (pkg in ruled) Bad else Good, RoundedCornerShape(3.dp)))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(dur(ms), style = MaterialTheme.typography.titleSmall)
                    if (delta != null) Delta(delta.first, delta.second)
                }
            }
        }
        Text(stringResource(R.string.pa_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// 3 · Is Unrot helping?

@Composable
private fun HelpingQuestion(history: FeedHistory?, steps: Map<String, Int>) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val locale = appLocale()
    ProgressCard {
        if (history != null) {
            val week = history.week(0).filter { it.detailed }
            if (week.isNotEmpty()) {
                val opens = week.sumOf { it.opens }
                Label(stringResource(R.string.ph_opens_label))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${countUp(opens.toLong())}", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ph_opens_word), style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                val last = history.lastWeekComparable({ it.opens.toLong() }, needsDetail = true)
                val perDay = stringResource(R.string.ph_per_day, (opens + week.size / 2) / week.size)
                val diff = last?.let { opens - it.toInt() }
                Text(
                    when {
                        diff == null || diff == 0 -> perDay
                        diff < 0 -> perDay + " · " + stringResource(R.string.ph_fewer, -diff)
                        else -> perDay + " · " + stringResource(R.string.ph_more_opens, diff)
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        val parts = listOf(
            Triple(StepIns.CLOSED, Color(0xFFF4C34E), R.string.ph_closed),
            Triple(StepIns.WALKED, Color(0xFF9EC39A), R.string.ph_walked),
            Triple(StepIns.BREATHED, Color(0xFF9CC8E0), R.string.ph_breathed),
            Triple(StepIns.OPENED, Color(0xFFE6DDCB), R.string.ph_opened)
        )
        val total = parts.sumOf { steps[it.first] ?: 0 }
        if (total == 0) {
            Text(stringResource(R.string.ph_none), style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(
                Modifier.fillMaxWidth().height(12.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                    .padding(2.dp)
            ) {
                Row(Modifier.fillMaxWidth(grow(1f, 250).coerceAtLeast(0.01f)).fillMaxHeight()) {
                parts.forEach { (kind, color, _) ->
                    val n = steps[kind] ?: 0
                    if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(color, RoundedCornerShape(4.dp)))
                }
                }
            }
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                parts.forEach { (kind, color, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(color, CircleShape).border(1.5.dp, UnrotInk, CircleShape))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(label, steps[kind] ?: 0), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Text(pluralStringResource(R.plurals.ph_total, total, total), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp))
            val walked = steps[StepIns.WALKED] ?: 0
            val opened = steps[StepIns.OPENED] ?: 0
            if (walked + opened > 0) {
                Text(stringResource(R.string.ph_ratio, Math.round(walked * 10f / (walked + opened))),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        history?.week(0)?.filter { it.detailed && it.longestMs > 0 }?.maxByOrNull { it.longestMs }?.let { day ->
            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label(stringResource(R.string.ph_longest))
                    Text(dur(day.longestMs), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.ph_longest_at,
                            day.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale) + " " + clock(day.longestMinute),
                            day.longestApp?.let { appLabel(context, it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val lastWeek = history.week(1).filter { it.detailed }.maxOfOrNull { it.longestMs } ?: 0L
                if (history.week(1).all { it.detailed } && lastWeek > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Label(stringResource(R.string.ph_last_week))
                        Text(dur(lastWeek), style = MaterialTheme.typography.titleSmall,
                            color = if (lastWeek > day.longestMs) Good else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

// 4 · When is it hardest? (until there are pauses to learn the hard hours from)

@Composable
private fun FeedHoursCard(history: FeedHistory) {
    val hours = LongArray(24).also { sum -> history.week(0).filter { it.detailed }.forEach { d -> for (h in 0 until 24) sum[h] += d.hours[h] } }
    if (hours.sum() <= 0L) return
    val start = (0 until 24).maxBy { hours[it] + hours[(it + 1) % 24] }
    ProgressCard {
        Text(stringResource(R.string.pf_title, clock(start * 60), clock(((start + 2) % 24) * 60)),
            style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.pf_body), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HourStrip(hours, setOf(start, (start + 1) % 24))
    }
}

@Composable
private fun HourStrip(hours: LongArray, strong: Set<Int>) {
    val most = (hours.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val ink = MaterialTheme.colorScheme.onSurface
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
    ) {
        Column(Modifier.padding(top = 8.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom) {
                hours.forEachIndexed { h, ms ->
                    Box(Modifier.weight(1f).fillMaxHeight((ms.toFloat() / most).coerceAtLeast(0.05f))
                        .background(ink.copy(alpha = if (h in strong) 1f else 0.18f), RoundedCornerShape(2.dp)))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                listOf("00", "06", "12", "18").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// 5 · Are you getting it back?

private data class Window(val from: Int, val to: Int)

private fun goalWindow(goal: String): Window? = when (goal) {
    "sleep" -> Window(23, 5)
    "evenings" -> Window(18, 22)
    "focus" -> Window(9, 17)
    "mornings" -> Window(5, 10)
    else -> null
}

private fun inWindow(day: FeedDay, window: Window): Long {
    var total = 0L
    var h = window.from
    while (h != window.to) { total += day.hours[h]; h = (h + 1) % 24 }
    return total
}

@Composable
private fun GoalsQuestion(history: FeedHistory?, goals: Set<String>, blocked: Long, onEdit: () -> Unit) {
    val haptics = rememberHisnHaptics()
    ProgressCard {
        if (goals.isEmpty()) {
            Text(stringResource(R.string.pg_none_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pg_none_body), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.Button(onClick = { haptics.tap(); onEdit() }) { Text(stringResource(R.string.pg_none_action)) }
            return@ProgressCard
        }
        val tiles = mapOf("sleep" to TileSky, "focus" to TileButter, "evenings" to TileBlush, "adult" to TileSage)
        UnrotAnswers.GOALS.filter { it in goals }.take(3).forEach { goal ->
            var line: String? = null
            var delta: Pair<String, Boolean>? = null
            val window = goalWindow(goal)
            when {
                goal == "adult" -> line = stringResource(R.string.pg_adult,
                    java.text.NumberFormat.getIntegerInstance().format(blocked))
                history == null -> line = null
                window != null -> {
                    val week = history.week(0).filter { it.detailed }
                    if (week.isNotEmpty()) {
                        val now = week.sumOf { inWindow(it, window) }
                        line = stringResource(R.string.pg_window, clock(window.from * 60), clock(window.to * 60), dur(now))
                        if (history.week(0).all { it.detailed }) {
                            delta = history.lastWeekComparable({ inWindow(it, window) }, needsDetail = true)?.let { timeDelta(now - it) }
                        }
                    }
                }
                else -> {
                    val now = history.week(0).sumOf { it.totalMs }
                    line = stringResource(R.string.pg_total, dur(now))
                    delta = history.lastWeekComparable({ it.totalMs })?.let { timeDelta(now - it) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Float3D(goalArt(goal), 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(UnrotAnswers.goalTitle(goal)), style = MaterialTheme.typography.titleSmall)
                    if (line != null) Text(line, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                delta?.let { Delta(it.first, it.second) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pg_from), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { haptics.tap(); onEdit() }) { Text(stringResource(R.string.pg_change)) }
        }
    }
}

// A single day, against the person's usual.

@Composable
internal fun UnrotDayScreen(state: HisnState, epochDay: Long, onBack: () -> Unit) {
    val haptics = rememberHisnHaptics()
    val context = LocalContext.current
    val locale = appLocale()
    var date by rememberSaveable { androidx.compose.runtime.mutableLongStateOf(epochDay) }
    val day = LocalDate.ofEpochDay(date)
    val today = LocalDate.now()
    val apps = remember { FeedHistories.feedApps(context, state) }
    val ruled = remember { FeedHistories.ruledApps(context, state) }
    val history by produceState<FeedHistory?>(null, apps) {
        value = withContext(Dispatchers.IO) { FeedHistories.read(context, apps) }
    }
    val use by produceState<DayUsage?>(null, date) {
        value = null
        value = withContext(Dispatchers.IO) { DayUsages.read(context, LocalDate.ofEpochDay(date)) }
    }
    var allApps by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var allSessions by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val feedSet = remember(apps) { apps }
    val title = when (day) {
        today -> stringResource(R.string.pd_today)
        today.minusDays(1) -> stringResource(R.string.pd_yesterday)
        else -> day.format(DateTimeFormatter.ofPattern("EEEE d MMM", locale))
    }
    val h0 = history
    val index0 = h0?.index(day) ?: -1
    DetailScaffold(title, onBack, actions = {
        IconButton(onClick = { haptics.tick(); date-- }, enabled = h0 == null || index0 > 0) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.pd_prev))
        }
        IconButton(onClick = { haptics.tick(); date++ }, enabled = day.isBefore(today)) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.pd_next))
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val h = history
            val index = h?.index(day) ?: -1
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (h == null || index < 0) {
                    item { PageIntro(stringResource(if (Usage.hasAccess(context)) R.string.pd_loading else R.string.pd_no_usage)) }
                    return@LazyColumn
                }
                val d = h.days[index]
                val usualAll = h.usualDays(index, detail = false)
                val usualDetail = h.usualDays(index, detail = true)
                val steps = StepIns.day(context, day)
                val usualWalked = (1..14).map { StepIns.day(context, day.minusDays(it.toLong()))[StepIns.WALKED] ?: 0 }
                use?.let { u ->
                    item {
                        ProgressCard {
                            Label(stringResource(R.string.pd_screen))
                            Text(dur(countUp(u.totalMs)), style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.semantics { heading() })
                            Text(stringResource(R.string.pd_screen_meta, u.opens, u.apps.size),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.pd_feed_part, dur(d.totalMs)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (u.apps.isNotEmpty()) item {
                        ProgressCard {
                            Label(stringResource(R.string.pd_opened))
                            val top = u.apps.first().ms.coerceAtLeast(1L)
                            val shown = if (allApps) u.apps else u.apps.take(8)
                            shown.forEach { a ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {},
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AppIcon(a.pkg, 30.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(appLabel(context, a.pkg), style = MaterialTheme.typography.titleSmall, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis)
                                        Box(Modifier.padding(top = 4.dp).fillMaxWidth(grow((a.ms.toFloat() / top).coerceIn(0.03f, 1f), 150)).height(5.dp)
                                            .background(
                                                when {
                                                    a.pkg in ruled -> Bad
                                                    a.pkg in feedSet -> Good
                                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                                }, RoundedCornerShape(3.dp)))
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(dur(a.ms), style = MaterialTheme.typography.titleSmall)
                                        if (a.opens > 0) Text(pluralStringResource(R.plurals.pd_app_opens, a.opens, a.opens),
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (u.apps.size > 8) {
                                TextButton(onClick = { haptics.tap(); allApps = !allApps }, contentPadding = PaddingValues(0.dp)) {
                                    Text(if (allApps) stringResource(R.string.pd_show_less) else stringResource(R.string.pd_show_all, u.apps.size))
                                }
                            }
                            Text(stringResource(R.string.pd_colors), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    ProgressCard {
                        Label(stringResource(R.string.pd_usual))
                        Text(stringResource(R.string.pd_usual_note), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (usualAll.isNotEmpty()) {
                            Gauge(stringResource(R.string.pd_feed), dur(d.totalMs),
                                d.totalMs.toDouble(), usualAll.map { it.totalMs.toDouble() }.average(), lowerIsBetter = true,
                                deltaText = { diff -> (if (diff < 0) "↓ " else "↑ ") + dur(diff.toLong()) })
                        }
                        if (d.detailed && usualDetail.isNotEmpty()) {
                            Gauge(stringResource(R.string.pd_opens), "${d.opens}", d.opens.toDouble(),
                                usualDetail.map { it.opens.toDouble() }.average(), lowerIsBetter = true,
                                deltaText = { diff -> (if (diff < 0) "↓ " else "↑ ") + Math.round(kotlin.math.abs(diff)) })
                            val firsts = usualDetail.filter { it.firstMinute >= 0 }
                            if (d.firstMinute >= 0 && firsts.isNotEmpty()) {
                                Gauge(stringResource(R.string.pd_first), clock(d.firstMinute), d.firstMinute.toDouble(),
                                    firsts.map { it.firstMinute.toDouble() }.average(), lowerIsBetter = false,
                                    deltaText = { diff -> minutesWord(diff) })
                            }
                            val lasts = usualDetail.filter { it.lastMinute >= 0 }
                            if (d.lastMinute >= 0 && lasts.isNotEmpty()) {
                                Gauge(stringResource(R.string.pd_last), clock(d.lastMinute), d.lastMinute.toDouble(),
                                    lasts.map { it.lastMinute.toDouble() }.average(), lowerIsBetter = true,
                                    deltaText = { diff -> minutesWord(diff) })
                            }
                            if (d.longestMs > 0) {
                                Gauge(stringResource(R.string.pd_longest), dur(d.longestMs), d.longestMs.toDouble(),
                                    usualDetail.map { it.longestMs.toDouble() }.average(), lowerIsBetter = true,
                                    deltaText = { diff -> (if (diff < 0) "↓ " else "↑ ") + dur(diff.toLong()) })
                            }
                        }
                        val walked = steps[StepIns.WALKED] ?: 0
                        if (walked > 0 || usualWalked.any { it > 0 }) {
                            Gauge(stringResource(R.string.pd_walked), "$walked", walked.toDouble(), usualWalked.average(),
                                lowerIsBetter = false,
                                deltaText = { diff -> (if (diff < 0) "↓ " else "↑ ") + Math.round(kotlin.math.abs(diff)) })
                        }
                    }
                }
                if (d.detailed && d.hours.sum() > 0) {
                    item {
                        ProgressCard {
                            Label(stringResource(R.string.pd_hours))
                            val top2 = (0 until 24).sortedByDescending { d.hours[it] }.take(2).toSet()
                            HourStrip(d.hours, top2)
                        }
                    }
                }
                val sessions = use?.sessions?.filter { it.ms >= 60_000L }.orEmpty()
                if (sessions.isNotEmpty()) {
                    item {
                        ProgressCard {
                            Label(stringResource(R.string.pd_timeline))
                            Text(stringResource(R.string.pd_timeline_note), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val shown = if (allSessions) sessions else sessions.take(12)
                            shown.forEach { session ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).semantics(mergeDescendants = true) {},
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(clock(session.startMinute), style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.width(46.dp))
                                    AppIcon(session.pkg, 24.dp)
                                    Text(appLabel(context, session.pkg), style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(dur(session.ms), style = MaterialTheme.typography.labelLarge,
                                        color = if (session.pkg in ruled) Bad else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            if (sessions.size > 12) {
                                TextButton(onClick = { haptics.tap(); allSessions = !allSessions }, contentPadding = PaddingValues(0.dp)) {
                                    Text(if (allSessions) stringResource(R.string.pd_show_less) else stringResource(R.string.pd_show_all, sessions.size))
                                }
                            }
                        }
                    }
                }
                val stepped = steps.values.sum()
                if (stepped > 0) {
                    item {
                        Text(pluralStringResource(R.plurals.pd_stepped, stepped, stepped), style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
                if (!d.detailed) item { PageIntro(stringResource(R.string.pd_partial)) }
            }
        }
    }
}

@Composable
private fun minutesWord(diff: Double): String {
    val minutes = Math.round(kotlin.math.abs(diff)).toInt()
    if (minutes < 1) return stringResource(R.string.pd_same)
    val text = Usage.formatDuration(minutes * 60_000L)
    return stringResource(if (diff > 0) R.string.pd_later else R.string.pd_earlier, text)
}

/** One number on a bar centred on the person's usual; green when it moved the way they want. */
@Composable
private fun Gauge(
    name: String,
    value: String,
    now: Double,
    usual: Double,
    lowerIsBetter: Boolean,
    deltaText: @Composable (Double) -> String
) {
    val diff = now - usual
    val share = if (usual > 0) (diff / usual).coerceIn(-1.0, 1.0) else if (diff > 0) 1.0 else 0.0
    val same = kotlin.math.abs(share) < 0.05
    val good = if (same) null else (diff < 0) == lowerIsBetter
    val ink = MaterialTheme.colorScheme.onSurface
    val text = if (same) stringResource(R.string.pd_same) else deltaText(diff)
    Column(Modifier.padding(top = 10.dp).semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(6.dp))
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Delta(text, good)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp).height(14.dp)) {
            val half = maxWidth / 2
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(7.dp).background(ink.copy(alpha = 0.1f), RoundedCornerShape(4.dp)))
            if (!same) {
                val w = half * grow(kotlin.math.abs(share).toFloat(), 150)
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = if (share < 0) half - w else half)
                        .width(w)
                        .height(7.dp)
                        .background(if (good == true) Good else Bad, RoundedCornerShape(4.dp))
                )
            }
            Box(Modifier.align(Alignment.Center).width(2.dp).fillMaxHeight().background(ink))
        }
    }
}
