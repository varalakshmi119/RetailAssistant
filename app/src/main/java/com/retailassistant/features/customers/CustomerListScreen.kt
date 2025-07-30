package com.retailassistant.features.customers
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retailassistant.ui.components.common.CenteredTopAppBar
import com.retailassistant.ui.components.common.EmptyState
import com.retailassistant.ui.components.common.SearchBar
import com.retailassistant.ui.components.common.ShimmeringList
import com.retailassistant.ui.components.specific.CustomerCard
import org.koin.androidx.compose.koinViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    onNavigateToCustomer: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: CustomerListViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel.event) {
        viewModel.event.collect { event ->
            when (event) {
                is CustomerListEvent.OpenDialer -> openDialer(context, event.phone)
                is CustomerListEvent.OpenEmail -> openEmail(context, event.email, snackbarHostState)
                is CustomerListEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }
    Scaffold(
        topBar = {
            CenteredTopAppBar(
                title = "Customers",
                actions = {
                    IconButton(
                        onClick = { viewModel.sendAction(CustomerListAction.RefreshData) },
                        enabled = !state.isRefreshing
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SearchBar(
                value = state.searchQuery,
                onValueChange = { viewModel.sendAction(CustomerListAction.Search(it)) },
                placeholder = "Search by name or phone...",
                modifier = Modifier.padding(16.dp)
            )
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.sendAction(CustomerListAction.RefreshData) },
                modifier = Modifier.weight(1f)
            ) {
                when {
                    state.isLoading && state.filteredCustomers.isEmpty() -> {
                        ShimmeringList(itemHeight = 130.dp)
                    }
                    state.filteredCustomers.isEmpty() -> {
                        EmptyState(
                            title = if (state.searchQuery.isNotEmpty()) "No customers found" else "No customers yet",
                            subtitle = "Your customers will appear here.",
                            icon = Icons.Default.PeopleOutline
                        )
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.filteredCustomers, key = { it.customer.id }) { customerWithStats ->
                                CustomerCard(
                                    customer = customerWithStats.customer,
                                    stats = customerWithStats.stats,
                                    onClick = { onNavigateToCustomer(customerWithStats.customer.id) },
                                    onCallClick = { viewModel.sendAction(CustomerListAction.CallCustomer(customerWithStats.customer)) },
                                    onEmailClick = { viewModel.sendAction(CustomerListAction.EmailCustomer(customerWithStats.customer)) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
private fun openDialer(context: Context, phone: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()))
    } catch (_: Exception) {
        // Fails silently if no dialer is available.
    }
}
private suspend fun openEmail(context: Context, email: String, snackbar: SnackbarHostState) {
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:$email".toUri())
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        snackbar.showSnackbar("No email app found.")
    }
}
