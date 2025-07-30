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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.retailassistant.core.Utils
import com.retailassistant.data.db.InteractionLog
import com.retailassistant.data.db.InteractionType
import com.retailassistant.data.db.Invoice
import com.retailassistant.ui.components.common.ElevatedCard
@Composable
fun InvoiceCard(
    invoice: Invoice,
    customerName: String,
    friendlyDueDate: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPaid = invoice.balanceDue <= 0.0
    val statusColor = when {
        invoice.isOverdue -> MaterialTheme.colorScheme.error
        isPaid -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    ElevatedCard(modifier = modifier, onClick = onClick) {
        Row {
            // Status Indicator
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(4.dp)
                    .height(50.dp) // Adjust height as needed
                    .clip(MaterialTheme.shapes.small)
                    .background(statusColor)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp), // Less end padding to accommodate icon
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: Customer and Due Date
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = customerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                        InfoChip(icon = Icons.Default.Schedule, text = "Due: $friendlyDueDate")
                    }
                }
                // Right side: Amount and Arrow
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = Utils.formatCurrency(if (isPaid) invoice.totalAmount else invoice.balanceDue),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isPaid) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isPaid) "Paid" else "Due",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "View Invoice",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
@Composable
fun InfoChip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = LocalContentColor.current
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current
        )
    }
}
@Composable
fun InteractionLogItem(log: InteractionLog, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        val (icon, title, valueInfo) = when (log.type) {
            InteractionType.PAYMENT -> Triple(
                Icons.Default.Payment,
                "Payment Received",
                "+${Utils.formatCurrency(log.value ?: 0.0)}" to MaterialTheme.colorScheme.tertiary
            )
            InteractionType.NOTE -> Triple(
                Icons.AutoMirrored.Filled.Comment,
                "Note Added",
                null
            )
            InteractionType.DUE_DATE_CHANGED -> Triple(
                Icons.Default.DateRange,
                "Due Date Changed",
                null
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = log.type.name,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp).size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                valueInfo?.let { (text, color) ->
                    Text(text, fontWeight = FontWeight.Bold, color = color, style = MaterialTheme.typography.bodyMedium)
                }
            }
            log.notes?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = Utils.formatTimestamp(log.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
