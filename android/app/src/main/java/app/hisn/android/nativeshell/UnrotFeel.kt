package app.hisn.android.nativeshell

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Unrot's vibration: the on/off switch and the few patterns the app uses.
 *
 * The app plays no sounds. Every vibration goes through the view, so it needs no vibrate permission
 * and follows the system's touch-vibration setting. The newer constants (Android 11 and 14) are used
 * where the phone has them, with a light click on older phones, so nothing falls back to the heavy
 * long-press buzz by accident.
 */
object UnrotFeel {
    private const val PREFS = "hisn-native"
    const val VIBRATION_KEY = "ui.vibration"

    /** Vibration is on by default; Android's own touch-vibration setting still applies on top. */
    fun vibrationOn(context: Context): Boolean = !unrotEdition ||
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(VIBRATION_KEY, true)

    fun setVibration(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(VIBRATION_KEY, on).apply()

    /** The lightest tick: a picker step, a breath step. */
    val TICK: Int get() =
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK

    /** Something finished well. */
    val CONFIRM: Int get() =
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK

    /** Something was refused. */
    val REJECT: Int get() =
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.CONTEXT_CLICK

    fun toggle(on: Boolean): Int = when {
        Build.VERSION.SDK_INT >= 34 -> if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
        else -> HapticFeedbackConstants.CONTEXT_CLICK
    }

    /** A drag or swipe crossed the point where it counts. */
    val EDGE: Int get() =
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
        else HapticFeedbackConstants.CONTEXT_CLICK

    fun buzz(view: View, kind: Int = HapticFeedbackConstants.CONTEXT_CLICK) {
        if (!vibrationOn(view.context)) return
        view.performHapticFeedback(kind)
    }

    /** One beat of a building hold: light ticks first, firmer clicks as [strength] nears 1. */
    fun pulse(view: View, strength: Float) =
        buzz(view, if (strength < 0.5f) TICK else HapticFeedbackConstants.CONTEXT_CLICK)

    /** A reached milestone or a finished pause: a confirm, then one light tick. */
    fun celebrate(view: View) {
        if (!vibrationOn(view.context)) return
        view.performHapticFeedback(CONFIRM)
        view.postDelayed({ view.performHapticFeedback(TICK) }, 140)
    }
}
