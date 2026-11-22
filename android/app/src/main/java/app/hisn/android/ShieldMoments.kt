package app.hisn.android

import android.content.Context

/**
 * When a block or wait screen last appeared, so onboarding's "first real test" can say "It
 * worked" only when it really did. Only a time is kept, never the app.
 */
object ShieldMoments {
    private const val PREFS = "shield_moments"
    private const val LAST = "last"

    fun mark(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(LAST, System.currentTimeMillis()).apply()
        }
    }

    fun last(context: Context): Long = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(LAST, 0L)
    }.getOrDefault(0L)
}
