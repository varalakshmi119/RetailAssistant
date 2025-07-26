package com.example.retailassistant.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.retailassistant.data.*
import com.example.retailassistant.ui.MviViewModel
import com.example.retailassistant.ui.UiAction
import com.example.retailassistant.ui.UiEvent
import com.example.retailassistant.ui.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class InvoiceDetailState(
    val invoice: Invoice? = null,
    val customer: Customer? = null,
    val logs: List<InteractionLog> = emptyList(),
    val imageUrl: String? = null,
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false // For actions like adding payment
) : UiState

sealed class InvoiceDetailAction : UiAction {
    object LoadDetails : InvoiceDetailAction()
    data class AddPayment(val amount: Double, val note: String?) : InvoiceDetailAction()
    data class AddNote(val note: String) : InvoiceDetailAction()
}

sealed class InvoiceDetailEvent : UiEvent {
    data class ShowError(val message: String) : InvoiceDetailEvent()
    data class ShowSuccess(val message: String) : InvoiceDetailEvent()
    data class MakePhoneCall(val phoneNumber: String) : InvoiceDetailEvent()
}

class InvoiceDetailViewModel(
    private val invoiceId: String,
    private val repository: InvoiceRepository
) : MviViewModel<InvoiceDetailState, InvoiceDetailAction, InvoiceDetailEvent>() {

    override fun createInitialState(): InvoiceDetailState = InvoiceDetailState()

    init {
        sendAction(InvoiceDetailAction.LoadDetails)
    }

    override fun handleAction(action: InvoiceDetailAction) {
        when (action) {
            is InvoiceDetailAction.LoadDetails -> loadInvoiceDetails()
            is InvoiceDetailAction.AddPayment -> addPayment(action.amount, action.note)
            is InvoiceDetailAction.AddNote -> addNote(action.note)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadInvoiceDetails() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            repository.getInvoiceDetails(invoiceId)
                .flatMapLatest { details ->
                    details.invoice?.let { invoice ->
                        repository.getCustomerById(invoice.customerId).map { customer ->
                            details to customer
                        }
                    } ?: flowOf(details to null)
                }
                .collect { (details, customer) ->
                    setState {
                        copy(
                            invoice = details.invoice,
                            logs = details.logs,
                            customer = customer,
                            isLoading = false
                        )
                    }
                    // Fetch public URL for the image
                    details.invoice?.originalScanUrl?.let { path ->
                        if (currentState.imageUrl == null) {
                           launch {
                               repository.getPublicUrl(path).onSuccess { url ->
                                   setState { copy(imageUrl = url) }
                               }
                           }
                        }
                    }
                }
        }
    }

    private fun addPayment(amount: Double, note: String?) {
        viewModelScope.launch {
            if(amount <= 0) {
                sendEvent(InvoiceDetailEvent.ShowError("Payment amount must be positive."))
                return@launch
            }
            setState { copy(isProcessing = true) }
            repository.addPayment(invoiceId, amount, note)
                .onSuccess {
                    sendEvent(InvoiceDetailEvent.ShowSuccess("Payment recorded successfully."))
                }
                .onFailure {
                    sendEvent(InvoiceDetailEvent.ShowError(it.message ?: "Failed to record payment."))
                }
            setState { copy(isProcessing = false) }
        }
    }

    private fun addNote(note: String) {
        viewModelScope.launch {
            if(note.isBlank()) {
                sendEvent(InvoiceDetailEvent.ShowError("Note cannot be empty."))
                return@launch
            }
            setState { copy(isProcessing = true) }
            repository.addNote(invoiceId, note)
                .onSuccess {
                    sendEvent(InvoiceDetailEvent.ShowSuccess("Note added."))
                }
                .onFailure {
                    sendEvent(InvoiceDetailEvent.ShowError(it.message ?: "Failed to add note."))
                }
            setState { copy(isProcessing = false) }
        }
    }

    fun onCallCustomer() {
        currentState.customer?.phone?.let {
            if (it.isNotBlank()) {
                sendEvent(InvoiceDetailEvent.MakePhoneCall(it))
            } else {
                sendEvent(InvoiceDetailEvent.ShowError("No phone number available for this customer."))
            }
        }
    }
}
