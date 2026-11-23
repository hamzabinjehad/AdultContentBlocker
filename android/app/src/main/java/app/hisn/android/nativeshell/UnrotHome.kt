package app.hisn.android.nativeshell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisn.android.FocusSession
import app.hisn.android.GlassMoments
import app.hisn.android.R
import app.hisn.core.GlassState

/**
 * Shared, live state of the glass for every piece of the Glasshouse home: the hero, the
 * "Had a hard moment" button and the Breathe pill all read the same moment.
 */
internal object GlassUi {
    val version = mutableIntStateOf(0)
    /** The hero owns the "Had a hard moment" dialog; the button below it only asks for it. */
    val askHard = mutableStateOf(false)
    fun changed() { version.intValue++ }
}

/** The glass now, refreshed on return to the app, after any change, and each second while cracked. */
@Composable
internal fun rememberGlass(): GlassState {
    val context = LocalContext.current
    var resumes by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { resumes++; onPauseOrDispose { } }
    var glass by remember { mutableStateOf(GlassMoments.now(context)) }
    val version = GlassUi.version.intValue
    LaunchedEffect(resumes, version) {
        glass = GlassMoments.now(context)
        while (glass.look == GlassState.Look.CRACKED) {
            kotlinx.coroutines.delay(1_000)
            glass = GlassMoments.now(context)
        }
    }
    return glass
}

/**
 * The one line Home needs when something is open: what is not covered, why, and Fix. Amber,
 * outlined, never the whole card the old Today had.
 */
@Composable
internal fun UnrotHomeBanner(title: String, body: String, action: String, onAction: () -> Unit) {
    val amber = Color(0xFFFFB366)
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth()
            .background(Color(0x24FF963C), shape)
            .border(1.dp, Color(0x59FF963C), shape)
            .clickable(role = Role.Button, onClick = onAction)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() })
            Text(body, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFD9B0), maxLines = 3)
        }
        Text(
            action,
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF2A1606),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.background(amber, CircleShape).padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}

/**
 * Home's tools, as in the Glasshouse mockup (CleanPath's home row): Focus, My notes, Instead,
 * More as round buttons; then the daily check-in and "Had a hard moment". Breathe is the pill
 * above the tabs, so it is not repeated here.
 */
@Composable
internal fun UnrotHomeActions(state: HisnState, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHisnHaptics()
    val context = LocalContext.current
    var more by remember { mutableStateOf(false) }
    var instead by remember { mutableStateOf(false) }
    var checkIn by remember { mutableStateOf(false) }
    val focusLeft = focusLeftMs()
    if (more) UnrotMoreSheet(
        onDismiss = { more = false },
        onPause = state::openPause,
        onFocus = { FocusRequest.open.value = true },
        onOpen = onOpen
    )
    if (instead) UnrotInsteadSheet(state, onDismiss = { instead = false })
    if (checkIn) CheckInDialog(onDismiss = { checkIn = false })
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundTool(Icons.Rounded.CenterFocusStrong,
                if (focusLeft > 0L) stringResource(R.string.today_tool_focus_left, Wins.short(focusLeft)) else stringResource(R.string.today_tool_focus),
                lit = focusLeft > 0L) { haptics.nav(); FocusRequest.open.value = true }
            RoundTool(Icons.Rounded.EditNote, stringResource(R.string.today_tool_note)) { haptics.nav(); onOpen("notes") }
            RoundTool(Icons.AutoMirrored.Rounded.DirectionsWalk, stringResource(R.string.home_instead)) { haptics.nav(); instead = true }
            RoundTool(Icons.Rounded.MoreHoriz, stringResource(R.string.today_tool_more)) { haptics.nav(); more = true }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val today = remember(CheckIns.version.intValue) { CheckIns.today(context) }
            val label = stringResource(if (today != null) R.string.home_checked_in else R.string.three_checkin)
            Row(
                Modifier.height(58.dp).widthIn(min = 112.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF34302B), Color(0xFF1C1916))), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = .1f), CircleShape)
                    .clickable(role = Role.Button) { haptics.tap(); checkIn = true }
                    .semantics(mergeDescendants = true) { contentDescription = label }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                if (today == null) {
                    Icon(Icons.Rounded.SentimentSatisfied, contentDescription = null, tint = Color(0xFFF2C14E), modifier = Modifier.size(22.dp))
                    Text(stringResource(R.string.three_checkin), style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Text(listOf("😞", "😕", "😐", "🙂", "😄")[today.first.coerceIn(0, 4)], fontSize = 22.sp)
                    Text(stringResource(R.string.home_today), style = MaterialTheme.typography.titleSmall, color = Color(0xFFA99D86), fontWeight = FontWeight.Bold)
                }
            }
            Box(
                Modifier.weight(1f).height(58.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF34302B), Color(0xFF1C1916))), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = .1f), CircleShape)
                    .clickable(role = Role.Button) { haptics.tap(); GlassUi.askHard.value = true },
                contentAlignment = Alignment.Center
            ) {
                Text("↺  " + stringResource(R.string.hard_moment_button), style = MaterialTheme.typography.titleSmall,
                    color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RoundTool(icon: ImageVector, label: String, lit: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier.width(80.dp).clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(72.dp).pressScale(interaction)
                .background(Brush.verticalGradient(listOf(Color(0xFF2E2A26), Color(0xFF171513))), CircleShape)
                .border(1.dp, if (lit) Color(0xFFF2C14E) else Color.White.copy(alpha = .08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (lit) Color(0xFFF2C14E) else Color.White, modifier = Modifier.size(28.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFFCFC6B6), textAlign = TextAlign.Center, maxLines = 1)
    }
}

/**
 * "Instead": the person's own ideas from setup (or all of them), one tap each. A tap closes the
 * feed apps for ten minutes through Focus, so the idea has room; without app access it still
 * names the idea and says the feeds stay open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnrotInsteadSheet(state: HisnState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHisnHaptics()
    val picks = remember { UnrotAnswers.instead(context).let { mine -> UnrotAnswers.INSTEAD.filter { mine.isEmpty() || it in mine } } }
    val apps = remember { FeedHistories.feedApps(context, state) }
    var started by remember { mutableStateOf<String?>(null) }
    GlassSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp).padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val chosen = started
            if (chosen != null) {
                Text(stringResource(R.string.instead_go, chosen), style = MaterialTheme.typography.headlineSmall.storySerif(),
                    modifier = Modifier.semantics { heading() })
                Text(
                    stringResource(if (state.apps.on && apps.isNotEmpty()) R.string.instead_go_closed else R.string.instead_go_open),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(stringResource(R.string.instead_go_done)) }
                return@Column
            }
            Text(stringResource(R.string.instead_sheet_title), style = MaterialTheme.typography.headlineSmall.storySerif(),
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.instead_sheet_body), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            picks.forEach { key ->
                val action = stringResource(UnrotAnswers.insteadAction(key))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp)
                        .background(Color(0xFF1A1915), RoundedCornerShape(18.dp))
                        .clickable(role = Role.Button) {
                            haptics.confirm()
                            if (state.apps.on && apps.isNotEmpty() && FocusSession.until(context) <= System.currentTimeMillis()) {
                                FocusSession.start(context, 10, apps.toSet())
                            }
                            started = action
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(36.dp).background(Color.White.copy(alpha = .06f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(insteadIcon(key), contentDescription = null, tint = Color(0xFFFFDF8A), modifier = Modifier.size(20.dp))
                    }
                    Text(action.replaceFirstChar { it.titlecase(appLocale()) }, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.instead_ten), style = MaterialTheme.typography.labelLarge, color = Color(0xFFFFDF8A),
                        modifier = Modifier.background(Color(0x29F2C14E), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }
    }
}

private fun insteadIcon(key: String): ImageVector = when (key) {
    "sleep" -> Icons.Rounded.Bedtime
    "read" -> Icons.AutoMirrored.Rounded.MenuBook
    "train" -> Icons.Rounded.FitnessCenter
    "friends" -> Icons.Rounded.Groups
    "study" -> Icons.Rounded.School
    "make" -> Icons.Rounded.Brush
    "outside" -> Icons.Rounded.Park
    else -> Icons.Rounded.Home
}

/** The Glasshouse bottom sheet: dim page behind, warm dark sheet, a quiet handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlassSheet(onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = Color.Black.copy(alpha = .6f),
        containerColor = Color(0xFF15120E),
        dragHandle = {
            Box(Modifier.padding(vertical = 12.dp).width(36.dp).height(4.dp).background(Color.White.copy(alpha = .24f), CircleShape))
        },
        content = content
    )
}
