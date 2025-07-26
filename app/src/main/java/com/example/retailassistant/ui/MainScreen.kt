package com.example.retailassistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.retailassistant.ui.viewmodel.*
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToAddInvoice: () -> Unit,
    dashboardViewModel: DashboardViewModel = koinViewModel(),
    customerViewModel: CustomerViewModel = koinViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val dashboardState by dashboardViewModel.uiState.collectAsState()
    val customerState by customerViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle dashboard events
    LaunchedEffect(dashboardViewModel) {
        dashboardViewModel.event.collect { event ->
            when (event) {
                is DashboardEvent.NavigateToAddInvoice -> onNavigateToAddInvoice()
                is DashboardEvent.ShowError -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
                else -> {}
            }
        }
    }

    // Handle customer events
    LaunchedEffect(customerViewModel) {
        customerViewModel.event.collect { event ->
            when (event) {
                is CustomerEvent.ShowError -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
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
                            if (selectedTab == 0) {
                                dashboardViewModel.sendAction(DashboardAction.RefreshData)
                            } else {
                                customerViewModel.sendAction(CustomerAction.RefreshData)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Dashboard") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("Customers") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { dashboardViewModel.sendAction(DashboardAction.NavigateToAddInvoice) }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Invoice")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> DashboardContent(
                state = dashboardState,
                modifier = Modifier.padding(paddingValues)
            )
            1 -> CustomerContent(
                state = customerState,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Summary Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoCard(
                title = "Total Unpaid",
                value = NumberFormat.getCurrencyInstance(Locale.US).format(state.totalUnpaid),
                modifier = Modifier.weight(1f)
            )
            InfoCard(
                title = "Overdue",
                value = state.overdueCount.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Invoices Header
        Text(
            text = "Recent Invoices",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.invoices.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No invoices yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap the + button to add your first invoice",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.invoices) { invoice ->
                    // For now, we'll show invoice without customer details
                    // In a full implementation, you'd join with customer data
                    InvoiceCard(
                        invoice = invoice,
                        customer = null, // TODO: Join with customer data
                        onClick = { /* TODO: Navigate to invoice detail */ }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerContent(
    state: CustomerState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "All Customers",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.customers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No customers yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Customers will appear here when you add invoices",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.customers) { customer ->
                    CustomerCard(
                        customer = customer,
                        onClick = { /* TODO: Navigate to customer detail */ }
                    )
                }
            }
        }
    }
}