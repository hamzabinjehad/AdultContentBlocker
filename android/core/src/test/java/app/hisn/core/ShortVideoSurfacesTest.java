package app.hisn.core;

public final class ShortVideoSurfacesTest {
    private static void check(boolean value, String label) { if (!value) throw new AssertionError("Short video: " + label); }
    public static void main(String[] args) {
        check(ShortVideoSurfaces.wholeApp("com.zhiliaoapp.musically") && ShortVideoSurfaces.watches("com.zhiliaoapp.musically"),
                "TikTok is short video as a whole");
        check(!ShortVideoSurfaces.wholeApp("com.google.android.youtube") && ShortVideoSurfaces.watches("com.google.android.youtube"),
                "YouTube is watched for Shorts, not blocked");
        check(ShortVideoSurfaces.surfaceViewIds("com.google.android.youtube").contains("com.google.android.youtube:id/reel_recycler"),
                "Shorts player IDs are qualified with their own package");
        check(ShortVideoSurfaces.surfaceViewIds("com.google.android.youtube").contains("com.google.android.youtube:id/reel_watch_fragment_root")
                && !ShortVideoSurfaces.surfaceViewIds("com.google.android.youtube").contains("com.google.android.youtube:id/reel_time_bar"),
                "Shorts fragment counts; the time bar Home also shows does not");
        check(ShortVideoSurfaces.surfaceViewIds("com.instagram.android").contains("com.instagram.android:id/clips_viewer_view_pager"),
                "Reels viewer IDs");
        for (String id : ShortVideoSurfaces.surfaceViewIds("com.instagram.android")) {
            check(id.startsWith("com.instagram.android:id/"), "an app's IDs never name another app");
        }
        check(!ShortVideoSurfaces.wholeApp("com.snapchat.android")
                && ShortVideoSurfaces.surfaceViewIds("com.snapchat.android")
                        .equals(java.util.Arrays.asList("com.snapchat.android:id/spotlight_container")),
                "Snapchat is watched for Spotlight only, never by its general media viewer");
        check(ShortVideoSurfaces.watches("com.android.chrome")
                && "com.android.chrome:id/url_bar".equals(ShortVideoSurfaces.urlBarViewId("com.android.chrome"))
                && ShortVideoSurfaces.surfaceViewIds("com.android.chrome").isEmpty(),
                "Chrome is watched by its address bar only");
        check(!ShortVideoSurfaces.watches("com.example.notes") && !ShortVideoSurfaces.watches(null)
                && ShortVideoSurfaces.urlBarViewId("com.example.notes") == null && ShortVideoSurfaces.surfaceViewIds(null).isEmpty(),
                "other apps are not watched");
        for (String link : new String[] {"youtube.com/shorts/abc123", "https://m.youtube.com/shorts/abc?feature=x",
                "YouTube.com/shorts", "www.instagram.com/reels/xyz/", "instagram.com/reel/abc", "instagram.com/explore",
                "m.facebook.com/reel/123", "facebook.com/watch/reels", "facebook.com/share/reel/9",
                "snapchat.com/spotlight/abc", "tiktok.com", "\u200Etiktok.com", "\u200Fwww.tiktok.com/@a\u200E", "www.tiktok.com/@someone/video/1", "vm.tiktok.com/ZM123/"}) {
            check(ShortVideoSurfaces.shortVideoLink(link), "short-video link matched: " + link);
        }
        for (String link : new String[] {"youtube.com/watch?v=abc", "youtube.com", "youtube.com/@channel/shorts",
                "instagram.com/someone", "facebook.com/someone/posts/1", "snapchat.com", "notiktok.com",
                "tiktok.com.evil.example", "google.com/search?q=youtube.com/shorts", "youtube.com/shortsfan",
                "youtube shorts", "\u200Eyoutube.com", "\u200E", "", null}) {
            check(!ShortVideoSurfaces.shortVideoLink(link), "other link left alone: " + link);
        }
        for (String feed : new String[] {"video.like", "com.kwai.video", "com.kwai.bulldog", "com.smile.gifmaker",
                "in.mohalla.video", "com.eterno.shortvideos", "io.chingari.app", "co.triller.droid"}) {
            check(ShortVideoSurfaces.wholeApp(feed) && ShortVideoSurfaces.watches(feed)
                    && SetupPlan.hasFeed(feed), "other all-feed apps close as a whole: " + feed);
        }
        java.util.List<String> revanced = ShortVideoSurfaces.surfaceViewIds("app.revanced.android.youtube");
        check(revanced.contains("app.revanced.android.youtube:id/reel_recycler")
                && revanced.contains("com.google.android.youtube:id/reel_recycler")
                && revanced.size() == 2 * ShortVideoSurfaces.surfaceViewIds("com.google.android.youtube").size(),
                "a patched YouTube is searched under its own and YouTube's resource names");
        check(!ShortVideoSurfaces.wholeApp("com.vanced.android.youtube") && ShortVideoSurfaces.watches("com.vanced.android.youtube")
                && SetupPlan.hasFeed("app.rvx.android.youtube"), "patched YouTube keeps the rest of the app");
        for (String id : ShortVideoSurfaces.surfaceViewIds("com.google.android.youtube")) {
            check(id.startsWith("com.google.android.youtube:id/"), "YouTube itself is searched only under its own name");
        }
        for (String browser : new String[] {"com.microsoft.emmx", "org.mozilla.firefox", "com.sec.android.app.sbrowser",
                "com.opera.browser", "com.vivaldi.browser", "com.kiwibrowser.browser", "com.duckduckgo.mobile.android"}) {
            String bar = ShortVideoSurfaces.urlBarViewId(browser);
            check(bar != null && bar.startsWith(browser + ":id/") && ShortVideoSurfaces.watches(browser)
                    && !SetupPlan.hasFeed(browser) && !ShortVideoSurfaces.wholeApp(browser),
                    "browser read by its own address bar, never closed as a feed: " + browser);
        }
        String fb = "com.facebook.katana";
        check(ShortVideoSurfaces.watches(fb) && ShortVideoSurfaces.labelsPlayer(fb) && !ShortVideoSurfaces.wholeApp(fb)
                && !SetupPlan.hasFeed(fb), "Facebook is watched for Reels by label, and setup still treats it as an app");
        check(ShortVideoSurfaces.isLabelledPlayer(fb, "Reel", 1080, 2054, 1080), "Reels tab player (read on a phone)");
        check(ShortVideoSurfaces.isLabelledPlayer(fb, "Reel by \u0627\u062D\u0645\u062F", 1080, 1920, 1080),
                "reel opened from the feed (read on a phone)");
        check(!ShortVideoSurfaces.isLabelledPlayer(fb, "Reel", 1080, 1619, 1080), "Home feed reel card (read on a phone)");
        check(!ShortVideoSurfaces.isLabelledPlayer(fb, "Reel", 1080, 1149, 1080), "a card cut off by the screen edge");
        check(!ShortVideoSurfaces.isLabelledPlayer(fb, "Reel", 900, 2054, 1080), "a view narrower than the window");
        for (String label : new String[] {"Reels", "Reel by ", "Create reel", "View someone's reels", "reel", "", null}) {
            check(!ShortVideoSurfaces.isLabelledPlayer(fb, label, 1080, 2054, 1080), "other labels: " + label);
        }
        check(!ShortVideoSurfaces.isLabelledPlayer("com.instagram.android", "Reel", 1080, 2054, 1080)
                && !ShortVideoSurfaces.isLabelledPlayer(fb, "Reel", 1080, 2054, 0),
                "only apps listed by label, and only with a known window");
        try { ShortVideoSurfaces.WHOLE_APPS.add("x.y"); throw new AssertionError("Whole apps are fixed"); }
        catch (UnsupportedOperationException expected) { }
        System.out.println("ShortVideoSurfaces tests passed (whole apps, qualified player IDs, unwatched apps)");
    }
}
