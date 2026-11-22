package app.hisn.android.nativeshell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hisn.android.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val DAY = 1440
private const val STEP = 30

/** Minutes from [start] to [end] going forward round the clock. */
internal fun quietLength(start: Int, end: Int): Int = ((end - start) % DAY + DAY) % DAY

/**
 * Quiet hours painted on a 24-hour clock: midnight at the top, noon at the bottom, the quiet
 * stretch as a thick arc with a handle at each end. Dragging moves the nearer handle in
 * half-hour steps. TalkBack gets the times read out and four actions (each end earlier or
 * later), so the dial never needs a drag to use.
 */
@Composable
internal fun QuietDial(start: Int, end: Int, onChange: (Int, Int) -> Unit, size: Dp = 272.dp) {
    val colors = MaterialTheme.colorScheme
    val resources = LocalResources.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurfaceVariant)
    val current by rememberUpdatedState(start to end)
    val change by rememberUpdatedState(onChange)
    var dragging by remember { mutableIntStateOf(-1) } // 0 start, 1 end
    fun clean(minute: Int) = ((minute % DAY + DAY) % DAY / STEP) * STEP
    fun move(which: Int, minute: Int) {
        val (s, e) = current
        if (which == 0) { if (quietLength(minute, e) >= STEP) change(clean(minute), e) }
        else { if (quietLength(s, minute) >= STEP) change(s, clean(minute)) }
    }
    val hours = quietLength(start, end) / 60f
    val label = stringResource(R.string.quiet_dial_description, time(start), time(end))
    val actions = listOf(
        R.string.quiet_dial_start_earlier to { move(0, start - STEP) },
        R.string.quiet_dial_start_later to { move(0, start + STEP) },
        R.string.quiet_dial_end_earlier to { move(1, end - STEP) },
        R.string.quiet_dial_end_later to { move(1, end + STEP) }
    ).map { (text, run) -> CustomAccessibilityAction(resources.getString(text)) { run(); true } }
    Box(
        Modifier
            .size(size)
            .semantics {
                contentDescription = label
                customActions = actions
            },
        contentAlignment = Alignment.Center
    ) {
        val track = colors.onSurface.copy(alpha = 0.10f)
        // Gold in both themes: the night uses the bright gold, the day a deeper one that holds on ivory.
        val dark = LocalDarkTheme.current
        val arc = if (dark) colors.primary else androidx.compose.ui.graphics.Color(0xFFE0A532)
        val handle = if (dark) colors.onPrimaryContainer else androidx.compose.ui.graphics.Color(0xFFB07A12)
        val handleFill = if (dark) colors.primaryContainer else androidx.compose.ui.graphics.Color.White
        Canvas(
            Modifier
                .size(size)
                .pointerInput(Unit) {
                    fun minuteAt(p: Offset): Int {
                        val c = Offset(this.size.width / 2f, this.size.height / 2f)
                        val angle = Math.toDegrees(atan2((p.x - c.x).toDouble(), -(p.y - c.y).toDouble()))
                        return (((angle + 360) % 360) / 360.0 * DAY).toInt()
                    }
                    detectDragGestures(
                        onDragStart = { p ->
                            val m = minuteAt(p)
                            val (s, e) = current
                            val ds = min(quietLength(s, m), quietLength(m, s))
                            val de = min(quietLength(e, m), quietLength(m, e))
                            dragging = if (ds <= de) 0 else 1
                        },
                        onDragEnd = { dragging = -1 },
                        onDragCancel = { dragging = -1 }
                    ) { change, _ ->
                        change.consume()
                        if (dragging >= 0) move(dragging, minuteAt(change.position) + STEP / 2)
                    }
                }
        ) {
            val stroke = 26.dp.toPx()
            val radius = this.size.minDimension / 2f - stroke / 2f - 24.dp.toPx()
            val topLeft = Offset(center.x - radius, center.y - radius)
            val box = Size(radius * 2, radius * 2)
            drawCircle(track, radius, center, style = Stroke(stroke))
            val sweep = quietLength(start, end) / DAY.toFloat() * 360f
            drawArc(arc, -90f + start / DAY.toFloat() * 360f, sweep, false, topLeft, box,
                style = Stroke(stroke, cap = StrokeCap.Round))
            listOf(start, end).forEachIndexed { i, m ->
                val a = Math.toRadians(m / DAY.toDouble() * 360.0 - 90.0)
                val p = Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
                val r = if (dragging == i) 17.dp.toPx() else 14.dp.toPx()
                drawCircle(handleFill, r, p)
                drawCircle(handle, r, p, style = Stroke(2.5.dp.toPx()))
            }
            // Hour marks for 0, 6, 12 and 18, just outside the ring, clear of the centre text.
            listOf(0, 6, 12, 18).forEach { h ->
                val a = Math.toRadians(h * 15.0 - 90.0)
                val inner = radius + stroke / 2f + 12.dp.toPx()
                val layout = measurer.measure("$h", labelStyle)
                val p = Offset(center.x + inner * cos(a).toFloat(), center.y + inner * sin(a).toFloat())
                drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${time(start)}–${time(end)}", style = MaterialTheme.typography.titleMedium)
            Text(
                resources.getString(R.string.quiet_dial_hours, formatHours(hours)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

private fun formatHours(hours: Float): String =
    if (hours % 1f == 0f) "${hours.toInt()}" else String.format(java.util.Locale.getDefault(), "%.1f", hours)

private fun time(minute: Int): String =
    String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)
