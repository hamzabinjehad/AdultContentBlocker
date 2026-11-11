package app.hisn.core;

public final class InspectionHealthTest {
    private static void expect(boolean incomplete, InspectionHealth health, String step) {
        if (health.incomplete() != incomplete) throw new AssertionError(step + ": expected incomplete=" + incomplete);
    }

    public static void main(String[] args) {
        InspectionHealth health = new InspectionHealth();
        expect(false, health, "fresh service");
        health.record(InspectionHealth.Outcome.INCOMPLETE);
        expect(true, health, "partial scan");
        health.record(InspectionHealth.Outcome.PENDING);
        expect(true, health, "a queued retry does not hide the failure");
        health.record(InspectionHealth.Outcome.INCOMPLETE);
        health.record(InspectionHealth.Outcome.COMPLETE);
        expect(false, health, "a later complete scan clears it");
        health.record(InspectionHealth.Outcome.PENDING);
        expect(false, health, "a retry does not invent a failure");
        health.record(InspectionHealth.Outcome.COMPLETE);
        health.record(InspectionHealth.Outcome.INCOMPLETE);
        expect(true, health, "the latest scan decides");
        if (health.completedAt() != -1) throw new AssertionError("untimed scan is not evidence");
        health.record(InspectionHealth.Outcome.COMPLETE, 5_000);
        if (health.completedAt() != 5_000) throw new AssertionError("timed complete not recorded");
        health.record(InspectionHealth.Outcome.INCOMPLETE, 6_000);
        if (health.completedAt() != 5_000) throw new AssertionError("a failure erased the receipt");
        health.record(InspectionHealth.Outcome.COMPLETE);
        if (health.completedAt() != 5_000) throw new AssertionError("untimed success moved the receipt");
        PolicyReceipt receipt = new PolicyReceipt(9, 2, 4, PolicyReceipt.Kind.TEXT_SCAN,
                PolicyReceipt.Result.PASSED, 7_000);
        health.record(InspectionHealth.Outcome.COMPLETE, receipt);
        if (health.receipt() != receipt || health.completedAt() != 7_000) {
            throw new AssertionError("timed receipt not stored");
        }
        health.record(InspectionHealth.Outcome.INCOMPLETE, 8_000);
        if (health.receipt() != receipt || health.completedAt() != 7_000) {
            throw new AssertionError("failure erased the receipt");
        }
        health.record(InspectionHealth.Outcome.INCOMPLETE);
        expect(true, health, "the latest scan decides");
        try { health.record(null); throw new AssertionError("Missing outcome accepted"); }
        catch (IllegalArgumentException expected) { }
        expect(true, health, "a rejected record changes nothing");
        System.out.println("InspectionHealth tests passed (failures stay until a later complete scan)");
    }
}
