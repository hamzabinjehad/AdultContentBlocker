package app.hisn.android.nativeshell

import android.content.Context
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hisn.core.HijriTabular
import app.hisn.core.PrayerPlaces
import app.hisn.core.PrayerTimes
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

/** Arabic or English copy, side by side so neither can drift from the other. */
internal fun ia(en: String, ar: String): String = if (isArabicUi()) hisnWords(ar) else en

private const val AR_WORD = "[\\u0621-\\u0652\\u0670]+"
private val WordRules = listOf(
    "المواقع والبحث والكلمات والصور الإباحية" to "مواقع الزنا وبحثه وكلماته وصوره",
    "المواقع والكلمات والصور الإباحية" to "مواقع الزنا وكلماته وصوره",
    "الكلمات والوسوم الإباحية" to "كلمات الزنا ووسومه",
    "المواقع والصور الإباحية" to "مواقع الزنا وصوره",
    "الكلمات والصور الإباحية" to "كلمات الزنا وصوره",
    "بأنها إباحية" to "بأنها للزنا",
)
private val WordPatterns = listOf(
    Regex("ال($AR_WORD) الإباحية") to "$1 الزنا",
    Regex("ال($AR_WORD) الإباحي(?![\\u0621-\\u064a])") to "$1 الزنا",
    Regex("($AR_WORD) إباحية") to "$1 زنا",
    Regex("($AR_WORD) إباحي(?![\\u0621-\\u064a])") to "$1 زنا",
)

/**
 * Hisn's Arabic, by the owner's choice: 1 2 3 rather than Arabic-Indic digits, and "الزنا" where
 * the general app says "الإباحي". The same rules write the hisn string overrides
 * (tools/hisn_arabic_overrides.py); this covers text built in code.
 */
internal fun hisnWords(text: String): String {
    if (!app.hisn.android.Edition.islamic) return text
    var t = text
    if (t.contains("إباح")) {
        for ((a, b) in WordRules) t = t.replace(a, b)
        for ((rx, to) in WordPatterns) t = rx.replace(t, to)
    }
    if (t.any { it in '٠'..'٩' }) {
        val b = StringBuilder(t.length)
        for (c in t) b.append(if (c in '٠'..'٩') ('0' + (c - '٠')) else c)
        t = b.toString()
    }
    return t
}

internal fun isArabicUi(): Boolean = UiLanguage.arabic ?: (Locale.getDefault().language == "ar")

/**
 * The language the screen is drawn in, set by [HisnTheme] from the activity's configuration.
 * Locale.getDefault() is not enough: when the protection service starts the process before any
 * activity, it keeps the system language even though the app's own language is Arabic.
 */
internal object UiLanguage {
    @Volatile var arabic: Boolean? = null
}

/**
 * Arabic screens of the Islamic edition count in 1 2 3. Some phones format %d in "ar" with
 * Arabic-Indic digits, so the activity's resources get the same language with Latin numbering;
 * every string resource, date and number format then follows without touching each call.
 */
fun arabicDigitsContext(base: Context): Context {
    if (!app.hisn.android.Edition.islamic) return base
    val config = base.resources.configuration
    val locale = androidx.core.os.ConfigurationCompat.getLocales(config)[0] ?: return base
    if (locale.language != "ar" || locale.getUnicodeLocaleType("nu") == "latn") return base
    val next = android.content.res.Configuration(config)
    next.setLocale(Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build())
    return base.createConfigurationContext(next)
}

/** The locale Compose observes, so month and day names follow a language change. */
@Composable
internal fun uiLocale(): Locale =
    androidx.core.os.ConfigurationCompat.getLocales(androidx.compose.ui.platform.LocalConfiguration.current)[0] ?: Locale.ENGLISH

/** Numbers in Arabic copy stay 1 2 3; kept as one place to change that. */
internal fun nums(s: String): String = s

internal fun nums(n: Int): String = nums(n.toString())

/** A number standing alone in large type. */
internal fun bigNum(n: Int): String = n.toString()

/** "3/5" in English; "3 من 5" in Arabic. */
internal fun ofCount(done: Int, total: Int): String = if (isArabicUi()) "$done من $total" else "$done/$total"

// ---------------- Hijri dates ----------------

internal data class HijriDay(val year: Int, val month: Int, val day: Int) {
    fun monthName(): String = if (isArabicUi()) HijriTabular.MONTHS_AR[month - 1] else HijriTabular.MONTHS_EN[month - 1]
    fun label(withYear: Boolean = false): String =
        nums("$day ") + monthName() + if (withYear) nums(" $year") + ia(" AH", " هـ") else ""
}

/** Umm al-Qura from the system calendar (Android 7+); the tabular calendar on Android 6. */
internal fun hijriOf(date: LocalDate): HijriDay {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        runCatching {
            val cal = android.icu.util.IslamicCalendar()
            cal.calculationType = android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
            val g = android.icu.util.GregorianCalendar(date.year, date.monthValue - 1, date.dayOfMonth, 12, 0)
            cal.timeInMillis = g.timeInMillis
            return HijriDay(
                cal.get(android.icu.util.Calendar.EXTENDED_YEAR),
                cal.get(android.icu.util.Calendar.MONTH) + 1,
                cal.get(android.icu.util.Calendar.DAY_OF_MONTH)
            )
        }
    }
    val h = HijriTabular.fromGregorian(date.year, date.monthValue, date.dayOfMonth)
    return HijriDay(h.year, h.month, h.day)
}

// ---------------- dates for where the person is ----------------

/** Arabic countries name the Gregorian months differently: يناير in Egypt and the Gulf, كانون الثاني in the Levant and Iraq, جانفي in Algeria and Tunisia. */
private val ZoneCountry = mapOf(
    "Asia/Riyadh" to "SA", "Asia/Dubai" to "AE", "Asia/Kuwait" to "KW", "Asia/Qatar" to "QA", "Asia/Bahrain" to "BH", "Asia/Muscat" to "OM",
    "Asia/Aden" to "YE", "Asia/Amman" to "JO", "Asia/Damascus" to "SY", "Asia/Beirut" to "LB", "Asia/Baghdad" to "IQ", "Asia/Gaza" to "PS",
    "Asia/Hebron" to "PS", "Asia/Jerusalem" to "PS", "Africa/Cairo" to "EG", "Africa/Khartoum" to "SD", "Africa/Tripoli" to "LY",
    "Africa/Tunis" to "TN", "Africa/Algiers" to "DZ", "Africa/Casablanca" to "MA"
)

/** The locale Gregorian dates are written in: the person's own country's Arabic when the screen is Arabic, with 1 2 3 digits. */
internal fun placeLocale(context: Context): Locale {
    if (!isArabicUi()) return Locale.ENGLISH
    val country = ZoneCountry[HisnPrayers.place(context).zone] ?: ZoneCountry[TimeZone.getDefault().id] ?: "EG"
    return Locale.Builder().setLanguage("ar").setRegion(country).setUnicodeLocaleKeyword("nu", "latn").build()
}

/** A Gregorian date as the person's country writes it, e.g. "23 نوفمبر" or "23 تشرين الثاني". */
internal fun gregLabel(context: Context, date: LocalDate, withYear: Boolean = false): String =
    hisnWords(date.format(java.time.format.DateTimeFormatter.ofPattern(if (withYear) "d MMMM yyyy" else "d MMMM", placeLocale(context)))) +
        if (withYear && isArabicUi()) " م" else ""

/**
 * Marks an Arabic line as right to left. A line that starts or ends with «, — or a number otherwise takes its
 * direction from the phone's own language and puts those marks on the wrong end. The marks are invisible.
 */
internal fun rtlLine(text: String): String = "\u200F$text\u200F"

/** Both calendars are always shown. The one the person's country writes its days in comes first: Hijri in Saudi Arabia, Gregorian elsewhere. */
internal fun bothDates(context: Context, hijri: String, gregorian: String): String =
    if (isArabicUi() && placeLocale(context).country == "SA") "$hijri · $gregorian" else "$gregorian · $hijri"

/** The city the dates and prayer times are worked out for. */
internal fun placeName(context: Context): String = HisnPrayers.place(context).let { if (isArabicUi()) it.ar else it.en }

// ---------------- prayer times ----------------

internal object HisnPrayers {
    private const val PREFS = "hisn-native"

    fun place(context: Context): PrayerPlaces.Place {
        val key = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("prayer.city", null)
        return PrayerPlaces.byKey(key) ?: PrayerPlaces.forZone(TimeZone.getDefault().id)
    }

    fun setPlace(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("prayer.city", key).apply()
    }

    fun asr(context: Context): PrayerTimes.Asr =
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("prayer.hanafi", false)) PrayerTimes.Asr.HANAFI
        else PrayerTimes.Asr.STANDARD

    fun today(context: Context, date: LocalDate = LocalDate.now()): PrayerTimes {
        val p = place(context)
        val zone = ZoneId.of(p.zone)
        val offset = zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 60
        return PrayerTimes.compute(date.year, date.monthValue, date.dayOfMonth, p.latitude, p.longitude, offset, p.method, asr(context))
    }

    /** Which of today's five the reader marked prayed: a bitmask kept per day. */
    fun prayed(context: Context, date: LocalDate = LocalDate.now()): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("prayed." + date.toEpochDay(), 0)

    fun togglePrayed(context: Context, index: Int, date: LocalDate = LocalDate.now()): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = prayed(context, date) xor (1 shl index)
        prefs.edit().putInt("prayed." + date.toEpochDay(), next).apply()
        return next
    }

    val namesEn = listOf("Fajr", "Dhuhr", "ʿAṣr", "Maghrib", "ʿIshāʾ")
    val namesAr = listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")
    fun name(i: Int): String = if (isArabicUi()) namesAr[i] else namesEn[i]
}

/** Minutes after midnight now, refreshed every [periodMs]. */
@Composable
internal fun rememberNowMinutes(periodMs: Long = 20_000): Int {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(periodMs)
            now = System.currentTimeMillis()
        }
    }
    val c = java.util.Calendar.getInstance().apply { timeInMillis = now }
    return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
}

// ---------------- shared visuals ----------------

/** One of the sixteen painted icons; nothing in the general edition, which has none. */
@Composable
internal fun HisnIcon(name: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val id = remember(name) { HisnArt.icon(context, name) }
    if (id != 0) {
        Image(painterResource(id), contentDescription = null, modifier = modifier.size(size), contentScale = ContentScale.Fit)
    } else {
        Box(modifier.size(size))
    }
}

/** The soft white card every Islamic screen is built from. */
internal fun Modifier.hisnCard(look: HisnLook, radius: Dp = 18.dp, color: Color = look.t.card): Modifier =
    this
        .shadow(if (look.dark) 0.dp else 6.dp, RoundedCornerShape(radius), ambientColor = Color(0x14281446), spotColor = Color(0x14281446))
        .clip(RoundedCornerShape(radius))
        .background(color)

internal class Ref<T>(initial: T) { var value by mutableStateOf(initial) }
