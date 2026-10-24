package app.hisn.core;

public final class VisualExecutionPolicyTest {
    public static void main(String[] args) {
        VisualExecutionPolicy automatic = new VisualExecutionPolicy("auto", false);
        int gpuToken = automatic.generation();
        check("webgl".equals(automatic.backend()), "GPU not attempted");
        check(automatic.failed(true), "Initialization did not retry CPU");
        check("cpu".equals(automatic.backend()) && automatic.preferCpuAfterFailure(), "CPU recovery not selected");
        check(!automatic.accepts(gpuToken) && automatic.accepts(automatic.generation()), "Stale GPU reply accepted");
        check(!automatic.failed(true), "CPU retried indefinitely");
        VisualExecutionPolicy runtime = new VisualExecutionPolicy("auto", false);
        check(!runtime.failed(false) && runtime.preferCpuAfterFailure(), "Failed frame replayed or future CPU not preferred");
        check("cpu".equals(new VisualExecutionPolicy("auto", true).backend()), "GPU failure forgotten within process");
        for (String backend : new String[]{"webgl", "cpu"}) {
            VisualExecutionPolicy explicit = new VisualExecutionPolicy(backend, true);
            check(backend.equals(explicit.backend()) && !explicit.failed(true) && !explicit.preferCpuAfterFailure(),
                    "Explicit comparison changed default policy");
        }
        int current = automatic.generation();
        automatic.close();
        check(!automatic.accepts(current) && !automatic.failed(true), "Closed runtime accepted callbacks or restarted");
        try { new VisualExecutionPolicy("remote", false); throw new AssertionError("Invalid backend accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("VisualExecutionPolicy tests passed: bounded recovery, no frame replay, stale replies and explicit trial isolation");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
