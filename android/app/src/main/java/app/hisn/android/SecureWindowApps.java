package app.hisn.android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Apps whose private (screenshot-refusing) windows are left uncovered. Password managers fill in
 * other apps' passwords, so Android lists them as autofill or credential services: they are never
 * media and never covered. Any other private app (a bank, an authenticator) is covered until the
 * owner taps "This app is safe" on the cover; a protection lock refuses that tap.
 */
final class SecureWindowApps {
    private static final String PREFS = "secure_window_apps";
    private static final String ALLOWED = "allowed";
    private static volatile Set<String> allowed;
    private static volatile Set<String> passwordManagers;

    private SecureWindowApps() { }

    /** True when this app's private window is not covered. */
    static boolean exempt(Context context, String packageName) {
        return packageName != null && (allowed(context).contains(packageName)
                || passwordManagers(context).contains(packageName));
    }

    /** False during a protection lock, which only lets rules grow. */
    static boolean allow(Context context, String packageName) {
        if (packageName == null || OpenWaitStore.locked(context)) return false;
        Set<String> next = new HashSet<>(allowed(context));
        next.add(packageName);
        prefs(context).edit().putStringSet(ALLOWED, next).apply();
        allowed = Collections.unmodifiableSet(next);
        return true;
    }

    private static Set<String> allowed(Context context) {
        Set<String> held = allowed;
        if (held == null) {
            held = Collections.unmodifiableSet(new HashSet<>(prefs(context).getStringSet(ALLOWED, Collections.emptySet())));
            allowed = held;
        }
        return held;
    }

    /** Asked once per guard instance; a password manager installed later is found after a rebind. */
    private static Set<String> passwordManagers(Context context) {
        Set<String> held = passwordManagers;
        if (held != null) return held;
        Set<String> found = new HashSet<>();
        for (String action : new String[] {"android.service.autofill.AutofillService",
                "android.service.credentials.CredentialProviderService"}) {
            try {
                for (ResolveInfo info : context.getPackageManager().queryIntentServices(new Intent(action), 0)) {
                    if (info.serviceInfo != null) found.add(info.serviceInfo.packageName);
                }
            } catch (RuntimeException unavailable) { /* Unknown: the cover stays. */ }
        }
        // A phone's own autofill (Google, Samsung Pass) is a system app, never a covered window.
        held = Collections.unmodifiableSet(found);
        passwordManagers = held;
        return held;
    }

    static void refresh() { passwordManagers = null; }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
