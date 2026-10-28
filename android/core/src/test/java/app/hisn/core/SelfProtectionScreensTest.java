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
        check(!SelfProtectionScreens.isTamperScreen("com.android.systemui",
                "Hisn\nUninstall", names), "System UI treated as an uninstall surface");

        // Safe Mode prompt in the power menu; ordinary power off and restart stay available.
        String systemUi = "com.android.systemui";
        check(SelfProtectionScreens.watches(systemUi), "power menu not watched");
        check(SelfProtectionScreens.isSafeModePrompt("android",
                "Reboot to safe mode\nDo you want to reboot into safe mode?\nCancel\nOK"), "AOSP safe mode dialog");
        check(SelfProtectionScreens.isSafeModePrompt(systemUi, "Safe mode"), "Samsung safe mode button");
        // Captured from the Samsung power menu in German after long-pressing "Ausschalten".
        check(SelfProtectionScreens.isSafeModePrompt(systemUi, "Sicherer Modus,\nSicherer Modus\n"
                + "Tippe erneut darauf, um dein Telefon im sicheren Modus neu zu starten.\nAusschalten-Menü"),
                "Samsung German safe mode screen");
        check(SelfProtectionScreens.isSafeModePrompt(systemUi, "Abgesicherter Modus"), "AOSP German safe mode button");
        check(SelfProtectionScreens.isSafeModePrompt(systemUi, "الوضع الآمن"), "Arabic safe mode button");
        check(!SelfProtectionScreens.isSafeModePrompt(systemUi,
                "Power off\nRestart\nEmergency mode"), "ordinary power menu blocked");
        check(!SelfProtectionScreens.isSafeModePrompt(systemUi, "Ausschalten-Menü\nAusschalten,\nAusschalten\n"
                + "Neustart,\nNeustart\nNotruf,\nNotruf\nEinstellungen für Funktionstaste"),
                "Samsung German power menu blocked");
        check(!SelfProtectionScreens.isSafeModePrompt(systemUi,
                "Messages\nAnna: my laptop is stuck in safe mode again"), "notification mentioning safe mode blocked");
        check(!SelfProtectionScreens.isSafeModePrompt(settings, "Safe mode"), "Settings treated as power menu");
        check(!SelfProtectionScreens.isSafeModePrompt(systemUi, ""), "empty power menu blocked");

        System.out.println("SelfProtectionScreens tests passed: uninstall, App info, admin, "
                + "accessibility, Arabic and Safe Mode screens; ordinary Settings and power menu left usable");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
