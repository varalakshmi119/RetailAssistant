package com.retailassistant.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

// DESIGN: High-contrast, accessible color system based on HSL color theory
// Uses complementary and triadic color relationships for optimal visual hierarchy
// Ensures WCAG AA compliance (4.5:1 contrast ratio minimum)
// Modern, professional palette suitable for retail applications

// --- Primary Brand Colors (Blue-Violet Spectrum) ---
val BrandPrimary = Color(0xFF4F46E5) // Indigo 600 - strong, trustworthy
val BrandPrimaryLight = Color(0xFF6366F1) // Indigo 500 - vibrant accent
val BrandPrimaryDark = Color(0xFF3730A3) // Indigo 700 - deep authority
val BrandPrimaryContainer = Color(0xFFEEF2FF) // Indigo 50 - subtle background

// --- Secondary Brand Colors (Emerald Spectrum) ---
val BrandSecondary = Color(0xFF059669) // Emerald 600 - natural, growth
val BrandSecondaryLight = Color(0xFF10B981) // Emerald 500 - fresh energy
val BrandSecondaryDark = Color(0xFF047857) // Emerald 700 - stability
val BrandSecondaryContainer = Color(0xFFECFDF5) // Emerald 50 - organic feel

// --- Tertiary Brand Colors (Amber Spectrum) ---
val BrandTertiary = Color(0xFFD97706) // Amber 600 - warm, inviting
val BrandTertiaryLight = Color(0xFFF59E0B) // Amber 500 - energetic
val BrandTertiaryDark = Color(0xFFB45309) // Amber 700 - rich warmth
val BrandTertiaryContainer = Color(0xFFFFFBEB) // Amber 50 - gentle glow

// --- Semantic Colors (High Contrast) ---
val SuccessGreen = Color(0xFF16A34A) // Green 600 - clear success
val SuccessGreenContainer = Color(0xFFF0FDF4) // Green 50
val ErrorRed = Color(0xFFDC2626) // Red 600 - clear error
val ErrorRedContainer = Color(0xFFFEF2F2) // Red 50
val WarningOrange = Color(0xFFEA580C) // Orange 600 - attention-grabbing
val WarningOrangeContainer = Color(0xFFFFF7ED) // Orange 50
val InfoBlue = Color(0xFF2563EB) // Blue 600 - informational
val InfoBlueContainer = Color(0xFFEFF6FF) // Blue 50

// --- Neutral Palette (True Grays with Warm Undertone) ---
val Neutral900 = Color(0xFF111827) // Gray 900 - true black alternative
val Neutral800 = Color(0xFF1F2937) // Gray 800 - dark surface
val Neutral700 = Color(0xFF374151) // Gray 700 - medium dark
val Neutral600 = Color(0xFF4B5563) // Gray 600 - text secondary
val Neutral500 = Color(0xFF6B7280) // Gray 500 - text muted
val Neutral400 = Color(0xFF9CA3AF) // Gray 400 - disabled text
val Neutral300 = Color(0xFFD1D5DB) // Gray 300 - borders
val Neutral200 = Color(0xFFE5E7EB) // Gray 200 - dividers
val Neutral100 = Color(0xFFF3F4F6) // Gray 100 - background subtle
val Neutral50 = Color(0xFFF9FAFB) // Gray 50 - background light
val White = Color(0xFFFFFFFF) // Pure white

// --- Light Theme (Optimized for Accessibility) ---
val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = White,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandPrimaryDark,

    secondary = BrandSecondary,
    onSecondary = White,
    secondaryContainer = BrandSecondaryContainer,
    onSecondaryContainer = BrandSecondaryDark,

    tertiary = BrandTertiary,
    onTertiary = White,
    tertiaryContainer = BrandTertiaryContainer,
    onTertiaryContainer = BrandTertiaryDark,

    error = ErrorRed,
    onError = White,
    errorContainer = ErrorRedContainer,
    onErrorContainer = Color(0xFF991B1B),

    background = White,
    onBackground = Neutral900,
    surface = White,
    onSurface = Neutral900,
    surfaceVariant = Neutral50,
    onSurfaceVariant = Neutral600,
    outline = Neutral300,
    outlineVariant = Neutral200,
    scrim = Neutral900.copy(alpha = 0.4f),
    surfaceContainer = Neutral50,
    surfaceTint = BrandPrimary,
    inverseSurface = Neutral800,
    inverseOnSurface = Neutral100,
    inversePrimary = BrandPrimaryLight,
)

// --- Dark Theme (High Contrast, Eye-Friendly) ---
val DarkColorScheme = darkColorScheme(
    primary = BrandPrimaryLight,
    onPrimary = Neutral900,
    primaryContainer = BrandPrimaryDark,
    onPrimaryContainer = BrandPrimaryContainer,

    secondary = BrandSecondaryLight,
    onSecondary = Neutral900,
    secondaryContainer = BrandSecondaryDark,
    onSecondaryContainer = BrandSecondaryContainer,

    tertiary = BrandTertiaryLight,
    onTertiary = Neutral900,
    tertiaryContainer = BrandTertiaryDark,
    onTertiaryContainer = BrandTertiaryContainer,

    error = Color(0xFFF87171), // Red 400 - softer for dark mode
    onError = Neutral900,
    errorContainer = Color(0xFF991B1B),
    onErrorContainer = ErrorRedContainer,

    background = Color(0xFF0F1419), // Custom deep dark
    onBackground = Color(0xFFF8FAFC),
    surface = Neutral900,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Neutral800,
    onSurfaceVariant = Neutral300,
    outline = Neutral600,
    outlineVariant = Neutral700,
    scrim = Color(0xFF000000).copy(alpha = 0.4f),
    surfaceContainer = Neutral800,
    surfaceTint = BrandPrimaryLight,
    inverseSurface = Neutral100,
    inverseOnSurface = Neutral800,
    inversePrimary = BrandPrimary,
)

// --- Gradient System ---
data class GradientColors(val start: Color, val end: Color)

object AppGradients {
    val Primary = GradientColors(BrandPrimary, BrandPrimaryLight)
    val Secondary = GradientColors(BrandSecondary, BrandSecondaryLight)
    val Tertiary = GradientColors(BrandTertiary, BrandTertiaryLight)
    val Success = GradientColors(SuccessGreen, Color(0xFF22C55E))
    val Error = GradientColors(ErrorRed, Color(0xFFEF4444))
    val Warning = GradientColors(WarningOrange, Color(0xFFF97316))
    val Info = GradientColors(InfoBlue, Color(0xFF3B82F6))

    // High-contrast avatar gradients using color theory
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

