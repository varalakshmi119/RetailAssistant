package com.retailassistant.features.customers

import androidx.lifecycle.viewModelScope
import com.retailassistant.core.MviViewModel
import com.retailassistant.core.UiAction
import com.retailassistant.core.UiEvent
import com.retailassistant.core.UiState
import com.retailassistant.data.db.Customer
import com.retailassistant.data.db.Invoice
import com.retailassistant.data.repository.RetailRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn

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

interface CustomerDetailEvent : UiEvent

class CustomerDetailViewModel(
    customerId: String,
    repository: RetailRepository
) : MviViewModel<CustomerDetailState, CustomerDetailAction, CustomerDetailEvent>() {

    init {
        // userId is not needed for customer-specific queries where customerId is globally unique.
        val customerStream = repository.getCustomerById(customerId)
        val invoicesStream = repository.getCustomerInvoicesStream("", customerId)

        customerStream.combine(invoicesStream) { customer, invoices ->
            sendAction(CustomerDetailAction.DataLoaded(customer, invoices))
        }.launchIn(viewModelScope)
    }

    override fun createInitialState(): CustomerDetailState = CustomerDetailState()

    override fun handleAction(action: CustomerDetailAction) {
        when (action) {
            is CustomerDetailAction.DataLoaded -> {
                val totalBilled = action.invoices.sumOf { it.totalAmount }
                val totalOutstanding = action.invoices.sumOf { it.balanceDue }
                setState {
                    copy(
                        customer = action.customer,
                        invoices = action.invoices.sortedByDescending { it.createdAt },
                        totalBilled = totalBilled,
                        totalOutstanding = totalOutstanding,
                        isLoading = false
                    )
                }
            }
        }
    }
}
