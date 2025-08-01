package com.retailassistant.ui.navigation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A sealed class representing all unique destinations in the app for type-safe navigation.
 * Routes are structured hierarchically for better organization and back-stack management.
 */
sealed class Screen(val route: String) {
    // Top-level navigation graphs
    data object Auth : Screen("auth_graph") {
        data object Login : Screen("auth_graph/login")
    }
    data object Main : Screen("main_graph") {
        data object Content : Screen("main_graph/content")
    }
    // Bottom navigation items
    data object Dashboard : Screen("dashboard")
    data object Invoices : Screen("invoices")
    data object Customers : Screen("customers")
    data object Settings : Screen("settings")
    
    // Detail/Creation screens
    data object InvoiceCreation : Screen("invoice_creation")
    data object InvoiceDetail : Screen("invoice_detail/{invoiceId}") {
        fun createRoute(invoiceId: String) = "invoice_detail/$invoiceId"
    }
    data object CustomerDetail : Screen("customer_detail/{customerId}") {
        fun createRoute(customerId: String) = "customer_detail/$customerId"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val title: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, "Dashboard", Icons.Rounded.Dashboard),
    BottomNavItem(Screen.Invoices, "Invoices", Icons.AutoMirrored.Rounded.ReceiptLong),
    BottomNavItem(Screen.Customers, "Customers", Icons.Rounded.Groups),
    BottomNavItem(Screen.Settings, "Settings", Icons.Rounded.Settings)
)
