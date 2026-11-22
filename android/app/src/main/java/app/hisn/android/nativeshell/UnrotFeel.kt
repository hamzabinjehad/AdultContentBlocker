package app.hisn.android.nativeshell

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.view.HapticFeedbackConstants
import android.view.View
import app.hisn.android.R

/**
 * Unrot's sounds and the switches for sound and vibration.
 *
 * The sounds are tiny tones made for Unrot (soft taps, a two-note switch, a small chime). They are
 * played as "sonification", like the keyboard clicks, so they follow the system volume. They stay
 * quiet whenever the phone is on silent or vibrate, and when the person turns them off in Settings.
 * The Islamic edition plays none, so it stays as it was.
 */
enum class FeelSound(val res: Int, val volume: Float) {
    TAP(R.raw.unrot_tap, 0.35f),
    TICK(R.raw.unrot_tick, 0.25f),
    ON(R.raw.unrot_on, 0.45f),
    OFF(R.raw.unrot_off, 0.4f),
    CONFIRM(R.raw.unrot_confirm, 0.5f),
    REJECT(R.raw.unrot_reject, 0.45f),
    CELEBRATE(R.raw.unrot_celebrate, 0.55f),
    BLOCK(R.raw.unrot_block, 0.45f),
    BREATH(R.raw.unrot_breath, 0.35f)
}

object UnrotFeel {
    private const val PREFS = "hisn-native"
    const val SOUNDS_KEY = "ui.sounds"
    const val VIBRATION_KEY = "ui.vibration"

    private var pool: SoundPool? = null
    private val loaded = HashMap<FeelSound, Int>()
    private val ready = HashSet<Int>()
    /** A sound asked for before it finished loading (the block screen's first moment), with when. */
    private var waiting: Pair<FeelSound, Long>? = null

    fun soundsOn(context: Context): Boolean = unrotEdition &&
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SOUNDS_KEY, true)

    /** Vibration is on by default; Android's own touch-vibration setting still applies on top. */
    fun vibrationOn(context: Context): Boolean = !unrotEdition ||
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(VIBRATION_KEY, true)

    fun setSounds(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(SOUNDS_KEY, on).apply()
        if (on) warm(context)
    }

    fun setVibration(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(VIBRATION_KEY, on).apply()

    /** Loads every sound once, so the first tap is not silent. */
    @Synchronized
    fun warm(context: Context) {
        if (!soundsOn(context) || pool != null) return
        val app = context.applicationContext
        val created = runCatching {
            SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                ).build()
        }.getOrNull() ?: return
        created.setOnLoadCompleteListener { p, id, status ->
            if (status != 0) return@setOnLoadCompleteListener
            synchronized(this) {
                ready += id
                val (sound, at) = waiting ?: return@synchronized
                if (loaded[sound] == id) {
                    waiting = null
                    if (System.currentTimeMillis() - at < 1_500L) p.play(id, sound.volume, sound.volume, 1, 0, 1f)
                }
            }
        }
        FeelSound.entries.forEach { sound -> runCatching { loaded[sound] = created.load(app, sound.res, 1) } }
        pool = created
    }

    fun play(context: Context, sound: FeelSound) {
        if (!soundsOn(context)) return
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        warm(context)
        val id = loaded[sound] ?: return
        synchronized(this) {
            if (id !in ready) { waiting = sound to System.currentTimeMillis(); return }
        }
        runCatching { pool?.play(id, sound.volume, sound.volume, 1, 0, 1f) }
    }

    /**
     * A vibration with no Compose around it (the block screen, the wait screen). It goes through the
     * view, so it needs no vibrate permission and follows the system's touch-vibration setting.
     */
    fun buzz(view: View, kind: Int = HapticFeedbackConstants.CONTEXT_CLICK) {
        if (!vibrationOn(view.context)) return
        view.performHapticFeedback(kind)
    }

    /**
     * One beat of a building hold: light at the start, firmer as [strength] nears 1, so a three-second
     * press feels like it is gathering. Through the view, so no vibrate permission is needed.
     */
    fun pulse(view: View, strength: Float) {
        if (!vibrationOn(view.context)) return
        view.performHapticFeedback(when {
            strength < 0.34f -> HapticFeedbackConstants.CLOCK_TICK
            strength < 0.67f -> HapticFeedbackConstants.CONTEXT_CLICK
            else -> HapticFeedbackConstants.VIRTUAL_KEY
        })
    }

    /** A short three-beat pattern for a reached milestone or a finished pause. */
    fun celebrate(view: View) {
        if (!vibrationOn(view.context)) return
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        view.postDelayed({ view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }, 110)
        view.postDelayed({ view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }, 240)
    }
}
