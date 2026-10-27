package app.hisn.core;

import java.util.Random;

public final class InterventionRepeatTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static void main(String[] args) {
        InterventionRepeat repeat = new InterventionRepeat();
        check(repeat.wait("popup", 5, false, 0) == 0, "nothing acted on yet");
        repeat.acted("popup", 5, 1_000);
        // A pop-up window Home left on screen: windows-changed churn does not loop Home and the notice.
        check(repeat.wait("popup", 5, false, 1_500) == InterventionRepeat.HOLD_OFF_MILLIS - 500, "held off");
        check(repeat.wait("popup", 5, false, 1_000 + InterventionRepeat.HOLD_OFF_MILLIS) == 0, "then acted on again");
        // The user going back to it, a reopened app, another window or another app: act at once.
        check(repeat.wait("popup", 5, true, 1_500) == 0, "the window is active again");
        check(repeat.wait("popup", 6, false, 1_500) == 0, "another window of the app");
        check(repeat.wait("other", 5, false, 1_500) == 0, "another app");
        check(repeat.wait(null, 5, false, 1_500) == 0 && repeat.wait("popup", -1, false, 1_500) == 0, "unknown");
        check(repeat.wait("popup", 5, false, 500) == 0, "a clock before the last act");

        // Random sequences: a hold-off never exceeds the period and never applies to an active window.
        Random random = new Random(11);
        for (int round = 0; round < 5000; round++) {
            InterventionRepeat r = new InterventionRepeat();
            long t = 0, last = -1;
            String lastApp = null;
            int lastWindow = -1;
            for (int step = 0; step < 30; step++) {
                t += random.nextInt(4000);
                String app = random.nextBoolean() ? "a" : "b";
                int window = random.nextInt(3);
                boolean active = random.nextInt(3) == 0;
                long wait = r.wait(app, window, active, t);
                check(wait >= 0 && wait <= InterventionRepeat.HOLD_OFF_MILLIS, "bounded");
                check(wait == 0 || (!active && app.equals(lastApp) && window == lastWindow
                        && t - last < InterventionRepeat.HOLD_OFF_MILLIS), "only the same inactive window, briefly");
                if (wait == 0) { r.acted(app, window, t); last = t; lastApp = app; lastWindow = window; }
            }
        }
        System.out.println("InterventionRepeat tests passed (a window Home leaves on screen is acted on at most every "
                + InterventionRepeat.HOLD_OFF_MILLIS / 1000 + " s unless it is active again; 5,000 random sequences)");
    }
}
