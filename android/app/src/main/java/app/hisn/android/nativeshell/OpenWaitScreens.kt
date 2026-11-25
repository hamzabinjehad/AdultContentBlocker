package app.hisn.android.nativeshell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposePath
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hisn.android.OpenWaitStore
import app.hisn.android.R
import kotlinx.coroutines.delay

const val OPEN_WAIT_CIRCLE = "open_wait_circle"

// A soft nine-lobed "cookie" and a circle with the same vertex count, so the morph between them is
// smooth. Both are normalised to a unit square and scaled to the box when drawn.
private val BreathMorph by lazy {
    val cookie = androidx.graphics.shapes.RoundedPolygon.star(
        numVerticesPerRadius = 9,
        innerRadius = 0.8f,
        rounding = androidx.graphics.shapes.CornerRounding(radius = 0.35f, smoothing = 0.6f)
    ).normalized()
    val circle = androidx.graphics.shapes.RoundedPolygon.circle(numVertices = 18).normalized()
    androidx.graphics.shapes.Morph(cookie, circle)
}

/** The breathing shape at [progress]: 0 is the cookie (out), 1 the circle (in). */
private class BreathShape(private val progress: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val native = android.graphics.Path()
        BreathMorph.toPath(progress, native)
        native.transform(android.graphics.Matrix().apply { setScale(size.width, size.height) })
        return androidx.compose.ui.graphics.Outline.Generic(native.asComposePath())
    }
}

/**
 * The few seconds before a chosen app opens. The breath circle grows and shrinks with the count;
 * "Close" is always there and is the main button, "Open" unlocks when the count reaches zero.
 * The counts are this phone's own, for today only.
 */
@Composable
fun OpenWaitScreen(
    appLabel: String,
    appPackage: String,
    seconds: Int,
    openedToday: Int,
    closedToday: Int,
    reason: String,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    onOpenFor: ((Int) -> Unit)? = null,
    preview: Boolean = false
) {
    if (app.hisn.android.Edition.islamic) {
        HisnOpenWait(appLabel, seconds, openedToday, closedToday, reason, onClose, onOpen, onOpenFor)
        return
    }
    val colors = MaterialTheme.colorScheme
    var left by rememberSaveable { mutableIntStateOf(seconds) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left--
        }
    }
    val breath = remember { Animatable(0.86f) }
    // Shown outside the shell, so read "Remove animations" directly too. With animations off
    // an endless animation would also spin without pause.
    val context = androidx.compose.ui.platform.LocalContext.current
    val still = LocalReduceMotion.current || remember { systemReduceMotion(context) }
    LaunchedEffect(still) {
        // With reduced motion the circle holds still; the count and the words still change.
        while (!still) {
            breath.animateTo(1f, tween(4000, easing = FastOutSlowInEasing))
            breath.animateTo(0.72f, tween(4000, easing = FastOutSlowInEasing))
        }
    }
    val breathingIn = still || breath.targetValue == 1f

    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(40.dp))
                if (preview) {
                    Text(
                        stringResource(R.string.welcome_pause_preview),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary
                    )
                    Spacer(Modifier.height(16.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(appPackage, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(appLabel, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.open_wait_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(36.dp))
                Box(Modifier.size(240.dp).testTag(OPEN_WAIT_CIRCLE), contentAlignment = Alignment.Center) {
                    // The wait itself, as a ring that empties second by second (Pixel's
                    // round-capped progress). It is information, so it runs with reduced motion too.
                    val waited by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (seconds <= 0) 1f else 1f - left / seconds.toFloat(),
                        animationSpec = tween(if (still) 0 else 1000, easing = androidx.compose.animation.core.LinearEasing),
                        label = "waited"
                    )
                    androidx.compose.material3.CircularProgressIndicator(
                        progress = { waited },
                        modifier = Modifier
                            .size(240.dp)
                            .clearAndSetSemantics { },
                        color = colors.primary,
                        trackColor = colors.surfaceContainerHighest,
                        strokeWidth = 6.dp,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        gapSize = 6.dp
                    )
                    // A soft nine-sided cookie that rounds into a circle on the in-breath and
                    // back on the out-breath, turning a little each time: the breath you follow.
                    val t = ((breath.value - 0.72f) / 0.28f).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .size(196.dp)
                            .graphicsLayer {
                                scaleX = 0.82f + 0.18f * t
                                scaleY = scaleX
                                rotationZ = 40f * t
                            }
                            .background(colors.primaryContainer, BreathShape(t))
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (left > 0) left.toString() else "✓",
                            style = MaterialTheme.typography.displayMedium,
                            color = colors.onSurface
                        )
                        if (left > 0) Text(
                            stringResource(if (breathingIn) R.string.open_wait_in else R.string.open_wait_out),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                if (left == 0) {
                    Text(
                        stringResource(R.string.open_wait_done),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                Spacer(Modifier.height(32.dp))
                if (reason.isNotBlank()) {
                    Text(
                        stringResource(R.string.open_wait_reason, reason),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (!preview) {
                    Text(
                        stringResource(R.string.open_wait_today, openedToday, closedToday),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = colors.onSurfaceVariant
                    )
                }
                // One reminder per wait, about time where the pack has one.
                val reminder = remember { Reminders.pick(context, Reminders.TOPIC_TIME) }
                if (reminder != null) {
                    Spacer(Modifier.height(24.dp))
                    ReminderText(reminder, center = true)
                }
                Spacer(Modifier.height(24.dp))
            }
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) { Text(stringResource(R.string.open_wait_close, appLabel), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpen,
                enabled = left == 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text(
                    if (left > 0) pluralStringResource(R.plurals.open_wait_open_in, left, left)
                    else stringResource(R.string.open_wait_open, appLabel),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            // Once the wait is over, a bounded visit is one tap: the wait comes back when it ends.
            if (onOpenFor != null && left == 0) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.open_wait_just), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(5, 15).forEach { m ->
                        TextButton(onClick = { onOpenFor(m) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.open_wait_for, m))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The Rules screen for "Wait before opening": the apps, the wait, and whether it grows. */
@Composable
fun OpenWaitSettings(
    apps: List<InstalledApp>,
    chosen: Set<String>,
    seconds: Int,
    grows: Boolean,
    guardOn: Boolean,
    showAll: Boolean = false,
    onShowAll: (Boolean) -> Unit = {},
    onChange: (Set<String>, Int, Boolean) -> Unit,
    onFixGuard: () -> Unit,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    DetailScaffold(title = stringResource(R.string.open_wait_rule), onBack = onBack) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.open_wait_rule_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                if (!guardOn && chosen.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    AttentionCard(
                        message = stringResource(R.string.open_wait_needs_guard),
                        action = stringResource(R.string.open_wait_turn_on_guard),
                        onAction = onFixGuard
                    )
                }
                Spacer(Modifier.height(16.dp))
                SectionHeader(stringResource(R.string.open_wait_how_long))
                Spacer(Modifier.height(8.dp))
                ConnectedChoice(
                    options = app.hisn.core.OpenWait.WAITS.toList(),
                    selected = seconds,
                    label = { stringResource(R.string.open_wait_seconds_short, it) },
                    onSelect = { onChange(chosen, it, grows) }
                )
                Spacer(Modifier.height(8.dp))
                HisnCard {
                    SwitchRow(
                        icon = Icons.Rounded.TrendingUp,
                        title = stringResource(R.string.open_wait_grows),
                        supporting = stringResource(R.string.open_wait_grows_body),
                        checked = grows,
                        onCheckedChange = { onChange(chosen, seconds, it) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                ShowAllAppsRow(showAll, onShowAll)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.open_wait_apps),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f).semantics { heading() }
                    )
                    Text(stringResource(R.string.welcome_apps_count, chosen.size),
                        style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                val on = app.packageName in chosen
                val shape = when {
                    apps.size == 1 -> MaterialTheme.shapes.large
                    index == 0 -> MaterialTheme.shapes.large.copy(
                        bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                        bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp))
                    index == apps.lastIndex -> MaterialTheme.shapes.large.copy(
                        topStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                        topEnd = androidx.compose.foundation.shape.CornerSize(0.dp))
                    else -> androidx.compose.ui.graphics.RectangleShape
                }
                Surface(color = LocalHisnStatusColors.current.card, shape = shape) {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .toggleable(value = on, role = Role.Checkbox, onValueChange = {
                                    onChange(if (it) chosen + app.packageName else chosen - app.packageName, seconds, grows)
                                })
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(app.packageName, 32.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                // Today's honest counts for the apps that wait: the walk-aways are
                                // the number that matters (one sec, PNAS 2023).
                                if (on) {
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    val opened = remember(app.packageName) {
                                        OpenWaitStore.openedToday(context, app.packageName)
                                    }
                                    val closed = remember(app.packageName) {
                                        OpenWaitStore.closedToday(context, app.packageName)
                                    }
                                    Text(
                                        if (opened == 0 && closed == 0) stringResource(R.string.open_wait_today_none)
                                        // Arabic spells the counts out: a lone 0 beside "·" reads like a dot.
                                        else if (isArabicUi()) "اليوم: " +
                                            (if (opened == 0) "لم يُفتح" else "فُتح ${timesAr(opened)}") + "، " +
                                            (if (closed == 0) "ولم يُغلق بدلًا من ذلك" else "وأُغلق بدلًا من ذلك ${timesAr(closed)}")
                                        else stringResource(R.string.open_wait_today_counts, opened, closed),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                            Checkbox(checked = on, onCheckedChange = null)
                        }
                        if (index < apps.lastIndex) InsetDivider()
                    }
                }
            }
        }
    }
}

/** Icon for the Rules tile. */
val OpenWaitIcon = Icons.Rounded.HourglassTop
