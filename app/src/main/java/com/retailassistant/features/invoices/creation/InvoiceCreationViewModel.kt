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
        get() = customerName.trim().isNotBlank() &&
                customerName.trim().length >= 2 &&
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
    data class ShowError(val message: String) : InvoiceCreationEvent
}

class InvoiceCreationViewModel(
    private val repository: RetailRepository,
    private val geminiClient: GeminiClient,
    private val imageHandler: ImageHandler,
    private val supabase: SupabaseClient,
    private val savedStateHandle: SavedStateHandle // Injected by Koin
) : MviViewModel<InvoiceCreationState, InvoiceCreationAction, InvoiceCreationEvent>() {

    // Keys for SavedStateHandle
    private companion object {
        const val CUSTOMER_NAME_KEY = "customerName"
        const val CUSTOMER_ID_KEY = "selectedCustomerId"
        const val PHONE_KEY = "phoneNumber"
        const val EMAIL_KEY = "email"
        const val ISSUE_DATE_KEY = "issueDate"
        const val DUE_DATE_KEY = "dueDate"
        const val AMOUNT_KEY = "amount"
        const val IMAGE_URI_KEY = "scannedImageUri"
        const val AI_EXTRACTING_KEY = "isAiExtracting"
        const val IS_SAVING_KEY = "isSaving"
    }

    private var imageBytes: ByteArray? = null
    private var aiExtractionJob: Job? = null
    private val userId: String? = supabase.auth.currentUserOrNull()?.id

    // State is now derived from SavedStateHandle, making it survive process death.
    private val _customers = MutableStateFlow<List<Customer>>(emptyList())

    private val savedState = combine(
        savedStateHandle.getStateFlow(CUSTOMER_NAME_KEY, ""),
        savedStateHandle.getStateFlow<String?>(CUSTOMER_ID_KEY, null),
        savedStateHandle.getStateFlow(PHONE_KEY, ""),
        savedStateHandle.getStateFlow(EMAIL_KEY, ""),
        savedStateHandle.getStateFlow(ISSUE_DATE_KEY, LocalDate.now().toEpochDay()),
        savedStateHandle.getStateFlow(DUE_DATE_KEY, LocalDate.now().plusDays(30).toEpochDay()),
        savedStateHandle.getStateFlow(AMOUNT_KEY, ""),
        savedStateHandle.getStateFlow<String?>(IMAGE_URI_KEY, null),
        savedStateHandle.getStateFlow(AI_EXTRACTING_KEY, false),
        savedStateHandle.getStateFlow(IS_SAVING_KEY, false),
        _customers
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        InvoiceCreationState(
            customerName = args[0] as String,
            selectedCustomerId = args[1] as String?,
            phoneNumber = args[2] as String,
            email = args[3] as String,
            issueDate = LocalDate.ofEpochDay(args[4] as Long),
            dueDate = LocalDate.ofEpochDay(args[5] as Long),
            amount = args[6] as String,
            scannedImageUri = (args[7] as String?)?.toUri(),
            isAiExtracting = args[8] as Boolean,
            isSaving = args[9] as Boolean,
            customers = args[10] as List<Customer>
        )
    }

    private val _uiState = MutableStateFlow(createInitialState())
    override val uiState: StateFlow<InvoiceCreationState> = _uiState.asStateFlow()

    init {
        // Collect from the combined saved state and update the final UI state
        viewModelScope.launch {
            savedState.collect {
                _uiState.value = it
            }
        }

        if (userId != null) {
            repository.getCustomersStream(userId)
                .onEach { _customers.value = it }
                .launchIn(viewModelScope)
        }
    }

    override fun createInitialState(): InvoiceCreationState = InvoiceCreationState()

    override fun handleAction(action: InvoiceCreationAction) {
        when (action) {
            is InvoiceCreationAction.ImageSelected -> processImage(action.uri)
            is InvoiceCreationAction.SaveInvoice -> saveInvoice()
            is InvoiceCreationAction.UpdateCustomerName -> {
                savedStateHandle[CUSTOMER_NAME_KEY] = action.value
                savedStateHandle[CUSTOMER_ID_KEY] = null // Clear selected ID when name changes
            }
            is InvoiceCreationAction.CustomerSelected -> {
                savedStateHandle[CUSTOMER_NAME_KEY] = action.customer.name
                savedStateHandle[CUSTOMER_ID_KEY] = action.customer.id
                savedStateHandle[PHONE_KEY] = action.customer.phone ?: ""
                savedStateHandle[EMAIL_KEY] = action.customer.email ?: ""
            }
            is InvoiceCreationAction.UpdatePhoneNumber -> savedStateHandle[PHONE_KEY] = action.value
            is InvoiceCreationAction.UpdateEmail -> savedStateHandle[EMAIL_KEY] = action.value
            is InvoiceCreationAction.UpdateIssueDate -> savedStateHandle[ISSUE_DATE_KEY] = action.value.toEpochDay()
            is InvoiceCreationAction.UpdateDueDate -> savedStateHandle[DUE_DATE_KEY] = action.value.toEpochDay()
            is InvoiceCreationAction.UpdateAmount -> savedStateHandle[AMOUNT_KEY] = action.value.filter { it.isDigit() || it == '.' }
            is InvoiceCreationAction.ClearImage -> clearImageData()
            is InvoiceCreationAction.ShowScannerError -> sendEvent(InvoiceCreationEvent.ShowError("Scanner unavailable."))
        }
    }

    private fun processImage(uri: Uri) {
        aiExtractionJob?.cancel()
        // Save URI to SavedStateHandle IMMEDIATELY. This is the most crucial step.
        savedStateHandle[IMAGE_URI_KEY] = uri.toString()

        aiExtractionJob = viewModelScope.launch {
            savedStateHandle[AI_EXTRACTING_KEY] = true
            try {
                imageBytes = imageHandler.compressImageForUpload(uri)
                if (imageBytes == null) {
                    sendEvent(InvoiceCreationEvent.ShowError("Failed to process image. Please try a different image."))
                    clearImageData()
                    return@launch
                }
                geminiClient.extractInvoiceData(imageBytes!!)
                    .onSuccess { data ->
                        val matchedCustomer = data.customerName?.let { name ->
                            _uiState.value.customers.find { it.name.equals(name, ignoreCase = true) }
                        }
                        // Update SavedStateHandle with extracted data
                        if (matchedCustomer != null) {
                            handleAction(InvoiceCreationAction.CustomerSelected(matchedCustomer))
                        } else {
                            data.customerName?.let { savedStateHandle[CUSTOMER_NAME_KEY] = it }
                        }
                        data.phoneNumber?.let { savedStateHandle[PHONE_KEY] = it }
                        data.email?.let { savedStateHandle[EMAIL_KEY] = it }
                        parseDate(data.date)?.let { savedStateHandle[ISSUE_DATE_KEY] = it.toEpochDay() }
                        (parseDate(data.dueDate) ?: parseDate(data.date)?.plusDays(30))?.let {
                            savedStateHandle[DUE_DATE_KEY] = it.toEpochDay()
                        }
                        data.totalAmount?.takeIf { it > 0 }?.let { savedStateHandle[AMOUNT_KEY] = it.toString() }

                        sendEvent(InvoiceCreationEvent.ShowError("Data extracted. Please review."))
                    }
                    .onFailure { error ->
                        sendEvent(InvoiceCreationEvent.ShowError(ErrorHandler.getErrorMessage(error, "AI extraction failed.")))
                    }
            } catch (e: Exception) {
                sendEvent(InvoiceCreationEvent.ShowError("Failed to process image: ${e.message}"))
                clearImageData()
            } finally {
                savedStateHandle[AI_EXTRACTING_KEY] = false
            }
        }
    }

    private fun saveInvoice() {
        if (userId == null) {
            sendEvent(InvoiceCreationEvent.ShowError("User not authenticated. Please sign in again."))
            return
        }
        if (!_uiState.value.isFormValid) {
            sendEvent(InvoiceCreationEvent.ShowError("Please fill all required fields."))
            return
        }

        // We need to re-compress the image if the process was killed, as imageBytes is not saved.
        viewModelScope.launch {
            savedStateHandle[IS_SAVING_KEY] = true
            try {
                val currentImageBytes = imageBytes ?: _uiState.value.scannedImageUri?.let {
                    imageHandler.compressImageForUpload(it)
                }

                if (currentImageBytes == null) {
                    sendEvent(InvoiceCreationEvent.ShowError("Image data is missing. Please re-scan."))
                    savedStateHandle[IS_SAVING_KEY] = false
                    return@launch
                }

                val state = _uiState.value
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
                    sendEvent(InvoiceCreationEvent.ShowError(error.message ?: "Failed to save invoice."))
                }
            } catch (e: Exception) {
                sendEvent(InvoiceCreationEvent.ShowError("Unexpected error: ${e.message}"))
            } finally {
                savedStateHandle[IS_SAVING_KEY] = false
            }
        }
    }

    private fun clearImageData() {
        aiExtractionJob?.cancel()
        imageBytes = null
        savedStateHandle[IMAGE_URI_KEY] = null
        savedStateHandle[AI_EXTRACTING_KEY] = false
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