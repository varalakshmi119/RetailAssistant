package com.example.retailassistant.features.auth

import androidx.lifecycle.viewModelScope
import com.example.retailassistant.core.MviViewModel
import com.example.retailassistant.core.UiAction
import com.example.retailassistant.core.UiEvent
import com.example.retailassistant.core.UiState
import com.example.retailassistant.data.repository.RetailRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.launch

// --- MVI Definitions ---
data class AuthState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isSignUpMode: Boolean = false
) : UiState

sealed interface AuthAction : UiAction {
    data class UpdateEmail(val email: String) : AuthAction
    data class UpdatePassword(val password: String) : AuthAction
    object ToggleMode : AuthAction
    object Submit : AuthAction
}

sealed interface AuthEvent : UiEvent {
    data class NavigateToDashboard(val syncFailed: Boolean) : AuthEvent
    data class ShowError(val message: String) : AuthEvent
}

// --- ViewModel ---
class AuthViewModel(
    private val supabase: SupabaseClient,
    private val repository: RetailRepository
) : MviViewModel<AuthState, AuthAction, AuthEvent>() {

    override fun createInitialState(): AuthState = AuthState()

    override fun handleAction(action: AuthAction) {
        when (action) {
            is AuthAction.UpdateEmail -> setState { copy(email = action.email) }
            is AuthAction.UpdatePassword -> setState { copy(password = action.password) }
            is AuthAction.ToggleMode -> setState { copy(isSignUpMode = !isSignUpMode) }
            is AuthAction.Submit -> if (currentState.isSignUpMode) signUp() else signIn()
        }
    }

    private fun signIn() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            var syncFailed = false
            try {
                // 1. Perform the sign in
                supabase.auth.signInWith(Email) {
                    email = currentState.email.trim()
                    password = currentState.password
                }

                // 2. AFTER sign-in succeeds, get the user ID from the now-current session.
                val userId = supabase.auth.currentUserOrNull()?.id
                if (userId != null) {
                    // 3. Sync data for the logged-in user.
                    repository.syncAllUserData(userId).onFailure {
                        // If sync fails, we still proceed but notify the dashboard to show a message.
                        syncFailed = true
                    }
                } else {
                    // This case is unlikely but a good safeguard.
                    throw IllegalStateException("Sign-in successful but user not found.")
                }
                sendEvent(AuthEvent.NavigateToDashboard(syncFailed))
            } catch (e: Exception) {
                sendEvent(AuthEvent.ShowError(mapAuthException(e)))
            } finally {
                setState { copy(isLoading = false) }
            }
        }
    }

    private fun signUp() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            try {
                supabase.auth.signUpWith(Email) {
                    email = currentState.email.trim()
                    password = currentState.password
                }
                // On successful sign-up, the user is logged in. Navigate to the dashboard.
                // No data sync is needed for a new account.
                sendEvent(AuthEvent.NavigateToDashboard(syncFailed = false))
            } catch (e: Exception) {
                sendEvent(AuthEvent.ShowError(mapAuthException(e)))
            } finally {
                setState { copy(isLoading = false) }
            }
        }
    }

    private fun mapAuthException(e: Exception): String {
        return when (e) {
            is RestException -> e.message ?: "An authentication error occurred."
            is HttpRequestException -> "Network error. Please check your connection."
            else -> "An unknown error occurred. Please try again."
        }
    }
}
