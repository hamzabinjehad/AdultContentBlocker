package app.hisn.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Saved settings live in a main file and a second copy. Each holds every value plus a seal: a SHA-256
 * over all the other values, written in the same commit. Android reads a corrupt or unreadable
 * preferences file as empty, the same as a new installation, so reading alone cannot tell them apart;
 * the seal, the copy and whether a file exists on disk can. Clearing app data removes both files,
 * which is a new installation again.
 */
public final class SettingsIntegrity {
    public static final String SEAL = "seal";
    private static final String VERSION = "1:";

    public enum Source {
        /** Nothing was ever saved: a new installation, or app data cleared in Android settings. */
        FRESH,
        /** The main file is intact. */
        MAIN,
        /** Saved before seals existed (Hisn 0.5.3 and earlier), with no copy yet: read as before. */
        UNSEALED,
        /** The main file is damaged or unreadable; its intact copy replaces it. */
        COPY,
        /** Settings were saved, but neither file can be trusted: the commitment cannot be read. */
        UNKNOWN
    }

    private SettingsIntegrity() { }

    /** The seal for these values; any {@link #SEAL} entry among them is ignored. */
    public static String seal(Map<String, ?> values) {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        for (String key : new TreeSet<>(values.keySet())) {
            if (SEAL.equals(key)) continue;
            Object value = values.get(key);
            put(digest, key);
            if (value instanceof String) { put(digest, "s"); put(digest, (String) value); }
            else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
                put(digest, value.getClass().getSimpleName()); put(digest, value.toString());
            } else if (value instanceof Set) {
                Set<String> items = new TreeSet<>();
                for (Object item : (Set<?>) value) {
                    if (!(item instanceof String)) throw new IllegalArgumentException("Unsealable set item");
                    items.add((String) item);
                }
                put(digest, "set"); put(digest, Integer.toString(items.size()));
                for (String item : items) put(digest, item);
            } else throw new IllegalArgumentException("Unsealable value");
        }
        StringBuilder hex = new StringBuilder(VERSION);
        for (byte b : digest.digest()) hex.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return hex.toString();
    }

    /** True when the values carry a seal that matches them. */
    public static boolean intact(Map<String, ?> values) {
        Object seal = values.get(SEAL);
        if (!(seal instanceof String)) return false;
        try { return seal.equals(seal(values)); }
        catch (RuntimeException unsealable) { return false; }
    }

    /**
     * Which saved values to trust. {@code onDisk} says a file exists even if it read as empty, and
     * {@code readable} says sealed values hold valid settings. Values saved before seals are used as
     * before only while no copy exists, because every sealed save also writes the copy; if they do not
     * hold valid settings the caller refuses to load them, as before, rather than guessing.
     */
    public static Source choose(Map<String, ?> main, boolean mainOnDisk, Map<String, ?> copy, boolean copyOnDisk,
            Predicate<Map<String, ?>> readable) {
        if (intact(main) && readable.test(main)) return Source.MAIN;
        if (intact(copy) && readable.test(copy)) return Source.COPY;
        boolean copySaved = copyOnDisk || !copy.isEmpty();
        if (!copySaved && !main.isEmpty() && !main.containsKey(SEAL)) return Source.UNSEALED;
        if (!copySaved && !mainOnDisk && main.isEmpty()) return Source.FRESH;
        return Source.UNKNOWN;
    }

    // Length-prefixed, so no two different value lists produce the same bytes.
    private static void put(MessageDigest digest, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        digest.update(new byte[]{(byte) (bytes.length >>> 24), (byte) (bytes.length >>> 16), (byte) (bytes.length >>> 8), (byte) bytes.length});
        digest.update(bytes);
    }
}
