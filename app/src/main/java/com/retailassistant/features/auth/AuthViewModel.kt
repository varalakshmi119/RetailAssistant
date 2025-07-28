package com.retailassistant.features.auth

import androidx.lifecycle.viewModelScope
import com.retailassistant.core.MviViewModel
import com.retailassistant.core.UiAction
import com.retailassistant.core.UiEvent
import com.retailassistant.core.UiState
import com.retailassistant.data.repository.RetailRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.launch

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
    object NavigateToDashboard : AuthEvent
    data class ShowMessage(val message: String) : AuthEvent
}

class AuthViewModel(
    private val supabase: SupabaseClient,
    private val repository: RetailRepository
) : MviViewModel<AuthState, AuthAction, AuthEvent>() {

    override fun createInitialState(): AuthState = AuthState()

    override fun handleAction(action: AuthAction) {
        when (action) {
            is AuthAction.UpdateEmail -> setState { copy(email = action.email) }
            is AuthAction.UpdatePassword -> setState { copy(password = action.password) }
            is AuthAction.ToggleMode -> setState { copy(isSignUpMode = !isSignUpMode, password = "") }
            is AuthAction.Submit -> if (uiState.value.isSignUpMode) signUp() else signIn()
        }
    }

    private fun signIn() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            try {
                supabase.auth.signInWith(Email) {
                    email = uiState.value.email.trim()
                    password = uiState.value.password
                }
                val userId = supabase.auth.currentUserOrNull()?.id
                    ?: throw IllegalStateException("Sign-in successful but user not found.")

                // Sync data after login, but navigate immediately for better UX.
                repository.syncAllUserData(userId)
                sendEvent(AuthEvent.NavigateToDashboard)
            } catch (e: Exception) {
                sendEvent(AuthEvent.ShowMessage(mapAuthException(e)))
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
                    email = uiState.value.email.trim()
                    password = uiState.value.password
                }
                sendEvent(AuthEvent.ShowMessage("Account created! Please check your email for a confirmation link."))
                // Switch to sign-in mode for convenience.
                setState { copy(isSignUpMode = false, password = "") }
            } catch (e: Exception) {
                sendEvent(AuthEvent.ShowMessage(mapAuthException(e)))
            } finally {
                setState { copy(isLoading = false) }
            }
        }
    }

    private fun mapAuthException(e: Exception): String {
        return when (e) {
            is RestException -> when {
                e.message?.contains("email_not_confirmed", true) == true -> "Please confirm your email, then sign in."
                e.message?.contains("invalid_login_credentials", true) == true -> "Invalid email or password."
                e.message?.contains("User already registered", true) == true -> "An account with this email already exists. Please sign in."
                e.message?.contains("weak_password", true) == true -> "Password is too weak. Please use at least 6 characters."
                else -> e.message ?: "An authentication error occurred."
            }
            is HttpRequestException -> "Network error. Please check your connection."
            else -> e.message ?: "An unknown error occurred."
        }
    }
}
