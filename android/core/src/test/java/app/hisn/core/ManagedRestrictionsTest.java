package app.hisn.core;

import java.util.EnumSet;
import java.util.Set;

/** Pure decisions for the opt-in managed-lockdown restrictions. */
public final class ManagedRestrictionsTest {
    public static void main(String[] args) {
        // Nothing applies without all three: device owner, the explicit
        // lockdown opt-in, and an active commitment or keep-installed lock.
        check(!ManagedRestrictions.shouldApply(false, true, true, 0), "ordinary install applies restrictions");
        check(!ManagedRestrictions.shouldApply(true, false, true, 0), "lockdown off still applies restrictions");
        check(!ManagedRestrictions.shouldApply(true, true, false, 0), "idle owner applies restrictions");
        check(!ManagedRestrictions.shouldApply(true, true, false, -1), "negative remaining applies restrictions");
        check(ManagedRestrictions.shouldApply(true, true, true, 0), "keep-installed did not apply restrictions");
        check(ManagedRestrictions.shouldApply(true, true, false, 1), "commitment did not apply restrictions");

        Set<ManagedRestrictions.Restriction> all = ManagedRestrictions.desired(true, true, true, 0, true);
        check(all.equals(EnumSet.allOf(ManagedRestrictions.Restriction.class)),
                "not all five restrictions desired on a supported Android");
        Set<ManagedRestrictions.Restriction> older = EnumSet.allOf(ManagedRestrictions.Restriction.class);
        older.remove(ManagedRestrictions.Restriction.CONFIG_PRIVATE_DNS);
        check(ManagedRestrictions.desired(true, true, true, 0, false).equals(older),
                "Android 8/9 was asked for the Private DNS restriction");
        check(ManagedRestrictions.desired(true, true, false, 0, true).isEmpty(), "idle desired set not empty");
        check(ManagedRestrictions.desired(false, true, true, 0, true).isEmpty(), "unmanaged desired set not empty");

        // Readback is exact: one missing or one extra restriction is out of sync.
        check(ManagedRestrictions.matches(all, EnumSet.allOf(ManagedRestrictions.Restriction.class)),
                "identical sets did not match");
        check(!ManagedRestrictions.matches(all, EnumSet.of(ManagedRestrictions.Restriction.SAFE_BOOT)),
                "a partial readback matched");
        check(!ManagedRestrictions.matches(EnumSet.noneOf(ManagedRestrictions.Restriction.class), all),
                "extra restrictions matched an empty policy");
        Set<ManagedRestrictions.Restriction> missing = EnumSet.allOf(ManagedRestrictions.Restriction.class);
        missing.remove(ManagedRestrictions.Restriction.SAFE_BOOT);
        check(ManagedRestrictions.missing(all, EnumSet.of(ManagedRestrictions.Restriction.SAFE_BOOT))
                .equals(missing), "missing set wrong");
        check(ManagedRestrictions.extra(EnumSet.of(ManagedRestrictions.Restriction.SAFE_BOOT), all)
                .equals(missing), "extra set wrong");

        // Apply order is stable and release order is its reverse, so Safe Mode
        // stays blocked until the last moment of a release.
        check(java.util.Arrays.equals(ManagedRestrictions.applyOrder(all),
                new ManagedRestrictions.Restriction[]{
                        ManagedRestrictions.Restriction.SAFE_BOOT,
                        ManagedRestrictions.Restriction.APPS_CONTROL,
                        ManagedRestrictions.Restriction.FACTORY_RESET,
                        ManagedRestrictions.Restriction.CONFIG_VPN,
                        ManagedRestrictions.Restriction.CONFIG_PRIVATE_DNS}),
                "apply order changed");
        check(java.util.Arrays.equals(ManagedRestrictions.releaseOrder(all),
                new ManagedRestrictions.Restriction[]{
                        ManagedRestrictions.Restriction.CONFIG_PRIVATE_DNS,
                        ManagedRestrictions.Restriction.CONFIG_VPN,
                        ManagedRestrictions.Restriction.FACTORY_RESET,
                        ManagedRestrictions.Restriction.APPS_CONTROL,
                        ManagedRestrictions.Restriction.SAFE_BOOT}),
                "release order is not the reverse of apply");
        check(java.util.Arrays.equals(ManagedRestrictions.applyOrder(
                        EnumSet.of(ManagedRestrictions.Restriction.CONFIG_PRIVATE_DNS,
                                ManagedRestrictions.Restriction.SAFE_BOOT)),
                new ManagedRestrictions.Restriction[]{
                        ManagedRestrictions.Restriction.SAFE_BOOT,
                        ManagedRestrictions.Restriction.CONFIG_PRIVATE_DNS}),
                "subset apply order wrong");

        // The uninstall state must not read PROTECTED over a restriction that
        // Android does not hold, nor READY while an old restriction remains.
        check(UninstallProtection.state(true, true, true, true, 0, true)
                == UninstallProtection.State.PROTECTED, "matching restrictions not protected");
        check(UninstallProtection.state(true, true, true, true, 0, false)
                == UninstallProtection.State.OUT_OF_SYNC, "missing restriction hidden");
        check(UninstallProtection.state(true, true, false, false, 0, false)
                == UninstallProtection.State.OUT_OF_SYNC, "extra restriction hidden");
        check(UninstallProtection.state(true, true, false, false, 0, true)
                == UninstallProtection.State.READY, "idle owner with no restrictions not ready");

        // The keys are Android's documented user-restriction strings.
        check(ManagedRestrictions.Restriction.SAFE_BOOT.key.equals("no_safe_boot"), "safe-boot key");
        check(ManagedRestrictions.Restriction.APPS_CONTROL.key.equals("no_apps_control"), "apps-control key");
        check(ManagedRestrictions.Restriction.FACTORY_RESET.key.equals("no_factory_reset"), "factory-reset key");
        check(ManagedRestrictions.Restriction.CONFIG_VPN.key.equals("no_config_vpn"), "config-vpn key");
        check(ManagedRestrictions.Restriction.CONFIG_PRIVATE_DNS.key.equals("disallow_config_private_dns"),
                "private-DNS key");

        System.out.println("ManagedRestrictions tests passed: opt-in matrix, API-29 Private DNS, "
                + "exact readback, apply/release order and combined protection state");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
