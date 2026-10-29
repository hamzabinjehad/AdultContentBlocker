package app.hisn.android;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserManager;
import android.util.Log;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import app.hisn.core.ManagedRestrictions;
import app.hisn.core.UninstallProtection;

/**
 * The Android half of opt-in managed protection.
 *
 * Only a device owner can make an app undeletable on Android. A normal install,
 * even with the legacy device admin, cannot: the admin adds a confirmation step
 * and nothing more. This controller never claims otherwise. It reads the
 * platform's own readback, asks {@link UninstallProtection} whether the flag
 * should be on and {@link ManagedRestrictions} which hardening restrictions
 * should hold, then applies only the difference.
 *
 * The owner must provision device ownership outside the app (the usual route is
 * a factory-reset device with no accounts, then
 * {@code adb shell dpm set-device-owner <package>/<receiver>}). Hisn does not
 * attempt that silently: it changes the phone's administrative scope and is not
 * reversible from inside an ordinary app. When no commitment and no keep
 * switch remain, {@link #releaseManagedMode(Context)} clears the restrictions,
 * the uninstall block and the device-owner appointment so the app can be
 * removed again.
 *
 * The restrictions are a second, explicit opt-in (`managedLockdown`). They are
 * broad: APPS_CONTROL stops uninstalling, disabling or clearing the data of
 * every app, not only Hisn. The UI discloses that before the switch is saved.
 */
final class UninstallProtectionController {
    private static final String TAG = "HisnUninstall";

    private UninstallProtectionController() { }

    /** A snapshot of platform facts, never of what Hisn merely requested. */
    static final class Status {
        final boolean deviceOwner;
        final boolean adminActive;
        final boolean uninstallBlocked;
        final boolean keepInstalled;
        final boolean managedLockdown;
        final long remainingMillis;
        final Set<ManagedRestrictions.Restriction> restrictions;
        final String error;

        Status(boolean deviceOwner, boolean adminActive, boolean uninstallBlocked,
               boolean keepInstalled, boolean managedLockdown, long remainingMillis,
               Set<ManagedRestrictions.Restriction> restrictions, String error) {
            this.deviceOwner = deviceOwner;
            this.adminActive = adminActive;
            this.uninstallBlocked = uninstallBlocked;
            this.keepInstalled = keepInstalled;
            this.managedLockdown = managedLockdown;
            this.remainingMillis = remainingMillis;
            Set<ManagedRestrictions.Restriction> copy = EnumSet.noneOf(ManagedRestrictions.Restriction.class);
            copy.addAll(restrictions);
            this.restrictions = Collections.unmodifiableSet(copy);
            this.error = error;
        }

        /** The restrictions the person's choices and the platform facts call for right now. */
        Set<ManagedRestrictions.Restriction> desiredRestrictions() {
            return ManagedRestrictions.desired(deviceOwner, managedLockdown, keepInstalled, remainingMillis);
        }

        /** True when the platform holds exactly the desired set, with nothing missing or extra. */
        boolean restrictionsMatch() {
            return ManagedRestrictions.matches(desiredRestrictions(), restrictions);
        }

        UninstallProtection.State state() {
            return UninstallProtection.state(deviceOwner, adminActive, uninstallBlocked,
                    keepInstalled, remainingMillis, restrictionsMatch());
        }

        boolean mayRelease() {
            return UninstallProtection.mayRelease(deviceOwner, keepInstalled, remainingMillis);
        }
    }

    static final class Result {
        final boolean ok;
        final String reason;
        final Status status;

        Result(boolean ok, String reason, Status status) {
            this.ok = ok;
            this.reason = reason;
            this.status = status;
        }
    }

    static Status read(Context context) {
        boolean owner = false;
        boolean admin = false;
        boolean blocked = false;
        boolean keep = false;
        boolean lockdown = false;
        long remaining = 0;
        String error = null;
        Set<ManagedRestrictions.Restriction> held = EnumSet.noneOf(ManagedRestrictions.Restriction.class);
        DevicePolicyManager manager = context.getSystemService(DevicePolicyManager.class);
        if (manager != null) {
            try { owner = manager.isDeviceOwnerApp(context.getPackageName()); }
            catch (RuntimeException unavailable) { error = "owner"; }
            admin = CommitmentAdminReceiver.isActive(context);
        } else {
            error = "policy-service";
        }
        try {
            PolicyStore store = PolicyStore.get(context);
            keep = store.uninstallLock();
            lockdown = store.managedLockdown();
            remaining = store.remainingMillis();
        } catch (RuntimeException unavailable) {
            error = error == null ? "settings" : error;
        }
        if (owner && manager != null) {
            try {
                blocked = manager.isUninstallBlocked(CommitmentAdminReceiver.component(context),
                        context.getPackageName());
            } catch (RuntimeException unavailable) {
                error = error == null ? "readback" : error;
            }
            UserManager users = context.getSystemService(UserManager.class);
            if (users == null) {
                error = error == null ? "restrictions" : error;
            } else {
                try {
                    Bundle values = users.getUserRestrictions();
                    for (ManagedRestrictions.Restriction restriction
                            : ManagedRestrictions.Restriction.values()) {
                        if (values.getBoolean(restriction.key, false)) held.add(restriction);
                    }
                } catch (RuntimeException unavailable) {
                    error = error == null ? "restrictions" : error;
                }
            }
        }
        return new Status(owner, admin, blocked, keep, lockdown, remaining, held, error);
    }

    /**
     * Apply the difference between policy and the platform.
     *
     * A non-owner is a successful no-op: there is nothing managed to repair,
     * and the UI explains how to enroll rather than pretending a flag was set.
     * A partial failure never becomes a green light: the caller reads the
     * platform again and shows OUT_OF_SYNC.
     */
    static Result sync(Context context) {
        Status status = read(context);
        if (!status.deviceOwner) return new Result(true, "not-managed", status);
        if (status.error != null) return new Result(false, status.error, status);
        boolean desiredBlock = UninstallProtection.shouldBlock(true, status.keepInstalled,
                status.remainingMillis);
        Set<ManagedRestrictions.Restriction> desired = status.desiredRestrictions();
        if (desiredBlock == status.uninstallBlocked && status.restrictionsMatch()) {
            return new Result(true, desiredBlock ? "already-protected" : "already-clear", status);
        }
        DevicePolicyManager manager = context.getSystemService(DevicePolicyManager.class);
        UserManager users = context.getSystemService(UserManager.class);
        if (manager == null || users == null) return new Result(false, "policy-service", status);

        ComponentName admin = CommitmentAdminReceiver.component(context);
        String failure = clearRestrictions(manager, admin, ManagedRestrictions.extra(desired, status.restrictions));
        if (failure == null) failure = applyRestrictions(manager, admin, ManagedRestrictions.missing(desired, status.restrictions));
        // The uninstall block is applied even when a restriction failed: a
        // partly hardened app must not also become easy to remove. The readback
        // below still reports the partial state instead of claiming success.
        if (desiredBlock != status.uninstallBlocked) {
            try {
                manager.setUninstallBlocked(CommitmentAdminReceiver.component(context),
                        context.getPackageName(), desiredBlock);
            } catch (SecurityException denied) {
                Log.w(TAG, "Uninstall policy was not accepted by the platform", denied);
                failure = failure == null ? "not-owner" : failure;
            } catch (RuntimeException unavailable) {
                Log.w(TAG, "Uninstall policy could not be applied", unavailable);
                failure = failure == null ? "apply" : failure;
            }
        }

        Status after = read(context);
        boolean matches = after.error == null && after.uninstallBlocked == desiredBlock
                && after.restrictionsMatch();
        if (failure != null || !matches) {
            return new Result(false, failure == null ? "readback" : failure, after);
        }
        return new Result(true, desiredBlock ? "protected" : "cleared", after);
    }

    private static String applyRestrictions(DevicePolicyManager manager, ComponentName admin,
                                            Set<ManagedRestrictions.Restriction> missing) {
        for (ManagedRestrictions.Restriction restriction : ManagedRestrictions.applyOrder(missing)) {
            try {
                manager.addUserRestriction(admin, restriction.key);
            } catch (SecurityException denied) {
                Log.w(TAG, "Restriction " + restriction.key + " was not accepted", denied);
                return "not-owner";
            } catch (RuntimeException unavailable) {
                Log.w(TAG, "Restriction " + restriction.key + " could not be applied", unavailable);
                return "restrictions";
            }
        }
        return null;
    }

    private static String clearRestrictions(DevicePolicyManager manager, ComponentName admin,
                                            Set<ManagedRestrictions.Restriction> extra) {
        for (ManagedRestrictions.Restriction restriction : ManagedRestrictions.releaseOrder(extra)) {
            try {
                manager.clearUserRestriction(admin, restriction.key);
            } catch (RuntimeException unavailable) {
                Log.w(TAG, "Restriction " + restriction.key + " could not be cleared", unavailable);
                return "restrictions";
            }
        }
        return null;
    }

    /**
     * Remove the device-owner appointment after the owner has turned off both
     * the commitment and the keep switch. Returns an error string, or null.
     *
     * Restrictions are cleared first in reverse apply order (safe boot last),
     * then the uninstall block, then ownership itself. Without this recovery
     * path, a voluntary self-control enrollment would be a one-way change
     * nobody could undo from the app.
     */
    static String releaseManagedMode(Context context) {
        Status status = read(context);
        if (!status.deviceOwner) return "not-managed";
        if (!status.mayRelease()) return "locked";
        DevicePolicyManager manager = context.getSystemService(DevicePolicyManager.class);
        UserManager users = context.getSystemService(UserManager.class);
        if (manager == null || users == null) return "policy-service";
        try {
            String failure = clearRestrictions(manager, CommitmentAdminReceiver.component(context),
                    status.restrictions);
            if (failure != null) return failure;
            manager.setUninstallBlocked(CommitmentAdminReceiver.component(context),
                    context.getPackageName(), false);
            manager.clearDeviceOwnerApp(context.getPackageName());
            return null;
        } catch (RuntimeException unavailable) {
            Log.w(TAG, "Managed mode could not be released", unavailable);
            return "release";
        }
    }

    static Intent adminIntent(Context context) {
        return new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                        CommitmentAdminReceiver.component(context))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        context.getString(R.string.admin_explanation));
    }

    /** The exact owner command, shown as text; Hisn never runs it itself. */
    static String provisioningCommand(Context context) {
        return "adb shell dpm set-device-owner " + context.getPackageName() + "/"
                + CommitmentAdminReceiver.class.getName();
    }
}
