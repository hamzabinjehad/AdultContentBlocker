package app.hisn.core;

import java.util.Arrays;
import java.util.List;

/** Screen matrix for the direct build's uninstall-screen guard. */
public final class SelfProtectionScreensTest {
    public static void main(String[] args) {
        List<String> names = Arrays.asList("Hisn", "حصن", "app.hisn.android");
        String settings = "com.android.settings";

        check(!SelfProtectionScreens.active(false), "guard ran without the switch");
        check(SelfProtectionScreens.active(true), "guard off with the switch");

        // Removal and disable screens that name Hisn.
        check(SelfProtectionScreens.isTamperScreen("com.google.android.packageinstaller",
                "Hisn\nDo you want to uninstall this app?\nCancel\nOK", names), "uninstall dialog");
        check(SelfProtectionScreens.isTamperScreen("com.samsung.android.packageinstaller",
                "Uninstall Hisn?\nCancel\nUninstall", names), "Samsung uninstall dialog");
        check(SelfProtectionScreens.isTamperScreen(settings,
                "App info\nHisn\nOpen\nUninstall\nForce stop", names), "App info page");
        check(SelfProtectionScreens.isTamperScreen(settings,
                "Storage\nHisn\nClear data\nClear cache", names), "storage page");
        check(SelfProtectionScreens.isTamperScreen(settings,
                "Device admin app\nHisn\nDeactivate this device admin app", names), "admin deactivation");
        check(SelfProtectionScreens.isTamperScreen(settings,
                "Hisn\nUse Hisn\nHisn shortcut", names), "accessibility toggle");
        check(SelfProtectionScreens.isTamperScreen(settings,
                "معلومات التطبيق\nحصن\nإلغاء التثبيت\nفرض الإيقاف", names), "Arabic App info");
        check(SelfProtectionScreens.isTamperScreen(settings,
                "حصن\nاستخدام حصن", names), "Arabic accessibility toggle");
        // Captured from a Samsung Galaxy S21 FE (Android 16) in German with Hisn's app language Arabic.
        check(SelfProtectionScreens.isTamperScreen(settings,
                "App-Info\nحصن\nInstalliert\nÖffnen\nDeinstallieren\nStopp erzwingen", names), "German App info");
        check(SelfProtectionScreens.isTamperScreen("com.google.android.packageinstaller",
                "حصن\nMöchtest du diese App deinstallieren?\nAbbrechen\nOK", names), "German uninstall dialog");
        check(SelfProtectionScreens.isTamperScreen(settings,
                "Speicherplatz\nحصن\nDaten löschen\nCache leeren", names), "German storage page");
        check(SelfProtectionScreens.isTamperScreen(settings,
                "حصن\nEin\nحصن-Shortcut\nEinstellungen\nApp-Info", names), "Samsung accessibility page");
        check(SelfProtectionScreens.isTamperScreen(settings,
                "Hisn\nOn\nHisn shortcut", SelfProtectionScreens.LABELS), "English Samsung accessibility page");
        check(!SelfProtectionScreens.isTamperScreen(settings,
                "Installierte Apps\nBlockerHero\nAus\nHisn Visual Test\nEin\nحصن\nEin", names),
                "accessibility app list blocked");
        check(SelfProtectionScreens.isTamperScreen("com.android.vending",
                "Hisn\nUninstall\nOpen", names), "Play Store page");

        // Ordinary screens stay usable.
        check(!SelfProtectionScreens.isTamperScreen(settings,
                "Apps\nCalculator\nHisn\nMaps", names), "apps list blocked");
        check(!SelfProtectionScreens.isTamperScreen(settings,
                "App info\nMaps\nUninstall\nForce stop", names), "another app's App info blocked");
        check(!SelfProtectionScreens.isTamperScreen(settings,
                "Accessibility\nTalkBack\nUse TalkBack", names), "another service's toggle blocked");
        check(!SelfProtectionScreens.isTamperScreen("com.example.notes",
                "Hisn\nUninstall", names), "unwatched app blocked");
        check(!SelfProtectionScreens.isTamperScreen(settings, "", names), "empty window blocked");
        check(!SelfProtectionScreens.watches(null), "null package watched");

        System.out.println("SelfProtectionScreens tests passed: uninstall, App info, admin, "
                + "accessibility and Arabic screens; ordinary Settings pages left usable");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
