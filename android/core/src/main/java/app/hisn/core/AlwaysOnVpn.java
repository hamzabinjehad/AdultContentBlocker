package app.hisn.core;

/**
 * Pure decisions for the device-owner always-on DNS VPN.
 *
 * This is not a full-tunnel VPN and does not enable VPN lockdown. Hisn's tunnel
 * carries DNS only, so always-on keeps the DNS filter connected without routing
 * every app's traffic through the phone. A full-tunnel service is a separate
 * future change.
 */
public final class AlwaysOnVpn {
    private AlwaysOnVpn() { }

    /** Whether Android should be asked to keep Hisn's DNS VPN always on. */
    public static boolean shouldApply(boolean deviceOwner, boolean managedLockdown,
                                      boolean keepInstalled, long remainingMillis, boolean dnsOn) {
        return shouldApply(deviceOwner, managedLockdown, keepInstalled, remainingMillis, dnsOn, true);
    }

    /**
     * Whether Android should be asked to keep Hisn's DNS VPN always on.
     * {@code vpnPrepared} is false when the VPN approval is missing; setting
     * always-on then only leaves a stale request that cannot start.
     */
    public static boolean shouldApply(boolean deviceOwner, boolean managedLockdown,
                                      boolean keepInstalled, long remainingMillis, boolean dnsOn,
                                      boolean vpnPrepared) {
        return deviceOwner && managedLockdown && (keepInstalled || remainingMillis > 0)
                && dnsOn && vpnPrepared;
    }

    /** True when the package Android reports is Hisn itself. */
    public static boolean isOurs(String currentPackage, String packageName) {
        return currentPackage != null && currentPackage.equals(packageName);
    }

    /**
     * True when the platform matches the policy. When the policy is off, another
     * app's always-on VPN is not Hisn's to remove, so it still matches.
     */
    public static boolean matches(boolean desired, String currentPackage, String packageName) {
        return desired == isOurs(currentPackage, packageName);
    }
}
