package app.hisn.android.nativeshell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.hisn.android.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import app.hisn.core.PersonalProfile
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.remember
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Progress's hero: the feed time a day lately, how much less than "before", and a gold curve of
 * the recent days under a dashed line for "before". Less time draws lower, so getting better reads
 * as the curve falling away from the line. Shown only when the week is lighter than before; a
 * heavier week gets its own card instead.
 */
@Composable
internal fun UnrotProgressHero(w: WinsSummary) {
    val days = w.daily.takeLast(14)
    if (days.size < 2 || w.base.feedMs <= 0) return
    val gold = if (LocalDarkTheme.current) Color(0xFFF2C94F) else Color(0xFFB8861F)
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val less = (-w.percent).coerceAtLeast(0)
    val shape = MaterialTheme.shapes.extraLarge
    val describe = stringResource(R.string.progress_hero_chart, Usage.formatDuration(w.base.feedMs), Usage.formatDuration(w.nowFeedMs))
    Column(
        Modifier.fillMaxWidth().unrotFrame(shape, hero = true).background(LocalHisnStatusColors.current.card, shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(stringResource(R.string.progress_hero_label).uppercase(appLocale()), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(Wins.short(w.nowFeedMs), style = MaterialTheme.typography.displaySmall, color = gold,
                modifier = Modifier.semantics { heading() })
            if (less > 0) Text(stringResource(R.string.progress_hero_less, less), style = MaterialTheme.typography.labelLarge,
                color = LocalHisnStatusColors.current.success,
                modifier = Modifier.padding(bottom = 8.dp)
                    .background(LocalHisnStatusColors.current.successContainer, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp))
        }
        val top = (maxOf(days.max(), w.base.feedMs) * 1.15f).coerceAtLeast(1f)
        val grow = grow(1f, 150)
        Canvas(Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp).semantics { contentDescription = describe }) {
            fun y(v: Long) = size.height * (1f - v / top)
            val step = size.width / (days.size - 1)
            val by = y(w.base.feedMs)
            drawLine(line, Offset(0f, by), Offset(size.width, by), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            val shown = (days.size * grow).roundToInt().coerceIn(2, days.size)
            val curve = Path(); val fill = Path()
            days.take(shown).forEachIndexed { i, v ->
                val p = Offset(i * step, y(v))
                if (i == 0) { curve.moveTo(p.x, p.y); fill.moveTo(p.x, size.height); fill.lineTo(p.x, p.y) }
                else { curve.lineTo(p.x, p.y); fill.lineTo(p.x, p.y) }
            }
            fill.lineTo((shown - 1) * step, size.height); fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(gold.copy(alpha = 0.45f), Color.Transparent)))
            drawPath(curve, gold, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(gold, 4.5.dp.toPx(), Offset((shown - 1) * step, y(days[shown - 1])))
        }
        Text(stringResource(R.string.progress_hero_foot, Usage.formatDuration(w.base.feedMs), Usage.formatDuration(w.givenBackMs)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val MEDAL_DAYS = intArrayOf(7, 14, 30, 60, 90, 180, 365)

/**
 * The medal wall: every milestone the day count can reach, earned ones in gold and the rest dimmed,
 * with the next one marked so there is always something close to aim for. It shows only the days,
 * nothing about which apps or what is blocked.
 */
@Composable
internal fun UnrotMedalWall(day: Int) {
    if (day <= 0) return
    val shape = MaterialTheme.shapes.extraLarge
    val next = MEDAL_DAYS.firstOrNull { it > day } ?: 0
    val earned = MEDAL_DAYS.count { day >= it }
    val gold = if (LocalDarkTheme.current) Color(0xFFF2C94F) else Color(0xFFB8861F)
    Column(
        Modifier.fillMaxWidth().unrotFrame(shape).background(LocalHisnStatusColors.current.card, shape)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val locale = appLocale()
        val since = remember(day, locale) {
            LocalDate.now().minusDays((day - 1).toLong()).format(DateTimeFormatter.ofPattern("d MMM", locale))
        }
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(stringResource(R.string.progress_medals_title, earned, MEDAL_DAYS.size), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.pm_label, day, since), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth()) {
            MEDAL_DAYS.forEach { m ->
                val won = day >= m
                val label = stringResource(R.string.progress_medal_day, m)
                val said = stringResource(if (won) R.string.progress_medal_won else if (m == next) R.string.progress_medal_next else R.string.progress_medal_later, label)
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = said },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (won) Float3D(R.drawable.unrot_3d_medal, 40.dp, glow = m == MEDAL_DAYS.last { day >= it })
                    else Image(painterResource(R.drawable.unrot_3d_medal), null, Modifier.size(40.dp).alpha(if (m == next) 0.55f else 0.28f),
                        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }))
                    Text(m.toString(), style = MaterialTheme.typography.labelLarge,
                        color = if (won) gold else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (next > 0) {
            LinearProgressIndicator(progress = { PersonalProfile.milestoneProgress(day) }, color = gold,
                trackColor = gold.copy(alpha = 0.16f), drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(6.dp))
            Text(LocalResources.current.getQuantityString(R.plurals.progress_medal_left, next - day, next - day, next),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp))
        }
        Text(stringResource(R.string.today_day_never_resets), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/**
 * A quiet stand-in while a card's numbers load: soft rounded blocks the shape of what is coming,
 * breathing slowly unless the person turned motion down. Screen readers hear "Loading".
 */
@Composable
internal fun LoadingBlocks(vararg heights: Int, chart: Boolean = false) {
    val reduce = LocalReduceMotion.current
    val pulse = if (reduce) 0.5f else {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "loading")
        t.animateFloat(0.35f, 0.75f, androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "pulse").value
    }
    val fill = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f * pulse / 0.5f)
    val loading = stringResource(R.string.loading_label)
    Column(Modifier.fillMaxWidth().semantics { contentDescription = loading }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        heights.forEachIndexed { i, h ->
            Box(Modifier.fillMaxWidth(if (i == 0) 0.45f else if (i % 2 == 0) 0.7f else 1f).height(h.dp)
                .background(fill, RoundedCornerShape(10.dp)))
        }
        if (chart) Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom) {
            listOf(0.6f, 0.85f, 0.5f, 0.7f, 0.4f, 0.65f, 0.3f).forEach { f ->
                Box(Modifier.weight(1f).fillMaxHeight(f).background(fill, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp)))
            }
        }
    }
}
