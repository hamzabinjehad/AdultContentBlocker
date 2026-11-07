package app.hisn.android;

import android.animation.ValueAnimator;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import app.hisn.core.PauseTally;

import java.time.LocalDate;

/**
 * Shows a reason category, never captured text, matched words or browsing history, then a short
 * pause: guided breaths, a reminder, a question about what the person needs, and today's count.
 * Returning home is never delayed by the pause.
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
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(side, top, side, bottom);
        scroll.addView(column, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        ImageView mark = Ui.iconInTile(this, R.drawable.ic_shield, R.color.accent, 72, 40);
        LinearLayout.LayoutParams markParams = (LinearLayout.LayoutParams) mark.getLayoutParams();
        markParams.gravity = Gravity.CENTER_HORIZONTAL;
        column.addView(mark);
        TextView title = Ui.text(this, getString(voluntary ? R.string.pause_voluntary_title
                : R.string.restriction_title), 28, R.color.text_primary);
        title.setTypeface(Ui.medium());
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(this, 20), 0, 0);
        Ui.heading(title);
        column.addView(title);

        TextView body = Ui.text(this, getString(reason()), 16, R.color.text_secondary);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, Ui.dp(this, 10), 0, 0);
        column.addView(body);

        // A voluntary pause must not open with the loudest control on the screen: leaving is
        // allowed, but the breath is the point. An enforced pause keeps the way out primary.
        addRestCard(column);

        Button home = Ui.button(this, voluntary ? R.string.pause_voluntary_done
                : R.string.restriction_home,
                voluntary ? Ui.ButtonKind.GHOST : Ui.ButtonKind.PRIMARY);
        column.addView(home, Ui.margins(this, 18, 0));
        home.setOnClickListener(view -> {
            if (voluntary) finish(); else goHome();
        });

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

    /** The "rest longer / lock screen" card, only after a word or image detection. */
    private void addRestCard(LinearLayout column) {
        final String app = getIntent().getStringExtra(TextGuardService.EXTRA_APP);
        final int minutes = getIntent().getIntExtra(TextGuardService.EXTRA_MINUTES, 0);
        if (!getIntent().getBooleanExtra("canrest", false) || app == null || minutes <= 0) return;
        LinearLayout card = Ui.card(this);
        card.setPadding(Ui.dp(this, 22), Ui.dp(this, 18), Ui.dp(this, 22), Ui.dp(this, 18));
        TextView closed = Ui.text(this, getString(R.string.restriction_closed,
                getString(R.string.rest_minutes, minutes)), 15, R.color.text_secondary);
        closed.setGravity(Gravity.CENTER);
        card.addView(closed);
        Button five = Ui.button(this, R.string.restriction_rest_5, Ui.ButtonKind.TONAL);
        five.setOnClickListener(view -> rest(app, 5));
        card.addView(five, Ui.buttonParams(this));
        Button fifteen = Ui.button(this, R.string.restriction_rest_15, Ui.ButtonKind.TONAL);
        fifteen.setOnClickListener(view -> rest(app, 15));
        card.addView(fifteen, Ui.buttonParams(this));
        Button hour = Ui.button(this, R.string.restriction_rest_60, Ui.ButtonKind.TONAL);
        hour.setOnClickListener(view -> rest(app, 60));
        card.addView(hour, Ui.buttonParams(this));
        if (Build.VERSION.SDK_INT >= 28) {
            Button lock = Ui.button(this, R.string.restriction_lock_screen, Ui.ButtonKind.GHOST);
            lock.setOnClickListener(view -> lockScreen());
            card.addView(lock, Ui.buttonParams(this));
        }
        column.addView(card, Ui.margins(this, 16, 0));
    }

    private void rest(String app, int minutes) {
        sendBroadcast(new Intent(TextGuardService.ACTION_REST).setPackage(getPackageName())
                .putExtra(TextGuardService.EXTRA_APP, app)
                .putExtra(TextGuardService.EXTRA_MINUTES, minutes)
                .putExtra(TextGuardService.EXTRA_VISUAL, getIntent().getBooleanExtra("visual", false)));
        goHome();
    }

    private void lockScreen() {
        sendBroadcast(new Intent(TextGuardService.ACTION_LOCK_SCREEN).setPackage(getPackageName()));
        goHome();
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

    private void addPause(LinearLayout layout, boolean count) {
        LinearLayout card = Ui.card(this);
        card.setPadding(Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = Ui.dp(this, 22);
        layout.addView(card, cardParams);

        TextView heading = Ui.text(this, getString(R.string.pause_title), 22, R.color.text_primary);
        heading.setTypeface(Ui.medium());
        heading.setGravity(Gravity.CENTER);
        Ui.heading(heading);
        card.addView(heading);

        FrameLayout ringBox = new FrameLayout(this);
        LinearLayout.LayoutParams ringBoxParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 188), Ui.dp(this, 188));
        ringBoxParams.gravity = Gravity.CENTER_HORIZONTAL;
        ringBoxParams.topMargin = Ui.dp(this, 14);
        card.addView(ringBox, ringBoxParams);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Ui.withAlpha(Ui.color(this, R.color.accent), 0.10f));
        ring.setStroke(Ui.dp(this, 2), Ui.color(this, R.color.accent));
        circle = new View(this);
        circle.setBackground(ring);
        circle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        float rest = ValueAnimator.areAnimatorsEnabled() ? 0.55f : 0.8f;
        circle.setScaleX(rest);
        circle.setScaleY(rest);
        FrameLayout.LayoutParams circleParams = new FrameLayout.LayoutParams(
                Ui.dp(this, 148), Ui.dp(this, 148), Gravity.CENTER);
        ringBox.addView(circle, circleParams);

        breathLabel = Ui.text(this, getString(R.string.pause_breathe_in), 18, R.color.text_primary);
        breathLabel.setTypeface(Ui.medium());
        breathLabel.setGravity(Gravity.CENTER);
        card.addView(breathLabel);
        breathStep = Ui.caption(this, getString(R.string.pause_breath_step, 1, BREATH_CYCLES));
        breathStep.setGravity(Gravity.CENTER);
        breathStep.setPadding(0, Ui.dp(this, 4), 0, 0);
        card.addView(breathStep);

        LinearLayout dotRow = new LinearLayout(this);
        dotRow.setOrientation(LinearLayout.HORIZONTAL);
        dotRow.setGravity(Gravity.CENTER);
        dotRow.setPadding(0, Ui.dp(this, 10), 0, 0);
        for (int index = 0; index < BREATH_CYCLES; index++) {
            View dot = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(Ui.color(this, R.color.surface_alt));
            dot.setBackground(shape);
            dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(Ui.dp(this, 7), Ui.dp(this, 7));
            if (index > 0) dotParams.setMarginStart(Ui.dp(this, 6));
            dotRow.addView(dot, dotParams);
            dots[index] = dot;
        }
        card.addView(dotRow);

        breatheAgain = Ui.button(this, R.string.pause_breathe_again, Ui.ButtonKind.TONAL);
        breatheAgain.setVisibility(View.GONE);
        card.addView(breatheAgain, Ui.buttonParams(this));
        breatheAgain.setOnClickListener(view -> breathe());

        LinearLayout reminderPanel = Ui.softPanel(this);
        TextView reminder = Ui.text(this, getString(REMINDERS[0]), 15, R.color.text_secondary);
        reminder.setGravity(Gravity.CENTER);
        reminderPanel.addView(reminder);
        card.addView(reminderPanel, Ui.margins(this, 16, 0));

        TextView question = Ui.text(this, getString(R.string.pause_need_question), 15, R.color.text_primary);
        question.setTypeface(Ui.medium());
        question.setGravity(Gravity.CENTER);
        question.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 6));
        card.addView(question);

        TextView tip = Ui.text(this, "", 14, R.color.accent);
        tip.setGravity(Gravity.CENTER);
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
            card.addView(row, Ui.margins(this, 8, 0));
        }
        card.addView(tip);

        // Counting is a day and a number only. A recreated screen (rotation) is the same pause.
        SharedPreferences prefs = getSharedPreferences(PAUSE_PREFS, MODE_PRIVATE);
        PauseTally tally = PauseTally.record(prefs.getLong("day", -1), prefs.getInt("count", 0),
                prefs.getLong("at", 0), LocalDate.now().toEpochDay(), System.currentTimeMillis());
        if (count) {
            prefs.edit().putLong("day", tally.day).putInt("count", tally.count)
                    .putLong("at", tally.at).apply();
        }

        reminder.setText(REMINDERS[PauseTally.reminderFor(tally.count) - 1]);

        TextView today = tally.count == 1 ? Ui.caption(this, getString(R.string.pause_tally_first))
                : Ui.caption(this, getString(R.string.pause_tally_many, tally.count));
        today.setGravity(Gravity.CENTER);
        today.setPadding(0, Ui.dp(this, 16), 0, 0);
        card.addView(today);
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
        cycle = 0;
        updateDots(0);
        phase(true);
    }

    private void phase(boolean inhale) {
        long duration = inhale ? BREATH_IN_MS : BREATH_OUT_MS;
        breathLabel.setText(inhale ? R.string.pause_breathe_in : R.string.pause_breathe_out);
        breathStep.setText(getString(R.string.pause_breath_step, cycle + 1, BREATH_CYCLES));
        updateDots(cycle);
        if (ValueAnimator.areAnimatorsEnabled()) {
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
            breathLabel.setText(R.string.pause_breath_done);
            breatheAgain.setVisibility(View.VISIBLE);
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
