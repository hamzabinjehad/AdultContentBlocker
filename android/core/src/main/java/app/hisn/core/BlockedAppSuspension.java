package app.hisn.core;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Pure decisions for suspending the apps the owner has already blocked.
 *
 * This is a stronger form of the app block: a suspended app disappears from the
 * launcher and cannot be opened, while its data is kept. It is a separate opt-in
 * because suspension changes what the whole phone shows, not only what Hisn
 * closes. Only apps the owner already put in the block list are suspended, and
 * only while managed lockdown, the keep switch or a commitment holds.
 */
public final class BlockedAppSuspension {
    private BlockedAppSuspension() { }

    public static boolean shouldApply(boolean deviceOwner, boolean managedLockdown,
                                      boolean suspendRequested, boolean keepInstalled,
                                      long remainingMillis) {
        return deviceOwner && managedLockdown && suspendRequested
                && (keepInstalled || remainingMillis > 0);
    }

    /** The blocked packages that are installed and should be suspended right now. */
    public static Set<String> desired(boolean deviceOwner, boolean managedLockdown,
                                      boolean suspendRequested, boolean keepInstalled,
                                      long remainingMillis, Set<String> blockedApps,
                                      Set<String> installedPackages) {
        if (!shouldApply(deviceOwner, managedLockdown, suspendRequested, keepInstalled, remainingMillis)) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String packageName : blockedApps) {
            if (packageName != null && installedPackages.contains(packageName)) result.add(packageName);
        }
        return Collections.unmodifiableSet(result);
    }

    /** Packages to suspend: wanted, installed and not already suspended. */
    public static Set<String> toSuspend(Set<String> desired, Set<String> suspended) {
        return difference(desired, suspended);
    }

    /** Packages to resume: suspended but no longer wanted. */
    public static Set<String> toResume(Set<String> desired, Set<String> suspended) {
        return difference(suspended, desired);
    }

    /** True when the platform's suspended set is exactly the wanted set. */
    public static boolean matches(Set<String> desired, Set<String> suspended) {
        return desired.equals(suspended);
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : left) if (!right.contains(value)) result.add(value);
        return Collections.unmodifiableSet(result);
    }
}
