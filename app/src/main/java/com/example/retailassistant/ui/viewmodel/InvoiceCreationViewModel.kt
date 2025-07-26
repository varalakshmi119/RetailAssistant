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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class InvoiceCreationState(
    val customerName: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val issueDate: String = "",
    val dueDate: String = "",
    val amount: String = "",
    val imageUri: Uri? = null,
    val compressedImage: ByteArray? = null,
    val isAiExtracting: Boolean = false,
    val isSaving: Boolean = false
) : UiState {
    val isFormValid: Boolean
        get() = customerName.isNotBlank() &&
                issueDate.isNotBlank() &&
                dueDate.isNotBlank() &&
                amount.toDoubleOrNull() ?: 0.0 > 0.0 &&
                compressedImage != null
}

sealed class InvoiceCreationAction : UiAction {
    data class ImageSelected(val uri: Uri, val context: Context) : InvoiceCreationAction()
    object SaveInvoice : InvoiceCreationAction()
    data class UpdateField(val field: Field, val value: String) : InvoiceCreationAction()
    enum class Field { CustomerName, PhoneNumber, Email, IssueDate, DueDate, Amount }
}

sealed class InvoiceCreationEvent : UiEvent {
    object NavigateBack : InvoiceCreationEvent()
    data class ShowError(val message: String) : InvoiceCreationEvent()
    data class ShowSuccess(val message: String) : InvoiceCreationEvent()
}

class InvoiceCreationViewModel(
    private val repository: InvoiceRepository
) : MviViewModel<InvoiceCreationState, InvoiceCreationAction, InvoiceCreationEvent>() {

    override fun createInitialState(): InvoiceCreationState {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return InvoiceCreationState(issueDate = today, dueDate = today)
    }

    override fun handleAction(action: InvoiceCreationAction) {
        when (action) {
            is InvoiceCreationAction.ImageSelected -> processImage(action.uri, action.context)
            is InvoiceCreationAction.SaveInvoice -> saveInvoice()
            is InvoiceCreationAction.UpdateField -> updateField(action.field, action.value)
        }
    }

    private fun updateField(field: InvoiceCreationAction.Field, value: String) {
        setState {
            when (field) {
                InvoiceCreationAction.Field.CustomerName -> copy(customerName = value)
                InvoiceCreationAction.Field.PhoneNumber -> copy(phoneNumber = value)
                InvoiceCreationAction.Field.Email -> copy(email = value)
                InvoiceCreationAction.Field.IssueDate -> copy(issueDate = value)
                InvoiceCreationAction.Field.DueDate -> copy(dueDate = value)
                InvoiceCreationAction.Field.Amount -> copy(amount = value)
            }
        }
    }

    private fun processImage(uri: Uri, context: Context) {
        viewModelScope.launch {
            setState { copy(imageUri = uri, isAiExtracting = true, compressedImage = null) }
            val imageBytes = ImageUtils.compressImage(context, uri)
            if (imageBytes == null) {
                sendEvent(InvoiceCreationEvent.ShowError("Failed to read or compress image."))
                setState { copy(isAiExtracting = false, imageUri = null) }
                return@launch
            }
            setState { copy(compressedImage = imageBytes) }

            GeminiApiClient.extractInvoiceData(imageBytes)
                .onSuccess { data ->
                    setState {
                        copy(
                            customerName = data.customer_name ?: customerName,
                            phoneNumber = data.phone_number ?: phoneNumber,
                            email = data.email ?: email,
                            issueDate = data.date ?: issueDate,
                            dueDate = data.due_date ?: dueDate,
                            amount = data.total_amount?.toString() ?: amount,
                            isAiExtracting = false
                        )
                    }
                }
                .onFailure {
                    sendEvent(InvoiceCreationEvent.ShowError(it.message ?: "AI Extraction Failed. Please enter details manually."))
                    setState { copy(isAiExtracting = false) }
                }
        }
    }

    private fun saveInvoice() {
        if (!currentState.isFormValid) {
            sendEvent(InvoiceCreationEvent.ShowError("Please fill all required fields and select an image."))
            return
        }
        viewModelScope.launch {
            setState { copy(isSaving = true) }
            val state = currentState
            repository.addInvoice(
                customerName = state.customerName,
                customerPhone = state.phoneNumber.takeIf { it.isNotBlank() },
                customerEmail = state.email.takeIf { it.isNotBlank() },
                issueDate = state.issueDate,
                dueDate = state.dueDate,
                totalAmount = state.amount.toDouble(),
                imageBytes = state.compressedImage!!
            ).onSuccess {
                sendEvent(InvoiceCreationEvent.ShowSuccess("Invoice saved successfully!"))
                sendEvent(InvoiceCreationEvent.NavigateBack)
            }.onFailure { error ->
                sendEvent(InvoiceCreationEvent.ShowError(error.message ?: "Failed to save invoice."))
            }
            setState { copy(isSaving = false) }
        }
    }
}
