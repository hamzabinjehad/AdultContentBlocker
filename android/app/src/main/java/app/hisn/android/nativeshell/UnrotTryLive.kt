package app.hisn.android.nativeshell

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.hisn.android.R
import app.hisn.core.SetupPlan

/**
 * The wait screen, live and inline, before anything is switched on. The person picks one of
 * their apps, watches the ring count down with the breathing cue, and presses either way out.
 * Closing is the big button: one sec's 2023 study found that walking away at the wait, not the
 * reflective message, did the work. Nothing here blocks anything.
 */
@Composable
internal fun TryLive(apps: List<String>, labels: Map<String, String>, onFullScreen: (String) -> Unit) {
    val haptics = rememberHisnHaptics()
    var picked by rememberSaveable { mutableStateOf(apps.firstOrNull() ?: "") }
    var run by remember { mutableIntStateOf(0) }
    var outcome by rememberSaveable { mutableStateOf(0) } // 0 waiting, 1 closed, 2 opened
    val seconds = SetupPlan.WAIT_SECONDS
    // Real time, not an animation: with Android's "remove animations" on, an animation would
    // finish at once and the wait would vanish.
    var progress by remember(run, picked) { mutableStateOf(0f) }
    LaunchedEffect(run, picked) {
        outcome = 0
        val start = android.os.SystemClock.uptimeMillis()
        while (progress < 1f && outcome == 0) {
            progress = ((android.os.SystemClock.uptimeMillis() - start) / (seconds * 1000f)).coerceAtMost(1f)
            kotlinx.coroutines.delay(32)
        }
    }
    val left = (seconds - (progress * seconds).toInt()).coerceAtLeast(0)
    val name = labels[picked] ?: stringResource(R.string.welcome_sample_app)
    val colors = MaterialTheme.colorScheme

    if (apps.size > 1) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            apps.forEach { pkg ->
                FilterChip(
                    selected = pkg == picked,
                    onClick = { haptics.tick(); picked = pkg; run++ },
                    label = { Text(labels[pkg] ?: pkg) },
                    leadingIcon = { AppIcon(pkg, size = 20.dp) }
                )
            }
        }
    }
    val shape = MaterialTheme.shapes.extraLarge
    Column(
        Modifier
            .fillMaxWidth()
            .unrotFrame(shape, hero = true)
            .background(androidx.compose.ui.graphics.Color(0xFF1B1A16), shape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val paper = androidx.compose.ui.graphics.Color(0xFFF3EEE6)
        val gold = androidx.compose.ui.graphics.Color(0xFFF4C34E)
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(132.dp)) {
                val stroke = 10.dp.toPx()
                drawCircle(paper.copy(alpha = 0.15f), radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
                drawArc(gold, -90f, 360f * progress, false,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke, stroke),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke * 2, size.height - stroke * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text("$left", style = MaterialTheme.typography.displaySmall, color = paper)
        }
        // A breath cue on a 4-second in / 6-second out rhythm, matching the full wait screen's pace.
        val elapsed = progress * seconds
        Text(
            stringResource(if (elapsed % 10f < 4f) R.string.try_live_in else R.string.try_live_out),
            style = MaterialTheme.typography.titleMedium.storySerif(),
            color = paper
        )
        Text(
            stringResource(R.string.try_live_opens, name, left),
            style = MaterialTheme.typography.bodyMedium,
            color = paper.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        AnimatedContent(targetState = outcome, label = "try outcome") { shown ->
            if (shown == 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = { haptics.celebrate(); outcome = 1 },
                        colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = UnrotInk),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text(stringResource(R.string.try_live_close)) }
                    TextButton(onClick = { haptics.tap(); outcome = 2 }, enabled = left == 0) {
                        Text(stringResource(R.string.try_live_open, name),
                            color = if (left == 0) paper else paper.copy(alpha = 0.45f))
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(if (shown == 1) R.string.try_live_closed else R.string.try_live_opened, name),
                        style = MaterialTheme.typography.bodyLarge,
                        color = paper,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    TextButton(onClick = { haptics.tap(); run++ }) { Text(stringResource(R.string.try_live_again), color = gold) }
                }
            }
        }
    }
    TextButton(onClick = { onFullScreen(picked) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.try_live_full), color = colors.primary)
    }
}
