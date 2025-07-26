package com.example.retailassistant.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF63B3ED),
    onPrimary = Color(0xFF003355),
    primaryContainer = Color(0xFF004A77),
    onPrimaryContainer = Color(0xFFD0E6FF),
    secondary = Color(0xFF68D391),
    onSecondary = Color(0xFF00391C),
    secondaryContainer = Color(0xFF00522A),
    onSecondaryContainer = Color(0xFF8CFABE),
    tertiary = Color(0xFFF6AD55),
    onTertiary = Color(0xFF4A2A00),
    tertiaryContainer = Color(0xFF6A3F00),
    onTertiaryContainer = Color(0xFFFFDCBB),
    error = Color(0xFFFC8181),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A202C),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF2D3748),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF4A5568),
    onSurfaceVariant = Color(0xFFCBD5E0),
    outline = Color(0xFF718096)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3182CE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBEE3F8),
    onPrimaryContainer = Color(0xFF001D35),
    secondary = Color(0xFF38A169),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC6F6D5),
    onSecondaryContainer = Color(0xFF002112),
    tertiary = Color(0xFFDD6B20),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEEBC8),
    onTertiaryContainer = Color(0xFF2C1A00),
    error = Color(0xFFE53E3E),
    onError = Color.White,
    errorContainer = Color(0xFFFED7D7),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7FAFC),
    onBackground = Color(0xFF1A202C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A202C),
    surfaceVariant = Color(0xFFEDF2F7),
    onSurfaceVariant = Color(0xFF4A5568),
    outline = Color(0xFF718096)
)

@Composable
fun RetailAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
