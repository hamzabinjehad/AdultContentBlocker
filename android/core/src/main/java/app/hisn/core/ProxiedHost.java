package app.hisn.core;

import java.net.IDN;

/**
 * Reads the original site out of a proxy hostname that spells it in one label.
 *
 * <p>Google Translate's website proxy serves www.example.com at
 * www-example-com.translate.goog, and the AMP cache serves it at
 * www-example-com.cdn.ampproject.org. Both write the original name with each "-"
 * doubled and each "." turned into "-", and wrap a label whose third and fourth
 * characters would read "--" (the punycode marker) as "0-…-0". A blocked site opened
 * through either proxy therefore resolves a name that no blocklist contains. Decoding
 * the label lets the same decision apply to the proxied copy, and only to it: the
 * proxy's own hostnames, and every site that is not blocked, stay reachable.
 *
 * <p>A name that was too long for one label is replaced by a hash and cannot be read
 * back; it returns null and is decided on its own name, as before.
 */
public final class ProxiedHost {
    private static final String[] SUFFIXES = {".translate.goog", ".cdn.ampproject.org"};

    private ProxiedHost() { }

    /** The canonical original hostname, or null when this is not a readable proxy name. */
    public static String original(String canonicalHost) {
        if (canonicalHost == null) return null;
        for (String suffix : SUFFIXES) {
            if (!canonicalHost.endsWith(suffix)) continue;
            String label = canonicalHost.substring(0, canonicalHost.length() - suffix.length());
            // Exactly one label: a deeper name is the proxy's own infrastructure.
            if (label.isEmpty() || label.indexOf('.') >= 0) return null;
            return decode(label);
        }
        return null;
    }

    static String decode(String label) {
        String text = label;
        if (text.startsWith("xn--")) {
            // A Unicode original is carried as one punycode label; read it back first.
            try { text = IDN.toUnicode(text, IDN.ALLOW_UNASSIGNED); }
            catch (IllegalArgumentException invalid) { return null; }
            if (text.startsWith("xn--")) return null;
        }
        if (text.length() > 4 && text.startsWith("0-") && text.endsWith("-0")
                && text.regionMatches(4, "--", 0, 2)) {
            text = text.substring(2, text.length() - 2);
        }
        StringBuilder host = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '-') { host.append(c); continue; }
            if (i + 1 < text.length() && text.charAt(i + 1) == '-') {
                host.append('-');
                i++;
            } else {
                host.append('.');
            }
        }
        String decoded = host.toString();
        if (decoded.indexOf('.') < 0) return null;
        try { return Hostname.normalize(decoded); }
        catch (IllegalArgumentException invalid) { return null; }
    }
}
