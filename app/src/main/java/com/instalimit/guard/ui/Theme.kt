package com.instalimit.guard.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD5A0),
    onPrimary = Color(0xFF062317),
    primaryContainer = Color(0xFF123B27),
    onPrimaryContainer = Color(0xFFA6E9B9),
    secondary = Color(0xFFC9B7FF),
    onSecondary = Color(0xFF1B1038),
    background = Color(0xFF0B0F0D),
    onBackground = Color(0xFFE7EFE9),
    surface = Color(0xFF141A16),
    onSurface = Color(0xFFE7EFE9),
    surfaceVariant = Color(0xFF202A23),
    onSurfaceVariant = Color(0xFFB6C4BA),
    error = Color(0xFFFF8A80)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D52),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F1C8),
    onPrimaryContainer = Color(0xFF0B2C1A),
    secondary = Color(0xFF6746B5),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F8F6),
    onBackground = Color(0xFF142019),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF142019),
    surfaceVariant = Color(0xFFE8EFE9),
    onSurfaceVariant = Color(0xFF4A5B50),
    error = Color(0xFFB3261E)
)

@Composable
fun InstaLimitTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
