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

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF50E3C2), // Teal
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF005143),
    onPrimaryContainer = Color(0xFF72FDE0),
    secondary = Color(0xFF8BC6EC), // Light Blue
    onSecondary = Color(0xFF00344B),
    secondaryContainer = Color(0xFF004C6B),
    onSecondaryContainer = Color(0xFFC8E6FF),
    tertiary = Color(0xFF96DEDA), // Aqua
    onTertiary = Color(0xFF003739),
    tertiaryContainer = Color(0xFF004F52),
    onTertiaryContainer = Color(0xFFB1F7F9),
    error = Color(0xFFF5A623), // Orange
    onError = Color(0xFF452700),
    errorContainer = Color(0xFFD0021B),
    onErrorContainer = Color(0xFFFFCDD2),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CE),
    outline = Color(0xFF8C9199)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006A59), // Darker Teal for contrast
    onPrimary = Color.White,
    primaryContainer = Color(0xFF72FDE0),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF006492), // Darker Blue
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E6FF),
    onSecondaryContainer = Color(0xFF001E2F),
    tertiary = Color(0xFF00696D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB1F7F9),
    onTertiaryContainer = Color(0xFF002021),
    error = Color(0xFFD0021B), // Strong Red
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FF), // Off-white
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDEE3EB),
    onSurfaceVariant = Color(0xFF42474E),
    outline = Color(0xFF72787E)
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
