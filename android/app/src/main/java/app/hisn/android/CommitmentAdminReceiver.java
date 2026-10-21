package app.hisn.android;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** Optional legacy admin confirmation. Declares no wipe, password or device-lock policies. */
public final class CommitmentAdminReceiver extends DeviceAdminReceiver {
    public static ComponentName component(Context context) {
        return new ComponentName(context, CommitmentAdminReceiver.class);
    }

    public static boolean isActive(Context context) {
        DevicePolicyManager manager = context.getSystemService(DevicePolicyManager.class);
        return manager != null && manager.isAdminActive(component(context));
    }

    @Override public CharSequence onDisableRequested(Context context, Intent intent) {
        try {
            long remaining = PolicyStore.get(context).remainingMillis();
            if (remaining > 0) return context.getString(R.string.admin_commitment_warning, (remaining + 3599999) / 3600000);
        } catch (RuntimeException unavailable) { /* Android must retain its removal controls. */ }
        return context.getString(R.string.admin_disable_warning);
    }
}
