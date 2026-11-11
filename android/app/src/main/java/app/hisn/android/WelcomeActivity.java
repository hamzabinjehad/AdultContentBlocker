package app.hisn.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A short first-run introduction in the same shape as the screen-time apps people
 * already know: one idea per screen, then one permission card per Android grant,
 * each with a single full-width action.
 *
 * <p>The page grants nothing by itself. A card opens the exact Android screen or
 * starts the one service it names; returning to this page re-reads the platform
 * state. The last page, present only in the direct build, offers the opt-in
 * uninstall guard in one tap. Skipping is always available.
 */
public final class WelcomeActivity extends Activity {
    private static final String PREFS = "welcome";
    private static final String SEEN = "seen";
    private static final int PAGE_INTROS = 3;
    private static final int PAGE_PERMISSIONS = 3;
    private static final int PAGE_LOCK = 4;
    private static final int REQUEST_VPN = 900;
    private static final int REQUEST_NOTIFICATIONS = 902;

    private static final int[] INTRO_ICONS = {R.drawable.ic_shield, R.drawable.ic_clock, R.drawable.ic_lock};
    private static final int[] INTRO_TITLES = {R.string.welcome_title_1, R.string.welcome_title_2,
            R.string.welcome_title_3};
    private static final int[] INTRO_BODIES = {R.string.welcome_body_1, R.string.welcome_body_2,
            R.string.welcome_body_3};

    private int page;
    private LinearLayout content;
    private LinearLayout dotRow;
    private Button next;
    private final List<View> dots = new ArrayList<>();
    private final List<PermissionCard> permissionCards = new ArrayList<>();
    private PolicyStore settings;

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
        try { settings = PolicyStore.get(this); }
        catch (RuntimeException unavailable) { settings = null; }

        int side = Ui.sidePadding(this);
        int top = Ui.dp(this, 20);
        int bottom = Ui.dp(this, 20);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_welcome);
        root.setPadding(side, top, side, bottom);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = Ui.display(this, getString(R.string.app_name), 22);
        brand.setTextColor(Ui.color(this, R.color.text_primary));
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        header.addView(brand, brandParams);
        Button skip = Ui.button(this, R.string.welcome_skip, Ui.ButtonKind.QUIET);
        skip.setTextSize(14);
        skip.setMinHeight(Ui.dp(this, 48));
        skip.setMinimumHeight(Ui.dp(this, 48));
        skip.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        skip.setOnClickListener(view -> finishWelcome());
        header.addView(skip);
        root.addView(header);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        content.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 20));
        scroller.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroller, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        dotRow = new LinearLayout(this);
        dotRow.setOrientation(LinearLayout.HORIZONTAL);
        dotRow.setGravity(Gravity.CENTER);
        dotRow.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 12));
        for (int index = 0; index < pageCount(); index++) {
            View dot = new View(this);
            dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    Ui.dp(this, index == page ? 28 : 10), Ui.dp(this, 5));
            if (index > 0) params.setMarginStart(Ui.dp(this, 6));
            dotRow.addView(dot, params);
            dots.add(dot);
        }
        root.addView(dotRow);

        next = Ui.button(this, R.string.welcome_next, Ui.ButtonKind.PRIMARY);
        next.setTextSize(16);
        next.setMinHeight(Ui.dp(this, 56));
        next.setMinimumHeight(Ui.dp(this, 56));
        next.setOnClickListener(view -> advance());
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

    @Override protected void onResume() {
        super.onResume();
        refreshPermissionCards();
        refreshLockState();
    }

    @SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { back(); }

    private int pageCount() {
        return BuildConfig.SELF_PROTECTION ? PAGE_LOCK + 1 : PAGE_PERMISSIONS + 1;
    }

    private void back() {
        if (page > 0) render(page - 1);
        else finishWelcome();
    }

    private void advance() {
        if (page < pageCount() - 1) render(page + 1);
        else if (page == PAGE_LOCK) enableGuardAndFinish();
        else finishWelcome();
    }

    private void finishWelcome() {
        markSeen(this);
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("page", 0));
        finish();
    }

    private void render(int newPage) {
        page = newPage;
        content.removeAllViews();
        permissionCards.clear();
        if (page < PAGE_INTROS) renderIntro();
        else if (page == PAGE_PERMISSIONS) renderPermissions();
        else renderLock();
        updateDots();
        next.setText(getString(page == PAGE_LOCK ? R.string.welcome_lock_action
                : page == PAGE_PERMISSIONS ? R.string.welcome_perm_continue : R.string.welcome_next));
        content.setAlpha(0f);
        content.animate().alpha(1f).setDuration(180).start();
        content.announceForAccessibility(getString(R.string.welcome_page, page + 1, pageCount()));
    }

    private void updateDots() {
        for (int index = 0; index < dots.size(); index++) {
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(Ui.color(this, index == page ? R.color.accent : R.color.divider));
            shape.setCornerRadius(Ui.dp(this, 100));
            dots.get(index).setBackground(shape);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) dots.get(index).getLayoutParams();
            params.width = Ui.dp(this, index == page ? 28 : 10);
            dots.get(index).setLayoutParams(params);
        }
        dotRow.setContentDescription(getString(R.string.welcome_page, page + 1, pageCount()));
    }

    // ------------------------------------------------------------------ intro

    private void renderIntro() {
        ImageView icon = Ui.iconInTile(this, INTRO_ICONS[page], R.color.accent, 64, 30);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 64), Ui.dp(this, 64));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(icon, iconParams);

        TextView title = Ui.display(this, getString(INTRO_TITLES[page]), 32);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = Ui.dp(this, 26);
        content.addView(title, titleParams);

        TextView body = Ui.text(this, getString(INTRO_BODIES[page]), 15.5f, R.color.text_secondary);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(Ui.dp(this, 4), 1.2f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = Ui.dp(this, 14);
        content.addView(body, bodyParams);
    }

    // ------------------------------------------------------------------ permissions

    private void renderPermissions() {
        content.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        TextView title = Ui.display(this, getString(R.string.welcome_perm_title), 30);
        title.setGravity(Gravity.START);
        content.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView body = Ui.body(this, getString(R.string.welcome_perm_body));
        body.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 16));
        content.addView(body);

        addPermissionCard(R.drawable.ic_shield, R.string.perm_access_title, R.string.perm_access_body,
                R.string.perm_access_action, () -> Permissions.accessibility(this),
                () -> openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                        R.string.perm_access_hint));
        addPermissionCard(R.drawable.ic_network, R.string.perm_dns_title, R.string.perm_dns_body,
                R.string.perm_dns_action, Permissions::dnsRunning, this::requestDns);
        addPermissionCard(R.drawable.ic_clock, R.string.perm_usage_title, R.string.perm_usage_body,
                R.string.perm_usage_action, () -> Permissions.usage(this),
                () -> openSettings(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), 0));
        addPermissionCard(R.drawable.ic_apps, R.string.perm_overlay_title, R.string.perm_overlay_body,
                R.string.perm_overlay_action, () -> Permissions.overlay(this), this::requestOverlay);
        addPermissionCard(R.drawable.ic_alert, R.string.perm_notify_title, R.string.perm_notify_body,
                R.string.perm_notify_action, () -> Permissions.notifications(this),
                this::requestNotifications);
        addPermissionCard(R.drawable.ic_pulse, R.string.perm_battery_title, R.string.perm_battery_body,
                R.string.perm_battery_action, () -> Permissions.battery(this), this::requestBattery);

        TextView note = Ui.caption(this, getString(R.string.welcome_perm_note));
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, Ui.dp(this, 8), 0, 0);
        content.addView(note);
    }

    private void addPermissionCard(int icon, int titleRes, int bodyRes, int actionRes,
            BooleanSupplier granted, Runnable action) {
        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = Ui.dp(this, 12);
        content.addView(card, cardParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.iconInTile(this, icon, R.color.accent, 44, 24));
        TextView title = Ui.strong(this, getString(titleRes));
        title.setTextSize(18);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMarginStart(Ui.dp(this, 12));
        header.addView(title, titleParams);
        ImageView check = Ui.icon(this, R.drawable.ic_check, R.color.success, 22);
        header.addView(check);
        card.addView(header);

        TextView body = Ui.body(this, getString(bodyRes));
        body.setPadding(0, Ui.dp(this, 10), 0, 0);
        card.addView(body);

        Button button = Ui.button(this, getString(actionRes), Ui.ButtonKind.PRIMARY);
        button.setOnClickListener(view -> action.run());
        card.addView(button, Ui.buttonParams(this));

        PermissionCard permission = new PermissionCard(button, check, actionRes, granted);
        permission.refresh();
        permissionCards.add(permission);
    }

    private void refreshPermissionCards() {
        for (PermissionCard card : permissionCards) card.refresh();
    }

    private final class PermissionCard {
        private final Button button;
        private final ImageView check;
        private final int actionRes;
        private final BooleanSupplier granted;

        PermissionCard(Button button, ImageView check, int actionRes, BooleanSupplier granted) {
            this.button = button;
            this.check = check;
            this.actionRes = actionRes;
            this.granted = granted;
        }

        void refresh() {
            boolean on;
            try { on = granted.getAsBoolean(); }
            catch (RuntimeException unavailable) { on = false; }
            check.setVisibility(on ? View.VISIBLE : View.GONE);
            button.setText(getString(on ? R.string.perm_on : actionRes));
            Ui.style(button, on ? Ui.ButtonKind.GHOST : Ui.ButtonKind.PRIMARY);
            button.setAlpha(1f);
        }
    }

    private void openSettings(Intent intent, int hintRes) {
        try {
            startActivity(intent);
            if (hintRes != 0) Toast.makeText(this, hintRes, Toast.LENGTH_LONG).show();
        } catch (RuntimeException unavailable) {
            Toast.makeText(this, R.string.perm_settings_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void requestOverlay() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException unavailable) {
            openSettings(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION), 0);
        }
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
            return;
        }
        if (!Compat.openNotificationSettings(this)) {
            Toast.makeText(this, R.string.perm_settings_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void requestBattery() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException unavailable) {
            openSettings(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), 0);
        }
    }

    private void requestDns() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 8));
        form.addView(Ui.body(this, getString(R.string.dns_consent_short)));
        form.addView(Ui.disclosure(this, getString(R.string.full_disclosure),
                getString(R.string.dns_consent_body)).root, Ui.margins(this, 8, 0));
        new AlertDialog.Builder(this).setTitle(R.string.dns_consent_title).setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.dns_continue, (dialog, which) -> continueDns()).show();
    }

    private void continueDns() {
        Intent consent = VpnService.prepare(this);
        if (consent != null) {
            try { startActivityForResult(consent, REQUEST_VPN); }
            catch (RuntimeException unavailable) {
                Toast.makeText(this, R.string.perm_settings_unavailable, Toast.LENGTH_LONG).show();
            }
        } else {
            startDnsService();
        }
    }

    private void startDnsService() {
        try { Compat.startForegroundService(this, new Intent(this, DnsVpnService.class)); }
        catch (RuntimeException unavailable) {
            Toast.makeText(this, R.string.perm_dns_failed, Toast.LENGTH_LONG).show();
        }
        refreshPermissionCards();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VPN && resultCode == RESULT_OK) startDnsService();
        else refreshPermissionCards();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_NOTIFICATIONS) refreshPermissionCards();
    }

    // ------------------------------------------------------------------ lock

    private TextView lockState;
    private Button lockLater;

    private void renderLock() {
        content.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        ImageView icon = Ui.iconInTile(this, R.drawable.ic_lock, R.color.accent, 72, 34);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 72), Ui.dp(this, 72));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(icon, iconParams);

        TextView title = Ui.display(this, getString(R.string.welcome_lock_title), 30);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = Ui.dp(this, 24);
        content.addView(title, titleParams);

        TextView body = Ui.body(this, getString(R.string.welcome_lock_body));
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 18));
        content.addView(body);

        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = Ui.dp(this, 8);
        content.addView(card, cardParams);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.iconInTile(this, R.drawable.ic_lock, R.color.accent, 40, 21));
        TextView cardTitle = Ui.strong(this, getString(R.string.welcome_lock_card_title));
        cardTitle.setTextSize(18);
        LinearLayout.LayoutParams cardTitleParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        cardTitleParams.setMarginStart(Ui.dp(this, 12));
        header.addView(cardTitle, cardTitleParams);
        lockState = Ui.text(this, "", 15, R.color.accent);
        lockState.setTypeface(Ui.medium());
        header.addView(lockState);
        card.addView(header);
        TextView cardBody = Ui.body(this, getString(R.string.welcome_lock_card_body));
        cardBody.setPadding(0, Ui.dp(this, 10), 0, 0);
        card.addView(cardBody);
        TextView requires = Ui.caption(this, getString(R.string.welcome_lock_requires));
        requires.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(requires);

        lockLater = Ui.button(this, R.string.welcome_lock_skip, Ui.ButtonKind.QUIET);
        lockLater.setOnClickListener(view -> finishWelcome());
        content.addView(lockLater, Ui.buttonParams(this));
        refreshLockState();
    }

    private void refreshLockState() {
        if (lockState == null) return;
        boolean on = false;
        try { on = settings != null && settings.uninstallLock(); }
        catch (RuntimeException unreadable) { /* shown as off */ }
        lockState.setText(getString(on ? R.string.perm_on : R.string.perm_off));
        lockState.setTextColor(Ui.color(this, on ? R.color.success : R.color.text_tertiary));
        if (next != null) {
            next.setText(getString(on ? R.string.welcome_lock_finish : R.string.welcome_lock_action));
        }
        if (lockLater != null) lockLater.setVisibility(on ? View.GONE : View.VISIBLE);
    }

    private void enableGuardAndFinish() {
        boolean enabled = false;
        if (settings != null) {
            try {
                settings.updateUninstallLock(true);
                enabled = true;
            } catch (RuntimeException refused) {
                Toast.makeText(this, R.string.policy_unavailable, Toast.LENGTH_LONG).show();
            }
        }
        if (enabled && !Permissions.accessibility(this)) {
            Toast.makeText(this, R.string.welcome_lock_needs_access, Toast.LENGTH_LONG).show();
        }
        finishWelcome();
    }
}
