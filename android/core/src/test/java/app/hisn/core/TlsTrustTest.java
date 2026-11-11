package app.hisn.core;

import java.io.FileInputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * The bundled old-Android resolver anchor. The fixture is the certificate shipped in the APK;
 * test-core.sh also generates an unrelated self-signed certificate for the rejection case.
 */
public final class TlsTrustTest {
    /** The DER digest pinned in ResolverTls and in the Android README. */
    private static final String ROOT_DER_SHA256 =
            "3417bb06cc6007da1b961c920b8ab4ce3fad820e4aa30b9acbc4a74ebdcebc65";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException(
                "TlsTrustTest needs the bundled root and an unrelated certificate");
        X509Certificate root = load(args[0]);
        X509Certificate unrelated = load(args[1]);

        String digest = sha256(root.getEncoded());
        if (!ROOT_DER_SHA256.equals(digest)) throw new AssertionError("Bundled root digest changed: " + digest);
        if (root.getBasicConstraints() < 0) throw new AssertionError("Bundled root is not a CA");
        if (!root.getSubjectX500Principal().equals(root.getIssuerX500Principal()))
            throw new AssertionError("Bundled root is not self-signed");
        if (!root.getSubjectX500Principal().getName().contains("SSL.com Root Certification Authority ECC"))
            throw new AssertionError("Unexpected bundled root subject");

        javax.net.ssl.X509TrustManager pinned = TlsTrust.pinned(root);
        if (pinned.getAcceptedIssuers().length != 1 || !root.equals(pinned.getAcceptedIssuers()[0]))
            throw new AssertionError("Pinned manager does not expose its root");

        pinned.checkServerTrusted(new X509Certificate[]{root}, "ECDHE_ECDSA");
        try {
            pinned.checkServerTrusted(new X509Certificate[]{unrelated}, "ECDHE_ECDSA");
            throw new AssertionError("An unrelated certificate was trusted");
        } catch (CertificateException expected) {
            // The chain does not build to the bundled root.
        }

        if (TlsTrust.socketFactory(root) == null) throw new AssertionError("No socket factory");
        javax.net.ssl.X509TrustManager combined = TlsTrust.combined(
                TlsTrust.systemTrustManager(), pinned);
        if (combined.getAcceptedIssuers().length < 2)
            throw new AssertionError("Combined manager lost one trust store");
        System.out.println("TlsTrust tests passed (pinned root, unrelated rejection and combined store)");
    }

    private static X509Certificate load(String path) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (FileInputStream input = new FileInputStream(path)) {
            return (X509Certificate) factory.generateCertificate(input);
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder(64);
        for (byte value : hash) {
            hex.append(Character.forDigit((value >>> 4) & 15, 16));
            hex.append(Character.forDigit(value & 15, 16));
        }
        return hex.toString();
    }
}
