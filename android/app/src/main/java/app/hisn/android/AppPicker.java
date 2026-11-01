package app.hisn.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A searchable, icon-bearing app chooser. It keeps the whole saved selection, including packages
 * that are temporarily not installed, and only reports the person's explicit additions and
 * removals. Rows expose a checked state to accessibility services; the checkbox itself is
 * decorative so TalkBack focuses one element per app.
 */
final class AppPicker {
    interface Save {
        /** Returns false to keep the dialog open, for example when the store refuses the change. */
        boolean onSave(Set<String> selected);
    }

    static AlertDialog create(Activity activity, int titleRes, int positiveRes, List<ChosenApps.Entry> apps,
            Set<String> saved, Save save) {
        Set<String> chosen = new LinkedHashSet<>(saved);
        List<ChosenApps.Entry> visible = new ArrayList<>(apps);
        Map<String, Drawable> icons = new HashMap<>();

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 4), Ui.dp(activity, 20), 0);

        TextView privateNote = Ui.caption(activity, activity.getString(R.string.app_picker_private));
        root.addView(privateNote);

        EditText search = Ui.input(activity, R.string.app_search_hint, false);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH
                | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        search.setCompoundDrawablePadding(Ui.dp(activity, 8));
        Drawable searchIcon = search.getCompoundDrawablesRelative()[0];
        if (searchIcon != null) searchIcon.setTint(Ui.color(activity, R.color.text_tertiary));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        searchParams.topMargin = Ui.dp(activity, 10);
        root.addView(search, searchParams);

        LinearLayout counterRow = new LinearLayout(activity);
        counterRow.setOrientation(LinearLayout.HORIZONTAL);
        counterRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView counter = Ui.caption(activity, "");
        counter.setTextColor(Ui.color(activity, R.color.accent));
        TextView limit = Ui.caption(activity, activity.getResources().getQuantityString(
                R.plurals.app_limit, PolicyStore.MAX_APPS, PolicyStore.MAX_APPS));
        LinearLayout.LayoutParams limitParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        limitParams.setMarginStart(Ui.dp(activity, 8));
        counterRow.addView(counter);
        counterRow.addView(limit, limitParams);
        LinearLayout.LayoutParams counterParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        counterParams.topMargin = Ui.dp(activity, 8);
        root.addView(counterRow, counterParams);

        TextView error = Ui.caption(activity, "");
        error.setTextColor(Ui.color(activity, R.color.danger));
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        error.setVisibility(View.GONE);
        root.addView(error);

        ListView list = new ListView(activity);
        list.setDivider(new ColorDrawable(Ui.color(activity, R.color.divider)));
        list.setDividerHeight(Math.max(1, Ui.dp(activity, 1)));
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        list.setVerticalScrollBarEnabled(false);
        int listHeight = Math.min(Ui.dp(activity, 360),
                Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.45f));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, listHeight);
        listParams.topMargin = Ui.dp(activity, 4);
        root.addView(list, listParams);

        TextView empty = Ui.body(activity, activity.getString(R.string.app_search_empty));
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, Ui.dp(activity, 24), 0, Ui.dp(activity, 24));
        empty.setVisibility(View.GONE);
        empty.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(empty);

        Runnable updateCounter = () -> counter.setText(activity.getResources().getQuantityString(
                R.plurals.selected_apps, chosen.size(), chosen.size()));
        updateCounter.run();
        Runnable selectionChanged = () -> {
            error.setVisibility(View.GONE);
            updateCounter.run();
        };
        Runnable atLimit = () -> {
            error.setText(activity.getResources().getQuantityString(
                    R.plurals.rules_too_many_apps, PolicyStore.MAX_APPS, PolicyStore.MAX_APPS));
            error.setVisibility(View.VISIBLE);
            error.announceForAccessibility(error.getText());
        };
        Adapter adapter = new Adapter(activity, visible, chosen, icons, selectionChanged, atLimit);
        list.setAdapter(adapter);

        Runnable refilter = () -> {
            String query = search.getText().toString().trim().toLowerCase(Locale.getDefault());
            visible.clear();
            for (ChosenApps.Entry entry : apps) {
                if (query.isEmpty() || entry.label.toLowerCase(Locale.getDefault()).contains(query)) {
                    visible.add(entry);
                }
            }
            adapter.notifyDataSetChanged();
            empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
            list.setVisibility(visible.isEmpty() ? View.GONE : View.VISIBLE);
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable text) { refilter.run(); }
        });
        search.setOnEditorActionListener((view, action, event) -> {
            InputMethodManager keyboard = activity.getSystemService(InputMethodManager.class);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(search.getWindowToken(), 0);
            return true;
        });

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(titleRes).setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(positiveRes, null).create();
        dialog.setOnShowListener(ignored -> {
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (save.onSave(new LinkedHashSet<>(chosen))) dialog.dismiss();
            });
        });
        list.setItemsCanFocus(true);
        return dialog;
    }

    private static final class Adapter extends BaseAdapter {
        private final Activity activity;
        private final List<ChosenApps.Entry> visible;
        private final Set<String> chosen;
        private final Map<String, Drawable> icons;
        private final Runnable onChanged;
        private final Runnable onLimit;

        Adapter(Activity activity, List<ChosenApps.Entry> visible, Set<String> chosen,
                Map<String, Drawable> icons, Runnable onChanged, Runnable onLimit) {
            this.activity = activity;
            this.visible = visible;
            this.chosen = chosen;
            this.icons = icons;
            this.onChanged = onChanged;
            this.onLimit = onLimit;
        }

        private void toggleAt(int position) {
            ChosenApps.Entry entry = visible.get(position);
            if (chosen.contains(entry.packageName)) {
                chosen.remove(entry.packageName);
                onChanged.run();
                notifyDataSetChanged();
            } else if (chosen.size() >= PolicyStore.MAX_APPS) {
                onLimit.run();
            } else {
                chosen.add(entry.packageName);
                onChanged.run();
                notifyDataSetChanged();
            }
        }

        @Override public int getCount() { return visible.size(); }
        @Override public ChosenApps.Entry getItem(int position) { return visible.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean hasStableIds() { return false; }

        @Override public View getView(int position, View recycled, ViewGroup parent) {
            Row row;
            if (recycled instanceof Row) {
                row = (Row) recycled;
            } else {
                row = new Row(activity);
            }
            ChosenApps.Entry entry = visible.get(position);
            row.bind(entry, chosen.contains(entry.packageName), icon(entry.packageName));
            row.setOnClickListener(view -> toggleAt(position));
            return row;
        }

        private Drawable icon(String packageName) {
            Drawable cached = icons.get(packageName);
            if (cached != null) return cached;
            Drawable loaded;
            try {
                loaded = activity.getPackageManager().getApplicationIcon(packageName);
            } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
                loaded = activity.getDrawable(R.drawable.ic_apps);
            }
            icons.put(packageName, loaded);
            return loaded;
        }
    }

    private static final class Row extends LinearLayout {
        private final ImageView icon;
        private final TextView label;
        private final CheckBox check;
        private final View.AccessibilityDelegate delegate;
        private String packageName;

        Row(Activity activity) {
            super(activity);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setBackgroundResource(R.drawable.bg_row);
            setPadding(Ui.dp(activity, 8), Ui.dp(activity, 10), Ui.dp(activity, 8), Ui.dp(activity, 10));
            setMinimumHeight(Ui.dp(activity, 56));
            setClickable(true);
            setFocusable(true);
            icon = new ImageView(activity);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(icon, new LinearLayout.LayoutParams(Ui.dp(activity, 34), Ui.dp(activity, 34)));
            label = new TextView(activity);
            label.setTextSize(15);
            label.setTextColor(Ui.color(activity, R.color.text_primary));
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            labelParams.setMarginStart(Ui.dp(activity, 12));
            labelParams.setMarginEnd(Ui.dp(activity, 8));
            addView(label, labelParams);
            check = new CheckBox(activity);
            check.setClickable(false);
            check.setFocusable(false);
            check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(check);
            delegate = new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName(CheckBox.class.getName());
                    info.setCheckable(true);
                    info.setChecked(chosenState(host));
                }
            };
        }

        void bind(ChosenApps.Entry entry, boolean selected, Drawable appIcon) {
            packageName = entry.packageName;
            label.setText(entry.label);
            icon.setImageDrawable(appIcon);
            check.setChecked(selected);
            setContentDescription(entry.label);
            setSelected(selected);
            // ListView may replace a child's delegate while rebinding a clicked row; keep ours.
            setAccessibilityDelegate(delegate);
        }

        private boolean chosenState(View host) {
            Row row = (Row) host;
            return row.check.isChecked();
        }
    }

    private AppPicker() { }
}
