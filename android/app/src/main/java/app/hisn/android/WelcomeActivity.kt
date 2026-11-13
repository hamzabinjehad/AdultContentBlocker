package app.hisn.android

import android.Manifest
import android.app.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.hisn.android.nativeshell.HisnTheme
import app.hisn.android.nativeshell.Onboarding
import app.hisn.android.nativeshell.OnboardingActions
import app.hisn.android.nativeshell.SystemBars

/**
 * First run, in the shell's own look: three one-sentence intros, an optional name and reason,
 * the Android grants (needed versus optional), and on the direct build the uninstall guard.
 *
 * The page grants nothing by itself. Each row opens the exact Android screen or starts the one
 * service it names, after the same disclosure the shell shows; returning here re-reads the
 * platform state. Skipping is always available.
 */
class WelcomeActivity : ComponentActivity(), OnboardingActions {
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
            val dark = when (theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            HisnTheme(darkTheme = dark) {
                SystemBars(dark)
                Onboarding(
                    actions = this,
                    directBuild = BuildConfig.SELF_PROTECTION,
                    initialName = saved.name,
                    initialPreset = saved.preset,
                    initialReason = saved.reason,
                    refresh = refresh
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh++
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

    override fun request(key: String) {
        val app = Uri.parse("package:$packageName")
        when (key) {
            "accessibility" -> openSettings(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), R.string.perm_access_hint
            )
            "dns" -> requestDns()
            "usage" -> openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), 0)
            "overlay" -> if (!tryStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, app))) {
                openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION), 0)
            }
            "notifications" -> requestNotifications()
            "battery" -> if (!tryStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, app))) {
                openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), 0)
            }
        }
    }

    override fun guardOn(): Boolean = try {
        settings?.uninstallLock() == true
    } catch (unreadable: RuntimeException) {
        false
    }

    override fun savePersonal(name: String, preset: String, reason: String) {
        try {
            PersonalStore(this).save(name, preset, reason)
        } catch (unsaved: RuntimeException) {
            // Optional words; the person can add them later in Settings.
        }
    }

    override fun finish(enableGuard: Boolean) {
        if (enableGuard) {
            var enabled = false
            try {
                settings?.updateUninstallLock(true)
                enabled = settings != null
            } catch (refused: RuntimeException) {
                toast(R.string.policy_unavailable)
            }
            if (enabled && !Permissions.accessibility(this)) toast(R.string.welcome_lock_needs_access)
        }
        markSeen(this)
        // The native shell is the app's home.
        startActivity(
            Intent(this, ShellActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    // ------------------------------------------------------------------ system hand-offs

    /** The same disclosure the shell and the classic page show before Android's VPN prompt. */
    private fun requestDns() {
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
