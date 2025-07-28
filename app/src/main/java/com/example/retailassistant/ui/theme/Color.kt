package com.example.retailassistant.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

object AppColors {
    // Primary gradient colors
    val PrimaryGradientStart = Color(0xFF4A90E2) // Brighter Blue
    val PrimaryGradientEnd = Color(0xFF50E3C2)   // Teal

    // Success gradient colors
    val SuccessGradientStart = Color(0xFF50C9C3) // Aqua
    val SuccessGradientEnd = Color(0xFF96DEDA)   // Light Aqua

    // Warning gradient colors
    val WarningGradientStart = Color(0xFFF5A623) // Orange
    val WarningGradientEnd = Color(0xFFF8E71C)   // Yellow

    // Error gradient colors
    val ErrorGradientStart = Color(0xFFD0021B)   // Strong Red
    val ErrorGradientEnd = Color(0xFFF5A623)     // Orange
}

// Gradient definitions
data class GradientColors(
    val start: Color,
    val end: Color
)

object AppGradients {
    val Primary = GradientColors(AppColors.PrimaryGradientStart, AppColors.PrimaryGradientEnd)
    val Success = GradientColors(AppColors.SuccessGradientStart, AppColors.SuccessGradientEnd)
    val Warning = GradientColors(AppColors.WarningGradientStart, AppColors.WarningGradientEnd)
    val Error = GradientColors(AppColors.ErrorGradientStart, AppColors.ErrorGradientEnd)

    private val avatarGradients = listOf(
        GradientColors(Color(0xFFFF9A8B), Color(0xFFFF6A88)), // Red-Pink
        GradientColors(Color(0xFF8BC6EC), Color(0xFF9599E2)), // Blue-Purple
        GradientColors(Color(0xFF96E6A1), Color(0xFFD4FC79)), // Green-Lime
        GradientColors(Color(0xFFFAD961), Color(0xFFF76B1C)), // Yellow-Orange
        GradientColors(Color(0xFFB224EF), Color(0xFF7579FF)), // Purple-Blue
        GradientColors(Color(0xFF43E97B), Color(0xFF38F9D7))  // Green-Cyan
    )

    fun getGradientForName(name: String): GradientColors {
        val hash = abs(name.hashCode())
        return avatarGradients[hash % avatarGradients.size]
    }
}
