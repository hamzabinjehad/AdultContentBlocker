package app.hisn.android;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * "Focus now": the person closes their feed apps for a set number of minutes, in one tap, with
 * no schedule to set up. It is their own choice, so it can be ended early; it never touches the
 * saved rules, and it ends by itself at the time it named. Kept on this phone only.
 *
 * The guard reads it on every check, so the state is cached in memory and written through to
 * preferences, which survive a restart of the app's process.
 */
public final class FocusSession {
    private static final String PREFS = "focus";
    private static final String UNTIL = "until";
    private static final String APPS = "apps";
    /** The longest focus offered; a damaged stored end further away than this is ignored. */
    static final long MAX_MILLIS = 4L * 60L * 60_000L;

    private static volatile boolean loaded;
    private static volatile long until;
    private static volatile Set<String> apps = Collections.emptySet();

    private FocusSession() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Reads the stored session once per process. */
    public static void load(Context context) {
        if (loaded) return;
        try {
            SharedPreferences p = prefs(context);
            long end = p.getLong(UNTIL, 0L);
            Set<String> stored = p.getStringSet(APPS, null);
            long now = System.currentTimeMillis();
            if (end > now && end - now <= MAX_MILLIS && stored != null) {
                until = end;
                apps = Collections.unmodifiableSet(new HashSet<>(stored));
            }
        } catch (RuntimeException damaged) {
            until = 0L;
            apps = Collections.emptySet();
        }
        loaded = true;
    }

    /** Starts a session of {@code minutes} over {@code packages}; false when there is nothing to close. */
    public static boolean start(Context context, int minutes, Set<String> packages) {
        if (minutes <= 0 || packages == null || packages.isEmpty()) return false;
        long end = System.currentTimeMillis() + Math.min(minutes * 60_000L, MAX_MILLIS);
        Set<String> copy = Collections.unmodifiableSet(new HashSet<>(packages));
        prefs(context).edit().putLong(UNTIL, end).putStringSet(APPS, new HashSet<>(packages)).apply();
        apps = copy;
        until = end;
        loaded = true;
        showNotice(context, end, packages.size());
        return true;
    }

    private static final String CHANNEL = "focus";
    private static final int NOTICE_ID = 0x0F0C;

    /**
     * A quiet ongoing notice while the focus runs, counting down to its end where Android can
     * (7+), and taking itself away when the focus ends (8+). Tapping it opens the app, where the
     * focus can be ended early. Without notification permission nothing is shown, and the focus
     * still holds.
     */
    private static void showNotice(Context context, long end, int count) {
        try {
            android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
            if (manager == null) return;
            Compat.createChannel(context, CHANNEL, R.string.focus_channel, android.app.NotificationManager.IMPORTANCE_LOW);
            android.content.Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            android.app.PendingIntent open = launch == null ? null : android.app.PendingIntent.getActivity(context, NOTICE_ID,
                    launch, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            String ends = java.time.Instant.ofEpochMilli(end).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            android.app.Notification.Builder builder = Compat.builder(context, CHANNEL, android.app.NotificationManager.IMPORTANCE_LOW)
                    .setSmallIcon(R.drawable.ic_pause).setColor(context.getColor(R.color.accent))
                    .setContentTitle(context.getString(R.string.focus_notice_title))
                    .setContentText(context.getResources().getQuantityString(R.plurals.focus_notice_body, count, count, ends))
                    .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(true).setWhen(end).setUsesChronometer(true)
                    .setCategory(android.app.Notification.CATEGORY_STATUS);
            if (open != null) builder.setContentIntent(open);
            if (android.os.Build.VERSION.SDK_INT >= 24) builder.setChronometerCountDown(true);
            if (android.os.Build.VERSION.SDK_INT >= 26) builder.setTimeoutAfter(Math.max(1000L, end - System.currentTimeMillis()));
            manager.notify(NOTICE_ID, builder.build());
        } catch (RuntimeException unavailable) {
            // No notice; the focus itself is unaffected.
        }
    }

    private static void clearNotice(Context context) {
        try {
            android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
            if (manager != null) manager.cancel(NOTICE_ID);
        } catch (RuntimeException ignored) { }
    }

    public static void stop(Context context) {
        prefs(context).edit().clear().apply();
        clearNotice(context);
        until = 0L;
        apps = Collections.emptySet();
        loaded = true;
    }

    public static boolean active(Context context) {
        load(context);
        return until > System.currentTimeMillis();
    }

    /** When the session ends, in wall-clock milliseconds; 0 when none is running. */
    public static long until(Context context) {
        return active(context) ? until : 0L;
    }

    public static Set<String> apps(Context context) {
        return active(context) ? apps : Collections.emptySet();
    }

    /** Whether a running session closes this app. Safe to call before {@link #load}: false then. */
    public static boolean blocks(String packageName) {
        return packageName != null && until > System.currentTimeMillis() && apps.contains(packageName);
    }
}
