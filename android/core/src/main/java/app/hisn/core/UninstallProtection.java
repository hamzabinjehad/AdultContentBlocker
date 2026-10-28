package app.hisn.core;

/**
 * Pure decisions for the opt-in managed-device uninstall guard.
 *
 * Android only enforces {@code DevicePolicyManager.setUninstallBlocked} for a
 * device owner (or a profile owner inside that profile). An ordinary app, even
 * with a legacy device admin, cannot make itself undeletable. This class keeps
 * the decision separate from that platform fact so the states can be tested
 * without a managed phone:
 *
 *   UNAVAILABLE  no device owner and no admin — nothing managed is possible
 *   ADMIN_ONLY   the legacy admin is active, which adds a confirmation step
 *                but does not block uninstall
 *   READY        device owner, nothing needs blocking right now
 *   PROTECTED    device owner and Android reports uninstall blocked
 *   OUT_OF_SYNC  device owner, but the platform flag does not match the policy
 *
 * The guard blocks while a commitment is running, or while the owner chose to
 * keep Hisn installed. The owner may change that choice only when no
 * commitment is running; after both are off, {@code mayRelease} allows the
 * device-owner appointment itself to be removed.
 */
public final class UninstallProtection {
    private UninstallProtection() { }

    public enum State { UNAVAILABLE, ADMIN_ONLY, READY, PROTECTED, OUT_OF_SYNC }

    /** True when Android should refuse an ordinary uninstall of Hisn. */
    public static boolean shouldBlock(boolean deviceOwner, boolean keepInstalled, long remainingMillis) {
        if (!deviceOwner) return false;
        return keepInstalled || remainingMillis > 0;
    }

    /** Whether the owner may turn the "keep installed" switch off right now. */
    public static boolean mayChangeLock(long remainingMillis) {
        return remainingMillis <= 0;
    }

    /** Whether managed mode itself may be released back to an ordinary install. */
    public static boolean mayRelease(boolean deviceOwner, boolean keepInstalled, long remainingMillis) {
        return deviceOwner && !keepInstalled && remainingMillis <= 0;
    }

    /**
     * Derive the displayed state from platform facts.
     *
     * {@code uninstallBlocked} is Android's own readback, not what Hisn asked
     * for. A mismatch is surfaced rather than hidden; a policy that looks
     * enabled in the UI while the platform does not hold it is the failure this
     * project treats as worse than being switched off.
     */
    public static State state(boolean deviceOwner, boolean adminActive,
                              boolean uninstallBlocked, boolean keepInstalled,
                              long remainingMillis) {
        if (!deviceOwner) return adminActive ? State.ADMIN_ONLY : State.UNAVAILABLE;
        boolean target = shouldBlock(true, keepInstalled, remainingMillis);
        if (target == uninstallBlocked) return target ? State.PROTECTED : State.READY;
        return State.OUT_OF_SYNC;
    }
}
