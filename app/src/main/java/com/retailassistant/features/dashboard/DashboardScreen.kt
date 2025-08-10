package com.retailassistant.features.dashboard
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retailassistant.core.ConnectivityObserver
import com.retailassistant.core.Utils
import com.retailassistant.ui.components.common.AnimatedCounter
import com.retailassistant.ui.components.common.CenteredTopAppBar
import com.retailassistant.ui.components.common.EmptyState
import com.retailassistant.ui.components.common.GradientBox
import com.retailassistant.ui.components.common.PanelCard
import com.retailassistant.ui.components.common.shimmerBackground
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
    val context = LocalContext.current
    // Observe network connectivity
    val connectivityObserver = remember { ConnectivityObserver(context) }
    val isOnline by connectivityObserver.observe().collectAsStateWithLifecycle(initialValue = true)
    var showPermissionCard by rememberSaveable { mutableStateOf(true) }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasNotificationPermission = isGranted
            showPermissionCard = !isGranted // Keep showing if permission denied
        }
    )
    LaunchedEffect(key1 = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
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
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Logout")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.sendAction(DashboardAction.RefreshData) },
            modifier = Modifier
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { DashboardHeader(userName = state.userName) }
                if (!isOnline) {
                    item { OfflineIndicatorCard() }
                }
                if (showPermissionCard && !hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    item {
                        PermissionRequestCard(
                            onAllowClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            onDismissClick = { showPermissionCard = false }
                        )
                    }
                }
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
                if (state.isLoading && state.invoicesWithCustomers.isEmpty()) {
                    items(count = 3) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .shimmerBackground(MaterialTheme.shapes.large)
                        )
                    }
                } else if (state.invoicesWithCustomers.isEmpty()) {
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
                            customerName = item.customer?.name ?: "Unknown Customer",
                            friendlyDueDate = friendlyDueDate,
                            onClick = { onNavigateToInvoice(item.invoice.id) },
                            // FIX: Removed redundant .animateItem() modifier. The key handles animations.
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun PermissionRequestCard(
    onAllowClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    PanelCard(
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "Stay Updated",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Allow notifications for overdue invoice reminders.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAllowClick) {
                    Text("Allow", color = MaterialTheme.colorScheme.secondary)
                }
                IconButton(onClick = onDismissClick) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss notification permission request",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
@Composable
private fun DashboardHeader(userName: String?) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
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
            label = "userNameAnimation"
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
            icon = Icons.Default.WarningAmber,
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
    GradientBox(modifier = modifier, gradient = brush, shape = MaterialTheme.shapes.large) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
            )
            AnimatedCounter(
                targetValue = value,
                formatter = formatter,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
@Composable
private fun OfflineIndicatorCard() {
    PanelCard(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Column {
                Text(
                    text = "You're offline",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Data will sync when connection is restored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}