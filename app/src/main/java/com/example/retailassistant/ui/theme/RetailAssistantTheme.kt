package com.example.retailassistant.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Enhanced professional and modern color scheme with better contrast and accessibility
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF82B1FF), // Lighter blue for better contrast on dark background
    onPrimary = Color(0xFF002245),
    primaryContainer = Color(0xFF004A97),
    onPrimaryContainer = Color(0xFFD4E3FF),

    secondary = Color(0xFF81C784), // Lighter green
    onSecondary = Color(0xFF003913),
    secondaryContainer = Color(0xFF2E7D32),
    onSecondaryContainer = Color(0xFFC8E6C9),

    tertiary = Color(0xFFFFB74D), // Lighter orange
    onTertiary = Color(0xFF452700),
    tertiaryContainer = Color(0xFF6B4F00),
    onTertiaryContainer = Color(0xFFFFE0B2),

    error = Color(0xFFFF8A80), // Lighter red
    onError = Color(0xFF410002),
    errorContainer = Color(0xFFC62828),
    onErrorContainer = Color(0xFFFFCDD2),

    background = Color(0xFF121212),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF1E1E1E), // Slightly elevated surface
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF2D2D2D), // For cards and text fields
    onSurfaceVariant = Color(0xFFC8C8C8),
    outline = Color(0xFF909090),
    surfaceContainer = Color(0xFF212121),
    surfaceContainerHigh = Color(0xFF292929)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1967D2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4E3FF),
    onPrimaryContainer = Color(0xFF001D36),

    secondary = Color(0xFF2E7D32),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E6C9),
    onSecondaryContainer = Color(0xFF00220A),

    tertiary = Color(0xFFF57C00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0B2),
    onTertiaryContainer = Color(0xFF271A00),

    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF0F0F0), // Lighter variant for cards/inputs
    onSurfaceVariant = Color(0xFF454547),
    outline = Color(0xFF757577),
    surfaceContainer = Color(0xFFF5F5F5),
    surfaceContainerHigh = Color(0xFFECECEC)
)

@Composable
fun RetailAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, false)

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
