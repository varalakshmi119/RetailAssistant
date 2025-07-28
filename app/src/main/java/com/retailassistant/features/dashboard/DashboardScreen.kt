package com.retailassistant.features.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retailassistant.core.Utils
import com.retailassistant.ui.components.common.*
import com.retailassistant.ui.components.specific.InvoiceCard
import com.retailassistant.ui.theme.AppGradients
import com.retailassistant.ui.theme.GradientColors
import org.koin.androidx.compose.koinViewModel
import java.time.format.DateTimeFormatter

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
                    if (state.isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        IconButton(onClick = { viewModel.sendAction(DashboardAction.RefreshData) }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Logout")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            ShimmeringList()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                    item { DashboardHeader(userName = state.userName) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            StatCard(
                                label = "Total Unpaid",
                                value = state.totalUnpaid,
                                icon = Icons.Default.AccountBalanceWallet,
                                gradient = AppGradients.Primary,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "Overdue",
                                value = state.overdueCount.toDouble(),
                                formatter = { it.toInt().toString() },
                                icon = Icons.Default.Warning,
                                gradient = if (state.overdueCount > 0) AppGradients.Error else AppGradients.Success,
                                modifier = Modifier.weight(1f)
                            )
                        }
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
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            AnimatedCounter(
                targetValue = value,
                formatter = formatter,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
        }
    }
}
