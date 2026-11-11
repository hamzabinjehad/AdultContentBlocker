package app.hisn.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.hisn.core.BrowserCoverage;
import app.hisn.core.AppTimeLimit;
import app.hisn.core.DailyAppSchedule;
import app.hisn.core.DistractionSites;
import app.hisn.core.GuardHealth;
import app.hisn.core.HostDecision;
import app.hisn.core.PolicyReceipt;
import app.hisn.core.ProtectionReadiness;
import app.hisn.core.VisualHealth;
import app.hisn.core.PolicyChangeError;
import app.hisn.core.ProtectionLock;
import app.hisn.core.RuleDocument;
import app.hisn.core.RulePreview;
import app.hisn.core.SeedFreshness;
import app.hisn.core.SetupGuide;
import app.hisn.core.SiteCheckInput;
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
    private static final int REQUEST_EXPORT_RULES = 103;
    private static final int REQUEST_IMPORT_RULES = 104;
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

    private TextView appBarTitle;
    private TextView appBarSubtitle;

    private TextView statusTitle;
    private TextView statusBody;
    private View heroRule;
    private Ui.LayerTile layerDns;
    private Ui.LayerTile layerApps;
    private Ui.LayerTile layerImages;
    private LinearLayout setupSection;
    private boolean heroAttention;
    private LinearLayout attentionCard;
    private LinearLayout attentionList;
    private Ui.Disclosure attentionDetails;
    private String attentionSignature = "";
    private LinearLayout restoredCard;
    private Button restoredConfirm;

    private TextView setupState;
    private Button setupNext;
    private TextView networkState;
    private Ui.StatusPill networkPill;
    private TextView seedFreshness;
    private TextView networkCheckState;
    private TextView watchdogState;
    private String diagnosticsText = "";
    private Button startDns;
    private boolean dnsPrimaryStyle;
    private Button checkNetwork;
    private Button notifSettings;
    private Button privateDnsSettings;

    private Ui.SettingRow accessibilityRow;
    private Ui.SettingRow homeBlockedRow;
    private Ui.SettingRow homeTextRow;
    private Ui.SettingRow homeVisualRow;
    private Ui.SettingRow homeScheduleRow;
    private Ui.SettingRow homeTimeRow;
    private Ui.SettingRow homeCommitmentRow;
    private Ui.SettingRow homeUninstallRow;
    private Ui.SettingRow settingsUninstallRow;
    private Ui.SettingRow settingsPermissionsRow;

    private Switch adultToggle;
    private Switch visualToggle;
    private Ui.SettingRow personBlurRow;
    private Ui.SettingRow personResponseRow;
    private Switch shortVideoToggle;
    private Switch grayToggle;
    private Switch safeSearchToggle;
    private Ui.SettingRow domainsRow;
    private Ui.SettingRow wordsRow;
    private Ui.SettingRow checkSiteRow;
    private Ui.SettingRow portableRow;
    private Ui.SettingRow updateRow;
    private Ui.SettingRow blockedRow;
    private Ui.SettingRow scannedRow;
    private Ui.SettingRow browserRow;
    private Ui.SettingRow scheduleRow;
    private TextView timeLimitState;
    private Ui.SettingRow timeLimitRow;
    private LinearLayout rulesLockCard;
    private TextView rulesLockText;
    private LinearLayout setupCard;
    private Ui.Disclosure dnsAdvanced;
    private Ui.SettingRow batteryRow;
    private TextView scheduleState;
    private TextView commitmentStateRules;
    private Ui.SettingRow commitmentRow;
    private final List<Ui.Chip> distractionChipViews = new ArrayList<>();
    private List<DistractionSites.Site> distractionSites = new ArrayList<>();

    private SetupGuide.Step currentSetupStep = SetupGuide.Step.CHOOSE_RULES;
    /** A passed route check is network-scoped and invalidated by change; a day bounds an unchanged one. */
    private static final long DNS_EVIDENCE_MAX_AGE_MILLIS = 24L * 60 * 60 * 1000;
    /** An app/text decision describes the current viewing spell; after ten quiet minutes it is unproven. */
    private static final long APP_EVIDENCE_MAX_AGE_MILLIS = 10L * 60 * 1000;
    /** Frames are sampled continuously while a selected window is visible; five quiet minutes expire it. */
    private static final long VISUAL_EVIDENCE_MAX_AGE_MILLIS = 5L * 60 * 1000;

    /** Open dialogs showing the person's lists, and whether this window currently has FLAG_SECURE. */
    private int privateDialogs;
    private boolean secured;
    private PolicyStore settings;
    private long lastUninstallSyncAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService hostLookup = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "hisn-host-check");
        thread.setDaemon(true);
        return thread;
    });
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
        appBarRow.setPadding(sidePadding, Ui.dp(this, 8), sidePadding, Ui.dp(this, 8));
        ImageView brandMark = Ui.iconInTile(this, R.drawable.ic_shield,
                R.color.accent, 38, 22);
        appBarRow.addView(brandMark);
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        // The bar names the page once. On Protection it carries the product name and tagline;
        // the other pages swap in the page name, so their content needs no second title.
        appBarTitle = Ui.display(this, getString(R.string.app_name), 22);
        appBarTitle.setGravity(Gravity.CENTER_VERTICAL);
        appBarSubtitle = Ui.text(this, getString(R.string.app_tagline), 10,
                R.color.text_tertiary);
        appBarSubtitle.setAllCaps(true);
        if (!Ui.isRtl(this)) appBarSubtitle.setLetterSpacing(0.08f);
        brand.addView(appBarTitle);
        brand.addView(appBarSubtitle);
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
        navBar.setElevation(Ui.dp(this, 2));
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
            item.setMinimumHeight(Ui.dp(this, 62));
            item.setContentDescription(getString(labels[index]));
            FrameLayout wrap = new FrameLayout(this);
            wrap.setBackgroundResource(0);
            ImageView icon = Ui.icon(this, icons[index], R.color.text_secondary, 21);
            wrap.addView(icon, new FrameLayout.LayoutParams(Ui.dp(this, 21), Ui.dp(this, 21), Gravity.CENTER));
            LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 28));
            item.addView(wrap, wrapParams);
            TextView label = Ui.text(this, getString(labels[index]), 12, R.color.text_tertiary);
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
            LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            navParams.setMarginStart(Ui.dp(this, 3));
            navParams.setMarginEnd(Ui.dp(this, 3));
            navRow.addView(item, navParams);
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
                appBarRow.setPadding(sidePadding, Ui.dp(this, 8) + bars.top,
                        sidePadding, Ui.dp(this, 8));
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
            navItems[i].setBackgroundResource(visible ? R.drawable.bg_nav_selected : R.drawable.bg_row);
            navWraps[i].setBackgroundResource(0);
            navIcons[i].setColorFilter(Ui.color(this, visible ? R.color.accent : R.color.text_tertiary));
            navLabels[i].setTextColor(Ui.color(this, visible ? R.color.accent : R.color.text_tertiary));
            navLabels[i].setTypeface(visible ? Ui.medium() : Typeface.create("sans-serif", Typeface.NORMAL));
        }
        if (animate && changes && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            View page = pages[selectedPage];
            page.setAlpha(0f);
            page.animate().alpha(1f).setDuration(140).start();
        } else {
            pages[selectedPage].setAlpha(1f);
        }
        updateAppBar();
        if (selectedPage == PAGE_RULES) refreshRuleSummaries();
    }

    /** Protection keeps the product name; the other pages name themselves in the same bar. */
    private void updateAppBar() {
        if (appBarTitle == null) return;
        boolean home = selectedPage == PAGE_PROTECTION;
        int titleRes = home ? R.string.app_name
                : selectedPage == PAGE_RULES ? R.string.nav_rules : R.string.nav_guide;
        Ui.setTextIfChanged(appBarTitle, getString(titleRes));
        appBarSubtitle.setVisibility(home ? View.VISIBLE : View.GONE);
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
        hero.setBackgroundResource(R.drawable.bg_card);
        hero.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 18));

        heroRule = new View(this);
        heroRule.setBackgroundColor(Ui.color(this, R.color.accent));
        hero.addView(heroRule, new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 2)));

        TextView eyebrow = Ui.eyebrow(this, getString(R.string.nav_protection));
        LinearLayout.LayoutParams eyebrowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        eyebrowParams.topMargin = Ui.dp(this, 14);
        hero.addView(eyebrow, eyebrowParams);

        statusTitle = Ui.display(this, getString(R.string.status_title), 31);
        statusTitle.setGravity(Gravity.START);
        statusTitle.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams statusTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusTitleParams.topMargin = Ui.dp(this, 6);
        hero.addView(statusTitle, statusTitleParams);

        statusBody = Ui.text(this, "", 15, R.color.text_secondary);
        statusBody.setGravity(Gravity.START);
        statusBody.setLineSpacing(Ui.dp(this, 4), 1.18f);
        LinearLayout.LayoutParams statusBodyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusBodyParams.topMargin = Ui.dp(this, 10);
        hero.addView(statusBody, statusBodyParams);
        LinearLayout.LayoutParams heroDivider = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1));
        heroDivider.topMargin = Ui.dp(this, 16);
        heroDivider.bottomMargin = Ui.dp(this, 14);
        hero.addView(Ui.divider(this), heroDivider);
        LinearLayout coverage = new LinearLayout(this);
        coverage.setOrientation(LinearLayout.VERTICAL);
        LinearLayout layerRow = new LinearLayout(this);
        layerRow.setOrientation(LinearLayout.HORIZONTAL);
        layerRow.setGravity(Gravity.CENTER_VERTICAL);
        layerDns = Ui.layerTile(this, getString(R.string.layer_dns), getString(R.string.state_off), Ui.OFF);
        layerApps = Ui.layerTile(this, getString(R.string.layer_apps), getString(R.string.state_off), Ui.OFF);
        layerImages = Ui.layerTile(this, getString(R.string.layer_images), getString(R.string.state_off),
                Ui.OFF);
        Ui.LayerTile[] tiles = {layerDns, layerApps, layerImages};
        for (int i = 0; i < tiles.length; i++) {
            if (i > 0) {
                View divider = new View(this);
                divider.setBackgroundColor(Ui.color(this, R.color.divider));
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        Math.max(1, Ui.dp(this, 1)), Ui.dp(this, 38));
                dividerParams.gravity = Gravity.CENTER_VERTICAL;
                dividerParams.setMarginStart(Ui.dp(this, 10));
                dividerParams.setMarginEnd(Ui.dp(this, 10));
                layerRow.addView(divider, dividerParams);
            }
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            layerRow.addView(tiles[i].root, tileParams);
        }
        coverage.addView(layerRow);
        hero.addView(coverage);
        page.addView(hero, Ui.margins(this, 8, 16));

        attentionCard = Ui.card(this);
        attentionCard.setBackgroundResource(R.drawable.bg_warning_card);
        attentionList = new LinearLayout(this);
        attentionList.setOrientation(LinearLayout.VERTICAL);
        attentionList.setPadding(0, Ui.dp(this, 4), 0, 0);
        attentionDetails = Ui.disclosure(this, getString(R.string.section_attention), attentionList);
        attentionCard.addView(attentionDetails.root);
        attentionCard.setVisibility(View.GONE);
        page.addView(attentionCard, Ui.margins(this, 0, 10));

        // One next action, on paper. The label is hidden with the step on the DNS pages, where
        // the DNS card below owns the same action.
        setupSection = new LinearLayout(this);
        setupSection.setOrientation(LinearLayout.VERTICAL);
        setupSection.addView(Ui.sectionLabel(this, getString(R.string.next_step)));
        setupCard = Ui.card(this);
        setupCard.setPadding(Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16));
        setupState = Ui.text(this, getString(R.string.setup_first), 15, R.color.text_secondary);
        setupState.setLineSpacing(Ui.dp(this, 3), 1.16f);
        setupCard.addView(setupState);
        setupNext = Ui.button(this, R.string.setup_next, Ui.ButtonKind.PRIMARY);
        setupCard.addView(setupNext, Ui.margins(this, 12, 0));
        setupNext.setOnClickListener(view -> nextSetupStep());
        setupSection.addView(setupCard);
        page.addView(setupSection, Ui.margins(this, 0, 14));

        LinearLayout network = Ui.card(this);
        LinearLayout networkHead = new LinearLayout(this);
        networkHead.setOrientation(LinearLayout.HORIZONTAL);
        networkHead.setGravity(Gravity.CENTER_VERTICAL);
        networkHead.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 8));
        TextView networkTitle = Ui.text(this, getString(R.string.section_network), 17,
                R.color.text_primary);
        networkTitle.setTypeface(Ui.medium());
        Ui.heading(networkTitle);
        networkHead.addView(networkTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        networkPill = Ui.statusPill(this, getString(R.string.state_off), Ui.OFF);
        networkHead.addView(networkPill.root);
        network.addView(networkHead);
        networkState = statusLine();
        network.addView(networkState);
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
        startDns = Ui.button(this, R.string.dns_start, Ui.ButtonKind.TONAL);
        network.addView(startDns, Ui.buttonParams(this));
        startDns.setOnClickListener(v -> showDnsConsent());
        Button vpnSettings = Ui.button(this, R.string.dns_settings, Ui.ButtonKind.GHOST);
        LinearLayout advanced = new LinearLayout(this);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.addView(vpnSettings, Ui.buttonParams(this));
        TextView seedVersion = Ui.caption(this, getString(R.string.dns_seed_version,
                BuildConfig.DOMAIN_SEED_VERSION, seedDate(BuildConfig.DOMAIN_SEED_BUILT)));
        seedVersion.setPadding(0, Ui.dp(this, 8), 0, 0);
        advanced.addView(seedVersion);
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
        page.addView(network, Ui.margins(this, 0, 12));

        page.addView(Ui.sectionLabel(this, getString(R.string.home_rules_title)));
        LinearLayout rulesCard = Ui.card(this);

        // One line per rule: the answer sits at the end of the row, so the whole
        // set fits on one screen and the eye can compare states down the column.
        homeBlockedRow = Ui.settingRow(this, R.drawable.ic_apps,
                getString(R.string.choose_blocked_apps), "", getString(R.string.none_chosen),
                view -> chooseApps(false));
        rulesCard.addView(homeBlockedRow.root);
        rulesCard.addView(Ui.divider(this));

        homeTextRow = Ui.settingRow(this, R.drawable.ic_text,
                getString(R.string.home_adult_text), "", getString(R.string.state_off),
                view -> editAdultMode());
        rulesCard.addView(homeTextRow.root);
        rulesCard.addView(Ui.divider(this));

        homeVisualRow = Ui.settingRow(this, R.drawable.ic_image,
                getString(R.string.home_image_checks), "", getString(R.string.state_off),
                view -> editVisualMode());
        rulesCard.addView(homeVisualRow.root);
        rulesCard.addView(Ui.divider(this));

        homeScheduleRow = Ui.settingRow(this, R.drawable.ic_calendar,
                getString(R.string.section_schedule), "", getString(R.string.state_off),
                view -> editSchedule());
        rulesCard.addView(homeScheduleRow.root);
        rulesCard.addView(Ui.divider(this));

        homeTimeRow = Ui.settingRow(this, R.drawable.ic_clock,
                getString(R.string.section_time_limits), "", getString(R.string.state_off),
                view -> editTimeLimits());
        rulesCard.addView(homeTimeRow.root);
        rulesCard.addView(Ui.divider(this));

        homeCommitmentRow = Ui.settingRow(this, R.drawable.ic_lock,
                getString(R.string.section_commitment), "", getString(R.string.state_off),
                view -> chooseCommitment());
        rulesCard.addView(homeCommitmentRow.root);
        rulesCard.addView(Ui.divider(this));

        homeUninstallRow = Ui.settingRow(this, R.drawable.ic_shield,
                getString(R.string.section_uninstall), "", getString(R.string.state_off),
                view -> showUninstallDialog());
        rulesCard.addView(homeUninstallRow.root);
        page.addView(rulesCard, Ui.margins(this, 0, 10));

        standaloneSettingRow(page, R.drawable.ic_pause,
                R.string.pause_voluntary_action, getString(R.string.pause_voluntary_summary),
                view -> startActivity(new Intent(this, RestrictionActivity.class)
                        .putExtra("voluntary", true)));
    }

    // ------------------------------------------------------------------ rules page

    private void buildRulesPage(LinearLayout page) {
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

        // Named sites, not categories. One tap writes the same ordinary domain rules the editor
        // below writes, so a chip can never hide or bypass the saved-list contract.
        page.addView(Ui.sectionLabel(this, getString(R.string.section_common_sites)));
        LinearLayout commonSites = Ui.card(this);
        commonSites.setPadding(Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 4));
        TextView commonIntro = Ui.body(this, getString(R.string.common_sites_intro));
        commonIntro.setPadding(0, Ui.dp(this, 12), 0, 0);
        commonSites.addView(commonIntro);
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(true);
        chipScroll.setHorizontalFadingEdgeEnabled(true);
        chipScroll.setFadingEdgeLength(Ui.dp(this, 24));
        chipScroll.setClipToPadding(false);
        chipScroll.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        distractionSites = DistractionSites.common();
        for (DistractionSites.Site site : distractionSites) {
            Ui.Chip chip = Ui.chip(this, site.label);
            chip.root.setOnClickListener(view -> toggleCommonSite(site));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            chipParams.setMarginEnd(Ui.dp(this, 8));
            chipRow.addView(chip.root, chipParams);
            distractionChipViews.add(chip);
        }
        chipScroll.addView(chipRow, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        commonSites.addView(chipScroll);
        // Establish the leading edge after measurement; ScrollView otherwise restores an
        // arbitrary left offset when an RTL window contains Latin site labels.
        chipScroll.post(() -> chipScroll.fullScroll(Ui.isRtl(this) ? View.FOCUS_RIGHT : View.FOCUS_LEFT));
        TextView commonNote = Ui.caption(this, getString(R.string.common_sites_note));
        commonNote.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 12));
        commonSites.addView(commonNote);
        page.addView(commonSites, Ui.margins(this, 0, 12));

        LinearLayout builtin = addCard(page, R.string.section_builtin);
        adultToggle = Ui.toggle(this, getString(R.string.toggle_adult));
        adultToggle.setOnClickListener(view -> {
            adultToggle.setChecked(!adultToggle.isChecked());
            editAdultMode();
        });
        builtin.addView(adultToggle);
        builtin.addView(Ui.divider(this));
        visualToggle = Ui.toggle(this, getString(R.string.toggle_visual));
        visualToggle.setOnClickListener(view -> {
            visualToggle.setChecked(!visualToggle.isChecked());
            editVisualMode();
        });
        builtin.addView(visualToggle);
        builtin.addView(Ui.divider(this));
        personBlurRow = Ui.settingRow(this, R.drawable.ic_image,
                getString(R.string.person_blur_title), getString(R.string.person_blur_summary),
                getString(R.string.person_blur_off), view -> editPersonBlur());
        builtin.addView(personBlurRow.root);
        builtin.addView(Ui.divider(this));
        personResponseRow = Ui.settingRow(this, R.drawable.ic_text,
                getString(R.string.person_response_title), getString(R.string.person_response_summary),
                getString(R.string.person_response_back), view -> editPersonResponse());
        builtin.addView(personResponseRow.root);
        builtin.addView(Ui.divider(this));
        shortVideoToggle = Ui.toggle(this, getString(R.string.toggle_short_video));
        shortVideoToggle.setOnClickListener(view -> {
            shortVideoToggle.setChecked(!shortVideoToggle.isChecked());
            editShortVideo();
        });
        builtin.addView(shortVideoToggle);
        builtin.addView(Ui.divider(this));
        grayToggle = Ui.toggle(this, getString(R.string.gray_title));
        grayToggle.setOnClickListener(view -> {
            grayToggle.setChecked(!grayToggle.isChecked());
            editGray();
        });
        builtin.addView(grayToggle);

        // The service is a status, not a form: one standalone row answers on/off, and the tap
        // opens the same disclosure the old button did.
        accessibilityRow = standaloneSettingRow(page, R.drawable.ic_text,
                R.string.section_accessibility, "", getString(R.string.state_off),
                view -> showAccessibilityDisclosure());

        LinearLayout custom = addCard(page, R.string.section_rules_custom);
        domainsRow = Ui.settingRow(this, R.drawable.ic_network,
                getString(R.string.edit_domains), getString(R.string.none_saved),
                view -> editRules(true));
        custom.addView(domainsRow.root);
        custom.addView(Ui.divider(this));
        wordsRow = Ui.settingRow(this, R.drawable.ic_text,
                getString(R.string.edit_words), getString(R.string.none_saved),
                view -> editRules(false));
        custom.addView(wordsRow.root);
        custom.addView(Ui.divider(this));
        checkSiteRow = Ui.settingRow(this, R.drawable.ic_search,
                getString(R.string.check_site_title), getString(R.string.check_site_summary),
                view -> showSiteCheck());
        custom.addView(checkSiteRow.root);
        custom.addView(Ui.divider(this));
        portableRow = Ui.settingRow(this, R.drawable.ic_book,
                getString(R.string.rules_file_title), getString(R.string.rules_file_summary),
                view -> showRulesFile());
        custom.addView(portableRow.root);

        LinearLayout appRules = addCard(page, R.string.section_apps);
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

        timeLimitRow = standaloneSettingRow(page, R.drawable.ic_clock,
                R.string.section_time_limits, getString(R.string.state_off), view -> editTimeLimits());
        timeLimitState = timeLimitRow.summary;

        scheduleRow = standaloneSettingRow(page, R.drawable.ic_calendar,
                R.string.section_schedule, getString(R.string.state_off), view -> editSchedule());
        scheduleState = scheduleRow.summary;

        commitmentRow = standaloneSettingRow(page, R.drawable.ic_clock,
                R.string.section_commitment, getString(R.string.state_off),
                view -> chooseCommitment());
        commitmentStateRules = commitmentRow.summary;
    }

    /** A tappable settings row that stands on its own, the way ScreenZen's Options list does. */
    private Ui.SettingRow standaloneSettingRow(LinearLayout page, int iconRes, int titleRes,
            CharSequence summary, View.OnClickListener click) {
        return standaloneSettingRow(page, iconRes, titleRes, summary, null, click);
    }

    private Ui.SettingRow standaloneSettingRow(LinearLayout page, int iconRes, int titleRes,
            CharSequence summary, CharSequence value, View.OnClickListener click) {
        Ui.SettingRow row = Ui.settingRow(this, iconRes, getString(titleRes), summary, value, click);
        row.root.setBackgroundResource(R.drawable.bg_row_card);
        row.root.setPaddingRelative(Ui.dp(this, 26), Ui.dp(this, 12), Ui.dp(this, 26), Ui.dp(this, 12));
        page.addView(row.root, Ui.margins(this, 0, 10));
        return row;
    }

    // ------------------------------------------------------------------ settings page

    private void buildGuidePage(LinearLayout page) {
        page.addView(Ui.sectionLabel(this, getString(R.string.settings_protection_title)));
        LinearLayout protection = Ui.card(this);

        settingsUninstallRow = Ui.settingRow(this, R.drawable.ic_shield,
                getString(R.string.section_uninstall), "", getString(R.string.state_off),
                view -> showUninstallDialog());
        protection.addView(settingsUninstallRow.root);
        protection.addView(Ui.divider(this));

        settingsPermissionsRow = Ui.settingRow(this, R.drawable.ic_text,
                getString(R.string.settings_permissions), getString(R.string.state_off),
                view -> showPermissionsDialog());
        protection.addView(settingsPermissionsRow.root);
        protection.addView(Ui.divider(this));

        batteryRow = Ui.settingRow(this, R.drawable.ic_pulse,
                getString(R.string.battery_title), getString(R.string.state_off),
                view -> requestBatteryExemption());
        protection.addView(batteryRow.root);
        protection.addView(Ui.divider(this));

        Ui.SettingRow guardianRow = Ui.settingRow(this, R.drawable.ic_shield,
                getString(R.string.guardian_pair_title), getString(R.string.settings_guardian_summary),
                view -> startActivity(new Intent(this, GuardianPairActivity.class)));
        protection.addView(guardianRow.root);
        protection.addView(Ui.divider(this));
        updateRow = Ui.settingRow(this, R.drawable.ic_pulse,
                getString(R.string.list_update_title), getString(R.string.list_update_summary),
                view -> showListUpdate());
        protection.addView(updateRow.root);
        page.addView(protection, Ui.margins(this, 0, 12));

        page.addView(Ui.sectionLabel(this, getString(R.string.settings_about_title)));
        LinearLayout about = Ui.card(this);

        Ui.SettingRow intro = Ui.settingRow(this, R.drawable.ic_book,
                getString(R.string.settings_replay_intro), getString(R.string.settings_intro_summary),
                view -> startActivity(new Intent(this, WelcomeActivity.class)));
        about.addView(intro.root);
        about.addView(Ui.divider(this));

        Ui.SettingRow how = Ui.settingRow(this, R.drawable.ic_shield,
                getString(R.string.app_goal_more), getString(R.string.settings_how_summary),
                view -> new AlertDialog.Builder(this).setTitle(R.string.app_goal_title)
                        .setMessage(R.string.app_goal_body)
                        .setPositiveButton(android.R.string.ok, null).show());
        about.addView(how.root);
        about.addView(Ui.divider(this));

        Ui.SettingRow limits = Ui.settingRow(this, R.drawable.ic_info,
                getString(R.string.guide_limits_title), getString(R.string.settings_limits_summary),
                view -> new AlertDialog.Builder(this).setTitle(R.string.guide_limits_title)
                        .setMessage(R.string.guide_limits_body)
                        .setPositiveButton(android.R.string.ok, null).show());
        about.addView(limits.root);
        about.addView(Ui.divider(this));

        Ui.SettingRow privacy = Ui.settingRow(this, R.drawable.ic_lock,
                getString(R.string.section_privacy), getString(R.string.settings_privacy_summary),
                view -> new AlertDialog.Builder(this).setTitle(R.string.section_privacy)
                        .setMessage(getString(R.string.privacy) + "\n\n"
                                + getString(R.string.private_screens))
                        .setPositiveButton(android.R.string.ok, null).show());
        about.addView(privacy.root);
        about.addView(Ui.divider(this));

        Ui.SettingRow diagnostics = Ui.settingRow(this, R.drawable.ic_pulse,
                getString(R.string.section_diagnostics), getString(R.string.settings_diagnostics_summary),
                view -> showDiagnosticsDialog());
        about.addView(diagnostics.root);
        about.addView(Ui.divider(this));

        Ui.SettingRow tester = Ui.settingRow(this, R.drawable.ic_tune,
                getString(R.string.tester), getString(R.string.settings_tester_summary),
                view -> showTesterDialog());
        about.addView(tester.root);
        about.addView(Ui.divider(this));

        Ui.SettingRow notices = Ui.settingRow(this, R.drawable.ic_book,
                getString(R.string.notices_title), "",
                view -> Notices.show(this));
        about.addView(notices.root);
        about.addView(Ui.divider(this));

        Ui.SettingRow aboutRow = Ui.settingRow(this, R.drawable.ic_info,
                getString(R.string.settings_about), getString(R.string.settings_about_summary,
                        BuildConfig.VERSION_NAME),
                view -> new AlertDialog.Builder(this).setTitle(R.string.settings_about)
                        .setMessage(getString(R.string.app_goal_short) + "\n\n"
                                + getString(R.string.subtitle, BuildConfig.VERSION_NAME))
                        .setPositiveButton(android.R.string.ok, null).show());
        about.addView(aboutRow.root);
        page.addView(about, Ui.margins(this, 0, 12));
        refreshBatteryState();
    }

    private void showPermissionsDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        addPermissionRow(form, R.drawable.ic_text, R.string.perm_access_title,
                () -> Permissions.accessibility(this),
                view -> openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        form.addView(Ui.divider(this));
        addPermissionRow(form, R.drawable.ic_clock, R.string.perm_usage_title,
                () -> Permissions.usage(this),
                view -> openSettings(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        form.addView(Ui.divider(this));
        addPermissionRow(form, R.drawable.ic_apps, R.string.perm_overlay_title,
                () -> Permissions.overlay(this),
                view -> openSettings(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName()))));
        form.addView(Ui.divider(this));
        addPermissionRow(form, R.drawable.ic_alert, R.string.perm_notify_title,
                () -> Permissions.notifications(this),
                view -> openSettings(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
        form.addView(Ui.divider(this));
        addPermissionRow(form, R.drawable.ic_pulse, R.string.perm_battery_title,
                () -> Permissions.battery(this), view -> requestBatteryExemption());
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this).setTitle(R.string.settings_permissions).setView(scroll)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private void addPermissionRow(LinearLayout form, int icon, int titleRes,
            java.util.function.BooleanSupplier granted, View.OnClickListener click) {
        boolean on = granted.getAsBoolean();
        Ui.SettingRow row = Ui.settingRow(this, icon, getString(titleRes),
                getString(on ? R.string.perm_on : R.string.perm_off), click);
        form.addView(row.root);
    }

    private void openSettings(Intent intent) {
        try { startActivity(intent); }
        catch (RuntimeException unavailable) {
            Toast.makeText(this, R.string.perm_settings_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void showDiagnosticsDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 8));
        TextView stats = Ui.text(this, diagnosticsText, 13, R.color.text_secondary);
        stats.setTextIsSelectable(true);
        form.addView(stats);
        form.addView(Ui.caption(this, getString(R.string.diagnostics_hint)),
                Ui.margins(this, 8, 0));
        Button copy = Ui.button(this, R.string.copy_diagnostics, Ui.ButtonKind.QUIET);
        copy.setOnClickListener(view -> copyText(diagnosticsText, R.string.diagnostics_copied));
        form.addView(copy, Ui.buttonParams(this));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this).setTitle(R.string.section_diagnostics).setView(scroll)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private void showTesterDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 12));
        form.addView(Ui.body(this, getString(R.string.tester_help)));
        form.addView(Ui.caption(this, getString(R.string.tester_private)), Ui.margins(this, 6, 0));
        EditText rule = Ui.input(this, R.string.rule_hint, true);
        rule.setMinLines(3);
        rule.setMaxLines(6);
        form.addView(rule, Ui.margins(this, 10, 0));
        EditText sample = Ui.input(this, R.string.sample_hint, true);
        sample.setMinLines(2);
        sample.setMaxLines(5);
        form.addView(sample, Ui.margins(this, 8, 0));
        Button check = Ui.button(this, R.string.test_button, Ui.ButtonKind.TONAL);
        form.addView(check, Ui.buttonParams(this));
        TextView result = Ui.text(this, getString(R.string.tester_result_hint), 14,
                R.color.text_tertiary);
        result.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        form.addView(result, Ui.margins(this, 8, 0));

        TextWatcher clearResult = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                result.setText(R.string.tester_result_hint);
                result.setTextColor(Ui.color(MainActivity.this, R.color.text_tertiary));
            }
            @Override public void afterTextChanged(Editable text) { }
        };
        rule.addTextChangedListener(clearResult);
        sample.addTextChangedListener(clearResult);
        check.setOnClickListener(v -> {
            String sampleText = sample.getText().toString();
            if (sampleText.trim().isEmpty()) {
                result.setText(R.string.invalid);
                result.setTextColor(Ui.color(this, R.color.danger));
                return;
            }
            if (sampleText.codePointCount(0, sampleText.length()) > TEXT_LIMIT) {
                result.setText(R.string.too_long);
                result.setTextColor(Ui.color(this, R.color.danger));
                return;
            }
            try {
                WordMatcher matcher = WordRuleList.compile(rule.getText().toString());
                boolean matched = !matcher.matches(sampleText).isEmpty();
                result.setText(matched ? R.string.match : R.string.no_match);
                result.setTextColor(Ui.color(this, matched ? R.color.warning : R.color.success));
            } catch (IllegalArgumentException error) {
                result.setText(R.string.invalid);
                result.setTextColor(Ui.color(this, R.color.danger));
            }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.tester)
                .setView(scroll).setPositiveButton(android.R.string.ok, null).create();
        showPrivate(dialog);
    }

    // ------------------------------------------------------------------ page building helpers

    private LinearLayout addCard(LinearLayout page, int titleRes) {
        page.addView(Ui.sectionLabel(this, getString(titleRes)));
        LinearLayout card = Ui.card(this);
        page.addView(card, Ui.margins(this, 0, 12));
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

    /** A status line that is also the second half of a row's spoken label. */
    private void setRowStatus(Ui.SettingRow row, CharSequence text, int kind) {
        if (row == null) return;
        if (row.value != null) {
            row.setValue(text, kind);
            return;
        }
        setStatus(row.summary, text, kind);
        row.root.setContentDescription(row.title.getText() + ". " + text);
    }

    /** Writes a row's answer where that row keeps it: the trailing value or the second line. */
    private void setRowValue(Ui.SettingRow row, CharSequence text, int kind) {
        if (row == null) return;
        if (row.value != null) row.setValue(text, kind);
        else setRowStatus(row, text, kind);
    }

    /**
     * Renders the guide's numbered paragraphs as a scannable list with a number badge each.
     * Steps may arrive in separate paragraphs or be numbered inline, and Arabic-Indic digits
     * must keep their own numerals.
     */
    private List<View> numberedSteps(String body) {
        List<View> views = new ArrayList<>();
        String[] parts = body.replace("\r\n", "\n").split(
                "\\n\\s*\\n|\\s+(?=[0-9\\u0660-\\u0669\\u06F0-\\u06F9]+\\s*\\.\\s)");
        java.util.regex.Pattern numbered = java.util.regex.Pattern.compile(
                "^\\s*([0-9\\u0660-\\u0669\\u06F0-\\u06F9]+)\\s*\\.\\s*");
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index].trim();
            if (part.isEmpty()) continue;
            String number = java.text.NumberFormat.getIntegerInstance().format(index + 1);
            String text = part;
            java.util.regex.Matcher match = numbered.matcher(part);
            if (match.find()) {
                number = match.group(1);
                text = part.substring(match.end()).trim();
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
            // A wrapped guide line needs the font's descent room; without it the second
            // line can be clipped when the helper removes font padding.
            step.setIncludeFontPadding(true);
            step.setPadding(0, 0, 0, Ui.dp(this, 1));
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
            setDistractionChipsUnavailable();
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
            refreshDistractionChips(rules.domains.blockedRules());
        } catch (RuntimeException unavailable) {
            setRuleSummaries(getString(R.string.policy_unavailable), getString(R.string.policy_unavailable),
                    getString(R.string.policy_unavailable), getString(R.string.policy_unavailable),
                    getString(R.string.policy_unavailable));
            setDistractionChipsUnavailable();
        }
    }

    /** A chip is the answer for the exact site or for a parent rule; both read as blocked. */
    private void refreshDistractionChips(Set<String> savedRules) {
        for (int index = 0; index < distractionChipViews.size(); index++) {
            DistractionSites.Site site = distractionSites.get(index);
            DistractionSites.State state = DistractionSites.state(savedRules, site.domain);
            Ui.Chip chip = distractionChipViews.get(index);
            chip.setSelected(state != DistractionSites.State.NONE);
            chip.root.setContentDescription(site.label);
        }
    }

    private void setDistractionChipsUnavailable() {
        for (Ui.Chip chip : distractionChipViews) chip.setEnabled(false);
    }

    /** One tap writes the normal domain list. A covered chip explains its wider rule instead. */
    private void toggleCommonSite(DistractionSites.Site site) {
        if (refuseIfRulesLocked()) return;
        if (settings == null) {
            Toast.makeText(this, R.string.policy_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            PolicyStore.Snapshot rules = settings.snapshot();
            Set<String> saved = rules.domains.blockedRules();
            DistractionSites.State state = DistractionSites.state(saved, site.domain);
            if (state == DistractionSites.State.COVERED) {
                Toast.makeText(this, getString(R.string.common_sites_covered, site.label),
                        Toast.LENGTH_LONG).show();
                return;
            }
            String next = state == DistractionSites.State.DIRECT
                    ? DistractionSites.remove(saved, site.domain)
                    : DistractionSites.add(saved, site.domain);
            settings.update(next, settings.wordText(), rules.blockedApps, rules.scannedApps,
                    rules.adultWordsEnabled);
            refreshRuleSummaries();
            refreshStatus();
            // Removing the exact line can leave the site covered by a wider saved rule; say so
            // instead of leaving a selected chip that looks like a failed tap.
            if (state == DistractionSites.State.DIRECT
                    && DistractionSites.state(settings.snapshot().domains.blockedRules(), site.domain)
                    == DistractionSites.State.COVERED) {
                Toast.makeText(this, getString(R.string.common_sites_covered, site.label),
                        Toast.LENGTH_LONG).show();
            }
        } catch (RuntimeException refused) {
            showRefusal(refused);
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
        if (homeBlockedRow != null) setRowValue(homeBlockedRow, blocked, Ui.OFF);
        if (scannedRow != null) scannedRow.setSummary(scanned);
        if (browserRow != null) browserRow.setSummary(browsers);
    }

    private void updateToggle(Switch toggle, int titleRes, CharSequence summary) {
        if (toggle == null) return;
        // The switch itself shows the state. The detail behind a layer belongs in
        // the disclosure that appears when it is turned on, not in a second line
        // under every row.
        Ui.setToggleText(toggle, getString(titleRes), null);
    }

    // ------------------------------------------------------------------ refresh

    /** The layer identity the saved settings currently describe, or -1 when unreadable. */
    private long savedIdentity(ProtectionReadiness.Layer layer) {
        if (settings == null) return -1;
        try {
            switch (layer) {
                case DNS: return PolicyEvidence.dns(settings, AdultListUpdater.installedGeneration(this));
                case APPS: return PolicyEvidence.text(settings);
                case VISUAL: return PolicyEvidence.visual(settings);
                default: return -1;
            }
        } catch (RuntimeException unreadable) {
            return -1;
        }
    }

    /**
     * The DNS tile's honest state. A passed route check is evidence for this policy, service
     * session and network only: a policy or network change clears it, and its age is bounded.
     * The service being connected is not evidence.
     */
    private ProtectionReadiness.Status dnsReadiness(DnsVpnService.State current,
            DnsVpnService.CheckState check, boolean privateDnsBypass) {
        boolean chosen = current != DnsVpnService.State.OFF || DnsVpnService.resumeRequested(this);
        boolean failed = current == DnsVpnService.State.DEGRADED
                || current == DnsVpnService.State.ERROR || privateDnsBypass
                || check == DnsVpnService.CheckState.FAILED;
        boolean running = current == DnsVpnService.State.RUNNING
                || current == DnsVpnService.State.DEGRADED;
        long saved = savedIdentity(ProtectionReadiness.Layer.DNS);
        PolicyReceipt receipt = DnsVpnService.connectionCheck.receipt;
        long applied = receipt == null ? -1 : receipt.identity;
        long evidenceAt = -1;
        if (receipt != null && saved >= 0 && receipt.identity == saved
                && receipt.session == DnsVpnService.serviceSession) evidenceAt = receipt.completedAt;
        ProtectionReadiness.Facts facts = chosen
                ? ProtectionReadiness.Facts.expected(null, running,
                        current == DnsVpnService.State.STARTING, failed, evidenceAt, saved, applied)
                : ProtectionReadiness.Facts.off();
        return ProtectionReadiness.evaluate(ProtectionReadiness.Layer.DNS, facts,
                SystemClock.elapsedRealtime(), DNS_EVIDENCE_MAX_AGE_MILLIS);
    }

    /**
     * The selected-app tile's honest state. A bound service that has not completed a scan
     * is not evidence that text was checked; the receipt names the policy and session it
     * belongs to. An app-rule decision and a text pass are both app-layer decisions.
     */
    private ProtectionReadiness.Status appsReadiness(boolean configured, Boolean permission,
            boolean scanIncomplete) {
        long saved = savedIdentity(ProtectionReadiness.Layer.APPS);
        PolicyReceipt receipt = TextGuardService.inspection.receipt();
        long applied = receipt == null ? -1 : receipt.identity;
        long evidenceAt = -1;
        if (receipt != null && saved >= 0 && receipt.identity == saved
                && receipt.session == TextGuardService.serviceSession) evidenceAt = receipt.completedAt;
        ProtectionReadiness.Facts facts = configured
                ? ProtectionReadiness.Facts.expected(permission, TextGuardService.connected, false,
                        scanIncomplete, evidenceAt, saved, applied)
                : ProtectionReadiness.Facts.off();
        return ProtectionReadiness.evaluate(ProtectionReadiness.Layer.APPS, facts,
                SystemClock.elapsedRealtime(), APP_EVIDENCE_MAX_AGE_MILLIS);
    }

    /**
     * The image tile's honest state. Sampling is opt-in and needs a selected window; no
     * inspected sample means "not checked", not "on". The receipt names the policy, guard
     * session and window that produced the sample. An unsupported Android version is a
     * coverage gap, not a failure.
     */
    private ProtectionReadiness.Status visualReadiness(PolicyStore.Snapshot rules, Boolean permission) {
        // The receipt is stamped with elapsedRealtime, so deep sleep ages a sample instead of
        // freezing its age at the moment the screen went off. The sampling cadence itself still
        // uses uptime, because no sampling happens while the screen is off.
        long now = SystemClock.elapsedRealtime();
        if (Build.VERSION.SDK_INT < 34) {
            return ProtectionReadiness.evaluate(ProtectionReadiness.Layer.VISUAL,
                    ProtectionReadiness.Facts.unsupported(), now, VISUAL_EVIDENCE_MAX_AGE_MILLIS);
        }
        boolean chosen = rules != null && rules.visualEnabled && !rules.scannedApps.isEmpty();
        if (!chosen) {
            return ProtectionReadiness.evaluate(ProtectionReadiness.Layer.VISUAL,
                    ProtectionReadiness.Facts.off(), now, VISUAL_EVIDENCE_MAX_AGE_MILLIS);
        }
        VisualHealth.State shown = VisualGuard.health.shown();
        boolean starting = shown == VisualHealth.State.LOADING
                || shown == VisualHealth.State.CHECKING;
        long saved = savedIdentity(ProtectionReadiness.Layer.VISUAL);
        PolicyReceipt receipt = VisualGuard.health.receipt();
        long applied = receipt == null ? -1 : receipt.identity;
        long evidenceAt = -1;
        if (receipt != null && saved >= 0 && receipt.identity == saved
                && receipt.session == TextGuardService.serviceSession) evidenceAt = receipt.completedAt;
        ProtectionReadiness.Facts facts = ProtectionReadiness.Facts.expected(permission,
                TextGuardService.connected, starting, shown.failure(), evidenceAt, saved, applied);
        return ProtectionReadiness.evaluate(ProtectionReadiness.Layer.VISUAL, facts, now,
                VISUAL_EVIDENCE_MAX_AGE_MILLIS);
    }

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
        ProtectionReadiness.Status dnsStatus = dnsReadiness(current, check, privateDnsBypass);
        ProtectionReadiness.Status appsStatus = appsReadiness(accessibilityConfigured, permission,
                scanIncomplete);
        ProtectionReadiness.Status visualStatus = visualReadiness(rules, permission);

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

        boolean dnsExpected = current == DnsVpnService.State.OFF
                && DnsVpnService.resumeRequested(this);
        boolean needsAttention = guard.needsAttention || current == DnsVpnService.State.ERROR
                || current == DnsVpnService.State.DEGRADED || dnsExpected || privateDnsBypass
                || check == DnsVpnService.CheckState.FAILED || visualReason != 0
                || dnsStatus.attention() || appsStatus.attention() || visualStatus.attention()
                || unknown || restored || uninstallAttention || managedAttention
                || watchdogLost || notificationsAttention;
        CharSequence title = getString(needsAttention ? R.string.protection_attention
                : current == DnsVpnService.State.RUNNING || guard == GuardHealth.ACTIVE
                ? R.string.protection_partial : R.string.status_title);
        Ui.setTextIfChanged(statusTitle, title);
        statusTitle.setTextColor(Ui.color(this, needsAttention ? R.color.warning : R.color.text_primary));
        if (heroRule != null && heroAttention != needsAttention) {
            heroAttention = needsAttention;
            heroRule.setBackgroundColor(Ui.color(this,
                    needsAttention ? R.color.warning : R.color.accent));
        }
        Ui.setTextIfChanged(statusBody, !settingsReadable
                ? getString(R.string.policy_unavailable)
                : rulesLocked
                ? getString(R.string.status_body_locked, Durations.remaining(this, remaining))
                : needsAttention
                ? getString(R.string.status_body_attention)
                : current == DnsVpnService.State.RUNNING
                || current == DnsVpnService.State.STARTING
                || guard == GuardHealth.ACTIVE
                ? getString(R.string.status_body_partial)
                : getString(R.string.status_body_off));

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
        // A connected tunnel is not evidence that it blocked the check. Until a route check
        // passes, the tile says so instead of reading "On" because the service is running.
        if (dnsStatus.state == ProtectionReadiness.State.NOT_CHECKED) {
            dnsKind = Ui.OFF;
            dnsText = R.string.state_not_checked;
        } else if (dnsStatus.state == ProtectionReadiness.State.WORKING) {
            dnsKind = Ui.ON;
            dnsText = R.string.state_on;
        }
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
        // Bound and configured is not the same as having completed one inspection.
        if (guard == GuardHealth.ACTIVE
                && appsStatus.state == ProtectionReadiness.State.NOT_CHECKED) {
            appsKind = Ui.OFF;
            appsText = R.string.state_not_checked;
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
                case UNREADABLE:
                case PROTECTED: imagesKind = Ui.ATTENTION; imagesText = R.string.state_attention; break;
                case LOADING:
                case CHECKING: imagesKind = Ui.STARTING; imagesText = R.string.state_starting; break;
                case SAMPLED:
                case WAITING:
                case INPUT: imagesKind = Ui.ON; imagesText = R.string.state_on; break;
                default: imagesKind = Ui.OFF; imagesText = R.string.state_off; break;
            }
        }
        // Opted-in sampling with no inspected sample yet is watched, not verified.
        if (Build.VERSION.SDK_INT >= 34 && rules != null && rules.visualEnabled
                && !rules.scannedApps.isEmpty()
                && visualStatus.state == ProtectionReadiness.State.NOT_CHECKED) {
            imagesKind = Ui.OFF;
            imagesText = R.string.state_not_checked;
        }
        layerDns.setState(getString(dnsText), dnsKind);
        layerApps.setState(getString(appsText), appsKind);
        layerImages.setState(getString(imagesText), imagesKind);
        PolicyReceipt appsReceipt = TextGuardService.inspection.receipt();
        int appsSubject = appsReceipt != null && appsReceipt.kind == PolicyReceipt.Kind.TEXT_SCAN
                ? R.string.evidence_subject_text : R.string.evidence_subject_app;
        layerDns.setDetail(Durations.evidence(this, dnsStatus, R.string.evidence_subject_dns));
        layerApps.setDetail(Durations.evidence(this, appsStatus, appsSubject));
        layerImages.setDetail(Durations.evidence(this, visualStatus, R.string.evidence_subject_image));

        int networkText;
        switch (current) {
            case STARTING: networkText = R.string.dns_starting; break;
            case RUNNING: networkText = R.string.dns_running; break;
            case DEGRADED: networkText = R.string.dns_degraded; break;
            case ERROR: networkText = R.string.dns_error; break;
            default: networkText = R.string.dns_off_body; break;
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
        if (networkPill != null) {
            networkPill.setState(getString(networkKind == Ui.ON ? R.string.state_on
                    : networkKind == Ui.ATTENTION ? R.string.state_attention
                    : networkKind == Ui.STARTING ? R.string.state_starting : R.string.state_off),
                    networkKind);
        }
        privateDnsSettings.setVisibility(privateDnsBypass ? View.VISIBLE : View.GONE);
        if (privateDnsBypass && dnsAdvanced != null) dnsAdvanced.setExpanded(true);

        boolean canStartDns = settings != null
                && (current == DnsVpnService.State.OFF || current == DnsVpnService.State.ERROR);
        startDns.setVisibility(canStartDns ? View.VISIBLE : View.GONE);
        Ui.setTextIfChanged(startDns, getString(R.string.dns_start));
        diagnosticsText = getString(R.string.dns_summary,
                getString(R.string.dns_stats, DnsVpnService.domains, DnsVpnService.blocked.get(),
                        DnsVpnService.forwarded.get(), DnsVpnService.rewritten.get(),
                        DnsVpnService.errors.get()),
                getString(R.string.dns_packet_diagnostics, DnsVpnService.unsupportedPackets.get(),
                        DnsVpnService.unsupportedTcpPackets.get(), DnsVpnService.malformedPackets.get()),
                getString(R.string.dns_benign_packets, DnsVpnService.ignoredPackets.get(),
                        DnsVpnService.refusedTlsConnections.get(),
                        DnsVpnService.blockedResolvers.get()),
                getString(R.string.dns_recoveries, DnsVpnService.recoveries.get()),
                getString(DnsVpnService.networkMonitoring ? R.string.dns_monitor_active
                        : R.string.dns_monitor_inactive, DnsVpnService.networkChanges.get()),
                getString(R.string.dns_cancelled_network_requests,
                        DnsVpnService.cancelledNetworkRequests.get()),
                getString(R.string.dns_tcp_stats, DnsVpnService.tcpQueries.get(),
                        DnsVpnService.tcpReplies.get(), DnsVpnService.udpTruncated.get()));

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
        setRowValue(accessibilityRow, getString(textRes), textKind);
        if (homeTextRow != null) {
            boolean adultOn = rules != null && rules.adultWordsEnabled;
            setRowValue(homeTextRow, getString(!adultOn ? R.string.state_off
                    : guard == GuardHealth.ACTIVE ? R.string.state_on : R.string.state_attention),
                    !adultOn ? Ui.OFF : guard == GuardHealth.ACTIVE ? Ui.ON : Ui.ATTENTION);
        }

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
        setRowStatus(commitmentRow, commitment, commitmentKind);
        if (homeCommitmentRow != null) {
            setRowValue(homeCommitmentRow, !settingsReadable ? getString(R.string.state_attention)
                    : remaining == 0 ? getString(R.string.state_off)
                    : getString(R.string.home_commitment_on, Durations.remaining(this, remaining)),
                    !settingsReadable ? Ui.ATTENTION : remaining == 0 ? Ui.OFF : Ui.ON);
        }
        if (settingsPermissionsRow != null) {
            settingsPermissionsRow.setSummary(getString(R.string.settings_permissions_summary,
                    getString(Permissions.accessibility(this) ? R.string.perm_on : R.string.perm_off),
                    getString(Permissions.notifications(this) ? R.string.perm_on : R.string.perm_off)));
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
        setRowEnabled(scheduleRow, editable);
        setRowEnabled(timeLimitRow, editable);
        for (Ui.Chip chip : distractionChipViews) chip.setEnabled(editable);
        if (rulesLockCard == null || rulesLockText == null) return;
        rulesLockCard.setVisibility(locked ? View.VISIBLE : View.GONE);
        if (locked) {
            rulesLockText.setText(getString(R.string.rules_lock_until,
                    Durations.remaining(this, remaining)));
        }
    }

    /** A frozen row keeps its text readable: a lock replaces the chevron instead of a grey wash. */
    private static void setRowEnabled(Ui.SettingRow row, boolean enabled) {
        if (row == null) return;
        row.root.setEnabled(enabled);
        row.root.setClickable(enabled);
        row.root.setFocusable(enabled);
        row.setLocked(!enabled);
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
        if (attentionDetails != null) attentionDetails.setExpanded(true);
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
        // The DNS card below owns the start action on that step; hide the duplicate
        // hero action and let the DNS button carry the primary emphasis.
        boolean dnsStep = currentSetupStep == SetupGuide.Step.ENABLE_DNS
                || currentSetupStep == SetupGuide.Step.READY_APPS;
        setupSection.setVisibility(dnsStep ? View.GONE : View.VISIBLE);
        if (dnsStep != dnsPrimaryStyle) {
            dnsPrimaryStyle = dnsStep;
            Ui.style(startDns, dnsStep ? Ui.ButtonKind.PRIMARY : Ui.ButtonKind.TONAL);
        }
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

    private void showDnsConsent() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 8));
        form.addView(Ui.body(this, getString(R.string.dns_consent_short)));
        form.addView(Ui.disclosure(this, getString(R.string.full_disclosure),
                getString(R.string.dns_consent_body)).root, Ui.margins(this, 8, 0));
        new AlertDialog.Builder(this).setTitle(R.string.dns_consent_title).setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.dns_continue, (dialog, which) -> requestDns()).show();
    }

    private void showAccessibilityDisclosure() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 8));
        form.addView(Ui.body(this, getString(R.string.accessibility_short)));
        form.addView(Ui.disclosure(this, getString(R.string.full_disclosure),
                getString(BuildConfig.SELF_PROTECTION ? R.string.accessibility_disclosure_direct
                        : R.string.accessibility_disclosure)).root, Ui.margins(this, 8, 0));
        new AlertDialog.Builder(this).setTitle(R.string.accessibility_title).setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.open_accessibility, (dialog, which) ->
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show();
    }

    private int refreshVisual(PolicyStore.Snapshot rules, long remaining, Boolean permission) {
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
                case PROTECTED:
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
        setRowValue(homeVisualRow, getString(!rules.visualEnabled ? R.string.state_off
                : kind == Ui.ON ? R.string.state_on
                : kind == Ui.UNSUPPORTED ? R.string.state_unsupported
                : kind == Ui.STARTING ? R.string.state_starting
                : R.string.state_attention), !rules.visualEnabled ? Ui.OFF : kind);
        updateToggle(visualToggle, R.string.toggle_visual, rules.visualEnabled
                ? getString(label) : getString(R.string.visual_toggle_summary));
        visualToggle.setChecked(rules.visualEnabled);
        Ui.setEnabledWithAlpha(visualToggle,
                Build.VERSION.SDK_INT >= 34 && (!rules.visualEnabled || remaining == 0));
        if (personBlurRow != null) {
            String people = PersonBlur.mode(this);
            String response = PersonBlur.response(this);
            personBlurRow.setValue(getString(PersonBlur.label(people)),
                    PersonBlur.on(people) ? Ui.ON : Ui.OFF);
            boolean enabled = Build.VERSION.SDK_INT >= 34 && rules.visualEnabled && remaining == 0;
            Ui.setEnabledWithAlpha(personBlurRow.root, enabled);
            if (personResponseRow != null) {
                personResponseRow.setValue(getString(PersonBlur.responseLabel(response)),
                        PersonBlur.on(people) ? Ui.ON : Ui.OFF);
                Ui.setEnabledWithAlpha(personResponseRow.root, enabled && PersonBlur.on(people));
            }
        }
        return attention;
    }

    private void refreshGray(long remaining) {
        ScreenGray.reassert(this);
        boolean wanted = ScreenGray.wanted(this);
        boolean gray = ScreenGray.isGray(this);
        updateToggle(grayToggle, R.string.gray_title, getString(wanted && remaining > 0
                ? R.string.gray_committed : gray ? R.string.gray_on : R.string.gray_off));
        grayToggle.setChecked(wanted);
        Ui.setEnabledWithAlpha(grayToggle, !wanted || remaining == 0);
    }

    private void refreshSchedule(DailyAppSchedule schedule) {
        if (!schedule.enabled || !schedule.isConfigured()) {
            setRowStatus(scheduleRow, getString(R.string.schedule_off), Ui.OFF);
            setRowValue(homeScheduleRow, getString(R.string.state_off), Ui.OFF);
            return;
        }
        LocalTime now = LocalTime.now();
        int minute = now.getHour() * 60 + now.getMinute();
        String days = scheduleDays(this, schedule.days);
        setRowStatus(scheduleRow, getString(schedule.allApps ? R.string.schedule_summary_all
                        : R.string.schedule_summary, schedule.apps.size(),
                scheduleTime(schedule.startMinute), scheduleTime(schedule.endMinute),
                getString(schedule.activeAt(minute, java.time.LocalDate.now().getDayOfWeek())
                        ? R.string.schedule_window_active : R.string.schedule_window_inactive), days), Ui.ON);
        setRowValue(homeScheduleRow, getString(R.string.home_schedule_value,
                scheduleTime(schedule.startMinute), scheduleTime(schedule.endMinute), days), Ui.ON);
    }

    /** Daily time limits are a saved rule plus a separate Android Usage access grant. Show both,
     *  so a configured limit without the grant never reads as enforced. */
    private void refreshTimeLimits(PolicyStore.Snapshot rules) {
        if (!rules.timeLimits.configured()) {
            setRowStatus(timeLimitRow, getString(R.string.time_limits_off), Ui.OFF);
            setRowValue(homeTimeRow, getString(R.string.state_off), Ui.OFF);
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
        setRowStatus(timeLimitRow, summary, access ? Ui.ON : Ui.ATTENTION);
        setRowValue(homeTimeRow, access
                ? getResources().getQuantityString(R.plurals.time_limits_summary, count, count)
                : getString(R.string.state_attention), access ? Ui.ON : Ui.ATTENTION);
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

    /** "Every day" or a short count of chosen days, for the schedule row summaries. */
    static String scheduleDays(Context context, int days) {
        if (days == DailyAppSchedule.ALL_DAYS) return context.getString(R.string.schedule_days_every);
        int count = Integer.bitCount(days);
        return context.getResources().getQuantityString(R.plurals.schedule_days_count, count, count);
    }

    // ------------------------------------------------------------------ dialogs and actions

    private void editAdultMode() {
        if (settings == null || refuseIfRulesLocked()) return;
        boolean enabled = settings.snapshot().adultWordsEnabled;
        showLayerConsent(enabled ? R.string.adult_mode_disable : R.string.adult_mode_enable,
                R.string.adult_mode_short, R.string.adult_mode_disclosure, () -> {
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
                });
    }

    private void editShortVideo() {
        if (settings == null || refuseIfRulesLocked()) return;
        boolean enabled = settings.snapshot().shortVideoEnabled;
        showLayerConsent(enabled ? R.string.short_video_disable : R.string.short_video_enable,
                R.string.short_video_short, R.string.short_video_disclosure, () -> {
                    try {
                        settings.updateShortVideo(!enabled);
                        refreshStatus();
                    } catch (RuntimeException unavailable) {
                        Toast.makeText(this, R.string.rules_locked, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void editVisualMode() {
        if (settings == null || refuseIfRulesLocked() || Build.VERSION.SDK_INT < 34) return;
        boolean enabled = settings.snapshot().visualEnabled;
        showLayerConsent(enabled ? R.string.visual_disable : R.string.visual_enable,
                R.string.visual_short, R.string.visual_disclosure, () -> {
                    try {
                        settings.updateVisual(!enabled);
                        refreshStatus();
                        if (!enabled && settings.snapshot().scannedApps.isEmpty()) chooseApps(true);
                    } catch (RuntimeException refused) { showRefusal(refused); }
                });
    }

    /** One short paragraph, then the full disclosure only for anyone who opens it. */
    private void showLayerConsent(int titleRes, int shortRes, int fullRes, Runnable onAccept) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 8));
        form.addView(Ui.body(this, getString(shortRes)));
        form.addView(Ui.disclosure(this, getString(R.string.full_disclosure),
                getString(fullRes)).root, Ui.margins(this, 8, 0));
        new AlertDialog.Builder(this).setTitle(titleRes).setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save_rules, (dialog, which) -> onAccept.run()).show();
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

    /** Read-only hostname explanation. It never opens the destination or stores the query. */
    private void showSiteCheck() {
        if (settings == null) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 8));
        form.addView(Ui.body(this, getString(R.string.check_site_summary)));
        form.addView(Ui.caption(this, getString(R.string.check_site_path_note)), Ui.margins(this, 4, 0));
        EditText input = Ui.input(this, R.string.check_site_hint, false);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        form.addView(input, Ui.margins(this, 10, 0));
        Button check = Ui.button(this, R.string.check_site_button, Ui.ButtonKind.TONAL);
        form.addView(check, Ui.buttonParams(this));
        TextView result = Ui.text(this, "", 14, R.color.text_secondary);
        result.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        form.addView(result, Ui.margins(this, 8, 0));
        Button add = Ui.button(this, R.string.check_site_add, Ui.ButtonKind.QUIET);
        add.setVisibility(View.GONE);
        form.addView(add, Ui.buttonParams(this));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.check_site_title)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null).create();
        final String[] checkedHost = new String[1];
        check.setEnabled(false);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkedHost[0] = null;
                result.setText("");
                add.setVisibility(View.GONE);
                check.setEnabled(s.toString().trim().length() > 0);
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        input.setOnEditorActionListener((view, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_GO) return false;
            if (check.isEnabled()) check.performClick();
            return true;
        });
        check.setOnClickListener(view -> {
            String raw = input.getText().toString();
            if (raw.trim().isEmpty() || !input.isEnabled()) return;
            input.setEnabled(false);
            checkedHost[0] = null;
            result.setText(R.string.check_site_checking);
            result.setTextColor(Ui.color(this, R.color.text_tertiary));
            add.setVisibility(View.GONE);
            check.setEnabled(false);
            hostLookup.execute(() -> {
                String message;
                boolean canAdd = false;
                String host = null;
                try {
                    host = SiteCheckInput.host(raw);
                    HostDecision.Result decision = HostData.policy(this).evaluate(host);
                    message = getString(R.string.check_site_host, host) + "\n"
                            + siteCheckMessage(decision) + "\n"
                            + getString(R.string.check_site_generation, decision.generation);
                    HostListStore.Active source = HostListStore.active(this);
                    if (source.source != HostListStore.Source.INSTALLED) {
                        message += "\n" + getString(R.string.check_site_bundled, source.generation);
                    }
                    if ("installed-older".equals(source.error)) {
                        message += "\n" + getString(R.string.check_site_older);
                    } else if (source.error != null) {
                        message += "\n" + getString(R.string.check_site_degraded);
                    }
                    if (HostData.termsFailed()) {
                        message += "\n" + getString(R.string.check_site_terms_unavailable);
                    }
                    canAdd = decision.outcome == HostDecision.Outcome.NOT_LISTED;
                } catch (SiteCheckInput.Invalid invalid) {
                    message = getString(R.string.check_site_invalid);
                } catch (RuntimeException unavailable) {
                    message = getString(R.string.check_site_unavailable);
                }
                final String shown = message;
                final boolean offer = canAdd;
                final String found = host;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || !dialog.isShowing()) return;
                    input.setEnabled(true);
                    check.setEnabled(true);
                    result.setText(shown);
                    result.setTextColor(Ui.color(this, offer
                            ? R.color.text_secondary : R.color.text_primary));
                    checkedHost[0] = found;
                    add.setVisibility(offer ? View.VISIBLE : View.GONE);
                });
            });
        });
        add.setOnClickListener(view -> {
            String host = checkedHost[0];
            if (host != null) confirmAddSite(host, dialog);
        });
        showPrivate(dialog);
    }

    private void showRulesFile() {
        if (settings == null) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.rules_file_title)
                .setMessage(R.string.rules_file_summary)
                .setPositiveButton(R.string.rules_export, (dialog, which) -> exportRules())
                .setNeutralButton(R.string.rules_import, (dialog, which) -> importRules())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void exportRules() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "hisn-rules.json");
        try { startActivityForResult(intent, REQUEST_EXPORT_RULES); }
        catch (RuntimeException unavailable) { Toast.makeText(this, R.string.rules_export_failed, Toast.LENGTH_LONG).show(); }
    }

    private void importRules() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain"});
        try { startActivityForResult(intent, REQUEST_IMPORT_RULES); }
        catch (RuntimeException unavailable) { Toast.makeText(this, R.string.rules_import_failed, Toast.LENGTH_LONG).show(); }
    }

    private void applyImportedRules(RuleDocument.Result imported) {
        try {
            PolicyStore.Snapshot current = settings.snapshot();
            String domains = RuleDocument.merge(settings.domainText(), imported.blocks);
            String words = RuleDocument.merge(settings.wordText(), imported.words);
            settings.update(domains, words, current.blockedApps, current.scannedApps);
            Toast.makeText(this, R.string.rules_imported, Toast.LENGTH_SHORT).show();
            refreshRuleSummaries();
            refreshStatus();
        } catch (RuntimeException refused) {
            showRefusal(refused);
        }
    }

    private String siteCheckMessage(HostDecision.Result decision) {
        switch (decision.source) {
            case PERSONAL_BLOCK: return getString(R.string.check_site_blocked_personal);
            case ADULT_TLD: return getString(R.string.check_site_blocked_tld);
            case HOST_TERM: return getString(R.string.check_site_blocked_term);
            case PUBLISHED_LIST:
                return getString(R.string.check_site_blocked_list);
            default:
                if (decision.outcome == HostDecision.Outcome.NOT_LISTED) {
                    return getString(R.string.check_site_not_listed);
                }
                if (decision.outcome == HostDecision.Outcome.UNAVAILABLE) {
                    return getString(R.string.check_site_unavailable);
                }
                return getString(R.string.check_site_invalid);
        }
    }

    private void confirmAddSite(String host, AlertDialog checker) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.check_site_add_title, host))
                .setMessage(R.string.check_site_add_body)
                .setPositiveButton(R.string.check_site_add, (dialog, which) -> {
                    try {
                        PolicyStore.Snapshot current = settings.snapshot();
                        String domains = settings.domainText();
                        settings.update(domains.trim().isEmpty() ? host : domains + "\n" + host,
                                settings.wordText(), current.blockedApps, current.scannedApps);
                        Toast.makeText(this, R.string.check_site_added, Toast.LENGTH_SHORT).show();
                        refreshRuleSummaries();
                        refreshStatus();
                        if (checker != null && checker.isShowing()) checker.dismiss();
                    } catch (RuntimeException refused) {
                        showRefusal(refused);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showListUpdate() {
        int generation = AdultListUpdater.installedGeneration(this);
        String current = getString(R.string.list_update_summary, generation) + "\n\n"
                + getString(R.string.list_update_domains_only);
        long verified = AdultListUpdater.verifiedAt(this);
        if (verified > 0) {
            current += "\n" + getString(R.string.list_update_verified,
                    java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(verified)));
        }
        String error = AdultListUpdater.lastError(this);
        if (error != null && !error.isEmpty()) {
            current += "\n" + getString(R.string.list_update_failed, error);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.list_update_title)
                .setMessage(current)
                .setPositiveButton(R.string.list_update_check, (dialog, which) -> {
                    Toast.makeText(this, R.string.list_update_checking, Toast.LENGTH_SHORT).show();
                    AdultListUpdater.check(this, true, result -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (result.error != null && !result.error.isEmpty()) {
                            new AlertDialog.Builder(this).setTitle(R.string.list_update_title)
                                    .setMessage(getString(R.string.list_update_failed, result.error))
                                    .setPositiveButton(android.R.string.ok, null).show();
                        } else {
                            new AlertDialog.Builder(this).setTitle(R.string.list_update_title)
                                    .setMessage(getString(result.updated
                                            ? R.string.list_update_new : R.string.list_update_ok,
                                            result.generation))
                                    .setPositiveButton(android.R.string.ok, null).show();
                        }
                        refreshUpdateRow();
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void refreshUpdateRow() {
        if (updateRow == null) return;
        int generation = AdultListUpdater.installedGeneration(this);
        updateRow.setSummary(getString(generation == BuildConfig.DOMAIN_SEED_VERSION
                ? R.string.list_update_bundled : R.string.list_update_installed, generation));
    }

    private void editPersonBlur() {
        if (settings == null || refuseIfRulesLocked() || Build.VERSION.SDK_INT < 34) return;
        if (!settings.snapshot().visualEnabled) {
            new AlertDialog.Builder(this).setTitle(R.string.person_blur_title)
                    .setMessage(R.string.person_blur_need_visual)
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        String[] modes = {PersonBlur.OFF, PersonBlur.ALL, PersonBlur.WOMEN, PersonBlur.MEN};
        CharSequence[] labels = new CharSequence[modes.length];
        int checked = 0;
        String current = PersonBlur.mode(this);
        for (int i = 0; i < modes.length; i++) {
            labels[i] = getString(PersonBlur.label(modes[i]));
            if (modes[i].equals(current)) checked = i;
        }
        new AlertDialog.Builder(this).setTitle(R.string.person_blur_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    PersonBlur.set(this, modes[which]);
                    if (personBlurRow != null) personBlurRow.setValue(
                            getString(PersonBlur.label(modes[which])),
                            PersonBlur.on(modes[which]) ? Ui.ON : Ui.OFF);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void editPersonResponse() {
        if (settings == null || refuseIfRulesLocked() || Build.VERSION.SDK_INT < 34) return;
        if (!PersonBlur.on(PersonBlur.mode(this))) {
            new AlertDialog.Builder(this).setTitle(R.string.person_response_title)
                    .setMessage(R.string.person_response_need_people)
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        String[] responses = {PersonBlur.BACK, PersonBlur.COVER};
        CharSequence[] labels = {getString(R.string.person_response_back),
                getString(R.string.person_response_cover)};
        int checked = PersonBlur.COVER.equals(PersonBlur.response(this)) ? 1 : 0;
        new AlertDialog.Builder(this).setTitle(R.string.person_response_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    PersonBlur.setResponse(this, responses[which]);
                    if (personResponseRow != null) personResponseRow.setValue(
                            getString(PersonBlur.responseLabel(responses[which])), Ui.ON);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
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
        TextView counter = Ui.caption(this, "");
        form.addView(counter, Ui.margins(this, 4, 0));
        TextView preview = Ui.caption(this, "");
        preview.setTextColor(Ui.color(this, R.color.text_tertiary));
        preview.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        form.addView(preview, Ui.margins(this, 2, 0));
        EditText find = Ui.input(this, R.string.rule_preview_search, false);
        form.addView(find, Ui.margins(this, 6, 0));
        TextView error = Ui.caption(this, "");
        error.setTextColor(Ui.color(this, R.color.danger));
        error.setVisibility(View.GONE);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        form.addView(error);
        final RulePreview.Report[] report = new RulePreview.Report[1];
        Runnable refresh = () -> refreshRulePreview(input.getText().toString(), domain,
                find.getText().toString(), counter, preview, error, report);
        refresh.run();
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable text) { refresh.run(); }
        };
        input.addTextChangedListener(watcher);
        find.addTextChangedListener(watcher);
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
                RulePreview.Report current = report[0];
                if (current == null) {
                    error.setText(R.string.rules_invalid);
                    error.setVisibility(View.VISIBLE);
                    return;
                }
                try {
                    PolicyStore.Snapshot old = settings.snapshot();
                    String accepted = current.acceptedText();
                    settings.update(domain ? accepted : settings.domainText(),
                            domain ? settings.wordText() : accepted,
                            old.blockedApps, old.scannedApps);
                    dialog.dismiss();
                    Toast.makeText(this, getString(R.string.rule_preview_saved, current.accepted.size(),
                            current.duplicates + current.rejected), Toast.LENGTH_SHORT).show();
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

    private void refreshRulePreview(String text, boolean domain, String query, TextView counter,
            TextView preview, TextView error, RulePreview.Report[] report) {
        try {
            RulePreview.Report next = RulePreview.of(text,
                    domain ? RulePreview.Kind.DOMAIN : RulePreview.Kind.WORD, MAX_RULE_LINES);
            report[0] = next;
            int count = next.accepted.size();
            counter.setText(getResources().getQuantityString(
                    R.plurals.editor_lines, count, count, MAX_RULE_LINES));
            StringBuilder detail = new StringBuilder(getString(R.string.rule_preview_counts,
                    next.accepted.size(), next.duplicates, next.rejected));
            String search = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            if (!search.isEmpty()) {
                int matches = 0;
                for (RulePreview.Entry entry : next.entries) {
                    if (entry.status != RulePreview.Status.VALID) continue;
                    if (!entry.normalized.toLowerCase(Locale.ROOT).contains(search)) continue;
                    if (matches == 0) detail.append('\n').append(getString(R.string.rule_preview_search_matches));
                    if (matches < 8) detail.append('\n').append(entry.normalized);
                    matches++;
                }
                if (matches == 0) detail.append('\n').append(getString(R.string.rule_preview_no_matches));
            } else {
                int shown = 0;
                for (RulePreview.Entry entry : next.entries) {
                    if (entry.status == RulePreview.Status.VALID || shown >= 6) continue;
                    String reason = entry.status == RulePreview.Status.DUPLICATE
                            ? getString(R.string.rule_reason_duplicate)
                            : "hostname".equals(entry.reason) ? getString(R.string.rule_reason_hostname)
                            : getString(R.string.rule_reason_word);
                    String raw = entry.raw.length() > 80 ? entry.raw.substring(0, 80) + "…" : entry.raw;
                    detail.append('\n').append(getString(R.string.rule_preview_rejected, raw, reason));
                    shown++;
                }
            }
            preview.setText(detail.toString());
            preview.setTextColor(Ui.color(this, next.hasRejected()
                    ? R.color.warning : R.color.text_tertiary));
            error.setVisibility(View.GONE);
        } catch (RuntimeException invalid) {
            report[0] = null;
            counter.setText(getResources().getQuantityString(
                    R.plurals.editor_lines, countRules(text), countRules(text), MAX_RULE_LINES));
            preview.setText("");
            error.setText(R.string.rules_invalid);
            error.setVisibility(View.VISIBLE);
        }
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

    /** Pick the weekdays a schedule starts on; a zero-day rule is not allowed. */
    private void chooseScheduleDays(int[] days, Ui.SettingRow row) {
        DayOfWeek[] week = DayOfWeek.values();
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        form.addView(Ui.caption(this, getString(R.string.schedule_days_help)));
        boolean[] checked = new boolean[week.length];
        for (int index = 0; index < week.length; index++) {
            boolean on = (days[0] & DailyAppSchedule.bitOf(week[index])) != 0;
            checked[index] = on;
            CheckBox box = new CheckBox(this);
            box.setText(week[index].getDisplayName(TextStyle.FULL, locale));
            box.setTextColor(Ui.color(this, R.color.text_primary));
            box.setTextSize(16);
            box.setMinHeight(Ui.dp(this, 48));
            box.setChecked(on);
            int at = index;
            box.setOnCheckedChangeListener((button, value) -> checked[at] = value);
            form.addView(box);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.schedule_days_title)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.schedule_save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    int mask = 0;
                    for (int index = 0; index < week.length; index++) {
                        if (checked[index]) mask |= DailyAppSchedule.bitOf(week[index]);
                    }
                    if (mask == 0) {
                        Toast.makeText(this, R.string.schedule_days_error, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    days[0] = mask;
                    row.setSummary(scheduleDays(this, mask));
                    dialog.dismiss();
                }));
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
        int[] days = {existing.days};
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

        // The days the window starts on. A window that wraps past midnight
        // carries into the following morning, so the row names the start day
        // and the summary says "Every day" for the common case.
        final Ui.SettingRow[] daysRow = {null};
        daysRow[0] = Ui.settingRow(this, R.drawable.ic_calendar,
                getString(R.string.schedule_days_title), scheduleDays(this, days[0]),
                view -> chooseScheduleDays(days, daysRow[0]));
        form.addView(daysRow[0].root, Ui.margins(this, 10, 0));

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
                                enabled.isChecked(), minutes[0], minutes[1], selected, keep, days[0]));
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
            setHomeUninstall(Ui.ATTENTION);
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
            int kind;
            if (committed && !connected) kind = Ui.ATTENTION;
            else if (committed) kind = Ui.ON;
            else if (!status.keepInstalled) kind = settings.installLock() ? Ui.ON : Ui.OFF;
            else if (connected) kind = Ui.ON;
            else kind = Ui.ATTENTION;
            setHomeUninstall(kind);
            return status;
        }
        switch (state) {
            case PROTECTED: setHomeUninstall(Ui.ON); break;
            case OUT_OF_SYNC: setHomeUninstall(Ui.ATTENTION); break;
            case UNAVAILABLE: setHomeUninstall(status.error != null ? Ui.ATTENTION : Ui.OFF); break;
            default: setHomeUninstall(Ui.OFF); break;
        }
        return status;
    }

    private void setHomeUninstall(int kind) {
        CharSequence summary = getString(kind == Ui.ON ? R.string.state_on
                : kind == Ui.ATTENTION ? R.string.state_attention : R.string.state_off);
        int color = Ui.color(this, kind == Ui.ON ? R.color.success
                : kind == Ui.ATTENTION || (kind == Ui.OFF && BuildConfig.SELF_PROTECTION)
                ? R.color.warning : R.color.text_tertiary);
        for (Ui.SettingRow row : new Ui.SettingRow[] {homeUninstallRow, settingsUninstallRow}) {
            if (row == null) continue;
            if (row.value != null) {
                row.setValue(summary, kind);
                row.value.setTextColor(color);
            } else {
                row.setSummary(summary);
                row.summary.setTextColor(color);
            }
        }
    }

    /** The full platform state, shown only inside the uninstall dialog. */
    private CharSequence uninstallLongStatus(UninstallProtectionController.Status status) {
        if (BuildConfig.SELF_PROTECTION && !status.deviceOwner) {
            boolean committed = ProtectionLock.locked(status.remainingMillis);
            boolean connected = TextGuardService.connected;
            if (settings == null) return getString(R.string.policy_unavailable);
            if (committed && !connected) return getString(R.string.self_protection_state_lock_inactive);
            if (committed) return getString(settings.installLock()
                            ? R.string.self_protection_state_lock_on_installs
                            : R.string.self_protection_state_lock_on,
                    Durations.remaining(this, status.remainingMillis));
            if (!status.keepInstalled) return getString(settings.installLock()
                    ? R.string.self_protection_state_installs_only : R.string.self_protection_state_off);
            if (connected) return getString(settings.installLock()
                    ? R.string.self_protection_state_on_installs : R.string.self_protection_state_on);
            return getString(R.string.self_protection_state_inactive);
        }
        switch (status.state()) {
            case ADMIN_ONLY: return getString(R.string.uninstall_state_admin);
            case READY: return getString(R.string.uninstall_state_ready);
            case PROTECTED:
                return status.remainingMillis > 0
                        ? getString(R.string.uninstall_state_protected_commitment,
                        Durations.remaining(this, status.remainingMillis))
                        : getString(R.string.uninstall_state_protected);
            case OUT_OF_SYNC: return getString(R.string.uninstall_state_sync);
            case UNAVAILABLE:
            default:
                return getString(status.error != null ? R.string.uninstall_state_error
                        : R.string.uninstall_state_unavailable);
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
        TextView current = Ui.caption(this, uninstallLongStatus(status));
        current.setPadding(0, Ui.dp(this, 12), 0, 0);
        form.addView(current);

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
            boolean turningOn = !settings.uninstallLock();
            primary = Ui.button(this, turningOn ? R.string.uninstall_turn_on : R.string.uninstall_apply,
                    Ui.ButtonKind.PRIMARY);
            primary.setOnClickListener(view -> {
                applyUninstallChoice(turningOn || (keep != null && keep.isChecked()),
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
        else if (requestCode == REQUEST_EXPORT_RULES && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri == null) return;
            try (java.io.OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new java.io.IOException("no stream");
                out.write(RuleDocument.export(settings.domainText(), settings.wordText())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                Toast.makeText(this, R.string.rules_exported, Toast.LENGTH_SHORT).show();
            } catch (RuntimeException | java.io.IOException failed) {
                Toast.makeText(this, R.string.rules_export_failed, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_IMPORT_RULES && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri == null) return;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new java.io.IOException("no stream");
                byte[] bytes = HostListStore.read(in, RuleDocument.MAX_BYTES + 1);
                RuleDocument.Result imported = RuleDocument.parse(bytes);
                String body = getString(R.string.rules_import_body,
                        imported.blocks.size(), imported.words.size());
                int skipped = imported.skippedHosts + imported.skippedWords;
                if (skipped > 0) body += "\n" + getString(R.string.rules_import_skipped, skipped);
                if (!imported.unsupported.isEmpty()) {
                    body += "\n" + getString(R.string.rules_import_unsupported,
                            String.join(", ", imported.unsupported));
                }
                new AlertDialog.Builder(this)
                        .setTitle(R.string.rules_import_title)
                        .setMessage(body)
                        .setPositiveButton(R.string.rules_import_apply,
                                (dialog, which) -> applyImportedRules(imported))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            } catch (java.io.IOException tooLarge) {
                Toast.makeText(this, R.string.rules_import_oversized, Toast.LENGTH_LONG).show();
            } catch (RuntimeException invalid) {
                Toast.makeText(this, R.string.rules_import_failed, Toast.LENGTH_LONG).show();
            }
        }
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
        if (batteryRow == null) return;
        boolean ignoring = Permissions.battery(this);
        batteryRow.setSummary(getString(ignoring ? R.string.settings_battery_on
                : R.string.settings_battery_off));
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

    /** The same two sources as Permissions.accessibility: the manager cache, then Android's own value. */
    private Boolean guardPermissionEnabled() {
        try {
            AccessibilityManager manager = getSystemService(AccessibilityManager.class);
            if (manager != null) {
                for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                    android.content.pm.ServiceInfo service = info.getResolveInfo().serviceInfo;
                    if (getPackageName().equals(service.packageName)
                            && TextGuardService.class.getName().equals(service.name)) return true;
                }
            }
            return Permissions.accessibilityInSettings(this);
        } catch (RuntimeException unavailable) { return null; }
    }

    // ------------------------------------------------------------------ window privacy and lifecycle

    /** FLAG_SECURE: Android leaves the window out of screenshots, screen recording and the recent-apps
     * preview. It does not hide the text from accessibility services or from anyone looking. */
    private void updateSecure() {
        boolean secure = privateDialogs > 0;
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
        AdultListUpdater.schedule(this);
        refreshUpdateRow();
        ensureDnsRunning();
        rearmGuardIfNeeded();
        handler.post(refresh);
    }

    /** After an update the saved DNS approval can survive while the foreground tunnel is gone. */
    private void ensureDnsRunning() {
        if (!DnsVpnService.resumeRequested(this)) return;
        DnsVpnService.State current = DnsVpnService.state;
        if (current == DnsVpnService.State.RUNNING || current == DnsVpnService.State.STARTING
                || current == DnsVpnService.State.DEGRADED) return;
        try {
            // VpnService.prepare returns null only while Android still holds the approval.
            if (android.net.VpnService.prepare(this) != null) return;
            startForegroundService(new Intent(this, DnsVpnService.class));
        } catch (RuntimeException unavailable) { /* The dashboard reports the gap. */ }
    }

    /** A system-bound service can stay alive without repeating onServiceConnected after an update. */
    private void rearmGuardIfNeeded() {
        if (TextGuardService.connected || !Permissions.accessibility(this)) return;
        try {
            sendBroadcast(new Intent(TextGuardService.ACTION_REARM).setPackage(getPackageName()));
        } catch (RuntimeException unavailable) { /* The setup card still offers the settings page. */ }
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
        hostLookup.shutdownNow();
        super.onDestroy();
    }
}
