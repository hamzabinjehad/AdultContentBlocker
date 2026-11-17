package app.hisn.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Apps Hisn blocks on its own while the built-in adult layer is on, with no setup.
 *
 * <p>Only apps whose purpose is adult content are listed, never a general tool that someone may
 * also misuse: MX Player, VLC and the original XPlayer stay usable. Each entry needs evidence,
 * recorded in docs/ADULT_APPS_RESEARCH.md: the iPlayer family names MDisk and TeraBox, the hosts
 * of the adult link pipeline, in its own package ids; com.hdvideo.player.offline ("XPlayer")
 * shows a short-video feed of adult clips (reported by an owner on a Galaxy S21 FE,
 * 17 November 2026). The app is blocked like one chosen under Apps to block; the owner's own
 * list is unchanged.</p>
 */
public final class AdultApps {
    public static final Set<String> PACKAGES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "com.lite.tera.iplayerbox", "iplayer.link.mdisk.terabox.com", "iplayer.video.mdisk.terabox.com",
            "iplayer.and.new.com", "com.hdvideo.player.offline")));

    private AdultApps() { }

    /** True when the built-in adult layer is on and this app is on the list. */
    public static boolean blocks(boolean adultLayerOn, String packageName) {
        return adultLayerOn && packageName != null && PACKAGES.contains(packageName);
    }
}
