package com.example.retailassistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.retailassistant.ui.viewmodel.CustomerAction
import com.example.retailassistant.ui.viewmodel.CustomerEvent
import com.example.retailassistant.ui.viewmodel.CustomerState
import com.example.retailassistant.ui.viewmodel.CustomerViewModel
import com.example.retailassistant.ui.viewmodel.DashboardAction
import com.example.retailassistant.ui.viewmodel.DashboardEvent
import com.example.retailassistant.ui.viewmodel.DashboardState
import com.example.retailassistant.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat
import java.util.Locale

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
    val coroutineScope = rememberCoroutineScope()

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
                modifier = Modifier.padding(paddingValues),
                onCustomerClick = { message ->
                    // Show snackbar when customer is clicked
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(message)
                    }
                }
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
        } else if (state.invoicesWithCustomers.isEmpty()) {
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
                items(state.invoicesWithCustomers) { invoiceWithCustomer ->
                    InvoiceCard(
                        invoice = invoiceWithCustomer.invoice,
                        customer = invoiceWithCustomer.customer,
                        onClick = { 
                            // TODO: Navigate to invoice detail
                            // For now, just show a placeholder action
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerContent(
    state: CustomerState,
    modifier: Modifier = Modifier,
    onCustomerClick: (String) -> Unit = {}
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
                        onClick = { 
                            onCustomerClick("Clicked on ${customer.name}")
                        }
                    )
                }
            }
        }
    }
}