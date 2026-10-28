package app.hisn.core;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure decisions for the direct (sideload) build's uninstall-screen guard.
 *
 * Without device ownership Android cannot refuse an uninstall. The direct build
 * instead leaves the system screens that would remove or disarm Hisn — the
 * uninstall confirmation, Hisn's App info page (force stop, clear data), the
 * device-admin deactivation screen and Hisn's accessibility toggle — while the
 * owner's keep-installed switch is on. This is friction, not enforcement: Safe
 * Mode turns third-party accessibility off and ADB can still remove the app.
 *
 * A screen qualifies only when it belongs to a watched system package, names
 * Hisn, and shows one of the removal or disable actions. Settings pages that
 * merely list Hisn (the apps list, notifications) stay usable.
 *
 * The guard also leaves the power menu's "reboot to safe mode" prompt, since
 * Safe Mode turns this accessibility service off. Ordinary power off and
 * restart stay available, and the hardware-key route into Safe Mode at boot
 * cannot be seen by any app.
 */
public final class SelfProtectionScreens {
    private SelfProtectionScreens() { }

    private static final Set<String> WATCHED = new HashSet<>(Arrays.asList(
            "com.android.settings", "com.google.android.settings",
            "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller", "com.android.vending"));

    /** The power menu: Samsung and recent AOSP draw it in System UI, older AOSP in the system server. */
    private static final Set<String> POWER_MENU = new HashSet<>(Arrays.asList(
            "com.android.systemui", "android"));

    /**
     * Safe Mode button or dialog titles in English, German and Arabic power menus, lower-cased.
     * Matched as a whole line, so a notification that merely mentions safe mode is not.
     */
    private static final Set<String> SAFE_MODE = new HashSet<>(Arrays.asList(
            "safe mode", "reboot to safe mode", "sicherer modus", "abgesicherter modus",
            "im abgesicherten modus neu starten", "الوضع الآمن", "الوضع الامن",
            "إعادة التشغيل في الوضع الآمن"));

    /**
     * Removal or disable actions in English, Arabic and German Settings, lower-cased.
     * "Shortcut" marks a service's own accessibility page (Samsung shows "Hisn shortcut"
     * beside the toggle rather than "Use Hisn").
     */
    private static final List<String> ACTIONS = Collections.unmodifiableList(Arrays.asList(
            "uninstall", "force stop", "clear data", "clear storage", "deactivate", "shortcut",
            "إلغاء التثبيت", "الغاء التثبيت", "فرض الإيقاف", "فرض الايقاف", "إيقاف إجباري",
            "مسح البيانات", "محو البيانات", "مسح مساحة التخزين", "إلغاء التنشيط", "إلغاء تنشيط", "اختصار",
            "deinstallieren", "stopp erzwingen", "beenden erzwingen", "daten löschen",
            "speicher löschen", "deaktivieren", "verknüpfung", "kurzbefehl"));

    /** Hisn's English and Arabic labels. A per-app language can show either in any Settings locale. */
    public static final List<String> LABELS = Collections.unmodifiableList(Arrays.asList("Hisn", "حصن"));

    /** The guard runs only while the owner keeps the keep-installed switch on. */
    public static boolean active(boolean keepInstalled) {
        return keepInstalled;
    }

    /** Metadata-only pre-check: the window belongs to a system surface that can remove or disarm Hisn. */
    public static boolean watches(String packageName) {
        return packageName != null && (WATCHED.contains(packageName) || POWER_MENU.contains(packageName));
    }

    /** True when a power-menu window offers to reboot into Safe Mode. */
    public static boolean isSafeModePrompt(String packageName, String visibleText) {
        if (packageName == null || !POWER_MENU.contains(packageName)
                || visibleText == null || visibleText.isEmpty()) return false;
        for (String line : visibleText.split("\n")) {
            // Samsung's content descriptions end in a comma ("Sicherer Modus,").
            String label = line.trim().toLowerCase(Locale.ROOT);
            if (label.endsWith(",")) label = label.substring(0, label.length() - 1).trim();
            if (SAFE_MODE.contains(label)) return true;
        }
        return false;
    }

    /**
     * True when the visible text of a watched window is one of Hisn's removal
     * or disable screens. {@code names} are Hisn's labels and package name.
     */
    public static boolean isTamperScreen(String packageName, String visibleText, Collection<String> names) {
        if (packageName == null || !WATCHED.contains(packageName) || visibleText == null || visibleText.isEmpty()) return false;
        String text = visibleText.toLowerCase(Locale.ROOT);
        boolean named = false;
        for (String name : names) {
            if (name == null || name.isEmpty()) continue;
            String own = name.toLowerCase(Locale.ROOT);
            if (!text.contains(own)) continue;
            named = true;
            // Hisn's own accessibility page: "Use Hisn" / "استخدام حصن" / "Hisn verwenden" toggle.
            if (text.contains("use " + own) || text.contains("استخدام " + own)
                    || text.contains(own + " verwenden")) return true;
        }
        if (!named) return false;
        for (String action : ACTIONS) if (text.contains(action)) return true;
        return false;
    }
}
