package com.example.retailassistant.ui.screen.invoice
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.example.retailassistant.data.Invoice
import com.example.retailassistant.data.InvoiceStatus
import com.example.retailassistant.ui.components.EmptyState
import com.example.retailassistant.ui.components.InteractionLogCard
import com.example.retailassistant.ui.components.LabeledTextField
import com.example.retailassistant.ui.components.StatusChip
import com.example.retailassistant.ui.components.formatCurrency
import com.example.retailassistant.ui.theme.Shapes
import com.example.retailassistant.ui.viewmodel.InvoiceDetailAction
import com.example.retailassistant.ui.viewmodel.InvoiceDetailEvent
import com.example.retailassistant.ui.viewmodel.InvoiceDetailState
import com.example.retailassistant.ui.viewmodel.InvoiceDetailViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(
    invoiceId: String,
    onNavigateBack: () -> Unit,
    viewModel: InvoiceDetailViewModel = koinViewModel { parametersOf(invoiceId) }
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showAddPaymentDialog by remember { mutableStateOf(false) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is InvoiceDetailEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is InvoiceDetailEvent.ShowSuccess -> snackbarHostState.showSnackbar(event.message)
                is InvoiceDetailEvent.MakePhoneCall -> {
                    val intent = Intent(Intent.ACTION_DIAL, "tel:${event.phoneNumber}".toUri())
                    context.startActivity(intent)
                }
            }
        }
    }
    if (showAddPaymentDialog) {
        AddPaymentDialog(
            onDismiss = { showAddPaymentDialog = false },
            onConfirm = { amount, note ->
                viewModel.sendAction(InvoiceDetailAction.AddPayment(amount, note))
                showAddPaymentDialog = false
            },
            isProcessing = state.isProcessing
        )
    }
    if (showAddNoteDialog) {
        AddNoteDialog(
            onDismiss = { showAddNoteDialog = false },
            onConfirm = { note ->
                viewModel.sendAction(InvoiceDetailAction.AddNote(note))
                showAddNoteDialog = false
            },
            isProcessing = state.isProcessing
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invoice Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.invoice == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Invoice not found.")
                }
            }
            else -> {
                InvoiceDetailContent(
                    state = state,
                    modifier = Modifier.padding(padding),
                    onCallCustomer = { viewModel.onCallCustomer() },
                    onAddPayment = { showAddPaymentDialog = true },
                    onAddNote = { showAddNoteDialog = true }
                )
            }
        }
    }
}
@Composable
fun InvoiceDetailContent(
    state: InvoiceDetailState,
    modifier: Modifier,
    onCallCustomer: () -> Unit,
    onAddPayment: () -> Unit,
    onAddNote: () -> Unit
) {
    val invoice = state.invoice!!
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            CustomerHeader(
                customerName = state.customer?.name ?: "Unknown",
                customerPhone = state.customer?.phone,
                onCall = onCallCustomer
            )
        }
        item {
            PaymentSummaryCard(invoice = invoice)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    text = "Add Payment",
                    icon = Icons.Default.Payment,
                    onClick = onAddPayment,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    text = "Add Note",
                    icon = Icons.AutoMirrored.Filled.Comment,
                    onClick = onAddNote,
                    modifier = Modifier.weight(1f)
                )
            }
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }
        } else {
            item {
                EmptyState(
                    title = "No Activity",
                    subtitle = "Payments and notes will appear here.",
                    icon = Icons.Default.History
                )
            }
        }
    }
}
@Composable
fun CustomerHeader(customerName: String, customerPhone: String?, onCall: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(customerName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (customerPhone?.isNotBlank() == true) {
                Text(customerPhone, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (customerPhone?.isNotBlank() == true) {
            IconButton(
                onClick = onCall,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(Icons.Default.Call, contentDescription = "Call Customer", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}
@Composable
fun PaymentSummaryCard(invoice: Invoice) {
    Card(shape = Shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total Amount", style = MaterialTheme.typography.bodyLarge)
                Text(formatCurrency(invoice.totalAmount), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            val progress = if (invoice.totalAmount > 0) {
                (invoice.amountPaid / invoice.totalAmount).toFloat().coerceIn(0f, 1f)
            } else {
                1f
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape),
                color = if (invoice.status == InvoiceStatus.PAID) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${formatCurrency(invoice.amountPaid)} Paid",
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${formatCurrency(invoice.totalAmount - invoice.amountPaid)} Due",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatusChip(status = invoice.status, isOverdue = invoice.isOverdue)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Due Date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(invoice.dueDate, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
@Composable
fun InvoiceImageCard(imageUrl: String?) {
    var isExpanded by remember { mutableStateOf(false) }
    Column {
        Text("Original Scan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Card(
            shape = Shapes.large,
            modifier = Modifier.clickable { isExpanded = !isExpanded }
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Invoice Scan",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 500.dp)
                    .animateContentSize(),
                contentScale = if(isExpanded) ContentScale.FillWidth else ContentScale.Crop
            )
        }
    }
}
@Composable
fun ActionButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = Shapes.medium
    ) {
        Icon(icon, contentDescription = null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}
@Composable
fun AddPaymentDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, String?) -> Unit,
    isProcessing: Boolean
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record a Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LabeledTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "Amount Paid",
                    leadingIcon = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                LabeledTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "Notes (optional)",
                    singleLine = false
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val paymentAmount = amount.toDoubleOrNull()
                    if (paymentAmount != null && paymentAmount > 0) {
                        onConfirm(paymentAmount, note.takeIf { it.isNotBlank() })
                    }
                },
                enabled = !isProcessing && (amount.toDoubleOrNull() ?: 0.0) > 0.0
            ) {
                if (isProcessing) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isProcessing) {
                Text("Cancel")
            }
        }
    )
}
@Composable
fun AddNoteDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isProcessing: Boolean
) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a Note") },
        text = {
            LabeledTextField(
                value = note,
                onValueChange = { note = it },
                label = "Note",
                placeholder = "e.g., Customer will pay next week.",
                singleLine = false
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(note) },
                enabled = !isProcessing && note.isNotBlank()
            ) {
                if (isProcessing) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isProcessing) {
                Text("Cancel")
            }
        }
    )
}