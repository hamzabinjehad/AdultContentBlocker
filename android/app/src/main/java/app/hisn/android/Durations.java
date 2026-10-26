package app.hisn.android;

import android.content.Context;

import app.hisn.core.RemainingTime;

/** Remaining commitment time as a correctly pluralized phrase, hours up to 48 then days. */
final class Durations {
    static String remaining(Context context, long millis) {
        RemainingTime time = RemainingTime.of(millis);
        return context.getResources().getQuantityString(time.unit == RemainingTime.Unit.HOURS
                ? R.plurals.remaining_hours : R.plurals.remaining_days, time.count, time.count);
    }

    private Durations() { }
}
