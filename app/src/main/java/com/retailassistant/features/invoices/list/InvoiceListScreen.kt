package com.retailassistant.features.invoices.list

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retailassistant.ui.components.common.*
import com.retailassistant.ui.components.specific.InvoiceCard
import org.koin.androidx.compose.koinViewModel
import java.time.format.DateTimeFormatter

@Composable
fun InvoiceListScreen(
    onNavigateToInvoice: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: InvoiceListViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenteredTopAppBar(
                title = "All Invoices",
                actions = {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        IconButton(onClick = { viewModel.sendAction(InvoiceListAction.RefreshData) }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SearchBar(
                value = state.searchQuery,
                onValueChange = { viewModel.sendAction(InvoiceListAction.Search(it)) },
                placeholder = "Search by customer or amount...",
                modifier = Modifier.padding(16.dp)
            )
            if (state.isLoading) {
                ShimmeringList()
            } else if (state.filteredInvoices.isEmpty()) {
                EmptyState(
                    title = if (state.searchQuery.isNotEmpty()) "No invoices found" else "No invoices yet",
                    subtitle = "Your created invoices will appear here.",
                    icon = Icons.AutoMirrored.Filled.ReceiptLong
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.filteredInvoices, key = { it.invoice.id }) { item ->
                        val friendlyDueDate = item.invoice.dueDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                        InvoiceCard(
                            invoice = item.invoice,
                            customerName = item.customer?.name ?: "Unknown",
                            friendlyDueDate = friendlyDueDate,
                            onClick = { onNavigateToInvoice(item.invoice.id) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}
