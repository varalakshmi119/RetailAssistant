package com.example.retailassistant.ui.viewmodel
import androidx.lifecycle.viewModelScope
import com.example.retailassistant.data.Customer
import com.example.retailassistant.data.Invoice
import com.example.retailassistant.data.InvoiceRepository
import com.example.retailassistant.ui.MviViewModel
import com.example.retailassistant.ui.UiAction
import com.example.retailassistant.ui.UiEvent
import com.example.retailassistant.ui.UiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
// --- Customer List ---
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
// --- Customer Detail ---
data class CustomerDetailState(
    val customer: Customer? = null,
    val invoices: List<Invoice> = emptyList(),
    val isLoading: Boolean = true,
) : UiState

// Define NoOp for ViewModels that don't need actions/events.
sealed class NoOpAction : UiAction
sealed class NoOpEvent : UiEvent

class CustomerDetailViewModel(
    private val customerId: String,
    private val repository: InvoiceRepository
) : MviViewModel<CustomerDetailState, NoOpAction, NoOpEvent>() {
    // Renamed from 'uiState' to avoid conflict with the base class property.
    val customerDetailState: StateFlow<CustomerDetailState> =
        combine(
            repository.getCustomerById(customerId),
            repository.getInvoicesStream() // Gets all invoices for the user
        ) { customer, allInvoices ->
            val customerInvoices = allInvoices.filter { it.customerId == customerId }
            CustomerDetailState(
                customer = customer,
                invoices = customerInvoices.sortedByDescending { it.createdAt },
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CustomerDetailState()
        )
    override fun createInitialState(): CustomerDetailState = CustomerDetailState()

    // Implemented the missing abstract function from the MviViewModel base class.
    override fun handleAction(action: NoOpAction) {
        // No actions are handled in this ViewModel.
    }
}
