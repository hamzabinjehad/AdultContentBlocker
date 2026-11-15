package app.hisn.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where browsers mark a private (incognito) tab, for the opt-in "Block private tabs" rule.
 *
 * <p>Only view IDs read from a real device are listed. Chrome shows
 * {@code location_bar_incognito_badge} in its toolbar on every incognito page and
 * {@code new_tab_incognito_container} on the incognito new-tab page (Chrome on the API 36
 * emulator, 15 November 2026; a normal tab shows neither). Chrome Beta, Dev and Canary are the
 * same app under other package names. A renamed ID is a missed tab, never a wrong close, so
 * update this list from a device when one drifts.</p>
 *
 * <p>The action is Back, not Home: Back walks the private tab's history and then closes the
 * tab, so the browser itself stays usable. Home would leave the private tab in front for the
 * next open, which would lock the person out of the browser.</p>
 */
public final class PrivateBrowsing {
    private static final List<String> CHROME_IDS = Collections.unmodifiableList(Arrays.asList(
            "location_bar_incognito_badge", "new_tab_incognito_container"));

    private static final Map<String, List<String>> MARKERS;
    static {
        Map<String, List<String>> markers = new LinkedHashMap<>();
        for (String chrome : new String[] {"com.android.chrome", "com.chrome.beta", "com.chrome.dev",
                "com.chrome.canary"}) {
            markers.put(chrome, CHROME_IDS);
        }
        MARKERS = Collections.unmodifiableMap(markers);
    }

    private PrivateBrowsing() { }

    /** True when this app is a browser whose private tabs Hisn can recognise. */
    public static boolean watches(String packageName) {
        return packageName != null && MARKERS.containsKey(packageName);
    }

    /** The browsers covered, for the settings text and tests. */
    public static List<String> browsers() {
        return Collections.unmodifiableList(Arrays.asList(MARKERS.keySet().toArray(new String[0])));
    }

    /** Fully qualified view IDs ("package:id/name") that mark a private tab in this browser. */
    public static List<String> markerViewIds(String packageName) {
        List<String> names = packageName == null ? null : MARKERS.get(packageName);
        if (names == null) return Collections.emptyList();
        String[] qualified = new String[names.size()];
        for (int i = 0; i < qualified.length; i++) qualified[i] = packageName + ":id/" + names.get(i);
        return Collections.unmodifiableList(Arrays.asList(qualified));
    }

    /** During a protection lock the rule may be turned on but not off. */
    public static boolean refusesChange(boolean locked, boolean wasOn, boolean on) {
        return locked && wasOn && !on;
    }
}
