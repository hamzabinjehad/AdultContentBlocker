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
        DayWidget.update(context)
        if (!Edition.islamic) crackNotice(context)
    }

    private const val CHANNEL = "glass"
    private const val NOTICE_ID = 7301

    /**
     * "Your glass cracked": six minutes to seal it with a breath, with Breathe one tap away.
     * It never says what was closed, so it is safe on a lock screen; it goes away on its own
     * when the window closes, and as soon as a breath seals it.
     */
    private fun crackNotice(context: Context) {
        runCatching {
            val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
            if (!Permissions.notifications(context)) return
            Compat.createChannel(context, CHANNEL, R.string.glass_channel, android.app.NotificationManager.IMPORTANCE_DEFAULT)
            val flags = android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            val open = android.app.PendingIntent.getActivity(context, 30,
                android.content.Intent(context, ShellActivity::class.java), flags)
            val breathe = android.app.PendingIntent.getActivity(context, 31,
                android.content.Intent(context, BreatheActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK), flags)
            val body = context.getString(R.string.glass_notice_body, app.hisn.android.nativeshell.glassShort(GlassState.SEAL_WINDOW_MS))
            val b = Compat.builder(context, CHANNEL, android.app.NotificationManager.IMPORTANCE_DEFAULT)
                .setSmallIcon(R.drawable.ic_pause).setColor(0xFFF2C14E.toInt())
                .setContentTitle(context.getString(R.string.glass_notice_title))
                .setContentText(body)
                .setStyle(android.app.Notification.BigTextStyle().bigText(body))
                .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
                .addAction(android.app.Notification.Action.Builder(null, context.getString(R.string.nudge_breathe), breathe).build())
                .setCategory(android.app.Notification.CATEGORY_REMINDER)
            if (android.os.Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(GlassState.SEAL_WINDOW_MS)
            manager.notify(NOTICE_ID, b.build())
        }
    }

    private fun clearNotice(context: Context) {
        runCatching { context.getSystemService(android.app.NotificationManager::class.java)?.cancel(NOTICE_ID) }
    }

    /** A breathing pause: inside the window it seals the crack with gold. */
    @JvmStatic
    fun breathed(context: Context) {
        runCatching {
            val p = prefs(context)
            val now = System.currentTimeMillis()
            if (GlassState.breathSeals(now, p.getLong(CRACKED, 0L), p.getLong(SEALED, 0L))) {
                p.edit().putLong(SEALED, now).putInt(SEAMS, p.getInt(SEAMS, 0) + 1).apply()
                clearNotice(context)
                DayWidget.update(context)
            }
        }
    }

    /** The person's own report that adult content got through: the glass breaks. */
    @JvmStatic
    fun broke(context: Context) {
        runCatching { prefs(context).edit().putLong(BROKEN, System.currentTimeMillis()).apply() }
        clearNotice(context)
        DayWidget.update(context)
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

    /** When Unrot last closed an adult page (the crack), or 0. Today says "Cracked at 14:02". */
    fun crackedAt(context: Context): Long = runCatching { prefs(context).getLong(CRACKED, 0L) }.getOrDefault(0L)

    /** When the person last said adult content got through, or 0. Clean time counts from here. */
    fun brokenAt(context: Context): Long = runCatching { prefs(context).getLong(BROKEN, 0L) }.getOrDefault(0L)

    private const val GIVEN = "given_back_ms"

    /** The time given back as Today last read it, kept so the home-screen widget can show it. */
    fun rememberGiven(context: Context, ms: Long) {
        runCatching {
            val p = prefs(context)
            if (p.getLong(GIVEN, -1L) != ms) { p.edit().putLong(GIVEN, ms).apply(); DayWidget.update(context) }
        }
    }

    fun lastGiven(context: Context): Long = runCatching { prefs(context).getLong(GIVEN, 0L) }.getOrDefault(0L)

    /** The widget's picture: the glass's painting for its state, or the stage's painting. */
    @JvmStatic
    fun widgetArt(context: Context): Int = when (now(context).look) {
        GlassState.Look.CRACKED -> R.drawable.unrot_glass_cracked
        GlassState.Look.GOLD -> R.drawable.unrot_glass_gold
        GlassState.Look.BROKEN -> R.drawable.unrot_glass_broken
        else -> app.hisn.android.nativeshell.glassArtFor(lastGiven(context))
    }

    /** "2h 35m" for the widget, or null before anything is given back. */
    @JvmStatic
    fun widgetGiven(context: Context): String? {
        val ms = lastGiven(context)
        return if (ms < 60_000L) null else app.hisn.android.nativeshell.glassShort(ms)
    }

    fun now(context: Context): GlassState = runCatching {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val sealed = p.getLong(SEALED, 0L)
        val sealedDay = if (sealed > 0) Instant.ofEpochMilli(sealed).atZone(zone).toLocalDate().toEpochDay() else -1L
        GlassState.at(now, p.getLong(CRACKED, 0L), sealed, p.getLong(BROKEN, 0L), sealedDay, LocalDate.now(zone).toEpochDay())
    }.getOrDefault(GlassState.at(0, 0, 0, 0, 0, 0))
}
