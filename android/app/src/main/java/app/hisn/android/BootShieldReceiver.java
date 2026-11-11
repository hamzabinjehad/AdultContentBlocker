package app.hisn.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Readies the boot shield before the first unlock on Android 7+, which is the first release with a
 * device-protected storage and a locked-boot broadcast. Android 6 has neither, so it falls back to
 * the ordinary boot broadcast and starts the shield once boot has completed.
 */
public final class BootShieldReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            BootShieldService.startIfNeeded(context);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action) && Build.VERSION.SDK_INT < 24) {
            BootShieldService.startIfNeeded(context);
        }
    }
}
