package com.retailassistant.features.customers

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retailassistant.ui.components.common.*
import com.retailassistant.ui.components.specific.CustomerCard
import org.koin.androidx.compose.koinViewModel

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
                    if (state.isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        IconButton(onClick = { viewModel.sendAction(CustomerListAction.RefreshData) }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {

                SearchBar(
                    value = state.searchQuery,
                    onValueChange = { viewModel.sendAction(CustomerListAction.Search(it)) },
                    placeholder = "Search by name, phone, or email...",
                    modifier = Modifier.padding(16.dp)
                )

                if (state.isLoading) {
                    ShimmeringList()
                } else if (state.filteredCustomers.isEmpty()) {
                    EmptyState(
                        title = if (state.searchQuery.isNotEmpty()) "No customers found" else "No customers yet",
                        subtitle = "Your customers will appear here.",
                        icon = Icons.Default.PeopleOutline
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
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

private fun openDialer(context: Context, phone: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()))
    } catch (_: Exception) {}
}

private suspend fun openEmail(context: Context, email: String, snackbar: SnackbarHostState) {
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:$email".toUri())
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        snackbar.showSnackbar("No email app found.")
    }
}
