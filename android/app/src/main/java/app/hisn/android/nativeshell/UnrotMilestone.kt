package app.hisn.android.nativeshell

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.hisn.android.R
import app.hisn.core.FeedWeek
import app.hisn.core.PersonalProfile
import app.hisn.core.SetupPlan
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The milestone moment, once per milestone, on Today. It celebrates with numbers the phone has
 * (pauses taken, feed time this week against last when that comparison is fair) and never with
 * invented ones. The share card carries only the day count: no apps, times or reasons, so sharing
 * it tells nobody what the person is working on.
 */

private const val PREFS = "milestone"
private const val CELEBRATED = "celebrated"

internal object MilestoneMemory {
    fun celebrated(context: Context): Int = try {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(CELEBRATED, 0)
    } catch (damaged: RuntimeException) { 0 }

    fun keep(context: Context, milestone: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(CELEBRATED, milestone).apply()
    }

    /** The milestone waiting to be celebrated, or 0. */
    fun due(context: Context, day: Int): Int {
        val last = PersonalProfile.lastMilestone(day)
        return if (last > 0 && last > celebrated(context)) last else 0
    }
}

@Composable
internal fun milestoneTitle(milestone: Int): String {
    val resources = LocalResources.current
    return when (milestone) {
        7 -> stringResource(R.string.milestone_7)
        14 -> stringResource(R.string.milestone_14)
        30 -> stringResource(R.string.milestone_30)
        60 -> stringResource(R.string.milestone_60)
        90 -> stringResource(R.string.milestone_90)
        180 -> stringResource(R.string.milestone_180)
        else -> resources.getQuantityString(R.plurals.milestone_years, milestone / 365, milestone / 365)
    }
}

@Composable
internal fun UnrotMilestoneCard(milestone: Int, pauses: Int, onKeep: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptics = rememberHisnHaptics()
    val change by produceState<Int?>(initialValue = null) {
        if (!Usage.hasAccess(context)) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val apps = installedApps(context).map { it.packageName }.filter { SetupPlan.hasFeed(it) }.toSet()
                FeedWeek(Usage.dailyForeground(context, apps, FeedWeek.DAYS * 2), 1f).changePercent()
            }.getOrNull()
        }
    }
    val pop = remember { Animatable(0.92f) }
    LaunchedEffect(Unit) {
        haptics.celebrate()
        pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 300f))
    }
    val title = milestoneTitle(milestone)
    val shape = MaterialTheme.shapes.extraLarge
    Column(
        Modifier
            .fillMaxWidth()
            .scale(pop.value)
            .unrotFrame(shape, hero = true)
            // Premium: a polished gold gradient with the medal as its hero object.
            .background(androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(androidx.compose.ui.graphics.Color(0xFFF9DE85), UnrotGoldCard, androidx.compose.ui.graphics.Color(0xFFE0A532))), shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$milestone", style = MaterialTheme.typography.displayLarge, color = UnrotInk)
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = UnrotInk,
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    }
                )
            }
            Float3D(R.drawable.unrot_3d_medal, 104.dp, glow = false)
        }
        val pauseText = resources.getQuantityString(R.plurals.milestone_pauses, pauses, pauses)
        val c = change
        Text(
            if (c != null && c <= -3) stringResource(R.string.milestone_body_less, pauseText, -c)
            else stringResource(R.string.milestone_body, pauseText),
            style = MaterialTheme.typography.bodyMedium,
            color = UnrotInk
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onKeep,
                colors = ButtonDefaults.buttonColors(containerColor = UnrotInk, contentColor = UnrotGoldCard)
            ) { Text(stringResource(R.string.milestone_keep)) }
            TextButton(onClick = { shareDayCard(context, milestone, title) }) {
                Text(stringResource(R.string.milestone_share), color = UnrotInk)
            }
        }
    }
}

private val UnrotGoldCard = androidx.compose.ui.graphics.Color(0xFFF4C34E)

/** A square card with the day count only, shared as a picture. */
internal fun shareDayCard(context: Context, milestone: Int, title: String) {
    runCatching {
        val size = 1080
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFF4C34E.toInt())
        val ink = 0xFF1F2A23.toInt()
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; textSize = 420f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; textSize = 64f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }
        canvas.drawText("$milestone", size / 2f, 600f, big)
        canvas.drawText(title, size / 2f, 760f, line)
        // The pause mark: two bars, the app's only sign.
        canvas.drawRoundRect(500f, 860f, 528f, 940f, 14f, 14f, mark)
        canvas.drawRoundRect(552f, 860f, 580f, 940f, 14f, 14f, mark)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "milestone.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".share", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
