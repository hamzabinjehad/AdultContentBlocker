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

    /** The packed table behaves as a Set: membership, misses, size and iteration. */
    private static void checkCompactSet() {
        java.util.Random random = new java.util.Random(3);
        java.util.Set<String> expected = new java.util.HashSet<>();
        CompactHostSet.Builder builder = new CompactHostSet.Builder();
        while (expected.size() < 50000) {
            String name = "h" + Integer.toString(random.nextInt(1 << 30), 36) + "." + (random.nextBoolean() ? "example" : "test");
            if (expected.add(name)) builder.add(name);
        }
        CompactHostSet set = builder.build();
        if (set == null || set.size() != expected.size() || !new java.util.HashSet<>(set).equals(expected))
            throw new AssertionError("Packed table differs from the names added");
        for (String name : expected) if (!set.contains(name)) throw new AssertionError("Packed member missing");
        for (int i = 0; i < 50000; i++) {
            String miss = "m" + i + ".example";
            if (set.contains(miss) != expected.contains(miss)) throw new AssertionError("Packed table false hit");
        }
        if (set.contains(null) || set.contains(42) || set.contains("caf\u00e9.example"))
            throw new AssertionError("Packed table accepted a non-name");
        CompactHostSet.Builder twice = new CompactHostSet.Builder();
        twice.add("a.example");
        twice.add("a.example");
        if (twice.build() != null) throw new AssertionError("Packed table accepted a duplicate");
    }

    public static void main(String[] args) throws Exception {
        String text = "# seed\nblocked.example\nbücher.example\n192.0.2.1\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        DomainSeed seed = DomainSeed.load(new ByteArrayInputStream(bytes), digest(bytes), 3);
        if (seed.domainCount != 2 || seed.skippedAddresses != 1 || !seed.policy.blocks("sub.blocked.example")) {
            throw new AssertionError("Seed policy mismatch");
        }
        // The loader normalizes once, so the IDN entry is enforced under its punycode spelling.
        if (!seed.policy.blocks("xn--bcher-kva.example") || !seed.policy.blocks("BÜCHER.example")) {
            throw new AssertionError("Normalized seed entry missing");
        }
        rejected(text + "other.example\n", digest(bytes), 3);
        rejected(text, digest(bytes), 2);
        String duplicate = "blocked.example\nblocked.example\n";
        rejected(duplicate, digest(duplicate.getBytes(StandardCharsets.UTF_8)), 2);
        String invalid = "https://example.com\n";
        rejected(invalid, digest(invalid.getBytes(StandardCharsets.UTF_8)), 1);
        // Two spellings of one name are one duplicate in the packed table too.
        String folded = "Blocked.Example\nblocked.example.\n";
        rejected(folded, digest(folded.getBytes(StandardCharsets.UTF_8)), 2);
        checkCompactSet();
        if (args.length == 3) {
            try (FileInputStream input = new FileInputStream(args[0])) {
                DomainSeed actual = DomainSeed.load(input, args[1], Integer.parseInt(args[2]));
                // Every listed line answers from the packed table, and its parents do not.
                java.util.Set<String> rules = actual.policy.blockedRules();
                int lines = 0;
                for (String line : java.nio.file.Files.readAllLines(java.nio.file.Paths.get(args[0]),
                        StandardCharsets.UTF_8)) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") || line.matches("[0-9.]+")) continue;
                    String name = Hostname.normalize(line);
                    if (!rules.contains(name) || !actual.policy.blocks("www." + name))
                        throw new AssertionError("Seed name missing from the packed table: " + name);
                    lines++;
                }
                if (lines != rules.size()) throw new AssertionError("Packed table size mismatch");
                System.out.println("Actual seed loaded: " + actual.domainCount + " domains, " + actual.skippedAddresses + " IP entries excluded");
            }
        }
        System.out.println("DomainSeed tests passed");
    }
}
