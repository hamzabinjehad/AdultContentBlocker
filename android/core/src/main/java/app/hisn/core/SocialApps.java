package app.hisn.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Named social platforms for the app chooser's "Social apps" preset.
 *
 * <p>The same rule as {@link DistractionSites}: named apps, not a category. A
 * category would hide what is actually selected, and this preset only edits the
 * ordinary chosen-apps list — it adds no enforcement layer and stores nothing
 * of its own. The app then applies whatever the person already chose for those
 * apps: the whole-app block, or text/visual inspection.</p>
 *
 * <p>Android's inspection unit is the app window, not a post: the local model
 * sees one screenshot and a positive adult verdict leaves the app (see
 * {@code VisualGuard}). That is deliberately stronger than hiding one post, so
 * this preset is how a person puts that existing guard on the social apps they
 * use, rather than a second, weaker enforcement path.</p>
 */
public final class SocialApps {
    public static final class Entry {
        public final String label;
        public final String packageName;

        Entry(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }

    private static final List<Entry> COMMON = Collections.unmodifiableList(Arrays.asList(
            new Entry("X", "com.twitter.android"),
            new Entry("Instagram", "com.instagram.android"),
            new Entry("Facebook", "com.facebook.katana"),
            new Entry("Reddit", "com.reddit.frontpage"),
            new Entry("TikTok", "com.zhiliaoapp.musically"),
            new Entry("TikTok", "com.ss.android.ugc.trill"),
            new Entry("Threads", "com.instagram.barcelona"),
            new Entry("Bluesky", "xyz.blueskyweb.app"),
            new Entry("LinkedIn", "com.linkedin.android"),
            new Entry("Pinterest", "com.pinterest"),
            new Entry("Tumblr", "com.tumblr"),
            new Entry("Snapchat", "com.snapchat.android")));

    private SocialApps() { }

    /** The named platforms in a stable order; duplicate package names are a bug. */
    public static List<Entry> common() {
        return COMMON;
    }

    /** Installed entries, in {@link #common()} order. */
    public static List<Entry> installed(Set<String> installedPackages) {
        List<Entry> found = new ArrayList<>();
        if (installedPackages == null) return found;
        for (Entry entry : COMMON) {
            if (installedPackages.contains(entry.packageName)) found.add(entry);
        }
        return found;
    }

    /**
     * Adds the installed social apps that are not already chosen, never more
     * than {@code max}. Returns how many entries were added; an app that is
     * already chosen is left alone, so tapping twice cannot duplicate or
     * unselect anything.
     */
    public static int select(Set<String> installedPackages, Set<String> chosen, int max) {
        if (installedPackages == null || chosen == null || max <= 0) return 0;
        int added = 0;
        for (Entry entry : COMMON) {
            if (chosen.size() >= max) break;
            if (installedPackages.contains(entry.packageName) && !chosen.contains(entry.packageName)) {
                chosen.add(entry.packageName);
                added++;
            }
        }
        return added;
    }
}
