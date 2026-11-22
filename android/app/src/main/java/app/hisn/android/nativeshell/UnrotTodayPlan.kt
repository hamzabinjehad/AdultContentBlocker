package app.hisn.android.nativeshell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.hisn.android.OpenWaitStore
import app.hisn.android.R
import app.hisn.android.StepIns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

/*
 * Today's live view of the person's own rules: what comes next (quiet hours, a limit running out),
 * and one row per rule with how today is going. A first day with no rules gets one starter card
 * built from Android's record of the biggest time-taker. Nothing here is invented: every number
 * is a rule the person set or a count the phone keeps.
 */

private class PlanData(
    val used: Map<String, Long>,
    val feedsClosed: Int,
    val waitApps: Set<String>,
    val walked: Int,
    val opened: Int,
    val starter: AppRow?
)

private val PlanSage = Color(0xFF9EC39A)
private val PlanSky = Color(0xFF9CC8E0)
private val PlanButter = Color(0xFFF4C34E)
private val PlanLilac = Color(0xFFC9C3EA)
private val PlanBlush = Color(0xFFF2A08F)

/** True when the person has set no app rule at all: Today then shows the starter instead of a plan. */
private fun noRules(state: HisnState, waits: Set<String>) =
    state.appLimits.isEmpty() && state.blockedApps.isEmpty() && waits.isEmpty() && !state.appSchedule.isConfigured

@Composable
internal fun UnrotTodayPlan(state: HisnState, resumes: Int, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    var changed by remember { mutableStateOf(0) }
    val data by produceState<PlanData?>(null, resumes, changed, state.appLimits.size, state.blockedApps.size, state.shortVideos.on) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val waits = OpenWaitStore.apps(context)
                val starter = if (noRules(state, waits) && Usage.hasAccess(context)) {
                    YourApps.read(context).filter { it.weekAvg >= 20 * 60_000L }.maxByOrNull { it.weekAvg }
                } else null
                PlanData(
                    used = Usage.usedTodayAll(context),
                    feedsClosed = StepIns.day(context, LocalDate.now())[StepIns.CLOSED] ?: 0,
                    waitApps = waits,
                    walked = waits.sumOf { OpenWaitStore.closedToday(context, it) },
                    opened = waits.sumOf { OpenWaitStore.openedToday(context, it) },
                    starter = starter
                )
            }.getOrNull()
        }
    }
    val d = data ?: return
    if (d.starter != null) {
        StarterCard(d.starter, state) { changed++ }
        return
    }
    NextUp(state, d)
    PlanCard(state, d, onOpen)
}

/** Whatever comes next: quiet hours now or soon, or a limit about to run out. */
@Composable
private fun NextUp(state: HisnState, d: PlanData) {
    val now = LocalTime.now()
    val minute = now.hour * 60 + now.minute
    val s = state.appSchedule
    val quietNow = s.isConfigured && s.activeAt(minute, LocalDate.now().dayOfWeek)
    val untilQuiet = if (s.isConfigured && !quietNow) ((s.startMinute - minute) + 1440) % 1440 else -1
    val nearLimit = state.appLimits.entries.mapNotNull { (pkg, min) ->
        val left = min * 60_000L - (d.used[pkg] ?: 0L)
        if (left in 1..15 * 60_000L) pkg to left else null
    }.minByOrNull { it.second }
    val context = LocalContext.current
    val (title, body, progress) = when {
        quietNow -> Triple(stringResource(R.string.plan_next_quiet_now, Wins.clock(s.endMinute)),
            stringResource(R.string.plan_next_quiet_body), 1f)
        nearLimit != null -> Triple(stringResource(R.string.plan_next_limit, appLabel(context, nearLimit.first), Wins.short(nearLimit.second)),
            stringResource(R.string.plan_next_limit_body), 1f - nearLimit.second / (15 * 60_000f))
        untilQuiet in 1..360 -> Triple(stringResource(R.string.plan_next_quiet_at, Wins.clock(s.startMinute)),
            stringResource(R.string.plan_next_quiet_in, Wins.short(untilQuiet * 60_000L)), 1f - untilQuiet / 360f)
        else -> return
    }
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier.riseIn(1).fillMaxWidth().padding(bottom = 8.dp).unrotFrame(shape)
            .background(if (LocalDarkTheme.current) Color(0xFF2A2416) else Color(0xFF1B1A16), shape).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Float3D(if (nearLimit != null && !quietNow) R.drawable.unrot_3d_battery else R.drawable.unrot_3d_moon, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.plan_next_label).uppercase(appLocale()), style = MaterialTheme.typography.labelSmall,
                color = PlanLilac)
            Text(title, style = MaterialTheme.typography.titleMedium.storySerif(), color = Color(0xFFF3ECDF))
            Text(body, style = MaterialTheme.typography.bodySmall, color = Color(0xFFF3ECDF).copy(alpha = 0.75f))
        }
        val ring = grow(progress.coerceIn(0f, 1f), 200)
        CircularProgressIndicator(progress = { ring }, modifier = Modifier.size(30.dp),
            color = PlanButter, trackColor = Color(0x33F3ECDF), strokeWidth = 4.dp)
    }
}

@Composable
private fun PlanCard(state: HisnState, d: PlanData, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val rows = buildList<@Composable () -> Unit> {
        if (state.shortVideos.on) add {
            PlanRow(Icons.AutoMirrored.Rounded.ViewList, PlanButter, stringResource(R.string.plan_feeds),
                if (d.feedsClosed > 0) androidx.compose.ui.res.pluralStringResource(R.plurals.plan_feeds_closed, d.feedsClosed, d.feedsClosed)
                else stringResource(R.string.plan_feeds_none), status = stringResource(R.string.plan_on), good = true) { onOpen("apps") }
        }
        state.appLimits.entries.sortedBy { appLabel(context, it.key) }.forEach { (pkg, minutes) ->
            add {
                val used = d.used[pkg] ?: 0L
                val total = minutes * 60_000L
                val done = used >= total
                PlanRow(Icons.Rounded.Timer, PlanSage, stringResource(R.string.plan_limit, appLabel(context, pkg), minutes),
                    if (done) stringResource(R.string.plan_limit_done)
                    else stringResource(R.string.plan_limit_left, Wins.short(used), Wins.short(total - used)),
                    progress = (used.toFloat() / total).coerceIn(0f, 1f), progressColor = if (done) PlanBlush else PlanButter) { onOpen("apps") }
            }
        }
        if (d.waitApps.isNotEmpty()) add {
            val names = d.waitApps.map { appLabel(context, it) }.sorted()
            val total = d.walked + d.opened
            PlanRow(Icons.Rounded.Pause, PlanSky, stringResource(R.string.plan_wait, names.take(2).joinToString(", ") +
                if (names.size > 2) " +${names.size - 2}" else ""),
                if (total == 0) stringResource(R.string.plan_wait_none) else stringResource(R.string.plan_wait_walked, d.walked, total),
                status = if (total > 0) "${d.walked} / $total" else null, good = d.walked * 2 >= total) { onOpen("apps") }
        }
        val s = state.appSchedule
        if (s.isConfigured) add {
            val minute = LocalTime.now().let { it.hour * 60 + it.minute }
            val active = s.activeAt(minute, LocalDate.now().dayOfWeek)
            val until = ((s.startMinute - minute) + 1440) % 1440
            PlanRow(Icons.Rounded.Bedtime, PlanLilac, stringResource(R.string.plan_quiet),
                stringResource(R.string.plan_quiet_window, Wins.clock(s.startMinute), Wins.clock(s.endMinute)),
                status = if (active) stringResource(R.string.plan_quiet_now) else Wins.short(until * 60_000L), good = active) { onOpen("schedule") }
        }
    }
    if (rows.isEmpty()) return
    val shape = MaterialTheme.shapes.large
    Column(Modifier.riseIn(2).fillMaxWidth().unrotFrame(shape).background(LocalHisnStatusColors.current.card, shape)
        .padding(vertical = 8.dp)) {
        Text(stringResource(R.string.today_plan_title).uppercase(appLocale()), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp).semantics { heading() })
        rows.forEachIndexed { i, row ->
            if (i > 0) InsetDivider(start = 64.dp)
            row()
        }
    }
}

@Composable
private fun PlanRow(icon: ImageVector, tile: Color, title: String, body: String, status: String? = null, good: Boolean = false,
                    progress: Float? = null, progressColor: Color = PlanButter, onClick: () -> Unit) {
    val haptics = rememberHisnHaptics()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button) { haptics.tap(); onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val art = art3D(icon)
        if (art != null) Float3D(art, 38.dp) else Box(Modifier.size(34.dp).border(1.5.dp, UnrotInk, RoundedCornerShape(10.dp)).background(tile, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = UnrotInk, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (progress != null) {
                val g = grow(progress, 150)
                Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(8.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(4.dp))) {
                    Box(Modifier.fillMaxWidth(g).height(8.dp).background(progressColor, RoundedCornerShape(4.dp)))
                }
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (status != null) {
            Spacer(Modifier.width(8.dp))
            Text(status, style = MaterialTheme.typography.labelMedium,
                color = if (good) Color(0xFF3F6B45) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.background(if (good) Color(0xFFDCEBD8) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** Day one, no rules yet: the biggest time-taker and three ways to start, from Android's own record. */
@Composable
private fun StarterCard(row: AppRow, state: HisnState, onDone: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHisnHaptics()
    val snackbar = LocalHisnSnackbar.current
    val minutes = remember { suggestedMinutes(row.weekAvg) }
    val choices = buildList { add(AppRule.WAIT); add(AppRule.LIMIT); if (row.hasFeed) add(AppRule.FEED) }
    var chosen by remember { mutableStateOf(AppRule.LIMIT) }
    val dark = LocalDarkTheme.current
    val shape = MaterialTheme.shapes.large
    val saved = stringResource(R.string.yourapps_saved, row.label)
    Column(Modifier.riseIn(1).fillMaxWidth().unrotFrame(shape)
        .background(if (dark) Color(0xFF3A3016) else Color(0xFFFCEFC8), shape).padding(18.dp)) {
        Text(stringResource(R.string.plan_start_label).uppercase(appLocale()), style = MaterialTheme.typography.labelMedium,
            color = if (dark) Color(0xFFE6D3A0) else Color(0xFF7A6A3A))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.plan_start_title, row.label, Wins.short(row.weekAvg)),
            style = MaterialTheme.typography.headlineSmall.storySerif(), modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.plan_start_body), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { c ->
                val on = c == chosen
                Text(
                    when (c) {
                        AppRule.WAIT -> stringResource(R.string.plan_start_wait)
                        AppRule.LIMIT -> stringResource(R.string.plan_start_limit, minutes)
                        else -> stringResource(R.string.plan_start_feed)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) Color(0xFFFFFDF8) else UnrotInk,
                    modifier = Modifier
                        .background(if (on) UnrotInk else UnrotInk.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                        .clickable(role = Role.RadioButton) { haptics.tick(); chosen = c }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = {
            val ok = applyRule(context, state, row, chosen, minutes)
            if (ok) { haptics.confirm(); snackbar(HisnMessage(saved)) } else haptics.reject()
            onDone()
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(stringResource(R.string.plan_start_set)) }
    }
}
