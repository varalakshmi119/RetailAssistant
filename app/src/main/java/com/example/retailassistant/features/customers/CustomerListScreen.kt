package com.example.retailassistant.features.customers

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.retailassistant.core.MviViewModel
import com.example.retailassistant.core.UiAction
import com.example.retailassistant.core.UiEvent
import com.example.retailassistant.core.UiState
import com.example.retailassistant.data.db.Customer
import com.example.retailassistant.data.db.Invoice
import com.example.retailassistant.data.db.InvoiceStatus
import com.example.retailassistant.data.repository.RetailRepository
import com.example.retailassistant.ui.components.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

// --- MVI Definitions ---
data class CustomerWithStats(
    val customer: Customer,
    val stats: CustomerStats
)

data class CustomerListState(
    val customersWithStats: List<CustomerWithStats> = emptyList(),
    val searchQuery: String = "",
    val filteredCustomers: List<CustomerWithStats> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false
) : UiState

sealed interface CustomerListAction : UiAction {
    object RefreshData : CustomerListAction
    data class Search(val query: String) : CustomerListAction
    data class Call(val phoneNumber: String) : CustomerListAction
    data class Email(val email: String) : CustomerListAction
    data class DataLoaded(val customers: List<Customer>, val invoices: List<Invoice>) : CustomerListAction
}

sealed interface CustomerListEvent : UiEvent {
    data class ShowError(val message: String) : CustomerListEvent
    data class OpenDialer(val phoneNumber: String) : CustomerListEvent
    data class OpenEmail(val email: String) : CustomerListEvent
}

// --- ViewModel ---
class CustomerListViewModel(
    private val repository: RetailRepository
) : MviViewModel<CustomerListState, CustomerListAction, CustomerListEvent>() {

    init {
        viewModelScope.launch {
            repository.getCustomersStream()
                .combine(repository.getInvoicesStream()) { customers, invoices ->
                    CustomerListAction.DataLoaded(customers, invoices)
                }
                .catch { e -> sendEvent(CustomerListEvent.ShowError(e.message ?: "Failed to load data")) }
                .collect { sendAction(it) }
        }
    }

    override fun createInitialState(): CustomerListState = CustomerListState()

    override fun handleAction(action: CustomerListAction) {
        when (action) {
            is CustomerListAction.RefreshData -> refreshData()
            is CustomerListAction.Search -> searchCustomers(action.query)
            is CustomerListAction.Call -> sendEvent(CustomerListEvent.OpenDialer(action.phoneNumber))
            is CustomerListAction.Email -> sendEvent(CustomerListEvent.OpenEmail(action.email))
            is CustomerListAction.DataLoaded -> processLoadedData(action.customers, action.invoices)
        }
    }

    private fun processLoadedData(customers: List<Customer>, invoices: List<Invoice>) {
        val customersWithStats = customers.map { customer ->
            val customerInvoices = invoices.filter { it.customerId == customer.id }
            val stats = CustomerStats(
                totalInvoices = customerInvoices.size,
                unpaidAmount = customerInvoices.filter { it.status != InvoiceStatus.PAID }.sumOf { it.balanceDue },
                overdueCount = customerInvoices.count { it.isOverdue }
            )
            CustomerWithStats(customer, stats)
        }.sortedByDescending { it.stats.unpaidAmount } // Show customers with most debt first

        setState {
            copy(
                customersWithStats = customersWithStats,
                isLoading = false,
                isRefreshing = false
            ).applySearch(searchQuery) // Re-apply search on new data
        }
    }

    private fun refreshData() {
        viewModelScope.launch {
            setState { copy(isRefreshing = true) }
            repository.syncAllUserData().onFailure { error ->
                sendEvent(CustomerListEvent.ShowError(error.message ?: "Sync failed"))
            }
            // `isRefreshing` will be set to false automatically when new data is loaded.
            setState { copy(isRefreshing = false) }
        }
    }

    private fun searchCustomers(query: String) {
        setState { applySearch(query) }
    }

    // A helper extension function to keep the state update logic clean.
    private fun CustomerListState.applySearch(query: String): CustomerListState {
        val filtered = if (query.isBlank()) {
            customersWithStats
        } else {
            val lowerCaseQuery = query.lowercase()
            customersWithStats.filter {
                it.customer.name.lowercase().contains(lowerCaseQuery) ||
                it.customer.phone?.contains(lowerCaseQuery) == true ||
                it.customer.email?.lowercase()?.contains(lowerCaseQuery) == true
            }
        }
        return this.copy(searchQuery = query, filteredCustomers = filtered)
    }
}

// --- Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    onNavigateToCustomerDetail: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: CustomerListViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is CustomerListEvent.OpenDialer -> {
                    context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${event.phoneNumber}".toUri()))
                }
                is CustomerListEvent.OpenEmail -> {
                    val intent = Intent(Intent.ACTION_SENDTO, "mailto:${event.email}".toUri())
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    }
                }
                is CustomerListEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customers", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.sendAction(CustomerListAction.RefreshData) },
            modifier = Modifier.padding(padding)
        ) {
            if (state.isLoading) {
                ShimmeringCustomerList()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SearchTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.sendAction(CustomerListAction.Search(it)) },
                            placeholder = "Search by name, phone, or email"
                        )
                    }
                    if (state.filteredCustomers.isEmpty()) {
                        item {
                            EmptyState(
                                title = if (state.searchQuery.isNotEmpty()) "No customers found" else "No customers yet",
                                subtitle = if (state.searchQuery.isNotEmpty())
                                    "Try adjusting your search query."
                                else
                                    "Your customers will appear here once you create invoices for them.",
                                icon = Icons.Default.People
                            )
                        }
                    } else {
                        items(state.filteredCustomers, key = { it.customer.id }) { customerWithStats ->
                            CustomerCard(
                                customer = customerWithStats.customer,
                                stats = customerWithStats.stats,
                                onClick = { onNavigateToCustomerDetail(customerWithStats.customer.id) },
                                onCallClick = customerWithStats.customer.phone?.let { phone -> { viewModel.sendAction(CustomerListAction.Call(phone)) } },
                                onEmailClick = customerWithStats.customer.email?.let { email -> { viewModel.sendAction(CustomerListAction.Email(email)) } }
                            )
                        }
                    }
                }
            }
        }
    }
}
