package app.hisn.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Set;

import app.hisn.core.LayerWatch;

/**
 * Background health check for the layers that live outside the app window.
 *
 * A periodic JobScheduler job compares what the owner left armed (the direct
 * build's accessibility guard while "Keep Hisn installed" is on, and the DNS
 * VPN while it is left on) with what is actually running. A lost layer raises
 * one local notification, repeats at most once every twelve hours, and clears
 * as soon as the layer is back. It never restarts a service, never reads screen
 * text and never contacts a server.
 *
 * The job is scheduled only while at least one layer is expected, and it
 * survives a reboot; the app and the boot receiver refresh the schedule. A
 * process that Android kills cannot restart itself, so the notice says what
 * stopped instead of claiming protection.
 */
final class LayerWatchdog {
    private static final String TAG = "HisnLayerWatch";
    private static final int JOB_ID = 1401;
    private static final int NOTIFICATION_ID = 4;
    private static final String CHANNEL = "layer_health";
    private static final String PREFS = "hisn_watchdog";
    private static final String LAST_SIGNATURE = "last_signature";
    private static final String LAST_NOTIFIED = "last_notified";
    private static final String LAST_CHECKED = "last_checked";
    static final long REPEAT_MILLIS = 12 * 3600000L;
    /** Android's minimum periodic interval. */
    static final long PERIOD_MILLIS = 15 * 60 * 1000L;

    private LayerWatchdog() { }

    /** The layers the owner's saved choices expect to be running. */
    static Set<LayerWatch.Layer> expected(Context context) {
        boolean keep = false;
        try {
            keep = PolicyStore.get(context).uninstallLock();
        } catch (RuntimeException unavailable) {
            // Unreadable settings are not a reason to claim a layer is armed.
        }
        return LayerWatch.expected(BuildConfig.SELF_PROTECTION, keep,
                DnsVpnService.resumeRequested(context));
    }

    /** The layers actually running right now, from this process's live state. */
    static Set<LayerWatch.Layer> running() {
        boolean accessibility = BuildConfig.SELF_PROTECTION && TextGuardService.ready;
        DnsVpnService.State dns = DnsVpnService.state;
        boolean dnsRunning = dns == DnsVpnService.State.RUNNING || dns == DnsVpnService.State.DEGRADED;
        return LayerWatch.running(accessibility, dnsRunning);
    }

    /** Keep the periodic job scheduled exactly while a layer is expected. */
    static void syncSchedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        try {
            boolean expected = !expected(context).isEmpty();
            boolean scheduled = scheduler.getPendingJob(JOB_ID) != null;
            if (!expected) {
                if (scheduled) scheduler.cancel(JOB_ID);
                // A notice from before the last layer was turned off must not linger.
                reset(context);
            } else if (!scheduled) {
                scheduler.schedule(new JobInfo.Builder(JOB_ID,
                        new ComponentName(context, LayerWatchdogJobService.class))
                        .setPeriodic(PERIOD_MILLIS)
                        .setPersisted(true)
                        .setRequiresCharging(false)
                        .setRequiresDeviceIdle(false)
                        .build());
            }
        } catch (RuntimeException unavailable) {
            // A rejected schedule is not a protection claim; the next open retries.
            android.util.Log.w(TAG, "Layer watchdog schedule was not accepted", unavailable);
        }
    }

    /** Run one check and post a notice only when a loss changed or is due to repeat. */
    static void check(Context context) {
        Set<LayerWatch.Layer> expected = expected(context);
        Set<LayerWatch.Layer> lost = LayerWatch.lost(expected, running());
        String signature = LayerWatch.signature(lost);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        // The check ran even when everything is healthy; this is what the status shows.
        prefs.edit().putLong(LAST_CHECKED, now).apply();
        if (signature.isEmpty()) {
            clearLoss(context);
            return;
        }
        boolean due = LayerWatch.shouldNotify(prefs.getString(LAST_SIGNATURE, ""),
                prefs.getLong(LAST_NOTIFIED, 0), signature, now, REPEAT_MILLIS);
        if (!due) return;
        // The status line must show a loss even when no notification could be
        // posted. The repeat time is stored only on a successful notice, so the
        // job retries until permission or the channel is available.
        if (notify(context, lost)) {
            prefs.edit().putString(LAST_SIGNATURE, signature).putLong(LAST_NOTIFIED, now).apply();
        } else {
            prefs.edit().putString(LAST_SIGNATURE, signature).apply();
        }
    }

    private static boolean notify(Context context, Set<LayerWatch.Layer> lost) {
        boolean accessibility = lost.contains(LayerWatch.Layer.ACCESSIBILITY);
        boolean dns = lost.contains(LayerWatch.Layer.DNS);
        int body = accessibility && dns ? R.string.layer_lost_both
                : accessibility ? R.string.layer_lost_accessibility : R.string.layer_lost_dns;
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (notifications == null) return false;
        try {
            notifications.createNotificationChannel(new NotificationChannel(CHANNEL,
                    context.getString(R.string.layer_health_channel), NotificationManager.IMPORTANCE_DEFAULT));
            PendingIntent open = PendingIntent.getActivity(context, 0,
                    new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            notifications.notify(NOTIFICATION_ID, new Notification.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_shield).setColor(context.getColor(R.color.accent))
                    .setContentTitle(context.getString(R.string.layer_health_title))
                    .setContentText(context.getString(body))
                    .setStyle(new Notification.BigTextStyle().bigText(context.getString(body)))
                    .setContentIntent(open).setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_STATUS).build());
            return true;
        } catch (RuntimeException unavailable) {
            // Notifications denied or unavailable: the app status is still honest.
            android.util.Log.w(TAG, "Layer loss notice could not be posted", unavailable);
            return false;
        }
    }

    private static void clearLoss(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(LAST_SIGNATURE).remove(LAST_NOTIFIED).apply();
        cancelNotice(context);
    }

    private static void reset(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        cancelNotice(context);
    }

    private static void cancelNotice(Context context) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (notifications != null) {
            try { notifications.cancel(NOTIFICATION_ID); }
            catch (RuntimeException ignored) { /* nothing to cancel */ }
        }
    }

    /** When the last check ran, or 0; informational, never a protection claim. */
    static long lastCheckedAt(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(LAST_CHECKED, 0);
    }

    /** The layers the last check found missing, or an empty string. */
    static String lostSignature(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LAST_SIGNATURE, "");
    }
}
