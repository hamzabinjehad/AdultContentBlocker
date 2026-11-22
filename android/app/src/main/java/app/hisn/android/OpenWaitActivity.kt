package app.hisn.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import app.hisn.android.nativeshell.HisnTheme
import app.hisn.android.nativeshell.OpenWaitScreen
import app.hisn.android.nativeshell.SystemBars

/**
 * Comes up over an app the person chose for "Wait before opening". "Close" (and Back) goes
 * Home; "Open" brings the app's own task back once the wait is over. Nothing is blocked.
 */
class OpenWaitActivity : ComponentActivity() {

    private var app: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        app = intent.getStringExtra(EXTRA_APP).orEmpty()
        if (app.isEmpty()) {
            finish()
            return
        }
        if (savedInstanceState == null) ShieldMoments.mark(this)
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(app, 0)).toString()
        }.getOrDefault(app)
        val reason = runCatching {
            val profile = PersonalStore(this).profile()
            if (profile.reason.isNullOrBlank() && profile.preset.isNullOrBlank()) ""
            else PersonalStore.reasonLabel(this, profile).orEmpty()
        }.getOrDefault("")
        val seconds = OpenWaitStore.waitFor(this, app)
        val opened = OpenWaitStore.openedToday(this, app)
        val closed = OpenWaitStore.closedToday(this, app)
        val theme = getSharedPreferences("hisn-native", Context.MODE_PRIVATE).getString("ui.theme", "system")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = close()
        })
        setContent {
            val dark = when (theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            HisnTheme(darkTheme = dark) {
                SystemBars(dark)
                OpenWaitScreen(
                    appLabel = label,
                    appPackage = app,
                    seconds = seconds,
                    openedToday = opened,
                    closedToday = closed,
                    reason = reason,
                    onClose = ::close,
                    onOpen = ::open,
                    onOpenFor = ::openFor
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The guard checks this to know the wait really appeared (see OpenWaitOverlay).
        resumedFor = app
        resumedAt = android.os.SystemClock.elapsedRealtime()
        // On a slow phone the overlay may have stood in already; one wait is enough.
        if (OpenWaitOverlay.app == app) OpenWaitOverlay.hide(this)
    }

    private fun close() {
        OpenWaitStore.closed(this, app)
        runCatching {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    private fun openFor(minutes: Int) {
        OpenWaitStore.openedFor(this, app, minutes)
        launchApp()
    }

    private fun open() {
        OpenWaitStore.opened(this, app)
        launchApp()
    }

    private fun launchApp() {
        val launch = packageManager.getLaunchIntentForPackage(app)
        if (launch != null) runCatching { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
    }

    companion object {
        const val EXTRA_APP = "app"

        @JvmStatic @Volatile var resumedFor: String? = null
        @JvmStatic @Volatile var resumedAt: Long = 0L
    }
}
