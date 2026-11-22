package app.hisn.android.nativeshell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * One choice out of a few, as Pixel's own apps draw it (Clock, Settings, Recorder): one connected
 * segmented row, round at both ends, the chosen segment filled with a check. It replaces loose
 * filter chips, which read as "filters" and left a ragged right edge.
 *
 * TalkBack hears a radio group: one item is "selected", the others are not.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun <T> ConnectedChoice(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = rememberHisnHaptics()
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = {
                    if (option != selected) {
                        haptics.tick()
                        onSelect(option)
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp),
                label = { Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

/** How loud an [AttentionCard] is: something is off and needs a tap, or just worth knowing. */
enum class Attention { Problem, Note }

/**
 * Pixel Settings' "needs attention" card: a tonal container, an icon in the container's own
 * accent, one sentence, and the fix as a real button rather than an indented text link. The
 * colour never carries the meaning alone; the icon and the words do too.
 */
@Composable
fun AttentionCard(
    message: String,
    modifier: Modifier = Modifier,
    kind: Attention = Attention.Problem,
    icon: ImageVector = if (kind == Attention.Problem) Icons.Rounded.ErrorOutline else Icons.Rounded.Info,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    val status = LocalHisnStatusColors.current
    val scheme = MaterialTheme.colorScheme
    val container: Color
    val content: Color
    val accent: Color
    when (kind) {
        Attention.Problem -> {
            container = status.warningContainer
            content = status.onWarningContainer
            accent = status.warning
        }
        Attention.Note -> {
            container = scheme.secondaryContainer
            content = scheme.onSecondaryContainer
            accent = scheme.secondary
        }
    }
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (action != null) 12.dp else 16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            if (action != null && onAction != null) {
                Spacer(Modifier.padding(top = 8.dp))
                FilledTonalButton(
                    onClick = onAction,
                    modifier = Modifier
                        .padding(start = 36.dp)
                        .heightIn(min = 48.dp),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = accent,
                        contentColor = container
                    )
                ) { Text(action) }
            }
        }
    }
}

/**
 * Pixel's switch: the thumb carries a check when on, so "on" never rests on colour alone, which
 * matters in the grey "Screen in grey" mode and for colour-blind readers.
 */
@Composable
fun HisnSwitch(checked: Boolean, enabled: Boolean = true) {
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = null,
        enabled = enabled,
        thumbContent = if (checked) {
            {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(androidx.compose.material3.SwitchDefaults.IconSize)
                )
            }
        } else null
    )
}
