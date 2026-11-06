package com.prism.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LocalIsTv = staticCompositionLocalOf { false }

object Palette {
    val Background = Color(0xFF05070D)
    val Accent = Color(0xFF7DD3FC)
    val AccentAlt = Color(0xFFA78BFA)
    val TextPrimary = Color(0xFFF5F7FF)
    val TextSecondary = Color(0xB3E6EAF5)
    val TextTertiary = Color(0x80E6EAF5)
    val Danger = Color(0xFFFCA5A5)
}

private val colors = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Color(0xFF04121C),
    secondary = Palette.AccentAlt,
    background = Palette.Background,
    surface = Color(0xFF0E1220),
    surfaceVariant = Color(0xFF1A2033),
    onBackground = Palette.TextPrimary,
    onSurface = Palette.TextPrimary,
    onSurfaceVariant = Palette.TextSecondary,
    error = Palette.Danger,
)

private val base = Typography()
private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun PrismTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
