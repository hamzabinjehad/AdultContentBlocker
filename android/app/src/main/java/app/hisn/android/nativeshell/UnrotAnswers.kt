package app.hisn.android.nativeshell

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import app.hisn.android.R
import java.util.Calendar

/**
 * What the person told setup about themselves, kept on this phone only, and where each answer is
 * used. Every question in setup has a job:
 *  - goals pre-fill the plan (quiet hours, how firm, adult blocking),
 *  - "when" sets the quiet window,
 *  - "how it feels" words the block and pause screens in the person's own terms,
 *  - "time back" becomes the "instead" line on those screens.
 * Unchaind asks fourteen questions and uses them for a made-up dependency score; these four are
 * all used, and nothing leaves the phone.
 */
object UnrotAnswers {
    private const val PREFS = "unrot_answers"

    val GOALS = listOf("sleep", "focus", "evenings", "adult", "mornings", "less")
    val WHEN = listOf("night", "morning", "bored", "stressed", "between")
    val FEEL = listOf("tired", "guilty", "behind", "anxious", "numb", "fine")
    val INSTEAD = listOf("sleep", "read", "train", "friends", "study", "make", "outside", "family")

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, goals: Set<String>, whenKey: String, feel: Set<String>, instead: Set<String>) {
        prefs(context).edit()
            .putStringSet("goals", goals)
            .putString("when", whenKey)
            .putStringSet("feel", feel)
            .putStringSet("instead", instead)
            .apply()
    }

    fun goals(context: Context): Set<String> =
        runCatching { prefs(context).getStringSet("goals", emptySet())!!.toSet() }.getOrDefault(emptySet())

    /** The age band from setup ("u18", "18", "25"…), or "" when skipped. Only used for the year numbers. */
    fun age(context: Context): String =
        runCatching { prefs(context).getString("age", "") ?: "" }.getOrDefault("")

    fun saveAge(context: Context, key: String) { prefs(context).edit().putString("age", key).apply() }

    fun whenKey(context: Context): String =
        runCatching { prefs(context).getString("when", "") ?: "" }.getOrDefault("")

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun feel(context: Context): Set<String> =
        runCatching { prefs(context).getStringSet("feel", emptySet())!!.toSet() }.getOrDefault(emptySet())

    fun instead(context: Context): Set<String> =
        runCatching { prefs(context).getStringSet("instead", emptySet())!!.toSet() }.getOrDefault(emptySet())

    /** "You said scrolling leaves you tired and behind on things.", or null. */
    fun feelLine(context: Context): String? {
        val words = FEEL.filter { it in feel(context) && it != "fine" }.map { context.getString(feelWord(it)) }
        if (words.isEmpty()) return null
        val joined = if (words.size == 1) words[0]
            else words.dropLast(1).joinToString(", ") + context.getString(R.string.answers_and) + words.last()
        return context.getString(R.string.answers_feel_line, joined)
    }

    /** "Instead: read 10 pages · text a friend", or null. */
    fun insteadLine(context: Context): String? {
        val picks = INSTEAD.filter { it in instead(context) }.take(2).map { context.getString(insteadAction(it)) }
        if (picks.isEmpty()) return null
        return context.getString(R.string.answers_instead_line, picks.joinToString(" · "))
    }

    fun goalTitle(key: String) = when (key) {
        "sleep" -> R.string.goal_sleep; "focus" -> R.string.goal_focus; "evenings" -> R.string.goal_evenings
        "adult" -> R.string.goal_adult; "mornings" -> R.string.goal_mornings; else -> R.string.goal_less
    }
    fun goalBody(key: String) = when (key) {
        "sleep" -> R.string.goal_sleep_body; "focus" -> R.string.goal_focus_body; "evenings" -> R.string.goal_evenings_body
        "adult" -> R.string.goal_adult_body; "mornings" -> R.string.goal_mornings_body; else -> R.string.goal_less_body
    }
    fun whenTitle(key: String) = when (key) {
        "night" -> R.string.when_night; "morning" -> R.string.when_morning; "bored" -> R.string.when_bored
        "stressed" -> R.string.when_stressed; else -> R.string.when_between
    }
    fun feelTitle(key: String) = when (key) {
        "tired" -> R.string.feel_tired; "guilty" -> R.string.feel_guilty; "behind" -> R.string.feel_behind
        "anxious" -> R.string.feel_anxious; "numb" -> R.string.feel_numb; else -> R.string.feel_fine
    }
    fun feelWord(key: String) = when (key) {
        "tired" -> R.string.feel_word_tired; "guilty" -> R.string.feel_word_guilty; "behind" -> R.string.feel_word_behind
        "anxious" -> R.string.feel_word_anxious; else -> R.string.feel_word_numb
    }
    fun insteadTitle(key: String) = when (key) {
        "sleep" -> R.string.instead_sleep; "read" -> R.string.instead_read; "train" -> R.string.instead_train
        "friends" -> R.string.instead_friends; "study" -> R.string.instead_study; "make" -> R.string.instead_make
        "outside" -> R.string.instead_outside; else -> R.string.instead_family
    }
    fun insteadAction(key: String) = when (key) {
        "sleep" -> R.string.instead_do_sleep; "read" -> R.string.instead_do_read; "train" -> R.string.instead_do_train
        "friends" -> R.string.instead_do_friends; "study" -> R.string.instead_do_study; "make" -> R.string.instead_do_make
        "outside" -> R.string.instead_do_outside; else -> R.string.instead_do_family
    }
}

/**
 * The person's real starting point over the last seven days, from Android's own usage events:
 * average daily time in feed apps, per-app averages, how often those apps are opened, the longest
 * unbroken session, and time by hour of day. Read on the phone each time; nothing is stored.
 */
data class StartingPoint(
    val dailyMs: Long,
    val apps: List<Pair<String, Long>>,
    val opensPerDay: Int,
    val longestMs: Long,
    val hours: LongArray,
    val hardestHour: Int
) {
    val hasData: Boolean get() = dailyMs > 0
}

internal object StartingPoints {
    const val DAYS = 7
    private const val RESUMED = 1 // MOVE_TO_FOREGROUND / ACTIVITY_RESUMED
    private const val PAUSED = 2 // MOVE_TO_BACKGROUND / ACTIVITY_PAUSED
    private const val STOPPED = 23 // ACTIVITY_STOPPED (Android 10+)
    private const val SCREEN_OFF = 16 // SCREEN_NON_INTERACTIVE (Android 9+)
    private const val KEYGUARD = 17 // KEYGUARD_SHOWN (Android 9+)
    /** No real scroll lasts longer; a longer span means Android missed the end (a video left playing). */
    private const val MAX_SESSION_MS = 4 * 3_600_000L
    /** A return to the same app within this gap is the same session (a dialog, a share sheet). */
    private const val SAME_SESSION_MS = 5_000L

    fun read(context: Context, packages: Set<String>): StartingPoint? {
        if (packages.isEmpty() || !Usage.hasAccess(context)) return null
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val end = System.currentTimeMillis()
        val start = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -(DAYS - 1))
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val events = runCatching { manager.queryEvents(start, end) }.getOrNull() ?: return null
        val perApp = HashMap<String, Long>()
        val hours = LongArray(24)
        var opens = 0
        var longest = 0L
        var current: String? = null
        var sessionStart = 0L
        var pausedAt = -1L
        fun close(at: Long) {
            val pkg = current ?: return
            if (pkg in packages && at > sessionStart) {
                val length = minOf(at - sessionStart, MAX_SESSION_MS)
                perApp[pkg] = (perApp[pkg] ?: 0L) + length
                longest = maxOf(longest, length)
                hours[Calendar.getInstance().apply { timeInMillis = sessionStart }.get(Calendar.HOUR_OF_DAY)] += length
            }
            current = null
        }
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            if (!events.getNextEvent(event)) break
            val pkg = event.packageName ?: ""
            if (pkg.isEmpty() && event.eventType != SCREEN_OFF && event.eventType != KEYGUARD) continue
            when (event.eventType) {
                RESUMED -> {
                    if (pkg == current && pausedAt >= 0 && event.timeStamp - pausedAt < SAME_SESSION_MS) {
                        pausedAt = -1L
                    } else {
                        close(if (pausedAt >= 0) pausedAt else event.timeStamp)
                        current = pkg
                        sessionStart = event.timeStamp
                        pausedAt = -1L
                        if (pkg in packages) opens++
                    }
                }
                PAUSED, STOPPED -> if (pkg == current && pausedAt < 0) pausedAt = event.timeStamp
                // The screen went off: whatever was open stopped being looked at.
                SCREEN_OFF, KEYGUARD -> { close(if (pausedAt >= 0) pausedAt else event.timeStamp); pausedAt = -1L }
            }
        }
        close(if (pausedAt >= 0) pausedAt else end)
        val total = perApp.values.sum()
        val hardest = if (total > 0) hours.indices.maxBy { hours[it] } else -1
        return StartingPoint(
            dailyMs = total / DAYS,
            apps = perApp.entries.sortedByDescending { it.value }.map { it.key to it.value / DAYS },
            opensPerDay = (opens + DAYS / 2) / DAYS,
            longestMs = longest,
            hours = hours,
            hardestHour = hardest
        )
    }
}
