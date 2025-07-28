package com.retailassistant.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
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
            val isNotInAuthGraph = navController.currentBackStack.value.any { it.destination.route?.startsWith(Screen.Auth.route) == false }
            if(isNotInAuthGraph) {
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
                startDestination = "root_decider"
            ) {
                // This transient destination decides where to go on a cold start.
                // This is a robust pattern for handling process death and initial auth state.
                composable("root_decider") {
                    val targetRoute = remember {
                        if (supabase.auth.currentSessionOrNull() != null) Screen.Dashboard.route else Screen.Auth.route
                    }
                    LaunchedEffect(Unit) {
                        navController.navigate(targetRoute) {
                            popUpTo("root_decider") { inclusive = true }
                        }
                    }
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
    val slideHorizontal = slideInHorizontally(animationSpec = tween(400)) { it }
    val popSlideHorizontal = slideOutHorizontally(animationSpec = tween(400)) { it }
    val slideVertical = slideInVertically(animationSpec = tween(500)) { it }
    val popSlideVertical = slideOutVertically(animationSpec = tween(500)) { it }

    navigation(
        route = Screen.Dashboard.route,
        startDestination = "main_content"
    ) {
        composable("main_content") {
            MainScreen(rootNavController = navController)
        }
        composable(
            route = Screen.InvoiceDetail.route,
            arguments = listOf(navArgument("invoiceId") { type = NavType.StringType }),
            enterTransition = { slideHorizontal }, popExitTransition = { popSlideHorizontal }
        ) {
            InvoiceDetailScreen(
                invoiceId = it.arguments?.getString("invoiceId") ?: "",
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCustomer = { id -> navController.navigate(Screen.CustomerDetail.createRoute(id)) }
            )
        }
        composable(
            route = Screen.CustomerDetail.route,
            arguments = listOf(navArgument("customerId") { type = NavType.StringType }),
            enterTransition = { slideHorizontal }, popExitTransition = { popSlideHorizontal }
        ) {
            CustomerDetailScreen(
                customerId = it.arguments?.getString("customerId") ?: "",
                onNavigateBack = { navController.popBackStack() },
                onNavigateToInvoice = { id -> navController.navigate(Screen.InvoiceDetail.createRoute(id)) }
            )
        }
        composable(
            route = Screen.InvoiceCreation.route,
            enterTransition = { slideVertical },
            popExitTransition = { popSlideVertical }
        ) {
            InvoiceCreationScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun MainScreen(rootNavController: NavHostController) {
    val bottomBarNavController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        bottomBar = { AppBottomBar(navController = bottomBarNavController) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { rootNavController.navigate(Screen.InvoiceCreation.route) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Rounded.Add, "Create Invoice")
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        NavHost(
            navController = bottomBarNavController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(300)) },
            exitTransition = { fadeOut(tween(300)) }
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToInvoice = { rootNavController.navigate(Screen.InvoiceDetail.createRoute(it)) },
                    snackbarHostState = snackbarHostState
                )
            }
            composable(Screen.Invoices.route) {
                InvoiceListScreen(
                    onNavigateToInvoice = { rootNavController.navigate(Screen.InvoiceDetail.createRoute(it)) },
                    snackbarHostState = snackbarHostState
                )
            }
            composable(Screen.Customers.route) {
                CustomerListScreen(
                    onNavigateToCustomer = { rootNavController.navigate(Screen.CustomerDetail.createRoute(it)) },
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }
}
