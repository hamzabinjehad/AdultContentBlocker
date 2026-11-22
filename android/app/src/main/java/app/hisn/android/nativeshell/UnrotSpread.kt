package app.hisn.android.nativeshell

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.hisn.android.R

/*
 * Asking for a rating and for word of mouth, at the right moment only: after a good week (less
 * feed time than "before" and at least an hour given back), never on a heavy day, never during
 * setup, once at a time and at most every 30 days after "Not now". The shared text names the app
 * and the link, never the person's numbers, and never adult blocking.
 */
internal object Spread {
    private const val PREFS = "unrot_spread"
    private const val SNOOZE_DAYS = 30L
    /** Set by UI tests so screens depend only on their fake state. */
    @Volatile var disabled = false

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun today() = java.time.LocalDate.now().toEpochDay()

    fun storeLink(context: Context) = "https://play.google.com/store/apps/details?id=" + context.packageName

    fun shouldAsk(context: Context, w: WinsSummary): Boolean {
        if (disabled || w.heavier || w.day < 7 || w.givenBackMs < 3_600_000L) return false
        val p = prefs(context)
        if (p.getBoolean("done", false)) return false
        return today() >= p.getLong("snoozeUntil", 0L)
    }

    fun snooze(context: Context) = prefs(context).edit().putLong("snoozeUntil", today() + SNOOZE_DAYS).apply()
    private fun done(context: Context) = prefs(context).edit().putBoolean("done", true).apply()

    /** Opens the store page in the Play app, or in the browser without it. */
    fun rate(context: Context) {
        done(context)
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(market) }.onFailure {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(storeLink(context))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    /** The share sheet with a short line and the link; no numbers about the person. */
    fun tellAFriend(context: Context) {
        done(context)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.spread_share_text, storeLink(context)))
        runCatching {
            context.startActivity(Intent.createChooser(send, context.getString(R.string.spread_tell)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** "Is Unrot helping?" after a good week: rate it, tell a friend, or not now. */
@Composable
internal fun UnrotSpreadCard(w: WinsSummary) {
    val context = LocalContext.current
    var shown by remember(w) { mutableStateOf(Spread.shouldAsk(context, w)) }
    if (!shown) return
    val haptics = rememberHisnHaptics()
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier.fillMaxWidth().unrotFrame(shape).background(LocalHisnStatusColors.current.card, shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon3D(R.drawable.unrot_3d_heart, Color3DTile, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.spread_title), style = MaterialTheme.typography.titleMedium.storySerif(),
                    modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.spread_body, Usage.formatDuration(w.givenBackMs)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { haptics.confirm(); Spread.rate(context); shown = false }) { Text(stringResource(R.string.spread_rate)) }
            OutlinedButton(onClick = { haptics.tap(); Spread.tellAFriend(context); shown = false }) { Text(stringResource(R.string.spread_tell)) }
        }
        TextButton(onClick = { haptics.tap(); Spread.snooze(context); shown = false },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text(stringResource(R.string.spread_later))
        }
    }
}

private val Color3DTile = androidx.compose.ui.graphics.Color(0xFFFBE3DD)
