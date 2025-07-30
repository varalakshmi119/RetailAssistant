package com.retailassistant.ui.components.specific
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retailassistant.core.Utils
import com.retailassistant.data.db.Customer
import com.retailassistant.ui.components.common.ElevatedCard
import com.retailassistant.ui.theme.AppGradients
data class CustomerStats(val totalInvoices: Int, val unpaidAmount: Double, val overdueCount: Int)
@Composable
fun CustomerCard(
    customer: Customer,
    stats: CustomerStats,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    onEmailClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Avatar(name = customer.name)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = customer.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    InfoChip(
                        icon = Icons.Default.Receipt,
                        text = "${stats.totalInvoices} invoice${if (stats.totalInvoices != 1) "s" else ""}",
                    )
                }
                Row {
                    customer.phone?.let {
                        IconButton(onClick = onCallClick) { Icon(Icons.Default.Call, "Call ${customer.name}") }
                    }
                    customer.email?.let {
                        IconButton(onClick = onEmailClick) { Icon(Icons.Default.Email, "Email ${customer.name}") }
                    }
                }
            }
            if (stats.unpaidAmount > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Outstanding", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Utils.formatCurrency(stats.unpaidAmount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                    if (stats.overdueCount > 0) {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.errorContainer) {
                            Text(
                                "${stats.overdueCount} Overdue",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val gradient = AppGradients.getGradientForName(name)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors = listOf(gradient.start, gradient.end))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = Utils.getInitials(name),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = (size.value / 2.5).sp),
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}
