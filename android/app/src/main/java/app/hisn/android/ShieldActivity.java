package app.hisn.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import app.hisn.core.SelfProtectionScreens.Guard;

/**
 * Direct builds only: the page that takes the screen after the accessibility guard
 * leaves a removal, disable, Safe Mode or new-app-install screen.
 *
 * The guard already pressed BACK and HOME, so the dangerous screen is gone before
 * this page appears. This page explains what happened, holds the two ways forward
 * for a few seconds and then offers Hisn or the home screen. It never opens
 * Settings, hides nothing, and the system Home button always works. Nothing on
 * screen is read or stored; the reason is one of four fixed categories.
 */
public final class ShieldActivity extends Activity {
    public static final String EXTRA_GUARD = "guard";
    /** Long enough for the impulse to pass, short enough not to trap a person who needs the phone. */
    static final long HOLD_MILLIS = 8000;
    /** An untouched page must never hold the screen indefinitely. */
    static final long LEAVE_MILLIS = 60000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Guard guard = Guard.DISABLE;
    private long startedAt;
    private TextView countdown;
    private Button open;
    private Button home;
    private ProgressBar progress;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        guard = parse(getIntent());
        startedAt = SystemClock.elapsedRealtime();
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        build();
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::leave);
        }
        tick();
    }

    private static Guard parse(Intent intent) {
        String name = intent == null ? null : intent.getStringExtra(EXTRA_GUARD);
        if (name == null) return Guard.DISABLE;
        try { return Guard.valueOf(name); }
        catch (IllegalArgumentException unknown) { return Guard.DISABLE; }
    }

    private void build() {
        int side = Ui.sidePadding(this);
        int top = Ui.dp(this, 32);
        int bottom = Ui.dp(this, 36);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(Ui.color(this, R.color.background));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.START);
        column.setPadding(side, top, side, bottom);
        scroll.addView(column, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        View rule = new View(this);
        rule.setBackgroundColor(Ui.color(this, R.color.accent));
        column.addView(rule, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 2)));

        TextView eyebrow = Ui.eyebrow(this, getString(R.string.app_name));
        LinearLayout.LayoutParams eyebrowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        eyebrowParams.topMargin = Ui.dp(this, 14);
        column.addView(eyebrow, eyebrowParams);

        TextView title = Ui.display(this, getString(R.string.shield_title), 31);
        title.setGravity(Gravity.START);
        title.setPadding(0, Ui.dp(this, 8), 0, 0);
        column.addView(title);

        TextView body = Ui.text(this, getString(bodyFor(guard)), 15, R.color.text_secondary);
        body.setGravity(Gravity.START);
        body.setLineSpacing(Ui.dp(this, 4), 1.18f);
        body.setPadding(0, Ui.dp(this, 10), 0, 0);
        column.addView(body);

        LinearLayout card = Ui.softPanel(this);
        card.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 20));
        column.addView(card, Ui.margins(this, 22, 0));

        countdown = Ui.text(this, getString(R.string.shield_wait, (int) (HOLD_MILLIS / 1000)),
                17, R.color.text_primary);
        countdown.setTypeface(Ui.medium());
        countdown.setGravity(Gravity.CENTER);
        countdown.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(countdown);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgress(0);
        progress.setProgressTintList(ColorStateList.valueOf(Ui.color(this, R.color.accent)));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.color(this, R.color.surface_alt)));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 4));
        progressParams.topMargin = Ui.dp(this, 12);
        card.addView(progress, progressParams);

        open = Ui.button(this, R.string.shield_open, Ui.ButtonKind.PRIMARY);
        Ui.setEnabledWithAlpha(open, false);
        open.setOnClickListener(view -> openHisn());
        column.addView(open, Ui.buttonParams(this));

        home = Ui.button(this, R.string.shield_home, Ui.ButtonKind.GHOST);
        Ui.setEnabledWithAlpha(home, false);
        home.setOnClickListener(view -> goHome());
        column.addView(home, Ui.buttonParams(this));

        TextView note = Ui.caption(this, getString(R.string.shield_note));
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, Ui.dp(this, 18), 0, 0);
        column.addView(note);

        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            int barsTop = 0, barsBottom = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                barsTop = bars.top;
                barsBottom = bars.bottom;
            }
            column.setPadding(side, top + barsTop, side, bottom + barsBottom);
            return insets;
        });
        setContentView(scroll);
        Ui.applySystemBarIcons(getWindow(), this);
    }

    private static int bodyFor(Guard guard) {
        switch (guard) {
            case UNINSTALL: return R.string.shield_body_uninstall;
            case DISABLE: return R.string.shield_body_disable;
            case SAFE_MODE: return R.string.shield_body_safe_mode;
            case VPN: return R.string.shield_body_vpn;
            case INSTALL: return R.string.shield_body_install;
            default: return R.string.shield_body_disable;
        }
    }

    private void tick() {
        if (isFinishing() || isDestroyed()) return;
        long elapsed = SystemClock.elapsedRealtime() - startedAt;
        // Only an abandoned page still in front leaves by itself; one the person
        // already covered with Home stays where it is.
        if (elapsed >= LEAVE_MILLIS && hasWindowFocus()) {
            goHome();
            return;
        }
        long left = Math.max(0, HOLD_MILLIS - elapsed);
        if (left > 0) {
            Ui.setTextIfChanged(countdown, getString(R.string.shield_wait, (left + 999) / 1000));
        } else if (!open.isEnabled()) {
            Ui.setTextIfChanged(countdown, getString(R.string.shield_ready));
            Ui.setEnabledWithAlpha(open, true);
            Ui.setEnabledWithAlpha(home, true);
        }
        progress.setProgress((int) Math.min(1000, elapsed * 1000 / HOLD_MILLIS));
        handler.postDelayed(this::tick, 200);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        guard = parse(intent);
        startedAt = SystemClock.elapsedRealtime();
        recreate();
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

    /** Back is a way out only after the hold; either way it goes home, never back to Settings. */
    private void leave() {
        if (SystemClock.elapsedRealtime() - startedAt < HOLD_MILLIS) return;
        goHome();
    }

    @SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() {
        leave();
    }

    private void openHisn() {
        startActivity(new Intent(this, ShellActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    private void goHome() {
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }
}
