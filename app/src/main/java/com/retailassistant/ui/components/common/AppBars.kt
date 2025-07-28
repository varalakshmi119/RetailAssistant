package com.retailassistant.ui.components.common

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.retailassistant.ui.navigation.bottomNavItems

@Composable
fun AppBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isVisible = bottomNavItems.any { it.screen.route == currentDestination?.route }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(animationSpec = tween(300)) { it } + fadeIn(tween(300)),
        exit = slideOutVertically(animationSpec = tween(300)) { it } + fadeOut(tween(300))
    ) {
        NavigationBar(
            tonalElevation = 8.dp
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                NavigationBarItem(
                    selected = isSelected,
                    onClick = {
                        navController.navigate(item.screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            item.icon,
                            contentDescription = item.title,
                            modifier = Modifier.size(if (isSelected) 26.dp else 24.dp)
                        )
                    },
                    label = { Text(item.title, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CenteredTopAppBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        )
    )
}
