package app.hisn.core;

/** Bounds execution fallback without replaying a failed frame or accepting an old runtime's reply. */
public final class VisualExecutionPolicy {
    private final boolean automatic;
    private String backend;
    private int generation = 1;
    private boolean closed;
    private boolean skipGpu;

    public VisualExecutionPolicy(String requested, boolean previousGpuFailure) {
        if (!"auto".equals(requested) && !"cpu".equals(requested) && !"webgl".equals(requested)
                && !"wasm".equals(requested))
            throw new IllegalArgumentException("Unsupported visual backend");
        automatic = "auto".equals(requested);
        // A WebGL failure is remembered for the process so the next runtime
        // starts on the fast WASM path instead of failing on graphics again.
        backend = automatic ? previousGpuFailure ? "wasm" : "webgl" : requested;
    }
    public String backend() { return backend; }
    public int generation() { return generation; }
    public boolean accepts(int token) { return !closed && generation == token; }
    public boolean skipGpuAfterFailure() { return skipGpu; }

    /** Initialization may walk WebGL, WASM, CPU once. A failed frame is never replayed. */
    public boolean failed(boolean loading) {
        if (closed || !automatic) return false;
        if ("webgl".equals(backend)) skipGpu = true;
        if (!loading) return false;
        if ("webgl".equals(backend)) { backend = "wasm"; generation++; return true; }
        if ("wasm".equals(backend)) { backend = "cpu"; generation++; return true; }
        return false;
    }
    public void close() { closed = true; generation++; }
}
