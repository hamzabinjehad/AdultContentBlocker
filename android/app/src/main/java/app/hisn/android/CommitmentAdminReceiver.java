package app.hisn.android;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** Optional legacy admin confirmation. Declares no wipe, password or device-lock policies.
 *  Managed uninstall blocking needs this receiver to be device owner; see
 *  {@link UninstallProtectionController}. */
public final class CommitmentAdminReceiver extends DeviceAdminReceiver {
    public static ComponentName component(Context context) {
        return new ComponentName(context, CommitmentAdminReceiver.class);
    }

    public static boolean isActive(Context context) {
        try {
            DevicePolicyManager manager = context.getSystemService(DevicePolicyManager.class);
            return manager != null && manager.isAdminActive(component(context));
        } catch (RuntimeException unavailable) {
            // A device-policy service that cannot answer is not an active
            // admin. The managed status screen reports the missing fact rather
            // than crashing the whole overview.
            return false;
        }
    }

    @Override public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        // A device-owner appointment may enable this receiver; apply an
        // already-running commitment's uninstall guard immediately. A legacy
        // admin activation is a no-op because no owner exists yet.
        try { UninstallProtectionController.sync(context); }
        catch (RuntimeException ignored) { /* Status screen reports the gap. */ }
    }

    @Override public CharSequence onDisableRequested(Context context, Intent intent) {
        try {
            long remaining = PolicyStore.get(context).remainingMillis();
            if (remaining > 0) return context.getString(R.string.admin_commitment_warning, (remaining + 3599999) / 3600000);
        } catch (RuntimeException unavailable) { /* Android must retain its removal controls. */ }
        return context.getString(R.string.admin_disable_warning);
    }
}
