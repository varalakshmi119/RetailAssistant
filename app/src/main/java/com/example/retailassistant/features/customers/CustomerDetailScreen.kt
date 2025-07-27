package com.example.retailassistant.features.customers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.retailassistant.core.MviViewModel
import com.example.retailassistant.core.UiAction
import com.example.retailassistant.core.UiEvent
import com.example.retailassistant.core.UiState
import com.example.retailassistant.core.Utils.formatCurrency
import com.example.retailassistant.data.db.Customer
import com.example.retailassistant.data.db.Invoice
import com.example.retailassistant.data.db.InvoiceStatus
import com.example.retailassistant.data.repository.RetailRepository
import com.example.retailassistant.ui.components.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

// --- MVI Definitions ---
data class CustomerDetailState(
    val customer: Customer? = null,
    val invoices: List<Invoice> = emptyList(),
    val isLoading: Boolean = true,
    val totalBilled: Double = 0.0,
    val totalOutstanding: Double = 0.0,
) : UiState

sealed interface CustomerDetailAction : UiAction {
    data class DataLoaded(val customer: Customer?, val invoices: List<Invoice>) : CustomerDetailAction
}

interface CustomerDetailEvent : UiEvent // No events needed for this screen

// --- ViewModel ---
class CustomerDetailViewModel(
    customerId: String,
    repository: RetailRepository
) : MviViewModel<CustomerDetailState, CustomerDetailAction, CustomerDetailEvent>() {

    init {
        repository.getCustomerById(customerId)
            .combine(repository.getCustomerInvoicesStream(customerId)) { customer, invoices ->
                CustomerDetailAction.DataLoaded(customer, invoices)
            }
            .onEach(::sendAction)
            .launchIn(viewModelScope)
    }

    override fun createInitialState(): CustomerDetailState = CustomerDetailState()

    override fun handleAction(action: CustomerDetailAction) {
        when(action) {
            is CustomerDetailAction.DataLoaded -> {
                val (customer, customerInvoices) = action
                val totalBilled = customerInvoices.sumOf { it.totalAmount }
                val totalOutstanding = customerInvoices
                    .filter { it.status != InvoiceStatus.PAID }
                    .sumOf { it.balanceDue }
                setState {
                    copy(
                        customer = customer,
                        invoices = customerInvoices.sortedByDescending { it.createdAt },
                        totalBilled = totalBilled,
                        totalOutstanding = totalOutstanding,
                        isLoading = false
                    )
                }
            }
        }
    }
}

// --- Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerId: String,
    onNavigateBack: () -> Unit,
    onNavigateToInvoiceDetail: (String) -> Unit,
    viewModel: CustomerDetailViewModel = koinViewModel { parametersOf(customerId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.customer?.name ?: "Customer Details", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            // In a real app, a dedicated shimmer for this screen would be even better.
            ShimmeringCustomerList(modifier = Modifier.padding(padding))
        } else if (state.customer == null) {
            EmptyState(
                title = "Customer Not Found",
                subtitle = "The requested customer could not be found.",
                icon = Icons.Default.People,
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { CustomerDetailHeader(state.customer!!) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        EnhancedStatCard(
                            label = "Total Billed",
                            value = formatCurrency(state.totalBilled),
                            icon = Icons.Default.AccountBalanceWallet,
                            modifier = Modifier.weight(1f)
                        )
                        EnhancedStatCard(
                            label = "Outstanding",
                            value = formatCurrency(state.totalOutstanding),
                            icon = Icons.Default.Warning,
                            modifier = Modifier.weight(1f),
                            gradient = if (state.totalOutstanding > 0) com.example.retailassistant.ui.theme.AppGradients.Error else com.example.retailassistant.ui.theme.AppGradients.Secondary
                        )
                    }
                }
                item {
                    Text(
                        "Invoices (${state.invoices.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                if (state.invoices.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No Invoices",
                            subtitle = "This customer does not have any invoices yet.",
                            icon = Icons.AutoMirrored.Filled.ReceiptLong
                        )
                    }
                } else {
                    items(state.invoices, key = { it.id }) { invoice ->
                        InvoiceCard(
                            invoice = invoice,
                            customer = state.customer, // pass the non-null customer
                            onClick = { onNavigateToInvoiceDetail(invoice.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerDetailHeader(customer: Customer) {
    FloatingCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EnhancedAvatar(name = customer.name, size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            customer.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                InfoRow(icon = Icons.Default.Phone, text = phone)
            }
            customer.email?.takeIf { it.isNotBlank() }?.let { email ->
                InfoRow(icon = Icons.Default.Email, text = email)
            }
        }
    }
}
