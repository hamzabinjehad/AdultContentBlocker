package app.hisn.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SignedManifestTest {
    private static final String PINNED =
            "eb6751a0429413d0cfb24a778f7d6ecdd8436e573af94c325de5fcbd38e59ede";

    private static byte[] hex(String text) {
        byte[] out = new byte[text.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    private static void invalid(String json) {
        try { SignedManifest.parse(json.getBytes(StandardCharsets.UTF_8)); throw new AssertionError("accepted: " + json); }
        catch (SignedManifest.Invalid expected) { }
    }

    public static void main(String[] args) throws Exception {
        Path manifest = Path.of("..", "seed", "manifest.json");
        Path signature = Path.of("..", "seed", "manifest.json.sig");
        if (!Files.exists(manifest) || !Files.exists(signature)) throw new AssertionError("seed manifest absent");
        byte[] bytes = Files.readAllBytes(manifest);
        String sig = Files.readString(signature, StandardCharsets.UTF_8).trim();
        SignedManifest signed = SignedManifest.verify(bytes, sig, hex(PINNED));
        if (signed.version != 5 || signed.coreDomainCount < 200_000) throw new AssertionError("seed manifest fields");
        if (signed.domains().bytes != Files.size(Path.of("..", "seed", "domains_core.txt"))) {
            throw new AssertionError("domain artifact size");
        }
        byte[] tampered = bytes.clone();
        tampered[tampered.length / 2] ^= 1;
        try { SignedManifest.verify(tampered, sig, hex(PINNED)); throw new AssertionError("tampered manifest accepted"); }
        catch (SignedManifest.Invalid expected) { }
        try { SignedManifest.verify(bytes, sig, hex("00".repeat(32))); throw new AssertionError("wrong key accepted"); }
        catch (SignedManifest.Invalid expected) { }
        try { SignedManifest.verify(bytes, "xyz", hex(PINNED)); throw new AssertionError("bad signature hex accepted"); }
        catch (SignedManifest.Invalid expected) { }
        try { SignedManifest.verify(new byte[0], sig, hex(PINNED)); throw new AssertionError("empty manifest accepted"); }
        catch (SignedManifest.Invalid expected) { }

        String base = "\"schema\":1,\"version\":6,\"core_domain_count\":2000,\"domain_count\":3000,"
                + "\"artifacts\":{\"domains_core.txt\":{\"sha256\":\""
                + "a".repeat(64) + "\",\"bytes\":1234}}";
        SignedManifest parsed = SignedManifest.parse(("{" + base + "}").getBytes(StandardCharsets.UTF_8));
        if (parsed.version != 6 || parsed.domains().bytes != 1234) throw new AssertionError("parse fields");
        invalid("{");
        invalid("{\"schema\":2,\"version\":6,\"core_domain_count\":2000,\"domain_count\":3000,"
                + "\"artifacts\":{\"domains_core.txt\":{\"sha256\":\"" + "a".repeat(64) + "\",\"bytes\":1234}}}");
        invalid("{\"schema\":1,\"version\":0,\"core_domain_count\":2000,\"domain_count\":3000,"
                + "\"artifacts\":{\"domains_core.txt\":{\"sha256\":\"" + "a".repeat(64) + "\",\"bytes\":1234}}}");
        invalid("{\"schema\":1,\"version\":6,\"core_domain_count\":999,\"domain_count\":3000,"
                + "\"artifacts\":{\"domains_core.txt\":{\"sha256\":\"" + "a".repeat(64) + "\",\"bytes\":1234}}}");
        invalid("{\"schema\":1,\"version\":6,\"core_domain_count\":2000,\"domain_count\":1999,"
                + "\"artifacts\":{\"domains_core.txt\":{\"sha256\":\"" + "a".repeat(64) + "\",\"bytes\":1234}}}");
        invalid("{\"schema\":1,\"version\":6,\"core_domain_count\":2000,\"domain_count\":3000,"
                + "\"artifacts\":{\"terms.json\":{\"sha256\":\"" + "a".repeat(64) + "\",\"bytes\":1234}}}");
        invalid("{\"schema\":1,\"version\":6,\"core_domain_count\":2000,\"domain_count\":3000,"
                + "\"artifacts\":{\"domains_core.txt\":{\"sha256\":\"short\",\"bytes\":1234}}}");
        invalid("{\"schema\":1,\"version\":6,\"core_domain_count\":2000,\"domain_count\":3000,"
                + "\"artifacts\":{\"domains_core.txt\":{\"sha256\":\"" + "a".repeat(64) + "\",\"bytes\":9000000}}}");
        invalid("{\"schema\":1,\"schema\":1,\"version\":6,\"core_domain_count\":2000,\"domain_count\":3000,"
                + "\"artifacts\":{\"domains_core.txt\":{\"sha256\":\"" + "a".repeat(64) + "\",\"bytes\":1234}}}");
        invalid("{\"schema\":1,\"version\":6,\"core_domain_count\":2000,\"domain_count\":3000,"
                + "\"artifacts\":{\"domains_core.txt\":{\"sha256\":\"" + "a".repeat(64) + "\",\"bytes\":\"1234\"}}}");
        System.out.println("SignedManifest tests passed (pinned signature, tampering, schema/coverage/artifact bounds)");
    }
}
