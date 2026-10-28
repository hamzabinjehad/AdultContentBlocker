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
 * Whole apps are short video and nothing else. Inside YouTube and Instagram only the Shorts
 * and Reels players are, so those are recognised by their view IDs, and the rest of each app
 * stays usable. View IDs belong to each app and change between versions: a renamed ID is a
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
        SURFACE_IDS = Collections.unmodifiableMap(ids);
    }

    private ShortVideoSurfaces() { }

    /** True when the short-video block has anything to look at in this app. */
    public static boolean watches(String packageName) {
        return packageName != null && (WHOLE_APPS.contains(packageName) || SURFACE_IDS.containsKey(packageName));
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
