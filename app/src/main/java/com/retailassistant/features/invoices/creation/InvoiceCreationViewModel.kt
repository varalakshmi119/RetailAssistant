package com.retailassistant.features.invoices.creation
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.retailassistant.core.ErrorHandler
import com.retailassistant.core.ImageHandler
import com.retailassistant.core.MviViewModel
import com.retailassistant.core.UiAction
import com.retailassistant.core.UiEvent
import com.retailassistant.core.UiState
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
        get() = customerName.trim().length >= 2 &&
                (amount.toDoubleOrNull() ?: 0.0) > 0.0 &&
                scannedImageUri != null &&
                dueDate.isAfter(issueDate) && // Ensure due date is after issue date
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
    private val savedStateHandle: SavedStateHandle,
    private val repository: RetailRepository,
    private val geminiClient: GeminiClient,
    private val imageHandler: ImageHandler,
    supabase: SupabaseClient
) : MviViewModel<InvoiceCreationState, InvoiceCreationAction, InvoiceCreationEvent>() {
    private var imageBytes: ByteArray? = null
    private var aiExtractionJob: Job? = null
    private val userId: String? = supabase.auth.currentUserOrNull()?.id
    init {
        restoreState()
        if (userId != null) {
            repository.getCustomersStream(userId)
                .onEach { customers -> setState { copy(customers = customers) } }
                .launchIn(viewModelScope)
        }
    }
    override fun createInitialState() = InvoiceCreationState()
    override fun handleAction(action: InvoiceCreationAction) {
        when (action) {
            is InvoiceCreationAction.ImageSelected -> processImage(action.uri)
            is InvoiceCreationAction.SaveInvoice -> saveInvoice()
            is InvoiceCreationAction.UpdateCustomerName -> updateState(Keys.CUSTOMER_NAME, action.value) { copy(customerName = it ?: "", selectedCustomerId = null) }
            is InvoiceCreationAction.CustomerSelected -> selectCustomer(action.customer)
            is InvoiceCreationAction.UpdatePhoneNumber -> updateState(Keys.PHONE, action.value) { copy(phoneNumber = it ?: "") }
            is InvoiceCreationAction.UpdateEmail -> updateState(Keys.EMAIL, action.value) { copy(email = it ?: "") }
            is InvoiceCreationAction.UpdateIssueDate -> updateState(Keys.ISSUE_DATE, action.value.toEpochDay()) { copy(issueDate = action.value) }
            is InvoiceCreationAction.UpdateDueDate -> updateState(Keys.DUE_DATE, action.value.toEpochDay()) { copy(dueDate = action.value) }
            is InvoiceCreationAction.UpdateAmount -> {
                val filtered = action.value.filter { it.isDigit() || it == '.' }
                updateState(Keys.AMOUNT, filtered) { copy(amount = filtered) }
            }
            is InvoiceCreationAction.ClearImage -> clearImageData()
            is InvoiceCreationAction.ShowScannerError -> sendEvent(InvoiceCreationEvent.ShowMessage("Document scanner is unavailable on this device."))
        }
    }
    private fun processImage(uri: Uri) {
        aiExtractionJob?.cancel()
        updateState(Keys.IMAGE_URI, uri.toString()) { copy(scannedImageUri = uri, isAiExtracting = true) }
        aiExtractionJob = viewModelScope.launch {
            imageHandler.compressImageForUpload(uri)
                .onSuccess { bytes ->
                    imageBytes = bytes
                    extractDataFromImage(bytes)
                }
                .onFailure { error ->
                    sendEvent(InvoiceCreationEvent.ShowMessage(error.message ?: "Failed to process image."))
                    clearImageData()
                }
        }
    }
    private suspend fun extractDataFromImage(bytes: ByteArray) {
        geminiClient.extractInvoiceData(bytes)
            .onSuccess { data ->
                data.customerName?.let { name ->
                    val matched = uiState.value.customers.find { it.name.equals(name, ignoreCase = true) }
                    if (matched != null) sendAction(InvoiceCreationAction.CustomerSelected(matched))
                    else sendAction(InvoiceCreationAction.UpdateCustomerName(name))
                }
                data.phoneNumber?.let { sendAction(InvoiceCreationAction.UpdatePhoneNumber(it)) }
                data.email?.let { sendAction(InvoiceCreationAction.UpdateEmail(it)) }
                parseDate(data.date)?.let { sendAction(InvoiceCreationAction.UpdateIssueDate(it)) }
                (parseDate(data.dueDate) ?: parseDate(data.date)?.plusDays(30))?.let { sendAction(InvoiceCreationAction.UpdateDueDate(it)) }
                data.totalAmount?.takeIf { it > 0 }?.let { sendAction(InvoiceCreationAction.UpdateAmount(it.toString())) }
                sendEvent(InvoiceCreationEvent.ShowMessage("AI extracted data. Please review."))
            }
            .onFailure { error ->
                sendEvent(InvoiceCreationEvent.ShowMessage(ErrorHandler.getErrorMessage(error, "AI extraction failed.")))
            }
        setState { copy(isAiExtracting = false) }
    }
    private fun saveInvoice() {
        if (userId == null) {
            sendEvent(InvoiceCreationEvent.ShowMessage("User not authenticated. Please sign in again."))
            return
        }
        val state = uiState.value
        if (!state.isFormValid) {
            val errorMessage = when {
                state.customerName.trim().length < 2 -> "Customer name must be at least 2 characters."
                (state.amount.toDoubleOrNull() ?: 0.0) <= 0.0 -> "Amount must be greater than zero."
                state.scannedImageUri == null -> "Please scan an invoice image."
                !state.dueDate.isAfter(state.issueDate) -> "Due date must be after issue date."
                else -> "Please fill all required fields and scan an invoice."
            }
            sendEvent(InvoiceCreationEvent.ShowMessage(errorMessage))
            return
        }
        viewModelScope.launch {
            setState { copy(isSaving = true) }
            val currentImageBytes = imageBytes
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
                setState { copy(isSaving = false) }
            }
        }
    }
    private fun selectCustomer(customer: Customer) {
        savedStateHandle[Keys.CUSTOMER_NAME] = customer.name
        savedStateHandle[Keys.CUSTOMER_ID] = customer.id
        savedStateHandle[Keys.PHONE] = customer.phone ?: ""
        savedStateHandle[Keys.EMAIL] = customer.email ?: ""
        setState {
            copy(
                customerName = customer.name,
                selectedCustomerId = customer.id,
                phoneNumber = customer.phone ?: "",
                email = customer.email ?: ""
            )
        }
    }
    private fun clearImageData() {
        aiExtractionJob?.cancel()
        imageBytes = null
        updateState(Keys.IMAGE_URI, null) { copy(scannedImageUri = null, isAiExtracting = false) }
    }
    private fun parseDate(dateStr: String?): LocalDate? {
        return dateStr?.let { try { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) } catch (e: DateTimeParseException) { null } }
    }
    // --- State Persistence ---
    private object Keys {
        const val CUSTOMER_NAME = "customerName"
        const val CUSTOMER_ID = "selectedCustomerId"
        const val PHONE = "phoneNumber"
        const val EMAIL = "email"
        const val ISSUE_DATE = "issueDate"
        const val DUE_DATE = "dueDate"
        const val AMOUNT = "amount"
        const val IMAGE_URI = "scannedImageUri"
    }
    private fun <T> updateState(key: String, value: T?, reducer: InvoiceCreationState.(T?) -> InvoiceCreationState) {
        savedStateHandle[key] = value
        setState { reducer(value) }
    }
    private fun restoreState() {
        setState {
            copy(
                customerName = savedStateHandle[Keys.CUSTOMER_NAME] ?: "",
                selectedCustomerId = savedStateHandle[Keys.CUSTOMER_ID],
                phoneNumber = savedStateHandle[Keys.PHONE] ?: "",
                email = savedStateHandle[Keys.EMAIL] ?: "",
                issueDate = (savedStateHandle.get<Long>(Keys.ISSUE_DATE))?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now(),
                dueDate = (savedStateHandle.get<Long>(Keys.DUE_DATE))?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now().plusDays(30),
                amount = savedStateHandle[Keys.AMOUNT] ?: "",
                scannedImageUri = savedStateHandle.get<String>(Keys.IMAGE_URI)?.toUri()
            )
        }
    }
}
