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
import io.github.jan.supabase.SupabaseClient
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
    private val supabase: SupabaseClient
) : MviViewModel<InvoiceDetailState, InvoiceDetailAction, InvoiceDetailEvent>() {

    init {
        val invoiceDetailsStream = repository.getInvoiceWithDetails(invoiceId)

        invoiceDetailsStream
            .onEach { (invoice, logs) ->
                if (invoice != null) {
                    sendAction(InvoiceDetailAction.DetailsLoaded(invoice, logs))
                } else if (!uiState.value.isLoading) {
                    // Invoice was deleted, navigate back
                    sendEvent(InvoiceDetailEvent.NavigateBack)
                } else {
                    setState { copy(isLoading = false) }
                }
            }
            .launchIn(viewModelScope)

        invoiceDetailsStream.filterNotNull().onEach { (invoice, _) ->
            invoice?.customerId?.let { customerId ->
                repository.getCustomerById(customerId).filterNotNull().onEach { customer ->
                    sendAction(InvoiceDetailAction.CustomerLoaded(customer))
                }.launchIn(viewModelScope)
            }
        }.launchIn(viewModelScope)

        loadImage()
    }

    private fun loadImage() = viewModelScope.launch {
        // Load image URL once
        if (uiState.value.imageUrl != null) return@launch
        val invoice = repository.getInvoiceWithDetails(invoiceId).first().first
        if (invoice != null) {
            repository.getPublicUrl(invoice.originalScanUrl)
                .onSuccess { url -> sendAction(InvoiceDetailAction.ImageUrlLoaded(url)) }
                .onFailure { sendEvent(InvoiceDetailEvent.ShowMessage("Could not load invoice image.")) }
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

    private fun performAction(
        repoCall: suspend () -> Result<Unit>,
        successMessage: String? = "Action completed successfully.",
        onSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            setState { copy(isProcessingAction = true) }
            val result = repoCall()
            result.onSuccess {
                if (successMessage != null) sendEvent(InvoiceDetailEvent.ShowMessage(successMessage))
                onSuccess?.invoke()
            }.onFailure {
                sendEvent(InvoiceDetailEvent.ShowMessage(it.message ?: "An error occurred"))
            }
            setState { copy(isProcessingAction = false, activeDialog = null) }
        }
    }

    private fun addPayment(amount: Double, note: String?) {
        val userId = uiState.value.invoice?.userId ?: return
        if (amount <= 0) {
            sendEvent(InvoiceDetailEvent.ShowMessage("Payment amount must be positive."))
            return
        }
        performAction(repoCall = { repository.addPayment(userId, invoiceId, amount, note) })
    }

    private fun addNote(note: String) {
        val userId = uiState.value.invoice?.userId ?: return
        if (note.isBlank()) {
            sendEvent(InvoiceDetailEvent.ShowMessage("Note cannot be empty."))
            return
        }
        performAction(repoCall = { repository.addNote(userId, invoiceId, note) })
    }

    private fun postponeDueDate(newDueDate: LocalDate, reason: String?) {
        val userId = uiState.value.invoice?.userId ?: return
        performAction(repoCall = { repository.postponeDueDate(userId, invoiceId, newDueDate, reason) })
    }

    private fun deleteInvoice() {
        performAction(
            repoCall = { repository.deleteInvoice(invoiceId) },
            successMessage = "Invoice deleted successfully.",
            onSuccess = { sendEvent(InvoiceDetailEvent.NavigateBack) }
        )
    }

    private fun callCustomer() {
        uiState.value.customer?.phone?.takeIf { it.isNotBlank() }
            ?.let { phone -> sendEvent(InvoiceDetailEvent.MakePhoneCall(phone)) }
            ?: sendEvent(InvoiceDetailEvent.ShowMessage("Customer has no phone number."))
    }
}
