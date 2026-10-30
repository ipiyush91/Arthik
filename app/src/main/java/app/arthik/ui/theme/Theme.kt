package app.arthik.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Single accent token: change these two colours to re-theme the app. */
object Accent {
    val primary = Color(0xFF2DD4BF) // teal
    val secondary = Color(0xFFA78BFA) // violet
}

@Immutable
data class GlassColors(
    val background: Color = Color(0xFF0B0B12),
    val fill: Color = Color.White.copy(alpha = 0.07f),
    val fillStrong: Color = Color.White.copy(alpha = 0.11f),
    val border: Color = Color.White.copy(alpha = 0.15f),
    val sheen: Color = Color.White.copy(alpha = 0.10f),
    val textPrimary: Color = Color(0xFFF4F4F8),
    val textSecondary: Color = Color(0xFFB4B4C4), // ≥4.5:1 on the glass fill over the background
    val positive: Color = Color(0xFF4ADE80),
    val negative: Color = Color(0xFFFB7185),
    val warning: Color = Color(0xFFFBBF24),
    val blobs: List<Color> = listOf(Color(0xFF0F766E), Color(0xFF6D28D9), Color(0xFF1D4ED8)),
)

val LocalGlass = staticCompositionLocalOf { GlassColors() }

private val scheme = darkColorScheme(
    primary = Accent.primary,
    onPrimary = Color(0xFF00201C),
    secondary = Accent.secondary,
    onSecondary = Color(0xFF1E0B47),
    background = Color(0xFF0B0B12),
    onBackground = Color(0xFFF4F4F8),
    surface = Color(0xFF14141E),
    onSurface = Color(0xFFF4F4F8),
    surfaceVariant = Color(0xFF1C1C28),
    onSurfaceVariant = Color(0xFFB4B4C4),
    surfaceContainer = Color(0xFF181824),
    surfaceContainerHigh = Color(0xFF1E1E2C),
    surfaceContainerHighest = Color(0xFF242434),
    outline = Color(0x40FFFFFF),
    error = Color(0xFFFB7185),
)

private val base = Typography()
private val rounded = FontFamily.Default

private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = rounded, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = rounded, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = rounded, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = rounded, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = rounded, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(fontFamily = rounded),
    bodyMedium = base.bodyMedium.copy(fontFamily = rounded),
    labelLarge = base.labelLarge.copy(fontFamily = rounded, fontWeight = FontWeight.Medium),
)

/** Large tabular figures for amounts. */
val AmountStyle = TextStyle(fontFamily = rounded, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, letterSpacing = (-0.5).sp,
    fontFeatureSettings = "tnum")

@Composable
fun ArthikTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
