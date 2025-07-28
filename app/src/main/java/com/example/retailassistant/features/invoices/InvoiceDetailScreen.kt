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
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.retailassistant.data.db.Invoice
import com.example.retailassistant.data.db.InvoiceStatus
import com.example.retailassistant.ui.components.*
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.LocalDate

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

    LaunchedEffect(viewModel.event) {
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
                    onAction = viewModel::sendAction
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
    when (val dialog = state.activeDialog) {
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
            currentDueDate = state.invoice?.dueDate ?: LocalDate.now(),
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
    onAction: (InvoiceDetailAction) -> Unit,
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
                onCall = { onAction(InvoiceDetailAction.CallCustomer) }
            )
        }
        item { PaymentSummaryCard(invoice = state.invoice!!) }
        item {
            ActionButtons(
                invoice = state.invoice,
                onAddPayment = { onAction(InvoiceDetailAction.ShowDialog(ActiveDialog.AddPayment)) },
                onAddNote = { onAction(InvoiceDetailAction.ShowDialog(ActiveDialog.AddNote)) },
                onPostpone = { onAction(InvoiceDetailAction.ShowDialog(ActiveDialog.Postpone)) }
            )
        }
        item {
            InvoiceImageCard(
                imageUrl = state.imageUrl,
                storagePath = state.invoice?.originalScanUrl
            )
        }
        if (state.logs.isNotEmpty()) {
            item {
                Text("Activity Log", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
private fun InvoiceImageCard(imageUrl: String?, storagePath: String?) {
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
                    .memoryCacheKey(storagePath) // Use stable path as cache key
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            Text("Image failed to load", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            )
        }
    }
}

private fun makePhoneCall(context: Context, phoneNumber: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL, "tel:$phoneNumber".toUri())
        context.startActivity(intent)
    } catch (e: Exception) {
        // Can fail if no phone app is available
    }
}
