package app.hisn.android.nativeshell

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hisn.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The problem, then the answer. CleanPath's order (your problem → why it's hard → the fix) with
 * honest content: the person's own numbers or answers, research with its source, no score and no
 * scare images. Two setup steps after "what it adds up to", and the same content in the app under
 * "Why this works", refreshed with the latest week.
 */

private val CostSky = Color(0xFFDCEBF4)
private val CostLilac = Color(0xFFE6DFF6)
private val CostBlush = Color(0xFFFBE3DD)
private val CostSage = Color(0xFFDCEBD8)
private val CostRed = Color(0xFFB83F29)

/** Hours counted as "late": 22:00 to 04:59. */
private val LATE_HOURS = listOf(22, 23, 0, 1, 2, 3, 4)

/** Average feed time a night between 22:00 and 05:00, from the seven-day hour totals. */
internal fun lateNightMs(point: StartingPoint?): Long =
    if (point == null || !point.hasData) 0L else LATE_HOURS.sumOf { point.hours[it] } / StartingPoints.DAYS

/** A 3D icon from Unrot's own set. */
@Composable
internal fun Icon3D(icon: Int, tile: Color, size: Dp = 58.dp) {
    // Premium: the object floats on its own; the tile colour is kept only for the call sites' meaning.
    Float3D(icon, size)
}

@Composable
private fun Source(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
}

@Composable
private fun CostCard(icon: Int, tile: Color, title: String, yours: String?, body: String, source: String) {
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier
            .fillMaxWidth()
            .unrotFrame(shape)
            .background(LocalHisnStatusColors.current.card, shape)
            .padding(12.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon3D(icon, tile)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.storySerif())
            if (yours != null) {
                Text(yours, style = MaterialTheme.typography.labelLarge,
                    color = if (LocalDarkTheme.current) SetupBlush else CostRed)
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Source(source)
        }
    }
}

/**
 * What it costs: focus, sleep and mood, each with the person's own number or answer when there is
 * one, and adult content when they asked to block it. Ends on recovery, not fear.
 */
@Composable
internal fun CostContent(point: StartingPoint?, goals: Set<String>, whenKey: String, feel: Set<String>, adult: Boolean) {
    val context = LocalContext.current
    val hasData = point?.hasData == true
    val focusYours = when {
        hasData && point!!.opensPerDay > 0 -> context.resources.getQuantityString(R.plurals.why_cost_focus_yours,
            point.opensPerDay, point.opensPerDay)
        "focus" in goals -> stringResource(R.string.why_cost_focus_said)
        else -> null
    }
    val late = lateNightMs(point)
    val sleepYours = when {
        late >= 10 * 60_000L -> stringResource(R.string.why_cost_sleep_yours, Usage.formatDuration(late))
        whenKey == "night" || "sleep" in goals -> stringResource(R.string.why_cost_sleep_said)
        else -> null
    }
    val feelWords = listOf("tired", "behind", "anxious", "guilty", "numb").filter { it in feel }
    val moodYours = if (feelWords.isEmpty()) null else stringResource(R.string.why_cost_mood_said,
        feelWords.take(2).joinToString(context.getString(R.string.answers_and)) { context.getString(UnrotAnswers.feelWord(it)) })
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CostCard(R.drawable.unrot_3d_target, CostSky, stringResource(R.string.why_cost_focus), focusYours,
            stringResource(R.string.why_cost_focus_body), stringResource(R.string.why_cost_focus_src))
        CostCard(R.drawable.unrot_3d_moon, CostLilac, stringResource(R.string.why_cost_sleep), sleepYours,
            stringResource(R.string.why_cost_sleep_body), stringResource(R.string.why_cost_sleep_src))
        CostCard(R.drawable.unrot_3d_heart, CostBlush, stringResource(R.string.why_cost_mood), moodYours,
            stringResource(R.string.why_cost_mood_body), stringResource(R.string.why_cost_mood_src))
        if (adult) {
            CostCard(R.drawable.unrot_3d_lock, CostSage, stringResource(R.string.why_cost_adult),
                stringResource(R.string.why_cost_adult_yours),
                stringResource(R.string.why_cost_adult_body), stringResource(R.string.why_cost_adult_src))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("↗", style = MaterialTheme.typography.titleMedium, color = LocalHisnStatusColors.current.success)
            Text(stringResource(R.string.why_cost_hope), style = MaterialTheme.typography.labelLarge,
                color = LocalHisnStatusColors.current.success)
        }
    }
}

/** A tiny phone whose feed scrolls by itself: no bottom, every few posts a bright one. */
@Composable
private fun EndlessFeed(modifier: Modifier) {
    val still = LocalReduceMotion.current
    val shift by if (still) remember { androidx.compose.runtime.mutableFloatStateOf(0f) } else
        rememberInfiniteTransition(label = "feed").animateFloat(
            0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "feed shift"
        )
    val paper = Color(0xFFF3ECDF)
    Canvas(modifier.clipToBounds()) {
        val pad = 6.dp.toPx()
        val postH = 30.dp.toPx()
        val gap = 6.dp.toPx()
        val step = postH + gap
        // Four posts make one loop, so the bright post comes back at the same place.
        val offset = shift * step * 4
        var i = 0
        var y = pad - offset
        while (y < size.height) {
            if (y + postH > 0) {
                val bright = i % 4 == 3
                drawRoundRect(
                    if (bright) SetupButter else paper.copy(alpha = 0.16f),
                    Offset(pad, y), Size(size.width - pad * 2, postH), CornerRadius(6.dp.toPx())
                )
            }
            y += step; i++
        }
    }
}

@Composable
private fun Callout(icon: Int, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        // Cream tile: the grey icons would vanish on the dark panel.
        Float3D(icon, 32.dp)
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall.storySerif(), color = SetupButter)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Color(0xFFF3ECDF))
        }
    }
}

/** Why it's hard: the feed is built this way, and a short pause is what breaks it. */
@Composable
internal fun HardContent() {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF1B1A16), shape).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(84.dp).height(156.dp)
                .border(3.dp, Color(0xFFF3ECDF), RoundedCornerShape(16.dp))
                .background(Color(0xFF2A2416), RoundedCornerShape(16.dp))
                .padding(3.dp)
        ) { EndlessFeed(Modifier.fillMaxSize()) }
        // The panel is dark in both themes, so its objects get the night treatment.
        androidx.compose.runtime.CompositionLocalProvider(LocalDarkTheme provides true) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Callout(R.drawable.unrot_3d_mobile, stringResource(R.string.why_hard_bottom), stringResource(R.string.why_hard_bottom_body))
            Callout(R.drawable.unrot_3d_bulb, stringResource(R.string.why_hard_next), stringResource(R.string.why_hard_next_body))
            Callout(R.drawable.unrot_3d_target, stringResource(R.string.why_hard_there), stringResource(R.string.why_hard_there_body))
        }
        }
    }
    val card = MaterialTheme.shapes.large
    Row(
        Modifier.fillMaxWidth().unrotFrame(card).background(LocalHisnStatusColors.current.card, card).padding(14.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                drawCircle(track, size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
                drawArc(SetupButter, -90f, 360f * 0.72f, false,
                    Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Image(painterResource(R.drawable.unrot_3d_pause), contentDescription = null, modifier = Modifier.size(34.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.why_hard_pause), style = MaterialTheme.typography.titleMedium.storySerif())
            Text(stringResource(R.string.why_hard_pause_body, 57), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Source(stringResource(R.string.why_hard_pause_src))
        }
    }
    Text(stringResource(R.string.why_hard_not_you), style = MaterialTheme.typography.titleMedium.storySerif(),
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
internal fun CostStep(point: StartingPoint?, goals: Set<String>, whenKey: String, feel: Set<String>, adult: Boolean) {
    val title = stringResource(R.string.why_cost_title)
    SetupHeader(stringResource(R.string.why_cost_eyebrow), title, stringResource(R.string.why_cost_title_mark))
    CostContent(point, goals, whenKey, feel, adult)
}

@Composable
internal fun HardStep() {
    SetupHeader(stringResource(R.string.why_hard_eyebrow), stringResource(R.string.why_hard_title),
        stringResource(R.string.why_hard_title_mark))
    HardContent()
}

/** In the app: the same two pages, with the latest week's numbers and the person's saved answers. */
@Composable
internal fun UnrotWhyScreen(state: HisnState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val point by produceState<StartingPoint?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val feeds = runCatching { FeedHistories.feedApps(context, state) }.getOrDefault(emptySet())
            StartingPoints.read(context, feeds)
        }
    }
    val goals = remember { UnrotAnswers.goals(context) }
    val whenKey = remember { UnrotAnswers.whenKey(context) }
    val feel = remember { UnrotAnswers.feel(context) }
    val adult = state.tags.on || "adult" in goals
    DetailScaffold(stringResource(R.string.why_screen_title), onBack) { innerPadding ->
        LazyColumn(
            Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(stringResource(R.string.why_cost_eyebrow).uppercase(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
            }
            item { CostContent(point, goals, whenKey, feel, adult) }
            item {
                Text(stringResource(R.string.why_hard_eyebrow).uppercase(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
            }
            item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { HardContent() } }
            item {
                androidx.compose.material3.TextButton(onClick = { onOpen("info:how") }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.why_how_link))
                }
            }
        }
    }
}

/** Each part of the problem, and what Unrot does about it: the answer after "why it's hard". */
private val SolveRows = listOf(
    Triple(app.hisn.android.R.drawable.unrot_3d_mobile, R.string.solve_feed_problem, R.string.solve_feed_fix),
    Triple(app.hisn.android.R.drawable.unrot_3d_pause, R.string.solve_auto_problem, R.string.solve_auto_fix),
    Triple(app.hisn.android.R.drawable.unrot_3d_moon, R.string.solve_night_problem, R.string.solve_night_fix),
    Triple(app.hisn.android.R.drawable.unrot_3d_battery, R.string.solve_time_problem, R.string.solve_time_fix),
    Triple(app.hisn.android.R.drawable.unrot_3d_shield, R.string.solve_adult_problem, R.string.solve_adult_fix)
)

@Composable
internal fun SolveStep() {
    SetupHeader(stringResource(R.string.solve_eyebrow), stringResource(R.string.solve_title), stringResource(R.string.solve_title_mark))
    val gold = if (LocalDarkTheme.current) Color(0xFFF2C94F) else Color(0xFF9A6E12)
    SolveRows.forEachIndexed { i, (art, problem, fix) ->
        Row(
            Modifier.fillMaxWidth().riseIn(i + 1).unrotFrame(MaterialTheme.shapes.large)
                .background(LocalHisnStatusColors.current.card, MaterialTheme.shapes.large)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Float3D(art, 48.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(problem), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(fix), style = MaterialTheme.typography.titleSmall, color = gold)
            }
        }
    }
    Text(stringResource(R.string.solve_foot), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}
