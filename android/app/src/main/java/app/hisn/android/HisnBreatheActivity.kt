package app.hisn.android

import app.hisn.android.nativeshell.arabicDigitsContext
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.hisn.android.nativeshell.HisnBreathe
import app.hisn.android.nativeshell.HisnTheme
import app.hisn.android.nativeshell.systemReduceMotion

/**
 * The Islamic edition's voluntary pause, from Help now, the block screen, the launcher shortcut
 * and the Quick Settings tile: three breaths with istighfār, then wuḍūʾ, two rakʿahs and a du'a.
 * Nothing is blocked here, and the pause is counted on this phone like the other pause screens.
 */
class HisnBreatheActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(arabicDigitsContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A rotation is the same pause: only a fresh start counts.
        PauseCount.record(this, savedInstanceState == null)
        if (savedInstanceState == null) StepIns.record(this, StepIns.BREATHED)
        val profile = PersonalStore(this).profile()
        val reason = if (profile.hasReason()) PersonalStore.reasonLabel(this, profile) else ""
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val still = systemReduceMotion(this)
        setContent {
            HisnTheme(darkTheme = false) {
                HisnBreathe(reason = reason, still = still, onClose = ::finish)
            }
        }
    }
}
