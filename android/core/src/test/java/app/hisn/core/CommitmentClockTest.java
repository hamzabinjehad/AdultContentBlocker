package app.hisn.core;

public final class CommitmentClockTest {
    private static void check(boolean value) { if (!value) throw new AssertionError("Commitment clock"); }
    public static void main(String[] args) {
        CommitmentClock clock = new CommitmentClock(60000, 1000, 7);
        check(clock.remaining(31000, 7) == 30000);
        check(clock.remaining(90000, 7) == 0);
        check(clock.remaining(500, 7) == 60000);
        // A restart counts the running time since boot, so a commitment cannot outlive its duration.
        check(clock.remaining(90000, 8) == 0);
        check(clock.remaining(20000, 8) == 40000);
        CommitmentClock checkpoint = clock.checkpoint(31000, 7);
        check(checkpoint.remaining(500, 8) == 29500);
        CommitmentClock restarted = checkpoint.checkpoint(500, 8);
        check(restarted.remaining(10500, 8) == 19500);
        check(restarted.remaining(100, 8) == 29500);
        check(restarted.checkpoint(30000, 9).remaining(30000, 9) == 0);
        try { new CommitmentClock(-1, 0, 0); throw new AssertionError("Invalid duration"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("CommitmentClock tests passed (expiry, rollback, restart time since boot and checkpoints)");
    }
}
