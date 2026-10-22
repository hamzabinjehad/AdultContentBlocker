package app.hisn.android;

public final class ReleaseVisualTestHooksTest {
    public static void main(String[] args) {
        for (String owner : new String[]{"app.hisn.android", "app.hisn.android.visualcheck", null}) {
            if (VisualTestHooks.arm(owner, 100)) throw new AssertionError("Release can arm synthetic verdicts");
            for (String verdict : new String[]{"allow", "block", "unknown", null}) {
                if (VisualTestHooks.verdict(owner, "app.hisn.android.visualcheck.test", verdict, 101) != verdict)
                    throw new AssertionError("Release changed classifier verdict");
            }
            VisualTestHooks.clear();
        }
        System.out.println("Release visual hook tests passed: cannot arm or alter a real verdict");
    }
}
