package com.example.retailassistant.features.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import com.example.retailassistant.ui.components.EmptyState
import com.example.retailassistant.ui.components.EnhancedStatCard
import com.example.retailassistant.ui.components.InvoiceCard
import com.example.retailassistant.ui.components.ShimmeringInvoiceList
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

// --- MVI Definitions ---
data class InvoiceWithCustomer(
    val invoice: Invoice,
    val customer: Customer?
)

data class DashboardState(
    val invoicesWithCustomers: List<InvoiceWithCustomer> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val totalUnpaid: Double = 0.0,
    val overdueCount: Int = 0
) : UiState

sealed interface DashboardAction : UiAction {
    object RefreshData : DashboardAction
    data class DataLoaded(val invoices: List<Invoice>, val customers: List<Customer>) : DashboardAction
    object SignOut : DashboardAction
}

sealed interface DashboardEvent : UiEvent {
    data class ShowError(val message: String) : DashboardEvent
    object NavigateToAuth : DashboardEvent
}


// --- ViewModel ---
class DashboardViewModel(
    private val repository: RetailRepository
) : MviViewModel<DashboardState, DashboardAction, DashboardEvent>() {

    init {
        // This stream combination is the core of the local-first architecture.
        // It observes local data and automatically updates the state whenever it changes.
        viewModelScope.launch {
            repository.getInvoicesStream()
                .combine(repository.getCustomersStream()) { invoices, customers ->
                    DashboardAction.DataLoaded(invoices, customers)
                }
                .catch { e -> sendEvent(DashboardEvent.ShowError(e.message ?: "Failed to load data")) }
                .collect { sendAction(it) }
        }
        // Initial sync is triggered here to ensure data is fresh on app open.
        // It won't block the UI, which will show cached data immediately.
        refreshData(isInitial = true)
    }

    override fun createInitialState(): DashboardState = DashboardState()

    override fun handleAction(action: DashboardAction) {
        when (action) {
            is DashboardAction.RefreshData -> refreshData()
            is DashboardAction.DataLoaded -> updateStateWithData(action.invoices, action.customers)
            is DashboardAction.SignOut -> signOut()
        }
    }

    private fun updateStateWithData(invoices: List<Invoice>, customers: List<Customer>) {
        val customerMap = customers.associateBy { it.id }
        val invoicesWithCustomers = invoices.map { invoice ->
            InvoiceWithCustomer(invoice, customerMap[invoice.customerId])
        }
        val totalUnpaid = invoices
            .filter { it.status != InvoiceStatus.PAID }
            .sumOf { it.balanceDue }
        val overdueCount = invoices.count { it.isOverdue }

        setState {
            copy(
                invoicesWithCustomers = invoicesWithCustomers,
                totalUnpaid = totalUnpaid,
                overdueCount = overdueCount,
                isLoading = false,
                isRefreshing = false
            )
        }
    }

    private fun refreshData(isInitial: Boolean = false) {
        viewModelScope.launch {
            if (!isInitial) setState { copy(isRefreshing = true) }

            repository.syncAllUserData().onFailure { error ->
                sendEvent(DashboardEvent.ShowError(error.message ?: "Sync failed"))
            }

            // `isRefreshing` will be set to false automatically when the `DataLoaded` action is processed.
            if (!isInitial) setState { copy(isRefreshing = false) }
        }
    }

    private fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            sendEvent(DashboardEvent.NavigateToAuth)
        }
    }
}


// --- Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToInvoiceDetail: (String) -> Unit,
    onLogout: () -> Unit,
    showSyncError: Boolean,
    snackbarHostState: SnackbarHostState,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Show a snackbar if the initial sync failed upon login.
    LaunchedEffect(showSyncError) {
        if (showSyncError) {
            snackbarHostState.showSnackbar(
                "Could not sync data. Showing cached information.",
                duration = SnackbarDuration.Long
            )
        }
    }

    // Handle ViewModel events.
    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is DashboardEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is DashboardEvent.NavigateToAuth -> onLogout()
            }
        }
    }

    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onDismiss = { showLogoutDialog = false },
            onConfirm = {
                showLogoutDialog = false
                viewModel.sendAction(DashboardAction.SignOut)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.sendAction(DashboardAction.RefreshData) },
            modifier = Modifier.padding(padding)
        ) {
            if (state.isLoading) {
                ShimmeringInvoiceList()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            "Overview",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            EnhancedStatCard(
                                label = "Total Unpaid",
                                value = formatCurrency(state.totalUnpaid),
                                icon = Icons.Default.AccountBalanceWallet,
                                modifier = Modifier.weight(1f),
                                gradient = com.example.retailassistant.ui.theme.AppGradients.Primary
                            )
                            EnhancedStatCard(
                                label = "Overdue",
                                value = state.overdueCount.toString(),
                                icon = Icons.Default.Warning,
                                modifier = Modifier.weight(1f),
                                gradient = if (state.overdueCount > 0)
                                    com.example.retailassistant.ui.theme.AppGradients.Error
                                else
                                    com.example.retailassistant.ui.theme.AppGradients.Secondary,
                                isAlert = state.overdueCount > 0
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("Recent Invoices", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    if (state.invoicesWithCustomers.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No invoices yet",
                                subtitle = "Tap the '+' button below to add your first one and get started.",
                                icon = Icons.AutoMirrored.Filled.ReceiptLong
                            )
                        }
                    } else {
                        items(state.invoicesWithCustomers, key = { it.invoice.id }) { item ->
                            InvoiceCard(
                                invoice = item.invoice,
                                customer = item.customer,
                                onClick = { onNavigateToInvoiceDetail(item.invoice.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogoutConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Logout") },
        text = { Text("Are you sure you want to log out? Local data will be cleared for security.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Logout") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
