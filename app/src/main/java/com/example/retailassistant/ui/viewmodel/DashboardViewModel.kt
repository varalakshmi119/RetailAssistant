package com.example.retailassistant.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.retailassistant.data.Customer
import com.example.retailassistant.data.Invoice
import com.example.retailassistant.data.InvoiceRepository
import com.example.retailassistant.ui.MviViewModel
import com.example.retailassistant.ui.UiAction
import com.example.retailassistant.ui.UiEvent
import com.example.retailassistant.ui.UiState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    val overdueCount: Int = 0
) : UiState

sealed class DashboardAction : UiAction {
    object RefreshData : DashboardAction()
    object Logout : DashboardAction()
}

sealed class DashboardEvent : UiEvent {
    data class ShowError(val message: String) : DashboardEvent()
    data class NavigateToInvoiceDetail(val invoiceId: String) : DashboardEvent()
    object Logout : DashboardEvent()
}

class DashboardViewModel(
    private val repository: InvoiceRepository,
    private val supabase: SupabaseClient
) : MviViewModel<DashboardState, DashboardAction, DashboardEvent>() {

    val combinedData: StateFlow<DashboardState> =
        combine(
            repository.getInvoicesStream(),
            repository.getCustomersStream()
        ) { invoices, customers ->
            val customerMap = customers.associateBy { it.id }
            val invoicesWithCustomers = invoices.sortedByDescending { it.createdAt }.map { invoice ->
                InvoiceWithCustomer(
                    invoice = invoice,
                    customer = customerMap[invoice.customerId]
                )
            }
            val totalUnpaid = invoices.filter { it.status != com.example.retailassistant.data.InvoiceStatus.PAID }.sumOf { it.totalAmount - it.amountPaid }
            val overdueCount = invoices.count { it.isOverdue }

            DashboardState(
                invoicesWithCustomers = invoicesWithCustomers,
                isLoading = false,
                totalUnpaid = totalUnpaid,
                overdueCount = overdueCount
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardState()
        )

    override fun createInitialState(): DashboardState = DashboardState()

    init {
        refreshData(isInitial = true)
    }

    override fun handleAction(action: DashboardAction) {
        when (action) {
            is DashboardAction.RefreshData -> refreshData()
            is DashboardAction.Logout -> logout()
        }
    }

    private fun logout() {
        viewModelScope.launch {
            supabase.auth.signOut()
            sendEvent(DashboardEvent.Logout)
        }
    }

    fun onInvoiceClicked(invoiceId: String) {
        sendEvent(DashboardEvent.NavigateToInvoiceDetail(invoiceId))
    }

    private fun refreshData(isInitial: Boolean = false) {
        viewModelScope.launch {
            if (!isInitial) setState { copy(isRefreshing = true) }
            repository.syncUserData().onFailure { error ->
                sendEvent(DashboardEvent.ShowError(error.message ?: "Sync failed"))
            }
            if (!isInitial) setState { copy(isRefreshing = false) }
        }
    }
}
