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
import app.hisn.android.R
import kotlinx.coroutines.delay

const val OPEN_WAIT_CIRCLE = "open_wait_circle"

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
    onOpen: () -> Unit
) {
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
                Box(Modifier.size(220.dp).testTag(OPEN_WAIT_CIRCLE), contentAlignment = Alignment.Center) {
                    // A thin ring that breathes; nothing filled, nothing decorative.
                    Box(
                        Modifier
                            .size(220.dp)
                            .scale(breath.value)
                            .border(2.dp, colors.primary, CircleShape)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (left > 0) left.toString() else "✓",
                            style = MaterialTheme.typography.displayMedium,
                            color = colors.onSurface
                        )
                        Text(
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
                Text(
                    stringResource(R.string.open_wait_today, openedToday, closedToday),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = colors.onSurfaceVariant
                )
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
                    HisnCard {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.open_wait_needs_guard),
                                style = MaterialTheme.typography.bodyMedium, color = colors.error)
                            TextButton(onClick = onFixGuard, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.open_wait_turn_on_guard))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                SectionHeader(stringResource(R.string.open_wait_how_long))
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    app.hisn.core.OpenWait.WAITS.forEach { option ->
                        FilterChip(
                            selected = option == seconds,
                            onClick = { onChange(chosen, option, grows) },
                            label = { Text(stringResource(R.string.open_wait_seconds_short, option)) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
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
                            Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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
