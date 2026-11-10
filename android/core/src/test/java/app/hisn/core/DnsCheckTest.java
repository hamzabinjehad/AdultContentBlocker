package app.hisn.core;

public final class DnsCheckTest {
    private static void expect(DnsCheck.Outcome outcome, boolean passed, boolean observed,
            boolean network, boolean policy, boolean session, String step) {
        DnsCheck.Outcome actual = DnsCheck.outcome(passed, observed, network, policy, session);
        if (actual != outcome) throw new AssertionError(step + ": expected " + outcome + ", got " + actual);
    }

    public static void main(String[] args) {
        expect(DnsCheck.Outcome.PASSED, true, true, true, true, true, "current successful check");
        expect(DnsCheck.Outcome.FAILED, true, false, true, true, true, "probe not seen in the tunnel");
        expect(DnsCheck.Outcome.FAILED, false, true, true, true, true, "probe failed");

        // A late success from an earlier network generation, policy or service must not certify.
        expect(DnsCheck.Outcome.STALE, true, true, false, true, true, "network changed");
        expect(DnsCheck.Outcome.STALE, true, true, true, false, true, "policy changed");
        expect(DnsCheck.Outcome.STALE, true, true, true, true, false, "service restarted");
        expect(DnsCheck.Outcome.STALE, false, false, true, false, false, "old failed check is stale");
        System.out.println("DnsCheck tests passed (network, policy and session generations gate a route result)");
    }
}
