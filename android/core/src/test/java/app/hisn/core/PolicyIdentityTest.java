package app.hisn.core;

public final class PolicyIdentityTest {
    public static void main(String[] args) {
        long first = PolicyIdentity.of(3, 9);
        if (first != PolicyIdentity.of(3, 9)) throw new AssertionError("identity is not stable");
        if (first == PolicyIdentity.of(4, 9)) throw new AssertionError("revision did not change identity");
        if (first == PolicyIdentity.of(3, 10)) throw new AssertionError("component did not change identity");
        if (first == PolicyIdentity.UNKNOWN) throw new AssertionError("reserved unknown returned");
        if (PolicyIdentity.component("") != PolicyIdentity.component("")) {
            throw new AssertionError("text component is not stable");
        }
        if (PolicyIdentity.component("a") == PolicyIdentity.component("b")) {
            throw new AssertionError("different text has the same component");
        }
        if (PolicyIdentity.component("مرحبا") != PolicyIdentity.component("مرحبا")) {
            throw new AssertionError("Arabic component is not stable");
        }
        long combined = PolicyIdentity.combine(1, 2);
        if (combined != PolicyIdentity.combine(1, 2)) throw new AssertionError("combine is not stable");
        if (combined == PolicyIdentity.combine(2, 1)) throw new AssertionError("combine ignores order");
        // Consumers use -1 as unknown and identity >= 0 as readable. Every produced identity
        // must honour that, including revisions/components whose FNV result has the high bit set.
        for (long revision = 0; revision < 128; revision++) {
            for (long component = -64; component < 128; component++) {
                long identity = PolicyIdentity.of(revision, component);
                if (identity < 0 || identity == PolicyIdentity.UNKNOWN) {
                    throw new AssertionError("non-readable identity produced");
                }
            }
        }
        try { PolicyIdentity.of(-1, 0); throw new AssertionError("negative revision accepted"); }
        catch (IllegalArgumentException expected) { }
        try { PolicyIdentity.component(null); throw new AssertionError("missing text accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("PolicyIdentity tests passed (stable, layer-scoped, order-sensitive, unknown reserved)");
    }
}
