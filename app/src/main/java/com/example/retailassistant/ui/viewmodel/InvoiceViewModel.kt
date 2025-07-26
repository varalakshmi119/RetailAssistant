package com.example.retailassistant.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.retailassistant.data.GeminiApiClient
import com.example.retailassistant.data.ImageUtils
import com.example.retailassistant.data.InvoiceRepository
import com.example.retailassistant.ui.MviViewModel
import com.example.retailassistant.ui.UiAction
import com.example.retailassistant.ui.UiEvent
import com.example.retailassistant.ui.UiState
import kotlinx.coroutines.launch

// State, Actions, and Events for Invoice Screen
data class InvoiceState(
    val customerName: String = "",
    val phoneNumber: String = "",
    val issueDate: String = "",
    val amount: String = "",
    val imageUri: Uri? = null,
    private val compressedImage: ByteArray? = null, // Keep private from UI
    val isAiExtracting: Boolean = false,
    val isSaving: Boolean = false
) : UiState {
    // Helper to check if we have compressed image data
    val hasImageData: Boolean get() = compressedImage != null
    
    // Internal method to update compressed image
    fun withCompressedImage(imageBytes: ByteArray?): InvoiceState = 
        copy(compressedImage = imageBytes)
    
    // Internal method to get compressed image
    fun getCompressedImage(): ByteArray? = compressedImage
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InvoiceState

        if (isAiExtracting != other.isAiExtracting) return false
        if (isSaving != other.isSaving) return false
        if (customerName != other.customerName) return false
        if (phoneNumber != other.phoneNumber) return false
        if (issueDate != other.issueDate) return false
        if (amount != other.amount) return false
        if (imageUri != other.imageUri) return false
        if (!compressedImage.contentEquals(other.compressedImage)) return false
        if (hasImageData != other.hasImageData) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isAiExtracting.hashCode()
        result = 31 * result + isSaving.hashCode()
        result = 31 * result + customerName.hashCode()
        result = 31 * result + phoneNumber.hashCode()
        result = 31 * result + issueDate.hashCode()
        result = 31 * result + amount.hashCode()
        result = 31 * result + (imageUri?.hashCode() ?: 0)
        result = 31 * result + (compressedImage?.contentHashCode() ?: 0)
        result = 31 * result + hasImageData.hashCode()
        return result
    }
}

sealed class InvoiceAction : UiAction {
    data class ScanCompleted(val uri: Uri, val context: Context) : InvoiceAction()
    object SaveInvoice : InvoiceAction()
    data class UpdateCustomerName(val name: String) : InvoiceAction()
    data class UpdatePhoneNumber(val phone: String) : InvoiceAction()
    data class UpdateIssueDate(val date: String) : InvoiceAction()
    data class UpdateAmount(val amount: String) : InvoiceAction()
}

sealed class InvoiceEvent : UiEvent {
    object NavigateToDashboard : InvoiceEvent()
    data class ShowError(val message: String) : InvoiceEvent()
    data class ShowSuccess(val message: String) : InvoiceEvent()
}

class InvoiceViewModel(
    private val repository: InvoiceRepository
) : MviViewModel<InvoiceState, InvoiceAction, InvoiceEvent>() {

    override fun createInitialState(): InvoiceState = InvoiceState()

    override fun handleAction(action: InvoiceAction) {
        when (action) {
            is InvoiceAction.ScanCompleted -> processScannedImage(action.uri, action.context)
            is InvoiceAction.SaveInvoice -> saveInvoice()
            is InvoiceAction.UpdateCustomerName -> setState { copy(customerName = action.name) }
            is InvoiceAction.UpdatePhoneNumber -> setState { copy(phoneNumber = action.phone) }
            is InvoiceAction.UpdateIssueDate -> setState { copy(issueDate = action.date) }
            is InvoiceAction.UpdateAmount -> setState { copy(amount = action.amount) }
        }
    }

    private fun processScannedImage(uri: Uri, context: Context) {
        viewModelScope.launch {
            setState { copy(imageUri = uri, isAiExtracting = true) }
            
            val imageBytes = ImageUtils.compressImage(context, uri)
            if (imageBytes == null) {
                sendEvent(InvoiceEvent.ShowError("Failed to process image."))
                setState { copy(isAiExtracting = false) }
                return@launch
            }

            // Store compressed image in state
            setState { withCompressedImage(imageBytes) }

            GeminiApiClient.extractInvoiceData(imageBytes)
                .onSuccess { data ->
                    setState {
                        copy(
                            customerName = data.customer_name ?: customerName,
                            phoneNumber = data.phone_number ?: phoneNumber,
                            issueDate = data.date ?: issueDate,
                            isAiExtracting = false
                        )
                    }
                }
                .onFailure {
                    sendEvent(InvoiceEvent.ShowError(it.message ?: "AI Extraction Failed"))
                    setState { copy(isAiExtracting = false) }
                }
        }
    }

    private fun saveInvoice() {
        viewModelScope.launch {
            val state = currentState
            
            // Validation
            if (state.customerName.isBlank()) {
                sendEvent(InvoiceEvent.ShowError("Customer name is required"))
                return@launch
            }
            
            if (state.amount.isBlank()) {
                sendEvent(InvoiceEvent.ShowError("Amount is required"))
                return@launch
            }
            
            val totalAmount = state.amount.toDoubleOrNull()
            if (totalAmount == null || totalAmount <= 0) {
                sendEvent(InvoiceEvent.ShowError("Please enter a valid amount"))
                return@launch
            }
            
            if (state.issueDate.isBlank()) {
                sendEvent(InvoiceEvent.ShowError("Issue date is required"))
                return@launch
            }
            
            val imageBytes = state.getCompressedImage()
            if (imageBytes == null) {
                sendEvent(InvoiceEvent.ShowError("Please scan an invoice image"))
                return@launch
            }

            setState { copy(isSaving = true) }

            repository.addInvoice(
                customerName = state.customerName,
                customerPhone = state.phoneNumber,
                issueDate = state.issueDate,
                totalAmount = totalAmount,
                imageBytes = imageBytes
            ).onSuccess {
                sendEvent(InvoiceEvent.ShowSuccess("Invoice saved successfully"))
                sendEvent(InvoiceEvent.NavigateToDashboard)
            }.onFailure { error ->
                sendEvent(InvoiceEvent.ShowError(error.message ?: "Failed to save invoice"))
            }

            setState { copy(isSaving = false) }
        }
    }
}