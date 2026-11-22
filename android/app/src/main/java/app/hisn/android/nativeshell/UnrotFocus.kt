package app.hisn.android.nativeshell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.hisn.android.FocusSession
import app.hisn.android.R
import kotlinx.coroutines.delay

/** The focus lengths offered, in minutes: a short sprint, a work block, a long stretch. */
private val FocusMinutes = intArrayOf(25, 50, 90)

/** Minutes left in a running focus, refreshed every few seconds; 0 when none runs. */
@Composable
internal fun focusLeftMs(): Long {
    val context = LocalContext.current
    val left by produceState(0L) {
        while (true) {
            val until = FocusSession.until(context)
            value = if (until > 0L) until - System.currentTimeMillis() else 0L
            delay(5_000L)
        }
    }
    return left
}

/**
 * "Focus now": the feed apps close for a chosen time, in one tap, with nothing to set up. A
 * running focus shows when it ends and can be ended early; it is the person's own choice, so
 * nothing locks it. Without app access it says what is missing instead of starting a session
 * that would not hold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnrotFocusSheet(state: HisnState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHisnHaptics()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val apps = remember { FeedHistories.feedApps(context, state).sorted() }
    var minutes by rememberSaveable { mutableIntStateOf(FocusMinutes[0]) }
    val left = focusLeftMs()
    val gold = if (LocalDarkTheme.current) Color(0xFFF2C94F) else Color(0xFFB07A12)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = LocalHisnStatusColors.current.card) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Float3D(app.hisn.android.R.drawable.unrot_3d_target, 88.dp)
            if (left > 0L) {
                Text(stringResource(R.string.focus_running_title), style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.focus_running_body, Wins.short(left)), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                OutlinedButton(onClick = { haptics.tap(); FocusSession.stop(context); onDismiss() },
                    modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.focus_end)) }
                return@Column
            }
            Text(stringResource(R.string.focus_title), style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.focus_body), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusMinutes.forEach { m ->
                    val picked = m == minutes
                    val shape = RoundedCornerShape(18.dp)
                    Box(
                        Modifier.weight(1f).height(56.dp)
                            .background(if (picked) chosenFill() else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), shape)
                            .then(if (picked) Modifier.border(1.5.dp, gold, shape) else Modifier)
                            .clickable(role = Role.RadioButton) { haptics.tap(); minutes = m }
                            .semantics { selected = picked },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.focus_minutes, m), style = MaterialTheme.typography.titleMedium,
                            color = if (picked) gold else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            if (apps.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp), verticalAlignment = Alignment.CenterVertically) {
                    apps.take(6).forEach { AppIcon(it, size = 32.dp) }
                }
                Text(pluralStringResource(R.plurals.focus_apps, apps.size, apps.size), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                !state.apps.on -> {
                    Text(stringResource(R.string.focus_needs_access), style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center)
                    Button(onClick = { haptics.tap(); state.openPermission("accessibility") },
                        modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.focus_turn_on)) }
                }
                apps.isEmpty() -> Text(stringResource(R.string.focus_no_apps), style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center)
                else -> Button(
                    onClick = {
                        haptics.confirm()
                        if (FocusSession.start(context, minutes, apps.toSet())) onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text(stringResource(R.string.focus_start, minutes)) }
            }
        }
    }
}


/** Set by the launcher's "Focus" shortcut: Today opens the focus sheet once it is on screen. */
object FocusRequest {
    val open = androidx.compose.runtime.mutableStateOf(false)
}
