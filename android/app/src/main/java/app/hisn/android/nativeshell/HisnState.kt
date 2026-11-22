package app.hisn.android.nativeshell

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.hisn.android.DnsVpnService
import app.hisn.android.PolicyStore
import app.hisn.core.AppTimeLimit
import app.hisn.android.R
import app.hisn.core.DailyAppSchedule
import app.hisn.core.DeviceFamily
import app.hisn.core.PolicyChangeError
import app.hisn.core.SiteCheckInput
import app.hisn.core.VisualHealth
import app.hisn.core.WordMatcher

/**
 * What the native screens can do to the real app. Implemented by the activity, which owns the
 * consent dialogs, the VPN permission and the system settings screens.
 */
interface ShellActions {
    fun requestDns()
    fun stopDns()
    fun openAccessibility()
    fun openGrayScreenHelp()

    /** Hisn's saved gray-screen choice, whether or not Android has applied it. */
    fun grayWanted(): Boolean

    /** Saves the choice and applies it when Android allows; false when a lock refuses turning it off. */
    fun chooseGray(on: Boolean): Boolean
    fun grayApplied(): Boolean
    fun grayCanApply(): Boolean
    fun confirmAdult(enable: Boolean, onAccept: () -> Unit)
    fun confirmImages(enable: Boolean, onAccept: () -> Unit)
    fun toast(text: String)

    /** One of [HisnState.PERMISSIONS]; true when Android reports the grant (or the tunnel) live. */
    fun permission(key: String): Boolean
    fun openPermission(key: String)

    /** Shows the lock disclosure, then starts a real protection lock. */
    fun startLock(days: Int, onDone: () -> Unit)
    fun remainingLabel(millis: Long): String
    fun openGuardian()

    /** The phone-maker facts the setup screen shows; every reading is live. */
    fun phone(): PhoneState

    /** Opens the maker's or Android's screen for [key]: battery, appinfo, autostart or vpn. */
    fun openPhoneSetting(key: String)

    /** Opens the uninstall-protection choices (direct build); the shell refreshes after a change. */
    fun openUninstall(onChanged: () -> Unit)

    /** The live uninstall-protection state as one line. */
    fun uninstallSummary(): String

    /** The local personal layer as display text; see [PersonalSummary]. */
    fun personal(): PersonalSummary
    fun savePersonal(name: String, preset: String, reason: String)
    fun clearPersonal()

    fun openIntroduction()
    /** The voluntary breathing pause: nothing is blocked, the person simply asked for a moment. */
    fun openPause()

    /** Explains a hostname decision off the main thread; the callback runs on the main thread. */
    fun checkSite(raw: String, onResult: (SiteCheck) -> Unit)

    /** The installed list generation and the last verification, as display text. */
    fun listUpdateSummary(): String
    fun checkListUpdate(onResult: (String) -> Unit)

    /** Android's file pickers; the import shows a preview before anything is merged. */
    fun exportRules()
    fun importRules()

    /** Shows the route-check disclosure, then asks the running tunnel to check itself. */
    fun confirmNetworkCheck()

    fun pickScheduleApps(keepList: Boolean, selected: Set<String>, onChoose: (Set<String>) -> Unit)

    fun protectionRuntime(): ProtectionRuntime
}

/** Live service facts, kept separate from the user's saved switches. */
data class ProtectionRuntime(
    val dns: DnsVpnService.State = DnsVpnService.State.OFF,
    val dnsRequested: Boolean = false,
    val privateDnsStrict: Boolean = false,
    val dnsAlwaysOn: Boolean? = null,
    val dnsLockdown: Boolean? = null,
    val dnsCheck: DnsVpnService.CheckState = DnsVpnService.CheckState.IDLE,
    val guardExpected: Boolean = false,
    val guardReady: Boolean = false,
    val visual: VisualHealth.State = VisualHealth.State.OFF
) {
    val dnsAttached: Boolean get() = dns == DnsVpnService.State.RUNNING || dns == DnsVpnService.State.DEGRADED
    val dnsReady: Boolean get() = dns == DnsVpnService.State.RUNNING && !privateDnsStrict && dnsLockdown != true
}

/** One hostname decision as display text; [host] is set only when it may be added as a rule. */
data class SiteCheck(val message: String, val host: String? = null, val blocked: Boolean? = null)

/**
 * What this phone's maker does to background apps, as the setup screen shows it.
 *
 * [batteryUnrestricted] and [backgroundRestricted] are live Android readings. The two
 * confirmations are the owner's own ticks for OEM switches Android does not expose; they are
 * saved on this phone and are never reported as protection.
 */
data class PhoneState(
    val maker: String = "",
    val model: String = "",
    val android: String = "",
    val family: DeviceFamily = DeviceFamily.OTHER,
    val batteryUnrestricted: Boolean = false,
    val backgroundRestricted: Boolean? = null,
    val autostartConfirmed: Boolean = false,
    val recentsConfirmed: Boolean = false,
    /** Xiaomi's live pop-up answer; null where the maker does not answer. */
    val popupsAllowed: Boolean? = null,
    val popupsConfirmed: Boolean = false
) {
    /** How many checklist rows, pop-ups included, still need the owner. */
    val open: Int get() = app.hisn.core.OemGuide.open(
        family, batteryUnrestricted, backgroundRestricted, autostartConfirmed, recentsConfirmed,
        popupsAllowed, popupsConfirmed
    )
    /** "Samsung SM-G990B2", or just the maker when the build has no model name. */
    val name: String get() = listOf(maker, model).filter { it.isNotEmpty() }.joinToString(" ")
}

/**
 * The optional personal layer, already turned into display text by the activity. Local only: a
 * name, a reason, a day count, and pause counts per hour of the day (no app, site or date).
 */
data class PersonalSummary(
    val greeting: String = "",
    val reason: String = "",
    val metric: String = "",
    /** The saved raw values, for the editor. */
    val name: String = "",
    val preset: String = "",
    val customReason: String = "",
    val hours: List<Int> = List(24) { 0 },
    val peakStart: Int = -1,
    /** "Day N with Hisn", or zero before the first save. Never reset by a pause or a block. */
    val day: Int = 0,
    val pauses: Int = 0
) {
    val empty: Boolean get() = greeting.isEmpty() && reason.isEmpty() && hours.sum() == 0
}

/**
 * The sample app's state API, backed by the real policy and services.
 *
 * The screens were written against a small set of lists, switches and setters; this class keeps
 * that shape so the ported UI is unchanged, but every read comes from [PolicyStore] or the running
 * services and every write goes through the same validation and protection-lock checks as the
 * classic screens. [refreshAll] is called by the host so external changes (a service dying, an
 * Android setting changed) show up.
 */
class HisnState(private val context: Context, private val actions: ShellActions) {

    /** One switch: reads the live state, and its setter performs the real action. */
    class LayerSwitch internal constructor(
        private val read: () -> Boolean,
        private val write: (Boolean) -> Unit
    ) {
        var on: Boolean by mutableStateOf(read())
            private set

        fun set(value: Boolean) {
            write(value)
            refresh()
        }

        internal fun refresh() {
            on = read()
        }
    }

    private fun store(): PolicyStore? = runCatching { PolicyStore.get(context) }.getOrNull()

    private fun snapshot(): PolicyStore.Snapshot? =
        runCatching { store()?.snapshot() }.getOrNull()

    internal fun locked(): Boolean = runCatching {
        (store()?.remainingMillis() ?: 0L) > 0L
    }.getOrDefault(false)

    private fun refuseLocked() {
        actions.toast(context.getString(R.string.rules_locked))
    }

    /** The real reason a write failed; only a lock refusal says "locked". */
    private fun refuse(failure: Throwable) {
        val reason = (failure as? RuntimeException)?.let { PolicyChangeError.of(it) }
        actions.toast(
            when (reason) {
                PolicyChangeError.TOO_MANY_APPS -> context.resources.getQuantityString(
                    R.plurals.rules_too_many_apps, PolicyStore.MAX_APPS, PolicyStore.MAX_APPS
                )
                PolicyChangeError.INVALID -> context.getString(R.string.rules_invalid)
                PolicyChangeError.COMMITMENT -> context.getString(R.string.rules_locked)
                PolicyChangeError.NEEDS_RULES -> context.getString(R.string.commitment_needs_rules)
                PolicyChangeError.SAVE_FAILED -> context.getString(R.string.rules_save_failed)
                else -> context.getString(R.string.policy_unavailable)
            }
        )
    }

    // Live status the screens show instead of fixed sample text.
    var lockRemainingMillis: Long by mutableStateOf(readRemaining())
        private set
    var lockLabel: String by mutableStateOf("")
        private set
    var hasRestrictions: Boolean by mutableStateOf(false)
        private set
    var permissions: Map<String, Boolean> by mutableStateOf(emptyMap())
        private set
    var runtime: ProtectionRuntime by mutableStateOf(ProtectionRuntime())
        private set
    val dnsState get() = runtime.dns
    val check get() = runtime.dnsCheck
    /** App protection turned off inside Hisn while Android's accessibility switch stays on. */
    var guardPaused: Boolean by mutableStateOf(false)
        private set
    val guardReady get() = runtime.guardReady && permissions["accessibility"] == true && !guardPaused
    var dnsDomains: Int by mutableStateOf(DnsVpnService.domains)
        private set

    var personal: PersonalSummary by mutableStateOf(PersonalSummary())
        private set
    /** The phone's maker, family and live background readings; see [PhoneState]. */
    var phone: PhoneState by mutableStateOf(PhoneState())
        private set
    var blockedLookups: Long by mutableStateOf(DnsVpnService.blocked.get())
        private set
    var uninstallSummary: String by mutableStateOf("")
        private set

    private fun readRemaining(): Long = runCatching { store()?.remainingMillis() ?: 0L }.getOrDefault(0L)

    fun startLock(days: Int) = actions.startLock(days) { refreshAll() }
    fun openPermission(key: String) = actions.openPermission(key)
    fun openGuardian() = actions.openGuardian()
    fun openUninstall() = actions.openUninstall { refreshAll() }

    fun openIntroduction() = actions.openIntroduction()
    fun openPause() = actions.openPause()
    fun openPhoneSetting(key: String) = actions.openPhoneSetting(key)

    /**
     * Saves the owner's tick for an OEM switch Android does not expose. It is a local note, not
     * a permission check: the phone screen still shows the live battery and background readings.
     */
    fun confirmPhoneStep(key: String, done: Boolean) {
        val name = when (key) {
            "autostart" -> KEY_AUTOSTART
            "recents" -> KEY_RECENTS
            "popups" -> KEY_POPUPS
            else -> return
        }
        context.getSharedPreferences("hisn-native", Context.MODE_PRIVATE)
            .edit().putBoolean(name, done).apply()
        refreshAll()
    }

    fun checkSite(raw: String, onResult: (SiteCheck) -> Unit) = actions.checkSite(raw, onResult)
    fun listUpdateSummary(): String = runCatching { actions.listUpdateSummary() }.getOrDefault("")
    fun checkListUpdate(onResult: (String) -> Unit) = actions.checkListUpdate(onResult)
    fun exportRules() = actions.exportRules()
    fun importRules() = actions.importRules()

    fun savePersonal(name: String, preset: String, reason: String) {
        actions.savePersonal(name, preset, reason)
        refreshAll()
    }

    fun clearPersonal() {
        actions.clearPersonal()
        refreshAll()
    }
    fun confirmNetworkCheck() = actions.confirmNetworkCheck()

    fun fixDns() {
        when {
            runtime.dnsAttached && runtime.dnsLockdown == true -> openPhoneSetting("vpn")
            runtime.dnsAttached && runtime.privateDnsStrict -> openPermission("privateDns")
            runtime.dnsAttached -> if (check != DnsVpnService.CheckState.CHECKING) confirmNetworkCheck()
            dnsState != DnsVpnService.State.STARTING -> dns.set(true)
        }
    }

    private fun refreshStatus() {
        lockRemainingMillis = readRemaining()
        lockLabel = if (lockRemainingMillis > 0L) actions.remainingLabel(lockRemainingMillis) else ""
        hasRestrictions = snapshot()?.hasConfiguredRestrictions() == true
        val next = PERMISSIONS.associateWith { key -> runCatching { actions.permission(key) }.getOrDefault(false) }
        if (next != permissions) permissions = next
        runtime = runCatching { actions.protectionRuntime() }.getOrDefault(ProtectionRuntime())
        guardPaused = app.hisn.android.GuardPause.paused(context)
        dnsDomains = DnsVpnService.domains
        blockedLookups = DnsVpnService.blocked.get()
        val summary = runCatching { actions.personal() }.getOrDefault(PersonalSummary())
        if (summary != personal) personal = summary
        val phoneSummary = runCatching { actions.phone() }.getOrNull()
        if (phoneSummary != null) {
            // The two OEM switches cannot be read, so the owner's saved tick is the only source.
            val saved = context.getSharedPreferences("hisn-native", Context.MODE_PRIVATE)
            val merged = phoneSummary.copy(
                autostartConfirmed = saved.getBoolean(KEY_AUTOSTART, false),
                recentsConfirmed = saved.getBoolean(KEY_RECENTS, false),
                popupsConfirmed = saved.getBoolean(KEY_POPUPS, false)
            )
            if (merged != phone) phone = merged
        }
    }

    init { refreshStatus() }

    // Protection layers.
    val dns = LayerSwitch(
        read = { runtime.dnsRequested || runtime.dnsAttached || dnsState == DnsVpnService.State.STARTING },
        write = { on -> if (on) actions.requestDns() else actions.stopDns() }
    )

    // Off pauses App protection inside Hisn; Android's accessibility switch is left alone, so
    // nobody is sent to the phone's settings to turn it off. On resumes at once when Android's
    // switch is still on, and opens the phone's settings only when it is not.
    val apps = LayerSwitch(
        read = { permissions["accessibility"] == true && !guardPaused },
        write = { on ->
            if (!on) {
                if (!app.hisn.android.GuardPause.set(context, true)) refuseLocked()
            } else {
                app.hisn.android.GuardPause.set(context, false)
                if (permissions["accessibility"] != true) actions.openAccessibility()
            }
            refreshAll()
        }
    )

    val tags = LayerSwitch(
        read = { snapshot()?.adultWordsEnabled == true },
        write = { on ->
            if (locked()) refuseLocked() else actions.confirmAdult(on) { writeAdult(on) }
        }
    )

    val images = LayerSwitch(
        read = { snapshot()?.visualEnabled == true },
        write = { on ->
            if (locked()) refuseLocked() else actions.confirmImages(on) { writeImages(on) }
        }
    )

    val shortVideos = LayerSwitch(
        read = { snapshot()?.shortVideoEnabled == true },
        write = { on ->
            if (locked()) refuseLocked() else {
                runCatching { store()?.updateShortVideo(on) }.onFailure { refuse(it) }
                refreshAll()
            }
        }
    )

    /** Text and image checks in every app, not only the chosen ones. Saved with the rules. */
    val scanAll = LayerSwitch(
        read = { app.hisn.core.ScanScope.all(snapshot()?.scannedApps) },
        write = { on ->
            // Turning it on only adds coverage, so a lock allows it; turning it off is refused.
            if (!on && locked()) refuseLocked() else {
                editRules { store, rules ->
                    if (app.hisn.core.ScanScope.all(rules.scannedApps) == on) return@editRules false
                    store.update(
                        store.domainText(), store.wordText(), rules.blockedApps,
                        app.hisn.core.ScanScope.with(rules.scannedApps, on), rules.adultWordsEnabled
                    )
                    true
                }
            }
        }
    )

    /**
     * Adds the phone's browsers to the apps the word and image checks read: where adult pages
     * open, at a fraction of the battery of "every app". Only adds, so a lock allows it.
     */
    fun scanBrowsers(): Boolean {
        val browsers = app.hisn.android.ChosenApps.browsers(context).map { it.packageName }.toSet()
        if (browsers.isEmpty()) return false
        return editRules { store, rules ->
            if (rules.scannedApps.containsAll(browsers)) return@editRules false
            store.update(store.domainText(), store.wordText(), rules.blockedApps, rules.scannedApps + browsers,
                rules.adultWordsEnabled)
            true
        }
    }

    /** Apps chosen by name for the checks, not counting the every-app scope. */
    var scannedCount: Int by mutableStateOf(0)
        private set

    /** The saved choice. The guard applies it and puts it back; [grayApplied] says if it is on now. */
    val gray = LayerSwitch(
        read = { runCatching { actions.grayWanted() }.getOrDefault(false) },
        write = { on ->
            if (!actions.chooseGray(on)) refuseLocked()
            refreshAll()
        }
    )
    var grayApplied: Boolean by mutableStateOf(false)
        private set
    var grayCanApply: Boolean by mutableStateOf(false)
        private set

    // Custom rules.
    var appSchedule: DailyAppSchedule by mutableStateOf(snapshot()?.schedule ?: DailyAppSchedule.disabled())
        private set

    fun saveSchedule(expected: DailyAppSchedule, edited: DailyAppSchedule): Boolean = editRules { store, rules ->
        if (rules.schedule != expected) {
            actions.toast(context.getString(R.string.schedule_changed))
            return@editRules false
        }
        if (rules.schedule != edited) store.updateSchedule(edited)
        true
    }

    fun pickScheduleApps(keepList: Boolean, selected: Set<String>, onChoose: (Set<String>) -> Unit) =
        actions.pickScheduleApps(keepList, selected, onChoose)

    val limit = LayerSwitch(
        read = { snapshot()?.timeLimits?.configured() == true },
        write = { on -> if (!on) writeLimits(AppTimeLimit.none()) }
    )

    // Appearance.
    private val prefs = context.getSharedPreferences("hisn-native", Context.MODE_PRIVATE)
    val systemColors = LayerSwitch(
        read = { prefs.getBoolean("ui.systemColors", false) },
        write = { on -> prefs.edit().putBoolean("ui.systemColors", on).apply() }
    )
    var themeMode: String by mutableStateOf(prefs.getString("ui.theme", "system") ?: "system")
        private set

    fun updateThemeMode(mode: String) {
        if (themeMode == mode) return
        themeMode = mode
        prefs.edit().putString("ui.theme", mode).apply()
    }

    /** Daily limit per app, in minutes, keyed by package name. */
    val appLimits = mutableStateMapOf<String, Int>().apply { putAll(savedLimits()) }

    private fun savedLimits(rules: PolicyStore.Snapshot? = snapshot()): Map<String, Int> {
        val limits = rules?.timeLimits ?: return emptyMap()
        return limits.apps().mapNotNull { pkg ->
            limits.minutesFor(pkg)?.let { pkg to it }
        }.toMap()
    }

    fun appLimit(packageName: String): Int? = appLimits[packageName]

    fun setAppLimit(packageName: String, minutes: Int): Boolean = editRules { store, rules ->
        val clamped = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        if (rules.timeLimits.minutesFor(packageName) == clamped) return@editRules false
        store.updateTimeLimits(AppTimeLimit(savedLimits(rules) + (packageName to clamped)))
        true
    }

    fun removeAppLimit(packageName: String): Boolean = editRules { store, rules ->
        if (rules.timeLimits.minutesFor(packageName) == null) return@editRules false
        store.updateTimeLimits(AppTimeLimit(savedLimits(rules) - packageName))
        true
    }

    // Custom lists.
    val domains = mutableStateListOf<String>().apply { addAll(savedDomains()) }
    val words = mutableStateListOf<String>().apply { addAll(savedWords()) }
    val blockedApps = mutableStateListOf<String>().apply { addAll(savedApps()) }

    private fun savedDomains(): List<String> = snapshot()?.domains?.blockedRules()?.sorted() ?: emptyList()
    private fun savedWords(): List<String> = snapshot()?.words?.configuredRules()?.sorted() ?: emptyList()
    private fun savedApps(): List<String> = snapshot()?.blockedApps?.sorted() ?: emptyList()

    fun addDomain(input: String): Boolean {
        val canonical = runCatching { SiteCheckInput.ruleHost(input) }.getOrElse { refuse(it); return false }
        return setDomain(canonical, blocked = true)
    }

    fun removeDomain(domain: String): Boolean = setDomain(domain, blocked = false)

    private fun setDomain(domain: String, blocked: Boolean): Boolean = editRules { store, rules ->
        val current = rules.domains.blockedRules()
        if ((domain in current) == blocked) return@editRules false
        val next = if (blocked) current + domain else current - domain
        store.update(next.joinToString("\n"), store.wordText(), rules.blockedApps, rules.scannedApps)
        true
    }

    fun addWord(input: String): Boolean {
        val canonical = runCatching { WordMatcher.canonicalRule(input.trim()) }.getOrElse { refuse(it); return false }
        return setWord(canonical, blocked = true)
    }

    fun removeWord(word: String): Boolean = setWord(word, blocked = false)

    private fun setWord(word: String, blocked: Boolean): Boolean = editRules { store, rules ->
        val current = rules.words.configuredRules()
        if ((word in current) == blocked) return@editRules false
        val next = if (blocked) current + word else current - word
        store.update(store.domainText(), next.joinToString("\n"), rules.blockedApps, rules.scannedApps)
        true
    }

    fun isAppBlocked(packageName: String): Boolean = blockedApps.contains(packageName)

    fun setAppBlocked(packageName: String, blocked: Boolean) {
        editRules { store, rules ->
            val current = rules.blockedApps
            if ((packageName in current) == blocked) return@editRules false
            val next = if (blocked) current + packageName else current - packageName
            store.update(store.domainText(), store.wordText(), next, rules.scannedApps)
            true
        }
    }

    fun refreshAll() {
        refreshStatus()
        uninstallSummary = runCatching { actions.uninstallSummary() }.getOrDefault("")
        scanAll.refresh()
        grayApplied = runCatching { actions.grayApplied() }.getOrDefault(false)
        grayCanApply = runCatching { actions.grayCanApply() }.getOrDefault(false)
        scannedCount = app.hisn.core.ScanScope.chosen(snapshot()?.scannedApps)
        dns.refresh()
        apps.refresh()
        tags.refresh()
        images.refresh()
        shortVideos.refresh()
        gray.refresh()
        appSchedule = snapshot()?.schedule ?: DailyAppSchedule.disabled()
        limit.refresh()
        syncMap(appLimits, savedLimits())
        sync(domains, savedDomains())
        sync(words, savedWords())
        sync(blockedApps, savedApps())
    }

    // ------------------------------------------------------------------ writes

    private fun writeAdult(on: Boolean) {
        editRules { store, rules ->
            if (rules.adultWordsEnabled == on) return@editRules false
            store.update(store.domainText(), store.wordText(), rules.blockedApps,
                rules.scannedApps, on)
            true
        }
    }

    private fun writeImages(on: Boolean) {
        runCatching { store()?.updateVisual(on) }.onFailure { refuse(it) }
        refreshAll()
    }

    private fun writeLimits(limits: AppTimeLimit) {
        if (locked()) { refuseLocked(); return }
        runCatching { store()?.updateTimeLimits(limits) }.onFailure { refuse(it) }
        refreshAll()
    }

    /** Applies a delta to the store's latest rules and only displays what it saved. */
    private fun editRules(change: (PolicyStore, PolicyStore.Snapshot) -> Boolean): Boolean {
        val store = store()
        if (store == null) {
            actions.toast(context.getString(R.string.policy_unavailable))
            refreshAll()
            return false
        }
        val changed = runCatching {
            synchronized(store) { change(store, store.snapshot()) }
        }.getOrElse { failure ->
            refuse(failure)
            false
        }
        refreshAll()
        return changed
    }

    private fun <T> sync(target: MutableCollection<T>, source: Collection<T>) {
        if (target == source) return
        target.clear()
        target.addAll(source)
    }

    private fun <K, V> syncMap(target: MutableMap<K, V>, source: Map<K, V>) {
        if (target == source) return
        target.clear()
        target.putAll(source)
    }

    companion object {
        const val MIN_MINUTES = 5
        const val MAX_MINUTES = 480
        const val DEFAULT_MINUTES = 30
        private const val KEY_AUTOSTART = "phone.autostart"
        private const val KEY_RECENTS = "phone.recents"
        private const val KEY_POPUPS = "phone.popups"
        val PERMISSIONS = listOf("accessibility", "dns", "usage", "overlay", "notifications", "battery")
    }
}
