package app.hisn.android.nativeshell

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import app.hisn.android.FocusSession
import app.hisn.android.GlassMoments
import app.hisn.core.GlassMood

/**
 * Living colour (Unrot, dark theme): the app takes the colour of what the person is doing, so the
 * state of the day can be read before a word is. Green while the plant is rising, cool while a
 * Focus session runs, ember while the glass is cracked, indigo in quiet hours, grey after a slip,
 * and the lamp-lit gold once a crack is sealed. [HisnTheme] animates between them.
 *
 * Which state it is comes from [GlassMood.of]; this file only holds the palettes and reads the
 * three facts (the glass, Focus, quiet hours) the state is made from.
 */
internal object UnrotMood {
    /** Set by the Home hero, which already knows the quiet-hours rule and the minute. */
    val quietNow = mutableStateOf(false)

    private class Tones(
        val ground: Long, val bar: Long, val card: Long, val raised: Long, val track: Long,
        val ink: Long, val muted: Long, val gold: Long, val goldInk: Long
    )

    private fun tones(mood: GlassMood): Tones? = when (mood) {
        GlassMood.GROWING -> Tones(0xFF0E1710, 0xFF121D14, 0xFF172419, 0xFF203224, 0xFF2D4432, 0xFFF3F7EA, 0xFFA9BBA8, 0xFFE6CF58, 0xFF1D2204)
        GlassMood.WORKING -> Tones(0xFF0A1318, 0xFF0E191F, 0xFF122027, 0xFF1A2D36, 0xFF27424E, 0xFFEAF5F8, 0xFF9DB4BC, 0xFFF3DC96, 0xFF1C2226)
        GlassMood.HARD -> Tones(0xFF1A0F0A, 0xFF20130C, 0xFF2A1810, 0xFF382118, 0xFF4C2F20, 0xFFFDEFE2, 0xFFC4AC9A, 0xFFFF9F5A, 0xFF2C1002)
        GlassMood.NIGHT -> Tones(0xFF0D0F1C, 0xFF121526, 0xFF171A2E, 0xFF212540, 0xFF2C3154, 0xFFEEF0FF, 0xFFA7ACCE, 0xFFF2C96A, 0xFF2A1F00)
        GlassMood.SLIP -> Tones(0xFF131312, 0xFF181817, 0xFF1D1D1B, 0xFF282826, 0xFF363633, 0xFFECEAE5, 0xFFA9A69E, 0xFFCBBB92, 0xFF1F1B10)
        GlassMood.REST -> null // the base scheme is the lamp-lit room
    }

    /** [base] recoloured for [mood]; status and error colours stay as they are. */
    fun scheme(base: ColorScheme, mood: GlassMood): ColorScheme {
        val t = tones(mood) ?: return base
        val ground = Color(t.ground); val gold = Color(t.gold); val ink = Color(t.ink)
        return base.copy(
            primary = gold, onPrimary = Color(t.goldInk), surfaceTint = gold,
            primaryContainer = gold.copy(alpha = 0.26f).compositeOver(ground),
            onPrimaryContainer = gold.copy(alpha = 0.7f).compositeOver(ink),
            secondaryContainer = Color(t.raised), onSecondaryContainer = ink,
            background = ground, onBackground = ink, surface = ground, onSurface = ink, surfaceDim = ground,
            surfaceVariant = Color(t.track), onSurfaceVariant = Color(t.muted),
            surfaceContainerLowest = Color.Black.copy(alpha = 0.35f).compositeOver(ground),
            surfaceContainerLow = Color(t.bar), surfaceContainer = Color(t.card),
            surfaceContainerHigh = Color(t.raised),
            surfaceContainerHighest = Color(t.track).copy(alpha = 0.6f).compositeOver(Color(t.raised)),
            surfaceBright = Color(t.track),
            outline = Color(t.muted).copy(alpha = 0.7f).compositeOver(ground),
            outlineVariant = Color(t.track).copy(alpha = 0.7f).compositeOver(ground)
        )
    }

    fun card(mood: GlassMood): Color? = tones(mood)?.let { Color(it.card) }
    fun cardMuted(mood: GlassMood): Color? = tones(mood)?.let { Color(it.raised) }
}

/** The colour of the moment, re-read when the glass changes and every few seconds besides. */
@Composable
internal fun rememberUnrotMood(): GlassMood {
    val context = LocalContext.current.applicationContext
    val version = GlassUi.version.intValue
    val quiet = UnrotMood.quietNow.value
    fun read() = runCatching {
        GlassMood.of(GlassMoments.now(context).look, FocusSession.until(context) > System.currentTimeMillis(), quiet)
    }.getOrDefault(GlassMood.REST)
    var mood by remember { mutableStateOf(read()) }
    LaunchedEffect(version, quiet) {
        while (true) {
            mood = read()
            kotlinx.coroutines.delay(if (mood == GlassMood.HARD) 1_000 else 4_000)
        }
    }
    return mood
}
