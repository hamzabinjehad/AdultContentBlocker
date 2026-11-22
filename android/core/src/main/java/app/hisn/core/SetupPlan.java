package app.hisn.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What the general edition's setup turns into real rules. The person points at apps and picks
 * one of three strengths for each; this class decides which existing rule each choice becomes,
 * so the screens never invent a rule the engine does not have.
 *
 * <ul>
 *   <li>{@link Strength#WAIT}: the app opens after a short breath (Open Wait), growing with each
 *       open that day.</li>
 *   <li>{@link Strength#LIMIT}: a daily limit, after which the app closes until tomorrow.</li>
 *   <li>{@link Strength#CLOSE}: for an app with a short-video feed the engine can see (YouTube
 *       Shorts, Instagram Reels, Snapchat Spotlight) or an app that is only a feed (TikTok),
 *       the short-video layer closes the feed and the rest of the app keeps working. Any other
 *       app has no feed to close, so it is blocked as a whole.</li>
 * </ul>
 */
public final class SetupPlan {
    public enum Strength { WAIT, LIMIT, CLOSE }

    /** The wait, in seconds, and the daily limit, in minutes, that setup starts people on. */
    public static final int WAIT_SECONDS = 10;
    public static final int LIMIT_MINUTES = 45;

    public final Map<String, Strength> apps;
    public final boolean adult;
    /** Quiet hours as minutes after midnight, or -1 when the person chose none. */
    public final int quietStart;
    public final int quietEnd;

    public SetupPlan(Map<String, Strength> apps, boolean adult, int quietStart, int quietEnd) {
        this.apps = Collections.unmodifiableMap(new LinkedHashMap<>(apps));
        this.adult = adult;
        boolean quiet = quietStart >= 0 && quietEnd >= 0 && quietStart < 1440 && quietEnd < 1440
                && quietStart != quietEnd;
        this.quietStart = quiet ? quietStart : -1;
        this.quietEnd = quiet ? quietEnd : -1;
    }

    /** True when "close" can close only the feed of this app rather than the whole app. */
    public static boolean closesFeedOnly(String packageName) {
        return !ShortVideoSurfaces.surfaceViewIds(packageName).isEmpty()
                && !ShortVideoSurfaces.wholeApp(packageName);
    }

    /** True when "close" is meaningful as a feed choice: the app is, or has, a short-video feed. */
    public static boolean hasFeed(String packageName) {
        // Browsers are watched too (for short-video links in the address bar), but a browser
        // is not a feed: only apps with a known feed screen, or apps that are all feed, count.
        return !ShortVideoSurfaces.surfaceViewIds(packageName).isEmpty()
                || ShortVideoSurfaces.wholeApp(packageName);
    }

    /** The strength setup suggests before the person changes anything. */
    public static Strength suggested(String packageName) {
        return hasFeed(packageName) ? Strength.CLOSE : Strength.WAIT;
    }

    public Set<String> waitApps() { return with(Strength.WAIT); }

    public Map<String, Integer> limits() {
        Map<String, Integer> limits = new LinkedHashMap<>();
        for (String app : with(Strength.LIMIT)) limits.put(app, LIMIT_MINUTES);
        return limits;
    }

    /** Apps closed as a whole: "close" on an app with no feed the engine can close. */
    public Set<String> blockedApps() {
        Set<String> blocked = new LinkedHashSet<>();
        for (String app : with(Strength.CLOSE)) if (!hasFeed(app)) blocked.add(app);
        return blocked;
    }

    /** The short-video layer is needed when any chosen app has its feed closed. */
    public boolean shortVideos() {
        for (String app : with(Strength.CLOSE)) if (hasFeed(app)) return true;
        return false;
    }

    public boolean quietHours() { return quietStart >= 0; }

    /** Quiet hours close every chosen app, whatever its strength: that is what "quiet" means. */
    public DailyAppSchedule schedule() {
        if (!quietHours() || apps.isEmpty()) return DailyAppSchedule.disabled();
        return new DailyAppSchedule(true, quietStart, quietEnd, new LinkedHashSet<>(apps.keySet()));
    }

    private Set<String> with(Strength strength) {
        Set<String> chosen = new LinkedHashSet<>();
        for (Map.Entry<String, Strength> entry : apps.entrySet()) {
            if (entry.getValue() == strength) chosen.add(entry.getKey());
        }
        return chosen;
    }
}
