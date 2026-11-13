package app.hisn.android;

import android.content.Context;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.hisn.core.PersonalProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * The optional name, the five reasons and the "in my own words" line, shared by the first-run page
 * and the Settings dialog so both write the same cleaned values. It never saves by itself: the
 * caller decides when the moment is complete, which keeps a half-typed name out of storage.
 */
final class PersonalForm {
    private static final String[] KEYS = {"faith", "family", "focus", "rest", "self",
            PersonalProfile.CUSTOM};
    private static final int[] LABELS = {R.string.personal_preset_faith,
            R.string.personal_preset_family, R.string.personal_preset_focus,
            R.string.personal_preset_rest, R.string.personal_preset_self,
            R.string.personal_preset_custom};

    final LinearLayout root;
    final EditText name;
    final EditText custom;
    private final List<Ui.Chip> chips = new ArrayList<>();
    private String selected = "";

    PersonalForm(Context context, PersonalProfile initial) {
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        name = Ui.input(context, R.string.personal_name_hint, false);
        // A name, not a sentence: capitalize each word and keep the keyboard's suggestions off.
        name.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        name.setText(initial.name);
        name.setFilters(new InputFilter[]{new CodePointFilter(
                PersonalProfile.MAX_NAME_CODE_POINTS)});
        root.addView(name, Ui.margins(context, 2, 0));

        TextView question = Ui.strong(context, context.getString(R.string.personal_reason_question));
        question.setPadding(0, Ui.dp(context, 18), 0, Ui.dp(context, 6));
        root.addView(question);

        // Two columns rather than one scrolling row: a long Arabic reason is not clipped at
        // the card edge, and the last option is visible without a hidden horizontal gesture.
        LinearLayout choices = new LinearLayout(context);
        choices.setOrientation(LinearLayout.VERTICAL);
        for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < 2; column++) {
                int index = rowIndex * 2 + column;
                String key = KEYS[index];
                String label = context.getString(LABELS[index]);
                Ui.Chip chip = Ui.chip(context, label);
                chip.root.setContentDescription(label);
                chip.root.setMinHeight(Ui.dp(context, 56));
                chip.root.setOnClickListener(view -> select(key));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                if (column > 0) params.setMarginStart(Ui.dp(context, 8));
                if (rowIndex > 0) params.topMargin = Ui.dp(context, 8);
                row.addView(chip.root, params);
                chips.add(chip);
            }
            choices.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        root.addView(choices);

        custom = Ui.input(context, R.string.personal_reason_hint, true);
        custom.setText(initial.reason);
        custom.setMinLines(2);
        custom.setMaxLines(3);
        custom.setFilters(new InputFilter[]{new CodePointFilter(
                PersonalProfile.MAX_REASON_CODE_POINTS)});
        root.addView(custom, Ui.margins(context, 10, 0));
        select(initial.preset);
    }

    String name() {
        return name.getText().toString();
    }

    String preset() {
        return selected;
    }

    String reason() {
        return custom.getText().toString();
    }

    /** True when the person chose their own words but left them blank. */
    boolean customMissing() {
        return PersonalProfile.CUSTOM.equals(selected)
                && PersonalProfile.cleanReason(reason()).isEmpty();
    }

    /** Saves the values this form currently shows; the caller owns the toast and dismissal. */
    void save(PersonalStore store) {
        String key = selected;
        String text = reason();
        if (PersonalProfile.CUSTOM.equals(key) && PersonalProfile.cleanReason(text).isEmpty()) {
            key = "";
        }
        store.save(name(), key, text);
    }

    /**
     * Counts code points rather than UTF-16 units, so a name of 24 emoji is not cut to 12.
     * The field simply stops accepting input at the limit instead of silently dropping the
     * extra characters when the form is saved.
     */
    private static final class CodePointFilter implements InputFilter {
        private final int max;

        CodePointFilter(int max) {
            this.max = max;
        }

        @Override public CharSequence filter(CharSequence source, int start, int end,
                Spanned destination, int dstart, int dend) {
            int room = max - codePoints(destination, 0, dstart)
                    - codePoints(destination, dend, destination.length());
            if (room <= 0) return "";
            int stop = start;
            int kept = 0;
            while (stop < end && kept < room) {
                int codePoint = Character.codePointAt(source, stop);
                stop += Character.charCount(codePoint);
                kept++;
            }
            return stop >= end ? null : source.subSequence(start, stop);
        }

        private static int codePoints(CharSequence text, int start, int end) {
            int count = 0;
            for (int index = start; index < end; ) {
                index += Character.charCount(Character.codePointAt(text, index));
                count++;
            }
            return count;
        }
    }

    private void select(String key) {
        selected = PersonalProfile.cleanPreset(key);
        if (!PersonalProfile.CUSTOM.equals(selected) && !PersonalProfile.isPreset(selected)) {
            selected = "";
        }
        for (int index = 0; index < chips.size(); index++) {
            chips.get(index).setSelected(KEYS[index].equals(selected));
        }
        custom.setVisibility(PersonalProfile.CUSTOM.equals(selected) ? View.VISIBLE : View.GONE);
        if (PersonalProfile.CUSTOM.equals(selected)) {
            custom.setGravity(Gravity.TOP | Gravity.START);
        }
    }
}
