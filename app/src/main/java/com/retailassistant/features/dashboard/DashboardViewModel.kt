package com.retailassistant.features.dashboard

import androidx.lifecycle.viewModelScope
import com.retailassistant.core.MviViewModel
import com.retailassistant.core.UiAction
import com.retailassistant.core.UiEvent
import com.retailassistant.core.UiState
import com.retailassistant.data.db.Customer
import com.retailassistant.data.db.Invoice
import com.retailassistant.data.repository.RetailRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class InvoiceWithCustomer(
    val invoice: Invoice,
    val customer: Customer?
)

data class DashboardState(
    val invoicesWithCustomers: List<InvoiceWithCustomer> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val totalUnpaid: Double = 0.0,
    val overdueCount: Int = 0,
    val userName: String? = null
) : UiState

sealed interface DashboardAction : UiAction {
    object RefreshData : DashboardAction
    data class DataLoaded(val invoices: List<Invoice>, val customers: List<Customer>) : DashboardAction
    object SignOut : DashboardAction
}

sealed interface DashboardEvent : UiEvent {
    data class ShowError(val message: String) : DashboardEvent
}

class DashboardViewModel(
    private val repository: RetailRepository,
    private val supabase: SupabaseClient
) : MviViewModel<DashboardState, DashboardAction, DashboardEvent>() {

    private val userId: String? = supabase.auth.currentUserOrNull()?.id

    init {
        val user = supabase.auth.currentUserOrNull()
        setState { copy(userName = user?.email?.substringBefore('@')?.replaceFirstChar { it.titlecase() }) }

        if (userId != null) {
            repository.getInvoicesStream(userId)
                .combine(repository.getCustomersStream(userId)) { invoices, customers ->
                    DashboardAction.DataLoaded(invoices, customers)
                }
                .catch { e -> sendEvent(DashboardEvent.ShowError(e.message ?: "Failed to load data")) }
                .onEach { sendAction(it) }
                .launchIn(viewModelScope)

            refreshData(isInitial = true)
        } else {
            setState { copy(isLoading = false) }
        }
    }

    override fun createInitialState(): DashboardState = DashboardState()

    override fun handleAction(action: DashboardAction) {
        when (action) {
            is DashboardAction.RefreshData -> refreshData()
            is DashboardAction.DataLoaded -> updateStateWithData(action.invoices, action.customers)
            is DashboardAction.SignOut -> signOut()
        }
    }

    private fun updateStateWithData(invoices: List<Invoice>, customers: List<Customer>) {
        val customerMap = customers.associateBy { it.id }
        val invoicesWithCustomers = invoices.map { invoice ->
            InvoiceWithCustomer(invoice, customerMap[invoice.customerId])
        }
        setState {
            copy(
                invoicesWithCustomers = invoicesWithCustomers.take(10), // Show most recent 10
                totalUnpaid = invoices.sumOf { it.balanceDue },
                overdueCount = invoices.count { it.isOverdue },
                isLoading = false,
                isRefreshing = false
            )
        }
    }

    private fun refreshData(isInitial: Boolean = false) {
        if (userId == null) return
        viewModelScope.launch {
            if (!isInitial) setState { copy(isRefreshing = true) }
            repository.syncAllUserData(userId).onFailure { error ->
                sendEvent(DashboardEvent.ShowError(error.message ?: "Sync failed"))
                if (!isInitial) setState { copy(isRefreshing = false) }
            }
        }
    }

    private fun signOut() {
        if (userId == null) return
        viewModelScope.launch {
            repository.signOut(userId)
            // Navigation is handled reactively by AppNavigation observing the session status.
        }
    }
}
