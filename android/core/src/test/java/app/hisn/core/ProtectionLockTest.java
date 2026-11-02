package app.hisn.core;

public final class ProtectionLockTest {
    public static void main(String[] args) {
        lockedWhileTimeRemains();
        editableOnlyAfterTheEnd();
        guardArmsFromALockOrASwitch();
        bootShieldIgnoresTheInstallOnlySwitch();
        systemOutOfScope();
        System.out.println("ProtectionLockTest passed");
    }

    private static void lockedWhileTimeRemains() {
        check(ProtectionLock.locked(1), "one millisecond is still a lock");
        check(ProtectionLock.locked(90L * 24 * 3600_000L), "the longest period is a lock");
        check(!ProtectionLock.locked(0), "zero is over");
        check(!ProtectionLock.locked(-1), "negative is over");
    }

    private static void editableOnlyAfterTheEnd() {
        check(!ProtectionLock.mayEditRules(1), "no rule edits during a lock");
        check(!ProtectionLock.mayEditRules(90L * 24 * 3600_000L), "the longest lock still refuses");
        check(ProtectionLock.mayEditRules(0), "editable at the end");
        check(ProtectionLock.mayEditRules(-5), "editable after the end");
    }

    private static void guardArmsFromALockOrASwitch() {
        check(ProtectionLock.screenGuardArmed(true, false, false, 1),
                "a direct-build lock arms the guard without a switch");
        check(ProtectionLock.screenGuardArmed(true, true, false, 0),
                "keep installed arms the guard");
        check(ProtectionLock.screenGuardArmed(true, false, true, 0),
                "the install switch arms the guard");
        check(!ProtectionLock.screenGuardArmed(true, false, false, 0),
                "nothing armed is off");
        check(!ProtectionLock.screenGuardArmed(false, true, true, 1),
                "a Play build never compiles the guard in");
    }

    private static void bootShieldIgnoresTheInstallOnlySwitch() {
        check(ProtectionLock.bootShieldArmed(true, false, 1),
                "a lock arms the boot shield");
        check(ProtectionLock.bootShieldArmed(true, true, 0),
                "keep installed arms the boot shield");
        check(!ProtectionLock.bootShieldArmed(true, false, 0),
                "the install-only switch does not arm it");
        check(!ProtectionLock.bootShieldArmed(false, true, 1),
                "a Play build has no boot shield");
    }

    private static void systemOutOfScope() {
        // A lock never changes Android's own recovery routes. This test documents
        // the boundary next to the decisions instead of implying they are covered.
        check(!ProtectionLock.screenGuardArmed(false, true, true, 1),
                "no platform uninstall block is claimed outside device-owner mode");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
