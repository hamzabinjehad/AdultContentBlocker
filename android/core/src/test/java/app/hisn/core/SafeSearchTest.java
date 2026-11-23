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
        for (String country : new String[] {"google.co.uk", "www.google.com.sa", "www.google.de", "google.ae",
                "www.google.com.eg", "www.google.com.pk", "www.google.co.id", "www.google.com.tr", "WWW.GOOGLE.FR."}) {
            check(SafeSearch.GOOGLE.equals(SafeSearch.target(country)), "published Google domain: " + country);
        }
        check(SafeSearch.GOOGLE.equals(SafeSearch.target("images.google.com")), "Google Images");
        check(SafeSearch.target("google.example") == null, "unpublished Google suffix");
        check(SafeSearch.target("mail.google.com") == null && SafeSearch.target("accounts.google.com") == null,
                "other Google services keep their own address");
        check(SafeSearch.target("www.google.co.uk.evil.example") == null, "country suffix confused");
        check(SafeSearch.target("www.google.com.evil.example") == null, "suffix confused");
        for (String api : new String[] {"youtubei.googleapis.com", "youtube.googleapis.com", "www.youtube-nocookie.com"}) {
            check(SafeSearch.YOUTUBE.equals(SafeSearch.target(api)), "documented YouTube name: " + api);
        }
        check(SafeSearch.target("youtube-nocookie.com") == null, "bare nocookie name fails the target's certificate");
        check(SafeSearch.target("www.googleapis.com") == null && SafeSearch.target("music.youtube.com") == null,
                "undocumented Google names left alone");
        for (String yandex : new String[] {"yandex.ru", "www.yandex.com", "ya.ru", "yandex.com.tr", "www.yandex.kz"}) {
            check(SafeSearch.YANDEX.equals(SafeSearch.target(yandex)), "Yandex family search: " + yandex);
        }
        check(SafeSearch.target("yandex.ua") == null, "yandex.ua fails the target's certificate");
        check(SafeSearch.target("mail.yandex.ru") == null, "other Yandex services left alone");
        check(SafeSearch.PIXABAY.equals(SafeSearch.target("www.pixabay.com")), "Pixabay safe search");
        check(SafeSearch.target("notgoogle.com") == null, "prefix confused");
        check(SafeSearch.target(null) == null, "null target");
        for (String target : new String[] {SafeSearch.GOOGLE, SafeSearch.BING, SafeSearch.DUCKDUCKGO,
                SafeSearch.YOUTUBE, SafeSearch.YANDEX, SafeSearch.PIXABAY}) {
            check(SafeSearch.target(target) == null, "target rewritten: " + target);
        }
        System.out.println("SafeSearch tests passed: Google's published domains, YouTube's documented names, "
                + "Bing, DuckDuckGo, Yandex and Pixabay; other services and suffixes left alone");
    }
}
