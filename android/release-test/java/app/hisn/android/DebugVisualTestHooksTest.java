package app.hisn.android;

public final class DebugVisualTestHooksTest {
    public static void main(String[] args) {
        String owner = "app.hisn.android.visualcheck", fixture = owner + ".test";
        check(!VisualTestHooks.arm("app.hisn.android", 100), "Owner app armed");
        check(VisualTestHooks.arm(owner, 100), "Isolated copy could not arm");
        check("allow".equals(VisualTestHooks.verdict(owner, "other.app", "allow", 101)), "Unselected target changed");
        check("unknown".equals(VisualTestHooks.verdict(owner, fixture, "unknown", 101)), "Unknown became a verdict");
        check("allow".equals(VisualTestHooks.verdict(owner, fixture, "allow", 99)), "Backwards time accepted");
        check("block".equals(VisualTestHooks.verdict(owner, fixture, "allow", 101)), "Fixture not overridden");
        check("allow".equals(VisualTestHooks.verdict(owner, fixture, "allow", 102)), "Lease not one shot");
        VisualTestHooks.arm(owner, 100);
        check("allow".equals(VisualTestHooks.verdict(owner, fixture, "allow", 120100)), "Expired lease accepted");
        VisualTestHooks.clear();
        check("allow".equals(VisualTestHooks.verdict(owner, fixture, "allow", 101)), "Cleared lease accepted");
        System.out.println("Debug visual hooks passed: isolated fixture only, one shot, expiry and unknown preservation");
    }
    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
}
