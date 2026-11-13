package app.hisn.android.nativeshell

import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Update
import androidx.compose.ui.res.stringResource
import app.hisn.android.BuildConfig
import app.hisn.android.R
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: HisnState,
    onOpen: (String) -> Unit = {}
) {
    var showThemeSheet by remember { mutableStateOf(false) }
    val themeLabel = when (state.themeMode) {
        "light" -> tr("Light")
        "dark" -> tr("Dark")
        else -> tr("System")
    }
    val themeSupporting = when (state.themeMode) {
        "light" -> tr("Always light")
        "dark" -> tr("Always dark")
        else -> tr("Follow system")
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
                    text = tr("Theme"),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = tr("The app repaints with a short colour blend; Android keeps its own dark-mode switch."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                listOf(
                    "system" to tr("Follow system"),
                    "light" to tr("Light"),
                    "dark" to tr("Dark")
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
        item { SectionHeader(tr("Protection")) }
        item {
            HisnCard {
                SettingRow(
                    icon = Icons.Rounded.Lock,
                    title = tr("Protection lock"),
                    supporting = if (state.lockRemainingMillis > 0L) {
                        stringResource(R.string.commitment_remaining, state.lockLabel)
                    } else {
                        tr("Not active · Make rules read-only")
                    },
                    onClick = { onOpen("lock") }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.Security,
                    title = tr("Uninstall protection"),
                    supporting = if (BuildConfig.SELF_PROTECTION) {
                        tr("Direct build · Works while a protection lock is active")
                    } else {
                        tr("Not in this build · Direct build only")
                    },
                    onClick = {
                        if (BuildConfig.SELF_PROTECTION) state.openClassic(2) else onOpen("info:uninstall")
                    }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.Notifications,
                    title = tr("Permissions"),
                    supporting = stringResource(
                        R.string.settings_permissions_summary,
                        stringResource(if (state.permissions["accessibility"] == true) R.string.perm_on else R.string.perm_off),
                        stringResource(if (state.permissions["notifications"] == true) R.string.perm_on else R.string.perm_off)
                    ),
                    onClick = { onOpen("permissions") }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = tr("Keep protection running"),
                    supporting = stringResource(
                        if (state.permissions["battery"] == true) R.string.settings_battery_on
                        else R.string.settings_battery_off
                    ),
                    onClick = {
                        if (state.permissions["battery"] == true) onOpen("info:keep")
                        else state.openPermission("battery")
                    }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.People,
                    title = tr("Guardian pairing"),
                    supporting = stringResource(R.string.settings_guardian_summary),
                    onClick = { state.openGuardian() }
                )
            }
        }

        item { SectionHeader(tr("More")) }
        item {
            HisnCard {
                SettingRow(
                    icon = Icons.Rounded.Person,
                    title = stringResource(R.string.settings_personal_title),
                    supporting = tr("Your name and reason, kept on this phone"),
                    onClick = { onOpen("personal") }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.Update,
                    title = stringResource(R.string.list_update_title),
                    supporting = tr("Check for a newer signed blocklist"),
                    onClick = { onOpen("listupdate") }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.PlayCircle,
                    title = stringResource(R.string.settings_replay_intro),
                    supporting = stringResource(R.string.settings_intro_summary),
                    onClick = { state.openIntroduction() }
                )
            }
        }

        item { SectionHeader(tr("Appearance")) }
        item {
            HisnCard {
                SettingRow(
                    icon = Icons.Rounded.DarkMode,
                    title = tr("Theme"),
                    supporting = themeSupporting,
                    trailing = { ValueTrailing(themeLabel) },
                    onClick = { showThemeSheet = true }
                )
                InsetDivider()
                SwitchRow(
                    icon = Icons.Rounded.ColorLens,
                    title = tr("Use system colors"),
                    supporting = tr("Match your wallpaper on Android 12+"),
                    checked = state.systemColors.on,
                    onCheckedChange = state.systemColors::set
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.Language,
                    title = tr("Language"),
                    supporting = "English · العربية",
                    onClick = { onOpen("info:language") }
                )
            }
        }

        item { SectionHeader(tr("Information")) }
        item {
            HisnCard {
                SettingRow(
                    icon = Icons.Rounded.Security,
                    title = tr("How Hisn works"),
                    supporting = tr("What it does and where it stops"),
                    onClick = { onOpen("info:how") }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.PrivacyTip,
                    title = tr("What can bypass Hisn"),
                    supporting = tr("The limits of each layer"),
                    onClick = { onOpen("info:bypass") }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.Lock,
                    title = tr("Privacy"),
                    supporting = tr("On-device matching · private screens"),
                    onClick = { onOpen("info:privacy") }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.BarChart,
                    title = tr("Session diagnostics"),
                    supporting = tr("Counters and network checks"),
                    onClick = { onOpen("diagnostics") }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.TextFields,
                    title = tr("Try word rules"),
                    supporting = tr("Test a rule locally"),
                    onClick = { onOpen("wordtester") }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.Security,
                    title = tr("About Hisn"),
                    supporting = stringResource(R.string.settings_about_summary, BuildConfig.VERSION_NAME),
                    onClick = { onOpen("info:about") }
                )
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            ReassuranceRow(
                icon = Icons.Rounded.Lock,
                text = tr("No account, no cloud, no browsing log.")
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
