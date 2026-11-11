package app.hisn.android;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityWindowInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URLConnection;
import java.util.Locale;

/**
 * The small set of platform differences between Android 6.0 (the app's floor) and current
 * releases. Every method keeps the modern call for the new platform and a documented fallback for
 * the old one, so the caller reads one name and no version check leaks into the feature.
 *
 * <p>This class deliberately has no androidx dependency: the app is framework-only. A method whose
 * body touches a class added after API 23 is only reached after its own version guard, which is the
 * form both Android's verifier and lint expect.</p>
 */
final class Compat {
    private Compat() { }

    /** Creates a notification channel where channels exist; below Android 8 the channel is unused. */
    static void createChannel(Context context, String id, int nameResource, int importance) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(id,
                context.getString(nameResource), importance));
    }

    /**
     * A notification builder for a channel. Android 8+ binds the channel; on Android 6 and 7 the
     * same importance maps to the legacy priority so the notice still interrupts at the right level.
     */
    static Notification.Builder builder(Context context, String channel, int importance) {
        if (Build.VERSION.SDK_INT >= 26) return new Notification.Builder(context, channel);
        int priority = importance >= NotificationManager.IMPORTANCE_HIGH ? Notification.PRIORITY_HIGH
                : importance <= NotificationManager.IMPORTANCE_LOW ? Notification.PRIORITY_LOW
                : Notification.PRIORITY_DEFAULT;
        return new Notification.Builder(context).setPriority(priority);
    }

    /** Android 8 requires startForegroundService; older releases accept an ordinary start. */
    static void startForegroundService(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    /** Removes the ongoing notification; the int overload exists from Android 7. */
    static void stopForeground(Service service) {
        if (Build.VERSION.SDK_INT >= 24) service.stopForeground(Service.STOP_FOREGROUND_REMOVE);
        else service.stopForeground(true);
    }

    /**
     * Storage readable before the first unlock. Direct boot arrived in Android 7; on Android 6 the
     * ordinary context is already the only one and the boot receiver starts after boot completes.
     */
    static Context deviceProtected(Context context) {
        return Build.VERSION.SDK_INT >= 24 ? context.createDeviceProtectedStorageContext() : context;
    }

    /** The app's data directory, without the Android 7-only getDataDir call. */
    @SuppressWarnings("deprecation")
    static File dataDir(Context context) {
        if (Build.VERSION.SDK_INT >= 24) return context.getDataDir();
        File files = context.getFilesDir();
        return files == null ? null : files.getParentFile();
    }

    /** The declared response size where the long accessor exists, otherwise the header itself. */
    static long contentLength(URLConnection connection) {
        if (Build.VERSION.SDK_INT >= 24) return connection.getContentLengthLong();
        String header = connection.getHeaderField("Content-Length");
        if (header == null) return -1;
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException malformed) {
            return -1;
        }
    }

    /** Whether the job with this id is already scheduled, on both the Android 7 and older APIs. */
    static boolean hasPendingJob(JobScheduler scheduler, int id) {
        if (Build.VERSION.SDK_INT >= 24) return scheduler.getPendingJob(id) != null;
        for (JobInfo job : scheduler.getAllPendingJobs()) {
            if (job.getId() == id) return true;
        }
        return false;
    }

    /** Removes a preferences file; Android 6 only has the clear operation. */
    static void deleteSharedPreferences(Context context, String name) {
        if (Build.VERSION.SDK_INT >= 24) {
            context.deleteSharedPreferences(name);
            return;
        }
        SharedPreferences preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        // Android 6 has no public delete call; remove the backing XML so a later read sees a
        // fresh install, as it would after Settings' "Clear data".
        File data = dataDir(context);
        if (data != null) {
            File file = new File(new File(data, "shared_prefs"), name + ".xml");
            if (file.exists() && !file.delete()) {
                // An empty file remains; the next read still treats it as saved settings.
            }
        }
    }

    /** Whether the system's animator scale asks for animations; Android 8 added the direct query. */
    static boolean animationsEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= 26) return ValueAnimator.areAnimatorsEnabled();
        try {
            return Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        } catch (RuntimeException unreadable) {
            return true;
        }
    }

    /** Keeps editable text out of Android's autofill suggestions where the flag exists. */
    static void disableAutofill(View view) {
        if (Build.VERSION.SDK_INT >= 26) view.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
    }

    /** True only on Android 8+, the first release whose accessibility windows expose this state. */
    static boolean inPictureInPicture(AccessibilityWindowInfo window) {
        return Build.VERSION.SDK_INT >= 26 && window.isInPictureInPictureMode();
    }

    /** The first locale, without the Android 7-only LocaleList accessor. */
    @SuppressWarnings("deprecation")
    static Locale locale(Configuration configuration) {
        if (Build.VERSION.SDK_INT >= 24) return configuration.getLocales().get(0);
        return configuration.locale;
    }

    /** The overlay type Android 8 moved; older releases use the SYSTEM_ALERT_WINDOW type. */
    @SuppressWarnings("deprecation")
    static int overlayType() {
        return Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
    }

    /** Notifications enabled; Android 6 has no per-app query and must assume the system default. */
    static boolean notificationsEnabled(NotificationManager manager) {
        if (Build.VERSION.SDK_INT < 24) return true;
        return manager.areNotificationsEnabled();
    }

    /**
     * The per-app notification screen. Android 8 added a direct action; Android 6 and 7 only have
     * the app-info screen, which links to the same notification controls.
     */
    static boolean openNotificationSettings(Context context) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= 26) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.getPackageName()));
        }
        try {
            context.startActivity(intent);
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    /** The VPN settings screen, falling back to Settings itself where the direct action is absent. */
    static boolean openVpnSettings(Context context) {
        try {
            context.startActivity(new Intent(Settings.ACTION_VPN_SETTINGS));
            return true;
        } catch (RuntimeException unavailable) {
            try {
                context.startActivity(new Intent(Settings.ACTION_SETTINGS));
                return true;
            } catch (RuntimeException alsoUnavailable) {
                return false;
            }
        }
    }

    /**
     * A per-boot value used by the protection clock to notice a restart. Android 7 records it in
     * Settings.Global; Android 6 may not, so the kernel's boot time is the stable fallback. Both
     * values change on a reboot and stay fixed within one boot.
     */
    static int bootCount(Context context) {
        int stored = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.BOOT_COUNT, -1);
        if (stored >= 0) return stored;
        int kernelBoot = kernelBootSeconds();
        if (kernelBoot >= 0) return kernelBoot;
        throw new IllegalStateException("Boot counter unavailable");
    }

    private static int kernelBootSeconds() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("btime ")) continue;
                long seconds = Long.parseLong(line.substring(6).trim());
                return (int) (seconds & 0x7fffffffL);
            }
        } catch (Exception unavailable) {
            // Fall through to the caller's failure, which keeps commitments conservative.
        }
        return -1;
    }
}
