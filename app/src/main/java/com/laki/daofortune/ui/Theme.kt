package com.laki.daofortune.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF0B0E13)
val InkSurface = Color(0xFF141922)
val InkSurfaceHigh = Color(0xFF1C222D)
val Gold = Color(0xFFD9B36B)
val GoldSoft = Color(0xFF8E7442)
val Jade = Color(0xFF6FBFA0)
val Crimson = Color(0xFFD2705F)
val Amber = Color(0xFFE0A458)
val Parchment = Color(0xFFECE7DC)
val Muted = Color(0xFF9AA3AF)

private val DarkScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1A1408),
    primaryContainer = Color(0xFF3A2F17),
    onPrimaryContainer = Color(0xFFF1DDB4),
    secondary = Jade,
    onSecondary = Color(0xFF04201A),
    secondaryContainer = Color(0xFF1B3A33),
    onSecondaryContainer = Color(0xFFB6E7D5),
    tertiary = Amber,
    onTertiary = Color(0xFF2A1B06),
    background = Ink,
    onBackground = Parchment,
    surface = InkSurface,
    onSurface = Parchment,
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = Muted,
    outline = Color(0xFF2E3743),
    outlineVariant = Color(0xFF232A35),
    error = Crimson,
    onError = Color(0xFF2A0B08)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF8A6A2E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3E3C4),
    onPrimaryContainer = Color(0xFF2E230D),
    secondary = Color(0xFF2F7A62),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFAF7F0),
    onBackground = Color(0xFF1B1A17),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1A17),
    surfaceVariant = Color(0xFFEDE8DE),
    onSurfaceVariant = Color(0xFF5B564C),
    error = Color(0xFFA63A2B)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
)

@Composable
fun DaoFortuneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content
    )
}
