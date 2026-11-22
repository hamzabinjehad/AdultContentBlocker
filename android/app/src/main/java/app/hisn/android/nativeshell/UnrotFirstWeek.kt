package app.hisn.android.nativeshell

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.hisn.android.R
import kotlin.math.cos
import kotlin.math.sin

/** Remembers the last day the first-week path celebrated, so each new day bursts once. */
private object FirstWeekMemory {
    private const val PREFS = "unrot_first_week"
    fun seen(context: Context): Int = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("seen", 0)
    }.getOrDefault(0)
    fun see(context: Context, day: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("seen", day).apply()
    }
}

/**
 * The first seven days as a path that visibly moves: the days already kept pop in as gold ticks,
 * one after another; today's stop breathes; and the first time Today opens on a new day a small
 * gold burst goes off there, with a soft celebration buzz. It says "you started something" with
 * motion, and never resets.
 */
@Composable
internal fun FirstWeekPath(day: Int) {
    if (day !in 1..7) return
    val context = LocalContext.current
    val haptics = rememberHisnHaptics()
    val reduce = LocalReduceMotion.current
    val dark = LocalDarkTheme.current
    val gold = if (dark) Color(0xFFF2C94F) else Color(0xFFC8962A)
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val ink = MaterialTheme.colorScheme.onSurface
    // Each kept day pops in turn; the path fills behind them.
    val pops = remember(day) { List(7) { Animatable(if (reduce) 1f else 0f) } }
    val fill = remember(day) { Animatable(if (reduce) 1f else 0f) }
    val burst = remember(day) { Animatable(0f) }
    val fresh = remember(day) { day > FirstWeekMemory.seen(context) }
    LaunchedEffect(day) {
        if (!reduce) {
            for (i in 0 until day) {
                kotlinx.coroutines.delay(if (i == 0) 250L else 110L)
                launchPop(pops[i])
                if (i < day - 1) haptics.quietTick()
            }
            fill.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        }
        if (fresh) {
            FirstWeekMemory.see(context, day)
            if (!reduce) {
                haptics.celebrate()
                burst.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            }
        }
    }
    val breath = if (reduce) 0.5f else rememberInfiniteTransition(label = "today stop").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath"
    ).value
    val described = stringResource(R.string.first_week_path, day)
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = described }) {
            val r = 11.dp.toPx()
            val y = size.height / 2
            val step = (size.width - 2 * r) / 6f
            fun x(i: Int) = r + i * step
            // The track, then the kept part of it in gold.
            drawLine(line, Offset(x(0), y), Offset(x(6), y), 4.dp.toPx(), StrokeCap.Round)
            val reach = x(0) + (x(day - 1) - x(0)) * fill.value
            if (day > 1) drawLine(gold, Offset(x(0), y), Offset(reach, y), 4.dp.toPx(), StrokeCap.Round)
            for (i in 0 until 7) {
                val c = Offset(x(i), y)
                when {
                    i < day - 1 || (i == day - 1) -> {
                        val s = pops[i].value
                        if (i == day - 1) {
                            // Today: a breathing halo, and a burst the first time this day is seen.
                            drawCircle(gold.copy(alpha = 0.18f + 0.12f * breath), r * (1.55f + 0.25f * breath), c)
                            if (burst.value in 0.01f..0.99f) {
                                val b = burst.value
                                for (k in 0 until 10) {
                                    val a = Math.toRadians(k * 36.0 + 18.0)
                                    val d = r * (1.4f + 2.6f * b)
                                    drawCircle(gold.copy(alpha = 1f - b), 2.5.dp.toPx() * (1f - b * 0.5f),
                                        Offset(c.x + d * cos(a).toFloat(), c.y + d * sin(a).toFloat()))
                                }
                            }
                        }
                        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9A8), gold, Color(0xFFB9821F)),
                            center = Offset(c.x - r * 0.3f, c.y - r * 0.3f), radius = r * 1.6f), r * s, c)
                        if (s > 0.6f) {
                            // A tick on each kept day, drawn as two strokes.
                            val t = r * 0.42f
                            val tick = if (dark) Color(0xFF14130F) else Color.White
                            drawLine(tick, Offset(c.x - t, c.y), Offset(c.x - t * 0.25f, c.y + t * 0.7f), 2.2.dp.toPx(), StrokeCap.Round)
                            drawLine(tick, Offset(c.x - t * 0.25f, c.y + t * 0.7f), Offset(c.x + t, c.y - t * 0.6f), 2.2.dp.toPx(), StrokeCap.Round)
                        }
                    }
                    else -> drawCircle(ink.copy(alpha = 0.22f), r * 0.7f, c, style = Stroke(2.dp.toPx()))
                }
            }
        }
        Text(
            stringResource(if (day == 7) R.string.first_week_done else R.string.first_week_line, day, 7 - day),
            style = MaterialTheme.typography.titleSmall
        )
    }
}

private suspend fun launchPop(a: Animatable<Float, *>) {
    a.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
}
