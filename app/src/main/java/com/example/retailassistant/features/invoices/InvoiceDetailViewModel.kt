package com.example.retailassistant.features.invoices

import androidx.lifecycle.viewModelScope
import com.example.retailassistant.core.MviViewModel
import com.example.retailassistant.core.UiAction
import com.example.retailassistant.core.UiEvent
import com.example.retailassistant.core.UiState
import com.example.retailassistant.data.db.Customer
import com.example.retailassistant.data.db.InteractionLog
import com.example.retailassistant.data.db.Invoice
import com.example.retailassistant.data.repository.RetailRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate

// --- MVI Definitions ---
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
    val isProcessing: Boolean = false, // For dialog actions
    val activeDialog: ActiveDialog? = null,
) : UiState

sealed interface InvoiceDetailAction : UiAction {
    data class DetailsLoaded(val details: Pair<Invoice?, List<InteractionLog>>) : InvoiceDetailAction
    data class CustomerLoaded(val customer: Customer?) : InvoiceDetailAction
    data class ImageUrlLoaded(val url: String) : InvoiceDetailAction
    object CallCustomer : InvoiceDetailAction
    // Dialog Actions
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

// --- ViewModel ---
class InvoiceDetailViewModel(
    private val invoiceId: String,
    private val repository: RetailRepository,
    private val supabase: SupabaseClient
) : MviViewModel<InvoiceDetailState, InvoiceDetailAction, InvoiceDetailEvent>() {

    private val userId: String? = supabase.auth.currentUserOrNull()?.id

    init {
        repository.getInvoiceWithDetails(invoiceId)
            .onEach { (invoice, logs) ->
                sendAction(InvoiceDetailAction.DetailsLoaded(invoice to logs))

                invoice?.let {
                    // Launch these concurrently instead of sequentially
                    viewModelScope.launch { loadCustomer(it.customerId) }
                    viewModelScope.launch { loadImage(it.originalScanUrl) }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadCustomer(customerId: String) {
        // Using `launchIn` is cleaner for fire-and-forget collection
        repository.getCustomerById(customerId)
            .onEach { customer -> sendAction(InvoiceDetailAction.CustomerLoaded(customer)) }
            .launchIn(viewModelScope)
    }

    private fun loadImage(storagePath: String) {
        viewModelScope.launch {
            if (currentState.imageUrl == null) { // Only load if not already loaded
                repository.getPublicUrl(storagePath)
                    .onSuccess { url -> sendAction(InvoiceDetailAction.ImageUrlLoaded(url)) }
                    .onFailure { sendEvent(InvoiceDetailEvent.ShowError("Could not load invoice image.")) }
            }
        }
    }

    override fun createInitialState(): InvoiceDetailState = InvoiceDetailState()

    override fun handleAction(action: InvoiceDetailAction) {
        when (action) {
            is InvoiceDetailAction.DetailsLoaded -> setState { copy(invoice = action.details.first, logs = action.details.second, isLoading = false) }
            is InvoiceDetailAction.CustomerLoaded -> setState { copy(customer = action.customer) }
            is InvoiceDetailAction.ImageUrlLoaded -> setState { copy(imageUrl = action.url) }
            is InvoiceDetailAction.AddPayment -> addPayment(action.amount, action.note)
            is InvoiceDetailAction.AddNote -> addNote(action.note)
            is InvoiceDetailAction.PostponeDueDate -> postponeDueDate(action.newDueDate, action.reason)
            is InvoiceDetailAction.CallCustomer -> callCustomer()
            is InvoiceDetailAction.ShowDialog -> setState { copy(activeDialog = action.dialog) }
        }
    }

    private fun addPayment(amount: Double, note: String?) {
        if (userId == null) return
        viewModelScope.launch {
            if (amount <= 0) {
                sendEvent(InvoiceDetailEvent.ShowError("Payment amount must be positive."))
                return@launch
            }
            setState { copy(isProcessing = true) }
            repository.addPayment(userId, invoiceId, amount, note)
                .onSuccess { sendEvent(InvoiceDetailEvent.ShowSuccess("Payment recorded.")) }
                .onFailure { sendEvent(InvoiceDetailEvent.ShowError(it.message ?: "Failed to record payment.")) }
            setState { copy(isProcessing = false, activeDialog = null) }
        }
    }

    private fun addNote(note: String) {
        if (userId == null) return
        viewModelScope.launch {
            if (note.isBlank()) {
                sendEvent(InvoiceDetailEvent.ShowError("Note cannot be empty."))
                return@launch
            }
            setState { copy(isProcessing = true) }
            repository.addNote(userId, invoiceId, note)
                .onSuccess { sendEvent(InvoiceDetailEvent.ShowSuccess("Note added.")) }
                .onFailure { sendEvent(InvoiceDetailEvent.ShowError(it.message ?: "Failed to add note.")) }
            setState { copy(isProcessing = false, activeDialog = null) }
        }
    }

    private fun postponeDueDate(newDueDate: LocalDate, reason: String?) {
        if (userId == null) return
        viewModelScope.launch {
            setState { copy(isProcessing = true) }
            repository.postponeDueDate(userId, invoiceId, newDueDate, reason)
                .onSuccess { sendEvent(InvoiceDetailEvent.ShowSuccess("Due date postponed.")) }
                .onFailure { sendEvent(InvoiceDetailEvent.ShowError(it.message ?: "Failed to postpone due date.")) }
            setState { copy(isProcessing = false, activeDialog = null) }
        }
    }

    private fun callCustomer() {
        currentState.customer?.phone?.takeIf { it.isNotBlank() }
            ?.let { sendEvent(InvoiceDetailEvent.MakePhoneCall(it)) }
            ?: sendEvent(InvoiceDetailEvent.ShowError("Customer has no phone number."))
    }
}
