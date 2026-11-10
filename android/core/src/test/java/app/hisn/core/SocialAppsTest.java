package app.hisn.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SocialAppsTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    public static void main(String[] args) {
        List<SocialApps.Entry> apps = SocialApps.common();
        check(apps.size() == 12, "twelve named social packages");
        Set<String> packages = new HashSet<>();
        for (SocialApps.Entry app : apps) {
            check(app.label != null && !app.label.isEmpty(), "app label");
            check(app.packageName.matches("[a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)+"), "Android package name");
            check(packages.add(app.packageName), "package names are unique");
        }
        check(packages.contains("com.twitter.android"), "X");
        check(packages.contains("com.instagram.android"), "Instagram");
        check(packages.contains("com.zhiliaoapp.musically")
                && packages.contains("com.ss.android.ugc.trill"), "both TikTok package names");
        check(packages.contains("xyz.blueskyweb.app"), "Bluesky");
        check(packages.contains("com.instagram.barcelona"), "Threads");

        // Only installed apps are offered, in the list's stable order.
        List<SocialApps.Entry> installed = SocialApps.installed(
                set("com.instagram.android", "xyz.blueskyweb.app", "com.example.other"));
        check(installed.size() == 2 && installed.get(0).packageName.equals("com.instagram.android")
                && installed.get(1).packageName.equals("xyz.blueskyweb.app"),
                "installed filters and keeps the common order");
        check(SocialApps.installed(set()).isEmpty(), "nothing installed means nothing offered");
        check(SocialApps.installed(null).isEmpty(), "a missing package set is not a crash");

        // The preset adds, never replaces or unselects.
        Set<String> chosen = new LinkedHashSet<>(Arrays.asList("com.example.already"));
        int added = SocialApps.select(set("com.instagram.android", "com.reddit.frontpage"), chosen, 20);
        check(added == 2, "both installed social apps are added");
        check(chosen.contains("com.example.already"), "an existing choice is preserved");
        check(chosen.contains("com.instagram.android") && chosen.contains("com.reddit.frontpage"),
                "the social apps are now chosen");
        check(SocialApps.select(set("com.instagram.android", "com.reddit.frontpage"), chosen, 20) == 0,
                "tapping again adds nothing");
        check(chosen.size() == 3, "tapping again leaves the list unchanged");

        // A limit is respected and never exceeded.
        Set<String> full = new LinkedHashSet<>(Arrays.asList("a.one", "a.two"));
        int limited = SocialApps.select(set("com.instagram.android", "com.twitter.android"), full, 3);
        check(limited == 1 && full.size() == 3, "the chooser limit is never exceeded");
        Set<String> atLimit = new LinkedHashSet<>(Arrays.asList("a.one", "a.two", "a.three"));
        check(SocialApps.select(set("com.instagram.android"), atLimit, 3) == 0, "a full list adds nothing");
        check(SocialApps.select(set("com.instagram.android"), new LinkedHashSet<>(), 0) == 0,
                "a zero limit adds nothing");
        check(SocialApps.select(null, new LinkedHashSet<>(), 20) == 0, "a missing package set adds nothing");

        // Order and membership are deterministic across calls.
        Set<String> first = new LinkedHashSet<>();
        Set<String> second = new LinkedHashSet<>();
        SocialApps.select(set("com.snapchat.android", "com.twitter.android"), first, 20);
        SocialApps.select(set("com.twitter.android", "com.snapchat.android"), second, 20);
        check(first.equals(second), "selection does not depend on the installed set's order");
        check(first.iterator().next().equals("com.twitter.android"), "the common order wins");

        System.out.println("SocialApps tests passed");
    }
}
