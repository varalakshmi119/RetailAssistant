package com.retailassistant.features.invoices.creation

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.retailassistant.core.*
import com.retailassistant.data.db.Customer
import com.retailassistant.data.remote.GeminiClient
import com.retailassistant.data.repository.RetailRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class InvoiceCreationState(
    val customerName: String = "",
    val selectedCustomerId: String? = null,
    val phoneNumber: String = "",
    val email: String = "",
    val issueDate: LocalDate = LocalDate.now(),
    val dueDate: LocalDate = LocalDate.now().plusDays(30),
    val amount: String = "",
    val scannedImageUri: Uri? = null,
    val isAiExtracting: Boolean = false,
    val isSaving: Boolean = false,
    val customers: List<Customer> = emptyList()
) : UiState {
    val isFormValid: Boolean
        get() = customerName.isNotBlank() &&
                (amount.toDoubleOrNull() ?: 0.0) > 0.0 &&
                scannedImageUri != null
}

sealed interface InvoiceCreationAction : UiAction {
    data class ImageSelected(val uri: Uri) : InvoiceCreationAction
    object SaveInvoice : InvoiceCreationAction
    data class UpdateCustomerName(val value: String) : InvoiceCreationAction
    data class CustomerSelected(val customer: Customer) : InvoiceCreationAction
    data class CustomersLoaded(val customers: List<Customer>) : InvoiceCreationAction
    data class UpdatePhoneNumber(val value: String) : InvoiceCreationAction
    data class UpdateEmail(val value: String) : InvoiceCreationAction
    data class UpdateIssueDate(val value: LocalDate) : InvoiceCreationAction
    data class UpdateDueDate(val value: LocalDate) : InvoiceCreationAction
    data class UpdateAmount(val value: String) : InvoiceCreationAction
    object ClearImage : InvoiceCreationAction
    object ShowScannerError : InvoiceCreationAction
}

sealed interface InvoiceCreationEvent : UiEvent {
    object NavigateBack : InvoiceCreationEvent
    data class ShowError(val message: String) : InvoiceCreationEvent
}

class InvoiceCreationViewModel(
    private val repository: RetailRepository,
    private val geminiClient: GeminiClient,
    private val imageHandler: ImageHandler,
    private val supabase: SupabaseClient
) : MviViewModel<InvoiceCreationState, InvoiceCreationAction, InvoiceCreationEvent>() {

    private var imageBytes: ByteArray? = null
    private var aiExtractionJob: Job? = null

    init {
        val userId = supabase.auth.currentUserOrNull()?.id
        if (userId != null) {
            repository.getCustomersStream(userId)
                .onEach { customers -> sendAction(InvoiceCreationAction.CustomersLoaded(customers)) }
                .launchIn(viewModelScope)
        }
    }

    override fun createInitialState(): InvoiceCreationState = InvoiceCreationState()

    override fun handleAction(action: InvoiceCreationAction) {
        when (action) {
            is InvoiceCreationAction.ImageSelected -> processImage(action.uri)
            is InvoiceCreationAction.SaveInvoice -> saveInvoice()
            is InvoiceCreationAction.UpdateCustomerName -> setState { copy(customerName = action.value, selectedCustomerId = null) }
            is InvoiceCreationAction.CustomerSelected -> setState {
                copy(
                    customerName = action.customer.name,
                    selectedCustomerId = action.customer.id,
                    phoneNumber = action.customer.phone ?: "",
                    email = action.customer.email ?: ""
                )
            }
            is InvoiceCreationAction.CustomersLoaded -> setState { copy(customers = action.customers) }
            is InvoiceCreationAction.UpdatePhoneNumber -> setState { copy(phoneNumber = action.value) }
            is InvoiceCreationAction.UpdateEmail -> setState { copy(email = action.value) }
            is InvoiceCreationAction.UpdateIssueDate -> setState { copy(issueDate = action.value) }
            is InvoiceCreationAction.UpdateDueDate -> setState { copy(dueDate = action.value) }
            is InvoiceCreationAction.UpdateAmount -> setState { copy(amount = action.value.filter { it.isDigit() || it == '.' }) }
            is InvoiceCreationAction.ClearImage -> clearImageData()
            is InvoiceCreationAction.ShowScannerError -> sendEvent(InvoiceCreationEvent.ShowError("Scanner unavailable."))
        }
    }

    private fun processImage(uri: Uri) {
        aiExtractionJob?.cancel()
        aiExtractionJob = viewModelScope.launch {
            setState { copy(scannedImageUri = uri, isAiExtracting = true) }
            imageBytes = imageHandler.compressImageForUpload(uri)

            if (imageBytes == null) {
                sendEvent(InvoiceCreationEvent.ShowError("Failed to read image."))
                clearImageData()
                return@launch
            }

            geminiClient.extractInvoiceData(imageBytes!!)
                .onSuccess { data ->
                    val matchedCustomer = data.customerName?.let { name: String ->
                        uiState.value.customers.find { customer -> customer.name.equals(name, ignoreCase = true) }
                    }
                    setState {
                        copy(
                            customerName = matchedCustomer?.name ?: data.customerName ?: customerName,
                            selectedCustomerId = matchedCustomer?.id,
                            phoneNumber = matchedCustomer?.phone ?: data.phoneNumber ?: phoneNumber,
                            email = matchedCustomer?.email ?: data.email ?: email,
                            issueDate = parseDate(data.date) ?: issueDate,
                            dueDate = parseDate(data.dueDate) ?: parseDate(data.date)?.plusDays(30) ?: dueDate,
                            amount = data.totalAmount?.toString() ?: amount,
                            isAiExtracting = false
                        )
                    }
                }
                .onFailure { error ->
                    sendEvent(InvoiceCreationEvent.ShowError(error.message ?: "AI Extraction Failed."))
                    setState { copy(isAiExtracting = false) }
                }
        }
    }

    private fun saveInvoice() {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        if (!uiState.value.isFormValid) {
            sendEvent(InvoiceCreationEvent.ShowError("Please fill all required fields."))
            return
        }
        val currentImageBytes = imageBytes ?: return

        viewModelScope.launch {
            setState { copy(isSaving = true) }
            repository.addInvoice(
                userId = userId,
                existingCustomerId = uiState.value.selectedCustomerId,
                customerName = uiState.value.customerName,
                customerPhone = uiState.value.phoneNumber.takeIf { phone -> phone.isNotBlank() },
                customerEmail = uiState.value.email.takeIf { email -> email.isNotBlank() },
                issueDate = uiState.value.issueDate,
                dueDate = uiState.value.dueDate,
                totalAmount = uiState.value.amount.toDouble(),
                imageBytes = currentImageBytes
            ).onSuccess {
                sendEvent(InvoiceCreationEvent.NavigateBack)
            }.onFailure { error ->
                sendEvent(InvoiceCreationEvent.ShowError(error.message ?: "Failed to save invoice."))
            }
            setState { copy(isSaving = false) }
        }
    }

    private fun clearImageData() {
        aiExtractionJob?.cancel()
        imageBytes = null
        setState { copy(scannedImageUri = null, isAiExtracting = false) }
    }

    private fun parseDate(dateStr: String?): LocalDate? {
        return dateStr?.let {
            try {
                LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e: DateTimeParseException) {
                null
            }
        }
    }
}
