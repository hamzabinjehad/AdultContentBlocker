package app.hisn.android.nativeshell

import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.res.stringResource
import app.hisn.android.R
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
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

@Composable
fun RulesScreen(
    state: HisnState,
    onOpen: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val snackbar = LocalHisnSnackbar.current
    var showGrayDialog by remember { mutableStateOf(false) }
    var showScanAllDialog by remember { mutableStateOf(false) }

    if (showScanAllDialog) {
        AlertDialog(
            onDismissRequest = { showScanAllDialog = false },
            title = { Text(tr("Check every app?")) },
            text = {
                Text(
                    tr(
                        "Hisn will read on-screen text, and sample images when image checks are on, in " +
                            "every app instead of only the ones you chose. Nothing leaves this phone. " +
                            "System screens, the launcher, the phone app, keyboards and password fields " +
                            "are skipped. Expect more battery use and more mistaken blocks. While a " +
                            "protection lock is active this cannot be turned off."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showScanAllDialog = false
                    state.scanAll.set(true)
                }) { Text(tr("Turn on")) }
            },
            dismissButton = {
                TextButton(onClick = { showScanAllDialog = false }) {
                    Text(androidx.compose.ui.res.stringResource(android.R.string.cancel))
                }
            }
        )
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
            PageIntro(tr("Choose what Hisn blocks or checks. Every rule below is saved on this phone."))
        }

        if (state.lockRemainingMillis > 0L) {
            // Say it before the user tries an edit, instead of a toast after each refused change.
            item {
                HisnCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.commitment_remaining, state.lockLabel),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.commitment_locked_note),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = tr("Built-in checks"),
                supporting = tr("Each layer is independent and can be turned off.")
            )
        }
        item {
            HisnCard {
                SwitchRow(
                    icon = Icons.Rounded.LocalOffer,
                    title = tr("Adult tags"),
                    supporting = if (state.tags.on) {
                        tr("Matches adult words and tags on this device")
                    } else {
                        tr("Adult words and tags are not matched")
                    },
                    checked = state.tags.on,
                    onCheckedChange = state.tags::set
                )
                InsetDivider()
                SwitchRow(
                    icon = Icons.Rounded.Image,
                    title = tr("Images and video"),
                    supporting = if (state.images.on) {
                        tr("Samples the screen while you browse")
                    } else {
                        tr("Images and video are not sampled")
                    },
                    checked = state.images.on,
                    onCheckedChange = state.images::set
                )
                InsetDivider()
                SwitchRow(
                    icon = Icons.Rounded.Apps,
                    title = tr("Check every app"),
                    supporting = if (state.scanAll.on) {
                        tr("On · Adult tags and image checks read every app")
                    } else {
                        String.format(tr("Off · Checks read only the apps you chose (%1\$d)"), state.scannedCount)
                    },
                    checked = state.scanAll.on,
                    onCheckedChange = { wanted ->
                        if (wanted) showScanAllDialog = true else state.scanAll.set(false)
                    }
                )
                InsetDivider()
                SwitchRow(
                    icon = Icons.Rounded.PlayCircle,
                    title = tr("Short videos"),
                    supporting = if (state.shortVideos.on) {
                        tr("Blocks short-video feeds in the apps you choose")
                    } else {
                        tr("Short-video feeds are not blocked")
                    },
                    checked = state.shortVideos.on,
                    onCheckedChange = state.shortVideos::set
                )
                InsetDivider()
                SwitchRow(
                    icon = Icons.Rounded.Contrast,
                    title = tr("Gray screen"),
                    supporting = when {
                        state.gray.on && state.grayApplied -> tr("On · The whole screen is gray")
                        state.gray.on && !state.grayCanApply ->
                            tr("On · Waiting for Android's permission, then it applies by itself")
                        state.gray.on -> tr("On · Android has not applied it — see Accessibility")
                        else -> tr("Off · Turns the whole screen gray, not the app")
                    },
                    checked = state.gray.on,
                    onCheckedChange = { wanted ->
                        state.gray.set(wanted)
                        when {
                            // A lock refused turning it off; the state class already said why.
                            state.gray.on != wanted -> Unit
                            wanted && !state.grayCanApply -> showGrayDialog = true
                            wanted -> snackbar(HisnMessage(tr("Screen is gray")))
                            !state.grayCanApply && state.grayApplied ->
                                snackbar(HisnMessage(tr("Turn grayscale off in Accessibility › Color correction")))
                        }
                    }
                )
            }
        }

        item {
            SectionHeader(
                title = tr("Custom rules"),
                supporting = tr("Domains, words, apps and schedules.")
            )
        }
        item {
            // A saved rule does nothing while its layer is off, so the tile says so where the
            // person is looking instead of leaving it to the Protection tab.
            val guardReady = state.apps.on && state.guardConnected
            // Short enough for a half-width tile; the tile's own title says which rule it is about.
            val guardIssue = if (state.apps.on) tr("Not connected") else tr("Protection is off")
            val fixGuard = { state.openPermission("accessibility") }
            val limitedApps = state.appLimits.size
            TileGrid(
                listOf(
                    { modifier ->
                        RuleTile(
                            icon = Icons.Rounded.Language,
                            title = tr("Blocked domains"),
                            value = state.domains.size.toString(),
                            onClick = { onOpen("domains") },
                            modifier = modifier,
                            active = state.domains.isNotEmpty(),
                            issue = tr("DNS filtering is off").takeIf { state.domains.isNotEmpty() && !state.dns.on },
                            onIssue = { state.dns.set(true) }
                        )
                    },
                    { modifier ->
                        RuleTile(
                            icon = Icons.Rounded.Block,
                            title = tr("Blocked words"),
                            value = if (state.words.isEmpty()) tr("None") else state.words.size.toString(),
                            onClick = { onOpen("words") },
                            modifier = modifier,
                            active = state.words.isNotEmpty(),
                            issue = guardIssue.takeIf { state.words.isNotEmpty() && !guardReady },
                            onIssue = fixGuard
                        )
                    },
                    { modifier ->
                        RuleTile(
                            icon = Icons.Rounded.Apps,
                            title = tr("Apps to block"),
                            value = state.blockedApps.size.toString(),
                            onClick = { onOpen("apps") },
                            modifier = modifier,
                            active = state.blockedApps.isNotEmpty(),
                            issue = guardIssue.takeIf { state.blockedApps.isNotEmpty() && !guardReady },
                            onIssue = fixGuard,
                            extra = if (state.blockedApps.isEmpty()) null else {
                                {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        state.blockedApps.take(3).forEach { AppIcon(it, size = 24.dp) }
                                    }
                                }
                            }
                        )
                    },
                    { modifier ->
                        RuleTile(
                            icon = Icons.Rounded.Timer,
                            title = tr("Daily limit"),
                            value = when {
                                !state.limit.on -> tr("Off")
                                limitedApps == 0 -> tr("No apps")
                                else -> "$limitedApps ${tr(if (limitedApps == 1) "app" else "apps")}"
                            },
                            onClick = { onOpen("limit") },
                            modifier = modifier,
                            active = state.limit.on && limitedApps > 0,
                            issue = guardIssue.takeIf { state.limit.on && limitedApps > 0 && !guardReady },
                            onIssue = fixGuard
                        )
                    },
                    { modifier ->
                        RuleTile(
                            icon = Icons.Rounded.Schedule,
                            title = tr("Schedule"),
                            value = if (state.schedule.on) tr("On") else tr("Off"),
                            onClick = { onOpen("schedule") },
                            modifier = modifier,
                            active = state.schedule.on,
                            issue = guardIssue.takeIf { state.schedule.on && !guardReady },
                            onIssue = fixGuard
                        )
                    }
                )
            )
        }

        item { SectionHeader(tr("Tools")) }
        item {
            HisnCard {
                SettingRow(
                    icon = Icons.Rounded.Search,
                    title = stringResource(R.string.check_site_title),
                    supporting = stringResource(R.string.check_site_summary),
                    onClick = { onOpen("sitecheck") }
                )
                InsetDivider()
                SettingRow(
                    icon = Icons.Rounded.ImportExport,
                    title = stringResource(R.string.rules_file_title),
                    supporting = stringResource(R.string.rules_file_summary),
                    onClick = { onOpen("rulesfile") }
                )
            }
        }

        item { SectionHeader(tr("Protection lock")) }
        item {
            HisnCard {
                SettingRow(
                    icon = Icons.Rounded.Lock,
                    title = tr("Start a protection lock"),
                    supporting = tr("Make saved rules read-only for a set time"),
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
    val snackbar = LocalHisnSnackbar.current
    val haptics = rememberHisnHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Gray screen needs one Android permission")) },
        text = {
            Column {
                Text(
                    tr("Android only lets an app switch Color correction › Grayscale with a permission a computer grants once. Run this on the connected computer:")
                )
                Text(
                    text = command,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(tr("Without it, turn it on yourself in Accessibility › Color correction."))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("adb grant", command))
                haptics.confirm()
                snackbar(HisnMessage(tr("Command copied")))
            }) {
                Text(tr("Copy command"))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                ScreenGray.openSettings(context)
                onDismiss()
            }) {
                Text(tr("Open Accessibility"))
            }
        }
    )
}
