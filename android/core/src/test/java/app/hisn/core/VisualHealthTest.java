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
        health.report(VisualHealth.State.SAMPLED);
        expect(VisualHealth.State.SAMPLED, health, "a later inspected sample clears it");
        health.report(VisualHealth.State.WAITING);
        expect(VisualHealth.State.WAITING, health, "no failure, current state shown");
        for (VisualHealth.State clearing : new VisualHealth.State[]{VisualHealth.State.OFF, VisualHealth.State.UNSUPPORTED}) {
            health.report(VisualHealth.State.ERROR);
            health.report(clearing);
            expect(clearing, health, clearing + " clears a failure");
        }
        int failures = 0;
        for (VisualHealth.State state : VisualHealth.State.values()) if (state.failure()) failures++;
        if (failures != 2) throw new AssertionError("Unexpected failure states");
        try { health.report(null); throw new AssertionError("Missing state accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("VisualHealth tests passed (failures stay visible until a later sample)");
    }
}
