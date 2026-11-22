package app.hisn.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;

/** The launcher's long-press shortcuts: the breathing pause, then one tab of the shell each. */
final class Shortcuts {
    /** The shell's tabs, as read from the "page" extra. */
    static final int TAB_RULES = 1;
    // The general edition has four tabs (Today, Shield, Progress, You): settings live on You.
    static final int TAB_SETTINGS = Edition.islamic ? 2 : 3;

    private Shortcuts() { }

    static void sync(Context context) {
        // ShortcutManager arrived in Android 7.1; 6.x simply has no long-press shortcuts.
        if (Build.VERSION.SDK_INT < 25) return;
        syncApi25(context);
    }

    @SuppressLint("NewApi") // Only called from sync after its Android 7.1 check.
    private static void syncApi25(Context context) {
        try {
            ShortcutManager manager = context.getSystemService(ShortcutManager.class);
            if (manager == null) return;
            List<ShortcutInfo> shortcuts = new ArrayList<>();
            // First, so it sits nearest the finger: the pause is for the moment of an urge.
            shortcuts.add(new ShortcutInfo.Builder(context, "pause")
                    .setShortLabel(context.getString(R.string.pause_voluntary_action))
                    .setLongLabel(context.getString(R.string.shortcut_pause_long))
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_breath))
                    .setIntent(PauseEntry.intent(context))
                    .build());
            // Unrot: start a focus from the icon, straight into the sheet on Today.
            if (!Edition.islamic) shortcuts.add(new ShortcutInfo.Builder(context, "focus")
                    .setShortLabel(context.getString(R.string.shortcut_focus_short))
                    .setLongLabel(context.getString(R.string.shortcut_focus_long))
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_breath))
                    .setIntent(new Intent(context, ShellActivity.class).setAction(Intent.ACTION_VIEW)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            .putExtra("page", 0).putExtra("focus", true))
                    .build());
            shortcuts.add(shortcut(context, "rules", R.string.shortcut_rules_short,
                    R.string.shortcut_rules_long, R.drawable.ic_tune, TAB_RULES));
            shortcuts.add(shortcut(context, "guide", R.string.shortcut_guide_short,
                    R.string.shortcut_guide_long, R.drawable.ic_book, TAB_SETTINGS));
            manager.setDynamicShortcuts(shortcuts);
        } catch (RuntimeException unavailable) {
            // A launcher without shortcut support, or a rate limit: the app still opens normally.
        }
    }

    @SuppressLint("NewApi") // Only reached from syncApi25.
    private static ShortcutInfo shortcut(Context context, String id, int shortLabel, int longLabel,
            int icon, int tab) {
        return new ShortcutInfo.Builder(context, id)
                .setShortLabel(context.getString(shortLabel))
                .setLongLabel(context.getString(longLabel))
                .setIcon(Icon.createWithResource(context, icon))
                .setIntent(new Intent(context, ShellActivity.class).setAction(Intent.ACTION_VIEW)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra("page", tab))
                .build();
    }
}
