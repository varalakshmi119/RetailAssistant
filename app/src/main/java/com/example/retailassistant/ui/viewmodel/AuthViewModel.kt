package com.example.retailassistant.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.retailassistant.data.InvoiceRepository
import com.example.retailassistant.ui.MviViewModel
import com.example.retailassistant.ui.UiAction
import com.example.retailassistant.ui.UiEvent
import com.example.retailassistant.ui.UiState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

// State, Actions, and Events for Auth Screen
data class AuthState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isSignUp: Boolean = false
) : UiState

sealed class AuthAction : UiAction {
    data class UpdateEmail(val email: String) : AuthAction()
    data class UpdatePassword(val password: String) : AuthAction()
    object ToggleSignUpMode : AuthAction()
    object SignIn : AuthAction()
    object SignUp : AuthAction()
}

sealed class AuthEvent : UiEvent {
    object NavigateToDashboard : AuthEvent()
    data class ShowError(val message: String) : AuthEvent()
}

class AuthViewModel(
    private val supabase: SupabaseClient,
    private val repository: InvoiceRepository
) : MviViewModel<AuthState, AuthAction, AuthEvent>() {

    override fun createInitialState(): AuthState = AuthState()

    override fun handleAction(action: AuthAction) {
        when (action) {
            is AuthAction.UpdateEmail -> setState { copy(email = action.email) }
            is AuthAction.UpdatePassword -> setState { copy(password = action.password) }
            is AuthAction.ToggleSignUpMode -> setState { copy(isSignUp = !isSignUp) }
            is AuthAction.SignIn -> signIn()
            is AuthAction.SignUp -> signUp()
        }
    }

    private fun signIn() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            
            try {
                supabase.auth.signInWith(Email) {
                    email = currentState.email
                    password = currentState.password
                }
                
                // Sync user data after successful login
                repository.syncUserData().onFailure { 
                    // Log error but don't block login
                }
                
                sendEvent(AuthEvent.NavigateToDashboard)
            } catch (e: Exception) {
                sendEvent(AuthEvent.ShowError(e.message ?: "Sign in failed"))
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
                    email = currentState.email
                    password = currentState.password
                }
                
                sendEvent(AuthEvent.NavigateToDashboard)
            } catch (e: Exception) {
                sendEvent(AuthEvent.ShowError(e.message ?: "Sign up failed"))
            } finally {
                setState { copy(isLoading = false) }
            }
        }
    }
}