package com.retailassistant.features.invoices.detail

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.retailassistant.core.Utils
import com.retailassistant.data.db.Invoice
import com.retailassistant.data.db.InvoiceStatus
import com.retailassistant.ui.components.common.ActionButton
import com.retailassistant.ui.components.common.AddNoteDialog
import com.retailassistant.ui.components.common.AddPaymentDialog
import com.retailassistant.ui.components.common.CenteredTopAppBar
import com.retailassistant.ui.components.common.ConfirmDeleteDialog
import com.retailassistant.ui.components.common.EmptyState
import com.retailassistant.ui.components.common.FullScreenLoading
import com.retailassistant.ui.components.common.PostponeDueDateDialog
import com.retailassistant.ui.components.specific.Avatar
import com.retailassistant.ui.components.specific.InteractionLogItem
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun InvoiceDetailScreen(
    invoiceId: String,
    onNavigateBack: () -> Unit,
    onNavigateToCustomer: (String) -> Unit,
    viewModel: InvoiceDetailViewModel = koinViewModel { parametersOf(invoiceId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel.event) {
        viewModel.event.collect { event ->
            when (event) {
                is InvoiceDetailEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is InvoiceDetailEvent.MakePhoneCall -> makePhoneCall(context, event.phoneNumber)
                is InvoiceDetailEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    HandleDialogs(state = state, onAction = viewModel::sendAction)

    Scaffold(
        topBar = {
            CenteredTopAppBar(
                title = "Invoice Details",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (state.invoice != null) {
                        IconButton(onClick = { viewModel.sendAction(InvoiceDetailAction.ShowDialog(ActiveDialog.ConfirmDeleteInvoice)) }) {
                            Icon(Icons.Default.DeleteOutline, "Delete Invoice", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> FullScreenLoading(modifier = Modifier.padding(padding))
            state.invoice == null -> EmptyState("Not Found", "This invoice may have been deleted.", Icons.Default.Error, Modifier.padding(padding))
            else -> {
                val invoice = state.invoice
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        CustomerHeader(
                            customerName = state.customer?.name ?: "Unknown Customer",
                            customerPhone = state.customer?.phone,
                            onCall = { viewModel.sendAction(InvoiceDetailAction.CallCustomer) },
                            onNavigate = { state.customer?.id?.let(onNavigateToCustomer) }
                        )
                    }
                    item { PaymentSummaryCard(invoice!!) }
                    item {
                        ActionButtons(
                            invoiceStatus = invoice!!.status,
                            onAddPayment = { viewModel.sendAction(InvoiceDetailAction.ShowDialog(ActiveDialog.AddPayment)) },
                            onAddNote = { viewModel.sendAction(InvoiceDetailAction.ShowDialog(ActiveDialog.AddNote)) },
                            onPostpone = { viewModel.sendAction(InvoiceDetailAction.ShowDialog(ActiveDialog.Postpone)) }
                        )
                    }
                    item { InvoiceImageCard(imageUrl = state.imageUrl) }

                    if (state.logs.isNotEmpty()) {
                        item {
                            Text("Activity Log", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        items(state.logs, key = { it.id }) { log ->
                            InteractionLogItem(log = log, modifier = Modifier.animateItem())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HandleDialogs(state: InvoiceDetailState, onAction: (InvoiceDetailAction) -> Unit) {
    when (val dialog = state.activeDialog) {
        ActiveDialog.AddPayment -> AddPaymentDialog(
            onDismiss = { onAction(InvoiceDetailAction.ShowDialog(null)) },
            onConfirm = { amount, note -> onAction(InvoiceDetailAction.AddPayment(amount, note)) },
            isProcessing = state.isProcessingAction
        )
        ActiveDialog.AddNote -> AddNoteDialog(
            onDismiss = { onAction(InvoiceDetailAction.ShowDialog(null)) },
            onConfirm = { note -> onAction(InvoiceDetailAction.AddNote(note)) },
            isProcessing = state.isProcessingAction
        )
        ActiveDialog.Postpone -> PostponeDueDateDialog(
            currentDueDate = state.invoice?.dueDate ?: LocalDate.now(),
            onDismiss = { onAction(InvoiceDetailAction.ShowDialog(null)) },
            onConfirm = { newDueDate, reason -> onAction(InvoiceDetailAction.PostponeDueDate(newDueDate, reason)) },
            isProcessing = state.isProcessingAction
        )
        ActiveDialog.ConfirmDeleteInvoice -> ConfirmDeleteDialog(
            title = "Delete Invoice?",
            text = "This action is permanent and cannot be undone.",
            onDismiss = { onAction(InvoiceDetailAction.ShowDialog(null)) },
            onConfirm = { onAction(InvoiceDetailAction.DeleteInvoice) },
            isProcessing = state.isProcessingAction
        )
        null -> {}
    }
}

@Composable
private fun CustomerHeader(customerName: String, customerPhone: String?, onCall: () -> Unit, onNavigate: () -> Unit) {
    ElevatedCard(onClick = onNavigate) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Avatar(name = customerName)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(customerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    customerPhone?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!customerPhone.isNullOrBlank()) {
                IconButton(onClick = onCall, colors = IconButtonDefaults.filledTonalIconButtonColors()) {
                    Icon(Icons.Default.Call, "Call $customerName")
                }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, "View customer", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PaymentSummaryCard(invoice: Invoice) {
    val progress by animateFloatAsState(
        targetValue = if (invoice.totalAmount > 0) (invoice.amountPaid / invoice.totalAmount).toFloat() else 0f,
        label = "paymentProgress"
    )

    ElevatedCard {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Total Amount", style = MaterialTheme.typography.bodyLarge)
            Text(Utils.formatCurrency(invoice.totalAmount), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.small),
            color = if (invoice.status == InvoiceStatus.PAID) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("${Utils.formatCurrency(invoice.amountPaid)} Paid", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
            Text("${Utils.formatCurrency(invoice.balanceDue)} Due", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("Due Date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(invoice.dueDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ActionButtons(invoiceStatus: InvoiceStatus, onAddPayment: () -> Unit, onAddNote: () -> Unit, onPostpone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (invoiceStatus != InvoiceStatus.PAID) {
            ActionButton(text = "Add Payment", icon = Icons.Default.Payment, onClick = onAddPayment, color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(text = "Add Note", icon = Icons.AutoMirrored.Filled.Comment, onClick = onAddNote, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
            if (invoiceStatus != InvoiceStatus.PAID) {
                ActionButton(text = "Postpone", icon = Icons.Default.DateRange, onClick = onPostpone, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun InvoiceImageCard(imageUrl: String?) {
    var isExpanded by remember { mutableStateOf(false) }
    ElevatedCard(modifier = Modifier.clickable { isExpanded = !isExpanded }) {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = "Invoice Scan",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = if (isExpanded) 600.dp else 200.dp)
                .animateContentSize(),
            contentScale = if (isExpanded) ContentScale.Fit else ContentScale.Crop,
            loading = { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() } },
            error = {
                Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BrokenImage, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Text("Image failed to load", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        )
    }
}

private fun makePhoneCall(context: Context, phoneNumber: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$phoneNumber".toUri()))
    } catch (_: Exception) { /* Fails silently */ }
}
