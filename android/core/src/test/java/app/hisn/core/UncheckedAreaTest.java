package app.hisn.core;

import java.util.Arrays;
import java.util.List;

public final class UncheckedAreaTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static boolean is(List<int[]> areas, int... expected) {
        return areas.size() == 1 && Arrays.equals(areas.get(0), expected);
    }

    public static void main(String[] args) {
        UncheckedArea area = new UncheckedArea();
        area.moved(0, -50);
        check(area.isEmpty(), "without a window nothing is tracked");

        area.all(0, 100, 1080, 2300);
        check(is(area.areas(), 0, 100, 1080, 2300), "a new screen is all unchecked");

        area.checked(0, 100, 1080, 2300, 0, 0);
        check(area.isEmpty(), "a check with no scroll since leaves nothing unchecked");

        area.checked(0, 100, 1080, 2300, 0, -120);
        check(is(area.areas(), 0, 2180, 1080, 2300), "content that went up since the capture uncovered the bottom strip");

        area.moved(0, -80);
        List<int[]> moving = area.areas();
        check(moving.size() == 2 && Arrays.equals(moving.get(0), new int[]{0, 2100, 1080, 2300}),
                "the old strip moves up and joins the new one");
        check(Arrays.equals(moving.get(1), new int[]{0, 2060, 1080, 2300}),
                "while moving, a lead band three steps deep waits at the bottom edge");
        area.settled();
        check(is(area.areas(), 0, 2100, 1080, 2300), "when the page settles the lead band goes, the strip stays");
        area.moved(0, -2000);
        int[] deepest = area.areas().get(area.areas().size() - 1);
        check(deepest[3] - deepest[1] == 880, "the lead band is never more than 40% of the window");
        area.settled();
        area.checked(0, 100, 1080, 2300, 0, -120);

        area.moved(0, 300);
        area.settled();
        check(is(area.areas(), 0, 100, 1080, 400), "scrolling back moves the strip off the bottom and uncovers the top");

        area.checked(0, 100, 1080, 2300, 0, 0);
        area.moved(0, 5000);
        area.settled();
        check(is(area.areas(), 0, 100, 1080, 2300), "a scroll longer than the window leaves all of it unchecked");

        area.checked(0, 0, 1000, 1000, -40, 0);
        check(is(area.areas(), 960, 0, 1000, 1000), "a sideways scroll uncovers the side");

        area.checked(0, 0, 1000, 1000, 0, 0);
        for (int i = 0; i < 4; i++) { area.moved(0, -10); area.moved(-10, 0); }
        check(area.areas().size() <= UncheckedArea.MAX_AREAS, "areas stay few");

        area.all(0, 0, 10, 10);
        area.areas().get(0)[0] = 9;
        boolean refused = false;
        try { area.areas().clear(); } catch (UnsupportedOperationException expected) { refused = true; }
        check(refused && area.areas().get(0)[0] == 0, "areas() is a read-only copy: changing it cannot change the state");
        area.clear();
        check(area.isEmpty(), "clear forgets everything");
        System.out.println("UncheckedAreaTest passed");
    }
}
