package com.example.retailassistant.ui.navigation
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.retailassistant.ui.screen.auth.AuthScreen
import com.example.retailassistant.ui.screen.dashboard.CustomerDetailScreen
import com.example.retailassistant.ui.screen.dashboard.MainScreen
import com.example.retailassistant.ui.screen.invoice.InvoiceCreationScreen
import com.example.retailassistant.ui.screen.invoice.InvoiceDetailScreen
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
sealed class Screen(val route: String) {
    data object Auth : Screen("auth")
    data object Main : Screen("main")
    data object AddInvoice : Screen("add_invoice")
    data object InvoiceDetail : Screen("invoice_detail/{invoiceId}") {
        fun createRoute(invoiceId: String) = "invoice_detail/$invoiceId"
    }
    data object CustomerDetail : Screen("customer_detail/{customerId}") {
        fun createRoute(customerId: String) = "customer_detail/$customerId"
    }
}
@Composable
fun AppNavigation(supabase: SupabaseClient) {
    val navController = rememberNavController()
    val isLoggedIn = supabase.auth.currentUserOrNull() != null
    val startDestination = if (isLoggedIn) Screen.Main.route else Screen.Auth.route
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() }
    ) {
        composable(Screen.Auth.route) {
            AuthScreen(
                onNavigateToDashboard = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToAddInvoice = {
                    navController.navigate(Screen.AddInvoice.route)
                },
                onNavigateToInvoiceDetail = { invoiceId ->
                    navController.navigate(Screen.InvoiceDetail.createRoute(invoiceId))
                },
                onNavigateToCustomerDetail = { customerId ->
                    navController.navigate(Screen.CustomerDetail.createRoute(customerId))
                },
                onLogout = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.AddInvoice.route) {
            InvoiceCreationScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = Screen.InvoiceDetail.route,
            arguments = listOf(navArgument("invoiceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val invoiceId = backStackEntry.arguments?.getString("invoiceId")
            requireNotNull(invoiceId) { "Invoice ID is required" }
            InvoiceDetailScreen(
                invoiceId = invoiceId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = Screen.CustomerDetail.route,
            arguments = listOf(navArgument("customerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getString("customerId")
            requireNotNull(customerId) { "Customer ID is required" }
            CustomerDetailScreen(
                customerId = customerId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToInvoiceDetail = { invoiceId ->
                    navController.navigate(Screen.InvoiceDetail.createRoute(invoiceId))
                }
            )
        }
    }
}
