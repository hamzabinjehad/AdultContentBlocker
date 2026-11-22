package app.hisn.android;

import java.util.Arrays;

public final class FaceResultCacheTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int[] pixels = {0xFF123456, 0xFFABCDEF, 0xFF654321, 0xFF000000};
        byte[] key = FaceResultCache.fingerprint(pixels, 2, 2);
        check(Arrays.equals(key, FaceResultCache.fingerprint(pixels.clone(), 2, 2)), "identical RGB input");
        int[] alpha = pixels.clone();
        alpha[0] ^= 0xFF000000;
        check(Arrays.equals(key, FaceResultCache.fingerprint(alpha, 2, 2)), "model ignores alpha");
        int[] subtle = pixels.clone();
        subtle[3] ^= 1;
        byte[] changed = FaceResultCache.fingerprint(subtle, 2, 2);
        check(!Arrays.equals(key, changed), "one low RGB bit requires a fresh face check");
        check(!Arrays.equals(key, FaceResultCache.fingerprint(pixels, 1, 4)), "shape participates in key");
        int[] longFrame = new int[2049];
        byte[] beforeTail = FaceResultCache.fingerprint(longFrame, 3, 683);
        longFrame[2048] = 1;
        check(!Arrays.equals(beforeTail, FaceResultCache.fingerprint(longFrame, 3, 683)),
                "pixels after full hashing chunks still participate");

        FaceResultCache cache = new FaceResultCache();
        VisualResult covered = new VisualResult("allow", true);
        cache.put(key, covered, 1000);
        check(cache.get(key, 1001) == covered, "matching input reuses the people verdict");
        check(cache.get(changed, 1001) == null, "changed input misses");
        key[0] ^= 1;
        check(cache.get(FaceResultCache.fingerprint(pixels, 2, 2), 1002) == covered, "cache owns its key");
        key = FaceResultCache.fingerprint(pixels, 2, 2);
        check(cache.get(key, 1000 + FaceResultCache.TTL_MILLIS) == covered, "TTL boundary");
        check(cache.get(key, 1001 + FaceResultCache.TTL_MILLIS) == null, "reuse cannot extend TTL");
        cache.put(key, new VisualResult("unknown", false), 10000);
        check(cache.get(key, 10001) == null, "unknown is never reusable");
        cache.put(key, new VisualResult("block", false), 10000);
        check(cache.get(key, 10001) == null, "adult block never substitutes for a face check");
        cache.put(key, covered, 10000);
        check(cache.get(key, 9999) == null, "clock reversal expires the result");
        cache.put(key, covered, 10000);
        cache.clear();
        check(cache.get(key, 10001) == null, "closing or replacing the runtime clears reuse");
        FaceResultCache anotherMode = new FaceResultCache();
        check(anotherMode.get(key, 10001) == null, "a new people mode starts empty");
        VisualResult clear = new VisualResult("allow", false);
        cache.put(changed, clear, 10000);
        check(cache.get(changed, 10001) == clear && cache.get(key, 10001) == null,
                "a changed clean frame replaces the previous covered frame");
        System.out.println("FaceResultCache tests passed (RGB, shape, expiry, unknown, isolation)");
    }
}
