package app.hisn.core;

public final class ControlledBrowsersTest {
    private static void check(boolean value, String label) { if (!value) throw new AssertionError("Controlled browsers: " + label); }
    public static void main(String[] args) {
        check(ControlledBrowsers.controlled().contains("com.android.chrome"), "Chrome is controlled");
        check(!ControlledBrowsers.blocks(true, "com.android.chrome", true), "Chrome stays usable");
        check(ControlledBrowsers.blocks(true, "org.mozilla.firefox", true), "another browser is blocked");
        check(ControlledBrowsers.blocks(true, "com.sec.android.app.sbrowser", true), "Samsung Internet is blocked");
        check(!ControlledBrowsers.blocks(false, "org.mozilla.firefox", true), "nothing is blocked while the rule is off");
        check(!ControlledBrowsers.blocks(true, "com.example.notes", false), "an app that is not a browser is left alone");
        check(!ControlledBrowsers.blocks(true, null, true), "no package is not an app");
        for (String browser : ControlledBrowsers.controlled()) {
            check(PrivateBrowsing.watches(browser) && ShortVideoSurfaces.urlBarViewId(browser) != null,
                    "a controlled browser has both markers: " + browser);
        }
        check(ControlledBrowsers.refusesChange(true, true, false) && !ControlledBrowsers.refusesChange(true, false, true)
                && !ControlledBrowsers.refusesChange(false, true, false), "a lock only refuses turning it off");
        System.out.println("ControlledBrowsers tests passed (Chrome kept, other browsers blocked, lock)");
    }
}
