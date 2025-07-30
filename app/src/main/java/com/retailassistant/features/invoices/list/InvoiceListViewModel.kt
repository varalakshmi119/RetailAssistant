package com.retailassistant.features.invoices.list
import androidx.lifecycle.viewModelScope
import com.retailassistant.core.MviViewModel
import com.retailassistant.core.UiAction
import com.retailassistant.core.UiEvent
import com.retailassistant.core.UiState
import com.retailassistant.data.db.Customer
import com.retailassistant.data.db.Invoice
import com.retailassistant.data.db.InvoiceStatus
import com.retailassistant.data.repository.RetailRepository
import com.retailassistant.features.dashboard.InvoiceWithCustomer
import com.retailassistant.ui.components.specific.InvoiceFilter
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
data class InvoiceListState(
    val allInvoices: List<InvoiceWithCustomer> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: InvoiceFilter = InvoiceFilter.ALL,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false
) : UiState {
    private val filteredBySearchAndFilter: List<InvoiceWithCustomer> by lazy {
        val byFilter = when (selectedFilter) {
            InvoiceFilter.ALL -> allInvoices
            InvoiceFilter.UNPAID -> allInvoices.filter { it.invoice.status == InvoiceStatus.UNPAID && !it.invoice.isOverdue }
            InvoiceFilter.OVERDUE -> allInvoices.filter { it.invoice.isOverdue }
            InvoiceFilter.PAID -> allInvoices.filter { it.invoice.status == InvoiceStatus.PAID }
        }
        if (searchQuery.isBlank()) {
            byFilter
        } else {
            val query = searchQuery.lowercase().trim()
            byFilter.filter {
                it.customer?.name?.lowercase()?.contains(query) == true ||
                        it.invoice.totalAmount.toString().contains(query)
            }
        }
    }
    val groupedInvoices: Map<String, List<InvoiceWithCustomer>> by lazy {
        filteredBySearchAndFilter
            .groupBy {
                when {
                    it.invoice.isOverdue -> "Overdue"
                    it.invoice.status == InvoiceStatus.PAID -> "Paid"
                    else -> "Upcoming" // Includes UNPAID and PARTIALLY_PAID
                }
            }
            .toSortedMap(compareBy {
                when (it) { // Custom sort order for the groups
                    "Overdue" -> 0
                    "Upcoming" -> 1
                    "Paid" -> 2
                    else -> 3
                }
            })
    }
}
sealed interface InvoiceListAction : UiAction {
    object RefreshData : InvoiceListAction
    data class Search(val query: String) : InvoiceListAction
    data class Filter(val filter: InvoiceFilter) : InvoiceListAction
    data class DataLoaded(val invoices: List<Invoice>, val customers: List<Customer>) : InvoiceListAction
}
sealed interface InvoiceListEvent : UiEvent {
    data class ShowError(val message: String) : InvoiceListEvent
}
class InvoiceListViewModel(
    private val repository: RetailRepository,
    supabase: SupabaseClient
) : MviViewModel<InvoiceListState, InvoiceListAction, InvoiceListEvent>() {
    private val userId: String? = supabase.auth.currentUserOrNull()?.id
    init {
        if (userId != null) {
            repository.getInvoicesStream(userId)
                .combine(repository.getCustomersStream(userId)) { invoices, customers ->
                    InvoiceListAction.DataLoaded(invoices, customers)
                }
                .catch {
                    sendEvent(InvoiceListEvent.ShowError(it.message ?: "Failed to load data from cache."))
                    setState { copy(isLoading = false) }
                }
                .onEach { sendAction(it) }
                .launchIn(viewModelScope)
            refreshData(isInitial = true)
        } else {
            setState { copy(isLoading = false) }
        }
    }
    override fun createInitialState(): InvoiceListState = InvoiceListState()
    override fun handleAction(action: InvoiceListAction) {
        when (action) {
            is InvoiceListAction.RefreshData -> refreshData()
            is InvoiceListAction.Search -> setState { copy(searchQuery = action.query) }
            is InvoiceListAction.Filter -> setState { copy(selectedFilter = action.filter) }
            is InvoiceListAction.DataLoaded -> {
                val customerMap = action.customers.associateBy { it.id }
                val invoicesWithCustomers = action.invoices.map { invoice ->
                    InvoiceWithCustomer(invoice, customerMap[invoice.customerId])
                }
                setState {
                    copy(
                        allInvoices = invoicesWithCustomers.sortedByDescending { it.invoice.createdAt },
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            }
        }
    }
    private fun refreshData(isInitial: Boolean = false) {
        if (userId == null) return
        viewModelScope.launch {
            if (!isInitial) setState { copy(isRefreshing = true) }
            repository.syncAllUserData(userId).onFailure { error ->
                sendEvent(InvoiceListEvent.ShowError(error.message ?: "Sync failed"))
                // FIX: Reset both loading states on failure to prevent infinite loading
                setState { copy(isRefreshing = false, isLoading = false) }
            }
            // On success, isRefreshing is set to false by the DataLoaded action
        }
    }
}
