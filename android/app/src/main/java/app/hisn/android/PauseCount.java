package app.hisn.android;

import android.content.Context;
import android.content.SharedPreferences;

import app.hisn.core.PauseTally;

import java.time.LocalDate;

/**
 * Today's pause count, shared by every pause screen. Counting is a day and a number only. A
 * recreated screen (rotation) is the same pause, and the dashboard total follows a real new pause
 * only: a rotation, a merged re-open or a clock moved backwards cannot inflate it.
 */
public final class PauseCount {
    static final String PREFS = "pause";

    private PauseCount() { }

    /** Today's count without recording anything; zero on a new day. */
    public static int today(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getLong("day", -1) == LocalDate.now().toEpochDay() ? prefs.getInt("count", 0) : 0;
    }

    /** Records a pause when {@code count} is true and returns today's tally either way. */
    static PauseTally record(Context context, boolean count) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long storedDay = prefs.getLong("day", -1);
        int storedCount = prefs.getInt("count", 0);
        PauseTally tally = PauseTally.record(storedDay, storedCount,
                prefs.getLong("at", 0), LocalDate.now().toEpochDay(), System.currentTimeMillis());
        if (count) {
            prefs.edit().putLong("day", tally.day).putInt("count", tally.count)
                    .putLong("at", tally.at).apply();
            if (tally.day != storedDay || tally.count != storedCount) new PersonalStore(context).recordPause();
        }
        return tally;
    }
}
