package com.retailassistant.features.customers
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retailassistant.core.Utils
import com.retailassistant.core.Utils.formatCurrency
import com.retailassistant.ui.components.common.CenteredTopAppBar
import com.retailassistant.ui.components.common.ConfirmDeleteDialog
import com.retailassistant.ui.components.common.EmptyState
import com.retailassistant.ui.components.common.FullScreenLoading
import com.retailassistant.ui.components.common.PanelCard
import com.retailassistant.ui.components.specific.Avatar
import com.retailassistant.ui.components.specific.InfoChip
import com.retailassistant.ui.components.specific.InvoiceCard
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.format.DateTimeFormatter
@Composable
fun CustomerDetailScreen(
    customerId: String,
    onNavigateBack: () -> Unit,
    onNavigateToInvoice: (String) -> Unit,
    viewModel: CustomerDetailViewModel = koinViewModel { parametersOf(customerId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel.event) {
        viewModel.event.collect { event ->
            when (event) {
                is CustomerDetailEvent.NavigateBack -> onNavigateBack()
                is CustomerDetailEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }
    if (state.activeDialog == CustomerDetailDialog.ConfirmDeleteCustomer) {
        ConfirmDeleteDialog(
            title = "Delete Customer?",
            text = "This will also delete all of their invoices. This action cannot be undone.",
            onDismiss = { viewModel.sendAction(CustomerDetailAction.ShowDialog(null)) },
            onConfirm = { viewModel.sendAction(CustomerDetailAction.ConfirmDeleteCustomer) },
            isProcessing = state.isProcessingAction
        )
    }
    Scaffold(
        topBar = {
            CenteredTopAppBar(
                title = state.customer?.name ?: "Customer Details",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (state.customer != null) {
                        IconButton(onClick = { viewModel.sendAction(CustomerDetailAction.ShowDialog(CustomerDetailDialog.ConfirmDeleteCustomer)) }) {
                            Icon(Icons.Default.DeleteOutline, "Delete Customer", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            state.isLoading -> FullScreenLoading(modifier = Modifier.padding(padding))
            state.customer == null -> EmptyState("Not Found", "This customer may have been deleted.", Icons.Default.PeopleOutline, Modifier.padding(padding))
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { CustomerDetailHeader(state) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                label = "Total Billed",
                                value = formatCurrency(state.totalBilled),
                                icon = Icons.Default.AccountBalanceWallet,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "Outstanding",
                                value = formatCurrency(state.totalOutstanding),
                                icon = Icons.Default.HourglassTop,
                                modifier = Modifier.weight(1f),
                                isError = state.totalOutstanding > 0
                            )
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Invoices",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (state.invoices.isNotEmpty()) {
                                Text(
                                    text = "${state.invoices.size} total",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (state.invoices.isEmpty()) {
                        item {
                            EmptyState("No Invoices", "This customer has no invoices yet.", Icons.AutoMirrored.Filled.ReceiptLong)
                        }
                    } else {
                        items(state.invoices, key = { it.id }) { invoice ->
                            val friendlyDueDate = invoice.dueDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                            InvoiceCard(
                                invoice = invoice,
                                customerName = state.customer!!.name,
                                friendlyDueDate = friendlyDueDate,
                                onClick = { onNavigateToInvoice(invoice.id) },
                                // FIX: Removed redundant .animateItem() modifier. The key handles animations.
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun CustomerDetailHeader(state: CustomerDetailState) {
    PanelCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Avatar(name = state.customer!!.name, size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.customer.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Customer since ${state.invoices.minOfOrNull { it.createdAt }?.let { Utils.formatTimestamp(it).substringBefore(" at") } ?: "N/A"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Contact Information Section
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.customer?.phone?.takeIf { it.isNotBlank() }?.let {
                    InfoChip(icon = Icons.Default.Phone, text = it)
                }
                state.customer?.email?.takeIf { it.isNotBlank() }?.let {
                    InfoChip(icon = Icons.Default.Email, text = it)
                }
            }
        }
    }
}
@Composable
private fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, isError: Boolean = false) {
    PanelCard(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}