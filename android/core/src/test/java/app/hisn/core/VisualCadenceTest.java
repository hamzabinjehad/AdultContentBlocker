package app.hisn.core;

import java.util.Random;

public final class VisualCadenceTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    static void expect(long expected, long actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        VisualCadence cadence = new VisualCadence();
        // Default: about every 2 s, as before; nothing sampled yet, so an event may poll soon.
        expect(VisualCadence.BASE_MILLIS, cadence.interval(true, 0), "default interval");
        expect(VisualCadence.MIN_WAIT_MILLIS, cadence.earliest(true, 0), "first sample may come soon");
        expect(VisualCadence.OFF_MILLIS, cadence.interval(false, 0), "screen off");
        expect(VisualCadence.OFF_MILLIS, cadence.earliest(false, 0), "screen off never brought forward");

        // Scrolling: every second, never sooner after the last sample started.
        cadence.started(10_000);
        cadence.activity(10_100);
        expect(VisualCadence.FAST_MILLIS, cadence.interval(true, 10_200), "scrolling");
        expect(900, cadence.earliest(true, 10_100), "an event waits for a second after the last start");
        expect(VisualCadence.MIN_WAIT_MILLIS, cadence.earliest(true, 10_950), "short waits are bounded below");
        expect(VisualCadence.FAST_MILLIS, cadence.interval(true, 10_100 + VisualCadence.ACTIVE_FOR_MILLIS),
                "events count for a while");
        expect(VisualCadence.BASE_MILLIS, cadence.interval(true, 10_101 + VisualCadence.ACTIVE_FOR_MILLIS),
                "then the default returns");
        expect(VisualCadence.OFF_MILLIS, cadence.interval(false, 10_200), "screen off beats scrolling");

        // A still screen backs off by a second per unchanged sample, up to the maximum.
        long now = 100_000;
        cadence.finished(7, 42);
        expect(VisualCadence.BASE_MILLIS, cadence.interval(true, now), "first sample of a window is not still");
        long expected = VisualCadence.BASE_MILLIS;
        for (int i = 0; i < 10; i++) {
            cadence.finished(7, 42);
            expected = Math.min(VisualCadence.STILL_MAX_MILLIS, expected + VisualCadence.STILL_STEP_MILLIS);
            expect(expected, cadence.interval(true, now), "still sample " + i);
        }
        expect(VisualCadence.STILL_MAX_MILLIS, cadence.interval(true, now), "bounded back-off");
        // A changed frame (a video with no events), another window or an event ends the back-off.
        cadence.finished(7, 43);
        expect(VisualCadence.BASE_MILLIS, cadence.interval(true, now), "a changed frame");
        cadence.finished(7, 43);
        cadence.finished(8, 43);
        expect(VisualCadence.BASE_MILLIS, cadence.interval(true, now), "another window");
        cadence.finished(8, 43);
        cadence.finished(8, 43);
        cadence.activity(now);
        expect(VisualCadence.FAST_MILLIS, cadence.interval(true, now), "an event");
        expect(VisualCadence.BASE_MILLIS, cadence.interval(true, now + 5_000), "and no back-off is left over");

        // Signatures: the same screen, or one differing only in low colour bits, gives the same value.
        int[] frame = new int[256], noisy = new int[256], moved = new int[256];
        Random random = new Random(7);
        for (int i = 0; i < frame.length; i++) {
            frame[i] = random.nextInt() | 0xFF000000;
            noisy[i] = (frame[i] & ~0x0F0F0F) | (random.nextInt() & 0x0F0F0F);
            moved[i] = frame[(i + 1) % frame.length];
        }
        check(VisualCadence.signature(frame) == VisualCadence.signature(frame.clone()), "same frame");
        check(VisualCadence.signature(frame) == VisualCadence.signature(noisy), "dithering noise ignored");
        check(VisualCadence.signature(frame) != VisualCadence.signature(moved), "moved content differs");

        // Random sequences: sampling is never faster than once a second after a start, and never
        // slower than the bounded maximum while the screen is on.
        for (int round = 0; round < 2000; round++) {
            VisualCadence c = new VisualCadence();
            long t = random.nextInt(10_000), start = -1;
            for (int step = 0; step < 50; step++) {
                t += random.nextInt(3000);
                switch (random.nextInt(4)) {
                    case 0: c.activity(t); break;
                    case 1: c.started(t); start = t; break;
                    case 2: c.finished(random.nextInt(3), random.nextInt(3)); break;
                    default: break;
                }
                boolean on = random.nextInt(5) != 0;
                long interval = c.interval(on, t), wait = c.earliest(on, t);
                check(on ? interval >= VisualCadence.FAST_MILLIS && interval <= VisualCadence.STILL_MAX_MILLIS
                        : interval == VisualCadence.OFF_MILLIS, "interval bounds");
                check(wait >= VisualCadence.MIN_WAIT_MILLIS && wait <= interval, "earliest bounds");
                if (on && start >= 0) check(t + wait >= start + VisualCadence.FAST_MILLIS,
                        "never sooner than a second after the last start");
            }
        }
        System.out.println("VisualCadence tests passed (1 s while scrolling, 2 s by default, up to "
                + VisualCadence.STILL_MAX_MILLIS / 1000 + " s on a still screen, " + VisualCadence.OFF_MILLIS / 1000
                + " s with the screen off; 2,000 random sequences)");
    }
}
