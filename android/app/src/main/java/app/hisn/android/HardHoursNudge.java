package app.hisn.android;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.Calendar;
import java.util.Locale;

/**
 * The opt-in nudge before a person's hard hours: once a day, {@link #LEAD_MINUTES} minutes before
 * the busiest {@link app.hisn.core.PauseHours#PEAK_SPAN}-hour window of their own pauses, carrying
 * their own note.
 *
 * <p>Why this shape: if-then plans (Gollwitzer and Sheeran's 2006 meta-analysis of 94 studies)
 * help most when the cue arrives just before the risky moment, and just-in-time support
 * (Nahum-Shani et al. 2018) works when it is rare and wanted. So it is off until the person
 * asks, fires at most once a day, follows the window when the pauses move, and says nothing when
 * there are too few pauses to know the window.</p>
 *
 * <p>An inexact alarm window is used: no exact-alarm permission, and a few minutes either way
 * does not change the point.</p>
 */
public final class HardHoursNudge extends BroadcastReceiver {
    static final int LEAD_MINUTES = 15;
    private static final String PREFS = "hard_hours";
    private static final String ON = "nudge_on";
    private static final String LAST_DAY = "last_day";
    private static final String CHANNEL = "hard_hours";
    private static final int NOTIFICATION_ID = 0x4848;
    private static final String ACTION_FIRE = "app.hisn.android.HARD_HOURS_NUDGE";
    private static final String ACTION_SKIP = "app.hisn.android.HARD_HOURS_SKIP";
    private static final long WINDOW_MS = 10 * 60_000L;

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isOn(Context context) {
        try { return prefs(context).getBoolean(ON, false); }
        catch (RuntimeException damaged) { return false; }
    }

    public static void set(Context context, boolean on) {
        prefs(context).edit().putBoolean(ON, on).apply();
        schedule(context);
    }

    /** Whether Android lets the nudge show. */
    public static boolean canNotify(Context context) {
        return Permissions.notifications(context);
    }

    /** First hour of the hard hours, or -1 when there are too few pauses to say. */
    public static int peakStart(Context context) {
        return app.hisn.core.PauseHours.peakStart(new PersonalStore(context).pauseHours());
    }

    /** Minute of the day the nudge fires for a window starting at {@code peakHour}. */
    public static int nudgeMinute(int peakHour) {
        return ((peakHour * 60 - LEAD_MINUTES) % 1440 + 1440) % 1440;
    }

    public static String time(int minuteOfDay) {
        return String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    /** Arms the next nudge, or cancels it when it is off or there is no window yet. */
    public static void schedule(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms == null) return;
        PendingIntent fire = PendingIntent.getBroadcast(context, 0,
                new Intent(context, HardHoursNudge.class).setAction(ACTION_FIRE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        int peak = peakStart(context);
        if (!isOn(context) || peak < 0) {
            alarms.cancel(fire);
            return;
        }
        int minute = nudgeMinute(peak);
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, minute / 60);
        next.set(Calendar.MINUTE, minute % 60);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) next.add(Calendar.DAY_OF_YEAR, 1);
        try {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), WINDOW_MS, fire);
        } catch (RuntimeException unavailable) {
            android.util.Log.w("HardHoursNudge", "Nudge could not be armed", unavailable);
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (ACTION_SKIP.equals(intent.getAction())) {
            NotificationManager notifications = context.getSystemService(NotificationManager.class);
            if (notifications != null) notifications.cancel(NOTIFICATION_ID);
            return;
        }
        if (!ACTION_FIRE.equals(intent.getAction())) return;
        try {
            long today = java.time.LocalDate.now().toEpochDay();
            // Once a day, even if a reschedule lands twice.
            if (isOn(context) && prefs(context).getLong(LAST_DAY, -1) != today && peakStart(context) >= 0) {
                if (post(context)) prefs(context).edit().putLong(LAST_DAY, today).apply();
            }
        } finally {
            schedule(context);
        }
    }

    private static boolean post(Context context) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (notifications == null || !Permissions.notifications(context)) return false;
        try {
            Compat.createChannel(context, CHANNEL, R.string.nudge_channel, NotificationManager.IMPORTANCE_DEFAULT);
            int peak = peakStart(context);
            String title = context.getString(R.string.nudge_title, time(peak * 60));
            app.hisn.core.PersonalProfile profile = new PersonalStore(context).profile();
            String note = PersonalStore.reasonLabel(context, profile);
            String body = note.isEmpty() ? context.getString(R.string.nudge_body)
                    : context.getString(R.string.personal_quote, note);
            PendingIntent open = PendingIntent.getActivity(context, 1,
                    new Intent(context, ShellActivity.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent breathe = PendingIntent.getActivity(context, 2,
                    new Intent(context, BreatheActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent skip = PendingIntent.getBroadcast(context, 3,
                    new Intent(context, HardHoursNudge.class).setAction(ACTION_SKIP),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            notifications.notify(NOTIFICATION_ID, Compat.builder(context, CHANNEL,
                    NotificationManager.IMPORTANCE_DEFAULT)
                    .setSmallIcon(R.drawable.ic_pause).setColor(context.getColor(R.color.accent))
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setContentIntent(open).setAutoCancel(true)
                    .addAction(new Notification.Action.Builder(null,
                            context.getString(R.string.nudge_breathe), breathe).build())
                    .addAction(new Notification.Action.Builder(null,
                            context.getString(R.string.nudge_skip), skip).build())
                    .setCategory(Notification.CATEGORY_REMINDER).build());
            return true;
        } catch (RuntimeException unavailable) {
            android.util.Log.w("HardHoursNudge", "Nudge could not be posted", unavailable);
            return false;
        }
    }
}
