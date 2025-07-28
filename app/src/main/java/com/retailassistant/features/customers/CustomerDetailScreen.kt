package com.retailassistant.features.customers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retailassistant.core.Utils.formatCurrency
import com.retailassistant.ui.components.common.*
import com.retailassistant.ui.components.specific.Avatar
import com.retailassistant.ui.components.specific.InfoChip
import com.retailassistant.ui.components.specific.InvoiceCard
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.format.DateTimeFormatter

@Composable
fun CustomerDetailScreen(
    customerId: String,
    onNavigateBack: () -> Unit,
    onNavigateToInvoice: (String) -> Unit,
    viewModel: CustomerDetailViewModel = koinViewModel { parametersOf(customerId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenteredTopAppBar(
                title = state.customer?.name ?: "Customer Details",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isCustomerLoading -> FullScreenLoading(modifier = Modifier.padding(padding))
            state.customer == null -> EmptyState("Not Found", "Customer not found.", Icons.Default.PeopleOutline, Modifier.padding(padding))
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { CustomerDetailHeader(state) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            StatCard("Total Billed", formatCurrency(state.totalBilled), Icons.Default.AccountBalanceWallet, Modifier.weight(1f))
                            StatCard("Outstanding", formatCurrency(state.totalOutstanding), Icons.Default.HourglassTop, Modifier.weight(1f), if (state.totalOutstanding > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    item {
                        Text("Invoices", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    }

                    if (state.areInvoicesLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (state.invoices.isEmpty()) {
                        item {
                            EmptyState("No Invoices", "This customer has no invoices yet.", Icons.AutoMirrored.Filled.ReceiptLong)
                        }
                    } else {
                        items(state.invoices, key = { it.id }) { invoice ->
                            val friendlyDueDate = invoice.dueDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                            InvoiceCard(
                                invoice = invoice,
                                customerName = state.customer!!.name,
                                friendlyDueDate = friendlyDueDate,
                                onClick = { onNavigateToInvoice(invoice.id) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerDetailHeader(state: CustomerDetailState) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(name = state.customer!!.name, size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Text(state.customer.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            state.customer?.phone?.takeIf { it.isNotBlank() }?.let {
                InfoChip(icon = Icons.Default.Phone, text = it)
            }
            state.customer?.email?.takeIf { it.isNotBlank() }?.let {
                InfoChip(icon = Icons.Default.Email, text = it)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, valueColor: androidx.compose.ui.graphics.Color = LocalContentColor.current) {
    ElevatedCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}
