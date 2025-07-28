package com.example.retailassistant.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.retailassistant.features.auth.AuthScreen
import com.example.retailassistant.features.customers.CustomerDetailScreen
import com.example.retailassistant.features.customers.CustomerListScreen
import com.example.retailassistant.features.dashboard.DashboardScreen
import com.example.retailassistant.features.invoices.InvoiceCreationScreen
import com.example.retailassistant.features.invoices.InvoiceDetailScreen
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import org.koin.compose.koinInject

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val supabase: SupabaseClient = koinInject()
    val sessionStatus by supabase.auth.sessionStatus.collectAsState()

    // The start route is determined by the auth state. It's null while initializing.
    val startRoute = remember(sessionStatus) {
        when (sessionStatus) {
            is SessionStatus.Authenticated -> Screen.Main.createRoute(false) // Default to no sync error
            is SessionStatus.NotAuthenticated -> Screen.Auth.route
            else -> null // Initializing or other states
        }
    }

    if (startRoute == null) {
        // Show a loading indicator while the session is being initialized.
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        NavHost(
            navController = navController,
            startDestination = startRoute
        ) {
            composable(Screen.Auth.route) {
                AuthScreen(
                    onLoginSuccess = { showSyncError ->
                        // Navigate to the main graph, clearing the auth backstack.
                        navController.navigate(Screen.Main.createRoute(showSyncError)) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                route = Screen.Main.route,
                arguments = listOf(navArgument("showSyncError") { type = NavType.BoolType })
            ) { backStackEntry ->
                val showSyncError = backStackEntry.arguments?.getBoolean("showSyncError") ?: false
                MainScreenContainer(
                    showSyncError = showSyncError
                )
            }
        }
    }
}

@Composable
private fun MainScreenContainer(showSyncError: Boolean) {
    val mainNavController = rememberNavController()
    val navBackStackEntry by mainNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val showBottomBarAndFab = currentDestination?.route in bottomNavItems.map { it.route }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBarAndFab,
                enter = slideInVertically(animationSpec = tween(200)) { it },
                exit = slideOutVertically(animationSpec = tween(200)) { it }
            ) {
                AppBottomBar(mainNavController, currentDestination)
            }
        },
        floatingActionButton = {
            if (showBottomBarAndFab) {
                FloatingActionButton(
                    onClick = { mainNavController.navigate(Screen.AddInvoice.route) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    Icon(Icons.Default.Add, "Add Invoice")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        MainAppNavHost(mainNavController, padding, showSyncError, snackbarHostState)
    }
}

@Composable
private fun AppBottomBar(navController: NavHostController, currentDestination: NavDestination?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)) {
        bottomNavItems.forEach { screen ->
            val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
            NavigationBarItem(
                icon = { Icon(screen.icon, null, modifier = Modifier.size(if (isSelected) 26.dp else 24.dp)) },
                label = { Text(screen.title, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
                selected = isSelected,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun MainAppNavHost(
    navController: NavHostController,
    padding: PaddingValues,
    showSyncError: Boolean,
    snackbarHostState: SnackbarHostState
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = Modifier.padding(padding),
    ) {
        composable(
            Screen.Dashboard.route,
            enterTransition = { fadeIn(tween(300)) },
            exitTransition = { fadeOut(tween(300)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(300)) }
        ) {
            DashboardScreen(
                onNavigateToInvoiceDetail = { navController.navigate(Screen.InvoiceDetail.createRoute(it)) },
                showSyncError = showSyncError,
                snackbarHostState = snackbarHostState
            )
        }
        composable(
            Screen.Customers.route,
            enterTransition = { fadeIn(tween(300)) },
            exitTransition = { fadeOut(tween(300)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(300)) }
        ) {
            CustomerListScreen(
                onNavigateToCustomerDetail = { navController.navigate(Screen.CustomerDetail.createRoute(it)) },
                snackbarHostState = snackbarHostState
            )
        }
        composable(
            Screen.AddInvoice.route,
            enterTransition = { slideInVertically(animationSpec = tween(400)) { it } + fadeIn() },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { slideInVertically(animationSpec = tween(400)) { -it } + fadeIn() },
            popExitTransition = { slideOutVertically(animationSpec = tween(400)) { it } + fadeOut() },
        ) {
            InvoiceCreationScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(
            Screen.InvoiceDetail.route,
            arguments = listOf(navArgument("invoiceId") { type = NavType.StringType }),
            enterTransition = { slideInHorizontally(animationSpec = tween(350)) { it } },
            exitTransition = { slideOutHorizontally(animationSpec = tween(350)) { -it } },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(350)) { -it } },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(350)) { it } }
        ) {
            val id = it.arguments?.getString("invoiceId") ?: ""
            InvoiceDetailScreen(invoiceId = id, onNavigateBack = { navController.popBackStack() })
        }
        composable(
            Screen.CustomerDetail.route,
            arguments = listOf(navArgument("customerId") { type = NavType.StringType }),
            enterTransition = { slideInHorizontally(animationSpec = tween(350)) { it } },
            exitTransition = { slideOutHorizontally(animationSpec = tween(350)) { -it } },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(350)) { -it } },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(350)) { it } }
        ) {
            val id = it.arguments?.getString("customerId") ?: ""
            CustomerDetailScreen(
                customerId = id,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToInvoiceDetail = { navController.navigate(Screen.InvoiceDetail.createRoute(it)) }
            )
        }
    }
}