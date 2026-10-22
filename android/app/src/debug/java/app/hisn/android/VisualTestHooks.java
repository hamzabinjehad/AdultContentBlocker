package app.hisn.android;

/** Fixed one-shot synthetic intervention in the isolated fixture only; never an accuracy test. */
final class VisualTestHooks {
    private static long deadline;
    static boolean arm(String owner, long now) {
        if (!"app.hisn.android.visualcheck".equals(owner) || now < 0) return false;
        deadline = now + 120000;
        return true;
    }
    static void clear() { deadline = 0; }
    static String verdict(String owner, String target, String actual, long now) {
        if (!"app.hisn.android.visualcheck".equals(owner) || !"app.hisn.android.visualcheck.test".equals(target)
                || deadline == 0 || now < deadline - 120000 || now >= deadline || !"allow".equals(actual)) return actual;
        clear();
        return "block";
    }
}
