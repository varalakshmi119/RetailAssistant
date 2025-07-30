package com.retailassistant.ui.navigation
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
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
private const val FADE_ANIM_DURATION = 300
private const val SLIDE_ANIM_DURATION = 400
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val supabase: SupabaseClient = koinInject()
    val sessionStatus by supabase.auth.sessionStatus.collectAsState()
    // This effect handles automatic navigation on auth state changes (e.g., logout).
    LaunchedEffect(sessionStatus, navController) {
        if (sessionStatus is SessionStatus.NotAuthenticated) {
            navController.navigateToAuth()
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
                // Decides where to go on a cold start, preventing a flash of the auth screen.
                composable("root_decider") {
                    when (sessionStatus) {
                        is SessionStatus.Authenticated -> {
                            LaunchedEffect(Unit) {
                                navController.navigate(Screen.Main.route) {
                                    popUpTo("root_decider") { inclusive = true }
                                }
                            }
                        }
                        is SessionStatus.NotAuthenticated -> {
                            LaunchedEffect(Unit) {
                                navController.navigate(Screen.Auth.route) {
                                    popUpTo("root_decider") { inclusive = true }
                                }
                            }
                        }
                        else -> FullScreenLoading(modifier = Modifier.fillMaxSize()) // Loading, Awaiting...
                    }
                }
                authGraph(navController)
                mainGraph(navController)
            }
        }
    }
}
private fun NavGraphBuilder.authGraph(navController: NavHostController) {
    navigation(
        route = Screen.Auth.route,
        startDestination = Screen.Auth.Login.route
    ) {
        composable(Screen.Auth.Login.route) {
            AuthScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
private fun NavGraphBuilder.mainGraph(navController: NavHostController) {
    navigation(
        route = Screen.Main.route,
        startDestination = Screen.Main.Content.route
    ) {
        composable(Screen.Main.Content.route) {
            MainScreen(rootNavController = navController)
        }
        composable(
            route = Screen.InvoiceDetail.route,
            arguments = listOf(navArgument("invoiceId") { type = NavType.StringType }),
            enterTransition = { slideInHorizontally(tween(SLIDE_ANIM_DURATION)) { it } },
            popExitTransition = { slideOutHorizontally(tween(SLIDE_ANIM_DURATION)) { it } }
        ) { backStackEntry ->
            // FIX: Replaced !! with requireNotNull for safer argument access
            val invoiceId = requireNotNull(backStackEntry.arguments?.getString("invoiceId"))
            InvoiceDetailScreen(
                invoiceId = invoiceId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCustomer = { id -> navController.navigate(Screen.CustomerDetail.createRoute(id)) }
            )
        }
        composable(
            route = Screen.CustomerDetail.route,
            arguments = listOf(navArgument("customerId") { type = NavType.StringType }),
            enterTransition = { slideInHorizontally(tween(SLIDE_ANIM_DURATION)) { it } },
            popExitTransition = { slideOutHorizontally(tween(SLIDE_ANIM_DURATION)) { it } }
        ) { backStackEntry ->
            // FIX: Replaced !! with requireNotNull for safer argument access
            val customerId = requireNotNull(backStackEntry.arguments?.getString("customerId"))
            CustomerDetailScreen(
                customerId = customerId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToInvoice = { id -> navController.navigate(Screen.InvoiceDetail.createRoute(id)) }
            )
        }
        composable(
            route = Screen.InvoiceCreation.route,
            enterTransition = { slideInVertically(tween(SLIDE_ANIM_DURATION)) { it } },
            popExitTransition = { slideOutVertically(tween(SLIDE_ANIM_DURATION)) { it } }
        ) {
            InvoiceCreationScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
@Composable
private fun MainScreen(rootNavController: NavHostController) {
    val bottomBarNavController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentNavBackStackEntry by bottomBarNavController.currentBackStackEntryAsState()
    val currentRoute = currentNavBackStackEntry?.destination?.route
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { AppBottomBar(navController = bottomBarNavController) },
        floatingActionButton = {
            // DESIGN: FAB is now more prominent and only shows on the dashboard.
            if (currentRoute == Screen.Dashboard.route) {
                FloatingActionButton(
                    onClick = { rootNavController.navigate(Screen.InvoiceCreation.route) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Rounded.Add, "Create Invoice")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        NavHost(
            navController = bottomBarNavController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(FADE_ANIM_DURATION)) },
            exitTransition = { fadeOut(tween(FADE_ANIM_DURATION)) }
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
fun NavHostController.navigateToAuth() {
    if (this.currentDestination?.route?.startsWith(Screen.Auth.route) == false) {
        navigate(Screen.Auth.route) {
            popUpTo(Screen.Main.route) { inclusive = true }
        }
    }
}
