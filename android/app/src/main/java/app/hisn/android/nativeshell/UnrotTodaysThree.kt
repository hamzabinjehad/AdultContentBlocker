package app.hisn.android.nativeshell

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisn.android.R
import app.hisn.android.StepIns
import java.time.LocalDate

/**
 * One tap a day: how today feels and what pulls most. Kept for 60 days on this phone only, as a
 * day and two small numbers, so Progress can colour a day and Today can tick "Check in".
 */
internal object CheckIns {
    private const val PREFS = "check_ins"
    private const val KEEP_DAYS = 60
    /** Bumped on each save so every screen showing today's check-in updates at once. */
    val version = androidx.compose.runtime.mutableIntStateOf(0)

    fun today(context: Context): Pair<Int, Int>? = runCatching {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = LocalDate.now().toEpochDay().toString()
        if (!p.contains("$key:mood")) null else p.getInt("$key:mood", 2) to p.getInt("$key:pull", -1)
    }.getOrNull()

    fun save(context: Context, mood: Int, pull: Int) {
        runCatching {
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val today = LocalDate.now().toEpochDay()
            val edit = p.edit().putInt("$today:mood", mood).putInt("$today:pull", pull)
            p.all.keys.forEach { k -> val d = k.substringBefore(':').toLongOrNull(); if (d == null || d < today - KEEP_DAYS) edit.remove(k) }
            edit.apply()
        }
        version.intValue++
    }
}

private val moodFaces = listOf("😞", "😕", "😐", "🙂", "😄")
private val moodNames = intArrayOf(R.string.checkin_mood_0, R.string.checkin_mood_1, R.string.checkin_mood_2, R.string.checkin_mood_3, R.string.checkin_mood_4)
private val pullNames = intArrayOf(R.string.checkin_pull_bored, R.string.checkin_pull_tired, R.string.checkin_pull_stress, R.string.checkin_pull_alone, R.string.checkin_pull_none)

/**
 * Today's three, from the Glasshouse mockup (CleanPath's Today's Mission, made honest): check in,
 * take one pause, stay under your own before. Each tick comes from something the phone records.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UnrotTodaysThree(state: HisnState, wins: WinsSummary?, resumes: Int, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val checkedIn = remember(resumes, CheckIns.version.intValue) { CheckIns.today(context) != null }
    var asking by remember { mutableStateOf(false) }
    val paused = remember(resumes) { (StepIns.day(context, LocalDate.now())[StepIns.BREATHED] ?: 0) > 0 }
    val feedToday by produceState<Long?>(null, resumes) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val apps = FeedHistories.feedApps(context, state)
                Usage.usedTodayAll(context).filterKeys { it in apps }.values.sum()
            }.getOrNull()
        }
    }
    val before = wins?.base?.feedMs ?: 0L
    val under = before > 0L && feedToday != null && feedToday!! < before
    val done = listOf(checkedIn, paused, under).count { it }
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = .08f), shape)
            .background(LocalHisnStatusColors.current.card, shape).padding(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.three_title), style = MaterialTheme.typography.titleLarge.storySerif(), modifier = Modifier.weight(1f).semantics { heading() })
            Text("$done/3", style = MaterialTheme.typography.titleSmall, color = Color(0xFFF2C14E))
        }
        Spacer(Modifier.height(8.dp))
        ThreeRow("☺", checkedIn, stringResource(R.string.three_checkin), stringResource(R.string.three_checkin_body),
            action = if (checkedIn) null else stringResource(R.string.three_start), last = false) { asking = true }
        ThreeRow("◎", paused, stringResource(R.string.three_pause), stringResource(R.string.three_pause_body),
            action = if (paused) null else stringResource(R.string.three_start), last = false) { state.openPause() }
        val underBody = when {
            before <= 0L -> stringResource(R.string.three_under_body_unknown)
            feedToday == null -> stringResource(R.string.three_under_body_unknown)
            under -> stringResource(R.string.three_under_body_left, Wins.short(before - feedToday!!), Wins.short(before))
            else -> stringResource(R.string.three_under_body_over, Wins.short(before))
        }
        ThreeRow("⏳", under, stringResource(R.string.three_under), underBody,
            action = stringResource(R.string.three_see), last = true) { onOpen("progress") }
    }
    if (asking) CheckInDialog(onDismiss = { asking = false })
}

@Composable
private fun ThreeRow(icon: String, done: Boolean, title: String, body: String, action: String?, last: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(32.dp).background(if (done) Color(0xFFF2C14E) else Color.White.copy(alpha = .08f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(if (done) "✓" else icon, color = if (done) Color(0xFF2A1C02) else Color(0xFFFFDF8A), fontWeight = FontWeight.Bold) }
            if (!last) Box(Modifier.padding(top = 4.dp).width(2.dp).height(22.dp).background(Color.White.copy(alpha = .1f)))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall,
                color = if (done) Color(0xFFA99D86) else MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFF2C14E),
                modifier = Modifier.padding(start = 8.dp)
                    .background(Color(0x22F2C14E), CircleShape)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

/** The daily check-in: how today feels and what pulls most. Saved on this phone only. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CheckInDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
        var mood by remember { mutableIntStateOf(2) }
        var pull by remember { mutableIntStateOf(-1) }
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { CheckIns.save(context, mood, pull); onDismiss() }) {
                    Text(stringResource(R.string.checkin_done))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.hard_moment_not_now)) } },
            title = { Text(stringResource(R.string.checkin_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        moodFaces.forEachIndexed { i, face ->
                            val label = stringResource(moodNames[i])
                            Column(
                                Modifier.clickable(role = Role.RadioButton, onClickLabel = label) { mood = i },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    Modifier.size(44.dp)
                                        .background(if (mood == i) Color(0x33F2C14E) else Color.White.copy(alpha = .06f), CircleShape)
                                        .border(1.dp, if (mood == i) Color(0xFFF2C14E) else Color.Transparent, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) { Text(face, fontSize = 22.sp) }
                                Text(label, style = MaterialTheme.typography.labelSmall,
                                    color = if (mood == i) Color(0xFFF2C14E) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text(stringResource(R.string.checkin_pull_title), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pullNames.forEachIndexed { i, res ->
                            Text(
                                stringResource(res),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (pull == i) Color(0xFF2A1C02) else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .background(if (pull == i) Color(0xFFF2C14E) else Color.White.copy(alpha = .08f), CircleShape)
                                    .clickable(role = Role.RadioButton) { pull = i }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                    Text(stringResource(R.string.checkin_private), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
}
