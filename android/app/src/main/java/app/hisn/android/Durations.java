package app.hisn.android;

import android.content.Context;

import app.hisn.core.EvidenceAge;
import app.hisn.core.ProtectionReadiness;
import app.hisn.core.RemainingTime;

/** Remaining commitment time as a correctly pluralized phrase, hours up to 48 then days. */
final class Durations {
    /** A short, localized age line for a layer that has (or lacks) evidence. */
    static String evidence(Context context, ProtectionReadiness.Status status, int subjectRes) {
        if (status == null) return null;
        String age = age(context, status);
        return age == null ? null
                : context.getString(R.string.evidence_subject, context.getString(subjectRes), age);
    }

    private static String age(Context context, ProtectionReadiness.Status status) {
        if (status.state == ProtectionReadiness.State.NOT_CHECKED) {
            return context.getString(status.reason == ProtectionReadiness.Reason.STALE_EVIDENCE
                    ? R.string.evidence_check_again : R.string.evidence_not_checked);
        }
        if (status.state != ProtectionReadiness.State.WORKING) return null;
        long age = status.evidenceAgeMillis;
        if (age < 0) return context.getString(R.string.evidence_not_checked);
        switch (EvidenceAge.unit(age)) {
            case NOW:
                return context.getString(R.string.evidence_checked_now);
            case MINUTES:
                int minutes = EvidenceAge.count(age);
                return context.getResources().getQuantityString(
                        R.plurals.evidence_checked_minutes, minutes, minutes);
            default:
                int hours = EvidenceAge.count(age);
                return context.getResources().getQuantityString(
                        R.plurals.evidence_checked_hours, hours, hours);
        }
    }

    static String remaining(Context context, long millis) {
        RemainingTime time = RemainingTime.of(millis);
        return context.getResources().getQuantityString(time.unit == RemainingTime.Unit.HOURS
                ? R.plurals.remaining_hours : R.plurals.remaining_days, time.count, time.count);
    }

    private Durations() { }
}
