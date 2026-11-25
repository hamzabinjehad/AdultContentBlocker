package app.hisn.android.nativeshell

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisn.android.DnsVpnService
import app.hisn.android.FocusSession
import app.hisn.android.GlassMoments
import app.hisn.android.R
import app.hisn.android.StepIns
import app.hisn.core.GlassState
import app.hisn.core.VisualHealth
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle

/*
 * Unrot's Today, as the phone prototype draws it (design/unrot-phone-prototype-2026-11-24, "Today"):
 * the glass alone on the dark, its stage, one running count of clean time, the next stage, three
 * small numbers, four round tools, one line about adult content, Today's three, and Help now fixed
 * under the thumb. It answers three questions: is it working, how am I doing, what do I do now.
 *
 * Unrot only. Everything the screen shows comes from something the phone records.
 */

/** The prototype's sizes: 18 dp from the screen edge, 9 dp between rows. */
private val Edge = 18.dp

@Composable
internal fun UnrotTodayScreen(
    state: HisnState,
    onOpen: (String) -> Unit,
    onSelectTab: (Int) -> Unit,
    innerPadding: PaddingValues
) {
    val context = LocalContext.current
    val locale = androidx.core.os.ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: java.util.Locale.ENGLISH
    val haptics = rememberHisnHaptics()
    val glass = rememberGlass()

    // Before Unrot → now, read off the main thread, again each time the screen returns.
    var resumes by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { resumes++; onPauseOrDispose { } }
    val wins by produceState<WinsSummary?>(null, resumes, state.personal.day) {
        value = if (Wins.disabled) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { Wins.read(context, state) }.getOrNull()
        }
    }
    // The home-screen widget shows the same time given back as the glass.
    LaunchedEffect(wins?.givenBackMs) { wins?.let { GlassMoments.rememberGiven(context, it.givenBackMs) } }
    val feedToday by produceState<Long?>(null, resumes) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                if (!Usage.hasAccess(context)) null else {
                    val apps = FeedHistories.feedApps(context, state)
                    Usage.usedTodayAll(context).filterKeys { it in apps }.values.sum()
                }
            }.getOrNull()
        }
    }
    // The clock moves each minute.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(15_000); now = System.currentTimeMillis() } }

    val day = state.personal.day.coerceAtLeast(1)
    val zone = ZoneId.systemDefault()
    val startMs = remember(day, now / 86_400_000L) {
        LocalDate.now(zone).minusDays((day - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val brokenAt = remember(GlassUi.version.intValue, resumes) { GlassMoments.brokenAt(context) }
    val cleanSince = maxOf(startMs, brokenAt)
    val cleanMs = (now - cleanSince).coerceAtLeast(0L)
    val cleanDays = ((now - cleanSince) / 86_400_000L).toInt()

    val panes = unrotAdultPanes(state)
    val closed = panes.count { it }
    val stepsLeft = setupStepsLeft(state)
    val cracked = glass.look == GlassState.Look.CRACKED
    val crackedAt = remember(GlassUi.version.intValue, cracked) { GlassMoments.crackedAt(context) }
    val crackedClock = remember(crackedAt, locale) {
        if (crackedAt <= 0L) "" else Instant.ofEpochMilli(crackedAt).atZone(zone).toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm", locale))
    }
    val night = UnrotMood.quietNow.value && !cracked && glass.look != GlassState.Look.BROKEN
    // Keeps the quiet-hours colour current, as the old hero did, while this is the only glass on screen.
    QuietWatch(state.appSchedule)

    val givenBack = wins?.givenBackMs ?: 0L
    val stage = glassStage(givenBack)
    val before = wins?.base?.feedMs ?: 0L
    val backToday = if (before > 0L && feedToday != null) (before - feedToday!!).coerceAtLeast(0L) else null
    val trees = remember(wins, glass.look) {
        wins?.let { w -> woodTrees(w, GlassMoments.brokenDay(context)).count { !it.today && !it.dry } } ?: (day - 1)
    }

    var more by rememberSaveable { mutableStateOf(false) }
    var hard by rememberSaveable { mutableStateOf(false) }
    var instead by rememberSaveable { mutableStateOf(false) }
    var checkIn by rememberSaveable { mutableStateOf(false) }
    if (instead) UnrotInsteadSheet(state, onDismiss = { instead = false })
    if (checkIn) CheckInDialog(onDismiss = { checkIn = false })
    if (hard) UnrotHardMomentSheet(state, onDismiss = { hard = false })
    if (more) UnrotTodayMoreSheet(
        onDismiss = { more = false },
        onBreathe = state::openPause,
        onHard = { hard = true },
        onTab = onSelectTab,
        onOpen = onOpen,
        onInstead = { instead = true },
        onCheckIn = { checkIn = true }
    )

    val scheme = MaterialTheme.colorScheme
    val gold = scheme.primary
    val muted = scheme.onSurfaceVariant
    val card = scheme.surfaceContainer

    Box(Modifier.fillMaxSize().padding(innerPadding)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Edge, end = Edge, top = 4.dp, bottom = 124.dp)
        ) {
            // Header: the small gold date line, the title, and the five panes as a tiny dome.
            item(key = "top") {
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        val date = LocalDate.now(zone)
                        val eyebrow = date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
                        Text(
                            stringResource(R.string.ut_eyebrow, eyebrow, day).uppercase(locale),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold),
                            color = gold, maxLines = 1, modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            stringResource(R.string.tab_today),
                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, lineHeight = 34.sp),
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                    val label = when {
                        cracked -> stringResource(R.string.ut_chip_cracked)
                        stepsLeft > 0 -> setupStepsLeftLabel(stepsLeft)
                        closed == panes.size -> stringResource(R.string.ut_chip_closed)
                        else -> stringResource(R.string.ut_chip_open, panes.size - closed)
                    }
                    Box(Modifier.padding(top = 2.dp).tourTarget("shield")) {
                        PaneChip(
                            panes = panes, label = label, warn = cracked || stepsLeft > 0 || closed < panes.size, cracked = cracked,
                            onClick = { haptics.tap(); onSelectTab(1) }
                        )
                    }
                }
            }

            // The glass, alone on the dark with its own light; then its stage and one count.
            item(key = "glass") {
                Column(
                    Modifier.fillMaxWidth().pullUp(top = 46.dp, bottom = 0.dp)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null, role = Role.Button,
                            onClickLabel = stringResource(if (cracked) R.string.ut_help_seal else R.string.ut_more_woods)
                        ) { haptics.tap(); if (cracked) state.openPause() else onSelectTab(UNROT_TAB_PROGRESS) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LiveGlass(
                        art = when {
                            night -> R.drawable.unrot_glass_night
                            glass.look == GlassState.Look.CRACKED -> R.drawable.unrot_glass_cracked
                            glass.look == GlassState.Look.GOLD -> R.drawable.unrot_glass_gold
                            glass.look == GlassState.Look.BROKEN -> R.drawable.unrot_glass_broken
                            else -> glassArt(stage)
                        },
                        still = cracked,
                        night = night,
                        air = when (glass.look) {
                            GlassState.Look.GOLD -> AIR_GOLD
                            GlassState.Look.BROKEN, GlassState.Look.CRACKED -> AIR_DIM
                            else -> AIR_CLEAR
                        },
                        modifier = Modifier.fillMaxWidth(0.86f)
                    )
                    val stageLine = when {
                        cracked -> stringResource(R.string.ut_stage_cracked, crackedClock)
                        night -> stringResource(R.string.ut_stage_resting)
                        glass.look == GlassState.Look.GOLD -> stringResource(R.string.ut_stage_gold, day)
                        glass.look == GlassState.Look.BROKEN -> stringResource(R.string.ut_stage_broken)
                        else -> stringResource(R.string.ut_stage_day, stringResource(glassStageName(stage)), day)
                    }
                    Text(
                        stageLine.uppercase(locale),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold),
                        color = gold, modifier = Modifier.pullUp(top = 30.dp)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (cracked) {
                            val left = (glass.sealLeftMs / 1000L).coerceAtLeast(0L)
                            val desc = stringResource(R.string.ut_seal_desc, (left / 60).toInt(), (left % 60).toInt())
                            Text(
                                "%d:%02d".format(locale, left / 60, left % 60),
                                style = MaterialTheme.typography.displayLarge.copy(fontSize = 50.sp, lineHeight = 56.sp),
                                color = scheme.onSurface,
                                modifier = Modifier.clearAndSetSemantics { contentDescription = desc }
                            )
                            Text(stringResource(R.string.ut_seal_left), style = MaterialTheme.typography.bodyMedium, color = muted)
                        } else {
                            CleanClock(cleanMs)
                            Text(
                                if (cleanMs < 86_400_000L && LocalDate.now(zone) == Instant.ofEpochMilli(cleanSince).atZone(zone).toLocalDate())
                                    stringResource(R.string.ut_clean_today)
                                else stringResource(R.string.ut_clean_since, sinceWords(cleanSince, locale)),
                                style = MaterialTheme.typography.bodyMedium, color = muted
                            )
                        }
                    }
                }
            }

            // The next stage, or while cracked: nothing is lost.
            item(key = "next") {
                Column(Modifier) {
                    if (cracked) {
                        NextCard(warn = true, onClick = { haptics.tap(); state.openPause() }) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.ut_nothing_lost), style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp))
                                Text(
                                    if (cleanDays > 0) pluralStringResource(R.plurals.ut_seal_body, cleanDays, cleanDays)
                                    else stringResource(R.string.ut_seal_body_new),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), color = muted
                                )
                            }
                        }
                    } else {
                        NextCard(warn = false, onClick = { haptics.tap(); onSelectTab(UNROT_TAB_PROGRESS) }) {
                            Image(
                                painterResource(if (stage >= 5) R.drawable.unrot_glass_tree else glassArt(stage + 1)),
                                contentDescription = null, contentScale = ContentScale.Crop,
                                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(.5f) }),
                                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).graphicsLayer { alpha = .85f }
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (stage < 6) {
                                    val next = stringResource(glassStageName(stage + 1))
                                    val left = (glassStageAt[stage + 1] - givenBack).coerceAtLeast(60_000L)
                                    Text(
                                        buildAnnotatedString {
                                            append(next); append(" ")
                                            withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Medium)) {
                                                append(stringResource(R.string.ut_next_in, Wins.short(left)))
                                            }
                                        },
                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp)
                                    )
                                    val part = ((givenBack - glassStageAt[stage]).toFloat() / (glassStageAt[stage + 1] - glassStageAt[stage])).coerceIn(0f, 1f)
                                    GoldBar(part)
                                    Text(
                                        stringResource(R.string.ut_next_of, Wins.short(givenBack.coerceAtLeast(0L)), Wins.short(glassStageAt[stage + 1])),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), color = muted
                                    )
                                } else {
                                    Text(stringResource(R.string.ut_next_top), style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp))
                                    GoldBar(1f)
                                    Text(stringResource(R.string.ut_next_top_body, Wins.short(givenBack)),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), color = muted)
                                }
                            }
                        }
                    }
                }
            }

            // Three small numbers.
            item(key = "tiles") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cracked) SmallTile("1", stringResource(R.string.ut_tile_page_label)) { onSelectTab(1) }
                    else SmallTile(stringResource(R.string.ut_tile_panes, closed, panes.size), stringResource(R.string.ut_tile_panes_label)) { onSelectTab(1) }
                    SmallTile(trees.toString(), stringResource(R.string.ut_tile_trees_label)) { onSelectTab(UNROT_TAB_PROGRESS) }
                    SmallTile(backToday?.let { Wins.short(it) } ?: "–", stringResource(R.string.ut_tile_back_label)) { onSelectTab(UNROT_TAB_PROGRESS) }
                }
            }

            // Four round tools.
            item(key = "tools") {
                val focusLeft = remember(now) { (FocusSession.until(context) - now).coerceAtLeast(0L) }
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp).tourTarget("pause"),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RoundTool3D(R.drawable.unrot_3d_pause, stringResource(R.string.ut_breathe)) { haptics.nav(); state.openPause() }
                    RoundTool3D(
                        R.drawable.unrot_3d_target,
                        if (focusLeft > 0L) stringResource(R.string.today_tool_focus_left, Wins.short(focusLeft)) else stringResource(R.string.today_tool_focus),
                        lit = focusLeft > 0L
                    ) { haptics.nav(); FocusRequest.open.value = true }
                    RoundTool3D(R.drawable.unrot_3d_pencil, stringResource(R.string.ut_tool_notes)) { haptics.nav(); onOpen("notes") }
                    RoundTool3D(R.drawable.unrot_3d_dial, stringResource(R.string.today_tool_more)) { haptics.nav(); more = true }
                }
            }

            // One line about adult content.
            item(key = "adult") {
                val title = when {
                    cracked -> stringResource(R.string.ut_adult_cracked, crackedClock)
                    closed == panes.size -> stringResource(R.string.ut_adult_closed)
                    else -> pluralStringResource(R.plurals.ut_adult_open, panes.size - closed, panes.size - closed)
                }
                val body = when {
                    cracked -> stringResource(R.string.ut_adult_cracked_body)
                    closed == panes.size -> stringResource(R.string.ut_adult_closed_body, closed, panes.size)
                    else -> stringResource(R.string.ut_adult_open_body)
                }
                UnrotRow(
                    icon = R.drawable.unrot_3d_shield, title = title, body = body,
                    warn = cracked || closed < panes.size,
                    modifier = Modifier.padding(top = 14.dp)
                ) { haptics.tap(); if (cracked) state.openPause() else onSelectTab(1) }
            }

            // Setup first, until it is done; a milestone when one is due.
            if (stepsLeft > 0) item(key = "setup") {
                Box(Modifier.padding(top = 10.dp)) { UnrotSetupCard(state) }
            }
            item(key = "milestone") {
                var due by remember(state.personal.day) { mutableIntStateOf(MilestoneMemory.due(context, state.personal.day)) }
                if (due > 0) Box(Modifier.padding(top = 10.dp)) {
                    UnrotMilestoneCard(due, state.personal.pauses) { MilestoneMemory.keep(context, due); due = 0 }
                }
            }

            // Today's three: each tick comes from something the phone records.
            if (!Wins.disabled) item(key = "three") {
                val checked = remember(resumes, CheckIns.version.intValue) { CheckIns.today(context) }
                val paused = remember(resumes) { (StepIns.day(context, LocalDate.now())[StepIns.BREATHED] ?: 0) > 0 }
                val under = before > 0L && feedToday != null && feedToday!! < before
                val done = listOf(checked != null, paused, under).count { it }
                Column(Modifier.tourTarget("day")) {
                    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
                        Text(stringResource(R.string.three_title), style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                            modifier = Modifier.weight(1f).semantics { heading() })
                        Text(stringResource(R.string.ut_three_count, done), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = muted)
                    }
                    ThreeItem(
                        R.drawable.unrot_3d_tick, checked != null,
                        stringResource(if (checked != null) R.string.ut_three_checked else R.string.ut_three_checkin),
                        checked?.let { stringResource(checkInMoodName(it.first)) } ?: stringResource(R.string.ut_three_checkin_body)
                    ) { haptics.tap(); checkIn = true }
                    ThreeItem(
                        R.drawable.unrot_3d_pause, paused, stringResource(R.string.ut_three_pause),
                        stringResource(if (paused) R.string.ut_three_done else R.string.ut_three_not_yet)
                    ) { haptics.tap(); state.openPause() }
                    ThreeItem(
                        R.drawable.unrot_3d_hourglass, under, stringResource(R.string.ut_three_under),
                        if (before > 0L && feedToday != null) stringResource(R.string.ut_three_under_body, Wins.short(feedToday!!), Wins.short(before))
                        else stringResource(R.string.ut_three_under_unknown)
                    ) { haptics.tap(); onSelectTab(UNROT_TAB_PROGRESS) }
                }
            }
        }

        // Help, fixed under the thumb.
        HelpBar(
            sealing = cracked,
            onHelp = { haptics.nav(); state.openPause() },
            onHard = { haptics.tap(); hard = true },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** The check-in's mood word ("Tired, then better" in the prototype is the person's own day). */
private fun checkInMoodName(mood: Int): Int = when (mood.coerceIn(0, 4)) {
    0 -> R.string.checkin_mood_0
    1 -> R.string.checkin_mood_1
    2 -> R.string.checkin_mood_2
    3 -> R.string.checkin_mood_3
    else -> R.string.checkin_mood_4
}

/** "Saturday" within the last week, "12 Nov" before that. */
private fun sinceWords(ms: Long, locale: java.util.Locale): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(date, LocalDate.now(zone))
    return if (days in 1..6) date.dayOfWeek.getDisplayName(JTextStyle.FULL, locale)
    else date.format(DateTimeFormatter.ofPattern("d MMM", locale))
}

/** Sets [UnrotMood.quietNow] from the quiet-hours rule, each minute, as the old glass hero did. */
@Composable
private fun QuietWatch(quiet: app.hisn.core.DailyAppSchedule?) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); tick++ } }
    val resting = remember(quiet, tick) {
        val t = java.time.LocalTime.now()
        quiet != null && runCatching { quiet.activeAt(t.hour * 60 + t.minute, LocalDate.now().dayOfWeek) }.getOrDefault(false)
    }
    androidx.compose.runtime.SideEffect { UnrotMood.quietNow.value = resting }
}

/**
 * The five panes that keep adult content out, closed or open, the same five as Shield: sites, words
 * in apps, images, seeing which app is open, and the lock. An unset pane is an open pane.
 */
@Composable
internal fun unrotAdultPanes(state: HisnState): List<Boolean> {
    val runtime = state.runtime
    val guardReady = state.guardReady
    val dnsIssue = (runtime.dnsAttached && runtime.dnsLockdown == true) ||
        (runtime.dnsAttached && runtime.privateDnsStrict) ||
        runtime.dns == DnsVpnService.State.ERROR ||
        runtime.dns == DnsVpnService.State.DEGRADED ||
        (runtime.dns == DnsVpnService.State.OFF && runtime.dnsRequested) ||
        (runtime.dnsAttached && state.check == DnsVpnService.CheckState.FAILED)
    val imageIssue = !state.images.on || android.os.Build.VERSION.SDK_INT < 34 || state.guardPaused || !guardReady ||
        (state.scannedCount == 0 && !state.scanAll.on) ||
        runtime.visual == VisualHealth.State.UNREADABLE || runtime.visual == VisualHealth.State.PROTECTED ||
        runtime.visual == VisualHealth.State.ERROR
    return listOf(
        state.dns.on && runtime.dnsReady && !dnsIssue,
        state.tags.on && guardReady,
        state.images.on && !imageIssue,
        guardReady,
        state.lockRemainingMillis > 0L
    )
}

/** Unrot's own mark: the five panes of the glass as a tiny dome. Closed is gold, open an amber outline. */
@Composable
private fun PaneChip(panes: List<Boolean>, label: String, warn: Boolean, cracked: Boolean, onClick: () -> Unit) {
    val gold = MaterialTheme.colorScheme.primary
    val amber = Color(0xFFF2A65A)
    val desc = stringResource(R.string.ut_chip_desc, label)
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = desc },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .height(34.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF353535), Color(0xFF242424))), CircleShape)
                .border(1.dp, Color.White.copy(alpha = .07f), CircleShape)
                .padding(start = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(width = 28.dp, height = 17.dp)) {
                val heights = floatArrayOf(9f, 13f, 15f, 13f, 9f)
                val w = 4.dp.toPx(); val gap = 2.dp.toPx()
                val base = size.height - 3.dp.toPx()
                heights.forEachIndexed { i, hh ->
                    val h = hh.dp.toPx()
                    val left = i * (w + gap)
                    val tl = androidx.compose.ui.geometry.Offset(left, base - h)
                    val sz = androidx.compose.ui.geometry.Size(w, h)
                    val r = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                    if (panes.getOrElse(i) { false }) {
                        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFE19A), gold), startY = base - h, endY = base), tl, sz, r)
                    } else {
                        drawRoundRect(amber, tl, sz, r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
                    }
                }
                drawRect(Color(0xFF8D8472), androidx.compose.ui.geometry.Offset(0f, base + 1.dp.toPx()),
                    androidx.compose.ui.geometry.Size(5 * w + 4 * gap, 2.dp.toPx()))
                if (cracked) {
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width * .6f, 0f); lineTo(size.width * .5f, size.height * .33f)
                        lineTo(size.width * .64f, size.height * .53f); lineTo(size.width * .53f, size.height)
                    }
                    drawPath(p, Color(0xFF1F1F1F), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx()))
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                color = if (warn) Color(0xFFFFC58A) else Color(0xFFF3EAD6), maxLines = 1
            )
        }
    }
}

/**
 * The painted jar, melting into the page on every side: it breathes, the lamp glows, gold motes
 * rise. Still while cracked. With Reduce Motion on, nothing moves.
 */
@Composable
private fun LiveGlass(art: Int, still: Boolean, night: Boolean, air: Int, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "glass")
    val breath by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (night) 6000 else 3500, easing = LinearEasing), RepeatMode.Reverse), label = "jar")
    val lamp by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Reverse), label = "lamp")
    val b = if (reduce || still) 0f else breath
    val l = if (reduce) .5f else lamp
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        androidx.compose.animation.Crossfade(targetState = art, label = "glass art") { res ->
            Image(
                painterResource(res), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        scaleX = 1f + b * .012f; scaleY = 1f + b * .018f
                        transformOrigin = TransformOrigin(.5f, .78f)
                    }
                    .drawWithContent {
                        drawContent()
                        // An oval fade, so the painting has no edge on any colour of the moment.
                        drawRect(
                            Brush.radialGradient(
                                0f to Color.Black, .56f to Color.Black, .78f to Color.Black.copy(alpha = .5f), 1f to Color.Transparent,
                                center = androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .51f),
                                radius = size.minDimension * .57f
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            )
        }
        // The lamp: a warm glow that rises and falls over the glass.
        Box(Modifier.fillMaxSize().drawBehind {
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFD678).copy(alpha = (if (still) .06f else .10f) + l * .06f), Color.Transparent),
                    center = center.copy(y = size.height * .4f), radius = size.width * .45f
                ),
                radius = size.width * .45f, center = center.copy(y = size.height * .4f)
            )
        })
        if (!reduce) GlassAir(night, Modifier.fillMaxSize(), mood = air)
    }
}

/** "3d 14h 22m": Fraunces numbers, the units small and muted. */
@Composable
private fun CleanClock(ms: Long) {
    val minutes = ms / 60_000L
    val d = minutes / (24 * 60); val h = (minutes / 60) % 24; val m = minutes % 60
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val unit = SpanStyle(fontSize = 19.sp, color = muted, fontWeight = FontWeight.Medium, fontFamily = MaterialTheme.typography.bodyMedium.fontFamily)
    val ud = stringResource(R.string.glass_unit_d); val uh = stringResource(R.string.glass_unit_h); val um = stringResource(R.string.glass_unit_m)
    val desc = stringResource(R.string.ut_clock_desc, d.toInt(), h.toInt(), m.toInt())
    Text(
        buildAnnotatedString {
            append(d.toString()); withStyle(unit) { append(ud) }; append("  ")
            append(h.toString()); withStyle(unit) { append(uh) }; append("  ")
            append(m.toString()); withStyle(unit) { append(um) }
        },
        style = MaterialTheme.typography.displayLarge.copy(fontSize = 50.sp, lineHeight = 56.sp),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 6.dp).clearAndSetSemantics { contentDescription = desc }
    )
}

@Composable
private fun NextCard(warn: Boolean, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (warn) Modifier.border(1.5.dp, Color(0x80F2A65A), shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = if (warn) 16.dp else 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        content()
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A thin gold line for how far along something is. */
@Composable
private fun GoldBar(part: Float) {
    val gold = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (part > 0f) Box(
            Modifier.fillMaxWidth(part).height(5.dp).clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(gold.copy(alpha = .8f).compositeOver(Color(0xFF6A4A00)), gold)))
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SmallTile(value: String, label: String, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 22.sp), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 15.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** A well in the colour of the moment, lit gold from above, so the black-and-gold object stands out. */
@Composable
internal fun ObjectWell(res: Int, size: Dp, art: Dp, shape: androidx.compose.ui.graphics.Shape = CircleShape) {
    val scheme = MaterialTheme.colorScheme
    val raised = scheme.surfaceContainerHigh
    val gold = scheme.primary
    Box(
        Modifier.size(size).clip(shape)
            .background(
                Brush.radialGradient(
                    0f to gold.copy(alpha = .46f).compositeOver(raised),
                    .62f to gold.copy(alpha = .16f).compositeOver(raised),
                    1f to raised,
                    center = androidx.compose.ui.geometry.Offset.Unspecified
                )
            )
            .border(1.dp, gold.copy(alpha = .34f), shape),
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(res), contentDescription = null, modifier = Modifier.size(art))
    }
}

@Composable
private fun RoundTool3D(res: Int, label: String, lit: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(if (lit) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier) {
            ObjectWell(res, 60.dp, 40.dp)
        }
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A row as on the Mac: an object, a bold line, a quiet line, and a chevron. */
@Composable
internal fun UnrotRow(icon: Int, title: String, body: String, warn: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (warn) Modifier.border(1.5.dp, Color(0x80F2A65A), shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ObjectWell(icon, 36.dp, 28.dp, RoundedCornerShape(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(body, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ThreeItem(icon: Int, done: Boolean, title: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            ObjectWell(icon, 32.dp, 24.dp, RoundedCornerShape(9.dp))
            if (done) Box(
                Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).size(14.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("✓", fontSize = 9.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Help now under the thumb, and the hard moment beside it. While cracked it says Breathe to seal it. */
@Composable
private fun HelpBar(sealing: Boolean, onHelp: () -> Unit, onHard: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val gold = scheme.primary
    Row(modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.weight(1.3f).heightIn(min = 54.dp)
                .drawBehind {
                    drawRoundRect(Color.Black.copy(alpha = .35f), topLeft = androidx.compose.ui.geometry.Offset(0f, 6.dp.toPx()),
                        size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                }
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f).compositeOver(gold), gold.copy(alpha = .85f).compositeOver(Color(0xFF6A4A00)))))
                .border(1.dp, Color.White.copy(alpha = .25f), CircleShape)
                .clickable(role = Role.Button, onClick = onHelp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(painterResource(R.drawable.unrot_3d_wave), contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(9.dp))
            Text(stringResource(if (sealing) R.string.ut_help_seal else R.string.ut_help_now),
                style = MaterialTheme.typography.titleMedium, color = scheme.onPrimary)
        }
        Box(
            Modifier.weight(1f).heightIn(min = 54.dp).clip(CircleShape)
                .background(Color(0xEB282118).compositeOver(scheme.background))
                .border(1.dp, Color.White.copy(alpha = .10f), CircleShape)
                .clickable(role = Role.Button, onClick = onHard)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.ut_hard_title), style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp),
                color = scheme.onSurface, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

/**
 * "Had a hard moment": three honest answers. Stopped cracks the glass (a breath seals it), through
 * breaks it (what was given back stays), a strong pull goes straight to Breathe. No one sees this.
 */
@Composable
internal fun UnrotHardMomentSheet(state: HisnState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    GlassSheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.ut_hard_title), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.ut_hard_body), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            HardOption(R.drawable.unrot_3d_hand, stringResource(R.string.ut_hard_stopped)) {
                GlassMoments.cracked(context); GlassUi.changed(); onDismiss()
            }
            HardOption(R.drawable.unrot_3d_flash, stringResource(R.string.ut_hard_through)) {
                GlassMoments.broke(context); GlassUi.changed(); onDismiss()
            }
            HardOption(R.drawable.unrot_3d_wave, stringResource(R.string.ut_hard_pull)) {
                onDismiss(); state.openPause()
            }
        }
    }
}

@Composable
private fun HardOption(icon: Int, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ObjectWell(icon, 36.dp, 28.dp, RoundedCornerShape(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
    }
}

/**
 * More, as the prototype groups it: help first (Help now, Hard moment), then Keep it out, Your
 * time and See it grow, each a round object with its name.
 */
@Composable
internal fun UnrotTodayMoreSheet(
    onDismiss: () -> Unit,
    onBreathe: () -> Unit,
    onHard: () -> Unit,
    onTab: (Int) -> Unit,
    onOpen: (String) -> Unit,
    onInstead: () -> Unit,
    onCheckIn: () -> Unit
) {
    val context = LocalContext.current
    fun then(a: () -> Unit): () -> Unit = { onDismiss(); a() }
    GlassSheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.ut_more_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FirstTile(R.drawable.unrot_3d_wave, stringResource(R.string.ut_help_now), stringResource(R.string.ut_more_help_body), gold = true, then(onBreathe))
                FirstTile(R.drawable.unrot_3d_bulb, stringResource(R.string.ut_more_hard), stringResource(R.string.ut_more_hard_body), gold = false, then(onHard))
            }
            MoreGroup(stringResource(R.string.ut_more_keep_out)) {
                MoreItem(R.drawable.unrot_3d_shield, stringResource(R.string.ut_more_shield), then { onTab(1) })
                MoreItem(R.drawable.unrot_3d_notebook, stringResource(R.string.ut_more_lists), then { onOpen("lists") })
                MoreItem(R.drawable.unrot_3d_lock, stringResource(R.string.ut_more_lock), then { onOpen("lock") })
                MoreItem(R.drawable.unrot_3d_moon, stringResource(R.string.ut_more_quiet), then { onOpen("schedule") })
            }
            MoreGroup(stringResource(R.string.ut_more_your_time)) {
                MoreItem(R.drawable.unrot_3d_target, stringResource(R.string.ut_more_focus), then { FocusRequest.open.value = true })
                MoreItem(R.drawable.unrot_3d_hourglass, stringResource(R.string.ut_more_wait), then {
                    context.startActivity(Intent(context, app.hisn.android.OpenWaitSettingsActivity::class.java))
                })
                MoreItem(R.drawable.unrot_3d_dial, stringResource(R.string.ut_more_rules), then { onTab(UNROT_TAB_RULES) })
                MoreItem(R.drawable.unrot_3d_rocket, stringResource(R.string.ut_more_instead), then(onInstead))
            }
            MoreGroup(stringResource(R.string.ut_more_grow)) {
                MoreItem(R.drawable.unrot_3d_leaf, stringResource(R.string.ut_more_woods), then { onTab(UNROT_TAB_PROGRESS) })
                MoreItem(R.drawable.unrot_3d_trophy, stringResource(R.string.ut_more_progress), then { onTab(UNROT_TAB_PROGRESS) })
                MoreItem(R.drawable.unrot_3d_pencil, stringResource(R.string.ut_more_notes), then { onOpen("notes") })
                MoreItem(R.drawable.unrot_3d_tick, stringResource(R.string.ut_more_checkin), then(onCheckIn))
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.FirstTile(icon: Int, title: String, body: String, gold: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
            .background(if (gold) Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f).compositeOver(scheme.primary), scheme.primary))
                else Brush.verticalGradient(listOf(scheme.surfaceContainer, scheme.surfaceContainer)))
            .clickable(role = Role.Button, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (gold) scheme.onPrimary else scheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = if (gold) scheme.onPrimary.copy(alpha = .75f) else scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MoreGroup(title: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, content = content)
    }
}

@Composable
private fun MoreItem(icon: Int, label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ObjectWell(icon, 52.dp, 34.dp)
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** Pulls a piece up into the one above it, and gives the space back, as the prototype's negative margins do. */
private fun Modifier.pullUp(top: Dp = 0.dp, bottom: Dp = 0.dp): Modifier = this.layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val t = top.roundToPx(); val b = bottom.roundToPx()
    layout(p.width, (p.height - t - b).coerceAtLeast(0)) { p.place(0, -t) }
}
