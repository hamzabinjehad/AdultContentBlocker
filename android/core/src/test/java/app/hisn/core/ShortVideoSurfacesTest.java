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
                "snapchat.com/spotlight/abc", "tiktok.com", "www.tiktok.com/@someone/video/1", "vm.tiktok.com/ZM123/"}) {
            check(ShortVideoSurfaces.shortVideoLink(link), "short-video link matched: " + link);
        }
        for (String link : new String[] {"youtube.com/watch?v=abc", "youtube.com", "youtube.com/@channel/shorts",
                "instagram.com/someone", "facebook.com/someone/posts/1", "snapchat.com", "notiktok.com",
                "tiktok.com.evil.example", "google.com/search?q=youtube.com/shorts", "youtube.com/shortsfan",
                "youtube shorts", "", null}) {
            check(!ShortVideoSurfaces.shortVideoLink(link), "other link left alone: " + link);
        }
        try { ShortVideoSurfaces.WHOLE_APPS.add("x.y"); throw new AssertionError("Whole apps are fixed"); }
        catch (UnsupportedOperationException expected) { }
        System.out.println("ShortVideoSurfaces tests passed (whole apps, qualified player IDs, unwatched apps)");
    }
}
