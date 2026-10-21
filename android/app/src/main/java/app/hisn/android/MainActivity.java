package app.hisn.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Calendar;
import java.time.LocalTime;

import app.hisn.core.WordMatcher;
import app.hisn.core.WordRuleList;
import app.hisn.core.GuardHealth;
import app.hisn.core.SetupGuide;
import app.hisn.core.DailyAppSchedule;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityManager;

/** Development preview with explicit consent for experimental DNS filtering. */
public final class MainActivity extends Activity {
    private static final int TEXT_LIMIT = 65536;
    private LinearLayout root;
    private TextView statusTitle;
    private TextView networkState;
    private TextView networkStats;
    private Button startDns;
    private TextView textState;
    private TextView commitmentState;
    private TextView adminState;
    private TextView setupState;
    private TextView adultState;
    private TextView scheduleState;
    private TextView networkCheckState;
    private Button adultToggle;
    private Button checkNetwork;
    private Button setupNext;
    private SetupGuide.Step currentSetupStep = SetupGuide.Step.CHOOSE_RULES;
    private PolicyStore settings;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { refreshStatus(); handler.postDelayed(this, 1000); }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(30), dp(24), dp(32));
        root.setBackgroundColor(Color.rgb(16, 21, 30));
        scroll.addView(root);
        setContentView(scroll);

        try { settings = PolicyStore.get(this); }
        catch (RuntimeException unavailable) { paragraph(R.string.policy_unavailable); }

        heading(R.string.title, 27);
        paragraph(R.string.subtitle);
        statusTitle = heading(R.string.status_title, 21);
        paragraph(R.string.status_body);
        heading(R.string.setup_heading, 21);
        setupState = paragraph(R.string.setup_first);
        setupNext = new Button(this);
        setupNext.setText(R.string.setup_next);
        setupNext.setAllCaps(false);
        root.addView(setupNext, layout());
        setupNext.setOnClickListener(view -> nextSetupStep());
        heading(R.string.layers, 21);
        networkState = paragraph(R.string.network);
        networkStats = paragraph(R.string.dns_stats_empty);
        startDns = new Button(this);
        startDns.setText(R.string.dns_start);
        startDns.setAllCaps(false);
        root.addView(startDns, layout());
        startDns.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.dns_consent_title).setMessage(R.string.dns_consent_body)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.dns_continue, (dialog, which) -> requestDns()).show());
        Button vpnSettings = new Button(this);
        vpnSettings.setText(R.string.dns_settings);
        vpnSettings.setAllCaps(false);
        root.addView(vpnSettings, layout());
        vpnSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_VPN_SETTINGS)));
        networkCheckState = paragraph(R.string.network_check_idle);
        checkNetwork = new Button(this);
        checkNetwork.setText(R.string.network_check_button);
        checkNetwork.setAllCaps(false);
        root.addView(checkNetwork, layout());
        checkNetwork.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.network_check_button).setMessage(R.string.network_check_disclosure)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.network_check_start, (dialog, which) -> {
                    DnsVpnService.requestConnectionCheck(); refreshStatus();
                }).show());
        textState = paragraph(R.string.words);
        paragraph(R.string.text_guard_recheck_note);
        paragraph(R.string.visual);
        commitmentState = paragraph(R.string.commitment);
        heading(R.string.rules_heading, 21);
        paragraph(R.string.rules_help);
        adultState = paragraph(R.string.adult_mode_off);
        adultToggle = new Button(this);
        adultToggle.setText(R.string.adult_mode_enable);
        adultToggle.setAllCaps(false);
        adultToggle.setEnabled(settings != null);
        root.addView(adultToggle, layout());
        adultToggle.setOnClickListener(view -> editAdultMode());
        policyButton(R.string.edit_domains, view -> editRules(true));
        policyButton(R.string.edit_words, view -> editRules(false));
        policyButton(R.string.choose_blocked_apps, view -> chooseApps(false));
        policyButton(R.string.choose_scanned_apps, view -> chooseApps(true));
        scheduleState = paragraph(R.string.schedule_off);
        policyButton(R.string.schedule_edit, view -> editSchedule());
        policyButton(R.string.enable_text_guard, view -> new AlertDialog.Builder(this)
                .setTitle(R.string.accessibility_title).setMessage(R.string.accessibility_disclosure)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.open_accessibility, (dialog, which) ->
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show());
        policyButton(R.string.commitment_setup, view -> chooseCommitment());
        adminState = paragraph(R.string.admin_off);
        policyButton(R.string.admin_setup, view -> new AlertDialog.Builder(this)
                .setTitle(R.string.admin_title).setMessage(R.string.admin_explanation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.admin_continue, (dialog, which) -> {
                    Intent request = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                            .putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, CommitmentAdminReceiver.component(this))
                            .putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explanation));
                    startActivity(request);
                }).show());
        heading(R.string.tester, 21);
        paragraph(R.string.tester_help);

        EditText rule = input(R.string.rule_hint, true);
        EditText sample = input(R.string.sample_hint, true);
        Button check = new Button(this);
        check.setText(R.string.test_button);
        check.setAllCaps(false);
        root.addView(check, layout());
        TextView result = paragraph(R.string.privacy);
        result.setText("");
        result.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        TextWatcher clearResult = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                result.setText("");
            }
            @Override public void afterTextChanged(Editable text) { }
        };
        rule.addTextChangedListener(clearResult);
        sample.addTextChangedListener(clearResult);
        check.setOnClickListener(v -> {
            String ruleText = rule.getText().toString();
            String sampleText = sample.getText().toString();
            if (sampleText.trim().isEmpty()) {
                result.setText(R.string.invalid);
                return;
            }
            if (sampleText.codePointCount(0, sampleText.length()) > TEXT_LIMIT) {
                result.setText(R.string.too_long);
                return;
            }
            try {
                WordMatcher matcher = WordRuleList.compile(ruleText);
                result.setText(matcher.matches(sampleText).isEmpty() ? R.string.no_match : R.string.match);
            } catch (IllegalArgumentException error) {
                result.setText(R.string.invalid);
            }
        });
        paragraph(R.string.privacy);
    }

    @Override protected void onResume() {
        super.onResume();
        handler.post(refresh);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        if (settings != null) {
            try { settings.checkpoint(); }
            catch (RuntimeException unavailable) { Toast.makeText(this, R.string.policy_unavailable, Toast.LENGTH_LONG).show(); }
        }
        super.onPause();
    }

    private void requestDns() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
        } else prepareDns();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 101) prepareDns();
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

    private void refreshStatus() {
        DnsVpnService.State current = DnsVpnService.state;
        int text;
        switch (current) {
            case STARTING: text = R.string.dns_starting; break;
            case RUNNING: text = R.string.dns_running; break;
            case DEGRADED: text = R.string.dns_degraded; break;
            case ERROR: text = R.string.dns_error; break;
            default: text = R.string.network;
        }
        networkState.setText(text);
        boolean accessibilityConfigured = false;
        boolean settingsReadable = settings != null;
        if (settings != null) {
            try {
                PolicyStore.Snapshot rules = settings.snapshot();
                accessibilityConfigured = !rules.blockedApps.isEmpty()
                        || (rules.schedule.enabled && rules.schedule.isConfigured())
                        || (!rules.scannedApps.isEmpty() && rules.hasWordProtection());
                refreshSchedule(rules.schedule);
                if (rules.adultWordsEnabled) AdultPolicyAssets.ensureLoaded(this);
            } catch (RuntimeException unavailable) {
                settingsReadable = false;
                scheduleState.setText(R.string.policy_unavailable);
            }
        }
        GuardHealth guard = GuardHealth.evaluate(guardPermissionEnabled(), TextGuardService.connected,
                accessibilityConfigured, settingsReadable, TextGuardService.lastInspectionPartial
                        || (settingsReadable && settings.snapshot().adultWordsEnabled && AdultPolicyAssets.state != AdultPolicyAssets.State.READY));
        if (guard.needsAttention || current == DnsVpnService.State.ERROR || current == DnsVpnService.State.DEGRADED
                || DnsVpnService.connectionCheck.state == DnsVpnService.CheckState.FAILED)
            statusTitle.setText(R.string.protection_attention);
        else statusTitle.setText(current == DnsVpnService.State.RUNNING || guard == GuardHealth.ACTIVE
                ? R.string.protection_partial : R.string.status_title);
        startDns.setEnabled(settings != null && (current == DnsVpnService.State.OFF || current == DnsVpnService.State.ERROR));
        networkStats.setText(getString(R.string.dns_summary,
                getString(R.string.dns_stats, DnsVpnService.domains, DnsVpnService.blocked.get(),
                        DnsVpnService.forwarded.get(), DnsVpnService.errors.get()),
                getString(R.string.dns_packet_diagnostics, DnsVpnService.unsupportedPackets.get(),
                        DnsVpnService.unsupportedTcpPackets.get(), DnsVpnService.malformedPackets.get()),
                getString(DnsVpnService.networkMonitoring ? R.string.dns_monitor_active : R.string.dns_monitor_inactive,
                        DnsVpnService.networkChanges.get()),
                getString(R.string.dns_cancelled_network_requests, DnsVpnService.cancelledNetworkRequests.get()),
                getString(R.string.dns_tcp_stats, DnsVpnService.tcpQueries.get(),
                        DnsVpnService.tcpReplies.get(), DnsVpnService.udpTruncated.get())));
        switch (DnsVpnService.connectionCheck.state) {
            case CHECKING: networkCheckState.setText(R.string.network_check_running); break;
            case PASSED: networkCheckState.setText(R.string.network_check_passed); break;
            case FAILED: networkCheckState.setText(R.string.network_check_failed); break;
            case STALE: networkCheckState.setText(R.string.network_check_stale); break;
            default: networkCheckState.setText(R.string.network_check_idle);
        }
        checkNetwork.setEnabled((current == DnsVpnService.State.RUNNING || current == DnsVpnService.State.DEGRADED)
                && DnsVpnService.connectionCheck.state != DnsVpnService.CheckState.CHECKING);
        try { refreshSetup(guard, current); }
        catch (RuntimeException unavailable) {
            setupState.setText(R.string.policy_unavailable);
            setupNext.setEnabled(false);
            statusTitle.setText(R.string.protection_attention);
        }
        switch (guard) {
            case ACTIVE: textState.setText(R.string.text_guard_connected); break;
            case LIMITED: textState.setText(R.string.text_guard_partial); break;
            case READY_NO_RULES: textState.setText(R.string.text_guard_no_rules); break;
            case RECONNECT_REQUIRED: textState.setText(R.string.text_guard_reconnect); break;
            case PERMISSION_REQUIRED: textState.setText(R.string.text_guard_permission); break;
            case PERMISSION_UNKNOWN: textState.setText(R.string.text_guard_unknown); break;
            case SETTINGS_UNAVAILABLE: textState.setText(R.string.policy_unavailable); break;
            default: textState.setText(R.string.words);
        }
        adminState.setText(CommitmentAdminReceiver.isActive(this) ? R.string.admin_active : R.string.admin_off);
        if (settings != null) {
            try {
                long remaining = settings.remainingMillis();
                commitmentState.setText(remaining == 0 ? getString(R.string.commitment)
                        : getString(R.string.commitment_remaining, (remaining + 3599999) / 3600000));
            } catch (RuntimeException unavailable) {
                commitmentState.setText(R.string.policy_unavailable);
                statusTitle.setText(R.string.protection_attention);
            }
        }
    }

    private void refreshSetup(GuardHealth guard, DnsVpnService.State network) {
        if (settings == null) { setupState.setText(R.string.policy_unavailable); setupNext.setEnabled(false); return; }
        PolicyStore.Snapshot rules = settings.snapshot();
        boolean appRules = !rules.blockedApps.isEmpty() || (rules.schedule.enabled && rules.schedule.isConfigured())
                || (!rules.scannedApps.isEmpty() && rules.hasWordProtection());
        if (rules.adultWordsEnabled) {
            switch (AdultPolicyAssets.state) {
                case READY: adultState.setText(getString(R.string.adult_mode_ready, AdultPolicyAssets.policy().ruleCount())); break;
                case ERROR: adultState.setText(R.string.adult_mode_error); break;
                default: adultState.setText(R.string.adult_mode_loading);
            }
        } else adultState.setText(R.string.adult_mode_off);
        adultToggle.setText(rules.adultWordsEnabled ? R.string.adult_mode_disable : R.string.adult_mode_enable);
        adultToggle.setEnabled(!rules.adultWordsEnabled || settings.remainingMillis() == 0);
        SetupGuide.Assets assets = !rules.adultWordsEnabled ? SetupGuide.Assets.DISABLED
                : AdultPolicyAssets.state == AdultPolicyAssets.State.READY ? SetupGuide.Assets.READY
                : AdultPolicyAssets.state == AdultPolicyAssets.State.ERROR ? SetupGuide.Assets.ERROR : SetupGuide.Assets.LOADING;
        currentSetupStep = SetupGuide.next(appRules, guard, assets, SetupGuide.Network.valueOf(network.name()),
                DnsVpnService.connectionCheck.state == DnsVpnService.CheckState.STALE ? SetupGuide.Probe.IDLE
                        : SetupGuide.Probe.valueOf(DnsVpnService.connectionCheck.state.name()));
        setupNext.setEnabled(true);
        setupNext.setText(R.string.setup_next);
        switch (currentSetupStep) {
            case CHOOSE_RULES: setupState.setText(R.string.setup_choose_rules); break;
            case CONNECT_SERVICE: setupState.setText(R.string.setup_connect); setupNext.setText(R.string.open_accessibility); break;
            case LOAD_POLICY: setupState.setText(R.string.setup_policy_loading); setupNext.setText(R.string.setup_wait); setupNext.setEnabled(false); break;
            case REPAIR_POLICY: setupState.setText(R.string.adult_mode_error); setupNext.setText(R.string.setup_retry_policy); break;
            case REVIEW_APP_LAYER: setupState.setText(R.string.setup_review); setupNext.setText(R.string.setup_review_app); break;
            case ENABLE_DNS: setupState.setText(R.string.setup_dns); setupNext.setText(R.string.dns_start); break;
            case STARTING_DNS: setupState.setText(R.string.dns_starting); setupNext.setText(R.string.setup_wait); setupNext.setEnabled(false); break;
            case REVIEW_DNS: setupState.setText(R.string.dns_degraded); setupNext.setText(R.string.setup_review_network); break;
            case TEST_DNS: setupState.setText(R.string.setup_test); setupNext.setText(R.string.network_check_button); break;
            case CHECKING_DNS: setupState.setText(R.string.network_check_running); setupNext.setText(R.string.setup_wait); setupNext.setEnabled(false); break;
            case READY: setupState.setText(R.string.setup_ready); setupNext.setText(R.string.network_check_button); break;
        }
    }

    private void nextSetupStep() {
        if (settings == null) return;
        switch (currentSetupStep) {
            case CHOOSE_RULES:
                PolicyStore.Snapshot rules = settings.snapshot();
                if (rules.schedule.enabled && rules.schedule.isConfigured()) editSchedule();
                else if (!rules.hasWordProtection() && rules.blockedApps.isEmpty()) editAdultMode();
                else chooseApps(true);
                break;
            case CONNECT_SERVICE:
                new AlertDialog.Builder(this).setTitle(R.string.accessibility_title).setMessage(R.string.accessibility_disclosure)
                    .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.open_accessibility,
                            (dialog, which) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show();
                break;
            case REPAIR_POLICY: AdultPolicyAssets.retryLoading(this); refreshStatus(); break;
            case REVIEW_APP_LAYER:
                new AlertDialog.Builder(this).setTitle(R.string.setup_review_app).setMessage(R.string.accessibility_description)
                        .setPositiveButton(android.R.string.ok, null).show(); break;
            case ENABLE_DNS: startDns.performClick(); break;
            case REVIEW_DNS: startActivity(new Intent(Settings.ACTION_VPN_SETTINGS)); break;
            case TEST_DNS:
            case READY: checkNetwork.performClick(); break;
            default: break;
        }
    }

    private void editAdultMode() {
        if (settings == null) return;
        boolean enabled = settings.snapshot().adultWordsEnabled;
        new AlertDialog.Builder(this).setTitle(enabled ? R.string.adult_mode_disable : R.string.adult_mode_enable)
                .setMessage(R.string.adult_mode_disclosure).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save_rules, (dialog, which) -> {
                    try {
                        PolicyStore.Snapshot rules = settings.snapshot();
                        settings.update(settings.domainText(), settings.wordText(), rules.blockedApps, rules.scannedApps, !enabled);
                        if (!enabled) AdultPolicyAssets.ensureLoaded(this);
                        refreshStatus();
                    } catch (RuntimeException unavailable) { Toast.makeText(this, R.string.rules_locked, Toast.LENGTH_LONG).show(); }
                }).show();
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

    private void policyButton(int title, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setEnabled(settings != null);
        button.setOnClickListener(action);
        root.addView(button, layout());
    }

    private void editRules(boolean domain) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        input.setSaveEnabled(false);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(65536)});
        input.setMinLines(5);
        input.setMaxLines(10);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setText(domain ? settings.domainText() : settings.wordText());
        input.setHint(domain ? R.string.domain_hint : R.string.rule_hint);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(domain ? R.string.edit_domains : R.string.edit_words).setView(input)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.save_rules, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                PolicyStore.Snapshot old = settings.snapshot();
                settings.update(domain ? input.getText().toString() : settings.domainText(),
                        domain ? settings.wordText() : input.getText().toString(), old.blockedApps, old.scannedApps);
                dialog.dismiss();
                Toast.makeText(this, R.string.rules_saved, Toast.LENGTH_SHORT).show();
            } catch (IllegalArgumentException invalid) { input.setError(getString(R.string.rules_invalid)); }
            catch (IllegalStateException locked) { input.setError(getString(R.string.rules_locked)); }
        }));
        dialog.show();
    }

    private void chooseApps(boolean scan) {
        List<ChosenApps.Entry> apps = ChosenApps.list(this);
        PolicyStore.Snapshot old = settings.snapshot();
        Set<String> chosen = new LinkedHashSet<>(scan ? old.scannedApps : old.blockedApps);
        String[] labels = new String[apps.size()];
        boolean[] checks = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = apps.get(i).label;
            checks[i] = chosen.contains(apps.get(i).packageName);
        }
        new AlertDialog.Builder(this).setTitle(scan ? R.string.choose_scanned_apps : R.string.choose_blocked_apps)
                .setMultiChoiceItems(labels, checks, (dialog, index, checked) -> {
                    if (checked) chosen.add(apps.get(index).packageName);
                    else chosen.remove(apps.get(index).packageName);
                }).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save_rules, (dialog, which) -> {
                    try {
                        PolicyStore.Snapshot current = settings.snapshot();
                        settings.update(settings.domainText(), settings.wordText(),
                                scan ? current.blockedApps : chosen, scan ? chosen : current.scannedApps);
                        Toast.makeText(this, R.string.rules_saved, Toast.LENGTH_SHORT).show();
                    } catch (RuntimeException unavailable) {
                        Toast.makeText(this, R.string.rules_locked, Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void refreshSchedule(DailyAppSchedule schedule) {
        if (!schedule.enabled || !schedule.isConfigured()) {
            scheduleState.setText(R.string.schedule_off);
            return;
        }
        LocalTime now = LocalTime.now();
        int minute = now.getHour() * 60 + now.getMinute();
        boolean active = false;
        for (String app : schedule.apps) {
            if (schedule.blocks(app, minute)) { active = true; break; }
        }
        scheduleState.setText(getString(R.string.schedule_summary, schedule.apps.size(),
                scheduleTime(schedule.startMinute), scheduleTime(schedule.endMinute),
                getString(active ? R.string.schedule_window_active : R.string.schedule_window_inactive)));
    }

    private String scheduleTime(int minute) {
        Calendar time = Calendar.getInstance();
        time.set(Calendar.HOUR_OF_DAY, minute / 60);
        time.set(Calendar.MINUTE, minute % 60);
        return android.text.format.DateFormat.getTimeFormat(this).format(time.getTime());
    }

    private void editSchedule() {
        if (settings == null) return;
        final DailyAppSchedule existing;
        try { existing = settings.snapshot().schedule; }
        catch (RuntimeException unavailable) {
            Toast.makeText(this, R.string.policy_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        int[] minutes = {existing.startMinute, existing.endMinute};
        Set<String> selected = new LinkedHashSet<>(existing.apps);
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(8));
        scroll.addView(form);
        TextView explanation = new TextView(this);
        explanation.setText(R.string.schedule_help);
        form.addView(explanation, layout());
        CheckBox enabled = new CheckBox(this);
        enabled.setText(R.string.schedule_enable);
        enabled.setChecked(existing.enabled);
        form.addView(enabled, layout());
        Button start = new Button(this);
        start.setAllCaps(false);
        start.setText(getString(R.string.schedule_start, scheduleTime(minutes[0])));
        form.addView(start, layout());
        start.setOnClickListener(view -> new TimePickerDialog(this, (picker, hour, minute) -> {
            minutes[0] = hour * 60 + minute;
            start.setText(getString(R.string.schedule_start, scheduleTime(minutes[0])));
        }, minutes[0] / 60, minutes[0] % 60, android.text.format.DateFormat.is24HourFormat(this)).show());
        Button end = new Button(this);
        end.setAllCaps(false);
        end.setText(getString(R.string.schedule_end, scheduleTime(minutes[1])));
        form.addView(end, layout());
        end.setOnClickListener(view -> new TimePickerDialog(this, (picker, hour, minute) -> {
            minutes[1] = hour * 60 + minute;
            end.setText(getString(R.string.schedule_end, scheduleTime(minutes[1])));
        }, minutes[1] / 60, minutes[1] % 60, android.text.format.DateFormat.is24HourFormat(this)).show());
        Button apps = new Button(this);
        apps.setAllCaps(false);
        apps.setText(getString(R.string.schedule_apps, selected.size()));
        form.addView(apps, layout());
        apps.setOnClickListener(view -> chooseScheduleApps(selected, apps));
        TextView limits = new TextView(this);
        limits.setText(R.string.schedule_limits);
        form.addView(limits, layout());
        TextView error = new TextView(this);
        error.setTextColor(Color.rgb(180, 32, 32));
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        form.addView(error, layout());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.schedule_edit).setView(scroll)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.schedule_save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (minutes[0] == minutes[1]) {
                error.setText(R.string.schedule_invalid_time);
                return;
            }
            if (enabled.isChecked() && selected.isEmpty()) {
                error.setText(R.string.schedule_choose_apps_error);
                return;
            }
            try {
                settings.updateSchedule(new DailyAppSchedule(enabled.isChecked(), minutes[0], minutes[1], selected));
                dialog.dismiss();
                refreshStatus();
                Toast.makeText(this, R.string.rules_saved, Toast.LENGTH_SHORT).show();
            } catch (IllegalArgumentException invalid) {
                error.setText(R.string.schedule_invalid);
            } catch (IllegalStateException locked) {
                error.setText(R.string.rules_locked);
            } catch (RuntimeException unavailable) {
                error.setText(R.string.policy_unavailable);
            }
        }));
        dialog.show();
    }

    private void chooseScheduleApps(Set<String> selected, Button count) {
        List<ChosenApps.Entry> apps = ChosenApps.list(this);
        Set<String> draft = new LinkedHashSet<>(selected);
        String[] labels = new String[apps.size()];
        boolean[] checks = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = apps.get(i).label;
            checks[i] = draft.contains(apps.get(i).packageName);
        }
        new AlertDialog.Builder(this).setTitle(R.string.schedule_choose_apps)
                .setMultiChoiceItems(labels, checks, (dialog, index, checked) -> {
                    if (checked) draft.add(apps.get(index).packageName);
                    else draft.remove(apps.get(index).packageName);
                }).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.schedule_use_apps, (dialog, which) -> {
                    selected.clear();
                    selected.addAll(draft);
                    count.setText(getString(R.string.schedule_apps, selected.size()));
                }).show();
    }

    private void chooseCommitment() {
        if (settings == null || !settings.snapshot().hasConfiguredRestrictions()) {
            Toast.makeText(this, R.string.commitment_needs_rules, Toast.LENGTH_LONG).show();
            return;
        }
        int[] days = {7, 30, 90};
        new AlertDialog.Builder(this).setTitle(R.string.commitment_setup)
                .setItems(new String[]{getString(R.string.days_count, 7), getString(R.string.days_count, 30),
                        getString(R.string.days_count, 90)}, (dialog, index) -> new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.days_count, days[index])).setMessage(R.string.commitment_disclosure)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.commitment_start, (confirm, which) -> {
                            try { settings.startCommitment(days[index]); refreshStatus(); }
                            catch (RuntimeException unavailable) { Toast.makeText(this, R.string.rules_locked, Toast.LENGTH_LONG).show(); }
                        }).show()).show();
    }

    private EditText input(int hint, boolean multiline) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextColor(Color.WHITE);
        edit.setHintTextColor(Color.rgb(170, 182, 198));
        edit.setInputType(InputType.TYPE_CLASS_TEXT | (multiline
                ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        edit.setSingleLine(!multiline);
        edit.setSaveEnabled(false);
        edit.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        if (multiline) {
            edit.setMinLines(3);
            edit.setGravity(Gravity.TOP | Gravity.START);
        }
        root.addView(edit, layout());
        return edit;
    }

    private TextView heading(int text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.WHITE);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams params = layout();
        params.topMargin = dp(20);
        root.addView(view, params);
        return view;
    }

    private TextView paragraph(int text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(16);
        view.setTextColor(Color.rgb(201, 210, 222));
        LinearLayout.LayoutParams params = layout();
        params.topMargin = dp(10);
        root.addView(view, params);
        return view;
    }

    private LinearLayout.LayoutParams layout() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
