package com.example.retailassistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.retailassistant.core.Utils
import com.example.retailassistant.data.db.Customer
import com.example.retailassistant.data.db.InteractionLog
import com.example.retailassistant.data.db.InteractionType
import com.example.retailassistant.data.db.Invoice
import com.example.retailassistant.data.db.InvoiceStatus
import com.example.retailassistant.ui.theme.AppGradients
import com.example.retailassistant.ui.theme.GradientColors


@Composable
fun FloatingCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    gradient: GradientColors = AppGradients.Primary,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(gradient.start, gradient.end),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
                )
            )
            .padding(20.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            content()
        }
    }
}

@Composable
fun EnhancedStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    gradient: GradientColors = AppGradients.Primary,
    isAlert: Boolean = false
) {
    GradientCard(modifier = modifier, gradient = gradient) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(24.dp))
                if (isAlert) {
                    PulsingDot(color = Color.White, size = 8.dp)
                }
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            Text(value, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceCard(invoice: Invoice, customer: Customer?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingCard(modifier = modifier.fillMaxWidth(), onClick = onClick, elevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = customer?.name ?: "Unknown Customer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                InfoRow(icon = Icons.Default.Schedule, text = "Due: ${invoice.dueDate}")
            }
            StatusChip(status = invoice.status, isOverdue = invoice.isOverdue)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Payment progress bar
        val progress = if (invoice.totalAmount > 0) (invoice.amountPaid / invoice.totalAmount).toFloat() else 0f
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = if (invoice.status == InvoiceStatus.PAID) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = if (invoice.balanceDue <= 0) "Fully Paid" else "Balance Due",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (invoice.balanceDue <= 0) "✓" else Utils.formatCurrency(invoice.balanceDue),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (invoice.balanceDue <= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Total Amount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = Utils.formatCurrency(invoice.totalAmount),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

data class CustomerStats(val totalInvoices: Int, val unpaidAmount: Double, val overdueCount: Int)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerCard(
    customer: Customer, stats: CustomerStats, onClick: () -> Unit, modifier: Modifier = Modifier,
    onCallClick: (() -> Unit)? = null, onEmailClick: (() -> Unit)? = null
) {
    FloatingCard(modifier = modifier.fillMaxWidth(), onClick = onClick, elevation = 3.dp) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EnhancedAvatar(name = customer.name)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                InfoRow(icon = Icons.Default.Receipt, text = "${stats.totalInvoices} invoice${if (stats.totalInvoices != 1) "s" else ""}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                onCallClick?.let {
                    IconButton(
                        onClick = it,
                        modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) { Icon(Icons.Default.Call, "Call", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp)) }
                }
                onEmailClick?.let {
                    IconButton(
                        onClick = it,
                        modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    ) { Icon(Icons.Default.Email, "Email", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp)) }
                }
            }
        }
        if (stats.totalInvoices > 0 && stats.unpaidAmount > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Outstanding", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Utils.formatCurrency(stats.unpaidAmount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
                if (stats.overdueCount > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            PulsingDot(color = MaterialTheme.colorScheme.error, size = 6.dp)
                            Text("Overdue", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${stats.overdueCount}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun InteractionLogCard(log: InteractionLog, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            val (icon, title, valueText) = when (log.type) {
                InteractionType.PAYMENT -> Triple(Icons.Default.Payment, "Payment Received", "+${Utils.formatCurrency(log.value ?: 0.0)}")
                InteractionType.CALL -> Triple(Icons.Default.Call, "Call Logged", null)
                InteractionType.NOTE -> Triple(Icons.AutoMirrored.Filled.Comment, "Note Added", null)
            }
            Icon(imageVector = icon, contentDescription = log.type.name, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp).size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    valueText?.let {
                        Text(it, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(4.dp))
                log.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(4.dp))
                Text(text = Utils.formatTimestamp(log.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(top = 16.dp))
    }
}


@Composable
fun PulsingDot(color: Color = MaterialTheme.colorScheme.primary, size: Dp = 8.dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing_dot")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ), label = "dot_scale"
    )
    Box(modifier = Modifier.size(size).scale(scale).background(color, CircleShape))
}

@Composable
fun EmptyState(title: String, subtitle: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp).fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun EnhancedAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val gradient = AppGradients.Primary
    Box(
        modifier = modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(colors = listOf(gradient.start, gradient.end))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = (size.value / 2).sp),
            color = Color.White, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun InfoRow(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CustomerHeader(customerName: String, customerPhone: String?, onCall: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.weight(1f)) {
            Text(customerName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (!customerPhone.isNullOrBlank()) {
                Text(customerPhone, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!customerPhone.isNullOrBlank()) {
            IconButton(
                onClick = onCall,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(Icons.Default.Call, "Call Customer", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
fun PaymentSummaryCard(invoice: Invoice) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Total Amount", style = MaterialTheme.typography.bodyLarge)
                Text(Utils.formatCurrency(invoice.totalAmount), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            val progress = if (invoice.totalAmount > 0) (invoice.amountPaid / invoice.totalAmount).toFloat() else 0f
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                color = if (invoice.status == InvoiceStatus.PAID) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${Utils.formatCurrency(invoice.amountPaid)} Paid", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                Text("${Utils.formatCurrency(invoice.balanceDue)} Due", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatusChip(status = invoice.status, isOverdue = invoice.isOverdue)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Due Date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(invoice.dueDate, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}


@Composable
fun StatusChip(status: InvoiceStatus, isOverdue: Boolean, modifier: Modifier = Modifier) {
    val finalStatus = if (isOverdue && status != InvoiceStatus.PAID) InvoiceStatus.OVERDUE else status
    val style = when (finalStatus) {
        InvoiceStatus.PAID -> StatusStyle(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, "Paid")
        InvoiceStatus.OVERDUE -> StatusStyle(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "Overdue")
        InvoiceStatus.PARTIALLY_PAID -> StatusStyle(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "Partial")
        InvoiceStatus.UNPAID -> StatusStyle(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Unpaid")
    }
    Surface(modifier = modifier, shape = CircleShape, color = style.backgroundColor) {
        Text(
            text = style.text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = style.textColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private data class StatusStyle(val backgroundColor: Color, val textColor: Color, val text: String)

@Composable
fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon?.let { Icon(it, null, tint = color, modifier = Modifier.size(14.dp)) }
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}
