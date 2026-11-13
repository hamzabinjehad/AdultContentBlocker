package app.hisn.android.nativeshell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SectionHeader(
    title: String,
    supporting: String? = null
) {
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() }
        )
        if (supporting != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PageIntro(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@Composable
fun HisnCard(
    modifier: Modifier = Modifier,
    containerColor: Color = LocalHisnStatusColors.current.card,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(content = content)
    }
}

@Composable
fun InsetDivider(start: Dp = 68.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = start, end = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    )
}

@Composable
fun IconBadge(
    icon: ImageVector,
    container: Color,
    content: Color,
    size: Dp = 40.dp
) {
    Surface(
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(size * 0.52f)
            )
        }
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }
) {
    val haptics = rememberHisnHaptics()
    val rowModifier = if (onClick != null) {
        Modifier.clickable(role = Role.Button) {
            haptics.tick()
            onClick()
        }
    } else {
        Modifier
    }
    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.titleMedium)
        },
        supportingContent = supporting?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = rowModifier
    )
}

@Composable
fun SwitchRow(
    icon: ImageVector,
    title: String,
    supporting: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptics = rememberHisnHaptics()
    val interaction = remember { MutableInteractionSource() }
    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.titleMedium)
        },
        supportingContent = supporting?.let {
            {
                if (LocalReduceMotion.current) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    AnimatedContent(
                        targetState = it,
                        transitionSpec = {
                            (fadeIn(HisnMotion.fadeIn) +
                                slideInVertically(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                                ) { height -> height / 3 }) togetherWith
                                (fadeOut(HisnMotion.fadeOut) +
                                    slideOutVertically(
                                        animationSpec = tween(140, easing = FastOutSlowInEasing)
                                    ) { height -> -height / 3 })
                        },
                        label = "supporting copy"
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .toggleable(
                value = checked,
                interactionSource = interaction,
                role = Role.Switch,
                onValueChange = {
                    haptics.toggle(it)
                    onCheckedChange(it)
                }
            )
            .animateContentSize(HisnMotion.spatial())
    )
}

@Composable
fun RuleRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    val haptics = rememberHisnHaptics()
    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.titleMedium)
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (LocalReduceMotion.current) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    AnimatedContent(
                        targetState = value,
                        transitionSpec = {
                            (fadeIn(HisnMotion.fadeIn) +
                                slideInVertically(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                                ) { height -> height / 2 }) togetherWith
                                (fadeOut(HisnMotion.fadeOut) +
                                    slideOutVertically(
                                        animationSpec = tween(120, easing = FastOutSlowInEasing)
                                    ) { height -> -height / 2 })
                        },
                        label = "row value"
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(role = Role.Button) {
            haptics.tick()
            onClick()
        }
    )
}

@Composable
fun StatusDot(color: Color, size: Dp = 8.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * The status hero.
 *
 * One surface answers three questions: am I protected, which layer is the gap, and what happens
 * next. The seal's ring is the coverage count, so the headline and the ledger do not need a third
 * progress bar. Colour, ring and labels animate; the copy crossfades instead of bouncing.
 */
@Composable
fun StatusHero(
    title: String,
    body: String,
    icon: ImageVector,
    accent: Color,
    container: Color,
    onContainer: Color,
    actionLabel: String,
    onAction: () -> Unit,
    layerStates: List<Triple<String, Boolean, String>>,
    actionIcon: ImageVector? = null,
    checking: Boolean = false,
    celebrate: Boolean = false
) {
    val accentAnimated by animateColorAsState(
        targetValue = accent,
        animationSpec = HisnMotion.effects(),
        label = "hero accent"
    )
    val containerAnimated by animateColorAsState(
        targetValue = container,
        animationSpec = HisnMotion.effects(),
        label = "hero container"
    )
    val onContainerAnimated by animateColorAsState(
        targetValue = onContainer,
        animationSpec = HisnMotion.effects(),
        label = "hero text"
    )
    val coverage = if (layerStates.isEmpty()) {
        0f
    } else {
        layerStates.count { it.second }.toFloat() / layerStates.size.toFloat()
    }
    val interaction = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = containerAnimated)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                ProtectionSeal(
                    coverage = coverage,
                    accent = accentAnimated,
                    icon = icon,
                    checking = checking,
                    celebrate = celebrate
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = {
                            (fadeIn(HisnMotion.fadeIn) +
                                slideInVertically(
                                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                                ) { height -> height / 5 }) togetherWith
                                (fadeOut(tween(110)) +
                                    slideOutVertically(
                                        animationSpec = tween(160, easing = FastOutSlowInEasing)
                                    ) { height -> -height / 6 })
                        },
                        label = "hero title"
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.headlineSmall,
                            color = onContainerAnimated,
                            modifier = Modifier.semantics {
                                heading()
                                liveRegion = LiveRegionMode.Polite
                            }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    AnimatedContent(
                        targetState = body,
                        transitionSpec = {
                            fadeIn(tween(240, delayMillis = 60)) togetherWith fadeOut(tween(100))
                        },
                        label = "hero body"
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onContainerAnimated.copy(alpha = 0.78f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Three layers fit one ledger row; four wrap to a 2 × 2 grid so "Adult tags" and the
                // Arabic label never clip.
                val rows = if (layerStates.size <= 3) listOf(layerStates) else layerStates.chunked(2)
                Column {
                    rows.forEachIndexed { rowIndex, row ->
                        if (rowIndex > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEachIndexed { index, layer ->
                                if (index > 0) {
                                    Box(
                                        Modifier
                                            .width(1.dp)
                                            .height(34.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    )
                                }
                                LayerStat(
                                    label = layer.first,
                                    on = layer.second,
                                    value = layer.third,
                                    accent = accentAnimated,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onAction,
                interactionSource = interaction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .pressScale(interaction),
                shape = RoundedCornerShape(24.dp)
            ) {
                AnimatedContent(
                    targetState = actionIcon,
                    transitionSpec = {
                        (fadeIn(tween(200)) + scaleIn()) togetherWith (fadeOut(tween(120)) + scaleOut())
                    },
                    label = "hero action icon"
                ) { image ->
                    if (image != null) {
                        Icon(
                            imageVector = image,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (actionIcon != null) Spacer(Modifier.width(8.dp))
                AnimatedContent(
                    targetState = actionLabel,
                    transitionSpec = {
                        (fadeIn(HisnMotion.fadeIn) +
                            slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 3 }) togetherWith
                            (fadeOut(HisnMotion.fadeOut) +
                                slideOutVertically(tween(120, easing = FastOutSlowInEasing)) { -it / 3 })
                    },
                    label = "hero action"
                ) { label ->
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * The seal is the status: a full ring means every layer is on, a gap is visible as a missing arc.
 * It breathes while protected, spins while a check runs, and fires one halo when the last gap
 * closes. All of it is phase-drawn, not a stack of images.
 */
@Composable
private fun ProtectionSeal(
    coverage: Float,
    accent: Color,
    icon: ImageVector,
    checking: Boolean,
    celebrate: Boolean
) {
    val reduceMotion = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduceMotion) coverage else 0f) }
    LaunchedEffect(coverage) {
        progress.animateTo(coverage, HisnMotion.expressive())
    }
    val halo = remember { Animatable(0f) }
    LaunchedEffect(celebrate) {
        if (celebrate && !reduceMotion) {
            halo.snapTo(0f)
            halo.animateTo(1f, tween(780, easing = FastOutSlowInEasing))
            halo.snapTo(0f)
        }
    }
    val transition = rememberInfiniteTransition(label = "seal")
    val breathe by transition.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "seal breathe"
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "seal spin"
    )

    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val radius = size.minDimension / 2f - stroke
            val arcTopLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            // A soft ring behind the track, breathing only when the app is fully protected.
            drawCircle(
                color = accent.copy(alpha = if (coverage == 1f) breathe else 0.08f),
                radius = radius
            )
            drawCircle(
                color = accent.copy(alpha = 0.16f),
                radius = radius,
                style = Stroke(width = stroke)
            )
            if (checking && !reduceMotion) {
                drawArc(
                    color = accent,
                    startAngle = spin,
                    sweepAngle = 92f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            } else {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.value,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            if (halo.value > 0f) {
                drawCircle(
                    color = accent.copy(alpha = (1f - halo.value) * 0.30f),
                    radius = radius * (1f + halo.value * 0.20f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = accent.copy(alpha = 0.14f),
            contentColor = accent,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = icon,
                    transitionSpec = {
                        (fadeIn(tween(200)) + scaleIn()) togetherWith (fadeOut(tween(120)) + scaleOut())
                    },
                    label = "hero icon"
                ) { image ->
                    Icon(image, contentDescription = null, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

private fun scaleIn() = androidx.compose.animation.scaleIn(
    animationSpec = tween(240, easing = FastOutSlowInEasing),
    initialScale = 0.72f
)

private fun scaleOut() = androidx.compose.animation.scaleOut(
    animationSpec = tween(140, easing = FastOutSlowInEasing),
    targetScale = 0.72f
)

@Composable
private fun LayerStat(
    label: String,
    on: Boolean,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val dotColor by animateColorAsState(
        targetValue = if (on) accent else MaterialTheme.colorScheme.outline,
        animationSpec = HisnMotion.effects(),
        label = "layer dot"
    )
    val dotScale by animateFloatAsState(
        targetValue = if (on) 1f else 0.72f,
        animationSpec = if (on) HisnMotion.expressive() else HisnMotion.spatialFast(),
        label = "layer dot scale"
    )
    val valueColor by animateColorAsState(
        targetValue = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        animationSpec = HisnMotion.effects(),
        label = "layer value"
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        scaleX = dotScale
                        scaleY = dotScale
                    }
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(2.dp))
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (fadeIn(HisnMotion.fadeIn) +
                    slideInVertically(tween(200, easing = FastOutSlowInEasing)) { it / 2 }) togetherWith
                    (fadeOut(HisnMotion.fadeOut) +
                        slideOutVertically(tween(120, easing = FastOutSlowInEasing)) { -it / 2 })
            },
            label = "layer value"
        ) { word ->
            Text(
                text = word,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            )
        }
    }
}

@Composable
fun ValueTrailing(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ReassuranceRow(
    icon: ImageVector,
    text: String
) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
