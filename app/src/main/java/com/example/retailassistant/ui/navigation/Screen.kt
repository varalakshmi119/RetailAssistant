package com.example.retailassistant.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A sealed class representing all navigable screens in the app.
 * Using this provides compile-time safety for navigation routes.
 */
sealed class Screen(val route: String) {
    // Top-level graphs/destinations
    data object Auth : Screen("auth")
    data object Main : Screen("main/{showSyncError}") {
        fun createRoute(showSyncError: Boolean) = "main/$showSyncError"
    }

    // Bottom navigation destinations
    data object Dashboard : Screen("dashboard")
    data object Customers : Screen("customers")

    // Feature destinations
    data object AddInvoice : Screen("add_invoice")
    data object InvoiceDetail : Screen("invoice_detail/{invoiceId}") {
        fun createRoute(invoiceId: String) = "invoice_detail/$invoiceId"
    }
    data object CustomerDetail : Screen("customer_detail/{customerId}") {
        fun createRoute(customerId: String) = "customer_detail/$customerId"
    }
}

/**
 * Data class representing an item in the bottom navigation bar.
 */
data class BottomNavItem(
    val title: String,
    val route: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem("Dashboard", Screen.Dashboard.route, Icons.Default.Dashboard),
    BottomNavItem("Customers", Screen.Customers.route, Icons.Default.Group)
)
