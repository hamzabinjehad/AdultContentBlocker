package app.hisn.android;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Selectable launchable apps, excluding core settings, launchers and dialers. */
public final class ChosenApps {
    public static final class Entry {
        public final String packageName;
        public final String label;
        Entry(String name, String label) { packageName = name; this.label = label; }
    }

    public static Set<String> excluded(Context context) {
        Set<String> excluded = new HashSet<>();
        Collections.addAll(excluded, context.getPackageName(), "com.android.settings", "com.android.systemui",
                "com.android.permissioncontroller", "com.google.android.permissioncontroller",
                "com.android.packageinstaller", "com.google.android.packageinstaller",
                "com.samsung.android.packageinstaller");
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"));
        for (Intent intent : new Intent[]{home, dial}) {
            for (ResolveInfo info : context.getPackageManager().queryIntentActivities(intent, 0)) {
                if (info.activityInfo != null) excluded.add(info.activityInfo.packageName);
            }
        }
        return excluded;
    }

    /** Installed apps that can open ordinary web links, which is how this screen finds browsers.
     *
     * <p>The query uses example.com, so an app that only handles its own hosts (a video app
     * opening youtube.com links) is not listed. A generic handler that is not a general
     * browser can still appear, which is why the screen says "can open web links" and leaves
     * the choice to the owner. */
    public static List<Entry> browsers(Context context) {
        Set<String> excluded = excluded(context);
        Map<String, Entry> entries = new HashMap<>();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : context.getPackageManager().queryIntentActivities(launcher, 0)) {
            if (info.activityInfo == null || excluded.contains(info.activityInfo.packageName)) continue;
            String name = info.activityInfo.packageName;
            entries.put(name, new Entry(name, info.loadLabel(context.getPackageManager()).toString()));
        }
        Set<String> handlers = new java.util.LinkedHashSet<>();
        for (String scheme : new String[]{"https", "http"}) {
            Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(scheme + "://example.com/"));
            view.addCategory(Intent.CATEGORY_BROWSABLE);
            for (ResolveInfo info : context.getPackageManager().queryIntentActivities(view, 0)) {
                if (info.activityInfo != null && !excluded.contains(info.activityInfo.packageName))
                    handlers.add(info.activityInfo.packageName);
            }
        }
        List<Entry> result = new ArrayList<>();
        for (String name : handlers) {
            Entry entry = entries.get(name);
            result.add(entry != null ? entry : new Entry(name, name));
        }
        result.sort((left, right) -> left.label.compareToIgnoreCase(right.label));
        return result;
    }

    public static List<Entry> list(Context context) {
        Set<String> excluded = excluded(context);
        Map<String, Entry> entries = new HashMap<>();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : context.getPackageManager().queryIntentActivities(launcher, 0)) {
            if (info.activityInfo == null || excluded.contains(info.activityInfo.packageName)) continue;
            String name = info.activityInfo.packageName;
            entries.put(name, new Entry(name, info.loadLabel(context.getPackageManager()).toString()));
        }
        List<Entry> result = new ArrayList<>(entries.values());
        result.sort((left, right) -> left.label.compareToIgnoreCase(right.label));
        return result;
    }

    private ChosenApps() { }
}
