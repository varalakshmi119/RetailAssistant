package com.example.retailassistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.shimmerBackground(shape: Shape = MaterialTheme.shapes.medium): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
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
            start = Offset.Zero,
            end = Offset(x = translateAnimation.value, y = translateAnimation.value)
        ),
        shape = shape
    ).clip(shape)
}

@Composable
fun ShimmeringInvoiceList(itemCount: Int = 5, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f).height(120.dp).shimmerBackground(MaterialTheme.shapes.large))
                Box(modifier = Modifier.weight(1f).height(120.dp).shimmerBackground(MaterialTheme.shapes.large))
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
        items(itemCount) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp).shimmerBackground(RoundedCornerShape(20.dp)))
        }
    }
}

@Composable
fun ShimmeringCustomerList(itemCount: Int = 7, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(56.dp).shimmerBackground(MaterialTheme.shapes.extraLarge))
        }
        items(itemCount) {
            Box(modifier = Modifier.fillMaxWidth().height(80.dp).shimmerBackground(RoundedCornerShape(20.dp)))
        }
    }
}
