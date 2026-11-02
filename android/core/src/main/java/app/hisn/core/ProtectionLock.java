package app.hisn.core;

/**
 * Pure decisions for the in-app protection lock.
 *
 * <p>An active lock (the saved commitment period) freezes every saved rule: the
 * person may not add, remove or edit a domain, word, app, schedule or built-in
 * switch from inside Hisn until the period ends. Extending the lock is allowed
 * because it only makes the same promise longer. A guardian-signed policy may
 * still tighten rules; that path is separate and checked by the caller.
 *
 * <p>The lock also arms the direct build's accessibility screen guard even when
 * the separate "Keep Hisn installed" switch is off. The guard is friction, not
 * an Android uninstall block: only device-owner mode makes the platform refuse
 * an uninstall.
 */
public final class ProtectionLock {
    private ProtectionLock() { }

    /** True while the commitment period still has time. */
    public static boolean locked(long remainingMillis) {
        return remainingMillis > 0;
    }

    /** Whether the person may change a saved rule right now. */
    public static boolean mayEditRules(long remainingMillis) {
        return remainingMillis <= 0;
    }

    /**
     * Whether the direct build's screen guard should watch removal screens.
     * A lock arms it on its own; either explicit switch also arms it.
     */
    public static boolean screenGuardArmed(boolean selfProtectionBuild, boolean keepInstalled,
                                           boolean installLocked, long remainingMillis) {
        return selfProtectionBuild && (keepInstalled || installLocked || locked(remainingMillis));
    }

    /**
     * Whether the boot shield should cover the launcher after unlock. It
     * protects Hisn from removal, so the install-only switch does not arm it.
     */
    public static boolean bootShieldArmed(boolean selfProtectionBuild, boolean keepInstalled,
                                          long remainingMillis) {
        return selfProtectionBuild && (keepInstalled || locked(remainingMillis));
    }
}
