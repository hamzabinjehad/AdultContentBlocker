package app.hisn.core;

import java.util.Arrays;

public final class VisualVerdictCacheTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        VisualVerdictCache cache = new VisualVerdictCache();
        long now = 10_000;
        cache.put(7, 42, "allow", now);
        check("allow".equals(cache.get(7, 42, now + 1)), "same frame is reused");
        check(cache.get(7, 43, now + 1) == null, "a changed signature is not reused");
        check(cache.get(8, 42, now + 1) == null, "another window is not reused");
        check("allow".equals(cache.get(7, 42, now + VisualVerdictCache.TTL_MILLIS)),
                "the edge of the TTL is still valid");
        check(cache.get(7, 42, now + VisualVerdictCache.TTL_MILLIS + 1) == null,
                "an expired entry is not reused");
        cache.put(7, 42, "unknown", now + 20_000);
        check(cache.get(7, 42, now + 20_000) == null, "unknown is never cached");
        cache.put(7, 42, "block", now + 30_000);
        check("block".equals(cache.get(7, 42, now + 30_000)), "a block is reusable for the same frame");
        cache.put(7, 43, "allow", now + 40_000);
        check("allow".equals(cache.get(7, 43, now + 40_000)), "a new signature replaces the old one");

        VisualVerdictCache bounded = new VisualVerdictCache();
        for (int window = 0; window < VisualVerdictCache.MAX_WINDOWS + 3; window++) {
            bounded.put(window, window, "allow", now);
        }
        check(bounded.size() == VisualVerdictCache.MAX_WINDOWS, "the cache is bounded");
        check(bounded.get(0, 0, now) == null, "the oldest window was evicted");
        check(bounded.get(VisualVerdictCache.MAX_WINDOWS + 2, VisualVerdictCache.MAX_WINDOWS + 2, now) != null,
                "the newest window is retained");
        bounded.retain(Arrays.asList(VisualVerdictCache.MAX_WINDOWS + 2));
        check(bounded.size() == 1, "retain keeps only live windows");
        bounded.clear();
        check(bounded.size() == 0 && bounded.get(VisualVerdictCache.MAX_WINDOWS + 2,
                VisualVerdictCache.MAX_WINDOWS + 2, now) == null, "clear forgets every window");
        System.out.println("VisualVerdictCache tests passed (signature/window/TTL/unknown/bound/retain/clear)");
    }
}
