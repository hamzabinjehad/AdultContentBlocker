package app.hisn.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import app.hisn.core.OpenWait;

/**
 * "Wait before opening": the chosen apps, the wait, and today's counts, kept on this phone.
 *
 * <p>Stored apart from the sealed rule file because it never blocks anything: the person can
 * always open the app once the wait is over. A running protection lock still refuses making it
 * weaker (see {@link OpenWait#loosens}). The visit clock lives in memory only; the guard service
 * and the wait screen run in the same process.</p>
 */
public final class OpenWaitStore {
    private static final String PREFS = "open_wait";
    private static final String APPS = "apps";
    private static final String SECONDS = "seconds";
    private static final String GROWS = "grows";
    private static final String DAY = "day";
    private static final String OPENED = "opened:";
    private static final String CLOSED = "closed:";

    /** When each app was last in use after its wait, and its opening window (elapsed realtime). */
    private static final ConcurrentHashMap<String, Long> LAST_SEEN = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> ALLOWED_UNTIL = new ConcurrentHashMap<>();
    /** A visit the person bounded themselves ("Open for 5 min"): when it is over (elapsed realtime). */
    private static final ConcurrentHashMap<String, Long> VISIT_END = new ConcurrentHashMap<>();

    private OpenWaitStore() { }

    public static Set<String> apps(Context context) {
        return Collections.unmodifiableSet(new HashSet<>(prefs(context).getStringSet(APPS, Collections.emptySet())));
    }

    public static int seconds(Context context) {
        return OpenWait.sanitize(prefs(context).getInt(SECONDS, OpenWait.DEFAULT_WAIT));
    }

    public static boolean grows(Context context) {
        return prefs(context).getBoolean(GROWS, true);
    }

    /** False when a running protection lock refuses the change because it loosens the rule. */
    public static boolean save(Context context, Set<String> apps, int seconds, boolean grows) {
        if (locked(context) && OpenWait.loosens(apps(context), seconds(context), grows(context), apps, seconds, grows)) {
            return false;
        }
        prefs(context).edit()
                .putStringSet(APPS, new HashSet<>(apps))
                .putInt(SECONDS, OpenWait.sanitize(seconds))
                .putBoolean(GROWS, grows)
                .apply();
        return true;
    }

    public static boolean locked(Context context) {
        try {
            return PolicyStore.get(context).remainingMillis() > 0;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    /** Called by the guard for every event of an app. True when the wait screen should come up. */
    static boolean onAppEvent(Context context, String app, boolean newWindow) {
        // Read without copying: this runs for every accessibility event.
        if (!prefs(context).getStringSet(APPS, Collections.emptySet()).contains(app)) return false;
        long now = SystemClock.elapsedRealtime();
        // A visit the person timed ends on the next event from the app once its time is up,
        // so the wait comes back even if they never leave the app.
        Long end = VISIT_END.get(app);
        if (end != null && now >= end) {
            VISIT_END.remove(app);
            LAST_SEEN.remove(app);
            ALLOWED_UNTIL.remove(app);
            return true;
        }
        long lastSeen = LAST_SEEN.getOrDefault(app, 0L);
        long allowedUntil = ALLOWED_UNTIL.getOrDefault(app, 0L);
        if (newWindow && OpenWait.needsWait(true, now, lastSeen, allowedUntil)) return true;
        if (OpenWait.inVisit(now, lastSeen, allowedUntil)) LAST_SEEN.put(app, now);
        return false;
    }

    /** "Open anyway": counts the open and lets the app come up. */
    public static void opened(Context context, String app) {
        StepIns.record(context, StepIns.OPENED);
        long now = SystemClock.elapsedRealtime();
        ALLOWED_UNTIL.put(app, now + OpenWait.OPENING_MILLIS);
        LAST_SEEN.put(app, now);
        bump(context, OPENED + app);
        VISIT_END.remove(app);
    }

    /** "Open for N min": like {@link #opened}, but the wait returns once the minutes are up. */
    public static void openedFor(Context context, String app, int minutes) {
        opened(context, app);
        if (minutes > 0) VISIT_END.put(app, SystemClock.elapsedRealtime() + minutes * 60_000L);
    }

    /** "Close": counts it, and the next open waits again. */
    public static void closed(Context context, String app) {
        StepIns.record(context, StepIns.WALKED);
        LAST_SEEN.remove(app);
        ALLOWED_UNTIL.remove(app);
        VISIT_END.remove(app);
        bump(context, CLOSED + app);
    }

    public static int openedToday(Context context, String app) {
        return today(context).getInt(OPENED + app, 0);
    }

    public static int closedToday(Context context, String app) {
        return today(context).getInt(CLOSED + app, 0);
    }

    /** Seconds for the next open of this app. */
    public static int waitFor(Context context, String app) {
        return OpenWait.seconds(seconds(context), grows(context), openedToday(context, app));
    }

    private static void bump(Context context, String key) {
        SharedPreferences prefs = today(context);
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply();
    }

    /** The counts reset on a new local day. */
    private static SharedPreferences today(Context context) {
        SharedPreferences prefs = prefs(context);
        String day = LocalDate.now().toString();
        if (!day.equals(prefs.getString(DAY, null))) {
            SharedPreferences.Editor edit = prefs.edit();
            for (String key : prefs.getAll().keySet()) {
                if (key.startsWith(OPENED) || key.startsWith(CLOSED)) edit.remove(key);
            }
            edit.putString(DAY, day).commit();
        }
        return prefs;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
