package com.example.retailassistant.features.customers

import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.example.retailassistant.core.MviViewModel
import com.example.retailassistant.core.UiAction
import com.example.retailassistant.core.UiEvent
import com.example.retailassistant.core.UiState
import com.example.retailassistant.data.db.Customer
import com.example.retailassistant.data.db.Invoice
import com.example.retailassistant.data.db.InvoiceStatus
import com.example.retailassistant.data.repository.RetailRepository
import com.example.retailassistant.ui.components.CustomerStats
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// --- MVI Definitions ---
data class CustomerWithStats(
    val customer: Customer,
    val stats: CustomerStats
)

data class CustomerListState(
    val customersWithStats: List<CustomerWithStats> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false
) : UiState {
    val filteredCustomers: List<CustomerWithStats>
        get() = if (searchQuery.isBlank()) {
            customersWithStats
        } else {
            val lowerCaseQuery = searchQuery.lowercase()
            customersWithStats.filter {
                it.customer.name.lowercase().contains(lowerCaseQuery) ||
                        it.customer.phone?.contains(lowerCaseQuery) == true ||
                        it.customer.email?.lowercase()?.contains(lowerCaseQuery) == true
            }
        }
}

sealed interface CustomerListAction : UiAction {
    object RefreshData : CustomerListAction
    data class Search(val query: String) : CustomerListAction
    data class StartCall(val phoneNumber: String) : CustomerListAction
    data class StartEmail(val email: String) : CustomerListAction
    data class DataLoaded(val customers: List<Customer>, val invoices: List<Invoice>) : CustomerListAction
}

sealed interface CustomerListEvent : UiEvent {
    data class ShowError(val message: String) : CustomerListEvent
    data class OpenDialer(val intent: Intent) : CustomerListEvent
    data class OpenEmail(val intent: Intent) : CustomerListEvent
}

// --- ViewModel ---
class CustomerListViewModel(
    private val repository: RetailRepository,
    private val supabase: SupabaseClient
) : MviViewModel<CustomerListState, CustomerListAction, CustomerListEvent>() {

    init {
        // Observe local data streams. The streams will only start if a user is logged in.
        val userId = supabase.auth.currentUserOrNull()?.id
        if (userId != null) {
            viewModelScope.launch {
                repository.getCustomersStream(userId)
                    .combine(repository.getInvoicesStream(userId)) { customers, invoices ->
                        CustomerListAction.DataLoaded(customers, invoices)
                    }
                    .catch { e -> sendEvent(CustomerListEvent.ShowError(e.message ?: "Failed to load data")) }
                    .collect { sendAction(it) }
            }
        } else {
            setState { copy(isLoading = false) } // Not logged in, stop loading
        }
    }

    override fun createInitialState(): CustomerListState = CustomerListState()

    override fun handleAction(action: CustomerListAction) {
        when (action) {
            is CustomerListAction.RefreshData -> refreshData()
            is CustomerListAction.Search -> setState { copy(searchQuery = action.query) }
            is CustomerListAction.StartCall -> {
                val intent = Intent(Intent.ACTION_DIAL, "tel:${action.phoneNumber}".toUri())
                sendEvent(CustomerListEvent.OpenDialer(intent))
            }
            is CustomerListAction.StartEmail -> {
                val intent = Intent(Intent.ACTION_SENDTO, "mailto:${action.email}".toUri())
                sendEvent(CustomerListEvent.OpenEmail(intent))
            }
            is CustomerListAction.DataLoaded -> processLoadedData(action.customers, action.invoices)
        }
    }

    private fun processLoadedData(customers: List<Customer>, invoices: List<Invoice>) {
        // SUPERIOR IMPLEMENTATION: Use a map for O(N+M) efficiency instead of O(N*M).
        val invoicesByCustomerId = invoices.groupBy { it.customerId }

        val customersWithStats = customers.map { customer ->
            val customerInvoices = invoicesByCustomerId[customer.id] ?: emptyList()
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
                isRefreshing = false // Reset refreshing state upon data load
            )
        }
    }

    private fun refreshData() {
        viewModelScope.launch {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            setState { copy(isRefreshing = true) }
            repository.syncAllUserData(userId).onFailure { error ->
                sendEvent(CustomerListEvent.ShowError(error.message ?: "Sync failed"))
                // Ensure isRefreshing is turned off even if sync fails
                setState { copy(isRefreshing = false) }
            }
            // `isRefreshing` will be set to false automatically by processLoadedData on success.
        }
    }
}
