package com.example.retailassistant.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.retailassistant.data.Invoice
import com.example.retailassistant.data.InvoiceRepository
import com.example.retailassistant.ui.MviViewModel
import com.example.retailassistant.ui.UiAction
import com.example.retailassistant.ui.UiEvent
import com.example.retailassistant.ui.UiState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// State, Actions, and Events for Dashboard Screen
data class DashboardState(
    val invoices: List<Invoice> = emptyList(),
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
            repository.getInvoicesStream().collectLatest { invoices ->
                val totalUnpaid = invoices.filter { it.status.name == "UNPAID" }
                    .sumOf { it.totalAmount - it.amountPaid }
                
                val overdueCount = invoices.count { it.status.name == "OVERDUE" }
                
                setState {
                    copy(
                        invoices = invoices,
                        isLoading = false,
                        totalUnpaid = totalUnpaid,
                        overdueCount = overdueCount
                    )
                }
            }
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