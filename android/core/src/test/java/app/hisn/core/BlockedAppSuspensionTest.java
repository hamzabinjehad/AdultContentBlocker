package app.hisn.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Blocked-app suspension decisions, testable without Android. */
public final class BlockedAppSuspensionTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    public static void main(String[] args) {
        check(!BlockedAppSuspension.shouldApply(false, true, true, true, 0), "ordinary install suspends");
        check(!BlockedAppSuspension.shouldApply(true, false, true, true, 0), "lockdown off suspends");
        check(!BlockedAppSuspension.shouldApply(true, true, false, true, 0), "request off suspends");
        check(!BlockedAppSuspension.shouldApply(true, true, true, false, 0), "idle owner suspends");
        check(!BlockedAppSuspension.shouldApply(true, true, true, false, -1), "negative remaining suspends");
        check(BlockedAppSuspension.shouldApply(true, true, true, true, 0), "keep did not suspend");
        check(BlockedAppSuspension.shouldApply(true, true, true, false, 1), "commitment did not suspend");

        Set<String> installed = set("browser.a", "browser.b", "other.app");
        Set<String> blocked = set("browser.a", "browser.b", "not.installed");
        Set<String> wanted = BlockedAppSuspension.desired(true, true, true, true, 0, blocked, installed);
        check(wanted.equals(set("browser.a", "browser.b")), "wanted set wrong");
        check(!wanted.contains("not.installed"), "uninstalled package wanted");
        check(!wanted.contains("other.app"), "unblocked package wanted");
        check(BlockedAppSuspension.desired(true, true, false, true, 0, blocked, installed).isEmpty(),
                "request off still wanted packages");
        check(BlockedAppSuspension.desired(false, true, true, true, 0, blocked, installed).isEmpty(),
                "ordinary install still wanted packages");

        // Only the difference is applied, in both directions.
        check(BlockedAppSuspension.toSuspend(set("a", "b"), set("b")).equals(set("a")),
                "toSuspend wrong");
        check(BlockedAppSuspension.toResume(set("b"), set("a", "b")).equals(set("a")),
                "toResume wrong");
        check(BlockedAppSuspension.toSuspend(set("a"), set("a")).isEmpty(), "already suspended repeated");
        check(BlockedAppSuspension.toResume(set("a"), set("a")).isEmpty(), "wanted package resumed");

        check(BlockedAppSuspension.matches(set("a", "b"), set("a", "b")), "identical sets did not match");
        check(!BlockedAppSuspension.matches(set("a", "b"), set("a")), "missing suspension matched");
        check(!BlockedAppSuspension.matches(set("a"), set("a", "b")), "extra suspension matched");
        check(BlockedAppSuspension.matches(Collections.emptySet(), Collections.emptySet()),
                "empty sets did not match");

        System.out.println("BlockedAppSuspension tests passed: opt-in matrix, installed intersection, "
                + "two-way diff and exact readback");
    }
}
