package app.hisn.android.nativeshell

/**
 * The contract between the Compose shell and the real Hisn app.
 *
 * The shell module is presentation-only: it cannot see `PolicyStore`, the VPN service or the
 * policy lock. The debug app implements this interface in the same package and installs it into
 * [NativeShellBridge] before the activity starts. Every string is already localized by the host,
 * so the shell does not keep a second set of English labels.
 */
data class ShellLayer(
    val key: String,
    val label: String,
    val detail: String,
    val state: String,
    val on: Boolean
)

data class ShellIssue(
    val message: String
)

data class ShellLabels(
    val appName: String,
    val tagline: String,
    val protection: String,
    val rules: String,
    val settings: String,
    val runCheck: String,
    val openClassic: String,
    val live: String,
    val liveBody: String,
    val attention: String,
    val layers: String
)

data class ShellProtection(
    val status: String,
    val title: String,
    val body: String,
    val layers: List<ShellLayer>,
    val issues: List<ShellIssue>,
    val domains: Int,
    val words: Int,
    val apps: Int,
    val dnsState: String,
    val guardState: String,
    val checkedAt: String?,
    val canCheck: Boolean,
    val labels: ShellLabels
)

data class ShellRule(
    val label: String,
    val detail: String,
    val state: String,
    val on: Boolean
)

data class ShellRules(
    val builtInTitle: String,
    val customTitle: String,
    val note: String,
    val builtIn: List<ShellRule>,
    val custom: List<ShellRule>,
    val labels: ShellLabels
)

data class ShellSetting(
    val label: String,
    val value: String,
    val detail: String
)

data class ShellSettings(
    val sections: List<ShellSetting>,
    val labels: ShellLabels
)

/** A host message after an action. `refresh` asks the shell to poll immediately. */
data class ShellActionResult(
    val message: String,
    val refresh: Boolean = true
)

interface NativeShellHost {
    fun protection(): ShellProtection
    fun rules(): ShellRules
    fun settings(): ShellSettings

    /** Starts the existing route check. Returns a user-facing message. */
    fun runCheck(): ShellActionResult

    /** Opens the classic Hisn activity for the changes the shell does not own yet. */
    fun openClassic()
}

/**
 * Installed by the debug app's `Application` before `ShellActivity` is created. The shell shows a
 * clear unavailable state if this is null; it never falls back to sample protection data.
 */
object NativeShellBridge {
    @Volatile
    var host: NativeShellHost? = null
}
