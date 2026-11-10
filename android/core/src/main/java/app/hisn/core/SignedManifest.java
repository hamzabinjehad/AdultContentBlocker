package app.hisn.core;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The published list manifest, verified against the pinned Ed25519 key before any
 * field is trusted. The signature covers the exact manifest bytes; artifact hashes
 * are then checked against the verified values. Shape and coverage limits are
 * deliberate: a validly signed generation that collapsed to a handful of domains
 * is a broken build, and keeping yesterday's generation is safer than installing it.
 */
public final class SignedManifest {
    public static final String DOMAINS = "domains_core.txt";
    public static final String TERMS = "terms.json";
    public static final int MAX_MANIFEST_BYTES = 64 * 1024;
    public static final int MIN_DOMAINS = 1_000;
    public static final int MAX_DOMAINS = 250_000;
    public static final int MAX_ARTIFACT_BYTES = 8 * 1024 * 1024;

    public static final class Invalid extends IllegalArgumentException {
        public final String reason;
        public Invalid(String reason) { super(reason); this.reason = reason; }
    }

    public static final class Artifact {
        public final String name;
        public final String sha256;
        public final long bytes;

        Artifact(String name, String sha256, long bytes) {
            this.name = name;
            this.sha256 = sha256;
            this.bytes = bytes;
        }
    }

    public final int schema;
    public final int version;
    public final int coreDomainCount;
    public final int domainCount;
    public final Map<String, Artifact> artifacts;
    private final byte[] exactBytes;

    private SignedManifest(int schema, int version, int coreDomainCount, int domainCount,
            Map<String, Artifact> artifacts, byte[] exactBytes) {
        this.schema = schema;
        this.version = version;
        this.coreDomainCount = coreDomainCount;
        this.domainCount = domainCount;
        this.artifacts = Collections.unmodifiableMap(artifacts);
        this.exactBytes = exactBytes.clone();
    }

    /** Verify the pinned signature and then the schema/coverage shape. */
    public static SignedManifest verify(byte[] manifestBytes, String signatureHex, byte[] publicKey) {
        if (manifestBytes == null || manifestBytes.length == 0
                || manifestBytes.length > MAX_MANIFEST_BYTES) throw new Invalid("manifest-size");
        if (signatureHex == null) throw new Invalid("signature");
        String hex = signatureHex.trim();
        if (!hex.matches("[0-9a-fA-F]{128}")) throw new Invalid("signature");
        byte[] signature = new byte[64];
        for (int i = 0; i < 64; i++) {
            signature[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        if (!Ed25519.verify(publicKey, manifestBytes, signature)) throw new Invalid("signature");
        return parse(manifestBytes);
    }

    static SignedManifest parse(byte[] manifestBytes) {
        final Map<String, Object> root;
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(manifestBytes)).toString();
            root = Json.object(text);
        } catch (CharacterCodingException invalid) { throw new Invalid("manifest-encoding"); }
        catch (Json.Invalid invalid) { throw new Invalid("manifest-json"); }
        int schema = integer(root, "schema", 1, 1);
        int version = integer(root, "version", 1, 1_000_000);
        int core = integer(root, "core_domain_count", MIN_DOMAINS, MAX_DOMAINS);
        int all = integer(root, "domain_count", core, Integer.MAX_VALUE);
        Object artifactValue = root.get("artifacts");
        if (!(artifactValue instanceof Map)) throw new Invalid("artifacts");
        Map<?, ?> raw = (Map<?, ?>) artifactValue;
        if (raw.isEmpty() || raw.size() > 32) throw new Invalid("artifacts");
        Map<String, Artifact> artifacts = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String)) throw new Invalid("artifact-name");
            String name = (String) entry.getKey();
            if (!name.matches("[A-Za-z0-9._-]{1,64}")) throw new Invalid("artifact-name");
            if (!(entry.getValue() instanceof Map)) throw new Invalid("artifact");
            Map<?, ?> meta = (Map<?, ?>) entry.getValue();
            // Only the artifacts this client can use are allowed; unknown files are not fetched.
            if (!DOMAINS.equals(name) && !TERMS.equals(name)) continue;
            Object sha = meta.get("sha256");
            Object bytes = meta.get("bytes");
            if (!(sha instanceof String) || !((String) sha).matches("[0-9a-f]{64}")) {
                throw new Invalid("artifact-sha256");
            }
            if (!(bytes instanceof Long)) throw new Invalid("artifact-bytes");
            long size = (Long) bytes;
            if (size < 1 || size > MAX_ARTIFACT_BYTES) throw new Invalid("artifact-bytes");
            artifacts.put(name, new Artifact(name, (String) sha, size));
        }
        if (!artifacts.containsKey(DOMAINS)) throw new Invalid("missing-domains");
        // A generation is useful only when the core tier has plausible coverage.
        if (core < MIN_DOMAINS) throw new Invalid("coverage");
        return new SignedManifest(schema, version, core, all, artifacts, manifestBytes);
    }

    public Artifact domains() { return artifacts.get(DOMAINS); }
    public Artifact terms() { return artifacts.get(TERMS); }
    public byte[] exactBytes() { return exactBytes.clone(); }

    private static int integer(Map<String, Object> root, String key, int min, int max) {
        Object value = root.get(key);
        if (!(value instanceof Long)) throw new Invalid(key);
        long number = (Long) value;
        if (number < min || number > max) throw new Invalid(key);
        return (int) number;
    }
}
