package app.hisn.android

import android.content.Context
import android.content.Intent
import android.os.Build
import app.hisn.android.nativeshell.NativeShellHost
import app.hisn.android.nativeshell.ShellActionResult
import app.hisn.android.nativeshell.ShellIssue
import app.hisn.android.nativeshell.ShellLabels
import app.hisn.android.nativeshell.ShellLayer
import app.hisn.android.nativeshell.ShellProtection
import app.hisn.android.nativeshell.ShellRule
import app.hisn.android.nativeshell.ShellRules
import app.hisn.android.nativeshell.ShellSetting
import app.hisn.android.nativeshell.ShellSettings
import java.util.Locale

/**
 * The debug-only bridge between the Compose shell and the real Hisn state.
 *
 * It lives in `app.hisn.android` so it can read package-private production helpers
 * ([Permissions], `DnsVpnService.resumeRequested`) without widening their API. It never writes a
 * rule and never starts or stops a layer yet: the Phase 2 shell is a real read-only dashboard,
 * and changes still open [MainActivity] so the audited editors keep owning policy writes.
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
        layers = string(R.string.native_layers)
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
        val snap = store()?.snapshot()
        val settings = store()
        if (snap == null) {
            return ShellRules(
                builtInTitle = string(R.string.section_builtin),
                customTitle = string(R.string.section_rules_custom),
                note = string(R.string.native_rules_note),
                builtIn = emptyList(),
                custom = emptyList(),
                labels = labels
            )
        }
        val lockMs = settings?.remainingMillis() ?: 0L
        val builtIn = listOf(
            ShellRule(
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
                label = string(R.string.edit_domains),
                detail = plurals(R.plurals.saved_domains, snap.domains.blockedRules().size),
                state = "",
                on = false
            ),
            ShellRule(
                label = string(R.string.edit_words),
                detail = plurals(R.plurals.saved_words, snap.words.configuredRules().size),
                state = "",
                on = false
            ),
            ShellRule(
                label = string(R.string.section_apps),
                detail = plurals(R.plurals.blocked_apps_count, snap.blockedApps.size),
                state = "",
                on = false
            ),
            ShellRule(
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
                label = string(R.string.section_time_limits),
                detail = if (snap.timeLimits.configured()) {
                    string(R.string.state_on)
                } else {
                    string(R.string.state_off)
                },
                state = "",
                on = false
            ),
            ShellRule(
                label = string(R.string.section_commitment),
                detail = if (lockMs > 0) {
                    string(R.string.state_on)
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
            labels = labels
        )
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
        return ShellSettings(
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

    override fun openClassic() {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
    }
}
