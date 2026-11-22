package app.hisn.android

import android.content.Context
import java.time.LocalDate

/**
 * What happened each time Unrot stepped in, counted per day for Progress: a rule closed
 * something, the person walked away at a wait screen, opened anyway, or took a breathing pause.
 * Only a date and four numbers are kept, never the app or the time of day, and only for 60 days.
 */
object StepIns {
    const val CLOSED = "closed"
    const val WALKED = "walked"
    const val OPENED = "opened"
    const val BREATHED = "breathed"
    /** An adult page closed by the word or picture check. */
    const val ADULT = "adult"
    /** A site request the filter refused (adult list or the person's own sites), batched. */
    const val SITES = "sites"
    val KINDS = listOf(CLOSED, WALKED, OPENED, BREATHED, ADULT, SITES)

    private const val PREFS = "step_ins"
    private const val KEEP_DAYS = 60

    private val pendingSites = java.util.concurrent.atomic.AtomicInteger()
    @Volatile private var lastFlush = 0L

    /**
     * Called by the filter on every refused request, so it only counts in memory and writes at
     * most once a minute (or every 50), never slowing the filter down.
     */
    @JvmStatic
    fun siteBlocked(context: Context) {
        val n = pendingSites.incrementAndGet()
        val now = System.currentTimeMillis()
        if (n < 50 && now - lastFlush < 60_000L) return
        lastFlush = now
        val take = pendingSites.getAndSet(0)
        if (take > 0) add(context, SITES, take)
    }

    @JvmStatic
    fun record(context: Context, kind: String) = add(context, kind, 1)

    private fun add(context: Context, kind: String, amount: Int) {
        runCatching {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val today = LocalDate.now().toEpochDay()
            val key = "$today:$kind"
            val edit = prefs.edit().putInt(key, prefs.getInt(key, 0) + amount)
            prefs.all.keys.forEach { stored ->
                val day = stored.substringBefore(':').toLongOrNull()
                if (day == null || day < today - KEEP_DAYS) edit.remove(stored)
            }
            edit.apply()
        }
    }

    /**
     * Sites and pages kept out today: the saved count plus what the filter has not written yet.
     * Unlike the filter's own counter, it survives the filter restarting (an app update does that).
     */
    @JvmStatic
    fun keptOutToday(context: Context): Int {
        val today = day(context, LocalDate.now())
        return (today[SITES] ?: 0) + (today[ADULT] ?: 0) + pendingSites.get()
    }

    /** Counts per kind for one local day. */
    fun day(context: Context, day: LocalDate): Map<String, Int> = runCatching {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        KINDS.associateWith { prefs.getInt("${day.toEpochDay()}:$it", 0) }
    }.getOrDefault(KINDS.associateWith { 0 })

    /** Counts per kind summed over [from]..[to], both included. */
    fun range(context: Context, from: LocalDate, to: LocalDate): Map<String, Int> {
        val total = KINDS.associateWith { 0 }.toMutableMap()
        var day = from
        while (!day.isAfter(to)) {
            day(context, day).forEach { (kind, count) -> total[kind] = (total[kind] ?: 0) + count }
            day = day.plusDays(1)
        }
        return total
    }
}
