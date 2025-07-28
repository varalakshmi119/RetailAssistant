package com.retailassistant.features.invoices.list

import androidx.lifecycle.viewModelScope
import com.retailassistant.core.MviViewModel
import com.retailassistant.core.UiAction
import com.retailassistant.core.UiEvent
import com.retailassistant.core.UiState
import com.retailassistant.data.db.Customer
import com.retailassistant.data.db.Invoice
import com.retailassistant.data.repository.RetailRepository
import com.retailassistant.features.dashboard.InvoiceWithCustomer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class InvoiceListState(
    val allInvoices: List<InvoiceWithCustomer> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false
) : UiState {
    val filteredInvoices: List<InvoiceWithCustomer>
        get() = if (searchQuery.isBlank()) {
            allInvoices
        } else {
            val query = searchQuery.lowercase()
            allInvoices.filter {
                it.customer?.name?.lowercase()?.contains(query) == true ||
                it.invoice.totalAmount.toString().contains(query)
            }
        }
}

sealed interface InvoiceListAction : UiAction {
    object RefreshData : InvoiceListAction
    data class Search(val query: String) : InvoiceListAction
    data class DataLoaded(val invoices: List<Invoice>, val customers: List<Customer>) : InvoiceListAction
}

interface InvoiceListEvent : UiEvent

class InvoiceListViewModel(
    private val repository: RetailRepository,
    private val supabase: SupabaseClient
) : MviViewModel<InvoiceListState, InvoiceListAction, InvoiceListEvent>() {

    private val userId: String? = supabase.auth.currentUserOrNull()?.id

    init {
        if (userId != null) {
            viewModelScope.launch {
                repository.getInvoicesStream(userId)
                    .combine(repository.getCustomersStream(userId)) { invoices, customers ->
                        InvoiceListAction.DataLoaded(invoices, customers)
                    }
                    .catch { setState { copy(isLoading = false) } }
                    .collect { sendAction(it) }
            }
            sendAction(InvoiceListAction.RefreshData)
        } else {
            setState { copy(isLoading = false) }
        }
    }

    override fun createInitialState(): InvoiceListState = InvoiceListState()

    override fun handleAction(action: InvoiceListAction) {
        when (action) {
            is InvoiceListAction.RefreshData -> refreshData()
            is InvoiceListAction.Search -> setState { copy(searchQuery = action.query) }
            is InvoiceListAction.DataLoaded -> {
                val customerMap = action.customers.associateBy { it.id }
                val invoicesWithCustomers = action.invoices.map { invoice ->
                    InvoiceWithCustomer(invoice, customerMap[invoice.customerId])
                }
                setState {
                    copy(
                        allInvoices = invoicesWithCustomers,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            }
        }
    }

    private fun refreshData() {
        if (userId == null) return
        viewModelScope.launch {
            setState { copy(isRefreshing = true) }
            repository.syncAllUserData(userId).onFailure {
                setState { copy(isRefreshing = false) }
            }
        }
    }
}
