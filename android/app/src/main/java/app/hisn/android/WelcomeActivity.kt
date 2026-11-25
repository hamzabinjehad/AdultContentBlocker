package app.hisn.android

import app.hisn.android.nativeshell.arabicDigitsContext
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.hisn.android.nativeshell.HisnTheme
import app.hisn.android.nativeshell.HisnOnboarding
import app.hisn.android.nativeshell.OnboardingActions
import app.hisn.android.nativeshell.OnboardingProtectionState
import app.hisn.android.nativeshell.OnboardingProtectionState.NEEDS_ATTENTION
import app.hisn.android.nativeshell.OnboardingProtectionState.OFF
import app.hisn.android.nativeshell.OnboardingProtectionState.ON
import app.hisn.android.nativeshell.OnboardingProtectionState.STARTING
import app.hisn.android.nativeshell.SystemBars
import app.hisn.android.nativeshell.UnrotOnboarding
import app.hisn.android.nativeshell.UnrotSetupActions
import app.hisn.core.AppTimeLimit
import app.hisn.core.PersonalProfile
import app.hisn.core.SetupPlan
import app.hisn.core.ProtectionLock
import kotlinx.coroutines.delay

/**
 * Owns the system hand-offs and reads live protection for the first-run flow.
 */
class WelcomeActivity : ComponentActivity(), UnrotSetupActions {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(arabicDigitsContext(newBase))
    }

    private data class ProtectionReading(val state: OnboardingProtectionState, val issue: Int? = null)

    private var refresh by mutableIntStateOf(0)
    private var settings: PolicyStore? = null

    private val vpnConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startDnsService() else refresh++
    }

    private val notificationGrant = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = try {
            PolicyStore.get(this)
        } catch (unavailable: RuntimeException) {
            null
        }
        val saved = PersonalStore(this).profile()
        val theme = getSharedPreferences("hisn-native", Context.MODE_PRIVATE)
            .getString("ui.theme", "system")
        setContent {
            LaunchedEffect(Unit) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    var previous = readAppProtection() to readDnsProtection()
                    refresh++
                    while (true) {
                        delay(500)
                        val current = readAppProtection() to readDnsProtection()
                        if (current != previous) {
                            previous = current
                            refresh++
                        }
                    }
                }
            }
            val dark = when (theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            HisnTheme(darkTheme = dark) {
                SystemBars(dark)
                if (Edition.islamic) {
                    HisnOnboarding(
                        actions = this,
                        directBuild = BuildConfig.SELF_PROTECTION,
                        initialName = saved.name,
                        initialReason = if (saved.preset == PersonalProfile.CUSTOM) saved.reason else "",
                        refresh = refresh
                    )
                } else {
                    UnrotOnboarding(
                        actions = this,
                        refresh = refresh,
                        initialReason = if (saved.preset == PersonalProfile.CUSTOM) saved.reason else ""
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!TextGuardService.connected && Permissions.accessibility(this)) {
            // As in the shell, ask a live guard instance to rearm after an Android rebind.
            runCatching { sendBroadcast(Intent(TextGuardService.ACTION_REARM).setPackage(packageName)) }
        }
    }

    // ------------------------------------------------------------------ OnboardingActions

    override fun granted(key: String): Boolean = when (key) {
        "accessibility" -> Permissions.accessibility(this)
        "dns" -> Permissions.dnsRunning()
        "usage" -> Permissions.usage(this)
        "overlay" -> Permissions.overlay(this)
        "notifications" -> Permissions.notifications(this)
        "battery" -> Permissions.battery(this)
        else -> false
    }

    override fun protectionState(key: String): OnboardingProtectionState = protectionReading(key).state

    override fun protectionIssue(key: String): Int? = protectionReading(key).issue

    private fun protectionReading(key: String): ProtectionReading = when (key) {
        "accessibility" -> readAppProtection()
        "dns" -> readDnsProtection()
        else -> ProtectionReading(OFF)
    }

    private fun readAppProtection(): ProtectionReading = when {
        !Permissions.accessibility(this) -> ProtectionReading(OFF)
        GuardPause.paused(this) -> ProtectionReading(OFF)
        !TextGuardService.connected -> ProtectionReading(NEEDS_ATTENTION, R.string.attention_guard_disconnected)
        TextGuardService.ready -> ProtectionReading(ON)
        else -> ProtectionReading(NEEDS_ATTENTION, R.string.welcome_guard_unready)
    }

    private fun readDnsProtection(): ProtectionReading = when (val state = DnsVpnService.state) {
        DnsVpnService.State.OFF -> ProtectionReading(OFF)
        DnsVpnService.State.STARTING -> ProtectionReading(STARTING)
        DnsVpnService.State.ERROR -> ProtectionReading(NEEDS_ATTENTION, R.string.attention_dns_error)
        DnsVpnService.State.RUNNING, DnsVpnService.State.DEGRADED -> when {
            DnsVpnService.privateDnsStrict -> ProtectionReading(NEEDS_ATTENTION, R.string.attention_private_dns)
            state == DnsVpnService.State.DEGRADED ->
                ProtectionReading(NEEDS_ATTENTION, R.string.attention_dns_degraded)
            else -> ProtectionReading(ON)
        }
    }

    override fun request(key: String) {
        val app = Uri.parse("package:$packageName")
        when (key) {
            "accessibility" -> requestAccessibility()
            "dns" -> when (val issue = protectionIssue(key) ?: 0) {
                R.string.attention_private_dns, R.string.attention_dns_degraded ->
                    openNetworkSettings(issue)
                else -> requestDns()
            }
            "usage" -> openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), 0)
            "overlay" -> if (!tryStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, app))) {
                openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION), 0)
            }
            "notifications" -> requestNotifications()
            "battery" -> if (!OemSupport.openBattery(this)) {
                toast(R.string.battery_unavailable)
            }
        }
    }

    override fun guardOn(): Boolean = try {
        settings?.let { it.uninstallLock() || ProtectionLock.locked(it.remainingMillis()) } == true
    } catch (unreadable: RuntimeException) {
        false
    }

    override fun setGuard() {
        if (!BuildConfig.SELF_PROTECTION) return
        if (protectionState("accessibility") != ON) {
            toast(R.string.welcome_lock_needs_access)
            refresh++
            return
        }
        val store = settings
        if (store == null) {
            toast(R.string.policy_unavailable)
            return
        }
        try {
            store.updateUninstallLock(true)
        } catch (refused: RuntimeException) {
            toast(R.string.policy_unavailable)
        }
        refresh++
    }

    override fun savePersonal(name: String, preset: String, reason: String) {
        try {
            PersonalStore(this).save(name, preset, reason)
        } catch (unsaved: RuntimeException) {
            // Optional words; the person can add them later in Settings.
        }
    }

    /**
     * Unrot's setup choices, merged into whatever rules already exist: a second run of setup adds
     * to the person's rules and never quietly removes one. A running protection lock refuses
     * the write; the setup then says so instead of pretending.
     */
    override fun applyPlan(plan: SetupPlan, note: String): Boolean {
        val store = settings ?: return false
        return try {
            val rules = store.snapshot()
            if (plan.waitApps().isNotEmpty()) {
                val apps = OpenWaitStore.apps(this) + plan.waitApps()
                val seconds = maxOf(OpenWaitStore.seconds(this), SetupPlan.WAIT_SECONDS)
                OpenWaitStore.save(this, apps, seconds, true)
            }
            if (plan.limits().isNotEmpty()) {
                val current = rules.timeLimits.apps().mapNotNull { pkg ->
                    rules.timeLimits.minutesFor(pkg)?.let { pkg to it }
                }.toMap()
                store.updateTimeLimits(AppTimeLimit(plan.limits() + current))
            }
            if (plan.shortVideos() && !rules.shortVideoEnabled) store.updateShortVideo(true)
            if (plan.blockedApps().isNotEmpty()) {
                store.update(store.domainText(), store.wordText(), rules.blockedApps + plan.blockedApps(),
                    rules.scannedApps, rules.adultWordsEnabled)
            }
            // "No adult content": the word and image checks read the phone's browsers, where adult
            // pages open. Without at least one chosen app those checks are on but see nothing.
            if (plan.adult) {
                val now = store.snapshot()
                if (app.hisn.core.ScanScope.chosen(now.scannedApps) == 0 && !app.hisn.core.ScanScope.all(now.scannedApps)) {
                    val browsers = ChosenApps.browsers(this).map { it.packageName }.toSet()
                    if (browsers.isNotEmpty()) {
                        store.update(store.domainText(), store.wordText(), now.blockedApps, now.scannedApps + browsers,
                            now.adultWordsEnabled)
                    }
                }
            }
            if (plan.quietHours()) store.updateSchedule(plan.schedule())
            if (note.isNotBlank()) {
                val profile = PersonalStore(this).profile()
                PersonalStore(this).save(profile.name, PersonalProfile.CUSTOM, note)
            }
            refresh++
            true
        } catch (refused: RuntimeException) {
            refresh++
            false
        }
    }

    override fun completeSetup() {
        markSeen(this)
        // The native shell is the app's home.
        startActivity(
            Intent(this, ShellActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    override fun checkOn(key: String): Boolean = try {
        val rules = settings?.snapshot()
        when (key) {
            "tags" -> rules?.adultWordsEnabled == true
            "images" -> rules?.visualEnabled == true
            "allapps" -> app.hisn.core.ScanScope.all(rules?.scannedApps)
            else -> false
        }
    } catch (unreadable: RuntimeException) {
        false
    }

    /** Turning a check on shows the same disclosure as the shell's switch; off saves at once. */
    override fun setCheck(key: String, on: Boolean) {
        val write = { saveCheck(key, on) }
        if (!on) {
            write()
            return
        }
        when (key) {
            "tags" -> confirm(
                R.string.adult_mode_enable, R.string.adult_mode_short, R.string.adult_mode_disclosure,
                R.string.save_rules, write
            )
            "images" -> confirm(
                R.string.visual_enable, R.string.visual_short, R.string.visual_disclosure,
                R.string.save_rules, write
            )
            // The page has already shown "Check every app?" with what it costs.
            "allapps" -> write()
        }
    }

    override fun checkedApps(): Set<String> = try {
        settings?.snapshot()?.scannedApps ?: emptySet()
    } catch (unreadable: RuntimeException) {
        emptySet()
    }

    override fun setCheckedApps(packages: Set<String>) {
        val store = settings ?: return
        try {
            val rules = store.snapshot()
            store.update(store.domainText(), store.wordText(), rules.blockedApps, packages, rules.adultWordsEnabled)
        } catch (refused: RuntimeException) {
            // A running protection lock keeps the chosen apps; the shell says why in Rules.
            toast(R.string.policy_unavailable)
        }
        refresh++
    }

    private fun saveCheck(key: String, on: Boolean) {
        val store = settings ?: return
        try {
            val rules = store.snapshot()
            when (key) {
                "tags" -> store.update(store.domainText(), store.wordText(), rules.blockedApps, rules.scannedApps, on)
                "images" -> store.updateVisual(on)
                "allapps" -> store.update(
                    store.domainText(), store.wordText(), rules.blockedApps,
                    app.hisn.core.ScanScope.with(rules.scannedApps, on), rules.adultWordsEnabled
                )
            }
        } catch (refused: RuntimeException) {
            toast(R.string.policy_unavailable)
        }
        refresh++
    }

    private fun confirm(title: Int, short: Int, disclosure: Int, accept: Int, onAccept: () -> Unit) {
        Disclosure.show(this, title, short, disclosure, accept, onAccept)
    }

    // ------------------------------------------------------------------ system hand-offs

    private fun requestAccessibility() {
        // Turned off inside Hisn while Android's switch stayed on: turning it on is just this.
        if (Permissions.accessibility(this) && GuardPause.paused(this)) {
            GuardPause.set(this, false)
            refresh++
            return
        }
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val protection = readAppProtection()
        if (protection.state != OFF) {
            openSettings(intent, protection.issue ?: R.string.perm_access_hint)
            return
        }
        val disclosure = if (BuildConfig.SELF_PROTECTION) {
            R.string.accessibility_disclosure_direct
        } else {
            R.string.accessibility_disclosure
        }
        confirm(
            R.string.accessibility_title, R.string.accessibility_short, disclosure,
            R.string.open_accessibility
        ) { openSettings(intent, R.string.perm_access_hint) }
    }

    /** The same disclosure the shell and the classic page show before Android's VPN prompt. */
    private fun requestDns() {
        confirm(
            R.string.dns_consent_title, R.string.dns_consent_short, R.string.dns_consent_body,
            R.string.dns_continue, ::continueDns
        )
    }

    private fun continueDns() {
        val consent = VpnService.prepare(this)
        if (consent == null) {
            startDnsService()
            return
        }
        try {
            vpnConsent.launch(consent)
        } catch (unavailable: RuntimeException) {
            toast(R.string.perm_settings_unavailable)
        }
    }

    private fun startDnsService() {
        try {
            Compat.startForegroundService(this, Intent(this, DnsVpnService::class.java))
        } catch (unavailable: RuntimeException) {
            if (DnsVpnService.state == DnsVpnService.State.OFF) DnsVpnService.state = DnsVpnService.State.ERROR
            toast(R.string.perm_dns_failed)
        }
        refresh++
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationGrant.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (!Compat.openNotificationSettings(this)) toast(R.string.perm_settings_unavailable)
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (unavailable: RuntimeException) {
        false
    }

    private fun openSettings(intent: Intent, hint: Int) {
        if (tryStart(intent)) {
            if (hint != 0) toast(hint)
        } else {
            toast(R.string.perm_settings_unavailable)
        }
    }

    private fun openNetworkSettings(hint: Int) {
        if (tryStart(Intent(Settings.ACTION_WIRELESS_SETTINGS))) toast(hint)
        else openSettings(Intent(Settings.ACTION_SETTINGS), hint)
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        private const val PREFS = "welcome"
        private const val SEEN = "seen"

        /** A fresh install sees it once; rules already saved mean an upgrade, so no welcome. */
        @JvmStatic
        fun shouldShow(context: Context, settings: PolicyStore?): Boolean {
            if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SEEN, false)) return false
            return try {
                settings != null && !settings.snapshot().hasConfiguredRestrictions()
            } catch (unreadable: RuntimeException) {
                false
            }
        }

        @JvmStatic
        fun markSeen(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(SEEN, true).apply()
        }
    }
}
