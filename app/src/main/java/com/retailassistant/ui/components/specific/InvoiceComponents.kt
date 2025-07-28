package com.retailassistant.ui.components.specific

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.retailassistant.core.Utils
import com.retailassistant.data.db.InteractionLog
import com.retailassistant.data.db.InteractionType
import com.retailassistant.data.db.Invoice
import com.retailassistant.data.db.InvoiceStatus
import com.retailassistant.ui.components.common.ElevatedCard

@Composable
fun InvoiceCard(
    invoice: Invoice,
    customerName: String,
    friendlyDueDate: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = customerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    InfoChip(icon = Icons.Default.Schedule, text = "Due: $friendlyDueDate")
                }
                StatusChip(status = invoice.status, isOverdue = invoice.isOverdue)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                AmountColumn(
                    label = if (invoice.balanceDue <= 0) "Fully Paid" else "Balance Due",
                    amount = if (invoice.balanceDue <= 0) invoice.totalAmount else invoice.balanceDue,
                    amountColor = if (invoice.balanceDue <= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    isPrimary = false
                )
                AmountColumn(
                    label = "Total",
                    amount = invoice.totalAmount,
                    amountColor = MaterialTheme.colorScheme.primary,
                    isPrimary = true,
                    horizontalAlignment = Alignment.End
                )
            }
        }
    }
}

@Composable
fun StatusChip(status: InvoiceStatus, isOverdue: Boolean, modifier: Modifier = Modifier) {
    val finalStatus = if (isOverdue && status != InvoiceStatus.PAID) InvoiceStatus.OVERDUE else status
    val (color, text) = when (finalStatus) {
        InvoiceStatus.PAID -> Pair(MaterialTheme.colorScheme.tertiary, "Paid")
        InvoiceStatus.OVERDUE -> Pair(MaterialTheme.colorScheme.error, "Overdue")
        InvoiceStatus.PARTIALLY_PAID -> Pair(MaterialTheme.colorScheme.primary, "Partial")
        InvoiceStatus.UNPAID -> Pair(MaterialTheme.colorScheme.onSurfaceVariant, "Unpaid")
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.1f),
        contentColor = color
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
fun AmountColumn(
    label: String,
    amount: Double,
    amountColor: Color,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    val amountStyle = if (isPrimary) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = Utils.formatCurrency(amount),
            style = amountStyle,
            fontWeight = FontWeight.Bold,
            color = amountColor
        )
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun InteractionLogItem(log: InteractionLog, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        val (icon, title, valueText, valueColor) = when (log.type) {
            InteractionType.PAYMENT -> Quad(Icons.Default.Payment, "Payment Received", "+${Utils.formatCurrency(log.value ?: 0.0)}", MaterialTheme.colorScheme.tertiary)
            InteractionType.NOTE -> Quad(Icons.AutoMirrored.Filled.Comment, "Note Added", null, Color.Unspecified)
            InteractionType.DUE_DATE_CHANGED -> Quad(Icons.Default.DateRange, "Due Date Changed", null, Color.Unspecified)
        }
        Icon(
            imageVector = icon,
            contentDescription = log.type.name,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp).size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                valueText?.let {
                    Text(it, fontWeight = FontWeight.Bold, color = valueColor, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
            log.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(4.dp))
            Text(
                text = Utils.formatTimestamp(log.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
// Private data class for tuple-like returns.
private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
