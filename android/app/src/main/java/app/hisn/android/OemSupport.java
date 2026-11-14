package app.hisn.android;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

import app.hisn.core.DeviceFamily;

/**
 * The phone-maker parts of keeping Hisn alive.
 *
 * <p>Android's own battery setting is not the whole story on Samsung, Xiaomi, ColorOS, OriginOS,
 * EMUI and HiOS: each adds a background manager, and several hide it outside Android Settings.
 * This class classifies the build with {@link DeviceFamily}, opens the maker's own screen when it
 * resolves, and falls back to Android's app-info and battery screens. A failed launch is normal
 * (the screen was renamed or the package is absent) and never crashes or is reported as success
 * unless an activity actually started.</p>
 *
 * <p>Nothing here changes a setting by itself. The only readable states are Android's battery
 * optimization and app-info background restriction; an OEM autostart switch is confirmed by the
 * owner, not by Hisn.</p>
 */
public final class OemSupport {
    private OemSupport() { }

    /** The maker family for this build. */
    public static DeviceFamily family() {
        return DeviceFamily.classify(Build.MANUFACTURER, Build.BRAND, Build.MODEL);
    }

    /** "Samsung", "Xiaomi", "Nothing" — the maker as the phone spells it, never a guessed family. */
    public static String maker() {
        String maker = clean(Build.MANUFACTURER);
        if (maker.isEmpty()) maker = clean(Build.BRAND);
        if (maker.isEmpty()) return "Android";
        return titleCase(maker);
    }

    /** The model name when the build provides one; empty otherwise. */
    public static String model() {
        String model = clean(Build.MODEL);
        return "unknown".equalsIgnoreCase(model) ? "" : model;
    }

    /** "Android 16 · API 36", or "Android" plus the release where the SDK is missing. */
    public static String android() {
        String release = clean(Build.VERSION.RELEASE);
        if (release.isEmpty()) return "Android · API " + Build.VERSION.SDK_INT;
        return "Android " + release + " · API " + Build.VERSION.SDK_INT;
    }

    /**
     * Android's app-info "Restricted" switch. {@code TRUE} means the platform blocks Hisn's
     * background work even with battery optimization off. {@code null} below Android 9, where
     * the value does not exist, so a row is omitted instead of guessed.
     */
    public static Boolean backgroundRestricted(Context context) {
        if (Build.VERSION.SDK_INT < 28) return null;
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            return manager == null ? null : manager.isBackgroundRestricted();
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /**
     * Xiaomi's "Display pop-up windows while running in the background" (MIUI app op 10021).
     * {@code TRUE}/{@code FALSE} when MIUI answers; {@code null} on every other maker, and on a
     * Xiaomi build that does not know the op, so the row falls back to the owner's tick.
     */
    public static Boolean backgroundPopups(Context context) {
        if (family() != DeviceFamily.XIAOMI) return null;
        try {
            android.app.AppOpsManager ops = context.getSystemService(android.app.AppOpsManager.class);
            if (ops == null) return null;
            java.lang.reflect.Method check = android.app.AppOpsManager.class.getMethod(
                    "checkOpNoThrow", int.class, int.class, String.class);
            Object mode = check.invoke(ops, MIUI_OP_BACKGROUND_START, android.os.Process.myUid(),
                    context.getPackageName());
            return mode instanceof Integer && (Integer) mode == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            return null;
        }
    }

    private static final int MIUI_OP_BACKGROUND_START = 10021;

    /** Opens the maker's per-app permission page with the pop-up switch, or app info. */
    public static boolean openPopups(Context context) {
        List<Intent> intents = new ArrayList<>();
        DeviceFamily family = family();
        String app = context.getPackageName();
        if (family == DeviceFamily.XIAOMI) {
            intents.add(new Intent("miui.intent.action.APP_PERM_EDITOR")
                    .setClassName("com.miui.securitycenter",
                            "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    .putExtra("extra_pkgname", app));
            intents.add(new Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", app));
        } else if (family == DeviceFamily.VIVO) {
            intents.add(new Intent().setComponent(new ComponentName("com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"))
                    .putExtra("packagename", app));
        }
        intents.add(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)));
        return start(context, intents);
    }

    /** Opens the maker's battery / app-power screen, or Android's own fallback. */
    public static boolean openBattery(Context context) {
        return start(context, batteryIntents(context));
    }

    /** Opens the maker's launch-in-background screen, or Android's app-info fallback. */
    public static boolean openAutostart(Context context) {
        return start(context, autostartIntents(context));
    }

    /** Opens Android's app-info page for Hisn, where Battery and the restriction switch live. */
    public static boolean openAppInfo(Context context) {
        return start(context, appInfoIntents(context));
    }

    /** Opens Android's VPN list, where Hisn can be made always-on. */
    public static boolean openVpnSettings(Context context) {
        return Compat.openVpnSettings(context);
    }

    /** The battery candidates in the order they are tried. Visible for the device check. */
    public static List<Intent> batteryIntents(Context context) {
        List<Intent> intents = new ArrayList<>();
        DeviceFamily family = family();
        Uri app = packageUri(context);
        if (family == DeviceFamily.XIAOMI) {
            intents.add(new Intent()
                    .setComponent(new ComponentName("com.miui.powerkeeper",
                            "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                    .putExtra("package_name", context.getPackageName())
                    .putExtra("package_label", appLabel(context)));
        } else if (family == DeviceFamily.HUAWEI) {
            intents.add(new Intent().setComponent(new ComponentName("com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity")));
            intents.add(new Intent().setComponent(new ComponentName("com.hihonor.systemmanager",
                    "com.hihonor.systemmanager.optimize.process.ProtectActivity")));
        } else if (family == DeviceFamily.OPPO) {
            intents.add(new Intent().setComponent(new ComponentName("com.oplus.battery",
                    "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity")));
            intents.add(new Intent().setComponent(new ComponentName("com.coloros.oppoguardelf",
                    "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")));
        } else if (family == DeviceFamily.VIVO) {
            intents.add(new Intent().setComponent(new ComponentName("com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.PurviewTabActivity")));
        }
        // The standard request is the one-tap "Allow" dialog. One UI 8 does not resolve it
        // (checked on SM-G990B2 / Android 16), and Device care's power screens are guarded by
        // READ_SEARCH_INDEXABLES, so Samsung falls through to app info: its Battery row and
        // "Manage app if unused" switch are both reachable there (checked on the same phone).
        intents.add(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, app));
        // The app-info page names Battery and the background-restriction switch on every build.
        intents.add(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app));
        // The battery list is a wider fallback that still resolves on builds with no direct page.
        intents.add(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        return intents;
    }

    /** The launch-in-background candidates in the order they are tried. */
    public static List<Intent> autostartIntents(Context context) {
        List<Intent> intents = new ArrayList<>();
        DeviceFamily family = family();
        if (family == DeviceFamily.XIAOMI) {
            intents.add(new Intent().setComponent(new ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity")));
        } else if (family == DeviceFamily.OPPO) {
            intents.add(new Intent().setComponent(new ComponentName("com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity")));
            intents.add(new Intent().setComponent(new ComponentName("com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity")));
            intents.add(new Intent().setComponent(new ComponentName("com.oplus.safecenter",
                    "com.oplus.safecenter.startupapp.StartupAppListActivity")));
            // OnePlus kept its own launch manager after moving to ColorOS.
            intents.add(new Intent().setComponent(new ComponentName("com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")));
        } else if (family == DeviceFamily.VIVO) {
            intents.add(new Intent().setComponent(new ComponentName("com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")));
            intents.add(new Intent().setComponent(new ComponentName("com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")));
        } else if (family == DeviceFamily.HUAWEI) {
            intents.add(new Intent().setComponent(new ComponentName("com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")));
            intents.add(new Intent().setComponent(new ComponentName("com.huawei.systemmanager",
                    "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")));
            intents.add(new Intent().setComponent(new ComponentName("com.hihonor.systemmanager",
                    "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")));
        }
        // Every maker's app-info page is reachable, so the row still leads somewhere useful
        // when the hidden screen has been renamed in a ROM update.
        intents.add(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)));
        return intents;
    }

    private static List<Intent> appInfoIntents(Context context) {
        List<Intent> intents = new ArrayList<>();
        intents.add(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)));
        intents.add(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
        return intents;
    }

    private static Uri packageUri(Context context) {
        return Uri.parse("package:" + context.getPackageName());
    }

    private static String appLabel(Context context) {
        try {
            PackageManager manager = context.getPackageManager();
            return manager.getApplicationLabel(
                    manager.getApplicationInfo(context.getPackageName(), 0)).toString();
        } catch (RuntimeException | android.content.pm.PackageManager.NameNotFoundException unavailable) {
            return "Hisn";
        }
    }

    /**
     * Tries each intent in turn. The first activity that starts wins; a renamed, missing or
     * non-exported screen throws and the next candidate is tried. Never returns true for a launch
     * that did not happen.
     */
    private static boolean start(Context context, List<Intent> candidates) {
        for (Intent intent : candidates) {
            if (intent == null) continue;
            if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
                return true;
            } catch (RuntimeException unavailable) {
                // The next candidate is tried; the last one is Android's own app-info page.
            }
        }
        return false;
    }

    private static String clean(String value) {
        if (value == null) return "";
        StringBuilder clean = new StringBuilder();
        for (String word : value.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            if (clean.length() > 0) clean.append(' ');
            clean.append(word);
        }
        return clean.toString();
    }

    /** "samsung" and "HUAWEI" read as a name; an already mixed name such as "OnePlus" is kept. */
    private static String titleCase(String value) {
        boolean upper = true;
        boolean lower = true;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isLowerCase(character)) upper = false;
            if (Character.isUpperCase(character)) lower = false;
        }
        if (!upper && !lower) return value;
        StringBuilder title = new StringBuilder(value.length());
        boolean start = true;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (start) character = Character.toUpperCase(character);
            else character = Character.toLowerCase(character);
            title.append(character);
            start = character == ' ';
        }
        return title.toString();
    }
}
