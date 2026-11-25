package app.hisn.android.nativeshell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.hisn.android.DnsVpnService
import app.hisn.android.R
import app.hisn.core.VisualHealth
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The saved setting drives the control; the live state drives the coverage row. */
private class Layer(
    val icon: ImageVector,
    val label: String,
    val title: String,
    val supporting: String,
    val enabled: Boolean,
    val active: Boolean,
    val status: String,
    val set: (Boolean) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectionScreen(
    state: HisnState,
    copy: ProtectionCopy = EnglishProtectionCopy,
    onOpen: (String) -> Unit = {},
    mode: HomeMode = HomeMode.Full,
    onShowShield: () -> Unit = {},
    /** Unrot's five tabs: Shield shows only the adult panes ([SHIELD_ADULT]), Rules only feeds and apps ([SHIELD_FEEDS]). */
    part: Int = SHIELD_ALL
) {
    // Unrot splits this screen across two tabs: Today (the person's day) and Shield (the
    // protection itself). One computation of the protection state serves both.
    val today = mode == HomeMode.Today
    val shield = mode == HomeMode.Shield
    // Unrot's Shield tabs (All · Feeds and apps · Adult): one list, filtered.
    var shieldTab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(SHIELD_ALL) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val status = LocalHisnStatusColors.current
    val haptics = rememberHisnHaptics()
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    val runtime = state.runtime
    var refreshing by remember { mutableStateOf(false) }
    var celebrate by remember { mutableStateOf(false) }

    val attentionText = stringResource(R.string.protection_home_needs_attention)
    val waitingText = stringResource(R.string.protection_home_waiting)
    val startingText = stringResource(R.string.welcome_status_starting)
    val imagesUnsupported = android.os.Build.VERSION.SDK_INT < 34
    val noScannedApps = state.scannedCount == 0 && !state.scanAll.on
    var askScanAll by remember { mutableStateOf(false) }
    if (askScanAll) {
        ScanAllDialog(onConfirm = { askScanAll = false; state.scanAll.set(true) }, onDismiss = { askScanAll = false })
    }
    val guardReady = state.guardReady
    val dnsIssue = when {
        runtime.dnsAttached && runtime.dnsLockdown == true -> R.string.attention_dns_lockdown
        runtime.dnsAttached && runtime.privateDnsStrict -> R.string.attention_private_dns
        runtime.dns == DnsVpnService.State.ERROR -> R.string.attention_dns_error
        runtime.dns == DnsVpnService.State.DEGRADED -> R.string.attention_dns_degraded
        runtime.dns == DnsVpnService.State.OFF && runtime.dnsRequested -> R.string.attention_dns_not_running
        runtime.dnsAttached && state.check == DnsVpnService.CheckState.FAILED -> R.string.attention_route_failed
        else -> null
    }
    val guardIssue = when {
        // Turned off in Hisn on purpose: nothing to fix, the switch says Off.
        state.guardPaused -> null
        !runtime.guardExpected && !state.apps.on -> null
        !state.apps.on -> R.string.attention_guard_permission
        !guardReady -> R.string.attention_guard_disconnected
        else -> null
    }
    val imageIssue = when {
        !state.images.on -> null
        imagesUnsupported -> R.string.attention_visual_unsupported
        state.guardPaused -> R.string.attention_guard_paused
        !guardReady -> R.string.attention_guard_permission
        noScannedApps -> R.string.attention_visual_no_apps
        else -> when (runtime.visual) {
            VisualHealth.State.UNREADABLE -> R.string.visual_unreadable
            VisualHealth.State.PROTECTED -> R.string.visual_protected
            VisualHealth.State.ERROR -> R.string.visual_error
            else -> null
        }
    }
    val imageActive = state.images.on && imageIssue == null && runtime.visual == VisualHealth.State.SAMPLED
    val layers = listOf(
        Layer(
            Icons.Rounded.Wifi, copy.network, copy.dnsTitle,
            supporting = when {
                dnsIssue != null -> stringResource(dnsIssue)
                runtime.dnsReady -> copy.dnsOnText(state.dnsDomains)
                runtime.dns == DnsVpnService.State.STARTING -> startingText
                else -> copy.dnsOff
            },
            enabled = state.dns.on, active = runtime.dnsReady && dnsIssue == null,
            status = when {
                dnsIssue != null -> attentionText
                runtime.dnsReady -> copy.on
                runtime.dns == DnsVpnService.State.STARTING -> startingText
                else -> copy.off
            },
            set = state.dns::set
        ),
        Layer(
            Icons.Rounded.Apps, copy.apps, copy.appTitle,
            supporting = if (guardIssue != null) stringResource(guardIssue)
                else if (guardReady) copy.appsBlockedText(state.blockedApps.size) else copy.appOff,
            enabled = state.apps.on, active = guardReady,
            status = if (guardReady) copy.on else if (guardIssue != null) attentionText else copy.off,
            set = state.apps::set
        ),
        Layer(
            Icons.Rounded.LocalOffer, copy.tags, copy.tagsTitle,
            supporting = when {
                !state.tags.on -> copy.tagsOff
                !guardReady -> waitingText
                noScannedApps -> stringResource(R.string.protection_home_tags_no_scope)
                else -> copy.tagsOn
            },
            enabled = state.tags.on, active = state.tags.on && guardReady,
            status = if (!state.tags.on) copy.off else if (guardReady) copy.on else waitingText,
            set = state.tags::set
        ),
        Layer(
            Icons.Rounded.Image, copy.images, copy.imageTitle,
            supporting = when {
                imageIssue != null -> stringResource(imageIssue)
                imageActive -> stringResource(R.string.visual_sampled)
                state.images.on -> stringResource(R.string.visual_waiting)
                else -> copy.imageOff
            },
            enabled = state.images.on, active = imageActive,
            status = when {
                !state.images.on -> copy.off
                imageIssue != null -> attentionText
                imageActive -> copy.on
                else -> waitingText
            },
            set = state.images::set
        )
    )

    val milestoneContext = androidx.compose.ui.platform.LocalContext.current
    val otherVpn = shield && otherVpnActive()
    var milestoneDue by remember(state.personal.day) {
        mutableStateOf(if (today) MilestoneMemory.due(milestoneContext, state.personal.day) else 0)
    }
    // Unrot's "when something breaks": the parts the person turned on, each running or stopped.
    val unrotParts = buildList {
        if (state.shortVideos.on) add(stringResource(R.string.part_feeds) to guardReady)
        if (state.dns.on) add(stringResource(R.string.part_sites) to (runtime.dnsReady && dnsIssue == null))
        if ((state.limit.on && state.appLimits.isNotEmpty()) || state.appSchedule.enabled) {
            add(stringResource(R.string.part_limits) to guardReady)
        }
        if (state.tags.on) add(stringResource(R.string.part_words) to guardReady)
        // Waiting for its first screen after a restart is not "stopped"; only a real issue is.
        if (state.images.on) add(stringResource(R.string.part_images) to (imageIssue == null))
    }
    // The Shield tab's glass: the five panes that keep adult content out, closed or open, whether
    // or not the person has switched each one on (an unset pane is an open pane).
    val adultPanes = listOf(
        stringResource(R.string.part_sites) to (state.dns.on && runtime.dnsReady && dnsIssue == null),
        stringResource(R.string.part_words) to (state.tags.on && guardReady),
        stringResource(R.string.part_images) to (state.images.on && imageIssue == null),
        stringResource(R.string.pane_see_apps) to guardReady,
        stringResource(R.string.today_tool_lock) to (state.lockRemainingMillis > 0L)
    )
    val shownParts = if (unrotEdition && part == SHIELD_ADULT) adultPanes else unrotParts
    // Browsers that load pages through their maker's servers or a built-in VPN: the website
    // filter never sees which site opens there. An issue until blocked or "only Chrome" is on.
    val leakyContext = androidx.compose.ui.platform.LocalContext.current
    val leaky = remember(state.blockedApps.size, state.tags.on, state.dns.on) {
        if (!(state.tags.on || state.dns.on) || app.hisn.android.BrowserOnlyStore.on(leakyContext)) emptyList()
        else LeakyBrowsers.installed(leakyContext).filter { it !in state.blockedApps }
    }
    val leakyNames = leaky.joinToString(", ") { appLabel(leakyContext, it) }
    val issues = buildList {
        if (dnsIssue != null) add(Issue(
            Icons.Rounded.Wifi, copy.dnsTitle, stringResource(dnsIssue), state::fixDns
        ))
        if (guardIssue != null) add(Issue(
            Icons.Rounded.Security, copy.appTitle, stringResource(guardIssue)
        ) { state.openPermission("accessibility") })
        if (imageIssue != null && (imageIssue != R.string.attention_guard_permission || guardIssue == null)) {
            val fixImages: (() -> Unit)? = when {
                imagesUnsupported -> null
                state.guardPaused -> { { state.apps.set(true) } }
                !guardReady -> { { state.openPermission("accessibility") } }
                // Unrot has no per-app list for image checks, only "Check every app".
                noScannedApps -> if (unrotEdition) { { if (!state.scanBrowsers()) askScanAll = true } } else { { onOpen("apps") } }
                else -> { { onOpen("diagnostics") } }
            }
            add(Issue(Icons.Rounded.Image, copy.imageTitle, stringResource(imageIssue), fixImages))
        }
        if (leaky.isNotEmpty()) add(Issue(
            Icons.Rounded.Public, stringResource(R.string.leaky_browser_title, leakyNames),
            stringResource(R.string.leaky_browser_body, leakyNames)
        ) { leaky.forEach { state.setAppBlocked(it, true) } })
    }
    val showAttention = issues.isNotEmpty()
    val phoneAttention = state.phone.maker.isNotEmpty() &&
        (!state.phone.batteryUnrestricted || state.phone.backgroundRestricted == true)
    val context = androidx.compose.ui.platform.LocalContext.current
    val showAlwaysOn = runtime.dnsAttached && runtime.dnsAlwaysOn == false && runtime.dnsLockdown != true
    val otherSpace = remember {
        runCatching {
            context.getSystemService(android.os.UserManager::class.java).userProfiles.size > 1
        }.getOrDefault(false)
    }

    // Unrot: the steps left after onboarding, as one numbered list (see UnrotSetupGuide.kt).
    val stepsLeft = setupStepsLeft(state)
    val tab = if (unrotEdition && part != SHIELD_ALL) part else if (unrotEdition) shieldTab else SHIELD_ALL
    val guardPermissionText = stringResource(R.string.attention_guard_permission)

    val anythingActive = layers.any { it.active }
    val healthy = anythingActive && !showAttention
    val checking = state.check == DnsVpnService.CheckState.CHECKING
    val accent = if (healthy) status.success else status.warning
    val container = if (healthy) status.successContainer else status.warningContainer
    val onContainer = if (healthy) status.onSuccessContainer else status.onWarningContainer
    val heroTitle = when {
        showAttention -> copy.attentionTitle
        anythingActive -> stringResource(R.string.protection_home_active)
        runtime.dns == DnsVpnService.State.STARTING -> startingText
        else -> stringResource(R.string.protection_home_off)
    }
    val heroBody = when {
        showAttention -> issues.first().body
        anythingActive -> stringResource(R.string.protection_home_active_body)
        runtime.dns == DnsVpnService.State.STARTING -> stringResource(R.string.protection_home_starting_body)
        else -> stringResource(R.string.protection_home_off_body)
    }
    val fix = issues.firstNotNullOfOrNull { it.fix }
    val heroAction = when {
        fix != null -> copy.fixNow
        runtime.dnsReady -> stringResource(R.string.network_check_button)
        anythingActive || runtime.dns == DnsVpnService.State.STARTING || showAttention ->
            stringResource(R.string.protection_home_permissions)
        else -> stringResource(R.string.welcome_enable_website)
    }
    val heroActionIcon = if (runtime.dnsReady && fix == null) Icons.Rounded.FactCheck else Icons.Rounded.AutoFixHigh

    // Before Unrot → now: read off the main thread, again each time the screen returns.
    var winsResumes by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { winsResumes++; onPauseOrDispose { } }
    val winsContext = androidx.compose.ui.platform.LocalContext.current
    val wins by androidx.compose.runtime.produceState<WinsSummary?>(null, winsResumes, state.personal.day) {
        value = if (!unrotEdition) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { Wins.read(winsContext, state) }.getOrNull()
        }
    }
    // The home-screen widget shows the same time given back as the glass.
    LaunchedEffect(wins?.givenBackMs) { wins?.let { app.hisn.android.GlassMoments.rememberGiven(winsContext, it.givenBackMs) } }

    var wasActive by remember { mutableStateOf(healthy) }
    LaunchedEffect(healthy) {
        if (healthy && !wasActive && !reduceMotion) {
            celebrate = true
            delay(900)
            celebrate = false
        }
        wasActive = healthy
    }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            haptics.edge()
            state.refreshAll()
            delay(if (reduceMotion) 120 else 350)
            refreshing = false
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = ::refresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp,
                // Unrot keeps the pause as a floating button, so the last card must clear it.
                bottom = if (today) 104.dp else 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (today) {
                // The Glasshouse: the painted sprout and time given back, first thing on Today.
                // The Glasshouse home (design/glasshouse-2026-11-23): glass, one banner if
                // something is open, round tools, check-in and "Had a hard moment"; Breathe is
                // the pill above the tabs. Everything else follows below.
                if (unrotEdition) {
                    if (!Wins.disabled) item(key = "glass") {
                        UnrotGlassHero(givenBackMs = wins?.givenBackMs ?: 0L, day = state.personal.day, quiet = state.appSchedule)
                    }
                    // Day one: what Unrot does and the steps left come first, until setup is done.
                    if (stepsLeft > 0) {
                        item(key = "now") { UnrotNowCard(stepsLeft, unrotParts.filter { it.second }.map { it.first }) }
                        item(key = "setup") { UnrotSetupCard(state) }
                    }
                    item(key = "breathe-inline") {
                        Box(Modifier.fillMaxWidth().padding(top = 0.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                            val glassNow = rememberGlass()
                            UnrotPauseFab(
                                onPause = state::openPause,
                                onNote = { onOpen("notes") },
                                onLock = { onOpen("lock") },
                                pill = true,
                                sealing = glassNow.look == app.hisn.core.GlassState.Look.CRACKED
                            )
                        }
                    }
                    if (stepsLeft == 0 && !healthy) item(key = "banner") {
                        UnrotHomeBanner(
                            title = heroTitle,
                            body = heroBody,
                            action = copy.fixNow,
                            onAction = { if (fix != null) fix() else onShowShield() }
                        )
                    }
                    item(key = "quick-tools") {
                        UnrotHomeActions(state, onOpen, Modifier.tourTarget("pause").padding(vertical = 4.dp))
                    }
                    if (!Wins.disabled) item(key = "three") { UnrotTodaysThree(state, wins, winsResumes, onOpen) }
                }
                if (!healthy && !unrotEdition) {
                    item {
                        UnrotNeedsYouCard(
                            title = heroTitle,
                            body = heroBody,
                            action = if (fix != null) copy.fixNow else stringResource(R.string.today_open_shield),
                            onAction = { if (fix != null) fix() else onShowShield() },
                            onDetails = onShowShield,
                            parts = unrotParts
                        )
                    }
                }
                if (milestoneDue > 0) {
                    item {
                        UnrotMilestoneCard(milestoneDue, state.personal.pauses) {
                            MilestoneMemory.keep(milestoneContext, milestoneDue)
                            milestoneDue = 0
                        }
                    }
                }
                val winsNow = wins
                // Unrot's Home ends like the Glasshouse mockup: Today's three, the glass's
                // switches and the person's own words. The day, before/now and given-back
                // cards live on Progress.
                if (unrotEdition) {
                    item(key = "glass-switches") { UnrotGlassSwitches(state, onOpen) }
                } else if (winsNow != null) {
                    item {
                        Box(Modifier.tourTarget("day")) {
                            // A heavier stretch shows what still went well, never a rising line.
                            if (winsNow.heavier) UnrotHeavyCard(winsNow, state, winsResumes, onOpen, onChanged = { winsResumes++ })
                            else UnrotBeforeNowCard(winsNow, onOpen = { onOpen("progress") })
                        }
                    }
                    if (!winsNow.heavier || winsNow.givenBackMs >= 3_600_000L) {
                        item { UnrotGivenBackCard(winsNow, onShare = { shareWinsCard(winsContext, winsNow) }) }
                    }
                    if (unrotEdition && stepsLeft == 0 && healthy) {
                        item(key = "next") { UnrotNextCard(state, onOpen) }
                    }
                } else if (!unrotEdition) item {
                    Box(Modifier.tourTarget("day")) {
                    UnrotDayCard(
                        day = state.personal.day,
                        pauses = state.personal.pauses,
                        onStart = { onOpen("personal") },
                        onWeek = { onOpen("progress") },
                        hero = healthy,
                        sticker = if (milestoneDue > 0) 0 else MilestoneMemory.celebrated(milestoneContext)
                    )
                    }
                }
            }
            // Shield's tabs sit on top, like CleanPath's Blocker; the status follows.
            if (shield && unrotEdition && part == SHIELD_ALL) {
                item(key = "shield-tabs") { UnrotShieldTabs(shieldTab) { shieldTab = it } }
            }
            if (!today) item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Rules (feeds and apps) has no status of its own: the glass and its setup live on Shield.
                    if (shield && part == SHIELD_FEEDS) {
                    } else if (shield && stepsLeft > 0 && part != SHIELD_ADULT) {
                        UnrotSetupCard(state)
                    } else if (shield) {
                        // On the Shield tab the panes always show; unfinished setup follows them (below).
                        // Unrot: one sentence and the one action that would fix it.
                        // Today's saved count: the filter's own counter restarts at 0 with the filter.
                        val shieldContext = androidx.compose.ui.platform.LocalContext.current
                        val blocked = remember(state.blockedLookups) { app.hisn.android.StepIns.keptOutToday(shieldContext) }
                        // The mockup's status line: how many panes of the glass are open, by name below.
                        val openPanes = shownParts.count { !it.second }
                        val panes = shownParts.size
                        val paneTitle = when {
                            panes == 0 -> null
                            openPanes == 0 -> androidx.compose.ui.res.pluralStringResource(R.plurals.shield_panes_closed, panes, panes)
                            else -> androidx.compose.ui.res.pluralStringResource(R.plurals.shield_panes_open, panes, openPanes, panes)
                        }
                        UnrotShieldStatus(
                            healthy = healthy,
                            title = paneTitle ?: if (healthy) stringResource(R.string.shield_status_ok) else heroTitle,
                            body = when {
                                !healthy -> heroBody
                                runtime.dnsAttached -> stringResource(
                                    R.string.shield_status_ok_body,
                                    String.format(uiLocale(), "%,d", blocked)
                                )
                                else -> stringResource(R.string.shield_status_ok_body_no_filter)
                            },
                            // A healthy shield keeps the real route check, quietly.
                            action = if (!healthy || runtime.dnsReady) heroAction else null,
                            onAction = {
                                when {
                                    fix != null -> fix()
                                    runtime.dnsReady -> state.confirmNetworkCheck()
                                    anythingActive || runtime.dns == DnsVpnService.State.STARTING || showAttention ->
                                        onOpen("permissions")
                                    else -> state.fixDns()
                                }
                            },
                            parts = shownParts
                        )
                        if (stepsLeft > 0 && part == SHIELD_ADULT) UnrotSetupCard(state)
                    } else StatusHero(
                        title = heroTitle,
                        body = heroBody,
                        icon = if (healthy) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                        accent = accent,
                        container = container,
                        onContainer = onContainer,
                        actionLabel = heroAction,
                        actionIcon = heroActionIcon,
                        onAction = {
                            when {
                                fix != null -> fix()
                                runtime.dnsReady -> state.confirmNetworkCheck()
                                anythingActive || runtime.dns == DnsVpnService.State.STARTING || showAttention ->
                                    onOpen("permissions")
                                else -> state.fixDns()
                            }
                        },
                        layerStates = layers.map { Triple(it.label, it.active, it.status) },
                        checking = checking,
                        celebrate = celebrate
                    )

                    // While setup is unfinished the checklist already covers the app-watching
                    // permission, so the list keeps only what the checklist does not.
                    val listed = if (shield && stepsLeft > 0) issues.filter { it.body != guardPermissionText } else issues
                    AnimatedVisibility(
                        // Unrot's status card already says a single issue; the list is for two or more.
                        visible = listed.isNotEmpty() && !(shield && stepsLeft == 0 && listed.size <= 1),
                        enter = fadeIn(HisnMotion.fadeIn) + expandVertically(
                            animationSpec = HisnMotion.spatial(),
                            expandFrom = Alignment.Top
                        ),
                        exit = fadeOut(HisnMotion.fadeOut) + shrinkVertically(
                            animationSpec = HisnMotion.spatial(),
                            shrinkTowards = Alignment.Top
                        )
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeader(copy.needsAttention)
                            HisnCard {
                                listed.forEachIndexed { index, issue ->
                                    IssueRow(
                                        icon = issue.icon,
                                        title = issue.title,
                                        supporting = issue.body,
                                        onClick = issue.fix
                                    )
                                    if (index < listed.lastIndex) InsetDivider()
                                }
                            }
                        }
                    }
                }
            }

            if (shield && state.lockRemainingMillis > 0L) {
                item { UnrotLockBanner(state.lockLabel, onBreathe = state::openPause) }
            }
            if (shield && (tab == SHIELD_ALL || part == SHIELD_ADULT)) {
                item { UnrotShieldNumbers(state, onOpen) }
            }
            // Unrot's plan for today is the glass's switches on Home (UnrotGlassSwitches).
            if (today) {
                // Unrot's tools sit under the glass (UnrotHomeActions) instead.
                if (!unrotEdition) item(key = "quick-tools") {
                    Box(Modifier.tourTarget("pause")) {
                    UnrotQuickTools(
                        state = state,
                        onPause = state::openPause,
                        onNote = { onOpen("notes") },
                        onOpen = onOpen
                    )
                    }
                }
                // Unrot's numbers are on Progress, at the top.
                if (!unrotEdition) item { UnrotTodayNumbers(state) }
            } else if (!shield) {
                item {
                    PauseCard(onPause = state::openPause)
                }
            }

            if (today) item { UnrotTonightCard(state, onEdit = { onOpen("personal") }) }
            if (!shield) {
            item {
                ReminderCard()
            }

            // Unrot's Today shows the note in the evening card above; the hours live on Progress.
            if (!today) item {
                PersonalCard(
                    summary = state.personal,
                    onSetUp = { onOpen("personal") },
                    // Unrot shows the day count in its own card at the top.
                    showDay = !unrotEdition
                )
            }
            }

            if (!today) {
            if (showAlwaysOn || otherSpace) {
                item {
                    HisnCard {

                        if (showAlwaysOn) {
                            IssueRow(
                                icon = Icons.Rounded.Security,
                                title = stringResource(R.string.dns_offer_always_on),
                                supporting = stringResource(R.string.dns_offer_always_on_body),
                                onClick = { state.openPhoneSetting("vpn") }
                            )
                        }
                        if (showAlwaysOn && otherSpace) InsetDivider()
                        if (otherSpace) {
                            IssueRow(
                                icon = Icons.Rounded.Info,
                                title = stringResource(R.string.dns_other_profile),
                                supporting = stringResource(R.string.dns_other_profile_body),
                                onClick = null
                            )
                        }
                    }
                }
            }

            // The most common setup error: another VPN holds Android's one VPN slot.
            if (shield && unrotEdition && state.dns.on && !runtime.dnsAttached && otherVpn) {
                item {
                    UnrotVpnConflict(
                        onSettings = { state.openPhoneSetting("vpn") },
                        onKeep = { state.dns.set(false) }
                    )
                }
            }

            // Unrot lists this as a setup step instead.
            if (phoneAttention && !unrotEdition) {
                item {
                    HisnCard {
                        IssueRow(
                            icon = Icons.Rounded.BatteryChargingFull,
                            title = stringResource(
                                R.string.phone_attention_title,
                                state.phone.maker
                            ),
                            supporting = stringResource(R.string.phone_attention_body),
                            onClick = { onOpen("phone") }
                        )
                    }
                }
            }

            if (shield) {
                item {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val waiting = remember(state.blockedApps.size) { app.hisn.android.OpenWaitStore.apps(context).size }
                    UnrotShieldLayers(state, onOpen, waiting, tab = tab)
                }
            }
            if (!shield) item { SectionHeader(copy.layers) }
            if (!shield) item {
                HisnCard {
                    layers.forEachIndexed { index, layer ->
                        SwitchRow(
                            icon = layer.icon,
                            title = layer.title,
                            supporting = layer.supporting,
                            checked = layer.enabled,
                            onCheckedChange = layer.set
                        )
                        if (index < layers.lastIndex) InsetDivider()
                    }
                }
            }
            // The people cover rides on Images and video, so it needs Android 14 like that layer.
            if (android.os.Build.VERSION.SDK_INT >= 34 && !(shield && tab == SHIELD_FEEDS)) {
                item { PeopleCoverCard(imagesOn = state.images.on) }
            }

            if (shield) {
                item {
                    HisnCard {
                        RuleRow(
                            icon = Icons.Rounded.Tune,
                            title = stringResource(R.string.rules_all),
                            value = "",
                            onClick = { onOpen("rules") }
                        )
                    }
                }
            }
            if (!shield) item { SectionHeader(copy.yourRules) }
            if (!shield) item {
                val locale = androidx.core.os.ConfigurationCompat.getLocales(
                    androidx.compose.ui.platform.LocalConfiguration.current
                )[0] ?: java.util.Locale.ENGLISH
                fun count(n: Long) = String.format(locale, "%,d", n)
                StatStrip(
                    buildList {
                        add(Stat(copy.domains, count(state.domains.size.toLong())) { onOpen("domains") })
                        add(Stat(copy.appsBlocked, count(state.blockedApps.size.toLong())) { onOpen("apps") })
                        if (runtime.dnsAttached) {
                            add(Stat(tr("Lookups blocked"), count(state.blockedLookups)) { onOpen("diagnostics") })
                        }
                    }
                )
            }
            item {
                HisnCard {
                    RuleRow(
                        icon = Icons.Rounded.Info,
                        title = copy.bypass,
                        value = "",
                        onClick = { onOpen("info:bypass") }
                    )
                }
            }

            item {
                ReassuranceRow(
                    icon = Icons.Rounded.Lock,
                    text = copy.reassurance
                )
            }
            }

        }
        // The floating pause would sit on top of the tools row and repeat its Breathe button:
        // it only shows while that row is off screen.
        val toolsVisible by androidx.compose.runtime.remember {
            androidx.compose.runtime.derivedStateOf {
                listState.layoutInfo.visibleItemsInfo.any { it.key == "quick-tools" }
            }
        }
        // The Glasshouse Breathe sits under the tools; once that spot scrolls away (or behind
        // the tabs) the same pill floats, centred above the tabs.
        val breatheShown by androidx.compose.runtime.remember {
            androidx.compose.runtime.derivedStateOf {
                // Any part of the inline pill on screen: the floating one stays away, never two.
                val info = listState.layoutInfo
                info.visibleItemsInfo.any {
                    it.key == "breathe-inline" && it.offset + it.size > info.viewportStartOffset && it.offset < info.viewportEndOffset
                }
            }
        }
        if (today && unrotEdition && !breatheShown) {
            // "Breathe to seal it" while the glass is cracked.
            val glassNow = rememberGlass()
            UnrotPauseFab(
                onPause = state::openPause,
                onNote = { onOpen("notes") },
                onLock = { onOpen("lock") },
                expanded = true,
                pill = true,
                sealing = glassNow.look == app.hisn.core.GlassState.Look.CRACKED,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
            )
        } else if (today && !toolsVisible) {
            UnrotPauseFab(
                onPause = state::openPause,
                onNote = { onOpen("notes") },
                onLock = { onOpen("lock") },
                // Always named: a bare pause mark read as a media control, not "Breathe".
                expanded = true,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
            )
        }
    }
}

/**
 * The person's own words and numbers: greeting, reason, day count, and the hours their pauses
 * fall in. Nothing here is a rule, and an untouched install shows a one-line invitation instead.
 */
@Composable
private fun PersonalCard(summary: PersonalSummary, onSetUp: () -> Unit, showDay: Boolean = true) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    if (summary.empty) {
        // Unrot's day card at the top already invites the same setup.
        if (unrotEdition) return
        HisnCard {
            IssueRowPlain(
                title = resources.getString(app.hisn.android.R.string.personal_prompt_title),
                supporting = resources.getString(app.hisn.android.R.string.personal_prompt_body),
                onClick = onSetUp
            )
        }
        return
    }
    val total = summary.hours.sum()
    val peak = summary.hours.maxOrNull() ?: 0
    HisnCard {
        Column(
            modifier = Modifier
                .clickable(
                    role = Role.Button,
                    onClickLabel = resources.getString(app.hisn.android.R.string.personal_edit),
                    onClick = onSetUp
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = summary.greeting.ifEmpty { tr("Your progress") },
                style = MaterialTheme.typography.titleMedium
            )
            if (summary.reason.isNotEmpty()) {
                Text(
                    text = resources.getString(app.hisn.android.R.string.personal_quote, summary.reason),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (summary.day > 0 && showDay) {
                DayProgress(day = summary.day, pauses = summary.pauses)
            } else if (summary.metric.isNotEmpty()) {
                Text(
                    text = summary.metric,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (total > 0) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                Text(
                    text = tr("When your pauses happen"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val window = if (summary.peakStart >= 0) {
                    summary.peakStart until summary.peakStart + app.hisn.core.PauseHours.PEAK_SPAN
                } else {
                    IntRange.EMPTY
                }
                val from = hourLabel(summary.peakStart)
                val to = hourLabel(summary.peakStart + app.hisn.core.PauseHours.PEAK_SPAN)
                val sentence = if (summary.peakStart >= 0) {
                    String.format(
                        tr("Most pauses come between %1\$s and %2\$s. Plan something else for those hours."),
                        from, to
                    )
                } else {
                    tr("Not enough pauses yet to show a usual time.")
                }
                // Hours always read 0 to 23 left to right, like a clock axis, in both languages.
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides
                        androidx.compose.ui.unit.LayoutDirection.Ltr
                ) {
                    Column(
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = sentence
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            summary.hours.forEachIndexed { hour, count ->
                                val inPeak = window.any { it % 24 == hour }
                                val fraction = if (peak > 0) count.toFloat() / peak else 0f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(maxOf(fraction, 0.04f))
                                        .background(
                                            color = if (inPeak) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            },
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf("00", "06", "12", "18").forEach { label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                Text(text = sentence, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = tr("Only the hour is kept — no app, site or date."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The way into the voluntary breathing pause, high on the dashboard. It is an action, not a
 * status: nothing is blocked, and the screen it opens only breathes and counts the pause locally.
 */
@Composable
private fun PauseCard(onPause: () -> Unit) {
    val haptics = rememberHisnHaptics()
    HisnCard {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconBadge(
                    icon = Icons.Rounded.SelfImprovement,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.pause_home_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.pause_home_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        if (android.os.Build.VERSION.SDK_INT >= 25) R.string.pause_home_shortcut_hint
                        else R.string.pause_voluntary_summary
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                )
                FilledTonalButton(onClick = {
                    haptics.confirm()
                    onPause()
                }) {
                    Icon(
                        Icons.Rounded.SelfImprovement,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.pause_voluntary_action))
                }
            }
        }
    }
}

/**
 * "Day N" with a quiet bar to the next marker. The count only goes up: it is days since the
 * person set this up, so the bar shows time kept, never a streak that a hard day can break.
 */
@Composable
private fun DayProgress(day: Int, pauses: Int) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val next = app.hisn.core.PersonalProfile.nextMilestone(day)
    val progress = app.hisn.core.PersonalProfile.milestoneProgress(day)
    val reduceMotion = LocalReduceMotion.current
    val shown by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress,
        animationSpec = if (reduceMotion) androidx.compose.animation.core.snap()
            else HisnMotion.spatialSlow(),
        label = "day progress"
    )
    val left = next - day
    val nextLine = resources.getQuantityString(R.plurals.personal_milestone_next, left, left, next)
    val pauseLine = if (pauses > 0) {
        resources.getQuantityString(R.plurals.personal_pauses, pauses, pauses)
    } else {
        resources.getString(R.string.personal_no_pauses)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = resources.getString(R.string.personal_day_number, day),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = resources.getString(R.string.personal_day_since),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (next > 0) {
            LinearProgressIndicator(
                progress = { shown },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .semantics {
                        contentDescription =
                            resources.getString(R.string.personal_milestone_progress, next)
                    },
                drawStopIndicator = {}
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = nextLine,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = pauseLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun hourLabel(hour: Int): String =
    String.format(java.util.Locale.ROOT, "%02d:00", ((hour % 24) + 24) % 24)

@Composable
private fun IssueRowPlain(title: String, supporting: String, onClick: () -> Unit) {
    val haptics = rememberHisnHaptics()
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(role = Role.Button) {
            haptics.tick()
            onClick()
        }
    )
}

private class Issue(val icon: ImageVector, val title: String, val body: String, val fix: (() -> Unit)?)

@Composable
private fun IssueRow(
    icon: ImageVector,
    title: String,
    supporting: String,
    onClick: (() -> Unit)?
) {
    val status = LocalHisnStatusColors.current
    val haptics = rememberHisnHaptics()
    val arrow: (@Composable () -> Unit)? = if (onClick == null) null else {
        {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.titleMedium)
        },
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            IconBadge(
                icon = icon,
                container = status.warningContainer,
                content = status.warning
            )
        },
        trailingContent = arrow,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button) {
            haptics.tick()
            onClick()
        }
    )
}
