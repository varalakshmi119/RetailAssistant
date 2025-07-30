package com.retailassistant.ui.theme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
// DESIGN: A completely new, brand-focused color palette.
// It uses a professional blue as the primary color, with a warm amber for accents and calls to action.
// Neutrals are carefully selected for a clean, modern, and airy feel.
// --- Brand Colors ---
val BrandBlue = Color(0xFF4F46E5) // Primary Action Color (e.g., Indigo 600)
val BrandBlueContainer = Color(0xFFE0E7FF) // Light container for primary elements
val BrandAmber = Color(0xFFF59E0B) // Accent / Secondary Action Color (e.g., Amber 500)
val BrandAmberContainer = Color(0xFFFEF3C7)
val BrandGreen = Color(0xFF10B981) // Success State (e.g., Emerald 500)
val BrandGreenContainer = Color(0xFFD1FAE5)
val BrandRed = Color(0xFFEF4444) // Error State (e.g., Red 500)
val BrandRedContainer = Color(0xFFFEE2E2)
// --- Neutral Palette ---
val Slate900 = Color(0xFF0F172A) // Darkest text
val Slate700 = Color(0xFF334155) // Regular text
val Slate500 = Color(0xFF64748B) // Subdued text, icons
val Slate300 = Color(0xFFCBD5E1) // Borders
val Slate100 = Color(0xFFF1F5F9) // Light backgrounds, panels
val Slate50 = Color(0xFFF8FAFC) // Main app background
val White = Color(0xFFFFFFFF) // Card backgrounds
// --- Light Theme ---
val LightColorScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = White,
    primaryContainer = BrandBlueContainer,
    onPrimaryContainer = Color(0xFF312E81),
    secondary = BrandAmber,
    onSecondary = Slate900,
    secondaryContainer = BrandAmberContainer,
    onSecondaryContainer = Color(0xFFB45309),
    tertiary = BrandGreen,
    onTertiary = White,
    tertiaryContainer = BrandGreenContainer,
    onTertiaryContainer = Color(0xFF047857),
    error = BrandRed,
    onError = White,
    errorContainer = BrandRedContainer,
    onErrorContainer = Color(0xFFB91C1C),
    background = Slate50,
    onBackground = Slate900,
    surface = White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate500,
    outline = Slate300,
    outlineVariant = Slate100,
    scrim = Slate900.copy(alpha = 0.5f),
    surfaceContainer = Slate100,
)
// --- Dark Theme ---
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Slate900,
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = BrandBlueContainer,
    secondary = Color(0xFFFBBF24),
    onSecondary = Slate900,
    secondaryContainer = Color(0xFFB45309),
    onSecondaryContainer = BrandAmberContainer,
    tertiary = Color(0xFF34D399),
    onTertiary = Slate900,
    tertiaryContainer = Color(0xFF047857),
    onTertiaryContainer = BrandGreenContainer,
    error = Color(0xFFF87171),
    onError = Slate900,
    errorContainer = Color(0xFF991B1B),
    onErrorContainer = BrandRedContainer,
    background = Color(0xFF020617), // e.g., Slate 950
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate700,
    onSurfaceVariant = Slate300,
    outline = Slate700,
    outlineVariant = Color(0xFF1E293B), // e.g., Slate 800
    scrim = Slate900.copy(alpha = 0.5f),
    surfaceContainer = Color(0xFF1E293B)
)
// --- Gradients ---
data class GradientColors(val start: Color, val end: Color)
object AppGradients {
    val Primary = GradientColors(BrandBlue, Color(0xFF6366F1))
    val Error = GradientColors(BrandRed, Color(0xFFF87171))
    val Success = GradientColors(BrandGreen, Color(0xFF34D399))
    val Info = GradientColors(Color(0xFF8B5CF6), Color(0xFFA78BFA)) // Violet
    private val avatarGradients = listOf(
        GradientColors(Color(0xFF4F46E5), Color(0xFF818CF8)), // Indigo
        GradientColors(Color(0xFFD946EF), Color(0xFFF0ABFC)), // Fuchsia
        GradientColors(Color(0xFF0EA5E9), Color(0xFF7DD3FC)), // Sky
        GradientColors(Color(0xFFF59E0B), Color(0xFFFCD34D)), // Amber
        GradientColors(Color(0xFFEC4899), Color(0xFFF9A8D4)), // Pink
    )
    fun getGradientForName(name: String): GradientColors {
        val hash = abs(name.hashCode())
        return avatarGradients[hash % avatarGradients.size]
    }
}
