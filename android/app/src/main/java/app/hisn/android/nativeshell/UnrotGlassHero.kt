package app.hisn.android.nativeshell

import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight

import androidx.compose.material.icons.rounded.Schedule

import androidx.compose.material.icons.rounded.HeartBroken

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisn.android.GlassMoments
import app.hisn.android.R
import app.hisn.core.GlassState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * The Glasshouse hero at the top of Unrot's Today (design/glasshouse-2026-11-23), laid out like
 * CleanPath's home: the painted sprout under glass, its stage name, how long it has been growing,
 * one big number (time given back, never reset), and the next stage with a segmented bar.
 * The sprout's stage comes only from time given back, so it never shrinks.
 */

/** Stage thresholds in time given back: Seed, Sprout, Two leaves, Young plant, Bud, Flower, Tree. */
internal val glassStageAt = longArrayOf(0L, 5 * 60_000L, 15 * 60_000L, 60 * 60_000L, 3 * 3_600_000L, 8 * 3_600_000L, 24 * 3_600_000L)

internal fun glassStage(givenBackMs: Long): Int = glassStageAt.indexOfLast { givenBackMs >= it }.coerceAtLeast(0)

private val glassStageNames = intArrayOf(
    R.string.glass_stage_seed, R.string.glass_stage_sprout, R.string.glass_stage_two_leaves,
    R.string.glass_stage_young, R.string.glass_stage_bud, R.string.glass_stage_flower, R.string.glass_stage_tree
)

/** The painted picture for a stage: one seed, one sprout for the growing stages, one tree. */
internal fun glassArt(stage: Int): Int = when {
    stage <= 0 -> R.drawable.unrot_glass_seed
    stage >= 6 -> R.drawable.unrot_glass_tree
    else -> R.drawable.unrot_glass_sprout
}

private val GlassGold = Color(0xFFF2C14E)
private val GlassGoldLight = Color(0xFFFFDF8A)

@Composable
internal fun UnrotGlassHero(givenBackMs: Long, day: Int, quiet: app.hisn.core.DailyAppSchedule? = null, modifier: Modifier = Modifier) {
    val stage = glassStage(givenBackMs)
    // The glass: cracked by a closed adult page, sealed with gold by a breath, broken by a report.
    val context = androidx.compose.ui.platform.LocalContext.current
    val glass = rememberGlass()
    // Quiet hours: the glass rests at night, blue, and says when feeds open again.
    var minuteTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); minuteTick++ } }
    val resting = remember(quiet, minuteTick) {
        val now = java.time.LocalTime.now()
        quiet != null && runCatching { quiet.activeAt(now.hour * 60 + now.minute, LocalDate.now().dayOfWeek) }.getOrDefault(false)
    }
    val night = resting && (glass.look == GlassState.Look.CLEAR || glass.look == GlassState.Look.GOLD)
    val stageName = if (night) stringResource(R.string.glass_resting) else when (glass.look) {
        GlassState.Look.CRACKED -> stringResource(R.string.glass_cracked)
        GlassState.Look.GOLD -> stringResource(R.string.glass_gold)
        GlassState.Look.BROKEN -> stringResource(R.string.glass_broken)
        else -> stringResource(glassStageNames[stage])
    }
    var explain by remember { mutableStateOf(false) }
    var hard by GlassUi.askHard
    var scrolled by remember { mutableStateOf(false) }
    val locale = androidx.core.os.ConfigurationCompat.getLocales(LocalConfiguration.current)[0]
        ?: java.util.Locale.ENGLISH
    val since = remember(day, locale) {
        LocalDate.now().minusDays((day - 1).coerceAtLeast(0).toLong())
            .format(DateTimeFormatter.ofPattern("d MMM", locale))
    }
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 8.dp, start = 8.dp, end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The painted sprout, faded into the card so it has no hard edge.
        val art = if (night) R.drawable.unrot_glass_night else when (glass.look) {
            GlassState.Look.CRACKED -> R.drawable.unrot_glass_cracked
            GlassState.Look.GOLD -> R.drawable.unrot_glass_gold
            GlassState.Look.BROKEN -> R.drawable.unrot_glass_broken
            else -> glassArt(stage)
        }
        // Between seed and tree the plant is drawn live over the empty glass, so it grows every day.
        val livePlant = !night && glass.look == GlassState.Look.CLEAR && stage in 1..5
        val shown = if (livePlant) R.drawable.unrot_glass_seed else art
        Box(Modifier.fillMaxWidth().height(270.dp), contentAlignment = Alignment.Center) {
        Crossfade(targetState = shown, label = "glass-art") { res ->
            Box(Modifier.fillMaxWidth().height(270.dp), contentAlignment = Alignment.Center) {
                // A soft beam from above, like a lamp over the glass (moonlight at night).
                val beam = if (night) Color(0xFF8DA6FF) else GlassGold
                Box(
                    Modifier.fillMaxWidth().height(270.dp).drawBehind {
                        val w = size.width; val h = size.height
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(w * .42f, 0f); lineTo(w * .58f, 0f); lineTo(w * .86f, h * .92f); lineTo(w * .14f, h * .92f); close()
                        }
                        drawPath(path, Brush.verticalGradient(listOf(beam.copy(alpha = .26f), beam.copy(alpha = .08f), Color.Transparent)))
                        drawCircle(Brush.radialGradient(listOf(beam.copy(alpha = .22f), Color.Transparent),
                            center = center.copy(y = h * .55f), radius = w * .42f), radius = w * .42f, center = center.copy(y = h * .55f))
                    }
                )
                // The painting fades out at its edges, so it sits in the page with no frame.
                Image(
                    painterResource(res),
                    contentDescription = stringResource(R.string.glass_image_desc, stageName),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(270.dp)
                        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.radialGradient(listOf(Color.Black, Color.Black, Color.Transparent), radius = size.minDimension * .5f),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                            )
                        }
                )
            }
        }
            if (livePlant) GlassPlant(givenBackMs, Modifier.size(270.dp))
            GlassAir(night, Modifier.fillMaxWidth().height(270.dp))
        }
        // Seven stages as dots, the current one long, like CleanPath's page dots.
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(7) { i ->
                Box(
                    Modifier
                        .height(5.dp)
                        .width(if (i == stage) 18.dp else 5.dp)
                        .background(if (i <= stage) Color.White else Color.White.copy(alpha = .28f), CircleShape)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "❦  " + stageName.uppercase(locale) + "  ❦",
            style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.ExtraBold),
            color = Color.White,
            modifier = Modifier.semantics { heading() }
        )
        Row(
            Modifier
                .padding(top = 2.dp)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.glass_info)) { explain = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                when {
                    night -> stringResource(R.string.glass_resting_sub, java.time.LocalTime.of(quiet!!.endMinute / 60, quiet.endMinute % 60).toString())
                    glass.look == GlassState.Look.CRACKED ->
                        stringResource(R.string.glass_cracked_sub, Wins.short(glass.sealLeftMs.coerceAtLeast(60_000L)))
                    glass.look == GlassState.Look.GOLD -> stringResource(R.string.glass_gold_sub)
                    glass.look == GlassState.Look.BROKEN -> stringResource(R.string.glass_broken_sub)
                    day <= 1 && givenBackMs <= 0L -> stringResource(R.string.glass_day_one)
                    else -> stringResource(R.string.glass_since, since)
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB8AC94)
            )
            Box(
                Modifier.size(16.dp).border(1.dp, Color(0xFFB8AC94), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("i", fontSize = 10.sp, color = Color(0xFFB8AC94)) }
        }
        if (glass.look == GlassState.Look.CRACKED) SealTimer(glass.sealLeftMs) else GlassTimer(givenBackMs)
        Spacer(Modifier.height(6.dp))
        if ((glass.look == GlassState.Look.CRACKED || glass.look == GlassState.Look.BROKEN) && givenBackMs >= 60_000L) {
            // Nothing given back is ever taken: said plainly while the glass is hurt.
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)) {
                        append(stringResource(R.string.glass_nothing_lost).uppercase(locale))
                    }
                    append("   ")
                    append(stringResource(R.string.glass_stays_yours, Wins.short(givenBackMs.coerceAtLeast(0L))))
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFB8AC94),
                textAlign = TextAlign.Center
            )
        } else if (stage < 6) {
            val next = stringResource(glassStageNames[stage + 1])
            val left = glassStageAt[stage + 1] - givenBackMs
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)) {
                        append(next.uppercase(locale))
                    }
                    append("   ")
                    append(stringResource(R.string.glass_next, Wins.short(left.coerceAtLeast(60_000L))))
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFB8AC94)
            )
            Spacer(Modifier.height(10.dp))
            val part = ((givenBackMs - glassStageAt[stage]).toFloat() / (glassStageAt[stage + 1] - glassStageAt[stage])).coerceIn(0f, 1f)
            GlassSegments(filled = stage, part = part)
        } else {
            Text(stringResource(R.string.glass_top), style = MaterialTheme.typography.labelMedium, color = Color(0xFFB8AC94))
        }
    }
    if (hard) {
        GlassSheet(onDismiss = { hard = false }) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp).padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.hard_moment_title), style = MaterialTheme.typography.headlineSmall.storySerif(),
                    modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.hard_moment_body), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA99D86))
                HardChoice(androidx.compose.material.icons.Icons.Rounded.HeartBroken, stringResource(R.string.hard_moment_adult)) {
                    GlassMoments.broke(context); GlassUi.changed(); hard = false
                }
                HardChoice(androidx.compose.material.icons.Icons.Rounded.Schedule, stringResource(R.string.hard_moment_scroll)) {
                    hard = false; scrolled = true
                }
                TextButton(onClick = { hard = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.hard_moment_not_now), color = Color(0xFFA99D86))
                }
            }
        }
    }
    if (scrolled) {
        AlertDialog(
            onDismissRequest = { scrolled = false },
            confirmButton = { TextButton(onClick = { scrolled = false }) { Text(stringResource(android.R.string.ok)) } },
            text = { Text(stringResource(R.string.hard_moment_scroll_done)) }
        )
    }
    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            confirmButton = { TextButton(onClick = { explain = false }) { Text(stringResource(android.R.string.ok)) } },
            title = { Text(stringResource(R.string.glass_info)) },
            text = { Text(stringResource(R.string.glass_info_body)) }
        )
    }
}

/** While cracked: minutes and seconds left to seal it with a breath, ticking. */
@Composable
private fun SealTimer(leftMs: Long) {
    val left = (leftMs / 1000L).coerceAtLeast(0L)
    val um = stringResource(R.string.glass_unit_m)
    val us = stringResource(R.string.glass_unit_s)
    val desc = stringResource(R.string.glass_cracked_sub, Wins.short(leftMs.coerceAtLeast(60_000L)))
    val big = MaterialTheme.typography.displayMedium
    Text(
        buildAnnotatedString {
            append((left / 60).toString())
            withStyle(SpanStyle(fontSize = big.fontSize * 0.5f, color = Color(0xFFB8AC94))) { append(um) }
            append(" ")
            append((left % 60).toString())
            withStyle(SpanStyle(fontSize = big.fontSize * 0.5f, color = Color(0xFFB8AC94))) { append(us) }
        },
        style = big.copy(fontWeight = FontWeight.SemiBold),
        color = GlassGoldLight,
        textAlign = TextAlign.Center,
        modifier = Modifier.clearAndSetSemantics { contentDescription = desc }
    )
}

/** "0d 0h 59m": the number in big type, the units small, like CleanPath's clean-time clock. */
@Composable
private fun GlassTimer(givenBackMs: Long) {
    val minutes = (givenBackMs / 60_000L).coerceAtLeast(0L)
    val d = minutes / (24 * 60)
    val h = (minutes / 60) % 24
    val m = minutes % 60
    val ud = stringResource(R.string.glass_unit_d)
    val uh = stringResource(R.string.glass_unit_h)
    val um = stringResource(R.string.glass_unit_m)
    val desc = stringResource(R.string.glass_timer_desc, Wins.short(givenBackMs.coerceAtLeast(0L)))
    val big = MaterialTheme.typography.displayMedium
    Text(
        buildAnnotatedString {
            fun part(n: Long, unit: String, last: Boolean = false) {
                append(n.toString())
                withStyle(SpanStyle(fontSize = big.fontSize * 0.5f, color = Color(0xFFB8AC94))) { append(unit) }
                if (!last) append(" ")
            }
            part(d, ud); part(h, uh); part(m, um, last = true)
        },
        style = big.copy(fontWeight = FontWeight.SemiBold),
        color = GlassGoldLight,
        textAlign = TextAlign.Center,
        modifier = Modifier.clearAndSetSemantics { contentDescription = desc }
    )
}

/** Six steps between the seven stages: done ones full, the current one part-filled. */
@Composable
private fun GlassSegments(filled: Int, part: Float) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(6) { i ->
            Box(Modifier.weight(1f).height(7.dp).background(Color.White.copy(alpha = .12f), CircleShape)) {
                val f = when {
                    i < filled -> 1f
                    i == filled -> part
                    else -> 0f
                }
                if (f > 0f) Box(
                    Modifier.fillMaxWidth(f).height(7.dp)
                        .background(Brush.horizontalGradient(listOf(Color(0xFFC98D22), GlassGoldLight)), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun HardChoice(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp)
            .background(Color(0xFF1A1915), RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(36.dp).background(Color.White.copy(alpha = .06f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = Color(0xFFFFDF8A), modifier = Modifier.size(20.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null, tint = Color(0xFFA99D86))
    }
}
