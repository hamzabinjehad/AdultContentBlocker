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
        // Fingerprint: a small change between the cadence grid's sample points must miss the cache.
        int width = 224, height = 160;
        int[] screen = new int[width * height];
        java.util.Random random = new java.util.Random(11);
        for (int i = 0; i < screen.length; i++) screen[i] = random.nextInt() | 0xFF000000;
        int[] dithered = screen.clone(), thumbnail = screen.clone(), grid = new int[256], gridAfter = new int[256];
        for (int i = 0; i < dithered.length; i++) dithered[i] = (screen[i] & ~0x0F0F0F) | (random.nextInt() & 0x0F0F0F);
        // A 6 x 6 patch off the 14 px grid (x 3..8, y 3..8), like a small image loading in.
        for (int y = 3; y < 9; y++) for (int x = 3; x < 9; x++) thumbnail[y * width + x] = 0xFFFFC0A0 ^ screen[y * width + x];
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            grid[y * 16 + x] = screen[(y * height / 16) * width + x * width / 16];
            gridAfter[y * 16 + x] = thumbnail[(y * height / 16) * width + x * width / 16];
        }
        check(VisualCadence.signature(grid) == VisualCadence.signature(gridAfter),
                "fixture: the coarse grid cannot see the patch");
        check(VisualVerdictCache.fingerprint(screen, width) == VisualVerdictCache.fingerprint(screen.clone(), width),
                "the same frame has the same fingerprint");
        check(VisualVerdictCache.fingerprint(screen, width) == VisualVerdictCache.fingerprint(dithered, width),
                "dithering noise is ignored");
        check(VisualVerdictCache.fingerprint(screen, width) != VisualVerdictCache.fingerprint(thumbnail, width),
                "a small image between grid points changes the fingerprint");
        check(VisualVerdictCache.fingerprint(screen, width) != VisualVerdictCache.fingerprint(screen, height),
                "a different shape changes the fingerprint");
        System.out.println("VisualVerdictCache tests passed (signature/window/TTL/unknown/bound/retain/clear/fingerprint)");
    }
}
