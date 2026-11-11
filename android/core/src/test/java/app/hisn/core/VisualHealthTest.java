package app.hisn.core;

public final class VisualHealthTest {
    private static void expect(VisualHealth.State shown, VisualHealth health, String step) {
        if (health.shown() != shown) throw new AssertionError(step + ": expected " + shown + ", got " + health.shown());
    }

    public static void main(String[] args) {
        VisualHealth health = new VisualHealth();
        expect(VisualHealth.State.OFF, health, "fresh");
        health.report(VisualHealth.State.LOADING);
        health.report(VisualHealth.State.ERROR);
        // The two-second poll used to overwrite this with WAITING at once.
        for (VisualHealth.State transient_ : new VisualHealth.State[]{VisualHealth.State.WAITING,
                VisualHealth.State.LOADING, VisualHealth.State.CHECKING, VisualHealth.State.INPUT}) {
            health.report(transient_);
            expect(VisualHealth.State.ERROR, health, transient_ + " after a failure");
            if (health.current() != transient_) throw new AssertionError("Diagnostics lost the current state");
        }
        health.report(VisualHealth.State.UNREADABLE);
        health.report(VisualHealth.State.WAITING);
        expect(VisualHealth.State.UNREADABLE, health, "latest failure wins");
        health.report(VisualHealth.State.PROTECTED);
        health.report(VisualHealth.State.WAITING);
        expect(VisualHealth.State.PROTECTED, health, "protected window stays visible until a sample");
        health.report(VisualHealth.State.SAMPLED);
        expect(VisualHealth.State.SAMPLED, health, "a later inspected sample clears it");
        if (health.sampledAt() != -1) throw new AssertionError("untimed sample is not evidence");
        health.report(VisualHealth.State.SAMPLED, 7_000);
        if (health.sampledAt() != 7_000) throw new AssertionError("timed sample not recorded");
        health.report(VisualHealth.State.WAITING, 8_000);
        if (health.sampledAt() != 7_000) throw new AssertionError("waiting erased the receipt");
        PolicyReceipt receipt = new PolicyReceipt(4, 3, 9, PolicyReceipt.Kind.VISUAL_SAMPLE,
                PolicyReceipt.Result.PASSED, 9_000);
        health.report(VisualHealth.State.SAMPLED, 9_000, receipt);
        if (health.receipt() != receipt || health.sampledAt() != 9_000) {
            throw new AssertionError("visual receipt not stored");
        }
        health.report(VisualHealth.State.ERROR, 10_000);
        if (!health.shown().failure() || health.receipt() != receipt) {
            throw new AssertionError("visual failure erased the receipt or stayed hidden");
        }
        health.report(VisualHealth.State.SAMPLED);
        if (health.sampledAt() != 9_000) throw new AssertionError("untimed sample moved the receipt");
        health.report(VisualHealth.State.WAITING);
        expect(VisualHealth.State.WAITING, health, "no failure, current state shown");
        for (VisualHealth.State clearing : new VisualHealth.State[]{VisualHealth.State.OFF, VisualHealth.State.UNSUPPORTED}) {
            health.report(VisualHealth.State.ERROR);
            health.report(clearing);
            expect(clearing, health, clearing + " clears a failure");
        }
        int failures = 0;
        for (VisualHealth.State state : VisualHealth.State.values()) if (state.failure()) failures++;
        if (failures != 3) throw new AssertionError("Unexpected failure states");
        try { health.report(null); throw new AssertionError("Missing state accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("VisualHealth tests passed (failures stay visible until a later sample)");
    }
}
