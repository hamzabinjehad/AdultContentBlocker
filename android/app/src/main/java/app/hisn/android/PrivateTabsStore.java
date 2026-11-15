package app.hisn.android;

import android.content.Context;

import app.hisn.core.PrivateBrowsing;

/**
 * "Block private tabs": one switch, kept on this phone. A running protection lock refuses
 * turning it off. The guard reads {@link #on} from memory on every check, so it is cached.
 */
public final class PrivateTabsStore {
    private static final String PREFS = "private_tabs";
    private static final String ON = "on";
    private static volatile Boolean cached;

    private PrivateTabsStore() { }

    public static boolean on(Context context) {
        Boolean value = cached;
        if (value == null) {
            value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ON, false);
            cached = value;
        }
        return value;
    }

    /** False when a protection lock refuses turning it off. */
    public static boolean set(Context context, boolean on) {
        if (PrivateBrowsing.refusesChange(OpenWaitStore.locked(context), on(context), on)) return false;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ON, on).apply();
        cached = on;
        return true;
    }
}
