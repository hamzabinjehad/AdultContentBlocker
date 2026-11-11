package app.hisn.core;

import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * The TLS trust decision for the encrypted resolver.
 *
 * <p>Android 6's system trust store predates the root that signs Cloudflare's DNS-over-TLS
 * certificate, so the system check alone rejects the resolver on that release while newer
 * releases accept it. Hisn bundles that one public root and accepts a server chain only when the
 * system trust store accepts it or when it builds to the pinned root. The custom context is used
 * for the resolver socket only; no other connection in the app inherits it.</p>
 *
 * <p>The extra anchor is not a pin of Cloudflare's leaf certificate. It keeps the resolver
 * reachable across Cloudflare's certificate renewals while the name check in
 * {@link TlsHostname} still requires a certificate for the configured resolver hostname.</p>
 */
public final class TlsTrust {
    private TlsTrust() { }

    /** Reads one X.509 certificate from a PEM or DER stream. */
    public static X509Certificate certificate(InputStream input) throws CertificateException {
        if (input == null) throw new CertificateException("Missing trust anchor");
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        java.security.cert.Certificate certificate = factory.generateCertificate(input);
        if (!(certificate instanceof X509Certificate)) {
            throw new CertificateException("Trust anchor is not X.509");
        }
        return (X509Certificate) certificate;
    }

    /**
     * A socket factory that accepts the system trust store plus one extra root. The root must be a
     * CA certificate and self-signed; anything else is refused before it can widen trust.
     */
    public static SSLSocketFactory socketFactory(X509Certificate extraRoot)
            throws GeneralSecurityException {
        if (!isTrustAnchor(extraRoot)) throw new CertificateException("Invalid extra trust anchor");
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{combined(systemTrustManager(), pinned(extraRoot))}, null);
        return context.getSocketFactory();
    }

    /** The platform's normal server trust manager. */
    public static X509TrustManager systemTrustManager() throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        factory.init((java.security.KeyStore) null);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager) return (X509TrustManager) manager;
        }
        throw new GeneralSecurityException("No X.509 trust manager");
    }

    /** A trust manager that accepts only a chain building to the supplied root. */
    public static X509TrustManager pinned(X509Certificate root) throws CertificateException {
        if (!isTrustAnchor(root)) throw new CertificateException("Invalid pinned root");
        return new PinnedRoot(root);
    }

    /** Accepts when either delegate accepts; both must have already validated the chain. */
    public static X509TrustManager combined(X509TrustManager primary, X509TrustManager fallback) {
        if (primary == null || fallback == null) throw new IllegalArgumentException("Missing trust manager");
        return new Combined(primary, fallback);
    }

    private static boolean isTrustAnchor(X509Certificate certificate) {
        return certificate != null
                && certificate.getBasicConstraints() >= 0
                && certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal());
    }

    private static final class PinnedRoot implements X509TrustManager {
        private final X509Certificate root;

        PinnedRoot(X509Certificate root) { this.root = root; }

        @Override public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            throw new CertificateException("Client certificates are not accepted");
        }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            if (chain == null || chain.length == 0) throw new CertificateException("Empty chain");
            final CertPath path;
            try {
                path = CertificateFactory.getInstance("X.509")
                        .generateCertPath(Arrays.asList(chain));
            } catch (CertificateException invalid) {
                throw new CertificateException("Malformed certificate chain", invalid);
            }
            try {
                PKIXParameters parameters = new PKIXParameters(
                        Collections.singleton(new TrustAnchor(root, null)));
                parameters.setRevocationEnabled(false);
                CertPathValidator.getInstance("PKIX").validate(path, parameters);
            } catch (GeneralSecurityException rejected) {
                throw new CertificateException("Chain is not issued by the pinned resolver root", rejected);
            }
        }

        @Override public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[]{root};
        }
    }

    private static final class Combined implements X509TrustManager {
        private final X509TrustManager primary;
        private final X509TrustManager fallback;

        Combined(X509TrustManager primary, X509TrustManager fallback) {
            this.primary = primary;
            this.fallback = fallback;
        }

        @Override public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            CertificateException failure = null;
            try {
                primary.checkClientTrusted(chain, authType);
                return;
            } catch (CertificateException rejected) { failure = rejected; }
            try {
                fallback.checkClientTrusted(chain, authType);
                return;
            } catch (CertificateException rejected) { failure = rejected; }
            throw failure;
        }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            CertificateException failure = null;
            try {
                primary.checkServerTrusted(chain, authType);
                return;
            } catch (CertificateException rejected) { failure = rejected; }
            try {
                fallback.checkServerTrusted(chain, authType);
                return;
            } catch (CertificateException rejected) { failure = rejected; }
            throw failure;
        }

        @Override public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> issuers = new ArrayList<>();
            issuers.addAll(Arrays.asList(primary.getAcceptedIssuers()));
            issuers.addAll(Arrays.asList(fallback.getAcceptedIssuers()));
            return issuers.toArray(new X509Certificate[0]);
        }
    }
}
