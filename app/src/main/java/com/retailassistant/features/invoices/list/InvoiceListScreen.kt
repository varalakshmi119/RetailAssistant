package com.retailassistant.features.invoices.list
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retailassistant.ui.components.common.CenteredTopAppBar
import com.retailassistant.ui.components.common.EmptyState
import com.retailassistant.ui.components.common.SearchBar
import com.retailassistant.ui.components.common.ShimmeringList
import com.retailassistant.ui.components.specific.InvoiceCard
import com.retailassistant.ui.components.specific.InvoiceFilter
import com.retailassistant.ui.components.specific.InvoiceFilterChips
import org.koin.androidx.compose.koinViewModel
import java.time.format.DateTimeFormatter
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
            InvoiceFilterChips(
                selectedFilter = state.selectedFilter,
                onFilterSelected = { viewModel.sendAction(InvoiceListAction.Filter(it)) },
                modifier = Modifier.padding(bottom = 8.dp)
            )
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.sendAction(InvoiceListAction.RefreshData) },
                modifier = Modifier.fillMaxSize()
            ) {
                val groupedInvoices = state.groupedInvoices
                when {
                    state.isLoading && groupedInvoices.isEmpty() -> {
                        ShimmeringList()
                    }
                    groupedInvoices.isEmpty() -> {
                        EmptyState(
                            title = if (state.searchQuery.isNotEmpty() || state.selectedFilter != InvoiceFilter.ALL) "No Invoices Found" else "No Invoices Yet",
                            subtitle = "Try adjusting your filters or creating a new invoice.",
                            icon = Icons.AutoMirrored.Filled.ReceiptLong
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            groupedInvoices.forEach { (status, invoices) ->
                                stickyHeader {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                    ) {
                                        Text(
                                            text = "$status (${invoices.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                }
                                items(invoices, key = { it.invoice.id }) { item ->
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
}
