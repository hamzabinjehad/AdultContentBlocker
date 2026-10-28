package app.hisn.android;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
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
    private View circle;
    private TextView breathLabel;
    private Button breatheAgain;
    private int cycle;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = dp(28);
        layout.setPadding(padding, padding, padding, padding);
        TextView title = new TextView(this);
        title.setText(R.string.restriction_title);
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);
        TextView body = new TextView(this);
        body.setText(getIntent().getBooleanExtra("shortvideo", false) ? R.string.restriction_short_video
                : getIntent().getBooleanExtra("visual", false) ? R.string.restriction_visual
                : getIntent().getBooleanExtra("schedule", false) ? R.string.restriction_schedule
                : getIntent().getBooleanExtra("word", false) ? R.string.restriction_word : R.string.restriction_app);
        body.setTextSize(18);
        body.setGravity(Gravity.CENTER);
        layout.addView(body);
        Button home = new Button(this);
        home.setText(R.string.restriction_home);
        home.setOnClickListener(view -> goHome());
        layout.addView(home);
        addPause(layout, state == null);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(layout);
        // Android 15+ draws edge to edge: keep the title clear of the status bar and the last line above navigation.
        layout.setOnApplyWindowInsetsListener((view, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(padding, padding + top, padding, padding + bottom);
            return insets;
        });
        setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goHome);
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void addPause(LinearLayout layout, boolean count) {
        TextView heading = text(R.string.pause_title, 20);
        heading.setPadding(0, dp(28), 0, dp(12));
        layout.addView(heading);

        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(0x220866D8);
        ring.setStroke(dp(2), 0xFF0866D8);
        circle = new View(this);
        circle.setBackground(ring);
        circle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        float rest = ValueAnimator.areAnimatorsEnabled() ? 0.55f : 0.8f;
        circle.setScaleX(rest);
        circle.setScaleY(rest);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(110), dp(110));
        size.gravity = Gravity.CENTER_HORIZONTAL;
        layout.addView(circle, size);
        breathLabel = text(0, 17);
        breathLabel.setPadding(0, dp(12), 0, 0);
        layout.addView(breathLabel);
        breatheAgain = new Button(this);
        breatheAgain.setText(R.string.pause_breathe_again);
        breatheAgain.setOnClickListener(view -> breathe());
        layout.addView(breatheAgain);

        // Counting is a day and a number only. A recreated screen (rotation) is the same pause.
        SharedPreferences prefs = getSharedPreferences(PAUSE_PREFS, MODE_PRIVATE);
        PauseTally tally = PauseTally.record(prefs.getLong("day", -1), prefs.getInt("count", 0),
                prefs.getLong("at", 0), LocalDate.now().toEpochDay(), System.currentTimeMillis());
        if (count) prefs.edit().putLong("day", tally.day).putInt("count", tally.count).putLong("at", tally.at).apply();

        TextView reminder = text(REMINDERS[PauseTally.reminderFor(tally.count) - 1], 16);
        reminder.setPadding(0, dp(16), 0, dp(16));
        layout.addView(reminder);
        layout.addView(text(R.string.pause_need_question, 16));
        TextView tip = text(0, 15);
        tip.setVisibility(View.GONE);
        tip.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        for (int[] need : NEEDS) {
            Button choice = new Button(this);
            choice.setText(need[0]);
            choice.setOnClickListener(view -> {
                tip.setText(need[1]);
                tip.setVisibility(View.VISIBLE);
            });
            layout.addView(choice);
        }
        layout.addView(tip);
        TextView today = tally.count == 1 ? text(R.string.pause_tally_first, 14) : text(0, 14);
        if (tally.count != 1) today.setText(getString(R.string.pause_tally_many, tally.count));
        today.setPadding(0, dp(16), 0, 0);
        layout.addView(today);
        breathe();
    }

    /** Three breaths; with animations turned off in Android settings, the circle stays still. */
    private void breathe() {
        handler.removeCallbacksAndMessages(null);
        breatheAgain.setVisibility(View.GONE);
        cycle = 0;
        phase(true);
    }

    private void phase(boolean inhale) {
        long duration = inhale ? BREATH_IN_MS : BREATH_OUT_MS;
        breathLabel.setText(inhale ? R.string.pause_breathe_in : R.string.pause_breathe_out);
        if (ValueAnimator.areAnimatorsEnabled()) {
            float scale = inhale ? 1f : 0.55f;
            circle.animate().scaleX(scale).scaleY(scale).setDuration(duration).start();
        }
        handler.postDelayed(() -> {
            if (inhale) { phase(false); return; }
            if (++cycle < BREATH_CYCLES) { phase(true); return; }
            breathLabel.setText(R.string.pause_breath_done);
            breatheAgain.setVisibility(View.VISIBLE);
        }, duration);
    }

    private TextView text(int resource, int size) {
        TextView view = new TextView(this);
        if (resource != 0) view.setText(resource);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void goHome() {
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

}
