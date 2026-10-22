package app.hisn.core;

public final class VisualFrameGateTest {
    public static void main(String[] args) {
        VisualFrameGate gate = new VisualFrameGate();
        VisualFrameGate.Frame frame = gate.begin("selected.app", 7, 100);
        check(gate.accepts(frame, "selected.app", 7, true, 100), "current frame");
        check(gate.accepts(frame, "selected.app", 7, true, 8100), "deadline inclusive");
        check(!gate.accepts(frame, "selected.app", 7, true, 8101), "expired result");
        check(!gate.accepts(frame, "selected.app", 7, true, 99), "backwards clock");
        check(!gate.accepts(frame, "other.app", 7, true, 200), "different app");
        check(!gate.accepts(frame, "selected.app", 8, true, 200), "different window");
        check(!gate.accepts(frame, "selected.app", 7, false, 200), "opt-out or removed app");
        gate.invalidate();
        check(!gate.accepts(frame, "selected.app", 7, true, 200), "return to same app after navigation");
        VisualFrameGate.Frame next = gate.begin("selected.app", 7, 300);
        check(gate.accepts(next, "selected.app", 7, true, 301), "new session");
        check(!gate.accepts(null, "selected.app", 7, true, 301), "missing request");
        System.out.println("VisualFrameGateTest passed: stale, unselected, changed-window and expired verdicts rejected");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
