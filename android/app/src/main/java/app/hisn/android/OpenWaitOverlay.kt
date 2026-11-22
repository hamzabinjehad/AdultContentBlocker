package app.hisn.android

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.hisn.android.nativeshell.HisnTheme
import app.hisn.android.nativeshell.OpenWaitScreen

/**
 * The wait as an accessibility overlay, for phones that drop the wait activity.
 *
 * Xiaomi and vivo add a "pop-up windows while running in the background" permission that can
 * silently stop [OpenWaitActivity] from appearing, which would let the app open with no wait.
 * An accessibility overlay belongs to the guard service itself and needs no such permission, so
 * the same screen is drawn here instead. Close presses Home; Open removes the overlay.
 */
object OpenWaitOverlay {
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    /** The service's window manager: only the one that added the overlay can remove it. */
    private var manager: WindowManager? = null

    /** The app the overlay is waiting for, or null when none is shown. */
    @JvmStatic
    var app: String? = null
        private set

    /** When the overlay appeared; the launcher's own closing events arrive just after. */
    private var shownAt = 0L

    /** Whether a window event from another app means the person really left. */
    @JvmStatic
    fun leftBy(eventPackage: String, ownPackage: String): Boolean {
        val waiting = app ?: return false
        if (eventPackage == waiting || eventPackage == ownPackage || eventPackage == "com.android.systemui") return false
        return android.os.SystemClock.elapsedRealtime() - shownAt > 1500
    }

    @JvmStatic
    fun show(service: AccessibilityService, app: String): Boolean {
        ShieldMoments.mark(service)
        if (view != null) hide(service)
        val manager = service.getSystemService(WindowManager::class.java) ?: return false
        val label = runCatching {
            val pm = service.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(app, 0)).toString()
        }.getOrDefault(app)
        val reason = runCatching {
            val profile = PersonalStore(service).profile()
            if (profile.reason.isNullOrBlank() && profile.preset.isNullOrBlank()) ""
            else PersonalStore.reasonLabel(service, profile).orEmpty()
        }.getOrDefault("")
        val seconds = OpenWaitStore.waitFor(service, app)
        val opened = OpenWaitStore.openedToday(service, app)
        val closed = OpenWaitStore.closedToday(service, app)
        val dark = when (service.getSharedPreferences("hisn-native", Context.MODE_PRIVATE)
            .getString("ui.theme", "system")) {
            "dark" -> true
            "light" -> false
            else -> (service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
        val lifecycle = OverlayOwner()
        val compose = ComposeView(service).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            setContent {
                HisnTheme(darkTheme = dark) {
                    OpenWaitScreen(
                        appLabel = label,
                        appPackage = app,
                        seconds = seconds,
                        openedToday = opened,
                        closedToday = closed,
                        reason = reason,
                        onClose = {
                            OpenWaitStore.closed(service, app)
                            hide(service)
                            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                        },
                        onOpen = {
                            OpenWaitStore.opened(service, app)
                            hide(service)
                        }
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        }
        return try {
            lifecycle.start()
            manager.addView(compose, params)
            view = compose
            owner = lifecycle
            this.manager = manager
            this.app = app
            shownAt = android.os.SystemClock.elapsedRealtime()
            true
        } catch (unavailable: RuntimeException) {
            android.util.Log.w("HisnWait", "The wait overlay was refused", unavailable)
            lifecycle.stop()
            false
        }
    }

    /** Removes the overlay; leaving the app another way counts as neither open nor close. */
    @JvmStatic
    fun hide(context: Context) {
        val shown = view ?: return
        runCatching { (manager ?: context.getSystemService(WindowManager::class.java))?.removeView(shown) }
        owner?.stop()
        view = null
        owner = null
        manager = null
        app = null
    }

    /** The lifecycle a ComposeView needs outside an activity. */
    private class OverlayOwner : SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

        fun start() {
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
