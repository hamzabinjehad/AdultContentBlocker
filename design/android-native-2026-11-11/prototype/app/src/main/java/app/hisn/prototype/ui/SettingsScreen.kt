package app.hisn.prototype.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hisn.prototype.HisnState

@Composable
fun SettingsScreen(
    state: HisnState,
    onOpen: (String) -> Unit = {}
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    val themeLabel = when (state.themeMode) {
        "light" -> "Light"
        "dark" -> "Dark"
        else -> "System"
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    ThemeOption(
                        label = "Follow system",
                        selected = state.themeMode == "system",
                        onClick = {
                            state.updateThemeMode("system")
                            showThemeDialog = false
                        }
                    )
                    ThemeOption(
                        label = "Light",
                        selected = state.themeMode == "light",
                        onClick = {
                            state.updateThemeMode("light")
                            showThemeDialog = false
                        }
                    )
                    ThemeOption(
                        label = "Dark",
                        selected = state.themeMode == "dark",
                        onClick = {
                            state.updateThemeMode("dark")
                            showThemeDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Done")
                }
            }
        )
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
                    supporting = "Follow system",
                    trailing = { ValueTrailing(themeLabel) },
                    onClick = { showThemeDialog = true }
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
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
