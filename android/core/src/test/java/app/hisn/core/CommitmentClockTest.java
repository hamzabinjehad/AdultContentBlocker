package app.hisn.core;

public final class CommitmentClockTest {
    private static void check(boolean value) { if (!value) throw new AssertionError("Commitment clock"); }
    public static void main(String[] args) {
        CommitmentClock clock = new CommitmentClock(60000, 1000, 7);
        check(clock.remaining(31000, 7) == 30000);
        check(clock.remaining(90000, 7) == 0);
        check(clock.remaining(500, 7) == 60000);
        check(clock.remaining(90000, 8) == 60000);
        CommitmentClock checkpoint = clock.checkpoint(31000, 7);
        check(checkpoint.remaining(500, 8) == 30000);
        CommitmentClock restarted = checkpoint.checkpoint(500, 8);
        check(restarted.remaining(10500, 8) == 20000);
        try { new CommitmentClock(-1, 0, 0); throw new AssertionError("Invalid duration"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("CommitmentClock tests passed (expiry, rollback, restart and checkpoints)");
    }
}
