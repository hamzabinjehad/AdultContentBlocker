package app.hisn.android.nativeshell

import android.graphics.Bitmap

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
    val unsupported: String,
    val dnsStart: String,
    val settingsControls: String,
    val settingsAndroid: String
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

data class ShellUninstall(
    val title: String,
    val body: String,
    val status: String,
    val commandLabel: String,
    val command: String,
    val copyLabel: String,
    val copyMessage: String,
    val adminLabel: String,
    val managed: Boolean,
    val managedNote: String
)

data class ShellToggle(
    val key: String,
    val label: String,
    val detail: String,
    val on: Boolean,
    val enabled: Boolean
)

data class ShellAction(
    val key: String,
    val label: String,
    val detail: String
)

data class ShellSettings(
    val sections: List<ShellSetting>,
    val labels: ShellLabels,
    val toggles: List<ShellToggle> = emptyList(),
    val actions: List<ShellAction> = emptyList()
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

data class ShellScheduleDay(
    val bit: Int,
    val label: String
)

data class ShellScheduleEditor(
    val enabled: Boolean,
    val startMinute: Int,
    val endMinute: Int,
    val days: Int,
    val allApps: Boolean,
    val selected: Set<String>,
    val apps: List<ShellApp>,
    val title: String,
    val help: String,
    val enableLabel: String,
    val startTemplate: String,
    val endTemplate: String,
    val daysTitle: String,
    val daysHelp: String,
    val modeAllLabel: String,
    val modeListLabel: String,
    val modeListHelp: String,
    val modeAllHelp: String,
    val chooseAppsLabel: String,
    val keepAppsLabel: String,
    val useAppsLabel: String,
    val searchHint: String,
    val emptyLabel: String,
    val privateNote: String,
    val max: Int,
    val saveLabel: String,
    val cancelLabel: String,
    val lockedNote: String,
    val locked: Boolean,
    val invalidTime: String,
    val chooseAppsError: String,
    val keepAppsError: String,
    val daysError: String,
    val invalid: String,
    val dayLabels: List<ShellScheduleDay>
)

data class ShellLimitChoice(
    val minutes: Int,
    val label: String
)

data class ShellLimitsEditor(
    val title: String,
    val help: String,
    val searchHint: String,
    val accessTitle: String,
    val accessDetail: String,
    val accessUnavailable: String,
    val saveLabel: String,
    val cancelLabel: String,
    val lockedNote: String,
    val locked: Boolean,
    val max: Int,
    val defaultMinutes: Int,
    val choices: List<ShellLimitChoice>,
    val selected: Map<String, Int>,
    val apps: List<ShellApp>
)

data class ShellApp(
    val packageName: String,
    val label: String,
    val icon: Bitmap?
)

data class ShellLockOption(
    val days: Int,
    val label: String
)

data class ShellLockState(
    val active: Boolean,
    val remaining: String,
    val title: String,
    val choosePeriod: String,
    val disclosure: String,
    val startLabel: String,
    val cancelLabel: String,
    val needsRules: String,
    val lockedNote: String,
    val canStart: Boolean,
    val options: List<ShellLockOption>
)

data class ShellAppPicker(
    val kind: String,
    val title: String,
    val note: String,
    val searchHint: String,
    val emptyLabel: String,
    val saveLabel: String,
    val cancelLabel: String,
    val lockedNote: String,
    val locked: Boolean,
    val max: Int,
    val selected: Set<String>,
    val apps: List<ShellApp>
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

    /** The real daily schedule, installed apps and the day/time/mode labels. */
    fun scheduleEditor(): ShellScheduleEditor

    /** Saves the enabled window, day mask, mode and installed-app selection. */
    fun saveSchedule(
        enabled: Boolean,
        startMinute: Int,
        endMinute: Int,
        days: Int,
        allApps: Boolean,
        apps: Set<String>
    ): ShellActionResult

    /** The real daily-limit selection, Usage access status and per-app choices. */
    fun limitsEditor(): ShellLimitsEditor

    /** Saves only installed rows; saved limits for uninstalled apps are preserved. */
    fun saveLimits(limits: Map<String, Int>): ShellActionResult

    /** Opens Android's Usage access screen; false when it cannot be opened. */
    fun openUsageAccess(): Boolean

    /** The real protection-lock state and disclosure. */
    fun lockState(): ShellLockState

    /** Starts or extends the lock; the PolicyStore decides whether the period is allowed. */
    fun startLock(days: Int): ShellActionResult

    /** Installed apps and the saved selection for a blocked or inspected-app picker. */
    fun appPicker(kind: String): ShellAppPicker

    /** Saves only installed rows; saved packages that are uninstalled are preserved. */
    fun saveAppPicker(kind: String, selected: Set<String>): ShellActionResult

    /** The real platform uninstall-protection status and provisioning command. */
    fun uninstallState(): ShellUninstall

    /** Copies the device-owner provisioning command; false when it could not be copied. */
    fun copyUninstallCommand(): Boolean

    /** Opens Android's device-admin setup screen. */
    fun openAdminSetup(): Boolean

    /** Saves a real settings switch such as SafeSearch. */
    fun setToggle(key: String, enabled: Boolean): ShellActionResult

    /** Opens an Android settings surface; returns false when it cannot be opened. */
    fun runSettingAction(key: String): Boolean

    /** The real DNS disclosure; the shell shows it before requesting VPN approval. */
    fun dnsConsent(): ShellConsent

    /** The system VPN consent intent, or null when Android already holds the approval. */
    fun vpnConsentIntent(): android.content.Intent?

    /** Starts the existing DNS foreground service after consent. */
    fun startDns(): ShellActionResult

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
