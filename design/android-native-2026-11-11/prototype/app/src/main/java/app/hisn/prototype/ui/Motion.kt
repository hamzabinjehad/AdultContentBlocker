package app.hisn.prototype.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Motion tokens for the prototype.
 *
 * They follow Material 3's 2026 scheme rather than a house curve: springs own anything that can be
 * interrupted (size, position, shape), tweens own opacity only. The standard values are the
 * generated AOSP tokens (spatial 0.9/700, spatial fast 0.9/1400, effects 1.0/1600), so the app
 * moves like the platform instead of like a web landing page.
 */
object HisnMotion {
    /** Short 4 — small fades. */
    const val FastMs = 160

    /** Medium 2 — most effects and screen fades. */
    const val MediumMs = 280

    /** Long 2 — a whole-surface push. */
    const val SlowMs = 450

    /** Spatial standard: a control changing size or place. */
    fun <T> spatial() = spring<T>(dampingRatio = 0.9f, stiffness = 700f)

    /** Spatial fast: press feedback, dots, chips. */
    fun <T> spatialFast() = spring<T>(dampingRatio = 0.9f, stiffness = 1400f)

    /** Spatial slow: a full-screen move. */
    fun <T> spatialSlow() = spring<T>(dampingRatio = 0.9f, stiffness = 300f)

    /** Expressive spatial: a little overshoot, used only for the hero. */
    fun <T> expressive() = spring<T>(dampingRatio = 0.8f, stiffness = 380f)

    /** Effects standard: colour and alpha, critically damped. */
    fun <T> effects() = spring<T>(dampingRatio = 1f, stiffness = 1600f)

    val fadeIn = tween<Float>(200, easing = FastOutSlowInEasing)
    val fadeOut = tween<Float>(90, easing = LinearOutSlowInEasing)
    val emphasizeIn = tween<Float>(MediumMs, easing = FastOutSlowInEasing)
}

/**
 * True when Android's "Remove animations" accessibility setting is on. Compose already ends its own
 * animations when the animator scale is zero; this local exists for the parts Compose cannot know
 * about — artificial cascade delays, looping decorations and one-shot celebrations.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** One snackbar request. The root host owns the queue; screens only describe the message. */
data class HisnMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

val LocalHisnSnackbar = staticCompositionLocalOf<(HisnMessage) -> Unit> { {} }

/** Reads both scales so "Remove animations" is honoured even if only transitions are disabled. */
fun systemReduceMotion(context: Context): Boolean {
    val resolver = context.contentResolver
    val animator = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    val transition = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    }.getOrDefault(1f)
    return animator == 0f || transition == 0f
}

/**
 * Haptics with meaning: a switch reports on/off, a finished check confirms, invalid input rejects,
 * a picker ticks. [LocalHapticFeedback] maps these to `HapticFeedbackConstants`, so the system's own
 * haptic setting still decides whether anything happens.
 */
class HisnHaptics internal constructor(private val haptics: HapticFeedback) {
    fun toggle(on: Boolean) = haptics.performHapticFeedback(
        if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
    )

    fun confirm() = haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    fun reject() = haptics.performHapticFeedback(HapticFeedbackType.Reject)
    fun tick() = haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    fun nav() = haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
    fun edge() = haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
}

@Composable
fun rememberHisnHaptics(): HisnHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { HisnHaptics(feedback) }
}

/**
 * Press feedback for any button: 0.96 with the fast spatial spring. The ripple still comes from the
 * Material component; this only adds the physical "the surface gave a little" cue.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = HisnMotion.spatialFast(),
        label = "press scale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * A number that rolls when it changes. Tabular figures keep the width stable, and the label is the
 * only thing that moves — never the row around it.
 */
@Composable
fun AnimatedCount(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null
) {
    if (LocalReduceMotion.current) {
        Text(
            text = value.toString(),
            modifier = modifier,
            style = style.copy(fontFeatureSettings = "tnum"),
            color = color,
            textAlign = textAlign
        )
        return
    }
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) {
                if (up) it else -it
            } + fadeIn(HisnMotion.fadeIn)) togetherWith
                (slideOutVertically(animationSpec = tween(140, easing = LinearOutSlowInEasing)) {
                    if (up) -it else it
                } + fadeOut(tween(90)))
        },
        modifier = modifier,
        label = "count"
    ) { current ->
        Text(
            text = current.toString(),
            style = style.copy(fontFeatureSettings = "tnum"),
            color = color,
            textAlign = textAlign
        )
    }
}

/**
 * A shared skeleton block for content that is still being read from the phone. Under reduced motion
 * it is a flat placeholder instead of a sweep.
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    corner: Dp = 8.dp
) {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f)
    if (LocalReduceMotion.current) {
        Box(modifier.background(base, RoundedCornerShape(corner)))
        return
    }
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeleton sweep"
    )
    Box(
        modifier
            .drawWithCache {
                val width = size.width.coerceAtLeast(1f)
                val start = -width + shift * width * 2.4f
                val brush = Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(start, 0f),
                    end = Offset(start + width, size.height)
                )
                onDrawBehind {
                    drawRoundRect(
                        brush = brush,
                        cornerRadius = CornerRadius(corner.toPx(), corner.toPx())
                    )
                }
            }
    )
}

@Composable
fun SkeletonCircle(size: Dp) {
    SkeletonBlock(Modifier.size(size), corner = size / 2)
}
