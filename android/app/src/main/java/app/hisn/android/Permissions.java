package app.hisn.android;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import app.hisn.core.AccessibilityGrant;

/**
 * Read-only checks for the Android grants the onboarding checklist shows.
 *
 * <p>Each check reads the platform's own state. A saved switch is never used as
 * proof that a permission is on. The checklist can therefore be reopened after
 * Android revokes a grant and will show the true state.
 */
final class Permissions {
    private Permissions() { }

    /**
     * True when the guard service is enabled. The manager's cached list is asked first; a device
     * that has the service bound while its cache is stale would otherwise read as "permission
     * missing" until the process restarts, so Android's own enabled-services value is the second
     * source. That value is platform state Settings writes, never a switch Hisn saves.
     */
    static boolean accessibility(Context context) {
        if (listedByManager(context)) return true;
        return Boolean.TRUE.equals(accessibilityInSettings(context));
    }

    /** Android's enabled-services value, readable without a permission; null when it cannot be read. */
    static Boolean accessibilityInSettings(Context context) {
        try {
            ContentResolver resolver = context.getContentResolver();
            if (Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) {
                return false;
            }
            return AccessibilityGrant.holds(
                    Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                    context.getPackageName(), TextGuardService.class.getName());
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private static boolean listedByManager(Context context) {
        try {
            AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
            if (manager == null) return false;
            for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                android.content.pm.ServiceInfo service = info.getResolveInfo().serviceInfo;
                if (context.getPackageName().equals(service.packageName)
                        && TextGuardService.class.getName().equals(service.name)) return true;
            }
            return false;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    static boolean usage(Context context) {
        return AppUsage.hasAccess(context);
    }

    static boolean overlay(Context context) {
        try { return Settings.canDrawOverlays(context); }
        catch (RuntimeException unavailable) { return false; }
    }

    static boolean battery(Context context) {
        try {
            PowerManager power = context.getSystemService(PowerManager.class);
            return power != null && power.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    static boolean notifications(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) return false;
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            return manager != null && manager.areNotificationsEnabled();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    static boolean dnsRunning() {
        return DnsVpnService.state == DnsVpnService.State.STARTING
                || DnsVpnService.state == DnsVpnService.State.RUNNING
                || DnsVpnService.state == DnsVpnService.State.DEGRADED;
    }
}
