package app.hisn.android.nativeshell

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import app.hisn.core.FeedWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One local day in the person's feed apps.
 *
 * [totalMs] and [perApp] come from Android's daily usage totals, which it keeps for weeks. The
 * rest (opens, first and last scroll, the longest session, time by hour) comes from Android's
 * usage events, which it keeps for about a week or two; [detailed] says whether they cover the
 * whole day, and the page never compares a day it only half knows.
 */
internal class FeedDay(
    val date: LocalDate,
    val totalMs: Long,
    val perApp: Map<String, Long>,
    val detailed: Boolean,
    val opens: Int,
    /** Minute of the day the first feed session started, or -1. */
    val firstMinute: Int,
    /** Minute of the day the last feed session ended, or -1; after midnight counts as 24:xx. */
    val lastMinute: Int,
    val longestMs: Long,
    val longestApp: String?,
    val longestMinute: Int,
    val hours: LongArray
)

internal class FeedHistory(val days: List<FeedDay>, val todayFraction: Float, val apps: Set<String>) {
    val today: FeedDay get() = days.last()

    /** The seven days ending [weeksBack] weeks ago, oldest first. */
    fun week(weeksBack: Int = 0): List<FeedDay> {
        val end = days.size - weeksBack * 7
        return days.subList((end - 7).coerceAtLeast(0), end.coerceAtLeast(0))
    }

    fun feedWeek(): FeedWeek = FeedWeek(LongArray(14) { days[days.size - 14 + it].totalMs }, todayFraction)

    /** Four weekly totals, oldest first; the last one is this week so far. */
    fun fourWeeks(): LongArray = LongArray(4) { w -> week(3 - w).sumOf { it.totalMs } }

    /**
     * Last week's value for the same stretch of time as this week so far: six whole days and the
     * share of its seventh that has passed today. Null when last week is not fully known.
     */
    fun lastWeekComparable(value: (FeedDay) -> Long, needsDetail: Boolean = false): Long? {
        val last = week(1)
        if (last.size < 7) return null
        if (needsDetail && last.any { !it.detailed }) return null
        return last.dropLast(1).sumOf(value) + Math.round(value(last.last()) * todayFraction.toDouble())
    }

    fun index(date: LocalDate): Int = days.indexOfFirst { it.date == date }

    /** The days before [index] (up to 14) that a usual can be built from. */
    fun usualDays(index: Int, detail: Boolean): List<FeedDay> =
        days.subList((index - 14).coerceAtLeast(0), index.coerceAtLeast(0))
            .filter { (!detail || it.detailed) && it.totalMs > 0 }
}

internal object FeedHistories {
    const val DAYS = 28
    private const val EVENT_DAYS = 14
    private const val RESUMED = 1
    private const val PAUSED = 2
    private const val STOPPED = 23
    private const val SCREEN_OFF = 16
    private const val KEYGUARD = 17
    private const val MAX_SESSION_MS = 4 * 3_600_000L
    private const val SAME_SESSION_MS = 5_000L

    @Volatile private var cache: Triple<Long, Set<String>, FeedHistory>? = null

    /**
     * Apps that are never "feed time": Android's own Settings (a protection lock or setup keeps
     * people there for minutes) and this app. Counting them would inflate every number.
     */
    private fun notFeeds(context: Context): Set<String> {
        val settings = runCatching {
            context.packageManager.resolveActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS), 0)
                ?.activityInfo?.packageName
        }.getOrNull()
        return setOfNotNull(settings, "com.android.settings", context.packageName)
    }

    /** Feed apps: the ones with a rule, plus every installed app with a short-video feed. */
    fun feedApps(context: Context, state: HisnState): Set<String> {
        val ruled = state.blockedApps.toSet() + state.appLimits.keys + app.hisn.android.OpenWaitStore.apps(context) +
            state.appSchedule.let { if (it.enabled && !it.allApps) it.apps else emptySet() }
        return ruled + installedApps(context).map { it.packageName }.filter { app.hisn.core.SetupPlan.hasFeed(it) } -
            notFeeds(context) - Wins.notFeeds(context)
    }

    /** Ruled apps, the ones drawn in the warning colour. */
    fun ruledApps(context: Context, state: HisnState): Set<String> =
        state.blockedApps.toSet() + state.appLimits.keys + app.hisn.android.OpenWaitStore.apps(context) +
            state.appSchedule.let { if (it.enabled && !it.allApps) it.apps else emptySet() } +
            installedApps(context).map { it.packageName }.filter { app.hisn.core.SetupPlan.hasFeed(it) && state.shortVideos.on }

    fun read(context: Context, apps: Set<String>, fresh: Boolean = false): FeedHistory? {
        if (apps.isEmpty() || !Usage.hasAccess(context)) return null
        cache?.let { (at, set, history) ->
            if (!fresh && set == apps && System.currentTimeMillis() - at < 60_000L) return history
        }
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = System.currentTimeMillis()
        val dates = (0 until DAYS).map { today.minusDays((DAYS - 1 - it).toLong()) }
        val starts = dates.map { it.atStartOfDay(zone).toInstant().toEpochMilli() }

        // Daily totals per app, from Android's aggregated record.
        val perApp = dates.indices.map { i ->
            val end = if (i == dates.lastIndex) now else starts[i + 1]
            runCatching {
                manager.queryAndAggregateUsageStats(starts[i], end)
                    ?.filterKeys { it in apps }
                    ?.mapValues { it.value.totalTimeInForeground.coerceAtLeast(0L) }
                    ?.filterValues { it > 0L }
            }.getOrNull() ?: emptyMap()
        }

        // Sessions from the event log, for the last 14 days.
        val eventStart = starts[DAYS - EVENT_DAYS]
        val opens = IntArray(DAYS)
        val first = IntArray(DAYS) { -1 }
        val last = IntArray(DAYS) { -1 }
        val longest = LongArray(DAYS)
        val longestApp = arrayOfNulls<String>(DAYS)
        val longestMinute = IntArray(DAYS) { -1 }
        val hours = Array(DAYS) { LongArray(24) }
        var firstEventAt = Long.MAX_VALUE
        fun dayOf(ms: Long): Int {
            val date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
            return dates.indexOf(date)
        }
        fun minuteOf(ms: Long): Int = Instant.ofEpochMilli(ms).atZone(zone).let { it.hour * 60 + it.minute }
        fun addSpan(from: Long, to: Long) {
            var t = from
            while (t < to) {
                val z = Instant.ofEpochMilli(t).atZone(zone)
                val next = z.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli()
                val end = minOf(to, next)
                val d = dates.indexOf(z.toLocalDate())
                if (d >= 0) hours[d][z.hour] += end - t
                t = end
            }
        }
        var current: String? = null
        var sessionStart = 0L
        var pausedAt = -1L
        fun close(at: Long) {
            val pkg = current ?: return
            if (pkg in apps && at > sessionStart) {
                val length = minOf(at - sessionStart, MAX_SESSION_MS)
                val end = sessionStart + length
                addSpan(sessionStart, end)
                val d = dayOf(sessionStart)
                if (d >= 0) {
                    if (length > longest[d]) {
                        longest[d] = length; longestApp[d] = pkg; longestMinute[d] = minuteOf(sessionStart)
                    }
                    // A session that runs past midnight still belongs to the night it started in.
                    val endMinute = minuteOf(end) + if (dayOf(end) > d) 24 * 60 else 0
                    last[d] = maxOf(last[d], endMinute)
                }
            }
            current = null
        }
        runCatching { manager.queryEvents(eventStart, now) }.getOrNull()?.let { events ->
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                if (!events.getNextEvent(event)) break
                firstEventAt = minOf(firstEventAt, event.timeStamp)
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
                            if (pkg in apps) {
                                val d = dayOf(event.timeStamp)
                                if (d >= 0) {
                                    opens[d]++
                                    val minute = minuteOf(event.timeStamp)
                                    if (first[d] < 0) first[d] = minute
                                }
                            }
                        }
                    }
                    PAUSED, STOPPED -> if (pkg == current && pausedAt < 0) pausedAt = event.timeStamp
                    SCREEN_OFF, KEYGUARD -> { close(if (pausedAt >= 0) pausedAt else event.timeStamp); pausedAt = -1L }
                }
            }
            close(if (pausedAt >= 0) pausedAt else now)
        }
        // A day is known in detail only when the event log reaches back before it began.
        val firstKnown = if (firstEventAt == Long.MAX_VALUE) Long.MAX_VALUE else firstEventAt
        val days = dates.indices.map { i ->
            val detailed = i >= DAYS - EVENT_DAYS && (starts[i] >= firstKnown ||
                (i == DAYS - EVENT_DAYS && firstKnown - starts[i] < 6 * 3_600_000L))
            FeedDay(
                date = dates[i],
                totalMs = perApp[i].values.sum(),
                perApp = perApp[i],
                detailed = detailed,
                opens = opens[i],
                firstMinute = first[i],
                lastMinute = last[i],
                longestMs = longest[i],
                longestApp = longestApp[i],
                longestMinute = longestMinute[i],
                hours = hours[i]
            )
        }
        val fraction = (minuteOf(now) / 1440f).coerceIn(0.05f, 1f)
        return FeedHistory(days, fraction, apps).also { cache = Triple(System.currentTimeMillis(), apps, it) }
    }
}
