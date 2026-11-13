package app.hisn.core;

/**
 * How many pause screens were counted in each local hour of the day: twenty-four numbers, never an
 * app, a site, a reason or a date. It lets a person see when their own difficult hours are.
 *
 * <p>The counts are a personal note, not evidence: nothing here changes a block decision, and the
 * file holding them is never exported or put in diagnostics.</p>
 */
public final class PauseHours {
    public static final int HOURS = 24;
    /** Below this many pauses a "usual time" would be a guess, so none is offered. */
    public static final int MIN_FOR_PEAK = 5;
    /** The peak is reported as a window this many hours wide. */
    public static final int PEAK_SPAN = 3;
    private static final int MAX_COUNT = 9999;

    /** The saved text for one more pause at {@code hour}; a damaged or missing value starts empty. */
    public static String record(String saved, int hour) {
        int[] counts = parse(saved);
        if (hour >= 0 && hour < HOURS && counts[hour] < MAX_COUNT) counts[hour]++;
        return format(counts);
    }

    /** Always twenty-four non-negative counts; anything unreadable is all zeros. */
    public static int[] parse(String saved) {
        int[] counts = new int[HOURS];
        if (saved == null) return counts;
        String[] parts = saved.split(",", -1);
        if (parts.length != HOURS) return counts;
        int[] parsed = new int[HOURS];
        for (int at = 0; at < HOURS; at++) {
            try {
                int value = Integer.parseInt(parts[at]);
                if (value < 0 || value > MAX_COUNT) return counts;
                parsed[at] = value;
            } catch (NumberFormatException damaged) {
                return counts;
            }
        }
        return parsed;
    }

    public static String format(int[] counts) {
        StringBuilder out = new StringBuilder();
        for (int at = 0; at < HOURS; at++) {
            if (at > 0) out.append(',');
            out.append(counts != null && at < counts.length ? Math.max(0, Math.min(MAX_COUNT, counts[at])) : 0);
        }
        return out.toString();
    }

    public static int total(int[] counts) {
        int sum = 0;
        for (int count : counts) sum += count;
        return sum;
    }

    /**
     * The first hour of the busiest {@link #PEAK_SPAN}-hour window, wrapping past midnight, or -1
     * when there are too few pauses to say. A tie goes to the earliest window.
     */
    public static int peakStart(int[] counts) {
        if (counts == null || counts.length != HOURS || total(counts) < MIN_FOR_PEAK) return -1;
        int best = -1;
        int bestSum = 0;
        for (int start = 0; start < HOURS; start++) {
            int sum = 0;
            for (int offset = 0; offset < PEAK_SPAN; offset++) sum += counts[(start + offset) % HOURS];
            if (sum > bestSum) { bestSum = sum; best = start; }
        }
        return best;
    }

    private PauseHours() { }
}
