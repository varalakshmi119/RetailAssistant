package com.retailassistant.features.customers

import androidx.lifecycle.viewModelScope
import com.retailassistant.core.*
import com.retailassistant.data.db.Customer
import com.retailassistant.data.db.Invoice
import com.retailassistant.data.db.InvoiceStatus
import com.retailassistant.data.repository.RetailRepository
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

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

class CustomerDetailViewModel(
    customerId: String,
    repository: RetailRepository
) : MviViewModel<CustomerDetailState, CustomerDetailAction, CustomerDetailEvent>() {

    init {
        // Start loading customer details
        repository.getCustomerById(customerId)
            .onEach { customer -> sendAction(CustomerDetailAction.CustomerLoaded(customer)) }
            .launchIn(viewModelScope)

        // Start loading invoices in parallel
        // The user ID isn't needed here as the customerId is globally unique.
        repository.getCustomerInvoicesStream("", customerId) // userId is ignored in implementation for this query
            .onEach { invoices -> sendAction(CustomerDetailAction.InvoicesLoaded(invoices)) }
            .launchIn(viewModelScope)
    }

    override fun createInitialState(): CustomerDetailState = CustomerDetailState()

    override fun handleAction(action: CustomerDetailAction) {
        when(action) {
            is CustomerDetailAction.CustomerLoaded -> {
                setState { copy(customer = action.customer, isCustomerLoading = false) }
            }
            is CustomerDetailAction.InvoicesLoaded -> {
                val totalBilled = action.invoices.sumOf { it.totalAmount }
                val totalOutstanding = action.invoices
                    .filter { it.status != InvoiceStatus.PAID }
                    .sumOf { it.balanceDue }
                setState {
                    copy(
                        invoices = action.invoices.sortedByDescending { it.createdAt },
                        totalBilled = totalBilled,
                        totalOutstanding = totalOutstanding,
                        areInvoicesLoading = false
                    )
                }
            }
        }
    }
}
