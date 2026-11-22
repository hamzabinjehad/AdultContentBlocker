package app.hisn.android;

import android.app.Notification;
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
import app.hisn.core.ProtectionLock;

/**
 * Background health check for the layers that live outside the app window.
 *
 * A periodic JobScheduler job compares the saved app rules, direct-build guard
 * and DNS VPN choices with what is actually running. A lost layer raises
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
    private static final String LAST_SENT_SIGNATURE = "last_sent_signature";
    private static final String LAST_NOTIFIED = "last_notified";
    private static final String LAST_CHECKED = "last_checked";
    static final long REPEAT_MILLIS = 12 * 3600000L;
    /** Android's minimum periodic interval. */
    static final long PERIOD_MILLIS = 15 * 60 * 1000L;

    private LayerWatchdog() { }

    /** The layers the owner's saved choices expect to be running. */
    static Set<LayerWatch.Layer> expected(Context context) {
        boolean armed = false;
        boolean appProtection = false;
        try {
            PolicyStore store = PolicyStore.get(context);
            // Either screen-guard switch keeps the accessibility service worth
            // watching, and a running protection lock arms the removal guard by
            // itself even when both switches are off.
            armed = store.uninstallLock() || store.installLock()
                    || ProtectionLock.locked(store.remainingMillis());
            appProtection |= store.snapshot().hasConfiguredAppProtection();
        } catch (RuntimeException unavailable) {
            // Unreadable settings are not a reason to claim a layer is armed.
        }
        try {
            appProtection |= PrivateTabsStore.on(context) || BrowserOnlyStore.on(context)
                    || !OpenWaitStore.apps(context).isEmpty();
        } catch (RuntimeException unavailable) {
            // Keep the expectation from any readable saved rules.
        }
        return LayerWatch.expected(BuildConfig.SELF_PROTECTION, armed, appProtection,
                DnsVpnService.resumeRequested(context));
    }

    static boolean expectsAppProtection(Context context) {
        return expected(context).contains(LayerWatch.Layer.ACCESSIBILITY);
    }

    /** The layers actually running right now, from this process's live state. */
    static Set<LayerWatch.Layer> running() {
        boolean accessibility = TextGuardService.connected && TextGuardService.ready;
        DnsVpnService.State dns = DnsVpnService.state;
        boolean dnsRunning = dns == DnsVpnService.State.RUNNING || dns == DnsVpnService.State.DEGRADED;
        return LayerWatch.running(accessibility, dnsRunning);
    }

    /** Keep the periodic job scheduled exactly while a layer is expected. */
    static void syncSchedule(Context context) {
        try {
            JobScheduler scheduler = context.getSystemService(JobScheduler.class);
            if (scheduler == null) return;
            boolean expected = !expected(context).isEmpty();
            boolean scheduled = Compat.hasPendingJob(scheduler, JOB_ID);
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
        if (signature.isEmpty()) {
            prefs.edit().putLong(LAST_CHECKED, now).apply();
            clearLoss(context);
            return;
        }
        boolean due = LayerWatch.shouldNotify(prefs.getString(LAST_SENT_SIGNATURE, ""),
                prefs.getLong(LAST_NOTIFIED, 0), signature, now, REPEAT_MILLIS);
        SharedPreferences.Editor observed = prefs.edit().putString(LAST_SIGNATURE, signature).putLong(LAST_CHECKED, now);
        // The status line must show a loss even when no notification could be
        // posted. The repeat time is stored only on a successful notice, so the
        // job retries until permission or the channel is available.
        if (due && notify(context, lost)) {
            observed.putString(LAST_SENT_SIGNATURE, signature).putLong(LAST_NOTIFIED, now);
        }
        observed.apply();
    }

    private static boolean notify(Context context, Set<LayerWatch.Layer> lost) {
        boolean accessibility = lost.contains(LayerWatch.Layer.ACCESSIBILITY);
        boolean dns = lost.contains(LayerWatch.Layer.DNS);
        int body = accessibility && dns ? R.string.layer_lost_both
                : accessibility ? R.string.layer_lost_accessibility : R.string.layer_lost_dns;
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (notifications == null || !Permissions.notifications(context)) return false;
        try {
            Compat.createChannel(context, CHANNEL, R.string.layer_health_channel,
                    NotificationManager.IMPORTANCE_DEFAULT);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                android.app.NotificationChannel channel = notifications.getNotificationChannel(CHANNEL);
                if (channel == null || channel.getImportance() == NotificationManager.IMPORTANCE_NONE) return false;
            }
            PendingIntent open = PendingIntent.getActivity(context, 0,
                    new Intent(context, ShellActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            notifications.notify(NOTIFICATION_ID, Compat.builder(context, CHANNEL,
                    NotificationManager.IMPORTANCE_DEFAULT)
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
                .remove(LAST_SIGNATURE).remove(LAST_SENT_SIGNATURE).remove(LAST_NOTIFIED).apply();
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
