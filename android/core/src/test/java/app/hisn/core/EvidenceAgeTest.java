package app.hisn.core;

public final class EvidenceAgeTest {
    public static void main(String[] args) {
        if (EvidenceAge.unit(0) != EvidenceAge.Unit.NOW) throw new AssertionError("zero not now");
        if (EvidenceAge.unit(EvidenceAge.JUST_NOW_MILLIS - 1) != EvidenceAge.Unit.NOW) {
            throw new AssertionError("sub-minute not now");
        }
        if (EvidenceAge.unit(EvidenceAge.JUST_NOW_MILLIS) != EvidenceAge.Unit.MINUTES
                || EvidenceAge.count(EvidenceAge.JUST_NOW_MILLIS) != 1) {
            throw new AssertionError("one minute");
        }
        if (EvidenceAge.count(EvidenceAge.JUST_NOW_MILLIS + 59_999) != 1) {
            throw new AssertionError("partial minute rounded up");
        }
        if (EvidenceAge.count(EvidenceAge.HOUR_MILLIS - 1) != 59) {
            throw new AssertionError("last minute");
        }
        if (EvidenceAge.unit(EvidenceAge.HOUR_MILLIS) != EvidenceAge.Unit.HOURS
                || EvidenceAge.count(EvidenceAge.HOUR_MILLIS) != 1) {
            throw new AssertionError("one hour");
        }
        if (EvidenceAge.count(EvidenceAge.HOUR_MILLIS * 5 + 1) != 5) {
            throw new AssertionError("whole hours");
        }
        try { EvidenceAge.unit(-1); throw new AssertionError("negative age accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("EvidenceAge tests passed (just now, whole minutes, whole hours)");
    }
}
