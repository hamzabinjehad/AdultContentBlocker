package app.hisn.core;

public final class VisualDutyTest {
    private static void expect(VisualDuty.Action expected, VisualDuty duty, boolean interactive,
            boolean selected, boolean loaded, long now) {
        VisualDuty.Action actual = duty.next(interactive, selected, loaded, now);
        if (actual != expected) throw new AssertionError(interactive + "/" + selected + "/" + loaded + "@" + now
                + ": expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        long idle = VisualDuty.IDLE_RELEASE_MILLIS;
        VisualDuty duty = new VisualDuty();
        // Screen off: never sample, and free a loaded model.
        expect(VisualDuty.Action.PAUSE, duty, false, true, false, 0);
        expect(VisualDuty.Action.RELEASE, duty, false, true, true, 1);
        // Screen on and enabled: warm the model before any selected app comes forward.
        expect(VisualDuty.Action.PRELOAD, duty, true, false, false, 1_000);
        expect(VisualDuty.Action.PRELOAD, duty, true, true, false, 1_000);
        // Loaded and idle: keep it warm for a while, then release it.
        expect(VisualDuty.Action.PAUSE, duty, true, false, true, 2_000);
        expect(VisualDuty.Action.PAUSE, duty, true, false, true, 1_000 + idle - 1);
        expect(VisualDuty.Action.RELEASE, duty, true, false, true, 1_000 + idle);
        // Released: the next screen-on poll preloads again, and a selected app samples at once.
        expect(VisualDuty.Action.PRELOAD, duty, true, false, false, 1_000 + 10 * idle);
        expect(VisualDuty.Action.SAMPLE, duty, true, true, true, 1_000 + 10 * idle);
        // Each sample restarts the idle period; deep sleep counts toward it.
        expect(VisualDuty.Action.SAMPLE, duty, true, true, true, 20 * idle);
        expect(VisualDuty.Action.PAUSE, duty, true, false, true, 21 * idle - 1);
        expect(VisualDuty.Action.RELEASE, duty, true, false, true, 21 * idle);
        // A loaded model that was never sampled is kept only for the same idle period.
        VisualDuty fresh = new VisualDuty();
        expect(VisualDuty.Action.PAUSE, fresh, true, false, true, 0);
        expect(VisualDuty.Action.RELEASE, fresh, true, false, true, idle);
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
                    if (action == VisualDuty.Action.RELEASE && interactive && !selected) {
                        throw new AssertionError("Released a model that was just used");
                    }
                }
        if (samples != 1 || preloads != 2) throw new AssertionError("Sampling or preloading unreachable");
        System.out.println("VisualDuty tests passed (preloaded on screen on; no sampling with the screen off; "
                + "model released after " + idle / 60_000 + " idle minutes or when the screen goes off)");
    }
}
