package app.hisn.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Direct-boot aware: readies the boot shield before the first unlock. Reads no credential storage. */
public final class BootShieldReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) return;
        BootShieldService.startIfNeeded(context);
    }
}
