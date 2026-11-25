package app.hisn.android.nativeshell

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The Islamic edition's three looks, chosen by the user in onboarding and Settings:
 *  - Fajr: dawn rose and indigo (light)
 *  - Madinah: ivory, emerald and gold (light)
 *  - Layl: night indigo and lamp gold (dark)
 * The look replaces the light/dark setting in this edition: Layl is the dark one. The general
 * edition never reads this.
 */
enum class HisnLook(val key: String, val dark: Boolean, val t: LookTokens) {
    FAJR(
        "fajr", false, LookTokens(
            sky = Color(0xFFFBF1EC), ink = Color(0xFF241640), sub = Color(0xFF6D6280),
            card = Color(0xFFFFFFFF), line = Color(0xFFEFE3EA), pri = Color(0xFF2B1A5E),
            priInk = Color(0xFFFFFFFF), acc = Color(0xFFC8862F), soft = Color(0xFFF5EBF1),
            deep = Color(0xFF2B1A5E), deepInk = Color(0xFFFFFFFF), helpTop = Color(0xFFFBE2DD),
            helpBottom = Color(0xFFF6D3D6), helpInk = Color(0xFF3A1830), chip = Color(0xFFEFE6F6),
            ring = Color(0x1F2B1A5E)
        )
    ),
    MADINAH(
        "madinah", false, LookTokens(
            sky = Color(0xFFF7F4EA), ink = Color(0xFF13261E), sub = Color(0xFF5C6B62),
            card = Color(0xFFFFFFFF), line = Color(0xFFE5E7DC), pri = Color(0xFF0F4D3A),
            priInk = Color(0xFFFFFFFF), acc = Color(0xFFB8862B), soft = Color(0xFFEEF2E8),
            deep = Color(0xFF0F3D2F), deepInk = Color(0xFFFFFFFF), helpTop = Color(0xFFF7EAD6),
            helpBottom = Color(0xFFF2DCC5), helpInk = Color(0xFF3A2614), chip = Color(0xFFE5EFE6),
            ring = Color(0x240F4D3A)
        )
    ),
    LAYL(
        "layl", true, LookTokens(
            sky = Color(0xFF14132C), ink = Color(0xFFEFEBFA), sub = Color(0xFFA9A5C6),
            card = Color(0xFF1F1E3E), line = Color(0xFF302E57), pri = Color(0xFFE9C46A),
            priInk = Color(0xFF1B1530), acc = Color(0xFFE9C46A), soft = Color(0xFF24234A),
            deep = Color(0xFF0D0C24), deepInk = Color(0xFFEFEBFA), helpTop = Color(0xFF2C1A36),
            helpBottom = Color(0xFF3A1D33), helpInk = Color(0xFFF6E3EA), chip = Color(0xFF2A2952),
            ring = Color(0x29E9C46A)
        )
    );

    /** The background picture painted behind the home hero (hisn flavor resource). */
    val backgroundName: String get() = "hisn_bg_$key"

    /** A full Material scheme, so the shared protection and settings screens follow the look too. */
    fun scheme(base: ColorScheme): ColorScheme = base.copy(
        primary = t.pri,
        onPrimary = t.priInk,
        primaryContainer = t.chip,
        onPrimaryContainer = t.ink,
        inversePrimary = if (dark) t.deep else t.acc,
        secondary = t.sub,
        onSecondary = t.card,
        secondaryContainer = t.soft,
        onSecondaryContainer = t.ink,
        tertiary = t.acc,
        onTertiary = if (dark) t.priInk else Color.White,
        tertiaryContainer = t.chip,
        onTertiaryContainer = t.ink,
        background = t.sky,
        onBackground = t.ink,
        surface = t.sky,
        onSurface = t.ink,
        surfaceVariant = t.soft,
        onSurfaceVariant = t.sub,
        surfaceTint = t.pri,
        outline = t.sub,
        outlineVariant = t.line,
        surfaceBright = t.card,
        surfaceDim = t.soft,
        surfaceContainerLowest = t.card,
        surfaceContainerLow = t.card,
        surfaceContainer = t.card,
        surfaceContainerHigh = t.soft,
        surfaceContainerHighest = t.line
    )

    companion object {
        fun of(key: String?): HisnLook = entries.firstOrNull { it.key == key } ?: FAJR
    }
}

data class LookTokens(
    val sky: Color,
    val ink: Color,
    val sub: Color,
    val card: Color,
    val line: Color,
    val pri: Color,
    val priInk: Color,
    val acc: Color,
    val soft: Color,
    val deep: Color,
    val deepInk: Color,
    val helpTop: Color,
    val helpBottom: Color,
    val helpInk: Color,
    val chip: Color,
    val ring: Color
) {
    /** The Help button's red; the same in every look so it is never mistaken. */
    val help: Color get() = Color(0xFFD64545)
    val kept: Color get() = Color(0xFF2FB36B)
}

/** The chosen look, app-wide. Saved in the shell preferences under "ui.look". */
object HisnLooks {
    private const val PREFS = "hisn-native"
    private const val KEY = "ui.look"
    val current = mutableStateOf(HisnLook.FAJR)
    private var loaded = false

    fun load(context: Context): HisnLook {
        if (!loaded) {
            loaded = true
            current.value = HisnLook.of(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
        }
        return current.value
    }

    fun set(context: Context, look: HisnLook) {
        current.value = look
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, look.key).apply()
    }
}

val LocalLook = staticCompositionLocalOf { HisnLook.FAJR }

/**
 * Hisn-only pictures (icons, backgrounds) live in the hisn flavor, so the general edition carries
 * none of them. Code here finds them by name and gets 0 where they do not exist.
 * hisn/res/raw/hisn_keep.xml keeps them through resource shrinking.
 */
object HisnArt {
    private val cache = HashMap<String, Int>()

    @SuppressLint("DiscouragedApi")
    fun id(context: Context, name: String): Int = cache.getOrPut(name) {
        context.resources.getIdentifier(name, "drawable", context.packageName)
    }

    fun icon(context: Context, name: String): Int = id(context, "hisn_ic_$name")
}
