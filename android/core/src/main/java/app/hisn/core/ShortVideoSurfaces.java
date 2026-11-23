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
    /**
     * Apps that are a short-video feed and nothing else: TikTok, its regional builds, TikTok Lite
     * and Douyin, and the other vertical-feed apps that took its place where it is banned or
     * small (Likee, Kwai and Snack Video, Kuaishou, Moj, Josh, Chingari, Triller). A package
     * name that is not installed matches nothing, so a name here can only miss, never block
     * the wrong app.
     */
    public static final Set<String> WHOLE_APPS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go",
            "com.ss.android.ugc.aweme",
            "video.like", "com.kwai.video", "com.kwai.bulldog", "com.smile.gifmaker", "in.mohalla.video",
            "com.eterno.shortvideos", "io.chingari.app", "co.triller.droid")));

    /**
     * Patched YouTube builds (ReVanced, ReVanced Extended, Vanced) install under their own
     * package name to sit beside YouTube, which would otherwise make them a way round the
     * Shorts block. They rename the package, not the resource table, so their Shorts player
     * still reports YouTube's view IDs; both spellings are searched.
     */
    private static final Map<String, String> RESOURCE_TWINS;
    static {
        Map<String, String> twins = new LinkedHashMap<>();
        for (String patched : new String[] {"app.revanced.android.youtube", "app.rvx.android.youtube",
                "com.vanced.android.youtube"}) {
            twins.put(patched, "com.google.android.youtube");
        }
        RESOURCE_TWINS = Collections.unmodifiableMap(twins);
    }

    private static final Map<String, List<String>> SURFACE_IDS;
    static {
        Map<String, List<String>> ids = new LinkedHashMap<>();
        // The Shorts player: its vertical pager, each page and the fragment holding them. Read again
        // from YouTube 21.45 on a Galaxy S21 FE (23 November 2026): all four are on screen in Shorts,
        // and none on Home or its feed, which carry only reel_time_bar.
        ids.put("com.google.android.youtube", Arrays.asList("reel_recycler", "reel_player_page_container",
                "reel_watch_player", "reel_watch_fragment_root"));
        // The Reels viewer, from the Reels tab, Explore or a shared reel.
        ids.put("com.instagram.android", Arrays.asList("clips_viewer_view_pager", "clips_viewer_container",
                "clips_video_container"));
        // The Spotlight feed. Read from Snapchat 14.32 on a Galaxy S21 FE (16 November 2026): the
        // container is on screen in Spotlight and absent from the camera and Memories pages; checked
        // again on 23 November 2026: absent from Stories, Chat and Map too, and Back leaves Spotlight.
        // opera_viewer is not listed: it is Snapchat's general media viewer, stories included.
        ids.put("com.snapchat.android", Arrays.asList("spotlight_container"));
        SURFACE_IDS = Collections.unmodifiableMap(ids);
    }

    /**
     * Apps that name none of their views, so their player is known by its label and shape.
     * Facebook reports every view ID as "(name removed)". Read from Facebook 588 on a Galaxy S21
     * FE with Facebook in English (23 November 2026): the Reels tab's player is a button labelled
     * "Reel" at 1080x2054, a reel opened from the feed is "Reel by <name>" at 1080x1920, and a
     * reel card in the Home feed is also "Reel" but 1080x1619. Only the player is at least
     * {@link #PLAYER_MIN_ASPECT} times as tall as it is wide, so the card never counts.
     * Other languages label it differently and are missed until read from a device.
     */
    private static final Map<String, List<String>> PLAYER_LABELS;
    static {
        Map<String, List<String>> labels = new LinkedHashMap<>();
        labels.put("com.facebook.katana", Arrays.asList("Reel", "Reel by "));
        PLAYER_LABELS = Collections.unmodifiableMap(labels);
    }
    /** Player 1.78 to 1.90 on a 20:9 phone; feed card 1.50 on any screen. */
    public static final double PLAYER_MIN_ASPECT = 1.65;

    /**
     * Browsers whose address bar Hisn reads, by its view ID. Read from Chrome 157 on a Galaxy
     * S21 FE (17 November 2026); Chrome Beta, Dev and Canary are the same app. Brave was read
     * the same way; the other browsers below still need reading from a device.
     */
    private static final Map<String, String> URL_BARS;
    static {
        Map<String, String> bars = new LinkedHashMap<>();
        for (String chrome : new String[] {"com.android.chrome", "com.chrome.beta", "com.chrome.dev",
                "com.chrome.canary"}) {
            bars.put(chrome, chrome + ":id/url_bar");
        }
        // Brave's address bar, read on the same Galaxy S21 FE (18 November 2026). Brave's private
        // tab has no marker of its own, so Brave is not a browser Hisn controls.
        bars.put("com.brave.browser", "com.brave.browser:id/url_bar");
        // Samsung Internet's bar, read from Samsung Internet 31 on the same Galaxy S21 FE (23 November
        // 2026). It shows the host only ("tiktok.com", not "youtube.com/shorts/..."), so there it
        // catches TikTok but not Shorts, Reels or Spotlight pages.
        bars.put("com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser:id/location_bar_edit_text");
        // Not yet read from a device here: Edge, Vivaldi and Kiwi keep Chromium's url_bar as Chrome
        // and Brave do, and the others are the IDs accessibility blockers commonly read. A stale ID
        // finds no node, so it is a missed address, never a wrong block: the address itself must
        // still match a short-video link.
        for (String chromium : new String[] {"com.microsoft.emmx", "com.vivaldi.browser", "com.kiwibrowser.browser"}) {
            bars.put(chromium, chromium + ":id/url_bar");
        }
        bars.put("org.mozilla.firefox", "org.mozilla.firefox:id/mozac_browser_toolbar_url_view");
        bars.put("com.opera.browser", "com.opera.browser:id/url_field");
        bars.put("com.duckduckgo.mobile.android", "com.duckduckgo.mobile.android:id/omnibarTextInput");
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
        // Samsung Internet puts an invisible left-to-right mark before the host (read from
        // Samsung Internet 31 on a Galaxy S21 FE, 23 November 2026); format characters are not text.
        String text = address.toString().replaceAll("\\p{Cf}", "").trim().toLowerCase(java.util.Locale.ROOT);
        text = text.replaceFirst("^[a-z][a-z0-9+.-]*://", "");
        return !text.isEmpty() && text.indexOf(' ') < 0 && SHORT_VIDEO_LINK.matcher(text).matches();
    }

    /** True when the short-video block has anything to look at in this app. */
    public static boolean watches(String packageName) {
        return packageName != null && (WHOLE_APPS.contains(packageName) || SURFACE_IDS.containsKey(packageName)
                || RESOURCE_TWINS.containsKey(packageName) || URL_BARS.containsKey(packageName)
                || PLAYER_LABELS.containsKey(packageName));
    }

    /** True when this app's player is found by label rather than view ID. */
    public static boolean labelsPlayer(String packageName) {
        return packageName != null && PLAYER_LABELS.containsKey(packageName);
    }

    /**
     * True when a view is this app's full-screen short-video player: its label is the player's
     * ("Reel", or "Reel by " followed by a name), it spans the window's width, and it is tall
     * like a phone screen, not like a card in a feed.
     */
    public static boolean isLabelledPlayer(String packageName, CharSequence label, int width, int height,
            int windowWidth) {
        List<String> labels = packageName == null ? null : PLAYER_LABELS.get(packageName);
        if (labels == null || label == null || width <= 0 || height <= 0 || windowWidth <= 0) return false;
        if (width * 100L < windowWidth * 95L) return false;
        if (height < width * PLAYER_MIN_ASPECT) return false;
        String text = label.toString().replaceAll("\\p{Cf}", "").trim();
        for (String name : labels) {
            if (name.endsWith(" ") ? text.startsWith(name) && text.length() > name.length() : text.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static boolean wholeApp(String packageName) {
        return packageName != null && WHOLE_APPS.contains(packageName);
    }

    /**
     * Fully qualified view IDs ("package:id/name") that mark a short-video player in this app.
     * A patched build of a listed app gets the original's IDs under both package names.
     */
    public static List<String> surfaceViewIds(String packageName) {
        if (packageName == null) return Collections.emptyList();
        String original = RESOURCE_TWINS.get(packageName);
        List<String> names = SURFACE_IDS.get(original != null ? original : packageName);
        if (names == null) return Collections.emptyList();
        List<String> qualified = new java.util.ArrayList<>();
        for (String name : names) qualified.add(packageName + ":id/" + name);
        if (original != null) for (String name : names) qualified.add(original + ":id/" + name);
        return Collections.unmodifiableList(qualified);
    }
}
