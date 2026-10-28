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
        expect(VisualCadence.BASE_MILLIS, cadence.interval(true, now), "a new window in the rotation");
        cadence.finished(8, 43);
        cadence.finished(8, 43);
        expect(VisualCadence.BASE_MILLIS + VisualCadence.STILL_STEP_MILLIS, cadence.interval(true, now),
                "the least still window sets the back-off");
        cadence.activity(now);
        expect(VisualCadence.FAST_MILLIS, cadence.interval(true, now), "an event");
        expect(VisualCadence.BASE_MILLIS, cadence.interval(true, now + 5_000), "and no back-off is left over");

        // Two unchanged windows taking turns (paused PiP over a still feed, an idle split screen) back off.
        VisualCadence turns = new VisualCadence();
        for (int i = 0; i < 10; i++) { turns.finished(1, 100); turns.finished(2, 200); }
        expect(VisualCadence.STILL_MAX_MILLIS, turns.interval(true, now), "alternating unchanged windows back off");
        turns.finished(1, 101);
        expect(VisualCadence.BASE_MILLIS, turns.interval(true, now), "one changed window ends it");
        turns.finished(1, 101);
        turns.finished(2, 200);
        expect(VisualCadence.BASE_MILLIS + VisualCadence.STILL_STEP_MILLIS, turns.interval(true, now),
                "and it resumes from the least still window");
        turns.finished(3, 300);
        expect(VisualCadence.BASE_MILLIS, turns.interval(true, now), "a window joining the rotation");
        turns.retain(java.util.Arrays.asList(1, 2));
        expect(VisualCadence.BASE_MILLIS + VisualCadence.STILL_STEP_MILLIS, turns.interval(true, now),
                "a closed window no longer holds the back-off");
        turns.retain(java.util.Collections.emptyList());
        expect(VisualCadence.BASE_MILLIS, turns.interval(true, now), "no windows, the default");
        for (int i = 0; i < 20; i++) turns.finished(10 + i, 0);
        check(turns.interval(true, now) == VisualCadence.BASE_MILLIS, "per-window memory stays bounded");

        // Polls are bounded too: events every 160 ms while no sample can start (typing, a retry wait)
        // bring polls forward no more than once a second, as VisualGuard.soon() schedules them.
        VisualCadence storm = new VisualCadence();
        long next = 0, polls = 0;
        for (long t = 0; t <= 10_000; t += 10) {
            if (t >= next) { polls++; storm.polled(t); next = t + storm.interval(true, t); }
            if (t % 160 == 0) {
                storm.activity(t);
                long wait = storm.earliest(true, t);
                if (t + wait < next) next = t + wait;
            }
        }
        check(polls <= 11, "at most one poll a second during an event storm, got " + polls);
        VisualCadence quiet = new VisualCadence();
        quiet.polled(1_000);
        expect(VisualCadence.FAST_MILLIS - 100, quiet.earliest(true, 1_100), "a second after the last poll");
        expect(VisualCadence.MIN_WAIT_MILLIS, quiet.earliest(true, 2_500), "then soon");

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
            long t = random.nextInt(10_000), start = -1, poll = -1;
            for (int step = 0; step < 50; step++) {
                t += random.nextInt(3000);
                switch (random.nextInt(5)) {
                    case 0: c.activity(t); break;
                    case 1: c.polled(t); c.started(t); start = poll = t; break;
                    case 2: c.finished(random.nextInt(3), random.nextInt(3)); break;
                    case 3: c.polled(t); poll = t; break;
                    default: break;
                }
                boolean on = random.nextInt(5) != 0;
                long interval = c.interval(on, t), wait = c.earliest(on, t);
                check(on ? interval >= VisualCadence.FAST_MILLIS && interval <= VisualCadence.STILL_MAX_MILLIS
                        : interval == VisualCadence.OFF_MILLIS, "interval bounds");
                check(wait >= VisualCadence.MIN_WAIT_MILLIS && wait <= interval, "earliest bounds");
                if (on && start >= 0) check(t + wait >= start + VisualCadence.FAST_MILLIS,
                        "never sooner than a second after the last start");
                if (on && poll >= 0) check(t + wait >= poll + VisualCadence.FAST_MILLIS,
                        "never sooner than a second after the last poll, sampled or not");
            }
        }
        System.out.println("VisualCadence tests passed (1 s while scrolling, 2 s by default, up to "
                + VisualCadence.STILL_MAX_MILLIS / 1000 + " s on a still screen, " + VisualCadence.OFF_MILLIS / 1000
                + " s with the screen off; 2,000 random sequences)");
    }
}
