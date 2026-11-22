package app.hisn.android.nativeshell

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One app on one day: time in front, and how many times it was opened. */
internal class AppDayUse(val pkg: String, val ms: Long, val opens: Int)

/** One stretch in one app, for the day's timeline. */
internal class UseSession(val pkg: String, val startMinute: Int, val ms: Long)

/**
 * Everything the person used on one day, every app and not only feeds, the way Android's own
 * Digital Wellbeing shows it: total screen time, each app's time and opens, and a timeline.
 * Read on the phone from Android's usage record each time; nothing is stored.
 */
internal class DayUsage(val totalMs: Long, val apps: List<AppDayUse>, val sessions: List<UseSession>) {
    val opens: Int get() = apps.sumOf { it.opens }
}

internal object DayUsages {
    private const val RESUMED = 1
    private const val PAUSED = 2
    private const val STOPPED = 23
    private const val SCREEN_OFF = 16
    private const val KEYGUARD = 17
    private const val MAX_SESSION_MS = 4 * 3_600_000L
    private const val SAME_SESSION_MS = 5_000L

    /** Home screens, the system bar and this app: not "use" anyone chose. */
    private fun hidden(context: Context): Set<String> {
        val homes = runCatching {
            context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
            ).mapNotNull { it.activityInfo?.packageName }
        }.getOrDefault(emptyList())
        return homes.toSet() + setOf(context.packageName, "com.android.systemui", "android")
    }

    fun read(context: Context, date: LocalDate): DayUsage? {
        if (!Usage.hasAccess(context)) return null
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
        if (end <= start) return DayUsage(0, emptyList(), emptyList())
        val skip = hidden(context)
        val time = runCatching {
            manager.queryAndAggregateUsageStats(start, end)
                ?.filterKeys { it !in skip }
                ?.mapValues { it.value.totalTimeInForeground.coerceAtLeast(0L) }
                ?.filterValues { it >= 1_000L }
        }.getOrNull() ?: emptyMap()

        val opens = HashMap<String, Int>()
        val sessions = ArrayList<UseSession>()
        var current: String? = null
        var sessionStart = 0L
        var pausedAt = -1L
        fun minuteOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).let { it.hour * 60 + it.minute }
        fun close(at: Long) {
            val pkg = current ?: return
            if (pkg !in skip && at > sessionStart) {
                sessions += UseSession(pkg, minuteOf(sessionStart), minOf(at - sessionStart, MAX_SESSION_MS))
            }
            current = null
        }
        runCatching { manager.queryEvents(start, end) }.getOrNull()?.let { events ->
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                if (!events.getNextEvent(event)) break
                val pkg = event.packageName ?: ""
                if (pkg.isEmpty() && event.eventType != SCREEN_OFF && event.eventType != KEYGUARD) continue
                when (event.eventType) {
                    RESUMED -> {
                        if (pkg == current && pausedAt >= 0 && event.timeStamp - pausedAt < SAME_SESSION_MS) {
                            pausedAt = -1L
                        } else {
                            close(if (pausedAt >= 0) pausedAt else event.timeStamp)
                            current = pkg
                            sessionStart = event.timeStamp
                            pausedAt = -1L
                            if (pkg !in skip) opens[pkg] = (opens[pkg] ?: 0) + 1
                        }
                    }
                    PAUSED, STOPPED -> if (pkg == current && pausedAt < 0) pausedAt = event.timeStamp
                    SCREEN_OFF, KEYGUARD -> { close(if (pausedAt >= 0) pausedAt else event.timeStamp); pausedAt = -1L }
                }
            }
            close(if (pausedAt >= 0) pausedAt else end)
        }
        // Apps with no name to show (uninstalled, or helper packages) are left out rather than print a package id.
        val installed = { pkg: String -> appLabel(context, pkg) != pkg }
        val apps = (time.keys + opens.keys).filter(installed).map { AppDayUse(it, time[it] ?: 0L, opens[it] ?: 0) }
            .filter { it.ms >= 60_000L || it.opens > 0 }
            .sortedWith(compareByDescending<AppDayUse> { it.ms }.thenByDescending { it.opens })
        val known = apps.map { it.pkg }.toSet()
        return DayUsage(time.filterKeys { it in known }.values.sum(), apps, sessions.filter { it.pkg in known })
    }
}
