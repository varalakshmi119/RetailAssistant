package com.example.retailassistant.ui.theme

import androidx.compose.ui.graphics.Color

object AppColors {
    // Primary gradient colors
    val PrimaryGradientStart = Color(0xFF1967D2)
    val PrimaryGradientEnd = Color(0xFF4285F4)

    // Secondary gradient colors (success)
    val SecondaryGradientStart = Color(0xFF2E7D32)
    val SecondaryGradientEnd = Color(0xFF4CAF50)

    // Error gradient colors
    val ErrorGradientStart = Color(0xFFC62828)
    val ErrorGradientEnd = Color(0xFFE53935)
}

// Gradient definitions
data class GradientColors(
    val start: Color,
    val end: Color
)

object AppGradients {
    val Primary = GradientColors(AppColors.PrimaryGradientStart, AppColors.PrimaryGradientEnd)
    val Secondary = GradientColors(AppColors.SecondaryGradientStart, AppColors.SecondaryGradientEnd)
    val Error = GradientColors(AppColors.ErrorGradientStart, AppColors.ErrorGradientEnd)
}
