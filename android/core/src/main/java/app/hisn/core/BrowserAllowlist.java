package app.hisn.core;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** The one-tap "keep only this browser" selection for the browser dialog. */
public final class BrowserAllowlist {
    private BrowserAllowlist() { }

    /**
     * Every installed browser except {@code keep}. A browser not in the list is
     * not invented; the caller passes the same list it showed to the owner.
     */
    public static Set<String> blockAllExcept(Collection<String> browsers, String keep) {
        Set<String> result = new LinkedHashSet<>();
        if (keep == null || !browsers.contains(keep)) return result;
        for (String browser : browsers) {
            if (!keep.equals(browser)) result.add(browser);
        }
        return result;
    }

    /** True when exactly one listed browser is not blocked and there are at least two. */
    public static boolean isOnlyOne(Collection<String> browsers, Set<String> blocked) {
        if (browsers.size() < 2) return false;
        int kept = 0;
        for (String browser : browsers) {
            if (!blocked.contains(browser)) kept++;
        }
        return kept == 1;
    }
}
