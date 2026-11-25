package app.hisn.android.nativeshell

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.hisn.android.GlassMoments
import app.hisn.android.R
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val YouGold = Color(0xFFF2C14E)
private val YouGoldLight = Color(0xFFFFDF8A)
private val YouMuted = Color(0xFFA99D86)

/**
 * The top of Unrot's You tab, as the Glasshouse prototype draws it (Opal's profile, Forest's
 * share card): the sprout as an avatar, the person's name and day, their reason in their words,
 * a small record of what they have grown, and "Share your sprout". The record and the card hold
 * time given back, trees and gold seams only: nothing about adult content, ever.
 */
@Composable
internal fun UnrotYouHero(state: HisnState, onEdit: () -> Unit) {
    val context = LocalContext.current
    var resumes by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { resumes++; onPauseOrDispose { } }
    val wins by produceState<WinsSummary?>(null, resumes, state.personal.day) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { Wins.read(context, state) }.getOrNull()
        }
    }
    val summary = state.personal
    val givenBack = wins?.givenBackMs ?: 0L
    val stage = glassStage(givenBack)
    val trees = remember(wins) { wins?.let { woodTrees(it, GlassMoments.brokenDay(context)).size } ?: 0 }
    val seams = remember(resumes) { GlassMoments.seams(context) }
    val locale = appLocale()
    val since = remember(summary.day, locale) {
        LocalDate.now().minusDays((summary.day - 1).coerceAtLeast(0).toLong()).format(DateTimeFormatter.ofPattern("d MMM", locale))
    }
    val stageName = stringResource(glassStageName(stage))
    val cardLine = stringResource(R.string.share_sprout_line)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // The sprout as the avatar: the empty glass painting with the live plant inside.
            Box(
                Modifier.size(72.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF3A2A12), Color(0xFF120C06))))
                    .border(1.dp, Color.White.copy(alpha = .10f), CircleShape)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center
            ) {
                Image(painterResource(if (stage >= 6) R.drawable.unrot_glass_tree else R.drawable.unrot_glass_seed),
                    contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(96.dp))
                if (stage < 6) GlassPlant(givenBack, Modifier.size(96.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(summary.name.ifBlank { stringResource(R.string.you_title_default) },
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
                if (summary.day > 0) Text(stringResource(R.string.you_day_since, summary.day, since),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Their reason, in their words; tapping it edits it.
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF2A2620), Color(0xFF171512))), shape)
                .border(1.dp, Color.White.copy(alpha = .07f), shape)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.you_edit), onClick = onEdit)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(stringResource(R.string.you_reason_label).uppercase(locale), style = MaterialTheme.typography.labelSmall, color = YouMuted)
            Text(
                if (summary.reason.isNotEmpty()) stringResource(R.string.personal_quote, summary.reason) else stringResource(R.string.you_no_note),
                style = if (summary.reason.isNotEmpty()) MaterialTheme.typography.titleLarge.storySerif() else MaterialTheme.typography.bodyMedium,
                color = if (summary.reason.isNotEmpty()) Color.White else YouMuted
            )
        }
        // The record: what they have grown, never what they were shielded from.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecordTile(Wins.short(givenBack), stringResource(R.string.you_record_given), Modifier.weight(1f))
            RecordTile(trees.toString(), androidx.compose.ui.res.pluralStringResource(R.plurals.you_record_trees, trees), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecordTile(seams.toString(), androidx.compose.ui.res.pluralStringResource(R.plurals.you_record_seams, seams), Modifier.weight(1f))
            RecordTile(stageName, stringResource(R.string.you_record_stage), Modifier.weight(1f))
        }
        // Share your sprout: gold, like the prototype's button.
        val haptics = rememberHisnHaptics()
        val gshape = RoundedCornerShape(20.dp)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFFFFD76A), Color(0xFFD99A1E))), gshape)
                .clickable(role = Role.Button) {
                    haptics.tap()
                    shareSproutCard(context, stage, stageName, Wins.short(givenBack), cardLine)
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.IosShare, contentDescription = null, tint = Color(0xFF2A1C02))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.share_sprout), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color(0xFF2A1C02))
                Text(stringResource(R.string.share_sprout_sub), style = MaterialTheme.typography.bodySmall, color = Color(0xFF2A1C02).copy(alpha = .8f))
            }
        }
    }
}

@Composable
private fun RecordTile(value: String, label: String, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.background(Color(0xFF151412), shape).border(1.dp, Color.White.copy(alpha = .05f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .clearAndSetSemantics { contentDescription = "$value, $label" },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = YouGoldLight, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = YouMuted, maxLines = 2)
    }
}

/** The card shared as a picture: the sprout, its stage and the time given back. Nothing else. */
internal fun shareSproutCard(context: Context, stage: Int, stageName: String, givenBack: String, line: String) {
    runCatching {
        val w = 1080; val h = 1350
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = Paint().apply {
            shader = RadialGradient(w / 2f, h * .38f, h * .75f, intArrayOf(0xFF3A2810.toInt(), 0xFF0B0805.toInt()), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        ContextCompat.getDrawable(context, glassArt(stage))?.let { art ->
            art.setBounds(140, 90, w - 140, 90 + (w - 280))
            art.draw(canvas)
        }
        fun text(size: Float, color: Int, bold: Boolean, spacing: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size; textAlign = Paint.Align.CENTER; letterSpacing = spacing
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        canvas.drawText("UNROT", w / 2f, 1000f, text(34f, 0xFFF2C14E.toInt(), true, .3f))
        canvas.drawText(stageName, w / 2f, 1070f, text(54f, 0xFFFFFFFF.toInt(), true))
        val big = text(120f, 0xFFFFDF8A.toInt(), true).apply {
            shader = LinearGradient(0f, 1100f, 0f, 1230f, 0xFFFFE6A0.toInt(), 0xFFE0A93A.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawText(givenBack, w / 2f, 1205f, big)
        canvas.drawText(line, w / 2f, 1275f, text(40f, 0xFFA99D86.toInt(), false))
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "sprout.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".share", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
