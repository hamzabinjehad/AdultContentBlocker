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

        Set<ManagedRestrictions.Restriction> basicAll =
                EnumSet.allOf(ManagedRestrictions.Restriction.class);
        for (ManagedRestrictions.Restriction restriction : ManagedRestrictions.Restriction.values()) {
            if (restriction.strictOnly) basicAll.remove(restriction);
        }
        Set<ManagedRestrictions.Restriction> strictAll =
                EnumSet.allOf(ManagedRestrictions.Restriction.class);
        Set<ManagedRestrictions.Restriction> all = ManagedRestrictions.desired(true, true, true, 0, 36);
        check(all.equals(basicAll), "not all eight basic restrictions desired on a supported Android");
        check(ManagedRestrictions.desired(true, true, true, 0, 36, true).equals(strictAll),
                "not all nine strict restrictions desired on a supported Android");
        check(!all.contains(ManagedRestrictions.Restriction.INSTALL_UNKNOWN_SOURCES),
                "strict key leaked into the basic set");

        // API 26/27 have no date/time, default-app or Private DNS restriction.
        Set<ManagedRestrictions.Restriction> api27 = EnumSet.copyOf(basicAll);
        api27.remove(ManagedRestrictions.Restriction.CONFIG_DATE_TIME);
        api27.remove(ManagedRestrictions.Restriction.CONFIG_DEFAULT_APPS);
        api27.remove(ManagedRestrictions.Restriction.CONFIG_PRIVATE_DNS);
        check(ManagedRestrictions.desired(true, true, true, 0, 27).equals(api27),
                "Android 8 was asked for a restriction it does not know");
        check(ManagedRestrictions.desired(true, true, true, 0, 26).equals(api27),
                "Android 8.0 was asked for a restriction it does not know");

        // Android 9 (API 28) adds manual date/time control, not Private DNS.
        Set<ManagedRestrictions.Restriction> api28 = EnumSet.copyOf(basicAll);
        api28.remove(ManagedRestrictions.Restriction.CONFIG_DEFAULT_APPS);
        api28.remove(ManagedRestrictions.Restriction.CONFIG_PRIVATE_DNS);
        check(ManagedRestrictions.desired(true, true, true, 0, 28).equals(api28),
                "Android 9 was asked for a later restriction");
        Set<ManagedRestrictions.Restriction> api29 = EnumSet.copyOf(basicAll);
        api29.remove(ManagedRestrictions.Restriction.CONFIG_DEFAULT_APPS);
        check(ManagedRestrictions.desired(true, true, true, 0, 29).equals(api29),
                "Android 10 is missing a supported restriction");
        // Android 14 (API 34) adds the default-app restriction; API 33 must not get it.
        Set<ManagedRestrictions.Restriction> api33 = EnumSet.copyOf(api29);
        check(ManagedRestrictions.desired(true, true, true, 0, 33).equals(api33),
                "Android 13 was asked for the default-app restriction");
        check(ManagedRestrictions.desired(true, true, true, 0, 34).equals(basicAll),
                "Android 14 is missing the default-app restriction");

        // Strict adds only unknown-source installs, from Android 10.
        check(ManagedRestrictions.desired(true, true, true, 0, 27, true).equals(api27),
                "strict Android 8 was asked for a key it does not know");
        check(ManagedRestrictions.desired(true, true, true, 0, 28, true).equals(api28),
                "strict Android 9 was asked for a key it does not know");
        Set<ManagedRestrictions.Restriction> strict29 = EnumSet.copyOf(api29);
        strict29.add(ManagedRestrictions.Restriction.INSTALL_UNKNOWN_SOURCES);
        check(ManagedRestrictions.desired(true, true, true, 0, 29, true).equals(strict29),
                "strict Android 10 is missing the unknown-source key");

        check(ManagedRestrictions.desired(true, true, false, 0, 36).isEmpty(), "idle desired set not empty");
        check(ManagedRestrictions.desired(false, true, true, 0, 36).isEmpty(), "unmanaged desired set not empty");

        // Readback is exact: one missing or one extra restriction is out of sync.
        check(ManagedRestrictions.matches(all, basicAll),
                "identical sets did not match");
        check(!ManagedRestrictions.matches(all, EnumSet.of(ManagedRestrictions.Restriction.SAFE_BOOT)),
                "a partial readback matched");
        check(!ManagedRestrictions.matches(EnumSet.noneOf(ManagedRestrictions.Restriction.class), all),
                "extra restrictions matched an empty policy");
        Set<ManagedRestrictions.Restriction> missing = EnumSet.copyOf(basicAll);
        missing.remove(ManagedRestrictions.Restriction.SAFE_BOOT);
        check(ManagedRestrictions.missing(all, EnumSet.of(ManagedRestrictions.Restriction.SAFE_BOOT))
                .equals(missing), "missing set wrong");
        check(ManagedRestrictions.extra(EnumSet.of(ManagedRestrictions.Restriction.SAFE_BOOT), all)
                .equals(missing), "extra set wrong");
        check(ManagedRestrictions.extra(all, strictAll).equals(EnumSet.of(
                        ManagedRestrictions.Restriction.INSTALL_UNKNOWN_SOURCES)),
                "strict extra set wrong");

        // Apply order is stable and release order is its reverse, so Safe Mode
        // stays blocked until the last moment of a release.
        check(java.util.Arrays.equals(ManagedRestrictions.applyOrder(all),
                new ManagedRestrictions.Restriction[]{
                        ManagedRestrictions.Restriction.SAFE_BOOT,
                        ManagedRestrictions.Restriction.APPS_CONTROL,
                        ManagedRestrictions.Restriction.FACTORY_RESET,
                        ManagedRestrictions.Restriction.ADD_USER,
                        ManagedRestrictions.Restriction.CONFIG_DATE_TIME,
                        ManagedRestrictions.Restriction.CONFIG_DEFAULT_APPS,
                        ManagedRestrictions.Restriction.CONFIG_VPN,
                        ManagedRestrictions.Restriction.CONFIG_PRIVATE_DNS}),
                "apply order changed");
        check(java.util.Arrays.equals(ManagedRestrictions.releaseOrder(all),
                new ManagedRestrictions.Restriction[]{
                        ManagedRestrictions.Restriction.CONFIG_PRIVATE_DNS,
                        ManagedRestrictions.Restriction.CONFIG_VPN,
                        ManagedRestrictions.Restriction.CONFIG_DEFAULT_APPS,
                        ManagedRestrictions.Restriction.CONFIG_DATE_TIME,
                        ManagedRestrictions.Restriction.ADD_USER,
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
        check(java.util.Arrays.equals(ManagedRestrictions.applyOrder(strictAll),
                new ManagedRestrictions.Restriction[]{
                        ManagedRestrictions.Restriction.SAFE_BOOT,
                        ManagedRestrictions.Restriction.APPS_CONTROL,
                        ManagedRestrictions.Restriction.FACTORY_RESET,
                        ManagedRestrictions.Restriction.ADD_USER,
                        ManagedRestrictions.Restriction.CONFIG_DATE_TIME,
                        ManagedRestrictions.Restriction.CONFIG_DEFAULT_APPS,
                        ManagedRestrictions.Restriction.INSTALL_UNKNOWN_SOURCES,
                        ManagedRestrictions.Restriction.CONFIG_VPN,
                        ManagedRestrictions.Restriction.CONFIG_PRIVATE_DNS}),
                "strict apply order changed");

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

        // The keys and API gates are Android's documented user-restriction strings.
        check(ManagedRestrictions.Restriction.SAFE_BOOT.key.equals("no_safe_boot"), "safe-boot key");
        check(ManagedRestrictions.Restriction.APPS_CONTROL.key.equals("no_control_apps"), "apps-control key");
        check(ManagedRestrictions.Restriction.FACTORY_RESET.key.equals("no_factory_reset"), "factory-reset key");
        check(ManagedRestrictions.Restriction.ADD_USER.key.equals("no_add_user"), "add-user key");
        check(ManagedRestrictions.Restriction.CONFIG_DATE_TIME.key.equals("no_config_date_time"),
                "date-time key");
        check(ManagedRestrictions.Restriction.CONFIG_VPN.key.equals("no_config_vpn"), "config-vpn key");
        check(ManagedRestrictions.Restriction.CONFIG_PRIVATE_DNS.key.equals("disallow_config_private_dns"),
                "private-DNS key");
        check(ManagedRestrictions.Restriction.CONFIG_DEFAULT_APPS.key.equals("disallow_config_default_apps"),
                "default-app key");
        check(ManagedRestrictions.Restriction.INSTALL_UNKNOWN_SOURCES.key.equals(
                "no_install_unknown_sources_globally"), "unknown-source key");
                check(ManagedRestrictions.Restriction.CONFIG_DATE_TIME.minSdk == 28, "date-time API gate");
        check(ManagedRestrictions.Restriction.CONFIG_DEFAULT_APPS.minSdk == 34, "default-app API gate");
        check(ManagedRestrictions.Restriction.INSTALL_UNKNOWN_SOURCES.minSdk == 29,
                "unknown-source API gate");
        check(ManagedRestrictions.Restriction.INSTALL_UNKNOWN_SOURCES.strictOnly, "unknown-source not strict");
        check(ManagedRestrictions.Restriction.CONFIG_PRIVATE_DNS.minSdk == 29, "private-DNS API gate");
        check(ManagedRestrictions.Restriction.ADD_USER.minSdk == 21, "add-user API gate");

        System.out.println("ManagedRestrictions tests passed: opt-in matrix, API-gated eight basic and one strict restriction, "
                + "exact readback, apply/release order and combined protection state");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
