package app.hisn.core;

/** Fences asynchronous pixel decisions to the selected foreground window and a short lifetime. */
public final class VisualFrameGate {
    private long generation;
    public static final class Frame {
        final String app;
        final int window;
        final long started;
        final long generation;
        Frame(String app, int window, long started, long generation) {
            this.app = app; this.window = window; this.started = started; this.generation = generation;
        }
    }
    public Frame begin(String app, int window, long now) {
        if (app == null || app.isEmpty() || window < 0 || now < 0) throw new IllegalArgumentException("Invalid frame");
        return new Frame(app, window, now, generation);
    }
    public void invalidate() { generation++; }
    public boolean accepts(Frame frame, String app, int window, boolean selected, long now) {
        return frame != null && selected && frame.generation == generation && frame.app.equals(app)
                && frame.window == window && now >= frame.started && now - frame.started <= 8000;
    }
}
