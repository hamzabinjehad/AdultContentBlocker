package app.hisn.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ScanScopeTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError("Scan scope: " + label);
    }

    public static void main(String[] args) {
        Set<String> chosen = new LinkedHashSet<>(Arrays.asList("org.example.browser"));
        check(!ScanScope.all(chosen), "a chosen list is not every app");
        check(ScanScope.covers(chosen, "org.example.browser"), "a chosen app is covered");
        check(!ScanScope.covers(chosen, "org.example.other"), "an unchosen app is not covered");
        check(!ScanScope.covers(null, "org.example.other"), "no list covers nothing");
        check(!ScanScope.covers(chosen, null) && !ScanScope.covers(chosen, ""), "no name is not covered");

        Set<String> every = ScanScope.with(chosen, true);
        check(ScanScope.all(every), "every app is on");
        check(ScanScope.covers(every, "org.example.other"), "every app covers an unchosen app");
        check(ScanScope.covers(every, "org.example.browser"), "every app still covers the chosen app");
        check(!ScanScope.covers(every, ScanScope.ALL_APPS), "the reserved entry is never an app");
        check(ScanScope.chosen(every) == 1 && ScanScope.chosen(chosen) == 1 && ScanScope.chosen(null) == 0,
                "the reserved entry is not counted as a chosen app");
        check(every.containsAll(chosen), "turning every app on only adds, so a lock allows it");
        check(!chosen.contains(ScanScope.ALL_APPS), "with() does not change its input");

        Set<String> back = ScanScope.with(every, false);
        check(back.equals(chosen), "turning every app off keeps the chosen apps");
        check(!back.containsAll(every), "turning every app off removes an entry, so a lock refuses it");
        check(ScanScope.with(null, true).equals(Collections.singleton(ScanScope.ALL_APPS)), "null list");

        check(ScanScope.ALL_APPS.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"),
                "the reserved entry passes the saved package-name rule");

        Set<String> expanded = ScanScope.expand(every, Arrays.asList("a.b", "c.d"));
        check(expanded.contains("a.b") && expanded.contains("c.d") && !expanded.contains(ScanScope.ALL_APPS),
                "every app expands to the candidates");
        check(ScanScope.expand(chosen, Arrays.asList("a.b")) == chosen, "a chosen list does not expand");
        System.out.println("ScanScope tests passed (chosen, every app, lock direction and expansion)");
    }
}
