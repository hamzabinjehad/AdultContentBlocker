package app.hisn.android;

import android.content.Context;

import app.hisn.core.RestPeriod;

/** The chosen quiet period after a word or image detection.
 *
 * <p>Stored apart from the sealed rule file because it is not a detection rule: changing it never
 * makes a matched word or image pass, and it is never read as permission to weaken a rule. It only
 * sets how long the app whose screen was detected stays closed. The platform cannot force-stop
 * another app, so the guard closes its page and its window and holds that one package.</p>
 */
final class AfterBlock {
    private static final String PREFS = "hisn_after_block";
    private static final String KEY_MINUTES = "minutes";

    static int minutes(Context context) {
        return RestPeriod.sanitize(context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_MINUTES, RestPeriod.DEFAULT_MINUTES));
    }

    static void set(Context context, int minutes) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_MINUTES, RestPeriod.sanitize(minutes)).apply();
    }

    private AfterBlock() { }
}
