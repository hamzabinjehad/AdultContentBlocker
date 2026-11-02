package app.hisn.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Search-engine hostnames that Hisn answers with a SafeSearch CNAME.
 *
 * <p>A DNS server can force SafeSearch by returning a CNAME instead of the
 * engine's own address; the client follows the target and the target serves the
 * restricted experience. Hisn rewrites only exact hostnames it knows. Country
 * domains, `youtubei` APIs and app-specific endpoints are deliberately not
 * guessed: a wrong rewrite can break sign-in or video playback, and the
 * target's certificate may not cover the original name.
 */
public final class SafeSearch {
    public static final String GOOGLE = "forcesafesearch.google.com";
    public static final String BING = "strict.bing.com";
    public static final String DUCKDUCKGO = "safe.duckduckgo.com";
    public static final String YOUTUBE = "restrict.youtube.com";

    private static final Set<String> GOOGLE_NAMES = new HashSet<>(Arrays.asList(
            "google.com", "www.google.com"));
    private static final Set<String> BING_NAMES = new HashSet<>(Arrays.asList(
            "bing.com", "www.bing.com"));
    private static final Set<String> DUCKDUCKGO_NAMES = new HashSet<>(Arrays.asList(
            "duckduckgo.com", "www.duckduckgo.com"));
    private static final Set<String> YOUTUBE_NAMES = new HashSet<>(Arrays.asList(
            "youtube.com", "www.youtube.com", "m.youtube.com"));

    private SafeSearch() { }

    /** The CNAME target for an exact search hostname, or null to forward normally. */
    public static String target(String hostname) {
        if (hostname == null) return null;
        String name = hostname.toLowerCase(Locale.ROOT);
        if (name.endsWith(".")) name = name.substring(0, name.length() - 1);
        if (GOOGLE_NAMES.contains(name)) return GOOGLE;
        if (BING_NAMES.contains(name)) return BING;
        if (DUCKDUCKGO_NAMES.contains(name)) return DUCKDUCKGO;
        if (YOUTUBE_NAMES.contains(name)) return YOUTUBE;
        return null;
    }
}
