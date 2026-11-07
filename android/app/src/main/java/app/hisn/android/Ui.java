package app.hisn.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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
        // Rows and switches carry their own vertical padding, so the card only adds a little:
        // the list stays dense without the rows colliding with the rounded edge.
        card.setPadding(dp(context, 18), dp(context, 8), dp(context, 18), dp(context, 8));
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

    /**
     * A quiet label above a card. It names the group without a second icon or a second heading,
     * so the page reads as a list of answers rather than a wall of tiles.
     */
    static TextView sectionLabel(Context context, CharSequence text) {
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextSize(12.5f);
        label.setTextColor(color(context, R.color.text_secondary));
        label.setTypeface(medium());
        label.setLetterSpacing(0.06f);
        label.setIncludeFontPadding(false);
        label.setPadding(dp(context, 2), dp(context, 18), dp(context, 2), dp(context, 8));
        heading(label);
        return label;
    }

    /** Page heading (larger than a card's section header). */
    static TextView pageTitle(Context context, CharSequence text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(30);
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
            background.setColor(withAlpha(color, 0.12f));
            background.setCornerRadius(dp(context, 16));
            root.setBackground(background);
            root.setContentDescription(name.getText() + ": " + state);
        }
    }

    static LayerTile layerTile(Context context, CharSequence label, CharSequence state, int stateKind) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(context, 12), dp(context, 11), dp(context, 10), dp(context, 11));
        LinearLayout nameRow = new LinearLayout(context);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameRow.addView(dot(context, color(context, R.color.text_tertiary), 6));
        TextView name = text(context, label, 11, R.color.text_secondary);
        name.setTypeface(medium());
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        nameParams.setMarginStart(dp(context, 6));
        nameRow.addView(name, nameParams);
        TextView value = text(context, state, 15, R.color.text_primary);
        value.setTypeface(medium());
        value.setPadding(0, dp(context, 4), 0, 0);
        tile.addView(nameRow);
        tile.addView(value);
        LayerTile layer = new LayerTile(tile, name, value);
        layer.setState(state, stateKind);
        return layer;
    }

    /**
     * The answer to "am I protected?" as one calm shape: concentric rings tinted by state with the
     * shield at the centre. Drawn rather than asset-based so it follows light, dark and large text.
     */
    static final class ShieldView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable icon;
        private int kind = OFF;
        private int stateTint;

        ShieldView(Context context) {
            super(context);
            icon = context.getDrawable(R.drawable.ic_shield);
            stateTint = color(context, R.color.accent);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setState(int stateKind) {
            if (kind == stateKind) return;
            // Off still uses the brand accent: the shield is the product mark, while the
            // tile words and the headline carry the literal state.
            int next = stateKind == OFF ? color(getContext(), R.color.accent)
                    : stateColor(getContext(), stateKind);
            kind = stateKind;
            stateTint = next;
            invalidate();
            if (android.animation.ValueAnimator.areAnimatorsEnabled() && isAttachedToWindow()) {
                setScaleX(0.94f);
                setScaleY(0.94f);
                animate().scaleX(1f).scaleY(1f).setDuration(260).start();
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float outer = Math.min(getWidth(), getHeight()) / 2f - dp(getContext(), 1);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1));
            paint.setColor(withAlpha(stateTint, 0.14f));
            canvas.drawCircle(cx, cy, outer, paint);
            paint.setColor(withAlpha(stateTint, 0.26f));
            canvas.drawCircle(cx, cy, outer - dp(getContext(), 9), paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(withAlpha(stateTint, 0.10f));
            canvas.drawCircle(cx, cy, outer - dp(getContext(), 18), paint);

            int iconSize = dp(getContext(), 40);
            icon.setBounds((int) (cx - iconSize / 2f), (int) (cy - iconSize / 2f),
                    (int) (cx + iconSize / 2f), (int) (cy + iconSize / 2f));
            icon.setTint(stateTint);
            icon.draw(canvas);
        }
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
        button.setMinHeight(dp(context, 52));
        button.setMinimumHeight(dp(context, 52));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(context, 20), dp(context, 14), dp(context, 20), dp(context, 14));
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
        /** Right-aligned short answer such as On, Off or 8 hours; null when the row has none. */
        final TextView value;
        private final ImageView chevron;

        SettingRow(LinearLayout root, TextView title, TextView summary, TextView value,
                ImageView chevron) {
            this.root = root;
            this.title = title;
            this.summary = summary;
            this.value = value;
            this.chevron = chevron;
        }

        /** Keeps the row's spoken label in step when the same row changes meaning, as the schedule mode does. */
        void setTitle(CharSequence text) {
            setTextIfChanged(title, text);
            describe();
        }

        /** Keeps the row's spoken label in step with its visible summary. */
        void setSummary(CharSequence text) {
            setTextIfChanged(summary, text);
            describe();
        }

        /** Sets the trailing answer and colours it by kind: ON green, ATTENTION amber, OFF muted. */
        void setValue(CharSequence text, int stateKind) {
            if (value == null) {
                setSummary(text);
                return;
            }
            setTextIfChanged(value, text);
            value.setTextColor(stateColor(value.getContext(), stateKind));
            describe();
        }

        /** A locked row shows a lock instead of the chevron: the tap still explains why. */
        void setLocked(boolean locked) {
            chevron.setImageResource(locked ? R.drawable.ic_lock : R.drawable.ic_chevron_right);
            chevron.setColorFilter(color(root.getContext(),
                    locked ? R.color.text_secondary : R.color.text_tertiary));
            root.setAlpha(locked ? 0.72f : 1f);
        }

        private void describe() {
            StringBuilder spoken = new StringBuilder(title.getText());
            if (summary.getText().length() > 0) spoken.append(". ").append(summary.getText());
            if (value != null && value.getText().length() > 0) {
                spoken.append(". ").append(value.getText());
            }
            root.setContentDescription(spoken);
        }
    }

    static SettingRow settingRow(Context context, int iconRes, CharSequence title, CharSequence summary,
            View.OnClickListener click) {
        return settingRow(context, iconRes, title, summary, null, click);
    }

    /**
     * One list row: a line icon, a title, an optional one-line summary and an optional trailing
     * answer. The icon is drawn at row height rather than inside a filled tile, which keeps the
     * text column wide and the list calm.
     */
    static SettingRow settingRow(Context context, int iconRes, CharSequence title, CharSequence summary,
            CharSequence value, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_row);
        row.setPadding(dp(context, 8), dp(context, 12), dp(context, 8), dp(context, 12));
        row.setMinimumHeight(dp(context, 54));
        row.setClickable(true);
        row.setFocusable(true);
        row.addView(icon(context, iconRes, R.color.accent, 22));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView name = strong(context, title);
        name.setTextSize(15.5f);
        TextView detail = text(context, summary, 12.5f, R.color.text_tertiary);
        detail.setPadding(0, dp(context, 2), 0, 0);
        column.addView(name);
        column.addView(detail);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        columnParams.setMarginStart(dp(context, 12));
        columnParams.setMarginEnd(dp(context, 8));
        row.addView(column, columnParams);
        TextView trailing = null;
        if (value != null) {
            trailing = text(context, value, 13.5f, R.color.text_secondary);
            trailing.setTypeface(medium());
            trailing.setGravity(Gravity.END);
            trailing.setMaxLines(1);
            trailing.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            valueParams.setMarginEnd(dp(context, 8));
            row.addView(trailing, valueParams);
        }
        ImageView chevron = icon(context, R.drawable.ic_chevron_right, R.color.text_tertiary, 18);
        row.addView(chevron);
        row.setOnClickListener(click);
        SettingRow holder = new SettingRow(row, name, detail, trailing, chevron);
        holder.setSummary(summary);
        if (value != null) holder.setValue(value, OFF);
        return holder;
    }

    /**
     * A short state chip: a dot and one or two words on a tinted pill. Used for a verdict that
     * answers a question in the header of a card.
     */
    static final class StatusPill {
        final LinearLayout root;
        final TextView label;
        private final View dot;

        StatusPill(LinearLayout root, TextView label, View dot) {
            this.root = root;
            this.label = label;
            this.dot = dot;
        }

        void setState(CharSequence text, int stateKind) {
            Context context = root.getContext();
            int tint = stateColor(context, stateKind);
            label.setText(text);
            label.setTextColor(tint);
            dot.getBackground().setTint(tint);
            GradientDrawable background = new GradientDrawable();
            background.setColor(withAlpha(tint, 0.12f));
            background.setCornerRadius(dp(context, 999));
            root.setBackground(background);
            root.setContentDescription(text);
        }
    }

    static StatusPill statusPill(Context context, CharSequence text, int stateKind) {
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(context, 10), dp(context, 5), dp(context, 11), dp(context, 5));
        View dot = dot(context, color(context, R.color.text_tertiary), 6);
        pill.addView(dot);
        TextView label = text(context, text, 12.5f, R.color.text_secondary);
        label.setTypeface(medium());
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.setMarginStart(dp(context, 6));
        pill.addView(label, labelParams);
        StatusPill holder = new StatusPill(pill, label, dot);
        holder.setState(text, stateKind);
        return holder;
    }

    /** A switch with its explanation as a second, smaller line in the same accessible label. */
    static Switch toggle(Context context, CharSequence title) {
        return toggle(context, title, null);
    }

    static Switch toggle(Context context, CharSequence title, CharSequence summary) {
        Switch toggle = new Switch(context);
        setToggleText(toggle, title, summary);
        toggle.setTextSize(16);
        toggle.setTextColor(color(context, R.color.text_primary));
        toggle.setTypeface(medium());
        toggle.setLineSpacing(dp(context, 3), 1.12f);
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
        text.setSpan(new RelativeSizeSpan(0.80f), split, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
        header.setMinimumHeight(dp(context, 52));
        header.setBackgroundResource(R.drawable.bg_row);
        header.setPadding(0, dp(context, 8), 0, dp(context, 8));
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(16);
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
        edit.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
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

    /** Dims a disabled control without making its label unreadable: 0.45 fails contrast. */
    static void setEnabledWithAlpha(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.58f);
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
