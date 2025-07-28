package com.retailassistant.ui.components.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.shimmerBackground(shape: Shape = MaterialTheme.shapes.medium): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation = transition.animateFloat(
        initialValue = -500f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer-translate"
    )
    val shimmerColor = MaterialTheme.colorScheme.surfaceVariant
    val shimmerColors = listOf(
        shimmerColor.copy(0.5f),
        shimmerColor.copy(0.8f),
        shimmerColor.copy(0.5f),
    )
    background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnimation.value - 500, translateAnimation.value - 500),
            end = Offset(translateAnimation.value, translateAnimation.value)
        ),
        shape = shape
    )
}

@Composable
fun ShimmeringList(modifier: Modifier = Modifier, itemCount: Int = 5) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(itemCount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .shimmerBackground(MaterialTheme.shapes.large)
            )
        }
    }
}
