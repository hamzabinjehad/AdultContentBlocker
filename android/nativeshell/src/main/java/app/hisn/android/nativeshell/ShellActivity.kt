package app.hisn.android.nativeshell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Debug-only entry point for the native shell.
 *
 * Phase 2 runs in the real `app.hisn.android` package so it can read the installed policy and
 * service state; the host implementation is installed by the debug `Application`. The classic
 * [app.hisn.android.MainActivity] remains the launcher until the native editors reach parity.
 */
class ShellActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HisnShellTheme {
                NativeShell(NativeShellBridge.host)
            }
        }
    }
}
