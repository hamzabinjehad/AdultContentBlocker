package app.hisn.core;

import java.math.BigInteger;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Phone-side reference for Hisn's pairing protocol.
 *
 * <p>This is the P-256 part the Android client needs: parse a
 * {@code hisn://pair/1} offer, build or verify the device response, and derive
 * the six-digit SAS. It deliberately does not store keys, show UI or scan QR
 * codes; the app shell must keep the private key in the Android Keystore and
 * pin the guardian public key after the person confirms the SAS.
 *
 * <p>Android Keystore returns the private and public key separately, so the
 * response and SAS methods take both. The wire format and canonical JSON are
 * documented in {@code docs/PAIRING_PROTOCOL.md} and implemented in Python at
 * {@code script/guardian_protocol.py}. The test fixture comes from that
 * implementation, so both sides are checked against the same bytes.
 */
public final class GuardianPairing {
    private static final String KIND = "pair-response";
    private static final int NONCE_BYTES = 16;
    private static final int MAX_LABEL = 64;
    private static final String CURVE = "secp256r1";

    private GuardianPairing() { }

    public static final class Offer {
        public final PublicKey guardian;
        public final String guardianB64;
        public final byte[] nonce;
        public final String nonceB64;
        public final String label;
        public final String uri;

        Offer(PublicKey guardian, String guardianB64, byte[] nonce, String nonceB64,
              String label, String uri) {
            this.guardian = guardian;
            this.guardianB64 = guardianB64;
            this.nonce = nonce.clone();
            this.nonceB64 = nonceB64;
            this.label = label;
            this.uri = uri;
        }
    }

    public static final class Result {
        public final PublicKey device;
        public final String deviceB64;
        public final String sas;

        Result(PublicKey device, String deviceB64, String sas) {
            this.device = device;
            this.deviceB64 = deviceB64;
            this.sas = sas;
        }
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec(CURVE), new SecureRandom());
            return generator.generateKeyPair();
        } catch (Exception unavailable) {
            throw new IllegalStateException("P-256 is unavailable", unavailable);
        }
    }

    public static String publicB64(PublicKey key) {
        requireP256(key);
        return encode(key.getEncoded());
    }

    public static PublicKey loadPublicB64(String text) {
        try {
            PublicKey key = KeyFactory.getInstance("EC")
                    .generatePublic(new X509EncodedKeySpec(decode(text)));
            requireP256(key);
            return key;
        } catch (PairingException failure) {
            throw failure;
        } catch (Exception unavailable) {
            throw new PairingException("cannot read public key", unavailable);
        }
    }

    public static PrivateKey loadPrivateB64(String text) {
        try {
            PrivateKey key = KeyFactory.getInstance("EC")
                    .generatePrivate(new PKCS8EncodedKeySpec(decodePrivate(text)));
            requirePrivate(key);
            return key;
        } catch (PairingException failure) {
            throw failure;
        } catch (Exception unavailable) {
            throw new PairingException("cannot read private key", unavailable);
        }
    }

    public static String deviceId(PublicKey key) {
        try {
            return encode(MessageDigest.getInstance("SHA-256").digest(key.getEncoded()));
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    public static Offer parseOffer(String uri) {
        if (uri == null) throw new PairingException("missing offer");
        final URI parsed;
        try {
            parsed = URI.create(uri);
        } catch (IllegalArgumentException invalid) {
            throw new PairingException("malformed offer URI", invalid);
        }
        if (!"hisn".equals(parsed.getScheme()) || !"pair".equals(parsed.getHost())
                || !"/1".equals(parsed.getPath()) || parsed.getRawFragment() != null
                || parsed.getRawUserInfo() != null || parsed.getPort() != -1) {
            throw new PairingException("not a Hisn pairing offer");
        }
        String query = parsed.getRawQuery();
        if (query == null || query.isEmpty()) throw new PairingException("offer has no query");
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String pair : query.split("&", -1)) {
                int equals = pair.indexOf('=');
                if (equals <= 0) throw new PairingException("malformed offer parameter");
                String key = URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8.name());
                String value = URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8.name());
                if (values.put(key, value) != null) {
                    throw new PairingException("repeated offer parameter");
                }
            }
        } catch (IllegalArgumentException invalid) {
            throw new PairingException("malformed offer parameter", invalid);
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new PairingException("UTF-8 is unavailable", impossible);
        }
        for (String key : values.keySet()) {
            if (!"gk".equals(key) && !"nonce".equals(key) && !"label".equals(key)) {
                throw new PairingException("unknown offer parameter");
            }
        }
        String gk = values.get("gk");
        String nonceText = values.get("nonce");
        if (gk == null || nonceText == null) throw new PairingException("offer is missing a key or nonce");
        PublicKey guardian = loadPublicB64(gk);
        byte[] nonce = decode(nonceText);
        if (nonce.length != NONCE_BYTES) throw new PairingException("offer nonce is the wrong length");
        String label = values.containsKey("label") ? values.get("label") : "";
        if (label.codePointCount(0, label.length()) > MAX_LABEL || hasControl(label)) {
            throw new PairingException("offer label is invalid");
        }
        return new Offer(guardian, publicB64(guardian), nonce, nonceText, label, uri);
    }

    /** Build the response with the public key Android Keystore keeps alongside the private key. */
    public static String buildResponse(PrivateKey device, PublicKey devicePublic, Offer offer) {
        requirePrivate(device);
        requireP256(devicePublic);
        String deviceB64 = publicB64(devicePublic);
        String body = responseBody(deviceB64, offer.guardianB64, offer.nonceB64);
        String proof = encode(sign(device, body));
        return "{\"device\":\"" + deviceB64 + "\",\"guardian\":\"" + offer.guardianB64
                + "\",\"kind\":\"" + KIND + "\",\"nonce\":\"" + offer.nonceB64
                + "\",\"proof\":\"" + proof + "\",\"v\":1}";
    }

    public static Result verifyResponse(Offer offer, String response, PrivateKey guardian) {
        Map<String, String> values = parseFlat(response);
        if (!values.keySet().equals(new java.util.LinkedHashSet<>(Arrays.asList(
                "device", "guardian", "kind", "nonce", "proof", "v")))) {
            throw new PairingException("response has missing or unknown fields");
        }
        if (!"1".equals(values.get("v")) || !KIND.equals(values.get("kind"))) {
            throw new PairingException("wrong response version or kind");
        }
        if (!offer.nonceB64.equals(values.get("nonce"))) {
            throw new PairingException("response nonce does not match the offer");
        }
        if (!offer.guardianB64.equals(values.get("guardian"))) {
            throw new PairingException("response names a different guardian");
        }
        PublicKey device = loadPublicB64(values.get("device"));
        String body = responseBody(values.get("device"), offer.guardianB64, offer.nonceB64);
        if (!verify(device, body, decode(values.get("proof")))) {
            throw new PairingException("response proof is invalid");
        }
        String sas = sas(guardian, device, offer.guardianB64, values.get("device"), offer.nonceB64);
        return new Result(device, values.get("device"), sas);
    }

    public static String deviceSas(PrivateKey device, PublicKey devicePublic, Offer offer) {
        requireP256(devicePublic);
        return sas(device, offer.guardian, offer.guardianB64, publicB64(devicePublic), offer.nonceB64);
    }

    static String sas(PrivateKey own, PublicKey peer, String guardianB64, String deviceB64,
                      String nonceB64) {
        String info = "{\"device\":\"" + deviceB64 + "\",\"guardian\":\"" + guardianB64
                + "\",\"nonce\":\"" + nonceB64 + "\",\"v\":1}";
        byte[] material = hkdf(sharedSecret(own, peer), "hisn-pair-v1".getBytes(StandardCharsets.UTF_8),
                info.getBytes(StandardCharsets.UTF_8), 32);
        int code = new BigInteger(1, material).mod(BigInteger.valueOf(1_000_000)).intValue();
        return String.format(java.util.Locale.ROOT, "%06d", code);
    }

    static String responseBody(String deviceB64, String guardianB64, String nonceB64) {
        return "{\"device\":\"" + deviceB64 + "\",\"guardian\":\"" + guardianB64
                + "\",\"kind\":\"" + KIND + "\",\"nonce\":\"" + nonceB64 + "\",\"v\":1}";
    }

    private static byte[] sharedSecret(PrivateKey own, PublicKey peer) {
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(own);
            agreement.doPhase(peer, true);
            return agreement.generateSecret();
        } catch (Exception unavailable) {
            throw new PairingException("ECDH failed", unavailable);
        }
    }

    private static byte[] sign(PrivateKey key, String body) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(key);
            signature.update(body.getBytes(StandardCharsets.UTF_8));
            return signature.sign();
        } catch (Exception unavailable) {
            throw new PairingException("cannot sign the response", unavailable);
        }
    }

    private static boolean verify(PublicKey key, String body, byte[] proof) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initVerify(key);
            signature.update(body.getBytes(StandardCharsets.UTF_8));
            return signature.verify(proof);
        } catch (Exception unavailable) {
            return false;
        }
    }

    private static byte[] hkdf(byte[] ikm, byte[] salt, byte[] info, int length) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            byte[] prk = mac.doFinal(ikm);
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update(info);
            mac.update((byte) 1);
            return Arrays.copyOf(mac.doFinal(), length);
        } catch (Exception unavailable) {
            throw new PairingException("HKDF failed", unavailable);
        }
    }

    static Map<String, String> parseFlat(String json) {
        if (json == null) throw new PairingException("missing response");
        Map<String, String> values = new LinkedHashMap<>();
        int[] at = {0};
        skip(json, at);
        expect(json, at, '{');
        while (true) {
            skip(json, at);
            if (peek(json, at) == '}') {
                at[0]++;
                break;
            }
            String key = quoted(json, at);
            skip(json, at);
            expect(json, at, ':');
            skip(json, at);
            String value;
            if (peek(json, at) == '"') {
                value = quoted(json, at);
            } else {
                int start = at[0];
                while (at[0] < json.length()
                        && (Character.isDigit(json.charAt(at[0])) || json.charAt(at[0]) == '-')) {
                    at[0]++;
                }
                if (start == at[0]) throw new PairingException("bad response value");
                value = json.substring(start, at[0]);
            }
            if (values.put(key, value) != null) throw new PairingException("repeated response field");
            skip(json, at);
            char next = peek(json, at);
            if (next == ',') { at[0]++; continue; }
            if (next == '}') { at[0]++; break; }
            throw new PairingException("bad response separator");
        }
        skip(json, at);
        if (at[0] != json.length()) throw new PairingException("trailing response data");
        return values;
    }

    private static String quoted(String json, int[] at) {
        expect(json, at, '"');
        int start = at[0];
        int end = json.indexOf('"', start);
        if (end < 0) throw new PairingException("unterminated response string");
        String value = json.substring(start, end);
        if (value.indexOf('\\') >= 0 || value.indexOf('\n') >= 0) {
            throw new PairingException("escaped response string");
        }
        at[0] = end + 1;
        return value;
    }

    private static char peek(String json, int[] at) {
        return at[0] < json.length() ? json.charAt(at[0]) : '\0';
    }

    private static void expect(String json, int[] at, char expected) {
        if (peek(json, at) != expected) throw new PairingException("bad response syntax");
        at[0]++;
    }

    private static void skip(String json, int[] at) {
        while (at[0] < json.length() && Character.isWhitespace(json.charAt(at[0]))) at[0]++;
    }

    private static boolean hasControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7f) return true;
        }
        return false;
    }

    private static void requireP256(PublicKey key) {
        if (!(key instanceof ECPublicKey)) throw new PairingException("not a P-256 public key");
        if (((ECPublicKey) key).getParams().getCurve().getField().getFieldSize() != 256) {
            throw new PairingException("not a P-256 public key");
        }
    }

    private static void requirePrivate(PrivateKey key) {
        if (key == null || !"EC".equalsIgnoreCase(key.getAlgorithm())) {
            throw new PairingException("not an EC private key");
        }
        // Android Keystore EC keys may not expose ECParameterSpec; the provider
        // still enforces the curve it generated. Software keys are checked.
        if (key instanceof ECPrivateKey) {
            ECPrivateKey ec = (ECPrivateKey) key;
            if (ec.getParams() != null
                    && ec.getParams().getCurve().getField().getFieldSize() != 256) {
                throw new PairingException("not a P-256 private key");
            }
        }
    }

    private static String encode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] decode(String text) {
        try {
            return Base64.getUrlDecoder().decode(text);
        } catch (IllegalArgumentException invalid) {
            throw new PairingException("invalid base64url", invalid);
        }
    }

    private static byte[] decodePrivate(String text) {
        try {
            return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException notStandard) {
            return decode(text);
        }
    }

    public static final class PairingException extends RuntimeException {
        PairingException(String message) { super(message); }
        PairingException(String message, Throwable cause) { super(message, cause); }
    }
}
