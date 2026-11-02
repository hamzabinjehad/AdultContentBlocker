package app.hisn.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Canonical SHA-256 of a policy's domain list, matching the Python signer. */
public final class DomainListHash {
    private DomainListHash() { }

    public static String sha256(Iterable<String> domains) {
        List<String> sorted = new ArrayList<>();
        for (String domain : domains) sorted.add(domain);
        Collections.sort(sorted);
        String canonical = String.join("\n", sorted);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }
}
