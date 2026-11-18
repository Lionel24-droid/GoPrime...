package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val GoPrimeLightColors = lightColorScheme(
    primary = PrimeNavy,
    onPrimary = Color.White,
    primaryContainer = PrimeNavyContainer,
    onPrimaryContainer = OnPrimeNavyContainer,
    secondary = PrimeCobalt,
    onSecondary = Color.White,
    secondaryContainer = PrimeCobaltContainer,
    onSecondaryContainer = OnPrimeCobaltContainer,
    tertiary = PrimeSteel,
    onTertiary = Color.White,
    tertiaryContainer = PrimeSteelContainer,
    onTertiaryContainer = OnPrimeSteelContainer,
    background = SilverBackground,
    onBackground = NavyInkPrimary,
    surface = PureSurface,
    onSurface = NavyInkPrimary,
    surfaceVariant = SilverSurfaceVariant,
    onSurfaceVariant = NavyInkSecondary,
    outline = OutlineSilver,
    error = StatusDanger,
    onError = Color.White,
    errorContainer = StatusDangerBg,
    onErrorContainer = StatusDanger
)

private val GoPrimeDarkColors = darkColorScheme(
    primary = DarkPrimePrimary,
    onPrimary = Color(0xFF001C57),
    primaryContainer = DarkPrimeContainer,
    onPrimaryContainer = Color(0xFFDCE5FA),
    secondary = DarkCobaltSecondary,
    onSecondary = Color(0xFF07235C),
    secondaryContainer = DarkCobaltContainer,
    onSecondaryContainer = Color(0xFFE3ECFF),
    tertiary = Color(0xFFB6C4DC),
    onTertiary = Color(0xFF1A2538),
    background = DarkBackground,
    onBackground = DarkInkPrimary,
    surface = DarkSurface,
    onSurface = DarkInkPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkSecondary,
    outline = Color(0xFF2E3F66),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val GoPrimeShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalGoPrimeDarkTheme = compositionLocalOf { false }

@Composable
fun goPrimeBackgroundBrush(darkTheme: Boolean = LocalGoPrimeDarkTheme.current): Brush =
    remember(darkTheme) {
        if (darkTheme) {
            Brush.linearGradient(
                colors = listOf(DarkGradientStart, DarkGradientMid, DarkGradientEnd),
                start = Offset.Zero,
                end = Offset(1200f, 1800f)
            )
        } else {
            Brush.linearGradient(
                colors = listOf(SilverGradientStart, SilverGradientMid, SilverGradientEnd),
                start = Offset.Zero,
                end = Offset(1100f, 900f)
            )
        }
    }

@Composable
fun GoPrimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) GoPrimeDarkColors else GoPrimeLightColors
    CompositionLocalProvider(LocalGoPrimeDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GoPrimeTypography,
            shapes = GoPrimeShapes,
            content = content
        )
    }
}
