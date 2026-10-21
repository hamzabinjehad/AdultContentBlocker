package app.hisn.core;

public final class GuardHealthTest {
    private static void check(GuardHealth expected, Boolean enabled, boolean connected,
            boolean configured, boolean readable, boolean partial) {
        if (GuardHealth.evaluate(enabled, connected, configured, readable, partial) != expected)
            throw new AssertionError("Incorrect guard health: " + expected);
    }
    public static void main(String[] args) {
        check(GuardHealth.DISABLED, false, false, false, true, false);
        check(GuardHealth.PERMISSION_REQUIRED, false, true, true, true, false);
        check(GuardHealth.RECONNECT_REQUIRED, true, false, true, true, false);
        check(GuardHealth.READY_NO_RULES, true, true, false, true, true);
        check(GuardHealth.ACTIVE, true, true, true, true, false);
        check(GuardHealth.LIMITED, true, true, true, true, true);
        check(GuardHealth.SETTINGS_UNAVAILABLE, true, true, true, false, false);
        check(GuardHealth.PERMISSION_UNKNOWN, null, true, true, true, false);
        for (Boolean enabled : new Boolean[]{null, false, true})
            for (boolean connected : new boolean[]{false, true})
                for (boolean configured : new boolean[]{false, true})
                    for (boolean readable : new boolean[]{false, true})
                        for (boolean partial : new boolean[]{false, true}) {
                            GuardHealth state = GuardHealth.evaluate(enabled, connected, configured, readable, partial);
                            if (state == GuardHealth.ACTIVE && !(Boolean.TRUE.equals(enabled)
                                    && connected && configured && readable && !partial))
                                throw new AssertionError("False active state");
                        }
        System.out.println("GuardHealth tests passed (48 permission, binding, configuration and inspection states)");
    }
}
