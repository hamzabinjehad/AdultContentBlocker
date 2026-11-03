package app.hisn.android;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

import java.util.Calendar;
import java.util.Map;

/**
 * Reads Android's own on-device foreground-time record for the current local day.
 *
 * <p>This is a special-access permission ("Usage access"), not a runtime
 * permission. Without it every query reads as zero, so the caller must check
 * {@link #hasAccess(Context)} and show the missing step instead of reporting an
 * enforced limit. No usage event, app name or history leaves the phone.
 */
final class AppUsage {
    /** Whether the owner has granted Hisn access to Android's usage records. */
    static boolean hasAccess(Context context) {
        try {
            AppOpsManager operations = context.getSystemService(AppOpsManager.class);
            if (operations == null) return false;
            @SuppressWarnings("deprecation")
            int mode = operations.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    /** Foreground milliseconds for one app since local midnight; zero when unavailable. */
    static long usedToday(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return 0;
        try {
            UsageStatsManager manager = context.getSystemService(UsageStatsManager.class);
            if (manager == null) return 0;
            Calendar start = Calendar.getInstance();
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            long now = System.currentTimeMillis();
            Map<String, UsageStats> stats = manager.queryAndAggregateUsageStats(start.getTimeInMillis(), now);
            UsageStats entry = stats == null ? null : stats.get(packageName);
            return entry == null ? 0 : Math.max(0, entry.getTotalTimeInForeground());
        } catch (RuntimeException unavailable) {
            return 0;
        }
    }

    private AppUsage() { }
}
