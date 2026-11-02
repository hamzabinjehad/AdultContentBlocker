package app.hisn.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Pure decisions for the optional managed-lockdown restrictions.
 *
 * A device owner can add user restrictions that ordinary apps cannot. Hisn uses
 * eight in the basic lockdown and one more only when the owner also turns on
 * strict mode, and only while managed lockdown is on and an active commitment
 * or the keep-installed switch is holding the app:
 *
 *   SAFE_BOOT            no Safe Mode from the power menu or hardware keys
 *   APPS_CONTROL         no uninstall, disable, clear-data or force-stop for any app
 *   FACTORY_RESET        no Settings erase (recovery mode still works)
 *   ADD_USER             no second user or work profile
 *   CONFIG_DATE_TIME     no manual date, time or timezone change (Android 9+, API 28)
 *   CONFIG_DEFAULT_APPS  no changing default apps (Android 14+, API 34)
 *   CONFIG_VPN           no VPN configuration changes
 *   CONFIG_PRIVATE_DNS   no Private DNS changes (Android 10+, API 29)
 *
 * Strict mode adds:
 *
 *   INSTALL_UNKNOWN_SOURCES  no unknown-source installs (Android 10+, API 29)
 *
 * Strict is deliberately separate: it also blocks the guardian's own
 * sideloaded update. USB debugging is deliberately not blocked, so the
 * guardian can still verify and recover the phone over ADB; ADB is stated as a
 * remaining route rather than hidden. A device-wide install block
 * ({@code no_install_apps}) is still not used because it would freeze Play
 * updates as well. None of this survives ADB, root, a device-owner removal at
 * the console, or recovery mode.
 */
public final class ManagedRestrictions {
    private ManagedRestrictions() { }

    /** The Android user-restriction keys, kept here so the decision is testable without Android. */
    public enum Restriction {
        SAFE_BOOT("no_safe_boot", 21, false),
        APPS_CONTROL("no_control_apps", 21, false),
        FACTORY_RESET("no_factory_reset", 21, false),
        ADD_USER("no_add_user", 21, false),
        CONFIG_DATE_TIME("no_config_date_time", 28, false),
        CONFIG_DEFAULT_APPS("disallow_config_default_apps", 34, false),
        INSTALL_UNKNOWN_SOURCES("no_install_unknown_sources_globally", 29, true),
        CONFIG_VPN("no_config_vpn", 21, false),
        CONFIG_PRIVATE_DNS("disallow_config_private_dns", 29, false);

        public final String key;
        /** The first API level whose UserManager knows this key. */
        public final int minSdk;
        /** True only for the separate strict-mode opt-in. */
        public final boolean strictOnly;

        Restriction(String key, int minSdk, boolean strictOnly) {
            this.key = key;
            this.minSdk = minSdk;
            this.strictOnly = strictOnly;
        }

        /** Apply order, and the reverse of it is the release order. */
        public static final Restriction[] APPLY_ORDER = {
                SAFE_BOOT, APPS_CONTROL, FACTORY_RESET, ADD_USER, CONFIG_DATE_TIME,
                CONFIG_DEFAULT_APPS, INSTALL_UNKNOWN_SOURCES, CONFIG_VPN,
                CONFIG_PRIVATE_DNS
        };
    }

    /** Whether managed lockdown should hold any restriction right now. */
    public static boolean shouldApply(boolean deviceOwner, boolean managedLockdown,
                                      boolean keepInstalled, long remainingMillis) {
        return deviceOwner && managedLockdown && (keepInstalled || remainingMillis > 0);
    }

    /** The exact basic set Android should hold, assuming every current platform key is supported. */
    public static Set<Restriction> desired(boolean deviceOwner, boolean managedLockdown,
                                           boolean keepInstalled, long remainingMillis) {
        return desired(deviceOwner, managedLockdown, keepInstalled, remainingMillis,
                Integer.MAX_VALUE, false);
    }

    /** The exact basic set Android should hold on the given API level. */
    public static Set<Restriction> desired(boolean deviceOwner, boolean managedLockdown,
                                           boolean keepInstalled, long remainingMillis, int sdkInt) {
        return desired(deviceOwner, managedLockdown, keepInstalled, remainingMillis, sdkInt, false);
    }

    /**
     * The exact set Android should hold. {@code strict} adds the two keys that
     * also close sideloaded installs and USB debugging. The caller passes
     * {@code Build.VERSION.SDK_INT}; older versions must not be asked for a key
     * they do not know, and an unsupported key would turn the status out of sync.
     */
    public static Set<Restriction> desired(boolean deviceOwner, boolean managedLockdown,
                                           boolean keepInstalled, long remainingMillis, int sdkInt,
                                           boolean strict) {
        if (!shouldApply(deviceOwner, managedLockdown, keepInstalled, remainingMillis)) {
            return Collections.emptySet();
        }
        Set<Restriction> wanted = EnumSet.noneOf(Restriction.class);
        for (Restriction restriction : Restriction.values()) {
            if (sdkInt >= restriction.minSdk && (strict || !restriction.strictOnly)) {
                wanted.add(restriction);
            }
        }
        return Collections.unmodifiableSet(wanted);
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

    /** Apply order for missing restrictions: safe boot first, VPN and Private DNS last. */
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
