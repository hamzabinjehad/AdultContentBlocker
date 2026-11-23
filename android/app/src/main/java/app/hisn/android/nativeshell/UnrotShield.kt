package app.hisn.android.nativeshell

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.unit.dp
import app.hisn.android.R

/*
 * Unrot's Shield tab, as drawn in the mockup: one status sentence, the real numbers, and the
 * protection sorted by outcome (feeds, waits, limits, quiet hours, adult sites, images) rather
 * than by engine layer. CleanPath's blocker tab shows one big "Shield is Off" and a list count;
 * here every row says its real state in one line, a switch where it is on/off and an arrow where
 * there is more to set. "+ Add" opens one sheet for every kind of rule.
 */

internal val TileButter = Color(0xFFF4C34E)
internal val TileSky = Color(0xFF9CC8E0)
internal val TileSage = Color(0xFF9EC39A)
internal val TileBlush = Color(0xFFF2A08F)
internal val TilePaper = Color(0xFFE6DDCB)

/** The status in one sentence, with the one action that would fix it. */
@Composable
internal fun UnrotShieldStatus(
    healthy: Boolean,
    title: String,
    body: String,
    action: String?,
    onAction: () -> Unit,
    parts: List<Pair<String, Boolean>> = emptyList()
) {
    // Laid out like CleanPath's Blocker: one big object that is the status (our glass), one plain
    // line, and one big button that fixes it. When something is off the glass gets an amber ring.
    val shape = RoundedCornerShape(32.dp)
    val warm = Color(0xFFFFB366)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    if (healthy) listOf(Color(0xFF3A2810), Color(0xFF140D06), Color(0xFF0B0805))
                    else listOf(Color(0xFF5A2A0E), Color(0xFF1C0C05), Color(0xFF0B0604))
                )
            )
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val b = breath()
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            if (!healthy) {
                androidx.compose.foundation.Canvas(Modifier.size(196.dp).graphicsLayer { alpha = 0.7f + b * 0.3f }) {
                    drawCircle(
                        color = warm,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 3.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 12f))
                        )
                    )
                }
            }
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(R.drawable.unrot_glass_sprout),
                contentDescription = null,
                modifier = Modifier.size(180.dp).clip(CircleShape)
                    .graphicsLayer { scaleX = 1f + b * 0.02f; scaleY = 1f + b * 0.02f }
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = if (healthy) Color(0xFFF2C94F) else Color.White,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            }
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFD8CCB4),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (!healthy) UnrotPartsList(parts, Color.White)
        if (action != null) {
            if (healthy) {
                androidx.compose.material3.TextButton(onClick = onAction) { Text(action, color = Color(0xFFF2C94F)) }
            } else {
                Button(
                    onClick = onAction,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = warm, contentColor = Color(0xFF2A1206)),
                    modifier = Modifier.padding(top = 4.dp).heightIn(min = 52.dp)
                ) { Text(action, style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

/** The protection, sorted by what the person wants. */
@Composable
internal fun UnrotShieldLayers(state: HisnState, onOpen: (String) -> Unit, waitingApps: Int, tab: Int = SHIELD_ALL) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val shape = MaterialTheme.shapes.large
    val limits = state.appLimits
    val schedule = state.appSchedule
    fun apps(count: Int) = resources.getQuantityString(R.plurals.shield_apps, count, count)
    // Feeds, waits, limits and quiet hours run through app access; while it is off they are
    // saved but not working, and the row says so instead of claiming the result.
    val access = state.apps.on
    val needsAccess = stringResource(R.string.shield_row_needs_access)
    // While a protection lock runs, what is on stays on: the row says so and its switch is
    // dimmed, but anything off can still be turned on (stricter is always allowed).
    val locked = state.lockRemainingMillis > 0L
    val lockedOn = stringResource(R.string.shield_row_locked_on)
    fun lockText(on: Boolean, text: String) = if (locked && on) lockedOn else text
    var explaining by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(-1) }
    val openWait = {
        context.startActivity(Intent(context, app.hisn.android.OpenWaitSettingsActivity::class.java))
    }
    // title, plain words, and where "Change it" goes (null: the switch on the row is the change).
    val explanations = listOf(
        Triple(R.string.shield_row_feeds, R.string.explain_feeds, null),
        Triple(R.string.shield_row_wait, R.string.explain_wait, openWait),
        Triple(R.string.shield_row_limits, R.string.explain_limits, { onOpen("limit") }),
        Triple(R.string.shield_row_quiet, R.string.explain_quiet, { onOpen("schedule") }),
        Triple(R.string.shield_row_sites, R.string.explain_sites, { onOpen("domains") }),
        Triple(R.string.shield_row_words, R.string.explain_words, { onOpen("words") }),
        Triple(R.string.shield_row_images, R.string.explain_images, null),
        Triple(R.string.shield_row_access, R.string.explain_access, null)
    )
    explanations.getOrNull(explaining)?.let { (title, body, change) ->
        ExplainSheet(
            title = stringResource(title),
            body = stringResource(body),
            change = if (change != null) stringResource(R.string.explain_change) else null,
            onChange = { change?.invoke() },
            onDismiss = { explaining = -1 }
        )
    }
    var row = 0
    fun explain(): () -> Unit { val n = row++; return { explaining = n } }
    // Each row belongs to a tab; "All" shows every row. App access feeds the app rules, so it
    // sits with Feeds and apps.
    var shown = 0
    @Composable
    fun Part(group: Int, content: @Composable () -> Unit) {
        if (tab != SHIELD_ALL && tab != group) return
        if (shown++ > 0) InsetDivider(start = 72.dp)
        content()
    }
    fun real(on: Boolean, text: String) = if (on && !access) needsAccess else text
    Column(
        Modifier
            .riseIn(2)
            .fillMaxWidth()
            .unrotFrame(shape)
            .background(LocalHisnStatusColors.current.card, shape)
    ) {
        val e0 = explain()
        Part(SHIELD_FEEDS) {
            ShieldRow(Icons.AutoMirrored.Rounded.ViewList, TileButter, stringResource(R.string.shield_row_feeds),
            lockText(state.shortVideos.on, real(state.shortVideos.on,
                stringResource(if (state.shortVideos.on) R.string.shield_row_feeds_on else R.string.shield_row_off))),
            checked = state.shortVideos.on, onChecked = state.shortVideos::set, lockedOn = locked && state.shortVideos.on,
            onLongPress = e0)
        }
        val e1 = explain()
        Part(SHIELD_FEEDS) {
            ShieldRow(Icons.Rounded.Pause, TileSky, stringResource(R.string.shield_row_wait),
            if (waitingApps == 0) stringResource(R.string.shield_row_off) else real(true, apps(waitingApps)),
            onClick = {
                context.startActivity(Intent(context, app.hisn.android.OpenWaitSettingsActivity::class.java))
            },
            onLongPress = e1)
        }
        val e2 = explain()
        Part(SHIELD_FEEDS) {
            ShieldRow(Icons.Rounded.Timer, TileSage, stringResource(R.string.shield_row_limits),
            if (!state.limit.on || limits.isEmpty()) stringResource(R.string.shield_row_off)
                else if (locked) stringResource(R.string.shield_row_locked_stricter) else real(true, limitLive(limits) ?: apps(limits.size)),
            onClick = { onOpen("limit") },
            onLongPress = e2)
        }
        val e3 = explain()
        Part(SHIELD_FEEDS) {
            ShieldRow(Icons.Rounded.Bedtime, TilePaper, stringResource(R.string.shield_row_quiet),
            if (!schedule.enabled) stringResource(R.string.shield_row_off) else real(true, quietLive(schedule)
                ?: stringResource(R.string.shield_row_quiet_on, minute(schedule.startMinute), minute(schedule.endMinute))),
            onClick = { onOpen("schedule") },
            onLongPress = e3)
        }
        val e4 = explain()
        Part(SHIELD_ADULT) {
            ShieldRow(Icons.Rounded.Language, TileBlush, stringResource(R.string.shield_row_sites),
            lockText(state.dns.on, stringResource(if (state.dns.on) R.string.shield_row_sites_on else R.string.shield_row_off)),
            checked = state.dns.on, onChecked = state.dns::set, lockedOn = locked && state.dns.on,
            onLongPress = e4)
        }
        val e5 = explain()
        Part(SHIELD_ADULT) {
            ShieldRow(Icons.Rounded.LocalOffer, TileBlush, stringResource(R.string.shield_row_words),
            stringResource(if (state.tags.on) R.string.shield_row_words_on else R.string.shield_row_off),
            checked = state.tags.on, onChecked = state.tags::set, lockedOn = locked && state.tags.on,
            onLongPress = e5)
        }
        val e6 = explain()
        Part(SHIELD_ADULT) {
            ShieldRow(Icons.Rounded.Image, TilePaper, stringResource(R.string.shield_row_images),
            stringResource(
                when {
                    android.os.Build.VERSION.SDK_INT < 34 -> R.string.shield_row_images_unsupported
                    state.images.on -> R.string.shield_row_images_on
                    else -> R.string.shield_row_off
                }
            ),
            checked = state.images.on, onChecked = state.images::set, lockedOn = locked && state.images.on,
            onLongPress = e6)
        }
        // Not an outcome but what the outcomes above run on, so it sits last and says so.
        val e7 = explain()
        Part(SHIELD_FEEDS) {
            ShieldRow(Icons.Rounded.Apps, TilePaper, stringResource(R.string.shield_row_access),
            stringResource(R.string.shield_row_access_body),
            checked = state.apps.on, onChecked = state.apps::set, lockedOn = locked && state.apps.on,
            onLongPress = e7)
        }
    }
}

internal const val SHIELD_ALL = 0
internal const val SHIELD_FEEDS = 1
internal const val SHIELD_ADULT = 2

private fun minute(value: Int): String =
    String.format(java.util.Locale.ROOT, "%02d:%02d", value / 60, value % 60)

@Composable
internal fun ShieldRow(
    icon: ImageVector,
    tile: Color,
    title: String,
    supporting: String,
    checked: Boolean? = null,
    onChecked: (Boolean) -> Unit = {},
    onClick: (() -> Unit)? = null,
    lockedOn: Boolean = false,
    onLongPress: (() -> Unit)? = null
) {
    val haptics = rememberHisnHaptics()
    val base = Modifier
        .fillMaxWidth()
        .heightIn(min = 64.dp)
    // Explain mode: a long-press on any row opens its plain-words card.
    val interactive = when {
        checked != null -> base
            .combinedClickable(
                role = Role.Switch,
                onLongClick = onLongPress,
                onClick = {
                    if (!lockedOn) {
                        haptics.toggle(!checked)
                        onChecked(!checked)
                    } else haptics.reject()
                }
            )
            .semantics {
                toggleableState = androidx.compose.ui.state.ToggleableState(checked)
                if (lockedOn) disabled()
            }
        onClick != null -> base.combinedClickable(role = Role.Button, onClick = { haptics.tap(); onClick() }, onLongClick = onLongPress?.let { long -> { haptics.edge(); long() } })
        else -> base
    }
    Row(
        interactive.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val art = art3D(icon)
        if (art != null) Float3D(art, 44.dp) else Box(
            Modifier
                .size(42.dp)
                .background(tile, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = UnrotInk, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            checked != null -> Switch(checked = checked, onCheckedChange = null, enabled = !lockedOn)
            onClick != null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One sheet for every kind of rule, each worded as the outcome and opening its editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnrotAddSheet(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                stringResource(R.string.shield_add_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).semantics { heading() }
            )
            AddOption(Icons.Rounded.Language, TileBlush, R.string.shield_add_site, R.string.shield_add_site_body) {
                onDismiss(); onOpen("domains")
            }
            AddOption(Icons.Rounded.Pause, TileSky, R.string.shield_add_wait, R.string.shield_add_wait_body) {
                onDismiss()
                context.startActivity(Intent(context, app.hisn.android.OpenWaitSettingsActivity::class.java))
            }
            AddOption(Icons.Rounded.Timer, TileSage, R.string.shield_add_limit, R.string.shield_add_limit_body) {
                onDismiss(); onOpen("limit")
            }
            AddOption(Icons.Rounded.Bedtime, TilePaper, R.string.shield_add_quiet, R.string.shield_add_quiet_body) {
                onDismiss(); onOpen("schedule")
            }
            AddOption(Icons.Rounded.Block, TileButter, R.string.shield_add_app, R.string.shield_add_app_body) {
                onDismiss(); onOpen("apps")
            }
            AddOption(Icons.Rounded.LocalOffer, TilePaper, R.string.shield_add_word, R.string.shield_add_word_body) {
                onDismiss(); onOpen("words")
            }
        }
    }
}

@Composable
private fun AddOption(icon: ImageVector, tile: Color, title: Int, body: Int, onClick: () -> Unit) {
    val haptics = rememberHisnHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button) { haptics.tap(); onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val linkArt = art3D(icon)
        if (linkArt != null) Float3D(linkArt, 44.dp)
        else Box(Modifier.size(42.dp).background(tile, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = UnrotInk, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * When something breaks: which part stopped and what still works, one line each. Calm, exact,
 * and only the parts the person turned on.
 */
@Composable
internal fun UnrotPartsList(parts: List<Pair<String, Boolean>>, color: Color) {
    if (parts.size < 2) return
    val status = LocalHisnStatusColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 2.dp, bottom = 2.dp)
            .background(color.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        parts.forEach { (label, running) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).background(if (running) status.success else status.warning, CircleShape))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = color,
                    modifier = Modifier.weight(1f).padding(start = 10.dp))
                Text(
                    stringResource(if (running) R.string.part_running else R.string.part_stopped),
                    style = MaterialTheme.typography.labelLarge,
                    color = color
                )
            }
        }
    }
}

/** Whether Android reports a VPN on the active network; ours is not running when this is asked. */
@Composable
internal fun otherVpnActive(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
        }.getOrDefault(false)
    }
}

/** Why the website filter cannot start, both choices, and what still works either way. */
@Composable
internal fun UnrotVpnConflict(onSettings: () -> Unit, onKeep: () -> Unit) {
    val status = LocalHisnStatusColors.current
    val shape = MaterialTheme.shapes.extraLarge
    Column(
        Modifier
            .fillMaxWidth()
            .unrotFrame(shape)
            .background(status.card, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.vpn_conflict_title), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.vpn_conflict_body), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(
            R.string.vpn_conflict_ours to R.string.vpn_conflict_ours_body,
            R.string.vpn_conflict_theirs to R.string.vpn_conflict_theirs_body
        ).forEachIndexed { index, (title, body) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${index + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(body), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSettings) { Text(stringResource(R.string.vpn_conflict_settings)) }
            androidx.compose.material3.TextButton(onClick = onKeep) { Text(stringResource(R.string.vpn_conflict_keep)) }
        }
    }
}

/** The lock, always visible on Shield while it runs: how long, what it allows, and a way through. */
@Composable
internal fun UnrotLockBanner(remaining: String, onBreathe: () -> Unit) {
    val shape = MaterialTheme.shapes.extraLarge
    Column(
        Modifier
            .fillMaxWidth()
            .unrotFrame(shape, hero = true)
            .background(LocalHisnStatusColors.current.card, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(androidx.compose.material.icons.Icons.Rounded.Lock, contentDescription = null)
            Text(stringResource(R.string.shield_locked_title, remaining), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
        }
        Text(stringResource(R.string.shield_locked_body), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.shield_locked_urge), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onBreathe) { Text(stringResource(R.string.shield_locked_breathe)) }
        }
    }
}

/**
 * Quiet hours as they stand right now, like a rule that knows the clock: "On now · 6 h 40 left"
 * or "Starts in 3 h 10". It walks forward in five-minute steps through the coming week, so days
 * the window skips are counted too, and refreshes each minute. Null when the window never runs.
 */
@Composable
internal fun quietLive(schedule: app.hisn.core.DailyAppSchedule): String? {
    val now by androidx.compose.runtime.produceState(java.time.LocalDateTime.now(), schedule) {
        while (true) { value = java.time.LocalDateTime.now(); kotlinx.coroutines.delay(60_000L) }
    }
    val state = androidx.compose.runtime.remember(schedule, now.hour, now.minute) {
        fun on(t: java.time.LocalDateTime) = schedule.activeAt(t.hour * 60 + t.minute, t.dayOfWeek)
        val active = on(now)
        var step = 0
        var t = now
        while (step < 7 * 288 && on(t) == active) { t = t.plusMinutes(5); step++ }
        if (step >= 7 * 288) null else active to step * 5 * 60_000L
    } ?: return null
    val (active, ms) = state
    return stringResource(if (active) R.string.shield_row_quiet_now else R.string.shield_row_quiet_starts, Wins.short(ms))
}

/**
 * The daily limit closest to running out, as it stands now: "YouTube · 12 min left", or
 * "YouTube used up today". Reads Android's own screen-time record off the main thread and
 * refreshes each minute; null until it has an answer or without usage access.
 */
@Composable
internal fun limitLive(limits: Map<String, Int>): String? {
    val context = LocalContext.current
    val tightest by androidx.compose.runtime.produceState<Pair<String, Long>?>(null, limits) {
        while (true) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    if (!Usage.hasAccess(context)) return@runCatching null
                    val used = Usage.usedTodayAll(context)
                    limits.map { (pkg, min) -> pkg to (min * 60_000L - (used[pkg] ?: 0L)) }.minByOrNull { it.second }
                        ?.let { (pkg, left) ->
                            val label = runCatching {
                                context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
                            }.getOrDefault(pkg)
                            label to left
                        }
                }.getOrNull()
            }
            kotlinx.coroutines.delay(60_000L)
        }
    }
    val (label, left) = tightest ?: return null
    return if (left <= 0L) stringResource(R.string.shield_row_limit_done, label)
        else stringResource(R.string.shield_row_limit_left, label, Wins.short(left))
}

/** Shield's three tabs, a pill row like CleanPath's Blocker: All · Feeds and apps · Adult. */
@Composable
internal fun UnrotShieldTabs(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(R.string.shield_tab_all, R.string.shield_tab_feeds, R.string.shield_tab_adult)
    Row(
        Modifier.fillMaxWidth()
            .background(Color.White.copy(alpha = .08f), CircleShape)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { i, res ->
            val on = i == selected
            Text(
                stringResource(res),
                style = MaterialTheme.typography.labelLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                color = if (on) Color(0xFF241708) else Color(0xFFA99D86),
                modifier = Modifier
                    .weight(1f)
                    .background(if (on) Color(0xFFFFF8E6) else Color.Transparent, CircleShape)
                    .selectable(selected = on, role = androidx.compose.ui.semantics.Role.Tab) { onSelect(i) }
                    .padding(vertical = 10.dp)
            )
        }
    }
}
