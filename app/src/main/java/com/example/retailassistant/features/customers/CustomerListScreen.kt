package com.example.retailassistant.features.customers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.retailassistant.ui.components.CustomerCard
import com.example.retailassistant.ui.components.EmptyState
import com.example.retailassistant.ui.components.SearchTextField
import com.example.retailassistant.ui.components.ShimmeringCustomerList
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    onNavigateToCustomerDetail: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: CustomerListViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel.event) {
        viewModel.event.collect { event ->
            when (event) {
                is CustomerListEvent.OpenDialer -> {
                    context.startActivity(event.intent)
                }
                is CustomerListEvent.OpenEmail -> {
                    // Check if an email app is available before launching
                    if (event.intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(event.intent)
                    } else {
                        snackbarHostState.showSnackbar("No email app found.")
                    }
                }
                is CustomerListEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customers", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.sendAction(CustomerListAction.RefreshData) },
            modifier = Modifier.padding(padding)
        ) {
            if (state.isLoading) {
                ShimmeringCustomerList()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SearchTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.sendAction(CustomerListAction.Search(it)) },
                            placeholder = "Search by name, phone, or email"
                        )
                    }

                    if (state.filteredCustomers.isEmpty()) {
                        item {
                            EmptyState(
                                title = if (state.searchQuery.isNotEmpty()) "No customers found" else "No customers yet",
                                subtitle = if (state.searchQuery.isNotEmpty())
                                    "Try adjusting your search query."
                                else
                                    "Your customers will appear here once you create invoices for them.",
                                icon = Icons.Default.PeopleOutline
                            )
                        }
                    } else {
                        items(state.filteredCustomers, key = { it.customer.id }) { customerWithStats ->
                            CustomerCard(
                                customer = customerWithStats.customer,
                                stats = customerWithStats.stats,
                                onClick = { onNavigateToCustomerDetail(customerWithStats.customer.id) },
                                onCallClick = customerWithStats.customer.phone?.let { phone -> { viewModel.sendAction(CustomerListAction.StartCall(phone)) } },
                                onEmailClick = customerWithStats.customer.email?.let { email -> { viewModel.sendAction(CustomerListAction.StartEmail(email)) } }
                            )
                        }
                    }
                }
            }
        }
    }
}
