package com.example.retailassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.retailassistant.ui.AuthScreen
import com.example.retailassistant.ui.InvoiceScreen
import com.example.retailassistant.ui.MainScreen
import com.example.retailassistant.ui.theme.RetailAssistantTheme
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    
    private val supabase: SupabaseClient by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            RetailAssistantTheme {
                RetailAssistantApp()
            }
        }
    }

    @Composable
    private fun RetailAssistantApp() {
        val navController = rememberNavController()
        
        // Check if user is already logged in
        val isLoggedIn = supabase.auth.currentUserOrNull() != null
        val startDestination = if (isLoggedIn) "main" else "auth"

        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable("auth") {
                AuthScreen(
                    onNavigateToDashboard = {
                        navController.navigate("main") {
                            popUpTo("auth") { inclusive = true }
                        }
                    }
                )
            }
            
            composable("main") {
                MainScreen(
                    onNavigateToAddInvoice = {
                        navController.navigate("add_invoice")
                    }
                )
            }
            
            composable("add_invoice") {
                InvoiceScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}