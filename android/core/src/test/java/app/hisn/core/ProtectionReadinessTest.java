package app.hisn.core;

import java.util.Arrays;
import java.util.Collections;

public final class ProtectionReadinessTest {
    private static final long NOW = 1_000_000L;
    private static final long MAX_AGE = 60_000L;

    private static ProtectionReadiness.Facts facts(Boolean permission, boolean running,
            boolean starting, boolean failed, long evidenceAt) {
        return ProtectionReadiness.Facts.expected(permission, running, starting, failed, evidenceAt,
                -1, -1);
    }

    private static void expect(ProtectionReadiness.Layer layer,
            ProtectionReadiness.State state, ProtectionReadiness.Reason reason,
            ProtectionReadiness.Facts facts) {
        ProtectionReadiness.Status status = ProtectionReadiness.evaluate(layer, facts, NOW, MAX_AGE);
        if (status.state != state || status.reason != reason) {
            throw new AssertionError(layer + ": expected " + state + "/" + reason + ", got "
                    + status.state + "/" + status.reason);
        }
    }

    private static void expectAge(long age, ProtectionReadiness.Facts facts) {
        ProtectionReadiness.Status status = ProtectionReadiness.evaluate(
                ProtectionReadiness.Layer.DNS, facts, NOW, MAX_AGE);
        if (status.evidenceAgeMillis != age) {
            throw new AssertionError("expected age " + age + ", got " + status.evidenceAgeMillis);
        }
    }

    public static void main(String[] args) {
        ProtectionReadiness.Layer dns = ProtectionReadiness.Layer.DNS;
        ProtectionReadiness.Facts off = ProtectionReadiness.Facts.off();
        ProtectionReadiness.Facts unsupported = ProtectionReadiness.Facts.unsupported();
        expect(dns, ProtectionReadiness.State.OFF, ProtectionReadiness.Reason.NOT_CHOSEN, off);
        expect(dns, ProtectionReadiness.State.UNSUPPORTED,
                ProtectionReadiness.Reason.NOT_SUPPORTED, unsupported);
        expectAge(-1, off);

        // A granted permission is not a running service, and a running service is not evidence.
        expect(dns, ProtectionReadiness.State.NEEDS_ACTION,
                ProtectionReadiness.Reason.PERMISSION,
                facts(Boolean.FALSE, false, false, false, -1));
        expect(dns, ProtectionReadiness.State.NEEDS_ACTION,
                ProtectionReadiness.Reason.NOT_RUNNING,
                facts(null, false, false, false, -1));
        expect(dns, ProtectionReadiness.State.STARTING, ProtectionReadiness.Reason.NONE,
                facts(Boolean.TRUE, false, true, false, -1));
        expect(dns, ProtectionReadiness.State.DEGRADED, ProtectionReadiness.Reason.FAILURE,
                facts(Boolean.TRUE, true, false, true, -1));
        expect(dns, ProtectionReadiness.State.NOT_CHECKED,
                ProtectionReadiness.Reason.NO_EVIDENCE,
                facts(Boolean.TRUE, true, false, false, -1));

        // A future timestamp is a clock anomaly, not fresh evidence.
        expect(dns, ProtectionReadiness.State.NOT_CHECKED,
                ProtectionReadiness.Reason.NO_EVIDENCE,
                facts(Boolean.TRUE, true, false, false, NOW + 1));
        expectAge(-1, facts(Boolean.TRUE, true, false, false, NOW + 1));

        // Exactly at the limit is still fresh; one millisecond past it is not.
        ProtectionReadiness.Facts atLimit = facts(Boolean.TRUE, true, false, false, NOW - MAX_AGE);
        expect(dns, ProtectionReadiness.State.WORKING, ProtectionReadiness.Reason.NONE, atLimit);
        expectAge(MAX_AGE, atLimit);
        expect(dns, ProtectionReadiness.State.NOT_CHECKED,
                ProtectionReadiness.Reason.STALE_EVIDENCE,
                facts(Boolean.TRUE, true, false, false, NOW - MAX_AGE - 1));

        ProtectionReadiness.Facts fresh = facts(Boolean.TRUE, true, false, false, NOW - 2_000L);
        ProtectionReadiness.Status working = ProtectionReadiness.evaluate(dns, fresh, NOW, MAX_AGE);
        if (!working.working() || working.attention()) throw new AssertionError("fresh is working");
        if (working.action() != ProtectionReadiness.Action.NONE) throw new AssertionError("no action");
        if (ProtectionReadiness.evaluate(dns, facts(Boolean.TRUE, true, false, false, -1), NOW, MAX_AGE)
                .action() != ProtectionReadiness.Action.RUN_CHECK) throw new AssertionError("run check");
        if (ProtectionReadiness.evaluate(dns, facts(Boolean.FALSE, false, false, false, -1), NOW, MAX_AGE)
                .action() != ProtectionReadiness.Action.GRANT_PERMISSION) throw new AssertionError("permission");
        if (ProtectionReadiness.evaluate(dns, facts(null, false, false, false, -1), NOW, MAX_AGE)
                .action() != ProtectionReadiness.Action.START_SERVICE) throw new AssertionError("start");

        // A saved revision the enforcing service did not apply is never called working.
        ProtectionReadiness.Facts staleRevision = ProtectionReadiness.Facts.expected(
                Boolean.TRUE, true, false, false, NOW - 1000L, 7, 6);
        ProtectionReadiness.Status revision = ProtectionReadiness.evaluate(dns, staleRevision, NOW, MAX_AGE);
        expect(dns, ProtectionReadiness.State.NEEDS_ACTION, ProtectionReadiness.Reason.REVISION,
                staleRevision);
        if (!revision.identityMismatch) throw new AssertionError("mismatch not reported");
        if (revision.action() != ProtectionReadiness.Action.RETRY) throw new AssertionError("retry");
        // An unknown revision is not a mismatch; the evidence still decides.
        expect(dns, ProtectionReadiness.State.WORKING, ProtectionReadiness.Reason.NONE,
                ProtectionReadiness.Facts.expected(Boolean.TRUE, true, false, false, NOW - 1000L, -1, -1));
        expect(dns, ProtectionReadiness.State.WORKING, ProtectionReadiness.Reason.NONE,
                ProtectionReadiness.Facts.expected(Boolean.TRUE, true, false, false, NOW - 1000L, 7, 7));

        // Summary ignores an unchosen layer and treats an unsupported one as a gap, not a failure.
        ProtectionReadiness.Status offStatus = ProtectionReadiness.evaluate(dns, off, NOW, MAX_AGE);
        ProtectionReadiness.Status unsupportedStatus = ProtectionReadiness.evaluate(dns, unsupported, NOW, MAX_AGE);
        ProtectionReadiness.Status workingStatus = ProtectionReadiness.evaluate(dns, fresh, NOW, MAX_AGE);
        ProtectionReadiness.Status unchecked = ProtectionReadiness.evaluate(dns,
                facts(Boolean.TRUE, true, false, false, -1), NOW, MAX_AGE);
        ProtectionReadiness.Status starting = ProtectionReadiness.evaluate(dns,
                facts(Boolean.TRUE, false, true, false, -1), NOW, MAX_AGE);
        ProtectionReadiness.Status broken = ProtectionReadiness.evaluate(dns,
                facts(Boolean.TRUE, true, false, true, -1), NOW, MAX_AGE);
        if (ProtectionReadiness.summarize(Collections.emptyList())
                != ProtectionReadiness.Summary.OFF) throw new AssertionError("empty off");
        if (ProtectionReadiness.summarize(Collections.singletonList(offStatus))
                != ProtectionReadiness.Summary.OFF) throw new AssertionError("unchosen off");
        if (ProtectionReadiness.summarize(Collections.singletonList(unchecked))
                != ProtectionReadiness.Summary.NOT_CHECKED) throw new AssertionError("not checked");
        if (ProtectionReadiness.summarize(Collections.singletonList(starting))
                != ProtectionReadiness.Summary.STARTING) throw new AssertionError("starting");
        if (ProtectionReadiness.summarize(Arrays.asList(workingStatus, unchecked))
                != ProtectionReadiness.Summary.PARTIAL) throw new AssertionError("partial");
        if (ProtectionReadiness.summarize(Arrays.asList(workingStatus, unsupportedStatus))
                != ProtectionReadiness.Summary.WORKING) throw new AssertionError("unsupported gap");
        if (ProtectionReadiness.summarize(Arrays.asList(workingStatus, broken))
                != ProtectionReadiness.Summary.ACTION_NEEDED) throw new AssertionError("action");
        if (ProtectionReadiness.summarize(Arrays.asList(workingStatus, unchecked, broken))
                != ProtectionReadiness.Summary.ACTION_NEEDED) throw new AssertionError("action wins");

        // Bad input is refused, not silently turned into a working verdict.
        try { ProtectionReadiness.evaluate(null, fresh, NOW, MAX_AGE); throw new AssertionError("null layer"); }
        catch (IllegalArgumentException expected) { }
        try { ProtectionReadiness.evaluate(dns, null, NOW, MAX_AGE); throw new AssertionError("null facts"); }
        catch (IllegalArgumentException expected) { }
        try { ProtectionReadiness.evaluate(dns, fresh, NOW, 0); throw new AssertionError("zero age"); }
        catch (IllegalArgumentException expected) { }
        try { ProtectionReadiness.summarize(null); throw new AssertionError("null statuses"); }
        catch (IllegalArgumentException expected) { }
        try { ProtectionReadiness.summarize(Arrays.asList(workingStatus, null)); throw new AssertionError("null status"); }
        catch (IllegalArgumentException expected) { }

        System.out.println("ProtectionReadiness tests passed (evidence, revision, failure and honesty)");
    }
}
