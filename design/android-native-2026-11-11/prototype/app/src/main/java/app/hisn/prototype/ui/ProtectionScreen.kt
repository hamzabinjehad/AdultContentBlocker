package app.hisn.prototype.ui

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
import androidx.compose.material.icons.rounded.ChevronRight
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
import app.hisn.prototype.HisnState
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

    var resolved by remember { mutableStateOf(false) }
    val showAttention = attention && !resolved

    var refreshing by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var celebrate by remember { mutableStateOf(false) }

    val layers = listOf(
        Layer(
            icon = Icons.Rounded.Wifi,
            label = copy.network,
            title = copy.dnsTitle,
            supportingOn = copy.dnsOn,
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
        showAttention -> copy.attentionBody
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

    fun performCheck(pulled: Boolean) {
        if (checking) return
        scope.launch {
            if (pulled) refreshing = true
            checking = true
            haptics.edge()
            delay(if (reduceMotion) 120 else 900)
            checking = false
            if (pulled) refreshing = false
            haptics.confirm()
            snackbar(HisnMessage(copy.checkPassed))
        }
    }

    fun fixGaps() {
        if (checking) return
        val before = layers.map { it.on }
        scope.launch {
            checking = true
            var step = 0
            layers.forEach { layer ->
                if (!layer.on) {
                    if (!reduceMotion) {
                        if (step > 0) delay(90)
                        layer.set(true)
                        haptics.tick()
                        step++
                    } else {
                        layer.set(true)
                    }
                }
            }
            checking = false
            resolved = true
            haptics.confirm()
            snackbar(
                HisnMessage(
                    text = copy.allArmed,
                    actionLabel = copy.undo,
                    onAction = {
                        layers.forEachIndexed { index, layer -> layer.set(before[index]) }
                        resolved = false
                    }
                )
            )
        }
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
                                IssueRow(
                                    icon = Icons.Rounded.Wifi,
                                    title = copy.dnsStoppedTitle,
                                    supporting = copy.dnsStoppedBody,
                                    onClick = {
                                        state.dns.set(true)
                                        resolved = true
                                        haptics.confirm()
                                    }
                                )
                                InsetDivider()
                                IssueRow(
                                    icon = Icons.Rounded.Security,
                                    title = copy.gapTitle,
                                    supporting = copy.gapBody,
                                    onClick = { onOpen("info:gap") }
                                )
                            }
                        }
                    }
                }
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
                Icons.Rounded.ChevronRight,
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
