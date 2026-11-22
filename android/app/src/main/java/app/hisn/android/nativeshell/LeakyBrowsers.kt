package app.hisn.android.nativeshell

import android.content.Context

/**
 * Browsers the website filter cannot see into: Opera Mini and UC Browser load pages through their
 * makers' servers to save data, Puffin renders pages in the cloud, and Opera has a built-in VPN.
 * Any of them can open an adult site that the filter would block in Chrome. Blocking the app, or
 * "only browsers Hisn controls", closes the gap; the words and image checks still read their
 * pages while app protection is on.
 */
internal object LeakyBrowsers {
    val PACKAGES = listOf(
        "com.opera.mini.native", "com.opera.mini.native.beta", "com.opera.mini.android",
        "com.opera.browser", "com.opera.browser.beta", "com.opera.gx",
        "com.UCMobile.intl", "com.uc.browser.en", "com.ucturbo",
        "com.cloudmosa.puffinFree", "com.cloudmosa.puffin", "com.cloudmosa.puffinTV"
    )

    fun installed(context: Context): List<String> = PACKAGES.filter { pkg ->
        runCatching { context.packageManager.getApplicationInfo(pkg, 0).enabled }.getOrDefault(false)
    }
}
