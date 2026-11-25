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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import kotlin.math.abs
import kotlin.math.sin

/*
 * Your wood (Glasshouse round 11, after Forest): every day with Unrot becomes a tree on the hill
 * around the glasshouse. Its size is that day's time given back; a heavy day is a small bush,
 * never a dead tree. Only the day adult content got through (the glass broke) stays a dry tree.
 * Kinds of tree are earned with days, never bought; in winter pines wear snow. Today is the
 * sapling still growing. Painted trees on a painted hill, swaying in the wind.
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

private val GlassGoldW = Color(0xFFF2C14E)

@Composable
internal fun UnrotWoodCard(w: WinsSummary) {
    val context = LocalContext.current
    val trees = remember(w) { woodTrees(w, GlassMoments.brokenDay(context)).takeLast(MAX_TREES) }
    UnrotWoodCard(trees, w.day, w.base.feedMs, w.givenBackMs)
}

/**
 * The wood when Android has given no usage numbers yet: still a tree for every day with Unrot,
 * all one size, so the top of Progress is never empty. The dry tree and today's sapling are real.
 */
@Composable
internal fun UnrotWoodCardByDays(day: Int) {
    val context = LocalContext.current
    val broken = remember { GlassMoments.brokenDay(context) }
    val trees = remember(day, broken) {
        val today = LocalDate.now()
        (day - 1 downTo 1).map { back ->
            val date = today.minusDays(back.toLong())
            WoodTree(date, 30 * 60_000L, treeKindFor(date, day - back), date.toEpochDay() == broken, false)
        }.takeLast(MAX_TREES - 1) + WoodTree(today, 0L, null, today.toEpochDay() == broken, true)
    }
    UnrotWoodCard(trees, day, 60 * 60_000L, 0L)
}

/** A wood of sample days, for looking at the trees on a phone with no history (debug builds only). */
@Composable
internal fun UnrotWoodPreview(onBack: () -> Unit) {
    val today = LocalDate.now()
    val trees = remember {
        (22 downTo 1).map { back ->
            val date = today.minusDays(back.toLong()); val n = 23 - back
            WoodTree(date, ((20 + (n * 37) % 70) * 60_000L).takeIf { n % 9 != 4 } ?: 2 * 60_000L, treeKindFor(date, n * 2 + 2), n == 15, false)
        } + WoodTree(today, 0L, null, false, true)
    }
    DetailScaffold(stringResource(R.string.wood_title), onBack) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) { UnrotWoodCard(trees, 46, 110 * 60_000L, 14 * 3_600_000L) }
    }
}

@Composable
private fun UnrotWoodCard(trees: List<WoodTree>, day: Int, baseFeedMs: Long, givenBackMs: Long) {
    val reduce = LocalReduceMotion.current
    val haptics = rememberHisnHaptics()
    var picked by remember(trees) { mutableIntStateOf(trees.lastIndex) }
    val appear = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) appear.animateTo(1f, tween(1800, easing = LinearEasing)) }
    val wind = rememberInfiniteTransition(label = "wood-wind")
    val phase by wind.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val locale = appLocale()
    val fmt = remember(locale) { DateTimeFormatter.ofPattern("EEE d MMM", locale) }
    val nextKind = TreeKind.entries.firstOrNull { it.unlockDay > day }
    val names = TreeKind.entries.associateWith { stringResource(it.label) }
    val woodDesc = stringResource(R.string.wood_desc, trees.size, Wins.short(givenBackMs))
    val art = rememberTreeArt()

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
            Modifier.fillMaxWidth().aspectRatio(360f / 300f)
                .semantics { contentDescription = woodDesc }
                .pointerInput(trees) {
                    detectTapGestures { tap ->
                        val wpx = size.width.toFloat(); val hpx = size.height.toFloat()
                        val best = layout.indices.minByOrNull { i ->
                            val (x, y) = layout[i].pos(wpx, hpx)
                            abs(x - tap.x) + abs(y - hpx * .05f - tap.y) * .6f
                        }
                        if (best != null && best != picked) { picked = best; haptics.tick() }
                    }
                }
        ) {
            val p = if (reduce) 0f else phase
            drawHill(p)
            // Back rows first, so the front trees stand over them.
            layout.indices.sortedBy { layout[it].fy }.forEach { i ->
                val spot = layout[i]
                val t = trees[i]
                val (x, y) = spot.pos(size.width, size.height)
                val grow = easeBack(((appear.value - i * (.7f / layout.size)) / .3f).coerceIn(0f, 1f))
                if (grow <= 0f) return@forEach
                val full = size.width * .105f * spot.scale
                val sizeFactor = if (t.today) .8f else (.62f + .38f * (t.gainMs / (baseFeedMs * .5f).coerceAtLeast(1f))).coerceIn(.62f, 1f)
                val h = full * sizeFactor * grow
                val sway = sin(p + i * .8f) * 2.2f
                val haze = 0f
                if (i == picked) {
                    drawCircle(Brush.radialGradient(listOf(GlassGoldW.copy(alpha = .35f), Color.Transparent), center = Offset(x, y - h * .5f), radius = h * .9f), h * .9f, Offset(x, y - h * .5f))
                }
                drawTree(t, Offset(x, y), h, sway, haze, art)
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
/**
 * Where each tree stands on the painted hill, as fractions of the picture. [row] 0 is the back
 * row near the crest; later rows are lower and nearer. [scale] grows towards the front.
 */
private class Spot(val fx: Float, val fy: Float, val row: Int, val scale: Float) {
    fun pos(w: Float, h: Float): Pair<Float, Float> = Pair(fx * w, fy * h)
}

/** The crest of the painted hill, in the picture's own 360 × 300 units. */
private fun crest(x: Float): Float = 124f + 15f * ((x - 180f) / 150f) * ((x - 180f) / 150f)

/**
 * Oldest days at the back, newest at the front, in one to four rows. Trees stand on the two
 * grass banks only: the painted path down the middle of the hill stays clear.
 */
private fun placeTrees(n: Int): List<Spot> {
    val rows = when { n <= 6 -> 1; n <= 18 -> 2; n <= 32 -> 3; else -> 4 }
    val per = kotlin.math.ceil(n / rows.toFloat()).toInt().coerceAtLeast(1)
    return List(n) { i ->
        val r = i / per; val c = i % per
        val inRow = minOf(per, n - r * per)
        val u = (c + .5f + (if (r % 2 == 1) .22f else -.08f)) / inRow
        val x = if (u < .5f) 38f + u * 2f * 114f else 232f + (u - .5f) * 2f * 92f
        val y = crest(x) + (if (rows == 1) 44f else 20f) + r * 33f
        Spot(x / 360f, y / 300f, r, (.74f + r * .13f) * (if (n <= 8) 1.7f else 1f))
    }
}

private fun easeBack(t: Float): Float {
    val c = 1.9f; val x = t - 1f
    return 1f + (c + 1f) * x * x * x + c * x * x
}


/** One tree in the paintings' style: dark outline round the whole crown, flat colour, a light edge. */
/**
 * The hill, drawn (not a generated picture), in the picture's own 360 × 300 units like the
 * prototype's: a glow behind the crest, a far hill, the grass slope, the sandy path down the
 * middle (trees keep off it, see [placeTrees]), grass tufts, flowers and stars that twinkle.
 */
private fun DrawScope.drawHill(phase: Float) {
    val k = size.width / 360f
    fun o(x: Float, y: Float) = Offset(x * k, y * k)
    // glow behind the crest
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFD98A).copy(alpha = .5f), Color(0xFFF0B93C).copy(alpha = .16f), Color.Transparent), center = o(180f, 124f), radius = 150f * k),
        150f * k, o(180f, 124f)
    )
    // stars
    val stars = floatArrayOf(22f, 30f, 58f, 62f, 96f, 22f, 128f, 54f, 226f, 34f, 262f, 66f, 300f, 24f, 338f, 58f, 40f, 96f, 318f, 100f, 166f, 18f, 204f, 70f)
    for (i in 0 until stars.size / 2) {
        val tw = .25f + .7f * (.5f + .5f * sin(phase * 2f + i * 1.7f))
        drawCircle(Color(0xFFFFE9B0).copy(alpha = tw), (if (i % 3 == 0) 1.4f else .9f) * k, o(stars[i * 2], stars[i * 2 + 1]))
    }
    // far hill
    drawPath(Path().apply {
        moveTo(-20f * k, 178f * k); quadraticTo(70f * k, 128f * k, 150f * k, 150f * k); quadraticTo(230f * k, 172f * k, 380f * k, 160f * k)
        lineTo(380f * k, 300f * k); lineTo(-20f * k, 300f * k); close()
    }, Color(0xFF1D3F27).copy(alpha = .7f))
    // the slope
    drawPath(Path().apply {
        moveTo(-10f * k, 150f * k); quadraticTo(180f * k, 98f * k, 370f * k, 150f * k)
        lineTo(370f * k, 300f * k); lineTo(-10f * k, 300f * k); close()
    }, Brush.verticalGradient(0f to Color(0xFF6AA544), .35f to Color(0xFF3F7D38), 1f to Color(0xFF173A22), startY = 120f * k, endY = 300f * k))
    // light along the crest
    drawPath(Path().apply {
        moveTo(30f * k, 140f * k); quadraticTo(180f * k, 104f * k, 330f * k, 140f * k); quadraticTo(180f * k, 120f * k, 30f * k, 140f * k); close()
    }, Color(0xFFFFF0B0).copy(alpha = .28f))
    // the path
    drawPath(Path().apply {
        moveTo(184f * k, 126f * k)
        cubicTo(172f * k, 152f * k, 206f * k, 172f * k, 186f * k, 204f * k)
        cubicTo(170f * k, 230f * k, 136f * k, 250f * k, 140f * k, 300f * k)
        lineTo(190f * k, 300f * k)
        cubicTo(186f * k, 258f * k, 212f * k, 238f * k, 224f * k, 208f * k)
        cubicTo(238f * k, 172f * k, 198f * k, 152f * k, 190f * k, 126f * k)
        close()
    }, Brush.verticalGradient(listOf(Color(0xFFE2C48A), Color(0xFF8A6A3F)), startY = 126f * k, endY = 300f * k), alpha = .9f)
    // grass tufts and flowers
    val tufts = floatArrayOf(36f, 196f, 70f, 232f, 104f, 178f, 128f, 248f, 58f, 270f, 246f, 186f, 280f, 226f, 312f, 196f, 330f, 250f, 262f, 268f, 96f, 214f, 296f, 160f, 150f, 284f, 232f, 244f)
    for (i in 0 until tufts.size / 2) {
        val x = tufts[i * 2]; val y = tufts[i * 2 + 1]
        for ((dx, dy) in listOf(-3f to -7f, 0f to -9f, 3f to -7f)) {
            drawLine(Color(0xFF9FD070).copy(alpha = .8f), o(x, y), o(x + dx, y + dy), 1.3f * k, StrokeCap.Round)
        }
    }
    val dots = floatArrayOf(48f, 214f, 118f, 262f, 86f, 190f, 270f, 204f, 322f, 228f, 244f, 262f, 300f, 276f, 140f, 222f)
    for (i in 0 until dots.size / 2) drawCircle(if (i % 2 == 0) Color(0xFFFFF6D8) else Color(0xFFFFD76A), 1.6f * k, o(dots[i * 2], dots[i * 2 + 1]))
    // the slope sinks into the card
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF15100A)), startY = size.height * .8f, endY = size.height), topLeft = Offset(0f, size.height * .8f))
}

/**
 * The painted trees (one family for Android, iPhone and Mac), each on its own patch of grass.
 * They replaced the trees that were drawn in code.
 */
internal class TreeArt(
    val oak: ImageBitmap, val pine: ImageBitmap, val cherry: ImageBitmap, val lemon: ImageBitmap,
    val olive: ImageBitmap, val willow: ImageBitmap, val winter: ImageBitmap, val sapling: ImageBitmap,
    val dry: ImageBitmap
) {
    fun of(t: WoodTree): ImageBitmap = when {
        t.today -> sapling
        t.dry -> dry
        t.gainMs < 5 * 60_000L -> sapling
        else -> when (t.kind) {
            TreeKind.PINE -> if (winter(t.date)) winter else pine
            TreeKind.CHERRY -> cherry
            TreeKind.LEMON -> lemon
            TreeKind.OLIVE -> olive
            TreeKind.WILLOW -> willow
            else -> oak
        }
    }
}

@Composable
internal fun rememberTreeArt(): TreeArt {
    @Composable fun img(id: Int) = ImageBitmap.imageResource(id)
    return TreeArt(
        img(R.drawable.unrot_tree_oak), img(R.drawable.unrot_tree_pine), img(R.drawable.unrot_tree_cherry),
        img(R.drawable.unrot_tree_lemon), img(R.drawable.unrot_tree_olive), img(R.drawable.unrot_tree_willow),
        img(R.drawable.unrot_tree_winter), img(R.drawable.unrot_tree_sapling), img(R.drawable.unrot_tree_dry)
    )
}

private fun DrawScope.drawTree(t: WoodTree, root: Offset, h: Float, sway: Float, haze: Float, art: TreeArt) {
    // The painting is square with the tree standing on a patch of grass near its foot.
    val side = (h * 1.3f).toInt().coerceAtLeast(1)
    rotate(sway * .6f, pivot = root) {
        drawImage(
            art.of(t),
            dstOffset = androidx.compose.ui.unit.IntOffset((root.x - side / 2f).toInt(), (root.y - side * .9f).toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(side, side),
            // Trees further up the hill sit back in the evening haze.
            alpha = 1f - haze * .35f,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium
        )
    }
}








