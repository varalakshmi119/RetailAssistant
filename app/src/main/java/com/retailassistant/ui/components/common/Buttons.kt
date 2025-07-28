package com.retailassistant.ui.components.common

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.retailassistant.ui.theme.AppGradients
import com.retailassistant.ui.theme.GradientColors

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: GradientColors = AppGradients.Primary,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val brush = if (enabled) {
        Brush.horizontalGradient(colors = listOf(gradient.start, gradient.end))
    } else {
        Brush.horizontalGradient(colors = listOf(Color.Gray.copy(alpha = 0.5f), Color.DarkGray.copy(alpha = 0.5f)))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(brush, shape = MaterialTheme.shapes.medium)
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Using a custom or no ripple
                enabled = enabled && !isLoading,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isLoading,
            transitionSpec = { (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut()) },
            label = "GradientButtonLoading"
        ) { loading ->
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    icon?.let { Icon(imageVector = it, contentDescription = null, tint = Color.White) }
                    Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(icon, contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}
