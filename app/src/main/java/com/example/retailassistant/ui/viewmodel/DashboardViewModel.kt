package com.example.retailassistant.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.retailassistant.data.Invoice
import com.example.retailassistant.data.Customer
import com.example.retailassistant.data.InvoiceRepository
import com.example.retailassistant.ui.MviViewModel
import com.example.retailassistant.ui.UiAction
import com.example.retailassistant.ui.UiEvent
import com.example.retailassistant.ui.UiState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// Data class to hold invoice with customer info
data class InvoiceWithCustomer(
    val invoice: Invoice,
    val customer: Customer?
)

// State, Actions, and Events for Dashboard Screen
data class DashboardState(
    val invoicesWithCustomers: List<InvoiceWithCustomer> = emptyList(),
    val isLoading: Boolean = true,
    val totalUnpaid: Double = 0.0,
    val overdueCount: Int = 0
) : UiState

sealed class DashboardAction : UiAction {
    object LoadData : DashboardAction()
    object RefreshData : DashboardAction()
    object NavigateToAddInvoice : DashboardAction()
    object NavigateToCustomers : DashboardAction()
}

sealed class DashboardEvent : UiEvent {
    object NavigateToAddInvoice : DashboardEvent()
    object NavigateToCustomers : DashboardEvent()
    data class ShowError(val message: String) : DashboardEvent()
}

class DashboardViewModel(
    private val repository: InvoiceRepository
) : MviViewModel<DashboardState, DashboardAction, DashboardEvent>() {

    override fun createInitialState(): DashboardState = DashboardState()

    init {
        loadData()
    }

    override fun handleAction(action: DashboardAction) {
        when (action) {
            is DashboardAction.LoadData -> loadData()
            is DashboardAction.RefreshData -> refreshData()
            is DashboardAction.NavigateToAddInvoice -> sendEvent(DashboardEvent.NavigateToAddInvoice)
            is DashboardAction.NavigateToCustomers -> sendEvent(DashboardEvent.NavigateToCustomers)
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                repository.getInvoicesStream(),
                repository.getCustomersStream()
            ) { invoices, customers ->
                // Create a map for quick customer lookup
                val customerMap = customers.associateBy { it.id }
                
                // Join invoices with customers
                val invoicesWithCustomers = invoices.map { invoice ->
                    InvoiceWithCustomer(
                        invoice = invoice,
                        customer = customerMap[invoice.customerId]
                    )
                }
                
                val totalUnpaid = invoices.filter { it.status.name == "UNPAID" }
                    .sumOf { it.totalAmount - it.amountPaid }
                
                val overdueCount = invoices.count { it.status.name == "OVERDUE" }
                
                setState {
                    copy(
                        invoicesWithCustomers = invoicesWithCustomers,
                        isLoading = false,
                        totalUnpaid = totalUnpaid,
                        overdueCount = overdueCount
                    )
                }
            }.collectLatest { }
        }
    }

    private fun refreshData() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            
            repository.syncUserData().onFailure { error ->
                sendEvent(DashboardEvent.ShowError(error.message ?: "Sync failed"))
            }
            
            setState { copy(isLoading = false) }
        }
    }
}