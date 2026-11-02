package app.hisn.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure decisions for the direct (sideload) build's protection-screen guard.
 *
 * Without device ownership Android cannot refuse an uninstall. The direct build
 * instead leaves the system screens that would remove or disarm Hisn — the
 * uninstall confirmation, Hisn's App info page (force stop, clear data), the
 * device-admin deactivation screen and Hisn's accessibility toggle — while the
 * owner's keep-installed switch is on. A second opt-in also leaves the package
 * installer's confirmation for a new app, because a new browser or VPN can
 * bypass the layers the owner turned on.
 *
 * This is friction, not enforcement: Safe Mode turns third-party accessibility
 * off, the system Home button always works and ADB can still remove the app.
 *
 * A screen qualifies only when it belongs to a watched system package and shows
 * one of the actions above. Settings pages that merely list Hisn (the apps
 * list, notifications) stay usable, app updates stay available, and the guard
 * never touches the dialler or an emergency screen.
 */
public final class SelfProtectionScreens {
    private SelfProtectionScreens() { }

    /** What the guard found on screen; {@link #NONE} means the window is left alone. */
    public enum Guard {
        NONE,
        /** The uninstall confirmation for Hisn. */
        UNINSTALL,
        /** App info, clear data, device-admin deactivation or Hisn's accessibility toggle. */
        DISABLE,
        /** The power menu's Safe Mode prompt. */
        SAFE_MODE,
        /** VPN settings that can disconnect or reconfigure Hisn's DNS layer. */
        VPN,
        /** The package installer's confirmation for a new app, or a store page that offers Install. */
        INSTALL
    }

    private static final Set<String> WATCHED = new HashSet<>(Arrays.asList(
            "com.android.settings", "com.google.android.settings",
            "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller", "com.android.vending"));

    /** Packages that show a new-app install confirmation. */
    private static final Set<String> INSTALL_SURFACES = new HashSet<>(Arrays.asList(
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

    /** VPN actions that can remove Hisn's DNS layer, in English, Arabic and German. */
    private static final List<String> VPN_ACTIONS = Collections.unmodifiableList(Arrays.asList(
            "disconnect", "forget", "forget vpn", "always-on vpn", "always on vpn",
            "block connections without vpn", "edit vpn", "vpn settings", "settings",
            "قطع الاتصال", "نسيان", "نسيان VPN", "VPN دائم", "VPN دائمًا",
            "حظر الاتصالات بدون VPN", "تعديل VPN", "إعدادات VPN", "إعدادات",
            "trennen", "vergessen", "VPN immer aktiv", "Verbindungen ohne VPN blockieren",
            "VPN bearbeiten", "VPN-einstellungen", "einstellungen"));

    /** Whole-line install actions. "Install unknown apps" is a setting, not an install. */
    private static final Set<String> INSTALL_ACTIONS = new HashSet<>(Arrays.asList(
            "install", "install app", "install now", "install the app",
            "تثبيت", "تثبيت التطبيق", "تثبيت الآن",
            "installieren", "app installieren", "jetzt installieren"));

    /** Whole-line update actions: an update stays allowed, including Hisn's own. */
    private static final Set<String> UPDATE_ACTIONS = new HashSet<>(Arrays.asList(
            "update", "update app", "تحديث", "تحديث التطبيق", "aktualisieren", "app aktualisieren"));

    /** Whole-line uninstall actions; an uninstall screen is not an install screen. */
    private static final Set<String> UNINSTALL_ACTIONS = new HashSet<>(Arrays.asList(
            "uninstall", "إلغاء التثبيت", "الغاء التثبيت", "deinstallieren"));

    private static final Set<String> CANCEL_ACTIONS = new HashSet<>(Arrays.asList(
            "cancel", "إلغاء", "الغاء", "abbrechen"));

    /** Package-installer confirmation text in English, Arabic and German. */
    private static final List<String> INSTALL_CONFIRM = Collections.unmodifiableList(Arrays.asList(
            "do you want to install", "möchtest du diese app installieren",
            "هل تريد تثبيت", "سيحصل هذا التطبيق على", "möchte diese app installieren"));

    /** Hisn's English and Arabic labels. A per-app language can show either in any Settings locale. */
    public static final List<String> LABELS = Collections.unmodifiableList(Arrays.asList("Hisn", "حصن"));

    /** The guard runs while either owner switch is on. */
    public static boolean active(boolean keepInstalled, boolean installLock) {
        return keepInstalled || installLock;
    }

    /** Metadata-only pre-check: the window belongs to a system surface that can remove or disarm Hisn. */
    public static boolean watches(String packageName) {
        return packageName != null && (WATCHED.contains(packageName) || POWER_MENU.contains(packageName));
    }

    /**
     * The same pre-check narrowed to the switches that are on, so an install-only
     * switch never causes Hisn to scan a Settings or power-menu window it cannot
     * act on.
     */
    public static boolean watches(String packageName, boolean keepInstalled, boolean installLock) {
        if (packageName == null) return false;
        if (keepInstalled && (WATCHED.contains(packageName) || POWER_MENU.contains(packageName))) return true;
        return installLock && INSTALL_SURFACES.contains(packageName);
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

    /** True when a Settings VPN screen names Hisn and offers to disconnect or reconfigure it. */
    public static boolean isVpnTamperScreen(String packageName, String visibleText, Collection<String> names) {
        if (packageName == null || !WATCHED.contains(packageName)
                || visibleText == null || visibleText.isEmpty()) return false;
        String text = visibleText.toLowerCase(Locale.ROOT);
        if (!text.contains("vpn")) return false;
        boolean named = false;
        for (String name : names) {
            if (name != null && !name.isEmpty() && text.contains(name.toLowerCase(Locale.ROOT))) {
                named = true;
                break;
            }
        }
        if (!named) return false;
        for (String action : VPN_ACTIONS) if (text.contains(action)) return true;
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

    /**
     * True when the window is the package installer's confirmation for a new
     * app, or any store page that offers an Install action (the store home, a
     * search result or an app page). The store has no separate confirmation
     * step, so an Install action anywhere on the page is enough; a page that
     * only updates or opens apps is not an install page. A package installer
     * screen that names Hisn is excluded, so reinstalling Hisn from its own APK
     * stays possible.
     */
    public static boolean isInstallScreen(String packageName, String visibleText, Collection<String> names) {
        if (packageName == null || !INSTALL_SURFACES.contains(packageName)
                || visibleText == null || visibleText.isEmpty()) return false;
        String text = visibleText.toLowerCase(Locale.ROOT);
        List<String> lines = lines(visibleText);
        if ("com.android.vending".equals(packageName)) return hasLine(lines, INSTALL_ACTIONS);
        if (hasLine(lines, UPDATE_ACTIONS) || hasLine(lines, UNINSTALL_ACTIONS)) return false;
        if (!hasLine(lines, INSTALL_ACTIONS)) return false;
        for (String name : names) {
            if (name == null || name.isEmpty()) continue;
            if (text.contains(name.toLowerCase(Locale.ROOT))) return false;
        }
        return hasLine(lines, CANCEL_ACTIONS) || contains(text, INSTALL_CONFIRM);
    }

    /**
     * What, if anything, to close on this window. {@code keepInstalled} covers
     * Hisn's own removal and disable screens; {@code installLock} additionally
     * covers a new app install. Both off means nothing is read.
     */
    public static Guard classify(String packageName, String visibleText, Collection<String> names,
            boolean keepInstalled, boolean installLock) {
        if (packageName == null || visibleText == null || visibleText.isEmpty()) return Guard.NONE;
        if (keepInstalled) {
            if (isSafeModePrompt(packageName, visibleText)) return Guard.SAFE_MODE;
            if (isVpnTamperScreen(packageName, visibleText, names)) return Guard.VPN;
            if (isTamperScreen(packageName, visibleText, names)) {
                return contains(visibleText.toLowerCase(Locale.ROOT), UNINSTALL_ACTIONS)
                        ? Guard.UNINSTALL : Guard.DISABLE;
            }
        }
        if (installLock && isInstallScreen(packageName, visibleText, names)) return Guard.INSTALL;
        return Guard.NONE;
    }

    private static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = raw.trim().toLowerCase(Locale.ROOT);
            while (!line.isEmpty() && ",:;.،؛".indexOf(line.charAt(line.length() - 1)) >= 0) {
                line = line.substring(0, line.length() - 1).trim();
            }
            if (!line.isEmpty()) lines.add(line);
        }
        return lines;
    }

    private static boolean hasLine(List<String> lines, Set<String> labels) {
        for (String line : lines) if (labels.contains(line)) return true;
        return false;
    }

    private static boolean contains(String text, Collection<String> phrases) {
        for (String phrase : phrases) if (text.contains(phrase)) return true;
        return false;
    }
}
