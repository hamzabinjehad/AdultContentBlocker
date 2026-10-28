package app.hisn.core;

/** Stops an intervention loop on a window that Home does not remove (a pop-up, or a picture-in-picture
 * window whose dismiss was refused). Android-free so the rule is tested.
 *
 * After acting on a window, the same app's same window is not acted on again for
 * {@link #HOLD_OFF_MILLIS} while it is not the active window: Home already ran, and repeating it
 * would only reopen the notice and take the user out of whatever else is on screen. Once that window
 * is active again (the user went back to it), or the period has passed, it is acted on again. Other
 * windows and apps are never held off.
 */
public final class InterventionRepeat {
    public static final long HOLD_OFF_MILLIS = 10000;
    private String app;
    private int window = -1;
    private long at;

    /** Milliseconds to wait before acting on this window again, or 0 to act now. */
    public synchronized long wait(String app, int window, boolean active, long now) {
        if (active || app == null || window < 0 || !app.equals(this.app) || window != this.window) return 0;
        if (now < at || now - at >= HOLD_OFF_MILLIS) return 0;
        return at + HOLD_OFF_MILLIS - now;
    }

    public synchronized void acted(String app, int window, long now) {
        this.app = app;
        this.window = window;
        at = now;
    }
}
