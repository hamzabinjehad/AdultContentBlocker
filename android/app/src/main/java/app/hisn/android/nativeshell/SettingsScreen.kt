package app.hisn.android.nativeshell

import app.hisn.android.Edition
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Update
import androidx.compose.ui.res.stringResource
import app.hisn.android.BuildConfig
import app.hisn.core.OemGuide
import app.hisn.android.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
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
    onOpen: (String) -> Unit = {},
    /**
     * Unrot splits this page in two, as the mockup draws it: the You tab keeps the person, their
     * commitments and what keeps the app alive; "More" (one row deeper) keeps tools, appearance
     * and help. The Islamic edition shows the whole list ([YouPart.All]).
     */
    part: YouPart = if (unrotEdition) YouPart.You else YouPart.All,
    padding: PaddingValues = PaddingValues(0.dp)
) {
    val you = part != YouPart.More
    val more = part != YouPart.You
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
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (unrotEdition && you) {
            item {
                UnrotYouCard(
                    summary = state.personal,
                    onEdit = { onOpen("personal") },
                    onWeek = { onOpen("progress") }
                )
            }
        }
        if (unrotEdition && you) {
            item { SectionHeader(stringResource(R.string.you_commitment)) }
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
                    icon = Icons.Rounded.People,
                    title = tr("Guardian pairing"),
                    supporting = stringResource(R.string.settings_guardian_summary),
                    onClick = { state.openGuardian() }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.Security,
                    title = tr("Uninstall protection"),
                    supporting = if (BuildConfig.SELF_PROTECTION) {
                        state.uninstallSummary.ifEmpty { tr("Direct build · Works while a protection lock is active") }
                    } else {
                        tr("Not in this build · Direct build only")
                    },
                    onClick = {
                        if (BuildConfig.SELF_PROTECTION) state.openUninstall() else onOpen("info:uninstall")
                    }
                )
                }
            }
            item { SectionHeader(stringResource(R.string.you_keep_running)) }
            item {
                HisnCard {
                val phoneOpen = state.phone.open
                val phoneState = if (phoneOpen == 0) {
                    stringResource(R.string.phone_state_ready)
                } else {
                    stringResource(R.string.phone_state_check, phoneOpen)
                }
                SettingRow(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = stringResource(R.string.you_phone_setup, state.phone.maker.ifEmpty { "Android" }),
                    // The title already names the maker.
                    supporting = phoneState.replaceFirstChar { it.uppercase() },
                    onClick = { onOpen("phone") }
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
                }
            }
            item { SectionHeader(stringResource(R.string.you_more)) }
            item {
                HisnCard {
                    SettingRow(
                        icon = Icons.Rounded.Tune,
                        title = stringResource(R.string.you_more_title),
                        supporting = stringResource(R.string.you_more_body),
                        onClick = { onOpen("more") }
                    )
                }
            }
        }
        if (!unrotEdition) {
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
                        state.uninstallSummary.ifEmpty { tr("Direct build · Works while a protection lock is active") }
                    } else {
                        tr("Not in this build · Direct build only")
                    },
                    onClick = {
                        if (BuildConfig.SELF_PROTECTION) state.openUninstall() else onOpen("info:uninstall")
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
                val phoneOpen = state.phone.open
                val phoneState = if (phoneOpen == 0) {
                    stringResource(R.string.phone_state_ready)
                } else {
                    stringResource(R.string.phone_state_check, phoneOpen)
                }
                SettingRow(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = tr("Keep protection running"),
                    supporting = stringResource(
                        R.string.settings_keep_summary,
                        state.phone.maker.ifEmpty { "Android" },
                        phoneState
                    ),
                    onClick = { onOpen("phone") }
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

        }
        if (more && unrotEdition) {
            item { UnrotToolTiles(onOpen = onOpen, onReplay = { state.openIntroduction() }) }
        }
        if (more) {
        if (!unrotEdition) item { SectionHeader(tr("More")) }
        if (!unrotEdition) item {
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
                if (unrotEdition) {
                    val feelContext = androidx.compose.ui.platform.LocalContext.current
                    var sounds by remember { mutableStateOf(UnrotFeel.soundsOn(feelContext)) }
                    var vibration by remember { mutableStateOf(UnrotFeel.vibrationOn(feelContext)) }
                    InsetDivider()
                    SwitchRow(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        title = stringResource(R.string.feel_sounds),
                        supporting = stringResource(R.string.feel_sounds_body),
                        checked = sounds,
                        onCheckedChange = {
                            sounds = it
                            UnrotFeel.setSounds(feelContext, it)
                            if (it) UnrotFeel.play(feelContext, FeelSound.ON)
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        icon = Icons.Rounded.Vibration,
                        title = stringResource(R.string.feel_vibration),
                        supporting = stringResource(R.string.feel_vibration_body),
                        checked = vibration,
                        onCheckedChange = { vibration = it; UnrotFeel.setVibration(feelContext, it) }
                    )
                    var weekly by remember { mutableStateOf(app.hisn.android.WeeklyReport.isOn(feelContext)) }
                    InsetDivider()
                    SwitchRow(
                        icon = Icons.Rounded.DateRange,
                        title = stringResource(R.string.weekly_setting),
                        supporting = stringResource(R.string.weekly_setting_body),
                        checked = weekly,
                        onCheckedChange = { weekly = it; app.hisn.android.WeeklyReport.set(feelContext, it) }
                    )
                }
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.Translate,
                    title = tr("Language"),
                    supporting = "English · العربية",
                    onClick = { onOpen("info:language") }
                )
            }
        }

        item { SectionHeader(if (unrotEdition) stringResource(R.string.tools_help) else tr("Information")) }
        item {
            HisnCard {
                SettingRow(
                    icon = Icons.Rounded.Security,
                    title = tr("How ${Edition.NAME} works"),
                    supporting = tr("What it does and where it stops"),
                    onClick = { onOpen("info:how") }
                )
                InsetDivider()
                if (unrotEdition) {
                    val spreadContext = androidx.compose.ui.platform.LocalContext.current
                    SettingRow(
                        icon = Icons.Rounded.Star,
                        title = stringResource(R.string.spread_rate),
                        supporting = stringResource(R.string.spread_rate_row),
                        onClick = { Spread.rate(spreadContext) }
                    )
                    InsetDivider()
                    SettingRow(
                        icon = Icons.Rounded.Share,
                        title = stringResource(R.string.spread_tell),
                        supporting = stringResource(R.string.spread_tell_row),
                        onClick = { Spread.tellAFriend(spreadContext) }
                    )
                    InsetDivider()
                    SettingRow(
                        icon = Icons.Rounded.Lightbulb,
                        title = stringResource(R.string.why_screen_title),
                        supporting = stringResource(R.string.why_screen_row),
                        onClick = { onOpen("info:why") }
                    )
                    InsetDivider()
                }
                SettingRow(
                    icon = Icons.Rounded.PrivacyTip,
                    title = tr("What can bypass ${Edition.NAME}"),
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
                if (!unrotEdition) InsetDivider()
                if (!unrotEdition) SettingRow(
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
                    title = tr("About ${Edition.NAME}"),
                    supporting = stringResource(R.string.settings_about_summary, BuildConfig.VERSION_NAME),
                    onClick = { onOpen("info:about") }
                )
            }
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

enum class YouPart { All, You, More }

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
