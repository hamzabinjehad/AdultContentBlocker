package app.hisn.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import app.hisn.core.BrowserCoverage;
import app.hisn.core.AppTimeLimit;
import app.hisn.core.DailyAppSchedule;
import app.hisn.core.GuardHealth;
import app.hisn.core.PolicyChangeError;
import app.hisn.core.ProtectionLock;
import app.hisn.core.SeedFreshness;
import app.hisn.core.SetupGuide;
import app.hisn.core.UninstallProtection;
import app.hisn.core.WordMatcher;
import app.hisn.core.WordRuleList;

/**
 * Development preview with explicit consent for experimental DNS filtering.
 *
 * <p>The window is three pages behind a bottom navigation bar. The Protection page leads with the
 * current state, then a short list of anything that needs attention, then the layers themselves.
 * Rules holds every switch and list. Guide holds the reasoning, limits, diagnostics, local tester
 * and notices. All enforcement and consent logic is unchanged; this class only presents it.
 */
public final class MainActivity extends Activity {
    private static final int TEXT_LIMIT = 65536;
    private static final int MAX_RULE_LINES = 1000;
    private static final int PAGE_PROTECTION = 0;
    private static final int PAGE_RULES = 1;
    private static final int PAGE_GUIDE = 2;
    private static final int PAGE_COUNT = 3;
    private static final long REFRESH_MS = 2000;

    private LinearLayout shell;
    private LinearLayout appBarRow;
    private FrameLayout pageHost;
    private View navBar;
    private LinearLayout navRow;
    private final ScrollView[] pages = new ScrollView[PAGE_COUNT];
    private final LinearLayout[] columns = new LinearLayout[PAGE_COUNT];
    private final LinearLayout[] navItems = new LinearLayout[PAGE_COUNT];
    private final FrameLayout[] navWraps = new FrameLayout[PAGE_COUNT];
    private final ImageView[] navIcons = new ImageView[PAGE_COUNT];
    private final TextView[] navLabels = new TextView[PAGE_COUNT];
    private int selectedPage;
    private int sidePadding;

    private TextView statusTitle;
    private TextView statusBody;
    private ImageView heroIcon;
    private Ui.LayerTile layerDns;
    private Ui.LayerTile layerApps;
    private Ui.LayerTile layerImages;
    private LinearLayout attentionCard;
    private LinearLayout attentionList;
    private Ui.Disclosure attentionDetails;
    private String attentionSignature = "";
    private LinearLayout restoredCard;
    private Button restoredConfirm;

    private TextView setupState;
    private Button setupNext;
    private TextView networkState;
    private TextView seedFreshness;
    private TextView networkCheckState;
    private TextView watchdogState;
    private TextView networkStats;
    private Button startDns;
    private Button checkNetwork;
    private Button notifSettings;
    private Button privateDnsSettings;

    private TextView textState;
    private TextView visualState;
    private TextView shortVideoState;
    private TextView grayState;
    private TextView commitmentState;
    private TextView uninstallState;
    private TextView managedState;
    private Button uninstallAction;

    private Switch adultToggle;
    private Switch visualToggle;
    private Switch shortVideoToggle;
    private Switch grayToggle;
    private Switch safeSearchToggle;
    private Ui.SettingRow domainsRow;
    private Ui.SettingRow wordsRow;
    private Ui.SettingRow blockedRow;
    private Ui.SettingRow scannedRow;
    private Ui.SettingRow browserRow;
    private Button scheduleEdit;
    private TextView timeLimitState;
    private Button timeLimitEdit;
    private LinearLayout rulesLockCard;
    private TextView rulesLockText;
    private LinearLayout setupCard;
    private Ui.Disclosure dnsAdvanced;
    private TextView batteryState;
    private Button batteryAction;
    private TextView scheduleState;
    private TextView commitmentStateRules;
    private TextView commitmentNote;
    private TextView adminState;

    private EditText testerRule;
    private EditText testerSample;
    private TextView testerResult;
    private SetupGuide.Step currentSetupStep = SetupGuide.Step.CHOOSE_RULES;

    /** Open dialogs showing the person's lists, whether the word tester shows their text, and whether
     * this window currently has FLAG_SECURE. */
    private int privateDialogs;
    private boolean testerPrivate, secured;
    private PolicyStore settings;
    private long lastUninstallSyncAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            refreshStatus();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        // Edge to edge (targetSdk 35+ on Android 15+) leaves the keyboard to the app: the page is
        // padded by the keyboard inset and the shrinking page scrolls the focused field into view.
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        try { settings = PolicyStore.get(this); }
        catch (RuntimeException unavailable) { settings = null; }

        // A fresh install gets one short, skippable explanation before the dashboard.
        // Rules already saved mean this is an upgrade or a configured install; no welcome.
        if (savedInstanceState == null && WelcomeActivity.shouldShow(this, settings)) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }

        buildShell();
        setContentView(shell);
        applyInsets();
        Ui.applySystemBarIcons(getWindow(), this);
        syncShortcuts();
        // Keep the background layer check alive for the choices already saved:
        // a DNS session the owner left on, or the direct build's keep switch.
        try { LayerWatchdog.syncSchedule(this); }
        catch (RuntimeException unavailable) { /* The next open retries. */ }

        int page = savedInstanceState == null ? intentPage(getIntent())
                : savedInstanceState.getInt("selectedPage", PAGE_PROTECTION);
        showPage(page, false);
        registerBackHandling();
        refreshRuleSummaries();
        refreshStatus();
    }

    // ------------------------------------------------------------------ chrome

    private void buildShell() {
        sidePadding = Ui.sidePadding(this);
        shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Ui.color(this, R.color.background));

        LinearLayout appBar = new LinearLayout(this);
        appBar.setOrientation(LinearLayout.VERTICAL);
        appBar.setBackgroundColor(Ui.color(this, R.color.background));
        appBarRow = new LinearLayout(this);
        appBarRow.setOrientation(LinearLayout.HORIZONTAL);
        appBarRow.setGravity(Gravity.CENTER_VERTICAL);
        appBarRow.setPadding(sidePadding, Ui.dp(this, 10), sidePadding, Ui.dp(this, 10));
        appBarRow.addView(Ui.iconInTile(this, R.drawable.ic_shield, R.color.accent, 40, 24));
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, getString(R.string.title), 19, R.color.text_primary);
        title.setTypeface(Ui.medium());
        TextView subtitle = Ui.text(this, getString(R.string.subtitle, BuildConfig.VERSION_NAME), 12,
                R.color.text_tertiary);
        brand.addView(title);
        brand.addView(subtitle);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        brandParams.setMarginStart(Ui.dp(this, 12));
        appBarRow.addView(brand, brandParams);
        appBar.addView(appBarRow);
        appBar.addView(Ui.divider(this));
        shell.addView(appBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        pageHost = new FrameLayout(this);
        shell.addView(pageHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        buildPages();
        buildNavigation();
    }

    private void buildPages() {
        int[] scrollIds = {R.id.page_protection, R.id.page_rules, R.id.page_guide};
        for (int index = 0; index < PAGE_COUNT; index++) {
            ScrollView scroll = new ScrollView(this);
            scroll.setId(scrollIds[index]);
            scroll.setFillViewport(true);
            scroll.setClipToPadding(false);
            scroll.setSaveEnabled(true);
            scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setPadding(sidePadding, Ui.dp(this, 8), sidePadding, Ui.dp(this, 28));
            scroll.addView(column, new ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
            pageHost.addView(scroll, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            pages[index] = scroll;
            columns[index] = column;
        }
        buildProtectionPage(columns[PAGE_PROTECTION]);
        buildRulesPage(columns[PAGE_RULES]);
        buildGuidePage(columns[PAGE_GUIDE]);
    }

    private void buildNavigation() {
        navBar = new LinearLayout(this);
        ((LinearLayout) navBar).setOrientation(LinearLayout.VERTICAL);
        navBar.setBackgroundColor(Ui.color(this, R.color.surface));
        navBar.setElevation(Ui.dp(this, 10));
        ((LinearLayout) navBar).addView(Ui.divider(this));
        navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setPadding(sidePadding, Ui.dp(this, 4), sidePadding, Ui.dp(this, 4));
        int[] icons = {R.drawable.ic_shield, R.drawable.ic_tune, R.drawable.ic_book};
        int[] labels = {R.string.nav_protection, R.string.nav_rules, R.string.nav_guide};
        for (int index = 0; index < PAGE_COUNT; index++) {
            final int page = index;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(Ui.dp(this, 4), Ui.dp(this, 7), Ui.dp(this, 4), Ui.dp(this, 7));
            item.setBackgroundResource(R.drawable.bg_row);
            item.setClickable(true);
            item.setFocusable(true);
            item.setMinimumHeight(Ui.dp(this, 58));
            item.setContentDescription(getString(labels[index]));
            FrameLayout wrap = new FrameLayout(this);
            wrap.setBackgroundResource(0);
            ImageView icon = Ui.icon(this, icons[index], R.color.text_secondary, 21);
            wrap.addView(icon, new FrameLayout.LayoutParams(Ui.dp(this, 21), Ui.dp(this, 21), Gravity.CENTER));
            LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 28));
            item.addView(wrap, wrapParams);
            TextView label = Ui.text(this, getString(labels[index]), 12, R.color.text_secondary);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            label.setPadding(0, Ui.dp(this, 2), 0, 0);
            item.addView(label);
            item.setOnClickListener(view -> showPage(page, true));
            navItems[index] = item;
            navWraps[index] = wrap;
            navIcons[index] = icon;
            navLabels[index] = label;
            navRow.addView(item, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
        ((LinearLayout) navBar).addView(navRow);
        shell.addView(navBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void applyInsets() {
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars()
                                | android.view.WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = insets.getInsets(android.view.WindowInsets.Type.ime());
                view.setPadding(bars.left, 0, bars.right, 0);
                appBarRow.setPadding(sidePadding, Ui.dp(this, 10) + bars.top,
                        sidePadding, Ui.dp(this, 10));
                boolean keyboard = ime.bottom > 0;
                navBar.setVisibility(keyboard ? View.GONE : View.VISIBLE);
                navRow.setPadding(sidePadding, Ui.dp(this, 4), sidePadding,
                        Ui.dp(this, 4) + (keyboard ? 0 : bars.bottom));
                pageHost.setPadding(0, 0, 0, keyboard ? ime.bottom : 0);
            }
            return insets;
        });
        shell.requestApplyInsets();
    }

    private void syncShortcuts() {
        try {
            ShortcutManager manager = getSystemService(ShortcutManager.class);
            if (manager == null) return;
            List<ShortcutInfo> shortcuts = new ArrayList<>();
            shortcuts.add(shortcut("rules", R.string.shortcut_rules_short, R.string.shortcut_rules_long,
                    R.drawable.ic_tune, PAGE_RULES));
            shortcuts.add(shortcut("guide", R.string.shortcut_guide_short, R.string.shortcut_guide_long,
                    R.drawable.ic_book, PAGE_GUIDE));
            manager.setDynamicShortcuts(shortcuts);
        } catch (RuntimeException unavailable) {
            // A launcher without shortcut support, or a rate limit: the app still opens normally.
        }
    }

    private ShortcutInfo shortcut(String id, int shortLabel, int longLabel, int icon, int page) {
        return new ShortcutInfo.Builder(this, id)
                .setShortLabel(getString(shortLabel))
                .setLongLabel(getString(longLabel))
                .setIcon(Icon.createWithResource(this, icon))
                .setIntent(new Intent(this, MainActivity.class).setAction(Intent.ACTION_VIEW)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra("page", page))
                .build();
    }

    private int intentPage(Intent intent) {
        if (intent == null) return PAGE_PROTECTION;
        int page = intent.getIntExtra("page", PAGE_PROTECTION);
        return Math.max(0, Math.min(PAGE_COUNT - 1, page));
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showPage(intentPage(intent), true);
    }

    private void showPage(int index, boolean animate) {
        int next = Math.max(0, Math.min(PAGE_COUNT - 1, index));
        boolean changes = next != selectedPage;
        selectedPage = next;
        for (int i = 0; i < PAGE_COUNT; i++) {
            boolean visible = i == selectedPage;
            pages[i].setVisibility(visible ? View.VISIBLE : View.GONE);
            navItems[i].setSelected(visible);
            navWraps[i].setBackgroundResource(visible ? R.drawable.bg_nav_selected : 0);
            navIcons[i].setColorFilter(Ui.color(this, visible ? R.color.accent : R.color.text_secondary));
            navLabels[i].setTextColor(Ui.color(this, visible ? R.color.accent : R.color.text_secondary));
            navLabels[i].setTypeface(visible ? Ui.medium() : Typeface.create("sans-serif", Typeface.NORMAL));
        }
        if (animate && changes && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            View page = pages[selectedPage];
            page.setAlpha(0f);
            page.animate().alpha(1f).setDuration(140).start();
        } else {
            pages[selectedPage].setAlpha(1f);
        }
        updateTesterPrivacy();
        if (selectedPage == PAGE_RULES) refreshRuleSummaries();
    }

    private boolean handleBack() {
        if (selectedPage != PAGE_PROTECTION) {
            showPage(PAGE_PROTECTION, true);
            return true;
        }
        return false;
    }

    /** Android 13+ uses the predictive-back dispatcher; older versions still call onBackPressed. */
    private void registerBackHandling() {
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> { if (!handleBack()) finish(); });
        }
    }

    @SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() {
        if (!handleBack()) super.onBackPressed();
    }

    // ------------------------------------------------------------------ protection page

    private void buildProtectionPage(LinearLayout page) {
        restoredCard = Ui.card(this);
        restoredCard.setBackgroundResource(R.drawable.bg_warning_card);
        LinearLayout restoredRow = new LinearLayout(this);
        restoredRow.setOrientation(LinearLayout.HORIZONTAL);
        restoredRow.setGravity(Gravity.TOP);
        restoredRow.addView(Ui.icon(this, R.drawable.ic_alert, R.color.warning, 22));
        TextView restoredText = Ui.body(this, getString(R.string.settings_restored));
        LinearLayout.LayoutParams restoredTextParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        restoredTextParams.setMarginStart(Ui.dp(this, 12));
        restoredRow.addView(restoredText, restoredTextParams);
        restoredCard.addView(restoredRow);
        restoredConfirm = Ui.button(this, R.string.settings_restored_confirm, Ui.ButtonKind.TONAL);
        restoredCard.addView(restoredConfirm, Ui.buttonParams(this));
        restoredCard.setVisibility(View.GONE);
        page.addView(restoredCard, Ui.margins(this, 0, 10));
        restoredConfirm.setOnClickListener(view -> {
            try { settings.confirmRestored(); } catch (RuntimeException refused) { showRefusal(refused); }
            refreshStatus();
        });

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackgroundResource(R.drawable.bg_hero);
        hero.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 16));
        LinearLayout heroTop = new LinearLayout(this);
        heroTop.setOrientation(LinearLayout.HORIZONTAL);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);
        heroIcon = Ui.iconInTile(this, R.drawable.ic_shield, R.color.accent, 44, 26);
        heroTop.addView(heroIcon);
        statusTitle = Ui.text(this, getString(R.string.status_title), 20, R.color.text_primary);
        statusTitle.setTypeface(Ui.medium());
        statusTitle.setLineSpacing(Ui.dp(this, 2), 1.05f);
        statusTitle.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams statusTitleParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        statusTitleParams.setMarginStart(Ui.dp(this, 12));
        heroTop.addView(statusTitle, statusTitleParams);
        hero.addView(heroTop);
        statusBody = Ui.caption(this, "");
        hero.addView(statusBody, Ui.margins(this, 0, 2));

        LinearLayout layerRow = new LinearLayout(this);
        layerRow.setOrientation(LinearLayout.HORIZONTAL);
        layerRow.setPadding(0, Ui.dp(this, 14), 0, 0);
        layerDns = Ui.layerTile(this, getString(R.string.layer_dns), getString(R.string.state_off), Ui.OFF);
        layerApps = Ui.layerTile(this, getString(R.string.layer_apps), getString(R.string.state_off), Ui.OFF);
        layerImages = Ui.layerTile(this, getString(R.string.layer_images), getString(R.string.state_off),
                Ui.OFF);
        Ui.LayerTile[] tiles = {layerDns, layerApps, layerImages};
        for (int i = 0; i < tiles.length; i++) {
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) tileParams.setMarginStart(Ui.dp(this, 8));
            layerRow.addView(tiles[i].root, tileParams);
        }
        hero.addView(layerRow);
        page.addView(hero, Ui.margins(this, 0, 10));

        attentionCard = Ui.card(this);
        attentionCard.setBackgroundResource(R.drawable.bg_warning_card);
        attentionList = new LinearLayout(this);
        attentionList.setOrientation(LinearLayout.VERTICAL);
        attentionList.setPadding(0, Ui.dp(this, 4), 0, 0);
        attentionDetails = Ui.disclosure(this, getString(R.string.section_attention), attentionList);
        attentionCard.addView(attentionDetails.root);
        attentionCard.setVisibility(View.GONE);
        page.addView(attentionCard, Ui.margins(this, 0, 10));

        LinearLayout pauseCard = Ui.card(this);
        Ui.SettingRow pauseRow = Ui.settingRow(this, R.drawable.ic_pulse,
                getString(R.string.pause_voluntary_action), getString(R.string.pause_voluntary_summary),
                view -> startActivity(new Intent(this, RestrictionActivity.class)
                        .putExtra("voluntary", true)));
        pauseCard.addView(pauseRow.root);
        page.addView(pauseCard, Ui.margins(this, 0, 10));

        setupCard = addCard(page, R.drawable.ic_check, R.string.setup_heading);
        setupState = Ui.body(this, getString(R.string.setup_first));
        setupCard.addView(setupState);
        setupNext = Ui.button(this, R.string.setup_next, Ui.ButtonKind.PRIMARY);
        setupCard.addView(setupNext, Ui.buttonParams(this));
        setupNext.setOnClickListener(view -> nextSetupStep());

        LinearLayout network = addCard(page, R.drawable.ic_network, R.string.section_network);
        networkState = statusLine();
        network.addView(networkState);
        TextView seedVersion = Ui.caption(this, getString(R.string.dns_seed_version,
                BuildConfig.DOMAIN_SEED_VERSION, seedDate(BuildConfig.DOMAIN_SEED_BUILT)));
        seedVersion.setPadding(0, Ui.dp(this, 8), 0, 0);
        network.addView(seedVersion);
        seedFreshness = Ui.body(this, "");
        seedFreshness.setTextColor(Ui.color(this, R.color.warning));
        seedFreshness.setVisibility(View.GONE);
        seedFreshness.setPadding(0, Ui.dp(this, 4), 0, 0);
        Long seedAge = SeedFreshness.ageDays(BuildConfig.DOMAIN_SEED_BUILT, System.currentTimeMillis());
        if (seedAge != null && seedAge >= SeedFreshness.WARN_DAYS) {
            int days = (int) Math.min(seedAge, Integer.MAX_VALUE);
            String age = getResources().getQuantityString(R.plurals.seed_age_days, days, days);
            seedFreshness.setText(getString(R.string.dns_seed_stale, age));
            seedFreshness.setVisibility(View.VISIBLE);
        }
        network.addView(seedFreshness);
        startDns = Ui.button(this, R.string.dns_start, Ui.ButtonKind.PRIMARY);
        network.addView(startDns, Ui.buttonParams(this));
        startDns.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.dns_consent_title).setMessage(R.string.dns_consent_body)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.dns_continue, (dialog, which) -> requestDns()).show());
        Button vpnSettings = Ui.button(this, R.string.dns_settings, Ui.ButtonKind.GHOST);
        LinearLayout advanced = new LinearLayout(this);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.addView(vpnSettings, Ui.buttonParams(this));
        vpnSettings.setOnClickListener(v -> reviewVpnSettings());
        safeSearchToggle = Ui.toggle(this, getString(R.string.dns_safe_search_switch),
                getString(R.string.dns_safe_search_hint));
        safeSearchToggle.setChecked(settings != null && settings.safeSearchEnabled());
        safeSearchToggle.setOnClickListener(view -> {
            // A switch has already toggled itself before OnClick runs, so the
            // checked state is the intended new value. Revert only on refusal.
            boolean wanted = safeSearchToggle.isChecked();
            try {
                if (settings != null) settings.updateSafeSearch(wanted);
                refreshStatus();
            } catch (RuntimeException refused) {
                safeSearchToggle.setChecked(!wanted);
                showRefusal(refused);
            }
        });
        advanced.addView(safeSearchToggle, Ui.margins(this, 8, 0));
        advanced.addView(Ui.divider(this), Ui.margins(this, 14, 2));

        advanced.addView(subheading(R.string.section_route_check));
        networkCheckState = statusLine();
        advanced.addView(networkCheckState);
        checkNetwork = Ui.button(this, R.string.network_check_button, Ui.ButtonKind.TONAL);
        advanced.addView(checkNetwork, Ui.buttonParams(this));
        checkNetwork.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.network_check_button).setMessage(R.string.network_check_disclosure)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.network_check_start, (dialog, which) -> {
                    DnsVpnService.requestConnectionCheck();
                    refreshStatus();
                }).show());

        advanced.addView(Ui.divider(this), Ui.margins(this, 14, 2));
        advanced.addView(subheading(R.string.section_background_check));
        watchdogState = statusLine();
        advanced.addView(watchdogState);
        notifSettings = Ui.button(this, R.string.open_notification_settings, Ui.ButtonKind.QUIET);
        notifSettings.setVisibility(View.GONE);
        advanced.addView(notifSettings, Ui.buttonParams(this));
        notifSettings.setOnClickListener(view -> {
            try {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            } catch (RuntimeException unavailable) {
                Toast.makeText(this, R.string.open_notification_settings, Toast.LENGTH_SHORT).show();
            }
        });
        privateDnsSettings = Ui.button(this, R.string.private_dns_settings, Ui.ButtonKind.QUIET);
        privateDnsSettings.setVisibility(View.GONE);
        advanced.addView(privateDnsSettings, Ui.buttonParams(this));
        privateDnsSettings.setOnClickListener(view -> {
            if (Build.VERSION.SDK_INT >= 29) {
                try { startActivity(new Intent("android.settings.PRIVATE_DNS_SETTINGS")); }
                catch (RuntimeException unavailable) { reviewVpnSettings(); }
            } else {
                reviewVpnSettings();
            }
        });
        dnsAdvanced = Ui.disclosure(this, getString(R.string.dns_advanced), advanced);
        network.addView(dnsAdvanced.root, Ui.margins(this, 0, 4));

        LinearLayout apps = addCard(page, R.drawable.ic_apps, R.string.section_app_layer);
        textState = statusLine();
        apps.addView(textState);
        visualState = statusLine();
        apps.addView(visualState, Ui.margins(this, 10, 0));
        shortVideoState = statusLine();
        apps.addView(shortVideoState, Ui.margins(this, 10, 0));
        grayState = statusLine();
        apps.addView(grayState, Ui.margins(this, 10, 0));
        commitmentState = statusLine();
        apps.addView(commitmentState, Ui.margins(this, 10, 0));
        Button openRules = Ui.button(this, R.string.open_rules, Ui.ButtonKind.TONAL);
        apps.addView(openRules, Ui.buttonParams(this));
        openRules.setOnClickListener(view -> showPage(PAGE_RULES, true));

        LinearLayout uninstall = addCard(page, R.drawable.ic_lock, R.string.section_uninstall);
        uninstallState = statusLine();
        uninstall.addView(uninstallState);
        managedState = statusLine();
        managedState.setVisibility(View.GONE);
        uninstall.addView(managedState, Ui.margins(this, 8, 0));
        uninstallAction = Ui.button(this, R.string.uninstall_action, Ui.ButtonKind.TONAL);
        uninstall.addView(uninstallAction, Ui.buttonParams(this));
        uninstallAction.setOnClickListener(view -> showUninstallDialog());
    }

    // ------------------------------------------------------------------ rules page

    private void buildRulesPage(LinearLayout page) {
        page.addView(Ui.pageTitle(this, getString(R.string.rules_heading)), Ui.margins(this, 6, 6));
        page.addView(Ui.body(this, getString(R.string.rules_help)), Ui.margins(this, 0, 8));

        rulesLockCard = Ui.card(this);
        rulesLockCard.setBackgroundResource(R.drawable.bg_warning_card);
        LinearLayout lockRow = new LinearLayout(this);
        lockRow.setOrientation(LinearLayout.HORIZONTAL);
        lockRow.setGravity(Gravity.TOP);
        lockRow.addView(Ui.icon(this, R.drawable.ic_lock, R.color.warning, 22));
        LinearLayout lockCopy = new LinearLayout(this);
        lockCopy.setOrientation(LinearLayout.VERTICAL);
        TextView lockTitle = Ui.strong(this, getString(R.string.rules_lock_title));
        lockCopy.addView(lockTitle);
        rulesLockText = Ui.body(this, "");
        lockCopy.addView(rulesLockText, Ui.margins(this, 0, 2));
        LinearLayout.LayoutParams lockCopyParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lockCopyParams.setMarginStart(Ui.dp(this, 12));
        lockRow.addView(lockCopy, lockCopyParams);
        rulesLockCard.addView(lockRow);
        rulesLockCard.setVisibility(View.GONE);
        page.addView(rulesLockCard, Ui.margins(this, 0, 10));

        LinearLayout builtin = addCard(page, R.drawable.ic_shield, R.string.section_builtin);
        adultToggle = Ui.toggle(this, getString(R.string.toggle_adult), getString(R.string.adult_mode_off));
        adultToggle.setOnClickListener(view -> {
            adultToggle.setChecked(!adultToggle.isChecked());
            editAdultMode();
        });
        builtin.addView(adultToggle);
        builtin.addView(Ui.divider(this));
        visualToggle = Ui.toggle(this, getString(R.string.toggle_visual), getString(R.string.visual));
        visualToggle.setOnClickListener(view -> {
            visualToggle.setChecked(!visualToggle.isChecked());
            editVisualMode();
        });
        builtin.addView(visualToggle);
        builtin.addView(Ui.divider(this));
        shortVideoToggle = Ui.toggle(this, getString(R.string.toggle_short_video),
                getString(R.string.short_video_off));
        shortVideoToggle.setOnClickListener(view -> {
            shortVideoToggle.setChecked(!shortVideoToggle.isChecked());
            editShortVideo();
        });
        builtin.addView(shortVideoToggle);
        builtin.addView(Ui.divider(this));
        grayToggle = Ui.toggle(this, getString(R.string.gray_title), getString(R.string.gray_off));
        grayToggle.setOnClickListener(view -> {
            grayToggle.setChecked(!grayToggle.isChecked());
            editGray();
        });
        builtin.addView(grayToggle);

        LinearLayout accessibility = addCard(page, R.drawable.ic_text, R.string.section_accessibility);
        textState = statusLine();
        accessibility.addView(textState);
        accessibility.addView(Ui.caption(this, getString(R.string.text_guard_recheck_note)),
                Ui.margins(this, 8, 0));
        Button connect = Ui.button(this, R.string.enable_text_guard, Ui.ButtonKind.TONAL);
        accessibility.addView(connect, Ui.buttonParams(this));
        connect.setOnClickListener(view -> showAccessibilityDisclosure());

        LinearLayout custom = addCard(page, R.drawable.ic_tune, R.string.section_rules_custom);
        domainsRow = Ui.settingRow(this, R.drawable.ic_network,
                getString(R.string.edit_domains), getString(R.string.none_saved),
                view -> editRules(true));
        custom.addView(domainsRow.root);
        custom.addView(Ui.divider(this));
        wordsRow = Ui.settingRow(this, R.drawable.ic_text,
                getString(R.string.edit_words), getString(R.string.none_saved),
                view -> editRules(false));
        custom.addView(wordsRow.root);

        LinearLayout appRules = addCard(page, R.drawable.ic_apps, R.string.section_apps);
        blockedRow = Ui.settingRow(this, R.drawable.ic_lock,
                getString(R.string.choose_blocked_apps), getString(R.string.none_chosen),
                view -> chooseApps(false));
        appRules.addView(blockedRow.root);
        appRules.addView(Ui.divider(this));
        scannedRow = Ui.settingRow(this, R.drawable.ic_image,
                getString(R.string.choose_scanned_apps), getString(R.string.none_chosen),
                view -> chooseApps(true));
        appRules.addView(scannedRow.root);
        appRules.addView(Ui.divider(this));
        browserRow = Ui.settingRow(this, R.drawable.ic_network,
                getString(R.string.browsers_title), getString(R.string.browsers_checking),
                view -> showBrowserCoverage());
        appRules.addView(browserRow.root);

        LinearLayout limits = addCard(page, R.drawable.ic_clock, R.string.section_time_limits);
        timeLimitState = statusLine();
        limits.addView(timeLimitState);
        timeLimitEdit = Ui.button(this, R.string.time_limits_edit_button, Ui.ButtonKind.TONAL);
        limits.addView(timeLimitEdit, Ui.buttonParams(this));
        timeLimitEdit.setOnClickListener(view -> editTimeLimits());

        LinearLayout schedule = addCard(page, R.drawable.ic_calendar, R.string.section_schedule);
        scheduleState = statusLine();
        schedule.addView(scheduleState);
        scheduleEdit = Ui.button(this, R.string.schedule_edit_button, Ui.ButtonKind.TONAL);
        schedule.addView(scheduleEdit, Ui.buttonParams(this));
        scheduleEdit.setOnClickListener(view -> editSchedule());

        LinearLayout commitment = addCard(page, R.drawable.ic_clock, R.string.section_commitment);
        commitmentStateRules = statusLine();
        commitment.addView(commitmentStateRules);
        Button commitmentChoice = Ui.button(this, R.string.commitment_setup, Ui.ButtonKind.TONAL);
        commitment.addView(commitmentChoice, Ui.buttonParams(this));
        commitmentChoice.setOnClickListener(view -> chooseCommitment());
        commitmentNote = Ui.caption(this, getString(R.string.commitment_locked_note));
        commitmentNote.setVisibility(View.GONE);
        commitment.addView(commitmentNote, Ui.margins(this, 8, 0));

        LinearLayout privateCard = Ui.card(this);
        Ui.Disclosure privateScreens = Ui.disclosure(this, getString(R.string.section_private_screens),
                getString(R.string.private_screens));
        privateCard.addView(privateScreens.root);
        page.addView(privateCard, Ui.margins(this, 0, 10));
    }

    // ------------------------------------------------------------------ guide page

    private void buildGuidePage(LinearLayout page) {
        page.addView(Ui.pageTitle(this, getString(R.string.app_goal_title)), Ui.margins(this, 6, 6));
        page.addView(Ui.body(this, getString(R.string.app_goal_short)), Ui.margins(this, 0, 6));
        page.addView(Ui.disclosure(this, getString(R.string.app_goal_more),
                getString(R.string.app_goal_body)).root, Ui.margins(this, 0, 4));

        LinearLayout steps = addCard(page, R.drawable.ic_check, R.string.section_setup_steps);
        for (View step : numberedSteps(getString(R.string.guide_steps_core))) steps.addView(step);
        LinearLayout optionalSteps = new LinearLayout(this);
        optionalSteps.setOrientation(LinearLayout.VERTICAL);
        for (View step : numberedSteps(getString(R.string.guide_steps_more))) {
            optionalSteps.addView(step);
        }
        steps.addView(Ui.disclosure(this, getString(R.string.guide_more_steps), optionalSteps).root,
                Ui.margins(this, 0, 2));

        LinearLayout limits = addCard(page, R.drawable.ic_info, R.string.section_limits);
        limits.addView(Ui.disclosure(this, getString(R.string.guide_limits_title),
                getString(R.string.guide_limits_body)).root);

        LinearLayout privacy = addCard(page, R.drawable.ic_lock, R.string.section_privacy);
        privacy.addView(Ui.body(this, getString(R.string.privacy_short)));
        Ui.Disclosure privateScreens = Ui.disclosure(this, getString(R.string.section_private_screens),
                getString(R.string.private_screens));
        privacy.addView(privateScreens.root, Ui.margins(this, 6, 0));
        privacy.addView(Ui.disclosure(this, getString(R.string.guide_details),
                getString(R.string.privacy)).root, Ui.margins(this, 0, 2));

        LinearLayout uninstall = addCard(page, R.drawable.ic_lock, R.string.uninstall_title);
        LinearLayout uninstallDetails = new LinearLayout(this);
        uninstallDetails.setOrientation(LinearLayout.VERTICAL);
        uninstallDetails.addView(Ui.body(this, getString(R.string.uninstall_scope)));
        uninstallDetails.addView(Ui.body(this, getString(R.string.uninstall_guide)),
                Ui.margins(this, 8, 0));
        uninstallDetails.addView(Ui.caption(this, getString(R.string.uninstall_setup_note)),
                Ui.margins(this, 8, 0));
        uninstall.addView(Ui.disclosure(this, getString(R.string.guide_details),
                uninstallDetails).root);
        Button uninstallActionGuide = Ui.button(this, R.string.uninstall_action, Ui.ButtonKind.GHOST);
        uninstall.addView(uninstallActionGuide, Ui.buttonParams(this));
        uninstallActionGuide.setOnClickListener(view -> showUninstallDialog());

        LinearLayout admin = addCard(page, R.drawable.ic_shield, R.string.admin_title);
        adminState = statusLine();
        admin.addView(adminState);
        Button adminSetup = Ui.button(this, R.string.admin_setup, Ui.ButtonKind.TONAL);
        admin.addView(adminSetup, Ui.buttonParams(this));
        adminSetup.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.admin_title).setMessage(R.string.admin_explanation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.admin_continue, (dialog, which) -> {
                    Intent request = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                            .putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                    CommitmentAdminReceiver.component(this))
                            .putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    getString(R.string.admin_explanation));
                    startActivity(request);
                }).show());

        LinearLayout battery = addCard(page, R.drawable.ic_pulse, R.string.battery_title);
        battery.addView(Ui.body(this, getString(R.string.battery_body)));
        batteryState = statusLine();
        battery.addView(batteryState);
        batteryAction = Ui.button(this, R.string.battery_action, Ui.ButtonKind.TONAL);
        battery.addView(batteryAction, Ui.buttonParams(this));
        batteryAction.setOnClickListener(view -> requestBatteryExemption());
        refreshBatteryState();

        LinearLayout guardian = addCard(page, R.drawable.ic_shield, R.string.guardian_pair_title);
        guardian.addView(Ui.body(this, getString(R.string.guardian_pair_body)));
        Button guardianAction = Ui.button(this, R.string.guardian_pair_action, Ui.ButtonKind.GHOST);
        guardian.addView(guardianAction, Ui.buttonParams(this));
        guardianAction.setOnClickListener(view ->
                startActivity(new Intent(this, GuardianPairActivity.class)));

        LinearLayout diagnostics = addCard(page, R.drawable.ic_pulse, R.string.section_diagnostics);
        networkStats = Ui.text(this, getString(R.string.dns_stats_empty), 13, R.color.text_secondary);
        networkStats.setLineSpacing(Ui.dp(this, 3), 1.12f);
        networkStats.setTextIsSelectable(true);
        diagnostics.addView(networkStats);
        diagnostics.addView(Ui.caption(this, getString(R.string.diagnostics_hint)),
                Ui.margins(this, 6, 0));
        Button copyDiagnostics = Ui.button(this, R.string.copy_diagnostics, Ui.ButtonKind.QUIET);
        diagnostics.addView(copyDiagnostics, Ui.buttonParams(this));
        copyDiagnostics.setOnClickListener(view ->
                copyText(networkStats.getText().toString(), R.string.diagnostics_copied));

        LinearLayout tools = addCard(page, R.drawable.ic_tune, R.string.section_tools);
        TextView testerHeading = Ui.strong(this, getString(R.string.tester));
        testerHeading.setTextSize(16);
        tools.addView(testerHeading);
        tools.addView(Ui.body(this, getString(R.string.tester_help)), Ui.margins(this, 4, 0));
        tools.addView(Ui.caption(this, getString(R.string.tester_private)), Ui.margins(this, 6, 0));
        testerRule = Ui.input(this, R.string.rule_hint, true);
        testerRule.setMinLines(3);
        testerRule.setMaxLines(6);
        tools.addView(testerRule, Ui.margins(this, 10, 0));
        testerSample = Ui.input(this, R.string.sample_hint, true);
        testerSample.setMinLines(2);
        testerSample.setMaxLines(5);
        tools.addView(testerSample, Ui.margins(this, 8, 0));
        Button check = Ui.button(this, R.string.test_button, Ui.ButtonKind.TONAL);
        tools.addView(check, Ui.buttonParams(this));
        testerResult = Ui.text(this, getString(R.string.tester_result_hint), 14, R.color.text_tertiary);
        testerResult.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        tools.addView(testerResult, Ui.margins(this, 8, 0));

        TextWatcher clearResult = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                setTesterResult(R.string.tester_result_hint, R.color.text_tertiary);
                updateTesterPrivacy();
            }
            @Override public void afterTextChanged(Editable text) { }
        };
        testerRule.addTextChangedListener(clearResult);
        testerSample.addTextChangedListener(clearResult);
        check.setOnClickListener(v -> {
            String ruleText = testerRule.getText().toString();
            String sampleText = testerSample.getText().toString();
            if (sampleText.trim().isEmpty()) {
                setTesterResult(R.string.invalid, R.color.danger);
                return;
            }
            if (sampleText.codePointCount(0, sampleText.length()) > TEXT_LIMIT) {
                setTesterResult(R.string.too_long, R.color.danger);
                return;
            }
            try {
                WordMatcher matcher = WordRuleList.compile(ruleText);
                boolean matched = !matcher.matches(sampleText).isEmpty();
                setTesterResult(matched ? R.string.match : R.string.no_match,
                        matched ? R.color.warning : R.color.success);
            } catch (IllegalArgumentException error) {
                setTesterResult(R.string.invalid, R.color.danger);
            }
        });

        LinearLayout notices = addCard(page, R.drawable.ic_book, R.string.notices_title);
        notices.addView(Ui.body(this, getString(R.string.notices_help)));
        Button noticesOpen = Ui.button(this, R.string.notices_open, Ui.ButtonKind.GHOST);
        notices.addView(noticesOpen, Ui.buttonParams(this));
        noticesOpen.setOnClickListener(view -> Notices.show(this));
    }

    // ------------------------------------------------------------------ page building helpers

    private LinearLayout addCard(LinearLayout page, int iconRes, int titleRes) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.sectionHeader(this, iconRes, getString(titleRes)));
        page.addView(card, Ui.margins(this, 0, 10));
        return card;
    }

    private TextView subheading(int textRes) {
        TextView view = Ui.text(this, getString(textRes), 15, R.color.text_primary);
        view.setTypeface(Ui.medium());
        view.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
        return view;
    }

    private TextView statusLine() {
        TextView view = Ui.text(this, "", 14, R.color.text_secondary);
        view.setLineSpacing(Ui.dp(this, 3), 1.12f);
        view.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        return view;
    }

    /** Colours the leading dot, never the whole sentence, so long status text stays readable. */
    private void setStatus(TextView view, CharSequence text, int kind) {
        if (view == null || text == null) return;
        int dotColor = Ui.stateColor(this, kind);
        SpannableString marked = new SpannableString("\u25CF  " + text);
        marked.setSpan(new ForegroundColorSpan(dotColor), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Ui.setTextIfChanged(view, marked);
        view.setContentDescription(text);
    }

    private void setTesterResult(int textRes, int colorRes) {
        if (testerResult == null) return;
        testerResult.setText(textRes);
        testerResult.setTextColor(Ui.color(this, colorRes));
    }

    /** Renders the guide's numbered paragraphs as a scannable list with a number badge each. */
    private List<View> numberedSteps(String body) {
        List<View> views = new ArrayList<>();
        String[] parts = body.split("\n\n");
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index].trim();
            if (part.isEmpty()) continue;
            String number = String.valueOf(index + 1);
            String text = part;
            int dot = part.indexOf(". ");
            if (dot > 0 && dot <= 2 && part.substring(0, dot).matches("\\d+")) {
                number = part.substring(0, dot);
                text = part.substring(dot + 2);
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
            TextView badge = Ui.text(this, number, 12, R.color.accent);
            badge.setTypeface(Ui.medium());
            badge.setGravity(Gravity.CENTER);
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(Ui.color(this, R.color.accent_soft));
            badge.setBackground(background);
            row.addView(badge, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
            TextView step = Ui.body(this, text);
            LinearLayout.LayoutParams stepParams = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            stepParams.setMarginStart(Ui.dp(this, 12));
            row.addView(step, stepParams);
            views.add(row);
        }
        return views;
    }

    /** The real reason a change was refused; only a commitment refusal says "locked". */
    private String refusalText(RuntimeException refused) {
        switch (PolicyChangeError.of(refused)) {
            case TOO_MANY_APPS: return getResources().getQuantityString(R.plurals.rules_too_many_apps,
                    PolicyStore.MAX_APPS, PolicyStore.MAX_APPS);
            case INVALID: return getString(R.string.rules_invalid);
            case COMMITMENT: return getString(R.string.rules_locked);
            case NEEDS_RULES: return getString(R.string.commitment_needs_rules);
            case SAVE_FAILED: return getString(R.string.rules_save_failed);
            default: return getString(R.string.policy_unavailable);
        }
    }

    private void showRefusal(RuntimeException refused) {
        Toast.makeText(this, refusalText(refused), Toast.LENGTH_LONG).show();
    }

    private int countRules(String text) {
        int lines = 0;
        for (String line : text.split("\n")) if (!line.trim().isEmpty()) lines++;
        return Math.min(lines, MAX_RULE_LINES);
    }

    private void refreshRuleSummaries() {
        if (domainsRow == null) return;
        if (settings == null) {
            setRuleSummaries(getString(R.string.policy_unavailable), getString(R.string.policy_unavailable),
                    getString(R.string.policy_unavailable), getString(R.string.policy_unavailable),
                    getString(R.string.policy_unavailable));
            return;
        }
        try {
            PolicyStore.Snapshot rules = settings.snapshot();
            int domains = countRules(settings.domainText());
            int words = countRules(settings.wordText());
            int blocked = rules.blockedApps.size();
            int scanned = rules.scannedApps.size();
            setRuleSummaries(
                    domains == 0 ? getString(R.string.none_saved)
                            : getResources().getQuantityString(R.plurals.saved_domains, domains, domains),
                    words == 0 ? getString(R.string.none_saved)
                            : getResources().getQuantityString(R.plurals.saved_words, words, words),
                    blocked == 0 ? getString(R.string.none_chosen)
                            : getResources().getQuantityString(R.plurals.blocked_apps_count, blocked, blocked),
                    scanned == 0 ? getString(R.string.none_chosen)
                            : getResources().getQuantityString(R.plurals.inspected_apps_count, scanned, scanned),
                    browserSummary(rules));
        } catch (RuntimeException unavailable) {
            setRuleSummaries(getString(R.string.policy_unavailable), getString(R.string.policy_unavailable),
                    getString(R.string.policy_unavailable), getString(R.string.policy_unavailable),
                    getString(R.string.policy_unavailable));
        }
    }

    /** Installed browsers and how many of them no running layer covers. */
    private CharSequence browserSummary(PolicyStore.Snapshot rules) {
        List<ChosenApps.Entry> browsers;
        try {
            browsers = ChosenApps.browsers(this);
        } catch (RuntimeException unavailable) {
            return getString(R.string.browsers_checking);
        }
        if (browsers.isEmpty()) return getString(R.string.browsers_none);
        List<String> names = new ArrayList<>();
        for (ChosenApps.Entry browser : browsers) names.add(browser.packageName);
        int uncovered = BrowserCoverage.withoutCoverage(names, rules.blockedApps, rules.scannedApps,
                inspectionActive(rules), domainsRunning()).size();
        return getString(R.string.browsers_summary, browsers.size(), uncovered);
    }

    /** Inspection covers an app only while the guard is connected and a text or image check is on. */
    private static boolean inspectionActive(PolicyStore.Snapshot rules) {
        return TextGuardService.connected && (rules.hasWordProtection() || rules.visualEnabled);
    }

    private static boolean domainsRunning() {
        return DnsVpnService.state == DnsVpnService.State.RUNNING
                || DnsVpnService.state == DnsVpnService.State.DEGRADED;
    }

    private void setRuleSummaries(CharSequence domains, CharSequence words, CharSequence blocked,
            CharSequence scanned, CharSequence browsers) {
        if (domainsRow != null) domainsRow.setSummary(domains);
        if (wordsRow != null) wordsRow.setSummary(words);
        if (blockedRow != null) blockedRow.setSummary(blocked);
        if (scannedRow != null) scannedRow.setSummary(scanned);
        if (browserRow != null) browserRow.setSummary(browsers);
    }

    private void updateToggle(Switch toggle, int titleRes, CharSequence summary) {
        if (toggle == null) return;
        String key = summary == null ? "" : summary.toString();
        if (key.equals(toggle.getTag())) return;
        toggle.setTag(key);
        Ui.setToggleText(toggle, getString(titleRes), summary);
    }

    // ------------------------------------------------------------------ refresh

    private void refreshStatus() {
        if (statusTitle == null || isFinishing()) return;
        DnsVpnService.State current = DnsVpnService.state;
        DnsVpnService.CheckState check = DnsVpnService.connectionCheck.state;
        boolean privateDnsBypass = DnsVpnService.privateDnsStrict;

        PolicyStore.Snapshot rules = null;
        boolean settingsReadable = settings != null;
        long remaining = 0;
        boolean unknown = false, restored = false;
        if (settings != null) {
            try {
                rules = settings.snapshot();
                remaining = settings.remainingMillis();
                unknown = settings.commitmentUnknown();
                restored = settings.restoredFromCopy();
            } catch (RuntimeException unavailable) {
                settingsReadable = false;
                rules = null;
            }
        }
        boolean accessibilityConfigured = rules != null && (!rules.blockedApps.isEmpty()
                || (rules.schedule.enabled && rules.schedule.isConfigured())
                || rules.timeLimits.configured()
                || (!rules.scannedApps.isEmpty() && (rules.hasWordProtection() || rules.visualEnabled))
                || rules.shortVideoEnabled);
        boolean rulesLocked = settingsReadable && ProtectionLock.locked(remaining);
        boolean scanIncomplete = TextGuardService.inspection.incomplete();
        boolean policyPending = rules != null && rules.adultWordsEnabled
                && AdultPolicyAssets.state != AdultPolicyAssets.State.READY;
        boolean inspectionPartial = scanIncomplete || policyPending;
        Boolean permission = guardPermissionEnabled();
        GuardHealth guard = GuardHealth.evaluate(permission,
                TextGuardService.connected, accessibilityConfigured, settingsReadable, inspectionPartial);

        if (rules != null) {
            refreshSchedule(rules.schedule);
            refreshTimeLimits(rules);
            if (rules.adultWordsEnabled) AdultPolicyAssets.ensureLoaded(this);
            if (safeSearchToggle != null) safeSearchToggle.setChecked(settings.safeSearchEnabled());
        } else {
            setStatus(scheduleState, getString(R.string.policy_unavailable), Ui.ATTENTION);
            if (timeLimitState != null) setStatus(timeLimitState, getString(R.string.policy_unavailable), Ui.ATTENTION);
        }

        int visualReason = 0;
        if (rules != null) {
            visualReason = refreshVisual(rules, remaining, permission);
            refreshGray(remaining);
        } else {
            setStatus(visualState, getString(R.string.policy_unavailable), Ui.ATTENTION);
            setStatus(grayState, getString(R.string.policy_unavailable), Ui.ATTENTION);
            setStatus(shortVideoState, getString(R.string.policy_unavailable), Ui.ATTENTION);
            Ui.setEnabledWithAlpha(visualToggle, false);
            Ui.setEnabledWithAlpha(grayToggle, false);
            Ui.setEnabledWithAlpha(shortVideoToggle, false);
            Ui.setEnabledWithAlpha(adultToggle, false);
            updateToggle(visualToggle, R.string.toggle_visual, getString(R.string.policy_unavailable));
            updateToggle(grayToggle, R.string.gray_title, getString(R.string.policy_unavailable));
            updateToggle(shortVideoToggle, R.string.toggle_short_video, getString(R.string.policy_unavailable));
            updateToggle(adultToggle, R.string.toggle_adult, getString(R.string.policy_unavailable));
        }
        applyRulesLock(rulesLocked, settingsReadable, remaining);

        UninstallProtectionController.Status uninstallStatus = refreshUninstallState();
        boolean uninstallAttention = uninstallStatus != null
                && uninstallStatus.state() == UninstallProtection.State.OUT_OF_SYNC;
        boolean managedAttention = uninstallStatus != null && uninstallStatus.deviceOwner
                && uninstallStatus.managedLockdown && !uninstallStatus.restrictionsMatch();
        // On a Play build the lock freezes rules but cannot stop an uninstall. Say
        // so instead of letting the lock read as stronger than it is.
        boolean lockUnprotected = rulesLocked && !BuildConfig.SELF_PROTECTION
                && (uninstallStatus == null
                || uninstallStatus.state() != UninstallProtection.State.PROTECTED);

        refreshWatchdogState();
        boolean watchdogLost = !LayerWatchdog.lostSignature(this).isEmpty();
        boolean notificationsOff = !notificationsEnabled();
        boolean notificationsAttention = LayerWatchdog.lastCheckedAt(this) > 0 && notificationsOff;

        boolean needsAttention = guard.needsAttention || current == DnsVpnService.State.ERROR
                || current == DnsVpnService.State.DEGRADED || privateDnsBypass
                || check == DnsVpnService.CheckState.FAILED || visualReason != 0
                || unknown || restored || uninstallAttention || managedAttention
                || watchdogLost || notificationsAttention;
        CharSequence title = getString(needsAttention ? R.string.protection_attention
                : current == DnsVpnService.State.RUNNING || guard == GuardHealth.ACTIVE
                ? R.string.protection_partial : R.string.status_title);
        Ui.setTextIfChanged(statusTitle, title);
        statusTitle.setTextColor(Ui.color(this, needsAttention ? R.color.warning : R.color.text_primary));
        heroIcon.setColorFilter(Ui.color(this, needsAttention ? R.color.warning : R.color.accent));
        Ui.setTextIfChanged(statusBody, !settingsReadable
                ? getString(R.string.policy_unavailable)
                : rulesLocked
                ? getString(R.string.status_body_locked, Durations.remaining(this, remaining))
                : rules != null && rules.hasConfiguredRestrictions()
                ? getString(R.string.status_body_unlocked)
                : getString(R.string.status_body_short));

        // The three tiles are the fastest reading of the app: one word per layer.
        int dnsKind, appsKind, imagesKind;
        int dnsText, appsText, imagesText;
        switch (current) {
            case RUNNING: dnsKind = Ui.ON; dnsText = R.string.state_on; break;
            case DEGRADED: dnsKind = Ui.ATTENTION; dnsText = R.string.state_attention; break;
            case STARTING: dnsKind = Ui.STARTING; dnsText = R.string.state_starting; break;
            case ERROR: dnsKind = Ui.ATTENTION; dnsText = R.string.state_attention; break;
            default: dnsKind = Ui.OFF; dnsText = R.string.state_off; break;
        }
        if (privateDnsBypass) dnsKind = Ui.ATTENTION;
        switch (guard) {
            case ACTIVE: appsKind = Ui.ON; appsText = R.string.state_on; break;
            case LIMITED: appsKind = Ui.ATTENTION; appsText = R.string.state_attention; break;
            case READY_NO_RULES: appsKind = Ui.OFF; appsText = R.string.state_not_set_up; break;
            case PERMISSION_REQUIRED:
            case RECONNECT_REQUIRED:
            case PERMISSION_UNKNOWN:
            case SETTINGS_UNAVAILABLE: appsKind = Ui.ATTENTION; appsText = R.string.state_attention; break;
            default: appsKind = Ui.OFF; appsText = R.string.state_off; break;
        }
        if (Build.VERSION.SDK_INT < 34) {
            imagesKind = Ui.UNSUPPORTED;
            imagesText = R.string.state_unsupported;
        } else if (rules == null || !rules.visualEnabled) {
            imagesKind = Ui.OFF;
            imagesText = R.string.state_off;
        } else if (rules.scannedApps.isEmpty()) {
            imagesKind = Ui.OFF;
            imagesText = R.string.state_not_set_up;
        } else if (!Boolean.TRUE.equals(permission) || !TextGuardService.connected) {
            imagesKind = Ui.ATTENTION;
            imagesText = R.string.state_attention;
        } else {
            switch (VisualGuard.health.shown()) {
                case ERROR:
                case UNREADABLE: imagesKind = Ui.ATTENTION; imagesText = R.string.state_attention; break;
                case LOADING:
                case CHECKING: imagesKind = Ui.STARTING; imagesText = R.string.state_starting; break;
                case SAMPLED:
                case WAITING:
                case INPUT: imagesKind = Ui.ON; imagesText = R.string.state_on; break;
                default: imagesKind = Ui.OFF; imagesText = R.string.state_off; break;
            }
        }
        layerDns.setState(getString(dnsText), dnsKind);
        layerApps.setState(getString(appsText), appsKind);
        layerImages.setState(getString(imagesText), imagesKind);

        int networkText;
        switch (current) {
            case STARTING: networkText = R.string.dns_starting; break;
            case RUNNING: networkText = R.string.dns_running; break;
            case DEGRADED: networkText = R.string.dns_degraded; break;
            case ERROR: networkText = R.string.dns_error; break;
            default: networkText = R.string.network; break;
        }
        String network = privateDnsBypass
                ? getString(R.string.dns_private_dns_strict, getString(networkText))
                : getString(networkText);
        int networkKind;
        switch (current) {
            case RUNNING: networkKind = Ui.ON; break;
            case STARTING: networkKind = Ui.STARTING; break;
            case DEGRADED:
            case ERROR: networkKind = Ui.ATTENTION; break;
            default: networkKind = Ui.OFF; break;
        }
        if (privateDnsBypass) networkKind = Ui.ATTENTION;
        setStatus(networkState, network, networkKind);
        privateDnsSettings.setVisibility(privateDnsBypass ? View.VISIBLE : View.GONE);
        if (privateDnsBypass && dnsAdvanced != null) dnsAdvanced.setExpanded(true);

        boolean canStartDns = current == DnsVpnService.State.OFF || current == DnsVpnService.State.ERROR;
        Ui.setTextIfChanged(startDns, getString(current == DnsVpnService.State.STARTING
                ? R.string.state_starting : canStartDns ? R.string.dns_start : R.string.dns_on_button));
        Ui.setEnabledWithAlpha(startDns, settings != null && canStartDns);
        Ui.setTextIfChanged(networkStats, getString(R.string.dns_summary,
                getString(R.string.dns_stats, DnsVpnService.domains, DnsVpnService.blocked.get(),
                        DnsVpnService.forwarded.get(), DnsVpnService.rewritten.get(),
                        DnsVpnService.errors.get()),
                getString(R.string.dns_packet_diagnostics, DnsVpnService.unsupportedPackets.get(),
                        DnsVpnService.unsupportedTcpPackets.get(), DnsVpnService.malformedPackets.get()),
                getString(R.string.dns_benign_packets, DnsVpnService.ignoredPackets.get(),
                        DnsVpnService.refusedTlsConnections.get()),
                getString(R.string.dns_recoveries, DnsVpnService.recoveries.get()),
                getString(DnsVpnService.networkMonitoring ? R.string.dns_monitor_active
                        : R.string.dns_monitor_inactive, DnsVpnService.networkChanges.get()),
                getString(R.string.dns_cancelled_network_requests,
                        DnsVpnService.cancelledNetworkRequests.get()),
                getString(R.string.dns_tcp_stats, DnsVpnService.tcpQueries.get(),
                        DnsVpnService.tcpReplies.get(), DnsVpnService.udpTruncated.get())));

        int checkText;
        int checkKind;
        switch (check) {
            case CHECKING: checkText = R.string.network_check_running; checkKind = Ui.STARTING; break;
            case PASSED: checkText = R.string.network_check_passed; checkKind = Ui.ON; break;
            case FAILED: checkText = R.string.network_check_failed; checkKind = Ui.ATTENTION; break;
            case STALE: checkText = R.string.network_check_stale; checkKind = Ui.ATTENTION; break;
            default: checkText = R.string.network_check_idle; checkKind = Ui.OFF; break;
        }
        setStatus(networkCheckState, getString(checkText), checkKind);
        Ui.setEnabledWithAlpha(checkNetwork, (current == DnsVpnService.State.RUNNING
                || current == DnsVpnService.State.DEGRADED)
                && check != DnsVpnService.CheckState.CHECKING);

        try { refreshSetup(guard, current, rules, remaining); }
        catch (RuntimeException unavailable) {
            Ui.setTextIfChanged(setupState, getString(R.string.policy_unavailable));
            Ui.setEnabledWithAlpha(setupNext, false);
        }

        int textRes;
        int textKind;
        switch (guard) {
            case ACTIVE: textRes = R.string.text_guard_connected; textKind = Ui.ON; break;
            case LIMITED:
                if (policyPending && !scanIncomplete) {
                    textRes = R.string.text_guard_connected;
                    textKind = Ui.ON;
                } else {
                    textRes = R.string.text_guard_partial;
                    textKind = Ui.ATTENTION;
                }
                break;
            case READY_NO_RULES: textRes = R.string.text_guard_no_rules; textKind = Ui.OFF; break;
            case RECONNECT_REQUIRED: textRes = R.string.text_guard_reconnect; textKind = Ui.ATTENTION; break;
            case PERMISSION_REQUIRED: textRes = R.string.text_guard_permission; textKind = Ui.ATTENTION; break;
            case PERMISSION_UNKNOWN: textRes = R.string.text_guard_unknown; textKind = Ui.ATTENTION; break;
            case SETTINGS_UNAVAILABLE: textRes = R.string.policy_unavailable; textKind = Ui.ATTENTION; break;
            default: textRes = R.string.text_guard_disabled; textKind = Ui.OFF; break;
        }
        setStatus(textState, getString(textRes), textKind);

        boolean adminActive = CommitmentAdminReceiver.isActive(this);
        setStatus(adminState, getString(adminActive ? R.string.admin_active : R.string.admin_off),
                adminActive ? Ui.ON : Ui.OFF);

        String commitment;
        if (!settingsReadable) {
            commitment = getString(R.string.policy_unavailable);
        } else {
            commitment = remaining == 0 ? getString(R.string.commitment)
                    : getString(unknown ? R.string.commitment_unknown : R.string.commitment_remaining,
                            Durations.remaining(this, remaining));
        }
        int commitmentKind = !settingsReadable || unknown ? Ui.ATTENTION
                : remaining == 0 ? Ui.OFF : Ui.ON;
        setStatus(commitmentState, commitment, commitmentKind);
        setStatus(commitmentStateRules, commitment, commitmentKind);
        if (commitmentNote != null) {
            commitmentNote.setVisibility(settingsReadable && remaining > 0 ? View.VISIBLE : View.GONE);
        }
        restoredCard.setVisibility(restored ? View.VISIBLE : View.GONE);

        LinkedHashSet<Integer> attention = new LinkedHashSet<>();
        if (!settingsReadable) attention.add(R.string.attention_policy_unavailable);
        if (current == DnsVpnService.State.ERROR) attention.add(R.string.attention_dns_error);
        else if (current == DnsVpnService.State.DEGRADED) attention.add(R.string.attention_dns_degraded);
        else if (current == DnsVpnService.State.OFF && DnsVpnService.resumeRequested(this))
            attention.add(R.string.attention_dns_not_running);
        if (privateDnsBypass) attention.add(R.string.attention_private_dns);
        switch (guard) {
            case PERMISSION_REQUIRED: attention.add(R.string.attention_guard_permission); break;
            case RECONNECT_REQUIRED: attention.add(R.string.attention_guard_disconnected); break;
            case PERMISSION_UNKNOWN: attention.add(R.string.attention_guard_unknown); break;
            case LIMITED:
                if (scanIncomplete) attention.add(R.string.attention_scan_incomplete);
                if (policyPending) attention.add(AdultPolicyAssets.state == AdultPolicyAssets.State.ERROR
                        ? R.string.attention_policy_error : R.string.attention_policy_loading);
                if (!scanIncomplete && !policyPending) attention.add(R.string.attention_scan_incomplete);
                break;
            case SETTINGS_UNAVAILABLE: attention.add(R.string.attention_policy_unavailable); break;
            default: break;
        }
        if (visualReason != 0) attention.add(visualReason);
        if (rules != null && rules.timeLimits.configured() && !AppUsage.hasAccess(this))
            attention.add(R.string.attention_time_limits_access);
        if (check == DnsVpnService.CheckState.FAILED) attention.add(R.string.attention_route_failed);
        if (uninstallAttention) attention.add(R.string.attention_uninstall_sync);
        if (managedAttention) attention.add(R.string.attention_managed_sync);
        if (lockUnprotected) attention.add(R.string.attention_uninstall_unlocked);
        if (watchdogLost) attention.add(R.string.attention_watchdog_lost);
        else if (notificationsAttention) attention.add(R.string.attention_notifications_off);
        if (unknown) attention.add(R.string.attention_commitment_unknown);
        if (restored) attention.add(R.string.attention_settings_restored);
        updateAttention(attention);
    }

    /**
     * Freeze every rule editor while the protection lock runs. The backend also
     * refuses the change; disabling the controls is what makes the promise
     * legible instead of a surprise refusal after typing.
     */
    private void applyRulesLock(boolean locked, boolean readable, long remaining) {
        boolean editable = readable && !locked;
        Ui.setEnabledWithAlpha(adultToggle, editable);
        Ui.setEnabledWithAlpha(shortVideoToggle, editable);
        Ui.setEnabledWithAlpha(visualToggle, editable && Build.VERSION.SDK_INT >= 34);
        Ui.setEnabledWithAlpha(grayToggle, editable);
        Ui.setEnabledWithAlpha(safeSearchToggle, editable);
        setRowEnabled(domainsRow, editable);
        setRowEnabled(wordsRow, editable);
        setRowEnabled(blockedRow, editable);
        setRowEnabled(scannedRow, editable);
        setRowEnabled(browserRow, editable);
        Ui.setEnabledWithAlpha(scheduleEdit, editable);
        Ui.setEnabledWithAlpha(timeLimitEdit, editable);
        if (rulesLockCard == null || rulesLockText == null) return;
        rulesLockCard.setVisibility(locked ? View.VISIBLE : View.GONE);
        if (locked) {
            rulesLockText.setText(getString(R.string.rules_lock_until,
                    Durations.remaining(this, remaining)));
        }
    }

    private static void setRowEnabled(Ui.SettingRow row, boolean enabled) {
        if (row == null) return;
        row.root.setEnabled(enabled);
        row.root.setClickable(enabled);
        row.root.setFocusable(enabled);
        row.root.setAlpha(enabled ? 1f : 0.45f);
    }

    /** A short refusal before a dialog opens; the backend still re-checks the change. */
    private boolean refuseIfRulesLocked() {
        try {
            if (settings != null && ProtectionLock.locked(settings.remainingMillis())) {
                Toast.makeText(this, R.string.rules_locked, Toast.LENGTH_LONG).show();
                return true;
            }
            return false;
        } catch (RuntimeException unreadable) {
            Toast.makeText(this, R.string.policy_unavailable, Toast.LENGTH_LONG).show();
            return true;
        }
    }

    private void refreshWatchdogState() {
        if (watchdogState == null) return;
        long last = LayerWatchdog.lastCheckedAt(this);
        boolean notifications = notificationsEnabled();
        notifSettings.setVisibility(notifications ? View.GONE : View.VISIBLE);
        if (last <= 0) {
            setStatus(watchdogState, getString(R.string.watchdog_not_run), Ui.OFF);
            return;
        }
        CharSequence when = DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS);
        String lost = LayerWatchdog.lostSignature(this);
        if (!lost.isEmpty()) {
            setStatus(watchdogState, getString(R.string.watchdog_lost, when), Ui.ATTENTION);
        } else if (!notifications) {
            setStatus(watchdogState, getString(R.string.watchdog_notifications_off), Ui.ATTENTION);
        } else {
            setStatus(watchdogState, getString(R.string.watchdog_last, when), Ui.ON);
        }
    }

    /**
     * Shows what the background check last saw. This is informational: the live DNS/accessibility
     * status is the real state, and the notice is the closed-app signal. A lost signature is read
     * from the same store the job writes, never guessed from the saved switches.
     */
    private void updateAttention(Set<Integer> items) {
        StringBuilder signature = new StringBuilder();
        for (Integer item : items) signature.append(item).append(',');
        if (signature.toString().equals(attentionSignature)) return;
        attentionSignature = signature.toString();
        attentionList.removeAllViews();
        if (items.isEmpty()) {
            attentionCard.setVisibility(View.GONE);
            return;
        }
        if (attentionDetails != null) attentionDetails.setExpanded(false);
        for (Integer item : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setPadding(0, Ui.dp(this, 6), 0, 0);
            View dot = Ui.dot(this, Ui.color(this, R.color.warning), 7);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(Ui.dp(this, 7), Ui.dp(this, 7));
            dotParams.topMargin = Ui.dp(this, 7);
            row.addView(dot, dotParams);
            TextView text = Ui.text(this, getString(item), 14, R.color.text_secondary);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            textParams.setMarginStart(Ui.dp(this, 10));
            row.addView(text, textParams);
            attentionList.addView(row);
        }
        attentionCard.setVisibility(View.VISIBLE);
    }

    private void refreshSetup(GuardHealth guard, DnsVpnService.State network,
            PolicyStore.Snapshot rules, long remaining) {
        if (settings == null || rules == null) {
            setStatus(setupState, getString(R.string.policy_unavailable), Ui.ATTENTION);
            Ui.setEnabledWithAlpha(setupNext, false);
            return;
        }
        boolean appRules = !rules.blockedApps.isEmpty()
                || (rules.schedule.enabled && rules.schedule.isConfigured())
                || (!rules.scannedApps.isEmpty() && (rules.hasWordProtection() || rules.visualEnabled))
                || rules.shortVideoEnabled;
        int shortVideo = rules.shortVideoEnabled ? R.string.short_video_on : R.string.short_video_off;
        setStatus(shortVideoState, getString(shortVideo), rules.shortVideoEnabled ? Ui.ON : Ui.OFF);
        updateToggle(shortVideoToggle, R.string.toggle_short_video,
                getString(rules.shortVideoEnabled ? R.string.short_video_on_short
                        : R.string.short_video_toggle_summary));
        shortVideoToggle.setChecked(rules.shortVideoEnabled);
        // A protection lock freezes the switch in both directions; outside a
        // lock, turning an enabled layer off waits for the period to end.
        Ui.setEnabledWithAlpha(shortVideoToggle, remaining == 0);

        if (rules.adultWordsEnabled) {
            String adultSummary;
            switch (AdultPolicyAssets.state) {
                case READY:
                    int compiled = AdultPolicyAssets.policy().ruleCount();
                    adultSummary = getResources().getQuantityString(R.plurals.adult_mode_ready,
                            compiled, compiled);
                    break;
                case ERROR: adultSummary = getString(R.string.adult_mode_error); break;
                default: adultSummary = getString(R.string.adult_mode_loading); break;
            }
            updateToggle(adultToggle, R.string.toggle_adult, adultSummary);
        } else {
            updateToggle(adultToggle, R.string.toggle_adult, getString(R.string.adult_toggle_summary));
        }
        adultToggle.setChecked(rules.adultWordsEnabled);
        Ui.setEnabledWithAlpha(adultToggle, remaining == 0);

        SetupGuide.Assets assets = !rules.adultWordsEnabled ? SetupGuide.Assets.DISABLED
                : AdultPolicyAssets.state == AdultPolicyAssets.State.READY ? SetupGuide.Assets.READY
                : AdultPolicyAssets.state == AdultPolicyAssets.State.ERROR ? SetupGuide.Assets.ERROR
                : SetupGuide.Assets.LOADING;
        SetupGuide.Visual visual = !rules.visualEnabled ? SetupGuide.Visual.OFF
                : Build.VERSION.SDK_INT < 34 || rules.scannedApps.isEmpty()
                || VisualGuard.health.shown().failure() ? SetupGuide.Visual.FAILED : SetupGuide.Visual.OK;
        currentSetupStep = SetupGuide.next(appRules, DnsVpnService.resumeRequested(this), guard, assets,
                visual, SetupGuide.Network.valueOf(network.name()),
                DnsVpnService.connectionCheck.state == DnsVpnService.CheckState.STALE
                        ? SetupGuide.Probe.IDLE
                        : SetupGuide.Probe.valueOf(DnsVpnService.connectionCheck.state.name()));
        Ui.setEnabledWithAlpha(setupNext, true);
        int nextText = R.string.setup_next;
        switch (currentSetupStep) {
            case CHOOSE_RULES: setTextIfChanged(setupState, R.string.setup_choose_rules); break;
            case CONNECT_SERVICE:
                setTextIfChanged(setupState, R.string.setup_connect);
                nextText = R.string.open_accessibility;
                break;
            case LOAD_POLICY:
                setTextIfChanged(setupState, R.string.setup_policy_loading);
                nextText = R.string.setup_wait;
                Ui.setEnabledWithAlpha(setupNext, false);
                break;
            case REPAIR_POLICY:
                setTextIfChanged(setupState, R.string.adult_mode_error);
                nextText = R.string.setup_retry_policy;
                break;
            case REVIEW_APP_LAYER:
                setTextIfChanged(setupState, R.string.setup_review);
                nextText = R.string.setup_review_app;
                break;
            case REVIEW_VISUAL:
                setTextIfChanged(setupState, R.string.setup_review_visual);
                nextText = R.string.setup_review_visual_button;
                break;
            case ENABLE_DNS:
                setTextIfChanged(setupState, R.string.setup_dns);
                nextText = R.string.dns_start;
                break;
            case STARTING_DNS:
                setTextIfChanged(setupState, R.string.dns_starting);
                nextText = R.string.setup_wait;
                Ui.setEnabledWithAlpha(setupNext, false);
                break;
            case REVIEW_DNS:
                setTextIfChanged(setupState, R.string.dns_degraded);
                nextText = R.string.setup_review_network;
                break;
            case TEST_DNS:
                setTextIfChanged(setupState, R.string.setup_test);
                nextText = R.string.network_check_button;
                break;
            case CHECKING_DNS:
                setTextIfChanged(setupState, R.string.network_check_running);
                nextText = R.string.setup_wait;
                Ui.setEnabledWithAlpha(setupNext, false);
                break;
            case READY:
                setTextIfChanged(setupState, R.string.setup_ready);
                nextText = R.string.network_check_button;
                break;
            case READY_APPS:
                setTextIfChanged(setupState, R.string.setup_ready_apps);
                nextText = R.string.dns_start;
                break;
            case READY_DNS:
                setTextIfChanged(setupState, R.string.setup_ready_dns);
                nextText = R.string.network_check_button;
                break;
        }
        Ui.setTextIfChanged(setupNext, getString(nextText));
        // The DNS card below already owns the start action; hide the duplicate
        // recommendation card on that step.
        setupCard.setVisibility(currentSetupStep == SetupGuide.Step.ENABLE_DNS
                ? View.GONE : View.VISIBLE);
    }

    private void setTextIfChanged(TextView view, int textRes) {
        Ui.setTextIfChanged(view, getString(textRes));
    }

    private void nextSetupStep() {
        if (settings == null) return;
        switch (currentSetupStep) {
            case CHOOSE_RULES:
                PolicyStore.Snapshot rules = settings.snapshot();
                if (rules.schedule.enabled && rules.schedule.isConfigured()) editSchedule();
                else if (!rules.hasWordProtection() && !rules.visualEnabled && rules.blockedApps.isEmpty())
                    editAdultMode();
                else chooseApps(true);
                break;
            case CONNECT_SERVICE: showAccessibilityDisclosure(); break;
            case REPAIR_POLICY: AdultPolicyAssets.retryLoading(this); refreshStatus(); break;
            case REVIEW_APP_LAYER:
                new AlertDialog.Builder(this).setTitle(R.string.setup_review_app)
                        .setMessage(R.string.accessibility_description)
                        .setPositiveButton(android.R.string.ok, null).show();
                break;
            case REVIEW_VISUAL:
                PolicyStore.Snapshot visualRules = settings.snapshot();
                if (visualRules.visualEnabled && visualRules.scannedApps.isEmpty()) chooseApps(true);
                else showPage(PAGE_RULES, true);
                break;
            case ENABLE_DNS:
            case READY_APPS: startDns.performClick(); break;
            case REVIEW_DNS: reviewVpnSettings(); break;
            case TEST_DNS:
            case READY:
            case READY_DNS: checkNetwork.performClick(); break;
            default: break;
        }
    }

    private void showAccessibilityDisclosure() {
        new AlertDialog.Builder(this).setTitle(R.string.accessibility_title)
                .setMessage(BuildConfig.SELF_PROTECTION
                        ? R.string.accessibility_disclosure_direct : R.string.accessibility_disclosure)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.open_accessibility, (dialog, which) ->
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show();
    }

    private int refreshVisual(PolicyStore.Snapshot rules, long remaining, Boolean permission) {
        if (visualState == null) return 0;
        int label;
        int kind;
        int attention = 0;
        if (Build.VERSION.SDK_INT < 34) {
            if (rules.visualEnabled) {
                label = R.string.visual_unsupported;
                kind = Ui.UNSUPPORTED;
                attention = R.string.attention_visual_unsupported;
            } else {
                label = R.string.visual;
                kind = Ui.OFF;
            }
        } else if (!rules.visualEnabled) {
            label = R.string.visual;
            kind = Ui.OFF;
        } else if (rules.scannedApps.isEmpty()) {
            label = R.string.visual_no_apps;
            kind = Ui.ATTENTION;
            attention = R.string.attention_visual_no_apps;
        } else if (!Boolean.TRUE.equals(permission) || !TextGuardService.connected) {
            label = R.string.visual_disconnected;
            kind = Ui.ATTENTION;
            attention = R.string.attention_guard_disconnected;
        } else {
            switch (VisualGuard.health.shown()) {
                case LOADING: label = R.string.visual_loading; kind = Ui.STARTING; break;
                case CHECKING: label = R.string.visual_checking; kind = Ui.STARTING; break;
                case SAMPLED: label = R.string.visual_sampled; kind = Ui.ON; break;
                case UNREADABLE:
                    label = R.string.visual_unreadable;
                    kind = Ui.ATTENTION;
                    attention = R.string.attention_visual;
                    break;
                case INPUT: label = R.string.visual_input; kind = Ui.ON; break;
                case ERROR:
                    label = R.string.visual_error;
                    kind = Ui.ATTENTION;
                    attention = R.string.attention_visual;
                    break;
                default: label = R.string.visual_waiting; kind = Ui.ON; break;
            }
        }
        setStatus(visualState, getString(label), kind);
        updateToggle(visualToggle, R.string.toggle_visual, rules.visualEnabled
                ? getString(label) : getString(R.string.visual_toggle_summary));
        visualToggle.setChecked(rules.visualEnabled);
        Ui.setEnabledWithAlpha(visualToggle,
                Build.VERSION.SDK_INT >= 34 && (!rules.visualEnabled || remaining == 0));
        return attention;
    }

    private void refreshGray(long remaining) {
        if (grayState == null) return;
        ScreenGray.reassert(this);
        boolean wanted = ScreenGray.wanted(this);
        boolean gray = ScreenGray.isGray(this);
        int text;
        int kind;
        if (wanted && remaining > 0) {
            text = R.string.gray_committed;
            kind = Ui.ON;
        } else if (gray) {
            text = R.string.gray_on;
            kind = Ui.ON;
        } else {
            text = R.string.gray_off;
            kind = Ui.OFF;
        }
        setStatus(grayState, getString(text), kind);
        updateToggle(grayToggle, R.string.gray_title, getString(text));
        grayToggle.setChecked(wanted);
        Ui.setEnabledWithAlpha(grayToggle, !wanted || remaining == 0);
    }

    private void refreshSchedule(DailyAppSchedule schedule) {
        if (scheduleState == null) return;
        if (!schedule.enabled || !schedule.isConfigured()) {
            setStatus(scheduleState, getString(R.string.schedule_off), Ui.OFF);
            return;
        }
        LocalTime now = LocalTime.now();
        int minute = now.getHour() * 60 + now.getMinute();
        setStatus(scheduleState, getString(schedule.allApps ? R.string.schedule_summary_all
                        : R.string.schedule_summary, schedule.apps.size(),
                scheduleTime(schedule.startMinute), scheduleTime(schedule.endMinute),
                getString(schedule.activeAt(minute) ? R.string.schedule_window_active
                        : R.string.schedule_window_inactive)), Ui.ON);
    }

    /** Daily time limits are a saved rule plus a separate Android Usage access grant. Show both,
     *  so a configured limit without the grant never reads as enforced. */
    private void refreshTimeLimits(PolicyStore.Snapshot rules) {
        if (timeLimitState == null) return;
        if (!rules.timeLimits.configured()) {
            setStatus(timeLimitState, getString(R.string.time_limits_off), Ui.OFF);
            return;
        }
        int count = rules.timeLimits.size();
        String summary = getResources().getQuantityString(R.plurals.time_limits_summary, count, count);
        Integer single = null;
        boolean same = true;
        for (String app : rules.timeLimits.apps()) {
            int minutes = rules.timeLimits.minutesFor(app);
            if (single == null) single = minutes;
            else if (single != minutes) same = false;
        }
        if (same && single != null) {
            summary = getString(R.string.time_limits_summary_limit, summary, timeLimitText(single));
        }
        boolean access = AppUsage.hasAccess(this);
        if (!access) summary = getString(R.string.time_limits_summary_access, summary);
        setStatus(timeLimitState, summary, access ? Ui.ON : Ui.ATTENTION);
    }

    private String timeLimitText(int minutes) {
        return minutes >= 60 && minutes % 60 == 0
                ? getString(R.string.time_limits_hours, minutes / 60)
                : getString(R.string.time_limits_minutes, minutes);
    }

    private CharSequence scheduleAppsCount(boolean keep, int count) {
        return getResources().getQuantityString(keep ? R.plurals.kept_apps : R.plurals.selected_apps, count, count);
    }

    /** The bundled list's signed build date, in the reader's own date format. */
    private static String seedDate(String isoDate) {
        try {
            return java.time.LocalDate.parse(isoDate).format(java.time.format.DateTimeFormatter
                    .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                    .withLocale(java.util.Locale.getDefault()));
        } catch (RuntimeException unparsable) { return isoDate; }
    }

    private String scheduleTime(int minute) {
        java.text.DateFormat format = android.text.format.DateFormat.getTimeFormat(this);
        // Format a wall-clock minute without normalizing it through a local DST transition.
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(minute * 60_000L));
    }

    // ------------------------------------------------------------------ dialogs and actions

    private void editAdultMode() {
        if (settings == null || refuseIfRulesLocked()) return;
        boolean enabled = settings.snapshot().adultWordsEnabled;
        new AlertDialog.Builder(this).setTitle(enabled ? R.string.adult_mode_disable : R.string.adult_mode_enable)
                .setMessage(R.string.adult_mode_disclosure).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save_rules, (dialog, which) -> {
                    try {
                        PolicyStore.Snapshot rules = settings.snapshot();
                        settings.update(settings.domainText(), settings.wordText(), rules.blockedApps,
                                rules.scannedApps, !enabled);
                        if (!enabled) {
                            AdultPolicyAssets.ensureLoaded(this);
                            refreshStatus();
                            if (settings.snapshot().scannedApps.isEmpty()) chooseApps(true);
                        } else {
                            refreshStatus();
                        }
                    } catch (RuntimeException refused) { showRefusal(refused); }
                }).show();
    }

    private void editShortVideo() {
        if (settings == null || refuseIfRulesLocked()) return;
        boolean enabled = settings.snapshot().shortVideoEnabled;
        new AlertDialog.Builder(this).setTitle(enabled ? R.string.short_video_disable : R.string.short_video_enable)
                .setMessage(R.string.short_video_disclosure).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save_rules, (dialog, which) -> {
                    try {
                        settings.updateShortVideo(!enabled);
                        refreshStatus();
                    } catch (RuntimeException unavailable) {
                        Toast.makeText(this, R.string.rules_locked, Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void editVisualMode() {
        if (settings == null || refuseIfRulesLocked() || Build.VERSION.SDK_INT < 34) return;
        boolean enabled = settings.snapshot().visualEnabled;
        new AlertDialog.Builder(this).setTitle(enabled ? R.string.visual_disable : R.string.visual_enable)
                .setMessage(R.string.visual_disclosure).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save_rules, (dialog, which) -> {
                    try {
                        settings.updateVisual(!enabled);
                        refreshStatus();
                        if (!enabled && settings.snapshot().scannedApps.isEmpty()) chooseApps(true);
                    } catch (RuntimeException refused) { showRefusal(refused); }
                }).show();
    }

    /** With adb's permission Hisn switches it; otherwise Settings has the same switch. */
    private void editGray() {
        if (refuseIfRulesLocked()) return;
        boolean wanted = ScreenGray.wanted(this);
        if (ScreenGray.canControl(this)) {
            if (!ScreenGray.set(this, !wanted, committed()))
                Toast.makeText(this, R.string.gray_committed, Toast.LENGTH_LONG).show();
            refreshStatus();
            return;
        }
        new AlertDialog.Builder(this).setTitle(R.string.gray_title)
                .setMessage(getString(R.string.gray_manual, ScreenGray.GRANT))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.open_accessibility, (dialog, which) ->
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show();
    }

    private boolean committed() {
        try { return settings != null && settings.remainingMillis() > 0; }
        catch (RuntimeException unavailable) { return true; }
    }

    private void editRules(boolean domain) {
        if (settings == null || refuseIfRulesLocked()) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), 0);
        form.addView(Ui.caption(this, getString(R.string.rules_editor_private)));
        EditText input = Ui.input(this, domain ? R.string.domain_hint : R.string.rule_hint, true);
        input.setMinLines(8);
        input.setMaxLines(14);
        input.setText(domain ? settings.domainText() : settings.wordText());
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(TEXT_LIMIT)});
        form.addView(input, Ui.margins(this, 10, 0));
        int lines = countRules(input.getText().toString());
        TextView counter = Ui.caption(this, getResources().getQuantityString(
                R.plurals.editor_lines, lines, lines, MAX_RULE_LINES));
        form.addView(counter, Ui.margins(this, 4, 0));
        TextView error = Ui.caption(this, "");
        error.setTextColor(Ui.color(this, R.color.danger));
        error.setVisibility(View.GONE);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        form.addView(error);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable text) {
                int count = countRules(text.toString());
                counter.setText(getResources().getQuantityString(
                        R.plurals.editor_lines, count, count, MAX_RULE_LINES));
                error.setVisibility(View.GONE);
            }
        });
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(domain ? R.string.edit_domains : R.string.edit_words)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save_rules, null).create();
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                try {
                    PolicyStore.Snapshot old = settings.snapshot();
                    settings.update(domain ? input.getText().toString() : settings.domainText(),
                            domain ? settings.wordText() : input.getText().toString(),
                            old.blockedApps, old.scannedApps);
                    dialog.dismiss();
                    Toast.makeText(this, R.string.rules_saved, Toast.LENGTH_SHORT).show();
                    refreshRuleSummaries();
                    refreshStatus();
                } catch (RuntimeException refused) {
                    error.setText(refusalText(refused));
                    error.setVisibility(View.VISIBLE);
                    input.setError(refusalText(refused));
                }
            });
        });
        showPrivate(dialog);
    }

    private void chooseApps(boolean scan) {
        if (settings == null || refuseIfRulesLocked()) return;
        List<ChosenApps.Entry> apps = ChosenApps.list(this);
        Set<String> chosen = new LinkedHashSet<>(scan
                ? settings.snapshot().scannedApps : settings.snapshot().blockedApps);
        AlertDialog dialog = AppPicker.create(this,
                scan ? R.string.choose_scanned_apps : R.string.choose_blocked_apps,
                R.string.save_rules, apps, chosen, selected -> {
                    try {
                        PolicyStore.Snapshot current = settings.snapshot();
                        settings.update(settings.domainText(), settings.wordText(),
                                scan ? current.blockedApps : selected,
                                scan ? selected : current.scannedApps);
                        Toast.makeText(this, R.string.rules_saved, Toast.LENGTH_SHORT).show();
                        refreshRuleSummaries();
                        refreshStatus();
                        return true;
                    } catch (RuntimeException refused) {
                        showRefusal(refused);
                        return false;
                    }
                });
        showPrivate(dialog);
    }

    private void showBrowserCoverage() {
        if (settings == null || refuseIfRulesLocked()) return;
        List<ChosenApps.Entry> browsers = ChosenApps.browsers(this);
        if (browsers.isEmpty()) {
            Toast.makeText(this, R.string.browsers_none, Toast.LENGTH_SHORT).show();
            return;
        }
        PolicyStore.Snapshot rules = settings.snapshot();
        AlertDialog dialog = BrowserBlock.create(this, browsers, rules.blockedApps,
                rules.scannedApps, inspectionActive(rules), domainsRunning(),
                selected -> {
                    try {
                        PolicyStore.Snapshot current = settings.snapshot();
                        Set<String> blocked = new LinkedHashSet<>(current.blockedApps);
                        for (ChosenApps.Entry browser : browsers) blocked.remove(browser.packageName);
                        blocked.addAll(selected);
                        settings.update(settings.domainText(), settings.wordText(), blocked, current.scannedApps);
                        Toast.makeText(this, R.string.rules_saved, Toast.LENGTH_SHORT).show();
                        refreshRuleSummaries();
                        refreshStatus();
                        return true;
                    } catch (RuntimeException refused) {
                        showRefusal(refused);
                        return false;
                    }
                });
        showPrivate(dialog);
    }

    private void chooseScheduleApps(Set<String> selected, Ui.SettingRow summaryRow, boolean keep) {
        List<ChosenApps.Entry> apps = ChosenApps.list(this);
        Set<String> draft = new LinkedHashSet<>(selected);
        AlertDialog dialog = AppPicker.create(this,
                keep ? R.string.schedule_keep_apps : R.string.schedule_choose_apps,
                R.string.schedule_use_apps, apps, draft, picked -> {
                    selected.clear();
                    selected.addAll(picked);
                    if (summaryRow != null) summaryRow.setSummary(scheduleAppsCount(keep, selected.size()));
                    return true;
                });
        showPrivate(dialog);
    }

    private void editSchedule() {
        if (settings == null || refuseIfRulesLocked()) return;
        final DailyAppSchedule existing;
        try { existing = settings.snapshot().schedule; }
        catch (RuntimeException unavailable) {
            Toast.makeText(this, R.string.policy_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        int[] minutes = {existing.startMinute, existing.endMinute};
        boolean[] allApps = {existing.allApps};
        Set<String> selected = new LinkedHashSet<>(existing.apps);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), 0);
        form.addView(Ui.body(this, getString(R.string.schedule_help)));
        Switch enabled = Ui.toggle(this, getString(R.string.schedule_enable));
        enabled.setChecked(existing.enabled);
        form.addView(enabled, Ui.margins(this, 8, 0));

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        Button start = Ui.button(this, getString(R.string.schedule_start, scheduleTime(minutes[0])),
                Ui.ButtonKind.TONAL);
        Button end = Ui.button(this, getString(R.string.schedule_end, scheduleTime(minutes[1])),
                Ui.ButtonKind.TONAL);
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        LinearLayout.LayoutParams endParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        endParams.setMarginStart(Ui.dp(this, 8));
        times.addView(start, startParams);
        times.addView(end, endParams);
        form.addView(times, Ui.margins(this, 10, 0));
        start.setOnClickListener(view -> new TimePickerDialog(this, (picker, hour, minute) -> {
            minutes[0] = hour * 60 + minute;
            start.setText(getString(R.string.schedule_start, scheduleTime(minutes[0])));
        }, minutes[0] / 60, minutes[0] % 60, android.text.format.DateFormat.is24HourFormat(this)).show());
        end.setOnClickListener(view -> new TimePickerDialog(this, (picker, hour, minute) -> {
            minutes[1] = hour * 60 + minute;
            end.setText(getString(R.string.schedule_end, scheduleTime(minutes[1])));
        }, minutes[1] / 60, minutes[1] % 60, android.text.format.DateFormat.is24HourFormat(this)).show());

        // Two modes: block the apps below, or block everything except the apps below.
        Switch mode = Ui.toggle(this, getString(R.string.schedule_mode_list),
                getString(R.string.schedule_list_help));
        mode.setChecked(existing.allApps);
        form.addView(mode, Ui.margins(this, 8, 0));

        final Ui.SettingRow[] appsRow = {null};
        appsRow[0] = Ui.settingRow(this, R.drawable.ic_apps,
                getString(R.string.schedule_choose_apps), scheduleAppsCount(false, selected.size()),
                view -> chooseScheduleApps(selected, appsRow[0], allApps[0]));
        form.addView(appsRow[0].root, Ui.margins(this, 8, 0));
        form.addView(Ui.caption(this, getString(R.string.schedule_limits)), Ui.margins(this, 10, 0));
        TextView error = Ui.caption(this, "");
        error.setTextColor(Ui.color(this, R.color.danger));
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        error.setVisibility(View.GONE);
        form.addView(error);

        Runnable syncMode = () -> {
            boolean keep = mode.isChecked();
            allApps[0] = keep;
            Ui.setToggleText(mode, getString(keep ? R.string.schedule_mode_all : R.string.schedule_mode_list),
                    getString(keep ? R.string.schedule_all_apps_help : R.string.schedule_list_help));
            appsRow[0].setTitle(getString(keep ? R.string.schedule_keep_apps : R.string.schedule_choose_apps));
            appsRow[0].setSummary(scheduleAppsCount(keep, selected.size()));
            error.setVisibility(View.GONE);
        };
        mode.setOnCheckedChangeListener((button, checked) -> syncMode.run());
        syncMode.run();

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.schedule_edit)
                .setView(scroll).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.schedule_save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    if (minutes[0] == minutes[1]) {
                        error.setText(R.string.schedule_invalid_time);
                        error.setVisibility(View.VISIBLE);
                        return;
                    }
                    boolean keep = allApps[0];
                    if (enabled.isChecked() && selected.isEmpty()) {
                        error.setText(keep ? R.string.schedule_keep_apps_error : R.string.schedule_choose_apps_error);
                        error.setVisibility(View.VISIBLE);
                        return;
                    }
                    try {
                        settings.updateSchedule(new DailyAppSchedule(
                                enabled.isChecked(), minutes[0], minutes[1], selected, keep));
                        dialog.dismiss();
                        refreshStatus();
                        Toast.makeText(this, R.string.rules_saved, Toast.LENGTH_SHORT).show();
                    } catch (RuntimeException refused) {
                        PolicyChangeError reason = PolicyChangeError.of(refused);
                        error.setText(reason == PolicyChangeError.INVALID
                                ? getString(R.string.schedule_invalid) : refusalText(refused));
                        error.setVisibility(View.VISIBLE);
                    }
                }));
        showPrivate(dialog);
    }

    // ------------------------------------------------------------ daily time limits

    private void chooseTimeLimitApps(Set<String> selected, Ui.SettingRow row, Runnable onChanged) {
        List<ChosenApps.Entry> apps = ChosenApps.list(this);
        Set<String> draft = new LinkedHashSet<>(selected);
        AlertDialog dialog = AppPicker.create(this, R.string.time_limits_choose_apps,
                R.string.schedule_use_apps, apps, draft, picked -> {
                    selected.clear();
                    selected.addAll(picked);
                    row.setSummary(getResources().getQuantityString(R.plurals.selected_apps,
                            selected.size(), selected.size()));
                    onChanged.run();
                    return true;
                });
        showPrivate(dialog);
    }

    private void pickTimeLimit(int[] minutes, Ui.SettingRow row, Runnable onChanged) {
        int[] choices = {5, 15, 30, 60, 120, 240, 480, 1440};
        String[] labels = new String[choices.length];
        int checked = 1;
        for (int index = 0; index < choices.length; index++) {
            labels[index] = timeLimitText(choices[index]);
            if (choices[index] == minutes[0]) checked = index;
        }
        new AlertDialog.Builder(this).setTitle(R.string.time_limits_limit_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    minutes[0] = choices[which];
                    row.setSummary(timeLimitText(minutes[0]));
                    onChanged.run();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void editTimeLimits() {
        if (settings == null || refuseIfRulesLocked()) return;
        final PolicyStore.Snapshot current;
        try { current = settings.snapshot(); }
        catch (RuntimeException unavailable) {
            Toast.makeText(this, R.string.policy_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        Set<String> selected = new LinkedHashSet<>(current.timeLimits.apps());
        int[] minutes = {15};
        if (!current.timeLimits.apps().isEmpty()) {
            Integer saved = current.timeLimits.minutesFor(current.timeLimits.apps().iterator().next());
            if (saved != null) minutes[0] = saved;
        }

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), 0);
        form.addView(Ui.body(this, getString(R.string.time_limits_help)));

        boolean access = AppUsage.hasAccess(this);
        Ui.SettingRow accessRow = Ui.settingRow(this, R.drawable.ic_lock,
                getString(R.string.time_limits_access_title),
                getString(access ? R.string.time_limits_access_granted : R.string.time_limits_access_needed),
                view -> {
                    try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); }
                    catch (RuntimeException unavailable) {
                        Toast.makeText(this, R.string.time_limits_access_unavailable, Toast.LENGTH_LONG).show();
                    }
                });
        form.addView(accessRow.root, Ui.margins(this, 8, 0));

        Map<String, String> labels = new LinkedHashMap<>();
        try {
            for (ChosenApps.Entry entry : ChosenApps.list(this)) labels.put(entry.packageName, entry.label);
        } catch (RuntimeException unavailable) {
            // Package names still identify a saved app if labels cannot be read.
        }

        LinearLayout usageList = new LinearLayout(this);
        usageList.setOrientation(LinearLayout.VERTICAL);
        form.addView(Ui.strong(this, getString(R.string.time_limits_today)), Ui.margins(this, 14, 4));
        form.addView(usageList);

        final Runnable[] refreshUsage = {null};
        refreshUsage[0] = () -> {
            usageList.removeAllViews();
            if (selected.isEmpty()) {
                usageList.addView(Ui.caption(this, getString(R.string.time_limits_no_apps)),
                        Ui.margins(this, 0, 2));
                return;
            }
            if (!AppUsage.hasAccess(this)) {
                usageList.addView(Ui.caption(this, getString(R.string.time_limits_usage_unavailable)),
                        Ui.margins(this, 0, 2));
                return;
            }
            int shown = 0;
            for (String app : selected) {
                if (shown >= 20) {
                    usageList.addView(Ui.caption(this, getString(R.string.time_limits_more,
                            selected.size() - shown)), Ui.margins(this, 0, 2));
                    break;
                }
                shown++;
                String label = labels.get(app);
                if (label == null) label = app;
                long used = AppUsage.usedToday(this, app);
                long usedMinutes = Math.max(0, used / 60000L);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(0, Ui.dp(this, 4), 0, 0);
                row.addView(Ui.strong(this, label));
                row.addView(Ui.caption(this, getString(R.string.time_limits_used_today,
                        timeLimitText((int) Math.min(Integer.MAX_VALUE, usedMinutes)),
                        timeLimitText(minutes[0]))));
                ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                bar.setMax(minutes[0]);
                bar.setProgress((int) Math.min(minutes[0], usedMinutes));
                bar.setProgressTintList(ColorStateList.valueOf(Ui.color(this, R.color.accent)));
                bar.setProgressBackgroundTintList(ColorStateList.valueOf(
                        Ui.color(this, R.color.surface_alt)));
                LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 6));
                barParams.topMargin = Ui.dp(this, 4);
                barParams.bottomMargin = Ui.dp(this, 8);
                row.addView(bar, barParams);
                usageList.addView(row);
            }
        };

        final Ui.SettingRow[] appsRow = {null};
        appsRow[0] = Ui.settingRow(this, R.drawable.ic_apps,
                getString(R.string.time_limits_choose_apps),
                getResources().getQuantityString(R.plurals.selected_apps, selected.size(), selected.size()),
                view -> chooseTimeLimitApps(selected, appsRow[0], refreshUsage[0]));
        form.addView(appsRow[0].root, Ui.margins(this, 8, 0));

        final Ui.SettingRow[] limitRow = {null};
        limitRow[0] = Ui.settingRow(this, R.drawable.ic_clock,
                getString(R.string.time_limits_limit_title), timeLimitText(minutes[0]),
                view -> pickTimeLimit(minutes, limitRow[0], refreshUsage[0]));
        form.addView(limitRow[0].root, Ui.margins(this, 8, 0));
        refreshUsage[0].run();
        form.addView(Ui.caption(this, getString(R.string.time_limits_limits)), Ui.margins(this, 10, 0));

        TextView error = Ui.caption(this, "");
        error.setTextColor(Ui.color(this, R.color.danger));
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        error.setVisibility(View.GONE);
        form.addView(error);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.section_time_limits)
                .setView(scroll).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.time_limits_save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        app.hisn.core.AppTimeLimit limits;
                        if (selected.isEmpty()) {
                            limits = app.hisn.core.AppTimeLimit.none();
                        } else {
                            java.util.Map<String, Integer> map = new java.util.LinkedHashMap<>();
                            for (String app : selected) map.put(app, minutes[0]);
                            limits = new app.hisn.core.AppTimeLimit(map);
                        }
                        settings.updateTimeLimits(limits);
                        dialog.dismiss();
                        refreshStatus();
                        Toast.makeText(this, AppUsage.hasAccess(this) || selected.isEmpty() ? R.string.rules_saved
                                : R.string.time_limits_saved_access_needed, Toast.LENGTH_LONG).show();
                    } catch (RuntimeException refused) {
                        PolicyChangeError reason = PolicyChangeError.of(refused);
                        error.setText(reason == PolicyChangeError.INVALID
                                ? getString(R.string.time_limits_invalid) : refusalText(refused));
                        error.setVisibility(View.VISIBLE);
                    }
                }));
        showPrivate(dialog);
    }

    private void chooseCommitment() {
        if (settings == null || !settings.snapshot().hasConfiguredRestrictions()) {
            Toast.makeText(this, R.string.commitment_needs_rules, Toast.LENGTH_LONG).show();
            return;
        }
        int[] days = {1, 7, 30, 90};
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        choices.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 8));
        choices.addView(Ui.caption(this, getString(R.string.commitment_choose_period)),
                Ui.margins(this, 0, 6));
        AlertDialog chooser = new AlertDialog.Builder(this)
                .setTitle(R.string.commitment_setup).setView(choices)
                .setNegativeButton(android.R.string.cancel, null).create();
        for (int at = 0; at < days.length; at++) {
            final int period = days[at];
            Button option = Ui.button(this,
                    getResources().getQuantityString(R.plurals.days, period, period),
                    Ui.ButtonKind.GHOST);
            option.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            option.setOnClickListener(view -> {
                chooser.dismiss();
                confirmCommitment(period);
            });
            choices.addView(option, Ui.buttonParams(this));
        }
        chooser.show();
    }

    private void confirmCommitment(int days) {
        String label = getResources().getQuantityString(R.plurals.days, days, days);
        new AlertDialog.Builder(this).setTitle(label).setMessage(R.string.commitment_disclosure)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.commitment_start, (confirm, which) -> {
                    try {
                        settings.startCommitment(days);
                        UninstallProtectionController.sync(this);
                        // A lock arms removal protection on its own: copy the boot
                        // state before the next restart and keep the loss check scheduled.
                        BootShieldService.mirror(this, ProtectionLock.bootShieldArmed(
                                BuildConfig.SELF_PROTECTION, settings.uninstallLock(),
                                settings.remainingMillis()));
                        try { LayerWatchdog.syncSchedule(this); }
                        catch (RuntimeException unavailable) { /* The next open retries. */ }
                        if (BuildConfig.SELF_PROTECTION && !Settings.canDrawOverlays(this)) {
                            Toast.makeText(this, R.string.boot_shield_permission, Toast.LENGTH_LONG).show();
                            try {
                                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:" + getPackageName())));
                            } catch (RuntimeException unavailable) { /* The guard still works. */ }
                        }
                        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                                android.Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{
                                    android.Manifest.permission.POST_NOTIFICATIONS}, 102);
                        }
                        refreshStatus();
                    } catch (RuntimeException refused) { showRefusal(refused); }
                }).show();
    }

    private void reviewVpnSettings() {
        long remaining = 0;
        boolean unknown = false;
        if (settings != null) {
            try { remaining = settings.remainingMillis(); unknown = settings.commitmentUnknown(); }
            catch (RuntimeException unavailable) {
                Toast.makeText(this, R.string.policy_unavailable, Toast.LENGTH_LONG).show();
            }
        }
        if (remaining <= 0) {
            startActivity(new Intent(Settings.ACTION_VPN_SETTINGS));
            return;
        }
        new AlertDialog.Builder(this).setTitle(R.string.vpn_settings_reminder_title)
                .setMessage(getString(unknown ? R.string.vpn_settings_reminder_body_unknown
                                : R.string.vpn_settings_reminder_body,
                        Durations.remaining(this, remaining)))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.vpn_settings_continue, (dialog, which) ->
                        startActivity(new Intent(Settings.ACTION_VPN_SETTINGS))).show();
    }

    private UninstallProtectionController.Status refreshUninstallState() {
        if (settings == null) {
            setStatus(uninstallState, getString(R.string.policy_unavailable), Ui.ATTENTION);
            Ui.setEnabledWithAlpha(uninstallAction, false);
            managedState.setVisibility(View.GONE);
            return null;
        }
        UninstallProtectionController.Status status = UninstallProtectionController.read(this);
        UninstallProtection.State state = status.state();
        // Keep the pre-unlock boot shield copy in step with the effective lock:
        // a running protection lock arms it even when the keep switch is off.
        BootShieldService.mirror(this, ProtectionLock.bootShieldArmed(BuildConfig.SELF_PROTECTION,
                status.keepInstalled, status.remainingMillis));
        // A commitment can expire while this screen is open. Repair a stale
        // platform flag at most every five seconds, never on every UI tick.
        if (state == UninstallProtection.State.OUT_OF_SYNC && status.deviceOwner
                && SystemClock.elapsedRealtime() - lastUninstallSyncAt > 5000) {
            lastUninstallSyncAt = SystemClock.elapsedRealtime();
            UninstallProtectionController.sync(this);
            status = UninstallProtectionController.read(this);
            state = status.state();
        }
        if (BuildConfig.SELF_PROTECTION && !status.deviceOwner) {
            // Direct build without device ownership: the accessibility screen guard
            // is the protection, and a running lock arms it on its own.
            boolean committed = ProtectionLock.locked(status.remainingMillis);
            boolean connected = TextGuardService.connected;
            CharSequence label;
            int kind;
            if (committed && !connected) {
                label = getString(R.string.self_protection_state_lock_inactive);
                kind = Ui.ATTENTION;
            } else if (committed) {
                // The lock states carry the remaining time; keep the format call
                // on the exact resource so lint and the translator see the argument.
                label = settings.installLock()
                        ? getString(R.string.self_protection_state_lock_on_installs,
                        Durations.remaining(this, status.remainingMillis))
                        : getString(R.string.self_protection_state_lock_on,
                        Durations.remaining(this, status.remainingMillis));
                kind = Ui.ON;
            } else if (!status.keepInstalled) {
                label = getString(settings.installLock() ? R.string.self_protection_state_installs_only
                        : R.string.self_protection_state_off);
                kind = settings.installLock() ? Ui.ON : Ui.OFF;
            } else if (connected) {
                label = getString(settings.installLock() ? R.string.self_protection_state_on_installs
                        : R.string.self_protection_state_on);
                kind = Ui.ON;
            } else {
                label = getString(R.string.self_protection_state_inactive);
                kind = Ui.ATTENTION;
            }
            setStatus(uninstallState, label, kind);
            managedState.setVisibility(View.GONE);
            Ui.setEnabledWithAlpha(uninstallAction, true);
            return status;
        }
        int text;
        int kind;
        switch (state) {
            case ADMIN_ONLY: text = R.string.uninstall_state_admin; kind = Ui.OFF; break;
            case READY: text = R.string.uninstall_state_ready; kind = Ui.OFF; break;
            case PROTECTED:
                text = R.string.uninstall_state_protected;
                kind = Ui.ON;
                break;
            case OUT_OF_SYNC: text = R.string.uninstall_state_sync; kind = Ui.ATTENTION; break;
            case UNAVAILABLE:
            default:
                text = status.error != null ? R.string.uninstall_state_error
                        : R.string.uninstall_state_unavailable;
                kind = status.error != null ? Ui.ATTENTION : Ui.OFF;
                break;
        }
        if (state == UninstallProtection.State.PROTECTED && status.remainingMillis > 0) {
            setStatus(uninstallState, getString(R.string.uninstall_state_protected_commitment,
                    Durations.remaining(this, status.remainingMillis)), Ui.ON);
        } else {
            setStatus(uninstallState, getString(text), kind);
        }
        Ui.setEnabledWithAlpha(uninstallAction, true);
        refreshManagedState(status);
        return status;
    }

    /**
     * The managed lockdown is a second opt-in on top of the uninstall block.
     * Show the platform readback, never the saved switch alone: a restriction
     * Android did not accept is described as out of sync, not as protection.
     */
    private void refreshManagedState(UninstallProtectionController.Status status) {
        if (managedState == null) return;
        if (!status.deviceOwner) {
            managedState.setVisibility(View.GONE);
            return;
        }
        managedState.setVisibility(View.VISIBLE);
        if (!status.managedLockdown) {
            setStatus(managedState, getString(R.string.managed_lockdown_off), Ui.OFF);
        } else if (!status.managedMatches()) {
            setStatus(managedState, getString(R.string.managed_lockdown_sync), Ui.ATTENTION);
        } else if (status.desiredRestrictions().isEmpty()) {
            setStatus(managedState, getString(R.string.managed_lockdown_idle), Ui.OFF);
        } else if (status.managedStrict && Build.VERSION.SDK_INT >= 29) {
            setStatus(managedState, getString(R.string.managed_lockdown_strict_on), Ui.ON);
        } else if (status.suspendBlocked) {
            setStatus(managedState, getString(R.string.managed_lockdown_suspend_on), Ui.ON);
        } else {
            setStatus(managedState, getString(R.string.managed_lockdown_on), Ui.ON);
        }
    }

    private void showUninstallDialog() {
        if (settings == null) return;
        UninstallProtectionController.Status status = UninstallProtectionController.read(this);
        boolean commitment = status.remainingMillis > 0;
        boolean screenGuard = BuildConfig.SELF_PROTECTION && !status.deviceOwner;

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 12));
        form.addView(Ui.body(this, getString(status.deviceOwner ? R.string.uninstall_dialog_body_owner
                : screenGuard ? R.string.self_protection_dialog_body
                : R.string.uninstall_dialog_body_unmanaged)));

        Switch keepSwitch = null;
        if (status.deviceOwner || screenGuard) {
            keepSwitch = Ui.toggle(this, getString(screenGuard
                            ? R.string.self_protection_keep : R.string.uninstall_keep),
                    getString(screenGuard ? (commitment ? R.string.self_protection_commitment
                                    : R.string.uninstall_keep_hint)
                            : commitment ? R.string.uninstall_commitment_auto
                            : R.string.uninstall_keep_hint));
            keepSwitch.setChecked(settings.uninstallLock());
            // Enabling is a tightening and stays available during a protection
            // lock. Disabling is refused until the lock ends. The guard itself is
            // already on for the lock even when this switch reads off.
            keepSwitch.setEnabled(!(commitment && keepSwitch.isChecked()));
            form.addView(keepSwitch, Ui.margins(this, 8, 0));
            if (screenGuard && commitment && !settings.uninstallLock()) {
                form.addView(Ui.caption(this, getString(R.string.self_protection_lock_auto)),
                        Ui.margins(this, 2, 0));
            }
        }
        Switch installSwitch = null;
        if (screenGuard) {
            installSwitch = Ui.toggle(this, getString(R.string.self_protection_install),
                    getString(commitment && settings.installLock()
                            ? R.string.self_protection_commitment
                            : R.string.self_protection_install_hint));
            installSwitch.setChecked(settings.installLock());
            installSwitch.setEnabled(!(commitment && installSwitch.isChecked()));
            form.addView(installSwitch, Ui.margins(this, 8, 0));
        }
        Switch lockdownSwitch = null;
        Switch strictSwitch = null;
        Switch suspendSwitch = null;
        if (status.deviceOwner) {
            lockdownSwitch = Ui.toggle(this, getString(R.string.managed_lockdown_switch));
            lockdownSwitch.setChecked(settings.managedLockdown());
            lockdownSwitch.setEnabled(!(settings.managedLockdown() && commitment));
            form.addView(lockdownSwitch, Ui.margins(this, 8, 0));
            form.addView(Ui.caption(this, getString(R.string.managed_lockdown_hint)),
                    Ui.margins(this, 4, 0));
            if (Build.VERSION.SDK_INT >= 29) {
                strictSwitch = Ui.toggle(this, getString(R.string.managed_strict_switch),
                        getString(R.string.managed_strict_hint));
                strictSwitch.setChecked(settings.managedStrict());
                strictSwitch.setEnabled(!(settings.managedStrict() && commitment));
                form.addView(strictSwitch, Ui.margins(this, 12, 0));
            }
            suspendSwitch = Ui.toggle(this, getString(R.string.managed_suspend_switch),
                    getString(R.string.managed_suspend_hint));
            suspendSwitch.setChecked(settings.suspendBlocked());
            suspendSwitch.setEnabled(!(settings.suspendBlocked() && commitment));
            form.addView(suspendSwitch, Ui.margins(this, 8, 0));
        }
        if (!status.deviceOwner && !screenGuard) {
            form.addView(Ui.caption(this, getString(R.string.uninstall_command_label)),
                    Ui.margins(this, 10, 0));
            form.addView(Ui.commandBlock(this,
                    UninstallProtectionController.provisioningCommand(this)), Ui.margins(this, 6, 0));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final Switch keep = keepSwitch;
        final Switch install = installSwitch;
        final Switch lockdown = lockdownSwitch;
        final Switch strict = strictSwitch;
        final Switch suspend = suspendSwitch;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.uninstall_title).setView(scroll).create();
        form.addView(Ui.divider(this), Ui.margins(this, 14, 2));
        Button primary;
        if (status.deviceOwner || screenGuard) {
            primary = Ui.button(this, R.string.uninstall_apply, Ui.ButtonKind.PRIMARY);
            primary.setOnClickListener(view -> {
                applyUninstallChoice(keep != null && keep.isChecked(),
                        install != null ? install.isChecked() : settings.installLock(),
                        status.deviceOwner && lockdown != null
                                ? lockdown.isChecked() : settings.managedLockdown(),
                        status.deviceOwner && strict != null
                                ? strict.isChecked() : settings.managedStrict(),
                        status.deviceOwner && suspend != null
                                ? suspend.isChecked() : settings.suspendBlocked());
                dialog.dismiss();
            });
        } else {
            primary = Ui.button(this, R.string.uninstall_copy_command, Ui.ButtonKind.PRIMARY);
            primary.setOnClickListener(view -> {
                copyText(UninstallProtectionController.provisioningCommand(this));
                dialog.dismiss();
            });
        }
        form.addView(primary, Ui.buttonParams(this));
        if (status.deviceOwner || screenGuard) {
            if (status.mayRelease()) {
                Button release = Ui.button(this, R.string.uninstall_release, Ui.ButtonKind.GHOST);
                release.setOnClickListener(view -> {
                    dialog.dismiss();
                    confirmReleaseManagedMode();
                });
                form.addView(release, Ui.buttonParams(this));
            } else if (screenGuard && !status.adminActive) {
                // The admin adds a deactivation screen in front of uninstall, which the guard also leaves.
                Button admin = Ui.button(this, R.string.admin_setup, Ui.ButtonKind.GHOST);
                admin.setOnClickListener(view -> {
                    dialog.dismiss();
                    startActivity(UninstallProtectionController.adminIntent(this));
                });
                form.addView(admin, Ui.buttonParams(this));
            }
        } else if (!status.adminActive) {
            Button admin = Ui.button(this, R.string.admin_setup, Ui.ButtonKind.GHOST);
            admin.setOnClickListener(view -> {
                dialog.dismiss();
                startActivity(UninstallProtectionController.adminIntent(this));
            });
            form.addView(admin, Ui.buttonParams(this));
        }
        Button cancel = Ui.button(this, android.R.string.cancel, Ui.ButtonKind.QUIET);
        cancel.setOnClickListener(view -> dialog.dismiss());
        form.addView(cancel, Ui.buttonParams(this));
        dialog.show();
    }

    private void applyUninstallChoice(boolean keepInstalled, boolean installLock, boolean managedLockdown,
                                      boolean managedStrict, boolean suspendBlocked) {
        try {
            // All choices are saved in one write, so the guard switches, the
            // uninstall block and the restrictions can never describe different states.
            settings.updateManagedChoice(keepInstalled, installLock, managedLockdown, managedStrict,
                    suspendBlocked);
        } catch (IllegalStateException locked) {
            Toast.makeText(this, R.string.uninstall_locked, Toast.LENGTH_LONG).show();
            return;
        } catch (RuntimeException unavailable) {
            Toast.makeText(this, R.string.policy_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        UninstallProtectionController.Result result = UninstallProtectionController.sync(this);
        long remaining = 0;
        try { remaining = settings.remainingMillis(); }
        catch (RuntimeException unavailable) { /* The status refresh reports it. */ }
        boolean bootArmed = ProtectionLock.bootShieldArmed(BuildConfig.SELF_PROTECTION,
                keepInstalled, remaining);
        BootShieldService.mirror(this, bootArmed);
        if (bootArmed && !android.provider.Settings.canDrawOverlays(this)) {
            // The boot shield needs this; without it the first moments after a restart stay open.
            Toast.makeText(this, R.string.boot_shield_permission, Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (RuntimeException unavailable) { /* Settings lacks the page; the guard still works. */ }
        } else {
            Toast.makeText(this, result.ok ? R.string.uninstall_saved : R.string.uninstall_failed,
                    Toast.LENGTH_LONG).show();
        }
        try { LayerWatchdog.syncSchedule(this); }
        catch (RuntimeException unavailable) { /* The next open retries. */ }
        if (bootArmed && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            // The watchdog's only signal for a closed app is a notification.
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 102);
        }
        refreshStatus();
    }

    private void confirmReleaseManagedMode() {
        new AlertDialog.Builder(this).setTitle(R.string.uninstall_release)
                .setMessage(R.string.uninstall_release_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.uninstall_release, (dialog, which) -> {
                    String error = UninstallProtectionController.releaseManagedMode(this);
                    if (error == null) {
                        Toast.makeText(this, R.string.uninstall_released, Toast.LENGTH_LONG).show();
                    } else if ("locked".equals(error)) {
                        Toast.makeText(this, R.string.uninstall_locked, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, R.string.uninstall_failed, Toast.LENGTH_LONG).show();
                    }
                    refreshStatus();
                }).show();
    }

    private void copyText(String text) {
        copyText(text, R.string.uninstall_copied);
    }

    private void copyText(String text, int toastRes) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, R.string.uninstall_failed, Toast.LENGTH_LONG).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Hisn diagnostics and setup", text));
        Toast.makeText(this, toastRes, Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ permissions and layers

    private void requestDns() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
        } else prepareDns();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 101) prepareDns();
        else if (requestCode == 102) refreshStatus();
    }

    private void prepareDns() {
        Intent consent = VpnService.prepare(this);
        if (consent != null) startActivityForResult(consent, 100);
        else startDnsService();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) startDnsService();
    }

    private void startDnsService() {
        try {
            startForegroundService(new Intent(this, DnsVpnService.class));
        } catch (RuntimeException unavailable) {
            DnsVpnService.state = DnsVpnService.State.ERROR;
        }
        refreshStatus();
    }

    private void refreshBatteryState() {
        if (batteryState == null || batteryAction == null) return;
        android.os.PowerManager power = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        boolean ignoring = power != null
                && power.isIgnoringBatteryOptimizations(getPackageName());
        batteryState.setText(ignoring ? R.string.battery_allowed : R.string.battery_limited);
        batteryState.setTextColor(Ui.color(this, ignoring ? R.color.text_secondary : R.color.warning));
        batteryAction.setVisibility(ignoring ? View.GONE : View.VISIBLE);
    }

    private void requestBatteryExemption() {
        try {
            Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:" + getPackageName()));
            startActivity(request);
        } catch (RuntimeException unavailable) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                Toast.makeText(this, R.string.battery_unavailable, Toast.LENGTH_LONG).show();
            } catch (RuntimeException missing) {
                Toast.makeText(this, R.string.battery_unavailable, Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean notificationsEnabled() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return false;
        NotificationManager manager = getSystemService(NotificationManager.class);
        return manager != null && manager.areNotificationsEnabled();
    }

    private Boolean guardPermissionEnabled() {
        try {
            AccessibilityManager manager = getSystemService(AccessibilityManager.class);
            if (manager == null) return null;
            for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                android.content.pm.ServiceInfo service = info.getResolveInfo().serviceInfo;
                if (getPackageName().equals(service.packageName)
                        && TextGuardService.class.getName().equals(service.name)) return true;
            }
            return false;
        } catch (RuntimeException unavailable) { return null; }
    }

    // ------------------------------------------------------------------ window privacy and lifecycle

    /** The word tester is private while it is on screen holding the person's text. */
    private void updateTesterPrivacy() {
        testerPrivate = selectedPage == PAGE_GUIDE && testerRule != null
                && (testerRule.length() > 0 || testerSample.length() > 0);
        updateSecure();
    }

    /** FLAG_SECURE: Android leaves the window out of screenshots, screen recording and the recent-apps
     * preview. It does not hide the text from accessibility services or from anyone looking. */
    private void updateSecure() {
        boolean secure = privateDialogs > 0 || testerPrivate;
        if (secure == secured) return; // Each flag change relayouts the window; not on every keystroke.
        secured = secure;
        if (secure) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    /** Shows a dialog holding the person's lists as a secure window. This window is secured too while it
     * is open, because some Android versions build the recent-apps preview from the activity window. */
    private void showPrivate(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.setOnDismissListener(closed -> { privateDialogs--; updateSecure(); });
        privateDialogs++;
        updateSecure();
        dialog.show();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("selectedPage", selectedPage);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
        // A cold launch can briefly keep the splash's light icons over the light page; re-assert
        // the app's own bar appearance whenever the window returns to the foreground.
        Ui.applySystemBarIcons(getWindow(), this);
        // Re-assert managed uninstall state on every foreground visit: a
        // commitment may have expired, or the owner may have changed it while
        // another screen was open. This is a no-op on an unmanaged install.
        UninstallProtectionController.sync(this);
        try { LayerWatchdog.syncSchedule(this); }
        catch (RuntimeException unavailable) { /* The next open retries. */ }
        refreshRuleSummaries();
        refreshBatteryState();
        handler.post(refresh);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.applySystemBarIcons(getWindow(), this);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        if (settings != null) {
            try { settings.checkpoint(); }
            catch (RuntimeException unavailable) {
                Toast.makeText(this, R.string.policy_unavailable, Toast.LENGTH_LONG).show();
            }
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
