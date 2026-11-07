package app.hisn.core;

public final class VisualRetryTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        VisualRetry retry = new VisualRetry();
        check(retry.pending() == VisualRetry.FIRST_MILLIS, "starts at the first wait");
        check(retry.failed() == 5_000, "first failure waits 5 s");
        check(retry.failed() == 10_000, "second failure doubles");
        check(retry.failed() == 20_000, "third failure doubles");
        check(retry.failed() == 30_000, "fourth failure reaches the cap");
        check(retry.failed() == 30_000, "the cap holds");
        check(retry.pending() == 30_000, "pending stays at the cap");
        retry.recovered();
        check(retry.pending() == VisualRetry.FIRST_MILLIS, "a finished sample clears the escalation");
        check(retry.failed() == 5_000, "after recovery the short wait returns");
        check(VisualRetry.MAX_MILLIS == 30_000 && VisualRetry.FIRST_MILLIS == 5_000, "constants are the documented ones");
        System.out.println("VisualRetry tests passed (5 s first, doubling to a 30 s cap, recovery resets)");
    }
}
