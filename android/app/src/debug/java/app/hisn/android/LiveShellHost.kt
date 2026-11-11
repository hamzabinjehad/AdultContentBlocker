package app.hisn.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.LinkedHashMap
import app.hisn.android.nativeshell.NativeShellHost
import app.hisn.android.nativeshell.ShellActionResult
import app.hisn.android.nativeshell.ShellApp
import app.hisn.android.nativeshell.ShellAppPicker
import app.hisn.android.nativeshell.ShellConsent
import app.hisn.android.nativeshell.ShellIssue
import app.hisn.android.nativeshell.ShellLabels
import app.hisn.android.nativeshell.ShellLayer
import app.hisn.android.nativeshell.ShellLimitChoice
import app.hisn.android.nativeshell.ShellLimitsEditor
import app.hisn.android.nativeshell.ShellLockOption
import app.hisn.android.nativeshell.ShellLockState
import app.hisn.android.nativeshell.ShellProtection
import app.hisn.android.nativeshell.ShellRule
import app.hisn.android.nativeshell.ShellRules
import app.hisn.android.nativeshell.ShellRulesEditor
import app.hisn.android.nativeshell.ShellRulesPreview
import app.hisn.android.nativeshell.ShellScheduleDay
import app.hisn.android.nativeshell.ShellAction
import app.hisn.android.nativeshell.ShellScheduleEditor
import app.hisn.android.nativeshell.ShellSetting
import app.hisn.android.nativeshell.ShellSettings
import app.hisn.android.nativeshell.ShellToggle
import app.hisn.android.nativeshell.ShellUninstall
import app.hisn.core.AppTimeLimit
import app.hisn.core.DailyAppSchedule
import app.hisn.core.PolicyChangeError
import app.hisn.core.ProtectionLock
import app.hisn.core.RulePreview
import app.hisn.core.UninstallProtection
import java.util.Locale

/**
 * The debug-only bridge between the Compose shell and the real Hisn state.
 *
 * It lives in `app.hisn.android` so it can read package-private production helpers
 * ([Permissions], `DnsVpnService.resumeRequested`) without widening their API. Phase 3 writes
 * only through [PolicyStore], only after the shell's consent sheet, and only when the protection
 * lock allows it. The classic editors still own every surface not ported here.
 */
class LiveShellHost(private val context: Context) : NativeShellHost {

    private fun string(id: Int): String = context.getString(id)
    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)
    private fun plurals(id: Int, count: Int): String =
        context.resources.getQuantityString(id, count, count)

    private fun labels() = ShellLabels(
        appName = string(R.string.app_name),
        tagline = string(R.string.app_tagline),
        protection = string(R.string.nav_protection),
        rules = string(R.string.nav_rules),
        settings = string(R.string.nav_guide),
        runCheck = string(R.string.native_run_check),
        openClassic = string(R.string.native_open_classic),
        live = string(R.string.native_live_title),
        liveBody = string(R.string.native_live_body),
        attention = string(R.string.native_attention),
        layers = string(R.string.native_layers),
        locked = string(R.string.rules_locked),
        unsupported = string(R.string.native_unsupported),
        dnsStart = string(R.string.dns_start),
        settingsControls = string(R.string.native_settings_controls),
        settingsAndroid = string(R.string.native_settings_android)
    )

    private fun store(): PolicyStore? = runCatching { PolicyStore.get(context) }.getOrNull()

    override fun protection(): ShellProtection {
        val labels = labels()
        val settings = store()
        val snap = settings?.snapshot()
        if (snap == null) {
            return ShellProtection(
                status = "attention",
                title = string(R.string.protection_attention),
                body = string(R.string.native_state_unavailable),
                layers = emptyList(),
                issues = listOf(ShellIssue(string(R.string.native_state_unavailable))),
                domains = 0,
                words = 0,
                apps = 0,
                dnsState = string(R.string.native_permission_off),
                guardState = string(R.string.native_permission_off),
                checkedAt = null,
                canCheck = false,
                labels = labels
            )
        }

        val dnsState = DnsVpnService.state
        val checkState = DnsVpnService.connectionCheck.state
        val dnsOn = dnsState == DnsVpnService.State.RUNNING ||
            dnsState == DnsVpnService.State.DEGRADED
        val accessibility = runCatching { Permissions.accessibility(context) }.getOrDefault(false)
        val blockedApps = snap.blockedApps.size
        val loadedDomains = if (dnsOn) {
            DnsVpnService.domains.takeIf { it > 0 } ?: BuildConfig.DOMAIN_SEED_COUNT
        } else {
            0
        }

        val layers = listOf(
            ShellLayer(
                key = "network",
                label = string(R.string.section_network),
                detail = if (dnsOn) {
                    string(R.string.native_layer_dns_on, loadedDomains)
                } else {
                    string(R.string.native_layer_dns_off)
                },
                state = if (dnsOn) string(R.string.state_on) else string(R.string.state_off),
                on = dnsOn
            ),
            ShellLayer(
                key = "apps",
                label = string(R.string.section_accessibility),
                detail = when {
                    blockedApps == 0 -> string(R.string.native_no_apps)
                    else -> string(R.string.native_layer_apps_on, blockedApps)
                },
                state = if (accessibility) string(R.string.state_on) else string(R.string.state_off),
                on = accessibility
            ),
            ShellLayer(
                key = "words",
                label = string(R.string.toggle_adult),
                detail = if (snap.adultWordsEnabled) {
                    string(R.string.native_layer_words_on, snap.words.configuredRules().size)
                } else {
                    string(R.string.native_layer_words_off)
                },
                state = if (snap.adultWordsEnabled) string(R.string.state_on) else string(R.string.state_off),
                on = snap.adultWordsEnabled
            ),
            ShellLayer(
                key = "images",
                label = string(R.string.toggle_visual),
                detail = if (snap.visualEnabled) {
                    string(R.string.native_layer_images_on)
                } else {
                    string(R.string.native_layer_images_off)
                },
                state = if (snap.visualEnabled) string(R.string.state_on) else string(R.string.state_off),
                on = snap.visualEnabled
            )
        )

        val issues = mutableListOf<ShellIssue>()
        when (dnsState) {
            DnsVpnService.State.ERROR -> issues += ShellIssue(string(R.string.attention_dns_error))
            DnsVpnService.State.DEGRADED -> issues += ShellIssue(string(R.string.attention_dns_degraded))
            DnsVpnService.State.OFF -> if (DnsVpnService.resumeRequested(context)) {
                issues += ShellIssue(string(R.string.attention_dns_not_running))
            }
            else -> Unit
        }
        if (DnsVpnService.privateDnsStrict) {
            issues += ShellIssue(string(R.string.attention_private_dns))
        }
        if (checkState == DnsVpnService.CheckState.FAILED) {
            issues += ShellIssue(string(R.string.attention_route_failed))
        }
        val hasAppRules = blockedApps > 0 || snap.scannedApps.isNotEmpty() ||
            snap.shortVideoEnabled || snap.schedule.isConfigured() || snap.timeLimits.configured()
        if (!accessibility && hasAppRules) {
            issues += ShellIssue(string(R.string.attention_guard_permission))
        }
        if (snap.visualEnabled && snap.scannedApps.isEmpty()) {
            issues += ShellIssue(string(R.string.attention_visual_no_apps))
        }
        if (snap.visualEnabled && Build.VERSION.SDK_INT < 34) {
            issues += ShellIssue(string(R.string.attention_visual_unsupported))
        }
        if (settings.commitmentUnknown()) {
            issues += ShellIssue(string(R.string.attention_commitment_unknown))
        }
        if (!Permissions.notifications(context) && (dnsOn || accessibility)) {
            issues += ShellIssue(string(R.string.attention_notifications_off))
        }

        val allOn = layers.all { it.on }
        val anyOn = layers.any { it.on }
        val status = when {
            issues.isNotEmpty() -> "attention"
            !anyOn -> "off"
            allOn -> "protected"
            else -> "partial"
        }
        val title = when (status) {
            "attention" -> string(R.string.protection_attention)
            "off" -> string(R.string.status_title)
            "protected" -> string(R.string.native_protected_title)
            else -> string(R.string.protection_partial)
        }
        val body = when (status) {
            "attention" -> string(R.string.status_body_attention)
            "off" -> string(R.string.status_body_off)
            "protected" -> string(R.string.native_protected_body)
            else -> string(R.string.status_body_partial)
        }

        return ShellProtection(
            status = status,
            title = title,
            body = body,
            layers = layers,
            issues = issues,
            domains = snap.domains.blockedRules().size,
            words = snap.words.configuredRules().size,
            apps = blockedApps,
            dnsState = if (dnsOn) string(R.string.state_on) else string(R.string.state_off),
            guardState = if (accessibility) string(R.string.state_on) else string(R.string.state_off),
            checkedAt = null,
            canCheck = dnsOn,
            labels = labels
        )
    }

    override fun rules(): ShellRules {
        val labels = labels()
        val settings = store()
        val snap = settings?.snapshot()
        val locked = (settings?.remainingMillis() ?: 0L) > 0
        if (snap == null) {
            return ShellRules(
                builtInTitle = string(R.string.section_builtin),
                customTitle = string(R.string.section_rules_custom),
                note = string(R.string.native_rules_note),
                builtIn = emptyList(),
                custom = emptyList(),
                locked = false,
                labels = labels
            )
        }
        val builtIn = listOf(
            ShellRule(
                key = "adult",
                label = string(R.string.toggle_adult),
                detail = if (snap.adultWordsEnabled) {
                    string(R.string.native_layer_words_on, snap.words.configuredRules().size)
                } else {
                    string(R.string.native_layer_words_off)
                },
                state = if (snap.adultWordsEnabled) string(R.string.state_on) else string(R.string.state_off),
                on = snap.adultWordsEnabled
            ),
            ShellRule(
                key = "images",
                label = string(R.string.toggle_visual),
                detail = if (snap.visualEnabled) {
                    string(R.string.native_layer_images_on)
                } else {
                    string(R.string.native_layer_images_off)
                },
                state = if (snap.visualEnabled) string(R.string.state_on) else string(R.string.state_off),
                on = snap.visualEnabled
            ),
            ShellRule(
                key = "shortVideo",
                label = string(R.string.toggle_short_video),
                detail = if (snap.shortVideoEnabled) {
                    string(R.string.state_on)
                } else {
                    string(R.string.state_off)
                },
                state = if (snap.shortVideoEnabled) string(R.string.state_on) else string(R.string.state_off),
                on = snap.shortVideoEnabled
            )
        )
        val custom = listOf(
            ShellRule(
                key = "domains",
                label = string(R.string.edit_domains),
                detail = plurals(R.plurals.saved_domains, snap.domains.blockedRules().size),
                state = "",
                on = false
            ),
            ShellRule(
                key = "words",
                label = string(R.string.edit_words),
                detail = plurals(R.plurals.saved_words, snap.words.configuredRules().size),
                state = "",
                on = false
            ),
            ShellRule(
                key = "blockedApps",
                label = string(R.string.choose_blocked_apps),
                detail = plurals(R.plurals.blocked_apps_count, snap.blockedApps.size),
                state = "",
                on = false
            ),
            ShellRule(
                key = "scannedApps",
                label = string(R.string.choose_scanned_apps),
                detail = plurals(R.plurals.inspected_apps_count, snap.scannedApps.size),
                state = "",
                on = false
            ),
            ShellRule(
                key = "schedule",
                label = string(R.string.section_schedule),
                detail = if (snap.schedule.isConfigured()) {
                    string(R.string.state_on)
                } else {
                    string(R.string.state_off)
                },
                state = "",
                on = false
            ),
            ShellRule(
                key = "limit",
                label = string(R.string.section_time_limits),
                detail = when {
                    !snap.timeLimits.configured() -> string(R.string.state_off)
                    snap.timeLimits.size() == 1 -> {
                        val app = snap.timeLimits.apps().first()
                        val minutes = snap.timeLimits.minutesFor(app) ?: 0
                        string(
                            R.string.time_limits_summary_limit,
                            context.resources.getQuantityString(
                                R.plurals.time_limits_summary,
                                1,
                                1
                            ),
                            timeLimitText(minutes)
                        )
                    }
                    else -> context.resources.getQuantityString(
                        R.plurals.time_limits_summary,
                        snap.timeLimits.size(),
                        snap.timeLimits.size()
                    )
                },
                state = "",
                on = false
            ),
            ShellRule(
                key = "lock",
                label = string(R.string.section_commitment),
                detail = if (locked) {
                    string(
                        R.string.commitment_remaining,
                        Durations.remaining(context, settings?.remainingMillis() ?: 0L)
                    )
                } else {
                    string(R.string.state_off)
                },
                state = "",
                on = false
            )
        )
        return ShellRules(
            builtInTitle = string(R.string.section_builtin),
            customTitle = string(R.string.section_rules_custom),
            note = string(R.string.native_rules_note),
            builtIn = builtIn,
            custom = custom,
            locked = locked,
            labels = labels
        )
    }

    override fun layerConsent(key: String): ShellConsent? {
        val snap = store()?.snapshot() ?: return null
        return when (key) {
            "adult" -> ShellConsent(
                title = string(
                    if (snap.adultWordsEnabled) R.string.adult_mode_disable
                    else R.string.adult_mode_enable
                ),
                body = string(R.string.adult_mode_short) + "\n\n" +
                    string(R.string.adult_mode_disclosure),
                acceptLabel = string(R.string.native_consent_continue),
                cancelLabel = string(android.R.string.cancel)
            )
            "images" -> {
                if (Build.VERSION.SDK_INT < 34) return null
                ShellConsent(
                    title = string(
                        if (snap.visualEnabled) R.string.visual_disable
                        else R.string.visual_enable
                    ),
                    body = string(R.string.visual_short) + "\n\n" +
                        string(R.string.visual_disclosure),
                    acceptLabel = string(R.string.native_consent_continue),
                    cancelLabel = string(android.R.string.cancel)
                )
            }
            "shortVideo" -> ShellConsent(
                title = string(
                    if (snap.shortVideoEnabled) R.string.short_video_disable
                    else R.string.short_video_enable
                ),
                body = string(R.string.short_video_short) + "\n\n" +
                    string(R.string.short_video_disclosure),
                acceptLabel = string(R.string.native_consent_continue),
                cancelLabel = string(android.R.string.cancel)
            )
            else -> null
        }
    }

    override fun setLayer(key: String, enabled: Boolean): ShellActionResult {
        val settings = store()
            ?: return ShellActionResult(string(R.string.policy_unavailable), ok = false, refresh = false)
        if (settings.remainingMillis() > 0) {
            return ShellActionResult(string(R.string.rules_locked), ok = false, refresh = false)
        }
        if (key == "images" && Build.VERSION.SDK_INT < 34) {
            return ShellActionResult(string(R.string.native_unsupported), ok = false, refresh = false)
        }
        val snap = settings.snapshot()
        return try {
            when (key) {
                "adult" -> {
                    settings.update(
                        settings.domainText(),
                        settings.wordText(),
                        snap.blockedApps,
                        snap.scannedApps,
                        enabled
                    )
                    if (enabled && snap.scannedApps.isEmpty()) {
                        ShellActionResult(string(R.string.native_choose_apps))
                    } else {
                        ShellActionResult(string(if (enabled) R.string.state_on else R.string.state_off))
                    }
                }
                "images" -> {
                    settings.updateVisual(enabled)
                    if (enabled && snap.scannedApps.isEmpty()) {
                        ShellActionResult(string(R.string.native_choose_apps))
                    } else {
                        ShellActionResult(string(if (enabled) R.string.state_on else R.string.state_off))
                    }
                }
                "shortVideo" -> {
                    settings.updateShortVideo(enabled)
                    ShellActionResult(string(if (enabled) R.string.state_on else R.string.state_off))
                }
                else -> ShellActionResult(string(R.string.native_unsupported), ok = false, refresh = false)
            }
        } catch (refused: RuntimeException) {
            ShellActionResult(refusalText(refused), ok = false, refresh = true)
        }
    }

    override fun rulesEditor(kind: String): ShellRulesEditor {
        val settings = store()
        val domain = kind == "domains"
        val locked = (settings?.remainingMillis() ?: 0L) > 0
        return ShellRulesEditor(
            kind = kind,
            title = string(if (domain) R.string.edit_domains else R.string.edit_words),
            text = when {
                settings == null -> ""
                domain -> settings.domainText()
                else -> settings.wordText()
            },
            hint = string(if (domain) R.string.domain_hint else R.string.rule_hint),
            note = string(R.string.rules_editor_private),
            saveLabel = string(R.string.save_rules),
            cancelLabel = string(android.R.string.cancel),
            lockedNote = string(R.string.rules_locked),
            locked = locked
        )
    }

    override fun previewRules(kind: String, text: String): ShellRulesPreview {
        val previewKind = if (kind == "domains") RulePreview.Kind.DOMAIN else RulePreview.Kind.WORD
        return try {
            val report = RulePreview.of(text, previewKind, 1000)
            val rejected = report.entries
                .filter { it.status == RulePreview.Status.INVALID }
                .take(3)
                .joinToString("\n") { string(R.string.rule_preview_rejected, it.raw, it.reason) }
            ShellRulesPreview(
                summary = string(
                    R.string.rule_preview_counts,
                    report.accepted.size,
                    report.duplicates,
                    report.rejected
                ),
                error = rejected.ifEmpty { null }
            )
        } catch (invalid: IllegalArgumentException) {
            ShellRulesPreview(summary = "", error = string(R.string.rules_invalid))
        }
    }

    override fun saveRules(kind: String, text: String): ShellActionResult {
        val settings = store()
            ?: return ShellActionResult(string(R.string.policy_unavailable), ok = false, refresh = false)
        if (settings.remainingMillis() > 0) {
            return ShellActionResult(string(R.string.rules_locked), ok = false, refresh = false)
        }
        val previewKind = if (kind == "domains") RulePreview.Kind.DOMAIN else RulePreview.Kind.WORD
        val report = try {
            RulePreview.of(text, previewKind, 1000)
        } catch (invalid: IllegalArgumentException) {
            return ShellActionResult(string(R.string.rules_invalid), ok = false, refresh = false)
        }
        if (report.accepted.isEmpty() && text.isNotBlank() && report.rejected > 0) {
            return ShellActionResult(string(R.string.rules_invalid), ok = false, refresh = false)
        }
        val snap = settings.snapshot()
        return try {
            if (kind == "domains") {
                settings.update(report.acceptedText(), settings.wordText(), snap.blockedApps, snap.scannedApps)
            } else {
                settings.update(settings.domainText(), report.acceptedText(), snap.blockedApps, snap.scannedApps)
            }
            ShellActionResult(
                message = string(
                    R.string.rule_preview_saved,
                    report.accepted.size,
                    report.duplicates + report.rejected
                ),
                ok = true,
                refresh = true
            )
        } catch (refused: RuntimeException) {
            ShellActionResult(refusalText(refused), ok = false, refresh = true)
        }
    }

    private fun refusalText(refused: RuntimeException): String =
        when (PolicyChangeError.of(refused)) {
            PolicyChangeError.TOO_MANY_APPS -> context.resources.getQuantityString(
                R.plurals.rules_too_many_apps,
                PolicyStore.MAX_APPS,
                PolicyStore.MAX_APPS
            )
            PolicyChangeError.INVALID -> string(R.string.rules_invalid)
            PolicyChangeError.COMMITMENT -> string(R.string.rules_locked)
            PolicyChangeError.NEEDS_RULES -> string(R.string.commitment_needs_rules)
            PolicyChangeError.SAVE_FAILED -> string(R.string.rules_save_failed)
            else -> string(R.string.policy_unavailable)
        }

    override fun settings(): ShellSettings {
        val labels = labels()
        val accessibility = runCatching { Permissions.accessibility(context) }.getOrDefault(false)
        val notifications = runCatching { Permissions.notifications(context) }.getOrDefault(false)
        val usage = runCatching { Permissions.usage(context) }.getOrDefault(false)
        val dns = DnsVpnService.state == DnsVpnService.State.RUNNING ||
            DnsVpnService.state == DnsVpnService.State.DEGRADED
        val onOff = { enabled: Boolean ->
            if (enabled) string(R.string.native_permission_on) else string(R.string.native_permission_off)
        }
        val settings = store()
        val locked = (settings?.remainingMillis() ?: 0L) > 0
        return ShellSettings(
            toggles = listOf(
                ShellToggle(
                    key = "safeSearch",
                    label = string(R.string.dns_safe_search_switch),
                    detail = string(R.string.dns_safe_search_hint),
                    on = settings?.safeSearchEnabled() == true,
                    enabled = !locked
                )
            ),
            actions = listOf(
                ShellAction(
                    key = "vpnSettings",
                    label = string(R.string.dns_settings),
                    detail = ""
                ),
                ShellAction(
                    key = "usageAccess",
                    label = string(R.string.native_permission_usage),
                    detail = string(
                        if (usage) R.string.time_limits_access_granted
                        else R.string.time_limits_access_needed
                    )
                ),
                ShellAction(
                    key = "accessibility",
                    label = string(R.string.native_permission_accessibility),
                    detail = onOff(accessibility)
                ),
                ShellAction(
                    key = "notifications",
                    label = string(R.string.native_permission_notifications),
                    detail = onOff(notifications)
                ),
                ShellAction(
                    key = "uninstall",
                    label = string(R.string.section_uninstall),
                    detail = uninstallSummary()
                )
            ),
            sections = listOf(
                ShellSetting(
                    label = string(R.string.native_permission_section),
                    value = "",
                    detail = listOf(
                        "${string(R.string.native_permission_accessibility)} · ${onOff(accessibility)}",
                        "${string(R.string.native_permission_notifications)} · ${onOff(notifications)}",
                        "${string(R.string.native_permission_vpn)} · ${onOff(dns)}",
                        "${string(R.string.native_permission_usage)} · ${onOff(usage)}"
                    ).joinToString("\n")
                ),
                ShellSetting(
                    label = string(R.string.native_settings_language),
                    value = Locale.getDefault().getDisplayLanguage(Locale.getDefault()),
                    detail = ""
                ),
                ShellSetting(
                    label = string(R.string.native_about_section),
                    value = string(R.string.native_settings_version, BuildConfig.VERSION_NAME),
                    detail = string(R.string.native_rules_note)
                )
            ),
            labels = labels
        )
    }

    private fun uninstallSummary(): String {
        val status = runCatching { UninstallProtectionController.read(context) }.getOrNull()
            ?: return string(R.string.state_attention)
        return when {
            status.uninstallBlocked -> string(R.string.state_on)
            status.deviceOwner || BuildConfig.SELF_PROTECTION -> string(R.string.state_attention)
            else -> string(R.string.state_off)
        }
    }

    private fun uninstallStatusText(status: UninstallProtectionController.Status): String {
        val settings = store()
        val screenGuard = BuildConfig.SELF_PROTECTION && !status.deviceOwner
        if (screenGuard) {
            val committed = ProtectionLock.locked(status.remainingMillis)
            val connected = TextGuardService.connected
            if (settings == null) return string(R.string.policy_unavailable)
            if (committed && !connected) {
                return string(R.string.self_protection_state_lock_inactive)
            }
            if (committed) {
                return string(
                    if (settings.installLock()) R.string.self_protection_state_lock_on_installs
                    else R.string.self_protection_state_lock_on,
                    Durations.remaining(context, status.remainingMillis)
                )
            }
            if (!status.keepInstalled) {
                return string(
                    if (settings.installLock()) R.string.self_protection_state_installs_only
                    else R.string.self_protection_state_off
                )
            }
            if (connected) {
                return string(
                    if (settings.installLock()) R.string.self_protection_state_on_installs
                    else R.string.self_protection_state_on
                )
            }
            return string(R.string.self_protection_state_inactive)
        }
        return when (status.state()) {
            UninstallProtection.State.ADMIN_ONLY -> string(R.string.uninstall_state_admin)
            UninstallProtection.State.READY -> string(R.string.uninstall_state_ready)
            UninstallProtection.State.PROTECTED -> if (status.remainingMillis > 0) {
                string(
                    R.string.uninstall_state_protected_commitment,
                    Durations.remaining(context, status.remainingMillis)
                )
            } else {
                string(R.string.uninstall_state_protected)
            }
            UninstallProtection.State.OUT_OF_SYNC -> string(R.string.uninstall_state_sync)
            else -> string(
                if (status.error != null) R.string.uninstall_state_error
                else R.string.uninstall_state_unavailable
            )
        }
    }

    override fun uninstallState(): ShellUninstall {
        val status = runCatching { UninstallProtectionController.read(context) }.getOrNull()
        val managed = status != null &&
            (status.deviceOwner || (BuildConfig.SELF_PROTECTION && !status.deviceOwner))
        val body = when {
            status == null -> string(R.string.policy_unavailable)
            status.deviceOwner -> string(R.string.uninstall_dialog_body_owner)
            BuildConfig.SELF_PROTECTION -> string(R.string.self_protection_dialog_body)
            else -> string(R.string.uninstall_dialog_body_unmanaged)
        }
        val command = if (!managed) {
            runCatching { UninstallProtectionController.provisioningCommand(context) }
                .getOrDefault("")
        } else {
            ""
        }
        return ShellUninstall(
            title = string(R.string.uninstall_title),
            body = body,
            status = status?.let { uninstallStatusText(it) }
                ?: string(R.string.uninstall_state_error),
            commandLabel = string(R.string.uninstall_command_label),
            command = command,
            copyLabel = string(R.string.uninstall_copy_command),
            copyMessage = string(R.string.uninstall_copied),
            adminLabel = string(R.string.admin_setup),
            managed = managed,
            managedNote = string(R.string.native_uninstall_managed_classic)
        )
    }

    override fun copyUninstallCommand(): Boolean = try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        val command = UninstallProtectionController.provisioningCommand(context)
        clipboard.setPrimaryClip(ClipData.newPlainText("Hisn device-owner setup", command))
        true
    } catch (unavailable: RuntimeException) {
        false
    }

    override fun openAdminSetup(): Boolean = try {
        context.startActivity(
            UninstallProtectionController.adminIntent(context)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (unavailable: RuntimeException) {
        false
    }

    override fun setToggle(key: String, enabled: Boolean): ShellActionResult {
        if (key != "safeSearch") {
            return ShellActionResult(string(R.string.native_unsupported), ok = false, refresh = false)
        }
        val settings = store()
            ?: return ShellActionResult(string(R.string.policy_unavailable), ok = false, refresh = false)
        return try {
            settings.updateSafeSearch(enabled)
            ShellActionResult(string(R.string.rules_saved), ok = true, refresh = true)
        } catch (refused: RuntimeException) {
            ShellActionResult(refusalText(refused), ok = false, refresh = true)
        }
    }

    override fun runSettingAction(key: String): Boolean = try {
        val intent = when (key) {
            "vpnSettings" -> null
            "usageAccess" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "notifications" -> {
                if (Build.VERSION.SDK_INT >= 26) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else {
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + context.packageName)
                    )
                }
            }
            else -> return false
        }
        if (intent == null) {
            Compat.openVpnSettings(context)
        } else {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        true
    } catch (unavailable: RuntimeException) {
        false
    }

    override fun runCheck(): ShellActionResult {
        val state = DnsVpnService.state
        if (state != DnsVpnService.State.RUNNING && state != DnsVpnService.State.DEGRADED) {
            return ShellActionResult(string(R.string.native_check_unavailable), refresh = false)
        }
        val started = runCatching { DnsVpnService.requestConnectionCheck() }.getOrDefault(false)
        return ShellActionResult(
            message = if (started) {
                string(R.string.native_check_started)
            } else {
                string(R.string.native_check_unavailable)
            },
            refresh = started
        )
    }

    private fun timeLimitText(minutes: Int): String = if (minutes >= 60 && minutes % 60 == 0) {
        string(R.string.time_limits_hours, minutes / 60)
    } else {
        string(R.string.time_limits_minutes, minutes)
    }

    override fun scheduleEditor(): ShellScheduleEditor {
        val schedule = store()?.snapshot()?.schedule ?: DailyAppSchedule.disabled()
        val density = context.resources.displayMetrics.density
        val iconSize = (36 * density).toInt().coerceAtLeast(1)
        val apps = runCatching { ChosenApps.list(context) }.getOrDefault(emptyList())
            .map { entry ->
                ShellApp(
                    packageName = entry.packageName,
                    label = entry.label,
                    icon = appIcon(entry.packageName, iconSize)
                )
            }
        val weekdays = java.text.DateFormatSymbols().shortWeekdays
        val dayBits = listOf(
            1 to java.util.Calendar.MONDAY,
            2 to java.util.Calendar.TUESDAY,
            4 to java.util.Calendar.WEDNESDAY,
            8 to java.util.Calendar.THURSDAY,
            16 to java.util.Calendar.FRIDAY,
            32 to java.util.Calendar.SATURDAY,
            64 to java.util.Calendar.SUNDAY
        )
        return ShellScheduleEditor(
            enabled = schedule.enabled,
            startMinute = schedule.startMinute,
            endMinute = schedule.endMinute,
            days = schedule.days,
            allApps = schedule.allApps,
            selected = schedule.apps,
            apps = apps,
            title = string(R.string.schedule_edit),
            help = string(R.string.schedule_help),
            enableLabel = string(R.string.schedule_enable),
            startTemplate = string(R.string.schedule_start, "__TIME__"),
            endTemplate = string(R.string.schedule_end, "__TIME__"),
            daysTitle = string(R.string.schedule_days_title),
            daysHelp = string(R.string.schedule_days_help),
            modeAllLabel = string(R.string.schedule_mode_all),
            modeListLabel = string(R.string.schedule_mode_list),
            modeListHelp = string(R.string.schedule_list_help),
            modeAllHelp = string(R.string.schedule_all_apps_help),
            chooseAppsLabel = string(R.string.schedule_choose_apps),
            keepAppsLabel = string(R.string.schedule_keep_apps),
            useAppsLabel = string(R.string.schedule_use_apps),
            searchHint = string(R.string.app_search_hint),
            emptyLabel = string(R.string.app_search_empty),
            privateNote = string(R.string.app_picker_private),
            max = PolicyStore.MAX_APPS,
            saveLabel = string(R.string.schedule_save),
            cancelLabel = string(android.R.string.cancel),
            lockedNote = string(R.string.rules_locked),
            locked = (store()?.remainingMillis() ?: 0L) > 0,
            invalidTime = string(R.string.schedule_invalid_time),
            chooseAppsError = string(R.string.schedule_choose_apps_error),
            keepAppsError = string(R.string.schedule_keep_apps_error),
            daysError = string(R.string.schedule_days_error),
            invalid = string(R.string.schedule_invalid),
            dayLabels = dayBits.map { (bit, day) ->
                ShellScheduleDay(bit, weekdays.getOrElse(day) { "" })
            }
        )
    }

    override fun saveSchedule(
        enabled: Boolean,
        startMinute: Int,
        endMinute: Int,
        days: Int,
        allApps: Boolean,
        apps: Set<String>
    ): ShellActionResult {
        val settings = store()
            ?: return ShellActionResult(string(R.string.policy_unavailable), ok = false, refresh = false)
        if (settings.remainingMillis() > 0) {
            return ShellActionResult(string(R.string.rules_locked), ok = false, refresh = false)
        }
        if (startMinute == endMinute) {
            return ShellActionResult(string(R.string.schedule_invalid_time), ok = false, refresh = false)
        }
        if (days < 1 || days > DailyAppSchedule.ALL_DAYS) {
            return ShellActionResult(string(R.string.schedule_days_error), ok = false, refresh = false)
        }
        val snap = settings.snapshot()
        val installed = runCatching { ChosenApps.list(context) }
            .getOrDefault(emptyList())
            .map { it.packageName }
            .toSet()
        val previous = snap.schedule.apps
        val next = LinkedHashSet(apps)
        for (app in previous) {
            if (app !in installed && app !in next) next.add(app)
        }
        if (enabled && next.isEmpty()) {
            return ShellActionResult(
                string(if (allApps) R.string.schedule_keep_apps_error else R.string.schedule_choose_apps_error),
                ok = false,
                refresh = false
            )
        }
        return try {
            settings.updateSchedule(
                DailyAppSchedule(enabled, startMinute, endMinute, next, allApps, days)
            )
            ShellActionResult(string(R.string.rules_saved), ok = true, refresh = true)
        } catch (refused: RuntimeException) {
            ShellActionResult(string(R.string.schedule_invalid), ok = false, refresh = true)
        }
    }

    override fun limitsEditor(): ShellLimitsEditor {
        val snap = store()?.snapshot()
        val selected = LinkedHashMap<String, Int>()
        snap?.timeLimits?.let { limits ->
            for (app in limits.apps()) {
                selected[app] = limits.minutesFor(app) ?: 15
            }
        }
        val density = context.resources.displayMetrics.density
        val iconSize = (36 * density).toInt().coerceAtLeast(1)
        val apps = runCatching { ChosenApps.list(context) }.getOrDefault(emptyList())
            .map { entry ->
                ShellApp(
                    packageName = entry.packageName,
                    label = entry.label,
                    icon = appIcon(entry.packageName, iconSize)
                )
            }
        val access = Permissions.usage(context)
        return ShellLimitsEditor(
            title = string(R.string.time_limits_choose_apps),
            help = string(R.string.time_limits_help),
            searchHint = string(R.string.app_search_hint),
            accessTitle = string(R.string.time_limits_access_title),
            accessDetail = string(
                if (access) R.string.time_limits_access_granted
                else R.string.time_limits_access_needed
            ),
            accessUnavailable = string(R.string.time_limits_access_unavailable),
            saveLabel = string(R.string.time_limits_save),
            cancelLabel = string(android.R.string.cancel),
            lockedNote = string(R.string.rules_locked),
            locked = (store()?.remainingMillis() ?: 0L) > 0,
            max = AppTimeLimit.MAX_APPS,
            defaultMinutes = 15,
            choices = listOf(5, 15, 30, 60, 120, 240, 480, 1440).map { minutes ->
                ShellLimitChoice(minutes, timeLimitText(minutes))
            },
            selected = selected,
            apps = apps
        )
    }

    override fun saveLimits(limits: Map<String, Int>): ShellActionResult {
        val settings = store()
            ?: return ShellActionResult(string(R.string.policy_unavailable), ok = false, refresh = false)
        if (settings.remainingMillis() > 0) {
            return ShellActionResult(string(R.string.rules_locked), ok = false, refresh = false)
        }
        val snap = settings.snapshot()
        val installed = runCatching { ChosenApps.list(context) }
            .getOrDefault(emptyList())
            .map { it.packageName }
            .toSet()
        val previous = LinkedHashMap<String, Int>()
        for (app in snap.timeLimits.apps()) {
            previous[app] = snap.timeLimits.minutesFor(app) ?: 15
        }
        val next = LinkedHashMap(limits)
        for ((app, minutes) in previous) {
            if (app !in installed && app !in next) next[app] = minutes
        }
        if (next.size > AppTimeLimit.MAX_APPS) {
            return ShellActionResult(string(R.string.time_limits_invalid), ok = false, refresh = false)
        }
        return try {
            settings.updateTimeLimits(AppTimeLimit(next))
            val message = if (Permissions.usage(context)) {
                string(R.string.rules_saved)
            } else {
                string(R.string.time_limits_saved_access_needed)
            }
            ShellActionResult(message, ok = true, refresh = true)
        } catch (refused: RuntimeException) {
            ShellActionResult(
                string(R.string.time_limits_invalid),
                ok = false,
                refresh = true
            )
        }
    }

    override fun openUsageAccess(): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (unavailable: RuntimeException) {
        false
    }

    override fun lockState(): ShellLockState {
        val settings = store()
        val snap = settings?.snapshot()
        val remaining = settings?.remainingMillis() ?: 0L
        return ShellLockState(
            active = remaining > 0,
            remaining = if (remaining > 0) {
                string(R.string.commitment_remaining, Durations.remaining(context, remaining))
            } else {
                ""
            },
            title = string(R.string.commitment_setup),
            choosePeriod = string(R.string.commitment_choose_period),
            disclosure = string(R.string.commitment_disclosure),
            startLabel = string(R.string.commitment_start),
            cancelLabel = string(android.R.string.cancel),
            needsRules = string(R.string.commitment_needs_rules),
            lockedNote = string(R.string.commitment_locked_note),
            canStart = snap?.hasConfiguredRestrictions() == true,
            options = listOf(1, 7, 30, 90).map { days ->
                ShellLockOption(
                    days = days,
                    label = context.resources.getQuantityString(R.plurals.days, days, days)
                )
            }
        )
    }

    override fun startLock(days: Int): ShellActionResult {
        val settings = store()
            ?: return ShellActionResult(string(R.string.policy_unavailable), ok = false, refresh = false)
        if (settings.snapshot().hasConfiguredRestrictions().not()) {
            return ShellActionResult(string(R.string.commitment_needs_rules), ok = false, refresh = false)
        }
        return try {
            settings.startCommitment(days)
            runCatching { UninstallProtectionController.sync(context) }
            runCatching {
                BootShieldService.mirror(
                    context,
                    ProtectionLock.bootShieldArmed(
                        BuildConfig.SELF_PROTECTION,
                        settings.uninstallLock(),
                        settings.remainingMillis()
                    )
                )
            }
            runCatching { LayerWatchdog.syncSchedule(context) }
            ShellActionResult(string(R.string.native_lock_started), ok = true, refresh = true)
        } catch (refused: RuntimeException) {
            ShellActionResult(refusalText(refused), ok = false, refresh = true)
        }
    }

    override fun appPicker(kind: String): ShellAppPicker {
        val scan = kind == "scanned"
        val snap = store()?.snapshot()
        val settings = store()
        val selected = when {
            snap == null -> emptySet()
            scan -> snap.scannedApps
            else -> snap.blockedApps
        }
        val density = context.resources.displayMetrics.density
        val iconSize = (36 * density).toInt().coerceAtLeast(1)
        val apps = runCatching { ChosenApps.list(context) }.getOrDefault(emptyList())
            .map { entry ->
                ShellApp(
                    packageName = entry.packageName,
                    label = entry.label,
                    icon = appIcon(entry.packageName, iconSize)
                )
            }
        return ShellAppPicker(
            kind = kind,
            title = string(if (scan) R.string.choose_scanned_apps else R.string.choose_blocked_apps),
            note = string(R.string.app_picker_private),
            searchHint = string(R.string.app_search_hint),
            emptyLabel = string(R.string.app_search_empty),
            saveLabel = string(R.string.save_rules),
            cancelLabel = string(android.R.string.cancel),
            lockedNote = string(R.string.rules_locked),
            locked = (settings?.remainingMillis() ?: 0L) > 0,
            max = PolicyStore.MAX_APPS,
            selected = selected,
            apps = apps
        )
    }

    private fun appIcon(packageName: String, size: Int): Bitmap? = runCatching {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        bitmap
    }.getOrNull()

    override fun saveAppPicker(kind: String, selected: Set<String>): ShellActionResult {
        val scan = kind == "scanned"
        val settings = store()
            ?: return ShellActionResult(string(R.string.policy_unavailable), ok = false, refresh = false)
        if (settings.remainingMillis() > 0) {
            return ShellActionResult(string(R.string.rules_locked), ok = false, refresh = false)
        }
        val snap = settings.snapshot()
        val installed = runCatching { ChosenApps.list(context) }
            .getOrDefault(emptyList())
            .map { it.packageName }
            .toSet()
        // The classic picker keeps saved packages that are temporarily uninstalled; only the
        // installed rows can be changed here, so carry the rest forward.
        val previous = if (scan) snap.scannedApps else snap.blockedApps
        val preserved = previous.filter { it !in installed }
        val next = selected + preserved
        if (next.size > PolicyStore.MAX_APPS) {
            return ShellActionResult(
                context.resources.getQuantityString(
                    R.plurals.rules_too_many_apps,
                    PolicyStore.MAX_APPS,
                    PolicyStore.MAX_APPS
                ),
                ok = false,
                refresh = false
            )
        }
        return try {
            settings.update(
                settings.domainText(),
                settings.wordText(),
                if (scan) snap.blockedApps else next,
                if (scan) next else snap.scannedApps
            )
            ShellActionResult(string(R.string.rules_saved), ok = true, refresh = true)
        } catch (refused: RuntimeException) {
            ShellActionResult(refusalText(refused), ok = false, refresh = true)
        }
    }

    override fun dnsConsent(): ShellConsent = ShellConsent(
        title = string(R.string.dns_consent_title),
        body = string(R.string.dns_consent_short) + "\n\n" + string(R.string.dns_consent_body),
        acceptLabel = string(R.string.dns_continue),
        cancelLabel = string(android.R.string.cancel)
    )

    override fun vpnConsentIntent(): Intent? = try {
        android.net.VpnService.prepare(context)
    } catch (unavailable: RuntimeException) {
        null
    }

    override fun startDns(): ShellActionResult {
        return try {
            Compat.startForegroundService(context, Intent(context, DnsVpnService::class.java))
            ShellActionResult(string(R.string.dns_starting), ok = true, refresh = true)
        } catch (unavailable: RuntimeException) {
            ShellActionResult(string(R.string.attention_dns_error), ok = false, refresh = true)
        }
    }

    override fun openClassic() {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
    }
}
