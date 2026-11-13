package app.hisn.android.nativeshell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.hisn.android.R
import kotlinx.coroutines.launch

/** What the onboarding can ask of the activity. Every grant is read live, never assumed. */
interface OnboardingActions {
    /** One of [OnboardingKeys]; true when Android reports the grant (or the tunnel) live. */
    fun granted(key: String): Boolean

    /** Opens the exact Android screen or consent for [key]; DNS shows Hisn's disclosure first. */
    fun request(key: String)

    fun guardOn(): Boolean
    fun savePersonal(name: String, preset: String, reason: String)

    /** Leaves onboarding; [enableGuard] turns the uninstall guard on first (direct build). */
    fun finish(enableGuard: Boolean)
}

object OnboardingKeys {
    val NEEDED = listOf("accessibility", "dns")
    val OPTIONAL = listOf("usage", "overlay", "notifications", "battery")
}

private data class Grant(val icon: ImageVector, val title: Int, val body: Int)

private val Grants = mapOf(
    "accessibility" to Grant(Icons.Rounded.Shield, R.string.perm_access_title, R.string.perm_access_body),
    "dns" to Grant(Icons.Rounded.Language, R.string.perm_dns_title, R.string.perm_dns_body),
    "usage" to Grant(Icons.Rounded.Timer, R.string.perm_usage_title, R.string.perm_usage_body),
    "overlay" to Grant(Icons.Rounded.PauseCircle, R.string.perm_overlay_title, R.string.perm_overlay_body),
    "notifications" to Grant(Icons.Rounded.Notifications, R.string.perm_notify_title, R.string.perm_notify_body),
    "battery" to Grant(Icons.Rounded.BatteryChargingFull, R.string.perm_battery_title, R.string.perm_battery_body)
)

/** The story pages: one sentence each, tap anywhere to go on. */
private sealed interface Story
private data class Intro(val title: Int, val body: Int) : Story
private data class How(val text: Int, val frame: Int) : Story

private val Stories: List<Story> = listOf(
    Intro(R.string.welcome_title_1, R.string.welcome_body_1),
    Intro(R.string.welcome_title_2, R.string.welcome_body_2),
    Intro(R.string.welcome_title_3, R.string.welcome_body_3),
    How(R.string.welcome_how_1, 0),
    How(R.string.welcome_how_2, 1),
    How(R.string.welcome_how_3, 2)
)

private val PAGE_YOU = Stories.size
private val PAGE_PERMISSIONS = PAGE_YOU + 1
private val PAGE_GUARD = PAGE_YOU + 2

/**
 * First run, told as a story and then a short conversation: six one-sentence pages (three
 * promises, then "how it works" on a drawn phone), the person's name and reason as chips, the
 * Android grants as switches with one line of why, and on the direct build the uninstall guard.
 * Skipping is always available, and nothing is granted by this screen itself.
 *
 * The pages sit in a pager, so a swipe moves between them the way Android's own setup does; a
 * tap, the buttons, the back arrow and the system back gesture do the same.
 *
 * [refresh] changes whenever the activity resumes or a system result returns, so grants are
 * re-read after the person comes back from Android's settings.
 */
@Composable
fun Onboarding(
    actions: OnboardingActions,
    directBuild: Boolean,
    initialName: String = "",
    initialPreset: String = "",
    initialReason: String = "",
    refresh: Int = 0
) {
    val lastPage = if (directBuild) PAGE_GUARD else PAGE_PERMISSIONS
    val setupSteps = lastPage - PAGE_YOU + 1
    val pager = rememberPagerState(pageCount = { lastPage + 1 })
    val page = pager.currentPage
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var name by rememberSaveable { mutableStateOf(initialName) }
    var preset by rememberSaveable { mutableStateOf(initialPreset) }
    var reason by rememberSaveable { mutableStateOf(initialReason) }
    val haptics = rememberHisnHaptics()
    val reduceMotion = systemReduceMotion(LocalContext.current)

    fun savePersonal() {
            val custom = preset == app.hisn.core.PersonalProfile.CUSTOM
            val words = if (custom) reason.trim() else ""
            // A custom reason with no words is saved as no reason, not as an empty choice.
            actions.savePersonal(name.trim(), if (custom && words.isEmpty()) "" else preset, words)
    }

    fun savePersonalIfShown() {
        if (pager.currentPage == PAGE_YOU) savePersonal()
    }

    fun go(target: Int) {
        scope.launch {
            if (reduceMotion) pager.scrollToPage(target) else pager.animateScrollToPage(target)
        }
    }

    // A swipe leaves the personal page without pressing anything, so it saves here too.
    LaunchedEffect(pager) {
        var last = pager.settledPage
        snapshotFlow { pager.settledPage }.collect { now ->
            if (last == PAGE_YOU && now != PAGE_YOU) savePersonal()
            if (last != now) focus.clearFocus()
            last = now
        }
    }

    fun finish(enableGuard: Boolean) {
        savePersonalIfShown()
        actions.finish(enableGuard)
    }

    fun next() {
        haptics.tick()
        savePersonalIfShown()
        when {
            page < lastPage -> go(page + 1)
            page == PAGE_GUARD -> finish(enableGuard = !actions.guardOn())
            else -> finish(enableGuard = false)
        }
    }

    BackHandler {
        savePersonalIfShown()
        if (page > 0) go(page - 1) else finish(enableGuard = false)
    }

    val granted = remember(refresh, page) {
        (OnboardingKeys.NEEDED + OnboardingKeys.OPTIONAL).associateWith {
            runCatching { actions.granted(it) }.getOrDefault(false)
        }
    }
    val guardOn = remember(refresh, page) { runCatching { actions.guardOn() }.getOrDefault(false) }

    // A deep, calm field: the brand colour glows from the top, the rest stays quiet.
    val colors = MaterialTheme.colorScheme
    // Outside a Surface, text would fall back to black; the whole page reads on the background.
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides colors.onBackground
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .background(
                Brush.radialGradient(
                    listOf(colors.primary.copy(alpha = 0.22f), Color.Transparent),
                    radius = 1400f
                )
            )
            .safeDrawingPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 24.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (page > 0) {
                    IconButton(onClick = {
                        haptics.tick()
                        savePersonalIfShown()
                        go(page - 1)
                    }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.welcome_back)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = when {
                        page >= PAGE_YOU -> stringResource(R.string.welcome_step, page - PAGE_YOU + 1, setupSteps)
                        page >= 3 -> stringResource(R.string.welcome_how)
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
                TextButton(
                    onClick = { finish(enableGuard = false) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.welcome_skip)) }
            }
            StoryProgress(page, lastPage + 1)

            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .weight(1f)
                    .testTag(ONBOARDING_PAGER),
                pageSpacing = 24.dp
            ) { shown ->
                // Only the page in view is read out and tappable; a neighbour kept ready for the
                // swipe would otherwise give TalkBack two "Tap to continue" buttons.
                Box(
                    if (shown == pager.currentPage) Modifier.fillMaxSize()
                    else Modifier.fillMaxSize().clearAndSetSemantics { }
                ) {
                    when {
                        shown < PAGE_YOU -> StoryPage(Stories[shown], onTap = { next() })
                        shown == PAGE_YOU -> ConversationPage {
                            YouPage(
                                name = name,
                                onName = {
                                    name = limitCodePoints(it, app.hisn.core.PersonalProfile.MAX_NAME_CODE_POINTS)
                                },
                                preset = preset,
                                onPreset = { preset = if (preset == it) "" else it },
                                reason = reason,
                                onReason = {
                                    reason = limitCodePoints(it, app.hisn.core.PersonalProfile.MAX_REASON_CODE_POINTS)
                                }
                            )
                        }
                        shown == PAGE_PERMISSIONS -> ConversationPage {
                            PermissionsPage(name.trim(), granted) { actions.request(it) }
                        }
                        else -> ConversationPage { GuardPage(guardOn, granted["accessibility"] == true) }
                    }
                }
            }

            if (page >= PAGE_YOU) {
                Button(
                    onClick = { next() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(
                        stringResource(
                            when {
                                page == PAGE_GUARD && guardOn -> R.string.welcome_lock_finish
                                page == PAGE_GUARD -> R.string.welcome_lock_action
                                else -> R.string.welcome_perm_continue
                            }
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (page == PAGE_GUARD && !guardOn) {
                    TextButton(
                        onClick = { finish(enableGuard = false) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.welcome_lock_skip)) }
                } else {
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
    }
}

/** The pager's tag, so tests can tell it from a row that scrolls sideways inside a page. */
const val ONBOARDING_PAGER = "onboarding-pager"

/** How far through the pages; the step label beside Skip says the same in words. */
@Composable
private fun StoryProgress(page: Int, count: Int) {
    val progress by animateFloatAsState(
        targetValue = (page + 1).toFloat() / count,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "onboarding progress"
    )
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clearAndSetSemantics { },
        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}

// ---------------------------------------------------------------------- story

/** The whole page is the button, as on the screen-time apps people know; the text is its label. */
@Composable
private fun StoryPage(story: Story, onTap: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .clickable(role = Role.Button, onClick = onTap)
            .semantics(mergeDescendants = true) { },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (story) {
            is Intro -> {
                Spacer(Modifier.weight(0.7f))
                Text(
                    stringResource(story.title),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(story.body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.weight(0.3f))
                ShieldGem()
                Spacer(Modifier.weight(0.6f))
                if (story.title == R.string.welcome_title_1) PrivacyLine()
            }
            is How -> {
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(story.text),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(24.dp))
                PhoneFrame(Modifier.weight(1f)) { HowFrame(story.frame) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.welcome_tap_continue),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
    }
}

/** Hisn's mark as something to look at: a shield inside a slowly breathing glow. */
@Composable
private fun ShieldGem() {
    val colors = MaterialTheme.colorScheme
    val reduceMotion = systemReduceMotion(LocalContext.current)
    val breath = rememberInfiniteTransition(label = "gem")
    val glow by breath.animateFloat(
        initialValue = 0.85f,
        targetValue = if (reduceMotion) 0.85f else 1.08f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(200.dp)
                .scale(glow)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(colors.primary.copy(alpha = 0.38f), colors.primary.copy(alpha = 0f))
                    )
                )
        )
        Box(
            Modifier
                .size(108.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(colors.primary, colors.tertiary.copy(alpha = 0.9f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = colors.onPrimary,
                modifier = Modifier.size(56.dp))
        }
    }
}

@Composable
private fun PrivacyLine() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Security, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.welcome_privacy_line),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A drawn phone: decorative, so it is silent to TalkBack; the sentence above says what happens. */
@Composable
private fun PhoneFrame(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .fillMaxSize()
                .aspectRatio(0.5f, matchHeightConstraintsFirst = true)
                .clip(shape)
                .border(2.dp, colors.outlineVariant, shape)
                .background(colors.surfaceContainer)
                .padding(14.dp)
                .clearAndSetSemantics { },
            content = content
        )
    }
}

@Composable
private fun HowFrame(frame: Int) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceContainerHighest)
        )
        Spacer(Modifier.height(14.dp))
        when (frame) {
            0 -> Skeleton(lines = 6)
            1 -> {
                Skeleton(lines = 2, alpha = 0.35f)
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.primaryContainer)
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = colors.primary,
                        modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.welcome_how_blocked),
                        style = MaterialTheme.typography.titleMedium, color = colors.onPrimaryContainer)
                }
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BreathCircle() }
        }
    }
}

@Composable
private fun Skeleton(lines: Int, alpha: Float = 1f) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.alpha(alpha), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(90.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceContainerHigh)
        )
        repeat(lines) { index ->
            Box(
                Modifier
                    .fillMaxWidth(if (index % 3 == 2) 0.6f else 1f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surfaceContainerHigh)
            )
        }
    }
}

@Composable
private fun BreathCircle() {
    val colors = MaterialTheme.colorScheme
    val reduceMotion = systemReduceMotion(LocalContext.current)
    val breath = rememberInfiniteTransition(label = "breath")
    val size by breath.animateFloat(
        initialValue = 0.7f,
        targetValue = if (reduceMotion) 0.7f else 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "size"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(130.dp)
                .scale(size)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.3f))
                .border(2.dp, colors.primary, CircleShape)
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.welcome_how_breathe), style = MaterialTheme.typography.titleMedium)
    }
}

// ---------------------------------------------------------------------- conversation

@Composable
private fun ConversationPage(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(vertical = 20.dp)
    ) { content() }
}

/** A line Hisn says: large and centred, the way a conversation reads. */
@Composable
private fun Said(text: String, dim: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics { heading() }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YouPage(
    name: String,
    onName: (String) -> Unit,
    preset: String,
    onPreset: (String) -> Unit,
    reason: String,
    onReason: (String) -> Unit
) {
    Said(stringResource(R.string.welcome_chat_name))
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        label = { Text(stringResource(R.string.personal_name_hint)) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
            autoCorrectEnabled = false
        ),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(28.dp))
    Said(stringResource(R.string.personal_reason_question))
    Spacer(Modifier.height(12.dp))
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        PersonalPresets.forEach { (key, label) ->
            FilterChip(
                selected = preset == key,
                onClick = { onPreset(key) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.heightIn(min = 48.dp)
            )
        }
    }
    AnimatedVisibility(preset == app.hisn.core.PersonalProfile.CUSTOM) {
        OutlinedTextField(
            value = reason,
            onValueChange = onReason,
            label = { Text(stringResource(R.string.personal_reason_hint)) },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
    Spacer(Modifier.height(20.dp))
    Text(
        stringResource(R.string.welcome_personal_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/** "I can help with that", then each grant as a switch with one line of why. */
@Composable
private fun PermissionsPage(name: String, granted: Map<String, Boolean>, onRequest: (String) -> Unit) {
    Said(
        if (name.isEmpty()) stringResource(R.string.welcome_chat_help)
        else stringResource(R.string.welcome_chat_help_name, name),
        dim = true
    )
    Said(stringResource(R.string.welcome_chat_permission))
    GrantGroup(R.string.welcome_needed_group, OnboardingKeys.NEEDED, granted, onRequest, count = true)
    GrantGroup(R.string.welcome_optional_group, OnboardingKeys.OPTIONAL, granted, onRequest)
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.welcome_perm_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun GrantGroup(
    title: Int,
    keys: List<String>,
    granted: Map<String, Boolean>,
    onRequest: (String) -> Unit,
    count: Boolean = false
) {
    Spacer(Modifier.height(20.dp))
    Row(
        Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        if (count) {
            // Says how much of the blocking is actually on, read from Android, not from taps.
            val on = keys.count { granted[it] == true }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.welcome_needed_progress, on, keys.size),
                style = MaterialTheme.typography.labelLarge,
                color = if (on == keys.size) LocalHisnStatusColors.current.success
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HisnCard {
        keys.forEachIndexed { index, key ->
            GrantRow(Grants.getValue(key), granted[key] == true) { onRequest(key) }
            if (index < keys.lastIndex) InsetDivider()
        }
    }
}

/**
 * The switch shows what Android reports. Flipping it opens Android's own screen; it never
 * pretends a grant happened, and it reads the truth again when the person comes back.
 */
@Composable
private fun GrantRow(grant: Grant, on: Boolean, onRequest: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        headlineContent = { Text(stringResource(grant.title), style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(
                stringResource(grant.body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(grant.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
        },
        trailingContent = { Switch(checked = on, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(value = on, role = Role.Switch, onValueChange = { onRequest() })
    )
}

@Composable
private fun GuardPage(guardOn: Boolean, accessibilityOn: Boolean) {
    val colors = MaterialTheme.colorScheme
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.primary, modifier = Modifier.size(44.dp))
        }
    }
    Spacer(Modifier.height(20.dp))
    Said(stringResource(R.string.welcome_lock_title))
    Text(
        stringResource(R.string.welcome_lock_body),
        style = MaterialTheme.typography.bodyLarge,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(24.dp))
    HisnCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.welcome_lock_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(if (guardOn) R.string.perm_on else R.string.perm_off),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (guardOn) LocalHisnStatusColors.current.success else colors.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.welcome_lock_card_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
            if (!accessibilityOn) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.welcome_lock_requires),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error
                )
            }
        }
    }
}
