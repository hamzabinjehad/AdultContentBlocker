package app.hisn.android;

import android.content.Context;

/**
 * "App protection" turned off inside Hisn. Android's accessibility switch stays on, so turning
 * App protection back on is one tap here, with no trip to the phone's settings. While paused, the
 * guard closes nothing, shows no wait and checks no text or images. It keeps watching only its own
 * uninstall and disable screens when the owner asked for that, which has its own switch.
 *
 * A running protection lock refuses the pause. The guard reads {@link #paused} on every event, so
 * it is cached in memory.
 */
public final class GuardPause {
    private static final String PREFS = "guard_pause";
    private static final String PAUSED = "paused";
    private static volatile Boolean cached;

    private GuardPause() { }

    public static boolean paused(Context context) {
        Boolean value = cached;
        if (value == null) {
            value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PAUSED, false);
            cached = value;
        }
        return value;
    }

    /** False when a protection lock refuses the pause. Resuming is always allowed. */
    public static boolean set(Context context, boolean paused) {
        if (paused && OpenWaitStore.locked(context)) return false;
        // commit(): the guard may read this from a fresh process right after the tap.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(PAUSED, paused).commit();
        cached = paused;
        if (paused) OpenWaitOverlay.hide(context);
        return true;
    }
}
