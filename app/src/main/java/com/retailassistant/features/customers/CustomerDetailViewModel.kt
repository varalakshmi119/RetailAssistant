package com.retailassistant.features.customers
import androidx.lifecycle.viewModelScope
import com.retailassistant.core.MviViewModel
import com.retailassistant.core.UiAction
import com.retailassistant.core.UiEvent
import com.retailassistant.core.UiState
import com.retailassistant.data.db.Customer
import com.retailassistant.data.db.Invoice
import com.retailassistant.data.repository.RetailRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
// MODIFIED: Added dialog and processing state
sealed class CustomerDetailDialog {
    data object ConfirmDeleteCustomer : CustomerDetailDialog()
}
data class CustomerDetailState(
    val customer: Customer? = null,
    val invoices: List<Invoice> = emptyList(),
    val isLoading: Boolean = true,
    val totalBilled: Double = 0.0,
    val totalOutstanding: Double = 0.0,
    // MODIFIED: Added dialog and processing state
    val activeDialog: CustomerDetailDialog? = null,
    val isProcessingAction: Boolean = false
) : UiState
sealed interface CustomerDetailAction : UiAction {
    data class DataLoaded(val customer: Customer?, val invoices: List<Invoice>) : CustomerDetailAction
    // MODIFIED: Added actions for deletion
    data class ShowDialog(val dialog: CustomerDetailDialog?) : CustomerDetailAction
    object DeleteCustomer : CustomerDetailAction
}
// MODIFIED: Added NavigateBack event
sealed interface CustomerDetailEvent : UiEvent {
    object NavigateBack : CustomerDetailEvent
    data class ShowMessage(val message: String) : CustomerDetailEvent
}
class CustomerDetailViewModel(
    private val customerId: String, // Made it a property for delete action
    private val repository: RetailRepository,
    supabase: SupabaseClient
) : MviViewModel<CustomerDetailState, CustomerDetailAction, CustomerDetailEvent>() {
    private val userId: String? = supabase.auth.currentUserOrNull()?.id
    init {
        if (userId != null) {
            val customerStream = repository.getCustomerById(customerId)
            val invoicesStream = repository.getCustomerInvoicesStream(userId, customerId)
            customerStream.combine(invoicesStream) { customer, invoices ->
                sendAction(CustomerDetailAction.DataLoaded(customer, invoices))
            }.launchIn(viewModelScope)
        } else {
            // Handle case where user is not authenticated.
            setState { copy(isLoading = false) }
        }
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
            // MODIFIED: Handle delete actions
            is CustomerDetailAction.ShowDialog -> setState { copy(activeDialog = action.dialog, isProcessingAction = false) }
            is CustomerDetailAction.DeleteCustomer -> deleteCustomer()
        }
    }
    private fun deleteCustomer() {
        viewModelScope.launch {
            setState { copy(isProcessingAction = true) }
            val result = repository.deleteCustomer(customerId)
            result.onSuccess {
                sendEvent(CustomerDetailEvent.ShowMessage("Customer deleted successfully"))
                sendEvent(CustomerDetailEvent.NavigateBack)
            }.onFailure {
                sendEvent(CustomerDetailEvent.ShowMessage(it.message ?: "Failed to delete customer"))
            }
            setState { copy(isProcessingAction = false, activeDialog = null) }
        }
    }
}