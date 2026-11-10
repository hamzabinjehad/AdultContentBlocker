package app.hisn.core;

/** Fences native-runtime replies to one operation and a monotonic deadline.
 * A timed-out runtime is never reused: its worker may still be inside native code. */
public final class VisualRuntimeDeadline {
    public enum Reply { ACCEPTED, TIMED_OUT, STALE }
    private long sequence, pending, deadline;
    private boolean failed, closed;

    public synchronized long begin(long now, long timeout) {
        if (closed || failed || pending != 0) throw new IllegalStateException("Runtime unavailable");
        if (now < 0 || timeout <= 0 || now > Long.MAX_VALUE - timeout)
            throw new IllegalArgumentException("Invalid deadline");
        pending = ++sequence;
        deadline = now + timeout;
        return pending;
    }

    /** A late result is a timeout even when the main thread delivered it before its timer. */
    public synchronized Reply complete(long token, long now) {
        if (token == 0 || token != pending || closed) return Reply.STALE;
        pending = 0;
        if (now < 0 || now >= deadline) {
            failed = true;
            return Reply.TIMED_OUT;
        }
        return Reply.ACCEPTED;
    }

    /** True exactly once when the current operation reaches its deadline. */
    public synchronized boolean expire(long token, long now) {
        if (token == 0 || token != pending || closed || now < deadline) return false;
        pending = 0;
        failed = true;
        return true;
    }

    public synchronized void close() {
        closed = true;
        pending = 0;
    }
}
