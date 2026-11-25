package app.hisn.android.nativeshell

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.hisn.android.R

/*
 * Help that is offered, never forced:
 *  - an explainer sheet per page ("How to read this page"), which also says what is not measured,
 *  - explain mode: long-press a Shield row for plain words, a way to change it and read-aloud,
 *  - a three-stop first-visit tour of Today (day count, pause, shield), skippable, shown once.
 * CleanPath does a spotlight tour and an explainer sheet; ours are shorter and add what we
 * deliberately leave out.
 */

/** "How to read this page" for Progress. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProgressExplainerSheet(onDismiss: () -> Unit) {
    val haptics = rememberHisnHaptics()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // Scrolls so the last rows and "Got it" stay reachable on small screens and large text.
        Column(
            Modifier
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.explain_progress_title), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
            listOf(
                R.string.explain_progress_feed to R.string.explain_progress_feed_body,
                R.string.explain_progress_walked to R.string.explain_progress_walked_body,
                R.string.explain_progress_stickers to R.string.explain_progress_stickers_body,
                R.string.explain_progress_hard to R.string.explain_progress_hard_body,
                R.string.explain_progress_not to R.string.explain_progress_not_body
            ).forEach { (title, body) ->
                Column {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(body), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = { haptics.tap(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.explain_got_it)) }
        }
    }
}

/** Explain mode's card: plain words, a way to change it right there, and read-aloud. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExplainSheet(title: String, body: String, change: String?, onChange: () -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHisnHaptics()
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.close() } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(body, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { haptics.tap(); onDismiss() }) { Text(stringResource(R.string.explain_got_it)) }
                if (change != null) TextButton(onClick = { onDismiss(); onChange() }) { Text(change) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { speaker.say("$title. $body") }) { Text(stringResource(R.string.explain_read_aloud)) }
            }
        }
    }
}

/** Android's own text-to-speech in the phone's language; silent when no voice is installed. */
private class Speaker(context: Context) {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }
    fun say(text: String) {
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "explain")
    }
    fun close() = runCatching { tts.stop(); tts.shutdown() }
}

// First-visit tour.

internal object Tour {
    val targets = mutableStateMapOf<String, Rect>()
    private const val PREFS = "tour"
    private const val DONE = "today_done"
    fun done(context: Context): Boolean = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(DONE, false)
    }.getOrDefault(true)
    fun finish(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(DONE, true).apply()
    }
    /** For "Show the tour again" and for tests that need a clean Today. */
    fun reset(context: Context, done: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(DONE, done).apply()
    }
}

/** Marks an element as a tour stop. */
internal fun Modifier.tourTarget(key: String): Modifier =
    onGloballyPositioned { Tour.targets[key] = it.boundsInRoot() }

private val TourStops = listOf(
    Triple("day", R.string.tour_day_title, R.string.tour_day_body),
    Triple("pause", R.string.tour_pause_title, R.string.tour_pause_body),
    Triple("shield", R.string.tour_shield_title, R.string.tour_shield_body)
)

/** A dim layer with a hole around the current stop, and one card. Three stops, then never again. */
@Composable
internal fun UnrotTour() {
    val haptics = rememberHisnHaptics()
    val context = LocalContext.current
    var show by remember { mutableStateOf(!Tour.done(context)) }
    var index by remember { mutableIntStateOf(0) }
    if (!show) return
    val stops = TourStops.filter { Tour.targets.containsKey(it.first) }
    if (stops.isEmpty()) return
    val (key, title, body) = stops[index.coerceAtMost(stops.lastIndex)]
    // The name from setup, read from the same private file the day count uses.
    val name = remember {
        runCatching { context.getSharedPreferences("personal", Context.MODE_PRIVATE).getString("name", "").orEmpty().trim() }
            .getOrDefault("")
    }
    val target = Tour.targets[key] ?: return
    fun end() { Tour.finish(context); show = false }
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Swallow taps so the page underneath is not used by accident during the tour.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        val pad = with(density) { 8.dp.toPx() }
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = 0.62f))
            drawRoundRect(
                Color.Transparent,
                topLeft = Offset(target.left - pad, target.top - pad),
                size = Size(target.width + pad * 2, target.height + pad * 2),
                cornerRadius = CornerRadius(pad * 3),
                blendMode = BlendMode.Clear
            )
        }
        val heightPx = with(density) { maxHeight.toPx() }
        // The card sits on the far side of the stop so it never covers it.
        val below = target.center.y < heightPx / 2
        val y = with(density) {
            if (below) (target.bottom + pad * 2).toDp() else (target.top - pad * 2).toDp() - 190.dp
        }
        Column(
            Modifier
                .offset(y = y.coerceAtLeast(16.dp))
                .padding(horizontal = 20.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraLarge)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (index == 0 && name.isNotEmpty()) {
                Text(stringResource(R.string.tour_home_named, name), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tour_step, index + 1, stops.size),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { haptics.tap(); end() }) { Text(stringResource(R.string.tour_skip)) }
                Spacer(Modifier.width(4.dp))
                Button(onClick = { if (index >= stops.lastIndex) { haptics.confirm(); end() } else { haptics.tick(); index++ } }) {
                    Text(stringResource(if (index >= stops.lastIndex) R.string.tour_done else R.string.tour_next))
                }
            }
        }
    }
}
