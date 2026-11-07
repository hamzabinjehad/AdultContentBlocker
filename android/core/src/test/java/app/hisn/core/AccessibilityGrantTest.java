package app.hisn.core;

public final class AccessibilityGrantTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String pkg = "app.hisn.android", cls = "app.hisn.android.TextGuardService";
        check(AccessibilityGrant.holds(pkg + "/" + cls, pkg, cls), "fully qualified entry");
        check(AccessibilityGrant.holds(pkg + "/.TextGuardService", pkg, cls), "relative entry");
        check(AccessibilityGrant.holds("a/b:" + pkg + "/" + cls + ":c/d", pkg, cls), "entry inside a list");
        check(!AccessibilityGrant.holds("a/b", pkg, cls), "short list");
        check(!AccessibilityGrant.holds(null, pkg, cls), "null list");
        check(!AccessibilityGrant.holds("", pkg, cls), "empty list");
        check(!AccessibilityGrant.holds(pkg, pkg, cls), "entry without a class");
        check(!AccessibilityGrant.holds(pkg + "/", pkg, cls), "entry with an empty class");
        check(!AccessibilityGrant.holds("/" + cls, pkg, cls), "entry with an empty package");
        check(!AccessibilityGrant.holds(pkg + "/.Other", pkg, cls), "other class in the same package");
        check(!AccessibilityGrant.holds("app.hisn.android.visualcheck/" + cls, pkg, cls), "the isolated test package");
        check(!AccessibilityGrant.holds(pkg + "x/" + cls, pkg, cls), "a package that only starts with ours");
        check(!AccessibilityGrant.holds("com.screenzen/" + cls, pkg, cls), "another app");
        check(AccessibilityGrant.holds(" " + pkg + "/" + cls + " ", pkg, cls), "surrounding spaces");
        check(AccessibilityGrant.holds(pkg + "/" + cls + ":", pkg, cls), "trailing separator");
        check(!AccessibilityGrant.holds(pkg + "/" + cls, "", cls), "empty package argument");
        check(!AccessibilityGrant.holds(pkg + "/" + cls, pkg, ""), "empty class argument");
        check(!AccessibilityGrant.holds(pkg + "/" + cls, null, cls), "null package argument");
        System.out.println("AccessibilityGrant tests passed (formats, separators, near-miss packages)");
    }
}
