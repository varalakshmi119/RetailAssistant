package com.retailassistant.features.invoices.detail

import androidx.lifecycle.viewModelScope
import com.retailassistant.core.*
import com.retailassistant.data.db.Customer
import com.retailassistant.data.db.InteractionLog
import com.retailassistant.data.db.Invoice
import com.retailassistant.data.repository.RetailRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed class ActiveDialog {
    data object AddPayment : ActiveDialog()
    data object AddNote : ActiveDialog()
    data object Postpone : ActiveDialog()
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
    data class DetailsLoaded(val details: Pair<Invoice?, List<InteractionLog>>) : InvoiceDetailAction
    data class CustomerLoaded(val customer: Customer?) : InvoiceDetailAction
    data class ImageUrlLoaded(val url: String) : InvoiceDetailAction
    object CallCustomer : InvoiceDetailAction
    data class ShowDialog(val dialog: ActiveDialog?) : InvoiceDetailAction
    data class AddPayment(val amount: Double, val note: String?) : InvoiceDetailAction
    data class AddNote(val note: String) : InvoiceDetailAction
    data class PostponeDueDate(val newDueDate: LocalDate, val reason: String?) : InvoiceDetailAction
}

sealed interface InvoiceDetailEvent : UiEvent {
    data class ShowError(val message: String) : InvoiceDetailEvent
    data class ShowSuccess(val message: String) : InvoiceDetailEvent
    data class MakePhoneCall(val phoneNumber: String) : InvoiceDetailEvent
}

class InvoiceDetailViewModel(
    private val invoiceId: String,
    private val repository: RetailRepository,
) : MviViewModel<InvoiceDetailState, InvoiceDetailAction, InvoiceDetailEvent>() {

    init {
        repository.getInvoiceWithDetails(invoiceId)
            .onEach { (invoice, logs) ->
                sendAction(InvoiceDetailAction.DetailsLoaded(invoice to logs))
                invoice?.let {
                    loadCustomer(it.customerId)
                    loadImage(it.originalScanUrl)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadCustomer(customerId: String) {
        repository.getCustomerById(customerId)
            .onEach { customer -> sendAction(InvoiceDetailAction.CustomerLoaded(customer)) }
            .launchIn(viewModelScope)
    }

    private fun loadImage(storagePath: String) {
        viewModelScope.launch {
            if (uiState.value.imageUrl == null) {
                repository.getPublicUrl(storagePath)
                    .onSuccess { url -> sendAction(InvoiceDetailAction.ImageUrlLoaded(url)) }
                    .onFailure { sendEvent(InvoiceDetailEvent.ShowError("Could not load invoice image.")) }
            }
        }
    }

    override fun createInitialState(): InvoiceDetailState = InvoiceDetailState()

    override fun handleAction(action: InvoiceDetailAction) {
        when (action) {
            is InvoiceDetailAction.DetailsLoaded -> setState {
                copy(invoice = action.details.first, logs = action.details.second, isLoading = action.details.first == null)
            }
            is InvoiceDetailAction.CustomerLoaded -> setState { copy(customer = action.customer) }
            is InvoiceDetailAction.ImageUrlLoaded -> setState { copy(imageUrl = action.url) }
            is InvoiceDetailAction.AddPayment -> addPayment(action.amount, action.note)
            is InvoiceDetailAction.AddNote -> addNote(action.note)
            is InvoiceDetailAction.PostponeDueDate -> postponeDueDate(action.newDueDate, action.reason)
            is InvoiceDetailAction.CallCustomer -> callCustomer()
            is InvoiceDetailAction.ShowDialog -> setState { copy(activeDialog = action.dialog) }
        }
    }

    private fun <T> handleRepoResult(
        result: Result<T>,
        successMessage: String,
        failurePrefix: String
    ) {
        result.onSuccess {
            sendEvent(InvoiceDetailEvent.ShowSuccess(successMessage))
        }.onFailure {
            sendEvent(InvoiceDetailEvent.ShowError("$failurePrefix: ${it.message}"))
        }
        setState { copy(isProcessingAction = false, activeDialog = null) }
    }

    private fun addPayment(amount: Double, note: String?) {
        val userId = uiState.value.invoice?.userId ?: return
        viewModelScope.launch {
            if (amount <= 0) {
                sendEvent(InvoiceDetailEvent.ShowError("Payment amount must be positive."))
                return@launch
            }
            setState { copy(isProcessingAction = true) }
            val result = repository.addPayment(userId, invoiceId, amount, note)
            handleRepoResult(result, "Payment recorded.", "Failed to record payment")
        }
    }

    private fun addNote(note: String) {
        val userId = uiState.value.invoice?.userId ?: return
        viewModelScope.launch {
            if (note.isBlank()) {
                sendEvent(InvoiceDetailEvent.ShowError("Note cannot be empty."))
                return@launch
            }
            setState { copy(isProcessingAction = true) }
            val result = repository.addNote(userId, invoiceId, note)
            handleRepoResult(result, "Note added.", "Failed to add note")
        }
    }

    private fun postponeDueDate(newDueDate: LocalDate, reason: String?) {
        val userId = uiState.value.invoice?.userId ?: return
        viewModelScope.launch {
            setState { copy(isProcessingAction = true) }
            val result = repository.postponeDueDate(userId, invoiceId, newDueDate, reason)
            handleRepoResult(result, "Due date postponed.", "Failed to postpone due date")
        }
    }

    private fun callCustomer() {
        uiState.value.customer?.phone?.takeIf { phone -> phone.isNotBlank() }
            ?.let { phone -> sendEvent(InvoiceDetailEvent.MakePhoneCall(phone)) }
            ?: sendEvent(InvoiceDetailEvent.ShowError("Customer has no phone number."))
    }
}
