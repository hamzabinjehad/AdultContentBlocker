package app.hisn.android.nativeshell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.hisn.android.OpenWaitStore
import app.hisn.android.PauseCount
import app.hisn.android.R
import app.hisn.core.FeedWeek
import app.hisn.core.PersonalProfile
import app.hisn.core.SetupPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * Unrot's weekly view. Every number is one the phone really holds:
 *  - time in the person's feed apps from Android's own usage record, this week against last
 *    (only when that comparison is fair, see [FeedWeek]),
 *  - today's walk-aways at the wait screen (the "dismiss" that one sec's study found mattered
 *    most), today's pauses and opens,
 *  - the milestones of a day count that never resets, and the hours pauses cluster in.
 * No scores, levels, moods or recovery percentages: CleanPath's analytics shows those, built on
 * nothing the phone measured.
 */
@Composable
fun ProgressScreen(state: HisnState, onBack: () -> Unit, onOpen: (String) -> Unit = {}) {
    DetailScaffold(title = stringResource(R.string.progress_title), onBack = onBack) { padding ->
        ProgressContent(state, padding, onOpen)
    }
}

/** The week itself: Unrot's Progress tab, and the page behind "See your week". */
@Composable
fun ProgressContent(state: HisnState, padding: PaddingValues = PaddingValues(0.dp), onOpen: (String) -> Unit = {}) {
    if (unrotEdition) {
        UnrotProgress(state, padding, onOpen)
        return
    }
    val context = LocalContext.current
    val usageOn = remember { Usage.hasAccess(context) }
    val waitApps = remember { OpenWaitStore.apps(context) }
    val apps = remember(waitApps) {
        val ruled = state.blockedApps.toSet() + state.appLimits.keys + waitApps +
            state.appSchedule.let { if (it.enabled && !it.allApps) it.apps else emptySet() }
        ruled + installedApps(context).map { it.packageName }.filter { SetupPlan.hasFeed(it) }
    }
    val week by produceState<FeedWeek?>(initialValue = null, usageOn, apps) {
        if (!usageOn) return@produceState
        value = withContext(Dispatchers.IO) {
            val now = java.util.Calendar.getInstance()
            val fraction = (now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)) / 1440f
            FeedWeek(Usage.dailyForeground(context, apps, FeedWeek.DAYS * 2), fraction)
        }
    }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            tick++
        }
    }
    val walked = remember(tick) { waitApps.sumOf { OpenWaitStore.closedToday(context, it) } }
    val opens = remember(tick) { waitApps.sumOf { OpenWaitStore.openedToday(context, it) } }
    val pauses = remember(tick) { PauseCount.today(context) }

    run {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                if (usageOn) WeekCard(week) else UsageInvite { state.openPermission("usage") }
            }
            item { SectionHeader(stringResource(R.string.progress_today)) }
            item { TodayNumbers(walked, pauses, opens) }
            item { SectionHeader(stringResource(R.string.progress_milestones)) }
            item { Milestones(state.personal.day) }
            if (state.personal.peakStart >= 0) {
                item { HardHoursCard(state) }
            }
        }
    }
}

/**
 * The hard hours, the pause-by-hour chart behind them, and the opt-in nudge 15 minutes before
 * (see [app.hisn.android.HardHoursNudge] for why it is shaped this way).
 */
@Composable
internal fun HardHoursCard(state: HisnState) {
    val context = LocalContext.current
    val peak = state.personal.peakStart
    val nudgeAt = app.hisn.android.HardHoursNudge.time(app.hisn.android.HardHoursNudge.nudgeMinute(peak))
    var on by remember { androidx.compose.runtime.mutableStateOf(app.hisn.android.HardHoursNudge.isOn(context)) }
    // Re-read on return from Android's notification settings.
    var resumed by remember { mutableIntStateOf(0) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumed++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val notifications = remember(resumed) { app.hisn.android.HardHoursNudge.canNotify(context) }
    val shape = MaterialTheme.shapes.extraLarge
    val ink = UnrotInk
    Column(
        Modifier
            .fillMaxWidth()
            .unrotFrame(shape)
            .background(if (on || LocalDarkTheme.current) LocalHisnStatusColors.current.card else UnrotBlush, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val text = if (on || LocalDarkTheme.current) MaterialTheme.colorScheme.onSurface else ink
        Text(
            if (on) stringResource(R.string.progress_hard_on_title, nudgeAt)
            else stringResource(R.string.progress_hard_title, hour(peak), hour(peak + app.hisn.core.PauseHours.PEAK_SPAN)),
            style = MaterialTheme.typography.titleLarge,
            color = text,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            if (on) stringResource(R.string.progress_hard_on_body)
            else stringResource(R.string.progress_hard_body, nudgeAt),
            style = MaterialTheme.typography.bodyMedium,
            color = text.copy(alpha = 0.8f)
        )
        HourBars(state.personal.hours, peak, text)
        when {
            on && !notifications -> {
                Text(stringResource(R.string.progress_hard_needs_notifications),
                    style = MaterialTheme.typography.bodySmall, color = text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { state.openPermission("notifications") }) {
                        Text(stringResource(R.string.progress_hard_allow))
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        app.hisn.android.HardHoursNudge.set(context, false); on = false
                    }) { Text(stringResource(R.string.progress_hard_off)) }
                }
            }
            on -> androidx.compose.material3.TextButton(onClick = {
                app.hisn.android.HardHoursNudge.set(context, false); on = false
            }) { Text(stringResource(R.string.progress_hard_off)) }
            else -> androidx.compose.material3.Button(
                onClick = {
                    app.hisn.android.HardHoursNudge.set(context, true); on = true
                    if (!notifications) state.openPermission("notifications")
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color.White, contentColor = ink
                ),
                border = androidx.compose.foundation.BorderStroke(2.dp, ink)
            ) { Text(stringResource(R.string.progress_hard_nudge, nudgeAt)) }
        }
    }
}

/** 24 bars, one per hour; the hard hours are solid, the rest faint. */
@Composable
private fun HourBars(hours: List<Int>, peak: Int, color: androidx.compose.ui.graphics.Color) {
    val most = (hours.maxOrNull() ?: 0).coerceAtLeast(1)
    val inPeak = { h: Int -> ((h - peak) % 24 + 24) % 24 < app.hisn.core.PauseHours.PEAK_SPAN }
    val label = stringResource(R.string.progress_hard_chart)
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(top = 4.dp)
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        hours.forEachIndexed { h, count ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight((count.toFloat() / most).coerceAtLeast(0.1f))
                    .background(color.copy(alpha = if (inPeak(h)) 1f else 0.22f), RoundedCornerShape(2.dp))
            )
        }
    }
}

private fun hour(value: Int): String =
    String.format(java.util.Locale.ROOT, "%02d:00", ((value % 24) + 24) % 24)

@Composable
internal fun UsageInvite(onAllow: () -> Unit) {
    val shape = MaterialTheme.shapes.extraLarge
    Column(
        Modifier
            .fillMaxWidth()
            .unrotFrame(shape, hero = true)
            .background(LocalHisnStatusColors.current.card, shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.progress_usage_title), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.progress_usage_body), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onAllow) {
            Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.progress_usage_action))
        }
    }
}

@Composable
private fun WeekCard(week: FeedWeek?) {
    val colors = MaterialTheme.colorScheme
    val status = LocalHisnStatusColors.current
    val shape = MaterialTheme.shapes.extraLarge
    val locale = appLocale()
    Column(
        Modifier
            .fillMaxWidth()
            .unrotFrame(shape, hero = true)
            .background(status.card, shape)
            .padding(20.dp)
    ) {
        Text(stringResource(R.string.progress_feed_time), style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant)
        Text(
            week?.let { Usage.formatDuration(it.thisWeekTotal()) } ?: "…",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics { heading() }
        )
        val change = week?.changePercent()
        Text(
            when {
                change == null -> stringResource(R.string.progress_no_compare)
                change <= -3 -> stringResource(R.string.progress_less, -change)
                change >= 3 -> stringResource(R.string.progress_more, change)
                else -> stringResource(R.string.progress_same)
            },
            style = MaterialTheme.typography.titleSmall,
            color = when {
                change == null -> colors.onSurfaceVariant
                change <= -3 -> status.success
                change >= 3 -> colors.tertiary
                else -> colors.onSurfaceVariant
            }
        )
        Spacer(Modifier.height(16.dp))
        val days = week?.thisWeek() ?: LongArray(FeedWeek.DAYS)
        val peak = week?.peak() ?: 1L
        val today = LocalDate.now()
        Row(
            Modifier.fillMaxWidth().height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEachIndexed { index, millis ->
                val date = today.minusDays((FeedWeek.DAYS - 1 - index).toLong())
                val isToday = index == FeedWeek.DAYS - 1
                val label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$label ${Usage.formatDuration(millis)}"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    val share = (millis.toFloat() / peak).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .fillMaxHeight(share.coerceAtLeast(0.04f))
                            .background(
                                if (isToday) colors.tertiary else colors.onSurface.copy(alpha = 0.16f),
                                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp)
                            )
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isToday) colors.onSurface else colors.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.progress_apps_note), style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant)
    }
}

@Composable
private fun TodayNumbers(walked: Int, pauses: Int, opens: Int) {
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier
            .fillMaxWidth()
            .unrotFrame(shape)
            .background(LocalHisnStatusColors.current.card, shape)
            .padding(vertical = 14.dp)
    ) {
        TodayNumber(walked, R.string.progress_walked, R.string.progress_walked_detail, Modifier.weight(1f))
        TodayNumber(pauses, R.string.progress_pauses, R.string.progress_pauses_detail, Modifier.weight(1f))
        TodayNumber(opens, R.string.progress_opens, R.string.progress_opens_detail, Modifier.weight(1f))
    }
}

@Composable
private fun TodayNumber(value: Int, label: Int, detail: Int, modifier: Modifier) {
    Column(
        modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$value", style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        Text(stringResource(detail), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Milestones(day: Int) {
    val colors = MaterialTheme.colorScheme
    val resources = LocalResources.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PersonalProfile.milestones().forEach { milestone ->
            val reached = day >= milestone
            val shape = MaterialTheme.shapes.medium
            Column(
                Modifier
                    .size(width = 78.dp, height = 104.dp)
                    .unrotFrame(shape, hero = false)
                    .background(if (reached) colors.primaryContainer else LocalHisnStatusColors.current.cardMuted, shape)
                    .padding(8.dp)
                    .semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (reached) {
                    Box(
                        Modifier.size(22.dp).background(colors.onPrimaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.primaryContainer,
                            modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    "$milestone",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (reached) colors.onPrimaryContainer else colors.onSurfaceVariant
                )
                Text(
                    if (reached) stringResource(R.string.progress_milestone_reached)
                    else if (day > 0) resources.getQuantityString(R.plurals.progress_milestone_in, milestone - day, milestone - day)
                    else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (reached) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}
