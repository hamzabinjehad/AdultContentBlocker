package app.hisn.android;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** One face verdict for an identical RGB input. Owned by a single runtime and people mode. */
final class FaceResultCache {
    static final long TTL_MILLIS = 5000;
    private byte[] key;
    private VisualResult result;
    private long at;

    static byte[] fingerprint(int[] pixels, int width, int height) {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
        byte[] chunk = new byte[3072];
        try {
            for (int shift = 24; shift >= 0; shift -= 8) digest.update((byte) (width >> shift));
            for (int shift = 24; shift >= 0; shift -= 8) digest.update((byte) (height >> shift));
            int used = 0;
            for (int pixel : pixels) {
                chunk[used++] = (byte) (pixel >> 16);
                chunk[used++] = (byte) (pixel >> 8);
                chunk[used++] = (byte) pixel;
                if (used == chunk.length) {
                    digest.update(chunk);
                    used = 0;
                }
            }
            digest.update(chunk, 0, used);
            return digest.digest();
        } finally {
            Arrays.fill(chunk, (byte) 0);
        }
    }

    VisualResult get(byte[] fingerprint, long now) {
        if (result == null) return null;
        if (now < at || now - at > TTL_MILLIS) {
            clear();
            return null;
        }
        return Arrays.equals(key, fingerprint) ? result : null;
    }

    void put(byte[] fingerprint, VisualResult value, long capturedAt) {
        // Unknown and adult-block results cannot settle a face check.
        if (!"allow".equals(value.verdict) || capturedAt < 0) return;
        key = fingerprint.clone();
        result = value;
        at = capturedAt;
    }

    void clear() {
        key = null;
        result = null;
    }
}
