package app.hisn.android.nativeshell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.hisn.android.R

/*
 * Unrot's setup checklist. New owners got lost because the steps left after onboarding had five
 * different names across the tabs ("Needs you", "Google may pause", "Permissions", ...) and the
 * fix dropped them into Android settings with no hint of which switch was ours. Here they are one
 * numbered list with a count, and each step has a "Show me" sheet with a drawing of the exact
 * switch (the CleanPath pattern, drawn in our own style). The list ticks itself from live
 * readings when the person comes back, and goes away once everything is on.
 */

internal enum class SetupStepKey { Sites, Watch, Usage, Battery }

internal data class SetupStep(val key: SetupStepKey, val done: Boolean)

/**
 * The steps, from the switches and Android's own readings. "Done" means the person did their
 * part; a running problem after that (the filter crashed, another VPN took the slot) is still
 * reported by the Shield status, not here.
 */
internal fun unrotSetupSteps(state: HisnState): List<SetupStep> = buildList {
    add(SetupStep(SetupStepKey.Sites, state.dns.on))
    // Paused inside the app on purpose is a choice, not a missing step.
    add(SetupStep(SetupStepKey.Watch, state.permissions["accessibility"] == true || state.guardPaused))
    add(SetupStep(SetupStepKey.Usage, state.permissions["usage"] == true))
    // Only phones whose maker we know how to guide; the same reading as the old "may pause" card.
    if (state.phone.maker.isNotEmpty()) add(SetupStep(
        SetupStepKey.Battery,
        state.phone.batteryUnrestricted && state.phone.backgroundRestricted != true
    ))
}

internal fun setupStepsLeft(state: HisnState): Int =
    if (unrotEdition) unrotSetupSteps(state).count { !it.done } else 0

@Composable
private fun stepTitle(key: SetupStepKey, state: HisnState): String = when (key) {
    SetupStepKey.Sites -> stringResource(R.string.setup_step_sites)
    SetupStepKey.Watch -> stringResource(R.string.setup_step_watch)
    SetupStepKey.Usage -> stringResource(R.string.setup_step_usage)
    SetupStepKey.Battery -> stringResource(R.string.setup_step_battery, state.phone.maker)
}

@Composable
private fun stepWhy(key: SetupStepKey): String = stringResource(when (key) {
    SetupStepKey.Sites -> R.string.setup_step_sites_why
    SetupStepKey.Watch -> R.string.setup_step_watch_why
    SetupStepKey.Usage -> R.string.setup_step_usage_why
    SetupStepKey.Battery -> R.string.setup_step_battery_why
})

/** Today's and Shield's card while steps are left: a count, a bar, and the steps in order. */
@Composable
internal fun UnrotSetupCard(state: HisnState, modifier: Modifier = Modifier) {
    val steps = unrotSetupSteps(state)
    val done = steps.count { it.done }
    if (done == steps.size) return
    val haptics = rememberHisnHaptics()
    val status = LocalHisnStatusColors.current
    val shape = MaterialTheme.shapes.extraLarge
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val next = steps.indexOfFirst { !it.done }
    Column(
        modifier
            .fillMaxWidth()
            .unrotFrame(shape, hero = true)
            .background(status.warningContainer, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall,
            color = status.onWarningContainer, modifier = Modifier.semantics { heading() })
        val progress = grow(done.toFloat() / steps.size, 300)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = status.warning,
            trackColor = status.onWarningContainer.copy(alpha = 0.14f),
            drawStopIndicator = {}
        )
        Text(stringResource(R.string.setup_progress, done, steps.size),
            style = MaterialTheme.typography.bodySmall, color = status.onWarningContainer.copy(alpha = 0.8f))
        steps.forEachIndexed { index, step ->
            SetupRow(
                number = index + 1,
                title = stepTitle(step.key, state),
                why = if (index == next) stepWhy(step.key) else null,
                done = step.done,
                isNext = index == next,
                ink = status.onWarningContainer,
                accent = status.warning,
                onClick = { haptics.tap(); open = step.key.name }
            )
        }
    }
    open?.let { name ->
        val key = SetupStepKey.valueOf(name)
        UnrotSetupSheet(
            key = key,
            number = steps.indexOfFirst { it.key == key } + 1,
            total = steps.size,
            state = state,
            onDismiss = { open = null }
        )
    }
}

@Composable
private fun SetupRow(
    number: Int,
    title: String,
    why: String?,
    done: Boolean,
    isNext: Boolean,
    ink: Color,
    accent: Color,
    onClick: () -> Unit
) {
    val doneLabel = stringResource(R.string.setup_done_label)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (done) Modifier.semantics(mergeDescendants = true) { contentDescription = "$title, $doneLabel" }
                else Modifier.clickable(role = Role.Button, onClick = onClick)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(26.dp)
                .then(if (done) Modifier.background(LocalHisnStatusColors.current.success, CircleShape)
                    else Modifier.border(2.dp, accent, CircleShape)),
            contentAlignment = Alignment.Center
        ) {
            if (done) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            else Text("$number", style = MaterialTheme.typography.labelLarge, color = ink)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium),
                color = if (done) ink.copy(alpha = 0.55f) else ink)
            if (why != null) Text(why, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
        }
        when {
            done -> {}
            isNext -> FilledTonalButton(onClick = onClick) { Text(stringResource(R.string.setup_show_me)) }
            else -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = ink.copy(alpha = 0.7f))
        }
    }
}

/** One step, shown: what it is for, a drawing of the switch, three numbered moves, one button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnrotSetupSheet(key: SetupStepKey, number: Int, total: Int, state: HisnState, onDismiss: () -> Unit) {
    val haptics = rememberHisnHaptics()
    var why by rememberSaveable { mutableStateOf(false) }
    val app = stringResource(R.string.app_name)
    val moves = when (key) {
        SetupStepKey.Sites -> listOf(R.string.setup_sites_move1, R.string.setup_sites_move2, R.string.setup_sites_move3)
        SetupStepKey.Watch -> listOf(R.string.setup_watch_move1, R.string.setup_watch_move2, R.string.setup_watch_move3)
        SetupStepKey.Usage -> listOf(R.string.setup_usage_move1, R.string.setup_usage_move2, R.string.setup_usage_move3)
        SetupStepKey.Battery -> listOf(R.string.setup_battery_move1, R.string.setup_battery_move2, R.string.setup_battery_move3)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.setup_step_of, number, total).uppercase(appLocale()),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(stepTitle(key, state), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(stepWhy(key), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingsPicture(key, app)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                moves.forEachIndexed { i, move ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${i + 1}.", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.width(20.dp))
                        Text(stringResource(move), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Button(
                onClick = {
                    haptics.confirm()
                    onDismiss()
                    when (key) {
                        SetupStepKey.Sites -> state.fixDns()
                        SetupStepKey.Watch -> state.openPermission("accessibility")
                        SetupStepKey.Usage -> state.openPermission("usage")
                        SetupStepKey.Battery -> state.openPermission("battery")
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text(stringResource(if (key == SetupStepKey.Sites) R.string.setup_turn_on else R.string.setup_open_settings))
            }
            TextButton(onClick = { why = !why }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.setup_why_need))
            }
            AnimatedVisibility(visible = why) {
                Text(stringResource(when (key) {
                    SetupStepKey.Sites -> R.string.setup_sites_why_long
                    SetupStepKey.Watch -> R.string.setup_watch_why_long
                    SetupStepKey.Usage -> R.string.setup_usage_why_long
                    SetupStepKey.Battery -> R.string.setup_battery_why_long
                }, state.phone.maker.ifEmpty { "Android" }), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * A drawing of the Android screen the person is about to see, with our row ringed in green.
 * Drawn, not a screenshot: makers word and lay out these screens differently, so the drawing
 * keeps only what is the same everywhere (the path, the row with our name, the switch).
 */
@Composable
private fun SettingsPicture(key: SetupStepKey, app: String) {
    val paper = Color(0xFFF7F4EE)
    val ink = Color(0xFF1F1B16)
    val faint = Color(0xFFD9D3C7)
    val ring = Color(0xFF2F9E57)
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .background(paper, shape)
            .border(1.dp, faint, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(when (key) {
            SetupStepKey.Sites -> R.string.setup_pic_sites_path
            SetupStepKey.Watch -> R.string.setup_pic_watch_path
            SetupStepKey.Usage -> R.string.setup_pic_usage_path
            SetupStepKey.Battery -> R.string.setup_pic_battery_path
        }), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.6f))
        @Composable
        fun bar() = Box(Modifier.fillMaxWidth(0.55f).height(10.dp).background(faint, CircleShape))
        @Composable
        fun ringed(content: @Composable () -> Unit) = Box(
            Modifier
                .fillMaxWidth()
                .border(3.dp, ring, RoundedCornerShape(12.dp))
                .background(ring.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) { content() }
        @Composable
        fun toggle(on: Boolean) = Box(
            Modifier.size(width = 36.dp, height = 20.dp)
                .background(if (on) ring else Color(0xFFB9B3A8), CircleShape)
                .padding(3.dp),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
        ) { Box(Modifier.size(14.dp).background(Color.White, CircleShape)) }

        when (key) {
            SetupStepKey.Sites -> {
                // Android's own "Connection request" dialog, OK ringed.
                Column(
                    Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.setup_pic_vpn_title), style = MaterialTheme.typography.titleSmall, color = ink)
                    Text(stringResource(R.string.setup_pic_vpn_body, app), style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.7f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.setup_pic_cancel), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 12.dp))
                        Box(Modifier.border(3.dp, ring, CircleShape).padding(horizontal = 14.dp, vertical = 6.dp)) {
                            Text(stringResource(R.string.setup_pic_ok), style = MaterialTheme.typography.labelLarge, color = ring)
                        }
                    }
                }
            }
            SetupStepKey.Watch, SetupStepKey.Usage -> {
                bar()
                ringed {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(22.dp).background(Color(0xFFF2C14E), RoundedCornerShape(6.dp)))
                        Text(app, style = MaterialTheme.typography.titleSmall, color = ink,
                            modifier = Modifier.padding(start = 10.dp).weight(1f))
                        toggle(false)
                    }
                }
                bar()
            }
            SetupStepKey.Battery -> {
                listOf(R.string.setup_pic_unrestricted to true, R.string.setup_pic_optimised to false, R.string.setup_pic_restricted to false)
                    .forEach { (label, pick) ->
                        val row: @Composable () -> Unit = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(18.dp).border(2.dp, if (pick) ring else Color(0xFFB9B3A8), CircleShape),
                                    contentAlignment = Alignment.Center) {
                                    if (pick) Box(Modifier.size(8.dp).background(ring, CircleShape))
                                }
                                Text(stringResource(label), style = MaterialTheme.typography.titleSmall,
                                    color = if (pick) ink else ink.copy(alpha = 0.55f), modifier = Modifier.padding(start = 10.dp))
                            }
                        }
                        if (pick) ringed(row) else Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) { row() }
                    }
            }
        }
    }
}

/**
 * The top of Today, in one plain sentence: what the app does while setup is unfinished, then
 * what is running right now. Only real readings go in the second; nothing is claimed.
 */
@Composable
internal fun UnrotNowCard(setupLeft: Int, running: List<String>) {
    if (setupLeft == 0 && running.isEmpty()) return
    val shape = MaterialTheme.shapes.large
    val status = LocalHisnStatusColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .unrotFrame(shape)
            .background(status.card, shape)
            .semantics(mergeDescendants = true) {}
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(stringResource(if (setupLeft > 0) R.string.now_what_title else R.string.now_running_title).uppercase(appLocale()),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (setupLeft > 0) {
            Text(stringResource(R.string.now_what_body), style = MaterialTheme.typography.bodyLarge)
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).background(status.success, CircleShape))
            Text(running.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** After setup: one suggested thing to do next, so Today always has a clear next tap. */
@Composable
internal fun UnrotNextCard(state: HisnState, onOpen: (String) -> Unit) {
    val haptics = rememberHisnHaptics()
    val (title, body, action, go) = when {
        !state.appSchedule.enabled -> listOf(R.string.next_quiet_title, R.string.next_quiet_body, R.string.next_quiet_action, 0)
        !(state.limit.on && state.appLimits.isNotEmpty()) -> listOf(R.string.next_limit_title, R.string.next_limit_body, R.string.next_limit_action, 1)
        else -> listOf(R.string.next_focus_title, R.string.next_focus_body, R.string.next_focus_action, 2)
    }
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier
            .fillMaxWidth()
            .unrotFrame(shape)
            .background(LocalHisnStatusColors.current.card, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.next_label).uppercase(appLocale()),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = {
            haptics.tap()
            when (go) {
                0 -> onOpen("schedule")
                1 -> onOpen("limit")
                else -> FocusRequest.open.value = true
            }
        }) { Text(stringResource(action)) }
    }
}

@Composable
internal fun setupStepsLeftLabel(n: Int): String = pluralStringResource(R.plurals.setup_steps_left, n, n)
