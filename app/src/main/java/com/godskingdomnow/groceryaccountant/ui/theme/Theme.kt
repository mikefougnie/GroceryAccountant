package com.godskingdomnow.groceryaccountant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/* Ledger green and wheat gold: calm, money-minded, easy to read in a store. */
private val Light = lightColorScheme(
    primary = Color(0xFF1F5C45), onPrimary = Color.White,
    primaryContainer = Color(0xFFC6EBD9), onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF8A5A12), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E2BE), onSecondaryContainer = Color(0xFF2C1800),
    tertiary = Color(0xFF2F5A9E), onTertiary = Color.White,
    background = Color(0xFFF6F5EF), onBackground = Color(0xFF1B1C18),
    surface = Color(0xFFF6F5EF), onSurface = Color(0xFF1B1C18),
    surfaceVariant = Color(0xFFE0E4DB), onSurfaceVariant = Color(0xFF43483F),
    surfaceContainer = Color(0xFFEEEEE6), surfaceContainerHigh = Color(0xFFE8E8E0),
    outline = Color(0xFF73796E), outlineVariant = Color(0xFFC3C8BC),
    error = Color(0xFFB3261E),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8FD5B5), onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF075137), onPrimaryContainer = Color(0xFFAAF2D0),
    secondary = Color(0xFFF0C27A), onSecondary = Color(0xFF462B00),
    secondaryContainer = Color(0xFF654000), onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Color(0xFFAFC6FF), onTertiary = Color(0xFF002E69),
    background = Color(0xFF121512), onBackground = Color(0xFFE2E3DC),
    surface = Color(0xFF121512), onSurface = Color(0xFFE2E3DC),
    surfaceVariant = Color(0xFF43483F), onSurfaceVariant = Color(0xFFC3C8BC),
    surfaceContainer = Color(0xFF1E211D), surfaceContainerHigh = Color(0xFF282B27),
    outline = Color(0xFF8D9387), outlineVariant = Color(0xFF43483F),
    error = Color(0xFFF2B8B5),
)

/** Colours for the benefit labels, the same meaning in light and dark. */
@Immutable
data class BenefitColors(val wic: Color, val wicSoft: Color, val snap: Color, val snapSoft: Color, val coupon: Color, val couponSoft: Color)

private val LightBenefit = BenefitColors(Color(0xFF0F766E), Color(0xFFD7F0EC), Color(0xFF1D4ED8), Color(0xFFDFE8FD), Color(0xFF8A5A12), Color(0xFFFBEFD0))
private val DarkBenefit = BenefitColors(Color(0xFF5EEAD4), Color(0xFF123A35), Color(0xFF93B4FD), Color(0xFF1B2847), Color(0xFFFBBF24), Color(0xFF3A2E10))

val LocalBenefitColors = staticCompositionLocalOf { LightBenefit }

@Composable
fun GroceryTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalBenefitColors provides if (dark) DarkBenefit else LightBenefit) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}
