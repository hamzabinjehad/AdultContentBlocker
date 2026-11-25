package app.hisn.android.nativeshell

import app.hisn.android.Edition
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.material.icons.rounded.VisibilityOff
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

/** Set once the gray-screen steps were shown in this run of Hisn. */
private var grayStepsShown = false

@Composable
fun RulesScreen(
    state: HisnState,
    onOpen: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val snackbar = LocalHisnSnackbar.current
    var showGrayDialog by remember { mutableStateOf(false) }
    // Android has no prompt for this permission, so "waiting" would wait forever: while Gray
    // screen is on but cannot apply, the steps are shown again once each time Hisn opens.
    LaunchedEffect(state.gray.on, state.grayCanApply) {
        if (state.gray.on && !state.grayCanApply && !grayStepsShown) {
            grayStepsShown = true
            showGrayDialog = true
        }
    }
    var showScanAllDialog by remember { mutableStateOf(false) }
    // The wait-before-opening screen is its own activity; re-read its count on coming back.
    var resumes by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }

    if (showScanAllDialog) {
        ScanAllDialog(onConfirm = { showScanAllDialog = false; state.scanAll.set(true) },
            onDismiss = { showScanAllDialog = false })
    }

    if (showGrayDialog) {
        GrayPermissionDialog(
            command = ScreenGray.grantCommand(context),
            onDismiss = { showGrayDialog = false }
        )
    }

    if (unrotEdition) {
        val lockedText = stringResource(R.string.rules_locked_refused)
        UnrotRulesList(
            state = state,
            onOpen = onOpen,
            resumes = resumes,
            onScanAll = { showScanAllDialog = true },
            onGrayWanted = { wanted ->
                state.gray.set(wanted)
                if (state.gray.on == wanted && wanted && !state.grayCanApply) showGrayDialog = true
            },
            onLockedRefusal = { snackbar(HisnMessage(lockedText)) }
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageIntro(tr("Choose what ${Edition.NAME} blocks or checks. Every rule below is saved on this phone."))
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
                        tr("Matches adult words and tags, and blocks known adult apps")
                    } else {
                        tr("Adult words and tags are not matched")
                    },
                    checked = state.tags.on,
                    onCheckedChange = state.tags::set
                )
                // Installed apps whose name or package id marks them as adult, blocked while
                // Adult tags is on. Shown only when there is one, with a way to allow a mistake.
                var flagged by remember(resumes, state.tags.on) {
                    mutableStateOf(if (state.tags.on) app.hisn.android.AdultNamedApps.installedFlagged(context) else emptyList())
                }
                var reviewFlagged by remember { mutableStateOf(false) }
                if (flagged.isNotEmpty()) {
                    InsetDivider()
                    SettingRow(
                        icon = Icons.Rounded.VisibilityOff,
                        title = tr("Blocked by their name"),
                        supporting = flagged.joinToString(", ") { appLabel(context, it) },
                        onClick = { reviewFlagged = true }
                    )
                }
                if (reviewFlagged) {
                    AlertDialog(
                        onDismissRequest = { reviewFlagged = false },
                        title = { Text(tr("Blocked by their name")) },
                        text = {
                            Column {
                                Text(tr("These apps call themselves adult, by their name or their website. Allow one only if it was flagged by mistake."))
                                for (packageName in flagged) {
                                    TextButton(onClick = {
                                        if (app.hisn.android.AdultNamedApps.allow(context, packageName)) {
                                            flagged = flagged - packageName
                                            if (flagged.isEmpty()) reviewFlagged = false
                                        } else {
                                            snackbar(HisnMessage(tr("A protection lock is running, so this stays on until it ends.")))
                                        }
                                    }) { Text(tr("Allow") + " " + appLabel(context, packageName)) }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { reviewFlagged = false }) { Text(tr("Done")) }
                        }
                    )
                }
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
                        if (state.scannedCount == 0 && isArabicUi()) "متوقف · تقرأ الفحوص التطبيقات التي تختارها فقط، ولم تختر شيئًا بعد"
                        else nums(String.format(tr("Off · Checks read only the apps you chose (%1\$d)"), state.scannedCount))
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
                var privateTabs by remember(resumes) { mutableStateOf(app.hisn.android.PrivateTabsStore.on(context)) }
                SwitchRow(
                    icon = Icons.Rounded.VisibilityOff,
                    title = androidx.compose.ui.res.stringResource(app.hisn.android.R.string.private_tabs_title),
                    supporting = androidx.compose.ui.res.stringResource(
                        when {
                            privateTabs && !(state.guardReady) -> app.hisn.android.R.string.private_tabs_needs_guard
                            privateTabs -> app.hisn.android.R.string.private_tabs_on
                            else -> app.hisn.android.R.string.private_tabs_off
                        }
                    ),
                    checked = privateTabs,
                    onCheckedChange = { wanted ->
                        if (app.hisn.android.PrivateTabsStore.set(context, wanted)) {
                            privateTabs = wanted
                        } else {
                            snackbar(HisnMessage(tr("A protection lock is running, so this stays on until it ends.")))
                        }
                    }
                )
                InsetDivider()
                var browserOnly by remember(resumes) { mutableStateOf(app.hisn.android.BrowserOnlyStore.on(context)) }
                SwitchRow(
                    icon = Icons.Rounded.VisibilityOff,
                    title = tr("Only browsers ${Edition.NAME} controls"),
                    supporting = when {
                        browserOnly && !(state.guardReady) -> tr("On · Needs App protection to block other browsers")
                        browserOnly -> tr("On · Other browsers are blocked; Chrome stays")
                        else -> tr("Off · Every browser can be used")
                    },
                    checked = browserOnly,
                    onCheckedChange = { wanted ->
                        if (app.hisn.android.BrowserOnlyStore.set(context, wanted)) {
                            browserOnly = wanted
                        } else {
                            snackbar(HisnMessage(tr("A protection lock is running, so this stays on until it ends.")))
                        }
                    }
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
            val guardReady = state.guardReady
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
                            issue = when {
                                state.domains.isEmpty() || state.runtime.dnsReady -> null
                                state.dnsState == app.hisn.android.DnsVpnService.State.STARTING -> null
                                state.dns.on -> stringResource(R.string.protection_home_needs_attention)
                                else -> tr("DNS filtering is off")
                            },
                            onIssue = state::fixDns
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
                        val waiting = remember(resumes) { app.hisn.android.OpenWaitStore.apps(context).size }
                        RuleTile(
                            icon = OpenWaitIcon,
                            title = androidx.compose.ui.res.stringResource(app.hisn.android.R.string.open_wait_rule),
                            value = if (waiting == 0) tr("Off") else "$waiting ${tr(if (waiting == 1) "app" else "apps")}",
                            onClick = {
                                context.startActivity(
                                    android.content.Intent(context, app.hisn.android.OpenWaitSettingsActivity::class.java)
                                )
                            },
                            modifier = modifier,
                            active = waiting > 0,
                            issue = guardIssue.takeIf { waiting > 0 && !guardReady },
                            onIssue = fixGuard
                        )
                    },
                    { modifier ->
                        RuleTile(
                            icon = Icons.Rounded.Schedule,
                            title = tr("Schedule"),
                            value = if (state.appSchedule.isConfigured) tr("On") else tr("Off"),
                            onClick = { onOpen("schedule") },
                            modifier = modifier,
                            active = state.appSchedule.isConfigured,
                            issue = guardIssue.takeIf { state.appSchedule.isConfigured && !guardReady },
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


/**
 * Unrot's Shield shows what protection is made of, in numbers it really holds (CleanPath's
 * "395k sites" idea, without the invented ones): the size of the built-in adult list, how many
 * apps have a rule, and lookups blocked since the website filter last started. Each opens the
 * screen behind it.
 */
@Composable
internal fun UnrotShieldNumbers(state: HisnState, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val locale = androidx.core.os.ConfigurationCompat.getLocales(
        androidx.compose.ui.platform.LocalConfiguration.current
    )[0] ?: java.util.Locale.ENGLISH
    val sites = app.hisn.android.BuildConfig.DOMAIN_SEED_COUNT
    val sitesShort = if (sites >= 10_000) {
        stringResource(R.string.shield_num_thousands, java.text.NumberFormat.getIntegerInstance(locale).format(sites / 1000))
    } else {
        java.text.NumberFormat.getIntegerInstance(locale).format(sites)
    }
    val waiting = remember(state.blockedApps.size, state.appLimits.size) { app.hisn.android.OpenWaitStore.apps(context) }
    val scheduled = state.appSchedule.let { if (it.enabled && !it.allApps) it.apps else emptySet() }
    val apps = (state.blockedApps.toSet() + state.appLimits.keys + waiting + scheduled).size
    val filterOn = state.runtime.dnsAttached
    val format = java.text.NumberFormat.getIntegerInstance(locale)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ShieldNumber(sitesShort, stringResource(R.string.shield_num_sites), Modifier.weight(1f)) { onOpen(if (unrotEdition) "lists" else "sitecheck") }
        ShieldNumber(format.format(apps), stringResource(R.string.shield_num_apps), Modifier.weight(1f)) { onOpen("apps") }
        ShieldNumber(
            if (filterOn) format.format(remember(state.blockedLookups) { app.hisn.android.StepIns.keptOutToday(context) }) else "—",
            stringResource(if (filterOn) R.string.shield_num_blocked else R.string.shield_num_filter_off),
            Modifier.weight(1f)
        ) { onOpen("diagnostics") }
    }
}

@Composable
private fun ShieldNumber(value: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .riseIn(1)
            .unrotFrame(shape)
            .background(LocalHisnStatusColors.current.card, shape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value.toLongOrNull()?.let { "${countUp(it)}" } ?: value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 2,
            maxLines = 3
        )
    }
}


/** "Check every app?": the honest cost of reading text and images in every app, before it turns on. */
@Composable
internal fun ScanAllDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(tr("Check every app?")) },
            text = {
                Text(
                    tr(
                        "${Edition.NAME} will read on-screen text, and sample images when image checks are on, in " +
                            "every app instead of only the ones you chose. Nothing leaves this phone. " +
                            "System screens, the launcher, the phone app, keyboards and password fields " +
                            "are skipped. Expect more battery use and more mistaken blocks. While a " +
                            "protection lock is active this cannot be turned off."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirm) { Text(tr("Turn on")) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(androidx.compose.ui.res.stringResource(android.R.string.cancel))
                }
            }
        )
}
