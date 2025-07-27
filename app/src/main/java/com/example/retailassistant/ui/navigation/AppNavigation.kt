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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.retailassistant.core.rememberAuthState
import com.example.retailassistant.features.auth.AuthScreen
import com.example.retailassistant.features.customers.CustomerDetailScreen
import com.example.retailassistant.features.customers.CustomerListScreen
import com.example.retailassistant.features.dashboard.DashboardScreen
import com.example.retailassistant.features.invoices.InvoiceCreationScreen
import com.example.retailassistant.features.invoices.InvoiceDetailScreen
import com.example.retailassistant.ui.theme.AppGradients
import io.github.jan.supabase.SupabaseClient
import org.koin.compose.koinInject

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val supabase: SupabaseClient = koinInject()
    val authState = rememberAuthState(supabase)

    var isInitializing by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Give Supabase a moment to restore the session from storage
        kotlinx.coroutines.delay(200)
        isInitializing = false
    }

    // This block automatically navigates the user based on their auth state
    LaunchedEffect(authState.isAuthenticated, isInitializing) {
        if (!isInitializing) {
            val targetRoute = if (authState.isAuthenticated) Screen.Main.createRoute(false) else Screen.Auth.route
            if (navController.currentDestination?.route != targetRoute) {
                navController.navigate(targetRoute) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        }
    }

    if (isInitializing) {
        // Show a branded loading screen while checking session
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination = if (authState.isAuthenticated) Screen.Main.createRoute(false) else Screen.Auth.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Auth.route) {
            AuthScreen(
                onLoginSuccess = { showSyncError ->
                    // Navigation is handled by the LaunchedEffect above, this is a fallback.
                    if (navController.currentDestination?.route != Screen.Main.route) {
                        navController.navigate(Screen.Main.createRoute(showSyncError)) {
                           popUpTo(Screen.Auth.route) { inclusive = true }
                        }
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
                showSyncError = showSyncError,
                onLogout = {
                    if (navController.currentDestination?.route != Screen.Auth.route) {
                        navController.navigate(Screen.Auth.route) { popUpTo(0) { inclusive = true } }
                    }
                }
            )
        }
    }
}

@Composable
private fun MainScreenContainer(showSyncError: Boolean, onLogout: () -> Unit) {
    val mainNavController = rememberNavController()
    val navBackStackEntry by mainNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val snackbarHostState = remember { SnackbarHostState() }

    val showBottomBarAndFab = currentDestination?.route in bottomNavItems.map { it.route }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { if (showBottomBarAndFab) AppBottomBar(mainNavController, currentDestination) },
        floatingActionButton = {
            if (showBottomBarAndFab) {
                 FloatingActionButton(
                    onClick = { mainNavController.navigate(Screen.AddInvoice.route) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, "Add Invoice")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        MainAppNavHost(mainNavController, padding, onLogout, showSyncError, snackbarHostState)
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
    onLogout: () -> Unit,
    showSyncError: Boolean,
    snackbarHostState: SnackbarHostState
) {
    val slideSpec = tween<IntOffset>(350)
    val fadeSpec = tween<Float>(350)
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = Modifier.padding(padding),
    ) {
        composable(
            Screen.Dashboard.route,
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) }
            ) {
            DashboardScreen(
                onNavigateToInvoiceDetail = { navController.navigate(Screen.InvoiceDetail.createRoute(it)) },
                onLogout = onLogout,
                showSyncError = showSyncError,
                snackbarHostState = snackbarHostState
            )
        }
        composable(
            Screen.Customers.route,
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) }
            ) {
            CustomerListScreen(
                onNavigateToCustomerDetail = { navController.navigate(Screen.CustomerDetail.createRoute(it)) },
                snackbarHostState = snackbarHostState
            )
        }

        // --- Detail and Creation Screens ---
        val commonEnter: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition) =
            { slideInHorizontally(animationSpec = slideSpec) { it } + fadeIn(fadeSpec) }
        val commonExit: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition) =
            { slideOutHorizontally(animationSpec = slideSpec) { -it } + fadeOut(fadeSpec) }
        val commonPopEnter: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition) =
            { slideInHorizontally(animationSpec = slideSpec) { -it } + fadeIn(fadeSpec) }
        val commonPopExit: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition) =
            { slideOutHorizontally(animationSpec = slideSpec) { it } + fadeOut(fadeSpec) }


        composable(
            Screen.AddInvoice.route,
            enterTransition = { slideInVertically(animationSpec = tween(400)) { it } + fadeIn() },
            exitTransition = { slideOutVertically(animationSpec = tween(400)) { it } + fadeOut() },
        ) {
            InvoiceCreationScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            Screen.InvoiceDetail.route, arguments = listOf(navArgument("invoiceId") { type = NavType.StringType }),
            enterTransition = commonEnter, exitTransition = commonExit, popEnterTransition = commonPopEnter, popExitTransition = commonPopExit
        ) {
            val id = it.arguments?.getString("invoiceId") ?: ""
            InvoiceDetailScreen(invoiceId = id, onNavigateBack = { navController.popBackStack() })
        }
        composable(
            Screen.CustomerDetail.route, arguments = listOf(navArgument("customerId") { type = NavType.StringType }),
            enterTransition = commonEnter, exitTransition = commonExit, popEnterTransition = commonPopEnter, popExitTransition = commonPopExit
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
