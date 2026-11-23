package app.hisn.android.nativeshell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.hisn.android.GlassMoments
import app.hisn.android.R
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/*
 * Your wood (Glasshouse round 11, after Forest): every day with Unrot becomes a tree on the hill
 * around the glasshouse. Its size is that day's time given back; a heavy day is a small bush,
 * never a dead tree. Only the day adult content got through (the glass broke) stays a dry tree.
 * Kinds of tree are earned with days, never bought; in winter pines wear snow. Today is the
 * sapling still growing. Drawn in code in the paintings' cartoon style, swaying in the wind.
 */

internal enum class TreeKind(val unlockDay: Int, val label: Int) {
    OAK(1, R.string.wood_oak), PINE(7, R.string.wood_pine), CHERRY(14, R.string.wood_cherry),
    LEMON(21, R.string.wood_lemon), OLIVE(30, R.string.wood_olive), WILLOW(45, R.string.wood_willow)
}

internal class WoodTree(val date: LocalDate, val gainMs: Long, val kind: TreeKind?, val dry: Boolean, val today: Boolean)

/** The kind of tree a day grew: one of the kinds unlocked by then, picked steadily by its date. */
internal fun treeKindFor(date: LocalDate, dayNumber: Int): TreeKind {
    val open = TreeKind.entries.filter { it.unlockDay <= dayNumber.coerceAtLeast(1) }
    val h = ((date.toEpochDay() * 2654435761L) ushr 7).toInt() and 0x7fffffff
    // The newest kind shows up more often, so a fresh unlock is seen at once.
    return if (h % 3 == 0) open.last() else open[h % open.size]
}

private fun winter(date: LocalDate) = date.month == Month.DECEMBER || date.month == Month.JANUARY || date.month == Month.FEBRUARY

internal fun woodTrees(w: WinsSummary, brokenDay: Long): List<WoodTree> {
    val days = w.daily.mapIndexed { i, feed ->
        val date = w.start.plusDays(i.toLong())
        WoodTree(date, (w.base.feedMs - feed).coerceAtLeast(0L), treeKindFor(date, i + 1), date.toEpochDay() == brokenDay, false)
    }
    val today = LocalDate.now()
    return days + WoodTree(today, 0L, null, today.toEpochDay() == brokenDay, true)
}

private val Bark = Color(0xFF7A4A24)
private val BarkLight = Color(0xFFA36A3A)
private val GlassGoldW = Color(0xFFF2C14E)

@Composable
internal fun UnrotWoodCard(w: WinsSummary) {
    val context = LocalContext.current
    val reduce = LocalReduceMotion.current
    val haptics = rememberHisnHaptics()
    val trees = remember(w) { woodTrees(w, GlassMoments.brokenDay(context)).takeLast(MAX_TREES) }
    var picked by remember(trees) { mutableIntStateOf(trees.lastIndex) }
    val appear = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) appear.animateTo(1f, tween(1800, easing = LinearEasing)) }
    val wind = rememberInfiniteTransition(label = "wood-wind")
    val phase by wind.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val locale = appLocale()
    val fmt = remember(locale) { DateTimeFormatter.ofPattern("EEE d MMM", locale) }
    val nextKind = TreeKind.entries.firstOrNull { it.unlockDay > w.day }
    val names = TreeKind.entries.associateWith { stringResource(it.label) }
    val woodDesc = stringResource(R.string.wood_desc, trees.size, Wins.short(w.givenBackMs))

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0xFF15100A)).padding(bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp)) {
            Text(stringResource(R.string.wood_title), style = MaterialTheme.typography.titleLarge.storySerif(), color = Color.White,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.wood_sub, trees.size), style = MaterialTheme.typography.bodySmall, color = Color(0xFFB8AC94))
        }
        val layout = remember(trees) { placeTrees(trees.size) }
        Canvas(
            Modifier.fillMaxWidth().height(230.dp)
                .semantics { contentDescription = woodDesc }
                .pointerInput(trees) {
                    detectTapGestures { tap ->
                        val wpx = size.width.toFloat(); val hpx = size.height.toFloat()
                        val best = layout.indices.minByOrNull { i ->
                            val (x, y) = layout[i].pos(wpx, hpx)
                            abs(x - tap.x) + abs(y - hpx * .12f - tap.y) * .6f
                        }
                        if (best != null && best != picked) { picked = best; haptics.tick() }
                    }
                }
        ) {
            val p = if (reduce) 0f else phase
            drawHill(p)
            // Back rows first, so the front trees stand over them.
            layout.indices.sortedBy { layout[it].row * -1 }.forEach { i ->
                val spot = layout[i]
                val t = trees[i]
                val (x, y) = spot.pos(size.width, size.height)
                val grow = easeBack(((appear.value - i * (.7f / layout.size)) / .3f).coerceIn(0f, 1f))
                if (grow <= 0f) return@forEach
                val depth = 1f - spot.row * .22f
                val full = size.height * .36f * depth
                val sizeFactor = if (t.today) .38f else (.42f + .58f * (t.gainMs / (w.base.feedMs * .5f).coerceAtLeast(1f))).coerceIn(.42f, 1f)
                val h = full * sizeFactor * grow
                val sway = sin(p + i * .8f) * 2.2f
                val haze = spot.row * .22f
                if (i == picked) {
                    drawCircle(Brush.radialGradient(listOf(GlassGoldW.copy(alpha = .35f), Color.Transparent), center = Offset(x, y - h * .5f), radius = h * .9f), h * .9f, Offset(x, y - h * .5f))
                }
                drawTree(t, Offset(x, y), h, sway, p + i, haze)
            }
            // A few fireflies over the wood.
            if (!reduce) for (k in 0 until 7) {
                val fx = ((k * .173f + p / 12f) % 1f) * size.width
                val fy = size.height * (.25f + .3f * ((k * .41f) % 1f)) + sin(p * 2 + k) * 8f
                val tw = .5f + .5f * sin(p * 3 + k * 2)
                drawStar(Offset(fx, fy), 3f + 3f * tw, Color(0xFFFFE39A).copy(alpha = .3f + .5f * tw))
            }
        }
        trees.getOrNull(picked)?.let { t ->
            val label = when {
                t.today -> stringResource(R.string.wood_today)
                t.dry -> stringResource(R.string.wood_dry, fmt.format(t.date))
                t.gainMs < 5 * 60_000L -> stringResource(R.string.wood_small, fmt.format(t.date))
                else -> stringResource(R.string.wood_tree, fmt.format(t.date), Wins.short(t.gainMs), names[t.kind] ?: "")
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 18.dp))
        }
        nextKind?.let {
            Text(stringResource(R.string.wood_next, names[it] ?: "", it.unlockDay), style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFDF8A), modifier = Modifier.padding(horizontal = 18.dp))
        }
    }
}

private const val MAX_TREES = 24

/** Where each tree stands: rows from the front (0) back, newest at the front. */
private class Spot(val fx: Float, val row: Int) {
    fun pos(w: Float, h: Float): Pair<Float, Float> = Pair(fx * w, hillY(fx, row, h))
}

private fun hillY(fx: Float, row: Int, h: Float): Float =
    h * (.90f - row * .17f) - sin(fx * PI.toFloat() * 1.2f + row * 1.7f) * h * .045f

private fun placeTrees(n: Int): List<Spot> {
    val perRow = 8
    return List(n) { i ->
        val fromNewest = n - 1 - i
        val row = (fromNewest / perRow).coerceAtMost(2)
        val col = fromNewest % perRow
        val jitter = (((i * 37) % 11) / 11f - .5f) * .05f
        val offset = if (row % 2 == 1) .5f / perRow else 0f
        Spot(((perRow - 1 - col) + .5f) / perRow + offset * .6f + jitter - (if (row % 2 == 1) .03f else 0f), row)
    }
}

private fun easeBack(t: Float): Float {
    val c = 1.9f; val x = t - 1f
    return 1f + (c + 1f) * x * x * x + c * x * x
}

private fun DrawScope.drawHill(phase: Float) {
    val w = size.width; val h = size.height
    // Sky with the glasshouse lamp glowing behind the hill.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2B1D10), Color(0xFF15100A))))
    val glow = Offset(w * .5f, h * .38f)
    drawCircle(Brush.radialGradient(listOf(GlassGoldW.copy(alpha = .22f + .04f * sin(phase)), Color.Transparent), center = glow, radius = w * .45f), w * .45f, glow)
    val colors = listOf(Color(0xFF26301A), Color(0xFF30401F), Color(0xFF3D5226))
    for (row in 2 downTo 0) {
        val path = Path().apply {
            moveTo(0f, h)
            var x = 0f
            moveTo(0f, hillY(0f, row, h) + h * .03f)
            while (x <= 1.0001f) { lineTo(x * w, hillY(x, row, h) + h * .03f); x += .02f }
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(path, colors[2 - row])
        val rim = Path().apply {
            var x = 0f; moveTo(0f, hillY(0f, row, h) + h * .03f)
            while (x <= 1.0001f) { lineTo(x * w, hillY(x, row, h) + h * .03f); x += .02f }
        }
        drawPath(rim, Color(0xFF9CC46A).copy(alpha = .25f - row * .06f), style = Stroke(2f))
    }
}

/** One tree in the paintings' style: dark outline round the whole crown, flat colour, a light edge. */
private fun DrawScope.drawTree(t: WoodTree, root: Offset, h: Float, sway: Float, phase: Float, haze: Float) {
    val ink = (h * .025f).coerceIn(1f, 2.4f)
    // A soft shadow on the grass.
    drawOval(Color.Black.copy(alpha = .25f), topLeft = Offset(root.x - h * .28f, root.y - h * .03f), size = androidx.compose.ui.geometry.Size(h * .56f, h * .07f))
    rotate(sway, pivot = root) {
        when {
            t.today -> drawSapling(root, h, phase, ink)
            t.dry -> drawDry(root, h, ink)
            t.gainMs < 5 * 60_000L -> drawBush(root, h, ink, haze)
            else -> when (t.kind) {
                TreeKind.PINE -> drawPine(root, h, ink, haze, snow = winter(t.date))
                TreeKind.WILLOW -> drawWillow(root, h, ink, haze, phase)
                else -> drawRound(root, h, ink, haze, t.kind ?: TreeKind.OAK)
            }
        }
    }
}

private fun Color.hazed(haze: Float): Color = androidx.compose.ui.graphics.lerp(this, Color(0xFF2A2418), haze)

private fun DrawScope.trunk(root: Offset, h: Float, top: Float, width: Float, ink: Float, haze: Float, bend: Float = 0f) {
    val path = Path().apply {
        moveTo(root.x - width, root.y)
        quadraticTo(root.x - width * .5f + bend, root.y - h * top * .5f, root.x - width * .45f + bend, root.y - h * top)
        lineTo(root.x + width * .45f + bend, root.y - h * top)
        quadraticTo(root.x + width * .5f + bend, root.y - h * top * .5f, root.x + width, root.y)
        close()
    }
    drawPath(path, GlassInk, style = Stroke(ink * 2f, join = StrokeJoin.Round))
    drawPath(path, Brush.horizontalGradient(listOf(BarkLight.hazed(haze), Bark.hazed(haze)), startX = root.x - width, endX = root.x + width))
}

/** Oak, cherry, lemon and olive: a crown of overlapping balls under one ink outline. */
private fun DrawScope.drawRound(root: Offset, h: Float, ink: Float, haze: Float, kind: TreeKind) {
    val (light, dark) = when (kind) {
        TreeKind.CHERRY -> Color(0xFFFFC2D6) to Color(0xFFD9709A)
        TreeKind.OLIVE -> Color(0xFFC9D6A3) to Color(0xFF7E9563)
        TreeKind.LEMON -> Color(0xFF9FD45E) to Color(0xFF3F8A36)
        else -> Color(0xFF9CD45A) to Color(0xFF3F7F33)
    }
    val olive = kind == TreeKind.OLIVE
    trunk(root, h, .5f, h * (if (olive) .07f else .055f), ink, haze, bend = if (olive) h * .05f else 0f)
    val c = Offset(root.x, root.y - h * .64f)
    val r = h * .33f
    val balls = listOf(
        Offset(0f, 0f) to 1f, Offset(-.62f, .2f) to .7f, Offset(.62f, .18f) to .72f,
        Offset(-.35f, -.5f) to .7f, Offset(.38f, -.48f) to .66f, Offset(0f, .42f) to .6f
    ).map { (o, s) -> Offset(c.x + o.x * r, c.y + o.y * r) to s * r * (if (olive) .9f else 1f) }
    balls.forEach { (p, br) -> drawCircle(GlassInk, br + ink, p) }
    balls.forEach { (p, br) -> drawCircle(Brush.radialGradient(listOf(light.hazed(haze), dark.hazed(haze)), center = p - Offset(br * .35f, br * .45f), radius = br * 1.5f), br, p) }
    // Light edges on the upper left of each ball.
    balls.take(5).forEach { (p, br) -> drawCircle(Color.White.copy(alpha = .14f * (1 - haze)), br * .35f, p - Offset(br * .35f, br * .4f)) }
    when (kind) {
        TreeKind.CHERRY -> for (k in 0 until 9) {
            val a = k * 1.9f; val d = r * (.25f + (k % 3) * .25f)
            drawCircle(Color.White.copy(alpha = .85f * (1 - haze)), r * .07f, c + Offset(sin(a) * d, kotlin.math.cos(a) * d * .8f))
        }
        TreeKind.LEMON -> for (k in 0 until 7) {
            val a = k * 2.3f; val d = r * (.3f + (k % 3) * .22f)
            val p = c + Offset(sin(a) * d, kotlin.math.cos(a) * d * .8f)
            drawCircle(GlassInk, r * .11f + ink * .6f, p)
            drawCircle(Color(0xFFFFE04A).hazed(haze), r * .11f, p)
        }
        TreeKind.OLIVE -> for (k in 0 until 6) {
            val a = k * 2.1f; val d = r * (.3f + (k % 2) * .3f)
            drawCircle(Color(0xFF3A3550).hazed(haze), r * .06f, c + Offset(sin(a) * d, kotlin.math.cos(a) * d * .8f))
        }
        else -> Unit
    }
}

private fun DrawScope.drawPine(root: Offset, h: Float, ink: Float, haze: Float, snow: Boolean) {
    trunk(root, h, .22f, h * .05f, ink, haze)
    val tiers = listOf(Triple(.16f, .36f, .5f), Triple(.36f, .3f, .4f), Triple(.55f, .24f, .32f))
    tiers.forEach { (base, half, tall) ->
        val y0 = root.y - h * base
        val path = Path().apply {
            moveTo(root.x - h * half, y0)
            quadraticTo(root.x, y0 + h * .05f, root.x + h * half, y0)
            lineTo(root.x, y0 - h * tall)
            close()
        }
        drawPath(path, GlassInk, style = Stroke(ink * 2f, join = StrokeJoin.Round))
        drawPath(path, Brush.linearGradient(listOf(Color(0xFF5FAE6A).hazed(haze), Color(0xFF2E6E45).hazed(haze)),
            start = Offset(root.x - h * half, y0 - h * tall), end = Offset(root.x + h * half, y0)))
        if (snow) {
            val cap = Path().apply {
                moveTo(root.x, y0 - h * tall)
                lineTo(root.x - h * half * .45f, y0 - h * tall * .55f)
                quadraticTo(root.x, y0 - h * tall * .45f, root.x + h * half * .45f, y0 - h * tall * .55f)
                close()
            }
            drawPath(cap, Color(0xFFF4F8FF).hazed(haze))
        }
    }
}

private fun DrawScope.drawWillow(root: Offset, h: Float, ink: Float, haze: Float, phase: Float) {
    trunk(root, h, .55f, h * .06f, ink, haze)
    val c = Offset(root.x, root.y - h * .62f)
    val r = h * .3f
    drawCircle(GlassInk, r + ink, c)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFB8DC7A).hazed(haze), Color(0xFF5E9A44).hazed(haze)), center = c - Offset(r * .3f, r * .4f), radius = r * 1.4f), r, c)
    for (k in -5..5) {
        val sx = c.x + k * r * .17f
        val sy = c.y + r * (.1f - abs(k) * .03f)
        val drift = sin(phase * 1.3f + k) * h * .02f
        val strand = Path().apply { moveTo(sx, sy); quadraticTo(sx + drift, sy + h * .25f, sx + drift * 1.6f, sy + h * .42f) }
        drawPath(strand, GlassInk, style = Stroke(ink * 2.6f, cap = StrokeCap.Round))
        drawPath(strand, Color(0xFF8CC35E).hazed(haze), style = Stroke(ink * 1.3f, cap = StrokeCap.Round))
    }
}

/** A heavy day: a low bush, still green. */
private fun DrawScope.drawBush(root: Offset, h: Float, ink: Float, haze: Float) {
    val r = h * .2f
    val balls = listOf(Offset(-.6f, -.55f) to .75f, Offset(.6f, -.55f) to .75f, Offset(0f, -.9f) to .95f)
        .map { (o, s) -> Offset(root.x + o.x * r, root.y + o.y * r) to s * r }
    balls.forEach { (p, br) -> drawCircle(GlassInk, br + ink, p) }
    balls.forEach { (p, br) -> drawCircle(Brush.radialGradient(listOf(Color(0xFF8CC35E).hazed(haze), Color(0xFF3F7F33).hazed(haze)), center = p - Offset(br * .3f, br * .4f), radius = br * 1.5f), br, p) }
}

/** The day the glass broke: a bare dry tree. It stays, but it never spreads. */
private fun DrawScope.drawDry(root: Offset, h: Float, ink: Float) {
    val dry = Color(0xFF8A6A48)
    fun branch(from: Offset, to: Offset, wd: Float) {
        drawLine(GlassInk, from, to, wd + ink * 2f, StrokeCap.Round)
        drawLine(dry, from, to, wd, StrokeCap.Round)
    }
    val top = Offset(root.x + h * .02f, root.y - h * .62f)
    branch(root, top, h * .06f)
    branch(Offset(root.x, root.y - h * .35f), Offset(root.x - h * .22f, root.y - h * .62f), h * .035f)
    branch(Offset(root.x + h * .01f, root.y - h * .45f), Offset(root.x + h * .24f, root.y - h * .7f), h * .03f)
    branch(top, Offset(top.x - h * .08f, top.y - h * .14f), h * .025f)
}

/** Today's tree, still a sapling, glowing gold until the day is done. */
private fun DrawScope.drawSapling(root: Offset, h: Float, phase: Float, ink: Float) {
    val glow = Offset(root.x, root.y - h * .5f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE39A).copy(alpha = .45f), Color.Transparent), center = glow, radius = h * .9f), h * .9f, glow)
    val tip = Offset(root.x, root.y - h * .75f)
    drawLine(GlassInk, root, tip, h * .07f + ink * 2f, StrokeCap.Round)
    drawLine(Color(0xFF5FA03E), root, tip, h * .07f, StrokeCap.Round)
    val f = sin(phase * 1.8f) * 6f
    drawLeaf(tip, h * .42f, -150f + f, ink)
    drawLeaf(tip, h * .44f, -30f - f, ink)
}
