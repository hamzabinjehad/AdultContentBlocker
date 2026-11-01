package app.hisn.core;

import java.util.List;
import java.util.Set;

/** The browser coverage answer must never claim a layer the app does not run. */
public final class BrowserCoverageTest {
    public static void main(String[] args) {
        Set<String> blocked = Set.of("com.example.blocked");
        Set<String> inspected = Set.of("com.example.inspected");

        // Blocking wins over every other answer, even when the other layers are off.
        check(BrowserCoverage.layer("com.example.blocked", blocked, inspected, false, false)
                        == BrowserCoverage.Layer.BLOCKED,
                "blocked browser reported as uncovered");
        check(BrowserCoverage.layer("com.example.blocked", blocked, inspected, true, true)
                        == BrowserCoverage.Layer.BLOCKED,
                "blocked browser reported through another layer");

        // Inspection only covers a browser while the accessibility guard is connected.
        check(BrowserCoverage.layer("com.example.inspected", blocked, inspected, true, false)
                        == BrowserCoverage.Layer.INSPECTED,
                "inspected browser ignored while the guard runs");
        check(BrowserCoverage.layer("com.example.inspected", blocked, inspected, false, true)
                        == BrowserCoverage.Layer.DOMAINS,
                "disconnected inspection still claimed coverage");

        // DNS is the weakest layer and the only one that changes while the app runs.
        check(BrowserCoverage.layer("com.example.plain", blocked, inspected, true, true)
                        == BrowserCoverage.Layer.DOMAINS,
                "running DNS not reported");
        check(BrowserCoverage.layer("com.example.plain", blocked, inspected, true, false)
                        == BrowserCoverage.Layer.NONE,
                "stopped DNS still claimed coverage");

        // The uncovered list keeps the caller's order and contains only uncovered browsers.
        List<String> browsers = List.of("com.example.plain", "com.example.blocked", "com.example.inspected");
        check(BrowserCoverage.withoutCoverage(browsers, blocked, inspected, true, false)
                        .equals(List.of("com.example.plain")),
                "uncovered list wrong with inspection running");
        check(BrowserCoverage.withoutCoverage(browsers, blocked, inspected, false, false)
                        .equals(List.of("com.example.plain", "com.example.inspected")),
                "uncovered list wrong while the guard is disconnected");
        check(BrowserCoverage.withoutCoverage(browsers, blocked, inspected, false, true).isEmpty(),
                "running DNS left no browser covered");

        System.out.println("BrowserCoverage tests passed (layer precedence, inspection and DNS state)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
