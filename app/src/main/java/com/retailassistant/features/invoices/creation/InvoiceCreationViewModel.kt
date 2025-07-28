package com.retailassistant.features.invoices.creation

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.retailassistant.core.*
import com.retailassistant.data.db.Customer
import com.retailassistant.data.remote.GeminiClient
import com.retailassistant.data.repository.RetailRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
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
        get() = customerName.trim().length >= 2 &&
                (amount.toDoubleOrNull() ?: 0.0) > 0.0 &&
                scannedImageUri != null &&
                !isAiExtracting &&
                !isSaving
}

sealed interface InvoiceCreationAction : UiAction {
    data class ImageSelected(val uri: Uri) : InvoiceCreationAction
    object SaveInvoice : InvoiceCreationAction
    data class UpdateCustomerName(val value: String) : InvoiceCreationAction
    data class CustomerSelected(val customer: Customer) : InvoiceCreationAction
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
    data class ShowMessage(val message: String) : InvoiceCreationEvent
}

class InvoiceCreationViewModel(
    private val repository: RetailRepository,
    private val geminiClient: GeminiClient,
    private val imageHandler: ImageHandler,
    private val supabase: SupabaseClient,
    private val savedStateHandle: SavedStateHandle
) : MviViewModel<InvoiceCreationState, InvoiceCreationAction, InvoiceCreationEvent>() {

    private companion object {
        const val CUSTOMER_NAME_KEY = "customerName"
        const val CUSTOMER_ID_KEY = "selectedCustomerId"
        const val PHONE_KEY = "phoneNumber"
        const val EMAIL_KEY = "email"
        const val ISSUE_DATE_KEY = "issueDate"
        const val DUE_DATE_KEY = "dueDate"
        const val AMOUNT_KEY = "amount"
        const val IMAGE_URI_KEY = "scannedImageUri"
    }

    private var imageBytes: ByteArray? = null
    private var aiExtractionJob: Job? = null
    private val userId: String? = supabase.auth.currentUserOrNull()?.id

    init {
        if (userId != null) {
            repository.getCustomersStream(userId)
                .onEach { customers -> setState { copy(customers = customers) } }
                .launchIn(viewModelScope)
        }
        // Restore state from SavedStateHandle on init
        setState {
            copy(
                customerName = savedStateHandle[CUSTOMER_NAME_KEY] ?: "",
                selectedCustomerId = savedStateHandle[CUSTOMER_ID_KEY],
                phoneNumber = savedStateHandle[PHONE_KEY] ?: "",
                email = savedStateHandle[EMAIL_KEY] ?: "",
                issueDate = (savedStateHandle.get<Long>(ISSUE_DATE_KEY))?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now(),
                dueDate = (savedStateHandle.get<Long>(DUE_DATE_KEY))?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now().plusDays(30),
                amount = savedStateHandle[AMOUNT_KEY] ?: "",
                scannedImageUri = savedStateHandle.get<String>(IMAGE_URI_KEY)?.toUri()
            )
        }
    }

    override fun createInitialState() = InvoiceCreationState()

    override fun handleAction(action: InvoiceCreationAction) {
        when (action) {
            is InvoiceCreationAction.ImageSelected -> processImage(action.uri)
            is InvoiceCreationAction.SaveInvoice -> saveInvoice()
            is InvoiceCreationAction.UpdateCustomerName -> setStateAndSave(CUSTOMER_NAME_KEY, action.value) { copy(customerName = it, selectedCustomerId = null) }
            is InvoiceCreationAction.CustomerSelected -> {
                savedStateHandle[CUSTOMER_NAME_KEY] = action.customer.name
                savedStateHandle[CUSTOMER_ID_KEY] = action.customer.id
                savedStateHandle[PHONE_KEY] = action.customer.phone ?: ""
                savedStateHandle[EMAIL_KEY] = action.customer.email ?: ""
                setState { copy(
                    customerName = action.customer.name,
                    selectedCustomerId = action.customer.id,
                    phoneNumber = action.customer.phone ?: "",
                    email = action.customer.email ?: ""
                ) }
            }
            is InvoiceCreationAction.UpdatePhoneNumber -> setStateAndSave(PHONE_KEY, action.value) { copy(phoneNumber = it) }
            is InvoiceCreationAction.UpdateEmail -> setStateAndSave(EMAIL_KEY, action.value) { copy(email = it) }
            is InvoiceCreationAction.UpdateIssueDate -> setStateAndSave(ISSUE_DATE_KEY, action.value.toEpochDay()) { copy(issueDate = action.value) }
            is InvoiceCreationAction.UpdateDueDate -> setStateAndSave(DUE_DATE_KEY, action.value.toEpochDay()) { copy(dueDate = action.value) }
            is InvoiceCreationAction.UpdateAmount -> {
                val filtered = action.value.filter { it.isDigit() || it == '.' }
                setStateAndSave(AMOUNT_KEY, filtered) { copy(amount = filtered) }
            }
            is InvoiceCreationAction.ClearImage -> clearImageData()
            is InvoiceCreationAction.ShowScannerError -> sendEvent(InvoiceCreationEvent.ShowMessage("Document scanner is unavailable on this device."))
        }
    }

    private fun <T> setStateAndSave(key: String, value: T, reducer: InvoiceCreationState.(T) -> InvoiceCreationState) {
        savedStateHandle[key] = value
        setState { reducer(value) }
    }

    private fun processImage(uri: Uri) {
        aiExtractionJob?.cancel()
        setStateAndSave(IMAGE_URI_KEY, uri.toString()) { copy(scannedImageUri = uri, isAiExtracting = true) }

        aiExtractionJob = viewModelScope.launch {
            try {
                imageBytes = imageHandler.compressImageForUpload(uri)
                if (imageBytes == null) {
                    sendEvent(InvoiceCreationEvent.ShowMessage("Failed to process image. Please try another."))
                    clearImageData()
                    return@launch
                }

                geminiClient.extractInvoiceData(imageBytes!!)
                    .onSuccess { data ->
                        data.customerName?.let { name ->
                            val matched = uiState.value.customers.find { it.name.equals(name, ignoreCase = true) }
                            if (matched != null) sendAction(
                                InvoiceCreationAction.CustomerSelected(
                                    matched
                                )
                            )
                            else sendAction(InvoiceCreationAction.UpdateCustomerName(name))
                        }
                        data.phoneNumber?.let { sendAction(
                            InvoiceCreationAction.UpdatePhoneNumber(
                                it
                            )
                        ) }
                        data.email?.let { sendAction(InvoiceCreationAction.UpdateEmail(it)) }
                        parseDate(data.date)?.let { sendAction(
                            InvoiceCreationAction.UpdateIssueDate(
                                it
                            )
                        ) }
                        (parseDate(data.dueDate) ?: parseDate(data.date)?.plusDays(30))?.let { sendAction(
                            InvoiceCreationAction.UpdateDueDate(it)
                        ) }
                        data.totalAmount?.takeIf { it > 0 }?.let { sendAction(
                            InvoiceCreationAction.UpdateAmount(
                                it.toString()
                            )
                        ) }
                        sendEvent(InvoiceCreationEvent.ShowMessage("AI extracted data. Please review."))
                    }
                    .onFailure { error -> sendEvent(InvoiceCreationEvent.ShowMessage(ErrorHandler.getErrorMessage(error, "AI extraction failed."))) }
            } catch (e: Exception) {
                sendEvent(InvoiceCreationEvent.ShowMessage("Failed to process image: ${e.message}"))
                clearImageData()
            } finally {
                setState { copy(isAiExtracting = false) }
            }
        }
    }

    private fun saveInvoice() {
        if (userId == null) {
            sendEvent(InvoiceCreationEvent.ShowMessage("User not authenticated. Please sign in again."))
            return
        }
        if (!uiState.value.isFormValid) {
            sendEvent(InvoiceCreationEvent.ShowMessage("Please fill all required fields and scan an invoice."))
            return
        }

        viewModelScope.launch {
            setState { copy(isSaving = true) }
            try {
                val currentImageBytes = imageBytes ?: uiState.value.scannedImageUri?.let { imageHandler.compressImageForUpload(it) }
                if (currentImageBytes == null) {
                    sendEvent(InvoiceCreationEvent.ShowMessage("Image data is missing. Please re-scan."))
                    setState { copy(isSaving = false) }
                    return@launch
                }

                val state = uiState.value
                repository.addInvoice(
                    userId = userId,
                    existingCustomerId = state.selectedCustomerId,
                    customerName = state.customerName.trim(),
                    customerPhone = state.phoneNumber.trim().takeIf { it.isNotBlank() },
                    customerEmail = state.email.trim().takeIf { it.isNotBlank() },
                    issueDate = state.issueDate,
                    dueDate = state.dueDate,
                    totalAmount = state.amount.toDouble(),
                    imageBytes = currentImageBytes
                ).onSuccess {
                    sendEvent(InvoiceCreationEvent.NavigateBack)
                }.onFailure { error ->
                    sendEvent(InvoiceCreationEvent.ShowMessage(error.message ?: "Failed to save invoice."))
                }
            } catch (e: Exception) {
                sendEvent(InvoiceCreationEvent.ShowMessage("An unexpected error occurred: ${e.message}"))
            } finally {
                setState { copy(isSaving = false) }
            }
        }
    }

    private fun clearImageData() {
        aiExtractionJob?.cancel()
        imageBytes = null
        setStateAndSave(IMAGE_URI_KEY, null) { copy(scannedImageUri = null, isAiExtracting = false) }
    }

    private fun parseDate(dateStr: String?): LocalDate? {
        return dateStr?.let { try { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) } catch (e: DateTimeParseException) { null } }
    }
}
