package app.hisn.core;

import java.util.List;

public final class PrivateBrowsingTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(PrivateBrowsing.watches("com.android.chrome"), "Chrome is covered");
        check(PrivateBrowsing.watches("com.chrome.beta") && PrivateBrowsing.watches("com.chrome.canary"),
                "Chrome's other channels are covered");
        check(!PrivateBrowsing.watches("org.mozilla.firefox") && !PrivateBrowsing.watches(null)
                && !PrivateBrowsing.watches(""), "nothing unverified or empty is claimed");

        List<String> ids = PrivateBrowsing.markerViewIds("com.android.chrome");
        check(ids.contains("com.android.chrome:id/location_bar_incognito_badge"),
                "the toolbar badge, present on every incognito page");
        check(ids.contains("com.android.chrome:id/new_tab_incognito_container"),
                "the incognito new-tab page");
        check(PrivateBrowsing.markerViewIds("com.chrome.dev").get(0).startsWith("com.chrome.dev:id/"),
                "IDs are qualified with the browser's own package");
        check(PrivateBrowsing.markerViewIds("org.example").isEmpty(), "an unknown app has no markers");
        check(PrivateBrowsing.browsers().size() == 4, "four Chrome channels");

        check(PrivateBrowsing.refusesChange(true, true, false), "a lock refuses turning it off");
        check(!PrivateBrowsing.refusesChange(true, false, true), "a lock allows turning it on");
        check(!PrivateBrowsing.refusesChange(false, true, false), "without a lock it can be turned off");
        System.out.println("PrivateBrowsing tests passed (Chrome channels, markers, unknown apps, lock)");
    }
}
