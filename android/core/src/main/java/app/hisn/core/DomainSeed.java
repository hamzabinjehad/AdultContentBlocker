package app.hisn.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Loads an APK seed against the digest pinned at the signature-verified build step. */
public final class DomainSeed {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    public final DomainPolicy policy;
    public final int domainCount;
    public final int skippedAddresses;

    private DomainSeed(DomainPolicy policy, int count, int skipped) {
        this.policy = policy;
        this.domainCount = count;
        this.skippedAddresses = skipped;
    }

    public static DomainSeed load(InputStream input, String digest, int expectedEntries) throws IOException {
        if (input == null || digest == null || !digest.matches("[0-9a-f]{64}")
                || expectedEntries < 1 || expectedEntries > 250000) throw new IOException("Invalid seed metadata");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > MAX_BYTES) throw new IOException("Seed too large");
            bytes.write(buffer, 0, count);
        }
        byte[] content = bytes.toByteArray();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder hex = new StringBuilder(64);
            for (byte value : hash) {
                hex.append(Character.forDigit((value >>> 4) & 15, 16));
                hex.append(Character.forDigit(value & 15, 16));
            }
            if (!digest.equals(hex.toString())) throw new IOException("Seed digest mismatch");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IOException("SHA-256 unavailable", unavailable);
        }
        final String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException invalid) { throw new IOException("Invalid seed encoding", invalid); }
        Set<String> entries = new HashSet<>();
        List<String> names = new ArrayList<>();
        int skipped = 0;
        for (String line : text.split("\n")) {
            String name = line.trim();
            if (name.isEmpty() || name.startsWith("#")) continue;
            if (name.length() > 253 || !entries.add(name) || entries.size() > expectedEntries) {
                throw new IOException("Invalid seed entries");
            }
            // IP literals in the shared list cannot be enforced by domain-only DNS filtering.
            if (ipv4(name)) skipped++;
            else names.add(name);
        }
        if (entries.size() != expectedEntries || names.isEmpty()) throw new IOException("Seed count mismatch");
        try {
            return new DomainSeed(new DomainPolicy(names, Collections.emptyList()), names.size(), skipped);
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid seed domain", invalid); }
    }

    private static boolean ipv4(String name) {
        String[] parts = name.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return false;
            int value = 0;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < '0' || c > '9') return false;
                value = value * 10 + c - '0';
            }
            if (value > 255) return false;
        }
        return true;
    }
}
