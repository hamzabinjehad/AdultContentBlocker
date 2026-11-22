package app.hisn.android.nativeshell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.hisn.android.Edition

/**
 * The general edition's (Unrot's) card language, "premium" since November 2026:
 *
 *  - Light (Ivory): white cards lifted off warm ivory by a soft, warm shadow. No outlines; the
 *    hero card (the one thing that matters most on a screen) gets a deeper shadow.
 *  - Dark (Night gold): near-black, cards as faint frosted glass with a hairline edge; the hero
 *    gets a gold edge.
 *
 * Depth instead of lines, and gold as the only accent. The Islamic edition keeps its flat
 * Material cards: this modifier does nothing there.
 */
internal val UnrotInk = Color(0xFF1F2A23)
internal val UnrotBlush = Color(0xFFF2A08F)
private val UnrotGold = Color(0xFFF2C14E)
private val IvoryShadow = Color(0xFF5A4512)

@Composable
internal fun Modifier.unrotFrame(shape: Shape, hero: Boolean = false): Modifier {
    if (Edition.islamic) return this
    return if (LocalDarkTheme.current) {
        this.border(1.dp, if (hero) UnrotGold.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.07f), shape)
    } else {
        this.shadow(
            elevation = if (hero) 18.dp else 8.dp, shape = shape, clip = false,
            ambientColor = IvoryShadow.copy(alpha = if (hero) 0.30f else 0.18f),
            spotColor = IvoryShadow.copy(alpha = if (hero) 0.34f else 0.22f)
        )
    }
}

/**
 * The screen behind Unrot's pages: the background colour with a soft gold glow from the top,
 * warmer and stronger at night. Scaffolds paint transparent over it.
 */
@Composable
internal fun Modifier.unrotBackdrop(): Modifier {
    val base = MaterialTheme.colorScheme.background
    if (Edition.islamic) return this.background(base)
    val glow = if (LocalDarkTheme.current) Color(0xFF3A3018).copy(alpha = 0.95f) else Color(0xFFFFF0C4).copy(alpha = 0.9f)
    return this.drawBehind {
        drawRect(base)
        drawRect(
            androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(glow, Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width / 2f, -size.width * 0.15f),
                radius = size.width * 1.05f
            )
        )
    }
}

/**
 * The 3D object for a Material icon used in Unrot's rows (Unrot's own black-and-gold set), or null
 * to keep the vector. One table, so a rule looks the same on every page.
 */
internal fun art3D(icon: androidx.compose.ui.graphics.vector.ImageVector): Int? =
    when (icon.name.substringAfterLast('.')) {
        "ViewList" -> app.hisn.android.R.drawable.unrot_3d_mobile
        "List", "FormatListBulleted" -> app.hisn.android.R.drawable.unrot_3d_book
        "PlayArrow", "Replay" -> app.hisn.android.R.drawable.unrot_3d_rocket
        "Pause" -> app.hisn.android.R.drawable.unrot_3d_pause
        "Timer", "HourglassBottom" -> app.hisn.android.R.drawable.unrot_3d_battery
        "Bedtime", "DarkMode", "NightsStay" -> app.hisn.android.R.drawable.unrot_3d_moon
        "Language", "Security", "Shield", "HealthAndSafety", "Public", "Wifi", "PrivacyTip" -> app.hisn.android.R.drawable.unrot_3d_shield
        "EditNote" -> app.hisn.android.R.drawable.unrot_3d_notebook
        "LocalOffer", "TextFields", "Edit" -> app.hisn.android.R.drawable.unrot_3d_pencil
        "Image", "PhotoLibrary" -> app.hisn.android.R.drawable.unrot_3d_magic_trick
        "Apps", "Accessibility" -> app.hisn.android.R.drawable.unrot_3d_eye
        "Contrast", "Lightbulb" -> app.hisn.android.R.drawable.unrot_3d_bulb
        "TravelExplore", "Search" -> app.hisn.android.R.drawable.unrot_3d_explorer
        "Lock", "Block", "Close" -> app.hisn.android.R.drawable.unrot_3d_lock
        "Check", "CheckCircle" -> app.hisn.android.R.drawable.unrot_3d_tick
        "CenterFocusStrong", "CenterFocusWeak" -> app.hisn.android.R.drawable.unrot_3d_target
        "Star", "StarRate" -> app.hisn.android.R.drawable.unrot_3d_star
        "Notifications" -> app.hisn.android.R.drawable.unrot_3d_toggle
        "Bolt", "BatteryChargingFull" -> app.hisn.android.R.drawable.unrot_3d_flash
        else -> null
    }

/**
 * A floating 3D object: no tile behind it. At night it sits on a faint gold glow so the dark
 * metal reads against the dark page.
 */
@Composable
internal fun Float3D(res: Int, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, glow: Boolean = true) {
    val dark = LocalDarkTheme.current
    androidx.compose.foundation.layout.Box(
        modifier.size(size).then(
            if (dark && glow) Modifier.drawBehind {
                drawCircle(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(UnrotGold.copy(alpha = 0.30f), Color.Transparent),
                        radius = this.size.minDimension * 0.62f
                    ),
                    radius = this.size.minDimension * 0.62f
                )
            } else Modifier
        ),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(res), contentDescription = null,
            modifier = Modifier.size(size * 0.92f),
            colorFilter = if (dark) androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                androidx.compose.ui.graphics.ColorMatrix().apply { setToScale(1.08f, 1.07f, 1.05f, 1f) }
            ) else null
        )
    }
}

/** True for the general edition, where the Unrot styles apply. */
internal val unrotEdition: Boolean get() = !Edition.islamic

/** Hairline that separates the bottom bar from paper in the general edition. */
@Composable
internal fun unrotHairline(): Color =
    if (LocalDarkTheme.current) Color.White.copy(alpha = 0.08f) else UnrotInk.copy(alpha = 0.12f)

@Composable
internal fun unrotBarColor(): Color = MaterialTheme.colorScheme.background

/**
 * Story headings are serif in both editions. Unrot's type scale already sets Fraunces, so keep it;
 * the Islamic edition (and Unrot in Arabic, which uses system fonts) gets the system serif.
 */
internal fun androidx.compose.ui.text.TextStyle.storySerif(): androidx.compose.ui.text.TextStyle {
    val family = fontFamily
    val bundled = family != null && family != androidx.compose.ui.text.font.FontFamily.SansSerif &&
        family != androidx.compose.ui.text.font.FontFamily.Default
    return if (unrotEdition && bundled) this
    else copy(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
    )
}
