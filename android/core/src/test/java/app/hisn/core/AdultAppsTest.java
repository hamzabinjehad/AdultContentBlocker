package app.hisn.core;

public final class AdultAppsTest {
    private static void check(boolean value, String label) { if (!value) throw new AssertionError("Adult apps: " + label); }
    public static void main(String[] args) {
        check(AdultApps.blocks(true, "com.hdvideo.player.offline"), "the XPlayer clone with an adult feed is blocked");
        check(AdultApps.blocks(true, "iplayer.link.mdisk.terabox.com"), "the MDisk/TeraBox player family is blocked");
        check(!AdultApps.blocks(false, "com.hdvideo.player.offline"), "nothing is blocked while the adult layer is off");
        for (String general : new String[] {"video.player.videoplayer", "com.mxtech.videoplayer.ad", "org.videolan.vlc",
                "com.google.android.youtube", "com.android.chrome"}) {
            check(!AdultApps.blocks(true, general), "general-purpose app stays usable: " + general);
        }
        check(!AdultApps.blocks(true, null) && !AdultApps.blocks(true, ""), "no package is not an app");
        try { AdultApps.PACKAGES.add("x.y"); throw new AssertionError("The list is fixed"); }
        catch (UnsupportedOperationException expected) { }
        System.out.println("AdultApps tests passed (listed apps, layer switch, general players untouched)");
    }
}
