package app.hisn.android;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;

/**
 * The whole screen in gray: Android's own Color correction › Grayscale filter, switched from Hisn.
 *
 * <p>Changing it needs WRITE_SECURE_SETTINGS, which only adb (or a device owner) can grant. Without it
 * Hisn opens Accessibility settings and says where the switch is. While a commitment runs, turning gray
 * off waits until it ends, and the guard puts it back if it was switched off in Settings.
 */
final class ScreenGray {
    static final String ENABLED = "accessibility_display_daltonizer_enabled";
    static final String MODE = "accessibility_display_daltonizer";
    /** The daltonizer mode Settings calls Grayscale. */
    static final int MONOCHROMACY = 0;
    static final String GRANT = "adb shell pm grant " + BuildConfig.APPLICATION_ID + " " + Manifest.permission.WRITE_SECURE_SETTINGS;
    private static final String PREFS = "screen_gray";
    private static final String WANTED = "wanted";

    private ScreenGray() { }

    static boolean canControl(Context context) {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean isGray(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(), ENABLED, 0) == 1
                && Settings.Secure.getInt(context.getContentResolver(), MODE, -1) == MONOCHROMACY;
    }

    /** Hisn's own choice. Nothing is changed while it is off, so a filter set in Settings stays. */
    static boolean wanted(Context context) {
        return prefs(context).getBoolean(WANTED, false);
    }

    /** False when the change is refused: no permission, or loosening during a commitment. */
    static boolean set(Context context, boolean on, boolean committed) {
        if (!on && committed && wanted(context)) return false;
        if (!canControl(context)) return false;
        try {
            Settings.Secure.putInt(context.getContentResolver(), ENABLED, on ? 1 : 0);
            if (on) Settings.Secure.putInt(context.getContentResolver(), MODE, MONOCHROMACY);
        } catch (SecurityException revoked) { return false; }
        prefs(context).edit().putBoolean(WANTED, on).apply();
        return true;
    }

    /** Puts gray back if Hisn wants it and something turned it off. */
    static void reassert(Context context) {
        try {
            if (wanted(context) && canControl(context) && !isGray(context)) {
                Settings.Secure.putInt(context.getContentResolver(), ENABLED, 1);
                Settings.Secure.putInt(context.getContentResolver(), MODE, MONOCHROMACY);
            }
        } catch (RuntimeException unavailable) { /* Retry on the next check. */ }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
