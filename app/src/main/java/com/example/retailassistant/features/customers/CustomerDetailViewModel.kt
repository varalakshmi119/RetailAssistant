package com.example.retailassistant.features.customers

import androidx.lifecycle.viewModelScope
import com.example.retailassistant.core.MviViewModel
import com.example.retailassistant.core.UiAction
import com.example.retailassistant.core.UiEvent
import com.example.retailassistant.core.UiState
import com.example.retailassistant.data.db.Customer
import com.example.retailassistant.data.db.Invoice
import com.example.retailassistant.data.db.InvoiceStatus
import com.example.retailassistant.data.repository.RetailRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

// --- MVI Definitions ---
data class CustomerDetailState(
    val customer: Customer? = null,
    val invoices: List<Invoice> = emptyList(),
    val isLoading: Boolean = true,
    val totalBilled: Double = 0.0,
    val totalOutstanding: Double = 0.0,
) : UiState

sealed interface CustomerDetailAction : UiAction {
    data class DataLoaded(val customer: Customer?, val invoices: List<Invoice>) : CustomerDetailAction
}

interface CustomerDetailEvent : UiEvent // No events needed for this screen

// --- ViewModel ---
class CustomerDetailViewModel(
    customerId: String,
    repository: RetailRepository,
    supabase: SupabaseClient // Injected by Koin
) : MviViewModel<CustomerDetailState, CustomerDetailAction, CustomerDetailEvent>() {

    init {
        val userId = supabase.auth.currentUserOrNull()?.id
        if (userId != null) {
            // Combine flows for customer details and their associated invoices.
            repository.getCustomerById(customerId)
                .combine(repository.getCustomerInvoicesStream(userId, customerId)) { customer, invoices ->
                    CustomerDetailAction.DataLoaded(customer, invoices)
                }
                .onEach(::sendAction)
                .launchIn(viewModelScope)
        } else {
            // Handle case where user is logged out while ViewModel is initializing
            setState { copy(isLoading = false) }
        }
    }

    override fun createInitialState(): CustomerDetailState = CustomerDetailState()

    override fun handleAction(action: CustomerDetailAction) {
        when(action) {
            is CustomerDetailAction.DataLoaded -> {
                val (customer, customerInvoices) = action
                val totalBilled = customerInvoices.sumOf { it.totalAmount }
                val totalOutstanding = customerInvoices
                    .filter { it.status != InvoiceStatus.PAID }
                    .sumOf { it.balanceDue }
                setState {
                    copy(
                        customer = customer,
                        invoices = customerInvoices.sortedByDescending { it.createdAt },
                        totalBilled = totalBilled,
                        totalOutstanding = totalOutstanding,
                        isLoading = false
                    )
                }
            }
        }
    }
}
