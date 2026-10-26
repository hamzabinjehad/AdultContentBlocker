package app.hisn.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;

/** Resumes DNS filtering after a reboot only if the owner left it on and Android still holds
 * Hisn's VPN approval. It never asks for approval or changes a system setting. */
public final class VpnBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || !DnsVpnService.resumeRequested(context)) return;
        try {
            // A non-null consent intent means approval is gone; it is never launched from here.
            if (VpnService.prepare(context) != null) {
                DnsVpnService.setResumeRequested(context, false);
                DnsVpnService.notifyStopped(context, R.string.dns_not_resumed_body);
                return;
            }
            context.startForegroundService(new Intent(context, DnsVpnService.class));
        } catch (RuntimeException unavailable) {
            try { DnsVpnService.notifyStopped(context, R.string.dns_not_resumed_body); }
            catch (RuntimeException ignored) { /* Hisn shows OFF when opened. */ }
        }
    }
}
