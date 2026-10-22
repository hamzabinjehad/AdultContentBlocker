package app.hisn.android;

/** Release has no synthetic visual intervention, including in a differently named package. */
final class VisualTestHooks {
    static boolean arm(String owner, long now) { return false; }
    static void clear() { }
    static String verdict(String owner, String target, String actual, long now) { return actual; }
}
