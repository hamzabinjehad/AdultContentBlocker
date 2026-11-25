package app.hisn.android

import app.hisn.android.nativeshell.arabicDigitsContext
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.edit
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.hisn.android.nativeshell.HisnTheme
import app.hisn.android.nativeshell.rememberHisnHaptics
import app.hisn.android.nativeshell.storySerif
import app.hisn.android.nativeshell.systemReduceMotion
import app.hisn.core.BreathPattern

/**
 * Unrot's voluntary breathing pause, opened from Today, the launcher shortcut and the Quick
 * Settings tile. Nothing is blocked here: the person asked for a moment.
 *
 * Built on two findings rather than on taste:
 *  - Cyclic sighing (in, a short top-up, a long slow out) calmed people more than other breathing
 *    patterns or meditation in a randomized study (Balban et al., 2023). It is the default; the
 *    old even 4-in, 6-out stays one tap away.
 *  - In the one sec field study (Grüning et al., PNAS 2023) the clear option to walk away did the
 *    most and a reflective message did nothing on its own. So the pause ends on actions, things
 *    to do instead and one big way back to the day, not on a question.
 *
 * The screen is a dark, quiet surface in both themes, keeps the display awake while breathing,
 * marks each step with a light haptic tick, and with system animations off the ring holds still
 * while the words and the count still guide the breath.
 */
class BreatheActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(arabicDigitsContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        // A rotation recreates the screen: that is the same pause, so only a fresh start counts.
        val tally = PauseCount.record(this, savedInstanceState == null)
        if (savedInstanceState == null) StepIns.record(this, StepIns.BREATHED)
        val store = PersonalStore(this)
        val profile = store.profile()
        val reason = if (profile.hasReason()) PersonalStore.reasonLabel(this, profile) else ""
        val prefs = getSharedPreferences(PauseCount.PREFS, MODE_PRIVATE)
        val saved = BreathPattern.fromKey(prefs.getString(PATTERN_KEY, null))
        val savedRounds = prefs.getInt(ROUNDS_KEY, BreathPattern.BREATHS).takeIf { it in RoundChoices } ?: BreathPattern.BREATHS
        // Opened from a block screen in a hard moment: no choices, straight into 3 · 2 · 1.
        val quick = intent?.getBooleanExtra(EXTRA_QUICK, false) == true
        setContent {
            HisnTheme(darkTheme = true) {
                BreatheScreen(
                    pausesToday = tally.count,
                    reason = reason,
                    initialPattern = saved,
                    initialRounds = savedRounds,
                    quick = quick,
                    onPatternChosen = { prefs.edit { putString(PATTERN_KEY, it.key) } },
                    onRoundsChosen = { prefs.edit { putInt(ROUNDS_KEY, it) } },
                    onBreathing = { breathing ->
                        if (breathing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    },
                    onClose = ::finish
                )
            }
        }
    }

    companion object {
        /**
         * Set by a block or pause screen: the person is in a hard moment, so Unrot skips the start
         * screen and goes straight to the get-ready count with the saved rounds and pattern.
         */
        const val EXTRA_QUICK = "app.hisn.android.extra.BREATHE_QUICK"
        private const val PATTERN_KEY = "pattern"
        private const val ROUNDS_KEY = "rounds"
    }
}

private val Gold = Color(0xFFF4C34E)
private val Cream = Color(0xFFF5EFE3)
private val InkText = Color(0xFF1F2A23)

/** How many breaths the Unrot start screen offers; three is the thirty seconds Today promises. */
private val RoundChoices = listOf(3, 5, 10)

// Unrot's steps before the breathing; the Islamic edition always starts on BREATHING.
private const val STAGE_START = 0
private const val STAGE_READY = 1
private const val STAGE_BREATHING = 2
private const val COUNTDOWN_MILLIS = 3_000L

@Composable
private fun breatheBackground(): Color =
    if (app.hisn.android.Edition.islamic) (if (isSystemInDarkTheme()) Color(0xFF121110) else Color(0xFF1F2A23))
    else Color(0xFF0E0D0B) // Unrot: Night gold, a quiet near-black in both themes.

@Composable
private fun BreatheScreen(
    pausesToday: Int,
    reason: String,
    initialPattern: BreathPattern,
    initialRounds: Int,
    quick: Boolean,
    onPatternChosen: (BreathPattern) -> Unit,
    onRoundsChosen: (Int) -> Unit,
    onBreathing: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val unrot = !app.hisn.android.Edition.islamic
    var patternKey by rememberSaveable { mutableStateOf(initialPattern.key) }
    var done by rememberSaveable { mutableStateOf(false) }
    var run by rememberSaveable { mutableIntStateOf(0) }
    // The Islamic edition keeps its fixed three breaths and starts breathing at once.
    var rounds by rememberSaveable { mutableIntStateOf(if (unrot) initialRounds else BreathPattern.BREATHS) }
    var stage by rememberSaveable {
        mutableIntStateOf(if (!unrot) STAGE_BREATHING else if (quick) STAGE_READY else STAGE_START)
    }
    val pattern = BreathPattern.fromKey(patternKey)

    val glassContext = androidx.compose.ui.platform.LocalContext.current
    // Keep the display awake from the count until the last breath, not while choosing.
    LaunchedEffect(done, stage) { onBreathing(!done && stage != STAGE_START) }

    Box(
        Modifier
            .fillMaxSize()
            .background(breatheBackground())
            .safeDrawingPadding()
    ) {
        if (done) {
            AfterPause(
                pausesToday = pausesToday,
                onAgain = {
                    run++
                    // Unrot: once more goes through the short count again, not the choices.
                    if (unrot) stage = STAGE_READY
                    done = false
                },
                onClose = onClose
            )
        } else if (stage == STAGE_START) {
            StartBreathing(
                pattern = pattern,
                rounds = rounds,
                onPattern = {
                    if (it != pattern) {
                        patternKey = it.key
                        onPatternChosen(it)
                    }
                },
                onRounds = {
                    rounds = it
                    onRoundsChosen(it)
                },
                onStart = { stage = STAGE_READY },
                onClose = onClose
            )
        } else if (stage == STAGE_READY) {
            GetReady(
                run = run,
                onReady = { stage = STAGE_BREATHING },
                onClose = onClose
            )
        } else {
            Breathing(
                pattern = pattern,
                rounds = rounds,
                run = run,
                reason = reason,
                onPattern = {
                    if (it != pattern) {
                        patternKey = it.key
                        onPatternChosen(it)
                        run++
                    }
                },
                // A finished breath, not an opened screen, is what seals a crack with gold.
                onFinished = { GlassMoments.breathed(glassContext); done = true },
                onClose = onClose
            )
        }
    }
}

@Composable
private fun Breathing(
    pattern: BreathPattern,
    rounds: Int,
    run: Int,
    reason: String,
    onPattern: (BreathPattern) -> Unit,
    onFinished: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val reduceMotion = remember { systemReduceMotion(context) }
    val haptics = rememberHisnHaptics()
    var elapsed by remember(run, pattern, rounds) { mutableLongStateOf(0L) }
    val totalMillis = pattern.breathMillis() * rounds

    // The clock is the frame clock: no timers to drift, and it stops with the screen.
    LaunchedEffect(run, pattern, rounds) {
        val start = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            elapsed = now - start
            if (elapsed >= totalMillis) break
        }
        haptics.celebrate()
        onFinished()
    }

    val breath = breathAt(pattern, rounds, elapsed)
    val position = positionAt(pattern, rounds, elapsed)
    LaunchedEffect(run, pattern, breath, position.index) {
        haptics.quietTick()
    }

    val scale = if (reduceMotion) 0.84f else ringScale(pattern, position)
    val progress = elapsed.toFloat() / totalMillis.toFloat()
    val word = when (position.phase) {
        BreathPattern.Phase.IN -> R.string.breathe_in
        BreathPattern.Phase.TOP_UP -> R.string.breathe_top_up
        else -> R.string.breathe_out
    }
    val hint = when {
        pattern == BreathPattern.EVEN && position.phase == BreathPattern.Phase.IN -> R.string.breathe_even_in_hint
        pattern == BreathPattern.EVEN -> R.string.breathe_even_out_hint
        position.phase == BreathPattern.Phase.IN -> R.string.breathe_in_hint
        position.phase == BreathPattern.Phase.TOP_UP -> R.string.breathe_top_up_hint
        else -> R.string.breathe_out_hint
    }
    val resources = LocalResources.current
    val total = (totalMillis / 1000).toInt()
    val secondsIn = (elapsed / 1000).toInt().coerceAtMost(total)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CloseButton(onClose)
            Spacer(Modifier.weight(1f))
            PatternSwitch(pattern, onPattern)
        }
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .size(260.dp)
                .semantics {
                    contentDescription = resources.getString(R.string.breathe_progress, secondsIn, total)
                },
            contentAlignment = Alignment.Center
        ) {
            BreathRing(scale = scale, progress = progress)
            Text(
                "${position.secondsLeft().coerceAtLeast(1)}",
                style = MaterialTheme.typography.displayMedium,
                color = InkText
            )
        }
        Spacer(Modifier.height(24.dp))
        AnimatedContent(
            targetState = word to hint,
            transitionSpec = {
                if (reduceMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else fadeIn(tween(260, delayMillis = 60)) togetherWith fadeOut(tween(140))
            },
            label = "breath words"
        ) { (title, sub) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(title),
                    style = MaterialTheme.typography.displaySmall.storySerif(),
                    color = Cream,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cream.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        StepChips(pattern, position)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.pause_breath_step, breath + 1, rounds),
            style = MaterialTheme.typography.labelMedium,
            color = Cream.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(32.dp))
        if (reason.isNotEmpty()) {
            Text(
                stringResource(R.string.personal_quote, reason),
                style = MaterialTheme.typography.titleLarge.storySerif(),
                color = Cream.copy(alpha = 0.88f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

/** Which breath (0-based) the session is on after [elapsed] ms of [rounds] breaths. */
private fun breathAt(pattern: BreathPattern, rounds: Int, elapsed: Long): Int {
    val clamped = elapsed.coerceIn(0L, pattern.breathMillis() * rounds)
    return (clamped / pattern.breathMillis()).toInt().coerceAtMost(rounds - 1)
}

/**
 * The step inside the current breath. [BreathPattern] itself counts its fixed three breaths, so
 * any number of rounds is read one breath at a time; the very end is the last step, complete.
 */
private fun positionAt(pattern: BreathPattern, rounds: Int, elapsed: Long): BreathPattern.Position {
    val clamped = elapsed.coerceIn(0L, pattern.breathMillis() * rounds)
    val within = clamped - breathAt(pattern, rounds, clamped) * pattern.breathMillis()
    return if (within >= pattern.breathMillis()) pattern.at(pattern.totalMillis()) else pattern.at(within)
}

/**
 * Unrot's start (Glasshouse br1, CleanPath's meditation start): one calm sentence, the flower
 * closed and still, the pattern, how many rounds, and one big Start.
 */
@Composable
private fun StartBreathing(
    pattern: BreathPattern,
    rounds: Int,
    onPattern: (BreathPattern) -> Unit,
    onRounds: (Int) -> Unit,
    onStart: () -> Unit,
    onClose: () -> Unit
) {
    val haptics = rememberHisnHaptics()
    val resources = LocalResources.current
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CloseButton(onClose)
                Spacer(Modifier.weight(1f))
                PatternSwitch(pattern, onPattern)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.brs_start_line),
                style = MaterialTheme.typography.headlineSmall.storySerif(),
                color = Cream,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .semantics { heading() }
            )
            Spacer(Modifier.height(28.dp))
            // The flower waits closed: it only opens once the first breath begins.
            Box(Modifier.size(290.dp)) { BreathRing(scale = BreathPattern.REST, progress = 0f) }
        }
        // The choice sits by the Start button, so the flower has the page to itself.
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.brs_rounds_label).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Cream.copy(alpha = 0.7f)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    sessionLength(resources, pattern.breathMillis() * rounds),
                    style = MaterialTheme.typography.labelLarge,
                    color = Gold
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RoundChoices.forEach { option ->
                    val selected = option == rounds
                    val description = pluralStringResource(R.plurals.brs_rounds_option, option, option)
                    val shape = RoundedCornerShape(18.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .background(if (selected) Gold else Cream.copy(alpha = 0.07f), shape)
                            .border(1.5.dp, if (selected) Gold else Cream.copy(alpha = 0.16f), shape)
                            .selectable(selected = selected, role = Role.RadioButton) {
                                if (!selected) {
                                    haptics.tap()
                                    onRounds(option)
                                }
                            }
                            .semantics { contentDescription = description },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$option",
                            style = MaterialTheme.typography.headlineSmall.storySerif(),
                            color = if (selected) InkText else Cream
                        )
                    }
                }
            }
        }
        val startDescription = pluralStringResource(R.plurals.brs_start_description, rounds, rounds)
        Button(
            onClick = { haptics.confirm(); onStart() },
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = InkText),
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp, top = 8.dp)
                .heightIn(min = 60.dp)
                .semantics { contentDescription = startDescription }
        ) {
            Text(stringResource(R.string.brs_start), style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** "30 sec", "1 min", "1 min 40 sec": how long the chosen rounds take. */
private fun sessionLength(resources: android.content.res.Resources, millis: Long): String {
    val seconds = (millis / 1000).toInt()
    return when {
        seconds < 60 -> resources.getString(R.string.brs_length_sec, seconds)
        seconds % 60 == 0 -> resources.getString(R.string.brs_length_min, seconds / 60)
        else -> resources.getString(R.string.brs_length_min_sec, seconds / 60, seconds % 60)
    }
}

/**
 * Unrot's get-ready count (Glasshouse br2): 3 · 2 · 1 over the closed flower, each number easing
 * in a little large and settling, then the first breath. With animations off the numbers simply
 * change. Driven by the frame clock, like the breathing, so nothing depends on animator scale.
 */
@Composable
private fun GetReady(run: Int, onReady: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember { systemReduceMotion(context) }
    val haptics = rememberHisnHaptics()
    var elapsed by remember(run) { mutableLongStateOf(0L) }
    LaunchedEffect(run) {
        val start = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            elapsed = now - start
            if (elapsed >= COUNTDOWN_MILLIS) break
        }
        onReady()
    }
    val number = (3 - (elapsed / 1000).toInt()).coerceIn(1, 3)
    LaunchedEffect(run, number) { haptics.quietTick() }
    val fraction = ((elapsed % 1000) / 1000f).coerceIn(0f, 1f)
    val numberScale = if (reduceMotion) 1f else 1.18f - 0.18f * FastOutSlowInEasing.transform(fraction)
    val numberAlpha = if (reduceMotion) 1f else when {
        fraction < 0.2f -> fraction / 0.2f
        fraction > 0.85f -> (1f - fraction) / 0.15f
        else -> 1f
    }.coerceIn(0f, 1f)
    val announcement = stringResource(R.string.brs_countdown_number, number)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CloseButton(onClose)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(28.dp))
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            BreathRing(scale = BreathPattern.REST, progress = 0f)
            Text(
                "$number",
                style = MaterialTheme.typography.displayLarge.storySerif(),
                color = InkText,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = numberScale
                        scaleY = numberScale
                        alpha = numberAlpha
                    }
                    .semantics {
                        contentDescription = announcement
                        liveRegion = LiveRegionMode.Polite
                    }
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.brs_get_ready),
            style = MaterialTheme.typography.displaySmall.storySerif(),
            color = Cream,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.brs_get_ready_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Cream.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The ring follows the breath: it eases from where the last step left it to this step's size.
 * An inhale speeds up then settles, an exhale lets go slowly, like real lungs.
 */
private fun ringScale(pattern: BreathPattern, position: BreathPattern.Position): Float {
    val from = if (position.index == 0) BreathPattern.REST
    else pattern.ringTarget(pattern.phase(position.index - 1))
    val to = pattern.ringTarget(position.phase)
    val fraction = if (position.length == 0L) 1f else position.done.toFloat() / position.length
    val eased = FastOutSlowInEasing.transform(fraction.coerceIn(0f, 1f))
    return from + (to - from) * eased
}

/** Three layered discs that swell and sink, inside a thin arc that fills over the whole pause. */
@Composable
private fun BreathRing(scale: Float, progress: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 4.dp.toPx()
        val radius = size.minDimension / 2f - stroke
        val centre = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Cream.copy(alpha = 0.12f), radius, centre, style = Stroke(stroke))
        drawArc(
            color = Gold,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(centre.x - radius, centre.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        val inner = radius - 18.dp.toPx()
        if (!app.hisn.android.Edition.islamic) {
            // Unrot (Glasshouse mockup): a gold flower of five layers that opens on the in-breath
            // and closes on the out-breath, like CleanPath's meditation flower.
            val colours = listOf(Color(0xFFC98D22), Color(0xFFE0A832), Gold, Color(0xFFFFD978), Color(0xFFFFF1C4))
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(listOf(Gold.copy(alpha = 0.35f * scale.coerceIn(0.4f, 1f)), Color.Transparent),
                    center = centre, radius = inner * 1.05f),
                inner * 1.05f, centre
            )
            listOf(1f, 0.8f, 0.6f, 0.41f, 0.22f).forEachIndexed { i, r ->
                val big = inner * r * (0.62f + 0.38f * scale)
                val path = androidx.compose.ui.graphics.Path()
                for (deg in 0..360 step 3) {
                    val t = Math.toRadians(deg.toDouble())
                    val rr = big * (0.8f + 0.2f * Math.pow(kotlin.math.abs(kotlin.math.cos(2.5 * t)), 0.55).toFloat())
                    val x = centre.x + rr * kotlin.math.cos(t - Math.PI / 2).toFloat()
                    val y = centre.y + rr * kotlin.math.sin(t - Math.PI / 2).toFloat()
                    if (deg == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, colours[i])
            }
            return@Canvas
        }
        // Premium: three fine rings, a glow that breathes, and a polished gold orb with a highlight.
        listOf(1f to 0.16f, 0.82f to 0.26f, 0.64f to 0.45f).forEach { (r, a) ->
            drawCircle(Gold.copy(alpha = a), inner * r * (0.9f + 0.1f * scale), centre, style = Stroke(1.dp.toPx()))
        }
        val core = inner * 0.44f * scale.coerceAtLeast(0.7f)
        drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(Gold.copy(alpha = 0.42f * scale.coerceIn(0.5f, 1f)), Color.Transparent),
                center = centre, radius = core * 2.1f
            ),
            core * 2.1f, centre
        )
        drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(Color(0xFFFFF1C2), Gold, Color(0xFFB9821F)),
                center = Offset(centre.x - core * 0.35f, centre.y - core * 0.4f),
                radius = core * 1.45f
            ),
            core, centre
        )
    }
}

@Composable
private fun StepChips(pattern: BreathPattern, position: BreathPattern.Position) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (index in 0 until pattern.phaseCount()) {
            val label = when (pattern.phase(index)) {
                BreathPattern.Phase.IN -> R.string.breathe_step_in
                BreathPattern.Phase.TOP_UP -> R.string.breathe_step_top_up
                else -> R.string.breathe_step_out
            }
            val current = index == position.index
            val past = index < position.index
            Box(
                Modifier
                    .background(if (current) Gold else Color.Transparent, CircleShape)
                    .border(1.5.dp, if (current) Gold else Cream.copy(alpha = 0.22f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        current -> InkText
                        past -> Cream.copy(alpha = 0.4f)
                        else -> Cream.copy(alpha = 0.7f)
                    }
                )
            }
        }
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    IconButton(
        onClick = onClose,
        modifier = Modifier
            .size(48.dp)
            .border(1.5.dp, Cream.copy(alpha = 0.3f), CircleShape)
    ) {
        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.breathe_close), tint = Cream)
    }
}

@Composable
private fun PatternSwitch(pattern: BreathPattern, onPattern: (BreathPattern) -> Unit) {
    val label = stringResource(R.string.breathe_pattern_label)
    Row(
        Modifier
            .border(1.5.dp, Cream.copy(alpha = 0.3f), CircleShape)
            .semantics { contentDescription = label }
            .selectableGroup()
    ) {
        listOf(BreathPattern.SIGH to R.string.breathe_pattern_sigh, BreathPattern.EVEN to R.string.breathe_pattern_even)
            .forEach { (option, text) ->
                val selected = option == pattern
                Box(
                    Modifier
                        .heightIn(min = 44.dp)
                        .background(if (selected) Gold else Color.Transparent, CircleShape)
                        .selectable(selected = selected, role = Role.RadioButton) { onPattern(option) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(text),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) InkText else Cream
                    )
                }
            }
    }
}

private data class Alternative(val icon: ImageVector, val text: Int)

private val Alternatives = listOf(
    Alternative(Icons.AutoMirrored.Rounded.DirectionsWalk, R.string.breathe_alt_walk),
    Alternative(Icons.Rounded.LocalDrink, R.string.breathe_alt_water),
    Alternative(Icons.AutoMirrored.Rounded.Chat, R.string.breathe_alt_text),
    Alternative(Icons.AutoMirrored.Rounded.MenuBook, R.string.breathe_alt_read)
)

@Composable
private fun AfterPause(pausesToday: Int, onAgain: () -> Unit, onClose: () -> Unit) {
    val haptics = rememberHisnHaptics()
    var chosen by rememberSaveable { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CloseButton(onClose)
                Spacer(Modifier.weight(1f))
                if (pausesToday > 0) {
                    Text(
                        pluralStringResource(R.plurals.breathe_tally, pausesToday, pausesToday),
                        style = MaterialTheme.typography.labelLarge,
                        color = Cream,
                        modifier = Modifier
                            .background(Cream.copy(alpha = 0.1f), CircleShape)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            // Unrot: the glass itself says what the breath did. Sealed just now shows the gold seam.
            val glassContext = androidx.compose.ui.platform.LocalContext.current
            val sealedNow = remember { GlassMoments.sealedWithin(glassContext, 60_000L) }
            if (!app.hisn.android.Edition.islamic) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(if (sealedNow) R.drawable.unrot_glass_gold else R.drawable.unrot_glass_sprout),
                    contentDescription = null,
                    modifier = Modifier.size(200.dp).clip(CircleShape)
                )
                if (sealedNow) {
                    Text(
                        stringResource(R.string.breathe_glass_sealed),
                        style = MaterialTheme.typography.titleMedium.storySerif(),
                        color = Gold,
                        textAlign = TextAlign.Center
                    )
                }
            } else Box(
                Modifier
                    .size(124.dp)
                    .background(Gold.copy(alpha = 0.06f), CircleShape)
                    .padding(14.dp)
                    .background(Gold.copy(alpha = 0.12f), CircleShape)
                    .padding(14.dp)
                    .background(Gold, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = InkText, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.breathe_done_title),
                style = MaterialTheme.typography.headlineLarge.storySerif(),
                color = Cream,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                }
            )
            val context = androidx.compose.ui.platform.LocalContext.current
            val insteadLine = remember { app.hisn.android.nativeshell.UnrotAnswers.insteadLine(context) }
            if (insteadLine != null) {
                Spacer(Modifier.height(8.dp))
                Text(insteadLine, style = MaterialTheme.typography.titleMedium.storySerif(), color = Gold, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.breathe_done_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Cream.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Alternatives.forEachIndexed { index, alternative ->
                    AlternativeRow(alternative, selected = chosen == index) {
                        chosen = index
                        haptics.confirm()
                    }
                }
            }
            if (chosen >= 0) {
                Text(
                    stringResource(R.string.breathe_alt_chosen),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            TextButton(onClick = onAgain, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Cream, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.pause_breathe_again), color = Cream)
            }
        }
        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = InkText),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                .heightIn(min = 58.dp)
        ) {
            Text(stringResource(R.string.breathe_back), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun AlternativeRow(alternative: Alternative, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val on = stringResource(R.string.breathe_alt_chosen)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(if (selected) Gold else Cream.copy(alpha = 0.07f), shape)
            .border(1.5.dp, if (selected) Gold else Cream.copy(alpha = 0.14f), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { if (selected) stateDescription = on }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(alternative.icon, contentDescription = null, tint = if (selected) InkText else Gold)
        Text(
            stringResource(alternative.text),
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) InkText else Cream
        )
    }
}
