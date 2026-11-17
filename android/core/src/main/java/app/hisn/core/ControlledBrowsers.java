package app.hisn.core;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * "Only browsers Hisn controls": with it on, every browser is blocked except the ones Hisn can
 * fully watch, so a second browser is not a way around private-tab closing or the short-video
 * check. A browser is controlled when Hisn recognises both its private tab and its address bar
 * ({@link PrivateBrowsing} and {@link ShortVideoSurfaces}); today that is Chrome and its Beta,
 * Dev and Canary builds. A browser joins this set only once both of its markers are read from a
 * device, never by name alone.
 */
public final class ControlledBrowsers {
    private ControlledBrowsers() { }

    /** The browsers left usable while the rule is on. */
    public static Set<String> controlled() {
        Set<String> result = new LinkedHashSet<>();
        for (String browser : PrivateBrowsing.browsers()) {
            if (ShortVideoSurfaces.urlBarViewId(browser) != null) result.add(browser);
        }
        return result;
    }

    public static boolean controlled(String packageName) {
        return packageName != null && PrivateBrowsing.watches(packageName)
                && ShortVideoSurfaces.urlBarViewId(packageName) != null;
    }

    /** True when the rule is on and this app is a browser Hisn cannot watch. */
    public static boolean blocks(boolean on, String packageName, boolean isBrowser) {
        return on && isBrowser && packageName != null && !controlled(packageName);
    }

    /** During a protection lock the rule may be turned on but not off. */
    public static boolean refusesChange(boolean locked, boolean wasOn, boolean on) {
        return locked && wasOn && !on;
    }
}
