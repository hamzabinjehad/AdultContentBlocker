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
import java.util.HashSet;
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
        // Every published list so far is plain ASCII, which is valid UTF-8 as it stands: read
        // its lines straight from the bytes instead of first decoding a 4 MB String.
        boolean ascii = true;
        for (byte value : content) if (value < 0) { ascii = false; break; }
        final String text;
        if (ascii) text = null;
        else try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException invalid) { throw new IOException("Invalid seed encoding", invalid); }
        // One pass, normalizing each line as it is accepted, into one packed table rather than
        // a set of 236k Strings (CompactHostSet explains the memory).
        CompactHostSet.Builder entries = new CompactHostSet.Builder();
        // IP literals cannot be enforced by domain-only DNS filtering. They still count
        // toward the signed entry total, so they are tracked (five lines) and skipped.
        Set<String> addresses = new HashSet<>();
        int skipped = 0;
        int length = ascii ? content.length : text.length();
        int start = 0;
        while (start <= length) {
            int end = ascii ? indexOf(content, start) : text.indexOf('\n', start);
            if (end < 0) end = length;
            String name = (ascii ? new String(content, start, end - start, StandardCharsets.US_ASCII)
                    : text.substring(start, end)).trim();
            start = end + 1;
            if (name.isEmpty() || name.startsWith("#")) continue;
            if (name.length() > 253) throw new IOException("Invalid seed entries");
            if (ipv4(name)) {
                if (!addresses.add(name)) throw new IOException("Invalid seed entries");
                skipped++;
            } else {
                try {
                    entries.add(Hostname.normalize(name));
                } catch (IllegalArgumentException invalid) {
                    throw new IOException("Invalid seed domain", invalid);
                }
            }
            if (entries.count() + addresses.size() > expectedEntries)
                throw new IOException("Invalid seed entries");
        }
        if (entries.count() + addresses.size() != expectedEntries || entries.count() == 0)
            throw new IOException("Seed count mismatch");
        CompactHostSet domains = entries.build();
        if (domains == null) throw new IOException("Invalid seed entries"); // A repeated domain.
        return new DomainSeed(DomainPolicy.fromCanonical(domains), domains.size(), skipped);
    }

    private static int indexOf(byte[] content, int from) {
        for (int i = from; i < content.length; i++) if (content[i] == '\n') return i;
        return -1;
    }

    /** Four dotted decimal labels; no allocation or regex per line. */
    private static boolean ipv4(String name) {
        int labels = 0, digits = 0, value = 0;
        for (int i = 0; i <= name.length(); i++) {
            if (i == name.length() || name.charAt(i) == '.') {
                if (digits == 0 || digits > 3 || value > 255) return false;
                labels++;
                digits = 0;
                value = 0;
            } else {
                char c = name.charAt(i);
                if (c < '0' || c > '9') return false;
                value = value * 10 + (c - '0');
                digits++;
            }
        }
        return labels == 4;
    }
}
