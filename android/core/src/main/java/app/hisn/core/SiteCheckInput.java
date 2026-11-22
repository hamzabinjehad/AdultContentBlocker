package app.hisn.core;

import java.util.Locale;

/**
 * Parses the read-only site checker's input. It accepts a bare hostname or an
 * HTTP(S) URL and returns the canonical hostname only; path, query, fragment,
 * credentials and port are never evaluated. Every other scheme or shape is
 * refused, not guessed at.
 */
public final class SiteCheckInput {
    public static final class Invalid extends IllegalArgumentException {
        public Invalid(String message) { super(message); }
    }

    /** The canonical hostname, or throws {@link Invalid} with a user-facing reason code. */
    public static String host(String raw) {
        if (raw == null) throw new Invalid("empty");
        String text = raw.trim();
        if (text.isEmpty()) throw new Invalid("empty");
        String authority;
        int scheme = text.indexOf("://");
        if (scheme >= 0) {
            String protocol = text.substring(0, scheme).toLowerCase(Locale.ROOT);
            if (!"http".equals(protocol) && !"https".equals(protocol)) throw new Invalid("scheme");
            authority = text.substring(scheme + 3);
        } else if (text.startsWith("//")) {
            authority = text.substring(2);
        } else {
            // A bare hostname has no path, query, fragment or credentials.
            if (text.indexOf('/') >= 0 || text.indexOf('?') >= 0 || text.indexOf('#') >= 0
                    || text.indexOf('@') >= 0 || text.indexOf(':') >= 0
                    || containsWhitespace(text)) throw new Invalid("hostname");
            authority = text;
        }
        authority = authority.substring(0, authorityEnd(authority));
        int at = authority.lastIndexOf('@');
        if (at >= 0) authority = authority.substring(at + 1);
        if (authority.isEmpty()) throw new Invalid("host");
        // A single colon is a port; brackets are an IP literal, which Hostname refuses.
        int colon = authority.lastIndexOf(':');
        if (colon >= 0 && authority.indexOf(']') < 0) {
            String port = authority.substring(colon + 1);
            if (port.isEmpty() || !port.matches("[0-9]{1,5}")) throw new Invalid("host");
            authority = authority.substring(0, colon);
        }
        if (authority.isEmpty()) throw new Invalid("host");
        try {
            return Hostname.normalize(authority);
        } catch (IllegalArgumentException invalid) {
            throw new Invalid("hostname");
        }
    }

    /** A rule editor also accepts a pasted address without its HTTP(S) scheme. */
    public static String ruleHost(String raw) {
        if (raw == null || raw.length() > 8192) throw new Invalid("host");
        String text = raw.trim();
        int boundary = authorityEnd(text);
        int scheme = text.indexOf("://");
        if (text.startsWith("//")) return host("https:" + text);
        if (scheme >= 0 && scheme < boundary) return host(text);
        int at = text.indexOf('@');
        if (at >= 0 && at < boundary) throw new Invalid("hostname");
        return host("https://" + text);
    }

    private static int authorityEnd(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '/' || ch == '?' || ch == '#') return i;
        }
        return text.length();
    }

    private static boolean containsWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) if (Character.isWhitespace(text.charAt(i))) return true;
        return false;
    }

    private SiteCheckInput() { }
}
