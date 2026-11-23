package app.hisn.android.nativeshell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisn.android.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * Setup v2.2 steps. Researched against the owner's captures of Unchaind (89 screens) and Unrot
 * (64), and CleanPath's recording: one question per screen with every answer used; the app shown
 * working before anything is asked; the real starting point from Android instead of a quiz score;
 * the plan drawn from the person's own choices; a held commitment; a dated first milestone.
 */

internal val SetupButter = Color(0xFFF4C34E)
internal val SetupTomato = Color(0xFFE5553A)
internal val SetupSage = Color(0xFF9EC39A)
internal val SetupSky = Color(0xFF9CC8E0)
internal val SetupBlush = Color(0xFFF2A08F)
internal val SetupPaper = Color(0xFFF5EFE3)

/** The small label above a headline ("ABOUT YOU"), and the headline with an optional coloured word. */
@Composable
internal fun SetupHeader(eyebrow: String, title: String, highlight: String? = null, body: String? = null, center: Boolean = false) {
    val align = if (center) TextAlign.Center else TextAlign.Start
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = align, modifier = Modifier.fillMaxWidth())
        val accent = if (LocalDarkTheme.current) Color(0xFFF2C94F) else Color(0xFFB07A12)
        val styled = androidx.compose.ui.text.buildAnnotatedString {
            val at = highlight?.let { title.indexOf(it) } ?: -1
            if (at < 0) append(title) else {
                append(title.substring(0, at))
                pushStyle(androidx.compose.ui.text.SpanStyle(color = accent))
                append(highlight!!)
                pop()
                append(title.substring(at + highlight.length))
            }
        }
        Text(styled, style = MaterialTheme.typography.headlineLarge.storySerif(), textAlign = align,
            modifier = Modifier.fillMaxWidth().semantics { heading() })
        if (body != null) {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = align, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun PrivateLine(text: Int = R.string.setup_answers_private) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UsedLine(text: String) {
    Text("↳ $text", style = MaterialTheme.typography.labelLarge, color = LocalHisnStatusColors.current.success)
}

// See it work: three demos of the real screens, each with numbered markers and a legend.

@Composable
internal fun DemoStep(page: Int) {
    val (eyebrow, title, highlight) = when (page) {
        0 -> Triple(R.string.demo_1_eyebrow, R.string.demo_1_title, R.string.demo_1_word)
        1 -> Triple(R.string.demo_2_eyebrow, R.string.demo_2_title, R.string.demo_2_word)
        else -> Triple(R.string.demo_3_eyebrow, R.string.demo_3_title, R.string.demo_3_word)
    }
    val legend = when (page) {
        0 -> listOf(R.string.demo_1_a, R.string.demo_1_b, R.string.demo_1_c)
        1 -> listOf(R.string.demo_2_a, R.string.demo_2_b, R.string.demo_2_c)
        else -> listOf(R.string.demo_3_a, R.string.demo_3_b, R.string.demo_3_c)
    }
    SetupHeader(stringResource(eyebrow), stringResource(title), stringResource(highlight))
    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .width(170.dp)
                .height(296.dp)
                .unrotFrame(RoundedCornerShape(28.dp))
                .background(if (page == 2) Color(0xFF242B44) else Color(0xFF141414), RoundedCornerShape(28.dp))
                .semantics(mergeDescendants = true) { }
        ) {
            when (page) {
                0 -> DemoFeedClosed()
                1 -> DemoWait()
                else -> DemoNight()
            }
        }
        listOf(Alignment.TopStart to Offset(-96f, 14f), Alignment.TopEnd to Offset(96f, 100f), Alignment.TopStart to Offset(-96f, 200f))
            .forEachIndexed { index, (align, offset) ->
                Box(Modifier.align(Alignment.TopCenter).offset(x = offset.x.dp, y = offset.y.dp)) { Marker(index + 1) }
            }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        legend.forEachIndexed { index, text ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Marker(index + 1, small = true)
                Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(3) { i ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = if (i == page) 22.dp else 8.dp, height = 8.dp)
                    .background(if (i == page) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), CircleShape)
            )
        }
    }
}

@Composable
private fun Marker(number: Int, small: Boolean = false) {
    Box(
        Modifier
            .size(if (small) 22.dp else 28.dp)
            .background(SetupButter, CircleShape)
            .border(2.dp, UnrotInk, CircleShape),
        contentAlignment = Alignment.Center
    ) { Text("$number", style = MaterialTheme.typography.labelLarge, color = UnrotInk, fontWeight = FontWeight.Bold) }
}

@Composable
private fun DemoFeedClosed() {
    Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.demo_tabs), color = Color(0xFFBBBBBB), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(36.dp))
        Column(
            Modifier.fillMaxWidth().background(SetupPaper, RoundedCornerShape(18.dp)).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) { Box(Modifier.size(width = 6.dp, height = 20.dp).background(SetupTomato, RoundedCornerShape(3.dp))) }
            }
            Text(stringResource(R.string.demo_feed_closed), color = UnrotInk, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.demo_note), color = UnrotInk, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall.storySerif())
            Text(stringResource(R.string.demo_still_work), color = UnrotInk.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DemoWait() {
    val paper = Color(0xFFF3EEE6)
    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Spacer(Modifier.height(18.dp))
        Box(Modifier.size(86.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val s = 7.dp.toPx()
                drawCircle(paper.copy(alpha = 0.15f), radius = size.minDimension / 2 - s, style = Stroke(s))
                drawArc(SetupButter, -90f, 216f, false, topLeft = Offset(s, s),
                    size = Size(size.width - 2 * s, size.height - 2 * s), style = Stroke(s, cap = StrokeCap.Round))
            }
            Text("8", color = paper, style = MaterialTheme.typography.headlineMedium.storySerif())
        }
        Text(stringResource(R.string.try_live_in), color = paper, style = MaterialTheme.typography.titleSmall.storySerif())
        Box(Modifier.fillMaxWidth().background(SetupButter, RoundedCornerShape(12.dp)).padding(9.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.try_live_close), color = UnrotInk, style = MaterialTheme.typography.labelMedium)
        }
        Text(stringResource(R.string.demo_open_anyway), color = paper.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DemoNight() {
    val light = Color(0xFFEDE7F6)
    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Spacer(Modifier.height(22.dp))
        Text("☾", color = light, style = MaterialTheme.typography.displaySmall)
        Text(stringResource(R.string.demo_quiet), color = light, style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.demo_asleep), color = light.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center)
        Text(stringResource(R.string.demo_note), color = light, style = MaterialTheme.typography.bodySmall.storySerif(),
            textAlign = TextAlign.Center)
    }
}

// About you.

@Composable
internal fun NameStep(name: String, onName: (String) -> Unit) {
    SetupHeader(stringResource(R.string.setup_about_you), stringResource(R.string.name_title),
        body = stringResource(R.string.name_body))
    OutlinedTextField(
        value = name,
        onValueChange = { if (it.length <= 24) onName(it.replace("\n", "")) },
        singleLine = true,
        label = { Text(stringResource(R.string.name_label)) },
        textStyle = MaterialTheme.typography.headlineSmall.storySerif(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done
        ),
        modifier = Modifier.fillMaxWidth()
    )
    PrivateLine()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GoalsStep(goals: Set<String>, onToggle: (String) -> Unit) {
    SetupHeader(stringResource(R.string.setup_goals_eyebrow), stringResource(R.string.goals_title),
        body = stringResource(R.string.goals_body))
    val rows = UnrotAnswers.GOALS.chunked(2)
    val art = UnrotAnswers.GOALS.associateWith { goalArt(it) }
    rows.forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { key ->
                val on = key in goals
                val shape = MaterialTheme.shapes.large
                // Premium tile: a floating 3D object over the words; chosen ones get a gold edge.
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 150.dp)
                        .unrotFrame(shape, hero = on)
                        .background(if (on) chosenFill() else LocalHisnStatusColors.current.card, shape)
                        .then(if (on) Modifier.border(2.dp, SetupButter, shape) else Modifier)
                        .toggleable(value = on, role = Role.Checkbox) { onToggle(key) }
                        .padding(12.dp)
                ) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Float3D(art.getValue(key), 58.dp)
                        Text(stringResource(UnrotAnswers.goalTitle(key)), style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                        Text(stringResource(UnrotAnswers.goalBody(key)), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                    if (on) Icon(Icons.Rounded.Check, contentDescription = null, tint = if (LocalDarkTheme.current) SetupButter else UnrotInk,
                        modifier = Modifier.align(Alignment.TopEnd).size(20.dp))
                }
            }
        }
    }
}

/** One answer per tap; the step moves on by itself, like Unchaind's question cards. */
@Composable
internal fun WhenStep(selected: String, onPick: (String) -> Unit) {
    SetupHeader(stringResource(R.string.setup_about_you), stringResource(R.string.when_title),
        body = stringResource(R.string.when_body))
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UnrotAnswers.WHEN.forEachIndexed { index, key ->
            AnswerCard(index + 1, stringResource(UnrotAnswers.whenTitle(key)), key == selected, Role.RadioButton,
                art = if (unrotEdition) whenArt(key) else null) { onPick(key) }
        }
    }
    UsedLine(stringResource(R.string.when_used))
    PrivateLine()
}

@Composable
internal fun FeelStep(selected: Set<String>, onToggle: (String) -> Unit) {
    SetupHeader(stringResource(R.string.setup_about_you), stringResource(R.string.feel_title),
        body = stringResource(R.string.feel_body))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UnrotAnswers.FEEL.forEachIndexed { index, key ->
            AnswerCard(index + 1, stringResource(UnrotAnswers.feelTitle(key)), key in selected, Role.Checkbox) { onToggle(key) }
        }
    }
    UsedLine(stringResource(R.string.feel_used))
    PrivateLine()
}

@Composable
private fun AnswerCard(number: Int, text: String, selected: Boolean, role: Role, art: Int? = null, onClick: () -> Unit) {
    val haptics = rememberHisnHaptics()
    val shape = MaterialTheme.shapes.large
    val content = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .unrotFrame(shape, hero = selected)
            .background(if (selected) chosenFill() else LocalHisnStatusColors.current.card, shape)
            .then(if (selected) Modifier.border(2.dp, SetupButter, shape) else Modifier)
            .selectable(selected = selected, role = role) { haptics.tick(); onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (art != null) Float3D(art, 40.dp, glow = selected)
        else Box(Modifier.size(24.dp).border(2.dp, content, CircleShape), contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.labelMedium, color = content)
        }
        Text(text, style = MaterialTheme.typography.titleSmall, color = content, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = if (LocalDarkTheme.current) SetupButter else UnrotInk)
    }
}

/** The 3D object for "when it gets you": the moment, as a thing you can see. */
internal fun whenArt(key: String): Int = when (key) {
    "night" -> R.drawable.unrot_3d_moon
    "morning" -> R.drawable.unrot_3d_sun
    "bored" -> R.drawable.unrot_3d_bulb
    "stressed" -> R.drawable.unrot_3d_heart
    else -> R.drawable.unrot_3d_book
}

/** The 3D object for a goal, the same in setup and on Progress. */
internal fun goalArt(key: String): Int = when (key) {
    "sleep" -> R.drawable.unrot_3d_moon
    "focus" -> R.drawable.unrot_3d_target
    "evenings" -> R.drawable.unrot_3d_heart
    "adult" -> R.drawable.unrot_3d_shield
    "mornings" -> R.drawable.unrot_3d_sun
    else -> R.drawable.unrot_3d_leaf
}

/** The warm fill of a chosen answer: butter on ivory, a gold tint at night. */
@Composable
internal fun chosenFill(): Color = if (LocalDarkTheme.current) Color(0xFF2A2416) else Color(0xFFFFF4D6)

// Your real starting point.

@Composable
internal fun StartStep(point: StartingPoint?, usageOn: Boolean, guessMinutes: Int, labels: Map<String, String>, onGuess: (Int) -> Unit) {
    if (!usageOn) {
        SetupHeader(stringResource(R.string.start_permission_eyebrow), stringResource(R.string.start_title),
            body = stringResource(R.string.start_permission_body))
        val shape = MaterialTheme.shapes.large
        Column(
            Modifier.fillMaxWidth().unrotFrame(shape).background(LocalHisnStatusColors.current.card, shape).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(stringResource(R.string.start_why_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.start_why_body), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.start_where), style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(8.dp))
        }
        Column(
            Modifier.fillMaxWidth().unrotFrame(shape).background(LocalHisnStatusColors.current.card, shape).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(stringResource(R.string.start_guess_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.start_guess_body), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = guessMinutes.toFloat(), onValueChange = { onGuess((it / 15f).roundToInt() * 15) },
                valueRange = 0f..360f)
            Text(stringResource(R.string.start_guess_value, Usage.formatDuration(guessMinutes * 60_000L)),
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        return
    }
    SetupHeader(stringResource(R.string.start_eyebrow), stringResource(R.string.start_title),
        body = stringResource(R.string.start_body))
    if (point == null) {
        Text("…", style = MaterialTheme.typography.displaySmall)
        return
    }
    if (!point.hasData) {
        Text(stringResource(R.string.start_empty), style = MaterialTheme.typography.bodyLarge)
        return
    }
    // A half gauge: the share of a 6-hour scale, so a typical day reads at a glance.
    Box(Modifier.fillMaxWidth().height(132.dp), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.width(240.dp).height(128.dp)) {
            val stroke = 18.dp.toPx()
            val r = size.width / 2 - stroke / 2
            val tl = Offset(size.width / 2 - r, size.height - r - stroke / 2)
            val box = Size(r * 2, r * 2)
            drawArc(Color(0xFFEDE4D3), 180f, 180f, false, tl, box, style = Stroke(stroke, cap = StrokeCap.Round))
            val share = (point.dailyMs / (6 * 3_600_000f)).coerceIn(0.02f, 1f)
            drawArc(SetupTomato, 180f, 180f * share, false, tl, box, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Usage.formatDuration(point.dailyMs), style = MaterialTheme.typography.displaySmall.storySerif(),
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.start_gauge_label), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("${point.opensPerDay}", stringResource(R.string.start_opens), Modifier.weight(1f))
        StatTile(Usage.formatDuration(point.longestMs), stringResource(R.string.start_longest), Modifier.weight(1f))
    }
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier.fillMaxWidth().unrotFrame(shape).background(LocalHisnStatusColors.current.card, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val top = point.apps.firstOrNull()?.second?.coerceAtLeast(1L) ?: 1L
        point.apps.take(3).forEach { (pkg, ms) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(pkg, size = 28.dp)
                Text(labels[pkg] ?: pkg, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(88.dp), maxLines = 1)
                Box(Modifier.weight(1f).height(10.dp).background(Color(0xFFEDE4D3), RoundedCornerShape(5.dp))) {
                    Box(Modifier.fillMaxWidth(ms.toFloat() / top).height(10.dp).background(SetupTomato, RoundedCornerShape(5.dp)))
                }
                Text(Usage.formatDuration(ms), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (point.hardestHour >= 0) {
            Text(stringResource(R.string.start_hardest, setupHour(point.hardestHour)), style = MaterialTheme.typography.labelLarge,
                color = SetupTomato)
            HourBars24(point.hours, point.hardestHour)
        }
    }
    PrivateLine(R.string.start_private)
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    val shape = MaterialTheme.shapes.large
    Column(modifier.unrotFrame(shape).background(LocalHisnStatusColors.current.card, shape).padding(12.dp)
        .semantics(mergeDescendants = true) { }) {
        Text(value, style = MaterialTheme.typography.headlineSmall.storySerif())
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HourBars24(hours: LongArray, hardest: Int) {
    val most = (hours.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val ink = MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        hours.forEachIndexed { h, v ->
            val hot = h == hardest || h == (hardest + 1) % 24
            Box(Modifier.weight(1f).height(34.dp * (v.toFloat() / most).coerceAtLeast(0.08f))
                .background(if (hot) SetupTomato else ink.copy(alpha = 0.18f), RoundedCornerShape(2.dp)))
        }
    }
}

internal fun setupHour(hour: Int) = String.format(java.util.Locale.ROOT, "%02d:00", ((hour % 24) + 24) % 24)

// What it adds up to.

@Composable
internal fun YearStep(name: String, dailyMs: Long, estimate: Boolean, ageKey: String = "") {
    val days = (dailyMs * 365 / 86_400_000L).toInt().coerceIn(0, 365)
    val back = days / 2
    val time = Usage.formatDuration(dailyMs)
    // The number climbs with the dots.
    val daysText = stringResource(R.string.year_days, countUp(days.toLong(), 2000).toInt())
    val title = if (name.isBlank()) stringResource(if (estimate) R.string.year_title_about else R.string.year_title, time, daysText)
        else stringResource(if (estimate) R.string.year_title_named_about else R.string.year_title_named, name, time, daysText)
    SetupHeader(stringResource(R.string.year_eyebrow), title, daysText, stringResource(R.string.year_body))
    val faded = if (LocalDarkTheme.current) Color(0xFF2B2822) else Color(0xFFEDE4D3)
    // The year fills in front of you: feed days first, then the half you could take back.
    val reduceMotion = LocalReduceMotion.current
    val reveal = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val haptics = rememberHisnHaptics()
    LaunchedEffect(days) {
        reveal.animateTo(1f, tween(1800, delayMillis = 250, easing = FastOutSlowInEasing))
        haptics.confirm()
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(21f / 18f)
            .semantics { contentDescription = title }
    ) {
        val cols = 21
        val cell = size.width / cols
        val r = cell * 0.36f
        for (i in 0 until 365) {
            val c = i % cols
            val row = i / cols
            val shown = (reveal.value * days).toInt()
            val color = when {
                i >= shown -> faded
                i < days - back -> SetupTomato
                i < days -> SetupSage
                else -> faded
            }
            // The newest dot lands slightly larger, so the fill reads as movement.
            val grow = if (i in (shown - 3) until shown && reveal.value < 1f) 1.25f else 1f
            drawCircle(color, r * grow, Offset(c * cell + cell / 2, row * cell + cell / 2))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendDot(SetupTomato, stringResource(R.string.year_legend_feeds, days - back))
        LegendDot(SetupSage, stringResource(R.string.year_legend_back, back))
    }
    // With an age, the same pace as years of a life to 75: said once, plainly.
    lifetimeYears(ageKey, dailyMs)?.takeIf { it >= 0.1 }?.let { years ->
        Text(stringResource(R.string.year_lifetime, java.text.DecimalFormat("0.#").format(years)),
            style = MaterialTheme.typography.titleMedium.storySerif(), modifier = Modifier.padding(top = 4.dp))
    }
    val shape = MaterialTheme.shapes.large
    Text(
        stringResource(R.string.year_goal, back),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().unrotFrame(shape).background(LocalHisnStatusColors.current.card, shape).padding(14.dp)
    )
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Your time back.

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InsteadStep(name: String, backDays: Int, picks: Set<String>, onToggle: (String) -> Unit) {
    val daysText = stringResource(R.string.year_days, backDays.coerceAtLeast(1))
    val title = if (name.isBlank()) stringResource(R.string.instead_title, daysText)
        else stringResource(R.string.instead_title_named, name, daysText)
    SetupHeader(stringResource(R.string.instead_eyebrow), title, daysText, stringResource(R.string.instead_body))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UnrotAnswers.INSTEAD.forEach { key ->
            FilterChip(selected = key in picks, onClick = { onToggle(key) },
                label = { Text(stringResource(UnrotAnswers.insteadTitle(key))) })
        }
    }
    val context = LocalContext.current
    val preview = UnrotAnswers.INSTEAD.filter { it in picks }.take(2).map { context.getString(UnrotAnswers.insteadAction(it)) }
    if (preview.isNotEmpty()) {
        val shape = MaterialTheme.shapes.large
        Column(Modifier.fillMaxWidth().unrotFrame(shape).background(LocalHisnStatusColors.current.card, shape).padding(14.dp)) {
            Text(stringResource(R.string.instead_preview_label), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.answers_instead_line, preview.joinToString(" · ")),
                style = MaterialTheme.typography.titleMedium.storySerif())
        }
    }
}

// Your plan, drawn from the person's own choices.

@Composable
internal fun PlanStep(name: String, nodes: List<Pair<String, String>>, extra: String?) {
    val title = if (name.isBlank()) stringResource(R.string.plan_title) else stringResource(R.string.plan_title_named, name)
    SetupHeader(stringResource(R.string.plan_eyebrow), title, stringResource(R.string.plan_word))
    val ink = MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(Modifier.fillMaxWidth().height(300.dp)) {
        val shown = nodes.take(4)
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val corners = listOf(Offset(size.width * 0.22f, size.height * 0.14f), Offset(size.width * 0.78f, size.height * 0.14f),
                Offset(size.width * 0.22f, size.height * 0.86f), Offset(size.width * 0.78f, size.height * 0.86f))
            corners.take(shown.size).forEach {
                drawLine(ink, c, it, 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f)))
            }
        }
        Float3D(R.drawable.unrot_3d_pause, 104.dp, Modifier.align(Alignment.Center))
        val aligns = listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)
        shown.forEachIndexed { i, (head, body) ->
            val shape = MaterialTheme.shapes.medium
            Column(
                Modifier.align(aligns[i]).widthIn(max = maxWidth * 0.46f)
                    .unrotFrame(shape).background(LocalHisnStatusColors.current.card, shape).padding(10.dp)
                    .semantics(mergeDescendants = true) { }
            ) {
                Text(head, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
    if (extra != null) {
        Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

// The three notifications, exactly as they look.

@Composable
internal fun NotifyStep(note: String) {
    SetupHeader(stringResource(R.string.notify_eyebrow), stringResource(R.string.notify_title), stringResource(R.string.notify_word),
        stringResource(R.string.notify_body))
    Column(
        Modifier.fillMaxWidth()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF2E2A20), Color(0xFF14130F))), RoundedCornerShape(24.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("21:45", color = Color(0xFFF3EEE6), style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        listOf(
            R.string.notify_when_1 to (if (note.isBlank()) stringResource(R.string.nudge_body) else stringResource(R.string.notify_1, note)),
            R.string.notify_when_2 to stringResource(R.string.notify_2),
            R.string.notify_when_3 to stringResource(R.string.notify_3)
        ).forEach { (time, text) ->
            Column(Modifier.fillMaxWidth().background(Color(0xE6FFFDF8), RoundedCornerShape(14.dp)).padding(10.dp)) {
                Text(stringResource(time), style = MaterialTheme.typography.labelSmall, color = Color(0xFF7A8277))
                Text(text, style = MaterialTheme.typography.bodySmall, color = UnrotInk)
            }
        }
    }
    Text(stringResource(R.string.notify_never), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(12.dp))
}

// Hold to begin.

@Composable
internal fun ColumnScope.HoldStep(note: String, goals: String, onDone: () -> Unit) {
    val haptics = rememberHisnHaptics()
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var job by remember { mutableStateOf<Job?>(null) }
    var done by remember { mutableStateOf(false) }
    val view = androidx.compose.ui.platform.LocalView.current
    SetupHeader(stringResource(R.string.hold_eyebrow), stringResource(R.string.hold_title), body = stringResource(R.string.hold_body), center = true)
    Spacer(Modifier.height(16.dp))
    val label = stringResource(R.string.hold_action)
    Box(
        Modifier
            .align(Alignment.CenterHorizontally)
            .size(200.dp)
            .semantics {
                contentDescription = label
                onClick(label) { done = true; haptics.confirm(); onDone(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    if (done) return@detectTapGestures
                    job = scope.launch {
                        // Real time, so "remove animations" does not skip the three seconds.
                        val start = android.os.SystemClock.uptimeMillis()
                        var beat = -1
                        while (progress.value < 1f) {
                            val p = ((android.os.SystemClock.uptimeMillis() - start) / 3000f).coerceAtMost(1f)
                            progress.snapTo(p)
                            // A building pulse, faster and firmer as the ring fills.
                            val step = (p * p * 24).toInt()
                            if (step > beat) { beat = step; UnrotFeel.pulse(view, p) }
                            kotlinx.coroutines.delay(16)
                        }
                        done = true
                        haptics.celebrate()
                        onDone()
                    }
                    tryAwaitRelease()
                    if (!done) {
                        job?.cancel()
                        scope.launch { progress.animateTo(0f, tween(250, easing = LinearEasing)) }
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        val ink = MaterialTheme.colorScheme.onSurface
        Canvas(Modifier.fillMaxSize()) {
            val s = 10.dp.toPx()
            drawCircle(track, size.minDimension / 2 - s, style = Stroke(s))
            drawArc(if (unrotEdition) Color(0xFFF2C94F) else ink, -90f, 360f * progress.value, false, Offset(s, s), Size(size.width - 2 * s, size.height - 2 * s),
                style = Stroke(s, cap = StrokeCap.Round))
        }
        Float3D(R.drawable.unrot_3d_pause, 140.dp)
    }
    Spacer(Modifier.height(12.dp))
    if (note.isNotBlank()) {
        Text(stringResource(R.string.personal_quote, note), style = MaterialTheme.typography.titleLarge.storySerif(),
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    if (goals.isNotBlank()) {
        Text(stringResource(R.string.hold_for, goals), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

// The finish: a dated first milestone and the plan.

@Composable
internal fun FirstWeekArch(onGreen: Boolean) {
    val content = if (onGreen) UnrotFinishPaper else MaterialTheme.colorScheme.onSurface
    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.width(270.dp).height(146.dp)) {
            val s = 14.dp.toPx()
            val r = size.width / 2 - s / 2
            val tl = Offset(size.width / 2 - r, size.height - r - s / 2)
            drawArc(content.copy(alpha = 0.25f), 180f, 180f, false, tl, Size(r * 2, r * 2), style = Stroke(s, cap = StrokeCap.Round))
            drawArc(SetupButter, 180f, 180f / 7f, false, tl, Size(r * 2, r * 2), style = Stroke(s, cap = StrokeCap.Round))
        }
        Column(Modifier.padding(top = 46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("7", style = MaterialTheme.typography.displayMedium.storySerif(), color = SetupButter)
            Text(stringResource(R.string.finish_days), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp), color = content)
        }
    }
}

// Age: optional, on this phone, and only used to make the year numbers personal.

internal val AgeBands = listOf("u18" to 16, "18" to 21, "25" to 30, "35" to 40, "45" to 50, "55" to 62)

@Composable
internal fun AgeStep(selected: String, onPick: (String) -> Unit) {
    SetupHeader(stringResource(R.string.setup_about_you), stringResource(R.string.age_title),
        body = stringResource(R.string.age_body))
    val labels = listOf(R.string.age_u18, R.string.age_18, R.string.age_25, R.string.age_35, R.string.age_45, R.string.age_55)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AgeBands.forEachIndexed { index, (key, _) ->
            AnswerCard(index + 1, stringResource(labels[index]), key == selected, Role.RadioButton) { onPick(key) }
        }
    }
    UsedLine(stringResource(R.string.age_used))
    PrivateLine()
}

/** Years of a life, from the person's age band to 75, that the current daily pace adds up to. */
internal fun lifetimeYears(ageKey: String, dailyMs: Long): Double? {
    val age = AgeBands.firstOrNull { it.first == ageKey }?.second ?: return null
    val years = (75 - age).coerceAtLeast(1)
    return dailyMs / 86_400_000.0 * years
}
