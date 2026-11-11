package app.hisn.android.nativeshell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import android.text.format.DateFormat
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Phase 2 surfaces. Every value comes from NativeShellHost; no number or protection claim is
// fabricated here. Editing stays in the classic screen until the native editors land.

@Composable
fun LiveBanner(labels: ShellLabels) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(labels.live, style = MaterialTheme.typography.titleSmall)
                Text(
                    labels.liveBody,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ProtectionShellScreen(
    state: ShellProtection,
    onCheck: () -> Unit,
    onOpenClassic: () -> Unit,
    onStartDns: () -> Unit
) {
    ShellScreen {
        item { LiveHero(state, onCheck, onOpenClassic, onStartDns) }
        if (state.issues.isNotEmpty()) {
            item { SectionLabel(state.labels.attention) }
            item { IssueCard(state.issues, state.labels.openClassic, onOpenClassic) }
        }
        item { SectionLabel(state.labels.layers) }
        item { LayerCard(state.layers, onOpenClassic) }
    }
}

@Composable
fun RulesShellScreen(
    state: ShellRules,
    onOpenClassic: () -> Unit,
    onLayer: (String, Boolean) -> Unit,
    onEditRules: (String) -> Unit,
    onEditApps: (String) -> Unit,
    onEditLock: () -> Unit,
    onEditLimits: () -> Unit,
    onEditSchedule: () -> Unit,
    onExportRules: () -> Unit,
    onImportRules: () -> Unit
) {
    ShellScreen {
        if (state.locked) {
            item {
                val status = LocalHisnStatusColors.current
                Surface(
                    color = status.warningContainer,
                    contentColor = status.onWarningContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        state.labels.locked,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
        item { SectionLabel(state.builtInTitle) }
        item {
            HisnCard {
                state.builtIn.forEachIndexed { index, rule ->
                    RuleSwitchRow(rule) { onLayer(rule.key, !rule.on) }
                    if (index < state.builtIn.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
        item { SectionLabel(state.customTitle) }
        item {
            HisnCard {
                state.custom.forEachIndexed { index, rule ->
                    RuleValueRow(rule) {
                        when (rule.key) {
                            "domains", "words" -> onEditRules(rule.key)
                            "blockedApps" -> onEditApps("blocked")
                            "scannedApps" -> onEditApps("scanned")
                            "lock" -> onEditLock()
                            "limit" -> onEditLimits()
                            "schedule" -> onEditSchedule()
                            else -> onOpenClassic()
                        }
                    }
                    if (index < state.custom.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
        item {
            Text(
                state.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onExportRules, modifier = Modifier.weight(1f)) {
                    Text(state.labels.exportRules)
                }
                TextButton(onClick = onImportRules, modifier = Modifier.weight(1f)) {
                    Text(state.labels.importRules)
                }
            }
        }
        item {
            Button(onClick = onOpenClassic, modifier = Modifier.fillMaxWidth()) {
                Text(state.labels.openClassic)
            }
        }
    }
}

@Composable
fun SettingsShellScreen(
    state: ShellSettings,
    onOpenClassic: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onAction: (String) -> Unit
) {
    ShellScreen {
        if (state.toggles.isNotEmpty()) {
            item { SectionLabel(state.labels.settingsControls) }
            item {
                HisnCard {
                    state.toggles.forEachIndexed { index, toggle ->
                        SettingToggleRow(toggle) { wanted -> onToggle(toggle.key, wanted) }
                        if (index < state.toggles.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
        if (state.actions.isNotEmpty()) {
            item { SectionLabel(state.labels.settingsAndroid) }
            item {
                HisnCard {
                    state.actions.forEachIndexed { index, action ->
                        SettingActionRow(action) { onAction(action.key) }
                        if (index < state.actions.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
        state.sections.forEach { section ->
            item { SectionLabel(section.label) }
            item {
                HisnCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (section.value.isNotEmpty()) {
                            Text(section.value, style = MaterialTheme.typography.titleMedium)
                        }
                        if (section.detail.isNotEmpty()) {
                            Text(
                                section.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = onOpenClassic, modifier = Modifier.fillMaxWidth()) {
                Text(state.labels.openClassic)
            }
        }
        item {
            Text(
                state.labels.liveBody,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun UnavailableShellScreen(
    labels: ShellLabels,
    onOpenClassic: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(labels.live, style = MaterialTheme.typography.titleLarge)
            Text(
                labels.liveBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onOpenClassic) { Text(labels.openClassic) }
        }
    }
}

@Composable
fun LoadingShellScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun LiveHero(
    state: ShellProtection,
    onCheck: () -> Unit,
    onOpenClassic: () -> Unit,
    onStartDns: () -> Unit
) {
    val status = LocalHisnStatusColors.current
    val protected = state.status == "protected"
    val accentTarget = if (protected) status.success else status.warning
    val containerTarget = if (protected) status.successContainer else status.warningContainer
    val onTarget = if (protected) status.onSuccessContainer else status.onWarningContainer
    val accent by animateColorAsState(accentTarget, tween(320, easing = FastOutSlowInEasing), label = "hero accent")
    val container by animateColorAsState(containerTarget, tween(320, easing = FastOutSlowInEasing), label = "hero container")
    val onContainer by animateColorAsState(onTarget, tween(320, easing = FastOutSlowInEasing), label = "hero text")
    val coverage = if (state.layers.isEmpty()) {
        0f
    } else {
        state.layers.count { it.on }.toFloat() / state.layers.size.toFloat()
    }
    val animatedCoverage by animateFloatAsState(
        targetValue = coverage,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
        label = "coverage"
    )

    Surface(
        color = container,
        contentColor = onContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                StatusSeal(animatedCoverage, accent, protected)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = state.title,
                        transitionSpec = {
                            (fadeIn(tween(200)) +
                                slideInVertically(tween(240, easing = FastOutSlowInEasing)) { it / 6 }) togetherWith
                                (fadeOut(tween(110)) +
                                    slideOutVertically(tween(140, easing = FastOutSlowInEasing)) { -it / 8 })
                        },
                        label = "hero title"
                    ) { title ->
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = onContainer
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    AnimatedContent(
                        targetState = state.body,
                        transitionSpec = { fadeIn(tween(240, delayMillis = 60)) togetherWith fadeOut(tween(100)) },
                        label = "hero body"
                    ) { body ->
                        Text(
                            body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            CoverageLedger(state.layers, accent)
            Spacer(Modifier.height(16.dp))

            val dnsOn = state.layers.firstOrNull { it.key == "network" }?.on == true
            when {
                state.canCheck -> {
                    Button(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(state.labels.runCheck)
                    }
                    TextButton(onClick = onOpenClassic, modifier = Modifier.fillMaxWidth()) {
                        Text(state.labels.openClassic)
                    }
                }
                !dnsOn -> {
                    Button(onClick = onStartDns, modifier = Modifier.fillMaxWidth()) {
                        Text(state.labels.dnsStart)
                    }
                    TextButton(onClick = onOpenClassic, modifier = Modifier.fillMaxWidth()) {
                        Text(state.labels.openClassic)
                    }
                }
                else -> {
                    Button(onClick = onOpenClassic, modifier = Modifier.fillMaxWidth()) {
                        Text(state.labels.openClassic)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusSeal(coverage: Float, accent: Color, protected: Boolean) {
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val radius = size.minDimension / 2f - stroke
            val topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)
            val arcSize = Size(radius * 2f, radius * 2f)
            drawCircle(color = accent.copy(alpha = 0.14f), radius = radius)
            drawCircle(
                color = accent.copy(alpha = 0.18f),
                radius = radius,
                style = Stroke(width = stroke)
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * coverage,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Surface(
            shape = CircleShape,
            color = accent.copy(alpha = 0.16f),
            contentColor = accent,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (protected) Icons.Outlined.Check else Icons.Outlined.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun CoverageLedger(layers: List<ShellLayer>, accent: Color) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            layers.chunked(2).forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    row.forEachIndexed { index, layer ->
                        if (index > 0) {
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .height(34.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(
                                            if (layer.on) accent else MaterialTheme.colorScheme.outline,
                                            CircleShape
                                        )
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    layer.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                layer.state,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (layer.on) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun IssueCard(
    issues: List<ShellIssue>,
    openClassicLabel: String,
    onOpenClassic: () -> Unit
) {
    val status = LocalHisnStatusColors.current
    Surface(
        color = status.warningContainer,
        contentColor = status.onWarningContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenClassic)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            issues.forEach { issue ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(issue.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                openClassicLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun LayerCard(layers: List<ShellLayer>, onOpenClassic: () -> Unit) {
    HisnCard {
        layers.forEachIndexed { index, layer ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenClassic)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    iconFor(layer.key),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(layer.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        layer.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    layer.state,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (layer.on) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            if (index < layers.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 52.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun SettingToggleRow(toggle: ShellToggle, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = toggle.on,
                enabled = toggle.enabled,
                role = Role.Switch,
                onValueChange = onChange
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(toggle.label, style = MaterialTheme.typography.titleMedium)
            if (toggle.detail.isNotEmpty()) {
                Text(
                    toggle.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = toggle.on,
            onCheckedChange = null,
            enabled = toggle.enabled
        )
    }
}

@Composable
private fun SettingActionRow(action: ShellAction, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(action.label, style = MaterialTheme.typography.titleMedium)
            if (action.detail.isNotEmpty()) {
                Text(
                    action.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RuleSwitchRow(rule: ShellRule, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(rule.label, style = MaterialTheme.typography.titleMedium)
            Text(
                rule.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            rule.state,
            style = MaterialTheme.typography.labelLarge,
            color = if (rule.on) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }
}

@Composable
private fun RuleValueRow(rule: ShellRule, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(rule.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
            rule.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HisnCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun ShellScreen(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

private fun formatTemplate(template: String, minutes: Int): String {
    val hour = (minutes / 60).coerceIn(0, 23)
    val minute = (minutes % 60).coerceIn(0, 59)
    return template.replace("__TIME__", String.format(Locale.getDefault(), "%02d:%02d", hour, minute))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeChoiceDialog(
    title: String,
    initialMinutes: Int,
    onPicked: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = (initialMinutes / 60).coerceIn(0, 23),
        initialMinute = (initialMinutes % 60).coerceIn(0, 59),
        is24Hour = DateFormat.is24HourFormat(context)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimeInput(state) },
        confirmButton = {
            TextButton(onClick = { onPicked(state.hour * 60 + state.minute) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
fun ImportPreviewDialog(
    preview: ShellImportPreview,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(preview.title) },
        text = {
            Text(
                preview.body,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            if (!preview.error) {
                TextButton(onClick = onApply) {
                    Text(preview.applyLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(preview.cancelLabel)
            }
        }
    )
}

@Composable
fun UninstallDialog(
    state: ShellUninstall,
    onCopy: () -> Unit,
    onAdmin: () -> Unit,
    onOpenClassic: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    state.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(state.status, style = MaterialTheme.typography.titleSmall)
                if (state.command.isNotEmpty()) {
                    Text(state.commandLabel, style = MaterialTheme.typography.labelLarge)
                    SelectionContainer {
                        Text(
                            state.command,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                TextButton(onClick = onAdmin) {
                    Text(state.adminLabel)
                }
                if (state.managed) {
                    Text(
                        state.managedNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (state.managed) {
                TextButton(onClick = onOpenClassic) {
                    Text("Open classic Hisn")
                }
            } else if (state.command.isNotEmpty()) {
                TextButton(onClick = onCopy) {
                    Text(state.copyLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ScheduleDialog(
    editor: ShellScheduleEditor,
    onSave: (Boolean, Int, Int, Int, Boolean, Set<String>) -> ShellActionResult,
    onDismiss: () -> Unit,
    onResult: (ShellActionResult) -> Unit
) {
    var enabled by remember(editor) { mutableStateOf(editor.enabled) }
    var start by remember(editor) { mutableStateOf(editor.startMinute) }
    var end by remember(editor) { mutableStateOf(editor.endMinute) }
    var days by remember(editor) { mutableStateOf(editor.days) }
    var allApps by remember(editor) { mutableStateOf(editor.allApps) }
    var selected by remember(editor) { mutableStateOf(editor.selected) }
    var pickingApps by remember(editor) { mutableStateOf(false) }
    var timeTarget by remember(editor) { mutableStateOf(0) }
    var error by remember(editor) { mutableStateOf<String?>(null) }

    if (pickingApps) {
        val picker = ShellAppPicker(
            kind = "schedule",
            title = if (allApps) editor.keepAppsLabel else editor.chooseAppsLabel,
            note = editor.privateNote,
            searchHint = editor.searchHint,
            emptyLabel = editor.emptyLabel,
            saveLabel = editor.useAppsLabel,
            cancelLabel = editor.cancelLabel,
            lockedNote = editor.lockedNote,
            locked = editor.locked,
            max = editor.max,
            selected = selected,
            apps = editor.apps
        )
        AppPickerDialog(
            picker = picker,
            onSave = { picked ->
                selected = picked
                pickingApps = false
                error = null
                ShellActionResult("", ok = true, refresh = false)
            },
            onDismiss = { pickingApps = false },
            onResult = { }
        )
        return
    }

    if (timeTarget != 0) {
        val template = if (timeTarget == 1) editor.startTemplate else editor.endTemplate
        TimeChoiceDialog(
            title = template.replace("__TIME__", "").trim().trimEnd(':').trim(),
            initialMinutes = if (timeTarget == 1) start else end,
            onPicked = { minutes ->
                if (timeTarget == 1) start = minutes else end = minutes
                timeTarget = 0
            },
            onDismiss = { timeTarget = 0 }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(editor.title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    editor.help,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            error = null
                        },
                        enabled = !editor.locked
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(editor.enableLabel, style = MaterialTheme.typography.titleSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { timeTarget = 1 },
                        enabled = !editor.locked,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(formatTemplate(editor.startTemplate, start))
                    }
                    TextButton(
                        onClick = { timeTarget = 2 },
                        enabled = !editor.locked,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(formatTemplate(editor.endTemplate, end))
                    }
                }
                Text(editor.daysTitle, style = MaterialTheme.typography.titleSmall)
                editor.dayLabels.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { day ->
                            val on = days and day.bit != 0
                            FilterChip(
                                selected = on,
                                onClick = {
                                    days = if (on) days and day.bit.inv() else days or day.bit
                                    error = null
                                },
                                enabled = !editor.locked,
                                label = { Text(day.label) }
                            )
                        }
                    }
                }
                Text(
                    editor.daysHelp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = allApps,
                        onCheckedChange = {
                            allApps = it
                            error = null
                        },
                        enabled = !editor.locked
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (allApps) editor.modeAllLabel else editor.modeListLabel,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text(
                    if (allApps) editor.modeAllHelp else editor.modeListHelp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { pickingApps = true },
                    enabled = !editor.locked,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        (if (allApps) editor.keepAppsLabel else editor.chooseAppsLabel) +
                            " · " + selected.size
                    )
                }
                if (editor.locked) {
                    Text(
                        editor.lockedNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (error != null) {
                    Text(
                        error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !editor.locked,
                onClick = {
                    val result = onSave(enabled, start, end, days, allApps, selected)
                    if (result.ok) {
                        onResult(result)
                    } else {
                        error = result.message
                    }
                }
            ) {
                Text(editor.saveLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(editor.cancelLabel)
            }
        }
    )
}

@Composable
fun LimitsDialog(
    editor: ShellLimitsEditor,
    onSave: (Map<String, Int>) -> ShellActionResult,
    onOpenUsageAccess: () -> Boolean,
    onDismiss: () -> Unit,
    onResult: (ShellActionResult) -> Unit
) {
    var query by remember(editor) { mutableStateOf("") }
    var selected by remember(editor) { mutableStateOf(editor.selected) }
    var expandedMenu by remember(editor) { mutableStateOf<String?>(null) }
    var error by remember(editor) { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val listFocus = remember { FocusRequester() }
    LaunchedEffect(editor) {
        focusManager.clearFocus(force = true)
        listFocus.requestFocus()
    }
    val visible = remember(editor.apps, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) {
            editor.apps
        } else {
            editor.apps.filter {
                it.label.lowercase().contains(needle) ||
                    it.packageName.lowercase().contains(needle)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(editor.title) },
        text = {
            Column(
                Modifier.heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    editor.help,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 96.dp)
                        .verticalScroll(rememberScrollState())
                )
                TextButton(
                    onClick = {
                        if (!onOpenUsageAccess()) {
                            error = editor.accessUnavailable
                        }
                    },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            editor.accessTitle,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            editor.accessDetail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(editor.searchHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${selected.size} / ${editor.max}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .focusRequester(listFocus)
                        .focusable()
                ) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(visible, key = { it.packageName }) { app ->
                            val checked = selected.containsKey(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = checked,
                                        enabled = !editor.locked,
                                        role = Role.Checkbox,
                                        onValueChange = { wanted ->
                                            selected = if (wanted) {
                                                selected + (app.packageName to editor.defaultMinutes)
                                            } else {
                                                selected - app.packageName
                                            }
                                            if (!wanted && expandedMenu == app.packageName) {
                                                expandedMenu = null
                                            }
                                            error = null
                                        }
                                    )
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = null,
                                    enabled = !editor.locked
                                )
                                Spacer(Modifier.width(6.dp))
                                if (app.icon != null) {
                                    Image(
                                        bitmap = app.icon.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp)
                                    )
                                } else {
                                    Spacer(Modifier.size(32.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        app.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (checked) {
                                    val minutes = selected[app.packageName] ?: editor.defaultMinutes
                                    val label = editor.choices
                                        .firstOrNull { it.minutes == minutes }?.label
                                        ?: "$minutes min"
                                    Box {
                                        TextButton(
                                            onClick = { expandedMenu = app.packageName }
                                        ) {
                                            Text(label)
                                        }
                                        DropdownMenu(
                                            expanded = expandedMenu == app.packageName,
                                            onDismissRequest = { expandedMenu = null }
                                        ) {
                                            editor.choices.forEach { choice ->
                                                DropdownMenuItem(
                                                    text = { Text(choice.label) },
                                                    onClick = {
                                                        selected = selected +
                                                            (app.packageName to choice.minutes)
                                                        expandedMenu = null
                                                        error = null
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (visible.isEmpty()) {
                            item {
                                Text(
                                    "No matching apps",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
                if (editor.locked) {
                    Text(
                        editor.lockedNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (error != null) {
                    Text(
                        error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !editor.locked,
                onClick = {
                    val result = onSave(selected)
                    if (result.ok) {
                        onResult(result)
                    } else {
                        error = result.message
                    }
                }
            ) {
                Text(editor.saveLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(editor.cancelLabel)
            }
        }
    )
}

@Composable
fun LockDialog(
    state: ShellLockState,
    onStart: (Int) -> ShellActionResult,
    onDismiss: () -> Unit,
    onResult: (ShellActionResult) -> Unit
) {
    var days by remember(state) { mutableStateOf(state.options.firstOrNull()?.days ?: 1) }
    var error by remember(state) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.active && state.remaining.isNotEmpty()) {
                    Text(
                        state.remaining,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        state.lockedNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    state.choosePeriod,
                    style = MaterialTheme.typography.titleSmall
                )
                state.options.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            FilterChip(
                                selected = days == option.days,
                                onClick = { days = option.days },
                                label = { Text(option.label) }
                            )
                        }
                    }
                }
                Text(
                    state.disclosure,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!state.canStart) {
                    Text(
                        state.needsRules,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (error != null) {
                    Text(
                        error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = state.canStart,
                onClick = {
                    val result = onStart(days)
                    if (result.ok) {
                        onResult(result)
                    } else {
                        error = result.message
                    }
                }
            ) {
                Text(state.startLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(state.cancelLabel)
            }
        }
    )
}

@Composable
fun AppPickerDialog(
    picker: ShellAppPicker,
    onSave: (Set<String>) -> ShellActionResult,
    onDismiss: () -> Unit,
    onResult: (ShellActionResult) -> Unit
) {
    var query by remember(picker) { mutableStateOf("") }
    var selected by remember(picker) { mutableStateOf(picker.selected) }
    var error by remember(picker) { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val listFocus = remember { FocusRequester() }
    // The dialog window focuses the first text field on some Android versions. Move focus to the
    // list so the keyboard stays down until the search field is tapped.
    LaunchedEffect(picker) {
        focusManager.clearFocus(force = true)
        listFocus.requestFocus()
    }
    val visible = remember(picker.apps, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) {
            picker.apps
        } else {
            picker.apps.filter {
                it.label.lowercase().contains(needle) ||
                    it.packageName.lowercase().contains(needle)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(picker.title) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    picker.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(picker.searchHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${selected.size} / ${picker.max}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .focusRequester(listFocus)
                        .focusable()
                ) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(visible, key = { it.packageName }) { app ->
                            val checked = selected.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = checked,
                                        enabled = !picker.locked,
                                        role = Role.Checkbox,
                                        onValueChange = { wanted ->
                                            selected = if (wanted) {
                                                selected + app.packageName
                                            } else {
                                                selected - app.packageName
                                            }
                                            error = null
                                        }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = null,
                                    enabled = !picker.locked
                                )
                                Spacer(Modifier.width(10.dp))
                                if (app.icon != null) {
                                    Image(
                                        bitmap = app.icon.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp)
                                    )
                                } else {
                                    Spacer(Modifier.size(36.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        app.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (visible.isEmpty()) {
                            item {
                                Text(
                                    picker.emptyLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
                if (picker.locked) {
                    Text(
                        picker.lockedNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (error != null) {
                    Text(
                        error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !picker.locked,
                onClick = {
                    val result = onSave(selected)
                    if (result.ok) {
                        onResult(result)
                    } else {
                        error = result.message
                    }
                }
            ) {
                Text(picker.saveLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(picker.cancelLabel)
            }
        }
    )
}

@Composable
fun RulesEditorDialog(
    editor: ShellRulesEditor,
    onPreview: (String) -> ShellRulesPreview,
    onSave: (String) -> ShellActionResult,
    onDismiss: () -> Unit,
    onResult: (ShellActionResult) -> Unit
) {
    var text by remember(editor) { mutableStateOf(editor.text) }
    var error by remember(editor) { mutableStateOf<String?>(null) }
    val preview = remember(text) { onPreview(text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(editor.title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    editor.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    label = { Text(editor.hint) },
                    minLines = 6,
                    maxLines = 10,
                    isError = error != null || preview.error != null,
                    enabled = !editor.locked,
                    modifier = Modifier.fillMaxWidth()
                )
                if (preview.summary.isNotEmpty()) {
                    Text(
                        preview.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val shownError = error ?: preview.error
                if (shownError != null) {
                    Text(
                        shownError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (editor.locked) {
                    Text(
                        editor.lockedNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !editor.locked,
                onClick = {
                    val result = onSave(text)
                    if (result.ok) {
                        onResult(result)
                    } else {
                        error = result.message
                    }
                }
            ) {
                Text(editor.saveLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(editor.cancelLabel)
            }
        }
    )
}

private fun iconFor(key: String): ImageVector = when (key) {
    "network" -> Icons.Outlined.Lock
    "apps" -> Icons.AutoMirrored.Outlined.List
    "words" -> Icons.Outlined.Search
    else -> Icons.Outlined.Info
}
