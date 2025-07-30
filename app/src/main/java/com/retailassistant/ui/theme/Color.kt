package com.retailassistant.ui.theme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
// Modern color palette inspired by TailwindCSS colors
val Slate = Color(0xFF2D3748)
val LightSlate = Color(0xFF4A5568)
val Steel = Color(0xFF718096)
val OffWhite = Color(0xFFF7FAFC)
val White = Color(0xFFFFFFFF)
val Coral = Color(0xFFF56565)
val Teal = Color(0xFF38B2AC)
val Amber = Color(0xFFED8936)
val Purple = Color(0xFF805AD5)
val Green = Color(0xFF48BB78)
val LightColorScheme = lightColorScheme(
    primary = Teal,
    onPrimary = White,
    primaryContainer = Color(0xFFB2F5EA),
    onPrimaryContainer = Color(0xFF234E4A),
    secondary = Purple,
    onSecondary = White,
    secondaryContainer = Color(0xFFE9D8FD),
    onSecondaryContainer = Color(0xFF201A23),
    tertiary = Green,
    onTertiary = White,
    tertiaryContainer = Color(0xFFC6F6D5),
    onTertiaryContainer = Color(0xFF213429),
    error = Coral,
    onError = White,
    errorContainer = Color(0xFFFED7D7),
    onErrorContainer = Color(0xFF552828),
    background = OffWhite,
    onBackground = Slate,
    surface = White,
    onSurface = Slate,
    surfaceVariant = Color(0xFFEDF2F7),
    onSurfaceVariant = LightSlate,
    surfaceContainer = Color(0xFFF0F4F8),
    outline = Color(0xFFCBD5E0),
    outlineVariant = Color(0xFFE2E8F0)
)
val DarkColorScheme = darkColorScheme(
    primary = Teal,
    onPrimary = White,
    primaryContainer = Color(0xFF285E61),
    onPrimaryContainer = Color(0xFFB2F5EA),
    secondary = Purple,
    onSecondary = White,
    secondaryContainer = Color(0xFF553C9A),
    onSecondaryContainer = Color(0xFFE9D8FD),
    tertiary = Green,
    onTertiary = White,
    tertiaryContainer = Color(0xFF2F855A),
    onTertiaryContainer = Color(0xFFC6F6D5),
    error = Coral,
    onError = White,
    errorContainer = Color(0xFF9B2C2C),
    onErrorContainer = Color(0xFFFED7D7),
    background = Color(0xFF1A202C),
    onBackground = OffWhite,
    surface = Slate,
    onSurface = OffWhite,
    surfaceVariant = LightSlate,
    onSurfaceVariant = Steel,
    surfaceContainer = Color(0xFF273041),
    outline = LightSlate,
    outlineVariant = Color(0xFF2D3748)
)
data class GradientColors(val start: Color, val end: Color)
object AppGradients {
    val Primary = GradientColors(Teal, Color(0xFF319795))
    val Warning = GradientColors(Amber, Color(0xFFDD6B20))
    val Error = GradientColors(Coral, Color(0xFFC53030))
    val Success = GradientColors(Green, Color(0xFF38A169))
    val Info = GradientColors(Purple, Color(0xFF6B46C1))
    private val avatarGradients = listOf(
        GradientColors(Color(0xFF81E6D9), Color(0xFF4FD1C5)), // Teal
        GradientColors(Color(0xFFB794F4), Color(0xFF9F7AEA)), // Purple
        GradientColors(Color(0xFFF6AD55), Color(0xFFF687B3)), // Orange-Pink
        GradientColors(Color(0xFF76E4F7), Color(0xFF4299E1)), // Cyan-Blue
        GradientColors(Color(0xFF68D391), Color(0xFF48BB78)), // Green
    )
    fun getGradientForName(name: String): GradientColors {
        val hash = abs(name.hashCode())
        return avatarGradients[hash % avatarGradients.size]
    }
}
