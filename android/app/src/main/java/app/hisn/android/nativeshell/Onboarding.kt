package app.hisn.android.nativeshell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.NightsStay
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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

    /** "tags" or "images": whether the saved rules have that check on. */
    fun checkOn(key: String): Boolean = false

    /** Turns a check on after the same disclosure the shell shows, or off at once. */
    fun setCheck(key: String, on: Boolean) {}

    /** The apps the text and image checks look inside, as saved. */
    fun checkedApps(): Set<String> = emptySet()

    /** Saves the apps the checks look inside. */
    fun setCheckedApps(packages: Set<String>) {}
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
private data class Intro(
    val title: Int,
    val body: Int,
    val icon: ImageVector = Icons.Rounded.Shield,
    /** A small line under the body, for saying how far a claim goes. */
    val note: Int? = null
) : Story
private data class How(val text: Int, val frame: Int) : Story
private data object Layers : Story

/**
 * What Hisn is for, one problem per page with its answer under it. Unchaind and Unrot make this
 * case with shame slides and invented numbers; these three say only what is plainly true.
 */
private data class Problem(val title: Int, val body: Int, val answer: Int, val icon: ImageVector) : Story

/** The four layers as Hisn's Home tab names them, each with what it actually does. */
private val LayerTiles = listOf(
    Triple(Icons.Rounded.Wifi, R.string.welcome_layer_dns, R.string.welcome_layer_dns_body),
    Triple(Icons.Rounded.Apps, R.string.welcome_layer_apps, R.string.welcome_layer_apps_body),
    Triple(Icons.Rounded.LocalOffer, R.string.welcome_layer_tags, R.string.welcome_layer_tags_body),
    Triple(Icons.Rounded.Image, R.string.welcome_layer_images, R.string.welcome_layer_images_body)
)

private val Stories: List<Story> = listOf(
    Intro(R.string.welcome_title_1, R.string.welcome_body_1, Icons.Outlined.Shield),
    // Why it is hard: what feeds and adult content do, in terms the research supports. Unchaind's
    // slides here say "pornography is a drug" and "it rewires your body"; these say what is
    // established (novelty and reward, habit loops), mark the rest as links, and end on change.
    Intro(R.string.welcome_brain_1, R.string.welcome_brain_1_body, Icons.Outlined.Bolt),
    Intro(R.string.welcome_brain_2, R.string.welcome_brain_2_body, Icons.Outlined.Loop),
    Intro(R.string.welcome_brain_3, R.string.welcome_brain_3_body, Icons.Outlined.Bedtime, R.string.welcome_brain_3_note),
    Intro(R.string.welcome_brain_4, R.string.welcome_brain_4_body, Icons.Outlined.Spa),
    Problem(R.string.welcome_problem_1, R.string.welcome_problem_1_body, R.string.welcome_answer_1, Icons.Outlined.Shield),
    Problem(R.string.welcome_problem_2, R.string.welcome_problem_2_body, R.string.welcome_answer_2, Icons.Outlined.PauseCircle),
    Problem(R.string.welcome_problem_3, R.string.welcome_problem_3_body, R.string.welcome_answer_3, Icons.Outlined.Lock),
    How(R.string.welcome_how_1, 0),
    How(R.string.welcome_how_2, 1),
    How(R.string.welcome_how_3, 2),
    // Where Unchaind shows shame slides, Hisn shows what each of its parts does.
    Layers,
    // Hisn counts days with it and pauses taken, never clean days, so this page is simply true.
    Intro(R.string.welcome_title_4, R.string.welcome_body_4, Icons.Outlined.FavoriteBorder)
)

/** Setup is one question per page, as on the quiz-style onboardings people know. */
private val PAGE_NAME = Stories.size
private val PAGE_REASON = PAGE_NAME + 1
private val PAGE_START = PAGE_NAME + 2
private val PAGE_HARD = PAGE_NAME + 3
private val PAGE_BEFORE = PAGE_NAME + 4
private val PAGE_PLEDGE = PAGE_NAME + 5
private val PLEDGES = listOf(
    R.string.welcome_pledge_text,
    R.string.welcome_pledge_text_2,
    R.string.welcome_pledge_text_3
)
private val PAGE_PERMISSIONS = PAGE_PLEDGE + PLEDGES.size
private val PAGE_CHECKS = PAGE_PERMISSIONS + 1
private val PAGE_READY = PAGE_PERMISSIONS + 2
private val PAGE_GUARD = PAGE_PERMISSIONS + 3
private val PLEDGE_PAGES = PAGE_PLEDGE until PAGE_PERMISSIONS
private val PERSONAL_PAGES = setOf(PAGE_NAME, PAGE_REASON)

/** Where the pull usually starts. It only orders the next page; it is not saved anywhere. */
private val StartAnswers = listOf(
    "browser" to R.string.welcome_start_browser,
    "social" to R.string.welcome_start_social,
    "videos" to R.string.welcome_start_videos,
    "unsure" to R.string.welcome_start_unsure
)

/**
 * What got in the way before. Unchaind and Unrot both name it ("app blockers: you delete them");
 * Hisn answers each with the part built for it, on the setup summary.
 */
private val BeforeAnswers = listOf(
    "first" to R.string.welcome_before_first,
    "switched" to R.string.welcome_before_switched,
    "deleted" to R.string.welcome_before_deleted,
    "private" to R.string.welcome_before_private,
    "late" to R.string.welcome_before_late
)

/** When it is hardest. Only the setup summary uses it, to point at the rule that fits. */
private val HardAnswers = listOf(
    "morning" to R.string.welcome_hard_morning,
    "day" to R.string.welcome_hard_day,
    "evening" to R.string.welcome_hard_evening,
    "night" to R.string.welcome_hard_night,
    "varies" to R.string.welcome_hard_varies
)

/**
 * First run, told as a story and then a short conversation: six one-sentence pages (three
 * promises, then "how it works" on a drawn phone), then one question per page (name, reason,
 * where the pull starts), a held promise in the person's own words, the Android grants as
 * switches with one line of why, and on the direct build the uninstall guard. Answers move on
 * when tapped. Skipping is always available, and nothing is granted by this screen itself.
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
    val lastPage = if (directBuild) PAGE_GUARD else PAGE_READY
    val setupSteps = lastPage - PAGE_NAME + 1
    val pager = rememberPagerState(pageCount = { lastPage + 1 })
    val page = pager.currentPage
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var name by rememberSaveable { mutableStateOf(initialName) }
    var preset by rememberSaveable { mutableStateOf(initialPreset) }
    var reason by rememberSaveable { mutableStateOf(initialReason) }
    var start by rememberSaveable { mutableStateOf("") }
    var hard by rememberSaveable { mutableStateOf("") }
    var before by rememberSaveable { mutableStateOf("") }
    val haptics = rememberHisnHaptics()
    val reduceMotion = systemReduceMotion(LocalContext.current)

    fun savePersonal() {
        val custom = preset == app.hisn.core.PersonalProfile.CUSTOM
        val words = if (custom) reason.trim() else ""
        // A custom reason with no words is saved as no reason, not as an empty choice.
        actions.savePersonal(name.trim(), if (custom && words.isEmpty()) "" else preset, words)
    }

    fun savePersonalIfShown() {
        if (pager.currentPage in PERSONAL_PAGES) savePersonal()
    }

    fun go(target: Int) {
        scope.launch {
            if (reduceMotion) pager.scrollToPage(target) else pager.animateScrollToPage(target)
        }
    }

    // A swipe leaves a personal page without pressing anything, so it saves here too.
    LaunchedEffect(pager) {
        var last = pager.settledPage
        snapshotFlow { pager.settledPage }.collect { now ->
            if (last in PERSONAL_PAGES && now != last) savePersonal()
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
        val current = pager.currentPage
        when {
            current < lastPage -> go(current + 1)
            current == PAGE_GUARD -> finish(enableGuard = !actions.guardOn())
            else -> finish(enableGuard = false)
        }
    }

    // A tapped answer is the answer: the card shows as chosen while the next question slides
    // in. A second tap while the page moves, or on a page already left, does not skip a page.
    fun answered(onPage: Int) {
        if (pager.currentPage == onPage && !pager.isScrollInProgress) next()
    }

    BackHandler {
        savePersonalIfShown()
        if (page > 0) go(page - 1) else finish(enableGuard = false)
    }

    val reasonText = when (preset) {
        "" -> ""
        app.hisn.core.PersonalProfile.CUSTOM -> reason.trim()
        else -> PersonalPresets.firstOrNull { it.first == preset }?.let { stringResource(it.second) }.orEmpty()
    }

    val granted = remember(refresh, page) {
        (OnboardingKeys.NEEDED + OnboardingKeys.OPTIONAL).associateWith {
            runCatching { actions.granted(it) }.getOrDefault(false)
        }
    }
    val guardOn = remember(refresh, page) { runCatching { actions.guardOn() }.getOrDefault(false) }
    val checks = remember(refresh, page) {
        listOf("tags", "images").associateWith { runCatching { actions.checkOn(it) }.getOrDefault(false) }
    }
    // Held here so a tick shows at once; the saved set is read again after each refresh.
    var checkedApps by remember(refresh, page) {
        mutableStateOf(runCatching { actions.checkedApps() }.getOrDefault(emptySet()))
    }

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
                        page >= PAGE_NAME -> stringResource(R.string.welcome_step, page - PAGE_NAME + 1, setupSteps)
                        // From the first "How it works" frame on, wherever that page sits.
                        page >= Stories.indexOfFirst { it is How } -> stringResource(R.string.welcome_how)
                        // The pages between the welcome and the first problem explain the pull.
                        page in 1 until Stories.indexOfFirst { it is Problem } -> stringResource(R.string.welcome_why)
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
                        shown < PAGE_NAME -> StoryPage(Stories[shown], onTap = { next() })
                        shown == PAGE_NAME -> ConversationPage {
                            NamePage(name = name, onDone = { next() }) {
                                name = limitCodePoints(it, app.hisn.core.PersonalProfile.MAX_NAME_CODE_POINTS)
                            }
                        }
                        shown == PAGE_REASON -> ConversationPage {
                            ReasonPage(
                                preset = preset,
                                onPreset = {
                                    preset = it
                                    if (it != app.hisn.core.PersonalProfile.CUSTOM) answered(PAGE_REASON)
                                },
                                reason = reason,
                                onReason = {
                                    reason = limitCodePoints(it, app.hisn.core.PersonalProfile.MAX_REASON_CODE_POINTS)
                                }
                            )
                        }
                        shown == PAGE_START -> ConversationPage {
                            QuestionPage(R.string.welcome_start_question, StartAnswers, start) {
                                start = it
                                answered(PAGE_START)
                            }
                        }
                        shown == PAGE_HARD -> ConversationPage {
                            QuestionPage(R.string.welcome_hard_question, HardAnswers, hard) {
                                hard = it
                                answered(PAGE_HARD)
                            }
                        }
                        shown == PAGE_BEFORE -> ConversationPage {
                            QuestionPage(R.string.welcome_before_question, BeforeAnswers, before) {
                                before = it
                                answered(PAGE_BEFORE)
                            }
                        }
                        shown in PLEDGE_PAGES -> ConversationPage {
                            PledgePage(PLEDGES[shown - PAGE_PLEDGE], shown - PAGE_PLEDGE + 1, PLEDGES.size, reasonText, name.trim())
                        }
                        shown == PAGE_PERMISSIONS -> ConversationPage {
                            PermissionsPage(name.trim(), start, granted) { actions.request(it) }
                        }
                        shown == PAGE_CHECKS -> ConversationPage {
                            ChecksPage(
                                checks = checks,
                                checked = checkedApps,
                                appProtectionOn = granted["accessibility"] == true,
                                onCheck = { key, on -> actions.setCheck(key, on) },
                                onApps = {
                                    actions.setCheckedApps(it)
                                    checkedApps = runCatching { actions.checkedApps() }.getOrDefault(it)
                                }
                            )
                        }
                        shown == PAGE_READY -> ConversationPage {
                            ReadyPage(name.trim(), reasonText, hard, before, directBuild, granted, checks, checkedApps.size)
                        }
                        else -> ConversationPage { GuardPage(guardOn, granted["accessibility"] == true) }
                    }
                }
            }

            if (page in PLEDGE_PAGES) {
                // One button per promise: a fresh fill, not the last promise's finished one.
                key(page) {
                    HoldToPromise(
                        label = stringResource(R.string.welcome_pledge_hold),
                        onDone = { answered(page) }
                    )
                }
                TextButton(
                    // "Not now" puts off every remaining promise, not just this one.
                    onClick = {
                        haptics.tick()
                        go(PAGE_PERMISSIONS)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.welcome_pledge_later)) }
            } else if (page >= PAGE_NAME) {
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
                                page == lastPage -> R.string.welcome_ready_finish
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
                    style = MaterialTheme.typography.headlineLarge.formal(),
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
                if (story.note != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(story.note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.weight(0.3f))
                ShieldGem(story.icon)
                Spacer(Modifier.weight(0.6f))
                if (story.title == R.string.welcome_title_1) PrivacyLine()
            }
            is Layers -> {
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.welcome_layers_title),
                    style = MaterialTheme.typography.headlineSmall.formal(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.welcome_layers_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                // Tiles as tall as their words, the pair in a row matched, the grid centred.
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                ) {
                    LayerTiles.chunked(2).forEach { row ->
                        Row(
                            Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { (icon, title, body) ->
                                LayerTile(icon, title, body, Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
            }
            is Problem -> {
                val colors = MaterialTheme.colorScheme
                Spacer(Modifier.weight(0.5f))
                Text(
                    stringResource(R.string.welcome_problem_label).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(story.title),
                    style = MaterialTheme.typography.headlineMedium.formal(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(story.body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(28.dp))
                androidx.compose.material3.HorizontalDivider(
                    Modifier.width(40.dp), thickness = 1.5.dp, color = colors.outlineVariant
                )
                Spacer(Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(story.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.welcome_answer_label).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(story.answer),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.weight(0.7f))
            }
            is How -> {
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(story.text),
                    style = MaterialTheme.typography.headlineSmall.formal(),
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

/** One layer: its mark, its name and one line on what it does. TalkBack reads all three. */
@Composable
private fun LayerTile(icon: ImageVector, title: Int, body: Int, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerHigh)
            .padding(16.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
    }
}

/**
 * Onboarding headings are set in the system serif, as Unchaind's are: formal, and it needs no
 * bundled font. Arabic falls to the system's Naskh face. Body text stays in the app's sans.
 */
private fun androidx.compose.ui.text.TextStyle.formal() = copy(
    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
)

/** The page's mark, kept formal: one outlined icon in a thin ring. No glow, gradient or motion. */
@Composable
private fun ShieldGem(icon: ImageVector) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(124.dp)
            .border(1.5.dp, colors.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(48.dp))
    }
}

/** What you get, on the first page: two things Hisn does and the privacy promise. */
@Composable
private fun PrivacyLine() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BenefitRow(Icons.Outlined.Shield, R.string.welcome_benefit_1)
        BenefitRow(Icons.Outlined.PauseCircle, R.string.welcome_benefit_2)
        BenefitRow(Icons.Rounded.Security, R.string.welcome_privacy_line)
    }
}

@Composable
private fun BenefitRow(icon: ImageVector, text: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(text),
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
        style = MaterialTheme.typography.titleLarge.formal(),
        textAlign = TextAlign.Center,
        color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics { heading() }
    )
}

/** The promise Unchaind and Unrot ask for, kept honest: whatever the answers were, no data leaves. */
@Composable
private fun AnswersStayHere() {
    Spacer(Modifier.height(20.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.welcome_answers_private),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NamePage(name: String, onDone: () -> Unit, onName: (String) -> Unit) {
    Spacer(Modifier.height(24.dp))
    Said(stringResource(R.string.welcome_chat_name))
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        label = { Text(stringResource(R.string.personal_name_hint)) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
            autoCorrectEnabled = false,
            imeAction = androidx.compose.ui.text.input.ImeAction.Next
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onNext = { onDone() }),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.welcome_personal_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    AnswersStayHere()
}

@Composable
private fun ReasonPage(
    preset: String,
    onPreset: (String) -> Unit,
    reason: String,
    onReason: (String) -> Unit
) {
    Spacer(Modifier.height(24.dp))
    Said(stringResource(R.string.personal_reason_question))
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PersonalPresets.forEachIndexed { index, (key, label) ->
            AnswerCard(index + 1, stringResource(label), preset == key) { onPreset(key) }
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
                .padding(top = 12.dp)
        )
    }
    AnswersStayHere()
}

/**
 * What the checks look at. Opal pre-picks your block list and BlockerX opens on its filter
 * switch; Hisn lets the person turn the checks on here, behind the shell's own disclosure, and
 * pick the apps, with every browser one tap away. Nothing is ticked for them.
 */
@Composable
private fun ChecksPage(
    checks: Map<String, Boolean>,
    checked: Set<String>,
    appProtectionOn: Boolean,
    onCheck: (String, Boolean) -> Unit,
    onApps: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var showAll by rememberSaveable { mutableStateOf(false) }
    // "Show all apps" also brings the apps that have no home-screen icon.
    val apps by androidx.compose.runtime.produceState(initialValue = emptyList<InstalledApp>(), showAll) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { installedApps(context, showAll) }
    }
    val browsers = remember(apps) { browserPackages(context) }
    val usage = remember(apps) {
        if (Usage.hasAccess(context)) Usage.usedTodayAll(context) else emptyMap()
    }
    val ordered = remember(apps, browsers, usage) {
        apps.sortedWith(
            compareByDescending<InstalledApp> { it.packageName in browsers }
                .thenByDescending { usage[it.packageName] ?: 0L }
                .thenBy { it.label.lowercase() }
        )
    }

    Spacer(Modifier.height(16.dp))
    Said(stringResource(R.string.welcome_checks_title))
    Text(
        stringResource(R.string.welcome_checks_body),
        style = MaterialTheme.typography.bodyLarge,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    if (!appProtectionOn) {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.welcome_checks_needs_access),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(Modifier.height(16.dp))
    HisnCard {
        CheckRow(Icons.Rounded.LocalOffer, R.string.welcome_check_tags, R.string.adult_mode_short,
            checks["tags"] == true, enabled = true) { onCheck("tags", it) }
        InsetDivider()
        val imagesSupported = android.os.Build.VERSION.SDK_INT >= 34
        CheckRow(Icons.Rounded.Image, R.string.welcome_check_images,
            if (imagesSupported) R.string.visual_short else R.string.welcome_check_images_old,
            checks["images"] == true, enabled = imagesSupported) { onCheck("images", it) }
    }

    Spacer(Modifier.height(20.dp))
    Row(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.welcome_apps_title),
            style = MaterialTheme.typography.titleSmall,
            color = colors.primary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        Text(
            stringResource(R.string.welcome_apps_count, checked.size),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant
        )
    }
    val installedBrowsers = ordered.filter { it.packageName in browsers }.map { it.packageName }.toSet()
    if (installedBrowsers.isNotEmpty() && !checked.containsAll(installedBrowsers)) {
        androidx.compose.material3.FilledTonalButton(
            onClick = { onApps(checked + installedBrowsers) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) { Text(stringResource(R.string.welcome_apps_browsers, installedBrowsers.size)) }
        Spacer(Modifier.height(10.dp))
    }
    HisnCard {
        val shown = if (showAll) ordered else ordered.take(8)
        shown.forEachIndexed { index, app ->
            val on = app.packageName in checked
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = {
                        onApps(if (it) checked + app.packageName else checked - app.packageName)
                    })
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(app.packageName, 32.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (app.packageName in browsers) {
                        Text(stringResource(R.string.welcome_apps_browser_tag),
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                androidx.compose.material3.Checkbox(checked = on, onCheckedChange = null)
            }
            if (index < shown.lastIndex) InsetDivider()
        }
        if (ordered.size > 8) {
            TextButton(
                onClick = { showAll = !showAll },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) { Text(stringResource(if (showAll) R.string.welcome_apps_fewer else R.string.welcome_apps_all)) }
        }
    }
}

/** One check: its mark, its name, the short disclosure, and a switch that asks before it saves. */
@Composable
private fun CheckRow(icon: ImageVector, title: Int, body: Int, on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        headlineContent = { Text(stringResource(title), style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        },
        leadingContent = { Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp)) },
        trailingContent = { Switch(checked = on, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(value = on, enabled = enabled, role = Role.Switch, onValueChange = onChange)
    )
}

/** Packages that open web links: the browsers Website filtering already covers at the network. */
private fun browserPackages(context: android.content.Context): Set<String> = runCatching {
    val view = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))
        .addCategory(android.content.Intent.CATEGORY_BROWSABLE)
    context.packageManager.queryIntentActivities(view, android.content.pm.PackageManager.MATCH_ALL)
        .mapNotNull { it.activityInfo?.packageName }
        .toSet()
}.getOrDefault(emptySet())

/** One question, its answers as numbered cards; picking a card is the answer. */
@Composable
private fun QuestionPage(
    question: Int,
    answers: List<Pair<String, Int>>,
    chosen: String,
    onChoose: (String) -> Unit
) {
    Spacer(Modifier.height(24.dp))
    Said(stringResource(question))
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        answers.forEachIndexed { index, (key, label) ->
            AnswerCard(index + 1, stringResource(label), chosen == key) { onChoose(key) }
        }
    }
    AnswersStayHere()
}

/**
 * Unrot and Unchaind end with a "personalised plan" and a date you will be free by. Hisn ends
 * with what is true: the reason in the person's words, which layers Android reports running
 * right now, and the one rule that fits the hour they said is hardest.
 */
@Composable
private fun ReadyPage(
    name: String,
    reason: String,
    hard: String,
    before: String,
    directBuild: Boolean,
    granted: Map<String, Boolean>,
    checks: Map<String, Boolean> = emptyMap(),
    checkedCount: Int = 0
) {
    val colors = MaterialTheme.colorScheme
    val status = LocalHisnStatusColors.current
    Spacer(Modifier.height(16.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(status.successContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = status.success, modifier = Modifier.size(40.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
    Said(
        if (name.isEmpty()) stringResource(R.string.welcome_ready_title)
        else stringResource(R.string.welcome_ready_title_name, name)
    )
    if (reason.isNotEmpty()) {
        Text(
            stringResource(R.string.personal_quote, reason),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(Modifier.height(20.dp))
    Text(
        stringResource(R.string.welcome_ready_running),
        style = MaterialTheme.typography.titleSmall,
        color = colors.primary,
        modifier = Modifier
            .padding(start = 4.dp, bottom = 8.dp)
            .semantics { heading() }
    )
    HisnCard {
        OnboardingKeys.NEEDED.forEachIndexed { index, key ->
            val on = granted[key] == true
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .semantics(mergeDescendants = true) { },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Grants.getValue(key).icon,
                    contentDescription = null,
                    tint = if (on) status.success else colors.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    stringResource(Grants.getValue(key).title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(if (on) R.string.perm_on else R.string.perm_off),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) status.success else colors.onSurfaceVariant
                )
            }
            InsetDivider()
        }
        // The checks inside apps, with how many apps they look at.
        listOf(
            Triple("tags", Icons.Rounded.LocalOffer, R.string.welcome_check_tags),
            Triple("images", Icons.Rounded.Image, R.string.welcome_check_images)
        ).forEachIndexed { index, (key, icon, title) ->
            val saved = checks[key] == true && checkedCount > 0
            // Running only once App protection is on; until then the choice is saved, not running.
            val on = saved && granted["accessibility"] == true
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .semantics(mergeDescendants = true) { },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null,
                    tint = if (on) status.success else colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    when {
                        on -> stringResource(R.string.welcome_check_on_apps, checkedCount)
                        saved -> stringResource(R.string.welcome_check_saved)
                        else -> stringResource(R.string.perm_off)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) status.success else colors.onSurfaceVariant
                )
            }
            if (index == 0) InsetDivider()
        }
    }
    if (OnboardingKeys.NEEDED.none { granted[it] == true }) {
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.welcome_ready_none),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    // One tip per answer, never the same tip twice. The Play build has no uninstall guard, so it
    // does not offer one.
    val tips = buildList {
        when (hard) {
            "" -> Unit
            "night" -> add(Icons.Rounded.NightsStay to R.string.welcome_ready_tip_night)
            else -> add(Icons.Rounded.Timer to R.string.welcome_ready_tip_limit)
        }
        when (before) {
            "switched" -> add(Icons.Rounded.Lock to R.string.welcome_ready_tip_lock)
            "deleted" -> if (directBuild) add(Icons.Rounded.Shield to R.string.welcome_ready_tip_guard)
            "private" -> add(Icons.Rounded.Language to R.string.welcome_ready_tip_private)
            "late" -> add(Icons.Rounded.NightsStay to R.string.welcome_ready_tip_night)
        }
    }.distinctBy { it.second }
    tips.forEach { (icon, tip) ->
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.primaryContainer)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(tip),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onPrimaryContainer
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.welcome_ready_next),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * One answer as a numbered card. It is a radio button to a screen reader; picking it is the
 * answer, so there is nothing else to press on most question pages.
 */
@Composable
private fun AnswerCard(number: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        label = "answer container"
    )
    val outline by animateColorAsState(
        if (selected) colors.primary else colors.outlineVariant.copy(alpha = 0.6f),
        label = "answer outline"
    )
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(container)
            .border(if (selected) 2.dp else 1.dp, outline, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (selected) colors.primary else colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface
        )
    }
}

/**
 * The person's reason, held as a promise. Unchaind and Unrot make this moment physical with a
 * long press; so does Hisn, but it says plainly that nothing is locked by it.
 */
@Composable
private fun PledgePage(text: Int, number: Int, count: Int, reason: String, name: String = "") {
    val colors = MaterialTheme.colorScheme
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.welcome_pledge_count, number, count),
        style = MaterialTheme.typography.labelLarge,
        color = colors.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Said(
        // Only the first promise greets by name; three in a row would be too much.
        if (name.isNotEmpty() && number == 1) stringResource(R.string.welcome_pledge_title_name, name)
        else stringResource(R.string.welcome_pledge_title)
    )
    Spacer(Modifier.height(20.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.primaryContainer)
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.Shield, contentDescription = null, tint = colors.primary, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(text),
            style = MaterialTheme.typography.headlineSmall.formal(),
            color = colors.onPrimaryContainer,
            textAlign = TextAlign.Center
        )
        if (reason.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.personal_quote, reason),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.welcome_pledge_note),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Press and hold to promise: the fill grows while the finger stays down and drains if it lifts
 * early. A screen reader gets one ordinary double-tap action with the same label.
 */
@Composable
private fun HoldToPromise(label: String, onDone: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHisnHaptics()
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }
    val doneLabel = stringResource(R.string.welcome_pledge_done)

    fun complete() {
        if (done) return
        done = true
        haptics.confirm()
        scope.launch { progress.snapTo(1f) }
        onDone()
    }

    val shape = RoundedCornerShape(28.dp)
    // A fixed height: the fill inside grows to the button's height, not to the page's.
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(colors.primary.copy(alpha = 0.16f))
            .border(1.dp, colors.primary.copy(alpha = 0.5f), shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (done) return@awaitEachGesture
                    haptics.tick()
                    val fill = scope.launch {
                        progress.animateTo(1f, tween(1200, easing = LinearEasing))
                        complete()
                    }
                    waitForUpOrCancellation()
                    if (!done) {
                        fill.cancel()
                        scope.launch { progress.animateTo(0f, tween(220)) }
                    }
                }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick(label = label) {
                    complete()
                    true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(colors.primary)
        )
        Text(
            if (done) doneLabel else label,
            style = MaterialTheme.typography.titleMedium,
            color = if (progress.value > 0.5f) colors.onPrimary else colors.primary
        )
    }
}

/** "I can help with that", then each grant as a switch with one line of why. */
@Composable
private fun PermissionsPage(
    name: String,
    start: String,
    granted: Map<String, Boolean>,
    onRequest: (String) -> Unit
) {
    Said(
        if (name.isEmpty()) stringResource(R.string.welcome_chat_help)
        else stringResource(R.string.welcome_chat_help_name, name),
        dim = true
    )
    Said(stringResource(R.string.welcome_chat_permission))
    // The earlier answer decides which grant comes first and says why, in one line.
    val focus = when (start) {
        "browser" -> R.string.welcome_focus_browser
        "social" -> R.string.welcome_focus_social
        "videos" -> R.string.welcome_focus_videos
        else -> null
    }
    if (focus != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(focus),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(16.dp)
        )
    }
    val needed = if (start == "browser") OnboardingKeys.NEEDED.sortedBy { it != "dns" } else OnboardingKeys.NEEDED
    GrantGroup(R.string.welcome_needed_group, needed, granted, onRequest, count = true)
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
