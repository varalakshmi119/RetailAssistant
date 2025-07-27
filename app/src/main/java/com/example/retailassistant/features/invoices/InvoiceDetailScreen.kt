package com.example.retailassistant.features.invoices

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.retailassistant.core.MviViewModel
import com.example.retailassistant.core.UiAction
import com.example.retailassistant.core.UiEvent
import com.example.retailassistant.core.UiState
import com.example.retailassistant.core.Utils
import com.example.retailassistant.data.db.Customer
import com.example.retailassistant.data.db.InteractionLog
import com.example.retailassistant.data.db.Invoice
import com.example.retailassistant.data.db.InvoiceStatus
import com.example.retailassistant.data.repository.RetailRepository
import com.example.retailassistant.ui.components.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

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
    data class LoadImage(val storagePath: String) : InvoiceDetailAction
    object CallCustomer : InvoiceDetailAction

    // Dialog Actions
    data class ShowDialog(val dialog: ActiveDialog?) : InvoiceDetailAction
    data class AddPayment(val amount: Double, val note: String?) : InvoiceDetailAction
    data class AddNote(val note: String) : InvoiceDetailAction
    data class PostponeDueDate(val newDueDate: String, val reason: String?) : InvoiceDetailAction
}

sealed interface InvoiceDetailEvent : UiEvent {
    data class ShowError(val message: String) : InvoiceDetailEvent
    data class ShowSuccess(val message: String) : InvoiceDetailEvent
    data class MakePhoneCall(val phoneNumber: String) : InvoiceDetailEvent
}

// --- ViewModel ---
class InvoiceDetailViewModel(
    private val invoiceId: String,
    private val repository: RetailRepository
) : MviViewModel<InvoiceDetailState, InvoiceDetailAction, InvoiceDetailEvent>() {

    init {
        repository.getInvoiceWithDetails(invoiceId)
            .onEach { (invoice, logs) ->
                sendAction(InvoiceDetailAction.DetailsLoaded(invoice to logs))
                // When invoice details load, trigger loading of related data.
                invoice?.let {
                    loadCustomer(it.customerId)
                    sendAction(InvoiceDetailAction.LoadImage(it.originalScanUrl))
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadCustomer(customerId: String) {
        viewModelScope.launch {
            repository.getCustomerById(customerId).collect { customer ->
                sendAction(InvoiceDetailAction.CustomerLoaded(customer))
            }
        }
    }

    private fun loadImage(storagePath: String) {
        viewModelScope.launch {
            if (currentState.imageUrl == null) { // Only load if not already loaded
                repository.getPublicUrl(storagePath).onSuccess { url ->
                    sendAction(InvoiceDetailAction.ImageUrlLoaded(url))
                }
            }
        }
    }

    override fun createInitialState(): InvoiceDetailState = InvoiceDetailState()

    override fun handleAction(action: InvoiceDetailAction) {
        when (action) {
            is InvoiceDetailAction.DetailsLoaded -> setState { copy(invoice = action.details.first, logs = action.details.second, isLoading = false) }
            is InvoiceDetailAction.CustomerLoaded -> setState { copy(customer = action.customer) }
            is InvoiceDetailAction.ImageUrlLoaded -> setState { copy(imageUrl = action.url) }
            is InvoiceDetailAction.LoadImage -> loadImage(action.storagePath)
            is InvoiceDetailAction.AddPayment -> addPayment(action.amount, action.note)
            is InvoiceDetailAction.AddNote -> addNote(action.note)
            is InvoiceDetailAction.PostponeDueDate -> postponeDueDate(action.newDueDate, action.reason)
            is InvoiceDetailAction.CallCustomer -> callCustomer()
            is InvoiceDetailAction.ShowDialog -> setState { copy(activeDialog = action.dialog) }
        }
    }

    private fun addPayment(amount: Double, note: String?) {
        viewModelScope.launch {
            if (amount <= 0) {
                sendEvent(InvoiceDetailEvent.ShowError("Payment amount must be positive."))
                return@launch
            }
            setState { copy(isProcessing = true) }
            repository.addPayment(invoiceId, amount, note)
                .onSuccess { sendEvent(InvoiceDetailEvent.ShowSuccess("Payment recorded.")) }
                .onFailure { sendEvent(InvoiceDetailEvent.ShowError(it.message ?: "Failed to record payment.")) }
            setState { copy(isProcessing = false, activeDialog = null) }
        }
    }

    private fun addNote(note: String) {
        viewModelScope.launch {
            if (note.isBlank()) {
                sendEvent(InvoiceDetailEvent.ShowError("Note cannot be empty."))
                return@launch
            }
            setState { copy(isProcessing = true) }
            repository.addNote(invoiceId, note)
                .onSuccess { sendEvent(InvoiceDetailEvent.ShowSuccess("Note added.")) }
                .onFailure { sendEvent(InvoiceDetailEvent.ShowError(it.message ?: "Failed to add note.")) }
            setState { copy(isProcessing = false, activeDialog = null) }
        }
    }

    private fun postponeDueDate(newDueDate: String, reason: String?) {
        viewModelScope.launch {
            if (!Utils.isValidDate(newDueDate)) {
                sendEvent(InvoiceDetailEvent.ShowError("Invalid date format. Use YYYY-MM-DD."))
                return@launch
            }
            setState { copy(isProcessing = true) }
            repository.postponeDueDate(invoiceId, newDueDate, reason)
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


// --- Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(
    invoiceId: String,
    onNavigateBack: () -> Unit,
    viewModel: InvoiceDetailViewModel = koinViewModel { parametersOf(invoiceId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is InvoiceDetailEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is InvoiceDetailEvent.ShowSuccess -> snackbarHostState.showSnackbar(event.message)
                is InvoiceDetailEvent.MakePhoneCall -> makePhoneCall(context, event.phoneNumber)
            }
        }
    }

    HandleDialogs(
        state = state,
        onAction = viewModel::sendAction
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invoice Details", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            state.invoice == null -> EmptyState(
                title = "Invoice Not Found",
                subtitle = "The requested invoice could not be loaded.",
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                modifier = Modifier.padding(padding)
            )
            else -> {
                InvoiceDetailContent(
                    state = state,
                    modifier = Modifier.padding(padding),
                    onCallCustomer = { viewModel.sendAction(InvoiceDetailAction.CallCustomer) },
                    onShowDialog = { dialog -> viewModel.sendAction(InvoiceDetailAction.ShowDialog(dialog)) }
                )
            }
        }
    }
}

@Composable
private fun HandleDialogs(
    state: InvoiceDetailState,
    onAction: (InvoiceDetailAction) -> Unit
) {
    when (state.activeDialog) {
        ActiveDialog.AddPayment -> AddPaymentDialog(
            onDismiss = { onAction(InvoiceDetailAction.ShowDialog(null)) },
            onConfirm = { amount, note -> onAction(InvoiceDetailAction.AddPayment(amount, note)) },
            isProcessing = state.isProcessing
        )
        ActiveDialog.AddNote -> AddNoteDialog(
            onDismiss = { onAction(InvoiceDetailAction.ShowDialog(null)) },
            onConfirm = { note -> onAction(InvoiceDetailAction.AddNote(note)) },
            isProcessing = state.isProcessing
        )
        ActiveDialog.Postpone -> PostponeDueDateDialog(
            currentDueDate = state.invoice?.dueDate ?: "",
            onDismiss = { onAction(InvoiceDetailAction.ShowDialog(null)) },
            onConfirm = { newDueDate, reason -> onAction(InvoiceDetailAction.PostponeDueDate(newDueDate, reason)) },
            isProcessing = state.isProcessing
        )
        null -> {}
    }
}

@Composable
private fun InvoiceDetailContent(
    state: InvoiceDetailState,
    modifier: Modifier,
    onCallCustomer: () -> Unit,
    onShowDialog: (ActiveDialog) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            CustomerHeader(
                customerName = state.customer?.name ?: "Unknown Customer",
                customerPhone = state.customer?.phone,
                onCall = onCallCustomer
            )
        }
        item { PaymentSummaryCard(invoice = state.invoice!!) }
        item {
            ActionButtons(
                invoice = state.invoice,
                onAddPayment = { onShowDialog(ActiveDialog.AddPayment) },
                onAddNote = { onShowDialog(ActiveDialog.AddNote) },
                onPostpone = { onShowDialog(ActiveDialog.Postpone) }
            )
        }
        item {
            InvoiceImageCard(imageUrl = state.imageUrl)
        }
        if (state.logs.isNotEmpty()) {
            item {
                Text("Activity Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(state.logs, key = { it.id }) { log ->
                InteractionLogCard(log = log)
            }
        }
    }
}

@Composable
private fun ActionButtons(invoice: Invoice?, onAddPayment: () -> Unit, onAddNote: () -> Unit, onPostpone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(text = "Add Payment", icon = Icons.Default.Payment, onClick = onAddPayment, modifier = Modifier.weight(1f))
            ActionButton(text = "Add Note", icon = Icons.AutoMirrored.Filled.Comment, onClick = onAddNote, modifier = Modifier.weight(1f))
        }
        if (invoice?.status != InvoiceStatus.PAID) {
            ActionButton(text = "Postpone Due Date", icon = Icons.Default.DateRange, onClick = onPostpone, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun InvoiceImageCard(imageUrl: String?) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column {
        Text("Original Scan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Card(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.clickable { isExpanded = !isExpanded }
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Invoice Scan",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = if (isExpanded) 600.dp else 150.dp)
                    .animateContentSize(),
                contentScale = if (isExpanded) ContentScale.Fit else ContentScale.Crop,
                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                error = {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Image not available or failed to load", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            )
        }
    }
}

private fun makePhoneCall(context: Context, phoneNumber: String) {
    val intent = Intent(Intent.ACTION_DIAL, "tel:$phoneNumber".toUri())
    context.startActivity(intent)
}
