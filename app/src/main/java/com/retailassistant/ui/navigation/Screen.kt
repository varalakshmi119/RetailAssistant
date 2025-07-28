package com.retailassistant.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A sealed class representing all unique destinations in the app for type-safe navigation.
 */
sealed class Screen(val route: String) {
    data object Auth : Screen("auth")
    data object Dashboard : Screen("dashboard")
    data object Invoices : Screen("invoices")
    data object Customers : Screen("customers")
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
    BottomNavItem(Screen.Customers, "Customers", Icons.Rounded.Groups)
)
