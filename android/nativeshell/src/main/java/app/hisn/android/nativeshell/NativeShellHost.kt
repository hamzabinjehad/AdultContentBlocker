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
    val layers: String,
    val locked: String,
    val unsupported: String
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
    val key: String,
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
    val locked: Boolean,
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
    val ok: Boolean = true,
    val refresh: Boolean = true
)

/** A consent sheet for one layer. The shell shows it before any policy write. */
data class ShellConsent(
    val title: String,
    val body: String,
    val acceptLabel: String,
    val cancelLabel: String
)

data class ShellRulesEditor(
    val kind: String,
    val title: String,
    val text: String,
    val hint: String,
    val note: String,
    val saveLabel: String,
    val cancelLabel: String,
    val lockedNote: String,
    val locked: Boolean
)

data class ShellRulesPreview(
    val summary: String,
    val error: String? = null
)

interface NativeShellHost {
    fun protection(): ShellProtection
    fun rules(): ShellRules
    fun settings(): ShellSettings

    /** Starts the existing route check. Returns a user-facing message. */
    fun runCheck(): ShellActionResult

    /** The consent text for enabling/disabling a built-in layer, or null when unsupported. */
    fun layerConsent(key: String): ShellConsent?

    /** Applies a built-in layer change after consent. Never bypasses the protection lock. */
    fun setLayer(key: String, enabled: Boolean): ShellActionResult

    /** The real saved text and labels for the domains or words editor. */
    fun rulesEditor(kind: String): ShellRulesEditor

    /** Live counts for the editor; does not write. */
    fun previewRules(kind: String, text: String): ShellRulesPreview

    /** Saves the canonical accepted set through PolicyStore. */
    fun saveRules(kind: String, text: String): ShellActionResult

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
