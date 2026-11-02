package app.hisn.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** "Keep only this" blocks every other installed browser, never an unknown package. */
public final class BrowserAllowlistTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static void main(String[] args) {
        Set<String> browsers = new LinkedHashSet<>(Arrays.asList("chrome", "firefox", "edge"));
        check(BrowserAllowlist.blockAllExcept(browsers, "chrome")
                .equals(new LinkedHashSet<>(Arrays.asList("firefox", "edge"))), "keep chrome");
        check(BrowserAllowlist.blockAllExcept(browsers, "firefox")
                .equals(new LinkedHashSet<>(Arrays.asList("chrome", "edge"))), "keep firefox");
        check(BrowserAllowlist.blockAllExcept(browsers, "not-installed").isEmpty(),
                "unknown browser blocked others");
        check(BrowserAllowlist.blockAllExcept(browsers, null).isEmpty(), "null keep blocked others");
        check(BrowserAllowlist.blockAllExcept(Collections.singletonList("chrome"), "chrome").isEmpty(),
                "single browser blocked itself");
        check(BrowserAllowlist.isOnlyOne(browsers,
                new LinkedHashSet<>(Arrays.asList("firefox", "edge"))), "one kept not detected");
        check(!BrowserAllowlist.isOnlyOne(browsers,
                new LinkedHashSet<>(Arrays.asList("chrome", "firefox", "edge"))), "all blocked still kept");
        check(!BrowserAllowlist.isOnlyOne(Collections.singletonList("chrome"),
                Collections.emptySet()), "single-browser list treated as allowlist");
        System.out.println("BrowserAllowlist tests passed: keep-only selection, unknown and single-browser cases");
    }
}
