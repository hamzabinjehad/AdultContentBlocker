package app.hisn.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * A short, skippable first-run explanation. It uses the same three ideas as the
 * rest of the product: block the paths you chose, make opening deliberate, and
 * freeze the choice when you want. It changes no rule and grants no permission;
 * finishing opens Rules, skipping opens Protection.
 *
 * <p>Shown only when the app has no configured rules and the welcome has not
 * been seen. Existing installs with rules go straight to the dashboard.
 */
public final class WelcomeActivity extends Activity {
    private static final String PREFS = "welcome";
    private static final String SEEN = "seen";
    private static final int PAGE_COUNT = 3;
    private static final int[] ICONS = {R.drawable.ic_shield, R.drawable.ic_clock, R.drawable.ic_lock};
    private static final int[] TITLES = {R.string.welcome_title_1, R.string.welcome_title_2,
            R.string.welcome_title_3};
    private static final int[] BODIES = {R.string.welcome_body_1, R.string.welcome_body_2,
            R.string.welcome_body_3};

    private int page;
    private LinearLayout content;
    private LinearLayout dotRow;
    private Button next;
    private final View[] dots = new View[PAGE_COUNT];

    static boolean shouldShow(Context context, PolicyStore settings) {
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SEEN, false)) return false;
        try {
            return settings != null && !settings.snapshot().hasConfiguredRestrictions();
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    static void markSeen(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(SEEN, true).apply();
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        int side = Ui.sidePadding(this);
        int top = Ui.dp(this, 24);
        int bottom = Ui.dp(this, 24);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.color(this, R.color.background));
        root.setPadding(side, top, side, bottom);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = Ui.strong(this, getString(R.string.app_name));
        brand.setTextColor(Ui.color(this, R.color.accent));
        brand.setTextSize(18);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        header.addView(brand, brandParams);
        Button skip = Ui.button(this, R.string.welcome_skip, Ui.ButtonKind.QUIET);
        skip.setTextSize(14);
        skip.setMinHeight(Ui.dp(this, 40));
        skip.setMinimumHeight(Ui.dp(this, 40));
        skip.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        skip.setOnClickListener(view -> finishWelcome(false));
        header.addView(skip);
        root.addView(header);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        content.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 24));
        scroller.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroller, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        dotRow = new LinearLayout(this);
        dotRow.setOrientation(LinearLayout.HORIZONTAL);
        dotRow.setGravity(Gravity.CENTER);
        dotRow.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        for (int index = 0; index < PAGE_COUNT; index++) {
            View dot = new View(this);
            dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8));
            if (index > 0) params.setMarginStart(Ui.dp(this, 8));
            dotRow.addView(dot, params);
            dots[index] = dot;
        }
        root.addView(dotRow);

        next = Ui.button(this, R.string.welcome_next, Ui.ButtonKind.PRIMARY);
        next.setTextSize(16);
        next.setMinHeight(Ui.dp(this, 56));
        next.setMinimumHeight(Ui.dp(this, 56));
        next.setOnClickListener(view -> {
            if (page < PAGE_COUNT - 1) render(page + 1);
            else finishWelcome(true);
        });
        root.addView(next, Ui.buttonParams(this));

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int barsTop = 0, barsBottom = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                barsTop = bars.top;
                barsBottom = bars.bottom;
            }
            root.setPadding(side, top + barsTop, side, bottom + barsBottom);
            return insets;
        });
        setContentView(root);
        Ui.applySystemBarIcons(getWindow(), this);
        render(0);

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::back);
        }
    }

    @SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { back(); }

    private void back() {
        if (page > 0) render(page - 1);
        else finishWelcome(false);
    }

    private void finishWelcome(boolean setup) {
        markSeen(this);
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("page", setup ? 1 : 0));
        finish();
    }

    private void render(int newPage) {
        page = newPage;
        content.removeAllViews();
        ImageView icon = Ui.iconInTile(this, ICONS[page], R.color.accent, 128, 72);
        // CENTER_INSIDE leaves a 24dp vector at its intrinsic size; FIT_CENTER
        // scales it up into the tile's content box.
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 128), Ui.dp(this, 128));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(icon, iconParams);

        TextView title = Ui.text(this, getString(TITLES[page]), 28, R.color.text_primary);
        title.setTypeface(Ui.medium());
        title.setGravity(Gravity.CENTER);
        title.setLineSpacing(Ui.dp(this, 2), 1.05f);
        Ui.heading(title);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = Ui.dp(this, 24);
        content.addView(title, titleParams);

        TextView body = Ui.text(this, getString(BODIES[page]), 16, R.color.text_secondary);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(Ui.dp(this, 3), 1.15f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = Ui.dp(this, 12);
        content.addView(body, bodyParams);

        for (int index = 0; index < PAGE_COUNT; index++) {
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(Ui.color(this, index == page ? R.color.accent : R.color.divider));
            dots[index].setBackground(shape);
        }
        dotRow.setContentDescription(getString(R.string.welcome_page, page + 1, PAGE_COUNT));
        next.setText(getString(page == PAGE_COUNT - 1 ? R.string.welcome_finish : R.string.welcome_next));
        content.setAlpha(0f);
        content.animate().alpha(1f).setDuration(180).start();
        content.announceForAccessibility(getString(R.string.welcome_page, page + 1, PAGE_COUNT)
                + ". " + getString(TITLES[page]));
    }
}
