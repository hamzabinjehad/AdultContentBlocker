package app.hisn.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import app.hisn.core.BrowserCoverage;

/**
 * The installed apps that can open ordinary web links, with the layer that covers each one and a
 * checkbox that adds it to the app block. Rows without any covering layer start checked, so the
 * common case (no DNS layer, nothing inspected) is one tap. Saving replaces only the installed
 * browsers in the block list; app blocks that are not browsers stay untouched.
 */
final class BrowserBlock {
    interface Save {
        /** Returns false to keep the dialog open, for example when the store refuses the change. */
        boolean onSave(Set<String> blockedBrowsers);
    }

    static AlertDialog create(Activity activity, List<ChosenApps.Entry> browsers, Set<String> blocked,
            Set<String> inspected, boolean inspectionActive, boolean domainsRunning, Save save) {
        Set<String> checked = new LinkedHashSet<>();
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 8), Ui.dp(activity, 20), 0);

        root.addView(Ui.body(activity, activity.getString(R.string.browsers_help)),
                Ui.margins(activity, 0, 8));

        for (ChosenApps.Entry browser : browsers) {
            BrowserCoverage.Layer layer = BrowserCoverage.layer(browser.packageName, blocked, inspected,
                    inspectionActive, domainsRunning);
            // Already blocked: keep it checked so saving does not silently unblock. Uncovered:
            // check it so the common case is one tap.
            if (layer == BrowserCoverage.Layer.BLOCKED || layer == BrowserCoverage.Layer.NONE) {
                checked.add(browser.packageName);
            }
            root.addView(row(activity, browser, layer, checked), Ui.margins(activity, 0, 0));
            root.addView(Ui.divider(activity));
        }
        root.addView(Ui.caption(activity, activity.getString(R.string.browsers_limit_note)),
                Ui.margins(activity, 0, 6));

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(root);
        int height = Math.min(Ui.dp(activity, 380),
                Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.5f));
        scroll.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.browsers_title).setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.browsers_block_action, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    if (save.onSave(new LinkedHashSet<>(checked))) dialog.dismiss();
                }));
        return dialog;
    }

    private static View row(Activity activity, ChosenApps.Entry browser, BrowserCoverage.Layer layer,
            Set<String> checked) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(activity, 6), 0, Ui.dp(activity, 6));

        Drawable icon;
        try {
            icon = activity.getPackageManager().getApplicationIcon(browser.packageName);
        } catch (Exception unavailable) {
            icon = null;
        }
        if (icon != null) {
            ImageView image = new ImageView(activity);
            image.setImageDrawable(icon);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Ui.dp(activity, 28), Ui.dp(activity, 28));
            params.setMarginEnd(Ui.dp(activity, 12));
            row.addView(image, params);
        }

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(Ui.strong(activity, browser.label));
        column.addView(Ui.caption(activity, activity.getString(stateRes(layer))));
        row.addView(column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        CheckBox box = new CheckBox(activity);
        box.setChecked(checked.contains(browser.packageName));
        box.setContentDescription(browser.label);
        box.setPadding(0, 0, 0, 0);
        box.setMinWidth(Ui.dp(activity, 48));
        box.setMinHeight(Ui.dp(activity, 48));
        box.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) checked.add(browser.packageName);
            else checked.remove(browser.packageName);
        });
        row.addView(box);
        row.setOnClickListener(view -> box.toggle());
        return row;
    }

    private static int stateRes(BrowserCoverage.Layer layer) {
        switch (layer) {
            case BLOCKED: return R.string.browser_state_blocked;
            case INSPECTED: return R.string.browser_state_inspected;
            case DOMAINS: return R.string.browser_state_domains;
            default: return R.string.browser_state_uncovered;
        }
    }

    private BrowserBlock() { }
}
