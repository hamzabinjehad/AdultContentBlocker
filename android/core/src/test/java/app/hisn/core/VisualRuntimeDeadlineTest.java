package app.hisn.core;

public final class VisualRuntimeDeadlineTest {
    public static void main(String[] args) {
        VisualRuntimeDeadline runtime = new VisualRuntimeDeadline();
        long load = runtime.begin(100, 60000);
        check(!runtime.expire(load, 60099), "Load timed out early");
        check(runtime.complete(load, 60100) == VisualRuntimeDeadline.Reply.TIMED_OUT,
                "Late model load accepted before its delayed timer ran");
        check(!runtime.expire(load, 60100), "Timeout reported twice");
        check(runtime.complete(load, 60101) == VisualRuntimeDeadline.Reply.STALE,
                "Timed-out load answered twice");
        unavailable(runtime);

        runtime = new VisualRuntimeDeadline();
        load = runtime.begin(100, 60000);
        check(runtime.complete(load, 110) == VisualRuntimeDeadline.Reply.ACCEPTED, "Model load rejected");
        long frame = runtime.begin(120, 5000);
        check(runtime.complete(load, 130) == VisualRuntimeDeadline.Reply.STALE, "Old load completed a frame");
        check(!runtime.expire(load, 99999), "Old timer expired the new frame");
        check(runtime.expire(frame, 5120), "Hung frame did not expire");
        check(runtime.complete(frame, 5121) == VisualRuntimeDeadline.Reply.STALE,
                "Late inference accepted after timeout recovery");
        unavailable(runtime);

        runtime = new VisualRuntimeDeadline();
        frame = runtime.begin(100, 5000);
        check(runtime.complete(frame, 5099) == VisualRuntimeDeadline.Reply.ACCEPTED,
                "Timely frame rejected");
        long next = runtime.begin(5100, 5000);
        check(next != frame, "Frame token reused");
        check(runtime.complete(frame, 5101) == VisualRuntimeDeadline.Reply.STALE,
                "Previous frame answered a later request");
        runtime.close();
        check(runtime.complete(next, 5102) == VisualRuntimeDeadline.Reply.STALE,
                "Closed runtime accepted a frame");
        check(!runtime.expire(next, 11000), "Closed runtime fired a timeout");
        unavailable(runtime);
        System.out.println("VisualRuntimeDeadline tests passed: bounded loads/frames, delayed timers, stale callbacks and no reuse after timeout");
    }

    private static void unavailable(VisualRuntimeDeadline runtime) {
        try {
            runtime.begin(20000, 5000);
            throw new AssertionError("Failed or closed runtime reused");
        } catch (IllegalStateException expected) { }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
