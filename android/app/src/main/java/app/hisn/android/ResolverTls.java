package app.hisn.android;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLSocketFactory;

import app.hisn.core.TlsTrust;

/**
 * Builds the trust context for the DNS-over-TLS resolver.
 *
 * <p>Cloudflare's DoT chain is rooted at SSL.com's 2016 ECC root, which Android 6's 2015 system
 * store does not contain. The root is shipped in the APK, checked against the SHA-256 below, and
 * added as a second trust anchor for this socket only. Newer releases accept the same chain
 * through their system store, so the extra anchor changes nothing there. If the asset is missing
 * or the pin does not match, the factory is not built and the caller falls back to the platform
 * store: an old device then fails closed rather than trusting something unverified.</p>
 */
final class ResolverTls {
    private static final String TAG = "HisnTls";
    private static final String ROOT_ASSET = "trust/ssl-com-root-ecc.crt";
    /** Subject: C=US, O=SSL Corporation, CN=SSL.com Root Certification Authority ECC. */
    private static final String ROOT_DER_SHA256 =
            "3417bb06cc6007da1b961c920b8ab4ce3fad820e4aa30b9acbc4a74ebdcebc65";
    private static final int MAX_ROOT_BYTES = 8192;

    private static SSLSocketFactory cached;
    private static boolean loaded;

    private ResolverTls() { }

    /** The resolver socket factory, or null when the bundled anchor cannot be verified. */
    static synchronized SSLSocketFactory factory(Context context) {
        if (loaded) return cached;
        loaded = true;
        try (InputStream input = context.getAssets().open(ROOT_ASSET)) {
            X509Certificate root = TlsTrust.certificate(new ByteArrayInputStream(read(input)));
            if (!ROOT_DER_SHA256.equals(sha256(root.getEncoded()))) {
                Log.w(TAG, "Bundled resolver trust anchor does not match its pin");
                return null;
            }
            cached = TlsTrust.socketFactory(root);
            return cached;
        } catch (Exception unavailable) {
            Log.w(TAG, "Bundled resolver trust anchor unavailable", unavailable);
            return null;
        }
    }

    private static byte[] read(InputStream input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > MAX_ROOT_BYTES) throw new IllegalStateException("Trust anchor too large");
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
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
