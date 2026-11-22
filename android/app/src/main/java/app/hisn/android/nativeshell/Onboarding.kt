package app.hisn.android.nativeshell

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.hisn.android.R
import app.hisn.core.PersonalProfile
import app.hisn.core.ScanScope

enum class OnboardingProtectionState {
    OFF, STARTING, ON, NEEDS_ATTENTION
}

interface OnboardingActions {
    fun granted(key: String): Boolean

    /** Reads the live service independently of the permission Android granted it. */
    fun protectionState(key: String): OnboardingProtectionState =
        if (granted(key)) OnboardingProtectionState.ON else OnboardingProtectionState.OFF

    /** Explains a service-specific issue, when available. */
    fun protectionIssue(key: String): Int? = null

    fun request(key: String)
    fun guardOn(): Boolean
    fun setGuard() {}
    fun savePersonal(name: String, preset: String, reason: String)
    fun completeSetup()
    fun checkOn(key: String): Boolean = false
    fun setCheck(key: String, on: Boolean) {}
    fun checkedApps(): Set<String> = emptySet()
    fun setCheckedApps(packages: Set<String>) {}
}

private val ProtectionKeys = listOf("dns", "accessibility")
private val OptionalGrants = listOf("usage", "overlay", "notifications", "battery")

private data class Grant(val icon: ImageVector, val title: Int, val body: Int)

private val Grants = mapOf(
    "accessibility" to Grant(Icons.Rounded.Shield, R.string.perm_access_title, R.string.welcome_access_value),
    "dns" to Grant(Icons.Rounded.Language, R.string.perm_dns_title, R.string.welcome_dns_value),
    "usage" to Grant(Icons.Rounded.Timer, R.string.perm_usage_title, R.string.perm_usage_body),
    "overlay" to Grant(Icons.Rounded.PauseCircle, R.string.perm_overlay_title, R.string.perm_overlay_body),
    "notifications" to Grant(Icons.Rounded.Notifications, R.string.perm_notify_title, R.string.perm_notify_body),
    "battery" to Grant(Icons.Rounded.BatteryChargingFull, R.string.perm_battery_title, R.string.perm_battery_body)
)

internal enum class SetupPage(val title: Int) {
    Welcome(R.string.welcome_title_1),
    Name(R.string.welcome_chat_name),
    Chat(R.string.story_chat_title),
    Focus(R.string.welcome_start_question),
    Hours(R.string.story_hours_question),
    Math(R.string.story_math_title),
    Year(R.string.story_year_title),
    Feelings(R.string.story_feelings_question),
    Truth(R.string.story_truth_title),
    LearnHabit(R.string.story_learn_habit_title),
    LearnBrainFeeds(R.string.story_learn_brain_feeds_title),
    LearnBrainAdult(R.string.story_learn_brain_adult_title),
    LearnLoop(R.string.story_learn_loop_title),
    LearnFeeds(R.string.story_learn_feeds_title),
    LearnWillpower(R.string.story_learn_willpower_title),
    LearnChange(R.string.story_learn_change_title),
    Moment(R.string.welcome_moment_question),
    Triggers(R.string.story_triggers_question),
    Reason(R.string.personal_reason_question),
    Goals(R.string.story_goals_question),
    ShowWebsites(R.string.story_show_websites_title),
    ShowPause(R.string.story_show_pause_title),
    ShowShorts(R.string.story_show_shorts_title),
    ShowPictures(R.string.story_show_pictures_title),
    ShowPrivate(R.string.story_show_private_title),
    Compare(R.string.story_compare_title),
    Hold(R.string.story_hold_title),
    Paths(R.string.story_paths_title),
    Faq(R.string.story_faq_title),
    FirstMove(R.string.story_first_move_title),
    Rehearse(R.string.story_rehearse_title),
    Restart(R.string.story_restart_title),
    Building(R.string.story_building_title),
    Plan(R.string.welcome_plan_title),
    Reminders(R.string.story_reminders_title),
    Pledge(R.string.welcome_pledge_title),
    DayOne(R.string.story_day_title),
    Protection(R.string.welcome_protection_title),
    Checks(R.string.welcome_checks_title),
    Guard(R.string.welcome_lock_title),
    Review(R.string.welcome_review_title)
}

internal val ShowPages = listOf(
    SetupPage.ShowWebsites, SetupPage.ShowPause, SetupPage.ShowShorts, SetupPage.ShowPictures, SetupPage.ShowPrivate
)

/**
 * The pages before setup, in order. Pages that would only repeat an answer the person skipped
 * (the time maths without an hours answer) or ask for something already granted are left out.
 */
private fun storyPages(hours: String, targets: Set<String>, notificationsGranted: Boolean): List<SetupPage> = buildList {
    add(SetupPage.Welcome)
    add(SetupPage.Name)
    add(SetupPage.Chat)
    add(SetupPage.Focus)
    add(SetupPage.Hours)
    if (hours.isNotEmpty()) {
        add(SetupPage.Math)
        add(SetupPage.Year)
    }
    add(SetupPage.Feelings)
    add(SetupPage.Truth)
    addAll(learnPages(targets))
    add(SetupPage.Moment)
    add(SetupPage.Triggers)
    add(SetupPage.Reason)
    add(SetupPage.Goals)
    addAll(showOrder(targets))
    add(SetupPage.Compare)
    add(SetupPage.Hold)
    add(SetupPage.Paths)
    add(SetupPage.Faq)
    add(SetupPage.FirstMove)
    add(SetupPage.Rehearse)
    add(SetupPage.Restart)
    add(SetupPage.Building)
    add(SetupPage.Plan)
    if (!notificationsGranted) add(SetupPage.Reminders)
    add(SetupPage.Pledge)
    add(SetupPage.DayOne)
}

/** What the person wants less of. Brainrot comes first; adult content is one of the answers, not the frame. */
private val TargetAnswers = listOf(
    "shorts" to R.string.welcome_target_shorts,
    "feeds" to R.string.welcome_target_feeds,
    "time" to R.string.welcome_target_time,
    "adult" to R.string.welcome_target_adult
)

/** Short videos, feeds or phone time: the answers that App protection and pauses answer first. */
private fun wantsBrainrotFirst(targets: Set<String>): Boolean =
    targets.any { it == "shorts" || it == "feeds" || it == "time" }

/** The "how Hisn helps" pages, with the ones that answer the person's picks first. */
internal fun showOrder(targets: Set<String>): List<SetupPage> =
    if (wantsBrainrotFirst(targets)) {
        listOf(SetupPage.ShowShorts, SetupPage.ShowPause, SetupPage.ShowWebsites, SetupPage.ShowPictures, SetupPage.ShowPrivate)
    } else {
        ShowPages
    }

private val MomentAnswers = listOf(
    "often" to R.string.welcome_moment_often,
    "once" to R.string.welcome_moment_once,
    "never" to R.string.welcome_moment_never,
    "first" to R.string.welcome_moment_first
)

private data class PlanItem(val icon: ImageVector, val title: Int, val why: Int)

/** What the answers ask for, most useful first. Only layers this build can turn on are listed. */
private fun planFor(targets: Set<String>, moment: String, directBuild: Boolean): List<PlanItem> {
    val adult = targets.isEmpty() || "adult" in targets
    val websites = PlanItem(Icons.Rounded.Language, R.string.perm_dns_title, R.string.welcome_plan_websites_why)
    val apps = PlanItem(
        Icons.Rounded.Shield, R.string.perm_access_title,
        when {
            "shorts" in targets && "adult" in targets -> R.string.welcome_plan_apps_videos_why
            "shorts" in targets -> R.string.welcome_plan_shorts_why
            else -> R.string.welcome_plan_apps_why
        }
    )
    val plan = if (wantsBrainrotFirst(targets)) mutableListOf(apps) else mutableListOf(websites, apps)
    if ("feeds" in targets || "time" in targets) {
        plan += PlanItem(Icons.Rounded.HourglassTop, R.string.story_hold_wait_title, R.string.welcome_plan_pause_why)
    }
    if (wantsBrainrotFirst(targets)) plan += websites
    if (Build.VERSION.SDK_INT >= 34 && adult) {
        plan += PlanItem(Icons.Rounded.Image, R.string.welcome_check_images, R.string.welcome_plan_images_why)
    }
    if (directBuild && (moment == "often" || moment == "once")) {
        plan += PlanItem(Icons.Rounded.Lock, R.string.welcome_lock_card_title, R.string.welcome_plan_guard_why)
    }
    return plan
}

/** Tips that point at real rules, from the moments the person named. */
private fun tipsFor(triggers: Set<String>): List<Int> = buildList {
    if ("night" in triggers || "morning" in triggers) add(R.string.story_tip_schedule)
    if ("bored" in triggers || "alone" in triggers || "stressed" in triggers) add(R.string.story_tip_pause)
}

private fun split(value: String): Set<String> = value.split(',').filter { it.isNotEmpty() }.toSet()
private fun join(values: Set<String>): String = values.sorted().joinToString(",")

/** The activity refreshes this flow when system settings or either protection service changes. */
@Composable
fun Onboarding(
    actions: OnboardingActions,
    directBuild: Boolean,
    initialName: String = "",
    initialPreset: String = "",
    initialReason: String = "",
    refresh: Int = 0
) {
    var page by rememberSaveable { mutableStateOf(SetupPage.Welcome) }
    var forward by rememberSaveable { mutableStateOf(true) }
    var personalized by rememberSaveable { mutableStateOf(false) }
    var start by rememberSaveable { mutableStateOf("") }
    var hours by rememberSaveable { mutableStateOf("") }
    var feelings by rememberSaveable { mutableStateOf("") }
    var triggers by rememberSaveable { mutableStateOf("") }
    var goals by rememberSaveable { mutableStateOf("") }
    var firstResponse by rememberSaveable { mutableStateOf<FirstResponse?>(null) }
    var moment by rememberSaveable { mutableStateOf("") }
    var chat by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var preset by rememberSaveable { mutableStateOf(initialPreset) }
    var reason by rememberSaveable { mutableStateOf(initialReason) }
    var promises by rememberSaveable { mutableStateOf(0) }
    val pledged = promises >= Promises.size
    var showOptional by rememberSaveable { mutableStateOf(false) }
    var showPausePreview by rememberSaveable { mutableStateOf(false) }
    if (showPausePreview) {
        val dismiss = { showPausePreview = false }
        BackHandler(onBack = dismiss)
        OpenWaitScreen(
            appLabel = stringResource(R.string.welcome_sample_app),
            appPackage = "",
            seconds = 5,
            openedToday = 0,
            closedToday = 0,
            reason = "",
            onClose = dismiss,
            onOpen = dismiss,
            preview = true
        )
        return
    }
    val focusManager = LocalFocusManager.current
    val haptics = rememberHisnHaptics()
    val notificationsGranted = remember(refresh) { actions.granted("notifications") }
    // Settled once the person passes the reminders page, so granting there does not reshuffle the pages.
    val story = remember(hours, start, notificationsGranted, page) {
        storyPages(hours, split(start), notificationsGranted && page != SetupPage.Reminders)
    }
    val steps = if (directBuild) listOf(SetupPage.Protection, SetupPage.Checks, SetupPage.Guard, SetupPage.Review)
    else listOf(SetupPage.Protection, SetupPage.Checks, SetupPage.Review)
    val step = steps.indexOf(page) + 1
    val storyIndex = story.indexOf(page)

    fun go(target: SetupPage, ahead: Boolean = true) {
        focusManager.clearFocus()
        forward = ahead
        page = target
    }

    fun savePersonal() {
        val custom = preset == PersonalProfile.CUSTOM
        val words = if (custom) reason.trim() else ""
        actions.savePersonal(name.trim(), if (custom && words.isEmpty()) "" else preset, words)
    }

    fun back() {
        when {
            page == SetupPage.Welcome -> actions.completeSetup()
            page == SetupPage.Protection -> go(if (personalized) story.last() else SetupPage.Welcome, ahead = false)
            storyIndex > 0 -> go(story[storyIndex - 1], ahead = false)
            step > 1 -> go(steps[step - 2], ahead = false)
            else -> go(SetupPage.Welcome, ahead = false)
        }
    }

    fun skipQuestions() {
        personalized = false
        start = ""
        moment = ""
        go(SetupPage.Protection)
    }

    fun next() {
        when {
            page == SetupPage.Welcome -> {
                personalized = true
                start = ""
                moment = ""
                go(SetupPage.Name)
            }
            page == SetupPage.Review -> actions.completeSetup()
            storyIndex >= 0 -> {
                if (page == SetupPage.Reason) savePersonal()
                go(story.getOrNull(storyIndex + 1) ?: SetupPage.Protection)
            }
            else -> go(steps[step])
        }
    }

    BackHandler(onBack = ::back)

    // The last promise moves on by itself a moment after it is made, so the confirmation is seen.
    LaunchedEffect(pledged, page) {
        if (pledged && page == SetupPage.Pledge && forward) {
            delay(900)
            next()
        }
    }

    val states = remember(refresh, page) { ProtectionKeys.associateWith(actions::protectionState) }
    val issues = remember(refresh, page) { ProtectionKeys.associateWith(actions::protectionIssue) }
    val appProtection = states.getValue("accessibility")
    val guardOn = remember(refresh, page, directBuild) { directBuild && actions.guardOn() }
    val guardState = when {
        !guardOn -> OnboardingProtectionState.OFF
        appProtection == OnboardingProtectionState.OFF -> OnboardingProtectionState.NEEDS_ATTENTION
        else -> appProtection
    }
    val optional = remember(refresh, page, showOptional) {
        if (showOptional && page == SetupPage.Protection) OptionalGrants.associateWith(actions::granted)
        else emptyMap()
    }
    val checks = remember(refresh, page) { listOf("tags", "images").associateWith(actions::checkOn) }
    var checkedApps by remember(refresh, page) { mutableStateOf(actions.checkedApps()) }
    val reasonText = if (!personalized) "" else if (preset == PersonalProfile.CUSTOM) reason.trim()
    else PersonalPresets.firstOrNull { it.first == preset }?.let { stringResource(it.second) }.orEmpty()
    val colors = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val progressTarget = when {
        storyIndex > 0 -> storyIndex.toFloat() / (story.size - 1)
        step > 0 -> step.toFloat() / steps.size
        else -> 0f
    }
    val progress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = if (reduceMotion) snap() else tween(HisnMotion.MediumMs),
        label = "setup progress"
    )

    CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides colors.onBackground) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .background(Brush.verticalGradient(listOf(colors.primaryContainer.copy(alpha = 0.45f), Color.Transparent)))
                .safeDrawingPadding()
        ) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (page != SetupPage.Welcome) {
                        IconButton(onClick = ::back) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.welcome_back))
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = when {
                            step > 0 -> stringResource(R.string.welcome_step, step, steps.size)
                            storyIndex > 0 -> stringResource(R.string.story_percent, (progressTarget * 100).toInt())
                            else -> stringResource(R.string.app_name)
                        },
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (page == SetupPage.Welcome || (step > 0 && page != SetupPage.Review)) {
                        TextButton(onClick = actions::completeSetup, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.welcome_skip))
                        }
                    }
                }
                if (progressTarget > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clearAndSetSemantics { },
                        trackColor = colors.outlineVariant.copy(alpha = 0.55f)
                    )
                }

                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        if (reduceMotion) {
                            fadeIn(tween(HisnMotion.FastMs)) togetherWith fadeOut(tween(HisnMotion.FastMs))
                        } else {
                            // Forward pages arrive from the reading end; back pages from the start.
                            val sign = (if (forward) 1 else -1) * (if (rtl) -1 else 1)
                            (fadeIn(tween(HisnMotion.MediumMs)) +
                                slideInHorizontally(tween(HisnMotion.MediumMs)) { sign * it / 6 }) togetherWith
                                (fadeOut(tween(HisnMotion.FastMs)) +
                                    slideOutHorizontally(tween(HisnMotion.MediumMs)) { -sign * it / 6 })
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    label = "setup page"
                ) { shown ->
                    val title = stringResource(shown.title)
                    Column(
                        Modifier
                            .fillMaxSize()
                            .semantics { paneTitle = title }
                            .verticalScroll(rememberScrollState())
                            .padding(top = if (shown == SetupPage.Welcome) 26.dp else 22.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (shown) {
                            SetupPage.Welcome -> WelcomePage { showPausePreview = true }
                            SetupPage.Name -> NamePage(name, onDone = ::next) {
                                name = limitCodePoints(it, PersonalProfile.MAX_NAME_CODE_POINTS)
                            }
                            SetupPage.Chat -> {
                                val picks = chat.split(',').mapNotNull { it.toIntOrNull() }
                                ChatPage(name.trim(), picks) { reply ->
                                    haptics.tick()
                                    chat = (picks + reply).joinToString(",")
                                    if (picks.size + 1 >= ChatBlocks) next()
                                }
                            }
                            SetupPage.Focus -> FocusPage(split(start)) {
                                haptics.tick()
                                start = join(it)
                            }
                            SetupPage.Hours -> HoursPage(hours) {
                                haptics.tick()
                                hours = it
                                // The maths pages join the story only now, so move on from the new list.
                                go(SetupPage.Math)
                            }
                            SetupPage.Math -> MathPage(name.trim(), hours)
                            SetupPage.Year -> YearPage(hours)
                            SetupPage.Feelings -> FeelingsPage(split(feelings)) {
                                haptics.tick()
                                feelings = join(it)
                            }
                            SetupPage.Truth -> TruthPage(name.trim())
                            SetupPage.LearnHabit, SetupPage.LearnBrainFeeds, SetupPage.LearnBrainAdult,
                            SetupPage.LearnLoop, SetupPage.LearnFeeds,
                            SetupPage.LearnWillpower, SetupPage.LearnChange -> LearnPage(shown, learnPages(split(start)))
                            SetupPage.Goals -> GoalsPage(split(goals)) {
                                haptics.tick()
                                goals = join(it)
                            }
                            SetupPage.Compare -> ComparePage()
                            SetupPage.Faq -> FaqPage()
                            SetupPage.FirstMove -> FirstMovePage(firstResponse) { response ->
                                haptics.tick()
                                firstResponse = response
                                next()
                            }
                            SetupPage.Rehearse -> RehearsePage(firstResponse) { showPausePreview = true }
                            SetupPage.Restart -> RestartPage()
                            SetupPage.Building -> BuildingPage(
                                start.isNotEmpty(), split(triggers).isNotEmpty(), reasonText.isNotEmpty()
                            )
                            SetupPage.DayOne -> DayOnePage(name.trim())
                            SetupPage.Hold -> HoldPage(directBuild)
                            SetupPage.Moment -> MomentPage(moment) {
                                haptics.tick()
                                moment = it
                                next()
                            }
                            SetupPage.Triggers -> TriggersPage(split(triggers)) {
                                haptics.tick()
                                triggers = join(it)
                            }
                            SetupPage.Reason -> ReasonPage(preset, {
                                haptics.tick()
                                preset = it
                                if (it != PersonalProfile.CUSTOM) next()
                            }, reason) {
                                reason = limitCodePoints(it, PersonalProfile.MAX_REASON_CODE_POINTS)
                            }
                            SetupPage.ShowWebsites, SetupPage.ShowPause, SetupPage.ShowShorts,
                            SetupPage.ShowPictures, SetupPage.ShowPrivate ->
                                ShowPage(shown, showOrder(split(start)), onPreview = { showPausePreview = true })
                            SetupPage.Paths -> PathsPage(split(feelings))
                            SetupPage.Plan -> PlanPage(
                                name.trim(), reasonText, planFor(split(start), moment, directBuild), tipsFor(split(triggers)),
                                split(goals), firstResponse
                            )
                            SetupPage.Reminders -> RemindersPage()
                            SetupPage.Pledge -> PledgePage(name.trim(), reasonText, promises) {
                                haptics.confirm()
                                promises = (promises + 1).coerceAtMost(Promises.size)
                            }
                            SetupPage.Protection -> ProtectionPage(
                                split(start), states, issues, optional, showOptional,
                                onShowOptional = { showOptional = !showOptional },
                                onRequest = actions::request
                            )
                            SetupPage.Checks -> ChecksPage(
                                checks = checks,
                                checked = checkedApps,
                                appProtection = appProtection,
                                onAppProtection = { actions.request("accessibility") },
                                onCheck = actions::setCheck,
                                onApps = {
                                    actions.setCheckedApps(it)
                                    checkedApps = actions.checkedApps()
                                }
                            )
                            SetupPage.Guard -> GuardPage(
                                guardState, appProtection, actions::setGuard,
                                onAppProtection = { actions.request("accessibility") }
                            )
                            SetupPage.Review -> ReviewPage(
                                reasonText, states, issues, checks, checkedApps,
                                guard = if (directBuild) guardState else null,
                                onRequest = actions::request
                            )
                        }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val unanswered = when (page) {
                        SetupPage.Focus -> start.isEmpty()
                        SetupPage.Hours -> hours.isEmpty()
                        SetupPage.Feelings -> feelings.isEmpty()
                        SetupPage.Moment -> moment.isEmpty()
                        SetupPage.Triggers -> triggers.isEmpty()
                        SetupPage.Goals -> goals.isEmpty()
                        SetupPage.FirstMove -> firstResponse == null
                        SetupPage.Reason -> preset.isEmpty()
                        SetupPage.Chat -> chat.split(',').count { it.isNotEmpty() } < ChatBlocks
                        SetupPage.Pledge -> !pledged
                        else -> false
                    }
                    when {
                        page == SetupPage.Reminders -> {
                            Button(
                                onClick = {
                                    actions.request("notifications")
                                    next()
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) { Text(stringResource(R.string.story_reminders_allow), style = MaterialTheme.typography.titleMedium) }
                            TextButton(onClick = ::next, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.welcome_pledge_later))
                            }
                        }
                        unanswered -> TextButton(onClick = ::next, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(
                                stringResource(
                                    when (page) {
                                        SetupPage.Pledge -> R.string.welcome_pledge_later
                                        SetupPage.Chat -> R.string.story_chat_skip
                                        else -> R.string.welcome_skip_question
                                    }
                                ),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        else -> Button(onClick = ::next, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(
                                stringResource(
                                    when (page) {
                                        SetupPage.Welcome -> R.string.welcome_get_started
                                        SetupPage.DayOne -> R.string.welcome_plan_start
                                        SetupPage.Review -> R.string.welcome_ready_finish
                                        else -> R.string.welcome_perm_continue
                                    }
                                ),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    if (page == SetupPage.Welcome) {
                        TextButton(
                            onClick = ::skipQuestions,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text(stringResource(R.string.welcome_skip_questions)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Heading(title: Int, body: Int? = null, hero: Boolean = false) {
    val typography = MaterialTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(title),
            style = (if (hero) typography.displaySmall else typography.headlineMedium)
                .storySerif(),
            modifier = Modifier.fillMaxWidth().semantics { heading() }
        )
        if (body != null) SupportingText(stringResource(body))
    }
}

@Composable
internal fun SupportingText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WelcomePage(onPreview: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(98.dp).border(1.dp, colors.primary.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        IconBadge(Icons.Outlined.Shield, colors.primaryContainer, colors.onPrimaryContainer, size = 76.dp)
    }
    Heading(R.string.welcome_title_1, R.string.welcome_first_body, hero = true)
    HisnCard {
        BenefitRow(Icons.Rounded.SmartDisplay, R.string.story_show_shorts_title, R.string.welcome_benefit_shorts)
        InsetDivider()
        BenefitRow(Icons.Rounded.HourglassTop, R.string.story_show_pause_title, R.string.welcome_benefit_pause)
        InsetDivider()
        BenefitRow(Icons.Rounded.Language, R.string.story_show_websites_title, R.string.welcome_benefit_adult)
        TextButton(
            onClick = onPreview,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp).heightIn(min = 48.dp)
        ) {
            Icon(Icons.Rounded.HourglassTop, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.welcome_preview_pause))
        }
    }
    Row(
        Modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
        SupportingText(stringResource(R.string.welcome_privacy_line))
    }
}

@Composable
private fun BenefitRow(icon: ImageVector, title: Int, body: Int) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(16.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, colors.primaryContainer, colors.onPrimaryContainer)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            SupportingText(stringResource(body))
        }
    }
}

@Composable
private fun FocusPage(chosen: Set<String>, onChange: (Set<String>) -> Unit) {
    Heading(R.string.welcome_start_question, R.string.welcome_focus_body)
    MultiChoice(TargetAnswers, chosen, onChange)
    PrivateAnswersNote()
}

@Composable
private fun MomentPage(chosen: String, onChoose: (String) -> Unit) {
    Heading(R.string.welcome_moment_question, R.string.welcome_moment_body)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MomentAnswers.forEach { (value, label) -> ChoiceRow(label, chosen == value) { onChoose(value) } }
    }
    PrivateAnswersNote()
}

@Composable
internal fun PrivateAnswersNote() {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp))
        Text(stringResource(R.string.welcome_answers_private), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The answers turned into an ordered list of layers, each with the reason it is on the list. */
@Composable
private fun PlanPage(
    name: String,
    reason: String,
    plan: List<PlanItem>,
    tips: List<Int>,
    goals: Set<String>,
    firstResponse: FirstResponse?
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    IconBadge(Icons.Rounded.CheckCircle, colors.primaryContainer, colors.onPrimaryContainer, size = 56.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (name.isEmpty()) stringResource(R.string.welcome_plan_title)
            else stringResource(R.string.welcome_plan_title_named, name),
            style = typography.headlineMedium.storySerif(),
            modifier = Modifier.fillMaxWidth().semantics { heading() }
        )
        SupportingText(androidx.compose.ui.res.pluralStringResource(R.plurals.welcome_plan_body, plan.size, plan.size))
    }
    HisnCard {
        plan.forEachIndexed { index, item ->
            Row(
                Modifier.fillMaxWidth().padding(16.dp).semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    Modifier.size(32.dp).background(colors.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${index + 1}", style = typography.labelLarge, color = colors.onPrimary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(item.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                        Text(stringResource(item.title), style = typography.titleMedium)
                    }
                    SupportingText(stringResource(item.why))
                }
            }
            if (index < plan.lastIndex) InsetDivider(start = 60.dp)
        }
    }
    GoalChips(goals)
    if (firstResponse != null) FirstResponseCard(firstResponse)
    if (tips.isNotEmpty()) {
        HisnCard(containerColor = colors.secondaryContainer) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.story_tips_title), style = typography.labelLarge,
                    color = colors.onSecondaryContainer.copy(alpha = 0.8f))
                tips.forEach { Text(stringResource(it), style = typography.bodyMedium, color = colors.onSecondaryContainer) }
            }
        }
    }
    if (reason.isNotEmpty()) ReasonQuote(reason)
}

@Composable
internal fun ReasonQuote(reason: String) {
    HisnCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.personal_card_eyebrow),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
            Text(
                stringResource(R.string.personal_quote, reason),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/** A promise made on purpose: the person holds the button; TalkBack users activate it once. */
@Composable
private fun PledgePage(name: String, reason: String, made: Int, onPledge: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pledged = made >= Promises.size
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(104.dp).border(1.dp, colors.primary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            IconBadge(
                if (pledged) Icons.Rounded.CheckCircle else Icons.Rounded.Favorite,
                colors.primaryContainer, colors.onPrimaryContainer, size = 80.dp
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (name.isEmpty()) stringResource(R.string.welcome_pledge_title)
            else stringResource(R.string.welcome_pledge_title_named, name),
            style = MaterialTheme.typography.headlineMedium.storySerif(),
            modifier = Modifier.fillMaxWidth().semantics { heading() }
        )
        SupportingText(stringResource(R.string.welcome_pledge_body))
    }
    if (reason.isNotEmpty()) ReasonQuote(reason)
    // Promises already made stay listed with a check; the next one waits for its own hold.
    Promises.take(made.coerceAtMost(Promises.size)).forEach { promise ->
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = LocalHisnStatusColors.current.success)
            Text(stringResource(promise), style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (!pledged) {
        Text(
            stringResource(R.string.story_promise_count, made + 1, Promises.size),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        HisnCard {
            Text(
                stringResource(Promises[made]),
                style = MaterialTheme.typography.titleLarge.storySerif(),
                modifier = Modifier.padding(20.dp)
            )
        }
    }
    key(made) { HoldToPromise(pledged, onPledge) }
}

internal val Promises = listOf(R.string.welcome_pledge_line, R.string.story_promise_pause, R.string.story_promise_restart)

private const val HoldMs = 1_500

@Composable
private fun HoldToPromise(done: Boolean, onDone: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var holding by remember { mutableStateOf(false) }
    val fill = remember { Animatable(if (done) 1f else 0f) }
    LaunchedEffect(holding, done) {
        when {
            done -> fill.snapTo(1f)
            holding -> {
                fill.animateTo(1f, tween(((1f - fill.value) * HoldMs).toInt(), easing = LinearEasing))
                onDone()
            }
            else -> fill.animateTo(0f, tween(HisnMotion.FastMs))
        }
    }
    val label = stringResource(if (done) R.string.welcome_pledge_done else R.string.welcome_pledge_hold)
    val fillColor = colors.primary.copy(alpha = 0.35f)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(CircleShape)
            .background(if (done) colors.primary else colors.primaryContainer)
            .drawBehind {
                val width = size.width * fill.value
                val left = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                drawRect(fillColor, topLeft = Offset(left, 0f), size = Size(width, size.height))
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (!done) onClick(label) { onDone(); true }
            }
            .pointerInput(done) {
                if (!done) detectTapGestures(onPress = {
                    holding = true
                    tryAwaitRelease()
                    holding = false
                })
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                if (done) Icons.Rounded.Check else Icons.Rounded.TouchApp, contentDescription = null,
                tint = if (done) colors.onPrimary else colors.onPrimaryContainer
            )
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = if (done) colors.onPrimary else colors.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun NamePage(name: String, onDone: () -> Unit, onName: (String) -> Unit) {
    Heading(R.string.welcome_chat_name, R.string.welcome_personal_body)
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        label = { Text(stringResource(R.string.personal_name_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onNext = { onDone() }),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ReasonPage(preset: String, onPreset: (String) -> Unit, reason: String, onReason: (String) -> Unit) {
    Heading(R.string.personal_reason_question, R.string.welcome_answers_private)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PersonalPresets.forEach { (value, label) -> ChoiceRow(label, preset == value) { onPreset(value) } }
    }
    if (preset == PersonalProfile.CUSTOM) {
        OutlinedTextField(
            value = reason,
            onValueChange = onReason,
            label = { Text(stringResource(R.string.personal_reason_hint)) },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun ChoiceRow(label: Int, selected: Boolean, icon: ImageVector? = null, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    HisnCard(containerColor = if (selected) colors.primaryContainer else LocalHisnStatusColors.current.card) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                IconBadge(
                    icon,
                    if (selected) colors.primary else colors.primaryContainer,
                    if (selected) colors.onPrimary else colors.onPrimaryContainer,
                    size = 36.dp
                )
            }
            Text(
                stringResource(label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                color = if (selected) colors.onPrimaryContainer else colors.onSurface
            )
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimaryContainer)
        }
    }
}

@Composable
private fun ChecksPage(
    checks: Map<String, Boolean>,
    checked: Set<String>,
    appProtection: OnboardingProtectionState,
    onAppProtection: () -> Unit,
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

    Heading(R.string.welcome_checks_title, R.string.welcome_checks_body)
    if (appProtection != OnboardingProtectionState.ON) {
        HisnCard(containerColor = colors.secondaryContainer) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.welcome_checks_needs_access),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSecondaryContainer
                )
                if (appProtection != OnboardingProtectionState.STARTING) {
                    TextButton(onClick = onAppProtection, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.welcome_open_access))
                    }
                }
            }
        }
    }
    HisnCard {
        CheckRow(Icons.Rounded.LocalOffer, R.string.welcome_check_tags, R.string.adult_mode_short,
            checks["tags"] == true, enabled = true) { onCheck("tags", it) }
        InsetDivider()
        val imagesSupported = Build.VERSION.SDK_INT >= 34
        CheckRow(Icons.Rounded.Image, R.string.welcome_check_images,
            if (imagesSupported) R.string.visual_short else R.string.welcome_check_images_old,
            checks["images"] == true, enabled = imagesSupported) { onCheck("images", it) }
    }

    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.welcome_apps_title),
            style = MaterialTheme.typography.titleSmall,
            color = colors.primary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        Text(
            if (ScanScope.all(checked)) stringResource(R.string.welcome_check_all_apps)
            else stringResource(R.string.welcome_apps_count, ScanScope.chosen(checked)),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
    val installedBrowsers = ordered.filter { it.packageName in browsers }.map { it.packageName }.toSet()
    if (installedBrowsers.isNotEmpty() && !checked.containsAll(installedBrowsers)) {
        FilledTonalButton(
            onClick = { onApps(checked + installedBrowsers) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) { Text(stringResource(R.string.welcome_apps_browsers, installedBrowsers.size)) }
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
        if (showAll || ordered.size > 8 || (ordered.isNotEmpty() && canListEveryApp(context))) {
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
        trailingContent = { HisnSwitch(on, enabled = enabled) },
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

@Composable
private fun ProtectionPage(
    targets: Set<String>,
    states: Map<String, OnboardingProtectionState>,
    issues: Map<String, Int?>,
    optional: Map<String, Boolean>,
    showOptional: Boolean,
    onShowOptional: () -> Unit,
    onRequest: (String) -> Unit
) {
    val appsFirst = wantsBrainrotFirst(targets)
    val body = when {
        appsFirst -> R.string.welcome_focus_social
        "adult" in targets -> R.string.welcome_focus_browser
        else -> R.string.welcome_protection_body
    }
    Heading(R.string.welcome_protection_title, body)
    ProtectionCards(
        if (appsFirst) ProtectionKeys.asReversed() else ProtectionKeys,
        states, issues, onRequest
    )
    TextButton(
        onClick = onShowOptional,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        Text(stringResource(if (showOptional) R.string.welcome_hide_optional else R.string.welcome_optional_permissions))
        Spacer(Modifier.width(8.dp))
        Icon(if (showOptional) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
    }
    if (showOptional) {
        HisnCard {
            OptionalGrants.forEachIndexed { index, id ->
                val grant = Grants.getValue(id)
                val granted = optional[id] == true
                val action = if (granted) Modifier else Modifier.clickable(role = Role.Button) { onRequest(id) }
                ListItem(
                    headlineContent = { Text(stringResource(grant.title)) },
                    supportingContent = { SupportingText(stringResource(grant.body)) },
                    leadingContent = { Icon(grant.icon, contentDescription = null) },
                    trailingContent = {
                        if (granted) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = stringResource(R.string.welcome_status_active),
                                tint = LocalHisnStatusColors.current.success
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = action.heightIn(min = 48.dp)
                )
                if (index < OptionalGrants.lastIndex) InsetDivider()
            }
        }
    }
}

@Composable
private fun ProtectionCards(
    keys: List<String>,
    states: Map<String, OnboardingProtectionState>,
    issues: Map<String, Int?>,
    onRequest: (String) -> Unit,
    showDetails: Boolean = true
) {
    keys.forEach { id ->
        val grant = Grants.getValue(id)
        val state = states.getValue(id)
        val issue = issues[id]
        val action = when {
            id == "accessibility" -> R.string.welcome_open_access
            issue == R.string.attention_private_dns || issue == R.string.attention_dns_degraded ->
                R.string.welcome_open_network
            else -> R.string.welcome_enable_website
        }
        StatusCard(grant.icon, grant.title, state) {
            if (showDetails) SupportingText(stringResource(grant.body))
            if (issue != null) SupportingText(stringResource(issue))
            if (state == OnboardingProtectionState.OFF || state == OnboardingProtectionState.NEEDS_ATTENTION) {
                if (showDetails && id == "accessibility" && issue == null) {
                    SupportingText(stringResource(R.string.welcome_access_path))
                }
                CardAction(action) { onRequest(id) }
            }
        }
    }
}

@Composable
private fun StatusCard(
    icon: ImageVector,
    title: Int,
    state: OnboardingProtectionState,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val status = LocalHisnStatusColors.current
    val tint = when (state) {
        OnboardingProtectionState.ON -> status.success
        OnboardingProtectionState.NEEDS_ATTENTION -> status.warning
        OnboardingProtectionState.STARTING -> colors.primary
        OnboardingProtectionState.OFF -> colors.onSurfaceVariant
    }
    val label = when (state) {
        OnboardingProtectionState.ON -> R.string.welcome_status_active
        OnboardingProtectionState.NEEDS_ATTENTION -> R.string.welcome_status_attention
        OnboardingProtectionState.STARTING -> R.string.welcome_status_starting
        OnboardingProtectionState.OFF -> R.string.perm_off
    }
    HisnCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(icon, colors.primaryContainer, colors.onPrimaryContainer)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = tint)
                }
            }
            content()
        }
    }
}

@Composable
private fun CardAction(label: Int, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) { Text(stringResource(label)) }
}

@Composable
private fun GuardPage(
    state: OnboardingProtectionState,
    appProtection: OnboardingProtectionState,
    onEnable: () -> Unit,
    onAppProtection: () -> Unit
) {
    Heading(R.string.welcome_lock_title, R.string.welcome_guard_body)
    StatusCard(Icons.Rounded.Lock, R.string.welcome_lock_card_title, state) {
        SupportingText(stringResource(R.string.welcome_guard_details))
        when {
            appProtection == OnboardingProtectionState.OFF || appProtection == OnboardingProtectionState.NEEDS_ATTENTION -> {
                SupportingText(stringResource(R.string.welcome_guard_requires_access))
                CardAction(R.string.welcome_open_access, onAppProtection)
            }
            appProtection == OnboardingProtectionState.STARTING -> SupportingText(stringResource(R.string.welcome_guard_requires_access))
            state == OnboardingProtectionState.OFF -> CardAction(R.string.welcome_enable_guard, onEnable)
        }
    }
}

@Composable
private fun ReviewPage(
    reason: String,
    states: Map<String, OnboardingProtectionState>,
    issues: Map<String, Int?>,
    checks: Map<String, Boolean>,
    checkedApps: Set<String>,
    guard: OnboardingProtectionState?,
    onRequest: (String) -> Unit
) {
    val active = states.values.count { it == OnboardingProtectionState.ON }
    val details = when (active) {
        0 -> R.string.welcome_review_inactive
        ProtectionKeys.size -> R.string.welcome_review_active
        else -> R.string.welcome_review_partial
    }
    if (active == ProtectionKeys.size) {
        val status = LocalHisnStatusColors.current
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(104.dp).border(1.dp, status.success.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                IconBadge(Icons.Rounded.CheckCircle, status.success.copy(alpha = 0.16f), status.success, size = 80.dp)
            }
        }
        Heading(R.string.welcome_review_done_title, details)
    } else {
        Heading(R.string.welcome_review_title, details)
    }
    if (reason.isNotEmpty()) ReasonQuote(reason)
    ProtectionCards(ProtectionKeys, states, issues, onRequest, showDetails = false)
    HisnCard {
        val checksActive = states["accessibility"] == OnboardingProtectionState.ON
        val allApps = ScanScope.all(checkedApps)
        val checkedCount = ScanScope.chosen(checkedApps)
        val checkRows = listOf(
            Triple("tags", Icons.Rounded.LocalOffer, R.string.welcome_check_tags),
            Triple("images", Icons.Rounded.Image, R.string.welcome_check_images)
        )
        checkRows.forEachIndexed { index, (key, icon, title) ->
            val label = when {
                key == "images" && Build.VERSION.SDK_INT < 34 -> stringResource(R.string.welcome_check_images_old)
                checks[key] != true -> stringResource(R.string.perm_off)
                checksActive && allApps -> stringResource(R.string.welcome_check_on_all_apps)
                !allApps && checkedCount == 0 -> stringResource(R.string.welcome_check_no_apps)
                checksActive -> stringResource(R.string.welcome_check_on_apps, checkedCount)
                else -> stringResource(R.string.welcome_check_saved)
            }
            ReviewRow(icon, title, label)
            if (index != checkRows.lastIndex || guard != null) InsetDivider()
        }
        if (guard != null) {
            val label = when (guard) {
                OnboardingProtectionState.ON -> R.string.welcome_status_active
                OnboardingProtectionState.STARTING -> R.string.welcome_status_starting
                OnboardingProtectionState.NEEDS_ATTENTION -> R.string.welcome_status_attention
                OnboardingProtectionState.OFF -> R.string.perm_off
            }
            ReviewRow(Icons.Rounded.Lock, R.string.welcome_lock_card_title, stringResource(label))
        }
    }
    SupportingText(stringResource(R.string.welcome_review_next))
}

@Composable
private fun ReviewRow(icon: ImageVector, title: Int, label: String) {
    ListItem(
        headlineContent = { Text(stringResource(title), style = MaterialTheme.typography.titleMedium) },
        supportingContent = { SupportingText(label) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.semantics(mergeDescendants = true) {}
    )
}
