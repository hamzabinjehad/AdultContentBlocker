package app.hisn.core;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Android verification of a version-2 signed policy.
 *
 * <p>The envelope is flat, so verification needs no nested JSON parser:
 *
 * <pre>
 * {"counter":1,"device":"...","issued_at":...,"kind":"policy",
 *  "not_after":...,"rules":"&lt;base64url of canonical rules JSON&gt;",
 *  "sig":"...","v":2}
 * </pre>
 *
 * The signature covers the canonical body without {@code sig}. The rules bytes
 * are authenticated but deliberately not interpreted here; a later policy
 * compiler can parse them and must ignore rules it cannot enforce rather than
 * silently weakening.
 */
public final class GuardianPolicy {
    private static final int VERSION = 2;
    private static final long MAX_SKEW_SECONDS = 300;

    private GuardianPolicy() { }

    public static final class Verified {
        public final String device;
        public final long counter;
        public final long issuedAt;
        public final long notAfter;
        public final byte[] rulesJson;

        Verified(String device, long counter, long issuedAt, long notAfter, byte[] rulesJson) {
            this.device = device;
            this.counter = counter;
            this.issuedAt = issuedAt;
            this.notAfter = notAfter;
            this.rulesJson = rulesJson.clone();
        }
    }

    public static Verified verify(PublicKey guardian, String envelope, String expectedDevice,
                                  long now) {
        return verify(guardian, envelope, expectedDevice, now, 0, MAX_SKEW_SECONDS);
    }

    public static Verified verify(PublicKey guardian, String envelope, String expectedDevice,
                                  long now, long lastCounter, long maxSkew) {
        Map<String, String> values = GuardianPairing.parseFlat(envelope);
        if (!values.keySet().equals(new LinkedHashSet<>(Arrays.asList(
                "counter", "device", "issued_at", "kind", "not_after", "rules", "sig", "v")))) {
            throw new GuardianPairing.PairingException("policy has missing or unknown fields");
        }
        if (!String.valueOf(VERSION).equals(values.get("v")) || !"policy".equals(values.get("kind"))) {
            throw new GuardianPairing.PairingException("wrong policy version or kind");
        }
        String device = values.get("device");
        if (device == null || !device.equals(expectedDevice)) {
            throw new GuardianPairing.PairingException("policy is for a different device");
        }
        long counter = number(values, "counter");
        long issuedAt = number(values, "issued_at");
        long notAfter = number(values, "not_after");
        if (counter <= lastCounter) {
            throw new GuardianPairing.PairingException("policy replay: counter is not newer");
        }
        if (notAfter <= issuedAt) {
            throw new GuardianPairing.PairingException("policy expires before it was issued");
        }
        if (issuedAt > now + maxSkew) {
            throw new GuardianPairing.PairingException("policy is issued in the future");
        }
        if (notAfter <= now) {
            throw new GuardianPairing.PairingException("policy has expired");
        }
        String rules = values.get("rules");
        if (rules == null || rules.isEmpty()) {
            throw new GuardianPairing.PairingException("policy rules are missing");
        }
        byte[] rulesBytes;
        try {
            rulesBytes = Base64.getUrlDecoder().decode(rules);
        } catch (IllegalArgumentException invalid) {
            throw new GuardianPairing.PairingException("policy rules are not base64url", invalid);
        }
        if (rulesBytes.length == 0 || firstNonSpace(rulesBytes) != '{'
                || lastNonSpace(rulesBytes) != '}') {
            throw new GuardianPairing.PairingException("policy rules are not a JSON object");
        }
        String rulesJson = strictUtf8(rulesBytes);
        String body = "{\"counter\":" + counter + ",\"device\":\"" + device
                + "\",\"issued_at\":" + issuedAt + ",\"kind\":\"policy\",\"not_after\":"
                + notAfter + ",\"rules\":\"" + rules + "\",\"v\":2}";
        byte[] signature;
        try {
            signature = Base64.getUrlDecoder().decode(values.get("sig"));
        } catch (IllegalArgumentException invalid) {
            throw new GuardianPairing.PairingException("policy signature is not base64url", invalid);
        }
        try {
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(guardian);
            verifier.update(body.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(signature)) {
                throw new GuardianPairing.PairingException("policy signature is invalid");
            }
        } catch (GuardianPairing.PairingException failure) {
            throw failure;
        } catch (Exception unavailable) {
            throw new GuardianPairing.PairingException("cannot verify the policy", unavailable);
        }
        return new Verified(device, counter, issuedAt, notAfter, rulesBytes);
    }

    private static long number(Map<String, String> values, String name) {
        String text = values.get(name);
        try {
            long parsed = Long.parseLong(text);
            if (!Long.toString(parsed).equals(text)) {
                throw new GuardianPairing.PairingException(name + " is not a canonical integer");
            }
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new GuardianPairing.PairingException(name + " is not an integer", invalid);
        }
    }

    private static String strictUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException invalid) {
            throw new GuardianPairing.PairingException("policy rules are not UTF-8", invalid);
        }
    }

    private static char firstNonSpace(byte[] bytes) {
        for (byte value : bytes) {
            char c = (char) (value & 0xff);
            if (!Character.isWhitespace(c)) return c;
        }
        return '\0';
    }

    private static char lastNonSpace(byte[] bytes) {
        for (int i = bytes.length - 1; i >= 0; i--) {
            char c = (char) (bytes[i] & 0xff);
            if (!Character.isWhitespace(c)) return c;
        }
        return '\0';
    }
}
