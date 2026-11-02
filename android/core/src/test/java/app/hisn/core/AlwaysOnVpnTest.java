package app.hisn.core;

/** Always-on DNS VPN decisions, testable without Android. */
public final class AlwaysOnVpnTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static void main(String[] args) {
        check(!AlwaysOnVpn.shouldApply(false, true, true, 0, true), "ordinary install applies always-on");
        check(!AlwaysOnVpn.shouldApply(true, false, true, 0, true), "lockdown off applies always-on");
        check(!AlwaysOnVpn.shouldApply(true, true, false, 0, true), "idle owner applies always-on");
        check(!AlwaysOnVpn.shouldApply(true, true, true, 0, false), "DNS off applies always-on");
        check(AlwaysOnVpn.shouldApply(true, true, true, 0, true), "keep switch did not apply always-on");
        check(!AlwaysOnVpn.shouldApply(true, true, true, 0, true, false),
                "missing VPN approval applied always-on");
        check(AlwaysOnVpn.shouldApply(true, true, false, 1, true), "commitment did not apply always-on");
        check(!AlwaysOnVpn.shouldApply(true, true, false, -1, true), "negative remaining applied always-on");

        check(AlwaysOnVpn.isOurs("app.hisn.android", "app.hisn.android"), "own package not recognised");
        check(!AlwaysOnVpn.isOurs("com.other.vpn", "app.hisn.android"), "another package recognised as ours");
        check(!AlwaysOnVpn.isOurs(null, "app.hisn.android"), "null package recognised as ours");

        check(AlwaysOnVpn.matches(true, "app.hisn.android", "app.hisn.android"), "desired own package did not match");
        check(!AlwaysOnVpn.matches(true, null, "app.hisn.android"), "desired null package matched");
        check(!AlwaysOnVpn.matches(true, "com.other.vpn", "app.hisn.android"), "desired other package matched");
        check(AlwaysOnVpn.matches(false, null, "app.hisn.android"), "no always-on did not match off");
        check(AlwaysOnVpn.matches(false, "com.other.vpn", "app.hisn.android"), "another app's always-on was claimed");
        check(!AlwaysOnVpn.matches(false, "app.hisn.android", "app.hisn.android"), "our stale always-on matched off");

        System.out.println("AlwaysOnVpn tests passed: opt-in matrix, own-package detection and stale-policy match");
    }
}
