package com.retailassistant.features.customers
import androidx.lifecycle.viewModelScope
import com.retailassistant.core.MviViewModel
import com.retailassistant.core.UiAction
import com.retailassistant.core.UiEvent
import com.retailassistant.core.UiState
import com.retailassistant.data.db.Customer
import com.retailassistant.data.db.Invoice
import com.retailassistant.data.repository.RetailRepository
import com.retailassistant.ui.components.specific.CustomerStats
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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
    val filteredCustomers: List<CustomerWithStats> by lazy {
        if (searchQuery.isBlank()) {
            customersWithStats
        } else {
            val query = searchQuery.lowercase().trim()
            customersWithStats.filter {
                it.customer.name.lowercase().contains(query) ||
                        it.customer.phone?.contains(query) == true ||
                        it.customer.email?.lowercase()?.contains(query) == true
            }
        }
    }
}
sealed interface CustomerListAction : UiAction {
    object RefreshData : CustomerListAction
    data class Search(val query: String) : CustomerListAction
    data class CallCustomer(val customer: Customer) : CustomerListAction
    data class EmailCustomer(val customer: Customer) : CustomerListAction
    data class DataLoaded(val customers: List<Customer>, val invoices: List<Invoice>) : CustomerListAction
}
sealed interface CustomerListEvent : UiEvent {
    data class ShowError(val message: String) : CustomerListEvent
    data class OpenDialer(val phone: String) : CustomerListEvent
    data class OpenEmail(val email: String) : CustomerListEvent
}
class CustomerListViewModel(
    private val repository: RetailRepository,
    supabase: SupabaseClient
) : MviViewModel<CustomerListState, CustomerListAction, CustomerListEvent>() {
    private val userId: String? = supabase.auth.currentUserOrNull()?.id
    init {
        if (userId != null) {
            repository.getCustomersStream(userId)
                .combine(repository.getInvoicesStream(userId)) { customers, invoices ->
                    CustomerListAction.DataLoaded(customers, invoices)
                }
                .catch { e -> sendEvent(CustomerListEvent.ShowError(e.message ?: "Failed to load data")) }
                .onEach { sendAction(it) }
                .launchIn(viewModelScope)
            refreshData()
        } else {
            setState { copy(isLoading = false) }
        }
    }
    override fun createInitialState(): CustomerListState = CustomerListState()
    override fun handleAction(action: CustomerListAction) {
        when (action) {
            is CustomerListAction.RefreshData -> refreshData()
            is CustomerListAction.Search -> setState { copy(searchQuery = action.query) }
            is CustomerListAction.CallCustomer -> action.customer.phone?.let { sendEvent(CustomerListEvent.OpenDialer(it)) }
            is CustomerListAction.EmailCustomer -> action.customer.email?.let { sendEvent(CustomerListEvent.OpenEmail(it)) }
            is CustomerListAction.DataLoaded -> processLoadedData(action.customers, action.invoices)
        }
    }
    private fun processLoadedData(customers: List<Customer>, invoices: List<Invoice>) {
        val invoicesByCustomerId = invoices.groupBy { it.customerId }
        val customersWithStats = customers.map { customer ->
            val customerInvoices = invoicesByCustomerId[customer.id] ?: emptyList()
            val stats = CustomerStats(
                totalInvoices = customerInvoices.size,
                unpaidAmount = customerInvoices.sumOf { it.balanceDue },
                overdueCount = customerInvoices.count { it.isOverdue }
            )
            CustomerWithStats(customer, stats)
        }.sortedByDescending { it.stats.unpaidAmount }
        setState {
            copy(
                customersWithStats = customersWithStats,
                isLoading = false,
                isRefreshing = false
            )
        }
    }
    private fun refreshData() {
        if (userId == null) return
        viewModelScope.launch {
            setState { copy(isRefreshing = true) }
            repository.syncAllUserData(userId).onFailure { error ->
                sendEvent(CustomerListEvent.ShowError(error.message ?: "Sync failed"))
                setState { copy(isRefreshing = false) }
            }
            // isRefreshing is set to false in processLoadedData upon success
        }
    }
}
