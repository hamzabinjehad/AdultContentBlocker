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
import app.hisn.core.PolicyChangeError

/**
 * What the native screens can do to the real app. Implemented by the activity, which owns the
 * consent dialogs, the VPN permission and the system settings screens.
 */
interface ShellActions {
    fun requestDns()
    fun stopDns()
    fun openAccessibility()
    fun openGrayScreenHelp()
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

    /** Opens the classic screen at a page (1 rules, 2 settings) for tools not yet ported. */
    fun openClassic(page: Int)

    /** The local personal layer as display text; see [PersonalSummary]. */
    fun personal(): PersonalSummary
    fun savePersonal(name: String, preset: String, reason: String)
    fun clearPersonal()

    fun openIntroduction()

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
}

/** One hostname decision as display text; [host] is set only when it may be added as a rule. */
data class SiteCheck(val message: String, val host: String? = null)

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
    val peakStart: Int = -1
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

    /** Same check the classic activity uses, without reaching into its package-private helper. */
    private fun accessibilityGranted(): Boolean = runCatching {
        val manager = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        if (manager != null) {
            for (info in manager.getEnabledAccessibilityServiceList(
                    android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                val service = info.resolveInfo?.serviceInfo ?: continue
                if (context.packageName == service.packageName &&
                    app.hisn.android.TextGuardService::class.java.name == service.name) return true
            }
        }
        val saved = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        saved?.contains("${context.packageName}/${app.hisn.android.TextGuardService::class.java.name}") == true
    }.getOrDefault(false)

    private fun snapshot(): PolicyStore.Snapshot? =
        runCatching { store()?.snapshot() }.getOrNull()

    private fun locked(): Boolean = runCatching {
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
    var dnsState: DnsVpnService.State by mutableStateOf(DnsVpnService.state)
        private set
    var dnsDomains: Int by mutableStateOf(DnsVpnService.domains)
        private set
    var guardConnected: Boolean by mutableStateOf(app.hisn.android.TextGuardService.connected)
        private set
    var check: DnsVpnService.CheckState by mutableStateOf(DnsVpnService.connectionCheck.state)
        private set

    var personal: PersonalSummary by mutableStateOf(PersonalSummary())
        private set
    var blockedLookups: Long by mutableStateOf(DnsVpnService.blocked.get())
        private set

    private fun readRemaining(): Long = runCatching { store()?.remainingMillis() ?: 0L }.getOrDefault(0L)

    fun startLock(days: Int) = actions.startLock(days) { refreshAll() }
    fun openPermission(key: String) = actions.openPermission(key)
    fun openGuardian() = actions.openGuardian()
    fun openClassic(page: Int) = actions.openClassic(page)

    fun openIntroduction() = actions.openIntroduction()
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

    private fun refreshStatus() {
        lockRemainingMillis = readRemaining()
        lockLabel = if (lockRemainingMillis > 0L) actions.remainingLabel(lockRemainingMillis) else ""
        hasRestrictions = snapshot()?.hasConfiguredRestrictions() == true
        val next = PERMISSIONS.associateWith { key -> runCatching { actions.permission(key) }.getOrDefault(false) }
        if (next != permissions) permissions = next
        dnsState = DnsVpnService.state
        dnsDomains = DnsVpnService.domains
        guardConnected = app.hisn.android.TextGuardService.connected
        check = DnsVpnService.connectionCheck.state
        blockedLookups = DnsVpnService.blocked.get()
        val summary = runCatching { actions.personal() }.getOrDefault(PersonalSummary())
        if (summary != personal) personal = summary
    }

    init { refreshStatus() }

    // Protection layers.
    val dns = LayerSwitch(
        read = { DnsVpnService.state != DnsVpnService.State.OFF },
        write = { on -> if (on) actions.requestDns() else actions.stopDns() }
    )

    val apps = LayerSwitch(
        read = { accessibilityGranted() },
        write = { actions.openAccessibility() }
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

    val gray = LayerSwitch(
        read = { ScreenGray.isGray(context) },
        write = { on ->
            if (ScreenGray.set(context, on)) {
                refreshAll()
            } else {
                actions.openGrayScreenHelp()
            }
        }
    )

    // Custom rules.
    val schedule = LayerSwitch(
        read = { snapshot()?.schedule?.enabled == true },
        write = { on ->
            val current = snapshot()?.schedule ?: return@LayerSwitch
            writeSchedule(
                DailyAppSchedule(
                    on, current.startMinute, current.endMinute, current.apps,
                    current.allApps, current.days
                )
            )
        }
    )

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

    /** Monday-first day indices, as the sample's chips label them. */
    var scheduleDays: Set<Int> by mutableStateOf(savedDays())
        private set

    private fun savedDays(): Set<Int> {
        val mask = snapshot()?.schedule?.days ?: DailyAppSchedule.ALL_DAYS
        return (0..6).filter { (mask and (1 shl it)) != 0 }.toSet()
    }

    fun updateScheduleDays(days: Set<Int>) {
        if (scheduleDays == days) return
        if (locked()) { refuseLocked(); return }
        val mask = days.fold(0) { acc, day -> acc or (1 shl day.coerceIn(0, 6)) }
        if (mask == 0) return
        val current = snapshot()?.schedule ?: return
        writeSchedule(
            DailyAppSchedule(
                current.enabled, current.startMinute, current.endMinute, current.apps,
                current.allApps, mask
            )
        )
        scheduleDays = days
    }

    /** Daily limit per app, in minutes, keyed by package name. */
    val appLimits = mutableStateMapOf<String, Int>().apply { putAll(savedLimits()) }

    private fun savedLimits(): Map<String, Int> {
        val limits = snapshot()?.timeLimits ?: return emptyMap()
        return limits.apps().mapNotNull { pkg ->
            limits.minutesFor(pkg)?.let { pkg to it }
        }.toMap()
    }

    fun appLimit(packageName: String): Int? = appLimits[packageName]

    fun setAppLimit(packageName: String, minutes: Int) {
        if (locked()) { refuseLocked(); return }
        val clamped = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        if (appLimits[packageName] == clamped) return
        val next = savedLimits().toMutableMap()
        next[packageName] = clamped
        writeLimits(AppTimeLimit(next))
        appLimits[packageName] = clamped
        limit.refresh()
    }

    fun removeAppLimit(packageName: String) {
        if (locked()) { refuseLocked(); return }
        val next = savedLimits().toMutableMap()
        next.remove(packageName)
        writeLimits(AppTimeLimit(next))
        appLimits.remove(packageName)
        limit.refresh()
    }

    // Custom lists.
    val domains = mutableStateListOf<String>().apply { addAll(savedDomains()) }
    val words = mutableStateListOf<String>().apply { addAll(savedWords()) }
    val blockedApps = mutableStateListOf<String>().apply { addAll(savedApps()) }

    private fun savedDomains(): List<String> = snapshot()?.domains?.blockedRules()?.sorted() ?: emptyList()
    private fun savedWords(): List<String> = snapshot()?.words?.configuredRules()?.sorted() ?: emptyList()
    private fun savedApps(): List<String> = snapshot()?.blockedApps?.sorted() ?: emptyList()

    fun addDomain(domain: String) {
        val clean = domain.trim().lowercase()
            .removePrefix("http://").removePrefix("https://")
            .substringBefore('/').substringBefore('?').substringBefore('#')
        if (clean.isBlank() || clean.contains(' ')) return
        if (locked()) { refuseLocked(); return }
        if (domains.contains(clean)) return
        writeLists(domains = domains + clean)
        domains.add(clean)
    }

    fun removeDomain(domain: String) {
        if (locked()) { refuseLocked(); return }
        if (!domains.remove(domain)) return
        writeLists(domains = domains.toList())
    }

    fun addWord(word: String) {
        val clean = word.trim()
        if (clean.isBlank()) return
        if (locked()) { refuseLocked(); return }
        if (words.contains(clean)) return
        writeLists(words = words + clean)
        words.add(clean)
    }

    fun removeWord(word: String) {
        if (locked()) { refuseLocked(); return }
        if (!words.remove(word)) return
        writeLists(words = words.toList())
    }

    fun isAppBlocked(packageName: String): Boolean = blockedApps.contains(packageName)

    fun setAppBlocked(packageName: String, blocked: Boolean) {
        if (locked()) { refuseLocked(); return }
        val changed = if (blocked) {
            if (blockedApps.contains(packageName)) false else blockedApps.add(packageName)
        } else {
            blockedApps.remove(packageName)
        }
        if (changed) writeLists(apps = blockedApps.toList())
    }

    /** True when a chosen layer is not actually running; the hero offers the attention list. */
    fun needsAttention(): Boolean {
        val rules = snapshot() ?: return true
        val networkOff = DnsVpnService.state == DnsVpnService.State.ERROR
        val guardMissing = rules.hasWordProtection() || rules.visualEnabled ||
            rules.blockedApps.isNotEmpty()
        return networkOff || (guardMissing && !accessibilityGranted())
    }

    fun refreshAll() {
        refreshStatus()
        dns.refresh()
        apps.refresh()
        tags.refresh()
        images.refresh()
        shortVideos.refresh()
        gray.refresh()
        schedule.refresh()
        limit.refresh()
        scheduleDays = savedDays()
        syncMap(appLimits, savedLimits())
        sync(domains, savedDomains())
        sync(words, savedWords())
        sync(blockedApps, savedApps())
    }

    // ------------------------------------------------------------------ writes

    private fun writeAdult(on: Boolean) {
        runCatching {
            val store = store() ?: return
            val rules = store.snapshot()
            store.update(store.domainText(), store.wordText(), rules.blockedApps,
                rules.scannedApps, on)
        }.onFailure { refuse(it) }
        refreshAll()
    }

    private fun writeImages(on: Boolean) {
        runCatching { store()?.updateVisual(on) }.onFailure { refuse(it) }
        refreshAll()
    }

    private fun writeSchedule(schedule: DailyAppSchedule) {
        if (locked()) { refuseLocked(); return }
        runCatching { store()?.updateSchedule(schedule) }.onFailure { refuse(it) }
        refreshAll()
    }

    private fun writeLimits(limits: AppTimeLimit) {
        if (locked()) { refuseLocked(); return }
        runCatching { store()?.updateTimeLimits(limits) }.onFailure { refuse(it) }
        refreshAll()
    }

    private fun writeLists(
        domains: List<String>? = null,
        words: List<String>? = null,
        apps: List<String>? = null
    ) {
        if (locked()) { refuseLocked(); return }
        runCatching {
            val store = store() ?: return
            val rules = store.snapshot()
            store.update(
                domains?.joinToString("\n") ?: store.domainText(),
                words?.joinToString("\n") ?: store.wordText(),
                (apps ?: rules.blockedApps).toSet(),
                rules.scannedApps,
                rules.adultWordsEnabled
            )
        }.onFailure { refuse(it) }
        refreshAll()
    }

    private fun <T> sync(target: MutableCollection<T>, source: Collection<T>) {
        target.clear()
        target.addAll(source)
    }

    private fun <K, V> syncMap(target: MutableMap<K, V>, source: Map<K, V>) {
        target.clear()
        target.putAll(source)
    }

    companion object {
        const val MIN_MINUTES = 5
        const val MAX_MINUTES = 480
        const val DEFAULT_MINUTES = 30
        val PERMISSIONS = listOf("accessibility", "dns", "usage", "overlay", "notifications", "battery")
    }
}
