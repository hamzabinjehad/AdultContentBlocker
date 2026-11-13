package app.hisn.android.nativeshell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A warm neutral canvas with ink-blue as the brand seed. The tonal roles follow Material 3 so
// every component gets a native state layer, container and on-colour without hand-picked pairs.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2B4A6F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF0B1D33),
    inversePrimary = Color(0xFFA5C8F0),
    secondary = Color(0xFF52606F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E4F4),
    onSecondaryContainer = Color(0xFF0F1D2A),
    tertiary = Color(0xFF5E5A7A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE5DFF9),
    onTertiaryContainer = Color(0xFF1B1735),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFBF8F3),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE2E2E8),
    onSurfaceVariant = Color(0xFF45464D),
    surfaceTint = Color(0xFF2B4A6F),
    inverseSurface = Color(0xFF303034),
    inverseOnSurface = Color(0xFFF3F0F4),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF76777D),
    outlineVariant = Color(0xFFC6C6CC),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFBF8F3),
    surfaceDim = Color(0xFFDBD9DD),
    surfaceContainer = Color(0xFFF0EDE7),
    surfaceContainerHigh = Color(0xFFEAE7E1),
    surfaceContainerHighest = Color(0xFFE4E1DB),
    surfaceContainerLow = Color(0xFFF6F3ED),
    surfaceContainerLowest = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5C8F0),
    onPrimary = Color(0xFF12314F),
    primaryContainer = Color(0xFF2B4A6F),
    onPrimaryContainer = Color(0xFFD3E4FF),
    inversePrimary = Color(0xFF2B4A6F),
    secondary = Color(0xFFBAC8D8),
    onSecondary = Color(0xFF243240),
    secondaryContainer = Color(0xFF3A4857),
    onSecondaryContainer = Color(0xFFD6E4F4),
    tertiary = Color(0xFFC9C2E8),
    onTertiary = Color(0xFF302B4C),
    tertiaryContainer = Color(0xFF46415F),
    onTertiaryContainer = Color(0xFFE5DFF9),
    background = Color(0xFF121416),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF121416),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF44474C),
    onSurfaceVariant = Color(0xFFC4C6CC),
    surfaceTint = Color(0xFFA5C8F0),
    inverseSurface = Color(0xFFE3E2E6),
    inverseOnSurface = Color(0xFF2F3033),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8E9096),
    outlineVariant = Color(0xFF44474C),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF38393C),
    surfaceDim = Color(0xFF121416),
    surfaceContainer = Color(0xFF1E2022),
    surfaceContainerHigh = Color(0xFF282A2D),
    surfaceContainerHighest = Color(0xFF333538),
    surfaceContainerLow = Color(0xFF1A1C1E),
    surfaceContainerLowest = Color(0xFF0D0F11)
)

// Status colours are semantic, so they live outside the tonal palette. Each pair keeps at least
// 4.5:1 for body text and never relies on colour alone (the row always carries a word).
data class HisnStatusColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val card: Color,
    val cardMuted: Color
)

private val LightStatusColors = HisnStatusColors(
    success = Color(0xFF2E6B4F),
    successContainer = Color(0xFFD9F2E2),
    onSuccessContainer = Color(0xFF0B2B1B),
    warning = Color(0xFF7A5200),
    warningContainer = Color(0xFFFFE8C2),
    onWarningContainer = Color(0xFF2B1B00),
    card = Color(0xFFFFFFFF),
    cardMuted = Color(0xFFF3EFE7)
)

private val DarkStatusColors = HisnStatusColors(
    success = Color(0xFF8FD3AE),
    successContainer = Color(0xFF14351F),
    onSuccessContainer = Color(0xFFD9F2E2),
    warning = Color(0xFFF0C36A),
    warningContainer = Color(0xFF3B2A00),
    onWarningContainer = Color(0xFFFFE8C2),
    card = Color(0xFF1E2022),
    cardMuted = Color(0xFF26282B)
)

val LocalHisnStatusColors = staticCompositionLocalOf { LightStatusColors }

private val HisnTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

private val HisnShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Colour changes are effects, so they animate with a critically damped spring rather than a hard
 * swap. This is what makes Light/Dark and "Use system colors" feel like Android instead of a
 * configuration restart: 320 ms, every visible role, no flash of the old surface edges.
 */
@Composable
private fun Color.animatedRole(label: String): Color = animateColorAsState(
    targetValue = this,
    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
    label = label
).value

@Composable
private fun animateScheme(target: ColorScheme): ColorScheme = target.copy(
    primary = target.primary.animatedRole("primary"),
    onPrimary = target.onPrimary.animatedRole("onPrimary"),
    primaryContainer = target.primaryContainer.animatedRole("primaryContainer"),
    onPrimaryContainer = target.onPrimaryContainer.animatedRole("onPrimaryContainer"),
    inversePrimary = target.inversePrimary.animatedRole("inversePrimary"),
    secondary = target.secondary.animatedRole("secondary"),
    onSecondary = target.onSecondary.animatedRole("onSecondary"),
    secondaryContainer = target.secondaryContainer.animatedRole("secondaryContainer"),
    onSecondaryContainer = target.onSecondaryContainer.animatedRole("onSecondaryContainer"),
    tertiary = target.tertiary.animatedRole("tertiary"),
    tertiaryContainer = target.tertiaryContainer.animatedRole("tertiaryContainer"),
    background = target.background.animatedRole("background"),
    onBackground = target.onBackground.animatedRole("onBackground"),
    surface = target.surface.animatedRole("surface"),
    onSurface = target.onSurface.animatedRole("onSurface"),
    surfaceVariant = target.surfaceVariant.animatedRole("surfaceVariant"),
    onSurfaceVariant = target.onSurfaceVariant.animatedRole("onSurfaceVariant"),
    surfaceTint = target.surfaceTint.animatedRole("surfaceTint"),
    inverseSurface = target.inverseSurface.animatedRole("inverseSurface"),
    inverseOnSurface = target.inverseOnSurface.animatedRole("inverseOnSurface"),
    error = target.error.animatedRole("error"),
    onError = target.onError.animatedRole("onError"),
    errorContainer = target.errorContainer.animatedRole("errorContainer"),
    onErrorContainer = target.onErrorContainer.animatedRole("onErrorContainer"),
    outline = target.outline.animatedRole("outline"),
    outlineVariant = target.outlineVariant.animatedRole("outlineVariant"),
    surfaceBright = target.surfaceBright.animatedRole("surfaceBright"),
    surfaceDim = target.surfaceDim.animatedRole("surfaceDim"),
    surfaceContainer = target.surfaceContainer.animatedRole("surfaceContainer"),
    surfaceContainerHigh = target.surfaceContainerHigh.animatedRole("surfaceContainerHigh"),
    surfaceContainerHighest = target.surfaceContainerHighest.animatedRole("surfaceContainerHighest"),
    surfaceContainerLow = target.surfaceContainerLow.animatedRole("surfaceContainerLow"),
    surfaceContainerLowest = target.surfaceContainerLowest.animatedRole("surfaceContainerLowest")
)

@Composable
private fun animateStatus(target: HisnStatusColors): HisnStatusColors = target.copy(
    success = target.success.animatedRole("success"),
    successContainer = target.successContainer.animatedRole("successContainer"),
    onSuccessContainer = target.onSuccessContainer.animatedRole("onSuccessContainer"),
    warning = target.warning.animatedRole("warning"),
    warningContainer = target.warningContainer.animatedRole("warningContainer"),
    onWarningContainer = target.onWarningContainer.animatedRole("onWarningContainer"),
    card = target.card.animatedRole("card"),
    cardMuted = target.cardMuted.animatedRole("cardMuted")
)

@Composable
fun HisnTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val target = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val colors = animateScheme(target)
    val status = animateStatus(if (darkTheme) DarkStatusColors else LightStatusColors)
    CompositionLocalProvider(LocalHisnStatusColors provides status) {
        MaterialTheme(
            colorScheme = colors,
            typography = HisnTypography,
            shapes = HisnShapes,
            content = content
        )
    }
}
