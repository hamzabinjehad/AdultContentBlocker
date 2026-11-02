package app.hisn.core;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;

/** The Android policy verifier must accept the Python-signed envelope and refuse tampering. */
public final class GuardianPolicyTest {
    private static final String GUARDIAN_PUB =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEbKtl-_EQGIULSB63CmN-jaelV2TczrCIidmQoeFtMoyrF1K7PAlmwnC8QwCj17wJMSgox9J449_C15fWpzyBKA";
    private static final String DEVICE = "NeqHtCnX5zgprbc4HCDg2oJBiAH91a2so3F__Z5Qs-k";
    private static final long NOW = 1700000000L;
    private static final String POLICY =
            "{\"counter\":7,\"device\":\"NeqHtCnX5zgprbc4HCDg2oJBiAH91a2so3F__Z5Qs-k\","
            + "\"issued_at\":1700000000,\"kind\":\"policy\",\"not_after\":1700003600,"
            + "\"rules\":\"eyJibG9ja2VkX2hvc3RzX3NoYTI1NiI6IjAwIiwic2NoZWR1bGUiOnsiZW5kIjo0MjAsInN0YXJ0IjoxMzIwfSwic3RyaWN0Ijp0cnVlfQ\","
            + "\"sig\":\"MEUCIQDNbmR3X0QzyS-6CB1iagQl7rJVM5NZF7NfhZYqlxuUjAIgXwhu6gxyFnf3c2VHYH0q_rek7h0Y7SA67rCzt9R8bDc\",\"v\":2}";

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static void refuses(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (GuardianPairing.PairingException expected) { }
    }

    public static void main(String[] args) throws Exception {
        PublicKey guardian = GuardianPairing.loadPublicB64(GUARDIAN_PUB);
        GuardianPolicy.Verified verified = GuardianPolicy.verify(guardian, POLICY, DEVICE, NOW);
        check(verified.counter == 7, "counter");
        check(verified.issuedAt == NOW && verified.notAfter == NOW + 3600, "times");
        check(verified.device.equals(DEVICE), "device");
        String rules = new String(verified.rulesJson, StandardCharsets.UTF_8);
        check(rules.contains("\"strict\":true") && rules.contains("\"blocked_hosts_sha256\":\"00\""),
                "rules bytes");

        check(GuardianPolicy.verify(guardian, POLICY, DEVICE, NOW, 6, 300).counter == 7,
                "older counter accepted");
        refuses(() -> GuardianPolicy.verify(guardian, POLICY, DEVICE, NOW, 7, 300),
                "replayed counter accepted");
        refuses(() -> GuardianPolicy.verify(guardian, POLICY, "other", NOW), "wrong device accepted");
        refuses(() -> GuardianPolicy.verify(guardian, POLICY, DEVICE, NOW + 3600),
                "expired policy accepted");
        refuses(() -> GuardianPolicy.verify(guardian, POLICY, DEVICE, NOW - 400),
                "future policy accepted");
        check(GuardianPolicy.verify(guardian, POLICY, DEVICE, NOW - 200).counter == 7,
                "small clock skew refused");
        refuses(() -> GuardianPolicy.verify(guardian,
                POLICY.replace("\"sig\":\"", "\"sig\":\"AA"), DEVICE, NOW),
                "tampered signature accepted");
        refuses(() -> GuardianPolicy.verify(guardian,
                POLICY.replace("\"rules\":\"eyJ", "\"rules\":\"e30"), DEVICE, NOW),
                "tampered rules accepted");
        refuses(() -> GuardianPolicy.verify(guardian,
                POLICY.replace("}", ",\"extra\":\"x\"}"), DEVICE, NOW),
                "extra field accepted");
        refuses(() -> GuardianPolicy.verify(guardian, POLICY.replace("\"v\":2", "\"v\":3"),
                DEVICE, NOW), "wrong version accepted");
        refuses(() -> GuardianPolicy.verify(guardian,
                POLICY.replace("\"counter\":7", "\"counter\":07"), DEVICE, NOW),
                "non-canonical integer accepted");
        refuses(() -> GuardianPolicy.verify(guardian,
                POLICY.replace("\"rules\":\"eyJ", "\"rules\":\"bm90"), DEVICE, NOW),
                "non-object rules accepted");

        System.out.println("GuardianPolicy tests passed: Python fixture, rules bytes, replay, "
                + "expiry, future, tamper and canonical-number rejection");
    }
}
