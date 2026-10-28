package app.hisn.core;

/** Pure state matrix for the opt-in device-owner uninstall guard. */
public final class UninstallProtectionTest {
    public static void main(String[] args) {
        // No managed owner: an ordinary install can never be made undeletable,
        // and the legacy admin only adds a confirmation step.
        check(!UninstallProtection.shouldBlock(false, true, 60000), "ordinary install claimed blocked");
        check(!UninstallProtection.shouldBlock(false, true, 0), "ordinary install claimed blocked");
        check(UninstallProtection.state(false, false, false, true, 60000)
                == UninstallProtection.State.UNAVAILABLE, "no-owner state");
        check(UninstallProtection.state(false, true, false, true, 60000)
                == UninstallProtection.State.ADMIN_ONLY, "admin-only state");
        check(!UninstallProtection.mayRelease(false, false, 0), "ordinary install can release managed mode");

        // Device owner + an active commitment blocks regardless of the
        // persistent switch; that is the commitment promise.
        check(UninstallProtection.shouldBlock(true, false, 1), "commitment did not block");
        check(UninstallProtection.shouldBlock(true, true, 0), "keep-installed did not block");
        check(!UninstallProtection.shouldBlock(true, false, 0), "idle owner blocked unexpectedly");
        check(!UninstallProtection.shouldBlock(true, false, -1), "negative remaining blocked");
        check(UninstallProtection.state(true, true, true, false, 1)
                == UninstallProtection.State.PROTECTED, "protected state");
        check(UninstallProtection.state(true, true, false, true, 1)
                == UninstallProtection.State.OUT_OF_SYNC, "requested block not read back");
        check(UninstallProtection.state(true, true, true, false, 0)
                == UninstallProtection.State.OUT_OF_SYNC, "stale block not surfaced");
        check(UninstallProtection.state(true, true, false, false, 0)
                == UninstallProtection.State.READY, "idle owner state");

        // Changing the switch is allowed only outside a commitment; releasing
        // managed mode additionally requires the switch to be off.
        check(!UninstallProtection.mayChangeLock(1), "commitment allowed switch change");
        check(UninstallProtection.mayChangeLock(0), "expired commitment blocked switch change");
        check(UninstallProtection.mayChangeLock(-1), "negative remaining blocked switch change");
        check(!UninstallProtection.mayRelease(true, true, 0), "keep-installed allowed release");
        check(!UninstallProtection.mayRelease(true, false, 1), "commitment allowed release");
        check(UninstallProtection.mayRelease(true, false, 0), "idle owner could not release");

        System.out.println("UninstallProtection tests passed: owner/commitment matrix, "
                + "readback mismatch, switch and release guards");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
