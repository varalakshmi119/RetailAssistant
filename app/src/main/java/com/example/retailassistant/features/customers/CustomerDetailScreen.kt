package com.example.retailassistant.features.customers

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
import com.example.retailassistant.core.Utils.formatCurrency
import com.example.retailassistant.data.db.Customer
import com.example.retailassistant.ui.components.*
import com.example.retailassistant.ui.theme.AppGradients
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerId: String,
    onNavigateBack: () -> Unit,
    onNavigateToInvoiceDetail: (String) -> Unit,
    viewModel: CustomerDetailViewModel = koinViewModel { parametersOf(customerId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.customer?.name ?: "Customer Details", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        if (state.isLoading) {
            ShimmeringCustomerList(modifier = Modifier.padding(padding))
        } else if (state.customer == null) {
            EmptyState(
                title = "Customer Not Found",
                subtitle = "The requested customer could not be found.",
                icon = Icons.Default.PeopleOutline,
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { CustomerDetailHeader(state.customer!!) }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        EnhancedStatCard(
                            label = "Total Billed",
                            value = formatCurrency(state.totalBilled),
                            icon = Icons.Default.AccountBalanceWallet,
                            modifier = Modifier.weight(1f)
                        )
                        EnhancedStatCard(
                            label = "Outstanding",
                            value = formatCurrency(state.totalOutstanding),
                            icon = Icons.Default.HourglassTop,
                            modifier = Modifier.weight(1f),
                            gradient = if (state.totalOutstanding > 0) AppGradients.Warning else AppGradients.Success
                        )
                    }
                }

                item {
                    Text(
                        "Invoices (${state.invoices.size})",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (state.invoices.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No Invoices",
                            subtitle = "This customer does not have any invoices yet.",
                            icon = Icons.AutoMirrored.Filled.ReceiptLong
                        )
                    }
                } else {
                    items(state.invoices, key = { it.id }) { invoice ->
                        val friendlyDueDate = invoice.dueDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                        InvoiceCard(
                            invoice = invoice,
                            customerName = state.customer?.name ?: "Unknown",
                            friendlyDueDate = friendlyDueDate,
                            onClick = { onNavigateToInvoiceDetail(invoice.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerDetailHeader(customer: Customer) {
    FloatingCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EnhancedAvatar(name = customer.name, size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            customer.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                InfoRow(icon = Icons.Default.Phone, text = phone, iconContentDescription = "Phone")
            }
            customer.email?.takeIf { it.isNotBlank() }?.let { email ->
                InfoRow(icon = Icons.Default.Email, text = email, iconContentDescription = "Email")
            }
        }
    }
}
