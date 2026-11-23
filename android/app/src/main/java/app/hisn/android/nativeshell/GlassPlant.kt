package app.hisn.android.nativeshell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The living parts of the Glasshouse, drawn in code in the painted cartoon's style (flat colour,
 * a dark ink outline, one light edge) so they sit inside the Higgsfield paintings:
 *  - GlassPlant: the plant for the in-between stages (Sprout → Flower), grown on top of the empty
 *    seed painting. Its size follows time given back continuously, so it grows a little every
 *    day, sways, and bursts into sparkles when a new stage arrives.
 *  - GlassAir: the air under the lamp: the beam breathes, gold motes drift up, and a glint runs
 *    over the glass now and then.
 * Both stand still with "Remove animations" on.
 */

internal val GlassInk = Color(0xFF24160A)
private val LeafLight = Color(0xFFA6DD62)
internal val LeafDark = Color(0xFF4A9238)
private val LeafVein = Color(0xFFD4F2A2)
private val StemGreen = Color(0xFF5FA03E)
private val PetalLight = Color(0xFFFFF3CC)
private val PetalGold = Color(0xFFF2C14E)
private val Heart = Color(0xFFE0892B)
private val BudLight = Color(0xFFFFC07A)
private val BudDark = Color(0xFFE07A3E)
private val Dew = Color(0xFFBFE6FF)

/** Where the soil is in the seed painting (fractions of the picture), measured from the art. */
private const val BaseX = 0.528f
private const val BaseY = 0.700f

/** Growth as one number: 1 = just sprouted, 6 = the tree. The part between stages comes from time. */
internal fun glassGrowth(givenBackMs: Long): Float {
    val stage = glassStage(givenBackMs)
    if (stage >= 6) return 6f
    val from = glassStageAt[stage]; val to = glassStageAt[stage + 1]
    return stage + ((givenBackMs - from).toFloat() / (to - from)).coerceIn(0f, 1f)
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

/** A table of values at growth 1…6, read with straight lines between them. */
private fun at(g: Float, vararg v: Float): Float {
    val x = (g - 1f).coerceIn(0f, (v.size - 1).toFloat())
    val i = x.toInt().coerceAtMost(v.size - 2)
    return lerp(v[i], v[i + 1], x - i)
}

@Composable
internal fun GlassPlant(givenBackMs: Long, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val target = glassGrowth(givenBackMs).coerceIn(1f, 5.99f)
    // Grows from where it was to where it is, with a little overshoot, like a plant stretching.
    val grown = remember { Animatable(if (reduce) target else (target - .35f).coerceAtLeast(.6f)) }
    val stage = target.toInt()
    var seenStage by rememberSaveable { mutableIntStateOf(stage) }
    val burst = remember { Animatable(1f) }
    LaunchedEffect(target, reduce) {
        if (reduce) grown.snapTo(target)
        else grown.animateTo(target, spring(dampingRatio = .55f, stiffness = Spring.StiffnessVeryLow))
    }
    LaunchedEffect(stage, reduce) {
        if (stage > seenStage && !reduce) { burst.snapTo(0f); burst.animateTo(1f, tween(1400)) }
        seenStage = stage
    }
    val wind = rememberInfiniteTransition(label = "plant-wind")
    val phase by wind.animateFloat(0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(6200, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Canvas(modifier) {
        val p = if (reduce) 0f else phase
        val side = size.height
        val left = (size.width - side * 682f / 688f) / 2f
        val base = Offset(left + side * 682f / 688f * BaseX, side * BaseY)
        drawPlant(base, side, grown.value, p, burst.value)
    }
}

private fun DrawScope.drawPlant(base: Offset, side: Float, g: Float, phase: Float, burst: Float) {
    val u = side / 100f // one unit = 1% of the picture
    val ink = (u * .62f).coerceAtLeast(1.2f)
    // Squash and stretch for a moment when a new stage arrives.
    val pop = if (burst < 1f) sin(burst * PI.toFloat()) * (1f - burst) else 0f
    val stemH = at(g, 7f, 12f, 19f, 25f, 29.5f, 31f) * u * (1f + pop * .12f)
    val sway = sin(phase) * at(g, 1.6f, 2.0f, 2.4f, 2.6f, 2.8f, 3f)
    rotate(sway, pivot = base) {
        val tip = Offset(base.x + sin(phase * 2f) * u * .5f, base.y - stemH)
        val ctrl = Offset(base.x + u * 2.2f, base.y - stemH * .55f)
        fun onStem(t: Float): Offset {
            val a = 1 - t
            return Offset(a * a * base.x + 2 * a * t * ctrl.x + t * t * tip.x, a * a * base.y + 2 * a * t * ctrl.y + t * t * tip.y)
        }
        val stem = Path().apply { moveTo(base.x, base.y); quadraticTo(ctrl.x, ctrl.y, tip.x, tip.y) }
        val stemW = at(g, 1.3f, 1.6f, 2.0f, 2.3f, 2.5f, 2.6f) * u
        drawPath(stem, GlassInk, style = Stroke(stemW + ink * 2, cap = StrokeCap.Round))
        drawPath(stem, StemGreen, style = Stroke(stemW, cap = StrokeCap.Round))
        drawPath(stem, LeafLight.copy(alpha = .55f), style = Stroke(stemW * .3f, cap = StrokeCap.Round))

        // Leaf pairs down the stem: each one appears at its own growth and opens out.
        class Node(val t: Float, val from: Float, val size: Float, val tilt: Float)
        val nodes = listOf(
            Node(.42f, 1.9f, 5.2f, 34f),
            Node(.66f, 2.6f, 5.8f, 28f),
            Node(.25f, 3.3f, 4.6f, 40f),
            Node(.82f, 4.1f, 5.0f, 24f)
        )
        nodes.forEachIndexed { i, n ->
            val open = ((g - n.from) / .8f).coerceIn(0f, 1f)
            if (open <= 0f) return@forEachIndexed
            val at = onStem(n.t)
            val len = n.size * u * open
            val flutter = sin(phase * 1.7f + i * 1.3f) * 4f
            val lift = if (i % 2 == 0) 0f else 8f
            drawLeaf(at, len, -90f - 90f + n.tilt + lift + flutter, ink)
            drawLeaf(at, len * .94f, -n.tilt - lift - flutter, ink)
        }
        // The top pair: the sprout's first two leaves, which stay biggest until the bud comes.
        val topLen = at(g, 6.2f, 8.4f, 8.8f, 7.6f, 7.0f, 7.0f) * u
        val f2 = sin(phase * 1.9f) * 5f
        val hasBud = g >= 3.8f
        val leafAt = if (hasBud) onStem(.93f) else tip
        drawLeaf(leafAt, topLen, -150f + f2, ink)
        drawLeaf(leafAt, topLen * 1.04f, -32f - f2, ink, dew = g >= 1.4f)

        when {
            g >= 5f -> drawFlower(tip, u * at(g, 0f, 0f, 0f, 0f, 3.6f, 5.6f), ((g - 5f) / .7f).coerceIn(0f, 1f), phase, ink)
            hasBud -> drawBud(tip, u * lerp(1.4f, 3.4f, (g - 3.8f) / 1.2f), ink)
        }
        if (burst < 1f) drawSparkles(tip, u, burst)
    }
}

/** One leaf: two curves, a dark outline, a light vein, a soft highlight. Angle in degrees, 0 = right. */
internal fun DrawScope.drawLeaf(at: Offset, len: Float, angle: Float, ink: Float, dew: Boolean = false) {
    if (len < 1f) return
    val w = len * .46f
    val leaf = Path().apply {
        moveTo(0f, 0f)
        cubicTo(len * .25f, -w * 1.05f, len * .78f, -w * .8f, len, 0f)
        cubicTo(len * .72f, w * .7f, len * .25f, w * .75f, 0f, 0f)
        close()
    }
    translate(at.x, at.y) {
        rotate(angle, pivot = Offset.Zero) {
            drawPath(leaf, GlassInk, style = Stroke(ink * 2f, join = StrokeJoin.Round))
            drawPath(leaf, Brush.linearGradient(listOf(LeafLight, LeafDark), start = Offset(0f, -w), end = Offset(len * .3f, w)))
            val vein = Path().apply { moveTo(len * .06f, 0f); quadraticTo(len * .5f, -w * .12f, len * .86f, 0f) }
            drawPath(vein, LeafVein.copy(alpha = .8f), style = Stroke(ink * .9f, cap = StrokeCap.Round))
            drawOval(Color.White.copy(alpha = .22f), topLeft = Offset(len * .3f, -w * .62f), size = Size(len * .32f, w * .22f))
            if (dew) {
                val c = Offset(len * .62f, -w * .1f); val r = len * .11f
                drawCircle(GlassInk.copy(alpha = .7f), r + ink * .6f, c)
                drawCircle(Brush.radialGradient(listOf(Color.White, Dew, Color(0xFF6FB5E8)), center = c - Offset(r * .3f, r * .3f), radius = r * 1.3f), r, c)
                drawCircle(Color.White, r * .28f, c - Offset(r * .35f, r * .35f))
            }
        }
    }
}

private fun DrawScope.drawBud(tip: Offset, r: Float, ink: Float) {
    val bud = Path().apply {
        moveTo(tip.x, tip.y - r * 1.9f)
        cubicTo(tip.x + r * 1.25f, tip.y - r * 1.1f, tip.x + r * 1.1f, tip.y + r * .2f, tip.x, tip.y + r * .3f)
        cubicTo(tip.x - r * 1.1f, tip.y + r * .2f, tip.x - r * 1.25f, tip.y - r * 1.1f, tip.x, tip.y - r * 1.9f)
        close()
    }
    drawPath(bud, GlassInk, style = Stroke(ink * 2f, join = StrokeJoin.Round))
    drawPath(bud, Brush.verticalGradient(listOf(BudLight, BudDark), startY = tip.y - r * 1.9f, endY = tip.y + r * .3f))
    drawLine(Color.White.copy(alpha = .35f), Offset(tip.x - r * .45f, tip.y - r * 1.2f), Offset(tip.x - r * .5f, tip.y - r * .3f), ink, StrokeCap.Round)
    // Two little sepals holding it.
    for (s in listOf(-1f, 1f)) {
        val sep = Path().apply {
            moveTo(tip.x, tip.y + r * .35f)
            quadraticTo(tip.x + s * r * 1.1f, tip.y, tip.x + s * r * .55f, tip.y - r * .7f)
            quadraticTo(tip.x + s * r * .3f, tip.y - r * .1f, tip.x, tip.y + r * .35f)
        }
        drawPath(sep, GlassInk, style = Stroke(ink * 1.6f, join = StrokeJoin.Round))
        drawPath(sep, LeafDark)
    }
}

/** Six petals that open out as the flower stage goes on, and turn slowly in the air. */
private fun DrawScope.drawFlower(c: Offset, r: Float, open: Float, phase: Float, ink: Float) {
    if (r < 1f) return
    val spread = lerp(.35f, 1f, open)
    val turn = sin(phase) * 4f
    for (i in 0 until 6) {
        val a = turn + i * 60f - 90f
        val len = r * lerp(.8f, 1.15f, open)
        val w = len * .62f * spread
        val petal = Path().apply {
            moveTo(0f, 0f)
            cubicTo(len * .3f, -w, len * .95f, -w * .8f, len, 0f)
            cubicTo(len * .95f, w * .8f, len * .3f, w, 0f, 0f)
            close()
        }
        translate(c.x, c.y) {
            rotate(a, pivot = Offset.Zero) {
                drawPath(petal, GlassInk, style = Stroke(ink * 2f, join = StrokeJoin.Round))
                drawPath(petal, Brush.linearGradient(listOf(PetalGold, PetalLight), start = Offset.Zero, end = Offset(len, 0f)))
            }
        }
    }
    val hr = r * .42f
    drawCircle(GlassInk, hr + ink, c)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC163), Heart), center = c - Offset(hr * .3f, hr * .3f), radius = hr * 1.4f), hr, c)
    for (k in 0 until 5) {
        val a = k * 72f * PI.toFloat() / 180f
        drawCircle(Color(0xFF8A4A14), hr * .12f, c + Offset(cos(a) * hr * .5f, sin(a) * hr * .5f))
    }
}

/** A ring of gold stars that flies out from the tip and fades: a new stage. */
private fun DrawScope.drawSparkles(c: Offset, u: Float, t: Float) {
    val fade = 1f - t
    for (i in 0 until 10) {
        val a = (i * 36f + 18f) * PI.toFloat() / 180f
        val d = u * (3f + t * 16f) * (if (i % 2 == 0) 1f else .75f)
        val p = c + Offset(cos(a) * d, sin(a) * d)
        drawStar(p, u * (1.6f - t * .8f), Color(0xFFFFE39A).copy(alpha = fade))
    }
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE39A).copy(alpha = .55f * fade), Color.Transparent), center = c, radius = u * 14f), u * 14f, c)
}

/** A four-pointed twinkle, like the ones painted in the air of the pictures. */
internal fun DrawScope.drawStar(c: Offset, r: Float, color: Color) {
    if (r <= 0f) return
    val s = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x, c.y, c.x + r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(s, color)
}

/**
 * The air under the lamp, drawn over the painting: the beam's glow breathes (5 s), gold motes
 * rise slowly and twinkle, and every few seconds a glint runs across the glass.
 */
@Composable
internal fun GlassAir(night: Boolean, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val air = rememberInfiniteTransition(label = "glass-air")
    val t by air.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val glow by air.animateFloat(0f, 1f, infiniteRepeatable(tween(5000), RepeatMode.Reverse), label = "glow")
    val glint by air.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), label = "glint")
    val motes = remember { List(16) { i -> floatArrayOf(((i * 0.618f) % 1f), ((i * 0.377f + .13f) % 1f), .6f + (i % 4) * .25f, i * .9f) } }
    Canvas(modifier) {
        val time = if (reduce) .3f else t
        val gl = if (reduce) .5f else glow
        val light = if (night) Color(0xFFB9C6FF) else Color(0xFFFFE39A)
        val side = size.height
        val left = (size.width - side) / 2f
        // The glow round the glass, slowly breathing.
        val c = Offset(size.width / 2f, side * .55f)
        val r = side * (.40f + gl * .04f)
        drawCircle(Brush.radialGradient(listOf(light.copy(alpha = .07f + gl * .07f), Color.Transparent), center = c, radius = r), r, c)
        // Motes rising inside the beam.
        motes.forEach { m ->
            val y = 1f - ((m[1] + time * m[2]) % 1f)
            val spreadAt = .10f + y * .12f
            val x = .5f + (m[0] - .5f) * 2f * spreadAt + sin(time * 6.283f * 2f + m[3]) * .012f
            val tw = .5f + .5f * sin(time * 6.283f * 6f + m[3] * 2f)
            val edge = (minOf(y, 1f - y) * 5f).coerceIn(0f, 1f)
            val p = Offset(left + x * side, y * side * .9f)
            drawStar(p, side * (.006f + .006f * tw), light.copy(alpha = (.35f + .55f * tw) * edge))
        }
        // A glint across the dome: a soft white band, only during the first part of each cycle.
        if (!reduce && glint < .28f) {
            val k = glint / .28f
            val gx = left + side * lerp(.22f, .80f, k)
            val dome = Path().apply {
                val l = left + side * .30f; val rgt = left + side * .76f; val top = side * .19f; val mid = side * .44f; val bot = side * .70f
                moveTo(l, bot); lineTo(l, mid)
                cubicTo(l, top - side * .02f, rgt, top - side * .02f, rgt, mid)
                lineTo(rgt, bot); close()
            }
            val a = sin(k * PI.toFloat()) * .16f
            clipPath(dome) {
                rotate(18f, pivot = Offset(gx, side * .45f)) {
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = a), Color.Transparent), startX = gx - side * .05f, endX = gx + side * .05f),
                        topLeft = Offset(gx - side * .05f, side * .05f), size = Size(side * .1f, side * .8f)
                    )
                }
            }
        }
    }
}
