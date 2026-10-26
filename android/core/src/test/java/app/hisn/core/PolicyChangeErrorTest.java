package app.hisn.core;

public final class PolicyChangeErrorTest {
    private static void expect(PolicyChangeError expected, RuntimeException failure) {
        PolicyChangeError actual = PolicyChangeError.of(failure);
        if (actual != expected) throw new AssertionError(failure + ": expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        for (PolicyChangeError reason : PolicyChangeError.values()) {
            expect(reason, new PolicyChangeError.Refused(reason, "refused"));
            expect(reason, new PolicyChangeError.Invalid(reason, "invalid"));
        }
        // Existing callers catch these types; the reasons must not change them.
        RuntimeException tooMany = new PolicyChangeError.Invalid(PolicyChangeError.TOO_MANY_APPS, "apps");
        RuntimeException locked = new PolicyChangeError.Refused(PolicyChangeError.COMMITMENT, "locked");
        if (!(tooMany instanceof IllegalArgumentException)) throw new AssertionError("Invalid changed type");
        if (!(locked instanceof IllegalStateException)) throw new AssertionError("Refused changed type");
        expect(PolicyChangeError.INVALID, new IllegalArgumentException("Too many domain rules"));
        // Only an explicit commitment refusal may be reported as the commitment.
        expect(PolicyChangeError.UNAVAILABLE, new IllegalStateException("Boot counter unavailable"));
        expect(PolicyChangeError.UNAVAILABLE, new RuntimeException("unexpected"));
        expect(PolicyChangeError.UNAVAILABLE, null);
        System.out.println("PolicyChangeError tests passed (each refusal keeps its reason and exception type)");
    }
}
