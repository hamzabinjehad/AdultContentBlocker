package app.hisn.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import app.hisn.core.PauseTally;

import java.time.LocalDate;

/**
 * Shows a reason category, never captured text, matched words or browsing history, then a short
 * pause: guided breaths, a reminder, a question about what the person needs, and today's count.
 * Returning home is never delayed by the pause.
 *
 * <p>The reflection is progressive: the breath and one way out are on the first screen, and the
 * reminder and the four needs appear only after the third breath. The page still never gates the
 * exit on finishing the exercise.
 */
public final class RestrictionActivity extends Activity {
    private static final String PAUSE_PREFS = "pause";
    private static final long BREATH_IN_MS = 4000, BREATH_OUT_MS = 6000;
    private static final int BREATH_CYCLES = 3;
    private static final int[] REMINDERS = {R.string.pause_reminder_1, R.string.pause_reminder_2,
            R.string.pause_reminder_3, R.string.pause_reminder_4, R.string.pause_reminder_5};
    private static final int[][] NEEDS = {{R.string.pause_need_tired, R.string.pause_need_tired_tip},
            {R.string.pause_need_lonely, R.string.pause_need_lonely_tip},
            {R.string.pause_need_stressed, R.string.pause_need_stressed_tip},
            {R.string.pause_need_bored, R.string.pause_need_bored_tip}};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final View[] dots = new View[BREATH_CYCLES];
    private final Button[] needButtons = new Button[NEEDS.length];
    private View circle;
    private TextView breathLabel;
    private TextView breathStep;
    private Button breatheAgain;
    private LinearLayout reflectionPanel;
    private int cycle;
    private boolean voluntary;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        voluntary = getIntent().getBooleanExtra("voluntary", false);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        int side = Ui.sidePadding(this);
        int top = Ui.dp(this, 28);
        int bottom = Ui.dp(this, 36);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(Ui.color(this, R.color.background));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(side, top, side, bottom);
        scroll.addView(column, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        View rule = new View(this);
        rule.setBackgroundColor(Ui.color(this, R.color.accent));
        column.addView(rule, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 2)));

        TextView eyebrow = Ui.eyebrow(this, getString(voluntary
                ? R.string.pause_eyebrow : R.string.block_eyebrow));
        LinearLayout.LayoutParams eyebrowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        eyebrowParams.topMargin = Ui.dp(this, 14);
        column.addView(eyebrow, eyebrowParams);

        TextView title = Ui.display(this, getString(voluntary ? R.string.pause_voluntary_title
                : R.string.restriction_title), 32);
        title.setGravity(Gravity.START);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = Ui.dp(this, 8);
        column.addView(title, titleParams);

        TextView body = Ui.text(this, getString(reason()), 15, R.color.text_secondary);
        body.setGravity(Gravity.START);
        body.setLineSpacing(Ui.dp(this, 4), 1.18f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = Ui.dp(this, 10);
        column.addView(body, bodyParams);

        addPause(column, state == null);

        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            int barsTop = 0, barsBottom = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars()
                                | android.view.WindowInsets.Type.displayCutout());
                barsTop = bars.top;
                barsBottom = bars.bottom;
            }
            column.setPadding(side, top + barsTop, side, bottom + barsBottom);
            return insets;
        });
        setContentView(scroll);
        Ui.applySystemBarIcons(getWindow(), this);

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::back);
        }
    }

    private int reason() {
        if (voluntary) return R.string.pause_voluntary_body;
        return getIntent().getBooleanExtra("selfprotect", false) ? R.string.restriction_self_protection
                : getIntent().getBooleanExtra("shortvideo", false) ? R.string.restriction_short_video
                : getIntent().getBooleanExtra("visual", false) ? R.string.restriction_visual
                : getIntent().getBooleanExtra("schedule", false) ? R.string.restriction_schedule
                : getIntent().getBooleanExtra("limit", false) ? R.string.restriction_limit
                : getIntent().getBooleanExtra("word", false) ? R.string.restriction_word
                : R.string.restriction_app;
    }

    @Override protected void onResume() {
        super.onResume();
        Ui.applySystemBarIcons(getWindow(), this);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.applySystemBarIcons(getWindow(), this);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void addPause(LinearLayout page, boolean count) {
        // Counting is a day and a number only. A recreated screen (rotation) is the same pause.
        SharedPreferences prefs = getSharedPreferences(PAUSE_PREFS, MODE_PRIVATE);
        PauseTally tally = PauseTally.record(prefs.getLong("day", -1), prefs.getInt("count", 0),
                prefs.getLong("at", 0), LocalDate.now().toEpochDay(), System.currentTimeMillis());
        if (count) {
            prefs.edit().putLong("day", tally.day).putInt("count", tally.count)
                    .putLong("at", tally.at).apply();
        }

        LinearLayout breath = new LinearLayout(this);
        breath.setOrientation(LinearLayout.VERTICAL);
        breath.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams breathParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        breathParams.topMargin = Ui.dp(this, 22);
        page.addView(breath, breathParams);

        FrameLayout ringBox = new FrameLayout(this);
        LinearLayout.LayoutParams ringBoxParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 172), Ui.dp(this, 172));
        ringBoxParams.gravity = Gravity.CENTER_HORIZONTAL;
        breath.addView(ringBox, ringBoxParams);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ring.setGradientRadius(Ui.dp(this, 66));
        ring.setGradientCenter(0.5f, 0.5f);
        int accent = Ui.color(this, R.color.accent);
        ring.setColors(new int[]{Ui.withAlpha(accent, 0.16f), Ui.withAlpha(accent, 0.02f)});
        ring.setStroke(Math.max(1, Ui.dp(this, 1)), accent);
        circle = new View(this);
        circle.setBackground(ring);
        circle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        float rest = Compat.animationsEnabled(this) ? 0.55f : 0.8f;
        circle.setScaleX(rest);
        circle.setScaleY(rest);
        FrameLayout.LayoutParams circleParams = new FrameLayout.LayoutParams(
                Ui.dp(this, 132), Ui.dp(this, 132), Gravity.CENTER);
        ringBox.addView(circle, circleParams);

        breathLabel = Ui.display(this, getString(R.string.pause_breathe_in), 24);
        breathLabel.setGravity(Gravity.CENTER);
        breathLabel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = Ui.dp(this, 18);
        breath.addView(breathLabel, labelParams);

        breathStep = Ui.caption(this, getString(R.string.pause_breath_step, 1, BREATH_CYCLES));
        breathStep.setGravity(Gravity.CENTER);
        breathStep.setPadding(0, Ui.dp(this, 4), 0, 0);
        breath.addView(breathStep);

        LinearLayout dotRow = new LinearLayout(this);
        dotRow.setOrientation(LinearLayout.HORIZONTAL);
        dotRow.setGravity(Gravity.CENTER);
        dotRow.setPadding(0, Ui.dp(this, 12), 0, 0);
        for (int index = 0; index < BREATH_CYCLES; index++) {
            View dot = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(Ui.color(this, R.color.surface_alt));
            dot.setBackground(shape);
            dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                    Ui.dp(this, 6), Ui.dp(this, 6));
            if (index > 0) dotParams.setMarginStart(Ui.dp(this, 6));
            dotRow.addView(dot, dotParams);
            dots[index] = dot;
        }
        breath.addView(dotRow);

        breatheAgain = Ui.button(this, R.string.pause_breathe_again, Ui.ButtonKind.QUIET);
        breatheAgain.setVisibility(View.GONE);
        breath.addView(breatheAgain, Ui.buttonParams(this));
        breatheAgain.setOnClickListener(view -> breathe());

        // The way out stays on the first screen. The reflection below never gates it.
        Button home = Ui.button(this, voluntary ? R.string.pause_voluntary_done
                : R.string.restriction_home,
                voluntary ? Ui.ButtonKind.GHOST : Ui.ButtonKind.PRIMARY);
        page.addView(home, Ui.margins(this, 22, 0));
        home.setOnClickListener(view -> {
            if (voluntary) finish(); else goHome();
        });

        reflectionPanel = new LinearLayout(this);
        reflectionPanel.setOrientation(LinearLayout.VERTICAL);
        reflectionPanel.setVisibility(View.GONE);
        page.addView(reflectionPanel, Ui.margins(this, 24, 0));
        reflectionPanel.addView(Ui.divider(this));

        LinearLayout quote = new LinearLayout(this);
        quote.setOrientation(LinearLayout.HORIZONTAL);
        quote.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams quoteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        quoteParams.topMargin = Ui.dp(this, 14);
        reflectionPanel.addView(quote, quoteParams);
        View quoteRule = new View(this);
        quoteRule.setBackgroundColor(Ui.color(this, R.color.accent));
        quote.addView(quoteRule, new LinearLayout.LayoutParams(Ui.dp(this, 2), Ui.dp(this, 54)));
        TextView reminder = Ui.text(this, getString(REMINDERS[PauseTally.reminderFor(tally.count) - 1]),
                15, R.color.text_primary);
        reminder.setLineSpacing(Ui.dp(this, 4), 1.18f);
        LinearLayout.LayoutParams reminderParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        reminderParams.setMarginStart(Ui.dp(this, 12));
        quote.addView(reminder, reminderParams);

        TextView question = Ui.display(this, getString(R.string.pause_need_question), 22);
        question.setGravity(Gravity.START);
        LinearLayout.LayoutParams questionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        questionParams.topMargin = Ui.dp(this, 20);
        reflectionPanel.addView(question, questionParams);

        TextView tip = Ui.text(this, "", 14, R.color.accent);
        tip.setGravity(Gravity.START);
        tip.setVisibility(View.GONE);
        tip.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        tip.setPadding(0, Ui.dp(this, 8), 0, 0);
        for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int columnIndex = 0; columnIndex < 2; columnIndex++) {
                int index = rowIndex * 2 + columnIndex;
                Button choice = Ui.button(this, NEEDS[index][0], Ui.ButtonKind.TONAL);
                LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                if (columnIndex > 0) choiceParams.setMarginStart(Ui.dp(this, 8));
                row.addView(choice, choiceParams);
                needButtons[index] = choice;
                choice.setOnClickListener(view -> chooseNeed(index, tip));
            }
            reflectionPanel.addView(row, Ui.margins(this, 8, 0));
        }
        reflectionPanel.addView(tip);

        TextView today = tally.count == 1 ? Ui.caption(this, getString(R.string.pause_tally_first))
                : Ui.caption(this, getString(R.string.pause_tally_many, tally.count));
        today.setGravity(Gravity.START);
        today.setPadding(0, Ui.dp(this, 20), 0, 0);
        reflectionPanel.addView(today);

        breathe();
    }

    private void chooseNeed(int index, TextView tip) {
        for (int i = 0; i < needButtons.length; i++) {
            Ui.style(needButtons[i], i == index ? Ui.ButtonKind.PRIMARY : Ui.ButtonKind.TONAL);
        }
        tip.setText(NEEDS[index][1]);
        tip.setVisibility(View.VISIBLE);
    }

    /** Three breaths; with animations turned off in Android settings, the circle stays still. */
    private void breathe() {
        handler.removeCallbacksAndMessages(null);
        breatheAgain.setVisibility(View.GONE);
        if (reflectionPanel != null) {
            reflectionPanel.animate().cancel();
            reflectionPanel.setVisibility(View.GONE);
            reflectionPanel.setAlpha(1f);
            reflectionPanel.setTranslationY(0f);
        }
        cycle = 0;
        updateDots(0);
        phase(true);
    }

    private void phase(boolean inhale) {
        long duration = inhale ? BREATH_IN_MS : BREATH_OUT_MS;
        breathLabel.setText(inhale ? R.string.pause_breathe_in : R.string.pause_breathe_out);
        breathStep.setText(getString(R.string.pause_breath_step, cycle + 1, BREATH_CYCLES));
        updateDots(cycle);
        if (Compat.animationsEnabled(this)) {
            float scale = inhale ? 1f : 0.55f;
            circle.animate().scaleX(scale).scaleY(scale).setDuration(duration)
                    .setInterpolator(new AccelerateDecelerateInterpolator()).start();
        } else {
            float scale = inhale ? 1f : 0.55f;
            circle.setScaleX(scale);
            circle.setScaleY(scale);
        }
        handler.postDelayed(() -> {
            if (inhale) { phase(false); return; }
            if (++cycle < BREATH_CYCLES) { phase(true); return; }
            updateDots(BREATH_CYCLES - 1);
            breathLabel.setText(R.string.pause_title);
            breathStep.setText(R.string.pause_breath_done);
            breatheAgain.setVisibility(View.VISIBLE);
            if (reflectionPanel != null) {
                reflectionPanel.setVisibility(View.VISIBLE);
                reflectionPanel.setAlpha(0f);
                reflectionPanel.setTranslationY(Ui.dp(this, 10));
                if (Compat.animationsEnabled(this)) {
                    reflectionPanel.animate().alpha(1f).translationY(0f)
                            .setDuration(320).start();
                } else {
                    reflectionPanel.setAlpha(1f);
                    reflectionPanel.setTranslationY(0f);
                }
                reflectionPanel.announceForAccessibility(getString(R.string.pause_need_question));
            }
        }, duration);
    }

    private void updateDots(int active) {
        for (int index = 0; index < dots.length; index++) {
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(Ui.color(this,
                    index <= active ? R.color.accent : R.color.surface_alt));
            dots[index].setBackground(shape);
        }
    }

    @SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() {
        back();
    }

    private void back() {
        if (voluntary) finish(); else goHome();
    }

    private void goHome() {
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }
}
