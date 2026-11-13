package app.hisn.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which apps the text and image checks read: the chosen ones, or every app.
 *
 * <p>"Every app" is saved as one reserved entry in the same sealed list as the chosen apps. It has
 * the shape of a package name, so files, the guardian format and the lock rule ("a locked list may
 * only grow") carry it without a new field, and an older build that does not know it sees one
 * package that is not installed. It is never a real app: {@link #covers} refuses it as a name.
 *
 * <p>The caller still applies its own exclusions (the app itself, system screens, launchers,
 * dialers, keyboards); this class only answers what the saved rule says.</p>
 */
public final class ScanScope {
    /** The reserved entry meaning "every app". */
    public static final String ALL_APPS = "hisn.scope.all_apps";

    public static boolean all(Set<String> scans) {
        return scans != null && scans.contains(ALL_APPS);
    }

    /** True when the saved rule reads {@code app}: chosen by name, or every app is chosen. */
    public static boolean covers(Set<String> scans, String app) {
        if (scans == null || app == null || app.isEmpty() || ALL_APPS.equals(app)) return false;
        return scans.contains(app) || scans.contains(ALL_APPS);
    }

    /** The same list with "every app" switched on or off; the chosen apps are kept either way. */
    public static Set<String> with(Set<String> scans, boolean all) {
        Set<String> next = new LinkedHashSet<>(scans == null ? Collections.<String>emptySet() : scans);
        if (all) next.add(ALL_APPS); else next.remove(ALL_APPS);
        return next;
    }

    /** How many real apps are chosen by name, not counting the reserved entry. */
    public static int chosen(Set<String> scans) {
        if (scans == null) return 0;
        return scans.size() - (scans.contains(ALL_APPS) ? 1 : 0);
    }

    /** The list a per-app question should be asked of: with "every app", each candidate counts. */
    public static Set<String> expand(Set<String> scans, Collection<String> candidates) {
        if (!all(scans) || candidates == null) return scans;
        Set<String> next = new LinkedHashSet<>(scans);
        next.addAll(candidates);
        next.remove(ALL_APPS);
        return next;
    }

    private ScanScope() { }
}
