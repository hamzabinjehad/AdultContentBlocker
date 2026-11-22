package app.hisn.android.nativeshell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisn.android.R

/**
 * Unrot's first screen. No mascot and no fake app icons: one drawing that says the whole product
 * (a feed that runs out, with the pause mark on it), one line, and three plain promises. The
 * scaffold below owns the "Get started" button; the app-pause preview stays as a text button so
 * the feature is one tap away before any question is asked.
 */
@Composable
internal fun UnrotWelcomePage(onPreview: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val art = stringResource(R.string.unrot_welcome_art)
        // Premium hero: the phone as a floating 3D object with the gold pause resting on it.
        Box(Modifier.size(width = 200.dp, height = 180.dp).semantics { contentDescription = art }) {
            Float3D(R.drawable.unrot_3d_mobile, 160.dp, Modifier.align(Alignment.Center))
            Float3D(R.drawable.unrot_3d_pause, 84.dp, Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.height(4.dp))
        // What the app is, in one line, before anything else.
        Text(
            stringResource(R.string.unrot_welcome_what).uppercase(appLocale()),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
            color = if (LocalDarkTheme.current) androidx.compose.ui.graphics.Color(0xFFF2C94F)
                else androidx.compose.ui.graphics.Color(0xFF8A5E0C),
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
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Promise(1, R.string.unrot_promise_shorts, R.string.unrot_promise_shorts_detail)
        Promise(2, R.string.unrot_promise_pause, R.string.unrot_promise_pause_detail)
        Promise(3, R.string.unrot_promise_adult, R.string.unrot_promise_adult_detail)
    }
    TextButton(onClick = onPreview, modifier = Modifier.heightIn(min = 48.dp)) {
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
        Text(
            stringResource(R.string.welcome_privacy_line),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun Promise(number: Int, title: Int, detail: Int) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Float3D(when (number) {
            1 -> R.drawable.unrot_3d_mobile
            2 -> R.drawable.unrot_3d_pause
            else -> R.drawable.unrot_3d_shield
        }, 40.dp)
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(detail),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

/** A phone whose feed fades into empty slots, with Unrot's gold pause mark stuck on its edge. */
@Composable
private fun FeedArt(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val ink = colors.onSurface
    val paper = colors.surfaceContainerLowest
    val tomato = Color(0xFFE5553A)
    val gold = Color(0xFFF4C34E)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier) {
        val u = size.height / 190f
        val left = (size.width - 104f * u) / 2f - 14f * u
        fun r(x: Float, y: Float, w: Float, h: Float) = Pair(Offset(left + x * u, y * u), Size(w * u, h * u))
        // Phone body.
        r(0f, 6f, 104f, 178f).let { (o, s) ->
            drawRoundRect(paper, o, s, CornerRadius(20f * u))
            drawRoundRect(ink, o, s, CornerRadius(20f * u), style = Stroke(3f * u))
        }
        r(34f, 14f, 36f, 6f).let { (o, s) -> drawRoundRect(ink.copy(alpha = 0.85f), o, s, CornerRadius(3f * u)) }
        // The feed: a loud tile, a fading one, then empty dashed slots.
        r(12f, 30f, 80f, 44f).let { (o, s) -> drawRoundRect(tomato, o, s, CornerRadius(9f * u)) }
        val play = androidx.compose.ui.graphics.Path().apply {
            moveTo(left + 46f * u, 44f * u); lineTo(left + 60f * u, 52f * u); lineTo(left + 46f * u, 60f * u); close()
        }
        drawPath(play, paper)
        r(12f, 82f, 80f, 34f).let { (o, s) -> drawRoundRect(tomato.copy(alpha = 0.38f), o, s, CornerRadius(9f * u)) }
        val dash = PathEffect.dashPathEffect(floatArrayOf(4f * u, 4f * u))
        r(12f, 124f, 80f, 24f).let { (o, s) ->
            drawRoundRect(ink.copy(alpha = 0.28f), o, s, CornerRadius(8f * u), style = Stroke(2f * u, pathEffect = dash))
        }
        r(12f, 155f, 80f, 16f).let { (o, s) ->
            drawRoundRect(ink.copy(alpha = 0.16f), o, s, CornerRadius(6f * u), style = Stroke(2f * u, pathEffect = dash))
        }
        // The pause mark, on the trailing edge of the phone.
        val cx = if (rtl) left - 6f * u else left + 110f * u
        translate(cx, 120f * u) {
            drawCircle(gold, 27f * u, Offset.Zero)
            drawCircle(Color(0xFF1F2A23), 27f * u, Offset.Zero, style = Stroke(3f * u))
            drawRoundRect(Color(0xFF1F2A23), Offset(-9f * u, -11f * u), Size(6f * u, 22f * u), CornerRadius(2f * u))
            drawRoundRect(Color(0xFF1F2A23), Offset(3f * u, -11f * u), Size(6f * u, 22f * u), CornerRadius(2f * u))
        }
    }
}
