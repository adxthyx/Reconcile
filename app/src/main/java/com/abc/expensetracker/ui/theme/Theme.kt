package com.abc.expensetracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abc.expensetracker.ThemeMode

/** Reconcile 2.0's fixed dark palette from the Velocity Dark design system. */
object ReconcileColors {
    val Ink = Color(0xFF090C10)
    val Surface = Color(0xFF11161D)
    val Elevated = Color(0xFF171D25)
    val Border = Color(0xFF27303A)
    val Text = Color(0xFFF4F7F5)
    val TextSecondary = Color(0xFF8C98A5)
    val Mint = Color(0xFF65F2C3)
    val MintContainer = Color(0xFF064B3E)
    val Coral = Color(0xFFFF7A86)
    val CoralContainer = Color(0xFF4C1E28)
    val Amber = Color(0xFFFFC857)
    val AmberContainer = Color(0xFF493408)
    val Indigo = Color(0xFF7C8CFF)
    val IndigoContainer = Color(0xFF242B51)
}

object ReconcileSpacing {
    val Grid = 4.dp
    val Screen = 20.dp
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Xxxl = 32.dp
    val TouchTarget = 48.dp
}

object ReconcileRadii {
    val Small = 12.dp
    val Medium = 16.dp
    val Large = 20.dp
}

private val ReconcileDarkColors = darkColorScheme(
    primary = ReconcileColors.Mint,
    onPrimary = ReconcileColors.Ink,
    primaryContainer = ReconcileColors.MintContainer,
    onPrimaryContainer = ReconcileColors.Mint,
    secondary = ReconcileColors.Indigo,
    onSecondary = ReconcileColors.Ink,
    secondaryContainer = ReconcileColors.IndigoContainer,
    onSecondaryContainer = ReconcileColors.Indigo,
    tertiary = ReconcileColors.Amber,
    onTertiary = ReconcileColors.Ink,
    tertiaryContainer = ReconcileColors.AmberContainer,
    onTertiaryContainer = ReconcileColors.Amber,
    background = ReconcileColors.Ink,
    onBackground = ReconcileColors.Text,
    surface = ReconcileColors.Surface,
    onSurface = ReconcileColors.Text,
    surfaceVariant = ReconcileColors.Elevated,
    onSurfaceVariant = ReconcileColors.TextSecondary,
    outline = ReconcileColors.Border,
    outlineVariant = ReconcileColors.Border,
    error = ReconcileColors.Coral,
    onError = ReconcileColors.Ink,
    errorContainer = ReconcileColors.CoralContainer,
    onErrorContainer = ReconcileColors.Coral,
)

private val ReconcileTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    ),
)

private val ReconcileShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(ReconcileRadii.Small),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(ReconcileRadii.Medium),
    large = androidx.compose.foundation.shape.RoundedCornerShape(ReconcileRadii.Large),
)

/** Semantic money colors. Expenses remain neutral unless they indicate risk. */
object MoneyColors {
    val incomeLight = ReconcileColors.Mint
    val incomeDark = ReconcileColors.Mint
    val expenseLight = ReconcileColors.Coral
    val expenseDark = ReconcileColors.Coral
}

@Composable
fun incomeColor(): Color = ReconcileColors.Mint

@Composable
fun expenseColor(): Color = ReconcileColors.Coral

/**
 * Reconcile 2.0 is intentionally dark-only. [mode] remains in the signature so
 * existing DataStore values and callers stay binary/data compatible.
 */
@Composable
fun KharchaTheme(@Suppress("UNUSED_PARAMETER") mode: ThemeMode, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ReconcileDarkColors,
        typography = ReconcileTypography,
        shapes = ReconcileShapes,
        content = content,
    )
}
