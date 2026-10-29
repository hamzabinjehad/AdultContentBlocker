package app.hisn.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Pure decisions for the optional managed-lockdown restrictions.
 *
 * A device owner can add user restrictions that ordinary apps cannot. Hisn uses
 * four, and only while the person has explicitly turned on managed lockdown and
 * an active commitment or the keep-installed switch is holding the app:
 *
 *   SAFE_BOOT       no Safe Mode from the power menu or hardware keys
 *   APPS_CONTROL    no uninstall, disable, clear-data or force-stop for any app
 *   FACTORY_RESET   no Settings erase (recovery mode still works)
 *   CONFIG_VPN      no VPN or private-DNS configuration changes
 *
 * APPS_CONTROL is deliberately broad: it changes what the person can do to
 * every app, not only Hisn. That is why it is a separate opt-in and why the
 * caller must show it before applying. None of this survives ADB, root, a
 * device-owner removal at the console, or recovery mode; the UI says so.
 */
public final class ManagedRestrictions {
    private ManagedRestrictions() { }

    /** The Android user-restriction keys, kept here so the decision is testable without Android. */
    public enum Restriction {
        SAFE_BOOT("no_safe_boot"),
        APPS_CONTROL("no_apps_control"),
        FACTORY_RESET("no_factory_reset"),
        CONFIG_VPN("no_config_vpn");

        public final String key;

        Restriction(String key) { this.key = key; }

        /** Apply order, and the reverse of it is the release order. */
        public static final Restriction[] APPLY_ORDER = {
                SAFE_BOOT, APPS_CONTROL, FACTORY_RESET, CONFIG_VPN
        };
    }

    /** Whether managed lockdown should hold any restriction right now. */
    public static boolean shouldApply(boolean deviceOwner, boolean managedLockdown,
                                      boolean keepInstalled, long remainingMillis) {
        return deviceOwner && managedLockdown && (keepInstalled || remainingMillis > 0);
    }

    /** The exact set Android should hold, given the person's choices and platform facts. */
    public static Set<Restriction> desired(boolean deviceOwner, boolean managedLockdown,
                                           boolean keepInstalled, long remainingMillis) {
        if (!shouldApply(deviceOwner, managedLockdown, keepInstalled, remainingMillis)) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.allOf(Restriction.class));
    }

    /** True when Android's readback matches the policy exactly: none missing and none extra. */
    public static boolean matches(Set<Restriction> desired, Set<Restriction> held) {
        return desired.equals(held);
    }

    /** Restrictions the policy wants and the platform does not hold. */
    public static Set<Restriction> missing(Set<Restriction> desired, Set<Restriction> held) {
        Set<Restriction> result = EnumSet.noneOf(Restriction.class);
        for (Restriction restriction : desired) if (!held.contains(restriction)) result.add(restriction);
        return result;
    }

    /** Restrictions Hisn still holds although the policy no longer wants them. */
    public static Set<Restriction> extra(Set<Restriction> desired, Set<Restriction> held) {
        Set<Restriction> result = EnumSet.noneOf(Restriction.class);
        for (Restriction restriction : held) if (!desired.contains(restriction)) result.add(restriction);
        return result;
    }

    /** Apply order for missing restrictions: safe boot first, VPN last. */
    public static Restriction[] applyOrder(Set<Restriction> restrictions) {
        java.util.List<Restriction> ordered = new java.util.ArrayList<>();
        for (Restriction restriction : Restriction.APPLY_ORDER) {
            if (restrictions.contains(restriction)) ordered.add(restriction);
        }
        return ordered.toArray(new Restriction[0]);
    }

    /** Release order: the reverse of {@link #applyOrder}, so safe boot stays blocked longest. */
    public static Restriction[] releaseOrder(Set<Restriction> restrictions) {
        Restriction[] ordered = applyOrder(restrictions);
        for (int left = 0, right = ordered.length - 1; left < right; left++, right--) {
            Restriction swap = ordered[left];
            ordered[left] = ordered[right];
            ordered[right] = swap;
        }
        return ordered;
    }
}
