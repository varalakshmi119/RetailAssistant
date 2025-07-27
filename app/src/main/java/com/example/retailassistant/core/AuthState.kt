package com.example.retailassistant.core

import androidx.compose.runtime.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth

/**
 * Utility composable to observe authentication state changes.
 * This provides a clean way to react to login/logout events throughout the app.
 */
@Composable
fun rememberAuthState(supabase: SupabaseClient): AuthState {
    val session by supabase.auth.sessionStatus.collectAsState()
    val isAuthenticated = supabase.auth.currentUserOrNull() != null
    return AuthState(
        isAuthenticated = isAuthenticated,
        user = supabase.auth.currentUserOrNull()
    )
}

/**
 * Data class representing the current authentication state.
 */
data class AuthState(
    val isAuthenticated: Boolean,
    val user: io.github.jan.supabase.auth.user.UserInfo?,
)
