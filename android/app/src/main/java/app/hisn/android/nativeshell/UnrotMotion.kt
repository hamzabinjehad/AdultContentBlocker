package app.hisn.android.nativeshell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Unrot's motion, in four small pieces used across the pages:
 *  - riseIn: a card fades up into place the first time it appears, a little after the one above,
 *  - grow: a bar, gauge or ring fills from empty to its value,
 *  - countUp: a number counts up to its value, once,
 *  - breathe: the "running" dot slowly breathes, like the pause button's rhythm.
 * Every one of them is skipped when Android's "Remove animations" is on.
 */

/** Fades and lifts the element into place once, [index] steps after the first. */
@Composable
internal fun Modifier.riseIn(index: Int = 0): Modifier {
    if (LocalReduceMotion.current) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // Whatever happens to the animation, the element must end fully visible.
        try {
            progress.animateTo(1f, tween(durationMillis = 320, delayMillis = (index.coerceAtMost(7)) * 55,
                easing = FastOutSlowInEasing))
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { progress.snapTo(1f) }
        }
    }
    val lift = with(LocalDensity.current) { 18.dp.toPx() }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * lift
    }
}

/** Wraps a whole block so it rises in; for list items that have no modifier of their own. */
@Composable
internal fun Rise(index: Int, content: @Composable () -> Unit) {
    Box(Modifier.riseIn(index)) { content() }
}

/** A value from 0 to [target] that grows once on first show and follows later changes. */
@Composable
internal fun grow(target: Float, delayMs: Int = 0): Float {
    if (LocalReduceMotion.current) return target
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) {
        value.animateTo(target, tween(durationMillis = 700, delayMillis = delayMs, easing = FastOutSlowInEasing))
    }
    return value.value
}

/** A whole number that counts up from zero the first time it shows. */
@Composable
internal fun countUp(target: Long, durationMs: Int = 650): Long {
    if (LocalReduceMotion.current || target <= 0L) return target
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) {
        value.animateTo(target.toFloat(), tween(durationMillis = durationMs, easing = FastOutSlowInEasing))
    }
    return value.value.toLong()
}

/** A gentle, endless 0..1 breath for live status dots (4 s in, 4 s out). */
@Composable
internal fun breath(): Float {
    if (LocalReduceMotion.current) return 0f
    val transition = rememberInfiniteTransition(label = "breath")
    val value by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "breath"
    )
    return value
}

/** A small pop for something that just turned true (a reached milestone, a passed check). */
@Composable
internal fun Modifier.popIn(index: Int = 0): Modifier {
    if (LocalReduceMotion.current) return this
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        try {
            kotlinx.coroutines.delay(150L + index * 90L)
            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f))
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { scale.snapTo(1f) }
        }
    }
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/**
 * The empty-state picture: a tilted coloured tile with the page's icon and a few scattered dots,
 * drawn in code so it follows light and dark and costs no image files.
 */
@Composable
internal fun EmptyArt(icon: ImageVector, tile: Color, size: Dp = 112.dp) {
    val ink = UnrotInk
    val wobble = if (LocalReduceMotion.current) 0f else {
        val t = rememberInfiniteTransition(label = "art")
        val v by t.animateFloat(-4f, 4f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tilt")
        v
    }
    // Premium: the page's 3D object floating, when there is one.
    (if (unrotEdition) art3D(icon) else null)?.let { art ->
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Float3D(art, size * 0.8f, Modifier.rotate(-6f + wobble))
        }
        return
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension
            drawCircle(tile.copy(alpha = 0.35f), radius = r * 0.47f)
            listOf(Offset(0.12f, 0.2f), Offset(0.88f, 0.28f), Offset(0.82f, 0.86f), Offset(0.16f, 0.8f)).forEachIndexed { i, o ->
                drawCircle(if (i % 2 == 0) ink else tile, radius = r * (if (i % 2 == 0) 0.022f else 0.04f),
                    center = Offset(o.x * r, o.y * r))
            }
        }
        Box(
            Modifier
                .size(size * 0.52f)
                .rotate(-8f + wobble)
                .background(tile, RoundedCornerShape(size * 0.15f))
                .border(2.5.dp, ink, RoundedCornerShape(size * 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(size * 0.26f))
        }
    }
}
