package app.hisn.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Ed25519Test {
    private static final String PINNED =
            "eb6751a0429413d0cfb24a778f7d6ecdd8436e573af94c325de5fcbd38e59ede";

    private static byte[] hex(String text) {
        byte[] out = new byte[text.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        // RFC 8032 section 7.1, test 1.
        byte[] pk1 = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        byte[] sig1 = hex("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155"
                + "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b");
        check(Ed25519.verify(pk1, new byte[0], sig1), "RFC 8032 vector 1");
        check(!Ed25519.verify(pk1, "x".getBytes(StandardCharsets.UTF_8), sig1), "changed message");

        // RFC 8032 section 7.1, test 2.
        byte[] pk2 = hex("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c");
        byte[] msg2 = hex("72");
        byte[] sig2 = hex("92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da"
                + "085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00");
        check(Ed25519.verify(pk2, msg2, sig2), "RFC 8032 vector 2");

        // RFC 8032 section 7.1, test 3.
        byte[] pk3 = hex("fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025");
        byte[] msg3 = hex("af82");
        byte[] sig3 = hex("6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3ac"
                + "18ff9b538d16f290ae67f760984dc6594a7c15e9716ed28dc027beceea1ec40a");
        check(Ed25519.verify(pk3, msg3, sig3), "RFC 8032 vector 3");

        // Tampering, wrong key, malformed values and non-canonical S are all false.
        byte[] tampered = sig2.clone(); tampered[0] ^= 1;
        check(!Ed25519.verify(pk2, msg2, tampered), "tampered signature");
        check(!Ed25519.verify(pk3, msg2, sig2), "wrong key");
        check(!Ed25519.verify(pk2, msg2, java.util.Arrays.copyOf(sig2, 63)), "short signature");
        check(!Ed25519.verify(java.util.Arrays.copyOf(pk2, 31), msg2, sig2), "short key");
        check(!Ed25519.verify(null, msg2, sig2), "null key");
        check(!Ed25519.verify(pk2, null, sig2), "null message");
        check(!Ed25519.verify(pk2, msg2, null), "null signature");
        byte[] badS = sig2.clone();
        java.util.Arrays.fill(badS, 32, 64, (byte) 0xff);
        check(!Ed25519.verify(pk2, msg2, badS), "S above L");
        byte[] badKey = pk2.clone(); java.util.Arrays.fill(badKey, (byte) 0xff);
        check(!Ed25519.verify(badKey, msg2, sig2), "non-canonical key");

        // The repository's own signed manifest, exactly as an update would receive it.
        Path manifest = Path.of("..", "seed", "manifest.json");
        Path signature = Path.of("..", "seed", "manifest.json.sig");
        if (Files.exists(manifest) && Files.exists(signature)) {
            byte[] bytes = Files.readAllBytes(manifest);
            byte[] sig = hex(Files.readString(signature, StandardCharsets.UTF_8).trim());
            check(Ed25519.verify(hex(PINNED), bytes, sig), "seed manifest signature");
            check(!Ed25519.verify(hex(PINNED), bytes, tampered), "manifest with foreign signature");
            System.out.println("Ed25519 tests passed (RFC 8032 vectors, tampering and the signed seed manifest)");
        } else {
            System.out.println("Ed25519 tests passed (RFC 8032 vectors and tampering; seed manifest absent)");
        }
    }
}
