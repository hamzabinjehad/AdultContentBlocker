package app.hisn.android;

import android.content.Context;
import android.content.SharedPreferences;

import app.hisn.core.PersonalProfile;

import java.time.LocalDate;
import java.util.Calendar;

/**
 * Stores the optional name, reason and local pause count. This is deliberately separate from
 * {@link PolicyStore}: these words are not a rule, are not covered by the protection lock and can
 * never change a block decision.
 *
 * <p>The app has backup disabled, and this file is not exported with the rule file or read by the
 * guard. Nothing here is written to diagnostics or a notification.
 */
final class PersonalStore {
    private static final String PREFS = "personal";
    private static final String NAME = "name";
    private static final String PRESET = "preset";
    private static final String REASON = "reason";
    private static final String START_DAY = "start_day";
    private static final String PAUSES = "pauses";
    private static final String PAUSE_HOURS = "pause_hours";

    private final SharedPreferences prefs;

    PersonalStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    PersonalProfile profile() {
        try {
            return new PersonalProfile(prefs.getString(NAME, ""), prefs.getString(PRESET, ""),
                    prefs.getString(REASON, ""), prefs.getLong(START_DAY, 0),
                    prefs.getInt(PAUSES, 0));
        } catch (RuntimeException unreadable) {
            return PersonalProfile.empty();
        }
    }

    /**
     * Saves the cleaned values. The first save starts the day count; later edits keep it, so
     * changing the wording never resets the person's history. A local day is used, not a timezone
     * instant: the counter follows the phone's calendar day.
     */
    void save(String name, String preset, String reason) {
        PersonalProfile cleaned = new PersonalProfile(name, preset, reason, 0, 0);
        long start;
        try {
            start = prefs.getLong(START_DAY, 0);
        } catch (RuntimeException damaged) {
            // A wrong-typed value in a damaged file is treated as a fresh start, not a crash.
            start = 0;
        }
        if (start <= 0) start = LocalDate.now().toEpochDay();
        prefs.edit()
                .putString(NAME, cleaned.name)
                .putString(PRESET, cleaned.preset)
                .putString(REASON, cleaned.reason)
                .putLong(START_DAY, start)
                .apply();
    }

    /** One newly counted pause screen. A recreated or merged screen does not call this. */
    void recordPause() {
        PersonalProfile current = profile();
        String hours;
        try { hours = prefs.getString(PAUSE_HOURS, null); }
        catch (RuntimeException damaged) { hours = null; }
        prefs.edit().putInt(PAUSES, PersonalProfile.nextPauseCount(current.pauses))
                // Only the local hour is kept: no date, app or reason.
                .putString(PAUSE_HOURS, app.hisn.core.PauseHours.record(hours,
                        Calendar.getInstance().get(Calendar.HOUR_OF_DAY)))
                .apply();
    }

    /** Pauses counted in each local hour; all zeros when none were recorded. */
    int[] pauseHours() {
        try { return app.hisn.core.PauseHours.parse(prefs.getString(PAUSE_HOURS, null)); }
        catch (RuntimeException damaged) { return new int[app.hisn.core.PauseHours.HOURS]; }
    }

    /** Removes the name, reason and counters. Protection rules are untouched. */
    void clear() {
        prefs.edit().clear().apply();
    }

    /** The day number for today, or zero when the first save never happened. */
    static int dayNumber(PersonalProfile profile) {
        return PersonalProfile.dayNumber(profile.startDay, LocalDate.now().toEpochDay());
    }

    /** A localized preset label, or the person's own words. Empty when no reason was saved. */
    static String reasonLabel(Context context, PersonalProfile profile) {
        if (!profile.hasReason()) return "";
        switch (profile.preset) {
            case "faith": return context.getString(R.string.personal_preset_faith);
            case "family": return context.getString(R.string.personal_preset_family);
            case "focus": return context.getString(R.string.personal_preset_focus);
            case "rest": return context.getString(R.string.personal_preset_rest);
            case "self": return context.getString(R.string.personal_preset_self);
            case PersonalProfile.CUSTOM: return profile.reason;
            default: return "";
        }
    }

    /** The greeting that belongs to the phone's current local hour. */
    static String greeting(Context context) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        switch (PersonalProfile.greeting(hour)) {
            case PersonalProfile.MORNING: return context.getString(R.string.greeting_morning);
            case PersonalProfile.AFTERNOON: return context.getString(R.string.greeting_afternoon);
            default: return context.getString(R.string.greeting_evening);
        }
    }
}
