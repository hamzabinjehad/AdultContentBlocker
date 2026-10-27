package app.hisn.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class GuardWindowsTest {
    static GuardWindows.Window app(int id, String app) { return new GuardWindows.Window(id, app, true, false, false, false); }
    static GuardWindows.Window active(int id, String app) { return new GuardWindows.Window(id, app, true, true, true, false); }
    static GuardWindows.Window focused(int id, String app) { return new GuardWindows.Window(id, app, true, false, true, false); }
    static GuardWindows.Window pip(int id, String app) { return new GuardWindows.Window(id, app, true, false, false, true); }
    static GuardWindows.Window system(int id, String app) { return new GuardWindows.Window(id, app, false, false, false, false); }

    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    static final Set<String> EXCLUDED = new HashSet<>(Arrays.asList("app.hisn.android", "com.android.settings",
            "com.android.systemui", "launcher"));
    static final Set<String> SELECTED = new HashSet<>(Arrays.asList("video", "feed", "chat", "com.android.settings"));

    static String ids(List<GuardWindows.Window> windows) {
        StringBuilder out = new StringBuilder();
        for (GuardWindows.Window window : windows) out.append(out.length() == 0 ? "" : " ").append(window);
        return out.toString();
    }

    static String select(GuardWindows.Window... windows) {
        return ids(GuardWindows.select(Arrays.asList(windows), SELECTED::contains, EXCLUDED));
    }

    public static void main(String[] args) {
        // Single full-screen app: as before.
        check(select(active(1, "feed")).equals("1:feed"), "the active selected window");
        check(select(active(1, "other")).isEmpty(), "an unselected app is never inspected");
        // Split screen: the selected half is inspected while another app is active.
        check(select(active(1, "other"), app(2, "feed")).equals("2:feed"), "inactive split-screen half");
        check(select(app(2, "feed"), active(1, "video")).equals("1:video 2:feed"), "active first");
        check(select(app(2, "feed"), focused(3, "chat"), active(1, "video")).equals("1:video 3:chat 2:feed"),
                "then the focused window, then top to bottom");
        // Picture-in-picture and pop-up windows over an unselected or excluded app.
        check(select(active(1, "launcher"), pip(9, "video")).equals("9:video"), "PiP over the home screen");
        check(select(active(1, "com.android.settings"), pip(9, "video")).equals("9:video"),
                "settings is never read, even when selected, but a PiP beside it is");
        check(select(active(1, "app.hisn.android"), app(4, "feed")).equals("4:feed"), "Hisn's own window is skipped");
        // Non-application windows never qualify, whatever their package.
        check(select(system(5, "feed"), system(6, "com.android.systemui"), active(1, "other")).isEmpty(),
                "system, keyboard, overlay and divider windows are skipped");
        check(select(app(7, null), app(8, ""), app(-1, "feed")).isEmpty(), "unknown app or id");
        // Two windows of one app are both inspected; a repeated id only once; the list is bounded.
        check(select(app(2, "feed"), pip(3, "feed"), app(2, "feed")).equals("2:feed 3:feed"), "duplicates dropped");
        check(select(app(1, "feed"), app(2, "feed"), app(3, "feed"), app(4, "feed"), active(5, "feed"))
                .equals("5:feed 1:feed 2:feed 3:feed"), "at most " + GuardWindows.MAX_WINDOWS + ", active kept");
        check(select().isEmpty() && GuardWindows.select(Arrays.asList(null, app(2, "feed")), SELECTED::contains, EXCLUDED)
                .size() == 1, "empty and null entries");

        // Turns: every selected window is sampled in turn, starting again after a missing one.
        List<GuardWindows.Window> three = GuardWindows.select(Arrays.asList(active(1, "video"), app(2, "feed"),
                pip(3, "chat")), SELECTED::contains, EXCLUDED);
        check(GuardWindows.after(three, -1).id == 1 && GuardWindows.after(three, 1).id == 2
                && GuardWindows.after(three, 2).id == 3 && GuardWindows.after(three, 3).id == 1
                && GuardWindows.after(three, 42).id == 1, "round robin");
        check(GuardWindows.after(new ArrayList<>(), 1) == null, "nothing to sample");
        check(GuardWindows.find(three, 3, "chat") != null && GuardWindows.find(three, 3, "feed") == null
                && GuardWindows.find(three, 4, "chat") == null, "a decision applies only to the same window and app");

        // Node budget shares: never more than what is left in total.
        check(GuardWindows.share(512, 1) == 512 && GuardWindows.share(512, 2) == 256 && GuardWindows.share(500, 0) == 500
                && GuardWindows.share(-1, 2) == 0, "shares");

        // Random lists: the rule holds for every result.
        Random random = new Random(55);
        String[] apps = {"video", "feed", "chat", "other", "launcher", "com.android.settings", "app.hisn.android", null};
        for (int round = 0; round < 5000; round++) {
            List<GuardWindows.Window> windows = new ArrayList<>();
            int count = random.nextInt(9);
            for (int i = 0; i < count; i++) {
                windows.add(new GuardWindows.Window(random.nextInt(6) - 1, apps[random.nextInt(apps.length)],
                        random.nextInt(4) != 0, random.nextInt(4) == 0, random.nextInt(3) == 0, random.nextInt(4) == 0));
            }
            List<GuardWindows.Window> chosen = GuardWindows.select(windows, SELECTED::contains, EXCLUDED);
            check(chosen.size() <= GuardWindows.MAX_WINDOWS, "bounded");
            Set<Integer> seen = new HashSet<>();
            boolean pastActive = false;
            int budget = LabelScan.NODE_BUDGET, used = 0;
            for (int i = 0; i < chosen.size(); i++) {
                GuardWindows.Window window = chosen.get(i);
                check(window.application && SELECTED.contains(window.app) && !EXCLUDED.contains(window.app)
                        && window.id >= 0 && seen.add(window.id) && windows.contains(window), "only eligible windows");
                if (!window.active) pastActive = true;
                else check(!pastActive, "active windows first");
                int share = GuardWindows.share(budget - used, chosen.size() - i);
                used += random.nextInt(share + 1);
            }
            check(used <= budget, "shares stay within one budget");
            boolean anyEligible = false;
            for (GuardWindows.Window window : windows) {
                anyEligible |= window != null && window.application && window.app != null && window.id >= 0
                        && SELECTED.contains(window.app) && !EXCLUDED.contains(window.app);
            }
            check(anyEligible == !chosen.isEmpty(), "an eligible window is never dropped from a short list");
        }
        System.out.println("GuardWindows tests passed (split screen, pop-up and PiP windows of selected apps;"
                + " system surfaces and excluded apps never; turns and node shares; 5,000 random lists)");
    }
}
