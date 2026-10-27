package app.hisn.core;

public final class ResolverFailoverTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static void main(String[] args) {
        ResolverFailover failover = new ResolverFailover(2);
        check(failover.choose(0, -1) == 0 && failover.preferred(0) == 0, "Main resolver first");
        check(failover.choose(0, 1) == 0, "A working main resolver replaces a backup connection");

        // The main resolver cannot be reached: the same exchange moves to the backup once.
        failover.failed(0, 1000);
        check(failover.next(1, 1000) == 1, "Backup after a main failure");
        check(failover.next(3, 1000) == -1, "Each resolver at most once per exchange");
        check(failover.choose(1000, -1) == 1 && failover.choose(5000, 1) == 1, "Backup while the main one waits");
        // Concurrent attempts that began before the failure was recorded do not lengthen the wait.
        failover.failed(0, 1200);
        check(failover.choose(30999, 1) == 1, "Wait is 30 s from the first failure");

        // After the wait, exactly one connection probes the main resolver.
        check(failover.preferred(31000) == 0, "Main resolver due again");
        check(failover.choose(31000, 1) == 0, "First caller probes the main resolver");
        check(failover.choose(31001, 1) == 1 && failover.choose(31001, -1) == 1, "Others keep the backup meanwhile");
        failover.failed(0, 33000);
        check(failover.choose(92999, 1) == 1, "Second failure doubles the wait to 60 s");
        check(failover.choose(93000, 1) == 0, "Probe after 60 s");
        failover.succeeded(0);
        check(failover.choose(93001, 1) == 0 && failover.preferred(93001) == 0, "Return to the main resolver after a success");

        // Waits double up to five minutes, never longer.
        ResolverFailover capped = new ResolverFailover(2);
        long now = 0;
        for (int i = 0; i < 12; i++) {
            check(capped.choose(now, -1) == 0, "Main resolver probed when due " + i);
            capped.failed(0, now);
            long wait = Math.min(ResolverFailover.LONGEST_WAIT, ResolverFailover.FIRST_WAIT << i);
            check(capped.choose(now + wait - 1, -1) == 1, "Waiting " + wait);
            now += wait;
        }

        // A probe whose result never arrives does not hold the resolver forever.
        ResolverFailover lost = new ResolverFailover(2);
        lost.failed(0, 0);
        check(lost.choose(30000, -1) == 0, "Probe claimed");
        check(lost.choose(59999, -1) == 1, "Probe lease");
        check(lost.choose(60000, -1) == 0, "New probe after the lease");

        // Both failing: one attempt per exchange, at the resolver available soonest.
        ResolverFailover down = new ResolverFailover(2);
        down.failed(0, 0);
        check(down.next(1, 0) == 1, "Backup tried after main failure");
        down.failed(1, 500);
        check(down.next(2, 500) == -1, "No third attempt");
        check(down.choose(600, -1) == 0 && down.next(1, 600) == -1, "Soonest resolver, then stop");
        check(down.choose(600, 1) == 1, "An open connection is kept while every resolver waits");
        check(down.preferred(600) == -1, "Nothing preferred while all fail");
        check(down.choose(30000, -1) == 0, "Main probe when due");
        check(down.next(1, 30000) == -1, "Backup still waiting");
        check(down.choose(30500, -1) == 1, "Backup probe when due");

        // A cached connection to a failing main resolver is kept until it fails itself.
        ResolverFailover cached = new ResolverFailover(2);
        cached.failed(0, 0);
        check(cached.choose(10, 0) == 0, "Established main connection kept");

        // A network change gives every resolver a fresh chance, main first.
        down.reset();
        check(down.choose(31000, 1) == 0 && down.preferred(31000) == 0, "Reset returns to the main resolver");

        ResolverFailover single = new ResolverFailover(1);
        single.failed(0, 0);
        check(single.next(1, 0) == -1 && single.choose(10, -1) == 0, "Single resolver has no fallback");
        try { new ResolverFailover(0); throw new AssertionError("Empty resolver list"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("ResolverFailover tests passed (main first, one backup attempt, single probe, 30 s to 5 min waits, reset)");
    }
}
