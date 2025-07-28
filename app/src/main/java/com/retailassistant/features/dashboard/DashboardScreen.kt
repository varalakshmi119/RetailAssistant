package com.retailassistant.features.dashboard
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retailassistant.core.Utils
import com.retailassistant.ui.components.common.AnimatedCounter
import com.retailassistant.ui.components.common.CenteredTopAppBar
import com.retailassistant.ui.components.common.EmptyState
import com.retailassistant.ui.components.common.GradientBox
import com.retailassistant.ui.components.common.ShimmeringList
import com.retailassistant.ui.components.specific.InvoiceCard
import com.retailassistant.ui.theme.AppGradients
import com.retailassistant.ui.theme.GradientColors
import org.koin.androidx.compose.koinViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToInvoice: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.event) {
        viewModel.event.collect { event ->
            when (event) {
                is DashboardEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Confirm Logout") },
            text = { Text("Are you sure you want to log out?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.sendAction(DashboardAction.SignOut)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Logout") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            CenteredTopAppBar(
                title = "Dashboard",
                actions = {
                    IconButton(
                        onClick = { viewModel.sendAction(DashboardAction.RefreshData) },
                        enabled = !state.isRefreshing
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = if (state.isRefreshing) {
                                Modifier.size(20.dp)
                            } else Modifier
                        )
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Logout")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.sendAction(DashboardAction.RefreshData) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading && state.invoicesWithCustomers.isEmpty()) {
                ShimmeringList()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { DashboardHeader(userName = state.userName) }
                    item {
                        DashboardStats(
                            totalUnpaid = state.totalUnpaid,
                            overdueCount = state.overdueCount
                        )
                    }
                    item {
                        Text(
                            "Recent Invoices",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (state.invoicesWithCustomers.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No invoices yet",
                                subtitle = "Tap the '+' button below to add your first one.",
                                icon = Icons.AutoMirrored.Filled.ReceiptLong
                            )
                        }
                    } else {
                        items(state.invoicesWithCustomers, key = { it.invoice.id }) { item ->
                            val friendlyDueDate = item.invoice.dueDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                            InvoiceCard(
                                invoice = item.invoice,
                                customerName = item.customer?.name ?: "Unknown",
                                friendlyDueDate = friendlyDueDate,
                                onClick = { onNavigateToInvoice(item.invoice.id) },
                                modifier = Modifier.animateItem()
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
                (slideInVertically { h -> h } + fadeIn()).togetherWith(slideOutVertically { h -> -h } + fadeOut())
            },
            label = "userName"
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
private fun DashboardStats(
    totalUnpaid: Double,
    overdueCount: Int
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatCard(
            label = "Total Unpaid",
            value = totalUnpaid,
            icon = Icons.Default.AccountBalanceWallet,
            gradient = AppGradients.Primary,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "Overdue",
            value = overdueCount.toDouble(),
            formatter = { it.toInt().toString() },
            icon = Icons.Default.Warning,
            gradient = if (overdueCount > 0) AppGradients.Error else AppGradients.Success,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: Double,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    gradient: GradientColors,
    formatter: (Double) -> String = { Utils.formatCurrency(it) }
) {
    val brush = Brush.linearGradient(colors = listOf(gradient.start, gradient.end))
    GradientBox(modifier = modifier, gradient = brush) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp),
                tint = Color.White
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
            AnimatedCounter(
                targetValue = value,
                formatter = formatter,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
        }
    }
}