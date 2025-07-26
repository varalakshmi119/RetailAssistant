package com.example.retailassistant.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.retailassistant.data.Customer
import com.example.retailassistant.data.InvoiceRepository
import com.example.retailassistant.ui.MviViewModel
import com.example.retailassistant.ui.UiAction
import com.example.retailassistant.ui.UiEvent
import com.example.retailassistant.ui.UiState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// State, Actions, and Events for Customer Screen
data class CustomerState(
    val customers: List<Customer> = emptyList(),
    val isLoading: Boolean = true
) : UiState

sealed class CustomerAction : UiAction {
    object LoadCustomers : CustomerAction()
    object RefreshData : CustomerAction()
}

sealed class CustomerEvent : UiEvent {
    data class ShowError(val message: String) : CustomerEvent()
}

class CustomerViewModel(
    private val repository: InvoiceRepository
) : MviViewModel<CustomerState, CustomerAction, CustomerEvent>() {

    override fun createInitialState(): CustomerState = CustomerState()

    init {
        loadCustomers()
    }

    override fun handleAction(action: CustomerAction) {
        when (action) {
            is CustomerAction.LoadCustomers -> loadCustomers()
            is CustomerAction.RefreshData -> refreshData()
        }
    }

    private fun loadCustomers() {
        viewModelScope.launch {
            repository.getCustomersStream().collectLatest { customers ->
                setState {
                    copy(
                        customers = customers,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun refreshData() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            
            repository.syncUserData().onFailure { error ->
                sendEvent(CustomerEvent.ShowError(error.message ?: "Sync failed"))
            }
            
            setState { copy(isLoading = false) }
        }
    }
}