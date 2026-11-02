package app.hisn.core;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

/**
 * The Java client must parse and verify the exact bytes produced by the Python
 * reference, not a Java-only variant of the protocol.
 */
public final class GuardianPairingTest {
    private static final String GUARDIAN_PKCS8 =
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgnxN9frd2CpQDvpU/hscD1/zcAEOzMIISMde/LDV8j3ChRANCAARsq2X78RAYhQtIHrcKY36Np6VXZNzOsIiJ2ZCh4W0yjKsXUrs8CWbCcLxDAKPXvAkxKCjH0njj38LXl9anPIEo";
    private static final String GUARDIAN_PUB =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEbKtl-_EQGIULSB63CmN-jaelV2TczrCIidmQoeFtMoyrF1K7PAlmwnC8QwCj17wJMSgox9J449_C15fWpzyBKA";
    private static final String DEVICE_PKCS8 =
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgvPHXXLuLiYudTmPi0A6tMWtQFaku3j0LpLvDplLLPaOhRANCAARAUyVBPhhojZ9F+gvYx3xIl6vQBk0P421A4lIkF9TkZkRlUqWSkYE7TdhbqFItWKsUoGlM6BdGlEfaAHFfHLCY";
    private static final String DEVICE_PUB =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEQFMlQT4YaI2fRfoL2Md8SJer0AZND-NtQOJSJBfU5GZEZVKlkpGBO03YW6hSLVirFKBpTOgXRpRH2gBxXxywmA";
    private static final String OFFER =
            "hisn://pair/1?gk=MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEbKtl-_EQGIULSB63CmN-jaelV2TczrCIidmQoeFtMoyrF1K7PAlmwnC8QwCj17wJMSgox9J449_C15fWpzyBKA&nonce=AAECAwQFBgcICQoLDA0ODw&label=Fixture+guardian";
    private static final String RESPONSE =
            "{\"device\":\"MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEQFMlQT4YaI2fRfoL2Md8SJer0AZND-NtQOJSJBfU5GZEZVKlkpGBO03YW6hSLVirFKBpTOgXRpRH2gBxXxywmA\","
            + "\"guardian\":\"MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEbKtl-_EQGIULSB63CmN-jaelV2TczrCIidmQoeFtMoyrF1K7PAlmwnC8QwCj17wJMSgox9J449_C15fWpzyBKA\","
            + "\"kind\":\"pair-response\",\"nonce\":\"AAECAwQFBgcICQoLDA0ODw\","
            + "\"proof\":\"MEUCIBstIW4mR8KIwUJniKsfByoPJM3MD9R_VcyUFdUSEp_sAiEAikif43h1_RkDj-PzxrOH476EwmA_7aVnJyzFvhiVbjw\",\"v\":1}";
    private static final String SAS = "754636";

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static void refuses(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (GuardianPairing.PairingException expected) { }
    }

    public static void main(String[] args) throws Exception {
        PrivateKey guardian = GuardianPairing.loadPrivateB64(GUARDIAN_PKCS8);
        PublicKey guardianPublic = GuardianPairing.loadPublicB64(GUARDIAN_PUB);
        PrivateKey device = GuardianPairing.loadPrivateB64(DEVICE_PKCS8);
        PublicKey devicePublic = GuardianPairing.loadPublicB64(DEVICE_PUB);
        check(GUARDIAN_PUB.equals(GuardianPairing.publicB64(guardianPublic)), "guardian key round-trip");
        check(DEVICE_PUB.equals(GuardianPairing.publicB64(devicePublic)), "device key round-trip");
        check(GuardianPairing.deviceId(devicePublic).length() == 43, "device id length");
        check(GuardianPairing.deviceId(devicePublic).equals(GuardianPairing.deviceId(devicePublic)),
                "device id is not stable");

        GuardianPairing.Offer offer = GuardianPairing.parseOffer(OFFER);
        check(GUARDIAN_PUB.equals(offer.guardianB64), "offer guardian");
        check(offer.nonce.length == 16, "offer nonce length");
        check("Fixture guardian".equals(offer.label), "offer label decoding");
        check(offer.uri.equals(OFFER), "offer uri preserved");

        GuardianPairing.Result verified = GuardianPairing.verifyResponse(offer, RESPONSE, guardian);
        check(SAS.equals(verified.sas), "Python fixture SAS: " + verified.sas);
        check(DEVICE_PUB.equals(verified.deviceB64), "Python fixture device key");
        check(SAS.equals(GuardianPairing.deviceSas(device, devicePublic, offer)), "device-side SAS");

        String built = GuardianPairing.buildResponse(device, devicePublic, offer);
        check(built.contains("\"kind\":\"pair-response\"") && built.contains("\"v\":1"),
                "built response fields");
        GuardianPairing.Result builtResult = GuardianPairing.verifyResponse(offer, built, guardian);
        check(builtResult.sas.equals(SAS), "Java-built response SAS");
        check(builtResult.deviceB64.equals(DEVICE_PUB), "Java-built device");

        refuses(() -> GuardianPairing.verifyResponse(offer,
                RESPONSE.replace("\"proof\":\"", "\"proof\":\"AA"), guardian), "tampered proof accepted");
        refuses(() -> GuardianPairing.verifyResponse(offer,
                RESPONSE.replace("\"nonce\":\"AAECAwQFBgcICQoLDA0ODw\"",
                        "\"nonce\":\"AAAAAAAAAAAAAAAAAAAAAA\""), guardian), "wrong nonce accepted");
        refuses(() -> GuardianPairing.verifyResponse(offer,
                RESPONSE.replace("}", ",\"extra\":\"x\"}"), guardian), "extra field accepted");
        refuses(() -> GuardianPairing.parseOffer(OFFER.replace("hisn://pair/1", "https://pair/1")),
                "wrong scheme accepted");
        refuses(() -> GuardianPairing.parseOffer(OFFER + "&nonce=AAECAwQFBgcICQoLDA0ODw"),
                "duplicate parameter accepted");
        GuardianPairing.Offer noLabel = GuardianPairing.parseOffer(
                OFFER.replace("&label=Fixture+guardian", ""));
        check(noLabel.label.isEmpty(), "a missing label is allowed");

        KeyPair generated = GuardianPairing.generateKeyPair();
        check(GuardianPairing.deviceId(generated.getPublic()).length() == 43, "generated device id");
        check(GuardianPairing.publicB64(generated.getPublic()).startsWith("MFkw"),
                "generated public key is not P-256 DER");

        System.out.println("GuardianPairing tests passed: Python fixture, Java-built response, "
                + "SAS equality and rejection paths");
    }
}
