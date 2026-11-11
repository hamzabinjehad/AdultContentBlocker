package app.hisn.core;

public final class PolicyReceiptTest {
    private static final long NOW = 10_000L;
    private static final long MAX = 5_000L;

    private static PolicyReceipt receipt(long identity, int session, long generation,
            PolicyReceipt.Kind kind, PolicyReceipt.Result result, long at) {
        return new PolicyReceipt(identity, session, generation, kind, result, at);
    }

    public static void main(String[] args) {
        PolicyReceipt passed = receipt(7, 2, 11, PolicyReceipt.Kind.TEXT_SCAN,
                PolicyReceipt.Result.PASSED, NOW - 1_000L);
        if (!passed.matches(7, 2, 11)) throw new AssertionError("matching receipt rejected");
        if (!passed.usable(7, 2, 11, NOW, MAX)) throw new AssertionError("fresh receipt unusable");
        if (passed.failed()) throw new AssertionError("passed receipt marked failed");

        // A saved policy, service session or window/network generation change invalidates it.
        if (passed.matches(8, 2, 11) || passed.matches(7, 3, 11) || passed.matches(7, 2, 12)) {
            throw new AssertionError("receipt matched a different context");
        }
        if (passed.usable(8, 2, 11, NOW, MAX) || passed.usable(7, 3, 11, NOW, MAX)
                || passed.usable(7, 2, 12, NOW, MAX)) {
            throw new AssertionError("changed context still certified");
        }

        // A failed check is not evidence, and a future time is not fresh.
        PolicyReceipt failed = receipt(7, 2, 11, PolicyReceipt.Kind.ROUTE_CHECK,
                PolicyReceipt.Result.FAILED, NOW - 1_000L);
        if (failed.usable(7, 2, 11, NOW, MAX) || !failed.failed()) throw new AssertionError("failure certified");
        PolicyReceipt future = receipt(7, 2, 11, PolicyReceipt.Kind.VISUAL_SAMPLE,
                PolicyReceipt.Result.PASSED, NOW + 1L);
        if (future.usable(7, 2, 11, NOW, MAX)) throw new AssertionError("future receipt certified");

        // Exactly at the limit is usable; one millisecond beyond is not.
        if (!receipt(7, 2, 11, PolicyReceipt.Kind.APP_DECISION, PolicyReceipt.Result.PASSED, NOW - MAX)
                .usable(7, 2, 11, NOW, MAX)) throw new AssertionError("limit boundary rejected");
        if (receipt(7, 2, 11, PolicyReceipt.Kind.APP_DECISION, PolicyReceipt.Result.PASSED, NOW - MAX - 1)
                .usable(7, 2, 11, NOW, MAX)) throw new AssertionError("stale receipt certified");

        try { receipt(-1, 0, 0, PolicyReceipt.Kind.TEXT_SCAN, PolicyReceipt.Result.PASSED, 0); throw new AssertionError("negative identity"); }
        catch (IllegalArgumentException expected) { }
        try { receipt(1, -1, 0, PolicyReceipt.Kind.TEXT_SCAN, PolicyReceipt.Result.PASSED, 0); throw new AssertionError("negative session"); }
        catch (IllegalArgumentException expected) { }
        try { receipt(1, 0, -1, PolicyReceipt.Kind.TEXT_SCAN, PolicyReceipt.Result.PASSED, 0); throw new AssertionError("negative generation"); }
        catch (IllegalArgumentException expected) { }
        try { receipt(1, 0, 0, null, PolicyReceipt.Result.PASSED, 0); throw new AssertionError("missing kind"); }
        catch (IllegalArgumentException expected) { }
        try { receipt(1, 0, 0, PolicyReceipt.Kind.TEXT_SCAN, null, 0); throw new AssertionError("missing result"); }
        catch (IllegalArgumentException expected) { }
        try { receipt(1, 0, 0, PolicyReceipt.Kind.TEXT_SCAN, PolicyReceipt.Result.PASSED, -1); throw new AssertionError("negative time"); }
        catch (IllegalArgumentException expected) { }
        try { passed.usable(7, 2, 11, NOW, 0); throw new AssertionError("zero max age"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("PolicyReceipt tests passed (identity/session/generation/time gate every receipt)");
    }
}
