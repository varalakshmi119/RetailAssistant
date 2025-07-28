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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

// --- MVI Definitions ---

data class CustomerDetailState(
    val customer: Customer? = null,
    val invoices: List<Invoice> = emptyList(),
    val isCustomerLoading: Boolean = true,
    val areInvoicesLoading: Boolean = true,
    val totalBilled: Double = 0.0,
    val totalOutstanding: Double = 0.0,
) : UiState

sealed interface CustomerDetailAction : UiAction {
    data class CustomerLoaded(val customer: Customer?) : CustomerDetailAction
    data class InvoicesLoaded(val invoices: List<Invoice>) : CustomerDetailAction
}

interface CustomerDetailEvent : UiEvent

// --- ViewModel ---

class CustomerDetailViewModel(
    customerId: String,
    repository: RetailRepository,
    supabase: SupabaseClient // Injected by Koin
) : MviViewModel<CustomerDetailState, CustomerDetailAction, CustomerDetailEvent>() {

    init {
        val userId = supabase.auth.currentUserOrNull()?.id
        if (userId != null) {
            // 1. Start loading the customer details.
            repository.getCustomerById(customerId)
                .onEach { customer -> sendAction(CustomerDetailAction.CustomerLoaded(customer)) }
                .launchIn(viewModelScope)

            // 2. Start loading the customer's invoices in parallel.
            repository.getCustomerInvoicesStream(userId, customerId)
                .onEach { invoices -> sendAction(CustomerDetailAction.InvoicesLoaded(invoices)) }
                .launchIn(viewModelScope)

        } else {
            // Handle case where user is logged out while ViewModel is initializing
            setState { copy(isCustomerLoading = false, areInvoicesLoading = false) }
        }
    }

    override fun createInitialState(): CustomerDetailState = CustomerDetailState()

    override fun handleAction(action: CustomerDetailAction) {
        when(action) {
            is CustomerDetailAction.CustomerLoaded -> {
                setState {
                    copy(
                        customer = action.customer,
                        isCustomerLoading = false
                    )
                }
            }
            is CustomerDetailAction.InvoicesLoaded -> {
                val customerInvoices = action.invoices
                val totalBilled = customerInvoices.sumOf { it.totalAmount }
                val totalOutstanding = customerInvoices
                    .filter { it.status != InvoiceStatus.PAID }
                    .sumOf { it.balanceDue }
                setState {
                    copy(
                        invoices = customerInvoices.sortedByDescending { it.createdAt },
                        totalBilled = totalBilled,
                        totalOutstanding = totalOutstanding,
                        areInvoicesLoading = false
                    )
                }
            }
        }
    }
}