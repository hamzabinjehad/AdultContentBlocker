package app.hisn.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class BrowserCanaryTest {
    private static final DnsPolicy.Rules ALLOW = host -> DomainPolicy.Decision.ALLOW;

    public static void main(String[] args) throws Exception {
        exactHostAndFallback();
        dnsMessageIntegration();
        deadlineClearAndRearm();
        clockFailureAndRollback();
        concurrentEvaluations();
        invalidDependencies();
        System.out.println("BrowserCanary tests passed (fixed host, DNS messages, bounded lease, owner policy, rollback, concurrency)");
    }

    private static void exactHostAndFallback() {
        AtomicLong now = new AtomicLong(100);
        BrowserCanary canary = new BrowserCanary(now::get);
        check(!canary.snapshot().active && canary.snapshot().remainingMillis == 0, "Must start inactive");
        check(canary.evaluate(BrowserCanary.HOST, ALLOW) == DomainPolicy.Decision.ALLOW,
                "Inactive diagnostic changed owner policy");
        canary.arm();
        check(canary.snapshot().active && canary.snapshot().remainingMillis == 120000, "Lease not armed");
        for (String host : new String[]{BrowserCanary.HOST, "WWW.EXAMPLE.ORG", "WwW.Example.Org."}) {
            check(canary.evaluate(host, ALLOW) == DomainPolicy.Decision.BLOCK, "Exact DNS name not blocked");
        }
        check(canary.snapshot().policyEvaluations == 3, "Diagnostic count must count matching evaluations");
        AtomicLong ownerCalls = new AtomicLong();
        DnsPolicy.Rules owner = host -> {
            ownerCalls.incrementAndGet();
            if ("blocked.example".equals(host)) return DomainPolicy.Decision.BLOCK;
            if (host == null || "invalid".equals(host)) return DomainPolicy.Decision.INVALID;
            return DomainPolicy.Decision.ALLOW;
        };
        for (String host : new String[]{"example.org", "sub.www.example.org", "www.example.org.evil.example",
                "www.example.org..", " www.example.org", "www.example.org ", "https://www.example.org",
                "", "www.example.org:443"}) {
            check(canary.evaluate(host, owner) == DomainPolicy.Decision.ALLOW, "Non-exact host overridden");
        }
        check(canary.evaluate("blocked.example", owner) == DomainPolicy.Decision.BLOCK, "Owner block weakened");
        check(canary.evaluate("invalid", owner) == DomainPolicy.Decision.INVALID, "Owner invalid changed");
        check(canary.evaluate(null, owner) == DomainPolicy.Decision.INVALID, "Null not delegated unchanged");
        check(ownerCalls.get() == 12 && canary.snapshot().policyEvaluations == 3, "Fallback/count isolation");
        canary.clear();
        check(canary.evaluate(BrowserCanary.HOST, host -> DomainPolicy.Decision.BLOCK) == DomainPolicy.Decision.BLOCK,
                "Clearing diagnostic bypassed owner block of the same host");
        check(canary.evaluate(BrowserCanary.HOST, host -> DomainPolicy.Decision.INVALID) == DomainPolicy.Decision.INVALID,
                "Clearing diagnostic bypassed owner invalid of the same host");
    }

    private static void deadlineClearAndRearm() {
        AtomicLong now = new AtomicLong(500);
        BrowserCanary canary = new BrowserCanary(now::get);
        canary.arm();
        BrowserCanary.Snapshot armed = canary.snapshot();
        canary.evaluate(BrowserCanary.HOST, ALLOW);
        now.addAndGet(119999);
        check(canary.snapshot().active && canary.snapshot().remainingMillis == 1, "Lease expired early");
        check(canary.evaluate(BrowserCanary.HOST, ALLOW) == DomainPolicy.Decision.BLOCK, "Last millisecond not active");
        now.incrementAndGet();
        check(!canary.snapshot().active && canary.snapshot().remainingMillis == 0, "Exact deadline did not expire");
        check(canary.evaluate(BrowserCanary.HOST, ALLOW) == DomainPolicy.Decision.ALLOW, "Expired deny retained");
        check(canary.snapshot().policyEvaluations == 2, "Expiry erased evidence");
        check(armed.active && armed.remainingMillis == 120000 && armed.policyEvaluations == 0, "Snapshot mutated");
        canary.arm();
        check(canary.snapshot().active && canary.snapshot().remainingMillis == 120000
                && canary.snapshot().policyEvaluations == 0, "Rearm did not reset lease/count");
        canary.evaluate(BrowserCanary.HOST, ALLOW);
        canary.clear();
        check(!canary.snapshot().active && canary.snapshot().remainingMillis == 0
                && canary.snapshot().policyEvaluations == 1, "Clear did not retain only evidence");
        now.addAndGet(1000000);
        check(canary.evaluate(BrowserCanary.HOST, ALLOW) == DomainPolicy.Decision.ALLOW, "Clear reactivated");
        canary.arm();
        check(canary.snapshot().policyEvaluations == 0, "Rearm after clear retained old evidence");
    }

    private static void dnsMessageIntegration() throws Exception {
        AtomicLong now = new AtomicLong(1000), forwarded = new AtomicLong();
        AtomicBoolean ownerBlocksCanary = new AtomicBoolean();
        BrowserCanary canary = new BrowserCanary(now::get);
        DnsPolicy.Rules owner = host -> ownerBlocksCanary.get() && BrowserCanary.HOST.equals(host)
                ? DomainPolicy.Decision.BLOCK : DomainPolicy.Decision.ALLOW;
        DnsExchange exchange = new DnsExchange(new DnsPolicy(host -> canary.evaluate(host, owner)), request -> {
            forwarded.incrementAndGet();
            byte[] reply = request.clone();
            reply[2] = (byte) 0x81;
            reply[3] = (byte) 0x80;
            return reply;
        });
        canary.arm();
        for (int type : new int[]{1, 28, 65}) assertLocalNxDomain(exchange, type);
        check(forwarded.get() == 0, "Active A/AAAA/HTTPS canary queries reached transport");
        check(canary.snapshot().policyEvaluations == 3, "DNS-message canary evaluations not counted");

        canary.clear();
        ownerBlocksCanary.set(true);
        for (int type : new int[]{1, 28, 65}) assertLocalNxDomain(exchange, type);
        check(forwarded.get() == 0, "Clear bypassed owner block or forwarded blocked query");
        byte[] safe = DnsPolicyTest.query("safe.example", 1);
        DnsExchange.Resolution allowed = exchange.resolveDetailed(safe);
        check(!allowed.blocked && (allowed.answer[3] & 15) == 0
                && allowed.answer[0] == safe[0] && allowed.answer[1] == safe[1]
                && forwarded.get() == 1, "Clear interfered with harmless owner-allowed query");

        canary.arm();
        now.addAndGet(BrowserCanary.LEASE_MILLIS);
        for (int type : new int[]{1, 28, 65}) assertLocalNxDomain(exchange, type);
        check(!canary.snapshot().active && canary.snapshot().policyEvaluations == 0
                && forwarded.get() == 1, "Expiry weakened owner block or retained diagnostic deny");
        ownerBlocksCanary.set(false);
        DnsExchange.Resolution expiredAllowed = exchange.resolveDetailed(DnsPolicyTest.query(BrowserCanary.HOST, 28));
        check(!expiredAllowed.blocked && (expiredAllowed.answer[3] & 15) == 0 && forwarded.get() == 2,
                "Expired diagnostic did not delegate owner-allowed hostname to transport");
    }

    private static void assertLocalNxDomain(DnsExchange exchange, int type) throws Exception {
        byte[] query = DnsPolicyTest.query(BrowserCanary.HOST, type), original = query.clone();
        DnsExchange.Resolution result = exchange.resolveDetailed(query);
        byte[] expected = original.clone();
        expected[2] = (byte) 0x81;
        expected[3] = 3;
        check(result.blocked && java.util.Arrays.equals(result.answer, expected)
                && java.util.Arrays.equals(query, original),
                "Local NXDOMAIN must preserve ID/question, leave counts empty and retain caller input for type " + type);
        check(!DnsReplyGuard.blocks(result.answer, ALLOW), "Local NXDOMAIN failed DNS reply-structure validation");
    }

    private static void clockFailureAndRollback() {
        AtomicLong now = new AtomicLong(1000);
        AtomicBoolean failClock = new AtomicBoolean();
        BrowserCanary canary = new BrowserCanary(() -> {
            if (failClock.get()) throw new IllegalStateException("Test clock unavailable");
            return now.get();
        });
        canary.arm();
        now.set(2000);
        check(canary.snapshot().remainingMillis == 119000, "Elapsed time not sampled");
        now.set(1999);
        check(canary.evaluate(BrowserCanary.HOST, ALLOW) == DomainPolicy.Decision.ALLOW,
                "Rollback after a later sample extended lease");
        check(!canary.snapshot().active, "Rollback did not expire permanently");
        now.set(2000);
        check(!canary.snapshot().active, "Clock recovery reactivated lease");
        canary.arm();
        now.set(1999);
        check(!canary.snapshot().active, "Rollback before arm time not expired");
        now.set(Long.MIN_VALUE);
        canary.arm();
        now.set(Long.MAX_VALUE);
        check(!canary.snapshot().active, "Elapsed overflow extended lease");
        now.set(Long.MAX_VALUE - 100);
        canary.arm();
        now.set(Long.MIN_VALUE);
        check(!canary.snapshot().active, "Clock wrap extended lease");
        now.set(3000);
        canary.arm();
        failClock.set(true);
        check(canary.evaluate(BrowserCanary.HOST, ALLOW) == DomainPolicy.Decision.ALLOW,
                "Unavailable clock retained temporary deny");
        check(!canary.snapshot().active, "Unavailable clock did not expire");
        failClock.set(false);
        canary.arm();
        failClock.set(true);
        expectIllegalState(canary::arm);
        failClock.set(false);
        check(!canary.snapshot().active, "Failed rearm retained earlier lease");
    }

    private static void concurrentEvaluations() throws Exception {
        AtomicLong now = new AtomicLong(1000);
        BrowserCanary canary = new BrowserCanary(now::get);
        canary.arm();
        CountDownLatch ready = new CountDownLatch(8), start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Thread worker = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int j = 0; j < 1000; j++) {
                        check(canary.evaluate(BrowserCanary.HOST, ALLOW) == DomainPolicy.Decision.BLOCK,
                                "Concurrent matching evaluation lost deny");
                        check(canary.evaluate("other.example", ALLOW) == DomainPolicy.Decision.ALLOW,
                                "Concurrent fallback changed");
                        BrowserCanary.Snapshot state = canary.snapshot();
                        check(state.active && state.remainingMillis == 120000, "Concurrent state torn");
                    }
                } catch (Throwable problem) { failure.compareAndSet(null, problem); }
            }, "hisn-canary-test-" + i);
            threads.add(worker);
            worker.start();
        }
        check(ready.await(5, java.util.concurrent.TimeUnit.SECONDS), "Workers did not start");
        start.countDown();
        for (Thread worker : threads) {
            worker.join(10000);
            check(!worker.isAlive(), "Concurrent evaluation stalled");
        }
        if (failure.get() != null) throw new AssertionError("Concurrent test failure", failure.get());
        check(canary.snapshot().policyEvaluations == 8000, "Concurrent matching counts lost");
        canary.clear();
        check(!canary.snapshot().active && canary.snapshot().policyEvaluations == 8000,
                "Clear after concurrency changed evidence");
    }

    private static void invalidDependencies() {
        try { new BrowserCanary(null); throw new AssertionError("Null clock accepted"); }
        catch (IllegalArgumentException expected) { }
        BrowserCanary canary = new BrowserCanary(() -> 0);
        canary.arm();
        try { canary.evaluate(BrowserCanary.HOST, null); throw new AssertionError("Missing owner policy accepted"); }
        catch (IllegalArgumentException expected) { }
    }

    private static void expectIllegalState(Runnable task) {
        try { task.run(); throw new AssertionError("Expected clock failure"); }
        catch (IllegalStateException expected) { }
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
