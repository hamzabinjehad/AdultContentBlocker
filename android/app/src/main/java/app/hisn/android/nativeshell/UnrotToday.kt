package app.hisn.android.nativeshell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.hisn.android.R
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.CenterFocusStrong
import app.hisn.core.PersonalProfile
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/*
 * Unrot's Today, rebuilt after studying CleanPath's home screen:
 *  - one big number at the top with a segmented bar to the next milestone (CleanPath shows a
 *    ticking clean-time clock; ours is "Day N" since setup and it never resets, so a hard day
 *    never wipes progress),
 *  - a row of four labelled tools under it (CleanPath's round shortcuts, but each one opens a
 *    real feature: the breathing pause, your note, the site check, the protection lock),
 *  - the urge button floating above the tabs at all times (CleanPath's "Panic Mode"), as a native
 *    extended FAB with a label, never an unlabelled icon.
 */

/**
 * The day count. It takes the hero frame only while protection is healthy and the count has
 * started: when something needs fixing, the status card is the one thing that should stand out.
 */
@Composable
internal fun UnrotDayCard(day: Int, pauses: Int, onStart: () -> Unit, onWeek: () -> Unit = {}, hero: Boolean = true, sticker: Int = 0) {
    val haptics = rememberHisnHaptics()
    val colors = MaterialTheme.colorScheme
    val resources = LocalResources.current
    val shape = MaterialTheme.shapes.extraLarge
    if (day <= 0) {
        Row(
            Modifier
                .fillMaxWidth()
                .unrotFrame(shape, hero = hero && day > 0)
                .background(LocalHisnStatusColors.current.card, shape)
                .clickable(role = Role.Button) { haptics.confirm(); onStart() }
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.today_day_start_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    stringResource(R.string.today_day_start_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
        return
    }
    val locale = androidx.core.os.ConfigurationCompat.getLocales(LocalConfiguration.current)[0]
        ?: java.util.Locale.ENGLISH
    val since = remember(day, locale) {
        LocalDate.now().minusDays((day - 1).toLong())
            .format(DateTimeFormatter.ofPattern("d MMM", locale))
    }
    val next = PersonalProfile.nextMilestone(day)
    val last = PersonalProfile.lastMilestone(day)
    val progress = PersonalProfile.milestoneProgress(day)
    val pauseLine = if (pauses > 0) {
        resources.getQuantityString(R.plurals.personal_pauses, pauses, pauses)
    } else {
        resources.getString(R.string.personal_no_pauses)
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        Modifier
            .riseIn(1)
            .fillMaxWidth()
            .unrotFrame(shape, hero = hero && day > 0)
            .background(LocalHisnStatusColors.current.card, shape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.today_see_week)) { haptics.tap(); onWeek() }
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)
    ) {
        // Premium hero: the day count with a floating gold trophy beside it.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.today_since_label, since),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.personal_day_number, countUp(day.toLong(), 800).toInt().coerceAtLeast(1)),
                    style = MaterialTheme.typography.displayMedium,
                    color = if (LocalDarkTheme.current) Color(0xFFF2C94F) else Color.Unspecified,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Float3D(R.drawable.unrot_3d_trophy, 88.dp)
        }
        if (day in 1..7) {
            // The first week moves: kept days pop in, today breathes, a new day bursts once.
            Spacer(Modifier.height(6.dp))
            FirstWeekPath(day)
            Text(pauseLine, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        } else if (next > 0) {
            Spacer(Modifier.height(10.dp))
            SegmentedProgress(
                progress = progress,
                segments = (next - last).coerceIn(2, 10),
                description = resources.getString(R.string.personal_milestone_progress, next)
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    resources.getQuantityString(R.plurals.personal_milestone_next, next - day, next - day, next),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(pauseLine, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        // The last milestone sticker the person kept, with the way to share it again.
        if (sticker > 0) {
            val title = milestoneTitle(sticker)
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.HorizontalDivider(color = colors.onSurface.copy(alpha = 0.12f), thickness = 1.5.dp)
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.size(30.dp).background(if (LocalDarkTheme.current) Color(0xFFF2C14E) else UnrotInk, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$sticker", style = MaterialTheme.typography.labelLarge,
                        color = if (LocalDarkTheme.current) UnrotInk else Color(0xFFF4C34E))
                }
                Text(stringResource(R.string.today_sticker_kept, sticker), style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { shareDayCard(context, sticker, title) }) {
                    Text(stringResource(R.string.milestone_share_short))
                }
            }
        }
    }
}

/** A bar cut into equal steps; full steps are ink (gold in dark), the current one half-filled. */
@Composable
private fun SegmentedProgress(progress: Float, segments: Int, description: String) {
    val dark = LocalDarkTheme.current
    val on = if (dark) Color(0xFFF2C14E) else MaterialTheme.colorScheme.onSurface
    val off = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val filled = grow(progress.coerceIn(0f, 1f), 250) * segments
    Row(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(segments) { index ->
            val part = (filled - index).coerceIn(0f, 1f)
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .background(off, RoundedCornerShape(4.dp))
            ) {
                if (part > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(part)
                            .height(8.dp)
                            .background(on, RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
internal fun UnrotQuickTools(
    state: HisnState,
    onPause: () -> Unit,
    onNote: () -> Unit,
    onOpen: (String) -> Unit
) {
    val haptics = rememberHisnHaptics()
    val focusLeft = focusLeftMs()
    var more by remember { mutableStateOf(false) }
    if (more) UnrotMoreSheet(
        onDismiss = { more = false },
        onPause = onPause,
        onFocus = { FocusRequest.open.value = true },
        onOpen = onOpen
    )
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).riseIn(2)) {
        QuickTool(Icons.Rounded.Pause, R.string.today_tool_breathe, primary = true) { haptics.confirm(); onPause() }
        QuickTool(Icons.Rounded.CenterFocusStrong, R.string.today_tool_focus, primary = focusLeft > 0L,
            text = if (focusLeft > 0L) stringResource(R.string.today_tool_focus_left, Wins.short(focusLeft)) else null) {
            haptics.nav(); FocusRequest.open.value = true
        }
        QuickTool(Icons.Rounded.EditNote, R.string.today_tool_note) { haptics.nav(); onNote() }
        QuickTool(Icons.Rounded.GridView, R.string.today_tool_more) { haptics.nav(); more = true }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.QuickTool(
    icon: ImageVector,
    label: Int,
    primary: Boolean = false,
    text: String? = null,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val container = if (primary) Color(0xFFF4C34E) else LocalHisnStatusColors.current.card
    val content = if (primary) Color(0xFF1F2A23) else colors.onSurface
    val ring = when {
        primary -> UnrotInk
        LocalDarkTheme.current -> Color.White.copy(alpha = 0.1f)
        else -> UnrotInk.copy(alpha = 0.16f)
    }
    Column(
        Modifier
            .weight(1f)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Premium: a floating 3D object on a soft card, Breathe on a gold one.
        val art = art3D(icon)
        val toolShape = RoundedCornerShape(22.dp)
        Box(
            Modifier
                .size(64.dp)
                .pressScale(interaction)
                .unrotFrame(toolShape)
                .background(if (primary) Color(0xFFF4C34E) else LocalHisnStatusColors.current.card, toolShape),
            contentAlignment = Alignment.Center
        ) {
            if (art != null) Float3D(art, 46.dp, glow = !primary)
            else Icon(icon, contentDescription = null, tint = if (primary) content else Color(0xFFF4C34E), modifier = Modifier.size(30.dp))
        }
        Text(
            text ?: stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

/**
 * "Feeling the pull?": always reachable on Today, above the tabs, with words not just an icon.
 *
 * Built on a clickable Surface rather than ExtendedFloatingActionButton: on this Material
 * version the extended FAB exposed an empty label to accessibility services, so TalkBack read a
 * nameless button. A Surface merges the icon and the words into one labelled button.
 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun UnrotPauseFab(
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    onNote: (() -> Unit)? = null,
    onLock: (() -> Unit)? = null,
    /** The Glasshouse home: a centred gold pill; while the glass is cracked it says "Breathe to seal it" and glows. */
    pill: Boolean = false,
    sealing: Boolean = false
) {
    val haptics = rememberHisnHaptics()
    val dark = LocalDarkTheme.current
    val interaction = remember { MutableInteractionSource() }
    // A tap breathes at once, the fastest path in a hard moment; holding offers the other choices.
    var choices by remember { androidx.compose.runtime.mutableStateOf(false) }
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "seal").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable<Float>(
            androidx.compose.animation.core.tween(1400), androidx.compose.animation.core.RepeatMode.Reverse), label = "seal-glow")
    Box(modifier.then(if (sealing) Modifier.drawBehind {
        // Two soft rings that breathe, not a slab (mockup .cpbreathe.pulse).
        val glow = pulse.value
        listOf(9.dp to .18f, 19.dp to .08f).forEach { (grow, alpha) ->
            val g = grow.toPx() * (0.7f + 0.3f * glow)
            drawRoundRect(Color(0xFFF2C14E).copy(alpha = alpha * (0.7f + 0.3f * glow)),
                topLeft = androidx.compose.ui.geometry.Offset(-g, -g),
                size = androidx.compose.ui.geometry.Size(size.width + 2 * g, size.height + 2 * g),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2 + g))
        }
    } else Modifier)) {
    androidx.compose.material3.DropdownMenu(expanded = choices, onDismissRequest = { choices = false }) {
        androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.today_tool_breathe)) },
            onClick = { choices = false; onPause() })
        if (onNote != null) androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.today_tool_note)) },
            onClick = { choices = false; onNote() })
        if (onLock != null) androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.today_tool_lock)) },
            onClick = { choices = false; onLock() })
    }
    androidx.compose.material3.Surface(
        modifier = Modifier.pressScale(interaction).combinedClickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onLongClickLabel = stringResource(R.string.fab_more_choices),
            onLongClick = if (onNote != null || onLock != null) ({ haptics.confirm(); choices = true }) else null,
            onClick = { haptics.confirm(); onPause() }
        ),
        shape = if (pill) CircleShape else RoundedCornerShape(20.dp),
        color = if (dark || pill) Color(0xFFF2C14E) else UnrotInk,
        contentColor = if (dark || pill) Color(0xFF1F1A0A) else Color(0xFFF5EFE3),
        shadowElevation = if (pill) 10.dp else 3.dp
    ) {
        // "Breathe", the same word as the tools row, so the two read as one thing.
        // The prototype's one gold button on Today: "Help now" (it opens Breathe).
        val label = stringResource(if (sealing) R.string.home_breathe_seal else R.string.home_help_now)
        Row(
            Modifier
                .heightIn(min = 56.dp)
                .semantics { contentDescription = label }
                .padding(start = if (pill) 18.dp else 12.dp, end = if (expanded) (if (pill) 26.dp else 20.dp) else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .background(if (dark || pill) Color(0xFF1F1A0A) else Color(0xFFF4C34E), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // A breath, not a media pause: a small open ring.
                Box(Modifier.size(12.dp).border(2.5.dp, if (dark || pill) Color(0xFFF2C14E) else UnrotInk, CircleShape))
            }
            // While the list scrolls the button shrinks to its mark, so it never sits on top of
            // the tools; the label stays for screen readers either way.
            androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = if (pill) androidx.compose.ui.text.font.FontWeight.Bold else null)
            }
        }
    }
    }
}

/** The top of Unrot's Settings: who this is for, their note, and the way to their week. */
@Composable
internal fun UnrotYouCard(summary: PersonalSummary, onEdit: () -> Unit, onWeek: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    Column(
        Modifier
            .fillMaxWidth()
            .unrotFrame(shape)
            .background(LocalHisnStatusColors.current.card, shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                summary.name.ifBlank { stringResource(R.string.you_title_default) },
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            if (summary.day > 0) {
                Text(
                    stringResource(R.string.personal_day_number, summary.day),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier
                        .background(colors.primaryContainer, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        Text(
            if (summary.reason.isNotEmpty()) stringResource(R.string.personal_quote, summary.reason)
            else stringResource(R.string.you_no_note),
            style = if (summary.reason.isNotEmpty()) MaterialTheme.typography.titleLarge.storySerif()
                else MaterialTheme.typography.bodyMedium,
            color = if (summary.reason.isNotEmpty()) colors.onSurface else colors.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilledTonalButton(onClick = onEdit) { Text(stringResource(R.string.you_edit)) }
            androidx.compose.material3.TextButton(onClick = onWeek) {
                Text(stringResource(R.string.today_see_week))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}


/** How the home screen is split: Hisn shows it whole; Unrot shows it as Today and Shield. */
enum class HomeMode { Full, Today, Shield }

/** "Friday, 20 November" under Today's title. */
@Composable
internal fun todayLine(): String {
    val locale = androidx.core.os.ConfigurationCompat.getLocales(LocalConfiguration.current)[0]
        ?: java.util.Locale.ENGLISH
    return remember(locale, LocalDate.now()) {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", if (locale.language == "ar") locale else java.util.Locale.ENGLISH))
    }
}

/**
 * Whether the shield is working, for the chip on Today. The full reading, with every reason, is
 * on the Shield tab; this only has to be honest about "on" versus "needs you".
 */
internal fun shieldWorking(state: HisnState): Boolean {
    val runtime = state.runtime
    val dnsBroken = state.dns.on && !runtime.dnsReady
    val appsBroken = state.apps.on && !state.guardReady
    val anything = runtime.dnsReady || state.guardReady
    return anything && !dnsBroken && !appsBroken
}

@Composable
internal fun ShieldChip(state: HisnState, onClick: () -> Unit) {
    val haptics = rememberHisnHaptics()
    val status = LocalHisnStatusColors.current
    val stepsLeft = setupStepsLeft(state)
    val working = shieldWorking(state) && stepsLeft == 0
    Row(
        Modifier
            .padding(end = 12.dp)
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button) { haptics.tap(); onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .background(if (working) status.successContainer else status.warningContainer, CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val b = if (working) breath() else 0f
            Box(
                Modifier.size(8.dp)
                    .graphicsLayer { scaleX = 1f + b * 0.35f; scaleY = 1f + b * 0.35f; alpha = 1f - b * 0.35f }
                    .background(if (working) status.success else status.warning, CircleShape)
            )
            Text(
                when {
                    stepsLeft > 0 -> setupStepsLeftLabel(stepsLeft)
                    working -> stringResource(R.string.today_shield_on)
                    else -> stringResource(R.string.today_shield_needs)
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (working) status.onSuccessContainer else status.onWarningContainer
            )
        }
    }
}

/** On Today only when something is wrong: one sentence, one fix, and the way to the details. */
@Composable
internal fun UnrotNeedsYouCard(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    onDetails: () -> Unit,
    parts: List<Pair<String, Boolean>> = emptyList()
) {
    val status = LocalHisnStatusColors.current
    val shape = MaterialTheme.shapes.extraLarge
    Column(
        Modifier
            .fillMaxWidth()
            .unrotFrame(shape, hero = true)
            .background(status.warningContainer, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = status.onWarningContainer,
            modifier = Modifier.semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodyMedium, color = status.onWarningContainer)
        UnrotPartsList(parts, status.onWarningContainer)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Button(onClick = onAction) { Text(action) }
            androidx.compose.material3.TextButton(onClick = onDetails) {
                Text(stringResource(R.string.today_see_shield), color = status.onWarningContainer)
            }
        }
    }
}

/**
 * Today in three numbers the phone really has: time in feed apps today (Android's own record),
 * pauses taken today, and lookups the website filter blocked since it started.
 */
@Composable
internal fun UnrotTodayNumbers(state: HisnState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val usage = remember { Usage.hasAccess(context) }
    val feeds by androidx.compose.runtime.produceState<Long?>(initialValue = null, usage) {
        if (!usage) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // The same feed apps as the before → now card, so the two numbers always agree.
            val apps = FeedHistories.feedApps(context, state)
            Usage.usedTodayAll(context).filterKeys { it in apps }.values.sum()
        }
    }
    val pauses = remember(state.personal.pauses) { app.hisn.android.PauseCount.today(context) }
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier
            .riseIn(3)
            .fillMaxWidth()
            .unrotFrame(shape)
            .background(LocalHisnStatusColors.current.card, shape)
            .padding(vertical = 14.dp)
    ) {
        // Premium: a ring around today's feed time, filling toward the aim (half the "before").
        val aim = remember { Wins.savedBeforeMs(context).takeIf { it > 0 }?.div(2) }
        val f = feeds
        if (usage && f != null && aim != null) {
            FeedRing(f, aim, Modifier.weight(1f))
        } else TodayCell(
            // Short form ("45 m", "2 h 05") so the big number always fits its third of the card.
            if (usage) feeds?.let { Wins.short(it) } ?: "…" else "—",
            stringResource(R.string.today_num_feeds),
            Modifier.weight(1f)
        )
        TodayCell("${countUp(pauses.toLong())}", stringResource(R.string.progress_pauses), Modifier.weight(1f))
        // Today's count, saved per day: the filter's own counter starts again at 0 whenever the
        // filter restarts, which read as "nothing blocked" after every update.
        val keptOut = remember(state.blockedLookups, state.personal.pauses) { app.hisn.android.StepIns.keptOutToday(context) }
        TodayCell(
            if (state.dns.on || state.tags.on) "${countUp(keptOut.toLong())}" else "—",
            stringResource(R.string.today_num_kept_out),
            Modifier.weight(1f)
        )
    }
}

/** Today's feed time inside a ring that fills toward the aim: gold under it, red past it. */
@Composable
private fun FeedRing(feedMs: Long, aimMs: Long, modifier: Modifier) {
    val share = (feedMs.toFloat() / aimMs).coerceAtLeast(0f)
    val over = share > 1f
    val sweep = grow(share.coerceAtMost(1f), 300)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val ringColor = if (over) Color(0xFFE5553A) else Color(0xFFF2C94F)
    val label = stringResource(R.string.today_ring_of, Wins.short(aimMs))
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.size(64.dp)) {
                val st = 5.dp.toPx()
                drawCircle(track, size.minDimension / 2 - st / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(st))
                drawArc(ringColor, -90f, 360f * sweep, false,
                    androidx.compose.ui.geometry.Offset(st / 2, st / 2),
                    androidx.compose.ui.geometry.Size(size.width - st, size.height - st),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(st, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Text(Wins.short(feedMs), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun TodayCell(value: String, label: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
    }
}

/** The Glasshouse header's "🌱 Day 2": days with Unrot, green like a new leaf. */
@Composable
internal fun DayChip(day: Int) {
    Text(
        "🌱 " + stringResource(R.string.personal_day_number, day),
        style = MaterialTheme.typography.labelLarge,
        color = Color(0xFF9BE07A),
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        modifier = Modifier
            .padding(end = 12.dp)
            .background(Color(0xFF1E3318), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
