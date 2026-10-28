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
        check(!ShortVideoSurfaces.watches("com.android.chrome") && !ShortVideoSurfaces.watches(null)
                && ShortVideoSurfaces.surfaceViewIds("com.android.chrome").isEmpty() && ShortVideoSurfaces.surfaceViewIds(null).isEmpty(),
                "other apps are not watched");
        try { ShortVideoSurfaces.WHOLE_APPS.add("x.y"); throw new AssertionError("Whole apps are fixed"); }
        catch (UnsupportedOperationException expected) { }
        System.out.println("ShortVideoSurfaces tests passed (whole apps, qualified player IDs, unwatched apps)");
    }
}
