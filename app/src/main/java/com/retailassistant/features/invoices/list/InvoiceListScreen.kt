package com.retailassistant.features.invoices.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retailassistant.ui.components.common.CenteredTopAppBar
import com.retailassistant.ui.components.common.EmptyState
import com.retailassistant.ui.components.common.SearchBar
import com.retailassistant.ui.components.common.ShimmeringList
import com.retailassistant.ui.components.specific.InvoiceCard
import org.koin.androidx.compose.koinViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceListScreen(
    onNavigateToInvoice: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: InvoiceListViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.event) {
        viewModel.event.collect { event ->
            when (event) {
                is InvoiceListEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            CenteredTopAppBar(
                title = "All Invoices",
                actions = {
                    IconButton(
                        onClick = { viewModel.sendAction(InvoiceListAction.RefreshData) },
                        enabled = !state.isRefreshing
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.sendAction(InvoiceListAction.RefreshData) },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    state.isLoading && state.filteredInvoices.isEmpty() -> {
                        ShimmeringList()
                    }
                    state.filteredInvoices.isEmpty() -> {
                        EmptyState(
                            title = if (state.searchQuery.isNotEmpty()) "No invoices found" else "No invoices yet",
                            subtitle = "Your created invoices will appear here.",
                            icon = Icons.AutoMirrored.Filled.ReceiptLong
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.filteredInvoices, key = { it.invoice.id }) { item ->
                                val friendlyDueDate = item.invoice.dueDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                                InvoiceCard(
                                    invoice = item.invoice,
                                    customerName = item.customer?.name ?: "Unknown Customer",
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
    }
}
