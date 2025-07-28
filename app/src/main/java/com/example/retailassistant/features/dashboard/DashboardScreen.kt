package com.example.retailassistant.features.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.retailassistant.core.Utils.formatCurrency
import com.example.retailassistant.ui.components.*
import com.example.retailassistant.ui.theme.AppGradients
import org.koin.androidx.compose.koinViewModel
import java.time.format.DateTimeFormatter

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

    LaunchedEffect(showSyncError) {
        if (showSyncError) {
            snackbarHostState.showSnackbar(
                "Could not sync data. Showing cached information.",
                duration = SnackbarDuration.Long
            )
        }
    }

    LaunchedEffect(viewModel.event) {
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
                title = { Text("Dashboard") },
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
                        DashboardHeader(userName = state.userName)
                    }
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
                                gradient = AppGradients.Primary
                            )
                            EnhancedStatCard(
                                label = "Overdue",
                                value = state.overdueCount.toString(),
                                icon = Icons.Default.Warning,
                                modifier = Modifier.weight(1f),
                                gradient = if (state.overdueCount > 0) AppGradients.Error else AppGradients.Success,
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
                            val friendlyDueDate = item.invoice.dueDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                            InvoiceCard(
                                invoice = item.invoice,
                                customerName = item.customer?.name ?: "Unknown Customer",
                                friendlyDueDate = friendlyDueDate,
                                onClick = { onNavigateToInvoiceDetail(item.invoice.id) },
                                modifier = Modifier.animateItem(fadeInSpec = tween(300), fadeOutSpec = tween(300))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(userName: String?) {
    Column {
        Text(
            text = "Welcome back,",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AnimatedContent(
            targetState = userName,
            transitionSpec = {
                (slideInVertically { height -> height } + fadeIn()).togetherWith(slideOutVertically { height -> -height } + fadeOut())
            }, label = "userName"
        ) { name ->
            Text(
                text = name ?: "User",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
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
