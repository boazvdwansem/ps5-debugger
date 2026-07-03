package com.osr.ps5debugger

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Curated Sleek Developer Theme Color Palette
object PS5ThemeColors {
    val DarkBg = Color(0xFF0C0E14)          // Deep Charcoal-Black
    val Surface = Color(0xFF161B22)         // Matte Dark Blue-Gray card surface
    val SecondaryBg = Color(0xFF21262D)     // Border/contrast panel background
    val BorderColor = Color(0xFF30363D)     // Clean separating line gray
    
    val AccentCyan = Color(0xFF00D2FF)      // Tech Neon Cyan accent
    val AccentAmber = Color(0xFFFEC260)     // Warning/highlight amber
    
    val TextMain = Color(0xFFC9D1D9)        // Muted white text
    val TextMuted = Color(0xFF8B949E)       // Gray text
    
    val StatusGreen = Color(0xFF39D353)     // Success green
    val StatusRed = Color(0xFFF85149)       // Error red
}

private val ModernDarkColorScheme = darkColorScheme(
    primary = PS5ThemeColors.AccentCyan,
    secondary = PS5ThemeColors.SecondaryBg,
    background = PS5ThemeColors.DarkBg,
    surface = PS5ThemeColors.Surface,
    surfaceVariant = PS5ThemeColors.Surface,
    surfaceContainer = PS5ThemeColors.Surface,
    surfaceContainerLow = PS5ThemeColors.Surface,
    surfaceContainerHigh = PS5ThemeColors.SecondaryBg,
    surfaceContainerLowest = PS5ThemeColors.DarkBg,
    surfaceContainerHighest = PS5ThemeColors.SecondaryBg,
    onPrimary = Color.Black,
    onSecondary = PS5ThemeColors.TextMain,
    onBackground = PS5ThemeColors.TextMain,
    onSurface = PS5ThemeColors.TextMain,
    onSurfaceVariant = PS5ThemeColors.TextMain,
    error = PS5ThemeColors.StatusRed,
    onError = Color.White
)

@Composable
fun Ps5DebuggerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ModernDarkColorScheme,
        typography = Typography(
            titleMedium = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 0.5.sp
            ),
            bodyLarge = TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp
            )
        ),
        content = content
    )
}
