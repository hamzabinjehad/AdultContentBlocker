package app.hisn.core;

import java.util.Arrays;
import java.util.List;

/** Screen matrix for the direct build's removals, disable, Safe Mode and install guard. */
public final class SelfProtectionScreensTest {
    public static void main(String[] args) {
        List<String> names = Arrays.asList("Hisn", "حصن", "app.hisn.android");
        String settings = "com.android.settings";
        String installer = "com.google.android.packageinstaller";

        check(!SelfProtectionScreens.active(false, false), "guard ran without a switch");
        check(SelfProtectionScreens.active(true, false), "guard off with the keep switch");
        check(SelfProtectionScreens.active(false, true), "guard off with the install switch");
        check(SelfProtectionScreens.active(true, true), "guard off with both switches");

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
        check(SelfProtectionScreens.watches(settings, true, false), "Settings not watched with the keep switch");
        check(!SelfProtectionScreens.watches(settings, false, true), "Settings watched with only the install switch");
        check(SelfProtectionScreens.watches(installer, false, true), "installer not watched with the install switch");
        check(!SelfProtectionScreens.watches("com.android.systemui", false, true),
                "power menu watched with only the install switch");
        check(!SelfProtectionScreens.isTamperScreen("com.android.systemui",
                "Hisn\nUninstall", names), "System UI treated as an uninstall surface");

        // New app installs: the package installer confirmation and any store page that offers Install.
        check(SelfProtectionScreens.isInstallScreen(installer,
                "Some Browser\nDo you want to install this app?\nCancel\nInstall", names), "installer confirmation");
        check(SelfProtectionScreens.isInstallScreen("com.samsung.android.packageinstaller",
                "Some VPN\nهل تريد تثبيت هذا التطبيق؟\nإلغاء\nتثبيت", names), "Arabic installer confirmation");
        check(SelfProtectionScreens.isInstallScreen("com.google.android.packageinstaller",
                "Some Browser\nMöchtest du diese App installieren?\nAbbrechen\nInstallieren", names),
                "German installer confirmation");
        check(SelfProtectionScreens.isInstallScreen("com.android.vending",
                "Some VPN\nAbout this app\nInstall\nReviews", names), "Play detail page");
        check(SelfProtectionScreens.isInstallScreen("com.android.vending",
                "Some VPN\nلمحة عن هذا التطبيق\nتثبيت\nالمراجعات", names), "Arabic Play detail page");
        check(SelfProtectionScreens.isInstallScreen("com.android.vending",
                "Some VPN\nInfo zu dieser App\nInstallieren\nBewertungen", names), "German Play detail page");
        check(!SelfProtectionScreens.isInstallScreen(installer,
                "Some App\nUpdate\nCancel", names), "app update treated as install");
        check(!SelfProtectionScreens.isInstallScreen(installer,
                "Hisn\nDo you want to install this app?\nCancel\nInstall", names), "Hisn reinstall blocked");
        check(SelfProtectionScreens.isInstallScreen("com.android.vending",
                "Apps\nInstall\nGames", names), "store home install not blocked");
        check(SelfProtectionScreens.isInstallScreen("com.android.vending",
                "Search results\nSome VPN\nInstall\nSome Browser\nInstall", names),
                "store search install not blocked");
        check(!SelfProtectionScreens.isInstallScreen("com.android.vending",
                "Updates\nSome App\nUpdate\nAnother App\nUpdate", names), "store updates blocked");
        check(SelfProtectionScreens.isInstallScreen("com.android.vending",
                "Some App\nUpdate\nAnother App\nInstall", names), "mixed store page install missed");
        check(SelfProtectionScreens.isInstallScreen("com.android.vending",
                "Hisn\nInstall\nOpen", names), "store install page for Hisn missed");
        check(!SelfProtectionScreens.isInstallScreen("com.android.vending",
                "Hisn\nUpdate\nOpen", names), "Hisn update page blocked");
        check(!SelfProtectionScreens.isInstallScreen("com.android.settings",
                "Install unknown apps\nChrome\nAllow from this source", names), "install settings blocked");
        check(!SelfProtectionScreens.isInstallScreen("com.example.notes",
                "Install", names), "unwatched install surface");
        check(!SelfProtectionScreens.isInstallScreen(installer,
                "Some App\nUninstall\nCancel", names), "another app's uninstall treated as install");

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

        // Classification picks the page's reason and respects each switch independently.
        check(SelfProtectionScreens.classify(settings,
                "App info\nHisn\nUninstall\nForce stop", names, true, false)
                == SelfProtectionScreens.Guard.UNINSTALL, "uninstall not classified");
        check(SelfProtectionScreens.classify(settings,
                "Hisn\nUse Hisn\nHisn shortcut", names, true, false)
                == SelfProtectionScreens.Guard.DISABLE, "accessibility toggle not classified");
        check(SelfProtectionScreens.classify(systemUi, "Safe mode", names, true, false)
                == SelfProtectionScreens.Guard.SAFE_MODE, "safe mode not classified");
        check(SelfProtectionScreens.isVpnTamperScreen(settings,
                "VPN\nHisn\nDisconnect\nAlways-on VPN", names), "VPN screen not detected");
        check(SelfProtectionScreens.classify(settings,
                "VPN\nHisn\nDisconnect\nAlways-on VPN", names, true, false)
                == SelfProtectionScreens.Guard.VPN, "VPN screen not classified");
        check(!SelfProtectionScreens.isVpnTamperScreen(settings,
                "VPN\nWork profile\nDisconnect", names), "other VPN guarded");
        check(SelfProtectionScreens.classify(settings,
                "VPN\nHisn\nDisconnect", names, false, true)
                == SelfProtectionScreens.Guard.NONE, "VPN guarded without its switch");
        check(SelfProtectionScreens.classify(installer,
                "Some Browser\nDo you want to install this app?\nCancel\nInstall", names, false, true)
                == SelfProtectionScreens.Guard.INSTALL, "install not classified");
        check(SelfProtectionScreens.classify(installer,
                "Some Browser\nDo you want to install this app?\nCancel\nInstall", names, true, false)
                == SelfProtectionScreens.Guard.NONE, "install guarded without its switch");
        check(SelfProtectionScreens.classify(settings,
                "App info\nHisn\nUninstall\nForce stop", names, false, true)
                == SelfProtectionScreens.Guard.NONE, "uninstall guarded without its switch");
        check(SelfProtectionScreens.classify(settings,
                "Apps\nCalculator\nHisn\nMaps", names, true, true)
                == SelfProtectionScreens.Guard.NONE, "ordinary Settings classified");

        System.out.println("SelfProtectionScreens tests passed: uninstall, App info, admin, "
                + "accessibility, install, VPN, Arabic and Safe Mode screens; ordinary Settings, "
                + "app updates and Hisn's own update page left usable");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
