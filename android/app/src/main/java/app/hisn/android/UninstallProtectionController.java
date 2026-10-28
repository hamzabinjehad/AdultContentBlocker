package app.hisn.android;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import app.hisn.core.UninstallProtection;

/**
 * The Android half of opt-in managed uninstall protection.
 *
 * Only a device owner can make an app undeletable on Android. A normal install,
 * even with the legacy device admin, cannot: the admin adds a confirmation step
 * and nothing more. This controller never claims otherwise. It reads the
 * platform's own readback, asks {@link UninstallProtection} whether the flag
 * should be on, and applies only the difference.
 *
 * The owner must provision device ownership outside the app (the usual route is
 * a factory-reset device with no accounts, then
 * {@code adb shell dpm set-device-owner <package>/<receiver>}). Hisn does not
 * attempt that silently: it changes the phone's administrative scope and is not
 * reversible from inside an ordinary app. When no commitment and no keep
 * switch remain, {@link #releaseManagedMode(Context)} clears the device-owner
 * appointment so the app can be removed again.
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
        final long remainingMillis;
        final String error;

        Status(boolean deviceOwner, boolean adminActive, boolean uninstallBlocked,
               boolean keepInstalled, long remainingMillis, String error) {
            this.deviceOwner = deviceOwner;
            this.adminActive = adminActive;
            this.uninstallBlocked = uninstallBlocked;
            this.keepInstalled = keepInstalled;
            this.remainingMillis = remainingMillis;
            this.error = error;
        }

        UninstallProtection.State state() {
            return UninstallProtection.state(deviceOwner, adminActive, uninstallBlocked,
                    keepInstalled, remainingMillis);
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
        long remaining = 0;
        String error = null;
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
        }
        return new Status(owner, admin, blocked, keep, remaining, error);
    }

    /**
     * Apply the difference between policy and the platform.
     *
     * A non-owner is a successful no-op: there is nothing managed to repair,
     * and the UI explains how to enroll rather than pretending a flag was set.
     */
    static Result sync(Context context) {
        Status status = read(context);
        if (!status.deviceOwner) return new Result(true, "not-managed", status);
        if (status.error != null) return new Result(false, status.error, status);
        boolean desired = UninstallProtection.shouldBlock(true, status.keepInstalled,
                status.remainingMillis);
        if (desired == status.uninstallBlocked) {
            return new Result(true, desired ? "already-protected" : "already-clear", status);
        }
        DevicePolicyManager manager = context.getSystemService(DevicePolicyManager.class);
        if (manager == null) return new Result(false, "policy-service", status);
        try {
            manager.setUninstallBlocked(CommitmentAdminReceiver.component(context),
                    context.getPackageName(), desired);
            boolean actual = manager.isUninstallBlocked(CommitmentAdminReceiver.component(context),
                    context.getPackageName());
            Status after = read(context);
            if (actual != desired || after.uninstallBlocked != desired) {
                return new Result(false, "readback", after);
            }
            return new Result(true, desired ? "protected" : "cleared", after);
        } catch (SecurityException denied) {
            Log.w(TAG, "Uninstall policy was not accepted by the platform", denied);
            return new Result(false, "not-owner", read(context));
        } catch (RuntimeException unavailable) {
            Log.w(TAG, "Uninstall policy could not be applied", unavailable);
            return new Result(false, "apply", read(context));
        }
    }

    /**
     * Remove the device-owner appointment after the owner has turned off both
     * the commitment and the keep switch. Returns an error string, or null.
     *
     * This is the recovery path. Without it, a voluntary self-control enrollment
     * would be a one-way change nobody could undo from the app.
     */
    static String releaseManagedMode(Context context) {
        Status status = read(context);
        if (!status.deviceOwner) return "not-managed";
        if (!status.mayRelease()) return "locked";
        DevicePolicyManager manager = context.getSystemService(DevicePolicyManager.class);
        if (manager == null) return "policy-service";
        try {
            // Clearing the block first makes the app removable even if the
            // ownership release itself is rejected by a platform policy.
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
