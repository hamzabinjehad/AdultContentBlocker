package app.hisn.android.nativeshell

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Records what the screens ask the activity to do, without opening system dialogs, starting the
 * VPN or touching the guard. Reads report "not granted", as on a fresh install.
 */
class FakeShellActions : ShellActions {
    val calls = mutableListOf<String>()

    private fun record(name: String) { calls += name }

    override fun requestDns() = record("requestDns")
    override fun stopDns() = record("stopDns")
    override fun openAccessibility() = record("openAccessibility")
    override fun openGrayScreenHelp() = record("openGrayScreenHelp")
    override fun grayWanted() = false
    override fun chooseGray(on: Boolean): Boolean { record("chooseGray:$on"); return true }
    override fun grayApplied() = false
    override fun grayCanApply() = false
    override fun confirmAdult(enable: Boolean, onAccept: () -> Unit) = record("confirmAdult:$enable")
    override fun confirmImages(enable: Boolean, onAccept: () -> Unit) = record("confirmImages:$enable")
    override fun toast(text: String) = record("toast:$text")
    override fun permission(key: String) = key in grantedPermissions
    override fun openPermission(key: String) = record("openPermission:$key")
    override fun startLock(days: Int, onDone: () -> Unit) = record("startLock:$days")
    override fun remainingLabel(millis: Long) = "${millis / 3_600_000} hours"
    override fun openGuardian() = record("openGuardian")
    override fun openUninstall(onChanged: () -> Unit) = record("openUninstall")
    override fun uninstallSummary() = "Off. Android can uninstall Hisn."
    /** A phone with one measured gap, so the phone screen has something to show in tests. */
    var phoneState = PhoneState(
        maker = "Samsung",
        model = "SM-G990B2",
        android = "Android 16 · API 36",
        family = app.hisn.core.DeviceFamily.SAMSUNG,
        batteryUnrestricted = false,
        backgroundRestricted = null
    )
    override fun phone() = phoneState

    /**
     * Unrot's setup checklist replaces Today's warning card while steps are left. Tests about a
     * running problem finish the steps first, as a real owner would have.
     */
    fun finishUnrotSetup() {
        grantedPermissions += listOf("accessibility", "usage")
        phoneState = phoneState.copy(batteryUnrestricted = true)
    }
    override fun openPhoneSetting(key: String) = record("openPhoneSetting:$key")
    var personalSummary = PersonalSummary()
    override fun personal() = personalSummary
    override fun savePersonal(name: String, preset: String, reason: String) = record("savePersonal")
    override fun clearPersonal() = record("clearPersonal")
    override fun openIntroduction() = record("openIntroduction")
    override fun openPause() = record("openPause")
    override fun checkSite(raw: String, onResult: (SiteCheck) -> Unit) {
        record("checkSite:$raw")
        onResult(SiteCheck("checked $raw"))
    }
    override fun listUpdateSummary() = "Bundled list"
    override fun checkListUpdate(onResult: (String) -> Unit) {
        record("checkListUpdate")
        onResult("up to date")
    }
    override fun exportRules() = record("exportRules")
    override fun importRules() = record("importRules")
    override fun confirmNetworkCheck() = record("confirmNetworkCheck")
    var liveProtection = ProtectionRuntime()
    val grantedPermissions = mutableSetOf<String>()
    override fun protectionRuntime() = liveProtection
    var scheduleSelection: Set<String>? = null
    override fun pickScheduleApps(keepList: Boolean, selected: Set<String>, onChoose: (Set<String>) -> Unit) {
        record("pickScheduleApps:$keepList")
        scheduleSelection?.let(onChoose)
    }
}

/** Shows the real shell over the app's real (test-device) policy store with [actions]. */
fun ComposeContentTestRule.showShell(
    actions: FakeShellActions = FakeShellActions(),
    fontScale: Float = 1f,
    rtl: Boolean = false,
    tab: Int = 0,
    detail: String? = null,
    tour: Boolean = false
): FakeShellActions {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    // The wins cards read the emulator's real usage; screens under test use the fake state only.
    Wins.disabled = true
    Spread.disabled = true
    // The first-visit tour would cover Today; only its own test asks for it.
    Tour.reset(context, done = !tour)
    setContent {
        val base = LocalDensity.current
        // One state object for the whole test: rebuilding it in the content lambda made every
        // recomposition construct a fresh set of Compose states (and looped in phone tests).
        val state = androidx.compose.runtime.remember { HisnState(context, actions) }
        CompositionLocalProvider(
            LocalDensity provides Density(base.density, fontScale),
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
            NativeShell(state = state, initialTab = tab, initialDetail = detail)
        }
    }
    waitForIdle()
    return actions
}

/** What a screen reader would announce for a node: its text, else its content description. */
fun SemanticsNode.spokenLabel(): String {
    val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    val described = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
    return (text + " " + described).trim()
}

/** True when the node sits inside a horizontally scrolling container (chips, carousels). */
fun SemanticsNode.insideHorizontalScroll(): Boolean {
    var node: SemanticsNode? = parent
    while (node != null) {
        if (node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null) return true
        node = node.parent
    }
    return false
}


/*
 * Where the rules and settings live in each edition. Hisn: three tabs (Protection, Rules,
 * Settings). Unrot: four tabs (Today, Shield, Progress, You); the full rules list is the
 * "All rules" page behind Shield.
 */
internal const val RulesTab = 1
internal val RulesDetail: String? get() = if (unrotEdition) "rules" else null
internal val SettingsTab: Int get() = if (unrotEdition) 3 else 2
internal val HomeTabLabel: String get() = if (unrotEdition) "Home" else "Protection"
internal val SettingsTabLabel: String get() = if (unrotEdition) "You" else "Settings"
