package app.hisn.android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import app.hisn.core.AdultAppNames;
import app.hisn.core.AdultTextPolicy;
import app.hisn.core.HostDecision;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Installed apps that name themselves as adult (rule in core {@link AdultAppNames}). Blocked
 * while Adult tags is on, unless the owner allowed one that was flagged by mistake. System apps
 * are never flagged. An answer is remembered per package; one that could not be judged yet (the
 * vocabulary or the site list still loading) is asked again next time.
 */
public final class AdultNamedApps {
    private static final String PREFS = "adult_named_apps";
    private static final String ALLOWED = "allowed";
    private static final Map<String, Boolean> answers = new ConcurrentHashMap<>();
    private static volatile Context app;
    private static volatile Set<String> allowed;

    private AdultNamedApps() { }

    /** Called when the guard connects, so checks without a context can use the app's. */
    static void init(Context context) {
        app = context.getApplicationContext();
        answers.clear();
    }

    /** True when this installed app names itself as adult and the owner has not allowed it. */
    static boolean flagged(String packageName) {
        Context context = app;
        if (context == null || packageName == null || packageName.equals(context.getPackageName())) return false;
        if (allowed(context).contains(packageName)) return false;
        Boolean known = answers.get(packageName);
        if (known != null) return known;
        Boolean answer = judge(context, packageName);
        if (answer == null) return false;
        answers.put(packageName, answer);
        return answer;
    }

    /** Null while it cannot be judged yet. */
    private static Boolean judge(Context context, String packageName) {
        PackageManager packages = context.getPackageManager();
        ApplicationInfo info;
        try { info = packages.getApplicationInfo(packageName, 0); }
        catch (PackageManager.NameNotFoundException | RuntimeException gone) { return false; }
        if ((info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) return false;
        AdultTextPolicy words = AdultPolicyAssets.policy();
        CharSequence label;
        try { label = packages.getApplicationLabel(info); } catch (RuntimeException unreadable) { label = null; }
        if (AdultAppNames.adultName(words, label)) return true;
        boolean unknown = words == null;
        try {
            HostDecision.Policy sites = HostData.policy(context);
            for (String host : AdultAppNames.hosts(packageName)) {
                HostDecision.Result result = sites.evaluate(host);
                if (AdultAppNames.adultSite(result)) return true;
                if (result.outcome == HostDecision.Outcome.UNAVAILABLE) unknown = true;
            }
        } catch (RuntimeException unavailable) {
            unknown = true;
        }
        return unknown ? null : false;
    }

    /** Launchable apps flagged right now, for the Rules screen. */
    public static List<String> installedFlagged(Context context) {
        init(context);
        List<String> result = new ArrayList<>();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : context.getPackageManager().queryIntentActivities(launcher, 0)) {
            if (info.activityInfo == null || !seen.add(info.activityInfo.packageName)) continue;
            if (flagged(info.activityInfo.packageName)) result.add(info.activityInfo.packageName);
        }
        return result;
    }

    public static Set<String> allowed(Context context) {
        Set<String> held = allowed;
        if (held == null) {
            held = Collections.unmodifiableSet(new HashSet<>(prefs(context).getStringSet(ALLOWED, Collections.emptySet())));
            allowed = held;
        }
        return held;
    }

    /** Allows one flagged app. False during a protection lock, which only lets rules grow. */
    public static boolean allow(Context context, String packageName) {
        if (OpenWaitStore.locked(context)) return false;
        Set<String> next = new HashSet<>(allowed(context));
        next.add(packageName);
        prefs(context).edit().putStringSet(ALLOWED, next).apply();
        allowed = Collections.unmodifiableSet(next);
        answers.remove(packageName);
        return true;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
