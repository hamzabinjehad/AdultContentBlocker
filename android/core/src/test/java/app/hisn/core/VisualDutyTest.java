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
        // Screen off: never sample, even in a selected app with the model ready.
        expect(VisualDuty.Action.PAUSE, duty, false, true, false, 0);
        expect(VisualDuty.Action.SAMPLE, duty, true, true, false, 1_000);
        expect(VisualDuty.Action.PAUSE, duty, false, true, true, 2_000);
        expect(VisualDuty.Action.PAUSE, duty, true, false, true, 1_000 + idle - 1);
        expect(VisualDuty.Action.RELEASE, duty, false, true, true, 1_000 + idle);
        // Released: nothing loaded to free, and returning to a selected app samples again.
        expect(VisualDuty.Action.PAUSE, duty, true, false, false, 1_000 + 10 * idle);
        expect(VisualDuty.Action.SAMPLE, duty, true, true, false, 1_000 + 10 * idle);
        // Each sample restarts the idle period; deep sleep counts toward it.
        expect(VisualDuty.Action.SAMPLE, duty, true, true, true, 20 * idle);
        expect(VisualDuty.Action.PAUSE, duty, false, false, true, 21 * idle - 1);
        expect(VisualDuty.Action.RELEASE, duty, false, false, true, 21 * idle);
        // A model that was never used for a sample is not kept.
        expect(VisualDuty.Action.RELEASE, new VisualDuty(), true, false, true, 0);
        int samples = 0;
        for (boolean interactive : new boolean[]{false, true})
            for (boolean selected : new boolean[]{false, true})
                for (boolean loaded : new boolean[]{false, true})
                    if (new VisualDuty().next(interactive, selected, loaded, 0) == VisualDuty.Action.SAMPLE) {
                        if (!interactive || !selected) throw new AssertionError("Sampled with the screen off or outside a selected app");
                        samples++;
                    }
        if (samples != 2) throw new AssertionError("Sampling unreachable");
        System.out.println("VisualDuty tests passed (no sampling with the screen off; model released after "
                + idle / 60_000 + " idle minutes)");
    }
}
