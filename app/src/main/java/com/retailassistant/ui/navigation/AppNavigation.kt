package com.retailassistant.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.*
import androidx.navigation.compose.*
import com.retailassistant.features.auth.AuthScreen
import com.retailassistant.features.customers.CustomerDetailScreen
import com.retailassistant.features.customers.CustomerListScreen
import com.retailassistant.features.dashboard.DashboardScreen
import com.retailassistant.features.invoices.creation.InvoiceCreationScreen
import com.retailassistant.features.invoices.detail.InvoiceDetailScreen
import com.retailassistant.features.invoices.list.InvoiceListScreen
import com.retailassistant.ui.components.common.AppBottomBar
import com.retailassistant.ui.components.common.FullScreenLoading
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import org.koin.compose.koinInject

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val supabase: SupabaseClient = koinInject()
    val sessionStatus by supabase.auth.sessionStatus.collectAsState()

    // This effect handles automatic navigation on auth state changes (e.g., logout).
    LaunchedEffect(sessionStatus, navController) {
        if (sessionStatus is SessionStatus.NotAuthenticated) {
            // If the current route is part of the main app graph, navigate to auth.
            // This prevents navigation loops if we're already on the auth screen.
            if (navController.currentDestination?.parent?.route == Screen.Dashboard.route) {
                navController.navigate(Screen.Auth.route) {
                    popUpTo(Screen.Dashboard.route) { inclusive = true }
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = "root_decider" // Use a static start destination
            ) {
                // This is a transient screen. It decides where to go on a cold start
                // and will not be on the back stack. This approach ensures that Compose
                // Navigation can correctly restore the back stack after process death.
                composable("root_decider") {
                    val targetRoute = remember {
                        if (supabase.auth.currentUserOrNull() != null) {
                            Screen.Dashboard.route
                        } else {
                            Screen.Auth.route
                        }
                    }

                    LaunchedEffect(Unit) {
                        navController.navigate(targetRoute) {
                            popUpTo("root_decider") { inclusive = true }
                        }
                    }
                    // Show a loading screen while the navigation action is processed.
                    FullScreenLoading()
                }

                authGraph(navController)
                mainGraph(navController)
            }
        }
    }
}

private fun NavGraphBuilder.authGraph(navController: NavController) {
    navigation(
        route = Screen.Auth.route,
        startDestination = "login"
    ) {
        composable("login") {
            AuthScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }
    }
}

private fun NavGraphBuilder.mainGraph(navController: NavHostController) {
    navigation(
        route = Screen.Dashboard.route,
        startDestination = "main_content"
    ) {
        composable("main_content") {
            MainScreen(navController = navController)
        }
        composable(
            route = Screen.InvoiceDetail.route,
            arguments = listOf(navArgument("invoiceId") { type = NavType.StringType }),
            enterTransition = { slideInHorizontally(animationSpec = tween(400)) { it } },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(400)) { it } }
        ) {
            val id = it.arguments?.getString("invoiceId") ?: ""
            InvoiceDetailScreen(
                invoiceId = id,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCustomer = { customerId ->
                    navController.navigate(Screen.CustomerDetail.createRoute(customerId))
                }
            )
        }
        composable(
            route = Screen.CustomerDetail.route,
            arguments = listOf(navArgument("customerId") { type = NavType.StringType }),
            enterTransition = { slideInHorizontally(animationSpec = tween(400)) { it } },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(400)) { it } }
        ) {
            val id = it.arguments?.getString("customerId") ?: ""
            CustomerDetailScreen(
                customerId = id,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToInvoice = { invoiceId ->
                    navController.navigate(Screen.InvoiceDetail.createRoute(invoiceId))
                }
            )
        }
        composable(
            route = Screen.InvoiceCreation.route,
            enterTransition = { slideInVertically(animationSpec = tween(500)) { it } },
            exitTransition = { slideOutVertically(animationSpec = tween(500)) { it } }
        ) {
            InvoiceCreationScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun MainScreen(navController: NavHostController) {
    val mainNavController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        bottomBar = { AppBottomBar(navController = mainNavController) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.InvoiceCreation.route) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Rounded.Add, "Create Invoice")
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        NavHost(
            navController = mainNavController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            val enterTransition = fadeIn(tween(300))
            val exitTransition = fadeOut(tween(300))
            composable(Screen.Dashboard.route, enterTransition = { enterTransition }, exitTransition = { exitTransition }) {
                DashboardScreen(
                    onNavigateToInvoice = { navController.navigate(Screen.InvoiceDetail.createRoute(it)) },
                    snackbarHostState = snackbarHostState
                )
            }
            composable(Screen.Invoices.route, enterTransition = { enterTransition }, exitTransition = { exitTransition }) {
                InvoiceListScreen(
                    onNavigateToInvoice = { navController.navigate(Screen.InvoiceDetail.createRoute(it)) },
                    snackbarHostState = snackbarHostState
                )
            }
            composable(Screen.Customers.route, enterTransition = { enterTransition }, exitTransition = { exitTransition }) {
                CustomerListScreen(
                    onNavigateToCustomer = { navController.navigate(Screen.CustomerDetail.createRoute(it)) },
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }
}