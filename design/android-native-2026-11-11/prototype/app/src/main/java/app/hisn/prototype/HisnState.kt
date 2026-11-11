package app.hisn.prototype

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * One switch, backed by both Compose state and SharedPreferences.
 *
 * The prototype used to keep every toggle in `remember`, so a tab switch or a restart silently threw
 * the choice away. A saved rule now survives both, which is what the Rules intro promises.
 */
class PrefSwitch internal constructor(
    private val prefs: SharedPreferences,
    private val key: String,
    default: Boolean
) {
    var on: Boolean by mutableStateOf(prefs.getBoolean(key, default))
        private set

    fun set(value: Boolean) {
        if (on == value) return
        on = value
        prefs.edit().putBoolean(key, value).apply()
    }
}

/**
 * Saved rules for the prototype: layers, built-in checks and the custom lists.
 *
 * Everything the Rules screen claims is saved on the phone really is saved here; the enforcement
 * layers themselves (VPN, accessibility guard) still belong to the production app.
 */
class HisnState(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hisn-prototype", Context.MODE_PRIVATE)

    // Protection layers.
    val dns = PrefSwitch(prefs, "layer.dns", true)
    val apps = PrefSwitch(prefs, "layer.apps", true)
    val images = PrefSwitch(prefs, "layer.images", true)
    val tags = PrefSwitch(prefs, "layer.tags", true)

    // Built-in checks that are not their own layer.
    val shortVideos = PrefSwitch(prefs, "check.shortVideos", false)
    val gray = PrefSwitch(prefs, "check.gray", false)

    // Custom rules.
    val schedule = PrefSwitch(prefs, "rule.schedule", false)
    val limit = PrefSwitch(prefs, "rule.limit", false)

    // Appearance.
    val systemColors = PrefSwitch(prefs, "ui.systemColors", false)
    var themeMode: String by mutableStateOf(prefs.getString("ui.theme", "system") ?: "system")
        private set

    var limitMinutes: Int by mutableIntStateOf(prefs.getInt("rule.limitMinutes", 60))
        private set

    /** Weekdays that the schedule covers; 0 is Monday, as the day chips are labelled. */
    var scheduleDays: Set<Int> by mutableStateOf(
        prefs.getStringSet("rule.scheduleDays", null)
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: setOf(0, 1, 2, 3, 4)
    )
        private set

    fun updateScheduleDays(days: Set<Int>) {
        if (scheduleDays == days) return
        scheduleDays = days
        prefs.edit().putStringSet("rule.scheduleDays", days.map { it.toString() }.toSet()).apply()
    }

    val domains = mutableStateListOf<String>().apply { addAll(readList("rule.domains")) }
    val words = mutableStateListOf<String>().apply { addAll(readList("rule.words")) }
    /** Package names, not labels: the phone decides which apps exist. */
    val blockedApps = mutableStateListOf<String>().apply { addAll(readList("rule.apps")) }

    fun updateThemeMode(mode: String) {
        if (themeMode == mode) return
        themeMode = mode
        prefs.edit().putString("ui.theme", mode).apply()
    }

    fun setLimit(enabled: Boolean, minutes: Int) {
        limit.set(enabled)
        if (limitMinutes != minutes) {
            limitMinutes = minutes
            prefs.edit().putInt("rule.limitMinutes", minutes).apply()
        }
    }

    fun addDomain(domain: String) {
        if (domains.contains(domain)) return
        domains.add(domain)
        writeList("rule.domains", domains)
    }

    fun removeDomain(domain: String) {
        if (!domains.remove(domain)) return
        writeList("rule.domains", domains)
    }

    fun addWord(word: String) {
        if (words.contains(word)) return
        words.add(word)
        writeList("rule.words", words)
    }

    fun removeWord(word: String) {
        if (!words.remove(word)) return
        writeList("rule.words", words)
    }

    fun isAppBlocked(packageName: String): Boolean = blockedApps.contains(packageName)

    fun setAppBlocked(packageName: String, blocked: Boolean) {
        val changed = if (blocked) {
            if (blockedApps.contains(packageName)) false else blockedApps.add(packageName)
        } else {
            blockedApps.remove(packageName)
        }
        if (changed) writeList("rule.apps", blockedApps)
    }

    private fun readList(key: String): List<String> {
        // A corrupt or hand-edited preference must not crash the screen.
        val raw = runCatching { prefs.getStringSet(key, null) }.getOrNull() ?: return emptyList()
        return raw.filter { it.isNotBlank() }.sorted()
    }

    private fun writeList(key: String, values: List<String>) {
        prefs.edit().putStringSet(key, values.toSet()).apply()
    }
}
