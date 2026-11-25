package app.hisn.android.nativeshell

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.hisn.android.OpenWaitStore
import app.hisn.android.R
import app.hisn.core.SetupPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/*
 * "Your apps": one page for every app rule. The apps that take the person's time come first,
 * grouped by what they are, each with its rule as a chip; one tap opens a sheet with every way
 * to rein it in (wait, limit, close the feed, block) and what opening it will look like.
 * Adult protection is not an app rule: it stays on the Shield tab, always on.
 */

internal enum class AppGroup { FEEDS, SOCIAL, GAMES, OTHER }

internal class AppRow(
    val packageName: String,
    val label: String,
    val group: AppGroup,
    /** Feed-app time per day for the last seven whole days and today, oldest first; empty without access. */
    val days: LongArray,
    val hasFeed: Boolean
) {
    val weekAvg: Long get() = if (days.size < 2) 0L else days.copyOfRange(0, days.size - 1).average().toLong()
}

internal object YourApps {
    /** Never listed: they are not "use" anyone chose, or blocking them would break the phone. */
    fun hidden(context: Context): Set<String> {
        val pm = context.packageManager
        val homes = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                .mapNotNull { it.activityInfo?.packageName }
        }.getOrDefault(emptyList())
        val settings = runCatching {
            pm.resolveActivity(Intent(android.provider.Settings.ACTION_SETTINGS), 0)?.activityInfo?.packageName
        }.getOrNull()
        val dialer = runCatching {
            pm.resolveActivity(Intent(Intent.ACTION_DIAL), 0)?.activityInfo?.packageName
        }.getOrNull()
        return homes.toSet() + setOfNotNull(settings, dialer, context.packageName, "com.android.settings",
            "com.android.vending", "com.sec.android.app.samsungapps", "com.android.systemui")
    }

    private fun groupOf(context: Context, pkg: String, hasFeed: Boolean): AppGroup {
        if (hasFeed) return AppGroup.FEEDS
        if (android.os.Build.VERSION.SDK_INT < 26) return AppGroup.OTHER
        val category = runCatching { context.packageManager.getApplicationInfo(pkg, 0).category }.getOrDefault(-1)
        return when (category) {
            ApplicationInfo.CATEGORY_SOCIAL -> AppGroup.SOCIAL
            ApplicationInfo.CATEGORY_GAME -> AppGroup.GAMES
            else -> AppGroup.OTHER
        }
    }

    /** Reads the phone's apps and, with usage access, eight days of time per app. Off the main thread. */
    fun read(context: Context, ruled: Set<String> = emptySet()): List<AppRow> {
        // A hidden app with a rule (a wait before Settings, say) is still listed, or that rule
        // could never be changed or removed here.
        val skip = hidden(context) - ruled
        val apps = installedApps(context).filter { it.packageName !in skip }
        val days = LongArray(0)
        val perDay: List<Map<String, Long>> = if (!Usage.hasAccess(context)) emptyList() else {
            val manager = context.getSystemService(UsageStatsManager::class.java)
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            (7 downTo 0).map { back ->
                val start = today.minusDays(back.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = if (back == 0) System.currentTimeMillis()
                    else today.minusDays(back - 1L).atStartOfDay(zone).toInstant().toEpochMilli()
                runCatching {
                    manager?.queryAndAggregateUsageStats(start, end)?.mapValues { it.value.totalTimeInForeground }
                }.getOrNull() ?: emptyMap()
            }
        }
        return apps.map { app ->
            val feed = SetupPlan.hasFeed(app.packageName)
            AppRow(
                app.packageName, app.label, groupOf(context, app.packageName, feed),
                if (perDay.isEmpty()) days else LongArray(perDay.size) { perDay[it][app.packageName] ?: 0L },
                feed
            )
        }
    }
}

private val ChipWait = Color(0xFF9CC8E0)
private val ChipLimit = Color(0xFF9EC39A)
private val ChipFeed = Color(0xFFF4C34E)
private val ChipBlock = Color(0xFFF2A08F)

/** What the app's current rule is, for its chip and for the sheet's first tab. */
internal enum class AppRule { NONE, WAIT, LIMIT, FEED, BLOCK }

internal fun ruleOf(context: Context, state: HisnState, pkg: String, hasFeed: Boolean): AppRule = when {
    pkg in state.blockedApps -> AppRule.BLOCK
    state.appLimits[pkg] != null -> AppRule.LIMIT
    pkg in OpenWaitStore.apps(context) -> AppRule.WAIT
    hasFeed && state.shortVideos.on -> AppRule.FEED
    else -> AppRule.NONE
}

@Composable
internal fun UnrotYourAppsScreen(state: HisnState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHisnHaptics()
    var rows by remember { mutableStateOf<List<AppRow>?>(null) }
    var version by remember { mutableIntStateOf(0) }
    val usageAccess = remember { Usage.hasAccess(context) }
    LaunchedEffect(Unit) {
        val ruled = state.blockedApps.toSet() + state.appLimits.keys + app.hisn.android.OpenWaitStore.apps(context) +
            state.appSchedule.let { if (it.enabled && !it.allApps) it.apps else emptySet() }
        rows = withContext(Dispatchers.IO) { YourApps.read(context, ruled - context.packageName) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var showOthers by rememberSaveable { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<AppRow?>(null) }
    val all = rows

    DetailScaffold(title = stringResource(R.string.yourapps_title), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.yourapps_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = if (query.isEmpty()) null else {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.apps_clear)) } }
                    },
                    singleLine = true, shape = RoundedCornerShape(30.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (all == null) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { AppListSkeleton() } }
                return@LazyColumn
            }
            if (!usageAccess) item { UsageCard { state.openPermission("usage") } }
            val feeds = all.filter { it.hasFeed }
            if (feeds.isNotEmpty() && query.isBlank()) item { CloseFeedsCard(state, feeds) }

            val needle = query.trim().lowercase()
            val visible = all.filter { needle.isEmpty() || it.label.lowercase().contains(needle) }
                .sortedWith(compareByDescending<AppRow> { it.weekAvg }.thenBy { it.label.lowercase() })
            AppGroup.entries.forEach { group ->
                val inGroup = visible.filter { it.group == group }
                if (inGroup.isEmpty()) return@forEach
                // "Other" is long: only apps with real time show until asked, unless searching.
                val shown = if (group == AppGroup.OTHER && needle.isEmpty() && !showOthers)
                    inGroup.filter { it.weekAvg >= 5 * 60_000L }.take(6) else inGroup
                item(key = "h-$group") {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.Bottom) {
                        Text(stringResource(when (group) {
                            AppGroup.FEEDS -> R.string.yourapps_group_feeds
                            AppGroup.SOCIAL -> R.string.yourapps_group_social
                            AppGroup.GAMES -> R.string.yourapps_group_games
                            AppGroup.OTHER -> R.string.yourapps_group_other
                        }).uppercase(appLocale()), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).semantics { heading() })
                        if (usageAccess && group == AppGroup.FEEDS) Text(stringResource(R.string.yourapps_per_day),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (shown.isNotEmpty()) item(key = "g-$group-$version") {
                    AppCard(shown, state) { haptics.tap(); sheetFor = it }
                }
                if (group == AppGroup.OTHER && shown.size < inGroup.size) item(key = "more") {
                    TextButton(onClick = { haptics.tap(); showOthers = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(pluralStringResource(R.plurals.yourapps_more, inGroup.size - shown.size, inGroup.size - shown.size))
                    }
                }
            }
            item {
                Text(stringResource(R.string.yourapps_never), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 12.dp, end = 12.dp))
            }
        }
    }
    sheetFor?.let { row ->
        AppRuleSheet(row, state, onPlus = { onOpen("plus") }, onDismiss = { sheetFor = null; version++ })
    }
}

@Composable
private fun UsageCard(onAllow: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier.riseIn(0).fillMaxWidth().unrotFrame(shape).background(LocalHisnStatusColors.current.card, shape).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.yourapps_usage_title), style = MaterialTheme.typography.titleLarge.storySerif(),
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.yourapps_usage_body), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAllow) { Text(stringResource(R.string.yourapps_usage_allow)) }
    }
}

@Composable
private fun CloseFeedsCard(state: HisnState, feeds: List<AppRow>) {
    val haptics = rememberHisnHaptics()
    val shape = MaterialTheme.shapes.large
    val on = state.shortVideos.on
    val dark = LocalDarkTheme.current
    Row(
        Modifier.riseIn(0).fillMaxWidth().unrotFrame(shape)
            .background(if (on) LocalHisnStatusColors.current.card else if (dark) Color(0xFF3A3016) else Color(0xFFFCEFC8), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(pluralStringResource(if (on) R.plurals.yourapps_feeds_on else R.plurals.yourapps_feeds_off, feeds.size, feeds.size),
                style = MaterialTheme.typography.titleSmall)
            Text(feeds.take(4).joinToString(" · ") { it.label }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (!on) Button(onClick = { haptics.toggle(true); state.shortVideos.set(true) }) {
            Text(stringResource(R.string.yourapps_close_feeds))
        } else Icon(Icons.AutoMirrored.Rounded.ViewList, contentDescription = null, tint = ChipLimit)
    }
}

@Composable
private fun AppCard(rows: List<AppRow>, state: HisnState, onOpen: (AppRow) -> Unit) {
    val shape = MaterialTheme.shapes.large
    Column(Modifier.riseIn(1).fillMaxWidth().unrotFrame(shape).background(LocalHisnStatusColors.current.card, shape)) {
        rows.forEachIndexed { i, row ->
            if (i > 0) InsetDivider(start = 68.dp)
            AppLine(row, state) { onOpen(row) }
        }
    }
}

@Composable
private fun AppLine(row: AppRow, state: HisnState, onClick: () -> Unit) {
    val context = LocalContext.current
    val rule = ruleOf(context, state, row.packageName, row.hasFeed)
    val quiet = state.appSchedule.let { it.isConfigured && it.targets(row.packageName) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(row.packageName, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            val sub = when {
                row.days.isEmpty() && row.hasFeed -> stringResource(R.string.yourapps_has_feed)
                row.days.isEmpty() -> null
                row.weekAvg < 60_000L -> stringResource(R.string.yourapps_hardly)
                else -> Wins.short(row.weekAvg)
            }
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (row.days.size >= 3 && row.weekAvg >= 5 * 60_000L) {
            Spark(row.days)
            Spacer(Modifier.width(8.dp))
        }
        if (quiet) {
            Icon(Icons.Rounded.Bedtime, contentDescription = stringResource(R.string.yourapps_quiet_icon),
                modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
        }
        RuleChip(rule, state.appLimits[row.packageName])
    }
}

/** Seven days as a tiny line: green when the last days are lower than the first, red when higher. */
@Composable
private fun Spark(days: LongArray) {
    val week = days.copyOfRange(0, days.size - 1)
    val falling = week.takeLast(3).average() < week.take(3).average()
    val color = if (falling) LocalHisnStatusColors.current.success else MaterialTheme.colorScheme.error
    val top = (week.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
    Canvas(Modifier.width(40.dp).height(18.dp)) {
        val step = size.width / (week.size - 1).coerceAtLeast(1)
        val path = Path()
        week.forEachIndexed { i, v ->
            val p = Offset(i * step, size.height - (v / top) * (size.height - 3f) - 1.5f)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun RuleChip(rule: AppRule, limit: Int?) {
    val (text, color) = when (rule) {
        AppRule.BLOCK -> stringResource(R.string.yourapps_chip_block) to ChipBlock
        AppRule.LIMIT -> stringResource(R.string.yourapps_chip_limit, limit ?: 0) to ChipLimit
        AppRule.WAIT -> stringResource(R.string.yourapps_chip_wait) to ChipWait
        AppRule.FEED -> stringResource(R.string.yourapps_chip_feed) to ChipFeed
        AppRule.NONE -> stringResource(R.string.yourapps_chip_add) to Color.Transparent
    }
    val ink = if (rule == AppRule.NONE) MaterialTheme.colorScheme.onSurfaceVariant else UnrotInk
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = ink, maxLines = 1,
        modifier = Modifier
            .border(1.5.dp, if (rule == AppRule.NONE) MaterialTheme.colorScheme.outline else UnrotInk, RoundedCornerShape(50))
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** Every way to rein one app in, firmest last, with what opening it will look like. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRuleSheet(row: AppRow, state: HisnState, onPlus: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHisnHaptics()
    val snackbar = LocalHisnSnackbar.current
    val current = remember { ruleOf(context, state, row.packageName, row.hasFeed) }
    val tabs = buildList { add(AppRule.WAIT); add(AppRule.LIMIT); if (row.hasFeed) add(AppRule.FEED); add(AppRule.BLOCK) }
    var chosen by remember { mutableStateOf(if (current == AppRule.NONE) AppRule.LIMIT else current) }
    var minutes by remember { mutableIntStateOf(state.appLimits[row.packageName] ?: suggestedMinutes(row.weekAvg)) }
    val schedule = state.appSchedule
    var quiet by remember { mutableStateOf(schedule.isConfigured && schedule.targets(row.packageName)) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val savedText = stringResource(R.string.yourapps_saved, row.label)
    val refusedText = stringResource(R.string.yourapps_refused)
    // A wait or a limit on one more app than the free three is Plus. Blocking an app stays free:
    // it is how an adult app is kept out.
    val timed = remember { (state.appLimits.keys + OpenWaitStore.apps(context)).size }
    val hadTimed = current == AppRule.WAIT || current == AppRule.LIMIT
    val needsPlus = !hadTimed && (chosen == AppRule.WAIT || chosen == AppRule.LIMIT) &&
        !UnrotPlus.open(context, app.hisn.core.PlusGate.Feature.MORE_RULES, timed)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(row.packageName, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(row.label, style = MaterialTheme.typography.titleLarge.storySerif())
                    if (row.weekAvg >= 60_000L) Text(stringResource(R.string.yourapps_sheet_avg, Wins.short(row.weekAvg)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            // The strengths as tabs, softest first.
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), RoundedCornerShape(14.dp))) {
                tabs.forEachIndexed { i, tab ->
                    val on = tab == chosen
                    Box(
                        Modifier.weight(1f).heightIn(min = 44.dp)
                            .background(if (on) tabColor(tab) else Color.Transparent,
                                when (i) { 0 -> RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                                    tabs.lastIndex -> RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                                    else -> RoundedCornerShape(0.dp) })
                            .clickable(role = Role.Tab) { haptics.tick(); chosen = tab },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(tabName(tab)), style = MaterialTheme.typography.labelLarge,
                            color = if (on) UnrotInk else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(tabBody(tab = chosen)), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (chosen == AppRule.LIMIT) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    StepButton("−") { haptics.tick(); minutes = (minutes - 15).coerceAtLeast(15) }
                    Text(stringResource(R.string.yourapps_minutes, minutes), style = MaterialTheme.typography.headlineMedium.storySerif(),
                        modifier = Modifier.padding(horizontal = 16.dp))
                    StepButton("+") { haptics.tick(); minutes = (minutes + 15).coerceAtMost(240) }
                }
                val back = row.weekAvg - minutes * 60_000L
                if (back >= 5 * 60_000L) Text(stringResource(R.string.yourapps_gives_back, Wins.short(back)),
                    style = MaterialTheme.typography.labelLarge, color = if (LocalDarkTheme.current) ChipLimit else Color(0xFF3F6B45),
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            Spacer(Modifier.height(12.dp))
            Preview(row.label, chosen, minutes)
            if (schedule.isConfigured && !schedule.allApps) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.yourapps_quiet_too, Wins.clock(schedule.startMinute), Wins.clock(schedule.endMinute)),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = quiet, onCheckedChange = { haptics.toggle(it); quiet = it })
                }
            }
            Spacer(Modifier.height(14.dp))
            if (needsPlus) Text(stringResource(R.string.plus_rules_limit, app.hisn.core.PlusGate.FREE_RULES),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            Button(
                onClick = {
                    if (needsPlus) { haptics.nav(); onDismiss(); onPlus(); return@Button }
                    val ok = applyRule(context, state, row, chosen, minutes) && applyQuiet(state, row.packageName, quiet)
                    if (ok) haptics.confirm() else haptics.reject()
                    snackbar(HisnMessage(if (ok) savedText else refusedText))
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text(stringResource(if (needsPlus) R.string.plus_unlock else R.string.yourapps_save)) }
            if (current != AppRule.NONE && current != AppRule.FEED) {
                TextButton(
                    onClick = {
                        val ok = applyRule(context, state, row, AppRule.NONE, minutes)
                        if (ok) haptics.toggle(false) else haptics.reject()
                        snackbar(HisnMessage(if (ok) savedText else refusedText))
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.yourapps_remove)) }
            }
            if (state.locked()) Text(stringResource(R.string.yourapps_locked), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

internal fun suggestedMinutes(avg: Long): Int {
    val half = (avg / 60_000L / 2).toInt()
    return ((half + 7) / 15 * 15).coerceIn(15, 120)
}

private fun tabColor(rule: AppRule) = when (rule) {
    AppRule.WAIT -> ChipWait; AppRule.LIMIT -> ChipLimit; AppRule.FEED -> ChipFeed; else -> ChipBlock
}

private fun tabName(rule: AppRule) = when (rule) {
    AppRule.WAIT -> R.string.yourapps_tab_wait; AppRule.LIMIT -> R.string.yourapps_tab_limit
    AppRule.FEED -> R.string.yourapps_tab_feed; else -> R.string.yourapps_tab_block
}

private fun tabBody(tab: AppRule) = when (tab) {
    AppRule.WAIT -> R.string.yourapps_body_wait; AppRule.LIMIT -> R.string.yourapps_body_limit
    AppRule.FEED -> R.string.yourapps_body_feed; else -> R.string.yourapps_body_block
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) }
}

/** What opening the app will look like with the chosen rule: before, then after. */
@Composable
private fun Preview(label: String, rule: AppRule, minutes: Int) {
    val shape = RoundedCornerShape(16.dp)
    val (first, second) = when (rule) {
        AppRule.WAIT -> stringResource(R.string.yourapps_pv_wait_1) to stringResource(R.string.yourapps_pv_wait_2, label)
        AppRule.LIMIT -> stringResource(R.string.yourapps_pv_limit_1) to stringResource(R.string.yourapps_pv_limit_2, label, minutes)
        AppRule.FEED -> stringResource(R.string.yourapps_pv_feed_1) to stringResource(R.string.yourapps_pv_feed_2)
        else -> stringResource(R.string.yourapps_pv_block_1) to stringResource(R.string.yourapps_pv_block_2, label)
    }
    Column(Modifier.fillMaxWidth().background(Color(0xFF24402A), shape).padding(12.dp)) {
        Text(stringResource(R.string.yourapps_pv_title, label).uppercase(appLocale()),
            style = MaterialTheme.typography.labelSmall, color = Color(0xFFC9D9C5))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PreviewTile(first, Color(0xFFF5EFE3), Modifier.weight(1f))
            Text("→", color = Color(0xFFC9D9C5), modifier = Modifier.padding(horizontal = 8.dp))
            PreviewTile(second, if (rule == AppRule.WAIT) Color(0xFFDCEBF4) else Color(0xFFFBE3DD), Modifier.weight(1f))
        }
    }
}

@Composable
private fun PreviewTile(text: String, color: Color, modifier: Modifier) {
    Box(modifier.heightIn(min = 64.dp).background(color, RoundedCornerShape(12.dp)).padding(10.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, style = MaterialTheme.typography.bodyMedium.storySerif(), color = UnrotInk)
    }
}

/** Saves one rule and clears the others for this app, so each app has exactly one. */
internal fun applyRule(context: Context, state: HisnState, row: AppRow, rule: AppRule, minutes: Int): Boolean {
    val pkg = row.packageName
    var ok = true
    val waits = OpenWaitStore.apps(context)
    fun setWait(on: Boolean) {
        if ((pkg in waits) == on) return
        val next = if (on) waits + pkg else waits - pkg
        ok = ok && OpenWaitStore.save(context, next, OpenWaitStore.seconds(context), OpenWaitStore.grows(context))
    }
    fun setLimit(on: Boolean) {
        if (on) ok = ok && state.setAppLimit(pkg, minutes)
        else if (state.appLimits[pkg] != null) ok = ok && state.removeAppLimit(pkg)
    }
    fun setBlock(on: Boolean) {
        if ((pkg in state.blockedApps) == on) return
        state.setAppBlocked(pkg, on)
        ok = ok && (pkg in state.blockedApps) == on
    }
    // Firmer first, so a lock never sees a moment with no rule at all.
    when (rule) {
        AppRule.BLOCK -> { setBlock(true); setLimit(false); setWait(false) }
        AppRule.LIMIT -> { setLimit(true); setBlock(false); setWait(false) }
        AppRule.WAIT -> { setWait(true); setBlock(false); setLimit(false) }
        AppRule.FEED -> { if (!state.shortVideos.on) state.shortVideos.set(true); setBlock(false); setLimit(false); setWait(false) }
        AppRule.NONE -> { setBlock(false); setLimit(false); setWait(false) }
    }
    state.refreshAll()
    return ok
}

private fun applyQuiet(state: HisnState, pkg: String, on: Boolean): Boolean {
    val s = state.appSchedule
    if (!s.isConfigured || s.allApps || s.targets(pkg) == on) return true
    val apps = if (on) s.apps + pkg else s.apps - pkg
    return state.saveSchedule(s, app.hisn.core.DailyAppSchedule(s.enabled, s.startMinute, s.endMinute, apps, s.allApps, s.days))
}
