package app.hisn.core;

import java.nio.charset.StandardCharsets;

/**
 * A stable identifier for the policy inputs one layer actually enforces.
 *
 * <p>The saved policy version is the base; a layer combines it with the other inputs that
 * change its behaviour (SafeSearch and list generation for DNS, the signed vocabulary for
 * text, the bundled model revision for image checks). The mix is FNV-1a over a fixed byte
 * order, so the same inputs give the same value across processes and launches. It is a
 * receipt identifier, not a security signature, and {@link #UNKNOWN} is reserved.</p>
 */
public final class PolicyIdentity {
    public static final long UNKNOWN = -1;
    private static final long OFFSET = 0xcbf29ce484222325L;
    private static final long PRIME = 0x100000001b3L;

    /**
     * One layer identity: the saved version plus a component describing that layer's inputs.
     * The high bit is cleared so every identity is non-negative; -1 stays reserved for "not
     * known", and the rest of the app may safely use {@code identity >= 0} as its readable check.
     */
    public static long of(long revision, long component) {
        if (revision < 0) throw new IllegalArgumentException("Invalid policy revision");
        return combine(revision, component) & Long.MAX_VALUE;
    }

    /** Combine two components in a fixed order; the result is not a reserved unknown. */
    public static long combine(long first, long second) {
        long hash = mix(OFFSET, first);
        hash = mix(hash, second);
        return hash == UNKNOWN ? UNKNOWN - 1 : hash;
    }

    /** A stable component from text such as an asset digest or model revision. */
    public static long component(String text) {
        if (text == null) throw new IllegalArgumentException("Missing identity component");
        long hash = OFFSET;
        for (byte value : text.getBytes(StandardCharsets.UTF_8)) {
            hash = mix(hash, value & 0xff);
        }
        return hash == UNKNOWN ? UNKNOWN - 1 : hash;
    }

    private static long mix(long hash, long value) {
        hash ^= value;
        return hash * PRIME;
    }

    private PolicyIdentity() { }
}
