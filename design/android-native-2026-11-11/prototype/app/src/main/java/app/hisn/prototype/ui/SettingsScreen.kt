package app.hisn.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.hisn.prototype.HisnState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: HisnState,
    onOpen: (String) -> Unit = {}
) {
    var showThemeSheet by remember { mutableStateOf(false) }
    val themeLabel = when (state.themeMode) {
        "light" -> "Light"
        "dark" -> "Dark"
        else -> "System"
    }
    val themeSupporting = when (state.themeMode) {
        "light" -> "Always light"
        "dark" -> "Always dark"
        else -> "Follow system"
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberHisnHaptics()

    if (showThemeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showThemeSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "The app repaints with a short colour blend; Android keeps its own dark-mode switch.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                listOf(
                    "system" to "Follow system",
                    "light" to "Light",
                    "dark" to "Dark"
                ).forEach { (mode, label) ->
                    ThemeOption(
                        label = label,
                        selected = state.themeMode == mode,
                        onClick = {
                            haptics.tick()
                            state.updateThemeMode(mode)
                            scope.launch { sheetState.hide() }
                                .invokeOnCompletion { showThemeSheet = false }
                        }
                    )
                }
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionHeader("Protection") }
        item {
            HisnCard {
                SettingRow(
                    icon = Icons.Rounded.Lock,
                    title = "Protection lock",
                    supporting = "Not active · Make rules read-only",
                    onClick = { onOpen("lock") }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.Security,
                    title = "Uninstall protection",
                    supporting = "Off · Direct build only",
                    onClick = { onOpen("info:uninstall") }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.Notifications,
                    title = "Permissions",
                    supporting = "App protection on · Notifications off",
                    onClick = { onOpen("permissions") }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = "Keep protection running",
                    supporting = "Android may pause it",
                    onClick = { onOpen("info:keep") }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.People,
                    title = "Guardian pairing",
                    supporting = "Pair with a guardian laptop",
                    onClick = { onOpen("info:guardian") }
                )
            }
        }

        item { SectionHeader("Appearance") }
        item {
            HisnCard {
                SettingRow(
                    icon = Icons.Rounded.DarkMode,
                    title = "Theme",
                    supporting = themeSupporting,
                    trailing = { ValueTrailing(themeLabel) },
                    onClick = { showThemeSheet = true }
                )
                InsetDivider()
                SwitchRow(
                    icon = Icons.Rounded.ColorLens,
                    title = "Use system colors",
                    supporting = "Match your wallpaper on Android 12+",
                    checked = state.systemColors.on,
                    onCheckedChange = state.systemColors::set
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.Language,
                    title = "Language",
                    supporting = "English · العربية",
                    onClick = { onOpen("info:language") }
                )
            }
        }

        item { SectionHeader("Information") }
        item {
            HisnCard {
                SettingRow(
                    icon = Icons.Rounded.Security,
                    title = "How Hisn works",
                    supporting = "What it does and where it stops",
                    onClick = { onOpen("info:how") }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.PrivacyTip,
                    title = "What can bypass Hisn",
                    supporting = "The limits of each layer",
                    onClick = { onOpen("info:bypass") }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.Lock,
                    title = "Privacy",
                    supporting = "On-device matching · private screens",
                    onClick = { onOpen("info:privacy") }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.BarChart,
                    title = "Session diagnostics",
                    supporting = "Counters and network checks",
                    onClick = { onOpen("diagnostics") }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.TextFields,
                    title = "Try word rules",
                    supporting = "Test a rule locally",
                    onClick = { onOpen("wordtester") }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.Security,
                    title = "About Hisn",
                    supporting = "Version 1.6.0 · no account",
                    onClick = { onOpen("info:about") }
                )
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            ReassuranceRow(
                icon = Icons.Rounded.Lock,
                text = "No account, no cloud, no browsing log."
            )
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
