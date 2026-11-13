package app.hisn.android.nativeshell

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Gray screen: Android's own Color correction › Grayscale filter, switched from the app.
 *
 * Android only lets an app change it with `WRITE_SECURE_SETTINGS`, which a phone grants once over
 * adb. Without that grant the screen opens Accessibility and says where the switch is, exactly like
 * the production app does — the toggle no longer pretends to have done something.
 */
object ScreenGray {

    private const val ENABLED = "accessibility_display_daltonizer_enabled"
    private const val MODE = "accessibility_display_daltonizer"

    /** The daltonizer mode Android Settings calls Grayscale. */
    private const val MONOCHROMACY = 0

    fun canControl(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun isGray(context: Context): Boolean = runCatching {
        Settings.Secure.getInt(context.contentResolver, ENABLED, 0) == 1 &&
            Settings.Secure.getInt(context.contentResolver, MODE, -1) == MONOCHROMACY
    }.getOrDefault(false)

    /** Returns true when the system state now matches the request. */
    fun set(context: Context, on: Boolean): Boolean {
        if (!canControl(context)) return false
        return runCatching {
            Settings.Secure.putInt(context.contentResolver, ENABLED, if (on) 1 else 0)
            if (on) Settings.Secure.putInt(context.contentResolver, MODE, MONOCHROMACY)
            isGray(context) == on
        }.getOrDefault(false)
    }

    /** Puts gray back if another app or Settings switched it off while the rule is on. */
    fun reassert(context: Context, wanted: Boolean) {
        if (wanted && canControl(context) && !isGray(context)) set(context, true)
    }

    fun grantCommand(context: Context): String =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    /** Opens the grayscale screen when the system has a deep link for it, else Accessibility. */
    fun openSettings(context: Context) {
        val deepLink = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "android.settings.COLOR_CORRECTION_SETTINGS"
        } else {
            "com.android.settings.ACCESSIBILITY_COLOR_SPACE_SETTINGS"
        }
        val candidates = listOf(
            Intent(deepLink).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        for (intent in candidates) {
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
    }
}
