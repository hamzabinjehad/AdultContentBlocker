package app.hisn.android.nativeshell

import androidx.compose.material.icons.rounded.Air

import androidx.compose.ui.unit.sp

import androidx.compose.ui.graphics.Brush

import androidx.compose.foundation.border

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.hisn.android.R

private class MoreTool(val icon: ImageVector, val label: Int, val tint: Color, val run: () -> Unit)

/**
 * The Glasshouse "More" sheet: every tool Unrot has, in three groups, so Today keeps four round
 * tools and nothing else is more than two taps away. Each tile opens a screen that already exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnrotMoreSheet(
    onDismiss: () -> Unit,
    onPause: () -> Unit,
    onFocus: () -> Unit,
    onOpen: (String) -> Unit
) {
    val gold = Color(0xFFF2C14E)
    val green = Color(0xFF8FE06A)
    val blue = Color(0xFF8DB8F2)
    val rose = Color(0xFFE58F7A)
    fun go(route: String): () -> Unit = { onDismiss(); onOpen(route) }
    val groups = listOf(
        R.string.more_group_now to listOf(
            MoreTool(Icons.Rounded.Air, R.string.today_tool_breathe, gold) { onDismiss(); onPause() },
            MoreTool(Icons.Rounded.CenterFocusStrong, R.string.today_tool_focus, gold) { onDismiss(); onFocus() },
            MoreTool(Icons.Rounded.EditNote, R.string.today_tool_note, gold, go("notes")),
        ),
        R.string.more_group_rules to listOf(
            MoreTool(Icons.Rounded.Apps, R.string.more_apps, green, go("apps")),
            MoreTool(Icons.Rounded.Bedtime, R.string.more_quiet, blue, go("schedule")),
            MoreTool(Icons.Rounded.Lock, R.string.today_tool_lock, rose, go("lock")),
            MoreTool(Icons.Rounded.ListAlt, R.string.more_lists, green, go("lists")),
            MoreTool(Icons.Rounded.Timer, R.string.more_limits, blue, go("limit")),
        ),
        R.string.more_group_you to listOf(
            MoreTool(Icons.Rounded.Insights, R.string.more_progress, gold, go("progress")),
            MoreTool(Icons.Rounded.Face, R.string.more_about_you, green, go("personal")),
            MoreTool(Icons.Rounded.HealthAndSafety, R.string.more_health, rose, go("diagnostics")),
            MoreTool(Icons.Rounded.HelpOutline, R.string.more_how, blue, go("info:how")),
        )
    )
    GlassSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.more_title), style = MaterialTheme.typography.headlineSmall.storySerif(),
                modifier = Modifier.semantics { heading() })
            groups.forEach { (title, tools) ->
                Text(stringResource(title).uppercase(appLocale()), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = Color(0xFFA99D86), modifier = Modifier.semantics { heading() })
                tools.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { t -> Tile(t, Modifier.weight(1f)) }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun Tile(t: MoreTool, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clickable(role = Role.Button, onClick = t.run)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // One quiet tile for every tool (mockup .toolgrid): warm charcoal, white mark.
        Box(Modifier.fillMaxWidth().height(56.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF3A3530), Color(0xFF1C1916))), shape)
            .border(1.dp, Color.White.copy(alpha = .06f), shape), contentAlignment = Alignment.Center) {
            Icon(t.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(stringResource(t.label), style = MaterialTheme.typography.labelSmall, color = Color(0xFFA99D86),
            textAlign = TextAlign.Center, maxLines = 2)
    }
}
