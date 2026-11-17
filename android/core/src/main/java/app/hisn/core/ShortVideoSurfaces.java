package app.hisn.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where short-video feeds live in Android apps, for the opt-in short-video block.
 *
 * Whole apps are short video and nothing else. Inside YouTube, Instagram and Snapchat only the
 * Shorts, Reels and Spotlight players are, so those are recognised by their view IDs, and the
 * rest of each app stays usable. View IDs belong to each app and change between versions: a renamed ID is a
 * missed screen, never a wrong block, so update this list from a device when one drifts.
 * The extension's matching rules for the web are in extension/rules/short_video.json.
 */
public final class ShortVideoSurfaces {
    /** TikTok, its regional builds and TikTok Lite. */
    public static final Set<String> WHOLE_APPS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go",
            "com.ss.android.ugc.aweme")));

    private static final Map<String, List<String>> SURFACE_IDS;
    static {
        Map<String, List<String>> ids = new LinkedHashMap<>();
        // The Shorts player: its vertical pager and each page.
        ids.put("com.google.android.youtube", Arrays.asList("reel_recycler", "reel_player_page_container",
                "reel_watch_player"));
        // The Reels viewer, from the Reels tab, Explore or a shared reel.
        ids.put("com.instagram.android", Arrays.asList("clips_viewer_view_pager", "clips_viewer_container",
                "clips_video_container"));
        // The Spotlight feed. Read from Snapchat 14.32 on a Galaxy S21 FE (16 November 2026): the
        // container is on screen in Spotlight and absent from the camera and Memories pages.
        // opera_viewer is not listed: it is Snapchat's general media viewer, stories included.
        ids.put("com.snapchat.android", Arrays.asList("spotlight_container"));
        SURFACE_IDS = Collections.unmodifiableMap(ids);
    }

    /**
     * Browsers whose address bar Hisn reads, by its view ID. Read from Chrome 157 on a Galaxy
     * S21 FE (17 November 2026); Chrome Beta, Dev and Canary are the same app. Other browsers
     * are not listed until their address bar ID is read from a device.
     */
    private static final Map<String, String> URL_BARS;
    static {
        Map<String, String> bars = new LinkedHashMap<>();
        for (String chrome : new String[] {"com.android.chrome", "com.chrome.beta", "com.chrome.dev",
                "com.chrome.canary"}) {
            bars.put(chrome, chrome + ":id/url_bar");
        }
        URL_BARS = Collections.unmodifiableMap(bars);
    }

    /** The same feeds on the web, as extension/rules/short_video.json blocks them. */
    private static final java.util.regex.Pattern SHORT_VIDEO_LINK = java.util.regex.Pattern.compile(
            "^(?:[a-z0-9-]+\\.)*(?:youtube\\.com/shorts|instagram\\.com/(?:reels?|explore)"
                    + "|facebook\\.com/(?:reels?|watch/reels?|share/reel)|snapchat\\.com/spotlight)(?:[/?#].*)?$"
                    + "|^(?:[a-z0-9-]+\\.)*tiktok\\.com(?:[/?#].*)?$");

    private ShortVideoSurfaces() { }

    /** Fully qualified view ID of this browser's address bar, or null when it is not read. */
    public static String urlBarViewId(String packageName) {
        return packageName == null ? null : URL_BARS.get(packageName);
    }

    /**
     * True when an address bar shows a short-video feed. Browsers show the address without its
     * scheme and sometimes with spaces trimmed oddly, so the scheme is optional and case ignored.
     */
    public static boolean shortVideoLink(CharSequence address) {
        if (address == null) return false;
        String text = address.toString().trim().toLowerCase(java.util.Locale.ROOT);
        text = text.replaceFirst("^[a-z][a-z0-9+.-]*://", "");
        return !text.isEmpty() && text.indexOf(' ') < 0 && SHORT_VIDEO_LINK.matcher(text).matches();
    }

    /** True when the short-video block has anything to look at in this app. */
    public static boolean watches(String packageName) {
        return packageName != null && (WHOLE_APPS.contains(packageName) || SURFACE_IDS.containsKey(packageName)
                || URL_BARS.containsKey(packageName));
    }

    public static boolean wholeApp(String packageName) {
        return packageName != null && WHOLE_APPS.contains(packageName);
    }

    /** Fully qualified view IDs ("package:id/name") that mark a short-video player in this app. */
    public static List<String> surfaceViewIds(String packageName) {
        List<String> names = packageName == null ? null : SURFACE_IDS.get(packageName);
        if (names == null) return Collections.emptyList();
        String[] qualified = new String[names.size()];
        for (int i = 0; i < qualified.length; i++) qualified[i] = packageName + ":id/" + names.get(i);
        return Collections.unmodifiableList(Arrays.asList(qualified));
    }
}
