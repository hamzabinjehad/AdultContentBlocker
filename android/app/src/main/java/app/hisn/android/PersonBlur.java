package app.hisn.android;

import android.content.Context;
import android.content.SharedPreferences;

/** The opt-in people cover: off, all detected faces, or an estimated women/men preference.
 *
 * <p>This is a comfort preference, not a saved rule. Turning it off never disables the adult
 * classifier, and changing it does not change the sealed rule file. The value names match the
 * shared visual engine's {@code PERSON_MODES}; {@code female}/{@code male} are model estimates,
 * not a person's identity.</p>
 */
final class PersonBlur {
    static final String OFF = "off", ALL = "all", WOMEN = "female", MEN = "male";
    /** A matched face sends the app back to the previous page; the alternative is a full cover. */
    static final String BACK = "back", COVER = "cover";
    private static final String FILE = "hisn_person_blur", KEY = "mode", RESPONSE = "response";
    private static final String[] MODES = {OFF, ALL, WOMEN, MEN};
    private static final String[] RESPONSES = {BACK, COVER};

    private PersonBlur() { }

    /** One of {@link #OFF}, {@link #ALL}, {@link #WOMEN} or {@link #MEN}; unknown values read as off. */
    static String mode(Context context) {
        String value = preferences(context).getString(KEY, OFF);
        for (String allowed : MODES) if (allowed.equals(value)) return value;
        return OFF;
    }

    static void set(Context context, String mode) {
        for (String allowed : MODES) if (allowed.equals(mode)) {
            preferences(context).edit().putString(KEY, mode).apply();
            return;
        }
        throw new IllegalArgumentException("Unsupported people mode");
    }

    static boolean on(String mode) { return mode != null && !OFF.equals(mode); }

    /** Back by default: a matched person returns the person to the last page they were on. */
    static String response(Context context) {
        String value = preferences(context).getString(RESPONSE, BACK);
        for (String allowed : RESPONSES) if (allowed.equals(value)) return value;
        return BACK;
    }

    static void setResponse(Context context, String response) {
        for (String allowed : RESPONSES) if (allowed.equals(response)) {
            preferences(context).edit().putString(RESPONSE, response).apply();
            return;
        }
        throw new IllegalArgumentException("Unsupported people response");
    }

    static int responseLabel(String response) {
        return COVER.equals(response) ? R.string.person_response_cover : R.string.person_response_back;
    }

    static int label(String mode) {
        if (ALL.equals(mode)) return R.string.person_blur_all;
        if (WOMEN.equals(mode)) return R.string.person_blur_women;
        if (MEN.equals(mode)) return R.string.person_blur_men;
        return R.string.person_blur_off;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
