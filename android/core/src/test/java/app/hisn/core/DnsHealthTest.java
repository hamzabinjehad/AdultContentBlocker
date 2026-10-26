package app.hisn.core;

public final class DnsHealthTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        DnsHealth health = new DnsHealth();
        check(!health.degraded() && !health.success(0), "healthy session has nothing to recover");
        health.failure(1_000);
        check(health.degraded(), "failure degrades");
        long quietEnd = 1_000 + DnsHealth.QUIET_MILLIS;
        for (int i = 0; i < DnsHealth.MIN_SUCCESSES * 3; i++) {
            check(!health.success(quietEnd - 1), "recovered before the quiet period ended");
        }
        check(health.degraded(), "successes alone do not recover");
        check(health.success(quietEnd) && !health.degraded(), "recovers after quiet period with successes");
        check(!health.success(quietEnd + 1), "recovery is reported once");

        health.failure(10_000);
        for (int i = 1; i < DnsHealth.MIN_SUCCESSES; i++) {
            check(!health.success(10_000 + DnsHealth.QUIET_MILLIS * 2), "recovered with too few successes " + i);
        }
        health.failure(10_000 + DnsHealth.QUIET_MILLIS * 2);
        long restart = 10_000 + DnsHealth.QUIET_MILLIS * 2;
        for (int i = 1; i < DnsHealth.MIN_SUCCESSES; i++) check(!health.success(restart + DnsHealth.QUIET_MILLIS), "new failure resets successes");
        check(health.degraded(), "new failure restarts the quiet period");
        check(health.success(restart + DnsHealth.QUIET_MILLIS), "recovers after a fresh full period");
        System.out.println("DnsHealth tests passed (degrade, quiet period, minimum successes, reset on new failure)");
    }
}
