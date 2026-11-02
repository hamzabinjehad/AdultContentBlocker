package app.hisn.android;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * The app's small, dependency-free design system: one place for spacing, surfaces, type, buttons,
 * status colours and the accessibility conventions the screens share. No drawable or colour is
 * hard-coded in an activity.
 */
final class Ui {
    enum ButtonKind { PRIMARY, TONAL, GHOST, DANGER, QUIET }

    static final int ON = 0;
    static final int OFF = 1;
    static final int ATTENTION = 2;
    static final int UNSUPPORTED = 3;
    static final int STARTING = 4;

    private Ui() { }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static int color(Context context, int resource) {
        return context.getColor(resource);
    }

    static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** Side padding that keeps a readable line length on tablets, foldables and landscape. */
    static int sidePadding(Context context) {
        int widthDp = context.getResources().getConfiguration().screenWidthDp;
        int spare = (widthDp - (int) (context.getResources().getDimension(R.dimen.content_max_width)
                / context.getResources().getDisplayMetrics().density)) / 2;
        return dp(context, Math.max(16, spare + 8));
    }

    static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    // ---------------------------------------------------------------- surfaces

    static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(context, 18), dp(context, 16), dp(context, 18), dp(context, 16));
        card.setElevation(dp(context, 1));
        return card;
    }

    static LinearLayout softPanel(Context context) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.bg_card_soft);
        panel.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        return panel;
    }

    static View divider(Context context) {
        View line = new View(context);
        line.setBackgroundColor(color(context, R.color.divider));
        line.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(context, 1))));
        return line;
    }

    /** Section header inside a page: icon tile, title, optional supporting line. */
    static LinearLayout sectionHeader(Context context, int iconRes, CharSequence title) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 6), 0, dp(context, 8));
        row.addView(icon(context, iconRes, R.color.accent, 20), tileParams(context));
        TextView label = new TextView(context);
        label.setText(title);
        label.setTextSize(18);
        label.setTextColor(color(context, R.color.text_primary));
        label.setTypeface(medium());
        label.setIncludeFontPadding(false);
        heading(label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMarginStart(dp(context, 10));
        row.addView(label, params);
        return row;
    }

    private static LinearLayout.LayoutParams tileParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(context, 34), dp(context, 34));
        return params;
    }

    /** Page heading (larger than a card's section header). */
    static TextView pageTitle(Context context, CharSequence text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(24);
        view.setTextColor(color(context, R.color.text_primary));
        view.setTypeface(medium());
        view.setLineSpacing(dp(context, 2), 1f);
        heading(view);
        return view;
    }

    // ---------------------------------------------------------------- text

    static TextView text(Context context, CharSequence text, float sizeSp, int colorRes) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color(context, colorRes));
        view.setLineSpacing(dp(context, 3), 1.12f);
        view.setIncludeFontPadding(false);
        return view;
    }

    static TextView body(Context context, CharSequence text) {
        return text(context, text, 15, R.color.text_secondary);
    }

    static TextView caption(Context context, CharSequence text) {
        TextView view = text(context, text, 13, R.color.text_tertiary);
        view.setLineSpacing(dp(context, 2), 1.14f);
        return view;
    }

    static TextView strong(Context context, CharSequence text) {
        TextView view = text(context, text, 15, R.color.text_primary);
        view.setTypeface(medium());
        return view;
    }

    static void heading(TextView view) {
        if (Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(true);
    }

    static void setTextIfChanged(TextView view, CharSequence text) {
        if (view == null) return;
        CharSequence current = view.getText();
        if (current == null ? text == null : current.toString().contentEquals(text == null ? "" : text)) return;
        view.setText(text);
    }

    // ---------------------------------------------------------------- icons and colour

    static ImageView icon(Context context, int iconRes, int colorRes, int sizeDp) {
        ImageView image = new ImageView(context);
        image.setImageResource(iconRes);
        image.setColorFilter(color(context, colorRes));
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        image.setLayoutParams(new LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp)));
        return image;
    }

    static ImageView iconInTile(Context context, int iconRes, int colorRes, int tileDp, int iconDp) {
        ImageView image = icon(context, iconRes, colorRes, iconDp);
        image.setBackgroundResource(R.drawable.bg_icon_tile);
        image.setPadding(dp(context, (tileDp - iconDp) / 2f), dp(context, (tileDp - iconDp) / 2f),
                dp(context, (tileDp - iconDp) / 2f), dp(context, (tileDp - iconDp) / 2f));
        image.setLayoutParams(new LinearLayout.LayoutParams(dp(context, tileDp), dp(context, tileDp)));
        return image;
    }

    static int stateColor(Context context, int state) {
        switch (state) {
            case ON: return color(context, R.color.success);
            case ATTENTION: return color(context, R.color.warning);
            case STARTING: return color(context, R.color.accent);
            case UNSUPPORTED: return color(context, R.color.text_tertiary);
            default: return color(context, R.color.text_tertiary);
        }
    }

    static View dot(Context context, int color, int sizeDp) {
        View dot = new View(context);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        dot.setBackground(shape);
        dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        dot.setLayoutParams(new LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp)));
        return dot;
    }

    /**
     * One glanceable layer state: a soft tinted tile with a label and a word. Short words keep the
     * three tiles readable at large font sizes, where a sentence would wrap into a wall.
     */
    static final class LayerTile {
        final LinearLayout root;
        final TextView name;
        final TextView value;

        LayerTile(LinearLayout root, TextView name, TextView value) {
            this.root = root;
            this.name = name;
            this.value = value;
        }

        void setState(CharSequence state, int stateKind) {
            Context context = root.getContext();
            int color = stateColor(context, stateKind);
            value.setText(state);
            value.setTextColor(color);
            GradientDrawable background = new GradientDrawable();
            background.setColor(withAlpha(color, 0.10f));
            background.setCornerRadius(dp(context, 14));
            root.setBackground(background);
            root.setContentDescription(name.getText() + ": " + state);
        }
    }

    static LayerTile layerTile(Context context, CharSequence label, CharSequence state, int stateKind) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(context, 12), dp(context, 10), dp(context, 10), dp(context, 10));
        TextView name = text(context, label, 12, R.color.text_secondary);
        name.setTypeface(medium());
        TextView value = text(context, state, 14, R.color.text_primary);
        value.setTypeface(medium());
        value.setPadding(0, dp(context, 2), 0, 0);
        tile.addView(name);
        tile.addView(value);
        LayerTile layer = new LayerTile(tile, name, value);
        layer.setState(state, stateKind);
        return layer;
    }

    // ---------------------------------------------------------------- buttons

    static Button button(Context context, CharSequence label, ButtonKind kind) {
        Button button = new Button(context);
        button.setText(label);
        style(button, kind);
        return button;
    }

    static Button button(Context context, int labelRes, ButtonKind kind) {
        return button(context, context.getString(labelRes), kind);
    }

    static void style(Button button, ButtonKind kind) {
        Context context = button.getContext();
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(medium());
        button.setMinHeight(dp(context, 48));
        button.setMinimumHeight(dp(context, 48));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(context, 18), dp(context, 12), dp(context, 18), dp(context, 12));
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setStateListAnimator(null);
        switch (kind) {
            case PRIMARY:
                button.setBackgroundResource(R.drawable.bg_button_primary);
                button.setTextColor(color(context, R.color.on_accent));
                break;
            case DANGER:
                button.setBackgroundResource(R.drawable.bg_button_danger);
                button.setTextColor(color(context, R.color.danger));
                break;
            case GHOST:
                button.setBackgroundResource(R.drawable.bg_button_ghost);
                button.setTextColor(color(context, R.color.text_primary));
                break;
            case QUIET:
                button.setBackgroundResource(R.drawable.bg_button_quiet);
                button.setTextColor(color(context, R.color.accent));
                break;
            case TONAL:
            default:
                button.setBackgroundResource(R.drawable.bg_button_tonal);
                button.setTextColor(color(context, R.color.accent));
                break;
        }
    }

    static LinearLayout.LayoutParams buttonParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, 8);
        return params;
    }

    // ---------------------------------------------------------------- rows

    /** A settings row with a title and a live summary, made entirely tappable. */
    static final class SettingRow {
        final LinearLayout root;
        final TextView title;
        final TextView summary;

        SettingRow(LinearLayout root, TextView title, TextView summary) {
            this.root = root;
            this.title = title;
            this.summary = summary;
        }

        /** Keeps the row's spoken label in step when the same row changes meaning, as the schedule mode does. */
        void setTitle(CharSequence text) {
            setTextIfChanged(title, text);
            root.setContentDescription(text + ". " + summary.getText());
        }

        /** Keeps the row's spoken label in step with its visible summary. */
        void setSummary(CharSequence text) {
            setTextIfChanged(summary, text);
            root.setContentDescription(title.getText() + ". " + text);
        }
    }

    static SettingRow settingRow(Context context, int iconRes, CharSequence title, CharSequence summary,
            View.OnClickListener click) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_row);
        row.setPadding(dp(context, 6), dp(context, 10), dp(context, 6), dp(context, 10));
        row.setMinimumHeight(dp(context, 56));
        row.setClickable(true);
        row.setFocusable(true);
        row.addView(icon(context, iconRes, R.color.accent, 22));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView name = strong(context, title);
        TextView detail = caption(context, summary);
        detail.setPadding(0, dp(context, 2), 0, 0);
        column.addView(name);
        column.addView(detail);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        columnParams.setMarginStart(dp(context, 14));
        columnParams.setMarginEnd(dp(context, 8));
        row.addView(column, columnParams);
        row.addView(icon(context, R.drawable.ic_chevron_right, R.color.text_tertiary, 20));
        row.setOnClickListener(click);
        SettingRow holder = new SettingRow(row, name, detail);
        holder.setSummary(summary);
        return holder;
    }

    /** A switch with its explanation as a second, smaller line in the same accessible label. */
    static Switch toggle(Context context, CharSequence title) {
        return toggle(context, title, null);
    }

    static Switch toggle(Context context, CharSequence title, CharSequence summary) {
        Switch toggle = new Switch(context);
        setToggleText(toggle, title, summary);
        toggle.setTextSize(15);
        toggle.setTextColor(color(context, R.color.text_primary));
        toggle.setTypeface(medium());
        toggle.setLineSpacing(dp(context, 2), 1.12f);
        toggle.setPadding(0, dp(context, 10), 0, dp(context, 10));
        toggle.setMinHeight(dp(context, 52));
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        return toggle;
    }

    static void setToggleText(Switch toggle, CharSequence title, CharSequence summary) {
        if (summary == null || summary.length() == 0) {
            toggle.setText(title);
            return;
        }
        SpannableString text = new SpannableString(title + "\n" + summary);
        int split = title.length() + 1;
        text.setSpan(new RelativeSizeSpan(0.84f), split, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(color(toggle.getContext(), R.color.text_secondary)),
                split, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new android.text.style.TypefaceSpan("sans-serif"), split, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        toggle.setText(text);
    }

    /** A local, selectable block of text such as the provisioning command. */
    static TextView commandBlock(Context context, CharSequence text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(12);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextColor(color(context, R.color.text_secondary));
        view.setTextIsSelectable(true);
        // Shell commands stay left to right inside an Arabic screen.
        view.setTextDirection(View.TEXT_DIRECTION_LTR);
        view.setGravity(Gravity.START);
        view.setBackgroundResource(R.drawable.bg_command);
        view.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        view.setLineSpacing(dp(context, 2), 1.1f);
        return view;
    }

    // ---------------------------------------------------------------- disclosure

    /** A collapsible explanation. The header is a 48 dp target and announces its action. */
    static final class Disclosure {
        final LinearLayout root;
        final TextView title;
        final View body;
        final ImageView chevron;
        boolean expanded;

        Disclosure(LinearLayout root, TextView title, View body, ImageView chevron) {
            this.root = root;
            this.title = title;
            this.body = body;
            this.chevron = chevron;
        }

        void setExpanded(boolean expand) {
            expanded = expand;
            body.setVisibility(expand ? View.VISIBLE : View.GONE);
            chevron.animate().rotation(expand ? 180f : 0f).setDuration(
                    android.animation.ValueAnimator.areAnimatorsEnabled() ? 180 : 0).start();
            updateDescription();
        }

        void updateDescription() {
            Context context = root.getContext();
            root.setContentDescription(title.getText() + ", "
                    + context.getString(expanded ? R.string.hide_details : R.string.show_details));
        }
    }

    static Disclosure disclosure(Context context, CharSequence title, CharSequence bodyText) {
        return disclosure(context, title, body(context, bodyText));
    }

    /** Disclosure whose body is an arbitrary view, e.g. a list that is built later. */
    static Disclosure disclosure(Context context, CharSequence title, View content) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(context, 48));
        header.setBackgroundResource(R.drawable.bg_row);
        header.setPadding(0, dp(context, 6), 0, dp(context, 6));
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(15);
        titleView.setTypeface(medium());
        titleView.setTextColor(color(context, R.color.text_primary));
        titleView.setIncludeFontPadding(false);
        header.addView(titleView, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        ImageView chevron = icon(context, R.drawable.ic_chevron_down, R.color.text_secondary, 20);
        header.addView(chevron);
        content.setVisibility(View.GONE);
        content.setPadding(0, dp(context, 2), 0, dp(context, 8));
        root.addView(header);
        root.addView(content);
        Disclosure disclosure = new Disclosure(root, titleView, content, chevron);
        disclosure.updateDescription();
        header.setOnClickListener(view -> disclosure.setExpanded(!disclosure.expanded));
        root.setTag(disclosure);
        return disclosure;
    }

    // ---------------------------------------------------------------- inputs

    static EditText input(Context context, int hintRes, boolean multiline) {
        EditText edit = new EditText(context);
        edit.setHint(hintRes);
        edit.setTextColor(color(context, R.color.text_primary));
        edit.setHintTextColor(color(context, R.color.text_tertiary));
        edit.setTextSize(15);
        edit.setBackgroundResource(R.drawable.bg_input);
        edit.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | (multiline ? android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                : android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        edit.setSingleLine(!multiline);
        edit.setSaveEnabled(false);
        edit.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        if (multiline) {
            edit.setMinLines(3);
            edit.setGravity(Gravity.TOP | Gravity.START);
        }
        return edit;
    }

    static void setEnabledWithAlpha(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.45f);
    }

    static LinearLayout.LayoutParams margins(Context context, int topDp, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, topDp);
        params.bottomMargin = dp(context, bottomDp);
        return params;
    }

    static int nightMode(Context context) {
        return context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
    }

    static boolean isNight(Context context) {
        return nightMode(context) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /** Colour for the clock/battery area: dark icons on the light theme, light icons on the dark. */
    static void applySystemBarIcons(android.view.Window window, Context context) {
        boolean lightBars = !isNight(context);
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(lightBars ? mask : 0, mask);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            window.getDecorView().setSystemUiVisibility(lightBars ? flags : 0);
            // Before Android 11 the window is not edge to edge, so give both bars the page colour
            // instead of leaving a transparent strip over the framework's default black.
            window.setStatusBarColor(color(context, R.color.background));
            window.setNavigationBarColor(color(context, R.color.surface));
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
    }
}
