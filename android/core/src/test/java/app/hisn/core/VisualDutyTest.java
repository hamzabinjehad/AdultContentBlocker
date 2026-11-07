package app.hisn.core;

public final class VisualDutyTest {
    private static void expect(VisualDuty.Action expected, VisualDuty duty, boolean interactive,
            boolean selected, boolean loaded, long now) {
        VisualDuty.Action actual = duty.next(interactive, selected, loaded, now);
        if (actual != expected) throw new AssertionError(interactive + "/" + selected + "/" + loaded + "@" + now
                + ": expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        long grace = VisualDuty.SCREEN_OFF_GRACE_MILLIS;
        VisualDuty duty = new VisualDuty();
        // Screen off: never sample. A loaded model stays warm through the short grace, then frees.
        expect(VisualDuty.Action.PAUSE, duty, false, true, false, 0);
        expect(VisualDuty.Action.PAUSE, duty, false, true, true, 1);
        expect(VisualDuty.Action.PAUSE, duty, false, true, true, grace - 1);
        expect(VisualDuty.Action.RELEASE, duty, false, true, true, grace);
        // Screen on and enabled: warm the model before any selected app comes forward.
        expect(VisualDuty.Action.PRELOAD, duty, true, false, false, 1_000);
        expect(VisualDuty.Action.PRELOAD, duty, true, true, false, 1_000);
        // A loaded model is kept while the screen stays on; a reload would blind the next app.
        expect(VisualDuty.Action.PAUSE, duty, true, false, true, 2_000);
        expect(VisualDuty.Action.PAUSE, duty, true, false, true, 10 * 60 * 60_000L);
        // A selected app samples immediately; another selected-window poll keeps sampling.
        expect(VisualDuty.Action.SAMPLE, duty, true, true, true, 2_000);
        expect(VisualDuty.Action.SAMPLE, duty, true, true, true, 20 * 60 * 60_000L);
        // Screen off after a long screen-on: release only after the grace.
        expect(VisualDuty.Action.PAUSE, duty, false, false, true, 20 * 60 * 60_000L + 1);
        expect(VisualDuty.Action.RELEASE, duty, false, false, true, 20 * 60 * 60_000L + grace);
        // Returning to the screen reuses the warm model, or preloads if it was freed.
        expect(VisualDuty.Action.PAUSE, duty, true, false, true, 20 * 60 * 60_000L + grace + 1);
        expect(VisualDuty.Action.PRELOAD, duty, true, false, false, 20 * 60 * 60_000L + grace + 2);
        // No power state is invented: a model that was never loaded is never "released".
        expect(VisualDuty.Action.PAUSE, new VisualDuty(), false, true, false, 0);
        int samples = 0, preloads = 0;
        for (boolean interactive : new boolean[]{false, true})
            for (boolean selected : new boolean[]{false, true})
                for (boolean loaded : new boolean[]{false, true}) {
                    VisualDuty.Action action = new VisualDuty().next(interactive, selected, loaded, 0);
                    if (action == VisualDuty.Action.SAMPLE) {
                        if (!interactive || !selected || !loaded) throw new AssertionError("Sampled without the screen, a selected app or a model");
                        samples++;
                    }
                    if (action == VisualDuty.Action.PRELOAD) {
                        if (!interactive || loaded) throw new AssertionError("Preloaded at the wrong time");
                        preloads++;
                    }
                    if (action == VisualDuty.Action.RELEASE) {
                        if (interactive || !loaded) throw new AssertionError("Released a model with the screen on or none loaded");
                    }
                }
        if (samples != 1 || preloads != 2) throw new AssertionError("Duty states unreachable");
        System.out.println("VisualDuty tests passed (preloaded on screen on; kept while the screen is on; "
                + "freed after a " + grace / 60_000 + "-minute screen-off grace; no sampling with the screen off)");
    }
}
