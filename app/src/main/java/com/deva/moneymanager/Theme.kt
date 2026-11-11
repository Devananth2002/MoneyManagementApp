package com.deva.moneymanager

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

val Good = Color(0xFF16A34A)

val PALETTE = listOf(
    Color(0xFF0F766E), Color(0xFF4F46E5), Color(0xFFF59E0B), Color(0xFFDB2777), Color(0xFF0EA5E9),
    Color(0xFF84CC16), Color(0xFFEF4444), Color(0xFF8B5CF6), Color(0xFF64748B)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E), onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1), onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF4F46E5), background = Color(0xFFF4F6F8), surface = Color.White,
    onSurface = Color(0xFF111827), surfaceVariant = Color(0xFFEEF0F3), onSurfaceVariant = Color(0xFF6B7280),
    error = Color(0xFFDC2626), errorContainer = Color(0xFFFEE2E2), onErrorContainer = Color(0xFF7F1D1D),
    outlineVariant = Color(0xFFE5E7EB)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2DD4BF), onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF134E4A), onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF818CF8), background = Color(0xFF0E1117), surface = Color(0xFF171B22),
    onSurface = Color(0xFFE5E7EB), surfaceVariant = Color(0xFF232832), onSurfaceVariant = Color(0xFF9CA3AF),
    error = Color(0xFFF87171), errorContainer = Color(0xFF450A0A), onErrorContainer = Color(0xFFFECACA),
    outlineVariant = Color(0xFF2B313B)
)

private val Type = Typography().let {
    it.copy(
        headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = it.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    )
}

// "system" follows the phone, "light" is day mode, "dark" is night mode
var themeMode by mutableStateOf("system")

private fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    // Keep the clock, battery and navigation icons readable on the chosen background
    SideEffect {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = Type, content = content)
}
