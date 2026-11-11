package app.hisn.android

import android.app.Application
import app.hisn.android.nativeshell.NativeShellBridge

/**
 * Debug-only wiring for the native shell. The release application has no Application subclass and
 * never links the shell; a plain debug build installs the same no-op bridge and keeps the classic
 * launcher.
 */
class NativeShellDebugApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NativeShellBridge.host = LiveShellHost(this)
    }
}
