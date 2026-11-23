package app.hisn.android.nativeshell

import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.ui.input.pointer.pointerInput

import kotlinx.coroutines.launch

import androidx.compose.material.icons.rounded.Bedtime

import androidx.compose.material.icons.rounded.VideocamOff

import androidx.compose.material.icons.rounded.NotificationsActive

import androidx.compose.material.icons.rounded.PlayCircle

import androidx.compose.material.icons.rounded.Casino

import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown

import androidx.compose.material.icons.rounded.Lock

import androidx.compose.material.icons.rounded.HourglassTop

import androidx.compose.ui.draw.clip

import androidx.compose.ui.semantics.onClick

import androidx.compose.ui.semantics.role

import androidx.compose.ui.semantics.clearAndSetSemantics

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.hisn.android.R
import app.hisn.core.SetupPlan
import app.hisn.core.SocialApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The general edition's setup: seven short steps after the welcome, built on what research and
 * competitors showed works and what does not.
 *
 *  - You point at your real apps (sorted by today's minutes when usage access is on) instead of
 *    answering a quiz about your habits. CleanPath's 13-question quiz and scare score are the
 *    opposite of this.
 *  - One three-way choice per app, mapped by [SetupPlan] to a rule the engine really has.
 *  - You try the real wait screen before anything is switched on.
 *  - A note to your late-night self: an if-then plan written while calm, shown at the hard moment.
 *  - The two Android switches come last, each with one line on why, and the plan is saved before
 *    them so nothing is lost if Android settings take a while.
 *
 * The Islamic edition keeps its own story-based setup in [Onboarding].
 */
interface UnrotSetupActions : OnboardingActions {
    /** Writes the plan into the real rules; false when a protection lock refuses the change. */
    fun applyPlan(plan: SetupPlan, note: String): Boolean
}

private enum class Step {
    Hello, Demo1, Demo2, Demo3, Name, Age, Goals, When, Feel, Start, Year, Cost, Tricks, Hard, Helps, Wave, Lives, Expect, Solve, Instead,
    Apps, Firm, Try, Quiet, Note, Plan, Switches, Notify, Test, Hold, Done
}

/**
 * The four chapters of the Glasshouse story: you, why it's hard, what helps, your plan. The bar at
 * the top has one segment per chapter, so a long setup never feels endless.
 */
private val Chapters = listOf(R.string.obg_chapter_you, R.string.obg_chapter_why, R.string.obg_chapter_helps, R.string.obg_chapter_plan)

private fun chapterOf(step: Step): Int = when (step) {
    Step.Hello, Step.Demo1, Step.Demo2, Step.Demo3, Step.Name, Step.Age, Step.Goals, Step.When, Step.Feel, Step.Start -> 0
    Step.Year, Step.Cost, Step.Tricks, Step.Hard -> 1
    Step.Helps, Step.Wave, Step.Lives, Step.Expect, Step.Solve, Step.Instead -> 2
    else -> 3
}

/** How full the current chapter's segment is, 0..1 (the welcome and the finish show no bar). */
private fun chapterFill(step: Step): Float {
    val steps = Step.entries.filter { chapterOf(it) == chapterOf(step) && it != Step.Hello && it != Step.Done }
    val at = steps.indexOf(step)
    return if (at < 0) 0f else (at + 1f) / steps.size
}

/** The Glasshouse gold: bright on the dark page, deeper on the light one so it still reads. */
@Composable
private fun glassGold(): Color = if (LocalDarkTheme.current) Color(0xFFF2C14E) else Color(0xFFB07A12)

private val QuietPresets = listOf(
    Triple(R.string.unrot_setup_quiet_nights, 22 * 60, 7 * 60),
    Triple(R.string.unrot_setup_quiet_late, 23 * 60, 6 * 60),
    Triple(R.string.unrot_setup_quiet_work, 9 * 60, 17 * 60)
)

/** "start,end" in minutes, or "none". Kept as text so it survives rotation and process death. */
private fun quietWindow(text: String): Pair<Int, Int> =
    text.split(',').takeIf { it.size == 2 }?.let { (a, b) ->
        val s = a.toIntOrNull(); val e = b.toIntOrNull()
        if (s != null && e != null && s in 0 until 1440 && e in 0 until 1440 && s != e) s to e else null
    } ?: (-1 to -1)

@Composable
fun UnrotOnboarding(actions: UnrotSetupActions, refresh: Int, initialReason: String) {
    var step by rememberSaveable { mutableStateOf(Step.Hello) }
    var forward by rememberSaveable { mutableStateOf(true) }
    var chosenText by rememberSaveable { mutableStateOf<String?>(null) }
    var strengthText by rememberSaveable { mutableStateOf("") }
    var adult by rememberSaveable { mutableStateOf(true) }
    var quiet by rememberSaveable { mutableStateOf("${22 * 60},${7 * 60}") }
    var quietTouched by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf(initialReason) }
    var age by rememberSaveable { mutableStateOf("") }
    var preview by rememberSaveable { mutableStateOf<String?>(null) }
    var saved by rememberSaveable { mutableStateOf(false) }
    // About you: every answer is used (see UnrotAnswers).
    var name by rememberSaveable { mutableStateOf("") }
    var goalsText by rememberSaveable { mutableStateOf("") }
    var whenKey by rememberSaveable { mutableStateOf("") }
    var feelText by rememberSaveable { mutableStateOf("") }
    var insteadText by rememberSaveable { mutableStateOf("") }
    var guessMinutes by rememberSaveable { mutableStateOf(180) }
    var why by rememberSaveable { mutableStateOf(false) }
    // The first real test: when the person left to try it, so the end can say "It worked" only
    // if a block or wait screen really showed after that.
    var testStarted by rememberSaveable { mutableStateOf(0L) }
    // The pledge: the seed is planted once the person has held the button all the way.
    var planted by rememberSaveable { mutableStateOf(false) }
    fun split(text: String) = text.split(',').filter { it.isNotEmpty() }.toSet()
    val goals = split(goalsText)
    val feel = split(feelText)
    val instead = split(insteadText)

    val context = LocalContext.current
    val apps by produceState<List<SetupApp>?>(initialValue = null, refresh) {
        value = withContext(Dispatchers.IO) { loadSetupApps(context) }
    }
    val usageOn = remember(refresh) { Usage.hasAccess(context) }
    val feedApps = apps.orEmpty().filter { it.known }.map { it.packageName }.toSet()
    val point by produceState<StartingPoint?>(initialValue = null, refresh, feedApps) {
        value = withContext(Dispatchers.IO) { StartingPoints.read(context, feedApps) }
    }
    // Apps the person really uses come first in the suggestion; known feed apps otherwise.
    val used = point?.apps?.filter { it.second > 5 * 60_000L }?.map { it.first }?.toSet().orEmpty()
    val chosen: Set<String> = chosenText?.split(',')?.filter { it.isNotEmpty() }?.toSet()
        ?: apps?.filter { it.suggested && (used.isEmpty() || it.packageName in used) }?.map { it.packageName }?.toSet()
        ?: emptySet()
    val topApp = point?.apps?.firstOrNull { it.first in chosen }?.first ?: chosen.firstOrNull()
    // Goals pre-set how firm each app is; the person's own pick on the Firm step wins.
    fun goalStrength(pkg: String): SetupPlan.Strength? = when {
        "evenings" in goals && pkg == topApp -> SetupPlan.Strength.LIMIT
        "focus" in goals -> SetupPlan.Strength.WAIT
        "less" in goals -> SetupPlan.suggested(pkg)
        else -> null
    }
    val strengths: Map<String, SetupPlan.Strength> = chosen.associateWith { pkg ->
        strengthText.split(';').firstOrNull { it.startsWith("$pkg=") }
            ?.substringAfter('=')?.let { runCatching { SetupPlan.Strength.valueOf(it) }.getOrNull() }
            ?: goalStrength(pkg) ?: SetupPlan.suggested(pkg)
    }
    fun setStrength(pkg: String, strength: SetupPlan.Strength) {
        val others = strengthText.split(';').filter { it.isNotEmpty() && !it.startsWith("$pkg=") }
        strengthText = (others + "$pkg=${strength.name}").joinToString(";")
    }
    val labels = apps.orEmpty().associate { it.packageName to it.label }
    val dailyMs = if (usageOn && point?.hasData == true) point!!.dailyMs else guessMinutes * 60_000L
    val estimate = !(usageOn && point?.hasData == true)

    /** Quiet hours from the answers, until the person drags the dial themselves. */
    fun suggestQuiet() {
        if (quietTouched) return
        val start = when {
            "sleep" in goals || whenKey == "night" -> 23 * 60
            "mornings" in goals || whenKey == "morning" -> 6 * 60
            else -> 22 * 60
        }
        val end = if ("mornings" in goals || whenKey == "morning") 9 * 60 else 7 * 60
        quiet = "$start,$end"
    }

    val previewPkg = preview
    if (previewPkg != null) {
        val dismiss = { preview = null }
        BackHandler(onBack = dismiss)
        OpenWaitScreen(
            appLabel = labels[previewPkg] ?: stringResource(R.string.welcome_sample_app),
            appPackage = previewPkg,
            seconds = SetupPlan.WAIT_SECONDS,
            openedToday = 2,
            closedToday = 0,
            reason = note.trim(),
            onClose = dismiss,
            onOpen = dismiss,
            preview = true
        )
        return
    }

    fun plan(): SetupPlan {
        val (start, end) = quietWindow(quiet)
        return SetupPlan(strengths, adult || "adult" in goals, start, end)
    }
    val accessOn = remember(refresh) { actions.protectionState("accessibility") } == OnboardingProtectionState.ON
    val notificationsOn = remember(refresh) { actions.granted("notifications") }
    // The app to test with: one that closes a feed or the whole app first, then one that waits.
    val testApp = chosen.filter { context.packageManager.getLaunchIntentForPackage(it) != null }
        .sortedBy { if (strengths[it] == SetupPlan.Strength.WAIT) 1 else 0 }
        .firstOrNull()
    val canTest = accessOn && testApp != null
    val worked = testStarted > 0 && remember(refresh) { app.hisn.android.ShieldMoments.last(context) } > testStarted
    fun go(target: Step, ahead: Boolean = true) {
        forward = ahead
        step = target
    }
    fun saveAnswers() = UnrotAnswers.save(context, goals, whenKey, feel, instead)
    fun next() {
        when (step) {
            Step.Hello -> go(Step.Demo1)
            Step.Demo1 -> go(Step.Demo2)
            Step.Demo2 -> go(Step.Demo3)
            Step.Demo3 -> go(Step.Name)
            Step.Name -> go(Step.Age)
            Step.Age -> { UnrotAnswers.saveAge(context, age); go(Step.Goals) }
            Step.Goals -> go(Step.When)
            Step.When -> { suggestQuiet(); go(Step.Feel) }
            Step.Feel -> go(Step.Start)
            Step.Start -> if (!usageOn) actions.request("usage") else go(Step.Year)
            Step.Year -> go(Step.Cost)
            Step.Cost -> go(Step.Tricks)
            Step.Tricks -> go(Step.Hard)
            Step.Hard -> go(Step.Helps)
            Step.Helps -> go(Step.Wave)
            Step.Wave -> go(Step.Lives)
            Step.Lives -> go(Step.Expect)
            Step.Expect -> go(Step.Solve)
            Step.Solve -> go(Step.Instead)
            Step.Instead -> { saveAnswers(); go(Step.Apps) }
            Step.Apps -> go(if (chosen.isEmpty()) Step.Note else Step.Firm)
            Step.Firm -> go(Step.Try)
            Step.Try -> { suggestQuiet(); go(Step.Quiet) }
            Step.Quiet -> go(Step.Note)
            Step.Note -> go(Step.Plan)
            Step.Plan -> {
                // Save before the Android switches: settings screens can take a while, and the
                // plan must not depend on the person coming back to this page.
                saveAnswers()
                actions.savePersonal(name.trim(), "", "")
                saved = actions.applyPlan(plan(), note.trim())
                go(Step.Switches)
            }
            Step.Switches -> go(if (notificationsOn) (if (canTest) Step.Test else Step.Hold) else Step.Notify)
            Step.Notify -> {
                if (!notificationsOn) actions.request("notifications")
                go(if (canTest) Step.Test else Step.Hold)
            }
            Step.Test -> go(Step.Hold)
            Step.Hold -> go(Step.Done)
            Step.Done -> actions.completeSetup()
        }
    }
    fun back() {
        val order = Step.entries
        when (step) {
            Step.Hello -> actions.completeSetup()
            Step.Note -> go(if (chosen.isEmpty()) Step.Apps else Step.Quiet, false)
            Step.Hold -> go(if (canTest) Step.Test else if (notificationsOn) Step.Switches else Step.Notify, false)
            Step.Test -> go(if (notificationsOn) Step.Switches else Step.Notify, false)
            Step.Done -> go(Step.Hold, false)
            else -> go(order[step.ordinal - 1], false)
        }
    }
    BackHandler(onBack = ::back)

    val primary: String? = when (step) {
        Step.Hello -> stringResource(R.string.unrot_setup_start)
        Step.Demo3 -> stringResource(R.string.demo_set_up)
        Step.When -> null
        // The pledge has its own hold button; "Next" appears once the seed is planted.
        Step.Hold -> if (planted) stringResource(R.string.unrot_setup_next) else null
        Step.Name -> stringResource(if (name.isBlank()) R.string.setup_skip_step else R.string.unrot_setup_next)
        Step.Goals -> if (goals.isEmpty()) stringResource(R.string.setup_skip_step)
            else pluralStringResource(R.plurals.goals_next, goals.size, goals.size)
        Step.Start -> stringResource(if (usageOn) R.string.start_next else R.string.start_allow)
        // Numbers → what they cost → why it's hard → what the time back is for.
        Step.Year -> stringResource(R.string.why_year_next)
        Step.Cost -> stringResource(R.string.why_cost_next)
        Step.Hard -> stringResource(R.string.solve_next)
        Step.Solve -> stringResource(R.string.year_next)
        Step.Age -> stringResource(if (age.isBlank()) R.string.setup_skip_step else R.string.unrot_setup_next)
        Step.Apps -> if (chosen.isEmpty()) stringResource(R.string.unrot_setup_next)
            else pluralStringResource(R.plurals.unrot_setup_apps_next, chosen.size, chosen.size)
        Step.Try -> stringResource(R.string.unrot_setup_fair)
        Step.Note -> stringResource(if (note.isBlank()) R.string.unrot_setup_skip_note else R.string.unrot_setup_save_note)
        Step.Plan -> stringResource(R.string.plan_next)
        Step.Notify -> stringResource(R.string.notify_allow)
        Step.Test -> stringResource(if (testStarted > 0) R.string.unrot_setup_test_back else R.string.unrot_setup_test_skip)
        Step.Done -> stringResource(R.string.unrot_setup_go_today)
        else -> stringResource(R.string.unrot_setup_next)
    }
    val secondary: Pair<String, () -> Unit>? = when (step) {
        Step.Start -> if (!usageOn) stringResource(R.string.start_use_guess) to { go(Step.Year) } else null
        Step.Notify -> stringResource(R.string.notify_not_now) to { go(if (canTest) Step.Test else Step.Hold) }
        Step.Hold -> if (planted) null else stringResource(R.string.hold_skip) to { go(Step.Done) }
        else -> null
    }

    if (why) WhySheet(step.name) { why = false }

    // A Surface, not a bare background: it also sets the text colour for light and dark.
    // The finish is a green page: the one place the app celebrates with colour.
    // Only when it really works: "Almost set" stays on the plain page.
    val finish = step == Step.Done && (accessOn || chosen.isEmpty())
    val surface by androidx.compose.animation.animateColorAsState(
        if (finish) UnrotFinishGreen else MaterialTheme.colorScheme.background, label = "finish"
    )
    val glow by androidx.compose.animation.animateColorAsState(stepGlow(step), label = "glow")
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    androidx.compose.material3.Surface(
        color = surface,
        contentColor = if (finish) UnrotFinishPaper else MaterialTheme.colorScheme.onBackground
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .then(if (finish) Modifier else Modifier.drawBehind {
                // A soft wash of the step's colour at the top: atmosphere, not decoration on content.
                drawRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(glow.copy(alpha = if (dark) 0.14f else 0.32f), Color.Transparent), endY = size.height * 0.32f
                    )
                )
            })
            .safeDrawingPadding()
            .imePadding()
    ) {
        if (step == Step.Hello) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = actions::completeSetup) { Text(stringResource(R.string.unrot_setup_skip)) }
            }
        } else if (step != Step.Done) {
            SetupBar(chapter = chapterOf(step), fill = chapterFill(step), onBack = ::back, onWhy = { why = true })
        }
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val sign = if (forward) 1 else -1
                (slideInHorizontally(tween(280)) { sign * it / 6 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(220)) { -sign * it / 8 } + fadeOut(tween(120)))
            },
            label = "setup step"
        ) { shown ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (shown) {
                    Step.Hello -> GlassWelcome(onPreview = { preview = "" })
                    Step.Demo1 -> DemoStep(0)
                    Step.Demo2 -> DemoStep(1)
                    Step.Demo3 -> DemoStep(2)
                    Step.Name -> NameStep(name) { name = it }
                    Step.Goals -> GoalsStep(goals) { key ->
                        goalsText = (if (key in goals) goals - key else goals + key).joinToString(",")
                    }
                    Step.When -> WhenStep(whenKey) { key -> whenKey = key; next() }
                    Step.Feel -> FeelStep(feel) { key ->
                        feelText = (if (key in feel) feel - key else feel + key).joinToString(",")
                    }
                    Step.Start -> StartStep(point, usageOn, guessMinutes, labels) { guessMinutes = it }
                    Step.Year -> YearStep(name.trim(), dailyMs, estimate, age)
                    Step.Cost -> CostStep(point, goals, whenKey, feel, adult || "adult" in goals)
                    Step.Tricks -> TricksStep()
                    Step.Hard -> HardStep()
                    Step.Helps -> HelpsStep()
                    Step.Wave -> WaveStep()
                    Step.Lives -> LivesStep()
                    Step.Expect -> ExpectStep()
                    Step.Solve -> SolveStep()
                    Step.Age -> AgeStep(age) { key -> age = key; next() }
                    Step.Instead -> {
                        // The time back, shown as what grows from it: the sprout under glass.
                        GlassArt(R.drawable.unrot_glass_sprout, 150.dp, stringResource(R.string.obg_sprout_art))
                        InsteadStep(name.trim(), (dailyMs * 365 / 86_400_000L / 2).toInt(), instead) { key ->
                            insteadText = (if (key in instead) instead - key else instead + key).joinToString(",")
                        }
                    }
                    Step.Apps -> AppsStep(apps, chosen, adult, actions, refresh,
                        onToggle = { pkg ->
                            chosenText = (if (pkg in chosen) chosen - pkg else chosen + pkg).joinToString(",")
                        },
                        onAdult = { adult = it })
                    Step.Firm -> FirmStep(chosen.toList(), labels, strengths, ::setStrength)
                    Step.Try -> TryStep(chosen.toList(), labels, strengths) { preview = it }
                    Step.Quiet -> QuietStep(quiet, hint = quietHint(goals, whenKey, point)) { quiet = it; quietTouched = true }
                    Step.Note -> NoteStep(note, noteTitle(point, labels), noteIdeas(feel, instead, quiet)) { note = it }
                    Step.Plan -> PlanStep(name.trim(), planNodes(plan(), labels), planExtra(plan(), note))
                    Step.Switches -> SwitchesStep(actions, refresh, adult || "adult" in goals, saved)
                    Step.Notify -> NotifyStep(note.trim())
                    Step.Test -> TestStep(testApp ?: "", labels[testApp] ?: "", started = testStarted > 0) {
                        testStarted = System.currentTimeMillis()
                        testApp?.let { pkg ->
                            context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) }
                        }
                    }
                    Step.Hold -> PledgeStep(note.trim(), adult || "adult" in goals, planted) { planted = true }
                    Step.Done -> DoneStep(plan(), labels, accessOn = accessOn, worked = worked, name = name.trim(),
                        aimMs = if (estimate) 0L else dailyMs / 2, onGreen = finish)
                }
            }
        }
        secondary?.let { (label, action) ->
            TextButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
        if (primary != null) {
            PillButton(
                text = primary,
                onClick = ::next,
                enabled = !(step == Step.Apps && chosen.isEmpty() && !adult),
                onGreen = finish
            )
        } else {
            Spacer(Modifier.height(16.dp))
        }
    }
    }
}

/**
 * The top bar: back, one thin segment per chapter (done ones full, the current one filling, the
 * rest empty), the chapter's name under it, and "Why are we asking?".
 */
@Composable
private fun SetupBar(chapter: Int, fill: Float, onBack: () -> Unit, onWhy: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val gold = glassGold()
    val name = stringResource(Chapters[chapter])
    val spoken = stringResource(R.string.obg_chapter_progress, chapter + 1, Chapters.size, name)
    val shown by androidx.compose.animation.core.animateFloatAsState(fill, tween(320), label = "chapter fill")
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.unrot_setup_back))
        }
        Column(
            Modifier.weight(1f).clearAndSetSemantics { contentDescription = spoken },
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Chapters.indices.forEach { i ->
                    val part = when {
                        i < chapter -> 1f
                        i == chapter -> shown.coerceIn(0f, 1f)
                        else -> 0f
                    }
                    Box(Modifier.weight(1f).height(4.dp).background(colors.onSurface.copy(alpha = 0.12f), RoundedCornerShape(2.dp))) {
                        if (part > 0f) {
                            Box(Modifier.fillMaxWidth(part).height(4.dp).background(gold, RoundedCornerShape(2.dp)))
                        }
                    }
                }
            }
            Text(
                name.uppercase(appLocale()),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(1.4f, androidx.compose.ui.unit.TextUnitType.Sp)),
                color = colors.onSurfaceVariant,
                maxLines = 1
            )
        }
        // "Why are we asking?": CleanPath's quiz help button, used for every step.
        IconButton(onClick = onWhy) {
            Box(Modifier.size(26.dp).background(colors.onSurface, CircleShape), contentAlignment = Alignment.Center) {
                Text("?", color = colors.surface, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The main button: a pill with an arrow circle, so "forward" always looks the same. */
@Composable
internal fun PillButton(text: String, onClick: () -> Unit, enabled: Boolean, onGreen: Boolean) {
    val haptics = rememberHisnHaptics()
    Button(
        onClick = { haptics.tap(); onClick() },
        enabled = enabled,
        shape = RoundedCornerShape(30.dp),
        colors = if (onGreen) ButtonDefaults.buttonColors(containerColor = UnrotFinishPaper, contentColor = UnrotFinishGreen)
            else ButtonDefaults.buttonColors(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp)
            .heightIn(min = 58.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        val arrowBg = if (onGreen) UnrotFinishGreen else MaterialTheme.colorScheme.onPrimary
        val arrowFg = if (onGreen) UnrotFinishPaper else MaterialTheme.colorScheme.primary
        Box(Modifier.size(42.dp).background(arrowBg, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = arrowFg)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun WhySheet(step: String, onDismiss: () -> Unit) {
    val body = when (step) {
        "Demo1", "Demo2", "Demo3" -> R.string.why_demo
        "Name", "Age" -> R.string.why_name
        "Goals" -> R.string.why_goals
        "When" -> R.string.why_when
        "Feel" -> R.string.why_feel
        "Start", "Year" -> R.string.why_start
        "Cost", "Tricks", "Hard", "Helps", "Wave", "Lives", "Expect", "Solve" -> R.string.why_why
        "Instead" -> R.string.why_instead
        "Apps", "Firm", "Try" -> R.string.why_apps
        "Quiet" -> R.string.why_quiet
        "Note" -> R.string.why_note
        "Switches", "Plan" -> R.string.why_switches
        "Notify" -> R.string.why_notify
        "Test" -> R.string.why_test
        else -> R.string.why_hold
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.why_title), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.setup_answers_private), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.explain_got_it)) }
        }
    }
}

private fun stepGlow(step: Step): Color = when (step) {
    Step.Hello, Step.Demo1, Step.Apps, Step.Try, Step.Notify, Step.Hold -> SetupButter
    Step.Demo2, Step.Goals, Step.When, Step.Quiet, Step.Cost -> SetupSky
    Step.Hard -> SetupButter
    Step.Tricks -> SetupSky
    Step.Helps -> SetupSage
    Step.Wave -> SetupSky
    Step.Lives -> SetupSage
    Step.Expect -> SetupButter
    Step.Demo3, Step.Start, Step.Year, Step.Note, Step.Feel, Step.Test -> SetupBlush
    Step.Name, Step.Instead, Step.Firm, Step.Plan, Step.Switches -> SetupSage
    else -> Color.Transparent
}

@Composable
private fun quietHint(goals: Set<String>, whenKey: String, point: StartingPoint?): String? {
    val hour = point?.hardestHour?.takeIf { it >= 0 }
    // The night window only "comes from" the hardest hour when that hour is itself at night.
    val nightHour = hour?.takeIf { it >= 20 || it <= 3 }
    return when {
        whenKey == "night" && nightHour != null -> stringResource(R.string.quiet_hint_night_hour, setupHour(nightHour))
        whenKey == "night" || "sleep" in goals -> stringResource(R.string.quiet_hint_night)
        whenKey == "morning" || "mornings" in goals -> stringResource(R.string.quiet_hint_morning)
        hour != null -> stringResource(R.string.quiet_hint_hour, setupHour(hour))
        else -> null
    }
}

@Composable
private fun noteTitle(point: StartingPoint?, labels: Map<String, String>): String? {
    val hour = point?.hardestHour?.takeIf { it >= 0 } ?: return null
    val app = point.apps.firstOrNull()?.first?.let { labels[it] } ?: return null
    return stringResource(R.string.note_title_moment, String.format(java.util.Locale.ROOT, "%02d:40", hour), app)
}

/** Note ideas from the answers: how scrolling feels, what they would do instead, when quiet starts. */
@Composable
private fun noteIdeas(feel: Set<String>, instead: Set<String>, quiet: String): List<String> {
    val ideas = mutableListOf<String>()
    if ("tired" in feel) ideas += stringResource(R.string.note_idea_tired)
    if ("behind" in feel) ideas += stringResource(R.string.note_idea_behind)
    if ("read" in instead) ideas += stringResource(R.string.note_idea_read)
    if ("friends" in instead) ideas += stringResource(R.string.note_idea_friends)
    if ("sleep" in instead) ideas += stringResource(R.string.note_idea_sleep)
    val (start, _) = quietWindow(quiet)
    if (start >= 0) ideas += stringResource(R.string.note_idea_said, minuteLabel(start))
    if (ideas.size < 3) {
        ideas += stringResource(R.string.unrot_setup_note_idea_1)
        ideas += stringResource(R.string.unrot_setup_note_idea_2)
    }
    return ideas.distinct().take(4)
}

@Composable
private fun planNodes(plan: SetupPlan, labels: Map<String, String>): List<Pair<String, String>> {
    val nodes = plan.apps.entries.take(if (plan.quietHours()) 3 else 4).map { (pkg, strength) ->
        (labels[pkg] ?: pkg) to when (strength) {
            SetupPlan.Strength.WAIT -> stringResource(R.string.plan_node_wait, SetupPlan.WAIT_SECONDS)
            SetupPlan.Strength.LIMIT -> stringResource(R.string.plan_node_limit, SetupPlan.LIMIT_MINUTES)
            else -> stringResource(R.string.plan_node_close)
        }
    }.toMutableList()
    if (plan.quietHours()) {
        nodes += stringResource(R.string.plan_node_quiet, minuteLabel(plan.quietStart), minuteLabel(plan.quietEnd)) to
            stringResource(R.string.plan_node_quiet_body)
    }
    return nodes
}

@Composable
private fun planExtra(plan: SetupPlan, note: String): String? {
    val parts = mutableListOf<String>()
    if (plan.adult) parts += stringResource(R.string.plan_extra_adult)
    if (note.isNotBlank()) parts += stringResource(R.string.plan_extra_note)
    return if (parts.isEmpty()) null else "+ " + parts.joinToString(" · ")
}

@Composable
private fun StepTitle(title: Int, body: Int? = null, bodyText: String? = null, titleText: String? = null, eyebrow: Int? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (eyebrow != null) {
            Text(stringResource(eyebrow).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(1.6f, androidx.compose.ui.unit.TextUnitType.Sp)),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            titleText ?: stringResource(title),
            style = MaterialTheme.typography.headlineLarge.storySerif(),
            modifier = Modifier.semantics { heading() }
        )
        val text = bodyText ?: body?.let { stringResource(it) }
        if (text != null) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A launchable app with today's use, and whether setup suggests it. */
private data class SetupApp(val packageName: String, val label: String, val usedMs: Long, val known: Boolean, val suggested: Boolean)

/**
 * Feed apps first, then the rest by today's minutes. Known apps (short-video feeds and the common
 * social apps) are suggested only when they have a feed: a messaging-heavy app is the person's
 * call, never a preset.
 */
private fun loadSetupApps(context: android.content.Context): List<SetupApp> {
    val usage = if (Usage.hasAccess(context)) Usage.usedTodayAll(context) else emptyMap()
    val social = SocialApps.common().map { it.packageName }.toSet()
    // Android's own Settings is never something to block or slow down.
    val settings = setOf("com.android.settings")
    return installedApps(context).filter { it.packageName !in settings }.map { app ->
        val feed = SetupPlan.hasFeed(app.packageName)
        SetupApp(app.packageName, app.label, usage[app.packageName] ?: 0L,
            known = feed || app.packageName in social, suggested = feed)
    }.sortedWith(
        compareByDescending<SetupApp> { it.known }
            .thenByDescending { it.usedMs }
            .thenBy { it.label.lowercase() }
    )
}

@Composable
private fun AppsStep(
    apps: List<SetupApp>?,
    chosen: Set<String>,
    adult: Boolean,
    actions: UnrotSetupActions,
    refresh: Int,
    onToggle: (String) -> Unit,
    onAdult: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val usageOn = remember(refresh) { Usage.hasAccess(context) }
    var all by rememberSaveable { mutableStateOf(false) }
    StepTitle(
        R.string.unrot_setup_apps_title,
        if (usageOn) R.string.unrot_setup_apps_body_usage else R.string.unrot_setup_apps_body,
        eyebrow = R.string.eyebrow_apps
    )
    if (!usageOn) {
        FilledTonalButton(onClick = { actions.request("usage") }) {
            Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.unrot_setup_show_minutes))
        }
    }
    if (apps == null) return
    val top = apps.maxOfOrNull { it.usedMs }?.coerceAtLeast(1L) ?: 1L
    val shown = if (all) apps else apps.filter { it.known || it.usedMs > 0 || it.packageName in chosen }.take(10)
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier
            .fillMaxWidth()
            .unrotFrame(shape)
            .background(LocalHisnStatusColors.current.card, shape)
    ) {
        shown.forEachIndexed { index, app ->
            if (index > 0) InsetDivider(start = 68.dp)
            AppChoiceRow(app, app.packageName in chosen, top, usageOn) { onToggle(app.packageName) }
        }
    }
    if (!all && apps.size > shown.size) {
        TextButton(onClick = { all = true }) {
            Text(pluralStringResource(R.plurals.unrot_setup_all_apps, apps.size, apps.size))
        }
    }
    val cardShape = MaterialTheme.shapes.large
    Row(
        Modifier
            .fillMaxWidth()
            .unrotFrame(cardShape)
            .background(LocalHisnStatusColors.current.card, cardShape)
            .toggleable(value = adult, role = Role.Switch, onValueChange = onAdult)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.unrot_setup_adult_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.unrot_setup_adult_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = adult, onCheckedChange = null)
    }
}

@Composable
private fun AppChoiceRow(app: SetupApp, checked: Boolean, top: Long, usageOn: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppIcon(app.packageName, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (usageOn) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(colors.onSurface.copy(alpha = 0.08f), RoundedCornerShape(3.dp))
                ) {
                    val share = (app.usedMs.toFloat() / top).coerceIn(0f, 1f)
                    if (share > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(share)
                                .height(6.dp)
                                .background(if (checked) colors.tertiary else colors.outline, RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
        if (usageOn) {
            Text(
                if (app.usedMs > 0) Usage.formatDuration(app.usedMs) else "—",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant
            )
        }
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun FirmStep(
    apps: List<String>,
    labels: Map<String, String>,
    strengths: Map<String, SetupPlan.Strength>,
    onStrength: (String, SetupPlan.Strength) -> Unit
) {
    StepTitle(R.string.unrot_setup_firm_title, R.string.unrot_setup_firm_body, eyebrow = R.string.eyebrow_firm)
    apps.forEach { pkg ->
        val strength = strengths[pkg] ?: SetupPlan.suggested(pkg)
        val shape = MaterialTheme.shapes.large
        Column(
            Modifier
                .fillMaxWidth()
                .unrotFrame(shape)
                .background(LocalHisnStatusColors.current.card, shape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(pkg, size = 32.dp)
                Text(labels[pkg] ?: pkg, style = MaterialTheme.typography.titleMedium)
            }
            StrengthSwitch(pkg, strength) { onStrength(pkg, it) }
            Text(
                strengthSentence(pkg, strength),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun strengthSentence(pkg: String, strength: SetupPlan.Strength): String = when (strength) {
    SetupPlan.Strength.WAIT -> stringResource(R.string.unrot_setup_wait_line, SetupPlan.WAIT_SECONDS)
    SetupPlan.Strength.LIMIT -> stringResource(R.string.unrot_setup_limit_line, SetupPlan.LIMIT_MINUTES)
    SetupPlan.Strength.CLOSE -> stringResource(
        when {
            SetupPlan.closesFeedOnly(pkg) -> R.string.unrot_setup_close_feed_line
            SetupPlan.hasFeed(pkg) -> R.string.unrot_setup_close_whole_feed_line
            else -> R.string.unrot_setup_block_line
        }
    )
}

@Composable
private fun StrengthSwitch(pkg: String, strength: SetupPlan.Strength, onChoose: (SetupPlan.Strength) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val close = if (SetupPlan.hasFeed(pkg)) R.string.unrot_setup_close_feed else R.string.unrot_setup_block
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.5.dp, colors.onSurface, shape)
            .selectableGroup()
    ) {
        listOf(
            SetupPlan.Strength.WAIT to R.string.unrot_setup_wait,
            SetupPlan.Strength.LIMIT to R.string.unrot_setup_limit,
            SetupPlan.Strength.CLOSE to close
        ).forEachIndexed { index, (option, label) ->
            val selected = option == strength
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .background(
                        if (selected) colors.onSurface else androidx.compose.ui.graphics.Color.Transparent,
                        when (index) {
                            0 -> RoundedCornerShape(topStart = 13.dp, bottomStart = 13.dp)
                            2 -> RoundedCornerShape(topEnd = 13.dp, bottomEnd = 13.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                    )
                    .selectable(selected = selected, role = Role.RadioButton) { onChoose(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) colors.surface else colors.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TryStep(
    apps: List<String>,
    labels: Map<String, String>,
    strengths: Map<String, SetupPlan.Strength>,
    onPreview: (String) -> Unit
) {
    StepTitle(R.string.unrot_setup_try_title, R.string.unrot_setup_try_body, eyebrow = R.string.eyebrow_try)
    // Apps set to wait first: that is the screen they will really meet.
    val ordered = apps.sortedBy { if (strengths[it] == SetupPlan.Strength.WAIT) 0 else 1 }
    TryLive(ordered, labels, onFullScreen = onPreview)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuietStep(selected: String, hint: String? = null, onSelect: (String) -> Unit) {
    StepTitle(R.string.unrot_setup_quiet_title, R.string.unrot_setup_quiet_body, eyebrow = R.string.eyebrow_quiet)
    val (start, end) = quietWindow(selected)
    val off = start < 0
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.alpha(if (off) 0.35f else 1f)) {
            QuietDial(
                start = if (off) 22 * 60 else start,
                end = if (off) 7 * 60 else end,
                onChange = { s, e -> onSelect("$s,$e") }
            )
        }
    }
    if (hint != null && !off) {
        Text(
            "☾ " + hint,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(chosenFill(), RoundedCornerShape(12.dp)).padding(8.dp)
        )
    }
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuietPresets.forEach { (label, s, e) ->
            androidx.compose.material3.FilterChip(
                selected = start == s && end == e,
                onClick = { onSelect("$s,$e") },
                label = { Text(stringResource(label)) }
            )
        }
        androidx.compose.material3.FilterChip(
            selected = off,
            onClick = { onSelect("none") },
            label = { Text(stringResource(R.string.unrot_setup_quiet_none)) }
        )
    }
    Text(
        stringResource(if (off) R.string.unrot_setup_quiet_none_detail else R.string.unrot_setup_quiet_always),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ChoiceCard(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = rememberHisnHaptics()
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier
            .fillMaxWidth()
            .unrotFrame(shape, hero = selected)
            .background(if (selected) colors.primaryContainer else LocalHisnStatusColors.current.card, shape)
            .selectable(selected = selected, role = Role.RadioButton) { haptics.tick(); onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = if (selected) colors.onPrimaryContainer else colors.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimaryContainer)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteStep(note: String, title: String?, ideas: List<String>, onNote: (String) -> Unit) {
    if (title != null) StepTitle(R.string.unrot_setup_note_title, R.string.unrot_setup_note_body, titleText = title, eyebrow = R.string.eyebrow_note)
    else StepTitle(R.string.unrot_setup_note_title, R.string.unrot_setup_note_body, eyebrow = R.string.eyebrow_note)
    OutlinedTextField(
        value = note,
        onValueChange = { if (it.length <= 120) onNote(it) },
        label = { Text(stringResource(R.string.unrot_setup_note_label)) },
        minLines = 3,
        textStyle = MaterialTheme.typography.titleLarge.storySerif(),
        modifier = Modifier.fillMaxWidth()
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ideas.forEach { text ->
            FilledTonalButton(onClick = { onNote(text) }) { Text(text) }
        }
    }
    Text(
        stringResource(R.string.unrot_setup_note_private),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SwitchesStep(actions: UnrotSetupActions, refresh: Int, adult: Boolean, saved: Boolean) {
    StepTitle(R.string.unrot_setup_switches_title, R.string.unrot_setup_switches_body, eyebrow = R.string.eyebrow_switches)
    if (!saved) {
        Text(
            stringResource(R.string.unrot_setup_not_saved),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
    val access = remember(refresh) { actions.protectionState("accessibility") }
    SwitchCard(
        icon = Icons.Rounded.Shield,
        title = R.string.unrot_setup_switch_apps,
        body = R.string.unrot_setup_switch_apps_body,
        state = access,
        onTurnOn = { actions.request("accessibility") }
    )
    if (adult) {
        val dns = remember(refresh) { actions.protectionState("dns") }
        SwitchCard(
            icon = Icons.Rounded.Language,
            title = R.string.unrot_setup_switch_sites,
            body = R.string.unrot_setup_switch_sites_body,
            state = dns,
            onTurnOn = { actions.request("dns") }
        )
        val tags = remember(refresh) { actions.checkOn("tags") }
        SwitchCard(
            icon = Icons.Rounded.Visibility,
            title = R.string.unrot_setup_switch_words,
            body = R.string.unrot_setup_switch_words_body,
            state = if (tags) OnboardingProtectionState.ON else OnboardingProtectionState.OFF,
            onTurnOn = { actions.setCheck("tags", true) }
        )
    }
}

@Composable
private fun SwitchCard(
    icon: ImageVector,
    title: Int,
    body: Int,
    state: OnboardingProtectionState,
    onTurnOn: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val status = LocalHisnStatusColors.current
    val shape = MaterialTheme.shapes.large
    val on = state == OnboardingProtectionState.ON
    Row(
        Modifier
            .fillMaxWidth()
            .unrotFrame(shape)
            .background(status.card, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(if (on) status.successContainer else colors.secondaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (on) Icons.Rounded.Check else icon, contentDescription = null,
                tint = if (on) status.onSuccessContainer else colors.onSecondaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        when (state) {
            OnboardingProtectionState.ON -> Text(stringResource(R.string.unrot_setup_on),
                style = MaterialTheme.typography.labelLarge, color = status.success)
            OnboardingProtectionState.STARTING -> Text(stringResource(R.string.unrot_setup_starting),
                style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            else -> FilledTonalButton(onClick = onTurnOn) { Text(stringResource(R.string.unrot_setup_turn_on)) }
        }
    }
}

@Composable
private fun ColumnScope.DoneStep(
    plan: SetupPlan,
    labels: Map<String, String>,
    accessOn: Boolean,
    worked: Boolean,
    name: String = "",
    aimMs: Long = 0L,
    onGreen: Boolean = false
) {
    if (accessOn) {
        // CleanPath's "your 90 days start now" arch, made honest: the real first milestone, dated.
        FirstWeekArch(onGreen)
        // The reward, made visible: a gold trophy pops in under the arch, with a celebration buzz.
        val celebrate = rememberHisnHaptics()
        androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(350); celebrate.celebrate() }
        Box(Modifier.align(Alignment.CenterHorizontally).popIn()) {
            Float3D(app.hisn.android.R.drawable.unrot_3d_trophy, 150.dp)
        }
        val dateLocale = appLocale()
        val date = remember(dateLocale) {
            java.time.LocalDate.now().plusDays(6).format(
                java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", dateLocale))
        }
        Text(
            if (name.isBlank()) stringResource(R.string.finish_title) else stringResource(R.string.finish_title_named, name),
            style = MaterialTheme.typography.headlineMedium.storySerif(),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() }
        )
        Text(
            stringResource(R.string.finish_date, date),
            style = MaterialTheme.typography.labelLarge,
            color = if (onGreen) UnrotFinishGreen else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally)
                .background(if (onGreen) UnrotFinishPaper else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        )
        if (aimMs > 0) {
            Text(stringResource(R.string.finish_aim, Usage.formatDuration(aimMs)), style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
    if (!accessOn) Spacer(Modifier.height(24.dp))
    if (!accessOn) Box(
        Modifier
            .align(Alignment.CenterHorizontally)
            .size(96.dp)
            .background(androidx.compose.ui.graphics.Color(0xFFF4C34E), RoundedCornerShape(48.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = UnrotInk, modifier = Modifier.size(44.dp))
    }
    // Honest ending: without app protection nothing chosen here can work yet, so say so.
    if (!accessOn || worked) Text(
        stringResource(
            when {
                worked -> R.string.unrot_setup_done_worked
                accessOn -> R.string.unrot_setup_done_title
                else -> R.string.unrot_setup_done_almost
            }
        ),
        style = MaterialTheme.typography.displaySmall.storySerif(),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { heading() }
    )
    if (!accessOn && plan.apps.isNotEmpty()) {
        Text(
            stringResource(R.string.unrot_setup_done_needs_access),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier
            .fillMaxWidth()
            .unrotFrame(shape)
            .background(LocalHisnStatusColors.current.card, shape)
    ) {
      // The card keeps its own ink on the green page.
      androidx.compose.runtime.CompositionLocalProvider(
          androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface
      ) {
        val lines = buildList {
            plan.apps.forEach { (pkg, strength) ->
                add((labels[pkg] ?: pkg) to strength.let { it to pkg })
            }
        }
        lines.forEachIndexed { index, (label, pair) ->
            if (index > 0) InsetDivider(start = 64.dp)
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box {
                    Float3D(when (pair.first) {
                        SetupPlan.Strength.WAIT -> app.hisn.android.R.drawable.unrot_3d_pause
                        SetupPlan.Strength.LIMIT -> app.hisn.android.R.drawable.unrot_3d_battery
                        SetupPlan.Strength.CLOSE -> app.hisn.android.R.drawable.unrot_3d_mobile
                    }, 48.dp)
                    Box(Modifier.align(Alignment.BottomEnd)) { AppIcon(pair.second, size = 20.dp) }
                }
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    Text(strengthSentence(pair.second, pair.first), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (plan.quietHours()) {
            if (lines.isNotEmpty()) InsetDivider(start = 74.dp)
            DonePlanRow(app.hisn.android.R.drawable.unrot_3d_moon, stringResource(R.string.shield_row_quiet),
                stringResource(R.string.done_quiet_window, minuteLabel(plan.quietStart), minuteLabel(plan.quietEnd)),
                stringResource(R.string.unrot_setup_done_quiet, minuteLabel(plan.quietStart), minuteLabel(plan.quietEnd)))
        }
        if (plan.adult) {
            if (lines.isNotEmpty() || plan.quietHours()) InsetDivider(start = 74.dp)
            DonePlanRow(app.hisn.android.R.drawable.unrot_3d_shield, stringResource(R.string.done_adult_title),
                stringResource(R.string.done_adult_body), stringResource(R.string.unrot_setup_done_adult))
        }
      }
    }
    if (!worked) {
        Text(
            stringResource(R.string.unrot_setup_done_test),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Premium finish: Night gold's near-black (the name stays for its call sites).
internal val UnrotFinishGreen = androidx.compose.ui.graphics.Color(0xFF17150F)
internal val UnrotFinishPaper = androidx.compose.ui.graphics.Color(0xFFF7F2E8)

/** Step 8: leave, meet the closed feed for real, come back. Proof beats promises. */
@Composable
private fun ColumnScope.TestStep(pkg: String, label: String, started: Boolean, onOpen: () -> Unit) {
    Spacer(Modifier.height(32.dp))
    Box(Modifier.align(Alignment.CenterHorizontally)) { AppIcon(pkg, size = 88.dp) }
    Text(
        stringResource(R.string.unrot_setup_test_title, label),
        style = MaterialTheme.typography.displaySmall.storySerif(),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { heading() }
    )
    Text(
        stringResource(if (started) R.string.unrot_setup_test_waiting else R.string.unrot_setup_test_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = onOpen,
        colors = ButtonDefaults.buttonColors(
            containerColor = androidx.compose.ui.graphics.Color(0xFFF4C34E),
            contentColor = UnrotInk
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) { Text(stringResource(R.string.unrot_setup_test_open, label), style = MaterialTheme.typography.titleMedium) }
}

private fun minuteLabel(minute: Int): String =
    String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

/** One line of the finished plan: a floating 3D object, what it is, and how it is set. */
@Composable
private fun DonePlanRow(art: Int, title: String, detail: String, spoken: String) {
    Row(
        Modifier.fillMaxWidth().padding(14.dp).semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Float3D(art, 48.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// The Glasshouse pages: painted glass art, the four tricks, what helps, and the pledge.

/** One of the painted glass pictures, centred, round-clipped so its dark corners melt into the page. */
@Composable
private fun ColumnScope.GlassArt(art: Int, size: androidx.compose.ui.unit.Dp, description: String?) {
    androidx.compose.foundation.Image(
        androidx.compose.ui.res.painterResource(art),
        contentDescription = description,
        modifier = Modifier.align(Alignment.CenterHorizontally).size(size).clip(CircleShape)
    )
}

/**
 * The first screen: the seed under glass, what Unrot is in one line, and three plain promises.
 * The scaffold owns the "Set it up" button; the app-pause preview stays one tap away.
 */
@Composable
private fun ColumnScope.GlassWelcome(onPreview: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    GlassArt(R.drawable.unrot_glass_seed, 190.dp, stringResource(R.string.obg_welcome_art))
    Text(
        stringResource(R.string.unrot_welcome_what).uppercase(appLocale()),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(1.4f, androidx.compose.ui.unit.TextUnitType.Sp)),
        color = glassGold(),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        stringResource(R.string.unrot_welcome_title),
        style = MaterialTheme.typography.displaySmall.storySerif(),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { heading() }
    )
    Text(
        stringResource(R.string.unrot_welcome_body),
        style = MaterialTheme.typography.bodyLarge,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
    )
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WelcomePromise(R.drawable.unrot_3d_mobile, R.string.unrot_promise_shorts, R.string.unrot_promise_shorts_detail)
        WelcomePromise(R.drawable.unrot_3d_pause, R.string.unrot_promise_pause, R.string.unrot_promise_pause_detail)
        WelcomePromise(R.drawable.unrot_3d_shield, R.string.unrot_promise_adult, R.string.unrot_promise_adult_detail)
    }
    TextButton(onClick = onPreview, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
        Icon(Icons.Rounded.HourglassTop, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.welcome_preview_pause))
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Text(stringResource(R.string.welcome_privacy_line), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun WelcomePromise(art: Int, title: Int, detail: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Float3D(art, 40.dp)
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A numbered row on the tricks and helps pages: a gold mark, a short title, two plain lines. */
@Composable
private fun GlassRow(icon: ImageVector, title: Int, body: Int, extra: (@Composable () -> Unit)? = null) {
    val shape = MaterialTheme.shapes.large
    val gold = glassGold()
    Row(
        Modifier
            .fillMaxWidth()
            .unrotFrame(shape)
            .background(LocalHisnStatusColors.current.card, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(40.dp).background(gold.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = gold, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        extra?.invoke()
    }
}

/** Why it's hard: the four tricks feeds use. Naming them takes away some of their pull. */
@Composable
private fun TricksStep() {
    SetupHeader(
        stringResource(R.string.obg_tricks_eyebrow), stringResource(R.string.obg_tricks_title),
        stringResource(R.string.obg_tricks_title_mark), stringResource(R.string.obg_tricks_body)
    )
    GlassRow(Icons.Rounded.KeyboardDoubleArrowDown, R.string.obg_trick_scroll_title, R.string.obg_trick_scroll_body)
    GlassRow(Icons.Rounded.Casino, R.string.obg_trick_reward_title, R.string.obg_trick_reward_body)
    GlassRow(Icons.Rounded.PlayCircle, R.string.obg_trick_autoplay_title, R.string.obg_trick_autoplay_body)
    GlassRow(Icons.Rounded.NotificationsActive, R.string.obg_trick_notify_title, R.string.obg_trick_notify_body)
    Text(
        stringResource(R.string.obg_tricks_foot),
        style = MaterialTheme.typography.titleMedium.storySerif(),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/** An urge is a wave: it rises, peaks and passes if you wait it out (urge surfing, Marlatt). */
@Composable
private fun WaveStep() {
    SetupHeader(
        stringResource(R.string.obg_wave_eyebrow), stringResource(R.string.obg_wave_title),
        stringResource(R.string.obg_wave_title_mark), stringResource(R.string.obg_wave_body)
    )
    val gold = glassGold()
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val desc = stringResource(R.string.obg_wave_art)
    val rises = stringResource(R.string.obg_wave_rises)
    val peaks = stringResource(R.string.obg_wave_peaks)
    val passes = stringResource(R.string.obg_wave_passes)
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier.fillMaxWidth().unrotFrame(shape, hero = true).background(LocalHisnStatusColors.current.card, shape)
            .padding(16.dp).clearAndSetSemantics { contentDescription = desc },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val w = size.width; val h = size.height
            // The baseline: where the person waits.
            drawLine(line.copy(alpha = .35f), androidx.compose.ui.geometry.Offset(0f, h * .85f),
                androidx.compose.ui.geometry.Offset(w, h * .85f), strokeWidth = 2f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, h * .85f)
                cubicTo(w * .22f, h * .84f, w * .30f, h * .08f, w * .45f, h * .08f)
                cubicTo(w * .60f, h * .08f, w * .70f, h * .82f, w, h * .85f)
            }
            val fill = androidx.compose.ui.graphics.Path().apply { addPath(path); lineTo(w, h * .85f); lineTo(0f, h * .85f); close() }
            drawPath(fill, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(gold.copy(alpha = .35f), gold.copy(alpha = .02f))))
            drawPath(path, gold, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawCircle(gold, 9f, androidx.compose.ui.geometry.Offset(w * .45f, h * .08f))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(rises, style = MaterialTheme.typography.labelMedium, color = line, modifier = Modifier.weight(1f))
            Text(peaks, style = MaterialTheme.typography.labelMedium, color = gold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text(passes, style = MaterialTheme.typography.labelMedium, color = line, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
    Text(stringResource(R.string.obg_wave_line), style = MaterialTheme.typography.titleMedium.storySerif(),
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.obg_wave_source), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

/** The sprout's rules before they ever happen: it grows, it cracks, it breaks, and what each means. */
@Composable
private fun LivesStep() {
    SetupHeader(
        stringResource(R.string.obg_lives_eyebrow), stringResource(R.string.obg_lives_title),
        stringResource(R.string.obg_lives_title_mark), stringResource(R.string.obg_lives_body)
    )
    LifeRow(R.drawable.unrot_glass_sprout, R.string.obg_lives_grows, R.string.obg_lives_grows_body)
    LifeRow(R.drawable.unrot_glass_cracked, R.string.obg_lives_cracks, R.string.obg_lives_cracks_body)
    LifeRow(R.drawable.unrot_glass_broken, R.string.obg_lives_breaks, R.string.obg_lives_breaks_body)
}

@Composable
private fun LifeRow(art: Int, title: Int, body: Int) {
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier.fillMaxWidth().unrotFrame(shape).background(LocalHisnStatusColors.current.card, shape)
            .padding(12.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(art), contentDescription = null,
            modifier = Modifier.size(76.dp).clip(CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = glassGold())
            Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** What to expect, honestly: what many people notice week by week, including the boring part. */
@Composable
private fun ExpectStep() {
    SetupHeader(
        stringResource(R.string.obg_expect_eyebrow), stringResource(R.string.obg_expect_title),
        stringResource(R.string.obg_expect_title_mark), stringResource(R.string.obg_expect_body)
    )
    val gold = glassGold()
    listOf(
        R.string.obg_expect_1 to R.string.obg_expect_1_body,
        R.string.obg_expect_2 to R.string.obg_expect_2_body,
        R.string.obg_expect_3 to R.string.obg_expect_3_body,
        R.string.obg_expect_4 to R.string.obg_expect_4_body
    ).forEachIndexed { i, (when_, body) ->
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(14.dp).background(if (i == 0) gold else gold.copy(alpha = .35f), CircleShape))
                if (i < 3) Box(Modifier.padding(top = 4.dp).width(2.dp).height(58.dp).background(gold.copy(alpha = .25f)))
            }
            Column(Modifier.weight(1f).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(when_), style = MaterialTheme.typography.labelLarge, color = gold)
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** What helps: how Unrot works, in four parts, ending on the glass that cracks and heals gold. */
@Composable
private fun HelpsStep() {
    SetupHeader(
        stringResource(R.string.obg_helps_eyebrow), stringResource(R.string.obg_helps_title),
        stringResource(R.string.obg_helps_title_mark), stringResource(R.string.obg_helps_body)
    )
    GlassRow(Icons.Rounded.VideocamOff, R.string.obg_help_feeds_title, R.string.obg_help_feeds_body)
    GlassRow(Icons.Rounded.HourglassTop, R.string.obg_help_pause_title, R.string.obg_help_pause_body)
    GlassRow(Icons.Rounded.Bedtime, R.string.obg_help_quiet_title, R.string.obg_help_quiet_body)
    // The glass: the cracked dome, then the same dome sealed with gold after Breathe.
    val shape = MaterialTheme.shapes.large
    val gold = glassGold()
    val art = stringResource(R.string.obg_help_glass_art)
    Column(
        Modifier
            .fillMaxWidth()
            .unrotFrame(shape, hero = true)
            .background(LocalHisnStatusColors.current.card, shape)
            .padding(14.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = art },
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(R.drawable.unrot_glass_cracked), contentDescription = null,
                modifier = Modifier.size(96.dp).clip(CircleShape)
            )
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = gold)
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(R.drawable.unrot_glass_gold), contentDescription = null,
                modifier = Modifier.size(96.dp).clip(CircleShape)
            )
        }
        Text(stringResource(R.string.obg_help_glass_title), style = MaterialTheme.typography.titleMedium, color = gold)
        Text(stringResource(R.string.obg_help_glass_body), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(
        stringResource(R.string.obg_helps_foot),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * The pledge, near the end: three short promises (adult content only when it is part of the
 * plan), the person's own note, and a gold button held for three seconds while a ring fills.
 * Once it is full the seed is planted and the scaffold shows "Next". It locks nothing, and
 * "Skip, I'll just start" stays until then. TalkBack gets a plain click action instead of a hold.
 */
@Composable
private fun ColumnScope.PledgeStep(note: String, adult: Boolean, planted: Boolean, onPlanted: () -> Unit) {
    val haptics = rememberHisnHaptics()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val progress = remember { androidx.compose.animation.core.Animatable(if (planted) 1f else 0f) }
    var holding by remember { mutableStateOf(false) }
    val view = androidx.compose.ui.platform.LocalView.current
    val gold = Color(0xFFF2C14E)
    val goldInk = Color(0xFF2A1C02)
    SetupHeader(
        stringResource(R.string.obg_pledge_eyebrow), stringResource(R.string.obg_pledge_title),
        stringResource(R.string.obg_pledge_title_mark), stringResource(R.string.obg_pledge_body)
    )
    val lines = buildList {
        add(R.string.obg_pledge_pause)
        if (adult) add(R.string.obg_pledge_adult)
        add(R.string.obg_pledge_kind)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { line ->
            val shape = MaterialTheme.shapes.large
            Row(
                Modifier.fillMaxWidth().unrotFrame(shape).background(LocalHisnStatusColors.current.card, shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = glassGold(), modifier = Modifier.size(20.dp))
                Text(stringResource(line), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    if (note.isNotBlank()) {
        Text(stringResource(R.string.personal_quote, note), style = MaterialTheme.typography.titleLarge.storySerif(),
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(4.dp))
    val label = stringResource(R.string.obg_pledge_hold)
    val plantedText = stringResource(R.string.obg_pledge_planted)
    fun finish() {
        if (planted) return
        haptics.celebrate()
        onPlanted()
    }
    Box(
        Modifier
            .align(Alignment.CenterHorizontally)
            .size(184.dp)
            .clearAndSetSemantics {
                contentDescription = if (planted) plantedText else label
                role = Role.Button
                if (!planted) onClick(label) { scope.launch { progress.snapTo(1f) }; finish(); true }
            }
            .pointerInput(planted) {
                if (planted) return@pointerInput
                detectTapGestures(onPress = {
                    holding = true
                    val job = scope.launch {
                        // Real time, so "remove animations" does not skip the three seconds.
                        val start = android.os.SystemClock.uptimeMillis() - (progress.value * 3000).toLong()
                        var beat = -1
                        while (progress.value < 1f) {
                            val p = ((android.os.SystemClock.uptimeMillis() - start) / 3000f).coerceAtMost(1f)
                            progress.snapTo(p)
                            // A building pulse, faster and firmer as the ring fills.
                            val step = (p * p * 24).toInt()
                            if (step > beat) { beat = step; UnrotFeel.pulse(view, p) }
                            kotlinx.coroutines.delay(16)
                        }
                        finish()
                    }
                    tryAwaitRelease()
                    holding = false
                    if (progress.value < 1f) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(250, easing = androidx.compose.animation.core.LinearEasing)) }
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val s = 8.dp.toPx()
            drawCircle(track, size.minDimension / 2 - s / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(s))
            drawArc(
                gold, -90f, 360f * progress.value, false,
                androidx.compose.ui.geometry.Offset(s / 2, s / 2),
                androidx.compose.ui.geometry.Size(size.width - s, size.height - s),
                style = androidx.compose.ui.graphics.drawscope.Stroke(s, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
        if (planted) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(R.drawable.unrot_glass_sprout), contentDescription = null,
                modifier = Modifier.size(150.dp).clip(CircleShape).popIn()
            )
        } else {
            Box(
                Modifier.size(140.dp).background(gold, CircleShape).padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = goldInk, textAlign = TextAlign.Center)
            }
        }
    }
    Text(
        when {
            planted -> plantedText
            holding -> stringResource(R.string.obg_pledge_keep)
            else -> " "
        },
        style = if (planted) MaterialTheme.typography.titleLarge.storySerif() else MaterialTheme.typography.bodySmall,
        color = if (planted) glassGold() else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}
