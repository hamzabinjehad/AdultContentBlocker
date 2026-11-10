package app.hisn.core;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DistractionSitesTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static Set<String> rules(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    public static void main(String[] args) {
        List<DistractionSites.Site> sites = DistractionSites.common();
        check(sites.size() == 12, "twelve named sites");
        for (DistractionSites.Site site : sites) {
            check(!site.label.isEmpty(), "site label");
            check(site.domain.equals(site.domain.toLowerCase(java.util.Locale.ROOT)), "canonical domain");
            check(DistractionSites.state(rules(), site.domain) == DistractionSites.State.NONE, "new chip is unselected");
        }

        check(DistractionSites.state(rules("youtube.com"), "youtube.com") == DistractionSites.State.DIRECT,
                "exact rule is selected");
        check(DistractionSites.state(rules("m.youtube.com"), "youtube.com") == DistractionSites.State.NONE,
                "a child rule does not select the parent");
        check(DistractionSites.state(rules("youtube.com"), "m.youtube.com") == DistractionSites.State.COVERED,
                "a parent rule covers a child");
        check(DistractionSites.state(rules("notyoutube.com"), "youtube.com") == DistractionSites.State.NONE,
                "a label suffix is not a parent");

        check(DistractionSites.add(rules(), "youtube.com").equals("youtube.com"), "add to empty list");
        check(DistractionSites.add(rules("reddit.com"), "youtube.com").equals("reddit.com\nyoutube.com"),
                "add preserves existing order");
        check(DistractionSites.add(rules("youtube.com"), "youtube.com").equals("youtube.com"),
                "add is a no-op for an exact rule");
        check(DistractionSites.add(rules("youtube.com"), "m.youtube.com").equals("youtube.com"),
                "add is a no-op under a parent rule");

        check(DistractionSites.remove(rules("reddit.com", "youtube.com"), "youtube.com").equals("reddit.com"),
                "remove exact rule");
        check(DistractionSites.remove(rules("m.youtube.com"), "youtube.com").equals("m.youtube.com"),
                "remove never takes a child rule");
        check(DistractionSites.remove(rules("youtube.com"), "m.youtube.com").equals("youtube.com"),
                "remove never takes a parent rule");

        for (String invalid : Arrays.asList("", "https://youtube.com", "*.youtube.com", "youtube", "you tube.com")) {
            try {
                DistractionSites.add(rules(), invalid);
                throw new AssertionError("invalid site accepted: " + invalid);
            } catch (IllegalArgumentException expected) {
                // The chip list is code-owned, so an invalid value is a programming error.
            }
        }
        System.out.println("DistractionSites tests passed");
    }
}
