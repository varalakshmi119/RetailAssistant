package com.example.retailassistant.ui.screen.dashboard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.retailassistant.ui.components.CustomerCard
import com.example.retailassistant.ui.components.EmptyState
import com.example.retailassistant.ui.components.InfoCard
import com.example.retailassistant.ui.components.InvoiceCard
import com.example.retailassistant.ui.components.formatCurrency
import com.example.retailassistant.ui.viewmodel.CustomerAction
import com.example.retailassistant.ui.viewmodel.CustomerDetailViewModel
import com.example.retailassistant.ui.viewmodel.CustomerEvent
import com.example.retailassistant.ui.viewmodel.CustomerState
import com.example.retailassistant.ui.viewmodel.CustomerViewModel
import com.example.retailassistant.ui.viewmodel.DashboardAction
import com.example.retailassistant.ui.viewmodel.DashboardEvent
import com.example.retailassistant.ui.viewmodel.DashboardState
import com.example.retailassistant.ui.viewmodel.DashboardViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToAddInvoice: () -> Unit,
    onNavigateToInvoiceDetail: (String) -> Unit,
    onNavigateToCustomerDetail: (String) -> Unit,
    onLogout: () -> Unit,
    dashboardViewModel: DashboardViewModel = koinViewModel(),
    customerViewModel: CustomerViewModel = koinViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val dashboardState by dashboardViewModel.combinedData.collectAsState()
    val customerState by customerViewModel.customerUiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(dashboardViewModel) {
        dashboardViewModel.event.collect { event ->
            when (event) {
                is DashboardEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Short)
                }
                is DashboardEvent.NavigateToInvoiceDetail -> onNavigateToInvoiceDetail(event.invoiceId)
                is DashboardEvent.Logout -> onLogout()
            }
        }
    }
    LaunchedEffect(customerViewModel) {
        customerViewModel.event.collect { event ->
            when (event) {
                is CustomerEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Short)
                }
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedTab == 0) "Dashboard" else "Customers",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (selectedTab == 0) dashboardViewModel.sendAction(DashboardAction.RefreshData)
                            else customerViewModel.sendAction(CustomerAction.RefreshData)
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { dashboardViewModel.sendAction(DashboardAction.Logout) }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                    label = { Text("Dashboard") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Group, contentDescription = null) },
                    label = { Text("Customers") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    text = { Text("New Invoice") },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = onNavigateToAddInvoice,
                    expanded = true
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> DashboardContent(
                    state = dashboardState,
                    onRefresh = { dashboardViewModel.sendAction(DashboardAction.RefreshData) },
                    onInvoiceClick = { dashboardViewModel.onInvoiceClicked(it) }
                )
                1 -> CustomerContent(
                    state = customerState,
                    onRefresh = { customerViewModel.sendAction(CustomerAction.RefreshData) },
                    onCustomerClick = onNavigateToCustomerDetail
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    state: DashboardState,
    onRefresh: () -> Unit,
    onInvoiceClick: (String) -> Unit
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Overview", style = MaterialTheme.typography.titleLarge)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val formattedTotalUnpaid = remember(state.totalUnpaid) {
                        formatCurrency(state.totalUnpaid)
                    }
                    InfoCard(
                        title = "Total Unpaid",
                        value = formattedTotalUnpaid,
                        icon = Icons.Default.AccountBalanceWallet,
                        modifier = Modifier.fillMaxWidth()
                    )
                    InfoCard(
                        title = "Overdue Invoices",
                        value = state.overdueCount.toString(),
                        icon = Icons.Default.Warning,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = if (state.overdueCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (state.overdueCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("Recent Invoices", style = MaterialTheme.typography.titleLarge)
            }
            if (state.isLoading) {
                items(5) {
                    // Shimmer loading placeholder
                }
            } else if (state.invoicesWithCustomers.isEmpty()) {
                item {
                    EmptyState(
                        title = "No invoices yet",
                        subtitle = "Tap the 'New Invoice' button to add your first one and get started.",
                        icon = Icons.AutoMirrored.Filled.ReceiptLong
                    )
                }
            } else {
                items(state.invoicesWithCustomers, key = { it.invoice.id }) { item ->
                    InvoiceCard(
                        invoice = item.invoice,
                        customer = item.customer,
                        onClick = { onInvoiceClick(item.invoice.id) }
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerContent(
    state: CustomerState,
    onRefresh: () -> Unit,
    onCustomerClick: (String) -> Unit
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isLoading) {
                items(5) {
                    // Shimmer loading placeholder
                }
            } else if (state.customers.isEmpty()) {
                item {
                    EmptyState(
                        title = "No customers found",
                        subtitle = "Your customers will appear here once you create invoices for them.",
                        icon = Icons.Default.People
                    )
                }
            } else {
                items(state.customers, key = { it.id }) { customer ->
                    CustomerCard(
                        customer = customer,
                        onClick = { onCustomerClick(customer.id) }
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerId: String,
    onNavigateBack: () -> Unit,
    onNavigateToInvoiceDetail: (String) -> Unit,
    viewModel: CustomerDetailViewModel = koinViewModel { parametersOf(customerId) }
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.customer?.name ?: "Customer Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text("Invoices for ${state.customer?.name}", style = MaterialTheme.typography.titleLarge)
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
                            customer = state.customer,
                            onClick = { onNavigateToInvoiceDetail(invoice.id) }
                        )
                    }
                }
            }
        }
    }
}
