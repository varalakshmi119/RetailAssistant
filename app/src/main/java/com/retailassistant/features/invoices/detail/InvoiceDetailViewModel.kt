package com.retailassistant.features.invoices.detail
import androidx.lifecycle.viewModelScope
import com.retailassistant.core.MviViewModel
import com.retailassistant.core.UiAction
import com.retailassistant.core.UiEvent
import com.retailassistant.core.UiState
import com.retailassistant.data.db.Customer
import com.retailassistant.data.db.InteractionLog
import com.retailassistant.data.db.Invoice
import com.retailassistant.data.repository.RetailRepository
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed class ActiveDialog {
    data object AddPayment : ActiveDialog()
    data object AddNote : ActiveDialog()
    data object Postpone : ActiveDialog()
    data object ConfirmDeleteInvoice : ActiveDialog()
}
data class InvoiceDetailState(
    val invoice: Invoice? = null,
    val customer: Customer? = null,
    val logs: List<InteractionLog> = emptyList(),
    val imageUrl: String? = null,
    val isLoading: Boolean = true,
    val isProcessingAction: Boolean = false, // For dialog actions
    val activeDialog: ActiveDialog? = null,
) : UiState
sealed interface InvoiceDetailAction : UiAction {
    data class DetailsLoaded(val invoice: Invoice, val logs: List<InteractionLog>) : InvoiceDetailAction
    data class CustomerLoaded(val customer: Customer) : InvoiceDetailAction
    data class ImageUrlLoaded(val url: String) : InvoiceDetailAction
    object CallCustomer : InvoiceDetailAction
    data class ShowDialog(val dialog: ActiveDialog?) : InvoiceDetailAction
    data class AddPayment(val amount: Double, val note: String?) : InvoiceDetailAction
    data class AddNote(val note: String) : InvoiceDetailAction
    data class PostponeDueDate(val newDueDate: LocalDate, val reason: String?) : InvoiceDetailAction
    object DeleteInvoice : InvoiceDetailAction
}
sealed interface InvoiceDetailEvent : UiEvent {
    data class ShowMessage(val message: String) : InvoiceDetailEvent
    data class MakePhoneCall(val phoneNumber: String) : InvoiceDetailEvent
    object NavigateBack : InvoiceDetailEvent
}
class InvoiceDetailViewModel(
    private val invoiceId: String,
    private val repository: RetailRepository,
) : MviViewModel<InvoiceDetailState, InvoiceDetailAction, InvoiceDetailEvent>() {
    init {
        val invoiceDetailsStream = repository.getInvoiceWithDetails(invoiceId)
        // Stream invoice and logs
        invoiceDetailsStream
            .onEach { (invoice, logs) ->
                if (invoice != null) {
                    sendAction(InvoiceDetailAction.DetailsLoaded(invoice, logs))
                } else {
                    // If invoice becomes null (e.g., after deletion), signal loading is done
                    // to prevent infinite spinner on an empty screen.
                    setState { copy(isLoading = false) }
                }
            }
            .launchIn(viewModelScope)
        // Stream customer details based on invoice
        invoiceDetailsStream.filterNotNull().onEach { (invoice, _) ->
            invoice?.customerId?.let { customerId ->
                repository.getCustomerById(customerId).filterNotNull().onEach { customer ->
                    sendAction(InvoiceDetailAction.CustomerLoaded(customer))
                }.launchIn(viewModelScope)
            }
        }.launchIn(viewModelScope)
        // Load image URL once
        viewModelScope.launch {
            val invoice = repository.getInvoiceWithDetails(invoiceId).first().first
            if (invoice != null && uiState.value.imageUrl == null) {
                repository.getPublicUrl(invoice.originalScanUrl)
                    .onSuccess { url -> sendAction(InvoiceDetailAction.ImageUrlLoaded(url)) }
                    .onFailure { sendEvent(InvoiceDetailEvent.ShowMessage("Could not load invoice image.")) }
            }
        }
    }
    override fun createInitialState(): InvoiceDetailState = InvoiceDetailState()
    override fun handleAction(action: InvoiceDetailAction) {
        when (action) {
            is InvoiceDetailAction.DetailsLoaded -> setState { copy(invoice = action.invoice, logs = action.logs, isLoading = false) }
            is InvoiceDetailAction.CustomerLoaded -> setState { copy(customer = action.customer) }
            is InvoiceDetailAction.ImageUrlLoaded -> setState { copy(imageUrl = action.url) }
            is InvoiceDetailAction.AddPayment -> addPayment(action.amount, action.note)
            is InvoiceDetailAction.AddNote -> addNote(action.note)
            is InvoiceDetailAction.PostponeDueDate -> postponeDueDate(action.newDueDate, action.reason)
            is InvoiceDetailAction.CallCustomer -> callCustomer()
            is InvoiceDetailAction.ShowDialog -> setState { copy(activeDialog = action.dialog, isProcessingAction = false) }
            is InvoiceDetailAction.DeleteInvoice -> deleteInvoice()
        }
    }
    private fun addPayment(amount: Double, note: String?) {
        val userId = uiState.value.invoice?.userId ?: return
        if (amount <= 0) {
            sendEvent(InvoiceDetailEvent.ShowMessage("Payment amount must be positive."))
            return
        }
        // FIXED: Use named argument `repoCall`
        performAction(repoCall = { repository.addPayment(userId, invoiceId, amount, note) })
    }
    private fun addNote(note: String) {
        val userId = uiState.value.invoice?.userId ?: return
        if (note.isBlank()) {
            sendEvent(InvoiceDetailEvent.ShowMessage("Note cannot be empty."))
            return
        }
        // FIXED: Use named argument `repoCall`
        performAction(repoCall = { repository.addNote(userId, invoiceId, note) })
    }
    private fun postponeDueDate(newDueDate: LocalDate, reason: String?) {
        val userId = uiState.value.invoice?.userId ?: return
        // FIXED: Use named argument `repoCall`
        performAction(repoCall = { repository.postponeDueDate(userId, invoiceId, newDueDate, reason) })
    }
    private fun deleteInvoice() {
        performAction(
            repoCall = { repository.deleteInvoice(invoiceId) },
            onSuccess = {
                sendEvent(InvoiceDetailEvent.ShowMessage("Invoice deleted successfully"))
                sendEvent(InvoiceDetailEvent.NavigateBack)
            }
        )
    }
    private fun performAction(
        repoCall: suspend () -> Result<Unit>,
        onSuccess: () -> Unit = { sendEvent(InvoiceDetailEvent.ShowMessage("Action completed successfully.")) }
    ) {
        viewModelScope.launch {
            setState { copy(isProcessingAction = true) }
            val result = repoCall()
            result.onSuccess {
                onSuccess()
            }.onFailure {
                sendEvent(InvoiceDetailEvent.ShowMessage(it.message ?: "An error occurred"))
            }
            setState { copy(isProcessingAction = false, activeDialog = null) }
        }
    }
    private fun callCustomer() {
        uiState.value.customer?.phone?.takeIf { it.isNotBlank() }
            ?.let { phone -> sendEvent(InvoiceDetailEvent.MakePhoneCall(phone)) }
            ?: sendEvent(InvoiceDetailEvent.ShowMessage("Customer has no phone number."))
    }
}