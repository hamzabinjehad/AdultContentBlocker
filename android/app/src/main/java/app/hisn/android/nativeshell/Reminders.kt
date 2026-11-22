package app.hisn.android.nativeshell

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.hisn.android.Edition
import app.hisn.android.R
import org.json.JSONObject

/**
 * One reminder from the edition's content pack (assets/reminders/reminders.json, which each
 * edition ships in src/<edition>/assets). The Islamic edition's pack holds Qur'an, hadith and Ibn
 * al-Qayyim, each checked against a source; the general edition's holds plain prompts.
 */
data class Reminder(
    val id: String,
    val kind: String,
    val topics: List<String>,
    val ar: String,
    val en: String,
    val sourceAr: String,
    val sourceEn: String
) {
    /** Scripture and quotes are shown in Arabic in every language; prompts follow the phone. */
    val alwaysArabic: Boolean get() = kind == KIND_QURAN || kind == KIND_HADITH || kind == KIND_SCHOLAR

    companion object {
        const val KIND_QURAN = "quran"
        const val KIND_HADITH = "hadith"
        const val KIND_SCHOLAR = "scholar"
    }
}

object Reminders {
    const val TOPIC_GAZE = "gaze"
    const val TOPIC_TIME = "time"

    @Volatile
    private var cache: List<Reminder>? = null

    fun all(context: Context): List<Reminder> =
        cache ?: load(context.applicationContext).also { cache = it }

    /** The same reminder all day, a new one each day. */
    fun forDay(context: Context, epochDay: Long = java.time.LocalDate.now().toEpochDay()): Reminder? {
        val items = all(context)
        return if (items.isEmpty()) null else items[Math.floorMod(epochDay, items.size.toLong()).toInt()]
    }

    /** A reminder for a moment (a block, a wait), preferring [topic] when the pack has one. */
    fun pick(context: Context, topic: String?, seed: Long = System.nanoTime()): Reminder? {
        val items = all(context)
        val matching = if (topic == null) items else items.filter { topic in it.topics }.ifEmpty { items }
        return if (matching.isEmpty()) null else matching[Math.floorMod(seed, matching.size.toLong()).toInt()]
    }

    internal fun parse(json: String): List<Reminder> {
        val array = JSONObject(json).getJSONArray("items")
        return (0 until array.length()).mapNotNull { index ->
            val item = array.getJSONObject(index)
            val ar = item.optString("ar").trim()
            val en = item.optString("en").trim()
            if (ar.isEmpty() || en.isEmpty()) return@mapNotNull null
            val topics = item.optJSONArray("topics")
            Reminder(
                id = item.optString("id"),
                kind = item.optString("kind"),
                topics = if (topics == null) emptyList() else (0 until topics.length()).map(topics::getString),
                ar = ar,
                en = en,
                sourceAr = item.optString("source_ar"),
                sourceEn = item.optString("source_en")
            )
        }
    }

    private fun load(context: Context): List<Reminder> = try {
        context.assets.open("reminders/reminders.json").use { parse(it.reader(Charsets.UTF_8).readText()) }
    } catch (missingOrBroken: Exception) {
        // A missing or damaged pack only hides the card; it never affects blocking.
        emptyList()
    }
}

private val ArabicText = TextStyle(
    localeList = LocaleList("ar"),
    textDirection = TextDirection.Rtl
)

/**
 * The reminder's words, the English meaning under scripture when the phone is not in Arabic, and
 * where it comes from. [center] is for the wait screen, where everything is centred.
 */
@Composable
fun ReminderText(reminder: Reminder, modifier: Modifier = Modifier, center: Boolean = false) {
    // Read through the configuration so a language change recomposes the text.
    val arabicPhone = androidx.core.os.ConfigurationCompat.getLocales(
        androidx.compose.ui.platform.LocalConfiguration.current
    )[0]?.language == "ar"
    val colors = MaterialTheme.colorScheme
    val align = if (center) TextAlign.Center else TextAlign.Start
    val showArabic = reminder.alwaysArabic || arabicPhone
    val main = if (showArabic) reminder.ar else reminder.en
    val framed = when (reminder.kind) {
        Reminder.KIND_QURAN -> "﴿$main﴾"
        Reminder.KIND_HADITH, Reminder.KIND_SCHOLAR -> "«$main»"
        else -> main
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            framed,
            style = (if (unrotEdition && !showArabic) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium).merge(if (showArabic) ArabicText else TextStyle())
                .copy(lineHeight = MaterialTheme.typography.titleMedium.lineHeight * 1.25f),
            textAlign = align,
            modifier = Modifier.fillMaxWidth()
        )
        if (showArabic && !arabicPhone) {
            Text(
                reminder.en,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = align,
                modifier = Modifier.fillMaxWidth()
            )
        }
        val source = if (arabicPhone) reminder.sourceAr else reminder.sourceEn
        if (source.isNotBlank()) {
            Text(
                source,
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
                textAlign = align,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Home's card: today's reminder, the same one all day. Nothing shows if the pack is empty. */
@Composable
fun ReminderCard() {
    val context = LocalContext.current
    val reminder = remember { Reminders.forDay(context) } ?: return
    HisnCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(if (Edition.islamic) R.string.reminder_title_islamic else R.string.reminder_title_general),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() }
            )
            ReminderText(reminder)
        }
    }
}
