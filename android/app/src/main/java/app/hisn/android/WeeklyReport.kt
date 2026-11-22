package app.hisn.android

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.hisn.android.nativeshell.FeedHistories
import app.hisn.android.nativeshell.Usage
import app.hisn.android.nativeshell.Wins
import app.hisn.android.nativeshell.installedApps
import app.hisn.android.nativeshell.unrotEdition
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Unrot's Sunday report: one notification at 19:00 with the week's feed time next to the
 * person's "before", which opens Progress, where the week can be shared.
 *
 * Why this shape: a weekly look back is where progress becomes visible (Harkin et al. 2016, a
 * meta-analysis of 138 studies: monitoring progress helps reach goals, more so when the progress
 * is seen), and once a week keeps it wanted rather than nagging. It names feed time only: adult
 * blocking counts stay inside the app and never reach a notification or a share card. It never
 * says "scrolled": a feed app may have been used for messages or study.
 */
class WeeklyReport : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_FIRE) return
        val pending = goAsync()
        Thread {
            try {
                val week = LocalDate.now().toEpochDay() / 7
                if (isOn(context) && prefs(context).getLong(LAST_WEEK, -1) != week && post(context)) {
                    prefs(context).edit().putLong(LAST_WEEK, week).apply()
                }
            } catch (failed: RuntimeException) {
                android.util.Log.w("WeeklyReport", "Report could not be made", failed)
            } finally {
                schedule(context)
                pending.finish()
            }
        }.start()
    }

    /** This week's numbers: last seven whole days, the "before", and the seven days before that. */
    internal data class Week(val feedMs: Long, val beforeMs: Long, val lastWeekMs: Long, val days: Int)

    companion object {
        private const val PREFS = "unrot_weekly"
        private const val ON = "on"
        private const val LAST_WEEK = "last_week"
        private const val CHANNEL = "weekly_report"
        private const val NOTIFICATION_ID = 0x5745
        private const val ACTION_FIRE = "app.hisn.android.WEEKLY_REPORT"
        const val HOUR = 19

        private fun prefs(context: Context) =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** On by default in Unrot: it is once a week, and the switch is in Settings. */
        fun isOn(context: Context): Boolean =
            unrotEdition && runCatching { prefs(context).getBoolean(ON, true) }.getOrDefault(false)

        fun set(context: Context, on: Boolean) {
            prefs(context).edit().putBoolean(ON, on).apply()
            schedule(context)
        }

        /** The next Sunday at 19:00, today if it is Sunday before then. */
        internal fun nextFire(now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
            var at = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(HOUR, 0)
            if (!at.isAfter(now)) at = at.plusWeeks(1)
            return at
        }

        fun schedule(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val fire = PendingIntent.getBroadcast(context, 0,
                Intent(context, WeeklyReport::class.java).setAction(ACTION_FIRE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            if (!isOn(context)) {
                alarms.cancel(fire)
                return
            }
            val at = nextFire().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            runCatching { alarms.setWindow(AlarmManager.RTC_WAKEUP, at, 30 * 60_000L, fire) }
                .onFailure { android.util.Log.w("WeeklyReport", "Report could not be armed", it) }
        }

        /** Feed apps without the shell's state: every installed app with a feed, plus apps with a wait. */
        internal fun feedApps(context: Context): Set<String> =
            (installedApps(context).map { it.packageName }.filter { app.hisn.core.SetupPlan.hasFeed(it) }.toSet() +
                OpenWaitStore.apps(context)) - Wins.notFeeds(context) - context.packageName

        /**
         * The widget's line, "Feeds today 42 min · before 2 h 10", or null in Hisn, without usage
         * access, or before anything was used today. Feed time only, like the report.
         */
        @JvmStatic fun widgetLine(context: Context): String? {
            if (!unrotEdition || !Usage.hasAccess(context)) return null
            val feeds = feedApps(context)
            val today = Usage.usedTodayAll(context).filterKeys { it in feeds }.values.sum()
            val before = Wins.savedBeforeMs(context)
            val now = Usage.formatDuration(today)
            return if (before > 0) context.getString(R.string.widget_feeds_before, now, Usage.formatDuration(before))
                else context.getString(R.string.widget_feeds, now)
        }

        /** Reads usage: call off the main thread. Null without usage access or a full week. */
        internal fun week(context: Context): Week? {
            if (!Usage.hasAccess(context)) return null
            val history = FeedHistories.read(context, feedApps(context), fresh = true) ?: return null
            val today = LocalDate.now()
            val whole = history.days.filter { it.date.isBefore(today) }
            val last7 = whole.filter { !it.date.isBefore(today.minusDays(7)) }
            if (last7.size < 5) return null
            val prior = whole.filter { it.date.isBefore(today.minusDays(7)) && !it.date.isBefore(today.minusDays(14)) }
            return Week(
                feedMs = last7.sumOf { it.totalMs } / last7.size,
                beforeMs = Wins.savedBeforeMs(context),
                lastWeekMs = if (prior.size >= 5) prior.sumOf { it.totalMs } / prior.size else -1L,
                days = last7.size
            )
        }

        /** Title and body; the comparison is against "before", or last week when there is none yet. */
        internal fun words(context: Context, w: Week): Pair<String, String> {
            val now = Usage.formatDuration(w.feedMs)
            val against = if (w.beforeMs > 0) w.beforeMs else w.lastWeekMs
            if (against <= 0) {
                return context.getString(R.string.weekly_title_plain, now) to context.getString(R.string.weekly_body_plain)
            }
            val diff = against - w.feedMs
            val pct = (kotlin.math.abs(diff) * 100 / against).toInt()
            val vs = context.getString(if (w.beforeMs > 0) R.string.weekly_vs_before else R.string.weekly_vs_last)
            return when {
                pct < 5 -> context.getString(R.string.weekly_title_same, now) to context.getString(R.string.weekly_body_same, vs)
                diff > 0 -> {
                    val yearHours = (diff * 365 / 3_600_000L).toInt()
                    context.getString(R.string.weekly_title_less, pct, vs) to
                        context.resources.getQuantityString(R.plurals.weekly_body_less, yearHours, now, yearHours)
                }
                else -> context.getString(R.string.weekly_title_more, now) to context.getString(R.string.weekly_body_more, pct, vs)
            }
        }

        private fun post(context: Context): Boolean {
            val notifications = context.getSystemService(NotificationManager::class.java) ?: return false
            if (!Permissions.notifications(context)) return false
            val w = week(context) ?: return false
            val (title, body) = words(context, w)
            Compat.createChannel(context, CHANNEL, R.string.weekly_channel, NotificationManager.IMPORTANCE_DEFAULT)
            val open = PendingIntent.getActivity(context, 20,
                Intent(context, ShellActivity::class.java).putExtra("page", 2)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            notifications.notify(NOTIFICATION_ID, Compat.builder(context, CHANNEL,
                NotificationManager.IMPORTANCE_DEFAULT)
                .setSmallIcon(R.drawable.ic_pause).setColor(context.getColor(R.color.accent))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setContentIntent(open).setAutoCancel(true)
                .addAction(Notification.Action.Builder(null, context.getString(R.string.weekly_open), open).build())
                .setCategory(Notification.CATEGORY_RECOMMENDATION).build())
            return true
        }
    }
}
