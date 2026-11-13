package app.hisn.android

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.hisn.android.nativeshell.HisnState
import app.hisn.android.nativeshell.NativeShell
import app.hisn.android.nativeshell.ShellActions

/**
 * The app's front door, running the ported native screens. The screens call into [HisnState],
 * which reads and writes the real policy and services; this activity owns the consent dialogs,
 * the VPN permission and the system settings hand-offs.
 */
class ShellActivity : ComponentActivity(), ShellActions {
    private var requestedTab by mutableIntStateOf(0)
    // Counts tab requests, so asking for the same tab twice still switches to it.
    private var tabRequest by mutableIntStateOf(0)
    private var state by mutableStateOf<HisnState?>(null)

    private val vpnConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startDnsService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedTab = tabOf(intent)
        MainActivity.syncShortcuts(this)
        if (savedInstanceState == null) {
            val settings = try {
                PolicyStore.get(this)
            } catch (unavailable: RuntimeException) {
                null
            }
            if (WelcomeActivity.shouldShow(this, settings)) {
                startActivity(Intent(this, WelcomeActivity::class.java))
            }
        }
        val native = HisnState(this, this)
        state = native
        setContent {
            NativeShell(state = native, initialTab = requestedTab, tabRequest = tabRequest)
        }
    }

    override fun onResume() {
        super.onResume()
        ensureDnsRunning()
        rearmGuard()
        state?.refreshAll()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTab = tabOf(intent)
        tabRequest++
    }

    private fun tabOf(intent: Intent?): Int =
        (intent?.getIntExtra("page", 0) ?: 0).coerceIn(0, 2)

    // ------------------------------------------------------------------ actions

    override fun requestDns() {
        // The same disclosure the introduction and the classic screen show before Android's prompt.
        AlertDialog.Builder(this)
            .setTitle(R.string.dns_consent_title)
            .setMessage(
                getString(R.string.dns_consent_short) + "\n\n" +
                    getString(R.string.full_disclosure) + "\n\n" +
                    getString(R.string.dns_consent_body)
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dns_continue) { _, _ -> continueDns() }
            .show()
    }

    private fun continueDns() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        } else {
            prepareDns()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) prepareDns()
    }

    private fun prepareDns() {
        val consent = VpnService.prepare(this)
        if (consent != null) {
            try {
                vpnConsent.launch(consent)
            } catch (unavailable: RuntimeException) {
                toast(getString(R.string.perm_settings_unavailable))
            }
        } else {
            startDnsService()
        }
    }

    private fun startDnsService() {
        try {
            Compat.startForegroundService(this, Intent(this, DnsVpnService::class.java))
        } catch (unavailable: RuntimeException) {
            DnsVpnService.state = DnsVpnService.State.ERROR
            toast(getString(R.string.perm_dns_failed))
        }
    }

    override fun stopDns() {
        try {
            startService(
                Intent(this, DnsVpnService::class.java).setAction(DnsVpnService.ACTION_STOP)
            )
        } catch (unavailable: RuntimeException) {
            toast(getString(R.string.policy_unavailable))
        }
    }

    override fun openAccessibility() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            toast(getString(R.string.perm_access_hint))
        } catch (unavailable: RuntimeException) {
            toast(getString(R.string.perm_settings_unavailable))
        }
    }

    override fun permission(key: String): Boolean = when (key) {
        "accessibility" -> Permissions.accessibility(this)
        "dns" -> Permissions.dnsRunning()
        "usage" -> Permissions.usage(this)
        "overlay" -> Permissions.overlay(this)
        "notifications" -> Permissions.notifications(this)
        "battery" -> Permissions.battery(this)
        else -> false
    }

    override fun openPermission(key: String) {
        val app = android.net.Uri.parse("package:$packageName")
        when (key) {
            "accessibility" -> openAccessibility()
            "dns" -> if (Permissions.dnsRunning()) {
                openSettings(Intent(Settings.ACTION_VPN_SETTINGS))
            } else {
                requestDns()
            }
            "usage" -> openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            "overlay" -> openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, app))
            "notifications" -> if (!Compat.openNotificationSettings(this)) {
                toast(getString(R.string.perm_settings_unavailable))
            }
            "battery" -> try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, app))
            } catch (unavailable: RuntimeException) {
                toast(getString(R.string.battery_unavailable))
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (missing: RuntimeException) {
                    // The message above already says where to go.
                }
            }
        }
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (unavailable: RuntimeException) {
            toast(getString(R.string.perm_settings_unavailable))
        }
    }

    override fun remainingLabel(millis: Long): String = Durations.remaining(this, millis)

    override fun openGuardian() = openSettings(Intent(this, GuardianPairActivity::class.java))

    override fun personal(): app.hisn.android.nativeshell.PersonalSummary {
        val store = PersonalStore(this)
        val profile = store.profile()
        val hours = store.pauseHours()
        val pauses = if (profile.pauses == 0) {
            getString(R.string.personal_no_pauses)
        } else {
            resources.getQuantityString(R.plurals.personal_pauses, profile.pauses, profile.pauses)
        }
        val day = PersonalStore.dayNumber(profile)
        val started = day > 0 || profile.pauses > 0
        return app.hisn.android.nativeshell.PersonalSummary(
            greeting = if (profile.hasName()) {
                getString(R.string.greeting_with_name, PersonalStore.greeting(this), profile.name)
            } else {
                ""
            },
            reason = PersonalStore.reasonLabel(this, profile),
            metric = when {
                !started -> ""
                day > 0 -> getString(R.string.personal_metric, getString(R.string.personal_day, day), pauses)
                else -> pauses
            },
            name = profile.name,
            preset = profile.preset,
            customReason = profile.reason,
            hours = hours.toList(),
            peakStart = app.hisn.core.PauseHours.peakStart(hours)
        )
    }

    override fun openIntroduction() = openSettings(Intent(this, WelcomeActivity::class.java))

    private val lookups = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "hisn-shell-lookup").apply { isDaemon = true }
    }

    override fun checkSite(raw: String, onResult: (app.hisn.android.nativeshell.SiteCheck) -> Unit) {
        lookups.execute {
            var host: String? = null
            var canAdd = false
            val message = try {
                val found = app.hisn.core.SiteCheckInput.host(raw)
                host = found
                val decision = HostData.policy(this).evaluate(found)
                val lines = mutableListOf(
                    getString(R.string.check_site_host, found),
                    siteMessage(decision),
                    getString(R.string.check_site_generation, decision.generation)
                )
                val source = HostListStore.active(this)
                if (source.source != HostListStore.Source.INSTALLED) {
                    lines += getString(R.string.check_site_bundled, source.generation)
                }
                if ("installed-older" == source.error) {
                    lines += getString(R.string.check_site_older)
                } else if (source.error != null) {
                    lines += getString(R.string.check_site_degraded)
                }
                if (HostData.termsFailed()) lines += getString(R.string.check_site_terms_unavailable)
                canAdd = decision.outcome == app.hisn.core.HostDecision.Outcome.NOT_LISTED
                lines.joinToString("\n")
            } catch (invalid: app.hisn.core.SiteCheckInput.Invalid) {
                getString(R.string.check_site_invalid)
            } catch (unavailable: RuntimeException) {
                getString(R.string.check_site_unavailable)
            }
            val result = app.hisn.android.nativeshell.SiteCheck(message, if (canAdd) host else null)
            runOnUiThread { if (!isFinishing && !isDestroyed) onResult(result) }
        }
    }

    private fun siteMessage(decision: app.hisn.core.HostDecision.Result): String = getString(
        when (decision.source) {
            app.hisn.core.HostDecision.Source.PERSONAL_BLOCK -> R.string.check_site_blocked_personal
            app.hisn.core.HostDecision.Source.ADULT_TLD -> R.string.check_site_blocked_tld
            app.hisn.core.HostDecision.Source.HOST_TERM -> R.string.check_site_blocked_term
            app.hisn.core.HostDecision.Source.PUBLISHED_LIST -> R.string.check_site_blocked_list
            else -> when (decision.outcome) {
                app.hisn.core.HostDecision.Outcome.NOT_LISTED -> R.string.check_site_not_listed
                app.hisn.core.HostDecision.Outcome.UNAVAILABLE -> R.string.check_site_unavailable
                else -> R.string.check_site_invalid
            }
        }
    )

    override fun listUpdateSummary(): String {
        val generation = AdultListUpdater.installedGeneration(this)
        val lines = mutableListOf(
            getString(
                if (generation == BuildConfig.DOMAIN_SEED_VERSION) R.string.list_update_bundled
                else R.string.list_update_installed,
                generation
            ),
            getString(R.string.list_update_domains_only)
        )
        val verified = AdultListUpdater.verifiedAt(this)
        if (verified > 0) {
            lines += getString(
                R.string.list_update_verified,
                java.text.DateFormat.getDateTimeInstance().format(java.util.Date(verified))
            )
        }
        val error = AdultListUpdater.lastError(this)
        if (!error.isNullOrEmpty()) lines += getString(R.string.list_update_failed, error)
        return lines.joinToString("\n\n")
    }

    override fun checkListUpdate(onResult: (String) -> Unit) {
        AdultListUpdater.check(this, true) { result ->
            if (isFinishing || isDestroyed) return@check
            onResult(
                if (!result.error.isNullOrEmpty()) {
                    getString(R.string.list_update_failed, result.error)
                } else {
                    getString(
                        if (result.updated) R.string.list_update_new else R.string.list_update_ok,
                        result.generation
                    )
                }
            )
        }
    }

    private val exportRulesFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val settings = PolicyStore.get(this)
            val out = contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("no stream")
            out.use {
                it.write(
                    app.hisn.core.RuleDocument.export(settings.domainText(), settings.wordText())
                        .toByteArray(Charsets.UTF_8)
                )
            }
            toast(getString(R.string.rules_exported))
        } catch (failed: RuntimeException) {
            toast(getString(R.string.rules_export_failed))
        } catch (failed: java.io.IOException) {
            toast(getString(R.string.rules_export_failed))
        }
    }

    private val importRulesFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val input = contentResolver.openInputStream(uri) ?: throw java.io.IOException("no stream")
            val imported = input.use {
                app.hisn.core.RuleDocument.parse(
                    HostListStore.read(it, app.hisn.core.RuleDocument.MAX_BYTES + 1L)
                )
            }
            var body = getString(R.string.rules_import_body, imported.blocks.size, imported.words.size)
            val skipped = imported.skippedHosts + imported.skippedWords
            if (skipped > 0) body += "\n" + getString(R.string.rules_import_skipped, skipped)
            if (imported.unsupported.isNotEmpty()) {
                body += "\n" + getString(
                    R.string.rules_import_unsupported, imported.unsupported.joinToString(", ")
                )
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.rules_import_title)
                .setMessage(body)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.rules_import_apply) { _, _ -> applyImport(imported) }
                .show()
        } catch (tooLarge: java.io.IOException) {
            toast(getString(R.string.rules_import_oversized))
        } catch (invalid: RuntimeException) {
            toast(getString(R.string.rules_import_failed))
        }
    }

    /** Imported rules only add to the saved ones; a protection lock refuses the write. */
    private fun applyImport(imported: app.hisn.core.RuleDocument.Result) {
        try {
            val settings = PolicyStore.get(this)
            val current = settings.snapshot()
            settings.update(
                app.hisn.core.RuleDocument.merge(settings.domainText(), imported.blocks),
                app.hisn.core.RuleDocument.merge(settings.wordText(), imported.words),
                current.blockedApps, current.scannedApps
            )
            toast(getString(R.string.rules_imported))
        } catch (refused: RuntimeException) {
            toast(
                getString(
                    when (app.hisn.core.PolicyChangeError.of(refused)) {
                        app.hisn.core.PolicyChangeError.COMMITMENT -> R.string.rules_locked
                        app.hisn.core.PolicyChangeError.INVALID -> R.string.rules_invalid
                        app.hisn.core.PolicyChangeError.SAVE_FAILED -> R.string.rules_save_failed
                        else -> R.string.policy_unavailable
                    }
                )
            )
        }
        state?.refreshAll()
    }

    override fun exportRules() {
        try {
            exportRulesFile.launch("hisn-rules.json")
        } catch (unavailable: RuntimeException) {
            toast(getString(R.string.rules_export_failed))
        }
    }

    override fun importRules() {
        try {
            importRulesFile.launch(arrayOf("application/json", "text/plain"))
        } catch (unavailable: RuntimeException) {
            toast(getString(R.string.rules_import_failed))
        }
    }

    override fun savePersonal(name: String, preset: String, reason: String) {
        PersonalStore(this).save(name, preset, reason)
    }

    override fun clearPersonal() {
        PersonalStore(this).clear()
    }

    override fun openClassic(page: Int) =
        openSettings(Intent(this, MainActivity::class.java).putExtra("page", page))

    override fun confirmNetworkCheck() {
        AlertDialog.Builder(this)
            .setTitle(R.string.network_check_button)
            .setMessage(R.string.network_check_disclosure)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.network_check_start) { _, _ ->
                if (!DnsVpnService.requestConnectionCheck()) {
                    toast(getString(R.string.network_check_failed))
                }
                state?.refreshAll()
            }
            .show()
    }

    /** The classic lock flow: the disclosure, the real commitment, then the guards it arms. */
    override fun startLock(days: Int, onDone: () -> Unit) {
        val settings = try {
            PolicyStore.get(this)
        } catch (unavailable: RuntimeException) {
            toast(getString(R.string.policy_unavailable))
            return
        }
        if (!settings.snapshot().hasConfiguredRestrictions()) {
            toast(getString(R.string.commitment_needs_rules))
            return
        }
        AlertDialog.Builder(this)
            .setTitle(resources.getQuantityString(R.plurals.days, days, days))
            .setMessage(R.string.commitment_disclosure)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.commitment_start) { _, _ ->
                try {
                    settings.startCommitment(days)
                    UninstallProtectionController.sync(this)
                    BootShieldService.mirror(
                        this,
                        app.hisn.core.ProtectionLock.bootShieldArmed(
                            BuildConfig.SELF_PROTECTION, settings.uninstallLock(),
                            settings.remainingMillis()
                        )
                    )
                    try {
                        LayerWatchdog.syncSchedule(this)
                    } catch (unavailable: RuntimeException) {
                        // The next open retries.
                    }
                    if (BuildConfig.SELF_PROTECTION && !Settings.canDrawOverlays(this)) {
                        toast(getString(R.string.boot_shield_permission))
                        openPermission("overlay")
                    }
                } catch (refused: RuntimeException) {
                    toast(
                        getString(
                            when (app.hisn.core.PolicyChangeError.of(refused)) {
                                app.hisn.core.PolicyChangeError.COMMITMENT -> R.string.rules_locked
                                app.hisn.core.PolicyChangeError.NEEDS_RULES -> R.string.commitment_needs_rules
                                app.hisn.core.PolicyChangeError.SAVE_FAILED -> R.string.rules_save_failed
                                else -> R.string.policy_unavailable
                            }
                        )
                    )
                }
                onDone()
            }
            .show()
    }

    override fun openGrayScreenHelp() {
        val command = "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
        AlertDialog.Builder(this)
            .setTitle("Gray screen needs one adb grant")
            .setMessage(
                "Android only lets an app switch the grayscale filter with WRITE_SECURE_SETTINGS, " +
                    "which is granted once over a computer:\n\n$command\n\n" +
                    "Without it, Settings › Accessibility › Color correction › Grayscale still works."
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Open Accessibility") { _, _ -> openAccessibility() }
            .show()
    }

    override fun confirmAdult(enable: Boolean, onAccept: () -> Unit) {
        confirm(
            if (enable) R.string.adult_mode_enable else R.string.adult_mode_disable,
            R.string.adult_mode_short,
            R.string.adult_mode_disclosure,
            onAccept
        )
    }

    override fun confirmImages(enable: Boolean, onAccept: () -> Unit) {
        confirm(
            if (enable) R.string.visual_enable else R.string.visual_disable,
            R.string.visual_short,
            R.string.visual_disclosure,
            onAccept
        )
    }

    override fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    // ------------------------------------------------------------------ lifecycle helpers

    private fun ensureDnsRunning() {
        if (!DnsVpnService.resumeRequested(this)) return
        val current = DnsVpnService.state
        if (current == DnsVpnService.State.RUNNING || current == DnsVpnService.State.STARTING ||
            current == DnsVpnService.State.DEGRADED
        ) return
        try {
            if (VpnService.prepare(this) != null) return
            Compat.startForegroundService(this, Intent(this, DnsVpnService::class.java))
        } catch (unavailable: RuntimeException) {
            // The dashboard reports the gap.
        }
    }

    private fun rearmGuard() {
        if (TextGuardService.connected || !Permissions.accessibility(this)) return
        try {
            sendBroadcast(Intent(TextGuardService.ACTION_REARM).setPackage(packageName))
        } catch (unavailable: RuntimeException) {
            // The setup row still offers the settings page.
        }
    }

    private fun confirm(title: Int, short: Int, disclosure: Int, onAccept: () -> Unit) {
        val message = getString(short) + "\n\n" +
            getString(R.string.full_disclosure) + "\n\n" +
            getString(disclosure)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save_rules) { _, _ -> onAccept() }
            .show()
    }
}
