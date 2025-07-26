package com.example.retailassistant.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.retailassistant.data.Customer
import com.example.retailassistant.data.InvoiceRepository
import com.example.retailassistant.ui.MviViewModel
import com.example.retailassistant.ui.UiAction
import com.example.retailassistant.ui.UiEvent
import com.example.retailassistant.ui.UiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CustomerState(
    val customers: List<Customer> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false
) : UiState

sealed class CustomerAction : UiAction {
    object RefreshData : CustomerAction()
}

sealed class CustomerEvent : UiEvent {
    data class ShowError(val message: String) : CustomerEvent()
}

class CustomerViewModel(
    private val repository: InvoiceRepository
) : MviViewModel<CustomerState, CustomerAction, CustomerEvent>() {

    val customerUiState: StateFlow<CustomerState> = repository.getCustomersStream()
        .map { customers -> CustomerState(customers = customers, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CustomerState()
        )

    override fun createInitialState(): CustomerState = CustomerState()

    override fun handleAction(action: CustomerAction) {
        when (action) {
            is CustomerAction.RefreshData -> refreshData()
        }
    }

    private fun refreshData() {
        viewModelScope.launch {
            setState { copy(isRefreshing = true) }
            repository.syncUserData().onFailure { error ->
                sendEvent(CustomerEvent.ShowError(error.message ?: "Sync failed"))
            }
            setState { copy(isRefreshing = false) }
        }
    }
}
