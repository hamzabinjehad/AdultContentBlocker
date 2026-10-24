package app.hisn.core;

/** Bounds execution fallback without replaying a failed frame or accepting an old runtime's reply. */
public final class VisualExecutionPolicy {
    private final boolean automatic;
    private String backend;
    private int generation = 1;
    private boolean closed;
    private boolean preferCpu;

    public VisualExecutionPolicy(String requested, boolean previousGpuFailure) {
        if (!"auto".equals(requested) && !"cpu".equals(requested) && !"webgl".equals(requested))
            throw new IllegalArgumentException("Unsupported visual backend");
        automatic = "auto".equals(requested);
        backend = automatic ? previousGpuFailure ? "cpu" : "webgl" : requested;
    }
    public String backend() { return backend; }
    public int generation() { return generation; }
    public boolean accepts(int token) { return !closed && generation == token; }
    public boolean preferCpuAfterFailure() { return preferCpu; }

    /** Initialization can retry once in a fresh CPU runtime. Failed frames always remain unknown. */
    public boolean failed(boolean loading) {
        if (closed || !automatic || !"webgl".equals(backend)) return false;
        preferCpu = true;
        if (!loading) return false;
        backend = "cpu";
        generation++;
        return true;
    }
    public void close() { closed = true; generation++; }
}
