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
import io.github.jan.supabase.auth.auth
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
    // Internal actions for loading data
    data class DetailsLoaded(val invoice: Invoice, val logs: List<InteractionLog>) : InvoiceDetailAction
    data class CustomerLoaded(val customer: Customer) : InvoiceDetailAction
    data class ImageUrlLoaded(val url: String) : InvoiceDetailAction
    data class LoadFailed(val error: Throwable) : InvoiceDetailAction
    // User-initiated actions
    object CallCustomer : InvoiceDetailAction
    data class ShowDialog(val dialog: ActiveDialog?) : InvoiceDetailAction
    data class AddPayment(val amount: Double, val note: String?) : InvoiceDetailAction
    data class AddNote(val note: String) : InvoiceDetailAction
    data class PostponeDueDate(val newDueDate: LocalDate, val reason: String?) : InvoiceDetailAction
    object DeleteInvoice : InvoiceDetailAction
    object SendWhatsAppReminder : InvoiceDetailAction
    data class ShowMessage(val message: String) : InvoiceDetailAction
}
sealed interface InvoiceDetailEvent : UiEvent {
    data class ShowMessage(val message: String) : InvoiceDetailEvent
    data class MakePhoneCall(val phoneNumber: String) : InvoiceDetailEvent
    data class SendWhatsAppReminder(val invoice: Invoice, val customer: Customer?, val imageBytes: ByteArray?) : InvoiceDetailEvent
    object NavigateBack : InvoiceDetailEvent
}
class InvoiceDetailViewModel(
    private val invoiceId: String,
    private val repository: RetailRepository,
    private val supabase: SupabaseClient
) : MviViewModel<InvoiceDetailState, InvoiceDetailAction, InvoiceDetailEvent>() {
    private val userId: String? = supabase.auth.currentUserOrNull()?.id
    init {
        // FIX: Consolidated data loading into a single, more robust stream
        repository.getInvoiceWithDetails(invoiceId)
            .onEach { (invoice, logs) ->
                if (invoice != null) {
                    // Once invoice is confirmed to exist, load its related data
                    sendAction(InvoiceDetailAction.DetailsLoaded(invoice, logs))
                    loadCustomer(invoice.customerId)
                    loadImageUrl(invoice.originalScanUrl)
                } else if (!uiState.value.isLoading) {
                    // This handles the case where the invoice is deleted while the user is viewing it
                    sendEvent(InvoiceDetailEvent.NavigateBack)
                } else {
                    // This handles the case where the invoice ID was invalid from the start
                    setState { copy(isLoading = false) }
                }
            }
            .launchIn(viewModelScope)
    }
    private fun loadCustomer(customerId: String) = viewModelScope.launch {
        repository.getCustomerById(customerId)
            .filterNotNull()
            .first() // Take the first non-null emission
            .let { sendAction(InvoiceDetailAction.CustomerLoaded(it)) }
    }
    private fun loadImageUrl(path: String) = viewModelScope.launch {
        // Prevent re-fetching if URL is already loaded
        if (uiState.value.imageUrl != null) return@launch
        repository.getPublicUrl(path)
            .onSuccess { sendAction(InvoiceDetailAction.ImageUrlLoaded(it)) }
            .onFailure { sendAction(InvoiceDetailAction.LoadFailed(it)) }
    }
    override fun createInitialState(): InvoiceDetailState = InvoiceDetailState()
    override fun handleAction(action: InvoiceDetailAction) {
        println("DEBUG: handleAction called with: ${action::class.simpleName}")
        when (action) {
            is InvoiceDetailAction.DetailsLoaded -> setState { copy(invoice = action.invoice, logs = action.logs, isLoading = false) }
            is InvoiceDetailAction.CustomerLoaded -> setState { copy(customer = action.customer) }
            is InvoiceDetailAction.ImageUrlLoaded -> setState { copy(imageUrl = action.url) }
            is InvoiceDetailAction.LoadFailed -> sendEvent(InvoiceDetailEvent.ShowMessage(action.error.message ?: "Failed to load image"))
            is InvoiceDetailAction.AddPayment -> addPayment(action.amount, action.note)
            is InvoiceDetailAction.AddNote -> addNote(action.note)
            is InvoiceDetailAction.PostponeDueDate -> postponeDueDate(action.newDueDate, action.reason)
            is InvoiceDetailAction.CallCustomer -> callCustomer()
            is InvoiceDetailAction.ShowDialog -> setState { copy(activeDialog = action.dialog, isProcessingAction = false) }
            is InvoiceDetailAction.DeleteInvoice -> deleteInvoice()
            is InvoiceDetailAction.SendWhatsAppReminder -> {
                println("DEBUG: SendWhatsAppReminder action received")
                sendWhatsAppReminder()
            }
            is InvoiceDetailAction.ShowMessage -> sendEvent(InvoiceDetailEvent.ShowMessage(action.message))
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
        if (userId == null) return
        if (amount <= 0) {
            sendEvent(InvoiceDetailEvent.ShowMessage("Payment amount must be positive."))
            return
        }
        performAction(repoCall = { repository.addPayment(userId, invoiceId, amount, note) })
    }
    private fun addNote(note: String) {
        if (userId == null) return
        if (note.isBlank()) {
            sendEvent(InvoiceDetailEvent.ShowMessage("Note cannot be empty."))
            return
        }
        performAction(repoCall = { repository.addNote(userId, invoiceId, note) })
    }
    private fun postponeDueDate(newDueDate: LocalDate, reason: String?) {
        if (userId == null) return
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
    
    private fun sendWhatsAppReminder() = viewModelScope.launch {
        println("DEBUG: sendWhatsAppReminder called")
        
        val currentState = uiState.value
        val invoice = currentState.invoice
        val customer = currentState.customer
        
        println("DEBUG: Invoice: ${invoice?.id}, Customer: ${customer?.name}, Phone: ${customer?.phone}")
        
        if (invoice == null) {
            println("DEBUG: Invoice is null")
            sendEvent(InvoiceDetailEvent.ShowMessage("Invoice not found"))
            return@launch
        }
        
        if (customer?.phone.isNullOrBlank()) {
            println("DEBUG: Customer phone is null or blank")
            sendEvent(InvoiceDetailEvent.ShowMessage("Customer phone number is required for WhatsApp reminder"))
            return@launch
        }
        
        println("DEBUG: Getting image bytes from URL: ${currentState.imageUrl}")
        // Get image bytes if available
        val imageBytes = currentState.imageUrl?.let { url ->
            try {
                val result = repository.downloadImageBytes(url).getOrNull()
                println("DEBUG: Downloaded image bytes: ${result?.size} bytes")
                result
            } catch (e: Exception) {
                println("DEBUG: Error downloading image: ${e.message}")
                null
            }
        }
        
        println("DEBUG: Sending WhatsApp reminder event")
        sendEvent(InvoiceDetailEvent.SendWhatsAppReminder(invoice, customer, imageBytes))
    }
}