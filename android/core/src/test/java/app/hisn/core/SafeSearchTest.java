package app.hisn.core;

/** SafeSearch rewrites only the exact hosts whose target certificate is known. */
public final class SafeSearchTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static void main(String[] args) {
        check(SafeSearch.GOOGLE.equals(SafeSearch.target("google.com")), "google");
        check(SafeSearch.GOOGLE.equals(SafeSearch.target("WWW.Google.com.")), "google case/dot");
        check(SafeSearch.BING.equals(SafeSearch.target("www.bing.com")), "bing");
        check(SafeSearch.DUCKDUCKGO.equals(SafeSearch.target("duckduckgo.com")), "duckduckgo");
        check(SafeSearch.YOUTUBE.equals(SafeSearch.target("m.youtube.com")), "youtube mobile");
        check(SafeSearch.YOUTUBE.equals(SafeSearch.target("www.youtube.com")), "youtube web");
        check(SafeSearch.target("google.co.uk") == null, "country Google guessed");
        check(SafeSearch.target("www.google.com.evil.example") == null, "suffix confused");
        check(SafeSearch.target("youtubei.googleapis.com") == null, "YouTube API guessed");
        check(SafeSearch.target("notgoogle.com") == null, "prefix confused");
        check(SafeSearch.target(null) == null, "null target");
        check(SafeSearch.target("forcesafesearch.google.com") == null, "target rewritten");
        System.out.println("SafeSearch tests passed: exact Google/Bing/DuckDuckGo/YouTube mapping, "
                + "country and suffix names left alone");
    }
}
