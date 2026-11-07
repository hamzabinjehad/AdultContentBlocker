package app.hisn.core;

/**
 * Reads Android's own enabled-accessibility-services value.
 *
 * <p>The system stores this list as colon-separated {@code package/class} entries, and a class may
 * be written relative to its package ({@code pkg/.Class}) or fully qualified ({@code pkg/pkg.Class}).
 * Some devices keep a bound service in this list while the cached answer of
 * {@code AccessibilityManager.getEnabledAccessibilityServiceList} is stale, so the app asks both.
 * This is platform state that Settings itself writes, not a switch Hisn saves.
 */
public final class AccessibilityGrant {
    private AccessibilityGrant() { }

    /** True when {@code list} names exactly this package and class. Null, empty and malformed entries never match. */
    public static boolean holds(String list, String packageName, String className) {
        if (list == null || packageName == null || className == null
                || packageName.isEmpty() || className.isEmpty()) return false;
        int start = 0;
        while (start < list.length()) {
            int end = list.indexOf(':', start);
            if (end < 0) end = list.length();
            String entry = list.substring(start, end).trim();
            start = end + 1;
            int slash = entry.indexOf('/');
            if (slash <= 0 || slash == entry.length() - 1) continue;
            if (!entry.substring(0, slash).trim().equals(packageName)) continue;
            String name = entry.substring(slash + 1).trim();
            String qualified = name.startsWith(".") ? packageName + name : name;
            if (qualified.equals(className)) return true;
        }
        return false;
    }
}
