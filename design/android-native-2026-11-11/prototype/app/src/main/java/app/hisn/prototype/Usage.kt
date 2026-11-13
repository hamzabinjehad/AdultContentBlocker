package app.hisn.prototype

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import java.util.Calendar

/**
 * Android's own on-device screen-time record for today.
 *
 * Reading it is a special access ("Usage access") the owner grants in Android settings; without it
 * every query reads as zero. Nothing here leaves the phone and no history is kept — only the total
 * foreground time per app since local midnight.
 */
object Usage {

    fun hasAccess(context: Context): Boolean = runCatching {
        val operations = context.getSystemService(AppOpsManager::class.java)
            ?: return@runCatching false
        @Suppress("DEPRECATION")
        operations.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Foreground milliseconds for one app since local midnight; zero when unavailable. */
    fun usedTodayMs(context: Context, packageName: String): Long {
        if (packageName.isEmpty()) return 0L
        return runCatching {
            val manager = context.getSystemService(UsageStatsManager::class.java)
                ?: return@runCatching 0L
            val midnight = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val stats = manager.queryAndAggregateUsageStats(
                midnight.timeInMillis,
                System.currentTimeMillis()
            )
            stats?.get(packageName)?.totalTimeInForeground?.coerceAtLeast(0L) ?: 0L
        }.getOrDefault(0L)
    }

    fun usedTodayMinutes(context: Context, packageName: String): Int =
        (usedTodayMs(context, packageName) / 60_000L).toInt()

    /** "under a minute", "12 min", "1 h 5 min" — a limit row should never read as a bare zero. */
    fun formatDuration(milliseconds: Long): String {
        if (milliseconds < 60_000L) return if (milliseconds <= 0L) "0 min" else "under a minute"
        val minutes = milliseconds / 60_000L
        if (minutes < 60L) return "$minutes min"
        val hours = minutes / 60L
        val rest = minutes % 60L
        return if (rest == 0L) "$hours h" else "$hours h $rest min"
    }

    fun openSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
