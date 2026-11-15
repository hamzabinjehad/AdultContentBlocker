package app.hisn.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.hisn.android.nativeshell.HisnTheme
import app.hisn.android.nativeshell.InstalledApp
import app.hisn.android.nativeshell.OpenWaitSettings
import app.hisn.android.nativeshell.SystemBars
import app.hisn.android.nativeshell.Usage
import app.hisn.android.nativeshell.installedApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Rules › Wait before opening. */
class OpenWaitSettingsActivity : ComponentActivity() {

    private var resumed by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val theme = getSharedPreferences("hisn-native", Context.MODE_PRIVATE).getString("ui.theme", "system")
        setContent {
            val dark = when (theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            var chosen by remember { mutableStateOf(OpenWaitStore.apps(this)) }
            var seconds by remember { mutableIntStateOf(OpenWaitStore.seconds(this)) }
            var grows by remember { mutableStateOf(OpenWaitStore.grows(this)) }
            val guardOn = remember(resumed) { Permissions.accessibility(this) }
            var showAll by remember { mutableStateOf(false) }
            val apps by produceState(initialValue = emptyList<InstalledApp>(), showAll) {
                value = withContext(Dispatchers.IO) {
                    val context = this@OpenWaitSettingsActivity
                    val used = if (Usage.hasAccess(context)) Usage.usedTodayAll(context) else emptyMap()
                    val saved = OpenWaitStore.apps(context)
                    // Only apps the guard watches: never Settings, launchers or Hisn itself.
                    val skipped = runCatching { ChosenApps.excluded(context) }.getOrDefault(emptySet())
                    // Chosen apps first, then today's most used, then by name.
                    installedApps(context, showAll).filter { it.packageName !in skipped }.sortedWith(
                        compareByDescending<InstalledApp> { it.packageName in saved }
                            .thenByDescending { used[it.packageName] ?: 0L }
                            .thenBy { it.label.lowercase() }
                    )
                }
            }
            HisnTheme(darkTheme = dark) {
                SystemBars(dark)
                OpenWaitSettings(
                    apps = apps,
                    chosen = chosen,
                    seconds = seconds,
                    grows = grows,
                    guardOn = guardOn,
                    showAll = showAll,
                    onShowAll = { showAll = it },
                    onChange = { newApps, newSeconds, newGrows ->
                        if (OpenWaitStore.save(this, newApps, newSeconds, newGrows)) {
                            chosen = newApps
                            seconds = newSeconds
                            grows = newGrows
                        } else {
                            Toast.makeText(this, R.string.open_wait_locked, Toast.LENGTH_LONG).show()
                        }
                    },
                    onFixGuard = {
                        runCatching {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            Toast.makeText(this, R.string.perm_access_hint, Toast.LENGTH_LONG).show()
                        }
                    },
                    onBack = ::finish
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed++
    }
}
