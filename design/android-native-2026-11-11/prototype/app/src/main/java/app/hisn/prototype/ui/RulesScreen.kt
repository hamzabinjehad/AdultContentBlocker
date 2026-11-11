package app.hisn.prototype.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.hisn.prototype.HisnState
import app.hisn.prototype.ScreenGray

@Composable
fun RulesScreen(
    state: HisnState,
    onOpen: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showGrayDialog by remember { mutableStateOf(false) }
    var grayOnScreen by remember { mutableStateOf(ScreenGray.isGray(context)) }

    // Coming back to Rules is the moment the saved rules are reconciled with the phone: gray is
    // re-asserted if it was switched off elsewhere, and the real system state replaces the saved one.
    LaunchedEffect(Unit) {
        // Re-assert the saved rule when Android allows it, then show the real system state. A revoked
        // grant is not allowed to silently drop the rule: the row says Android has not applied it.
        ScreenGray.reassert(context, state.gray.on)
        grayOnScreen = ScreenGray.isGray(context)
    }

    if (showGrayDialog) {
        GrayPermissionDialog(
            command = ScreenGray.grantCommand(context),
            onDismiss = { showGrayDialog = false }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageIntro("Choose what Hisn blocks or checks. Every rule below is saved on this phone.")
        }

        item {
            SectionHeader(
                title = "Built-in checks",
                supporting = "Each layer is independent and can be turned off."
            )
        }
        item {
            HisnCard {
                SwitchRow(
                    icon = Icons.Rounded.LocalOffer,
                    title = "Adult tags",
                    supporting = if (state.tags.on) {
                        "Matches adult words and tags on this device"
                    } else {
                        "Adult words and tags are not matched"
                    },
                    checked = state.tags.on,
                    onCheckedChange = state.tags::set
                )
                InsetDivider()
                SwitchRow(
                    icon = Icons.Rounded.Image,
                    title = "Images and video",
                    supporting = if (state.images.on) {
                        "Samples the screen while you browse"
                    } else {
                        "Images and video are not sampled"
                    },
                    checked = state.images.on,
                    onCheckedChange = state.images::set
                )
                InsetDivider()
                SwitchRow(
                    icon = Icons.Rounded.PlayCircle,
                    title = "Short videos",
                    supporting = if (state.shortVideos.on) {
                        "Blocks short-video feeds in the apps you choose"
                    } else {
                        "Short-video feeds are not blocked"
                    },
                    checked = state.shortVideos.on,
                    onCheckedChange = state.shortVideos::set
                )
                InsetDivider()
                SwitchRow(
                    icon = Icons.Rounded.Contrast,
                    title = "Gray screen",
                    supporting = when {
                        state.gray.on && grayOnScreen -> "On · The whole screen is gray"
                        state.gray.on -> "On · Android has not applied it — see Accessibility"
                        else -> "Off · Turns the whole screen gray, not the app"
                    },
                    checked = state.gray.on,
                    onCheckedChange = { wanted ->
                        if (wanted) {
                            if (ScreenGray.set(context, true)) {
                                state.gray.set(true)
                                grayOnScreen = true
                                toast(context, "Screen is gray")
                            } else {
                                showGrayDialog = true
                            }
                        } else {
                            state.gray.set(false)
                            if (!ScreenGray.set(context, false)) {
                                toast(context, "Gray was turned on outside Hisn")
                            }
                            grayOnScreen = ScreenGray.isGray(context)
                        }
                    }
                )
            }
        }

        item {
            SectionHeader(
                title = "Custom rules",
                supporting = "Domains, words, apps and schedules."
            )
        }
        item {
            HisnCard {
                RuleRow(
                    Icons.Rounded.Language,
                    "Blocked domains",
                    state.domains.size.toString(),
                    onClick = { onOpen("domains") }
                )
                InsetDivider()
                RuleRow(
                    Icons.Rounded.Block,
                    "Blocked words",
                    if (state.words.isEmpty()) "None" else state.words.size.toString(),
                    onClick = { onOpen("words") }
                )
                InsetDivider()
                RuleRow(
                    Icons.Rounded.Apps,
                    "Apps to block",
                    state.blockedApps.size.toString(),
                    onClick = { onOpen("apps") }
                )
                InsetDivider()
                RuleRow(
                    Icons.Rounded.Schedule,
                    "Schedule",
                    if (state.schedule.on) "On" else "Off",
                    onClick = { onOpen("schedule") }
                )
                InsetDivider()
                RuleRow(
                    Icons.Rounded.Timer,
                    "Daily limit",
                    if (state.limit.on) "On" else "Off",
                    onClick = { onOpen("limit") }
                )
            }
        }

        item { SectionHeader("Protection lock") }
        item {
            HisnCard {
                SettingRow(
                    icon = Icons.Rounded.Lock,
                    title = "Start a protection lock",
                    supporting = "Make saved rules read-only for a set time",
                    onClick = { onOpen("lock") }
                )
            }
        }
    }
}

/**
 * Android will not let an app switch the grayscale filter without `WRITE_SECURE_SETTINGS`. Instead of
 * silently doing nothing, the app says how to grant it once, or where the switch lives in Settings.
 */
@Composable
private fun GrayPermissionDialog(command: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gray screen needs one Android permission") },
        text = {
            Column {
                Text(
                    "Android only lets an app switch Color correction › Grayscale with a " +
                        "permission a computer grants once. Run this on the connected computer:"
                )
                Text(
                    text = command,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text("Without it, turn it on yourself in Accessibility › Color correction.")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("adb grant", command))
                toast(context, "Command copied")
            }) {
                Text("Copy command")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                ScreenGray.openSettings(context)
                onDismiss()
            }) {
                Text("Open Accessibility")
            }
        }
    )
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
