package app.hisn.android

import android.content.Context
import app.hisn.core.GlassState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The few moments the glass on Unrot's Today reacts to (rules in [GlassState]): the last adult
 * page Unrot closed, the last crack sealed with a breath, the count of gold seams, and the last
 * time the person said adult content got through. Only times are kept, never what was closed.
 */
object GlassMoments {
    private const val PREFS = "glass_moments"
    private const val CRACKED = "cracked_at"
    private const val SEALED = "sealed_at"
    private const val SEAMS = "gold_seams"
    private const val BROKEN = "broken_at"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** An adult page was closed: the glass cracks. */
    @JvmStatic
    fun cracked(context: Context) {
        runCatching { prefs(context).edit().putLong(CRACKED, System.currentTimeMillis()).apply() }
    }

    /** A breathing pause: inside the window it seals the crack with gold. */
    @JvmStatic
    fun breathed(context: Context) {
        runCatching {
            val p = prefs(context)
            val now = System.currentTimeMillis()
            if (GlassState.breathSeals(now, p.getLong(CRACKED, 0L), p.getLong(SEALED, 0L))) {
                p.edit().putLong(SEALED, now).putInt(SEAMS, p.getInt(SEAMS, 0) + 1).apply()
            }
        }
    }

    /** The person's own report that adult content got through: the glass breaks. */
    @JvmStatic
    fun broke(context: Context) {
        runCatching { prefs(context).edit().putLong(BROKEN, System.currentTimeMillis()).apply() }
    }

    /** True when a crack was sealed in the last [ms] milliseconds: the breath just did it. */
    fun sealedWithin(context: Context, ms: Long): Boolean = runCatching {
        val at = prefs(context).getLong(SEALED, 0L)
        at > 0 && System.currentTimeMillis() - at in 0..ms
    }.getOrDefault(false)

    /** The day (epoch day) the glass last broke, or -1: that day's tree in the wood stays dry. */
    fun brokenDay(context: Context): Long = runCatching {
        val at = prefs(context).getLong(BROKEN, 0L)
        if (at <= 0L) -1L else Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    }.getOrDefault(-1L)

    fun seams(context: Context): Int = runCatching { prefs(context).getInt(SEAMS, 0) }.getOrDefault(0)

    fun now(context: Context): GlassState = runCatching {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val sealed = p.getLong(SEALED, 0L)
        val sealedDay = if (sealed > 0) Instant.ofEpochMilli(sealed).atZone(zone).toLocalDate().toEpochDay() else -1L
        GlassState.at(now, p.getLong(CRACKED, 0L), sealed, p.getLong(BROKEN, 0L), sealedDay, LocalDate.now(zone).toEpochDay())
    }.getOrDefault(GlassState.at(0, 0, 0, 0, 0, 0))
}
