package app.hisn.core;

import java.util.Locale;

/**
 * The site a browser's address bar shows, read from the bar's text.
 *
 * <p>Chrome and its relatives show an open page as "example.com/path" or with its
 * scheme; the bar holds a search phrase only while it is being typed, and the caller
 * reads it only when it is not focused. Anything that is not one URL-shaped token
 * returns null and is left to the other layers: a missed site is never turned into a
 * block of something else.
 */
public final class AddressBarHost {
    private AddressBarHost() { }

    /** The canonical hostname of the page shown, or null for a search phrase, an IP or a browser page. */
    public static String host(CharSequence address) {
        if (address == null) return null;
        String text = address.toString().trim();
        if (text.isEmpty() || text.length() > 2048) return null;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int scheme = lower.indexOf("://");
        if (scheme >= 0) {
            String name = lower.substring(0, scheme);
            if (!name.equals("http") && !name.equals("https")) return null;
            text = text.substring(scheme + 3);
        } else if (lower.startsWith("chrome:") || lower.startsWith("about:") || lower.startsWith("file:")
                || lower.startsWith("content:") || lower.startsWith("data:") || lower.startsWith("javascript:")) {
            return null;
        }
        int end = text.length();
        for (char stop : new char[] {'/', '?', '#'}) {
            int at = text.indexOf(stop);
            if (at >= 0 && at < end) end = at;
        }
        String authority = text.substring(0, end);
        int user = authority.lastIndexOf('@');
        if (user >= 0) authority = authority.substring(user + 1);
        if (authority.startsWith("[")) return null; // An IPv6 literal names no site.
        int port = authority.indexOf(':');
        if (port >= 0) authority = authority.substring(0, port);
        if (authority.indexOf('.') < 0) return null;
        try { return Hostname.normalize(authority); }
        catch (IllegalArgumentException notAHost) { return null; }
    }
}
