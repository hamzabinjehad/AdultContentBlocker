package app.hisn.android.nativeshell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.hisn.android.DnsVpnService
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
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One protection layer as the screen needs it: list row, hero cell and the real setter. */
private class Layer(
    val icon: ImageVector,
    val label: String,
    val title: String,
    val supportingOn: String,
    val supportingOff: String,
    val on: Boolean,
    val set: (Boolean) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectionScreen(
    attention: Boolean,
    state: HisnState,
    copy: ProtectionCopy = EnglishProtectionCopy,
    onOpen: (String) -> Unit = {}
) {
    val status = LocalHisnStatusColors.current
    val snackbar = LocalHisnSnackbar.current
    val haptics = rememberHisnHaptics()
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current

    var refreshing by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var celebrate by remember { mutableStateOf(false) }

    val layers = listOf(
        Layer(
            icon = Icons.Rounded.Wifi,
            label = copy.network,
            title = copy.dnsTitle,
            supportingOn = copy.dnsOnText(state.dnsDomains),
            supportingOff = copy.dnsOff,
            on = state.dns.on,
            set = state.dns::set
        ),
        Layer(
            icon = Icons.Rounded.Apps,
            label = copy.apps,
            title = copy.appTitle,
            supportingOn = copy.appsBlockedText(state.blockedApps.size),
            supportingOff = copy.appOff,
            on = state.apps.on,
            set = state.apps::set
        ),
        Layer(
            icon = Icons.Rounded.LocalOffer,
            label = copy.tags,
            title = copy.tagsTitle,
            supportingOn = copy.tagsOn,
            supportingOff = copy.tagsOff,
            on = state.tags.on,
            set = state.tags::set
        ),
        Layer(
            icon = Icons.Rounded.Image,
            label = copy.images,
            title = copy.imageTitle,
            supportingOn = copy.imageOn,
            supportingOff = copy.imageOff,
            on = state.images.on,
            set = state.images::set
        )
    )

    // Only what is actually wrong right now; nothing is listed by default.
    val guardNeeded = state.tags.on || state.images.on || state.blockedApps.isNotEmpty()
    val issues = buildList {
        if (state.dnsState == DnsVpnService.State.ERROR) {
            add(Issue(Icons.Rounded.Wifi, copy.dnsStoppedTitle, copy.dnsStoppedBody) { state.dns.set(true) })
        }
        if (guardNeeded && !state.apps.on) {
            add(Issue(Icons.Rounded.Security, copy.guardOffTitle, copy.guardOffBody) {
                state.openPermission("accessibility")
            })
        } else if (guardNeeded && !state.guardConnected) {
            add(Issue(Icons.Rounded.Security, copy.guardWaitTitle, copy.guardWaitBody) {
                state.openPermission("accessibility")
            })
        }
    }
    val showAttention = attention && issues.isNotEmpty()

    val onCount = layers.count { it.on }
    val allOn = onCount == layers.size
    val partial = !showAttention && !allOn
    val accent = if (showAttention || partial) status.warning else status.success
    val container = if (showAttention || partial) status.warningContainer else status.successContainer
    val onContainer = if (showAttention || partial) status.onWarningContainer else status.onSuccessContainer
    val heroTitle = when {
        showAttention -> copy.attentionTitle
        partial -> copy.partialTitle
        else -> copy.protectedTitle
    }
    val heroBody = when {
        showAttention -> issues.firstOrNull()?.title?.let { "$it. ${copy.attentionBody}" } ?: copy.attentionBody
        partial -> copy.partialBody
        else -> copy.protectedBody
    }
    val heroIcon = if (allOn && !showAttention) Icons.Rounded.CheckCircle else Icons.Rounded.Warning
    val heroAction = when {
        showAttention -> copy.fixNow
        partial -> copy.fixGaps
        else -> copy.runCheck
    }
    val heroActionIcon = if (allOn && !showAttention) Icons.Rounded.FactCheck else Icons.Rounded.AutoFixHigh

    // The halo is a transition, not a permanent decoration: it fires once when the last gap closes.
    var wasAllOn by remember { mutableStateOf(allOn) }
    LaunchedEffect(allOn) {
        if (allOn && !wasAllOn && !reduceMotion) {
            celebrate = true
            delay(900)
            celebrate = false
        }
        wasAllOn = allOn
    }

    // Re-reads the real services and reports what is running; it never claims a pass.
    fun performCheck(pulled: Boolean) {
        if (checking) return
        scope.launch {
            if (pulled) refreshing = true
            checking = true
            haptics.edge()
            state.refreshAll()
            delay(if (reduceMotion) 120 else 500)
            checking = false
            if (pulled) refreshing = false
            snackbar(HisnMessage(copy.checkText(layers.count { it.on }, layers.size)))
        }
    }

    // One gap at a time: each layer needs its own Android consent, so the next one waits for
    // the user to finish the first. The ledger shows the result; nothing is announced early.
    fun fixGaps() {
        if (checking) return
        val issue = issues.firstOrNull()
        if (issue != null) {
            issue.fix()
            return
        }
        layers.firstOrNull { !it.on }?.set?.invoke(true)
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { performCheck(pulled = true) },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusHero(
                        title = heroTitle,
                        body = heroBody,
                        icon = heroIcon,
                        accent = accent,
                        container = container,
                        onContainer = onContainer,
                        actionLabel = heroAction,
                        actionIcon = heroActionIcon,
                        onAction = {
                            if (!allOn || showAttention) {
                                fixGaps()
                            } else {
                                performCheck(pulled = false)
                            }
                        },
                        layerStates = layers.map {
                            Triple(it.label, it.on, if (it.on) copy.on else copy.off)
                        },
                        checking = checking,
                        celebrate = celebrate
                    )

                    AnimatedVisibility(
                        visible = showAttention,
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
                                issues.forEachIndexed { index, issue ->
                                    IssueRow(
                                        icon = issue.icon,
                                        title = issue.title,
                                        supporting = issue.body,
                                        onClick = issue.fix
                                    )
                                    if (index < issues.lastIndex) InsetDivider()
                                }
                            }
                        }
                    }
                }
            }

            item {
                PersonalCard(
                    summary = state.personal,
                    onSetUp = { onOpen("personal") }
                )
            }

            item { SectionHeader(copy.layers) }
            item {
                HisnCard {
                    layers.forEachIndexed { index, layer ->
                        SwitchRow(
                            icon = layer.icon,
                            title = layer.title,
                            supporting = if (layer.on) layer.supportingOn else layer.supportingOff,
                            checked = layer.on,
                            onCheckedChange = layer.set
                        )
                        if (index < layers.lastIndex) InsetDivider()
                    }
                }
            }

            item { SectionHeader(copy.yourRules) }
            item {
                HisnCard {
                    RuleRow(
                        icon = Icons.Rounded.Language,
                        title = copy.domains,
                        value = state.domains.size.toString(),
                        onClick = { onOpen("domains") }
                    )
                    InsetDivider()
                    RuleRow(
                        icon = Icons.Rounded.Block,
                        title = copy.appsBlocked,
                        value = state.blockedApps.size.toString(),
                        onClick = { onOpen("apps") }
                    )
                    InsetDivider()
                    if (state.dns.on) {
                        RuleRow(
                            icon = Icons.Rounded.Security,
                            title = tr("Blocked lookups this session"),
                            value = String.format(java.util.Locale.getDefault(), "%,d", state.blockedLookups),
                            onClick = { onOpen("diagnostics") }
                        )
                        InsetDivider()
                    }
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
}

/**
 * The person's own words and numbers: greeting, reason, day count, and the hours their pauses
 * fall in. Nothing here is a rule, and an untouched install shows a one-line invitation instead.
 */
@Composable
private fun PersonalCard(summary: PersonalSummary, onSetUp: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (summary.empty) {
        HisnCard {
            IssueRowPlain(
                title = context.getString(app.hisn.android.R.string.personal_prompt_title),
                supporting = context.getString(app.hisn.android.R.string.personal_prompt_body),
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
                    onClickLabel = context.getString(app.hisn.android.R.string.personal_edit),
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
                    text = context.getString(app.hisn.android.R.string.personal_quote, summary.reason),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (summary.metric.isNotEmpty()) {
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

private class Issue(val icon: ImageVector, val title: String, val body: String, val fix: () -> Unit)

@Composable
private fun IssueRow(
    icon: ImageVector,
    title: String,
    supporting: String,
    onClick: () -> Unit
) {
    val status = LocalHisnStatusColors.current
    val haptics = rememberHisnHaptics()
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
