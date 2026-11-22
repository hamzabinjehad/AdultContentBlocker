package app.hisn.android

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.hisn.android.nativeshell.UnrotFeel.warm(this)
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
        setContent {
            HisnTheme(darkTheme = true) {
                BreatheScreen(
                    pausesToday = tally.count,
                    reason = reason,
                    initialPattern = saved,
                    onPatternChosen = { prefs.edit { putString(PATTERN_KEY, it.key) } },
                    onBreathing = { breathing ->
                        if (breathing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    },
                    onClose = ::finish
                )
            }
        }
    }

    private companion object {
        const val PATTERN_KEY = "pattern"
    }
}

private val Gold = Color(0xFFF4C34E)
private val Cream = Color(0xFFF5EFE3)
private val InkText = Color(0xFF1F2A23)

@Composable
private fun breatheBackground(): Color =
    if (app.hisn.android.Edition.islamic) (if (isSystemInDarkTheme()) Color(0xFF121110) else Color(0xFF1F2A23))
    else Color(0xFF0E0D0B) // Unrot: Night gold, a quiet near-black in both themes.

@Composable
private fun BreatheScreen(
    pausesToday: Int,
    reason: String,
    initialPattern: BreathPattern,
    onPatternChosen: (BreathPattern) -> Unit,
    onBreathing: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    var patternKey by rememberSaveable { mutableStateOf(initialPattern.key) }
    var done by rememberSaveable { mutableStateOf(false) }
    var run by rememberSaveable { mutableIntStateOf(0) }
    val pattern = BreathPattern.fromKey(patternKey)

    LaunchedEffect(done) { onBreathing(!done) }

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
                    done = false
                },
                onClose = onClose
            )
        } else {
            Breathing(
                pattern = pattern,
                run = run,
                reason = reason,
                onPattern = {
                    if (it != pattern) {
                        patternKey = it.key
                        onPatternChosen(it)
                        run++
                    }
                },
                onFinished = { done = true },
                onClose = onClose
            )
        }
    }
}

@Composable
private fun Breathing(
    pattern: BreathPattern,
    run: Int,
    reason: String,
    onPattern: (BreathPattern) -> Unit,
    onFinished: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val reduceMotion = remember { systemReduceMotion(context) }
    val haptics = rememberHisnHaptics()
    var elapsed by remember(run, pattern) { mutableLongStateOf(0L) }

    // The clock is the frame clock: no timers to drift, and it stops with the screen.
    LaunchedEffect(run, pattern) {
        val start = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            elapsed = now - start
            if (elapsed >= pattern.totalMillis()) break
        }
        haptics.celebrate()
        onFinished()
    }

    val position = pattern.at(elapsed)
    LaunchedEffect(run, pattern, position.breath, position.index) {
        haptics.quietTick()
        // One soft tone at the start of each breath in, so eyes can close.
        if (position.index == 0) haptics.sound(app.hisn.android.nativeshell.FeelSound.BREATH)
    }

    val scale = if (reduceMotion) 0.84f else ringScale(pattern, position)
    val progress = elapsed.toFloat() / pattern.totalMillis().toFloat()
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
    val total = (pattern.totalMillis() / 1000).toInt()
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
            stringResource(R.string.pause_breath_step, position.breath + 1, BreathPattern.BREATHS),
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
            Box(
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
