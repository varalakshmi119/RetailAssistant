package com.example.retailassistant.features.invoices

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.retailassistant.core.ImageHandler
import com.example.retailassistant.core.MviViewModel
import com.example.retailassistant.core.UiAction
import com.example.retailassistant.core.UiEvent
import com.example.retailassistant.core.UiState
import com.example.retailassistant.core.Utils
import com.example.retailassistant.data.db.Customer
import com.example.retailassistant.data.remote.GeminiClient
import com.example.retailassistant.data.repository.RetailRepository
import com.example.retailassistant.ui.components.AutoCompleteTextField
import com.example.retailassistant.ui.components.EnhancedDatePickerField
import com.example.retailassistant.ui.components.LabeledTextField
import com.example.retailassistant.ui.components.PrimaryButton
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter


// --- MVI Definitions ---
data class InvoiceCreationState(
    val customerName: String = "",
    val selectedCustomerId: String? = null,
    val phoneNumber: String = "",
    val email: String = "",
    val issueDate: String = "",
    val dueDate: String = "",
    val amount: String = "",
    val imageUri: Uri? = null,
    private val compressedImage: ByteArray? = null, // Kept private to enforce access through getter
    val isAiExtracting: Boolean = false,
    val isSaving: Boolean = false,
    val customers: List<Customer> = emptyList() // For autocomplete
) : UiState {
    val isFormValid: Boolean
        get() = customerName.isNotBlank() &&
                Utils.isValidDate(issueDate) &&
                Utils.isValidDate(dueDate) &&
                (amount.toDoubleOrNull() ?: 0.0) > 0.0 &&
                compressedImage != null

    fun getCompressedImageBytes(): ByteArray? = compressedImage
}

sealed interface InvoiceCreationAction : UiAction {
    data class ImageSelected(val uri: Uri, val context: Context) : InvoiceCreationAction
    object SaveInvoice : InvoiceCreationAction
    data class UpdateCustomerName(val value: String) : InvoiceCreationAction
    data class CustomerSelected(val customer: Customer) : InvoiceCreationAction
    data class CustomersLoaded(val customers: List<Customer>) : InvoiceCreationAction
    data class UpdatePhoneNumber(val value: String) : InvoiceCreationAction
    data class UpdateEmail(val value: String) : InvoiceCreationAction
    data class UpdateIssueDate(val value: String) : InvoiceCreationAction
    data class UpdateDueDate(val value: String) : InvoiceCreationAction
    data class UpdateAmount(val value: String) : InvoiceCreationAction
    data class SetCompressedImage(val bytes: ByteArray?) : InvoiceCreationAction
}

sealed interface InvoiceCreationEvent : UiEvent {
    object NavigateBack : InvoiceCreationEvent
    data class ShowError(val message: String) : InvoiceCreationEvent
    data class ShowSuccess(val message: String) : InvoiceCreationEvent
}

// --- ViewModel ---
class InvoiceCreationViewModel(
    private val repository: RetailRepository,
    private val geminiClient: GeminiClient,
    private val imageHandler: ImageHandler,
) : MviViewModel<InvoiceCreationState, InvoiceCreationAction, InvoiceCreationEvent>() {

    override fun createInitialState(): InvoiceCreationState {
        // Pre-populate dates for user convenience.
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val oneMonthFromToday = LocalDate.now().plusMonths(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        return InvoiceCreationState(issueDate = today, dueDate = oneMonthFromToday)
    }

    init {
        // Start collecting the customer list for the autocomplete feature.
        repository.getCustomersStream()
            .onEach { customers -> sendAction(InvoiceCreationAction.CustomersLoaded(customers)) }
            .launchIn(viewModelScope)
    }

    override fun handleAction(action: InvoiceCreationAction) {
        when (action) {
            is InvoiceCreationAction.ImageSelected -> processImage(action.uri, action.context)
            is InvoiceCreationAction.SaveInvoice -> saveInvoice()
            is InvoiceCreationAction.UpdateCustomerName -> {
                // When the name is manually changed, clear any selected customer ID.
                setState { copy(customerName = action.value, selectedCustomerId = null) }
            }
            is InvoiceCreationAction.CustomerSelected -> {
                setState {
                    copy(
                        customerName = action.customer.name,
                        selectedCustomerId = action.customer.id,
                        phoneNumber = action.customer.phone ?: "",
                        email = action.customer.email ?: ""
                    )
                }
            }
            is InvoiceCreationAction.CustomersLoaded -> setState { copy(customers = action.customers) }
            is InvoiceCreationAction.UpdatePhoneNumber -> setState { copy(phoneNumber = action.value) }
            is InvoiceCreationAction.UpdateEmail -> setState { copy(email = action.value) }
            is InvoiceCreationAction.UpdateIssueDate -> setState { copy(issueDate = action.value) }
            is InvoiceCreationAction.UpdateDueDate -> setState { copy(dueDate = action.value) }
            is InvoiceCreationAction.UpdateAmount -> setState { copy(amount = action.value) }
            is InvoiceCreationAction.SetCompressedImage -> setState { copy(compressedImage = action.bytes) }
        }
    }

    private fun processImage(uri: Uri, context: Context) {
        viewModelScope.launch {
            setState { copy(imageUri = uri, isAiExtracting = true, compressedImage = null) }
            val imageBytes = imageHandler.compressImageForUpload(context, uri, isDocument = true)
            if (imageBytes == null) {
                sendEvent(InvoiceCreationEvent.ShowError("Failed to read or compress image."))
                setState { copy(isAiExtracting = false, imageUri = null) }
                return@launch
            }
            sendAction(InvoiceCreationAction.SetCompressedImage(imageBytes))

            geminiClient.extractInvoiceData(imageBytes)
                .onSuccess { data ->
                    // Attempt to match the extracted name with an existing customer.
                    val matchedCustomer = data.customer_name?.let { name ->
                        currentState.customers.find { it.name.equals(name, ignoreCase = true) }
                    }
                    if (matchedCustomer != null) {
                        sendAction(InvoiceCreationAction.CustomerSelected(matchedCustomer))
                    }
                    setState {
                        copy(
                            // Only update customer name if we didn't match an existing one
                            customerName = if (matchedCustomer != null) customerName else data.customer_name ?: customerName,
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
            sendEvent(InvoiceCreationEvent.ShowError("Please fill all required fields and scan the invoice document."))
            return
        }
        viewModelScope.launch {
            setState { copy(isSaving = true) }
            val state = currentState
            repository.addInvoice(
                existingCustomerId = state.selectedCustomerId,
                customerName = state.customerName,
                customerPhone = state.phoneNumber,
                customerEmail = state.email,
                issueDate = state.issueDate,
                dueDate = state.dueDate,
                totalAmount = state.amount.toDouble(),
                imageBytes = state.getCompressedImageBytes()!!
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

// --- Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceCreationScreen(
    onNavigateBack: () -> Unit,
    viewModel: InvoiceCreationViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val snackbarHostState = remember { SnackbarHostState() }

    // MLKit Document Scanner setup
    val documentScannerOptions = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(1)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .build()
    val documentScanner = GmsDocumentScanning.getClient(documentScannerOptions)
    val documentScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanningResult?.pages?.firstOrNull()?.imageUri?.let { uri ->
                viewModel.sendAction(InvoiceCreationAction.ImageSelected(uri, context))
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is InvoiceCreationEvent.NavigateBack -> onNavigateBack()
                is InvoiceCreationEvent.ShowError -> snackbarHostState.showSnackbar(event.message, withDismissAction = true, duration = SnackbarDuration.Long)
                is InvoiceCreationEvent.ShowSuccess -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Invoice", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // A persistent bottom bar for the primary action is great for UX.
            Surface(shadowElevation = 8.dp) {
                PrimaryButton(
                    text = "Save Invoice",
                    onClick = { viewModel.sendAction(InvoiceCreationAction.SaveInvoice) },
                    isLoading = state.isSaving,
                    enabled = state.isFormValid,
                    modifier = Modifier.padding(16.dp).navigationBarsPadding()
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ImageSelectionBox(
                    imageUri = state.imageUri,
                    isAiExtracting = state.isAiExtracting,
                    onClick = {
                        documentScanner.getStartScanIntent(activity)
                            .addOnSuccessListener { intentSender ->
                                documentScannerLauncher.launch(
                                    IntentSenderRequest.Builder(intentSender).build()
                                )
                            }
                            .addOnFailureListener { exception ->
                                viewModel.sendEvent(InvoiceCreationEvent.ShowError("Scanner failed: ${exception.message}"))
                            }
                    }
                )
                if (state.isAiExtracting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }

            item { Text("Invoice Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            
            item {
                AutoCompleteTextField(
                    value = state.customerName,
                    onValueChange = { viewModel.sendAction(InvoiceCreationAction.UpdateCustomerName(it)) },
                    onItemSelected = { customer -> viewModel.sendAction(InvoiceCreationAction.CustomerSelected(customer)) },
                    label = "Customer Name*",
                    suggestions = state.customers,
                    enabled = !state.isSaving,
                    leadingIcon = { Icon(Icons.Default.Person, null) }
                )
            }
            item {
                LabeledTextField(
                    value = state.amount,
                    onValueChange = { viewModel.sendAction(InvoiceCreationAction.UpdateAmount(it)) },
                    label = "Total Amount*",
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = "₹"
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    EnhancedDatePickerField(
                        modifier = Modifier.weight(1f),
                        value = state.issueDate,
                        onValueChange = { viewModel.sendAction(InvoiceCreationAction.UpdateIssueDate(it)) },
                        label = "Issue Date*",
                        enabled = !state.isSaving
                    )
                    EnhancedDatePickerField(
                        modifier = Modifier.weight(1f),
                        value = state.dueDate,
                        onValueChange = { viewModel.sendAction(InvoiceCreationAction.UpdateDueDate(it)) },
                        label = "Due Date*",
                        enabled = !state.isSaving
                    )
                }
            }

            item { Text("Contact Info (Optional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            
            item {
                LabeledTextField(
                    value = state.phoneNumber,
                    onValueChange = { viewModel.sendAction(InvoiceCreationAction.UpdatePhoneNumber(it)) },
                    label = "Phone Number",
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = { Icon(Icons.Default.Phone, null) }
                )
            }
            item {
                LabeledTextField(
                    value = state.email,
                    onValueChange = { viewModel.sendAction(InvoiceCreationAction.UpdateEmail(it)) },
                    label = "Email",
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    leadingIcon = { Icon(Icons.Default.Email, null) }
                )
            }
        }
    }
}

@Composable
private fun ImageSelectionBox(imageUri: Uri?, isAiExtracting: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(200.dp).clickable(onClick = onClick, enabled = !isAiExtracting),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Scanned Invoice",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.CameraAlt, "Scan Document",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Tap to scan invoice", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("AI will attempt to extract details", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
