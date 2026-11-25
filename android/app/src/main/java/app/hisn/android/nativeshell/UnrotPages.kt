package app.hisn.android.nativeshell

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hisn.android.BuildConfig
import app.hisn.android.DnsVpnService
import app.hisn.android.R
import app.hisn.core.SetupPlan
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Unrot's second pass over its pages, from the approved screens-v2 pictures. Each page answers
 * one question in the person's words: "what does this rule do", "is it working", "what can I
 * still change", and keeps every control the first version had.
 */

private fun clock(minute: Int): String =
    String.format(java.util.Locale.ROOT, "%02d:%02d", (minute / 60) % 24, minute % 60)

private fun capitalized(text: String): String = text.replaceFirstChar { it.titlecase() }

/** A coloured tile with a line icon, the mark every Unrot row carries. */
@Composable
private fun RowTile(icon: ImageVector, tile: Color, size: Int = 42) {
    art3D(icon)?.let { Float3D(it, (size + 2).dp); return }
    Box(
        Modifier.size(size.dp).background(tile, RoundedCornerShape((size * 0.29f).dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = UnrotInk, modifier = Modifier.size((size * 0.52f).dp))
    }
}

/** A framed card in Unrot's paper colour. */
@Composable
private fun PaperCard(
    modifier: Modifier = Modifier,
    color: Color = LocalHisnStatusColors.current.card,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier
            .riseIn(1)
            .fillMaxWidth()
            .unrotFrame(shape)
            .background(color, shape),
        content = content
    )
}

// Today.

/**
 * The evening card on Today: the person's own note, and what tonight holds (quiet hours from the
 * saved schedule, or the hour their pauses usually come). Without a note it is not shown.
 */
@Composable
internal fun UnrotTonightCard(state: HisnState, onEdit: () -> Unit) {
    val haptics = rememberHisnHaptics()
    val summary = state.personal
    if (summary.reason.isEmpty()) return
    val now = remember { LocalDateTime.now() }
    val evening = now.hour >= 17 || now.hour < 4
    val schedule = state.appSchedule
    val nowMinute = now.hour * 60 + now.minute
    val line: String? = when {
        schedule.enabled && schedule.activeAt(nowMinute, now.dayOfWeek) ->
            stringResource(R.string.today_quiet_now, clock(schedule.endMinute))
        schedule.enabled -> {
            // The next start within twelve hours, on a day the window really repeats.
            val ahead = (5..720 step 5).firstOrNull { step ->
                val at = now.plusMinutes(step.toLong())
                schedule.activeAt(at.hour * 60 + at.minute, at.dayOfWeek)
            }
            when {
                ahead == null -> null
                ahead <= 180 -> stringResource(R.string.today_quiet_soon, clock(schedule.startMinute),
                    Usage.formatDuration(ahead * 60_000L))
                else -> stringResource(R.string.today_quiet_at, clock(schedule.startMinute))
            }
        }
        summary.peakStart >= 0 -> stringResource(R.string.today_peak_hint, setupHour(summary.peakStart))
        else -> null
    }
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier
            .riseIn(4)
            .fillMaxWidth()
            .unrotFrame(shape)
            .background(LocalHisnStatusColors.current.card, shape)
            .clickable(role = Role.Button) { haptics.tap(); onEdit() }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            stringResource(if (evening) R.string.today_tonight else R.string.today_from_you),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.personal_quote, summary.reason),
            style = MaterialTheme.typography.titleLarge.storySerif(),
            textAlign = TextAlign.Center
        )
        if (line != null) {
            Text(line, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// All rules, grouped by outcome.

@Composable
internal fun UnrotRulesList(
    state: HisnState,
    onOpen: (String) -> Unit,
    resumes: Int,
    onScanAll: () -> Unit,
    onGrayWanted: (Boolean) -> Unit,
    onLockedRefusal: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val locked = state.lockRemainingMillis > 0L
    val off = stringResource(R.string.shield_row_off)
    val lockedOn = stringResource(R.string.shield_row_locked_on)
    fun lockText(on: Boolean, text: String) = if (locked && on) lockedOn else text
    val waiting = remember(resumes) { app.hisn.android.OpenWaitStore.apps(context).size }
    var privateTabs by remember(resumes) { mutableStateOf(app.hisn.android.PrivateTabsStore.on(context)) }
    var browserOnly by remember(resumes) { mutableStateOf(app.hisn.android.BrowserOnlyStore.on(context)) }
    var flagged by remember(resumes, state.tags.on) {
        mutableStateOf(if (state.tags.on) app.hisn.android.AdultNamedApps.installedFlagged(context) else emptyList())
    }
    var reviewFlagged by remember { mutableStateOf(false) }
    if (reviewFlagged) {
        AlertDialog(
            onDismissRequest = { reviewFlagged = false },
            title = { Text(stringResource(R.string.rules_flagged)) },
            text = {
                Column {
                    Text(stringResource(R.string.rules_flagged_body))
                    for (packageName in flagged) {
                        TextButton(onClick = {
                            if (app.hisn.android.AdultNamedApps.allow(context, packageName)) {
                                flagged = flagged - packageName
                                if (flagged.isEmpty()) reviewFlagged = false
                            } else onLockedRefusal()
                        }) { Text(stringResource(R.string.rules_flagged_allow, appLabel(context, packageName))) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { reviewFlagged = false }) { Text(stringResource(R.string.rules_done)) } }
        )
    }
    fun apps(count: Int) = resources.getQuantityString(R.plurals.shield_apps, count, count)
    val blockedNames = state.blockedApps.take(2).joinToString(", ") { appLabel(context, it) }
    val siteCount = java.text.NumberFormat.getIntegerInstance().format(BuildConfig.DOMAIN_SEED_COUNT)

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { PageIntro(stringResource(R.string.rules_unrot_intro)) }
        if (locked) item { UnrotLockBanner(state.lockLabel, onBreathe = state::openPause) }

        item { SectionHeader(stringResource(R.string.rules_group_close)) }
        item {
            PaperCard {
                ShieldRow(Icons.AutoMirrored.Rounded.ViewList, TileButter, stringResource(R.string.shield_row_feeds),
                    lockText(state.shortVideos.on, stringResource(if (state.shortVideos.on) R.string.shield_row_feeds_on else R.string.shield_row_off)),
                    checked = state.shortVideos.on, onChecked = state.shortVideos::set, lockedOn = locked && state.shortVideos.on)
                InsetDivider(start = 72.dp)
                ShieldRow(Icons.Rounded.Block, TileButter, stringResource(R.string.rules_whole_apps),
                    if (state.blockedApps.isEmpty()) off else apps(state.blockedApps.size) + " · " + blockedNames,
                    onClick = { onOpen("apps") })
                InsetDivider(start = 72.dp)
                ShieldRow(Icons.Rounded.Security, TilePaper, stringResource(R.string.private_tabs_title),
                    lockText(privateTabs, stringResource(
                        when {
                            privateTabs && !state.guardReady -> R.string.private_tabs_needs_guard
                            privateTabs -> R.string.private_tabs_on
                            else -> R.string.private_tabs_off
                        }
                    )),
                    checked = privateTabs, lockedOn = locked && privateTabs,
                    onChecked = { wanted ->
                        if (app.hisn.android.PrivateTabsStore.set(context, wanted)) privateTabs = wanted else onLockedRefusal()
                    })
                InsetDivider(start = 72.dp)
                ShieldRow(Icons.Rounded.Language, TilePaper, stringResource(R.string.rules_browser_only),
                    lockText(browserOnly, if (browserOnly) stringResource(R.string.rules_browser_only_on) else off),
                    checked = browserOnly, lockedOn = locked && browserOnly,
                    onChecked = { wanted ->
                        if (app.hisn.android.BrowserOnlyStore.set(context, wanted)) browserOnly = wanted else onLockedRefusal()
                    })
                InsetDivider(start = 72.dp)
                ShieldRow(Icons.Rounded.Contrast, TilePaper, stringResource(R.string.rules_gray),
                    lockText(state.gray.on, when {
                        state.gray.on && state.grayApplied -> stringResource(R.string.rules_gray_on)
                        state.gray.on -> stringResource(R.string.rules_gray_waiting)
                        else -> off
                    }),
                    checked = state.gray.on, lockedOn = locked && state.gray.on, onChecked = onGrayWanted)
            }
        }

        item { SectionHeader(stringResource(R.string.rules_group_wait)) }
        item {
            PaperCard {
                ShieldRow(Icons.Rounded.Pause, TileSky, stringResource(R.string.shield_row_wait),
                    if (waiting == 0) off else apps(waiting),
                    onClick = { context.startActivity(Intent(context, app.hisn.android.OpenWaitSettingsActivity::class.java)) })
                InsetDivider(start = 72.dp)
                ShieldRow(Icons.Rounded.Timer, TileSage, stringResource(R.string.shield_row_limits),
                    if (!state.limit.on || state.appLimits.isEmpty()) off else apps(state.appLimits.size),
                    onClick = { onOpen("limit") })
                InsetDivider(start = 72.dp)
                ShieldRow(Icons.Rounded.Bedtime, TilePaper, stringResource(R.string.shield_row_quiet),
                    if (!state.appSchedule.enabled) off
                    else stringResource(R.string.shield_row_quiet_on, clock(state.appSchedule.startMinute), clock(state.appSchedule.endMinute)),
                    onClick = { onOpen("schedule") })
            }
        }

        item { SectionHeader(stringResource(R.string.rules_group_adult)) }
        item {
            PaperCard {
                ShieldRow(Icons.Rounded.Language, TileBlush, stringResource(R.string.rules_sites),
                    lockText(state.dns.on, if (state.dns.on) stringResource(R.string.rules_sites_count, siteCount) else off),
                    checked = state.dns.on, onChecked = state.dns::set, lockedOn = locked && state.dns.on)
                InsetDivider(start = 72.dp)
                ShieldRow(Icons.Rounded.LocalOffer, TileBlush, stringResource(R.string.rules_words),
                    lockText(state.tags.on, when {
                        !state.tags.on -> off
                        state.words.isEmpty() -> stringResource(R.string.rules_words_builtin)
                        else -> stringResource(R.string.rules_words_count, state.words.size)
                    }),
                    checked = state.tags.on, onChecked = state.tags::set, lockedOn = locked && state.tags.on)
                InsetDivider(start = 72.dp)
                ShieldRow(Icons.Rounded.Image, TilePaper, stringResource(R.string.shield_row_images),
                    lockText(state.images.on, stringResource(
                        when {
                            android.os.Build.VERSION.SDK_INT < 34 -> R.string.shield_row_images_unsupported
                            state.images.on -> R.string.shield_row_images_on
                            else -> R.string.shield_row_off
                        }
                    )),
                    checked = state.images.on, lockedOn = locked && state.images.on,
                    // Image checks look inside the apps they are allowed to; with none, ask right away.
                    onChecked = { wanted ->
                        state.images.set(wanted)
                        if (wanted && state.scannedCount == 0 && !state.scanAll.on && !state.scanBrowsers()) onScanAll()
                    })
                InsetDivider(start = 72.dp)
                ShieldRow(Icons.Rounded.Apps, TilePaper, stringResource(R.string.rules_scan_all),
                    lockText(state.scanAll.on,
                        if (state.scanAll.on) stringResource(R.string.rules_scan_all_on)
                        else stringResource(R.string.rules_scan_all_off, state.scannedCount)),
                    checked = state.scanAll.on, lockedOn = locked && state.scanAll.on,
                    onChecked = { wanted -> if (wanted) onScanAll() else state.scanAll.set(false) })
                if (flagged.isNotEmpty()) {
                    InsetDivider(start = 72.dp)
                    ShieldRow(Icons.Rounded.Block, TilePaper, stringResource(R.string.rules_flagged),
                        flagged.joinToString(", ") { appLabel(context, it) }, onClick = { reviewFlagged = true })
                }
            }
        }

        item { SectionHeader(stringResource(R.string.rules_group_more)) }
        item {
            PaperCard {
                ShieldRow(Icons.AutoMirrored.Rounded.ViewList, TileSage, stringResource(R.string.lists_title),
                    stringResource(R.string.rules_lists_body), onClick = { onOpen("lists") })
                InsetDivider(start = 72.dp)
                ShieldRow(Icons.Rounded.Lock, TileBlush, stringResource(R.string.lock_unrot_title),
                    if (locked) stringResource(R.string.commitment_remaining, state.lockLabel)
                    else stringResource(R.string.rules_lock_off),
                    onClick = { onOpen("lock") })
            }
        }
    }
}

// Block whole apps.

@Composable
internal fun UnrotAppsScreen(state: HisnState, onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalHisnSnackbar.current
    val resources = LocalResources.current
    var query by rememberSaveable { mutableStateOf("") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(showAll) {
        loading = true
        apps = withContext(Dispatchers.IO) { installedApps(context, showAll) }
        loading = false
    }
    val usageAccess = state.permissions["usage"] == true
    var usage by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    LaunchedEffect(usageAccess) {
        usage = if (usageAccess) withContext(Dispatchers.IO) { Usage.usedTodayAll(context) } else emptyMap()
    }
    val blocked = state.blockedApps.toSet()
    // Picked once per visit, so a row does not jump away the moment it is ticked.
    val suggested = remember(apps, usage) {
        apps.filter { (usage[it.packageName] ?: 0L) >= 60_000L && it.packageName !in blocked }
            .sortedByDescending { usage[it.packageName] ?: 0L }
            .take(4)
    }
    val shownSuggested = suggested.map { it.packageName }.toSet()
    val visible = remember(apps, query, blocked, shownSuggested) {
        val needle = query.trim().lowercase()
        apps.filter { needle.isNotEmpty() || it.packageName !in shownSuggested }
            .filter { needle.isEmpty() || it.label.lowercase().contains(needle) }
            .sortedWith(compareByDescending<InstalledApp> { it.packageName in blocked }.thenBy { it.label.lowercase() })
    }
    val feedsOn = state.shortVideos.on

    @Composable
    fun AppRow(item: InstalledApp, top: Boolean) {
        val on = item.packageName in blocked
        val used = usage[item.packageName] ?: 0L
        val haptics = rememberHisnHaptics()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .toggleable(value = on, role = Role.Checkbox) {
                    haptics.toggle(it)
                    state.setAppBlocked(item.packageName, it)
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AppIcon(item.packageName, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(item.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val line = when {
                    used > 0L -> stringResource(R.string.apps_used_today, Usage.formatDuration(used))
                    on -> stringResource(R.string.apps_blocked_whole)
                    else -> null
                }
                if (line != null) {
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // A feed app does not have to go whole: closing only its feed keeps messages.
                if (top && !on && !feedsOn && SetupPlan.hasFeed(item.packageName)) {
                    Text(
                        stringResource(R.string.apps_close_feed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable(role = Role.Button) {
                                haptics.toggle(true)
                                state.shortVideos.set(true)
                                snackbar(HisnMessage(resources.getString(R.string.apps_feed_closed)))
                            }
                            .padding(vertical = 4.dp)
                    )
                }
            }
            Checkbox(checked = on, onCheckedChange = null)
        }
    }

    DetailScaffold(stringResource(R.string.apps_unrot_title), onBack) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.apps_search)) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            { IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.apps_clear)) } }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (loading) {
                    item { AppListSkeleton() }
                } else {
                    if (query.isBlank() && suggested.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.apps_suggested)) }
                        item { PaperCard { suggested.forEachIndexed { i, a -> AppRow(a, top = true); if (i < suggested.lastIndex) InsetDivider() } } }
                    } else if (query.isBlank() && !usageAccess) {
                        item {
                            PaperCard {
                                ShieldRow(Icons.Rounded.Search, TileSky, stringResource(R.string.apps_usage_prompt),
                                    stringResource(R.string.apps_usage_prompt_body), onClick = { state.openPermission("usage") })
                            }
                        }
                    }
                    item { SectionHeader(stringResource(R.string.apps_all)) }
                    if (visible.isEmpty()) {
                        item { PageIntro(stringResource(R.string.apps_none_match, query)) }
                    } else {
                        item { PaperCard { visible.forEachIndexed { i, a -> AppRow(a, top = false); if (i < visible.lastIndex) InsetDivider() } } }
                    }
                    item { ShowAllAppsRow(showAll) { showAll = it } }
                }
            }
            val count = blocked.size
            PillButton(
                text = if (count == 0) stringResource(R.string.rules_done)
                    else pluralStringResource(R.plurals.apps_done_count, count, count),
                onClick = onBack,
                enabled = true,
                onGreen = false
            )
        }
    }
}

// Health check.

private data class Check(
    val icon: ImageVector,
    val title: String,
    val line: String,
    val ok: Boolean?,
    val fix: (() -> Unit)?
)

@Composable
internal fun UnrotHealthScreen(state: HisnState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalHisnSnackbar.current
    val haptics = rememberHisnHaptics()
    val status = LocalHisnStatusColors.current
    val runtime = state.runtime
    var checkedAt by remember { mutableStateOf(LocalDateTime.now()) }
    val format = java.text.NumberFormat.getIntegerInstance()
    val blockedText = format.format(remember(state.blockedLookups) { app.hisn.android.StepIns.keptOutToday(context) })
    val checks = buildList {
        add(Check(
            Icons.Rounded.Language, stringResource(R.string.health_filter),
            when {
                !state.dns.on -> stringResource(R.string.health_off)
                runtime.dnsReady && state.check == DnsVpnService.CheckState.PASSED ->
                    stringResource(R.string.health_filter_passed, blockedText)
                runtime.dnsReady -> stringResource(R.string.health_filter_running, blockedText)
                else -> stringResource(R.string.health_filter_stopped)
            },
            ok = if (!state.dns.on) null else runtime.dnsReady && state.check != DnsVpnService.CheckState.FAILED,
            fix = if (state.dns.on && !runtime.dnsReady) state::fixDns else null
        ))
        add(Check(
            Icons.Rounded.Apps, stringResource(R.string.health_apps),
            stringResource(if (state.guardReady) R.string.health_connected else R.string.health_not_connected),
            ok = if (!state.apps.on) null else state.guardReady,
            fix = if (state.apps.on && !state.guardReady) ({ state.openPermission("accessibility") }) else null
        ))
        if (state.shortVideos.on) add(Check(
            Icons.AutoMirrored.Rounded.ViewList, stringResource(R.string.health_feeds),
            stringResource(if (state.guardReady) R.string.health_feeds_ok else R.string.health_feeds_wait),
            ok = state.guardReady, fix = null
        ))
        val batteryOk = state.phone.batteryUnrestricted && state.phone.backgroundRestricted != true
        add(Check(
            Icons.Rounded.BatteryChargingFull, stringResource(R.string.health_battery),
            stringResource(if (batteryOk) R.string.health_battery_ok else R.string.health_battery_bad),
            ok = batteryOk, fix = if (batteryOk) null else ({ onOpen("phone") })
        ))
        val notify = state.permissions["notifications"] == true
        add(Check(
            Icons.Rounded.Notifications, stringResource(R.string.health_notify),
            stringResource(if (notify) R.string.health_notify_ok else R.string.health_notify_bad),
            ok = notify, fix = if (notify) null else ({ state.openPermission("notifications") })
        ))
    }
    val needs = checks.count { it.ok == false }
    val canRoute = (state.dnsState == DnsVpnService.State.RUNNING || state.dnsState == DnsVpnService.State.DEGRADED) &&
        state.check != DnsVpnService.CheckState.CHECKING
    val res = androidx.compose.ui.platform.LocalResources.current
    val report = remember(checks, res) {
        buildString {
            append(res.getString(R.string.health_title)).append('\n')
            append("${app.hisn.android.Edition.NAME} ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE}\n")
            checks.forEach { append("- ").append(it.title).append(": ").append(it.line).append('\n') }
        }
    }

    DetailScaffold(stringResource(R.string.health_title), onBack) { innerPadding ->
        LazyColumn(
            Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val shape = MaterialTheme.shapes.extraLarge
                val ok = needs == 0
                Row(
                    Modifier
                        .fillMaxWidth()
                        .unrotFrame(shape, hero = true)
                        .background(if (ok) status.successContainer else status.warningContainer, shape)
                        .padding(18.dp)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(Modifier.size(44.dp).background(if (ok) status.onSuccessContainer else status.onWarningContainer, CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(if (ok) Icons.Rounded.Check else Icons.Rounded.HealthAndSafety, contentDescription = null,
                            tint = if (ok) status.successContainer else status.warningContainer)
                    }
                    Column {
                        Text(
                            if (ok) stringResource(R.string.health_all_ok) else pluralStringResource(R.plurals.health_needs, needs, needs),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (ok) status.onSuccessContainer else status.onWarningContainer,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            stringResource(R.string.health_checked_at, checkedAt.format(DateTimeFormatter.ofPattern("HH:mm"))),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ok) status.onSuccessContainer else status.onWarningContainer
                        )
                    }
                }
            }
            item {
                PaperCard {
                    checks.forEachIndexed { index, check ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp)
                                .semantics(mergeDescendants = true) {},
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            RowTile(check.icon, when (check.ok) { true -> TileSage; false -> TileButter; null -> TilePaper })
                            Column(Modifier.weight(1f)) {
                                Text(check.title, style = MaterialTheme.typography.titleMedium)
                                Text(check.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            when {
                                check.fix != null -> androidx.compose.material3.FilledTonalButton(onClick = check.fix) {
                                    Text(stringResource(R.string.health_fix))
                                }
                                check.ok == true -> Icon(Icons.Rounded.Check, contentDescription = null, tint = status.success,
                                    modifier = Modifier.popIn(index))
                            }
                        }
                        if (index < checks.lastIndex) InsetDivider(start = 72.dp)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.Button(
                        onClick = {
                            haptics.tick()
                            state.refreshAll()
                            if (canRoute) state.confirmNetworkCheck()
                            checkedAt = LocalDateTime.now()
                        },
                        enabled = state.check != DnsVpnService.CheckState.CHECKING,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                    ) {
                        Text(stringResource(
                            if (state.check == DnsVpnService.CheckState.CHECKING) R.string.health_running else R.string.health_run
                        ))
                    }
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText(app.hisn.android.Edition.NAME, report))
                            haptics.confirm()
                            snackbar(HisnMessage(resources.getString(R.string.diagnostics_copied)))
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                    ) { Text(stringResource(R.string.health_copy)) }
                }
            }
            item {
                Text(stringResource(R.string.health_copy_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
            }
            item {
                TextButton(onClick = { onOpen("diagnostics:full") }) {
                    Text(stringResource(R.string.health_support))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }
}

// Protection lock.

@Composable
internal fun UnrotLockScreen(state: HisnState, onBack: () -> Unit) {
    var days by rememberSaveable { mutableIntStateOf(7) }
    val haptics = rememberHisnHaptics()
    val resources = LocalResources.current
    val active = state.lockRemainingMillis > 0L
    val locale = appLocale()
    val base = remember(state.lockRemainingMillis) {
        LocalDateTime.now().plusSeconds(state.lockRemainingMillis.coerceAtLeast(0L) / 1000L)
    }
    val ends = base.plusDays(days.toLong()).format(DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm", locale))
    DetailScaffold(stringResource(R.string.lock_unrot_title), onBack) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Float3D(R.drawable.unrot_3d_lock, 104.dp, Modifier.popIn())
                        Text(
                            stringResource(if (active) R.string.lock_hero_active else R.string.lock_hero_title),
                            style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            if (active) stringResource(R.string.commitment_remaining, state.lockLabel)
                            else stringResource(R.string.lock_hero_body),
                            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    ConnectedChoice(
                        options = listOf(1, 7, 30, 90),
                        selected = days,
                        label = { resources.getQuantityString(R.plurals.days, it, it) },
                        onSelect = { days = it }
                    )
                }
                item {
                    PaperCard {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.lock_ends), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(ends, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                item {
                    PaperCard {
                        ShieldRow(Icons.Rounded.Check, TileSage, stringResource(R.string.lock_can), stringResource(R.string.lock_can_body))
                        InsetDivider(start = 72.dp)
                        ShieldRow(Icons.Rounded.Close, TileBlush, stringResource(R.string.lock_cant),
                            stringResource(if (BuildConfig.SELF_PROTECTION) R.string.lock_cant_body else R.string.lock_cant_body_play))
                    }
                }
                item {
                    Text(
                        stringResource(if (BuildConfig.SELF_PROTECTION) R.string.lock_footnote else R.string.lock_footnote_play),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!state.hasRestrictions) item { AttentionCard(stringResource(R.string.commitment_needs_rules)) }
            }
            PillButton(
                text = if (active) pluralStringResource(R.plurals.lock_extend, days, days)
                    else pluralStringResource(R.plurals.lock_button, days, days),
                onClick = { haptics.confirm(); state.startLock(days) },
                enabled = state.hasRestrictions,
                onGreen = false
            )
        }
    }
}

// About you: the setup answers, still editable.

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UnrotAboutYouScreen(state: HisnState, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalHisnSnackbar.current
    val haptics = rememberHisnHaptics()
    val saved = state.personal
    var name by rememberSaveable { mutableStateOf(saved.name) }
    var note by rememberSaveable { mutableStateOf(saved.customReason) }
    var goals by remember { mutableStateOf(UnrotAnswers.goals(context)) }
    var whenKey by remember { mutableStateOf(UnrotAnswers.whenKey(context)) }
    var feel by remember { mutableStateOf(UnrotAnswers.feel(context)) }
    var instead by remember { mutableStateOf(UnrotAnswers.instead(context)) }
    var confirmClear by remember { mutableStateOf(false) }
    // Chips save the moment they change; the two text fields wait for "Save".
    fun saveAnswers() = UnrotAnswers.save(context, goals, whenKey, feel, instead)
    val dirty = name.trim() != saved.name || note.trim() != saved.customReason

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.personal_clear_title)) },
            text = { Text(stringResource(R.string.about_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    state.clearPersonal()
                    UnrotAnswers.clear(context)
                    haptics.confirm()
                    snackbar(HisnMessage(resources.getString(R.string.personal_removed)))
                    onBack()
                }) { Text(stringResource(R.string.personal_clear_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }

    @Composable
    fun Chips(keys: List<String>, title: (String) -> Int, on: (String) -> Boolean, toggle: (String) -> Unit) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            keys.forEach { key ->
                FilterChip(selected = on(key), onClick = { haptics.tick(); toggle(key) },
                    label = { Text(stringResource(title(key))) },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chosenFill(),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface
                    ),
                    leadingIcon = if (on(key)) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null)
            }
        }
    }

    DetailScaffold(stringResource(R.string.about_title), onBack) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = limitCodePoints(it, app.hisn.core.PersonalProfile.MAX_NAME_CODE_POINTS) },
                        label = { Text(stringResource(R.string.about_name)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.storySerif(),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = limitCodePoints(it, app.hisn.core.PersonalProfile.MAX_REASON_CODE_POINTS) },
                        label = { Text(stringResource(R.string.about_note)) },
                        placeholder = { Text(stringResource(R.string.about_note_hint)) },
                        textStyle = MaterialTheme.typography.titleMedium.storySerif(),
                        minLines = 2, maxLines = 3,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { SectionHeader(stringResource(R.string.about_goals)) }
                item {
                    Chips(UnrotAnswers.GOALS, UnrotAnswers::goalTitle, { it in goals }) {
                        goals = if (it in goals) goals - it else goals + it; saveAnswers()
                    }
                }
                item { SectionHeader(stringResource(R.string.about_when)) }
                item {
                    Chips(UnrotAnswers.WHEN, UnrotAnswers::whenTitle, { it == whenKey }) {
                        whenKey = if (whenKey == it) "" else it; saveAnswers()
                    }
                }
                item { SectionHeader(stringResource(R.string.about_feel)) }
                item {
                    Chips(UnrotAnswers.FEEL, UnrotAnswers::feelTitle, { it in feel }) {
                        feel = if (it in feel) feel - it else feel + it; saveAnswers()
                    }
                }
                item { SectionHeader(stringResource(R.string.about_instead)) }
                item {
                    Chips(UnrotAnswers.INSTEAD, UnrotAnswers::insteadTitle, { it in instead }) {
                        instead = if (it in instead) instead - it else instead + it; saveAnswers()
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.about_private), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { confirmClear = true }) {
                            Text(stringResource(R.string.about_remove), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (dirty) {
                PillButton(
                    text = stringResource(R.string.personal_save),
                    onClick = {
                        val cleanNote = note.trim()
                        val preset = if (cleanNote.isNotEmpty()) app.hisn.core.PersonalProfile.CUSTOM
                            else saved.preset.takeIf { it != app.hisn.core.PersonalProfile.CUSTOM } ?: ""
                        state.savePersonal(name.trim(), preset, cleanNote)
                        haptics.confirm()
                        snackbar(HisnMessage(resources.getString(R.string.personal_saved)))
                    },
                    enabled = true,
                    onGreen = false
                )
            }
        }
    }
}

// Your lists: personal sites, words and apps, the built-in list, and moving them to another phone.

@Composable
internal fun UnrotListsScreen(state: HisnState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptics = rememberHisnHaptics()
    var summary by remember { mutableStateOf(state.listUpdateSummary()) }
    var checking by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<String?>(null) }
    val none = stringResource(R.string.lists_none)
    fun sample(items: List<String>) = if (items.isEmpty()) none
        else "${items.size} · " + items.take(2).joinToString(", ")
    val siteCount = java.text.NumberFormat.getIntegerInstance().format(BuildConfig.DOMAIN_SEED_COUNT)

    DetailScaffold(stringResource(R.string.lists_title), onBack) { innerPadding ->
        LazyColumn(
            Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PaperCard {
                    ShieldRow(Icons.Rounded.Language, TileBlush, stringResource(R.string.lists_sites),
                        sample(state.domains.toList()), onClick = { onOpen("domains") })
                    InsetDivider(start = 72.dp)
                    ShieldRow(Icons.Rounded.LocalOffer, TileBlush, stringResource(R.string.lists_words),
                        if (state.words.isEmpty()) none else resources.getQuantityString(R.plurals.lists_words_count, state.words.size, state.words.size),
                        onClick = { onOpen("words") })
                    InsetDivider(start = 72.dp)
                    ShieldRow(Icons.Rounded.Block, TileButter, stringResource(R.string.rules_whole_apps),
                        sample(state.blockedApps.map { appLabel(context, it) }), onClick = { onOpen("apps") })
                }
            }
            item { SectionHeader(stringResource(R.string.lists_builtin)) }
            item {
                PaperCard {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        RowTile(Icons.Rounded.Security, TileSage)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.lists_adult), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.lists_adult_meta, siteCount), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (summary.isNotBlank()) {
                                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        androidx.compose.material3.FilledTonalButton(
                            onClick = {
                                haptics.tick(); checking = true; outcome = null
                                state.checkListUpdate {
                                    checking = false; outcome = it; summary = state.listUpdateSummary()
                                }
                            },
                            enabled = !checking
                        ) { Text(stringResource(if (checking) R.string.lists_updating else R.string.lists_update)) }
                    }
                    outcome?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.lists_move)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { state.exportRules() }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.lists_export))
                    }
                    OutlinedButton(onClick = { state.importRules() }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.lists_import))
                    }
                }
            }
            item {
                Text(stringResource(R.string.lists_file_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// How Unrot works, in four steps.

@Composable
internal fun UnrotHowScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val steps = listOf(
        R.string.how_1_t to R.string.how_1_b,
        R.string.how_2_t to R.string.how_2_b,
        R.string.how_3_t to R.string.how_3_b,
        R.string.how_4_t to R.string.how_4_b
    )
    DetailScaffold(stringResource(R.string.how_title), onBack) { innerPadding ->
        LazyColumn(
            Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            steps.forEachIndexed { index, (title, body) ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
                        Box(
                            Modifier.size(34.dp).background(TileButter, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text("${index + 1}", style = MaterialTheme.typography.titleSmall, color = UnrotInk) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                PaperCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.how_stops), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.how_stops_body), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { onOpen("info:bypass") }, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(R.string.how_bypass_link))
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { onOpen("info:why") }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.why_screen_title))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                }
            }
            item {
                TextButton(onClick = { onOpen("info:privacy") }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.how_privacy_link))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }
}

// Tools and help: four tiles on top.

@Composable
internal fun UnrotToolTiles(onOpen: (String) -> Unit, onReplay: () -> Unit) {
    val haptics = rememberHisnHaptics()
    val tiles = listOf(
        Triple(Icons.Rounded.EditNote, R.string.notes_title) { onOpen("notes") },
        Triple(Icons.Rounded.HealthAndSafety, R.string.tools_health) { onOpen("diagnostics") },
        Triple(Icons.AutoMirrored.Rounded.List, R.string.lists_title) { onOpen("lists") },
        Triple(Icons.Rounded.PlayArrow, R.string.tools_replay, onReplay)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEachIndexed { rowIndex, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEachIndexed { col, (icon, label, open) ->
                    val shape = MaterialTheme.shapes.large
                    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    Column(
                        Modifier
                            .weight(1f)
                            .riseIn(rowIndex * 2 + col)
                            .pressScale(interaction)
                            .heightIn(min = 100.dp)
                            .unrotFrame(shape)
                            .background(LocalHisnStatusColors.current.card, shape)
                            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), role = Role.Button) { haptics.tap(); open() }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RowTile(icon, TileButter, size = 38)
                        Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

// Daily limits: a first visit suggests limits from the person's own most-used apps.

/** About two-thirds of today's time, rounded to 5 minutes, never under 15. */
internal fun suggestedLimit(usedMs: Long): Int =
    (((usedMs / 60_000L) * 2 / 3 / 5) * 5).toInt().coerceIn(15, 180)

@Composable
internal fun UnrotLimitSuggestions(state: HisnState) {
    val context = LocalContext.current
    val haptics = rememberHisnHaptics()
    val picks by androidx.compose.runtime.produceState<List<Pair<String, Long>>>(emptyList()) {
        value = withContext(Dispatchers.IO) {
            val usual = installedApps(context).map { it.packageName }.filter { isUsualPick(it) }.toSet()
            Usage.usedTodayAll(context).filter { it.key in usual && it.value >= 20 * 60_000L }
                .toList().sortedByDescending { it.second }.take(3)
        }
    }
    if (picks.isEmpty()) return
    var chosen by remember(picks) { mutableStateOf(picks.map { it.first }.toSet()) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(stringResource(R.string.limit_suggested))
        PaperCard {
            picks.forEachIndexed { index, (pkg, used) ->
                val on = pkg in chosen
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp)
                        .toggleable(value = on, role = Role.Checkbox) { chosen = if (it) chosen + pkg else chosen - pkg }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AppIcon(pkg, 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(appLabel(context, pkg), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.apps_used_today, Usage.formatDuration(used)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        stringResource(R.string.limit_minutes, suggestedLimit(used)),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) UnrotInk else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(if (on) TileButter else Color.Transparent, RoundedCornerShape(12.dp))
                            .border(1.5.dp, if (on) UnrotInk else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                if (index < picks.lastIndex) InsetDivider(start = 66.dp)
            }
        }
        Text(stringResource(R.string.limit_suggest_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.Button(
            onClick = {
                haptics.confirm()
                picks.filter { it.first in chosen }.forEach { (pkg, used) -> state.setAppLimit(pkg, suggestedLimit(used)) }
                if (!state.limit.on) state.limit.set(true)
            },
            enabled = chosen.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text(pluralStringResource(R.plurals.limit_add, chosen.size, chosen.size)) }
    }
}

// Permissions: what each Android switch is for, and whether it is on.

@Composable
internal fun UnrotPermissionsScreen(state: HisnState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val haptics = rememberHisnHaptics()
    val status = LocalHisnStatusColors.current
    val tiles = mapOf("accessibility" to TileButter, "dns" to TileBlush, "usage" to TileSage,
        "overlay" to TileSky, "notifications" to TilePaper, "battery" to TileSage)
    val on = PermissionItems.count { state.permissions[it.key] == true }
    DetailScaffold(stringResource(R.string.settings_permissions), onBack) { innerPadding ->
        LazyColumn(
            Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.riseIn(0)) {
                    Text(stringResource(R.string.perm_unrot_count, on, PermissionItems.size),
                        style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PermissionItems.forEachIndexed { i, item ->
                            val granted = state.permissions[item.key] == true
                            Box(Modifier.weight(1f).height(8.dp).then(if (granted) Modifier.popIn(i) else Modifier)
                                .background(if (granted) status.success else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    RoundedCornerShape(4.dp)))
                        }
                    }
                }
            }
            item {
                PaperCard(Modifier.riseIn(1)) {
                    ShieldRow(Icons.Rounded.BatteryChargingFull, TileSage, stringResource(R.string.phone_title),
                        stringResource(R.string.phone_checks_body, state.phone.maker.ifEmpty { "Android" }),
                        onClick = { onOpen("phone") })
                }
            }
            item {
                PaperCard(Modifier.riseIn(2)) {
                    PermissionItems.forEachIndexed { index, item ->
                        val granted = state.permissions[item.key] == true
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                .clickable(role = Role.Button) { haptics.tap(); state.openPermission(item.key) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            RowTile(item.icon, tiles[item.key] ?: TilePaper)
                            Column(Modifier.weight(1f)) {
                                // The same name as on Shield, so one permission has one name.
                                val access = item.key == "accessibility"
                                Text(stringResource(if (access) R.string.shield_row_access else item.title), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(if (access) R.string.perm_unrot_access_body else item.body), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                stringResource(if (granted) R.string.perm_on else R.string.perm_off),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (granted) status.onSuccessContainer else status.onWarningContainer,
                                modifier = Modifier
                                    .background(if (granted) status.successContainer else status.warningContainer, CircleShape)
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                        if (index < PermissionItems.lastIndex) InsetDivider(start = 72.dp)
                    }
                }
            }
        }
    }
}
