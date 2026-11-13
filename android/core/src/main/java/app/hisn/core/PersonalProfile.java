package app.hisn.core;

import java.util.Locale;

/**
 * The optional name, reason and local pause count that make Hisn feel like the person's own.
 *
 * <p>Nothing here is a protection rule: these words never decide whether a host, word, app or
 * image is blocked, and changing them cannot weaken a layer. They are kept apart from the app's
 * sealed rule file, so a name or a reason is never read as policy. The class is pure so the
 * cleaning, greeting and day arithmetic can be tested without Android.
 *
 * <p>The cleaned values are bounded and single-line. Directional overrides and other invisible
 * formatting characters are removed; a name therefore cannot reorder the text around it. The class
 * deliberately has no {@code toString()} carrying the person's words.
 */
public final class PersonalProfile {
    public static final int MAX_NAME_CODE_POINTS = 24;
    public static final int MAX_REASON_CODE_POINTS = 80;
    public static final int MAX_PAUSES = 99999;
    public static final int MAX_DAY_NUMBER = 99999;

    public static final int MORNING = 0;
    public static final int AFTERNOON = 1;
    public static final int EVENING = 2;

    /** One of the built-in reasons, or {@code ""}. A saved custom reason uses {@link #CUSTOM}. */
    public static final String CUSTOM = "custom";
    private static final String[] PRESETS = {"faith", "family", "focus", "rest", "self"};

    public final String name;
    /** A built-in key, {@link #CUSTOM}, or empty. */
    public final String preset;
    /** The person's own words, only when {@link #preset} is {@link #CUSTOM}. */
    public final String reason;
    /** The local day the first details were saved, or zero when nothing was saved yet. */
    public final long startDay;
    public final int pauses;

    public PersonalProfile(String name, String preset, String reason, long startDay, int pauses) {
        this.name = cleanName(name);
        String key = cleanPreset(preset);
        String custom = cleanReason(reason);
        // A stored custom reason survives an empty or damaged key; a preset reason does not
        // keep a stale custom line around.
        if (key.isEmpty() && !custom.isEmpty()) key = CUSTOM;
        if (CUSTOM.equals(key)) {
            if (custom.isEmpty()) key = "";
        } else {
            custom = "";
        }
        this.preset = key;
        this.reason = custom;
        this.startDay = Math.max(0, startDay);
        this.pauses = Math.max(0, Math.min(pauses, MAX_PAUSES));
    }

    public static PersonalProfile empty() {
        return new PersonalProfile("", "", "", 0, 0);
    }

    public boolean hasName() {
        return !name.isEmpty();
    }

    public boolean hasReason() {
        return CUSTOM.equals(preset) ? !reason.isEmpty() : !preset.isEmpty();
    }

    /** Whether there is anything to show a person; the counters alone do not count. */
    public boolean hasDetails() {
        return hasName() || hasReason();
    }

    public static boolean isPreset(String value) {
        if (value == null) return false;
        String key = value.trim().toLowerCase(Locale.ROOT);
        for (String preset : PRESETS) if (preset.equals(key)) return true;
        return false;
    }

    public static String cleanName(String raw) {
        return cleanText(raw, MAX_NAME_CODE_POINTS);
    }

    public static String cleanReason(String raw) {
        return cleanText(raw, MAX_REASON_CODE_POINTS);
    }

    /** A known built-in key, {@link #CUSTOM}, or empty. Unknown keys are not invented. */
    public static String cleanPreset(String raw) {
        if (raw == null) return "";
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (CUSTOM.equals(key)) return CUSTOM;
        for (String preset : PRESETS) if (preset.equals(key)) return preset;
        return "";
    }

    /**
     * One of {@link #MORNING}, {@link #AFTERNOON} or {@link #EVENING}. Hours outside 0..23 are
     * wrapped, so a clock change or an odd caller cannot make the greeting disappear.
     */
    public static int greeting(int hour) {
        int normalized = ((hour % 24) + 24) % 24;
        if (normalized >= 5 && normalized < 12) return MORNING;
        if (normalized >= 12 && normalized < 18) return AFTERNOON;
        return EVENING;
    }

    /**
     * The day number shown as "Day N with Hisn". The first day is 1. A clock moved before the start
     * still reads as day 1 instead of a negative or zero number.
     */
    public static int dayNumber(long startDay, long todayDay) {
        if (startDay <= 0 || todayDay < 0) return 0;
        if (todayDay <= startDay) return 1;
        long elapsed = todayDay - startDay;
        if (elapsed >= MAX_DAY_NUMBER) return MAX_DAY_NUMBER;
        return (int) elapsed + 1;
    }

    /** The next local pause count, bounded and tolerant of a damaged stored value. */
    public static int nextPauseCount(int pauses) {
        if (pauses < 0) return 1;
        return pauses >= MAX_PAUSES ? MAX_PAUSES : pauses + 1;
    }

    private static String cleanText(String raw, int maxCodePoints) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder out = new StringBuilder(raw.length());
        boolean pendingSpace = false;
        int kept = 0;
        for (int index = 0; index < raw.length(); ) {
            int codePoint = raw.codePointAt(index);
            index += Character.charCount(codePoint);
            if (isInvisibleDirectional(codePoint)) continue;
            if (Character.isISOControl(codePoint)) {
                // Tabs and line breaks become one ordinary space; other controls are dropped.
                if (codePoint == '\t' || codePoint == '\n' || codePoint == '\r') {
                    pendingSpace = out.length() > 0;
                }
                continue;
            }
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = out.length() > 0;
                continue;
            }
            if (kept >= maxCodePoints) break;
            if (pendingSpace && out.length() > 0) out.append(' ');
            pendingSpace = false;
            out.appendCodePoint(codePoint);
            kept++;
        }
        return out.toString();
    }

    /**
     * Removes bidi overrides, isolates, marks and the byte-order mark. The zero-width joiner and
     * non-joiner stay because Arabic/Persian names and emoji sequences use them; this keeps the
     * text's own characters while refusing an invisible reordering of the screen.
     */
    private static boolean isInvisibleDirectional(int codePoint) {
        return codePoint == 0x061C // Arabic letter mark
                || codePoint == 0x200B // zero-width space
                || codePoint == 0x200E || codePoint == 0x200F // LRM, RLM
                || (codePoint >= 0x202A && codePoint <= 0x202E) // LRE..RLO
                || (codePoint >= 0x2066 && codePoint <= 0x2069) // LRI..PDI
                || codePoint == 0xFEFF; // BOM
    }
}
