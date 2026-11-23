package app.hisn.android

import app.hisn.android.nativeshell.UnrotAnswers
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.hisn.android.nativeshell.AppIcon
import app.hisn.android.nativeshell.HisnTheme
import app.hisn.android.nativeshell.LocalHisnStatusColors
import app.hisn.android.nativeshell.SystemBars
import app.hisn.android.nativeshell.appLabel
import app.hisn.android.nativeshell.storySerif
import app.hisn.android.nativeshell.unrotFrame
import app.hisn.android.nativeshell.unrotBackdrop
import app.hisn.android.nativeshell.Float3D
import app.hisn.android.nativeshell.popIn
import app.hisn.android.nativeshell.riseIn
import app.hisn.android.nativeshell.LocalReduceMotion
import app.hisn.android.nativeshell.systemReduceMotion
import app.hisn.android.nativeshell.rememberHisnHaptics
import app.hisn.core.ShortVideoSurfaces

/**
 * Unrot's block screen, shown after app protection has already left the feed, app or page.
 *
 * It answers the three things people need in that second, in this order: what was closed and
 * why ("Reels are closed"), what still works ("the rest of Instagram still works" / "opens again
 * at 07:00"), and the way out. The way out is the big button: in the one sec field study the clear
 * option to walk away did the most. Their own note sits in between, and "Breathe first" opens the
 * breathing pause for when the pull is strong. For a closed feed inside a bigger app there is a
 * way back to the rest of that app, so a blocked Reel never costs someone their messages.
 *
 * The Islamic edition keeps [RestrictionActivity] with its reminders.
 */
class BlockActivity : ComponentActivity() {
    private enum class Reason { FEED, WHOLE_FEED, LIMIT, QUIET, FOCUS, APP, PAGE_WORD, PAGE_VISUAL }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) { ShieldMoments.mark(this); StepIns.record(this, StepIns.CLOSED) }
        // Only an installed app is named: an unknown package would show as a raw ID.
        val raw = intent.getStringExtra("app")?.takeIf { it.isNotEmpty() && it != packageName }
        val app = raw?.takeIf { packageManager.getLaunchIntentForPackage(it) != null }
        val reason = when {
            intent.getBooleanExtra("shortvideo", false) ->
                if (raw != null && ShortVideoSurfaces.wholeApp(raw)) Reason.WHOLE_FEED else Reason.FEED
            intent.getBooleanExtra("focus", false) -> Reason.FOCUS
            intent.getBooleanExtra("schedule", false) -> Reason.QUIET
            intent.getBooleanExtra("limit", false) -> Reason.LIMIT
            intent.getBooleanExtra("visual", false) -> Reason.PAGE_VISUAL
            intent.getBooleanExtra("word", false) -> Reason.PAGE_WORD
            else -> Reason.APP
        }
        if (savedInstanceState == null && (reason == Reason.PAGE_VISUAL || reason == Reason.PAGE_WORD)) {
            StepIns.record(this, StepIns.ADULT)
            GlassMoments.cracked(this)
        }
        // A rotation is the same block: count it once, like every other pause screen.
        val tally = PauseCount.record(this, savedInstanceState == null).count
        val profile = PersonalStore(this).profile()
        val note = if (profile.hasReason()) PersonalStore.reasonLabel(this, profile) else ""
        val label = app?.let { appLabel(this, it) }
        val reopens = quietEnd()
        val theme = getSharedPreferences("hisn-native", MODE_PRIVATE).getString("ui.theme", "system")
        setContent {
            val dark = when (theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            HisnTheme(darkTheme = dark) {
              androidx.compose.runtime.CompositionLocalProvider(
                  LocalReduceMotion provides systemReduceMotion(this@BlockActivity)
              ) {
                SystemBars(dark)
                BackHandler(onBack = ::goHome)
                // A Surface sets the text colour for light and dark, not just the background.
                androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) {
                    BlockPage(reason, raw, app, label, note, tally, reopens)
                }
              }
            }
        }
    }

    /** When quiet hours end, as "07:00", from the saved schedule; null when unreadable. */
    private fun quietEnd(): String? = runCatching {
        val schedule = PolicyStore.get(this).snapshot().schedule
        if (!schedule.enabled) null
        else String.format(java.util.Locale.ROOT, "%02d:%02d", schedule.endMinute / 60, schedule.endMinute % 60)
    }.getOrNull()

    @androidx.compose.runtime.Composable
    private fun BlockPage(reason: Reason, feedApp: String?, app: String?, label: String?, note: String, tally: Int, reopens: String?) {
        val colors = MaterialTheme.colorScheme
        val haptics = rememberHisnHaptics()
        val title = when (reason) {
            Reason.FEED -> when (feedApp) {
                "com.google.android.youtube" -> stringResource(R.string.block_shorts_title)
                "com.instagram.android" -> stringResource(R.string.block_reels_title)
                "com.snapchat.android" -> stringResource(R.string.block_spotlight_title)
                else -> stringResource(R.string.block_feed_title)
            }
            Reason.WHOLE_FEED -> stringResource(R.string.block_whole_feed_title, label ?: "TikTok")
            Reason.LIMIT -> if (label != null) stringResource(R.string.block_limit_title, label)
                else stringResource(R.string.block_limit_title_generic)
            Reason.QUIET -> stringResource(R.string.block_quiet_title)
            Reason.FOCUS -> stringResource(R.string.block_focus_title)
            Reason.APP -> if (label != null) stringResource(R.string.block_app_title, label)
                else stringResource(R.string.block_app_title_generic)
            Reason.PAGE_WORD, Reason.PAGE_VISUAL -> stringResource(R.string.block_page_title)
        }
        val body = when (reason) {
            Reason.FEED -> if (label != null) stringResource(R.string.block_feed_rest, label)
                else stringResource(R.string.block_feed_rest_generic)
            Reason.WHOLE_FEED -> stringResource(R.string.block_whole_feed_body)
            Reason.LIMIT -> stringResource(R.string.block_limit_body)
            Reason.QUIET -> if (label != null && reopens != null) stringResource(R.string.block_quiet_body, label, reopens)
                else stringResource(R.string.block_quiet_body_generic)
            Reason.FOCUS -> {
                val ends = FocusSession.until(androidx.compose.ui.platform.LocalContext.current)
                if (ends > 0L) stringResource(R.string.block_focus_body, java.time.Instant.ofEpochMilli(ends)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
                else stringResource(R.string.block_focus_body_generic)
            }
            Reason.APP -> stringResource(R.string.block_app_body)
            Reason.PAGE_WORD -> stringResource(R.string.restriction_word)
            Reason.PAGE_VISUAL -> stringResource(R.string.restriction_visual)
        }
        Column(
            Modifier
                .fillMaxSize()
                .then(if (!Edition.islamic) Modifier.unrotBackdrop() else Modifier.background(colors.background))
                .safeDrawingPadding()
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(Modifier.height(20.dp))
                // Premium: the reason as a large 3D object, the app's own icon as a badge on it.
                val art = if (!Edition.islamic) when (reason) {
                    Reason.FEED, Reason.WHOLE_FEED -> R.drawable.unrot_3d_mobile
                    Reason.LIMIT -> R.drawable.unrot_3d_battery
                    Reason.QUIET -> R.drawable.unrot_3d_moon
                    Reason.FOCUS -> R.drawable.unrot_3d_target
                    Reason.APP -> R.drawable.unrot_3d_lock
                    Reason.PAGE_WORD, Reason.PAGE_VISUAL -> R.drawable.unrot_3d_shield
                } else null
                if (art != null) {
                    androidx.compose.foundation.layout.Box(Modifier.popIn().size(112.dp)) {
                        Float3D(art, 104.dp)
                        if (app != null) androidx.compose.foundation.layout.Box(Modifier.align(androidx.compose.ui.Alignment.BottomEnd)) {
                            AppIcon(app, size = 38.dp)
                        }
                    }
                } else if (app != null) androidx.compose.foundation.layout.Box(Modifier.popIn()) { AppIcon(app, size = 64.dp) }
                Text(
                    title,
                    style = MaterialTheme.typography.displaySmall.storySerif(),
                    modifier = Modifier.riseIn(1).semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    }
                )
                Text(body, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                if (note.isNotEmpty()) {
                    val shape = MaterialTheme.shapes.large
                    Column(
                        Modifier
                            .riseIn(2)
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .unrotFrame(shape)
                            .background(LocalHisnStatusColors.current.cardMuted, shape)
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(stringResource(R.string.block_your_note), style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant)
                        Text(stringResource(R.string.personal_quote, note),
                            style = MaterialTheme.typography.titleLarge.storySerif())
                    }
                }
                // The person's own answers from setup: how scrolling leaves them, and what they
                // said they would rather do. Only shown when they gave them.
                val context = androidx.compose.ui.platform.LocalContext.current
                val feelLine = androidx.compose.runtime.remember { UnrotAnswers.feelLine(context) }
                val instead = androidx.compose.runtime.remember {
                    UnrotAnswers.INSTEAD.filter { it in UnrotAnswers.instead(context) }.take(2)
                        .map { context.getString(UnrotAnswers.insteadAction(it)) }
                }
                if (feelLine != null) {
                    Text(feelLine, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                if (instead.isNotEmpty()) InsteadCard(instead)
                if (tally > 0) {
                    Text(
                        pluralStringResource(R.plurals.block_tally, tally, tally),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { haptics.confirm(); goHome() },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text(stringResource(R.string.block_back), style = MaterialTheme.typography.titleMedium) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            haptics.tap()
                            startActivity(PauseEntry.intent(this@BlockActivity).putExtra(BreatheActivity.EXTRA_QUICK, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            finish()
                        },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                    ) {
                        Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.block_breathe))
                    }
                    if (reason == Reason.FEED && app != null && label != null) {
                        TextButton(onClick = { haptics.tap(); openRest(app) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.block_open_rest, label), maxLines = 2)
                        }
                    }
                }
            }
        }
    }

    /**
     * What the person said they would rather do, as a card with one real step: a ten-minute timer
     * in the phone's own clock app, so the "instead" starts now rather than staying an intention.
     */
    @androidx.compose.runtime.Composable
    private fun InsteadCard(picks: List<String>) {
        var timerSet by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
        val timer = androidx.compose.runtime.remember { timerIntent(picks.first()) }
        val canTime = androidx.compose.runtime.remember { timer.resolveActivity(packageManager) != null }
        val shape = MaterialTheme.shapes.large
        val haptics = rememberHisnHaptics()
        Column(
            Modifier
                .riseIn(3)
                .fillMaxWidth()
                .unrotFrame(shape, hero = true)
                // Premium: black with gold in Ivory, a warm gold-tinted card at night.
                .background(when {
                    Edition.islamic -> androidx.compose.ui.graphics.Color(0xFF9EC39A)
                    app.hisn.android.nativeshell.LocalDarkTheme.current -> androidx.compose.ui.graphics.Color(0xFF2A2416)
                    else -> androidx.compose.ui.graphics.Color(0xFF1B1A16)
                }, shape)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val ink = androidx.compose.ui.graphics.Color(if (Edition.islamic) 0xFF1F2A23 else 0xFFF7F1E4)
            val gold = if (Edition.islamic) ink else androidx.compose.ui.graphics.Color(0xFFF2C94F)
            Text(stringResource(R.string.block_instead_label).uppercase(), style = MaterialTheme.typography.labelMedium, color = gold)
            Text(picks.first().replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.titleLarge.storySerif(), color = ink)
            if (picks.size > 1) {
                Text(stringResource(R.string.block_instead_or, picks[1]), style = MaterialTheme.typography.bodyMedium, color = ink)
            }
            if (canTime) {
                TextButton(
                    onClick = {
                        if (runCatching { startActivity(timer) }.isSuccess) { timerSet = true; haptics.confirm() }
                    },
                    enabled = !timerSet,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(
                        stringResource(if (timerSet) R.string.block_timer_set else R.string.block_timer),
                        style = MaterialTheme.typography.titleSmall,
                        color = gold,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
        }
    }

    private fun timerIntent(label: String): Intent =
        Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
            .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, 600)
            .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label.replaceFirstChar { it.titlecase() })
            .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)

    /** The app's own start screen: its home, never the feed that was just closed. */
    private fun openRest(app: String) {
        val launch = packageManager.getLaunchIntentForPackage(app)
        if (launch != null) runCatching { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
    }

    private fun goHome() {
        runCatching {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }
}
