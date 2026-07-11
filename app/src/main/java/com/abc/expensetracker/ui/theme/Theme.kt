package com.abc.expensetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.abc.expensetracker.ThemeMode

// Reconcile brand: blue + white (light) / blue + near-black (dark).
// Matches the navy/gold launcher mark; dynamic color intentionally off.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1A63C8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E7FB),
    onPrimaryContainer = Color(0xFF0A2A55),
    secondary = Color(0xFF52607A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE3F5),
    onSecondaryContainer = Color(0xFF101C33),
    tertiary = Color(0xFF9A7B2D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121722),
    surfaceVariant = Color(0xFFE4EAF4),
    onSurfaceVariant = Color(0xFF43506A),
    background = Color(0xFFF5F8FD),
    outline = Color(0xFF6E7B94),
    outlineVariant = Color(0xFFC9D3E4),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB8F8),
    onPrimary = Color(0xFF06264F),
    primaryContainer = Color(0xFF14335F),
    onPrimaryContainer = Color(0xFFD3E4FC),
    secondary = Color(0xFFB9C6DD),
    onSecondary = Color(0xFF232F45),
    secondaryContainer = Color(0xFF2B3852),
    onSecondaryContainer = Color(0xFFD8E2F5),
    tertiary = Color(0xFFE3C36C),
    surface = Color(0xFF0A0E15),
    onSurface = Color(0xFFE4E9F2),
    surfaceVariant = Color(0xFF222B3C),
    onSurfaceVariant = Color(0xFFAAB6CB),
    background = Color(0xFF0A0E15),
    outline = Color(0xFF7E8BA3),
    outlineVariant = Color(0xFF323D52),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF5C1D1A),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** Semantic money colors, theme-aware. */
object MoneyColors {
    val incomeLight = Color(0xFF099268)
    val incomeDark = Color(0xFF38D9A9)
    val expenseLight = Color(0xFFC2255C)
    val expenseDark = Color(0xFFF783AC)
}

@Composable
fun incomeColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) MoneyColors.incomeDark else MoneyColors.incomeLight

@Composable
fun expenseColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) MoneyColors.expenseDark else MoneyColors.expenseLight

private fun Color.luminance(): Float = (0.2126f * red + 0.7152f * green + 0.0722f * blue)

@Composable
fun KharchaTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
