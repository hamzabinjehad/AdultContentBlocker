package app.hisn.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Which on-screen windows the accessibility guard may inspect. Android-free so the rule is tested.
 *
 * Only application windows (full screen, split-screen halves, pop-up and picture-in-picture
 * windows) of selected apps qualify. System, input-method, accessibility-overlay and divider
 * windows, excluded apps (settings, launchers, dialers, installers, Hisn itself) and windows
 * whose app is unknown never do. The active window comes first, then the focused one, then the
 * rest in Android's top-to-bottom order; a repeated window id is dropped and at most
 * {@link #MAX_WINDOWS} are kept, so one inspection stays within one node budget.
 */
public final class GuardWindows {
    public static final int MAX_WINDOWS = 4;

    public static final class Window {
        public final int id;
        public final String app;
        public final boolean application, active, focused, pip;
        public Window(int id, String app, boolean application, boolean active, boolean focused, boolean pip) {
            this.id = id; this.app = app; this.application = application;
            this.active = active; this.focused = focused; this.pip = pip;
        }
        @Override public String toString() { return id + ":" + app; }
    }

    public static List<Window> select(List<Window> windows, Predicate<String> selected, Set<String> excluded) {
        List<Window> eligible = new ArrayList<>();
        for (int rank = 0; rank < 3; rank++) {
            for (Window window : windows) {
                if (window == null || rank != (window.active ? 0 : window.focused ? 1 : 2)) continue;
                if (!window.application || window.app == null || window.app.isEmpty() || window.id < 0) continue;
                if (excluded.contains(window.app) || !selected.test(window.app)) continue;
                eligible.add(window);
            }
        }
        List<Window> result = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Window window : eligible) {
            if (result.size() < MAX_WINDOWS && seen.add(window.id)) result.add(window);
        }
        return result;
    }

    /** The window to sample after window id {@code last}, in selection order, so that several selected
     * windows take turns; the first when {@code last} is not among them; null when there is none. */
    public static Window after(List<Window> windows, int last) {
        for (int i = 0; i < windows.size(); i++) {
            if (windows.get(i).id == last) return windows.get((i + 1) % windows.size());
        }
        return windows.isEmpty() ? null : windows.get(0);
    }

    /** The window with this id and app, or null: a frame or block decided for it may still apply. */
    public static Window find(List<Window> windows, int id, String app) {
        for (Window window : windows) if (window.id == id && window.app.equals(app)) return window;
        return null;
    }

    /** Node budget for the next of {@code windows} windows when {@code left} nodes remain: an even
     * share, so a small window leaves the rest to later ones, and the last takes all that remains. */
    public static int share(int left, int windows) {
        return Math.max(0, windows <= 1 ? left : left / windows);
    }

    private GuardWindows() { }
}
