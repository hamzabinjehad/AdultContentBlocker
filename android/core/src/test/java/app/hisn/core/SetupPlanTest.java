package app.hisn.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SetupPlanTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError("Setup plan: " + label);
    }

    public static void main(String[] args) {
        String tiktok = "com.zhiliaoapp.musically", youtube = "com.google.android.youtube",
                instagram = "com.instagram.android", reddit = "com.reddit.frontpage", x = "com.twitter.android";
        check(SetupPlan.suggested(tiktok) == SetupPlan.Strength.CLOSE, "TikTok is suggested closed");
        check(SetupPlan.suggested(youtube) == SetupPlan.Strength.CLOSE, "YouTube's feed is suggested closed");
        check(SetupPlan.suggested(reddit) == SetupPlan.Strength.WAIT, "an app without a feed starts with a wait");
        check(SetupPlan.closesFeedOnly(youtube), "YouTube keeps working without Shorts");
        check(!SetupPlan.closesFeedOnly(tiktok), "TikTok is all feed");
        check(!SetupPlan.hasFeed("com.android.chrome"), "a browser is not a feed app");
        check(SetupPlan.suggested("com.android.chrome") == SetupPlan.Strength.WAIT, "a browser is never pre-closed");
        check(SetupPlan.hasFeed("com.snapchat.android"), "Snapchat Spotlight is a feed");

        Map<String, SetupPlan.Strength> apps = new LinkedHashMap<>();
        apps.put(tiktok, SetupPlan.Strength.CLOSE);
        apps.put(instagram, SetupPlan.Strength.LIMIT);
        apps.put(youtube, SetupPlan.Strength.WAIT);
        apps.put(x, SetupPlan.Strength.CLOSE);
        SetupPlan plan = new SetupPlan(apps, true, 22 * 60, 7 * 60);
        check(plan.waitApps().size() == 1 && plan.waitApps().contains(youtube), "wait apps");
        check(plan.limits().size() == 1 && plan.limits().get(instagram) == SetupPlan.LIMIT_MINUTES, "limits");
        check(plan.shortVideos(), "closing TikTok needs the short-video layer");
        check(plan.blockedApps().size() == 1 && plan.blockedApps().contains(x), "an app with no feed is blocked whole");
        check(!plan.blockedApps().contains(tiktok), "TikTok goes through the feed layer, not the app block");
        check(plan.quietHours(), "quiet hours chosen");
        DailyAppSchedule schedule = plan.schedule();
        check(schedule.enabled && schedule.startMinute == 22 * 60 && schedule.endMinute == 7 * 60, "quiet window");
        check(schedule.apps.size() == 4, "quiet hours close every chosen app");

        SetupPlan none = new SetupPlan(new LinkedHashMap<>(), false, -1, -1);
        check(!none.shortVideos() && none.waitApps().isEmpty() && !none.quietHours(), "empty plan changes nothing");
        check(!none.schedule().enabled, "no apps, no schedule");
        check(!new SetupPlan(apps, false, 600, 600).quietHours(), "an empty window is no quiet hours");
        check(!new SetupPlan(apps, false, 2000, 60).quietHours(), "an impossible minute is no quiet hours");
        Map<String, SetupPlan.Strength> limitOnly = new LinkedHashMap<>();
        limitOnly.put(reddit, SetupPlan.Strength.LIMIT);
        check(!new SetupPlan(limitOnly, false, -1, -1).shortVideos(), "a limit never turns on the feed layer");
        System.out.println("SetupPlan passed");
    }
}
