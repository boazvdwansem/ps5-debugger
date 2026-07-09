package com.osr.ps5debugger
 
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.util.DefaultIpHelper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Curated Sleek Developer Theme Color Palette
object PS5ThemeColors {
    var activeTheme by mutableStateOf(try { DefaultIpHelper.getTheme() } catch (_: Exception) { "Dark" })

    val DarkBg: Color get() = when(activeTheme) {
        "Light" -> Color(0xFFF6F8FA)
        "Solarized" -> Color(0xFFFDF6E3)
        else -> Color(0xFF0C0E14)
    }
    
    val Surface: Color get() = when(activeTheme) {
        "Light" -> Color(0xFFFFFFFF)
        "Solarized" -> Color(0xFFEEE8D5)
        else -> Color(0xFF161B22)
    }
    
    val SecondaryBg: Color get() = when(activeTheme) {
        "Light" -> Color(0xFFF0F2F5)
        "Solarized" -> Color(0xFFECE4D9)
        else -> Color(0xFF21262D)
    }
    
    val BorderColor: Color get() = when(activeTheme) {
        "Light" -> Color(0xFFD0D7DE)
        "Solarized" -> Color(0xFFD3C7B1)
        else -> Color(0xFF30363D)
    }
    
    val AccentCyan: Color get() = when(activeTheme) {
        "Light" -> Color(0xFF0969DA)
        "Solarized" -> Color(0xFF2AA198)
        else -> Color(0xFF00D2FF)
    }
    
    val AccentAmber: Color get() = when(activeTheme) {
        "Light" -> Color(0xFFD97706)
        "Solarized" -> Color(0xFFB58900)
        else -> Color(0xFFFEC260)
    }
    
    val TextMain: Color get() = when(activeTheme) {
        "Light" -> Color(0xFF24292F)
        "Solarized" -> Color(0xFF586E75)
        else -> Color(0xFFC9D1D9)
    }
    
    val TextMuted: Color get() = when(activeTheme) {
        "Light" -> Color(0xFF57606A)
        "Solarized" -> Color(0xFF93A1A1)
        else -> Color(0xFF8B949E)
    }
    
    val StatusGreen: Color get() = when(activeTheme) {
        "Light" -> Color(0xFF1A7F37)
        "Solarized" -> Color(0xFF859900)
        else -> Color(0xFF39D353)
    }
    
    val StatusRed: Color get() = when(activeTheme) {
        "Light" -> Color(0xFFCF222E)
        "Solarized" -> Color(0xFFDC322F)
        else -> Color(0xFFF85149)
    }
}

@Composable
fun Ps5DebuggerTheme(
    content: @Composable () -> Unit
) {
    val dynamicColorScheme = if (PS5ThemeColors.activeTheme == "Light") {
        lightColorScheme(
            primary = PS5ThemeColors.AccentCyan,
            secondary = PS5ThemeColors.SecondaryBg,
            background = PS5ThemeColors.DarkBg,
            surface = PS5ThemeColors.Surface,
            surfaceVariant = PS5ThemeColors.Surface,
            onPrimary = Color.White,
            onSecondary = PS5ThemeColors.TextMain,
            onBackground = PS5ThemeColors.TextMain,
            onSurface = PS5ThemeColors.TextMain,
            error = PS5ThemeColors.StatusRed,
            onError = Color.White
        )
    } else {
        darkColorScheme(
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
    }

    MaterialTheme(
        colorScheme = dynamicColorScheme,
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
