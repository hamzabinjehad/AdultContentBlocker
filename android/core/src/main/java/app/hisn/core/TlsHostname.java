package app.hisn.core;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

/**
 * RFC 6125 DNS-name verification for the encrypted resolver.
 *
 * <p>The TLS library authenticates the certificate chain, but an {@code SSLSocket} does not compare
 * the name in the certificate with the name the client asked for before Android 7.0. Enabling the
 * platform endpoint-identification parameter would therefore leave Android 6 with any valid
 * certificate, not necessarily Cloudflare's. This check runs on every release after the handshake,
 * so the resolver name is authenticated on old and current Android alike.</p>
 *
 * <p>Matching follows the rules a browser applies: case-insensitive, one trailing dot ignored, a
 * wildcard only as the complete leftmost label ({@code *.example.com} matches
 * {@code a.example.com} but not {@code example.com} or {@code a.b.example.com}), and the subject
 * common name used only when the certificate carries no subjectAltName extension at all.</p>
 */
public final class TlsHostname {
    private TlsHostname() { }

    /** Throws an {@link SSLHandshakeException} unless the session's leaf certificate names host. */
    public static void verify(String hostname, SSLSession session) throws SSLException {
        if (hostname == null || hostname.isEmpty() || session == null) {
            throw new SSLHandshakeException("TLS identity missing");
        }
        java.security.cert.Certificate[] chain;
        try {
            chain = session.getPeerCertificates();
        } catch (SSLPeerUnverifiedException unavailable) {
            throw new SSLHandshakeException("TLS identity missing");
        }
        if (chain == null || chain.length == 0 || !(chain[0] instanceof X509Certificate)) {
            throw new SSLHandshakeException("TLS identity missing");
        }
        if (!matches(hostname, (X509Certificate) chain[0])) {
            throw new SSLHandshakeException("TLS certificate is not for " + hostname);
        }
    }

    /** Whether the certificate names the host; any malformed certificate data is a mismatch. */
    public static boolean matches(String hostname, X509Certificate certificate) {
        if (hostname == null || certificate == null) return false;
        String host = canonical(hostname);
        // The resolver hostname is a plain DNS name; a literal wildcard is never a host to verify.
        if (host.isEmpty() || host.indexOf('*') >= 0 || host.startsWith(".") || host.contains("..")) {
            return false;
        }

        Collection<List<?>> names;
        try {
            names = certificate.getSubjectAlternativeNames();
        } catch (CertificateParsingException malformed) {
            return false;
        }
        boolean sawSan = names != null && !names.isEmpty();
        boolean sawDnsName = false;
        if (names != null) {
            for (List<?> entry : names) {
                if (entry == null || entry.size() < 2) continue;
                Object type = entry.get(0);
                Object value = entry.get(1);
                if (!(type instanceof Integer) || !(value instanceof String)) continue;
                if (((Integer) type).intValue() != 2) continue; // 2 = dNSName
                sawDnsName = true;
                if (matchesPattern(host, (String) value)) return true;
            }
        }
        if (sawSan || sawDnsName) return false;
        // A certificate with no subjectAltName at all is the one legacy case where CN still counts.
        return matchesPattern(host, commonName(certificate.getSubjectX500Principal().getName()));
    }

    private static String canonical(String name) {
        String value = name.trim().toLowerCase(Locale.ROOT);
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static boolean matchesPattern(String host, String certificateName) {
        if (certificateName == null) return false;
        String pattern = canonical(certificateName);
        if (pattern.isEmpty()) return false;
        int star = pattern.indexOf('*');
        if (star < 0) return host.equals(pattern);
        // A wildcard is valid only as the whole first label, and only as the sole wildcard.
        if (star != 0 || pattern.length() < 3 || pattern.charAt(1) != '.'
                || pattern.indexOf('*', 1) >= 0) {
            return false;
        }
        int dot = host.indexOf('.');
        if (dot <= 0) return false; // No label before the suffix, or no suffix at all.
        return host.substring(dot).equals(pattern.substring(1));
    }

    /**
     * The first CN in an RFC 2253 distinguished name. Only used for certificates without a
     * subjectAltName extension; Cloudflare's certificate carries dNSName entries. Escaped commas
     * are rare in that legacy path and are deliberately treated as separators rather than guessed.
     */
    private static String commonName(String distinguishedName) {
        if (distinguishedName == null) return null;
        for (String part : distinguishedName.split(",")) {
            int equals = part.indexOf('=');
            if (equals <= 0) continue;
            if (!"CN".equalsIgnoreCase(part.substring(0, equals).trim())) continue;
            String value = part.substring(equals + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            return value;
        }
        return null;
    }
}
