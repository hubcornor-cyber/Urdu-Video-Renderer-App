package com.example.videorenderer.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Simple Color Scheme
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF10B981),      // Emerald green
    secondary = Color(0xFFF59E0B),    // Amber
    tertiary = Color(0xFF6B7280),     // Gray
    background = Color(0xFF0F172A),   // Dark blue-black
    surface = Color(0xFF1E293B),      // Dark gray
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color(0xFF1F2937), // Light text
    onSurface = Color(0xFF1F2937),
    error = Color(0xFFEF4444)         // Red
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF10B981),
    secondary = Color(0xFFF59E0B),
    tertiary = Color(0xFF6B7280),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF3F4F6),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color(0xFF1F2937),
    onSurface = Color(0xFF1F2937),
    error = Color(0xFFEF4444)
)

@Composable
fun UrduVideoRendererTheme(
    darkTheme: Boolean = true, // Default dark theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
