package app.hisn.core;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class DomainSeedTest {
    private static String digest(byte[] data) throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) hex.append(String.format("%02x", b & 255));
        return hex.toString();
    }

    private static void rejected(String text, String hash, int count) throws Exception {
        try {
            DomainSeed.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), hash, count);
            throw new AssertionError("Invalid seed accepted");
        } catch (IOException expected) { }
    }

    public static void main(String[] args) throws Exception {
        String text = "# seed\nblocked.example\nbücher.example\n192.0.2.1\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        DomainSeed seed = DomainSeed.load(new ByteArrayInputStream(bytes), digest(bytes), 3);
        if (seed.domainCount != 2 || seed.skippedAddresses != 1 || !seed.policy.blocks("sub.blocked.example")) {
            throw new AssertionError("Seed policy mismatch");
        }
        rejected(text + "other.example\n", digest(bytes), 3);
        rejected(text, digest(bytes), 2);
        String duplicate = "blocked.example\nblocked.example\n";
        rejected(duplicate, digest(duplicate.getBytes(StandardCharsets.UTF_8)), 2);
        String invalid = "https://example.com\n";
        rejected(invalid, digest(invalid.getBytes(StandardCharsets.UTF_8)), 1);
        if (args.length == 3) {
            try (FileInputStream input = new FileInputStream(args[0])) {
                DomainSeed actual = DomainSeed.load(input, args[1], Integer.parseInt(args[2]));
                System.out.println("Actual seed loaded: " + actual.domainCount + " domains, " + actual.skippedAddresses + " IP entries excluded");
            }
        }
        System.out.println("DomainSeed tests passed");
    }
}
